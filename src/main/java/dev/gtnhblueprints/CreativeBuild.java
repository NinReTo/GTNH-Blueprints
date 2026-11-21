package dev.gtnhblueprints;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.IOException;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** Постройка выполняется по тикам встроенного сервера одиночной игры. */
public final class CreativeBuild {
    static final CreativeBuild INSTANCE = new CreativeBuild();
    private final AtomicReference<Job> pending = new AtomicReference<>();
    private Job active;

    static boolean available(Minecraft game) {
        return game.func_71401_C() != null && game.field_71439_g != null &&
            game.field_71439_g.field_71075_bZ.field_75098_d;
    }

    void start(Minecraft game, Library.Entry entry) throws IOException {
        if (!available(game)) throw new IOException("Автопостройка доступна в творческом режиме одиночной игры");
        Blueprint blueprint = Hologram.INSTANCE.selected(game, entry);
        int[] origin = Hologram.INSTANCE.position(game, entry, blueprint);
        Schematics.Part[] parts = Schematics.create(blueprint);
        pending.set(new Job(game.func_71401_C(), game.field_71439_g.func_70005_c_(),
            game.field_71439_g.field_71093_bK, blueprint, parts, origin[0], origin[1], origin[2]));
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Job next = pending.getAndSet(null);
        if (next != null) active = next;
        Job job = active;
        if (job == null) return;
        EntityPlayerMP player = job.server == MinecraftServer.func_71276_C()
            ? job.server.func_71203_ab().func_152612_a(job.player) : null;
        if (player == null || !player.field_71075_bZ.field_75098_d || player.field_71093_bK != job.dimension) {
            active = null;
            return;
        }
        if (job.tick(player)) active = null;
    }

    private static final class Job {
        final MinecraftServer server;
        final String player;
        final int dimension;
        final Blueprint blueprint;
        final Schematics.Part[] parts;
        final int x, y, z;
        int cursor, placed, skipped;
        final BitSet pipes = new BitSet();
        int pipeCursor, rejectedSides;
        int rate = 20;
        int credit, stable;
        long lastTick;

        Job(MinecraftServer server, String player, int dimension, Blueprint blueprint, Schematics.Part[] parts, int x, int y, int z) {
            this.server = server;
            this.player = player;
            this.dimension = dimension;
            this.blueprint = blueprint;
            this.parts = parts;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        boolean tick(EntityPlayerMP player) {
            long start = System.nanoTime();
            if (lastTick != 0 && start - lastTick > 60_000_000L) slowDown();
            lastTick = start;
            credit += rate;
            World world = player.field_70170_p;
            while (credit >= 20 && cursor < blueprint.blocks.length) {
                credit -= 20;
                int index = cursor++;
                if (place(world, player, index)) placed++;
                else skipped++;
                if (System.nanoTime() - start > 4_000_000L) {
                    credit %= 20;
                    slowDown();
                    break;
                }
            }
            // Только после установки оборудования соединяем трубы с уже существующими портами.
            while (cursor == blueprint.blocks.length && credit >= 20 && System.nanoTime() - start <= 4_000_000L) {
                int index = pipes.nextSetBit(pipeCursor);
                if (index < 0) break;
                pipeCursor = index + 1;
                credit -= 20;
                connectPipe(world, index);
            }
            if (++stable >= 40) {
                rate = Math.min(50, rate + 5);
                stable = 0;
            }
            if (cursor < blueprint.blocks.length || pipes.nextSetBit(pipeCursor) >= 0) return false;
            player.func_145747_a(new ChatComponentText("Чертёж: поставлено " + placed + ", пропущено " + skipped +
                ". Сторон труб без подключения: " + rejectedSides + "."));
            return true;
        }

        private void slowDown() {
            rate = Math.max(5, rate / 2);
            stable = 0;
        }

        private boolean place(World world, EntityPlayerMP player, int index) {
            int[] cell = blueprint.blocks[index];
            Schematics.Part part = parts[index];
            int wx = x + cell[0], wy = y + cell[1], wz = z + cell[2];
            if (!world.func_72899_e(wx, wy, wz) || !world.func_147437_c(wx, wy, wz) ||
                player.field_70121_D.func_72326_a(AxisAlignedBB.func_72330_a(wx, wy, wz, wx + 1, wy + 1, wz + 1))) return false;
            if (part.machineId == 0) return world.func_147465_d(wx, wy, wz, part.block, part.metadata, 3);
            // Пока у корпуса нет данных машины, не рассылаем его клиенту и не оповещаем соседей.
            if (!world.func_147465_d(wx, wy, wz, part.block, part.metadata, 0)) return false;
            TileEntity target = world.func_147438_o(wx, wy, wz);
            if (target instanceof IGregTechTileEntity && initialize((IGregTechTileEntity) target, player, part)) {
                if (target instanceof BaseMetaPipeEntity) pipes.set(index);
                target.func_145843_s();
                world.func_147471_g(wx, wy, wz);
                return true;
            }
            clearFailedPlacement(world, wx, wy, wz, part);
            return false;
        }

        private void clearFailedPlacement(World world, int x, int y, int z, Schematics.Part part) {
            if (world.func_147439_a(x, y, z) == part.block) world.func_147468_f(x, y, z);
        }

        private boolean initialize(IGregTechTileEntity target, EntityPlayerMP player, Schematics.Part part) {
            target.setInitialValuesAsNBT(null, (short) part.machineId);
            if (target.getMetaTileEntity() == null || target.getMetaTileID() != part.machineId) return false;
            target.setOwnerName(player.func_70005_c_());
            target.setOwnerUuid(player.func_110124_au());
            target.setFrontFacing(ForgeDirection.getOrientation(part.facing));
            target.getMetaTileEntity().initDefaultModes(null);
            return true;
        }

        private void connectPipe(World world, int index) {
            int[] cell = blueprint.blocks[index];
            Schematics.Part part = parts[index];
            int wx = x + cell[0], wy = y + cell[1], wz = z + cell[2];
            TileEntity tile = world.func_72899_e(wx, wy, wz) ? world.func_147438_o(wx, wy, wz) : null;
            if (!(tile instanceof BaseMetaPipeEntity)) {
                rejectedSides += Integer.bitCount(part.connections & 63);
                return;
            }
            BaseMetaPipeEntity base = (BaseMetaPipeEntity) tile;
            if (base.getMetaTileID() != part.machineId || !(base.getMetaTileEntity() instanceof MetaPipeEntity)) {
                rejectedSides += Integer.bitCount(part.connections & 63);
                return;
            }
            MetaPipeEntity pipe = (MetaPipeEntity) base.getMetaTileEntity();
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                if ((part.connections & side.flag) == 0) continue;
                pipe.connect(side);
                if (!pipe.isConnectedAtSide(side)) rejectedSides++;
            }
            base.updateConnections();
            base.syncConnectionToClient();
            base.func_145843_s();
        }
    }
}

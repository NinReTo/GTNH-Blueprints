package dev.gtnhblueprints;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Контуры блоков чертежа поверх мира. Голубой — свободно, красный — занято. */
public final class Hologram {
    static final Hologram INSTANCE = new Hologram();
    private static final double RADIUS = 48;
    private static final int[][] EDGES = {
        {0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4},
        {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private final KeyBinding move = new KeyBinding("Перенести голограмму", Keyboard.KEY_NUMPAD7, "Чертежи фабрик");
    private final KeyBinding rotate = new KeyBinding("Повернуть голограмму", Keyboard.KEY_NUMPAD8, "Чертежи фабрик");
    private final KeyBinding hide = new KeyBinding("Показать или скрыть голограмму", Keyboard.KEY_NUMPAD9, "Чертежи фабрик");
    private World world;
    private Path entry;
    private Blueprint blueprint;
    private int[] origin;
    private boolean visible;

    void registerKeys() {
        ClientRegistry.registerKeyBinding(move);
        ClientRegistry.registerKeyBinding(rotate);
        ClientRegistry.registerKeyBinding(hide);
    }

    void show(Minecraft game, Library.Entry selection) throws IOException {
        if (game.field_71441_e == null) throw new IOException("Откройте мир");
        Blueprint next = selection.read();
        int[] point = aim(game, next);
        world = game.field_71441_e;
        entry = selection.path;
        blueprint = next;
        origin = point;
        visible = true;
    }

    int[] position(Minecraft game, Library.Entry selection, Blueprint next) throws IOException {
        if (world == game.field_71441_e && selection.path.equals(entry) && origin != null) return origin.clone();
        return aim(game, next);
    }

    Blueprint selected(Minecraft game, Library.Entry selection) throws IOException {
        return world == game.field_71441_e && selection.path.equals(entry) && blueprint != null
            ? blueprint : selection.read();
    }

    private static int[] aim(Minecraft game, Blueprint blueprint) throws IOException {
        MovingObjectPosition hit = game.field_71476_x;
        if (hit == null || !"BLOCK".equals(((Enum<?>) hit.field_72313_a).name())) {
            throw new IOException("Наведитесь на грань блока перед открытием инвентаря");
        }
        ForgeDirection face = ForgeDirection.getOrientation(hit.field_72310_e);
        int y = hit.field_72312_c + face.offsetY;
        if (y < 0 || y + blueprint.size[1] > game.field_71441_e.func_72800_K()) {
            throw new IOException("Чертёж не помещается по высоте мира");
        }
        return new int[] {hit.field_72311_b + face.offsetX, y, hit.field_72309_d + face.offsetZ};
    }

    @SubscribeEvent
    public void keys(InputEvent.KeyInputEvent event) {
        Minecraft game = Minecraft.func_71410_x();
        if (game.field_71462_r != null || world != game.field_71441_e || blueprint == null) return;
        if (hide.func_151468_f()) visible = !visible;
        if (rotate.func_151468_f()) {
            blueprint = blueprint.rotated();
            visible = true;
        }
        if (move.func_151468_f()) {
            try {
                origin = aim(game, blueprint);
                visible = true;
            } catch (IOException ignored) {
                // Прицел в воздухе — сохраняем прежнюю точку.
            }
        }
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft game = Minecraft.func_71410_x();
        if (world != game.field_71441_e) {
            world = null;
            blueprint = null;
            origin = null;
        }
        Entity camera = game.field_71451_h;
        if (!visible || blueprint == null || world == null || camera == null) return;
        float t = event.partialTicks;
        double cx = camera.field_70142_S + (camera.field_70165_t - camera.field_70142_S) * t;
        double cy = camera.field_70137_T + (camera.field_70163_u - camera.field_70137_T) * t;
        double cz = camera.field_70136_U + (camera.field_70161_v - camera.field_70136_U) * t;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            Tessellator lines = Tessellator.field_78398_a;
            lines.func_78371_b(GL11.GL_LINES);
            for (int[] cell : blueprint.blocks) {
                int x = origin[0] + cell[0], y = origin[1] + cell[1], z = origin[2] + cell[2];
                double dx = x - cx, dy = y - cy, dz = z - cz;
                if (dx * dx + dy * dy + dz * dz > RADIUS * RADIUS || !world.func_72899_e(x, y, z)) continue;
                boolean free = world.func_147437_c(x, y, z);
                lines.func_78369_a(free ? 64 : 255, free ? 211 : 93, free ? 255 : 83, 170);
                double inset = free ? 0.002 : -0.003;
                for (int[] edge : EDGES) {
                    vertex(lines, dx, dy, dz, edge[0], inset);
                    vertex(lines, dx, dy, dz, edge[1], inset);
                }
            }
            lines.func_78381_a();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void vertex(Tessellator lines, double x, double y, double z, int corner, double inset) {
        lines.func_78377_a(x + (((corner & 1) == 0) ? inset : 1 - inset),
            y + (((corner & 2) == 0) ? inset : 1 - inset),
            z + (((corner & 4) == 0) ? inset : 1 - inset));
    }
}

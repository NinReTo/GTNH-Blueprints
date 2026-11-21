package dev.gtnhblueprints;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.common.blocks.BlockMachines;
import gregtech.common.blocks.BlockFrameBox;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mods.railcraft.common.blocks.machine.beta.EnumMachineBeta;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

final class Schematics {
    // Порядок граней сайта: +X, -X, +Y, -Y, +Z, -Z.
    private static final int[] FACING = {5, 4, 1, 0, 3, 2};

    static final class Part {
        final Block block;
        final int metadata;
        final TileEntity tile;

        Part(Block block, int metadata, TileEntity tile) {
            this.block = block;
            this.metadata = metadata;
            this.tile = tile;
        }
    }

    static Part[] create(Blueprint blueprint) throws IOException {
        ItemStack[] items = new ItemStack[blueprint.palette.length];
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            Blueprint.Material material = blueprint.palette[i];
            items[i] = resolve(material, blueprint.tier);
            Block block = items[i] == null ? null : Block.func_149634_a(items[i].func_77973_b());
            if (block == null || block == Blocks.field_150350_a) unknown.add(material.name);
        }
        if (!unknown.isEmpty()) {
            throw new IOException("Не сопоставлены блоки (" + unknown.size() + "): " + String.join(", ", unknown));
        }
        Part[] parts = new Part[blueprint.blocks.length];
        for (int i = 0; i < blueprint.blocks.length; i++) {
            int[] cell = blueprint.blocks[i];
            ItemStack item = items[cell[3]];
            Block block = Block.func_149634_a(item.func_77973_b());
            int meta = item.func_77960_j();
            int facing = cell[4] < 0 ? 3 : FACING[cell[4]];
            if (block instanceof BlockMachines) {
                IMetaTileEntity definition = meta > 0 && meta < GregTechAPI.METATILEENTITIES.length ? GregTechAPI.METATILEENTITIES[meta] : null;
                if (definition == null) throw new IOException("Нет блока GregTech с номером " + meta);
                int baseType = definition.getTileEntityBaseType();
                TileEntity tile = ((BlockMachines) block).createTileEntity(null, baseType);
                NBTTagCompound data = new NBTTagCompound();
                data.func_74777_a("mID", (short) meta);
                data.func_74777_a("mFacing", (short) facing);
                data.func_74774_a("mConnections", pipeMask(cell[5]));
                if (tile instanceof BaseMetaTileEntity) {
                    BaseMetaTileEntity machine = (BaseMetaTileEntity) tile;
                    machine.func_145839_a(data);
                } else if (tile instanceof BaseMetaPipeEntity) {
                    BaseMetaPipeEntity pipe = (BaseMetaPipeEntity) tile;
                    pipe.func_145839_a(data);
                    pipe.mConnections = pipeMask(cell[5]);
                    if (pipe.getMetaTileEntity() instanceof MetaPipeEntity) {
                        ((MetaPipeEntity) pipe.getMetaTileEntity()).mConnections = pipe.mConnections;
                    }
                } else {
                    throw new IOException("Неизвестный тип блока GregTech " + meta);
                }
                parts[i] = new Part(block, baseType, tile);
            } else {
                if (block == Blocks.field_150486_ae) meta = facing < 2 ? 3 : facing;
                if (block == Blocks.field_150438_bZ) meta = cell[4] >= 0 ? (facing == 1 ? 0 : facing) :
                    hopperFacing(blueprint.palette[cell[3]].kind, facing);
                if (meta > 15 && !(block instanceof BlockFrameBox)) {
                    throw new IOException("Неподдерживаемый вариант блока " + blueprint.palette[cell[3]].kind);
                }
                parts[i] = new Part(block, meta, null);
            }
        }
        return parts;
    }

    private static byte pipeMask(int mask) {
        int result = 0;
        for (int i = 0; i < FACING.length; i++) if ((mask & (1 << i)) != 0) result |= 1 << FACING[i];
        return (byte) result;
    }

    private static int hopperFacing(String kind, int fallback) {
        if (kind.endsWith("Back")) return 2;
        if (kind.endsWith("Front")) return 3;
        if (kind.endsWith("Right")) return 5;
        if (kind.equals("ownHopper")) return 0;
        return fallback == 1 ? 0 : fallback;
    }

    private static ItemStack resolve(Blueprint.Material material, String rawTier) {
        if (material.item != null) {
            String[] id = material.item.split(":", 2);
            Item item = GameRegistry.findItem(id[0], id[1]);
            if (item != null) return new ItemStack(item, 1, material.damage);
        }
        String tier = rawTier.equals("luv") ? "LuV" : rawTier.toUpperCase(Locale.ROOT);
        String kind = material.kind;
        switch (kind) {
            case "ownChest": return new ItemStack(Blocks.field_150486_ae);
            case "ownHopper":
            case "ownHopperBack":
            case "ownHopperFront":
            case "ownHopperRight": return new ItemStack(Blocks.field_150438_bZ);
            case "ironBlock": return new ItemStack(Blocks.field_150339_S);
            case "glass": return new ItemStack(Blocks.field_150359_w);
            case "calcTank": return gt("Super_Tank_LV");
            case "rcFirebox": return EnumMachineBeta.BOILER_FIREBOX_SOLID.getItem();
            case "rcTank": return EnumMachineBeta.BOILER_TANK_HIGH_PRESSURE.getItem();
            case "heatproof": return gt("Casing_HeatProof");
            case "coil": return gt("Casing_Coil_Cupronickel");
            case "ebfController": return gt("Machine_Multi_BlastFurnace");
            case "bbfController": return gt("Machine_Bricked_BlastFurnace");
            case "firebrick": return gt("Casing_Firebricks");
            case "bronzeBricks": return gt("Casing_BronzePlatedBricks");
            case "bronzeGearbox": return gt("Casing_Gearbox_Bronze");
            case "bronzePipe": return gt("Casing_Pipe_Bronze");
            case "bronzeFirebox": return gt("Casing_Firebox_Bronze");
            case "wood": return new ItemStack(GregTechAPI.sBlockCasings9, 1, 2);
            case "bronzeFrame": return new ItemStack(GregTechAPI.sBlockFrames, 1, Materials.Bronze.mMetaItemSubID);
            case "pumpController": return metaMachine("MTESteamWaterPump");
            case "pumpSteamHatch": return metaMachine("MTEHatchSteamBusInput");
            case "pumpOutputHatch": return gt("Hatch_Output_LV");
            case "energy": return gt("Hatch_Energy_" + tier);
            case "maintenance": return gt("Hatch_Maintenance");
            case "inputBus": return gt("Hatch_Input_Bus_LV");
            case "outputBus": return gt("Hatch_Output_Bus_LV");
            case "inputHatch": return gt("Hatch_Input_LV");
            case "outputHatch": return gt("Hatch_Output_LV");
            case "muffler": return gt("Hatch_Muffler_LV");
            default: break;
        }
        if (kind.startsWith("steam-turbine-")) return gt("Generator_Steam_Turbine_" + tier);
        if (kind.startsWith("gas-turbine-")) return gt("Generator_Gas_Turbine_" + tier);
        if (kind.startsWith("combustion-generator-") || kind.startsWith("diesel-generator-")) return gt("Generator_Diesel_" + tier);
        if (kind.startsWith("calc-")) {
            String suffix = kind.substring(kind.lastIndexOf('-') + 1);
            if (suffix.matches("lv|mv|hv|ev|iv|luv|zpm|uv|uhv|uev|uiv|umv|uxv|max")) {
                tier = suffix.equals("luv") ? "LuV" : suffix.toUpperCase(Locale.ROOT);
            }
            String name = material.name.replaceFirst(" (?i:LV|MV|HV|EV|IV|LuV|ZPM|UV|UHV|UEV|UIV|UMV|UXV|MAX)$", "");
            if (name.equalsIgnoreCase("Furnace") || name.equalsIgnoreCase("Electric Furnace")) name = "E_Furnace";
            if (name.equalsIgnoreCase("Forge Hammer")) name = "Hammer";
            if (name.equalsIgnoreCase("Wire Mill")) name = "Wiremill";
            return gt("Machine_" + tier + "_" + name.replace(" ", ""));
        }
        return null;
    }

    private static ItemStack gt(String name) {
        try {
            return ItemList.valueOf(name).get(1);
        } catch (IllegalArgumentException missing) {
            for (ItemList item : ItemList.values()) if (item.name().equalsIgnoreCase(name)) return item.get(1);
            return null;
        } catch (IllegalStateException missing) {
            return null;
        }
    }

    private static ItemStack metaMachine(String className) {
        ItemStack match = null;
        for (IMetaTileEntity machine : GregTechAPI.METATILEENTITIES) {
            if (machine == null || !machine.getClass().getSimpleName().equals(className)) continue;
            if (match != null) return null;
            match = machine.getStackForm(1);
        }
        return match;
    }
}

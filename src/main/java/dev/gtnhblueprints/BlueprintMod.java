package dev.gtnhblueprints;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.ReflectionHelper;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = "gtnhblueprints", name = "GTNH Blueprints", version = "0.1.7",
    acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*",
    dependencies = "required-after:gregtech;required-after:Railcraft")
public final class BlueprintMod {
    private static final int INVENTORY_BUTTON = -17341;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new InventoryButton());
            MinecraftForge.EVENT_BUS.register(Hologram.INSTANCE);
            FMLCommonHandler.instance().bus().register(Hologram.INSTANCE);
            Hologram.INSTANCE.registerKeys();
            FMLCommonHandler.instance().bus().register(CreativeBuild.INSTANCE);
        }
    }

    private static final class BlueprintTab extends GuiButton {
        private final RenderItem renderer = new RenderItem();

        BlueprintTab(int x, int y) {
            super(INVENTORY_BUTTON, x, y, 26, 26, "");
        }

        @Override
        public void func_146112_a(Minecraft game, int mouseX, int mouseY) {
            int x = field_146128_h, y = field_146129_i;
            boolean hover = mouseX >= x && mouseX < x + 26 && mouseY >= y && mouseY < y + 26;
            func_73734_a(x, y + 3, x + 26, y + 26, 0xFF343434);
            func_73734_a(x + 3, y, x + 23, y + 3, 0xFF343434);
            func_73734_a(x + 2, y + 3, x + 24, y + 25, hover ? 0xFFF5E3AE : 0xFFD1D1D1);
            Item icon = GameRegistry.findItem("minecraft", "writable_book");
            if (icon != null) renderer.func_77015_a(game.field_71466_p, game.func_110434_K(), new ItemStack(icon), x + 5, y + 5);
            if (hover) game.field_71466_p.func_78261_a("Чертежи", x + 30, y + 8, 0xFFFFFFFF);
        }
    }

    private static final class NotebookButton extends GuiButton {
        private final RenderItem renderer = new RenderItem();

        NotebookButton(int id, int left, int top, int width, String label) {
            super(id, left, top, width, 20, "    " + label);
        }

        @Override
        public void func_146112_a(Minecraft minecraft, int mouseX, int mouseY) {
            super.func_146112_a(minecraft, mouseX, mouseY);
            Item notebook = GameRegistry.findItem("minecraft", "writable_book");
            if (notebook != null) renderer.func_77015_a(minecraft.field_71466_p, minecraft.func_110434_K(),
                new ItemStack(notebook), field_146128_h + 3, field_146129_i + 2);
        }
    }

    public static final class InventoryButton {
        private boolean isInventory(GuiScreen screen) {
            return screen instanceof GuiInventory || screen instanceof GuiContainerCreative;
        }

        @SubscribeEvent
        public void add(GuiScreenEvent.InitGuiEvent.Post event) {
            if (!isInventory(event.gui)) return;
            GuiContainer inventory = (GuiContainer) event.gui;
            int left = ReflectionHelper.getPrivateValue(GuiContainer.class, inventory, "guiLeft", "field_147003_i");
            int top = ReflectionHelper.getPrivateValue(GuiContainer.class, inventory, "guiTop", "field_147009_r");
            boolean creative = inventory instanceof GuiContainerCreative;
            event.buttonList.add(new BlueprintTab(left + (creative ? 55 : 56), Math.max(1, top - (creative ? 55 : 26))));
        }

        @SubscribeEvent
        public void open(GuiScreenEvent.ActionPerformedEvent.Pre event) {
            if (!isInventory(event.gui) || event.button.field_146127_k != INVENTORY_BUTTON) return;
            event.setCanceled(true);
            Minecraft game = Minecraft.func_71410_x();
            Library library = new Library(game.field_71412_D.toPath().resolve("blueprints").resolve("factory-calculator"));
            game.func_147108_a(new BlueprintScreen(event.gui, library));
        }
    }

    private static final class BlueprintScreen extends GuiScreen {
        private static final int PAGE_SIZE = 6;
        private static final int ADD = 1;
        private static final int PLACE = 2;
        private static final int BACK = 3;
        private static final int PREVIOUS = 4;
        private static final int NEXT = 5;
        private static final int BUILD = 6;
        private static final int ROW = 10;
        private final GuiScreen parent;
        private final Library library;
        private List<Library.Entry> entries = Collections.emptyList();
        private int page;
        private Library.Entry selected;
        private String status = "";

        BlueprintScreen(GuiScreen parent, Library library) {
            this.parent = parent;
            this.library = library;
            reload();
        }

        private void reload() {
            try {
                entries = library.entries();
            } catch (IOException failure) {
                status = "Не удалось прочитать библиотеку";
            }
        }

        @Override
        public void func_73866_w_() {
            field_146292_n.clear();
            int width = Math.min(300, field_146294_l - 16);
            int left = (field_146294_l - width) / 2;
            int top = Math.max(28, field_146295_m / 2 - 90);
            for (int row = 0; row < PAGE_SIZE && page * PAGE_SIZE + row < entries.size(); row++) {
                Library.Entry entry = entries.get(page * PAGE_SIZE + row);
                String title = (selected != null && entry.path.equals(selected.path) ? "> " : "") + entry.title;
                field_146292_n.add(new GuiButton(ROW + row, left, top + row * 20, width, 20,
                    field_146289_q.func_78269_a(title, width - 12)));
            }
            int bottom = top + PAGE_SIZE * 20;
            GuiButton previous = new GuiButton(PREVIOUS, left, bottom, 27, 20, "<");
            previous.field_146124_l = page > 0;
            field_146292_n.add(previous);
            GuiButton next = new GuiButton(NEXT, left + width - 27, bottom, 27, 20, ">");
            next.field_146124_l = (page + 1) * PAGE_SIZE < entries.size();
            field_146292_n.add(next);
            int section = width / 3;
            field_146292_n.add(new NotebookButton(ADD, left, bottom + 22, section, "+ Схема"));
            GuiButton place = new GuiButton(PLACE, left + section, bottom + 22, section, 20, "Голограмма");
            place.field_146124_l = selected != null;
            field_146292_n.add(place);
            field_146292_n.add(new GuiButton(BACK, left + section * 2, bottom + 22, width - section * 2, 20, "Назад"));
            if (CreativeBuild.available(field_146297_k)) {
                GuiButton build = new GuiButton(BUILD, left, bottom + 44, width, 20, "Автопостройка");
                build.field_146124_l = selected != null;
                field_146292_n.add(build);
            }
        }

        @Override
        protected void func_146284_a(GuiButton button) {
            int id = button.field_146127_k;
            if (id == BACK) {
                field_146297_k.func_147108_a(parent);
                return;
            }
            if (id == PREVIOUS) page--;
            if (id == NEXT) page++;
            if (id >= ROW) selected = entries.get(page * PAGE_SIZE + id - ROW);
            try {
                if (id == ADD) {
                    library.add(func_146277_j().trim());
                    reload();
                    status = "Чертёж добавлен. Выберите его в списке.";
                }
                if (id == PLACE && selected != null) {
                    Hologram.INSTANCE.show(field_146297_k, selected);
                    field_146297_k.func_147108_a(null);
                    return;
                }
                if (id == BUILD && selected != null) {
                    CreativeBuild.INSTANCE.start(field_146297_k, selected);
                    field_146297_k.func_147108_a(null);
                    return;
                }
            } catch (IOException failure) {
                status = failure.getMessage();
            }
            func_73866_w_();
        }

        @Override
        public void func_73863_a(int x, int y, float partialTicks) {
            func_146276_q_();
            int top = Math.max(28, field_146295_m / 2 - 90);
            func_73732_a(field_146289_q, "Чертежи фабрик", field_146294_l / 2, top - 17, 0xFFFFFF);
            if (entries.isEmpty()) {
                func_73732_a(field_146289_q, "Скопируйте код на сайте и нажмите «+ Схема»", field_146294_l / 2, top + 23, 0xCCCCCC);
            }
            field_146289_q.func_78279_b(status, 8, top + PAGE_SIZE * 20 + 68, field_146294_l - 16, 0xFFFFFF);
            super.func_73863_a(x, y, partialTicks);
        }

        @Override
        protected void func_73869_a(char key, int keyCode) {
            if (keyCode == 1) field_146297_k.func_147108_a(parent);
        }

        @Override
        public boolean func_73868_f() {
            return false;
        }
    }
}

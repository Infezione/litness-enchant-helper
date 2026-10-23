package com.litness.optimizer.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ClientApplicableItemsTooltip implements ClientTooltipComponent {

    private final List<ItemStack> items;
    private final Component title;
    private static final float SCALE = 0.58f; // ~9.3 px (altezza nativa font di Minecraft)
    private static final int SPACING_SCALED = 2;
    private static final int MAX_DISPLAY_ICONS = 8;

    public ClientApplicableItemsTooltip(ApplicableItemsTooltipData data) {
        this.items = data.items() != null ? data.items() : List.of();
        this.title = data.title() != null ? data.title() : Component.literal("Enchanted Book");
    }

    @Override
    public int getHeight(Font font) {
        // Altezza 0: le mini icone si posizionano direttamente ACCANTO al titolo senza creare una riga vuota!
        return 0;
    }

    @Override
    public int getWidth(Font font) {
        if (items.isEmpty()) return 0;
        int titleWidth = font.width(this.title);
        int count = Math.min(items.size(), MAX_DISPLAY_ICONS);
        int iconsWidth = Math.round(count * (16 + SPACING_SCALED) * SCALE);
        return titleWidth + 6 + iconsWidth;
    }

    @Override
    public boolean showTooltipWithItemInHand() {
        return true;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor extractor) {
        if (items.isEmpty()) return;

        int titleWidth = font.width(this.title);
        int startX = x + titleWidth + 5;
        // Allineato perfettamente alla linea del titolo (linea 0 sopra)
        int startY = y - 10;

        int count = Math.min(items.size(), MAX_DISPLAY_ICONS);

        extractor.pose().pushMatrix();
        extractor.pose().translate(startX, startY);
        extractor.pose().scale(SCALE, SCALE);

        for (int i = 0; i < count; i++) {
            ItemStack stack = items.get(i);
            int itemX = i * (16 + SPACING_SCALED);
            extractor.fakeItem(stack, itemX, 0);
        }

        extractor.pose().popMatrix();
    }
}

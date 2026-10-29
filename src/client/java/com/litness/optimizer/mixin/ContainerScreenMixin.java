package com.litness.optimizer.mixin;

import com.litness.optimizer.data.ActiveSessionManager;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import com.litness.optimizer.data.SavedSetsManager;
import com.litness.optimizer.data.SavedSetsManager.CustomSet;
import com.litness.optimizer.tooltip.LitnessTooltipHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.*;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin extends Screen {

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;

    private static long lastTargetCheck = 0;
    private static Set<String> cachedTargetEnchants = Collections.emptySet();

    protected ContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void litness_preScanContainer(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!(((Object) this) instanceof net.minecraft.client.gui.screens.inventory.AnvilScreen)) {
            com.litness.optimizer.gui.AhPriceManager.scanCurrentContainer((AbstractContainerScreen<?>) (Object) this);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void litness_renderContainerTopLayer(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (((Object) this) instanceof net.minecraft.client.gui.screens.inventory.AnvilScreen anvil) {
            com.litness.optimizer.gui.AnvilTooltipState.renderTopLayer(extractor, anvil, mouseX, mouseY);
        } else {
            com.litness.optimizer.gui.AhPriceManager.renderAhSidebar(extractor, (AbstractContainerScreen<?>) (Object) this, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void litness_onContainerMouseClicked(MouseButtonEvent event, boolean isDown, CallbackInfoReturnable<Boolean> cir) {
        if (com.litness.optimizer.gui.AhPriceManager.handleMouseClicked(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void litness_onContainerRemoved(CallbackInfo ci) {
        com.litness.optimizer.gui.AnvilTooltipState.clearHover();
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void litness_highlightSetSlots(GuiGraphicsExtractor extractor, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (slot == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Escludi categoricamente l'inventario personale del giocatore
        if (slot.container == mc.player.getInventory()) return;

        // Escludi l'incudine (ha la sua interfaccia laterale dedicata)
        if (((Object) this) instanceof net.minecraft.client.gui.screens.inventory.AnvilScreen) return;

        // Verifica che siamo in un contenitore supportato (Casse, Barili, Ender Chest, Shulker Box, /ah, /enchgui)
        if (!isSupportedStorageContainer()) return;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastTargetCheck > 50) {
            cachedTargetEnchants = getTargetEnchantments();
            lastTargetCheck = now;
        }

        if (cachedTargetEnchants == null || cachedTargetEnchants.isEmpty()) return;

        if (matchesAnyTargetEnchant(stack, cachedTargetEnchants)) {
            int x = slot.x;
            int y = slot.y;
            boolean isCheapest = com.litness.optimizer.gui.AhPriceManager.isCheapestSlot(slot.index);

            if (isCheapest) {
                // Evidenziazione ARANCIONE VIVIDO per il prezzo più basso assoluto
                extractor.fill(x, y, x + 16, y + 16, 0x65FF9900);
                extractor.fill(x - 1, y - 1, x + 17, y, 0xFFFF9900);
                extractor.fill(x - 1, y + 16, x + 17, y + 17, 0xFFFF9900);
                extractor.fill(x - 1, y, x, y + 16, 0xFFFF9900);
                extractor.fill(x + 16, y, x + 17, y + 16, 0xFFFF9900);
            } else {
                // Evidenziazione VERDE SMERALDO per gli altri libri lisci corrispondenti
                extractor.fill(x, y, x + 16, y + 16, 0x4000FF66);
                extractor.fill(x - 1, y - 1, x + 17, y, 0xFF00FF66);
                extractor.fill(x - 1, y + 16, x + 17, y + 17, 0xFF00FF66);
                extractor.fill(x - 1, y, x, y + 16, 0xFF00FF66);
                extractor.fill(x + 16, y, x + 17, y + 16, 0xFF00FF66);
            }
        }
    }

    private boolean isSupportedStorageContainer() {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        AbstractContainerMenu m = screen.getMenu();
        if (m instanceof ChestMenu) return true; // Casse singole/doppie, Barili, Ender Chest, /ah, /enchgui
        if (m instanceof ShulkerBoxMenu) return true; // Scatole di Shulker aperte
        if (m instanceof DispenserMenu) return true; // Dispenser e Dropper
        if (m instanceof HopperMenu) return true; // Tramogge
        if (com.litness.optimizer.gui.AhPriceManager.isAuctionHouse(screen)) return true;
        String titleStr = this.getTitle().getString();
        return isAuctionHouseOrEnchGui(titleStr);
    }

    private static boolean isAuctionHouseOrEnchGui(String title) {
        if (title == null) return false;
        String clean = title.replaceAll("(?i)§[0-9a-fk-orx]", "").replace('\u00A0', ' ').trim().toUpperCase();

        // Riconoscimento Auction House (/ah)
        if (clean.contains("AUCTION") || clean.contains("ASTA") || clean.contains("ASTE") || clean.contains("MERCATO")
                || clean.equals("AH") || clean.startsWith("AH ") || clean.endsWith(" AH") || clean.contains(" AH ")
                || clean.contains("BAZAAR")) {
            return true;
        }

        // Riconoscimento Enchantment GUI (/enchgui)
        if (clean.contains("ENCHANT") || clean.contains("INCANT") || clean.contains("ENCHGUI") || clean.contains("ENCH")
                || clean.contains("CATALOGO") || clean.contains("TIER") || clean.contains("CUSTOM ENCHANT")) {
            return true;
        }

        return false;
    }

    private static boolean hasServerGuiOrAhLore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            String s = c.getString().toLowerCase().replace('\u00A0', ' ').trim();
            if (s.contains("limite per oggetto") || s.contains("12 incantesimi in totale") || s.contains("1 unico, 1 incantesimo")
                    || s.contains("si applica a:") || s.contains("requisiti:") || s.contains("informazioni:")
                    || s.contains("seller:") || s.contains("price:") || s.contains("venditore:") || s.contains("prezzo:")) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> getTargetEnchantments() {
        return com.litness.optimizer.gui.AhPriceManager.getEffectiveSearchEnchants();
    }

    private static boolean matchesAnyTargetEnchant(ItemStack stack, Set<String> targetEnchants) {
        return com.litness.optimizer.gui.AhPriceManager.containsMatchingCleanBook(stack, targetEnchants);
    }
}

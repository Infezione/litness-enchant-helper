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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin extends Screen {

    private static long lastTargetCheck = 0;
    private static Set<String> cachedTargetEnchants = Collections.emptySet();

    protected ContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void litness_highlightSetSlots(GuiGraphicsExtractor extractor, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (slot == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Escludi categoricamente l'inventario personale del giocatore
        if (slot.container == mc.player.getInventory()) return;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        // Verifica che siamo in un menu server rilevante (/ah o /enchgui o item con lore server)
        String titleStr = this.getTitle().getString();
        boolean isTarget = isAuctionHouseOrEnchGui(titleStr);
        if (!isTarget && !hasServerGuiOrAhLore(stack)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastTargetCheck > 250) {
            cachedTargetEnchants = getTargetEnchantments();
            lastTargetCheck = now;
        }

        if (cachedTargetEnchants == null || cachedTargetEnchants.isEmpty()) return;

        if (matchesAnyTargetEnchant(stack, cachedTargetEnchants)) {
            int x = slot.x;
            int y = slot.y;
            // Evidenziazione verde smeraldo (fill traslucido + bordo solido di 1px)
            extractor.fill(x, y, x + 16, y + 16, 0x4000FF66);
            extractor.fill(x - 1, y - 1, x + 17, y, 0xFF00FF66);
            extractor.fill(x - 1, y + 16, x + 17, y + 17, 0xFF00FF66);
            extractor.fill(x - 1, y, x, y + 16, 0xFF00FF66);
            extractor.fill(x + 16, y, x + 17, y + 16, 0xFF00FF66);
        }
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

    public static void clearCache() {
        cachedTargetEnchants = Collections.emptySet();
        lastTargetCheck = 0;
    }

    private static Set<String> getTargetEnchantments() {
        Set<String> targets = new HashSet<>();

        // 1. Incantesimi selezionati dal Catalogo per la ricerca rapida in AH
        Set<String> searchEnchants = ActiveSessionManager.getSearchEnchants();
        if (searchEnchants != null && !searchEnchants.isEmpty()) {
            targets.addAll(searchEnchants);
        }

        // 2. Set Pinnato nell'HUD
        String pinnedId = ActiveSessionManager.getPinnedSetId();
        if (pinnedId != null) {
            CustomSet set = SavedSetsManager.getSetById(pinnedId);
            if (set != null && set.enchantments != null && !set.enchantments.isEmpty()) {
                targets.addAll(set.enchantments);
            }
        }

        return targets;
    }

    private static boolean matchesAnyTargetEnchant(ItemStack stack, Set<String> targetEnchants) {
        if (stack == null || stack.isEmpty() || targetEnchants == null || targetEnchants.isEmpty()) return false;

        // 1. STORED_ENCHANTMENTS (libri incantati standard)
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            String id = (ee != null) ? ee.id : rawId;
            if (isEnchantInSet(id, targetEnchants)) return true;
        }

        // 2. ENCHANTMENTS (armi, armature e attrezzi già incantati)
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            String id = (ee != null) ? ee.id : rawId;
            if (isEnchantInSet(id, targetEnchants)) return true;
        }

        // 3. Nome oggetto (display name in /enchgui o /ah)
        String hoverName = stack.getHoverName().getString();
        if (checkLineForMatch(hoverName, targetEnchants)) {
            return true;
        }

        // 4. Righe Lore
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            String line = c.getString();
            if (checkLineForMatch(line, targetEnchants)) {
                return true;
            }
        }

        return false;
    }

    private static boolean checkLineForMatch(String text, Set<String> targetEnchants) {
        if (text == null || text.isEmpty()) return false;

        LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(text);
        if (match != null && match.entry != null) {
            if (isEnchantInSet(match.entry.id, targetEnchants)) {
                return true;
            }
        }

        // Rimuove eventuali prefissi server ("Incantesimo:", "Incanto:", "Enchant:")
        String stripped = text.replaceAll("(?i)§[0-9a-fk-orx]", "")
                .replaceAll("(?i)^(?:incantesimo|incanto|enchantment|enchant):?\\s*", "").trim();
        if (!stripped.isEmpty() && !stripped.equalsIgnoreCase(text)) {
            LitnessTooltipHandler.MatchedEnchantment matchStripped = LitnessTooltipHandler.matchEnchantment(stripped);
            if (matchStripped != null && matchStripped.entry != null) {
                if (isEnchantInSet(matchStripped.entry.id, targetEnchants)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isEnchantInSet(String enchantId, Set<String> targetEnchants) {
        if (enchantId == null || targetEnchants == null || targetEnchants.isEmpty()) return false;
        String cleanId = enchantId.trim().toLowerCase();
        for (String target : targetEnchants) {
            if (target == null) continue;
            String t = target.trim().toLowerCase();
            if (cleanId.equals(t)) return true;
            if (cleanId.replace('-', '_').equals(t.replace('-', '_'))) return true;
            if (cleanId.replace("defense", "defence").equals(t.replace("defense", "defence"))) return true;

            EnchantmentEntry targetEe = EnchantmentDatabase.getEnchantment(t);
            if (targetEe != null) {
                if (targetEe.id.equalsIgnoreCase(cleanId)) return true;
                if (cleanId.replace('-', '_').equalsIgnoreCase(targetEe.id.replace('-', '_'))) return true;
            }
        }
        return false;
    }
}

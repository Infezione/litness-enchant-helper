package com.litness.optimizer.mixin;

import com.litness.optimizer.calculator.AnvilOptimizer;
import com.litness.optimizer.data.ActiveSessionManager;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import com.litness.optimizer.data.InventoryTracker;
import com.litness.optimizer.data.SavedSetsManager;
import com.litness.optimizer.data.SavedSetsManager.CustomSet;
import com.litness.optimizer.gui.LitnessOptimizerScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(Hud.class)
public class HudMixin {

    private static class HudItemLine {
        final String text;
        final int color;

        HudItemLine(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    private static class RenderStateCache {
        String title;
        int titleColor;
        List<HudItemLine> lines = new ArrayList<>();
        int width;
        int height;
        boolean allDone;
    }

    private static RenderStateCache cachedHud = null;
    private static long lastCacheUpdate = 0;
    private static String lastPinnedId = null;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void litness_renderPinnedHud(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        Hud self = (Hud) (Object) this;
        if (self.isHidden()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.gui != null && mc.gui.screen() instanceof LitnessOptimizerScreen) return;

        String pinnedId = ActiveSessionManager.getPinnedSetId();
        if (pinnedId == null) {
            cachedHud = null;
            return;
        }

        long now = System.currentTimeMillis();
        if (cachedHud == null || !pinnedId.equals(lastPinnedId) || now - lastCacheUpdate > 250) {
            updateCache(pinnedId, mc.font);
            lastCacheUpdate = now;
            lastPinnedId = pinnedId;
        }

        if (cachedHud == null || cachedHud.lines.isEmpty()) return;

        int startX = 6;
        int startY = 6;
        int w = cachedHud.width;
        int h = cachedHud.height;

        // Sfondo dark-glass con bordi puliti
        extractor.fill(startX, startY, startX + w, startY + h, 0xCC101018);
        int stripeColor = cachedHud.allDone ? 0xFF10B981 : 0xFFF59E0B;
        extractor.fill(startX, startY, startX + 2, startY + h, stripeColor);
        extractor.outline(startX, startY, w, h, 0x553F3F46);

        // Intestazione set
        Font font = mc.font;
        extractor.text(font, cachedHud.title, startX + 6, startY + 5, cachedHud.titleColor, true);

        // Separatore orizzontale
        extractor.fill(startX + 4, startY + 16, startX + w - 4, startY + 17, 0x22FFFFFF);

        // Elementi incantesimi
        int curY = startY + 20;
        for (HudItemLine line : cachedHud.lines) {
            extractor.text(font, line.text, startX + 6, curY, line.color, true);
            curY += 10;
        }
    }

    private static void updateCache(String pinnedId, Font font) {
        CustomSet set = SavedSetsManager.getSetById(pinnedId);
        if (set == null || set.enchantments == null || set.enchantments.isEmpty()) {
            cachedHud = null;
            return;
        }

        Set<String> applied = InventoryTracker.getAppliedEnchantmentsForItem(set.itemKey);
        Map<String, Integer> bookMap = InventoryTracker.getBookEnchantments();

        RenderStateCache next = new RenderStateCache();
        int appliedCount = 0;
        int inInvCount = 0;
        int maxTextWidth = 0;

        for (String enchId : set.enchantments) {
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(enchId);
            String name = (ee != null ? ee.name : enchId);
            String base = AnvilOptimizer.formatEnchantName(name, (ee != null && ee.maxLevel > 1) ? ee.maxLevel : 0);

            boolean isApplied = InventoryTracker.matchesApplied(enchId, applied);
            if (isApplied) {
                appliedCount++;
                String txt = "✔ " + base;
                next.lines.add(new HudItemLine(txt, 0xFFF87171));
                maxTextWidth = Math.max(maxTextWidth, font.width(txt));
            } else {
                boolean hasBook = InventoryTracker.matchesEnchant(enchId, bookMap);
                if (hasBook) {
                    inInvCount++;
                    String txt = "✔ " + base;
                    next.lines.add(new HudItemLine(txt, 0xFF34D399));
                    maxTextWidth = Math.max(maxTextWidth, font.width(txt));
                } else {
                    String txt = "• " + base;
                    next.lines.add(new HudItemLine(txt, 0xFFA1A1AA));
                    maxTextWidth = Math.max(maxTextWidth, font.width(txt));
                }
            }
        }

        int total = set.enchantments.size();
        int completed = appliedCount + inInvCount;
        next.allDone = (appliedCount == total);

        if (next.allDone) {
            next.title = "⚡ " + set.name + " ✔ COMPLETO";
            next.titleColor = 0xFF34D399;
        } else {
            next.title = "⚡ " + set.name + " (" + completed + "/" + total + ")";
            next.titleColor = 0xFFFDE047;
        }

        maxTextWidth = Math.max(maxTextWidth, font.width(next.title));
        next.width = Math.max(120, maxTextWidth + 12);
        next.height = 20 + (next.lines.size() * 10) + 4;

        cachedHud = next;
    }
}
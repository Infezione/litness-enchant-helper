package com.litness.optimizer.gui;

import com.litness.optimizer.calculator.AnvilOptimizer;
import com.litness.optimizer.calculator.AnvilOptimizer.EnchantApplied;
import com.litness.optimizer.calculator.AnvilOptimizer.MergeStep;
import com.litness.optimizer.calculator.AnvilOptimizer.Solution;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class AnvilTooltipState {

    public static MergeStep hoveredNextStep = null;
    public static List<EnchantApplied> hoveredCurrentEnchants = null;
    public static boolean hoveredInfoModal = false;
    public static String hoveredGhostSlotItemName = null;
    public static Solution cachedSolution = null;
    public static String cachedItemKey = "sword";

    public static void clearHover() {
        hoveredNextStep = null;
        hoveredCurrentEnchants = null;
        hoveredInfoModal = false;
        hoveredGhostSlotItemName = null;
    }

    public static void renderTopLayer(GuiGraphicsExtractor extractor, AnvilScreen anvil, int mouseX, int mouseY) {
        if (hoveredInfoModal) {
            renderInfoModalTooltip(extractor, anvil);
        } else if (hoveredGhostSlotItemName != null) {
            renderGhostSlotTooltip(extractor, anvil, hoveredGhostSlotItemName, mouseX, mouseY);
        } else if (hoveredNextStep != null) {
            renderStepTooltip(extractor, anvil, hoveredNextStep);
        } else if (hoveredCurrentEnchants != null && !hoveredCurrentEnchants.isEmpty()) {
            renderCurrentEnchantsTooltip(extractor, anvil, hoveredCurrentEnchants);
        }
    }

    private static void renderGhostSlotTooltip(GuiGraphicsExtractor extractor, AnvilScreen anvil, String ghostName, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        List<Component> tipLines = new ArrayList<>();
        tipLines.add(Component.literal("§6💡 Slot 1 (Oggetto Base)"));
        tipLines.add(Component.literal("§eTrascina qui " + ghostName + " per"));
        tipLines.add(Component.literal("§eavviare il calcolo automatico."));
        tipLines.add(Component.empty());
        tipLines.add(Component.literal("§8• L'assistente leggerà enchant e penalità"));
        tipLines.add(Component.literal("§8  e guiderà ogni singolo passo nell'incudine!"));

        int maxW = 0;
        for (Component c : tipLines) {
            maxW = Math.max(maxW, font.width(c));
        }
        int tipW = maxW + 16;
        int tipH = tipLines.size() * 11 + 10;

        int tipX = mouseX + 12;
        int tipY = mouseY - 12;

        if (tipX + tipW > anvil.width - 4) {
            tipX = mouseX - tipW - 8;
        }
        if (tipY + tipH > anvil.height - 4) {
            tipY = anvil.height - tipH - 4;
        }
        if (tipY < 4) tipY = 4;
        if (tipX < 4) tipX = 4;

        extractor.outline(tipX - 1, tipY - 1, tipW + 2, tipH + 2, 0xFF000000);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xF80E111A);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + 2, 0xFFF59E0B);
        extractor.outline(tipX, tipY, tipW, tipH, 0xFFD97706);

        int textY = tipY + 6;
        for (Component line : tipLines) {
            if (line.getString().isEmpty()) {
                textY += 4;
            } else {
                extractor.text(font, line, tipX + 8, textY, 0xFFFFFFFF, true);
                textY += 11;
            }
        }
    }

    private static void renderInfoModalTooltip(GuiGraphicsExtractor extractor, AnvilScreen anvil) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        List<Component> tipLines = new ArrayList<>();
        tipLines.add(Component.literal("§6⚡ ASSISTENTE DINAMICO (GPS LIVE)"));
        tipLines.add(Component.literal("§7Calcola la mossa migliore basandosi sui libri attualmente"));
        tipLines.add(Component.literal("§7presenti nel tuo inventario e sulla penalità effettiva"));
        tipLines.add(Component.literal("§7dell'oggetto nello slot 1."));
        tipLines.add(Component.empty());
        tipLines.add(Component.literal("§e❓ Perché i passi possono differire dalla Guida [N]?"));
        tipLines.add(Component.literal("§8• La Guida [N] mostra l'albero matematico a tavolino"));
        tipLines.add(Component.literal("§8  (tutti i libri a penalità 0)."));
        tipLines.add(Component.literal("§8• Questo assistente si adatta dinamicamente:"));
        tipLines.add(Component.literal("§8  se ti mancano libri o usi libri con penalità pregresse,"));
        tipLines.add(Component.literal("§8  ricalcola il percorso migliore in tempo reale"));
        tipLines.add(Component.literal("§8  per evitare 'Troppo Costoso!'."));

        int maxW = 0;
        for (Component c : tipLines) {
            maxW = Math.max(maxW, font.width(c));
        }
        int tipW = maxW + 16;
        int tipH = tipLines.size() * 11 + 10;

        int tipX = anvil.width / 2 - tipW / 2;
        int tipY = anvil.height / 2 - tipH / 2;

        extractor.outline(tipX - 1, tipY - 1, tipW + 2, tipH + 2, 0xFF000000);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xF80E111A);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + 2, 0xFFF59E0B);
        extractor.outline(tipX, tipY, tipW, tipH, 0xFFD97706);

        int textY = tipY + 6;
        for (Component line : tipLines) {
            if (line.getString().isEmpty()) {
                textY += 4;
            } else {
                extractor.text(font, line, tipX + 8, textY, 0xFFFFFFFF, true);
                textY += 11;
            }
        }
    }

    private static void renderStepTooltip(GuiGraphicsExtractor extractor, AnvilScreen anvil, MergeStep step) {
        if (step == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        List<Component> tipLines = new ArrayList<>();
        int totalSteps = cachedSolution != null ? cachedSolution.steps.size() : 1;

        if (step.isBookToBook) {
            tipLines.add(Component.literal("§6🔨 PASSO 1/" + totalSteps + ": UNIONE LIBRI TRA LORO"));
            tipLines.add(Component.literal("§7Unisci questi due libri prima di applicarli:"));
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§9📖 Slot 1: §f" + step.leftLabel));
            for (EnchantApplied ea : step.leftEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                String eName = AnvilOptimizer.formatEnchantName(ee != null ? ee.name : ea.id, ea.level);
                tipLines.add(Component.literal("   §b• " + eName));
            }
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§d📖 Slot 2: §f" + step.rightLabel));
            for (EnchantApplied ea : step.rightEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                String eName = AnvilOptimizer.formatEnchantName(ee != null ? ee.name : ea.id, ea.level);
                tipLines.add(Component.literal("   §d• " + eName));
            }
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§a➔ Risultato: §f" + step.resultLabel));
            String peakStr = cachedSolution != null ? " §7(Picco: §d" + cachedSolution.peakCost + "/39 liv.§7)" : "";
            tipLines.add(Component.literal("§6💰 Costo Incudine: §e" + step.cost + " liv. XP" + peakStr));
        } else {
            String targetName = anvil.getMenu().getSlot(0).hasItem() ? anvil.getMenu().getSlot(0).getItem().getHoverName().getString() : "Oggetto";
            int penalty = anvil.getMenu().getSlot(0).hasItem() ? anvil.getMenu().getSlot(0).getItem().getOrDefault(DataComponents.REPAIR_COST, 0) : 0;

            String actionName = "TEMPRA OGGETTO";
            String icon = "⚔";
            if ("pick".equals(cachedItemKey)) { actionName = "TEMPRA PICCONE"; icon = "⛏"; }
            else if ("sword".equals(cachedItemKey)) { actionName = "TEMPRA SPADA"; icon = "⚔"; }
            else if ("helmet".equals(cachedItemKey)) { actionName = "TEMPRA ELMO"; icon = "🪖"; }
            else if ("chest".equals(cachedItemKey)) { actionName = "TEMPRA CORAZZA"; icon = "🥋"; }
            else if ("legs".equals(cachedItemKey)) { actionName = "TEMPRA GAMBALI"; icon = "👖"; }
            else if ("boots".equals(cachedItemKey)) { actionName = "TEMPRA STIVALI"; icon = "👢"; }
            else if ("axe".equals(cachedItemKey)) { actionName = "TEMPRA ASCIA"; icon = "🪓"; }
            else if ("bow".equals(cachedItemKey)) { actionName = "TEMPRA ARCO"; icon = "🏹"; }

            tipLines.add(Component.literal("§a" + icon + " PASSO 1/" + totalSteps + ": " + actionName));
            tipLines.add(Component.literal("§7Applica questo libro sull'oggetto nell'incudine:"));
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§eSlot 1 (Oggetto): §f" + targetName + " §7(Penalità: +" + penalty + " liv.)"));
            tipLines.add(Component.literal("§eSlot 2 (Libro): §f" + step.rightLabel));
            for (EnchantApplied ea : step.rightEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                String eName = AnvilOptimizer.formatEnchantName(ee != null ? ee.name : ea.id, ea.level);
                tipLines.add(Component.literal("   §a• " + eName));
            }
            tipLines.add(Component.empty());

            String peakStr = cachedSolution != null ? " §7(Picco: §d" + cachedSolution.peakCost + "/39 liv.§7)" : "";
            tipLines.add(Component.literal("§6💰 Costo Incudine: §e" + step.cost + " liv. XP" + peakStr));
        }

        tipLines.add(Component.literal("§8💡 Premi [N] o 'Guida Passi' in alto per l'intera sequenza"));

        // Calcolo dimensioni
        int maxLineW = 180;
        for (Component c : tipLines) {
            maxLineW = Math.max(maxLineW, font.width(c));
        }
        int tipW = maxLineW + 20;
        int tipH = tipLines.size() * 11 + 10;

        // POSIZIONE CENTRALE SU SCHERMO
        int tipX = (anvil.width - tipW) / 2;
        int tipY = (anvil.height - tipH) / 2;

        // Ombra esterna scura per stacco netto sopra qualsiasi elemento
        extractor.outline(tipX - 1, tipY - 1, tipW + 2, tipH + 2, 0xFF000000);
        // Sfondo 100% solido e opaco per coprire totalmente slot, item o hotbar sottostanti
        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xFF0E111A);
        // Barra accento superiore colorata (ambra per book-to-book, smeraldo per tempra oggetto)
        int stripeColor = step.isBookToBook ? 0xFFF59E0B : 0xFF10B981;
        extractor.fill(tipX, tipY, tipX + tipW, tipY + 2, stripeColor);
        // Bordo solido di 1px
        int borderColor = step.isBookToBook ? 0xFFB45309 : 0xFF059669;
        extractor.outline(tipX, tipY, tipW, tipH, borderColor);

        // Rendering delle righe formattate
        int lineY = tipY + 6;
        for (Component line : tipLines) {
            if (line.getString().isEmpty()) {
                lineY += 4;
            } else {
                extractor.text(font, line, tipX + 10, lineY, 0xFFFFFFFF, true);
                lineY += 11;
            }
        }
    }

    private static void renderCurrentEnchantsTooltip(GuiGraphicsExtractor extractor, AnvilScreen anvil, List<EnchantApplied> enchants) {
        if (enchants == null || enchants.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        List<Component> tipLines = new ArrayList<>();
        tipLines.add(Component.literal("§b✨ Incantesimi Attuali sull'Oggetto:"));
        tipLines.add(Component.empty());

        for (EnchantApplied ea : enchants) {
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
            String name = AnvilOptimizer.formatEnchantName(ee != null ? ee.name : ea.id, ea.level);
            tipLines.add(Component.literal("   §a• " + name));
        }

        int maxLineW = 160;
        for (Component c : tipLines) {
            maxLineW = Math.max(maxLineW, font.width(c));
        }
        int tipW = maxLineW + 20;
        int tipH = tipLines.size() * 11 + 10;

        int tipX = (anvil.width - tipW) / 2;
        int tipY = (anvil.height - tipH) / 2;

        // Ombra esterna scura per stacco netto
        extractor.outline(tipX - 1, tipY - 1, tipW + 2, tipH + 2, 0xFF000000);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xFF0E111A);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + 2, 0xFF38BDF8);
        extractor.outline(tipX, tipY, tipW, tipH, 0xFF0284C7);

        int lineY = tipY + 6;
        for (Component line : tipLines) {
            if (line.getString().isEmpty()) {
                lineY += 4;
            } else {
                extractor.text(font, line, tipX + 10, lineY, 0xFFFFFFFF, true);
                lineY += 11;
            }
        }
    }
}

package com.litness.optimizer.gui;

import com.litness.optimizer.calculator.AnvilOptimizer;
import com.litness.optimizer.calculator.AnvilOptimizer.EnchantApplied;
import com.litness.optimizer.calculator.AnvilOptimizer.MergeStep;
import com.litness.optimizer.calculator.AnvilOptimizer.Solution;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import com.litness.optimizer.data.ItemTypeHelper;
import com.litness.optimizer.mixin.AnvilScreenMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import com.litness.optimizer.tooltip.LitnessTooltipHandler;

import java.util.*;

public class AnvilTooltipState {

    public static MergeStep hoveredNextStep = null;
    public static List<EnchantApplied> hoveredCurrentEnchants = null;
    public static boolean hoveredInfoModal = false;
    public static String hoveredGhostSlotItemName = null;
    public static com.litness.optimizer.calculator.MaxabilityAnalyzer.MaxabilityResult hoveredMaxabilityResult = null;
    public static Solution cachedSolution = null;
    public static String cachedItemKey = "sword";

    public static void clearHover() {
        hoveredNextStep = null;
        hoveredCurrentEnchants = null;
        hoveredInfoModal = false;
        hoveredGhostSlotItemName = null;
        hoveredMaxabilityResult = null;
    }

    public static void renderTopLayer(GuiGraphicsExtractor extractor, AnvilScreen anvil, int mouseX, int mouseY) {
        if (hoveredInfoModal) {
            renderInfoModalTooltip(extractor, anvil);
        } else if (hoveredMaxabilityResult != null) {
            renderMaxabilityTooltip(extractor, anvil, hoveredMaxabilityResult, mouseX, mouseY);
        } else if (hoveredGhostSlotItemName != null) {
            renderGhostSlotTooltip(extractor, anvil, hoveredGhostSlotItemName, mouseX, mouseY);
        } else if (hoveredNextStep != null) {
            renderStepTooltip(extractor, anvil, hoveredNextStep);
        } else if (hoveredCurrentEnchants != null && !hoveredCurrentEnchants.isEmpty()) {
            renderCurrentEnchantsTooltip(extractor, anvil, hoveredCurrentEnchants);
        }
    }

    public static class HighlightedSlot {
        public Slot slot;
        public int targetAnvilSlot; // 1 per Slot 1, 2 per Slot 2
        public String label;
        public String bookName;
    }

    public static List<HighlightedSlot> getTargetInventorySlots(AnvilScreen anvil) {
        List<HighlightedSlot> targets = new ArrayList<>();
        if (cachedSolution == null || cachedSolution.steps.isEmpty() || anvil == null || anvil.getMenu() == null) {
            return targets;
        }
        MergeStep step = cachedSolution.steps.get(0);
        ItemStack anvilSlot0 = anvil.getMenu().getSlot(0).getItem();
        ItemStack anvilSlot1 = anvil.getMenu().getSlot(1).getItem();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return targets;

        Set<Integer> usedIndices = new HashSet<>();

        if (step.isBookToBook) {
            boolean slot0HasLeft = matchesEnchantList(anvilSlot0, step.leftEnchants);
            boolean slot1HasRight = matchesEnchantList(anvilSlot1, step.rightEnchants);
            boolean slot0HasRight = matchesEnchantList(anvilSlot0, step.rightEnchants);
            boolean slot1HasLeft = matchesEnchantList(anvilSlot1, step.leftEnchants);

            boolean needLeft = !slot0HasLeft && !slot1HasLeft;
            boolean needRight = !slot0HasRight && !slot1HasRight;

            if (!needLeft && !needRight) return targets;

            if (needLeft) {
                for (Slot s : anvil.getMenu().slots) {
                    if (s.container != mc.player.getInventory()) continue;
                    ItemStack st = s.getItem();
                    if (st.isEmpty() || (!st.is(Items.ENCHANTED_BOOK) && !st.is(Items.BOOK))) continue;
                    if (matchesEnchantList(st, step.leftEnchants)) {
                        HighlightedSlot hs = new HighlightedSlot();
                        hs.slot = s;
                        hs.targetAnvilSlot = 1;
                        hs.label = "Devi inserire nello Slot 1";
                        hs.bookName = step.leftLabel;
                        targets.add(hs);
                        usedIndices.add(s.index);
                        break;
                    }
                }
            }

            if (needRight) {
                for (Slot s : anvil.getMenu().slots) {
                    if (s.container != mc.player.getInventory()) continue;
                    if (usedIndices.contains(s.index)) continue;
                    ItemStack st = s.getItem();
                    if (st.isEmpty() || (!st.is(Items.ENCHANTED_BOOK) && !st.is(Items.BOOK))) continue;
                    if (matchesEnchantList(st, step.rightEnchants)) {
                        HighlightedSlot hs = new HighlightedSlot();
                        hs.slot = s;
                        hs.targetAnvilSlot = 2;
                        hs.label = "Devi inserire nello Slot 2";
                        hs.bookName = step.rightLabel;
                        targets.add(hs);
                        break;
                    }
                }
            }
        } else {
            boolean slot0HasEquip = !anvilSlot0.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(anvilSlot0)
                    && ItemTypeHelper.detectItemKey(anvilSlot0).equalsIgnoreCase(cachedItemKey);
            boolean slot1HasBook = matchesEnchantList(anvilSlot1, step.rightEnchants);

            boolean needSlot1 = !slot0HasEquip;
            boolean needSlot2 = !slot1HasBook;

            if (!needSlot1 && !needSlot2) return targets;

            if (needSlot1) {
                for (Slot s : anvil.getMenu().slots) {
                    if (s.container != mc.player.getInventory()) continue;
                    ItemStack st = s.getItem();
                    if (st.isEmpty() || st.is(Items.ENCHANTED_BOOK) || st.is(Items.BOOK)) continue;
                    String itemKey = ItemTypeHelper.detectItemKey(st);
                    if (itemKey.equalsIgnoreCase(cachedItemKey)) {
                        HighlightedSlot hs = new HighlightedSlot();
                        hs.slot = s;
                        hs.targetAnvilSlot = 1;
                        hs.label = "Devi inserire nello Slot 1";
                        hs.bookName = st.getHoverName().getString();
                        targets.add(hs);
                        usedIndices.add(s.index);
                        break;
                    }
                }
            }

            if (needSlot2) {
                for (Slot s : anvil.getMenu().slots) {
                    if (s.container != mc.player.getInventory()) continue;
                    if (usedIndices.contains(s.index)) continue;
                    ItemStack st = s.getItem();
                    if (st.isEmpty() || (!st.is(Items.ENCHANTED_BOOK) && !st.is(Items.BOOK))) continue;
                    if (matchesEnchantList(st, step.rightEnchants)) {
                        HighlightedSlot hs = new HighlightedSlot();
                        hs.slot = s;
                        hs.targetAnvilSlot = 2;
                        hs.label = "Devi inserire nello Slot 2";
                        hs.bookName = step.rightLabel;
                        targets.add(hs);
                        break;
                    }
                }
            }
        }

        return targets;
    }

    public static List<EnchantApplied> extractAllEnchantments(ItemStack stack) {
        List<EnchantApplied> list = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return list;

        Set<String> seen = new HashSet<>();

        // 1. STORED_ENCHANTMENTS (libri incantati vanilla)
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(rawId, lvl);
            String id;
            if (vPlusId != null) {
                id = vPlusId;
            } else {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
                id = ee != null ? ee.id : rawId;
            }
            if (!id.isEmpty() && seen.add(id)) {
                list.add(new EnchantApplied(id, lvl));
            }
        }

        // 2. ENCHANTMENTS (armi, armature e attrezzi)
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(rawId, lvl);
            String id;
            if (vPlusId != null) {
                id = vPlusId;
            } else {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
                id = ee != null ? ee.id : rawId;
            }
            if (!id.isEmpty() && seen.add(id)) {
                list.add(new EnchantApplied(id, lvl));
            }
        }

        // 3. Da LORE (per incantesimi custom o inviati come testo dal server)
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(c.getString());
            if (match != null && match.entry != null) {
                int lvl = match.level > 0 ? match.level : match.entry.maxLevel;
                String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(match.entry.id, lvl);
                String id = vPlusId != null ? vPlusId : match.entry.id;
                if (!id.isEmpty() && seen.add(id)) {
                    list.add(new EnchantApplied(id, lvl));
                }
            }
        }

        // 4. Da CUSTOM_NAME (se il nome stesso del libro/item riporta l'incantesimo)
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(customName.getString());
            if (match != null && match.entry != null) {
                int lvl = match.level > 0 ? match.level : match.entry.maxLevel;
                String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(match.entry.id, lvl);
                String id = vPlusId != null ? vPlusId : match.entry.id;
                if (!id.isEmpty() && seen.add(id)) {
                    list.add(new EnchantApplied(id, lvl));
                }
            }
        }

        // 5. Fallback con getTooltipLines per eventuali tooltip sintetici
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                var lines = stack.getTooltipLines(Item.TooltipContext.EMPTY, mc.player, TooltipFlag.NORMAL);
                for (Component comp : lines) {
                    LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(comp.getString());
                    if (match != null && match.entry != null) {
                        int lvl = match.level > 0 ? match.level : match.entry.maxLevel;
                        String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(match.entry.id, lvl);
                        String id = vPlusId != null ? vPlusId : match.entry.id;
                        if (!id.isEmpty() && seen.add(id)) {
                            list.add(new EnchantApplied(id, lvl));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return list;
    }

    public static boolean matchesEnchantList(ItemStack stack, List<EnchantApplied> targetEnchants) {
        if (stack == null || stack.isEmpty() || targetEnchants == null || targetEnchants.isEmpty()) return false;
        List<EnchantApplied> stackEnchants = extractAllEnchantments(stack);
        if (stackEnchants.isEmpty() || stackEnchants.size() != targetEnchants.size()) return false;
        for (EnchantApplied target : targetEnchants) {
            boolean match = false;
            for (EnchantApplied se : stackEnchants) {
                if (se.id.equalsIgnoreCase(target.id) && se.level == target.level) {
                    match = true;
                    break;
                }
            }
            if (!match) return false;
        }
        return true;
    }

    public static void renderInventoryHighlightsAndBadges(GuiGraphicsExtractor extractor, AnvilScreen anvil, int leftPos, int topPos, int mouseX, int mouseY) {
        List<HighlightedSlot> targets = getTargetInventorySlots(anvil);
        if (targets.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        long time = System.currentTimeMillis();
        double wave = (Math.sin(time / 200.0) + 1.0) / 2.0; // 0.0 to 1.0
        int borderAlpha = (int)(0x90 + wave * 0x6F); // 144 to 255
        int cyanBorder = (borderAlpha << 24) | 0x38BDF8;
        int fillAlpha = (int)(0x35 + wave * 0x25); // 53 to 90
        int cyanFill = (fillAlpha << 24) | 0x0EA5E9;

        HighlightedSlot hoveredTarget = null;

        for (HighlightedSlot hs : targets) {
            int sx = leftPos + hs.slot.x;
            int sy = topPos + hs.slot.y;

            // 1. Evidenziazione luminosa celeste sullo slot
            extractor.fill(sx, sy, sx + 16, sy + 16, cyanFill);
            extractor.outline(sx - 1, sy - 1, 18, 18, cyanBorder);

            // 2. Targhetta fissa permanente visibile anche senza hover
            String tag = (hs.targetAnvilSlot == 1) ? "SLOT 1" : "SLOT 2";
            int tagW = font.width(tag) + 4;
            int tagH = 9;
            int tagX = sx + 8 - (tagW / 2);
            int tagY = sy - 6;

            extractor.fill(tagX - 1, tagY - 1, tagX + tagW + 1, tagY + tagH + 1, 0xEE0A0F1D);
            extractor.outline(tagX - 1, tagY - 1, tagW + 2, tagH + 2, 0xFF38BDF8);
            extractor.text(font, tag, tagX + 2, tagY + 1, 0xFF38BDF8, false);

            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                hoveredTarget = hs;
            }
        }

        // 3. Se il cursore è sopra lo slot, mostra lore esplicita
        if (hoveredTarget != null) {
            List<Component> tipLines = new ArrayList<>();
            tipLines.add(Component.literal("§b• Devi inserire questo nello Slot " + hoveredTarget.targetAnvilSlot + " dell'Incudine"));
            if (hoveredTarget.bookName != null && !hoveredTarget.bookName.isEmpty()) {
                tipLines.add(Component.literal("  §f" + hoveredTarget.bookName));
            }

            int tipW = 0;
            for (Component c : tipLines) {
                tipW = Math.max(tipW, font.width(c));
            }
            tipW += 16;
            int tipH = tipLines.size() * 11 + 8;

            int tipX = mouseX + 12;
            int tipY = mouseY - 12;
            if (tipX + tipW > anvil.width - 4) tipX = mouseX - tipW - 8;
            if (tipY + tipH > anvil.height - 4) tipY = anvil.height - tipH - 4;
            if (tipX < 4) tipX = 4;
            if (tipY < 4) tipY = 4;

            extractor.outline(tipX - 1, tipY - 1, tipW + 2, tipH + 2, 0xFF000000);
            extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xF80B132B);
            extractor.fill(tipX, tipY, tipX + tipW, tipY + 2, 0xFF38BDF8);
            extractor.outline(tipX, tipY, tipW, tipH, 0xFF0284C7);

            int ty = tipY + 5;
            for (Component line : tipLines) {
                extractor.text(font, line, tipX + 8, ty, 0xFFFFFFFF, true);
                ty += 11;
            }
        }
    }

    private static void renderGhostSlotTooltip(GuiGraphicsExtractor extractor, AnvilScreen anvil, String ghostName, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        List<Component> tipLines = new ArrayList<>();
        tipLines.add(Component.literal("§6• Slot 1 (Oggetto Base)"));
        tipLines.add(Component.literal("§eTrascina qui l'item desiderato"));
        tipLines.add(Component.literal("§eper iniziare la guida."));
        tipLines.add(Component.empty());
        tipLines.add(Component.literal("§8• Suggerito: §f" + ghostName));
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

    private static void renderMaxabilityTooltip(GuiGraphicsExtractor extractor, AnvilScreen anvil, com.litness.optimizer.calculator.MaxabilityAnalyzer.MaxabilityResult res, int mouseX, int mouseY) {
        if (res == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        List<Component> tipLines = new ArrayList<>();
        tipLines.add(Component.literal("§6• ANALISI MAXABILITÀ OGGETTO"));
        tipLines.add(Component.literal("§7Penalità attuale: §f+" + res.currentPenalty + " liv. §8(Lavori: " + res.currentWork + ")"));
        tipLines.add(Component.literal("§7Incantesimi presenti: §f" + res.currentEnchants + "/12"));
        tipLines.add(Component.empty());

        for (String line : res.details) {
            tipLines.add(Component.literal(line));
        }

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

        int headerColor = res.isFullyMaxable ? 0xFF10B981 : (res.status == com.litness.optimizer.calculator.MaxabilityAnalyzer.Status.PARTIALLY_MAXABLE ? 0xFFF59E0B : 0xFFEF4444);
        int borderColor = res.isFullyMaxable ? 0xFF059669 : (res.status == com.litness.optimizer.calculator.MaxabilityAnalyzer.Status.PARTIALLY_MAXABLE ? 0xFFD97706 : 0xFFDC2626);

        extractor.outline(tipX - 1, tipY - 1, tipW + 2, tipH + 2, 0xFF000000);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xF80E111A);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + 2, headerColor);
        extractor.outline(tipX, tipY, tipW, tipH, borderColor);

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
        tipLines.add(Component.literal("§6• ASSISTENTE DINAMICO (GPS LIVE)"));
        tipLines.add(Component.literal("§7Calcola la mossa migliore basandosi sui libri attualmente"));
        tipLines.add(Component.literal("§7presenti nel tuo inventario e sulla penalità effettiva"));
        tipLines.add(Component.literal("§7dell'oggetto nello slot 1."));
        tipLines.add(Component.empty());
        tipLines.add(Component.literal("§ePerché i passi possono differire dalla Guida [N]?"));
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
            tipLines.add(Component.literal("§6• PASSO 1/" + totalSteps + ": UNIONE LIBRI TRA LORO"));
            tipLines.add(Component.literal("§7Unisci questi due libri prima di applicarli:"));
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§9• Slot 1: §f" + step.leftLabel));
            for (EnchantApplied ea : step.leftEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                String eName = AnvilOptimizer.formatEnchantName(ee != null ? ee.name : ea.id, ea.level);
                tipLines.add(Component.literal("   §b• " + eName));
            }
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§d• Slot 2: §f" + step.rightLabel));
            for (EnchantApplied ea : step.rightEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                String eName = AnvilOptimizer.formatEnchantName(ee != null ? ee.name : ea.id, ea.level);
                tipLines.add(Component.literal("   §d• " + eName));
            }
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§a-> Risultato: §f" + step.resultLabel));
            String peakStr = cachedSolution != null ? " §7(Picco: §d" + cachedSolution.peakCost + "/39 liv.§7)" : "";
            tipLines.add(Component.literal("§6Costo Incudine: §e" + step.cost + " liv. XP" + peakStr));
        } else {
            String targetName = anvil.getMenu().getSlot(0).hasItem() ? anvil.getMenu().getSlot(0).getItem().getHoverName().getString() : "Oggetto";
            int penalty = anvil.getMenu().getSlot(0).hasItem() ? anvil.getMenu().getSlot(0).getItem().getOrDefault(DataComponents.REPAIR_COST, 0) : 0;

            String actionName = "TEMPRA OGGETTO";
            if ("pick".equals(cachedItemKey)) actionName = "TEMPRA PICCONE";
            else if ("sword".equals(cachedItemKey)) actionName = "TEMPRA SPADA";
            else if ("helmet".equals(cachedItemKey)) actionName = "TEMPRA ELMO";
            else if ("chest".equals(cachedItemKey)) actionName = "TEMPRA CORAZZA";
            else if ("legs".equals(cachedItemKey)) actionName = "TEMPRA GAMBALI";
            else if ("boots".equals(cachedItemKey)) actionName = "TEMPRA STIVALI";
            else if ("axe".equals(cachedItemKey)) actionName = "TEMPRA ASCIA";
            else if ("bow".equals(cachedItemKey)) actionName = "TEMPRA ARCO";

            tipLines.add(Component.literal("§a• PASSO 1/" + totalSteps + ": " + actionName));
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
            tipLines.add(Component.literal("§6Costo Incudine: §e" + step.cost + " liv. XP" + peakStr));
        }

        tipLines.add(Component.literal("§8• Premi [N] o 'Guida Passi' in alto per l'intera sequenza"));

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
        tipLines.add(Component.literal("§b• Incantesimi Attuali sull'Oggetto:"));
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

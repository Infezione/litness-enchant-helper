package com.litness.optimizer.gui;

import com.litness.optimizer.calculator.AnvilOptimizer;
import com.litness.optimizer.calculator.AnvilOptimizer.EnchantApplied;
import com.litness.optimizer.calculator.AnvilOptimizer.MergeStep;
import com.litness.optimizer.calculator.AnvilOptimizer.Solution;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import com.litness.optimizer.data.ItemTypeHelper;
import com.litness.optimizer.data.LitnessI18n;
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

    public static ItemStack explicitTargetEquip = ItemStack.EMPTY;

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
                    if (st.isEmpty() || ItemTypeHelper.isContainerOrShulker(st) || (!st.is(Items.ENCHANTED_BOOK) && !st.is(Items.BOOK))) continue;
                    if (matchesEnchantList(st, step.leftEnchants)) {
                        HighlightedSlot hs = new HighlightedSlot();
                        hs.slot = s;
                        hs.targetAnvilSlot = 1;
                        hs.label = com.litness.optimizer.data.LitnessI18n.t("must_put_slot_1");
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
                    if (st.isEmpty() || ItemTypeHelper.isContainerOrShulker(st) || (!st.is(Items.ENCHANTED_BOOK) && !st.is(Items.BOOK))) continue;
                    if (matchesEnchantList(st, step.rightEnchants)) {
                        HighlightedSlot hs = new HighlightedSlot();
                        hs.slot = s;
                        hs.targetAnvilSlot = 2;
                        hs.label = com.litness.optimizer.data.LitnessI18n.t("must_put_slot_2");
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
                Slot bestSlot = null;
                int minEnchants = 999;
                String bestName = "";

                for (Slot s : anvil.getMenu().slots) {
                    if (s.container != mc.player.getInventory()) continue;
                    ItemStack st = s.getItem();
                    if (st.isEmpty() || ItemTypeHelper.isContainerOrShulker(st) || st.is(Items.ENCHANTED_BOOK) || st.is(Items.BOOK)) continue;
                    if (!ItemTypeHelper.isEnchantableWeaponOrArmor(st)) continue;
                    if (isItemMaxed(st)) continue; // Salta SEMPRE item già al limite massimo di 12 incanti!

                    String itemKey = ItemTypeHelper.detectItemKey(st);
                    if (itemKey != null && !itemKey.isEmpty() && itemKey.equalsIgnoreCase(cachedItemKey)) {
                        // Se c'è un equipaggiamento target esplicito (es. Diamond Sword), priorità assoluta allo stesso Item!
                        if (!explicitTargetEquip.isEmpty() && st.getItem() == explicitTargetEquip.getItem()) {
                            bestSlot = s;
                            bestName = st.getHoverName().getString();
                            break;
                        }
                        int ec = extractAllEnchantments(st).size();
                        if (ec < minEnchants) {
                            minEnchants = ec;
                            bestSlot = s;
                            bestName = st.getHoverName().getString();
                        }
                    }
                }

                if (bestSlot != null) {
                    HighlightedSlot hs = new HighlightedSlot();
                    hs.slot = bestSlot;
                    hs.targetAnvilSlot = 1;
                    hs.label = com.litness.optimizer.data.LitnessI18n.t("must_put_slot_1");
                    hs.bookName = bestName;
                    targets.add(hs);
                    usedIndices.add(bestSlot.index);
                }
            }

            if (needSlot2) {
                for (Slot s : anvil.getMenu().slots) {
                    if (s.container != mc.player.getInventory()) continue;
                    if (usedIndices.contains(s.index)) continue;
                    ItemStack st = s.getItem();
                    if (st.isEmpty() || ItemTypeHelper.isContainerOrShulker(st) || (!st.is(Items.ENCHANTED_BOOK) && !st.is(Items.BOOK))) continue;
                    if (matchesEnchantList(st, step.rightEnchants)) {
                        HighlightedSlot hs = new HighlightedSlot();
                        hs.slot = s;
                        hs.targetAnvilSlot = 2;
                        hs.label = com.litness.optimizer.data.LitnessI18n.t("must_put_slot_2");
                        hs.bookName = step.rightLabel;
                        targets.add(hs);
                        break;
                    }
                }
            }
        }

        return targets;
    }

    private static final Map<ItemStack, List<EnchantApplied>> ENCHANT_CACHE = new WeakHashMap<>();

    public static boolean isItemMaxed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            String clean = c.getString().replaceAll("(?i)§[0-9a-fk-orx]", "").trim();
            if (clean.contains("12/12")) {
                return true;
            }
        }
        return extractAllEnchantments(stack).size() >= 12;
    }

    public static boolean isSameEnchant(String id1, String id2) {
        if (id1 == null || id2 == null) return false;
        if (id1.equalsIgnoreCase(id2)) return true;
        String n1 = id1.trim().toLowerCase().replace('_', '-');
        String n2 = id2.trim().toLowerCase().replace('_', '-');
        if (n1.equals(n2)) return true;

        String b1 = LitnessI18n.getBaseId(n1);
        String b2 = LitnessI18n.getBaseId(n2);
        if (!b1.isEmpty() && !b2.isEmpty()) {
            if (b1.replace('_', '-').equalsIgnoreCase(b2.replace('_', '-'))) return true;
        }
        if (b1.equalsIgnoreCase(n2) || b2.equalsIgnoreCase(n1)) return true;
        return false;
    }

    public static List<EnchantApplied> extractAllEnchantments(ItemStack stack) {
        if (stack == null || stack.isEmpty() || ItemTypeHelper.isContainerOrShulker(stack)) return Collections.emptyList();

        List<EnchantApplied> cached = ENCHANT_CACHE.get(stack);
        if (cached != null) return cached;

        List<EnchantApplied> list = new ArrayList<>();
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

        // 3. Da LORE (per incantesimi custom o inviati come testo dal server, supporta virgole)
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            List<LitnessTooltipHandler.MatchedEnchantment> matches = LitnessTooltipHandler.matchAllEnchantmentsFromLine(c.getString());
            for (LitnessTooltipHandler.MatchedEnchantment match : matches) {
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

        // 4. Da CUSTOM_NAME (se il nome stesso del libro/item riporta l'incantesimo)
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            List<LitnessTooltipHandler.MatchedEnchantment> matches = LitnessTooltipHandler.matchAllEnchantmentsFromLine(customName.getString());
            for (LitnessTooltipHandler.MatchedEnchantment match : matches) {
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

        ENCHANT_CACHE.put(stack, list);
        return list;
    }

    public static boolean matchesEnchantList(ItemStack stack, List<EnchantApplied> targetEnchants) {
        if (stack == null || stack.isEmpty() || ItemTypeHelper.isContainerOrShulker(stack) || targetEnchants == null || targetEnchants.isEmpty()) return false;
        List<EnchantApplied> stackEnchants = extractAllEnchantments(stack);
        if (stackEnchants.isEmpty()) return false;

        for (EnchantApplied target : targetEnchants) {
            boolean match = false;
            for (EnchantApplied se : stackEnchants) {
                if (isSameEnchant(se.id, target.id) && (target.level <= 0 || se.level >= target.level)) {
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
            boolean isSlot1 = (hs.targetAnvilSlot == 1);

            int slotBorder = isSlot1 ? ((borderAlpha << 24) | 0x38BDF8) : ((borderAlpha << 24) | 0xC084FC);
            int slotFill = isSlot1 ? ((fillAlpha << 24) | 0x0EA5E9) : ((fillAlpha << 24) | 0xA855F7);

            // 1. Evidenziazione morbida sullo slot (Ciano per Slot 1, Viola per Slot 2)
            extractor.fill(sx, sy, sx + 16, sy + 16, slotFill);
            extractor.outline(sx - 1, sy - 1, 18, 18, slotBorder);

            // 2. Mini-badge ultra-compatto soft [1] / [2] all'angolo superiore (massima leggibilità, mai sovrapposto)
            String tag = isSlot1 ? "1" : "2";
            int tagW = 8;
            int tagH = 8;
            int tagX = sx + 1;
            int tagY = sy + 1;

            int pillBg = isSlot1 ? 0xF00369A1 : 0xF06D28D9;
            int pillBorder = isSlot1 ? 0xFF38BDF8 : 0xFFC084FC;

            extractor.fill(tagX, tagY, tagX + tagW, tagY + tagH, pillBg);
            extractor.outline(tagX, tagY, tagW, tagH, pillBorder);
            extractor.centeredText(font, tag, tagX + (tagW / 2), tagY, 0xFFFFFFFF);

            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                hoveredTarget = hs;
            }
        }

        // 3. Se il cursore è sopra lo slot, mostra lore morbida e discreta
        if (hoveredTarget != null) {
            List<Component> tipLines = new ArrayList<>();
            boolean isSlot1 = (hoveredTarget.targetAnvilSlot == 1);
            String itemDesc = (hoveredTarget.bookName != null && !hoveredTarget.bookName.isEmpty()) 
                    ? hoveredTarget.bookName 
                    : (isSlot1 ? "Oggetto Base" : "Libro");
            
            if (isSlot1) {
                tipLines.add(Component.literal("§7Slot 1 • §b" + itemDesc));
            } else {
                tipLines.add(Component.literal("§7Slot 2 • §d" + itemDesc));
            }

            int tipW = 0;
            for (Component c : tipLines) {
                tipW = Math.max(tipW, font.width(c));
            }
            tipW += 12;
            int tipH = tipLines.size() * 10 + 6;

            int tipX = mouseX + 10;
            int tipY = mouseY - 10;
            if (tipX + tipW > anvil.width - 4) tipX = mouseX - tipW - 6;
            if (tipY + tipH > anvil.height - 4) tipY = anvil.height - tipH - 4;
            if (tipX < 4) tipX = 4;
            if (tipY < 4) tipY = 4;

            extractor.outline(tipX - 1, tipY - 1, tipW + 2, tipH + 2, 0x80000000);
            extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xEA0D111D);
            extractor.outline(tipX, tipY, tipW, tipH, isSlot1 ? 0x8038BDF8 : 0x80C084FC);

            int ty = tipY + 4;
            for (Component line : tipLines) {
                extractor.text(font, line, tipX + 6, ty, 0xFFFFFFFF, false);
                ty += 10;
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
                String eName = AnvilOptimizer.formatEnchantName(ee != null ? com.litness.optimizer.data.LitnessI18n.getEnchantName(ee) : ea.id, ea.level);
                tipLines.add(Component.literal("   §b• " + eName));
            }
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§d• Slot 2: §f" + step.rightLabel));
            for (EnchantApplied ea : step.rightEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                String eName = AnvilOptimizer.formatEnchantName(ee != null ? com.litness.optimizer.data.LitnessI18n.getEnchantName(ee) : ea.id, ea.level);
                tipLines.add(Component.literal("   §d• " + eName));
            }
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§a-> " + com.litness.optimizer.data.LitnessI18n.t("result_label") + " §f" + step.resultLabel));
            String peakStr = cachedSolution != null ? " §7(" + com.litness.optimizer.data.LitnessI18n.t("peak_cost") + ": §d" + cachedSolution.peakCost + "/39 liv.§7)" : "";
            tipLines.add(Component.literal("§6" + com.litness.optimizer.data.LitnessI18n.t("cost_xp") + "§e" + step.cost + " liv. XP" + peakStr));
        } else {
            String targetName = anvil.getMenu().getSlot(0).hasItem() ? anvil.getMenu().getSlot(0).getItem().getHoverName().getString() : com.litness.optimizer.data.LitnessI18n.getItemPieceName(cachedItemKey);
            int penalty = anvil.getMenu().getSlot(0).hasItem() ? anvil.getMenu().getSlot(0).getItem().getOrDefault(DataComponents.REPAIR_COST, 0) : 0;

            String actionName = com.litness.optimizer.data.LitnessI18n.getActionName(cachedItemKey);

            tipLines.add(Component.literal("§a• " + com.litness.optimizer.data.LitnessI18n.t("step_prefix") + "1/" + totalSteps + ": " + actionName));
            tipLines.add(Component.literal("§7" + com.litness.optimizer.data.LitnessI18n.t("temper_equip") + targetName + ":"));
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§eSlot 1: §f" + targetName + " §7(" + com.litness.optimizer.data.LitnessI18n.t("penalty_plus") + penalty + com.litness.optimizer.data.LitnessI18n.t("levels") + ")"));
            tipLines.add(Component.literal("§eSlot 2: §f" + step.rightLabel));
            for (EnchantApplied ea : step.rightEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                String eName = AnvilOptimizer.formatEnchantName(ee != null ? com.litness.optimizer.data.LitnessI18n.getEnchantName(ee) : ea.id, ea.level);
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

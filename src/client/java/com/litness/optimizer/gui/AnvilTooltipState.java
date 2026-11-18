package com.litness.optimizer.gui;

import com.litness.optimizer.calculator.AnvilOptimizer;
import com.litness.optimizer.calculator.AnvilOptimizer.EnchantApplied;
import com.litness.optimizer.calculator.AnvilOptimizer.MergeStep;
import com.litness.optimizer.calculator.AnvilOptimizer.Solution;
import com.litness.optimizer.data.ActiveSessionManager;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import com.litness.optimizer.data.InventoryTracker;
import com.litness.optimizer.data.ItemTypeHelper;
import com.litness.optimizer.data.LitnessI18n;
import com.litness.optimizer.data.ModConfig;
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
    public static ItemStack savedSelectedEquipment = ItemStack.EMPTY;
    public static int rightPanelScrollOffset = 0;
    public static int rightPanelMaxScroll = 0;
    public static EnchantmentEntry hoveredChecklistEnchant = null;

    public static int leftSideX = -9999, leftSideY = -9999, leftSideW = 0, leftSideH = 0;
    public static int rightSideX = -9999, rightSideY = -9999, rightSideW = 0, rightSideH = 0;
    public static int wellX = -9999, wellY = -9999, wellW = 0, wellH = 0;
    public static int calc12BtnX = -9999, calc12BtnY = -9999, calc12BtnW = 0, calc12BtnH = 0;
    public static boolean calc12BtnVisible = false;

    public static boolean isAnvilCleared = false;
    public static ItemStack clearedItemInstance = ItemStack.EMPTY;
    public static int resetBtnX = -9999, resetBtnY = -9999, resetBtnW = 0, resetBtnH = 0;
    public static boolean resetBtnVisible = false;

    public static boolean isInsideResetBtn(double mouseX, double mouseY) {
        return resetBtnVisible && resetBtnW > 0 && resetBtnH > 0
                && mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW
                && mouseY >= resetBtnY && mouseY <= resetBtnY + resetBtnH;
    }

    public static void resetAnvilEquipment(AnvilScreen anvil) {
        isAnvilCleared = true;
        clearedItemInstance = ItemStack.EMPTY;
        savedSelectedEquipment = ItemStack.EMPTY;
        explicitTargetEquip = ItemStack.EMPTY;
        cachedSolution = null;
        hoveredNextStep = null;
        hoveredCurrentEnchants = null;
        hoveredMaxabilityResult = null;
        hoveredChecklistEnchant = null;
        ActiveSessionManager.clearAnvilEquipment();
    }

    public static boolean isInsideCalc12Btn(double mouseX, double mouseY) {
        return calc12BtnVisible && calc12BtnW > 0 && calc12BtnH > 0
                && mouseX >= calc12BtnX && mouseX <= calc12BtnX + calc12BtnW
                && mouseY >= calc12BtnY && mouseY <= calc12BtnY + calc12BtnH;
    }

    public static void clearHover() {
        hoveredNextStep = null;
        hoveredCurrentEnchants = null;
        hoveredInfoModal = false;
        hoveredGhostSlotItemName = null;
        hoveredMaxabilityResult = null;
        hoveredChecklistEnchant = null;
        calc12BtnVisible = false;
        resetBtnVisible = false;
    }

    public static boolean isInsideSidebar(double mouseX, double mouseY) {
        if (leftSideW > 0 && leftSideH > 0 && mouseX >= leftSideX && mouseX <= leftSideX + leftSideW && mouseY >= leftSideY && mouseY <= leftSideY + leftSideH) {
            return true;
        }
        if (rightSideW > 0 && rightSideH > 0 && mouseX >= rightSideX && mouseX <= rightSideX + rightSideW && mouseY >= rightSideY && mouseY <= rightSideY + rightSideH) {
            return true;
        }
        return false;
    }

    public static boolean isInsideWell(double mouseX, double mouseY) {
        return wellW > 0 && wellH > 0 && mouseX >= wellX && mouseX <= wellX + wellW && mouseY >= wellY && mouseY <= wellY + wellH;
    }

    public static boolean isInsideRightPanel(double mouseX, double mouseY) {
        return rightSideW > 0 && rightSideH > 0 && mouseX >= rightSideX && mouseX <= rightSideX + rightSideW && mouseY >= rightSideY && mouseY <= rightSideY + rightSideH;
    }

    public static void selectAndSaveEquipment(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ItemTypeHelper.isEnchantableWeaponOrArmor(stack)) return;
        isAnvilCleared = false;
        clearedItemInstance = ItemStack.EMPTY;
        savedSelectedEquipment = stack.copy();
        explicitTargetEquip = stack.copy();
        String itemKey = ItemTypeHelper.detectItemKey(stack);
        String name = stack.getHoverName().getString();
        int penalty = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        ActiveSessionManager.saveAnvilEquipment(itemId, itemKey, name, penalty);
    }

    private static void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    public static boolean handleMouseClicked(AnvilScreen anvil, net.minecraft.world.inventory.AnvilMenu menu, net.minecraft.client.input.MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        int btn = event.button();
        if (btn == 0 || btn == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            // 1. Click sul pulsante "Azzera" nella sidebar sinistra
            if (isInsideResetBtn(mx, my)) {
                playClickSound();
                resetAnvilEquipment(anvil);
                return true;
            }

            // 2. Click sul pulsante "Calcola Guida 12 Incanti" nel pannello destro
            if (isInsideCalc12Btn(mx, my)) {
                playClickSound();
                open12EnchantGuide(anvil);
                return true;
            }

            // 3. Click / Rilascio sulla card / well sinistra ("a sinistra")
            if (isInsideWell(mx, my) || (isInsideSidebar(mx, my) && !isInsideRightPanel(mx, my))) {
                ItemStack carried = menu.getCarried();
                if (!carried.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(carried)) {
                    selectAndSaveEquipment(carried);
                    playClickSound();
                    return true;
                }
                // Se il cursore è vuoto: seleziona SOLO SE c'è un'arma o armatura nello slot 0 dell'incudine
                ItemStack s0 = menu.getSlot(0).getItem();
                if (!s0.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(s0)) {
                    selectAndSaveEquipment(s0);
                    playClickSound();
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public static boolean handleMouseReleased(AnvilScreen anvil, net.minecraft.world.inventory.AnvilMenu menu, net.minecraft.client.input.MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        int btn = event.button();
        if (btn == 0 || btn == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            if (isInsideWell(mx, my)) {
                ItemStack carried = menu.getCarried();
                if (!carried.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(carried)) {
                    selectAndSaveEquipment(carried);
                    playClickSound();
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean handleMouseScrolled(AnvilScreen anvil, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInsideRightPanel(mouseX, mouseY)) {
            if (rightPanelMaxScroll > 0) {
                rightPanelScrollOffset = Math.max(0, Math.min(rightPanelMaxScroll, rightPanelScrollOffset - (int)(verticalAmount * 16)));
                return true;
            }
        }
        return false;
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
        } else if (hoveredChecklistEnchant != null) {
            renderChecklistEnchantTooltip(extractor, anvil, hoveredChecklistEnchant, mouseX, mouseY);
        }
    }

    public static void renderChecklistEnchantTooltip(GuiGraphicsExtractor extractor, AnvilScreen anvil, EnchantmentEntry ee, int mouseX, int mouseY) {
        if (ee == null || anvil == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        List<Component> lines = new ArrayList<>();
        String name = LitnessI18n.getEnchantName(ee);
        String levelRoman = AnvilOptimizer.toRoman(ee.maxLevel > 1 ? ee.maxLevel : 1);
        lines.add(Component.literal("§b✦ " + name + " " + levelRoman));
        lines.add(Component.literal("§7" + LitnessI18n.t("anvil_label") + ee.getAnvilCostNumeric() + " " + LitnessI18n.t("levels").trim()));

        if (ee.description != null && !ee.description.isEmpty()) {
            List<String> wrapped = LitnessTooltipHandler.wrapText(ee.description, 36);
            for (String w : wrapped) {
                lines.add(Component.literal("  §8" + w));
            }
        }

        int tipW = 0;
        for (Component c : lines) {
            tipW = Math.max(tipW, font.width(c));
        }
        tipW += 12;
        int tipH = lines.size() * 10 + 6;

        int tipX = mouseX + 12;
        int tipY = mouseY - 10;
        // Limite destro: rispetta il pannello destro se visibile
        int rightLimit = (rightSideW > 0 && rightSideX > 0) ? rightSideX - 4 : anvil.width - 4;
        if (tipX + tipW > rightLimit) tipX = mouseX - tipW - 6;
        if (tipY + tipH > anvil.height - 4) tipY = anvil.height - tipH - 4;
        if (tipX < 4) tipX = 4;
        if (tipY < 4) tipY = 4;
        if (tipX + tipW > rightLimit) tipX = rightLimit - tipW;
        if (tipX < 4) tipX = 4;

        extractor.outline(tipX - 1, tipY - 1, tipW + 2, tipH + 2, 0x80000000);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xEA0D111D);
        extractor.outline(tipX, tipY, tipW, tipH, 0xFF38BDF8);

        int ty = tipY + 4;
        for (Component line : lines) {
            extractor.text(font, line, tipX + 6, ty, 0xFFFFFFFF, false);
            ty += 10;
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
            // Se lo slot 0 dell'incudine contiene già un'arma o armatura:
            if (!anvilSlot0.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(anvilSlot0)) {
                String slot0Key = ItemTypeHelper.detectItemKey(anvilSlot0);
                if (cachedItemKey != null && !cachedItemKey.isEmpty() && !cachedItemKey.equalsIgnoreCase(slot0Key)) {
                    // La soluzione memorizzata era per un item diverso (es. Spada invece di Corazza):
                    // non suggerire mai il vecchio item dall'inventario!
                    return targets;
                }
            }

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
                    // MAI selezionare slot armatura indossata (36-39) o offhand (40)
                    if (s.getContainerSlot() >= 36) continue;
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
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font font = mc.font;

        long time = System.currentTimeMillis();
        double wave = (Math.sin(time / 200.0) + 1.0) / 2.0; // 0.0 to 1.0
        int borderAlpha = (int)(0x90 + wave * 0x6F); // 144 to 255
        int fillAlpha = (int)(0x35 + wave * 0x25); // 53 to 90

        ModConfig.HighlightPreset preset = ModConfig.get().getCurrentPreset();
        HighlightedSlot hoveredTarget = null;
        Slot hoveredUsedBookSlot = null;
        int hoveredUsedBookPenalty = 0;

        List<HighlightedSlot> targets = getTargetInventorySlots(anvil);
        Set<Integer> targetSlotIndices = new HashSet<>();
        for (HighlightedSlot hs : targets) {
            if (hs.slot != null) {
                targetSlotIndices.add(hs.slot.index);
            }
        }

        // 1. Evidenziazione di TUTTI i libri con usura (>0 liv.) presenti nell'inventario del giocatore
        if (ModConfig.get().warnUsedBooksInAnvil && mc.player != null) {
            for (Slot s : anvil.getMenu().slots) {
                if (s.container != mc.player.getInventory()) continue;
                ItemStack st = s.getItem();
                if (st.isEmpty() || (!st.is(Items.ENCHANTED_BOOK) && !st.is(Items.BOOK))) continue;
                int rep = st.getOrDefault(DataComponents.REPAIR_COST, 0);
                if (rep == 0) rep = InventoryTracker.extractAnvilCostFromLore(st);
                if (rep > 0) {
                    int sx = leftPos + s.x;
                    int sy = topPos + s.y;

                    // Se non fa parte dei target primari, disegna il glow rosso/ambra di allerta
                    if (!targetSlotIndices.contains(s.index)) {
                        extractor.fill(sx, sy, sx + 16, sy + 16, 0x3CEF4444);
                        extractor.outline(sx - 1, sy - 1, 18, 18, 0xCCEF4444);
                    }

                    // Mini-badge rosso brillante "⚠" sempre visibile sopra OGNI libro con usura
                    int bW = 9;
                    int bH = 8;
                    int bx = sx + 16 - bW;
                    int by = sy;
                    extractor.fill(bx, by, bx + bW, by + bH, 0xEE7F1D1D);
                    extractor.outline(bx, by, bW, bH, 0xFFEF4444);
                    extractor.centeredText(font, "⚠", bx + (bW / 2), by, 0xFFFEE2E2);

                    if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                        hoveredUsedBookSlot = s;
                        hoveredUsedBookPenalty = rep;
                    }
                }
            }
        }

        // 2. Evidenziazione degli slot target primari (Slot 1 & Slot 2)
        for (HighlightedSlot hs : targets) {
            int sx = leftPos + hs.slot.x;
            int sy = topPos + hs.slot.y;
            boolean isSlot1 = (hs.targetAnvilSlot == 1);

            int baseColor = isSlot1 ? preset.slot1Color : preset.slot2Color;
            int baseBorder = isSlot1 ? preset.slot1Border : preset.slot2Border;
            int pillBg = isSlot1 ? preset.slot1PillBg : preset.slot2PillBg;
            int pillBorder = isSlot1 ? preset.slot1Border : preset.slot2Border;

            int slotBorder = (borderAlpha << 24) | (baseBorder & 0x00FFFFFF);
            int slotFill = (fillAlpha << 24) | (baseColor & 0x00FFFFFF);

            // Evidenziazione morbida sullo slot con palette personalizzabile
            extractor.fill(sx, sy, sx + 16, sy + 16, slotFill);
            extractor.outline(sx - 1, sy - 1, 18, 18, slotBorder);

            // Mini-badge ultra-compatto soft [1] / [2] all'angolo superiore sinistro
            String tag = isSlot1 ? "1" : "2";
            int tagW = 8;
            int tagH = 8;
            int tagX = sx + 1;
            int tagY = sy + 1;

            extractor.fill(tagX, tagY, tagX + tagW, tagY + tagH, pillBg);
            extractor.outline(tagX, tagY, tagW, tagH, pillBorder);
            extractor.centeredText(font, tag, tagX + (tagW / 2), tagY, 0xFFFFFFFF);

            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                hoveredTarget = hs;
            }
        }

        // 3. Se il cursore è sopra uno slot target o uno slot libro usato, mostra tooltip appropriato
        if (hoveredTarget != null) {
            List<Component> tipLines = new ArrayList<>();
            boolean isSlot1 = (hoveredTarget.targetAnvilSlot == 1);
            String itemDesc = (hoveredTarget.bookName != null && !hoveredTarget.bookName.isEmpty()) 
                    ? hoveredTarget.bookName 
                    : (isSlot1 ? "Oggetto Base" : "Libro");

            ItemStack hoveredStack = hoveredTarget.slot.getItem();
            int repCost = (!hoveredStack.isEmpty()) ? hoveredStack.getOrDefault(DataComponents.REPAIR_COST, 0) : 0;
            if (repCost == 0 && !hoveredStack.isEmpty()) repCost = InventoryTracker.extractAnvilCostFromLore(hoveredStack);
            
            if (isSlot1) {
                tipLines.add(Component.literal("§7Slot 1 • §f" + itemDesc));
            } else {
                tipLines.add(Component.literal("§7Slot 2 • §f" + itemDesc));
            }

            if (repCost > 0 && (hoveredStack.is(Items.ENCHANTED_BOOK) || hoveredStack.is(Items.BOOK)) && ModConfig.get().warnUsedBooksInAnvil) {
                tipLines.add(Component.literal("§c⚠ Usura incudine libro: +" + repCost + " liv."));
                tipLines.add(Component.literal("§e⚠ Attenzione: rischio blocco a 11 incanti!"));
            }

            renderTipPopup(extractor, anvil, tipLines, mouseX, mouseY, isSlot1 ? preset.slot1Border : preset.slot2Border);
        } else if (hoveredUsedBookSlot != null && ModConfig.get().warnUsedBooksInAnvil) {
            List<Component> tipLines = new ArrayList<>();
            tipLines.add(Component.literal("§c⚠ LIBRO USATO (Usura: +" + hoveredUsedBookPenalty + " liv.)"));
            tipLines.add(Component.literal("§eAttenzione: rischio blocco a 11 incanti!"));
            tipLines.add(Component.literal("§7Per creare un set 12/12 usa solo libri vergini (+0 liv.)."));
            renderTipPopup(extractor, anvil, tipLines, mouseX, mouseY, 0xFFEF4444);
        }
    }

    private static void renderTipPopup(GuiGraphicsExtractor extractor, AnvilScreen anvil, List<Component> tipLines, int mouseX, int mouseY, int borderColor) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int tipW = 0;
        for (Component c : tipLines) {
            tipW = Math.max(tipW, font.width(c));
        }
        tipW += 12;
        int tipH = tipLines.size() * 10 + 6;

        // Limite destro: se il pannello destro è visibile, non sforare su di esso
        int rightLimit = (rightSideW > 0 && rightSideX > 0) ? rightSideX - 4 : anvil.width - 4;

        int tipX = mouseX + 10;
        int tipY = mouseY - 10;
        if (tipX + tipW > rightLimit) tipX = mouseX - tipW - 6;
        if (tipY + tipH > anvil.height - 4) tipY = anvil.height - tipH - 4;
        if (tipX < 4) tipX = 4;
        if (tipY < 4) tipY = 4;
        // Clamp finale per sicurezza
        if (tipX + tipW > rightLimit) tipX = rightLimit - tipW;
        if (tipX < 4) tipX = 4;

        extractor.outline(tipX - 1, tipY - 1, tipW + 2, tipH + 2, 0x80000000);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xEA0D111D);
        extractor.outline(tipX, tipY, tipW, tipH, 0x80000000 | (borderColor & 0x00FFFFFF));

        int ty = tipY + 4;
        for (Component line : tipLines) {
            extractor.text(font, line, tipX + 6, ty, 0xFFFFFFFF, false);
            ty += 10;
        }
    }

    public static void open12EnchantGuide(AnvilScreen anvil) {
        if (anvil == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        ItemStack item = anvil.getMenu().getSlot(0).getItem();
        if (item.isEmpty()) item = explicitTargetEquip;
        if (item.isEmpty() && mc.player != null) item = mc.player.getMainHandItem();
        if (item.isEmpty() && savedSelectedEquipment != null) item = savedSelectedEquipment;
        if (item.isEmpty()) item = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);

        String itemKey = ItemTypeHelper.detectItemKey(item);
        if (itemKey == null || itemKey.isEmpty()) itemKey = "sword";

        List<EnchantApplied> existing = extractAllEnchantments(item);
        List<EnchantmentDatabase.EnchantmentEntry> candidates = com.litness.optimizer.calculator.MaxabilityAnalyzer.getApplicableCandidates(itemKey, existing);

        List<AnvilOptimizer.BookInput> rawBooks = new ArrayList<>();
        Set<String> targetIds = new HashSet<>();
        for (EnchantApplied ea : existing) {
            targetIds.add(ea.id.toLowerCase());
            targetIds.add(LitnessI18n.getBaseId(ea.id));
        }

        // Completa fino a 12 incanti senza conflitti
        for (EnchantmentDatabase.EnchantmentEntry ee : candidates) {
            if (targetIds.size() >= 12) break;
            String baseId = LitnessI18n.getBaseId(ee.id);
            if (targetIds.contains(ee.id.toLowerCase()) || targetIds.contains(baseId)) continue;

            boolean conf = false;
            for (String tid : targetIds) {
                EnchantmentDatabase.EnchantmentEntry tee = EnchantmentDatabase.getEnchantment(tid);
                if (EnchantmentDatabase.isEnchantPairConflicting(ee, tee)) {
                    conf = true;
                    break;
                }
            }
            if (!conf) {
                targetIds.add(ee.id.toLowerCase());
                targetIds.add(baseId);
                AnvilOptimizer.BookInput b = new AnvilOptimizer.BookInput(0);
                int lvl = ee.maxLevel > 0 ? ee.maxLevel : 1;
                b.enchantments.add(new EnchantApplied(ee.id, lvl));
                b.explicitCost = Math.max(1, ee.getAnvilCostNumeric());
                rawBooks.add(b);
            }
        }

        int currentPenalty = item.getOrDefault(DataComponents.REPAIR_COST, 0);
        int currentWork = 0;
        while ((1 << currentWork) - 1 < currentPenalty) currentWork++;

        EnchantmentDatabase.ItemEntry itemEntry = EnchantmentDatabase.getItem(itemKey);
        Solution solution = AnvilOptimizer.optimize(rawBooks, currentWork, itemEntry, existing);

        mc.setScreenAndShow(new LitnessOptimizerScreen(
            Component.literal("Litness Helper"),
            anvil,
            anvil.getMenu(),
            solution
        ));
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
        tipLines.add(Component.literal("§8• L'assistente leggera' enchant e penalita'"));
        tipLines.add(Component.literal("§8  e guidera' ogni singolo passo nell'incudine!"));

        int maxW = 0;
        for (Component c : tipLines) {
            maxW = Math.max(maxW, font.width(c));
        }
        int tipW = maxW + 16;
        int tipH = tipLines.size() * 11 + 10;

        // Limite destro: rispetta il pannello destro se visibile
        int rightLimit = (rightSideW > 0 && rightSideX > 0) ? rightSideX - 4 : anvil.width - 4;

        int tipX = mouseX + 12;
        int tipY = mouseY - 12;

        if (tipX + tipW > rightLimit) {
            tipX = mouseX - tipW - 8;
        }
        if (tipY + tipH > anvil.height - 4) {
            tipY = anvil.height - tipH - 4;
        }
        if (tipY < 4) tipY = 4;
        if (tipX < 4) tipX = 4;
        if (tipX + tipW > rightLimit) tipX = rightLimit - tipW;
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
        tipLines.add(Component.literal("§6• ANALISI MAXABILITA' OGGETTO"));
        tipLines.add(Component.literal("§7Penalita' attuale: §f+" + res.currentPenalty + " liv. §8(Lavori: " + res.currentWork + ")"));
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

        // Limite destro: rispetta il pannello destro se visibile
        int rightLimit = (rightSideW > 0 && rightSideX > 0) ? rightSideX - 4 : anvil.width - 4;

        int tipX = mouseX + 12;
        int tipY = mouseY - 12;

        if (tipX + tipW > rightLimit) {
            tipX = mouseX - tipW - 8;
        }
        if (tipY + tipH > anvil.height - 4) {
            tipY = anvil.height - tipH - 4;
        }
        if (tipY < 4) tipY = 4;
        if (tipX < 4) tipX = 4;
        if (tipX + tipW > rightLimit) tipX = rightLimit - tipW;
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
        tipLines.add(Component.literal("§7presenti nel tuo inventario e sulla penalita' effettiva"));
        tipLines.add(Component.literal("§7dell'oggetto nello slot 1."));
        tipLines.add(Component.empty());
        tipLines.add(Component.literal("§ePerche' i passi possono differire dalla Guida [N]?"));
        tipLines.add(Component.literal("§8• La Guida [N] mostra l'albero matematico a tavolino"));
        tipLines.add(Component.literal("§8  (tutti i libri a penalita' 0)."));
        tipLines.add(Component.literal("§8• Questo assistente si adatta dinamicamente:"));
        tipLines.add(Component.literal("§8  se ti mancano libri o usi libri con penalita' pregresse,"));
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

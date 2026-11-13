package com.litness.optimizer.data;

import com.litness.optimizer.tooltip.LitnessTooltipHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;

public class InventoryTracker {

    /**
     * Ritorna una mappa con ID incantesimo -> livello massimo trovato ESCLUSIVAMENTE sui LIBRI nell'inventario
     */
    public static Map<String, Integer> getBookEnchantments() {
        Map<String, Integer> map = new HashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return map;

        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && !ItemTypeHelper.isContainerOrShulker(stack) && (stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK))) {
                scanBookStack(stack, map);
            }
        }

        // Se c'è un'incudine aperta, scansiona solo lo slot 1 (il libro inserito dal giocatore per la fusione)
        if (mc.player.containerMenu instanceof AnvilMenu anvilMenu) {
            ItemStack slot1 = anvilMenu.getSlot(1).getItem();
            if (!slot1.isEmpty() && (slot1.is(Items.ENCHANTED_BOOK) || slot1.is(Items.BOOK))) {
                scanBookStack(slot1, map);
            }
        }

        return map;
    }

    public static int getRequiredLevel(String enchantId) {
        if (enchantId == null || enchantId.trim().isEmpty()) return 1;
        String clean = enchantId.trim().toLowerCase();
        int colon = clean.indexOf(':');
        if (colon > 0) {
            try {
                int parsed = Integer.parseInt(clean.substring(colon + 1).trim());
                if (parsed > 0) return parsed;
            } catch (Exception ignored) {}
        }
        if (clean.matches(".*_[0-9]+$")) {
            try {
                int parsed = Integer.parseInt(clean.substring(clean.lastIndexOf('_') + 1));
                if (parsed > 0) return parsed;
            } catch (Exception ignored) {}
        }
        if (clean.matches(".*-[0-9]+$")) {
            try {
                int parsed = Integer.parseInt(clean.substring(clean.lastIndexOf('-') + 1));
                if (parsed > 0) return parsed;
            } catch (Exception ignored) {}
        }
        EnchantmentDatabase.EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(clean);
        return (ee != null && ee.maxLevel > 0) ? ee.maxLevel : 1;
    }

    public static boolean matchesEnchant(String enchantId, Map<String, Integer> bookMap) {
        if (enchantId == null || bookMap == null || bookMap.isEmpty()) return false;
        String clean = enchantId.trim().toLowerCase();
        int reqLevel = getRequiredLevel(enchantId);

        // 1. Controllo esatto sull'ID
        Integer lvl = bookMap.get(clean);
        if (lvl != null && lvl >= reqLevel) return true;

        // 2. Controllo varianti trattino/underscore
        lvl = bookMap.get(clean.replace('-', '_'));
        if (lvl != null && lvl >= reqLevel) return true;
        lvl = bookMap.get(clean.replace('_', '-'));
        if (lvl != null && lvl >= reqLevel) return true;

        // 3. Controllo varianti defense/defence
        if (clean.contains("defense")) {
            lvl = bookMap.get(clean.replace("defense", "defence"));
            if (lvl != null && lvl >= reqLevel) return true;
        }
        if (clean.contains("defence")) {
            lvl = bookMap.get(clean.replace("defence", "defense"));
            if (lvl != null && lvl >= reqLevel) return true;
        }

        // 4. Controllo su base ID (es. unbreaking da unbreaking_5)
        String baseId = LitnessI18n.getBaseId(clean);
        if (!baseId.isEmpty() && !baseId.equalsIgnoreCase(clean)) {
            Integer baseLvl = bookMap.get(baseId);
            if (baseLvl != null && baseLvl >= reqLevel) return true;
        }

        return false;
    }

    public static boolean matchesApplied(String enchantId, Set<String> appliedSet) {
        if (enchantId == null || appliedSet == null || appliedSet.isEmpty()) return false;
        String clean = enchantId.trim().toLowerCase();
        if (appliedSet.contains(clean)) return true;
        if (clean.contains("defense") && appliedSet.contains(clean.replace("defense", "defence"))) return true;
        if (clean.contains("defence") && appliedSet.contains(clean.replace("defence", "defense"))) return true;
        if (appliedSet.contains(clean.replace('-', '_'))) return true;
        if (appliedSet.contains(clean.replace('_', '-'))) return true;
        return false;
    }

    public static boolean hasInBookInventory(String enchantId) {
        return matchesEnchant(enchantId, getBookEnchantments());
    }

    /**
     * Verifica la presenza dell'incantesimo su un libro nell'inventario
     */
    public static boolean hasInInventory(String enchantId) {
        return hasInBookInventory(enchantId);
    }

    public static int countInInventory(Collection<String> enchantIds) {
        if (enchantIds == null || enchantIds.isEmpty()) return 0;
        int count = 0;
        Map<String, Integer> bookMap = getBookEnchantments();
        for (String id : enchantIds) {
            if (matchesEnchant(id, bookMap)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Raccoglie gli incantesimi già applicati ESCLUSIVAMENTE sull'oggetto presente nell'incudine (zero interferenze da oggetti in mano!)
     */
    public static Set<String> getAppliedEnchantmentsForItem(String itemKey) {
        Set<String> set = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || itemKey == null) return set;
        String key = itemKey.trim().toLowerCase();

        // 1. Oggetto presente nell'Incudine nello slot 0 o slot 2 (output)
        if (mc.player.containerMenu instanceof AnvilMenu anvilMenu) {
            ItemStack slot0 = anvilMenu.getSlot(0).getItem();
            if (!slot0.isEmpty() && matchesItemKey(slot0, key)) {
                scanAppliedStack(slot0, set);
            }
            ItemStack slot2 = anvilMenu.getSlot(2).getItem();
            if (!slot2.isEmpty() && matchesItemKey(slot2, key)) {
                scanAppliedStack(slot2, set);
            }
        }

        // NOTA: Non scansioniamo mai getMainHandItem() qui!
        // Altrimenti, quando il giocatore ha in mano la sua spada maxata (es. Netherite),
        // il set pinnato pensa erroneamente che tutti gli incantesimi siano già stati applicati alla nuova spada.

        return set;
    }

    public static boolean matchesItemKey(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty() || key == null) return false;
        String detected = ItemTypeHelper.detectItemKey(stack);
        if (detected.equalsIgnoreCase(key)) return true;

        // Supporta alias localizzati
        if (("sword".equalsIgnoreCase(detected) && key.contains("spada"))
            || ("pick".equalsIgnoreCase(detected) && key.contains("piccone"))
            || ("helmet".equalsIgnoreCase(detected) && key.contains("elmo"))
            || ("chest".equalsIgnoreCase(detected) && key.contains("corazza"))
            || ("legs".equalsIgnoreCase(detected) && key.contains("gambali"))
            || ("boots".equalsIgnoreCase(detected) && key.contains("stivali"))
            || ("axe".equalsIgnoreCase(detected) && key.contains("ascia"))
            || ("bow".equalsIgnoreCase(detected) && (key.contains("arco") && !key.contains("balestra")))
            || ("xbow".equalsIgnoreCase(detected) && (key.contains("balestra") || key.contains("xbow")))
            || ("mace".equalsIgnoreCase(detected) && key.contains("mazza"))
            || ("trident".equalsIgnoreCase(detected) && key.contains("tridente"))
            || ("shovel".equalsIgnoreCase(detected) && key.contains("pala"))
            || ("hoe".equalsIgnoreCase(detected) && key.contains("zappa"))
            || ("rod".equalsIgnoreCase(detected) && key.contains("canna"))
            || ("elytra".equalsIgnoreCase(detected) && key.contains("elitr"))) {
            return true;
        }
        return false;
    }

    /**
     * Raccoglie tutti gli incantesimi già usati / applicati sull'oggetto da incantare (nell'incudine o in mano o indossato)
     */
    public static Set<String> getAppliedEnchantments() {
        Set<String> set = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return set;

        // 1. Oggetto presente nell'Incudine (Slot 0 o Slot 2 output)
        if (mc.player.containerMenu instanceof AnvilMenu anvilMenu) {
            ItemStack slot0 = anvilMenu.getSlot(0).getItem();
            if (!slot0.isEmpty() && !slot0.is(Items.ENCHANTED_BOOK) && !slot0.is(Items.BOOK)) {
                scanAppliedStack(slot0, set);
            }
            ItemStack slot2 = anvilMenu.getSlot(2).getItem();
            if (!slot2.isEmpty() && !slot2.is(Items.ENCHANTED_BOOK) && !slot2.is(Items.BOOK)) {
                scanAppliedStack(slot2, set);
            }
        }

        // 2. Oggetto tenuto nella mano principale (Spada, Arco, Attrezzo)
        ItemStack main = mc.player.getMainHandItem();
        if (!main.isEmpty() && !main.is(Items.ENCHANTED_BOOK) && !main.is(Items.BOOK)) {
            scanAppliedStack(main, set);
        }

        // 3. Oggetto nella seconda mano
        ItemStack off = mc.player.getOffhandItem();
        if (!off.isEmpty() && !off.is(Items.ENCHANTED_BOOK) && !off.is(Items.BOOK)) {
            scanAppliedStack(off, set);
        }

        return set;
    }

    public static boolean isEnchantmentApplied(String enchantId) {
        return matchesApplied(enchantId, getAppliedEnchantments());
    }

    private static void scanBookStack(ItemStack stack, Map<String, Integer> map) {
        if (stack == null || stack.isEmpty()) return;

        // 1. Libri incantati standard (STORED_ENCHANTMENTS)
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(rawId, lvl);
            if (vPlusId != null) {
                map.merge(vPlusId, lvl, Math::max);
                map.merge(vPlusId.replace('_', '-'), lvl, Math::max);
            }
            EnchantmentDatabase.EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            String canonId = ee != null ? ee.id : rawId;
            map.merge(rawId, lvl, Math::max);
            map.merge(canonId, lvl, Math::max);
            String baseId = LitnessI18n.getBaseId(canonId);
            if (!baseId.isEmpty()) {
                map.merge(baseId, lvl, Math::max);
            }
        }

        // 2. Lore con incantesimi custom sui libri
        int loreCost = extractAnvilCostFromLore(stack);
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            List<LitnessTooltipHandler.MatchedEnchantment> matches = LitnessTooltipHandler.matchAllEnchantmentsFromLine(c.getString());
            for (LitnessTooltipHandler.MatchedEnchantment match : matches) {
                if (match != null && match.entry != null) {
                    int lvl = match.level > 0 ? match.level : 1;
                    map.merge(match.entry.id, lvl, Math::max);
                    String baseId = LitnessI18n.getBaseId(match.entry.id);
                    if (!baseId.isEmpty()) {
                        map.merge(baseId, lvl, Math::max);
                    }
                    if (loreCost > 0) {
                        EnchantmentDatabase.updateAnvilCost(match.entry.id, loreCost);
                    }
                }
            }
        }
    }

    private static void scanAppliedStack(ItemStack stack, Set<String> set) {
        if (stack == null || stack.isEmpty() || ItemTypeHelper.isContainerOrShulker(stack)) return;

        // 1. DataComponent ENCHANTMENTS su armi / armature
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(rawId, lvl);
            if (vPlusId != null) {
                set.add(vPlusId);
                set.add(vPlusId.replace('_', '-'));
            }
            EnchantmentDatabase.EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            String canonId = ee != null ? ee.id : rawId;
            set.add(rawId.toLowerCase().trim());
            set.add(canonId.toLowerCase().trim());
            if (lvl >= getRequiredLevel(canonId)) {
                String baseId = LitnessI18n.getBaseId(canonId);
                if (!baseId.isEmpty()) set.add(baseId.toLowerCase().trim());
            }
        }

        // 2. Righe Lore su armi / armature (supporta più incantesimi per riga, es. "Phantom Strike II, Reaper III")
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            List<LitnessTooltipHandler.MatchedEnchantment> matches = LitnessTooltipHandler.matchAllEnchantmentsFromLine(c.getString());
            for (LitnessTooltipHandler.MatchedEnchantment match : matches) {
                if (match != null && match.entry != null) {
                    set.add(match.entry.id.toLowerCase().trim());
                    set.add(match.entry.id.toLowerCase().replace('_', '-').trim());
                    String baseId = LitnessI18n.getBaseId(match.entry.id);
                    if (!baseId.isEmpty()) set.add(baseId.toLowerCase().trim());
                }
            }
        }
    }

    public static int extractAnvilCostFromLore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            int cost = parseAnvilCostFromText(c.getString());
            if (cost > 0) return cost;
        }
        return -1;
    }

    public static int parseAnvilCostFromText(String text) {
        if (text == null || text.isEmpty()) return -1;
        String clean = text.replaceAll("§[0-9a-fk-orx]", "").trim();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)(?:costo\\s+incantesim[io]|costo\\s+incudine|enchantment\\s+cost|costo)[^\\d\\+]*\\+?(\\d+)");
        java.util.regex.Matcher m = pattern.matcher(clean);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }
}

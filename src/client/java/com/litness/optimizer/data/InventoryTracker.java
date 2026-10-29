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
            if (!stack.isEmpty() && (stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK))) {
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

    public static boolean matchesEnchant(String enchantId, Map<String, Integer> bookMap) {
        if (enchantId == null || bookMap == null || bookMap.isEmpty()) return false;
        String clean = enchantId.trim().toLowerCase();
        if (bookMap.containsKey(clean)) return true;
        if (clean.contains("defense") && bookMap.containsKey(clean.replace("defense", "defence"))) return true;
        if (clean.contains("defence") && bookMap.containsKey(clean.replace("defence", "defense"))) return true;
        if (bookMap.containsKey(clean.replace('-', '_'))) return true;
        if (bookMap.containsKey(clean.replace('_', '-'))) return true;
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
     * Raccoglie gli incantesimi già applicati ESCLUSIVAMENTE sull'oggetto corrispondente al set (zero falsi positivi!)
     */
    public static Set<String> getAppliedEnchantmentsForItem(String itemKey) {
        Set<String> set = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || itemKey == null) return set;
        String key = itemKey.trim().toLowerCase();

        // 1. Oggetto presente nell'Incudine nello slot 0 (se corrisponde alla categoria del set)
        if (mc.player.containerMenu instanceof AnvilMenu anvilMenu) {
            ItemStack slot0 = anvilMenu.getSlot(0).getItem();
            if (!slot0.isEmpty() && matchesItemKey(slot0, key)) {
                scanAppliedStack(slot0, set);
            }
        }

        // 2. Oggetto tenuto nella mano principale (se Spada, Piccone o arma/attrezzo)
        if (key.contains("sword") || key.contains("spada") || key.contains("pick") || key.contains("piccone") 
                || key.contains("axe") || key.contains("ascia") || key.contains("bow") || key.contains("arco")) {
            ItemStack main = mc.player.getMainHandItem();
            if (!main.isEmpty() && matchesItemKey(main, key)) {
                scanAppliedStack(main, set);
            }
        }

        return set;
    }

    public static boolean matchesItemKey(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) return false;
        String descId = stack.getItem().getDescriptionId().toLowerCase();
        String rawStr = stack.getItem().toString().toLowerCase();

        if (key.contains("sword") || key.contains("spada")) {
            return descId.contains("sword") || rawStr.contains("sword");
        }
        if (key.contains("pick") || key.contains("piccone")) {
            return descId.contains("pickaxe") || descId.contains("pick") || rawStr.contains("pickaxe") || rawStr.contains("pick");
        }
        if (key.contains("helmet") || key.contains("elmo") || key.contains("head")) {
            return descId.contains("helmet") || rawStr.contains("helmet") || descId.contains("cap");
        }
        if (key.contains("chest") || key.contains("corazza")) {
            return descId.contains("chestplate") || descId.contains("chest") || rawStr.contains("chestplate") || rawStr.contains("chest") || descId.contains("tunic");
        }
        if (key.contains("leg") || key.contains("gambali")) {
            return descId.contains("leggings") || descId.contains("legs") || rawStr.contains("leggings") || rawStr.contains("legs") || descId.contains("pants");
        }
        if (key.contains("boot") || key.contains("stivali") || key.contains("feet")) {
            return descId.contains("boots") || rawStr.contains("boots");
        }
        if (key.contains("axe") || key.contains("ascia")) {
            return descId.contains("axe") || rawStr.contains("axe");
        }
        if (key.contains("bow") || key.contains("arco")) {
            return descId.contains("bow") || rawStr.contains("bow");
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
            } else {
                EnchantmentDatabase.EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
                if (ee != null) {
                    map.merge(ee.id, lvl, Math::max);
                }
            }
        }

        // 2. Lore con incantesimi custom sui libri
        int loreCost = extractAnvilCostFromLore(stack);
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(c.getString());
            if (match != null && match.entry != null) {
                int lvl = match.level > 0 ? match.level : match.entry.maxLevel;
                map.merge(match.entry.id, lvl, Math::max);
                if (loreCost > 0) {
                    EnchantmentDatabase.updateAnvilCost(match.entry.id, loreCost);
                }
            }
        }
    }

    private static void scanAppliedStack(ItemStack stack, Set<String> set) {
        if (stack == null || stack.isEmpty()) return;

        // 1. DataComponent ENCHANTMENTS su armi / armature
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(rawId, lvl);
            if (vPlusId != null) {
                set.add(vPlusId);
            } else {
                EnchantmentDatabase.EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
                String id = ee != null ? ee.id : rawId;
                if (!id.isEmpty()) {
                    set.add(id.toLowerCase().trim());
                }
            }
        }

        // 2. Righe Lore su armi / armature
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(c.getString());
            if (match != null && match.entry != null) {
                set.add(match.entry.id.toLowerCase().trim());
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

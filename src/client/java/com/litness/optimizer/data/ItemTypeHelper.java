package com.litness.optimizer.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ItemTypeHelper {

    public static final String[] ITEM_KEYS = {
        "sword", "axe", "mace", "trident", "bow", "xbow", "pick", "shovel", "hoe", "rod", "helmet", "chest", "legs", "boots", "elytra"
    };

    public static String detectItemKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        if (!isEnchantableWeaponOrArmor(stack)) return "";

        Identifier regId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = (regId != null) ? regId.getPath().toLowerCase() : "";

        if (path.equals("bow")) return "bow";
        if (path.equals("crossbow") || path.equals("xbow")) return "xbow";
        if (path.equals("mace")) return "mace";
        if (path.equals("trident")) return "trident";
        if (path.equals("fishing_rod") || path.equals("rod")) return "rod";
        if (path.equals("elytra")) return "elytra";
        if (path.endsWith("_sword") || path.equals("sword")) return "sword";
        if (path.endsWith("_pickaxe") || path.equals("pickaxe")) return "pick";
        if (path.endsWith("_axe") || path.equals("axe")) return "axe";
        if (path.endsWith("_shovel") || path.equals("shovel")) return "shovel";
        if (path.endsWith("_hoe") || path.equals("hoe")) return "hoe";
        if (path.endsWith("_helmet") || path.contains("helmet") || path.contains("cap")) return "helmet";
        if (path.endsWith("_chestplate") || path.contains("chest") || path.contains("tunic")) return "chest";
        if (path.endsWith("_leggings") || path.contains("leg") || path.contains("pants")) return "legs";
        if (path.endsWith("_boots") || path.contains("boot")) return "boots";

        // Fallback su descriptionId
        String desc = stack.getItem().getDescriptionId().toLowerCase();
        if (desc.contains("crossbow") || desc.contains("xbow")) return "xbow";
        if (desc.contains("bow")) return "bow";
        if (desc.contains("mace")) return "mace";
        if (desc.contains("trident")) return "trident";
        if (desc.contains("fishing")) return "rod";
        if (desc.contains("elytra")) return "elytra";
        if (desc.contains("sword")) return "sword";
        if (desc.contains("pick")) return "pick";
        if (desc.contains("axe")) return "axe";
        if (desc.contains("shovel")) return "shovel";
        if (desc.contains("hoe")) return "hoe";
        if (desc.contains("helmet") || desc.contains("cap")) return "helmet";
        if (desc.contains("chest") || desc.contains("tunic")) return "chest";
        if (desc.contains("leg") || desc.contains("pants")) return "legs";
        if (desc.contains("boot")) return "boots";

        return "";
    }

    public static int getItemIndexForKey(String key) {
        if (key == null || key.isEmpty()) return 0;
        for (int i = 0; i < ITEM_KEYS.length; i++) {
            if (ITEM_KEYS[i].equalsIgnoreCase(key)) return i;
        }
        return 0;
    }

    public static String getActionName(String itemKey) {
        return LitnessI18n.getActionName(itemKey);
    }

    public static String getActionIcon(String itemKey) {
        return "";
    }

    public static String getSuggestedName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return LitnessI18n.isEnglish() ? "your Sword" : "la tua Spada";
        String key = detectItemKey(stack);
        if (key.isEmpty()) key = "sword";
        String piece = LitnessI18n.getItemPieceName(key);
        if (LitnessI18n.isEnglish()) {
            return "your " + piece;
        } else {
            switch (key) {
                case "bow": return "il tuo Arco";
                case "xbow": return "la tua Balestra";
                case "mace": return "la tua Mazza";
                case "trident": return "il tuo Tridente";
                case "pick": return "il tuo Piccone";
                case "axe": return "la tua Ascia";
                case "shovel": return "la tua Pala";
                case "hoe": return "la tua Zappa";
                case "rod": return "la tua Canna da pesca";
                case "helmet": return "il tuo Elmo";
                case "chest": return "la tua Corazza";
                case "legs": return "i tuoi Gambali";
                case "boots": return "i tuoi Stivali";
                case "elytra": return "le tue Elitre";
                default: return "la tua Spada";
            }
        }
    }

    public static boolean isContainerOrShulker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.has(net.minecraft.core.component.DataComponents.CONTAINER)
                || stack.has(net.minecraft.core.component.DataComponents.CONTAINER_LOOT)
                || stack.has(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS)) {
            return true;
        }
        Identifier regId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (regId != null) {
            String p = regId.getPath().toLowerCase();
            if (p.contains("shulker") || p.contains("bundle") || p.contains("backpack") || p.contains("chest") || p.contains("barrel") || p.contains("sack")) {
                return true;
            }
        }
        String desc = stack.getItem().getDescriptionId().toLowerCase();
        return desc.contains("shulker") || desc.contains("bundle") || desc.contains("backpack") || desc.contains("chest") || desc.contains("barrel");
    }

    public static boolean isEnchantableWeaponOrArmor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK)) return false;
        if (isContainerOrShulker(stack)) return false;
        if (stack.getMaxStackSize() != 1) return false;
        if (stack.has(net.minecraft.core.component.DataComponents.FOOD)) return false;

        Identifier regId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = (regId != null) ? regId.getPath().toLowerCase() : "";

        if (path.endsWith("_sword") || path.equals("sword")
                || path.endsWith("_pickaxe") || path.equals("pickaxe")
                || path.endsWith("_axe") || path.equals("axe")
                || path.endsWith("_shovel") || path.equals("shovel")
                || path.endsWith("_hoe") || path.equals("hoe")
                || path.endsWith("_helmet") || path.equals("turtle_helmet") || path.equals("leather_helmet")
                || path.endsWith("_chestplate") || path.equals("leather_chestplate")
                || path.endsWith("_leggings") || path.equals("leather_leggings")
                || path.endsWith("_boots") || path.equals("leather_boots")
                || path.equals("bow") || path.equals("crossbow") || path.equals("trident")
                || path.equals("mace") || path.equals("fishing_rod") || path.equals("elytra")) {
            return true;
        }

        // Secondary fallback for modded items with standard descriptions
        String desc = stack.getItem().getDescriptionId().toLowerCase();
        return desc.endsWith(".sword") || desc.endsWith(".pickaxe") || desc.endsWith(".axe")
                || desc.endsWith(".shovel") || desc.endsWith(".hoe") || desc.endsWith(".helmet")
                || desc.endsWith(".chestplate") || desc.endsWith(".leggings") || desc.endsWith(".boots")
                || desc.endsWith(".bow") || desc.endsWith(".crossbow") || desc.endsWith(".trident")
                || desc.endsWith(".mace") || desc.endsWith(".fishing_rod") || desc.endsWith(".elytra");
    }
}

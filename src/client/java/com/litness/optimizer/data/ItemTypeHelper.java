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
        if (stack == null || stack.isEmpty()) return "sword";

        Identifier regId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = (regId != null) ? regId.getPath().toLowerCase() : "";

        if (path.equals("bow")) return "bow";
        if (path.equals("crossbow") || path.equals("xbow")) return "xbow";
        if (path.equals("mace")) return "mace";
        if (path.equals("trident")) return "trident";
        if (path.equals("fishing_rod") || path.equals("rod")) return "rod";
        if (path.equals("elytra")) return "elytra";
        if (path.endsWith("_sword") || path.equals("sword")) return "sword";
        if (path.endsWith("_pickaxe") || path.contains("pick")) return "pick";
        if (path.endsWith("_axe") || path.equals("axe")) return "axe";
        if (path.endsWith("_shovel") || path.contains("shovel") || path.contains("spade")) return "shovel";
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
        if (desc.contains("shovel") || desc.contains("spade")) return "shovel";
        if (desc.contains("hoe")) return "hoe";
        if (desc.contains("helmet") || desc.contains("cap")) return "helmet";
        if (desc.contains("chest") || desc.contains("tunic")) return "chest";
        if (desc.contains("leg") || desc.contains("pants")) return "legs";
        if (desc.contains("boot")) return "boots";

        return "sword";
    }

    public static int getItemIndexForKey(String key) {
        if (key == null) return 0;
        for (int i = 0; i < ITEM_KEYS.length; i++) {
            if (ITEM_KEYS[i].equalsIgnoreCase(key)) return i;
        }
        return 0;
    }

    public static String getActionName(String itemKey) {
        if ("bow".equals(itemKey)) return "TEMPRA ARCO";
        if ("xbow".equals(itemKey)) return "TEMPRA BALESTRA";
        if ("mace".equals(itemKey)) return "TEMPRA MAZZA";
        if ("trident".equals(itemKey)) return "TEMPRA TRIDENTE";
        if ("pick".equals(itemKey)) return "TEMPRA PICCONE";
        if ("axe".equals(itemKey)) return "TEMPRA ASCIA";
        if ("shovel".equals(itemKey)) return "TEMPRA PALA";
        if ("hoe".equals(itemKey)) return "TEMPRA ZAPPA";
        if ("rod".equals(itemKey)) return "TEMPRA CANNA";
        if ("helmet".equals(itemKey)) return "TEMPRA ELMO";
        if ("chest".equals(itemKey)) return "TEMPRA CORAZZA";
        if ("legs".equals(itemKey)) return "TEMPRA GAMBALI";
        if ("boots".equals(itemKey)) return "TEMPRA STIVALI";
        if ("elytra".equals(itemKey)) return "TEMPRA ELITRE";
        return "TEMPRA SPADA";
    }

    public static String getActionIcon(String itemKey) {
        if ("bow".equals(itemKey)) return "🏹";
        if ("xbow".equals(itemKey)) return "🎯";
        if ("mace".equals(itemKey)) return "🔨";
        if ("trident".equals(itemKey)) return "🔱";
        if ("pick".equals(itemKey)) return "⛏";
        if ("axe".equals(itemKey)) return "🪓";
        if ("shovel".equals(itemKey)) return "🥄";
        if ("hoe".equals(itemKey)) return "🌾";
        if ("rod".equals(itemKey)) return "🎣";
        if ("helmet".equals(itemKey)) return "🪖";
        if ("chest".equals(itemKey)) return "🥋";
        if ("legs".equals(itemKey)) return "👖";
        if ("boots".equals(itemKey)) return "👢";
        if ("elytra".equals(itemKey)) return "🪽";
        return "⚔";
    }

    public static String getSuggestedName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "la tua Spada";
        String key = detectItemKey(stack);
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

    public static boolean isEnchantableWeaponOrArmor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK)) return false;

        Identifier regId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = (regId != null) ? regId.getPath().toLowerCase() : "";
        if (path.contains("sword") || path.contains("bow") || path.contains("pick") || path.contains("axe") 
            || path.contains("mace") || path.contains("trident") || path.contains("shovel") || path.contains("hoe")
            || path.contains("helmet") || path.contains("chest") || path.contains("leg") || path.contains("boot")
            || path.contains("elytra") || path.contains("rod") || path.contains("cap") || path.contains("tunic") || path.contains("pants")) {
            return true;
        }

        String desc = stack.getItem().getDescriptionId().toLowerCase();
        return desc.contains("sword") || desc.contains("bow") || desc.contains("pick") || desc.contains("axe") 
            || desc.contains("mace") || desc.contains("trident") || desc.contains("shovel") || desc.contains("hoe")
            || desc.contains("helmet") || desc.contains("chest") || desc.contains("leg") || desc.contains("boot")
            || desc.contains("elytra") || desc.contains("rod");
    }
}

package com.litness.optimizer.gui;

import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public class EnchantmentThematicIconHelper {
    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    public static ItemStack getIcon(EnchantmentEntry ee) {
        if (ee == null) return new ItemStack(Items.ENCHANTED_BOOK);
        return CACHE.computeIfAbsent(ee.id != null ? ee.id : ee.name, id -> resolveIcon(ee));
    }

    public static ItemStack getIconById(String enchantId) {
        if (enchantId == null) return new ItemStack(Items.ENCHANTED_BOOK);
        com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry ee =
                com.litness.optimizer.data.EnchantmentDatabase.getEnchantment(enchantId);
        return getIcon(ee);
    }

    private static ItemStack resolveIcon(EnchantmentEntry ee) {
        String id = ee.id != null ? ee.id.toLowerCase() : "";
        String name = ee.name != null ? ee.name.toLowerCase() : "";

        // Explicit mappings for vanilla & vanilla+
        if (id.startsWith("fire_aspect") || name.contains("aspetto di fuoco")) return new ItemStack(Items.FIRE_CHARGE);
        if (id.startsWith("flame") || name.contains("fiamma")) return new ItemStack(Items.FIRE_CHARGE);
        if (id.startsWith("fire_protection") || name.contains("protezione dal fuoco")) return new ItemStack(Items.MAGMA_CREAM);
        if (id.startsWith("frost_walker") || name.contains("passo gelato")) return new ItemStack(Items.BLUE_ICE);
        if (id.startsWith("feather_falling") || id.startsWith("father_falling") || name.contains("caduta morbida") || name.contains("atterraggio morbido")) return new ItemStack(Items.FEATHER);
        if (id.startsWith("depth_strider") || name.contains("passo anfibio")) return new ItemStack(Items.TURTLE_HELMET);
        if (id.startsWith("respiration") || name.contains("respirazione")) return new ItemStack(Items.PUFFERFISH);
        if (id.startsWith("aqua_affinity") || name.contains("affinità all'acqua")) return new ItemStack(Items.HEART_OF_THE_SEA);
        if (id.startsWith("sharpness") || name.contains("affilatezza")) return new ItemStack(Items.DIAMOND_SWORD);
        if (id.startsWith("smite") || name.contains("anatema")) return new ItemStack(Items.GOLDEN_SWORD);
        if (id.startsWith("bane_of_arthropods") || name.contains("flagello degli artropodi")) return new ItemStack(Items.FERMENTED_SPIDER_EYE);
        if (id.startsWith("knockback") || name.contains("contraccolpo")) return new ItemStack(Items.PISTON);
        if (id.startsWith("sweeping_edge") || name.contains("lama sferzante")) return new ItemStack(Items.IRON_SWORD);
        if (id.startsWith("looting") || name.contains("saccheggio")) return new ItemStack(Items.EMERALD);
        if (id.startsWith("unbreaking") || name.contains("indistruttibilità") || name.contains("incrollabilità")) return new ItemStack(Items.OBSIDIAN);
        if (id.startsWith("fortune") || name.contains("fortuna")) return new ItemStack(Items.DIAMOND);
        if (id.startsWith("efficiency") || name.contains("efficienza")) return new ItemStack(Items.DIAMOND_PICKAXE);
        if (id.startsWith("silk_touch") || name.contains("tocco di velluto")) return new ItemStack(Items.STRING);
        if (id.startsWith("protection") || name.contains("protezione")) return new ItemStack(Items.SHIELD);
        if (id.startsWith("blast_protection") || name.contains("esplosioni")) return new ItemStack(Items.TNT);
        if (id.startsWith("projectile_protection") || name.contains("proiettili")) return new ItemStack(Items.TARGET);
        if (id.startsWith("thorns") || name.contains("spine")) return new ItemStack(Items.CACTUS);
        if (id.startsWith("power") || name.contains("potenza") || name.contains("impatto")) return new ItemStack(Items.BOW);
        if (id.startsWith("punch") || name.contains("impulso")) return new ItemStack(Items.CROSSBOW);
        if (id.startsWith("infinity") || name.contains("infinità")) return new ItemStack(Items.SPECTRAL_ARROW);
        if (id.startsWith("loyalty") || name.contains("lealtà")) return new ItemStack(Items.LEAD);
        if (id.startsWith("riptide") || name.contains("risacca")) return new ItemStack(Items.TRIDENT);
        if (id.startsWith("channeling") || name.contains("canalizzazione")) return new ItemStack(Items.LIGHTNING_ROD.weathering().unaffected());
        if (id.startsWith("impaling") || name.contains("impalamento")) return new ItemStack(Items.PRISMARINE_CRYSTALS);
        if (id.startsWith("multishot") || name.contains("tiro multiplo")) return new ItemStack(Items.ARROW);
        if (id.startsWith("quick_charge") || name.contains("carica rapida")) return new ItemStack(Items.REPEATER);
        if (id.startsWith("piercing") || name.contains("perforazione")) return new ItemStack(Items.IRON_BARS);
        if (id.startsWith("mending") || name.contains("ripristino")) return new ItemStack(Items.EXPERIENCE_BOTTLE);
        if (id.startsWith("wind_burst") || name.contains("vento")) return new ItemStack(Items.WIND_CHARGE);
        if (id.startsWith("density") || name.contains("densità")) return new ItemStack(Items.HEAVY_CORE);
        if (id.startsWith("breach") || name.contains("breccia")) return new ItemStack(Items.MACE);
        if (id.startsWith("soul_speed") || name.contains("anime")) return new ItemStack(Items.SOUL_SAND);
        if (id.startsWith("swift_sneak") || name.contains("felpato")) return new ItemStack(Items.SCULK);
        if (id.startsWith("lure") || name.contains("esca")) return new ItemStack(Items.FISHING_ROD);
        if (id.startsWith("luck_of_the_sea") || name.contains("fortuna del mare")) return new ItemStack(Items.NAUTILUS_SHELL);
        if (id.startsWith("curse_of_binding") || name.contains("legame")) return new ItemStack(Items.IRON_CHAIN);
        if (id.startsWith("curse_of_vanishing") || name.contains("scomparsa")) return new ItemStack(Items.ENDER_EYE);

        // Keyword matches for Custom Enchants
        String combined = id + " " + name;
        if (combined.contains("fire") || combined.contains("fuoco") || combined.contains("flame") || combined.contains("burn") || combined.contains("ignit") || combined.contains("pyro") || combined.contains("blaze")) return new ItemStack(Items.FIRE_CHARGE);
        if (combined.contains("ice") || combined.contains("gelo") || combined.contains("frost") || combined.contains("freeze") || combined.contains("cold") || combined.contains("snow")) return new ItemStack(Items.PACKED_ICE);
        if (combined.contains("light") || combined.contains("fulmine") || combined.contains("storm") || combined.contains("thunder") || combined.contains("thor") || combined.contains("shock") || combined.contains("spark") || combined.contains("volt")) return new ItemStack(Items.LIGHTNING_ROD.weathering().unaffected());
        if (combined.contains("water") || combined.contains("acqua") || combined.contains("sea") || combined.contains("ocean") || combined.contains("depth") || combined.contains("stream")) return new ItemStack(Items.HEART_OF_THE_SEA);
        if (combined.contains("poison") || combined.contains("veleno") || combined.contains("toxic") || combined.contains("venom")) return new ItemStack(Items.FERMENTED_SPIDER_EYE);
        if (combined.contains("wither") || combined.contains("dark") || combined.contains("shadow") || combined.contains("void") || combined.contains("death") || combined.contains("wound")) return new ItemStack(Items.WITHER_SKELETON_SKULL);
        if (combined.contains("vampir") || combined.contains("blood") || combined.contains("lifesteal") || combined.contains("drain") || combined.contains("vital") || combined.contains("heal")) return new ItemStack(Items.GLISTERING_MELON_SLICE);
        if (combined.contains("bomb") || combined.contains("blast") || combined.contains("esplos") || combined.contains("tnt") || combined.contains("boom") || combined.contains("detonat")) return new ItemStack(Items.TNT);
        if (combined.contains("speed") || combined.contains("veloc") || combined.contains("swift") || combined.contains("rush") || combined.contains("dash") || combined.contains("agility") || combined.contains("stamina")) return new ItemStack(Items.SUGAR);
        if (combined.contains("arrow") || combined.contains("bow") || combined.contains("shoot") || combined.contains("snipe") || combined.contains("triple")) return new ItemStack(Items.BOW);
        if (combined.contains("mine") || combined.contains("scav") || combined.contains("vein") || combined.contains("drill") || combined.contains("pick")) return new ItemStack(Items.DIAMOND_PICKAXE);
        if (combined.contains("timber") || combined.contains("tree") || combined.contains("wood") || combined.contains("axe") || combined.contains("ascia")) return new ItemStack(Items.DIAMOND_AXE);
        if (combined.contains("teleport") || combined.contains("warp") || combined.contains("ender") || combined.contains("portal") || combined.contains("blink") || combined.contains("transmiss")) return new ItemStack(Items.ENDER_PEARL);
        if (combined.contains("soul") || combined.contains("anima") || combined.contains("spirit")) return new ItemStack(Items.SOUL_SAND);
        if (combined.contains("shield") || combined.contains("prot") || combined.contains("guard") || combined.contains("defen") || combined.contains("barrier") || combined.contains("resist")) return new ItemStack(Items.SHIELD);
        if (combined.contains("xp") || combined.contains("exp") || combined.contains("wisdom") || combined.contains("learn") || combined.contains("mending") || combined.contains("repair")) return new ItemStack(Items.EXPERIENCE_BOTTLE);
        if (combined.contains("gold") || combined.contains("coin") || combined.contains("money") || combined.contains("rich") || combined.contains("luck") || combined.contains("fortune") || combined.contains("loot") || combined.contains("sacchegg")) return new ItemStack(Items.EMERALD);
        if (combined.contains("strength") || combined.contains("force") || combined.contains("potenza") || combined.contains("damage") || combined.contains("sharp") || combined.contains("strike") || combined.contains("slash") || combined.contains("blade") || combined.contains("stun")) return new ItemStack(Items.DIAMOND_SWORD);
        if (combined.contains("feather") || combined.contains("soft") || combined.contains("fall") || combined.contains("glide") || combined.contains("fly") || combined.contains("wing") || combined.contains("slipstream")) return new ItemStack(Items.FEATHER);

        return new ItemStack(Items.ENCHANTED_BOOK);
    }
}

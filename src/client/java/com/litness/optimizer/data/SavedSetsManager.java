package com.litness.optimizer.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.*;

public class SavedSetsManager {

    public static class CustomSet {
        public String id;
        public String name;
        public String itemKey;
        public List<String> enchantments = new ArrayList<>();
        public long createdAt;

        public CustomSet() {}

        public CustomSet(String id, String name, String itemKey, Collection<String> enchantments) {
            this.id = id;
            this.name = name;
            this.itemKey = itemKey;
            this.enchantments = enchantments != null ? new ArrayList<>(enchantments) : new ArrayList<>();
            this.createdAt = System.currentTimeMillis();
        }
    }

    private static final List<CustomSet> customSets = new ArrayList<>();
    private static final File CONFIG_FILE = new File("config/litnessoptimizer/saved_sets.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!CONFIG_FILE.exists()) return;
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE.toPath())) {
            List<CustomSet> list = GSON.fromJson(reader, new TypeToken<List<CustomSet>>() {}.getType());
            if (list != null) {
                customSets.clear();
                customSets.addAll(list);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void save() {
        try {
            if (CONFIG_FILE.getParentFile() != null) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE.toPath())) {
                GSON.toJson(customSets, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized List<CustomSet> getSavedSets() {
        load();
        return new ArrayList<>(customSets);
    }

    public static synchronized CustomSet addSet(String name, String itemKey, Collection<String> enchantments) {
        load();
        String id = UUID.randomUUID().toString().substring(0, 8);
        String finalName = (name == null || name.trim().isEmpty()) ? "Set " + capitalize(itemKey) : name.trim();
        CustomSet set = new CustomSet(id, finalName, itemKey, enchantments);
        customSets.add(0, set);
        save();
        return set;
    }

    public static synchronized boolean deleteSet(String id) {
        load();
        CustomSet toDelete = null;
        for (CustomSet s : customSets) {
            if (s.id.equalsIgnoreCase(id)) {
                toDelete = s;
                break;
            }
        }
        if (toDelete != null) {
            if (toDelete.enchantments != null) {
                for (String enchId : toDelete.enchantments) {
                    PinnedEnchantmentsManager.setPinned(enchId, false);
                }
            }
            customSets.remove(toDelete);
            save();
            return true;
        }
        return false;
    }

    public static List<String> getPresetEnchantments(String itemKey) {
        if (itemKey == null) return Collections.emptyList();
        switch (itemKey.toLowerCase()) {
            case "sword":
                return List.of("sharpness", "first-strike", "criticals", "phantom-strike", "berserker", "dexterity", "bleed", "executioner", "goliath", "lifesteal", "ignis-fury", "mending");
            case "helmet":
                return List.of("protection", "respiration", "aqua_affinity", "thorns", "unbreaking", "mending", "arcane-defense", "night-stalker", "satiety", "block-breather");
            case "chest":
                return List.of("protection", "thorns", "unbreaking", "mending", "phoenix-heart", "thrive", "fortitude", "arcane-defense", "aura", "molten-heart", "stamina", "metabolism");
            case "legs":
                return List.of("protection", "thorns", "swift_sneak", "unbreaking", "mending", "iron-stance", "shadow-step", "static-barrier", "thrive", "fortitude", "arcane-defense", "aura");
            case "boots":
                return List.of("protection", "bouncy", "depth_strider", "soul_speed", "thorns", "unbreaking", "mending", "streamlining", "getaway", "arcane-defense", "feather-step");
            default:
                return Collections.emptyList();
        }
    }

    public static boolean isPresetPinned(String itemKey) {
        List<String> preset = getPresetEnchantments(itemKey);
        if (preset.isEmpty()) return false;
        for (String id : preset) {
            if (!PinnedEnchantmentsManager.isPinned(id)) return false;
        }
        return true;
    }

    public static void togglePinPreset(String itemKey) {
        List<String> preset = getPresetEnchantments(itemKey);
        if (preset.isEmpty()) return;
        if (isPresetPinned(itemKey)) {
            for (String id : preset) {
                PinnedEnchantmentsManager.setPinned(id, false);
            }
        } else {
            PinnedEnchantmentsManager.clearAll();
            PinnedEnchantmentsManager.pinAll(preset);
        }
    }

    public static boolean isSetPinned(Collection<String> enchantIds) {
        if (enchantIds == null || enchantIds.isEmpty()) return false;
        for (String id : enchantIds) {
            if (!PinnedEnchantmentsManager.isPinned(id)) return false;
        }
        return true;
    }

    public static void togglePinSet(Collection<String> enchantIds) {
        if (enchantIds == null || enchantIds.isEmpty()) return;
        if (isSetPinned(enchantIds)) {
            for (String id : enchantIds) {
                PinnedEnchantmentsManager.setPinned(id, false);
            }
        } else {
            PinnedEnchantmentsManager.clearAll();
            PinnedEnchantmentsManager.pinAll(enchantIds);
        }
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}

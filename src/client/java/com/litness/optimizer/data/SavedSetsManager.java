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

    public static synchronized boolean updateSet(String id, String name, String itemKey, Collection<String> enchantments) {
        load();
        for (CustomSet s : customSets) {
            if (s.id.equalsIgnoreCase(id)) {
                s.name = (name == null || name.trim().isEmpty()) ? "Set " + capitalize(itemKey) : name.trim();
                s.itemKey = itemKey;
                s.enchantments = enchantments != null ? new ArrayList<>(enchantments) : new ArrayList<>();
                save();
                return true;
            }
        }
        return false;
    }

    public static synchronized CustomSet getSetById(String id) {
        if (id == null) return null;
        load();
        for (CustomSet s : customSets) {
            if (s != null && s.id != null && s.id.equalsIgnoreCase(id)) return s;
        }
        return null;
    }

    public static synchronized boolean deleteSet(String id) {
        if (id == null) return false;
        load();
        CustomSet toDelete = null;
        for (CustomSet s : customSets) {
            if (s != null && s.id != null && s.id.equalsIgnoreCase(id)) {
                toDelete = s;
                break;
            }
        }
        if (toDelete != null) {
            customSets.remove(toDelete);
            if (id.equalsIgnoreCase(ActiveSessionManager.getPinnedSetId())) {
                ActiveSessionManager.setPinnedSetId(null);
            }
            save();
            return true;
        }
        return false;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    public static String normalizeItemKey(String key) {
        if (key == null) return "sword";
        String k = key.trim().toLowerCase(Locale.ROOT);
        switch (k) {
            case "chest": case "chestplate": case "corazza": case "pettorina": return "chest";
            case "helmet": case "elmo": case "casco": return "helmet";
            case "legs": case "leggings": case "gambali": case "pantaloni": return "legs";
            case "boots": case "stivali": return "boots";
            case "elytra": case "elitre": return "elytra";
            case "sword": case "spada": return "sword";
            case "axe": case "ascia": return "axe";
            case "mace": case "mazza": return "mace";
            case "trident": case "tridente": return "trident";
            case "bow": case "arco": return "bow";
            case "xbow": case "crossbow": case "balestra": return "xbow";
            case "pick": case "pickaxe": case "piccone": return "pick";
            case "shovel": case "pala": return "shovel";
            case "hoe": case "zappa": return "hoe";
            case "rod": case "fishing_rod": case "canna": return "rod";
            default: return k;
        }
    }

    public static String cleanEnchantId(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.isEmpty()) return "";
        String lower = s.toLowerCase(Locale.ROOT).replace(" ", "_");
        if (EnchantmentDatabase.getEnchantment(lower) != null) {
            return lower;
        }
        var match = com.litness.optimizer.tooltip.LitnessTooltipHandler.matchEnchantment(s);
        if (match != null && match.entry != null) {
            if (match.level > 1 && !match.entry.id.contains("_")) {
                String vPlus = EnchantmentDatabase.resolveVanillaPlusId(match.entry.id, match.level);
                if (vPlus != null) return vPlus;
                return match.entry.id + "_" + match.level;
            }
            return match.entry.id;
        }
        return lower;
    }

    public static class ExportedSet {
        public String name;
        public String itemKey;
        public List<String> enchantments = new ArrayList<>();
    }

    public static String exportSetToString(CustomSet set) {
        if (set == null) return null;
        try {
            String name = (set.name != null && !set.name.trim().isEmpty()) ? set.name.trim() : "Custom Set";
            String key = (set.itemKey != null && !set.itemKey.trim().isEmpty()) ? set.itemKey.trim().toLowerCase(Locale.ROOT) : "sword";
            String enchs = String.join(", ", set.enchantments);
            return String.format("Set: %s | %s | %s", name, key, enchs);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static CustomSet importSetFromString(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String trimmed = input.trim();
        try {
            String readable = trimmed;
            if (readable.startsWith("LITSET:")) {
                String sub = readable.substring("LITSET:".length()).trim();
                if (sub.contains("|")) {
                    readable = sub;
                }
            }
            if (readable.toLowerCase(Locale.ROOT).startsWith("set:")) {
                readable = readable.substring(4).trim();
            } else if (readable.toLowerCase(Locale.ROOT).startsWith("litnessset:")) {
                readable = readable.substring(11).trim();
            }

            // 1. Formato leggibile con pipe: "Nome | itemKey | ench1, ench2..."
            if (readable.contains("|")) {
                String[] parts = readable.split("\\|", 3);
                if (parts.length >= 2) {
                    String name = parts[0].trim();
                    String itemKey = normalizeItemKey(parts[1].trim());
                    List<String> enchs = new ArrayList<>();
                    if (parts.length > 2 && !parts[2].trim().isEmpty()) {
                        String[] rawList = parts[2].split("[,;]+");
                        for (String raw : rawList) {
                            String cleaned = cleanEnchantId(raw);
                            if (!cleaned.isEmpty()) enchs.add(cleaned);
                        }
                    }
                    if (name.isEmpty()) name = "Set " + capitalize(itemKey);
                    return addSet(name, itemKey, enchs);
                }
            }

            // 2. Formato separato da due punti: "Nome: itemKey: ench1, ench2..."
            if (readable.contains(":")) {
                String[] parts = readable.split(":", 3);
                if (parts.length >= 2) {
                    String name = parts[0].trim();
                    String itemKey = normalizeItemKey(parts[1].trim());
                    List<String> enchs = new ArrayList<>();
                    if (parts.length > 2 && !parts[2].trim().isEmpty()) {
                        String[] rawList = parts[2].split("[,;]+");
                        for (String raw : rawList) {
                            String cleaned = cleanEnchantId(raw);
                            if (!cleaned.isEmpty()) enchs.add(cleaned);
                        }
                    }
                    if (name.isEmpty()) name = "Set " + capitalize(itemKey);
                    return addSet(name, itemKey, enchs);
                }
            }

            // 3. Fallback per vecchi codici Base64 o JSON
            String json = null;
            if (trimmed.startsWith("LITSET:")) {
                String payload = trimmed.substring("LITSET:".length()).trim();
                try {
                    byte[] bytes = Base64.getDecoder().decode(payload);
                    json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            } else if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                json = trimmed;
            } else {
                try {
                    byte[] bytes = Base64.getDecoder().decode(trimmed);
                    json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }

            if (json != null) {
                ExportedSet exp = GSON.fromJson(json, ExportedSet.class);
                if (exp != null && exp.itemKey != null && !exp.itemKey.trim().isEmpty()) {
                    String finalName = (exp.name != null && !exp.name.trim().isEmpty()) ? exp.name.trim() : ("Set " + capitalize(exp.itemKey));
                    List<String> cleanList = new ArrayList<>();
                    if (exp.enchantments != null) {
                        for (String s : exp.enchantments) {
                            String c = cleanEnchantId(s);
                            if (!c.isEmpty()) cleanList.add(c);
                        }
                    }
                    return addSet(finalName, normalizeItemKey(exp.itemKey), cleanList);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

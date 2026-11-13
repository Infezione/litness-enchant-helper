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

    public static class ExportedSet {
        public String name;
        public String itemKey;
        public List<String> enchantments = new ArrayList<>();
    }

    public static String exportSetToString(CustomSet set) {
        if (set == null) return null;
        try {
            ExportedSet exp = new ExportedSet();
            exp.name = set.name;
            exp.itemKey = set.itemKey;
            exp.enchantments = new ArrayList<>(set.enchantments);
            String json = GSON.toJson(exp);
            String b64 = Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "LITSET:" + b64;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static CustomSet importSetFromString(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String trimmed = input.trim();
        try {
            String json = null;
            if (trimmed.startsWith("LITSET:")) {
                String payload = trimmed.substring("LITSET:".length()).trim();
                if (payload.contains("|")) {
                    String[] parts = payload.split("\\|", 3);
                    if (parts.length >= 2) {
                        String name = parts[0];
                        String itemKey = parts[1];
                        List<String> enchs = new ArrayList<>();
                        if (parts.length > 2 && !parts[2].isEmpty()) {
                            enchs.addAll(Arrays.asList(parts[2].split(",")));
                        }
                        return addSet(name, itemKey, enchs);
                    }
                } else {
                    byte[] bytes = Base64.getDecoder().decode(payload);
                    json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                }
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
                    return addSet(finalName, exp.itemKey.trim().toLowerCase(Locale.ROOT), exp.enchantments);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

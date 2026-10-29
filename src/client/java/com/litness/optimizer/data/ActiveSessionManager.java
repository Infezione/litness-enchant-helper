package com.litness.optimizer.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.*;

public class ActiveSessionManager {

    private static class SessionData {
        int activeItemIndex = 0;
        Map<String, List<String>> enchantsByItem = new HashMap<>();
        String activeSetId = null;
        String pinnedSetId = null;
        List<String> searchEnchants = new ArrayList<>();
    }

    private static final File CONFIG_FILE = new File("config/litnessoptimizer/active_session.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SessionData sessionData = new SessionData();
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!CONFIG_FILE.exists()) return;
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE.toPath())) {
            SessionData data = GSON.fromJson(reader, SessionData.class);
            if (data != null) {
                if (data.enchantsByItem == null) data.enchantsByItem = new HashMap<>();
                if (data.searchEnchants == null) data.searchEnchants = new ArrayList<>();
                sessionData = data;
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
                GSON.toJson(sessionData, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized int getActiveItemIndex() {
        load();
        return sessionData.activeItemIndex;
    }

    public static synchronized void setActiveItemIndex(int index) {
        load();
        sessionData.activeItemIndex = index;
        save();
    }

    public static synchronized Set<String> getSelectedEnchants(String itemKey) {
        load();
        if (itemKey == null) return Collections.emptySet();
        String key = itemKey.trim().toLowerCase();
        List<String> list = sessionData.enchantsByItem.get(key);
        if (list != null && !list.isEmpty()) {
            return new LinkedHashSet<>(list);
        }
        // Se non c'è una selezione esplicita, controlliamo se l'utente ha un set salvato per questo item
        for (SavedSetsManager.CustomSet cs : SavedSetsManager.getSavedSets()) {
            if (cs.itemKey.equalsIgnoreCase(key) && !cs.enchantments.isEmpty()) {
                return new LinkedHashSet<>(cs.enchantments);
            }
        }
        return Collections.emptySet();
    }

    public static synchronized void setSelectedEnchants(String itemKey, Collection<String> enchants) {
        load();
        if (itemKey == null) return;
        String key = itemKey.trim().toLowerCase();
        if (enchants == null || enchants.isEmpty()) {
            sessionData.enchantsByItem.remove(key);
        } else {
            sessionData.enchantsByItem.put(key, new ArrayList<>(enchants));
        }
        save();
    }

    public static synchronized void clearSelectedEnchants(String itemKey) {
        load();
        if (itemKey == null) return;
        sessionData.enchantsByItem.remove(itemKey.trim().toLowerCase());
        save();
    }

    public static synchronized String getActiveSetId() {
        load();
        return sessionData.activeSetId;
    }

    public static synchronized void setActiveSetId(String id) {
        load();
        sessionData.activeSetId = id;
        save();
    }

    public static synchronized String getPinnedSetId() {
        load();
        return sessionData.pinnedSetId;
    }

    public static synchronized void setPinnedSetId(String id) {
        load();
        sessionData.pinnedSetId = id;
        save();
    }

    public static synchronized boolean isSetPinned(String id) {
        if (id == null) return false;
        load();
        return id.equalsIgnoreCase(sessionData.pinnedSetId);
    }

    public static synchronized void togglePinSet(String id) {
        load();
        if (id != null && id.equalsIgnoreCase(sessionData.pinnedSetId)) {
            sessionData.pinnedSetId = null;
        } else {
            sessionData.pinnedSetId = id;
        }
        save();
    }

    public static synchronized Set<String> getSearchEnchants() {
        load();
        if (sessionData.searchEnchants == null) {
            sessionData.searchEnchants = new ArrayList<>();
        }
        return new LinkedHashSet<>(sessionData.searchEnchants);
    }

    public static synchronized boolean isSearchEnchant(String enchantId) {
        if (enchantId == null) return false;
        load();
        if (sessionData.searchEnchants == null) return false;
        String clean = enchantId.trim().toLowerCase();
        for (String s : sessionData.searchEnchants) {
            if (s.equalsIgnoreCase(clean)) return true;
            if (s.toLowerCase().startsWith(clean + ":")) return true;
            int colon = s.indexOf(':');
            if (colon > 0 && s.substring(0, colon).equalsIgnoreCase(clean)) return true;
        }
        return false;
    }

    public static synchronized void toggleSearchEnchant(String enchantId) {
        if (enchantId == null) return;
        load();
        if (sessionData.searchEnchants == null) {
            sessionData.searchEnchants = new ArrayList<>();
        }
        String clean = enchantId.trim().toLowerCase();
        int colon = clean.indexOf(':');
        String baseId = colon > 0 ? clean.substring(0, colon) : clean;

        boolean removed = sessionData.searchEnchants.removeIf(s -> {
            String sClean = s.trim().toLowerCase();
            if (sClean.equals(clean)) return true;
            int c = sClean.indexOf(':');
            String sBase = c > 0 ? sClean.substring(0, c) : sClean;
            return sBase.equals(baseId);
        });

        if (!removed) {
            sessionData.searchEnchants.add(enchantId);
        }
        save();
    }

    public static synchronized void clearSearchEnchants() {
        load();
        if (sessionData.searchEnchants != null) {
            sessionData.searchEnchants.clear();
            save();
        }
    }
}

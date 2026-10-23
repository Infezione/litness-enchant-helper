package com.litness.optimizer.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.*;

public class PinnedEnchantmentsManager {
    private static final Set<String> pinnedIds = new LinkedHashSet<>();
    private static final File CONFIG_FILE = new File("config/litnessoptimizer/pinned_enchants.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded = false;
    private static int version = 0;

    public static synchronized int getVersion() {
        return version;
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!CONFIG_FILE.exists()) return;
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE.toPath())) {
            Set<String> set = GSON.fromJson(reader, new TypeToken<Set<String>>() {}.getType());
            if (set != null) {
                pinnedIds.addAll(set);
                version++;
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
                GSON.toJson(pinnedIds, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized boolean isPinned(String id) {
        load();
        if (id == null) return false;
        String clean = id.trim().toLowerCase();
        if (pinnedIds.contains(clean)) return true;
        if (clean.contains("defense") && pinnedIds.contains(clean.replace("defense", "defence"))) return true;
        if (clean.contains("defence") && pinnedIds.contains(clean.replace("defence", "defense"))) return true;
        if (pinnedIds.contains(clean.replace('-', '_'))) return true;
        if (pinnedIds.contains(clean.replace('_', '-'))) return true;
        return false;
    }

    public static synchronized void setPinned(String id, boolean pin) {
        load();
        if (id == null) return;
        String clean = id.trim().toLowerCase();
        if (pin) {
            pinnedIds.add(clean);
        } else {
            pinnedIds.remove(clean);
            pinnedIds.remove(clean.replace("defense", "defence"));
            pinnedIds.remove(clean.replace("defence", "defense"));
            pinnedIds.remove(clean.replace('-', '_'));
            pinnedIds.remove(clean.replace('_', '-'));
        }
        version++;
        save();
    }

    public static synchronized void togglePinned(String id) {
        setPinned(id, !isPinned(id));
    }

    public static synchronized void pinAll(Collection<String> ids) {
        load();
        if (ids != null) {
            for (String id : ids) {
                if (id != null) pinnedIds.add(id.trim().toLowerCase());
            }
            version++;
            save();
        }
    }

    public static synchronized void clearAll() {
        load();
        pinnedIds.clear();
        version++;
        save();
    }

    public static synchronized Set<String> getPinnedIds() {
        load();
        return new LinkedHashSet<>(pinnedIds);
    }
}

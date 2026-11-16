package com.litness.optimizer.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;

/**
 * Gestore configurazioni e personalizzazioni del mod Litness Enchant Helper.
 */
public class ModConfig {

    public static class HighlightPreset {
        public final String name;
        public final int slot1Color;
        public final int slot1Border;
        public final int slot1PillBg;
        public final int slot2Color;
        public final int slot2Border;
        public final int slot2PillBg;

        public HighlightPreset(String name, int s1c, int s1b, int s1p, int s2c, int s2b, int s2p) {
            this.name = name;
            this.slot1Color = s1c;
            this.slot1Border = s1b;
            this.slot1PillBg = s1p;
            this.slot2Color = s2c;
            this.slot2Border = s2b;
            this.slot2PillBg = s2p;
        }
    }

    public static final HighlightPreset[] PRESETS = new HighlightPreset[] {
        new HighlightPreset("Neon Cyber (Ciano / Viola)", 0x0EA5E9, 0x38BDF8, 0xF00369A1, 0xA855F7, 0xC084FC, 0xF06D28D9),
        new HighlightPreset("Litness Fantasy (Smeraldo / Oro)", 0x10B981, 0x34D399, 0xF0047857, 0xF59E0B, 0xFBBF24, 0xF0B45309),
        new HighlightPreset("High Contrast (Blu / Arancio)", 0x3B82F6, 0x60A5FA, 0xF01D4ED8, 0xF97316, 0xFB923C, 0xF0C2410C),
        new HighlightPreset("Electric Pop (Lime / Fucsia)", 0x84CC16, 0xA3E635, 0xF04D7C0F, 0xEC4899, 0xF472B6, 0xF0BE185D),
        new HighlightPreset("Crimson Sun (Rosso / Giallo)", 0xEF4444, 0xF87171, 0xF0B91C1C, 0xEAB308, 0xFDE047, 0xF0A16207)
    };

    public static final String[] GUI_SCALE_LABELS = {
        "Auto (Adatta schermo)",
        "75% (Compatta)",
        "90% (Media)",
        "100% (Standard)",
        "115% (Ingrandita)",
        "130% (Molto Grande)"
    };

    public static final String[] LORE_DETAIL_LABELS = {
        "Completa (Costi, conflitti, descrizioni)",
        "Compatta (Solo costi base e livello)",
        "Minimale (Solo nome e livello)",
        "Disattivata (Nessuna lore aggiunta)"
    };

    // Opzioni configurabili
    public boolean showAnvilPenaltyTooltip = true;
    public int bookLoreDetailMode = 0;       // 0 = Completa, 1 = Compatta, 2 = Minimale, 3 = Disattivata
    public int colorPresetIndex = 0;         // Indice in PRESETS
    public int guiScaleMode = 0;              // 0 = Auto, 1 = 100%, 2 = 85%, 3 = 75%, 4 = 65%, 5 = 50%
    public boolean warnUsedBooksInAnvil = true;

    private static ModConfig INSTANCE = null;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File getConfigFile() {
        try {
            java.nio.file.Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            if (configDir != null) {
                File dir = configDir.resolve("litnessoptimizer").toFile();
                if (!dir.exists()) dir.mkdirs();
                return new File(dir, "mod_config.json");
            }
        } catch (Throwable ignored) {}
        File dir = new File("config/litnessoptimizer");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "mod_config.json");
    }

    private static File getFallbackMinecraftFile() {
        try {
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                File dir = new File(userHome, "AppData/Roaming/.minecraft/config/litnessoptimizer");
                if (!dir.exists()) dir.mkdirs();
                return new File(dir, "mod_config.json");
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static synchronized ModConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static synchronized void load() {
        File mainFile = getConfigFile();
        if (mainFile.exists()) {
            try (Reader r = Files.newBufferedReader(mainFile.toPath())) {
                INSTANCE = GSON.fromJson(r, ModConfig.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (INSTANCE == null) {
            File fb = getFallbackMinecraftFile();
            if (fb != null && fb.exists()) {
                try (Reader r = Files.newBufferedReader(fb.toPath())) {
                    INSTANCE = GSON.fromJson(r, ModConfig.class);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (INSTANCE == null) {
            File local = new File("config/litnessoptimizer/mod_config.json");
            if (local.exists()) {
                try (Reader r = Files.newBufferedReader(local.toPath())) {
                    INSTANCE = GSON.fromJson(r, ModConfig.class);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
    }

    public static synchronized void save() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
        try {
            File mainFile = getConfigFile();
            if (mainFile.getParentFile() != null) mainFile.getParentFile().mkdirs();
            try (Writer w = Files.newBufferedWriter(mainFile.toPath())) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            File fb = getFallbackMinecraftFile();
            if (fb != null) {
                if (fb.getParentFile() != null) fb.getParentFile().mkdirs();
                try (Writer w = Files.newBufferedWriter(fb.toPath())) {
                    GSON.toJson(INSTANCE, w);
                }
            }
        } catch (Exception ignored) {}
        try {
            File local = new File("config/litnessoptimizer/mod_config.json");
            if (local.getParentFile() != null) local.getParentFile().mkdirs();
            try (Writer w = Files.newBufferedWriter(local.toPath())) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (Exception ignored) {}
    }

    public HighlightPreset getCurrentPreset() {
        if (colorPresetIndex < 0 || colorPresetIndex >= PRESETS.length) {
            colorPresetIndex = 0;
        }
        return PRESETS[colorPresetIndex];
    }
}

package com.litness.optimizer.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class UpdateChecker {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/Infezione/litness-enchant-helper/releases/latest";
    private static final String DEFAULT_RELEASE_URL = "https://github.com/Infezione/litness-enchant-helper/releases/latest";

    public static final String CURRENT_VERSION;
    static {
        String ver = "1.0.0";
        try {
            var container = FabricLoader.getInstance().getModContainer("litnessoptimizer");
            if (container.isPresent()) {
                ver = container.get().getMetadata().getVersion().getFriendlyString();
            }
        } catch (Exception ignored) {}
        CURRENT_VERSION = ver;
    }

    private static boolean updateAvailable = false;
    private static String latestVersion = "";
    private static String releasePageUrl = DEFAULT_RELEASE_URL;
    private static String jarDownloadUrl = DEFAULT_RELEASE_URL;
    private static boolean hasNotifiedChat = false;
    private static boolean checkRunning = false;
    private static long lastCheckTime = 0;

    public static void checkForUpdatesAsync() {
        if (checkRunning) return;
        long now = System.currentTimeMillis();
        if (lastCheckTime > 0 && (now - lastCheckTime < 180_000)) return;
        checkRunning = true;
        lastCheckTime = now;

        CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(GITHUB_API_URL).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "LitnessEnchantHelper-Mod/" + CURRENT_VERSION);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                if (conn.getResponseCode() == 200) {
                    try (var reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("tag_name")) {
                            String tag = json.get("tag_name").getAsString();
                            latestVersion = tag;
                            if (json.has("html_url")) {
                                releasePageUrl = json.get("html_url").getAsString();
                            }
                            jarDownloadUrl = releasePageUrl;

                            if (json.has("assets") && json.get("assets").isJsonArray()) {
                                JsonArray assets = json.getAsJsonArray("assets");
                                for (JsonElement elem : assets) {
                                    if (elem.isJsonObject()) {
                                        JsonObject asset = elem.getAsJsonObject();
                                        String name = asset.has("name") ? asset.get("name").getAsString() : "";
                                        if (name.endsWith(".jar") && asset.has("browser_download_url")) {
                                            jarDownloadUrl = asset.get("browser_download_url").getAsString();
                                            break;
                                        }
                                    }
                                }
                            }

                            if (isNewer(tag, CURRENT_VERSION)) {
                                updateAvailable = true;
                                System.out.println("[LitnessOptimizer] Nuovo aggiornamento disponibile: " + tag + " (versione installata: " + CURRENT_VERSION + ")");
                                Minecraft client = Minecraft.getInstance();
                                if (client != null) {
                                    client.execute(() -> notifyPlayerIfUpdateAvailable(client));
                                }
                            } else {
                                System.out.println("[LitnessOptimizer] Mod aggiornata (versione " + CURRENT_VERSION + ")");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[LitnessOptimizer] Impossibile verificare aggiornamenti: " + e.getMessage());
            } finally {
                checkRunning = false;
            }
        });
    }

    public static boolean isNewer(String remote, String current) {
        if (remote == null || current == null) return false;
        String rClean = remote.trim().replaceAll("^[vV]", "");
        String cClean = current.trim().replaceAll("^[vV]", "");

        String[] rParts = rClean.split("[.-]");
        String[] cParts = cClean.split("[.-]");
        int len = Math.max(rParts.length, cParts.length);

        for (int i = 0; i < len; i++) {
            int rNum = 0;
            if (i < rParts.length) {
                try { rNum = Integer.parseInt(rParts[i]); } catch (Exception ignored) {}
            }
            int cNum = 0;
            if (i < cParts.length) {
                try { cNum = Integer.parseInt(cParts[i]); } catch (Exception ignored) {}
            }
            if (rNum > cNum) return true;
            if (rNum < cNum) return false;
        }
        return false;
    }

    public static boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public static boolean hasNotifiedChat() {
        return hasNotifiedChat;
    }

    public static String getLatestVersion() {
        return latestVersion;
    }

    public static String getReleasePageUrl() {
        return releasePageUrl;
    }

    public static String getJarDownloadUrl() {
        return jarDownloadUrl;
    }

    public static void notifyPlayerIfUpdateAvailable(Minecraft client) {
        if (client == null || client.player == null) return;
        if (!hasNotifiedChat && updateAvailable) {
            hasNotifiedChat = true;
            sendUpdateNotification(client);
        }
    }

    private static void sendUpdateNotification(Minecraft client) {
        if (client.player == null) return;

        client.player.sendSystemMessage(Component.literal("§8§m--------------------------------------------------"));
        client.player.sendSystemMessage(Component.literal("§6⚡ §e§lLITNESS ENCHANT HELPER §r§7- §a§lNUOVO AGGIORNAMENTO! §b(" + latestVersion + ")"));
        client.player.sendSystemMessage(Component.literal("§7È disponibile una nuova versione della mod con correzioni e migliorie."));

        MutableComponent downloadBtn = Component.literal("§a§n[SCARICA SUBITO .JAR]§r")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(jarDownloadUrl)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§aClicca per scaricare subito il file .jar aggiornato!")))
                );

        MutableComponent releaseBtn = Component.literal("§b§n[PAGINA RELEASE]§r")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(releasePageUrl)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§bClicca per leggere il changelog su GitHub!")))
                );

        MutableComponent line = Component.literal("👉 ").append(downloadBtn).append(" §7oppure ").append(releaseBtn);
        client.player.sendSystemMessage(line);
        client.player.sendSystemMessage(Component.literal("§8§m--------------------------------------------------"));
    }
}

package com.litness.optimizer.gui;

import com.litness.optimizer.calculator.AnvilOptimizer;
import com.litness.optimizer.data.ActiveSessionManager;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import com.litness.optimizer.data.SavedSetsManager;
import com.litness.optimizer.data.SavedSetsManager.CustomSet;
import com.litness.optimizer.tooltip.LitnessTooltipHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AhPriceManager {

    public static class AhListing implements Comparable<AhListing> {
        public final String key;
        public final String enchantId;
        public final String enchantName;
        public final int level;
        public final String seller;
        public final long price; // Prezzo complessivo comprensivo di tasse
        public final long netPrice; // Prezzo netto venditore
        public final long tax; // Tassa asta
        public final String formattedPrice;
        public final String formattedBreakdown;
        public final long seenTimestamp;

        public AhListing(String enchantId, String enchantName, int level, String seller, long price, long netPrice, long tax) {
            this.enchantId = enchantId;
            this.enchantName = enchantName;
            this.level = level;
            this.seller = (seller != null && !seller.isEmpty()) ? seller : "Sconosciuto";
            this.price = price;
            this.netPrice = netPrice > 0 ? netPrice : price;
            this.tax = tax >= 0 ? tax : 0;
            this.formattedPrice = formatPrice(price);
            if (this.tax > 0) {
                this.formattedBreakdown = "Netto: " + formatPrice(this.netPrice) + " + " + formatPrice(this.tax) + " tax";
            } else {
                this.formattedBreakdown = "Prezzo intero: " + this.formattedPrice;
            }
            this.seenTimestamp = System.currentTimeMillis();
            this.key = this.seller.toLowerCase() + ":" + this.enchantId + ":" + this.level + ":" + this.price;
        }

        public static String formatPrice(long price) {
            if (price <= 0) return "0$";
            return String.format(Locale.ITALY, "%,d$", price);
        }

        public String getDisplayNameWithLevel() {
            return AnvilOptimizer.formatEnchantName(enchantName, level);
        }

        @Override
        public int compareTo(AhListing o) {
            return Long.compare(this.price, o.price);
        }
    }

    private static final Pattern SELLER_PATTERN = Pattern.compile("(?i)(?:seller|venditore)\\s*:\\s*([a-zA-Z0-9_]{3,16})");
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?i)(?:price|prezzo)\\s*:\\s*\\$?([0-9.,kKmM]+)\\$?(?:\\s*\\(\\s*([0-9.,kKmM]+)\\$?\\s*\\+\\s*([0-9.,kKmM]+)\\$?\\s*tax\\))?");
    private static final Pattern PRICE_FALLBACK_PATTERN = Pattern.compile("(?i)^\\s*\\$\\s*([0-9.,kKmM]+)\\s*$|^\\s*([0-9.,kKmM]+)\\s*\\$\\s*$");

    private static final Map<String, AhListing> cachedListings = new ConcurrentHashMap<>();
    private static long lastScanTime = 0;
    private static Set<String> lastKnownSearchEnchants = Collections.emptySet();

    // Cache per il rilevamento veloce di /ah
    private static int cachedContainerId = -1;
    private static boolean cachedIsAh = false;
    private static long lastAhCheckTime = 0;

    // Coordinate tasto [Azzera]
    private static int btnClearX = 0;
    private static int btnClearY = 0;
    private static int btnClearW = 38;
    private static int btnClearH = 12;
    private static boolean isClearHovered = false;

    public static void clearCache() {
        cachedListings.clear();
    }

    /**
     * Riconoscimento infallibile di /ah tramite titolo e verifica degli slot
     */
    public static boolean isAuctionHouse(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null) return false;

        int menuId = screen.getMenu().containerId;
        long now = System.currentTimeMillis();
        if (menuId == cachedContainerId && (now - lastAhCheckTime < 1000)) {
            return cachedIsAh;
        }
        cachedContainerId = menuId;
        lastAhCheckTime = now;

        String title = screen.getTitle().getString();
        if (isAuctionHouseTitle(title)) {
            cachedIsAh = true;
            return true;
        }

        // Verifica contenuto degli slot (firma inconfondibile di /ah di Litness)
        Minecraft mc = Minecraft.getInstance();
        for (Slot slot : screen.getMenu().slots) {
            if (mc.player != null && slot.container == mc.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
            for (Component c : lore.lines()) {
                String s = c.getString().toLowerCase();
                if (s.contains("click to buy") || s.contains("seller:") || s.contains("venditore:")
                        || s.contains("expire:") || s.contains("scadenza:") || s.contains("tax)")) {
                    cachedIsAh = true;
                    return true;
                }
            }
        }

        cachedIsAh = false;
        return false;
    }

    private static boolean isAuctionHouseTitle(String title) {
        if (title == null) return false;
        String clean = title.replaceAll("(?i)§[0-9a-fk-orx]", "").replace('\u00A0', ' ').trim().toUpperCase();
        return clean.contains("AUCTION") || clean.contains("ASTA") || clean.contains("ASTE") || clean.contains("MERCATO")
                || clean.equals("AH") || clean.startsWith("AH ") || clean.endsWith(" AH") || clean.contains(" AH ")
                || clean.contains("BAZAAR");
    }

    public static boolean isBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK)) return true;
        String name = stack.getHoverName().getString().toLowerCase();
        return name.contains("libro") || name.contains("book");
    }

    /**
     * Restituisce gli incantesimi da monitorare:
     * Quelli espliciti di [+ Cerca AH] nel catalogo, oppure quelli mancanti del set attualmente pinnato a schermo.
     */
    public static Set<String> getEffectiveSearchEnchants() {
        Set<String> search = new LinkedHashSet<>(ActiveSessionManager.getSearchEnchants());
        if (search.isEmpty()) {
            String pinnedId = ActiveSessionManager.getPinnedSetId();
            if (pinnedId != null && !pinnedId.isEmpty()) {
                CustomSet pinned = SavedSetsManager.getSetById(pinnedId);
                if (pinned != null && pinned.enchantments != null) {
                    search.addAll(pinned.enchantments);
                }
            }
        }
        return search;
    }

    /**
     * Estrae TUTTI gli incantesimi presenti sull'oggetto in forma canonica
     */
    public static Set<String> extractDistinctEnchantments(ItemStack stack) {
        Set<String> set = new HashSet<>();
        if (stack == null || stack.isEmpty()) return set;

        // 1. STORED_ENCHANTMENTS (libri incantati standard)
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(rawId, lvl);
            if (vPlusId != null) {
                set.add(vPlusId);
            } else {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
                String id = (ee != null) ? ee.id : rawId;
                if (!id.isEmpty()) {
                    set.add(id.toLowerCase().trim());
                }
            }
        }

        // 2. ENCHANTMENTS (armi / armature / libri con componenti diretti)
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(rawId, lvl);
            if (vPlusId != null) {
                set.add(vPlusId);
            } else {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
                String id = (ee != null) ? ee.id : rawId;
                if (!id.isEmpty()) {
                    set.add(id.toLowerCase().trim());
                }
            }
        }

        // 3. Righe Lore (con pulizia dei tag client-side tipo [✓ Max I], [COMUNE], ecc.)
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            String text = c.getString();
            String strippedTags = text.replaceAll("\\[[^\\]]*\\]", "").trim();
            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(strippedTags);
            if (match != null && match.entry != null) {
                set.add(match.entry.id.toLowerCase().trim());
            } else {
                String stripped = strippedTags.replaceAll("(?i)§[0-9a-fk-orx]", "")
                        .replaceAll("(?i)^(?:incantesimo|incanto|enchantment|enchant):?\\s*", "").trim();
                if (!stripped.isEmpty() && !stripped.equalsIgnoreCase(strippedTags)) {
                    LitnessTooltipHandler.MatchedEnchantment matchStripped = LitnessTooltipHandler.matchEnchantment(stripped);
                    if (matchStripped != null && matchStripped.entry != null) {
                        set.add(matchStripped.entry.id.toLowerCase().trim());
                    }
                }
            }
        }

        // 4. Hover Name se ancora vuoto
        if (set.isEmpty()) {
            String hoverName = stack.getHoverName().getString();
            String strippedName = hoverName.replaceAll("\\[[^\\]]*\\]", "").trim();
            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(strippedName);
            if (match != null && match.entry != null) {
                set.add(match.entry.id.toLowerCase().trim());
            }
        }

        return set;
    }

    public static boolean isEnchantInSet(String enchantId, Set<String> targetEnchants) {
        if (enchantId == null || targetEnchants == null || targetEnchants.isEmpty()) return false;
        String cleanId = enchantId.trim().toLowerCase();
        for (String target : targetEnchants) {
            if (target == null) continue;
            String t = target.trim().toLowerCase();
            if (cleanId.equals(t)) return true;
            if (cleanId.replace('-', '_').equals(t.replace('-', '_'))) return true;
            if (cleanId.replace("defense", "defence").equals(t.replace("defense", "defence"))) return true;

            EnchantmentEntry targetEe = EnchantmentDatabase.getEnchantment(t);
            if (targetEe != null) {
                if (targetEe.id.equalsIgnoreCase(cleanId)) return true;
                if (cleanId.replace('-', '_').equalsIgnoreCase(targetEe.id.replace('-', '_'))) return true;
            }
        }
        return false;
    }

    /**
     * Verifica stretta per libro liscio (1 solo incantesimo in totale che matcha uno dei target)
     */
    public static boolean isCleanMatchingBook(ItemStack stack, Set<String> targetEnchants) {
        if (!isBook(stack)) return false;
        Set<String> distinct = extractDistinctEnchantments(stack);
        // TASSATIVO: esattamente 1 solo incantesimo su tutto il libro!
        if (distinct.size() != 1) return false;
        String singleId = distinct.iterator().next();
        return isEnchantInSet(singleId, targetEnchants);
    }

    /**
     * Verifica se lo stack è un libro liscio corrispondente oppure una Shulker Box / Bundle contenente libri lisci corrispondenti
     */
    public static boolean containsMatchingCleanBook(ItemStack stack, Set<String> targetEnchants) {
        if (stack == null || stack.isEmpty()) return false;

        // 1. Libro liscio diretto
        if (isCleanMatchingBook(stack, targetEnchants)) {
            return true;
        }

        // 2. Scatola di Shulker / cassa / contenitore con DataComponents.CONTAINER
        ItemContainerContents container = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (container != ItemContainerContents.EMPTY) {
            try {
                if (container.nonEmptyItemCopyStream().anyMatch(inner -> isCleanMatchingBook(inner, targetEnchants))) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // 3. Bundle (fagotto) con DataComponents.BUNDLE_CONTENTS
        BundleContents bundle = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, null);
        if (bundle != null) {
            try {
                if (bundle.itemCopyStream().anyMatch(inner -> isCleanMatchingBook(inner, targetEnchants))) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        return false;
    }

    public static long parseMoneyAmount(String str) {
        if (str == null) return 0;
        String clean = str.trim();
        if (clean.isEmpty()) return 0;

        double multiplier = 1.0;
        if (clean.toLowerCase().endsWith("m")) {
            multiplier = 1_000_000.0;
            clean = clean.substring(0, clean.length() - 1).trim();
        } else if (clean.toLowerCase().endsWith("k")) {
            multiplier = 1_000.0;
            clean = clean.substring(0, clean.length() - 1).trim();
        }

        // Se ha formato italiano come 50.000 o 1.250 (punto seguito da 3 cifre)
        if (clean.matches(".*\\d+\\.\\d{3}(?:\\.\\d{3})*.*")) {
            clean = clean.replace(".", "");
        } else if (clean.contains(",") && !clean.contains(".")) {
            clean = clean.replace(',', '.');
        } else if (clean.contains(".") && clean.contains(",")) {
            clean = clean.replace(".", "").replace(',', '.');
        }

        clean = clean.replaceAll("[^0-9.]", "").trim();
        try {
            double val = Double.parseDouble(clean);
            return (long) Math.round(val * multiplier);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int extractLevel(ItemStack stack, String enchantId) {
        if (stack == null) return 1;

        // 1. STORED_ENCHANTMENTS
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(rawId, lvl);
            if (vPlusId != null && vPlusId.equalsIgnoreCase(enchantId)) {
                return lvl;
            }
            if (rawId.equalsIgnoreCase(enchantId)) {
                return lvl;
            }
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            if (ee != null && ee.id.equalsIgnoreCase(enchantId)) {
                return lvl;
            }
        }

        // 2. Lore
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            String text = c.getString().replaceAll("\\[[^\\]]*\\]", "").trim();
            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(text);
            if (match != null && match.entry != null && match.entry.id.equalsIgnoreCase(enchantId)) {
                if (match.level > 0) return match.level;
            }
        }

        // 3. Hover Name
        String hoverName = stack.getHoverName().getString().replaceAll("\\[[^\\]]*\\]", "").trim();
        LitnessTooltipHandler.MatchedEnchantment matchName = LitnessTooltipHandler.matchEnchantment(hoverName);
        if (matchName != null && matchName.entry != null && matchName.entry.id.equalsIgnoreCase(enchantId)) {
            if (matchName.level > 0) return matchName.level;
        }

        return 1;
    }

    private static class ParsedInfo {
        String seller = "Player";
        long price = -1;
        long netPrice = -1;
        long tax = 0;
    }

    private static ParsedInfo parseSellerAndPrice(ItemStack stack) {
        ParsedInfo info = new ParsedInfo();
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component comp : lore.lines()) {
            String clean = comp.getString().replaceAll("(?i)§[0-9a-fk-orx]", "").trim();
            if (clean.isEmpty()) continue;

            if (info.seller.equals("Player")) {
                Matcher sm = SELLER_PATTERN.matcher(clean);
                if (sm.find()) {
                    info.seller = sm.group(1).trim();
                }
            }

            if (info.price < 0) {
                Matcher pm = PRICE_PATTERN.matcher(clean);
                if (pm.find()) {
                    info.price = parseMoneyAmount(pm.group(1));
                    if (pm.group(2) != null) {
                        info.netPrice = parseMoneyAmount(pm.group(2));
                    }
                    if (pm.group(3) != null) {
                        info.tax = parseMoneyAmount(pm.group(3));
                    }
                } else {
                    Matcher fb = PRICE_FALLBACK_PATTERN.matcher(clean);
                    if (fb.find()) {
                        String str = fb.group(1) != null ? fb.group(1) : fb.group(2);
                        info.price = parseMoneyAmount(str);
                    }
                }
            }
        }
        return info;
    }

    public static void scanCurrentContainer(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Set<String> searchEnchants = getEffectiveSearchEnchants();
        if (searchEnchants == null || searchEnchants.isEmpty()) return;

        // Se l'utente ha cambiato i filtri di ricerca, azzera la cache
        if (!searchEnchants.equals(lastKnownSearchEnchants)) {
            clearCache();
            lastKnownSearchEnchants = new HashSet<>(searchEnchants);
        }

        long now = System.currentTimeMillis();
        if (now - lastScanTime < 100) return;
        lastScanTime = now;

        List<Slot> slots = screen.getMenu().slots;
        for (Slot slot : slots) {
            if (slot.container == mc.player.getInventory()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !isBook(stack)) continue;

            Set<String> distinct = extractDistinctEnchantments(stack);
            // Solo libri lisci: esattamente 1 incantesimo
            if (distinct.size() != 1) continue;

            String singleId = distinct.iterator().next();
            if (!isEnchantInSet(singleId, searchEnchants)) continue;

            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(singleId);
            String eName = (ee != null) ? ee.name : singleId;
            int level = extractLevel(stack, singleId);

            ParsedInfo info = parseSellerAndPrice(stack);
            if (info.price > 0) {
                AhListing listing = new AhListing(singleId, eName, level, info.seller, info.price, info.netPrice, info.tax);
                cachedListings.put(listing.key, listing);
            }
        }
    }

    public static boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isClearHovered) {
            clearCache();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal("§aCache prezzi AH azzerata!"));
            }
            return true;
        }
        return false;
    }

    public static void renderAhSidebar(GuiGraphicsExtractor extractor, AbstractContainerScreen<?> screen, int leftPos, int topPos, int imageWidth, int imageHeight, int mouseX, int mouseY) {
        if (screen == null) return;
        if (!isAuctionHouse(screen)) return;

        scanCurrentContainer(screen);

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int sideW = 168;
        int sideX = leftPos - sideW - 6;
        if (sideX < 4 && (screen.width - (leftPos + imageWidth)) > sideW + 10) {
            sideX = leftPos + imageWidth + 6;
        }
        sideX = Math.max(4, sideX);
        int sideY = topPos;
        int sideH = imageHeight;

        // Sfondo dark glass solido
        extractor.outline(sideX - 1, sideY - 1, sideW + 2, sideH + 2, 0xFF000000);
        extractor.fill(sideX, sideY, sideX + sideW, sideY + sideH, 0xF50F111A);

        // Barra accento superiore verde smeraldo (AH / Economia)
        extractor.fill(sideX, sideY, sideX + sideW, sideY + 2, 0xFF10B981);
        extractor.outline(sideX, sideY, sideW, sideH, 0xFF2A2D40);

        // Titolo Header
        extractor.text(font, "⚡ PREZZI AH", sideX + 8, sideY + 6, 0xFFFFFFFF, true);
        extractor.text(font, "LIVE", sideX + 88, sideY + 6, 0xFF34D399, true);

        Set<String> searchEnchants = getEffectiveSearchEnchants();

        // Tasto [Azzera]
        btnClearX = sideX + sideW - btnClearW - 6;
        btnClearY = sideY + 4;
        isClearHovered = mouseX >= btnClearX && mouseX <= btnClearX + btnClearW && mouseY >= btnClearY && mouseY <= btnClearY + btnClearH;
        int btnBg = isClearHovered ? 0x60EF4444 : 0x301E293B;
        int btnBorder = isClearHovered ? 0xFFEF4444 : 0xFF334155;
        extractor.fill(btnClearX, btnClearY, btnClearX + btnClearW, btnClearY + btnClearH, btnBg);
        extractor.outline(btnClearX, btnClearY, btnClearW, btnClearH, btnBorder);
        extractor.centeredText(font, "Azzera", btnClearX + (btnClearW / 2), btnClearY + 2, isClearHovered ? 0xFFFCA5A5 : 0xFF94A3B8);

        int curY = sideY + 20;

        if (searchEnchants == null || searchEnchants.isEmpty()) {
            // Stato vuoto: Nessun incanto cercato
            int cardY = curY + 6;
            int cardH = sideH - 36;
            extractor.fill(sideX + 6, cardY, sideX + sideW - 6, cardY + cardH, 0x40161826);
            extractor.outline(sideX + 6, cardY, sideW - 12, cardH, 0xFF26293D);

            extractor.fakeItem(new ItemStack(Items.ENCHANTED_BOOK), sideX + (sideW / 2) - 8, cardY + 14);

            extractor.centeredText(font, "Nessun filtro AH", sideX + (sideW / 2), cardY + 36, 0xFFFFFFFF);
            extractor.centeredText(font, "Apri il Catalogo [N],", sideX + (sideW / 2), cardY + 52, 0xFF9CA3AF);
            extractor.centeredText(font, "premi [+ Cerca AH]", sideX + (sideW / 2), cardY + 64, 0xFF34D399);
            extractor.centeredText(font, "oppure pinna un set!", sideX + (sideW / 2), cardY + 76, 0xFF9CA3AF);
            return;
        }

        // Filtra e ordina i listing per prezzo crescente
        List<AhListing> matchingListings = new ArrayList<>();
        for (AhListing l : cachedListings.values()) {
            if (isEnchantInSet(l.enchantId, searchEnchants)) {
                matchingListings.add(l);
            }
        }
        Collections.sort(matchingListings);

        // Header info filtro
        int filterCount = searchEnchants.size();
        String filterInfo;
        if (filterCount == 1) {
            String firstId = searchEnchants.iterator().next();
            EnchantmentEntry fe = EnchantmentDatabase.getEnchantment(firstId);
            filterInfo = fe != null ? fe.name : firstId;
        } else {
            filterInfo = filterCount + " incanti cercati";
        }
        extractor.text(font, "§7Cerca: §e" + trimToWidth(font, filterInfo, sideW - 55), sideX + 8, curY, 0xFFFBBF24, false);
        curY += 10;
        extractor.text(font, "§a✔ Solo libri lisci", sideX + 8, curY, 0xFF34D399, false);
        curY += 12;

        if (matchingListings.isEmpty()) {
            // Nessun libro trovato finora
            int boxY = curY + 2;
            int boxH = sideH - (boxY - sideY) - 10;
            extractor.fill(sideX + 6, boxY, sideX + sideW - 6, boxY + boxH, 0x301E293B);
            extractor.outline(sideX + 6, boxY, sideW - 12, boxH, 0xFF334155);

            extractor.fakeItem(new ItemStack(Items.ENCHANTED_BOOK), sideX + (sideW / 2) - 8, boxY + 8);
            extractor.centeredText(font, "0 libri lisci finora", sideX + (sideW / 2), boxY + 28, 0xFFFCD34D);
            extractor.centeredText(font, "Sfoglia le pagine", sideX + (sideW / 2), boxY + 44, 0xFFE2E8F0);
            extractor.centeredText(font, "di /ah per leggere", sideX + (sideW / 2), boxY + 54, 0xFFE2E8F0);
            extractor.centeredText(font, "in automatico i prezzi!", sideX + (sideW / 2), boxY + 64, 0xFFE2E8F0);

            extractor.centeredText(font, "💡 Solo libri lisci", sideX + (sideW / 2), boxY + boxH - 14, 0xFF6B7280);
            return;
        }

        // 1. Box Miglior Prezzo (Top 1 più economico)
        AhListing best = matchingListings.get(0);
        int bestBoxY = curY;
        int bestBoxH = 40;
        extractor.fill(sideX + 5, bestBoxY, sideX + sideW - 5, bestBoxY + bestBoxH, 0x3310B981);
        extractor.outline(sideX + 5, bestBoxY, sideW - 10, bestBoxH, 0xFF10B981);

        extractor.text(font, "🏆 MIGLIOR PREZZO:", sideX + 8, bestBoxY + 3, 0xFFFBBF24, false);
        String bestName = trimToWidth(font, best.getDisplayNameWithLevel(), sideW - 24);
        extractor.text(font, bestName, sideX + 8, bestBoxY + 13, 0xFFFFFFFF, false);
        extractor.text(font, "💰 §a" + best.formattedPrice + " §7(da " + trimToWidth(font, best.seller, 55) + ")", sideX + 8, bestBoxY + 23, 0xFF34D399, false);
        extractor.text(font, "§8" + best.formattedBreakdown, sideX + 8, bestBoxY + 31, 0xFF059669, false);

        curY = bestBoxY + bestBoxH + 5;

        // 2. Lista altri prezzi trovati (fino a 6 elementi successivi)
        extractor.text(font, "Tutti i prezzi (" + matchingListings.size() + "):", sideX + 8, curY, 0xFF9CA3AF, false);
        curY += 10;

        int rowH = 16;
        int maxRows = Math.min(6, matchingListings.size());

        for (int i = 0; i < maxRows; i++) {
            AhListing l = matchingListings.get(i);
            boolean isTop = (i == 0);

            int itemY = curY + i * rowH;
            if (itemY + rowH > sideY + sideH - 16) break;

            int rowBg = isTop ? 0x2510B981 : (i % 2 == 0 ? 0x201E293B : 0x101E293B);
            extractor.fill(sideX + 5, itemY, sideX + sideW - 5, itemY + rowH - 1, rowBg);

            // Pallino o rango
            String rank = (i + 1) + ".";
            extractor.text(font, rank, sideX + 8, itemY + 3, isTop ? 0xFFFBBF24 : 0xFF6B7280, false);

            // Prezzo
            extractor.text(font, l.formattedPrice, sideX + 22, itemY + 3, isTop ? 0xFF34D399 : 0xFFE2E8F0, false);

            // Nome incanto abbreviato & venditore
            String sub = AnvilOptimizer.formatEnchantName(l.enchantName, l.level);
            String subTrimmed = trimToWidth(font, sub, 55);
            extractor.text(font, subTrimmed, sideX + 78, itemY + 3, isTop ? 0xFFA5B4FC : 0xFF94A3B8, false);

            String sellerTrimmed = trimToWidth(font, l.seller, 32);
            extractor.text(font, sellerTrimmed, sideX + sideW - 36, itemY + 3, 0xFF64748B, false);
        }

        // Footer informativo
        int footerY = sideY + sideH - 12;
        extractor.centeredText(font, "💡 Sfoglia /ah per aggiornare", sideX + (sideW / 2), footerY, 0xFF6B7280);
    }

    private static String trimToWidth(Font font, String s, int maxWidth) {
        if (s == null) return "";
        if (font.width(s) <= maxWidth) return s;
        while (s.length() > 3 && font.width(s + "...") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }
}

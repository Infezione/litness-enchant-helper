package com.litness.optimizer.gui;

import com.litness.optimizer.calculator.AnvilOptimizer;
import com.litness.optimizer.data.ActiveSessionManager;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
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
        public final long price;
        public final String formattedPrice;
        public final long seenTimestamp;

        public AhListing(String enchantId, String enchantName, int level, String seller, long price) {
            this.enchantId = enchantId;
            this.enchantName = enchantName;
            this.level = level;
            this.seller = (seller != null && !seller.isEmpty()) ? seller : "Sconosciuto";
            this.price = price;
            this.formattedPrice = formatPrice(price);
            this.seenTimestamp = System.currentTimeMillis();
            this.key = this.seller.toLowerCase() + ":" + this.enchantId + ":" + this.level + ":" + this.price;
        }

        public static String formatPrice(long price) {
            if (price <= 0) return "0$";
            return String.format(Locale.ITALY, "%,d$", price);
        }

        public String getDisplayNameWithLevel() {
            String roman = AnvilOptimizer.toRoman(level);
            return enchantName + (roman.isEmpty() ? "" : " " + roman);
        }

        @Override
        public int compareTo(AhListing o) {
            return Long.compare(this.price, o.price);
        }
    }

    private static final Pattern SELLER_PATTERN = Pattern.compile("(?i)(?:seller|venditore):\\s*([a-zA-Z0-9_]{3,16})");
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?i)(?:price|prezzo):\\s*\\$?([0-9.,kKmM]+)\\$?\\s*(?:\\(\\s*([0-9.,kKmM]+)\\$?\\s*\\+\\s*([0-9.,kKmM]+)\\$?\\s*tax\\))?");
    private static final Pattern PRICE_FALLBACK_PATTERN = Pattern.compile("(?i)^\\s*\\$\\s*([0-9.,kKmM]+)\\s*$|^\\s*([0-9.,kKmM]+)\\s*\\$\\s*$");

    private static final Map<String, AhListing> cachedListings = new ConcurrentHashMap<>();
    private static long lastScanTime = 0;
    private static Set<String> lastKnownSearchEnchants = Collections.emptySet();

    // Coordinate tasto [Azzera]
    private static int btnClearX = 0;
    private static int btnClearY = 0;
    private static int btnClearW = 38;
    private static int btnClearH = 12;
    private static boolean isClearHovered = false;

    public static void clearCache() {
        cachedListings.clear();
    }

    public static boolean isAuctionHouse(String title) {
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
     * Estrae TUTTI gli incantesimi presenti sull'oggetto in forma canonica
     */
    public static Set<String> extractDistinctEnchantments(ItemStack stack) {
        Set<String> set = new HashSet<>();
        if (stack == null || stack.isEmpty()) return set;

        // 1. STORED_ENCHANTMENTS (libri incantati standard)
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            String id = (ee != null) ? ee.id : rawId;
            if (!id.isEmpty()) {
                set.add(id.toLowerCase().trim());
            }
        }

        // 2. ENCHANTMENTS (armi / armature / libri con componenti diretti)
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            String id = (ee != null) ? ee.id : rawId;
            if (!id.isEmpty()) {
                set.add(id.toLowerCase().trim());
            }
        }

        // 3. Righe Lore
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            String text = c.getString();
            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(text);
            if (match != null && match.entry != null) {
                set.add(match.entry.id.toLowerCase().trim());
            } else {
                String stripped = text.replaceAll("(?i)§[0-9a-fk-orx]", "")
                        .replaceAll("(?i)^(?:incantesimo|incanto|enchantment|enchant):?\\s*", "").trim();
                if (!stripped.isEmpty() && !stripped.equalsIgnoreCase(text)) {
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
            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(hoverName);
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

    public static long parseMoneyAmount(String str) {
        if (str == null) return 0;
        String clean = str.replaceAll("[^0-9.,kKmM]", "").trim();
        if (clean.isEmpty()) return 0;

        double multiplier = 1.0;
        if (clean.toLowerCase().endsWith("m")) {
            multiplier = 1_000_000.0;
            clean = clean.substring(0, clean.length() - 1).trim();
        } else if (clean.toLowerCase().endsWith("k")) {
            multiplier = 1_000.0;
            clean = clean.substring(0, clean.length() - 1).trim();
        }

        clean = clean.replace(',', '.');
        int firstDot = clean.indexOf('.');
        int lastDot = clean.lastIndexOf('.');
        if (firstDot != lastDot) {
            clean = clean.replace(".", "");
        }

        try {
            double val = Double.parseDouble(clean);
            return (long) (val * multiplier);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int extractLevel(ItemStack stack, String enchantId) {
        // 1. STORED_ENCHANTMENTS
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            if (rawId.equalsIgnoreCase(enchantId)) {
                return entry.getIntValue();
            }
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            if (ee != null && ee.id.equalsIgnoreCase(enchantId)) {
                return entry.getIntValue();
            }
        }

        // 2. Lore
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(c.getString());
            if (match != null && match.entry != null && match.entry.id.equalsIgnoreCase(enchantId)) {
                if (match.level > 0) return match.level;
            }
        }

        // 3. Hover Name
        LitnessTooltipHandler.MatchedEnchantment matchName = LitnessTooltipHandler.matchEnchantment(stack.getHoverName().getString());
        if (matchName != null && matchName.entry != null && matchName.entry.id.equalsIgnoreCase(enchantId)) {
            if (matchName.level > 0) return matchName.level;
        }

        return 1;
    }

    private static class ParsedInfo {
        String seller = "Player";
        long price = -1;
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
                    String str = pm.group(1);
                    info.price = parseMoneyAmount(str);
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

        Set<String> searchEnchants = ActiveSessionManager.getSearchEnchants();
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
        int maxSlot = Math.min(45, slots.size());

        for (int i = 0; i < maxSlot; i++) {
            Slot slot = slots.get(i);
            if (slot.container == mc.player.getInventory()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !isBook(stack)) continue;

            Set<String> distinct = extractDistinctEnchantments(stack);
            // Solo libri lisci (1 incantesimo)
            if (distinct.size() != 1) continue;

            String singleId = distinct.iterator().next();
            if (!isEnchantInSet(singleId, searchEnchants)) continue;

            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(singleId);
            String eName = (ee != null) ? ee.name : singleId;
            int level = extractLevel(stack, singleId);

            ParsedInfo info = parseSellerAndPrice(stack);
            if (info.price > 0) {
                AhListing listing = new AhListing(singleId, eName, level, info.seller, info.price);
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
        String title = screen.getTitle().getString();
        if (!isAuctionHouse(title)) return;

        scanCurrentContainer(screen);

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int sideW = 158;
        int sideX = leftPos - sideW - 6;
        if (sideX < 4 && (screen.width - (leftPos + imageWidth)) > sideW + 10) {
            sideX = leftPos + imageWidth + 6;
        }
        sideX = Math.max(4, sideX);
        int sideY = topPos;
        int sideH = imageHeight;

        // Sfondo dark glass solido
        extractor.outline(sideX - 1, sideY - 1, sideW + 2, sideH + 2, 0xFF000000);
        extractor.fill(sideX, sideY, sideX + sideW, sideY + sideH, 0xF20F111A);

        // Barra accento superiore verde smeraldo (AH / Economia)
        extractor.fill(sideX, sideY, sideX + sideW, sideY + 2, 0xFF10B981);
        extractor.outline(sideX, sideY, sideW, sideH, 0xFF2A2D40);

        // Titolo Header
        extractor.text(font, "⚡ PREZZI AH (LITNESS)", sideX + 8, sideY + 6, 0xFF34D399, false);

        Set<String> searchEnchants = ActiveSessionManager.getSearchEnchants();

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
            extractor.centeredText(font, "e premi [+ Cerca AH]", sideX + (sideW / 2), cardY + 62, 0xFF34D399);
            extractor.centeredText(font, "per monitorare", sideX + (sideW / 2), cardY + 72, 0xFF9CA3AF);
            extractor.centeredText(font, "i prezzi dei libri lisci!", sideX + (sideW / 2), cardY + 82, 0xFF9CA3AF);
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
        String filterInfo = filterCount == 1 ? "1 incanto cercato" : filterCount + " incanti cercati";
        extractor.text(font, "§7Filtro: §a" + filterInfo, sideX + 8, curY, 0xFFE2E8F0, false);
        curY += 12;

        if (matchingListings.isEmpty()) {
            // Nessun libro trovato finora
            int boxY = curY + 4;
            int boxH = sideH - (boxY - sideY) - 10;
            extractor.fill(sideX + 6, boxY, sideX + sideW - 6, boxY + boxH, 0x301E293B);
            extractor.outline(sideX + 6, boxY, sideW - 12, boxH, 0xFF334155);

            extractor.centeredText(font, "0 libri lisci finora", sideX + (sideW / 2), boxY + 20, 0xFFFCD34D);
            extractor.centeredText(font, "Sfoglia le pagine", sideX + (sideW / 2), boxY + 38, 0xFFE2E8F0);
            extractor.centeredText(font, "di /ah per leggere", sideX + (sideW / 2), boxY + 48, 0xFFE2E8F0);
            extractor.centeredText(font, "in automatico i prezzi!", sideX + (sideW / 2), boxY + 58, 0xFFE2E8F0);

            extractor.centeredText(font, "💡 Solo libri lisci", sideX + (sideW / 2), boxY + boxH - 18, 0xFF6B7280);
            return;
        }

        // 1. Box Miglior Prezzo (Top 1 più economico)
        AhListing best = matchingListings.get(0);
        int bestBoxY = curY + 2;
        int bestBoxH = 38;
        extractor.fill(sideX + 6, bestBoxY, sideX + sideW - 6, bestBoxY + bestBoxH, 0x4010B981);
        extractor.outline(sideX + 6, bestBoxY, sideW - 12, bestBoxH, 0xFF10B981);

        extractor.text(font, "🏆 MIGLIOR PREZZO:", sideX + 10, bestBoxY + 4, 0xFFFBBF24, false);
        String bestName = trimToWidth(font, best.getDisplayNameWithLevel(), sideW - 24);
        extractor.text(font, bestName, sideX + 10, bestBoxY + 14, 0xFFFFFFFF, false);
        extractor.text(font, "💰 §a" + best.formattedPrice + " §7(da " + trimToWidth(font, best.seller, 55) + ")", sideX + 10, bestBoxY + 25, 0xFF34D399, false);

        curY = bestBoxY + bestBoxH + 6;

        // 2. Lista altri prezzi trovati (fino a 6 elementi successivi)
        extractor.text(font, "Tutti i prezzi (" + matchingListings.size() + "):", sideX + 8, curY, 0xFF9CA3AF, false);
        curY += 10;

        int rowH = 17;
        int maxRows = Math.min(6, matchingListings.size());

        for (int i = 0; i < maxRows; i++) {
            AhListing l = matchingListings.get(i);
            boolean isTop = (i == 0);

            int itemY = curY + i * rowH;
            if (itemY + rowH > sideY + sideH - 18) break;

            int rowBg = isTop ? 0x2010B981 : (i % 2 == 0 ? 0x201E293B : 0x101E293B);
            extractor.fill(sideX + 6, itemY, sideX + sideW - 6, itemY + rowH - 1, rowBg);

            // Pallino o rango
            String rank = (i + 1) + ".";
            extractor.text(font, rank, sideX + 8, itemY + 4, isTop ? 0xFF34D399 : 0xFF6B7280, false);

            // Prezzo
            extractor.text(font, l.formattedPrice, sideX + 22, itemY + 4, isTop ? 0xFFFBBF24 : 0xFFE2E8F0, false);

            // Nome incanto abbreviato & venditore
            String sub = l.enchantName + " " + AnvilOptimizer.toRoman(l.level);
            String subTrimmed = trimToWidth(font, sub, 55);
            extractor.text(font, subTrimmed, sideX + 78, itemY + 4, isTop ? 0xFFA5B4FC : 0xFF94A3B8, false);

            String sellerTrimmed = trimToWidth(font, l.seller, 32);
            extractor.text(font, sellerTrimmed, sideX + sideW - 38, itemY + 4, 0xFF64748B, false);
        }

        // Footer informativo
        int footerY = sideY + sideH - 14;
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

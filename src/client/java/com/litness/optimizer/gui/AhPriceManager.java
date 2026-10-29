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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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

    public static class AutocompleteSuggestion {
        public final String enchantId;
        public final int level;
        public final String searchKey;
        public final String displayName;
        public final String badge;
        public final int badgeColor;

        public AutocompleteSuggestion(String enchantId, int level, String searchKey, String displayName, String badge, int badgeColor) {
            this.enchantId = enchantId;
            this.level = level;
            this.searchKey = searchKey;
            this.displayName = displayName;
            this.badge = badge;
            this.badgeColor = badgeColor;
        }
    }

    public static class EnchantFilter {
        public final String original;
        public final String enchantId;
        public final int targetLevel; // > 0 se livello specifico richiesto

        public EnchantFilter(String original, String enchantId, int targetLevel) {
            this.original = original;
            this.enchantId = enchantId;
            this.targetLevel = targetLevel;
        }
    }

    private static final Pattern SELLER_PATTERN = Pattern.compile("(?i)(?:seller|venditore)\\s*[:\\-]?\\s*(?:\\[[^\\]]*\\]\\s*)?([a-zA-Z0-9_]{3,16})");
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?i)(?:price|prezzo)\\s*:\\s*\\$?([0-9.,kKmM]+)\\$?(?:\\s*\\(\\s*([0-9.,kKmM]+)\\$?\\s*\\+\\s*([0-9.,kKmM]+)\\$?\\s*tax\\))?");
    private static final Pattern PRICE_FALLBACK_PATTERN = Pattern.compile("(?i)^\\s*\\$\\s*([0-9.,kKmM]+)\\s*$|^\\s*([0-9.,kKmM]+)\\s*\\$\\s*$");

    private static final Map<String, AhListing> cachedListings = new ConcurrentHashMap<>();
    private static final Map<String, Long> currentScreenMinPrices = new ConcurrentHashMap<>();
    private static Set<String> lastKnownSearchEnchants = Collections.emptySet();

    // Barra di ricerca rapida nella sidebar AH
    public static EditBox searchBox = null;
    public static String searchBoxQuery = "";
    private static final List<AutocompleteSuggestion> currentSuggestions = new ArrayList<>();
    private static int hoveredSuggestionIndex = -1;
    private static int dropX = 0;
    private static int dropY = 0;
    private static int dropW = 0;
    private static int dropH = 0;

    // Cache per il rilevamento veloce di /ah
    private static int cachedContainerId = -1;
    private static boolean cachedIsAh = false;

    // Coordinate tasto [Azzera]
    private static int btnClearX = 0;
    private static int btnClearY = 0;
    private static int btnClearW = 38;
    private static int btnClearH = 12;
    private static boolean isClearHovered = false;

    // Coordinate barra di ricerca
    private static int lastSearchBoxX = 0;
    private static int lastSearchBoxY = 0;
    private static int lastSearchBoxW = 0;
    private static int lastSearchBoxH = 0;

    public static void clearCache() {
        cachedListings.clear();
        currentScreenMinPrices.clear();
    }

    public static boolean isCheapestOnScreen(ItemStack stack) {
        if (stack == null || stack.isEmpty() || currentScreenMinPrices.isEmpty()) return false;
        if (!isBook(stack)) return false;
        Set<String> distinct = extractDistinctEnchantments(stack);
        if (distinct.size() != 1) return false;
        String singleId = distinct.iterator().next();
        int bookLevel = extractLevel(stack, singleId);

        String matchKey = singleId + ":" + bookLevel;
        Long min = currentScreenMinPrices.get(matchKey);
        if (min == null || min <= 0) {
            min = currentScreenMinPrices.get(singleId);
        }
        if (min == null || min <= 0) return false;
        ParsedInfo info = parseSellerAndPrice(stack);
        return info.price > 0 && info.price <= min;
    }

    public static boolean isCheapestSlot(int slotIndex) {
        return false;
    }

    /**
     * Riconoscimento infallibile di /ah tramite titolo e verifica degli slot
     */
    public static boolean isAuctionHouse(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null) return false;

        int menuId = screen.getMenu().containerId;
        if (menuId == cachedContainerId && cachedIsAh) {
            return true;
        }

        String title = screen.getTitle().getString();
        if (isAuctionHouseTitle(title)) {
            cachedContainerId = menuId;
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
                    cachedContainerId = menuId;
                    cachedIsAh = true;
                    return true;
                }
            }
        }

        cachedContainerId = menuId;
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
     * Quelli espliciti di [+ Cerca] nel catalogo, oppure quelli mancanti del set attualmente pinnato a schermo.
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

    public static EnchantFilter parseFilter(String target) {
        if (target == null || target.trim().isEmpty()) return null;
        String t = target.trim();
        int colon = t.indexOf(':');
        if (colon > 0) {
            String id = t.substring(0, colon).trim().toLowerCase();
            int lvl = 0;
            try {
                lvl = Integer.parseInt(t.substring(colon + 1).trim());
            } catch (Exception ignored) {}
            if (lvl <= 0) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(id);
                lvl = (ee != null && ee.maxLevel > 1) ? ee.maxLevel : 1;
            }
            return new EnchantFilter(t, id, lvl);
        } else {
            String id = t.toLowerCase();
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(id);
            int lvl = (ee != null && ee.maxLevel > 1) ? ee.maxLevel : 1;
            return new EnchantFilter(t, id, lvl);
        }
    }

    public static boolean isIdMatching(String enchantId, String targetId) {
        if (enchantId == null || targetId == null) return false;
        String a = enchantId.trim().toLowerCase();
        String b = targetId.trim().toLowerCase();
        if (a.equals(b)) return true;
        if (a.replace('-', '_').equals(b.replace('-', '_'))) return true;
        if (a.replace("defense", "defence").equals(b.replace("defense", "defence"))) return true;
        EnchantmentEntry targetEe = EnchantmentDatabase.getEnchantment(b);
        if (targetEe != null) {
            if (targetEe.id.equalsIgnoreCase(a)) return true;
            if (a.replace('-', '_').equalsIgnoreCase(targetEe.id.replace('-', '_'))) return true;
        }
        return false;
    }

    public static boolean matchesFilter(String bookEnchantId, int bookLevel, EnchantFilter filter) {
        if (filter == null || bookEnchantId == null) return false;
        if (!isIdMatching(bookEnchantId, filter.enchantId)) return false;
        if (filter.targetLevel > 0 && bookLevel > 0) {
            return bookLevel == filter.targetLevel;
        }
        return true;
    }

    public static boolean matchesAnyFilter(String bookEnchantId, int bookLevel, Collection<EnchantFilter> filters) {
        if (filters == null || filters.isEmpty() || bookEnchantId == null) return false;
        for (EnchantFilter f : filters) {
            if (matchesFilter(bookEnchantId, bookLevel, f)) return true;
        }
        return false;
    }

    public static List<EnchantFilter> getEffectiveFilters() {
        Set<String> enchants = getEffectiveSearchEnchants();
        List<EnchantFilter> list = new ArrayList<>();
        for (String s : enchants) {
            EnchantFilter f = parseFilter(s);
            if (f != null) list.add(f);
        }
        return list;
    }

    public static boolean isEnchantInSet(String enchantId, Set<String> targetEnchants) {
        if (enchantId == null || targetEnchants == null || targetEnchants.isEmpty()) return false;
        List<EnchantFilter> filters = new ArrayList<>();
        for (String t : targetEnchants) {
            EnchantFilter f = parseFilter(t);
            if (f != null) filters.add(f);
        }
        for (EnchantFilter f : filters) {
            if (isIdMatching(enchantId, f.enchantId)) return true;
        }
        return false;
    }

    /**
     * Verifica stretta per libro liscio (1 solo incantesimo in totale che matcha uno dei target al livello esatto)
     */
    public static boolean isCleanMatchingBook(ItemStack stack, Set<String> targetEnchants) {
        if (!isBook(stack)) return false;
        Set<String> distinct = extractDistinctEnchantments(stack);
        // TASSATIVO: esattamente 1 solo incantesimo su tutto il libro!
        if (distinct.size() != 1) return false;
        String singleId = distinct.iterator().next();
        int bookLevel = extractLevel(stack, singleId);

        List<EnchantFilter> filters = new ArrayList<>();
        for (String t : targetEnchants) {
            EnchantFilter f = parseFilter(t);
            if (f != null) filters.add(f);
        }
        return matchesAnyFilter(singleId, bookLevel, filters);
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
        String s = str.trim();
        if (s.isEmpty()) return 0;

        double mult = 1.0;
        String lower = s.toLowerCase();
        if (lower.endsWith("kkk") || lower.endsWith("b")) {
            mult = 1_000_000_000.0;
            s = s.replaceAll("(?i)(?:kkk|b)$", "").trim();
        } else if (lower.endsWith("kk") || lower.endsWith("m")) {
            mult = 1_000_000.0;
            s = s.replaceAll("(?i)(?:kk|m)$", "").trim();
        } else if (lower.endsWith("k")) {
            mult = 1_000.0;
            s = s.replaceAll("(?i)k$", "").trim();
        }

        // Rimuove qualsiasi simbolo non numerico tranne virgola e punto
        s = s.replaceAll("[^0-9.,]", "").trim();
        if (s.isEmpty()) return 0;

        // Gestione formati valuta con separatore delle migliaia:
        // Es. "2,688" o "2,400" (formato con virgola migliaia) -> "2688"
        // Es. "50.000" o "1.500.000" (formato europeo con punto migliaia) -> "50000"
        if (s.contains(",") && s.contains(".")) {
            int lastComma = s.lastIndexOf(',');
            int lastDot = s.lastIndexOf('.');
            if (lastDot > lastComma) {
                // "1,250,000.50" -> virgola separa migliaia, punto separa decimali
                s = s.replace(",", "");
            } else {
                // "1.250.000,50" -> punto separa migliaia, virgola separa decimali
                s = s.replace(".", "").replace(',', '.');
            }
        } else if (s.contains(",")) {
            // Se la virgola è seguita da esattamente 3 cifre finali, è un separatore di migliaia (es: 2,688 o 1,500,000)
            if (s.matches("^\\d{1,3}(?:,\\d{3})+$") || s.matches("^\\d+,\\d{3}$")) {
                s = s.replace(",", "");
            } else {
                s = s.replace(',', '.');
            }
        } else if (s.contains(".")) {
            // Se il punto è seguito da esattamente 3 cifre finali (es: 50.000 o 1.500.000)
            if (s.matches("^\\d{1,3}(?:\\.\\d{3})+$") || s.matches("^\\d+\\.\\d{3}$")) {
                s = s.replace(".", "");
            }
        }

        try {
            double val = Double.parseDouble(s);
            return (long) Math.round(val * mult);
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
        String seller = "Sconosciuto";
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

            if (info.seller.equals("Sconosciuto")) {
                Matcher sm = SELLER_PATTERN.matcher(clean);
                if (sm.find()) {
                    info.seller = sm.group(1).trim();
                } else {
                    String lower = clean.toLowerCase();
                    if (lower.contains("seller") || lower.contains("venditore")) {
                        int idx = Math.max(lower.indexOf("seller"), lower.indexOf("venditore"));
                        String after = clean.substring(idx).replaceAll("(?i)^(?:seller|venditore)\\s*[:\\-]?\\s*", "").trim();
                        after = after.replaceAll("^\\[[^\\]]*\\]\\s*", "").trim();
                        String[] words = after.split("[\\s,;]+");
                        for (String w : words) {
                            String cand = w.replaceAll("[^a-zA-Z0-9_]", "");
                            if (cand.length() >= 3 && cand.length() <= 16) {
                                info.seller = cand;
                                break;
                            }
                        }
                    }
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
        if (searchEnchants == null || searchEnchants.isEmpty()) {
            currentScreenMinPrices.clear();
            return;
        }

        // Se l'utente ha cambiato i filtri di ricerca, azzera la cache
        if (!searchEnchants.equals(lastKnownSearchEnchants)) {
            clearCache();
            lastKnownSearchEnchants = new HashSet<>(searchEnchants);
        }

        List<Slot> slots = screen.getMenu().slots;
        Map<String, Long> minPriceOnScreen = new HashMap<>();
        List<EnchantFilter> filters = getEffectiveFilters();

        for (Slot slot : slots) {
            if (slot.container == mc.player.getInventory()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !isBook(stack)) continue;

            Set<String> distinct = extractDistinctEnchantments(stack);
            // Solo libri lisci: esattamente 1 incantesimo
            if (distinct.size() != 1) continue;

            String singleId = distinct.iterator().next();
            int level = extractLevel(stack, singleId);

            if (!matchesAnyFilter(singleId, level, filters)) continue;

            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(singleId);
            String eName = (ee != null) ? ee.name : singleId;

            ParsedInfo info = parseSellerAndPrice(stack);
            if (info.price > 0) {
                AhListing listing = new AhListing(singleId, eName, level, info.seller, info.price, info.netPrice, info.tax);
                cachedListings.put(listing.key, listing);

                String matchKey = singleId + ":" + level;
                long currentMin = minPriceOnScreen.getOrDefault(matchKey, Long.MAX_VALUE);
                if (info.price < currentMin) {
                    minPriceOnScreen.put(matchKey, info.price);
                }
                long generalMin = minPriceOnScreen.getOrDefault(singleId, Long.MAX_VALUE);
                if (info.price < generalMin) {
                    minPriceOnScreen.put(singleId, info.price);
                }
            }
        }

        currentScreenMinPrices.clear();
        currentScreenMinPrices.putAll(minPriceOnScreen);
    }

    public static void initSearchWidget(AbstractContainerScreen<?> screen, Consumer<EditBox> widgetAdder) {
        Minecraft mc = Minecraft.getInstance();
        searchBox = new EditBox(mc.font, 0, 0, 100, 16, Component.literal(""));
        searchBox.setHint(Component.literal("🔍 Cerca incanto..."));
        searchBox.setMaxLength(30);
        searchBox.setResponder(text -> {
            searchBoxQuery = text;
            updateAutocompleteSuggestions(text);
        });
        searchBox.visible = true;
        widgetAdder.accept(searchBox);
    }

    public static void onContainerClosed() {
        if (searchBox != null) {
            searchBox.setValue("");
            searchBox.setFocused(false);
            searchBox.visible = false;
        }
        currentSuggestions.clear();
        hoveredSuggestionIndex = -1;
    }

    public static boolean handleKeyPressed(KeyEvent event) {
        if (searchBox != null && searchBox.isFocused() && searchBox.visible) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                searchBox.setFocused(false);
                currentSuggestions.clear();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER) {
                if (!currentSuggestions.isEmpty()) {
                    AutocompleteSuggestion s = currentSuggestions.get(0);
                    ActiveSessionManager.toggleSearchEnchant(s.searchKey);
                    searchBox.setValue("");
                    searchBox.setFocused(false);
                    currentSuggestions.clear();
                    hoveredSuggestionIndex = -1;
                    clearCache();
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.sendOverlayMessage(Component.literal("§a✔ Filtro attivato: " + s.displayName));
                    }
                    return true;
                } else {
                    searchBox.setFocused(false);
                    return true;
                }
            }
            searchBox.keyPressed(event);
            return true; // Blocca i tasti del contenitore (es. 'E' che chiude l'inventario) mentre scrivi
        }
        return false;
    }

    public static void updateAutocompleteSuggestions(String query) {
        currentSuggestions.clear();
        hoveredSuggestionIndex = -1;
        if (query == null || query.trim().isEmpty()) return;
        String q = query.trim().toLowerCase();

        int requestedLevel = 0;
        String queryBase = q;
        Matcher numMatcher = Pattern.compile("^(.*?)\\s+([0-9ivx]+)$").matcher(q);
        if (numMatcher.find()) {
            queryBase = numMatcher.group(1).trim();
            requestedLevel = LitnessTooltipHandler.parseRomanOrInt(numMatcher.group(2).trim());
        }

        List<EnchantmentEntry> all = EnchantmentDatabase.getAllEnchantments();
        Set<String> addedKeys = new HashSet<>();

        for (EnchantmentEntry ee : all) {
            if (ee == null) continue;
            String nameLower = ee.name.toLowerCase();
            String idLower = ee.id.toLowerCase();

            boolean match = nameLower.contains(queryBase) || idLower.contains(queryBase);
            if (!match) {
                if (idLower.startsWith(queryBase) || nameLower.startsWith(queryBase)) match = true;
                if ("sharpness".startsWith(queryBase) && (idLower.contains("sharpness") || nameLower.contains("affilatezza"))) match = true;
                if ("protection".startsWith(queryBase) && (idLower.contains("protection") || nameLower.contains("protezione"))) match = true;
                if ("unbreaking".startsWith(queryBase) && (idLower.contains("unbreaking") || nameLower.contains("indistruttib"))) match = true;
                if ("feather".startsWith(queryBase) && (idLower.contains("feather") || nameLower.contains("atterraggio"))) match = true;
                if ("fortune".startsWith(queryBase) && (idLower.contains("fortune") || nameLower.contains("fortuna"))) match = true;
                if ("looting".startsWith(queryBase) && (idLower.contains("looting") || nameLower.contains("saccheggio"))) match = true;
                if ("mending".startsWith(queryBase) && (idLower.contains("mending") || nameLower.contains("ripristino"))) match = true;
                if ("depth".startsWith(queryBase) && (idLower.contains("depth") || nameLower.contains("passo anfibio"))) match = true;
                if ("fire".startsWith(queryBase) && (idLower.contains("fire") || nameLower.contains("fuoco"))) match = true;
            }

            if (match) {
                boolean isVanillaPlus = "vanilla_plus".equalsIgnoreCase(ee.category);
                boolean isCustom = "custom".equalsIgnoreCase(ee.category);
                String badge = isVanillaPlus ? "[V+]" : (isCustom ? "[C]" : "[V]");
                int badgeColor = isVanillaPlus ? 0xFFF59E0B : (isCustom ? 0xFF10B981 : 0xFF818CF8);

                int targetLvl = (requestedLevel > 0 && requestedLevel <= ee.maxLevel + 1) ? requestedLevel : (ee.maxLevel > 1 ? ee.maxLevel : 1);
                String searchKey = ee.id + ":" + targetLvl;

                if (!addedKeys.contains(searchKey)) {
                    addedKeys.add(searchKey);
                    String display = AnvilOptimizer.formatEnchantName(ee.name, targetLvl);
                    currentSuggestions.add(new AutocompleteSuggestion(ee.id, targetLvl, searchKey, display, badge, badgeColor));
                }
            }
            if (currentSuggestions.size() >= 5) break;
        }
    }

    public static boolean handleMouseClicked(Screen screen, MouseButtonEvent event, boolean isDown) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (button == 0 && hoveredSuggestionIndex >= 0 && hoveredSuggestionIndex < currentSuggestions.size()) {
            AutocompleteSuggestion s = currentSuggestions.get(hoveredSuggestionIndex);
            ActiveSessionManager.toggleSearchEnchant(s.searchKey);
            if (searchBox != null) {
                searchBox.setValue("");
                searchBox.setFocused(false);
            }
            if (screen != null && screen.getFocused() == searchBox) {
                screen.setFocused(null);
            }
            currentSuggestions.clear();
            hoveredSuggestionIndex = -1;
            clearCache();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal("§a✔ Filtro attivato: " + s.displayName));
            }
            return true;
        }

        if (button == 0 && isClearHovered) {
            ActiveSessionManager.clearSearchEnchants();
            if (searchBox != null) {
                searchBox.setValue("");
                searchBox.setFocused(false);
            }
            if (screen != null && screen.getFocused() == searchBox) {
                screen.setFocused(null);
            }
            currentSuggestions.clear();
            hoveredSuggestionIndex = -1;
            clearCache();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal("§c✔ Ricerca azzerata"));
            }
            return true;
        }

        // Click sulla barra di ricerca
        if (button == 0 && searchBox != null && searchBox.visible) {
            if (mouseX >= lastSearchBoxX && mouseX <= lastSearchBoxX + lastSearchBoxW &&
                mouseY >= lastSearchBoxY && mouseY <= lastSearchBoxY + lastSearchBoxH) {
                searchBox.setFocused(true);
                if (screen != null) {
                    screen.setFocused(searchBox);
                }
                searchBox.mouseClicked(event, isDown);
                return true;
            } else if (searchBox.isFocused()) {
                searchBox.setFocused(false);
                if (screen != null && screen.getFocused() == searchBox) {
                    screen.setFocused(null);
                }
            }
        }

        if (!currentSuggestions.isEmpty() && (mouseX < dropX || mouseX > dropX + dropW || mouseY < dropY || mouseY > dropY + dropH)) {
            currentSuggestions.clear();
            hoveredSuggestionIndex = -1;
        }

        return false;
    }

    public static void renderAhSidebar(GuiGraphicsExtractor extractor, AbstractContainerScreen<?> screen, int leftPos, int topPos, int imageWidth, int imageHeight, int mouseX, int mouseY, float delta) {
        if (screen == null) return;
        if (!isAuctionHouse(screen)) {
            if (searchBox != null) {
                searchBox.visible = false;
                searchBox.setFocused(false);
            }
            return;
        }

        scanCurrentContainer(screen);

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Larghezza ottimizzata adattiva (186px default per far entrare bene nomi e venditori)
        int availableLeft = leftPos - 8;
        int sideW = Math.min(190, Math.max(165, availableLeft));
        int sideX = leftPos - sideW - 6;
        if (sideX < 2 && (screen.width - (leftPos + imageWidth)) >= 170) {
            sideX = leftPos + imageWidth + 6;
            sideW = Math.min(190, screen.width - sideX - 4);
        }
        sideX = Math.max(2, sideX);
        int sideY = topPos;
        int sideH = imageHeight;

        // Sfondo dark glass solido
        extractor.outline(sideX - 1, sideY - 1, sideW + 2, sideH + 2, 0xFF000000);
        extractor.fill(sideX, sideY, sideX + sideW, sideY + sideH, 0xF50F111A);

        // Barra accento superiore Litness fiery gradient
        int halfW = sideW / 2;
        extractor.fillGradient(sideX, sideY, sideX + halfW, sideY + 2, 0xFFFFB800, 0xFFFF5722);
        extractor.fillGradient(sideX + halfW, sideY, sideX + sideW, sideY + 2, 0xFFFF5722, 0xFFC62828);
        extractor.outline(sideX, sideY, sideW, sideH, 0xFF2A2D40);

        // Titolo Header con colore tema Litness
        extractor.text(font, "⚡ PREZZI AH", sideX + 8, sideY + 6, 0xFFFFB800, true);

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

        // Barra di Ricerca Rapida Standard
        int searchBoxX = sideX + 6;
        int searchBoxY = sideY + 20;
        int searchBoxW = sideW - 12;
        int searchBoxH = 18;
        lastSearchBoxX = searchBoxX;
        lastSearchBoxY = searchBoxY;
        lastSearchBoxW = searchBoxW;
        lastSearchBoxH = searchBoxH;

        if (searchBox != null) {
            searchBox.visible = true;
            searchBox.setPosition(searchBoxX, searchBoxY);
            searchBox.setWidth(searchBoxW);
            searchBox.setHeight(searchBoxH);
            searchBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        int curY = searchBoxY + searchBoxH + 5;

        if (searchEnchants == null || searchEnchants.isEmpty()) {
            // Stato vuoto: Nessun incanto cercato
            int cardY = curY + 2;
            int cardH = sideH - (cardY - sideY) - 10;
            extractor.fill(sideX + 6, cardY, sideX + sideW - 6, cardY + cardH, 0x40161826);
            extractor.outline(sideX + 6, cardY, sideW - 12, cardH, 0xFF26293D);

            extractor.fakeItem(new ItemStack(Items.ENCHANTED_BOOK), sideX + (sideW / 2) - 8, cardY + 14);

            extractor.centeredText(font, "Nessun filtro attivo", sideX + (sideW / 2), cardY + 36, 0xFFFFFFFF);
            extractor.centeredText(font, "Scrivi nella barra sopra [🔍],", sideX + (sideW / 2), cardY + 52, 0xFF9CA3AF);
            extractor.centeredText(font, "premi [+ Cerca] in [N],", sideX + (sideW / 2), cardY + 64, 0xFFF97316);
            extractor.centeredText(font, "oppure pinna un set!", sideX + (sideW / 2), cardY + 76, 0xFF9CA3AF);

            // Rendering Menu a Discesa Autocomplete anche in stato vuoto se si sta scrivendo
            renderAutocompleteDropdown(extractor, font, searchBoxX, searchBoxY + searchBoxH + 1, searchBoxW, mouseX, mouseY);
            return;
        }

        // Filtra e ordina i listing per prezzo crescente
        List<AhListing> matchingListings = new ArrayList<>();
        List<EnchantFilter> filters = getEffectiveFilters();
        for (AhListing l : cachedListings.values()) {
            if (matchesAnyFilter(l.enchantId, l.level, filters)) {
                matchingListings.add(l);
            }
        }
        Collections.sort(matchingListings);

        // Header info filtro
        int filterCount = filters.size();
        String filterInfo;
        if (filterCount == 1) {
            EnchantFilter first = filters.get(0);
            EnchantmentEntry fe = EnchantmentDatabase.getEnchantment(first.enchantId);
            String baseName = fe != null ? fe.name : first.enchantId;
            if (first.targetLevel > 0) {
                filterInfo = AnvilOptimizer.formatEnchantName(baseName, first.targetLevel);
            } else {
                filterInfo = baseName;
            }
        } else {
            filterInfo = filterCount + " incanti cercati";
        }
        extractor.text(font, "§7Cerca: §e" + trimToWidth(font, filterInfo, sideW - 65), sideX + 8, curY, 0xFFFBBF24, false);
        curY += 10;
        extractor.text(font, "§6✔ Libri lisci (Migliori in Arancio)", sideX + 8, curY, 0xFFF97316, false);
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

            // Rendering Menu a Discesa Autocomplete
            renderAutocompleteDropdown(extractor, font, searchBoxX, searchBoxY + searchBoxH + 1, searchBoxW, mouseX, mouseY);
            return;
        }

        // Variabili per il tooltip al passaggio del mouse
        AhListing hoveredListing = null;
        boolean hoveredIsBest = false;
        int hoveredRank = 1;

        // Trova il miglior listing per ciascun incantesimo cercato
        Map<String, AhListing> bestPerEnchant = new LinkedHashMap<>();
        Set<String> bestKeys = new HashSet<>();
        for (EnchantFilter f : filters) {
            AhListing bestForThis = null;
            for (AhListing l : matchingListings) {
                if (matchesFilter(l.enchantId, l.level, f)) {
                    if (bestForThis == null || l.price < bestForThis.price) {
                        bestForThis = l;
                    }
                }
            }
            if (bestForThis != null) {
                bestPerEnchant.put(f.original, bestForThis);
                bestKeys.add(bestForThis.key);
            }
        }

        // 1. Box Miglior Prezzo (per ciascun incanto trovato, o singolo se 1 solo)
        if (bestPerEnchant.size() <= 1) {
            AhListing best = !bestPerEnchant.isEmpty() ? bestPerEnchant.values().iterator().next() : matchingListings.get(0);
            int bestBoxY = curY;
            int bestBoxH = 43;
            boolean isBestHovered = mouseX >= sideX + 5 && mouseX <= sideX + sideW - 5 && mouseY >= bestBoxY && mouseY <= bestBoxY + bestBoxH;
            if (isBestHovered) {
                hoveredListing = best;
                hoveredIsBest = true;
                hoveredRank = 1;
            }

            int bestBg = isBestHovered ? 0x48F97316 : 0x30F97316;
            int bestBorder = isBestHovered ? 0xFFFED7AA : 0xFFF97316;
            extractor.fill(sideX + 5, bestBoxY, sideX + sideW - 5, bestBoxY + bestBoxH, bestBg);
            extractor.outline(sideX + 5, bestBoxY, sideW - 10, bestBoxH, bestBorder);

            extractor.text(font, "🏆 MIGLIOR PREZZO:", sideX + 8, bestBoxY + 3, 0xFFFED7AA, false);
            EnchantmentEntry eeSingle = EnchantmentDatabase.getEnchantment(best.enchantId);
            ItemStack iconSingle = EnchantmentThematicIconHelper.getIcon(eeSingle);
            extractor.fakeItem(iconSingle, sideX + sideW - 24, bestBoxY + 3);

            String bestName = trimToWidth(font, best.getDisplayNameWithLevel(), sideW - 36);
            extractor.text(font, bestName, sideX + 8, bestBoxY + 13, 0xFFFFFFFF, true);

            String sellerDisplay = trimToWidth(font, best.seller, 65);
            String bestPriceLine = "💰 §6" + best.formattedPrice + " §7(da " + sellerDisplay + ")";
            extractor.text(font, bestPriceLine, sideX + 8, bestBoxY + 23, 0xFFFFA500, false);
            extractor.text(font, "§e" + best.formattedBreakdown, sideX + 8, bestBoxY + 33, 0xFFFDBA74, false);

            curY = bestBoxY + bestBoxH + 5;
        } else {
            // Più incanti in ricerca: mostra una card per ciascun incanto trovato
            extractor.text(font, "🏆 MIGLIORI PREZZI:", sideX + 8, curY, 0xFFFED7AA, false);
            curY += 10;
            int cardH = 26;
            for (AhListing best : bestPerEnchant.values()) {
                boolean isCardHovered = mouseX >= sideX + 5 && mouseX <= sideX + sideW - 5 && mouseY >= curY && mouseY <= curY + cardH;
                if (isCardHovered) {
                    hoveredListing = best;
                    hoveredIsBest = true;
                    hoveredRank = 1;
                }
                int cardBg = isCardHovered ? 0x48F97316 : 0x2CF97316;
                int cardBorder = isCardHovered ? 0xFFFED7AA : 0xFFF97316;
                extractor.fill(sideX + 5, curY, sideX + sideW - 5, curY + cardH, cardBg);
                extractor.outline(sideX + 5, curY, sideW - 10, cardH, cardBorder);

                EnchantmentEntry eeMulti = EnchantmentDatabase.getEnchantment(best.enchantId);
                ItemStack iconMulti = EnchantmentThematicIconHelper.getIcon(eeMulti);
                extractor.fakeItem(iconMulti, sideX + 8, curY + 5);

                String nameStr = trimToWidth(font, best.getDisplayNameWithLevel(), sideW - 90);
                extractor.text(font, nameStr, sideX + 27, curY + 3, 0xFFFFFFFF, true);
                extractor.text(font, best.formattedPrice, sideX + sideW - font.width(best.formattedPrice) - 8, curY + 3, 0xFFFFA500, true);

                String subStr = "§e" + best.formattedBreakdown + " §7(da " + trimToWidth(font, best.seller, 40) + ")";
                extractor.text(font, trimToWidth(font, subStr, sideW - 35), sideX + 27, curY + 14, 0xFFFDBA74, false);

                curY += cardH + 3;
            }
            curY += 2;
        }

        // 2. Lista tutti i prezzi trovati
        extractor.text(font, "Tutti i prezzi (" + matchingListings.size() + "):", sideX + 8, curY, 0xFF9CA3AF, false);
        curY += 10;

        int rowH = 15;
        int maxRows = Math.min(6, matchingListings.size());

        for (int i = 0; i < maxRows; i++) {
            AhListing l = matchingListings.get(i);
            boolean isTop = bestKeys.contains(l.key);

            int itemY = curY + i * rowH;
            if (itemY + rowH > sideY + sideH - 16) break;

            boolean isRowHovered = mouseX >= sideX + 5 && mouseX <= sideX + sideW - 5 && mouseY >= itemY && mouseY <= itemY + rowH - 1;
            if (isRowHovered) {
                hoveredListing = l;
                hoveredIsBest = isTop;
                hoveredRank = i + 1;
            }

            int rowBg = isRowHovered ? (isTop ? 0x50F97316 : 0x35334155) : (isTop ? 0x28F97316 : (i % 2 == 0 ? 0x181E293B : 0x0D1E293B));
            extractor.fill(sideX + 5, itemY, sideX + sideW - 5, itemY + rowH - 1, rowBg);
            if (isRowHovered) {
                extractor.outline(sideX + 5, itemY, sideW - 10, rowH - 1, isTop ? 0xFFF97316 : 0xFF475569);
            }

            // Rango (#1 arancione se è il migliore del suo incanto, altri slate)
            String rank = (i + 1) + ".";
            extractor.text(font, rank, sideX + 8, itemY + 3, isTop ? 0xFFF97316 : 0xFF64748B, false);

            // Prezzo formattato
            extractor.text(font, l.formattedPrice, sideX + 22, itemY + 3, isTop ? 0xFFFFA500 : 0xFFE2E8F0, false);

            // Nome incanto
            String sub = AnvilOptimizer.formatEnchantName(l.enchantName, l.level);
            String subTrimmed = trimToWidth(font, sub, 65);
            extractor.text(font, subTrimmed, sideX + 78, itemY + 3, isTop ? 0xFFFFF0D0 : 0xFF94A3B8, false);

            // Venditore allineato a destra
            String sellerTrimmed = trimToWidth(font, l.seller, Math.max(35, sideW - 146));
            extractor.text(font, sellerTrimmed, sideX + sideW - font.width(sellerTrimmed) - 8, itemY + 3, isTop ? 0xFFFED7AA : 0xFF64748B, false);
        }

        // Footer informativo
        int footerY = sideY + sideH - 12;
        extractor.centeredText(font, "💡 Sfoglia /ah per aggiornare", sideX + (sideW / 2), footerY, 0xFF64748B);

        // Rendering Menu a Discesa Autocomplete (in primo piano, sopra le card e i listing)
        renderAutocompleteDropdown(extractor, font, searchBoxX, searchBoxY + searchBoxH + 1, searchBoxW, mouseX, mouseY);

        // Rendering tooltip interattivo su hover del mouse (solo se non c'è dropdown aperto)
        if (hoveredListing != null && currentSuggestions.isEmpty()) {
            renderListingTooltip(extractor, font, hoveredListing, hoveredIsBest, hoveredRank, mouseX, mouseY, screen.width, screen.height);
        }
    }

    private static void renderAutocompleteDropdown(GuiGraphicsExtractor extractor, Font font, int x, int y, int w, int mouseX, int mouseY) {
        if (searchBox == null || searchBox.getValue().trim().isEmpty() || currentSuggestions.isEmpty()) {
            hoveredSuggestionIndex = -1;
            return;
        }

        dropX = x;
        dropY = y;
        dropW = w;
        dropH = currentSuggestions.size() * 18 + 4;

        // Pannello a discesa moderno
        extractor.outline(dropX - 1, dropY - 1, dropW + 2, dropH + 2, 0xFF000000);
        extractor.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xF8111422);
        extractor.outline(dropX, dropY, dropW, dropH, 0xFFF97316);

        hoveredSuggestionIndex = -1;
        for (int i = 0; i < currentSuggestions.size(); i++) {
            AutocompleteSuggestion sug = currentSuggestions.get(i);
            int itemY = dropY + 2 + i * 18;
            boolean isSugHovered = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY < itemY + 18;
            if (isSugHovered) {
                hoveredSuggestionIndex = i;
                extractor.fill(dropX + 2, itemY, dropX + dropW - 2, itemY + 18, 0x48F97316);
            }

            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(sug.enchantId);
            ItemStack icon = EnchantmentThematicIconHelper.getIcon(ee);
            extractor.fakeItem(icon, dropX + 3, itemY + 1);

            int badgeW = font.width(sug.badge);
            int maxNameW = Math.max(30, dropW - 24 - badgeW - 6);
            extractor.text(font, trimToWidth(font, sug.displayName, maxNameW), dropX + 22, itemY + 5, isSugHovered ? 0xFFFFA500 : 0xFFFFFFFF, false);
            extractor.text(font, sug.badge, dropX + dropW - badgeW - 5, itemY + 5, sug.badgeColor, false);
        }
    }

    private static void renderListingTooltip(GuiGraphicsExtractor extractor, Font font, AhListing listing, boolean isBest, int rank, int mouseX, int mouseY, int screenW, int screenH) {
        if (listing == null) return;

        List<Component> lines = new ArrayList<>();
        if (isBest) {
            lines.add(Component.literal("§6🏆 MIGLIOR PREZZO AH #1"));
        } else {
            lines.add(Component.literal("§e📋 OFFERTA IN CLASSIFICA #" + rank));
        }
        lines.add(Component.literal("§f📙 " + listing.getDisplayNameWithLevel()));
        lines.add(Component.empty());
        lines.add(Component.literal("§7Prezzo Totale:   " + (isBest ? "§6" : "§a") + listing.formattedPrice));
        if (listing.tax > 0) {
            lines.add(Component.literal("§7Netto Venditore: §f" + AhListing.formatPrice(listing.netPrice)));
            lines.add(Component.literal("§7Tassa Asta:     §c" + AhListing.formatPrice(listing.tax)));
        }
        lines.add(Component.literal("§7Venditore:      §b" + listing.seller));
        lines.add(Component.empty());
        lines.add(Component.literal("§8💡 Clicca sul libro per acquistarlo subito in /ah"));

        int maxW = 160;
        for (Component c : lines) {
            maxW = Math.max(maxW, font.width(c));
        }
        int tipW = maxW + 16;
        int tipH = lines.size() * 11 + 10;

        int tipX = mouseX + 12;
        int tipY = mouseY - 12;

        if (tipX + tipW > screenW - 4) {
            tipX = mouseX - tipW - 8;
        }
        if (tipY + tipH > screenH - 4) {
            tipY = screenH - tipH - 4;
        }
        if (tipY < 4) tipY = 4;
        if (tipX < 4) tipX = 4;

        // Scatola Dark Glass per il tooltip
        extractor.outline(tipX - 1, tipY - 1, tipW + 2, tipH + 2, 0xFF000000);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xF80E111A);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + 2, isBest ? 0xFFF97316 : 0xFF10B981);
        extractor.outline(tipX, tipY, tipW, tipH, isBest ? 0xFFEA580C : 0xFF2A2D40);

        int textY = tipY + 6;
        for (Component line : lines) {
            if (line.getString().isEmpty()) {
                textY += 4;
            } else {
                extractor.text(font, line, tipX + 8, textY, 0xFFFFFFFF, true);
                textY += 11;
            }
        }
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

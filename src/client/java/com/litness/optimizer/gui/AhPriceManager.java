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
import com.mojang.blaze3d.platform.InputConstants;

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
        public int page = 1;

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

    // Tracking pagine e rimozione automatica item acquistati/scomparsi
    private static final Map<Integer, Set<String>> pageToListings = new ConcurrentHashMap<>();
    private static Set<String> lastScreenListingKeys = new HashSet<>();
    private static int lastScannedPage = -1;
    private static String pendingBuyKey = null;
    private static long pendingBuyPrice = -1;
    private static long pendingBuyTime = 0;
    public static AhListing lastHoveredListing = null;

    // Auto-navigazione pagine per acquisto rapido
    private static AhListing autoNavListing = null;
    private static int autoNavAttempts = 0;
    private static long autoNavLastClick = 0;

    // Scansione automatica di tutte le pagine di /ah
    private static boolean isAutoScanning = false;
    private static boolean hasAutoScannedThisSession = false;
    private static boolean hasSelectedBooksCategory = false;
    private static long lastAutoScanClick = 0;
    private static int autoScanPageCount = 0;
    private static boolean autoScanReturning = false;

    public static class EnchantBadgeInfo {
        public final String shortName;
        public final int level;
        public final boolean isMatching;
        public final boolean isCheapest;
        public final boolean isClean;

        public EnchantBadgeInfo(String shortName, int level, boolean isMatching, boolean isCheapest, boolean isClean) {
            this.shortName = shortName;
            this.level = level;
            this.isMatching = isMatching;
            this.isCheapest = isCheapest;
            this.isClean = isClean;
        }

        public String getDisplayText() {
            if (shortName == null || shortName.isEmpty()) return "";
            if (level > 0) {
                return shortName + " " + com.litness.optimizer.calculator.AnvilOptimizer.toRoman(level);
            }
            return shortName;
        }
    }

    public static EnchantBadgeInfo getEnchantBadgeInfo(ItemStack stack, Set<String> targetEnchants) {
        if (stack == null || stack.isEmpty()) return null;

        boolean isMatching = (targetEnchants != null && !targetEnchants.isEmpty()) && containsMatchingTargetItem(stack, targetEnchants);
        boolean isCheapest = isMatching && isCheapestOnScreen(stack);
        boolean isClean = isMatching && isCleanMatchingBook(stack, targetEnchants);

        // 1. Se e' un libro, ricava l'incantesimo principale da mostrare
        if (isBook(stack)) {
            Set<String> distinct = extractDistinctEnchantments(stack);
            if (!distinct.isEmpty()) {
                String singleId = null;
                // Se combacia con uno dei target cercati, dagli priorita'
                if (isMatching && targetEnchants != null) {
                    for (String t : targetEnchants) {
                        EnchantFilter f = parseFilter(t);
                        if (f != null) {
                            for (String dId : distinct) {
                                if (isIdMatching(dId, f.enchantId)) {
                                    singleId = dId;
                                    break;
                                }
                            }
                        }
                        if (singleId != null) break;
                    }
                }
                if (singleId == null) {
                    singleId = distinct.iterator().next();
                }

                int level = extractLevel(stack, singleId);
                String shortName = getAbbreviatedEnchantName(singleId);
                return new EnchantBadgeInfo(shortName, level, isMatching, isCheapest, isClean);
            }
        }

        // 2. Se e' un altro item (arma, armatura, attrezzo) che combacia con un target cercato:
        if (isMatching && targetEnchants != null) {
            for (String t : targetEnchants) {
                EnchantFilter f = parseFilter(t);
                if (f != null) {
                    String shortName = getAbbreviatedEnchantName(f.enchantId);
                    return new EnchantBadgeInfo(shortName, f.targetLevel, true, false, false);
                }
            }
        }

        return null;
    }

    public static String getAbbreviatedEnchantName(String enchantId) {
        if (enchantId == null || enchantId.isEmpty()) return "";
        String id = enchantId.toLowerCase(Locale.ROOT).trim();
        switch (id) {
            case "efficiency": return "Eff";
            case "sharpness": return "Sharp";
            case "protection": return "Prot";
            case "feather_falling": return "Feath";
            case "unbreaking": return "Unbr";
            case "power": return "Power";
            case "looting": return "Loot";
            case "fortune": return "Fort";
            case "mending": return "Mend";
            case "respiration": return "Resp";
            case "depth_strider": return "Depth";
            case "fire_aspect": return "Fire";
            case "flame": return "Flame";
            case "infinity": return "Infin";
            case "punch": return "Punch";
            case "knockback": return "Knock";
            case "sweeping_edge": case "sweeping": return "Sweep";
            case "silk_touch": return "Silk";
            case "thorns": return "Thorn";
            case "aqua_affinity": return "Aqua";
            case "swift_sneak": return "Sneak";
            case "soul_speed": return "Soul";
            case "frost_walker": return "Frost";
            case "blast_protection": return "Blast";
            case "projectile_protection": return "Proj";
            case "fire_protection": return "FProt";
            case "smite": return "Smite";
            case "bane_of_arthropods": return "Bane";
            case "multishot": return "Multi";
            case "piercing": return "Pierc";
            case "quick_charge": return "Quick";
            case "riptide": return "Ript";
            case "channeling": return "Chan";
            case "loyalty": return "Loyal";
            case "impaling": return "Impal";
            case "luck_of_the_sea": return "Luck";
            case "lure": return "Lure";
            default:
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(id);
                if (ee != null && ee.name != null && !ee.name.isEmpty()) {
                    String cleanName = ee.name.replaceAll("[^a-zA-Z0-9 ]", "").trim();
                    if (cleanName.length() <= 7) return cleanName;
                    return cleanName.substring(0, 6);
                }
                if (id.length() <= 7) return id;
                return id.substring(0, 6);
        }
    }

        public static class ClickableListingRect {
        public final int y1, y2;
        public final AhListing listing;
        public ClickableListingRect(int y1, int y2, AhListing listing) {
            this.y1 = y1;
            this.y2 = y2;
            this.listing = listing;
        }
    }
    private static final List<ClickableListingRect> currentClickableRects = new ArrayList<>();

    // Coordinate tasto [⚡ Sfoglia]
    private static int btnScanX = -9999;
    private static int btnScanY = -9999;
    private static int btnScanW = 46;
    private static int btnScanH = 12;
    private static boolean isScanHovered = false;

    // Coordinate footer tasto sfoglia
    private static int footerBtnX = -9999;
    private static int footerBtnY = -9999;
    private static int footerBtnW = 0;
    private static int footerBtnH = 0;

    private static final Pattern PAGE_REGEX_1 = Pattern.compile("(?i)(?:pagina|page|pag\\.|p\\.)\\s*#?\\s*(\\d+)");
    private static final Pattern PAGE_REGEX_2 = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

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
    private static int btnClearX = -9999;
    private static int btnClearY = -9999;
    private static int btnClearW = 38;
    private static int btnClearH = 12;
    private static boolean isClearHovered = false;

    // Coordinate barra di ricerca
    private static int lastSearchBoxX = -9999;
    private static int lastSearchBoxY = -9999;
    private static int lastSearchBoxW = 0;
    private static int lastSearchBoxH = 0;

    // Coordinate e stato di scorrimento (Scrolling) della sidebar AH
    private static int scrollOffset = 0;
    private static int maxScroll = 0;
    private static int scrollAreaY = 0;
    private static int scrollAreaH = 0;
    private static int lastSideX = 0;
    private static int lastSideY = 0;
    private static int lastSideW = 0;
    private static int lastSideH = 0;
    private static int scrollBarX = 0;
    private static int scrollBarW = 3;
    private static int scrollThumbY = 0;
    private static int scrollThumbH = 0;
    private static boolean isDraggingScrollbar = false;
    private static double dragStartMouseY = 0;
    private static int dragStartScrollOffset = 0;

    public static void clearCache() {
        cachedListings.clear();
        currentScreenMinPrices.clear();
        pageToListings.clear();
        lastScreenListingKeys.clear();
        lastScannedPage = -1;
        pendingBuyKey = null;
        pendingBuyPrice = -1;
        autoNavListing = null;
        autoNavAttempts = 0;
        isAutoScanning = false;
        hasAutoScannedThisSession = false;
        hasSelectedBooksCategory = false;
        autoScanReturning = false;
        autoScanPageCount = 0;
        scrollOffset = 0;
        maxScroll = 0;
        isDraggingScrollbar = false;
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
                if (s.contains("click to buy") || s.contains("clicca per acquistare") || s.contains("clicca per comprare")
                        || s.contains("seller:") || s.contains("venditore:")
                        || s.contains("expire:") || s.contains("scadenza:")
                        || s.contains("price:") || s.contains("prezzo:")
                        || s.contains("tax)")) {
                    cachedContainerId = menuId;
                    cachedIsAh = true;
                    return true;
                }
            }
        }

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
        if (stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK) || stack.is(Items.WRITTEN_BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.KNOWLEDGE_BOOK)) return true;
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return name.contains("libr") || name.contains("book");
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
                lvl = com.litness.optimizer.data.InventoryTracker.getRequiredLevel(id);
            }
            return new EnchantFilter(t, id, lvl);
        } else {
            String id = t.toLowerCase();
            int lvl = com.litness.optimizer.data.InventoryTracker.getRequiredLevel(id);
            String baseId = com.litness.optimizer.data.LitnessI18n.getBaseId(id);
            return new EnchantFilter(t, baseId.isEmpty() ? id : baseId, lvl);
        }
    }

    public static boolean isIdMatching(String enchantId, String targetId) {
        if (enchantId == null || targetId == null) return false;
        String a = enchantId.trim().toLowerCase();
        String b = targetId.trim().toLowerCase();
        if (a.equals(b)) return true;
        if (a.replace('-', '_').equals(b.replace('-', '_'))) return true;
        if (a.replace("defense", "defence").equals(b.replace("defense", "defence"))) return true;
        String baseA = com.litness.optimizer.data.LitnessI18n.getBaseId(a);
        String baseB = com.litness.optimizer.data.LitnessI18n.getBaseId(b);
        if (!baseA.isEmpty() && baseA.equals(baseB)) return true;
        EnchantmentEntry targetEe = EnchantmentDatabase.getEnchantment(b);
        if (targetEe != null) {
            if (targetEe.id.equalsIgnoreCase(a)) return true;
            if (a.replace('-', '_').equalsIgnoreCase(targetEe.id.replace('-', '_'))) return true;
            String baseEe = com.litness.optimizer.data.LitnessI18n.getBaseId(targetEe.id);
            if (!baseA.isEmpty() && baseA.equalsIgnoreCase(baseEe)) return true;
        }
        return false;
    }

    public static boolean matchesFilter(String bookEnchantId, int bookLevel, EnchantFilter filter) {
        if (filter == null || bookEnchantId == null) return false;
        String cleanBook = com.litness.optimizer.data.LitnessI18n.getBaseId(bookEnchantId);
        String cleanFilter = com.litness.optimizer.data.LitnessI18n.getBaseId(filter.enchantId);
        if (!isIdMatching(cleanBook, cleanFilter) && !isIdMatching(bookEnchantId, filter.enchantId)) {
            return false;
        }
        if (filter.targetLevel > 0 && bookLevel > 0) {
            return bookLevel >= filter.targetLevel;
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
            } catch (Throwable ignored) {}
        }

        // 3. Bundle (fagotto) con DataComponents.BUNDLE_CONTENTS
        BundleContents bundle = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, null);
        if (bundle != null) {
            try {
                if (bundle.itemCopies().anyMatch(inner -> isCleanMatchingBook(inner, targetEnchants))) {
                    return true;
                }
            } catch (Throwable t) {
                try {
                    java.lang.reflect.Method m = bundle.getClass().getMethod("itemCopyStream");
                    @SuppressWarnings("unchecked")
                    java.util.stream.Stream<ItemStack> stream = (java.util.stream.Stream<ItemStack>) m.invoke(bundle);
                    if (stream.anyMatch(inner -> isCleanMatchingBook(inner, targetEnchants))) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
        }

        return false;
    }

    /**
     * Verifica universale per evidenziare qualsiasi oggetto (libri, armature, armi, attrezzi, shulker)
     * che possiede l'incantesimo cercato scritto nei componenti o nella lore.
     */
    public static boolean containsMatchingTargetItem(ItemStack stack, Set<String> targetEnchants) {
        if (stack == null || stack.isEmpty() || targetEnchants == null || targetEnchants.isEmpty()) return false;

        // 1. Libro liscio diretto
        if (isCleanMatchingBook(stack, targetEnchants)) {
            return true;
        }

        List<EnchantFilter> filters = new ArrayList<>();
        for (String t : targetEnchants) {
            EnchantFilter f = parseFilter(t);
            if (f != null) filters.add(f);
        }
        if (filters.isEmpty()) return false;

        // 2. Componente ENCHANTMENTS (armi, armature, attrezzi incantati)
        ItemEnchantments enchs = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchs.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            if (matchesAnyFilter(rawId, lvl, filters)) {
                return true;
            }
        }

        // 3. Componente STORED_ENCHANTMENTS (libri incantati con 1 o piu' incantesimi)
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            if (matchesAnyFilter(rawId, lvl, filters)) {
                return true;
            }
        }

        // 4. Scansione Lore dell'oggetto (per incantesimi Custom del server Litness scritti in lore)
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            String rawLine = c.getString().replace('\u00A0', ' ').trim();
            if (rawLine.isEmpty()) continue;

            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(rawLine);
            if (match != null && match.entry != null) {
                if (matchesAnyFilter(match.entry.id, match.level, filters)) {
                    return true;
                }
            }

            // Match testuale diretto sul nome dell'incantesimo cercato
            String normLine = LitnessTooltipHandler.normalize(rawLine);
            int lineLvl = LitnessTooltipHandler.extractLevelFromEnd(normLine);
            for (EnchantFilter f : filters) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(f.enchantId);
                if (ee != null) {
                    String normName = LitnessTooltipHandler.normalize(ee.name);
                    String normLocal = LitnessTooltipHandler.normalize(com.litness.optimizer.data.LitnessI18n.getEnchantName(ee));
                    boolean nameMatches = (normName.length() >= 3 && normLine.contains(normName)) || (normLocal.length() >= 3 && normLine.contains(normLocal));
                    if (nameMatches) {
                        if (f.targetLevel > 0 && lineLvl > 0 && lineLvl < f.targetLevel) {
                            continue;
                        }
                        return true;
                    }
                }
            }
        }

        // 5. Scatole di Shulker / Casse contenitori (DataComponents.CONTAINER)
        ItemContainerContents container = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (container != ItemContainerContents.EMPTY) {
            try {
                if (container.nonEmptyItemCopyStream().anyMatch(inner -> containsMatchingTargetItem(inner, targetEnchants))) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        // 6. Bundle (fagotto) con DataComponents.BUNDLE_CONTENTS
        BundleContents bundle = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, null);
        if (bundle != null) {
            try {
                if (bundle.itemCopies().anyMatch(inner -> containsMatchingTargetItem(inner, targetEnchants))) {
                    return true;
                }
            } catch (Throwable t) {
                try {
                    java.lang.reflect.Method m = bundle.getClass().getMethod("itemCopyStream");
                    @SuppressWarnings("unchecked")
                    java.util.stream.Stream<ItemStack> stream = (java.util.stream.Stream<ItemStack>) m.invoke(bundle);
                    if (stream.anyMatch(inner -> containsMatchingTargetItem(inner, targetEnchants))) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
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
            if (vPlusId != null && (vPlusId.equalsIgnoreCase(enchantId) || isIdMatching(vPlusId, enchantId))) {
                return lvl;
            }
            if (rawId.equalsIgnoreCase(enchantId) || isIdMatching(rawId, enchantId)) {
                return lvl;
            }
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            if (ee != null && (ee.id.equalsIgnoreCase(enchantId) || isIdMatching(ee.id, enchantId))) {
                return lvl;
            }
        }

        // 2. Lore
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            String text = c.getString().replaceAll("\\[[^\\]]*\\]", "").trim();
            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(text);
            if (match != null && match.entry != null && (match.entry.id.equalsIgnoreCase(enchantId) || isIdMatching(match.entry.id, enchantId))) {
                if (match.level > 0) return match.level;
            }
        }

        // 3. Hover Name
        String hoverName = stack.getHoverName().getString().replaceAll("\\[[^\\]]*\\]", "").trim();
        LitnessTooltipHandler.MatchedEnchantment matchName = LitnessTooltipHandler.matchEnchantment(hoverName);
        if (matchName != null && matchName.entry != null && (matchName.entry.id.equalsIgnoreCase(enchantId) || isIdMatching(matchName.entry.id, enchantId))) {
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

    public static int detectPageNumber(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null) return -1;

        // 1. Controlla il titolo del container (es. "AUCTION - Pagina 2")
        String title = screen.getTitle().getString();
        int titleP = parsePageFromString(title);
        if (titleP > 0) return titleP;

        List<Slot> slots = screen.getMenu().slots;
        int maxSlot = Math.min(54, slots.size());

        // 2. Cerca esplicito "Pagina X / Y" o "Pagina X di Y" nella barra di navigazione inferiore
        for (int i = 45; i < maxSlot; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) continue;

            ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
            for (Component c : lore.lines()) {
                Matcher m = Pattern.compile("(?i)(?:pagina|page|pag\\.)?\\s*(\\d+)\\s*(?:/|di|of)\\s*(\\d+)").matcher(c.getString());
                if (m.find()) {
                    try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
                }
            }

            Matcher mName = Pattern.compile("(?i)(?:pagina|page|pag\\.)?\\s*(\\d+)\\s*(?:/|di|of)\\s*(\\d+)").matcher(stack.getHoverName().getString());
            if (mName.find()) {
                try { return Integer.parseInt(mName.group(1)); } catch (Exception ignored) {}
            }
        }

        // 3. Cerca "Pagina X" generico (escludendo frecce che indicano "Pagina successiva: X")
        for (int i = 45; i < maxSlot; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            String hoverName = stack.getHoverName().getString();
            String lower = hoverName.toLowerCase();
            if (!lower.contains("succ") && !lower.contains("next") && !lower.contains("avanti") && !lower.contains("prossim")
                    && !lower.contains("prec") && !lower.contains("prev") && !lower.contains("indietro")) {
                int p = parsePageFromString(hoverName);
                if (p > 0) return p;
            }

            ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
            for (Component c : lore.lines()) {
                String line = c.getString();
                String lineLower = line.toLowerCase();
                if (!lineLower.contains("succ") && !lineLower.contains("next") && !lineLower.contains("avanti") && !lineLower.contains("prossim")
                        && !lineLower.contains("prec") && !lineLower.contains("prev") && !lineLower.contains("indietro")) {
                    int p = parsePageFromString(line);
                    if (p > 0) return p;
                }
            }
        }
        return -1;
    }

    private static int parsePageFromString(String text) {
        if (text == null || text.isEmpty()) return -1;
        Matcher m1 = PAGE_REGEX_1.matcher(text);
        if (m1.find()) {
            try { return Integer.parseInt(m1.group(1)); } catch (Exception ignored) {}
        }
        Matcher m2 = PAGE_REGEX_2.matcher(text);
        if (m2.find()) {
            try { return Integer.parseInt(m2.group(1)); } catch (Exception ignored) {}
        }
        return -1;
    }

    public static void onSlotClicked(AbstractContainerScreen<?> screen, Slot slot) {
        if (screen == null || slot == null) return;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        // Riconoscimento click di conferma in menu/popup di acquisto (es. Conferma Acquisto)
        String screenTitle = screen.getTitle().getString().toLowerCase();
        boolean isConfirmScreen = screenTitle.contains("conferma") || screenTitle.contains("confirm")
                || screenTitle.contains("acquisto") || screenTitle.contains("purchase");
        String itemName = stack.getHoverName().getString().toLowerCase();
        boolean isConfirmItem = itemName.contains("conferma") || itemName.contains("confirm")
                || itemName.contains("accetta") || itemName.contains("compra") || itemName.contains("buy");

        if ((isConfirmScreen || isConfirmItem) && pendingBuyKey != null) {
            // L'utente ha confermato l'acquisto: rimuovi subito l'offerta salvata!
            cachedListings.remove(pendingBuyKey);
            for (Set<String> pageSet : pageToListings.values()) {
                pageSet.remove(pendingBuyKey);
            }
            lastScreenListingKeys.remove(pendingBuyKey);
            recalculateMinPrices();
            pendingBuyKey = null;
            pendingBuyPrice = -1;
            return;
        }

        if (!isAuctionHouse(screen) || !isBook(stack)) return;

        ParsedInfo info = parseSellerAndPrice(stack);
        if (info.price > 0) {
            Set<String> distinct = extractDistinctEnchantments(stack);
            if (distinct.size() == 1) {
                String singleId = distinct.iterator().next();
                int level = extractLevel(stack, singleId);
                String sellerClean = (info.seller != null && !info.seller.isEmpty()) ? info.seller : "Sconosciuto";
                pendingBuyKey = sellerClean.toLowerCase() + ":" + singleId + ":" + level + ":" + info.price;
                pendingBuyPrice = info.price;
                pendingBuyTime = System.currentTimeMillis();
            }
        }
    }

    public static void onChatMessage(String text) {
        if (text == null || text.isEmpty()) return;
        String lower = text.toLowerCase(Locale.ROOT);
        // Riconoscimento messaggi di conferma acquisto su Litness in italiano e inglese
        boolean isPurchase = lower.contains("hai appena comprato")
                || lower.contains("hai comprato")
                || lower.contains("hai acquistato")
                || lower.contains("acquisto effettuato")
                || lower.contains("acquisto completato")
                || lower.contains("comprato x1")
                || lower.contains("you bought")
                || lower.contains("bought x1")
                || lower.contains("purchased")
                || lower.contains("vinto l'asta")
                || (lower.contains("asta") && (lower.contains("comprat") || lower.contains("acquistat")));

        if (isPurchase) {
            long parsedPrice = -1;
            Matcher m = Pattern.compile("(?i)(?:per|for|a)\\s+([0-9.,kKmM]+)\\s*\\$").matcher(text);
            if (m.find()) {
                parsedPrice = parseMoneyAmount(m.group(1));
            } else {
                Matcher m2 = Pattern.compile("(?i)([0-9.,kKmM]+)\\s*\\$").matcher(text);
                if (m2.find()) {
                    parsedPrice = parseMoneyAmount(m2.group(1));
                }
            }

            // 1. Rimuovi la chiave cliccata di recente (ultimi 25 secondi)
            if (pendingBuyKey != null && System.currentTimeMillis() - pendingBuyTime < 25000) {
                cachedListings.remove(pendingBuyKey);
                for (Set<String> pageSet : pageToListings.values()) {
                    pageSet.remove(pendingBuyKey);
                }
                lastScreenListingKeys.remove(pendingBuyKey);
            }

            // 2. Rimuovi tutti i listing che corrispondono al prezzo pagato
            if (parsedPrice > 0) {
                long priceToMatch = parsedPrice;
                cachedListings.entrySet().removeIf(entry -> entry.getValue().price == priceToMatch);
                for (Set<String> pageSet : pageToListings.values()) {
                    pageSet.removeIf(k -> k.endsWith(":" + priceToMatch));
                }
                lastScreenListingKeys.removeIf(k -> k.endsWith(":" + priceToMatch));
            }

            pendingBuyKey = null;
            pendingBuyPrice = -1;
            recalculateMinPrices();
        }
    }

    private static void recalculateMinPrices() {
        Map<String, Long> minMap = new HashMap<>();
        for (AhListing l : cachedListings.values()) {
            String matchKey = l.enchantId + ":" + l.level;
            long cur = minMap.getOrDefault(matchKey, Long.MAX_VALUE);
            if (l.price < cur) {
                minMap.put(matchKey, l.price);
            }
            long curGen = minMap.getOrDefault(l.enchantId, Long.MAX_VALUE);
            if (l.price < curGen) {
                minMap.put(l.enchantId, l.price);
            }
        }
        currentScreenMinPrices.clear();
        currentScreenMinPrices.putAll(minMap);
    }

    public static void clickSlot(AbstractContainerScreen<?> screen, Slot s) {
        if (screen == null || s == null) return;
        Minecraft mc = Minecraft.getInstance();
        int slotIndex = (screen.getMenu() != null) ? screen.getMenu().slots.indexOf(s) : s.index;
        if (slotIndex < 0) slotIndex = s.index;
        try {
            if ((Object) screen instanceof com.litness.optimizer.mixin.ContainerScreenAccessor accessor) {
                accessor.litness_invokeSlotClicked(s, slotIndex, 0, net.minecraft.world.inventory.ContainerInput.PICKUP);
                return;
            }
        } catch (Throwable ignored) {}
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleContainerInput(screen.getMenu().containerId, slotIndex, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
        }
    }

    public static void scanCurrentContainer(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null || !isAuctionHouse(screen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<Slot> slots = screen.getMenu().slots;
        int currentPage = detectPageNumber(screen);
        Set<String> currentFoundKeys = new HashSet<>();

        // Scansiona tutti i libri lisci (1 incantesimo) presenti nella pagina corrente di /ah
        int maxAuctionSlot = Math.min(45, slots.size());
        for (int i = 0; i < maxAuctionSlot; i++) {
            Slot slot = slots.get(i);
            if (slot.container == mc.player.getInventory()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !isBook(stack)) continue;

            Set<String> distinct = extractDistinctEnchantments(stack);
            // Solo libri lisci: esattamente 1 incantesimo
            if (distinct.size() != 1) continue;

            String singleId = distinct.iterator().next();
            int level = extractLevel(stack, singleId);

            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(singleId);
            String eName = (ee != null) ? com.litness.optimizer.data.LitnessI18n.getEnchantName(ee) : com.litness.optimizer.data.LitnessI18n.translateEnchantName(singleId);

            ParsedInfo info = parseSellerAndPrice(stack);
            if (info.price > 0) {
                AhListing listing = new AhListing(singleId, eName, level, info.seller, info.price, info.netPrice, info.tax);
                listing.page = Math.max(1, currentPage);
                cachedListings.put(listing.key, listing);
                pageToListings.computeIfAbsent(listing.page, k -> new HashSet<>()).add(listing.key);
                currentFoundKeys.add(listing.key);
            }
        }

        // Mantiene tutte le offerte accumulate attraverso tutte le pagine sfogliate di /ah!
        // Le offerte vengono rimosse all'acquisto confermato o su click destro dell'utente.
        lastScreenListingKeys = new HashSet<>(currentFoundKeys);

        // Se un acquisto era pendente ed è scaduto il timeout (30s), azzeralo
        if (pendingBuyKey != null && System.currentTimeMillis() - pendingBuyTime > 30000) {
            pendingBuyKey = null;
            pendingBuyPrice = -1;
        }

        // Ricalcola i prezzi minimi su TUTTI i listing salvati tra tutte le pagine sfogliate
        recalculateMinPrices();
    }

    public static int findSlotForListing(AbstractContainerScreen<?> screen, AhListing listing) {
        if (screen == null || screen.getMenu() == null || listing == null) return -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        List<Slot> slots = screen.getMenu().slots;
        int maxAuctionSlot = Math.min(45, slots.size());
        for (int i = 0; i < maxAuctionSlot; i++) {
            Slot slot = slots.get(i);
            if (slot.container == mc.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            Set<String> distinct = extractDistinctEnchantments(stack);
            boolean enchantMatch = false;
            for (String singleId : distinct) {
                if (isIdMatching(singleId, listing.enchantId)) {
                    int level = extractLevel(stack, singleId);
                    if (listing.level <= 0 || level == listing.level) {
                        enchantMatch = true;
                        break;
                    }
                }
            }
            if (!enchantMatch) {
                ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
                for (Component c : lore.lines()) {
                    LitnessTooltipHandler.MatchedEnchantment m = LitnessTooltipHandler.matchEnchantment(c.getString());
                    if (m != null && m.entry != null && isIdMatching(m.entry.id, listing.enchantId)) {
                        if (listing.level <= 0 || m.level == listing.level) {
                            enchantMatch = true;
                            break;
                        }
                    }
                }
            }
            if (!enchantMatch) continue;

            ParsedInfo info = parseSellerAndPrice(stack);
            if (info.price == listing.price) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isNextPageItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.AIR) || stack.is(Items.BARRIER)) return false;
        if (stack.is(Items.HOPPER) || stack.is(Items.CLOCK) || stack.is(Items.COMPASS)
                || stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)
                || stack.is(Items.GLASS_BOTTLE)) {
            return false;
        }
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (isPrevPageText(name)) return false;
        if (name.contains("filtri") || name.contains("ordina") || name.contains("le tue aste") || name.contains("informazion")) {
            return false;
        }
        if (name.contains("nessun") || name.contains("ultima") || name.contains("fine") || name.contains("blocc")) {
            return false;
        }
        if (isNextPageText(name)) return true;

        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            String l = c.getString().toLowerCase(Locale.ROOT);
            if (isPrevPageText(l)) return false;
            if (l.contains("nessuna altra pagina") || l.contains("ultima pagina") || l.contains("fine delle pagine")) return false;
            if (isNextPageText(l)) return true;
        }
        return false;
    }

    public static boolean isPrevPageItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.AIR) || stack.is(Items.BARRIER)) return false;
        if (stack.is(Items.HOPPER) || stack.is(Items.CLOCK) || stack.is(Items.COMPASS)
                || stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)
                || stack.is(Items.GLASS_BOTTLE)) {
            return false;
        }
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (isNextPageText(name)) return false;
        if (name.contains("filtri") || name.contains("ordina") || name.contains("le tue aste") || name.contains("informazion")) {
            return false;
        }
        if (name.contains("nessun") || name.contains("prima") || name.contains("inizio") || name.contains("blocc")) {
            return false;
        }
        if (isPrevPageText(name)) return true;

        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            String l = c.getString().toLowerCase(Locale.ROOT);
            if (isNextPageText(l)) return false;
            if (l.contains("prima pagina") || l.contains("nessuna pagina")) return false;
            if (isPrevPageText(l)) return true;
        }
        return false;
    }

    public static int findNextPageSlot(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null) return -1;
        List<Slot> slots = screen.getMenu().slots;
        if (slots.size() < 50) return -1;

        // 1. Cerca prima negli slot di navigazione prioritari (51 su pag 2+, 49 su pag 1)
        int[] candNext = new int[]{51, 49, 50, 52, 53};
        for (int idx : candNext) {
            if (idx < slots.size()) {
                ItemStack s = slots.get(idx).getItem();
                if (isNextPageItem(s)) return idx;
            }
        }

        // 2. Cerca in tutta la riga inferiore (45-53) per corrispondenza di testo
        for (int i = 45; i < Math.min(54, slots.size()); i++) {
            ItemStack stack = slots.get(i).getItem();
            if (isNextPageItem(stack)) return i;
        }

        // 3. Fallback per /ah: escludi sempre tramogge e controlli
        if (isAuctionHouse(screen)) {
            if (51 < slots.size()) {
                ItemStack s = slots.get(51).getItem();
                if (!s.isEmpty() && !s.is(Items.AIR) && !s.is(Items.BARRIER) && !s.is(Items.HOPPER) && !s.is(Items.CLOCK) && !isPrevPageItem(s)) {
                    return 51;
                }
            }
            if (49 < slots.size()) {
                ItemStack s = slots.get(49).getItem();
                if (!s.isEmpty() && !s.is(Items.AIR) && !s.is(Items.BARRIER) && !s.is(Items.HOPPER) && !s.is(Items.CLOCK) && !isPrevPageItem(s)) {
                    return 49;
                }
            }
        }
        return -1;
    }

    public static boolean isNextPageText(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("succ") || t.contains("next") || t.contains("avanti") || t.contains("prossim")
                || t.contains("forward") || t.contains(">") || t.contains("\u00BB") || t.contains("\u2192") || t.contains("\u25B6");
    }

    public static boolean isPrevPageText(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("prec") || t.contains("prev") || t.contains("indietro") || t.contains("back")
                || t.contains("<") || t.contains("\u00AB") || t.contains("\u2190") || t.contains("\u25C0");
    }

    public static int findPrevPageSlot(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null) return -1;
        List<Slot> slots = screen.getMenu().slots;
        if (slots.size() < 49) return -1;

        if (isAuctionHouse(screen)) {
            int page = detectPageNumber(screen);
            if (page == 1) return -1;
        }

        // 1. Cerca negli slot candidati: 49, 48, 47, 45, 46
        int[] candPrev = new int[]{49, 48, 47, 45, 46};
        for (int idx : candPrev) {
            if (idx < slots.size()) {
                ItemStack s = slots.get(idx).getItem();
                if (isPrevPageItem(s)) return idx;
            }
        }

        // 2. Cerca in tutta la riga inferiore (45-53)
        for (int i = 45; i < Math.min(54, slots.size()); i++) {
            ItemStack stack = slots.get(i).getItem();
            if (isPrevPageItem(stack)) return i;
        }

        // 3. Fallback per /ah se siamo oltre pagina 1
        if (isAuctionHouse(screen)) {
            if (49 < slots.size()) {
                ItemStack s = slots.get(49).getItem();
                if (!s.isEmpty() && !s.is(Items.AIR) && !s.is(Items.BARRIER) && !s.is(Items.HOPPER) && !s.is(Items.CLOCK) && !isNextPageItem(s)) {
                    return 49;
                }
            }
        }
        return -1;
    }

        public static void initSearchWidget(AbstractContainerScreen<?> screen, Consumer<EditBox> widgetAdder) {
        Minecraft mc = Minecraft.getInstance();
        searchBox = new EditBox(mc.font, -9999, -9999, 100, 16, Component.literal(""));
        searchBox.setHint(Component.literal("Cerca incanto..."));
        searchBox.setMaxLength(30);
        searchBox.setResponder(text -> {
            searchBoxQuery = text;
            updateAutocompleteSuggestions(text);
        });
        searchBox.visible = false;
        widgetAdder.accept(searchBox);
    }

    public static void onContainerClosed() {
        cachedContainerId = -1;
        cachedIsAh = false;
        scrollOffset = 0;
        maxScroll = 0;
        isDraggingScrollbar = false;
        lastHoveredListing = null;
        autoNavListing = null;
        autoNavAttempts = 0;
        isAutoScanning = false;
        hasAutoScannedThisSession = false;
        hasSelectedBooksCategory = false;
        autoScanReturning = false;
        autoScanPageCount = 0;
        // Non cancelliamo cachedListings così quando l'utente sfoglia le pagine di /ah
        // o riapre l'asta ritrova tutti i prezzi migliori precedentemente scansionati!
        if (searchBox != null) {
            searchBox.setValue("");
            searchBox.setFocused(false);
            searchBox.visible = false;
            searchBox.setPosition(-9999, -9999);
        }
        currentSuggestions.clear();
        hoveredSuggestionIndex = -1;
        isClearHovered = false;
        btnClearX = -9999;
        btnClearY = -9999;
        lastSearchBoxX = -9999;
        lastSearchBoxY = -9999;
        dropX = -9999;
        dropY = -9999;
        dropW = 0;
        dropH = 0;
    }

    public static boolean handleKeyPressed(KeyEvent event) {
        if (searchBox != null && searchBox.isFocused() && searchBox.visible) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                searchBox.setFocused(false);
                currentSuggestions.clear();
                return true;
            }
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
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
                        mc.player.sendOverlayMessage(Component.literal("§a• Filtro attivato: " + s.displayName));
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
                boolean isCustom = "custom".equalsIgnoreCase(ee.category);
                String badge = isCustom ? "[C]" : "[V]";
                int badgeColor = isCustom ? 0xFF10B981 : 0xFF818CF8;

                int targetLvl = (requestedLevel > 0 && requestedLevel <= ee.maxLevel + 1) ? requestedLevel : (ee.maxLevel > 1 ? ee.maxLevel : 1);
                String searchKey = ee.id + ":" + targetLvl;

                if (!addedKeys.contains(searchKey)) {
                    addedKeys.add(searchKey);
                    String display = AnvilOptimizer.formatEnchantName(com.litness.optimizer.data.LitnessI18n.getEnchantName(ee), targetLvl);
                    currentSuggestions.add(new AutocompleteSuggestion(ee.id, targetLvl, searchKey, display, badge, badgeColor));
                }
            }
            if (currentSuggestions.size() >= 5) break;
        }
    }

    public static boolean handleMouseClicked(Screen screen, MouseButtonEvent event) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen) || !isAuctionHouse(containerScreen)) {
            return false;
        }

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        boolean isLeftClick = (button == 0 || button == InputConstants.MOUSE_BUTTON_LEFT);

        boolean isMouseInSidebar = (mouseX >= lastSideX && mouseX <= lastSideX + lastSideW && mouseY >= lastSideY && mouseY <= lastSideY + lastSideH);
        boolean isMouseInDropdown = (dropW > 0 && dropH > 0 && mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY <= dropY + dropH);

        // Se il cursore non si trova né nella sidebar né nel menu a discesa, non intercettare: lascia gestire a vanilla
        if (!isMouseInSidebar && !isMouseInDropdown) {
            if (!currentSuggestions.isEmpty()) {
                currentSuggestions.clear();
                hoveredSuggestionIndex = -1;
            }
            return false;
        }

        // 1. Click su un suggerimento della tendina di completamento
        if (isLeftClick && !currentSuggestions.isEmpty() && dropW > 0 && dropH > 0) {
            int targetIndex = -1;
            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY <= dropY + dropH) {
                targetIndex = (int) ((mouseY - (dropY + 2)) / 18);
            } else if (hoveredSuggestionIndex >= 0) {
                targetIndex = hoveredSuggestionIndex;
            }

            if (targetIndex >= 0 && targetIndex < currentSuggestions.size()) {
                AutocompleteSuggestion s = currentSuggestions.get(targetIndex);
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
                scrollOffset = 0;
                maxScroll = 0;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendOverlayMessage(Component.literal("§a• Filtro attivato: " + s.displayName));
                }
                return true;
            }
        }

        // 2. Click sul pulsante Azzera (rigorosamente entro i confini visivi del pulsante)
        boolean clickedClear = (btnClearW > 0 && btnClearH > 0 
                && mouseX >= btnClearX && mouseX <= btnClearX + btnClearW 
                && mouseY >= btnClearY && mouseY <= btnClearY + btnClearH);
        if (isLeftClick && clickedClear) {
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
            scrollOffset = 0;
            maxScroll = 0;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal("§c• Ricerca azzerata"));
            }
            return true;
        }

        // 2b. Click sul pulsante [⚡ Sfoglia] o sul footer interattivo
        boolean clickedScan = (btnScanW > 0 && btnScanH > 0
                && mouseX >= btnScanX && mouseX <= btnScanX + btnScanW
                && mouseY >= btnScanY && mouseY <= btnScanY + btnScanH);
        boolean clickedFooter = (footerBtnW > 0 && footerBtnH > 0
                && mouseX >= footerBtnX && mouseX <= footerBtnX + footerBtnW
                && mouseY >= footerBtnY && mouseY <= footerBtnY + footerBtnH);
        if (isLeftClick && (clickedScan || clickedFooter)) {
            Minecraft mc = Minecraft.getInstance();
            if (isAutoScanning) {
                isAutoScanning = false;
                autoScanReturning = false;
                autoScanPageCount = 0;
                if (mc.player != null) {
                    mc.player.sendOverlayMessage(Component.literal("§c• Scansione /ah interrotta"));
                }
            } else {
                isAutoScanning = true;
                autoScanReturning = false;
                autoScanPageCount = 0;
                lastAutoScanClick = System.currentTimeMillis();
                int nextSlot = findNextPageSlot(containerScreen);
                if (nextSlot >= 0) {
                    Slot s = containerScreen.getMenu().slots.get(nextSlot);
                    clickSlot(containerScreen, s);
                    if (mc.player != null) {
                        mc.player.sendOverlayMessage(Component.literal("§e✦ Avvio scansione completa di tutte le pagine /ah..."));
                    }
                } else {
                    isAutoScanning = false;
                    if (mc.player != null) {
                        mc.player.sendOverlayMessage(Component.literal("§a✔ Sei già all'unica pagina disponibile"));
                    }
                }
            }
            return true;
        }

        // 3. Click sulla barra di ricerca
        if (isLeftClick && searchBox != null && searchBox.visible) {
            if (mouseX >= lastSearchBoxX && mouseX <= lastSearchBoxX + lastSearchBoxW &&
                mouseY >= lastSearchBoxY && mouseY <= lastSearchBoxY + lastSearchBoxH) {
                searchBox.setFocused(true);
                if (screen != null) {
                    screen.setFocused(searchBox);
                }
                searchBox.mouseClicked(event, false);
                return true;
            } else if (searchBox.isFocused()) {
                searchBox.setFocused(false);
                if (screen != null && screen.getFocused() == searchBox) {
                    screen.setFocused(null);
                }
            }
        }

        // 4. Click sulla scrollbar per scorrere o iniziare il trascinamento
        if (isLeftClick && maxScroll > 0 && scrollAreaH > 0
                && mouseX >= scrollBarX - 3 && mouseX <= scrollBarX + scrollBarW + 3
                && mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaH) {
            isDraggingScrollbar = true;
            dragStartMouseY = mouseY;
            if (mouseY < scrollThumbY) {
                scrollOffset = Math.max(0, scrollOffset - scrollAreaH / 2);
            } else if (mouseY > scrollThumbY + scrollThumbH) {
                scrollOffset = Math.min(maxScroll, scrollOffset + scrollAreaH / 2);
            }
            dragStartScrollOffset = scrollOffset;
            return true;
        }

        // 5. Click su un'offerta nella lista (Left-click: Acquisto / Apertura menu, Right-click: Rimuovi)
        boolean isRightClick = (button == 1 || button == InputConstants.MOUSE_BUTTON_RIGHT);
        AhListing clickedListing = null;
        if (mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaH) {
            for (ClickableListingRect r : currentClickableRects) {
                if (mouseY >= r.y1 && mouseY <= r.y2) {
                    clickedListing = r.listing;
                    break;
                }
            }
        }
        if (clickedListing == null) {
            clickedListing = lastHoveredListing;
        }

        if (clickedListing != null) {
            AhListing targetListing = clickedListing;
            if (isLeftClick) {
                int slotIdx = findSlotForListing(containerScreen, targetListing);
                Minecraft mc = Minecraft.getInstance();
                if (slotIdx >= 0) {
                    // L'oggetto e' sulla pagina corrente! Clicca direttamente lo slot per aprire il menu di conferma acquisto
                    Slot targetSlot = containerScreen.getMenu().slots.get(slotIdx);
                    pendingBuyKey = targetListing.key;
                    pendingBuyPrice = targetListing.price;
                    pendingBuyTime = System.currentTimeMillis();
                    clickSlot(containerScreen, targetSlot);
                    if (mc.player != null) {
                        mc.player.sendOverlayMessage(Component.literal("Â§aâœ” Apertura menu acquisto per " + targetListing.getDisplayNameWithLevel()));
                    }
                    return true;
                } else {
                    // L'oggetto NON si trova sulla pagina corrente: avvia auto-navigazione automatica!
                    autoNavListing = targetListing;
                    autoNavAttempts = 0;
                    autoNavLastClick = System.currentTimeMillis();

                    int nextSlot = findNextPageSlot(containerScreen);
                    if (nextSlot >= 0) {
                        Slot s = containerScreen.getMenu().slots.get(nextSlot);
                        clickSlot(containerScreen, s);
                        if (mc.player != null) {
                            mc.player.sendOverlayMessage(Component.literal("Â§eâœ¦ Sfoglio /ah per trovare " + targetListing.getDisplayNameWithLevel() + "..."));
                        }
                    } else {
                        int prevSlot = findPrevPageSlot(containerScreen);
                        if (prevSlot >= 0) {
                            Slot s = containerScreen.getMenu().slots.get(prevSlot);
                            clickSlot(containerScreen, s);
                            if (mc.player != null) {
                                mc.player.sendOverlayMessage(Component.literal("Â§eâœ¦ Sfoglio indietro per trovare " + targetListing.getDisplayNameWithLevel() + "..."));
                            }
                        } else {
                            if (mc.player != null) {
                                mc.player.sendOverlayMessage(Component.literal("Â§câ€¢ Impossibile sfogliare: tasto pagina non trovato"));
                            }
                            autoNavListing = null;
                        }
                    }
                    return true;
                }
            } else if (isRightClick) {
                cachedListings.remove(targetListing.key);
                for (Set<String> pageSet : pageToListings.values()) {
                    pageSet.remove(targetListing.key);
                }
                lastScreenListingKeys.remove(targetListing.key);
                recalculateMinPrices();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendOverlayMessage(Component.literal("Â§câ€¢ Offerta rimossa: " + targetListing.getDisplayNameWithLevel()));
                }
                return true;
            }
        }

        if (!currentSuggestions.isEmpty() && (mouseX < dropX || mouseX > dropX + dropW || mouseY < dropY || mouseY > dropY + dropH)) {
            currentSuggestions.clear();
            hoveredSuggestionIndex = -1;
        }

        return false;
    }

    public static boolean handleMouseScrolled(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen) || !isAuctionHouse(containerScreen)) {
            return false;
        }
        // Se il cursore si trova dentro la sidebar AH
        if (mouseX >= lastSideX && mouseX <= lastSideX + lastSideW && mouseY >= lastSideY && mouseY <= lastSideY + lastSideH) {
            if (maxScroll > 0) {
                int scrollStep = 22;
                int delta = (int) (verticalAmount * scrollStep);
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta));
                return true;
            }
        }
        return false;
    }

    public static boolean handleMouseDragged(Screen screen, MouseButtonEvent event, double dx, double dy) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen) || !isAuctionHouse(containerScreen)) {
            return false;
        }
        if (isDraggingScrollbar && maxScroll > 0 && scrollAreaH > scrollThumbH) {
            double deltaY = event.y() - dragStartMouseY;
            double trackRange = scrollAreaH - scrollThumbH;
            int newOffset = dragStartScrollOffset + (int) ((deltaY / trackRange) * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, newOffset));
            return true;
        }
        return false;
    }

    public static boolean handleMouseReleased(Screen screen, MouseButtonEvent event) {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return false;
    }

    public static void tickAutoNavigation(AbstractContainerScreen<?> screen) {
        if (screen == null || !isAuctionHouse(screen)) {
            autoNavListing = null;
            isAutoScanning = false;
            autoScanReturning = false;
            autoScanPageCount = 0;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        // Scansione controllata: attivata SOLO se l'utente clicca esplicitamente [âš¡ Sfoglia] nella sidebar
        // Non invia MAI click automatici non richiesti all'apertura di /ah! L'utente naviga libero.
        if (isAutoScanning) {
            if (now - lastAutoScanClick < 380) return; // Pausa per sincronizzazione della cassa

            int nextSlot = findNextPageSlot(screen);
            if (nextSlot >= 0 && autoScanPageCount < 10) {
                autoScanPageCount++;
                lastAutoScanClick = now;
                Slot s = screen.getMenu().slots.get(nextSlot);
                clickSlot(screen, s);
            } else {
                // Raggiunta la fine o il limite (10 pag): STOP pulito senza loop all'indietro!
                isAutoScanning = false;
                autoScanPageCount = 0;
                if (mc.player != null) {
                    mc.player.sendOverlayMessage(Component.literal("Â§aâœ” Scansione terminata: " + cachedListings.size() + " offerte in memoria"));
                }
            }
            return;
        }

        // 2. NAVIGAZIONE AUTOMATICA PER TROVARE E COMPRARE UN'OFFERTA SU ALTRA PAGINA
        if (autoNavListing != null) {
            if (now - autoNavLastClick < 350) return;

            int slotIdx = findSlotForListing(screen, autoNavListing);
            if (slotIdx >= 0) {
                Slot s = screen.getMenu().slots.get(slotIdx);
                pendingBuyKey = autoNavListing.key;
                pendingBuyPrice = autoNavListing.price;
                pendingBuyTime = now;
                clickSlot(screen, s);
                if (mc.player != null) {
                    mc.player.sendOverlayMessage(Component.literal("§a✔ Trovato! Apertura menu acquisto per " + autoNavListing.getDisplayNameWithLevel()));
                }
                autoNavListing = null;
                autoNavAttempts = 0;
                return;
            }

            if (autoNavAttempts >= 18) {
                cachedListings.remove(autoNavListing.key);
                recalculateMinPrices();
                if (mc.player != null) {
                    mc.player.sendOverlayMessage(Component.literal("§c• Offerta non più presente su /ah (rimossa dai prezzi)"));
                }
                autoNavListing = null;
                autoNavAttempts = 0;
                return;
            }

            int nextSlot = findNextPageSlot(screen);
            if (nextSlot >= 0) {
                autoNavAttempts++;
                autoNavLastClick = now;
                Slot s = screen.getMenu().slots.get(nextSlot);
                clickSlot(screen, s);
                if (mc.player != null) {
                    mc.player.sendOverlayMessage(Component.literal("§e✦ Sfoglio /ah per trovare " + autoNavListing.getDisplayNameWithLevel() + " (pagina " + (autoNavAttempts + 1) + ")..."));
                }
            } else {
                int prevSlot = findPrevPageSlot(screen);
                if (prevSlot >= 0) {
                    autoNavAttempts++;
                    autoNavLastClick = now;
                    Slot s = screen.getMenu().slots.get(prevSlot);
                    clickSlot(screen, s);
                    if (mc.player != null) {
                        mc.player.sendOverlayMessage(Component.literal("§e✦ Sfoglio indietro per trovare " + autoNavListing.getDisplayNameWithLevel() + "..."));
                    }
                } else {
                    cachedListings.remove(autoNavListing.key);
                    recalculateMinPrices();
                    if (mc.player != null) {
                        mc.player.sendOverlayMessage(Component.literal("§c• Fine delle aste: offerta non trovata (rimossa dai prezzi)"));
                    }
                    autoNavListing = null;
                    autoNavAttempts = 0;
                }
            }
        }
    }

    public static void renderAhSidebar(GuiGraphicsExtractor extractor, AbstractContainerScreen<?> screen, int leftPos, int topPos, int imageWidth, int imageHeight, int mouseX, int mouseY, float delta) {
        if (screen == null) return;
        if (!isAuctionHouse(screen)) {
            if (searchBox != null) {
                searchBox.visible = false;
                searchBox.setFocused(false);
                searchBox.setPosition(-9999, -9999);
            }
            btnClearX = -9999;
            btnClearY = -9999;
            isClearHovered = false;
            return;
        }

        scanCurrentContainer(screen);

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Calcolo posizione e larghezza adattiva senza MAI sovrapporre la cassa / container
        int availableLeft = leftPos - 8;
        int sideW = Math.min(190, Math.max(120, availableLeft));
        int sideX = leftPos - sideW - 6;
        if (sideX < 2 && (screen.width - (leftPos + imageWidth)) >= 140) {
            int rightStart = leftPos + imageWidth + 6;
            sideW = Math.min(190, screen.width - rightStart - 4);
            sideX = rightStart;
        } else if (sideX < 2) {
            sideX = 2;
            sideW = Math.max(110, leftPos - sideX - 6);
        }
        int sideY = topPos;
        int sideH = imageHeight;

        lastSideX = sideX;
        lastSideY = sideY;
        lastSideW = sideW;
        lastSideH = sideH;

        // Sfondo dark glass solido Obsidian Minimal
        LitnessOptimizerScreen.drawRoundedRect(extractor, sideX, sideY, sideW, sideH, 8, 0xFA090B10);
        LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX, sideY, sideW, sideH, 8, 0xFF1E2334);

        // Barra accento blu elettrico Obsidian |
        LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 8, sideY + 7, 3, 12, 1, 0xFF3B82F6);
        extractor.text(font, "MERCATO AH", sideX + 16, sideY + 9, 0xFFFFFFFF, false);

        Set<String> searchEnchants = getEffectiveSearchEnchants();
        String rawQuery = (searchBox != null) ? searchBox.getValue().trim() : "";
        boolean hasQuery = !rawQuery.isEmpty();
        boolean hasFilter = searchEnchants != null && !searchEnchants.isEmpty();

        // Tasto [Azzera]
        btnClearW = 34;
        btnClearX = sideX + sideW - btnClearW - 6;
        btnClearY = sideY + 5;
        isClearHovered = mouseX >= btnClearX && mouseX <= btnClearX + btnClearW && mouseY >= btnClearY && mouseY <= btnClearY + btnClearH;
        int btnBg = isClearHovered ? 0xFF991B1B : 0xFF181D2E;
        int btnBorder = isClearHovered ? 0xFFEF4444 : 0xFF1E2334;
        LitnessOptimizerScreen.drawRoundedRect(extractor, btnClearX, btnClearY, btnClearW, btnClearH, 4, btnBg);
        LitnessOptimizerScreen.drawRoundedOutline(extractor, btnClearX, btnClearY, btnClearW, btnClearH, 4, btnBorder);
        extractor.centeredText(font, "Azzera", btnClearX + (btnClearW / 2), btnClearY + 2, isClearHovered ? 0xFFFCA5A5 : 0xFF94A3B8);

        // Tasto [⚡ Sfoglia]
        btnScanW = 46;
        btnScanX = btnClearX - btnScanW - 4;
        btnScanY = sideY + 5;
        isScanHovered = mouseX >= btnScanX && mouseX <= btnScanX + btnScanW && mouseY >= btnScanY && mouseY <= btnScanY + btnScanH;
        int scanBg = isAutoScanning ? 0xFF065F46 : (isScanHovered ? 0xFF1E3A8A : 0xFF181D2E);
        int scanBorder = isAutoScanning ? 0xFF10B981 : (isScanHovered ? 0xFF3B82F6 : 0xFF1E2334);
        LitnessOptimizerScreen.drawRoundedRect(extractor, btnScanX, btnScanY, btnScanW, btnScanH, 4, scanBg);
        LitnessOptimizerScreen.drawRoundedOutline(extractor, btnScanX, btnScanY, btnScanW, btnScanH, 4, scanBorder);
        String scanText = isAutoScanning ? "Stop" : "⚡ Sfoglia";
        extractor.centeredText(font, scanText, btnScanX + (btnScanW / 2), btnScanY + 2, isAutoScanning ? 0xFF6EE7B7 : (isScanHovered ? 0xFF93C5FD : 0xFF60A5FA));

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

        if (!hasQuery && !hasFilter) {
            scrollOffset = 0;
            maxScroll = 0;
            // Stato vuoto: Nessun incanto cercato
            int cardY = curY + 2;
            int cardH = sideH - (cardY - sideY) - 10;
            LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 6, cardY, sideW - 12, cardH, 6, 0xFF131724);
            LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX + 6, cardY, sideW - 12, cardH, 6, 0xFF1E2334);

            extractor.fakeItem(new ItemStack(Items.ENCHANTED_BOOK), sideX + (sideW / 2) - 8, cardY + 14);

            extractor.centeredText(font, "Nessun filtro attivo", sideX + (sideW / 2), cardY + 36, 0xFFFFFFFF);
            extractor.centeredText(font, "Scrivi nella barra sopra,", sideX + (sideW / 2), cardY + 52, 0xFF9CA3AF);
            extractor.centeredText(font, "premi [+ Cerca] in [N],", sideX + (sideW / 2), cardY + 64, 0xFF60A5FA);
            extractor.centeredText(font, "oppure pinna un set!", sideX + (sideW / 2), cardY + 76, 0xFF9CA3AF);

            // Rendering Menu a Discesa Autocomplete anche in stato vuoto se si sta scrivendo
            renderAutocompleteDropdown(extractor, font, searchBoxX, searchBoxY + searchBoxH + 1, searchBoxW, mouseX, mouseY);
            return;
        }

        // Filtra e ordina i listing per prezzo crescente in tempo reale
        List<AhListing> matchingListings = new ArrayList<>();
        List<EnchantFilter> filters = getEffectiveFilters();

        String q = rawQuery.toLowerCase();
        int queryLevel = 0;
        String queryBase = q;
        Matcher numMatcher = Pattern.compile("^(.*?)\\s+([0-9ivx]+)$").matcher(q);
        if (numMatcher.find()) {
            queryBase = numMatcher.group(1).trim();
            queryLevel = LitnessTooltipHandler.parseRomanOrInt(numMatcher.group(2).trim());
        }

        for (AhListing l : cachedListings.values()) {
            if (hasQuery) {
                String nameLower = l.enchantName.toLowerCase();
                String idLower = l.enchantId.toLowerCase();
                boolean match = nameLower.contains(queryBase) || idLower.contains(queryBase);
                if (!match) {
                    String translated = com.litness.optimizer.data.LitnessI18n.translateEnchantName(l.enchantId).toLowerCase();
                    if (translated.contains(queryBase)) match = true;
                }
                if (match) {
                    if (queryLevel > 0) {
                        if (l.level == queryLevel) matchingListings.add(l);
                    } else {
                        matchingListings.add(l);
                    }
                }
            } else if (hasFilter) {
                if (matchesAnyFilter(l.enchantId, l.level, filters)) {
                    matchingListings.add(l);
                }
            }
        }
        Collections.sort(matchingListings);

        // Header info filtro
        if (hasQuery) {
            extractor.text(font, "§7Cerca: §f\"" + trimToWidth(font, rawQuery, sideW - 70) + "\"", sideX + 8, curY, 0xFFFFFFFF, false);
        } else {
            int filterCount = filters.size();
            String filterInfo;
            if (filterCount == 1) {
                EnchantFilter first = filters.get(0);
                EnchantmentEntry fe = EnchantmentDatabase.getEnchantment(first.enchantId);
                String baseName = fe != null ? com.litness.optimizer.data.LitnessI18n.getEnchantName(fe) : com.litness.optimizer.data.LitnessI18n.translateEnchantName(first.enchantId);
                filterInfo = (first.targetLevel > 0) ? AnvilOptimizer.formatEnchantName(baseName, first.targetLevel) : baseName;
            } else {
                filterInfo = filterCount + " incanti cercati";
            }
            extractor.text(font, "§7Cerca: §f" + trimToWidth(font, filterInfo, sideW - 65), sideX + 8, curY, 0xFFFFFFFF, false);
        }
        curY += 10;
        extractor.text(font, "§9• §7Libri lisci (1 incanto)", sideX + 8, curY, 0xFF9CA3AF, false);
        curY += 12;

        if (matchingListings.isEmpty()) {
            scrollOffset = 0;
            maxScroll = 0;
            // Nessun libro trovato finora
            int boxY = curY + 2;
            int boxH = sideH - (boxY - sideY) - 10;
            LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 6, boxY, sideW - 12, boxH, 6, 0xFF131724);
            LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX + 6, boxY, sideW - 12, boxH, 6, 0xFF1E2334);

            extractor.fakeItem(new ItemStack(Items.ENCHANTED_BOOK), sideX + (sideW / 2) - 8, boxY + 8);
            if (cachedListings.isEmpty()) {
                extractor.centeredText(font, "0 libri scansionati", sideX + (sideW / 2), boxY + 28, 0xFF60A5FA);
                extractor.centeredText(font, "Sfoglia le pagine", sideX + (sideW / 2), boxY + 44, 0xFFE2E8F0);
                extractor.centeredText(font, "di /ah o premi [⚡ Sfoglia]", sideX + (sideW / 2), boxY + 54, 0xFFE2E8F0);
                extractor.centeredText(font, "per leggere i prezzi!", sideX + (sideW / 2), boxY + 64, 0xFFE2E8F0);
            } else {
                extractor.centeredText(font, "0 offerte trovate", sideX + (sideW / 2), boxY + 28, 0xFF60A5FA);
                extractor.centeredText(font, "Nessun venditore per", sideX + (sideW / 2), boxY + 44, 0xFFE2E8F0);
                extractor.centeredText(font, hasQuery ? "\"" + trimToWidth(font, rawQuery, sideW - 24) + "\"" : "questi incantesimi", sideX + (sideW / 2), boxY + 54, 0xFFFBBF24);
                extractor.centeredText(font, "nelle pagine lette", sideX + (sideW / 2), boxY + 64, 0xFF9CA3AF);
            }

            extractor.centeredText(font, "Solo libri lisci", sideX + (sideW / 2), boxY + boxH - 14, 0xFF6B7280);

            // Rendering Menu a Discesa Autocomplete
            renderAutocompleteDropdown(extractor, font, searchBoxX, searchBoxY + searchBoxH + 1, searchBoxW, mouseX, mouseY);
            return;
        }

        // Trova il miglior listing per ciascun incantesimo cercato
        Map<String, AhListing> bestPerEnchant = new LinkedHashMap<>();
        Set<String> bestKeys = new HashSet<>();
        if (hasQuery) {
            for (AhListing l : matchingListings) {
                String groupKey = l.enchantId + ":" + l.level;
                AhListing curBest = bestPerEnchant.get(groupKey);
                if (curBest == null || l.price < curBest.price) {
                    bestPerEnchant.put(groupKey, l);
                }
            }
            for (AhListing b : bestPerEnchant.values()) {
                bestKeys.add(b.key);
            }
        } else {
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
        }

        // Calcolo area di scorrimento (Scroll Area)
        scrollAreaY = curY + 2;
        int footerH = 15;
        scrollAreaH = Math.max(30, (sideY + sideH - footerH) - scrollAreaY);

        int rowH = 15;
        int totalRows = Math.min(100, matchingListings.size());

        int totalContentHeight = 0;
        if (bestPerEnchant.size() <= 1) {
            totalContentHeight += 43 + 5;
        } else {
            totalContentHeight += 12;
            totalContentHeight += bestPerEnchant.size() * (26 + 3);
            totalContentHeight += 2;
        }
        totalContentHeight += 12; // "Tutti i prezzi (X):"
        totalContentHeight += totalRows * rowH;
        totalContentHeight += 4;

        maxScroll = Math.max(0, totalContentHeight - scrollAreaH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int rightPadding = maxScroll > 0 ? 9 : 5;
        int cardWidth = sideW - 5 - rightPadding;

        scrollBarW = 3;
        scrollBarX = sideX + sideW - scrollBarW - 2;
        scrollThumbH = Math.max(12, (int) ((float) scrollAreaH * scrollAreaH / Math.max(scrollAreaH + 1, totalContentHeight)));
        scrollThumbY = scrollAreaY + (maxScroll > 0 ? (int) ((float) scrollOffset / maxScroll * (scrollAreaH - scrollThumbH)) : 0);

        // Abilita il ritaglio (Scissoring) per non far sbordare il contenuto scrollabile
        extractor.enableScissor(sideX + 2, scrollAreaY, sideX + sideW - 1, scrollAreaY + scrollAreaH);

        // Variabili per il tooltip al passaggio del mouse
        AhListing hoveredListing = null;
        boolean hoveredIsBest = false;
        int hoveredRank = 1;

        currentClickableRects.clear();
        int drawY = scrollAreaY - scrollOffset;
        boolean isMouseInScrollArea = (mouseX >= sideX + 5 && mouseX <= sideX + 5 + cardWidth 
                && mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaH);

        // 1. Box Miglior Prezzo (per ciascun incanto trovato, o singolo se 1 solo)
        if (bestPerEnchant.size() <= 1) {
            AhListing best = !bestPerEnchant.isEmpty() ? bestPerEnchant.values().iterator().next() : matchingListings.get(0);
            int bestBoxY = drawY;
            int bestBoxH = 43;
            if (bestBoxY >= scrollAreaY - 4 && bestBoxY + bestBoxH <= scrollAreaY + scrollAreaH + 4) {
                boolean isBestHovered = isMouseInScrollArea && (mouseY >= bestBoxY && mouseY <= bestBoxY + bestBoxH);
                currentClickableRects.add(new ClickableListingRect(bestBoxY, bestBoxY + bestBoxH, best));
                if (isBestHovered) {
                    hoveredListing = best;
                    hoveredIsBest = true;
                    hoveredRank = 1;
                }

                int bestBg = isBestHovered ? 0xFF1E2438 : 0xFF131724;
                int bestBorder = isBestHovered ? 0xFF3B82F6 : 0xFF1E2334;
                LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 5, bestBoxY, cardWidth, bestBoxH, 6, bestBg);
                LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX + 5, bestBoxY, cardWidth, bestBoxH, 6, bestBorder);

                extractor.text(font, "• MIGLIOR PREZZO:", sideX + 8, bestBoxY + 3, 0xFF60A5FA, false);
                EnchantmentEntry eeSingle = EnchantmentDatabase.getEnchantment(best.enchantId);
                ItemStack iconSingle = EnchantmentThematicIconHelper.getIcon(eeSingle);
                if (bestBoxY + 3 >= scrollAreaY - 2 && bestBoxY + 19 <= scrollAreaY + scrollAreaH + 2) {
                    extractor.fakeItem(iconSingle, sideX + 5 + cardWidth - 19, bestBoxY + 3);
                }

                String bestName = trimToWidth(font, best.getDisplayNameWithLevel(), cardWidth - 28);
                extractor.text(font, bestName, sideX + 8, bestBoxY + 13, 0xFFFFFFFF, true);

                String sellerDisplay = trimToWidth(font, best.seller, 65);
                String bestPriceLine = "§a" + best.formattedPrice + " §7(da " + sellerDisplay + ")";
                extractor.text(font, bestPriceLine, sideX + 8, bestBoxY + 23, 0xFF34D399, false);
                extractor.text(font, "§9" + best.formattedBreakdown, sideX + 8, bestBoxY + 33, 0xFF60A5FA, false);
            }
            drawY = bestBoxY + bestBoxH + 5;
        } else {
            // Più incanti in ricerca: mostra una card per ciascun incanto trovato
            if (drawY >= scrollAreaY && drawY + 10 <= scrollAreaY + scrollAreaH) {
                extractor.text(font, "• MIGLIORI PREZZI:", sideX + 8, drawY, 0xFF60A5FA, false);
            }
            drawY += 10;
            int cardH = 26;
            for (AhListing best : bestPerEnchant.values()) {
                if (drawY >= scrollAreaY - 4 && drawY + cardH <= scrollAreaY + scrollAreaH + 4) {
                    boolean isCardHovered = isMouseInScrollArea && (mouseY >= drawY && mouseY <= drawY + cardH);
                    currentClickableRects.add(new ClickableListingRect(drawY, drawY + cardH, best));
                    if (isCardHovered) {
                        hoveredListing = best;
                        hoveredIsBest = true;
                        hoveredRank = 1;
                    }
                    int cardBg = isCardHovered ? 0xFF1E2438 : 0xFF131724;
                    int cardBorder = isCardHovered ? 0xFF3B82F6 : 0xFF1E2334;
                    LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 5, drawY, cardWidth, cardH, 6, cardBg);
                    LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX + 5, drawY, cardWidth, cardH, 6, cardBorder);

                    EnchantmentEntry eeMulti = EnchantmentDatabase.getEnchantment(best.enchantId);
                    ItemStack iconMulti = EnchantmentThematicIconHelper.getIcon(eeMulti);
                    if (drawY + 5 >= scrollAreaY - 2 && drawY + 21 <= scrollAreaY + scrollAreaH + 2) {
                        extractor.fakeItem(iconMulti, sideX + 8, drawY + 5);
                    }

                    int nameMaxW = Math.max(30, cardWidth - 80);
                    String nameStr = trimToWidth(font, best.getDisplayNameWithLevel(), nameMaxW);
                    extractor.text(font, nameStr, sideX + 27, drawY + 3, 0xFFFFFFFF, true);
                    extractor.text(font, best.formattedPrice, sideX + 5 + cardWidth - font.width(best.formattedPrice) - 3, drawY + 3, 0xFF34D399, true);

                    String subStr = "§9" + best.formattedBreakdown + " §7(da " + trimToWidth(font, best.seller, 40) + ")";
                    extractor.text(font, trimToWidth(font, subStr, cardWidth - 28), sideX + 27, drawY + 14, 0xFF60A5FA, false);
                }
                drawY += cardH + 3;
            }
            drawY += 2;
        }

        // 2. Lista tutti i prezzi trovati
        if (drawY >= scrollAreaY && drawY + 10 <= scrollAreaY + scrollAreaH) {
            extractor.text(font, "Tutti i prezzi (" + matchingListings.size() + "):", sideX + 8, drawY, 0xFF9CA3AF, false);
        }
        drawY += 10;

        for (int i = 0; i < totalRows; i++) {
            AhListing l = matchingListings.get(i);
            boolean isTop = bestKeys.contains(l.key);

            int itemY = drawY + i * rowH;
            if (itemY >= scrollAreaY - 2 && itemY + rowH <= scrollAreaY + scrollAreaH + 2) {
                boolean isRowHovered = isMouseInScrollArea && (mouseY >= itemY && mouseY <= itemY + rowH - 1);
                currentClickableRects.add(new ClickableListingRect(itemY, itemY + rowH - 1, l));
                if (isRowHovered) {
                    hoveredListing = l;
                    hoveredIsBest = isTop;
                    hoveredRank = i + 1;
                }

                int rowBg = isRowHovered ? 0xFF1E2438 : (isTop ? 0x251E293B : (i % 2 == 0 ? 0x15151926 : 0x00000000));
                LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 5, itemY, cardWidth, rowH - 1, 4, rowBg);
                if (isRowHovered) {
                    LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX + 5, itemY, cardWidth, rowH - 1, 4, 0xFF3B82F6);
                }

                // Rango
                String rank = (i + 1) + ".";
                extractor.text(font, rank, sideX + 8, itemY + 3, isTop ? 0xFF60A5FA : 0xFF64748B, false);

                // Prezzo formattato
                extractor.text(font, l.formattedPrice, sideX + 22, itemY + 3, isTop ? 0xFF34D399 : 0xFFE2E8F0, false);

                // Nome incanto
                String sub = AnvilOptimizer.formatEnchantName(l.enchantName, l.level);
                String subTrimmed = trimToWidth(font, sub, 65);
                extractor.text(font, subTrimmed, sideX + 78, itemY + 3, isTop ? 0xFFFFFFFF : 0xFF94A3B8, false);

                // Venditore allineato a destra
                String sellerTrimmed = trimToWidth(font, l.seller, Math.max(30, cardWidth - 138));
                extractor.text(font, sellerTrimmed, sideX + 5 + cardWidth - font.width(sellerTrimmed) - 3, itemY + 3, isTop ? 0xFFCBD5E1 : 0xFF64748B, false);
            }
        }

        // Disabilita lo scissoring
        extractor.disableScissor();

        // 3. Barra di scorrimento laterale (Scrollbar) se il contenuto eccede
        if (maxScroll > 0) {
            LitnessOptimizerScreen.drawRoundedRect(extractor, scrollBarX, scrollAreaY, scrollBarW, scrollAreaH, 2, 0x301E293B);
            boolean isThumbHovered = mouseX >= scrollBarX - 1 && mouseX <= scrollBarX + scrollBarW + 1 && mouseY >= scrollThumbY && mouseY <= scrollThumbY + scrollThumbH;
            int thumbColor = (isDraggingScrollbar || isThumbHovered) ? 0xFF60A5FA : 0xFF3B82F6;
            LitnessOptimizerScreen.drawRoundedRect(extractor, scrollBarX, scrollThumbY, scrollBarW, scrollThumbH, 2, thumbColor);
        }

        // Footer informativo e interattivo per avviare o fermare la scansione
        int footerBoxH = 15;
        int footerY = sideY + sideH - footerBoxH;
        footerBtnX = sideX + 1;
        footerBtnY = footerY;
        footerBtnW = sideW - 2;
        footerBtnH = footerBoxH - 1;
        boolean isFooterHovered = mouseX >= footerBtnX && mouseX <= footerBtnX + footerBtnW && mouseY >= footerBtnY && mouseY <= footerBtnY + footerBtnH;
        int footerBg = isAutoScanning ? 0xFF064E3B : (isFooterHovered ? 0xFF1E293B : 0xFF0D0F18);
        LitnessOptimizerScreen.drawRoundedRect(extractor, footerBtnX, footerBtnY, footerBtnW, footerBtnH, 6, footerBg);
        String footerMsg = isAutoScanning ? "§e✦ Scansione in corso... (clicca Stop)" : (isFooterHovered ? "§b⚡ Clicca per sfogliare tutte le pagine" : "Sfoglia /ah per aggiornare");
        extractor.centeredText(font, footerMsg, sideX + (sideW / 2), footerY + 3, isAutoScanning ? 0xFFFDE047 : (isFooterHovered ? 0xFF38BDF8 : 0xFF64748B));

        // Bordo finale contenitore
        LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX, sideY, sideW, sideH, 8, 0xFF1E2334);

        lastHoveredListing = hoveredListing;

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

        // Pannello a discesa moderno Obsidian Minimal
        LitnessOptimizerScreen.drawRoundedRect(extractor, dropX, dropY, dropW, dropH, 6, 0xFA090B10);
        LitnessOptimizerScreen.drawRoundedOutline(extractor, dropX, dropY, dropW, dropH, 6, 0xFF3B82F6);

        hoveredSuggestionIndex = -1;
        for (int i = 0; i < currentSuggestions.size(); i++) {
            AutocompleteSuggestion sug = currentSuggestions.get(i);
            int itemY = dropY + 2 + i * 18;
            boolean isSugHovered = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY < itemY + 18;
            if (isSugHovered) {
                hoveredSuggestionIndex = i;
                LitnessOptimizerScreen.drawRoundedRect(extractor, dropX + 2, itemY, dropW - 4, 18, 4, 0xFF1E2438);
            }

            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(sug.enchantId);
            ItemStack icon = EnchantmentThematicIconHelper.getIcon(ee);
            extractor.fakeItem(icon, dropX + 3, itemY + 1);

            int badgeW = font.width(sug.badge);
            int maxNameW = Math.max(30, dropW - 24 - badgeW - 6);
            extractor.text(font, trimToWidth(font, sug.displayName, maxNameW), dropX + 22, itemY + 5, isSugHovered ? 0xFF60A5FA : 0xFFFFFFFF, false);
            extractor.text(font, sug.badge, dropX + dropW - badgeW - 5, itemY + 5, sug.badgeColor, false);
        }
    }

    private static void renderListingTooltip(GuiGraphicsExtractor extractor, Font font, AhListing listing, boolean isBest, int rank, int mouseX, int mouseY, int screenW, int screenH) {
        if (listing == null) return;

        List<Component> lines = new ArrayList<>();
        if (isBest) {
            lines.add(Component.literal("§6• MIGLIOR PREZZO AH #1"));
        } else {
            lines.add(Component.literal("§e• OFFERTA IN CLASSIFICA #" + rank));
        }
        lines.add(Component.literal("§f" + listing.getDisplayNameWithLevel()));
        lines.add(Component.empty());
        lines.add(Component.literal("§7Prezzo Totale:   " + (isBest ? "§6" : "§a") + listing.formattedPrice));
        if (listing.tax > 0) {
            lines.add(Component.literal("§7Netto Venditore: §f" + AhListing.formatPrice(listing.netPrice)));
            lines.add(Component.literal("§7Tassa Asta:     §c" + AhListing.formatPrice(listing.tax)));
        }
        lines.add(Component.literal("§7Venditore:      §b" + listing.seller));
        lines.add(Component.empty());
        lines.add(Component.literal("§a✦ Clicca con il tasto sinistro per acquistare"));
        lines.add(Component.literal("§8• Tasto destro sulla card per rimuovere l'offerta"));

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

        // Scatola Dark Glass per il tooltip Obsidian Minimal
        LitnessOptimizerScreen.drawRoundedRect(extractor, tipX, tipY, tipW, tipH, 6, 0xFA090B10);
        LitnessOptimizerScreen.drawRoundedOutline(extractor, tipX, tipY, tipW, tipH, 6, 0xFF1E2334);
        LitnessOptimizerScreen.drawRoundedRect(extractor, tipX + 4, tipY + 4, 3, tipH - 8, 1, isBest ? 0xFF3B82F6 : 0xFF10B981);

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

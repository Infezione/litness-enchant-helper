package com.litness.optimizer.gui;

import com.litness.optimizer.calculator.AnvilOptimizer;
import com.litness.optimizer.calculator.AnvilOptimizer.BookInput;
import com.litness.optimizer.calculator.AnvilOptimizer.EnchantApplied;
import com.litness.optimizer.calculator.AnvilOptimizer.MergeStep;
import com.litness.optimizer.calculator.AnvilOptimizer.Solution;
import com.litness.optimizer.data.ActiveSessionManager;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import com.litness.optimizer.data.EnchantmentDatabase.ItemEntry;
import com.litness.optimizer.data.InventoryTracker;
import com.litness.optimizer.data.ItemTypeHelper;
import com.litness.optimizer.data.LitnessI18n;
import com.litness.optimizer.data.SavedSetsManager;
import com.litness.optimizer.data.SavedSetsManager.CustomSet;
import com.litness.optimizer.tooltip.LitnessTooltipHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;

public class LitnessOptimizerScreen extends Screen {

    public enum Tab {
        CATALOG,
        SETS,
        CALCULATOR
    }

    public enum CalcMode {
        SIMULATOR,
        INVENTORY
    }

    private Tab currentTab = Tab.CATALOG;
    private CalcMode calcMode = CalcMode.SIMULATOR;
    private final Screen parentScreen;
    private final AnvilMenu anvilMenu;
    private EnchantmentEntry selectedCatalogEnchant = null;
    private SoftwareButton btnToggleInspectorSet = null;

    // Dimensioni finestra stile Software Desktop (Marloww client style)
    private int getWinW() {
        return Math.min(740, this.width - 20);
    }

    private int getWinH() {
        return Math.min(450, this.height - 16);
    }

    private int getWinX() {
        return (this.width - getWinW()) / 2;
    }

    private int getWinY() {
        return (this.height - getWinH()) / 2;
    }

    private static final int HEADER_H = 32;
    private static final int SIDEBAR_W = 125;

    public static final int CATALOG_PAGE_SIZE = 6;
    public static final int CREATING_SET_PAGE_SIZE = 6;
    public static final int CALCULATOR_PAGE_SIZE = 5;

    // Catalogo
    private EditBox searchField;
    private String categoryFilter = "ALL";
    private String itemFilter = "ALL";
    private final List<EnchantmentEntry> filteredEnchantments = new ArrayList<>();
    private int catalogPage = 0;
    private final List<Button> catalogRowButtons = new ArrayList<>();
    private SoftwareButton btnClearAhSearch = null;

    // Oggetti supportati
    private static final String[] ITEM_KEYS = {
        "sword", "axe", "mace", "trident", "bow", "xbow", "pick", "shovel", "hoe", "rod", "helmet", "chest", "legs", "boots", "elytra"
    };

    private static final int[][] CATEGORY_ITEM_INDICES = {
        {0, 1, 2, 3},          // Armi: Spada, Ascia, Mazza, Tridente
        {4, 5},                // Archi: Arco, Balestra
        {10, 11, 12, 13, 14},  // Armature: Elmo, Corazza, Gambali, Stivali, Elitre
        {6, 7, 8, 9}           // Attrezzi: Piccone, Pala, Zappa, Canna
    };

    public static int getCategoryForItem(int itemIndex) {
        for (int c = 0; c < CATEGORY_ITEM_INDICES.length; c++) {
            for (int idx : CATEGORY_ITEM_INDICES[c]) {
                if (idx == itemIndex) return c;
            }
        }
        return 0;
    }

    private static final int[] PENALTY_STEPS = {0, 1, 3, 7, 15, 31};
    private int selectedItemIndex = 0;
    private int selectedPenaltyIndex = 0;

    // Creazione / Modifica Set
    private boolean isCreatingSet = false;
    private String editingSetId = null;
    private String currentCreatingSetName = "";
    private String currentCreatingSetSearch = "";
    private int creatingSetItemIndex = 0;
    private final Set<String> creatingSetEnchants = new LinkedHashSet<>();
    private EditBox creatingSetNameField;
    private EditBox creatingSetSearchField;
    private int creatingSetPage = 0;
    private final List<Button> creatingSetEnchantButtons = new ArrayList<>();
    private final List<Button> creatingSetLibraryButtons = new ArrayList<>();
    private static record CreatingSetButtonData(int x, int y, int w, int h, EnchantmentEntry entry) {}
    private final List<CreatingSetButtonData> creatingSetButtonDataList = new ArrayList<>();

    // Tasti set salvati
    private final List<Button> savedSetButtons = new ArrayList<>();
    private String savedSetsFilterCategory = "ALL";
    private int savedSetsPage = 0;

    // Calcolatore / Simulatore
    private static final java.util.concurrent.ExecutorService CALC_POOL = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LitnessCalcScreen");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean isCalculating = false;
    private volatile boolean needsWidgetRebuild = false;
    private int calculatorPage = 0;
    private int calculatorScrollOffset = 0;
    private SoftwareButton btnCalcPrev = null;

    // Notifica animata fluida a centro schermo
    private static String toastTitle = null;
    private static String toastSubtitle = null;
    private static boolean toastSuccess = true;
    private static long toastStartTime = 0;

    public static void showCenterNotification(String title, String subtitle, boolean success) {
        toastTitle = title;
        toastSubtitle = subtitle;
        toastSuccess = success;
        toastStartTime = System.currentTimeMillis();
    }
    private SoftwareButton btnCalcNext = null;
    private Solution currentSolution = null;
    private String statusMessage = null;
    private String lastCalculatedKey = "";
    private final Set<String> simulatorSelectedEnchants = new LinkedHashSet<>();
    private final List<EnchantmentEntry> currentVisibleSelectionEnchants = new ArrayList<>();

    // Tasto rapido lingua
    private SoftwareButton btnLangToggle = null;
    private SoftwareButton btnClose = null;

    // Animazioni fluide: pillola scorrevole barra laterale e transizione schede
    private float animatedPillY = -1.0f;
    private long tabTransitionStartTime = System.currentTimeMillis();

    private void switchTab(Tab newTab) {
        if (this.currentTab != newTab) {
            this.currentTab = newTab;
            this.isCreatingSet = false;
            this.tabTransitionStartTime = System.currentTimeMillis();
            this.clearWidgets();
            this.init();
        }
    }

    public LitnessOptimizerScreen(Component title) {
        this(title, null, null, null);
    }

    public LitnessOptimizerScreen(Component title, Screen parentScreen, AnvilMenu anvilMenu) {
        this(title, parentScreen, anvilMenu, null);
    }

    public LitnessOptimizerScreen(Component title, Screen parentScreen, AnvilMenu anvilMenu, Solution initialSolution) {
        super(title != null ? title : Component.literal("Litness Helper"));
        this.parentScreen = parentScreen;
        this.anvilMenu = anvilMenu;
        if (initialSolution != null && !initialSolution.steps.isEmpty()) {
            this.currentSolution = initialSolution;
            this.currentTab = Tab.CALCULATOR;
            this.calcMode = CalcMode.INVENTORY;
        } else if (anvilMenu != null) {
            this.currentTab = Tab.CALCULATOR;
            this.calcMode = CalcMode.INVENTORY;
        } else {
            this.selectedItemIndex = ActiveSessionManager.getActiveItemIndex();
            this.creatingSetItemIndex = this.selectedItemIndex;
        }
    }

    public LitnessOptimizerScreen() {
        this(Component.literal("Litness Helper"), null, null, null);
    }

    public void openSetEditor(SavedSetsManager.CustomSet set) {
        this.currentTab = Tab.SETS;
        this.isCreatingSet = true;
        this.tabTransitionStartTime = System.currentTimeMillis();
        this.editingSetId = (set != null) ? set.id : null;
        this.creatingSetEnchants.clear();
        if (set != null) {
            this.creatingSetEnchants.addAll(set.enchantments);
            this.currentCreatingSetName = set.name;
            int foundIdx = 0;
            for (int i = 0; i < ITEM_KEYS.length; i++) {
                if (ITEM_KEYS[i].equalsIgnoreCase(set.itemKey)) {
                    foundIdx = i;
                    break;
                }
            }
            this.creatingSetItemIndex = foundIdx;
        } else {
            this.currentCreatingSetName = "";
            this.creatingSetItemIndex = 0;
        }
        this.currentCreatingSetSearch = "";
        this.creatingSetPage = 0;
    }

    // -------------------------------------------------------------
    // ROUNDED RECTANGLE DRAWING UTILITIES (Obsidian Minimal Style)
    // -------------------------------------------------------------
    public static void drawRoundedRect(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0) {
            extractor.fill(x, y, x + w, y + h, color);
            return;
        }
        int r = Math.min(radius, Math.min(w / 2, h / 2));
        extractor.fill(x + r, y, x + w - r, y + h, color);
        extractor.fill(x, y + r, x + r, y + h - r, color);
        extractor.fill(x + w - r, y + r, x + w, y + h - r, color);
        if (r >= 2) {
            int cornerColor = ((color >>> 24) * 3 / 4 << 24) | (color & 0x00FFFFFF);
            extractor.fill(x + 1, y + 1, x + r, y + r, cornerColor);
            extractor.fill(x + w - r, y + 1, x + w - 1, y + r, cornerColor);
            extractor.fill(x + 1, y + h - r, x + r, y + h - 1, cornerColor);
            extractor.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, cornerColor);
        }
    }

    public static void drawRoundedOutline(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0) {
            extractor.outline(x, y, w, h, color);
            return;
        }
        int r = Math.min(radius, Math.min(w / 2, h / 2));
        extractor.fill(x + r, y, x + w - r, y + 1, color);
        extractor.fill(x + r, y + h - 1, x + w - r, y + h, color);
        extractor.fill(x, y + r, x + 1, y + h - r, color);
        extractor.fill(x + w - 1, y + r, x + w, y + h - r, color);
        extractor.fill(x + 1, y + 1, x + r, y + 2, color);
        extractor.fill(x + 1, y + 2, x + 2, y + r, color);
        extractor.fill(x + w - r, y + 1, x + w - 1, y + 2, color);
        extractor.fill(x + w - 2, y + 2, x + w - 1, y + r, color);
        extractor.fill(x + 1, y + h - 2, x + r, y + h - 1, color);
        extractor.fill(x + 1, y + h - r, x + 2, y + h - 2, color);
        extractor.fill(x + w - r, y + h - 2, x + w - 1, y + h - 1, color);
        extractor.fill(x + w - 2, y + h - r, x + w - 1, y + h - 2, color);
    }

    public static int lerpColor(int c1, int c2, float t) {
        if (t <= 0.0f) return c1;
        if (t >= 1.0f) return c2;
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int)(a1 + (a2 - a1) * t);
        int r = (int)(r1 + (r2 - r1) * t);
        int g = (int)(g1 + (g2 - g1) * t);
        int b = (int)(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // -------------------------------------------------------------
    // SOFTWARE BUTTON WIDGET (Obsidian Minimal Soft Rounded Style)
    // -------------------------------------------------------------
    public static class SoftwareButton extends Button {
        private boolean isPrimary = false;
        private boolean isDanger = false;
        private boolean isTabActive = false;
        private boolean isSidebar = false;
        private boolean isSuccess = false;
        private boolean isSocket = false;
        private long lastPressTime = 0;
        private float hoverAnim = 0.0f;

        public SoftwareButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        public SoftwareButton setPrimary(boolean primary) {
            this.isPrimary = primary;
            return this;
        }

        public SoftwareButton setDanger(boolean danger) {
            this.isDanger = danger;
            return this;
        }

        public SoftwareButton setSuccess(boolean success) {
            this.isSuccess = success;
            return this;
        }

        public SoftwareButton setSocket(boolean socket) {
            this.isSocket = socket;
            return this;
        }

        public SoftwareButton setTabActive(boolean active) {
            this.isTabActive = active;
            return this;
        }

        public SoftwareButton setSidebar(boolean sidebar) {
            this.isSidebar = sidebar;
            return this;
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            this.lastPressTime = System.currentTimeMillis();
            super.onPress(input);
        }

        @Override
        public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
            boolean hovered = isHovered();
            float targetHover = hovered ? 1.0f : 0.0f;
            hoverAnim += (targetHover - hoverAnim) * 0.28f;

            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            long elapsed = System.currentTimeMillis() - lastPressTime;
            int pressOffset = (elapsed < 110) ? 1 : 0;
            y += pressOffset;

            if (isSidebar) {
                int hoverShiftX = (int) (hoverAnim * 3.0f);
                x += hoverShiftX;
                w -= hoverShiftX;

                int restingBg = isTabActive ? 0xFF181D2C : 0x00000000;
                int hoveredBg = isTabActive ? 0xFF1E2436 : 0xFF131724;
                int bg = lerpColor(restingBg, hoveredBg, hoverAnim);

                int restingBorder = isTabActive ? 0xFF3B82F6 : 0x00000000;
                int hoveredBorder = isTabActive ? 0xFF60A5FA : 0xFF1E2334;
                int border = lerpColor(restingBorder, hoveredBorder, hoverAnim);

                if ((bg & 0xFF000000) != 0) {
                    drawRoundedRect(extractor, x, y, w, h, 6, bg);
                }
                if ((border & 0xFF000000) != 0) {
                    drawRoundedOutline(extractor, x, y, w, h, 6, border);
                }

                int textColor = isTabActive ? 0xFFFFFFFF : lerpColor(0xFF94A3B8, 0xFFE2E8F0, hoverAnim);
                extractor.text(Minecraft.getInstance().font, getMessage().getString(), x + 10, y + (h - 8) / 2, textColor, false);

                if (elapsed < 240) {
                    float progress = elapsed / 240f;
                    float rippleEase = 1.0f - (float) Math.pow(1.0f - progress, 2);
                    int rippleAlpha = (int)((1.0f - progress) * 120);
                    int rw = (int)(w * rippleEase);
                    int rh = (int)(h * rippleEase);
                    int rx = x + (w - rw) / 2;
                    int ry = y + (h - rh) / 2;
                    drawRoundedRect(extractor, rx, ry, rw, rh, 5, (rippleAlpha << 24) | 0x38BDF8);
                }
                return;
            }

            if (isSocket) {
                if (hovered) {
                    drawRoundedRect(extractor, x, y, w, h, 3, 0xFF28141F);
                    drawRoundedOutline(extractor, x, y, w, h, 3, 0xFFEF4444);
                }

                String text = getMessage().getString();
                int maxNameW = hovered ? (w - 18) : (w - 4);
                int textY = y + (h - 8) / 2;
                extractor.text(Minecraft.getInstance().font, trimToWidth(Minecraft.getInstance().font, text, maxNameW), x + 4, textY, hovered ? 0xFFFCA5A5 : 0xFFCBD5E1, false);
                if (hovered) {
                    extractor.text(Minecraft.getInstance().font, "\u00A7c\u2715", x + w - 10, textY, 0xFFFF4444, false);
                }

                if (elapsed < 220) {
                    float progress = elapsed / 220f;
                    int rippleAlpha = (int)((1.0f - progress) * 85);
                    drawRoundedRect(extractor, x + 1, y + 1, w - 2, h - 2, 3, (rippleAlpha << 24) | 0xEF4444);
                }
                return;
            }

            int restingBg, hoveredBg, restingBorder, hoveredBorder, restingText, hoveredText;
            if (!this.active) {
                restingBg = hoveredBg = 0xFF10121C;
                restingBorder = hoveredBorder = 0xFF1A1D2C;
                restingText = hoveredText = 0xFF4B5563;
            } else if (isDanger) {
                restingBg = 0xFF991B1B; hoveredBg = 0xFFDC2626;
                restingBorder = 0xFFB91C1C; hoveredBorder = 0xFFEF4444;
                restingText = hoveredText = 0xFFFFFFFF;
            } else if (isSuccess) {
                restingBg = 0xFF0B291B; hoveredBg = 0xFF0D3322;
                restingBorder = 0xFF10B981; hoveredBorder = 0xFF34D399;
                restingText = hoveredText = 0xFF86EFAC;
            } else if (isPrimary) {
                restingBg = 0xFF1D4ED8; hoveredBg = 0xFF2563EB;
                restingBorder = 0xFF3B82F6; hoveredBorder = 0xFF60A5FA;
                restingText = hoveredText = 0xFFFFFFFF;
            } else if (isTabActive) {
                restingBg = 0xFF1E2638; hoveredBg = 0xFF253046;
                restingBorder = 0xFF3B82F6; hoveredBorder = 0xFF60A5FA;
                restingText = hoveredText = 0xFFFFFFFF;
            } else {
                restingBg = 0xFF141724; hoveredBg = 0xFF1C2234;
                restingBorder = 0xFF1E2334; hoveredBorder = 0xFF3B82F6;
                restingText = 0xFFCBD5E1; hoveredText = 0xFFFFFFFF;
            }

            int bg = lerpColor(restingBg, hoveredBg, hoverAnim);
            int border = lerpColor(restingBorder, hoveredBorder, hoverAnim);
            int textColor = lerpColor(restingText, hoveredText, hoverAnim);

            drawRoundedRect(extractor, x, y, w, h, 5, bg);
            drawRoundedOutline(extractor, x, y, w, h, 5, border);

            if (isTabActive && !isPrimary && !isDanger) {
                extractor.fill(x + 4, y + h - 2, x + w - 4, y + h, 0xFF3B82F6);
            }

            if (elapsed < 240) {
                float progress = elapsed / 240f;
                float rippleEase = 1.0f - (float) Math.pow(1.0f - progress, 2);
                int rippleAlpha = (int)((1.0f - progress) * 120);
                int rw = (int)(w * rippleEase);
                int rh = (int)(h * rippleEase);
                int rx = x + (w - rw) / 2;
                int ry = y + (h - rh) / 2;
                drawRoundedRect(extractor, rx, ry, rw, rh, 4, (rippleAlpha << 24) | 0x38BDF8);
            }

            String label = getMessage().getString();
            extractor.centeredText(Minecraft.getInstance().font, label, x + w / 2, y + (h - 8) / 2, textColor);
        }
    }

    public static String trimToWidth(net.minecraft.client.gui.Font font, String str, int maxW) {
        if (str == null || font == null || font.width(str) <= maxW) return str;
        String ell = "...";
        int ellW = font.width(ell);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (font.width(sb.toString() + ch) + ellW > maxW) {
                return sb.toString() + ell;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------
    // ENCHANTMENT CARD WIDGET (Software 2-Line Card with Diamond ◆)
    // -------------------------------------------------------------
    public static class EnchantmentCardWidget extends Button {
        public final EnchantmentEntry entry;
        public final boolean isInspected;
        public final boolean isSelected;
        public final boolean hasConflict;
        public final String conflictName;
        public final String displayName;
        public final ItemStack iconStack;
        public boolean hasInlineSearchButton = false;
        private long lastPressTime = 0;
        private float hoverAnim = 0.0f;

        public EnchantmentCardWidget(int x, int y, int width, int height, EnchantmentEntry entry,
                                     boolean isInspected, boolean isSelected, boolean hasConflict, String conflictName,
                                     String displayName, ItemStack iconStack, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.entry = entry;
            this.isInspected = isInspected;
            this.isSelected = isSelected;
            this.hasConflict = hasConflict;
            this.conflictName = conflictName;
            this.displayName = displayName;
            this.iconStack = iconStack;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            if (hasInlineSearchButton && mouseX >= this.getX() + this.getWidth() - 76) {
                return false;
            }
            return super.isMouseOver(mouseX, mouseY);
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            this.lastPressTime = System.currentTimeMillis();
            super.onPress(input);
        }

        @Override
        public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
            boolean hovered = isHovered();
            float targetHover = hovered ? 1.0f : 0.0f;
            hoverAnim += (targetHover - hoverAnim) * 0.28f;

            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            // Scorrimento tattile orizzontale di 4 pixel su hover
            int hoverShiftX = (int) (hoverAnim * 4.0f);
            x += hoverShiftX;
            w -= hoverShiftX;

            long elapsed = System.currentTimeMillis() - lastPressTime;
            int pressOffset = (elapsed < 110) ? 1 : 0;
            y += pressOffset;

            int restingBg, hoveredBg, restingBorder, hoveredBorder;
            if (isInspected) {
                // Scheda visualizzata nell'ispettore: Blu Obsidian Stondato sempre attivo
                restingBg = 0xFF0E1A2C;
                hoveredBg = 0xFF14243A;
                restingBorder = hoveredBorder = 0xFF3B82F6; // Cornice blu Obsidian
            } else if (hasInlineSearchButton) {
                // In Catalogo: stile Obsidian uniforme senza conflitti cromatici
                restingBg = 0xFF131724;
                hoveredBg = 0xFF1C2234;
                restingBorder = 0xFF1E2334;
                hoveredBorder = 0xFF3B82F6;
            } else if (isSelected) {
                // Attivo nel set (durante creazione set): Stile smeraldo pulito
                restingBg = 0xFF091C13;
                hoveredBg = 0xFF0E251A;
                restingBorder = 0xFF10B981;
                hoveredBorder = 0xFF34D399;
            } else if (hasConflict) {
                // Incompatibile con il set: Stile muted sobrio (non rosso sangue allarmante)
                restingBg = 0xFF121017;
                hoveredBg = 0xFF19141D;
                restingBorder = 0xFF261922;
                hoveredBorder = 0xFF581C28;
            } else {
                restingBg = 0xFF131724;
                hoveredBg = 0xFF1C2234;
                restingBorder = 0xFF1E2334;
                hoveredBorder = 0xFF3B82F6;
            }

            int bg = lerpColor(restingBg, hoveredBg, hoverAnim);
            int border = lerpColor(restingBorder, hoveredBorder, hoverAnim);

            drawRoundedRect(extractor, x, y, w, h, 6, bg);
            drawRoundedOutline(extractor, x, y, w, h, 6, border);

            // Tacca neon luminosa a sinistra della card attiva/hovered
            if (hoverAnim > 0.05f) {
                int indH = (int) (hoverAnim * (h - 8));
                int indY = y + (h - indH) / 2;
                int indAlpha = (int) (hoverAnim * 255.0f);
                int indColor = isSelected ? 0x34D399 : 0x38BDF8;
                extractor.fill(x + 2, indY, x + 4, indY + indH, (indAlpha << 24) | indColor);
            }

            // Ripple fluidissimo dinamico al click
            if (elapsed < 240) {
                float progress = elapsed / 240f;
                float rippleEase = 1.0f - (float) Math.pow(1.0f - progress, 2);
                int rippleAlpha = (int)((1.0f - progress) * 120);
                int rippleColor = (hasInlineSearchButton || !isSelected) ? 0x38BDF8 : 0x10B981;
                int rw = (int)(w * rippleEase);
                int rh = (int)(h * rippleEase);
                int rx = x + (w - rw) / 2;
                int ry = y + (h - rh) / 2;
                drawRoundedRect(extractor, rx, ry, rw, rh, 5, (rippleAlpha << 24) | rippleColor);
            }

            // Icona item/libro
            if (iconStack != null && !iconStack.isEmpty()) {
                extractor.fakeItem(iconStack, x + 5, y + (h - 16) / 2);
            }

            int textX = (iconStack != null && !iconStack.isEmpty()) ? x + 25 : x + 7;
            int badgeSpace = hasInlineSearchButton ? 78 : (isSelected ? 24 : 14);
            int maxTextW = w - (textX - x) - badgeSpace - 4;

            // RIGA 1: NOME DELL'INCANTESIMO
            int nameColor;
            if (isInspected) {
                nameColor = 0xFFFFFFFF; // Bianco puro
            } else if (hasInlineSearchButton) {
                nameColor = hovered ? 0xFFFFFFFF : 0xFFCBD5E1;
            } else if (isSelected) {
                nameColor = 0xFFFFFFFF; // Bianco pulito e leggibile
            } else if (hasConflict) {
                nameColor = hovered ? 0xFFE2E8F0 : 0xFF94A3B8; // Muted sobrio
            } else {
                nameColor = hovered ? 0xFFFFFFFF : 0xFFCBD5E1;
            }

            String line1 = displayName;
            extractor.text(Minecraft.getInstance().font, trimToWidth(Minecraft.getInstance().font, line1, maxTextW), textX, y + 4, nameColor, false);

            // RIGA 2: SOTTOTESTO DEDICATO (CONFLITTO O INFO)
            if (h >= 24) {
                if (isSelected && !hasInlineSearchButton) {
                    int cost = entry != null ? entry.getAnvilCostNumeric() : 0;
                    String costUnit = LitnessI18n.isEnglish() ? " lv." : " liv.";
                    String costStr = cost > 0 ? (" \u2022 +" + cost + costUnit) : "";
                    String inSetLabel = LitnessI18n.isEnglish() ? "In Set" : "Nel Set";
                    String info = "\u00A7a\u2713 " + inSetLabel + " \u2022 \u00A77" + ("custom".equalsIgnoreCase(entry.category) ? "Custom" : "Vanilla") + costStr;
                    extractor.text(Minecraft.getInstance().font, trimToWidth(Minecraft.getInstance().font, info, maxTextW), textX, y + 16, 0xFF86EFAC, false);
                } else if (hasConflict && conflictName != null && !conflictName.isEmpty()) {
                    String prefix = LitnessI18n.isEnglish() ? "\u00A78\u21B3 Incompatible with: \u00A7c" : "\u00A78\u21B3 Incompatibile con: \u00A7c";
                    String confText = prefix + conflictName;
                    extractor.text(Minecraft.getInstance().font, trimToWidth(Minecraft.getInstance().font, confText, maxTextW), textX, y + 16, 0xFFFCA5A5, false);
                } else if (entry != null) {
                    int cost = entry.getAnvilCostNumeric();
                    String costUnit = LitnessI18n.isEnglish() ? " lv." : " liv.";
                    String costStr = cost > 0 ? (" \u2022 +" + cost + costUnit) : "";
                    String inSearchLabel = LitnessI18n.isEnglish() ? "In Search" : "In Cerca";
                    String prefix = hasInlineSearchButton ? (isSelected ? ("\u00A7a" + inSearchLabel + " \u2022 \u00A77") : "\u00A78") : "\u00A78";
                    String info = prefix + ("custom".equalsIgnoreCase(entry.category) ? "Custom" : "Vanilla") + costStr;
                    extractor.text(Minecraft.getInstance().font, trimToWidth(Minecraft.getInstance().font, info, maxTextW), textX, y + 16, isSelected ? 0xFF86EFAC : 0xFF6B7280, false);
                }
            }

            // In Catalogo c'e' il pulsante dedicato a destra; nelle altre schermate disegniamo il badge
            if (!hasInlineSearchButton) {
                int badgeY = y + (h - 16) / 2;
                if (isSelected) {
                    int badgeX = x + w - 22;
                    drawRoundedRect(extractor, badgeX, badgeY, 16, 16, 4, 0x4010B981);
                    drawRoundedOutline(extractor, badgeX, badgeY, 16, 16, 4, 0xFF34D399);
                    extractor.centeredText(Minecraft.getInstance().font, "\u00A7a\u2713", badgeX + 8, badgeY + 4, 0xFF34D399);
                } else if (hasConflict) {
                    extractor.text(Minecraft.getInstance().font, "\u00A78\u292B", x + w - 14, y + (h - 8) / 2, 0xFF64748B, false);
                } else {
                    extractor.text(Minecraft.getInstance().font, "\u00A77+", x + w - 14, y + (h - 8) / 2, 0xFF94A3B8, false);
                }
            }
        }
    }

    @Override
    protected void clearWidgets() {
        super.clearWidgets();
        creatingSetEnchantButtons.clear();
        creatingSetLibraryButtons.clear();
        creatingSetButtonDataList.clear();
        catalogRowButtons.clear();
        savedSetButtons.clear();
    }

    @Override
    protected void init() {
        super.init();

        int winW = getWinW();
        int winH = getWinH();
        int winX = getWinX();
        int winY = getWinY();

        // 1. TASTO LINGUA NELL'HEADER (in alto a destra, accanto a Chiudi)
        boolean isEng = LitnessI18n.isEnglish();
        String langLabel = isEng ? "   [ EN ]" : "   [ IT ]";
        this.btnLangToggle = new SoftwareButton(winX + winW - 98, winY + 6, 68, 20, Component.literal(langLabel), b -> {
            LitnessI18n.toggleLanguage();
            this.clearWidgets();
            this.init();
        });
        this.addRenderableWidget(this.btnLangToggle);

        // 2. TASTO CHIUDI (✕ in alto a destra, stile finestra software)
        this.btnClose = new SoftwareButton(winX + winW - 26, winY + 6, 20, 20, Component.literal("\u2715"), b -> this.onClose())
                .setDanger(true);
        this.addRenderableWidget(this.btnClose);

        // 3. BARRA LATERALE SINISTRA (1: Catalogo -> 2: I Miei Set -> 3: Guida Incudine)
        int tabY = winY + HEADER_H + 6;
        int tabH = 26;

        SoftwareButton tabCatalog = new SoftwareButton(winX + 6, tabY, SIDEBAR_W - 12, tabH,
                Component.literal("📖 " + LitnessI18n.t("tab_catalog")), b -> {
            switchTab(Tab.CATALOG);
        }).setSidebar(true).setTabActive(this.currentTab == Tab.CATALOG);
        this.addRenderableWidget(tabCatalog);

        SoftwareButton tabSets = new SoftwareButton(winX + 6, tabY + tabH + 4, SIDEBAR_W - 12, tabH,
                Component.literal("🛡 " + LitnessI18n.t("tab_sets")), b -> {
            switchTab(Tab.SETS);
        }).setSidebar(true).setTabActive(this.currentTab == Tab.SETS);
        this.addRenderableWidget(tabSets);

        SoftwareButton tabCalc = new SoftwareButton(winX + 6, tabY + (tabH * 2) + 8, SIDEBAR_W - 12, tabH,
                Component.literal("⚡ " + LitnessI18n.t("tab_calculator")), b -> {
            switchTab(Tab.CALCULATOR);
        }).setSidebar(true).setTabActive(this.currentTab == Tab.CALCULATOR);
        this.addRenderableWidget(tabCalc);

        // 4. AREA CONTENUTO PRINCIPALE
        int contentX = winX + SIDEBAR_W + 8;
        int contentY = winY + HEADER_H + 6;
        int contentW = winW - SIDEBAR_W - 16;
        int contentH = winH - HEADER_H - 12;

        if (this.currentTab == Tab.CATALOG) {
            initCatalogWidgets(contentX, contentY, contentW, contentH);
        } else if (this.currentTab == Tab.SETS) {
            initSavedSetsViewWidgets(contentX, contentY, contentW, contentH);
        } else {
            initGuideViewWidgets(contentX, contentY, contentW, contentH);
        }
    }

    // -------------------------------------------------------------
    // CATALOGO WIDGETS
    // -------------------------------------------------------------
    private void initCatalogWidgets(int startX, int startY, int panelWidth, int panelHeight) {
        int filterY = startY;

        // FILTRI CATEGORIA
        int catX = startX;
        String[] cats = {"ALL", "VANILLA", "CUSTOM"};
        String[] catLabels = {
            LitnessI18n.t("all_filter"),
            LitnessI18n.t("vanilla_filter"),
            LitnessI18n.t("custom_filter")
        };
        int[] catWidths = {44, 60, 56};

        for (int i = 0; i < cats.length; i++) {
            final String c = cats[i];
            SoftwareButton btn = new SoftwareButton(catX, filterY, catWidths[i], 20, Component.literal(catLabels[i]), b -> {
                categoryFilter = c;
                catalogPage = 0;
                updateSearch();
                refreshCatalogButtons(startX, filterY + 50, panelWidth);
                rebuildWidgets();
            }).setTabActive(c.equalsIgnoreCase(categoryFilter));
            this.addRenderableWidget(btn);
            catX += catWidths[i] + 4;
        }

        // CAMPO RICERCA
        String oldText = this.searchField != null ? this.searchField.getValue() : "";
        int searchW = Math.max(140, panelWidth - (catX - startX) - 105);
        this.searchField = new EditBox(this.font, catX + 4, filterY, searchW, 20, Component.literal(""));
        this.searchField.setValue(oldText);
        this.searchField.setHint(Component.literal(LitnessI18n.t("search_hint")));
        this.searchField.setResponder(text -> {
            catalogPage = 0;
            updateSearch();
            refreshCatalogButtons(startX, filterY + 50, panelWidth);
        });
        this.addRenderableWidget(this.searchField);

        // TASTO AZZERA RICERCA /AH
        int ahCount = ActiveSessionManager.getSearchEnchants().size();
        String ahBtnLabel = ahCount > 0 ? "\u00A7c" + LitnessI18n.t("btn_clear") + " (" + ahCount + ")" : "\u00A78" + LitnessI18n.t("btn_clear");
        this.btnClearAhSearch = new SoftwareButton(startX + panelWidth - 95, filterY, 95, 20, Component.literal(ahBtnLabel), b -> {
            ActiveSessionManager.clearSearchEnchants();
            refreshCatalogButtons(startX, filterY + 50, panelWidth);
            updateClearButtonState();
            statusMessage = "\u00A7a" + LitnessI18n.t("searches_cleared");
        });
        this.btnClearAhSearch.active = (ahCount > 0);
        this.addRenderableWidget(this.btnClearAhSearch);

        // RIGA 2: FILTRI PEZZO / OGGETTO
        int itemFilterY = filterY + 24;
        int curItemX = startX;

        SoftwareButton btnAllItems = new SoftwareButton(curItemX, itemFilterY, 44, 20, Component.literal(LitnessI18n.t("all_filter")), b -> {
            itemFilter = "ALL";
            catalogPage = 0;
            updateSearch();
            rebuildWidgets();
        }).setTabActive("ALL".equalsIgnoreCase(itemFilter));
        this.addRenderableWidget(btnAllItems);
        curItemX += 48;

        for (int i = 0; i < 4; i++) {
            final int catIdx = i;
            String catName = LitnessI18n.getItemCategoryName(catIdx);
            int catBtnW = Math.max(50, this.font.width(catName) + 16);
            SoftwareButton bCat = new SoftwareButton(curItemX, itemFilterY, catBtnW, 20, Component.literal(catName), b -> {
                itemFilter = "cat_" + catIdx;
                catalogPage = 0;
                updateSearch();
                rebuildWidgets();
            }).setTabActive(("cat_" + catIdx).equalsIgnoreCase(itemFilter));
            this.addRenderableWidget(bCat);
            curItemX += catBtnW + 4;
        }

        updateSearch();
        refreshCatalogButtons(startX, itemFilterY + 26, panelWidth);
    }

    private void refreshCatalogButtons(int cardX, int startY, int cardWidth) {
        for (Button b : catalogRowButtons) {
            this.removeWidget(b);
        }
        catalogRowButtons.clear();

        int listW = (int)(cardWidth * 0.58) - 4;
        int inspX = cardX + listW + 8;
        int inspW = cardWidth - listW - 8;

        int pageSize = CATALOG_PAGE_SIZE;
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredEnchantments.size() / pageSize));
        if (catalogPage >= totalPages) catalogPage = Math.max(0, totalPages - 1);

        int startIdx = catalogPage * pageSize;
        int endIdx = Math.min(filteredEnchantments.size(), startIdx + pageSize);

        int rowH = 30;

        for (int i = startIdx; i < endIdx; i++) {
            EnchantmentEntry ee = filteredEnchantments.get(i);
            int slot = i - startIdx;
            int bx = cardX;
            int by = startY + (slot * (rowH + 3));

            boolean inSearch = ActiveSessionManager.isSearchEnchant(ee.id);
            boolean isInspected = (selectedCatalogEnchant != null && selectedCatalogEnchant.id.equalsIgnoreCase(ee.id));
            String displayName = AnvilOptimizer.formatEnchantName(ee, ee.maxLevel);
            ItemStack icon = EnchantmentThematicIconHelper.getIcon(ee);

            // 1. Scheda Incantesimo (seleziona per visualizzare info a destra)
            EnchantmentCardWidget card = new EnchantmentCardWidget(bx, by, listW, rowH, ee, isInspected, inSearch, false, null, displayName, icon, b -> {
                selectedCatalogEnchant = ee;
                refreshCatalogButtons(cardX, startY, cardWidth);
            });
            card.hasInlineSearchButton = true;
            this.addRenderableWidget(card);
            catalogRowButtons.add(card);

            // 2. Pulsante Cerca inline sul lato destro della riga (come nella versione 1.2.8)
            int btnW = 68;
            int btnH = 20;
            int btnX = bx + listW - btnW - 5;
            int btnY = by + (rowH - btnH) / 2;

            String btnLabel = inSearch ? "\u2713 In Cerca" : "+ Cerca";
            SoftwareButton searchBtn = new SoftwareButton(btnX, btnY, btnW, btnH, Component.literal(btnLabel), b -> {
                ActiveSessionManager.toggleSearchEnchant(ee.id);
                selectedCatalogEnchant = ee;
                updateClearButtonState();
                refreshCatalogButtons(cardX, startY, cardWidth);
            });
            if (inSearch) {
                searchBtn.setSuccess(true);
                searchBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("\u00A7cRimuovi dalla ricerca in /ah e casse")
                ));
            } else {
                searchBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("\u00A7aCerca ed evidenzia in /ah e casse")
                ));
            }
            this.addRenderableWidget(searchBtn);
            catalogRowButtons.add(searchBtn);
        }

        if (selectedCatalogEnchant == null && !filteredEnchantments.isEmpty()) {
            selectedCatalogEnchant = filteredEnchantments.get(0);
        }

        // Tasti Navigazione Pagina in basso per la lista sinistra
        int navY = getWinY() + getWinH() - 24;
        int btnW = 50;
        SoftwareButton prev = new SoftwareButton(cardX, navY, btnW, 20, Component.literal("\u25C0 Prec"), b -> {
            if (catalogPage > 0) {
                catalogPage--;
                refreshCatalogButtons(cardX, startY, cardWidth);
            }
        });
        prev.active = (catalogPage > 0);
        this.addRenderableWidget(prev);
        catalogRowButtons.add(prev);

        SoftwareButton next = new SoftwareButton(cardX + listW - btnW, navY, btnW, 20, Component.literal("Succ \u25B6"), b -> {
            if (catalogPage < totalPages - 1) {
                catalogPage++;
                refreshCatalogButtons(cardX, startY, cardWidth);
            }
        });
        next.active = (catalogPage < totalPages - 1);
        this.addRenderableWidget(next);
        catalogRowButtons.add(next);
    }

    private void updateClearButtonState() {
        if (this.btnClearAhSearch == null) return;
        int count = ActiveSessionManager.getSearchEnchants().size();
        this.btnClearAhSearch.active = (count > 0);
        this.btnClearAhSearch.setMessage(Component.literal(count > 0 ? "\u00A7c" + LitnessI18n.t("btn_clear") + " (" + count + ")" : "\u00A78" + LitnessI18n.t("btn_clear")));
    }

    // -------------------------------------------------------------
    // SET SALVATI & CREAZIONE SET WIDGETS
    // -------------------------------------------------------------
    private void initSavedSetsViewWidgets(int startX, int startY, int panelWidth, int panelHeight) {
        if (isCreatingSet) {
            initSetCreatorWidgets(startX, startY, panelWidth, panelHeight);
        } else {
            initSavedSetsListWidgets(startX, startY, panelWidth, panelHeight);
        }
    }

    private void initSavedSetsListWidgets(int startX, int startY, int panelWidth, int panelHeight) {
        // Tasto + Nuovo Set (Primario Viola)
        SoftwareButton btnNew = new SoftwareButton(startX, startY, 95, 22, Component.literal(LitnessI18n.t("btn_new_set")), b -> {
            openSetEditor(null);
            this.clearWidgets();
            this.init();
        }).setPrimary(true);
        this.addRenderableWidget(btnNew);

        // Filtri categorie set
        int fx = startX + 102;
        String[] cats = {"ALL", "0", "1", "2", "3"};
        String[] catLabels = {
            LitnessI18n.t("all_filter"),
            LitnessI18n.getItemCategoryName(0),
            LitnessI18n.getItemCategoryName(1),
            LitnessI18n.getItemCategoryName(2),
            LitnessI18n.getItemCategoryName(3)
        };
        for (int i = 0; i < cats.length; i++) {
            final String c = cats[i];
            int bw = Math.max(46, this.font.width(catLabels[i]) + 14);
            SoftwareButton bCat = new SoftwareButton(fx, startY + 1, bw, 20, Component.literal(catLabels[i]), b -> {
                savedSetsFilterCategory = c;
                savedSetsPage = 0;
                this.clearWidgets();
                this.init();
            }).setTabActive(c.equalsIgnoreCase(savedSetsFilterCategory));
            this.addRenderableWidget(bCat);
            fx += bw + 4;
        }

        // Tasto Incolla Set (vicino a tools e categorie a destra)
        SoftwareButton btnPaste = new SoftwareButton(fx + 6, startY + 1, 88, 20, Component.literal(LitnessI18n.t("paste_set_btn")), b -> {
            pasteSetFromClipboard();
        });
        this.addRenderableWidget(btnPaste);

        refreshSavedSetButtons(startY + 28, startX, panelWidth);
    }

    private void pasteSetFromClipboard() {
        Minecraft mc = Minecraft.getInstance();
        String clipboard = mc.keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.trim().isEmpty()) {
            showCenterNotification("✖ APPUNTI VUOTI", "Copia prima un set da importare!", false);
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal("§c• Gli appunti sono vuoti!"));
            }
            return;
        }
        CustomSet imported = SavedSetsManager.importSetFromString(clipboard);
        if (imported != null) {
            showCenterNotification("✔ SET INCOLLATO CON SUCCESSO!", imported.name + " (" + imported.enchantments.size() + " incantesimi)", true);
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal("§a✔ Set '" + imported.name + "' importato con successo!"));
            }
            this.clearWidgets();
            this.init();
        } else {
            showCenterNotification("✖ FORMATO NON VALIDO", "La stringa negli appunti non è un set valido", false);
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal("§c• Formato set non valido negli appunti!"));
            }
        }
    }

    private List<CustomSet> getFilteredSavedSets() {
        List<CustomSet> list = new ArrayList<>();
        for (CustomSet set : SavedSetsManager.getSavedSets()) {
            if (!"ALL".equalsIgnoreCase(savedSetsFilterCategory)) {
                try {
                    int targetCat = Integer.parseInt(savedSetsFilterCategory);
                    int itemIdx = 0;
                    for (int k = 0; k < ITEM_KEYS.length; k++) {
                        if (ITEM_KEYS[k].equalsIgnoreCase(set.itemKey)) {
                            itemIdx = k;
                            break;
                        }
                    }
                    if (getCategoryForItem(itemIdx) != targetCat) continue;
                } catch (Exception ignored) {}
            }
            list.add(set);
        }
        return list;
    }

    private void refreshSavedSetButtons(int startY, int startX, int panelWidth) {
        for (Button b : savedSetButtons) {
            this.removeWidget(b);
        }
        savedSetButtons.clear();

        List<CustomSet> sets = getFilteredSavedSets();
        if (sets.isEmpty()) {
            savedSetsPage = 0;
            return;
        }

        int availableH = (getWinY() + getWinH() - 32) - startY;
        int setsPerPage = Math.max(1, availableH / 28);
        int totalPages = Math.max(1, (int) Math.ceil((double) sets.size() / setsPerPage));
        if (savedSetsPage >= totalPages) savedSetsPage = totalPages - 1;
        if (savedSetsPage < 0) savedSetsPage = 0;

        int startIdx = savedSetsPage * setsPerPage;
        int endIdx = Math.min(sets.size(), startIdx + setsPerPage);

        for (int i = startIdx; i < endIdx; i++) {
            CustomSet set = sets.get(i);
            int rowIdx = i - startIdx;
            int by = startY + (rowIdx * 28);

            // Tasto Carica / Guida
            SoftwareButton btnGuide = new SoftwareButton(startX + panelWidth - 238, by + 2, 46, 20,
                    Component.literal(LitnessI18n.t("guide_btn")), b -> loadSavedSetIntoSimulator(set)).setPrimary(true);
            this.addRenderableWidget(btnGuide);
            savedSetButtons.add(btnGuide);

            // Tasto Copia (prima di Edit / Modifica)
            SoftwareButton btnCopy = new SoftwareButton(startX + panelWidth - 190, by + 2, 44, 20,
                    Component.literal(LitnessI18n.t("copy_set_btn")), b -> {
                String code = SavedSetsManager.exportSetToString(set);
                if (code != null) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(code);
                    showCenterNotification("✔ SET COPIATO NEGLI APPUNTI!", set.name + " (" + set.enchantments.size() + " incantesimi)", true);
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendOverlayMessage(Component.literal("§a✔ Set '" + set.name + "' copiato negli appunti!"));
                    }
                }
            });
            this.addRenderableWidget(btnCopy);
            savedSetButtons.add(btnCopy);

            // Tasto Modifica
            SoftwareButton btnEdit = new SoftwareButton(startX + panelWidth - 144, by + 2, 48, 20,
                    Component.literal(LitnessI18n.t("edit_btn")), b -> {
                openSetEditor(set);
                this.clearWidgets();
                this.init();
            });
            this.addRenderableWidget(btnEdit);
            savedSetButtons.add(btnEdit);

            // Tasto Pin
            boolean isPinned = ActiveSessionManager.isSetPinned(set.id);
            SoftwareButton btnPin = new SoftwareButton(startX + panelWidth - 94, by + 2, 44, 20,
                    Component.literal(isPinned ? LitnessI18n.t("unpin_btn") : LitnessI18n.t("pin_btn")), b -> {
                ActiveSessionManager.togglePinSet(set.id);
                this.clearWidgets();
                this.init();
            }).setTabActive(isPinned);
            this.addRenderableWidget(btnPin);
            savedSetButtons.add(btnPin);

            // Tasto Elimina (Rosso)
            SoftwareButton btnDel = new SoftwareButton(startX + panelWidth - 48, by + 2, 44, 20,
                    Component.literal(LitnessI18n.t("delete_btn")), b -> {
                SavedSetsManager.deleteSet(set.id);
                this.clearWidgets();
                this.init();
            }).setDanger(true);
            this.addRenderableWidget(btnDel);
            savedSetButtons.add(btnDel);
        }

        // Tasti di navigazione pagina se ci sono piu' pagine di set
        if (totalPages > 1) {
            int navY = getWinY() + getWinH() - 24;
            SoftwareButton prev = new SoftwareButton(startX + panelWidth / 2 - 60, navY, 26, 18, Component.literal("\u25C0"), b -> {
                if (savedSetsPage > 0) {
                    savedSetsPage--;
                    this.clearWidgets();
                    this.init();
                }
            });
            SoftwareButton next = new SoftwareButton(startX + panelWidth / 2 + 34, navY, 26, 18, Component.literal("\u25B6"), b -> {
                if (savedSetsPage < totalPages - 1) {
                    savedSetsPage++;
                    this.clearWidgets();
                    this.init();
                }
            });
            this.addRenderableWidget(prev);
            this.addRenderableWidget(next);
            savedSetButtons.add(prev);
            savedSetButtons.add(next);
        }
    }
    private void initSetCreatorWidgets(int startX, int startY, int panelWidth, int panelHeight) {
        int studioW = Math.max(132, (int)(panelWidth * 0.32));
        int libX = startX + studioW + 8;
        int libW = panelWidth - studioW - 8;

        int botY = getWinY() + getWinH() - 24;
        int selCount = creatingSetEnchants.size();

        // --- STUDIO SINISTRO: TOOLTIP CARD DELL'ITEM ENCHANTATO ---
        // 1. Campo Nome Set
        int iconBoxX = startX + 4;
        int nameFieldX = iconBoxX + 26;
        int nameFieldW = studioW - 32;
        this.creatingSetNameField = new EditBox(this.font, nameFieldX, startY + 2, nameFieldW, 15, Component.literal(""));
        this.creatingSetNameField.setValue(currentCreatingSetName);
        this.creatingSetNameField.setHint(Component.literal(LitnessI18n.t("set_name_hint")));
        this.creatingSetNameField.setResponder(text -> currentCreatingSetName = text);
        this.addRenderableWidget(this.creatingSetNameField);

        // 2. 12 Righe Lore Incantesimi (Stile tooltip Minecraft in colonna singola)
        int availLoreH = (botY - (startY + 34)) - 24;
        int loreRowH = Math.min(11, Math.max(9, availLoreH / 12));
        int loreStartY = startY + 42;

        List<String> list = new ArrayList<>(creatingSetEnchants);
        for (int i = 0; i < creatingSetEnchants.size(); i++) {
            final int slot = i;
            String enchId = list.get(slot);
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(enchId);
            String rawName = (ee != null) ? LitnessI18n.getEnchantName(ee) : enchId;
            String name = AnvilOptimizer.formatEnchantName(rawName, (ee != null) ? ee.maxLevel : 1);
            boolean isCustom = (ee != null && "custom".equalsIgnoreCase(ee.category));
            String bullet = isCustom ? "\u00A7b\u2022 " : "\u00A77\u2022 ";
            String tag = bullet + name;
            int sy = loreStartY + (slot * loreRowH);

            SoftwareButton sockBtn = new SoftwareButton(startX + 4, sy, studioW - 8, loreRowH, Component.literal(tag), b -> {
                creatingSetEnchants.remove(enchId);
                this.clearWidgets();
                this.init();
            }).setSocket(true);
            boolean isEn = LitnessI18n.isEnglish();
            String removeTooltip = isEn ? ("\u00A7cRemove " + name + " from set") : ("\u00A7cRimuovi " + name + " dal set");
            sockBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal(removeTooltip)
            ));
            this.addRenderableWidget(sockBtn);
            creatingSetEnchantButtons.add(sockBtn);
        }

        // 3. Bottone Simulatore / Status Incudine
        int btnSimY = loreStartY + (12 * loreRowH) + 3;
        btnSimY = Math.min(btnSimY, botY - 21);
        int btnSimH = Math.min(18, botY - btnSimY - 3);

        boolean isEn = LitnessI18n.isEnglish();
        String simLabel = (selCount > 0) ? (isEn ? "\u00A7a\u2713 Maxable \u00A78\u2022 \u00A7bSimulate \u2794" : "\u00A7a\u2713 Maxabile \u00A78\u2022 \u00A7bSimula \u2794") : (isEn ? "\u00A78No enchantments" : "\u00A78Nessun incanto");
        SoftwareButton btnSim = new SoftwareButton(startX + 4, btnSimY, studioW - 8, btnSimH, Component.literal(simLabel), b -> {
            simulatorSelectedEnchants.clear();
            simulatorSelectedEnchants.addAll(creatingSetEnchants);
            selectedItemIndex = creatingSetItemIndex;
            calcMode = CalcMode.SIMULATOR;
            runCalculation();
            switchTab(Tab.CALCULATOR);
        }).setPrimary(selCount > 0);
        btnSim.active = (selCount > 0);
        btnSim.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal(isEn ? "Calculate optimal combination tree on anvil" : "Calcola i passi ottimali all'incudine")
        ));
        this.addRenderableWidget(btnSim);
        creatingSetEnchantButtons.add(btnSim);

        // 4. Tasti Azione Bottom Studio: Salva e Annulla
        int btnCancelW = 46;
        int btnSaveW = (studioW - 8) - btnCancelW - 4;

        String saveText = LitnessI18n.t("btn_save") + " (" + selCount + "/12)";
        SoftwareButton btnSave = new SoftwareButton(startX + 4, botY, btnSaveW, 20, Component.literal(saveText), b -> {
            String name = (creatingSetNameField != null) ? creatingSetNameField.getValue().trim() : currentCreatingSetName.trim();
            if (name.isEmpty()) name = LitnessI18n.getItemPieceName(ITEM_KEYS[creatingSetItemIndex]) + " Custom";
            if (editingSetId != null) {
                SavedSetsManager.updateSet(editingSetId, name, ITEM_KEYS[creatingSetItemIndex], new ArrayList<>(creatingSetEnchants));
            } else {
                SavedSetsManager.addSet(name, ITEM_KEYS[creatingSetItemIndex], new ArrayList<>(creatingSetEnchants));
            }
            isCreatingSet = false;
            editingSetId = null;
            tabTransitionStartTime = System.currentTimeMillis();
            this.clearWidgets();
            this.init();
        }).setSuccess(true);
        btnSave.active = !creatingSetEnchants.isEmpty();
        this.addRenderableWidget(btnSave);
        creatingSetEnchantButtons.add(btnSave);

        SoftwareButton btnCancel = new SoftwareButton(startX + 4 + btnSaveW + 4, botY, btnCancelW, 20, Component.literal(LitnessI18n.t("btn_cancel")), b -> {
            isCreatingSet = false;
            editingSetId = null;
            tabTransitionStartTime = System.currentTimeMillis();
            this.clearWidgets();
            this.init();
        });
        this.addRenderableWidget(btnCancel);
        creatingSetEnchantButtons.add(btnCancel);

        // --- LIBRERIA DESTRA: FILTRI CATEGORIE, RICERCA E CARDS ---
        int row1Y = startY;
        int activeCategory = getCategoryForItem(creatingSetItemIndex);
        int curCatX = libX;
        for (int c = 0; c < 4; c++) {
            final int catIdx = c;
            String catName = LitnessI18n.getItemCategoryName(catIdx);
            int bw = this.font.width(catName) + 10;
            SoftwareButton bCat = new SoftwareButton(curCatX, row1Y, bw, 18, Component.literal(catName), b -> {
                creatingSetItemIndex = CATEGORY_ITEM_INDICES[catIdx][0];
                creatingSetEnchants.clear();
                creatingSetPage = 0;
                this.clearWidgets();
                this.init();
            }).setTabActive(activeCategory == catIdx);
            this.addRenderableWidget(bCat);
            creatingSetEnchantButtons.add(bCat);
            curCatX += bw + 3;
        }

        // Ricerca ben dimensionata sulla destra della riga 1 - MAI sovrapposta!
        int searchX = curCatX + 4;
        int searchW = Math.max(50, (libX + libW) - searchX);
        this.creatingSetSearchField = new EditBox(this.font, searchX, row1Y, searchW, 18, Component.literal(""));
        this.creatingSetSearchField.setValue(currentCreatingSetSearch);
        this.creatingSetSearchField.setHint(Component.literal(LitnessI18n.t("search_hint")));
        this.creatingSetSearchField.setResponder(text -> {
            currentCreatingSetSearch = text;
            creatingSetPage = 0;
            refreshCreatingSetEnchantButtons(startY + 42, libX, libW);
        });
        this.addRenderableWidget(this.creatingSetSearchField);

        // Riga 2: Pezzi della categoria attiva
        int row2Y = row1Y + 21;
        int curPieceX = libX;
        for (int itemIdx : CATEGORY_ITEM_INDICES[activeCategory]) {
            final int idx = itemIdx;
            String piece = LitnessI18n.getItemPieceName(ITEM_KEYS[idx]);
            int bw = Math.max(34, this.font.width(piece) + 8);
            SoftwareButton bPiece = new SoftwareButton(curPieceX, row2Y, bw, 17, Component.literal(piece), b -> {
                creatingSetItemIndex = idx;
                creatingSetEnchants.clear();
                creatingSetPage = 0;
                this.clearWidgets();
                this.init();
            }).setTabActive(creatingSetItemIndex == idx);
            this.addRenderableWidget(bPiece);
            creatingSetEnchantButtons.add(bPiece);
            curPieceX += bw + 3;
        }

        refreshCreatingSetEnchantButtons(row2Y + 21, libX, libW);
    }

    private void refreshCreatingSetEnchantButtons(int startY, int libX, int libW) {
        for (Button b : creatingSetLibraryButtons) {
            this.removeWidget(b);
        }
        creatingSetLibraryButtons.clear();
        creatingSetButtonDataList.clear();

        currentVisibleSelectionEnchants.clear();
        String currentKey = ITEM_KEYS[creatingSetItemIndex];
        ItemEntry currentItem = EnchantmentDatabase.getItem(currentKey);
        if (currentItem == null) currentItem = EnchantmentDatabase.getItem("sword");

        String q = (currentCreatingSetSearch != null) ? currentCreatingSetSearch.trim().toLowerCase() : "";
        for (EnchantmentEntry ee : EnchantmentDatabase.getAllEnchantments()) {
            if (currentItem != null && EnchantmentDatabase.isApplicable(ee, currentItem.id)) {
                String localized = LitnessI18n.getEnchantName(ee);
                if (!q.isEmpty()) {
                    boolean match = (ee.name != null && ee.name.toLowerCase().contains(q))
                            || (localized != null && localized.toLowerCase().contains(q))
                            || (ee.id != null && ee.id.toLowerCase().contains(q));
                    if (!match) continue;
                }
                currentVisibleSelectionEnchants.add(ee);
            }
        }

        int pageSize = CREATING_SET_PAGE_SIZE;
        int totalPages = Math.max(1, (int) Math.ceil((double) currentVisibleSelectionEnchants.size() / pageSize));
        if (creatingSetPage >= totalPages) creatingSetPage = Math.max(0, totalPages - 1);

        int startIdx = creatingSetPage * pageSize;
        int endIdx = Math.min(currentVisibleSelectionEnchants.size(), startIdx + pageSize);

        int cardW = libW;
        int rowHeight = 26;

        for (int i = startIdx; i < endIdx; i++) {
            EnchantmentEntry ee = currentVisibleSelectionEnchants.get(i);
            int slot = i - startIdx;
            int btnX = libX;
            int btnY = startY + (slot * (rowHeight + 3));

            creatingSetButtonDataList.add(new CreatingSetButtonData(btnX, btnY, cardW, rowHeight, ee));

            String eeBaseId = LitnessI18n.getBaseId(ee.id);
            boolean isSelected = false;
            for (String sId : creatingSetEnchants) {
                if (sId.equalsIgnoreCase(ee.id)) {
                    isSelected = true;
                    break;
                }
            }
            final boolean selected = isSelected;
            String conflict = selected ? null : getConflictWithCollection(ee, creatingSetEnchants);
            boolean hasConflict = (conflict != null);
            String baseName = AnvilOptimizer.formatEnchantName(ee, ee.maxLevel);
            ItemStack icon = EnchantmentThematicIconHelper.getIcon(ee);

            EnchantmentCardWidget card = new EnchantmentCardWidget(btnX, btnY, cardW, rowHeight, ee, false, selected, hasConflict, conflict, baseName, icon, b -> {
                if (creatingSetEnchants.contains(ee.id)) {
                    creatingSetEnchants.remove(ee.id);
                } else {
                    if (creatingSetEnchants.size() >= 12) {
                        return;
                    }
                    List<String> toRemove = new ArrayList<>();
                    for (String selId : creatingSetEnchants) {
                        EnchantmentEntry selEe = EnchantmentDatabase.getEnchantment(selId);
                        if (selEe == null) continue;
                        List<String> conflicts = EnchantmentDatabase.getResolvedConflicts(ee.id);
                        List<String> selConflicts = EnchantmentDatabase.getResolvedConflicts(selId);
                        if (conflicts.contains(selEe.name) || conflicts.contains(selEe.id) ||
                            selConflicts.contains(ee.name) || selConflicts.contains(ee.id)) {
                            toRemove.add(selId);
                        }
                    }
                    if (!toRemove.isEmpty()) {
                        creatingSetEnchants.removeAll(toRemove);
                    }
                    // Se si seleziona un livello diverso dello stesso incantesimo (es. Efficienza VI -> Efficienza V), sostituisce il livello esistente
                    creatingSetEnchants.removeIf(sId -> LitnessI18n.getBaseId(sId).equalsIgnoreCase(eeBaseId));
                    creatingSetEnchants.add(ee.id);
                }
                this.clearWidgets();
                this.init();
            });

            this.addRenderableWidget(card);
            creatingSetLibraryButtons.add(card);
        }

        // Tasti Pagina Libreria
        int navY = getWinY() + getWinH() - 24;
        SoftwareButton prev = new SoftwareButton(libX, navY, 52, 20, Component.literal("\u25C0 Prec"), b -> {
            if (creatingSetPage > 0) {
                creatingSetPage--;
                this.clearWidgets();
                this.init();
            }
        });
        prev.active = (creatingSetPage > 0);
        this.addRenderableWidget(prev);
        creatingSetLibraryButtons.add(prev);

        SoftwareButton next = new SoftwareButton(libX + libW - 52, navY, 52, 20, Component.literal("Succ \u25B6"), b -> {
            if (creatingSetPage < totalPages - 1) {
                creatingSetPage++;
                this.clearWidgets();
                this.init();
            }
        });
        next.active = (creatingSetPage < totalPages - 1);
        this.addRenderableWidget(next);
        creatingSetLibraryButtons.add(next);
    }

    // -------------------------------------------------------------
    // CALCOLATORE & GUIDA WIDGETS
    // -------------------------------------------------------------
    private void initGuideViewWidgets(int startX, int startY, int panelWidth, int panelHeight) {
        // Tasti Modalità (Simulatore vs Inventario)
        SoftwareButton btnSim = new SoftwareButton(startX, startY, 90, 20, Component.literal("Simulatore"), b -> {
            calcMode = CalcMode.SIMULATOR;
            runCalculation();
            this.clearWidgets();
            this.init();
        }).setTabActive(calcMode == CalcMode.SIMULATOR);
        this.addRenderableWidget(btnSim);

        SoftwareButton btnInv = new SoftwareButton(startX + 94, startY, 90, 20, Component.literal(LitnessI18n.t("in_inventory")), b -> {
            calcMode = CalcMode.INVENTORY;
            runCalculation();
            this.clearWidgets();
            this.init();
        }).setTabActive(calcMode == CalcMode.INVENTORY);
        this.addRenderableWidget(btnInv);

        // Selettore Pezzo
        String piece = LitnessI18n.getItemPieceName(ITEM_KEYS[selectedItemIndex]);
        SoftwareButton btnItem = new SoftwareButton(startX + 192, startY, 90, 20, Component.literal(piece), b -> {
            selectedItemIndex = (selectedItemIndex + 1) % ITEM_KEYS.length;
            simulatorSelectedEnchants.clear();
            runCalculation();
            this.clearWidgets();
            this.init();
        });
        this.addRenderableWidget(btnItem);

        // Selettore Penalità
        String penText = selectedPenaltyIndex == 0 ? LitnessI18n.t("penalty_zero") : LitnessI18n.t("penalty_plus") + PENALTY_STEPS[selectedPenaltyIndex] + LitnessI18n.t("levels");
        SoftwareButton btnPen = new SoftwareButton(startX + 286, startY, 96, 20, Component.literal(penText), b -> {
            selectedPenaltyIndex = (selectedPenaltyIndex + 1) % PENALTY_STEPS.length;
            runCalculation();
            this.clearWidgets();
            this.init();
        });
        this.addRenderableWidget(btnPen);

        // Tasto Copia Guida
        SoftwareButton btnCopy = new SoftwareButton(startX + panelWidth - 95, startY, 95, 20, Component.literal(LitnessI18n.t("copy_guide_btn")), b -> copyGuideToClipboard()).setPrimary(true);
        btnCopy.active = (currentSolution != null && !currentSolution.steps.isEmpty());
        this.addRenderableWidget(btnCopy);

        // Pulsanti paginazione / scorrimento passi forgia
        int navY = getWinY() + getWinH() - 21;
        btnCalcPrev = new SoftwareButton(startX + 6, navY, 65, 18, Component.literal("◀ Prec"), b -> {
            if (calculatorScrollOffset > 0) {
                calculatorScrollOffset = Math.max(0, calculatorScrollOffset - CALCULATOR_PAGE_SIZE);
            }
        });
        btnCalcPrev.active = (calculatorScrollOffset > 0);
        this.addRenderableWidget(btnCalcPrev);

        btnCalcNext = new SoftwareButton(startX + panelWidth - 71, navY, 65, 18, Component.literal("Succ ▶"), b -> {
            if (currentSolution != null) {
                int maxScroll = Math.max(0, currentSolution.steps.size() - CALCULATOR_PAGE_SIZE);
                if (calculatorScrollOffset < maxScroll) {
                    calculatorScrollOffset = Math.min(maxScroll, calculatorScrollOffset + CALCULATOR_PAGE_SIZE);
                }
            }
        });
        btnCalcNext.active = false;
        if (currentSolution != null) {
            int maxScroll = Math.max(0, currentSolution.steps.size() - CALCULATOR_PAGE_SIZE);
            btnCalcNext.active = (calculatorScrollOffset < maxScroll);
        }
        this.addRenderableWidget(btnCalcNext);

        String inputKey = calcMode + "|" + selectedItemIndex + "|" + selectedPenaltyIndex + "|" + String.join(",", simulatorSelectedEnchants);
        if (!inputKey.equals(lastCalculatedKey) && !isCalculating) {
            runCalculation();
        }
    }

    private void copyGuideToClipboard() {
        if (currentSolution == null || currentSolution.steps.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        String piece = LitnessI18n.getItemPieceName(ITEM_KEYS[selectedItemIndex]);
        sb.append("=== GUIDA INCUDINE LITNESS: ").append(piece.toUpperCase()).append(" ===\n");
        int stepNum = 1;
        for (MergeStep step : currentSolution.steps) {
            sb.append(LitnessI18n.t("step_prefix")).append(stepNum++).append(": ");
            if (step.isBookToBook) {
                sb.append("[Slot 1: ").append(step.leftLabel).append("] + [Slot 2: ").append(step.rightLabel).append("]");
            } else {
                sb.append("[").append(step.leftLabel).append("] + [Libro: ").append(step.rightLabel).append("]");
            }
            sb.append(" -> ").append(step.cost).append(" liv. XP\n");
        }
        sb.append(LitnessI18n.t("total_cost")).append(": ").append(currentSolution.totalCost).append(" liv. XP | ")
          .append(LitnessI18n.t("peak_cost")).append(": ").append(currentSolution.peakCost).append("/39 liv. XP");

        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
        statusMessage = "\u00A7a" + LitnessI18n.t("btn_copy") + " \u2713";
    }

    private String getConflictWithCollection(EnchantmentEntry ee, Collection<String> selectedIds) {
        if (ee == null || selectedIds == null || selectedIds.isEmpty()) return null;
        String eeBaseName = LitnessI18n.getBaseEnchantName(ee);
        String eeBaseId = LitnessI18n.getBaseId(ee.id);
        List<String> conflicts = EnchantmentDatabase.getResolvedConflicts(ee.id);

        for (String selId : selectedIds) {
            if (selId.equalsIgnoreCase(ee.id)) continue;
            EnchantmentEntry selEe = EnchantmentDatabase.getEnchantment(selId);
            if (selEe == null) continue;

            String selBaseName = LitnessI18n.getBaseEnchantName(selEe);
            String selBaseId = LitnessI18n.getBaseId(selEe.id);

            // Se e' lo stesso incantesimo o la stessa base, non e' un conflitto con un altro incantesimo
            if (selBaseId.equalsIgnoreCase(eeBaseId) || selBaseName.equalsIgnoreCase(eeBaseName)) {
                continue;
            }

            // Due incantesimi UNICI non possono mai coesistere sullo stesso pezzo (es. Dynamite & Xray)
            if (ee.unique && selEe.unique) {
                return selBaseName;
            }

            for (String c : conflicts) {
                if (c.equalsIgnoreCase(selBaseName) || c.equalsIgnoreCase(selEe.name) || c.equalsIgnoreCase(selBaseId) || c.equalsIgnoreCase(selId)) {
                    return selBaseName;
                }
            }

            List<String> selConflicts = EnchantmentDatabase.getResolvedConflicts(selId);
            for (String sc : selConflicts) {
                if (sc.equalsIgnoreCase(eeBaseName) || sc.equalsIgnoreCase(ee.name) || sc.equalsIgnoreCase(eeBaseId) || sc.equalsIgnoreCase(ee.id)) {
                    return selBaseName;
                }
            }
        }
        return null;
    }

    private void loadSavedSetIntoSimulator(SavedSetsManager.CustomSet set) {
        if (set == null) return;
        for (int i = 0; i < ITEM_KEYS.length; i++) {
            if (ITEM_KEYS[i].equalsIgnoreCase(set.itemKey)) {
                selectedItemIndex = i;
                break;
            }
        }
        simulatorSelectedEnchants.clear();
        simulatorSelectedEnchants.addAll(set.enchantments);
        calcMode = CalcMode.SIMULATOR;
        runCalculation();
        statusMessage = "\u00A7aCaricato set '" + set.name + "' (" + set.enchantments.size() + "/12)!";
        switchTab(Tab.CALCULATOR);
    }

    private void updateSearch() {
        filteredEnchantments.clear();
        String q = (this.searchField != null) ? this.searchField.getValue().trim().toLowerCase() : "";

        for (EnchantmentEntry ee : EnchantmentDatabase.getAllEnchantments()) {
            if (!"ALL".equalsIgnoreCase(categoryFilter)) {
                if ("VANILLA".equalsIgnoreCase(categoryFilter) && !"vanilla".equalsIgnoreCase(ee.category)) continue;
                if ("VANILLA_PLUS".equalsIgnoreCase(categoryFilter) && !"vanilla_plus".equalsIgnoreCase(ee.category)) continue;
                if ("CUSTOM".equalsIgnoreCase(categoryFilter) && !"custom".equalsIgnoreCase(ee.category)) continue;
            }

            if (!"ALL".equalsIgnoreCase(itemFilter)) {
                if (itemFilter.startsWith("cat_")) {
                    int catIdx = Integer.parseInt(itemFilter.substring(4));
                    boolean matchesCat = false;
                    for (int idx : CATEGORY_ITEM_INDICES[catIdx]) {
                        ItemEntry ie = EnchantmentDatabase.getItem(ITEM_KEYS[idx]);
                        if (ie != null && EnchantmentDatabase.isApplicable(ee, ie.id)) {
                            matchesCat = true;
                            break;
                        }
                    }
                    if (!matchesCat) continue;
                }
            }

            if (!q.isEmpty()) {
                String localized = LitnessI18n.getEnchantName(ee);
                boolean match = (ee.name != null && ee.name.toLowerCase().contains(q))
                        || (localized != null && localized.toLowerCase().contains(q))
                        || (ee.id != null && ee.id.toLowerCase().contains(q))
                        || (ee.description != null && ee.description.toLowerCase().contains(q));
                if (!match) continue;
            }

            filteredEnchantments.add(ee);
        }
    }

    private List<EnchantApplied> extractAllEnchantmentsFromItem(ItemStack stack) {
        List<EnchantApplied> list = new ArrayList<>();
        if (stack.isEmpty()) return list;

        Set<String> seen = new HashSet<>();

        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(rawId, lvl);
            String id = (vPlusId != null) ? vPlusId : rawId;
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(id);
            if (ee != null) id = ee.id;
            if (!id.isEmpty() && seen.add(id)) {
                list.add(new EnchantApplied(id, lvl));
            }
        }

        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(rawId, lvl);
            String id = (vPlusId != null) ? vPlusId : rawId;
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(id);
            if (ee != null) id = ee.id;
            if (!id.isEmpty() && seen.add(id)) {
                list.add(new EnchantApplied(id, lvl));
            }
        }

        return list;
    }

    private void runCalculation() {
        try {
            calculatorPage = 0;
            calculatorScrollOffset = 0;
            final String itemKey = ITEM_KEYS[selectedItemIndex];
            final ItemEntry item = EnchantmentDatabase.getItem(itemKey);
            final int basePenalty = PENALTY_STEPS[selectedPenaltyIndex];

            final List<BookInput> books = new ArrayList<>();
            if (calcMode == CalcMode.INVENTORY && this.minecraft != null && this.minecraft.player != null) {
                // Leggi libri reali nell'inventario
                int invSize = this.minecraft.player.getInventory().getContainerSize();
                for (int i = 0; i < invSize; i++) {
                    ItemStack stack = this.minecraft.player.getInventory().getItem(i);
                    if (stack.isEmpty() || ItemTypeHelper.isContainerOrShulker(stack)) continue;
                    if (stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK)) {
                        int repCost = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
                        int work = 0;
                        while ((1 << work) - 1 < repCost) work++;
                        BookInput b = new BookInput(work);
                        b.enchantments = extractAllEnchantmentsFromItem(stack);
                        if (!b.enchantments.isEmpty()) books.add(b);
                    }
                }
            } else {
                // Simulazione teorica dai selezionati
                if (simulatorSelectedEnchants.isEmpty()) {
                    for (SavedSetsManager.CustomSet cs : SavedSetsManager.getSavedSets()) {
                        if (cs != null && cs.itemKey.equalsIgnoreCase(itemKey) && !cs.enchantments.isEmpty()) {
                            simulatorSelectedEnchants.addAll(cs.enchantments);
                            break;
                        }
                    }
                }
                for (String enchId : simulatorSelectedEnchants) {
                    EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(enchId);
                    if (ee != null) {
                        BookInput b = new BookInput(0);
                        b.enchantments.add(new EnchantApplied(ee.id, ee.maxLevel > 1 ? ee.maxLevel : 1));
                        books.add(b);
                    }
                }
            }

            this.lastCalculatedKey = calcMode + "|" + selectedItemIndex + "|" + selectedPenaltyIndex + "|" + String.join(",", simulatorSelectedEnchants);

            if (books.isEmpty()) {
                this.currentSolution = null;
                this.isCalculating = false;
                this.needsWidgetRebuild = true;
                return;
            }

            isCalculating = true;
            CALC_POOL.submit(() -> {
                try {
                    Solution sol = AnvilOptimizer.optimize(books, basePenalty, item, Collections.emptyList());
                    this.currentSolution = sol;
                } catch (Throwable t) {
                    this.currentSolution = null;
                } finally {
                    this.isCalculating = false;
                    this.needsWidgetRebuild = true;
                }
            });
        } catch (Throwable t) {
            currentSolution = null;
            isCalculating = false;
        }
    }

    // -------------------------------------------------------------
    // RENDERING COMPLETO SCHERMATA SOFTWARE (Obsidian Minimal Style)
    // -------------------------------------------------------------
    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        // 1. Sfondo trasparente scuro
        extractor.fill(0, 0, this.width, this.height, 0xC507080E);

        int winW = getWinW();
        int winH = getWinH();
        int winX = getWinX();
        int winY = getWinY();

        // 2. Finestra Principale Software (Obsidian Minimal Stondata)
        drawRoundedRect(extractor, winX, winY, winW, winH, 10, 0xFF0F1118);
        drawRoundedOutline(extractor, winX, winY, winW, winH, 10, 0xFF1E2334);

        // 3. Header Software Bar Stondata
        drawRoundedRect(extractor, winX + 1, winY + 1, winW - 2, HEADER_H - 1, 9, 0xFF141722);
        extractor.fill(winX, winY + HEADER_H - 1, winX + winW, winY + HEADER_H, 0xFF1E2334);

        // Barra accento blu elettrico Obsidian |
        drawRoundedRect(extractor, winX + 10, winY + 9, 3, 14, 2, 0xFF3B82F6);
        extractor.text(this.font, "Litness Enchant", winX + 20, winY + 12, 0xFFFFFFFF, false);
        int brandW = this.font.width("Litness Enchant ");
        extractor.text(this.font, "v1.3 | By Waaxey", winX + 20 + brandW, winY + 12, 0xFF60A5FA, false);

        // Bandiera pixel nel pulsante lingua
        if (this.btnLangToggle != null) {
            LitnessI18n.renderFlag(extractor, this.btnLangToggle.getX() + 5, this.btnLangToggle.getY() + 5, LitnessI18n.isEnglish());
        }

        // 4. Linea divisoria Barra Laterale Sinistra
        extractor.fill(winX + SIDEBAR_W, winY + HEADER_H, winX + SIDEBAR_W + 1, winY + winH, 0xFF1A1D2E);

        // 4b. Pillola accento e aura neon fluida che scorre dolcemente tra le schede
        int tabY0 = winY + HEADER_H + 6;
        int tabH0 = 26;
        float targetPillY = tabY0;
        if (this.currentTab == Tab.SETS) {
            targetPillY = tabY0 + tabH0 + 4;
        } else if (this.currentTab == Tab.CALCULATOR) {
            targetPillY = tabY0 + (tabH0 * 2) + 8;
        }

        if (this.animatedPillY < 0) {
            this.animatedPillY = targetPillY;
        } else {
            this.animatedPillY += (targetPillY - this.animatedPillY) * 0.35f;
        }

        int pillY = (int) this.animatedPillY;
        // Aura / bagliore azzurro attorno al pulsante attivo
        drawRoundedRect(extractor, winX + 5, pillY - 1, SIDEBAR_W - 10, tabH0 + 2, 7, 0x223B82F6);
        drawRoundedOutline(extractor, winX + 5, pillY - 1, SIDEBAR_W - 10, tabH0 + 2, 7, 0x553B82F6);

        // Pillola accento blu elettrico con nucleo ciano brillante
        drawRoundedRect(extractor, winX + 7, pillY + 3, 4, tabH0 - 6, 2, 0xFF3B82F6);
        extractor.fill(winX + 8, pillY + 5, winX + 10, pillY + tabH0 - 8, 0xFF67E8F9);

        // Scintilla neon pulsante a destra del pulsante attivo
        long animTime = System.currentTimeMillis();
        double pulse = (Math.sin(animTime / 180.0) + 1.0) / 2.0;
        int pulseAlpha = (int) (120 + pulse * 135);
        extractor.fill(winX + SIDEBAR_W - 14, pillY + (tabH0 - 4) / 2, winX + SIDEBAR_W - 10, pillY + (tabH0 + 4) / 2, (pulseAlpha << 24) | 0x38BDF8);

        // 5. Area Contenuto Destra con Matrix Slide & Scale Pop fluidissima
        int contentX = winX + SIDEBAR_W + 8;
        int contentY = winY + HEADER_H + 6;
        int contentW = winW - SIDEBAR_W - 16;
        int contentH = winH - HEADER_H - 12;

        long tabElapsed = System.currentTimeMillis() - this.tabTransitionStartTime;
        float progress = Math.min(1.0f, (float) tabElapsed / 300.0f);
        float ease = 1.0f - (float) Math.pow(1.0f - progress, 3); // cubic ease-out
        float offsetY = -24.0f * (1.0f - ease);
        float scale = 0.96f + (0.04f * ease);

        float centerX = contentX + contentW / 2.0f;
        float centerY = contentY + contentH / 2.0f;

        extractor.pose().pushMatrix();
        extractor.pose().translate(centerX, centerY);
        extractor.pose().scale(scale, scale);
        extractor.pose().translate(-centerX, -centerY + offsetY);

        if (this.currentTab == Tab.CATALOG) {
            renderCatalogTab(extractor, contentX, contentY, contentW, contentH, mouseX, mouseY);
        } else if (this.currentTab == Tab.SETS) {
            renderSavedSetsTab(extractor, contentX, contentY, contentW, contentH, mouseX, mouseY);
        } else {
            renderCalculatorTab(extractor, contentX, contentY, contentW, contentH, mouseX, mouseY);
        }

        // Render widget standard (pulsanti e campi testo) trasformati insieme al contenuto
        super.extractRenderState(extractor, mouseX, mouseY, delta);

        extractor.pose().popMatrix();

        // 6. Laser Streak Neon azzurro ad alta velocità in cima alla scheda al cambio tab
        if (tabElapsed < 320) {
            int laserW = (int) (contentW * ease);
            extractor.fill(contentX, contentY - 3, contentX + laserW, contentY - 1, 0xFF3B82F6);
            extractor.fill(contentX, contentY - 4, contentX + laserW, contentY, 0x4038BDF8);
            int headX = contentX + laserW;
            extractor.fill(Math.max(contentX, headX - 8), contentY - 4, headX, contentY, 0xFF67E8F9);
            extractor.fill(Math.max(contentX, headX - 4), contentY - 5, headX + 2, contentY + 1, 0xFFFFFFFF);
        }

        // 8. Tooltip flottanti in primo piano
        renderFloatingTooltips(extractor, mouseX, mouseY);

        // 9. Notifica animata fluida a centro schermo
        renderAnimatedCenterToast(extractor);
    }

    private void renderCatalogTab(GuiGraphicsExtractor extractor, int startX, int startY, int panelWidth, int panelHeight, int mouseX, int mouseY) {
        int listW = (int)(panelWidth * 0.58) - 4;
        int inspX = startX + listW + 8;
        int inspW = panelWidth - listW - 8;
        int inspY = startY + 50;
        int inspH = panelHeight - 54;

        int pageSize = CATALOG_PAGE_SIZE;
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredEnchantments.size() / pageSize));
        int navY = getWinY() + getWinH() - 19;
        String pageStr = (catalogPage + 1) + " / " + totalPages + " (" + filteredEnchantments.size() + ")";
        extractor.centeredText(this.font, pageStr, startX + listW / 2, navY, 0xFF9CA3AF);

        if (selectedCatalogEnchant == null && !filteredEnchantments.isEmpty()) {
            selectedCatalogEnchant = filteredEnchantments.get(0);
        }

        if (selectedCatalogEnchant != null) {
            renderCatalogInspector(extractor, inspX, inspY, inspW, inspH, selectedCatalogEnchant, mouseX, mouseY);
        }
    }

    private void renderCatalogInspector(GuiGraphicsExtractor extractor, int x, int y, int w, int h, EnchantmentEntry ee, int mouseX, int mouseY) {
        // Pannello Dossier Obsidian Minimal Stondato
        drawRoundedRect(extractor, x, y, w, h, 8, 0xFF0B0D14);
        drawRoundedOutline(extractor, x, y, w, h, 8, 0xFF1E2334);

        // 1. Header con icona 3D, Nome, Categoria e Costo Anvil
        ItemStack iconStack = EnchantmentThematicIconHelper.getIcon(ee);
        drawRoundedRect(extractor, x + 8, y + 8, 26, 26, 6, 0xFF151926);
        drawRoundedOutline(extractor, x + 8, y + 8, 26, 26, 6, 0xFF3B82F6);
        if (iconStack != null && !iconStack.isEmpty()) {
            extractor.fakeItem(iconStack, x + 13, y + 13);
        }

        String localizedName = LitnessI18n.getEnchantName(ee);
        String title = AnvilOptimizer.formatEnchantName(localizedName, ee.maxLevel);
        extractor.text(this.font, trimToWidth(this.font, title, w - 46), x + 40, y + 9, 0xFFFFFFFF, false);

        String cat = "custom".equalsIgnoreCase(ee.category) ? "Custom Litness" : "Vanilla";
        int cost = ee.getAnvilCostNumeric();
        String costStr = cost > 0 ? (" \u2022 +" + cost + " liv. XP") : "";
        extractor.text(this.font, "\u00A78" + cat + costStr, x + 40, y + 22, 0xFF3B82F6, false);

        int curY = y + 38;

        // 2. Box "Cosa Fa (Effetto in Gioco)"
        extractor.text(this.font, "\u00A77COSA FA:", x + 8, curY, 0xFF9CA3AF, false);
        curY += 10;

        int descBoxH = 48;
        drawRoundedRect(extractor, x + 8, curY, w - 16, descBoxH, 6, 0xFF08090E);
        drawRoundedOutline(extractor, x + 8, curY, w - 16, descBoxH, 6, 0xFF1A1E2C);

        String desc = (ee.description != null && !ee.description.isEmpty()) ? ee.description : "Nessuna descrizione disponibile.";
        List<String> descLines = wrapTextLines(desc, Math.max(15, (w - 28) / 6));
        int dy = curY + 4;
        for (int i = 0; i < Math.min(descLines.size(), 4); i++) {
            extractor.text(this.font, "\u00A7f" + descLines.get(i), x + 12, dy, 0xFFE2E8F0, false);
            dy += 10;
        }
        curY += descBoxH + 8;

        // 3. Conflitti Incompatibili (deduplicati, senza livelli)
        List<String> conflicts = EnchantmentDatabase.getResolvedConflicts(ee.id);
        extractor.text(this.font, "\u00A7c\u2694 CONFLITTI INCOMPATIBILI:", x + 8, curY, 0xFFF87171, false);
        curY += 11;

        if (conflicts.isEmpty()) {
            extractor.text(this.font, "\u00A78Nessun conflitto con altri incantesimi.", x + 12, curY, 0xFF6B7280, false);
            curY += 14;
        } else {
            int chipX = x + 8;
            for (String conf : conflicts) {
                String cleanConf = LitnessI18n.stripLevelSuffix(conf);
                int cw = this.font.width(cleanConf) + 10;
                if (chipX + cw > x + w - 8) {
                    chipX = x + 8;
                    curY += 14;
                    if (curY > y + h - 55) break;
                }
                drawRoundedRect(extractor, chipX, curY, cw, 12, 4, 0xFF2A1218);
                drawRoundedOutline(extractor, chipX, curY, cw, 12, 4, 0xFF7F1D1D);
                extractor.text(this.font, "\u00A7c" + cleanConf, chipX + 5, curY + 2, 0xFFFCA5A5, false);
                chipX += cw + 4;
            }
            curY += 16;
        }

        // 4. Oggetti Compatibili (3D preview)
        List<ItemStack> compItems = LitnessTooltipHandler.getOrderedItemStacks(ee.applicableTo);
        if (!compItems.isEmpty() && curY <= y + h - 52) {
            extractor.text(this.font, "\u00A77OGGETTI COMPATIBILI:", x + 8, curY, 0xFF9CA3AF, false);
            curY += 11;
            int itemX = x + 8;
            for (int i = 0; i < Math.min(compItems.size(), 7); i++) {
                drawRoundedRect(extractor, itemX, curY, 18, 18, 4, 0xFF141724);
                drawRoundedOutline(extractor, itemX, curY, 18, 18, 4, 0xFF22283A);
                extractor.fakeItem(compItems.get(i), itemX + 1, curY + 1);
                itemX += 22;
            }
        }
    }

    private void renderSavedSetsTab(GuiGraphicsExtractor extractor, int startX, int startY, int panelWidth, int panelHeight, int mouseX, int mouseY) {
        if (isCreatingSet) {
            renderSetCreatorOverlay(extractor, startX, startY, panelWidth, panelHeight, mouseX, mouseY);
            return;
        }

        List<CustomSet> sets = getFilteredSavedSets();
        if (sets.isEmpty()) {
            extractor.centeredText(this.font, LitnessI18n.t("empty_sets"), startX + panelWidth / 2, startY + 60, 0xFF6B7280);
            return;
        }

        int availableH = (getWinY() + getWinH() - 32) - (startY + 28);
        int setsPerPage = Math.max(1, availableH / 28);
        int totalPages = Math.max(1, (int) Math.ceil((double) sets.size() / setsPerPage));
        if (savedSetsPage >= totalPages) savedSetsPage = totalPages - 1;
        if (savedSetsPage < 0) savedSetsPage = 0;

        int startIdx = savedSetsPage * setsPerPage;
        int endIdx = Math.min(sets.size(), startIdx + setsPerPage);

        for (int i = startIdx; i < endIdx; i++) {
            CustomSet set = sets.get(i);
            int rowIdx = i - startIdx;
            int curY = startY + 28 + (rowIdx * 28);

            // Card Set Salvato con scorrimento tattile e highlight su hover
            boolean isPinned = ActiveSessionManager.isSetPinned(set.id);
            boolean isRowHovered = (mouseX >= startX && mouseX <= startX + panelWidth && mouseY >= curY && mouseY <= curY + 24);
            int rowShiftX = isRowHovered ? 4 : 0;

            int cardBg = isPinned ? (isRowHovered ? 0xFF1C223D : 0xFF151A2E) : (isRowHovered ? 0xFF181C2C : 0xFF121422);
            int cardBorder = isPinned ? (isRowHovered ? 0xFF818CF8 : 0xFF6366F1) : (isRowHovered ? 0xFF3B82F6 : 0xFF222538);

            extractor.fill(startX + rowShiftX, curY, startX + panelWidth, curY + 24, cardBg);
            extractor.outline(startX + rowShiftX, curY, panelWidth - rowShiftX, 24, cardBorder);

            if (isRowHovered) {
                extractor.fill(startX + rowShiftX + 1, curY + 2, startX + rowShiftX + 3, curY + 22, 0xFF38BDF8);
            }

            ItemStack pieceStack = getItemStackForType(set.itemKey);
            extractor.fakeItem(pieceStack, startX + rowShiftX + 4, curY + 4);

            String pinBadge = isPinned ? "\u00A76[\u2605 PIN] \u00A7r" : "";
            String title = pinBadge + "\u00A7f" + set.name + " \u00A78(" + set.enchantments.size() + "/12)";
            extractor.text(this.font, title, startX + rowShiftX + 24, curY + 8, 0xFFFFFFFF, false);
        }

        if (totalPages > 1) {
            int navY = getWinY() + getWinH() - 20;
            String pageStr = (savedSetsPage + 1) + " / " + totalPages + " (" + sets.size() + " set)";
            extractor.centeredText(this.font, pageStr, startX + panelWidth / 2, navY, 0xFF9CA3AF);
        }
    }

    private void renderSetCreatorOverlay(GuiGraphicsExtractor extractor, int startX, int startY, int panelWidth, int panelHeight, int mouseX, int mouseY) {
        int studioW = Math.max(132, (int)(panelWidth * 0.32));
        int libX = startX + studioW + 8;
        int libW = panelWidth - studioW - 8;
        int botY = getWinY() + getWinH() - 24;

        // 1. Box Studio Sinistro (Tooltip Card Item Enchantato con aura ametista obsidian)
        drawRoundedRect(extractor, startX - 2, startY - 2, studioW + 4, panelHeight - 26, 8, 0xFF0B0D14);
        drawRoundedOutline(extractor, startX - 2, startY - 2, studioW + 4, panelHeight - 26, 8, 0xFF261D38);

        // Icona 3D pezzo in alto a sinistra dentro slot dedicato con bagliore
        ItemStack pieceStack = getItemStackForType(ITEM_KEYS[creatingSetItemIndex]);
        drawRoundedRect(extractor, startX + 4, startY + 2, 22, 22, 5, 0xFF141024);
        drawRoundedOutline(extractor, startX + 4, startY + 2, 22, 22, 5, 0xFF8B5CF6);
        extractor.fakeItem(pieceStack, startX + 7, startY + 5);

        // Sottotitolo pezzo
        String pieceName = LitnessI18n.getItemPieceName(ITEM_KEYS[creatingSetItemIndex]);
        int count = creatingSetEnchants.size();
        boolean isEn = LitnessI18n.isEnglish();
        String summary = "\u00A77" + LitnessI18n.t("piece_label") + "\u00A7b" + pieceName + " \u00A78\u2022 \u00A7d" + count + "/12";
        extractor.text(this.font, summary, startX + 30, startY + 19, 0xFF9CA3AF, false);

        // Divisorio sotto header pezzo
        drawRoundedRect(extractor, startX + 4, startY + 28, studioW - 8, 1, 0, 0xFF261D38);
        extractor.text(this.font, isEn ? "\u00A7d\u2726 Enchantments:" : "\u00A7d\u2726 Incantesimi:", startX + 6, startY + 31, 0xFFC084FC, false);

        // Slot liberi (i >= count) stile tooltip Minecraft
        int availLoreH = (botY - (startY + 34)) - 24;
        int loreRowH = Math.min(11, Math.max(9, availLoreH / 12));
        int loreStartY = startY + 42;
        for (int i = count; i < 12; i++) {
            int sy = loreStartY + (i * loreRowH);
            extractor.text(this.font, "\u00A78\u2022 [Slot " + (i + 1) + "]", startX + 8, sy + 1, 0xFF334155, false);
        }

        // Divisorio sotto lista incantesimi
        int postLoreDivY = loreStartY + (12 * loreRowH) + 2;
        drawRoundedRect(extractor, startX + 4, postLoreDivY, studioW - 8, 1, 0, 0xFF261D38);

        // 2. Box Libreria Destra
        int pageSize = CREATING_SET_PAGE_SIZE;
        int totalPages = Math.max(1, (int) Math.ceil((double) currentVisibleSelectionEnchants.size() / pageSize));
        int navY = getWinY() + getWinH() - 19;
        String pageStr = (creatingSetPage + 1) + " / " + totalPages + " (" + currentVisibleSelectionEnchants.size() + " incantesimi)";
        extractor.centeredText(this.font, pageStr, libX + libW / 2, navY, 0xFF9CA3AF);
    }

    private void renderCalculatorTab(GuiGraphicsExtractor extractor, int startX, int startY, int panelWidth, int panelHeight, int mouseX, int mouseY) {
        if (isCalculating) {
            drawRoundedRect(extractor, startX, startY + 50, panelWidth, 60, 8, 0xFF0B0D14);
            drawRoundedOutline(extractor, startX, startY + 50, panelWidth, 60, 8, 0xFF1E2334);
            extractor.centeredText(this.font, "✦ Calcolo combinazione ottimale in corso...", startX + panelWidth / 2, startY + 74, 0xFF60A5FA);
            return;
        }

        if (currentSolution == null || currentSolution.steps.isEmpty()) {
            extractor.centeredText(this.font, "Seleziona incantesimi per calcolare i passi dell'incudine.", startX + panelWidth / 2, startY + 60, 0xFF6B7280);
            return;
        }

        // 4 KPI Stats Cards Super Stondate in alto
        int kpiW = (panelWidth - 18) / 4;
        int kpiH = 34;
        int kpiY = startY + 24;

        // KPI 1: Costo Totale XP
        drawRoundedRect(extractor, startX, kpiY, kpiW, kpiH, 6, 0xFF0B0D14);
        drawRoundedOutline(extractor, startX, kpiY, kpiW, kpiH, 6, 0xFF1E2334);
        extractor.text(this.font, "\u00A78COSTO TOTALE XP", startX + 6, kpiY + 5, 0xFF9CA3AF, false);
        extractor.text(this.font, "\u00A7a" + currentSolution.totalCost + " Livelli", startX + 6, kpiY + 18, 0xFF34D399, false);

        // KPI 2: Picco Massimo
        int kpi2X = startX + kpiW + 6;
        drawRoundedRect(extractor, kpi2X, kpiY, kpiW, kpiH, 6, 0xFF0B0D14);
        drawRoundedOutline(extractor, kpi2X, kpiY, kpiW, kpiH, 6, 0xFF1E2334);
        extractor.text(this.font, "\u00A78PICCO MASSIMO", kpi2X + 6, kpiY + 5, 0xFF9CA3AF, false);
        extractor.text(this.font, "\u00A7b" + currentSolution.peakCost + "/39 Livelli", kpi2X + 6, kpiY + 18, 0xFF60A5FA, false);

        // KPI 3: Passi Forgia
        int kpi3X = kpi2X + kpiW + 6;
        drawRoundedRect(extractor, kpi3X, kpiY, kpiW, kpiH, 6, 0xFF0B0D14);
        drawRoundedOutline(extractor, kpi3X, kpiY, kpiW, kpiH, 6, 0xFF1E2334);
        extractor.text(this.font, "\u00A78PASSI FORGIA", kpi3X + 6, kpiY + 5, 0xFF9CA3AF, false);
        extractor.text(this.font, "\u00A7f" + currentSolution.steps.size() + " Passi", kpi3X + 6, kpiY + 18, 0xFFFFFFFF, false);

        // KPI 4: Maxabilità
        int kpi4X = kpi3X + kpiW + 6;
        drawRoundedRect(extractor, kpi4X, kpiY, kpiW, kpiH, 6, 0xFF0B0D14);
        drawRoundedOutline(extractor, kpi4X, kpiY, kpiW, kpiH, 6, 0xFF1E2334);
        extractor.text(this.font, "\u00A78MAXABILIT\u00C0", kpi4X + 6, kpiY + 5, 0xFF9CA3AF, false);
        String maxText = (currentSolution.peakCost <= 39) ? "\u00A7a100% Maxabile" : "\u00A7cTroppo Costoso";
        extractor.text(this.font, maxText, kpi4X + 6, kpiY + 18, 0xFF34D399, false);

        // Lista passi ottimizzati con icone 3D e card stondate paginati
        int stepY = kpiY + kpiH + 8;
        int rowH = 32;
        ItemStack pieceStack = getItemStackForType(ITEM_KEYS[selectedItemIndex]);

        int pageSize = CALCULATOR_PAGE_SIZE;
        int totalSteps = currentSolution.steps.size();
        int maxScroll = Math.max(0, totalSteps - pageSize);
        if (calculatorScrollOffset > maxScroll) calculatorScrollOffset = maxScroll;
        if (calculatorScrollOffset < 0) calculatorScrollOffset = 0;
        int startIdx = calculatorScrollOffset;
        int endIdx = Math.min(startIdx + pageSize, totalSteps);

        if (btnCalcPrev != null) btnCalcPrev.active = (calculatorScrollOffset > 0);
        if (btnCalcNext != null) btnCalcNext.active = (calculatorScrollOffset < maxScroll);

        for (int i = startIdx; i < endIdx; i++) {
            MergeStep step = currentSolution.steps.get(i);
            int stepNum = i + 1;
            int cardBg = step.isBookToBook ? 0xFF131724 : 0xFF101924;
            int cardBorder = step.isBookToBook ? 0xFF1E2334 : 0xFF1E3A4B;

            drawRoundedRect(extractor, startX, stepY, panelWidth, rowH, 6, cardBg);
            drawRoundedOutline(extractor, startX, stepY, panelWidth, rowH, 6, cardBorder);

            // Step badge a sinistra in pillola
            drawRoundedRect(extractor, startX + 5, stepY + 7, 36, 18, 4, 0xFF1A1F30);
            drawRoundedOutline(extractor, startX + 5, stepY + 7, 36, 18, 4, 0xFF3B82F6);
            String stepTag = "#" + stepNum;
            extractor.centeredText(this.font, stepTag, startX + 23, stepY + 11, 0xFF60A5FA);

            int curX = startX + 46;

            // Slot 1: Oggetto base o Libro
            ItemStack leftStack = step.isBookToBook ? new ItemStack(Items.ENCHANTED_BOOK) : pieceStack;
            drawRoundedRect(extractor, curX - 2, stepY + 6, 20, 20, 4, 0xFF151926);
            extractor.fakeItem(leftStack, curX, stepY + 8);
            curX += 24;

            // Segno +
            extractor.text(this.font, "\u00A77+", curX, stepY + 12, 0xFF9CA3AF, false);
            curX += 12;

            // Slot 2: Sempre Libro
            drawRoundedRect(extractor, curX - 2, stepY + 6, 20, 20, 4, 0xFF151926);
            extractor.fakeItem(new ItemStack(Items.ENCHANTED_BOOK), curX, stepY + 8);
            curX += 24;

            // Freccia ➔
            extractor.text(this.font, "\u00A76\u2794", curX, stepY + 12, 0xFFF59E0B, false);
            curX += 14;

            // Risultato
            ItemStack resStack = step.isBookToBook ? new ItemStack(Items.ENCHANTED_BOOK) : pieceStack;
            drawRoundedRect(extractor, curX - 2, stepY + 6, 20, 20, 4, 0xFF151926);
            extractor.fakeItem(resStack, curX, stepY + 8);
            curX += 24;

            // Dettaglio testuale
            int availTextW = panelWidth - (curX - startX) - 68;
            String textSlot1 = (step.isBookToBook ? "\u00A79" : "\u00A7e") + step.leftLabel;
            String textSlot2 = "\u00A7d" + step.rightLabel;
            extractor.text(this.font, trimToWidth(this.font, "1: " + textSlot1, availTextW), curX, stepY + 5, 0xFFFFFFFF, false);
            extractor.text(this.font, trimToWidth(this.font, "2: " + textSlot2, availTextW), curX, stepY + 17, 0xFFCBD5E1, false);

            // Costo XP sulla destra in pillola smeraldo
            drawRoundedRect(extractor, startX + panelWidth - 62, stepY + 8, 56, 16, 4, 0xFF0D281E);
            drawRoundedOutline(extractor, startX + panelWidth - 62, stepY + 8, 56, 16, 4, 0xFF10B981);
            String costTag = "\u00A7a+" + step.cost + " liv.";
            extractor.centeredText(this.font, costTag, startX + panelWidth - 34, stepY + 12, 0xFF34D399);

            stepY += rowH + 4;
            if (stepY + rowH > getWinY() + getWinH() - 24) break;
        }

        if (totalSteps > pageSize) {
            int navY = getWinY() + getWinH() - 17;
            String pageStr = "Passi " + (startIdx + 1) + "-" + endIdx + " di " + totalSteps + " \u00A78(Scorri con rotellina)";
            extractor.centeredText(this.font, pageStr, startX + panelWidth / 2, navY, 0xFF9CA3AF);
        }
    }

    // -------------------------------------------------------------
    // NOTIFICA ANIMATA FLUIDA A CENTRO SCHERMO (Copia/Incolla Set)
    // -------------------------------------------------------------
    private void renderAnimatedCenterToast(GuiGraphicsExtractor extractor) {
        if (toastTitle == null || toastStartTime <= 0) return;
        long now = System.currentTimeMillis();
        long elapsed = now - toastStartTime;
        long totalDuration = 2400; // 2.4 secondi

        if (elapsed >= totalDuration) {
            toastTitle = null;
            toastSubtitle = null;
            toastStartTime = 0;
            return;
        }

        float alpha;
        float offsetY;

        // Fase 1: Entrata fluidissima con cubic ease-out (0..300ms)
        if (elapsed < 300) {
            float progress = (float) elapsed / 300.0f;
            float ease = 1.0f - (float) Math.pow(1.0f - progress, 3);
            alpha = ease;
            offsetY = -18.0f * (1.0f - ease);
        }
        // Fase 2: Sosta di lettura nitida (300..1800ms)
        else if (elapsed < 1800) {
            alpha = 1.0f;
            offsetY = 0.0f;
        }
        // Fase 3: Dissolvenza morbida in uscita (1800..2400ms)
        else {
            float progress = (float) (elapsed - 1800) / 600.0f;
            float ease = progress * progress; // ease-in
            alpha = Math.max(0.0f, 1.0f - ease);
            offsetY = -10.0f * ease;
        }

        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0f)));
        if (a <= 4) return;

        int titleW = this.font.width(toastTitle);
        int subW = (toastSubtitle != null && !toastSubtitle.isEmpty()) ? this.font.width(toastSubtitle) : 0;
        int toastW = Math.max(260, Math.max(titleW, subW) + 54);
        int toastH = (toastSubtitle != null && !toastSubtitle.isEmpty()) ? 46 : 34;

        int toastX = (this.width - toastW) / 2;
        int toastY = (this.height - toastH) / 2 + (int) offsetY;

        // Ombra morbida
        int shadowAlpha = Math.max(0, Math.min(160, a / 2));
        drawRoundedRect(extractor, toastX + 2, toastY + 3, toastW, toastH, 8, (shadowAlpha << 24));

        // Sfondo Obsidian Dark Glass
        int bgAlpha = Math.max(0, Math.min(248, (int) (a * 0.96f)));
        int bgColor = (bgAlpha << 24) | 0x0A0D15;
        drawRoundedRect(extractor, toastX, toastY, toastW, toastH, 8, bgColor);

        // Bordo luminoso
        int borderColor = toastSuccess ? ((a << 24) | 0x10B981) : ((a << 24) | 0xEF4444);
        drawRoundedOutline(extractor, toastX, toastY, toastW, toastH, 8, borderColor);

        // Pillola accento verticale colorata a sinistra
        int pillColor = toastSuccess ? ((a << 24) | 0x10B981) : ((a << 24) | 0xEF4444);
        drawRoundedRect(extractor, toastX + 6, toastY + 6, 4, toastH - 12, 2, pillColor);

        // Icona distintiva (badge circolare / tondo a sinistra)
        int iconX = toastX + 16;
        int iconCenterY = toastY + (toastH / 2) - 4;
        String icon = toastSuccess ? "✔" : "✖";
        int iconColor = toastSuccess ? ((a << 24) | 0x34D399) : ((a << 24) | 0xF87171);
        extractor.text(this.font, icon, iconX, iconCenterY - 1, iconColor, true);

        // Testo Titolo e Sottotitolo
        int textX = toastX + 32;
        int titleColor = (a << 24) | 0xFFFFFF;
        if (toastSubtitle != null && !toastSubtitle.isEmpty()) {
            extractor.text(this.font, toastTitle, textX, toastY + 9, titleColor, true);
            int subColor = toastSuccess ? ((a << 24) | 0x6EE7B7) : ((a << 24) | 0xFCA5A5);
            extractor.text(this.font, toastSubtitle, textX, toastY + 23, subColor, false);
        } else {
            extractor.text(this.font, toastTitle, textX, toastY + (toastH / 2) - 4, titleColor, true);
        }
    }

    // -------------------------------------------------------------
    // TOOLTIPS FLOTTANTI IN PRIMO PIANO
    // -------------------------------------------------------------
    private void renderFloatingTooltips(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        // 1. Tooltip incantesimo hoverato nel Catalogo
        if (this.currentTab == Tab.CATALOG) {
            for (Button b : catalogRowButtons) {
                if (b instanceof EnchantmentCardWidget) {
                    EnchantmentCardWidget card = (EnchantmentCardWidget) b;
                    if (card.isMouseOver(mouseX, mouseY)) {
                        renderEnchantmentTooltip(extractor, card.entry, mouseX, mouseY);
                        return;
                    }
                }
            }
        }

        // 2. Tooltip incantesimo hoverato nella Creazione Set
        if (this.currentTab == Tab.SETS && isCreatingSet) {
            for (CreatingSetButtonData data : creatingSetButtonDataList) {
                if (mouseX >= data.x && mouseX < data.x + data.w && mouseY >= data.y && mouseY < data.y + data.h) {
                    renderEnchantmentTooltip(extractor, data.entry, mouseX, mouseY);
                    return;
                }
            }

            // 3. Tooltip anteprima pezzo ed incantesimi nella barra superiore
            int prevX = getWinX() + SIDEBAR_W + 8 + 138;
            int prevY = getWinY() + HEADER_H + 6 + 1;
            if (mouseX >= prevX && mouseX < prevX + 160 && mouseY >= prevY && mouseY < prevY + 24) {
                renderCornerPreviewExpandedTooltip(extractor, mouseX, mouseY);
            }
        }

        // 4. Tooltip passo incudine hoverato nella Guida Incudine
        if (this.currentTab == Tab.CALCULATOR && currentSolution != null && !currentSolution.steps.isEmpty()) {
            int startX = getWinX() + SIDEBAR_W + 8;
            int panelWidth = getWinW() - SIDEBAR_W - 16;
            int startY = getWinY() + HEADER_H + 6;
            int kpiY = startY + 24;
            int kpiH = 34;
            int stepY = kpiY + kpiH + 8;
            int rowH = 32;

            int totalSteps = currentSolution.steps.size();
            int maxScroll = Math.max(0, totalSteps - CALCULATOR_PAGE_SIZE);
            if (calculatorScrollOffset > maxScroll) calculatorScrollOffset = maxScroll;
            int startIdx = calculatorScrollOffset;
            int endIdx = Math.min(startIdx + CALCULATOR_PAGE_SIZE, totalSteps);

            for (int i = startIdx; i < endIdx; i++) {
                if (mouseX >= startX && mouseX <= startX + panelWidth && mouseY >= stepY && mouseY <= stepY + rowH) {
                    MergeStep step = currentSolution.steps.get(i);
                    renderStepCardTooltip(extractor, step, i + 1, mouseX, mouseY);
                    return;
                }
                stepY += rowH + 4;
            }
        }
    }

    private void renderStepCardTooltip(GuiGraphicsExtractor extractor, MergeStep step, int stepNum, int mouseX, int mouseY) {
        if (step == null) return;
        List<Component> lines = new ArrayList<>();

        String opType = step.isBookToBook ? (LitnessI18n.isEnglish() ? "Merge Books" : "Unisci Libri") : (LitnessI18n.isEnglish() ? "Apply to Item" : "Applica all'Oggetto");
        lines.add(Component.literal("\u00A7b#" + stepNum + " \u00A7e" + opType + " \u00A78\u2022 \u00A7a+" + step.cost + (LitnessI18n.isEnglish() ? " lv." : " liv.")));

        if (!step.rightEnchants.isEmpty()) {
            for (AnvilOptimizer.EnchantApplied ea : step.rightEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                String eName = (ee != null) ? AnvilOptimizer.formatEnchantName(ee, ea.level) : ea.id;
                boolean isCustom = ee != null && "custom".equalsIgnoreCase(ee.category);
                lines.add(Component.literal((isCustom ? "\u00A7d\u2022 " : "\u00A7a\u2022 ") + eName + (isCustom ? " \u00A78[Custom]" : " \u00A78[Vanilla]")));
                if (ee != null && ee.description != null && !ee.description.trim().isEmpty()) {
                    for (String dLine : wrapFormattedText(ee.description.trim(), 36, "\u00A77")) {
                        lines.add(Component.literal("  \u00A77" + dLine));
                    }
                }
            }
        }

        if (step.isBookToBook && !step.leftEnchants.isEmpty()) {
            lines.add(Component.literal("\u00A78" + (LitnessI18n.isEnglish() ? "Base (Slot 1):" : "Base (Slot 1):")));
            for (AnvilOptimizer.EnchantApplied ea : step.leftEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                String eName = (ee != null) ? AnvilOptimizer.formatEnchantName(ee, ea.level) : ea.id;
                lines.add(Component.literal(" \u00A79\u2022 " + eName));
            }
        }

        int maxW = 0;
        for (Component c : lines) maxW = Math.max(maxW, this.font.width(c));
        int tw = Math.max(130, maxW + 12);
        int rowH = 10;
        int th = lines.size() * rowH + 8;
        int tx = mouseX + 12;
        int ty = mouseY - 8;

        if (tx + tw > this.width - 4) tx = mouseX - tw - 8;
        if (ty + th > this.height - 4) ty = this.height - th - 4;
        if (tx < 4) tx = 4;
        if (ty < 4) ty = 4;

        drawRoundedRect(extractor, tx, ty, tw, th, 4, 0xF6090C14);
        drawRoundedOutline(extractor, tx, ty, tw, th, 4, 0xFF3B82F6);

        int cy = ty + 4;
        for (Component c : lines) {
            extractor.text(this.font, c, tx + 6, cy, 0xFFFFFFFF, false);
            cy += rowH;
        }
    }

    private void renderCornerPreviewExpandedTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        if (creatingSetEnchants.isEmpty()) return;

        List<Component> lines = new ArrayList<>();
        String pieceName = LitnessI18n.getItemPieceName(ITEM_KEYS[creatingSetItemIndex]);
        lines.add(Component.literal("\u00A7b\u2022 " + pieceName + " [" + currentCreatingSetName + "]"));
        lines.add(Component.literal("\u00A7e" + LitnessI18n.t("enchants_word").trim() + " (" + creatingSetEnchants.size() + "/12):"));

        for (String id : creatingSetEnchants) {
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(id);
            String name = (ee != null) ? AnvilOptimizer.formatEnchantName(ee, ee.maxLevel) : id;
            lines.add(Component.literal(" \u00A7a\u2022 " + name));
        }

        int maxW = 0;
        for (Component c : lines) maxW = Math.max(maxW, this.font.width(c));
        int tw = maxW + 16;
        int th = lines.size() * 11 + 8;
        int tx = mouseX - tw - 6;
        int ty = mouseY + 6;

        drawRoundedRect(extractor, tx, ty, tw, th, 4, 0xF60B0D14);
        drawRoundedOutline(extractor, tx, ty, tw, th, 4, 0xFF3B82F6);

        int cy = ty + 4;
        for (Component c : lines) {
            extractor.text(this.font, c, tx + 6, cy, 0xFFFFFFFF, false);
            cy += 11;
        }
    }

    private void renderEnchantmentTooltip(GuiGraphicsExtractor extractor, EnchantmentEntry e, int mouseX, int mouseY) {
        if (e == null) return;

        List<Component> lines = new ArrayList<>();
        String localizedName = LitnessI18n.getEnchantName(e);
        String title = AnvilOptimizer.formatEnchantName(localizedName, e.maxLevel);

        boolean isCustom = "custom".equalsIgnoreCase(e.category);
        String cat = isCustom ? "Custom" : "Vanilla";
        int cost = e.getAnvilCostNumeric();
        String costUnit = LitnessI18n.isEnglish() ? " lv." : " liv.";
        String costStr = cost > 0 ? (" \u00A78\u2022 \u00A7a+" + cost + costUnit) : "";

        // RIGA 1: Titolo incantesimo con categoria e costo XP
        String header = (isCustom ? "\u00A7b" : "\u00A7f\u00A7l") + title + "  \u00A78[" + cat + "]" + costStr;
        lines.add(Component.literal(header));

        // RIGA 2+: Lore / Descrizione (piccola, compatta e ben leggibile)
        if (e.description != null && !e.description.trim().isEmpty()) {
            for (String descLine : wrapFormattedText(e.description.trim(), 42, "\u00A77")) {
                lines.add(Component.literal(descLine.startsWith("\u00A7") ? descLine : ("\u00A77" + descLine)));
            }
        } else {
            lines.add(Component.literal("\u00A78" + (LitnessI18n.isEnglish() ? "No description available." : "Nessuna descrizione disponibile.")));
        }

        // RIGA Conflitti: se presenti, compatti e senza suffissi numerici
        List<String> conflicts = EnchantmentDatabase.getResolvedConflicts(e.id);
        if (!conflicts.isEmpty()) {
            List<String> cleanConflicts = new ArrayList<>();
            for (String c : conflicts) {
                cleanConflicts.add(LitnessI18n.stripLevelSuffix(c));
            }
            String confLabel = LitnessI18n.t("conflicts_label");
            String confStr = "\u00A7c" + confLabel + "\u00A7e" + String.join(", ", cleanConflicts);
            for (String cLine : wrapFormattedText(confStr, 42, "\u00A7e")) {
                lines.add(Component.literal(cLine.startsWith("\u00A7") ? cLine : ("\u00A7e" + cLine)));
            }
        }

        int maxW = 0;
        for (Component c : lines) maxW = Math.max(maxW, this.font.width(c));
        int tw = Math.max(130, maxW + 12);
        int rowH = 10;
        int th = lines.size() * rowH + 8;
        int tx = mouseX + 12;
        int ty = mouseY - 8;

        if (tx + tw > this.width - 4) tx = mouseX - tw - 8;
        if (ty + th > this.height - 4) ty = this.height - th - 4;
        if (tx < 4) tx = 4;
        if (ty < 4) ty = 4;

        // Finestra Obsidian Minimal compatta
        drawRoundedRect(extractor, tx, ty, tw, th, 4, 0xF6090C14);
        drawRoundedOutline(extractor, tx, ty, tw, th, 4, 0xFF2A3146);

        int cy = ty + 4;
        for (Component c : lines) {
            extractor.text(this.font, c, tx + 6, cy, 0xFFFFFFFF, false);
            cy += rowH;
        }
    }

    private List<String> wrapFormattedText(String text, int maxChars, String defaultColor) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        String currentColor = defaultColor;
        for (String w : words) {
            for (int i = 0; i < w.length() - 1; i++) {
                if (w.charAt(i) == '\u00A7') {
                    currentColor = "\u00A7" + w.charAt(i + 1);
                }
            }
            int visualLen = current.toString().replaceAll("(?i)\u00A7[0-9a-fk-orx]", "").length();
            int wordVisualLen = w.replaceAll("(?i)\u00A7[0-9a-fk-orx]", "").length();
            if (visualLen + wordVisualLen + 1 > maxChars && current.length() > 0) {
                result.add(current.toString());
                current = new StringBuilder(currentColor).append(w);
            } else {
                if (current.length() > 0) current.append(" ");
                current.append(w);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private List<String> wrapTextLines(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        if (text.length() <= maxChars) {
            result.add(text);
            return result;
        }
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            if (current.length() + w.length() + 1 > maxChars) {
                result.add(current.toString());
                current = new StringBuilder(w);
            } else {
                if (current.length() > 0) current.append(" ");
                current.append(w);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private ItemStack getItemStackForType(String type) {
        if (type == null) return new ItemStack(Items.DIAMOND_SWORD);
        switch (type.toLowerCase()) {
            case "sword": return new ItemStack(Items.DIAMOND_SWORD);
            case "axe": return new ItemStack(Items.DIAMOND_AXE);
            case "mace": return new ItemStack(Items.MACE);
            case "trident": return new ItemStack(Items.TRIDENT);
            case "bow": return new ItemStack(Items.BOW);
            case "xbow": return new ItemStack(Items.CROSSBOW);
            case "pick": return new ItemStack(Items.DIAMOND_PICKAXE);
            case "shovel": return new ItemStack(Items.DIAMOND_SHOVEL);
            case "hoe": return new ItemStack(Items.DIAMOND_HOE);
            case "rod": return new ItemStack(Items.FISHING_ROD);
            case "helmet": return new ItemStack(Items.DIAMOND_HELMET);
            case "chest": return new ItemStack(Items.DIAMOND_CHESTPLATE);
            case "legs": return new ItemStack(Items.DIAMOND_LEGGINGS);
            case "boots": return new ItemStack(Items.DIAMOND_BOOTS);
            case "elytra": return new ItemStack(Items.ELYTRA);
            default: return new ItemStack(Items.ENCHANTED_BOOK);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int winX = getWinX();
        int winY = getWinY();
        int winW = getWinW();
        int winH = getWinH();
        if (mouseX < winX || mouseX > winX + winW || mouseY < winY || mouseY > winY + winH) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (this.currentTab == Tab.CATALOG) {
            int pageSize = CATALOG_PAGE_SIZE;
            int totalPages = Math.max(1, (int) Math.ceil((double) filteredEnchantments.size() / pageSize));
            if (verticalAmount > 0 && catalogPage > 0) {
                catalogPage--;
                this.clearWidgets();
                this.init();
                return true;
            } else if (verticalAmount < 0 && catalogPage < totalPages - 1) {
                catalogPage++;
                this.clearWidgets();
                this.init();
                return true;
            }
        } else if (this.currentTab == Tab.SETS && !isCreatingSet) {
            List<CustomSet> sets = getFilteredSavedSets();
            int startY = getWinY() + HEADER_H + 4;
            int availableH = (getWinY() + getWinH() - 32) - (startY + 28);
            int setsPerPage = Math.max(1, availableH / 28);
            int totalPages = Math.max(1, (int) Math.ceil((double) sets.size() / setsPerPage));
            if (verticalAmount > 0 && savedSetsPage > 0) {
                savedSetsPage--;
                this.clearWidgets();
                this.init();
                return true;
            } else if (verticalAmount < 0 && savedSetsPage < totalPages - 1) {
                savedSetsPage++;
                this.clearWidgets();
                this.init();
                return true;
            }
        } else if (this.currentTab == Tab.SETS && isCreatingSet) {
            int pageSize = CREATING_SET_PAGE_SIZE;
            int totalPages = Math.max(1, (int) Math.ceil((double) currentVisibleSelectionEnchants.size() / pageSize));
            if (verticalAmount > 0 && creatingSetPage > 0) {
                creatingSetPage--;
                this.clearWidgets();
                this.init();
                return true;
            } else if (verticalAmount < 0 && creatingSetPage < totalPages - 1) {
                creatingSetPage++;
                this.clearWidgets();
                this.init();
                return true;
            }
        } else if (this.currentTab == Tab.CALCULATOR && currentSolution != null && currentSolution.steps.size() > CALCULATOR_PAGE_SIZE) {
            int maxScroll = Math.max(0, currentSolution.steps.size() - CALCULATOR_PAGE_SIZE);
            if (verticalAmount > 0 && calculatorScrollOffset > 0) {
                calculatorScrollOffset--;
                return true;
            } else if (verticalAmount < 0 && calculatorScrollOffset < maxScroll) {
                calculatorScrollOffset++;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void tick() {
        super.tick();
        if (needsWidgetRebuild) {
            needsWidgetRebuild = false;
            this.clearWidgets();
            this.init();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            if (this.parentScreen != null) {
                this.minecraft.setScreenAndShow(this.parentScreen);
            } else {
                super.onClose();
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

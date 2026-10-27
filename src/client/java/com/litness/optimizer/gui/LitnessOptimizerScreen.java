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
import com.litness.optimizer.data.SavedSetsManager;
import com.litness.optimizer.data.SavedSetsManager.CustomSet;
import com.litness.optimizer.data.UpdateChecker;
import com.litness.optimizer.tooltip.LitnessTooltipHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class LitnessOptimizerScreen extends Screen {
    public enum Tab {
        CATALOG,
        CALCULATOR
    }

    public enum CalcMode {
        SIMULATOR,
        INVENTORY
    }

    public enum SimView {
        GUIDE,
        SETS
    }

    private Tab currentTab = Tab.CATALOG;
    private CalcMode calcMode = CalcMode.SIMULATOR;
    private SimView simView = SimView.SETS;
    private final Screen parentScreen;
    private final AnvilMenu anvilMenu;

    // Catalogo
    private EditBox searchField;
    private String categoryFilter = "ALL";
    private String itemFilter = "ALL";
    private final List<EnchantmentEntry> filteredEnchantments = new ArrayList<>();
    private int getItemsPerPage() {
        int avail = this.height - 116;
        return Math.max(5, Math.min(12, avail / 19));
    }
    private int catalogPage = 0;
    private final List<Button> catalogRowButtons = new ArrayList<>();
    private Button btnClearAhSearch = null;

    // Stato calcolatore & simulatore: Spada, Piccone e Armature (Elmo, Corazza, Gambali, Stivali)
    private static final String[] ITEM_KEYS = {
        "sword", "pick", "helmet", "chest", "legs", "boots"
    };
    private static final String[] ITEM_NAMES = {
        "Spada", "Piccone", "Elmo", "Corazza", "Gambali", "Stivali"
    };
    private static final int[] PENALTY_STEPS = {0, 1, 3, 7, 15, 31};

    private int selectedItemIndex = 0;
    private int selectedPenaltyIndex = 0;

    // Creazione / Modifica Set Futuri
    private boolean isCreatingSet = false;
    private String editingSetId = null;
    private String currentCreatingSetName = "";
    private int creatingSetItemIndex = 0;
    private final Set<String> creatingSetEnchants = new LinkedHashSet<>();
    private EditBox creatingSetNameField;
    private int creatingSetPage = 0;
    private final List<Button> creatingSetEnchantButtons = new ArrayList<>();

    // Selezione simulatore (ID incantesimi attivi)
    private final Set<String> simulatorSelectedEnchants = new LinkedHashSet<>();

    public static class HoveredBookSlot {
        public final boolean isBook;
        public final String slotTitle;
        public final String label;
        public final int work;
        public final List<EnchantApplied> enchants;

        public HoveredBookSlot(boolean isBook, String slotTitle, String label, int work, List<EnchantApplied> enchants) {
            this.isBook = isBook;
            this.slotTitle = slotTitle;
            this.label = label;
            this.work = work;
            List<EnchantApplied> list = (enchants != null && !enchants.isEmpty()) 
                    ? new ArrayList<>(enchants) 
                    : parseEnchantmentsFromLabel(label);
            this.enchants = list;
        }
    }

    // Guida incudine e paginazione passi
    private int guideStepPage = 0;
    private Button btnPrevStep;
    private Button btnNextStep;
    private Button btnCopyGuide;
    private EnchantmentEntry hoveredCatalogEntry = null;
    private HoveredBookSlot hoveredGuideSlot = null;
    private EnchantmentEntry hoveredSelectionEntry = null;
    private final List<EnchantmentEntry> currentVisibleSelectionEnchants = new ArrayList<>();

    private Solution currentSolution = null;
    private String statusMessage = "";
    private final List<BookInput> currentScannedBooks = new ArrayList<>();

    // Set salvati
    private int savedSetsPage = 0;
    private EditBox newSetNameField;
    private final List<Button> savedSetButtons = new ArrayList<>();

    public LitnessOptimizerScreen(Component title) {
        this(title, null, null, null);
    }

    public LitnessOptimizerScreen(Component title, Screen parentScreen, AnvilMenu anvilMenu) {
        this(title, parentScreen, anvilMenu, null);
    }

    public LitnessOptimizerScreen(Component title, Screen parentScreen, AnvilMenu anvilMenu, Solution initialSolution) {
        super(title);
        this.parentScreen = parentScreen;
        this.anvilMenu = anvilMenu;
        if (initialSolution != null && !initialSolution.steps.isEmpty()) {
            this.currentSolution = initialSolution;
            this.currentTab = Tab.CALCULATOR;
            this.calcMode = CalcMode.INVENTORY;
            this.simView = SimView.GUIDE;
        } else if (anvilMenu != null) {
            this.currentTab = Tab.CALCULATOR;
            this.calcMode = CalcMode.INVENTORY;
            this.simView = SimView.GUIDE;
        } else {
            this.selectedItemIndex = ActiveSessionManager.getActiveItemIndex();
            this.creatingSetItemIndex = this.selectedItemIndex;
            this.simView = SimView.SETS;
        }

        if (anvilMenu != null) {
            ItemStack slot0 = anvilMenu.getSlot(0).getItem();
            if (!slot0.isEmpty()) {
                String descId = slot0.getItem().getDescriptionId().toLowerCase();
                String rawStr = slot0.getItem().toString().toLowerCase();
                if (descId.contains("sword") || rawStr.contains("sword")) {
                    this.selectedItemIndex = 0;
                } else if (descId.contains("pickaxe") || descId.contains("pick") || rawStr.contains("pickaxe") || rawStr.contains("pick")) {
                    this.selectedItemIndex = 1;
                } else if (descId.contains("helmet") || rawStr.contains("helmet")) {
                    this.selectedItemIndex = 2;
                } else if (descId.contains("chestplate") || descId.contains("chest") || rawStr.contains("chestplate") || rawStr.contains("chest")) {
                    this.selectedItemIndex = 3;
                } else if (descId.contains("leggings") || descId.contains("legs") || rawStr.contains("leggings") || rawStr.contains("legs")) {
                    this.selectedItemIndex = 4;
                } else if (descId.contains("boots") || rawStr.contains("boots")) {
                    this.selectedItemIndex = 5;
                }

                int penalty = slot0.getOrDefault(DataComponents.REPAIR_COST, 0);
                for (int p = 0; p < PENALTY_STEPS.length; p++) {
                    if (PENALTY_STEPS[p] == penalty) {
                        this.selectedPenaltyIndex = p;
                        break;
                    }
                }
            }
        }

        String itemKey = ITEM_KEYS[selectedItemIndex];
        Set<String> activeEnchants = ActiveSessionManager.getSelectedEnchants(itemKey);
        if (activeEnchants != null && !activeEnchants.isEmpty()) {
            this.simulatorSelectedEnchants.addAll(activeEnchants);
        }
    }

    @Override
    protected void init() {
        super.init();

        int topY = 7;
        int tabWidth = 145;

        // Notifica aggiornamento disponibile in alto a sinistra
        if (UpdateChecker.isUpdateAvailable() && (this.width / 2 - tabWidth - 5) >= 115) {
            String upTxt = "§6⚡ " + UpdateChecker.getLatestVersion();
            this.addRenderableWidget(Button.builder(Component.literal(upTxt), b -> {
                net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create(UpdateChecker.getReleasePageUrl()));
            }).bounds(8, topY, 105, 19).build());
        }

        // 1. Tab Navigazione Superiore (Catalogo vs Simulatore/Calcolatore)
        this.addRenderableWidget(Button.builder(Component.literal("📖 Catalogo Incantesimi"), button -> {
            this.currentTab = Tab.CATALOG;
            this.clearWidgets();
            this.init();
        }).bounds(this.width / 2 - tabWidth - 5, topY, tabWidth, 19).build());

        this.addRenderableWidget(Button.builder(Component.literal("🔨 Calcolatore & Simulatore"), button -> {
            this.currentTab = Tab.CALCULATOR;
            this.clearWidgets();
            this.init();
        }).bounds(this.width / 2 + 5, topY, tabWidth, 19).build());

        String backLabel = this.parentScreen != null ? "Torna [ESC]" : "Chiudi [ESC]";
        this.addRenderableWidget(Button.builder(Component.literal(backLabel), button -> this.onClose())
                .bounds(this.width - 78, topY, 70, 19).build());

        if (this.currentTab == Tab.CATALOG) {
            initCatalogWidgets(topY);
        } else {
            initCalculatorWidgets(topY);
        }
    }

    private void initCatalogWidgets(int topY) {
        int filterY = topY + 23;

        // RIGA 1: Filtri Categoria + Campo Ricerca
        int catStartX = this.width / 2 - 270;
        this.addRenderableWidget(Button.builder(Component.literal("ALL".equals(categoryFilter) ? "§aTutti" : "Tutti"), b -> {
            categoryFilter = "ALL";
            catalogPage = 0;
            updateSearch();
            rebuildWidgets();
        }).bounds(catStartX, filterY, 45, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("VANILLA".equals(categoryFilter) ? "§aVanilla" : "Vanilla"), b -> {
            categoryFilter = "VANILLA";
            catalogPage = 0;
            updateSearch();
            rebuildWidgets();
        }).bounds(catStartX + 49, filterY, 55, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("CUSTOM".equals(categoryFilter) ? "§aCustom" : "Custom"), b -> {
            categoryFilter = "CUSTOM";
            catalogPage = 0;
            updateSearch();
            rebuildWidgets();
        }).bounds(catStartX + 108, filterY, 52, 18).build());

        // Campo ricerca
        String oldText = this.searchField != null ? this.searchField.getValue() : "";
        this.searchField = new EditBox(this.font, catStartX + 165, filterY, 230, 18, Component.literal(""));
        this.searchField.setValue(oldText);
        this.searchField.setHint(Component.literal("Cerca per nome o effetto..."));
        this.searchField.setResponder(text -> {
            catalogPage = 0;
            updateSearch();
            refreshCatalogButtons();
        });
        this.addRenderableWidget(this.searchField);

        // Tasto Rapido Azzera Ricerche AH
        int ahCount = ActiveSessionManager.getSearchEnchants().size();
        String ahBtnLabel = ahCount > 0 ? "§c✕ Azzera AH (" + ahCount + ")" : "§8Cerca AH: Nessuno";

        this.btnClearAhSearch = Button.builder(Component.literal(ahBtnLabel), b -> {
            ActiveSessionManager.clearSearchEnchants();
            com.litness.optimizer.mixin.ContainerScreenMixin.clearCache();
            refreshCatalogButtons();
            updateClearButtonState();
            statusMessage = "§aRicerche e filtri in /ah azzerati!";
        }).bounds(catStartX + 402, filterY, 138, 18).build();
        this.btnClearAhSearch.active = (ahCount > 0);
        this.addRenderableWidget(this.btnClearAhSearch);

        // RIGA 2: Filtri per Tipo di Oggetto
        int itemFilterY = filterY + 21;
        int curItemX = this.width / 2 - 265;

        // [Tutti]
        this.addRenderableWidget(Button.builder(Component.literal("ALL".equals(itemFilter) ? "§aTutti" : "Tutti"), b -> {
            itemFilter = "ALL";
            catalogPage = 0;
            updateSearch();
            rebuildWidgets();
        }).bounds(curItemX, itemFilterY, 42, 18).build());
        curItemX += 45;

        // [⚔ Spada]
        this.addRenderableWidget(Button.builder(Component.literal("sword".equals(itemFilter) ? "§a⚔ Spada" : "⚔ Spada"), b -> {
            itemFilter = "sword";
            catalogPage = 0;
            updateSearch();
            rebuildWidgets();
        }).bounds(curItemX, itemFilterY, 62, 18).build());
        curItemX += 65;

        // [⛏ Piccone]
        this.addRenderableWidget(Button.builder(Component.literal("pick".equals(itemFilter) ? "§a⛏ Piccone" : "⛏ Piccone"), b -> {
            itemFilter = "pick";
            catalogPage = 0;
            updateSearch();
            rebuildWidgets();
        }).bounds(curItemX, itemFilterY, 68, 18).build());
        curItemX += 71;

        // [🛡 Armature]
        this.addRenderableWidget(Button.builder(Component.literal("armor".equals(itemFilter) ? "§a🛡 Armature" : "🛡 Armature"), b -> {
            itemFilter = "armor";
            catalogPage = 0;
            updateSearch();
            rebuildWidgets();
        }).bounds(curItemX, itemFilterY, 74, 18).build());
        curItemX += 77;

        // [🏹 Arco]
        this.addRenderableWidget(Button.builder(Component.literal("bow".equals(itemFilter) ? "§a🏹 Arco" : "🏹 Arco"), b -> {
            itemFilter = "bow";
            catalogPage = 0;
            updateSearch();
            rebuildWidgets();
        }).bounds(curItemX, itemFilterY, 58, 18).build());
        curItemX += 61;

        // [🪓 Ascia]
        this.addRenderableWidget(Button.builder(Component.literal("axe".equals(itemFilter) ? "§a🪓 Ascia" : "🪓 Ascia"), b -> {
            itemFilter = "axe";
            catalogPage = 0;
            updateSearch();
            rebuildWidgets();
        }).bounds(curItemX, itemFilterY, 60, 18).build());
        curItemX += 63;

        // [🌾 Zappa]
        this.addRenderableWidget(Button.builder(Component.literal("hoe".equals(itemFilter) ? "§a🌾 Zappa" : "🌾 Zappa"), b -> {
            itemFilter = "hoe";
            catalogPage = 0;
            updateSearch();
            rebuildWidgets();
        }).bounds(curItemX, itemFilterY, 60, 18).build());
        curItemX += 63;

        // [🔧 Altri]
        this.addRenderableWidget(Button.builder(Component.literal("other".equals(itemFilter) ? "§a🔧 Altri" : "🔧 Altri"), b -> {
            itemFilter = "other";
            catalogPage = 0;
            updateSearch();
            rebuildWidgets();
        }).bounds(curItemX, itemFilterY, 57, 18).build());

        // RIGA 3: Barra paginazione
        int navY = itemFilterY + 21;
        this.addRenderableWidget(Button.builder(Component.literal("◀ Precedente"), b -> {
            if (catalogPage > 0) {
                catalogPage--;
                refreshCatalogButtons();
            }
        }).bounds(this.width / 2 - 270, navY, 85, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Successiva ▶"), b -> {
            int totalP = Math.max(1, (int) Math.ceil((double) filteredEnchantments.size() / getItemsPerPage()));
            if (catalogPage < totalP - 1) {
                catalogPage++;
                refreshCatalogButtons();
            }
        }).bounds(this.width / 2 + 185, navY, 85, 18).build());

        updateSearch();
        refreshCatalogButtons();
    }

    private void refreshCatalogButtons() {
        for (Button b : catalogRowButtons) {
            this.removeWidget(b);
        }
        catalogRowButtons.clear();

        if (this.currentTab != Tab.CATALOG) return;

        int itemsPerPage = getItemsPerPage();
        int maxPages = Math.max(1, (int) Math.ceil((double) filteredEnchantments.size() / itemsPerPage));
        if (catalogPage >= maxPages) catalogPage = maxPages - 1;
        if (catalogPage < 0) catalogPage = 0;

        int startY = 94;
        int cardWidth = Math.min(640, this.width - 24);
        int cardX = (this.width - cardWidth) / 2;

        int startIdx = catalogPage * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, filteredEnchantments.size());

        for (int i = startIdx; i < endIdx; i++) {
            EnchantmentEntry ee = filteredEnchantments.get(i);
            int row = i - startIdx;
            int y = startY + row * 19;

            int btnW = 76;
            int btnH = 16;
            int btnX = cardX + cardWidth - btnW - 3;
            int btnY = y + 1;

            boolean isSearching = ActiveSessionManager.isSearchEnchant(ee.id);
            String label = isSearching ? "§a✔ In AH" : "§7+ Cerca AH";

            Button searchBtn = Button.builder(Component.literal(label), b -> {
                ActiveSessionManager.toggleSearchEnchant(ee.id);
                com.litness.optimizer.mixin.ContainerScreenMixin.clearCache();
                boolean nowSearching = ActiveSessionManager.isSearchEnchant(ee.id);
                if (nowSearching) {
                    statusMessage = "§aEvidenziazione in /ah attivata per: " + ee.name;
                } else {
                    statusMessage = "§eEvidenziazione rimossa per: " + ee.name;
                }
                refreshCatalogButtons();
                updateClearButtonState();
            }).bounds(btnX, btnY, btnW, btnH).build();

            catalogRowButtons.add(searchBtn);
            this.addRenderableWidget(searchBtn);
        }
    }

    private void updateClearButtonState() {
        if (btnClearAhSearch == null) return;
        int count = ActiveSessionManager.getSearchEnchants().size();
        String lbl = count > 0 ? "§c✕ Azzera AH (" + count + ")" : "§8Cerca AH: Nessuno";
        btnClearAhSearch.setMessage(Component.literal(lbl));
        btnClearAhSearch.active = (count > 0);
    }

    private void initCalculatorWidgets(int topY) {
        for (Button b : catalogRowButtons) {
            this.removeWidget(b);
        }
        catalogRowButtons.clear();

        int panelWidth = Math.min(550, this.width - 20);
        int startX = (this.width - panelWidth) / 2;

        int row1Y = topY + 22; // Y = 29
        int curX = startX;

        // RIGA 1: SELEZIONE RAPIDA OGGETTO (Spada, Piccone, Armature: Elmo, Corazza, Gambali, Stivali)
        int activeItem = isCreatingSet ? creatingSetItemIndex : selectedItemIndex;

        // [⚔ Spada]
        this.addRenderableWidget(Button.builder(Component.literal(activeItem == 0 ? "§a⚔ Spada" : "⚔ Spada"), b -> handleItemSelection(0))
                .bounds(curX, row1Y, 54, 18).build());
        curX += 56;

        // [⛏ Piccone]
        this.addRenderableWidget(Button.builder(Component.literal(activeItem == 1 ? "§a⛏ Piccone" : "⛏ Piccone"), b -> handleItemSelection(1))
                .bounds(curX, row1Y, 64, 18).build());
        curX += 66;

        // [🪖 Elmo]
        this.addRenderableWidget(Button.builder(Component.literal(activeItem == 2 ? "§a🪖 Elmo" : "🪖 Elmo"), b -> handleItemSelection(2))
                .bounds(curX, row1Y, 52, 18).build());
        curX += 54;

        // [👕 Corazza]
        this.addRenderableWidget(Button.builder(Component.literal(activeItem == 3 ? "§a👕 Corazza" : "👕 Corazza"), b -> handleItemSelection(3))
                .bounds(curX, row1Y, 64, 18).build());
        curX += 66;

        // [👖 Gambali]
        this.addRenderableWidget(Button.builder(Component.literal(activeItem == 4 ? "§a👖 Gambali" : "👖 Gambali"), b -> handleItemSelection(4))
                .bounds(curX, row1Y, 64, 18).build());
        curX += 66;

        // [👢 Stivali]
        this.addRenderableWidget(Button.builder(Component.literal(activeItem == 5 ? "§a👢 Stivali" : "👢 Stivali"), b -> handleItemSelection(5))
                .bounds(curX, row1Y, 60, 18).build());
        curX += 62;

        int row2Y = row1Y + 21; // Y = 50

        if (simView == SimView.GUIDE) {
            // [◀ Torna a I Miei Set]
            this.addRenderableWidget(Button.builder(Component.literal("◀ Torna a I Miei Set"), b -> {
                simView = SimView.SETS;
                this.clearWidgets();
                this.init();
            }).bounds(startX, row2Y, 130, 18).build());

            // Penalità di lavoro
            int penaltyW = 66;
            this.addRenderableWidget(Button.builder(
                Component.literal("Pen: +" + PENALTY_STEPS[selectedPenaltyIndex]),
                button -> {
                    selectedPenaltyIndex = (selectedPenaltyIndex + 1) % PENALTY_STEPS.length;
                    guideStepPage = 0;
                    runCalculation();
                    this.clearWidgets();
                    this.init();
                }
            ).bounds(startX + panelWidth - penaltyW, row2Y, penaltyW, 18).build());

            initGuideViewWidgets(startX, panelWidth, row2Y + 22);
        } else {
            initSavedSetsViewWidgets(startX, panelWidth, row2Y);
        }

        if (currentSolution == null) {
            if (this.anvilMenu != null) {
                scanFromGameEnvironment();
                if (!this.currentScannedBooks.isEmpty()) {
                    this.calcMode = CalcMode.INVENTORY;
                    runCalculation();
                }
            }
            if (currentSolution == null) {
                this.calcMode = CalcMode.SIMULATOR;
                if (simulatorSelectedEnchants.isEmpty()) {
                    String itemKey = ITEM_KEYS[selectedItemIndex];
                    Set<String> saved = ActiveSessionManager.getSelectedEnchants(itemKey);
                    if (saved != null && !saved.isEmpty()) {
                        simulatorSelectedEnchants.addAll(saved);
                    }
                }
                runCalculation();
            }
        }
    }

    private void handleItemSelection(int index) {
        if (isCreatingSet) {
            if (this.creatingSetNameField != null) {
                currentCreatingSetName = this.creatingSetNameField.getValue();
            }
            creatingSetItemIndex = index;
            selectedItemIndex = index;
            creatingSetEnchants.clear();
            creatingSetPage = 0;
            statusMessage = "Creazione set per " + ITEM_NAMES[index] + "...";
            this.clearWidgets();
            this.init();
        } else {
            selectItem(index);
        }
    }

    private void selectItem(int index) {
        selectedItemIndex = index;
        creatingSetItemIndex = index;
        ActiveSessionManager.setActiveItemIndex(index);
        guideStepPage = 0;
        simulatorSelectedEnchants.clear();
        String itemKey = ITEM_KEYS[selectedItemIndex];
        Set<String> saved = ActiveSessionManager.getSelectedEnchants(itemKey);
        if (saved != null && !saved.isEmpty()) {
            simulatorSelectedEnchants.addAll(saved);
        }
        runCalculation();
        this.clearWidgets();
        this.init();
    }

    private void initGuideViewWidgets(int startX, int panelWidth, int contentY) {
        // Pulsante Copia Guida negli Appunti posizionato saldamente a destra del banner
        this.btnCopyGuide = Button.builder(Component.literal("📋 Copia Passi"), b -> copyGuideToClipboard())
                .bounds(startX + panelWidth - 88, contentY + 1, 86, 18).build();
        this.addRenderableWidget(this.btnCopyGuide);

        // Paginazione Passi Incudine posizionata saldamente in fondo allo schermo
        int navY = this.height - 21;
        this.btnPrevStep = Button.builder(Component.literal("◀ Passi Precedenti"), b -> {
            if (guideStepPage > 0) guideStepPage--;
        }).bounds(startX, navY, 125, 18).build();
        this.addRenderableWidget(this.btnPrevStep);

        this.btnNextStep = Button.builder(Component.literal("Passi Successivi ▶"), b -> {
            if (currentSolution != null && !currentSolution.steps.isEmpty()) {
                int totalSteps = currentSolution.steps.size();
                int maxPages = Math.max(1, (int) Math.ceil((double) totalSteps / 4));
                if (guideStepPage < maxPages - 1) guideStepPage++;
            }
        }).bounds(startX + panelWidth - 125, navY, 125, 18).build();
        this.addRenderableWidget(this.btnNextStep);
    }

    private void copyGuideToClipboard() {
        if (currentSolution == null || currentSolution.steps.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append("=== GUIDA LITNESS ENCHANT HELPER: ").append(ITEM_NAMES[selectedItemIndex].toUpperCase()).append(" ===\n");
        sb.append("Costo Totale: ").append(currentSolution.totalCost).append(" liv. XP | Picco Massimo: ").append(currentSolution.peakCost).append("/39 liv.\n\n");
        for (int i = 0; i < currentSolution.steps.size(); i++) {
            MergeStep step = currentSolution.steps.get(i);
            sb.append("Passo ").append(i + 1).append("/").append(currentSolution.steps.size()).append(": ");
            if (step.isBookToBook) {
                sb.append("[Slot 1: ").append(step.leftLabel).append("] + [Slot 2: ").append(step.rightLabel).append("]");
            } else {
                sb.append("[").append(step.leftLabel).append("] + [Libro: ").append(step.rightLabel).append("]");
            }
            sb.append(" ➔ ").append(step.resultLabel).append(" (Costo: ").append(step.cost).append(" liv.)\n");
        }
        if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
            this.minecraft.keyboardHandler.setClipboard(sb.toString());
            statusMessage = "§aGuida copiata negli appunti con successo!";
        }
    }

    private String getPresetButtonLabel() {
        String key = ITEM_KEYS[selectedItemIndex];
        switch (key) {
            case "sword": return "Spada";
            case "pick":
            case "pickaxe": return "Piccone";
            case "helmet": return "Elmo";
            case "chest": return "Corazza";
            case "legs": return "Gambali";
            case "boots": return "Stivali";
            default: return ITEM_NAMES[selectedItemIndex];
        }
    }

    private int getSelectedUniqueCount() {
        int count = 0;
        for (String id : simulatorSelectedEnchants) {
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(id);
            if (ee != null && ee.unique) count++;
        }
        return count;
    }

    private String getConflictWithCollection(EnchantmentEntry ee, Collection<String> selectedIds) {
        if (ee == null || selectedIds == null || selectedIds.isEmpty()) return null;
        List<String> conflicts = EnchantmentDatabase.getResolvedConflicts(ee.id);
        for (String selId : selectedIds) {
            if (selId.equalsIgnoreCase(ee.id)) continue;
            EnchantmentEntry selEe = EnchantmentDatabase.getEnchantment(selId);
            if (selEe == null) continue;
            if (conflicts.contains(selEe.name) || conflicts.contains(selEe.id)) {
                return selEe.name;
            }
            List<String> selConflicts = EnchantmentDatabase.getResolvedConflicts(selId);
            if (selConflicts.contains(ee.name) || selConflicts.contains(ee.id)) {
                return selEe.name;
            }
        }
        return null;
    }

    private String getConflictWithSelected(EnchantmentEntry ee) {
        return getConflictWithCollection(ee, isCreatingSet ? creatingSetEnchants : simulatorSelectedEnchants);
    }


    private void initSavedSetsViewWidgets(int startX, int panelWidth, int contentY) {
        if (isCreatingSet) {
            int nameFieldW = 160;
            this.creatingSetNameField = new EditBox(this.font, startX, contentY, nameFieldW, 18, Component.literal(""));
            if (currentCreatingSetName != null && !currentCreatingSetName.isEmpty()) {
                this.creatingSetNameField.setValue(currentCreatingSetName);
            } else if (editingSetId != null) {
                SavedSetsManager.CustomSet editSet = SavedSetsManager.getSetById(editingSetId);
                if (editSet != null) {
                    this.creatingSetNameField.setValue(editSet.name);
                    currentCreatingSetName = editSet.name;
                }
            }
            this.creatingSetNameField.setResponder(text -> currentCreatingSetName = text);
            this.creatingSetNameField.setHint(Component.literal("Nome Set Futuro..."));
            this.addRenderableWidget(this.creatingSetNameField);

            int curX = startX + nameFieldW + 4;

            // [Pezzo: Spada / Piccone / Elmo / Corazza / Gambali / Stivali]
            this.addRenderableWidget(Button.builder(Component.literal("Pezzo: " + ITEM_NAMES[creatingSetItemIndex]), b -> {
                if (this.creatingSetNameField != null) {
                    currentCreatingSetName = this.creatingSetNameField.getValue();
                }
                creatingSetItemIndex = (creatingSetItemIndex + 1) % ITEM_NAMES.length;
                selectedItemIndex = creatingSetItemIndex;
                creatingSetEnchants.clear();
                creatingSetPage = 0;
                this.clearWidgets();
                this.init();
            }).bounds(curX, contentY, 94, 18).build());
            curX += 98;

            // [💾 Salva Set Futuro]
            String saveLabel = "💾 Salva (" + creatingSetEnchants.size() + "/12)";
            this.addRenderableWidget(Button.builder(Component.literal(saveLabel), b -> {
                if (!creatingSetEnchants.isEmpty()) {
                    String inputName = (creatingSetNameField != null) ? creatingSetNameField.getValue().trim() : currentCreatingSetName.trim();
                    if (inputName.isEmpty()) {
                        inputName = "Set " + ITEM_NAMES[creatingSetItemIndex] + " #" + (SavedSetsManager.getSavedSets().size() + 1);
                    }
                    if (editingSetId != null) {
                        SavedSetsManager.updateSet(editingSetId, inputName, ITEM_KEYS[creatingSetItemIndex], creatingSetEnchants);
                        statusMessage = "§aSet '" + inputName + "' modificato con successo!";
                        editingSetId = null;
                    } else {
                        SavedSetsManager.addSet(inputName, ITEM_KEYS[creatingSetItemIndex], creatingSetEnchants);
                        statusMessage = "§aSet '" + inputName + "' salvato con successo!";
                    }
                    isCreatingSet = false;
                    currentCreatingSetName = "";
                    savedSetsPage = 0;
                    this.clearWidgets();
                    this.init();
                } else {
                    statusMessage = "§eSeleziona almeno un incantesimo per salvare il set!";
                }
            }).bounds(startX + panelWidth - 145, contentY, 86, 18).build());

            // [◀ Annulla]
            this.addRenderableWidget(Button.builder(Component.literal("◀ Annulla"), b -> {
                isCreatingSet = false;
                editingSetId = null;
                currentCreatingSetName = "";
                this.clearWidgets();
                this.init();
            }).bounds(startX + panelWidth - 55, contentY, 55, 18).build());

            // Status Banner a Y=71 (contentY + 21), Pulsanti incantesimi partono a Y=90 (contentY + 40)
            refreshCreatingSetEnchantButtons(contentY + 40, startX, panelWidth);
            return;
        }

        // VISTA STANDARD: LISTA DEI SET SALVATI & PRESET
        // [➕ Crea Nuovo Set Futuro]
        this.addRenderableWidget(Button.builder(Component.literal("➕ Crea Nuovo Set Futuro"), b -> {
            isCreatingSet = true;
            editingSetId = null;
            currentCreatingSetName = "";
            creatingSetItemIndex = selectedItemIndex;
            creatingSetEnchants.clear();
            creatingSetPage = 0;
            this.clearWidgets();
            this.init();
        }).bounds(startX, contentY, 155, 18).build());

        // Paginazione
        List<SavedSetsManager.CustomSet> allSets = SavedSetsManager.getSavedSets();
        int totalSavedPages = Math.max(1, (int) Math.ceil((double) allSets.size() / 4));
        if (savedSetsPage >= totalSavedPages) savedSetsPage = totalSavedPages - 1;
        if (savedSetsPage < 0) savedSetsPage = 0;

        Button prevBtn = Button.builder(Component.literal("◀"), b -> {
            if (savedSetsPage > 0) {
                savedSetsPage--;
                this.clearWidgets();
                this.init();
            }
        }).bounds(startX + panelWidth - 52, contentY, 24, 18).build();
        prevBtn.active = savedSetsPage > 0;
        this.addRenderableWidget(prevBtn);

        Button nextBtn = Button.builder(Component.literal("▶"), b -> {
            int t = Math.max(1, (int) Math.ceil((double) SavedSetsManager.getSavedSets().size() / 4));
            if (savedSetsPage < t - 1) {
                savedSetsPage++;
                this.clearWidgets();
                this.init();
            }
        }).bounds(startX + panelWidth - 26, contentY, 24, 18).build();
        nextBtn.active = savedSetsPage < totalSavedPages - 1;
        this.addRenderableWidget(nextBtn);

        refreshSavedSetButtons(contentY + 23, startX, panelWidth);
    }

    private void refreshCreatingSetEnchantButtons(int startY, int startX, int panelWidth) {
        for (Button b : creatingSetEnchantButtons) {
            this.removeWidget(b);
        }
        creatingSetEnchantButtons.clear();

        String itemKey = ITEM_KEYS[creatingSetItemIndex];
        ItemEntry targetItem = EnchantmentDatabase.getItem(itemKey);
        if (targetItem == null) targetItem = EnchantmentDatabase.getItem("sword");

        List<EnchantmentEntry> compList = new ArrayList<>();
        for (EnchantmentEntry ee : EnchantmentDatabase.getAllEnchantments()) {
            if (EnchantmentDatabase.isApplicable(ee, targetItem.id)) {
                compList.add(ee);
            }
        }

        currentVisibleSelectionEnchants.clear();
        currentVisibleSelectionEnchants.addAll(compList);

        int totalPages = Math.max(1, (int) Math.ceil((double) compList.size() / 10));
        if (creatingSetPage >= totalPages) creatingSetPage = totalPages - 1;
        if (creatingSetPage < 0) creatingSetPage = 0;

        int startIdx = creatingSetPage * 10;
        int endIdx = Math.min(startIdx + 10, compList.size());
        int colWidth = (panelWidth - 10) / 2;

        for (int i = startIdx; i < endIdx; i++) {
            EnchantmentEntry ee = compList.get(i);
            int slot = i - startIdx;
            int col = slot % 2;
            int row = slot / 2;

            int btnX = startX + (col * (colWidth + 10));
            int btnY = startY + (row * 20);

            boolean isSelected = creatingSetEnchants.contains(ee.id);
            String conflict = getConflictWithCollection(ee, creatingSetEnchants);
            boolean hasConflict = (conflict != null);
            String maxRom = AnvilOptimizer.toRoman(ee.maxLevel);
            String baseName = ee.name + " " + maxRom;

            String label;
            if (isSelected) {
                if (hasConflict) {
                    label = "§e⚠️ §a✔ §f" + baseName + " §c(" + conflict + ")";
                } else {
                    label = "§a✔ §f" + baseName;
                }
            } else {
                if (hasConflict) {
                    label = "§e⚠️ §8[  ] §c" + baseName + " §8(vs " + conflict + ")";
                } else {
                    label = "§8[  ] §7" + baseName;
                }
            }
            label = trimToWidth(label, colWidth - 8);

            Button enchBtn = Button.builder(Component.literal(label), b -> {
                if (this.creatingSetNameField != null) {
                    currentCreatingSetName = this.creatingSetNameField.getValue();
                }
                if (creatingSetEnchants.contains(ee.id)) {
                    creatingSetEnchants.remove(ee.id);
                    statusMessage = "Rimosso dal set: " + ee.name;
                } else {
                    if (creatingSetEnchants.size() >= 12) {
                        statusMessage = "§cLimite massimo di 12 incantesimi raggiunto!";
                        return;
                    }
                    if (hasConflict) {
                        statusMessage = "§e⚠️ Attenzione: '" + ee.name + "' è incompatibile con '" + conflict + "'!";
                    } else {
                        statusMessage = "Aggiunto al set futuro: " + ee.name + " " + maxRom;
                    }
                    creatingSetEnchants.add(ee.id);
                }
                this.clearWidgets();
                this.init();
            }).bounds(btnX, btnY, colWidth, 19).build();

            this.addRenderableWidget(enchBtn);
            creatingSetEnchantButtons.add(enchBtn);
        }

        int navY = startY + 5 * 20 + 4;
        Button prev = Button.builder(Component.literal("◀ Precedenti"), b -> {
            if (this.creatingSetNameField != null) {
                currentCreatingSetName = this.creatingSetNameField.getValue();
            }
            if (creatingSetPage > 0) {
                creatingSetPage--;
                this.clearWidgets();
                this.init();
            }
        }).bounds(startX, navY, 110, 18).build();
        prev.active = creatingSetPage > 0;
        this.addRenderableWidget(prev);
        creatingSetEnchantButtons.add(prev);

        Button next = Button.builder(Component.literal("Successivi ▶"), b -> {
            if (this.creatingSetNameField != null) {
                currentCreatingSetName = this.creatingSetNameField.getValue();
            }
            if (creatingSetPage < totalPages - 1) {
                creatingSetPage++;
                this.clearWidgets();
                this.init();
            }
        }).bounds(startX + panelWidth - 110, navY, 110, 18).build();
        next.active = creatingSetPage < totalPages - 1;
        this.addRenderableWidget(next);
        creatingSetEnchantButtons.add(next);
    }

    private void refreshSavedSetButtons(int startY, int startX, int panelWidth) {
        for (Button b : savedSetButtons) {
            this.removeWidget(b);
        }
        savedSetButtons.clear();

        List<SavedSetsManager.CustomSet> sets = SavedSetsManager.getSavedSets();
        if (sets.isEmpty()) {
            return;
        }

        int startIdx = savedSetsPage * 4;
        int endIdx = Math.min(startIdx + 4, sets.size());
        int cardH = 44;
        int gap = 4;

        for (int i = startIdx; i < endIdx; i++) {
            SavedSetsManager.CustomSet set = sets.get(i);
            int row = i - startIdx;
            int y = startY + row * (cardH + gap);

            // [⚡ Guida]
            Button loadBtn = Button.builder(Component.literal("⚡ Guida"), b -> {
                loadSavedSetIntoSimulator(set);
            }).bounds(startX + panelWidth - 176, y + 13, 56, 18).build();
            this.addRenderableWidget(loadBtn);
            savedSetButtons.add(loadBtn);

            // [📌 Pin HUD] / [★ Pinnato]
            boolean isPinned = ActiveSessionManager.isSetPinned(set.id);
            Button pinBtn = Button.builder(Component.literal(isPinned ? "§6★ Pinnato" : "📌 Pin HUD"), b -> {
                ActiveSessionManager.togglePinSet(set.id);
                this.clearWidgets();
                this.init();
            }).bounds(startX + panelWidth - 116, y + 13, 60, 18).build();
            this.addRenderableWidget(pinBtn);
            savedSetButtons.add(pinBtn);

            // [✏ Modifica Set]
            Button editBtn = Button.builder(Component.literal("✏"), b -> {
                isCreatingSet = true;
                editingSetId = set.id;
                currentCreatingSetName = set.name;
                for (int k = 0; k < ITEM_KEYS.length; k++) {
                    if (ITEM_KEYS[k].equalsIgnoreCase(set.itemKey)) {
                        creatingSetItemIndex = k;
                        selectedItemIndex = k;
                        break;
                    }
                }
                creatingSetEnchants.clear();
                creatingSetEnchants.addAll(set.enchantments);
                creatingSetPage = 0;
                statusMessage = "§eModifica del set '" + set.name + "' in corso...";
                this.clearWidgets();
                this.init();
            }).bounds(startX + panelWidth - 52, y + 13, 24, 18).build();
            this.addRenderableWidget(editBtn);
            savedSetButtons.add(editBtn);

            // [🗑 Elimina]
            Button delBtn = Button.builder(Component.literal("🗑"), b -> {
                SavedSetsManager.deleteSet(set.id);
                statusMessage = "§cSet '" + set.name + "' eliminato.";
                this.clearWidgets();
                this.init();
            }).bounds(startX + panelWidth - 26, y + 13, 24, 18).build();
            this.addRenderableWidget(delBtn);
            savedSetButtons.add(delBtn);
        }
    }

    private void loadSavedSetIntoSimulator(SavedSetsManager.CustomSet set) {
        if (set == null) return;
        for (int i = 0; i < ITEM_KEYS.length; i++) {
            if (ITEM_KEYS[i].equalsIgnoreCase(set.itemKey)) {
                selectedItemIndex = i;
                ActiveSessionManager.setActiveItemIndex(i);
                break;
            }
        }
        simulatorSelectedEnchants.clear();
        simulatorSelectedEnchants.addAll(set.enchantments);
        ActiveSessionManager.setSelectedEnchants(set.itemKey, set.enchantments);
        ActiveSessionManager.setActiveSetId(set.id);
        this.calcMode = CalcMode.SIMULATOR;
        guideStepPage = 0;
        statusMessage = "§aCaricato set '" + set.name + "' (" + set.enchantments.size() + "/12)!";
        simView = SimView.GUIDE;
        runCalculation();
        this.clearWidgets();
        this.init();
    }

    private void renderSavedSetsView(GuiGraphicsExtractor extractor, int panelX, int panelWidth, int mouseX, int mouseY) {
        int startY = 73;

        if (isCreatingSet) {
            int bannerY = 71;
            String conflictWarn = "";
            for (String enchId : creatingSetEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(enchId);
                String conf = getConflictWithCollection(ee, creatingSetEnchants);
                if (conf != null) {
                    conflictWarn = " • §e⚠️ Conflitto: " + (ee != null ? ee.name : enchId) + " vs " + conf;
                    break;
                }
            }

            int bannerBg = conflictWarn.isEmpty() ? 0xEE141422 : 0xEE2A1A14;
            int bannerStripe = conflictWarn.isEmpty() ? 0xFF10B981 : 0xFFF59E0B;
            extractor.fill(panelX, bannerY, panelX + panelWidth, bannerY + 16, bannerBg);
            extractor.fill(panelX, bannerY, panelX + 3, bannerY + 16, bannerStripe);
            extractor.outline(panelX, bannerY, panelWidth, 16, conflictWarn.isEmpty() ? 0xFF2A2A3E : 0xFF7C2D12);

            String titlePrefix = editingSetId != null ? "✏️ Modifica Set" : "➕ Crea Set Futuro";
            String status = titlePrefix + " per " + ITEM_NAMES[creatingSetItemIndex] + " • Selezionati: " + creatingSetEnchants.size() + "/12" + conflictWarn;
            extractor.text(this.font, status, panelX + 8, bannerY + 4, conflictWarn.isEmpty() ? 0xFF34D399 : 0xFFFBBF24, false);

            this.hoveredSelectionEntry = null;
            int startIdx = creatingSetPage * 10;
            int endIdx = Math.min(startIdx + 10, currentVisibleSelectionEnchants.size());

            // Controlla il passaggio del mouse direttamente sui pulsanti reali (zero falsi positivi nel vuoto)
            for (int i = startIdx; i < endIdx; i++) {
                int slot = i - startIdx;
                if (slot < creatingSetEnchantButtons.size()) {
                    Button b = creatingSetEnchantButtons.get(slot);
                    if (b != null && b.isHovered()) {
                        this.hoveredSelectionEntry = currentVisibleSelectionEnchants.get(i);
                        break;
                    }
                }
            }
            return;
        }

        List<SavedSetsManager.CustomSet> sets = SavedSetsManager.getSavedSets();

        if (sets.isEmpty()) {
            int emptyH = 50;
            extractor.fill(panelX, startY, panelX + panelWidth, startY + emptyH, 0xEE141422);
            extractor.outline(panelX, startY, panelWidth, emptyH, 0xFF2A2A3E);
            extractor.centeredText(this.font, "§7Nessun set salvato.", panelX + panelWidth / 2, startY + 12, 0xFFA1A1AA);
            extractor.centeredText(this.font, "§aClicca su '➕ Crea Nuovo Set Futuro' in alto a sinistra per creare il tuo set!", panelX + panelWidth / 2, startY + 28, 0xFF34D399);
            return;
        }

        int startIdx = savedSetsPage * 4;
        int endIdx = Math.min(startIdx + 4, sets.size());
        int cardH = 44;
        int gap = 4;

        for (int i = startIdx; i < endIdx; i++) {
            SavedSetsManager.CustomSet set = sets.get(i);
            int row = i - startIdx;
            int y = startY + row * (cardH + gap);

            int inInv = InventoryTracker.countInInventory(set.enchantments);

            String conflictFound = null;
            for (String enchId : set.enchantments) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(enchId);
                String conf = getConflictWithCollection(ee, set.enchantments);
                if (conf != null) {
                    conflictFound = (ee != null ? ee.name : enchId) + " vs " + conf;
                    break;
                }
            }

            boolean isPinned = ActiveSessionManager.isSetPinned(set.id);
            int stripeColor = (conflictFound != null) ? 0xFFEF4444 : (isPinned ? 0xFFF59E0B : 0xFF6366F1);
            int cardBg = isPinned ? 0xEE161528 : 0xEE12121E;
            int cardOutline = (conflictFound != null) ? 0xFF7F1D1D : (isPinned ? 0xFFB45309 : 0xFF2A2A3E);
            extractor.fill(panelX, y, panelX + panelWidth, y + cardH, cardBg);
            extractor.fill(panelX, y, panelX + 3, y + cardH, stripeColor);
            extractor.outline(panelX, y, panelWidth, cardH, cardOutline);

            extractor.fakeItem(getItemStackForType(set.itemKey), panelX + 8, y + 14);

            String warnIcon = (conflictFound != null) ? " §e⚠️" : "";
            String pinPrefix = isPinned ? "§6★ " : "";
            String title = pinPrefix + "§f" + set.name + " §8[" + capitalize(set.itemKey) + " • " + set.enchantments.size() + "/12]" + warnIcon;
            extractor.text(this.font, title, panelX + 32, y + 6, (conflictFound != null) ? 0xFFFBBF24 : 0xFFFFFFFF, false);

            String inInvTxt = (inInv == set.enchantments.size() && set.enchantments.size() > 0)
                    ? "§a📦 " + inInv + "/" + set.enchantments.size() + " In Inventario ✔"
                    : "§e📦 " + inInv + "/" + set.enchantments.size() + " In Inventario";
            if (conflictFound != null) {
                inInvTxt += "  §e⚠️ (Conflitto: " + conflictFound + ")";
            }
            extractor.text(this.font, inInvTxt, panelX + 32, y + 18, (conflictFound != null) ? 0xFFFBBF24 : 0xFFA1A1AA, false);

            List<String> names = new ArrayList<>();
            for (String enchId : set.enchantments) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(enchId);
                names.add(ee != null ? ee.name : enchId);
            }
            String preview = trimToWidth("§8" + String.join(", ", names), panelWidth - 215);
            extractor.text(this.font, preview, panelX + 32, y + 30, 0xFF71717A, false);
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private void applyDefaultRecommendedEnchants() {
        simulatorSelectedEnchants.clear();
        String itemKey = ITEM_KEYS[selectedItemIndex];
        ItemEntry targetItem = EnchantmentDatabase.getItem(itemKey);
        String targetId = targetItem != null ? targetItem.id : itemKey;

        int added = 0;
        for (EnchantmentEntry ee : EnchantmentDatabase.getAllEnchantments()) {
            if (EnchantmentDatabase.isApplicable(ee, targetId)) {
                simulatorSelectedEnchants.add(ee.id);
                added++;
                if (added >= 5) break;
            }
        }
    }

    private boolean matchesItemFilter(EnchantmentEntry ee) {
        if ("ALL".equals(itemFilter)) return true;
        if (ee == null) return false;

        switch (itemFilter) {
            case "sword":
                return EnchantmentDatabase.isApplicable(ee, "sword");
            case "pick":
                return EnchantmentDatabase.isApplicable(ee, "pick");
            case "armor":
                return EnchantmentDatabase.isApplicable(ee, "helmet")
                    || EnchantmentDatabase.isApplicable(ee, "chest")
                    || EnchantmentDatabase.isApplicable(ee, "legs")
                    || EnchantmentDatabase.isApplicable(ee, "boots");
            case "bow":
                return EnchantmentDatabase.isApplicable(ee, "bow")
                    || (ee.applicableTo != null && (ee.applicableTo.contains("bow") || ee.applicableTo.contains("xbow") || ee.applicableTo.contains("crossbow")));
            case "axe":
                return EnchantmentDatabase.isApplicable(ee, "axe");
            case "hoe":
                return EnchantmentDatabase.isApplicable(ee, "hoe");
            case "other":
                boolean isMain = EnchantmentDatabase.isApplicable(ee, "sword")
                    || EnchantmentDatabase.isApplicable(ee, "pick")
                    || EnchantmentDatabase.isApplicable(ee, "helmet")
                    || EnchantmentDatabase.isApplicable(ee, "chest")
                    || EnchantmentDatabase.isApplicable(ee, "legs")
                    || EnchantmentDatabase.isApplicable(ee, "boots")
                    || EnchantmentDatabase.isApplicable(ee, "bow")
                    || (ee.applicableTo != null && (ee.applicableTo.contains("bow") || ee.applicableTo.contains("xbow") || ee.applicableTo.contains("crossbow")))
                    || EnchantmentDatabase.isApplicable(ee, "axe")
                    || EnchantmentDatabase.isApplicable(ee, "hoe");
                return !isMain;
            default:
                return true;
        }
    }

    private void updateSearch() {
        filteredEnchantments.clear();
        String query = searchField != null ? searchField.getValue().toLowerCase().trim() : "";

        for (EnchantmentEntry ee : EnchantmentDatabase.getAllEnchantments()) {
            if ("VANILLA".equals(categoryFilter) && !"vanilla".equalsIgnoreCase(ee.category)) continue;
            if ("CUSTOM".equals(categoryFilter) && !"custom".equalsIgnoreCase(ee.category)) continue;
            if (!matchesItemFilter(ee)) continue;

            if (query.isEmpty()
                    || ee.name.toLowerCase().contains(query)
                    || ee.id.toLowerCase().contains(query)
                    || (ee.description != null && ee.description.toLowerCase().contains(query))) {
                filteredEnchantments.add(ee);
            }
        }
    }

    private void scanFromGameEnvironment() {
        currentScannedBooks.clear();
        if (this.minecraft == null || this.minecraft.player == null) return;

        Set<ItemStack> scannedStacks = new HashSet<>();

        // Se siamo dentro un'incudine reale, prendiamo gli slot dell'incudine
        if (this.anvilMenu != null) {
            for (int i = 0; i < 3; i++) {
                ItemStack slotStack = this.anvilMenu.getSlot(i).getItem();
                if (!slotStack.isEmpty() && scannedStacks.add(slotStack)) {
                    processItemStackForBooks(slotStack);
                }
            }
        }

        // Inventario del giocatore (tutti gli slot dell'inventario)
        var inv = this.minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && scannedStacks.add(stack)) {
                processItemStackForBooks(stack);
            }
        }
    }

    private void processItemStackForBooks(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK) && !stack.is(Items.BOOK)) return;

        int repairPenalty = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
        int priorWork = 0;
        for (int p = 0; p < PENALTY_STEPS.length; p++) {
            if (PENALTY_STEPS[p] == repairPenalty) {
                priorWork = p;
                break;
            }
        }

        BookInput book = new BookInput(priorWork);
        book.enchantments = extractAllEnchantmentsFromItem(stack);

        int loreCost = InventoryTracker.extractAnvilCostFromLore(stack);
        book.explicitCost = loreCost;
        if (loreCost > 0) {
            for (EnchantApplied ea : book.enchantments) {
                EnchantmentDatabase.updateAnvilCost(ea.id, loreCost);
            }
        }

        if (!book.enchantments.isEmpty()) {
            currentScannedBooks.add(book);
        }
    }

    private void runCalculation() {
        String itemKey = ITEM_KEYS[selectedItemIndex];
        ItemEntry targetItem = EnchantmentDatabase.getItem(itemKey);
        if (targetItem == null) targetItem = EnchantmentDatabase.getItem("sword");

        int priorPenaltyWork = selectedPenaltyIndex;
        List<BookInput> books = new ArrayList<>();

        if (calcMode == CalcMode.SIMULATOR) {
            for (String enchId : simulatorSelectedEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(enchId);
                if (ee != null) {
                    BookInput b = new BookInput(0);
                    b.enchantments.add(new EnchantApplied(ee.id, ee.maxLevel));
                    books.add(b);
                }
            }
            int uniqueCount = getSelectedUniqueCount();
            String uniqueText = uniqueCount > 0 ? " • §d" + uniqueCount + "/1 Unico" : "";
            String countColor = books.size() > 12 ? "§c" : (books.size() == 12 ? "§a" : "§f");
            statusMessage = "Simulazione " + targetItem.name + ": " + countColor + books.size() + "/12 incantesimi§r" + uniqueText;
        } else {
            List<EnchantApplied> existingEnchantsOnSlot0 = new ArrayList<>();
            if (this.anvilMenu != null) {
                ItemStack slot0 = this.anvilMenu.getSlot(0).getItem();
                if (!slot0.isEmpty()) {
                    existingEnchantsOnSlot0 = extractAllEnchantmentsFromItem(slot0);
                }
            }

            Set<String> plannedEnchants = ActiveSessionManager.getSelectedEnchants(targetItem.id);

            for (BookInput bi : currentScannedBooks) {
                BookInput filtered = new BookInput(bi.works);
                filtered.explicitCost = bi.explicitCost;
                for (EnchantApplied ea : bi.enchantments) {
                    EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                    if (ee != null && EnchantmentDatabase.isApplicable(ee, targetItem.id)) {
                        if (plannedEnchants != null && !plannedEnchants.isEmpty()) {
                            boolean isPartOfPlan = false;
                            for (String pid : plannedEnchants) {
                                if (pid.equalsIgnoreCase(ee.id) || pid.equalsIgnoreCase(ee.name)) {
                                    isPartOfPlan = true;
                                    break;
                                }
                            }
                            if (!isPartOfPlan) continue;
                        }

                        boolean alreadyPresent = false;
                        boolean conflictsWithItem = false;
                        List<String> confs = EnchantmentDatabase.getResolvedConflicts(ee.id);
                        for (EnchantApplied exist : existingEnchantsOnSlot0) {
                            if (exist.id.equalsIgnoreCase(ea.id) && exist.level >= ea.level) {
                                alreadyPresent = true;
                                break;
                            }
                            EnchantmentEntry eeExist = EnchantmentDatabase.getEnchantment(exist.id);
                            String existName = eeExist != null ? eeExist.name : exist.id;
                            if (confs.contains(existName) || confs.contains(exist.id)) {
                                conflictsWithItem = true;
                                break;
                            }
                        }
                        if (!alreadyPresent && !conflictsWithItem) {
                            filtered.enchantments.add(ea);
                        }
                    }
                }
                if (!filtered.enchantments.isEmpty()) {
                    books.add(filtered);
                }
            }

            if (books.isEmpty()) {
                statusMessage = "§cNessun libro compatibile trovato nell'inventario per " + targetItem.name;
            } else {
                statusMessage = "Rilevati " + books.size() + " libri compatibili nell'inventario per " + targetItem.name;
            }
        }

        currentSolution = AnvilOptimizer.optimize(books, priorPenaltyWork, targetItem, Collections.emptyList());
    }

    private List<EnchantApplied> extractAllEnchantmentsFromItem(ItemStack stack) {
        List<EnchantApplied> list = new ArrayList<>();
        if (stack.isEmpty()) return list;

        Set<String> seen = new HashSet<>();

        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            String id = ee != null ? ee.id : rawId;
            if (!id.isEmpty() && seen.add(id)) {
                list.add(new EnchantApplied(id, lvl));
            }
        }

        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            String id = ee != null ? ee.id : rawId;
            if (!id.isEmpty() && seen.add(id)) {
                list.add(new EnchantApplied(id, lvl));
            }
        }

        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component comp : lore.lines()) {
            LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(comp.getString());
            if (match != null && match.entry != null && seen.add(match.entry.id)) {
                list.add(new EnchantApplied(match.entry.id, match.level > 0 ? match.level : 1));
            }
        }

        try {
            if (this.minecraft != null && this.minecraft.player != null) {
                var lines = stack.getTooltipLines(Item.TooltipContext.EMPTY, this.minecraft.player, TooltipFlag.NORMAL);
                for (Component comp : lines) {
                    LitnessTooltipHandler.MatchedEnchantment match = LitnessTooltipHandler.matchEnchantment(comp.getString());
                    if (match != null && match.entry != null && seen.add(match.entry.id)) {
                        list.add(new EnchantApplied(match.entry.id, match.level > 0 ? match.level : 1));
                    }
                }
            }
        } catch (Throwable ignored) {}

        return list;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        try {
            extractor.fill(0, 0, this.width, this.height, 0xEE0B0B14);

            this.hoveredCatalogEntry = null;
            this.hoveredGuideSlot = null;
            this.hoveredSelectionEntry = null;

            if (currentTab == Tab.CATALOG) {
                renderCatalogTab(extractor, mouseX, mouseY);
            } else {
                renderCalculatorTab(extractor, mouseX, mouseY);
            }

            super.extractRenderState(extractor, mouseX, mouseY, delta);

            // Render hover tooltips strictly AFTER widgets so they are never covered by buttons
            if (currentTab == Tab.CATALOG) {
                if (hoveredCatalogEntry != null) {
                    renderEnchantmentTooltip(extractor, hoveredCatalogEntry, mouseX, mouseY);
                }
            } else if (currentTab == Tab.CALCULATOR) {
                if (simView == SimView.GUIDE) {
                    if (hoveredGuideSlot != null) {
                        renderGuideSlotTooltip(extractor, hoveredGuideSlot, mouseX, mouseY);
                    }
                } else if (simView == SimView.SETS) {
                    if (isCreatingSet && hoveredSelectionEntry != null) {
                        renderEnchantmentTooltip(extractor, hoveredSelectionEntry, mouseX, mouseY);
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void renderCatalogTab(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int itemsPerPage = getItemsPerPage();
        int maxPages = Math.max(1, (int) Math.ceil((double) filteredEnchantments.size() / itemsPerPage));
        if (catalogPage >= maxPages) catalogPage = maxPages - 1;

        int searchCount = ActiveSessionManager.getSearchEnchants().size();
        String searchInfo = searchCount > 0 ? "  •  §a" + searchCount + " in ricerca AH" : "";
        String pageInfo = "Pagina " + (catalogPage + 1) + " / " + maxPages + "  (" + filteredEnchantments.size() + " incantesimi)" + searchInfo;
        extractor.centeredText(this.font, pageInfo, this.width / 2, 77, 0xFF818CF8);

        int startY = 94;
        int cardWidth = Math.min(640, this.width - 24);
        int cardX = (this.width - cardWidth) / 2;

        int startIdx = catalogPage * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, filteredEnchantments.size());

        EnchantmentEntry hoveredEntry = null;

        for (int i = startIdx; i < endIdx; i++) {
            EnchantmentEntry e = filteredEnchantments.get(i);
            int row = i - startIdx;
            int y = startY + row * 19;

            int btnW = 76;
            boolean isHovered = mouseX >= cardX && mouseX < (cardX + cardWidth - btnW - 6) && mouseY >= y && mouseY <= y + 18;
            if (isHovered) hoveredEntry = e;

            boolean isSearching = ActiveSessionManager.isSearchEnchant(e.id);
            boolean isCustom = "custom".equalsIgnoreCase(e.category);
            int stripeColor = isSearching ? 0xFF00FF66 : (isCustom ? 0xFF10B981 : 0xFF6366F1);
            int bgColor = isSearching ? (isHovered ? 0xEE163224 : 0xDD10241A) : (isHovered ? 0xEE1E1E2E : 0xDD141420);

            extractor.fill(cardX, y, cardX + cardWidth, y + 18, bgColor);
            extractor.fill(cardX, y, cardX + 3, y + 18, stripeColor);
            if (isSearching) {
                extractor.fill(cardX, y, cardX + cardWidth, y + 1, 0x8000FF66);
                extractor.fill(cardX, y + 17, cardX + cardWidth, y + 18, 0x8000FF66);
            }

            String maxRoman = e.maxLevel > 1 ? " " + AnvilOptimizer.toRoman(e.maxLevel) : "";
            String title = e.name + maxRoman;
            extractor.text(this.font, title, cardX + 6, y + 5, isSearching ? 0xFF86EFAC : 0xFFFFFFFF, false);

            int titleW = this.font.width(title);
            int badgeX = cardX + 12 + titleW;

            String badge = isCustom ? "[" + (e.rarity != null ? e.rarity.toUpperCase() : "CUSTOM") + "]" : "[VANILLA]";
            int badgeColor = isCustom ? 0xFF34D399 : 0xFF818CF8;
            extractor.text(this.font, badge, badgeX, y + 5, badgeColor, false);
            int badgeW = this.font.width(badge);

            String cost = isCustom ? "+" + e.getAnvilCostNumeric() + " liv." : "Vanilla";
            String costText = "Costo: " + cost;
            int costW = this.font.width(costText);
            int costX = (cardX + cardWidth - btnW - 6) - costW - 6;
            extractor.text(this.font, costText, costX, y + 5, 0xFFFBBF24, false);

            int descStartX = Math.max(cardX + 235, badgeX + badgeW + 12);
            int availDescW = costX - descStartX - 8;
            if (availDescW > 30) {
                String desc = trimToWidth(e.description != null ? e.description : "", availDescW);
                extractor.text(this.font, desc, descStartX, y + 5, 0xFFA1A1AA, false);
            }
        }

        this.hoveredCatalogEntry = hoveredEntry;
    }

    private void renderEnchantmentTooltip(GuiGraphicsExtractor extractor, EnchantmentEntry e, int mouseX, int mouseY) {
        List<String> conflicts = EnchantmentDatabase.getResolvedConflicts(e.id);
        boolean hasConflicts = !conflicts.isEmpty();
        List<ItemStack> compItems = com.litness.optimizer.tooltip.LitnessTooltipHandler.getOrderedItemStacks(e.applicableTo);
        boolean hasComp = !compItems.isEmpty();

        Collection<String> activeSet = isCreatingSet ? creatingSetEnchants : simulatorSelectedEnchants;
        String activeConflict = getConflictWithCollection(e, activeSet);
        boolean hasActiveConflict = (activeConflict != null);

        int tipW = Math.min(340, this.width - 24);
        int innerW = tipW - 16;

        boolean isCustom = "custom".equalsIgnoreCase(e.category);
        String title = (hasActiveConflict ? "§e⚠️ " : "") + e.name + " (Livello Massimo: " + AnvilOptimizer.toRoman(e.maxLevel) + ")";
        String sub = (isCustom ? "Custom Litness (" + (e.rarity != null ? e.rarity : "Speciale") + ")" : "Incantesimo Vanilla") 
                + " • Costo: " + (isCustom ? "+" + e.getAnvilCostNumeric() + " liv." : "Standard");
        String desc = (e.description != null && !e.description.isEmpty()) ? e.description : "Nessuna descrizione.";

        int descH = this.font.wordWrapHeight(Component.literal(desc), innerW);

        String activeWarnText = hasActiveConflict ? "§e⚠️ ATTENZIONE: Incompatibile con '§c" + activeConflict + "§e' già nel set!" : null;
        int activeWarnH = hasActiveConflict ? this.font.wordWrapHeight(Component.literal(activeWarnText), innerW) : 0;

        String confText = hasConflicts ? "§c⚔ Conflitti: §f" + String.join("§7, §f", conflicts) : null;
        int confH = hasConflicts ? this.font.wordWrapHeight(Component.literal(confText), innerW) : 0;

        int tipH = 34 + (hasActiveConflict ? activeWarnH + 6 : 0) + descH + (hasConflicts ? 8 + confH : 0) + (hasComp ? 26 : 0) + 8;

        int tipX = mouseX + 12;
        if (tipX + tipW > this.width - 8) {
            tipX = mouseX - tipW - 12;
            if (tipX < 8) tipX = Math.max(8, this.width - tipW - 8);
        }
        int tipY = Math.max(8, Math.min(mouseY - 8, this.height - tipH - 8));

        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xFA0E0E18);
        int topStripe = hasActiveConflict ? 0xFFF59E0B : (isCustom ? 0xFF10B981 : 0xFF6366F1);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + 1, topStripe);
        extractor.outline(tipX, tipY, tipW, tipH, hasActiveConflict ? 0xFFF59E0B : 0xFF2A2A3E);

        extractor.text(this.font, title, tipX + 8, tipY + 6, hasActiveConflict ? 0xFFFBBF24 : 0xFFFFFFFF, false);
        extractor.text(this.font, sub, tipX + 8, tipY + 18, isCustom ? 0xFF34D399 : 0xFF818CF8, false);

        int currentY = tipY + 31;
        if (hasActiveConflict) {
            extractor.textWithWordWrap(this.font, Component.literal(activeWarnText), tipX + 8, currentY, innerW, 0xFFFBBF24);
            currentY += activeWarnH + 6;
        }

        extractor.textWithWordWrap(this.font, Component.literal(desc), tipX + 8, currentY, innerW, 0xFFA1A1AA);
        currentY += descH + 8;

        if (hasConflicts) {
            extractor.textWithWordWrap(this.font, Component.literal(confText), tipX + 8, currentY, innerW, 0xFFF87171);
            currentY += confH + 8;
        }

        if (hasComp) {
            extractor.text(this.font, "Compatibile:", tipX + 8, currentY + 3, 0xFF71717A, false);
            int itemX = tipX + 8 + this.font.width("Compatibile: ") + 3;
            int maxIcons = Math.min(compItems.size(), 12);
            for (int i = 0; i < maxIcons; i++) {
                ItemStack itemStack = compItems.get(i);
                extractor.fill(itemX, currentY, itemX + 16, currentY + 16, 0x55111118);
                extractor.outline(itemX, currentY, 16, 16, 0x666366F1);
                extractor.fakeItem(itemStack, itemX, currentY);
                itemX += 17;
            }
        }
    }

    private ItemStack getItemStackForType(String type) {
        if (type == null) return new ItemStack(Items.ENCHANTED_BOOK);
        switch (type.toLowerCase()) {
            case "sword": return new ItemStack(Items.DIAMOND_SWORD);
            case "pick":
            case "pickaxe": return new ItemStack(Items.DIAMOND_PICKAXE);
            case "helmet": return new ItemStack(Items.DIAMOND_HELMET);
            case "chest": return new ItemStack(Items.DIAMOND_CHESTPLATE);
            case "legs": return new ItemStack(Items.DIAMOND_LEGGINGS);
            case "boots": return new ItemStack(Items.DIAMOND_BOOTS);
            case "axe": return new ItemStack(Items.DIAMOND_AXE);
            case "shovel": return new ItemStack(Items.DIAMOND_SHOVEL);
            case "bow": return new ItemStack(Items.BOW);
            case "crossbow": return new ItemStack(Items.CROSSBOW);
            case "mace": return new ItemStack(Items.MACE);
            case "trident": return new ItemStack(Items.TRIDENT);
            default: return new ItemStack(Items.ENCHANTED_BOOK);
        }
    }

    private void renderCalculatorTab(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int panelWidth = Math.min(550, this.width - 20);
        int panelX = (this.width - panelWidth) / 2;

        if (simView == SimView.GUIDE) {
            renderGuideView(extractor, panelX, panelWidth, mouseX, mouseY);
        } else {
            renderSavedSetsView(extractor, panelX, panelWidth, mouseX, mouseY);
        }
    }

    private void renderGuideView(GuiGraphicsExtractor extractor, int panelX, int panelWidth, int mouseX, int mouseY) {
        int bannerY = 72;

        if (currentSolution != null && !currentSolution.steps.isEmpty()) {
            int bannerW = panelWidth - 92;
            // BANNER RIEPILOGO COSTI IN CIMA SENZA ALCUNA SOVRAPPOSIZIONE
            extractor.fill(panelX, bannerY, panelX + bannerW, bannerY + 20, 0xEE141422);
            extractor.fill(panelX, bannerY, panelX + 3, bannerY + 20, 0xFF10B981);
            extractor.outline(panelX, bannerY, bannerW, 20, 0xFF2A2A3E);

            int curBannerX = panelX + 10;
            String costTxt = "💰 " + currentSolution.totalCost + " liv.";
            extractor.text(this.font, costTxt, curBannerX, bannerY + 6, 0xFF34D399, false);
            curBannerX += this.font.width(costTxt) + 16;

            boolean safe = currentSolution.peakCost <= 39;
            String peakTxt = "⚡ Picco: " + currentSolution.peakCost + "/39 liv. " + (safe ? "✔" : "✖");
            extractor.text(this.font, peakTxt, curBannerX, bannerY + 6, safe ? 0xFF818CF8 : 0xFFEF4444, false);

            int totalSteps = currentSolution.steps.size();
            int stepsPerPage = 4;
            int maxPages = Math.max(1, (int) Math.ceil((double) totalSteps / stepsPerPage));
            if (guideStepPage >= maxPages) guideStepPage = maxPages - 1;
            if (guideStepPage < 0) guideStepPage = 0;

            String pagInfo = "Passi " + (guideStepPage * stepsPerPage + 1) + "-" + Math.min((guideStepPage + 1) * stepsPerPage, totalSteps) 
                    + " di " + totalSteps;
            int pagInfoX = panelX + bannerW - this.font.width(pagInfo) - 10;
            extractor.text(this.font, pagInfo, Math.max(curBannerX + this.font.width(peakTxt) + 16, pagInfoX), bannerY + 6, 0xFFA5B4FC, false);

            if (btnCopyGuide != null) {
                btnCopyGuide.visible = true;
                btnCopyGuide.active = true;
                btnCopyGuide.setPosition(panelX + panelWidth - 88, bannerY + 1);
            }

            if (btnPrevStep != null && btnNextStep != null) {
                btnPrevStep.visible = true;
                btnNextStep.visible = true;
                btnPrevStep.active = guideStepPage > 0;
                btnNextStep.active = guideStepPage < maxPages - 1;
            }

            int stepStartY = bannerY + 24;
            int cardH = 46;
            int cardGap = 4;

            int startStep = guideStepPage * stepsPerPage;
            int endStep = Math.min(startStep + stepsPerPage, totalSteps);

            HoveredBookSlot hoveredSlot = null;

            for (int i = startStep; i < endStep; i++) {
                MergeStep step = currentSolution.steps.get(i);
                int y = stepStartY + (i - startStep) * (cardH + cardGap);

                int stripeColor = step.isBookToBook ? 0xFF6366F1 : 0xFF10B981;
                extractor.fill(panelX, y, panelX + panelWidth, y + cardH, 0xEE12121E);
                extractor.fill(panelX, y, panelX + 3, y + cardH, stripeColor);
                extractor.outline(panelX, y, panelWidth, cardH, 0xFF2A2A3E);

                // HEADER CARD
                String badge = "PASSO " + (i + 1) + "/" + totalSteps;
                int badgeCol = step.isBookToBook ? 0xFF818CF8 : 0xFF34D399;
                extractor.text(this.font, "[" + badge + "]", panelX + 8, y + 4, badgeCol, false);

                String opType = step.isBookToBook ? "🔨 Fusione Libri nell'Incudine" : "⚔ Tempra Libro su " + step.leftLabel;
                extractor.text(this.font, opType, panelX + 80, y + 4, 0xFFFFFFFF, false);

                String costText = "💰 Costo: " + step.cost + " liv. XP";
                int costW = this.font.width(costText);
                extractor.text(this.font, costText, panelX + panelWidth - costW - 8, y + 4, 0xFFFBBF24, false);

                // 3 SLOT PROFESSIONALI CON IMMAGINI DEGLI OGGETTI (ANVIL WORKBENCH)
                int slotY = y + 16;
                int slotW = 185;

                // SLOT 1 (Sinistra)
                extractor.fill(panelX + 8, slotY, panelX + 8 + slotW, slotY + 26, 0xCC181826);
                extractor.outline(panelX + 8, slotY, slotW, 26, 0xFF35354E);

                // Render dell'icona reale dell'item/libro di Minecraft
                ItemStack slot1Stack = step.isBookToBook ? new ItemStack(Items.ENCHANTED_BOOK) : getItemStackForType(ITEM_KEYS[selectedItemIndex]);
                extractor.fakeItem(slot1Stack, panelX + 12, slotY + 5);

                String leftHeader = step.isBookToBook ? "Slot 1 (Libro):" : "Slot 1 (Oggetto):";
                extractor.text(this.font, leftHeader, panelX + 32, slotY + 4, 0xFF93C5FD, false);
                if (step.isBookToBook && InventoryTracker.hasInInventory(step.leftLabel)) {
                    String invBadge = "§a✔ In Inventario";
                    int bW = this.font.width(invBadge);
                    extractor.text(this.font, invBadge, panelX + 8 + slotW - bW - 4, slotY + 4, 0xFF34D399, false);
                }
                String leftName = trimToWidth(step.leftLabel, slotW - 36);
                extractor.text(this.font, leftName, panelX + 32, slotY + 14, 0xFFFFFFFF, false);

                // SEGNO PIÙ CENTRALE
                extractor.centeredText(this.font, "+", panelX + 8 + slotW + 10, slotY + 9, 0xFFFBBF24);

                // SLOT 2 (Destra)
                int slot2X = panelX + 8 + slotW + 20;
                extractor.fill(slot2X, slotY, slot2X + slotW, slotY + 26, 0xCC181826);
                extractor.outline(slot2X, slotY, slotW, 26, 0xFF35354E);

                // Render del libro sacrificato
                extractor.fakeItem(new ItemStack(Items.ENCHANTED_BOOK), slot2X + 4, slotY + 5);

                extractor.text(this.font, "Slot 2 (Libro):", slot2X + 24, slotY + 4, 0xFFF472B6, false);
                if (InventoryTracker.hasInInventory(step.rightLabel)) {
                    String invBadge = "§a✔ In Inventario";
                    int bW = this.font.width(invBadge);
                    extractor.text(this.font, invBadge, slot2X + slotW - bW - 4, slotY + 4, 0xFF34D399, false);
                }
                String rightName = trimToWidth(step.rightLabel, slotW - 28);
                extractor.text(this.font, rightName, slot2X + 24, slotY + 14, 0xFFFFFFFF, false);

                // FRECCIA RISULTATO
                extractor.centeredText(this.font, "➔", slot2X + slotW + 10, slotY + 9, 0xFF34D399);

                // RISULTATO (Output Slot)
                int resX = slot2X + slotW + 20;
                int resW = panelWidth - (resX - panelX) - 8;
                extractor.fill(resX, slotY, resX + resW, slotY + 26, 0xCC12241A);
                extractor.outline(resX, slotY, resW, 26, 0xFF1E6638);

                // Render dell'oggetto risultante
                ItemStack resStack = step.isBookToBook ? new ItemStack(Items.ENCHANTED_BOOK) : getItemStackForType(ITEM_KEYS[selectedItemIndex]);
                extractor.fakeItem(resStack, resX + 4, slotY + 5);

                extractor.text(this.font, "Risultato:", resX + 24, slotY + 4, 0xFF34D399, false);
                String resName = trimToWidth(step.resultLabel, resW - 28);
                extractor.text(this.font, resName, resX + 24, slotY + 14, 0xFFE4E4E7, false);

                // Hover sullo Slot 1 (Sinistra)
                if (mouseX >= panelX + 8 && mouseX <= panelX + 8 + slotW && mouseY >= slotY && mouseY <= slotY + 26) {
                    hoveredSlot = new HoveredBookSlot(
                        step.isBookToBook,
                        step.isBookToBook ? "Slot 1 (Libro Incantato)" : "Slot 1: " + step.leftLabel,
                        step.leftLabel,
                        step.leftWork,
                        step.leftEnchants
                    );
                }

                // Hover sullo Slot 2 (Destra - sempre libro)
                if (mouseX >= slot2X && mouseX <= slot2X + slotW && mouseY >= slotY && mouseY <= slotY + 26) {
                    hoveredSlot = new HoveredBookSlot(
                        true,
                        "Slot 2 (Libro da Unire)",
                        step.rightLabel,
                        step.rightWork,
                        step.rightEnchants
                    );
                }

                // Hover sul Risultato (Output)
                if (mouseX >= resX && mouseX <= resX + resW && mouseY >= slotY && mouseY <= slotY + 26) {
                    hoveredSlot = new HoveredBookSlot(
                        step.isBookToBook,
                        step.isBookToBook ? "Risultato (Libro Unito)" : "Risultato: " + step.resultLabel,
                        step.resultLabel,
                        step.resultWork,
                        step.resultEnchants
                    );
                }
            }

            this.hoveredGuideSlot = hoveredSlot;
        } else {
            int bannerW = panelWidth;
            extractor.fill(panelX, bannerY, panelX + bannerW, bannerY + 24, 0xEE141422);
            extractor.outline(panelX, bannerY, bannerW, 24, 0xFF2A2A3E);
            extractor.text(this.font, statusMessage.isEmpty() ? "Nessun incantesimo selezionato." : statusMessage, panelX + 10, bannerY + 8, 0xFFA5B4FC, false);
            if (btnPrevStep != null) btnPrevStep.visible = false;
            if (btnNextStep != null) btnNextStep.visible = false;
            if (btnCopyGuide != null) {
                btnCopyGuide.visible = false;
                btnCopyGuide.active = false;
            }
        }
    }

    private void renderGuideSlotTooltip(GuiGraphicsExtractor extractor, HoveredBookSlot slot, int mouseX, int mouseY) {
        if (slot == null) return;

        if (slot.enchants.isEmpty()) {
            String title = slot.slotTitle;
            String content = slot.label;
            String pen = "Penalità lavoro: +" + slot.work + " liv.";
            int tipW = Math.max(160, Math.max(this.font.width(content), this.font.width(pen)) + 16);
            int tipH = 44;
            int tipX = Math.min(mouseX + 10, this.width - tipW - 8);
            int tipY = Math.max(10, mouseY - 20);
            extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xFA0E1510);
            extractor.fill(tipX, tipY, tipX + tipW, tipY + 1, 0xFF10B981);
            extractor.outline(tipX, tipY, tipW, tipH, 0xFF227744);
            extractor.text(this.font, title, tipX + 8, tipY + 6, 0xFF34D399, false);
            extractor.text(this.font, content, tipX + 8, tipY + 18, 0xFFFFFFFF, false);
            extractor.text(this.font, pen, tipX + 8, tipY + 29, 0xFFFBBF24, false);
            return;
        }

        if (slot.enchants.size() == 1) {
            EnchantApplied ea = slot.enchants.get(0);
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
            if (ee == null) {
                ee = new EnchantmentEntry();
                ee.id = ea.id;
                ee.name = ea.id;
                ee.category = "vanilla";
                ee.maxLevel = 5;
            }

            boolean isCustom = "custom".equalsIgnoreCase(ee.category);
            String title = ee.name + " " + AnvilOptimizer.toRoman(ea.level);
            String sub = (isCustom ? "Custom Litness (" + (ee.rarity != null ? ee.rarity : "Speciale") + ")" : "Incantesimo Vanilla")
                    + " • Costo: " + (isCustom ? "+" + ee.getAnvilCostNumeric() + " liv." : "Standard");
            String penTxt = "Penalità lavoro libro: +" + slot.work + " liv.";
            String desc = (ee.description != null && !ee.description.isEmpty()) ? ee.description : "Nessuna descrizione.";

            List<String> conflicts = EnchantmentDatabase.getResolvedConflicts(ee.id);
            boolean hasConflicts = !conflicts.isEmpty();
            String confText = hasConflicts ? "§c⚔ Conflitti: §f" + String.join("§7, §f", conflicts) : null;

            int tipW = Math.min(320, this.width - 24);
            int innerW = tipW - 16;
            int descH = this.font.wordWrapHeight(Component.literal(desc), innerW);
            int confH = hasConflicts ? this.font.wordWrapHeight(Component.literal(confText), innerW) : 0;
            int tipH = 46 + descH + (hasConflicts ? 6 + confH : 0) + 8;

            int tipX = mouseX + 12;
            if (tipX + tipW > this.width - 8) {
                tipX = mouseX - tipW - 12;
                if (tipX < 8) tipX = Math.max(8, this.width - tipW - 8);
            }
            int tipY = Math.max(8, Math.min(mouseY - 8, this.height - tipH - 8));

            extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xFA0E1510);
            extractor.fill(tipX, tipY, tipX + tipW, tipY + 1, isCustom ? 0xFF10B981 : 0xFF6366F1);
            extractor.outline(tipX, tipY, tipW, tipH, 0xFF227744);

            extractor.text(this.font, title, tipX + 8, tipY + 6, 0xFF34D399, false);
            extractor.text(this.font, sub, tipX + 8, tipY + 18, isCustom ? 0xFF86EFAC : 0xFF93C5FD, false);
            extractor.text(this.font, penTxt, tipX + 8, tipY + 29, 0xFFFBBF24, false);

            int currentY = tipY + 42;
            extractor.textWithWordWrap(this.font, Component.literal(desc), tipX + 8, currentY, innerW, 0xFFFFFFFF);
            currentY += descH + 6;

            if (hasConflicts) {
                extractor.textWithWordWrap(this.font, Component.literal(confText), tipX + 8, currentY, innerW, 0xFFF87171);
            }
            return;
        }

        // Più incantesimi uniti sul libro
        int tipW = Math.min(330, this.width - 24);
        int innerW = tipW - 16;
        String headerTitle = "📖 Libro Incantato (" + slot.enchants.size() + " Incantesimi)";
        String penTxt = "Penalità lavoro: +" + slot.work + " liv.";

        int calcH = 34;
        List<Integer> descHeights = new ArrayList<>();
        for (EnchantApplied ea : slot.enchants) {
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
            calcH += 12;
            String d = (ee != null && ee.description != null && !ee.description.isEmpty()) ? ee.description : null;
            if (d != null) {
                int dh = this.font.wordWrapHeight(Component.literal(d), innerW - 8);
                descHeights.add(dh);
                calcH += dh + 4;
            } else {
                descHeights.add(0);
            }
            calcH += 4;
        }
        calcH += 6;

        int tipH = Math.min(calcH, this.height - 20);
        int tipX = mouseX + 12;
        if (tipX + tipW > this.width - 8) {
            tipX = mouseX - tipW - 12;
            if (tipX < 8) tipX = Math.max(8, this.width - tipW - 8);
        }
        int tipY = Math.max(8, Math.min(mouseY - 8, this.height - tipH - 8));

        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xFA0E1510);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + 1, 0xFF10B981);
        extractor.outline(tipX, tipY, tipW, tipH, 0xFF227744);

        extractor.text(this.font, headerTitle, tipX + 8, tipY + 6, 0xFF34D399, false);
        extractor.text(this.font, penTxt, tipX + 8, tipY + 18, 0xFFFBBF24, false);
        extractor.fill(tipX + 6, tipY + 29, tipX + tipW - 6, tipY + 30, 0xFF225533);

        int curY = tipY + 34;
        for (int i = 0; i < slot.enchants.size(); i++) {
            if (curY >= tipY + tipH - 12) break;
            EnchantApplied ea = slot.enchants.get(i);
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
            boolean isCustom = ee != null && "custom".equalsIgnoreCase(ee.category);
            String nameText = "• " + (ee != null ? ee.name : ea.id) + " " + AnvilOptimizer.toRoman(ea.level) 
                    + " §7(" + (isCustom ? ee.rarity : "Vanilla") + ")";
            extractor.text(this.font, nameText, tipX + 8, curY, isCustom ? 0xFF86EFAC : 0xFF93C5FD, false);
            curY += 11;

            String d = (ee != null && ee.description != null && !ee.description.isEmpty()) ? ee.description : null;
            if (d != null) {
                int dh = descHeights.get(i);
                extractor.textWithWordWrap(this.font, Component.literal(d), tipX + 14, curY, innerW - 8, 0xFFD1D5DB);
                curY += dh + 4;
            }
            curY += 2;
        }
    }

    public static List<EnchantApplied> parseEnchantmentsFromLabel(String label) {
        List<EnchantApplied> list = new ArrayList<>();
        if (label == null || label.trim().isEmpty()) return list;
        String[] parts = label.split("\\+");
        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) continue;
            String name = p;
            int lvl = 1;
            int lastSpace = p.lastIndexOf(' ');
            if (lastSpace > 0) {
                String possibleRoman = p.substring(lastSpace + 1).trim();
                int parsed = parseRomanOrInt(possibleRoman);
                if (parsed > 0) {
                    lvl = parsed;
                    name = p.substring(0, lastSpace).trim();
                }
            }
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(name);
            if (ee != null) {
                list.add(new EnchantApplied(ee.id, lvl));
            }
        }
        return list;
    }

    private static int parseRomanOrInt(String s) {
        if (s == null) return 0;
        s = s.trim().toUpperCase();
        switch (s) {
            case "I": case "1": return 1;
            case "II": case "2": return 2;
            case "III": case "3": return 3;
            case "IV": case "4": return 4;
            case "V": case "5": return 5;
            case "VI": case "6": return 6;
            case "VII": case "7": return 7;
            case "VIII": case "8": return 8;
            case "IX": case "9": return 9;
            case "X": case "10": return 10;
            default:
                try { return Integer.parseInt(s); } catch (Exception ignored) { return 0; }
        }
    }

    private String trimToWidth(String s, int maxWidth) {
        if (s == null) return "";
        if (this.font.width(s) <= maxWidth) return s;
        while (s.length() > 3 && this.font.width(s + "...") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentTab == Tab.CATALOG) {
            int totalPages = Math.max(1, (int) Math.ceil((double) filteredEnchantments.size() / getItemsPerPage()));
            if (verticalAmount > 0 && catalogPage > 0) {
                catalogPage--;
                refreshCatalogButtons();
                return true;
            } else if (verticalAmount < 0 && catalogPage < totalPages - 1) {
                catalogPage++;
                refreshCatalogButtons();
                return true;
            }
        } else if (currentTab == Tab.CALCULATOR) {
            if (simView == SimView.GUIDE && currentSolution != null && !currentSolution.steps.isEmpty()) {
                int totalSteps = currentSolution.steps.size();
                int maxPages = Math.max(1, (int) Math.ceil((double) totalSteps / 4));
                if (verticalAmount > 0 && guideStepPage > 0) {
                    guideStepPage--;
                    return true;
                } else if (verticalAmount < 0 && guideStepPage < maxPages - 1) {
                    guideStepPage++;
                    return true;
                }
            } else if (simView == SimView.SETS) {
                if (isCreatingSet) {
                    int totalPages = Math.max(1, (int) Math.ceil((double) currentVisibleSelectionEnchants.size() / 10));
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
                } else {
                    int totalPages = Math.max(1, (int) Math.ceil((double) SavedSetsManager.getSavedSets().size() / 4));
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
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static boolean isShiftKeyDown() {
        try {
            long handle = Minecraft.getInstance().getWindow().handle();
            return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.parentScreen != null) {
            this.minecraft.setScreenAndShow(this.parentScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

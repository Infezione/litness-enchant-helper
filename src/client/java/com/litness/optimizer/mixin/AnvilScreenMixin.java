package com.litness.optimizer.mixin;

import com.litness.optimizer.calculator.AnvilOptimizer;
import com.litness.optimizer.calculator.AnvilOptimizer.BookInput;
import com.litness.optimizer.calculator.AnvilOptimizer.EnchantApplied;
import com.litness.optimizer.calculator.AnvilOptimizer.MergeStep;
import com.litness.optimizer.calculator.AnvilOptimizer.Solution;
import com.litness.optimizer.calculator.MaxabilityAnalyzer;
import com.litness.optimizer.data.ActiveSessionManager;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import com.litness.optimizer.data.EnchantmentDatabase.ItemEntry;
import com.litness.optimizer.data.InventoryTracker;
import com.litness.optimizer.data.ItemTypeHelper;
import com.litness.optimizer.data.LitnessI18n;
import com.litness.optimizer.data.ModConfig;
import com.litness.optimizer.data.SavedSetsManager;
import com.litness.optimizer.gui.LitnessOptimizerScreen;
import com.litness.optimizer.gui.AnvilTooltipState;
import com.litness.optimizer.tooltip.LitnessTooltipHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin extends ItemCombinerScreen<AnvilMenu> {

    @Shadow
    private EditBox name;

    private ItemStack lastExplicitEquipment = ItemStack.EMPTY;
    private Solution cachedSolution = null;
    private int lastSlot0Hash = 0;
    private int lastInvBooksHash = 0;
    private int lastQuickInvHash = 0;
    private List<EnchantApplied> cachedSlot0Enchants = new ArrayList<>();
    private String cachedItemKey = "sword";

    private static final java.util.concurrent.ExecutorService CALC_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LitnessAnvilCalc");
        t.setDaemon(true);
        return t;
    });
    private java.util.concurrent.Future<?> currentCalcTask = null;
    private final java.util.concurrent.atomic.AtomicInteger calcIdCounter = new java.util.concurrent.atomic.AtomicInteger(0);

    public AnvilScreenMixin(AnvilMenu menu, Inventory inventory, Component title, Identifier texture) {
        super(menu, inventory, title, texture);
    }

    private int getSidebarWidth() {
        int screenW = this.width;
        int anvilW = this.imageWidth; // 176
        int anvilX = this.leftPos;
        int leftAvail = anvilX - 8;
        int rightAvail = screenW - (anvilX + anvilW) - 8;
        int minAvail = Math.min(leftAvail, rightAvail);
        if (minAvail < 160) {
            return Math.max(132, minAvail);
        }
        return Math.min(175, minAvail);
    }

    private int getSidebarX() {
        int sideW = getSidebarWidth();
        int sideX = this.leftPos - sideW - 6;
        return Math.max(4, sideX);
    }

    private String trimToWidth(String s, int maxWidth) {
        if (s == null) return "";
        if (this.font.width(s) <= maxWidth) return s;
        while (s.length() > 3 && this.font.width(s + "...") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }

    @Inject(method = "subInit", at = @At("TAIL"), require = 0)
    private void litness_addOptimizerButton(CallbackInfo ci) {
        int sideW = getSidebarWidth();
        int sideX = getSidebarX();
        int sideY = this.topPos - 14;
        int sideH = this.imageHeight + 10;
        int btnY = sideY + sideH + 3;

        // 1. Tasto nella sidebar sotto l'helper stile Obsidian
        this.addRenderableWidget(new LitnessOptimizerScreen.SoftwareButton(sideX, btnY, sideW, 18, Component.literal("✦ Guida Completa [N]"), button -> {
            openOptimizer();
        }).setPrimary(true));

        // 2. Tasto rapido direttamente sopra l'incudine
        int topBtnW = 86;
        int topBtnX = this.leftPos + this.imageWidth - topBtnW;
        int topBtnY = this.topPos - 18;
        this.addRenderableWidget(new LitnessOptimizerScreen.SoftwareButton(topBtnX, topBtnY, topBtnW, 16, Component.literal("✦ Guida Passi"), button -> {
            openOptimizer();
        }));
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void litness_onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() == InputConstants.KEY_N && (name == null || !name.isFocused())) {
            openOptimizer();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractBackground", at = @At("TAIL"), require = 0)
    private void litness_renderSidebar(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            com.litness.optimizer.gui.AnvilTooltipState.clearHover();

            int sideW = getSidebarWidth();
            int sideX = getSidebarX();
            int sideY = this.topPos - 14;
            int sideH = this.imageHeight + 10;

            AnvilTooltipState.leftSideX = sideX;
            AnvilTooltipState.leftSideY = sideY;
            AnvilTooltipState.leftSideW = sideW;
            AnvilTooltipState.leftSideH = sideH;

            // Finestra Laterale Obsidian Minimal Stondata
            LitnessOptimizerScreen.drawRoundedRect(extractor, sideX, sideY, sideW, sideH, 8, 0xFA090B10);
            LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX, sideY, sideW, sideH, 8, 0xFF1E2334);

            // Barra accento blu elettrico Obsidian |
            LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 8, sideY + 7, 3, 13, 1, 0xFF3B82F6);
            extractor.text(this.font, "LITNESS HELPER", sideX + 16, sideY + 9, 0xFFFFFFFF, false);

            int infoBtnX = sideX + sideW - 18;
            int infoBtnY = sideY + 8;
            boolean isInfoHovered = mouseX >= infoBtnX - 3 && mouseX <= infoBtnX + 15 && mouseY >= infoBtnY - 2 && mouseY <= infoBtnY + 12;
            extractor.text(this.font, isInfoHovered ? "§9[?]" : "§8[?]", infoBtnX, infoBtnY, isInfoHovered ? 0xFF60A5FA : 0xFF64748B, false);
            if (isInfoHovered) {
                com.litness.optimizer.gui.AnvilTooltipState.hoveredInfoModal = true;
            }

            ItemStack slot0 = this.menu.getSlot(0).getItem();
            ItemStack slot1 = this.menu.getSlot(1).getItem();

            // Identifica l'equipaggiamento attivo da potenziare
            ItemStack activeEquipment = ItemStack.EMPTY;
            boolean equipInSlot0 = false;

            if (!slot0.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(slot0)) {
                activeEquipment = slot0;
                lastExplicitEquipment = slot0.copy();
                AnvilTooltipState.selectAndSaveEquipment(slot0);
                equipInSlot0 = true;
            } else if (!slot1.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(slot1)) {
                activeEquipment = slot1;
                lastExplicitEquipment = slot1.copy();
                AnvilTooltipState.selectAndSaveEquipment(slot1);
            } else if (!this.menu.getCarried().isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(this.menu.getCarried())) {
                activeEquipment = this.menu.getCarried();
                lastExplicitEquipment = activeEquipment.copy();
                AnvilTooltipState.selectAndSaveEquipment(activeEquipment);
            } else if (AnvilTooltipState.savedSelectedEquipment != null && !AnvilTooltipState.savedSelectedEquipment.isEmpty()) {
                ItemStack found = findMatchingEquipmentInInventory(AnvilTooltipState.savedSelectedEquipment);
                activeEquipment = !found.isEmpty() ? found : AnvilTooltipState.savedSelectedEquipment;
                lastExplicitEquipment = activeEquipment.copy();
            } else if (ActiveSessionManager.getSavedAnvilItemKey() != null) {
                String savedId = ActiveSessionManager.getSavedAnvilItemId();
                String savedKey = ActiveSessionManager.getSavedAnvilItemKey();
                ItemStack found = findEquipmentBySavedDetails(savedId, savedKey);
                if (!found.isEmpty()) {
                    activeEquipment = found;
                } else {
                    activeEquipment = createFallbackEquipment(savedId, savedKey, ActiveSessionManager.getSavedAnvilItemName(), ActiveSessionManager.getSavedAnvilItemPenalty());
                }
                if (!activeEquipment.isEmpty()) {
                    lastExplicitEquipment = activeEquipment.copy();
                    AnvilTooltipState.savedSelectedEquipment = activeEquipment.copy();
                }
            } else if (!lastExplicitEquipment.isEmpty()) {
                ItemStack found = findMatchingEquipmentInInventory(lastExplicitEquipment);
                activeEquipment = !found.isEmpty() ? found : lastExplicitEquipment;
            } else {
                // Se nessun oggetto è ancora stato inserito nello slot 0, controlliamo se c'è un set esplicitamente pinnato
                String pinnedId = ActiveSessionManager.getPinnedSetId();
                if (pinnedId != null && !pinnedId.isEmpty()) {
                    SavedSetsManager.CustomSet pinned = SavedSetsManager.getSetById(pinnedId);
                    if (pinned != null && pinned.itemKey != null) {
                        activeEquipment = findEquipmentByKeyInInventory(pinned.itemKey);
                        if (!activeEquipment.isEmpty()) {
                            lastExplicitEquipment = activeEquipment.copy();
                            AnvilTooltipState.savedSelectedEquipment = activeEquipment.copy();
                        }
                    }
                }
                // Se ancora nessun equip è stato salvato o aperto, auto-seleziona arma in mano o prima arma in inventario
                if (activeEquipment.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
                    ItemStack mainHand = this.minecraft.player.getMainHandItem();
                    if (!mainHand.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(mainHand)) {
                        activeEquipment = mainHand;
                        lastExplicitEquipment = activeEquipment.copy();
                        AnvilTooltipState.savedSelectedEquipment = activeEquipment.copy();
                    } else {
                        ItemStack any = findAnyEquipmentInInventory();
                        if (!any.isEmpty()) {
                            activeEquipment = any;
                            lastExplicitEquipment = activeEquipment.copy();
                            AnvilTooltipState.savedSelectedEquipment = activeEquipment.copy();
                        }
                    }
                }
            }

            if (activeEquipment.isEmpty()) {
                cachedSolution = null;
                AnvilTooltipState.cachedSolution = null;
            }
            AnvilTooltipState.explicitTargetEquip = activeEquipment;

            if (!activeEquipment.isEmpty()) {
                updateAutoCalculation(activeEquipment);

                String itemName = activeEquipment.getHoverName().getString();
                int penalty = activeEquipment.getOrDefault(DataComponents.REPAIR_COST, 0);

                // Box interno per l'oggetto (Obsidian Minimal Well)
                int wellY = sideY + 23;
                int wellH = 38;
                AnvilTooltipState.wellX = sideX + 6;
                AnvilTooltipState.wellY = wellY;
                AnvilTooltipState.wellW = sideW - 12;
                AnvilTooltipState.wellH = wellH;

                ItemStack carried = this.menu.getCarried();
                boolean carryingEnchantable = !carried.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(carried);
                boolean isWellHovered = (mouseX >= sideX + 6 && mouseX <= sideX + sideW - 6 && mouseY >= wellY && mouseY <= wellY + wellH);

                int wellBg = (isWellHovered && carryingEnchantable) ? 0xFF142436 : 0xFF131724;
                int wellBorder = (isWellHovered && carryingEnchantable) ? 0xFF38BDF8 : 0xFF1E2334;

                LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 6, wellY, sideW - 12, wellH, 6, wellBg);
                LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX + 6, wellY, sideW - 12, wellH, 6, wellBorder);

                if (carryingEnchantable && isWellHovered) {
                    extractor.fakeItem(carried, sideX + 9, wellY + 11);
                    extractor.text(this.font, "§b✦ Rilascia / Clicca", sideX + 30, wellY + 8, 0xFF67E8F9, false);
                    extractor.text(this.font, "§fper selezionare item", sideX + 30, wellY + 20, 0xFFE0F2FE, false);
                } else {
                    // Icona 3D dell'oggetto
                    extractor.fakeItem(activeEquipment, sideX + 9, wellY + 11);

                    // Nome oggetto + stato inventario se non è nello slot 0
                    String titleSuffix = equipInSlot0 ? "" : " §8[Inv]";
                    String trimmedName = trimToWidth(itemName + titleSuffix, sideW - 38);
                    extractor.text(this.font, trimmedName, sideX + 30, wellY + 4, 0xFFFFFFFF, false);

                    // Penalità di lavoro
                    if (penalty == 0) {
                        extractor.text(this.font, "Penalità: 0 liv.", sideX + 30, wellY + 15, 0xFF34D399, false);
                    } else {
                        extractor.text(this.font, "Penalità: +" + penalty + " liv.", sideX + 30, wellY + 15, 0xFFF59E0B, false);
                    }

                    // Analisi Maxabilità
                    com.litness.optimizer.calculator.MaxabilityAnalyzer.MaxabilityResult maxability =
                            com.litness.optimizer.calculator.MaxabilityAnalyzer.analyze(activeEquipment, cachedSlot0Enchants);
                    String maxBadge = trimToWidth(maxability.statusBadge, sideW - 36);
                    extractor.text(this.font, maxBadge, sideX + 30, wellY + 26, 0xFFFFFFFF, false);

                    if (isWellHovered) {
                        com.litness.optimizer.gui.AnvilTooltipState.hoveredMaxabilityResult = maxability;
                    }
                }

                int curY = wellY + wellH + 5;

                // Incantesimi già presenti sull'oggetto
                if (!cachedSlot0Enchants.isEmpty()) {
                    extractor.text(this.font, "Incanti attuali (" + cachedSlot0Enchants.size() + "):", sideX + 8, curY, 0xFF9CA3AF, false);
                    curY += 10;
                    int showMax = Math.min(cachedSlot0Enchants.size(), 2);
                    for (int i = 0; i < showMax; i++) {
                        EnchantApplied ea = cachedSlot0Enchants.get(i);
                        EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                        String eName = AnvilOptimizer.formatEnchantName(ee != null ? ee.name : ea.id, ea.level);
                        extractor.text(this.font, "• " + trimToWidth(eName, sideW - 22), sideX + 10, curY, 0xFF60A5FA, false);
                        curY += 9;
                    }
                    if (cachedSlot0Enchants.size() > 2) {
                        extractor.text(this.font, "  +" + (cachedSlot0Enchants.size() - 2) + " altri...", sideX + 10, curY, 0xFF64748B, false);
                        curY += 9;
                    }

                    if (mouseX >= sideX + 6 && mouseX <= sideX + sideW - 6 && mouseY > wellY + wellH && mouseY < curY) {
                        com.litness.optimizer.gui.AnvilTooltipState.hoveredCurrentEnchants = cachedSlot0Enchants;
                    }
                }

                // Card Prossima Mossa
                int cardBottom = sideY + sideH - 7;
                int moveY = Math.max(curY + 4, sideY + 60);
                int moveH = cardBottom - moveY;

                if (cachedSolution != null && !cachedSolution.steps.isEmpty()) {
                    MergeStep nextStep = cachedSolution.steps.get(0);
                    int totalSteps = cachedSolution.steps.size();

                    boolean isMoveHovered = mouseX >= sideX + 6 && mouseX <= sideX + sideW - 6 && mouseY >= moveY && mouseY <= moveY + moveH;
                    if (isMoveHovered) {
                        com.litness.optimizer.gui.AnvilTooltipState.hoveredNextStep = nextStep;
                    }

                    String actionName = ItemTypeHelper.getActionName(cachedItemKey);
                    String icon = ItemTypeHelper.getActionIcon(cachedItemKey);

                    if (nextStep.isBookToBook) {
                        boolean slot0HasLeft = AnvilTooltipState.matchesEnchantList(slot0, nextStep.leftEnchants);
                        boolean slot1HasRight = AnvilTooltipState.matchesEnchantList(slot1, nextStep.rightEnchants);
                        boolean slot0HasRight = AnvilTooltipState.matchesEnchantList(slot0, nextStep.rightEnchants);
                        boolean slot1HasLeft = AnvilTooltipState.matchesEnchantList(slot1, nextStep.leftEnchants);

                        boolean ready = (slot0HasLeft && slot1HasRight) || (slot0HasRight && slot1HasLeft);
                        boolean oneInserted = slot0HasLeft || slot1HasRight || slot0HasRight || slot1HasLeft;

                        int boxBg = ready ? 0xFF0D281E : (isMoveHovered ? 0xFF181D2E : 0xFF131724);
                        int boxBorder = ready ? 0xFF10B981 : (isMoveHovered ? 0xFF3B82F6 : 0xFF1E2334);
                        LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 6, moveY, sideW - 12, moveH, 6, boxBg);
                        LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX + 6, moveY, sideW - 12, moveH, 6, boxBorder);

                        String badge = ready ? "§a✔ " + LitnessI18n.t("ready_to_merge") + " (1/" + totalSteps + ")" : "UNISCI 2 LIBRI (1/" + totalSteps + ")";
                        int badgeColor = ready ? 0xFF34D399 : 0xFF60A5FA;
                        extractor.text(this.font, badge, sideX + 10, moveY + 5, badgeColor, false);

                        boolean slot0HasWeapon = !slot0.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(slot0);

                        if (ready) {
                            extractor.text(this.font, "§a• " + LitnessI18n.t("take_from_output"), sideX + 10, moveY + 18, 0xFF34D399, false);
                            extractor.text(this.font, LitnessI18n.t("cost_xp") + "§a" + nextStep.cost + LitnessI18n.t("levels") + " XP", sideX + 10, moveY + 43, 0xFF34D399, false);
                        } else if (slot0HasWeapon) {
                            extractor.text(this.font, "§e⚠ Togli l'oggetto dall'incudine!", sideX + 10, moveY + 18, 0xFFFBBF24, false);
                            extractor.text(this.font, "§7Unisci prima i 2 libri [1] e [2]", sideX + 10, moveY + 30, 0xFFE2E8F0, false);
                            extractor.text(this.font, LitnessI18n.t("cost_xp") + "§a" + nextStep.cost + LitnessI18n.t("levels") + " XP", sideX + 10, moveY + 42, 0xFF34D399, false);
                        } else if (oneInserted) {
                            extractor.text(this.font, "§7Slot 1: §a✔ Inserito", sideX + 10, moveY + 18, 0xFF34D399, false);
                            String missingLabel = (slot0HasLeft || slot1HasLeft) ? nextStep.rightLabel : nextStep.leftLabel;
                            extractor.text(this.font, "§7Slot 2: §d" + trimToWidth(missingLabel, sideW - 60), sideX + 10, moveY + 30, 0xFFE0E7FF, false);
                            extractor.text(this.font, LitnessI18n.t("cost_xp") + "§a" + nextStep.cost + LitnessI18n.t("levels") + " XP", sideX + 10, moveY + 42, 0xFF34D399, false);
                        } else {
                            String l1 = trimToWidth(nextStep.leftLabel, sideW - 55);
                            String l2 = trimToWidth(nextStep.rightLabel, sideW - 55);
                            extractor.text(this.font, "§7Slot 1: §b" + l1, sideX + 10, moveY + 18, 0xFF9CA3AF, false);
                            extractor.text(this.font, "§7Slot 2: §d" + l2, sideX + 10, moveY + 30, 0xFF9CA3AF, false);
                            extractor.text(this.font, LitnessI18n.t("cost_xp") + "§a" + nextStep.cost + LitnessI18n.t("levels") + " XP", sideX + 10, moveY + 42, 0xFF34D399, false);
                            if (moveH >= 65) {
                                extractor.text(this.font, "§9• " + LitnessI18n.t("put_both_books"), sideX + 10, moveY + 54, 0xFF60A5FA, false);
                            }
                        }
                    } else {
                        // Tempra/Applica libro a equipaggiamento
                        boolean hasEquipInSlot0 = equipInSlot0;
                        boolean hasBookInSlot1 = AnvilTooltipState.matchesEnchantList(slot1, nextStep.rightEnchants);
                        boolean ready = hasEquipInSlot0 && hasBookInSlot1;

                        int boxBg = ready ? 0xFF0D281E : (isMoveHovered ? 0xFF181D2E : 0xFF131724);
                        int boxBorder = ready ? 0xFF10B981 : (isMoveHovered ? 0xFF3B82F6 : 0xFF1E2334);
                        LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 6, moveY, sideW - 12, moveH, 6, boxBg);
                        LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX + 6, moveY, sideW - 12, moveH, 6, boxBorder);

                        String badge = ready ? "§a✔ " + LitnessI18n.t("ready_to_forge") + " (1/" + totalSteps + ")" : ((icon.isEmpty() ? "" : icon + " ") + actionName + " (1/" + totalSteps + ")");
                        extractor.text(this.font, badge, sideX + 10, moveY + 5, 0xFF34D399, false);

                        if (ready) {
                            extractor.text(this.font, "§a• " + LitnessI18n.t("take_from_output"), sideX + 10, moveY + 18, 0xFF34D399, false);
                            extractor.text(this.font, LitnessI18n.t("cost_xp") + "§a" + nextStep.cost + LitnessI18n.t("levels") + " XP", sideX + 10, moveY + 43, 0xFF34D399, false);
                        } else if (!hasEquipInSlot0) {
                            extractor.text(this.font, "§7Slot 1: §b" + trimToWidth(itemName, sideW - 55), sideX + 10, moveY + 18, 0xFFE0E7FF, false);
                            extractor.text(this.font, "§7Slot 2: §d" + trimToWidth(nextStep.rightLabel, sideW - 55), sideX + 10, moveY + 30, 0xFFE0E7FF, false);
                            extractor.text(this.font, LitnessI18n.t("cost_xp") + "§a" + nextStep.cost + LitnessI18n.t("levels") + " XP", sideX + 10, moveY + 42, 0xFF34D399, false);
                        } else {
                            extractor.text(this.font, "§7Slot 2 (Libro):", sideX + 10, moveY + 18, 0xFFE0E7FF, false);
                            String bName = trimToWidth("• " + nextStep.rightLabel, sideW - 20);
                            extractor.text(this.font, bName, sideX + 10, moveY + 30, 0xFF34D399, false);
                            extractor.text(this.font, LitnessI18n.t("cost_xp") + "§a" + nextStep.cost + LitnessI18n.t("levels") + " XP", sideX + 10, moveY + 42, 0xFF34D399, false);
                        }
                    }
                } else {
                    com.litness.optimizer.calculator.MaxabilityAnalyzer.MaxabilityResult maxability =
                            com.litness.optimizer.calculator.MaxabilityAnalyzer.analyze(activeEquipment, cachedSlot0Enchants);
                    LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 6, moveY, sideW - 12, moveH, 6, 0xFF131724);
                    LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX + 6, moveY, sideW - 12, moveH, 6, 0xFF1E2334);

                    if (maxability.status == com.litness.optimizer.calculator.MaxabilityAnalyzer.Status.SATURATED) {
                        extractor.text(this.font, "§c[!] TROPPO COSTOSO!", sideX + 10, moveY + 5, 0xFFEF4444, false);
                        extractor.text(this.font, "Penalità +" + penalty + " troppo alta,", sideX + 10, moveY + 18, 0xFFE2E8F0, false);
                        extractor.text(this.font, "l'incudine blocca i libri.", sideX + 10, moveY + 30, 0xFF94A3B8, false);
                        extractor.text(this.font, "Premi [N] per spiegazione", sideX + 10, moveY + 43, 0xFF60A5FA, false);
                    } else if (maxability.status == com.litness.optimizer.calculator.MaxabilityAnalyzer.Status.PARTIALLY_MAXABLE) {
                        extractor.text(this.font, "§e[!] ATTENZIONE PENALITÀ", sideX + 10, moveY + 5, 0xFFF59E0B, false);
                        extractor.text(this.font, "Puoi aggiungere ancora", sideX + 10, moveY + 18, 0xFFE2E8F0, false);
                        extractor.text(this.font, "fino a §a+" + maxability.maxAddable + " incanti §7con guida.", sideX + 10, moveY + 30, 0xFFFFFFFF, false);
                        extractor.text(this.font, "Premi [N] per simulare!", sideX + 10, moveY + 43, 0xFF60A5FA, false);
                    } else {
                        extractor.text(this.font, "ENCHANT HELPER", sideX + 10, moveY + 5, 0xFF94A3B8, false);
                        extractor.text(this.font, "Nessun libro compatibile", sideX + 10, moveY + 18, 0xFFE2E8F0, false);
                        extractor.text(this.font, "trovato nell'inventario.", sideX + 10, moveY + 30, 0xFF94A3B8, false);
                        extractor.text(this.font, "Premi [N] per simulare!", sideX + 10, moveY + 43, 0xFF60A5FA, false);
                    }
                }

                // Render Ghost Slot nello slot 0 dell'incudine se lo slot 0 è vuoto
                if (slot0.isEmpty()) {
                    net.minecraft.world.inventory.Slot s0 = this.menu.getSlot(0);
                    int slot0X = this.leftPos + (s0 != null ? s0.x : 27);
                    int slot0Y = this.topPos + (s0 != null ? s0.y : 47);

                    ItemStack ghostItem = activeEquipment;
                    String ghostName = activeEquipment.getHoverName().getString();
                    if (cachedSolution != null && !cachedSolution.steps.isEmpty() && cachedSolution.steps.get(0).isBookToBook) {
                        ghostItem = new ItemStack(Items.ENCHANTED_BOOK);
                        ghostName = cachedSolution.steps.get(0).leftLabel;
                    }

                    long time = System.currentTimeMillis();
                    double wave = (Math.sin(time / 260.0) + 1.0) / 2.0;
                    int borderAlpha = (int)(0x60 + wave * 0x80);
                    LitnessOptimizerScreen.drawRoundedOutline(extractor, slot0X - 1, slot0Y - 1, 18, 18, 4, (borderAlpha << 24) | 0x3B82F6);
                    int bgAlpha = (int)(0x15 + wave * 0x25);
                    LitnessOptimizerScreen.drawRoundedRect(extractor, slot0X, slot0Y, 16, 16, 3, (bgAlpha << 24) | 0x3B82F6);
                    extractor.fakeItem(ghostItem, slot0X, slot0Y);
                    int holoAlpha = (int)(0x40 + (1.0 - wave) * 0x40);
                    LitnessOptimizerScreen.drawRoundedRect(extractor, slot0X, slot0Y, 16, 16, 3, (holoAlpha << 24) | 0x090B10);

                    if (mouseX >= slot0X && mouseX < slot0X + 16 && mouseY >= slot0Y && mouseY < slot0Y + 16) {
                        com.litness.optimizer.gui.AnvilTooltipState.hoveredGhostSlotItemName = ghostName;
                    }
                }
            } else {
                // Nessun equipaggiamento trovato: stato vuoto
                this.cachedSolution = null;
                this.lastSlot0Hash = 0;
                this.lastInvBooksHash = 0;
                com.litness.optimizer.gui.AnvilTooltipState.cachedSolution = null;

                net.minecraft.world.inventory.Slot s0 = this.menu.getSlot(0);
                int slot0X = this.leftPos + (s0 != null ? s0.x : 27);
                int slot0Y = this.topPos + (s0 != null ? s0.y : 47);

                ItemStack ghostItem = getSuggestedItem();
                String ghostName = ItemTypeHelper.getSuggestedName(ghostItem);

                long time = System.currentTimeMillis();
                double wave = (Math.sin(time / 260.0) + 1.0) / 2.0;
                int borderAlpha = (int)(0x60 + wave * 0x80);
                LitnessOptimizerScreen.drawRoundedOutline(extractor, slot0X - 1, slot0Y - 1, 18, 18, 4, (borderAlpha << 24) | 0x3B82F6);
                int bgAlpha = (int)(0x15 + wave * 0x25);
                LitnessOptimizerScreen.drawRoundedRect(extractor, slot0X, slot0Y, 16, 16, 3, (bgAlpha << 24) | 0x3B82F6);
                extractor.fakeItem(ghostItem, slot0X, slot0Y);
                int holoAlpha = (int)(0x40 + (1.0 - wave) * 0x40);
                LitnessOptimizerScreen.drawRoundedRect(extractor, slot0X, slot0Y, 16, 16, 3, (holoAlpha << 24) | 0x090B10);

                if (mouseX >= slot0X && mouseX < slot0X + 16 && mouseY >= slot0Y && mouseY < slot0Y + 16) {
                    com.litness.optimizer.gui.AnvilTooltipState.hoveredGhostSlotItemName = ghostName;
                }

                int cardY = sideY + 23;
                int cardH = sideH - 35;
                AnvilTooltipState.wellX = sideX + 6;
                AnvilTooltipState.wellY = cardY;
                AnvilTooltipState.wellW = sideW - 12;
                AnvilTooltipState.wellH = cardH;

                ItemStack carried = this.menu.getCarried();
                boolean carryingEnchantable = !carried.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(carried);
                boolean isWellHovered = (mouseX >= sideX + 6 && mouseX <= sideX + sideW - 6 && mouseY >= cardY && mouseY <= cardY + cardH);

                int cardBg = (isWellHovered && carryingEnchantable) ? 0xFF142436 : 0xFF131724;
                int cardBorder = (isWellHovered && carryingEnchantable) ? 0xFF38BDF8 : 0xFF1E2334;

                LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 6, cardY, sideW - 12, cardH, 6, cardBg);
                LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX + 6, cardY, sideW - 12, cardH, 6, cardBorder);

                int midX = sideX + (sideW / 2);
                if (carryingEnchantable && isWellHovered) {
                    extractor.fakeItem(carried, midX - 8, cardY + 18);
                    extractor.centeredText(this.font, "✦ Rilascia Quest'Arma", midX, cardY + 42, 0xFF38BDF8);
                    extractor.centeredText(this.font, "Rilascia o clicca qui", midX, cardY + 58, 0xFFE0F2FE);
                    extractor.centeredText(this.font, "per selezionarla", midX, cardY + 70, 0xFF94A3B8);
                } else {
                    extractor.fakeItem(ghostItem, midX - 8, cardY + 18);
                    extractor.centeredText(this.font, "Slot 1 vuoto", midX, cardY + 42, 0xFFFFFFFF);
                    extractor.centeredText(this.font, "Inserisci o trascina l'item", midX, cardY + 58, 0xFF60A5FA);
                    extractor.centeredText(this.font, "desiderato qui per", midX, cardY + 70, 0xFF60A5FA);
                    extractor.centeredText(this.font, "iniziare la guida.", midX, cardY + 82, 0xFF94A3B8);
                }

                int pillY = cardY + cardH - 22;
                LitnessOptimizerScreen.drawRoundedRect(extractor, sideX + 12, pillY, sideW - 24, 16, 4, 0xFF1E2334);
                LitnessOptimizerScreen.drawRoundedOutline(extractor, sideX + 12, pillY, sideW - 24, 16, 4, 0xFF3B82F6);
                extractor.centeredText(this.font, "Premi [N] per il tool", midX, pillY + 4, 0xFF60A5FA);
            }

            // Renderizza il pannello laterale destro con checklist e statistiche
            litness_renderRightPanel(extractor, activeEquipment, mouseX, mouseY, delta);
        } catch (Throwable t) {
            // Silenzia
        }
    }

    public static class ChecklistEntry {
        public final EnchantmentEntry ee;
        public final int targetLevel;
        public final int status; // 1: ON_EQUIP, 2: READY, 3: WARNING_PENALTY, 4: MISSING
        public final int bookPenalty;

        public ChecklistEntry(EnchantmentEntry ee, int targetLevel, int status, int bookPenalty) {
            this.ee = ee;
            this.targetLevel = targetLevel;
            this.status = status;
            this.bookPenalty = bookPenalty;
        }
    }

    private void litness_renderRightPanel(GuiGraphicsExtractor extractor, ItemStack activeEquipment, int mouseX, int mouseY, float delta) {
        int rightX = this.leftPos + this.imageWidth + 6;
        int rightY = this.topPos - 14;
        int maxW = this.width - rightX - 6;
        int rightW = Math.min(getSidebarWidth(), maxW);
        int rightH = this.imageHeight + 10;

        if (rightW < 90) {
            AnvilTooltipState.rightSideW = 0;
            AnvilTooltipState.calc12BtnVisible = false;
            return;
        }

        AnvilTooltipState.rightSideX = rightX;
        AnvilTooltipState.rightSideY = rightY;
        AnvilTooltipState.rightSideW = rightW;
        AnvilTooltipState.rightSideH = rightH;

        // Finestra Laterale Obsidian Minimal Stondata Scura
        LitnessOptimizerScreen.drawRoundedRect(extractor, rightX, rightY, rightW, rightH, 8, 0xFA090B10);
        LitnessOptimizerScreen.drawRoundedOutline(extractor, rightX, rightY, rightW, rightH, 8, 0xFF1E2334);

        if (activeEquipment == null || activeEquipment.isEmpty()) {
            AnvilTooltipState.calc12BtnVisible = false;
            // Stato vuoto: Invito alla selezione
            LitnessOptimizerScreen.drawRoundedRect(extractor, rightX + 8, rightY + 7, 3, 13, 1, 0xFF64748B);
            extractor.text(this.font, "STATO FORGIA", rightX + 16, rightY + 9, 0xFF94A3B8, false);

            int cardY = rightY + 23;
            int cardH = rightH - 35;
            LitnessOptimizerScreen.drawRoundedRect(extractor, rightX + 6, cardY, rightW - 12, cardH, 6, 0xFF131724);
            LitnessOptimizerScreen.drawRoundedOutline(extractor, rightX + 6, cardY, rightW - 12, cardH, 6, 0xFF1E2334);

            int midX = rightX + (rightW / 2);
            extractor.fakeItem(new ItemStack(Items.ENCHANTED_BOOK), midX - 8, cardY + 18);
            extractor.centeredText(this.font, "Nessun Equip", midX, cardY + 42, 0xFFFFFFFF);
            extractor.centeredText(this.font, "Trascina o inserisci", midX, cardY + 58, 0xFF60A5FA);
            extractor.centeredText(this.font, "un'arma per vedere", midX, cardY + 70, 0xFF94A3B8);
            extractor.centeredText(this.font, "la checklist libri.", midX, cardY + 82, 0xFF94A3B8);
            return;
        }

        String itemKey = ItemTypeHelper.detectItemKey(activeEquipment);
        if (itemKey == null || itemKey.isEmpty()) itemKey = "sword";

        // Determina gli incantesimi target (priorità: Incanti sull'arma -> Set Pinnato/Salvato -> Candidati consigliati fino a 12)
        List<String> targetEnchantIds = new ArrayList<>();

        // 1. Tutti gli incanti già presenti sull'arma
        for (EnchantApplied ea : cachedSlot0Enchants) {
            if (!targetEnchantIds.contains(ea.id)) targetEnchantIds.add(ea.id);
        }

        // 2. Set pinnato o salvato (se compatibili e non in conflitto)
        String pinnedId = ActiveSessionManager.getPinnedSetId();
        SavedSetsManager.CustomSet pinned = (pinnedId != null) ? SavedSetsManager.getSetById(pinnedId) : null;
        List<String> preferredEnchants = new ArrayList<>();
        if (pinned != null && pinned.itemKey != null && pinned.itemKey.equalsIgnoreCase(itemKey) && pinned.enchantments != null) {
            preferredEnchants.addAll(pinned.enchantments);
        } else {
            List<SavedSetsManager.CustomSet> sets = SavedSetsManager.getSavedSets();
            if (sets != null) {
                for (SavedSetsManager.CustomSet s : sets) {
                    if (s.itemKey != null && s.itemKey.equalsIgnoreCase(itemKey) && s.enchantments != null) {
                        preferredEnchants.addAll(s.enchantments);
                        break;
                    }
                }
            }
        }
        for (String pe : preferredEnchants) {
            if (targetEnchantIds.size() >= 12) break;
            if (targetEnchantIds.contains(pe)) continue;
            EnchantmentEntry pee = EnchantmentDatabase.getEnchantment(pe);
            if (pee == null || !EnchantmentDatabase.isApplicable(pee, itemKey)) continue;
            if (isEnchantConflicting(pee, cachedSlot0Enchants)) continue;
            boolean conf = false;
            for (String tid : targetEnchantIds) {
                EnchantmentEntry tee = EnchantmentDatabase.getEnchantment(tid);
                if (EnchantmentDatabase.isEnchantPairConflicting(pee, tee)) {
                    conf = true;
                    break;
                }
            }
            if (!conf) targetEnchantIds.add(pe);
        }

        // 3. Completa con i migliori candidati applicabili (max 12)
        List<EnchantmentEntry> candidates = MaxabilityAnalyzer.getApplicableCandidates(itemKey, cachedSlot0Enchants);
        for (EnchantmentEntry ee : candidates) {
            if (targetEnchantIds.size() >= 12) break;
            if (targetEnchantIds.contains(ee.id)) continue;
            boolean conf = false;
            for (String tid : targetEnchantIds) {
                EnchantmentEntry tee = EnchantmentDatabase.getEnchantment(tid);
                if (EnchantmentDatabase.isEnchantPairConflicting(ee, tee)) {
                    conf = true;
                    break;
                }
            }
            if (!conf) targetEnchantIds.add(ee.id);
        }

        // Costruisci checklist
        List<ChecklistEntry> checklist = new ArrayList<>();
        int onEquipCount = 0;
        int readyCount = 0;
        int warningCount = 0;
        int missingCount = 0;

        for (String eid : targetEnchantIds) {
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(eid);
            if (ee == null) {
                ee = new EnchantmentEntry();
                ee.id = eid;
                ee.name = eid;
                ee.maxLevel = 1;
            }
            int targetLvl = ee.maxLevel > 0 ? ee.maxLevel : 1;

            boolean isOnEquip = false;
            for (EnchantApplied ea : cachedSlot0Enchants) {
                if (ea.id.equalsIgnoreCase(ee.id) || ea.id.equalsIgnoreCase(ee.name)) {
                    isOnEquip = true;
                    break;
                }
            }

            if (isOnEquip) {
                checklist.add(new ChecklistEntry(ee, targetLvl, 1, 0));
                onEquipCount++;
            } else {
                if (isEnchantConflicting(ee, cachedSlot0Enchants)) continue;
                boolean confWithChecklist = false;
                for (ChecklistEntry ce : checklist) {
                    if (EnchantmentDatabase.isEnchantPairConflicting(ee, ce.ee)) {
                        confWithChecklist = true;
                        break;
                    }
                }
                if (confWithChecklist) continue;

                ItemStack book = findBookForEnchant(ee.id, 1);
                if (!book.isEmpty()) {
                    int rep = book.getOrDefault(DataComponents.REPAIR_COST, 0);
                    if (rep > 0) {
                        checklist.add(new ChecklistEntry(ee, targetLvl, 3, rep));
                        warningCount++;
                    } else {
                        checklist.add(new ChecklistEntry(ee, targetLvl, 2, 0));
                        readyCount++;
                    }
                } else {
                    checklist.add(new ChecklistEntry(ee, targetLvl, 4, 0));
                    missingCount++;
                }
            }
        }

        // Ordina checklist: 1 (Arma) -> 2 (Pronto) -> 3 (Usura) -> 4 (Manca)
        checklist.sort(Comparator.comparingInt(c -> c.status));

        int totalCount = checklist.size();
        boolean isMaxed = (onEquipCount >= 12);

        // Conta quanti libri con usura (>0) ci sono nell'intero inventario
        int totalUsedBooksInInv = 0;
        if (this.minecraft != null && this.minecraft.player != null) {
            for (int i = 0; i < this.minecraft.player.getInventory().getContainerSize(); i++) {
                ItemStack st = this.minecraft.player.getInventory().getItem(i);
                if (!st.isEmpty() && (st.is(Items.ENCHANTED_BOOK) || st.is(Items.BOOK))) {
                    if (st.getOrDefault(DataComponents.REPAIR_COST, 0) > 0) {
                        totalUsedBooksInInv++;
                    }
                }
            }
        }

        // 1. Header (Titolo + Pillola Conteggio)
        int headerAccent = isMaxed ? 0xFF10B981 : 0xFF38BDF8;
        LitnessOptimizerScreen.drawRoundedRect(extractor, rightX + 8, rightY + 7, 3, 12, 1, headerAccent);
        extractor.text(this.font, isMaxed ? "✦ SET COMPLETO" : "STATO FORGIA", rightX + 15, rightY + 9, isMaxed ? 0xFF34D399 : 0xFFFFFFFF, false);

        String countPill = "[" + onEquipCount + "/12]";
        int pillW = this.font.width(countPill);
        extractor.text(this.font, countPill, rightX + rightW - pillW - 8, rightY + 9, isMaxed ? 0xFF34D399 : 0xFF60A5FA, false);

        // 2. Card Equipaggiamento & Progresso
        int cardY = rightY + 22;
        int cardH = 26;
        LitnessOptimizerScreen.drawRoundedRect(extractor, rightX + 6, cardY, rightW - 12, cardH, 5, 0xFF111724);
        LitnessOptimizerScreen.drawRoundedOutline(extractor, rightX + 6, cardY, rightW - 12, cardH, 5, 0xFF1E293B);

        extractor.fakeItem(activeEquipment, rightX + 8, cardY + 5);
        String itemName = activeEquipment.getHoverName().getString();
        extractor.text(this.font, trimToWidth(itemName, rightW - 36), rightX + 28, cardY + 4, 0xFFFFFFFF, false);

        if (isMaxed) {
            extractor.text(this.font, "§a✔ 12/12 Incanti (MAX)", rightX + 28, cardY + 14, 0xFF34D399, false);
        } else {
            int missing = Math.max(0, 12 - onEquipCount);
            extractor.text(this.font, "§b✦ " + onEquipCount + "/12 §7(Mancano " + missing + ")", rightX + 28, cardY + 14, 0xFF93C5FD, false);
        }

        // 3. Info Usura Incudine & Margine Livelli
        int curY = cardY + cardH + 4;
        int penY = curY;
        int penH = 18;
        LitnessOptimizerScreen.drawRoundedRect(extractor, rightX + 6, penY, rightW - 12, penH, 4, 0xFF0B0F19);
        LitnessOptimizerScreen.drawRoundedOutline(extractor, rightX + 6, penY, rightW - 12, penH, 4, 0xFF1A2333);

        int currentPenalty = activeEquipment.getOrDefault(DataComponents.REPAIR_COST, 0);
        int freeLvls = Math.max(0, 39 - currentPenalty);
        String penInfo = "§7Usura: §e+" + currentPenalty + " liv. §8| §7Liberi: " + (freeLvls > 0 ? "§a" + freeLvls : "§c0");
        extractor.text(this.font, penInfo, rightX + 10, penY + 5, 0xFFE2E8F0, false);
        curY += penH + 4;

        // 4. Avviso Libri con Usura in Inventario (se presenti)
        if (totalUsedBooksInInv > 0 && ModConfig.get().warnUsedBooksInAnvil) {
            int alertH = 18;
            LitnessOptimizerScreen.drawRoundedRect(extractor, rightX + 6, curY, rightW - 12, alertH, 4, 0x4CEF4444);
            LitnessOptimizerScreen.drawRoundedOutline(extractor, rightX + 6, curY, rightW - 12, alertH, 4, 0xFFEF4444);
            String warnMsg = "§c⚠ " + totalUsedBooksInInv + (totalUsedBooksInInv == 1 ? " libro usato in inv!" : " libri usati in inv!");
            extractor.text(this.font, warnMsg, rightX + 9, curY + 5, 0xFFFCA5A5, false);
            curY += alertH + 4;
        }

        // 5. Pulsante Interattivo "Calcola Guida 12 Incanti"
        int btnH = 20;
        int btnW = rightW - 12;
        int btnX = rightX + 6;
        int btnY = curY;

        AnvilTooltipState.calc12BtnX = btnX;
        AnvilTooltipState.calc12BtnY = btnY;
        AnvilTooltipState.calc12BtnW = btnW;
        AnvilTooltipState.calc12BtnH = btnH;
        AnvilTooltipState.calc12BtnVisible = !isMaxed;

        if (!isMaxed) {
            boolean isBtnHovered = (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH);
            int btnBg = isBtnHovered ? 0xFF0369A1 : 0xFF0C243B;
            int btnBorder = isBtnHovered ? 0xFF38BDF8 : 0xFF0284C7;
            LitnessOptimizerScreen.drawRoundedRect(extractor, btnX, btnY, btnW, btnH, 5, btnBg);
            LitnessOptimizerScreen.drawRoundedOutline(extractor, btnX, btnY, btnW, btnH, 5, btnBorder);
            extractor.centeredText(this.font, "§b⚡ Guida 12 Incanti", btnX + (btnW / 2), btnY + 6, 0xFFE0F2FE);
        } else {
            LitnessOptimizerScreen.drawRoundedRect(extractor, btnX, btnY, btnW, btnH, 5, 0xFF064E3B);
            LitnessOptimizerScreen.drawRoundedOutline(extractor, btnX, btnY, btnW, btnH, 5, 0xFF10B981);
            extractor.centeredText(this.font, "§a✔ 12/12 Incanti Raggiunti", btnX + (btnW / 2), btnY + 6, 0xFF6EE7B7);
        }
        curY += btnH + 4;

        // 6. Intestazione Checklist
        extractor.text(this.font, "§7Checklist Incanti (" + onEquipCount + "/12):", rightX + 8, curY, 0xFF94A3B8, false);
        curY += 11;

        // 7. Area scorrevole checklist
        int listBottomY = rightY + rightH - 6;
        int listH = listBottomY - curY;
        if (listH > 16 && !checklist.isEmpty()) {
            int rowHeight = 15;
            int totalRowH = checklist.size() * rowHeight;
            AnvilTooltipState.rightPanelMaxScroll = Math.max(0, totalRowH - listH);
            if (AnvilTooltipState.rightPanelScrollOffset > AnvilTooltipState.rightPanelMaxScroll) {
                AnvilTooltipState.rightPanelScrollOffset = AnvilTooltipState.rightPanelMaxScroll;
            }

            extractor.enableScissor(rightX + 4, curY, rightX + rightW - 4, listBottomY);
            for (int i = 0; i < checklist.size(); i++) {
                ChecklistEntry ce = checklist.get(i);
                int rowY = curY + i * rowHeight - AnvilTooltipState.rightPanelScrollOffset;
                if (rowY + rowHeight < curY || rowY > listBottomY) continue;

                boolean isRowHovered = (mouseX >= rightX + 6 && mouseX <= rightX + rightW - 10 && mouseY >= rowY && mouseY < rowY + rowHeight);
                if (isRowHovered) {
                    LitnessOptimizerScreen.drawRoundedRect(extractor, rightX + 6, rowY, rightW - 12, rowHeight - 1, 3, 0xFF1E2334);
                    AnvilTooltipState.hoveredChecklistEnchant = ce.ee;
                }

                String prefix;
                int nameColor;
                String tag;
                int tagColor;
                switch (ce.status) {
                    case 1:
                        prefix = "§2✔ ";
                        nameColor = 0xFF34D399;
                        tag = "Arma";
                        tagColor = 0xFF10B981;
                        break;
                    case 2:
                        prefix = "§b📖 ";
                        nameColor = 0xFFFFFFFF;
                        tag = "Pronto";
                        tagColor = 0xFF38BDF8;
                        break;
                    case 3:
                        prefix = "§e⚠ ";
                        nameColor = 0xFFFDE68A;
                        tag = "+" + ce.bookPenalty + " liv";
                        tagColor = 0xFFF59E0B;
                        break;
                    default:
                        prefix = "§8✖ ";
                        nameColor = 0xFF64748B;
                        tag = "Manca";
                        tagColor = 0xFFEF4444;
                        break;
                }
                String eName = AnvilOptimizer.formatEnchantName(ce.ee.name, ce.targetLevel);
                int tagW = this.font.width(tag);
                int maxNameW = rightW - tagW - 28;
                String trimmedName = trimToWidth(prefix + eName, maxNameW);

                extractor.text(this.font, trimmedName, rightX + 8, rowY + 3, nameColor, false);
                extractor.text(this.font, tag, rightX + rightW - tagW - 10, rowY + 3, tagColor, false);
            }
            extractor.disableScissor();

            if (AnvilTooltipState.rightPanelMaxScroll > 0) {
                int sbX = rightX + rightW - 6;
                int sbH = Math.max(12, (int)((float) listH / totalRowH * listH));
                int sbY = curY + (int)((float) AnvilTooltipState.rightPanelScrollOffset / AnvilTooltipState.rightPanelMaxScroll * (listH - sbH));
                LitnessOptimizerScreen.drawRoundedRect(extractor, sbX, curY, 2, listH, 1, 0x301E293B);
                LitnessOptimizerScreen.drawRoundedRect(extractor, sbX, sbY, 2, sbH, 1, 0xFF38BDF8);
            }
        }
    }

    private boolean isEnchantConflicting(EnchantmentEntry ee, List<EnchantApplied> existingEnchants) {
        if (ee == null || existingEnchants == null || existingEnchants.isEmpty()) return false;
        List<String> confs = EnchantmentDatabase.getResolvedConflicts(ee.id);
        for (EnchantApplied exist : existingEnchants) {
            if (exist.id.equalsIgnoreCase(ee.id)) continue;
            EnchantmentEntry eeExist = EnchantmentDatabase.getEnchantment(exist.id);
            if (ee.unique && eeExist != null && eeExist.unique) {
                return true;
            }
            String existName = eeExist != null ? eeExist.name : exist.id;
            String existBaseName = eeExist != null ? LitnessI18n.getBaseEnchantName(eeExist) : LitnessI18n.getBaseEnchantNameById(exist.id, exist.id);
            String existBaseId = LitnessI18n.getBaseId(exist.id);
            for (String c : confs) {
                if (c.equalsIgnoreCase(existBaseName) || c.equalsIgnoreCase(existName) || c.equalsIgnoreCase(existBaseId) || c.equalsIgnoreCase(exist.id)) {
                    return true;
                }
            }
            if (eeExist != null) {
                List<String> existConfs = EnchantmentDatabase.getResolvedConflicts(eeExist.id);
                String eeBaseName = LitnessI18n.getBaseEnchantName(ee);
                String eeBaseId = LitnessI18n.getBaseId(ee.id);
                for (String c : existConfs) {
                    if (c.equalsIgnoreCase(eeBaseName) || c.equalsIgnoreCase(ee.name) || c.equalsIgnoreCase(eeBaseId) || c.equalsIgnoreCase(ee.id)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isEnchantPairConflicting(EnchantmentEntry a, EnchantmentEntry b) {
        if (a == null || b == null) return false;
        if (a.id.equalsIgnoreCase(b.id)) return false;
        if (a.unique && b.unique) return true;
        List<String> aConfs = EnchantmentDatabase.getResolvedConflicts(a.id);
        String bBaseName = LitnessI18n.getBaseEnchantName(b);
        String bBaseId = LitnessI18n.getBaseId(b.id);
        for (String c : aConfs) {
            if (c.equalsIgnoreCase(bBaseName) || c.equalsIgnoreCase(b.name) || c.equalsIgnoreCase(bBaseId) || c.equalsIgnoreCase(b.id)) {
                return true;
            }
        }
        List<String> bConfs = EnchantmentDatabase.getResolvedConflicts(b.id);
        String aBaseName = LitnessI18n.getBaseEnchantName(a);
        String aBaseId = LitnessI18n.getBaseId(a.id);
        for (String c : bConfs) {
            if (c.equalsIgnoreCase(aBaseName) || c.equalsIgnoreCase(a.name) || c.equalsIgnoreCase(aBaseId) || c.equalsIgnoreCase(a.id)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack findBookForEnchant(String enchantId, int minLevel) {
        if (this.minecraft == null || this.minecraft.player == null) return ItemStack.EMPTY;
        Inventory inv = this.minecraft.player.getInventory();
        int size = inv.getContainerSize();
        ItemStack s0 = this.menu.getSlot(0).getItem();
        if ((s0.is(Items.ENCHANTED_BOOK) || s0.is(Items.BOOK)) && hasEnchant(s0, enchantId, minLevel)) {
            return s0;
        }
        ItemStack s1 = this.menu.getSlot(1).getItem();
        if ((s1.is(Items.ENCHANTED_BOOK) || s1.is(Items.BOOK)) && hasEnchant(s1, enchantId, minLevel)) {
            return s1;
        }
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || ItemTypeHelper.isContainerOrShulker(stack)) continue;
            if ((stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK)) && hasEnchant(stack, enchantId, minLevel)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean hasEnchant(ItemStack stack, String enchantId, int minLevel) {
        List<EnchantApplied> enchants = extractAllEnchantments(stack);
        for (EnchantApplied ea : enchants) {
            if (ea.id.equalsIgnoreCase(enchantId)) {
                if (minLevel <= 0 || ea.level >= minLevel) return true;
            }
        }
        return false;
    }

    private ItemStack findEquipmentBySavedDetails(String savedItemId, String savedItemKey) {
        if (this.minecraft == null || this.minecraft.player == null) return ItemStack.EMPTY;
        Inventory inv = this.minecraft.player.getInventory();
        int size = inv.getContainerSize();
        if (savedItemId != null && !savedItemId.isEmpty()) {
            for (int i = 0; i < size; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty() || ItemTypeHelper.isContainerOrShulker(stack)) continue;
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (savedItemId.equalsIgnoreCase(id) && ItemTypeHelper.isEnchantableWeaponOrArmor(stack)) {
                    return stack;
                }
            }
        }
        if (savedItemKey != null && !savedItemKey.isEmpty()) {
            return findEquipmentByKeyInInventory(savedItemKey);
        }
        return ItemStack.EMPTY;
    }

    private ItemStack createFallbackEquipment(String savedId, String savedKey, String savedName, int savedPenalty) {
        String key = (savedKey != null && !savedKey.isEmpty()) ? savedKey : "sword";
        ItemStack fallback = getItemStackForKey(key);
        if (fallback.isEmpty()) fallback = new ItemStack(Items.NETHERITE_SWORD);
        if (savedName != null && !savedName.isEmpty()) {
            fallback.set(DataComponents.CUSTOM_NAME, Component.literal(savedName));
        }
        if (savedPenalty > 0) {
            fallback.set(DataComponents.REPAIR_COST, savedPenalty);
        }
        return fallback;
    }

    private ItemStack findMatchingEquipmentInInventory(ItemStack target) {
        if (target == null || target.isEmpty()) return ItemStack.EMPTY;
        if (this.menu != null) {
            ItemStack carried = this.menu.getCarried();
            if (!carried.isEmpty() && carried.getItem() == target.getItem()) {
                return carried;
            }
            ItemStack s0 = this.menu.getSlot(0).getItem();
            if (!s0.isEmpty() && s0.getItem() == target.getItem()) {
                return s0;
            }
            ItemStack s1 = this.menu.getSlot(1).getItem();
            if (!s1.isEmpty() && s1.getItem() == target.getItem()) {
                return s1;
            }
        }
        if (this.minecraft == null || this.minecraft.player == null) return target;
        Inventory inv = this.minecraft.player.getInventory();
        int size = inv.getContainerSize();
        String targetName = target.getHoverName().getString();

        // 1. Corrispondenza perfetta: stesso item, stesso nome personalizzato e stessa penalità
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || ItemTypeHelper.isContainerOrShulker(stack)) continue;
            if (stack.getItem() == target.getItem()) {
                int repTarget = target.getOrDefault(DataComponents.REPAIR_COST, 0);
                int repStack = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
                if (repTarget == repStack && stack.getHoverName().getString().equals(targetName)) {
                    return stack;
                }
            }
        }

        // 2. Corrispondenza per stesso item e stessa penalità
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || ItemTypeHelper.isContainerOrShulker(stack)) continue;
            if (stack.getItem() == target.getItem()) {
                int repTarget = target.getOrDefault(DataComponents.REPAIR_COST, 0);
                int repStack = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
                if (repTarget == repStack) {
                    return stack;
                }
            }
        }

        // 3. Corrispondenza per stesso identico item (non sostituisce mai es. Diamond Sword con Netherite)
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || ItemTypeHelper.isContainerOrShulker(stack)) continue;
            if (stack.getItem() == target.getItem()) {
                return stack;
            }
        }
        return target;
    }

    private ItemStack findEquipmentByKeyInInventory(String targetKey) {
        if (targetKey == null || this.minecraft == null || this.minecraft.player == null) return ItemStack.EMPTY;
        Inventory inv = this.minecraft.player.getInventory();
        int size = inv.getContainerSize();
        ItemStack bestCandidate = ItemStack.EMPTY;
        int minEnchants = 999;

        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && !ItemTypeHelper.isContainerOrShulker(stack) && ItemTypeHelper.isEnchantableWeaponOrArmor(stack)) {
                String k = ItemTypeHelper.detectItemKey(stack);
                if (k.equalsIgnoreCase(targetKey) && !AnvilTooltipState.isItemMaxed(stack)) {
                    int ec = extractAllEnchantments(stack).size();
                    if (ec < minEnchants) {
                        minEnchants = ec;
                        bestCandidate = stack;
                        if (ec == 0) break; // Oggetto senza incantesimi: candidato ideale!
                    }
                }
            }
        }
        return bestCandidate;
    }

    private ItemStack findAnyEquipmentInInventory() {
        if (this.minecraft == null || this.minecraft.player == null) return ItemStack.EMPTY;
        Inventory inv = this.minecraft.player.getInventory();
        int size = inv.getContainerSize();
        ItemStack bestCandidate = ItemStack.EMPTY;
        int minEnchants = 999;

        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && !ItemTypeHelper.isContainerOrShulker(stack) && ItemTypeHelper.isEnchantableWeaponOrArmor(stack) && !AnvilTooltipState.isItemMaxed(stack)) {
                int ec = extractAllEnchantments(stack).size();
                if (ec < minEnchants) {
                    minEnchants = ec;
                    bestCandidate = stack;
                    if (ec == 0) break;
                }
            }
        }
        return bestCandidate;
    }

    private void updateAutoCalculation(ItemStack targetEquip) {
        if (this.minecraft == null || this.minecraft.player == null || targetEquip == null || targetEquip.isEmpty()) return;

        int equipRepCost = targetEquip.getOrDefault(DataComponents.REPAIR_COST, 0);
        int currentHash = Objects.hash(
            targetEquip.getItem().toString(), 
            equipRepCost, 
            targetEquip.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).hashCode(),
            targetEquip.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).hashCode()
        );

        ItemStack s0 = this.menu.getSlot(0).getItem();
        ItemStack s1 = this.menu.getSlot(1).getItem();

        int quickInvHash = 0;
        int invSize = this.minecraft.player.getInventory().getContainerSize();
        for (int i = 0; i < invSize; i++) {
            ItemStack st = this.minecraft.player.getInventory().getItem(i);
            if (!st.isEmpty()) {
                quickInvHash = 31 * quickInvHash + (st.getItem().hashCode() ^ st.getCount() ^ st.getOrDefault(DataComponents.REPAIR_COST, 0));
            }
        }
        if (!s0.isEmpty()) quickInvHash = 31 * quickInvHash + (s0.getItem().hashCode() ^ s0.getCount() ^ s0.getOrDefault(DataComponents.REPAIR_COST, 0));
        if (!s1.isEmpty()) quickInvHash = 31 * quickInvHash + (s1.getItem().hashCode() ^ s1.getCount() ^ s1.getOrDefault(DataComponents.REPAIR_COST, 0));

        if (currentHash == lastSlot0Hash && quickInvHash == lastQuickInvHash && cachedSolution != null) {
            return;
        }
        lastQuickInvHash = quickInvHash;

        // Rileva tutti i libri dall'inventario e dall'incudine (SOLO libri sciolti, MAI shulker o contenitori!)
        List<BookInput> books = new ArrayList<>();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(i);
            if (stack.isEmpty() || ItemTypeHelper.isContainerOrShulker(stack)) continue;
            if (stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK)) {
                int repCost = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
                int work = 0;
                while ((1 << work) - 1 < repCost) work++;

                BookInput book = new BookInput(work);
                book.enchantments = extractAllEnchantments(stack);
                int loreCost = InventoryTracker.extractAnvilCostFromLore(stack);
                book.explicitCost = loreCost;
                if (loreCost > 0) {
                    for (EnchantApplied ea : book.enchantments) {
                        EnchantmentDatabase.updateAnvilCost(ea.id, loreCost);
                    }
                }
                if (!book.enchantments.isEmpty()) {
                    books.add(book);
                }
            }
        }

        // Se lo slot 0 dell'incudine ha un libro (mentre si stanno unendo 2 libri), includilo nel pool dei libri!
        if (s0.is(Items.ENCHANTED_BOOK) || s0.is(Items.BOOK)) {
            int repCost = s0.getOrDefault(DataComponents.REPAIR_COST, 0);
            int work = 0;
            while ((1 << work) - 1 < repCost) work++;

            BookInput book = new BookInput(work);
            book.enchantments = extractAllEnchantments(s0);
            int loreCost = InventoryTracker.extractAnvilCostFromLore(s0);
            book.explicitCost = loreCost;
            if (loreCost > 0) {
                for (EnchantApplied ea : book.enchantments) {
                    EnchantmentDatabase.updateAnvilCost(ea.id, loreCost);
                }
            }
            if (!book.enchantments.isEmpty()) {
                books.add(book);
            }
        }

        // Se lo slot 1 dell'incudine ha un libro, aggiungi anche quello!
        if (s1.is(Items.ENCHANTED_BOOK) || s1.is(Items.BOOK)) {
            int repCost = s1.getOrDefault(DataComponents.REPAIR_COST, 0);
            int work = 0;
            while ((1 << work) - 1 < repCost) work++;

            BookInput book = new BookInput(work);
            book.enchantments = extractAllEnchantments(s1);
            int loreCost = InventoryTracker.extractAnvilCostFromLore(s1);
            book.explicitCost = loreCost;
            if (loreCost > 0) {
                for (EnchantApplied ea : book.enchantments) {
                    EnchantmentDatabase.updateAnvilCost(ea.id, loreCost);
                }
            }
            if (!book.enchantments.isEmpty()) {
                books.add(book);
            }
        }

        // Ordinamento deterministico di tutti i libri disponibili (rende i calcoli stabili e non sensibili agli slot fisici)
        books.sort((b1, b2) -> {
            String id1 = b1.enchantments.isEmpty() ? "" : b1.enchantments.get(0).id;
            String id2 = b2.enchantments.isEmpty() ? "" : b2.enchantments.get(0).id;
            int c = id1.compareTo(id2);
            if (c != 0) return c;
            int lvl1 = b1.enchantments.isEmpty() ? 0 : b1.enchantments.get(0).level;
            int lvl2 = b2.enchantments.isEmpty() ? 0 : b2.enchantments.get(0).level;
            c = Integer.compare(lvl1, lvl2);
            if (c != 0) return c;
            c = Integer.compare(b1.works, b2.works);
            if (c != 0) return c;
            return Integer.compare(b1.explicitCost, b2.explicitCost);
        });

        int invHash = 0;
        for (BookInput b : books) {
            invHash = 31 * invHash + Objects.hash(b.works, b.explicitCost);
            for (EnchantApplied ea : b.enchantments) {
                invHash = 31 * invHash + Objects.hash(ea.id, ea.level);
            }
        }

        if (currentHash != lastSlot0Hash || invHash != lastInvBooksHash) {
            lastSlot0Hash = currentHash;
            lastInvBooksHash = invHash;
            cachedSlot0Enchants = extractAllEnchantments(targetEquip);

            String itemKey = ItemTypeHelper.detectItemKey(targetEquip);
            this.cachedItemKey = itemKey;

            ItemEntry itemEntry = EnchantmentDatabase.getItem(itemKey);
            if (itemEntry == null) itemEntry = EnchantmentDatabase.getItem("sword");

            int penalty = equipRepCost;
            int work = 0;
            while ((1 << work) - 1 < penalty) work++;

            // Se l'utente ha esplicitamente fissato (pinnato) un set per questo item, prova prima con quello
            String pinnedSetId = ActiveSessionManager.getPinnedSetId();
            SavedSetsManager.CustomSet pinnedSet = (pinnedSetId != null) ? SavedSetsManager.getSetById(pinnedSetId) : null;
            Set<String> plannedEnchants = null;
            if (pinnedSet != null && pinnedSet.itemKey != null && pinnedSet.itemKey.equalsIgnoreCase(itemKey)
                    && pinnedSet.enchantments != null && !pinnedSet.enchantments.isEmpty()) {
                plannedEnchants = new LinkedHashSet<>(pinnedSet.enchantments);
            }

            List<BookInput> applicableBooks = filterBooks(books, itemEntry, cachedSlot0Enchants, plannedEnchants);
            // Fallback: se il set pinnato non trova alcun libro compatibile presente nell'inventario, usa tutti i libri compatibili presenti!
            if (applicableBooks.isEmpty() && plannedEnchants != null) {
                applicableBooks = filterBooks(books, itemEntry, cachedSlot0Enchants, null);
            }

            final int calcId = calcIdCounter.incrementAndGet();
            if (currentCalcTask != null && !currentCalcTask.isDone()) {
                currentCalcTask.cancel(true);
            }

            if (!applicableBooks.isEmpty()) {
                final ItemEntry finalItemEntry = itemEntry;
                final int finalWork = work;
                final List<EnchantApplied> finalSlot0Enchants = new ArrayList<>(cachedSlot0Enchants);
                final List<BookInput> finalApplicableBooks = applicableBooks;
                currentCalcTask = CALC_EXECUTOR.submit(() -> {
                    try {
                        Solution sol = AnvilOptimizer.optimize(finalApplicableBooks, finalWork, finalItemEntry, finalSlot0Enchants);
                        if (calcId == calcIdCounter.get()) {
                            this.cachedSolution = sol;
                            com.litness.optimizer.gui.AnvilTooltipState.cachedSolution = sol;
                            com.litness.optimizer.gui.AnvilTooltipState.cachedItemKey = this.cachedItemKey;
                        }
                    } catch (Throwable ignored) {}
                });
            } else {
                this.cachedSolution = null;
                com.litness.optimizer.gui.AnvilTooltipState.cachedSolution = null;
                com.litness.optimizer.gui.AnvilTooltipState.cachedItemKey = this.cachedItemKey;
            }
        }

        // SEMPRE sincronizzato con AnvilTooltipState su ogni tick
        com.litness.optimizer.gui.AnvilTooltipState.cachedSolution = this.cachedSolution;
        com.litness.optimizer.gui.AnvilTooltipState.cachedItemKey = this.cachedItemKey;
    }

    private List<BookInput> filterBooks(List<BookInput> books, ItemEntry itemEntry, List<EnchantApplied> existingEnchants, Set<String> plannedEnchants) {
        List<BookInput> applicableBooks = new ArrayList<>();
        if (books == null || books.isEmpty() || itemEntry == null) return applicableBooks;

        for (BookInput b : books) {
            BookInput filtered = new BookInput(b.works);
            filtered.explicitCost = b.explicitCost;
            for (EnchantApplied ea : b.enchantments) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                if (ee == null) {
                    ee = new EnchantmentEntry();
                    ee.id = ea.id;
                    ee.name = ea.id;
                    ee.maxLevel = Math.max(1, ea.level);
                    ee.applicableTo = Collections.singletonList(itemEntry.id);
                }

                if (!EnchantmentDatabase.isApplicable(ee, itemEntry.id)) {
                    continue;
                }

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

                boolean alreadyOnItem = false;
                boolean conflictsWithItem = false;
                List<String> confs = EnchantmentDatabase.getResolvedConflicts(ee.id);
                for (EnchantApplied exist : existingEnchants) {
                    if (exist.id.equalsIgnoreCase(ea.id)) {
                        // Se l'oggetto ha già un livello superiore (es. piccone ha V, libro è IV), il libro è inutile
                        if (exist.level > ea.level) {
                            alreadyOnItem = true;
                            break;
                        }
                        // Se l'oggetto ha lo stesso livello del libro:
                        // - Se il libro è già al livello massimo dell'incantesimo (es. Efficienza V su V), non si sale oltre
                        // - Se è inferiore al livello massimo (es. Efficienza IV su IV -> diventa V!), È UN UPGRADE UTILE!
                        if (exist.level == ea.level && ea.level >= ee.maxLevel) {
                            alreadyOnItem = true;
                            break;
                        }
                    }
                    EnchantmentEntry eeExist = EnchantmentDatabase.getEnchantment(exist.id);
                    if (ee.unique && eeExist != null && eeExist.unique) {
                        conflictsWithItem = true;
                        break;
                    }
                    String existName = eeExist != null ? eeExist.name : exist.id;
                    String existBaseName = eeExist != null ? LitnessI18n.getBaseEnchantName(eeExist) : LitnessI18n.getBaseEnchantNameById(exist.id, exist.id);
                    String existBaseId = LitnessI18n.getBaseId(exist.id);
                    boolean hasConf = false;
                    for (String c : confs) {
                        if (c.equalsIgnoreCase(existBaseName) || c.equalsIgnoreCase(existName) || c.equalsIgnoreCase(existBaseId) || c.equalsIgnoreCase(exist.id)) {
                            hasConf = true;
                            break;
                        }
                    }
                    if (hasConf) {
                        conflictsWithItem = true;
                        break;
                    }
                }
                if (!alreadyOnItem && !conflictsWithItem) {
                    filtered.enchantments.add(ea);
                }
            }
            if (!filtered.enchantments.isEmpty()) {
                applicableBooks.add(filtered);
            }
        }
        return applicableBooks;
    }

    private static List<EnchantApplied> extractAllEnchantments(ItemStack stack) {
        return AnvilTooltipState.extractAllEnchantments(stack);
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

    private void openOptimizer() {
        if (this.minecraft != null) {
            Solution sol = this.cachedSolution;
            if (sol == null) {
                ItemStack equip = this.menu.getSlot(0).getItem();
                if (equip.isEmpty()) equip = AnvilTooltipState.explicitTargetEquip;
                if (equip.isEmpty() && this.minecraft.player != null) equip = this.minecraft.player.getMainHandItem();
                if (equip.isEmpty() && AnvilTooltipState.savedSelectedEquipment != null) equip = AnvilTooltipState.savedSelectedEquipment;
                if (!equip.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(equip)) {
                    AnvilTooltipState.open12EnchantGuide((AnvilScreen) (Object) this);
                    return;
                }
            }
            this.minecraft.setScreenAndShow(new LitnessOptimizerScreen(
                Component.translatable("gui.litnessoptimizer.title"), this, this.menu, sol
            ));
        }
    }

    private ItemStack getSuggestedItem() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return new ItemStack(Items.DIAMOND_SWORD);
        }
        // 1. Controlla il set pinnato attivo nell'HUD se presente
        String pinnedSetId = com.litness.optimizer.data.ActiveSessionManager.getPinnedSetId();
        if (pinnedSetId != null) {
            com.litness.optimizer.data.SavedSetsManager.CustomSet set = com.litness.optimizer.data.SavedSetsManager.getSetById(pinnedSetId);
            if (set != null && set.itemKey != null) {
                ItemStack mapped = getItemStackForKey(set.itemKey);
                if (mapped != null) return mapped;
            }
        }
        // 2. Controlla la mano principale (SOLO se non è già maxata!)
        ItemStack main = this.minecraft.player.getMainHandItem();
        if (ItemTypeHelper.isEnchantableWeaponOrArmor(main) && !AnvilTooltipState.isItemMaxed(main)) {
            return main;
        }
        // 3. Cerca nell'inventario una qualsiasi arma o armatura incantabile non maxata
        int invSize = this.minecraft.player.getInventory().getContainerSize();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(i);
            if (ItemTypeHelper.isEnchantableWeaponOrArmor(stack) && !AnvilTooltipState.isItemMaxed(stack)) {
                return stack;
            }
        }
        // 4. Default: Spada di Diamante
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    private ItemStack getItemStackForKey(String key) {
        if ("bow".equals(key)) return new ItemStack(Items.BOW);
        if ("xbow".equals(key)) return new ItemStack(Items.CROSSBOW);
        if ("mace".equals(key)) return new ItemStack(Items.MACE);
        if ("trident".equals(key)) return new ItemStack(Items.TRIDENT);
        if ("pick".equals(key)) return new ItemStack(Items.DIAMOND_PICKAXE);
        if ("axe".equals(key)) return new ItemStack(Items.DIAMOND_AXE);
        if ("shovel".equals(key)) return new ItemStack(Items.DIAMOND_SHOVEL);
        if ("hoe".equals(key)) return new ItemStack(Items.DIAMOND_HOE);
        if ("rod".equals(key)) return new ItemStack(Items.FISHING_ROD);
        if ("helmet".equals(key)) return new ItemStack(Items.DIAMOND_HELMET);
        if ("chest".equals(key)) return new ItemStack(Items.DIAMOND_CHESTPLATE);
        if ("legs".equals(key)) return new ItemStack(Items.DIAMOND_LEGGINGS);
        if ("boots".equals(key)) return new ItemStack(Items.DIAMOND_BOOTS);
        if ("elytra".equals(key)) return new ItemStack(Items.ELYTRA);
        return new ItemStack(Items.DIAMOND_SWORD);
    }
}

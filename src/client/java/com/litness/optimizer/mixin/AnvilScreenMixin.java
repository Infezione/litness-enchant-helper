package com.litness.optimizer.mixin;

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
    private List<EnchantApplied> cachedSlot0Enchants = new ArrayList<>();
    private String cachedItemKey = "sword";

    public AnvilScreenMixin(AnvilMenu menu, Inventory inventory, Component title, Identifier texture) {
        super(menu, inventory, title, texture);
    }

    private int getSidebarWidth() {
        return 182;
    }

    private int getSidebarX() {
        int sideW = getSidebarWidth();
        int sideX = this.leftPos - sideW - 6;
        if (sideX < 4 && (this.width - (this.leftPos + this.imageWidth)) > sideW + 10) {
            sideX = this.leftPos + this.imageWidth + 6;
        }
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

    @Inject(method = "subInit", at = @At("TAIL"))
    private void litness_addOptimizerButton(CallbackInfo ci) {
        int sideW = getSidebarWidth();
        int sideX = getSidebarX();
        int sideY = this.topPos - 14;
        int sideH = this.imageHeight + 10;
        int btnY = sideY + sideH + 3;

        // 1. Tasto nella sidebar sotto l'helper
        this.addRenderableWidget(Button.builder(Component.literal("Guida Completa [N]"), button -> {
            openOptimizer();
        }).bounds(sideX, btnY, sideW, 18).build());

        // 2. Tasto rapido direttamente sopra l'incudine (nessuna sovrapposizione con il titolo)
        int topBtnW = 80;
        int topBtnX = this.leftPos + this.imageWidth - topBtnW;
        int topBtnY = this.topPos - 18;
        this.addRenderableWidget(Button.builder(Component.literal("Guida Passi"), button -> {
            openOptimizer();
        }).bounds(topBtnX, topBtnY, topBtnW, 16).build());
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void litness_onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() == InputConstants.KEY_N && (name == null || !name.isFocused())) {
            openOptimizer();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void litness_renderSidebar(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            com.litness.optimizer.gui.AnvilTooltipState.clearHover();

            int sideW = getSidebarWidth();
            int sideX = getSidebarX();
            int sideY = this.topPos - 14;
            int sideH = this.imageHeight + 10;

            // Box scuro elegante laterale (stile dark glass premium)
            extractor.fill(sideX, sideY, sideX + sideW, sideY + sideH, 0xF20F111A);

            // Bordo bicolore d'accento superiore (Indigo -> Emerald)
            int halfW = sideW / 2;
            extractor.fill(sideX, sideY, sideX + halfW, sideY + 2, 0xFF6366F1);
            extractor.fill(sideX + halfW, sideY, sideX + sideW, sideY + 2, 0xFF10B981);

            // Contorno sottile moderno
            extractor.outline(sideX, sideY, sideW, sideH, 0xFF2A2D40);

            // Titolo Header con badge info [?] affiancato
            extractor.text(this.font, "LITNESS HELPER", sideX + 8, sideY + 6, 0xFFA5B4FC, false);

            int infoBtnX = sideX + sideW - 18;
            int infoBtnY = sideY + 5;
            boolean isInfoHovered = mouseX >= infoBtnX - 3 && mouseX <= infoBtnX + 15 && mouseY >= infoBtnY - 2 && mouseY <= infoBtnY + 12;
            extractor.text(this.font, isInfoHovered ? "§e[?]" : "§7[§e?§7]", infoBtnX, infoBtnY, isInfoHovered ? 0xFFFCD34D : 0xFF9CA3AF, false);
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
                equipInSlot0 = true;
            } else {
                activeEquipment = findMatchingEquipmentInInventory(lastExplicitEquipment);
                if (activeEquipment.isEmpty()) {
                    activeEquipment = findAnyEquipmentInInventory();
                    if (!activeEquipment.isEmpty() && lastExplicitEquipment.isEmpty()) {
                        lastExplicitEquipment = activeEquipment.copy();
                    }
                }
            }

            if (!activeEquipment.isEmpty()) {
                updateAutoCalculation(activeEquipment);

                int penalty = activeEquipment.getOrDefault(DataComponents.REPAIR_COST, 0);

                // Box interno per l'oggetto
                int wellY = sideY + 18;
                int wellH = 38;
                extractor.fill(sideX + 6, wellY, sideX + sideW - 6, wellY + wellH, 0x60161826);
                extractor.outline(sideX + 6, wellY, sideW - 12, wellH, 0xFF26293D);

                // Icona 3D dell'oggetto
                extractor.fakeItem(activeEquipment, sideX + 9, wellY + 11);

                // Nome oggetto + stato inventario se non è nello slot 0
                String itemName = activeEquipment.getHoverName().getString();
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

                if (mouseX >= sideX + 6 && mouseX <= sideX + sideW - 6 && mouseY >= wellY && mouseY <= wellY + wellH) {
                    com.litness.optimizer.gui.AnvilTooltipState.hoveredMaxabilityResult = maxability;
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
                        extractor.text(this.font, "• " + trimToWidth(eName, sideW - 22), sideX + 10, curY, 0xFF818CF8, false);
                        curY += 9;
                    }
                    if (cachedSlot0Enchants.size() > 2) {
                        extractor.text(this.font, "  +" + (cachedSlot0Enchants.size() - 2) + " altri...", sideX + 10, curY, 0xFF6B7280, false);
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

                        int boxBg = ready ? 0x6010B981 : (isMoveHovered ? 0x60F59E0B : 0x30F59E0B);
                        int boxBorder = ready ? 0xFF10B981 : 0xFFF59E0B;
                        extractor.fill(sideX + 6, moveY, sideX + sideW - 6, moveY + moveH, boxBg);
                        extractor.outline(sideX + 6, moveY, sideW - 12, moveH, boxBorder);

                        String badge = ready ? "§a✔ PRONTO DA UNIRE (1/" + totalSteps + ")" : "UNISCI 2 LIBRI (1/" + totalSteps + ")";
                        int badgeColor = ready ? 0xFF34D399 : 0xFFFBBF24;
                        extractor.text(this.font, badge, sideX + 10, moveY + 5, badgeColor, false);

                        if (ready) {
                            extractor.text(this.font, "§a• Prendi il libro unito", sideX + 10, moveY + 18, 0xFF34D399, false);
                            extractor.text(this.font, "  dallo slot di uscita!", sideX + 10, moveY + 30, 0xFFE2E8F0, false);
                            extractor.text(this.font, "Costo: §a" + nextStep.cost + " liv. XP", sideX + 10, moveY + 43, 0xFF34D399, false);
                        } else if (oneInserted) {
                            extractor.text(this.font, "Slot 1: §a✔ Inserito", sideX + 10, moveY + 18, 0xFF34D399, false);
                            String missingLabel = (slot0HasLeft || slot1HasLeft) ? nextStep.rightLabel : nextStep.leftLabel;
                            extractor.text(this.font, "Slot 2: Metti §d" + trimToWidth(missingLabel, sideW - 60), sideX + 10, moveY + 30, 0xFFE0E7FF, false);
                            extractor.text(this.font, "Costo: §a" + nextStep.cost + " liv. XP", sideX + 10, moveY + 42, 0xFF34D399, false);
                        } else {
                            String l1 = trimToWidth(nextStep.leftLabel, sideW - 55);
                            String l2 = trimToWidth(nextStep.rightLabel, sideW - 55);
                            extractor.text(this.font, "Slot 1: §b" + l1, sideX + 10, moveY + 18, 0xFF9CA3AF, false);
                            extractor.text(this.font, "Slot 2: §d" + l2, sideX + 10, moveY + 30, 0xFF9CA3AF, false);
                            extractor.text(this.font, "Costo: §a" + nextStep.cost + " liv. XP", sideX + 10, moveY + 42, 0xFF34D399, false);
                            if (moveH >= 65) {
                                extractor.text(this.font, "§6• Metti i 2 libri nell'incudine", sideX + 10, moveY + 54, 0xFFF59E0B, false);
                            }
                        }
                    } else {
                        // Tempra/Applica libro a equipaggiamento
                        boolean hasEquipInSlot0 = equipInSlot0;
                        boolean hasBookInSlot1 = AnvilTooltipState.matchesEnchantList(slot1, nextStep.rightEnchants);
                        boolean ready = hasEquipInSlot0 && hasBookInSlot1;

                        int boxBg = ready ? 0x6010B981 : (isMoveHovered ? 0x5010B981 : 0x3010B981);
                        int boxBorder = 0xFF10B981;
                        extractor.fill(sideX + 6, moveY, sideX + sideW - 6, moveY + moveH, boxBg);
                        extractor.outline(sideX + 6, moveY, sideW - 12, moveH, boxBorder);

                        String badge = ready ? "§a✔ PRONTO DA TEMPRARE (1/" + totalSteps + ")" : ((icon.isEmpty() ? "" : icon + " ") + actionName + " (1/" + totalSteps + ")");
                        extractor.text(this.font, badge, sideX + 10, moveY + 5, 0xFF34D399, false);

                        if (ready) {
                            extractor.text(this.font, "§a• Prendi l'oggetto", sideX + 10, moveY + 18, 0xFF34D399, false);
                            extractor.text(this.font, "  dallo slot di uscita!", sideX + 10, moveY + 30, 0xFFE2E8F0, false);
                            extractor.text(this.font, "Costo: §a" + nextStep.cost + " liv. XP", sideX + 10, moveY + 43, 0xFF34D399, false);
                        } else if (!hasEquipInSlot0) {
                            extractor.text(this.font, "Slot 1: Metti §b" + trimToWidth(itemName, sideW - 55), sideX + 10, moveY + 18, 0xFFE0E7FF, false);
                            extractor.text(this.font, "Slot 2: Metti §d" + trimToWidth(nextStep.rightLabel, sideW - 55), sideX + 10, moveY + 30, 0xFFE0E7FF, false);
                            extractor.text(this.font, "Costo: §a" + nextStep.cost + " liv. XP", sideX + 10, moveY + 42, 0xFF34D399, false);
                        } else {
                            extractor.text(this.font, "Metti nello §eSlot 2§7:", sideX + 10, moveY + 18, 0xFFE0E7FF, false);
                            String bName = trimToWidth("• " + nextStep.rightLabel, sideW - 20);
                            extractor.text(this.font, bName, sideX + 10, moveY + 30, 0xFF34D399, false);
                            extractor.text(this.font, "Costo: §a" + nextStep.cost + " liv. XP", sideX + 10, moveY + 42, 0xFF34D399, false);
                        }
                    }
                } else {
                    maxability = com.litness.optimizer.calculator.MaxabilityAnalyzer.analyze(activeEquipment, cachedSlot0Enchants);
                    extractor.fill(sideX + 6, moveY, sideX + sideW - 6, moveY + moveH, 0x301E293B);
                    extractor.outline(sideX + 6, moveY, sideW - 12, moveH, 0xFF334155);

                    if (maxability.status == com.litness.optimizer.calculator.MaxabilityAnalyzer.Status.SATURATED) {
                        extractor.text(this.font, "§c[!] TROPPO COSTOSO!", sideX + 10, moveY + 5, 0xFFEF4444, false);
                        extractor.text(this.font, "Penalità +" + penalty + " troppo alta,", sideX + 10, moveY + 18, 0xFFE2E8F0, false);
                        extractor.text(this.font, "l'incudine blocca i libri.", sideX + 10, moveY + 30, 0xFF94A3B8, false);
                        extractor.text(this.font, "Premi [N] per spiegazione", sideX + 10, moveY + 43, 0xFF818CF8, false);
                    } else if (maxability.status == com.litness.optimizer.calculator.MaxabilityAnalyzer.Status.PARTIALLY_MAXABLE) {
                        extractor.text(this.font, "§e[!] ATTENZIONE PENALITÀ", sideX + 10, moveY + 5, 0xFFF59E0B, false);
                        extractor.text(this.font, "Puoi aggiungere ancora", sideX + 10, moveY + 18, 0xFFE2E8F0, false);
                        extractor.text(this.font, "fino a §a+" + maxability.maxAddable + " incanti §7con guida.", sideX + 10, moveY + 30, 0xFFFFFFFF, false);
                        extractor.text(this.font, "Premi [N] per simulare!", sideX + 10, moveY + 43, 0xFF818CF8, false);
                    } else {
                        extractor.text(this.font, "ENCHANT HELPER", sideX + 10, moveY + 5, 0xFF94A3B8, false);
                        extractor.text(this.font, "Nessun libro compatibile", sideX + 10, moveY + 18, 0xFFE2E8F0, false);
                        extractor.text(this.font, "trovato nell'inventario.", sideX + 10, moveY + 30, 0xFF94A3B8, false);
                        extractor.text(this.font, "Premi [N] per simulare!", sideX + 10, moveY + 43, 0xFF818CF8, false);
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
                    extractor.outline(slot0X - 1, slot0Y - 1, 18, 18, (borderAlpha << 24) | 0xF59E0B);
                    int bgAlpha = (int)(0x15 + wave * 0x25);
                    extractor.fill(slot0X, slot0Y, slot0X + 16, slot0Y + 16, (bgAlpha << 24) | 0xF59E0B);
                    extractor.fakeItem(ghostItem, slot0X, slot0Y);
                    int holoAlpha = (int)(0x40 + (1.0 - wave) * 0x40);
                    extractor.fill(slot0X, slot0Y, slot0X + 16, slot0Y + 16, (holoAlpha << 24) | 0x0F172A);

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
                extractor.outline(slot0X - 1, slot0Y - 1, 18, 18, (borderAlpha << 24) | 0xF59E0B);
                int bgAlpha = (int)(0x15 + wave * 0x25);
                extractor.fill(slot0X, slot0Y, slot0X + 16, slot0Y + 16, (bgAlpha << 24) | 0xF59E0B);
                extractor.fakeItem(ghostItem, slot0X, slot0Y);
                int holoAlpha = (int)(0x40 + (1.0 - wave) * 0x40);
                extractor.fill(slot0X, slot0Y, slot0X + 16, slot0Y + 16, (holoAlpha << 24) | 0x0F172A);

                if (mouseX >= slot0X && mouseX < slot0X + 16 && mouseY >= slot0Y && mouseY < slot0Y + 16) {
                    com.litness.optimizer.gui.AnvilTooltipState.hoveredGhostSlotItemName = ghostName;
                }

                int cardY = sideY + 20;
                int cardH = sideH - 32;
                extractor.fill(sideX + 6, cardY, sideX + sideW - 6, cardY + cardH, 0x40161826);
                extractor.outline(sideX + 6, cardY, sideW - 12, cardH, 0xFF2A2D40);

                int midX = sideX + (sideW / 2);
                extractor.fakeItem(ghostItem, midX - 8, cardY + 18);
                extractor.centeredText(this.font, "Slot 1 vuoto", midX, cardY + 42, 0xFFFFFFFF);
                extractor.centeredText(this.font, "Inserisci l'item", midX, cardY + 58, 0xFFFCD34D);
                extractor.centeredText(this.font, "desiderato per", midX, cardY + 70, 0xFFFCD34D);
                extractor.centeredText(this.font, "iniziare la guida.", midX, cardY + 82, 0xFF9CA3AF);

                int pillY = cardY + cardH - 22;
                extractor.fill(sideX + 12, pillY, sideX + sideW - 12, pillY + 16, 0x406366F1);
                extractor.outline(sideX + 12, pillY, sideW - 24, 16, 0x806366F1);
                extractor.centeredText(this.font, "Premi [N] per il tool", midX, pillY + 4, 0xFFA5B4FC);
            }
        } catch (Throwable t) {
            // Silenzia
        }
    }

    private ItemStack findMatchingEquipmentInInventory(ItemStack target) {
        if (target == null || target.isEmpty() || this.minecraft == null || this.minecraft.player == null) return ItemStack.EMPTY;
        Inventory inv = this.minecraft.player.getInventory();
        int size = inv.getContainerSize();
        // 1. Corrispondenza esatta (stesso item e stessa penalità)
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == target.getItem()) {
                int repTarget = target.getOrDefault(DataComponents.REPAIR_COST, 0);
                int repStack = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
                if (repTarget == repStack) {
                    return stack;
                }
            }
        }
        // 2. Corrispondenza per tipo arma o armatura
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == target.getItem() && ItemTypeHelper.isEnchantableWeaponOrArmor(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack findAnyEquipmentInInventory() {
        if (this.minecraft == null || this.minecraft.player == null) return ItemStack.EMPTY;
        Inventory inv = this.minecraft.player.getInventory();
        int size = inv.getContainerSize();
        // Priorità hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(stack)) {
                return stack;
            }
        }
        // Poi resto dell'inventario
        for (int i = 9; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && ItemTypeHelper.isEnchantableWeaponOrArmor(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
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

        // Rileva tutti i libri dall'inventario
        List<BookInput> books = new ArrayList<>();
        int invHash = 0;
        int invSize = this.minecraft.player.getInventory().getContainerSize();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(i);
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
                    invHash = 31 * invHash + Objects.hash(stack.getItem().toString(), repCost, loreCost, i);
                    for (EnchantApplied ea : book.enchantments) {
                        invHash = 31 * invHash + Objects.hash(ea.id, ea.level);
                    }
                }
            }
        }

        // Se lo slot 0 dell'incudine ha un libro (mentre si stanno unendo 2 libri), includilo nel pool dei libri!
        ItemStack s0 = this.menu.getSlot(0).getItem();
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
                books.add(0, book);
                invHash = 31 * invHash + Objects.hash(s0.getItem().toString(), repCost, loreCost, 8888);
                for (EnchantApplied ea : book.enchantments) {
                    invHash = 31 * invHash + Objects.hash(ea.id, ea.level);
                }
            }
        }

        // Se lo slot 1 dell'incudine ha un libro, aggiungi anche quello!
        ItemStack s1 = this.menu.getSlot(1).getItem();
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
                books.add(0, book);
                invHash = 31 * invHash + Objects.hash(s1.getItem().toString(), repCost, loreCost, 9999);
                for (EnchantApplied ea : book.enchantments) {
                    invHash = 31 * invHash + Objects.hash(ea.id, ea.level);
                }
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

            if (!applicableBooks.isEmpty()) {
                cachedSolution = AnvilOptimizer.optimize(applicableBooks, work, itemEntry, cachedSlot0Enchants);
            } else {
                cachedSolution = null;
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
                    String existName = eeExist != null ? eeExist.name : exist.id;
                    if (confs.contains(existName) || confs.contains(exist.id)) {
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
            this.minecraft.setScreenAndShow(new LitnessOptimizerScreen(
                Component.translatable("gui.litnessoptimizer.title"), this, this.menu, this.cachedSolution
            ));
        }
    }

    private ItemStack getSuggestedItem() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return new ItemStack(Items.DIAMOND_SWORD);
        }
        // 1. Controlla la mano principale
        ItemStack main = this.minecraft.player.getMainHandItem();
        if (ItemTypeHelper.isEnchantableWeaponOrArmor(main)) {
            return main;
        }
        // 2. Controlla il set pinnato attivo nell'HUD se presente
        String pinnedSetId = com.litness.optimizer.data.ActiveSessionManager.getPinnedSetId();
        if (pinnedSetId != null) {
            com.litness.optimizer.data.SavedSetsManager.CustomSet set = com.litness.optimizer.data.SavedSetsManager.getSetById(pinnedSetId);
            if (set != null && set.itemKey != null) {
                ItemStack mapped = getItemStackForKey(set.itemKey);
                if (mapped != null) return mapped;
            }
        }
        // 3. Cerca nell'inventario una qualsiasi arma o armatura incantabile
        int invSize = this.minecraft.player.getInventory().getContainerSize();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(i);
            if (ItemTypeHelper.isEnchantableWeaponOrArmor(stack)) {
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

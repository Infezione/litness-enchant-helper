package com.litness.optimizer.mixin;

import com.litness.optimizer.calculator.AnvilOptimizer;
import com.litness.optimizer.calculator.AnvilOptimizer.BookInput;
import com.litness.optimizer.calculator.AnvilOptimizer.EnchantApplied;
import com.litness.optimizer.calculator.AnvilOptimizer.MergeStep;
import com.litness.optimizer.calculator.AnvilOptimizer.Solution;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import com.litness.optimizer.data.EnchantmentDatabase.ItemEntry;
import com.litness.optimizer.data.InventoryTracker;
import com.litness.optimizer.data.ItemTypeHelper;
import com.litness.optimizer.gui.LitnessOptimizerScreen;
import com.litness.optimizer.tooltip.LitnessTooltipHandler;
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
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;
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

    private Solution cachedSolution = null;
    private int lastSlot0Hash = 0;
    private int lastInvBookCount = 0;
    private List<EnchantApplied> cachedSlot0Enchants = new ArrayList<>();
    private String cachedItemKey = "sword";

    public AnvilScreenMixin(AnvilMenu menu, Inventory inventory, Component title, Identifier texture) {
        super(menu, inventory, title, texture);
    }

    private int getSidebarWidth() {
        return 160;
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
        int btnY = this.topPos + this.imageHeight - 20;

        // 1. Tasto nella sidebar sotto l'helper
        this.addRenderableWidget(Button.builder(Component.literal("📋 Guida Completa [N]"), button -> {
            openOptimizer();
        }).bounds(sideX, btnY, sideW, 20).build());

        // 2. Tasto rapido direttamente sopra l'incudine (nessuna sovrapposizione con il titolo)
        int topBtnW = 80;
        int topBtnX = this.leftPos + this.imageWidth - topBtnW;
        int topBtnY = this.topPos - 18;
        this.addRenderableWidget(Button.builder(Component.literal("📋 Guida Passi"), button -> {
            openOptimizer();
        }).bounds(topBtnX, topBtnY, topBtnW, 16).build());
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void litness_onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() == GLFW.GLFW_KEY_N && (name == null || !name.isFocused())) {
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
            int sideY = this.topPos;
            int sideH = this.imageHeight - 24;

            // Box scuro elegante laterale (stile dark glass premium)
            extractor.fill(sideX, sideY, sideX + sideW, sideY + sideH, 0xF20F111A);

            // Bordo bicolore d'accento superiore (Indigo -> Emerald)
            int halfW = sideW / 2;
            extractor.fill(sideX, sideY, sideX + halfW, sideY + 2, 0xFF6366F1);
            extractor.fill(sideX + halfW, sideY, sideX + sideW, sideY + 2, 0xFF10B981);

            // Contorno sottile moderno
            extractor.outline(sideX, sideY, sideW, sideH, 0xFF2A2D40);

            // Titolo Header con badge info [?] affiancato
            extractor.text(this.font, "⚡ LITNESS HELPER", sideX + 8, sideY + 6, 0xFFA5B4FC, false);

            int infoBtnX = sideX + sideW - 18;
            int infoBtnY = sideY + 5;
            boolean isInfoHovered = mouseX >= infoBtnX - 3 && mouseX <= infoBtnX + 15 && mouseY >= infoBtnY - 2 && mouseY <= infoBtnY + 12;
            extractor.text(this.font, isInfoHovered ? "§e[?]" : "§7[§e?§7]", infoBtnX, infoBtnY, isInfoHovered ? 0xFFFCD34D : 0xFF9CA3AF, false);
            if (isInfoHovered) {
                com.litness.optimizer.gui.AnvilTooltipState.hoveredInfoModal = true;
            }

            ItemStack slot0 = this.menu.getSlot(0).getItem();
            ItemStack slot1 = this.menu.getSlot(1).getItem();

            if (!slot0.isEmpty()) {
                updateAutoCalculation(slot0);

                int penalty = slot0.getOrDefault(DataComponents.REPAIR_COST, 0);

                // Box interno per l'oggetto inserito
                int wellY = sideY + 18;
                int wellH = 37;
                extractor.fill(sideX + 6, wellY, sideX + sideW - 6, wellY + wellH, 0x60161826);
                extractor.outline(sideX + 6, wellY, sideW - 12, wellH, 0xFF26293D);

                // Icona 3D dell'oggetto
                extractor.fakeItem(slot0, sideX + 9, wellY + 10);

                // Nome oggetto
                String itemName = slot0.getHoverName().getString();
                String trimmedName = trimToWidth(itemName, sideW - 44);
                extractor.text(this.font, trimmedName, sideX + 30, wellY + 4, 0xFFFFFFFF, false);

                // Penalità di lavoro
                if (penalty == 0) {
                    extractor.text(this.font, "Penalità: 0 liv.", sideX + 30, wellY + 14, 0xFF34D399, false);
                } else {
                    extractor.text(this.font, "Penalità: +" + penalty + " liv.", sideX + 30, wellY + 14, 0xFFF59E0B, false);
                }

                // Analisi Maxabilità (se il player ha già incantato senza guida)
                com.litness.optimizer.calculator.MaxabilityAnalyzer.MaxabilityResult maxability =
                        com.litness.optimizer.calculator.MaxabilityAnalyzer.analyze(slot0, cachedSlot0Enchants);
                String maxBadge = trimToWidth(maxability.statusBadge, sideW - 36);
                extractor.text(this.font, maxBadge, sideX + 30, wellY + 25, 0xFFFFFFFF, false);

                if (mouseX >= sideX + 6 && mouseX <= sideX + sideW - 6 && mouseY >= wellY && mouseY <= wellY + wellH) {
                    com.litness.optimizer.gui.AnvilTooltipState.hoveredMaxabilityResult = maxability;
                }

                int curY = wellY + wellH + 4;

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
                int cardBottom = sideY + sideH - 12;
                int moveY = Math.max(curY + 3, sideY + 54);
                int moveH = Math.max(44, cardBottom - moveY);

                if (cachedSolution != null && !cachedSolution.steps.isEmpty()) {
                    MergeStep nextStep = cachedSolution.steps.get(0);
                    int totalSteps = cachedSolution.steps.size();

                    boolean isMoveHovered = mouseX >= sideX + 6 && mouseX <= sideX + sideW - 6 && mouseY >= moveY && mouseY <= moveY + moveH;
                    if (isMoveHovered) {
                        com.litness.optimizer.gui.AnvilTooltipState.hoveredNextStep = nextStep;
                        com.litness.optimizer.gui.AnvilTooltipState.cachedSolution = this.cachedSolution;
                        com.litness.optimizer.gui.AnvilTooltipState.cachedItemKey = this.cachedItemKey;
                    }

                    // Rileva se lo slot 1 inserito ha un conflitto con l'oggetto base nello slot 0
                    String activeConflict = null;
                    if (!slot1.isEmpty()) {
                        List<EnchantApplied> slot1Enchants = extractAllEnchantments(slot1);
                        for (EnchantApplied ea1 : slot1Enchants) {
                            EnchantmentEntry ee1 = EnchantmentDatabase.getEnchantment(ea1.id);
                            if (ee1 != null) {
                                List<String> confs = EnchantmentDatabase.getResolvedConflicts(ee1.id);
                                for (EnchantApplied ea0 : cachedSlot0Enchants) {
                                    EnchantmentEntry ee0 = EnchantmentDatabase.getEnchantment(ea0.id);
                                    String name0 = ee0 != null ? ee0.name : ea0.id;
                                    if (confs.contains(name0) || confs.contains(ea0.id)) {
                                        activeConflict = ee1.name + " vs " + name0;
                                        break;
                                    }
                                }
                            }
                            if (activeConflict != null) break;
                        }
                    }

                    // Box evidenziato emerald o amber o red con feedback visivo
                    int boxBg = (activeConflict != null) ? 0x50EF4444 : (isMoveHovered ? 0x6010B981 : 0x3010B981);
                    int boxBorder = (activeConflict != null) ? 0xFFEF4444 : (nextStep.isBookToBook ? 0xFFF59E0B : 0xFF10B981);
                    extractor.fill(sideX + 6, moveY, sideX + sideW - 6, moveY + moveH, boxBg);
                    extractor.outline(sideX + 6, moveY, sideW - 12, moveH, boxBorder);

                    String actionName = ItemTypeHelper.getActionName(cachedItemKey);
                    String icon = ItemTypeHelper.getActionIcon(cachedItemKey);

                    String badge = nextStep.isBookToBook 
                        ? "🔨 UNISCI LIBRI (1/" + totalSteps + ")" 
                        : icon + " " + actionName + " (1/" + totalSteps + ")";
                    int badgeColor = nextStep.isBookToBook ? 0xFFFBBF24 : 0xFF34D399;
                    extractor.text(this.font, badge, sideX + 10, moveY + 4, badgeColor, false);

                    if (!slot1.isEmpty()) {
                        if (activeConflict != null) {
                            extractor.text(this.font, "§e⚠️ Conflitto!", sideX + sideW - 68, moveY + 4, 0xFFFBBF24, false);
                        } else {
                            extractor.text(this.font, "✓ Inserito", sideX + sideW - 54, moveY + 4, 0xFF34D399, false);
                        }
                    }

                    if (nextStep.isBookToBook) {
                        String l1 = trimToWidth("1. " + nextStep.leftLabel, sideW - 20);
                        String l2 = trimToWidth("2. " + nextStep.rightLabel, sideW - 20);
                        extractor.text(this.font, l1, sideX + 10, moveY + 16, 0xFF93C5FD, false);
                        extractor.text(this.font, l2, sideX + 10, moveY + 26, 0xFFF472B6, false);
                        extractor.text(this.font, "Costo: " + nextStep.cost + " liv. XP", sideX + 10, moveY + 36, 0xFFFBBF24, false);
                    } else {
                        extractor.text(this.font, "Metti nello slot 2:", sideX + 10, moveY + 15, 0xFFE0E7FF, false);
                        String bName = trimToWidth("• " + nextStep.rightLabel, sideW - 20);
                        extractor.text(this.font, bName, sideX + 10, moveY + 26, 0xFF34D399, false);
                        extractor.text(this.font, "Costo: " + nextStep.cost + " liv. XP", sideX + 10, moveY + 37, 0xFFFBBF24, false);
                    }
                } else {
                    maxability = com.litness.optimizer.calculator.MaxabilityAnalyzer.analyze(slot0, cachedSlot0Enchants);
                    extractor.fill(sideX + 6, moveY, sideX + sideW - 6, moveY + moveH, 0x301E293B);
                    extractor.outline(sideX + 6, moveY, sideW - 12, moveH, 0xFF334155);

                    if (maxability.status == com.litness.optimizer.calculator.MaxabilityAnalyzer.Status.SATURATED) {
                        extractor.text(this.font, "§c⛔ TROPPO COSTOSO!", sideX + 10, moveY + 4, 0xFFEF4444, false);
                        extractor.text(this.font, "Penalità +" + penalty + " troppo alta,", sideX + 10, moveY + 16, 0xFFE2E8F0, false);
                        extractor.text(this.font, "l'incudine blocca i libri.", sideX + 10, moveY + 26, 0xFF94A3B8, false);
                        extractor.text(this.font, "Premi [N] per spiegazione", sideX + 10, moveY + 37, 0xFF818CF8, false);
                    } else if (maxability.status == com.litness.optimizer.calculator.MaxabilityAnalyzer.Status.PARTIALLY_MAXABLE) {
                        extractor.text(this.font, "§e⚠️ ATTENZIONE PENALITÀ", sideX + 10, moveY + 4, 0xFFF59E0B, false);
                        extractor.text(this.font, "Puoi aggiungere ancora", sideX + 10, moveY + 15, 0xFFE2E8F0, false);
                        extractor.text(this.font, "fino a §a+" + maxability.maxAddable + " incanti §7con guida.", sideX + 10, moveY + 26, 0xFFFFFFFF, false);
                        extractor.text(this.font, "Premi [N] per simulare!", sideX + 10, moveY + 37, 0xFF818CF8, false);
                    } else {
                        extractor.text(this.font, "ENCHANT HELPER", sideX + 10, moveY + 4, 0xFF94A3B8, false);
                        extractor.text(this.font, "Nessun libro compatibile", sideX + 10, moveY + 16, 0xFFE2E8F0, false);
                        extractor.text(this.font, "trovato nell'inventario.", sideX + 10, moveY + 26, 0xFF94A3B8, false);
                        extractor.text(this.font, "Premi [N] per simulare!", sideX + 10, moveY + 37, 0xFF818CF8, false);
                    }
                }
            } else {
                // Ghost Slot nello Slot 1 dell'Incudine reale
                net.minecraft.world.inventory.Slot s0 = this.menu.getSlot(0);
                int slot0X = this.leftPos + (s0 != null ? s0.x : 27);
                int slot0Y = this.topPos + (s0 != null ? s0.y : 47);

                ItemStack ghostItem = getSuggestedItem();
                String ghostName = ItemTypeHelper.getSuggestedName(ghostItem);

                // Ologramma pulsante (onda sinusoidale temporizzata)
                long time = System.currentTimeMillis();
                double wave = (Math.sin(time / 260.0) + 1.0) / 2.0;

                // Contorno luminoso dorato pulsante
                int borderAlpha = (int)(0x60 + wave * 0x80);
                int glowBorderColor = (borderAlpha << 24) | 0xF59E0B;
                extractor.outline(slot0X - 1, slot0Y - 1, 18, 18, glowBorderColor);

                // Sfondo semitrasparente ambrato
                int bgAlpha = (int)(0x15 + wave * 0x25);
                extractor.fill(slot0X, slot0Y, slot0X + 16, slot0Y + 16, (bgAlpha << 24) | 0xF59E0B);

                // Render dell'oggetto fantasma
                extractor.fakeItem(ghostItem, slot0X, slot0Y);

                // Velo olografico trasparente per effetto fantasma
                int holoAlpha = (int)(0x40 + (1.0 - wave) * 0x40);
                extractor.fill(slot0X, slot0Y, slot0X + 16, slot0Y + 16, (holoAlpha << 24) | 0x0F172A);

                // Rilevamento passaggio mouse sullo slot 1
                if (mouseX >= slot0X && mouseX < slot0X + 16 && mouseY >= slot0Y && mouseY < slot0Y + 16) {
                    com.litness.optimizer.gui.AnvilTooltipState.hoveredGhostSlotItemName = ghostName;
                }

                // Empty state pulito e curato con suggerimento dinamico dell'oggetto
                int cardY = sideY + 20;
                int cardH = sideH - 34;

                extractor.fill(sideX + 6, cardY, sideX + sideW - 6, cardY + cardH, 0x40161826);
                extractor.outline(sideX + 6, cardY, sideW - 12, cardH, 0xFF2A2D40);

                extractor.fakeItem(ghostItem, sideX + (sideW / 2) - 8, cardY + 10);

                extractor.centeredText(this.font, "Slot 1 vuoto", sideX + (sideW / 2), cardY + 30, 0xFFFFFFFF);
                extractor.centeredText(this.font, "Inserisci l'item", sideX + (sideW / 2), cardY + 44, 0xFFFCD34D);
                extractor.centeredText(this.font, "desiderato per", sideX + (sideW / 2), cardY + 55, 0xFFFCD34D);
                extractor.centeredText(this.font, "iniziare la guida.", sideX + (sideW / 2), cardY + 66, 0xFF9CA3AF);

                int pillY = cardY + cardH - 18;
                extractor.fill(sideX + 12, pillY, sideX + sideW - 12, pillY + 14, 0x406366F1);
                extractor.outline(sideX + 12, pillY, sideW - 24, 14, 0x806366F1);
                extractor.centeredText(this.font, "Premi [N] per il tool", sideX + (sideW / 2), pillY + 3, 0xFFA5B4FC);
            }
        } catch (Throwable t) {
            // Silenzia
        }
    }

    private void updateAutoCalculation(ItemStack slot0) {
        if (this.minecraft == null || this.minecraft.player == null) return;

        int currentHash = Objects.hash(slot0.getItem().toString(), slot0.getOrDefault(DataComponents.REPAIR_COST, 0));

        // Rileva tutti i libri dall'inventario
        List<BookInput> books = new ArrayList<>();
        int invSize = this.minecraft.player.getInventory().getContainerSize();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(i);
            if (stack.getItem() == Items.ENCHANTED_BOOK) {
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

        // Se lo slot 1 dell'incudine ha un libro, aggiungi anche quello!
        ItemStack slot1 = this.menu.getSlot(1).getItem();
        if (slot1.getItem() == Items.ENCHANTED_BOOK) {
            int repCost = slot1.getOrDefault(DataComponents.REPAIR_COST, 0);
            int work = 0;
            while ((1 << work) - 1 < repCost) work++;

            BookInput book = new BookInput(work);
            book.enchantments = extractAllEnchantments(slot1);
            int loreCost = InventoryTracker.extractAnvilCostFromLore(slot1);
            book.explicitCost = loreCost;
            if (loreCost > 0) {
                for (EnchantApplied ea : book.enchantments) {
                    EnchantmentDatabase.updateAnvilCost(ea.id, loreCost);
                }
            }
            if (!book.enchantments.isEmpty()) {
                books.add(0, book);
            }
        }

        if (currentHash != lastSlot0Hash || books.size() != lastInvBookCount) {
            lastSlot0Hash = currentHash;
            lastInvBookCount = books.size();
            cachedSlot0Enchants = extractAllEnchantments(slot0);

            String itemKey = ItemTypeHelper.detectItemKey(slot0);
            this.cachedItemKey = itemKey;

            ItemEntry itemEntry = EnchantmentDatabase.getItem(itemKey);
            if (itemEntry == null) itemEntry = EnchantmentDatabase.getItem("sword");

            int penalty = slot0.getOrDefault(DataComponents.REPAIR_COST, 0);
            int work = 0;
            while ((1 << work) - 1 < penalty) work++;

            Set<String> plannedEnchants = com.litness.optimizer.data.ActiveSessionManager.getSelectedEnchants(itemKey);

            List<BookInput> applicableBooks = new ArrayList<>();
            for (BookInput b : books) {
                BookInput filtered = new BookInput(b.works);
                filtered.explicitCost = b.explicitCost;
                for (EnchantApplied ea : b.enchantments) {
                    EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                    if (ee != null && EnchantmentDatabase.isApplicable(ee, itemEntry.id)) {
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
                        for (EnchantApplied exist : cachedSlot0Enchants) {
                            if (exist.id.equalsIgnoreCase(ea.id) && exist.level >= ea.level) {
                                alreadyOnItem = true;
                                break;
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
                }
                if (!filtered.enchantments.isEmpty()) {
                    applicableBooks.add(filtered);
                }
            }

            if (!applicableBooks.isEmpty()) {
                cachedSolution = AnvilOptimizer.optimize(applicableBooks, work, itemEntry, Collections.emptyList());
            } else {
                cachedSolution = null;
            }
        }
    }

    private List<EnchantApplied> extractAllEnchantments(ItemStack stack) {
        List<EnchantApplied> list = new ArrayList<>();
        if (stack.isEmpty()) return list;

        Set<String> seen = new HashSet<>();

        // 1. STORED_ENCHANTMENTS (libri incantati)
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(rawId, lvl);
            String id;
            if (vPlusId != null) {
                id = vPlusId;
            } else {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
                id = ee != null ? ee.id : rawId;
            }
            if (!id.isEmpty() && seen.add(id)) {
                list.add(new EnchantApplied(id, lvl));
            }
        }

        // 2. ENCHANTMENTS (armi, armature e attrezzi)
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            String vPlusId = EnchantmentDatabase.resolveVanillaPlusId(rawId, lvl);
            String id;
            if (vPlusId != null) {
                id = vPlusId;
            } else {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
                id = ee != null ? ee.id : rawId;
            }
            if (!id.isEmpty() && seen.add(id)) {
                list.add(new EnchantApplied(id, lvl));
            }
        }

        // 3. Scansione righe Lore per incantesimi inviati come testo dal server
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

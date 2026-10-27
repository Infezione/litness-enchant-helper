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
    private MergeStep hoveredNextStep = null;
    private List<EnchantApplied> hoveredCurrentEnchants = null;
    private int hoverMouseX = 0;
    private int hoverMouseY = 0;

    public AnvilScreenMixin(AnvilMenu menu, Inventory inventory, Component title, Identifier texture) {
        super(menu, inventory, title, texture);
    }

    private int getSidebarWidth() {
        return 152;
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

            // Titolo Header
            extractor.text(this.font, "⚡ LITNESS ENCHANT HELPER", sideX + 8, sideY + 6, 0xFFA5B4FC, false);

            ItemStack slot0 = this.menu.getSlot(0).getItem();
            ItemStack slot1 = this.menu.getSlot(1).getItem();

            if (!slot0.isEmpty()) {
                updateAutoCalculation(slot0);

                int penalty = slot0.getOrDefault(DataComponents.REPAIR_COST, 0);

                // Box interno per l'oggetto inserito
                int wellY = sideY + 18;
                int wellH = 30;
                extractor.fill(sideX + 6, wellY, sideX + sideW - 6, wellY + wellH, 0x60161826);
                extractor.outline(sideX + 6, wellY, sideW - 12, wellH, 0xFF26293D);

                // Icona 3D dell'oggetto
                extractor.fakeItem(slot0, sideX + 9, wellY + 7);

                // Nome oggetto
                String itemName = slot0.getHoverName().getString();
                String trimmedName = trimToWidth(itemName, sideW - 44);
                extractor.text(this.font, trimmedName, sideX + 30, wellY + 5, 0xFFFFFFFF, false);

                // Penalità di lavoro
                if (penalty == 0) {
                    extractor.text(this.font, "Penalità: 0 liv.", sideX + 30, wellY + 16, 0xFF34D399, false);
                } else {
                    extractor.text(this.font, "Penalità: +" + penalty + " liv.", sideX + 30, wellY + 16, 0xFFF59E0B, false);
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
                        String eName = (ee != null ? ee.name : ea.id) + " " + AnvilOptimizer.toRoman(ea.level);
                        extractor.text(this.font, "• " + trimToWidth(eName, sideW - 22), sideX + 10, curY, 0xFF818CF8, false);
                        curY += 9;
                    }
                    if (cachedSlot0Enchants.size() > 2) {
                        extractor.text(this.font, "  +" + (cachedSlot0Enchants.size() - 2) + " altri...", sideX + 10, curY, 0xFF6B7280, false);
                        curY += 9;
                    }

                    if (mouseX >= sideX + 6 && mouseX <= sideX + sideW - 6 && mouseY >= wellY && mouseY < curY) {
                        this.hoveredCurrentEnchants = cachedSlot0Enchants;
                        this.hoverMouseX = mouseX;
                        this.hoverMouseY = mouseY;
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
                        this.hoveredNextStep = nextStep;
                        this.hoverMouseX = mouseX;
                        this.hoverMouseY = mouseY;
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

                    String actionName = "TEMPRA OGGETTO";
                    String icon = "⚔";
                    if ("pick".equals(cachedItemKey)) { actionName = "TEMPRA PICCONE"; icon = "⛏"; }
                    else if ("sword".equals(cachedItemKey)) { actionName = "TEMPRA SPADA"; icon = "⚔"; }
                    else if ("helmet".equals(cachedItemKey)) { actionName = "TEMPRA ELMO"; icon = "🪖"; }
                    else if ("chest".equals(cachedItemKey)) { actionName = "TEMPRA CORAZZA"; icon = "🥋"; }
                    else if ("legs".equals(cachedItemKey)) { actionName = "TEMPRA GAMBALI"; icon = "👖"; }
                    else if ("boots".equals(cachedItemKey)) { actionName = "TEMPRA STIVALI"; icon = "👢"; }
                    else if ("axe".equals(cachedItemKey)) { actionName = "TEMPRA ASCIA"; icon = "🪓"; }
                    else if ("bow".equals(cachedItemKey)) { actionName = "TEMPRA ARCO"; icon = "🏹"; }

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
                    extractor.fill(sideX + 6, moveY, sideX + sideW - 6, moveY + moveH, 0x301E293B);
                    extractor.outline(sideX + 6, moveY, sideW - 12, moveH, 0xFF334155);

                    extractor.text(this.font, "ENCHANT HELPER", sideX + 10, moveY + 4, 0xFF94A3B8, false);
                    extractor.text(this.font, "Nessun libro compatibile", sideX + 10, moveY + 16, 0xFFE2E8F0, false);
                    extractor.text(this.font, "trovato nell'inventario.", sideX + 10, moveY + 26, 0xFF94A3B8, false);
                    extractor.text(this.font, "Premi [N] per simulare!", sideX + 10, moveY + 37, 0xFF818CF8, false);
                }
            } else {
                // Empty state pulito e curato
                int cardY = sideY + 20;
                int cardH = sideH - 34;

                extractor.fill(sideX + 6, cardY, sideX + sideW - 6, cardY + cardH, 0x40161826);
                extractor.outline(sideX + 6, cardY, sideW - 12, cardH, 0xFF2A2D40);

                extractor.fakeItem(new ItemStack(Items.ANVIL), sideX + (sideW / 2) - 8, cardY + 12);

                extractor.centeredText(this.font, "Slot 1 vuoto", sideX + (sideW / 2), cardY + 34, 0xFFFFFFFF);
                extractor.centeredText(this.font, "Inserisci un oggetto", sideX + (sideW / 2), cardY + 48, 0xFF9CA3AF);
                extractor.centeredText(this.font, "da incantare per", sideX + (sideW / 2), cardY + 58, 0xFF9CA3AF);
                extractor.centeredText(this.font, "avviare l'assistente.", sideX + (sideW / 2), cardY + 68, 0xFF9CA3AF);

                int pillY = cardY + cardH - 18;
                extractor.fill(sideX + 12, pillY, sideX + sideW - 12, pillY + 14, 0x406366F1);
                extractor.outline(sideX + 12, pillY, sideW - 24, 14, 0x806366F1);
                extractor.centeredText(this.font, "Premi [N] per il tool", sideX + (sideW / 2), pillY + 3, 0xFFA5B4FC);
            }
        } catch (Throwable t) {
            // Silenzia
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void litness_renderHoverTooltips(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            if (this.hoveredNextStep != null) {
                renderStepTooltip(extractor, this.hoveredNextStep, this.hoverMouseX, this.hoverMouseY);
                this.hoveredNextStep = null;
            } else if (this.hoveredCurrentEnchants != null && !this.hoveredCurrentEnchants.isEmpty()) {
                renderCurrentEnchantsTooltip(extractor, this.hoveredCurrentEnchants, this.hoverMouseX, this.hoverMouseY);
                this.hoveredCurrentEnchants = null;
            }
        } catch (Throwable ignored) {}
    }

    private void renderStepTooltip(GuiGraphicsExtractor extractor, MergeStep step, int mouseX, int mouseY) {
        if (step == null) return;

        List<Component> tipLines = new ArrayList<>();
        int totalSteps = cachedSolution != null ? cachedSolution.steps.size() : 1;

        if (step.isBookToBook) {
            tipLines.add(Component.literal("§6🔨 PASSO 1/" + totalSteps + ": UNIONE LIBRI TRA LORO"));
            tipLines.add(Component.literal("§7Unisci questi due libri prima di applicarli:"));
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§9📖 Slot 1: §f" + step.leftLabel));
            for (EnchantApplied ea : step.leftEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                String eName = (ee != null ? ee.name : ea.id) + " " + AnvilOptimizer.toRoman(ea.level);
                tipLines.add(Component.literal("   §b• " + eName));
            }
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§d📖 Slot 2: §f" + step.rightLabel));
            for (EnchantApplied ea : step.rightEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                String eName = (ee != null ? ee.name : ea.id) + " " + AnvilOptimizer.toRoman(ea.level);
                tipLines.add(Component.literal("   §d• " + eName));
            }
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§a➔ Risultato: §f" + step.resultLabel));
            String peakStr = cachedSolution != null ? " §7(Picco: §d" + cachedSolution.peakCost + "/39 liv.§7)" : "";
            tipLines.add(Component.literal("§6💰 Costo Incudine: §e" + step.cost + " liv. XP" + peakStr));
        } else {
            String targetName = this.menu.getSlot(0).hasItem() ? this.menu.getSlot(0).getItem().getHoverName().getString() : "Oggetto";
            int penalty = this.menu.getSlot(0).hasItem() ? this.menu.getSlot(0).getItem().getOrDefault(DataComponents.REPAIR_COST, 0) : 0;

            String actionName = "TEMPRA OGGETTO";
            String icon = "⚔";
            if ("pick".equals(cachedItemKey)) { actionName = "TEMPRA PICCONE"; icon = "⛏"; }
            else if ("sword".equals(cachedItemKey)) { actionName = "TEMPRA SPADA"; icon = "⚔"; }
            else if ("helmet".equals(cachedItemKey)) { actionName = "TEMPRA ELMO"; icon = "🪖"; }
            else if ("chest".equals(cachedItemKey)) { actionName = "TEMPRA CORAZZA"; icon = "🥋"; }
            else if ("legs".equals(cachedItemKey)) { actionName = "TEMPRA GAMBALI"; icon = "👖"; }
            else if ("boots".equals(cachedItemKey)) { actionName = "TEMPRA STIVALI"; icon = "👢"; }
            else if ("axe".equals(cachedItemKey)) { actionName = "TEMPRA ASCIA"; icon = "🪓"; }
            else if ("bow".equals(cachedItemKey)) { actionName = "TEMPRA ARCO"; icon = "🏹"; }

            tipLines.add(Component.literal("§a" + icon + " PASSO 1/" + totalSteps + ": " + actionName));
            tipLines.add(Component.literal("§7Applica questo libro sull'oggetto nell'incudine:"));
            tipLines.add(Component.empty());

            tipLines.add(Component.literal("§eSlot 1 (Oggetto): §f" + targetName + " §7(Penalità: +" + penalty + " liv.)"));
            tipLines.add(Component.literal("§eSlot 2 (Libro): §f" + step.rightLabel));
            for (EnchantApplied ea : step.rightEnchants) {
                EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                String eName = (ee != null ? ee.name : ea.id) + " " + AnvilOptimizer.toRoman(ea.level);
                tipLines.add(Component.literal("   §a• " + eName));
            }
            tipLines.add(Component.empty());

            String peakStr = cachedSolution != null ? " §7(Picco: §d" + cachedSolution.peakCost + "/39 liv.§7)" : "";
            tipLines.add(Component.literal("§6💰 Costo Incudine: §e" + step.cost + " liv. XP" + peakStr));
        }

        tipLines.add(Component.literal("§8💡 Premi [N] o 'Guida Passi' in alto per l'intera sequenza"));

        // Calcolo dimensioni pixel-perfect
        int maxLineW = 160;
        for (Component c : tipLines) {
            maxLineW = Math.max(maxLineW, this.font.width(c));
        }
        int tipW = maxLineW + 16;
        int tipH = tipLines.size() * 11 + 8;

        int tipX = mouseX + 12;
        if (tipX + tipW > this.width - 8) {
            tipX = mouseX - tipW - 12;
            if (tipX < 8) tipX = Math.max(8, this.width - tipW - 8);
        }
        int tipY = Math.max(8, Math.min(mouseY - 12, this.height - tipH - 8));

        // Sfondo dark glass solido opaco (245/255 opacità) per coprire perfettamente ogni elemento sottostante
        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xF50E111A);
        // Barra accento superiore colorata (ambra per book-to-book, smeraldo per tempra oggetto)
        extractor.fill(tipX, tipY, tipX + tipW, tipY + 2, step.isBookToBook ? 0xFFF59E0B : 0xFF10B981);
        // Bordo sottile elegante
        extractor.outline(tipX, tipY, tipW, tipH, 0xFF2A2D40);

        int curY = tipY + 6;
        for (Component c : tipLines) {
            if (!c.getString().isEmpty()) {
                extractor.text(this.font, c, tipX + 8, curY, 0xFFFFFFFF, false);
            }
            curY += 11;
        }
    }

    private void renderCurrentEnchantsTooltip(GuiGraphicsExtractor extractor, List<EnchantApplied> enchants, int mouseX, int mouseY) {
        if (enchants == null || enchants.isEmpty()) return;

        List<Component> tipLines = new ArrayList<>();
        String targetName = this.menu.getSlot(0).hasItem() ? this.menu.getSlot(0).getItem().getHoverName().getString() : "Oggetto";
        tipLines.add(Component.literal("§a✨ INCANTESIMI ATTUALI SU " + targetName.toUpperCase()));
        tipLines.add(Component.empty());

        for (EnchantApplied ea : enchants) {
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
            String eName = (ee != null ? ee.name : ea.id) + " " + AnvilOptimizer.toRoman(ea.level);
            tipLines.add(Component.literal("§b• " + eName));
        }

        int maxLineW = 150;
        for (Component c : tipLines) {
            maxLineW = Math.max(maxLineW, this.font.width(c));
        }
        int tipW = maxLineW + 16;
        int tipH = tipLines.size() * 11 + 8;

        int tipX = mouseX + 12;
        if (tipX + tipW > this.width - 8) {
            tipX = mouseX - tipW - 12;
            if (tipX < 8) tipX = Math.max(8, this.width - tipW - 8);
        }
        int tipY = Math.max(8, Math.min(mouseY - 12, this.height - tipH - 8));

        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xF50E111A);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + 2, 0xFF6366F1);
        extractor.outline(tipX, tipY, tipW, tipH, 0xFF2A2D40);

        int curY = tipY + 6;
        for (Component c : tipLines) {
            if (!c.getString().isEmpty()) {
                extractor.text(this.font, c, tipX + 8, curY, 0xFFFFFFFF, false);
            }
            curY += 11;
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

            String descId = slot0.getItem().getDescriptionId().toLowerCase();
            String rawId = slot0.getItem().toString().toLowerCase();
            String itemKey = "sword";
            if (descId.contains("sword") || rawId.contains("sword")) itemKey = "sword";
            else if (descId.contains("pickaxe") || descId.contains("pick") || rawId.contains("pickaxe") || rawId.contains("pick")) itemKey = "pick";
            else if (descId.contains("helmet") || rawId.contains("helmet")) itemKey = "helmet";
            else if (descId.contains("chestplate") || descId.contains("chest") || rawId.contains("chestplate") || rawId.contains("chest")) itemKey = "chest";
            else if (descId.contains("leggings") || descId.contains("legs") || rawId.contains("leggings") || rawId.contains("legs")) itemKey = "legs";
            else if (descId.contains("boots") || rawId.contains("boots")) itemKey = "boots";
            else if (descId.contains("axe") || rawId.contains("axe")) itemKey = "axe";
            else if (descId.contains("bow") || rawId.contains("bow")) itemKey = "bow";
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
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            String id = ee != null ? ee.id : rawId;
            if (!id.isEmpty() && seen.add(id)) {
                list.add(new EnchantApplied(id, lvl));
            }
        }

        // 2. ENCHANTMENTS (armi, armature e attrezzi)
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
}

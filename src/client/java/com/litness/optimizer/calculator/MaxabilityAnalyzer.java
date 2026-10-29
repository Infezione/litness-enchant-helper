package com.litness.optimizer.calculator;

import com.litness.optimizer.calculator.AnvilOptimizer.BookInput;
import com.litness.optimizer.calculator.AnvilOptimizer.EnchantApplied;
import com.litness.optimizer.calculator.AnvilOptimizer.Solution;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import com.litness.optimizer.data.EnchantmentDatabase.ItemEntry;
import com.litness.optimizer.data.ItemTypeHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class MaxabilityAnalyzer {

    public enum Status {
        FULLY_MAXABLE,
        PARTIALLY_MAXABLE,
        SATURATED,
        PERMANENTLY_LOCKED
    }

    public static class MaxabilityResult {
        public Status status = Status.FULLY_MAXABLE;
        public boolean isFullyMaxable = true;
        public int currentPenalty = 0;
        public int currentWork = 0;
        public int currentEnchants = 0;
        public int remainingSlots = 12;
        public int maxAddable = 12;
        public String statusBadge = "§a✔ Maxabile al 100%";
        public String statusSummary = "Puoi maxare l'oggetto senza 'Troppo Costoso!'";
        public List<String> details = new ArrayList<>();
    }

    private static ItemStack lastAnalyzedStack = null;
    private static int lastAnalyzedPenalty = -1;
    private static MaxabilityResult cachedResult = null;

    public static MaxabilityResult analyze(ItemStack stack, List<EnchantApplied> existingEnchants) {
        if (stack == null || stack.isEmpty()) return new MaxabilityResult();

        int penalty = stack.getOrDefault(DataComponents.REPAIR_COST, 0);

        if (cachedResult != null && lastAnalyzedStack != null && ItemStack.isSameItemSameComponents(stack, lastAnalyzedStack) && lastAnalyzedPenalty == penalty) {
            return cachedResult;
        }

        MaxabilityResult res = new MaxabilityResult();
        res.currentPenalty = penalty;
        int work = 0;
        while ((1 << work) - 1 < penalty) work++;
        res.currentWork = work;

        res.currentEnchants = (existingEnchants != null) ? existingEnchants.size() : 0;
        res.remainingSlots = Math.max(0, 12 - res.currentEnchants);

        // Caso 1: Blocco permanente Minecraft (penalty >= 63)
        if (penalty >= 63) {
            res.status = Status.PERMANENTLY_LOCKED;
            res.isFullyMaxable = false;
            res.maxAddable = 0;
            res.statusBadge = "§c⛔ Bloccato (+63)";
            res.statusSummary = "Troppo Costoso: blocco permanente";
            res.details.add("§c⛔ Oggetto bloccato (Troppo Costoso!)");
            res.details.add("§7La penalità ha raggiunto +63 livelli.");
            res.details.add("§7Minecraft impedisce qualsiasi ulteriore operazione.");
            cache(stack, penalty, res);
            return res;
        }

        // Caso 2: Già a 12 incantesimi
        if (res.remainingSlots <= 0) {
            res.status = Status.FULLY_MAXABLE;
            res.isFullyMaxable = true;
            res.maxAddable = 0;
            res.statusBadge = "§6⭐ Già Maxato (12/12)";
            res.statusSummary = "Oggetto con 12/12 incantesimi";
            res.details.add("§6⭐ Oggetto già completato al massimo!");
            res.details.add("§7Hai già raggiunto il limite di 12 incantesimi.");
            cache(stack, penalty, res);
            return res;
        }

        // Caso 3: Penalità 0 -> sempre maxabile
        if (penalty == 0) {
            res.status = Status.FULLY_MAXABLE;
            res.isFullyMaxable = true;
            res.maxAddable = res.remainingSlots;
            res.statusBadge = "§a✔ Maxabile al 100%";
            res.statusSummary = "Puoi aggiungere tutti i " + res.remainingSlots + " incanti mancanti";
            res.details.add("§a✔ Maxabile al 100%!");
            res.details.add("§7L'oggetto non ha alcuna penalità pregressa (+0).");
            res.details.add("§7Puoi raggiungere 12/12 senza 'Troppo Costoso!'.");
            cache(stack, penalty, res);
            return res;
        }

        // Caso 4: Penalità pregressa (+1, +3, +7, +15, +31)
        String itemKey = ItemTypeHelper.detectItemKey(stack);
        ItemEntry targetItem = EnchantmentDatabase.getItem(itemKey);
        if (targetItem == null) targetItem = EnchantmentDatabase.getItem("sword");

        List<EnchantmentEntry> candidates = getApplicableCandidates(itemKey, existingEnchants);

        int maxSlotsToTest = Math.min(res.remainingSlots, candidates.size());
        if (maxSlotsToTest <= 0) {
            res.status = Status.FULLY_MAXABLE;
            res.isFullyMaxable = true;
            res.maxAddable = 0;
            res.statusBadge = "§a✔ Compatibilità piena";
            res.statusSummary = "Nessun altro incanto compatibile rimasto";
            cache(stack, penalty, res);
            return res;
        }

        // Creiamo la lista di libri candidati per il test matematico
        List<BookInput> candidateBooks = new ArrayList<>();
        for (int i = 0; i < maxSlotsToTest; i++) {
            EnchantmentEntry ee = candidates.get(i);
            BookInput b = new BookInput(0);
            b.enchantments.add(new EnchantApplied(ee.id, ee.maxLevel));
            b.explicitCost = ee.getAnvilCostNumeric();
            candidateBooks.add(b);
        }

        // Testiamo se tutti i candidateBooks ci stanno senza sforare 39
        Solution fullSol = AnvilOptimizer.optimize(candidateBooks, res.currentWork, targetItem, Collections.emptyList());
        if (fullSol != null && fullSol.peakCost <= 39) {
            res.status = Status.FULLY_MAXABLE;
            res.isFullyMaxable = true;
            res.maxAddable = maxSlotsToTest;
            res.statusBadge = "§a✔ Maxabile (" + (res.currentEnchants + maxSlotsToTest) + "/12)";
            res.statusSummary = "Puoi ancora aggiungere tutti i " + maxSlotsToTest + " incanti mancanti";
            res.details.add("§a✔ Maxabile al 100% con la guida!");
            res.details.add("§7Anche con la penalità attuale (+" + penalty + "),");
            res.details.add("§7puoi ancora aggiungere tutti i " + maxSlotsToTest + " incantesimi mancanti.");
            res.details.add("§8Picco massimo stimato: " + fullSol.peakCost + "/39 livelli.");
            cache(stack, penalty, res);
            return res;
        }

        // Se non entrano tutti, cerchiamo il massimo K che ci sta
        int bestK = 0;
        int bestPeak = 0;
        for (int k = maxSlotsToTest - 1; k >= 1; k--) {
            List<BookInput> subList = new ArrayList<>(candidateBooks.subList(0, k));
            Solution subSol = AnvilOptimizer.optimize(subList, res.currentWork, targetItem, Collections.emptyList());
            if (subSol != null && subSol.peakCost <= 39) {
                bestK = k;
                bestPeak = subSol.peakCost;
                break;
            }
        }

        if (bestK > 0) {
            res.status = Status.PARTIALLY_MAXABLE;
            res.isFullyMaxable = false;
            res.maxAddable = bestK;
            res.statusBadge = "§c⚠️ Troppo Costoso per full! §e(Max +" + bestK + ")";
            res.statusSummary = "Puoi aggiungere ancora fino a " + bestK + " incantesimi";
            res.details.add("§c⚠️ Troppo Costoso se provi a maxarlo tutto!");
            res.details.add("§7A causa delle fusioni precedenti effettuate senza guida (Penalità +" + penalty + "),");
            res.details.add("§7applicando troppi libri l'incudine si bloccherebbe.");
            res.details.add("");
            res.details.add("§e💡 Puoi ancora aggiungere fino a " + bestK + " incantesimi:");
            res.details.add("§7Totale raggiungibile: §f" + (res.currentEnchants + bestK) + "/12 incantesimi§7.");
            res.details.add("§8Picco previsto: " + bestPeak + "/39 liv. (Unisci sempre i libri tra loro!)");
        } else {
            res.status = Status.SATURATED;
            res.isFullyMaxable = false;
            res.maxAddable = 0;
            res.statusBadge = "§c⛔ Saturo: Troppo Costoso (0 incanti)";
            res.statusSummary = "Nessun altro incanto può essere applicato";
            res.details.add("§c⛔ Oggetto saturo (Troppo Costoso!)");
            res.details.add("§7Con la penalità attuale (+" + penalty + "), qualsiasi");
            res.details.add("§7ulteriore libro supera il limite di 39 livelli.");
        }

        cache(stack, penalty, res);
        return res;
    }

    private static void cache(ItemStack stack, int penalty, MaxabilityResult res) {
        lastAnalyzedStack = stack.copy();
        lastAnalyzedPenalty = penalty;
        cachedResult = res;
    }

    private static List<EnchantmentEntry> getApplicableCandidates(String itemKey, List<EnchantApplied> existing) {
        List<EnchantmentEntry> list = new ArrayList<>();
        Set<String> existingIds = new HashSet<>();
        Set<String> conflictNames = new HashSet<>();

        if (existing != null) {
            for (EnchantApplied ea : existing) {
                existingIds.add(ea.id.toLowerCase());
                List<String> confs = EnchantmentDatabase.getResolvedConflicts(ea.id);
                for (String c : confs) conflictNames.add(c.toLowerCase());
            }
        }

        for (EnchantmentEntry ee : EnchantmentDatabase.getAllEnchantments()) {
            if (ee == null) continue;
            if (existingIds.contains(ee.id.toLowerCase())) continue;
            if (conflictNames.contains(ee.id.toLowerCase()) || conflictNames.contains(ee.name.toLowerCase())) continue;
            if (EnchantmentDatabase.isApplicable(ee, itemKey)) {
                list.add(ee);
            }
        }

        list.sort((a, b) -> {
            boolean aVan = "vanilla".equalsIgnoreCase(a.category);
            boolean bVan = "vanilla".equalsIgnoreCase(b.category);
            if (aVan && !bVan) return -1;
            if (!aVan && bVan) return 1;
            return Integer.compare(a.getAnvilCostNumeric(), b.getAnvilCostNumeric());
        });

        return list;
    }
}

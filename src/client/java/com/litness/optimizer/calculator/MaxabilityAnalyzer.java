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
        public String statusBadge = "§a• Maxabile al 100%";
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
            res.statusBadge = "§c[!] Bloccato (+63)";
            res.statusSummary = "Troppo Costoso: blocco permanente";
            res.details.add("§c[!] Oggetto bloccato (Troppo Costoso!)");
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
            res.statusBadge = "§6• Già Maxato (12/12)";
            res.statusSummary = "Oggetto con 12/12 incantesimi";
            res.details.add("§6• Oggetto già completato al massimo!");
            res.details.add("§7Hai già raggiunto il limite di 12 incantesimi.");
            cache(stack, penalty, res);
            return res;
        }

        // Caso 3: Penalità 0 -> sempre maxabile
        if (penalty == 0) {
            res.status = Status.FULLY_MAXABLE;
            res.isFullyMaxable = true;
            res.maxAddable = res.remainingSlots;
            res.statusBadge = "§a• Maxabile al 100%";
            res.statusSummary = "Puoi aggiungere tutti i " + res.remainingSlots + " incanti mancanti";
            res.details.add("§a• Maxabile al 100%!");
            res.details.add("§7L'oggetto non ha alcuna penalità pregressa (+0).");
            res.details.add("§7Puoi raggiungere 12/12 senza 'Troppo Costoso!'.");
            cache(stack, penalty, res);
            return res;
        }

        // Caso 4: Penalità pregressa (+1, +3, +7, +15, +31)
        // Calcolo matematico analitico istantaneo (zero chiamate DP su render thread per evitare qualsiasi lag!)
        String itemKey = ItemTypeHelper.detectItemKey(stack);
        List<EnchantmentEntry> candidates = getApplicableCandidates(itemKey, existingEnchants);
        int maxSlotsToTest = Math.min(res.remainingSlots, candidates.size());
        if (maxSlotsToTest <= 0) {
            res.status = Status.FULLY_MAXABLE;
            res.isFullyMaxable = true;
            res.maxAddable = 0;
            res.statusBadge = "§a• Compatibilità piena";
            res.statusSummary = "Nessun altro incanto compatibile rimasto";
            cache(stack, penalty, res);
            return res;
        }

        // Modello matematico dell'incudine di Minecraft:
        // penalty = 2^work - 1. Limite max costo XP = 39.
        // work 0 (pen 0): 100% maxabile fino a 12 incanti
        // work 1 (pen 1): 100% maxabile fino a 12 incanti (con merge ad albero binario)
        // work 2 (pen 3): 100% maxabile fino a 12 incanti (con merge ad albero binario)
        // work 3 (pen 7): fino a 10 incanti addizionali
        // work 4 (pen 15): fino a 6 incanti addizionali
        // work 5 (pen 31): fino a 2 incanti addizionali
        int estimatedAddable;
        if (work <= 2) {
            estimatedAddable = maxSlotsToTest; // Penalità +0, +1 e +3 possono sempre raggiungere 12/12 con merge ad albero!
        } else if (work == 3) {
            estimatedAddable = Math.min(maxSlotsToTest, 10);
        } else if (work == 4) {
            estimatedAddable = Math.min(maxSlotsToTest, 6);
        } else if (work == 5) {
            estimatedAddable = Math.min(maxSlotsToTest, 2);
        } else {
            estimatedAddable = 0;
        }

        if (estimatedAddable >= maxSlotsToTest) {
            res.status = Status.FULLY_MAXABLE;
            res.isFullyMaxable = true;
            res.maxAddable = maxSlotsToTest;
            int totalEnchantsAchievable = res.currentEnchants + maxSlotsToTest;
            res.statusBadge = "§a• Maxabile al 100% (" + totalEnchantsAchievable + "/12)";
            res.statusSummary = "Puoi ancora aggiungere tutti i " + maxSlotsToTest + " incanti mancanti";
            res.details.add("§a• Maxabile al 100% (" + totalEnchantsAchievable + "/12)!");
            res.details.add("§7Con la penalità attuale (+" + penalty + " liv., Lavori: " + work + "),");
            res.details.add("§7puoi raggiungere " + totalEnchantsAchievable + "/12 incantesimi senza blocchi!");
            res.details.add("§bUnisci i libri tra loro in albero binario prima di applicarli.");
            cache(stack, penalty, res);
            return res;
        } else if (estimatedAddable > 0) {
            res.status = Status.PARTIALLY_MAXABLE;
            res.isFullyMaxable = false;
            res.maxAddable = estimatedAddable;
            int totalEnchantsAchievable = res.currentEnchants + estimatedAddable;
            res.statusBadge = "§c[!] Troppo Costoso per full! §e(Max +" + estimatedAddable + ")";
            res.statusSummary = "Puoi aggiungere ancora fino a " + estimatedAddable + " incantesimi";
            res.details.add("§c[!] Troppo Costoso se provi a maxarlo tutto!");
            res.details.add("§7A causa delle fusioni precedenti effettuate senza guida (Penalità +" + penalty + "),");
            res.details.add("§7applicando troppi libri l'incudine si bloccherebbe.");
            res.details.add("");
            res.details.add("§e• Puoi ancora aggiungere fino a " + estimatedAddable + " incantesimi:");
            res.details.add("§7Totale raggiungibile: §f" + totalEnchantsAchievable + "/12 incantesimi§7.");
            res.details.add("§8(Unisci sempre i libri tra loro!)");
            cache(stack, penalty, res);
            return res;
        } else {
            res.status = Status.SATURATED;
            res.isFullyMaxable = false;
            res.maxAddable = 0;
            res.statusBadge = "§c[!] Saturo: Troppo Costoso (0 incanti)";
            res.statusSummary = "Nessun altro incanto può essere applicato";
            res.details.add("§c[!] Oggetto saturo (Troppo Costoso!)");
            res.details.add("§7Con la penalità attuale (+" + penalty + "), qualsiasi");
            res.details.add("§7ulteriore libro supera il limite di 39 livelli.");
            cache(stack, penalty, res);
            return res;
        }
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
                existingIds.add(com.litness.optimizer.data.LitnessI18n.getBaseId(ea.id));
                List<String> confs = EnchantmentDatabase.getResolvedConflicts(ea.id);
                for (String c : confs) conflictNames.add(c.toLowerCase());
            }
        }

        for (EnchantmentEntry ee : EnchantmentDatabase.getAllEnchantments()) {
            if (ee == null) continue;
            String baseId = com.litness.optimizer.data.LitnessI18n.getBaseId(ee.id);
            String baseName = com.litness.optimizer.data.LitnessI18n.getBaseEnchantName(ee).toLowerCase();
            if (existingIds.contains(ee.id.toLowerCase()) || existingIds.contains(baseId)) continue;
            if (conflictNames.contains(ee.id.toLowerCase()) || conflictNames.contains(baseId) || conflictNames.contains(ee.name.toLowerCase()) || conflictNames.contains(baseName)) continue;
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

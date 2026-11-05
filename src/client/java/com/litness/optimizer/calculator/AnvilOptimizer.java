package com.litness.optimizer.calculator;

import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import com.litness.optimizer.data.EnchantmentDatabase.ItemEntry;

import java.util.*;

public class AnvilOptimizer {
    public static final int MAX_OPERATION_COST = 39;

    public static class EnchantApplied {
        public String id;
        public int level;

        public EnchantApplied(String id, int level) {
            this.id = id;
            this.level = level;
        }
    }

    public static class BookInput {
        public int works;
        public List<EnchantApplied> enchantments = new ArrayList<>();
        public int explicitCost = -1;

        public BookInput(int works) {
            this.works = works;
        }

        public BookInput(int works, List<EnchantApplied> enchants) {
            this.works = works;
            this.enchantments = enchants;
        }

        public BookInput(int works, List<EnchantApplied> enchants, int explicitCost) {
            this.works = works;
            this.enchantments = enchants;
            this.explicitCost = explicitCost;
        }
    }

    public static class MergeStep {
        public boolean isBookToBook;
        public String leftLabel;
        public String rightLabel;
        public String resultLabel;
        public List<EnchantApplied> leftEnchants = new ArrayList<>();
        public List<EnchantApplied> rightEnchants = new ArrayList<>();
        public List<EnchantApplied> resultEnchants = new ArrayList<>();
        public int cost;
        public int transferCost;
        public int leftWork;
        public int rightWork;
        public int resultWork;

        public int leftPenalty() {
            return getPriorPenalty(leftWork);
        }

        public int rightPenalty() {
            return getPriorPenalty(rightWork);
        }
    }

    public static class Solution {
        public int totalCost;
        public int peakCost;
        public List<MergeStep> steps = new ArrayList<>();
    }

    public static int getPriorPenalty(int work) {
        if (work < 0) return 0;
        if (work >= 30) return 1073741823;
        return (1 << work) - 1;
    }

    public static class SimNode {
        public int work;
        public int cost;
        public String label;
        public List<EnchantApplied> enchants = new ArrayList<>();
        public List<MergeStep> steps = new ArrayList<>();
        public int totalCost;
        public int peakCost;
    }

    public static String getBookSignature(BookInput b) {
        if (b == null) return "";
        List<String> list = new ArrayList<>();
        if (b.enchantments != null) {
            for (EnchantApplied ea : b.enchantments) {
                list.add(ea.id + ":" + ea.level);
            }
        }
        Collections.sort(list);
        return b.works + "|" + getBookCost(b) + "|" + String.join(",", list);
    }

    static int popcount(int v) { return Integer.bitCount(v); }

    private static class BookNode {
        int work, total, peak, cost, transfer, mask;
        BookNode left, right;
    }

    private static class ApplyNode {
        int targetWork, total, peak, cost, transfer;
        ApplyNode prev;
        BookNode bookNode;
    }

    private static void insertParetoNode(List<BookNode> list, BookNode next) {
        for (int i = 0; i < list.size(); i++) {
            BookNode curr = list.get(i);
            if (curr.total <= next.total && curr.peak <= next.peak) return;
        }
        list.removeIf(curr -> next.total <= curr.total && next.peak <= curr.peak);
        list.add(next);
    }

    private static void insertParetoApply(List<ApplyNode> list, ApplyNode next) {
        for (int i = 0; i < list.size(); i++) {
            ApplyNode curr = list.get(i);
            if (curr.peak <= next.peak && curr.total <= next.total) return;
        }
        list.removeIf(curr -> next.peak <= curr.peak && next.total <= curr.total);
        list.add(next);
    }

    private static void collectBookSteps(BookNode node, List<BookInput> rawBooks, List<MergeStep> steps, Set<BookNode> visited) {
        if (node == null || node.left == null || node.right == null) return;
        if (!visited.add(node)) return;

        collectBookSteps(node.left, rawBooks, steps, visited);
        collectBookSteps(node.right, rawBooks, steps, visited);

        MergeStep s = new MergeStep();
        s.isBookToBook = true;
        s.leftLabel = formatMaskLabel(node.left.mask, rawBooks);
        s.rightLabel = formatMaskLabel(node.right.mask, rawBooks);
        s.resultLabel = formatMaskLabel(node.mask, rawBooks);
        s.leftEnchants = getEnchantsForMask(node.left.mask, rawBooks);
        s.rightEnchants = getEnchantsForMask(node.right.mask, rawBooks);
        s.resultEnchants = getEnchantsForMask(node.mask, rawBooks);
        s.cost = node.cost;
        s.transferCost = node.transfer;
        s.leftWork = node.left.work;
        s.rightWork = node.right.work;
        s.resultWork = node.work;
        steps.add(s);
    }

    public static Solution optimize(List<BookInput> rawBooks, int targetWorks, ItemEntry targetItem, List<EnchantApplied> existingEnchants) {
        if (rawBooks == null || rawBooks.isEmpty()) return null;

        String itemLabel = targetItem != null ? 
            (com.litness.optimizer.data.LitnessI18n.isEnglish() ? targetItem.name : com.litness.optimizer.data.LitnessI18n.getItemPieceName(targetItem.id)) : "Oggetto";

        int count = rawBooks.size();
        if (count > 12) {
            rawBooks = rawBooks.subList(0, 12);
            count = 12;
        }

        int[] enchantmentCosts = new int[count];
        for (int i = 0; i < count; i++) {
            enchantmentCosts[i] = getBookCost(rawBooks.get(i));
        }

        int stateCount = 1 << count;
        int fullMask = stateCount - 1;

        int[] costsByMask = new int[stateCount];
        for (int mask = 1; mask < stateCount; mask++) {
            int bit = mask & -mask;
            int index = 31 - Integer.numberOfLeadingZeros(bit);
            costsByMask[mask] = costsByMask[mask ^ bit] + enchantmentCosts[index];
        }

        Map<Integer, Map<Integer, List<BookNode>>> builds = new HashMap<>();
        for (int m = 0; m < stateCount; m++) builds.put(m, new HashMap<>());

        for (int i = 0; i < count; i++) {
            int mask = 1 << i;
            BookNode n = new BookNode();
            n.work = rawBooks.get(i).works;
            n.mask = mask;
            builds.get(mask).computeIfAbsent(n.work, k -> new ArrayList<>()).add(n);
        }

        for (int size = 2; size <= count; size++) {
            for (int mask = 1; mask < stateCount; mask++) {
                if (popcount(mask) != size) continue;
                Map<Integer, List<BookNode>> targetMap = builds.get(mask);
                for (int leftMask = (mask - 1) & mask; leftMask > 0; leftMask = (leftMask - 1) & mask) {
                    int rightMask = mask ^ leftMask;
                    if (rightMask == 0 || leftMask > rightMask) continue;

                    int[][] orientations = (leftMask == rightMask)
                            ? new int[][]{{leftMask, rightMask}}
                            : new int[][]{{leftMask, rightMask}, {rightMask, leftMask}};

                    for (int[] orient : orientations) {
                        int tMask = orient[0];
                        int sMask = orient[1];
                        Map<Integer, List<BookNode>> leftMap = builds.get(tMask);
                        Map<Integer, List<BookNode>> rightMap = builds.get(sMask);

                        for (var le : leftMap.entrySet()) {
                            int lWork = le.getKey();
                            for (var re : rightMap.entrySet()) {
                                int rWork = re.getKey();
                                int cost = costsByMask[sMask] + getPriorPenalty(lWork) + getPriorPenalty(rWork);
                                if (cost > MAX_OPERATION_COST) continue;
                                int resWork = Math.max(lWork, rWork) + 1;
                                List<BookNode> candidates = targetMap.computeIfAbsent(resWork, k -> new ArrayList<>());

                                for (BookNode leftNode : le.getValue()) {
                                    for (BookNode rightNode : re.getValue()) {
                                        BookNode next = new BookNode();
                                        next.work = resWork;
                                        next.total = leftNode.total + rightNode.total + cost;
                                        next.peak = Math.max(Math.max(leftNode.peak, rightNode.peak), cost);
                                        next.cost = cost;
                                        next.transfer = costsByMask[sMask];
                                        next.mask = mask;
                                        next.left = leftNode;
                                        next.right = rightNode;
                                        insertParetoNode(candidates, next);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Map<Integer, Map<Integer, List<ApplyNode>>> states = new HashMap<>();
        for (int m = 0; m < stateCount; m++) states.put(m, new HashMap<>());

        ApplyNode root = new ApplyNode();
        root.targetWork = targetWorks;
        states.get(fullMask).computeIfAbsent(targetWorks, k -> new ArrayList<>()).add(root);

        for (int size = count; size >= 1; size--) {
            for (int remaining = 1; remaining < stateCount; remaining++) {
                if (popcount(remaining) != size) continue;
                Map<Integer, List<ApplyNode>> currMap = states.get(remaining);
                if (currMap.isEmpty()) continue;

                for (var entry : new ArrayList<>(currMap.entrySet())) {
                    int currTargetWork = entry.getKey();
                    List<ApplyNode> prefixes = entry.getValue();

                    for (int group = remaining; group > 0; group = (group - 1) & remaining) {
                        Map<Integer, List<BookNode>> groupBuilds = builds.get(group);
                        if (groupBuilds == null || groupBuilds.isEmpty()) continue;

                        int nextMask = remaining ^ group;
                        Map<Integer, List<ApplyNode>> nextWorkMap = states.get(nextMask);

                        for (var be : groupBuilds.entrySet()) {
                            int bookWork = be.getKey();
                            int cost = costsByMask[group] + getPriorPenalty(currTargetWork) + getPriorPenalty(bookWork);
                            if (cost > MAX_OPERATION_COST) continue;

                            int nextWork = Math.max(currTargetWork, bookWork) + 1;
                            List<ApplyNode> targetList = nextWorkMap.computeIfAbsent(nextWork, k -> new ArrayList<>());

                            for (ApplyNode prefix : prefixes) {
                                for (BookNode bNode : be.getValue()) {
                                    ApplyNode next = new ApplyNode();
                                    next.targetWork = nextWork;
                                    next.total = prefix.total + bNode.total + cost;
                                    next.peak = Math.max(Math.max(prefix.peak, bNode.peak), cost);
                                    next.cost = cost;
                                    next.transfer = costsByMask[group];
                                    next.prev = prefix;
                                    next.bookNode = bNode;
                                    insertParetoApply(targetList, next);
                                }
                            }
                        }
                    }
                }
            }
        }

        List<ApplyNode> solutions = new ArrayList<>();
        for (List<ApplyNode> l : states.get(0).values()) solutions.addAll(l);

        if (solutions.isEmpty()) {
            return null;
        }

        solutions.sort((a, b) -> a.peak != b.peak ? Integer.compare(a.peak, b.peak) : (a.total != b.total ? Integer.compare(a.total, b.total) : Integer.compare(a.targetWork, b.targetWork)));
        ApplyNode best = solutions.get(0);

        List<ApplyNode> applySeq = new ArrayList<>();
        ApplyNode cur = best;
        while (cur != null && cur.bookNode != null) {
            applySeq.add(cur);
            cur = cur.prev;
        }
        Collections.reverse(applySeq);

        List<MergeStep> allSteps = new ArrayList<>();
        Set<BookNode> visited = new HashSet<>();

        // 1. Raccogli tutte le combinazioni Libro + Libro all'inizio (post-order)
        for (ApplyNode an : applySeq) {
            collectBookSteps(an.bookNode, rawBooks, allSteps, visited);
        }

        // 2. Applica sequenzialmente i libri composti sull'oggetto base
        int curToolWork = targetWorks;
        List<EnchantApplied> currentToolEnchants = new ArrayList<>();
        if (existingEnchants != null) {
            currentToolEnchants.addAll(existingEnchants);
        }

        for (ApplyNode an : applySeq) {
            List<EnchantApplied> bookEnchants = getEnchantsForMask(an.bookNode.mask, rawBooks);
            List<EnchantApplied> resEnchants = new ArrayList<>(currentToolEnchants);
            for (EnchantApplied ea : bookEnchants) {
                boolean found = false;
                for (int k = 0; k < resEnchants.size(); k++) {
                    if (resEnchants.get(k).id.equals(ea.id)) {
                        found = true;
                        if (ea.level > resEnchants.get(k).level) {
                            resEnchants.set(k, ea);
                        }
                        break;
                    }
                }
                if (!found) resEnchants.add(ea);
            }

            MergeStep s = new MergeStep();
            s.isBookToBook = false;
            s.leftLabel = itemLabel;
            s.rightLabel = formatMaskLabel(an.bookNode.mask, rawBooks);
            s.resultLabel = itemLabel;
            s.leftEnchants = new ArrayList<>(currentToolEnchants);
            s.rightEnchants = new ArrayList<>(bookEnchants);
            s.resultEnchants = new ArrayList<>(resEnchants);
            s.cost = an.cost;
            s.transferCost = an.transfer;
            s.leftWork = curToolWork;
            s.rightWork = an.bookNode.work;
            s.resultWork = an.targetWork;

            allSteps.add(s);
            curToolWork = an.targetWork;
            currentToolEnchants = resEnchants;
        }

        Solution sol = new Solution();
        sol.totalCost = best.total;
        sol.peakCost = best.peak;
        sol.steps = allSteps;
        return sol;
    }

    public static String formatEnchantsList(List<EnchantApplied> enchants) {
        if (enchants == null || enchants.isEmpty()) return com.litness.optimizer.data.LitnessI18n.isEnglish() ? "Book" : "Libro";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (EnchantApplied ea : enchants) {
            if (!first) sb.append(" + ");
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
            sb.append(formatEnchantName(ee != null ? com.litness.optimizer.data.LitnessI18n.getEnchantName(ee) : ea.id, ea.level));
            first = false;
        }
        return sb.toString();
    }

    public static List<EnchantApplied> getEnchantsForMask(int mask, List<BookInput> books) {
        List<EnchantApplied> list = new ArrayList<>();
        if (books == null) return list;
        for (int i = 0; i < books.size(); i++) {
            if ((mask & (1 << i)) != 0 && i < books.size()) {
                list.addAll(books.get(i).enchantments);
            }
        }
        return list;
    }

    public static String formatMaskLabel(int mask, List<BookInput> books) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < books.size(); i++) {
            if ((mask & (1 << i)) != 0) {
                for (EnchantApplied ea : books.get(i).enchantments) {
                    if (!first) sb.append(" + ");
                    EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                    sb.append(formatEnchantName(ee != null ? com.litness.optimizer.data.LitnessI18n.getEnchantName(ee) : ea.id, ea.level));
                    first = false;
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : (com.litness.optimizer.data.LitnessI18n.isEnglish() ? "Book" : "Libro");
    }

    public static int getBookCost(BookInput b) {
        if (b == null) return 1;
        if (b.explicitCost > 0) return b.explicitCost;
        if (b.enchantments.isEmpty()) return 1;
        int sum = 0;
        for (EnchantApplied ea : b.enchantments) {
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
            if (ee != null) {
                sum += EnchantmentDatabase.getEnchantmentCost(ee, ea.level);
            }
        }
        return Math.max(1, sum);
    }

    public static String formatBookLabel(BookInput b) {
        if (b == null || b.enchantments.isEmpty()) return com.litness.optimizer.data.LitnessI18n.isEnglish() ? "Book" : "Libro";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (EnchantApplied ea : b.enchantments) {
            if (!first) sb.append(" + ");
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
            sb.append(formatEnchantName(ee != null ? com.litness.optimizer.data.LitnessI18n.getEnchantName(ee) : ea.id, ea.level));
            first = false;
        }
        return sb.length() > 0 ? sb.toString() : (com.litness.optimizer.data.LitnessI18n.isEnglish() ? "Book" : "Libro");
    }

    public static String formatEnchantName(String baseName, int level) {
        if (baseName == null) return "";
        String trimmed = baseName.trim();
        com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry ee = com.litness.optimizer.data.EnchantmentDatabase.getEnchantment(trimmed);
        if (ee != null) {
            return formatEnchantName(ee, level);
        }
        String translated = com.litness.optimizer.data.LitnessI18n.translateEnchantName(trimmed);
        if (translated.matches(".*\\s+[IVXLCDM]+$") || translated.matches(".*\\s+[0-9]+$")) {
            return translated;
        }
        if (level > 1) {
            return translated + " " + toRoman(level);
        }
        return translated;
    }

    public static String formatEnchantName(EnchantmentEntry ee, int level) {
        if (ee == null) return "";
        String name = com.litness.optimizer.data.LitnessI18n.getEnchantName(ee);
        if (name.matches(".*\\s+[IVXLCDM]+$") || name.matches(".*\\s+[0-9]+$")) {
            return name;
        }
        if (level <= 1 && ee.maxLevel <= 1) {
            return name;
        }
        if (level > 0) {
            return name + " " + toRoman(level);
        }
        return name;
    }

    public static String toRoman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (n >= 0 && n < r.length) return r[n];
        return String.valueOf(n);
    }
}

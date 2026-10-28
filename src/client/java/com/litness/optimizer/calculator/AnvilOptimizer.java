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

    private static class Node {
        int work;
        int total;
        int peak;
        int cost;
        int transferCost;
        int mask;
        Node left;
        Node right;

        String getSignature(List<BookInput> books) {
            if (left == null || right == null) {
                return formatMaskLabel(mask, books);
            }
            return "(" + left.getSignature(books) + "+" + right.getSignature(books) + ")";
        }
    }

    private static class ApplyNode {
        int targetWork;
        int total;
        int peak;
        int cost;
        int transferCost;
        ApplyNode prev;
        Node bookNode;

        String getSignature(List<BookInput> books) {
            String bSig = bookNode != null ? bookNode.getSignature(books) : "";
            String pSig = prev != null ? prev.getSignature(books) : "";
            return targetWork + ":" + cost + ":" + bSig + ">" + pSig;
        }
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

    public static Solution optimize(List<BookInput> rawBooks, int targetWorks, ItemEntry targetItem, List<EnchantApplied> existingEnchants) {
        if (rawBooks == null || rawBooks.isEmpty()) return null;

        List<MergeStep> prepSteps = new ArrayList<>();
        List<BookInput> books = new ArrayList<>();
        for (BookInput bi : rawBooks) {
            BookInput copy = new BookInput(bi.works);
            copy.enchantments.addAll(bi.enchantments);
            copy.explicitCost = bi.explicitCost;
            books.add(copy);
        }

        // Ordinamento canonico deterministico a monte
        Comparator<BookInput> canonicalBookComparator = (b1, b2) -> {
            if (b1.works != b2.works) return Integer.compare(b1.works, b2.works);
            int c1 = getBookCost(b1);
            int c2 = getBookCost(b2);
            if (c1 != c2) return Integer.compare(c2, c1);
            return getBookSignature(b1).compareTo(getBookSignature(b2));
        };
        books.sort(canonicalBookComparator);

        // Se ci sono più di 8 libri, pre-combiniamo a coppie i libri più economici
        // per garantire esecuzione istantanea (<2ms) senza sacrificare alcun incantesimo
        while (books.size() > 8) {
            books.sort((b1, b2) -> {
                if (b1.works != b2.works) return Integer.compare(b1.works, b2.works);
                int c1 = getBookCost(b1);
                int c2 = getBookCost(b2);
                if (c1 != c2) return Integer.compare(c1, c2);
                return getBookSignature(b1).compareTo(getBookSignature(b2));
            });

            BookInput b1 = books.remove(0);
            BookInput b2 = books.remove(0);

            int transfer = getBookCost(b2);
            int cost = transfer + getPriorPenalty(b1.works) + getPriorPenalty(b2.works);
            int resultWork = Math.max(b1.works, b2.works) + 1;

            BookInput combined = new BookInput(resultWork);
            combined.enchantments.addAll(b1.enchantments);
            combined.enchantments.addAll(b2.enchantments);
            int expCost1 = b1.explicitCost > 0 ? b1.explicitCost : getBookCost(b1);
            int expCost2 = b2.explicitCost > 0 ? b2.explicitCost : getBookCost(b2);
            combined.explicitCost = expCost1 + expCost2;

            MergeStep step = new MergeStep();
            step.isBookToBook = true;
            step.leftLabel = formatBookLabel(b1);
            step.rightLabel = formatBookLabel(b2);
            step.resultLabel = formatBookLabel(combined);
            step.leftEnchants.addAll(b1.enchantments);
            step.rightEnchants.addAll(b2.enchantments);
            step.resultEnchants.addAll(combined.enchantments);
            step.cost = cost;
            step.transferCost = transfer;
            step.leftWork = b1.works;
            step.rightWork = b2.works;
            step.resultWork = resultWork;

            prepSteps.add(step);
            books.add(combined);
        }

        int count = books.size();
        int stateCount = 1 << count;
        int fullMask = stateCount - 1;

        int[] enchantmentCosts = new int[count];
        for (int i = 0; i < count; i++) {
            BookInput b = books.get(i);
            int sum = 0;
            if (b.explicitCost > 0) {
                sum = b.explicitCost;
            } else {
                for (EnchantApplied ea : b.enchantments) {
                    EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
                    if (ee != null) {
                        sum += EnchantmentDatabase.getEnchantmentCost(ee, ea.level);
                    }
                }
            }
            enchantmentCosts[i] = Math.max(1, sum);
        }

        int[] costsByMask = new int[stateCount];
        for (int mask = 1; mask < stateCount; mask++) {
            int bit = mask & -mask;
            int index = 31 - Integer.numberOfLeadingZeros(bit);
            costsByMask[mask] = costsByMask[mask ^ bit] + enchantmentCosts[index];
        }

        // Costruzione combinazioni libro-libro deterministica
        Map<Integer, Map<Integer, List<Node>>> builds = new LinkedHashMap<>();
        for (int m = 0; m < stateCount; m++) builds.put(m, new TreeMap<>());

        // Libri base (dimensione 1)
        for (int i = 0; i < count; i++) {
            int mask = 1 << i;
            Node n = new Node();
            n.work = books.get(i).works;
            n.total = 0;
            n.peak = 0;
            n.mask = mask;
            builds.get(mask).computeIfAbsent(n.work, k -> new ArrayList<>()).add(n);
        }

        // Fusioni tra libri
        for (int size = 2; size <= count; size++) {
            for (int mask = 1; mask < stateCount; mask++) {
                if (Integer.bitCount(mask) != size) continue;

                Map<Integer, List<Node>> targetBuildMap = builds.get(mask);

                for (int leftMask = (mask - 1) & mask; leftMask > 0; leftMask = (leftMask - 1) & mask) {
                    int rightMask = mask ^ leftMask;
                    if (rightMask == 0 || leftMask > rightMask) continue;

                    int[][] orientations = (leftMask == rightMask)
                            ? new int[][]{{leftMask, rightMask}}
                            : new int[][]{{leftMask, rightMask}, {rightMask, leftMask}};

                    for (int[] orient : orientations) {
                        int tMask = orient[0];
                        int sMask = orient[1];

                        Map<Integer, List<Node>> leftMap = builds.get(tMask);
                        Map<Integer, List<Node>> rightMap = builds.get(sMask);

                        for (Map.Entry<Integer, List<Node>> le : leftMap.entrySet()) {
                            int lWork = le.getKey();
                            for (Map.Entry<Integer, List<Node>> re : rightMap.entrySet()) {
                                int rWork = re.getKey();
                                int cost = costsByMask[sMask] + getPriorPenalty(lWork) + getPriorPenalty(rWork);
                                if (cost > MAX_OPERATION_COST) continue;

                                int resWork = Math.max(lWork, rWork) + 1;
                                List<Node> candidates = targetBuildMap.computeIfAbsent(resWork, k -> new ArrayList<>());

                                for (Node leftNode : le.getValue()) {
                                    for (Node rightNode : re.getValue()) {
                                        Node next = new Node();
                                        next.work = resWork;
                                        next.total = leftNode.total + rightNode.total + cost;
                                        next.peak = Math.max(Math.max(leftNode.peak, rightNode.peak), cost);
                                        next.cost = cost;
                                        next.transferCost = costsByMask[sMask];
                                        next.mask = mask;
                                        next.left = leftNode;
                                        next.right = rightNode;

                                        insertParetoNode(candidates, next, books);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Applicazione all'oggetto finale deterministica
        Map<Integer, Map<Integer, List<ApplyNode>>> states = new LinkedHashMap<>();
        for (int m = 0; m < stateCount; m++) states.put(m, new TreeMap<>());

        ApplyNode root = new ApplyNode();
        root.targetWork = targetWorks;
        root.total = 0;
        root.peak = 0;
        states.get(fullMask).computeIfAbsent(targetWorks, k -> new ArrayList<>()).add(root);

        for (int size = count; size >= 1; size--) {
            for (int remaining = 1; remaining < stateCount; remaining++) {
                if (Integer.bitCount(remaining) != size) continue;

                Map<Integer, List<ApplyNode>> currentWorkMap = states.get(remaining);
                if (currentWorkMap.isEmpty()) continue;

                for (Map.Entry<Integer, List<ApplyNode>> entry : new ArrayList<>(currentWorkMap.entrySet())) {
                    int currTargetWork = entry.getKey();
                    List<ApplyNode> prefixes = entry.getValue();

                    for (int group = remaining; group > 0; group = (group - 1) & remaining) {
                        Map<Integer, List<Node>> groupBuilds = builds.get(group);
                        if (groupBuilds == null || groupBuilds.isEmpty()) continue;

                        int nextMask = remaining ^ group;
                        Map<Integer, List<ApplyNode>> nextWorkMap = states.get(nextMask);

                        for (Map.Entry<Integer, List<Node>> be : groupBuilds.entrySet()) {
                            int bookWork = be.getKey();
                            int cost = costsByMask[group] + getPriorPenalty(currTargetWork) + getPriorPenalty(bookWork);
                            if (cost > MAX_OPERATION_COST) continue;

                            int nextWork = Math.max(currTargetWork, bookWork) + 1;
                            List<ApplyNode> targetList = nextWorkMap.computeIfAbsent(nextWork, k -> new ArrayList<>());

                            for (ApplyNode prefix : prefixes) {
                                for (Node bNode : be.getValue()) {
                                    ApplyNode next = new ApplyNode();
                                    next.targetWork = nextWork;
                                    next.total = prefix.total + bNode.total + cost;
                                    next.peak = Math.max(Math.max(prefix.peak, bNode.peak), cost);
                                    next.cost = cost;
                                    next.transferCost = costsByMask[group];
                                    next.prev = prefix;
                                    next.bookNode = bNode;

                                    insertParetoApply(targetList, next, books);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Seleziona la soluzione migliore deterministica
        List<ApplyNode> finalSolutions = new ArrayList<>();
        for (List<ApplyNode> list : states.get(0).values()) {
            finalSolutions.addAll(list);
        }

        if (finalSolutions.isEmpty()) {
            Solution fallback = fallbackGreedy(books, targetWorks, targetItem, costsByMask);
            if (!prepSteps.isEmpty()) {
                List<MergeStep> combined = new ArrayList<>(prepSteps);
                combined.addAll(fallback.steps);
                fallback.steps = combined;
                for (MergeStep ps : prepSteps) {
                    fallback.totalCost += ps.cost;
                    fallback.peakCost = Math.max(fallback.peakCost, ps.cost);
                }
            }
            return fallback;
        }

        finalSolutions.sort((a, b) -> {
            if (a.peak != b.peak) return Integer.compare(a.peak, b.peak);
            if (a.total != b.total) return Integer.compare(a.total, b.total);
            if (a.targetWork != b.targetWork) return Integer.compare(a.targetWork, b.targetWork);
            return a.getSignature(books).compareTo(b.getSignature(books));
        });

        ApplyNode best = finalSolutions.get(0);
        Solution res = new Solution();
        res.totalCost = best.total;
        res.peakCost = best.peak;

        // Ricostruzione sequenza
        List<MergeStep> allSteps = new ArrayList<>();
        ApplyNode curr = best;
        List<ApplyNode> sequence = new ArrayList<>();
        while (curr != null && curr.bookNode != null) {
            sequence.add(curr);
            curr = curr.prev;
        }
        Collections.reverse(sequence);

        int currentItemWork = targetWorks;
        String currentItemLabel = targetItem != null ? targetItem.name : "Oggetto";

        for (ApplyNode an : sequence) {
            collectBookSteps(an.bookNode, books, allSteps);

            MergeStep step = new MergeStep();
            step.isBookToBook = false;
            step.leftLabel = currentItemLabel;
            step.rightLabel = formatMaskLabel(an.bookNode.mask, books);
            step.resultLabel = currentItemLabel;
            step.rightEnchants = getEnchantsForMask(an.bookNode.mask, books);
            step.resultEnchants.addAll(step.rightEnchants);
            step.cost = an.cost;
            step.transferCost = an.transferCost;
            step.leftWork = currentItemWork;
            step.rightWork = an.bookNode.work;
            step.resultWork = an.targetWork;
            allSteps.add(step);

            currentItemWork = an.targetWork;
        }

        if (!prepSteps.isEmpty()) {
            List<MergeStep> combined = new ArrayList<>(prepSteps);
            combined.addAll(allSteps);
            res.steps = combined;
            for (MergeStep ps : prepSteps) {
                res.totalCost += ps.cost;
                res.peakCost = Math.max(res.peakCost, ps.cost);
            }
        } else {
            res.steps = allSteps;
        }
        return res;
    }

    private static void collectBookSteps(Node node, List<BookInput> books, List<MergeStep> out) {
        if (node == null || node.left == null || node.right == null) return;
        collectBookSteps(node.left, books, out);
        collectBookSteps(node.right, books, out);

        MergeStep step = new MergeStep();
        step.isBookToBook = true;
        step.leftLabel = formatMaskLabel(node.left.mask, books);
        step.rightLabel = formatMaskLabel(node.right.mask, books);
        step.resultLabel = formatMaskLabel(node.mask, books);
        step.leftEnchants = getEnchantsForMask(node.left.mask, books);
        step.rightEnchants = getEnchantsForMask(node.right.mask, books);
        step.resultEnchants = getEnchantsForMask(node.mask, books);
        step.cost = node.cost;
        step.transferCost = node.transferCost;
        step.leftWork = node.left.work;
        step.rightWork = node.right.work;
        step.resultWork = node.work;
        out.add(step);
    }

    private static void insertParetoNode(List<Node> list, Node next, List<BookInput> books) {
        for (int i = 0; i < list.size(); i++) {
            Node curr = list.get(i);
            if (curr.total == next.total && curr.peak == next.peak) {
                if (curr.getSignature(books).compareTo(next.getSignature(books)) <= 0) return;
                list.set(i, next);
                return;
            }
            if (curr.total <= next.total && curr.peak <= next.peak) return;
        }
        list.removeIf(curr -> next.total <= curr.total && next.peak <= curr.peak);
        if (list.size() < 2) {
            list.add(next);
        }
    }

    private static void insertParetoApply(List<ApplyNode> list, ApplyNode next, List<BookInput> books) {
        for (int i = 0; i < list.size(); i++) {
            ApplyNode curr = list.get(i);
            if (curr.total == next.total && curr.peak == next.peak) {
                if (curr.getSignature(books).compareTo(next.getSignature(books)) <= 0) return;
                list.set(i, next);
                return;
            }
            if (curr.total <= next.total && curr.peak <= next.peak) return;
        }
        list.removeIf(curr -> next.total <= curr.total && next.peak <= curr.peak);
        if (list.size() < 2) {
            list.add(next);
        }
    }

    private static Solution fallbackGreedy(List<BookInput> books, int targetWorks, ItemEntry targetItem, int[] costsByMask) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < books.size(); i++) order.add(i);
        order.sort((a, b) -> Integer.compare(costsByMask[1 << a], costsByMask[1 << b]));

        Solution sol = new Solution();
        int curWork = targetWorks;
        String itemLabel = targetItem != null ? targetItem.name : "Oggetto";

        for (int idx : order) {
            int bWork = books.get(idx).works;
            int transfer = costsByMask[1 << idx];
            int cost = transfer + getPriorPenalty(curWork) + getPriorPenalty(bWork);
            int nextWork = Math.max(curWork, bWork) + 1;

            MergeStep step = new MergeStep();
            step.isBookToBook = false;
            step.leftLabel = itemLabel;
            step.rightLabel = formatMaskLabel(1 << idx, books);
            step.resultLabel = itemLabel;
            step.rightEnchants = getEnchantsForMask(1 << idx, books);
            step.resultEnchants.addAll(step.rightEnchants);
            step.cost = cost;
            step.transferCost = transfer;
            step.leftWork = curWork;
            step.rightWork = bWork;
            step.resultWork = nextWork;

            sol.steps.add(step);
            sol.totalCost += cost;
            sol.peakCost = Math.max(sol.peakCost, cost);
            curWork = nextWork;
        }
        return sol;
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
                    sb.append(formatEnchantName(ee != null ? ee.name : ea.id, ea.level));
                    first = false;
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : "Libro";
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
        if (b == null || b.enchantments.isEmpty()) return "Libro";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (EnchantApplied ea : b.enchantments) {
            if (!first) sb.append(" + ");
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(ea.id);
            sb.append(formatEnchantName(ee != null ? ee.name : ea.id, ea.level));
            first = false;
        }
        return sb.length() > 0 ? sb.toString() : "Libro";
    }

    public static String formatEnchantName(String baseName, int level) {
        if (baseName == null) return "";
        String trimmed = baseName.trim();
        if (trimmed.matches(".*\\s+[IVXLCDM]+$")) {
            return trimmed;
        }
        if (level > 0) {
            return trimmed + " " + toRoman(level);
        }
        return trimmed;
    }

    public static String formatEnchantName(EnchantmentEntry ee, int level) {
        if (ee == null) return "";
        return formatEnchantName(ee.name, level);
    }

    public static String toRoman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (n >= 0 && n < r.length) return r[n];
        return String.valueOf(n);
    }
}

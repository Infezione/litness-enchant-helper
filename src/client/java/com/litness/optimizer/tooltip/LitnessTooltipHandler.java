package com.litness.optimizer.tooltip;

import com.litness.optimizer.calculator.AnvilOptimizer;
import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.EnchantmentDatabase.EnchantmentEntry;
import com.litness.optimizer.data.InventoryTracker;
import com.litness.optimizer.data.PinnedEnchantmentsManager;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.text.Normalizer;
import java.util.*;

public class LitnessTooltipHandler {

    public static class MatchedEnchantment {
        public final EnchantmentEntry entry;
        public final int level;

        public MatchedEnchantment(EnchantmentEntry entry, int level) {
            this.entry = entry;
            this.level = level;
        }
    }

    private static Map<String, EnchantmentEntry> lookupMap = null;
    private static final Map<ItemStack, List<ItemStack>> cachedApplicableItems = Collections.synchronizedMap(new WeakHashMap<>());

    private static final List<String> CANONICAL_ORDER = List.of(
        "helmet", "chest", "legs", "boots", "elytra",
        "sword", "axe", "mace", "trident", "bow", "xbow",
        "pick", "shovel", "hoe", "rod", "shears", "flint", "shield", "brush"
    );

    public static synchronized Map<String, EnchantmentEntry> getLookupMap() {
        if (lookupMap == null || lookupMap.isEmpty()) {
            lookupMap = new HashMap<>(1024);
            for (EnchantmentEntry ee : EnchantmentDatabase.getAllEnchantments()) {
                registerVariants(ee);
            }

            // Aliases critici per Vanilla (nomi inglesi e italiani)
            putAlias("curse of binding", "binding_curse");
            putAlias("binding curse", "binding_curse");
            putAlias("curse of vanishing", "vanishing_curse");
            putAlias("vanishing curse", "vanishing_curse");
            putAlias("sweeping edge", "sweeping_edge");
            putAlias("sweeping", "sweeping_edge");
            putAlias("feather falling", "feather_falling");
            putAlias("caduta lenta", "feather_falling");
            putAlias("atterraggio morbido", "feather_falling");
            putAlias("aqua affinity", "aqua_affinity");
            putAlias("affinita all acqua", "aqua_affinity");
            putAlias("affinita acquatica", "aqua_affinity");
            putAlias("wind burst", "wind_burst");
            putAlias("colpo di vento", "wind_burst");
            putAlias("raffica di vento", "wind_burst");
            putAlias("efficiency", "efficiency");
            putAlias("efficienza", "efficiency");
            putAlias("unbreaking", "unbreaking");
            putAlias("indistruttibilita", "unbreaking");
            putAlias("mending", "mending");
            putAlias("ripristino", "mending");
            putAlias("sharpness", "sharpness");
            putAlias("affilatezza", "sharpness");
            putAlias("smite", "smite");
            putAlias("anatema", "smite");
            putAlias("bane of arthropods", "bane_of_arthropods");
            putAlias("flagello degli artropodi", "bane_of_arthropods");
            putAlias("knockback", "knockback");
            putAlias("contraccolpo", "knockback");
            putAlias("fire aspect", "fire_aspect");
            putAlias("aspetto di fuoco", "fire_aspect");
            putAlias("looting", "looting");
            putAlias("saccheggio", "looting");
            putAlias("silk touch", "silk_touch");
            putAlias("tocco di velluto", "silk_touch");
            putAlias("fortune", "fortune");
            putAlias("fortuna", "fortune");
            putAlias("power", "power");
            putAlias("potenza", "power");
            putAlias("punch", "punch");
            putAlias("impatto", "punch");
            putAlias("flame", "flame");
            putAlias("fiamma", "flame");
            putAlias("infinity", "infinity");
            putAlias("infinita", "infinity");
            putAlias("loyalty", "loyalty");
            putAlias("lealta", "loyalty");
            putAlias("riptide", "riptide");
            putAlias("risacca", "riptide");
            putAlias("channeling", "channeling");
            putAlias("canalizzazione", "channeling");
            putAlias("multishot", "multishot");
            putAlias("tiro multiplo", "multishot");
            putAlias("piercing", "piercing");
            putAlias("perforazione", "piercing");
            putAlias("quick charge", "quick_charge");
            putAlias("ricarica rapida", "quick_charge");
            putAlias("soul speed", "soul_speed");
            putAlias("velocita delle anime", "soul_speed");
            putAlias("swift sneak", "swift_sneak");
            putAlias("passo felpato", "swift_sneak");
            putAlias("breach", "breach");
            putAlias("breccia", "breach");
            putAlias("density", "density");
            putAlias("densita", "density");

            // Aliases server Litness espliciti (British/American e varianti)
            putAlias("arcane defence", "arcane-defense");
            putAlias("arcane defense", "arcane-defense");
            putAlias("critical", "criticals");
            putAlias("criticals", "criticals");
            putAlias("spark", "sparks");
            putAlias("sparks", "sparks");
            putAlias("streamline", "streamlining");
            putAlias("streamlining", "streamlining");
            putAlias("triple shot", "tripleshot");
            putAlias("tripleshot", "tripleshot");
            putAlias("vein miner", "veinminer");
            putAlias("veinminer", "veinminer");
            putAlias("x ray", "xray");
            putAlias("x-ray", "xray");
            putAlias("xray", "xray");
            putAlias("sniper", "snipe");
            putAlias("snipe", "snipe");
            putAlias("berserk", "berserker");
            putAlias("berserker", "berserker");
            putAlias("repair", "repairing");
            putAlias("repairing", "repairing");
            putAlias("vampire", "vampirism");
            putAlias("vampirism", "vampirism");
            putAlias("nether prospector", "nether-prospector");
            putAlias("prospector", "prospector");
            putAlias("boss hunter", "boss-hunter");
            putAlias("curse of breaklessness", "curse-of-breaklessness");
            putAlias("curse of harmlessness", "curse-of-harmlessness");
            putAlias("curse of hunger", "curse-of-hunger");
            putAlias("curse of misfortune", "curse-of-misfortune");
            putAlias("curse of permanence", "curse-of-permanence");
            putAlias("one for all", "one-for-all");
        }
        return lookupMap;
    }

    private static void registerVariants(EnchantmentEntry ee) {
        if (ee == null) return;
        String normName = normalize(ee.name);
        String normId = normalize(ee.id.replace('_', ' ').replace('-', ' '));

        addVariant(normName, ee);
        addVariant(normId, ee);

        // defense <-> defence
        if (normName.contains("defense")) {
            addVariant(normName.replace("defense", "defence"), ee);
        }
        if (normName.contains("defence")) {
            addVariant(normName.replace("defence", "defense"), ee);
        }
        if (normId.contains("defense")) {
            addVariant(normId.replace("defense", "defence"), ee);
        }
        if (normId.contains("defence")) {
            addVariant(normId.replace("defence", "defense"), ee);
        }

        // Senza spazi (es. "boss hunter" -> "bosshunter", "vein miner" -> "veinminer")
        addVariant(normName.replace(" ", ""), ee);
        addVariant(normId.replace(" ", ""), ee);

        // Singolare/Plurale su 's' finale (es. "criticals" -> "critical", "sparks" -> "spark")
        if (normName.endsWith("s") && normName.length() > 3) {
            addVariant(normName.substring(0, normName.length() - 1), ee);
        }
        if (normName.endsWith("ing") && normName.length() > 5) {
            addVariant(normName.substring(0, normName.length() - 3), ee);
            addVariant(normName.substring(0, normName.length() - 3) + "e", ee);
        }
    }

    private static void addVariant(String key, EnchantmentEntry ee) {
        if (key != null && !key.isEmpty()) {
            lookupMap.putIfAbsent(key, ee);
        }
    }

    private static void putAlias(String alias, String targetId) {
        EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(targetId);
        if (ee != null) {
            lookupMap.put(normalize(alias), ee);
        }
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register(LitnessTooltipHandler::onTooltip);
    }

    public static MatchedEnchantment matchEnchantment(String lineText) {
        if (lineText == null || lineText.isEmpty()) return null;

        // Se la riga inizia con spazi o tabulazioni (indented sub-lines), è una riga descrittiva e MAI un incantesimo!
        if (lineText.startsWith(" ") || lineText.startsWith("\t")) return null;

        // Se la riga contiene virgole, è un elenco (es. lista conflitti, oggetti), MAI un singolo incantesimo!
        if (lineText.contains(",")) return null;

        // Normalizza spazi non convenzionali (NBSP \u00A0, tab, ecc.)
        String clean = lineText.replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ');
        clean = clean.replaceAll("(?i)§[0-9a-fk-orx]", "").trim();
        if (clean.isEmpty()) return null;

        // Se contiene parole chiave tecniche, di conflitti o descrittive, la ignoriamo categoricamente
        String lowerClean = clean.toLowerCase();
        if (lowerClean.contains("conflitti") || lowerClean.contains("conflict") || clean.contains("⚔")
            || clean.contains("↳") || lowerClean.contains("[max") || clean.contains("[✓")
            || clean.contains("•") || lowerClean.contains("incudine") || lowerClean.contains("penalità")
            || lowerClean.contains("applicabile") || lowerClean.contains("costo") || lowerClean.contains("livell")
            || lowerClean.contains("requisiti") || lowerClean.contains("informazioni") || lowerClean.contains("limite")
            || lowerClean.contains("danno") || lowerClean.contains("probabilità") || lowerClean.contains("secondi")) {
            return null;
        }

        // Rimuove ricorsivamente simboli e tag iniziali (es: "⚡ [Custom] Boss Hunter" o "[Custom] ⚡ Boss Hunter")
        while (true) {
            String prev = clean;
            clean = clean.replaceAll("^[^a-zA-Z0-9\\[({]+", "").trim();
            clean = clean.replaceAll("^[\\[({][^\\])}]+[\\])}]\\s*", "").trim();
            if (clean.equals(prev)) break;
        }

        // Rimuove punteggiatura finale o simboli di decorazione
        clean = clean.replaceAll("[:\\-!|•~]+$", "").trim();
        if (clean.isEmpty()) return null;

        // Pulisce parole riempitive di livello (es: "Liv. 3", "Lv 3", "Level 4", "Livello V")
        String normalizedClean = clean.replaceAll("(?i)\\b(livello|liv|level|lvl|lv)\\.?\\s*", " ").replaceAll("\\s+", " ").trim();

        // Normalizza rimuovendo accenti e caratteri speciali
        String norm = normalize(normalizedClean);
        if (norm.isEmpty()) return null;

        // Rimuove eventuale suffisso "book" o "libro"
        if (norm.endsWith(" book")) norm = norm.substring(0, norm.length() - 5).trim();
        if (norm.endsWith(" libro")) norm = norm.substring(0, norm.length() - 6).trim();

        Map<String, EnchantmentEntry> lookup = getLookupMap();

        // PRIORITÀ 1: Risoluzione garantita per "Nether Prospector" (max 2) vs "Prospector" (max 3)
        if (norm.contains("nether") && norm.contains("prospector")) {
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment("nether-prospector");
            int lvl = extractLevelFromEnd(norm);
            if (lvl == 0 && ee != null && ee.maxLevel == 1) lvl = 1;
            if (lvl > 0 && ee != null && lvl <= ee.maxLevel + 5) {
                return new MatchedEnchantment(ee, lvl);
            }
        }

        // PRIORITÀ 2: Match diretto su tutta la stringa (per incantesimi a parola singola o composta senza livello)
        EnchantmentEntry direct = lookup.get(norm);
        if (direct != null) {
            int lvl = direct.maxLevel == 1 ? 1 : 0;
            return new MatchedEnchantment(direct, lvl);
        }

        // PRIORITÀ 3: Separazione tra Nome e Livello (Romano o Arabo) sull'ultimo spazio
        int lastSpace = norm.lastIndexOf(' ');
        if (lastSpace > 0) {
            String prefix = norm.substring(0, lastSpace).trim();
            String suffix = norm.substring(lastSpace + 1).trim();
            EnchantmentEntry matched = lookup.get(prefix);
            if (matched != null) {
                int lvl = parseRomanOrInt(suffix);
                if (matched.maxLevel == 1) {
                    if (lvl == 0 && (suffix.isEmpty() || suffix.equals("i") || suffix.equals("1"))) {
                        lvl = 1;
                    }
                    if (lvl == 1) {
                        return new MatchedEnchantment(matched, 1);
                    }
                } else {
                    // Per incantesimi a più livelli, il suffisso DEVE essere un livello valido (non una parola qualunque!)
                    if (lvl > 0 && lvl <= matched.maxLevel + 5) {
                        return new MatchedEnchantment(matched, lvl);
                    }
                }
            }
        }

        // PRIORITÀ 4: Match per suffisso numerico senza spazio (es. SharpnessV)
        int suffixLvl = extractLevelFromEnd(norm);
        if (suffixLvl > 0) {
            String namePart = norm.replaceAll("(?:\\s*\\d+|\\s*[ivxlcdm]+)$", "").trim();
            EnchantmentEntry matched = lookup.get(namePart);
            if (matched != null && (matched.maxLevel == 1 || (suffixLvl > 0 && suffixLvl <= matched.maxLevel + 5))) {
                return new MatchedEnchantment(matched, suffixLvl);
            }
        }

        return null;
    }

    private static int extractLevelFromEnd(String s) {
        if (s == null || s.isEmpty()) return 0;
        int lastSpace = s.lastIndexOf(' ');
        if (lastSpace >= 0) {
            int lvl = parseRomanOrInt(s.substring(lastSpace + 1).trim());
            if (lvl > 0) return lvl;
        }
        return 0;
    }

    private static void onTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag type, List<Component> lines) {
        if (stack.isEmpty() || lines == null || lines.isEmpty()) {
            return;
        }

        // 1. Rimuove scritte tecniche grigie di Minecraft da TUTTI gli oggetti (es: minecraft:rail, 15 component(s), NBT)
        lines.removeIf(line -> {
            String str = line.getString().trim();
            if (str.isEmpty()) return false;
            if (str.matches("(?i)^[a-z0-9_.-]+:[a-z0-9_.-]+$") || str.matches("(?i).*minecraft:[a-z0-9_.-]+.*")) {
                return true;
            }
            if (str.matches("(?i).*\\d+\\s+component(?:s|\\(s\\)|i).*")) {
                return true;
            }
            if (str.matches("(?i).*\\d+\\s+tag(?:s|\\(s\\)).*")) {
                return true;
            }
            // Rimuove eventuali righe con solo rarità (es: Non comune, Epico, Rarità: ...)
            String clean = str.replaceAll("(?i)^[§0-9a-fk-orx\\s\\[\\]•✦⚡*#\\-◆★✧✨]+", "").trim();
            if (clean.equalsIgnoreCase("comune") || clean.equalsIgnoreCase("non comune")
                    || clean.equalsIgnoreCase("raro") || clean.equalsIgnoreCase("epico")
                    || clean.equalsIgnoreCase("leggendario") || clean.equalsIgnoreCase("speciale")
                    || clean.toLowerCase().startsWith("rarit") || clean.toLowerCase().startsWith("rarity")) {
                return true;
            }
            return false;
        });

        // 2. Lore arricchita (Max Enchant, descrizione, costi, conflitti) ESCLUSIVAMENTE sui LIBRI INCANTATI!
        if (!stack.is(Items.ENCHANTED_BOOK) && !stack.is(Items.BOOK)) {
            return;
        }

        // Esclusione per menu GUI del server (es. /enchgui):
        // Se l'oggetto si trova in una GUI di catalogo del server o ha già la lore di catalogo del server,
        // NON modifichiamo nulla per preservare la lore nativa senza duplicazioni o conflitti visivi.
        if (isServerGuiItem(stack, lines)) {
            cachedApplicableItems.put(stack, Collections.emptyList());
            return;
        }

        // Snapshot delle righe originali dell'oggetto prima di QUALSIASI modifica da parte del mod!
        // In questo modo, PASSO B scansionerà SOLO ed ESCLUSIVAMENTE le righe originali,
        // rendendo IMPOSSIBILE che linee di conflitti o descrizioni aggiunte dal mod vengano ri-scansionate.
        List<Component> originalLinesSnapshot = new ArrayList<>(lines);

        // Pre-conteggio incantesimi sul libro per verificare se è multi-enchant
        Set<String> allEnchantIdsOnBook = new HashSet<>();
        ItemEnchantments storedCheck = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : storedCheck.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            if (ee != null) {
                allEnchantIdsOnBook.add(ee.id);
            } else if (!rawId.isEmpty()) {
                allEnchantIdsOnBook.add(rawId);
            }
        }
        for (Component origComp : originalLinesSnapshot) {
            String rawLine = origComp.getString();
            if (rawLine.startsWith(" ") || rawLine.startsWith("\t")) continue;
            String fullText = rawLine.replace('\u00A0', ' ').replace('\u2007', ' ').trim();
            if (fullText.isEmpty()) continue;
            MatchedEnchantment match = matchEnchantment(fullText);
            if (match != null && match.entry != null) {
                allEnchantIdsOnBook.add(match.entry.id);
            }
        }
        final boolean multiEnchant = allEnchantIdsOnBook.size() > 1;

        if (multiEnchant) {
            // Se ci sono più incantesimi, rimuoviamo anche eventuali descrizioni preesistenti nel lore per non intasare lo schermo
            lines.removeIf(line -> {
                String norm = normalize(line.getString());
                if (norm.length() < 6) return false;
                for (String enchId : allEnchantIdsOnBook) {
                    EnchantmentEntry e = EnchantmentDatabase.getEnchantment(enchId);
                    if (e != null && e.description != null && !e.description.isEmpty()) {
                        String descNorm = normalize(e.description);
                        if (descNorm.contains(norm) || (norm.length() >= 12 && descNorm.contains(norm.substring(0, Math.min(25, norm.length()))))) {
                            return true;
                        }
                    }
                }
                return false;
            });
        }

        List<EnchantmentEntry> matchedBookEntries = new ArrayList<>();
        Set<String> processedEnchantIds = new HashSet<>();
        int serverLoreCost = InventoryTracker.extractAnvilCostFromLore(stack);
        boolean hasServerCostLore = serverLoreCost > 0;

        // PASSO A: Rilevamento prioritario e infallibile da DataComponents.STORED_ENCHANTMENTS (Vanilla)
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = entry.getIntValue();
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            if (ee == null) continue;

            matchedBookEntries.add(ee);
            processedEnchantIds.add(ee.id);

            // Cerca nella lista lines la riga corrispondente (anche con localizzazioni diverse o NBSP)
            int targetLineIdx = -1;
            for (int i = 0; i < lines.size(); i++) {
                String lineStr = lines.get(i).getString().replace('\u00A0', ' ').replace('\u2007', ' ').trim();
                String normLine = normalize(lineStr);
                if (normLine.contains(normalize(ee.name)) || normLine.contains(normalize(ee.id)) || normLine.contains(rawId.replace('_', ' '))) {
                    targetLineIdx = i;
                    break;
                }
            }

            boolean isCustom = "custom".equalsIgnoreCase(ee.category);
            boolean isMax = lvl >= ee.maxLevel;
            String maxRoman = AnvilOptimizer.toRoman(ee.maxLevel);

            String maxTag = isMax ? " §8[§a✓ Max " + maxRoman + "§8]" : " §8[§eMax " + maxRoman + "§8]";

            String icon = isCustom ? "§6⚡ " : "§9✦ ";
            int insertOffset = 1;

            if (targetLineIdx >= 0) {
                String fullText = lines.get(targetLineIdx).getString().replace('\u00A0', ' ').trim();
                String displayBase = fullText.replaceAll("^[§0-9a-fk-orx\\s]*[✦⚡•»►*#\\-◆★✧✨⚔⚜✪✯⚙☠🗡🛡🏹⛏]+\\s*", "").trim();
                lines.set(targetLineIdx, Component.literal(icon + displayBase + maxTag));
            } else {
                targetLineIdx = Math.min(1, lines.size());
                String displayName = ee.name + " " + AnvilOptimizer.toRoman(lvl);
                lines.add(targetLineIdx, Component.literal(icon + displayName + maxTag));
            }

            // Info custom costo incudine (senza indicazione di rarità)
            if (isCustom && !hasServerCostLore) {
                int costNum = ee.getAnvilCostNumeric();
                if (costNum > 0) {
                    lines.add(targetLineIdx + insertOffset, Component.literal("  §7Incudine: §6+" + costNum + " liv."));
                    insertOffset++;
                }
            }

            // Conflitti: SOLO nomi degli incantesimi in conflitto (senza descrizioni!)
            List<String> conflicts = EnchantmentDatabase.getResolvedConflicts(ee.id);
            if (!conflicts.isEmpty()) {
                List<String> wrappedConflicts = wrapConflictLines(conflicts, 45);
                if (!wrappedConflicts.isEmpty()) {
                    lines.add(targetLineIdx + insertOffset, Component.literal("  §c⚔ Conflitti: §f" + wrappedConflicts.get(0)));
                    insertOffset++;
                    for (int w = 1; w < wrappedConflicts.size(); w++) {
                        lines.add(targetLineIdx + insertOffset, Component.literal("    §f" + wrappedConflicts.get(w)));
                        insertOffset++;
                    }
                }
            }

            // Descrizione dell'incantesimo attivo ("Cosa fa") - Esclusa se ci sono più incantesimi sul libro
            if (!multiEnchant && ee.description != null && !ee.description.isEmpty()) {
                boolean alreadyHasDesc = false;
                for (int checkIdx = targetLineIdx + 1; checkIdx < Math.min(targetLineIdx + 4, lines.size()); checkIdx++) {
                    String checkLine = normalize(lines.get(checkIdx).getString());
                    String descNorm = normalize(ee.description);
                    if (checkLine.length() > 8 && (descNorm.contains(checkLine) || checkLine.contains(descNorm.substring(0, Math.min(15, descNorm.length()))))) {
                        alreadyHasDesc = true;
                        break;
                    }
                }
                if (!alreadyHasDesc) {
                    List<String> wrapped = wrapText(ee.description, 42);
                    for (int w = 0; w < wrapped.size(); w++) {
                        lines.add(targetLineIdx + insertOffset, Component.literal("  §7" + wrapped.get(w)));
                        insertOffset++;
                    }
                }
            }
        }

        // PASSO B: Scansione righe Lore ESCLUSIVAMENTE dallo snapshot originale per incantesimi custom del server
        for (Component origComp : originalLinesSnapshot) {
            String rawLine = origComp.getString();
            // Ignora subito righe indentate con spazi o tab (sub-voci o descrizioni)
            if (rawLine.startsWith(" ") || rawLine.startsWith("\t")) continue;

            String fullText = rawLine.replace('\u00A0', ' ').replace('\u2007', ' ').trim();
            if (fullText.isEmpty()) continue;

            MatchedEnchantment match = matchEnchantment(fullText);
            if (match == null || match.entry == null || processedEnchantIds.contains(match.entry.id)) {
                continue;
            }

            EnchantmentEntry ee = match.entry;
            if (serverLoreCost > 0) {
                EnchantmentDatabase.updateAnvilCost(ee.id, serverLoreCost);
            }
            processedEnchantIds.add(ee.id);
            matchedBookEntries.add(ee);

            int currentLvl = match.level;
            if (currentLvl == 0 && ee.maxLevel == 1) {
                currentLvl = 1;
            }

            boolean isCustom = "custom".equalsIgnoreCase(ee.category);
            boolean isMax = currentLvl >= ee.maxLevel;
            String maxRoman = AnvilOptimizer.toRoman(ee.maxLevel);

            // Tag Max Enchant e Costo:
            String maxTag = isMax ? " §8[§a✓ Max " + maxRoman + "§8]" : " §8[§eMax " + maxRoman + "§8]";

            // Trova l'indice attuale della riga originale nella lista 'lines'
            int targetIdx = -1;
            for (int k = 0; k < lines.size(); k++) {
                if (lines.get(k) == origComp || lines.get(k).getString().trim().equals(rawLine.trim())) {
                    targetIdx = k;
                    break;
                }
            }

            String displayBase = fullText.replaceAll("^[§0-9a-fk-orx\\s]*[✦⚡•»►*#\\-◆★✧✨⚔⚜✪✯⚙☠🗡🛡🏹⛏]+\\s*", "").trim();
            String icon = isCustom ? "§6⚡ " : "§9✦ ";
            String headerLine = icon + displayBase + maxTag;

            if (targetIdx >= 0) {
                lines.set(targetIdx, Component.literal(headerLine));
            } else {
                targetIdx = lines.size();
                lines.add(Component.literal(headerLine));
            }

            int insertOffset = 1;

            // Info aggiuntive SOLO per Custom: Costo Incudine (senza indicazione di rarità)
            if (isCustom && !hasServerCostLore) {
                int costNum = ee.getAnvilCostNumeric();
                if (costNum > 0) {
                    String infoLine = "  §7Incudine: §6+" + costNum + " liv.";
                    lines.add(targetIdx + insertOffset, Component.literal(infoLine));
                    insertOffset++;
                }
            }

            // Conflitti: SOLO nomi degli incantesimi in conflitto (senza descrizioni!)
            List<String> conflicts = EnchantmentDatabase.getResolvedConflicts(ee.id);
            if (!conflicts.isEmpty()) {
                List<String> wrappedConflicts = wrapConflictLines(conflicts, 45);
                if (!wrappedConflicts.isEmpty()) {
                    lines.add(targetIdx + insertOffset, Component.literal("  §c⚔ Conflitti: §f" + wrappedConflicts.get(0)));
                    insertOffset++;
                    for (int w = 1; w < wrappedConflicts.size(); w++) {
                        lines.add(targetIdx + insertOffset, Component.literal("    §f" + wrappedConflicts.get(w)));
                        insertOffset++;
                    }
                }
            }

            // Descrizione dell'incantesimo attivo ("Cosa fa") - Esclusa se ci sono più incantesimi sul libro
            if (!multiEnchant && ee.description != null && !ee.description.isEmpty()) {
                boolean alreadyHasDesc = false;
                for (int checkIdx = targetIdx + 1; checkIdx < Math.min(targetIdx + 4, lines.size()); checkIdx++) {
                    String checkLine = normalize(lines.get(checkIdx).getString());
                    String descNorm = normalize(ee.description);
                    if (checkLine.length() > 8 && (descNorm.contains(checkLine) || checkLine.contains(descNorm.substring(0, Math.min(15, descNorm.length()))))) {
                        alreadyHasDesc = true;
                        break;
                    }
                }
                if (!alreadyHasDesc) {
                    List<String> wrapped = wrapText(ee.description, 42);
                    for (int w = 0; w < wrapped.size(); w++) {
                        lines.add(targetIdx + insertOffset, Component.literal("  §7" + wrapped.get(w)));
                        insertOffset++;
                    }
                }
            }
        }

        // 3. Penalità lavoro incudine se presente sul libro
        int repairCost = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
        if (repairCost > 0) {
            lines.add(Component.literal("§8Penalità incudine libro: §e+" + repairCost + " liv."));
        }

        // 4. Memorizza gli oggetti compatibili per il libro (Vanilla e Custom!)
        Set<String> appKeys = new LinkedHashSet<>();
        for (EnchantmentEntry ee : matchedBookEntries) {
            if (ee.applicableTo != null) {
                appKeys.addAll(ee.applicableTo);
            }
        }

        if (!appKeys.isEmpty()) {
            List<ItemStack> orderedStacks = getOrderedItemStacks(appKeys);
            if (!orderedStacks.isEmpty()) {
                cachedApplicableItems.put(stack, orderedStacks);
            } else {
                cachedApplicableItems.remove(stack);
            }
        } else {
            cachedApplicableItems.remove(stack);
        }

        // 5. Badge PINNATO se il libro contiene incantesimi cercati dall'utente!
        boolean hasPinned = false;
        for (EnchantmentEntry ee : matchedBookEntries) {
            if (PinnedEnchantmentsManager.isPinned(ee.id)) {
                hasPinned = true;
                break;
            }
        }
        if (hasPinned && lines.size() > 1) {
            lines.add(1, Component.literal("  §6★ §a§lPINNATO: TI SERVE PER IL TUO SET! §6★"));
        }
    }

    public static Optional<TooltipComponent> getApplicableItemsTooltipData(ItemStack stack) {
        if (stack == null || stack.isEmpty() || (!stack.is(Items.ENCHANTED_BOOK) && !stack.is(Items.BOOK))) {
            return Optional.empty();
        }

        if (isServerGuiStack(stack)) {
            return Optional.empty();
        }

        Component title = stack.getHoverName();

        List<ItemStack> cached = cachedApplicableItems.get(stack);
        if (cached != null) {
            return cached.isEmpty() ? Optional.empty() : Optional.of(new ApplicableItemsTooltipData(cached, title));
        }

        // Fallback per libri (Vanilla e Custom)
        Set<String> appKeys = extractApplicableKeys(stack);
        if (!appKeys.isEmpty()) {
            List<ItemStack> items = getOrderedItemStacks(appKeys);
            if (!items.isEmpty()) {
                cachedApplicableItems.put(stack, items);
                return Optional.of(new ApplicableItemsTooltipData(items, title));
            }
        }

        cachedApplicableItems.put(stack, Collections.emptyList());
        return Optional.empty();
    }

    public static boolean isServerGuiItem(ItemStack stack, List<Component> lines) {
        if (lines != null) {
            for (Component c : lines) {
                String s = c.getString().toLowerCase().replace('\u00A0', ' ').trim();
                // "12 incantesimi in totale", "limite per oggetto", "1 unico, 1 incantesimo" sono esclusivi della GUI catalogo /enchgui
                if (s.contains("limite per oggetto") || s.contains("12 incantesimi in totale") || s.contains("1 unico, 1 incantesimo")) {
                    return true;
                }
            }
        }
        if (isServerGuiScreen()) {
            if (lines != null) {
                for (Component c : lines) {
                    String s = c.getString().toLowerCase().replace('\u00A0', ' ').trim();
                    if (s.contains("requisiti:") || s.contains("informazioni:") || s.contains("↑ livello max") || s.contains("si applica a:")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isServerGuiStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component c : lore.lines()) {
            String s = c.getString().toLowerCase().replace('\u00A0', ' ').trim();
            if (s.contains("limite per oggetto") || s.contains("12 incantesimi in totale") || s.contains("1 unico, 1 incantesimo")) {
                return true;
            }
        }
        if (isServerGuiScreen()) {
            for (Component c : lore.lines()) {
                String s = c.getString().toLowerCase().replace('\u00A0', ' ').trim();
                if (s.contains("requisiti:") || s.contains("informazioni:") || s.contains("↑ livello max") || s.contains("si applica a:")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isServerGuiScreen() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                net.minecraft.client.gui.screens.Screen currentScreen = mc.gui.screen();
                if (currentScreen != null) {
                    // Schermate normali di gioco del giocatore: mai considerate menu del server
                    if (currentScreen instanceof InventoryScreen
                            || currentScreen instanceof MerchantScreen
                            || currentScreen instanceof AnvilScreen) {
                        return false;
                    }
                    String screenTitle = currentScreen.getTitle().getString().toLowerCase().replace('\u00A0', ' ').trim();
                    if (screenTitle.contains("enchant") || screenTitle.contains("incant")
                            || screenTitle.contains("enchgui") || screenTitle.contains("custom enchant")) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static Set<String> extractApplicableKeys(ItemStack stack) {
        Set<String> appKeys = new LinkedHashSet<>();

        // 1. Da STORED_ENCHANTMENTS (libri incantati vanilla nativi)
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            String rawId = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            EnchantmentEntry ee = EnchantmentDatabase.getEnchantment(rawId);
            if (ee != null && ee.applicableTo != null) {
                appKeys.addAll(ee.applicableTo);
            }
        }

        // 2. Da LORE (per incantesimi custom o descrizioni inviate dal server)
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component comp : lore.lines()) {
            MatchedEnchantment match = matchEnchantment(comp.getString());
            if (match != null && match.entry != null && match.entry.applicableTo != null) {
                appKeys.addAll(match.entry.applicableTo);
            }
        }

        // 3. Da CUSTOM_NAME se presente
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null && appKeys.isEmpty()) {
            MatchedEnchantment match = matchEnchantment(customName.getString());
            if (match != null && match.entry != null && match.entry.applicableTo != null) {
                appKeys.addAll(match.entry.applicableTo);
            }
        }

        return appKeys;
    }

    public static List<ItemStack> getOrderedItemStacks(Collection<String> keys) {
        List<ItemStack> list = new ArrayList<>();
        if (keys == null || keys.isEmpty()) return list;

        Set<String> normalizedKeys = new HashSet<>();
        for (String k : keys) {
            if (k == null) continue;
            String n = k.trim().toLowerCase();
            if (n.equals("pickaxe")) n = "pick";
            if (n.equals("crossbow")) n = "xbow";
            if (n.equals("chestplate")) n = "chest";
            if (n.equals("leggings")) n = "legs";
            if (n.equals("fishing_rod")) n = "rod";
            if (n.equals("flint_and_steel")) n = "flint";
            normalizedKeys.add(n);
        }

        for (String canonical : CANONICAL_ORDER) {
            if (normalizedKeys.contains(canonical)) {
                ItemStack item = getRepresentativeItemStack(canonical);
                if (!item.isEmpty()) {
                    list.add(item);
                }
            }
        }
        return list;
    }

    public static ItemStack getRepresentativeItemStack(String appKey) {
        if (appKey == null) return ItemStack.EMPTY;
        switch (appKey.trim().toLowerCase()) {
            case "helmet":
                return new ItemStack(Items.DIAMOND_HELMET);
            case "chest":
            case "chestplate":
                return new ItemStack(Items.DIAMOND_CHESTPLATE);
            case "legs":
            case "leggings":
                return new ItemStack(Items.DIAMOND_LEGGINGS);
            case "boots":
                return new ItemStack(Items.DIAMOND_BOOTS);
            case "elytra":
                return new ItemStack(Items.ELYTRA);
            case "sword":
                return new ItemStack(Items.DIAMOND_SWORD);
            case "axe":
                return new ItemStack(Items.DIAMOND_AXE);
            case "mace":
                return new ItemStack(Items.MACE);
            case "trident":
                return new ItemStack(Items.TRIDENT);
            case "bow":
                return new ItemStack(Items.BOW);
            case "xbow":
            case "crossbow":
                return new ItemStack(Items.CROSSBOW);
            case "pick":
            case "pickaxe":
                return new ItemStack(Items.DIAMOND_PICKAXE);
            case "shovel":
                return new ItemStack(Items.DIAMOND_SHOVEL);
            case "hoe":
                return new ItemStack(Items.DIAMOND_HOE);
            case "rod":
            case "fishing_rod":
                return new ItemStack(Items.FISHING_ROD);
            case "shears":
                return new ItemStack(Items.SHEARS);
            case "flint":
            case "flint_and_steel":
                return new ItemStack(Items.FLINT_AND_STEEL);
            case "shield":
                return new ItemStack(Items.SHIELD);
            case "brush":
                return new ItemStack(Items.BRUSH);
            default:
                return ItemStack.EMPTY;
        }
    }

    private static String normalize(String s) {
        if (s == null) return "";
        s = s.replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ');
        String norm = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return norm.replaceAll("[^a-zA-Z0-9 ]", " ").replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static List<String> wrapConflictLines(List<String> names, int maxCharsPerLine) {
        List<String> result = new ArrayList<>();
        if (names == null || names.isEmpty()) return result;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String sep = (i == names.size() - 1) ? "" : ", ";
            if (sb.length() > 0 && sb.length() + name.length() + sep.length() > maxCharsPerLine) {
                result.add(sb.toString());
                sb = new StringBuilder();
            }
            sb.append(name).append(sep);
        }
        if (sb.length() > 0) {
            result.add(sb.toString());
        }
        return result;
    }

    private static List<String> wrapText(String text, int maxChars) {
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

    public static int parseRomanOrInt(String s) {
        if (s == null) return 0;
        s = s.replace('\u00A0', ' ').trim().toUpperCase();
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
                try {
                    return Integer.parseInt(s);
                } catch (Exception ignored) {
                    return 0;
                }
        }
    }
}

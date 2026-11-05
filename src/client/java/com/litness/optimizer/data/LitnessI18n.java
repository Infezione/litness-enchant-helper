package com.litness.optimizer.data;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Map;

public class LitnessI18n {

    private static final Map<String, String> VANILLA_EN_TO_IT = new HashMap<>();
    private static final Map<String, String> VANILLA_IT_TO_EN = new HashMap<>();
    private static final Map<String, String> ID_TO_EN = new HashMap<>();
    private static final Map<String, String> ID_TO_IT = new HashMap<>();

    static {
        registerVanilla("sharpness", "Affilatezza", "Sharpness");
        registerVanilla("smite", "Anatema", "Smite");
        registerVanilla("bane_of_arthropods", "Flagello degli artropodi", "Bane of Arthropods");
        registerVanilla("knockback", "Contraccolpo", "Knockback");
        registerVanilla("fire_aspect", "Aspetto di fuoco", "Fire Aspect");
        registerVanilla("looting", "Saccheggio", "Looting");
        registerVanilla("sweeping_edge", "Lama sferzante", "Sweeping Edge");
        registerVanilla("unbreaking", "Indistruttibilit\u00E0", "Unbreaking");
        registerVanilla("mending", "Ripristino", "Mending");
        registerVanilla("efficiency", "Efficienza", "Efficiency");
        registerVanilla("silk_touch", "Tocco di velluto", "Silk Touch");
        registerVanilla("fortune", "Fortuna", "Fortune");
        registerVanilla("power", "Potenza", "Power");
        registerVanilla("punch", "Impatto", "Punch");
        registerVanilla("flame", "Fiamma", "Flame");
        registerVanilla("infinity", "Infinit\u00E0", "Infinity");
        registerVanilla("luck_of_the_sea", "Fortuna del mare", "Luck of the Sea");
        registerVanilla("lure", "Esca", "Lure");
        registerVanilla("loyalty", "Lealt\u00E0", "Loyalty");
        registerVanilla("impaling", "Impalamento", "Impaling");
        registerVanilla("riptide", "Risacca", "Riptide");
        registerVanilla("channeling", "Canalizzazione", "Channeling");
        registerVanilla("multishot", "Tiro multiplo", "Multishot");
        registerVanilla("quick_charge", "Ricarica rapida", "Quick Charge");
        registerVanilla("piercing", "Perforazione", "Piercing");
        registerVanilla("density", "Densit\u00E0", "Density");
        registerVanilla("breach", "Breccia", "Breach");
        registerVanilla("wind_burst", "Raffica di vento", "Wind Burst");
        registerVanilla("protection", "Protezione", "Protection");
        registerVanilla("fire_protection", "Protezione dal fuoco", "Fire Protection");
        registerVanilla("feather_falling", "Atterraggio morbido", "Feather Falling");
        registerVanilla("blast_protection", "Protezione dalle esplosioni", "Blast Protection");
        registerVanilla("projectile_protection", "Protezione dai proiettili", "Projectile Protection");
        registerVanilla("respiration", "Respirazione", "Respiration");
        registerVanilla("aqua_affinity", "Affinit\u00E0 all'acqua", "Aqua Affinity");
        registerVanilla("thorns", "Spine", "Thorns");
        registerVanilla("depth_strider", "Passo anfibio", "Depth Strider");
        registerVanilla("frost_walker", "Passo gelato", "Frost Walker");
        registerVanilla("soul_speed", "Velocit\u00E0 delle anime", "Soul Speed");
        registerVanilla("swift_sneak", "Passo felpato", "Swift Sneak");
        registerVanilla("binding_curse", "Maledizione del legame", "Curse of Binding");
        registerVanilla("vanishing_curse", "Maledizione della scomparsa", "Curse of Vanishing");

        // Vanilla+
        registerVanilla("sharpness_6", "Affilatezza VI", "Sharpness VI");
        registerVanilla("efficiency_6", "Efficienza VI", "Efficiency VI");
        registerVanilla("feather_falling_5", "Atterraggio morbido V", "Feather Falling V");
        registerVanilla("looting_4", "Saccheggio IV", "Looting IV");
        registerVanilla("respiration_4", "Respirazione IV", "Respiration IV");
        registerVanilla("unbreaking_4", "Indistruttibilit\u00E0 IV", "Unbreaking IV");
        registerVanilla("unbreaking_5", "Indistruttibilit\u00E0 V", "Unbreaking V");
        registerVanilla("depth_strider_4", "Passo anfibio IV", "Depth Strider IV");
        registerVanilla("protection_5", "Protezione V", "Protection V");
        registerVanilla("power_6", "Potenza VI", "Power VI");
    }

    private static void registerVanilla(String id, String it, String en) {
        ID_TO_IT.put(id.toLowerCase(), it);
        ID_TO_EN.put(id.toLowerCase(), en);
        VANILLA_IT_TO_EN.put(it.toLowerCase(), en);
        VANILLA_EN_TO_IT.put(en.toLowerCase(), it);
    }

    public static boolean isEnglish() {
        return ActiveSessionManager.isEnglish();
    }

    public static String getLanguage() {
        return ActiveSessionManager.getLanguage();
    }

    public static void setLanguage(String lang) {
        ActiveSessionManager.setLanguage(lang);
    }

    public static void toggleLanguage() {
        if (isEnglish()) {
            setLanguage("it");
        } else {
            setLanguage("en");
        }
    }

    /**
     * Rimuove qualsiasi suffisso di livello o numero romano da un nome di incantesimo.
     * Es: "Affilatezza VI" -> "Affilatezza", "Blood VIII" -> "Blood", "Breccia II" -> "Breccia".
     */
    public static String stripLevelSuffix(String name) {
        if (name == null || name.isEmpty()) return "";
        String trimmed = name.trim();
        String[] numerals = {" X", " IX", " VIII", " VII", " VI", " V", " IV", " III", " II", " I"};
        for (String num : numerals) {
            if (trimmed.toUpperCase().endsWith(num)) {
                return trimmed.substring(0, trimmed.length() - num.length()).trim();
            }
        }
        return trimmed.replaceAll("\\s+[0-9]+$", "").trim();
    }

    public static String getBaseId(String id) {
        if (id == null) return "";
        String s = id.trim().toLowerCase();
        if (s.startsWith("minecraft:")) s = s.substring(10);
        return s.replaceAll("_[0-9]+$", "").replaceAll("-[0-9]+$", "");
    }

    /**
     * Restituisce il nome BASE dell'incantesimo SENZA numeri romani o livelli,
     * rispettando la lingua attiva (IT / EN per Vanilla/Vanilla+, lingua originale per Custom).
     */
    public static String getBaseEnchantName(EnchantmentDatabase.EnchantmentEntry ee) {
        if (ee == null) return "";
        String baseId = getBaseId(ee.id);
        if (!baseId.equalsIgnoreCase(ee.id)) {
            EnchantmentDatabase.EnchantmentEntry baseEe = EnchantmentDatabase.getEnchantment(baseId);
            if (baseEe != null) {
                return stripLevelSuffix(getEnchantName(baseEe));
            }
        }
        return stripLevelSuffix(getEnchantName(ee));
    }

    public static String getBaseEnchantNameById(String id, String fallbackName) {
        if (id == null) return stripLevelSuffix(fallbackName);
        String baseId = getBaseId(id);
        return stripLevelSuffix(getEnchantNameById(baseId, fallbackName));
    }

    /**
     * Traduce universalmente qualsiasi nome di incantesimo preservando livelli e suffissi romani.
     */
    public static String translateEnchantName(String name) {
        if (name == null || name.isEmpty()) return "";
        String trimmed = name.trim();
        String levelSuffix = "";
        String base = trimmed;

        String[] numerals = {" X", " IX", " VIII", " VII", " VI", " V", " IV", " III", " II", " I"};
        for (String num : numerals) {
            if (base.toUpperCase().endsWith(num)) {
                levelSuffix = num;
                base = base.substring(0, base.length() - num.length()).trim();
                break;
            }
        }

        if (isEnglish()) {
            String tr = VANILLA_IT_TO_EN.get(base.toLowerCase());
            if (tr != null) return tr + levelSuffix;
            return trimmed;
        } else {
            String tr = VANILLA_EN_TO_IT.get(base.toLowerCase());
            if (tr != null) return tr + levelSuffix;
            return trimmed;
        }
    }

    public static String getEnchantName(EnchantmentDatabase.EnchantmentEntry ee) {
        if (ee == null) return "";
        if ("custom".equalsIgnoreCase(ee.category)) {
            // Incanti custom rimangono sempre in lingua inglese originale
            return ee.name;
        }
        if (isEnglish()) {
            String en = ID_TO_EN.get(ee.id.toLowerCase());
            if (en != null) return en;
            return translateEnchantName(ee.name);
        } else {
            String it = ID_TO_IT.get(ee.id.toLowerCase());
            if (it != null) return it;
            return translateEnchantName(ee.name);
        }
    }

    public static String getEnchantNameById(String id, String fallbackName) {
        if (id == null) return fallbackName;
        String cleanId = id.trim().toLowerCase();
        if (cleanId.startsWith("minecraft:")) cleanId = cleanId.substring(10);
        if (isEnglish()) {
            String en = ID_TO_EN.get(cleanId);
            if (en != null) return en;
            return translateEnchantName(fallbackName);
        } else {
            String it = ID_TO_IT.get(cleanId);
            if (it != null) return it;
            return translateEnchantName(fallbackName);
        }
    }

    public static String getItemCategoryName(int catIndex) {
        boolean en = isEnglish();
        switch (catIndex) {
            case 0: return en ? "Weapons" : "Armi";
            case 1: return en ? "Bows" : "Archi";
            case 2: return en ? "Armor" : "Armature";
            case 3: return en ? "Tools" : "Attrezzi";
            default: return en ? "All" : "Tutti";
        }
    }

    public static String getItemPieceName(String itemKey) {
        boolean en = isEnglish();
        if (itemKey == null) return en ? "Item" : "Oggetto";
        switch (itemKey.toLowerCase()) {
            case "sword": return en ? "Sword" : "Spada";
            case "axe": return en ? "Axe" : "Ascia";
            case "mace": return en ? "Mace" : "Mazza";
            case "trident": return en ? "Trident" : "Tridente";
            case "bow": return en ? "Bow" : "Arco";
            case "xbow": return en ? "Crossbow" : "Balestra";
            case "pick": return en ? "Pickaxe" : "Piccone";
            case "shovel": return en ? "Shovel" : "Pala";
            case "hoe": return en ? "Hoe" : "Zappa";
            case "rod": return en ? "Fishing Rod" : "Canna da pesca";
            case "helmet": return en ? "Helmet" : "Elmo";
            case "chest": return en ? "Chestplate" : "Corazza";
            case "legs": return en ? "Leggings" : "Gambali";
            case "boots": return en ? "Boots" : "Stivali";
            case "elytra": return en ? "Elytra" : "Elitre";
            default: return en ? "Item" : "Oggetto";
        }
    }

    public static String getActionName(String itemKey) {
        boolean en = isEnglish();
        if (itemKey == null) return en ? "FORGE ITEM" : "TEMPRA OGGETTO";
        String piece = getItemPieceName(itemKey).toUpperCase();
        return en ? ("FORGE " + piece) : ("TEMPRA " + piece);
    }

    public static String t(String key) {
        boolean en = isEnglish();
        switch (key) {
            case "tab_catalog": return en ? "Catalog" : "Catalogo";
            case "tab_calculator": return en ? "Anvil Guide" : "Guida Incudine";
            case "tab_sets": return en ? "My Sets" : "I Miei Set";
            case "search_hint": return en ? "Search..." : "Cerca...";
            case "btn_clear": return en ? "Reset" : "Azzera";
            case "btn_save": return en ? "Save" : "Salva";
            case "btn_cancel": return en ? "Cancel" : "Annulla";
            case "btn_close": return en ? "Close [ESC]" : "Chiudi [ESC]";
            case "btn_copy": return en ? "Copy Guide" : "Copia Guida";
            case "btn_prev": return en ? "Prev" : "Prec.";
            case "btn_next": return en ? "Next" : "Succ.";
            case "btn_full_guide": return en ? "Full Guide [N]" : "Guida Completa [N]";
            case "btn_step_guide": return en ? "Steps Guide" : "Guida Passi";
            case "slot_1_empty": return en ? "Slot 1 Empty" : "Slot 1 vuoto";
            case "insert_to_start": return en ? "Insert target item to start guide." : "Inserisci l'item desiderato per iniziare.";
            case "press_n_for_tool": return en ? "Press [N] for tool" : "Premi [N] per il tool";
            case "ready_to_merge": return en ? "READY TO MERGE" : "PRONTO DA UNIRE";
            case "ready_to_forge": return en ? "READY TO FORGE" : "PRONTO DA TEMPRARE";
            case "take_from_output": return en ? "Take item from output slot!" : "Prendi dallo slot di uscita!";
            case "put_both_books": return en ? "Place the 2 books in the anvil" : "Metti i 2 libri nell'incudine";
            case "must_put_slot_1": return en ? "Slot 1 (Anvil)" : "Slot 1 (Incudine)";
            case "must_put_slot_2": return en ? "Slot 2 (Anvil)" : "Slot 2 (Incudine)";
            case "penalty_zero": return en ? "Work Penalty: 0 lv." : "Penalit\u00E0: 0 liv.";
            case "penalty_plus": return en ? "Work Penalty: +" : "Penalit\u00E0: +";
            case "levels": return en ? " lv." : " liv.";
            case "cost_xp": return en ? "Cost: " : "Costo: ";
            case "current_enchants": return en ? "Current Enchants" : "Incanti attuali";
            case "conflicts_label": return en ? "Conflicts: " : "Conflitti: ";
            case "anvil_label": return en ? "Anvil: +" : "Incudine: +";
            case "clean_books_only": return en ? "Clean books (Best in Orange)" : "Libri lisci (Migliori in Arancio)";
            case "ah_prices": return en ? "AH PRICES" : "PREZZI AH";
            case "best_prices": return en ? "BEST OFFERS:" : "MIGLIORI PREZZI:";
            case "browse_ah_hint": return en ? "Browse /ah pages to read prices!" : "Sfoglia /ah per leggere i prezzi!";
            case "right_click_remove": return en ? "Right-click card to remove offer" : "Tasto destro sulla card per rimuovere l'offerta";
            case "offer_removed": return en ? "Offer removed from list" : "Offerta rimossa dalla lista";
            case "btn_new_set": return en ? "+ New Set" : "+ Nuovo Set";
            case "empty_sets": return en ? "No sets saved yet. Create your first set!" : "Nessun set salvato. Crea il tuo primo set!";
            case "guide_btn": return en ? "Guide" : "Guida";
            case "edit_btn": return en ? "Edit" : "Modifica";
            case "delete_btn": return en ? "Delete" : "Elimina";
            case "pin_btn": return en ? "Pin" : "Pin";
            case "pinned_badge": return en ? "PINNED" : "PINNATO";
            case "unpin_btn": return en ? "Unpin" : "Sblocca";
            case "enchants_count": return en ? " enchants" : " incantesimi";
            case "total_cost": return en ? "Total Cost" : "Costo Totale";
            case "peak_cost": return en ? "Peak Cost" : "Picco";
            case "copy_guide_btn": return en ? "Copy Steps" : "Copia Passi";
            case "step_prefix": return en ? "STEP " : "PASSO ";
            case "book_fusion": return en ? "Merge Books in Anvil" : "Fusione Libri nell'Incudine";
            case "temper_equip": return en ? "Temper Book onto " : "Tempra Libro su ";
            case "result_label": return en ? "Result:" : "Risultato:";
            case "in_inventory": return en ? "In Inventory" : "In Inventario";
            case "page_prefix": return en ? "Page " : "Pagina ";
            case "of_word": return en ? " of " : " di ";
            case "enchants_word": return en ? " enchantments" : " incantesimi";
            case "all_filter": return en ? "All" : "Tutti";
            case "custom_filter": return en ? "Custom" : "Custom";
            case "vanilla_filter": return en ? "Vanilla" : "Vanilla";
            case "vanilla_plus_filter": return en ? "Vanilla+" : "Vanilla+";
            case "clear_searches": return en ? "Clear Searches" : "Azzera Cerca";
            case "searches_cleared": return en ? "Searches and filters reset!" : "Ricerche e filtri azzerati (/ah e casse)!";
            case "piece_label": return en ? "Piece: " : "Pezzo: ";
            case "set_name_hint": return en ? "Set Name (e.g. PVP Sword)" : "Nome Set (es. Spada PVP)";
            case "limit_12_reached": return en ? "Maximum 12 enchantments limit reached!" : "Limite massimo di 12 incantesimi raggiunto!";
            case "added_word": return en ? "Added: " : "Aggiunto: ";
            case "removed_word": return en ? "Removed: " : "Rimosso: ";
            case "replaced_word": return en ? "Replaced with: " : "Sostituito con: ";
            case "saved_set_msg": return en ? "Set saved successfully!" : "Set salvato con successo!";
            default: return key;
        }
    }

    public static void renderFlag(GuiGraphicsExtractor extractor, int x, int y, boolean isEng) {
        int w = 15;
        int h = 10;
        extractor.outline(x - 1, y - 1, w + 2, h + 2, 0xFF374151);

        if (!isEng) {
            // Bandiera Italiana (Verde, Bianco, Rosso)
            extractor.fill(x, y, x + 5, y + h, 0xFF009246);
            extractor.fill(x + 5, y, x + 10, y + h, 0xFFFFFFFF);
            extractor.fill(x + 10, y, x + 15, y + h, 0xFFCE2B37);
        } else {
            // Bandiera UK (Blu reale con croce rossa e bianca)
            extractor.fill(x, y, x + w, y + h, 0xFF00247D);
            extractor.fill(x, y + 4, x + w, y + 6, 0xFFFFFFFF);
            extractor.fill(x + 6, y, x + 9, y + h, 0xFFFFFFFF);
            extractor.fill(x, y + 4, x + w, y + 5, 0xFFCF142B);
            extractor.fill(x + 7, y, x + 8, y + h, 0xFFCF142B);
        }
    }
}

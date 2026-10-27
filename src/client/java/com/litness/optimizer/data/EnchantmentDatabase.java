package com.litness.optimizer.data;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnchantmentDatabase {
    public static class ItemEntry {
        public String id;
        public String name;
        public String group;
        public String texture;
        public String emoji;
    }

    public static class EnchantmentEntry {
        public String id;
        public String name;
        public String category; // "vanilla" or "custom"
        public String description;
        public int maxLevel;
        public List<String> applicableTo = new ArrayList<>();
        public String targetScope;
        public String rarity; // for custom
        public List<String> conflicts = new ArrayList<>();
        public List<String> requirements = new ArrayList<>();
        public boolean unique;
        public Integer maxPerItem;
        public Object anvilCost; // Integer or String "?"

        public int getAnvilCostNumeric() {
            loadCustomCosts();
            Integer dyn = dynamicCustomCosts.get(this.id);
            if (dyn != null && dyn > 0) return dyn;
            if (anvilCost instanceof Number) {
                return ((Number) anvilCost).intValue();
            }
            return 0;
        }
    }

    private static final File COSTS_FILE = new File("config/litnessoptimizer/custom_costs.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean costsLoaded = false;
    private static final Map<String, Integer> dynamicCustomCosts = new ConcurrentHashMap<>();

    private static synchronized void loadCustomCosts() {
        if (costsLoaded) return;
        costsLoaded = true;
        if (!COSTS_FILE.exists()) return;
        try (Reader reader = Files.newBufferedReader(COSTS_FILE.toPath())) {
            Map<String, Double> map = GSON.fromJson(reader, new TypeToken<Map<String, Double>>() {}.getType());
            if (map != null) {
                for (Map.Entry<String, Double> e : map.entrySet()) {
                    dynamicCustomCosts.put(e.getKey().toLowerCase(), e.getValue().intValue());
                }
            }
        } catch (Exception ignored) {}
    }

    private static synchronized void saveCustomCosts() {
        try {
            if (COSTS_FILE.getParentFile() != null) {
                COSTS_FILE.getParentFile().mkdirs();
            }
            try (Writer writer = Files.newBufferedWriter(COSTS_FILE.toPath())) {
                GSON.toJson(dynamicCustomCosts, writer);
            }
        } catch (Exception ignored) {}
    }
    private static final Map<String, ItemEntry> itemsById = new LinkedHashMap<>();
    private static final Map<String, EnchantmentEntry> enchantmentsById = new LinkedHashMap<>();
    private static final List<EnchantmentEntry> allEnchantments = new ArrayList<>();
    private static final List<ItemEntry> allItems = new ArrayList<>();
    private static final Map<String, List<String>> resolvedConflicts = new HashMap<>();

    private static final Map<String, Integer> VANILLA_BOOK_MULTIPLIERS = new HashMap<>();
    static {
        VANILLA_BOOK_MULTIPLIERS.put("protection", 1);
        VANILLA_BOOK_MULTIPLIERS.put("fire_protection", 1);
        VANILLA_BOOK_MULTIPLIERS.put("feather_falling", 1);
        VANILLA_BOOK_MULTIPLIERS.put("blast_protection", 1);
        VANILLA_BOOK_MULTIPLIERS.put("projectile_protection", 1);
        VANILLA_BOOK_MULTIPLIERS.put("respiration", 1);
        VANILLA_BOOK_MULTIPLIERS.put("aqua_affinity", 2);
        VANILLA_BOOK_MULTIPLIERS.put("thorns", 4);
        VANILLA_BOOK_MULTIPLIERS.put("depth_strider", 2);
        VANILLA_BOOK_MULTIPLIERS.put("frost_walker", 2);
        VANILLA_BOOK_MULTIPLIERS.put("binding_curse", 4);
        VANILLA_BOOK_MULTIPLIERS.put("soul_speed", 4);
        VANILLA_BOOK_MULTIPLIERS.put("swift_sneak", 4);
        VANILLA_BOOK_MULTIPLIERS.put("sharpness", 1);
        VANILLA_BOOK_MULTIPLIERS.put("smite", 1);
        VANILLA_BOOK_MULTIPLIERS.put("bane_of_arthropods", 1);
        VANILLA_BOOK_MULTIPLIERS.put("knockback", 1);
        VANILLA_BOOK_MULTIPLIERS.put("fire_aspect", 2);
        VANILLA_BOOK_MULTIPLIERS.put("looting", 2);
        VANILLA_BOOK_MULTIPLIERS.put("sweeping_edge", 2);
        VANILLA_BOOK_MULTIPLIERS.put("efficiency", 1);
        VANILLA_BOOK_MULTIPLIERS.put("silk_touch", 2);
        VANILLA_BOOK_MULTIPLIERS.put("unbreaking", 1);
        VANILLA_BOOK_MULTIPLIERS.put("fortune", 2);
        VANILLA_BOOK_MULTIPLIERS.put("mending", 2);
        VANILLA_BOOK_MULTIPLIERS.put("power", 1);
        VANILLA_BOOK_MULTIPLIERS.put("punch", 1);
        VANILLA_BOOK_MULTIPLIERS.put("flame", 2);
        VANILLA_BOOK_MULTIPLIERS.put("infinity", 4);
        VANILLA_BOOK_MULTIPLIERS.put("luck_of_the_sea", 1);
        VANILLA_BOOK_MULTIPLIERS.put("lure", 1);
        VANILLA_BOOK_MULTIPLIERS.put("loyalty", 1);
        VANILLA_BOOK_MULTIPLIERS.put("impaling", 2);
        VANILLA_BOOK_MULTIPLIERS.put("riptide", 2);
        VANILLA_BOOK_MULTIPLIERS.put("channeling", 4);
        VANILLA_BOOK_MULTIPLIERS.put("multishot", 2);
        VANILLA_BOOK_MULTIPLIERS.put("piercing", 1);
        VANILLA_BOOK_MULTIPLIERS.put("quick_charge", 1);
        VANILLA_BOOK_MULTIPLIERS.put("density", 1);
        VANILLA_BOOK_MULTIPLIERS.put("breach", 2);
        VANILLA_BOOK_MULTIPLIERS.put("wind_burst", 4);
        VANILLA_BOOK_MULTIPLIERS.put("vanishing_curse", 4);
    }

    public static synchronized void load() {
        loadCustomCosts();
        if (!allEnchantments.isEmpty()) return;

        try (InputStream stream = EnchantmentDatabase.class.getResourceAsStream("/assets/litnessoptimizer/data/enchantments.json")) {
            if (stream == null) {
                System.err.println("[LitnessOptimizer] Impossibile trovare enchantments.json nei resources!");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();

            Gson gson = new Gson();
            JsonArray itemsArray = root.getAsJsonArray("items");
            for (JsonElement el : itemsArray) {
                ItemEntry item = gson.fromJson(el, ItemEntry.class);
                itemsById.put(item.id, item);
                allItems.add(item);
            }

            JsonArray enchantmentsArray = root.getAsJsonArray("enchantments");
            for (JsonElement el : enchantmentsArray) {
                EnchantmentEntry ench = gson.fromJson(el, EnchantmentEntry.class);
                enchantmentsById.put(ench.id, ench);
                allEnchantments.add(ench);
            }

            // Risoluzione bidirezionale dei conflitti (sia Vanilla che Custom)
            Map<String, Set<String>> conflictMap = new HashMap<>();

            // 1. Gruppi di incompatibilità Vanilla & Vanilla+
            List<List<String>> vanillaGroups = List.of(
                List.of("sharpness", "sharpness_6", "smite", "bane_of_arthropods", "density", "breach"),
                List.of("protection", "protection_5", "fire_protection", "blast_protection", "projectile_protection"),
                List.of("fortune", "silk_touch"),
                List.of("infinity", "mending"),
                List.of("multishot", "piercing"),
                List.of("loyalty", "riptide"),
                List.of("channeling", "riptide"),
                List.of("depth_strider", "depth_strider_4", "frost_walker"),
                List.of("feather_falling", "feather_falling_5"),
                List.of("looting", "looting_4"),
                List.of("respiration", "respiration_4"),
                List.of("unbreaking", "unbreaking_4", "unbreaking_5"),
                List.of("power", "power_6")
            );

            for (List<String> group : vanillaGroups) {
                for (String aId : group) {
                    EnchantmentEntry aEntry = enchantmentsById.get(aId);
                    if (aEntry == null) continue;
                    for (String bId : group) {
                        if (aId.equals(bId)) continue;
                        EnchantmentEntry bEntry = enchantmentsById.get(bId);
                        if (bEntry != null) {
                            conflictMap.computeIfAbsent(aEntry.id, k -> new TreeSet<>()).add(bEntry.name);
                            conflictMap.computeIfAbsent(bEntry.id, k -> new TreeSet<>()).add(aEntry.name);
                        }
                    }
                }
            }

            // 2. Conflitti espliciti dal JSON (bidirezionali per Custom e Vanilla)
            for (EnchantmentEntry ee : allEnchantments) {
                if (ee.conflicts != null) {
                    for (String cName : ee.conflicts) {
                        EnchantmentEntry target = getEnchantment(cName);
                        if (target != null) {
                            conflictMap.computeIfAbsent(ee.id, k -> new TreeSet<>()).add(target.name);
                            conflictMap.computeIfAbsent(target.id, k -> new TreeSet<>()).add(ee.name);
                        } else {
                            conflictMap.computeIfAbsent(ee.id, k -> new TreeSet<>()).add(cName);
                        }
                    }
                }
            }

            for (Map.Entry<String, Set<String>> entry : conflictMap.entrySet()) {
                EnchantmentEntry self = enchantmentsById.get(entry.getKey());
                if (self != null) {
                    entry.getValue().remove(self.name);
                }
                resolvedConflicts.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }

            System.out.println("[LitnessOptimizer] Database caricato con successo: " + allItems.size() + " oggetti, " + allEnchantments.size() + " incantesimi, " + resolvedConflicts.size() + " con conflitti risolti.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<EnchantmentEntry> getAllEnchantments() {
        load();
        return allEnchantments;
    }

    public static List<ItemEntry> getAllItems() {
        load();
        return allItems;
    }

    public static EnchantmentEntry getEnchantment(String id) {
        load();
        if (id == null) return null;
        id = id.trim().toLowerCase();
        if (id.startsWith("minecraft:")) {
            id = id.substring(10);
        }

        // Risoluzione prioritaria per nomi composti critici (es. Nether Prospector vs Prospector)
        if (id.contains("nether") && id.contains("prospector")) {
            return enchantmentsById.get("nether-prospector");
        }

        // Aliases speciali per Curses
        if (id.equals("curse_of_binding") || id.equals("curse-of-binding") || id.equals("binding_curse")) {
            EnchantmentEntry ee = enchantmentsById.get("binding_curse");
            if (ee != null) return ee;
        }
        if (id.equals("curse_of_vanishing") || id.equals("curse-of-vanishing") || id.equals("vanishing_curse")) {
            EnchantmentEntry ee = enchantmentsById.get("vanishing_curse");
            if (ee != null) return ee;
        }

        EnchantmentEntry direct = enchantmentsById.get(id);
        if (direct != null) return direct;

        String alt = id.replace('-', '_');
        direct = enchantmentsById.get(alt);
        if (direct != null) return direct;

        alt = id.replace('_', '-');
        direct = enchantmentsById.get(alt);
        if (direct != null) return direct;

        String normId = id.replaceAll("[^a-z0-9]", "");
        for (EnchantmentEntry ee : allEnchantments) {
            String normEeId = ee.id.toLowerCase().replaceAll("[^a-z0-9]", "");
            String normEeName = ee.name.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (normId.equals(normEeId) || normId.equals(normEeName)) {
                return ee;
            }
            // Supporta varianti di ortografia (es. defence / defense)
            if (normId.replace("defence", "defense").equals(normEeId.replace("defence", "defense")) ||
                normId.replace("defence", "defense").equals(normEeName.replace("defence", "defense"))) {
                return ee;
            }
        }
        return null;
    }

    public static List<String> getResolvedConflicts(String id) {
        load();
        if (id == null) return Collections.emptyList();
        id = id.trim().toLowerCase();
        if (id.startsWith("minecraft:")) id = id.substring(10);

        List<String> list = resolvedConflicts.get(id);
        if (list != null) return list;

        String alt = id.replace('-', '_');
        list = resolvedConflicts.get(alt);
        if (list != null) return list;

        alt = id.replace('_', '-');
        list = resolvedConflicts.get(alt);
        if (list != null) return list;

        EnchantmentEntry ee = getEnchantment(id);
        if (ee != null) {
            return resolvedConflicts.getOrDefault(ee.id, Collections.emptyList());
        }

        return Collections.emptyList();
    }

    public static ItemEntry getItem(String id) {
        load();
        if (id == null) return null;
        if ("pickaxe".equalsIgnoreCase(id)) id = "pick";
        return itemsById.get(id);
    }

    public static boolean isApplicable(EnchantmentEntry ee, String itemId) {
        if (ee == null || itemId == null) return false;
        if (ee.applicableTo == null || ee.applicableTo.isEmpty()) return true;
        String target = itemId.trim().toLowerCase();
        if (target.equals("pickaxe")) target = "pick";
        for (String app : ee.applicableTo) {
            if (app == null) continue;
            String cleanApp = app.trim().toLowerCase();
            if (cleanApp.equals(target)) return true;
            if (target.equals("pick") && (cleanApp.equals("pick") || cleanApp.equals("pickaxe") || cleanApp.equals("tools"))) return true;
            if (target.equals("chest") && (cleanApp.equals("chestplate") || cleanApp.equals("chest"))) return true;
            if (target.equals("legs") && (cleanApp.equals("leggings") || cleanApp.equals("legs"))) return true;
            if ((target.equals("sword") || target.equals("axe")) && cleanApp.equals("weapons")) return true;
            if ((target.equals("helmet") || target.equals("chest") || target.equals("legs") || target.equals("boots")) && cleanApp.equals("armor")) return true;
        }
        return false;
    }

    public static Integer getVanillaMultiplier(String enchId) {
        return VANILLA_BOOK_MULTIPLIERS.get(enchId);
    }

    public static void updateAnvilCost(String enchId, int cost) {
        if (enchId == null || cost <= 0) return;
        load();
        String clean = enchId.toLowerCase().trim();
        dynamicCustomCosts.put(clean, cost);
        EnchantmentEntry ee = enchantmentsById.get(clean);
        if (ee != null) {
            ee.anvilCost = cost;
        }
        String alt = clean.replace('_', '-');
        dynamicCustomCosts.put(alt, cost);
        EnchantmentEntry eeAlt = enchantmentsById.get(alt);
        if (eeAlt != null) {
            eeAlt.anvilCost = cost;
        }
        saveCustomCosts();
    }

    public static String resolveVanillaPlusId(String baseId, int level) {
        if (baseId == null) return null;
        String id = baseId.toLowerCase().trim();
        if (id.startsWith("minecraft:")) id = id.substring(10);
        if ("sharpness".equals(id) && level >= 6) return "sharpness_6";
        if ("feather_falling".equals(id) && level >= 5) return "feather_falling_5";
        if ("looting".equals(id) && level >= 4) return "looting_4";
        if ("respiration".equals(id) && level >= 4) return "respiration_4";
        if ("unbreaking".equals(id) && level == 4) return "unbreaking_4";
        if ("unbreaking".equals(id) && level >= 5) return "unbreaking_5";
        if ("depth_strider".equals(id) && level >= 4) return "depth_strider_4";
        if ("protection".equals(id) && level >= 5) return "protection_5";
        if ("power".equals(id) && level >= 6) return "power_6";
        return null;
    }

    public static int getEnchantmentCost(EnchantmentEntry ench, int level) {
        if ("vanilla".equalsIgnoreCase(ench.category)) {
            Integer mult = VANILLA_BOOK_MULTIPLIERS.get(ench.id);
            if (mult != null) return mult * level;
            return 1 * level;
        }
        Integer dyn = dynamicCustomCosts.get(ench.id);
        if (dyn != null && dyn > 0) return dyn;
        return ench.getAnvilCostNumeric();
    }
}

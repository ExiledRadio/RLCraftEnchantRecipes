package com.exiledradio.rlcraftenchantrecipes;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pack-compatibility layer for {@link ModConfig#PACK_MODE}.
 *
 * <p>Every recipe in this mod was authored against RLCraft Dregora v1.1.2b. Base RLCraft 2.9.3
 * ships older versions of several mods, so some registry IDs the recipes ask for either don't
 * exist there or exist under a different name. This class is the single place that knows about
 * those differences: {@link #findEnchantment} and {@link #findItem} replace the direct
 * {@code REGISTRY.getObject(...)} calls throughout ModRecipes, so no individual recipe has to
 * care which pack it's running in.
 *
 * <p><b>Dregora behaviour is unchanged by design.</b> In "dregora" mode the exact lookup always
 * hits on the first try and none of the fallback machinery below runs.
 *
 * <p>Resolution order in "rlcraft" mode is: exact ID, then an explicit alias, then a
 * case-insensitive registry scan. Anything still unresolved returns {@code null}, which the
 * calling recipe already handles by logging and skipping itself.
 */
public final class PackCompat {

    private PackCompat() {
    }

    public static boolean isRLCraft() {
        return "rlcraft".equals(ModConfig.PACK_MODE);
    }

    // ============================================================
    // SoManyEnchantments 1.0.8 (Dregora) -> 0.5.5 (base RLCraft)
    //
    // 0.5.5 doesn't merely use different casing - a lot of enchantments have
    // genuinely different registry names ("Arc Slash" is registered as Swiper,
    // "Jagged Rake" as ScytheDamage), and two of them are misspelled in 0.5.5
    // itself (AdvancedEfficency, Inefficent). These were derived by matching
    // display names between the two jars' en_us.lang, not guessed.
    //
    // The ~83 enchantments that differ only in casing are deliberately absent
    // from this map - the case-insensitive scan in resolveEnchantment() covers
    // them, which keeps this table down to the genuine renames.
    //
    // Enchantments with no base-RLCraft equivalent are also deliberately absent:
    // they resolve to null and their recipes skip themselves. That covers
    // Ascetic, Breached Plating, Extinguish, Combat Medic, and every school
    // subject except P.E. (base RLCraft only enables English, Science and P.E.).
    // ============================================================
    private static final Map<String, String> SME_ALIASES;

    static {
        Map<String, String> m = new HashMap<String, String>();
        m.put("advancedefficiency", "AdvancedEfficency");     // Advanced Efficiency (sic)
        m.put("advancedfireaspect", "afa");                   // Advanced Fire Aspect
        m.put("advancedflame", "afl");                        // Advanced Flame
        m.put("ancientswordmastery", "SwordMastery");         // Ancient Sword Mastery
        m.put("arcslash", "Swiper");                          // Arc Slash
        m.put("burningshield", "fieryshield");                // Burning Shield
        m.put("clearskiesfavor", "Clearsky");                 // Clearskies' Favor
        m.put("defusingedge", "Defusion");                    // Defusing Edge
        m.put("disorientatingblade", "Disorientation");       // Disorientating Blade
        m.put("dragging", "Pulling");                         // Dragging
        m.put("horsdecombat", "Hors_de_combat");              // Hors De Combat
        m.put("inefficient", "Inefficent");                   // Inefficient (sic)
        m.put("jaggedrake", "ScytheDamage");                  // Jagged Rake
        m.put("lunasblessing", "Moonlight");                  // Luna's -> Lunar's Blessing
        m.put("moisturized", "WellTilled");                   // Moisturized
        m.put("plowing", "TillingPower");                     // Plowing
        m.put("rainsbestowment", "Raining");                  // Rain's Bestowment
        m.put("reinforcedsharpness", "sharperedge");          // Reinforced Sharpness
        m.put("solsblessing", "Sunshine");                    // Sol's Blessing
        m.put("subjectpe", "PE");                             // Subject P.E.
        m.put("supremefireaspect", "sfa");                    // Supreme Fire Aspect
        m.put("supremeflame", "sfl");                         // Supreme Flame
        m.put("swiftswimming", "UnderwaterStrider");          // Swift Swimming -> Underwater Strider
        m.put("thunderstormsbestowment", "Thunderstorm");     // Thunderstorm's Bestowment
        m.put("unreasonable", "Frenzy");                      // Unreasonable
        m.put("upgradedpotentials", "upgrade");               // Upgraded Potentials
        m.put("wintersgrace", "Winter");                      // Winter's Grace
        SME_ALIASES = Collections.unmodifiableMap(m);
    }

    // ============================================================
    // Item substitutions for content base RLCraft doesn't have.
    // hydra_heart is NOT here - "any dragon heart" needs a multi-option
    // ingredient, so ModRecipes handles that one at the recipe site.
    // ============================================================
    private static final Map<String, ResourceLocation> ITEM_SUBSTITUTIONS;

    static {
        Map<String, ResourceLocation> m = new HashMap<String, ResourceLocation>();
        // Ice and Fire 1.7.1 has no copper at all; silver is the closest analogue.
        m.put("iceandfire:copper_ingot", new ResourceLocation("iceandfire", "silver_ingot"));
        // 1.7.1 predates hydras entirely.
        m.put("iceandfire:hydra_fang", new ResourceLocation("iceandfire", "sea_serpent_fang"));
        // Spartan Weaponry 1.5.3 predates scythes.
        m.put("spartanweaponry:scythe_iron", new ResourceLocation("minecraft", "iron_hoe"));
        ITEM_SUBSTITUTIONS = Collections.unmodifiableMap(m);
    }

    // Lazily built lowercase-path -> real ResourceLocation indexes. Registries are
    // frozen by the time recipes register, so caching once is safe.
    private static Map<String, ResourceLocation> enchantIndex;
    private static Map<String, ResourceLocation> itemIndex;

    private static Map<String, ResourceLocation> enchantIndex() {
        if (enchantIndex == null) {
            Map<String, ResourceLocation> m = new HashMap<String, ResourceLocation>();
            for (ResourceLocation key : Enchantment.REGISTRY.getKeys()) {
                m.put(indexKey(key), key);
            }
            enchantIndex = m;
        }
        return enchantIndex;
    }

    private static Map<String, ResourceLocation> itemIndex() {
        if (itemIndex == null) {
            Map<String, ResourceLocation> m = new HashMap<String, ResourceLocation>();
            for (ResourceLocation key : Item.REGISTRY.getKeys()) {
                m.put(indexKey(key), key);
            }
            itemIndex = m;
        }
        return itemIndex;
    }

    private static String indexKey(ResourceLocation key) {
        // ResourceLocation.toString() is already "domain:path".
        return key.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Enchantment lookup that tolerates base RLCraft's older SoManyEnchantments naming.
     * Returns null when there is no equivalent, exactly like a direct registry miss.
     */
    public static Enchantment findEnchantment(String modid, String name) {
        Enchantment exact = Enchantment.REGISTRY.getObject(new ResourceLocation(modid, name));
        if (exact != null || !isRLCraft()) {
            return exact;
        }

        if ("somanyenchantments".equals(modid)) {
            String alias = SME_ALIASES.get(name.toLowerCase(Locale.ROOT));
            if (alias != null) {
                Enchantment aliased = Enchantment.REGISTRY.getObject(new ResourceLocation(modid, alias));
                if (aliased != null) {
                    return aliased;
                }
            }
        }

        ResourceLocation ci = enchantIndex().get((modid + ":" + name).toLowerCase(Locale.ROOT));
        return ci == null ? null : Enchantment.REGISTRY.getObject(ci);
    }

    /**
     * Item lookup that applies base-RLCraft material substitutions.
     * Returns null when there is no equivalent.
     */
    public static Item findItem(String modid, String name) {
        Item exact = Item.REGISTRY.getObject(new ResourceLocation(modid, name));
        if (exact != null || !isRLCraft()) {
            return exact;
        }

        ResourceLocation substitute = ITEM_SUBSTITUTIONS.get(
                (modid + ":" + name).toLowerCase(Locale.ROOT));
        if (substitute != null) {
            Item swapped = Item.REGISTRY.getObject(substitute);
            if (swapped != null) {
                RLCraftEnchantRecipes.LOGGER.info("PACK_MODE=rlcraft: substituting " + substitute
                        + " for missing " + modid + ":" + name);
                return swapped;
            }
        }

        ResourceLocation ci = itemIndex().get((modid + ":" + name).toLowerCase(Locale.ROOT));
        return ci == null ? null : Item.REGISTRY.getObject(ci);
    }
}

package com.exiledradio.rlcraftenchantrecipes;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = RLCraftEnchantRecipes.MODID)
public class ModRecipes {

    // Populated as tome recipes register; read by ModJeiPlugin to build JEI-only
    // display recipes (a full, undamaged tome), since the real ConfigurableTomeRecipe
    // isn't a class JEI's vanilla plugin recognizes on its own.
    static final List<ConfigurableTomeRecipe> TOME_RECIPES = new ArrayList<>();

    // Set once if the dragon head upgrade recipe registers successfully;
    // read by ModJeiPlugin the same way as TOME_RECIPES.
    static DragonHeadUpgradeRecipe DRAGON_HEAD_UPGRADE_RECIPE;

    public static void preInit() {
        // Intentionally empty – everything happens in the registry event
    }

    // ============================================================
    // Difficulty tier ladder (ModConfig.DIFFICULTY_MODE). Every recipe in this
    // file looks up its glowing corner material through resolveGlowingPowder()/
    // resolveGlowingIngot()/resolveGlowingGem() instead of resolving
    // xat:glowing_powder/ingot/gem directly, so a single shift here silently
    // reprices every recipe in the mod without touching any of them individually.
    // "easy" shifts every recipe one tier cheaper, "hard" one tier more
    // expensive, "normal" leaves the ladder as originally designed.
    // ============================================================
    private static final ResourceLocation[] TIER_LADDER = {
            new ResourceLocation("minecraft", "glowstone_dust"),
            new ResourceLocation("xat", "glowing_powder"),
            new ResourceLocation("xat", "glowing_ingot"),
            new ResourceLocation("xat", "glowing_gem"),
            new ResourceLocation(RLCraftEnchantRecipes.MODID, "glowing_gem_block"),
    };

    private static int tierShift() {
        switch (ModConfig.DIFFICULTY_MODE) {
            case "easy": return -1;
            case "hard": return 1;
            default: return 0;
        }
    }

    private static Item resolveTier(int normalLadderIndex) {
        int index = normalLadderIndex + tierShift();
        index = Math.max(0, Math.min(TIER_LADDER.length - 1, index));
        return Item.REGISTRY.getObject(TIER_LADDER[index]);
    }

    private static Item resolveGlowingPowder() {
        return resolveTier(1);
    }

    private static Item resolveGlowingIngot() {
        return resolveTier(2);
    }

    private static Item resolveGlowingGem() {
        return resolveTier(3);
    }

    // Ice and Fire 1.7.1 (base RLCraft) predates hydras, so there is no hydra
    // heart to ask for. In PACK_MODE=rlcraft any dragon heart stands in for it;
    // the fire/ice pair is offered as alternatives rather than picking one, so
    // players aren't forced into a specific dragon type. Returns an Item in
    // dregora mode and a multi-option ingredient in rlcraft mode, or null if
    // nothing usable exists.
    private static Object resolveHydraHeartSlot() {
        Item hydraHeart = PackCompat.findItem("iceandfire", "hydra_heart");
        if (hydraHeart != null && !PackCompat.isRLCraft()) {
            return hydraHeart;
        }

        Item fireHeart = PackCompat.findItem("iceandfire", "fire_dragon_heart");
        Item iceHeart = PackCompat.findItem("iceandfire", "ice_dragon_heart");
        if (fireHeart == null && iceHeart == null) {
            return hydraHeart;
        }
        if (fireHeart == null) {
            return iceHeart;
        }
        if (iceHeart == null) {
            return fireHeart;
        }
        return new AnyOfItemsIngredient(fireHeart, iceHeart);
    }

    // Ocean loot is meaningfully harder to come by in base RLCraft than in
    // Dregora, so PACK_MODE=rlcraft steps both ocean materials down one crafting
    // step: the prismarine block becomes the shard it's built from, and the sea
    // lantern becomes prismarine crystals.
    private static Item resolvePrismarineSlot() {
        return PackCompat.isRLCraft()
                ? Items.PRISMARINE_SHARD
                : Item.getItemFromBlock(net.minecraft.init.Blocks.PRISMARINE);
    }

    private static Item resolveSeaLanternSlot() {
        return PackCompat.isRLCraft()
                ? Items.PRISMARINE_CRYSTALS
                : Item.getItemFromBlock(net.minecraft.init.Blocks.SEA_LANTERN);
    }

    // Drops nulls out of an alternatives list. Used for the elemental dragonbone
    // weapon slots: base RLCraft's Spartan Fire 1.1.0 has no lightning variants
    // (Ice and Fire 1.7.1 has no lightning dragons to bleed for them), so those
    // are accepted when present and quietly left out when not, instead of
    // failing the whole recipe.
    private static Item[] presentOnly(Item... candidates) {
        int count = 0;
        for (Item candidate : candidates) {
            if (candidate != null) count++;
        }
        Item[] present = new Item[count];
        int i = 0;
        for (Item candidate : candidates) {
            if (candidate != null) present[i++] = candidate;
        }
        return present;
    }

    // ============================================================
    // All registration happens here (correct timing)
    // ============================================================
    @SubscribeEvent
    public static void onRegisterRecipes(RegistryEvent.Register<IRecipe> event) {
        // ===== Normal recipes (no external mod dependencies) =====
        registerUnbreakingIII();

        Item glowingPowder = resolveGlowingPowder();
        if (glowingPowder == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder! Skipping recipes that need it.");
        }

        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping recipes that need it.");
        }

        // ===== XP tome recipes =====
        Item xpTome = PackCompat.findItem("xpbook", "xp_book");
        if (xpTome == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xpbook:xp_book! Skipping xp tome recipes.");
        } else {
            if (glowingIngot != null) {
                registerTomeRecipe(event, "mending", Enchantments.MENDING, 1,
                        "GBG", "BXB", "GBG",
                        'G', glowingIngot,
                        'B', Items.EXPERIENCE_BOTTLE,
                        'X', new XpTomeIngredient(xpTome));
            }

            if (glowingPowder != null) {
                registerAdeptIII(event, xpTome, glowingPowder);
            }

            registerLifestealII(event, xpTome);
            registerVampirismII(event, xpTome);
            registerEducationIII(event, xpTome);
            registerSwifterSlashesV(event, xpTome);
            registerParry(event, xpTome);
            registerRunePiercingCapabilities(event, xpTome);
            registerNaturalBlockingII(event, xpTome);
            registerRuneArrowPiercing(event, xpTome);
            registerEnvenomedIII(event, xpTome);
            registerViperV(event, xpTome);
            registerAshDestroyerV(event, xpTome);
            registerComboIII(event, xpTome);
            registerPurgingBladeV(event, xpTome);
            registerAdvancedFireAspectII(event, xpTome);
            registerSupremeFireAspectII(event, xpTome);
            registerAtomicDeconstructorII(event, xpTome);
            registerStrengthenedVitalityV(event, xpTome);
            registerDoubleJump(event, xpTome);
            registerLightWeightIII(event, xpTome);
            registerWallRunning(event, xpTome);
            registerSliding(event, xpTome);
            registerCurseOfPossession(event, xpTome);
            registerCriticalStrike(event, xpTome);
            registerAncientSwordMasteryIII(event, xpTome);
            registerBlessedEdgeV(event, xpTome);
            registerBrutalityV(event, xpTome);
            registerBurningShieldIV(event, xpTome);
            registerCombatMedicIII(event, xpTome);
            registerCounterAttackIII(event, xpTome);
            registerCullingIII(event, xpTome);
            registerCryogenicIII(event, xpTome);
            registerDarkShadowsIII(event, xpTome);
            registerDefusingEdgeV(event, xpTome);
            registerDesolatorIV(event, xpTome);
            registerDisarmamentV(event, xpTome);
            registerDisarmIII(event, xpTome);
            registerDisorientatingBladeIV(event, xpTome);
            registerEmpoweredDefenceII(event, xpTome);
            registerEvasionI(event, xpTome);
            registerHorsDeCombatIV(event, xpTome);
            registerInhumaneV(event, xpTome);
            registerInnerBerserkIV(event, xpTome);
            if (glowingPowder != null) {
                registerJaggedRakeV(event, xpTome, glowingPowder);
                registerSmelting(event, xpTome, glowingPowder);
                registerHoming(event, glowingPowder, xpTome);
                registerComplexityIII(event, glowingPowder);
                registerSturdyIII(event, glowingPowder);
                registerShockingV(event, glowingPowder);
                registerMagnetic(event, glowingPowder, xpTome);
                registerCurses(event, glowingPowder);
            }
            registerLevitatorII(event, xpTome);
            registerLuckMagnificationII(event, xpTome);
            registerLunasBlessingV(event, xpTome);
            registerMagmaWalkerII(event, xpTome);
            registerFrostWalkerII(event, xpTome);
            registerMortalitasVIII(event, xpTome);
            registerPenetratingEdgeVI(event, xpTome);
            registerPenetrationV(event, xpTome);
            registerPushingI(event, xpTome);
            registerRainsBestowmentV(event, xpTome);
            registerReinforcedSharpnessV(event, xpTome);
            registerReviledBladeIV(event, xpTome);
            registerRuneMagicalBlessingIV(event, xpTome);
            registerRuneResurrectionII(event, xpTome);
            registerRuneRevivalII(event, xpTome);
            registerSolsBlessingV(event, xpTome);
            if (glowingPowder != null) {
                registerSmelterI(event, xpTome, glowingPowder);
            }
            registerAdvancedSharpnessV(event, xpTome);
        }

        // ===== Burning Thorns (self-contained: consumes a Thorns III book, looks up its own glowing ingot) =====
        registerBurningThornsIII(event);

        // ===== Butchering V (self-contained: consumes a Looting III book, looks up its own glowing ingot) =====
        registerButcheringV(event);

        // ===== Flinging / Fling (self-contained: consume a Knockback II book, look up their own glowing ingot) =====
        registerFlingingII(event);
        registerFlingII(event);

        // ===== Advanced mending (consumes a Mending book instead of an xp tome) =====
        if (glowingIngot != null) {
            registerAdvancedMending(event, glowingIngot);
        }

        if (glowingPowder != null) {
            registerProtectionIV(event, glowingPowder);
            registerFortuneIII(event, glowingPowder);
            registerLootingIII(event, glowingPowder);
            registerSmiteV(event, glowingPowder);
            registerBaneOfArthropodsV(event, glowingPowder);
            registerFeatherFallingIV(event, glowingPowder);
            registerPowerV(event, glowingPowder);
            registerFireAspectII(event, glowingPowder);
            registerHeating(event, glowingPowder);
            registerChilling(event, glowingPowder);
            registerRespirationIII(event, glowingPowder);
            registerAquaAffinity(event, glowingPowder);
            registerFlame(event, glowingPowder);
            registerDepthStriderIII(event, glowingPowder);
            registerKnockbackII(event, glowingPowder);
            registerLuckOfTheSeaIII(event, glowingPowder);
            registerLureIII(event, glowingPowder);
            registerPunchII(event, glowingPowder);
            registerSilkTouch(event, glowingPowder);
            registerThornsIII(event, glowingPowder);
            registerSweepingEdgeIII(event, glowingPowder);
            registerPlowingI(event, glowingPowder);
            registerMoisturizedI(event, glowingPowder);
        }

        if (glowingIngot != null) {
            registerSharpnessV(event, glowingIngot);
            registerFieryEdge(event, glowingIngot);
            registerAdvancedKnockback(event, glowingIngot);
            registerAdvancedFlame(event, glowingIngot);
            registerSupremeFlame(event);
            registerAdvancedPunch(event, glowingIngot);
            registerAdvancedThorns(event, glowingIngot);
            if (xpTome != null) {
                registerInfinity(event, glowingIngot, xpTome);
            }
            registerAdvancedLuckOfTheSea(event, glowingIngot);
            registerAdvancedLureIII(event, glowingIngot);
            if (xpTome != null) {
                registerPurification(event, glowingIngot, xpTome);
                registerSpellBreaker(event, glowingIngot, xpTome);
                registerSplitshot(event, glowingIngot, xpTome);
                registerMultishot(event, glowingIngot, xpTome);
                registerStrafe(event, glowingIngot, xpTome);
                registerSwiftSwimming(event, glowingIngot, xpTome);
                registerThunderstormsBestowmentV(event, glowingIngot, xpTome);
                registerTrueStrike(event, glowingIngot, xpTome);
                registerUnreasonable(event, glowingIngot, xpTome);
                registerUnsheathing(event, glowingIngot, xpTome);
                registerUpgradedPotentials(event, xpTome);
                registerWaterAspectV(event, glowingIngot, xpTome);
                registerWintersGrace(event, glowingIngot, xpTome);
                registerSubjectGeographyV(event, glowingIngot, xpTome);
                registerSubjectBiologyV(event, glowingIngot, xpTome);
                registerSubjectChemistryV(event, glowingIngot, xpTome);
                registerSubjectHistoryV(event, glowingIngot, xpTome);
                registerSubjectMathematicsV(event, glowingIngot, xpTome);
                registerSubjectPeV(event, xpTome);
                registerSubjectPhysicsV(event, glowingIngot, xpTome);
                // Base RLCraft only enables English, Science and P.E. of the school
                // subjects, and the first two don't exist in Dregora's SoManyEnchantments
                // at all - so these two recipes are rlcraft-mode only. The remaining
                // subject recipes above resolve to null there and skip themselves.
                registerSubjectEnglishV(event, glowingIngot, xpTome);
                registerSubjectScienceV(event, glowingIngot, xpTome);
                registerAgilityII(event, glowingIngot, xpTome);
                registerArrowRecoveryIII(event, glowingIngot, xpTome);
                registerAssassinate(event, glowingIngot, xpTome);
                registerBash(event, glowingIngot, xpTome);
                registerBlast(event, glowingIngot, xpTome);
                registerBlockingPower(event, glowingIngot, xpTome);
                registerDiamondsEverywhere(event, glowingIngot, xpTome);
                registerHeaviness(event, glowingIngot, xpTome);
                registerRange(event, glowingIngot, xpTome);
                registerRapidFire(event, glowingIngot, xpTome);
                registerReflection(event, glowingIngot);
                registerSpikes(event, glowingIngot);
                registerSpellproof(event, glowingIngot, xpTome);
                registerTunneling(event, glowingIngot, xpTome);
                registerVersatility(event, glowingIngot, xpTome);
                registerWeightless(event, glowingIngot, xpTome);
                registerHighJump(event, glowingIngot, xpTome);
                registerExpanse(event, glowingIngot);
                registerHydrodynamic(event, glowingIngot);
                registerIncendiary(event, glowingIngot);
                registerLuckyThrow(event, glowingIngot);
                registerPropulsion(event, glowingIngot);
                registerRapidLoadIII(event, glowingIngot, xpTome);
                registerRazorsEdge(event, glowingIngot);
                registerReturn(event, glowingIngot);
                registerSpreadshot(event, glowingIngot);
                registerSupercharge(event, glowingIngot);
                registerSharpshooter(event, glowingIngot, xpTome);
                registerEconomical(event, glowingIngot, xpTome);
                registerDestructiveV(event, glowingIngot, xpTome);
                registerSafeguard(event, glowingIngot, xpTome);
                registerBlazing(event, glowingIngot, xpTome);
                registerCurseBreak(event, glowingIngot, xpTome);
                registerScope(event, glowingIngot, xpTome);
                registerPullSpeed(event, glowingIngot, xpTome);
                registerReduceCooldown(event, glowingIngot, xpTome);
            }
        }

        // ===== Arc Slash III (self-contained: consumes a Sweeping Edge III book) =====
        registerArcSlashIII(event);

        // ===== Clearskies' Favor (self-contained: xat tier-2 material + xp tome) =====
        registerClearskiesFavor(event);

        // ===== Advanced Power V (self-contained: looks up its own xat tier-2 material) =====
        registerAdvancedPowerV(event);

        // ===== Advanced Protection IV (self-contained: looks up its own xat tier-2 material) =====
        registerAdvancedProtectionIV(event);

        // ===== Advanced Looting III (self-contained: looks up its own defiledlands material) =====
        registerAdvancedLootingIII(event);

        // ===== Efficiency V (fully vanilla, no external mod dependencies) =====
        registerEfficiencyV(event);

        // ===== Protection variants: Fire/Blast/Projectile (fully vanilla) =====
        registerFireProtectionIV(event);
        registerBlastProtectionIV(event);
        registerProjectileProtectionIV(event);

        // ===== Physical/Magic Protection IV (self-contained: somanyenchantments) =====
        registerPhysicalProtectionIV(event);
        registerMagicProtectionIV(event);

        // ===== Advanced Feather Falling IV (self-contained: xat + potioncore) =====
        registerAdvancedFeatherFallingIV(event);

        // ===== Advanced Fire Protection IV (self-contained: quark + bountifulbaubles materials) =====
        registerAdvancedFireProtectionIV(event);

        // ===== Advanced Blast Protection IV (fully vanilla) =====
        registerAdvancedBlastProtectionIV(event);

        // ===== Advanced Projectile Protection IV (self-contained: spartanweaponry material) =====
        registerAdvancedProjectileProtectionIV(event);

        // ===== Advanced Efficiency V (self-contained: looks up its own xat tier-2 material) =====
        registerAdvancedEfficiencyV(event);

        // ===== Supreme Sharpness V (self-contained: xat tier-3 + iceandfire materials) =====
        registerSupremeSharpnessV(event);

        // ===== Advanced/Supreme Bane of Arthropods (self-contained: xat tier-2/tier-3 materials) =====
        registerAdvancedBaneOfArthropodsV(event);
        registerSupremeBaneOfArthropodsV(event);

        // ===== Advanced/Supreme Smite (self-contained: xat tier-2/tier-3 + iceandfire materials) =====
        registerAdvancedSmiteV(event, xpTome);
        registerSupremeSmiteV(event, xpTome);

        // ===== Dragon Head upgrade (non-enchantment recipe; self-contained) =====
        if (ModConfig.ENABLE_DRAGON_HEAD_UPGRADE) {
            registerDragonHeadUpgrade(event);
        }
    }

    // ============================================================
    // Normal recipe helper
    // ============================================================
    private static void registerUnbreakingIII() {
        Item glowingPowder = resolveGlowingPowder();
        if (glowingPowder == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder! Skipping unbreaking_iii recipe.");
            return;
        }

        ItemStack book = createBook(Enchantments.UNBREAKING, 3);

        GameRegistry.addShapedRecipe(
                new ResourceLocation(RLCraftEnchantRecipes.MODID, "unbreaking_iii"),
                null,
                book,
                "GAG",
                "OBO",
                "GAG",
                'G', glowingPowder,
                'A', net.minecraft.init.Blocks.ANVIL,
                'O', net.minecraft.init.Blocks.OBSIDIAN,
                'B', Items.BOOK
        );

        RLCraftEnchantRecipes.LOGGER.info("Registered normal recipe: unbreaking_iii");
    }

    // ============================================================
    // Tome recipe helper (configurable recipe, self-describing to JEI)
    // ============================================================
    private static void registerTomeRecipe(RegistryEvent.Register<IRecipe> event,
                                           String name, Enchantment ench, int level,
                                           String r1, String r2, String r3,
                                           Object... ingredients) {

        ItemStack book = createBook(ench, level);
        registerConfigurableRecipe(event, name, book, r1, r2, r3, ingredients);
    }

    // ============================================================
    // Advanced Mending: same shape/materials as mending (glowing ingot
    // corners), but consumes a Mending book instead of an xp tome, and
    // outputs an Advanced Mending book (a custom, stronger enchantment
    // added by the "So Many Enchantments" mod -
    // somanyenchantments:advancedmending).
    // ============================================================
    private static void registerAdvancedMending(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Enchantment advancedMending = PackCompat.findEnchantment("somanyenchantments", "advancedmending");
        if (advancedMending == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedmending! Skipping advanced_mending recipe.");
            return;
        }

        ItemStack output = createBook(advancedMending, advancedMending.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_mending", output,
                "GBG", "BXB", "GBG",
                'G', glowingIngot,
                'B', Items.EXPERIENCE_BOTTLE,
                'X', new EnchantedBookIngredient(Enchantments.MENDING, 1));
    }

    // ============================================================
    // Lifesteal II: xp tome center, glowing ingot corners (upgraded from
    // glowing powder), a scalinghealth heart container on the top-middle,
    // scalinghealth Medkits on the left/right middle, and a roughtweaks
    // Enchanted Medikit on the bottom-middle. Produces
    // somanyenchantments:lifesteal.
    //
    // The Medkit isn't its own registry entry - decompiling the mod's own
    // recipe JSON showed it's scalinghealth:healingitem at damage 1 (damage
    // 0 is a lesser healing item); the "medkit" name only exists as the
    // model/recipe filename, which is why a direct scalinghealth:medkit
    // lookup silently failed and dropped this recipe out of JEI entirely.
    // ============================================================
    private static void registerLifestealII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item heartContainer = PackCompat.findItem("scalinghealth", "heartcontainer");
        Item healingItem = PackCompat.findItem("scalinghealth", "healingitem");
        Item enchantedMedikit = PackCompat.findItem("roughtweaks", "medikitenchanted");
        if (glowingIngot == null || heartContainer == null || healingItem == null || enchantedMedikit == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot, scalinghealth:heartcontainer, scalinghealth:healingitem, or roughtweaks:medikitenchanted! Skipping lifesteal_ii recipe.");
            return;
        }

        Enchantment lifesteal = PackCompat.findEnchantment("somanyenchantments", "lifesteal");
        if (lifesteal == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:lifesteal! Skipping lifesteal_ii recipe.");
            return;
        }

        ItemStack medkit = new ItemStack(healingItem, 1, 1);

        ItemStack output = createBook(lifesteal, lifesteal.getMaxLevel());
        registerConfigurableRecipe(event, "lifesteal_ii", output,
                "GHG", "MXM", "GEG",
                'G', glowingIngot,
                'H', heartContainer,
                'M', medkit,
                'E', enchantedMedikit,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Vampirism II: glowing ingot corners, heart containers on the
    // left/right middle, a switchbow Vampire-Arrow on the top-middle, and
    // a roughtweaks Enchanted Medikit on the bottom-middle. Produces
    // mujmajnkraftsbettersurvival:vampirism (a distinct lifesteal-style
    // enchantment from a different mod than lifesteal_ii).
    // ============================================================
    private static void registerVampirismII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item heartContainer = PackCompat.findItem("scalinghealth", "heartcontainer");
        Item vampireArrow = PackCompat.findItem("switchbow", "arrowvampier");
        Item enchantedMedikit = PackCompat.findItem("roughtweaks", "medikitenchanted");
        if (glowingIngot == null || heartContainer == null || vampireArrow == null || enchantedMedikit == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot, scalinghealth:heartcontainer, switchbow:arrowvampier, or roughtweaks:medikitenchanted! Skipping vampirism_ii recipe.");
            return;
        }

        Enchantment vampirism = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "vampirism");
        if (vampirism == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:vampirism! Skipping vampirism_ii recipe.");
            return;
        }

        ItemStack output = createBook(vampirism, vampirism.getMaxLevel());
        registerConfigurableRecipe(event, "vampirism_ii", output,
                "GAG", "HXH", "GEG",
                'G', glowingIngot,
                'A', vampireArrow,
                'H', heartContainer,
                'E', enchantedMedikit,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Education III: glowing ingot corners, an experience bottle in the
    // center, and 4 xp tomes on the edge-middle slots (each gated by the
    // live ModConfig damage threshold, same as the other tome recipes).
    // Deliberately expensive - Education is a very strong enchantment
    // (mujmajnkraftsbettersurvival:education, boosts XP gained from kills).
    // ============================================================
    private static void registerEducationIII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping education_iii recipe.");
            return;
        }

        Enchantment education = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "education");
        if (education == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:education! Skipping education_iii recipe.");
            return;
        }

        ItemStack output = createBook(education, education.getMaxLevel());
        registerConfigurableRecipe(event, "education_iii", output,
                "GTG", "TXT", "GTG",
                'G', glowingIngot,
                'T', new XpTomeIngredient(xpTome),
                'X', Items.EXPERIENCE_BOTTLE);
    }

    // ============================================================
    // Adept III: same layout as education_iii, but the top/bottom middle
    // xp tomes are swapped for glowing powder, so only the left/right
    // middle slots still need a tome - a cheaper, lesser XP-boost book
    // (somanyenchantments:adept) than education_iii.
    // ============================================================
    private static void registerAdeptIII(RegistryEvent.Register<IRecipe> event, Item xpTome, Item glowingPowder) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping adept_iii recipe.");
            return;
        }

        Enchantment adept = PackCompat.findEnchantment("somanyenchantments", "adept");
        if (adept == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:adept! Skipping adept_iii recipe.");
            return;
        }

        ItemStack output = createBook(adept, adept.getMaxLevel());
        registerConfigurableRecipe(event, "adept_iii", output,
                "GPG", "TXT", "GPG",
                'G', glowingIngot,
                'P', glowingPowder,
                'T', new XpTomeIngredient(xpTome),
                'X', Items.EXPERIENCE_BOTTLE);
    }

    // ============================================================
    // Swifter Slashes V: glowing gem corners, a potionfingers Ring of
    // Speed on the top/bottom middle, a Potion of Swiftness II on the
    // left/right middle, xp tome in the center.
    // ============================================================
    private static void registerSwifterSlashesV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingGem = resolveGlowingGem();
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        if (glowingGem == null || potionRing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem or potionfingers:ring! Skipping swifter_slashes_v recipe.");
            return;
        }

        Enchantment swifterSlashes = PackCompat.findEnchantment("somanyenchantments", "swifterslashes");
        if (swifterSlashes == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:swifterslashes! Skipping swifter_slashes_v recipe.");
            return;
        }

        SpecialIngredient ringOfSpeed = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:speed");
        SpecialIngredient swiftnessII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:strong_swiftness");

        ItemStack output = createBook(swifterSlashes, swifterSlashes.getMaxLevel());
        registerConfigurableRecipe(event, "swifter_slashes_v", output,
                "GRG", "PXP", "GRG",
                'G', glowingGem,
                'R', ringOfSpeed,
                'P', swiftnessII,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Parry: glowing ingot corners, xp tome in the center, a diamond-tier
    // spartanshields shield on the remaining edge slots, a Bountiful
    // Baubles Cross Necklace on bottom.
    // ============================================================
    private static void registerParry(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item diamondShield = PackCompat.findItem("spartanshields", "shield_basic_diamond");
        Item crossNecklace = PackCompat.findItem("bountifulbaubles", "amuletcross");
        if (glowingIngot == null || diamondShield == null || crossNecklace == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot, spartanshields:shield_basic_diamond, or bountifulbaubles:amuletcross! Skipping parry recipe.");
            return;
        }

        Enchantment parry = PackCompat.findEnchantment("somanyenchantments", "parry");
        if (parry == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:parry! Skipping parry recipe.");
            return;
        }

        ItemStack output = createBook(parry, parry.getMaxLevel());
        registerConfigurableRecipe(event, "parry", output,
                "GDG", "DXD", "GNG",
                'G', glowingIngot,
                'D', diamondShield,
                'N', crossNecklace,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Rune: Piercing Capabilities: glowing gem corners, xp tome in the
    // center, a spartanfire elemental dragon-blood warhammer (fire, ice, or
    // lightning - any one is accepted, upgraded from the plain dragonbone
    // warhammer) on all four edge-middle slots.
    // ============================================================
    private static void registerRunePiercingCapabilities(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingGem = resolveGlowingGem();
        Item fireWarhammer = PackCompat.findItem("spartanfire", "warhammer_fire_dragonbone");
        Item iceWarhammer = PackCompat.findItem("spartanfire", "warhammer_ice_dragonbone");
        // Optional - absent in base RLCraft, see presentOnly().
        Item lightningWarhammer = PackCompat.findItem("spartanfire", "warhammer_lightning_dragonbone");
        if (glowingGem == null || fireWarhammer == null || iceWarhammer == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem or one of the spartanfire elemental dragonbone warhammers! Skipping rune_piercing_capabilities recipe.");
            return;
        }

        Enchantment runePiercingCapabilities = PackCompat.findEnchantment("somanyenchantments", "rune_piercingcapabilities");
        if (runePiercingCapabilities == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:rune_piercingcapabilities! Skipping rune_piercing_capabilities recipe.");
            return;
        }

        SpecialIngredient elementalWarhammer = new AnyOfItemsIngredient(
                presentOnly(fireWarhammer, iceWarhammer, lightningWarhammer));

        ItemStack output = createBook(runePiercingCapabilities, runePiercingCapabilities.getMaxLevel());
        registerConfigurableRecipe(event, "rune_piercing_capabilities", output,
                "GWG", "WXW", "GWG",
                'G', glowingGem,
                'W', elementalWarhammer,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Natural Blocking II: glowing gem corners, xp tome in the center, a
    // spartanshields diamond shield on the top/bottom middle, and a
    // spartanfire dragonbone saber on the left/right middle.
    // ============================================================
    private static void registerNaturalBlockingII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingGem = resolveGlowingGem();
        Item diamondShield = PackCompat.findItem("spartanshields", "shield_basic_diamond");
        Item dragonboneSaber = PackCompat.findItem("spartanfire", "saber_dragonbone");
        if (glowingGem == null || diamondShield == null || dragonboneSaber == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem, spartanshields:shield_basic_diamond, or spartanfire:saber_dragonbone! Skipping natural_blocking_ii recipe.");
            return;
        }

        Enchantment naturalBlocking = PackCompat.findEnchantment("somanyenchantments", "naturalblocking");
        if (naturalBlocking == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:naturalblocking! Skipping natural_blocking_ii recipe.");
            return;
        }

        ItemStack output = createBook(naturalBlocking, naturalBlocking.getMaxLevel());
        registerConfigurableRecipe(event, "natural_blocking_ii", output,
                "GDG", "SXS", "GDG",
                'G', glowingGem,
                'D', diamondShield,
                'S', dragonboneSaber,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Rune: Arrow Piercing IV: glowing gem corners, xp tome in the center,
    // a spartanfire elemental dragon-blood longbow (fire, ice, or
    // lightning - any one is accepted, upgraded from the plain dragonbone
    // longbow) on all four edge-middle slots.
    // ============================================================
    private static void registerRuneArrowPiercing(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingGem = resolveGlowingGem();
        Item fireLongbow = PackCompat.findItem("spartanfire", "longbow_fire_dragonbone");
        Item iceLongbow = PackCompat.findItem("spartanfire", "longbow_ice_dragonbone");
        // Optional - absent in base RLCraft, see presentOnly().
        Item lightningLongbow = PackCompat.findItem("spartanfire", "longbow_lightning_dragonbone");
        if (glowingGem == null || fireLongbow == null || iceLongbow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem or one of the spartanfire elemental dragonbone longbows! Skipping rune_arrow_piercing recipe.");
            return;
        }

        Enchantment runeArrowPiercing = PackCompat.findEnchantment("somanyenchantments", "rune_arrowpiercing");
        if (runeArrowPiercing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:rune_arrowpiercing! Skipping rune_arrow_piercing recipe.");
            return;
        }

        SpecialIngredient elementalLongbow = new AnyOfItemsIngredient(
                presentOnly(fireLongbow, iceLongbow, lightningLongbow));

        ItemStack output = createBook(runeArrowPiercing, runeArrowPiercing.getMaxLevel());
        registerConfigurableRecipe(event, "rune_arrow_piercing", output,
                "GLG", "LXL", "GLG",
                'G', glowingGem,
                'L', elementalLongbow,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Arc Slash III: glowing gem corners, a Charm "Coffee" potion (NBT tag
    // "Potion" = "charm:coffee" - confirmed via in-game NBT tooltip, not
    // Roguelike Dungeons as previously assumed) on the top/left/right
    // middle, a potionfingers Ring of Haste on the bottom-middle, center
    // consumes a Sweeping Edge III book.
    // ============================================================
    private static void registerArcSlashIII(RegistryEvent.Register<IRecipe> event) {
        Item glowingGem = resolveGlowingGem();
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        if (glowingGem == null || potionRing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem or potionfingers:ring! Skipping arc_slash_iii recipe.");
            return;
        }

        Enchantment arcSlash = PackCompat.findEnchantment("somanyenchantments", "arcslash");
        if (arcSlash == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:arcslash! Skipping arc_slash_iii recipe.");
            return;
        }

        SpecialIngredient coffeePotion = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "charm:coffee");
        SpecialIngredient ringOfHaste = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:haste");

        ItemStack output = createBook(arcSlash, arcSlash.getMaxLevel());
        registerConfigurableRecipe(event, "arc_slash_iii", output,
                "GCG", "CXC", "GRG",
                'G', glowingGem,
                'C', coffeePotion,
                'R', ringOfHaste,
                'X', new EnchantedBookIngredient(Enchantments.SWEEPING, 3));
    }

    // ============================================================
    // Envenomed III: glowing ingot corners, an iceandfire Hydra Fang on
    // the left/right middle, a wither skeleton skull on the bottom-middle,
    // a xat Poison Stone on the top-middle (leaning into the poison
    // theme), xp tome in the center. Produces somanyenchantments:envenomed.
    // ============================================================
    private static void registerEnvenomedIII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item hydraFang = PackCompat.findItem("iceandfire", "hydra_fang");
        Item poisonStone = PackCompat.findItem("xat", "poison_stone");
        if (glowingIngot == null || hydraFang == null || poisonStone == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot, iceandfire:hydra_fang, or xat:poison_stone! Skipping envenomed_iii recipe.");
            return;
        }

        Enchantment envenomed = PackCompat.findEnchantment("somanyenchantments", "envenomed");
        if (envenomed == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:envenomed! Skipping envenomed_iii recipe.");
            return;
        }

        ItemStack witherSkull = new ItemStack(Items.SKULL, 1, 1);

        registerConfigurableRecipe(event, "envenomed_iii", createBook(envenomed, envenomed.getMaxLevel()),
                "GPG", "BXB", "GWG",
                'G', glowingIngot,
                'P', poisonStone,
                'W', witherSkull,
                'B', hydraFang,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Viper V: same layout as envenomed_iii, but with the edge-middle
    // trinket and wither skeleton skull spots swapped - wither skulls on
    // the left/right middle, an iceandfire Hydra Heart on the
    // bottom-middle. Produces somanyenchantments:viper.
    // ============================================================
    private static void registerViperV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Object hydraHeart = resolveHydraHeartSlot();
        Item poisonStone = PackCompat.findItem("xat", "poison_stone");
        if (glowingIngot == null || hydraHeart == null || poisonStone == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot, iceandfire:hydra_heart, or xat:poison_stone! Skipping viper_v recipe.");
            return;
        }

        Enchantment viper = PackCompat.findEnchantment("somanyenchantments", "viper");
        if (viper == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:viper! Skipping viper_v recipe.");
            return;
        }

        ItemStack witherSkull = new ItemStack(Items.SKULL, 1, 1);

        registerConfigurableRecipe(event, "viper_v", createBook(viper, viper.getMaxLevel()),
                "GPG", "WXW", "GBG",
                'G', glowingIngot,
                'P', poisonStone,
                'B', hydraHeart,
                'W', witherSkull,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Ash Destroyer V: glowing ingot corners, xp tome in the center,
    // iceandfire's Ash block on all four edge-middle slots.
    // ============================================================
    private static void registerAshDestroyerV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item ash = PackCompat.findItem("iceandfire", "ash");
        if (glowingIngot == null || ash == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or iceandfire:ash! Skipping ash_destroyer_v recipe.");
            return;
        }

        Enchantment ashDestroyer = PackCompat.findEnchantment("somanyenchantments", "ashdestroyer");
        if (ashDestroyer == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:ashdestroyer! Skipping ash_destroyer_v recipe.");
            return;
        }

        ItemStack output = createBook(ashDestroyer, ashDestroyer.getMaxLevel());
        registerConfigurableRecipe(event, "ash_destroyer_v", output,
                "GAG", "AXA", "GAG",
                'G', glowingIngot,
                'A', ash,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Combo III: glowing ingot corners, xp tome in the center, a
    // potionfingers Ring of Haste on the top/bottom middle, and a Potion
    // of Haste II on the left/right middle. Produces
    // mujmajnkraftsbettersurvival:combo (not somanyenchantments, unlike
    // most of the others).
    // ============================================================
    private static void registerComboIII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        if (glowingIngot == null || potionRing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or potionfingers:ring! Skipping combo_iii recipe.");
            return;
        }

        Enchantment combo = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "combo");
        if (combo == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:combo! Skipping combo_iii recipe.");
            return;
        }

        SpecialIngredient ringOfHaste = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:haste");
        SpecialIngredient hasteII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "quark:haste");

        ItemStack output = createBook(combo, combo.getMaxLevel());
        registerConfigurableRecipe(event, "combo_iii", output,
                "GRG", "PXP", "GRG",
                'G', glowingIngot,
                'R', ringOfHaste,
                'P', hasteII,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Purging Blade V: glowing ingot corners, xp tome in the center, a
    // Lycanites Mobs Cleansing Crystal on all four edge-middle slots.
    // ============================================================
    private static void registerPurgingBladeV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item cleansingCrystal = PackCompat.findItem("lycanitesmobs", "cleansingcrystal");
        if (glowingIngot == null || cleansingCrystal == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or lycanitesmobs:cleansingcrystal! Skipping purging_blade_v recipe.");
            return;
        }

        Enchantment purgingBlade = PackCompat.findEnchantment("somanyenchantments", "purgingblade");
        if (purgingBlade == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:purgingblade! Skipping purging_blade_v recipe.");
            return;
        }

        ItemStack output = createBook(purgingBlade, purgingBlade.getMaxLevel());
        registerConfigurableRecipe(event, "purging_blade_v", output,
                "GCG", "CXC", "GCG",
                'G', glowingIngot,
                'C', cleansingCrystal,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Advanced Fire Aspect II: glowing ingot corners, blaze rods on all
    // four edge-middle slots, xp tome in the center.
    // ============================================================
    private static void registerAdvancedFireAspectII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping advanced_fire_aspect_ii recipe.");
            return;
        }

        Enchantment advancedFireAspect = PackCompat.findEnchantment("somanyenchantments", "advancedfireaspect");
        if (advancedFireAspect == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedfireaspect! Skipping advanced_fire_aspect_ii recipe.");
            return;
        }

        ItemStack output = createBook(advancedFireAspect, advancedFireAspect.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_fire_aspect_ii", output,
                "GBG", "BXB", "GBG",
                'G', glowingIngot,
                'B', Items.BLAZE_ROD,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Supreme Fire Aspect II: glowing gem corners, xp tome in the center,
    // quark's blaze lantern on all four edge-middle slots. Produces a
    // treasure-only enchant (somanyenchantments:supremefireaspect) that
    // can't drop from the enchanting table at all normally.
    // ============================================================
    private static void registerSupremeFireAspectII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingGem = resolveGlowingGem();
        Item blazeLantern = PackCompat.findItem("quark", "blaze_lantern");
        if (glowingGem == null || blazeLantern == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem or quark:blaze_lantern! Skipping supreme_fire_aspect_ii recipe.");
            return;
        }

        Enchantment supremeFireAspect = PackCompat.findEnchantment("somanyenchantments", "supremefireaspect");
        if (supremeFireAspect == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:supremefireaspect! Skipping supreme_fire_aspect_ii recipe.");
            return;
        }

        ItemStack output = createBook(supremeFireAspect, supremeFireAspect.getMaxLevel());
        registerConfigurableRecipe(event, "supreme_fire_aspect_ii", output,
                "GQG", "QXQ", "GQG",
                'G', glowingGem,
                'Q', blazeLantern,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Atomic Deconstructor II: glowing ingot corners, xp tome in the
    // center, wither skeleton skulls on the top/left/right middle slots,
    // and a bountifulbaubles Amulet of Sin: Pride on the bottom-middle.
    // ============================================================
    private static void registerAtomicDeconstructorII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item amuletSinPride = PackCompat.findItem("bountifulbaubles", "amuletsinpride");
        if (glowingIngot == null || amuletSinPride == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or bountifulbaubles:amuletsinpride! Skipping atomic_deconstructor_ii recipe.");
            return;
        }

        Enchantment atomicDeconstructor = PackCompat.findEnchantment("somanyenchantments", "atomicdeconstructor");
        if (atomicDeconstructor == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:atomicdeconstructor! Skipping atomic_deconstructor_ii recipe.");
            return;
        }

        ItemStack witherSkull = new ItemStack(Items.SKULL, 1, 1);

        ItemStack output = createBook(atomicDeconstructor, atomicDeconstructor.getMaxLevel());
        registerConfigurableRecipe(event, "atomic_deconstructor_ii", output,
                "GWG", "WXW", "GBG",
                'G', glowingIngot,
                'W', witherSkull,
                'X', new XpTomeIngredient(xpTome),
                'B', amuletSinPride);
    }

    // ============================================================
    // Strengthened Vitality V: glowing gem corners, xp tome in the center,
    // an ender dragon head on the top-middle, a Lifesteal II book on the
    // left-middle, a Vampirism II book on the right-middle, and a
    // potionfingers Ring of Regeneration on the bottom-middle.
    // ============================================================
    private static void registerStrengthenedVitalityV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingGem = resolveGlowingGem();
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        if (glowingGem == null || potionRing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem or potionfingers:ring! Skipping strengthened_vitality_v recipe.");
            return;
        }

        Enchantment strengthenedVitality = PackCompat.findEnchantment("somanyenchantments", "strengthenedvitality");
        if (strengthenedVitality == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:strengthenedvitality! Skipping strengthened_vitality_v recipe.");
            return;
        }

        Enchantment lifesteal = PackCompat.findEnchantment("somanyenchantments", "lifesteal");
        if (lifesteal == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:lifesteal! Skipping strengthened_vitality_v recipe.");
            return;
        }

        Enchantment vampirism = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "vampirism");
        if (vampirism == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:vampirism! Skipping strengthened_vitality_v recipe.");
            return;
        }

        ItemStack enderDragonHead = new ItemStack(Items.SKULL, 1, 5);
        SpecialIngredient ringOfRegeneration = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:regeneration");

        ItemStack output = createBook(strengthenedVitality, strengthenedVitality.getMaxLevel());
        registerConfigurableRecipe(event, "strengthened_vitality_v", output,
                "GEG", "LXV", "GRG",
                'G', glowingGem,
                'E', enderDragonHead,
                'L', new EnchantedBookIngredient(lifesteal, lifesteal.getMaxLevel()),
                'V', new EnchantedBookIngredient(vampirism, vampirism.getMaxLevel()),
                'X', new XpTomeIngredient(xpTome),
                'R', ringOfRegeneration);
    }

    // ============================================================
    // Double Jump: glowing powder corners, xp tome in the center, a Rabbit
    // Stew on the bottom-middle, and a Potion of Leaping II on the
    // top/left/right middle slots.
    // ============================================================
    private static void registerDoubleJump(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingPowder = resolveGlowingPowder();
        if (glowingPowder == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder! Skipping double_jump recipe.");
            return;
        }

        Enchantment doubleJump = PackCompat.findEnchantment("grapplemod", "doublejumpenchantment");
        if (doubleJump == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find grapplemod:doublejumpenchantment! Skipping double_jump recipe.");
            return;
        }

        SpecialIngredient leapingII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:strong_leaping");

        ItemStack output = createBook(doubleJump, doubleJump.getMaxLevel());
        registerConfigurableRecipe(event, "double_jump", output,
                "GPG", "PXP", "GSG",
                'G', glowingPowder,
                'P', leapingII,
                'X', new XpTomeIngredient(xpTome),
                'S', Items.RABBIT_STEW);
    }

    // ============================================================
    // Light Weight III: glowing powder corners, xp tome in the center, an
    // iceandfire Stymphalian Bird Feather or Amphithere Feather (either is
    // accepted) on all four edge-middle slots.
    // ============================================================
    private static void registerLightWeightIII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingPowder = resolveGlowingPowder();
        Item stymphalianFeather = PackCompat.findItem("iceandfire", "stymphalian_bird_feather");
        Item amphithereFeather = PackCompat.findItem("iceandfire", "amphithere_feather");
        if (glowingPowder == null || stymphalianFeather == null || amphithereFeather == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder, iceandfire:stymphalian_bird_feather, or iceandfire:amphithere_feather! Skipping light_weight_iii recipe.");
            return;
        }

        Enchantment lightWeight = PackCompat.findEnchantment("somanyenchantments", "lightweight");
        if (lightWeight == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:lightweight! Skipping light_weight_iii recipe.");
            return;
        }

        SpecialIngredient feather = new AnyOfItemsIngredient(stymphalianFeather, amphithereFeather);

        ItemStack output = createBook(lightWeight, lightWeight.getMaxLevel());
        registerConfigurableRecipe(event, "light_weight_iii", output,
                "GFG", "FXF", "GFG",
                'G', glowingPowder,
                'F', feather,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Wall Running: glowing powder corners, xp tome in the center, slime
    // blocks on all four edge-middle slots.
    // ============================================================
    private static void registerWallRunning(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingPowder = resolveGlowingPowder();
        if (glowingPowder == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder! Skipping wall_running recipe.");
            return;
        }

        Enchantment wallRunning = PackCompat.findEnchantment("grapplemod", "wallrunenchantment");
        if (wallRunning == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find grapplemod:wallrunenchantment! Skipping wall_running recipe.");
            return;
        }

        Item slimeBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.SLIME_BLOCK);

        ItemStack output = createBook(wallRunning, wallRunning.getMaxLevel());
        registerConfigurableRecipe(event, "wall_running", output,
                "GSG", "SXS", "GSG",
                'G', glowingPowder,
                'S', slimeBlock,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Sliding: same layout as wall_running, but packed ice instead of
    // slime blocks on the four edge-middle slots.
    // ============================================================
    private static void registerSliding(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingPowder = resolveGlowingPowder();
        if (glowingPowder == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder! Skipping sliding recipe.");
            return;
        }

        Enchantment sliding = PackCompat.findEnchantment("grapplemod", "slidingenchantment");
        if (sliding == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find grapplemod:slidingenchantment! Skipping sliding recipe.");
            return;
        }

        Item packedIce = Item.getItemFromBlock(net.minecraft.init.Blocks.PACKED_ICE);

        ItemStack output = createBook(sliding, sliding.getMaxLevel());
        registerConfigurableRecipe(event, "sliding", output,
                "GPG", "PXP", "GPG",
                'G', glowingPowder,
                'P', packedIce,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Curse of Possession: glowing ingot corners, xp tome in the center, a
    // defiledlands Ravaging Ingot on the left/right middle, and a
    // defiledlands Essence Mourner on the top/bottom middle. Unlike the
    // rest of the mod's curses, this one is deliberately craftable - its
    // RLCraft perk (immune to being dropped, including on death) makes it
    // genuinely desirable.
    // ============================================================
    private static void registerCurseOfPossession(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item ravagingIngot = PackCompat.findItem("defiledlands", "ravaging_ingot");
        Item essenceMourner = PackCompat.findItem("defiledlands", "essence_mourner");
        if (glowingIngot == null || ravagingIngot == null || essenceMourner == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot, defiledlands:ravaging_ingot, or defiledlands:essence_mourner! Skipping curse_of_possession recipe.");
            return;
        }

        Enchantment curseOfPossession = PackCompat.findEnchantment("somanyenchantments", "curseofpossession");
        if (curseOfPossession == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:curseofpossession! Skipping curse_of_possession recipe.");
            return;
        }

        ItemStack output = createBook(curseOfPossession, curseOfPossession.getMaxLevel());
        registerConfigurableRecipe(event, "curse_of_possession", output,
                "GEG", "RXR", "GEG",
                'G', glowingIngot,
                'E', essenceMourner,
                'R', ravagingIngot,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Critical Strike: glowing powder corners, xp tome in the center,
    // diamond swords on the top/left/right middle, and a potionfingers
    // Ring of Strength on the bottom-middle.
    // ============================================================
    private static void registerCriticalStrike(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingPowder = resolveGlowingPowder();
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        if (glowingPowder == null || potionRing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder or potionfingers:ring! Skipping critical_strike recipe.");
            return;
        }

        Enchantment criticalStrike = PackCompat.findEnchantment("somanyenchantments", "criticalstrike");
        if (criticalStrike == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:criticalstrike! Skipping critical_strike recipe.");
            return;
        }

        SpecialIngredient ringOfStrength = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:strength");

        ItemStack output = createBook(criticalStrike, criticalStrike.getMaxLevel());
        registerConfigurableRecipe(event, "critical_strike", output,
                "GSG", "SXS", "GRG",
                'G', glowingPowder,
                'S', Items.DIAMOND_SWORD,
                'X', new XpTomeIngredient(xpTome),
                'R', ringOfStrength);
    }

    // ============================================================
    // Ancient Sword Mastery: glowing gem corners, xp tome in the center, a
    // bountifulbaubles Magic Mirror on the left/right middle, a potioncore
    // Potion of Guarding II (Iron Skin II) on the top-middle, and a
    // potionfingers Ring of Strength on the bottom-middle.
    // ============================================================
    private static void registerAncientSwordMasteryIII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingGem = resolveGlowingGem();
        Item magicMirror = PackCompat.findItem("bountifulbaubles", "magicmirror");
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        if (glowingGem == null || magicMirror == null || potionRing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem, bountifulbaubles:magicmirror, or potionfingers:ring! Skipping ancient_sword_mastery_iii recipe.");
            return;
        }

        Enchantment ancientSwordMastery = PackCompat.findEnchantment("somanyenchantments", "ancientswordmastery");
        if (ancientSwordMastery == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:ancientswordmastery! Skipping ancient_sword_mastery_iii recipe.");
            return;
        }

        SpecialIngredient ironSkinII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "potioncore:strong_iron_skin");
        SpecialIngredient ringOfStrength = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:strength");

        ItemStack output = createBook(ancientSwordMastery, ancientSwordMastery.getMaxLevel());
        registerConfigurableRecipe(event, "ancient_sword_mastery_iii", output,
                "GIG", "MXM", "GRG",
                'G', glowingGem,
                'I', ironSkinII,
                'M', magicMirror,
                'X', new XpTomeIngredient(xpTome),
                'R', ringOfStrength);
    }

    // ============================================================
    // Blessed Edge: glowing ingot corners, xp tome in the center, a
    // scalinghealth Heart Crystal Shard on the left/right middle, and an
    // undead mob head (skeleton/wither skeleton/zombie only, via
    // UndeadHeadIngredient) on the top/bottom middle - fitting, since
    // Blessed Edge deals amplified damage to (and heals off of) the undead.
    // ============================================================
    private static void registerBlessedEdgeV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item crystalShard = PackCompat.findItem("scalinghealth", "crystalshard");
        if (glowingIngot == null || crystalShard == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or scalinghealth:crystalshard! Skipping blessed_edge_v recipe.");
            return;
        }

        Enchantment blessedEdge = PackCompat.findEnchantment("somanyenchantments", "blessededge");
        if (blessedEdge == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:blessededge! Skipping blessed_edge_v recipe.");
            return;
        }

        ItemStack output = createBook(blessedEdge, blessedEdge.getMaxLevel());
        registerConfigurableRecipe(event, "blessed_edge_v", output,
                "GHG", "CXC", "GHG",
                'G', glowingIngot,
                'H', new UndeadHeadIngredient(),
                'C', crystalShard,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Brutality V: glowing ingot corners, xp tome in the center, a
    // spartanweaponry Diamond Warhammer on all four edge-middle slots.
    // ============================================================
    private static void registerBrutalityV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item diamondWarhammer = PackCompat.findItem("spartanweaponry", "warhammer_diamond");
        if (glowingIngot == null || diamondWarhammer == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or spartanweaponry:warhammer_diamond! Skipping brutality_v recipe.");
            return;
        }

        Enchantment brutality = PackCompat.findEnchantment("somanyenchantments", "brutality");
        if (brutality == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:brutality! Skipping brutality_v recipe.");
            return;
        }

        ItemStack output = createBook(brutality, brutality.getMaxLevel());
        registerConfigurableRecipe(event, "brutality_v", output,
                "GWG", "WXW", "GWG",
                'G', glowingIngot,
                'W', diamondWarhammer,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Burning Shield: glowing ingot corners, xp tome in the center, a
    // Potion of Fire Resistance (8-minute, extended-duration variant) on
    // the top/bottom middle, and a spartanshields Bulky Obsidian Shield on
    // the left/right middle.
    // ============================================================
    private static void registerBurningShieldIV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item obsidianShield = PackCompat.findItem("spartanshields", "shield_basic_obsidian");
        if (glowingIngot == null || obsidianShield == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or spartanshields:shield_basic_obsidian! Skipping burning_shield_iv recipe.");
            return;
        }

        Enchantment burningShield = PackCompat.findEnchantment("somanyenchantments", "burningshield");
        if (burningShield == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:burningshield! Skipping burning_shield_iv recipe.");
            return;
        }

        SpecialIngredient fireResistance8Min = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:long_fire_resistance");

        ItemStack output = createBook(burningShield, burningShield.getMaxLevel());
        registerConfigurableRecipe(event, "burning_shield_iv", output,
                "GFG", "SXS", "GFG",
                'G', glowingIngot,
                'F', fireResistance8Min,
                'S', obsidianShield,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Burning Thorns: glowing ingot corners, blaze rods on the left/right
    // middle, fire charges on the top/bottom middle, center consumes a
    // Thorns III book.
    // ============================================================
    private static void registerBurningThornsIII(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping burning_thorns_iii recipe.");
            return;
        }

        Enchantment burningThorns = PackCompat.findEnchantment("somanyenchantments", "burningthorns");
        if (burningThorns == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:burningthorns! Skipping burning_thorns_iii recipe.");
            return;
        }

        ItemStack output = createBook(burningThorns, burningThorns.getMaxLevel());
        registerConfigurableRecipe(event, "burning_thorns_iii", output,
                "GFG", "BXB", "GFG",
                'G', glowingIngot,
                'F', Items.FIRE_CHARGE,
                'B', Items.BLAZE_ROD,
                'X', new EnchantedBookIngredient(Enchantments.THORNS, 3));
    }

    // ============================================================
    // Butchering V: glowing ingot corners, raw beef on the top-middle, raw
    // chicken on the right-middle, raw porkchop on the bottom-middle, raw
    // mutton on the left-middle, center consumes a Looting III book.
    // ============================================================
    private static void registerButcheringV(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping butchering_v recipe.");
            return;
        }

        Enchantment butchering = PackCompat.findEnchantment("somanyenchantments", "butchering");
        if (butchering == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:butchering! Skipping butchering_v recipe.");
            return;
        }

        ItemStack output = createBook(butchering, butchering.getMaxLevel());
        registerConfigurableRecipe(event, "butchering_v", output,
                "GBG", "MXC", "GPG",
                'G', glowingIngot,
                'B', Items.BEEF,
                'C', Items.CHICKEN,
                'P', Items.PORKCHOP,
                'M', Items.MUTTON,
                'X', new EnchantedBookIngredient(Enchantments.LOOTING, 3));
    }

    // ============================================================
    // Combat Medic: glowing ingot corners, xp tome in the center, a
    // scalinghealth Heart Dust on the top-middle, a scalinghealth Heart
    // Crystal Shard on the left/right middle, and a roughtweaks Medikit on
    // the bottom-middle.
    // ============================================================
    private static void registerCombatMedicIII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item heartDust = PackCompat.findItem("scalinghealth", "heartdust");
        Item crystalShard = PackCompat.findItem("scalinghealth", "crystalshard");
        Item medikit = PackCompat.findItem("roughtweaks", "medikit");
        if (glowingIngot == null || heartDust == null || crystalShard == null || medikit == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot, scalinghealth:heartdust, scalinghealth:crystalshard, or roughtweaks:medikit! Skipping combat_medic_iii recipe.");
            return;
        }

        Enchantment combatMedic = PackCompat.findEnchantment("somanyenchantments", "combatmedic");
        if (combatMedic == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:combatmedic! Skipping combat_medic_iii recipe.");
            return;
        }

        ItemStack output = createBook(combatMedic, combatMedic.getMaxLevel());
        registerConfigurableRecipe(event, "combat_medic_iii", output,
                "GDG", "CXC", "GKG",
                'G', glowingIngot,
                'D', heartDust,
                'C', crystalShard,
                'K', medikit,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Counter Attack: glowing ingot corners, xp tome in the center, a
    // vanilla shield on the left-middle, a diamond helmet on the
    // top-middle, a diamond sword on the right-middle, and a potioncore
    // Potion of Diamond Skin II on the bottom-middle.
    // ============================================================
    private static void registerCounterAttackIII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping counter_attack_iii recipe.");
            return;
        }

        Enchantment counterAttack = PackCompat.findEnchantment("somanyenchantments", "counterattack");
        if (counterAttack == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:counterattack! Skipping counter_attack_iii recipe.");
            return;
        }

        SpecialIngredient diamondSkinII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "potioncore:strong_diamond_skin");

        ItemStack output = createBook(counterAttack, counterAttack.getMaxLevel());
        registerConfigurableRecipe(event, "counter_attack_iii", output,
                "GHG", "LXR", "GPG",
                'G', glowingIngot,
                'H', Items.DIAMOND_HELMET,
                'L', Items.SHIELD,
                'R', Items.DIAMOND_SWORD,
                'P', diamondSkinII,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Culling: glowing ingot corners, xp tome in the center, any vanilla
    // mob head on the top-middle (fitting, since Culling drops the slain
    // enemy's head), redstone blocks on the left/right middle, and a
    // diamond axe on the bottom-middle.
    // ============================================================
    private static void registerCullingIII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping culling_iii recipe.");
            return;
        }

        Enchantment culling = PackCompat.findEnchantment("somanyenchantments", "culling");
        if (culling == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:culling! Skipping culling_iii recipe.");
            return;
        }

        Item redstoneBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.REDSTONE_BLOCK);

        ItemStack output = createBook(culling, culling.getMaxLevel());
        registerConfigurableRecipe(event, "culling_iii", output,
                "GHG", "RXR", "GAG",
                'G', glowingIngot,
                'H', new AnyHeadIngredient(),
                'R', redstoneBlock,
                'A', Items.DIAMOND_AXE,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Cryogenic: glowing ingot corners, xp tome in the center, a
    // lycanitesmobs Icefireball Charge on the top/left/right middle, and a
    // xat Potion of Ice Resistance (extended, 8-minute variant) on the
    // bottom-middle.
    // ============================================================
    private static void registerCryogenicIII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item icefireballCharge = PackCompat.findItem("lycanitesmobs", "icefireballcharge");
        if (glowingIngot == null || icefireballCharge == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or lycanitesmobs:icefireballcharge! Skipping cryogenic_iii recipe.");
            return;
        }

        Enchantment cryogenic = PackCompat.findEnchantment("somanyenchantments", "cryogenic");
        if (cryogenic == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:cryogenic! Skipping cryogenic_iii recipe.");
            return;
        }

        SpecialIngredient iceResistance8Min = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "xat:extended_ice_resistance");

        ItemStack output = createBook(cryogenic, cryogenic.getMaxLevel());
        registerConfigurableRecipe(event, "cryogenic_iii", output,
                "GFG", "FXF", "GPG",
                'G', glowingIngot,
                'F', icefireballCharge,
                'P', iceResistance8Min,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Dark Shadows: glowing ingot corners, xp tome in the center, a
    // bountifulbaubles Sunglasses on the top-middle, a Potion of Strength
    // II on the left/right middle, and an iceandfire Blindfold on the
    // bottom-middle.
    // ============================================================
    private static void registerDarkShadowsIII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item sunglasses = PackCompat.findItem("bountifulbaubles", "trinketmagiclenses");
        Item blindfold = PackCompat.findItem("iceandfire", "blindfold");
        if (glowingIngot == null || sunglasses == null || blindfold == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot, bountifulbaubles:trinketmagiclenses, or iceandfire:blindfold! Skipping dark_shadows_iii recipe.");
            return;
        }

        Enchantment darkShadows = PackCompat.findEnchantment("somanyenchantments", "darkshadows");
        if (darkShadows == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:darkshadows! Skipping dark_shadows_iii recipe.");
            return;
        }

        SpecialIngredient strengthII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:strong_strength");

        ItemStack output = createBook(darkShadows, darkShadows.getMaxLevel());
        registerConfigurableRecipe(event, "dark_shadows_iii", output,
                "GSG", "PXP", "GBG",
                'G', glowingIngot,
                'S', sunglasses,
                'P', strengthII,
                'B', blindfold,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Defusing Edge: glowing ingot corners, xp tome in the center, creeper
    // heads on all four edge-middle slots - fitting, since Defusing Edge
    // defuses and deals amplified damage to creepers.
    // ============================================================
    private static void registerDefusingEdgeV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping defusing_edge_v recipe.");
            return;
        }

        Enchantment defusingEdge = PackCompat.findEnchantment("somanyenchantments", "defusingedge");
        if (defusingEdge == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:defusingedge! Skipping defusing_edge_v recipe.");
            return;
        }

        ItemStack creeperHead = new ItemStack(Items.SKULL, 1, 4);

        ItemStack output = createBook(defusingEdge, defusingEdge.getMaxLevel());
        registerConfigurableRecipe(event, "defusing_edge_v", output,
                "GCG", "CXC", "GCG",
                'G', glowingIngot,
                'C', creeperHead,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Desolator: glowing ingot corners, xp tome in the center, a long
    // Splash Potion of Weakness on the top-middle, a long Splash Potion of
    // Slowness on the left-middle, a long Splash Potion of Poison on the
    // right-middle, and a long Splash Potion of Decay (potioncore's
    // wither effect) on the bottom-middle.
    // ============================================================
    private static void registerDesolatorIV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping desolator_iv recipe.");
            return;
        }

        Enchantment desolator = PackCompat.findEnchantment("somanyenchantments", "desolator");
        if (desolator == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:desolator! Skipping desolator_iv recipe.");
            return;
        }

        SpecialIngredient weaknessSplash = new NbtStringTagIngredient(Items.SPLASH_POTION, "Potion", "minecraft:long_weakness");
        SpecialIngredient slownessSplash = new NbtStringTagIngredient(Items.SPLASH_POTION, "Potion", "minecraft:long_slowness");
        SpecialIngredient poisonSplash = new NbtStringTagIngredient(Items.SPLASH_POTION, "Potion", "minecraft:long_poison");
        SpecialIngredient decaySplash = new NbtStringTagIngredient(Items.SPLASH_POTION, "Potion", "potioncore:long_wither");

        ItemStack output = createBook(desolator, desolator.getMaxLevel());
        registerConfigurableRecipe(event, "desolator_iv", output,
                "GWG", "SXP", "GDG",
                'G', glowingIngot,
                'W', weaknessSplash,
                'S', slownessSplash,
                'P', poisonSplash,
                'D', decaySplash,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Disarmament V: glowing ingot corners, xp tome in the center, cobwebs
    // on the top/bottom middle, mujmajnkraftsbettersurvival Diamond Battle
    // Axes on the left/right middle.
    // ============================================================
    private static void registerDisarmamentV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item diamondBattleAxe = PackCompat.findItem("mujmajnkraftsbettersurvival", "itemdiamondbattleaxe");
        if (glowingIngot == null || diamondBattleAxe == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or mujmajnkraftsbettersurvival:itemdiamondbattleaxe! Skipping disarmament_v recipe.");
            return;
        }

        Enchantment disarmament = PackCompat.findEnchantment("somanyenchantments", "disarmament");
        if (disarmament == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:disarmament! Skipping disarmament_v recipe.");
            return;
        }

        Item cobweb = Item.getItemFromBlock(net.minecraft.init.Blocks.WEB);

        ItemStack output = createBook(disarmament, disarmament.getMaxLevel());
        registerConfigurableRecipe(event, "disarmament_v", output,
                "GWG", "AXA", "GWG",
                'G', glowingIngot,
                'W', cobweb,
                'A', diamondBattleAxe,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Disarm: mujmajnkraftsbettersurvival's own take on disarmament. Same
    // layout/materials as disarmament_v, but with the cobweb and Diamond
    // Battle Axe spots swapped - Diamond Battle Axes on the top/bottom
    // middle, cobwebs on the left/right middle.
    // ============================================================
    private static void registerDisarmIII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item diamondBattleAxe = PackCompat.findItem("mujmajnkraftsbettersurvival", "itemdiamondbattleaxe");
        if (glowingIngot == null || diamondBattleAxe == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or mujmajnkraftsbettersurvival:itemdiamondbattleaxe! Skipping disarm_iii recipe.");
            return;
        }

        Enchantment disarm = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "disarm");
        if (disarm == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:disarm! Skipping disarm_iii recipe.");
            return;
        }

        Item cobweb = Item.getItemFromBlock(net.minecraft.init.Blocks.WEB);

        ItemStack output = createBook(disarm, disarm.getMaxLevel());
        registerConfigurableRecipe(event, "disarm_iii", output,
                "GAG", "WXW", "GAG",
                'G', glowingIngot,
                'A', diamondBattleAxe,
                'W', cobweb,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Disorientating Blade: glowing ingot corners, xp tome in the center, a
    // bountifulbaubles Sunglasses on the bottom-middle, and a long Splash
    // Potion of Blindness (mujmajnkraftsbettersurvival's variant) on the
    // top/left/right middle.
    // ============================================================
    private static void registerDisorientatingBladeIV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item sunglasses = PackCompat.findItem("bountifulbaubles", "trinketmagiclenses");
        if (glowingIngot == null || sunglasses == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or bountifulbaubles:trinketmagiclenses! Skipping disorientating_blade_iv recipe.");
            return;
        }

        Enchantment disorientatingBlade = PackCompat.findEnchantment("somanyenchantments", "disorientatingblade");
        if (disorientatingBlade == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:disorientatingblade! Skipping disorientating_blade_iv recipe.");
            return;
        }

        SpecialIngredient blindnessSplash = new NbtStringTagIngredient(Items.SPLASH_POTION, "Potion", "mujmajnkraftsbettersurvival:long_blindness");

        ItemStack output = createBook(disorientatingBlade, disorientatingBlade.getMaxLevel());
        registerConfigurableRecipe(event, "disorientating_blade_iv", output,
                "GBG", "BXB", "GSG",
                'G', glowingIngot,
                'B', blindnessSplash,
                'S', sunglasses,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Empowered Defence: glowing ingot corners, xp tome in the center, a
    // potioncore Potion of Diamond Skin II on the top/bottom middle, a
    // spartanshields Diamond Reinforced Shield on the left/right middle.
    // ============================================================
    private static void registerEmpoweredDefenceII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item diamondShield = PackCompat.findItem("spartanshields", "shield_basic_diamond");
        if (glowingIngot == null || diamondShield == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or spartanshields:shield_basic_diamond! Skipping empowered_defence_ii recipe.");
            return;
        }

        Enchantment empoweredDefence = PackCompat.findEnchantment("somanyenchantments", "empowereddefence");
        if (empoweredDefence == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:empowereddefence! Skipping empowered_defence_ii recipe.");
            return;
        }

        SpecialIngredient diamondSkinII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "potioncore:strong_diamond_skin");

        ItemStack output = createBook(empoweredDefence, empoweredDefence.getMaxLevel());
        registerConfigurableRecipe(event, "empowered_defence_ii", output,
                "GDG", "SXS", "GDG",
                'G', glowingIngot,
                'D', diamondSkinII,
                'S', diamondShield,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Evasion: glowing ingot corners, xp tome in the center, an iceandfire
    // Blindfold on the top-middle, a long Potion of Invisibility on the
    // left/right/bottom middle.
    // ============================================================
    private static void registerEvasionI(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item blindfold = PackCompat.findItem("iceandfire", "blindfold");
        if (glowingIngot == null || blindfold == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or iceandfire:blindfold! Skipping evasion_i recipe.");
            return;
        }

        Enchantment evasion = PackCompat.findEnchantment("somanyenchantments", "evasion");
        if (evasion == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:evasion! Skipping evasion_i recipe.");
            return;
        }

        SpecialIngredient invisibilityLong = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:long_invisibility");

        ItemStack output = createBook(evasion, evasion.getMaxLevel());
        registerConfigurableRecipe(event, "evasion_i", output,
                "GTG", "IXI", "GIG",
                'G', glowingIngot,
                'T', blindfold,
                'I', invisibilityLong,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Flinging: glowing ingot corners, sticky pistons on the top/bottom
    // middle, pistons on the left/right middle, center consumes a
    // Knockback II book.
    // ============================================================
    private static void registerFlingingII(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping flinging_ii recipe.");
            return;
        }

        Enchantment flinging = PackCompat.findEnchantment("somanyenchantments", "flinging");
        if (flinging == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:flinging! Skipping flinging_ii recipe.");
            return;
        }

        Item piston = Item.getItemFromBlock(net.minecraft.init.Blocks.PISTON);
        Item stickyPiston = Item.getItemFromBlock(net.minecraft.init.Blocks.STICKY_PISTON);

        ItemStack output = createBook(flinging, flinging.getMaxLevel());
        registerConfigurableRecipe(event, "flinging_ii", output,
                "GSG", "PXP", "GSG",
                'G', glowingIngot,
                'S', stickyPiston,
                'P', piston,
                'X', new EnchantedBookIngredient(Enchantments.KNOCKBACK, 2));
    }

    // ============================================================
    // Fling: mujmajnkraftsbettersurvival's own take on flinging. Same
    // layout/materials as flinging_ii, but with the piston and sticky
    // piston spots swapped - pistons on the top/bottom middle, sticky
    // pistons on the left/right middle.
    // ============================================================
    private static void registerFlingII(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping fling_ii recipe.");
            return;
        }

        Enchantment fling = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "fling");
        if (fling == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:fling! Skipping fling_ii recipe.");
            return;
        }

        Item piston = Item.getItemFromBlock(net.minecraft.init.Blocks.PISTON);
        Item stickyPiston = Item.getItemFromBlock(net.minecraft.init.Blocks.STICKY_PISTON);

        ItemStack output = createBook(fling, fling.getMaxLevel());
        registerConfigurableRecipe(event, "fling_ii", output,
                "GPG", "SXS", "GPG",
                'G', glowingIngot,
                'P', piston,
                'S', stickyPiston,
                'X', new EnchantedBookIngredient(Enchantments.KNOCKBACK, 2));
    }

    // ============================================================
    // Hors De Combat: glowing ingot corners, xp tome in the center, a long
    // Splash Potion of Weakness on the top/left/right middle, a
    // lycanitesmobs Immunizer on the bottom-middle.
    // ============================================================
    private static void registerHorsDeCombatIV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item immunizer = PackCompat.findItem("lycanitesmobs", "immunizer");
        if (glowingIngot == null || immunizer == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or lycanitesmobs:immunizer! Skipping hors_de_combat_iv recipe.");
            return;
        }

        Enchantment horsDeCombat = PackCompat.findEnchantment("somanyenchantments", "horsdecombat");
        if (horsDeCombat == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:horsdecombat! Skipping hors_de_combat_iv recipe.");
            return;
        }

        SpecialIngredient weaknessSplash = new NbtStringTagIngredient(Items.SPLASH_POTION, "Potion", "minecraft:long_weakness");

        ItemStack output = createBook(horsDeCombat, horsDeCombat.getMaxLevel());
        registerConfigurableRecipe(event, "hors_de_combat_iv", output,
                "GWG", "WXW", "GIG",
                'G', glowingIngot,
                'W', weaknessSplash,
                'I', immunizer,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Inhumane: glowing ingot corners, xp tome in the center, a
    // qualitytools Emerald Amulet on the top-middle, a qualitytools
    // Emerald Ring on the left/right middle, and a charm Charged Emerald
    // on the bottom-middle.
    // ============================================================
    private static void registerInhumaneV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item emeraldAmulet = PackCompat.findItem("qualitytools", "emerald_amulet");
        Item emeraldRing = PackCompat.findItem("qualitytools", "emerald_ring");
        Item chargedEmerald = PackCompat.findItem("charm", "charged_emerald");
        if (glowingIngot == null || emeraldAmulet == null || emeraldRing == null || chargedEmerald == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot, qualitytools:emerald_amulet, qualitytools:emerald_ring, or charm:charged_emerald! Skipping inhumane_v recipe.");
            return;
        }

        Enchantment inhumane = PackCompat.findEnchantment("somanyenchantments", "inhumane");
        if (inhumane == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:inhumane! Skipping inhumane_v recipe.");
            return;
        }

        ItemStack output = createBook(inhumane, inhumane.getMaxLevel());
        registerConfigurableRecipe(event, "inhumane_v", output,
                "GAG", "RXR", "GCG",
                'G', glowingIngot,
                'A', emeraldAmulet,
                'R', emeraldRing,
                'C', chargedEmerald,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Inner Berserk: glowing ingot corners, xp tome in the center, a
    // scalinghealth Medkit on the top-middle, a roughtweaks Enchanted
    // Medikit on the bottom-middle, and two potionfingers Rings of
    // Strength on the left/right middle.
    //
    // Medkit isn't its own registry entry - it's scalinghealth:healingitem
    // at damage 1 (see registerLifestealII for the same bug/fix).
    // ============================================================
    private static void registerInnerBerserkIV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item healingItem = PackCompat.findItem("scalinghealth", "healingitem");
        Item enchantedMedikit = PackCompat.findItem("roughtweaks", "medikitenchanted");
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        if (glowingIngot == null || healingItem == null || enchantedMedikit == null || potionRing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot, scalinghealth:healingitem, roughtweaks:medikitenchanted, or potionfingers:ring! Skipping inner_berserk_iv recipe.");
            return;
        }

        ItemStack medkit = new ItemStack(healingItem, 1, 1);

        Enchantment innerBerserk = PackCompat.findEnchantment("somanyenchantments", "innerberserk");
        if (innerBerserk == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:innerberserk! Skipping inner_berserk_iv recipe.");
            return;
        }

        SpecialIngredient ringOfStrength = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:strength");

        ItemStack output = createBook(innerBerserk, innerBerserk.getMaxLevel());
        registerConfigurableRecipe(event, "inner_berserk_iv", output,
                "GMG", "RXR", "GEG",
                'G', glowingIngot,
                'M', medkit,
                'E', enchantedMedikit,
                'R', ringOfStrength,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Jagged Rake: glowing powder corners (kept cheap - a niche enchant
    // few players use), xp tome in the center, diamond hoes on all four
    // edge-middle slots - fitting, since Jagged Rake increases damage
    // dealt by hoes.
    // ============================================================
    private static void registerJaggedRakeV(RegistryEvent.Register<IRecipe> event, Item xpTome, Item glowingPowder) {
        Enchantment jaggedRake = PackCompat.findEnchantment("somanyenchantments", "jaggedrake");
        if (jaggedRake == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:jaggedrake! Skipping jagged_rake_v recipe.");
            return;
        }

        ItemStack output = createBook(jaggedRake, jaggedRake.getMaxLevel());
        registerConfigurableRecipe(event, "jagged_rake_v", output,
                "GHG", "HXH", "GHG",
                'G', glowingPowder,
                'H', Items.DIAMOND_HOE,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Levitator: glowing ingot corners, xp tome in the center, a
    // potioncore Potion of Levitation II on the top/left/right middle, and
    // a feather on the bottom-middle.
    // ============================================================
    private static void registerLevitatorII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping levitator_ii recipe.");
            return;
        }

        Enchantment levitator = PackCompat.findEnchantment("somanyenchantments", "levitator");
        if (levitator == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:levitator! Skipping levitator_ii recipe.");
            return;
        }

        SpecialIngredient levitationII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "potioncore:strong_levitation");

        ItemStack output = createBook(levitator, levitator.getMaxLevel());
        registerConfigurableRecipe(event, "levitator_ii", output,
                "GLG", "LXL", "GFG",
                'G', glowingIngot,
                'L', levitationII,
                'F', Items.FEATHER,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Luck Magnification: glowing gem corners, xp tome in the center, a
    // Looting III book on the top-middle, a Fortune III book on the
    // left-middle, a Luck of the Sea III book on the right-middle, and a
    // spartanweaponry Lucky Throw III book on the bottom-middle.
    // ============================================================
    private static void registerLuckMagnificationII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingGem = resolveGlowingGem();
        if (glowingGem == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem! Skipping luck_magnification_ii recipe.");
            return;
        }

        Enchantment luckyThrow = PackCompat.findEnchantment("spartanweaponry", "lucky_throw");
        if (luckyThrow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:lucky_throw! Skipping luck_magnification_ii recipe.");
            return;
        }

        Enchantment luckMagnification = PackCompat.findEnchantment("somanyenchantments", "luckmagnification");
        if (luckMagnification == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:luckmagnification! Skipping luck_magnification_ii recipe.");
            return;
        }

        ItemStack output = createBook(luckMagnification, luckMagnification.getMaxLevel());
        registerConfigurableRecipe(event, "luck_magnification_ii", output,
                "GLG", "FXS", "GTG",
                'G', glowingGem,
                'L', new EnchantedBookIngredient(Enchantments.LOOTING, 3),
                'F', new EnchantedBookIngredient(Enchantments.FORTUNE, 3),
                'S', new EnchantedBookIngredient(Enchantments.LUCK_OF_THE_SEA, 3),
                'T', new EnchantedBookIngredient(luckyThrow, luckyThrow.getMaxLevel()),
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Luna's Blessing: glowing ingot corners, xp tome in the center, a
    // clock on the top/bottom middle, and a xat Mana Candy on the
    // left/right middle.
    // ============================================================
    private static void registerLunasBlessingV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item manaCandy = PackCompat.findItem("xat", "mana_candy");
        if (glowingIngot == null || manaCandy == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or xat:mana_candy! Skipping lunas_blessing_v recipe.");
            return;
        }

        Enchantment lunasBlessing = PackCompat.findEnchantment("somanyenchantments", "lunasblessing");
        if (lunasBlessing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:lunasblessing! Skipping lunas_blessing_v recipe.");
            return;
        }

        ItemStack output = createBook(lunasBlessing, lunasBlessing.getMaxLevel());
        registerConfigurableRecipe(event, "lunas_blessing_v", output,
                "GCG", "MXM", "GCG",
                'G', glowingIngot,
                'C', Items.CLOCK,
                'M', manaCandy,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Magma Walker: glowing ingot corners, xp tome in the center, magma
    // blocks on the top/left/right middle, leather boots on the
    // bottom-middle.
    // ============================================================
    private static void registerMagmaWalkerII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping magma_walker_ii recipe.");
            return;
        }

        Enchantment magmaWalker = PackCompat.findEnchantment("somanyenchantments", "magmawalker");
        if (magmaWalker == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:magmawalker! Skipping magma_walker_ii recipe.");
            return;
        }

        Item magmaBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.MAGMA);

        ItemStack output = createBook(magmaWalker, magmaWalker.getMaxLevel());
        registerConfigurableRecipe(event, "magma_walker_ii", output,
                "GMG", "MXM", "GLG",
                'G', glowingIngot,
                'M', magmaBlock,
                'L', Items.LEATHER_BOOTS,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Frost Walker: same layout/materials as magma_walker_ii, but with
    // packed ice instead of magma blocks. Produces vanilla
    // minecraft:frost_walker.
    // ============================================================
    private static void registerFrostWalkerII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingPowder = resolveGlowingPowder();
        if (glowingPowder == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder! Skipping frost_walker_ii recipe.");
            return;
        }

        Item packedIce = Item.getItemFromBlock(net.minecraft.init.Blocks.PACKED_ICE);

        ItemStack output = createBook(Enchantments.FROST_WALKER, 2);
        registerConfigurableRecipe(event, "frost_walker_ii", output,
                "GPG", "PXP", "GLG",
                'G', glowingPowder,
                'P', packedIce,
                'L', Items.LEATHER_BOOTS,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Mortalitas: glowing ingot corners, a full xp tome in the center, four
    // fully-drained xp tomes on the edge-middle slots - thematically fitting,
    // since Mortalitas empowers itself with each kill (the surrounding empty
    // tomes represent that spent progression around the "charged" result).
    // ============================================================
    private static void registerMortalitasVIII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping mortalitas_viii recipe.");
            return;
        }

        Enchantment mortalitas = PackCompat.findEnchantment("somanyenchantments", "mortalitas");
        if (mortalitas == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:mortalitas! Skipping mortalitas_viii recipe.");
            return;
        }

        ItemStack output = createBook(mortalitas, mortalitas.getMaxLevel());
        registerConfigurableRecipe(event, "mortalitas_viii", output,
                "GEG", "EXE", "GEG",
                'G', glowingIngot,
                'E', new EmptyXpTomeIngredient(xpTome),
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Penetrating Edge: glowing ingot corners, xp tome in the center, a
    // spartanweaponry Diamond Battleaxe on all four edge-middle slots.
    // ============================================================
    private static void registerPenetratingEdgeVI(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item diamondBattleaxe = PackCompat.findItem("spartanweaponry", "battleaxe_diamond");
        if (glowingIngot == null || diamondBattleaxe == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or spartanweaponry:battleaxe_diamond! Skipping penetrating_edge_vi recipe.");
            return;
        }

        Enchantment penetratingEdge = PackCompat.findEnchantment("somanyenchantments", "penetratingedge");
        if (penetratingEdge == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:penetratingedge! Skipping penetrating_edge_vi recipe.");
            return;
        }

        ItemStack output = createBook(penetratingEdge, penetratingEdge.getMaxLevel());
        registerConfigurableRecipe(event, "penetrating_edge_vi", output,
                "GAG", "AXA", "GAG",
                'G', glowingIngot,
                'A', diamondBattleaxe,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Penetration: mujmajnkraftsbettersurvival's own take on Penetrating
    // Edge. Same layout as penetrating_edge_vi, but with that mod's own
    // Diamond Battle Axe on all four edge-middle slots instead of
    // spartanweaponry's.
    // ============================================================
    private static void registerPenetrationV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item diamondBattleAxe = PackCompat.findItem("mujmajnkraftsbettersurvival", "itemdiamondbattleaxe");
        if (glowingIngot == null || diamondBattleAxe == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or mujmajnkraftsbettersurvival:itemdiamondbattleaxe! Skipping penetration_v recipe.");
            return;
        }

        Enchantment penetration = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "penetration");
        if (penetration == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:penetration! Skipping penetration_v recipe.");
            return;
        }

        ItemStack output = createBook(penetration, penetration.getMaxLevel());
        registerConfigurableRecipe(event, "penetration_v", output,
                "GAG", "AXA", "GAG",
                'G', glowingIngot,
                'A', diamondBattleAxe,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Pushing: spartanweaponry Diamond Arrow corners, xp tome in the
    // center, a xat Polarized Stone on the bottom-middle, a bow on the
    // top/left/right middle.
    // ============================================================
    private static void registerPushingI(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item diamondArrow = PackCompat.findItem("spartanweaponry", "arrow_diamond");
        Item polarizedStone = PackCompat.findItem("xat", "polarized_stone");
        if (diamondArrow == null || polarizedStone == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:arrow_diamond or xat:polarized_stone! Skipping pushing_i recipe.");
            return;
        }

        Enchantment pushing = PackCompat.findEnchantment("somanyenchantments", "pushing");
        if (pushing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:pushing! Skipping pushing_i recipe.");
            return;
        }

        ItemStack output = createBook(pushing, pushing.getMaxLevel());
        registerConfigurableRecipe(event, "pushing_i", output,
                "GBG", "BXB", "GPG",
                'G', diamondArrow,
                'B', Items.BOW,
                'P', polarizedStone,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Rain's Bestowment: glowing ingot corners, xp tome in the center, a
    // splash potion of water on all four edge-middle slots.
    // ============================================================
    private static void registerRainsBestowmentV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping rains_bestowment_v recipe.");
            return;
        }

        Enchantment rainsBestowment = PackCompat.findEnchantment("somanyenchantments", "rainsbestowment");
        if (rainsBestowment == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:rainsbestowment! Skipping rains_bestowment_v recipe.");
            return;
        }

        SpecialIngredient splashWater = new NbtStringTagIngredient(Items.SPLASH_POTION, "Potion", "minecraft:water");

        ItemStack output = createBook(rainsBestowment, rainsBestowment.getMaxLevel());
        registerConfigurableRecipe(event, "rains_bestowment_v", output,
                "GWG", "WXW", "GWG",
                'G', glowingIngot,
                'W', splashWater,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Reinforced Sharpness: glowing ingot corners, xp tome in the center,
    // a diamond sword on the top-middle, a diamond pickaxe on the
    // left-middle, a diamond shovel on the right-middle, and a diamond axe
    // on the bottom-middle.
    // ============================================================
    private static void registerReinforcedSharpnessV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping reinforced_sharpness_v recipe.");
            return;
        }

        Enchantment reinforcedSharpness = PackCompat.findEnchantment("somanyenchantments", "reinforcedsharpness");
        if (reinforcedSharpness == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:reinforcedsharpness! Skipping reinforced_sharpness_v recipe.");
            return;
        }

        ItemStack output = createBook(reinforcedSharpness, reinforcedSharpness.getMaxLevel());
        registerConfigurableRecipe(event, "reinforced_sharpness_v", output,
                "GSG", "PXH", "GAG",
                'G', glowingIngot,
                'S', Items.DIAMOND_SWORD,
                'P', Items.DIAMOND_PICKAXE,
                'H', Items.DIAMOND_SHOVEL,
                'A', Items.DIAMOND_AXE,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Reviled Blade: glowing ingot corners, xp tome in the center, a
    // Potion of Strength II on the top-middle, a switchbow Vampire-Arrow
    // on the left/right middle, and a diamond sword on the bottom-middle.
    // ============================================================
    private static void registerReviledBladeIV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item vampireArrow = PackCompat.findItem("switchbow", "arrowvampier");
        if (glowingIngot == null || vampireArrow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or switchbow:arrowvampier! Skipping reviled_blade_iv recipe.");
            return;
        }

        Enchantment reviledBlade = PackCompat.findEnchantment("somanyenchantments", "reviledblade");
        if (reviledBlade == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:reviledblade! Skipping reviled_blade_iv recipe.");
            return;
        }

        SpecialIngredient strengthII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:strong_strength");

        ItemStack output = createBook(reviledBlade, reviledBlade.getMaxLevel());
        registerConfigurableRecipe(event, "reviled_blade_iv", output,
                "GPG", "AXA", "GSG",
                'G', glowingIngot,
                'P', strengthII,
                'A', vampireArrow,
                'S', Items.DIAMOND_SWORD,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Rune: Magical Blessing: glowing ingot corners, xp tome in the
    // center, a xat Mana Candy on all four edge-middle slots.
    // ============================================================
    private static void registerRuneMagicalBlessingIV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        Item manaCandy = PackCompat.findItem("xat", "mana_candy");
        if (glowingIngot == null || manaCandy == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or xat:mana_candy! Skipping rune_magical_blessing_iv recipe.");
            return;
        }

        Enchantment runeMagicalBlessing = PackCompat.findEnchantment("somanyenchantments", "rune_magicalblessing");
        if (runeMagicalBlessing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:rune_magicalblessing! Skipping rune_magical_blessing_iv recipe.");
            return;
        }

        ItemStack output = createBook(runeMagicalBlessing, runeMagicalBlessing.getMaxLevel());
        registerConfigurableRecipe(event, "rune_magical_blessing_iv", output,
                "GMG", "MXM", "GMG",
                'G', glowingIngot,
                'M', manaCandy,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Rune: Resurrection: glowing ingot corners, xp tome in the center, an
    // emerald on the top/left/right middle, and a totem of undying on the
    // bottom-middle.
    // ============================================================
    private static void registerRuneResurrectionII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping rune_resurrection_ii recipe.");
            return;
        }

        Enchantment runeResurrection = PackCompat.findEnchantment("somanyenchantments", "rune_resurrection");
        if (runeResurrection == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:rune_resurrection! Skipping rune_resurrection_ii recipe.");
            return;
        }

        ItemStack output = createBook(runeResurrection, runeResurrection.getMaxLevel());
        registerConfigurableRecipe(event, "rune_resurrection_ii", output,
                "GEG", "EXE", "GTG",
                'G', glowingIngot,
                'E', Items.EMERALD,
                'T', Items.TOTEM_OF_UNDYING,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Rune: Revival: glowing ingot corners, xp tome in the center, an
    // experience bottle on the top/left/right middle, and an anvil on the
    // bottom-middle.
    // ============================================================
    private static void registerRuneRevivalII(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping rune_revival_ii recipe.");
            return;
        }

        Enchantment runeRevival = PackCompat.findEnchantment("somanyenchantments", "rune_revival");
        if (runeRevival == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:rune_revival! Skipping rune_revival_ii recipe.");
            return;
        }

        Item anvil = Item.getItemFromBlock(net.minecraft.init.Blocks.ANVIL);

        ItemStack output = createBook(runeRevival, runeRevival.getMaxLevel());
        registerConfigurableRecipe(event, "rune_revival_ii", output,
                "GEG", "EXE", "GAG",
                'G', glowingIngot,
                'E', Items.EXPERIENCE_BOTTLE,
                'A', anvil,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Smelter: glowing powder corners, xp tome in the center, a vanilla
    // furnace on the top/bottom middle, and a betternether Netherrack
    // Furnace on the left/right middle.
    // ============================================================
    private static void registerSmelterI(RegistryEvent.Register<IRecipe> event, Item xpTome, Item glowingPowder) {
        Item netherrackFurnace = PackCompat.findItem("betternether", "netherrack_furnace");
        if (netherrackFurnace == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find betternether:netherrack_furnace! Skipping smelter_i recipe.");
            return;
        }

        Enchantment smelter = PackCompat.findEnchantment("somanyenchantments", "smelter");
        if (smelter == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:smelter! Skipping smelter_i recipe.");
            return;
        }

        Item furnace = Item.getItemFromBlock(net.minecraft.init.Blocks.FURNACE);

        ItemStack output = createBook(smelter, smelter.getMaxLevel());
        registerConfigurableRecipe(event, "smelter_i", output,
                "GFG", "NXN", "GFG",
                'G', glowingPowder,
                'F', furnace,
                'N', netherrackFurnace,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Sol's Blessing: glowing ingot corners, xp tome in the center, a
    // clock on the top/bottom middle, and a daylight sensor on the
    // left/right middle - Luna's Blessing's daytime counterpart.
    // ============================================================
    private static void registerSolsBlessingV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping sols_blessing_v recipe.");
            return;
        }

        Enchantment solsBlessing = PackCompat.findEnchantment("somanyenchantments", "solsblessing");
        if (solsBlessing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:solsblessing! Skipping sols_blessing_v recipe.");
            return;
        }

        Item daylightSensor = Item.getItemFromBlock(net.minecraft.init.Blocks.DAYLIGHT_DETECTOR);

        ItemStack output = createBook(solsBlessing, solsBlessing.getMaxLevel());
        registerConfigurableRecipe(event, "sols_blessing_v", output,
                "GCG", "DXD", "GCG",
                'G', glowingIngot,
                'C', Items.CLOCK,
                'D', daylightSensor,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Plowing: glowing powder corners, a spartanweaponry Iron Scythe on
    // all four edge-middle slots, plain book in the center. No xp tome
    // needed.
    // ============================================================
    private static void registerPlowingI(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item ironScythe = PackCompat.findItem("spartanweaponry", "scythe_iron");
        if (ironScythe == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:scythe_iron! Skipping plowing_i recipe.");
            return;
        }

        Enchantment plowing = PackCompat.findEnchantment("somanyenchantments", "plowing");
        if (plowing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:plowing! Skipping plowing_i recipe.");
            return;
        }

        ItemStack output = createBook(plowing, plowing.getMaxLevel());
        registerConfigurableRecipe(event, "plowing_i", output,
                "GSG", "SXS", "GSG",
                'G', glowingPowder,
                'S', ironScythe,
                'X', Items.BOOK);
    }

    // ============================================================
    // Moisturized: glowing powder corners, a Forge universal bucket filled
    // with simpledifficulty's Purified Water on the top/bottom middle, a
    // simpledifficulty Purified Water Bottle on the left/right middle,
    // plain book in the center. No xp tome needed.
    // ============================================================
    private static void registerMoisturizedI(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item filledBucket = PackCompat.findItem("forge", "bucketfilled");
        Item purifiedWaterBottle = PackCompat.findItem("simpledifficulty", "purified_water_bottle");
        if (filledBucket == null || purifiedWaterBottle == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find forge:bucketfilled or simpledifficulty:purified_water_bottle! Skipping moisturized_i recipe.");
            return;
        }

        Enchantment moisturized = PackCompat.findEnchantment("somanyenchantments", "moisturized");
        if (moisturized == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:moisturized! Skipping moisturized_i recipe.");
            return;
        }

        SpecialIngredient purifiedWaterBucket = new NbtStringTagIngredient(filledBucket, new String[]{"Fluid", "FluidName"}, "purifiedwater");

        ItemStack output = createBook(moisturized, moisturized.getMaxLevel());
        registerConfigurableRecipe(event, "moisturized_i", output,
                "GBG", "PXP", "GBG",
                'G', glowingPowder,
                'B', purifiedWaterBucket,
                'P', purifiedWaterBottle,
                'X', Items.BOOK);
    }

    // ============================================================
    // Protection IV: a full set of diamond armor arranged around a plain
    // book, plus glowing powder in the corners. No xp tome or enchanted
    // book needed, so no SpecialIngredient checks apply to this recipe.
    // ============================================================
    private static void registerProtectionIV(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        ItemStack output = createBook(Enchantments.PROTECTION, 4);
        registerConfigurableRecipe(event, "protection_iv", output,
                "GHG", "CXO", "GLG",
                'G', glowingPowder,
                'H', Items.DIAMOND_HELMET,
                'C', Items.DIAMOND_CHESTPLATE,
                'X', Items.BOOK,
                'O', Items.DIAMOND_BOOTS,
                'L', Items.DIAMOND_LEGGINGS);
    }

    // ============================================================
    // Fire Protection IV: glowing powder corners, no helmet/boots -
    // chestplate on top, leggings on bottom, blaze rods on the sides,
    // plain book center.
    // ============================================================
    private static void registerFireProtectionIV(RegistryEvent.Register<IRecipe> event) {
        Item glowingPowder = resolveGlowingPowder();
        if (glowingPowder == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder! Skipping fire_protection_iv recipe.");
            return;
        }

        registerConfigurableRecipe(event, "fire_protection_iv", createBook(Enchantments.FIRE_PROTECTION, 4),
                "GCG", "TXT", "GLG",
                'G', glowingPowder,
                'T', Items.BLAZE_ROD,
                'C', Items.DIAMOND_CHESTPLATE,
                'X', Items.BOOK,
                'L', Items.DIAMOND_LEGGINGS);
    }

    // ============================================================
    // Blast Protection IV: glowing powder corners, no helmet/boots -
    // chestplate on top, leggings on bottom, TNT on the sides, plain book
    // center.
    // ============================================================
    private static void registerBlastProtectionIV(RegistryEvent.Register<IRecipe> event) {
        Item glowingPowder = resolveGlowingPowder();
        if (glowingPowder == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder! Skipping blast_protection_iv recipe.");
            return;
        }

        Item tnt = Item.getItemFromBlock(net.minecraft.init.Blocks.TNT);

        registerConfigurableRecipe(event, "blast_protection_iv", createBook(Enchantments.BLAST_PROTECTION, 4),
                "GCG", "TXT", "GLG",
                'G', glowingPowder,
                'T', tnt,
                'C', Items.DIAMOND_CHESTPLATE,
                'X', Items.BOOK,
                'L', Items.DIAMOND_LEGGINGS);
    }

    // ============================================================
    // Projectile Protection IV: glowing powder corners, no helmet/boots -
    // chestplate on top, leggings on bottom, bows on the sides, plain book
    // center.
    // ============================================================
    private static void registerProjectileProtectionIV(RegistryEvent.Register<IRecipe> event) {
        Item glowingPowder = resolveGlowingPowder();
        if (glowingPowder == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder! Skipping projectile_protection_iv recipe.");
            return;
        }

        registerConfigurableRecipe(event, "projectile_protection_iv", createBook(Enchantments.PROJECTILE_PROTECTION, 4),
                "GCG", "TXT", "GLG",
                'G', glowingPowder,
                'T', Items.BOW,
                'C', Items.DIAMOND_CHESTPLATE,
                'X', Items.BOOK,
                'L', Items.DIAMOND_LEGGINGS);
    }

    // ============================================================
    // Physical Protection IV: glowing powder corners, no helmet/boots -
    // chestplate on top, leggings on bottom, shields on the sides, plain
    // book center. Produces somanyenchantments:physicalprotection (not
    // vanilla, unlike the other protection variants).
    // ============================================================
    private static void registerPhysicalProtectionIV(RegistryEvent.Register<IRecipe> event) {
        Item glowingPowder = resolveGlowingPowder();
        if (glowingPowder == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder! Skipping physical_protection_iv recipe.");
            return;
        }

        Enchantment physicalProtection = PackCompat.findEnchantment("somanyenchantments", "physicalprotection");
        if (physicalProtection == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:physicalprotection! Skipping physical_protection_iv recipe.");
            return;
        }

        registerConfigurableRecipe(event, "physical_protection_iv", createBook(physicalProtection, physicalProtection.getMaxLevel()),
                "GCG", "TXT", "GLG",
                'G', glowingPowder,
                'T', Items.SHIELD,
                'C', Items.DIAMOND_CHESTPLATE,
                'X', Items.BOOK,
                'L', Items.DIAMOND_LEGGINGS);
    }

    // ============================================================
    // Magic Protection IV: glowing powder corners, no helmet/boots -
    // chestplate on top, leggings on bottom, eyes of ender on the sides,
    // plain book center; gold armor pieces to fit the magic theme.
    // Produces somanyenchantments:magicprotection (not vanilla, unlike
    // the other protection variants).
    // ============================================================
    private static void registerMagicProtectionIV(RegistryEvent.Register<IRecipe> event) {
        Item glowingPowder = resolveGlowingPowder();
        if (glowingPowder == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder! Skipping magic_protection_iv recipe.");
            return;
        }

        Enchantment magicProtection = PackCompat.findEnchantment("somanyenchantments", "magicprotection");
        if (magicProtection == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:magicprotection! Skipping magic_protection_iv recipe.");
            return;
        }

        registerConfigurableRecipe(event, "magic_protection_iv", createBook(magicProtection, magicProtection.getMaxLevel()),
                "GCG", "TXT", "GLG",
                'G', glowingPowder,
                'T', Items.ENDER_EYE,
                'C', Items.GOLDEN_CHESTPLATE,
                'X', Items.BOOK,
                'L', Items.GOLDEN_LEGGINGS);
    }

    // ============================================================
    // Advanced Fire Protection IV: glowing ingot corners, no helmet/boots -
    // chestplate on top, leggings on bottom, quark's blaze lantern on the
    // sides, center consumes a Fire Protection IV book. Produces
    // somanyenchantments:advancedfireprotection.
    // ============================================================
    private static void registerAdvancedFireProtectionIV(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping advanced_fire_protection_iv recipe.");
            return;
        }

        Item blazeLantern = PackCompat.findItem("quark", "blaze_lantern");
        if (blazeLantern == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find quark:blaze_lantern! Skipping advanced_fire_protection_iv recipe.");
            return;
        }

        Enchantment advancedFireProtection = PackCompat.findEnchantment("somanyenchantments", "advancedfireprotection");
        if (advancedFireProtection == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedfireprotection! Skipping advanced_fire_protection_iv recipe.");
            return;
        }

        ItemStack output = createBook(advancedFireProtection, advancedFireProtection.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_fire_protection_iv", output,
                "GCG", "TXT", "GLG",
                'G', glowingIngot,
                'T', blazeLantern,
                'C', Items.DIAMOND_CHESTPLATE,
                'X', new EnchantedBookIngredient(Enchantments.FIRE_PROTECTION, 4),
                'L', Items.DIAMOND_LEGGINGS);
    }

    // ============================================================
    // Advanced Blast Protection IV: glowing ingot corners, no helmet/boots -
    // chestplate on top, leggings on bottom, TNT on the sides, center
    // consumes a Blast Protection IV book. Produces
    // somanyenchantments:advancedblastprotection.
    // ============================================================
    private static void registerAdvancedBlastProtectionIV(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping advanced_blast_protection_iv recipe.");
            return;
        }

        Enchantment advancedBlastProtection = PackCompat.findEnchantment("somanyenchantments", "advancedblastprotection");
        if (advancedBlastProtection == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedblastprotection! Skipping advanced_blast_protection_iv recipe.");
            return;
        }

        Item tnt = Item.getItemFromBlock(net.minecraft.init.Blocks.TNT);

        ItemStack output = createBook(advancedBlastProtection, advancedBlastProtection.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_blast_protection_iv", output,
                "GCG", "TXT", "GLG",
                'G', glowingIngot,
                'T', tnt,
                'C', Items.DIAMOND_CHESTPLATE,
                'X', new EnchantedBookIngredient(Enchantments.BLAST_PROTECTION, 4),
                'L', Items.DIAMOND_LEGGINGS);
    }

    // ============================================================
    // Advanced Projectile Protection IV: glowing ingot corners, no
    // helmet/boots - chestplate on top, leggings on bottom,
    // spartanweaponry's diamond longbow on the sides, center consumes a
    // Projectile Protection IV book. Produces
    // somanyenchantments:advancedprojectileprotection.
    // ============================================================
    private static void registerAdvancedProjectileProtectionIV(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping advanced_projectile_protection_iv recipe.");
            return;
        }

        Item diamondLongbow = PackCompat.findItem("spartanweaponry", "longbow_diamond");
        if (diamondLongbow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:longbow_diamond! Skipping advanced_projectile_protection_iv recipe.");
            return;
        }

        Enchantment advancedProjectileProtection = PackCompat.findEnchantment("somanyenchantments", "advancedprojectileprotection");
        if (advancedProjectileProtection == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedprojectileprotection! Skipping advanced_projectile_protection_iv recipe.");
            return;
        }

        ItemStack output = createBook(advancedProjectileProtection, advancedProjectileProtection.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_projectile_protection_iv", output,
                "GCG", "TXT", "GLG",
                'G', glowingIngot,
                'T', diamondLongbow,
                'C', Items.DIAMOND_CHESTPLATE,
                'X', new EnchantedBookIngredient(Enchantments.PROJECTILE_PROTECTION, 4),
                'L', Items.DIAMOND_LEGGINGS);
    }

    // ============================================================
    // Advanced Protection IV: the full diamond armour set arranged exactly
    // like protection_iv, but with glowing ingot corners instead of glowing
    // powder, and the center consuming a Protection IV book rather than a
    // plain one. Produces somanyenchantments:advancedprotection.
    // ============================================================
    private static void registerAdvancedProtectionIV(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping advanced_protection_iv recipe.");
            return;
        }

        Enchantment advancedProtection = PackCompat.findEnchantment("somanyenchantments", "advancedprotection");
        if (advancedProtection == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedprotection! Skipping advanced_protection_iv recipe.");
            return;
        }

        ItemStack output = createBook(advancedProtection, advancedProtection.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_protection_iv", output,
                "GHG", "CXO", "GLG",
                'G', glowingIngot,
                'H', Items.DIAMOND_HELMET,
                'C', Items.DIAMOND_CHESTPLATE,
                'X', new EnchantedBookIngredient(Enchantments.PROTECTION, 4),
                'O', Items.DIAMOND_BOOTS,
                'L', Items.DIAMOND_LEGGINGS);
    }

    // ============================================================
    // Fortune III: glowing dust corners, iron/gold/diamond blocks around a
    // plain book, diamond pickaxe on the bottom-middle.
    // ============================================================
    private static void registerFortuneIII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item ironBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.IRON_BLOCK);
        Item goldBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.GOLD_BLOCK);
        Item diamondBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.DIAMOND_BLOCK);

        registerConfigurableRecipe(event, "fortune_iii", createBook(Enchantments.FORTUNE, 3),
                "GIG", "AXD", "GPG",
                'G', glowingPowder,
                'I', ironBlock,
                'A', goldBlock,
                'X', Items.BOOK,
                'D', diamondBlock,
                'P', Items.DIAMOND_PICKAXE);
    }

    // ============================================================
    // Looting III: identical layout to fortune_iii - glowing dust corners,
    // iron/gold/diamond blocks around a plain book - but with a diamond
    // sword on the bottom-middle instead of a pickaxe.
    // ============================================================
    private static void registerLootingIII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item ironBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.IRON_BLOCK);
        Item goldBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.GOLD_BLOCK);
        Item diamondBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.DIAMOND_BLOCK);

        registerConfigurableRecipe(event, "looting_iii", createBook(Enchantments.LOOTING, 3),
                "GIG", "AXD", "GPG",
                'G', glowingPowder,
                'I', ironBlock,
                'A', goldBlock,
                'X', Items.BOOK,
                'D', diamondBlock,
                'P', Items.DIAMOND_SWORD);
    }

    // ============================================================
    // Smite V: glowing powder corners, diamond swords on the left/right
    // middle, an undead mob head (skeleton/wither skeleton/zombie only -
    // Smite only affects the undead, so creeper/player/dragon heads no
    // longer qualify) on the top/bottom middle, plain book in the center.
    // ============================================================
    private static void registerSmiteV(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        registerConfigurableRecipe(event, "smite_v", createBook(Enchantments.SMITE, 5),
                "GHG", "SXS", "GHG",
                'G', glowingPowder,
                'H', new UndeadHeadIngredient(),
                'S', Items.DIAMOND_SWORD,
                'X', Items.BOOK);
    }

    // ============================================================
    // Advanced Smite: glowing ingot corners, an xp tome in the center, two
    // Smite V books on the top/bottom middle, undead heads on the
    // left/right middle. Produces somanyenchantments:advancedsmite.
    // ============================================================
    private static void registerAdvancedSmiteV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null || xpTome == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or xpbook:xp_book! Skipping advanced_smite_v recipe.");
            return;
        }

        Enchantment advancedSmite = PackCompat.findEnchantment("somanyenchantments", "advancedsmite");
        if (advancedSmite == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedsmite! Skipping advanced_smite_v recipe.");
            return;
        }

        SpecialIngredient smiteVBook = new EnchantedBookIngredient(Enchantments.SMITE, Enchantments.SMITE.getMaxLevel());

        ItemStack output = createBook(advancedSmite, advancedSmite.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_smite_v", output,
                "GBG", "HXH", "GBG",
                'G', glowingIngot,
                'B', smiteVBook,
                'H', new UndeadHeadIngredient(),
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Supreme Smite: glowing gem corners, an xp tome in the center, two
    // Advanced Smite books on the top/bottom middle, undead heads on the
    // left/right middle. Produces somanyenchantments:supremesmite.
    // ============================================================
    private static void registerSupremeSmiteV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingGem = resolveGlowingGem();
        if (glowingGem == null || xpTome == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem or xpbook:xp_book! Skipping supreme_smite_v recipe.");
            return;
        }

        Enchantment advancedSmite = PackCompat.findEnchantment("somanyenchantments", "advancedsmite");
        if (advancedSmite == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedsmite! Skipping supreme_smite_v recipe.");
            return;
        }

        Enchantment supremeSmite = PackCompat.findEnchantment("somanyenchantments", "supremesmite");
        if (supremeSmite == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:supremesmite! Skipping supreme_smite_v recipe.");
            return;
        }

        SpecialIngredient advancedSmiteBook = new EnchantedBookIngredient(advancedSmite, advancedSmite.getMaxLevel());

        ItemStack output = createBook(supremeSmite, supremeSmite.getMaxLevel());
        registerConfigurableRecipe(event, "supreme_smite_v", output,
                "GBG", "HXH", "GBG",
                'G', glowingGem,
                'B', advancedSmiteBook,
                'H', new UndeadHeadIngredient(),
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Bane of Arthropods V: glowing powder corners, fermented spider eyes
    // on all four edge-middle slots, plain book in the center.
    // ============================================================
    private static void registerBaneOfArthropodsV(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        registerConfigurableRecipe(event, "bane_of_arthropods_v", createBook(Enchantments.BANE_OF_ARTHROPODS, 5),
                "GFG", "FXF", "GFG",
                'G', glowingPowder,
                'F', Items.FERMENTED_SPIDER_EYE,
                'X', Items.BOOK);
    }

    // ============================================================
    // Feather Falling IV: glowing powder corners, a Potion of Feather
    // Falling (potioncore:slow_fall) on all four edge-middle slots,
    // plain book in the center.
    // ============================================================
    private static void registerFeatherFallingIV(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        SpecialIngredient featherFallingPotion = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "potioncore:slow_fall");

        registerConfigurableRecipe(event, "feather_falling_iv", createBook(Enchantments.FEATHER_FALLING, 4),
                "GPG", "PXP", "GPG",
                'G', glowingPowder,
                'P', featherFallingPotion,
                'X', Items.BOOK);
    }

    // ============================================================
    // Power V: glowing powder corners, a spartanweaponry diamond longbow
    // on all four edge-middle slots, plain book in the center. (Vanilla
    // Power's real max level is V, not IV.)
    // ============================================================
    private static void registerPowerV(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item diamondLongbow = PackCompat.findItem("spartanweaponry", "longbow_diamond");
        if (diamondLongbow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:longbow_diamond! Skipping power_v recipe.");
            return;
        }

        registerConfigurableRecipe(event, "power_v", createBook(Enchantments.POWER, 5),
                "GLG", "LXL", "GLG",
                'G', glowingPowder,
                'L', diamondLongbow,
                'X', Items.BOOK);
    }

    // ============================================================
    // Fire Aspect II: glowing powder corners, blaze rods on all four
    // edge-middle slots, plain book in the center.
    // ============================================================
    private static void registerFireAspectII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        registerConfigurableRecipe(event, "fire_aspect_ii", createBook(Enchantments.FIRE_ASPECT, 2),
                "GBG", "BXB", "GBG",
                'G', glowingPowder,
                'B', Items.BLAZE_ROD,
                'X', Items.BOOK);
    }

    // ============================================================
    // Heating: glowing powder corners, an armorunder warm chestplate liner
    // on the top-middle, armorunder warm liner material on the left/right
    // middle, a simpledifficulty Heater block on the bottom-middle, plain
    // book in the center.
    // ============================================================
    private static void registerHeating(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item warmChestplateLiner = PackCompat.findItem("armorunder", "warm_chestplate_liner");
        Item warmLinerMaterial = PackCompat.findItem("armorunder", "warm_liner_material");
        Item heater = PackCompat.findItem("simpledifficulty", "heater");
        if (warmChestplateLiner == null || warmLinerMaterial == null || heater == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find armorunder:warm_chestplate_liner, armorunder:warm_liner_material, or simpledifficulty:heater! Skipping heating recipe.");
            return;
        }

        Enchantment heating = PackCompat.findEnchantment("simpledifficulty", "heating");
        if (heating == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find simpledifficulty:heating! Skipping heating recipe.");
            return;
        }

        ItemStack output = createBook(heating, heating.getMaxLevel());
        registerConfigurableRecipe(event, "heating", output,
                "GTG", "MXM", "GHG",
                'G', glowingPowder,
                'T', warmChestplateLiner,
                'M', warmLinerMaterial,
                'X', Items.BOOK,
                'H', heater);
    }

    // ============================================================
    // Chilling: same layout as heating, but every warm component is
    // swapped for its cool counterpart - armorunder cool chestplate liner
    // on the top-middle, cool liner material on the left/right middle, a
    // simpledifficulty Chiller block on the bottom-middle, plain book in
    // the center, glowing powder corners.
    // ============================================================
    private static void registerChilling(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item coolChestplateLiner = PackCompat.findItem("armorunder", "cool_chestplate_liner");
        Item coolLinerMaterial = PackCompat.findItem("armorunder", "cool_liner_material");
        Item chiller = PackCompat.findItem("simpledifficulty", "chiller");
        if (coolChestplateLiner == null || coolLinerMaterial == null || chiller == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find armorunder:cool_chestplate_liner, armorunder:cool_liner_material, or simpledifficulty:chiller! Skipping chilling recipe.");
            return;
        }

        Enchantment chilling = PackCompat.findEnchantment("simpledifficulty", "chilling");
        if (chilling == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find simpledifficulty:chilling! Skipping chilling recipe.");
            return;
        }

        ItemStack output = createBook(chilling, chilling.getMaxLevel());
        registerConfigurableRecipe(event, "chilling", output,
                "GTG", "MXM", "GHG",
                'G', glowingPowder,
                'T', coolChestplateLiner,
                'M', coolLinerMaterial,
                'X', Items.BOOK,
                'H', chiller);
    }

    // ============================================================
    // Respiration III: glowing powder corners, a Potion of Water Breathing
    // (the 8-minute/extended variant) on all four edge-middle slots, plain
    // book in the center.
    // ============================================================
    private static void registerRespirationIII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        SpecialIngredient waterBreathingPotion = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:long_water_breathing");

        registerConfigurableRecipe(event, "respiration_iii", createBook(Enchantments.RESPIRATION, 3),
                "GPG", "PXP", "GPG",
                'G', glowingPowder,
                'P', waterBreathingPotion,
                'X', Items.BOOK);
    }

    // ============================================================
    // Aqua Affinity: same layout as respiration_iii, but the bottom-middle
    // Potion of Water Breathing is swapped for a potionfingers Ring of
    // Haste - the other three edge-middles stay Water Breathing.
    // ============================================================
    private static void registerAquaAffinity(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        if (potionRing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find potionfingers:ring! Skipping aqua_affinity recipe.");
            return;
        }

        SpecialIngredient waterBreathingPotion = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:long_water_breathing");
        SpecialIngredient ringOfHaste = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:haste");

        registerConfigurableRecipe(event, "aqua_affinity", createBook(Enchantments.AQUA_AFFINITY, 1),
                "GWG", "WXW", "GRG",
                'G', glowingPowder,
                'W', waterBreathingPotion,
                'R', ringOfHaste,
                'X', Items.BOOK);
    }

    // ============================================================
    // Flame: glowing powder corners, blaze powder on all four edge-middle
    // slots, plain book in the center.
    // ============================================================
    private static void registerFlame(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        registerConfigurableRecipe(event, "flame", createBook(Enchantments.FLAME, 1),
                "GBG", "BXB", "GBG",
                'G', glowingPowder,
                'B', Items.BLAZE_POWDER,
                'X', Items.BOOK);
    }

    // ============================================================
    // Depth Strider III: glowing powder corners, a Potion of Swiftness II
    // on the top/bottom middle, aquaculture Neptunium Boots on the
    // left/right middle, plain book in the center.
    // ============================================================
    private static void registerDepthStriderIII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item neptuniumBoots = PackCompat.findItem("aquaculture", "neptunium_boots");
        if (neptuniumBoots == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find aquaculture:neptunium_boots! Skipping depth_strider_iii recipe.");
            return;
        }

        SpecialIngredient swiftnessII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:strong_swiftness");

        registerConfigurableRecipe(event, "depth_strider_iii", createBook(Enchantments.DEPTH_STRIDER, 3),
                "GPG", "BXB", "GPG",
                'G', glowingPowder,
                'P', swiftnessII,
                'B', neptuniumBoots,
                'X', Items.BOOK);
    }

    // ============================================================
    // Fiery Edge: glowing ingot corners, blaze rods on the top/left/right
    // middle, an iceandfire Fire Dragon Blood bottle on the bottom-middle,
    // center consumes a Fire Aspect II book.
    // ============================================================
    private static void registerFieryEdge(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item fireDragonBlood = PackCompat.findItem("iceandfire", "fire_dragon_blood");
        if (fireDragonBlood == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find iceandfire:fire_dragon_blood! Skipping fiery_edge recipe.");
            return;
        }

        Enchantment fieryEdge = PackCompat.findEnchantment("somanyenchantments", "fieryedge");
        if (fieryEdge == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:fieryedge! Skipping fiery_edge recipe.");
            return;
        }

        ItemStack output = createBook(fieryEdge, fieryEdge.getMaxLevel());
        registerConfigurableRecipe(event, "fiery_edge", output,
                "GBG", "BXB", "GDG",
                'G', glowingIngot,
                'B', Items.BLAZE_ROD,
                'X', new EnchantedBookIngredient(Enchantments.FIRE_ASPECT, 2),
                'D', fireDragonBlood);
    }

    // ============================================================
    // Knockback II: glowing powder corners, a spartanweaponry Iron Hammer
    // on all four edge-middle slots, plain book in the center.
    // ============================================================
    private static void registerKnockbackII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item ironHammer = PackCompat.findItem("spartanweaponry", "hammer_iron");
        if (ironHammer == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:hammer_iron! Skipping knockback_ii recipe.");
            return;
        }

        registerConfigurableRecipe(event, "knockback_ii", createBook(Enchantments.KNOCKBACK, 2),
                "GHG", "HXH", "GHG",
                'G', glowingPowder,
                'H', ironHammer,
                'X', Items.BOOK);
    }

    // ============================================================
    // Advanced Knockback: same layout as knockback_ii, but the corners are
    // upgraded to xat's tier 2 material (glowing ingot instead of glowing
    // powder), the iron hammers are upgraded to spartanweaponry Diamond
    // Hammers, and the center consumes a Knockback II book instead of a
    // plain one, producing somanyenchantments:advancedknockback.
    // ============================================================
    private static void registerAdvancedKnockback(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item diamondHammer = PackCompat.findItem("spartanweaponry", "hammer_diamond");
        if (diamondHammer == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:hammer_diamond! Skipping advanced_knockback recipe.");
            return;
        }

        Enchantment advancedKnockback = PackCompat.findEnchantment("somanyenchantments", "advancedknockback");
        if (advancedKnockback == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedknockback! Skipping advanced_knockback recipe.");
            return;
        }

        ItemStack output = createBook(advancedKnockback, advancedKnockback.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_knockback", output,
                "GHG", "HXH", "GHG",
                'G', glowingIngot,
                'H', diamondHammer,
                'X', new EnchantedBookIngredient(Enchantments.KNOCKBACK, 2));
    }

    // ============================================================
    // Infinity: glowing ingot corners, xp tome in the center, a
    // spartanweaponry Heavy Quiver Arrow on the bottom-middle, and
    // spectral arrows on the top/left/right middle slots.
    // ============================================================
    private static void registerInfinity(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item heavyQuiverArrow = PackCompat.findItem("spartanweaponry", "quiver_arrow_heavy");
        if (heavyQuiverArrow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:quiver_arrow_heavy! Skipping infinity recipe.");
            return;
        }

        registerConfigurableRecipe(event, "infinity", createBook(Enchantments.INFINITY, 1),
                "GAG", "AXA", "GHG",
                'G', glowingIngot,
                'A', Items.SPECTRAL_ARROW,
                'H', heavyQuiverArrow,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Luck of the Sea III: glowing powder corners, sea lanterns on the
    // top/bottom middle, prismarine on the left/right middle, plain book
    // in the center.
    // ============================================================
    private static void registerLuckOfTheSeaIII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item prismarine = resolvePrismarineSlot();
        Item seaLantern = resolveSeaLanternSlot();

        registerConfigurableRecipe(event, "luck_of_the_sea_iii", createBook(Enchantments.LUCK_OF_THE_SEA, 3),
                "GSG", "PXP", "GSG",
                'G', glowingPowder,
                'S', seaLantern,
                'P', prismarine,
                'X', Items.BOOK);
    }

    // ============================================================
    // Lure III: same layout as luck_of_the_sea_iii, but the bottom-middle
    // sea lantern is swapped for a potionfingers Ring of Speed - top-middle
    // stays sea lantern, left/right stay prismarine.
    // ============================================================
    private static void registerLureIII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item prismarine = resolvePrismarineSlot();
        Item seaLantern = resolveSeaLanternSlot();
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        if (potionRing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find potionfingers:ring! Skipping lure_iii recipe.");
            return;
        }

        SpecialIngredient ringOfSpeed = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:speed");

        registerConfigurableRecipe(event, "lure_iii", createBook(Enchantments.LURE, 3),
                "GSG", "PXP", "GRG",
                'G', glowingPowder,
                'S', seaLantern,
                'P', prismarine,
                'R', ringOfSpeed,
                'X', Items.BOOK);
    }

    // ============================================================
    // Advanced Luck of the Sea: glowing ingot corners, a Potion of Luck on
    // the top/left/right middle, a pufferfish on the bottom-middle, center
    // consumes a Luck of the Sea III book.
    // ============================================================
    private static void registerAdvancedLuckOfTheSea(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Enchantment advancedLuckOfTheSea = PackCompat.findEnchantment("somanyenchantments", "advancedluckofthesea");
        if (advancedLuckOfTheSea == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedluckofthesea! Skipping advanced_luck_of_the_sea recipe.");
            return;
        }

        ItemStack pufferfish = new ItemStack(Items.FISH, 1, 3);
        SpecialIngredient potionOfLuck = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:luck");

        ItemStack output = createBook(advancedLuckOfTheSea, advancedLuckOfTheSea.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_luck_of_the_sea", output,
                "GPG", "PXP", "GFG",
                'G', glowingIngot,
                'P', potionOfLuck,
                'F', pufferfish,
                'X', new EnchantedBookIngredient(Enchantments.LUCK_OF_THE_SEA, 3));
    }

    // ============================================================
    // Advanced Lure: glowing ingot corners, a fishingmadebetter Bait Box on
    // the top-middle, a fishingmadebetter Bait Bucket on the bottom-middle,
    // a Potion of Swiftness II on the left/right middle, center consumes a
    // Lure III book.
    // ============================================================
    private static void registerAdvancedLureIII(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item baitBox = PackCompat.findItem("fishingmadebetter", "baitbox");
        Item baitBucket = PackCompat.findItem("fishingmadebetter", "bait_bucket");
        if (baitBox == null || baitBucket == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find fishingmadebetter:baitbox or fishingmadebetter:bait_bucket! Skipping advanced_lure recipe.");
            return;
        }

        Enchantment advancedLure = PackCompat.findEnchantment("somanyenchantments", "advancedlure");
        if (advancedLure == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedlure! Skipping advanced_lure recipe.");
            return;
        }

        SpecialIngredient swiftnessII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:strong_swiftness");

        ItemStack output = createBook(advancedLure, advancedLure.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_lure", output,
                "GBG", "SXS", "GKG",
                'G', glowingIngot,
                'B', baitBox,
                'S', swiftnessII,
                'K', baitBucket,
                'X', new EnchantedBookIngredient(Enchantments.LURE, 3));
    }

    // ============================================================
    // Purification: glowing ingot corners, xp tome in the center, a
    // simpledifficulty Purified Iron Canteen (item is a single
    // simpledifficulty:iron_canteen distinguished by an int "CanteenType"
    // NBT tag rather than damage - 3 = ThirstEnum.PURIFIED) on the
    // bottom-middle, emerald blocks on the top/left/right middle.
    // ============================================================
    private static void registerPurification(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item ironCanteen = PackCompat.findItem("simpledifficulty", "iron_canteen");
        if (ironCanteen == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find simpledifficulty:iron_canteen! Skipping purification recipe.");
            return;
        }

        Enchantment purification = PackCompat.findEnchantment("somanyenchantments", "purification");
        if (purification == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:purification! Skipping purification recipe.");
            return;
        }

        SpecialIngredient purifiedIronCanteen = new NbtIntTagIngredient(ironCanteen, "CanteenType", 3);
        Item emeraldBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.EMERALD_BLOCK);

        ItemStack output = createBook(purification, purification.getMaxLevel());
        registerConfigurableRecipe(event, "purification", output,
                "GEG", "EXE", "GCG",
                'G', glowingIngot,
                'E', emeraldBlock,
                'C', purifiedIronCanteen,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Spell Breaker: glowing ingot corners, xp tome in the center, a Quark
    // Witch Hat on the top-middle, a Totem of Undying on the bottom-middle,
    // a Lycanites Mobs Cleansing Crystal on the left/right middle.
    // ============================================================
    private static void registerSpellBreaker(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item witchHat = PackCompat.findItem("quark", "witch_hat");
        Item cleansingCrystal = PackCompat.findItem("lycanitesmobs", "cleansingcrystal");
        if (witchHat == null || cleansingCrystal == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find quark:witch_hat or lycanitesmobs:cleansingcrystal! Skipping spell_breaker recipe.");
            return;
        }

        Enchantment spellBreaker = PackCompat.findEnchantment("somanyenchantments", "spellbreaker");
        if (spellBreaker == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:spellbreaker! Skipping spell_breaker recipe.");
            return;
        }

        ItemStack output = createBook(spellBreaker, spellBreaker.getMaxLevel());
        registerConfigurableRecipe(event, "spell_breaker", output,
                "GWG", "CXC", "GTG",
                'G', glowingIngot,
                'W', witchHat,
                'C', cleansingCrystal,
                'T', Items.TOTEM_OF_UNDYING,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Splitshot: glowing ingot corners, xp tome in the center, a switchbow
    // Switch-Bow on the bottom-middle, switchbow Triple Arrows on the
    // top/left/right middle.
    // ============================================================
    private static void registerSplitshot(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item switchbow = PackCompat.findItem("switchbow", "switchbow");
        Item tripleArrow = PackCompat.findItem("switchbow", "arrowtriple");
        if (switchbow == null || tripleArrow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find switchbow:switchbow or switchbow:arrowtriple! Skipping splitshot recipe.");
            return;
        }

        Enchantment splitshot = PackCompat.findEnchantment("somanyenchantments", "splitshot");
        if (splitshot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:splitshot! Skipping splitshot recipe.");
            return;
        }

        ItemStack output = createBook(splitshot, splitshot.getMaxLevel());
        registerConfigurableRecipe(event, "splitshot", output,
                "GAG", "AXA", "GBG",
                'G', glowingIngot,
                'A', tripleArrow,
                'B', switchbow,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Multishot: same layout as splitshot, but the switchbow Switch-Bow on
    // the bottom-middle is swapped for a spartanweaponry Diamond Longbow -
    // switchbow Triple Arrows stay on the top/left/right middle.
    // ============================================================
    private static void registerMultishot(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item diamondLongbow = PackCompat.findItem("spartanweaponry", "longbow_diamond");
        Item tripleArrow = PackCompat.findItem("switchbow", "arrowtriple");
        if (diamondLongbow == null || tripleArrow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:longbow_diamond or switchbow:arrowtriple! Skipping multishot recipe.");
            return;
        }

        Enchantment multishot = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "multishot");
        if (multishot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:multishot! Skipping multishot recipe.");
            return;
        }

        ItemStack output = createBook(multishot, multishot.getMaxLevel());
        registerConfigurableRecipe(event, "multishot", output,
                "GAG", "AXA", "GBG",
                'G', glowingIngot,
                'A', tripleArrow,
                'B', diamondLongbow,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Strafe: glowing ingot corners, xp tome in the center, a
    // potionfingers Ring of Speed on the top-middle, sugar on the
    // left/right middle, a spartanweaponry Diamond Longbow on the
    // bottom-middle.
    // ============================================================
    private static void registerStrafe(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        Item diamondLongbow = PackCompat.findItem("spartanweaponry", "longbow_diamond");
        if (potionRing == null || diamondLongbow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find potionfingers:ring or spartanweaponry:longbow_diamond! Skipping strafe recipe.");
            return;
        }

        Enchantment strafe = PackCompat.findEnchantment("somanyenchantments", "strafe");
        if (strafe == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:strafe! Skipping strafe recipe.");
            return;
        }

        SpecialIngredient ringOfSpeed = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:speed");

        ItemStack output = createBook(strafe, strafe.getMaxLevel());
        registerConfigurableRecipe(event, "strafe", output,
                "GRG", "SXS", "GBG",
                'G', glowingIngot,
                'R', ringOfSpeed,
                'S', Items.SUGAR,
                'B', diamondLongbow,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Swift Swimming (this modpack's closest analog to "Underwater
    // Strider"): glowing ingot corners, xp tome in the center, a Potion of
    // Water Breathing (8-minute variant) on the top-middle, aquaculture
    // Neptunium Boots on the left/right middle, a potionfingers Ring of
    // Speed on the bottom-middle.
    // ============================================================
    private static void registerSwiftSwimming(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item neptuniumBoots = PackCompat.findItem("aquaculture", "neptunium_boots");
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        if (neptuniumBoots == null || potionRing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find aquaculture:neptunium_boots or potionfingers:ring! Skipping swift_swimming recipe.");
            return;
        }

        Enchantment swiftSwimming = PackCompat.findEnchantment("somanyenchantments", "swiftswimming");
        if (swiftSwimming == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:swiftswimming! Skipping swift_swimming recipe.");
            return;
        }

        SpecialIngredient waterBreathingPotion = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:long_water_breathing");
        SpecialIngredient ringOfSpeed = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:speed");

        ItemStack output = createBook(swiftSwimming, swiftSwimming.getMaxLevel());
        registerConfigurableRecipe(event, "swift_swimming", output,
                "GWG", "NXN", "GRG",
                'G', glowingIngot,
                'W', waterBreathingPotion,
                'N', neptuniumBoots,
                'R', ringOfSpeed,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Thunderstorm's Bestowment V: glowing ingot corners, xp tome in the
    // center, a switchbow Lightning Bolt Arrow on all four edge-middle
    // slots.
    // ============================================================
    private static void registerThunderstormsBestowmentV(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item lightningArrow = PackCompat.findItem("switchbow", "arrowlightningbolt");
        if (lightningArrow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find switchbow:arrowlightningbolt! Skipping thunderstorms_bestowment_v recipe.");
            return;
        }

        Enchantment thunderstormsBestowment = PackCompat.findEnchantment("somanyenchantments", "thunderstormsbestowment");
        if (thunderstormsBestowment == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:thunderstormsbestowment! Skipping thunderstorms_bestowment_v recipe.");
            return;
        }

        ItemStack output = createBook(thunderstormsBestowment, thunderstormsBestowment.getMaxLevel());
        registerConfigurableRecipe(event, "thunderstorms_bestowment_v", output,
                "GAG", "AXA", "GAG",
                'G', glowingIngot,
                'A', lightningArrow,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // True Strike: glowing ingot corners, xp tome in the center, an
    // iceandfire Blindfold on the bottom-middle, a Potion of Invisibility
    // (8-minute variant) on the top/left/right middle.
    // ============================================================
    private static void registerTrueStrike(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item blindfold = PackCompat.findItem("iceandfire", "blindfold");
        if (blindfold == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find iceandfire:blindfold! Skipping true_strike recipe.");
            return;
        }

        Enchantment trueStrike = PackCompat.findEnchantment("somanyenchantments", "truestrike");
        if (trueStrike == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:truestrike! Skipping true_strike recipe.");
            return;
        }

        SpecialIngredient longInvisibility = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:long_invisibility");

        ItemStack output = createBook(trueStrike, trueStrike.getMaxLevel());
        registerConfigurableRecipe(event, "true_strike", output,
                "GPG", "PXP", "GBG",
                'G', glowingIngot,
                'P', longInvisibility,
                'B', blindfold,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Unreasonable: glowing ingot corners, xp tome in the center, a Quark
    // Witch Hat on the top-middle, brewing stands on the left/right
    // middle, a cauldron on the bottom-middle.
    // ============================================================
    private static void registerUnreasonable(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item witchHat = PackCompat.findItem("quark", "witch_hat");
        if (witchHat == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find quark:witch_hat! Skipping unreasonable recipe.");
            return;
        }

        Enchantment unreasonable = PackCompat.findEnchantment("somanyenchantments", "unreasonable");
        if (unreasonable == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:unreasonable! Skipping unreasonable recipe.");
            return;
        }

        Item brewingStand = Item.getItemFromBlock(net.minecraft.init.Blocks.BREWING_STAND);
        Item cauldron = Item.getItemFromBlock(net.minecraft.init.Blocks.CAULDRON);

        ItemStack output = createBook(unreasonable, unreasonable.getMaxLevel());
        registerConfigurableRecipe(event, "unreasonable", output,
                "GWG", "SXS", "GCG",
                'G', glowingIngot,
                'W', witchHat,
                'S', brewingStand,
                'C', cauldron,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Unsheathing: glowing ingot corners, xp tome center, a clock on
    // the bottom, strength II potions on the remaining edge slots.
    // ============================================================
    private static void registerUnsheathing(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Enchantment unsheathing = PackCompat.findEnchantment("somanyenchantments", "unsheathing");
        if (unsheathing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:unsheathing! Skipping unsheathing recipe.");
            return;
        }

        SpecialIngredient strengthPotion = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:strong_strength");

        ItemStack output = createBook(unsheathing, unsheathing.getMaxLevel());
        registerConfigurableRecipe(event, "unsheathing", output,
                "GPG", "PXP", "GCG",
                'G', glowingIngot,
                'P', strengthPotion,
                'C', Items.CLOCK,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Upgraded Potentials: glowing gem corners, xp tome center,
    // Bountiful Baubles' reforger on top, Quality Tools' reforging
    // station on bottom, Better Nether's cincinnasite forge on the sides.
    // ============================================================
    private static void registerUpgradedPotentials(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingGem = resolveGlowingGem();
        net.minecraft.block.Block reforgerBlock = net.minecraft.block.Block.REGISTRY.getObject(new ResourceLocation("bountifulbaubles", "reforger"));
        net.minecraft.block.Block reforgingStationBlock = net.minecraft.block.Block.REGISTRY.getObject(new ResourceLocation("qualitytools", "reforging_station"));
        net.minecraft.block.Block cincinnasiteForgeBlock = net.minecraft.block.Block.REGISTRY.getObject(new ResourceLocation("betternether", "cincinnasite_forge"));
        if (glowingGem == null || reforgerBlock == null || reforgingStationBlock == null || cincinnasiteForgeBlock == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem, bountifulbaubles:reforger, qualitytools:reforging_station, or betternether:cincinnasite_forge! Skipping upgraded_potentials recipe.");
            return;
        }
        Item reforger = Item.getItemFromBlock(reforgerBlock);
        Item reforgingStation = Item.getItemFromBlock(reforgingStationBlock);
        Item cincinnasiteForge = Item.getItemFromBlock(cincinnasiteForgeBlock);

        Enchantment upgradedPotentials = PackCompat.findEnchantment("somanyenchantments", "upgradedpotentials");
        if (upgradedPotentials == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:upgradedpotentials! Skipping upgraded_potentials recipe.");
            return;
        }

        ItemStack output = createBook(upgradedPotentials, upgradedPotentials.getMaxLevel());
        registerConfigurableRecipe(event, "upgraded_potentials", output,
                "GRG", "CXC", "GSG",
                'G', glowingGem,
                'R', reforger,
                'C', cincinnasiteForge,
                'S', reforgingStation,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Water Aspect V: glowing ingot corners, xp tome center, a sea
    // serpent fang on top/bottom, any color of sea serpent scales
    // on the sides.
    // ============================================================
    private static void registerWaterAspectV(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item seaSerpentFang = PackCompat.findItem("iceandfire", "sea_serpent_fang");
        Item scalesBronze = PackCompat.findItem("iceandfire", "sea_serpent_scales_bronze");
        Item scalesBlue = PackCompat.findItem("iceandfire", "sea_serpent_scales_blue");
        Item scalesGreen = PackCompat.findItem("iceandfire", "sea_serpent_scales_green");
        Item scalesRed = PackCompat.findItem("iceandfire", "sea_serpent_scales_red");
        Item scalesTeal = PackCompat.findItem("iceandfire", "sea_serpent_scales_teal");
        Item scalesDeepblue = PackCompat.findItem("iceandfire", "sea_serpent_scales_deepblue");
        Item scalesPurple = PackCompat.findItem("iceandfire", "sea_serpent_scales_purple");
        if (seaSerpentFang == null || scalesBronze == null || scalesBlue == null || scalesGreen == null
                || scalesRed == null || scalesTeal == null || scalesDeepblue == null || scalesPurple == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find iceandfire sea serpent fang/scales items! Skipping water_aspect_v recipe.");
            return;
        }

        Enchantment waterAspect = PackCompat.findEnchantment("somanyenchantments", "wateraspect");
        if (waterAspect == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:wateraspect! Skipping water_aspect_v recipe.");
            return;
        }

        SpecialIngredient anyScales = new AnyOfItemsIngredient(scalesBronze, scalesBlue, scalesGreen, scalesRed, scalesTeal, scalesDeepblue, scalesPurple);

        ItemStack output = createBook(waterAspect, waterAspect.getMaxLevel());
        registerConfigurableRecipe(event, "water_aspect_v", output,
                "GFG", "SXS", "GFG",
                'G', glowingIngot,
                'F', seaSerpentFang,
                'S', anyScales,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Winter's Grace: glowing ingot corners, xp tome center, Serene
    // Seasons' season clock on the bottom, Lycanites Mobs' bucket of
    // ooze on the remaining edge slots.
    // ============================================================
    private static void registerWintersGrace(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item seasonClock = PackCompat.findItem("sereneseasons", "season_clock");
        Item bucketOoze = PackCompat.findItem("lycanitesmobs", "bucketooze");
        if (seasonClock == null || bucketOoze == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find sereneseasons:season_clock or lycanitesmobs:bucketooze! Skipping winters_grace recipe.");
            return;
        }

        Enchantment wintersGrace = PackCompat.findEnchantment("somanyenchantments", "wintersgrace");
        if (wintersGrace == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:wintersgrace! Skipping winters_grace recipe.");
            return;
        }

        ItemStack output = createBook(wintersGrace, wintersGrace.getMaxLevel());
        registerConfigurableRecipe(event, "winters_grace", output,
                "GBG", "BXB", "GCG",
                'G', glowingIngot,
                'B', bucketOoze,
                'C', seasonClock,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Subject Geography V: glowing ingot corners, xp tome center,
    // an empty antique atlas on all four edge slots.
    // ============================================================
    private static void registerSubjectGeographyV(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item emptyAtlas = PackCompat.findItem("antiqueatlas", "empty_antique_atlas");
        if (emptyAtlas == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find antiqueatlas:empty_antique_atlas! Skipping subject_geography_v recipe.");
            return;
        }

        Enchantment subjectGeography = PackCompat.findEnchantment("somanyenchantments", "subjectgeography");
        if (subjectGeography == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:subjectgeography! Skipping subject_geography_v recipe.");
            return;
        }

        ItemStack output = createBook(subjectGeography, subjectGeography.getMaxLevel());
        registerConfigurableRecipe(event, "subject_geography_v", output,
                "GAG", "AXA", "GAG",
                'G', glowingIngot,
                'A', emptyAtlas,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Subject Biology V: glowing ingot corners, xp tome center, a
    // Heart Container on the bottom, Heart Crystal Shards on the
    // remaining edge slots.
    // ============================================================
    private static void registerSubjectBiologyV(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item heartContainer = PackCompat.findItem("scalinghealth", "heartcontainer");
        Item crystalShard = PackCompat.findItem("scalinghealth", "crystalshard");
        if (heartContainer == null || crystalShard == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find scalinghealth:heartcontainer or scalinghealth:crystalshard! Skipping subject_biology_v recipe.");
            return;
        }

        Enchantment subjectBiology = PackCompat.findEnchantment("somanyenchantments", "subjectbiology");
        if (subjectBiology == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:subjectbiology! Skipping subject_biology_v recipe.");
            return;
        }

        ItemStack output = createBook(subjectBiology, subjectBiology.getMaxLevel());
        registerConfigurableRecipe(event, "subject_biology_v", output,
                "GCG", "CXC", "GHG",
                'G', glowingIngot,
                'C', crystalShard,
                'H', heartContainer,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Subject Chemistry V: glowing ingot corners, xp tome center, a
    // Baubles Miner's Ring on the bottom, Lycanites Mobs battle
    // burritos on the remaining edge slots.
    // ============================================================
    private static void registerSubjectChemistryV(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item minersRing = PackCompat.findItem("baubles", "ring");
        Item battleBurrito = PackCompat.findItem("lycanitesmobs", "battle_burrito");
        if (minersRing == null || battleBurrito == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find baubles:ring or lycanitesmobs:battle_burrito! Skipping subject_chemistry_v recipe.");
            return;
        }

        Enchantment subjectChemistry = PackCompat.findEnchantment("somanyenchantments", "subjectchemistry");
        if (subjectChemistry == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:subjectchemistry! Skipping subject_chemistry_v recipe.");
            return;
        }

        ItemStack output = createBook(subjectChemistry, subjectChemistry.getMaxLevel());
        registerConfigurableRecipe(event, "subject_chemistry_v", output,
                "GBG", "BXB", "GRG",
                'G', glowingIngot,
                'B', battleBurrito,
                'R', minersRing,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Subject History V: glowing ingot corners, xp tome center, a
    // clock on the bottom, long potions of slowness on the
    // remaining edge slots.
    // ============================================================
    private static void registerSubjectHistoryV(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Enchantment subjectHistory = PackCompat.findEnchantment("somanyenchantments", "subjecthistory");
        if (subjectHistory == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:subjecthistory! Skipping subject_history_v recipe.");
            return;
        }

        SpecialIngredient longSlowness = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:long_slowness");

        ItemStack output = createBook(subjectHistory, subjectHistory.getMaxLevel());
        registerConfigurableRecipe(event, "subject_history_v", output,
                "GPG", "PXP", "GCG",
                'G', glowingIngot,
                'P', longSlowness,
                'C', Items.CLOCK,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Subject Mathematics V: glowing ingot corners, xp tome center,
    // experience bottles on top/bottom, empty xp tomes (same style
    // as Mortalitas VIII) on the sides.
    // ============================================================
    private static void registerSubjectMathematicsV(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Enchantment subjectMathematics = PackCompat.findEnchantment("somanyenchantments", "subjectmathematics");
        if (subjectMathematics == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:subjectmathematics! Skipping subject_mathematics_v recipe.");
            return;
        }

        ItemStack output = createBook(subjectMathematics, subjectMathematics.getMaxLevel());
        registerConfigurableRecipe(event, "subject_mathematics_v", output,
                "GEG", "TXT", "GEG",
                'G', glowingIngot,
                'E', Items.EXPERIENCE_BOTTLE,
                'T', new EmptyXpTomeIngredient(xpTome),
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Subject P.E. V: glowing gem corners, xp tome center, Rough
    // Tweaks' Enchanted Medikit on top, Bountiful Baubles' Broken
    // Heart on the bottom, Heart Containers on the sides.
    // ============================================================
    private static void registerSubjectPeV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingGem = resolveGlowingGem();
        Item enchantedMedikit = PackCompat.findItem("roughtweaks", "medikitenchanted");
        Item brokenHeart = PackCompat.findItem("bountifulbaubles", "trinketbrokenheart");
        Item heartContainer = PackCompat.findItem("scalinghealth", "heartcontainer");
        if (glowingGem == null || enchantedMedikit == null || brokenHeart == null || heartContainer == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem, roughtweaks:medikitenchanted, bountifulbaubles:trinketbrokenheart, or scalinghealth:heartcontainer! Skipping subject_pe_v recipe.");
            return;
        }

        Enchantment subjectPe = PackCompat.findEnchantment("somanyenchantments", "subjectpe");
        if (subjectPe == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:subjectpe! Skipping subject_pe_v recipe.");
            return;
        }

        ItemStack output = createBook(subjectPe, subjectPe.getMaxLevel());
        registerConfigurableRecipe(event, "subject_pe_v", output,
                "GMG", "HXH", "GBG",
                'G', glowingGem,
                'M', enchantedMedikit,
                'H', heartContainer,
                'B', brokenHeart,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Subject Physics V: glowing ingot corners, xp tome center,
    // Lycanites Mobs raw arisaur meat on top, raw silex meat on the
    // sides, raw maka meat on the bottom.
    // ============================================================
    private static void registerSubjectPhysicsV(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item rawArisaurMeat = PackCompat.findItem("lycanitesmobs", "raw_arisaur_meat");
        Item rawSilexMeat = PackCompat.findItem("lycanitesmobs", "raw_silex_meat");
        Item rawMakaMeat = PackCompat.findItem("lycanitesmobs", "raw_maka_meat");
        if (rawArisaurMeat == null || rawSilexMeat == null || rawMakaMeat == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find lycanitesmobs raw arisaur/silex/maka meat! Skipping subject_physics_v recipe.");
            return;
        }

        Enchantment subjectPhysics = PackCompat.findEnchantment("somanyenchantments", "subjectphysics");
        if (subjectPhysics == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:subjectphysics! Skipping subject_physics_v recipe.");
            return;
        }

        ItemStack output = createBook(subjectPhysics, subjectPhysics.getMaxLevel());
        registerConfigurableRecipe(event, "subject_physics_v", output,
                "GAG", "SXS", "GMG",
                'G', glowingIngot,
                'A', rawArisaurMeat,
                'S', rawSilexMeat,
                'M', rawMakaMeat,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Subject English V (PACK_MODE=rlcraft only): glowing ingot corners, xp
    // tome center, a Quality Tools Emerald Amulet on top, Emerald Rings on the
    // sides, an emerald block on the bottom.
    //
    // Dregora's SoManyEnchantments has no Subject English - it only exists in
    // base RLCraft's 0.5.5, which registers it as plain "English".
    // ============================================================
    private static void registerSubjectEnglishV(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        if (!PackCompat.isRLCraft()) {
            return;
        }

        Item emeraldAmulet = PackCompat.findItem("qualitytools", "emerald_amulet");
        Item emeraldRing = PackCompat.findItem("qualitytools", "emerald_ring");
        Item emeraldBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.EMERALD_BLOCK);
        if (emeraldAmulet == null || emeraldRing == null || emeraldBlock == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find qualitytools:emerald_amulet or qualitytools:emerald_ring! Skipping subject_english_v recipe.");
            return;
        }

        Enchantment subjectEnglish = PackCompat.findEnchantment("somanyenchantments", "English");
        if (subjectEnglish == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:English! Skipping subject_english_v recipe.");
            return;
        }

        ItemStack output = createBook(subjectEnglish, subjectEnglish.getMaxLevel());
        registerConfigurableRecipe(event, "subject_english_v", output,
                "GAG", "RXR", "GBG",
                'G', glowingIngot,
                'A', emeraldAmulet,
                'R', emeraldRing,
                'B', emeraldBlock,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Subject Science V (PACK_MODE=rlcraft only): glowing ingot corners, xp
    // tome center, a creeper head on top, gunpowder on the sides, a TNT block
    // on the bottom.
    //
    // Same as Subject English - base RLCraft only, registered as "Science".
    // ============================================================
    private static void registerSubjectScienceV(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        if (!PackCompat.isRLCraft()) {
            return;
        }

        Item tnt = Item.getItemFromBlock(net.minecraft.init.Blocks.TNT);
        if (tnt == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find the TNT block item! Skipping subject_science_v recipe.");
            return;
        }

        Enchantment subjectScience = PackCompat.findEnchantment("somanyenchantments", "Science");
        if (subjectScience == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:Science! Skipping subject_science_v recipe.");
            return;
        }

        // Items.SKULL damage 4 is the creeper head.
        ItemStack creeperHead = new ItemStack(Items.SKULL, 1, 4);

        ItemStack output = createBook(subjectScience, subjectScience.getMaxLevel());
        registerConfigurableRecipe(event, "subject_science_v", output,
                "GCG", "PXP", "GTG",
                'G', glowingIngot,
                'C', creeperHead,
                'P', Items.GUNPOWDER,
                'T', tnt,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Agility II: glowing ingot corners, xp tome center, a
    // potionfingers Ring of Speed on top, a Tool Belt on bottom,
    // feathers on the sides.
    // ============================================================
    private static void registerAgilityII(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        Item toolBelt = PackCompat.findItem("toolbelt", "belt");
        if (potionRing == null || toolBelt == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find potionfingers:ring or toolbelt:belt! Skipping agility_ii recipe.");
            return;
        }

        Enchantment agility = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "agility");
        if (agility == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:agility! Skipping agility_ii recipe.");
            return;
        }

        SpecialIngredient ringOfSpeed = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:speed");

        ItemStack output = createBook(agility, agility.getMaxLevel());
        registerConfigurableRecipe(event, "agility_ii", output,
                "GRG", "FXF", "GBG",
                'G', glowingIngot,
                'R', ringOfSpeed,
                'F', Items.FEATHER,
                'B', toolBelt,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Arrow Recovery III: glowing ingot corners, xp tome center, a
    // Spartan Weaponry diamond longbow on bottom, diamond arrows on
    // the remaining edge slots.
    // ============================================================
    private static void registerArrowRecoveryIII(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item diamondLongbow = PackCompat.findItem("spartanweaponry", "longbow_diamond");
        Item diamondArrow = PackCompat.findItem("spartanweaponry", "arrow_diamond");
        if (diamondLongbow == null || diamondArrow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:longbow_diamond or spartanweaponry:arrow_diamond! Skipping arrow_recovery_iii recipe.");
            return;
        }

        Enchantment arrowRecovery = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "arrowrecovery");
        if (arrowRecovery == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:arrowrecovery! Skipping arrow_recovery_iii recipe.");
            return;
        }

        ItemStack output = createBook(arrowRecovery, arrowRecovery.getMaxLevel());
        registerConfigurableRecipe(event, "arrow_recovery_iii", output,
                "GAG", "AXA", "GLG",
                'G', glowingIngot,
                'A', diamondArrow,
                'L', diamondLongbow,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Assassinate: glowing ingot corners, xp tome center, a Spartan
    // Weaponry diamond dagger on all four edge-middle slots.
    // ============================================================
    private static void registerAssassinate(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item diamondDagger = PackCompat.findItem("spartanweaponry", "dagger_diamond");
        if (diamondDagger == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:dagger_diamond! Skipping assassinate recipe.");
            return;
        }

        Enchantment assassinate = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "assassinate");
        if (assassinate == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:assassinate! Skipping assassinate recipe.");
            return;
        }

        ItemStack output = createBook(assassinate, assassinate.getMaxLevel());
        registerConfigurableRecipe(event, "assassinate", output,
                "GDG", "DXD", "GDG",
                'G', glowingIngot,
                'D', diamondDagger,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Bash: glowing ingot corners, xp tome center, a Better Survival
    // diamond hammer on all four edge-middle slots.
    // ============================================================
    private static void registerBash(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item diamondHammer = PackCompat.findItem("mujmajnkraftsbettersurvival", "itemdiamondhammer");
        if (diamondHammer == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:itemdiamondhammer! Skipping bash recipe.");
            return;
        }

        Enchantment bash = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "bash");
        if (bash == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:bash! Skipping bash recipe.");
            return;
        }

        ItemStack output = createBook(bash, bash.getMaxLevel());
        registerConfigurableRecipe(event, "bash", output,
                "GHG", "HXH", "GHG",
                'G', glowingIngot,
                'H', diamondHammer,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Blast: glowing ingot corners, xp tome center, a Switch-Bow
    // TNT Arrow on all four edge-middle slots.
    // ============================================================
    private static void registerBlast(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item tntArrow = PackCompat.findItem("switchbow", "arrowtnt");
        if (tntArrow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find switchbow:arrowtnt! Skipping blast recipe.");
            return;
        }

        Enchantment blast = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "blast");
        if (blast == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:blast! Skipping blast recipe.");
            return;
        }

        ItemStack output = createBook(blast, blast.getMaxLevel());
        registerConfigurableRecipe(event, "blast", output,
                "GTG", "TXT", "GTG",
                'G', glowingIngot,
                'T', tntArrow,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Blocking Power: glowing ingot corners, xp tome center, a
    // Spartan Shields diamond tower shield on all four edge-middle
    // slots.
    // ============================================================
    private static void registerBlockingPower(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item diamondTowerShield = PackCompat.findItem("spartanshields", "shield_tower_diamond");
        if (diamondTowerShield == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanshields:shield_tower_diamond! Skipping blocking_power recipe.");
            return;
        }

        Enchantment blockingPower = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "blockpower");
        if (blockingPower == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:blockpower! Skipping blocking_power recipe.");
            return;
        }

        ItemStack output = createBook(blockingPower, blockingPower.getMaxLevel());
        registerConfigurableRecipe(event, "blocking_power", output,
                "GSG", "SXS", "GSG",
                'G', glowingIngot,
                'S', diamondTowerShield,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Diamonds Everywhere: glowing ingot corners, xp tome center,
    // diamond ore on all four edge-middle slots.
    // ============================================================
    private static void registerDiamondsEverywhere(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Enchantment diamondsEverywhere = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "diamonds");
        if (diamondsEverywhere == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:diamonds! Skipping diamonds_everywhere recipe.");
            return;
        }

        Item diamondOre = Item.getItemFromBlock(net.minecraft.init.Blocks.DIAMOND_ORE);

        ItemStack output = createBook(diamondsEverywhere, diamondsEverywhere.getMaxLevel());
        registerConfigurableRecipe(event, "diamonds_everywhere", output,
                "GOG", "OXO", "GOG",
                'G', glowingIngot,
                'O', diamondOre,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Heaviness: glowing ingot corners, xp tome center, a Fishing
    // Made Better Heavy Bobber on all four edge-middle slots.
    // ============================================================
    private static void registerHeaviness(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item heavyBobber = PackCompat.findItem("fishingmadebetter", "bobber_heavy");
        if (heavyBobber == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find fishingmadebetter:bobber_heavy! Skipping heaviness recipe.");
            return;
        }

        Enchantment heaviness = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "heavy");
        if (heaviness == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:heavy! Skipping heaviness recipe.");
            return;
        }

        ItemStack output = createBook(heaviness, heaviness.getMaxLevel());
        registerConfigurableRecipe(event, "heaviness", output,
                "GBG", "BXB", "GBG",
                'G', glowingIngot,
                'B', heavyBobber,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Range: glowing ingot corners, xp tome center, a Spartan
    // Weaponry diamond arrow on bottom, redstone blocks on the
    // remaining edge slots.
    // ============================================================
    private static void registerRange(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item diamondArrow = PackCompat.findItem("spartanweaponry", "arrow_diamond");
        if (diamondArrow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:arrow_diamond! Skipping range recipe.");
            return;
        }

        Enchantment range = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "range");
        if (range == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:range! Skipping range recipe.");
            return;
        }

        Item redstoneBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.REDSTONE_BLOCK);

        ItemStack output = createBook(range, range.getMaxLevel());
        registerConfigurableRecipe(event, "range", output,
                "GRG", "RXR", "GAG",
                'G', glowingIngot,
                'R', redstoneBlock,
                'A', diamondArrow,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Rapid Fire: glowing ingot corners, xp tome center, a Spartan
    // Weaponry diamond arrow on bottom, Potions of Swiftness II on
    // the remaining edge slots.
    // ============================================================
    private static void registerRapidFire(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item diamondArrow = PackCompat.findItem("spartanweaponry", "arrow_diamond");
        if (diamondArrow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:arrow_diamond! Skipping rapid_fire recipe.");
            return;
        }

        Enchantment rapidFire = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "rapidfire");
        if (rapidFire == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:rapidfire! Skipping rapid_fire recipe.");
            return;
        }

        SpecialIngredient swiftnessII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:strong_swiftness");

        ItemStack output = createBook(rapidFire, rapidFire.getMaxLevel());
        registerConfigurableRecipe(event, "rapid_fire", output,
                "GSG", "SXS", "GAG",
                'G', glowingIngot,
                'S', swiftnessII,
                'A', diamondArrow,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Reflection: glowing ingot corners, a Thorns III book in the
    // center (no xp tome), a Spartan Shields diamond reinforced
    // shield on all four edge-middle slots.
    // ============================================================
    private static void registerReflection(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item diamondShield = PackCompat.findItem("spartanshields", "shield_basic_diamond");
        if (diamondShield == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanshields:shield_basic_diamond! Skipping reflection recipe.");
            return;
        }

        Enchantment reflection = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "reflection");
        if (reflection == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:reflection! Skipping reflection recipe.");
            return;
        }

        ItemStack output = createBook(reflection, reflection.getMaxLevel());
        registerConfigurableRecipe(event, "reflection", output,
                "GSG", "SXS", "GSG",
                'G', glowingIngot,
                'S', diamondShield,
                'X', new EnchantedBookIngredient(Enchantments.THORNS, 3));
    }

    // ============================================================
    // Spikes: glowing ingot corners, a Thorns III book in the
    // center (no xp tome), a Spartan Shields diamond tower shield
    // on all four edge-middle slots.
    // ============================================================
    private static void registerSpikes(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item diamondTowerShield = PackCompat.findItem("spartanshields", "shield_tower_diamond");
        if (diamondTowerShield == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanshields:shield_tower_diamond! Skipping spikes recipe.");
            return;
        }

        Enchantment spikes = PackCompat.findEnchantment("spartanshields", "spikes");
        if (spikes == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanshields:spikes! Skipping spikes recipe.");
            return;
        }

        ItemStack output = createBook(spikes, spikes.getMaxLevel());
        registerConfigurableRecipe(event, "spikes", output,
                "GSG", "SXS", "GSG",
                'G', glowingIngot,
                'S', diamondTowerShield,
                'X', new EnchantedBookIngredient(Enchantments.THORNS, 3));
    }

    // ============================================================
    // Spellproof: glowing ingot corners, xp tome center, Mana Candy
    // on top, eyes of ender on the sides, a Charm ender pearl block
    // on bottom.
    // ============================================================
    private static void registerSpellproof(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item manaCandy = PackCompat.findItem("xat", "mana_candy");
        net.minecraft.block.Block enderPearlBlockBlock = net.minecraft.block.Block.REGISTRY.getObject(new ResourceLocation("charm", "ender_pearl_block"));
        if (manaCandy == null || enderPearlBlockBlock == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:mana_candy or charm:ender_pearl_block! Skipping spellproof recipe.");
            return;
        }
        Item enderPearlBlock = Item.getItemFromBlock(enderPearlBlockBlock);

        Enchantment spellproof = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "spellshield");
        if (spellproof == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:spellshield! Skipping spellproof recipe.");
            return;
        }

        ItemStack output = createBook(spellproof, spellproof.getMaxLevel());
        registerConfigurableRecipe(event, "spellproof", output,
                "GMG", "EXE", "GBG",
                'G', glowingIngot,
                'M', manaCandy,
                'E', Items.ENDER_EYE,
                'B', enderPearlBlock,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Tunneling: glowing ingot corners, xp tome center, diamond
    // pickaxes on all four edge-middle slots.
    // ============================================================
    private static void registerTunneling(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Enchantment tunneling = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "tunneling");
        if (tunneling == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:tunneling! Skipping tunneling recipe.");
            return;
        }

        ItemStack output = createBook(tunneling, tunneling.getMaxLevel());
        registerConfigurableRecipe(event, "tunneling", output,
                "GPG", "PXP", "GPG",
                'G', glowingIngot,
                'P', Items.DIAMOND_PICKAXE,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Versatility: glowing ingot corners, xp tome center, a diamond
    // pickaxe on top, a diamond axe on left, a diamond shovel on
    // right, a diamond hoe on bottom.
    // ============================================================
    private static void registerVersatility(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Enchantment versatility = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "versatility");
        if (versatility == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:versatility! Skipping versatility recipe.");
            return;
        }

        ItemStack output = createBook(versatility, versatility.getMaxLevel());
        registerConfigurableRecipe(event, "versatility", output,
                "GPG", "AXS", "GHG",
                'G', glowingIngot,
                'P', Items.DIAMOND_PICKAXE,
                'A', Items.DIAMOND_AXE,
                'S', Items.DIAMOND_SHOVEL,
                'H', Items.DIAMOND_HOE,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Weightless: glowing ingot corners, xp tome center, a Spartan
    // Shields diamond reinforced shield on the sides, Potions of
    // Swiftness II on top/bottom.
    // ============================================================
    private static void registerWeightless(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item diamondShield = PackCompat.findItem("spartanshields", "shield_basic_diamond");
        if (diamondShield == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanshields:shield_basic_diamond! Skipping weightless recipe.");
            return;
        }

        Enchantment weightless = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "weightless");
        if (weightless == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:weightless! Skipping weightless recipe.");
            return;
        }

        SpecialIngredient swiftnessII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:strong_swiftness");

        ItemStack output = createBook(weightless, weightless.getMaxLevel());
        registerConfigurableRecipe(event, "weightless", output,
                "GSG", "DXD", "GSG",
                'G', glowingIngot,
                'S', swiftnessII,
                'D', diamondShield,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // High Jump: glowing ingot corners, xp tome center, a Potion of
    // Levitation II on the remaining edge slots, a Bountiful
    // Baubles Balloon on bottom. Vanilla only registers a single
    // flat Levitation PotionType (no brewable amplified tier), so
    // the level II input is matched via CustomPotionEffects NBT.
    // ============================================================
    private static void registerHighJump(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item balloon = PackCompat.findItem("bountifulbaubles", "trinketballoon");
        if (balloon == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find bountifulbaubles:trinketballoon! Skipping high_jump recipe.");
            return;
        }

        Enchantment highJump = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "highjump");
        if (highJump == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:highjump! Skipping high_jump recipe.");
            return;
        }

        SpecialIngredient levitationII = new CustomPotionEffectIngredient(net.minecraft.init.MobEffects.LEVITATION, 1);

        ItemStack output = createBook(highJump, highJump.getMaxLevel());
        registerConfigurableRecipe(event, "high_jump", output,
                "GLG", "LXL", "GBG",
                'G', glowingIngot,
                'L', levitationII,
                'B', balloon,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Expanse: glowing ingot corners, plain book center, a Better
    // Survival diamond dagger on top, a Switch-Bow Quiver on the
    // sides, a Spartan Weaponry Heavy Arrow Quiver on bottom.
    // ============================================================
    private static void registerExpanse(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item diamondDagger = PackCompat.findItem("mujmajnkraftsbettersurvival", "itemdiamonddagger");
        Item switchbowQuiver = PackCompat.findItem("switchbow", "quiver");
        Item heavyArrowQuiver = PackCompat.findItem("spartanweaponry", "quiver_arrow_heavy");
        if (diamondDagger == null || switchbowQuiver == null || heavyArrowQuiver == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:itemdiamonddagger, switchbow:quiver, or spartanweaponry:quiver_arrow_heavy! Skipping expanse recipe.");
            return;
        }

        Enchantment expanse = PackCompat.findEnchantment("spartanweaponry", "expanse");
        if (expanse == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:expanse! Skipping expanse recipe.");
            return;
        }

        ItemStack output = createBook(expanse, expanse.getMaxLevel());
        registerConfigurableRecipe(event, "expanse", output,
                "GDG", "QXQ", "GHG",
                'G', glowingIngot,
                'D', diamondDagger,
                'Q', switchbowQuiver,
                'H', heavyArrowQuiver,
                'X', Items.BOOK);
    }

    // ============================================================
    // Hydrodynamic: glowing ingot corners, an Aqua Affinity book in
    // the center, a Better Survival diamond dagger on all four
    // edge-middle slots.
    // ============================================================
    private static void registerHydrodynamic(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item diamondDagger = PackCompat.findItem("mujmajnkraftsbettersurvival", "itemdiamonddagger");
        if (diamondDagger == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:itemdiamonddagger! Skipping hydrodynamic recipe.");
            return;
        }

        Enchantment hydrodynamic = PackCompat.findEnchantment("spartanweaponry", "hydrodynamic");
        if (hydrodynamic == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:hydrodynamic! Skipping hydrodynamic recipe.");
            return;
        }

        ItemStack output = createBook(hydrodynamic, hydrodynamic.getMaxLevel());
        registerConfigurableRecipe(event, "hydrodynamic", output,
                "GDG", "DXD", "GDG",
                'G', glowingIngot,
                'D', diamondDagger,
                'X', new EnchantedBookIngredient(Enchantments.AQUA_AFFINITY, 1));
    }

    // ============================================================
    // Incendiary: glowing ingot corners, a Fire Aspect II book in
    // the center, a Better Survival diamond dagger on all four
    // edge-middle slots.
    // ============================================================
    private static void registerIncendiary(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item diamondDagger = PackCompat.findItem("mujmajnkraftsbettersurvival", "itemdiamonddagger");
        if (diamondDagger == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:itemdiamonddagger! Skipping incendiary recipe.");
            return;
        }

        Enchantment incendiary = PackCompat.findEnchantment("spartanweaponry", "incendiary");
        if (incendiary == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:incendiary! Skipping incendiary recipe.");
            return;
        }

        ItemStack output = createBook(incendiary, incendiary.getMaxLevel());
        registerConfigurableRecipe(event, "incendiary", output,
                "GDG", "DXD", "GDG",
                'G', glowingIngot,
                'D', diamondDagger,
                'X', new EnchantedBookIngredient(Enchantments.FIRE_ASPECT, 2));
    }

    // ============================================================
    // Lucky Throw: glowing ingot corners, a Looting III book in the
    // center, a Better Survival diamond dagger on all four
    // edge-middle slots.
    // ============================================================
    private static void registerLuckyThrow(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item diamondDagger = PackCompat.findItem("mujmajnkraftsbettersurvival", "itemdiamonddagger");
        if (diamondDagger == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:itemdiamonddagger! Skipping lucky_throw recipe.");
            return;
        }

        Enchantment luckyThrow = PackCompat.findEnchantment("spartanweaponry", "lucky_throw");
        if (luckyThrow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:lucky_throw! Skipping lucky_throw recipe.");
            return;
        }

        ItemStack output = createBook(luckyThrow, luckyThrow.getMaxLevel());
        registerConfigurableRecipe(event, "lucky_throw", output,
                "GDG", "DXD", "GDG",
                'G', glowingIngot,
                'D', diamondDagger,
                'X', new EnchantedBookIngredient(Enchantments.LOOTING, 3));
    }

    // ============================================================
    // Propulsion: glowing ingot corners, a Punch II book in the
    // center, a Better Survival diamond dagger on all four
    // edge-middle slots.
    // ============================================================
    private static void registerPropulsion(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item diamondDagger = PackCompat.findItem("mujmajnkraftsbettersurvival", "itemdiamonddagger");
        if (diamondDagger == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:itemdiamonddagger! Skipping propulsion recipe.");
            return;
        }

        Enchantment propulsion = PackCompat.findEnchantment("spartanweaponry", "propulsion");
        if (propulsion == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:propulsion! Skipping propulsion recipe.");
            return;
        }

        ItemStack output = createBook(propulsion, propulsion.getMaxLevel());
        registerConfigurableRecipe(event, "propulsion", output,
                "GDG", "DXD", "GDG",
                'G', glowingIngot,
                'D', diamondDagger,
                'X', new EnchantedBookIngredient(Enchantments.PUNCH, 2));
    }

    // ============================================================
    // Rapid Load III: glowing ingot corners, xp tome center, a
    // Spartan Weaponry Diamond-Strengthened Crossbow on all four
    // edge-middle slots.
    // ============================================================
    private static void registerRapidLoadIII(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item diamondCrossbow = PackCompat.findItem("spartanweaponry", "crossbow_diamond");
        if (diamondCrossbow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:crossbow_diamond! Skipping rapid_load_iii recipe.");
            return;
        }

        Enchantment rapidLoad = PackCompat.findEnchantment("spartanweaponry", "rapid_load");
        if (rapidLoad == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:rapid_load! Skipping rapid_load_iii recipe.");
            return;
        }

        ItemStack output = createBook(rapidLoad, rapidLoad.getMaxLevel());
        registerConfigurableRecipe(event, "rapid_load_iii", output,
                "GCG", "CXC", "GCG",
                'G', glowingIngot,
                'C', diamondCrossbow,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Razor's Edge: glowing ingot corners, plain book center, a
    // Better Survival diamond dagger on all four edge-middle slots.
    // ============================================================
    private static void registerRazorsEdge(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item diamondDagger = PackCompat.findItem("mujmajnkraftsbettersurvival", "itemdiamonddagger");
        if (diamondDagger == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:itemdiamonddagger! Skipping razors_edge recipe.");
            return;
        }

        Enchantment razorsEdge = PackCompat.findEnchantment("spartanweaponry", "razors_edge");
        if (razorsEdge == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:razors_edge! Skipping razors_edge recipe.");
            return;
        }

        ItemStack output = createBook(razorsEdge, razorsEdge.getMaxLevel());
        registerConfigurableRecipe(event, "razors_edge", output,
                "GDG", "DXD", "GDG",
                'G', glowingIngot,
                'D', diamondDagger,
                'X', Items.BOOK);
    }

    // ============================================================
    // Return: glowing ingot corners, a Mending book in the center,
    // a Better Survival diamond dagger on all four edge-middle
    // slots.
    // ============================================================
    private static void registerReturn(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item diamondDagger = PackCompat.findItem("mujmajnkraftsbettersurvival", "itemdiamonddagger");
        if (diamondDagger == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:itemdiamonddagger! Skipping return recipe.");
            return;
        }

        Enchantment returnEnch = PackCompat.findEnchantment("spartanweaponry", "return");
        if (returnEnch == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:return! Skipping return recipe.");
            return;
        }

        ItemStack output = createBook(returnEnch, returnEnch.getMaxLevel());
        registerConfigurableRecipe(event, "return", output,
                "GDG", "DXD", "GDG",
                'G', glowingIngot,
                'D', diamondDagger,
                'X', new EnchantedBookIngredient(Enchantments.MENDING, 1));
    }

    // ============================================================
    // Spreadshot: glowing ingot corners, plain book center, Spartan
    // Weaponry diamond bolts on the sides, a Heavy Bolt Quiver on
    // bottom.
    // ============================================================
    private static void registerSpreadshot(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item diamondBolt = PackCompat.findItem("spartanweaponry", "bolt_diamond");
        Item heavyBoltQuiver = PackCompat.findItem("spartanweaponry", "quiver_bolt_heavy");
        if (diamondBolt == null || heavyBoltQuiver == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:bolt_diamond or spartanweaponry:quiver_bolt_heavy! Skipping spreadshot recipe.");
            return;
        }

        Enchantment spreadshot = PackCompat.findEnchantment("spartanweaponry", "spreadshot");
        if (spreadshot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:spreadshot! Skipping spreadshot recipe.");
            return;
        }

        ItemStack output = createBook(spreadshot, spreadshot.getMaxLevel());
        registerConfigurableRecipe(event, "spreadshot", output,
                "GBG", "BXB", "GHG",
                'G', glowingIngot,
                'B', diamondBolt,
                'H', heavyBoltQuiver,
                'X', Items.BOOK);
    }

    // ============================================================
    // Supercharge: glowing ingot corners, a Power V book in the
    // center, a Better Survival diamond dagger on all four
    // edge-middle slots.
    // ============================================================
    private static void registerSupercharge(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item diamondDagger = PackCompat.findItem("mujmajnkraftsbettersurvival", "itemdiamonddagger");
        if (diamondDagger == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:itemdiamonddagger! Skipping supercharge recipe.");
            return;
        }

        Enchantment supercharge = PackCompat.findEnchantment("spartanweaponry", "supercharge");
        if (supercharge == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:supercharge! Skipping supercharge recipe.");
            return;
        }

        ItemStack output = createBook(supercharge, supercharge.getMaxLevel());
        registerConfigurableRecipe(event, "supercharge", output,
                "GDG", "DXD", "GDG",
                'G', glowingIngot,
                'D', diamondDagger,
                'X', new EnchantedBookIngredient(Enchantments.POWER, 5));
    }

    // ============================================================
    // Sharpshooter (Spartan Weaponry): glowing ingot corners, xp
    // tome center, a potionfingers Ring of Speed on bottom, Spartan
    // Weaponry diamond bolts on the remaining edge slots.
    // ============================================================
    private static void registerSharpshooter(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item diamondBolt = PackCompat.findItem("spartanweaponry", "bolt_diamond");
        Item potionRing = PackCompat.findItem("potionfingers", "ring");
        if (diamondBolt == null || potionRing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:bolt_diamond or potionfingers:ring! Skipping sharpshooter recipe.");
            return;
        }

        Enchantment sharpshooter = PackCompat.findEnchantment("spartanweaponry", "sharpshooter");
        if (sharpshooter == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find spartanweaponry:sharpshooter! Skipping sharpshooter recipe.");
            return;
        }

        SpecialIngredient ringOfSpeed = new NbtStringTagIngredient(potionRing, 1, "effect", "minecraft:speed");

        ItemStack output = createBook(sharpshooter, sharpshooter.getMaxLevel());
        registerConfigurableRecipe(event, "sharpshooter", output,
                "GBG", "BXB", "GRG",
                'G', glowingIngot,
                'B', diamondBolt,
                'R', ringOfSpeed,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Economical: glowing ingot corners, xp tome center, a Defiled
    // Lands Scuttler Eye on bottom, Remorseful Essence on the
    // remaining edge slots.
    // ============================================================
    private static void registerEconomical(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item scuttlerEye = PackCompat.findItem("defiledlands", "scuttler_eye");
        Item remorsefulEssence = PackCompat.findItem("defiledlands", "essence_mourner");
        if (scuttlerEye == null || remorsefulEssence == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find defiledlands:scuttler_eye or defiledlands:essence_mourner! Skipping economical recipe.");
            return;
        }

        Enchantment economical = PackCompat.findEnchantment("defiledlands", "economical");
        if (economical == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find defiledlands:economical! Skipping economical recipe.");
            return;
        }

        ItemStack output = createBook(economical, economical.getMaxLevel());
        registerConfigurableRecipe(event, "economical", output,
                "GEG", "EXE", "GSG",
                'G', glowingIngot,
                'E', remorsefulEssence,
                'S', scuttlerEye,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Destructive V: glowing ingot corners, xp tome center, Defiled
    // Lands Scarlite on top, Umbrium Ingots on the sides, a Black
    // Heart on bottom.
    // ============================================================
    private static void registerDestructiveV(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item scarlite = PackCompat.findItem("defiledlands", "scarlite");
        Item umbriumIngot = PackCompat.findItem("defiledlands", "umbrium_ingot");
        Item blackHeart = PackCompat.findItem("defiledlands", "black_heart");
        if (scarlite == null || umbriumIngot == null || blackHeart == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find defiledlands:scarlite, defiledlands:umbrium_ingot, or defiledlands:black_heart! Skipping destructive_v recipe.");
            return;
        }

        Enchantment destructive = PackCompat.findEnchantment("defiledlands", "destructive");
        if (destructive == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find defiledlands:destructive! Skipping destructive_v recipe.");
            return;
        }

        ItemStack output = createBook(destructive, destructive.getMaxLevel());
        registerConfigurableRecipe(event, "destructive_v", output,
                "GSG", "UXU", "GBG",
                'G', glowingIngot,
                'S', scarlite,
                'U', umbriumIngot,
                'B', blackHeart,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Safeguard: glowing ingot corners, xp tome center, Defiled
    // Lands Scarlite on top, Umbrium Ingots on the sides, a
    // Phytoprostasia Amulet on bottom.
    // ============================================================
    private static void registerSafeguard(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item scarlite = PackCompat.findItem("defiledlands", "scarlite");
        Item umbriumIngot = PackCompat.findItem("defiledlands", "umbrium_ingot");
        Item phytoprostasiaAmulet = PackCompat.findItem("defiledlands", "phytoprostasia_amulet");
        if (scarlite == null || umbriumIngot == null || phytoprostasiaAmulet == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find defiledlands:scarlite, defiledlands:umbrium_ingot, or defiledlands:phytoprostasia_amulet! Skipping safeguard recipe.");
            return;
        }

        Enchantment safeguard = PackCompat.findEnchantment("defiledlands", "safeguard");
        if (safeguard == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find defiledlands:safeguard! Skipping safeguard recipe.");
            return;
        }

        ItemStack output = createBook(safeguard, safeguard.getMaxLevel());
        registerConfigurableRecipe(event, "safeguard", output,
                "GSG", "UXU", "GPG",
                'G', glowingIngot,
                'S', scarlite,
                'U', umbriumIngot,
                'P', phytoprostasiaAmulet,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Blazing: glowing ingot corners, xp tome center, Defiled Lands
    // Cooked Book Wyrm on all four edge-middle slots.
    // ============================================================
    private static void registerBlazing(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item cookedBookWyrm = PackCompat.findItem("defiledlands", "book_wyrm_cooked");
        if (cookedBookWyrm == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find defiledlands:book_wyrm_cooked! Skipping blazing recipe.");
            return;
        }

        Enchantment blazing = PackCompat.findEnchantment("defiledlands", "blazing");
        if (blazing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find defiledlands:blazing! Skipping blazing recipe.");
            return;
        }

        ItemStack output = createBook(blazing, blazing.getMaxLevel());
        registerConfigurableRecipe(event, "blazing", output,
                "GBG", "BXB", "GBG",
                'G', glowingIngot,
                'B', cookedBookWyrm,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Curse Break: glowing ingot corners, xp tome center, any
    // enchanted book carrying a curse on all four edge-middle slots.
    // ============================================================
    private static void registerCurseBreak(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Enchantment curseBreak = PackCompat.findEnchantment("charm", "curse_break");
        if (curseBreak == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find charm:curse_break! Skipping curse_break recipe.");
            return;
        }

        ItemStack output = createBook(curseBreak, curseBreak.getMaxLevel());
        registerConfigurableRecipe(event, "curse_break", output,
                "GCG", "CXC", "GCG",
                'G', glowingIngot,
                'C', new AnyCurseBookIngredient(),
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Homing: glowing powder corners, xp tome center, a carrot on a
    // stick on bottom, diamond hoes on the remaining edge slots.
    // ============================================================
    private static void registerHoming(RegistryEvent.Register<IRecipe> event, Item glowingPowder, Item xpTome) {
        Enchantment homing = PackCompat.findEnchantment("charm", "homing");
        if (homing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find charm:homing! Skipping homing recipe.");
            return;
        }

        ItemStack output = createBook(homing, homing.getMaxLevel());
        registerConfigurableRecipe(event, "homing", output,
                "GHG", "HXH", "GCG",
                'G', glowingPowder,
                'H', Items.DIAMOND_HOE,
                'C', Items.CARROT_ON_A_STICK,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Scope: glowing ingot corners, xp tome center, an Ice and Fire
    // Blindfold on all four edge-middle slots.
    // ============================================================
    private static void registerScope(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item blindfold = PackCompat.findItem("iceandfire", "blindfold");
        if (blindfold == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find iceandfire:blindfold! Skipping scope recipe.");
            return;
        }

        Enchantment scope = PackCompat.findEnchantment("switchbow", "activeScope");
        if (scope == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find switchbow:activeScope! Skipping scope recipe.");
            return;
        }

        ItemStack output = createBook(scope, scope.getMaxLevel());
        registerConfigurableRecipe(event, "scope", output,
                "GBG", "BXB", "GBG",
                'G', glowingIngot,
                'B', blindfold,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Pull-Speed: glowing ingot corners, xp tome center, a
    // Switch-Bow Quiver on the sides, a Switch-Bow on top, a Potion
    // of Swiftness II on bottom.
    // ============================================================
    private static void registerPullSpeed(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item switchbowQuiver = PackCompat.findItem("switchbow", "quiver");
        Item switchbow = PackCompat.findItem("switchbow", "switchbow");
        if (switchbowQuiver == null || switchbow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find switchbow:quiver or switchbow:switchbow! Skipping pull_speed recipe.");
            return;
        }

        Enchantment pullSpeed = PackCompat.findEnchantment("switchbow", "pullSpeed");
        if (pullSpeed == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find switchbow:pullSpeed! Skipping pull_speed recipe.");
            return;
        }

        SpecialIngredient swiftnessII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:strong_swiftness");

        ItemStack output = createBook(pullSpeed, pullSpeed.getMaxLevel());
        registerConfigurableRecipe(event, "pull_speed", output,
                "GSG", "QXQ", "GPG",
                'G', glowingIngot,
                'S', switchbow,
                'Q', switchbowQuiver,
                'P', swiftnessII,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Reduce Cooldown: glowing ingot corners, xp tome center, a
    // Switch-Bow Quiver on the sides, a Switch-Crossbow on top, a
    // Potion of Swiftness II on bottom.
    // ============================================================
    private static void registerReduceCooldown(RegistryEvent.Register<IRecipe> event, Item glowingIngot, Item xpTome) {
        Item switchbowQuiver = PackCompat.findItem("switchbow", "quiver");
        Item switchCrossbow = PackCompat.findItem("switchbow", "switchcrossbow");
        if (switchbowQuiver == null || switchCrossbow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find switchbow:quiver or switchbow:switchcrossbow! Skipping reduce_cooldown recipe.");
            return;
        }

        Enchantment reduceCooldown = PackCompat.findEnchantment("switchbow", "cooldownReduce");
        if (reduceCooldown == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find switchbow:cooldownReduce! Skipping reduce_cooldown recipe.");
            return;
        }

        SpecialIngredient swiftnessII = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:strong_swiftness");

        ItemStack output = createBook(reduceCooldown, reduceCooldown.getMaxLevel());
        registerConfigurableRecipe(event, "reduce_cooldown", output,
                "GCG", "QXQ", "GPG",
                'G', glowingIngot,
                'C', switchCrossbow,
                'Q', switchbowQuiver,
                'P', swiftnessII,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Magnetic (Charm): glowing powder corners, xp tome center, a
    // Fishing Made Better Magnetic Hook on all four edge-middle
    // slots.
    // ============================================================
    private static void registerMagnetic(RegistryEvent.Register<IRecipe> event, Item glowingPowder, Item xpTome) {
        Item magneticHook = PackCompat.findItem("fishingmadebetter", "hook_magnetic");
        if (magneticHook == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find fishingmadebetter:hook_magnetic! Skipping magnetic recipe.");
            return;
        }

        Enchantment magnetic = PackCompat.findEnchantment("charm", "magnetic");
        if (magnetic == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find charm:magnetic! Skipping magnetic recipe.");
            return;
        }

        ItemStack output = createBook(magnetic, magnetic.getMaxLevel());
        registerConfigurableRecipe(event, "magnetic", output,
                "GHG", "HXH", "GHG",
                'G', glowingPowder,
                'H', magneticHook,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Curse books: cheap, deliberately off-theme from the rest of the
    // project's glowing-material recipes. Glowing powder corners, plain
    // book center, a Fish's Undead Rising Curseweave Fabric on top/bottom,
    // a curse-specific cheap material on the sides.
    // ============================================================
    private static void registerCurses(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        // Fish's Undead Rising isn't in base RLCraft at all, so there is no
        // Curseweave Fabric to build the top/bottom slots from. In rlcraft mode
        // the recipes stay available and those slots fall back to the same
        // curse-specific material already on the sides (rotten flesh, bone, ...),
        // handled in registerCurseRecipe. In dregora mode a missing fabric still
        // means something is wrong with the install, so skip as before.
        Item curseweaveFabric = PackCompat.findItem("mod_lavacow", "cursed_fabric");
        if (curseweaveFabric == null && !PackCompat.isRLCraft()) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mod_lavacow:cursed_fabric! Skipping curse book recipes.");
            return;
        }

        Item iceChunk = PackCompat.findItem("simpledifficulty", "ice_chunk");
        if (iceChunk == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find simpledifficulty:ice_chunk! Skipping curse_of_clumsiness recipe.");
        } else {
            registerCurseRecipe(event, "curse_of_clumsiness", glowingPowder, curseweaveFabric,
                    PackCompat.findEnchantment("charm", "clumsiness_curse"), iceChunk);
        }

        registerCurseRecipe(event, "curse_of_binding", glowingPowder, curseweaveFabric,
                Enchantments.BINDING_CURSE, Items.ROTTEN_FLESH);
        registerCurseRecipe(event, "curse_of_vanishing", glowingPowder, curseweaveFabric,
                Enchantments.VANISHING_CURSE, Items.BONE);
        registerCurseRecipe(event, "curse_of_rusting", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("charm", "rusting_curse"), Items.IRON_INGOT);
        registerCurseRecipe(event, "curse_of_haunting", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("charm", "haunting_curse"), Items.STRING);
        registerCurseRecipe(event, "curse_of_harming", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("charm", "harming_curse"), Items.SPIDER_EYE);
        registerCurseRecipe(event, "cursed_edge_v", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "cursededge"), Items.IRON_SWORD);
        registerCurseRecipe(event, "curse_of_decay", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "curseofdecay"), Items.GUNPOWDER);
        registerCurseRecipe(event, "curse_of_holding", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "curseofholding"), Items.GLOWSTONE_DUST);
        registerCurseRecipe(event, "curse_of_inaccuracy", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "curseofinaccuracy"), Items.REDSTONE);
        registerCurseRecipe(event, "curse_of_vulnerability", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "curseofvulnerability"), Items.LEATHER);

        Item copperIngot = PackCompat.findItem("iceandfire", "copper_ingot");
        if (copperIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find iceandfire:copper_ingot! Skipping bluntness recipe.");
        } else {
            registerCurseRecipe(event, "bluntness", glowingPowder, curseweaveFabric,
                    PackCompat.findEnchantment("somanyenchantments", "bluntness"), copperIngot);
        }

        Item diamondCoin = PackCompat.findItem("variedcommodities", "coin_diamond");
        if (diamondCoin == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find variedcommodities:coin_diamond! Skipping instability recipe.");
        } else {
            registerCurseRecipe(event, "instability", glowingPowder, curseweaveFabric,
                    PackCompat.findEnchantment("somanyenchantments", "instability"), diamondCoin);
        }

        SpecialIngredient waterBottle = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "minecraft:water");
        Item obsidian = Item.getItemFromBlock(net.minecraft.init.Blocks.OBSIDIAN);
        Item cobblestone = Item.getItemFromBlock(net.minecraft.init.Blocks.COBBLESTONE);
        Item tnt = Item.getItemFromBlock(net.minecraft.init.Blocks.TNT);

        registerCurseRecipe(event, "ascetic", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "ascetic"), Items.QUARTZ);
        registerCurseRecipe(event, "breached_plating", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "breachedplating"), Items.GOLD_INGOT);
        registerCurseRecipe(event, "dragging", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "dragging"), Items.SLIME_BALL);
        registerCurseRecipe(event, "extinguish", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "extinguish"), waterBottle);
        registerCurseRecipe(event, "heavy_weight", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "heavyweight"), obsidian);
        registerCurseRecipe(event, "inefficient", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "inefficient"), cobblestone);
        registerCurseRecipe(event, "meltdown", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "meltdown"), tnt);
        registerCurseRecipe(event, "powerless", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "powerless"), Items.ARROW);
        registerCurseRecipe(event, "rusted", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "rusted"), Items.IRON_NUGGET);
        registerCurseRecipe(event, "unpredictable", glowingPowder, curseweaveFabric,
                PackCompat.findEnchantment("somanyenchantments", "unpredictable"), Items.FLINT);
    }

    private static void registerCurseRecipe(RegistryEvent.Register<IRecipe> event, String id, Item glowingPowder,
                                             Item curseweaveFabric, Enchantment enchantment, Object themedMaterial) {
        if (enchantment == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find enchantment for curse recipe " + id + "! Skipping.");
            return;
        }

        // Without Curseweave Fabric the top/bottom slots take the recipe's own
        // themed material, so the shape is preserved and the cost stays in the
        // same spirit - four of the curse's material instead of two plus fabric.
        Object fabricSlot = curseweaveFabric != null ? curseweaveFabric : themedMaterial;

        ItemStack output = createBook(enchantment, enchantment.getMaxLevel());
        registerConfigurableRecipe(event, id, output,
                "GFG", "TXT", "GFG",
                'G', glowingPowder,
                'F', fabricSlot,
                'T', themedMaterial,
                'X', Items.BOOK);
    }

    // ============================================================
    // Complexity III: glowing powder corners, plain book center,
    // Locks Steel Lock Mechanisms on the sides, Diamond Plated
    // Locks on top/bottom.
    // ============================================================
    private static void registerComplexityIII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item steelLockMechanism = PackCompat.findItem("locks", "steel_lock_mechanism");
        Item diamondLock = PackCompat.findItem("locks", "diamond_lock");
        if (steelLockMechanism == null || diamondLock == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find locks:steel_lock_mechanism or locks:diamond_lock! Skipping complexity_iii recipe.");
            return;
        }

        Enchantment complexity = PackCompat.findEnchantment("locks", "complexity");
        if (complexity == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find locks:complexity! Skipping complexity_iii recipe.");
            return;
        }

        ItemStack output = createBook(complexity, complexity.getMaxLevel());
        registerConfigurableRecipe(event, "complexity_iii", output,
                "GDG", "SXS", "GDG",
                'G', glowingPowder,
                'D', diamondLock,
                'S', steelLockMechanism,
                'X', Items.BOOK);
    }

    // ============================================================
    // Sturdy III: glowing powder corners, plain book center, Locks
    // Diamond Plated Locks on the sides, Steel Lock Mechanisms on
    // top/bottom.
    // ============================================================
    private static void registerSturdyIII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item steelLockMechanism = PackCompat.findItem("locks", "steel_lock_mechanism");
        Item diamondLock = PackCompat.findItem("locks", "diamond_lock");
        if (steelLockMechanism == null || diamondLock == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find locks:steel_lock_mechanism or locks:diamond_lock! Skipping sturdy_iii recipe.");
            return;
        }

        Enchantment sturdy = PackCompat.findEnchantment("locks", "sturdy");
        if (sturdy == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find locks:sturdy! Skipping sturdy_iii recipe.");
            return;
        }

        ItemStack output = createBook(sturdy, sturdy.getMaxLevel());
        registerConfigurableRecipe(event, "sturdy_iii", output,
                "GSG", "DXD", "GSG",
                'G', glowingPowder,
                'S', steelLockMechanism,
                'D', diamondLock,
                'X', Items.BOOK);
    }

    // ============================================================
    // Shocking V: glowing powder corners, plain book center, a
    // Defiled Lands Concussion Smasher on the sides, Locks Diamond
    // Plated Locks on top/bottom.
    // ============================================================
    private static void registerShockingV(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item concussionSmasher = PackCompat.findItem("defiledlands", "concussion_smasher");
        Item diamondLock = PackCompat.findItem("locks", "diamond_lock");
        if (concussionSmasher == null || diamondLock == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find defiledlands:concussion_smasher or locks:diamond_lock! Skipping shocking_v recipe.");
            return;
        }

        Enchantment shocking = PackCompat.findEnchantment("locks", "shocking");
        if (shocking == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find locks:shocking! Skipping shocking_v recipe.");
            return;
        }

        ItemStack output = createBook(shocking, shocking.getMaxLevel());
        registerConfigurableRecipe(event, "shocking_v", output,
                "GDG", "CXC", "GDG",
                'G', glowingPowder,
                'D', diamondLock,
                'C', concussionSmasher,
                'X', Items.BOOK);
    }

    // ============================================================
    // Smelting: glowing powder corners, xp tome center, vanilla
    // furnaces on the sides, Better Nether netherrack furnaces on
    // top/bottom.
    // ============================================================
    private static void registerSmelting(RegistryEvent.Register<IRecipe> event, Item xpTome, Item glowingPowder) {
        net.minecraft.block.Block netherrackFurnaceBlock = net.minecraft.block.Block.REGISTRY.getObject(new ResourceLocation("betternether", "netherrack_furnace"));
        if (netherrackFurnaceBlock == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find betternether:netherrack_furnace! Skipping smelting recipe.");
            return;
        }
        Item netherrackFurnace = Item.getItemFromBlock(netherrackFurnaceBlock);
        Item furnace = Item.getItemFromBlock(net.minecraft.init.Blocks.FURNACE);

        Enchantment smelting = PackCompat.findEnchantment("mujmajnkraftsbettersurvival", "smelting");
        if (smelting == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find mujmajnkraftsbettersurvival:smelting! Skipping smelting recipe.");
            return;
        }

        ItemStack output = createBook(smelting, smelting.getMaxLevel());
        registerConfigurableRecipe(event, "smelting", output,
                "GNG", "FXF", "GNG",
                'G', glowingPowder,
                'N', netherrackFurnace,
                'F', furnace,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Punch II: glowing powder corners, a switchbow Arrow Knockback
    // upgrade on all four edge-middle slots, plain book in the center.
    // ============================================================
    private static void registerPunchII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item arrowKnockback = PackCompat.findItem("switchbow", "arrowknockback");
        if (arrowKnockback == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find switchbow:arrowknockback! Skipping punch_ii recipe.");
            return;
        }

        registerConfigurableRecipe(event, "punch_ii", createBook(Enchantments.PUNCH, 2),
                "GAG", "AXA", "GAG",
                'G', glowingPowder,
                'A', arrowKnockback,
                'X', Items.BOOK);
    }

    // ============================================================
    // Silk Touch: glowing powder corners, a sereneseasons Greenhouse Glass
    // on all four edge-middle slots, plain book in the center.
    // ============================================================
    private static void registerSilkTouch(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item greenhouseGlass = PackCompat.findItem("sereneseasons", "greenhouse_glass");
        if (greenhouseGlass == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find sereneseasons:greenhouse_glass! Skipping silk_touch recipe.");
            return;
        }

        registerConfigurableRecipe(event, "silk_touch", createBook(Enchantments.SILK_TOUCH, 1),
                "GHG", "HXH", "GHG",
                'G', glowingPowder,
                'H', greenhouseGlass,
                'X', Items.BOOK);
    }

    // ============================================================
    // Thorns III: glowing powder corners, simpledifficulty Cactus Juice on
    // the top/bottom middle, cactus on the left/right middle, plain book in
    // the center.
    // ============================================================
    private static void registerThornsIII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item cactusJuice = PackCompat.findItem("simpledifficulty", "juice");
        if (cactusJuice == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find simpledifficulty:juice! Skipping thorns_iii recipe.");
            return;
        }

        ItemStack cactusJuiceStack = new ItemStack(cactusJuice, 1, 2); // JuiceEnum.CACTUS
        Item cactus = Item.getItemFromBlock(net.minecraft.init.Blocks.CACTUS);

        registerConfigurableRecipe(event, "thorns_iii", createBook(Enchantments.THORNS, 3),
                "GJG", "CXC", "GJG",
                'G', glowingPowder,
                'J', cactusJuiceStack,
                'C', cactus,
                'X', Items.BOOK);
    }

    // ============================================================
    // Sweeping Edge III: glowing powder corners, diamond swords on the
    // top/left/right middle, a bountifulbaubles Amulet of Sin: Pride on
    // the bottom-middle, plain book in the center.
    // ============================================================
    private static void registerSweepingEdgeIII(RegistryEvent.Register<IRecipe> event, Item glowingPowder) {
        Item amuletSinPride = PackCompat.findItem("bountifulbaubles", "amuletsinpride");
        if (amuletSinPride == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find bountifulbaubles:amuletsinpride! Skipping sweeping_edge_iii recipe.");
            return;
        }

        registerConfigurableRecipe(event, "sweeping_edge_iii", createBook(Enchantments.SWEEPING, 3),
                "GSG", "SXS", "GAG",
                'G', glowingPowder,
                'S', Items.DIAMOND_SWORD,
                'A', amuletSinPride,
                'X', Items.BOOK);
    }

    // ============================================================
    // Advanced Flame: glowing ingot corners, blaze powder on all four
    // edge-middle slots, center consumes a Flame book.
    // ============================================================
    private static void registerAdvancedFlame(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Enchantment advancedFlame = PackCompat.findEnchantment("somanyenchantments", "advancedflame");
        if (advancedFlame == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedflame! Skipping advanced_flame recipe.");
            return;
        }

        registerConfigurableRecipe(event, "advanced_flame", createBook(advancedFlame, advancedFlame.getMaxLevel()),
                "GBG", "BXB", "GBG",
                'G', glowingIngot,
                'B', Items.BLAZE_POWDER,
                'X', new EnchantedBookIngredient(Enchantments.FLAME, 1));
    }

    // ============================================================
    // Supreme Flame: same layout as advanced_flame, but corners are
    // upgraded to xat's tier 3 material (glowing gem instead of glowing
    // ingot), matching every other Supreme-tier recipe, and its center
    // consumes an Advanced Flame book instead of a plain Flame one.
    // ============================================================
    private static void registerSupremeFlame(RegistryEvent.Register<IRecipe> event) {
        Item glowingGem = resolveGlowingGem();
        if (glowingGem == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem! Skipping supreme_flame recipe.");
            return;
        }

        Enchantment advancedFlame = PackCompat.findEnchantment("somanyenchantments", "advancedflame");
        if (advancedFlame == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedflame! Skipping supreme_flame recipe.");
            return;
        }

        Enchantment supremeFlame = PackCompat.findEnchantment("somanyenchantments", "supremeflame");
        if (supremeFlame == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:supremeflame! Skipping supreme_flame recipe.");
            return;
        }

        registerConfigurableRecipe(event, "supreme_flame", createBook(supremeFlame, supremeFlame.getMaxLevel()),
                "GBG", "BXB", "GBG",
                'G', glowingGem,
                'B', Items.BLAZE_POWDER,
                'X', new EnchantedBookIngredient(advancedFlame, advancedFlame.getMaxLevel()));
    }

    // ============================================================
    // Advanced Punch: same layout as advanced_flame (glowing ingot corners,
    // blaze powder edge-middles), but the center consumes a Punch II book
    // instead of a Flame one.
    // ============================================================
    private static void registerAdvancedPunch(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Enchantment advancedPunch = PackCompat.findEnchantment("somanyenchantments", "advancedpunch");
        if (advancedPunch == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedpunch! Skipping advanced_punch recipe.");
            return;
        }

        registerConfigurableRecipe(event, "advanced_punch", createBook(advancedPunch, advancedPunch.getMaxLevel()),
                "GBG", "BXB", "GBG",
                'G', glowingIngot,
                'B', Items.BLAZE_POWDER,
                'X', new EnchantedBookIngredient(Enchantments.PUNCH, 2));
    }

    // ============================================================
    // Advanced Thorns: same layout as thorns_iii (Cactus Juice top/bottom,
    // cactus left/right), but the corners are upgraded to glowing ingot and
    // the center consumes a Thorns III book instead of a plain one.
    // ============================================================
    private static void registerAdvancedThorns(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        Item cactusJuice = PackCompat.findItem("simpledifficulty", "juice");
        if (cactusJuice == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find simpledifficulty:juice! Skipping advanced_thorns recipe.");
            return;
        }

        Enchantment advancedThorns = PackCompat.findEnchantment("somanyenchantments", "advancedthorns");
        if (advancedThorns == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedthorns! Skipping advanced_thorns recipe.");
            return;
        }

        ItemStack cactusJuiceStack = new ItemStack(cactusJuice, 1, 2); // JuiceEnum.CACTUS
        Item cactus = Item.getItemFromBlock(net.minecraft.init.Blocks.CACTUS);

        registerConfigurableRecipe(event, "advanced_thorns", createBook(advancedThorns, advancedThorns.getMaxLevel()),
                "GJG", "CXC", "GJG",
                'G', glowingIngot,
                'J', cactusJuiceStack,
                'C', cactus,
                'X', new EnchantedBookIngredient(Enchantments.THORNS, 3));
    }

    // ============================================================
    // Clearskies' Favor: glowing ingot corners, xp tome in the center, a
    // Quark Rain Detector on all four edge-middle slots.
    // ============================================================
    private static void registerClearskiesFavor(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        Item xpTome = PackCompat.findItem("xpbook", "xp_book");
        Item rainDetector = PackCompat.findItem("quark", "rain_detector");
        if (glowingIngot == null || xpTome == null || rainDetector == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot, xpbook:xp_book, or quark:rain_detector! Skipping clearskies_favor recipe.");
            return;
        }

        Enchantment clearskiesFavor = PackCompat.findEnchantment("somanyenchantments", "clearskiesfavor");
        if (clearskiesFavor == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:clearskiesfavor! Skipping clearskies_favor recipe.");
            return;
        }

        ItemStack output = createBook(clearskiesFavor, clearskiesFavor.getMaxLevel());
        registerConfigurableRecipe(event, "clearskies_favor", output,
                "GRG", "RXR", "GRG",
                'G', glowingIngot,
                'R', rainDetector,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Advanced Power V: same layout as power_v, but the corners are
    // upgraded to xat's tier 2 material (glowing ingot instead of
    // powder), and the center consumes a Power V book instead of a plain
    // one, producing somanyenchantments:advancedpower (also real max
    // level 5, confirmed via config).
    // ============================================================
    private static void registerAdvancedPowerV(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        Item diamondLongbow = PackCompat.findItem("spartanweaponry", "longbow_diamond");
        if (glowingIngot == null || diamondLongbow == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or spartanweaponry:longbow_diamond! Skipping advanced_power_v recipe.");
            return;
        }

        Enchantment advancedPower = PackCompat.findEnchantment("somanyenchantments", "advancedpower");
        if (advancedPower == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedpower! Skipping advanced_power_v recipe.");
            return;
        }

        ItemStack output = createBook(advancedPower, advancedPower.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_power_v", output,
                "GLG", "LXL", "GLG",
                'G', glowingIngot,
                'L', diamondLongbow,
                'X', new EnchantedBookIngredient(Enchantments.POWER, 5));
    }

    // ============================================================
    // Advanced Feather Falling IV: same layout as feather_falling_iv, but
    // the corners are upgraded to xat's tier 2 material (glowing ingot
    // instead of powder), the center consumes a Feather Falling IV book
    // instead of a plain one, and the bottom-middle is upgraded to a
    // bountifulbaubles Lucky Horseshoe instead of a feather falling potion
    // (harder to source, fitting since the horseshoe itself negates fall
    // damage). Produces somanyenchantments:advancedfeatherfalling.
    // ============================================================
    private static void registerAdvancedFeatherFallingIV(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        Item luckyHorseshoe = PackCompat.findItem("bountifulbaubles", "trinketluckyhorseshoe");
        if (glowingIngot == null || luckyHorseshoe == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot or bountifulbaubles:trinketluckyhorseshoe! Skipping advanced_feather_falling_iv recipe.");
            return;
        }

        Enchantment advancedFeatherFalling = PackCompat.findEnchantment("somanyenchantments", "advancedfeatherfalling");
        if (advancedFeatherFalling == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedfeatherfalling! Skipping advanced_feather_falling_iv recipe.");
            return;
        }

        SpecialIngredient featherFallingPotion = new NbtStringTagIngredient(Items.POTIONITEM, "Potion", "potioncore:slow_fall");

        ItemStack output = createBook(advancedFeatherFalling, advancedFeatherFalling.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_feather_falling_iv", output,
                "GPG", "PXP", "GHG",
                'G', glowingIngot,
                'P', featherFallingPotion,
                'H', luckyHorseshoe,
                'X', new EnchantedBookIngredient(Enchantments.FEATHER_FALLING, 4));
    }

    // ============================================================
    // Sharpness V: glowing ingot corners (upgraded from glowing powder to
    // make this recipe more expensive), diamond swords on the four
    // edge-middle slots, plain book in the center. No xp tome needed.
    // ============================================================
    private static void registerSharpnessV(RegistryEvent.Register<IRecipe> event, Item glowingIngot) {
        registerConfigurableRecipe(event, "sharpness_v", createBook(Enchantments.SHARPNESS, 5),
                "GSG", "SXS", "GSG",
                'G', glowingIngot,
                'S', Items.DIAMOND_SWORD,
                'X', Items.BOOK);
    }

    // ============================================================
    // Advanced Sharpness V: glowing ingot corners (upgraded from
    // sharpness_v's glowing powder), diamond swords on the left/right
    // middle, xp tome in the center, and two Sharpness V books consumed
    // on the top/bottom middle instead of a single one in the center -
    // producing an Advanced Sharpness book (somanyenchantments:advancedsharpness).
    // ============================================================
    private static void registerAdvancedSharpnessV(RegistryEvent.Register<IRecipe> event, Item xpTome) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping advanced_sharpness_v recipe.");
            return;
        }

        Enchantment advancedSharpness = PackCompat.findEnchantment("somanyenchantments", "advancedsharpness");
        if (advancedSharpness == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedsharpness! Skipping advanced_sharpness_v recipe.");
            return;
        }

        ItemStack output = createBook(advancedSharpness, advancedSharpness.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_sharpness_v", output,
                "GBG", "SXS", "GBG",
                'G', glowingIngot,
                'B', new EnchantedBookIngredient(Enchantments.SHARPNESS, 5),
                'S', Items.DIAMOND_SWORD,
                'X', new XpTomeIngredient(xpTome));
    }

    // ============================================================
    // Advanced Looting III: glowing gem corners, a defiledlands golden
    // book wyrm scale on all four edge-middle slots, center consumes a
    // Looting III book, producing an Advanced Looting book
    // (somanyenchantments:advancedlooting).
    // ============================================================
    private static void registerAdvancedLootingIII(RegistryEvent.Register<IRecipe> event) {
        Item glowingGem = resolveGlowingGem();
        Item goldenScale = PackCompat.findItem("defiledlands", "book_wyrm_scale_golden");
        if (glowingGem == null || goldenScale == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem or defiledlands:book_wyrm_scale_golden! Skipping advanced_looting_iii recipe.");
            return;
        }

        Enchantment advancedLooting = PackCompat.findEnchantment("somanyenchantments", "advancedlooting");
        if (advancedLooting == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedlooting! Skipping advanced_looting_iii recipe.");
            return;
        }

        ItemStack output = createBook(advancedLooting, advancedLooting.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_looting_iii", output,
                "GSG", "SXS", "GSG",
                'G', glowingGem,
                'S', goldenScale,
                'X', new EnchantedBookIngredient(Enchantments.LOOTING, 3));
    }

    // ============================================================
    // Efficiency V: glowing powder corners, a diamond pickaxe on top, a
    // baubles Miner's Ring on bottom, redstone blocks on the sides, plain
    // book in the center.
    // ============================================================
    private static void registerEfficiencyV(RegistryEvent.Register<IRecipe> event) {
        Item glowingPowder = resolveGlowingPowder();
        Item minersRing = PackCompat.findItem("baubles", "ring");
        if (glowingPowder == null || minersRing == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_powder or baubles:ring! Skipping efficiency_v recipe.");
            return;
        }

        Item redstoneBlock = Item.getItemFromBlock(net.minecraft.init.Blocks.REDSTONE_BLOCK);

        registerConfigurableRecipe(event, "efficiency_v", createBook(Enchantments.EFFICIENCY, 5),
                "GPG", "RXR", "GMG",
                'G', glowingPowder,
                'P', Items.DIAMOND_PICKAXE,
                'R', redstoneBlock,
                'M', minersRing,
                'X', Items.BOOK);
    }

    // ============================================================
    // Advanced Efficiency V: same layout as efficiency_v, but the corners
    // are upgraded to xat's tier 2 material (glowing ingot instead of
    // redstone blocks), and the center consumes an Efficiency V book
    // instead of a plain one, producing an Advanced Efficiency book
    // (somanyenchantments:advancedefficiency).
    // ============================================================
    private static void registerAdvancedEfficiencyV(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping advanced_efficiency_v recipe.");
            return;
        }

        Enchantment advancedEfficiency = PackCompat.findEnchantment("somanyenchantments", "advancedefficiency");
        if (advancedEfficiency == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedefficiency! Skipping advanced_efficiency_v recipe.");
            return;
        }

        ItemStack output = createBook(advancedEfficiency, advancedEfficiency.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_efficiency_v", output,
                "GPG", "PXP", "GPG",
                'G', glowingIngot,
                'P', Items.DIAMOND_PICKAXE,
                'X', new EnchantedBookIngredient(Enchantments.EFFICIENCY, 5));
    }

    // ============================================================
    // Supreme Sharpness V: xat tier-3 material (glowing gem) in the
    // corners, an Advanced Sharpness V book on the top/bottom middle, a
    // bountifulbaubles Black Dragon Scale trinket on the left/right
    // middle, and a Dragon's Eye (any variant - fire/ice/lightning
    // identity is Forge-capability-backed, not plain NBT/damage, so JEI
    // can only show one generic icon here rather than cycling the real
    // ones) in the center. Produces somanyenchantments:supremesharpness.
    // ============================================================
    private static void registerSupremeSharpnessV(RegistryEvent.Register<IRecipe> event) {
        Item glowingGem = resolveGlowingGem();
        Item blackDragonScale = PackCompat.findItem("bountifulbaubles", "trinketblackdragonscale");
        Item dragonsEye = PackCompat.findItem("xat", "dragons_eye");
        if (glowingGem == null || blackDragonScale == null || dragonsEye == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem, bountifulbaubles:trinketblackdragonscale, or xat:dragons_eye! Skipping supreme_sharpness_v recipe.");
            return;
        }

        Enchantment advancedSharpness = PackCompat.findEnchantment("somanyenchantments", "advancedsharpness");
        if (advancedSharpness == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedsharpness! Skipping supreme_sharpness_v recipe.");
            return;
        }

        Enchantment supremeSharpness = PackCompat.findEnchantment("somanyenchantments", "supremesharpness");
        if (supremeSharpness == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:supremesharpness! Skipping supreme_sharpness_v recipe.");
            return;
        }

        SpecialIngredient anyDragonsEye = new DragonsEyeIngredient(dragonsEye);

        ItemStack output = createBook(supremeSharpness, supremeSharpness.getMaxLevel());
        registerConfigurableRecipe(event, "supreme_sharpness_v", output,
                "GAG", "SDS", "GAG",
                'G', glowingGem,
                'A', new EnchantedBookIngredient(advancedSharpness, 5),
                'S', blackDragonScale,
                'D', anyDragonsEye);
    }

    // ============================================================
    // Advanced Bane of Arthropods: glowing ingot corners, fermented spider
    // eyes on all four edge-middle slots, center consumes a Bane of
    // Arthropods V book. Produces somanyenchantments:advancedbaneofarthropods.
    // ============================================================
    private static void registerAdvancedBaneOfArthropodsV(RegistryEvent.Register<IRecipe> event) {
        Item glowingIngot = resolveGlowingIngot();
        if (glowingIngot == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_ingot! Skipping advanced_bane_of_arthropods_v recipe.");
            return;
        }

        Enchantment advancedBaneOfArthropods = PackCompat.findEnchantment("somanyenchantments", "advancedbaneofarthropods");
        if (advancedBaneOfArthropods == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedbaneofarthropods! Skipping advanced_bane_of_arthropods_v recipe.");
            return;
        }

        ItemStack output = createBook(advancedBaneOfArthropods, advancedBaneOfArthropods.getMaxLevel());
        registerConfigurableRecipe(event, "advanced_bane_of_arthropods_v", output,
                "GFG", "FXF", "GFG",
                'G', glowingIngot,
                'F', Items.FERMENTED_SPIDER_EYE,
                'X', new EnchantedBookIngredient(Enchantments.BANE_OF_ARTHROPODS, Enchantments.BANE_OF_ARTHROPODS.getMaxLevel()));
    }

    // ============================================================
    // Supreme Bane of Arthropods: same layout as advanced_bane_of_arthropods_v,
    // but the corners are upgraded to xat's tier 3 material (glowing gem
    // instead of glowing ingot), and the center consumes an Advanced Bane of
    // Arthropods book instead of a plain Bane of Arthropods one, producing
    // somanyenchantments:supremebaneofarthropods.
    // ============================================================
    private static void registerSupremeBaneOfArthropodsV(RegistryEvent.Register<IRecipe> event) {
        Item glowingGem = resolveGlowingGem();
        if (glowingGem == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem! Skipping supreme_bane_of_arthropods_v recipe.");
            return;
        }

        Enchantment advancedBaneOfArthropods = PackCompat.findEnchantment("somanyenchantments", "advancedbaneofarthropods");
        if (advancedBaneOfArthropods == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:advancedbaneofarthropods! Skipping supreme_bane_of_arthropods_v recipe.");
            return;
        }

        Enchantment supremeBaneOfArthropods = PackCompat.findEnchantment("somanyenchantments", "supremebaneofarthropods");
        if (supremeBaneOfArthropods == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find somanyenchantments:supremebaneofarthropods! Skipping supreme_bane_of_arthropods_v recipe.");
            return;
        }

        ItemStack output = createBook(supremeBaneOfArthropods, supremeBaneOfArthropods.getMaxLevel());
        registerConfigurableRecipe(event, "supreme_bane_of_arthropods_v", output,
                "GFG", "FXF", "GFG",
                'G', glowingGem,
                'F', Items.FERMENTED_SPIDER_EYE,
                'X', new EnchantedBookIngredient(advancedBaneOfArthropods, advancedBaneOfArthropods.getMaxLevel()));
    }

    // ============================================================
    // Dragon Head upgrade: not an enchantment recipe. Takes a stage-4
    // iceandfire:dragon_skull (any of the fire/ice/lightning variants -
    // those are distinguished by item damage, not NBT) surrounded by
    // glowing gems (corners) and dragon bones (edge-middles), and outputs
    // a "tier 5" dragon skull: same item, same damage value (so the
    // fire/ice/lightning identity carries over), and the input's full NBT
    // copied over with "Stage" overwritten to 5. Stage 5 skulls do exist in
    // Ice and Fire, but only generate in rare underground structures and
    // never drop from a dragon - this recipe is a deterministic route to one
    // rather than a new item.
    // ============================================================
    private static void registerDragonHeadUpgrade(RegistryEvent.Register<IRecipe> event) {
        Item glowingGem = resolveGlowingGem();
        Item dragonBone = PackCompat.findItem("iceandfire", "dragonbone");
        Item dragonSkull = PackCompat.findItem("iceandfire", "dragon_skull");
        if (glowingGem == null || dragonBone == null || dragonSkull == null) {
            RLCraftEnchantRecipes.LOGGER.error("Could not find xat:glowing_gem, iceandfire:dragonbone, or iceandfire:dragon_skull! Skipping dragon head upgrade recipe.");
            return;
        }

        DragonHeadUpgradeRecipe recipe = new DragonHeadUpgradeRecipe(glowingGem, dragonBone, dragonSkull);
        recipe.setRegistryName(new ResourceLocation(RLCraftEnchantRecipes.MODID, "dragon_head_tier5_configurable"));
        event.getRegistry().register(recipe);
        DRAGON_HEAD_UPGRADE_RECIPE = recipe;

        RLCraftEnchantRecipes.LOGGER.info("Registered recipe: dragon_head_tier5");
    }

    // ============================================================
    // Workaround for iceandfire's ItemDragonSkull#onCreated, which
    // unconditionally replaces a freshly-crafted dragon skull's NBT tag
    // with a brand new empty NBTTagCompound (no null-check, no read of the
    // old tag at all). Vanilla fires that hook right after a crafting
    // pickup completes, which wipes the Stage:5 tag dragon_head_tier5 just
    // set, even though the crafting-grid preview showed it correctly (the
    // preview only ever calls IRecipe#getCraftingResult, never onCreated).
    //
    // A PlayerEvent.ItemCraftedEvent listener didn't catch this - RealBench
    // patches the vanilla crafting classes with its own ASM transformer
    // (pw.prok.realbench.asm.RBTransformer/ASMHooks), which apparently
    // changes enough of that codepath that Forge's crafting event doesn't
    // fire the way it does on an unpatched crafting table. Since there's no
    // reliable single hook to catch the moment of pickup across every
    // crafting UI in this pack, this instead periodically scans each
    // player's held/inventory/offhand items for the wiped state's unique
    // signature - a dragon_skull stack whose tag compound is non-null but
    // completely empty (0 keys). A skull only ever ends up in that exact
    // state via onCreated's unconditional wipe: a truly untouched skull has
    // no tag compound at all (null) until its own onUpdate fills in
    // Stage/DragonAge defaults, and every other skull in this pack (wild
    // drops, our own recipe output before pickup) always carries real data.
    // ============================================================
    private static final int DRAGON_SKULL_SCAN_INTERVAL_TICKS = 10;

    // Cached after the first successful lookup so the scan doesn't repeat a
    // registry hashmap lookup on every throttled tick.
    private static Item cachedDragonSkull;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.world.isRemote) return;
        if (event.player.ticksExisted % DRAGON_SKULL_SCAN_INTERVAL_TICKS != 0) return;

        if (cachedDragonSkull == null) {
            cachedDragonSkull = PackCompat.findItem("iceandfire", "dragon_skull");
            if (cachedDragonSkull == null) return;
        }

        EntityPlayer player = event.player;
        boolean fixedAny = false;
        fixedAny |= fixIfWiped(player.inventory.getItemStack(), cachedDragonSkull);
        for (ItemStack stack : player.inventory.mainInventory) {
            fixedAny |= fixIfWiped(stack, cachedDragonSkull);
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            fixedAny |= fixIfWiped(stack, cachedDragonSkull);
        }

        // Mutating the stack's NBT in place is correct server-side, but this
        // pack's RealBench crafting GUI (patched in via its own ASM
        // transformer) doesn't appear to notice/resync it through Forge's
        // normal per-tick slot diffing while its GUI stays open - that's why
        // closing and reopening the inventory (a full resync) shows the fix
        // but nothing does while the GUI is still up. Forcing that same full
        // resync ourselves, only on the rare tick something actually needed
        // fixing, covers both the held-cursor item and any slot it landed in.
        if (fixedAny && player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).sendContainerToPlayer(player.openContainer);
        }
    }

    private static boolean fixIfWiped(ItemStack stack, Item dragonSkull) {
        if (stack.isEmpty() || stack.getItem() != dragonSkull || !stack.hasTagCompound()) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag.getSize() > 0) return false;
        tag.setInteger("Stage", 5);
        return true;
    }

    private static void registerConfigurableRecipe(RegistryEvent.Register<IRecipe> event,
                                                    String name, ItemStack output,
                                                    String r1, String r2, String r3,
                                                    Object... ingredients) {
        ConfigurableTomeRecipe recipe = new ConfigurableTomeRecipe(output, r1, r2, r3, ingredients);
        recipe.setRegistryName(new ResourceLocation(RLCraftEnchantRecipes.MODID, name + "_configurable"));
        event.getRegistry().register(recipe);
        TOME_RECIPES.add(recipe);

        RLCraftEnchantRecipes.LOGGER.info("Registered tome recipe: " + name);
    }

    private static ItemStack createBook(Enchantment ench, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(ench, level);
        EnchantmentHelper.setEnchantments(map, book);
        return book;
    }

    // ============================================================
    // Configurable recipe class
    // ============================================================
    public static class ConfigurableTomeRecipe extends IForgeRegistryEntry.Impl<IRecipe>
            implements IRecipe, IShapedRecipe {

        private final ItemStack output;
        private final String r1, r2, r3;
        private final Map<Character, Object> ingredientMap = new HashMap<>();

        public ConfigurableTomeRecipe(ItemStack output, String r1, String r2, String r3, Object[] ingredients) {
            this.output = output;
            this.r1 = r1;
            this.r2 = r2;
            this.r3 = r3;

            for (int i = 0; i < ingredients.length; i += 2) {
                char key = (Character) ingredients[i];
                Object value = ingredients[i + 1];
                ingredientMap.put(key, value);
            }
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return buildDisplayIngredients();
        }

        private NonNullList<Ingredient> buildDisplayIngredients() {
            NonNullList<Ingredient> displayIngredients = NonNullList.create();
            for (char c : (r1 + r2 + r3).toCharArray()) {
                Object value = ingredientMap.get(c);
                if (value instanceof Item) {
                    displayIngredients.add(Ingredient.fromItem((Item) value));
                } else if (value instanceof ItemStack) {
                    displayIngredients.add(Ingredient.fromStacks((ItemStack) value));
                } else if (value instanceof SpecialIngredient) {
                    displayIngredients.add(Ingredient.fromStacks(((SpecialIngredient) value).getDisplayStacks()));
                } else {
                    displayIngredients.add(Ingredient.EMPTY);
                }
            }
            return displayIngredients;
        }

        // Same per-slot data as buildDisplayIngredients(), but as raw per-slot stack
        // lists instead of vanilla Ingredient objects - used by our own custom JEI
        // recipe wrapper (ModJeiPlugin.TomeRecipeWrapper) so multi-stack slots (e.g.
        // xat:dragons_eye's fire/ice/lightning cycling) reach JEI's layout directly via
        // IIngredients.setInputLists(), bypassing JEI's own auto-wrapper for raw
        // ShapedRecipes objects (ShapelessRecipeWrapper), which deduplicates same-item
        // stacks by unique ID before they ever reach the cycling display - collapsing
        // multiple NBT-distinct stacks down to one unless a JEI subtype interpreter is
        // registered for that item. Going around that wrapper entirely means cycling
        // works with zero subtype registration, so it can't interfere with "show
        // recipe"/"show uses" lookups on the real item elsewhere in JEI.
        public List<List<ItemStack>> buildDisplayStackLists() {
            List<List<ItemStack>> stackLists = new ArrayList<>();
            for (char c : (r1 + r2 + r3).toCharArray()) {
                Object value = ingredientMap.get(c);
                if (value instanceof Item) {
                    stackLists.add(java.util.Collections.singletonList(new ItemStack((Item) value)));
                } else if (value instanceof ItemStack) {
                    stackLists.add(java.util.Collections.singletonList((ItemStack) value));
                } else if (value instanceof SpecialIngredient) {
                    stackLists.add(java.util.Arrays.asList(((SpecialIngredient) value).getDisplayStacks()));
                } else {
                    stackLists.add(java.util.Collections.emptyList());
                }
            }
            return stackLists;
        }

        @Override
        public int getRecipeWidth() {
            return 3;
        }

        @Override
        public int getRecipeHeight() {
            return 3;
        }

        @Override
        public boolean matches(InventoryCrafting inv, World world) {
            if (inv.getWidth() < 3 || inv.getHeight() < 3) return false;
            return checkRow(inv, 0, r1) && checkRow(inv, 1, r2) && checkRow(inv, 2, r3);
        }

        private boolean checkRow(InventoryCrafting inv, int row, String pattern) {
            for (int col = 0; col < 3; col++) {
                char c = pattern.charAt(col);
                ItemStack stack = inv.getStackInRowAndColumn(col, row);

                if (c == ' ') {
                    if (!stack.isEmpty()) return false;
                    continue;
                }

                Object expected = ingredientMap.get(c);
                if (expected == null) return false;

                if (expected instanceof Item) {
                    if (stack.isEmpty() || stack.getItem() != expected) return false;
                } else if (expected instanceof ItemStack) {
                    if (!ItemStack.areItemsEqual(stack, (ItemStack) expected)) return false;
                } else if (expected instanceof SpecialIngredient) {
                    if (stack.isEmpty() || !((SpecialIngredient) expected).matches(stack)) return false;
                } else {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            return output.copy();
        }

        @Override
        public boolean canFit(int width, int height) {
            return width >= 3 && height >= 3;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return output;
        }

        // A full xp tome consumed by a plain XpTomeIngredient slot is drained to an
        // empty tome instead of vanishing, same as a filled bucket returning an empty
        // one - unless ModConfig.CONSUME_XP_BOOK_ON_CRAFT is on, in which case it's
        // fully consumed/removed like a normal ingredient. Recipes that intentionally
        // require an already-empty tome (EmptyXpTomeIngredient) always fully consume
        // it, regardless of the setting. Every other ingredient in every slot is
        // always fully consumed.
        @Override
        public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
            NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
            String[] rows = {r1, r2, r3};
            for (int row = 0; row < 3; row++) {
                String pattern = rows[row];
                for (int col = 0; col < pattern.length(); col++) {
                    char c = pattern.charAt(col);
                    if (c == ' ') continue;
                    Object expected = ingredientMap.get(c);
                    if (!(expected instanceof XpTomeIngredient)) continue;

                    ItemStack stack = inv.getStackInRowAndColumn(col, row);
                    if (stack.isEmpty()) continue;

                    if (!ModConfig.CONSUME_XP_BOOK_ON_CRAFT) {
                        int slot = row * 3 + col;
                        remaining.set(slot, new ItemStack(stack.getItem(), 1, stack.getItem().getMaxDamage()));
                    }
                }
            }
            return remaining;
        }
    }

    // ============================================================
    // Special ingredient matchers - for slots that can't be checked with a
    // plain Item/ItemStack comparison (damage thresholds, NBT/enchantments).
    // ============================================================
    private interface SpecialIngredient {
        boolean matches(ItemStack stack);

        // All valid display variants for this slot - JEI cycles through
        // every stack returned here, rather than only ever showing one.
        ItemStack[] getDisplayStacks();
    }

    // Matches the configured xp tome, gated by the live ModConfig damage threshold.
    private static final class XpTomeIngredient implements SpecialIngredient {
        private final Item xpTome;

        XpTomeIngredient(Item xpTome) {
            this.xpTome = xpTome;
        }

        @Override
        public boolean matches(ItemStack stack) {
            return stack.getItem() == xpTome && stack.getItemDamage() <= ModConfig.getMaxAllowedDamage();
        }

        @Override
        public ItemStack[] getDisplayStacks() {
            return new ItemStack[]{new ItemStack(xpTome, 1, ModConfig.getMaxAllowedDamage())};
        }
    }

    // Matches a fully-drained xp tome (0 XP stored, item damage at max) -
    // the "empty" counterpart to XpTomeIngredient, for recipes that
    // intentionally want spent tomes rather than full ones.
    private static final class EmptyXpTomeIngredient implements SpecialIngredient {
        private final Item xpTome;

        EmptyXpTomeIngredient(Item xpTome) {
            this.xpTome = xpTome;
        }

        @Override
        public boolean matches(ItemStack stack) {
            return stack.getItem() == xpTome && stack.getItemDamage() >= xpTome.getMaxDamage();
        }

        @Override
        public ItemStack[] getDisplayStacks() {
            return new ItemStack[]{new ItemStack(xpTome, 1, xpTome.getMaxDamage())};
        }
    }

    // Matches an enchanted book carrying the given enchantment at or above the given level.
    private static final class EnchantedBookIngredient implements SpecialIngredient {
        private final Enchantment enchantment;
        private final int minLevel;

        EnchantedBookIngredient(Enchantment enchantment, int minLevel) {
            this.enchantment = enchantment;
            this.minLevel = minLevel;
        }

        @Override
        public boolean matches(ItemStack stack) {
            if (stack.getItem() != Items.ENCHANTED_BOOK) return false;
            Integer level = EnchantmentHelper.getEnchantments(stack).get(enchantment);
            return level != null && level >= minLevel;
        }

        @Override
        public ItemStack[] getDisplayStacks() {
            return new ItemStack[]{createBook(enchantment, minLevel)};
        }
    }

    // Matches an enchanted book carrying ANY curse enchantment (vanilla's own
    // Enchantment.isCurse() flag - correctly picks up vanilla, So Many
    // Enchantments', and any other mod's curses without hardcoding a list).
    private static final class AnyCurseBookIngredient implements SpecialIngredient {
        @Override
        public boolean matches(ItemStack stack) {
            if (stack.getItem() != Items.ENCHANTED_BOOK) return false;
            for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
                if (enchantment.isCurse()) return true;
            }
            return false;
        }

        @Override
        public ItemStack[] getDisplayStacks() {
            List<ItemStack> stacks = new ArrayList<>();
            for (Enchantment enchantment : Enchantment.REGISTRY) {
                if (enchantment.isCurse()) {
                    stacks.add(createBook(enchantment, enchantment.getMaxLevel()));
                }
            }
            return stacks.toArray(new ItemStack[0]);
        }
    }

    // Matches a given item carrying a specific string NBT tag value - e.g. a
    // vanilla potion (tag "Potion") or a potionfingers ring (tag "effect"),
    // both of which use a single string tag to select their variant.
    private static final class NbtStringTagIngredient implements SpecialIngredient {
        private final Item item;
        private final int damage;
        private final String[] tagPath;
        private final String expectedValue;

        // Single top-level string tag, e.g. vanilla potions ("Potion").
        // Display stack defaults to damage 0 - fine for items whose NBT tag
        // alone carries their identity (vanilla potions never vary damage
        // by flavor).
        NbtStringTagIngredient(Item item, String tagKey, String expectedValue) {
            this(item, 0, new String[]{tagKey}, expectedValue);
        }

        // Nested path of compound keys ending in the string tag, e.g.
        // Roguelike Dungeons' mixture items store their identity at
        // display -> LocName rather than a top-level tag.
        NbtStringTagIngredient(Item item, String[] tagPath, String expectedValue) {
            this(item, 0, tagPath, expectedValue);
        }

        // Explicit-damage variants - needed for potionfingers rings, whose
        // real flavored output is built at damage 1 (ItemRing#getRingForPotion),
        // while the plain/unflavored ring (a completely different recipe)
        // is damage 0. Without matching that, JEI's default item+damage
        // uniqueness key (it ignores NBT unless a subtype interpreter says
        // otherwise) treated our damage-0 display stack as the same subtype
        // as the plain ring, so "show recipe" on a flavored ring jumped to
        // the wrong (blank ring) recipe instead of the correct one.
        NbtStringTagIngredient(Item item, int damage, String tagKey, String expectedValue) {
            this(item, damage, new String[]{tagKey}, expectedValue);
        }

        NbtStringTagIngredient(Item item, int damage, String[] tagPath, String expectedValue) {
            this.item = item;
            this.damage = damage;
            this.tagPath = tagPath;
            this.expectedValue = expectedValue;
        }

        @Override
        public boolean matches(ItemStack stack) {
            if (stack.getItem() != item || !stack.hasTagCompound()) return false;
            NBTTagCompound tag = stack.getTagCompound();
            for (int i = 0; i < tagPath.length - 1; i++) {
                if (!tag.hasKey(tagPath[i])) return false;
                tag = tag.getCompoundTag(tagPath[i]);
            }
            return expectedValue.equals(tag.getString(tagPath[tagPath.length - 1]));
        }

        @Override
        public ItemStack[] getDisplayStacks() {
            ItemStack stack = new ItemStack(item, 1, damage);
            NBTTagCompound innermost = new NBTTagCompound();
            innermost.setString(tagPath[tagPath.length - 1], expectedValue);
            for (int i = tagPath.length - 2; i >= 0; i--) {
                NBTTagCompound wrapper = new NBTTagCompound();
                wrapper.setTag(tagPath[i], innermost);
                innermost = wrapper;
            }
            stack.setTagCompound(innermost);
            return new ItemStack[]{stack};
        }
    }

    // Matches a given item carrying a specific top-level integer NBT tag -
    // e.g. simpledifficulty's canteens, which store their state (empty/
    // normal/purified/etc) as an int "CanteenType" tag rather than a string.
    private static final class NbtIntTagIngredient implements SpecialIngredient {
        private final Item item;
        private final String tagKey;
        private final int expectedValue;

        NbtIntTagIngredient(Item item, String tagKey, int expectedValue) {
            this.item = item;
            this.tagKey = tagKey;
            this.expectedValue = expectedValue;
        }

        @Override
        public boolean matches(ItemStack stack) {
            if (stack.getItem() != item || !stack.hasTagCompound()) return false;
            return stack.getTagCompound().getInteger(tagKey) == expectedValue;
        }

        @Override
        public ItemStack[] getDisplayStacks() {
            ItemStack stack = new ItemStack(item);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger(tagKey, expectedValue);
            stack.setTagCompound(tag);
            return new ItemStack[]{stack};
        }
    }

    // Matches a vanilla potion carrying the given effect at or above the given
    // amplifier via the "CustomPotionEffects" NBT list - used for potion levels
    // vanilla brewing itself can't produce (e.g. Levitation II), since vanilla
    // only ever registers a single flat PotionType for that effect.
    private static final class CustomPotionEffectIngredient implements SpecialIngredient {
        private final net.minecraft.potion.Potion effect;
        private final int minAmplifier;

        CustomPotionEffectIngredient(net.minecraft.potion.Potion effect, int minAmplifier) {
            this.effect = effect;
            this.minAmplifier = minAmplifier;
        }

        @Override
        public boolean matches(ItemStack stack) {
            if (stack.getItem() != Items.POTIONITEM || !stack.hasTagCompound()) return false;
            NBTTagCompound tag = stack.getTagCompound();
            if (!tag.hasKey("CustomPotionEffects", 9)) return false;
            net.minecraft.nbt.NBTTagList list = tag.getTagList("CustomPotionEffects", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound effectTag = list.getCompoundTagAt(i);
                net.minecraft.potion.Potion potion = net.minecraft.potion.Potion.getPotionById(effectTag.getByte("Id") & 255);
                int amplifier = effectTag.getByte("Amplifier") & 255;
                if (potion == effect && amplifier >= minAmplifier) return true;
            }
            return false;
        }

        @Override
        public ItemStack[] getDisplayStacks() {
            ItemStack stack = new ItemStack(Items.POTIONITEM);
            NBTTagCompound tag = new NBTTagCompound();
            net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
            NBTTagCompound effectTag = new NBTTagCompound();
            effectTag.setByte("Id", (byte) net.minecraft.potion.Potion.getIdFromPotion(effect));
            effectTag.setByte("Amplifier", (byte) minAmplifier);
            effectTag.setShort("Duration", (short) 200);
            list.appendTag(effectTag);
            tag.setTag("CustomPotionEffects", list);
            stack.setTagCompound(tag);
            return new ItemStack[]{stack};
        }
    }

    // Matches any one of several possible items - used when more than one
    // material is acceptable for the same slot (e.g. either of two mods'
    // feather drops).
    private static final class AnyOfItemsIngredient implements SpecialIngredient {
        private final Item[] items;

        AnyOfItemsIngredient(Item... items) {
            this.items = items;
        }

        @Override
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) return false;
            for (Item item : items) {
                if (stack.getItem() == item) return true;
            }
            return false;
        }

        @Override
        public ItemStack[] getDisplayStacks() {
            ItemStack[] stacks = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) {
                stacks[i] = new ItemStack(items[i]);
            }
            return stacks;
        }
    }

    // Matches a vanilla mob head restricted to the undead trio (skeleton,
    // wither skeleton, zombie) by item damage - excludes player, creeper,
    // and dragon heads, which also share Items.SKULL.
    private static final class UndeadHeadIngredient implements SpecialIngredient {
        private static final int[] UNDEAD_DAMAGES = {0, 1, 2};

        @Override
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty() || stack.getItem() != Items.SKULL) return false;
            for (int damage : UNDEAD_DAMAGES) {
                if (stack.getItemDamage() == damage) return true;
            }
            return false;
        }

        @Override
        public ItemStack[] getDisplayStacks() {
            ItemStack[] stacks = new ItemStack[UNDEAD_DAMAGES.length];
            for (int i = 0; i < UNDEAD_DAMAGES.length; i++) {
                stacks[i] = new ItemStack(Items.SKULL, 1, UNDEAD_DAMAGES[i]);
            }
            return stacks;
        }
    }

    // Matches any vanilla mob head - skeleton, wither skeleton, zombie,
    // player, creeper, or dragon - by item alone, ignoring damage value.
    // Unlike UndeadHeadIngredient, not restricted to the undead trio.
    private static final class AnyHeadIngredient implements SpecialIngredient {
        private static final int[] ALL_HEAD_DAMAGES = {0, 1, 2, 3, 4, 5};

        @Override
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() == Items.SKULL;
        }

        @Override
        public ItemStack[] getDisplayStacks() {
            ItemStack[] stacks = new ItemStack[ALL_HEAD_DAMAGES.length];
            for (int i = 0; i < ALL_HEAD_DAMAGES.length; i++) {
                stacks[i] = new ItemStack(Items.SKULL, 1, ALL_HEAD_DAMAGES[i]);
            }
            return stacks;
        }
    }

    // Matches xat:dragons_eye regardless of variant. Its fire/ice/lightning
    // identity is NOT sourced from NBT for rendering purposes - xat's own
    // recipe (RecipeDragonEye#getCraftingResult) and its creative-tab list
    // (TrinketDragonsEye#getSubItems) both set it by directly mutating the
    // live Forge capability object via Capabilities.getTrinketProperties()
    // (TrinketProperties#setVariant + ElementalAttributes#setPrimaryElement)
    // at construction time - NBT is only a write-out cache of whatever the
    // capability currently holds, populated later, never the source of
    // truth the model-selection code reads. Two earlier attempts that
    // fabricated NBT by hand (even from a full stack-format NBT blob)
    // rendered the wrong icon because of this - the capability itself
    // never got set. This calls xat's real mutation method via reflection
    // (xat isn't a compile-time dependency) to replicate exactly what a
    // genuinely crafted item goes through, rather than re-deriving their
    // internal state by hand.
    private static final class DragonsEyeIngredient implements SpecialIngredient {
        private final Item dragonsEye;

        DragonsEyeIngredient(Item dragonsEye) {
            this.dragonsEye = dragonsEye;
        }

        @Override
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() == dragonsEye;
        }

        @Override
        public ItemStack[] getDisplayStacks() {
            return new ItemStack[]{
                    variantStack(1, "xat:fire"),
                    variantStack(2, "xat:ice"),
                    variantStack(3, "xat:lightning")
            };
        }

        private ItemStack variantStack(int variant, String elementRegistryName) {
            ItemStack stack = new ItemStack(dragonsEye, 1, 0);
            try {
                Class<?> capabilitiesClass = Class.forName("xzeroair.trinkets.capabilities.Capabilities");
                Class<?> elementClass = Class.forName("xzeroair.trinkets.traits.elements.Element");
                Class<?> trinketPropertiesClass = Class.forName("xzeroair.trinkets.capabilities.Trinket.TrinketProperties");
                Class<?> elementalAttributesClass = Class.forName("xzeroair.trinkets.capabilities.race.ElementalAttributes");

                Object element = elementClass.getMethod("getByNameOrId", String.class).invoke(null, elementRegistryName);
                java.lang.reflect.Method setVariant = trinketPropertiesClass.getMethod("setVariant", int.class);
                java.lang.reflect.Method getElementAttributes = trinketPropertiesClass.getMethod("getElementAttributes");
                java.lang.reflect.Method setPrimaryElement = elementalAttributesClass.getMethod("setPrimaryElement", elementClass);
                java.lang.reflect.Method getTag = trinketPropertiesClass.getMethod("getTag");

                java.util.function.Consumer<Object> mutator = properties -> {
                    try {
                        setVariant.invoke(properties, variant);
                        Object attributes = getElementAttributes.invoke(properties);
                        setPrimaryElement.invoke(attributes, element);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                };

                java.lang.reflect.Method getTrinketProperties = capabilitiesClass.getMethod(
                        "getTrinketProperties", ItemStack.class, java.util.function.Consumer.class);
                Object properties = getTrinketProperties.invoke(null, stack, mutator);
                // getTag() only re-serializes the capability into NBT if the stack
                // currently has NO tag compound at all (decompiled TrinketProperties#getTag:
                // "if (tag == null) { saveToNBT(tag); setTagCompound(tag); }") - and
                // getTrinketProperties() above already lazily seeds a tag compound as part
                // of initializing the capability, before our mutator's changes exist. So the
                // "sync" call was always a no-op, silently keeping the stack's NBT on
                // whatever default state got seeded during init (Elements.primary stuck at
                // "xat:neutral") instead of our mutated variant/element. Clearing the tag
                // first forces getTag() down its real resave path, capturing the state our
                // mutator actually set.
                stack.setTagCompound(null);
                getTag.invoke(properties);
            } catch (ReflectiveOperationException | RuntimeException e) {
                RLCraftEnchantRecipes.LOGGER.error("Failed to build xat Dragon's Eye JEI display variant (" + elementRegistryName + ") via reflection", e);
            }
            return stack;
        }
    }

    // ============================================================
    // Dragon Head upgrade recipe. Unlike ConfigurableTomeRecipe, the output
    // isn't fixed - it's built from whichever center item was actually
    // used, so it can't reuse that class's ingredient/output machinery.
    // ============================================================
    public static class DragonHeadUpgradeRecipe extends IForgeRegistryEntry.Impl<IRecipe>
            implements IRecipe, IShapedRecipe {

        private static final int REQUIRED_INPUT_STAGE = 4;
        private static final int OUTPUT_STAGE = 5;

        private final Item glowingGem;
        private final Item dragonBone;
        private final Item dragonSkull;

        public DragonHeadUpgradeRecipe(Item glowingGem, Item dragonBone, Item dragonSkull) {
            this.glowingGem = glowingGem;
            this.dragonBone = dragonBone;
            this.dragonSkull = dragonSkull;
        }

        @Override
        public boolean matches(InventoryCrafting inv, World world) {
            if (inv.getWidth() < 3 || inv.getHeight() < 3) return false;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    ItemStack stack = inv.getStackInRowAndColumn(col, row);
                    if (row == 1 && col == 1) {
                        if (!isStage4DragonHead(stack)) return false;
                    } else if (row == 1 || col == 1) {
                        if (stack.isEmpty() || stack.getItem() != dragonBone) return false;
                    } else {
                        if (stack.isEmpty() || stack.getItem() != glowingGem) return false;
                    }
                }
            }
            return true;
        }

        private boolean isStage4DragonHead(ItemStack stack) {
            if (stack.isEmpty() || stack.getItem() != dragonSkull) return false;
            NBTTagCompound tag = stack.getTagCompound();
            return tag != null && tag.hasKey("Stage") && tag.getInteger("Stage") == REQUIRED_INPUT_STAGE;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            ItemStack input = inv.getStackInRowAndColumn(1, 1);
            ItemStack output = new ItemStack(dragonSkull, 1, input.getItemDamage());
            NBTTagCompound tag = input.hasTagCompound() ? input.getTagCompound().copy() : new NBTTagCompound();
            tag.setInteger("Stage", OUTPUT_STAGE);
            output.setTagCompound(tag);
            return output;
        }

        @Override
        public boolean canFit(int width, int height) {
            return width >= 3 && height >= 3;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return placeholderOutput();
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
            return NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        }

        @Override
        public int getRecipeWidth() {
            return 3;
        }

        @Override
        public int getRecipeHeight() {
            return 3;
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return buildDisplayIngredients();
        }

        // Same reasoning as ConfigurableTomeRecipe.toJeiDisplayRecipe(): JEI won't
        // recognize this custom IRecipe on its own, so ModJeiPlugin passes these
        // display-only ShapedRecipes (never registered to the game) instead.
        //
        // One recipe per element (fire/ice/lightning) rather than a single
        // recipe with a cycling input - real crafting always outputs the
        // same element it was given, but JEI has no way to keep a cycling
        // input and a cycling output in sync with each other, so a single
        // shared entry always showed the fixed fire output regardless of
        // which input variant happened to be on screen. Splitting them out
        // makes each entry accurately show that element in, that element
        // out.
        public List<ShapedRecipes> toJeiDisplayRecipes() {
            List<ShapedRecipes> recipes = new ArrayList<>();
            for (int damage = 0; damage < 3; damage++) {
                recipes.add(new ShapedRecipes("", 3, 3, buildDisplayIngredients(damage), placeholderOutput(damage)));
            }
            return recipes;
        }

        private NonNullList<Ingredient> buildDisplayIngredients(int damage) {
            Ingredient gemIngredient = Ingredient.fromItem(glowingGem);
            Ingredient boneIngredient = Ingredient.fromItem(dragonBone);
            Ingredient headIngredient = Ingredient.fromStacks(placeholderInput(damage));

            NonNullList<Ingredient> ingredients = NonNullList.create();
            ingredients.add(gemIngredient);
            ingredients.add(boneIngredient);
            ingredients.add(gemIngredient);
            ingredients.add(boneIngredient);
            ingredients.add(headIngredient);
            ingredients.add(boneIngredient);
            ingredients.add(gemIngredient);
            ingredients.add(boneIngredient);
            ingredients.add(gemIngredient);
            return ingredients;
        }

        private ItemStack placeholderInput(int damage) {
            ItemStack stack = new ItemStack(dragonSkull, 1, damage);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Stage", REQUIRED_INPUT_STAGE);
            stack.setTagCompound(tag);
            return stack;
        }

        private ItemStack placeholderOutput(int damage) {
            ItemStack stack = new ItemStack(dragonSkull, 1, damage);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Stage", OUTPUT_STAGE);
            stack.setTagCompound(tag);
            return stack;
        }

        private NonNullList<Ingredient> buildDisplayIngredients() {
            Ingredient gemIngredient = Ingredient.fromItem(glowingGem);
            Ingredient boneIngredient = Ingredient.fromItem(dragonBone);
            Ingredient headIngredient = Ingredient.fromStacks(placeholderInputs());

            NonNullList<Ingredient> ingredients = NonNullList.create();
            ingredients.add(gemIngredient);
            ingredients.add(boneIngredient);
            ingredients.add(gemIngredient);
            ingredients.add(boneIngredient);
            ingredients.add(headIngredient);
            ingredients.add(boneIngredient);
            ingredients.add(gemIngredient);
            ingredients.add(boneIngredient);
            ingredients.add(gemIngredient);
            return ingredients;
        }

        // All three fire/ice/lightning variants (damage 0/1/2) for JEI
        // display - real crafting accepts any of them via item damage, so
        // the display should cycle through all three rather than just fire.
        private ItemStack[] placeholderInputs() {
            ItemStack[] stacks = new ItemStack[3];
            for (int damage = 0; damage < 3; damage++) {
                ItemStack stack = new ItemStack(dragonSkull, 1, damage);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("Stage", REQUIRED_INPUT_STAGE);
                stack.setTagCompound(tag);
                stacks[damage] = stack;
            }
            return stacks;
        }

        private ItemStack placeholderOutput() {
            ItemStack stack = new ItemStack(dragonSkull, 1, 0);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Stage", OUTPUT_STAGE);
            stack.setTagCompound(tag);
            return stack;
        }
    }
}
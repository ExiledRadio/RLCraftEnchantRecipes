package com.exiledradio.rlcraftenchantrecipes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.util.List;

@Mod.EventBusSubscriber(modid = RLCraftEnchantRecipes.MODID)
public class ModConfig {

    public static Configuration config;
    public static int XP_TOME_LEVEL = 30;
    public static boolean CONSUME_XP_BOOK_ON_CRAFT = false;
    public static String DIFFICULTY_MODE = "normal";
    public static boolean ENABLE_DRAGON_HEAD_UPGRADE = true;
    public static String PACK_MODE = "dregora";
    public static String[] DISABLED_RECIPES = new String[0];

    public static void init(File configFile) {
        if (config == null) {
            config = new Configuration(configFile);
            loadConfig();
        }
    }

    public static void loadConfig() {
        XP_TOME_LEVEL = config.getInt(
                "XP_TOME_LEVEL",
                Configuration.CATEGORY_GENERAL,
                30,
                0,
                30,
                "Required XP Tome level for ALL tome-based enchantment recipes (e.g. mending, sharpness_v).\n" +
                        "Allowed values: 0, 5, 10, 15, 20, 25, 30\n" +
                        "A tome with MORE XP than required is accepted.\n" +
                        "A tome with LESS XP than required is rejected."
        );

        // Clamp and snap to nearest lower multiple of 5
        if (XP_TOME_LEVEL < 0) XP_TOME_LEVEL = 0;
        if (XP_TOME_LEVEL > 30) XP_TOME_LEVEL = 30;
        XP_TOME_LEVEL = (XP_TOME_LEVEL / 5) * 5;

        CONSUME_XP_BOOK_ON_CRAFT = config.getBoolean(
                "CONSUME_XP_BOOK_ON_CRAFT",
                Configuration.CATEGORY_GENERAL,
                false,
                "If false (default), a full xp tome used in a recipe is NOT consumed outright - it's" +
                        " drained to an empty tome that stays in the crafting grid, the same way a filled" +
                        " bucket returns an empty bucket instead of vanishing." +
                        "\nIf true, the xp tome is fully consumed/removed like a normal ingredient." +
                        "\nThis only affects recipes that consume a FULL xp tome - recipes that intentionally" +
                        " require an already-empty tome as an ingredient always consume it regardless of this setting."
        );

        // Recipes are registered exactly once, when the mod loads (RegistryEvent.Register<IRecipe>
        // fires a single time at startup) - unlike XP_TOME_LEVEL/CONSUME_XP_BOOK_ON_CRAFT (read live
        // at crafting-check time), changing this mid-session doesn't retroactively re-register
        // anything with different materials, so a restart is genuinely needed for it to apply.
        // Deliberately NOT calling setRequiresMcRestart()/setRequiresWorldRestart() - Forge's
        // GuiConfig disables editing entirely for those while a world is loaded (same underlying
        // flag drives both the "restart required" label AND the disabled button), which is more
        // restrictive than wanted here. The restart requirement is called out in the comment
        // (shown as this entry's in-GUI tooltip) instead, and the button stays editable always.
        DIFFICULTY_MODE = config.getString(
                "DIFFICULTY_MODE",
                Configuration.CATEGORY_GENERAL,
                "normal",
                "Shifts every glowing-material recipe cost up or down one tier on the ladder" +
                        " glowstone -> glowing powder -> glowing ingot -> glowing gem -> glowing gem block." +
                        "\n\"easy\" shifts every recipe one tier cheaper (e.g. glowing ingot recipes use" +
                        " glowing powder instead)." +
                        "\n\"hard\" shifts every recipe one tier more expensive (e.g. glowing gem recipes use" +
                        " the glowing gem block instead)." +
                        "\n\"normal\" (default) makes no change." +
                        "\nAllowed values: easy, normal, hard" +
                        "\n>> RESTART THE GAME after changing this - recipes only (re)register on launch. <<",
                new String[]{"easy", "normal", "hard"}
        ).toLowerCase(java.util.Locale.ROOT);
        if (!DIFFICULTY_MODE.equals("easy") && !DIFFICULTY_MODE.equals("hard")) {
            DIFFICULTY_MODE = "normal";
        }

        // Same one-time-registration reasoning as DIFFICULTY_MODE above.
        ENABLE_DRAGON_HEAD_UPGRADE = config.getBoolean(
                "ENABLE_DRAGON_HEAD_UPGRADE",
                Configuration.CATEGORY_GENERAL,
                true,
                "If true (default), enables the recipe that upgrades a stage-4 iceandfire dragon skull" +
                        " to a stage 5 (glowing gem corners + dragon bone edges)." +
                        "\nStage 5 skulls only generate in rare underground structures and are needed" +
                        " for the Dragon's Eye bauble, so this removes the luck involved in getting one." +
                        "\nSet to false to disable it." +
                        "\n>> RESTART THE GAME after changing this - recipes only (re)register on launch. <<"
        );

        // Same one-time-registration reasoning as DIFFICULTY_MODE above.
        PACK_MODE = config.getString(
                "PACK_MODE",
                Configuration.CATEGORY_GENERAL,
                "dregora",
                "Which pack this mod is running in. Recipes were authored against RLCraft Dregora;" +
                        " base RLCraft ships older versions of several mods with different registry" +
                        " names and less content, so some recipes need substituted materials there." +
                        "\n\"dregora\" (default) - recipes exactly as authored. Use this for RLCraft Dregora." +
                        "\n\"rlcraft\" - substitutes materials base RLCraft lacks (silver ingot for copper," +
                        " dragon heart for hydra heart, sea serpent fang for hydra fang, iron hoe for iron" +
                        " scythe, themed material for curseweave fabric), maps enchantment names onto" +
                        " SoManyEnchantments 0.5.5's naming, drops recipes whose content does not exist," +
                        " and adds Subject English / Subject Science recipes that only base RLCraft has." +
                        "\nAllowed values: dregora, rlcraft" +
                        "\n>> RESTART THE GAME after changing this - recipes only (re)register on launch. <<",
                new String[]{"dregora", "rlcraft"}
        ).toLowerCase(java.util.Locale.ROOT);
        if (!PACK_MODE.equals("rlcraft")) {
            PACK_MODE = "dregora";
        }

        // Same one-time-registration reasoning as DIFFICULTY_MODE above. Every recipe this mod
        // registers (curses and the plain vanilla-Mending tome recipe included, since both route
        // through the same shared registerConfigurableRecipe helper) is checked against this list
        // before it registers - a match skips that recipe entirely, as if it never existed.
        DISABLED_RECIPES = config.getStringList(
                "DISABLED_RECIPES",
                Configuration.CATEGORY_GENERAL,
                new String[0],
                "Recipe IDs to disable entirely - list one per line. Find the exact ID for a recipe" +
                        " in the game log at startup (\"Registered tome recipe: <id>\") or in this mod's" +
                        " ModRecipes.java source." +
                        "\n\"*\" works as a wildcard anywhere in an entry, matched against the whole ID:" +
                        " \"supreme_*\" disables every Supreme-tier recipe, \"*_v\" disables every recipe" +
                        " ending in _v, \"*sharpness*\" disables every recipe with \"sharpness\" anywhere" +
                        " in its ID. An entry with no \"*\" must match an ID exactly." +
                        "\nEmpty (default) disables nothing." +
                        "\nDoes NOT cover ENABLE_DRAGON_HEAD_UPGRADE (its own separate toggle above) or" +
                        " the Glowing Gem Block's crafting recipe (a plain vanilla-format JSON recipe," +
                        " not registered through this list)." +
                        "\n>> RESTART THE GAME after changing this - recipes only (re)register on launch. <<"
        );

        if (config.hasChanged()) {
            config.save();
        }

        RLCraftEnchantRecipes.LOGGER.info("Config loaded - XP_TOME_LEVEL = " + XP_TOME_LEVEL
                + ", CONSUME_XP_BOOK_ON_CRAFT = " + CONSUME_XP_BOOK_ON_CRAFT
                + ", DIFFICULTY_MODE = " + DIFFICULTY_MODE
                + ", ENABLE_DRAGON_HEAD_UPGRADE = " + ENABLE_DRAGON_HEAD_UPGRADE
                + ", PACK_MODE = " + PACK_MODE
                + ", DISABLED_RECIPES = " + java.util.Arrays.toString(DISABLED_RECIPES));
    }

    public static List<IConfigElement> getConfigElements() {
        // Expose the category's individual properties directly, instead of wrapping
        // them in a single "General" category element the user has to click into.
        return new ConfigElement(config.getCategory(Configuration.CATEGORY_GENERAL)).getChildElements();
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(RLCraftEnchantRecipes.MODID)) {
            String previousDifficultyMode = DIFFICULTY_MODE;
            boolean previousDragonHeadUpgrade = ENABLE_DRAGON_HEAD_UPGRADE;
            String previousPackMode = PACK_MODE;
            String[] previousDisabledRecipes = DISABLED_RECIPES;

            loadConfig();

            boolean recipeAffectingChange = !previousDifficultyMode.equals(DIFFICULTY_MODE)
                    || previousDragonHeadUpgrade != ENABLE_DRAGON_HEAD_UPGRADE
                    || !previousPackMode.equals(PACK_MODE)
                    || !java.util.Arrays.equals(previousDisabledRecipes, DISABLED_RECIPES);
            if (recipeAffectingChange) {
                notifyRestartRequired();
            }
        }
    }

    // Config screens only ever exist client-side, so OnConfigChangedEvent (fired when one is
    // saved) never fires on a dedicated server - this is safe to call unconditionally from the
    // common event handler above.
    @SideOnly(Side.CLIENT)
    private static void notifyRestartRequired() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return;
        player.sendMessage(new TextComponentString(
                TextFormatting.GOLD + "[RLCraft Enchantment Recipes] " + TextFormatting.YELLOW
                        + "A recipe-affecting setting changed (DIFFICULTY_MODE / ENABLE_DRAGON_HEAD_UPGRADE"
                        + " / PACK_MODE / DISABLED_RECIPES) - "
                        + TextFormatting.WHITE + "restart the game" + TextFormatting.YELLOW
                        + " for recipes to update. Nothing changes until then."));
    }

    public static int getMaxAllowedDamage() {
        switch (XP_TOME_LEVEL) {
            case 0:  return 1395;
            case 5:  return 1340;
            case 10: return 1235;
            case 15: return 1080;
            case 20: return 845;
            case 25: return 485;
            case 30:
            default: return 0;
        }
    }
}
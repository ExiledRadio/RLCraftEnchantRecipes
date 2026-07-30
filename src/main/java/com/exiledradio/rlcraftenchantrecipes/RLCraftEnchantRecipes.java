package com.exiledradio.rlcraftenchantrecipes;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = RLCraftEnchantRecipes.MODID,
        name = RLCraftEnchantRecipes.NAME,
        version = RLCraftEnchantRecipes.VERSION,
        guiFactory = "com.exiledradio.rlcraftenchantrecipes.ModGuiFactory"
)
public class RLCraftEnchantRecipes {

    public static final String MODID = "rlcraftenchantrecipes";
    public static final String NAME = "RLCraft Enchantment Recipes";
    // Replaced at build time by ForgeGradle from mod_version in gradle.properties.
    // Shows literally as "@VERSION@" in IDE dev runs; that is expected.
    public static final String VERSION = "@VERSION@";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("RLCraft Enchantment Recipes is loading (Pre-Initialization)");
        ModConfig.init(event.getSuggestedConfigurationFile());
        ModRecipes.preInit();   // ← add this
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("RLCraft Enchantment Recipes is loading (Initialization)");
    }
}
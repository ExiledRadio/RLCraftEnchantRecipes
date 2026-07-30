package com.exiledradio.rlcraftenchantrecipes;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

@Mod.EventBusSubscriber(modid = RLCraftEnchantRecipes.MODID)
public class ModBlocks {

    // Decorative gem block - same physical properties as vanilla's emerald/diamond
    // blocks (Material.IRON, SoundType.METAL, iron-pickaxe harvest level).
    public static final Block GLOWING_GEM_BLOCK = new BlockGlowingGem();

    private static final class BlockGlowingGem extends Block {
        BlockGlowingGem() {
            super(Material.IRON);
            setHardness(5.0F);
            setResistance(10.0F);
            setSoundType(SoundType.METAL);
            setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
            setRegistryName(RLCraftEnchantRecipes.MODID, "glowing_gem_block");
            setTranslationKey(RLCraftEnchantRecipes.MODID + ".glowing_gem_block");
            setHarvestLevel("pickaxe", 2);
        }
    }

    @SubscribeEvent
    public static void onRegisterBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(GLOWING_GEM_BLOCK);
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        ItemBlock itemBlock = new ItemGlowingGemBlock(GLOWING_GEM_BLOCK);
        itemBlock.setRegistryName(GLOWING_GEM_BLOCK.getRegistryName());
        event.getRegistry().register(itemBlock);
    }

    // Mirrors xat's own glowing_powder/glowing_ingot/glowing_gem tooltip convention
    // (an ascending-tier color: none, §e, §6...) with a §c "Tier 4 Crafting Material"
    // line, continuing the same tier ladder one step further.
    private static final class ItemGlowingGemBlock extends ItemBlock {
        ItemGlowingGemBlock(Block block) {
            super(block);
        }

        @Override
        public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
            super.addInformation(stack, worldIn, tooltip, flagIn);
            tooltip.add("§cTier 4 Crafting Material");
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onModelRegister(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(GLOWING_GEM_BLOCK), 0,
                new ModelResourceLocation(GLOWING_GEM_BLOCK.getRegistryName(), "normal")
        );
    }
}

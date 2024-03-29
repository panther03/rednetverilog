package com.panther03.rednetverilog;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.registry.GameRegistry;

@Mod(modid = RednetVerilogMod.MODID, version = Tags.VERSION, name = RednetVerilogMod.MODNAME, acceptedMinecraftVersions = "[1.7.10]")
public class RednetVerilogMod {

    public static final String MODID = "rednetverilog";
    public static final String GROUP = "com.panther03." + MODID;
    public static final String MODNAME = "Verilog for RedNet";

    @Mod.Instance(MODID)
    public static RednetVerilogMod instance;

    private static Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = GROUP + ".ClientProxy", serverSide = GROUP + ".CommonProxy")
    public static CommonProxy proxy;

    public static Block blockCompiler;

    @Mod.EventHandler
    // preInit "Run before anything else. Read your config, create blocks, items,
    // etc, and register them with the GameRegistry."
    public void preInit(FMLPreInitializationEvent event) {
        blockCompiler = new BlockCompiler(Material.rock).setBlockName("BlockCompiler");
        blockCompiler.setBlockTextureName(MODID + ":" + blockCompiler.getUnlocalizedName());
        GameRegistry.registerBlock(blockCompiler, "blockCompiler");
        proxy.preInit(event);
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes."
    public void init(FMLInitializationEvent event) {
        GameRegistry.addRecipe(
            new ItemStack(blockCompiler),
            new Object[] { "RDR", "DID", "RDR", 'R', Items.redstone, 'D', Blocks.diamond_block, 'I',
                Blocks.iron_block });
        proxy.init(event);
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this."
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        proxy.serverAboutToStart(event);
    }

    @Mod.EventHandler
    // register server commands in this event handler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        proxy.serverStarted(event);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        proxy.serverStopping(event);
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        proxy.serverStopped(event);
    }

    public static void debug(String message) {
        LOG.debug(message);
    }

    public static void info(String message) {
        LOG.info(message);
    }

    public static void warn(String message) {
        LOG.warn(message);
    }

    public static void error(String message) {
        LOG.error(message);
    }
}

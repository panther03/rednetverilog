package com.panther03.rednetverilog;

import com.google.common.io.ByteStreams;
import com.panther03.compiler.PortExtraction;
import com.panther03.compiler.RednetCodegen;
import cpw.mods.fml.common.event.*;
import org.apache.commons.io.IOUtils;

import java.io.*;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items,
    // etc, and register them with the GameRegistry."
    public void preInit(FMLPreInitializationEvent event) 	{
        RednetVerilogMod.info("I am " + Tags.MODNAME + " at version " + Tags.VERSION + " and group name " + Tags.GROUPNAME);

        File jsonFile = new File(event.getModConfigurationDirectory(), "rednetverilog_circuits.json");
        // https://github.com/lumien231/Custom-Main-Menu/blob/cc934c1d96691190d7479927735d58dac4c263a9/src/main/java/lumien/custommainmenu/configuration/ConfigurationLoader.java#L43
        InputStream input = null;
        try {
            input = getClass().getResourceAsStream("/assets/rednetverilog/circuits_default.json");
            RednetCodegen.SetupCircuits(input);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                IOUtils.closeQuietly(input);
            }
        }

        if (jsonFile.exists())
        {
            RednetCodegen.AddCircuits(jsonFile);
        }

    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes."
    public void init(FMLInitializationEvent event) {

    }

    // postInit "Handle interaction with other mods, complete your setup based on this."
    public void postInit(FMLPostInitializationEvent event) {

    }

    public void serverAboutToStart(FMLServerAboutToStartEvent event) {

    }

    // register server commands in this event handler
    public void serverStarting(FMLServerStartingEvent event) {

    }

    public void serverStarted(FMLServerStartedEvent event) {

    }

    public void serverStopping(FMLServerStoppingEvent event) {

    }

    public void serverStopped(FMLServerStoppedEvent event) {

    }
}

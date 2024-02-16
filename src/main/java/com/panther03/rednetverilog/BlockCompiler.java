package com.panther03.rednetverilog;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.world.World;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import com.panther03.compiler.Compiler;
import com.panther03.compiler.VerilogParser.VerilogLexer;
import com.panther03.compiler.VerilogParser.VerilogParser;

public class BlockCompiler extends Block {

    public static enum CompilerState {
        IDLE,
        RUNNING,
        COMPLETE
    }

    static CompilerState compilerState = CompilerState.IDLE;
    static NBTTagCompound result;

    public BlockCompiler(Material material) {
        super(material);
        this.setHardness(15.0F);
    }

    public static void compilerMsg(EntityPlayer player, String msg, int level) {
        String startText = EnumChatFormatting.DARK_GRAY + "[VerilogCompiler] ";

        String typeText;
        if (level == 2) {
            typeText = EnumChatFormatting.RED + "ERROR ";
        } else if (level == 1) {
            typeText = EnumChatFormatting.YELLOW + "WARN ";
        } else {
            typeText = EnumChatFormatting.GREEN + "INFO ";
        }

        player.addChatMessage(new ChatComponentTranslation(startText + typeText + EnumChatFormatting.WHITE + msg));
    }

    static class CompilerRunnable implements Runnable {

        ItemStack stack;
        EntityPlayer player;

        public CompilerRunnable(ItemStack stack, EntityPlayer player) {
            this.stack = stack;
            this.player = player;
        }

        public void compilerMsg(String msg, int level) {
            BlockCompiler.compilerMsg(player, msg, level);
        }

        private NBTBase compileProgram(String string) {
            CharStream input = CharStreams.fromString(string);
            VerilogLexer lexer;
            try {
                lexer = new VerilogLexer(input);
            } catch (Exception e) {
                compilerMsg("Lexer error: " + e.toString(), 2);
                return null;
            }
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            VerilogParser parser;
            try {
                parser = new VerilogParser(tokens);
            } catch (Exception e) {
                compilerMsg("Parser error: " + e.toString(), 2);
                return null;
            }

            ParseTree tree = parser.source_text();

            return Compiler.compile(tree);
        }

        public void downloadAndCompileProgram() {
            String name = stack.getDisplayName();
            URL url;
            try {
                url = new URL(name);
            } catch (MalformedURLException m) {
                compilerMsg("Malformed URL: " + name, 2);
                return;
            }
            Scanner s;
            try {
                s = new Scanner(url.openStream());
            } catch (IOException i) {
                compilerMsg("Could not read from URL: " + name + ". The error was: " + i, 2);
                return;
            }
            StringBuilder program = new StringBuilder();
            while (s.hasNextLine()) {
                program.append(s.nextLine())
                    .append(System.lineSeparator());
            }
            RednetVerilogMod.info("Downloaded program: " + program);

            NBTBase n;
            try {
                n = compileProgram(program.toString());
            } catch (Exception e) {
                compilerMsg("Exception during compilation: " + e, 2);
                return;
            }
            if (n == null) {
                compilerMsg("Exception during compilation; no tag data returned from compiler", 2);
            }
            compilerMsg(
                "Successfully compiled program! Right click block again to download program to memory card.",
                0);
            result = (NBTTagCompound) n;
            NBTTagCompound display = new NBTTagCompound();
            display.setString("Name", name);
            result.setTag("display", display);
            RednetVerilogMod.info("NBT tag: " + result);
        }

        public void run() {
            try {
                this.downloadAndCompileProgram();
            } finally {
                compilerState = CompilerState.COMPLETE;
            }
        }
    }

    @Override
    public synchronized boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int meta,
        float hitX, float hitY, float hitZ) {
        ItemStack heldItem = player.getHeldItem();
        if (heldItem == null) {
            return false;
        }
        if (!world.isRemote) {
            if (heldItem.getUnlocalizedName()
                .equals("item.mfr.rednet.memorycard")) {
                if (compilerState == CompilerState.COMPLETE) {
                    compilerState = CompilerState.IDLE;
                    if (result != null) {
                        compilerMsg(player, "Downloaded compiled program to memory card successfully!", 0);
                        player.getHeldItem()
                            .setTagCompound(result);
                        result = null;
                        return true;
                    }
                }
                if (compilerState == CompilerState.IDLE) {
                    compilerState = CompilerState.RUNNING;
                    compilerMsg(player, "Starting compilation...", 0);
                    CompilerRunnable r = new CompilerRunnable(heldItem, player);
                    Thread t = new Thread(r);
                    t.start();
                } else {
                    compilerMsg(player, "Compiler is busy!", 1);
                }
            }
            return true;
        }
        return true;
    }
}

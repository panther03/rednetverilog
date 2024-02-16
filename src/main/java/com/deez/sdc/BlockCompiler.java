package com.deez.sdc;

import com.deez.compiler.Compiler;
import com.deez.compiler.VerilogParser.VerilogLexer;
import com.deez.compiler.VerilogParser.VerilogParser;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Scanner;

public class BlockCompiler extends Block {
    public BlockCompiler(Material material) {
        super(material);
        this.setHardness(15.0F);
    }

    class CompilerRunnable implements Runnable {
        private static NBTBase compileProgram(String string) {
            CharStream input = CharStreams.fromString(string);
            VerilogLexer lexer;
            try {
                lexer = new VerilogLexer(input);
            } catch (Exception e) {
                MyMod.error("Lexer error: " + e.toString());
                return null;
            }
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            VerilogParser parser;
            try {
                parser = new VerilogParser(tokens);
            } catch (Exception e) {
                MyMod.error("parser error: " + e.toString());
                return null;
            }

            ParseTree tree = parser.source_text();
            MyMod.debug("Rule names: " + Arrays.toString(parser.getRuleNames()));
            MyMod.debug("Nodes: " + tree.toStringTree());

            return Compiler.compile(tree);
        }

        public void compileThread(ItemStack stack) {
            String name = stack.getDisplayName();
            URL url;
            try {
                url = new URL(name);
            } catch (MalformedURLException m) {
                MyMod.error("Malformed URL: " + name);
                return;
            }
            Scanner s;
            try {
                s = new Scanner(url.openStream());
            } catch (IOException i) {
                MyMod.error("Could not read from URL: " + name + ". The error was: " + i);
                return;
            }
            StringBuilder program = new StringBuilder();
            while (s.hasNextLine()) {
                program.append(s.nextLine()).append(System.lineSeparator());
            }
            MyMod.info("Downloaded program: " + program);

            NBTBase n;
            try {
                n = compileProgram(program.toString());
            } catch (Exception e) {
                MyMod.error("Exception during compilation: " + e);
                return;
            }
            if (n == null) {
                MyMod.error("Exception during compilation");
            }
            stack.setTagCompound(((NBTTagCompound)n));
        }
    }


    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int meta, float hitX, float hitY, float hitZ) {
        if (!world.isRemote){
            if (player.getHeldItem() != null) {
                compileThread(player.getHeldItem());
            }
            return true;
        }
        // deez
        return true;
    }
}

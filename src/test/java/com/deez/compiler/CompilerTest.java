package com.deez.compiler;


import com.deez.compiler.VerilogParser.VerilogLexer;
import com.deez.compiler.VerilogParser.VerilogPreParser;
import com.deez.compiler.VerilogParser.VerilogParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.gui.TreeViewer;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;


public class CompilerTest {




    @Test
    void simpleTest() {
        CharStream input;
        try {
            input = CharStreams.fromFileName("/home/julien/cursed.v");
        } catch (IOException e) {
            System.err.println("could not open test file: " + e.toString());
            return;
        }

        VerilogLexer lexer;
        try {
            lexer = new VerilogLexer(input);
        } catch (Exception e) {
            System.out.println("Lexer error: " + e.toString());
            return;
        }
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        for (Token token : tokens.getTokens()) {
            if (token.getChannel() == VerilogLexer.COMMENTS) {
                System.out.println("Comment: " + token.getText());
            }
        }
        tokens.seek(0);
        VerilogParser parser;
        try {
             parser = new VerilogParser(tokens);
        } catch (Exception e) {
            System.out.println("parser error: " + e.toString());
            return;
        }


        ParseTree tree = parser.source_text();
        System.out.println("Rule names: " + Arrays.toString(parser.getRuleNames()));
        System.out.println("Nodes: " + tree.toStringTree());

        Compiler.compile(tree);

        CountDownLatch latch = new CountDownLatch(1);

        JFrame frame = new JFrame("Antlr AST");
        JPanel panel = new JPanel();
        TreeViewer viewer = new TreeViewer(Arrays.asList(
                parser.getRuleNames()),tree);
        viewer.setScale(1.5); // Scale a little
        panel.add(viewer);
        frame.add(panel);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                latch.countDown(); // Release the latch when the window is closing
            }
        });
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        try {
            latch.await(); // Block until the latch is released
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}


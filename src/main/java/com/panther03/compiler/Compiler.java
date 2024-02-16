package com.panther03.compiler;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTBase;

import org.antlr.v4.runtime.tree.ParseTree;

public class Compiler {

    public static class PassData {

        protected HashMap<String, PortExtraction.Port> portMap;

        public PassData() {
            this.portMap = new HashMap<>();
        }

        public void printMap() {
            System.out.println("map:");
            for (Map.Entry<String, PortExtraction.Port> entry : this.portMap.entrySet()) {
                String key = entry.getKey();
                PortExtraction.Port value = entry.getValue();
                System.out.println("Key=" + key + ", Value=" + value.toString());
            }
        }
    }

    public static NBTBase compile(ParseTree tree) {
        PassData pd = new PassData();
        PortExtraction pe = new PortExtraction(pd);
        pe.visit(tree);
        pd.printMap();
        RednetCodegen rc = new RednetCodegen(pd);
        return rc.visit(tree);
    }
}

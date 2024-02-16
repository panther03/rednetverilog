package com.panther03.compiler;

import java.util.*;
import java.util.stream.Collectors;

import net.minecraft.nbt.NBTTagCompound;

import com.panther03.compiler.VerilogParser.*;

public class PortExtraction extends VerilogParserBaseVisitor<Void> {

    public enum Direction {
        DOWN,
        UP,
        LEFT,
        RIGHT,
        FRONT,
        BACK
    }

    private final List<String> directionNames = Arrays.stream(Direction.values())
        .map(Enum::name)
        .collect(Collectors.toList());

    public enum Color {
        WHITE,
        ORANGE,
        MAGENTA,
        LIGHTBLUE,
        YELLOW,
        LIME,
        PINK,
        GRAY,
        LIGHTGRAY,
        CYAN,
        PURPLE,
        BLUE,
        BROWN,
        GREEN,
        RED,
        BLACK
    }

    private final List<String> colorNames = Arrays.stream(Color.values())
        .map(Enum::name)
        .collect(Collectors.toList());

    public abstract class Port {

        private boolean isAnalog;

        public boolean isAnalog() {
            return isAnalog;
        }

        public void setAnalog() {
            this.isAnalog = true;
        }

        abstract public NBTTagCompound codegen(int index);

        abstract public int wireSize();
    }

    public class IOPort extends Port {

        private boolean isInput;
        private Direction dir;
        private List<Color> colors;

        @Override
        public String toString() {
            return "IOPort{" + "isInput="
                + isInput
                + ", dir="
                + dir
                + ", colors="
                + colors
                + ", isAnalog="
                + isAnalog()
                + '}';
        }

        public NBTTagCompound codegen(int index) {
            if (index > colors.size()) {
                throw new RuntimeException("Access beyond size of I/O port: " + index + " > " + colors.size());
            }
            Color color = colors.get(index);
            NBTTagCompound n = new NBTTagCompound();
            int offset = isInput ? 0 : 6;
            n.setInteger("buffer", offset + dir.ordinal());
            n.setInteger("pin", color.ordinal());
            return n;
        }

        public int wireSize() {
            return colors.size();
        }
    }

    public class VarPort extends Port {

        private int varIdStart;
        private int wireCnt;

        public void setVarIdStart(int varIdStart) {
            this.varIdStart = varIdStart;
        }

        public void setWireCnt(int wireCnt) {
            this.wireCnt = wireCnt;
        }

        @Override
        public String toString() {
            return "VarPort{" + "varIdStart=" + varIdStart + ", wireCnt=" + wireCnt + ", isAnalog=" + isAnalog() + '}';
        }

        public NBTTagCompound codegen(int index) {
            if (index > wireCnt || index < 0) {
                throw new RuntimeException("Access beyond size of variable port: " + index + " > " + wireCnt);
            }
            NBTTagCompound n = new NBTTagCompound();
            n.setInteger("buffer", 13);
            n.setInteger("pin", varIdStart + index);
            return n;
        }

        public int wireSize() {
            return wireCnt;
        }
    }

    private class AttributeProps {

        public boolean isAnalog;
        public Direction side;
        public List<Color> colors;

        public AttributeProps() {
            this.colors = new ArrayList<>();
            this.side = null;
        }
    }

    private Compiler.PassData pd;
    private AttributeProps propsTracker;
    private int varCounter;

    public PortExtraction(Compiler.PassData pd) {
        super();
        this.pd = pd;
        this.varCounter = 0;
    }

    @Override
    public Void visitModule_instantiation(VerilogParser.Module_instantiationContext ctx) {
        System.out.println(
            "Instantiating:" + ctx.getChild(0)
                .getChild(0)
                .getChild(0)
                .getChild(0)
                .getText());
        return super.visitModule_instantiation(ctx);
    }

    @Override
    public Void visitModule_or_generate_item(VerilogParser.Module_or_generate_itemContext ctx) {
        VerilogParser.Attribute_instanceContext attr = ctx.attribute_instance(0);
        propsTracker = null;
        if (attr != null) {
            propsTracker = visitAttribute_collectProps(attr);
        }
        VerilogParser.Module_or_generate_item_declarationContext mgd = ctx.module_or_generate_item_declaration();
        if (mgd != null) {
            visit(mgd);
        }
        propsTracker = null;
        return null;
    }

    @Override
    public Void visitPort_declaration(VerilogParser.Port_declarationContext ctx) {
        VerilogParser.Attribute_instanceContext attr = ctx.attribute_instance(0);
        propsTracker = null;
        if (attr != null) {
            propsTracker = visitAttribute_collectProps(attr);
        }
        try {
            if (ctx.input_declaration() != null) {
                visit(ctx.input_declaration());
            } else if (ctx.output_declaration() != null) {
                visit(ctx.output_declaration());
            } else {
                throw new RuntimeException("Inout not supported.");
            }
        } finally {
            propsTracker = null;
        }
        return null;
    }

    private AttributeProps visitAttribute_collectProps(VerilogParser.Attribute_instanceContext ctx) {
        AttributeProps ap = new AttributeProps();
        for (VerilogParser.Attr_specContext sc : ctx.attr_spec()) {
            String an = sc.attr_name()
                .getText();
            if (an.equals("analog") && (sc.constant_expression() == null)) {
                ap.isAnalog = true;
            } else if (an.equals("side") && (sc.constant_expression() != null)) {
                if (sc.constant_expression()
                    .constant_primary() == null
                    || (sc.constant_expression()
                        .constant_primary()
                        .identifier() == null)) {
                    throw new RuntimeException(
                        "Side attribute must be a constant identifier (UP, DOWN, LEFT, RIGHT, BACK, FRONT).");
                }
                String sideText = sc.constant_expression()
                    .constant_primary()
                    .identifier()
                    .getText();
                int sideInd = directionNames.indexOf(sideText);
                if (sideInd == -1) {
                    throw new RuntimeException(
                        "Side " + sideText + " not recognized. Must be one of: " + directionNames);
                }
                ap.side = Direction.values()[sideInd];
            } else if (an.equals("colors") && (sc.constant_expression() != null)) {
                visitConstantExpression_Color(sc.constant_expression(), ap);
            }
        }
        return ap;
    }

    private void visitConstantExpression_Color(VerilogParser.Constant_expressionContext ctx, AttributeProps ap) {
        final String ERR_MESSAGE = "Color should be a constant identifier or list of identifiers {RED, BLUE, ...}";
        if (ctx.constant_primary() == null) {
            throw new RuntimeException(ERR_MESSAGE);
        }
        VerilogParser.Constant_primaryContext cp_ctx = ctx.constant_primary();
        if (cp_ctx.identifier() != null) {
            String colorText = cp_ctx.identifier()
                .getText();
            int sideInd = colorNames.indexOf(colorText);
            if (sideInd == -1) {
                throw new RuntimeException("Color name" + colorText + "not recognized. Must be one of: " + colorNames);
            }
            ap.colors.add(Color.values()[sideInd]);
        } else if (cp_ctx.constant_concatenation() != null) {
            List<VerilogParser.Constant_expressionContext> exprs = cp_ctx.constant_concatenation()
                .constant_expression();
            for (VerilogParser.Constant_expressionContext expr : exprs) {
                visitConstantExpression_Color(expr, ap);
            }
        } else {
            throw new RuntimeException(ERR_MESSAGE);
        }
    }

    @Override
    public Void visitReg_declaration(VerilogParser.Reg_declarationContext ctx) {
        VarPort vp = new VarPort();

        VerilogParser.List_of_variable_identifiersContext lic = ctx.list_of_variable_identifiers();
        VerilogParser.Variable_typeContext vt = lic.variable_type()
            .get(0);
        VerilogParser.Range_Context r = ctx.range_();
        String vp_name = vt.getText();

        vp.setWireCnt(computeRangeSize(r));
        if (pd.portMap.get(vp_name) != null) {
            throw new RuntimeException("Duplicate variable declaration: " + vp_name);
        }
        vp.setVarIdStart(varCounter);
        if (propsTracker != null && propsTracker.isAnalog) {
            vp.setAnalog();
        }
        varCounter += vp.wireCnt;
        pd.portMap.put(vp_name, vp);
        return super.visitReg_declaration(ctx);
    }

    public static int computeRangeSize(VerilogParser.Range_Context r) {
        if (r == null) {
            return 1;
        } else {
            VerilogParser.Constant_expressionContext ub = r.msb_constant_expression()
                .constant_expression();
            VerilogParser.Constant_expressionContext lb = r.lsb_constant_expression()
                .constant_expression();
            if ((ub.constant_primary() == null) || (lb.constant_primary() == null)) {
                throw new RuntimeException("Ranges should be constants.");
            }
            int ub_n = Integer.parseInt(ub.getText());
            int lb_n = Integer.parseInt(lb.getText());
            if (ub_n - lb_n < 0) {
                throw new RuntimeException("Use [ MSB : LSB ] syntax where MSB > LSB.");
            }
            return ub_n - lb_n + 1;
        }
    }

    @Override
    public Void visitInput_declaration(VerilogParser.Input_declarationContext ctx) {
        VerilogParser.Port_identifierContext identifier = ctx.list_of_port_identifiers()
            .port_identifier(0);
        VerilogParser.Range_Context range = ctx.range_();
        visitInputOutputDeclaration(true, range, identifier);
        return null;
    }

    @Override
    public Void visitOutput_declaration(VerilogParser.Output_declarationContext ctx) {
        VerilogParser.Port_identifierContext identifier = ctx.list_of_port_identifiers()
            .port_identifier(0);
        VerilogParser.Range_Context range = ctx.range_();
        visitInputOutputDeclaration(false, range, identifier);
        return null;
    }

    private void visitInputOutputDeclaration(boolean isInput, VerilogParser.Range_Context rctx,
        VerilogParser.Port_identifierContext pctx) {
        if (propsTracker == null) {
            throw new RuntimeException(
                "Expected module port declaration to have an attribute declaration with color or list of colors, and side direction.");
        } else if (propsTracker.colors.isEmpty()) {
            throw new RuntimeException("Expected a color or list of colors in attribute for module port.");
        } else if (propsTracker.side == null) {
            throw new RuntimeException("Expected a side declaration in attribute for module port.");
        }

        IOPort ip = new IOPort();
        ip.isInput = isInput;
        ip.dir = propsTracker.side;
        ip.colors = propsTracker.colors;

        int rs = computeRangeSize(rctx);
        if (rs != ip.colors.size()) {
            throw new RuntimeException(
                "Size of port (" + rs + ") does not match amount of colors specified (" + ip.colors.size() + ").");
        }

        String ip_name = pctx.getText();
        if (pd.portMap.get(ip_name) != null) {
            throw new RuntimeException("Duplicate port declaration: " + ip_name);
        }
        if (propsTracker.isAnalog) {
            ip.setAnalog();
        }
        pd.portMap.put(ip_name, ip);
    }
}

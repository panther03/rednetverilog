package com.panther03.compiler;

import com.panther03.compiler.VerilogParser.VerilogParser;
import com.panther03.compiler.VerilogParser.VerilogParserBaseVisitor;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.*;


public class RednetCodegen extends VerilogParserBaseVisitor<NBTBase> {

    private static class PortSpec {
        boolean isInput;
        boolean isAnalog;
        int width;

        public PortSpec(boolean isInput, boolean isAnalog, int width) {
            this.isInput = isInput;
            this.isAnalog = isAnalog;
            this.width = width;
        }
    }

    private static class CircuitSpec {
        public List<String> portNames;
        public List<PortSpec> portSpecs;
        public String circuitName;

        public static NBTTagCompound newPin(int buffer, int pin) {
            NBTTagCompound n = new NBTTagCompound();
            n.setInteger("pin", pin);
            n.setInteger("buffer", buffer);
            return n;
        }

        public static int extractNumber(VerilogParser.NumberContext ctx) {
            final String BAD_NUMBER_TEXT = "Constants should be unsigned decimal numbers with no base, between 0 and 9999.";
            int extractedNumber;
            try {
                extractedNumber = Integer.parseInt(ctx.decimal_number().unsigned_number().getText());
            } catch (Exception e) {
                throw new RuntimeException(BAD_NUMBER_TEXT);
            }

            if ((extractedNumber < 0) || (extractedNumber > 9999)) {
                throw new RuntimeException(BAD_NUMBER_TEXT);
            }
            return extractedNumber;
        }

        public static int extractRangeStart(VerilogParser.Select_Context ctx) {
            VerilogParser.Range_expressionContext rctx = ctx.range_expression();
            try {
                return Integer.parseInt(rctx.lsb_constant_expression().constant_expression().constant_primary().number().decimal_number().unsigned_number().getText());
            } catch (Exception e) {
                try {
                    return Integer.parseInt(rctx.expression().primary().number().decimal_number().unsigned_number().getText());
                } catch (Exception f) {
                    throw new RuntimeException("Unrecognized range expression: "  + rctx);
                }
            }
        }

        public static int extractRangeEnd(VerilogParser.Select_Context ctx) {
            VerilogParser.Range_expressionContext rctx = ctx.range_expression();
            try {
                return Integer.parseInt(rctx.msb_constant_expression().constant_expression().constant_primary().number().decimal_number().unsigned_number().getText());
            } catch (Exception e) {
                try {
                    return Integer.parseInt(rctx.expression().primary().number().decimal_number().unsigned_number().getText());
                } catch (Exception f) {
                    throw new RuntimeException("Unrecognized range expression: "  + rctx);
                }
            }
        }

        public NBTBase codegen(VerilogParser.List_of_port_connectionsContext ctx, int index, Compiler.PassData pd) {
            NBTTagCompound n = new NBTTagCompound();
            if (ctx.ordered_port_connection() != null && ctx.named_port_connection() == null) {
                throw new RuntimeException("Use named port connections instead.");
            }
            Map<String,NBTTagCompound> inputPorts = new HashMap<>();
            Map<String,NBTTagCompound> outputPorts = new HashMap<>();
            for (VerilogParser.Named_port_connectionContext pctx : ctx.named_port_connection()) {
                String circuitPortName = pctx.port_identifier().getText();
                int circuitPortInd = portNames.indexOf(circuitPortName);
                if (circuitPortInd == -1) {
                    throw new RuntimeException("Unrecognized port " + circuitPortName + " on circuit " + circuitName);
                }
                PortSpec circuitPort = portSpecs.get(circuitPortInd);

                if (index >= circuitPort.width && circuitPort.width > 1) {
                    throw new RuntimeException("Cannot perform vectored instantiation with multi-bit circuit connections. " + ctx.start.getLine());
                }

                for (int i = 0; i < circuitPort.width; i++) {
                    // xd
                    String circuitPortKey = circuitPortName + "_" + i;

                    if (inputPorts.containsKey(circuitPortKey) || outputPorts.containsKey(circuitPortKey)) {
                        throw new RuntimeException("Duplicate port in module: " + circuitPortKey);
                    }

                    // TODO move this out but im lazy
                    VerilogParser.ExpressionContext ectx = pctx.expression();
                    if (ectx == null) {
                        if (circuitPort.isInput) {
                            throw new RuntimeException("Input port " + circuitPortName + " cannot be left empty.");
                        }
                        outputPorts.put(circuitPortKey, newPin(0,0));
                        continue;
                    } else if (ectx.primary() == null) {
                        throw new RuntimeException("Port expression should be a constant or identifier with optional bit selection.");
                    }

                    if (ectx.primary().number() != null) {
                        if (!circuitPort.isInput) {
                            throw new RuntimeException("Output port " + circuitPortName + "cannot be a constant.");
                        }
                        int pin = extractNumber(ectx.primary().number());
                        int buffer = 12;
                        inputPorts.put(circuitPortKey, newPin(buffer, pin));
                    } else if (ectx.primary().hierarchical_identifier() != null) {
                        NBTTagCompound primNbt;
                        String wiredPortName = ectx.primary().hierarchical_identifier().getText();
                        PortExtraction.Port wiredPort = pd.portMap.get(wiredPortName);
                        if (wiredPort == null) {
                            throw new RuntimeException("Undefined port " + wiredPortName);
                        }

                        VerilogParser.Select_Context sctx = ectx.primary().select_();
                        if (sctx == null) {
                            // Single-wire ports always work even in vectored instantiations;
                            // we simply duplicate the single wire
                            if (i > wiredPort.wireSize()) {
                                primNbt = newPin(0, 0);
                            } else if (wiredPort.wireSize() == 1) {
                                primNbt = wiredPort.codegen(0);
                            } else {
                                primNbt = wiredPort.codegen(index + i);
                            }
                        } else {
                            if (i != 0) {
                                throw new RuntimeException("Can't index into multi-bit port connection.");
                            }
                            int start = extractRangeStart(sctx);
                            int end = extractRangeEnd(sctx);
                            if ((end - start + 1) != 1 && (end - start + 1) != circuitPort.width) {
                                throw new RuntimeException("Port " + circuitPortName + " is " + end + " down to " + start + " but needs to be of length 1 or " + circuitPort.width);
                            }
                            primNbt = wiredPort.codegen(index + start);
                        }
                        if (circuitPort.isInput) {
                            inputPorts.put(circuitPortKey, primNbt);
                        } else {
                            outputPorts.put(circuitPortKey, primNbt);
                        }
                    }
                }
            }

            NBTTagList inputPins = new NBTTagList();
            NBTTagList outputPins = new NBTTagList();
            for (int i = 0; i < portNames.size(); i++){
                PortSpec circuitPortSpec = portSpecs.get(i);
                String circuitPortName = portNames.get(i);
                for (int j = 0; j < circuitPortSpec.width; j++) {
                    String circuitPortKey = circuitPortName + "_" + j;
                    boolean haveInputPort = (inputPorts.get(circuitPortKey) != null);
                    boolean haveOutputPort = (outputPorts.get(circuitPortKey) != null);
                    if (!haveInputPort && !haveOutputPort) {
                        if (!circuitPortSpec.isInput) {
                            inputPins.appendTag(newPin(0,0));
                        } else {
                            throw new RuntimeException("Input port " + circuitPortName + " cannot be left unconnected.");
                        }
                    } else {
                        if (circuitPortSpec.isInput) {
                            inputPins.appendTag(inputPorts.get(circuitPortKey));
                        } else {
                            outputPins.appendTag(outputPorts.get(circuitPortKey));
                        }
                    }
                }
            }

            n.setString("circuit", circuitName);
            n.setTag("inputPins", inputPins);
            n.setTag("outputPins", outputPins);
            return n;
        }

        public CircuitSpec(List<String> portNames, List<PortSpec> portSpecs, String circuitName) {
            if (portNames.size() != portSpecs.size()) {
                throw new RuntimeException("Circuit port names and specs should match in length");
            }
            this.portNames = portNames;
            this.portSpecs = portSpecs;
            this.circuitName = circuitName;
        }
    }
    private static final CircuitSpec TWO_AND = new CircuitSpec(
            new ArrayList<>(Arrays.asList("I0", "I1", "O")),
            new ArrayList<>(Arrays.asList(
                    new PortSpec(true, false, 1),
                    new PortSpec(true, false, 1),
                    new PortSpec(false, false, 1)
            )),
            "powercrystals.minefactoryreloaded.circuits.logic.And2"
    );
    private static final CircuitSpec THREE_AND = new CircuitSpec(
            new ArrayList<>(Arrays.asList("I0", "I1", "I2", "O")),
            new ArrayList<>(Arrays.asList(
                    new PortSpec(true, false, 1),
                    new PortSpec(true, false, 1),
                    new PortSpec(true, false, 1),
                    new PortSpec(false, false, 1)
                    )),
            "powercrystals.minefactoryreloaded.circuits.logic.And3"
    );
    private static final CircuitSpec ANALOG_RANDOMIZER = new CircuitSpec(
            new ArrayList<>(Arrays.asList("min", "max", "q")),
            new ArrayList<>(Arrays.asList(
                    new PortSpec(true, true, 1),
                    new PortSpec(true, true, 1),
                    new PortSpec(false, false, 1)
            )),
            "powercrystals.minefactoryreloaded.circuits.analog.RandomizerAnalog"
    );

    private static final CircuitSpec DEMUX_SIXTEEN = new CircuitSpec(
            new ArrayList<>(Arrays.asList("I", "S", "O")),
            new ArrayList<>(Arrays.asList(
                    new PortSpec(true, false, 1),
                    new PortSpec(true, true, 1),
                    new PortSpec(false, false, 16)
            )),
            "powercrystals.minefactoryreloaded.circuits.digital.DeMux16Analog"
    );

    private static final CircuitSpec DELAY = new CircuitSpec(
            new ArrayList<>(Arrays.asList("I", "D", "O")),
            new ArrayList<>(Arrays.asList(
                    new PortSpec(true, false, 1),
                    new PortSpec(true, true, 1),
                    new PortSpec(false, false, 1)
            )),
            "powercrystals.minefactoryreloaded.circuits.timing.Delay"
    );

    private static final CircuitSpec ANALOG_DELAY = new CircuitSpec(
            new ArrayList<>(Arrays.asList("I", "D", "O")),
            new ArrayList<>(Arrays.asList(
                    new PortSpec(true, true, 1),
                    new PortSpec(true, true, 1),
                    new PortSpec(false, true, 1)
            )),
            "powercrystals.minefactoryreloaded.circuits.timing.Delay"
    );


    private Compiler.PassData pd;

    public RednetCodegen(Compiler.PassData pd) {
        super();
        this.pd = pd;
    }

    @Override
    protected NBTBase aggregateResult(NBTBase aggregate, NBTBase nextResult) {
        if (aggregate != null) {
            return aggregate;
        } else {
            return nextResult;
        }
    }

    @Override
    public NBTBase visitModule_declaration(VerilogParser.Module_declarationContext ctx) {
        System.out.println("Visiting a module declaration");
        NBTTagCompound n = new NBTTagCompound();
        // TODO: what is p_rot?
        n.setByte("p_rot", ((byte)2));
        n.setString("Type", "tile.mfr.rednet.logic.name");
        NBTTagList nl = new NBTTagList();
        for (VerilogParser.Module_itemContext ictx: ctx.module_item()) {
            if (ictx.module_or_generate_item() != null && ictx.module_or_generate_item().module_instantiation() != null) {
                visitModule_instantiation_addNBT(ictx.module_or_generate_item().module_instantiation(), nl);
            }
        }
        n.setTag("circuits", nl);
        System.out.println("result nbt1: " + n.toString());
        return n;
    }

    public void visitModule_instantiation_addNBT(VerilogParser.Module_instantiationContext ctx, NBTTagList nl) {
        String circuit_name = ctx.module_identifier().getText();
        VerilogParser.Range_Context range = ctx.module_instance(0).name_of_module_instance().range_();
        int range_size = PortExtraction.computeRangeSize(range);

        CircuitSpec spec;
        switch (circuit_name) {
            case "ThreeAnd":
                spec = THREE_AND;
                break;
            case "AnalogRandomizer":
                spec = ANALOG_RANDOMIZER;
                break;
            case "DeMux16":
                spec = DEMUX_SIXTEEN;
                break;
            case "TwoAnd":
                spec = TWO_AND;
                break;
            case "Delay":
                spec = DELAY;
                break;
            case "AnalogDelay":
                spec = ANALOG_DELAY;
                break;
            default:
                throw new RuntimeException("Unrecognized circuit " + circuit_name);
        }
        VerilogParser.List_of_port_connectionsContext conns_ctx = ctx.module_instance(0).list_of_port_connections();
        for (int i = 0; i < range_size; i++) {
            nl.appendTag(spec.codegen(conns_ctx, i, pd));
        }
    }

}

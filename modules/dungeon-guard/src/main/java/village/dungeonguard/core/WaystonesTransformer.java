package village.dungeonguard.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.IdentityHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import village.dungeonguard.core.HookStatus;

public final class WaystonesTransformer
implements Opcodes {
    private static final String OWNER = "net/blay09/mods/waystones/WaystoneManager";
    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String ENTRY = "net/blay09/mods/waystones/util/WaystoneEntry";
    private static final String RUNTIME = "village/dungeonguard/DungeonWaystones";
    private static final String METHOD = "teleportToWaystone";
    private static final String HELPER = "dungeonguard$original$teleportToWaystone";
    private static final String DESC = "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/blay09/mods/waystones/util/WaystoneEntry;)Z";
    private static final String ALLOW_DESC = "(Lnet/minecraft/entity/player/EntityPlayer;Ljava/lang/Object;)Z";
    private static final String FINISH_DESC = "(ZLnet/minecraft/entity/player/EntityPlayer;Ljava/lang/Object;)Z";
    private static final String ORIGINAL_SHAPE = "baaa72cc55c4f0c8a1a8694da542fa15e557ae94fd72a69a2e3a289f637b1e77";

    private WaystonesTransformer() {
    }

    public static byte[] apply(String transformedName, byte[] original) {
        if (original == null || transformedName == null || !OWNER.equals(transformedName.replace('.', '/'))) {
            return original;
        }
        ClassReader reader = new ClassReader(original);
        ClassNode type = new ClassNode(327680);
        reader.accept((ClassVisitor)type, 8);
        if (!OWNER.equals(type.name)) {
            throw WaystonesTransformer.unsupported("unexpected owner");
        }
        MethodNode target = null;
        MethodNode helper = null;
        for (MethodNode method : type.methods) {
            if (METHOD.equals(method.name)) {
                if (target != null) {
                    throw WaystonesTransformer.unsupported("ambiguous travel method");
                }
                target = method;
            }
            if (!HELPER.equals(method.name)) continue;
            if (helper != null) {
                throw WaystonesTransformer.unsupported("ambiguous travel helper");
            }
            helper = method;
        }
        if (target == null || !DESC.equals(target.desc) || target.access != 9) {
            throw WaystonesTransformer.unsupported("missing or changed public static travel signature");
        }
        if (helper != null) {
            if (!(DESC.equals(helper.desc) && helper.access == 4106 && ORIGINAL_SHAPE.equals(WaystonesTransformer.shape(helper)) && WaystonesTransformer.shape(target).equals(WaystonesTransformer.shape(WaystonesTransformer.wrapper(target))))) {
                throw WaystonesTransformer.unsupported("unrecognized preexisting travel wrapper");
            }
            HookStatus.installed("Waystones.teleportToWaystone");
            return original;
        }
        if (!ORIGINAL_SHAPE.equals(WaystonesTransformer.shape(target))) {
            throw WaystonesTransformer.unsupported("unrecognized Waystones 4.1.0 travel structure");
        }
        MethodNode guarded = WaystonesTransformer.wrapper(target);
        guarded.visibleAnnotations = target.visibleAnnotations;
        guarded.invisibleAnnotations = target.invisibleAnnotations;
        guarded.visibleParameterAnnotations = target.visibleParameterAnnotations;
        guarded.invisibleParameterAnnotations = target.invisibleParameterAnnotations;
        guarded.visibleTypeAnnotations = target.visibleTypeAnnotations;
        guarded.invisibleTypeAnnotations = target.invisibleTypeAnnotations;
        guarded.parameters = target.parameters;
        target.visibleAnnotations = null;
        target.invisibleAnnotations = null;
        target.visibleParameterAnnotations = null;
        target.invisibleParameterAnnotations = null;
        target.visibleTypeAnnotations = null;
        target.invisibleTypeAnnotations = null;
        target.name = HELPER;
        target.access = 4106;
        type.methods.add(guarded);
        ClassWriter output = new ClassWriter(reader, 1);
        type.accept((ClassVisitor)output);
        byte[] result = output.toByteArray();
        HookStatus.installed("Waystones.teleportToWaystone");
        return result;
    }

    private static MethodNode wrapper(MethodNode original) {
        MethodNode result = new MethodNode(327680, 9, METHOD, DESC, original.signature, original.exceptions.toArray(new String[original.exceptions.size()]));
        result.instructions.add((AbstractInsnNode)new VarInsnNode(25, 0));
        result.instructions.add((AbstractInsnNode)new VarInsnNode(25, 1));
        result.instructions.add((AbstractInsnNode)new MethodInsnNode(184, RUNTIME, "allowTravel", ALLOW_DESC, false));
        LabelNode allowed = new LabelNode();
        result.instructions.add((AbstractInsnNode)new JumpInsnNode(154, allowed));
        result.instructions.add((AbstractInsnNode)new InsnNode(3));
        result.instructions.add((AbstractInsnNode)new InsnNode(172));
        result.instructions.add((AbstractInsnNode)allowed);
        result.instructions.add((AbstractInsnNode)new FrameNode(-1, 2, new Object[]{PLAYER, ENTRY}, 0, new Object[0]));
        result.instructions.add((AbstractInsnNode)new VarInsnNode(25, 0));
        result.instructions.add((AbstractInsnNode)new VarInsnNode(25, 1));
        result.instructions.add((AbstractInsnNode)new MethodInsnNode(184, OWNER, HELPER, DESC, false));
        result.instructions.add((AbstractInsnNode)new VarInsnNode(25, 0));
        result.instructions.add((AbstractInsnNode)new VarInsnNode(25, 1));
        result.instructions.add((AbstractInsnNode)new MethodInsnNode(184, RUNTIME, "travelFinished", FINISH_DESC, false));
        result.instructions.add((AbstractInsnNode)new InsnNode(172));
        result.maxLocals = 2;
        result.maxStack = 3;
        return result;
    }

    private static String shape(MethodNode method) {
        StringBuilder text = new StringBuilder(method.desc).append('|');
        for (String exception : method.exceptions) {
            text.append(exception).append(';');
        }
        if (!method.tryCatchBlocks.isEmpty()) {
            throw WaystonesTransformer.unsupported("unexpected travel exception handler");
        }
        IdentityHashMap<LabelNode, Integer> labels = new IdentityHashMap<LabelNode, Integer>();
        int position = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof LabelNode) {
                labels.put((LabelNode)instruction, position);
                continue;
            }
            if (instruction.getOpcode() < 0) continue;
            ++position;
        }
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            TableSwitchInsnNode table;
            int opcode = instruction.getOpcode();
            if (opcode < 0) continue;
            text.append(opcode).append(':');
            if (instruction instanceof VarInsnNode) {
                text.append(((VarInsnNode)instruction).var);
            } else if (instruction instanceof IntInsnNode) {
                text.append(((IntInsnNode)instruction).operand);
            } else if (instruction instanceof TypeInsnNode) {
                text.append(((TypeInsnNode)instruction).desc);
            } else if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)instruction;
                text.append(field.owner).append('.').append(WaystonesTransformer.member(field.owner, field.name)).append(field.desc);
            } else if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                text.append(call.owner).append('.').append(WaystonesTransformer.member(call.owner, call.name)).append(call.desc).append(call.itf);
            } else if (instruction instanceof JumpInsnNode) {
                text.append(labels.get(((JumpInsnNode)instruction).label));
            } else if (instruction instanceof LdcInsnNode) {
                Object constant = ((LdcInsnNode)instruction).cst;
                text.append(constant.getClass().getName()).append('=').append(constant);
            } else if (instruction instanceof IincInsnNode) {
                IincInsnNode increment = (IincInsnNode)instruction;
                text.append(increment.var).append(',').append(increment.incr);
            } else if (instruction instanceof TableSwitchInsnNode) {
                table = (TableSwitchInsnNode)instruction;
                text.append(table.min).append(',').append(table.max).append(',').append(labels.get(table.dflt));
                for (LabelNode label : table.labels) {
                    text.append(',').append(labels.get(label));
                }
            } else if (instruction instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode lookup = (LookupSwitchInsnNode)instruction;
                text.append(labels.get(lookup.dflt));
                for (int i = 0; i < lookup.keys.size(); ++i) {
                    text.append(',').append(lookup.keys.get(i)).append('=').append(labels.get(lookup.labels.get(i)));
                }
            } else if (instruction instanceof MultiANewArrayInsnNode) {
                MultiANewArrayInsnNode array = (MultiANewArrayInsnNode)instruction;
                text.append(array.desc).append(',').append(array.dims);
            } else if (!(instruction instanceof InsnNode)) {
                throw WaystonesTransformer.unsupported("unsupported travel instruction");
            }
            text.append(';');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xFF));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError((Object)impossible);
        }
    }

    private static String member(String owner, String name) {
        String[][] aliases;
        if (!owner.startsWith("net/minecraft/")) {
            return name;
        }
        for (String[] pair : aliases = new String[][]{{"getStyle", "func_150256_b"}, {"setColor", "func_150238_a"}, {"sendMessage", "func_145747_a"}, {"getBlockState", "func_180495_p"}, {"getValue", "func_177229_b"}, {"offset", "func_177972_a"}, {"getEntityWorld", "func_130014_f_"}, {"provider", "field_73011_w"}}) {
            if (!pair[0].equals(name)) continue;
            return pair[1];
        }
        return name;
    }

    private static IllegalStateException unsupported(String detail) {
        return new IllegalStateException("Dungeon Guard cannot safely protect Waystones travel: " + detail);
    }
}


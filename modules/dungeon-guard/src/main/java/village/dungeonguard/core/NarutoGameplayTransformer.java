package village.dungeonguard.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.IdentityHashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
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

public final class NarutoGameplayTransformer {
    public static final String TARGET = "net.narutomod.procedure.ProcedureKGDistribution";
    private static final String OWNER = "net.narutomod.procedure.ProcedureKGDistribution".replace('.', '/');
    private static final String HOOK = "village/dungeonguard/NarutoGameplayCompat";
    private static final String DESC = "(Ljava/util/Map;)V";
    private static final String GATE_DESC = "(Ljava/util/Map;)Z";
    private static final String EXPECTED_SHAPE = "d492a65bd5ac36e829c13c63cb1fc49d378c5616ed0d6fc2f7a6e19116a9de78";

    private NarutoGameplayTransformer() {
    }

    public static byte[] apply(String transformedName, byte[] original) {
        String shape;
        boolean installed;
        if (original == null || !TARGET.equals(transformedName)) {
            return original;
        }
        ClassReader reader = new ClassReader(original);
        ClassNode type = new ClassNode(327680);
        reader.accept((ClassVisitor)type, 8);
        if (!OWNER.equals(type.name)) {
            throw NarutoGameplayTransformer.unsupported("Unexpected KG owner");
        }
        MethodNode method = NarutoGameplayTransformer.target(type);
        AbstractInsnNode start = NarutoGameplayTransformer.meaningful(method.instructions.getFirst());
        int count = 0;
        AbstractInsnNode node = start;
        while (node != null) {
            if (NarutoGameplayTransformer.gate(node)) {
                ++count;
            }
            node = NarutoGameplayTransformer.next(node);
        }
        boolean bl = installed = count > 0;
        if (installed) {
            AbstractInsnNode call = NarutoGameplayTransformer.next(start);
            AbstractInsnNode branch = NarutoGameplayTransformer.next(call);
            AbstractInsnNode returned = NarutoGameplayTransformer.next(branch);
            if (!(count == 1 && start instanceof VarInsnNode && start.getOpcode() == 25 && ((VarInsnNode)start).var == 0 && NarutoGameplayTransformer.gate(call) && branch instanceof JumpInsnNode && branch.getOpcode() == 154 && returned != null && returned.getOpcode() == 177 && NarutoGameplayTransformer.meaningful((AbstractInsnNode)((JumpInsnNode)branch).label) == NarutoGameplayTransformer.next(returned))) {
                throw NarutoGameplayTransformer.unsupported("Unrecognized existing KG gate");
            }
            start = NarutoGameplayTransformer.next(returned);
        }
        if (!EXPECTED_SHAPE.equals(shape = NarutoGameplayTransformer.shape(method, start))) {
            throw NarutoGameplayTransformer.unsupported("KG distribution differs from verified AHZNB 0.3.1-beta (" + shape + ")");
        }
        if (installed) {
            HookStatus.installed("AHZNB.kgEligibility");
            return original;
        }
        LabelNode allowed = new LabelNode();
        InsnList prefix = new InsnList();
        prefix.add((AbstractInsnNode)new VarInsnNode(25, 0));
        prefix.add((AbstractInsnNode)new MethodInsnNode(184, HOOK, "allowKgDistribution", GATE_DESC, false));
        prefix.add((AbstractInsnNode)new JumpInsnNode(154, allowed));
        prefix.add((AbstractInsnNode)new InsnNode(177));
        prefix.add((AbstractInsnNode)allowed);
        prefix.add((AbstractInsnNode)new FrameNode(-1, 1, new Object[]{"java/util/Map"}, 0, new Object[0]));
        method.instructions.insert(prefix);
        ClassWriter writer = new ClassWriter(reader, 1);
        type.accept((ClassVisitor)writer);
        HookStatus.installed("AHZNB.kgEligibility");
        return writer.toByteArray();
    }

    public static String fingerprint(byte[] original) {
        ClassNode type = new ClassNode(327680);
        new ClassReader(original).accept((ClassVisitor)type, 8);
        MethodNode method = NarutoGameplayTransformer.target(type);
        return NarutoGameplayTransformer.shape(method, NarutoGameplayTransformer.meaningful(method.instructions.getFirst()));
    }

    private static MethodNode target(ClassNode type) {
        MethodNode found = null;
        for (MethodNode method : type.methods) {
            if (!method.name.equals("executeProcedure") || !method.desc.equals(DESC)) continue;
            if (found != null) {
                throw NarutoGameplayTransformer.unsupported("Duplicate KG method");
            }
            found = method;
        }
        if (found == null || (found.access & 0x509) != 9) {
            throw NarutoGameplayTransformer.unsupported("Unexpected KG method signature");
        }
        return found;
    }

    private static boolean gate(AbstractInsnNode node) {
        if (!(node instanceof MethodInsnNode)) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode)node;
        return call.getOpcode() == 184 && call.owner.equals(HOOK) && call.name.equals("allowKgDistribution") && call.desc.equals(GATE_DESC) && !call.itf;
    }

    private static AbstractInsnNode meaningful(AbstractInsnNode node) {
        while (node != null && node.getOpcode() < 0) {
            node = node.getNext();
        }
        return node;
    }

    private static AbstractInsnNode next(AbstractInsnNode node) {
        return node == null ? null : NarutoGameplayTransformer.meaningful(node.getNext());
    }

    /**
     * Minecraft member names are obfuscated (SRG) on a real server and readable (MCP) in a
     * development runtime; owner and descriptor still pin the member, so the name is left out.
     */
    private static String name(String owner, String name) {
        return owner.startsWith("net/minecraft/") || name.startsWith("func_") || name.startsWith("field_") ? "~" : name;
    }

    private static String shape(MethodNode method, AbstractInsnNode start) {
        if (!method.tryCatchBlocks.isEmpty()) {
            throw NarutoGameplayTransformer.unsupported("Unexpected KG exception handlers");
        }
        IdentityHashMap<AbstractInsnNode, Integer> indices = new IdentityHashMap<AbstractInsnNode, Integer>();
        int index = 0;
        AbstractInsnNode node = start;
        while (node != null) {
            indices.put(node, index++);
            node = NarutoGameplayTransformer.next(node);
        }
        StringBuilder value = new StringBuilder();
        AbstractInsnNode node2 = start;
        while (node2 != null) {
            TableSwitchInsnNode table;
            value.append(node2.getOpcode());
            if (node2 instanceof VarInsnNode) {
                value.append(':').append(((VarInsnNode)node2).var);
            } else if (node2 instanceof IntInsnNode) {
                value.append(':').append(((IntInsnNode)node2).operand);
            } else if (node2 instanceof IincInsnNode) {
                value.append(':').append(((IincInsnNode)node2).var).append(':').append(((IincInsnNode)node2).incr);
            } else if (node2 instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)node2;
                value.append(':').append(call.owner).append(':').append(NarutoGameplayTransformer.name(call.owner, call.name)).append(':').append(call.desc).append(':').append(call.itf);
            } else if (node2 instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)node2;
                value.append(':').append(field.owner).append(':').append(NarutoGameplayTransformer.name(field.owner, field.name)).append(':').append(field.desc);
            } else if (node2 instanceof TypeInsnNode) {
                value.append(':').append(((TypeInsnNode)node2).desc);
            } else if (node2 instanceof JumpInsnNode) {
                value.append(':').append(NarutoGameplayTransformer.destination(indices, ((JumpInsnNode)node2).label));
            } else if (node2 instanceof LdcInsnNode) {
                Object constant = ((LdcInsnNode)node2).cst;
                value.append(':').append(constant.getClass().getName()).append(':').append(constant instanceof Type ? ((Type)constant).getDescriptor() : String.valueOf(constant));
            } else if (node2 instanceof MultiANewArrayInsnNode) {
                MultiANewArrayInsnNode multi = (MultiANewArrayInsnNode)node2;
                value.append(':').append(multi.desc).append(':').append(multi.dims);
            } else if (node2 instanceof TableSwitchInsnNode) {
                table = (TableSwitchInsnNode)node2;
                value.append(':').append(table.min).append(':').append(table.max).append(':').append(NarutoGameplayTransformer.destination(indices, table.dflt));
                for (LabelNode label : table.labels) {
                    value.append(':').append(NarutoGameplayTransformer.destination(indices, label));
                }
            } else if (node2 instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode lookup = (LookupSwitchInsnNode)node2;
                value.append(':').append(NarutoGameplayTransformer.destination(indices, lookup.dflt));
                for (int i = 0; i < lookup.keys.size(); ++i) {
                    value.append(':').append(lookup.keys.get(i)).append(':').append(NarutoGameplayTransformer.destination(indices, lookup.labels.get(i)));
                }
            } else if (!(node2 instanceof InsnNode)) {
                throw NarutoGameplayTransformer.unsupported("Unrecognized KG instruction type " + node2.getType());
            }
            value.append('\n');
            node2 = NarutoGameplayTransformer.next(node2);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(Character.forDigit(b >>> 4 & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static int destination(Map<AbstractInsnNode, Integer> indices, LabelNode label) {
        Integer index = indices.get(NarutoGameplayTransformer.meaningful((AbstractInsnNode)label));
        if (index == null) {
            throw NarutoGameplayTransformer.unsupported("KG branch leaves verified body");
        }
        return index;
    }

    private static IllegalStateException unsupported(String detail) {
        return new IllegalStateException("Dungeon Guard cannot safely apply Naruto KG eligibility: " + detail + ". No partial gameplay patch is accepted.");
    }
}


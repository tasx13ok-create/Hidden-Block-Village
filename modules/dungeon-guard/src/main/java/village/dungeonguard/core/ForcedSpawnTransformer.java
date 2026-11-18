package village.dungeonguard.core;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import village.dungeonguard.core.HookStatus;

public final class ForcedSpawnTransformer
implements Opcodes {
    private static final String POLICY = "village/dungeonguard/ForcedSpawnPolicy";
    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String MP = "net/minecraft/entity/player/EntityPlayerMP";
    private static final String LIST = "net/minecraft/server/management/PlayerList";
    private static final String INFO = "net/minecraft/world/storage/WorldInfo";
    private static final String POS = "Lnet/minecraft/util/math/BlockPos;";
    private static final String NBT = "Lnet/minecraft/nbt/NBTTagCompound;";
    private static final String MAGMA_LOCATION = "Lorg/bukkit/Location;";
    private static final String OBJECT = "java/lang/Object";

    private ForcedSpawnTransformer() {
    }

    public static byte[] apply(String transformedName, byte[] original) {
        if (transformedName == null || original == null) {
            return original;
        }
        String owner = transformedName.replace('.', '/');
        if (!(owner.equals(PLAYER) || owner.equals(LIST) || owner.equals(INFO))) {
            return original;
        }
        ClassReader reader = new ClassReader(original);
        ClassNode type = new ClassNode(327680);
        reader.accept((ClassVisitor)type, 8);
        if (!owner.equals(type.name)) {
            throw ForcedSpawnTransformer.unsupported(owner, "unexpected transformed class " + type.name);
        }
        boolean changed = false;
        if (owner.equals(PLAYER)) {
            changed |= ForcedSpawnTransformer.patch(type, ForcedSpawnTransformer.require(type, "setSpawnPoint", "func_180473_a", "(Lnet/minecraft/util/math/BlockPos;Z)V"), Kind.SET_PERSONAL);
            changed |= ForcedSpawnTransformer.patch(type, ForcedSpawnTransformer.require(type, "setSpawnChunk", null, "(Lnet/minecraft/util/math/BlockPos;ZI)V"), Kind.SET_PERSONAL);
            changed |= ForcedSpawnTransformer.patch(type, ForcedSpawnTransformer.require(type, "setSpawnDimension", null, "(Ljava/lang/Integer;)V"), Kind.SET_PERSONAL);
            changed |= ForcedSpawnTransformer.patch(type, ForcedSpawnTransformer.require(type, "getSpawnDimension", null, "()I"), Kind.PERSONAL_DIMENSION);
            changed |= ForcedSpawnTransformer.patch(type, ForcedSpawnTransformer.require(type, "getBedLocation", null, "(I)Lnet/minecraft/util/math/BlockPos;"), Kind.PERSONAL_LOCATION);
            changed |= ForcedSpawnTransformer.patch(type, ForcedSpawnTransformer.require(type, "isSpawnForced", null, "(I)Z"), Kind.PERSONAL_FORCED);
        } else if (owner.equals(LIST)) {
            changed |= ForcedSpawnTransformer.patch(type, ForcedSpawnTransformer.require(type, "readPlayerDataFromFile", "func_72380_a", "(Lnet/minecraft/entity/player/EntityPlayerMP;)Lnet/minecraft/nbt/NBTTagCompound;"), Kind.LOAD);
            changed |= ForcedSpawnTransformer.patch(type, ForcedSpawnTransformer.require(type, "initializeConnectionToPlayer", "func_72355_a", "(Lnet/minecraft/network/NetworkManager;Lnet/minecraft/entity/player/EntityPlayerMP;Lnet/minecraft/network/NetHandlerPlayServer;)V"), Kind.LOGIN);
            MethodNode recreation = ForcedSpawnTransformer.require(type, "recreatePlayerEntity", "func_72368_a", "(Lnet/minecraft/entity/player/EntityPlayerMP;IZ)Lnet/minecraft/entity/player/EntityPlayerMP;");
            changed |= magmaPreparedTravel(type) ? patchMagmaRespawn(type, recreation)
                    : ForcedSpawnTransformer.patch(type, recreation, Kind.RESPAWN);
        } else {
            changed |= ForcedSpawnTransformer.patch(type, ForcedSpawnTransformer.require(type, "setSpawn", "func_176143_a", "(Lnet/minecraft/util/math/BlockPos;)V"), Kind.WORLD_POS);
            changed |= ForcedSpawnTransformer.optional(type, "setSpawnX", "func_76058_a", Kind.WORLD_X);
            changed |= ForcedSpawnTransformer.optional(type, "setSpawnY", "func_76056_b", Kind.WORLD_Y);
            changed |= ForcedSpawnTransformer.optional(type, "setSpawnZ", "func_76087_c", Kind.WORLD_Z);
        }
        byte[] output = original;
        if (changed) {
            ClassWriter writer = new ClassWriter(reader, 1);
            type.accept((ClassVisitor)writer);
            output = writer.toByteArray();
        }
        if (owner.equals(PLAYER)) {
            HookStatus.installed("EntityPlayer.forceSpawn");
        } else if (owner.equals(INFO)) {
            HookStatus.installed("WorldInfo.forceSpawn");
        } else {
            HookStatus.installed("PlayerList.forceRespawn");
            HookStatus.installed("PlayerList.firstJoinSpawn");
        }
        return output;
    }

    private static boolean optional(ClassNode type, String mcp, String srg, Kind kind) {
        MethodNode method = ForcedSpawnTransformer.find(type, mcp, srg, "(I)V");
        return method != null && ForcedSpawnTransformer.patch(type, method, kind);
    }

    private static MethodNode require(ClassNode type, String mcp, String srg, String desc) {
        MethodNode method = ForcedSpawnTransformer.find(type, mcp, srg, desc);
        if (method == null) {
            throw ForcedSpawnTransformer.unsupported(type.name, "missing " + mcp + desc);
        }
        return method;
    }

    private static MethodNode find(ClassNode type, String mcp, String srg, String desc) {
        MethodNode found = null;
        for (Object entry : type.methods) {
            MethodNode method = (MethodNode) entry;
            if (!method.name.equals(mcp) && !method.name.equals(srg) || !method.desc.equals(desc)) continue;
            if (found != null) {
                throw ForcedSpawnTransformer.unsupported(type.name, "ambiguous " + mcp + desc);
            }
            if ((method.access & 0x508) != 0 || (method.access & 1) == 0) {
                throw ForcedSpawnTransformer.unsupported(type.name, "unsupported signature for " + mcp);
            }
            found = method;
        }
        return found;
    }

    /**
     * Magma uses the same recreation method for deaths and live portal/Bukkit
     * transfers. Its explicit prepared destination distinguishes the latter;
     * the conqueredEnd argument does not (and is also used for real End return).
     */
    private static boolean magmaPreparedTravel(ClassNode type) {
        FieldNode location = null;
        int locationNames = 0;
        boolean namedPrepare = false;
        for (Object entry : type.fields) {
            FieldNode field = (FieldNode) entry;
            if (!"location".equals(field.name)) continue;
            ++locationNames;
            if (MAGMA_LOCATION.equals(field.desc)) location = field;
        }
        for (Object entry : type.methods) {
            MethodNode method = (MethodNode) entry;
            if ("prepareRecreatePlayerEntity".equals(method.name)) namedPrepare = true;
        }
        // An unrelated field named location is not evidence of Magma.
        if (location == null && !namedPrepare) return false;
        if (location == null || locationNames != 1 || (location.access & (ACC_STATIC | ACC_FINAL)) != 0) {
            throw unsupported(type.name, "unsupported Magma prepared destination field");
        }
        MethodNode prepare = require(type, "prepareRecreatePlayerEntity", null, "(" + MAGMA_LOCATION + "Z)V");
        List<AbstractInsnNode> code = meaningful(prepare.instructions);
        if (code.size() < 3 || !variable(code.get(0), ALOAD, 0) || !variable(code.get(1), ALOAD, 1)
                || !locationField(code.get(2), PUTFIELD, type.name)
                || locationFields(code, GETFIELD, type.name) != 0
                || locationFields(code, PUTFIELD, type.name) != 1) {
            throw unsupported(type.name, "unsupported Magma prepared destination setter");
        }
        return true;
    }

    private static boolean patchMagmaRespawn(ClassNode type, MethodNode method) {
        List<AbstractInsnNode> real = meaningful(method.instructions);
        int returns = 0;
        for (AbstractInsnNode instruction : real) if (instruction.getOpcode() == ARETURN) ++returns;
        if (returns == 0) throw unsupported(type.name, "no completed return in " + method.name);
        if (hooks(real) != 0) {
            verifyMagmaRespawn(type, method, real, returns);
            return false;
        }

        int captured = method.maxLocals;
        requireMagmaReadAndClear(type.name, real, 0, captured);
        if (captured < 4 || captured >= 65535
                || locationFields(real, GETFIELD, type.name) != 1
                || locationFields(real, PUTFIELD, type.name) != 1) {
            throw unsupported(type.name, "unsupported Magma recreation destination lifetime");
        }

        // This local belongs to one invocation and survives Magma clearing its
        // shared field, nested transfers and exception handlers. No global or
        // ThreadLocal bypass can leak into a later respawn.
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof FrameNode) {
                FrameNode frame = (FrameNode) instruction;
                if (frame.type != F_NEW) throw unsupported(type.name, "Magma recreation frames were not expanded");
                frame.local = withCapturedLocal(type.name, frame.local, captured);
            }
        }
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() == ARETURN) method.instructions.insertBefore(instruction, magmaSuffix(captured));
        }
        method.instructions.insert(magmaPrefix(type.name, method, captured));
        method.maxLocals = captured + 1;
        return true;
    }

    private static void requireMagmaReadAndClear(String owner, List<AbstractInsnNode> code, int start, int captured) {
        if (start + 6 > code.size() || !variable(code.get(start), ALOAD, 0)
                || !locationField(code.get(start + 1), GETFIELD, owner)
                || !(code.get(start + 2) instanceof VarInsnNode)
                || code.get(start + 2).getOpcode() != ASTORE
                || ((VarInsnNode) code.get(start + 2)).var < 4
                || ((VarInsnNode) code.get(start + 2)).var >= captured
                || !variable(code.get(start + 3), ALOAD, 0)
                || code.get(start + 4).getOpcode() != ACONST_NULL
                || !locationField(code.get(start + 5), PUTFIELD, owner)) {
            throw unsupported(owner, "unsupported Magma recreation destination read/clear entry");
        }
    }

    private static InsnList magmaPrefix(String owner, MethodNode method, int captured) {
        InsnList code = new InsnList();
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, owner, "location", MAGMA_LOCATION));
        code.add(new VarInsnNode(ASTORE, captured));
        code.add(new VarInsnNode(ALOAD, captured));
        LabelNode original = new LabelNode();
        code.add(new JumpInsnNode(IFNONNULL, original));
        code.add(prefix(owner, method, Kind.RESPAWN));
        code.add(original);
        List<Object> initial = new ArrayList<Object>();
        for (Object local : initialLocals(owner, method)) initial.add(local);
        List<Object> locals = withCapturedLocal(owner, initial, captured);
        code.add(new FrameNode(F_NEW, locals.size(), locals.toArray(), 0, new Object[0]));
        return code;
    }

    private static InsnList magmaSuffix(int captured) {
        InsnList code = new InsnList();
        code.add(new VarInsnNode(ALOAD, captured));
        LabelNode complete = new LabelNode();
        code.add(new JumpInsnNode(IFNONNULL, complete));
        code.add(suffix(Kind.RESPAWN));
        code.add(complete);
        // The only following instruction is ARETURN. Forgetting other locals
        // is valid at this merge and avoids guessing the body's live locals.
        Object[] locals = new Object[captured + 1];
        for (int i = 0; i < captured; ++i) locals[i] = TOP;
        locals[captured] = OBJECT;
        code.add(new FrameNode(F_NEW, locals.length, locals, 1, new Object[] {MP}));
        return code;
    }

    private static List<Object> withCapturedLocal(String owner, List<Object> original, int captured) {
        List<Object> locals = original == null ? new ArrayList<Object>() : new ArrayList<Object>(original);
        int slots = localSlots(locals);
        if (slots > captured) throw unsupported(owner, "Magma captured destination overlaps original locals");
        while (slots++ < captured) locals.add(TOP);
        locals.add(OBJECT);
        return locals;
    }

    private static int localSlots(List<Object> locals) {
        int slots = 0;
        if (locals != null) for (Object local : locals) slots += LONG.equals(local) || DOUBLE.equals(local) ? 2 : 1;
        return slots;
    }

    /** Refuse partial, older unconditional, or externally modified hooks. */
    private static void verifyMagmaRespawn(ClassNode type, MethodNode method, List<AbstractInsnNode> real, int returns) {
        if (real.size() < 3 || !(real.get(2) instanceof VarInsnNode) || real.get(2).getOpcode() != ASTORE) {
            throw unsupported(type.name, "modified Magma spawn entry hook");
        }
        int captured = ((VarInsnNode) real.get(2)).var;
        if (captured < 4 || captured != method.maxLocals - 1) {
            throw unsupported(type.name, "modified Magma captured destination local");
        }
        InsnList entry = magmaPrefix(type.name, method, captured);
        List<AbstractInsnNode> prefix = meaningful(entry);
        List<AbstractInsnNode> suffix = meaningful(magmaSuffix(captured));
        if (!matches(real, 0, prefix) || real.size() <= prefix.size()
                || nextReal(((JumpInsnNode) real.get(4)).label) != real.get(prefix.size())) {
            throw unsupported(type.name, "modified Magma spawn entry guard");
        }
        requireMagmaReadAndClear(type.name, real, prefix.size(), captured);
        verifyMergeFrame(type.name, ((JumpInsnNode) real.get(4)).label, frameAfter(entry), "entry");
        if (hooks(real) != 1 + returns || locationFields(real, GETFIELD, type.name) != 2
                || locationFields(real, PUTFIELD, type.name) != 1) {
            throw unsupported(type.name, "unexpected Magma spawn hooks or destination accesses");
        }

        List<AbstractInsnNode> permittedLocalUses = new ArrayList<AbstractInsnNode>();
        permittedLocalUses.add(real.get(2));
        permittedLocalUses.add(real.get(3));
        for (int i = 0; i < real.size(); ++i) {
            if (real.get(i).getOpcode() != ARETURN) continue;
            if (!matches(real, i - suffix.size(), suffix)) {
                throw unsupported(type.name, "missing guarded final Magma spawn hook");
            }
            JumpInsnNode skip = (JumpInsnNode) real.get(i - suffix.size() + 1);
            if (nextReal(skip.label) != real.get(i)) throw unsupported(type.name, "modified Magma spawn completion branch");
            verifyMergeFrame(type.name, skip.label, frameAfter(magmaSuffix(captured)), "completion");
            permittedLocalUses.add(real.get(i - suffix.size()));
        }
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof VarInsnNode) {
                VarInsnNode variable = (VarInsnNode) instruction;
                boolean wide = variable.getOpcode() == LLOAD || variable.getOpcode() == DLOAD
                        || variable.getOpcode() == LSTORE || variable.getOpcode() == DSTORE;
                if ((variable.var == captured || wide && variable.var + 1 == captured)
                        && !permittedLocalUses.contains(instruction)) {
                    throw unsupported(type.name, "modified Magma captured destination lifetime");
                }
            } else if (instruction instanceof IincInsnNode && ((IincInsnNode) instruction).var == captured) {
                throw unsupported(type.name, "modified Magma captured destination lifetime");
            } else if (instruction instanceof FrameNode) {
                FrameNode frame = (FrameNode) instruction;
                if (frame.type != F_NEW || frame.local == null || localSlots(frame.local) != captured + 1
                        || !OBJECT.equals(frame.local.get(frame.local.size() - 1))) {
                    throw unsupported(type.name, "modified Magma captured destination frame");
                }
            }
        }
    }

    private static FrameNode frameAfter(InsnList code) {
        for (AbstractInsnNode instruction : code.toArray()) if (instruction instanceof FrameNode) return (FrameNode) instruction;
        throw new IllegalStateException("Missing generated Magma spawn frame");
    }

    private static void verifyMergeFrame(String owner, LabelNode label, FrameNode expected, String phase) {
        FrameNode found = null;
        for (AbstractInsnNode instruction = label; instruction != null && instruction.getOpcode() < 0; instruction = instruction.getNext()) {
            if (!(instruction instanceof FrameNode)) continue;
            if (found != null) throw unsupported(owner, "duplicate Magma " + phase + " frame");
            found = (FrameNode) instruction;
        }
        if (found == null || found.type != F_NEW || !sameFrameValues(found.local, expected.local)
                || !sameFrameValues(found.stack, expected.stack)) {
            throw unsupported(owner, "modified Magma " + phase + " frame");
        }
    }

    private static boolean sameFrameValues(List<Object> a, List<Object> b) {
        return a == null || a.isEmpty() ? b == null || b.isEmpty() : a.equals(b);
    }

    private static boolean variable(AbstractInsnNode instruction, int opcode, int slot) {
        return instruction instanceof VarInsnNode && instruction.getOpcode() == opcode && ((VarInsnNode) instruction).var == slot;
    }

    private static boolean locationField(AbstractInsnNode instruction, int opcode, String owner) {
        if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != opcode) return false;
        FieldInsnNode field = (FieldInsnNode) instruction;
        return owner.equals(field.owner) && "location".equals(field.name) && MAGMA_LOCATION.equals(field.desc);
    }

    private static int locationFields(List<AbstractInsnNode> code, int opcode, String owner) {
        int count = 0;
        for (AbstractInsnNode instruction : code) if (locationField(instruction, opcode, owner)) ++count;
        return count;
    }

    /*
     * WARNING - void declaration
     */
    private static boolean patch(ClassNode type, MethodNode method, Kind kind) {
        List<AbstractInsnNode> real = ForcedSpawnTransformer.meaningful(method.instructions);
        List<AbstractInsnNode> prefix = ForcedSpawnTransformer.meaningful(ForcedSpawnTransformer.prefix(type.name, method, kind));
        List<AbstractInsnNode> suffix = ForcedSpawnTransformer.meaningful(ForcedSpawnTransformer.suffix(kind));
        int returns = 0;
        int returnOpcode = Type.getReturnType((String)method.desc).getOpcode(172);
        for (AbstractInsnNode abstractInsnNode : real) {
            if (abstractInsnNode.getOpcode() != returnOpcode) continue;
            ++returns;
        }
        if (!suffix.isEmpty() && returns == 0) {
            throw ForcedSpawnTransformer.unsupported(type.name, "no completed return in " + method.name);
        }
        int foundHooks = 0;
        for (AbstractInsnNode instruction : real) {
            if (!(instruction instanceof MethodInsnNode) || !((MethodInsnNode)instruction).owner.equals(POLICY)) continue;
            ++foundHooks;
        }
        if (foundHooks != 0) {
            int n;
            if (real.size() < prefix.size() || !ForcedSpawnTransformer.matches(real, 0, prefix)) {
                throw ForcedSpawnTransformer.unsupported(type.name, "modified spawn entry hook in " + method.name);
            }
            if (kind == Kind.SET_PERSONAL) {
                JumpInsnNode jumpInsnNode = (JumpInsnNode)real.get(2);
                if (real.size() <= prefix.size() || ForcedSpawnTransformer.nextReal((AbstractInsnNode)jumpInsnNode.label) != real.get(prefix.size())) {
                    throw ForcedSpawnTransformer.unsupported(type.name, "modified personal-spawn branch in " + method.name);
                }
            }
            if (!suffix.isEmpty()) {
                for (int i = 0; i < real.size(); ++i) {
                    if (real.get(i).getOpcode() == returnOpcode && !(i >= suffix.size() && ForcedSpawnTransformer.matches(real, i - suffix.size(), suffix))) {
                        throw ForcedSpawnTransformer.unsupported(type.name, "missing final spawn hook in " + method.name);
                    }
                }
            }
            if (foundHooks != (n = ForcedSpawnTransformer.hooks(prefix) + ForcedSpawnTransformer.hooks(suffix) * returns)) {
                throw ForcedSpawnTransformer.unsupported(type.name, "unexpected spawn hooks in " + method.name);
            }
            return false;
        }
        if (!suffix.isEmpty()) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != returnOpcode) continue;
                method.instructions.insertBefore(instruction, ForcedSpawnTransformer.suffix(kind));
            }
        }
        method.instructions.insert(ForcedSpawnTransformer.prefix(type.name, method, kind));
        return true;
    }

    private static InsnList prefix(String owner, MethodNode method, Kind kind) {
        InsnList code = new InsnList();
        if (kind == Kind.SET_PERSONAL) {
            code.add((AbstractInsnNode)new VarInsnNode(25, 0));
            ForcedSpawnTransformer.call(code, "assignPersonalSpawn", "(Lnet/minecraft/entity/player/EntityPlayer;)Z");
            LabelNode original = new LabelNode();
            code.add((AbstractInsnNode)new JumpInsnNode(153, original));
            code.add((AbstractInsnNode)new InsnNode(177));
            code.add((AbstractInsnNode)original);
            Object[] locals = ForcedSpawnTransformer.initialLocals(owner, method);
            code.add((AbstractInsnNode)new FrameNode(-1, locals.length, locals, 0, new Object[0]));
        } else if (kind == Kind.RESPAWN) {
            code.add((AbstractInsnNode)new VarInsnNode(25, 1));
            code.add((AbstractInsnNode)new VarInsnNode(21, 2));
            ForcedSpawnTransformer.call(code, "respawnDimension", "(Lnet/minecraft/entity/player/EntityPlayerMP;I)I");
            code.add((AbstractInsnNode)new VarInsnNode(54, 2));
        } else if (kind == Kind.WORLD_POS) {
            code.add((AbstractInsnNode)new VarInsnNode(25, 0));
            code.add((AbstractInsnNode)new VarInsnNode(25, 1));
            ForcedSpawnTransformer.call(code, "worldSpawn", "(Lnet/minecraft/world/storage/WorldInfo;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/BlockPos;");
            code.add((AbstractInsnNode)new VarInsnNode(58, 1));
        } else if (kind == Kind.WORLD_X || kind == Kind.WORLD_Y || kind == Kind.WORLD_Z) {
            code.add((AbstractInsnNode)new VarInsnNode(25, 0));
            code.add((AbstractInsnNode)new VarInsnNode(21, 1));
            code.add((AbstractInsnNode)new InsnNode(kind == Kind.WORLD_X ? 3 : (kind == Kind.WORLD_Y ? 4 : 5)));
            ForcedSpawnTransformer.call(code, "worldCoordinate", "(Lnet/minecraft/world/storage/WorldInfo;II)I");
            code.add((AbstractInsnNode)new VarInsnNode(54, 1));
        }
        return code;
    }

    private static InsnList suffix(Kind kind) {
        InsnList code = new InsnList();
        switch (kind) {
            case PERSONAL_DIMENSION: {
                code.add((AbstractInsnNode)new VarInsnNode(25, 0));
                ForcedSpawnTransformer.call(code, "personalDimension", "(ILnet/minecraft/entity/player/EntityPlayer;)I");
                break;
            }
            case PERSONAL_LOCATION: {
                code.add((AbstractInsnNode)new VarInsnNode(25, 0));
                code.add((AbstractInsnNode)new VarInsnNode(21, 1));
                ForcedSpawnTransformer.call(code, "personalLocation", "(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/EntityPlayer;I)Lnet/minecraft/util/math/BlockPos;");
                break;
            }
            case PERSONAL_FORCED: {
                code.add((AbstractInsnNode)new VarInsnNode(25, 0));
                code.add((AbstractInsnNode)new VarInsnNode(21, 1));
                ForcedSpawnTransformer.call(code, "personalForced", "(ZLnet/minecraft/entity/player/EntityPlayer;I)Z");
                break;
            }
            case LOAD: {
                code.add((AbstractInsnNode)new VarInsnNode(25, 1));
                ForcedSpawnTransformer.call(code, "playerDataLoaded", "(Lnet/minecraft/nbt/NBTTagCompound;Lnet/minecraft/entity/player/EntityPlayerMP;)Lnet/minecraft/nbt/NBTTagCompound;");
                break;
            }
            case LOGIN: {
                code.add((AbstractInsnNode)new VarInsnNode(25, 2));
                ForcedSpawnTransformer.call(code, "loginFinished", "(Lnet/minecraft/entity/player/EntityPlayerMP;)V");
                break;
            }
            case RESPAWN: {
                code.add((AbstractInsnNode)new VarInsnNode(25, 1));
                ForcedSpawnTransformer.call(code, "respawnFinished", "(Lnet/minecraft/entity/player/EntityPlayerMP;Lnet/minecraft/entity/player/EntityPlayerMP;)Lnet/minecraft/entity/player/EntityPlayerMP;");
                break;
            }
        }
        return code;
    }

    private static void call(InsnList code, String method, String desc) {
        code.add((AbstractInsnNode)new MethodInsnNode(184, POLICY, method, desc, false));
    }

    private static int hooks(List<AbstractInsnNode> code) {
        int count = 0;
        for (AbstractInsnNode instruction : code) {
            if (!(instruction instanceof MethodInsnNode) || !((MethodInsnNode)instruction).owner.equals(POLICY)) continue;
            ++count;
        }
        return count;
    }

    private static boolean matches(List<AbstractInsnNode> real, int from, List<AbstractInsnNode> expected) {
        if (from < 0 || from + expected.size() > real.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); ++i) {
            AbstractInsnNode a = real.get(from + i);
            AbstractInsnNode b = expected.get(i);
            if (a.getOpcode() != b.getOpcode() || a.getType() != b.getType()) {
                return false;
            }
            if (a instanceof VarInsnNode && ((VarInsnNode)a).var != ((VarInsnNode)b).var) {
                return false;
            }
            if (a instanceof FieldInsnNode) {
                FieldInsnNode x = (FieldInsnNode) a;
                FieldInsnNode y = (FieldInsnNode) b;
                if (!x.owner.equals(y.owner) || !x.name.equals(y.name) || !x.desc.equals(y.desc)) return false;
            }
            if (!(a instanceof MethodInsnNode)) continue;
            MethodInsnNode x = (MethodInsnNode)a;
            MethodInsnNode y = (MethodInsnNode)b;
            if (x.owner.equals(y.owner) && x.name.equals(y.name) && x.desc.equals(y.desc) && x.itf == y.itf) continue;
            return false;
        }
        return true;
    }

    private static List<AbstractInsnNode> meaningful(InsnList instructions) {
        ArrayList<AbstractInsnNode> result = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction : instructions.toArray()) {
            if (instruction.getOpcode() < 0) continue;
            result.add(instruction);
        }
        return result;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) {
            instruction = instruction.getNext();
        }
        return instruction;
    }

    private static Object[] initialLocals(String owner, MethodNode method) {
        ArrayList<Object> locals = new ArrayList<Object>();
        locals.add(owner);
        block7: for (Type arg : Type.getArgumentTypes((String)method.desc)) {
            switch (arg.getSort()) {
                case 10: {
                    locals.add(arg.getInternalName());
                    continue block7;
                }
                case 9: {
                    locals.add(arg.getDescriptor());
                    continue block7;
                }
                case 6: {
                    locals.add(FLOAT);
                    continue block7;
                }
                case 7: {
                    locals.add(LONG);
                    continue block7;
                }
                case 8: {
                    locals.add(DOUBLE);
                    continue block7;
                }
                default: {
                    locals.add(INTEGER);
                }
            }
        }
        return locals.toArray();
    }

    private static IllegalStateException unsupported(String owner, String detail) {
        return new IllegalStateException("Dungeon Guard cannot install its mandatory central-spawn hook in " + owner + ": " + detail);
    }

    private static enum Kind {
        SET_PERSONAL,
        PERSONAL_DIMENSION,
        PERSONAL_LOCATION,
        PERSONAL_FORCED,
        LOAD,
        LOGIN,
        RESPAWN,
        WORLD_POS,
        WORLD_X,
        WORLD_Y,
        WORLD_Z;

    }
}


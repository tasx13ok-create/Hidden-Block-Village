package village.dungeonguard.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Exact 1.12.2 entry gates, plus narrowly verified AHZNB compatibility paths. */
public final class GuardTransformer implements IClassTransformer {
    private static final String HOOK = "village/dungeonguard/GuardHooks";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String PISTON = "net/minecraft/block/BlockPistonBase";
    private static final String INTERACTION = "net/minecraft/server/management/PlayerInteractionManager";
    private static final String COMMAND_HANDLER = "net/minecraft/command/CommandHandler";
    private static final String COMMAND_BASE = "net/minecraft/command/CommandBase";
    private static final String MAGMA_COMMAND_WRAPPER = "org/bukkit/craftbukkit/v1_12_R1/command/VanillaCommandWrapper";
    private static final String OPERATOR = "village/dungeonguard/OperatorAccess";
    private static final String SCOPE = OPERATOR + "$Scope";
    private static final String PLAYER_REF = "Lnet/minecraft/entity/player/EntityPlayer;";
    private static final String HAND_REF = "Lnet/minecraft/util/EnumHand;";
    private static final String ACTION_REF = "Lnet/minecraft/util/EnumActionResult;";
    private static final String COMMAND = "net/minecraft/command/ICommand";
    private static final String COMMAND_DESC = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V";
    private static final String RUN_COMMAND_DESC = "(L" + COMMAND + ";" + COMMAND_DESC.substring(1);
    private static final String POS = "Lnet/minecraft/util/math/BlockPos;";
    private static final String STATE = "Lnet/minecraft/block/state/IBlockState;";
    private static final String ENTITY = "Lnet/minecraft/entity/Entity;";
    private static final String FACING = "Lnet/minecraft/util/EnumFacing;";
    private static final String ITEM = "Lnet/minecraft/item/ItemStack;";
    private static final String WORLD_REF = "L" + WORLD + ";";
    private static final String CHUNK_REF = "L" + CHUNK + ";";
    private static final String UTILS = "net/narutomod/procedure/ProcedureUtils";
    private static final String PORTAL = "net/narutomod/procedure/ProcedureYomotsuHirasaka";
    private static final String KAMUI = "net/narutomod/procedure/ProcedureKamuiTeleportEntity";
    // Digest of the observed 0.3.1-beta instruction structure, never upstream code.
    private static final String KAMUI_BLOCK_SHAPE = "63ff5dc1d296ce127326698eb3a6ce42049ea9191d34792c6e5233f2106f66b4";
    private static final String SET_DESC = "(" + POS + STATE + "I)Z";

    private static final class Gate {
        final String mcp, srg, desc, hook, hookDesc, status;
        final int[] locals;
        final int result;
        Gate(String mcp, String srg, String desc, String hook, String hookDesc, int result, int... locals) {
            this.mcp = mcp; this.srg = srg; this.desc = desc; this.hook = hook;
            this.hookDesc = hookDesc; this.result = result; this.locals = locals;
            this.status = mcp;
        }
    }

    private interface Patch { byte[] apply(String transformedName, byte[] original); }

    private static byte[] optional(String hook, String transformedName, byte[] original, Patch patch) {
        try {
            return patch.apply(transformedName, original);
        } catch (RuntimeException unsupported) {
            HookStatus.failed(hook, unsupported.getMessage());
            org.apache.logging.log4j.LogManager.getLogger("DungeonGuard").error("Optional hook " + hook + " was not installed; "
                    + transformedName + " is left unchanged and the features that need it stay off: " + unsupported.getMessage());
            return original;
        }
    }

    @Override public byte[] transform(String name, String transformedName, byte[] original) {
        if (original == null || transformedName == null) return original;
        // Optional compatibility hooks: if the other mod changed, leave it untouched and say so.
        // Throwing here would stop that mod's class from loading at all (NarutoMod then fails to
        // register its items). Features that need a hook check HookStatus and refuse cleanly.
        original = optional("AHZNB.kgEligibility", transformedName, original, NarutoGameplayTransformer::apply);
        original = optional("Waystones.teleportToWaystone", transformedName, original, WaystonesTransformer::apply);
        original = ForcedSpawnTransformer.apply(transformedName, original);
        String owner = transformedName.replace('.', '/');
        if (!owner.equals(WORLD) && !owner.equals(CHUNK) && !owner.equals(BLOCK)
                && !owner.equals(PISTON) && !owner.equals(UTILS) && !owner.equals(PORTAL) && !owner.equals(KAMUI)
                && !owner.equals(INTERACTION) && !owner.equals(COMMAND_HANDLER) && !owner.equals(MAGMA_COMMAND_WRAPPER)) return original;
        ClassReader reader = new ClassReader(original);
        ClassNode type = new ClassNode(Opcodes.ASM5);
        reader.accept(type, ClassReader.EXPAND_FRAMES);
        if (!owner.equals(type.name)) throw unsupported(owner, "unexpected post-deobfuscation owner " + type.name);
        List<String> installed = new ArrayList<String>();
        boolean changed = false;
        if (owner.equals(INTERACTION)) {
            changed = playerActions(type, installed);
        } else if (owner.equals(COMMAND_HANDLER)) {
            changed = commandExecution(type);
            installed.add("CommandHandler.operatorEdits");
        } else if (owner.equals(MAGMA_COMMAND_WRAPPER)) {
            changed = magmaCommands(type);
            installed.add("Magma.vanillaCommands");
        } else if (owner.equals(PORTAL)) {
            changed = portal(type);
            installed.add("AHZNB.portalPreflight");
        } else if (owner.equals(UTILS)) {
            changed = ahznbBreak(type);
            installed.add("AHZNB.breakBlockAndDropWithChance");
        } else if (owner.equals(KAMUI)) {
            changed = kamuiBlock(type);
            installed.add("AHZNB.kamuiBlock");
        } else {
            for (Gate gate : gates(owner)) {
                MethodNode method = unique(type, gate.mcp, gate.srg, gate.desc);
                boolean expectedStatic = owner.equals(BLOCK) && gate.mcp.equals("spawnAsEntity");
                if (((method.access & Opcodes.ACC_STATIC) != 0) != expectedStatic)
                    throw unsupported(owner, "unexpected static/instance signature for " + gate.mcp);
                changed |= entry(type.name, method, gate);
                installed.add(owner.substring(owner.lastIndexOf('/') + 1) + "." + gate.status);
            }
        }
        byte[] output = original;
        if (changed) {
            // All original frames are preserved as expanded frames. Added branches
            // supply explicit frames; no class hierarchy is loaded by COMPUTE_FRAMES.
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            type.accept(writer);
            output = writer.toByteArray();
        }
        for (String key : installed) HookStatus.installed(key);
        return output;
    }

    private static boolean playerActions(ClassNode type, List<String> installed) {
        boolean changed = false;
        changed |= actionWrapper(type, "tryHarvestBlock", "func_180237_b", "(" + POS + ")Z",
                "beginHarvest", "(L" + INTERACTION + ";" + POS + ")L" + SCOPE + ";", 0, 1);
        installed.add("PlayerInteractionManager.operatorHarvest");
        changed |= actionWrapper(type, "processRightClickBlock", "func_187251_a",
                "(" + PLAYER_REF + WORLD_REF + ITEM + HAND_REF + POS + FACING + "FFF)" + ACTION_REF,
                "beginUseOn", "(L" + INTERACTION + ";" + PLAYER_REF + WORLD_REF + ITEM + HAND_REF + POS + FACING + ")L" + SCOPE + ";",
                0, 1, 2, 3, 4, 5, 6);
        installed.add("PlayerInteractionManager.operatorUseOn");
        changed |= actionWrapper(type, "processRightClick", "func_187250_a",
                "(" + PLAYER_REF + WORLD_REF + ITEM + HAND_REF + ")" + ACTION_REF,
                "beginUse", "(L" + INTERACTION + ";" + PLAYER_REF + WORLD_REF + ITEM + HAND_REF + ")L" + SCOPE + ";",
                0, 1, 2, 3, 4);
        installed.add("PlayerInteractionManager.operatorUse");
        return changed;
    }

    /**
     * A small generated wrapper retains vanilla/Forge/Magma behavior and gives
     * all returns and exceptions one finally path. Existing implementation
     * frames remain untouched in a private synthetic helper.
     */
    private static boolean actionWrapper(ClassNode type, String mcp, String srg, String desc,
            String begin, String beginDesc, int... scopeLocals) {
        MethodNode original = unique(type, mcp, srg, desc);
        if ((original.access & Opcodes.ACC_STATIC) != 0) throw unsupported(type.name, "static player action " + mcp);
        String helper = "dungeonguard$original$" + mcp;
        MethodNode existingHelper = null;
        for (MethodNode method : type.methods) if (method.name.equals(helper)) {
            if (existingHelper != null) throw unsupported(type.name, "ambiguous operator action helper " + helper);
            existingHelper = method;
        }
        int beginCount = countCall(original, OPERATOR, begin, beginDesc, Opcodes.INVOKESTATIC);
        if (beginCount != 0 || existingHelper != null) {
            if (beginCount != 1 || existingHelper == null || !existingHelper.desc.equals(desc)
                    || (existingHelper.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC))
                        != (Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC)
                    || countCall(original, type.name, helper, desc, Opcodes.INVOKESPECIAL) != 1
                    || countCall(original, SCOPE, "close", "()V", Opcodes.INVOKEVIRTUAL) != 2
                    || original.tryCatchBlocks.size() != 1 || original.tryCatchBlocks.get(0).type != null)
                throw unsupported(type.name, "unrecognized operator wrapper for " + mcp);
            return false;
        }

        MethodNode wrapper = new MethodNode(Opcodes.ASM5, original.access, original.name, original.desc,
                original.signature, original.exceptions.toArray(new String[original.exceptions.size()]));
        wrapper.visibleAnnotations = original.visibleAnnotations;
        wrapper.invisibleAnnotations = original.invisibleAnnotations;
        wrapper.visibleParameterAnnotations = original.visibleParameterAnnotations;
        wrapper.invisibleParameterAnnotations = original.invisibleParameterAnnotations;
        wrapper.visibleTypeAnnotations = original.visibleTypeAnnotations;
        wrapper.invisibleTypeAnnotations = original.invisibleTypeAnnotations;
        wrapper.parameters = original.parameters;
        original.visibleAnnotations = null; original.invisibleAnnotations = null;
        original.visibleParameterAnnotations = null; original.invisibleParameterAnnotations = null;
        original.visibleTypeAnnotations = null; original.invisibleTypeAnnotations = null;
        original.name = helper;
        original.access = (original.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNCHRONIZED))
                | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC;

        Type[] args = Type.getArgumentTypes(desc);
        int scopeLocal = 1;
        for (Type arg : args) scopeLocal += arg.getSize();
        int resultLocal = scopeLocal + 1;
        Type result = Type.getReturnType(desc);
        int exceptionLocal = resultLocal + result.getSize();
        Type[] scopeArgs = Type.getArgumentTypes(beginDesc);
        if (scopeArgs.length != scopeLocals.length) throw new IllegalStateException("Invalid action-scope arguments");
        for (int i = 0; i < scopeLocals.length; i++)
            wrapper.instructions.add(new VarInsnNode(scopeArgs[i].getOpcode(Opcodes.ILOAD), scopeLocals[i]));
        wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OPERATOR, begin, beginDesc, false));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ASTORE, scopeLocal));
        LabelNode start = new LabelNode(), end = new LabelNode(), handler = new LabelNode();
        wrapper.instructions.add(start);
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        int local = 1;
        for (Type arg : args) {
            wrapper.instructions.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), local));
            local += arg.getSize();
        }
        wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, type.name, helper, desc, false));
        wrapper.instructions.add(new VarInsnNode(result.getOpcode(Opcodes.ISTORE), resultLocal));
        wrapper.instructions.add(end);
        closeScope(wrapper.instructions, scopeLocal);
        wrapper.instructions.add(new VarInsnNode(result.getOpcode(Opcodes.ILOAD), resultLocal));
        wrapper.instructions.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
        wrapper.instructions.add(handler);
        Object[] initial = initialLocals(type.name, wrapper);
        Object[] handlerLocals = Arrays.copyOf(initial, initial.length + 1);
        handlerLocals[initial.length] = SCOPE;
        wrapper.instructions.add(new FrameNode(Opcodes.F_NEW, handlerLocals.length, handlerLocals,
                1, new Object[] {"java/lang/Throwable"}));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ASTORE, exceptionLocal));
        closeScope(wrapper.instructions, scopeLocal);
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, exceptionLocal));
        wrapper.instructions.add(new InsnNode(Opcodes.ATHROW));
        wrapper.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
        wrapper.maxLocals = exceptionLocal + 1;
        type.methods.add(wrapper);
        return true;
    }

    private static void closeScope(InsnList list, int local) {
        list.add(new VarInsnNode(Opcodes.ALOAD, local));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SCOPE, "close", "()V", false));
    }

    private static boolean commandExecution(ClassNode type) {
        List<MethodInsnNode> calls = new ArrayList<MethodInsnNode>();
        int installed = 0;
        for (MethodNode method : type.methods) for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (!(node instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) node;
            if (call.getOpcode() == Opcodes.INVOKEINTERFACE && call.owner.equals(COMMAND) && call.desc.equals(COMMAND_DESC)
                    && (call.name.equals("execute") || call.name.equals("func_184881_a"))) calls.add(call);
            if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(OPERATOR)
                    && call.name.equals("runCommand") && call.desc.equals(RUN_COMMAND_DESC)) installed++;
        }
        if (calls.isEmpty() && installed == 1) return false;
        if (calls.size() != 1 || installed != 0)
            throw unsupported(type.name, "expected one verified ICommand.execute site; found " + calls.size() + " and " + installed + " operator hooks");
        MethodInsnNode call = calls.get(0);
        call.setOpcode(Opcodes.INVOKESTATIC); call.owner = OPERATOR;
        call.name = "runCommand"; call.desc = RUN_COMMAND_DESC; call.itf = false;
        return true;
    }

    private static int countCall(MethodNode method, String owner, String name, String desc, int opcode) {
        int result = 0;
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (!(node instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) node;
            if (call.getOpcode() == opcode && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) result++;
        }
        return result;
    }

    /** Magma's Bukkit wrapper dispatches vanilla commands without CommandHandler. */
    private static boolean magmaCommands(ClassNode type) {
        String descriptor = "(Lorg/bukkit/command/CommandSender;Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)I";
        MethodNode dispatch = unique(type, "dispatchVanillaCommand", "dispatchVanillaCommand", descriptor);
        if ((dispatch.access & Opcodes.ACC_STATIC) != 0) throw unsupported(type.name, "static Magma command dispatch");
        List<MethodInsnNode> original = new ArrayList<MethodInsnNode>();
        List<MethodInsnNode> guarded = new ArrayList<MethodInsnNode>();
        for (MethodNode method : type.methods) for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (!(node instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) node;
            boolean vanilla = call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals(COMMAND_BASE)
                    && call.desc.equals(COMMAND_DESC) && (call.name.equals("execute") || call.name.equals("func_184881_a"));
            boolean installed = call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(OPERATOR)
                    && call.name.equals("runCommand") && call.desc.equals(RUN_COMMAND_DESC);
            if (!vanilla && !installed) continue;
            if (method != dispatch || !magmaCommandOperands(type.name, call))
                throw unsupported(type.name, "Magma command invocation is outside the verified dispatch operands");
            if (vanilla) original.add(call); else guarded.add(call);
        }
        if (original.isEmpty() && guarded.size() == 2) return false;
        if (original.size() != 2 || !guarded.isEmpty())
            throw unsupported(type.name, "expected two verified Magma vanilla-command calls; found "
                    + original.size() + " original and " + guarded.size() + " guarded calls");
        for (MethodInsnNode call : original) {
            call.setOpcode(Opcodes.INVOKESTATIC); call.owner = OPERATOR;
            call.name = "runCommand"; call.desc = RUN_COMMAND_DESC; call.itf = false;
        }
        return true;
    }

    private static boolean magmaCommandOperands(String owner, MethodInsnNode call) {
        AbstractInsnNode args = previous(call), sender = previous(args), server = previous(sender);
        AbstractInsnNode command = previous(server), receiver = previous(command);
        if (!aload(args) || ((VarInsnNode) args).var != 3 || !aload(sender) || ((VarInsnNode) sender).var != 2
                || !aload(server) || ((VarInsnNode) server).var != 6 || !aload(receiver) || ((VarInsnNode) receiver).var != 0
                || !(command instanceof FieldInsnNode) || command.getOpcode() != Opcodes.GETFIELD) return false;
        FieldInsnNode field = (FieldInsnNode) command;
        return field.owner.equals(owner) && field.name.equals("vanillaCommand") && field.desc.equals("L" + COMMAND_BASE + ";");
    }

    private static List<Gate> gates(String owner) {
        if (owner.equals(WORLD)) return Arrays.asList(
                new Gate("setBlockState", "func_180501_a", SET_DESC, "blockChange", "(" + WORLD_REF + POS + STATE + ")Z", Opcodes.IRETURN, 0, 1, 2),
                new Gate("destroyBlock", "func_175655_b", "(" + POS + "Z)Z", "blockDestroy", "(" + WORLD_REF + POS + ")Z", Opcodes.IRETURN, 0, 1),
                new Gate("spawnEntity", "func_72838_d", "(" + ENTITY + ")Z", "blockSpawn", "(" + WORLD_REF + ENTITY + ")Z", Opcodes.IRETURN, 0, 1));
        if (owner.equals(CHUNK)) return Arrays.asList(
                new Gate("setBlockState", "func_177436_a", "(" + POS + STATE + ")" + STATE, "blockChunkChange", "(" + CHUNK_REF + POS + STATE + ")Z", Opcodes.ARETURN, 0, 1, 2));
        if (owner.equals(BLOCK)) return Arrays.asList(
                new Gate("dropBlockAsItemWithChance", "func_180653_a", "(" + WORLD_REF + POS + STATE + "FI)V", "blockDrop", "(" + WORLD_REF + POS + ")Z", Opcodes.RETURN, 1, 2),
                new Gate("spawnAsEntity", "func_180635_a", "(" + WORLD_REF + POS + ITEM + ")V", "blockDrop", "(" + WORLD_REF + POS + ")Z", Opcodes.RETURN, 0, 1));
        if (owner.equals(PISTON)) return Arrays.asList(
                new Gate("doMove", "func_176319_a", "(" + WORLD_REF + POS + FACING + "Z)Z", "blockPiston", "(" + WORLD_REF + POS + FACING + "Z)Z", Opcodes.IRETURN, 1, 2, 3, 4));
        throw unsupported(owner, "unknown gate owner");
    }

    private static MethodNode unique(ClassNode owner, String mcp, String srg, String desc) {
        MethodNode result = null;
        for (MethodNode method : owner.methods) {
            if ((method.name.equals(mcp) || method.name.equals(srg)) && method.desc.equals(desc)) {
                if (result != null) throw unsupported(owner.name, "ambiguous method " + mcp + desc);
                result = method;
            }
        }
        if (result == null || (result.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0)
            throw unsupported(owner.name, "missing executable method " + mcp + " / " + srg + desc);
        return result;
    }

    private static boolean entry(String owner, MethodNode method, Gate gate) {
        int hooks = count(method, gate.hook, gate.hookDesc);
        if (hooks != 0) {
            if (hooks != 1 || !matchesEntry(method, gate))
                throw unsupported(owner, "unrecognized existing guard in " + method.name);
            return false;
        }
        LabelNode proceed = new LabelNode();
        InsnList list = new InsnList();
        loads(list, gate);
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, gate.hook, gate.hookDesc, false));
        list.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
        denyReturn(list, gate.result);
        list.add(proceed);
        Object[] initial = initialLocals(owner, method);
        list.add(new FrameNode(Opcodes.F_NEW, initial.length, initial, 0, new Object[0]));
        method.instructions.insert(list);
        return true;
    }

    private static void loads(InsnList list, Gate gate) {
        Type[] arguments = Type.getArgumentTypes(gate.hookDesc);
        if (arguments.length != gate.locals.length) throw new IllegalStateException("Invalid internal guard arguments");
        for (int i = 0; i < arguments.length; i++)
            list.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ILOAD), gate.locals[i]));
    }

    private static void denyReturn(InsnList list, int opcode) {
        if (opcode == Opcodes.IRETURN) list.add(new InsnNode(Opcodes.ICONST_0));
        else if (opcode == Opcodes.ARETURN) list.add(new InsnNode(Opcodes.ACONST_NULL));
        list.add(new InsnNode(opcode));
    }

    private static boolean matchesEntry(MethodNode method, Gate gate) {
        AbstractInsnNode node = meaningful(method.instructions.getFirst());
        Type[] arguments = Type.getArgumentTypes(gate.hookDesc);
        for (int i = 0; i < arguments.length; i++) {
            if (!(node instanceof VarInsnNode) || node.getOpcode() != arguments[i].getOpcode(Opcodes.ILOAD)
                    || ((VarInsnNode) node).var != gate.locals[i]) return false;
            node = next(node);
        }
        if (!hook(node, gate.hook, gate.hookDesc)) return false;
        node = next(node);
        if (!(node instanceof JumpInsnNode) || node.getOpcode() != Opcodes.IFEQ) return false;
        LabelNode target = ((JumpInsnNode) node).label;
        node = next(node);
        if (gate.result != Opcodes.RETURN) {
            if (node == null || node.getOpcode() != (gate.result == Opcodes.IRETURN ? Opcodes.ICONST_0 : Opcodes.ACONST_NULL)) return false;
            node = next(node);
        }
        if (node == null || node.getOpcode() != gate.result) return false;
        for (node = node.getNext(); node != null && node.getOpcode() < 0; node = node.getNext())
            if (node == target) return true;
        return false;
    }

    private static boolean ahznbBreak(ClassNode owner) {
        String desc = "(" + WORLD_REF + POS + "FFFZ)Lnet/minecraft/entity/item/EntityItem;";
        MethodNode method = unique(owner, "breakBlockAndDropWithChance", "breakBlockAndDropWithChance", desc);
        if ((method.access & Opcodes.ACC_STATIC) == 0) throw unsupported(owner.name, "non-static AHZNB block helper");
        Gate gate = new Gate("breakBlockAndDropWithChance", "breakBlockAndDropWithChance", desc,
                "blockDestroy", "(" + WORLD_REF + POS + ")Z", Opcodes.ARETURN, 0, 1);
        String dropDesc = "(" + WORLD_REF + POS + "F)F";
        int filters = count(method, "filterDropChance", dropDesc);
        int destroys = count(method, gate.hook, gate.hookDesc);
        if (filters != 0 || destroys != 0) {
            if (filters != 1 || destroys != 1 || !matchesEntry(method, gate)
                    || !matchesFilterAfterEntry(method, dropDesc))
                throw unsupported(owner.name, "unrecognized existing AHZNB helper guard");
            return false;
        }
        // Insert filter first, then the destructive-operation entry gate in front.
        InsnList filter = new InsnList();
        filter.add(new VarInsnNode(Opcodes.ALOAD, 0));
        filter.add(new VarInsnNode(Opcodes.ALOAD, 1));
        filter.add(new VarInsnNode(Opcodes.FLOAD, 4));
        filter.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "filterDropChance", dropDesc, false));
        filter.add(new VarInsnNode(Opcodes.FSTORE, 4));
        method.instructions.insert(filter);
        entry(owner.name, method, gate);
        return true;
    }

    private static boolean matchesFilterAfterEntry(MethodNode method, String desc) {
        AbstractInsnNode node = meaningful(method.instructions.getFirst());
        while (node != null && !(node instanceof JumpInsnNode)) node = next(node);
        if (node == null) return false;
        node = meaningful(((JumpInsnNode) node).label);
        int[] opcodes = {Opcodes.ALOAD, Opcodes.ALOAD, Opcodes.FLOAD};
        int[] locals = {0, 1, 4};
        for (int i = 0; i < 3; i++) {
            if (!(node instanceof VarInsnNode) || node.getOpcode() != opcodes[i] || ((VarInsnNode) node).var != locals[i]) return false;
            node = next(node);
        }
        if (!hook(node, "filterDropChance", desc)) return false;
        node = next(node);
        return node instanceof VarInsnNode && node.getOpcode() == Opcodes.FSTORE && ((VarInsnNode) node).var == 4;
    }

    private static boolean kamuiBlock(ClassNode owner) {
        String desc = "(" + WORLD_REF + POS + "I)I";
        MethodNode method = unique(owner, "eBlock", "eBlock", desc);
        if ((method.access & Opcodes.ACC_STATIC) == 0)
            throw unsupported(owner.name, "non-static Kamui block helper");
        Gate gate = new Gate("eBlock", "eBlock", desc, "handleKamuiBlock",
                "(" + WORLD_REF + POS + ")Z", Opcodes.IRETURN, 0, 1);
        int guards = count(method, gate.hook, gate.hookDesc);
        AbstractInsnNode start = meaningful(method.instructions.getFirst());
        if (guards != 0) {
            if (guards != 1 || !matchesEntry(method, gate))
                throw unsupported(owner.name, "unrecognized existing Kamui entry guard");
            while (!(start instanceof JumpInsnNode)) start = next(start);
            start = meaningful(((JumpInsnNode) start).label);
        }
        String shape = kamuiShape(owner.name, method, start);
        if (!KAMUI_BLOCK_SHAPE.equals(shape))
            throw unsupported(owner.name, "Kamui eBlock instruction structure differs from verified AHZNB 0.3.1-beta (" + shape + ")");
        return entry(owner.name, method, gate);
    }

    /**
     * Canonicalizes only instruction semantics, including branch destinations.
     * Debug tables, stack frames and constant-pool layout do not affect this
     * check; an added side effect or changed operand does. MCP and SRG names
     * are equivalent. Unsupported future implementations fail before loading.
     */
    private static String kamuiShape(String owner, MethodNode method, AbstractInsnNode start) {
        if (!method.tryCatchBlocks.isEmpty()) throw unsupported(owner, "unexpected Kamui exception handler");
        Map<AbstractInsnNode, Integer> indices = new IdentityHashMap<AbstractInsnNode, Integer>();
        int index = 0;
        for (AbstractInsnNode node = start; node != null; node = next(node)) indices.put(node, index++);
        StringBuilder structure = new StringBuilder();
        for (AbstractInsnNode node = start; node != null; node = next(node)) {
            structure.append(node.getOpcode());
            if (node instanceof VarInsnNode) structure.append(':').append(((VarInsnNode) node).var);
            else if (node instanceof IincInsnNode) {
                IincInsnNode increment = (IincInsnNode) node;
                structure.append(':').append(increment.var).append(':').append(increment.incr);
            } else if (node instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) node;
                structure.append(':').append(call.owner).append(':').append(kamuiCallName(call))
                        .append(':').append(call.desc).append(':').append(call.itf);
            } else if (node instanceof TypeInsnNode) structure.append(':').append(((TypeInsnNode) node).desc);
            else if (node instanceof JumpInsnNode) {
                Integer destination = indices.get(meaningful(((JumpInsnNode) node).label));
                if (destination == null) throw unsupported(owner, "Kamui branch leaves its verified method body");
                structure.append(':').append(destination);
            } else if (!(node instanceof InsnNode)) throw unsupported(owner, "unexpected Kamui instruction kind " + node.getType());
            structure.append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(structure.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >>> 4) & 15, 16));
                hex.append(Character.forDigit(value & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }

    private static String kamuiCallName(MethodInsnNode call) {
        String[][] aliases = {
                {WORLD, "func_180495_p", "getBlockState"}, {WORLD, "func_72838_d", "spawnEntity"},
                {WORLD, "func_175698_g", "setBlockToAir"},
                {"net/minecraft/block/state/IBlockState", "func_177230_c", "getBlock"},
                {"net/minecraft/util/math/BlockPos", "func_177958_n", "getX"},
                {"net/minecraft/util/math/BlockPos", "func_177956_o", "getY"},
                {"net/minecraft/util/math/BlockPos", "func_177952_p", "getZ"},
                {"net/minecraft/entity/item/EntityItem", "func_174869_p", "setDefaultPickupDelay"},
                {"net/minecraft/entity/item/EntityItem", "func_184204_a", "changeDimension"}
        };
        for (String[] alias : aliases) if (call.owner.equals(alias[0]) && call.name.equals(alias[1])) return alias[2];
        return call.name;
    }

    private static boolean portal(ClassNode owner) {
        MethodNode method = unique(owner, "executeProcedure", "executeProcedure", "(Ljava/util/Map;)V");
        if ((method.access & Opcodes.ACC_STATIC) == 0) throw unsupported(owner.name, "non-static portal procedure");
        String hookDesc = "(" + WORLD_REF + POS + POS + ")Z";
        int present = count(method, "blockPortal", hookDesc);
        List<MethodInsnNode> writes = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) node;
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals(WORLD) && call.desc.equals(SET_DESC)
                        && (call.name.equals("setBlockState") || call.name.equals("func_180501_a"))) writes.add(call);
            }
        }
        if (writes.size() != 4) throw unsupported(owner.name, "portal must contain exactly four direct block writes (found " + writes.size() + ")");
        PortalWrite first = portalWrite(owner.name, writes.get(0));
        PortalWrite third = portalWrite(owner.name, writes.get(2));
        if (first.world != third.world) throw unsupported(owner.name, "portal uses different World locals");
        if (present != 0) {
            if (present != 1 || !matchesPortalBefore(first.start, first.world, first.pos, third.pos, hookDesc))
                throw unsupported(owner.name, "unrecognized existing portal preflight");
            return false;
        }
        // Reject rebinding between preflight and destination placement.
        for (AbstractInsnNode node = first.start; node != writes.get(2); node = node.getNext()) {
            if (node == null) throw unsupported(owner.name, "disconnected portal write sequence");
            if (node instanceof VarInsnNode && node.getOpcode() == Opcodes.ASTORE) {
                int local = ((VarInsnNode) node).var;
                if (local == first.world || local == first.pos || local == third.pos)
                    throw unsupported(owner.name, "portal target local changes after preflight");
            }
        }
        AnalyzerAdapter analyzer = new AnalyzerAdapter(owner.name, method.access, method.name, method.desc, null);
        for (AbstractInsnNode node = method.instructions.getFirst(); node != first.start; node = node.getNext()) {
            if (node == null) throw unsupported(owner.name, "missing portal first-write site");
            node.accept(analyzer);
        }
        if (analyzer.locals == null || analyzer.stack == null || !analyzer.stack.isEmpty())
            throw unsupported(owner.name, "portal preflight is not at a verified empty-stack boundary");
        for (int local : new int[] {first.world, first.pos, third.pos}) {
            if (local >= analyzer.locals.size() || !(analyzer.locals.get(local) instanceof String))
                throw unsupported(owner.name, "portal target local is not initialized before the first write");
        }
        Object[] locals = frameLocals(owner.name, analyzer.locals);
        LabelNode proceed = new LabelNode();
        InsnList check = new InsnList();
        check.add(new VarInsnNode(Opcodes.ALOAD, first.world));
        check.add(new VarInsnNode(Opcodes.ALOAD, first.pos));
        check.add(new VarInsnNode(Opcodes.ALOAD, third.pos));
        check.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "blockPortal", hookDesc, false));
        check.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
        check.add(new InsnNode(Opcodes.RETURN));
        check.add(proceed);
        check.add(new FrameNode(Opcodes.F_NEW, locals.length, locals, 0, new Object[0]));
        method.instructions.insertBefore(first.start, check);
        return true;
    }

    private static final class PortalWrite {
        final AbstractInsnNode start;
        final int world, pos;
        PortalWrite(AbstractInsnNode start, int world, int pos) { this.start = start; this.world = world; this.pos = pos; }
    }

    private static PortalWrite portalWrite(String owner, MethodInsnNode call) {
        AbstractInsnNode flags = previous(call), state = previous(flags), pos = previous(state), world = previous(pos);
        if (flags == null || flags.getOpcode() != Opcodes.ICONST_3 || !aload(state) || !aload(pos) || !aload(world))
            throw unsupported(owner, "portal source/destination write has an unsupported operand layout");
        return new PortalWrite(world, ((VarInsnNode) world).var, ((VarInsnNode) pos).var);
    }

    private static boolean matchesPortalBefore(AbstractInsnNode first, int world, int a, int b, String desc) {
        AbstractInsnNode ret = previous(first);
        if (ret == null || ret.getOpcode() != Opcodes.RETURN) return false;
        AbstractInsnNode branch = previous(ret);
        if (!(branch instanceof JumpInsnNode) || branch.getOpcode() != Opcodes.IFEQ
                || meaningful(((JumpInsnNode) branch).label) != first) return false;
        AbstractInsnNode call = previous(branch);
        if (!hook(call, "blockPortal", desc)) return false;
        int[] expected = {b, a, world};
        for (int local : expected) {
            call = previous(call);
            if (!aload(call) || ((VarInsnNode) call).var != local) return false;
        }
        return true;
    }

    private static Object[] frameLocals(String owner, List<Object> slots) {
        List<Object> values = new ArrayList<Object>();
        for (int i = 0; i < slots.size(); i++) {
            Object value = slots.get(i);
            if (!(value instanceof Integer) && !(value instanceof String))
                throw unsupported(owner, "uninitialized object in portal preflight frame");
            values.add(value);
            if (value.equals(Opcodes.LONG) || value.equals(Opcodes.DOUBLE)) {
                if (++i >= slots.size() || !Opcodes.TOP.equals(slots.get(i)))
                    throw unsupported(owner, "invalid wide local in portal frame");
            }
        }
        return values.toArray(new Object[values.size()]);
    }

    private static Object[] initialLocals(String owner, MethodNode method) {
        List<Object> locals = new ArrayList<Object>();
        if ((method.access & Opcodes.ACC_STATIC) == 0) locals.add(owner);
        for (Type type : Type.getArgumentTypes(method.desc)) {
            switch (type.getSort()) {
                case Type.BOOLEAN: case Type.BYTE: case Type.CHAR: case Type.SHORT: case Type.INT: locals.add(Opcodes.INTEGER); break;
                case Type.FLOAT: locals.add(Opcodes.FLOAT); break;
                case Type.LONG: locals.add(Opcodes.LONG); break;
                case Type.DOUBLE: locals.add(Opcodes.DOUBLE); break;
                case Type.ARRAY: locals.add(type.getDescriptor()); break;
                case Type.OBJECT: locals.add(type.getInternalName()); break;
                default: throw unsupported(owner, "unsupported argument type " + type);
            }
        }
        return locals.toArray(new Object[locals.size()]);
    }

    private static int count(MethodNode method, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext())
            if (hook(node, name, desc)) count++;
        return count;
    }
    private static boolean hook(AbstractInsnNode node, String name, String desc) {
        if (!(node instanceof MethodInsnNode)) return false;
        MethodInsnNode call = (MethodInsnNode) node;
        return call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(HOOK) && call.name.equals(name) && call.desc.equals(desc);
    }
    private static boolean aload(AbstractInsnNode node) { return node instanceof VarInsnNode && node.getOpcode() == Opcodes.ALOAD; }
    private static AbstractInsnNode meaningful(AbstractInsnNode node) { while (node != null && node.getOpcode() < 0) node = node.getNext(); return node; }
    private static AbstractInsnNode next(AbstractInsnNode node) { return node == null ? null : meaningful(node.getNext()); }
    private static AbstractInsnNode previous(AbstractInsnNode node) {
        if (node == null) return null;
        node = node.getPrevious();
        while (node != null && node.getOpcode() < 0) node = node.getPrevious();
        return node;
    }
    private static IllegalStateException unsupported(String owner, String message) {
        return new IllegalStateException("Dungeon Guard cannot safely protect " + owner.replace('/', '.') + ": " + message
                + ". Unsupported coremod/mod bytecode; no partial protection is accepted.");
    }
}

package village.vale.clientcompat;

import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Original synthetic fixtures plus read-only inspection of the privately supplied class. */
public final class GuiThreadDraftChecks {
    static int assertions;
    static void check(boolean value) { assertions++; if (!value) throw new AssertionError("check " + assertions); }
    static void rejects(Runnable value) { boolean rejected=false;try { value.run(); } catch (IllegalStateException expected) { rejected=true; } check(rejected); }
    static byte[] fixture(String method, String desc, String schedulerMethod, int count, int access) {
        return fixture(NpcGuiThreadTransformer.TARGET,method,desc,schedulerMethod,count,access);
    }
    static byte[] fixture(String target,String method, String desc, String schedulerMethod, int count, int access) {
        ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        out.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,target.replace('.','/'),null,"java/lang/Object",null);
        for (String name : new String[]{method,"untouchedOtherMethod"}) {
            MethodVisitor mv=out.visitMethod(access,name,name.equals(method)?desc:"()V",null,null);mv.visitCode();
            for(int i=0;i<(name.equals(method)?count:1);i++) {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,NpcGuiThreadTransformer.SCHEDULER,schedulerMethod,NpcGuiThreadTransformer.RUNNABLE,false);
            }
            mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,0);mv.visitEnd();
        }
        out.visitEnd();return out.toByteArray();
    }
    static void verify(byte[] transformed) throws Exception {
        ClassNode node=new ClassNode();new ClassReader(transformed).accept(node,0);
        for(Object entry:node.methods) {
            MethodNode method=(MethodNode)entry;
            if ((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0)
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name,method);
        }
        assertions++;
    }
    static byte[] methodSnapshot(MethodNode method) {
        ClassWriter out=new ClassWriter(0);out.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,"original/Snapshot",null,"java/lang/Object",null);
        method.accept(out);out.visitEnd();return out.toByteArray();
    }
    static void unchangedOtherMethods(byte[] before,byte[] after) {
        ClassNode a=new ClassNode(),b=new ClassNode();new ClassReader(before).accept(a,0);new ClassReader(after).accept(b,0);
        check(a.methods.size()==b.methods.size());
        for(int i=0;i<a.methods.size();i++) {
            MethodNode old=(MethodNode)a.methods.get(i),now=(MethodNode)b.methods.get(i);
            if(!old.name.equals(NpcGuiThreadTransformer.METHOD))check(Arrays.equals(methodSnapshot(old),methodSnapshot(now)));
        }
    }
    static int count(byte[] bytes,String method,String owner,String call) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int result=0;
        for(Object entry:node.methods) { MethodNode m=(MethodNode)entry;if(!m.name.equals(method))continue;
            for(AbstractInsnNode insn=m.instructions.getFirst();insn!=null;insn=insn.getNext())
                if(insn instanceof MethodInsnNode) { MethodInsnNode invocation=(MethodInsnNode)insn;
                    if(invocation.owner.equals(owner)&&invocation.name.equals(call))result++;
                }
        }return result;
    }
    public static void main(String[] args) throws Exception {
        int flags=Opcodes.ACC_PUBLIC;
        byte[] rawFixture=fixture(NpcGuiThreadTransformer.METHOD,"()V","runTack",1,flags);
        byte[] outputFixture=NpcGuiThreadTransformer.rewrite(rawFixture);
        check(count(outputFixture,NpcGuiThreadTransformer.METHOD,NpcGuiThreadTransformer.HELPER,"run")==1);
        check(count(outputFixture,"untouchedOtherMethod",NpcGuiThreadTransformer.SCHEDULER,"runTack")==1);
        check(count(outputFixture,NpcGuiThreadTransformer.METHOD,NpcGuiThreadTransformer.SCHEDULER,"runTack")==0);
        verify(outputFixture);unchangedOtherMethods(rawFixture,outputFixture);
        rejects(()->NpcGuiThreadTransformer.rewrite(outputFixture));
        rejects(()->NpcGuiThreadTransformer.rewrite(fixture("differentMethod","()V","runTack",1,flags)));
        rejects(()->NpcGuiThreadTransformer.rewrite(fixture(NpcGuiThreadTransformer.METHOD,"(I)V","runTack",1,flags)));
        rejects(()->NpcGuiThreadTransformer.rewrite(fixture(NpcGuiThreadTransformer.METHOD,"()V","runTack",0,flags)));
        rejects(()->NpcGuiThreadTransformer.rewrite(fixture(NpcGuiThreadTransformer.METHOD,"()V","runTack",2,flags)));
        rejects(()->NpcGuiThreadTransformer.rewrite(fixture(NpcGuiThreadTransformer.METHOD,"()V","unexpectedCall",1,flags)));
        rejects(()->NpcGuiThreadTransformer.rewrite(fixture(NpcGuiThreadTransformer.METHOD,"()V","runTack",1,flags|Opcodes.ACC_STATIC)));
        rejects(()->NpcGuiThreadTransformer.rewrite(fixture(NpcGuiThreadTransformer.METHOD,"()V","runTack",1,Opcodes.ACC_PRIVATE)));
        byte[] factionsFixture=fixture(NpcGuiThreadTransformer.FACTIONS,NpcGuiThreadTransformer.METHOD,"()V","runTack",1,flags);
        byte[] factionsOutput=NpcGuiThreadTransformer.rewrite(factionsFixture);
        verify(factionsOutput);unchangedOtherMethods(factionsFixture,factionsOutput);
        check(count(factionsOutput,NpcGuiThreadTransformer.METHOD,NpcGuiThreadTransformer.HELPER,"run")==1);
        rejects(()->NpcGuiThreadTransformer.rewrite(factionsOutput));
        rejects(()->NpcGuiThreadTransformer.rewrite(fixture("unrelated/InventoryTab",NpcGuiThreadTransformer.METHOD,"()V","runTack",1,flags)));
        NpcGuiThreadTransformer transformer=new NpcGuiThreadTransformer();byte[] unrelated=new byte[]{1,2};
        check(transformer.transform("other","other",unrelated)==unrelated);check(transformer.transform(null,null,null)==null);
        URL source=new URL("jar:"+Paths.get(args[0]).toUri()+"!/"+NpcGuiThreadTransformer.TARGET.replace('.','/')+".class");
        SupportedNpcSource.verify(source);assertions++;
        rejects(()->SupportedNpcSource.verify(null));
        rejects(()->{try{SupportedNpcSource.verify(new URL("file:/not-the-reviewed-jar.class"));}catch(java.net.MalformedURLException bad){throw new AssertionError(bad);}});
        rejects(()->{try{SupportedNpcSource.verify(new URL("jar:https://invalid.example/npc.jar!/x.class"));}catch(java.net.MalformedURLException bad){throw new AssertionError(bad);}});
        rejects(()->{try{SupportedNpcSource.verify(new URL("jar:"+Paths.get(args[1]).toUri()+"!/x.class"));}catch(java.net.MalformedURLException bad){throw new AssertionError(bad);}});
        try(ZipFile zip=new ZipFile(args[0])) {
          for(String target:new String[]{NpcGuiThreadTransformer.TARGET,NpcGuiThreadTransformer.FACTIONS}) {
            URL targetSource=new URL("jar:"+Paths.get(args[0]).toUri()+"!/"+target.replace('.','/')+".class");
            SupportedNpcSource.verify(targetSource,target);assertions++;
            rejects(()->SupportedNpcSource.verify(targetSource,"unsupported.Target"));
            String other=target.equals(NpcGuiThreadTransformer.TARGET)?NpcGuiThreadTransformer.FACTIONS:NpcGuiThreadTransformer.TARGET;
            rejects(()->SupportedNpcSource.verify(targetSource,other));
            byte[] raw;
            try(java.io.InputStream stream=zip.getInputStream(zip.getEntry(target.replace('.','/')+".class"));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];int n;while((n=stream.read(buffer))!=-1)out.write(buffer,0,n);raw=out.toByteArray();
            }
            byte[] output=NpcGuiThreadTransformer.rewrite(raw);verify(output);
            check(count(raw,NpcGuiThreadTransformer.METHOD,NpcGuiThreadTransformer.SCHEDULER,"runTack")==1);
            check(count(output,NpcGuiThreadTransformer.METHOD,NpcGuiThreadTransformer.HELPER,"run")==1);
            check(count(output,NpcGuiThreadTransformer.METHOD,NpcGuiThreadTransformer.SCHEDULER,"runTack")==0);
            check(count(output,"lambda$onTabClicked$0","noppes/npcs/client/gui/player/"+(target.equals(NpcGuiThreadTransformer.TARGET)?"GuiQuestLog":"GuiFaction"),"<init>")==1);
            check(count(output,"lambda$onTabClicked$0","net/minecraft/client/Minecraft","func_147108_a")==1);
            unchangedOtherMethods(raw,output);
          }
        }
        System.out.println("GUI_THREAD_DRAFT_BYTECODE_PASS assertions="+assertions);
    }
}

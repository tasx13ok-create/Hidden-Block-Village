package village.vale.clientcompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
public final class GuiThreadDispatchChecks {
    static int assertions;
    static void check(boolean value){assertions++;if(!value)throw new AssertionError("check "+assertions);}
    public static void main(String[] args)throws Exception {
        Minecraft mc=new Minecraft();GuiScreen first=new GuiScreen();NpcGuiThreadDispatch.run(()->mc.displayGuiScreen(first));
        check(mc.calls==1);check(mc.current==first);check(mc.queue.isEmpty());
        GuiScreen second=new GuiScreen();Thread worker=new Thread(()->NpcGuiThreadDispatch.run(()->mc.displayGuiScreen(second)));worker.start();worker.join();
        check(mc.calls==1);check(mc.current==first);check(mc.queue.size()==1);mc.queue.remove().run();check(mc.calls==2);check(mc.current==second);
        Thread close=new Thread(()->NpcGuiThreadDispatch.run(()->mc.displayGuiScreen(null)));close.start();close.join();check(mc.calls==2);mc.queue.remove().run();check(mc.calls==3);check(mc.current==null);check(mc.queue.isEmpty());
        for(int index=0;index<args.length;index++) {
        final String target=index==0?NpcGuiThreadTransformer.TARGET:NpcGuiThreadTransformer.FACTIONS;
        Class<?> gui=index==0?noppes.npcs.client.gui.player.GuiQuestLog.class:noppes.npcs.client.gui.player.GuiFaction.class;
        java.lang.reflect.Field constructions=gui.getField("constructions"),constructedOn=gui.getField("constructedOn");
        int beforeCalls=mc.calls,beforeScheduler=noppes.npcs.util.CustomNPCsScheduler.calls;
        byte[] input=java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[index]));byte[] output=NpcGuiThreadTransformer.rewrite(input);
        Class<?> fixture=new ClassLoader(GuiThreadDispatchChecks.class.getClassLoader()) {
            Class<?> define(){return defineClass(target,output,0,output.length);}
        }.define();
        Object tab=fixture.newInstance();java.lang.reflect.Method click=fixture.getMethod("onTabClicked");
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread questWorker=new Thread(()->{try{click.invoke(tab);}catch(Throwable error){failure.set(error);}});
        questWorker.start();questWorker.join();check(failure.get()==null);
        check(constructions.getInt(null)==0);check(mc.calls==beforeCalls);check(mc.queue.size()==1);
        check(noppes.npcs.util.CustomNPCsScheduler.calls==beforeScheduler);mc.queue.remove().run();
        check(constructions.getInt(null)==1);
        check(constructedOn.get(null)==mc.owner);check(mc.calls==beforeCalls+1);
        check(gui.isInstance(mc.current));check(mc.queue.isEmpty());
        click.invoke(tab);check(constructions.getInt(null)==2);check(mc.calls==beforeCalls+2);check(mc.queue.isEmpty());
        fixture.getMethod("untouchedScheduler").invoke(tab);check(noppes.npcs.util.CustomNPCsScheduler.calls==beforeScheduler+1);
        }
        System.out.println("GUI_THREAD_DRAFT_DISPATCH_PASS assertions="+assertions);
    }
}

package village.bijuu;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.model.*;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.*;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Disposable singleplayer acceptance fixture. Never packaged in the playable JAR. */
@Mod(modid="bijuurunprobe",name="Bijuu Running Probe",version="1",dependencies="required-after:hvbijuu_motion")
@Mod.EventBusSubscriber
public final class RunProbe {
    static volatile int t=-1,beast=Integer.getInteger("bijuu.firstBeast",0);
    static int warmup;
    static boolean opened, checked;
    static int captured=-1, lastMovieFrame=-1;
    static final boolean MOVIE=Boolean.getBoolean("bijuu.motionMovie");
    static final UUID ID=UUID.fromString("93ab39b2-5d9f-48ec-88bd-8ef2b18a3a2d");
    static double peak,baseline,late,start,firstRoot;
    static volatile boolean movedJoints;
    static boolean cleared;
    static final ClientProxy proxy=new ClientProxy();
    static final ModelBiped base=new ModelBiped();
    static void check(boolean ok,String message){if(!ok)throw new AssertionError("RUN_PROBE_FAIL "+message);}
    @SubscribeEvent public static void input(InputUpdateEvent e){
        if(t<0)return;
        boolean go=t>=15 && (t<205 || t>=233 && t<302);
        e.getMovementInput().field_192832_b=go?1:0;
        e.getMovementInput().field_78901_c=t==165;
        e.getMovementInput().field_78899_d=t>=212 && t<219;
        e.getEntityPlayer().func_70031_b(go);
    }
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent ev)throws Exception {
        if(ev.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.func_71410_x();
        if(!opened && mc.field_71462_r instanceof GuiMainMenu){
            opened=true;
            WorldSettings settings=new WorldSettings(17,GameType.SURVIVAL,false,false,WorldType.field_77138_c);
            settings.func_77166_b();mc.func_71371_a("motion-probe","Motion probe",settings);return;
        }
        if(beast>=9){mc.func_71400_g();return;}
        if(mc.field_71439_g==null)return;
        if(mc.field_71462_r!=null)mc.func_147108_a(null);
        mc.field_71474_y.field_74320_O=1;
        mc.field_71439_g.field_70177_z=0;
        if(t<10 || beast>=9)return;
        if(!checked){PoseChecks.run(mc.field_71439_g);checked=true;}
        if(t>=147 && t<155 && captured!=beast){
            captured=beast;
            net.minecraft.util.ScreenShotHelper.func_148259_a(mc.field_71412_D,"sprint-"+beast+".png",
                mc.field_71443_c,mc.field_71440_d,mc.func_147110_a());
        }
        if(MOVIE){
            mc.field_71474_y.field_74319_N=true;
            mc.field_71439_g.field_70125_A=12;
            if(t>=128 && t<=220 && (t&1)==0){
                int frame=beast*1000+t;
                if(frame!=lastMovieFrame){
                    lastMovieFrame=frame;
                    net.minecraft.util.ScreenShotHelper.func_148259_a(mc.field_71412_D,
                        String.format("motion-%d-%03d.png",beast,t),mc.field_71443_c,mc.field_71440_d,mc.func_147110_a());
                }
            }
            if(t==184)mc.field_71439_g.func_184609_a(net.minecraft.util.EnumHand.MAIN_HAND);
        }
        ModelBiped model=proxy.model(beast,EntityEquipmentSlot.CHEST,base);
        model.func_78087_a(mc.field_71439_g.field_184619_aG,mc.field_71439_g.field_70721_aZ,
             mc.field_71439_g.field_70173_aa+.5f,0,0,.0625f,mc.field_71439_g);
        Field f=ReferenceTails.class.getDeclaredField("rendered");f.setAccessible(true);
        List<?> roots=(List<?>)((Map<?,?>)f.get(null)).get(model);
        check(roots.size()==beast+1,"appendage count");
        ModelRenderer root=(ModelRenderer)roots.get(0);
        double angle=root.field_78795_f+root.field_78796_g+root.field_78808_h;
        if(t<=18)firstRoot=angle;
        if(t>35 && Math.abs(angle-firstRoot)>.015)movedJoints=true;
    }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST) public static void server(TickEvent.PlayerTickEvent ev){
        if(ev.phase!=TickEvent.Phase.END || !(ev.player instanceof EntityPlayerMP))return;
        EntityPlayerMP p=(EntityPlayerMP)ev.player;
        if(!p.func_70005_c_().equals("MotionProbe") || p.field_70173_aa<40 || beast>=9)return;
        // Allow a longer uninterrupted warm-up on heavily modded clients before timing.
        int frame=t==125 && warmup++<100?t:++t;
        MinecraftServer server=p.func_184102_h();
        if(frame==0){
            server.func_71187_D().func_71556_a(server,"difficulty peaceful");
            net.minecraft.world.WorldServer world=(net.minecraft.world.WorldServer)p.field_70170_p;
            for(int chunkX=-1;chunkX<=0;chunkX++)
                for(int chunkZ=-1;chunkZ<=30;chunkZ++)
                    world.func_72964_e(chunkX,chunkZ);
            server.func_71187_D().func_71556_a(server,"fill -3 89 -4 3 89 480 minecraft:stone");
            check(!world.func_175623_d(new net.minecraft.util.math.BlockPos(0,89,0)) &&
                  !world.func_175623_d(new net.minecraft.util.math.BlockPos(0,89,475)),"test lane floor");
            p.field_70143_R=0;
            server.func_71187_D().func_71556_a(server,"time set day");
            p.field_71135_a.func_147364_a(.5,90,.5,0,0);
            server.func_71187_D().func_71556_a(server,"bijuureward "+p.func_70005_c_()+" "+BijuuArmor.BEASTS[beast]+" set");
            for(int i=0;i<36;i++){
                ItemStack stack=p.field_71071_by.func_70301_a(i);
                if(!(stack.func_77973_b() instanceof BijuuArmor.Armor))continue;
                BijuuArmor.Armor armor=(BijuuArmor.Armor)stack.func_77973_b();
                if(armor.beast!=beast)continue;
                p.field_71071_by.func_70299_a(i,ItemStack.field_190927_a);p.func_184201_a(armor.field_77881_a,stack);
            }
            check(BijuuArmor.full(p)==beast,"equip "+beast);
            peak=0;warmup=0;movedJoints=false;
        }
        AttributeModifier mod=p.func_110148_a(SharedMonsterAttributes.field_111263_d).func_111127_a(ID);
        double bonus=mod==null?0:mod.func_111164_d();
        peak=Math.max(peak,bonus);
        if(frame==133 || frame==268)start=p.field_70161_v;
        // Control leg uses the same gear, facing, terrain and sprint input.
        // Remove only our own attribute after the gameplay listener on each control tick.
        if(frame>=233 && mod!=null)p.func_110148_a(SharedMonsterAttributes.field_111263_d).func_111124_b(mod);
        if(frame==298)baseline=(p.field_70161_v-start)/30;
        if(frame==163)late=(p.field_70161_v-start)/30;
        if(frame==225)cleared=bonus==0;
        if(frame==304){
            System.out.println("RUN_PROBE beast="+beast+" cap="+peak+" control="+baseline+" late="+late+" cleared="+cleared+" joints="+movedJoints);
            check(Math.abs(peak-.18*(beast+1))<.0001 && cleared && baseline>.1 && late>baseline*1.05 && movedJoints,"beast "+beast);
            if(++beast==9){System.out.println("RUN_PROBE_PASS");}
            t=-1;
        }
    }
}

package village.vale;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Final native focus receipts. Hurt intent alone never settles a chakra charge. */
@Mod.EventBusSubscriber(modid="valeorders")
public final class FocusTransactions {
    private static final List<Contact> PENDING=new ArrayList<>();
    private FocusTransactions(){}
    static boolean effect(EntityPlayerMP p,Potion potion,int ticks,int amplifier,FocusPayment payment){
        PotionEffect old=p.getActivePotionEffect(potion);int duration=old==null?0:old.getDuration(),amp=old==null?-1:old.getAmplifier();
        try{p.addPotionEffect(new PotionEffect(potion,ticks,amplifier));}
        finally{PotionEffect after=p.getActivePotionEffect(potion);if(after!=null&&(after.getAmplifier()>amp||after.getDuration()>duration))payment.accepted();}
        return payment.applied;
    }
    static void modify(EntityPlayerMP p,OriginalFocusGear.Focus focus,LivingHurtEvent event,int cost,int cooldown,float amount,boolean increase){
        if(PENDING.size()>=128||!Float.isFinite(amount)||amount<=0)return;
        FocusPayment payment=FocusPayment.reserve(p,cost,cooldown);if(payment==null)return;
        boolean queued=false;
        try{if(!payment.ticket.valid()||OriginalFocusGear.active(p)!=focus||event.isCanceled()||event.getAmount()<=0||p.world!=event.getEntityLiving().world)return;if(increase&&!ArsenalAccess.allowed(p,event.getEntityLiving()))return;if(!increase&&(!(event.getSource().getTrueSource() instanceof EntityLivingBase)||!ArsenalAccess.allowed(p,(EntityLivingBase)event.getSource().getTrueSource())))return;
            Contact contact=new Contact(p,event,payment,amount,increase);PENDING.add(contact);queued=true;event.setAmount(amount);
        }finally{if(!queued)payment.close();}
    }
    private static final class Contact {
        final EntityPlayerMP player;final EntityLivingBase target;final LivingHurtEvent hurt;final FocusPayment payment;
        final float before,absBefore,modified;final boolean increase;LivingDamageEvent damage;float absorptionLoss,observed;
        Contact(EntityPlayerMP p,LivingHurtEvent event,FocusPayment payment,float amount,boolean increase){player=p;target=event.getEntityLiving();hurt=event;this.payment=payment;modified=amount;this.increase=increase;before=target.getHealth()+target.getAbsorptionAmount();absBefore=target.getAbsorptionAmount();}
        boolean admitted(){return damage!=null&&!hurt.isCanceled()&&!damage.isCanceled()&&Float.isFinite(hurt.getAmount())&&hurt.getAmount()>0&&Float.isFinite(damage.getAmount())&&damage.getAmount()>=0&&(damage.getAmount()>0||absorptionLoss>0)&&(increase?hurt.getAmount()>=modified:hurt.getAmount()<=modified);}
        void observe(){float now=target.getHealth()+target.getAbsorptionAmount();if(admitted()&&Float.isFinite(before)&&Float.isFinite(now))observed=Math.max(observed,before-now);}
        void finish(){try{observe();if(admitted()&&observed>0)payment.accepted();}finally{payment.close();}}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void damage(LivingDamageEvent event){for(Contact c:PENDING)if(c.damage==null&&c.target==event.getEntityLiving()&&c.hurt.getSource()==event.getSource()){c.damage=event;c.absorptionLoss=Math.max(0,c.absBefore-c.target.getAbsorptionAmount());}}
    @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true) public static void attack(LivingAttackEvent event){beforeHealthReset(event.getEntityLiving());}
    @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true) public static void heal(LivingHealEvent event){beforeHealthReset(event.getEntityLiving());}
    public static void beforeHealthReset(EntityLivingBase target){for(Contact c:PENDING)if(c.target==target)c.observe();}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END)return;List<Contact> batch=new ArrayList<>(PENDING);PENDING.clear();for(Contact c:batch)try{c.finish();}catch(RuntimeException|LinkageError failure){org.apache.logging.log4j.LogManager.getLogger("NarutoFocus").warn("Focus receipt settlement failed",failure);}}
}

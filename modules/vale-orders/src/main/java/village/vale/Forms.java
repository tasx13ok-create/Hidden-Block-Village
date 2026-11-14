package village.vale;

import net.minecraft.entity.SharedMonsterAttributes;
import village.vale.bridge.MoveCost;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Timed original aura transformations; native potion packets synchronize their appearance/state. */
@Mod.EventBusSubscriber(modid=ValeOrders.ID)
public final class Forms {
    private static final class FormEffect extends Potion { FormEffect(int color,int icon){super(false,color);setIconIndex(icon,0);} }
    public static final String[] KEYS={"bastion","zephyr","oracle","eclipse"};
    public static final int[] LEVELS={5,8,12,16};
    public static final Potion[] EFFECTS=new Potion[4];
    private static final java.util.Set<java.util.UUID> STARTING=new java.util.HashSet<>();
    private Forms(){}
    @SubscribeEvent public static void register(RegistryEvent.Register<Potion> e){
        int[] colors={0xb99d72,0x83cfc4,0xe4c576,0x887baf};
        for(int i=0;i<4;i++){
            Potion potion=new FormEffect(colors[i],i+1).setPotionName("effect.valeorders."+KEYS[i]);
            potion.setRegistryName(new ResourceLocation(ValeOrders.ID,"form_"+KEYS[i]));
            String id="3b104b70-8859-421f-a9f3-9a5c96d2030"+i;
            if(i==0)potion.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR,id,4,0);
            if(i==1)potion.registerPotionAttributeModifier(SharedMonsterAttributes.MOVEMENT_SPEED,id,.20,1);
            if(i==2)potion.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_SPEED,id,.15,1);
            if(i==3)potion.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_DAMAGE,id,.25,1);
            EFFECTS[i]=potion;e.getRegistry().register(potion);
        }
    }
    public static boolean activate(EntityPlayerMP p,int kind){
        if(kind<0||kind>=4||!p.isEntityAlive()||p.isSpectator()||STARTING.contains(p.getUniqueID()))return false;
        NBTTagCompound s=PlayerState.data(p);long now=Adventures.now(p);
        if(active(p)>=0){end(p,true);return true;}
        if(LightningKit.active(p))return Adventures.tell(p,"Release Lightning Chakra Armor before starting another form.");
        if(Combat.fullSet(p)==null)return Adventures.tell(p,"Equip a matching issued set before transforming.");
        if(PlayerState.level(p)<LEVELS[kind])return Adventures.tell(p,"This form requires level "+LEVELS[kind]+".");
        if(now<s.getLong("formReady"))return Adventures.tell(p,"Form cooling down: "+Rules.cooldownSeconds(now,s.getLong("formReady"))+"s.");
        MoveCost.Ticket payment=MoveCost.reserve(p,60);if(payment==null)return Adventures.tell(p,"A form needs 60 chakra units and three each second.");
        if(!payment.valid()||PlayerState.data(p)!=s||Combat.fullSet(p)==null||LightningKit.active(p)){payment.close();return false;}
        PotionEffect previous=p.getActivePotionEffect(EFFECTS[kind]);previous=previous==null?null:new PotionEffect(previous);
        long oldReady=s.getLong("formReady"),oldCombat=s.getLong("lastCombat");STARTING.add(p.getUniqueID());
        s.setString("form",KEYS[kind]);s.setLong("formEnd",now+400);s.setLong("formDrain",now);s.setLong("formReady",now+2400);s.setLong("lastCombat",now);
        try{
            if(!payment.valid()||PlayerState.data(p)!=s||Combat.fullSet(p)==null||LightningKit.active(p))return false;
            p.addPotionEffect(new PotionEffect(EFFECTS[kind],400,0,false,true));
            if(!changed(p,EFFECTS[kind],previous))return Adventures.tell(p,"The form effect was refused; chakra kept.");
            payment.commit(); // effect exists before gesture, light or chat callbacks
            try{Adventures.gesture(p);}catch(RuntimeException|LinkageError cosmeticFailure){}if(kind==2)p.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION,400,0));
            p.sendMessage(new net.minecraft.util.text.TextComponentString(KEYS[kind]+" form: 20 seconds. Right-click a form token again to end early."));return true;
        }finally{
            if(payment.pending()&&changed(p,EFFECTS[kind],previous))payment.commit();
            if(payment.pending()&&PlayerState.data(p)==s){if(KEYS[kind].equals(s.getString("form"))&&s.getLong("formEnd")==now+400){s.removeTag("form");s.removeTag("formEnd");s.removeTag("formDrain");}if(s.getLong("formReady")==now+2400)s.setLong("formReady",oldReady);if(s.getLong("lastCombat")==now)s.setLong("lastCombat",oldCombat);}
            STARTING.remove(p.getUniqueID());payment.close();
        }
    }
    private static boolean changed(EntityPlayerMP p,Potion effect,PotionEffect before){PotionEffect after=p.getActivePotionEffect(effect);return after!=null&&(before==null||after.getAmplifier()>before.getAmplifier()||after.getDuration()>before.getDuration());}
    public static int active(EntityPlayerMP p){
        String key=PlayerState.data(p).getString("form");for(int i=0;i<4;i++)if(KEYS[i].equals(key))return i;return -1;
    }
    public static void tick(EntityPlayerMP p){
        int kind=active(p);if(kind<0)return;NBTTagCompound s=PlayerState.data(p);long now=Adventures.now(p);
        if(!p.isEntityAlive()||now>=s.getLong("formEnd")||Combat.fullSet(p)==null||!p.isPotionActive(EFFECTS[kind])){end(p,true);return;}
        if(!s.hasKey("formDrain",4)||s.getLong("formDrain")<0||s.getLong("formDrain")>now+20)s.setLong("formDrain",now);
        long due=s.getLong("formDrain");if(now<due||MoveCost.busy(p))return;
        int drains=(int)Math.min(20,1+(now-due)/20);
        MoveCost.Ticket upkeep=MoveCost.reserve(p,drains*3);if(upkeep==null){if(!MoveCost.busy(p))end(p,true);return;}
        try{
            if(PlayerState.data(p)!=s||active(p)!=kind||!p.isPotionActive(EFFECTS[kind]))return;
            if(!upkeep.commit()){end(p,true);return;}
            s.setLong("formDrain",due+drains*20L);s.setLong("lastCombat",now);
        }finally{upkeep.close();}
        ((net.minecraft.world.WorldServer)p.world).spawnParticle(net.minecraft.util.EnumParticleTypes.SPELL_MOB,p.posX,p.posY+1,p.posZ,8,.35,.7,.35,.01);
    }
    public static void end(EntityPlayerMP p,boolean fatigue){
        if(active(p)<0)return;
        for(Potion effect:EFFECTS)if(effect!=null)p.removePotionEffect(effect);
        PlayerState.data(p).removeTag("form");PlayerState.data(p).removeTag("formEnd");PlayerState.data(p).removeTag("formDrain");
        HudNetwork.dirty(p);
        if(fatigue&&p.isEntityAlive())p.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,120,0));
    }
}

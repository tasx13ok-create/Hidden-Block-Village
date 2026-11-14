package village.vale;

import net.minecraft.entity.SharedMonsterAttributes;
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
        if(kind<0||kind>=4||!p.isEntityAlive()||p.isSpectator())return false;
        NBTTagCompound s=PlayerState.data(p);long now=Adventures.now(p);
        if(active(p)>=0){end(p,true);return true;}
        if(LightningKit.active(p))return Adventures.tell(p,"Release Lightning Chakra Armor before starting another form.");
        if(Combat.fullSet(p)==null)return Adventures.tell(p,"Equip a matching issued set before transforming.");
        if(PlayerState.level(p)<LEVELS[kind])return Adventures.tell(p,"This form requires level "+LEVELS[kind]+".");
        if(now<s.getLong("formReady"))return Adventures.tell(p,"Form cooling down: "+Rules.cooldownSeconds(now,s.getLong("formReady"))+"s.");
        if(PlayerState.resolve(p)<60)return Adventures.tell(p,"A form needs 60 resolve and three resolve each second.");
        PlayerState.setResolve(p,PlayerState.resolve(p)-60);s.setString("form",KEYS[kind]);s.setLong("formEnd",now+400);s.setLong("formReady",now+2400);s.setLong("lastCombat",now);
        p.addPotionEffect(new PotionEffect(EFFECTS[kind],400,0,false,true));Adventures.gesture(p);
        if(kind==2)p.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION,400,0));
        p.sendMessage(new net.minecraft.util.text.TextComponentString(KEYS[kind]+" form: 20 seconds. Right-click a form token again to end early."));return true;
    }
    public static int active(EntityPlayerMP p){
        String key=PlayerState.data(p).getString("form");for(int i=0;i<4;i++)if(KEYS[i].equals(key))return i;return -1;
    }
    public static void tick(EntityPlayerMP p){
        int kind=active(p);if(kind<0)return;NBTTagCompound s=PlayerState.data(p);
        if(!p.isEntityAlive()||Adventures.now(p)>=s.getLong("formEnd")||Combat.fullSet(p)==null||!p.isPotionActive(EFFECTS[kind])||PlayerState.resolve(p)<3){end(p,true);return;}
        PlayerState.setResolve(p,PlayerState.resolve(p)-3);s.setLong("lastCombat",Adventures.now(p));
        ((net.minecraft.world.WorldServer)p.world).spawnParticle(net.minecraft.util.EnumParticleTypes.SPELL_MOB,p.posX,p.posY+1,p.posZ,8,.35,.7,.35,.01);
    }
    public static void end(EntityPlayerMP p,boolean fatigue){
        if(active(p)<0)return;
        for(Potion effect:EFFECTS)if(effect!=null)p.removePotionEffect(effect);
        PlayerState.data(p).removeTag("form");PlayerState.data(p).removeTag("formEnd");
        HudNetwork.dirty(p);
        if(fatigue&&p.isEntityAlive())p.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,120,0));
    }
}

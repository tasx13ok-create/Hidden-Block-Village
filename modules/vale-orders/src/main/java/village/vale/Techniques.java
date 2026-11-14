package village.vale;

import java.util.ArrayList;
import village.vale.bridge.MoveCost;
import net.minecraft.entity.player.EntityPlayerMP;
import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;

/** Six weapon roles using the same two mouse buttons as ordinary equipment. */
public final class Techniques {
    private Techniques() {}
    public static String name(String kind) {
        switch (kind) {
            case "saber": return "Patient Guard";
            case "spear": return "Long Thrust";
            case "dagger": return "Sidestep";
            case "glaive": return "Fan Cut";
            case "staff": return "Centering Breath";
            default: return "Crushing Strike";
        }
    }
    public static boolean activate(EntityPlayer player,Gear.WardBlade blade) {
        if(!(player instanceof EntityPlayerMP)||player.world.isRemote||!player.isEntityAlive()||player.isSpectator())return false;
        EntityPlayerMP owner=(EntityPlayerMP)player;
        net.minecraft.item.ItemStack held=player.getHeldItemMainhand();
        if(Combat.fullSet(player)!=blade.order()||!RewardLedger.get(player).valid(player,held))return tell(player,"Equip your issued matching armor and weapon.");
        NBTTagCompound state=PlayerState.data(player);long time=Combat.now(player);
        if(time<state.getLong("nextTechnique"))return tell(player,"Weapon technique is cooling down.");
        if(blade.kind.equals("staff")&&player.getHealth()>=player.getMaxHealth()&&!player.isPotionActive(MobEffects.SLOWNESS)&&!player.isPotionActive(MobEffects.WEAKNESS))return tell(player,"Already centered: health is full and no slowing or weakness.");
        MoveCost.Ticket payment=MoveCost.reserve(owner,20);if(payment==null)return tell(player,"Need 20 chakra units from the active resource provider.");
        float healthBefore=player.getHealth();boolean slowBefore=player.isPotionActive(MobEffects.SLOWNESS),weakBefore=player.isPotionActive(MobEffects.WEAKNESS);
        long oldReady=state.getLong("nextTechnique"),oldCombat=state.getLong("lastCombat");state.setLong("nextTechnique",time+120);state.setLong("lastCombat",time);
        try {
            if(!payment.valid()||PlayerState.data(player)!=state||player.getHeldItemMainhand()!=held||Combat.fullSet(player)!=blade.order()||!RewardLedger.get(player).valid(player,held))return false;
            switch(blade.kind){
                case "saber":state.setLong("guardUntil",time+50);payment.commit();break;
                case "dagger":Vec3d look=player.getLookVec();player.addVelocity(look.z*.65,.12,-look.x*.65);player.velocityChanged=true;payment.commit();player.addPotionEffect(new PotionEffect(MobEffects.SPEED,40,0));break;
                case "staff":{
                    float before=player.getHealth();player.heal(2);if(player.getHealth()>before)payment.commit();
                    boolean slow=player.isPotionActive(MobEffects.SLOWNESS),weak=player.isPotionActive(MobEffects.WEAKNESS);
                    player.removePotionEffect(MobEffects.SLOWNESS);if(slow&&!player.isPotionActive(MobEffects.SLOWNESS))payment.commit();
                    player.removePotionEffect(MobEffects.WEAKNESS);if(weak&&!player.isPotionActive(MobEffects.WEAKNESS))payment.commit();break;
                }
                case "glaive":{
                    List<EntityLivingBase> candidates=new ArrayList<>(player.world.getEntitiesWithinAABB(EntityLivingBase.class,player.getEntityBoundingBox().grow(3.75)));
                    candidates.sort(Comparator.comparingDouble(player::getDistanceSq));int hits=0;
                    for(EntityLivingBase target:candidates){if(hits>=3)break;if(!target.isEntityAlive()||!Targets.hostile(target)||player.isOnSameTeam(target)||player.getDistanceSq(target)>3.75*3.75)continue;Vec3d delta=target.getPositionVector().subtract(player.getPositionVector()).normalize();if(player.getLookVec().dotProduct(delta)<.15||!player.canEntityBeSeen(target))continue;if(Combat.impact(target,DamageSource.causePlayerDamage(player),6)){hits++;payment.commit();}}
                    if(hits==0)return tell(player,"No permitted enemies were hit in front of you.");break;
                }
                default:EntityLivingBase target=Combat.aimedTarget(player,blade.kind.equals("spear")?6:4);if(target==null||!Combat.impact(target,DamageSource.causePlayerDamage(player),blade.kind.equals("spear")?8:10))return tell(player,"Aim at an unblocked permitted enemy in weapon range.");payment.commit();break;
            }
            if(payment.pending())return tell(player,"The weapon effect was refused.");
            try{player.sendStatusMessage(new TextComponentString(name(blade.kind)+" activated."),true);}catch(RuntimeException|LinkageError cosmeticFailure){}return true;
        } finally {
            if(payment.pending()&&blade.kind.equals("staff")&&(player.getHealth()>healthBefore||slowBefore&&!player.isPotionActive(MobEffects.SLOWNESS)||weakBefore&&!player.isPotionActive(MobEffects.WEAKNESS)))payment.commit();
            if(payment.pending()&&PlayerState.data(player)==state){if(state.getLong("nextTechnique")==time+120)state.setLong("nextTechnique",oldReady);if(state.getLong("lastCombat")==time)state.setLong("lastCombat",oldCombat);}
            payment.close();
        }
    }
    private static boolean tell(EntityPlayer player, String text) { player.sendStatusMessage(new TextComponentString(text), true); return false; }
}

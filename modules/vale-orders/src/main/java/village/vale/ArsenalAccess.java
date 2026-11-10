package village.vale;

import java.util.Collections;
import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

/** Small public gateway to the existing observed-damage/protection authority.
 * New original arsenal code does not bypass cancellation, PvP, teams or NPCs.
 */
public final class ArsenalAccess {
    private ArsenalAccess() {}
    public static boolean allowed(EntityPlayerMP owner,EntityLivingBase target) {
        return owner!=null&&target!=null&&owner.isEntityAlive()&&!owner.isSpectator()
            &&owner.world==target.world&&!owner.world.isRemote&&OwnedSkills.allowed(owner,target);
    }
    public static boolean impact(EntityPlayerMP owner,EntityLivingBase target,DamageSource source,float amount) {
        if(!allowed(owner,target)||source==null||source.getTrueSource()!=owner||!Float.isFinite(amount)||amount<=0||amount>32)return false;
        return Combat.impact(target,source,amount);
    }
    public static boolean hit(EntityPlayerMP owner,EntityLivingBase target,float amount) {
        return owner!=null&&impact(owner,target,DamageSource.causePlayerDamage(owner),amount);
    }
    public static EntityLivingBase aimedTarget(EntityPlayerMP owner,double range) {
        if(owner==null||owner.world.isRemote||!owner.isEntityAlive()||owner.isSpectator()||!Double.isFinite(range)||range<=0||range>24)return null;
        EntityLivingBase target=Combat.aimedTarget(owner,range);return allowed(owner,target)?target:null;
    }
    public static List<EntityLivingBase> targets(EntityPlayerMP owner,double radius,boolean frontal,int limit) {
        if(owner==null||owner.world.isRemote||!owner.isEntityAlive()||owner.isSpectator()||!Double.isFinite(radius)||radius<=0||radius>16||limit<1||limit>12)return Collections.emptyList();
        return Arts.near(owner,radius,frontal,limit);
    }
    public static boolean issued(EntityPlayerMP owner,ItemStack stack) {
        return owner!=null&&stack!=null&&!stack.isEmpty()&&!owner.world.isRemote&&RewardLedger.get(owner).valid(owner,stack);
    }
    public static Order fullSet(EntityPlayerMP owner) { return owner==null?null:Combat.fullSet(owner); }
    public static long now(EntityPlayerMP owner) { return Adventures.now(owner); }
    public static void save(EntityPlayerMP owner) { Adventures.save(owner); }
    public static void gesture(EntityPlayerMP owner) { Adventures.gesture(owner); }
}

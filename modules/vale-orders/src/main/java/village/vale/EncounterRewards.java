package village.vale;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
/** Narrow server-side original-encounter integration. No packet or arbitrary reward key enters here. */
public final class EncounterRewards {
    private EncounterRewards(){}
    public static boolean boss(EntityLivingBase target){return new ResourceLocation("valeencounters","boss").equals(EntityList.getKey(target));}
    public static boolean mob(EntityLivingBase target){return new ResourceLocation("valeencounters","mob").equals(EntityList.getKey(target));}
    public static int tier(EntityLivingBase target){return boss(target)?Math.max(0,Math.min(3,target.getEntityData().getInteger("valeEncounterTier")-1)):-1;}
    public static boolean award(WorldServer world,EntityLivingBase boss,String profile,int tier,Map<UUID,Float> damage,Map<UUID,Long> lastHit){
        if(world==null||boss==null||boss.world!=world||world.isRemote||!boss(boss)||boss.getHealth()>0||!Float.isFinite(boss.getMaxHealth())||boss.getMaxHealth()<=0||profile==null||!profile.matches("[a-z][a-z0-9_]{0,63}")||damage==null||lastHit==null)return false;
        WorldServer home=world.getMinecraftServer().getWorld(0);if(home==null)return false;
        RewardLedger ledger=(RewardLedger)home.getMapStorage().getOrLoadData(RewardLedger.class,"vale_orders_rewards");
        if(ledger==null){ledger=new RewardLedger();home.getMapStorage().setData("vale_orders_rewards",ledger);}
        List<UUID> owners=new ArrayList<>(damage.keySet());owners.removeAll(Collections.singleton(null));Collections.sort(owners);int issued=0;
        for(UUID owner:owners){
            Float dealt=damage.get(owner);Long at=lastHit.get(owner);long now=world.getTotalWorldTime();
            if(dealt==null||!Float.isFinite(dealt)||dealt<boss.getMaxHealth()*.05F||at==null||at>now||now-at>1200)continue;
            String key=reward(boss.getUniqueID(),owner,profile,tier);
            if(!Gear.ITEMS.containsKey(key))return false;
            ledger.issue(home,owner,key,1,"encounter-v1/"+boss.getUniqueID()+"/"+owner);
            if(++issued>=128)break;
        }
        return true;
    }
    public static String reward(UUID boss,UUID owner,String profile,int tier){
        int hash=Objects.hash(boss,owner,profile);String[] supplies={"study_note","practice_note","travel_ration","herb_broth"};
        if(tier<=1)return supplies[Math.floorMod(hash,supplies.length)];
        if(tier==2){String[] weapons={"ward_fan","returning_sickle","waymark_knife","siphon_hook"};return weapons[Math.floorMod(hash,weapons.length)];}
        village.vale.armor.ArmorProfile[] profiles=village.vale.armor.ArmorProfile.values();String[] parts={"helmet","chestplate","leggings","boots"};
        return profiles[Math.floorMod(hash,profiles.length)].key+"_"+parts[Math.floorMod(hash/13,parts.length)];
    }
}

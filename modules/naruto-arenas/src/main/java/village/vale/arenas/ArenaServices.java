package village.vale.arenas;

import com.google.gson.*;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import village.vale.bridge.MoveCost;
import village.vale.bridge.ChakraLink;

/** Original camp actors, separate from imported CustomNPCs and Residents. */
@Mod.EventBusSubscriber(modid=ArenaDirector.ID)
public final class ArenaServices {
 private static final String[] ROLES={"keeper","aid","stores"};
 private ArenaServices(){}
 static JsonObject records(){
  ArenaDirector.Store s=ArenaDirector.store();
  if(s.readOnly)throw new IllegalStateException("Future state is read-only");
  if(!s.data.has("services")){s.data.add("services",new JsonObject());s.dirty();}
  return s.data.getAsJsonObject("services");
 }
 static boolean ensure(ArenaDirector.Definition d){
  if(d.services==null||d.services.length!=3)return false;
  WorldServer w=ArenaDirector.server.getWorld(0);boolean complete=true;
  for(int i=0;i<3;i++){
   int[] at=d.services[i];BlockPos pos=new BlockPos(at[0],at[1],at[2]);
   if(!d.contains(pos)||!w.isBlockLoaded(pos)||!ArenaDirector.safe(w,at)){complete=false;continue;}
   String key=d.id+"/"+ROLES[i];JsonObject saved=records();
   if(saved.has(key)){
    JsonObject row=saved.getAsJsonObject(key);
    if(!d.template_sha256.equals(ArenaDirector.text(row,"template"))){complete=false;continue;}
    Entity existing=w.getEntityFromUuid(UUID.fromString(ArenaDirector.text(row,"uuid")));
    if(existing!=null){
     if(!(existing instanceof EntityVillager)||!key.equals(existing.getEntityData().getString("HbvArenaService"))||!d.template_sha256.equals(existing.getEntityData().getString("HbvArenaTemplate")))complete=false;
     continue;
    }
    // Absence from the loaded UUID index is not proof of death. A pushed or
    // unloaded saved actor must never cause a second role actor to be created.
    complete=false;continue;
   }
   // A saved actor can be outside its nominal cell after native movement. Scan
   // only this loaded camp, and never adopt an untagged or imported actor.
   List<EntityVillager> nearby=w.getEntitiesWithinAABB(EntityVillager.class,new AxisAlignedBB(pos).grow(24));
   EntityVillager actor=null;boolean ambiguous=false;
   for(EntityVillager candidate:nearby)if(key.equals(candidate.getEntityData().getString("HbvArenaService"))){
    if(actor!=null||!d.template_sha256.equals(candidate.getEntityData().getString("HbvArenaTemplate"))){ambiguous=true;break;}actor=candidate;
   }
   if(ambiguous){complete=false;continue;}
   if(actor==null){
    actor=new EntityVillager(w);actor.setPosition(at[0]+.5,at[1],at[2]+.5);
    actor.setNoAI(true);actor.enablePersistence();actor.setEntityInvulnerable(true);
    actor.setCustomNameTag(d.title+" / "+ROLES[i]);actor.setAlwaysRenderNameTag(true);
    actor.getEntityData().setString("HbvArenaService",key);actor.getEntityData().setString("HbvArenaTemplate",d.template_sha256);
    if(!w.spawnEntity(actor)){complete=false;continue;}
   }
   JsonObject row=new JsonObject();row.addProperty("uuid",actor.getUniqueID().toString());row.addProperty("template",d.template_sha256);
   saved.add(key,row);ArenaDirector.store().dirty();
  }
  return complete;
 }
 @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
  if(e.phase!=TickEvent.Phase.END||ArenaDirector.server==null||ArenaDirector.ticks%100!=0||ArenaDirector.store().readOnly)return;
  WorldServer w=ArenaDirector.server.getWorld(0);
  for(ArenaDirector.Definition d:ArenaDirector.DEFINITIONS){
   boolean visited=false;
   for(EntityPlayerMP p:w.playerEntities.toArray(new EntityPlayerMP[0])){
    if(!ArenaDirector.eligible(p,true)||!d.contains(p.getPosition()))continue;
    if(p.getDistanceSq(new BlockPos(d.entry[0],d.entry[1],d.entry[2]))<10000)visited=true;
    if(d.services!=null)for(int[] service:d.services)if(p.getDistanceSq(new BlockPos(service[0],service[1],service[2]))<1024)visited=true;
    if(visited)break;
   }
   if(visited)ensure(d);
  }
 }
 @SubscribeEvent(priority=EventPriority.LOW) public static void interact(PlayerInteractEvent.EntityInteract e){
  if(ArenaDirector.server==null||e.getWorld().isRemote||e.getHand()!=EnumHand.MAIN_HAND||!(e.getEntityPlayer() instanceof EntityPlayerMP)||!(e.getTarget() instanceof EntityVillager))return;
  EntityPlayerMP p=(EntityPlayerMP)e.getEntityPlayer();Entity actor=e.getTarget();String key=actor.getEntityData().getString("HbvArenaService");
  if(key.isEmpty()||!ArenaDirector.eligible(p,true)||p.getDistanceSq(actor)>36)return;
  for(ArenaDirector.Definition d:ArenaDirector.DEFINITIONS){
   if(!key.startsWith(d.id+"/")||!d.template_sha256.equals(actor.getEntityData().getString("HbvArenaTemplate")))continue;
   JsonObject row=records().has(key)?records().getAsJsonObject(key):null;
   if(row==null||!actor.getUniqueID().toString().equals(ArenaDirector.text(row,"uuid")))return;
   e.setCanceled(true);
   JsonObject run=ArenaDirector.run(d);String state=run==null?"AVAILABLE":ArenaDirector.text(run,"state");
   if(key.endsWith("/keeper")){
    ArenaDirector.tell(p,state.equals("REWARD_PENDING")||state.equals("REWARDED")?"The stadium has answered your victory. Claim your chosen reward at the balcony, then take the marked return.":
     "Practice two gold seals with /narutoarena intro "+d.id+". Inspect the boss in unrated preview; normal runs use the saved introduction. Ordinary weapons and physical seals always offer a route.");
   }else if(key.endsWith("/stores")){
    ArenaDirector.tell(p,"The workshop and stadium service rooms have ordinary usable storage chests. Prepare supplies before the court. Your inventory stays yours; preview does not grant earned economy.");
   }else{
    if(!ArenaDirector.eligible(p,false)){ArenaDirector.tell(p,"Camp treatment is available in Survival mode.");return;}
    boolean fighting=run!=null&&run.getAsJsonObject("members").has(p.getUniqueID().toString())&&(state.startsWith("PHASE")||state.startsWith("SEALING"));
    if(fighting){ArenaDirector.tell(p,"Return after leaving the active fight for camp treatment. The short return remains marked.");return;}
    NBTTagCompound data=RewardOutbox.receipts(p);long now=p.world.getTotalWorldTime();String timer="campAid/"+d.id;
    if(now<data.getLong(timer)){ArenaDirector.tell(p,"Rest briefly before the next treatment.");return;}
    float before=p.getHealth();p.heal(6F);double chakra=0;
    ChakraLink.View view=MoveCost.view(p);
    // Camp recovery uses the one existing native pool. Installed but unready
    // accounts do not get a new fallback pool, XP, nature, maximum or unlock.
    if(view.ready()&&"narutomod".equals(view.provider)&&Double.isFinite(view.amount)&&Double.isFinite(view.maximum)){
     double restored=MoveCost.restore(p,40);if(Double.isFinite(restored)&&restored>0)chakra=restored;
    }
    // A legitimate timed camp benefit for the optional submerged passage.
    // Existing effects are never combined, downgraded, replaced or removed.
    // This is not a phase-owned effect: it expires normally after two minutes.
    boolean breathing=false;
    if(d.water&&!p.isPotionActive(MobEffects.WATER_BREATHING)){
     p.addPotionEffect(new PotionEffect(MobEffects.WATER_BREATHING,2400,0));
     breathing=p.isPotionActive(MobEffects.WATER_BREATHING);
    }
    if(p.getHealth()>before||chakra>0||breathing){data.setLong(timer,now+300);RewardOutbox.flush(p);ArenaDirector.tell(p,String.format(java.util.Locale.ROOT,"Camp treatment: +%.1f health, +%.1f chakra.%s",Math.max(0,p.getHealth()-before),chakra,breathing?" Two minutes of water breathing will help with the submerged passage.":""));}
    else ArenaDirector.tell(p,"You are ready. Use the preparation veranda and marked stairs to reach the court.");
   }
   return;
  }
 }
}

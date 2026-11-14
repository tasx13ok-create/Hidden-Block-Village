package village.vale.arenas;

import com.google.gson.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.world.*;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.registry.*;

/** Actual provider class/leader/UUID cleanup controls. Synthetic actor fixture,
 * not an earned encounter or a claim that every native summoning path was tested. */
public final class ActorNativeChecks {
 private ActorNativeChecks(){}
 private static void check(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
 static Entity spawn(WorldServer w,ArenaDirector.Definition d,String name,int offset)throws Exception{
  EntityEntry entry=null;for(EntityEntry e:ForgeRegistries.ENTITIES.getValuesCollection())if(e.getEntityClass().getName().equals(name)){entry=e;break;}check(entry!=null,"Native fixture class not registered: "+name);Entity e=entry.newInstance(w);e.setPosition(d.boss_anchor[0]+offset+.5,180,d.boss_anchor[2]+.5);NativeProbe.prewarm(w,e.getPosition());check(w.spawnEntity(e),"Actual native actor spawn rejected");return e;
 }
 static void leader(Entity e,Entity owner)throws Exception{e.getClass().getMethod("setLeader",EntityLivingBase.class).invoke(e,owner);}
 public static JsonObject run(ArenaDirector.Definition d){JsonObject result=new JsonObject();JsonArray errors=new JsonArray();result.add("failures",errors);result.addProperty("scope","ACTUAL_NATIVE_ACTORS_PUBLIC_LEADER_UUID_AND_BASELINE_NEGATIVE_FIXTURE; no earned entitlement");List<Entity> created=new ArrayList<>();
  try{check(Boolean.getBoolean("hbv.arenas.nativeProbe"),"Private actor guard");WorldServer w=ArenaDirector.server.getWorld(0);String ay="net.mcreator.kabutoaddon.entity.EntityAy$EntityCustom",darui="net.mcreator.kabutoaddon.entity.EntityDarui$EntityCustom",c="net.mcreator.kabutoaddon.entity.EntityC$EntityCustom";
   Entity boss=spawn(w,d,ay,0);created.add(boss);Entity other=spawn(w,d,ay,3);created.add(other);Entity baseline=spawn(w,d,darui,6);created.add(baseline);Entity owned=spawn(w,d,darui,9);created.add(owned);Entity healer=spawn(w,d,c,12);created.add(healer);Entity unowned=spawn(w,d,darui,15);created.add(unowned);
   JsonObject run=new JsonObject();String id=UUID.randomUUID().toString();run.addProperty("run",id);run.addProperty("boss_uuid",boss.getUniqueID().toString());boss.getEntityData().setString("HbvArenaRun",id);JsonArray original=new JsonArray();original.add(baseline.getUniqueID().toString());run.add("baseline_actor_uuids",original);
   leader(baseline,boss);leader(owned,boss);leader(healer,boss);ArenaActors.observe(d,run,boss);JsonObject records=run.getAsJsonObject("owned_native_allies");check(records.size()==2&&records.has(owned.getUniqueID().toString())&&records.has(healer.getUniqueID().toString()),"Public leader did not authenticate exact two new native allies");check(!records.has(baseline.getUniqueID().toString())&&!records.has(unowned.getUniqueID().toString()),"Baseline/adjoining unowned actor was adopted");result.addProperty("actual_new_public_leader_darui_and_c_only",true);result.addProperty("baseline_adopted_by_native_leader_preserved",true);result.addProperty("same_class_without_leader_preserved",true);
   JsonObject only=new JsonObject();only.addProperty("run",id);only.addProperty("boss_uuid",boss.getUniqueID().toString());only.add("baseline_actor_uuids",original);JsonObject one=new JsonObject();one.add(owned.getUniqueID().toString(),new JsonParser().parse(records.get(owned.getUniqueID().toString()).toString()));only.add("owned_native_allies",one);
   String template=owned.getEntityData().getString("HbvArenaAllyTemplate");owned.getEntityData().setString("HbvArenaAllyTemplate","changed");check(!ArenaActors.cleanup(d,only)&&!owned.isDead,"Changed template was removed");owned.getEntityData().setString("HbvArenaAllyTemplate",template);result.addProperty("changed_template_rejected_preserving_actor",true);
   leader(owned,other);check(!ArenaActors.cleanup(d,only)&&!owned.isDead,"Changed native public leader was removed");leader(owned,boss);result.addProperty("changed_public_leader_rejected_preserving_actor",true);
   check(ArenaActors.cleanup(d,run)&&owned.isDead&&healer.isDead&&!baseline.isDead&&!unowned.isDead&&!other.isDead,"Exact cleanup changed unrelated native actor");result.addProperty("exact_recorded_uuids_removed_unrelated_preserved",true);
  }catch(Throwable failure){errors.add(failure.toString());failure.printStackTrace();}finally{for(Entity e:created)e.setDead();}result.addProperty("passed",errors.size()==0);return result;
 }
}

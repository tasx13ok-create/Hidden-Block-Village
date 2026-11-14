package village.vale.arenas;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import village.vale.bridge.MoveCost;
import village.vale.bridge.ChakraLink;

/** Two physical seals teach the arena counter and issue one bounded native awakening receipt. */
final class ArenaIntro {
 private ArenaIntro(){}
 static NBTTagCompound records(EntityPlayerMP p){NBTTagCompound data=p.getEntityData();if(!data.hasKey("PlayerPersisted",10))data.setTag("PlayerPersisted",new NBTTagCompound());NBTTagCompound persisted=data.getCompoundTag("PlayerPersisted");if(!persisted.hasKey("HbvArenaIntro",10))persisted.setTag("HbvArenaIntro",new NBTTagCompound());return persisted.getCompoundTag("HbvArenaIntro");}
 static void start(EntityPlayerMP p,ArenaDirector.Definition d){if(!ArenaDirector.eligible(p,false))throw new IllegalStateException("Survival logged-in practice required");if(d.nodes==null||d.nodes.length<2)throw new IllegalStateException("Two physical seals are not configured");NBTTagCompound all=records(p);if(!all.hasKey(d.id,10)){NBTTagCompound row=new NBTTagCompound();row.setString("template",d.template_sha256);row.setString("state","PRACTICE");all.setTag(d.id,row);RewardOutbox.flush(p);}ArenaDirector.move(p,d.entry);ArenaDirector.tell(p,"Practice: reach and use two gold seals. This counter works with ordinary Minecraft movement. Awakening is a bounded once-only native grant; no nature is replaced.");}
 static boolean complete(EntityPlayerMP p,ArenaDirector.Definition d){NBTTagCompound all=records(p);return all.hasKey(d.id,10)&&all.getCompoundTag(d.id).getString("state").equals("COMPLETE");}
 static boolean contact(EntityPlayerMP p,ArenaDirector.Definition d,BlockPos pos){
  if(!ArenaDirector.eligible(p,false)||!records(p).hasKey(d.id,10))return false;NBTTagCompound row=records(p).getCompoundTag(d.id);if(row.getString("state").equals("COMPLETE"))return false;
  int node=-1;for(int i=0;i<d.nodes.length;i++)if(pos.equals(new BlockPos(d.nodes[i][0],d.nodes[i][1],d.nodes[i][2])))node=i;if(node<0||p.getDistanceSq(pos)>36)return false;
  row.setBoolean("node"+node,true);int count=0;for(int i=0;i<d.nodes.length;i++)if(row.getBoolean("node"+i))count++;
  if(count<2){RewardOutbox.flush(p);ArenaDirector.tell(p,"One seal complete. Reach a different gold seal to finish practice.");return true;}
  row.setString("state","GRANT_PENDING");RewardOutbox.flush(p);
  try{Class<?> tracker=Class.forName("net.narutomod.PlayerTracker");boolean ninja=(Boolean)tracker.getMethod("isNinja",net.minecraft.entity.player.EntityPlayer.class).invoke(null,p);
   if(!ninja&&!records(p).getBoolean("awakeningGranted")){row.setBoolean("fresh_awakening",true);RewardOutbox.flush(p);tracker.getMethod("addBattleXp",net.minecraft.entity.player.EntityPlayer.class,double.class).invoke(null,p,2000D);ninja=(Boolean)tracker.getMethod("isNinja",net.minecraft.entity.player.EntityPlayer.class).invoke(null,p);if(!ninja)throw new IllegalStateException("Native awakening was not accepted");records(p).setBoolean("awakeningGranted",true);}
   if(!ninja)throw new IllegalStateException("The saved native awakening receipt and current account disagree");
   // The bounded public native XP grant initializes the installed provider's
   // own capacity. A zero/unready pool is retained as a failed native gate;
   // the arena never invents or replaces a native maximum.
   ChakraLink.View view=MoveCost.view(p);if(!view.ready()||!"narutomod".equals(view.provider)||!Double.isFinite(view.maximum)||view.maximum<=0)throw new IllegalStateException("Native chakra capacity is not ready; practice receipt retained");
   if(!records(p).getBoolean("seedGranted")){double actual=MoveCost.restore(p,40);if(!Double.isFinite(actual)||actual<0)throw new IllegalStateException("Canonical initial chakra gain failed");records(p).setDouble("initial_seed_actual",actual);records(p).setBoolean("seedGranted",true);}
   row.setString("state","COMPLETE");RewardOutbox.flush(p);ArenaDirector.tell(p,"Practice complete. Native chakra account verified; your existing nature is preserved. Preview bosses carry no earned rewards.");return true;
  }catch(ReflectiveOperationException ex){throw new IllegalStateException("Exact public native awakening API unavailable; pending receipt retained",ex);}
 }
}

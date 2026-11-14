package village.vale.arenas;

import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Original environmental adaptations; these do not claim to call native named jutsu. */
final class EnvironmentalBeats {
 private EnvironmentalBeats(){}
 static int kind(ArenaDirector.Definition d){return d.id.contains("forest")?0:d.id.contains("cloud")?1:d.id.contains("sand")?2:d.id.contains("mist")?3:4;}
 static String name(int kind,int phase){String[][] names={{"Root lanes","Dragon commitment","Sage bloom"},{"Lariat lanes","Grounded crown","Guillotine commitment"},{"Sand guard","Burial lanes","Desert convergence"},{"Shark lanes","Water prison","Chakra pressure"},{"Fire and fan","Seal guardian pressure","Meteor commitment"}};return names[kind][phase-1];}
 static boolean volume(int kind,int phase,double x,double z,int cycle){
  if(Math.abs(x)>42||Math.abs(z)>42)return false;
  switch(kind){
   case 0:return phase==1?(Math.abs(x)<3||Math.abs(x-20)<3||Math.abs(x+20)<3):phase==2?Math.abs(x-22*Math.sin(z/13))<4:((x<0)==((cycle&1)==0))&&Math.abs(z)<34;
   case 1:return phase==1?Math.abs(x)<5:phase==2?((x<0)==((cycle&1)==0))&&Math.abs(z)<32:(x*x+z*z<144);
   case 2:return phase==1?(x>0&&Math.abs(z)<10):phase==2?Math.abs(z-18)<4||Math.abs(z+18)<4:Math.abs(x-z)<6||Math.abs(x+z)<6;
   case 3:return phase==1?Math.abs(x-25*Math.sin(z/17))<4:phase==2?Math.abs(x)<15&&Math.abs(z)<18:Math.abs(x)<7&&z>0&&z<24;
   default:return phase==1?z<0&&Math.abs(x)<32:phase==2?((Math.abs(x-22)<5&&z>-20)||(Math.abs(x+22)<5&&Math.abs(z+17)<17)):((x-20)*(x-20)+z*z<100||(x+20)*(x+20)+z*z<100);
  }
 }
 static void tick(ArenaDirector.Definition d,JsonObject run,int phase,int clock){
  WorldServer w=ArenaDirector.server.getWorld(0);int kind=kind(d),cycle=ArenaDirector.number(run,"cycles"),cx=d.boss_anchor[0],cz=d.boss_anchor[2];
  if(clock==0){if(!ArenaDirector.restore(d,run)){ArenaDirector.state(run,"SAFE_CLOSED");return;}ArenaDirector.broadcast(run,name(kind,phase)+": marked lanes commit after30ticks. Side galleries remain safe; use a gold seal to interrupt or guard then punish recovery.");}
  if(clock==1){
   for(int x=-40;x<=40;x+=4)for(int z=-40;z<=40;z+=4)if(volume(kind,phase,x,z,cycle)){BlockPos p=new BlockPos(cx+x,d.floor-1,cz+z);if(!w.isBlockLoaded(p))throw new IllegalStateException("Hazard target chunk is not prewarmed");if(d.contains(p)&&w.getBlockState(p).getMaterial().isSolid())ArenaDirector.journal(w,run,p,Blocks.SEA_LANTERN.getDefaultState());}
   // Deliberate original silhouettes, with no falling entities or uncontrolled explosions.
   if(kind==0&&phase==2)BeatSculptures.plan(w,d,run,BeatSculptures.dragon(cx,d.floor,cz));
   if(kind==2&&phase==2)for(int side:new int[]{-18,18})for(int x=-28;x<=28;x+=4)airOnly(w,d,run,new BlockPos(cx+x,d.floor,cz+side),Blocks.SANDSTONE.getDefaultState());
   if(kind==4&&phase==2)BeatSculptures.plan(w,d,run,BeatSculptures.guardian(cx,d.floor,cz));
   if(kind==4&&phase==3)for(int side:new int[]{-20,20})for(int x=-5;x<=5;x++)for(int y=-5;y<=5;y++)for(int z=-5;z<=5;z++)if(x*x+y*y+z*z<30)airOnly(w,d,run,new BlockPos(cx+side+x,d.floor+35+y,cz+z),Blocks.NETHERRACK.getDefaultState());
   if(kind==3&&phase==2)WaterPrison.plan(w,d,run);
   ArenaDirector.applyJournal(run);
  }
  // Native absorption remains authoritative. Chakra-pressure adds no debit.
  if(kind==3&&phase==2){if(!WaterPrison.contained(w,d)){ArenaDirector.restore(d,run);ArenaDirector.state(run,"SAFE_CLOSED");ArenaDirector.broadcast(run,"Water boundary changed. The owned water state was withdrawn; this candidate requires review.");return;}for(String uid:ArenaDirector.keys(run.getAsJsonObject("members"))){EntityPlayerMP p=ArenaDirector.server.getPlayerList().getPlayerByUUID(UUID.fromString(uid));if(p!=null&&d.contains(p.getPosition())&&p.getAir()<300)p.setAir(300);}}
  if(clock==30){for(String uid:ArenaDirector.keys(run.getAsJsonObject("members"))){EntityPlayerMP p=ArenaDirector.server.getPlayerList().getPlayerByUUID(UUID.fromString(uid));if(p==null||!ArenaDirector.eligible(p,true)||!d.contains(p.getPosition()))continue;
    boolean height=kind==3?p.posY>=d.floor-20&&p.posY<=d.floor+3.5:p.posY>=d.floor-.2&&p.posY<=d.floor+3.5;
    if(height&&volume(kind,phase,p.posX-cx,p.posZ-cz,cycle)){
     final Vec3d origin=new Vec3d(cx,d.floor+1,cz);DamageSource source=new DamageSource("hbvArena"+kind){public Vec3d getDamageLocation(){return origin;}};if(kind==4&&phase==1)source.setFireDamage();p.attackEntityFrom(source,phase==3?8:phase==2?5:3);
    }}ArenaDirector.broadcast(run,"Committed: clear lanes and long recovery window. Native boss AI keeps its own timings.");}
  if(clock==45)ArenaDirector.restore(d,run);
 }
 static void airOnly(WorldServer w,ArenaDirector.Definition d,JsonObject run,BlockPos p,IBlockState state){if(!d.contains(p)||!w.isBlockLoaded(p)||!w.isAirBlock(p)||!w.getEntitiesWithinAABB(EntityPlayerMP.class,new AxisAlignedBB(p)).isEmpty())return;ArenaDirector.journal(w,run,p,state);}
}

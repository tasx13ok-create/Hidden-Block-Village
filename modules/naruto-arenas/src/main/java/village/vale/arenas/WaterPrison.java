package village.vale.arenas;

import com.google.gson.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Pre-authored, contained owned water state; refuses an open or changed basin. */
final class WaterPrison {
 private WaterPrison(){}
 static boolean contained(WorldServer w,ArenaDirector.Definition d){
  ArenaDirector.Prison p=d.water_prison;if(p==null||p.min==null||p.max==null||p.exits==null||p.exits.length<2)return false;
  int dx=p.max[0]-p.min[0]+1,dy=p.max[1]-p.min[1]+1,dz=p.max[2]-p.min[2]+1;if(dx<3||dz<3||dy<1||dx>17||dz>17||dy>3||dx*dy*dz>768)return false;
  for(int x=p.min[0]-1;x<=p.max[0]+1;x++)for(int z=p.min[2]-1;z<=p.max[2]+1;z++){
   BlockPos bottom=new BlockPos(x,p.min[1]-1,z);if(!d.contains(bottom)||!w.isBlockLoaded(bottom)||!w.getBlockState(bottom).getMaterial().blocksMovement())return false;
   if(x==p.min[0]-1||x==p.max[0]+1||z==p.min[2]-1||z==p.max[2]+1)for(int y=p.min[1];y<=p.max[1];y++){BlockPos wall=new BlockPos(x,y,z);if(!w.isBlockLoaded(wall)||!w.getBlockState(wall).getMaterial().blocksMovement())return false;}
  }
  for(int[] exit:p.exits)if(!ArenaDirector.safe(w,exit))return false;return true;
 }
 static void plan(WorldServer w,ArenaDirector.Definition d,JsonObject run){if(!contained(w,d))throw new IllegalStateException("Authored water-prison boundary or two recovery exits are missing or changed");ArenaDirector.Prison p=d.water_prison;int eligible=0;for(int y=p.min[1];y<=p.max[1];y++)for(int z=p.min[2];z<=p.max[2];z++)for(int x=p.min[0];x<=p.max[0];x++){BlockPos pos=new BlockPos(x,y,z);if(w.getTileEntity(pos)!=null)throw new IllegalStateException("Unexpected tile in owned water state");if(!w.isAirBlock(pos)){if(w.getBlockState(pos).getMaterial().blocksMovement())continue;throw new IllegalStateException("Owned prison baseline has unexpected non-air state");}ArenaDirector.journal(w,run,pos,Blocks.WATER.getDefaultState());eligible++;}if(eligible==0)throw new IllegalStateException("Prison has no eligible dry state cells");run.addProperty("owned_water_active",true);ArenaDirector.store().dirty();}
 static boolean waterState(JsonObject cell,int current){if(!cell.has("owned_water")||!cell.get("owned_water").getAsBoolean())return false;net.minecraft.block.state.IBlockState s=net.minecraft.block.Block.getStateById(current);return (s.getBlock()==Blocks.WATER||s.getBlock()==Blocks.FLOWING_WATER)&&s.getBlock().getMetaFromState(s)>=0&&s.getBlock().getMetaFromState(s)<=7;}
}

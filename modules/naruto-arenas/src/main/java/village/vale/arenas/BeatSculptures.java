package village.vale.arenas;

import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Original bounded block-built environmental shapes, not copied native models. */
final class BeatSculptures {
 private BeatSculptures(){}
 static void box(Map<BlockPos,IBlockState> out,int x0,int y0,int z0,int x1,int y1,int z1,IBlockState state,boolean shell){for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)if(!shell||x==x0||x==x1||y==y0||y==y1||z==z0||z==z1)out.put(new BlockPos(x,y,z),state);}
 static Map<BlockPos,IBlockState> dragon(int cx,int y,int cz){
  Map<BlockPos,IBlockState> out=new LinkedHashMap<>();IBlockState bark=Blocks.LOG.getStateFromMeta(4),wood=Blocks.LOG.getStateFromMeta(0),ivory=Blocks.BONE_BLOCK.getDefaultState();
  for(int z=-28;z<=32;z++){int x=(int)Math.round(22*Math.sin(z/13D)),h=y+11+(int)Math.round(3*Math.cos(z/14D));box(out,cx+x-1,h-1,cz+z,cx+x+1,h+1,cz+z,bark,false);if(z%5==0)out.put(new BlockPos(cx+x,h+2,cz+z),ivory);}
  int hx=cx+(int)Math.round(22*Math.sin(32/13D)),hy=y+11+(int)Math.round(3*Math.cos(32/14D));
  box(out,hx-4,hy-2,cz+31,hx+4,hy+3,cz+39,wood,true);
  box(out,hx-4,hy-3,cz+33,hx+4,hy-3,cz+41,bark,false);
  box(out,hx-3,hy-2,cz+40,hx+3,hy-2,cz+40,ivory,false);
  for(int side:new int[]{-1,1}){box(out,hx+side*3,hy+3,cz+32,hx+side*3,hy+6,cz+32,ivory,false);out.put(new BlockPos(hx+side*4,hy+1,cz+37),Blocks.SEA_LANTERN.getDefaultState());}
  // Two broad source roots bear the dragon above the human walking envelope.
  for(int z:new int[]{-26,-18}){int x=(int)Math.round(22*Math.sin(z/13D)),h=y+11+(int)Math.round(3*Math.cos(z/14D));box(out,cx+x-1,y,cz+z-1,cx+x+1,h-1,cz+z+1,wood,false);}
  return out;
 }
 static Map<BlockPos,IBlockState> guardian(int cx,int y,int cz){
  Map<BlockPos,IBlockState> out=new LinkedHashMap<>();IBlockState armor=Blocks.STAINED_HARDENED_CLAY.getStateFromMeta(10),rib=Blocks.QUARTZ_BLOCK.getDefaultState(),dark=Blocks.COAL_BLOCK.getDefaultState();
  for(int side:new int[]{-1,1}){int x=cx+side*9;box(out,x-2,y,cz-26,x+2,y+17,cz-22,armor,false);box(out,x-3,y,cz-28,x+3,y+2,cz-19,dark,false);}
  box(out,cx-10,y+17,cz-27,cx+10,y+30,cz-21,armor,true);
  for(int x:new int[]{-8,0,8})box(out,cx+x,y+18,cz-20,cx+x,y+29,cz-20,rib,false);
  box(out,cx-4,y+32,cz-27,cx+4,y+39,cz-21,armor,true);box(out,cx-3,y+31,cz-25,cx+3,y+32,cz-22,rib,false);
  for(int side:new int[]{-1,1}){out.put(new BlockPos(cx+side*2,y+36,cz-20),Blocks.SEA_LANTERN.getDefaultState());box(out,cx+side*4,y+39,cz-25,cx+side*4,y+42,cz-25,rib,false);}
  box(out,cx-25,y+27,cz-25,cx-10,y+29,cz-23,armor,false);box(out,cx+10,y+27,cz-25,cx+23,y+29,cz-23,armor,false);
  // Shield and blade align with the two floor warnings emitted by this beat.
  box(out,cx-27,y+18,cz-17,cx-19,y+31,cz-17,rib,true);box(out,cx-25,y+20,cz-17,cx-21,y+29,cz-17,armor,false);
  box(out,cx+21,y+25,cz-20,cx+23,y+27,cz+20,rib,false);box(out,cx+18,y+24,cz-19,cx+26,y+28,cz-18,dark,true);
  return out;
 }
 static void plan(WorldServer w,ArenaDirector.Definition d,JsonObject run,Map<BlockPos,IBlockState> shape){
  if(shape.size()>3400)throw new IllegalStateException("Environmental sculpture exceeds bounded cell budget");
  for(Map.Entry<BlockPos,IBlockState> point:shape.entrySet()){BlockPos p=point.getKey();if(!d.contains(p)||!w.isBlockLoaded(p)||!w.isAirBlock(p)||!w.getEntitiesWithinAABB(net.minecraft.entity.player.EntityPlayerMP.class,new AxisAlignedBB(p)).isEmpty())continue;ArenaDirector.journal(w,run,p,point.getValue());}
 }
}

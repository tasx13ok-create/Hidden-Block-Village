package village.vale.arenas;

import com.google.gson.*;
import java.io.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Real 2048-cell write-ahead journal and actual process restart, private world only. */
public final class LargeStateNativeChecks {
 private LargeStateNativeChecks(){}
 static final String KEY="PRIVATE_QA_LARGE_JOURNAL_V1";
 static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
 public static JsonObject run(EntityPlayerMP p,ArenaDirector.Definition d){JsonObject report=new JsonObject();JsonArray failures=new JsonArray();report.add("failures",failures);report.addProperty("scope","PRIVATE_REAL_2048_OWNED_CELL_JOURNAL_AND_NATIVE_BYTE_ARRAY_SAVE_RESTART");int iteration=Integer.parseInt(System.getProperty("hbv.arenas.probeIteration","1"));report.addProperty("iteration",iteration);WorldServer world=p.getServer().getWorld(0);ArenaDirector.Store store=ArenaDirector.store();
  try{
   check(Boolean.getBoolean("hbv.arenas.nativeProbe"),"Private native guard required");
   JsonObject journal;
   if(iteration==1){check(!store.data.has(KEY),"Large journal already exists before first process");journal=new JsonObject();journal.addProperty("arena",d.id);journal.addProperty("template",d.template_sha256);journal.add("cells",new JsonArray());store.data.add(KEY,journal);store.dirty();
    BlockPos center=new BlockPos(d.min[0]+227,183,d.min[2]+227);NativeProbe.prewarm(world,center);
    for(int y=180;y<188;y++)for(int z=220;z<236;z++)for(int x=220;x<236;x++){BlockPos pos=new BlockPos(d.min[0]+x,y,d.min[2]+z);check(world.getTileEntity(pos)==null,"Unexpected tile in private codec cell");check(world.getBlockState(pos).getBlock()!=Blocks.GOLD_BLOCK,"Unexpected preexisting gold in private codec cell");ArenaDirector.journal(world,journal,pos,Blocks.GOLD_BLOCK.getDefaultState());}
    check(journal.getAsJsonArray("cells").size()==2048,"Native journal must contain all2048before/after states");ArenaDirector.applyJournal(journal);store.dirty();world.getPerWorldStorage().saveAllData();report.addProperty("phase","REAL_2048_NATIVE_CELLS_AND_UTF8_BYTE_ARRAY_SAVED_FIRST_PROCESS");
   }else{check(store.data.has(KEY),"Second process failed to load large native journal");journal=store.data.getAsJsonObject(KEY);check(d.template_sha256.equals(ArenaDirector.text(journal,"template")),"Large journal template identity changed");NativeProbe.prewarm(world,new BlockPos(d.min[0]+227,183,d.min[2]+227));check(journal.getAsJsonArray("cells").size()==2048,"Large journal cells lost in actual process restart");for(JsonElement element:journal.getAsJsonArray("cells")){JsonObject cell=element.getAsJsonObject();BlockPos pos=new BlockPos(ArenaDirector.number(cell,"x"),ArenaDirector.number(cell,"y"),ArenaDirector.number(cell,"z"));check(Block.getStateId(world.getBlockState(pos))==ArenaDirector.number(cell,"after"),"Owned native block changed before restart readback");}report.addProperty("second_process_exact_native_blocks",2048);}
   File file=new File(new File(world.getSaveHandler().getWorldDirectory(),"data"),"naruto_arenas_v1.dat");NBTTagCompound nativeFile;try(InputStream input=new FileInputStream(file)){nativeFile=CompressedStreamTools.readCompressed(input);}NBTTagCompound payload=nativeFile.getCompoundTag("data");check(payload.hasKey("PayloadUtf8",7),"Native disk payload is not byte array");check(!payload.hasKey("Payload",8),"Large payload still stored as JavaUTF string");int bytes=payload.getByteArray("PayloadUtf8").length;check(bytes>65535,"Fixture did not exceed JavaUTF string limit");report.addProperty("actual_native_disk_payload_utf8_bytes",bytes);
   if(iteration>1){check(ArenaDirector.restore(d,journal),"Actual large native journal failed conditional restoration");check(journal.getAsJsonArray("cells").size()==0,"Restored journal retains unexpected cells");store.data.remove(KEY);store.dirty();world.getPerWorldStorage().saveAllData();report.addProperty("phase","REAL_SECOND_PROCESS_2048_BLOCK_READBACK_AND_CONDITIONAL_RESTORE_COMPLETE");}
  }catch(Throwable failure){failures.add(failure.toString());failure.printStackTrace();}
  report.addProperty("passed",failures.size()==0);return report;
 }
}

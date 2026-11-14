package village.vale.arenas;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraftforge.fml.common.registry.*;
import net.minecraft.util.ResourceLocation;

/** Exact inspected provider identity. Each adapter is promoted only after its native flow passes. */
final class NativeBossPolicies {
 private NativeBossPolicies(){}
 private static final String SPRING="49e08a36c9b5584a637743ac3cf2c53de00da1b3fb64e47bb51d67d0aba8760c",NARUTO="6cb62642e995b90f1cf1fc3eecd8b8b68361469460b681384f1fd16d69e378c0",KABUTO="c5c56886982f266fac68898b86b9261a34a13ea9818216d221e212f34c7b8b59";
 // Promotion changes require a new matching source/build/native receipt. Config
 // cannot declare its own terminal acceptance or accept a changed provider.
 private static final boolean[] ACCEPTED={false,false,false,false,false};
 private static final String[][] ROWS={
  {"ahznbcursemarkaddon:edotensei_hashirama","net.mcreator.ahznbcursemarkaddon.entity.EntityEdotenseiHashirama$EntityCustom","406e57247f0ef06c6879305ef7fb97b5bf0f63e0a78dd05ba1830d49cacb2ac5",SPRING},
  {"kabutoaddon:ay","net.mcreator.kabutoaddon.entity.EntityAy$EntityCustom","1308e94a487661e973710564bf90a5ab3350f689768e46c2a985e54d51106f95",KABUTO},
  {"narutomod:gaara","net.narutomod.entity.EntityGaara$EntityCustom","61d82334fc6d72b2e1f7c4431022c86236dfaa8817549bcd4524574e1466e4b4",NARUTO},
  {"narutomod:kisame_hoshigaki","net.narutomod.entity.EntityKisameHoshigaki$EntityCustom","a34493c3204a6f0f62d079a8a3245529dca4eb083b7fd4340118e20af1800220",NARUTO},
  {"ahznbcursemarkaddon:edotensei_madara","net.mcreator.ahznbcursemarkaddon.entity.EntityEdotenseiMadara$EntityCustom","d670cc4128184dd6c9a2bebfc5f8d429b1f3ec95d5dab172df6dfbaba4f70c28",SPRING}
 };
 static String digest(InputStream input)throws Exception{try(InputStream in=input){MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int count;long bytes=0;while((count=in.read(buffer))>0){bytes+=count;if(bytes>256L*1024*1024)throw new IOException("Provider size cap");hash.update(buffer,0,count);}StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format("%02x",b&255));return out.toString();}}
 static boolean identity(ArenaDirector.Definition d){try{EntityEntry entry=ForgeRegistries.ENTITIES.getValue(new ResourceLocation(d.boss));if(entry==null)return false;Class<?> type=entry.getEntityClass();String[] row=null;for(String[] candidate:ROWS)if(candidate[0].equals(d.boss))row=candidate;if(row==null||!type.getName().equals(row[1]))return false;InputStream resource=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class");if(resource==null||!row[2].equals(digest(resource)))return false;URL location=type.getProtectionDomain().getCodeSource().getLocation();URL jar="jar".equals(location.getProtocol())?((JarURLConnection)location.openConnection()).getJarFileURL():location;if(!"file".equals(jar.getProtocol()))return false;Path path=Paths.get(jar.toURI());return Files.isRegularFile(path)&&row[3].equals(digest(Files.newInputStream(path)));}catch(Exception|LinkageError unsupported){return false;}}
 static boolean accepted(ArenaDirector.Definition d){for(int i=0;i<ROWS.length;i++)if(ROWS[i][0].equals(d.boss))return ACCEPTED[i]&&identity(d);return false;}
}

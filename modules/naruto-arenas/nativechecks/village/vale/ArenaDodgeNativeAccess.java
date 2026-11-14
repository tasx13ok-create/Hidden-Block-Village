package village.vale;

import net.minecraft.entity.player.EntityPlayerMP;

/** Private QA package access to the unchanged paid Core action. Not production API. */
public final class ArenaDodgeNativeAccess {
 private ArenaDodgeNativeAccess(){}
 public static boolean perform(EntityPlayerMP player,int direction){
  if(!Boolean.getBoolean("hbv.arenas.nativeProbe")||player.getServer()==null||!player.getServer().getFolderName().equals("arena-preview"))throw new IllegalStateException("Private arena Dodge guard rejected");
  return Dodge.perform(player,direction);
 }
}

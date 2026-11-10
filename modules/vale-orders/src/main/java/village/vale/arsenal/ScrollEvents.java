package village.vale.arsenal;

import village.vale.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.*;

public final class ScrollEvents {
 private static final ThreadLocal<Boolean> EXTRA=new ThreadLocal<>();
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent e){
  if(e.isCanceled()||e.getAmount()<=0||Boolean.TRUE.equals(EXTRA.get())||e.getEntityLiving().world.isRemote)return;
  Entity attacker=e.getSource().getTrueSource();if(!(attacker instanceof EntityPlayerMP))return;EntityPlayerMP p=(EntityPlayerMP)attacker;NBTTagCompound s=Scrolls.state(p);String mode=s.getString("mode");if(s.getLong("modeUntil")<ArsenalAccess.now(p)||!ArsenalAccess.allowed(p,e.getEntityLiving()))return;
  float extra=mode.equals("spiral")?8:mode.equals("charge")?6:mode.equals("fireedge")?3:mode.equals("windedge")?4:mode.equals("lightmode")?2:mode.equals("lava")?3:0;if(extra==0)return;
  EXTRA.set(true);try{if(ArsenalAccess.hit(p,e.getEntityLiving(),extra)){
   if(mode.equals("fireedge")||mode.equals("lava"))e.getEntityLiving().setFire(3);
   if(mode.equals("windedge"))ScrollEffects.push(p,e.getEntityLiving(),.65,.15);
   if(mode.equals("charge")||mode.equals("lightmode"))e.getEntityLiving().addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,40,2));
   if(mode.equals("spiral")){ScrollEffects.push(p,e.getEntityLiving(),1,.4);s.removeTag("mode");}
   if(mode.equals("charge"))s.removeTag("mode");ArsenalAccess.save(p);
  }}finally{EXTRA.remove();}
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void hurt(LivingHurtEvent e){
  if(e.isCanceled()||e.getAmount()<=0||!(e.getEntityLiving() instanceof EntityPlayerMP))return;EntityPlayerMP p=(EntityPlayerMP)e.getEntityLiving();NBTTagCompound s=Scrolls.state(p);
  if(s.getBoolean("replacement")&&s.getLong("replacementUntil")>=ArsenalAccess.now(p)){
   Vec3d from=p.getPositionVector();for(int i=2;i<=5;i++){Vec3d to=from.subtract(p.getLookVec().scale(i));if(ScrollEffects.safe(p,to)){
    if(ScrollActor.spawn(p,"clone",from,null,60,0)==null)return;p.connection.setPlayerLocation(to.x,to.y,to.z,p.rotationYaw,p.rotationPitch);s.setBoolean("replacement",false);e.setCanceled(true);ArsenalAccess.save(p);return;
   }}
  }
  String mode=s.getString("mode");if(s.getLong("modeUntil")>=ArsenalAccess.now(p)){if(mode.equals("lava"))e.setAmount(e.getAmount()*.7f);if(mode.equals("lightmode")){p.addPotionEffect(new PotionEffect(MobEffects.SPEED,30,1));e.setAmount(e.getAmount()*.85f);}}
 }
 @SubscribeEvent public static void death(LivingDeathEvent e){if(!(e.getEntityLiving() instanceof EntityPlayerMP)||e.isCanceled())return;EntityPlayerMP p=(EntityPlayerMP)e.getEntityLiving();boolean slate=false,cloud=false;
  for(int i=0;i<p.inventory.getSizeInventory();i++){ItemStack s=p.inventory.getStackInSlot(i);if(!(s.getItem() instanceof ScrollSupport.Support)||!ArsenalAccess.issued(p,s))continue;String k=((ScrollSupport.Support)s.getItem()).key;if(k.equals("exam_slate")&&!slate){s.shrink(1);slate=true;}else if(k.equals("exam_cloud")&&!cloud){ItemStack drop=s.copy();drop.setCount(1);s.shrink(1);p.entityDropItem(drop,0);cloud=true;}}
  NBTTagCompound st=Scrolls.state(p);st.removeTag("mode");st.setBoolean("replacement",false);ArsenalAccess.save(p);
 }
 @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getEntityPlayer().world.isRemote)return;NBTTagCompound old=PlayerState.data(e.getOriginal());if(old.hasKey("OriginalScrolls",10))PlayerState.data(e.getEntityPlayer()).setTag("OriginalScrolls",old.getCompoundTag("OriginalScrolls").copy());}
 @SubscribeEvent public static void login(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent e){if(!(e.player instanceof EntityPlayerMP))return;EntityPlayerMP p=(EntityPlayerMP)e.player;String team=Scrolls.state(p).getString("party");if(team.startsWith("vs")&&p.getTeam()==null&&p.world.getScoreboard().getTeam(team)!=null)p.world.getScoreboard().addPlayerToTeam(p.getName(),team);}
}

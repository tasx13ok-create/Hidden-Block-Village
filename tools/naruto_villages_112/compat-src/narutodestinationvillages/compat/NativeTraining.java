package narutodestinationvillages.compat;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.narutomod.Chakra;
import net.narutomod.item.ItemJutsu;

/** Original spendable training ledger and actual native effects. Unsupported disciplines are not invented. */
public final class NativeTraining {
 public enum Stat {
  NINJUTSU("ninjutsu","legacyNinjutsu","Ninjutsu",500),
  TAIJUTSU("taijutsu","legacyTaijutsu","Taijutsu",500),
  KENJUTSU("kenjutsu","legacyKenjutsu","Kenjutsu",500),
  SPEED("speed","legacySpeedPoints","Speed",500);
  public final String key,field,title;public final int cap;
  Stat(String k,String f,String t,int c){key=k;field=f;title=t;cap=c;}
  public static Stat byKey(String key){for(Stat s:values())if(s.key.equalsIgnoreCase(key))return s;return null;}
 }
 private static final UUID SPEED=UUID.fromString("ec34df38-414f-478e-9665-69156424e4bc"),HEALTH=UUID.fromString("a0da9e7f-36d7-4d1f-ad37-040592b2d807");
 private static final Set<UUID> BUSY=new HashSet<>();private static boolean registered;
 private NativeTraining(){}
 public static void initialize(){if(!registered){MinecraftForge.EVENT_BUS.register(new NativeTraining());registered=true;}}
 public static NBTTagCompound data(EntityPlayer player){return ExtendedPlayer.get(player).data();}
 public static long balance(EntityPlayer player,String kind){return Math.max(0,data(player).getLong(field(kind)));}
 private static String field(String kind){if("skill".equals(kind))return "unspentLegacyPoints";if("jutsu".equals(kind))return "jutsuPoints";if("reset".equals(kind))return "resetPoints";throw new IllegalArgumentException("Unknown training resource");}
 public static int points(EntityPlayer player,Stat stat){return Math.max(0,data(player).getInteger(stat.field));}
 public static long assigned(EntityPlayer player){long sum=0;for(Stat stat:Stat.values())sum+=points(player,stat);return sum;}
 public static long totalSkillPoints(EntityPlayer player){long balance=balance(player,"skill"),assigned=assigned(player);return balance>Long.MAX_VALUE-assigned?Long.MAX_VALUE:balance+assigned;}
 private static boolean server(EntityPlayerMP player){return player!=null&&!player.world.isRemote&&player.getServer()!=null&&player.getServer().isCallingFromMinecraftThread();}
 public static boolean credit(EntityPlayerMP player,String kind,long amount){
  if(!server(player)||amount<=0)return false;long previous=balance(player,kind);if(previous>Long.MAX_VALUE-amount)return false;
  data(player).setLong(field(kind),previous+amount);return true;
 }
 public static boolean spend(EntityPlayerMP player,Stat stat,int amount){
  if(!server(player)||stat==null||amount<=0||amount>stat.cap)return false;
  if(stat==Stat.NINJUTSU&&!"2".equals(System.getProperty("narutovillages.chakraHook")))return false;
  long balance=balance(player,"skill");int previous=points(player,stat);if(balance<amount||previous>stat.cap-amount)return false;
  NBTTagCompound next=data(player).copy();next.setLong(field("skill"),balance-amount);next.setInteger(stat.field,previous+amount);data(player).merge(next);
  refresh(player);statistics(player);return true;
 }
 public static boolean reset(EntityPlayerMP player){
  if(!server(player)||balance(player,"reset")<1)return false;long refunded=assigned(player),balance=balance(player,"skill");if(refunded==0||balance>Long.MAX_VALUE-refunded)return false;
  NBTTagCompound next=data(player).copy();next.setLong(field("skill"),balance+refunded);next.setLong(field("reset"),balance(player,"reset")-1);for(Stat stat:Stat.values())next.setInteger(stat.field,0);data(player).merge(next);
  refresh(player);Chakra.pathway(player).clear();statistics(player);return true;
 }
 public static double chakraMaximum(double nativeMaximum,EntityLivingBase actor){
  if(!(actor instanceof EntityPlayer)||actor.world==null||actor.world.isRemote||!Double.isFinite(nativeMaximum)||nativeMaximum<0)return nativeMaximum;
  EntityPlayer player=(EntityPlayer)actor;double extra=Math.min(Stat.NINJUTSU.cap,points(player,Stat.NINJUTSU))+0.25*Math.min(Stat.TAIJUTSU.cap,points(player,Stat.TAIJUTSU));
  return nativeMaximum>Double.MAX_VALUE-extra?nativeMaximum:nativeMaximum+extra;
 }
 public static void refresh(EntityPlayerMP player){
  IAttributeInstance health=player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);AttributeModifier oldHealth=health==null?null:health.getModifier(HEALTH);double oldBonus=oldHealth==null?0:oldHealth.getAmount(),newBonus=Math.min(Stat.TAIJUTSU.cap,points(player,Stat.TAIJUTSU))*0.5;
  modifier(player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED),SPEED,"Naruto speed training",Math.min(Stat.SPEED.cap,points(player,Stat.SPEED))*0.005,2);
  modifier(health,HEALTH,"Naruto taijutsu training",newBonus,0);
  if(newBonus<oldBonus&&player.getHealth()>player.getMaxHealth())player.setHealth(player.getMaxHealth());
 }
 private static void modifier(IAttributeInstance attribute,UUID id,String name,double amount,int operation){
  if(attribute==null)return;AttributeModifier previous=attribute.getModifier(id);if(previous!=null&&previous.getAmount()==amount&&previous.getOperation()==operation)return;
  if(previous!=null)attribute.removeModifier(id);if(amount!=0)attribute.applyModifier(new AttributeModifier(id,name,amount,operation).setSaved(false));
 }
 private static void statistics(EntityPlayerMP player){try{ExtendedPlayer.get(player).updateRecord();}catch(RuntimeException failure){org.apache.logging.log4j.LogManager.getLogger("NarutoTraining").error("Training saved but village statistics could not refresh",failure);}}
 @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.player instanceof EntityPlayerMP)refresh((EntityPlayerMP)e.player);}
 @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof EntityPlayerMP&&e.player.ticksExisted%20==0)refresh((EntityPlayerMP)e.player);}
 @SubscribeEvent(priority=EventPriority.LOW) public void hurt(LivingHurtEvent e){
  if(e.getEntityLiving().world.isRemote||!(e.getSource().getTrueSource() instanceof EntityPlayerMP)||!Float.isFinite(e.getAmount())||e.getAmount()<=0)return;
  EntityPlayerMP player=(EntityPlayerMP)e.getSource().getTrueSource();Stat stat;
  if(ItemJutsu.isDamageSourceNinjutsu(e.getSource())||ItemJutsu.isDamageSourceSenjutsu(e.getSource()))stat=Stat.NINJUTSU;
  else if("player".equals(e.getSource().getDamageType())&&e.getSource().getImmediateSource()==player){ItemStack held=player.getHeldItemMainhand();stat=!held.isEmpty()&&held.getAttributeModifiers(EntityEquipmentSlot.MAINHAND).containsKey(SharedMonsterAttributes.ATTACK_DAMAGE.getName())?Stat.KENJUTSU:Stat.TAIJUTSU;}
  else return;
  int invested=Math.min(stat.cap,points(player,stat));if(invested>0)e.setAmount((float)Math.min(Float.MAX_VALUE,(double)e.getAmount()*(1+invested*0.01)));
 }
 @SubscribeEvent public void use(PlayerInteractEvent.RightClickItem event){
  if(event.getWorld().isRemote||!(event.getEntityPlayer() instanceof EntityPlayerMP))return;EntityPlayerMP player=(EntityPlayerMP)event.getEntityPlayer();
  String kind=NativeRewardItems.kind(player.getHeldItem(event.getHand()));if(!NativeRewardItems.isPointKind(kind))return;
  event.setCanceled(true);event.setCancellationResult(redeem(player,event.getHand())?net.minecraft.util.EnumActionResult.SUCCESS:net.minecraft.util.EnumActionResult.FAIL);
 }
 public static boolean redeem(EntityPlayerMP player,EnumHand hand){
  if(!server(player)||!BUSY.add(player.getUniqueID()))return false;
  try{ItemStack stack=player.getHeldItem(hand);String kind=NativeRewardItems.kind(stack);if(!NativeRewardItems.isPointKind(kind))return false;
   int count=player.isSneaking()?stack.getCount():1;long amount=(long)NativeRewardItems.pointValue(stack)*count;if(count<1||count>64||!credit(player,kind,amount))return false;
   stack.shrink(count);if(player.getHeldItem(hand)==stack&&stack.isEmpty())player.setHeldItem(hand,ItemStack.EMPTY);player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();statistics(player);
   player.sendMessage(new TextComponentString("\u00a7aAdded "+amount+" "+kind+" point"+(amount==1?"":"s")+". Open /shinobitraining to spend them."));return true;
  }finally{BUSY.remove(player.getUniqueID());}
 }
}

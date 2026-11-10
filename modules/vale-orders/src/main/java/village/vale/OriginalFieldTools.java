package village.vale;
import java.util.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;
import net.minecraft.item.*;import net.minecraft.entity.*;import net.minecraft.entity.player.*;import net.minecraft.nbt.NBTTagCompound;import net.minecraft.util.*;import net.minecraft.util.text.*;import net.minecraft.world.World;import net.minecraftforge.event.RegistryEvent;
/** Five real original utility entry points. Read-only books never execute user-supplied commands. */
public final class OriginalFieldTools {
 private OriginalFieldTools(){}
 public static final String[] KEYS={"encounter_dossier","wayfarer_atlas","resolve_well","body_trial_charm","companion_covenant"};
 public static void register(RegistryEvent.Register<Item> e){for(int i=0;i<KEYS.length;i++)Gear.add(e,KEYS[i],new Tool(i));}
 public static int price(String key,int level){for(int i=0;i<KEYS.length;i++)if(KEYS[i].equals(key))return i>=3&&level<3?-1:i<2?1:4;return -2;}
 static boolean issued(EntityPlayerMP p,ItemStack s){return p.isEntityAlive()&&!p.isSpectator()&&RewardLedger.get(p).valid(p,s)&&(p.getHeldItemMainhand()==s||p.getHeldItemOffhand()==s);}
 public static final class Tool extends Item {
  public final int kind;Tool(int k){kind=k;setMaxStackSize(1);setCreativeTab(net.minecraft.creativetab.CreativeTabs.COMBAT);}
  @Override public String getItemStackDisplayName(ItemStack s){return new String[]{"Encounter Dossier","Wayfarer Atlas","Resolve Well","Body Trial Charm","Companion Covenant"}[kind];}
  @Override public EnumAction getItemUseAction(ItemStack s){return kind==2?EnumAction.BOW:EnumAction.NONE;}
  @Override public int getMaxItemUseDuration(ItemStack s){return kind==2?72000:0;}
  @Override public ActionResult<ItemStack> onItemRightClick(World w,EntityPlayer player,EnumHand hand){ItemStack stack=player.getHeldItem(hand);if(w.isRemote)return new ActionResult<>(EnumActionResult.SUCCESS,stack);if(!(player instanceof EntityPlayerMP))return new ActionResult<>(EnumActionResult.FAIL,stack);EntityPlayerMP p=(EntityPlayerMP)player;boolean result=false;
   if(kind==0){p.getServer().getCommandManager().executeCommand(p,"valeencounter list");result=true;}
   else if(kind==1){try{Path path=net.minecraftforge.fml.common.Loader.instance().getConfigDir().toPath().resolve("valeworldcompat-destinations.tsv");int count=0;for(String line:Files.readAllLines(path,StandardCharsets.UTF_8))if(!line.trim().isEmpty()&&!line.startsWith("#")&&++count<=32){String[] a=line.split("\\t",5);if(a.length==5)p.sendMessage(new TextComponentString(a[4]+" | dimension "+a[0]+" | "+a[1]+", "+a[2]+", "+a[3]));}result=true;}catch(java.io.IOException ex){Adventures.tell(p,"Travel chart is not installed yet; your atlas is kept.");}}
   else if(issued(p,stack)){
    NBTTagCompound state=PlayerState.data(p);
    if(kind==2){long now=Adventures.now(p);if(p.onGround&&PlayerState.resolve(p)<100&&now>=state.getLong("resolveWellReady")){state.setLong("resolveWellStart",now);state.setString("resolveWellSerial",stack.getTagCompound().getString("valeSerial"));state.setDouble("resolveWellX",p.posX);state.setDouble("resolveWellY",p.posY);state.setDouble("resolveWellZ",p.posZ);p.setActiveHand(hand);result=true;}}
    else {String key=kind==3?"bodyTrialChoice":"companionChoice";int limit=kind==3?Forms.KEYS.length:Summons.KEYS.length;int choice=Math.floorMod(state.getInteger(key),limit);if(p.isSneaking()){choice=(choice+1)%limit;state.setInteger(key,choice);Adventures.save(p);result=Adventures.tell(p,"Selected "+(kind==3?Forms.KEYS[choice]:Summons.KEYS[choice])+". Use normally to activate.",true);}else result=kind==3?Forms.activate(p,choice):Summons.activate(p,choice);}
   }
   return new ActionResult<>(result?EnumActionResult.SUCCESS:EnumActionResult.FAIL,stack);
  }
  @Override public void onPlayerStoppedUsing(ItemStack stack,World w,EntityLivingBase user,int left){if(kind!=2||w.isRemote||!(user instanceof EntityPlayerMP))return;EntityPlayerMP p=(EntityPlayerMP)user;if(!issued(p,stack))return;NBTTagCompound s=PlayerState.data(p);long now=Adventures.now(p);boolean ready=s.getString("resolveWellSerial").equals(stack.getTagCompound().getString("valeSerial"))&&now-s.getLong("resolveWellStart")>=40&&now-s.getLong("resolveWellStart")<=72000&&72000-left>=40&&p.onGround&&p.getDistanceSq(s.getDouble("resolveWellX"),s.getDouble("resolveWellY"),s.getDouble("resolveWellZ"))<.04&&now>=s.getLong("resolveWellReady");s.removeTag("resolveWellSerial");if(ready&&PlayerState.resolve(p)<100){PlayerState.setResolve(p,PlayerState.resolve(p)+25);s.setLong("resolveWellReady",now+600);Adventures.save(p);Adventures.tell(p,"Restored up to25 resolve. Resting well cools down for30seconds.");}}
  @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)@Override public void addInformation(ItemStack stack,World world,List<String> lines,net.minecraft.client.util.ITooltipFlag flag){lines.add(kind==2?"Hold use for2seconds while still and grounded; release. +25resolve,30second cooldown.":kind>=3?"Sneak-use selects. Normal use activates the existing issued-armor form/companion rules.":"Read-only field information. No item or permission grants.");}
 }
}

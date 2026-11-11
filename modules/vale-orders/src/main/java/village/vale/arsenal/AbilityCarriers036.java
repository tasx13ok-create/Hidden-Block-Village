package village.vale.arsenal;

import java.util.*;
import village.vale.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.relauncher.*;

/** Native multi-technique codices for existing original engines; no new spell claims. */
public final class AbilityCarriers036 {
 private AbilityCarriers036(){}
 public static final class Profile {
  public final String key,title;public final String[] lessons;
  public Profile(String key,String title,String[] lessons){if(key==null||!key.matches("[a-z][a-z0-9_]{1,48}")||title==null||lessons==null||lessons.length<1||lessons.length>49)throw new IllegalArgumentException("Invalid profile");this.key=key;this.title=title;this.lessons=lessons.clone();Set<String> unique=new HashSet<>();for(String s:lessons)if(!unique.add(s))throw new IllegalArgumentException("Duplicate lesson");}
 }
 public static final Profile[] PROFILES=CarrierProfiles036.ALL;
 private static final List<Item> ITEMS=new ArrayList<>();
 public static void register(RegistryEvent.Register<Item> e){for(Profile p:PROFILES)add(e,p.key,new CarrierItem(p));add(e,"resonance_vow",new VowItem());}
 private static void add(RegistryEvent.Register<Item> e,String key,Item item){if(Gear.ITEMS.containsKey(key))throw new IllegalStateException("Duplicate carrier "+key);item.setRegistryName(new ResourceLocation("valeorders",key));item.setTranslationKey("valeorders."+key);item.setMaxStackSize(1);Gear.ITEMS.put(key,item);e.getRegistry().register(item);ITEMS.add(item);}
 @SideOnly(Side.CLIENT) public static void registerRenderers(){for(Item item:ITEMS)net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(item,0,new net.minecraft.client.renderer.block.model.ModelResourceLocation("minecraft:book","inventory"));}
 public static int price(EntityPlayerMP p,String key){if(key.equals("resonance_vow"))return 4;for(Profile profile:PROFILES)if(profile.key.equals(key))return 6;return -2;}
 static Scrolls.Lesson lesson(String key){for(Scrolls.Lesson l:Scrolls.LESSONS)if(l.key.equals(key))return l;return null;}
 static boolean held(EntityPlayerMP p,ItemStack s){return s!=null&&!s.isEmpty()&&(p.getHeldItemMainhand()==s||p.getHeldItemOffhand()==s);}
 static boolean issuedHeld(EntityPlayerMP p,ItemStack s){return p!=null&&p.isEntityAlive()&&!p.isSpectator()&&held(p,s)&&ArsenalAccess.issued(p,s);}
 static NBTTagCompound canonical(EntityPlayerMP p,ItemStack s){if(!issuedHeld(p,s)||!(s.getItem() instanceof CarrierItem)||!s.hasTagCompound())return null;Profile profile=((CarrierItem)s.getItem()).profile;return CarrierJournal036.get(p).bind(s.getTagCompound().getString("valeSerial"),p.getUniqueID().toString(),profile.key);}
 static String selected(Profile profile,NBTTagCompound r){return profile.lessons[CarrierRules036.selection(r.getInteger("selected"),profile.lessons.length)];}
 static boolean learned(EntityPlayerMP p,Scrolls.Lesson l){return l!=null&&Scrolls.row(p,l.key).getBoolean("learned")&&(l.prerequisite.isEmpty()||Scrolls.row(p,l.prerequisite).getBoolean("learned"));}
 static boolean affinity(EntityPlayerMP p,Profile profile){NBTTagCompound a=CarrierJournal036.get(p).affinity(p.getUniqueID().toString());return a.getBoolean("confirmed")&&profile.key.equals(a.getString("profile"));}
 static int required(EntityPlayerMP p,Profile profile,Scrolls.Lesson l){return CarrierRules036.required(l.practice,affinity(p,profile));}
 /** Narrow Scrolls bridge guard: exact held, issued carrier, canonical selected lesson and mastery. */
 public static boolean authorizedCast(EntityPlayerMP p,ItemStack s,String key){
  NBTTagCompound r=canonical(p,s);if(r==null||p.isSneaking()||p.getHeldItemMainhand()!=s)return false;Profile profile=((CarrierItem)s.getItem()).profile;if(!selected(profile,r).equals(key))return false;Scrolls.Lesson l=lesson(key);return learned(p,l)&&PlayerState.level(p)>=l.level&&r.getCompoundTag("mastery").getInteger(key)>=required(p,profile,l)&&ArsenalAccess.now(p)>=r.getCompoundTag("cooldowns").getLong(key);
 }
 static void mirror(ItemStack stack,Profile profile,NBTTagCompound r,EntityPlayerMP p){NBTTagCompound n=stack.getTagCompound();NBTTagCompound view=new NBTTagCompound();String key=selected(profile,r);Scrolls.Lesson l=lesson(key);view.setString("serial",r.getString("serial"));view.setString("owner",r.getString("owner"));view.setString("profile",profile.key);view.setString("selected",key);view.setInteger("mastery",r.getCompoundTag("mastery").getInteger(key));view.setInteger("required",l==null?0:required(p,profile,l));view.setBoolean("affinity",affinity(p,profile));n.setTag("ValeCarrier036View",view);p.inventory.markDirty();}
 static boolean cycle(EntityPlayerMP p,ItemStack s,Profile profile,NBTTagCompound r){boolean[] known=new boolean[profile.lessons.length];for(int i=0;i<known.length;i++){Scrolls.Lesson l=lesson(profile.lessons[i]);known[i]=learned(p,l)&&PlayerState.level(p)>=l.level;}int next=CarrierRules036.nextKnown(r.getInteger("selected"),known);if(next<0)return Scrolls.tell(p,"No learned, level-eligible techniques in this codex. Learn a lesson first.");r.setInteger("selected",next);mirror(s,profile,r,p);CarrierJournal036.get(p).flush(p);Scrolls.tell(p,profile.title+": "+lesson(profile.lessons[next]).title);return true;}
 static boolean practice(EntityPlayerMP p,ItemStack s,Profile profile,NBTTagCompound r){
  String key=selected(profile,r);Scrolls.Lesson l=lesson(key);if(l==null)return false;NBTTagCompound global=Scrolls.row(p,key);long now=ArsenalAccess.now(p);int xp=r.getCompoundTag("mastery").getInteger(key),need=required(p,profile,l);
  CarrierRules036.Failure fail=CarrierRules036.gate(true,true,true,true,learned(p,l),PlayerState.level(p),l.level,xp,0,now,Math.max(r.getCompoundTag("cooldowns").getLong(key),global.getLong("next")),PlayerState.resolve(p),2);
  if(fail!=CarrierRules036.Failure.NONE)return Scrolls.tell(p,"Practice kept: "+fail.name().toLowerCase(Locale.ROOT));if(!CarrierRules036.canTrain(xp,need))return Scrolls.tell(p,"This codex technique is mastered. Resolve kept.");
  PlayerState.setResolve(p,PlayerState.resolve(p)-2);r.getCompoundTag("mastery").setInteger(key,CarrierRules036.advance(xp,1));long next=CarrierRules036.next(now,30);r.getCompoundTag("cooldowns").setLong(key,next);
  // Saturating global progress can stay60 while a replacement codex trains anew.
  global.setInteger("practice",ScrollRules.advance(global.getInteger("practice"),1,60));global.setLong("next",next);mirror(s,profile,r,p);CarrierJournal036.get(p).flush(p);ArsenalAccess.gesture(p);ArsenalAccess.save(p);Scrolls.tell(p,l.title+": codex mastery "+(xp+1)+"/"+need);return true;
 }
 static boolean use(EntityPlayerMP p,ItemStack s,Profile profile,EnumHand hand){
  NBTTagCompound r=canonical(p,s);if(r==null)return Scrolls.tell(p,"Only the issued owner's held codex can act.");if(p.isSneaking())return cycle(p,s,profile,r);if(hand==EnumHand.OFF_HAND)return practice(p,s,profile,r);String key=selected(profile,r);Scrolls.Lesson l=lesson(key);
  if(!authorizedCast(p,s,key))return Scrolls.tell(p,"Codex cast unavailable: learn, train, meet its level and wait for cooldown.");
  if(!Scrolls.castFromCarrier036(p,s,key))return false;
  NBTTagCompound cooldowns=r.getCompoundTag("cooldowns");cooldowns.setLong(key,Math.max(cooldowns.getLong(key),CarrierRules036.next(ArsenalAccess.now(p),l.cooldown)));
  // Effect callbacks may remove the carrier or change the held stack. Its saved
  // deadline remains, but never write its display mirror onto a replacement.
  if(issuedHeld(p,s)&&canonical(p,s)==r)mirror(s,profile,r,p);
  CarrierJournal036.get(p).flush(p);ArsenalAccess.save(p);return true;
 }
 public static final class CarrierItem extends Item {
  public final Profile profile;CarrierItem(Profile p){profile=p;}
  @Override public String getItemStackDisplayName(ItemStack s){return profile.title;}
  @Override public ActionResult<ItemStack> onItemRightClick(World w,EntityPlayer entity,EnumHand hand){ItemStack s=entity.getHeldItem(hand);if(w.isRemote)return new ActionResult<>(EnumActionResult.SUCCESS,s);boolean ok=entity instanceof EntityPlayerMP&&s.getItem()==this&&use((EntityPlayerMP)entity,s,profile,hand);return new ActionResult<>(ok?EnumActionResult.SUCCESS:EnumActionResult.FAIL,entity.getHeldItem(hand));}
  @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack s,World w,List<String> lines,net.minecraft.client.util.ITooltipFlag flag){lines.add("Main hand: cast. Offhand: practice. Sneak-use: cycle learned techniques.");lines.add(""+profile.lessons.length+" original techniques; learn their lessons first.");if(s.hasTagCompound()&&s.getTagCompound().hasKey("ValeCarrier036View",10)){NBTTagCompound n=s.getTagCompound().getCompoundTag("ValeCarrier036View");lines.add(n.getString("selected").replace('_',' ')+" — "+n.getInteger("mastery")+"/"+n.getInteger("required"));lines.add(n.getBoolean("affinity")?"Resonant training:1x":"Other discipline:2.5x training");}lines.add("Each issued codex keeps its own training.");}
 }
 static final class VowItem extends Item {
  @Override public String getItemStackDisplayName(ItemStack s){return "Resonance Vow";}
  @Override public ActionResult<ItemStack> onItemRightClick(World w,EntityPlayer entity,EnumHand hand){ItemStack s=entity.getHeldItem(hand);if(w.isRemote)return new ActionResult<>(EnumActionResult.SUCCESS,s);if(!(entity instanceof EntityPlayerMP)||s.getItem()!=this||!issuedHeld((EntityPlayerMP)entity,s))return new ActionResult<>(EnumActionResult.FAIL,s);EntityPlayerMP p=(EntityPlayerMP)entity;CarrierJournal036 journal=CarrierJournal036.get(p);NBTTagCompound a=journal.affinity(p.getUniqueID().toString());int choice=CarrierRules036.selection(a.getInteger("choice"),PROFILES.length);
   if(a.getBoolean("confirmed")){Scrolls.tell(p,"Your permanent resonance is "+a.getString("profile").replace('_',' ')+". Vow kept.");return new ActionResult<>(EnumActionResult.FAIL,s);}
   if(p.isSneaking()){choice=(choice+1)%PROFILES.length;a.setInteger("choice",choice);Scrolls.tell(p,"Proposed resonance: "+PROFILES[choice].title+". Use normally to confirm once.");}else{a.setBoolean("confirmed",true);a.setString("profile",PROFILES[choice].key);a.setString("receipt",s.getTagCompound().getString("valeSerial"));Scrolls.tell(p,"Confirmed resonance: "+PROFILES[choice].title+". Other disciplines need2.5x codex practice.");}journal.flush(p);ArsenalAccess.save(p);return new ActionResult<>(EnumActionResult.SUCCESS,s);
  }
  @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack s,World w,List<String> lines,net.minecraft.client.util.ITooltipFlag flag){lines.add("Sneak-use chooses a discipline; normal use confirms once.");lines.add("A permanent original training preference; no offensive stat bonus.");}
 }
}

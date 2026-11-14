package village.vale.arsenal;

import java.util.*;
import village.vale.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;

/** Distinct original support workflows using the native hands, inventory and scoreboard. */
final class ScrollSupport {
 static final String[] KEYS={"ember_loom","exam_slate","ancestral_seal","exam_cloud","practice_note","medical_loom","puppet_spindle","puppet_sentinel","puppet_wisp","puppet_furnace","party_charter","study_note"};
 static void register(RegistryEvent.Register<Item> e){for(String k:KEYS)Scrolls.add(e,k,new Support(k));for(String k:new String[]{"sight_eye","void_eye","eternal_eye","inferno_eye","hollow_eye","awakening_eye","awakened_eye"})Scrolls.add(e,k,new Eye(k));Scrolls.add(e,"scroll_actor_glyph",new Item());}
 static String title(String k){StringBuilder b=new StringBuilder();for(String s:k.split("_")){if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));}return b.toString();}
 static NBTTagCompound tag(ItemStack s){if(!s.hasTagCompound())s.setTagCompound(new NBTTagCompound());return s.getTagCompound();}
 static boolean consumed(EntityPlayerMP p,ItemStack s,String scope){return Scrolls.row(p,scope).getBoolean(tag(s).getString("valeSerial"));}
 static void consume(EntityPlayerMP p,ItemStack s,String scope){Scrolls.row(p,scope).setBoolean(tag(s).getString("valeSerial"),true);s.shrink(1);p.inventory.markDirty();}
 static boolean xp(EntityPlayerMP p,ItemStack s,EnumHand hand,boolean diminishing){
  ItemStack other=p.getHeldItem(hand==EnumHand.MAIN_HAND?EnumHand.OFF_HAND:EnumHand.MAIN_HAND);
  if(!(other.getItem() instanceof Scrolls.ScrollItem)||!((Scrolls.ScrollItem)other.getItem()).technique||!Scrolls.ready(p,other)||consumed(p,s,"usedNotes"))return Scrolls.tell(p,"Hold your learned technique in the other hand; note kept.");
  Scrolls.Lesson l=((Scrolls.ScrollItem)other.getItem()).lesson;NBTTagCompound r=Scrolls.row(p,l.key);if(!r.getBoolean("learned")||r.getInteger("practice")>=60)return false;
  NBTTagCompound st=Scrolls.state(p);int count=st.getInteger("studyCount");int amount=diminishing?ScrollRules.studyAward(count,p.getRNG().nextInt(101)):10;
  r.setInteger("practice",ScrollRules.advance(r.getInteger("practice"),amount,60));if(diminishing)st.setInteger("studyCount",Math.min(1000000,count+1));consume(p,s,"usedNotes");ArsenalAccess.save(p);return true;
 }
 static boolean medical(EntityPlayerMP p,ItemStack loom,boolean ember){
  if(PlayerState.level(p)<4||!Scrolls.row(p,"calm_mending").getBoolean("learned"))return Scrolls.tell(p,"Medical level 4 and Calm Mending required; inputs kept.");
  ScrollJournal journal=ScrollJournal.get(p);
  List<ItemStack> eyes=new ArrayList<>();for(ItemStack x:p.inventory.mainInventory)if(x.getItem() instanceof Eye&&!consumed(p,x,"medicalUsed")&&!journal.spent(tag(x).getString("valeSerial")))eyes.add(x);
  for(ItemStack a:eyes)for(ItemStack b:eyes)if(a!=b){
   NBTTagCompound na=tag(a),nb=tag(b);String da=na.getString("donor"),db=nb.getString("donor");if(da.isEmpty()||da.equals(db)||!na.getBoolean("opened")||!nb.getBoolean("opened"))continue;
   EntityPlayerMP pa, pb;try{pa=p.getServer().getPlayerList().getPlayerByUUID(UUID.fromString(da));pb=p.getServer().getPlayerList().getPlayerByUUID(UUID.fromString(db));}catch(IllegalArgumentException ex){continue;}
   if(pa==null||pb==null||!ArsenalAccess.issued(pa,a)||!ArsenalAccess.issued(pb,b))continue;
   String ka=((Eye)a.getItem()).key,kb=((Eye)b.getItem()).key;
   // Compatible additional pairs accelerate a real ticking awakening in inventory.
   ItemStack evolving=ItemStack.EMPTY;for(ItemStack x:eyes)if(((Eye)x.getItem()).key.equals("awakening_eye")&&tag(x).getLong("readyAt")>0)evolving=x;
   if(!evolving.isEmpty()&&ka.equals(kb)&&ka.equals("sight_eye")){if(!journal.claim(p,na.getString("valeSerial"),nb.getString("valeSerial")))continue;long due=tag(evolving).getLong("readyAt");tag(evolving).setLong("readyAt",Math.max(p.world.getTotalWorldTime(),due-345600));consume(p,a,"medicalUsed");consume(p,b,"medicalUsed");journal.finish(p,na.getString("valeSerial"),nb.getString("valeSerial"));ArsenalAccess.save(p);return true;}
   String result=null;if(ka.equals("sight_eye")&&kb.equals("sight_eye"))result=ember?"inferno_eye":"eternal_eye";if(!ember&&ka.equals("eternal_eye")&&kb.equals("eternal_eye"))result="awakening_eye";if(ember&&ka.equals("void_eye")&&kb.equals("void_eye"))result="hollow_eye";
   if(result==null)continue;String x=na.getString("valeSerial"),y=nb.getString("valeSerial");String receipt="vale-medical-v1/"+(x.compareTo(y)<0?x+"/"+y:y+"/"+x)+"/"+result;
   if(!journal.claim(p,x,y))continue;RewardLedger.get(p).issue(p,result,1,receipt);consume(p,a,"medicalUsed");consume(p,b,"medicalUsed");journal.finish(p,x,y);ArsenalAccess.save(p);return true;
  }
  return Scrolls.tell(p,"Keep two compatible opened eyes with different online donors in inventory; inputs kept.");
 }
 static boolean party(EntityPlayerMP p){
  NBTTagCompound s=Scrolls.state(p);Scoreboard board=p.world.getScoreboard();
  if(p.isSneaking()){if(p.getTeam()!=null&&p.getTeam().getName().startsWith("vs")){board.removePlayerFromTeams(p.getName());s.removeTag("party");ArsenalAccess.save(p);return true;}return false;}
  String invite=s.getString("partyInvite");if(!invite.isEmpty()&&s.getLong("partyInviteUntil")>=ArsenalAccess.now(p)){
   ScorePlayerTeam team=board.getTeam(invite);if(team==null||p.getTeam()!=null)return false;board.addPlayerToTeam(p.getName(),invite);s.setString("party",invite);s.removeTag("partyInvite");ArsenalAccess.save(p);return true;
  }
  EntityPlayerMP target=null;Vec3d eye=p.getPositionEyes(1),end=eye.add(p.getLookVec().scale(8));double distance=64;for(EntityPlayer x:p.world.playerEntities)if(x instanceof EntityPlayerMP&&x!=p&&x.getEntityBoundingBox().grow(.5).calculateIntercept(eye,end)!=null&&p.getDistanceSq(x)<distance){target=(EntityPlayerMP)x;distance=p.getDistanceSq(x);}
  if(p.getTeam()!=null&&!p.getTeam().getName().startsWith("vs"))return false;
  ScorePlayerTeam existing=ownParty(board,p.getUniqueID(),p.getName());if(existing==null)return false;
  String team=existing.getName();s.setString("party",team);
  if(target!=null){if(target.getTeam()!=null)return false;NBTTagCompound theirs=Scrolls.state(target);theirs.setString("partyInvite",team);theirs.setLong("partyInviteUntil",ArsenalAccess.now(target)+1200);Scrolls.tell(target,"Expedition invite from "+p.getName()+". Use your Party Charter to accept.");ArsenalAccess.save(target);}
  ArsenalAccess.save(p);return true;
 }
 /** Re-enter an existing owned expedition without stealing an outside team. */
 static ScorePlayerTeam ownParty(Scoreboard board,UUID owner,String playerName){
  String key="vs"+owner.toString().replace("-","").substring(0,12);
  ScorePlayerTeam current=board.getPlayersTeam(playerName);
  if(current!=null&&!key.equals(current.getName()))return null;
  ScorePlayerTeam team=board.getTeam(key);
  if(team==null){team=board.createTeam(key);team.setDisplayName(playerName+" expedition");team.setAllowFriendlyFire(false);}
  if(current==null&&!board.addPlayerToTeam(playerName,key))return null;
  return team.getMembershipCollection().contains(playerName)?team:null;
 }
 static NBTTagCompound companion(EntityPlayerMP p,ItemStack s){return ScrollJournal.get(p).companion(tag(s).getString("valeSerial"));}
 static boolean deploy(EntityPlayerMP p,ItemStack stack,String kind,BlockPos pos,EnumFacing face){
  if(face!=EnumFacing.UP||!p.world.getBlockState(pos).isSideSolid(p.world,pos,face)||PlayerState.level(p)<4||village.vale.bridge.MoveCost.availableUnits(p)<20)return false;
  NBTTagCompound n=companion(p,stack);if(n.getBoolean("deployed"))return false;if(n.hasKey("health")&&(!Float.isFinite(n.getFloat("health"))||n.getFloat("health")<=0))return Scrolls.tell(p,"Companion destroyed. Repair with four iron ingots while sealed.");
  if(kind.equals("summon"))kind="summon_"+Math.max(1,Math.min(3,n.getInteger("variant")));
  village.vale.bridge.MoveCost.Ticket payment=village.vale.bridge.MoveCost.reserve(p,20);if(payment==null)return false;
  net.minecraft.world.World world=p.world;NBTTagCompound account=PlayerState.data(p);
  try{if(!payment.valid()||!ArsenalAccess.issued(p,stack)||(p.getHeldItemMainhand()!=stack&&p.getHeldItemOffhand()!=stack))return false;
  ScrollActor actor=ScrollActor.spawnPaid(p,kind,new Vec3d(pos).add(.5,1,.5),null,12000,kind.startsWith("summon_")?6:4,payment);if(actor==null)return false;if(p.world!=world||PlayerState.data(p)!=account){actor.setDead();world.removeEntity(actor);return false;}if(n.hasKey("health"))actor.setHealth(Math.min(actor.getMaxHealth(),n.getFloat("health")));actor.sourceSerial=tag(stack).getString("valeSerial");actor.setOrder(n.getString("order"),false);ScrollJournal.get(p).deployCompanion(actor.sourceSerial,p.getUniqueID(),actor.getUniqueID(),p.dimension,actor.expires);tag(stack).setString("valeCompanionOrder",actor.order().title);if(payment.pending())payment.commit();ScrollJournal.get(p).flush(p);ArsenalAccess.save(p);return true;}finally{payment.close();}
 }
 static ScrollActor linkedActor(EntityPlayerMP p,UUID id){
  for(net.minecraft.world.WorldServer world:net.minecraftforge.common.DimensionManager.getWorlds())
   for(Entity entity:world.loadedEntityList)if(entity instanceof ScrollActor&&entity.getUniqueID().equals(id)&&((ScrollActor)entity).owned(p)&&!entity.isDead)return (ScrollActor)entity;
  return null;
 }
 static boolean recall(EntityPlayerMP p,ItemStack stack){
  NBTTagCompound n=companion(p,stack);if(!n.getBoolean("deployed")||(n.hasUniqueId("owner")&&!p.getUniqueID().equals(n.getUniqueId("owner"))))return false;
  ScrollJournal journal=ScrollJournal.get(p);ScrollActor actor=n.hasUniqueId("actor")?linkedActor(p,n.getUniqueId("actor")):null;
  if(actor!=null){actor.recordCompanion(true);actor.setDead();}
  // An unloaded actor loses its authoritative lease immediately and cannot act
  // when its chunk returns. Its last recorded health survives recall.
  journal.sealCompanion(tag(stack).getString("valeSerial"),p.getUniqueID());journal.flush(p);ArsenalAccess.save(p);return true;
 }
 static boolean cycleOrder(EntityPlayerMP p,ItemStack stack){
  NBTTagCompound n=companion(p,stack);String next=ScrollCompanionOrders.next(n.getString("order")).key;
  if(n.getBoolean("deployed")){
   if(!n.hasUniqueId("actor"))return false;ScrollActor actor=linkedActor(p,n.getUniqueId("actor"));
   if(actor==null||actor.world!=p.world||!ScrollJournal.get(p).activeCompanion(tag(stack).getString("valeSerial"),actor.getUniqueID(),p.getUniqueID(),p.dimension))return Scrolls.tell(p,"Recall the unloaded companion before changing its orders.");
   actor.setOrder(next,true);
  }
  n.setString("order",next);tag(stack).setString("valeCompanionOrder",ScrollCompanionOrders.read(next).title);p.inventory.markDirty();
  ScrollJournal.get(p).flush(p);ArsenalAccess.save(p);Scrolls.tell(p,"Companion order: "+ScrollCompanionOrders.read(next).title+". Sneak + use cycles orders.");return true;
 }
 static boolean repair(EntityPlayerMP p,ItemStack stack){
  NBTTagCompound n=companion(p,stack);if(n.getBoolean("deployed"))return false;
  float health=n.getFloat("health");if(Float.isFinite(health)&&health>0)return false;
  int found=0;for(int i=0;i<p.inventory.getSizeInventory()&&found<4;i++){ItemStack material=p.inventory.getStackInSlot(i);if(material.getItem()==net.minecraft.init.Items.IRON_INGOT)found+=Math.min(4-found,material.getCount());}
  if(found<4)return Scrolls.tell(p,"Four iron ingots required; all materials kept.");
  int left=4;for(int i=0;i<p.inventory.getSizeInventory()&&left>0;i++){ItemStack material=p.inventory.getStackInSlot(i);if(material.getItem()==net.minecraft.init.Items.IRON_INGOT){int used=Math.min(left,material.getCount());material.shrink(used);left-=used;}}
  n.setFloat("health",30);p.inventory.markDirty();ScrollJournal.get(p).flush(p);ArsenalAccess.save(p);return true;
 }
 /** Seal loaded and unloaded owned deployments without forcing any chunk loads. */
 static void closeOwner(EntityPlayerMP p){
  ScrollJournal journal=ScrollJournal.get(p);
  for(net.minecraft.world.WorldServer world:net.minecraftforge.common.DimensionManager.getWorlds())
   for(Entity entity:new ArrayList<Entity>(world.loadedEntityList))if(entity instanceof ScrollActor&&((ScrollActor)entity).owned(p)){
    ScrollActor actor=(ScrollActor)entity;actor.recordCompanion(true);actor.setDead();
   }
  journal.sealOwnerCompanions(p.getUniqueID());journal.flush(p);
 }
 static final class Support extends Item {
  final String key;Support(String k){key=k;}
  @Override public String getItemStackDisplayName(ItemStack s){return title(key);}
  @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT) @Override public void addInformation(ItemStack stack,World world,List<String> lines,net.minecraft.client.util.ITooltipFlag flag){
   if(key.startsWith("puppet")||key.equals("ancestral_seal")){lines.add("Level 4; use on solid upper face to deploy (20 resolve).");lines.add("Use in air to recall. Sneak + use: Assist / Follow / Guard / Hold.");lines.add("Combat roles: left-click foe for ten-second attack; sneak-click clears.");lines.add("Assist joins your recent combat; Guard protects its position from monsters.");if(stack.hasTagCompound()&&stack.getTagCompound().hasKey("valeCompanionOrder"))lines.add("Saved order: "+stack.getTagCompound().getString("valeCompanionOrder"));lines.add("Destroyed companion: use with four iron ingots to repair.");if(key.equals("ancestral_seal")){lines.add("While sealed, sneak + use selects guard, scout or expedition medic.");lines.add("Medics treat injured owner / expedition allies; they never attack.");}}
   else if(key.endsWith("loom")){lines.add("Level 4 + Calm Mending. Use with two compatible opened eyes in inventory.");lines.add("Donors must be distinct and online. Successful recipe consumes both inputs.");}
   else if(key.equals("party_charter")){lines.add("Aim at player and use to invite; use your charter to accept.");lines.add("Sneak + use leaves expedition. Existing outside teams are preserved.");}
   else if(key.endsWith("note"))lines.add("Hold learned technique in other hand; use consumes note for practice.");
   else lines.add("Passive exam token. Slate is removed on death; Cloud is dropped.");
  }
  @Override public ActionResult<ItemStack> onItemRightClick(World w,EntityPlayer player,EnumHand hand){ItemStack s=player.getHeldItem(hand);if(w.isRemote)return new ActionResult<>(EnumActionResult.SUCCESS,s);if(!(player instanceof EntityPlayerMP))return new ActionResult<>(EnumActionResult.FAIL,s);EntityPlayerMP p=(EntityPlayerMP)player;boolean ok=false;
   if(Scrolls.ready(p,s)){
    if(key.equals("practice_note")||key.equals("study_note"))ok=xp(p,s,hand,key.equals("study_note"));
    else if(key.endsWith("loom"))ok=medical(p,s,key.equals("ember_loom"));
    else if(key.equals("party_charter"))ok=party(p);
    else if(key.startsWith("puppet")||key.equals("ancestral_seal")){
     if(companion(p,s).getBoolean("deployed"))ok=p.isSneaking()?cycleOrder(p,s):recall(p,s);
     else if(companion(p,s).hasKey("health")&&(!Float.isFinite(companion(p,s).getFloat("health"))||companion(p,s).getFloat("health")<=0))ok=repair(p,s);
     else if(key.equals("ancestral_seal")&&p.isSneaking()){NBTTagCompound row=companion(p,s);row.setInteger("variant",Math.max(0,row.getInteger("variant"))%3+1);ScrollJournal.get(p).flush(p);Scrolls.tell(p,"Sealed companion variant: "+row.getInteger("variant")+" (1 guard, 2 scout, 3 expedition medic)");ok=true;}
     else if(p.isSneaking())ok=cycleOrder(p,s);else Scrolls.tell(p,"Use on the top of solid ground to deploy. Use in air to recall.");
    }else if(key.startsWith("exam"))Scrolls.tell(p,"Exam token: retained until death; the cloud token drops on defeat.");
   }return new ActionResult<>(ok?EnumActionResult.SUCCESS:EnumActionResult.FAIL,s);
  }
  @Override public EnumActionResult onItemUse(EntityPlayer player,World w,BlockPos pos,EnumHand hand,EnumFacing face,float x,float y,float z){if(w.isRemote)return EnumActionResult.SUCCESS;if(!(player instanceof EntityPlayerMP))return EnumActionResult.FAIL;EntityPlayerMP p=(EntityPlayerMP)player;ItemStack s=p.getHeldItem(hand);return Scrolls.ready(p,s)&&(key.startsWith("puppet")||key.equals("ancestral_seal"))&&deploy(p,s,key.equals("ancestral_seal")?"summon":key,pos,face)?EnumActionResult.SUCCESS:EnumActionResult.FAIL;}
  @Override public boolean onLeftClickEntity(ItemStack s,EntityPlayer player,Entity target){if(player instanceof EntityPlayerMP&&(key.startsWith("puppet")||key.equals("ancestral_seal"))){EntityPlayerMP p=(EntityPlayerMP)player;if(Scrolls.ready(p,s)&&companion(p,s).hasUniqueId("actor")){ScrollActor a=ScrollActor.find(p,companion(p,s).getUniqueId("actor"));if(a!=null&&(p.isSneaking()?a.command(p,null):target instanceof EntityLivingBase&&a.command(p,(EntityLivingBase)target))){Scrolls.tell(p,p.isSneaking()?"Companion target cleared.":"Companion attack ordered for ten seconds.");ArsenalAccess.save(p);}}return true;}return false;}
 }
 static final class Eye extends Item {
  final String key;Eye(String k){key=k;}
  @Override public String getItemStackDisplayName(ItemStack s){return title(key);}
  @Override public ActionResult<ItemStack> onItemRightClick(World w,EntityPlayer player,EnumHand h){ItemStack s=player.getHeldItem(h);if(w.isRemote)return new ActionResult<>(EnumActionResult.SUCCESS,s);if(!(player instanceof EntityPlayerMP)||!Scrolls.ready((EntityPlayerMP)player,s)||consumed((EntityPlayerMP)player,s,"medicalUsed"))return new ActionResult<>(EnumActionResult.FAIL,s);EntityPlayerMP p=(EntityPlayerMP)player;NBTTagCompound n=tag(s);if(!n.hasKey("donor"))n.setString("donor",p.getUniqueID().toString());if(PlayerState.level(p)<4)return new ActionResult<>(EnumActionResult.FAIL,s);n.setBoolean("opened",true);
   if(key.equals("awakening_eye")){if(!n.hasKey("readyAt"))n.setLong("readyAt",w.getTotalWorldTime()+1728000);if(w.getTotalWorldTime()>=n.getLong("readyAt")){RewardLedger.get(p).issue(p,"awakened_eye",1,"vale-eye-v1/"+n.getString("valeSerial"));consume(p,s,"medicalUsed");}else Scrolls.tell(p,"Awakening ticks remaining: "+(n.getLong("readyAt")-w.getTotalWorldTime()));}
   ArsenalAccess.save(p);return new ActionResult<>(EnumActionResult.SUCCESS,s);
  }
 }
}

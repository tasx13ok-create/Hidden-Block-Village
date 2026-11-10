package village.vale;
import java.util.*;import net.minecraft.entity.*;import net.minecraft.entity.player.*;import net.minecraft.entity.projectile.*;import net.minecraft.inventory.EntityEquipmentSlot;import net.minecraft.item.*;import net.minecraft.init.*;import net.minecraft.nbt.NBTTagCompound;import net.minecraft.potion.PotionEffect;import net.minecraft.util.*;import net.minecraft.util.math.*;import net.minecraft.world.World;import net.minecraftforge.common.util.EnumHelper;import net.minecraftforge.event.RegistryEvent;import net.minecraftforge.event.entity.living.*;import net.minecraftforge.event.entity.ProjectileImpactEvent;import net.minecraftforge.fml.common.Mod;import net.minecraftforge.fml.common.eventhandler.*;import net.minecraftforge.fml.common.gameevent.TickEvent;
/** Original powered garments with seven independently designed bounded engines. Legacy-specific powers remain separate obligations. */
@Mod.EventBusSubscriber(modid=ValeOrders.ID)
public final class OriginalFocusGear {
 private OriginalFocusGear(){}
 public static final class Spec {public final String key,title,role;public final EntityEquipmentSlot slot;public final Order order;Spec(String k,String t,EntityEquipmentSlot s,String r,Order o){key=k;title=t;slot=s;role=r;order=o;}}
 public static final Spec[] SPECS={ new Spec("focus_amber_wake_lens","Amber Wake Lens",EntityEquipmentSlot.HEAD,"absorb",Order.EMBER),
 new Spec("focus_birch_wake_lens","Birch Wake Lens",EntityEquipmentSlot.HEAD,"watch",Order.CURRENT),
 new Spec("focus_copper_wake_mantle","Copper Wake Mantle",EntityEquipmentSlot.CHEST,"ward",Order.GALE),
 new Spec("focus_dusk_wake_lens","Dusk Wake Lens",EntityEquipmentSlot.HEAD,"absorb",Order.FLINT),
 new Spec("focus_elm_wake_lens","Elm Wake Lens",EntityEquipmentSlot.HEAD,"mirror",Order.BLOOM),
 new Spec("focus_flint_wake_lens","Flint Wake Lens",EntityEquipmentSlot.HEAD,"ward",Order.RIME),
 new Spec("focus_gale_wake_lens","Gale Wake Lens",EntityEquipmentSlot.HEAD,"absorb",Order.STORM),
 new Spec("focus_heather_wake_lens","Heather Wake Lens",EntityEquipmentSlot.HEAD,"ward",Order.DUSK),
 new Spec("focus_ivory_wake_lens","Ivory Wake Lens",EntityEquipmentSlot.HEAD,"watch",Order.DAWN),
 new Spec("focus_juniper_wake_lens","Juniper Wake Lens",EntityEquipmentSlot.HEAD,"mirror",Order.EMBER),
 new Spec("focus_kiln_wake_lens","Kiln Wake Lens",EntityEquipmentSlot.HEAD,"mirror",Order.CURRENT),
 new Spec("focus_lantern_wake_lens","Lantern Wake Lens",EntityEquipmentSlot.HEAD,"watch",Order.GALE),
 new Spec("focus_moss_wake_lens","Moss Wake Lens",EntityEquipmentSlot.HEAD,"absorb",Order.FLINT),
 new Spec("focus_night_wake_lens","Night Wake Lens",EntityEquipmentSlot.HEAD,"mirror",Order.BLOOM),
 new Spec("focus_ochre_wake_lens","Ochre Wake Lens",EntityEquipmentSlot.HEAD,"mirror",Order.RIME),
 new Spec("focus_pearl_wake_lens","Pearl Wake Lens",EntityEquipmentSlot.HEAD,"mirror",Order.STORM),
 new Spec("focus_quartz_wake_lens","Quartz Wake Lens",EntityEquipmentSlot.HEAD,"absorb",Order.DUSK),
 new Spec("focus_reed_wake_lens","Reed Wake Lens",EntityEquipmentSlot.HEAD,"ward",Order.DAWN),
 new Spec("focus_slate_wake_lens","Slate Wake Lens",EntityEquipmentSlot.HEAD,"absorb",Order.EMBER),
 new Spec("focus_thistle_wake_mantle","Thistle Wake Mantle",EntityEquipmentSlot.CHEST,"ward",Order.CURRENT),
 new Spec("focus_amber_hollow_lens","Amber Hollow Lens",EntityEquipmentSlot.HEAD,"ward",Order.GALE),
 new Spec("focus_birch_hollow_greaves","Birch Hollow Greaves",EntityEquipmentSlot.LEGS,"ward",Order.FLINT),
 new Spec("focus_copper_hollow_lens","Copper Hollow Lens",EntityEquipmentSlot.HEAD,"ward",Order.BLOOM),
 new Spec("focus_dusk_hollow_mantle","Dusk Hollow Mantle",EntityEquipmentSlot.CHEST,"surge",Order.RIME),
 new Spec("focus_elm_hollow_mantle","Elm Hollow Mantle",EntityEquipmentSlot.CHEST,"surge",Order.STORM),
 new Spec("focus_flint_hollow_mantle","Flint Hollow Mantle",EntityEquipmentSlot.CHEST,"surge",Order.DUSK),
 new Spec("focus_gale_hollow_mantle","Gale Hollow Mantle",EntityEquipmentSlot.CHEST,"surge",Order.DAWN),
 new Spec("focus_heather_hollow_mantle","Heather Hollow Mantle",EntityEquipmentSlot.CHEST,"surge",Order.EMBER),
 new Spec("focus_ivory_hollow_mantle","Ivory Hollow Mantle",EntityEquipmentSlot.CHEST,"surge",Order.CURRENT),
 new Spec("focus_juniper_hollow_mantle","Juniper Hollow Mantle",EntityEquipmentSlot.CHEST,"surge",Order.GALE),
 new Spec("focus_kiln_hollow_mantle","Kiln Hollow Mantle",EntityEquipmentSlot.CHEST,"surge",Order.FLINT),
 new Spec("focus_lantern_hollow_mantle","Lantern Hollow Mantle",EntityEquipmentSlot.CHEST,"surge",Order.BLOOM),
 new Spec("focus_moss_hollow_mantle","Moss Hollow Mantle",EntityEquipmentSlot.CHEST,"ward",Order.RIME),
 new Spec("focus_night_hollow_greaves","Night Hollow Greaves",EntityEquipmentSlot.LEGS,"ward",Order.STORM),
 new Spec("focus_ochre_hollow_mantle","Ochre Hollow Mantle",EntityEquipmentSlot.CHEST,"ward",Order.DUSK),
 new Spec("focus_pearl_hollow_lens","Pearl Hollow Lens",EntityEquipmentSlot.HEAD,"watch",Order.DAWN),
 new Spec("focus_quartz_hollow_mantle","Quartz Hollow Mantle",EntityEquipmentSlot.CHEST,"surge",Order.EMBER),
 new Spec("focus_reed_hollow_lens","Reed Hollow Lens",EntityEquipmentSlot.HEAD,"surge",Order.CURRENT),
 new Spec("focus_slate_hollow_mantle","Slate Hollow Mantle",EntityEquipmentSlot.CHEST,"surge",Order.GALE),
 new Spec("focus_thistle_hollow_lens","Thistle Hollow Lens",EntityEquipmentSlot.HEAD,"surge",Order.FLINT),
 new Spec("focus_amber_crest_lens","Amber Crest Lens",EntityEquipmentSlot.HEAD,"absorb",Order.BLOOM),
 new Spec("focus_birch_crest_lens","Birch Crest Lens",EntityEquipmentSlot.HEAD,"watch",Order.RIME),
 new Spec("focus_copper_crest_mantle","Copper Crest Mantle",EntityEquipmentSlot.CHEST,"surge",Order.STORM),
 new Spec("focus_dusk_crest_lens","Dusk Crest Lens",EntityEquipmentSlot.HEAD,"surge",Order.DUSK),
 new Spec("focus_elm_crest_greaves","Elm Crest Greaves",EntityEquipmentSlot.LEGS,"surge",Order.DAWN),
 new Spec("focus_flint_crest_mantle","Flint Crest Mantle",EntityEquipmentSlot.CHEST,"surge",Order.EMBER),
 new Spec("focus_gale_crest_mantle","Gale Crest Mantle",EntityEquipmentSlot.CHEST,"mend",Order.CURRENT),
 new Spec("focus_heather_crest_mantle","Heather Crest Mantle",EntityEquipmentSlot.CHEST,"ward",Order.GALE),
 new Spec("focus_ivory_crest_mantle","Ivory Crest Mantle",EntityEquipmentSlot.CHEST,"ward",Order.FLINT),
 new Spec("focus_juniper_crest_mantle","Juniper Crest Mantle",EntityEquipmentSlot.CHEST,"surge",Order.BLOOM),
 new Spec("focus_kiln_crest_lens","Kiln Crest Lens",EntityEquipmentSlot.HEAD,"surge",Order.RIME),
 new Spec("focus_lantern_crest_greaves","Lantern Crest Greaves",EntityEquipmentSlot.LEGS,"surge",Order.STORM),
 new Spec("focus_moss_crest_mantle","Moss Crest Mantle",EntityEquipmentSlot.CHEST,"ward",Order.DUSK),
 new Spec("focus_night_crest_lens","Night Crest Lens",EntityEquipmentSlot.HEAD,"watch",Order.DAWN),
 new Spec("focus_ochre_crest_mantle","Ochre Crest Mantle",EntityEquipmentSlot.CHEST,"ward",Order.EMBER),
 new Spec("focus_pearl_crest_lens","Pearl Crest Lens",EntityEquipmentSlot.HEAD,"mirror",Order.CURRENT),
 new Spec("focus_quartz_crest_mantle","Quartz Crest Mantle",EntityEquipmentSlot.CHEST,"absorb",Order.GALE),
 new Spec("focus_reed_crest_lens","Reed Crest Lens",EntityEquipmentSlot.HEAD,"absorb",Order.FLINT),
 new Spec("focus_slate_crest_greaves","Slate Crest Greaves",EntityEquipmentSlot.LEGS,"absorb",Order.BLOOM),
 new Spec("focus_thistle_crest_lens","Thistle Crest Lens",EntityEquipmentSlot.HEAD,"absorb",Order.RIME),
 new Spec("focus_amber_haven_lens","Amber Haven Lens",EntityEquipmentSlot.HEAD,"absorb",Order.STORM),
 new Spec("focus_birch_haven_mantle","Birch Haven Mantle",EntityEquipmentSlot.CHEST,"ward",Order.DUSK),
 new Spec("focus_copper_haven_lens","Copper Haven Lens",EntityEquipmentSlot.HEAD,"ward",Order.DAWN),
 new Spec("focus_dusk_haven_greaves","Dusk Haven Greaves",EntityEquipmentSlot.LEGS,"ward",Order.EMBER),
 new Spec("focus_elm_haven_lens","Elm Haven Lens",EntityEquipmentSlot.HEAD,"ward",Order.CURRENT),
 new Spec("focus_flint_haven_mantle","Flint Haven Mantle",EntityEquipmentSlot.CHEST,"watch",Order.GALE),
 new Spec("focus_gale_haven_lens","Gale Haven Lens",EntityEquipmentSlot.HEAD,"watch",Order.FLINT),
 new Spec("focus_heather_haven_greaves","Heather Haven Greaves",EntityEquipmentSlot.LEGS,"watch",Order.BLOOM),
 new Spec("focus_ivory_haven_mantle","Ivory Haven Mantle",EntityEquipmentSlot.CHEST,"ward",Order.RIME),
 new Spec("focus_juniper_haven_treads","Juniper Haven Treads",EntityEquipmentSlot.FEET,"ward",Order.STORM),
 new Spec("focus_kiln_haven_greaves","Kiln Haven Greaves",EntityEquipmentSlot.LEGS,"ward",Order.DUSK),
 new Spec("focus_lantern_haven_mantle","Lantern Haven Mantle",EntityEquipmentSlot.CHEST,"ward",Order.DAWN),
 new Spec("focus_moss_haven_lens","Moss Haven Lens",EntityEquipmentSlot.HEAD,"ward",Order.EMBER),
 new Spec("focus_night_haven_mantle","Night Haven Mantle",EntityEquipmentSlot.CHEST,"ward",Order.CURRENT),
 new Spec("focus_ochre_haven_mantle","Ochre Haven Mantle",EntityEquipmentSlot.CHEST,"shade",Order.GALE),
 new Spec("focus_pearl_haven_mantle","Pearl Haven Mantle",EntityEquipmentSlot.CHEST,"shade",Order.FLINT),
 };
 public static NBTTagCompound state(EntityPlayerMP p){NBTTagCompound root=PlayerState.data(p);if(!root.hasKey("originalFocus",10))root.setTag("originalFocus",new NBTTagCompound());return root.getCompoundTag("originalFocus");}
 public static void register(RegistryEvent.Register<Item> e){ItemArmor.ArmorMaterial material=EnumHelper.addArmorMaterial("VALE_FOCUS_GARMENTS","minecraft:iron",32,new int[]{2,5,6,2},12,SoundEvents.ITEM_ARMOR_EQUIP_IRON,1);material.setRepairItem(new ItemStack(Items.IRON_INGOT));for(Spec s:SPECS)Gear.add(e,s.key,new Focus(s,material));Gear.add(e,"focus_talisman",new Talisman());}
 public static Focus active(EntityPlayerMP p){NBTTagCompound n=state(p);if(!n.getBoolean("active"))return null;String key=n.getString("key");for(EntityEquipmentSlot slot:new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET}){ItemStack worn=p.getItemStackFromSlot(slot);if(worn.getItem() instanceof Focus&&((Focus)worn.getItem()).spec.key.equals(key)&&RewardLedger.get(p).valid(p,worn))return(Focus)worn.getItem();}return null;}
 public static boolean toggle(EntityPlayerMP p,ItemStack control,boolean cycle){if(!p.isEntityAlive()||p.isSpectator()||control.getItem()!=Gear.ITEMS.get("focus_talisman")||!RewardLedger.get(p).valid(p,control))return false;List<Focus> worn=new ArrayList<>();for(EntityEquipmentSlot slot:new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET}){ItemStack stack=p.getItemStackFromSlot(slot);if(stack.getItem() instanceof Focus&&RewardLedger.get(p).valid(p,stack))worn.add((Focus)stack.getItem());}if(worn.isEmpty())return Adventures.tell(p,"Wear your issued focus garment first. No resources spent.");NBTTagCompound n=state(p);int index=0;for(int i=0;i<worn.size();i++)if(worn.get(i).spec.key.equals(n.getString("key")))index=i;if(cycle){index=(index+1)%worn.size();n.setBoolean("active",false);}else n.setBoolean("active",!n.getBoolean("active"));n.setString("key",worn.get(index).spec.key);Adventures.save(p);return Adventures.tell(p,worn.get(index).spec.title+" | "+(n.getBoolean("active")?"active":"resting")+". Sneak-use selects another worn focus.",true);}
 static boolean pay(EntityPlayerMP p,int cost,int cooldown){NBTTagCompound n=state(p);long now=Adventures.now(p);if(now<n.getLong("ready")||PlayerState.resolve(p)<cost)return false;PlayerState.setResolve(p,PlayerState.resolve(p)-cost);n.setLong("ready",now+cooldown);return true;}
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase!=TickEvent.Phase.END||e.player.world.isRemote||!(e.player instanceof EntityPlayerMP)||e.player.ticksExisted%20!=0)return;EntityPlayerMP p=(EntityPlayerMP)e.player;if(!p.isEntityAlive()||p.isSpectator())return;Focus f=active(p);if(f==null)return;String role=f.spec.role;
  if(role.equals("watch")&&pay(p,2,20)){p.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION,240,0));for(EntityLivingBase t:ArsenalAccess.targets(p,10,false,6))t.addPotionEffect(new PotionEffect(MobEffects.GLOWING,35,0));}
  if(role.equals("mend")&&p.getHealth()<p.getMaxHealth()*.6F&&pay(p,5,100))p.heal(2);
  if(role.equals("shade")&&p.isSneaking()&&pay(p,2,20))p.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY,30,0));
 }
 @SubscribeEvent(priority=EventPriority.LOWEST)public static void hurt(LivingHurtEvent e){if(e.isCanceled()||e.getAmount()<=0||e.getEntityLiving().world.isRemote)return;
  if(e.getEntityLiving() instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)e.getEntityLiving();Focus f=active(p);Entity attacker=e.getSource().getTrueSource();if(f!=null&&attacker instanceof EntityLivingBase&&ArsenalAccess.allowed(p,(EntityLivingBase)attacker)){
   if(f.spec.role.equals("ward")&&pay(p,5,80))e.setAmount(e.getAmount()*.8F);
   if(f.spec.role.equals("mirror")){Vec3d at=p.getPositionVector().subtract(p.getLookVec().scale(3));BlockPos block=new BlockPos(at);if(p.world.isBlockLoaded(block)&&p.world.getWorldBorder().contains(block)&&p.world.getBlockState(block.down()).isSideSolid(p.world,block.down(),EnumFacing.UP)&&p.world.getCollisionBoxes(p,p.getEntityBoundingBox().offset(at.subtract(p.getPositionVector()))).isEmpty()&&!p.world.containsAnyLiquid(p.getEntityBoundingBox().offset(at.subtract(p.getPositionVector())))&&p.world.rayTraceBlocks(p.getPositionEyes(1),at.add(0,p.getEyeHeight(),0),false,true,false)==null&&pay(p,25,400)){p.connection.setPlayerLocation(at.x,at.y,at.z,p.rotationYaw,p.rotationPitch);e.setCanceled(true);}}
  }}
  if(e.isCanceled())return;
  Entity source=e.getSource().getTrueSource();if(source instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)source;Focus f=active(p);if(f!=null&&f.spec.role.equals("surge")&&ArsenalAccess.allowed(p,e.getEntityLiving())&&pay(p,8,80))e.setAmount(Math.min(32,e.getAmount()+2));}
 }
 @SubscribeEvent(priority=EventPriority.LOWEST)public static void projectile(ProjectileImpactEvent e){if(e.isCanceled()||e.getEntity().world.isRemote||e.getRayTraceResult()==null||!(e.getRayTraceResult().entityHit instanceof EntityPlayerMP))return;EntityPlayerMP p=(EntityPlayerMP)e.getRayTraceResult().entityHit;Focus f=active(p);if(f==null||!f.spec.role.equals("absorb"))return;Entity projectile=e.getEntity();Entity shooter=projectile instanceof EntityArrow?((EntityArrow)projectile).shootingEntity:projectile instanceof EntityThrowable?((EntityThrowable)projectile).getThrower():projectile instanceof EntityFireball?((EntityFireball)projectile).shootingEntity:null;if(!(shooter instanceof EntityLivingBase)||!ArsenalAccess.allowed(p,(EntityLivingBase)shooter)||!pay(p,10,100))return;e.setCanceled(true);projectile.setDead();}
 public static final class Focus extends ItemArmor implements Gear.Ordered {public final Spec spec;Focus(Spec s,ArmorMaterial m){super(m,0,s.slot);spec=s;setCreativeTab(net.minecraft.creativetab.CreativeTabs.COMBAT);}public Order order(){return spec.order;}@Override public String getItemStackDisplayName(ItemStack stack){return spec.title;}@Override public String getArmorTexture(ItemStack stack,Entity e,EntityEquipmentSlot slot,String type){return "minecraft:textures/models/armor/iron_layer_"+(slot==EntityEquipmentSlot.LEGS?2:1)+".png";}@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)@Override public void addInformation(ItemStack s,World w,List<String> lines,net.minecraft.client.util.ITooltipFlag flag){lines.add("Original focus: "+spec.role+". Use an issued Focus Talisman while wearing.");lines.add("One active worn focus. Resolve and cooldowns are shared.");lines.add("Visual design pending; this is an original adaptation.");}}
 public static final class Talisman extends Item {@Override public ActionResult<ItemStack> onItemRightClick(World w,EntityPlayer p,EnumHand hand){boolean result=w.isRemote||p instanceof EntityPlayerMP&&toggle((EntityPlayerMP)p,p.getHeldItem(hand),p.isSneaking());return new ActionResult<>(result?EnumActionResult.SUCCESS:EnumActionResult.FAIL,p.getHeldItem(hand));}Talisman(){setMaxStackSize(1);setCreativeTab(net.minecraft.creativetab.CreativeTabs.COMBAT);}@Override public String getItemStackDisplayName(ItemStack s){return "Focus Talisman";}}
}

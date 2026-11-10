package village.vale.culinary;
import net.minecraft.entity.*;import net.minecraft.entity.player.*;import net.minecraft.init.Items;import net.minecraft.item.*;import net.minecraft.nbt.NBTTagCompound;import net.minecraft.util.*;import net.minecraft.world.World;import village.vale.PlayerState;
/** Ordinary consumables, with saved per-player shared pill recovery, no ownership serial. */
public final class KitchenTonic extends Item {
 final int tier;KitchenTonic(int t){tier=t;setMaxStackSize(16);}
 @Override public EnumAction getItemUseAction(ItemStack s){return tier>=4?EnumAction.DRINK:EnumAction.EAT;}
 @Override public int getMaxItemUseDuration(ItemStack s){return 32;}
 @Override public ActionResult<ItemStack> onItemRightClick(World w,EntityPlayer p,EnumHand hand){ItemStack s=p.getHeldItem(hand);NBTTagCompound n=PlayerState.data(p);long now=w.getTotalWorldTime();boolean ok=tier==5?now>=n.getLong("kitchenAntidoteReady")&&p.isPotionActive(net.minecraft.init.MobEffects.POISON):tier==4?now>=n.getLong("kitchenXpReady"):now>=n.getLong("kitchenPillReady")&&PlayerState.resolve(p)<100;if(ok)p.setActiveHand(hand);return new ActionResult<>(ok?EnumActionResult.SUCCESS:EnumActionResult.FAIL,s);}
 @Override public ItemStack onItemUseFinish(ItemStack s,World w,EntityLivingBase eater){if(!(eater instanceof EntityPlayer)||w.isRemote)return s;EntityPlayer p=(EntityPlayer)eater;NBTTagCompound n=PlayerState.data(p);long now=w.getTotalWorldTime();String timer=tier==5?"kitchenAntidoteReady":tier==4?"kitchenXpReady":"kitchenPillReady";if(now<n.getLong(timer)||tier<4&&PlayerState.resolve(p)>=100||tier==5&&!p.isPotionActive(net.minecraft.init.MobEffects.POISON))return s;
  n.setLong(timer,now+(tier==5?100:tier==4?2400:tier==1?600:tier==2?1200:2400));if(tier==5)p.removePotionEffect(net.minecraft.init.MobEffects.POISON);else if(tier==4)PlayerState.addXp(p,15);else PlayerState.setResolve(p,Math.min(100,PlayerState.resolve(p)+new int[]{0,15,30,50}[tier]));
  if(!p.capabilities.isCreativeMode)s.shrink(1);if(tier>=4&&!p.capabilities.isCreativeMode){ItemStack bottle=new ItemStack(Items.GLASS_BOTTLE);if(s.isEmpty())return bottle;if(!p.inventory.addItemStackToInventory(bottle))p.dropItem(bottle,false);}return s;
 }
}

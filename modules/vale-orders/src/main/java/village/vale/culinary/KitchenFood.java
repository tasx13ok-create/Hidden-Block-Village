package village.vale.culinary;
import java.util.*;import net.minecraft.entity.*;import net.minecraft.entity.player.*;import net.minecraft.init.*;import net.minecraft.item.*;import net.minecraft.potion.PotionEffect;import net.minecraft.util.*;import net.minecraft.world.World;import village.vale.PlayerState;
/** Hunger and saturation always work; bounded meal bonuses share a saved recovery timer. */
public final class KitchenFood extends ItemFood {
 final String key;final boolean bowl,bottle;
 KitchenFood(String k,int index){super(index<6?new int[]{3,2,4,1,4,5}[index]:k.equals("sake")?0:k.equals("ramen")?10:6+(index%3),index<6?.4f:.7f,false);key=k;bowl=k.endsWith("soup")||Arrays.asList("okayu","donburi","tofu_curry","ramen","zenzai","recovery_curry").contains(k);bottle=k.equals("soy_milk")||k.equals("soy_sauce")||k.equals("sake");setMaxStackSize(16);if(bowl||bottle)setContainerItem(bowl?Items.BOWL:Items.GLASS_BOTTLE);if(bottle)setAlwaysEdible();}
 @Override public EnumAction getItemUseAction(ItemStack s){return bottle||key.endsWith("soup")?EnumAction.DRINK:EnumAction.EAT;}
 @Override protected void onFoodEaten(ItemStack s,World w,EntityPlayer p){super.onFoodEaten(s,w,p);if(w.isRemote)return;net.minecraft.nbt.NBTTagCompound n=PlayerState.data(p);long now=w.getTotalWorldTime();if(key.equals("soy_milk")){p.removePotionEffect(MobEffects.POISON);n.setLong("kitchenPoisonUntil",now+200);}if(key.equals("sake"))p.addPotionEffect(new PotionEffect(MobEffects.NAUSEA,200,0));if(now<n.getLong("kitchenMealReady"))return;
  if(key.equals("fortune_fruit")&&now>=n.getLong("kitchenXpReady")){PlayerState.addXp(p,5);n.setLong("kitchenXpReady",now+2400);}
  if(key.equals("recovery_curry"))p.heal(3);
  if(Arrays.asList("suika","dango","anpan","zenzai","daifuku_mochi","tofu_soup","miso_soup","onigiri","tofu_curry","okonomiyaki","yakisoba","ramen").contains(key))p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION,80,0));
  if(Arrays.asList("yokan","miso_soup","yakisoba","ramen").contains(key))p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE,160,0));
  if(Arrays.asList("mochi","daifuku_mochi","donburi").contains(key))p.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION,200,0));
  if(Arrays.asList("donburi","okonomiyaki","yakisoba","ramen").contains(key))p.addPotionEffect(new PotionEffect(MobEffects.STRENGTH,160,0));
  if(key.equals("tofu_soup"))p.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE,200,0));
  if(key.equals("dashi_soup"))p.addPotionEffect(new PotionEffect(MobEffects.WATER_BREATHING,400,0));
  if(key.equals("okayu"))p.addPotionEffect(new PotionEffect(MobEffects.HEALTH_BOOST,200,0));
  if(key.equals("yakisoba")||key.equals("ramen"))p.getFoodStats().addStats(1,.1f);
  if(p instanceof EntityPlayerMP)village.vale.bridge.MoveCost.restore((EntityPlayerMP)p,key.equals("ramen")?12:4);n.setLong("kitchenMealReady",now+600);
 }
 @Override public ItemStack onItemUseFinish(ItemStack s,World w,EntityLivingBase e){ItemStack out=super.onItemUseFinish(s,w,e);if((!bowl&&!bottle)||!(e instanceof EntityPlayer)||((EntityPlayer)e).capabilities.isCreativeMode)return out;ItemStack returned=new ItemStack(bowl?Items.BOWL:Items.GLASS_BOTTLE);if(out.isEmpty())return returned;EntityPlayer p=(EntityPlayer)e;if(!w.isRemote&&!p.inventory.addItemStackToInventory(returned))p.dropItem(returned,false);return out;}
}

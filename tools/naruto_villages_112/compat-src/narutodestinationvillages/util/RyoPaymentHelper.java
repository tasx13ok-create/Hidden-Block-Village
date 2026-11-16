package narutodestinationvillages.util;
import java.util.*;import net.minecraft.item.*;import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;import net.minecraftforge.fml.common.registry.ForgeRegistries;
/** Original transaction planner. Native Ryo notes and retained base-nine coins share exact integer Ryo. */
public final class RyoPaymentHelper {
 public static final int BRONZE_VALUE=1,SILVER_VALUE=9,GOLD_VALUE=81,DIAMOND_VALUE=729,EMERALD_VALUE=6561;
 private static final class Coin {final Item item;final int value;Coin(Item i,int v){item=i;value=v;}}
 private static List<Coin> coins(){List<Coin> out=new ArrayList<>();
  add(out,"variedcommodities:coin_bronze",1);add(out,"variedcommodities:coin_iron",9);add(out,"variedcommodities:coin_gold",81);add(out,"variedcommodities:coin_diamond",729);add(out,"variedcommodities:coin_emerald",6561);
  add(out,"narutomod:ryo_100",100);add(out,"narutomod:ryo_1000",1000);add(out,"narutomod:ryo_10000",10000);add(out,"kabutoaddon:ryo_100000",100000);add(out,"narutomod:ryo_1_m",1000000);
  Collections.sort(out,Comparator.comparingInt(c->c.value));return out;}
 private static void add(List<Coin> coins,String id,int value){Item item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));if(item!=null)coins.add(new Coin(item,value));}
 public static boolean isAvailable(){for(Coin c:coins())if(c.value==1)return true;return false;}
 public static long getPlayerRyoBalance(EntityPlayerMP player){long total=0;List<Coin> coins=coins();for(int slot=0;slot<player.inventory.getSizeInventory();slot++){ItemStack stack=player.inventory.getStackInSlot(slot);if(stack.isEmpty())continue;for(Coin coin:coins)if(stack.getItem()==coin.item){total+=(long)stack.getCount()*coin.value;break;}}return total;}
 public static boolean canAfford(EntityPlayerMP player,int amount){return amount<=0||getPlayerRyoBalance(player)>=amount;}
 public static boolean takePayment(EntityPlayerMP player,int amount){
  if(amount<=0)return true;if(player==null||player.world.isRemote||!player.getServer().isCallingFromMinecraftThread())return false;
  List<Coin> coins=coins();if(!isAvailable()||!canAfford(player,amount))return false;
  ItemStack[] before=new ItemStack[player.inventory.getSizeInventory()],plan=new ItemStack[before.length];
  for(int slot=0;slot<before.length;slot++){before[slot]=player.inventory.getStackInSlot(slot).copy();plan[slot]=before[slot].copy();}
  long remaining=amount;
  for(Coin coin:coins){for(int slot=0;slot<plan.length&&remaining>0;slot++){ItemStack stack=plan[slot];if(stack.isEmpty()||stack.getItem()!=coin.item)continue;int count=(int)Math.min(stack.getCount(),(remaining+coin.value-1L)/coin.value);stack.shrink(count);remaining-=(long)count*coin.value;}}
  if(remaining>0)return false;long change=-remaining;
  for(int index=coins.size()-1;index>=0;index--){Coin coin=coins.get(index);int count=(int)(change/coin.value);if(count==0)continue;if(!insert(plan,new ItemStack(coin.item),count))return false;change-=(long)count*coin.value;}
  if(change!=0)return false;
  // No item has been mutated in the live inventory before the complete plan fits.
  for(int slot=0;slot<before.length;slot++)if(!ItemStack.areItemStacksEqual(before[slot],player.inventory.getStackInSlot(slot)))return false;
  for(int slot=0;slot<plan.length;slot++)if(!ItemStack.areItemStacksEqual(before[slot],plan[slot]))player.inventory.setInventorySlotContents(slot,plan[slot]);
  player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();return true;
 }
 private static boolean insert(ItemStack[] plan,ItemStack coin,int count){int cap=Math.min(64,coin.getMaxStackSize());
  for(int slot=0;slot<Math.min(36,plan.length)&&count>0;slot++){ItemStack stack=plan[slot];if(stack.isEmpty()||!ItemStack.areItemsEqual(stack,coin)||!ItemStack.areItemStackTagsEqual(stack,coin))continue;int add=Math.min(count,Math.max(0,cap-stack.getCount()));stack.grow(add);count-=add;}
  for(int slot=0;slot<Math.min(36,plan.length)&&count>0;slot++)if(plan[slot].isEmpty()){int add=Math.min(count,cap);plan[slot]=coin.copy();plan[slot].setCount(add);count-=add;}
  return count==0;
 }
 public static String formatRyoAmount(int value){return value==0?"Free":String.format("%,d Ryo",value);}
 public static String formatRyoAmountDetailed(int value){if(value==0)return "Free";StringBuilder text=new StringBuilder();int remaining=value;int[] amounts={6561,729,81,9,1};String[] names={"Emerald","Diamond","Gold","Silver","Bronze"};for(int i=0;i<amounts.length;i++){int n=remaining/amounts[i];remaining%=amounts[i];if(n>0){if(text.length()>0)text.append(' ');text.append(n).append(' ').append(names[i]);if(n>1)text.append('s');}}return text+String.format(" (%,d total)",value);}
}

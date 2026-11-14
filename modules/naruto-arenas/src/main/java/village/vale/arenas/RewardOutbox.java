package village.vale.arenas;

import com.google.gson.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionUtils;

/** Native/vanilla loot, separate from Core serial gear. Inventory and receipt share one player save. */
final class RewardOutbox {
 private RewardOutbox(){}
 static ItemStack choice(String arena,int choice){
  if(choice<0||choice>1)throw new IllegalArgumentException("Choose0or1");ItemStack stack;
  if(arena.contains("forest")){stack=choice==0?new ItemStack(Items.SHIELD):new ItemStack(Items.GOLDEN_APPLE,8);if(choice==0)stack.addEnchantment(Enchantments.UNBREAKING,3);}
  else if(arena.contains("cloud")){stack=choice==0?new ItemStack(Items.IRON_BOOTS):PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM),PotionTypes.STRONG_SWIFTNESS);if(choice==0){stack.addEnchantment(Enchantments.FEATHER_FALLING,4);stack.addEnchantment(Enchantments.UNBREAKING,3);}}
  else if(arena.contains("sand")){stack=choice==0?new ItemStack(Items.BOW):new ItemStack(Items.ARROW,64);if(choice==0)stack.addEnchantment(Enchantments.POWER,3);}
  else if(arena.contains("mist")){stack=choice==0?new ItemStack(Items.IRON_HELMET):PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM),PotionTypes.LONG_WATER_BREATHING);if(choice==0){stack.addEnchantment(Enchantments.RESPIRATION,3);stack.addEnchantment(Enchantments.AQUA_AFFINITY,1);}}
  else {stack=choice==0?new ItemStack(Items.DIAMOND_CHESTPLATE):new ItemStack(Items.DIAMOND_SWORD);stack.addEnchantment(choice==0?Enchantments.PROTECTION:Enchantments.SHARPNESS,choice==0?3:4);}
  if(stack.isEmpty()||stack.getItem().getRegistryName()==null)throw new IllegalStateException("Reward provider absent");return stack;
 }
 static NBTTagCompound receipts(EntityPlayerMP p){NBTTagCompound data=p.getEntityData();if(!data.hasKey("PlayerPersisted",10))data.setTag("PlayerPersisted",new NBTTagCompound());NBTTagCompound persisted=data.getCompoundTag("PlayerPersisted");if(!persisted.hasKey("HbvArenaReceipts",10))persisted.setTag("HbvArenaReceipts",new NBTTagCompound());return persisted.getCompoundTag("HbvArenaReceipts");}
 static void flush(EntityPlayerMP p){p.getServer().getWorld(0).getSaveHandler().getPlayerNBTManager().writePlayerData(p);}
 static boolean deliver(EntityPlayerMP p,JsonObject run,int choice){
  if(!ArenaDirector.eligible(p,false))throw new IllegalStateException("Only the living registered survival participant may claim");
  if(choice<0||choice>1)throw new IllegalArgumentException("Choose0or1");
  JsonObject member=run.getAsJsonObject("members").getAsJsonObject(p.getUniqueID().toString());if(member==null)throw new IllegalArgumentException("Wrong party");
  if(member.has("reward_mode")&&!member.get("reward_mode").getAsString().equals("native"))throw new IllegalStateException("Reward mode already selected");
  String token=ArenaDirector.text(run,"run")+":"+p.getUniqueID();NBTTagCompound receipts=receipts(p);
  if(receipts.hasKey(token)){member.addProperty("outbox","DELIVERED");ArenaDirector.store().dirty();return true;}
  if(!RunRules.paid(run.get("preview").getAsBoolean(),member.get("earned").getAsBoolean(),member.get("contribution").getAsDouble(),ArenaDirector.text(run,"state").equals("REWARD_PENDING"),false))throw new IllegalStateException("No earned terminal entitlement");
  if(receipts.getKeySet().size()>=8192)throw new IllegalStateException("Receipt capacity reached; retain ledger and request archival migration");
  // A free slot is deliberately required: no partial merge, world drop or deletion.
  int slot=p.inventory.getFirstEmptyStack();if(slot<0){member.addProperty("outbox","PENDING_FULL_INVENTORY");ArenaDirector.store().dirty();return false;}
  ItemStack stack=choice(ArenaDirector.text(run,"arena"),choice);NBTTagCompound tag=stack.hasTagCompound()?stack.getTagCompound():new NBTTagCompound();tag.setString("HbvArenaRewardReceipt",token);stack.setTagCompound(tag);
  member.addProperty("reward_mode","native");member.addProperty("outbox","PENDING");member.addProperty("choice",choice);ArenaDirector.store().dirty();p.getServer().getWorld(0).getPerWorldStorage().saveAllData();
  p.inventory.setInventorySlotContents(slot,stack);receipts.setString(token,"DELIVERED");
  // The token and inventory are persisted in the same player's atomic NBT file.
  // Reconciliation of a PENDING world record checks that authoritative token.
  flush(p);member.addProperty("outbox","DELIVERED");ArenaDirector.store().dirty();p.inventoryContainer.detectAndSendChanges();return true;
 }
}

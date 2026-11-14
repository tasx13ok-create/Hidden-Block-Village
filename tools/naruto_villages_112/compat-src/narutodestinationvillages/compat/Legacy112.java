package narutodestinationvillages.compat;
import net.minecraft.inventory.*;import net.minecraft.item.ItemStack;import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketOpenWindow;import net.minecraft.util.text.TextComponentString;
import net.minecraft.entity.Entity;import net.minecraft.world.World;import net.minecraft.item.Item;
import net.minecraft.init.SoundEvents;import net.minecraft.util.*;import net.minecraftforge.fml.common.registry.ForgeRegistries;
/** Original adapter for the actual 1.7-null / 1.12-EMPTY boundary. */
public final class Legacy112 {
 private Legacy112(){}
 public static ItemStack nullable(ItemStack stack){return stack==null||stack.isEmpty()?null:stack;}
 public static ItemStack present(ItemStack stack){return stack==null?ItemStack.EMPTY:stack;}
 public static ItemStack stack(IInventory inventory,int slot){return nullable(inventory.getStackInSlot(slot));}
 public static ItemStack stack(Slot slot){return nullable(slot.getStack());}
 public static ItemStack cursor(net.minecraft.entity.player.InventoryPlayer inventory){return nullable(inventory.getItemStack());}
 public static ItemStack readStack(NBTTagCompound n){return nullable(new ItemStack(n));}
 public static boolean equal(ItemStack a,ItemStack b){return ItemStack.areItemStacksEqual(present(a),present(b));}
 public static boolean sameItem(ItemStack a,ItemStack b){return ItemStack.areItemsEqual(present(a),present(b));}
 public static boolean sameTags(ItemStack a,ItemStack b){return ItemStack.areItemStackTagsEqual(present(a),present(b));}
 public static SPacketOpenWindow openWindow(int id,int type,String title,int size,boolean custom){
  if(type!=0)throw new IllegalArgumentException("Unrecognized legacy village window type "+type);
  return new SPacketOpenWindow(id,"minecraft:container",new TextComponentString(title),size);
 }
 public static void sound(World world,Entity entity,String name,float volume,float pitch){SoundEvent sound;
  if(name.equals("random.click")||name.equals("gui.button.press"))sound=SoundEvents.UI_BUTTON_CLICK;
  else if(name.equals("note.bass"))sound=SoundEvents.BLOCK_NOTE_BASS;
  else if(name.equals("mob.endermen.portal"))sound=SoundEvents.ENTITY_ENDERMEN_TELEPORT;
  else throw new IllegalArgumentException("Unrecognized legacy village sound "+name);
  world.playSound(null,entity.posX,entity.posY,entity.posZ,sound,SoundCategory.PLAYERS,volume,pitch);
 }
 public static int height(World world,int x,int z){return world.getHeight(x,z);}
 public static Item findItem(String mod,String name){return NativeRewardItems.supports(mod+":"+name)?net.minecraft.init.Items.PAPER:ForgeRegistries.ITEMS.getValue(new ResourceLocation(mod,name));}
}

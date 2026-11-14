package narutodestinationvillages.compat;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.narutomod.item.ItemJutsu;

/** Original JP learning flow through the held item's public NarutoMod API. */
public final class NativeLearning {
 public static final class Choice {
  public final ItemJutsu.JutsuEnum technique;public final int cost;
  private Choice(ItemJutsu.JutsuEnum t){technique=t;switch(t.rank){case 'S':cost=20;break;case 'A':cost=10;break;case 'B':cost=5;break;case 'C':cost=3;break;default:cost=1;}}
 }
 private NativeLearning(){}
 private static ItemStack plan(ItemStack held,ItemJutsu.JutsuEnum technique){
  if(held.isEmpty()||!(held.getItem() instanceof ItemJutsu.Base))return null;ItemStack copy=held.copy();ItemJutsu.Base item=(ItemJutsu.Base)copy.getItem();
  try{item.enableJutsu(copy,technique,true);item.setCurrentJutsu(copy,technique);return ItemJutsu.getCurrentJutsu(copy)==technique&&item.isJutsuEnabled(copy,technique)?copy:null;}
  catch(IndexOutOfBoundsException|IllegalArgumentException unsupported){return null;}
 }
 public static List<Choice> choices(EntityPlayerMP player){
  ItemStack held=player.getHeldItemMainhand();if(held.isEmpty()||!(held.getItem() instanceof ItemJutsu.Base)||ItemJutsu.getOwnerMatchingItemstack(player,held.getItem())!=held)return Collections.emptyList();
  Set<ItemJutsu.JutsuEnum> candidates=new LinkedHashSet<>();
  for(Class<?> type=held.getItem().getClass();type!=null;type=type.getSuperclass())for(Class<?> owner=type.getEnclosingClass();owner!=null;owner=owner.getEnclosingClass())for(java.lang.reflect.Field field:owner.getFields())if(field.getType()==ItemJutsu.JutsuEnum.class&&java.lang.reflect.Modifier.isStatic(field.getModifiers()))try{Object value=field.get(null);if(value instanceof ItemJutsu.JutsuEnum)candidates.add((ItemJutsu.JutsuEnum)value);}catch(IllegalAccessException inaccessible){}
  ItemJutsu.JutsuEnum current=ItemJutsu.getCurrentJutsu(held);if(current!=null)candidates.add(current);
  List<Choice> out=new ArrayList<>();for(ItemJutsu.JutsuEnum technique:candidates)if(plan(held,technique)!=null)out.add(new Choice(technique));return Collections.unmodifiableList(out);
 }
 public static boolean learned(ItemStack held,Choice choice){if(held.isEmpty()||!(held.getItem() instanceof ItemJutsu.Base))return false;ItemJutsu.Base item=(ItemJutsu.Base)held.getItem();return item.isJutsuEnabled(held,choice.technique)&&item.getJutsuXp(held,choice.technique)>=item.getRequiredXp(held,choice.technique);}
 public static boolean learn(EntityPlayerMP player,Choice choice){
  if(player==null||choice==null||player.world.isRemote||player.getServer()==null||!player.getServer().isCallingFromMinecraftThread())return false;
  ItemStack held=player.getHeldItemMainhand();if(held.isEmpty()||!(held.getItem() instanceof ItemJutsu.Base)||ItemJutsu.getOwnerMatchingItemstack(player,held.getItem())!=held||learned(held,choice))return false;
  long points=NativeTraining.balance(player,"jutsu");if(points<choice.cost)return false;ItemStack snapshot=held.copy(),next=plan(held,choice.technique);if(next==null)return false;
  ItemJutsu.Base item=(ItemJutsu.Base)next.getItem();int required=item.getRequiredXp(next,choice.technique),current=item.getJutsuXp(next,choice.technique);if(required<0||current<0)return false;
  if(current<required)item.addJutsuXp(next,choice.technique,required-current);if(!learned(next,choice))return false;
  if(player.getHeldItemMainhand()!=held||!ItemStack.areItemStacksEqual(held,snapshot)||NativeTraining.balance(player,"jutsu")!=points)return false;
  NativeTraining.data(player).setLong("jutsuPoints",points-choice.cost);held.setTagCompound(next.getTagCompound()==null?null:next.getTagCompound().copy());player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();return true;
 }
}

package narutodestinationvillages.compat;
import java.util.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.play.server.SPacketOpenWindow;
import net.minecraft.util.text.TextComponentString;

/** Original locked chest menu; all actions resolve on the server against the current owned container. */
public final class NativeTrainingMenu extends Container {
 private final EntityPlayerMP viewer;private final InventoryBasic icons=new InventoryBasic("Shinobi Training",true,54);
 private final ItemStack learningScroll;private final List<NativeLearning.Choice> choices;private int chosen;
 private NativeTrainingMenu(EntityPlayerMP player){viewer=player;learningScroll=player.getHeldItemMainhand();choices=NativeLearning.choices(player);
  for(int i=0;i<54;i++)addSlotToContainer(new Locked(icons,i,8+(i%9)*18,18+(i/9)*18));
  for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(player.inventory,col+row*9+9,8+col*18,140+row*18));
  for(int col=0;col<9;col++)addSlotToContainer(new Slot(player.inventory,col,8+col*18,198));refresh();
 }
 public static void open(EntityPlayerMP player){if(player.world.isRemote||player.getServer()==null||!player.getServer().isCallingFromMinecraftThread())return;player.closeContainer();NativeTrainingMenu menu=new NativeTrainingMenu(player);player.getNextWindowId();menu.windowId=player.currentWindowId;player.connection.sendPacket(new SPacketOpenWindow(menu.windowId,"minecraft:container",new TextComponentString("\u00a76Shinobi Training"),54));player.openContainer=menu;menu.addListener(player);}
 public static ItemStack button(){return icon(new ItemStack(Items.BOOK),"\u00a76Shinobi Training","Spend SP, learn jutsu and reclaim training points.");}
 private static ItemStack icon(ItemStack item,String name,String... lines){item.setStackDisplayName(name);NBTTagCompound root=item.hasTagCompound()?item.getTagCompound():new NBTTagCompound(),display=root.hasKey("display",10)?root.getCompoundTag("display"):new NBTTagCompound();NBTTagList lore=new NBTTagList();for(String line:lines)lore.appendTag(new NBTTagString("\u00a77"+line));display.setTag("Lore",lore);root.setTag("display",display);item.setTagCompound(root);return item;}
 private void refresh(){
  icons.clear();NativeTraining.Stat[] stats=NativeTraining.Stat.values();net.minecraft.item.Item[] art={Items.BLAZE_POWDER,Items.LEATHER,Items.IRON_SWORD,Items.FEATHER};
  String[] effects={"Chakra capacity and jutsu power","Unarmed power and toughness","Weapon power","Movement speed"};for(int i=0;i<stats.length;i++){NativeTraining.Stat stat=stats[i];icons.setInventorySlotContents(10+i*2,icon(new ItemStack(art[i]),"\u00a7e"+stat.title+" "+NativeTraining.points(viewer,stat)+"/"+stat.cap,effects[i],"Left-click: 1 SP | Right-click: 10 SP"));}
  icons.setInventorySlotContents(30,icon(new ItemStack(Items.GHAST_TEAR),"\u00a7cReset Training","Costs 1 reset credit.","Reclaims this menu's allocated SP and empties chakra."));
  if(!choices.isEmpty()){NativeLearning.Choice c=choices.get(chosen);icons.setInventorySlotContents(32,icon(learningScroll.copy(),"\u00a7b"+c.technique.getName(),"Cost: "+c.cost+" JP"+(NativeLearning.learned(learningScroll,c)?" | Learned":""),"Left-click: learn | Right-click: next technique","Native chakra and mode requirements still apply."));}
  else icons.setInventorySlotContents(32,icon(new ItemStack(Items.BOOK),"\u00a7bJutsu Learning","Hold your own Naruto jutsu scroll, then reopen training."));
  icons.setInventorySlotContents(49,icon(new ItemStack(Items.PAPER),"\u00a76SP "+NativeTraining.balance(viewer,"skill")+" | JP "+NativeTraining.balance(viewer,"jutsu"),"Reset credits: "+NativeTraining.balance(viewer,"reset")));
  icons.setInventorySlotContents(53,icon(new ItemStack(Items.ARROW),"\u00a7fClose"));detectAndSendChanges();
 }
 public boolean canInteractWith(EntityPlayer player){return player==viewer&&viewer.isEntityAlive();}
 public ItemStack slotClick(int slot,int mouse,ClickType type,EntityPlayer player){
  if(player!=viewer||viewer.openContainer!=this||!canInteractWith(player))return ItemStack.EMPTY;
  if(slot>=0&&slot<54){if(type!=ClickType.PICKUP||(mouse!=0&&mouse!=1))return ItemStack.EMPTY;boolean ok=false;
   if(slot==53){viewer.closeContainer();return ItemStack.EMPTY;}
   if(slot>=10&&slot<=16&&slot%2==0)ok=NativeTraining.spend(viewer,NativeTraining.Stat.values()[(slot-10)/2],mouse==1?10:1);
   else if(slot==30)ok=NativeTraining.reset(viewer);
   else if(slot==32&&!choices.isEmpty()&&viewer.getHeldItemMainhand()==learningScroll){if(mouse==1){chosen=(chosen+1)%choices.size();ok=true;}else ok=NativeLearning.learn(viewer,choices.get(chosen));}
   if(!ok)viewer.sendMessage(new TextComponentString("\u00a77Check your points, training cap and held scroll."));refresh();return ItemStack.EMPTY;
  }return super.slotClick(slot,mouse,type,player);
 }
 public ItemStack transferStackInSlot(EntityPlayer player,int slot){return ItemStack.EMPTY;}
 private static final class Locked extends Slot {Locked(IInventory inventory,int i,int x,int y){super(inventory,i,x,y);}public boolean isItemValid(ItemStack stack){return false;}public boolean canTakeStack(EntityPlayer player){return false;}}
}

package village.vale;

import java.util.*;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;

/** Operator recovery of explicitly archived container/NPC overflow, retaining untransferred units. */
public final class MigrationLoot extends WorldSavedData {
    private static final String NAME="vale_migration_loot";
    private final Map<String,NBTTagCompound> loot=new LinkedHashMap<>();
    public MigrationLoot(){super(NAME);}public MigrationLoot(String name){super(name);}
    static MigrationLoot get(WorldServer world){MigrationLoot data=(MigrationLoot)world.getMapStorage().getOrLoadData(MigrationLoot.class,NAME);if(data==null){data=new MigrationLoot();world.getMapStorage().setData(NAME,data);}return data;}
    @Override public void readFromNBT(NBTTagCompound nbt){loot.clear();NBTTagList list=nbt.getTagList("loot",10);for(int i=0;i<list.tagCount();i++){NBTTagCompound row=list.getCompoundTagAt(i);if(loot.put(row.getString("key"),row)!=null)throw new IllegalStateException("Duplicate migration loot key");}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt){NBTTagList list=new NBTTagList();for(NBTTagCompound row:loot.values())list.appendTag(row.copy());nbt.setTag("loot",list);return nbt;}
    public static final class Command extends CommandBase{
        @Override public String getName(){return "valemigration";}
        @Override public String getUsage(ICommandSender sender){return "/valemigration list | recover <key> <player>";}
        @Override public int getRequiredPermissionLevel(){return 4;}
        @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException{
            MigrationLoot data=get(server.getWorld(0));
            if(args.length==1&&args[0].equals("list")){for(NBTTagCompound row:data.loot.values())if(row.getTagList("items",10).tagCount()>0)sender.sendMessage(new TextComponentString(row.getString("key")+" | "+row.getString("source")));return;}
            if(args.length!=3||!args[0].equals("recover"))throw new WrongUsageException(getUsage(sender));
            NBTTagCompound row=data.loot.get(args[1]);if(row==null)throw new CommandException("Unknown saved loot key.");
            EntityPlayerMP player=getPlayer(server,sender,args[2]);if(!player.isEntityAlive()||player.capabilities.isCreativeMode||player.isSpectator())throw new CommandException("A living survival recipient is required.");
            NBTTagList old=row.getTagList("items",10),remaining=new NBTTagList();int units=0;
            for(int i=0;i<old.tagCount();i++){
                NBTTagCompound original=old.getCompoundTagAt(i);ItemStack stack=new ItemStack(original);
                if(stack.isEmpty()){remaining.appendTag(original.copy());continue;}
                int before=stack.getCount();player.inventory.addItemStackToInventory(stack);units+=before-stack.getCount();
                if(!stack.isEmpty())remaining.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
            row.setTag("items",remaining);data.markDirty();player.inventory.markDirty();
            for(int i=0;i<player.inventory.getSizeInventory();i++)MigrationClaims.get(server.getWorld(0)).claim(player,player.inventory.getStackInSlot(i));
            server.getWorld(0).getMapStorage().saveAllData();Adventures.save(player);
            sender.sendMessage(new TextComponentString("Recovered "+units+" item units; "+remaining.tagCount()+" stacks remain saved."));
        }
    }
}

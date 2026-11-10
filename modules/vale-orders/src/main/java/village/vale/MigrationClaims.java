package village.vale;

import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Server-authored, finite saved-world conversion tickets. No client request creates tickets. */
public final class MigrationClaims extends WorldSavedData {
    public static final String NAME="vale_migration_claims";
    private final Map<String,NBTTagCompound> claims=new LinkedHashMap<>();
    private static ItemStack authorizedStack; private static EntityPlayerMP authorizedPlayer;
    public MigrationClaims(){super(NAME);} public MigrationClaims(String name){super(name);}
    public static MigrationClaims get(WorldServer world){
        MigrationClaims data=(MigrationClaims)world.getMapStorage().getOrLoadData(MigrationClaims.class,NAME);
        if(data==null){data=new MigrationClaims();world.getMapStorage().setData(NAME,data);}return data;
    }
    static boolean authorized(EntityPlayerMP player,ItemStack stack){return player==authorizedPlayer&&stack==authorizedStack;}
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||event.player.world.isRemote||!(event.player instanceof EntityPlayerMP)||event.player.ticksExisted%10!=0)return;
        EntityPlayerMP player=(EntityPlayerMP)event.player;
        if(!player.isEntityAlive()||player.isSpectator())return;
        MigrationClaims data=get(player.getServer().getWorld(0));
        for(int i=0;i<player.inventory.getSizeInventory();i++)data.claim(player,player.inventory.getStackInSlot(i));
    }
    public boolean claim(EntityPlayerMP player,ItemStack stack){
        if(stack.isEmpty()||!stack.hasTagCompound())return false;
        NBTTagCompound tag=stack.getTagCompound();String token=tag.getString("valeMigrationToken");
        NBTTagCompound row=claims.get(token);
        if(row==null||!row.getString("owner").isEmpty()||!Gear.ITEMS.containsValue(stack.getItem())||!row.getString("item").equals(Gear.key(stack))||row.getInteger("quantity")!=stack.getCount()||tag.hasKey("valeSerial"))return false;
        try{authorizedPlayer=player;authorizedStack=stack;
            if(!RewardLedger.get(player).bindMigration(player,stack,token))return false;
            row.setString("owner",player.getUniqueID().toString());tag.removeTag("valeMigrationToken");markDirty();player.inventory.markDirty();return true;
        }finally{authorizedPlayer=null;authorizedStack=null;}
    }
    @Override public void readFromNBT(NBTTagCompound nbt){claims.clear();NBTTagList list=nbt.getTagList("claims",10);for(int i=0;i<list.tagCount();i++){NBTTagCompound row=list.getCompoundTagAt(i);String token=row.getString("token");if(token.isEmpty()||claims.put(token,row)!=null)throw new IllegalStateException("Duplicate/invalid migration ticket");}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt){NBTTagList list=new NBTTagList();for(NBTTagCompound row:claims.values())list.appendTag(row.copy());nbt.setTag("claims",list);return nbt;}
}

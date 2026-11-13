package village.vale;

import io.netty.buffer.ByteBuf;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.*;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

/** Owner-only server state. Repeated changes in one tick produce one reactive update. */
public final class HudNetwork {
    private static SimpleNetworkWrapper channel;
    private static final Set<UUID> DIRTY=new HashSet<>();
    private HudNetwork(){}
    public static void register(){if(channel!=null)return;channel=NetworkRegistry.INSTANCE.newSimpleChannel("valeorders_hud");channel.registerMessage(Handler.class,Message.class,0,Side.CLIENT);MinecraftForge.EVENT_BUS.register(new HudNetwork());}
    public static void dirty(EntityPlayer p){if(p instanceof EntityPlayerMP&&!(p instanceof FakePlayer)&&!p.world.isRemote)DIRTY.add(p.getUniqueID());AbilityNetwork.dirty(p);}
    public static void cast(EntityPlayerMP p,String art){NBTTagCompound s=PlayerState.data(p);long sequence=s.getLong("castSequence");s.setLong("castSequence",sequence<0||sequence==Long.MAX_VALUE?1:sequence+1);s.setLong("lastCastServerTick",Adventures.now(p));s.setString("lastCastArtId",art);dirty(p);}
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||event.player.world.isRemote||!(event.player instanceof EntityPlayerMP)||event.player instanceof FakePlayer)return;
        EntityPlayerMP p=(EntityPlayerMP)event.player;boolean changed=DIRTY.remove(p.getUniqueID());if(!p.isEntityAlive()||p.connection==null||channel==null)return;
        if(changed||p.ticksExisted%20==0)channel.sendTo(new Message(snapshot(p)),p);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){dirty(e.player);}
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){dirty(e.player);}
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e){dirty(e.player);}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){DIRTY.remove(e.player.getUniqueID());}
    public static HudSnapshot snapshot(EntityPlayerMP p){
        NBTTagCompound s=PlayerState.data(p);long now=Math.max(0,Adventures.now(p));Arts.Art a=Arts.selected(p);long artReady=a==null?0:s.getCompoundTag("artCooldowns").getLong(a.key),global=s.getLong("artGlobal");
        int total=a==null?0:artReady>=global?a.cooldown*20:20;int remaining=a==null?0:(int)Math.min(total,Math.max(0,Math.max(artReady,global)-now));
        int form=Forms.active(p);if(form>=0&&!p.isPotionActive(Forms.EFFECTS[form]))form=-1;
        EchoCompanion companion=null;if(s.hasUniqueId("summonId")&&s.getLong("summonEnd")>now){Entity e=((net.minecraft.world.WorldServer)p.world).getEntityFromUuid(s.getUniqueId("summonId"));if(e instanceof EchoCompanion&&e.isEntityAlive()&&p.getUniqueID().equals(((EchoCompanion)e).ownerId())&&((EchoCompanion)e).kind()<3)companion=(EchoCompanion)e;}
        Missions.Contract c=Missions.ALL.get(s.getString("mission"));int progress=c==null?0:s.getInteger("missionProgress");if(c!=null&&c.type.equals("supplies")){progress=0;for(ItemStack stack:p.inventory.mainInventory)if(stack.getItem()==Items.WHEAT&&!stack.hasTagCompound())progress+=stack.getCount();}
        return new HudSnapshot(p.getUniqueID(),p.dimension,now,PlayerState.resolve(p),100,PlayerState.level(p),Math.max(0,Math.min(1000000,s.getInteger("merit"))),a==null?"":a.key,a==null?"":a.title,remaining,total,
            LightningKit.active(p)?"lightning_chakra_armor":form<0?"":Forms.KEYS[form],LightningKit.active(p)?"Lightning Chakra Armor":form<0?"":title(Forms.KEYS[form])+" Form",companion==null?"":Summons.KEYS[companion.kind()],companion==null?"":new String[]{"Reedling Scout","Cairnling Guard","Lantern Attendant"}[companion.kind()],
            c==null?"":c.key,c==null?"":c.title,c==null?0:Math.max(0,Math.min(c.goal,progress)),c==null?0:c.goal,Math.max(0,s.getLong("castSequence")),Math.max(0,Math.min(now,s.getLong("lastCastServerTick"))),s.getString("lastCastArtId"));
    }
    private static String title(String key){return key.substring(0,1).toUpperCase(Locale.ROOT)+key.substring(1);}
    public static final class Message implements IMessage {
        private HudSnapshot value;
        public Message(){}
        Message(HudSnapshot value){this.value=value;}
        @Override public void toBytes(ByteBuf b){HudCodec.write(b,value);}
        @Override public void fromBytes(ByteBuf b){value=HudCodec.read(b);}
    }
    public static final class Handler implements IMessageHandler<Message,IMessage> {
        @Override public IMessage onMessage(Message message,MessageContext context){if(context.side==Side.CLIENT)ValeOrders.proxy.hudSnapshot(message.value);return null;}
    }
}

package village.vale.bridge;

import com.google.gson.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;
import net.minecraft.entity.*;import net.minecraft.entity.player.*;import net.minecraft.item.*;import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;import net.minecraft.util.text.TextComponentString;import net.minecraft.world.*;
import net.minecraftforge.common.*;import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;import net.minecraftforge.event.entity.living.*;import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.*;import net.minecraftforge.fml.common.gameevent.*;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import village.vale.arsenal.BridgeHooks;

/** Optional exact-name discovery and accepted-native-effect presentation. No second gameplay authority. */
public final class Bridge {
    private static final Bridge EVENTS=new Bridge();private static boolean registered;private static State active;
    private static final Set<String> NAMESPACES=new HashSet<>(Arrays.asList("narutomod","narutomodaddon","kabutoaddon","borutomodaddononero","ahznbcursemarkaddon","ahznbswordsplus"));
    private static final BridgeMappings.Registry REGISTRY=new BridgeMappings.Registry(){
        public boolean registered(String kind,String id){ResourceLocation key=new ResourceLocation(id);return kind.equals("item")?ForgeRegistries.ITEMS.containsKey(key):kind.equals("entity")&&ForgeRegistries.ENTITIES.containsKey(key);}
        public Set<String> weaponKeys(){return MoveVisuals.weaponKeys();}
    };
    private static final class State {
        final MinecraftServer server;final Path folder,moves,seen,registry;
        BridgeMappings maps=BridgeMappings.empty();final BridgeConfig config;BridgeDiscovery discovery=new BridgeDiscovery();String problem="";boolean seenWritable=true,dirty;long lastSave,sequence;
        final Map<EntityPlayerMP,Object> epochs=new WeakHashMap<>();final List<Spawn> spawns=new ArrayList<>();final List<Contact> contacts=new ArrayList<>();final BridgeReceipts.Budget budget=new BridgeReceipts.Budget();
        State(MinecraftServer server,Path folder){this.server=server;this.folder=folder;moves=folder.resolve("bridge-moves.json");seen=folder.resolve("bridge-discovered.json");registry=folder.resolve("bridge-registry.json");config=new BridgeConfig(moves,REGISTRY);}
        long now(){WorldServer world=server.getWorld(0);return world==null?Integer.toUnsignedLong(server.getTickCounter()):world.getTotalWorldTime();}
        Object epoch(EntityPlayerMP player){Object value=epochs.get(player);if(value==null){value=new Object();epochs.put(player,value);}return value;}
        boolean watched(String id){if(!BridgeMappings.validId(id))return false;String ns=id.substring(0,id.indexOf(':'));if(NAMESPACES.contains(ns))return true;for(BridgeMappings.Row row:maps.rows.values())if(row.id.startsWith(ns+":"))return true;return false;}
        void note(String kind,String id,String detail){if(watched(id)){discovery.note(kind,id,detail);dirty=true;}}
    }
    private static final class Context implements BridgeReceipts.Context {
        final State state;final EntityPlayerMP player;final UUID owner;final int entityId,dimension;final World world;final Object connection,epoch;final BridgeMappings maps;
        Context(State state,EntityPlayerMP player){this.state=state;this.player=player;owner=player.getUniqueID();entityId=player.getEntityId();dimension=player.dimension;world=player.world;connection=player.connection;epoch=state.epoch(player);maps=state.maps;}
        public boolean valid(){return active==state&&state.maps==maps&&state.epochs.get(player)==epoch&&current(player)&&player.getServer()==state.server&&owner.equals(player.getUniqueID())&&player.getEntityId()==entityId&&player.dimension==dimension&&player.world==world&&player.connection==connection;}
    }
    private static final class Spawn {
        final EntityJoinWorldEvent event;final Entity entity;final UUID id;final Context context;final BridgeMappings.Row row;final long born;
        Spawn(State state,EntityJoinWorldEvent event,EntityPlayerMP owner,BridgeMappings.Row row){this.event=event;entity=event.getEntity();id=entity.getUniqueID();context=new Context(state,owner);this.row=row;born=state.now();}
    }
    private static final class Contact {
        final LivingAttackEvent attack;LivingHurtEvent hurt;LivingDamageEvent damage;
        final Context context;final EntityLivingBase target;final UUID targetId;final Entity immediate;final BridgeMappings.Row row;final ItemStack held;final long born,serial;final BridgeReceipts.Damage receipt;
        Contact(State state,LivingAttackEvent event,EntityPlayerMP player,BridgeMappings.Row row,ItemStack held){attack=event;context=new Context(state,player);target=event.getEntityLiving();targetId=target.getUniqueID();immediate=event.getSource().getImmediateSource();this.row=row;this.held=held;born=state.now();serial=++state.sequence;receipt=new BridgeReceipts.Damage(context,target.getHealth(),target.getAbsorptionAmount());}
        boolean identity(){return context.valid()&&target.world==context.world&&targetId.equals(target.getUniqueID())&&attack.getSource().getTrueSource()==context.player&&attack.getSource().getImmediateSource()==immediate&&(!row.kind.equals("item")||context.player.getHeldItemMainhand()==held&&!held.isEmpty()&&row.id.equals(itemId(held)));}
        void observe(boolean seal){receipt.observe(target.getHealth(),target.getAbsorptionAmount(),seal);}
    }
    private Bridge(){}
    public static boolean current(EntityPlayerMP player){return player!=null&&!(player instanceof FakePlayer)&&player.world!=null&&!player.world.isRemote&&player.isEntityAlive()&&!player.isSpectator()&&player.connection!=null&&player.getServer()!=null&&player.getServer().isCallingFromMinecraftThread()&&player.getServer().getPlayerList().getPlayerByUUID(player.getUniqueID())==player&&player.world.getEntityByID(player.getEntityId())==player;}
    private static State state(){State state=active;if(state==null||!state.server.isCallingFromMinecraftThread())throw new IllegalStateException("Current bridge server thread required");return state;}
    public static void start(MinecraftServer server,File config){
        if(server==null||!server.isCallingFromMinecraftThread())throw new IllegalStateException("Server bridge start required");
        stop();active=new State(server,new File(config,"valeorders").toPath().toAbsolutePath().normalize());if(!registered){registered=true;MinecraftForge.EVENT_BUS.register(EVENTS);}
        State state=active;
        try{state.config.initialize();state.maps=state.config.snapshot();}catch(IOException|RuntimeException invalid){state.problem="Mapping not loaded: "+invalid.getMessage();}
        try{if(Files.exists(state.seen))state.discovery=BridgeDiscovery.read(BridgeFiles.read(state.seen,BridgeDiscovery.MAX_BYTES));}catch(IOException|RuntimeException invalid){state.seenWritable=false;state.problem+="; discovery preserved read-only: "+invalid.getMessage();}
    }
    public static void stop(){State old=active;if(old==null)return;if(old.server.isCallingFromMinecraftThread()){save();for(EntityPlayerMP player:new ArrayList<>(old.epochs.keySet()))BridgeHooks.close(player);}old.spawns.clear();old.contacts.clear();old.epochs.clear();old.budget.clear();active=null;}
    public static void reload()throws IOException{
        State state=state();try{state.config.reload();}catch(IOException invalid){state.problem="Reload refused; last valid mapping retained: "+invalid.getMessage();throw invalid;}
        state.maps=state.config.snapshot();state.problem="";state.spawns.clear();state.contacts.clear();state.budget.clear();
    }
    public static void mapping(BridgeMappings.Row row,String removeKey)throws IOException{
        State state=state();state.config.change(row,removeKey);state.maps=state.config.snapshot();state.spawns.clear();state.contacts.clear();state.budget.clear();
    }
    public static int mappingCount(){return state().maps.rows.size();}public static int seenCount(){return state().discovery.size();}public static long discoveryDropped(){return state().discovery.dropped();}public static String problem(){return state().problem;}
    public static String heldId(EntityPlayerMP player){return itemId(player.getHeldItemMainhand());}
    private static String itemId(ItemStack stack){return stack==null||stack.isEmpty()||stack.getItem().getRegistryName()==null?null:stack.getItem().getRegistryName().toString();}
    private static String entityId(Entity entity){ResourceLocation id=entity==null?null:EntityList.getKey(entity);return id==null?null:id.toString();}
    public static int exportRegistry()throws IOException{
        State state=state();TreeSet<String> items=new TreeSet<>(),entities=new TreeSet<>();
        for(ResourceLocation id:ForgeRegistries.ITEMS.getKeys())if(state.watched(id.toString()))items.add(id.toString());
        for(ResourceLocation id:ForgeRegistries.ENTITIES.getKeys())if(state.watched(id.toString()))entities.add(id.toString());
        if(items.size()>4096||entities.size()>4096)throw new IOException("Registry export exceeds explicit count limit");
        JsonObject root=new JsonObject();JsonArray a=new JsonArray(),b=new JsonArray();for(String id:items){if(!BridgeMappings.validId(id))throw new IOException("Registry ID exceeds bound");a.add(id);}for(String id:entities){if(!BridgeMappings.validId(id))throw new IOException("Registry ID exceeds bound");b.add(id);}root.add("items",a);root.add("entities",b);
        BridgeFiles.atomic(state.registry,(new GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n").getBytes(StandardCharsets.UTF_8),2*1024*1024);save();return items.size()+entities.size();
    }
    public static void save(){State state=active;if(state==null||!state.server.isCallingFromMinecraftThread()||!state.dirty||!state.seenWritable)return;try{BridgeFiles.atomic(state.seen,state.discovery.bytes(),BridgeDiscovery.MAX_BYTES);state.dirty=false;state.lastSave=state.now();}catch(IOException invalid){state.problem="Discovery save retained previous file: "+invalid.getMessage();}}
    public static boolean preview(EntityPlayerMP player,String style){State state=state();if(!current(player)||!player.canUseCommand(2,"narutobridge")||!BridgeMappings.validStyle(style,MoveVisuals.weaponKeys()))return false;
        if(!state.budget.take(player.getUniqueID(),"preview/"+(++state.sequence),state.now()))return false;MoveVisuals.accepted(player,style,new Context(state,player));player.sendStatusMessage(new TextComponentString("Cosmetic admin preview: "+style+" | no native cast or cost"),true);return true;}
    private static void present(Context context,BridgeMappings.Row row,String receipt){State state=context.state;if(!context.valid()||row.noop()||!state.budget.take(context.owner,receipt,state.now()))return;
        try{MoveVisuals.accepted(context.player,row.style,context);if(context.valid())context.player.sendStatusMessage(new TextComponentString("Native effect accepted: "+row.id+" | "+row.style),true);}catch(RuntimeException|LinkageError cosmetic){state.problem="Accepted native effect retained; cosmetic presentation failed";}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void use(PlayerInteractEvent.RightClickItem event){State state=active;if(state==null||event.getWorld().isRemote||!(event.getEntityPlayer() instanceof EntityPlayerMP)||!current((EntityPlayerMP)event.getEntityPlayer()))return;state.note("item",itemId(event.getItemStack()),"observation:item_use_intent");}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void spawn(EntityJoinWorldEvent event){
        State state=active;if(state==null||event.getWorld().isRemote||event.getWorld().getMinecraftServer()!=state.server||!state.server.isCallingFromMinecraftThread())return;
        Entity entity=event.getEntity();String id=entityId(entity);state.note("entity",id,"observation:spawn_seen");state.note("entity",id,entity.getClass().getName());BridgeMappings.Row row=state.maps.rows.get("entity/spawn/"+id);
        if(row==null||row.noop()||event.isCanceled()||state.spawns.size()>=256)return;EntityPlayerMP owner=BridgeOwnerProof.owner(entity,row.owner);
        if(current(owner)&&owner.world==event.getWorld())state.spawns.add(new Spawn(state,event,owner,row));
    }
    public static void beforeHealthReset(EntityLivingBase target){State state=active;if(state==null||target==null||target.world.isRemote||!state.server.isCallingFromMinecraftThread())return;for(Contact contact:state.contacts)if(contact.target==target)contact.observe(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)public void attack(LivingAttackEvent event){
        State state=active;if(state==null||event.getEntityLiving().world.isRemote||!state.server.isCallingFromMinecraftThread())return;beforeHealthReset(event.getEntityLiving());
        Entity owner=event.getSource().getTrueSource(),immediate=event.getSource().getImmediateSource();if(!(owner instanceof EntityPlayerMP)||!current((EntityPlayerMP)owner)||owner.world!=event.getEntityLiving().world||state.contacts.size()>=256)return;
        String id=entityId(immediate);state.note("damage",id,"observation:attack_seen");BridgeMappings.Row row=state.maps.rows.get("entity/damage/"+id);ItemStack held=ItemStack.EMPTY;
        if(row==null&&immediate==owner&&"player".equals(event.getSource().getDamageType())){held=((EntityPlayerMP)owner).getHeldItemMainhand();row=state.maps.rows.get("item/melee/"+itemId(held));}
        if(row!=null&&!row.noop())state.contacts.add(new Contact(state,event,(EntityPlayerMP)owner,row,held));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void hurt(LivingHurtEvent event){State state=active;if(state==null||event.getEntityLiving().world.isRemote||!state.server.isCallingFromMinecraftThread())return;for(int i=state.contacts.size()-1;i>=0;i--){Contact c=state.contacts.get(i);if(c.hurt==null&&c.target==event.getEntityLiving()&&c.attack.getSource()==event.getSource()){c.hurt=event;break;}}}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void damage(LivingDamageEvent event){State state=active;if(state==null||event.getEntityLiving().world.isRemote||!state.server.isCallingFromMinecraftThread())return;for(int i=state.contacts.size()-1;i>=0;i--){final Contact c=state.contacts.get(i);if(c.damage==null&&c.hurt!=null&&c.target==event.getEntityLiving()&&c.attack.getSource()==event.getSource()){
        c.damage=event;c.receipt.matched(new BridgeReceipts.FinalEvents(){public boolean attackCanceled(){return c.attack.isCanceled();}public boolean hurtCanceled(){return c.hurt.isCanceled();}public double hurtAmount(){return c.hurt.getAmount();}public boolean damageCanceled(){return c.damage.isCanceled();}public double damageAmount(){return c.damage.getAmount();}});break;
    }}}
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)public void heal(LivingHealEvent event){beforeHealthReset(event.getEntityLiving());}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
        State state=active;if(state==null||event.phase!=TickEvent.Phase.END||!state.server.isCallingFromMinecraftThread())return;long now=state.now();
        List<Spawn> spawns=new ArrayList<>(state.spawns);state.spawns.clear();for(Spawn pending:spawns){Entity entity=pending.entity;boolean same=pending.id.equals(entity.getUniqueID())&&entity.world==pending.context.world&&!entity.isDead;boolean admitted=same&&entity.world.getEntityByID(entity.getEntityId())==entity;
            if(now>=pending.born&&now-pending.born<=2&&BridgeReceipts.spawn(pending.context,pending.event.isCanceled(),same,admitted,BridgeOwnerProof.owner(entity,pending.row.owner)==pending.context.player)){state.note("entity",pending.row.id,"observation:spawn_admitted");present(pending.context,pending.row,"spawn/"+pending.context.owner+"/"+pending.id);}}
        List<Contact> contacts=new ArrayList<>(state.contacts);state.contacts.clear();for(Contact pending:contacts){pending.observe(false);if(now>=pending.born&&now-pending.born<=2&&pending.identity()&&pending.receipt.accepted()){state.note("damage",pending.row.id,"observation:accepted_health_or_absorption_loss");present(pending.context,pending.row,"damage/"+pending.context.owner+"/"+pending.serial);}}
        if(state.dirty&&(now<state.lastSave||now-state.lastSave>=200))save();
    }
    private static void invalidate(EntityPlayer player){State state=active;if(state==null||!(player instanceof EntityPlayerMP)||!state.server.isCallingFromMinecraftThread())return;EntityPlayerMP body=(EntityPlayerMP)player;state.epochs.remove(body);state.spawns.removeIf(p->p.context.player==body);state.contacts.removeIf(p->p.context.player==body);BridgeHooks.close(body);}
    @SubscribeEvent public void clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event){invalidate(event.getOriginal());invalidate(event.getEntityPlayer());}
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event){invalidate(event.player);}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event){invalidate(event.player);}
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event){invalidate(event.player);}
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event){invalidate(event.player);}
    @SubscribeEvent public void unload(net.minecraftforge.event.world.WorldEvent.Unload event){State state=active;if(state==null||event.getWorld().isRemote||!state.server.isCallingFromMinecraftThread())return;state.spawns.removeIf(p->p.context.world==event.getWorld());state.contacts.removeIf(p->p.context.world==event.getWorld());save();}
}

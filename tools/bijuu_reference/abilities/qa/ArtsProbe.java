package village.bijuu;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.potion.Potion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.narutomod.Chakra;
import net.narutomod.PlayerTracker;
import net.narutomod.item.ItemJutsu;

@Mod(modid="bijuuartstest",name="Private armor integration probe",version="1",dependencies="required-after:hvbijuu;required-after:narutomod")
public final class ArtsProbe {
    private static void check(boolean condition,String name) {
        if(!condition)throw new AssertionError(name);
    }
    private static void equip(EntityPlayerMP player,int beast) {
        for(BijuuArmor.Armor piece:BijuuArmor.ITEMS)
            if(piece.beast==beast)player.func_184201_a(piece.field_77881_a,new ItemStack(piece));
    }
    private static void finish(){
        NativeArmorArts.finish(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
    }
    private static void hit(EntityZombie target,DamageSource source){
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target,source,3));finish();
    }
    @Mod.EventHandler public void start(FMLServerStartedEvent event) {
        MinecraftServer server=FMLCommonHandler.instance().getMinecraftServerInstance();
        try {
            WorldServer world=server.func_71218_a(0);
            EntityPlayerMP p=new EntityPlayerMP(server,world,new GameProfile(UUID.randomUUID(),"ArtsFixture"),new PlayerInteractionManager(world));
            p.field_71135_a=new NetHandlerPlayServer(server,new NetworkManager(EnumPacketDirection.SERVERBOUND),p);
            EntityZombie target=new EntityZombie(world);
            DamageSource nin=ItemJutsu.causeJutsuDamage(target,p);
            DamageSource sage=ItemJutsu.causeSenjutsuDamage(target,p);
            NBTTagCompound state=BijuuArmor.state(p);
            BijuuArmor.requireIssued=true;
            equip(p,8);hit(target,nin);check(state.func_74762_e("energy")==0,"unissued gate");
            BijuuArmor.requireIssued=false;
            Potion[] effects={MobEffects.field_76444_x,MobEffects.field_76424_c,MobEffects.field_76429_m,MobEffects.field_76426_n,
                MobEffects.field_76424_c,MobEffects.field_76421_d,MobEffects.field_76430_j,MobEffects.field_76440_q,MobEffects.field_76428_l};
            for(int i=0;i<9;i++){
                equip(p,i);p.func_70674_bp();target.func_70674_bp();
                state.func_82580_o("nativeArtLastHit");state.func_74768_a("energy",25);
                hit(target,i%2==0?nin:sage);
                check(state.func_74762_e("energy")==33,"gain "+i);
                check((i==5||i==7?target:p).func_70644_a(effects[i]),"effect "+i);
                hit(target,nin);check(state.func_74762_e("energy")==33,"interval "+i);
            }
            state.func_82580_o("nativeArtLastHit");
            LivingDamageEvent canceled=new LivingDamageEvent(target,nin,3);
            NativeArmorArts.onDamage(canceled);canceled.setCanceled(true);finish();
            check(state.func_74762_e("energy")==33,"post-cancel");
            p.func_184201_a(EntityEquipmentSlot.MAINHAND,new ItemStack(Items.field_151048_u));
            NativeArmorArts.rememberSwing(new net.minecraftforge.event.entity.player.AttackEntityEvent(p,target));
            hit(target,DamageSource.func_76365_a(p));check(state.func_74762_e("energy")==33,"vanilla sword");
            java.util.Map<String,Integer> swords=new java.util.TreeMap<>();
            java.util.List<String> packs=java.util.Arrays.asList("narutomod","ahznbswordsplus","narutomodaddononero",
                "narutomodaddon","borutomodaddononero","kabutoaddon","ahznbcursemarkaddon");
            for(ResourceLocation id:Item.field_150901_e.func_148742_b()){
                Item sword=Item.field_150901_e.func_82594_a(id);
                String namespace=id.func_110624_b();
                if(!packs.contains(namespace) || !(sword instanceof net.minecraft.item.ItemSword))continue;
                p.func_184201_a(EntityEquipmentSlot.MAINHAND,new ItemStack(sword));
                state.func_82580_o("nativeArtLastHit");state.func_74768_a("energy",98);
                hit(target,DamageSource.func_76365_a(p));
                check(state.func_74762_e("energy")==98,"unrelated player damage with sword "+id);
                NativeArmorArts.rememberSwing(new net.minecraftforge.event.entity.player.AttackEntityEvent(p,target));
                hit(target,DamageSource.func_76365_a(p));
                check(state.func_74762_e("energy")==100,"weapon "+id);
                swords.put(namespace,swords.getOrDefault(namespace,0)+1);
            }
            check(swords.containsKey("narutomod") && swords.containsKey("ahznbswordsplus")
                && swords.containsKey("narutomodaddononero"),"expected native/addon swords");
            System.out.println("BIJUU_ARTS_WEAPONS "+swords);
            // Run a real damage call in addition to the event-contract cases above.
            state.func_82580_o("nativeArtLastHit");state.func_74768_a("energy",0);
            check(target.func_70097_a(nin,2),"native hit accepted");finish();
            check(state.func_74762_e("energy")==8,"actual damage pipeline");
            check(!ChakraBridge.pay(p),"non-ninja");
            PlayerTracker.addBattleXp(p,250);
            Chakra.PathwayPlayer pathway=Chakra.pathway(p);
            pathway.setMax(400);pathway.consume(-400.0,false);
            double before=pathway.getAmount(),cost=Math.max(10,pathway.getMax()*.05);
            check(before>cost,"funded server account");
            for(int beast=0;beast<9;beast++){
                equip(p,beast);if(beast==1)MatatabiPowers.engage(p);
                state.func_82580_o("ready");state.func_82580_o("requestTick");
                before=pathway.getAmount();
                state.func_74768_a("energy",100);BijuuArmor.activate(p);
                check(Math.abs(pathway.getAmount()-(before-cost))<.001 && state.func_74762_e("energy")==60,"active "+beast);
            }
            check(Math.abs(pathway.getAmount()-(before-cost))<.001 && state.func_74762_e("energy")==60,"active settlement");
            state.func_82580_o("requestTick");BijuuArmor.activate(p);
            check(Math.abs(pathway.getAmount()-(before-cost))<.001 && state.func_74762_e("energy")==60,"cooldown retry");
            pathway.clear();state.func_82580_o("requestTick");state.func_82580_o("ready");BijuuArmor.activate(p);
            check(state.func_74762_e("energy")==60 && !state.func_74764_b("ready"),"unfunded");
            System.out.println("BIJUU_ARTS_PASS nine sets, two native channels, registered Naruto swords, server chakra debit "+cost);
        }catch(Throwable error){error.printStackTrace();System.out.println("BIJUU_ARTS_FAIL "+error);}
        finally{server.func_71263_m();}
    }
}

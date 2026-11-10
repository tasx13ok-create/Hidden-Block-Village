package village.vale.armor.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import village.vale.OriginalArmorLightEntity;
import village.vale.OriginalArmorRegistry;

/** At most 24 nearest sources within 42 blocks; world/unequip/death cleanup stays client-only. */
@Mod.EventBusSubscriber(modid="valeorders",value=Side.CLIENT)
public final class OriginalArmorLights {
    private static final Map<UUID,OriginalArmorLightEntity> SOURCES=new HashMap<>();private static WorldClient world;private static int next=-890000;
    private OriginalArmorLights(){}
    public static void initialize(){RenderingRegistry.registerEntityRenderingHandler(OriginalArmorLightEntity.class,manager->new Render<OriginalArmorLightEntity>(manager){
        @Override public void doRender(OriginalArmorLightEntity e,double x,double y,double z,float yaw,float partial){}
        @Override protected ResourceLocation getEntityTexture(OriginalArmorLightEntity e){return null;}
    });}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        if(world!=mc.world){clear();world=mc.world;}if(world==null||mc.player==null){clear();return;}
        List<EntityPlayer> candidates=new ArrayList<>();
        for(EntityPlayer p:world.playerEntities)if(p.isEntityAlive()&&!p.isSpectator()&&p.getDistanceSq(mc.player)<=42*42&&OriginalArmorRegistry.visual(p)!=null)candidates.add(p);
        candidates.sort(Comparator.comparingDouble(p->p.getDistanceSq(mc.player)));Set<UUID> retained=new HashSet<>();
        for(int i=0;i<Math.min(24,candidates.size());i++){
            EntityPlayer wearer=candidates.get(i);UUID key=wearer.getUniqueID();retained.add(key);OriginalArmorLightEntity source=SOURCES.get(key);
            if(source==null||source.isDead){source=new OriginalArmorLightEntity(world);while(world.getEntityByID(next)!=null)next--;world.addEntityToWorld(next--,source);SOURCES.put(key,source);}
            source.setPosition(wearer.posX,wearer.posY+1.05,wearer.posZ);
        }
        Iterator<Map.Entry<UUID,OriginalArmorLightEntity>> it=SOURCES.entrySet().iterator();while(it.hasNext()){Map.Entry<UUID,OriginalArmorLightEntity> row=it.next();if(!retained.contains(row.getKey())){world.removeEntityFromWorld(row.getValue().getEntityId());it.remove();}}
    }
    static void clear(){if(world!=null)for(OriginalArmorLightEntity source:SOURCES.values())world.removeEntityFromWorld(source.getEntityId());SOURCES.clear();}
}

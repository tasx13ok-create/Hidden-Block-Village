package village.vale.client;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.vale.JutsuMath;

/**
 * Chest power core for Hidden Village Bijuu Armor chestplates (detected by reflection; no compile
 * dependency). Drawn on the body bone so it follows every pose, in stacked 3D layers:
 *   1. recessed dark socket and bevelled frame plates (face-shaded voxels)
 *   2. eight rune studs around the frame that light up in sequence
 *   3. energy veins running to the shoulders and down the ribs, with pulses travelling outward
 *   4. a tumbling emissive core cube inside a counter-rotating inner shell
 *   5. two orbit rings of voxel motes on tilted planes
 *   6. additive bloom (soft halo + hard core glow) and flaring light shafts
 *   7. a cosmetic light annulus on a loaded supporting surface (not dynamic block lighting)
 * "Power" drives brightness, pulse rate, ring speed, shaft length and sparks. It rises with
 * sprinting, attacking, taking damage and low health, and decays smoothly at rest.
 * Colour comes from the beast slot (original palette, colour only).
 */
@SideOnly(Side.CLIENT)
public final class ArmorCoreFx implements LayerRenderer<AbstractClientPlayer> {
    private static final String ARMOR_CLASS = "village.bijuu.BijuuArmor$Armor";
    private static final int[][] PALETTE = {
        {0xFFE7A8, 0xD89A3A}, {0xCDEBFF, 0x2F7BFF}, {0xE0FFF8, 0x3FC8C0}, {0xFFE0C8, 0xE8301A}, {0xFFF4F0, 0xE8C8F0},
        {0xF0FFD0, 0x58D83A}, {0xF8FFE0, 0xB8E05A}, {0xF2E6FF, 0x8A3AF0}, {0xFFF0D0, 0xFF7A1A}};
    private static Field beastField;
    private static final Set<RenderPlayer> HOOKED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static final ArmorCoreRules.Tracker<EntityPlayer> POWER=new ArmorCoreRules.Tracker<>();
    private static final java.util.List<Burst> BURSTS=new java.util.ArrayList<>();
    private static final java.util.Random RNG=new java.util.Random();
    private static boolean installed,enabled;
    private static java.io.File settingsFile;
    private final RenderPlayer renderer;

    private ArmorCoreFx(RenderPlayer r) { renderer = r; }

    public static void initialize(){if(!installed){installed=true;VoxelBits.install();settingsFile=new java.io.File(net.minecraftforge.fml.common.Loader.instance().getConfigDir(),"valeorders-client.cfg");try{net.minecraftforge.common.config.Configuration c=new net.minecraftforge.common.config.Configuration(settingsFile);c.load();enabled=c.getBoolean("armorPowerCore","effects",false,"Optional original core, runes and glow on compatible chest armor. Off preserves original armor presentation.");if(c.hasChanged())c.save();}catch(RuntimeException failure){enabled=false;}MinecraftForge.EVENT_BUS.register(new Hooks());}}
    public static boolean enabled(){return enabled;}
    /** Called by the optional client settings bridge; no equipment or server gameplay is changed. */
    public static boolean setEnabled(boolean value){
        if(!installed||settingsFile==null||!MC.isCallingFromMinecraftThread())return false;
        try{net.minecraftforge.common.config.Configuration c=new net.minecraftforge.common.config.Configuration(settingsFile);c.load();c.get("effects","armorPowerCore",false).set(value);c.save();if(!settingsFile.isFile())return false;net.minecraftforge.common.config.Configuration verify=new net.minecraftforge.common.config.Configuration(settingsFile);verify.load();if(!verify.hasKey("effects","armorPowerCore")||verify.get("effects","armorPowerCore",false).getBoolean()!=value)return false;enabled=value;if(!value)clear();return true;}catch(RuntimeException failure){return false;}
    }
    private static void clear(){POWER.clear();VoxelBits.clearArmor();BURSTS.clear();sparkWorld=sparkBody=sparkConnection=null;}

    public static final class Hooks {
        @SubscribeEvent public void attach(RenderPlayerEvent.Pre e){if(!enabled)return;RenderPlayer r=e.getRenderer();if(HOOKED.add(r))r.addLayer(new ArmorCoreFx(r));}
        @SubscribeEvent public void tick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent e){
            if(e.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END||!MC.isCallingFromMinecraftThread())return;
            if(!enabled){clear();return;}
            if(!POWER.begin(MC.world,MC.player,MC.getConnection(),MC.player==null?-1:MC.player.ticksExisted)){if(!POWER.context(MC.world,MC.player,MC.getConnection())){VoxelBits.clearArmor();BURSTS.clear();sparkWorld=sparkBody=sparkConnection=null;}return;}
            if(sparkWorld!=MC.world||sparkBody!=MC.player||sparkConnection!=MC.getConnection()){VoxelBits.clearArmor();BURSTS.clear();sparkWorld=MC.world;sparkBody=MC.player;sparkConnection=MC.getConnection();}
            boolean motion=animated();if(!motion){VoxelBits.clearArmor();BURSTS.clear();}else{for(java.util.Iterator<Burst> it=BURSTS.iterator();it.hasNext();){Burst b=it.next();b.age+=POWER.delta();if(b.age>=24)it.remove();}}
            java.util.List<EntityPlayer> selected=new java.util.ArrayList<>();java.util.Set<EntityPlayer> live=POWER.identities();
            if(eligible(MC.player)){selected.add(MC.player);live.add(MC.player);}
            for(int i=0;i<Math.min(256,MC.world.playerEntities.size())&&selected.size()<32;i++){EntityPlayer player=MC.world.playerEntities.get(i);if(live.contains(player)||!eligible(player))continue;selected.add(player);live.add(player);}
            POWER.retain(live);
            for(EntityPlayer player:selected){ArmorCoreRules.Power state=POWER.get(player);if(state==null)continue;int tick=MC.player.ticksExisted,beast=beast(player);net.minecraft.util.math.BlockPos pos=new net.minecraft.util.math.BlockPos(player.posX,player.posY+1.5,player.posZ);boolean loaded=player.world.isBlockLoaded(pos),wet=player.isInWater()||loaded&&player.world.isRainingAt(pos),night=!player.world.isDaytime()||loaded&&player.world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY,pos)<8;
                state.step(tick,player.isSprinting(),player.isSwingInProgress,player.hurtTime,player.getHealth(),player.getMaxHealth(),motion&&player.isSneaking(),wet,night);
                if(motion){if(state.hit(tick))hit(player,beast);if(state.burst(tick))burst(player,beast);ambient(player,beast,state,tick);}}
            if(motion)shower();
        }
        @SubscribeEvent public void ground(RenderWorldLastEvent e){
            if(!enabled||!POWER.context(MC.world,MC.player,MC.getConnection()))return;
            net.minecraft.entity.Entity camera=MC.getRenderViewEntity();if(camera==null)return;
            double partial=ArmorCoreRules.unit(e.getPartialTicks());double cx=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial,cy=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial,cz=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
            if(!Double.isFinite(cx)||!Double.isFinite(cy)||!Double.isFinite(cz))return;
            int drawn=0;for(int i=0;i<Math.min(256,MC.world.playerEntities.size())&&drawn<32;i++){
                EntityPlayer player=MC.world.playerEntities.get(i);ArmorCoreRules.Power state=POWER.find(player);int beast=beast(player);if(state==null||beast<0||!eligible(player))continue;
                double x=player.lastTickPosX+(player.posX-player.lastTickPosX)*partial,y=player.lastTickPosY+(player.posY-player.lastTickPosY)*partial,z=player.lastTickPosZ+(player.posZ-player.lastTickPosZ)*partial;
                double floor=floor(player,x,y,z);if(!Double.isFinite(floor))continue;drawn++;
                double power=state.sample(partial),pulse=state.pulse(partial,animated());int colour=PALETTE[beast][1];
                VfxDraw.begin(x-cx,y-cy,z-cz);try{VfxDraw.additive();double radius=(1.3+1.2*power+.25*pulse)*(state.night()?1.5:1);int steps=JutsuVfx.detail==0?12:24;
                    VfxDraw.annulus(0,floor+.015-y,0,0,radius*.35,colour,(float)(.28+.25*power),steps);
                    VfxDraw.annulus(0,floor+.015-y,0,radius*.35,radius*.7,colour,(float)(.16+.16*power),steps);
                    VfxDraw.annulus(0,floor+.015-y,0,radius*.7,radius,colour,(float)(.06+.08*power),steps);
                }finally{VfxDraw.end();}
            }
            if(animated()&&!BURSTS.isEmpty()){VfxDraw.begin(-cx,-cy,-cz);try{VfxDraw.additive();for(Burst burst:BURSTS){double age=burst.age+partial;if(age>=16||(burst.x-cx)*(burst.x-cx)+(burst.y-cy)*(burst.y-cy)+(burst.z-cz)*(burst.z-cz)>48*48)continue;for(int i=0;i<48;i++){double angle=i*Math.PI*2/48,radius=(.22+.12*noise(burst.seed,i))*(1-Math.pow(.9,age))/.1;ArmorCoreGeometry.cube(burst.x+Math.cos(angle)*radius,burst.y-.2+.025*age-.002*age*age,burst.z+Math.sin(angle)*radius,.065,i%3==0?burst.core:burst.glow,(float)(1-age/16));}}}finally{VfxDraw.end();}}
        }
    }
    private static Object sparkWorld,sparkBody,sparkConnection;
    private static final class Burst{double x,y,z;int age,remaining=30,core,glow;long seed;}
    private static boolean eligible(EntityPlayer player){return player!=null&&player.world==MC.world&&player.isEntityAlive()&&!player.isInvisible()&&!player.isSpectator()&&MC.player!=null&&player.getDistanceSq(MC.player)<=48*48&&beast(player)>=0;}
    private static double[] origin(EntityPlayer player){double[] forward=JutsuMath.forward(player.renderYawOffset);return new double[]{player.posX+forward[0]*.3,player.posY+(player.isSneaking()?1:1.2),player.posZ+forward[2]*.3,forward[0],forward[2]};}
    private static boolean add(double x,double y,double z,double vx,double vy,double vz,double size,int colour,double alpha,int life,double gravity,double drag){if(!POWER.emit())return false;return VoxelBits.spawnArmor(x,y,z,vx,vy,vz,size,colour,alpha,life,gravity,drag);}

    private static void hit(EntityPlayer player,int beast){double[] p=origin(player);for(int i=0;i<14;i++)if(!add(p[0],p[1],p[2],p[3]*.12+(RNG.nextDouble()-.5)*.25,.05+RNG.nextDouble()*.15,p[4]*.12+(RNG.nextDouble()-.5)*.25,.035,PALETTE[beast][1],1,12+RNG.nextInt(6),-.03,.93))break;}
    private static void burst(EntityPlayer player,int beast){if(BURSTS.size()>=32)BURSTS.remove(0);double[] p=origin(player);Burst b=new Burst();b.x=p[0];b.y=p[1];b.z=p[2];b.core=PALETTE[beast][0];b.glow=PALETTE[beast][1];b.seed=RNG.nextLong();BURSTS.add(b);if(POWER.sound())player.world.playSound(player.posX,player.posY,player.posZ,net.minecraft.init.SoundEvents.ENTITY_ILLAGER_CAST_SPELL,net.minecraft.util.SoundCategory.PLAYERS,.6f,.7f,false);}
    private static void shower(){for(Burst b:BURSTS)while(b.remaining>0){if(!add(b.x,b.y,b.z,(RNG.nextDouble()-.5)*.3,.15+RNG.nextDouble()*.25,(RNG.nextDouble()-.5)*.3,.035,RNG.nextBoolean()?b.core:b.glow,1,22+RNG.nextInt(12),-.03,.95))return;b.remaining--;}}
    private static void ambient(EntityPlayer player,int beast,ArmorCoreRules.Power state,int tick){double[] p=origin(player);double power=state.sample(1),charge=state.charging(0);if(charge>0&&RNG.nextDouble()<.6)for(int i=0;i<2;i++){double a=RNG.nextDouble()*Math.PI*2,d=.9+RNG.nextDouble()*.5,x=p[0]+Math.cos(a)*d,y=p[1]+(RNG.nextDouble()-.5)*.8,z=p[2]+Math.sin(a)*d;add(x,y,z,(p[0]-x)*.09,(p[1]-y)*.09,(p[2]-z)*.09,.035,PALETTE[beast][i],1,10,0,.98);}
        if(state.wet()&&RNG.nextDouble()<.35+.4*power)add(p[0]+(RNG.nextDouble()-.5)*.2,p[1]+.1,p[2]+(RNG.nextDouble()-.5)*.2,(RNG.nextDouble()-.5)*.01,.03+RNG.nextDouble()*.02,(RNG.nextDouble()-.5)*.01,.065,0xDCDCDC,.6,20,.001,.94);
        if(state.spark(tick)&&RNG.nextDouble()<power)add(p[0],p[1],p[2],p[3]*.05+(RNG.nextDouble()-.5)*.06,.03+RNG.nextDouble()*.04,p[4]*.05+(RNG.nextDouble()-.5)*.06,.025,PALETTE[beast][RNG.nextBoolean()?0:1],1,14+RNG.nextInt(8),-.002,.93);
    }

    private static double noise(long seed,int point){long v=seed+point*0x9e3779b97f4a7c15L;v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return ((v^(v>>>31))>>>11)*0x1.0p-53;}
    private static double floor(EntityPlayer player,double x,double y,double z){if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return Double.NaN;net.minecraft.util.math.BlockPos low=new net.minecraft.util.math.BlockPos(x,y-3,z),high=new net.minecraft.util.math.BlockPos(x,y+.2,z);if(!player.world.isAreaLoaded(low,high))return Double.NaN;net.minecraft.util.math.RayTraceResult hit=player.world.rayTraceBlocks(new net.minecraft.util.math.Vec3d(x,y+.2,z),new net.minecraft.util.math.Vec3d(x,y-3,z),false,true,false);return hit!=null&&hit.typeOfHit==net.minecraft.util.math.RayTraceResult.Type.BLOCK&&hit.sideHit==net.minecraft.util.EnumFacing.UP?hit.hitVec.y:Double.NaN;}
    private static boolean animated(){return VfxPreferences.motion();}

    static int beast(EntityPlayer p) {
        if(p==null)return -1;
        ItemStack s = p.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (s==null || s.isEmpty() || !ARMOR_CLASS.equals(s.getItem().getClass().getName())) return -1;
        try {
            if (beastField == null) { beastField = s.getItem().getClass().getField("beast"); }
            int b = beastField.getInt(s.getItem());
            return b >= 0 && b < PALETTE.length ? b : -1;
        } catch (ReflectiveOperationException | RuntimeException e) { return -1; }
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer p, float limb, float limbAmount, float partial, float age, float yaw, float pitch, float scale) {
        if(!enabled)return;int beast = beast(p);
        ArmorCoreRules.Power state=POWER.find(p);if(beast<0||state==null||!eligible(p)||!Float.isFinite(scale)||scale<=0||scale>1||!POWER.context(MC.world,MC.player,MC.getConnection()))return;
        int core = PALETTE[beast][0], glow = PALETTE[beast][1];
        boolean motion=animated();double t=motion?p.ticksExisted+ArmorCoreRules.unit(partial):0,power=state.sample(partial),pulse=state.pulse(partial,motion),charge=motion?state.charging(partial):0,flash=motion?state.flash(partial):0;
        VfxDraw.begin();
        try {
            renderer.getMainModel().bipedBody.postRender(scale);
            if (p.isSneaking()) GlStateManager.translate(0, 0.2, 0);
            // Chest front plane: x right(-) / left(+), y down, z front = -0.135 (just proud of the armour).
            GlStateManager.translate(0, 0.26, -0.15);
            double px = 1 / 16.0;
            // 1. socket + bevelled frame (dark plates, face-shaded)
            VfxDraw.normal();
            ArmorCoreGeometry.cube(0, 0, 0.012, px * 5.2, 0x15171C, 1f);
            int frame = ArmorCoreGeometry.shade(glow, 0.32);
            for (int i = -2; i <= 2; i++) {
                ArmorCoreGeometry.cube(i * px, -2.6 * px, -0.004, px * 1.05, frame, 1f);
                ArmorCoreGeometry.cube(i * px, 2.6 * px, -0.004, px * 1.05, frame, 1f);
                ArmorCoreGeometry.cube(-2.6 * px, i * px, -0.004, px * 1.05, frame, 1f);
                ArmorCoreGeometry.cube(2.6 * px, i * px, -0.004, px * 1.05, frame, 1f);
            }
            for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) ArmorCoreGeometry.cube(c[0] * 2.6 * px, c[1] * 2.6 * px, -0.012, px * 1.3, ArmorCoreGeometry.shade(glow, 0.5), 1f);
            // 2. rune studs light in a rotating sequence
            VfxDraw.additive();
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4, lit = 0.5 + 0.5 * Math.cos(a - t * (0.1 + 0.2 * power));
                ArmorCoreGeometry.cube(Math.cos(a) * 3.6 * px, Math.sin(a) * 3.6 * px, -0.01, px * 0.7, glow, (float) (0.25 + 0.75 * lit * (0.4 + 0.6 * power)));
            }
            // 3. energy veins to the shoulders and ribs with travelling pulses
            double[][] veins = {{-1, -1, -3.9, -4.4}, {1, -1, 3.9, -4.4}, {-1, 1, -3.6, 5.4}, {1, 1, 3.6, 5.4}, {0, 1, 0, 6.2}};
            for (int v = 0; v < veins.length; v++) {
                double x0 = veins[v][0] * 2.4 * px, y0 = veins[v][1] * 2.4 * px, x1 = veins[v][2] * px, y1 = veins[v][3] * px;
                int steps = 9;
                for (int s = 0; s <= steps; s++) {
                    double f = s / (double) steps, x = x0 + (x1 - x0) * f, y = y0 + (y1 - y0) * f;
                    double wave = Math.max(0, Math.cos((f - ((t * (0.04 + 0.08 * power) + v * 0.2) % 1.0)) * Math.PI * 3));
                    ArmorCoreGeometry.cube(ArmorCoreGeometry.pixel(x) , ArmorCoreGeometry.pixel(y), -0.004, px * 0.55, glow, (float) ((0.18 + 0.82 * wave) * (0.35 + 0.65 * power) * (1 - f * 0.4)));
                }
            }
            // 4. inner shell + tumbling core cube
            GlStateManager.pushMatrix();
            try{GlStateManager.translate(0, 0, -0.03);
            GlStateManager.rotate((float) (t * (2 + 6 * power)), 0, 0, 1);
            VfxDraw.normal();
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2;
                ArmorCoreGeometry.cube(Math.cos(a) * 1.6 * px, Math.sin(a) * 1.6 * px, 0, px * 0.8, ArmorCoreGeometry.shade(glow, 0.75), 1f);
            }
            GlStateManager.rotate((float) (t * (5 + 12 * power)), 1, 1, 0);
            VfxDraw.additive();
            double cs = px * (1.5 + 0.35 * pulse + 0.6 * power);
            ArmorCoreGeometry.cube(0, 0, 0, cs, glow, 0.95f);
            ArmorCoreGeometry.cube(0, 0, 0, cs * 0.6, core, 1f);
            }finally{GlStateManager.popMatrix();}
            // 5. two orbit rings of voxel motes on tilted planes
            for (int ring = 0; ring < 2; ring++) {
                GlStateManager.pushMatrix();
                try{GlStateManager.translate(0, 0, -0.03);
                GlStateManager.rotate(ring == 0 ? 25 : -35, 1, 0, 0);
                GlStateManager.rotate(ring == 0 ? 15 : -20, 0, 1, 0);
                double spin = t * (0.06 + 0.12 * power) * (ring == 0 ? 1 : -1);
                for (int i = 0; i < 6; i++) {
                    double a = spin + i * Math.PI / 3, r = (ring == 0 ? 3.0 : 3.8) * px;
                    ArmorCoreGeometry.cube(Math.cos(a) * r, Math.sin(a) * r, 0, px * (ring == 0 ? 0.45 : 0.35), ring == 0 ? core : glow, (float) (0.55 + 0.45 * power));
                }
                }finally{GlStateManager.popMatrix();}
            }
            // 6. bloom: soft halo + hard glow, and flaring light shafts
            VfxDraw.additive();
            VfxDraw.sphere(0, 0, -0.03, px * (3.2 + 2.5 * power + pulse), glow, (float) (0.06 + 0.10 * power), 10);
            VfxDraw.sphere(0, 0, -0.03, px * (1.9 + 1.0 * power), core, (float) (0.18 + 0.2 * power), 8);
            if(charge>0){double radius=px*(9-6*charge);for(int i=0;i<12;i++){double a=i*Math.PI/6+t*.2;ArmorCoreGeometry.cube(Math.cos(a)*radius,Math.sin(a)*radius,-.05,px*.5,i%2==0?core:glow,(float)(.4+.6*charge));}}
            if(flash>0){VfxDraw.sphere(0,0,-.03,px*(4+26*(1-flash)),core,(float)(.5*flash),10);VfxDraw.sphere(0,0,-.03,px*(3+8*(1-flash)),0xFFFFFF,(float)(.6*flash),8);}
            if (power > 0.25) {
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + t * 0.03, len = px * (4 + 10 * (power - 0.25) * (0.6 + 0.4 * Math.sin(t * 0.3 + i)));
                    double[][] in = {{0, 0, -0.04}, {Math.cos(a) * len, Math.sin(a) * len, -0.06}};
                    double[][] out = {{Math.cos(a + 0.5) * px * 0.8, Math.sin(a + 0.5) * px * 0.8, -0.04}, {Math.cos(a + 0.12) * len, Math.sin(a + 0.12) * len, -0.06}};
                    VfxDraw.ribbon(in, out, glow, (float) (0.35 * power), 0f);
                }
            }
        } finally {
            VfxDraw.end();
        }
    }

    @Override public boolean shouldCombineTextures() { return false; }
}

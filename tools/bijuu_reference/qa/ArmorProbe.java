package qa.bijuu;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid="bijuuvisualqa", name="Bijuu Visual QA", version="1.0",
     dependencies="required-after:hvbijuu", clientSideOnly=true)
public class ArmorProbe {
    private String[] roster;
    private Object client;
    private int galleryStage;
    private int worldTicks;
    private int frontIndex;
    private int nextFrontFrame;
    private boolean angled;
    private final boolean turnaround=Boolean.getBoolean("bijuu.turnaround");
    private final Object[] tailMounts=new Object[9];
    private final String[] bearings={"front","side","back","back-tails"};
    private final boolean motionTest=Boolean.getBoolean("bijuu.motion");
    private final Object[] armors=new Object[9];
    private final Object[] bodies=new Object[9];
    private float cameraOffset,cameraPitch;
    private boolean diagnosticControl;
    private Method translate;
    private int motionTick;
    private double startX,startZ,distance,maxBend,initialAngle,maxAngleDelta;
    private boolean crouched;
    private int bearing;
    private float galleryYaw;
    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    @Mod.EventHandler
    public void verify(FMLLoadCompleteEvent event) {
        try {
            Class<?> proxyType = type("village.bijuu.ClientProxy");
            Class<?> modelType = type("net.minecraft.client.model.ModelBiped");
            Class<?> slotType = type("net.minecraft.inventory.EntityEquipmentSlot");
            Class<?> actual = type("village.bijuu.BijuuArmor");
            Class<?> gl = type("org.lwjgl.opengl.GL11");
            Method getError = gl.getMethod("glGetError");
            for (int i=0; i<4 && (Integer)getError.invoke(null)!=0; i++) {}
            Object[] slots = (Object[]) actual.getField("SLOTS").get(null);
            String[] beasts = (String[]) actual.getField("BEASTS").get(null);
            Method model = proxyType.getMethod("model", int.class, slotType, modelType);
            Object proxy = proxyType.newInstance();
            Object base = modelType.newInstance();
            Class<?> mcType = type("net.minecraft.client.Minecraft");
            Object mc = mcType.getMethod("func_71410_x").invoke(null);
            Object manager = mcType.getMethod("func_110434_K").invoke(mc);
            Class<?> resourceType = type("net.minecraft.util.ResourceLocation");
            Method bind = manager.getClass().getMethod("func_110577_a", resourceType);
            Class<?> renderer = type("net.minecraft.client.model.ModelRenderer");
            Method render = renderer.getMethod("func_78785_a",float.class);
            Field visible = renderer.getField("field_78806_j");
            String[] roots = {"field_78116_c","field_78115_e","field_178724_i","field_178723_h","field_178721_j","field_178722_k"};
            int models=0;
            int textures=0;
            int draws=0;
            for (int i=0; i<beasts.length; i++) {
                String path="textures/armor/"+beasts[i]+".png";
                checkImage(actual,path);
                bind.invoke(manager,resourceType.getConstructor(String.class).newInstance("hvbijuu:"+path));
                textures++;
                for (Object slot : slots) {
                    Object armor=model.invoke(proxy,i,slot,base);
                    if((turnaround || motionTest) && slot.toString().equalsIgnoreCase("chest")) {
                        Object body=modelType.getField("field_78115_e").get(armor);
                        java.util.List<?> children=(java.util.List<?>)renderer.getField("field_78805_m").get(body);
                        armors[i]=armor;
                        bodies[i]=body;
                        tailMounts[i]=children.get(children.size()-1);
                    }
                    int before=draws;
                    for (String root : roots) {
                        Object bone=modelType.getField(root).get(armor);
                        if (visible.getBoolean(bone)) {
                            render.invoke(bone,0.0625f);
                            draws++;
                        }
                    }
                    if (before==draws) throw new AssertionError("No visible bones "+beasts[i]+" "+slot);
                    int error=(Integer)getError.invoke(null);
                    if (error!=0) throw new AssertionError("OpenGL "+error+" "+beasts[i]+" "+slot);
                    models++;
                    if (i==8 && slot.toString().equalsIgnoreCase("chest") &&
                        (Integer)armor.getClass().getMethod("renderedTailRoots").invoke(armor)!=9) {
                        throw new AssertionError("Expected nine Kurama tails");
                    }
                }
            }
            String cage="textures/armor/kurama_cage.png";
            checkImage(actual,cage);
            bind.invoke(manager,resourceType.getConstructor(String.class).newInstance("hvbijuu:"+cage));
            textures++;
            if (models!=36 || textures!=10 || (Integer)getError.invoke(null)!=0) {
                throw new AssertionError("Unexpected model counts or GL error");
            }
            System.out.println("BIJUU_VISUAL_QA_PASS "+models+" models "+textures+" atlases "+draws+" native root draws");
            if (Boolean.getBoolean("bijuu.gallery")) {
                roster = beasts;
                client = mc;
                if(motionTest){
                    translate=gl.getMethod("glTranslated",double.class,double.class,double.class);
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
                }
                FMLCommonHandler.instance().bus().register(this);
            } else {
                FMLCommonHandler.instance().exitJava(0, false);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            FMLCommonHandler.instance().exitJava(1, false);
        }
    }

    @SubscribeEvent
    public void galleryTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {
            Class<?> game = client.getClass();
            if (galleryStage == 0) {
                galleryStage++;
                Class<?> settings = type("net.minecraft.world.WorldSettings");
                Class<?> worldType = type("net.minecraft.world.WorldType");
                Class<?> modeType = type("net.minecraft.world.GameType");
                Object flat = worldType.getField("field_77138_c").get(null);
                Object creative = modeType.getField("CREATIVE").get(null);
                Object world = settings.getConstructor(long.class, modeType, boolean.class, boolean.class, worldType)
                                       .newInstance(104L,creative,false,false,flat);
                settings.getMethod("func_77166_b").invoke(world);
                game.getMethod("func_71371_a",String.class,String.class,settings)
                    .invoke(client,"bijuu-qa","Bijuu material proof",world);
                return;
            }
            if (game.getField("field_71439_g").get(client)==null) return;
            Object screen=game.getField("field_71462_r").get(client);
            if(screen!=null && screen.getClass().getSimpleName().equals("GuiIngameMenu")) {
                game.getMethod("func_147108_a",type("net.minecraft.client.gui.GuiScreen")).invoke(client,new Object[]{null});
                nextFrontFrame=worldTicks+12;
                return;
            }
            worldTicks++;
            if(motionTest){playerGallery();return;}
            if (galleryStage==1 && worldTicks>=35) {
                galleryStage++;
                chat("tp @p 0 4 -16 0 2");
            }
            if (galleryStage==2 && worldTicks>=100) {
                galleryStage++;
                chat("gamerule doMobSpawning false");
                chat("gamerule doDaylightCycle false");
                chat("time set 9000");
                chat("weather clear");
                chat("fill -23 3 -23 23 3 11 minecraft:stonebrick");
                for (int i=0;i<roster.length;i++) {
                    String armor = roster[i];
                    String slots = "{id:\"hvbijuu:"+armor+"_boots\",Count:1b},"
                         + "{id:\"hvbijuu:"+armor+"_leggings\",Count:1b},"
                         + "{id:\"hvbijuu:"+armor+"_chestplate\",Count:1b},"
                         + "{id:\"hvbijuu:"+armor+"_helmet\",Count:1b}";
                    chat("summon armor_stand "+(3*(i-4))+" 4 0 "
                         +"{Rotation:[0f],ArmorItems:[" +slots+"]}");
                }
                chat("tp @p 0 4 -16 0 2");
                Object options=game.getField("field_71474_y").get(client);
                options.getClass().getField("field_74319_N").setBoolean(options,true);
            }
            if (galleryStage==3 && worldTicks>=185) {
                galleryStage++;
                Object world=game.getField("field_71441_e").get(client);
                Iterable<?> visible=(Iterable<?>)world.getClass().getField("field_72996_f").get(world);
                int stands=0;
                for (Object entity : visible) {
                    if (entity.getClass().getSimpleName().equals("EntityArmorStand")) stands++;
                }
                if (stands!=9) throw new AssertionError("Expected nine equipped stands, found "+stands);
                screenshot("bijuu-4k-bsl-roster.png");
                positionCamera();
            }
            if (galleryStage==4) {
                // Lock the fixture's look direction while the teleported frame
                // settles. This removes variance from GUI focus/mouse input.
                Object player=game.getField("field_71439_g").get(client);
                Class<?> entity=player.getClass();
                entity.getField("field_70177_z").setFloat(player,galleryYaw);
                entity.getField("field_70126_B").setFloat(player,galleryYaw);
                entity.getField("field_70125_A").setFloat(player,0.0f);
                entity.getField("field_70127_C").setFloat(player,0.0f);
                if (worldTicks<nextFrontFrame) return;
                if(turnaround) {
                    screenshot(bearings[bearing]+"-"+roster[frontIndex]+".png");
                    bearing=(bearing+1)%bearings.length;
                    if(bearing==0)frontIndex++;
                } else {
                screenshot((angled ? "angle-" : "front-")+roster[frontIndex]+".png");
                if (angled) frontIndex++;
                angled=!angled;
                }
                if (frontIndex<roster.length) {
                    positionCamera();
                    return;
                }
                galleryStage++;
                System.out.println("BIJUU_BSL_GALLERY_PASS");
                FMLCommonHandler.instance().exitJava(0,false);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            FMLCommonHandler.instance().exitJava(1,false);
        }
    }

    private static double coord(Object object,String name) throws Exception {
        return object.getClass().getField(name).getDouble(object);
    }
    private static float angle(Object object,String name) throws Exception {
        return object.getClass().getField(name).getFloat(object);
    }
    private void input(Object settings,String field,boolean pressed) throws Exception {
        Object binding=settings.getClass().getField(field).get(settings);
        int code=(Integer)binding.getClass().getMethod("func_151463_i").invoke(binding);
        binding.getClass().getMethod("func_74510_a",int.class,boolean.class).invoke(null,code,pressed);
    }
    @SubscribeEvent
    public void orbit(net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup event) {
        if(!motionTest || client==null || worldTicks<45)return;
        event.setYaw(event.getYaw()+cameraOffset);
        event.setPitch(event.getPitch()+cameraPitch);
        try {translate.invoke(null,0.0,0.0,-1.7);}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private void diagnosticSkin(Object player,boolean green) throws Exception {
        int rgb=green?0xff00ff10:0xffff00d8;
        if(diagnosticControl && Boolean.getBoolean("bijuu.neutralSkin"))rgb=0xffdddddd;
        BufferedImage skin=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)skin.setRGB(x,y,rgb);
        if(diagnosticControl && Boolean.getBoolean("bijuu.visorFace")){
            try(java.io.InputStream stream=ArmorProbe.class.getResourceAsStream("/assets/minecraft/textures/entity/steve.png")){
                BufferedImage steve=javax.imageio.ImageIO.read(stream);
                for(int y=0;y<16;y++)for(int x=0;x<64;x++)skin.setRGB(x,y,steve.getRGB(x,y));
            }
        }
        Object id=player.getClass().getMethod("func_110306_p").invoke(player);
        Object texture=type("net.minecraft.client.renderer.texture.DynamicTexture").getConstructor(BufferedImage.class).newInstance(skin);
        Object manager=client.getClass().getMethod("func_110434_K").invoke(client);
        manager.getClass().getMethod("func_110579_a",type("net.minecraft.util.ResourceLocation"),
            type("net.minecraft.client.renderer.texture.ITextureObject")).invoke(manager,id,texture);
    }
    private void playerGallery() throws Exception {
        if(worldTicks==42)chat("tp @p 0.5 4 0.5 0 0");
        if(worldTicks==75){
            Object actor=client.getClass().getField("field_71439_g").get(client);
            Object opts=client.getClass().getField("field_71474_y").get(client);
            opts.getClass().getField("field_74320_O").setInt(opts,1);
            opts.getClass().getField("field_74319_N").setBoolean(opts,true);
            cameraOffset=180;diagnosticSkin(actor,false);
        }
        if(worldTicks==100){screenshot("skin-control.png");diagnosticControl=true;}
        if(worldTicks<115)return;
        Object player=client.getClass().getField("field_71439_g").get(client);
        Object settings=client.getClass().getField("field_71474_y").get(client);
        int t=motionTick++;
        String key=roster[frontIndex];
        if(t==0){
            cameraOffset=180f;cameraPitch=0f;
            if(!diagnosticControl)throw new AssertionError("Missing skin control");
            diagnosticSkin(player,frontIndex==7 || frontIndex==1);
            input(settings,"field_74351_w",false);input(settings,"field_74311_E",false);
            settings.getClass().getField("field_74320_O").setInt(settings,1);
            settings.getClass().getField("field_74319_N").setBoolean(settings,true);
            if(frontIndex==0){
                chat("gamerule doMobSpawning false");chat("gamerule doDaylightCycle false");chat("time set 9000");chat("weather clear");
                chat("fill -14 3 -14 14 3 14 minecraft:stonebrick");
            }
            chat("tp @p 0.5 4 0.5 0 0");
            String[] slots={"head","chest","legs","feet"},items={"helmet","chestplate","leggings","boots"};
            for(int i=0;i<4;i++)chat("replaceitem entity @p slot.armor."+slots[i]+" hvbijuu:"+key+"_"+items[i]);
            distance=0;maxBend=0;maxAngleDelta=0;crouched=false;
        }
        if(t==28 || t==46 || t==64)cameraOffset=t==28?180f:t==46?90f:0f;
        if(t==45 || t==63 || t==81)screenshot(new String[]{"front","side","back"}[(t-45)/18]+"-"+key+".png");
        if(t==82){
            cameraOffset=62f;settings.getClass().getField("field_74320_O").setInt(settings,1);
            startX=coord(player,"field_70165_t");startZ=coord(player,"field_70161_v");
        }
        if(t>=83 && t<142){
            float yaw=t<100?0f:Math.min(90f,(t-100)*5f);
            player.getClass().getField("field_70177_z").setFloat(player,yaw);
            input(settings,"field_74351_w",true);input(settings,"field_74311_E",t>=117 && t<=134);
            distance=Math.max(distance,Math.hypot(coord(player,"field_70165_t")-startX,coord(player,"field_70161_v")-startZ));
            crouched|=(Boolean)player.getClass().getMethod("func_70093_af").invoke(player);
            Object root=tailMounts[frontIndex],body=bodies[frontIndex];
            java.util.List<?> children=(java.util.List<?>)body.getClass().getField("field_78805_m").get(body);
            if(!children.contains(root)||root.getClass().getField("field_78807_k").getBoolean(root)
                ||angle(root,"field_78800_c")!=0 ||angle(root,"field_78797_d")!=10 ||angle(root,"field_78798_e")!=2.5f)
                throw new AssertionError("Waist mount shifted on "+key);
            maxBend=Math.max(maxBend,Math.abs(angle(body,"field_78795_f")));
            Class<?> adapter=type("village.bijuu.ReferenceTails");
            Field rendered=adapter.getDeclaredField("rendered");rendered.setAccessible(true);
            Object armor=armors[frontIndex];
            java.util.List<?> roots=(java.util.List<?>)((java.util.Map<?,?>)rendered.get(null)).get(armor);
            if(roots.size()!=frontIndex+1)throw new AssertionError("Wrong visible appendage count "+key);
            Object first=roots.get(0);
            double value=angle(first,"field_78795_f")+angle(first,"field_78796_g")+angle(first,"field_78808_h");
            if(t==83)initialAngle=value;
            maxAngleDelta=Math.max(maxAngleDelta,Math.abs(value-initialAngle));
            if((t-85)%4==0 && t>=85)screenshot(String.format("motion-%s-%03d.png",key,t));
        }
        if(t==142){
            input(settings,"field_74351_w",false);input(settings,"field_74311_E",false);
            cameraOffset=147;cameraPitch=26;
        }
        if(t==163){
            screenshot("shoulders-"+key+".png");
            input(settings,"field_74351_w",false);input(settings,"field_74311_E",false);
            if(distance<1 || !crouched)throw new AssertionError("Player motion missing "+key+" "+distance+" "+crouched);
            System.out.println("BIJUU_PLAYER_MOTION "+key+" distance="+distance+" bodyBend="+maxBend+" rootAngleDelta="+maxAngleDelta+" waist=(0,10,2.5)");
            if(++frontIndex<roster.length){motionTick=0;return;}
            System.out.println("BIJUU_BSL_GALLERY_PASS");FMLCommonHandler.instance().exitJava(0,false);
        }
    }

    private void positionCamera() throws Exception {
        if(turnaround) {
            double center=3*(frontIndex-4)+0.5;
            int rotation=bearing==0?0:bearing==1?90:180;
            Object mount=tailMounts[frontIndex];
            mount.getClass().getField("field_78807_k").setBoolean(mount,bearing==1 || bearing==2);
            chat("entitydata @e[type=armor_stand,x="+(int)Math.floor(center)+",y=4,z=0,r=2,c=1] {Rotation:["+rotation+"f,0f]}");
            galleryYaw=180f;
            chat("tp @p "+center+" 4 3.7 180 0");
            nextFrontFrame=worldTicks+32;
            return;
        }
        double x=3*(frontIndex-4)+0.5+(angled ? 2.0 : 0.0);
        galleryYaw=angled ? 148.0f : 180.0f;
        chat("tp @p "+x+" 4 3.7 "+galleryYaw+" 0");
        nextFrontFrame=worldTicks+30;
    }

    private void chat(String command) throws Exception {
        Object p = client.getClass().getField("field_71439_g").get(client);
        p.getClass().getMethod("func_71165_d",String.class).invoke(p,"/"+command);
    }

    private void screenshot(String name) throws Exception {
        Class<?> shaders=type("net.optifine.shaders.Shaders");
        if (!shaders.getField("shaderPackLoaded").getBoolean(null)
            || !shaders.getField("isShaderPackInitialized").getBoolean(null)
            || !shaders.getField("configNormalMap").getBoolean(null)
            || !shaders.getField("configSpecularMap").getBoolean(null)) {
            throw new AssertionError("Expected initialized BSL with both material maps enabled");
        }
        Class<?> game=client.getClass();
        Object fb=game.getMethod("func_147110_a").invoke(client);
        int width=game.getField("field_71443_c").getInt(client);
        int height=game.getField("field_71440_d").getInt(client);
        File root=new File(System.getProperty("user.dir"));
        type("net.minecraft.util.ScreenShotHelper")
            .getMethod("func_148259_a",File.class,String.class,int.class,int.class,fb.getClass())
            .invoke(null,root,name,width,height,fb);
        if (!new File(new File(root,"screenshots"),name).isFile()) {
            throw new AssertionError("Screenshot missing: "+name);
        }
    }
    private static void checkImage(Class<?> owner,String path) throws Exception {
        try (InputStream stream=owner.getResourceAsStream("/assets/hvbijuu/"+path)) {
            BufferedImage image=ImageIO.read(stream);
            int expected=Integer.getInteger("bijuu.atlasSize",4096);
            if (image.getWidth()!=expected || image.getHeight()!=expected || !image.getColorModel().hasAlpha()) {
                throw new AssertionError("Invalid atlas "+path);
            }
        }
    }
}

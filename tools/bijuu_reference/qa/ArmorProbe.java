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
            worldTicks++;
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
                entity.getField("field_70125_A").setFloat(player,8.0f);
                entity.getField("field_70127_C").setFloat(player,8.0f);
                if (worldTicks<nextFrontFrame) return;
                screenshot((angled ? "angle-" : "front-")+roster[frontIndex]+".png");
                if (angled) frontIndex++;
                angled=!angled;
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

    private void positionCamera() throws Exception {
        double x=3*(frontIndex-4)+0.5+(angled ? 2.0 : 0.0);
        galleryYaw=angled ? 148.0f : 180.0f;
        chat("tp @p "+x+" 4 3.7 "+galleryYaw+" 8");
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
            if (image.getWidth()!=4096 || image.getHeight()!=4096 || !image.getColorModel().hasAlpha()) {
                throw new AssertionError("Invalid atlas "+path);
            }
        }
    }
}

package village.vale.client;
/** Optional shared display preferences, without a compile dependency on Field HUD. */
final class VfxPreferences {
 private VfxPreferences(){}private static boolean checked;private static java.lang.reflect.Field motion,mode;
 static boolean motion(){if(JutsuVfx.detail==0)return false;if(!checked){checked=true;try{Class<?> c=Class.forName("village.fieldhud.HudConfig",false,net.minecraftforge.fml.common.Loader.instance().getModClassLoader());motion=c.getField("motion");mode=c.getField("mode");}catch(ReflectiveOperationException|RuntimeException|LinkageError absent){motion=mode=null;}}try{return motion==null||motion.getBoolean(null)&&mode.getInt(null)!=2;}catch(IllegalAccessException|RuntimeException unavailable){return false;}}
}

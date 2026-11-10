package village.controls;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Exercises the optional API across genuinely isolated HUD class versions. */
public final class FocusedCastChecks {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static URLClassLoader fixture(Path root,String name,String members)throws Exception {
        Path dir=root.resolve(name),file=dir.resolve("village/fieldhud/ClientApi.java");Files.createDirectories(file.getParent());
        Files.write(file,("package village.fieldhud; public class ClientApi {"+members+"}").getBytes(StandardCharsets.UTF_8));
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        check(compiler!=null,"The Java8 test launcher must provide the fixture compiler");
        check(compiler.run(null,null,null,"-source","8","-target","8","-d",dir.toString(),file.toString())==0,"HUD fixture must compile");
        return new URLClassLoader(new URL[]{dir.toUri().toURL()},null);
    }
    private static void ordinary(Path root,String name,String body,boolean consumed,boolean failed)throws Exception {
        List<Throwable> failures=new ArrayList<>();
        try(URLClassLoader loader=fixture(root,name,"public static int calls; public static boolean castFocusedJutsu(){calls++;"+body+"}")) {
            check(FocusedCast.consume(loader,failures::add)==consumed,name+" routes this press correctly");
            check(failures.size()==(failed?1:0),name+" reports exactly the routing failure");
            check(loader.loadClass("village.fieldhud.ClientApi").getField("calls").getInt(null)==1,name+" invokes the HUD exactly once");
            if(failed)check(failures.get(0) instanceof IllegalStateException,"Original invocation cause must be reported");
        }
    }
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);Files.createDirectories(root);List<Throwable> failures=new ArrayList<>();
        try(URLClassLoader absent=new URLClassLoader(new URL[0],null)){
            check(!FocusedCast.consume(absent,failures::add),"Absent HUD permits the established server command");
            check(failures.isEmpty(),"Absent optional HUD is not a routing error");
        }
        try(URLClassLoader old=fixture(root,"old-api","public static void legacyMethod(){}")){
            check(!FocusedCast.consume(old,failures::add),"Older HUD without this method permits fallback");
            check(failures.isEmpty(),"Older optional HUD is not a routing error");
        }
        ordinary(root,"item-focus","return false;",false,false);
        ordinary(root,"jutsu-focus","return true;",true,false);
        ordinary(root,"blocked-jutsu","return true;",true,false);
        ordinary(root,"throwing-hud","throw new IllegalStateException(\"blocked\");",true,true);
        String[][] invalid={{"instance-api","public boolean castFocusedJutsu(){return false;}"},{"boxed-api","public static Boolean castFocusedJutsu(){return false;}"},{"wrong-return","public static void castFocusedJutsu(){}"},{"init-failure","static {if(System.nanoTime()!=0)throw new IllegalStateException(\"init\");} public static boolean castFocusedJutsu(){return false;}"}};
        for(String[] fixture:invalid){
            failures.clear();try(URLClassLoader loader=fixture(root,fixture[0],fixture[1])){
                check(FocusedCast.consume(loader,failures::add),"Broken HUD contract must consume, never cast the old selected art: "+fixture[0]);
                check(failures.size()==1,"Broken HUD contract must be reported once: "+fixture[0]);
            }
        }
        for(final boolean security:new boolean[]{false,true}) {
            failures.clear();ClassLoader broken=new ClassLoader(null){@Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(security)throw new SecurityException("fixture");throw new NoClassDefFoundError("fixture");}};
            check(FocusedCast.consume(broken,failures::add),"Broken class loading must fail closed");
            check(failures.size()==1,"Broken class loading must report once");
        }
        for(String type:new String[]{"OutOfMemoryError","ThreadDeath"}) {
            failures.clear();try(URLClassLoader loader=fixture(root,"fatal-"+type,"public static boolean castFocusedJutsu(){throw new "+type+"();}")){
                boolean propagated=false;
                try{FocusedCast.consume(loader,failures::add);}catch(VirtualMachineError|ThreadDeath fatal){propagated=true;}
                check(propagated,"Fatal runtime errors must propagate");check(failures.isEmpty(),"Fatal errors are not an optional integration failure");
            }
        }
        System.out.println("PASS: "+checks+" isolated HUD cast-adapter checks");
    }
}

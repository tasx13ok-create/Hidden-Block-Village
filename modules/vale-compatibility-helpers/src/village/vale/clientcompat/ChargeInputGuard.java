package village.vale.clientcompat;

import java.io.*;import java.net.*;import java.nio.file.*;import java.security.*;import java.util.zip.*;
import net.minecraft.launchwrapper.Launch;

/** Exact private source identity, not a redistribution/licensing assertion. */
final class ChargeInputGuard {
    static final String TARGET="narutomodaddon.keybind.KeyBindingChakraChargeButton";
    static final String ORIGINAL="445a71010f1b9e3893a83294c8ad86ba487adec6951acdeaef68baf2322bab2d";
    static final String CLIENT="02274386f5459cb55227127965e03ad7423ba02a5badcd9a5ab38f8f0d1cd1f9";
    static final String SERVER="688558b76626df84843a0348ee8b006f0ddf7d3fb10002f681986fdf72ecb27b";
    static final String JAR="7eb82c19936f3902a19e477da8bd457250c7ceca2fef5eff6d4c574595f777bf";
    static String input(byte[] bytes)throws Exception{
        String value=hash(new ByteArrayInputStream(bytes));
        if(!value.equals(ORIGINAL)&&!value.equals(CLIENT)&&!value.equals(SERVER))throw new IllegalStateException("Unreviewed native charge input bytes: "+value);
        return value;
    }
    static void source()throws Exception{
        String entry=TARGET.replace('.','/')+".class";URL resource=Launch.classLoader.getResource(entry);
        if(resource==null||!"jar".equals(resource.getProtocol()))throw new IllegalStateException("Reviewed local addon JAR required");
        JarURLConnection connection=(JarURLConnection)resource.openConnection();connection.setUseCaches(false);
        if(!"file".equals(connection.getJarFileURL().getProtocol()))throw new IllegalStateException("Local source JAR required");
        Path path=Paths.get(connection.getJarFileURL().toURI());if(!JAR.equals(hash(Files.newInputStream(path))))throw new IllegalStateException("Unknown Naruto addon source JAR");
        try(ZipFile zip=new ZipFile(path.toFile())){
            ZipEntry value=zip.getEntry(entry);if(value==null||!ORIGINAL.equals(hash(zip.getInputStream(value))))throw new IllegalStateException("Unknown native charge class identity");
        }
    }
    static String hash(InputStream input)throws Exception{
        try(InputStream in=input){MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);StringBuilder value=new StringBuilder();for(byte b:digest.digest())value.append(String.format("%02x",b&255));return value.toString();}
    }
}

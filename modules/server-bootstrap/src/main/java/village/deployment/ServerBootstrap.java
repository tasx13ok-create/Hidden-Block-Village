package village.deployment;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** One-time, bounded replacement of explicitly inventoried superseded files.
 * Saved worlds, player records and credentials are never selected by this code.
 * The normal server main class runs only after the release preflight succeeds.
 */
public final class ServerBootstrap {
    private static final String RESOURCE="vale-release.properties";
    private static final String DELEGATE="net.minecraftforge.fml.relauncher.ServerLaunchWrapper";
    private ServerBootstrap() {}

    public static void main(String[] args) throws Exception {
        if(!System.getProperty("java.specification.version","").equals("1.8"))
            throw new IllegalStateException("This Minecraft1.12.2 server requires Java8. Select Java8 in the Bisect Home panel before starting.");
        Path jar=Paths.get(ServerBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        if(!Files.isRegularFile(jar,LinkOption.NOFOLLOW_LINKS))throw new IOException("Launch the packaged server JAR, not a loose class directory.");
        Path root=jar.getParent().toRealPath();
        Properties plan=new Properties();
        try(InputStream input=ServerBootstrap.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if(input==null)throw new IOException("The embedded release plan is missing.");
            plan.load(new InputStreamReader(input,StandardCharsets.UTF_8));
        }
        String release=plan.getProperty("release","");
        if(!release.matches("[A-Za-z0-9_-]{1,100}"))throw new IOException("Invalid release identifier.");
        Path state=resolve(root,"vale-update-state/"+release+".done");
        String marker="VALE_RELEASE="+release+"\n";
        if(Files.exists(state,LinkOption.NOFOLLOW_LINKS)) {
            if(!sameMarker(state,marker))
                throw new IOException("Release marker is invalid; no files were changed.");
            System.out.println("VALE_DEPLOYMENT: release already installed; normal startup.");
        } else {
            int expected=bounded(plan,"expected.count",1,2000),retired=bounded(plan,"retired.count",0,5000);
            // Validate the whole expected release before moving any old file.
            Set<String> protectedPaths=new HashSet<>();
            for(int i=0;i<expected;i++) {
                String rel=plan.getProperty("expected."+i+".path","");String hash=requiredHash(plan,"expected."+i+".sha256");
                Path file=resolve(root,rel);protectedPaths.add(rel.toLowerCase(Locale.ROOT));
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!hash.equals(sha(file)))
                    throw new IOException("Release file is missing or changed: "+rel+". Re-extract the complete update before starting.");
            }
            Path pending=resolve(root,"vale-update-state/"+release+".pending");
            if(Files.exists(pending,LinkOption.NOFOLLOW_LINKS)&&!sameMarker(pending,marker))throw new IOException("Unexpected pending deployment marker.");
            List<Move> moves=new ArrayList<>();Set<String> retiredPaths=new HashSet<>();
            for(int i=0;i<retired;i++) {
                String rel=plan.getProperty("retired."+i+".path","");String hash=requiredHash(plan,"retired."+i+".sha256");
                if(!allowedRetired(rel)||protectedPaths.contains(rel.toLowerCase(Locale.ROOT))||!retiredPaths.add(rel.toLowerCase(Locale.ROOT)))
                    throw new IOException("Invalid retirement path: "+rel);
                Path from=resolve(root,rel),to=resolve(root,"vale-update-backup/"+release+"/"+rel);
                if(!Files.exists(from,LinkOption.NOFOLLOW_LINKS))continue;
                if(!Files.isRegularFile(from,LinkOption.NOFOLLOW_LINKS)||!hash.equals(sha(from)))
                    throw new IOException("Old file differs from the audited server download: "+rel+". It was not removed.");
                if(Files.exists(to,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("A rollback destination already exists: "+rel+". It was not overwritten.");
                moves.add(new Move(from,to,hash));
            }
            // Do not quietly launch an additional unreviewed mod/plugin from an
            // old live installation that was absent from the downloaded archive.
            for(String folder:new String[]{"mods","mods/1.12.2","plugins","plugins/update"}) {
                Path directory=resolve(root,folder);
                if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))continue;
                try(DirectoryStream<Path> files=Files.newDirectoryStream(directory)) {
                    for(Path file:files) {
                        if(Files.isSymbolicLink(file))throw new IOException("Symlink in runtime folder: "+folder);
                        if(!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))continue;
                        String rel=folder+"/"+file.getFileName();String key=rel.toLowerCase(Locale.ROOT);
                        if(!protectedPaths.contains(key)&&!retiredPaths.contains(key))
                            throw new IOException("An extra runtime JAR is not in this release: "+rel+". Move it outside "+folder+" before the first start.");
                    }
                }
            }
            for(Move move:moves) {
                Files.createDirectories(move.to.getParent());
                // Re-check containment/symlinks and bytes immediately before a
                // move; existing destinations are never replaced.
                resolve(root,root.relativize(move.from).toString().replace('\\','/'));
                resolve(root,root.relativize(move.to).toString().replace('\\','/'));
                if(!move.hash.equals(sha(move.from)))throw new IOException("A superseded file changed during installation.");
                Files.move(move.from,move.to);
                System.out.println("VALE_DEPLOYMENT: preserved superseded file outside runtime: "+root.relativize(move.from));
            }
            Files.createDirectories(state.getParent());
            if(Files.exists(pending,LinkOption.NOFOLLOW_LINKS)) {
                if(!sameMarker(pending,marker))
                    throw new IOException("Unexpected pending deployment marker.");
            } else Files.write(pending,marker.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);
            Files.move(pending,state);
            System.out.println("VALE_DEPLOYMENT: installed "+release+"; preserved "+moves.size()+" superseded files in vale-update-backup. Saved worlds/player data were not moved.");
        }
        for(String arg:args)if(arg.equals("--vale-upgrade-check-only"))return;
        // Keep the verified runtime pinned; Magma's own documented flag disables
        // its automatic JAR updater without changing the original runtime code.
        List<String> launch=new ArrayList<>(Arrays.asList(args));if(!launch.contains("-dau"))launch.add("-dau");
        try { Class.forName(DELEGATE).getMethod("main",String[].class).invoke(null,(Object)launch.toArray(new String[0])); }
        catch(InvocationTargetException failure) {
            Throwable cause=failure.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw failure;
        }
    }
    private static boolean allowedRetired(String rel) {
        String[] parts=rel.split("/",-1);
        if(rel.endsWith(".jar")&&parts.length==2&&(parts[0].equals("mods")||parts[0].equals("plugins")))return true;
        if(rel.endsWith(".jar")&&parts.length==3&&(rel.startsWith("mods/1.12.2/")||rel.startsWith("plugins/update/")))return true;
        return parts.length==4&&rel.startsWith("plugins/CrazyCrates/Crates/")&&(rel.endsWith(".yml")||rel.endsWith(".yaml"));
    }
    private static boolean sameMarker(Path file,String value)throws IOException {
        return Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&Files.size(file)<=256&&new String(Files.readAllBytes(file),StandardCharsets.UTF_8).equals(value);
    }
    private static int bounded(Properties p,String key,int minimum,int maximum)throws IOException {
        try { int value=Integer.parseInt(p.getProperty(key,""));if(value<minimum||value>maximum)throw new NumberFormatException();return value; }
        catch(NumberFormatException bad) { throw new IOException("Invalid deployment count: "+key); }
    }
    private static String requiredHash(Properties p,String key)throws IOException {
        String value=p.getProperty(key,"");if(!value.matches("[0-9a-f]{64}"))throw new IOException("Invalid deployment fingerprint.");return value;
    }
    private static Path resolve(Path root,String rel)throws IOException {
        if(rel.length()==0||rel.indexOf(':')>=0||rel.startsWith("/")||rel.startsWith("\\"))throw new IOException("Invalid deployment path.");
        for(int i=0;i<rel.length();i++)if(rel.charAt(i)<32)throw new IOException("Control character in deployment path.");
        String[] parts=rel.replace('\\','/').split("/",-1);Path path=root;
        for(String part:parts) {
            if(part.length()==0||part.equals(".")||part.equals("..")||part.endsWith(".")||part.endsWith(" "))throw new IOException("Unsafe deployment path.");
            path=path.resolve(part);
            if(Files.isSymbolicLink(path))throw new IOException("Deployment path contains a symlink.");
        }
        path=path.toAbsolutePath().normalize();if(!path.startsWith(root)||path.equals(root))throw new IOException("Deployment path escaped the server folder.");return path;
    }
    private static String sha(Path file)throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];
            try(InputStream input=Files.newInputStream(file)) { for(int read;(read=input.read(buffer))!=-1;)digest.update(buffer,0,read); }
            StringBuilder result=new StringBuilder(64);for(byte b:digest.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
        } catch(NoSuchAlgorithmException unavailable) { throw new IOException(unavailable); }
    }
    private static final class Move {
        final Path from,to;final String hash;Move(Path from,Path to,String hash){this.from=from;this.to=to;this.hash=hash;}
    }
}

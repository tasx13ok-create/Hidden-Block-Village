package village.vale.expedition;

import java.io.*;
import java.nio.file.*;

/** Ships original reviewed story data; measured world/NPC bindings remain explicit local configuration. */
public final class ExpeditionContent {
    private ExpeditionContent(){}
    private static final String[] FILES={"gates.json","dialogue/keeper_hanae.json","dialogue/bellwarden_tsumugi.json",
        "dialogue/cisternkeeper_rihane.json","dialogue/pilot_shiori.json","dialogue/forgesentinel_kagero.json"};
    public static void initialize(File config){
        org.apache.logging.log4j.Logger log=org.apache.logging.log4j.LogManager.getLogger("ValeExpeditions");
        Path root=config.toPath().resolve("vale-expeditions");
        try{
            Files.createDirectories(root);
            for(String name:FILES){
                Path target=root.resolve(name);
                if(Files.isSymbolicLink(target))throw new IOException("Expedition file cannot be a symbolic link: "+name);
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))continue;
                Files.createDirectories(target.getParent());
                try(InputStream source=ExpeditionContent.class.getResourceAsStream("/assets/valeorders/expeditions/"+name)){
                    if(source==null)throw new IOException("Missing packaged expedition data: "+name);
                    try{Files.copy(source,target);}catch(FileAlreadyExistsException raced){/* Preserve an operator-created file. */}
                }
            }
            log.info(GateRuntime.load(root.resolve("gates.json").toFile(),root.resolve("sites.json").toFile()));
        }catch(IOException failure){log.error("Expedition content unavailable; ordinary progression remains available",failure);}
    }
}

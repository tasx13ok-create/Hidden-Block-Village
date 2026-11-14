package village.vale.bridge;
import java.io.*;import java.nio.file.*;

/** Testable all-or-nothing configuration publication shared by reload and permissioned edits. */
public final class BridgeConfig {
    private final Path path;private final BridgeMappings.Registry registry;private BridgeMappings active=BridgeMappings.empty();private boolean writable;
    public BridgeConfig(Path path,BridgeMappings.Registry registry){this.path=path;this.registry=registry;}
    public BridgeMappings snapshot(){return active;}public boolean writable(){return writable;}
    public void initialize()throws IOException{if(!Files.exists(path))BridgeFiles.atomic(path,BridgeMappings.empty().bytes(),BridgeMappings.MAX_BYTES);reload();}
    public void reload()throws IOException{
        try{BridgeMappings next=BridgeMappings.read(BridgeFiles.read(path,BridgeMappings.MAX_BYTES),registry);active=next;writable=true;}
        catch(IOException failure){writable=false;throw failure;}
    }
    public void change(BridgeMappings.Row row,String removeKey)throws IOException{
        if(!writable)throw new IOException("Invalid/unknown configuration is preserved read-only; review and reload first");
        BridgeMappings next=active.changed(row,removeKey,registry);BridgeFiles.atomic(path,next.bytes(),BridgeMappings.MAX_BYTES);active=next;
    }
}

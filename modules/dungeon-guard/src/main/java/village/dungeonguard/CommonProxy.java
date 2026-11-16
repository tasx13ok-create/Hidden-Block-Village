package village.dungeonguard;

import java.io.File;

/** Dedicated-server-safe boundary: this class must never reference client classes. */
public class CommonProxy {
    public void preInit(File configurationDirectory) { }
    public void receiveArea(AreaState area) { }
}

package village.vale.clientcompat;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.zip.*;

/** Exact private compatibility input identity, not a certificate-policy override. */
final class SupportedNpcSource {
    static final String JAR_SHA256 = "9002f091bd06dd08bbb0c93b57a9279729b6716813094954a5d57cef101b4e0b";
    static final String CLASS_SHA256 = "d5a51df78c0a0917a50f88ffd9cb76c0490388ee50c6337ce33f0a60069ff4a9";
    static final String VERSION = "CustomNPCs 1.12.2-(05Jul20)-SpringAddon-compat-r1; mcmod.info version 1.12";
    private SupportedNpcSource() {}
    static void verify(URL resource) {
        try {
            if (resource == null || !"jar".equals(resource.getProtocol()))
                throw new IllegalStateException("CustomNPCs GUI hook requires the reviewed local source JAR");
            JarURLConnection connection = (JarURLConnection) resource.openConnection();
            connection.setUseCaches(false);
            if (!"file".equals(connection.getJarFileURL().getProtocol()))
                throw new IllegalStateException("Remote CustomNPCs GUI source forbidden");
            Path jar = Paths.get(connection.getJarFileURL().toURI());
            if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || !JAR_SHA256.equals(hash(Files.newInputStream(jar))))
                throw new IllegalStateException("Unsupported CustomNPCs GUI source JAR hash/version");
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                String entryName = NpcGuiThreadTransformer.TARGET.replace('.', '/') + ".class";
                ZipEntry entry = zip.getEntry(entryName);
                if (!entryName.equals(connection.getEntryName()) || entry == null || !CLASS_SHA256.equals(hash(zip.getInputStream(entry))))
                    throw new IllegalStateException("Unsupported CustomNPCs GUI source class hash");
            }
        } catch (IllegalStateException unsupported) { throw unsupported; }
        catch (Exception failure) { throw new IllegalStateException("Cannot verify CustomNPCs GUI hook source", failure); }
    }
    private static String hash(InputStream input) throws Exception {
        try (InputStream stream = input) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536]; int count;
            while ((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count);
            StringBuilder text = new StringBuilder();
            for (byte value : digest.digest()) text.append(String.format("%02x", value & 255));
            return text.toString();
        }
    }
}

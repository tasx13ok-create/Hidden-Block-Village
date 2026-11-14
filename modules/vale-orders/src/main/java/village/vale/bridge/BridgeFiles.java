package village.vale.bridge;
import java.io.*;import java.nio.*;import java.nio.channels.*;import java.nio.file.*;

/** Bounded reads and atomic replace: the active map changes only after a complete durable write. */
public final class BridgeFiles {
    private BridgeFiles(){}
    public static byte[] read(Path path,int limit)throws IOException{
        if(Files.isSymbolicLink(path)||!Files.isRegularFile(path)||Files.size(path)>limit)throw new IOException("Invalid/oversized bridge file");
        try(InputStream input=Files.newInputStream(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] data=new byte[4096];int count,total=0;while((count=input.read(data))!=-1){total+=count;if(total>limit)throw new IOException("Bridge file grew beyond bound");out.write(data,0,count);}return out.toByteArray();
        }
    }
    public static void atomic(Path path,byte[] bytes,int limit)throws IOException{
        if(bytes==null||bytes.length>limit)throw new IOException("Bridge output exceeds bound");
        Path folder=path.toAbsolutePath().normalize().getParent();Files.createDirectories(folder);if(Files.isSymbolicLink(folder)||Files.isSymbolicLink(path))throw new IOException("Bridge symlink target rejected");
        Path temp=Files.createTempFile(folder,".bridge-",".tmp");boolean moved=false;
        try{
            try(FileChannel file=FileChannel.open(temp,StandardOpenOption.WRITE)){ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())file.write(buffer);file.force(true);}
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);moved=true;
        }finally{if(!moved)Files.deleteIfExists(temp);}
    }
}

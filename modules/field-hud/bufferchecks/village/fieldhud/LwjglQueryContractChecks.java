package village.fieldhud;
import java.nio.*;import org.lwjgl.BufferChecks;import org.lwjgl.BufferUtils;
/** Runs the actual installed LWJGL2 BufferChecks without an OpenGL context or native game. */
public final class LwjglQueryContractChecks {
 static int count;static void ok(boolean value){count++;if(!value)throw new AssertionError("contract "+count);}
 static void reject(Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException expected){failed=true;}ok(failed);}
 public static void main(String[] args){
  BufferChecks.checkBuffer(BufferUtils.createFloatBuffer(16),16);ok(true);
  BufferChecks.checkBuffer(BufferUtils.createIntBuffer(16),16);ok(true);
  BufferChecks.checkBuffer(BufferUtils.createDoubleBuffer(16),16);ok(true);
  BufferChecks.checkBuffer(BufferUtils.createByteBuffer(16),16);ok(true);
  reject(()->BufferChecks.checkBuffer(BufferUtils.createFloatBuffer(4),16));
  reject(()->BufferChecks.checkBuffer(BufferUtils.createIntBuffer(4),16));
  reject(()->BufferChecks.checkBuffer(BufferUtils.createDoubleBuffer(4),16));
  reject(()->BufferChecks.checkBuffer(BufferUtils.createByteBuffer(4),16));
  FloatBuffer f=BufferUtils.createFloatBuffer(20);f.position(5);reject(()->BufferChecks.checkBuffer(f,16));f.position(4);BufferChecks.checkBuffer(f,16);ok(f.position()==4);
  IntBuffer i=BufferUtils.createIntBuffer(20);i.limit(15);reject(()->BufferChecks.checkBuffer(i,16));i.clear();BufferChecks.checkBuffer(i,16);ok(i.remaining()==20);
  reject(()->BufferChecks.checkBuffer(FloatBuffer.allocate(16),16));
  System.out.println("LWJGL2_QUERY_BUFFER_CONTRACT_PASS: "+count);
 }
}

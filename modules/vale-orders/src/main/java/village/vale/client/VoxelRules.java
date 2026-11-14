package village.vale.client;
import java.util.*;
/** Pure bounds shared by the actual scoped renderer and native-tick particle engine. */
public final class VoxelRules {
 private VoxelRules(){}
 public static final class Frames {
  private static final class F{boolean voxel;int budget;F(int n){budget=n;}}
  private final Deque<F> stack=new ArrayDeque<>();
  private int globalLimit=-1,globalUsed,globalSkipped;
  public void frame(int limit){globalLimit=Math.max(0,Math.min(9000,limit));globalUsed=globalSkipped=0;}
  public boolean globalCube(){if(globalLimit<0)return true;if(globalUsed>=globalLimit){if(globalSkipped<Integer.MAX_VALUE)globalSkipped++;return false;}globalUsed++;return true;}
  public int globalUsed(){return globalUsed;}public int globalSkipped(){return globalSkipped;}
  public void push(int budget){stack.push(new F(Math.max(0,Math.min(2048,budget))));}
  public void pop(){if(!stack.isEmpty())stack.pop();}
  public void voxel(boolean value){if(!stack.isEmpty())stack.peek().voxel=value;}
  public boolean voxel(){return !stack.isEmpty()&&stack.peek().voxel;}
  public int remaining(){return stack.isEmpty()?0:stack.peek().budget;}
  public boolean use(int count){if(stack.isEmpty()||count<0||count>stack.peek().budget)return false;stack.peek().budget-=count;return true;}
 }
 public static final class Epoch {
  private Object world,body,connection;private int tick=Integer.MIN_VALUE,delta,armor,weapon;private long version;
  public boolean context(Object w,Object b,Object c){return w!=null&&b!=null&&c!=null&&world==w&&body==b&&connection==c;}
  public boolean prepare(Object w,Object b,Object c,int now){
   if(w==null||b==null||c==null||now<0){if(world!=null||body!=null||connection!=null){world=body=connection=null;tick=Integer.MIN_VALUE;version++;}armor=weapon=delta=0;return false;}
   if(!context(w,b,c)||now<tick){world=w;body=b;connection=c;tick=Integer.MIN_VALUE;version++;}
   if(now!=tick){delta=tick==Integer.MIN_VALUE?1:(int)Math.min(4L,(long)now-tick);tick=now;armor=weapon=0;}return true;
  }
  public boolean admit(boolean armorLane){if(world==null)return false;if(armorLane){if(armor>=16)return false;armor++;}else{if(weapon>=64)return false;weapon++;}return true;}
  public int tick(){return tick;}public int delta(){return delta;}public long version(){return version;}
 }
 public static final class Receipts<K> {
  private final Set<K> keys=Collections.newSetFromMap(new IdentityHashMap<K,Boolean>());
  public boolean take(K key){return key!=null&&keys.size()<512&&keys.add(key);}
  public boolean contains(K key){return keys.contains(key);}
  public void retain(java.util.function.Predicate<K> valid){keys.removeIf(k->!valid.test(k));}
  public void clear(){keys.clear();}public int size(){return keys.size();}
 }
}

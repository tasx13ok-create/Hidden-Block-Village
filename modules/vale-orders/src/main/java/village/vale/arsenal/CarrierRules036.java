package village.vale.arsenal;

/** Original independent carrier rules, with no engine or reference-mod dependency. */
public final class CarrierRules036 {
 private CarrierRules036(){}
 public enum Failure { NONE, UNISSUED, NOT_HELD, OWNER, PROFILE, UNLEARNED, LEVEL, MASTERY, COOLDOWN, RESOURCE, COMPLETE }
 public static Failure gate(boolean issued,boolean held,boolean owner,boolean profile,boolean learned,int level,int minimum,int mastery,int required,long now,long next,int resolve,int cost){
  if(!issued)return Failure.UNISSUED;if(!held)return Failure.NOT_HELD;if(!owner)return Failure.OWNER;if(!profile)return Failure.PROFILE;if(!learned)return Failure.UNLEARNED;if(level<minimum)return Failure.LEVEL;if(mastery<required)return Failure.MASTERY;if(now<next)return Failure.COOLDOWN;if(cost<1||cost>100||resolve<cost)return Failure.RESOURCE;return Failure.NONE;
 }
 public static int required(int base,boolean affinity){if(base<1||base>60)throw new IllegalArgumentException("Invalid base mastery");return affinity?base:(base*5+1)/2;}
 public static boolean canTrain(int mastery,int required){return mastery<required&&required>=1&&required<=150;}
 public static int advance(int value,int amount){if(amount<0||amount>150)throw new IllegalArgumentException("Invalid mastery amount");return(int)Math.min(150,Math.max(0L,(long)value)+amount);}
 public static int selection(int index,int size){if(size<1||size>49)throw new IllegalArgumentException("Invalid carrier size");return Math.floorMod(index,size);}
 public static int nextKnown(int start,boolean[] known){if(known==null||known.length<1||known.length>49)throw new IllegalArgumentException("Invalid choices");for(int i=1;i<=known.length;i++){int n=selection(start+i,known.length);if(known[n])return n;}return -1;}
 public static long next(long now,int delay){if(now<0||delay<1||delay>2400||now>Long.MAX_VALUE-delay)throw new IllegalArgumentException("Invalid carrier cooldown");return now+delay;}
 public static boolean provenance(String expectedOwner,String owner,String expectedProfile,String profile,String expectedSerial,String serial){return expectedOwner!=null&&expectedOwner.equals(owner)&&expectedProfile!=null&&expectedProfile.equals(profile)&&expectedSerial!=null&&!expectedSerial.isEmpty()&&expectedSerial.equals(serial);}
}

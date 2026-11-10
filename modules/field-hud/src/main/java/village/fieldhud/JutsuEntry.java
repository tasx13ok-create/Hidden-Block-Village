package village.fieldhud;

/** Immutable presentation of one server-owned available attack. */
public final class JutsuEntry {
    public final String id,name,iconItemId,kind,school,resource,reason;
    public final int cost,remaining,total,globalRemaining,iconMeta,requiredLevel,itemCount;
    public final boolean enabled,canCast,ratioOnly;
    public final double cooldownFraction;
    public JutsuEntry(String id,String name,String iconItemId,String kind,String resource,int cost,int remaining,int total,int globalRemaining,boolean canCast,String reason) {
        this(id,name,iconItemId,0,kind,"",0,resource,cost,0,remaining,total,globalRemaining,total>0?remaining/(double)total:0,canCast,reason);
    }
    public JutsuEntry(String id,String name,String iconItemId,int iconMeta,String kind,String school,int requiredLevel,String resource,int cost,int itemCount,int remaining,int total,int globalRemaining,double fraction,boolean enabled,String reason) {
        if(id==null||!id.matches("[a-z0-9_:./-]{1,96}"))throw new IllegalArgumentException("Invalid ability ID");
        this.id=id;this.name=text(name,96);this.iconItemId=text(iconItemId,128);this.kind=text(kind,24);
        this.school=text(school,32);this.resource=text(resource,32);this.reason=text(reason,256);
        ratioOnly=remaining==-1&&total==-1;
        if(cost<0||cost>10000||(!ratioOnly&&(remaining<0||total<0||remaining>total||total>72000))||globalRemaining<0||globalRemaining>1200
            ||iconMeta<0||iconMeta>32767||requiredLevel<0||requiredLevel>1000||itemCount<0||itemCount>1000000||!Double.isFinite(fraction)||fraction<0||fraction>1)throw new IllegalArgumentException("Invalid ability bounds");
        this.cost=cost;this.remaining=remaining;this.total=total;this.globalRemaining=globalRemaining;this.enabled=enabled;
        this.cooldownFraction=fraction;this.iconMeta=iconMeta;this.requiredLevel=requiredLevel;this.itemCount=itemCount;
        this.canCast=enabled&&(ratioOnly||remaining==0)&&fraction==0&&globalRemaining==0;
    }
    private static String text(String s,int max) {
        if(s==null||s.length()>max)throw new IllegalArgumentException("Invalid ability text");
        StringBuilder plain=new StringBuilder(s.length());
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);if(c=='\u00a7'){if(i+1<s.length())i++;continue;}
            plain.append(Character.isISOControl(c)?' ':c);
        }
        return plain.toString();
    }
    /** Interpolation is display-only and never announces ready ahead of receipt. */
    public int displayRemaining(double elapsedTicks) {
        if(ratioOnly)return -1;
        return remaining==0?0:Math.max(1,remaining-(int)Math.max(0,Math.min(remaining,elapsedTicks)));
    }
    public String seconds(double elapsedTicks) {
        int ticks=displayRemaining(elapsedTicks);
        if(ticks<0)return "";
        if(ticks==0)return "";
        if(ticks<20)return "<1s";
        return ((ticks+19)/20)+"s";
    }
    public double fraction(double elapsedTicks) {
        if(ratioOnly||total<=0||cooldownFraction>remaining/(double)total+.00001)return cooldownFraction;
        return Math.min(1,displayRemaining(elapsedTicks)/(double)total);
    }
    public String blockReason(){return !enabled?(reason.isEmpty()?"Unavailable":reason):canCast?"":"Cooling down";}
}

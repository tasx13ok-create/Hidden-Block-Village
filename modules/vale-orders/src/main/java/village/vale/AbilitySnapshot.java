package village.vale;

import java.util.*;

/** Immutable, bounded owner-only action catalog. An entry is never an authority token. */
public final class AbilitySnapshot {
    public static final int SCHEMA=1,MAX_ENTRIES=64;
    public final int schemaVersion=SCHEMA,dimension;
    public final UUID playerId;
    public final long serverTick;
    public final String selectedEntryId;
    public final List<Entry> entries;

    public AbilitySnapshot(UUID playerId,int dimension,long serverTick,String selectedEntryId,List<Entry> entries){
        this.playerId=Objects.requireNonNull(playerId,"playerId");
        if(serverTick<0||entries.size()>MAX_ENTRIES)throw new IllegalArgumentException("Ability bounds");
        this.dimension=dimension;this.serverTick=serverTick;this.selectedEntryId=text(selectedEntryId,96);
        Set<String> ids=new HashSet<>();List<Entry> copy=new ArrayList<>();
        for(Entry entry:entries){Objects.requireNonNull(entry,"entry");if(entry.kind.equals("weapon"))throw new IllegalArgumentException("Weapon shortcut needs schema2");if(!ids.add(entry.id))throw new IllegalArgumentException("Duplicate ability");copy.add(entry);}
        if(!selectedEntryId.isEmpty()&&!ids.contains(selectedEntryId))throw new IllegalArgumentException("Unknown selection");
        this.entries=Collections.unmodifiableList(copy);
    }
    public Entry find(String id){for(Entry entry:entries)if(entry.id.equals(id))return entry;return null;}
    public static boolean validId(String id){return id!=null&&id.length()>0&&id.length()<=96&&id.matches("(?:art|order|technique|item|weapon)/[a-z0-9_:.\\-/]+");}
    static String text(String value,int max){Objects.requireNonNull(value,"text");if(value.length()>max)throw new IllegalArgumentException("Ability text too long");return value;}

    public static final class Entry {
        public final String id,title,kind,schoolKey,iconItemId,reason;
        public final int iconMeta,requiredLevel,resolveCost,itemCount,cooldownRemainingTicks,cooldownTotalTicks;
        public final boolean enabled;
        public final float cooldownFraction;
        public Entry(String id,String title,String kind,String schoolKey,String iconItemId,int iconMeta,int requiredLevel,int resolveCost,int itemCount,boolean enabled,String reason,int remaining,int total,float fraction){
            if(!validId(id)||!Arrays.asList("art","order","technique","projectile","form","summon","weapon").contains(kind)||!schoolKey.matches("[a-z_]{1,24}")||!iconItemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Ability identity");
            String prefix=Arrays.asList("projectile","form","summon").contains(kind)?"item":kind;
            if(!id.startsWith(prefix+"/"))throw new IllegalArgumentException("Ability kind and ID differ");
            if(kind.equals("weapon")&&!id.matches("weapon/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid weapon instance ID");
            if(iconMeta<0||iconMeta>32767||requiredLevel<1||requiredLevel>20||resolveCost<0||resolveCost>100||itemCount<0||itemCount>2304||!Float.isFinite(fraction)||fraction<0||fraction>1)throw new IllegalArgumentException("Ability value bounds");
            if(!((remaining==-1&&total==-1)||(remaining>=0&&remaining<=total&&total<=24000)))throw new IllegalArgumentException("Ability timer bounds");
            if(enabled&&!reason.isEmpty())throw new IllegalArgumentException("Enabled entry has a disabled reason");
            this.id=id;this.title=text(title,96);this.kind=kind;this.schoolKey=schoolKey;this.iconItemId=iconItemId;
            this.iconMeta=iconMeta;this.requiredLevel=requiredLevel;this.resolveCost=resolveCost;this.itemCount=itemCount;this.enabled=enabled;this.reason=text(reason,160);
            cooldownRemainingTicks=remaining;cooldownTotalTicks=total;cooldownFraction=fraction;
        }
    }
}

package village.vale;

import java.util.*;

/** Additive schema2 catalog, including exact issued weapon instances. Legacy schema1 stays separate. */
public final class AbilityLoadoutSnapshot {
    public static final int SCHEMA=2,MAX_ENTRIES=128;
    public final int schemaVersion=SCHEMA,dimension;
    public final UUID playerId;
    public final long serverTick;
    public final String selectedEntryId;
    public final List<AbilitySnapshot.Entry> entries;
    public AbilityLoadoutSnapshot(UUID owner,int dimension,long tick,String selected,List<AbilitySnapshot.Entry> entries){
        playerId=Objects.requireNonNull(owner,"owner");if(tick<0||entries.size()>MAX_ENTRIES)throw new IllegalArgumentException("Loadout bounds");
        this.dimension=dimension;serverTick=tick;selectedEntryId=AbilitySnapshot.text(selected,96);Set<String> ids=new HashSet<>();List<AbilitySnapshot.Entry> copy=new ArrayList<>();
        for(AbilitySnapshot.Entry entry:entries){Objects.requireNonNull(entry,"entry");if(!ids.add(entry.id))throw new IllegalArgumentException("Duplicate loadout ID");copy.add(entry);}
        if(!selected.isEmpty()&&!ids.contains(selected))throw new IllegalArgumentException("Unknown loadout selection");this.entries=Collections.unmodifiableList(copy);
    }
    public AbilitySnapshot.Entry find(String id){for(AbilitySnapshot.Entry entry:entries)if(entry.id.equals(id))return entry;return null;}
}

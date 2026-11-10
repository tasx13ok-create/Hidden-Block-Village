package village.vale.armor;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A live server-ledger proof is required for every piece. This boundary never creates grants. */
public final class EquipmentGate {
    private EquipmentGate(){}
    public interface LedgerAuthority {
        boolean authorizes(UUID wearer,String registeredItem,String serial,ArmorMesh.Slot slot);
    }
    public static final class Piece {
        public final ArmorProfile profile;
        public final ArmorMesh.Slot slot;
        public final String registeredItem,serial;
        public Piece(ArmorProfile profile,ArmorMesh.Slot slot,String registeredItem,String serial){
            this.profile=profile;this.slot=slot;this.registeredItem=registeredItem;this.serial=serial;
        }
    }
    public static ArmorProfile resolve(UUID wearer,Piece[] equipment,LedgerAuthority ledger){
        if(wearer==null||equipment==null||equipment.length!=4||ledger==null)return null;
        ArmorProfile found=null;
        Set<String> serials=new HashSet<String>();
        EnumSet<ArmorMesh.Slot> slots=EnumSet.noneOf(ArmorMesh.Slot.class);
        for(Piece piece:equipment){
            if(piece==null||piece.profile==null||piece.slot==null||blank(piece.serial)||
               !piece.profile.item(piece.slot).equals(piece.registeredItem)||!slots.add(piece.slot)||!serials.add(piece.serial))return null;
            if(found==null)found=piece.profile;
            if(found!=piece.profile)return null;
            // Receipts belong to Core's issuance/outbox transaction, not to client-writable item evidence.
            try {if(!ledger.authorizes(wearer,piece.registeredItem,piece.serial,piece.slot))return null;}
            catch(RuntimeException failure){return null;}
        }
        return slots.size()==4?found:null;
    }
    private static boolean blank(String value){return value==null||value.trim().isEmpty();}
}

package village.vale.client;

import java.util.*;
import village.vale.*;

/** Actual client adapter's catalog choice; native cache/screen/transport tests remain separate. */
public final class AbilityRequestPolicyTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static AbilitySnapshot.Entry row(String id,String kind,boolean enabled){return new AbilitySnapshot.Entry(id,id,kind,"flint",kind.equals("weapon")?"valeorders:flint_saber":"valeorders:cairnroot_chestplate",0,1,25,0,enabled,enabled?"":"Requires the matching set.",0,0,0);}
    public static void main(String[] args){
        UUID owner=UUID.randomUUID();String armor="order/original_armor_cairnroot_ward",flight="order/original_armor_auric_skimmer_flight",weapon="weapon/12345678-1234-4567-89ab-123456789abc";
        AbilitySnapshot legacy=new AbilitySnapshot(owner,0,1,"",Arrays.asList(row("art/ward_circle","art",true),row("order/flint","order",true)));
        AbilityLoadoutSnapshot modern=new AbilityLoadoutSnapshot(owner,0,1,"",Arrays.asList(row(armor,"order",true),row(flight,"order",true),row(weapon,"weapon",true),row("art/ward_circle","art",true),row("order/original_armor_glasschime_ward","order",false)));
        for(boolean activate:new boolean[]{false,true}){
            check(AbilityControls.available(modern,legacy,armor,activate)!=null,"Schema2-only armor rejected");check(AbilityControls.available(modern,legacy,flight,activate)!=null,"Schema2-only flight rejected");
            check(AbilityControls.available(modern,legacy,"art/ward_circle",activate)!=null,"Existing art rejected in modern catalog");check(AbilityControls.available(null,legacy,"art/ward_circle",activate)!=null,"Fresh legacy server fallback lost");
            check(AbilityControls.available(modern,legacy,"order/flint",activate)==null,"Fresh modern miss fell back to a stale legacy entry");check(AbilityControls.available(modern,legacy,weapon,activate)==null,"Select/activate silently became equip");
            check(AbilityControls.available(null,legacy,armor,activate)==null&&AbilityControls.available(null,null,armor,activate)==null,"Absent/stale modern authority guessed");
            for(String invalid:new String[]{null,"","/op attacker","order/missing"})check(AbilityControls.available(modern,legacy,invalid,activate)==null,"Invalid/missing action queued");
        }
        String blocked="order/original_armor_glasschime_ward";check(AbilityControls.available(modern,legacy,blocked,false)!=null,"Cannot focus an unavailable card");check(AbilityControls.available(modern,legacy,blocked,true)==null,"Disabled cast queued");
        System.out.println("PASS: "+checks+" actual client catalog routing, schema2 armor/flight, legacy fallback and explicit-equip separation checks.");
    }
}

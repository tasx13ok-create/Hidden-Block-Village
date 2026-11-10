package village.vale.armor.api;

import java.util.*;
import java.lang.reflect.*;

public final class ApiContractChecks {
    static int checks;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        List<ArmorSnapshot.Piece> pieces=new ArrayList<>();pieces.add(new ArmorSnapshot.Piece("chest","valeorders:cairnroot_chestplate","s",true));
        List<ArmorSnapshot.Action> actions=new ArrayList<>();actions.add(action("cairnroot",true));
        ArmorSnapshot s=new ArmorSnapshot(UUID.randomUUID(),0,20,20,"cairnroot","flint","anchor",777,0,false,false,10,60,0,0,0,true,pieces,actions);
        pieces.clear();actions.clear();ok(s.pieces.size()==1&&s.actions.size()==1,"Snapshot defends both mutable inputs");
        boolean blocked=false;try{s.actions.clear();}catch(UnsupportedOperationException expected){blocked=true;}ok(blocked,"Action list is immutable");
        blocked=false;try{s.pieces.clear();}catch(UnsupportedOperationException expected){blocked=true;}ok(blocked,"Piece list is immutable");
        ok(s.staminaUnits==777&&s.staminaMaximumUnits==2000&&s.staminaUnitsPerPoint==20&&s.flightRestartUnits==400,"Stamina scale is explicit");
        ok(s.find("order/original_armor_cairnroot_ward")!=null&&s.find("order/flint")==null,"Provider action IDs cannot accidentally intercept core IDs");
        for(Class<?> type:new Class<?>[]{ArmorSnapshot.class,ArmorSnapshot.Piece.class,ArmorSnapshot.Action.class,ArmorActionResult.class})for(Field f:type.getFields()){
            ok(Modifier.isFinal(f.getModifiers()),"Every public DTO field is final");ok(!f.getType().getName().contains("minecraft"),"No live game objects escape in snapshots");
        }
        List<ArmorSnapshot.Action> tooMany=new ArrayList<>();for(int i=0;i<11;i++)tooMany.add(action("ward_"+(char)('a'+i),false));
        blocked=false;try{new ArmorSnapshot(UUID.randomUUID(),0,0,0,"","","",0,0,false,false,0,0,0,0,0,false,Collections.<ArmorSnapshot.Piece>emptyList(),tooMany);}catch(IllegalArgumentException expected){blocked=true;}ok(blocked,"Catalog count is bounded");
        List<ArmorSnapshot.Action> duplicates=Arrays.asList(action("same",false),action("same",false));blocked=false;
        try{new ArmorSnapshot(UUID.randomUUID(),0,0,0,"","","",0,0,false,false,0,0,0,0,0,false,Collections.<ArmorSnapshot.Piece>emptyList(),duplicates);}catch(IllegalArgumentException expected){blocked=true;}ok(blocked,"Duplicate IDs are rejected");
        ArmorActionResult repeated=ArmorActionResult.of(ArmorActionResult.Code.ALREADY_RECORDED,"Replay");ok(repeated.successful&&!repeated.accepted,"Reward replay is successful without being a fresh grant");
        System.out.println("ORIGINAL_ARMOR_API_CONTRACT_PASS checks="+checks);
    }
    static ArmorSnapshot.Action action(String profile,boolean enabled){return new ArmorSnapshot.Action("order/original_armor_"+profile+"_ward","order/flint","Ward skill","flint",profile,"valeorders:cairnroot_chestplate",25,enabled,enabled?"":"Not equipped",0,240,true);}
}

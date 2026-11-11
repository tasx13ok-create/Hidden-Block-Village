package village.vale.arsenal;

/** Pure cost/eligibility boundaries. Native callback acceptance is a separate fixture. */
public final class ScrollCastChecks039 {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static ScrollRules.Failure cast(int resolve,int cost){return ScrollRules.cast(true,true,8,4,8,8,100,100,resolve,cost);}
    private static void invalid(Runnable call,String why){boolean rejected=false;try{call.run();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,why);}
    public static void main(String[] args){
        int wet=ScrollRules.cost(16,false,false),dry=ScrollRules.cost(16,false,true);
        check(wet==16,"River Serpent's normal cost changed");
        check(dry==48,"The dry-land surcharge is not included in the reservation");
        check(cast(16,wet)==ScrollRules.Failure.NONE,"Exact normal cost was rejected");
        check(cast(15,wet)==ScrollRules.Failure.RESOURCE,"Normal cast overspent its budget");
        check(cast(48,dry)==ScrollRules.Failure.NONE,"Exact full dry-land cost was rejected");
        check(cast(47,dry)==ScrollRules.Failure.RESOURCE,"A dry cast passed with only part of its total cost");
        check(cast(16,dry)==ScrollRules.Failure.RESOURCE,"The dry surcharge can be deferred until after the effect");
        check(ScrollRules.cost(16,true,true)==2&&ScrollRules.cost(16,true,false)==2,"Practice inherited the dry-land surcharge");
        check(cast(2,ScrollRules.cost(16,true,true))==ScrollRules.Failure.NONE,"Exact practice cost was rejected");
        check(cast(1,ScrollRules.cost(16,true,true))==ScrollRules.Failure.RESOURCE,"Practice can run without its full cost");
        check(cast(100,ScrollRules.cost(100,false,true))==ScrollRules.Failure.RESOURCE,"An unaffordable catalog cost wrapped into a payable amount");
        invalid(()->ScrollRules.cost(0,false,false),"A free catalog cost was accepted");
        invalid(()->ScrollRules.cost(-1,true,true),"Practice hid an invalid catalog cost");
        invalid(()->ScrollRules.cost(Integer.MAX_VALUE,false,true),"An overflowing catalog cost was accepted");
        check(ScrollRules.cast(false,true,8,4,8,8,100,100,100,16)==ScrollRules.Failure.UNISSUED,"Unissued proof was accepted");
        check(ScrollRules.cast(true,false,8,4,8,8,100,100,100,16)==ScrollRules.Failure.UNLEARNED,"A replaced/unlearned lesson was accepted");
        check(ScrollRules.cast(true,true,3,4,8,8,100,100,100,16)==ScrollRules.Failure.LEVEL,"A level lock was skipped");
        check(ScrollRules.cast(true,true,8,4,7,8,100,100,100,16)==ScrollRules.Failure.PRACTICE,"Insufficient practice was accepted");
        check(ScrollRules.cast(true,true,8,4,8,8,100,101,100,16)==ScrollRules.Failure.COOLDOWN,"The saved deadline expired early");
        check(ScrollRules.cast(true,true,8,4,8,8,101,101,100,16)==ScrollRules.Failure.NONE,"The exact saved deadline remained locked");
        check(ScrollRules.advance(59,1,60)==60&&ScrollRules.advance(60,1,60)==60,"A successful cast escaped the mastery cap");
        check(ScrollRules.advance(41,1,60)==42,"Merging cast progress discarded an independent practice update");
        check(ScrollRules.next(100,30)==130&&ScrollRules.next(100,320)==420,"Practice or Waymark cooldown changed");
        invalid(()->ScrollRules.next(Long.MAX_VALUE-10,30),"Deadline overflow could apply an effect before failing");
        invalid(()->ScrollRules.next(-1,30),"A negative world clock was accepted");
        System.out.println("PASS: "+checks+" scroll cast cost/eligibility boundaries. Native effects, event cancellation and callback isolation are separate.");
    }
}

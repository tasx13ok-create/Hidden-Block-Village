package village.bijuu;

public final class SprintChecks {
    private static void check(boolean result,String message) {
        if(!result)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for(int tails=1;tails<=9;tails++){
            SprintCurve curve=new SprintCurve();
            double previous=0;
            int duration=SprintCurve.duration(tails);
            for(int tick=1;tick<=duration;tick++){
                double value=curve.advance(tails,true,true,true,1);
                check(value>previous && value<=SprintCurve.maximum(tails),"monotonic cap");
                previous=value;
            }
            check(curve.advance(tails,true,true,true,1)==previous,"held cap");
            check(curve.advance(tails,true,true,false,1)==previous,"jump carries");
            check(curve.advance(tails,true,true,true,-1)<previous,"reversal brakes");
            int before=curve.charge;
            check(curve.advance(tails,true,false,true,1)==0 && curve.charge<before,"standing");
            for(int tick=0;tick<duration;tick++)curve.advance(tails,false,true,true,1);
            check(curve.charge==0,"release drains");
            check(curve.advance(tails,true,true,false,1)==0,"air cannot build charge");
        }
        System.out.println("All nine sprint curves passed");
    }
}

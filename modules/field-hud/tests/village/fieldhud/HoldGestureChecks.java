package village.fieldhud;
public final class HoldGestureChecks {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        HoldGesture h=new HoldGesture();check(!h.update(true,true,1000),"press fired");
        check(!h.update(true,true,3999),"hold fired before three seconds");check(h.update(true,true,4000),"three-second hold did not fire");
        check(!h.update(true,true,10000),"long hold repeated");check(!h.update(false,true,10001),"release fired");
        check(!h.update(true,true,11000)&&h.update(true,true,14000),"second hold failed");
        h.reset();h.update(true,true,0);check(!h.update(false,true,2999)&&h.progress(3000)==0,"short tap persisted");
        h.update(true,true,5000);check(!h.update(true,false,7000),"menu fired");check(!h.update(true,true,10000),"held key carried across menu");
        h.update(false,true,10001);h.update(true,true,11000);check(h.update(true,true,14000),"release did not rearm after menu");
        h.reset();check(!h.update(true,false,0)&&!h.update(true,true,5000),"focus-loss held key fired on return");
        h.reset();h.update(true,true,4000);check(!h.update(true,true,1)&&!h.update(true,true,3000)&&h.update(true,true,3001),"clock rollback bypassed duration");
        h.reset();h.update(true,true,100);check(h.progress(1600)==.5&&h.progress(0)==0,"progress bounds");
        System.out.println("COMBAT_HOLD_PASS: "+checks);
    }
}

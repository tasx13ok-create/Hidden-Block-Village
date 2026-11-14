package village.vale.bridge;

import java.util.function.BooleanSupplier;

/** Original synchronous resource settlement; no optional-mod implementation is copied. */
final class ResourceEscrow {
    interface Account {
        Object identity();
        double amount();
        double maximum();
        boolean consume(double absolute, boolean allowOvercap);
        void clear();
    }
    /** Direct original NBT ownership only; optional/native APIs must use Account. */
    interface CapturedLegacyAccount extends Account {}
    static final class Result {
        final Debit debit; final String error;
        Result(Debit value,String why){debit=value;error=why;}
    }
    private ResourceEscrow(){}
    static boolean finiteBalance(double amount,double maximum){return Double.isFinite(amount)&&amount>=0&&Double.isFinite(maximum)&&maximum>0;}
    static boolean valid(BooleanSupplier context){try{return context.getAsBoolean();}catch(RuntimeException|LinkageError error){return false;}}
    static Result reserve(Account account,double cost,BooleanSupplier context){return reserve(account,cost,context,context);}
    static Result reserveCapturedLegacy(CapturedLegacyAccount account,double cost,BooleanSupplier context,BooleanSupplier refundContext){return reserve(account,cost,context,refundContext);}
    private static Result reserve(Account account,double cost,BooleanSupplier context,BooleanSupplier refundContext){
        if(account==null||!Double.isFinite(cost)||cost<0||!valid(context))return new Result(null,"Invalid reservation context or cost");
        double before;
        try{before=account.amount();if(!finiteBalance(before,account.maximum())||!valid(context))return new Result(null,"Unavailable resource balance");}
        catch(RuntimeException|LinkageError error){return new Result(null,"Resource read failed");}
        if(cost==0)return new Result(new Debit(account,context,refundContext,before,0),null);
        if(before<cost)return new Result(null,"Insufficient resource");
        double expected=before-cost;
        if(expected==before)return new Result(null,"Cost is below provider numeric precision");
        boolean invoked=false,reported=false;String error=null;
        try{
            invoked=true;
            if(expected==0){account.clear();reported=true;}else reported=account.consume(cost,false);
        }catch(RuntimeException|LinkageError failure){error="Resource debit failed";}
        double after;
        try{after=account.amount();}catch(RuntimeException|LinkageError failure){return new Result(null,"Resource debit outcome is unknown; no action granted");}
        if(error==null&&reported&&after==expected&&valid(context))return new Result(new Debit(account,context,refundContext,before,before-after),null);
        // A failing optional API can throw after changing its pool. Undo only the
        // bounded observed debit on this same still-valid account, never a snapshot.
        if(invoked&&Double.isFinite(after)&&after>=0&&after<before&&valid(refundContext)){
            double loss=Math.min(cost,before-after);
            Debit rollback=new Debit(account,context,refundContext,before,loss);rollback.refund();
        }
        return new Result(null,error==null?"Resource debit was refused or changed context":error);
    }
    static double gain(Account account,double raw,BooleanSupplier context){
        if(account==null||!Double.isFinite(raw)||raw<=0||!valid(context))return 0;
        double before,max,credit;
        try{
            before=account.amount();max=account.maximum();
            if(!finiteBalance(before,max)||before>=max||!valid(context))return 0;
            credit=Math.min(raw,max-before);if(!(credit>0)||before+credit==before)return 0;
        }catch(RuntimeException|LinkageError error){return 0;}
        // An API can throw after its persisted mutation/sync. Observed bounded
        // credit still consumes the trusted gain once; returning0 would mint
        // repeatable free tonics despite an actual resource increase.
        try{account.consume(-credit,false);}catch(RuntimeException|LinkageError error){}
        try{double after=account.amount();return valid(context)&&Double.isFinite(after)&&after>=before&&after<=max&&after-before<=credit+Math.ulp(after)*2?after-before:0;}
        catch(RuntimeException|LinkageError error){return 0;}
    }
    static final class Debit {
        final Account account;final BooleanSupplier context,refundContext;final Object identity;final double before,amount;
        private boolean done;private double credited;
        Debit(Account account,BooleanSupplier context,BooleanSupplier refundContext,double before,double amount){this.account=account;this.context=context;this.refundContext=refundContext;this.identity=account.identity();this.before=before;this.amount=amount;}
        boolean pending(){return !done;}
        double refunded(){return credited;}
        boolean commit(){if(done)return false;done=true;return valid(context);}
        boolean refund(){
            if(done)return false;done=true; // close BEFORE callbacks; refund can never repeat
            if(!valid(refundContext)||account.identity()!=identity)return false;
            if(amount==0)return true;
            try{
                double now=account.amount(),max=account.maximum();
                if(!finiteBalance(now,max)||!Double.isFinite(now+amount)||!valid(refundContext))return false;
                // True is deliberate: restore a legitimate over-cap debit without
                // overwriting unrelated native spending/gains since reservation.
                try{account.consume(-amount,true);}catch(RuntimeException|LinkageError error){}
                double after=account.amount();
                if(!valid(refundContext)||!Double.isFinite(after)||after<now||after-now>amount+Math.ulp(after)*2)return false;
                credited=after-now;return true;
            }catch(RuntimeException|LinkageError error){return false;}
        }
    }
}

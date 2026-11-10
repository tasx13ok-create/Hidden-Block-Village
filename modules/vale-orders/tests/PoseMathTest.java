package village.vale;

public final class PoseMathTest {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("run spring check "+checks);}
    public static void main(String[] args){
        PoseMath.Spring slow=new PoseMath.Spring(),fast=new PoseMath.Spring();float previous=0;
        for(int i=0;i<30;i++){float value=slow.step(1,2F/3,4.4F);check(value>=previous&&value<=1);previous=value;}
        for(int i=0;i<120;i++)fast.step(1,1F/6,4.4F);
        check(Math.abs(slow.value-fast.value)<.015F&&slow.value>.98F);
        float once=fast.value;fast.step(0,0,3.2F);check(fast.value==once);
        for(int i=0;i<60;i++){float value=fast.step(0,1F/3,3.2F);check(value<=once&&value>=0);once=value;}
        check(fast.value<.001F);
        for(int i=0;i<1000;i++){fast.step((i/7)%2,1F/3,3.2F);check(fast.value>=0&&fast.value<=1&&!Float.isNaN(fast.value));}
        System.out.println("PASS: "+checks+" run-pose spring transition, frame-rate, repeated-pass and bounded-response checks.");
    }
}

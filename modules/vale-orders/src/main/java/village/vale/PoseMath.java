package village.vale;

/** Frame-rate-independent critically damped spring, in game ticks. No Minecraft dependencies. */
public final class PoseMath {
    private PoseMath(){}
    public static final class Spring {
        public float value,velocity;
        public float step(float target,float ticks,float smoothTicks){
            target=Math.max(0,Math.min(1,target));if(ticks<=0)return value;
            ticks=Math.min(ticks,5);float omega=2/Math.max(.5F,smoothTicks),x=omega*ticks;
            float decay=1/(1+x+.48F*x*x+.235F*x*x*x),change=value-target,temp=(velocity+omega*change)*ticks;
            velocity=(velocity-omega*temp)*decay;float next=target+(change+temp)*decay;
            if((target-value>0)==(next>target)){next=target;velocity=0;}
            value=Math.max(0,Math.min(1,next));return value;
        }
    }
}

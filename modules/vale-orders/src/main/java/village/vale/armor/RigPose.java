package village.vale.armor;

/** Bounded cosmetic pose. Gameplay authorization and hit decisions stay on the server. */
public final class RigPose {
    private RigPose() {}
    public static final class Rotation {
        public final float x,y,z;
        Rotation(float x,float y,float z){this.x=x;this.y=y;this.z=z;}
    }
    public static Rotation at(String motion,double seconds,float movement,float flight,float strike) {
        // Clamp untrusted/render timing inputs before evaluating periodic functions.
        if(!Double.isFinite(seconds))seconds=0;
        // Both oscillators complete whole cycles over 20*pi seconds, so wrapping has no pose jump.
        seconds=Math.IEEEremainder(seconds,Math.PI*20.0);
        movement=unit(movement);flight=unit(flight);strike=unit(strike);
        float slow=(float)Math.sin(seconds*2.4),fast=(float)Math.sin(seconds*4.1);
        if("wing_left".equals(motion)||"wing_right".equals(motion)){
            float side="wing_left".equals(motion)?1:-1;
            // The mirrored hinge must fold toward positive local Z (behind the body).
            // Deploy before applying the full flight stroke, rather than flapping folded panels.
            float open=flight*flight*(3-2*flight);
            float stroke=unit((open-.45f)/.55f);stroke=stroke*stroke*(3-2*stroke);
            return new Rotation(.025f*slow-.04f*movement*open,
                -side*(1.03f-.91f*open),
                side*(.02f*slow*(1-open)+(.10f+.06f*movement)*stroke*fast));
        }
        if("contact_left".equals(motion)||"contact_right".equals(motion)) {
            float side="contact_left".equals(motion)?1:-1;
            return new Rotation(-.48f*strike,side*.14f*strike,.02f*slow);
        }
        if("chime".equals(motion))return new Rotation(.025f*slow,.015f*fast,.04f*movement*slow);
        if("sail".equals(motion))return new Rotation(.025f*movement*slow,.025f*movement*fast,0);
        return new Rotation(0,0,0);
    }
    public static float unit(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    /** Frame-rate-independent, slower landing fold; repeated render passes at dt0 do not advance. */
    public static float approachWing(float previous,float target,double elapsedTicks){
        previous=unit(previous);target=unit(target);
        if(!Double.isFinite(elapsedTicks)||elapsedTicks<=0)return previous;
        double rate=target>previous?.22:.17;
        return unit((float)(previous+(target-previous)*-Math.expm1(-Math.min(5,elapsedTicks)*rate)));
    }
    public static float approach(float previous,float target,double elapsedTicks){
        previous=unit(previous);target=unit(target);if(!Double.isFinite(elapsedTicks)||elapsedTicks<=0)return previous;
        return unit((float)(previous+(target-previous)*(1-Math.exp(-Math.min(5,elapsedTicks)*.6))));
    }
}

package village.bijuu;

/** Server-side wind-up. Charge is measured in moving ticks and never saved to the player. */
final class SprintCurve {
    int charge;
    static int duration(int tails) { return 24+12*tails; }
    static double maximum(int tails) { return .18*tails; }

    double advance(int tails,boolean sprint,boolean forward,boolean ground,double directionDot) {
        int duration=duration(tails);
        if(!sprint || !forward) {
            charge=Math.max(0,charge-5);
            return 0;
        }
        if(directionDot<.35)charge=Math.max(0,charge-8);
        else if(ground)charge=Math.min(duration,charge+1);
        double t=charge/(double)duration;
        return maximum(tails)*t*t*(3-2*t);
    }
}

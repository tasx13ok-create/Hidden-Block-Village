package village.fieldhud;

/** Readiness text gets priority; narrow screens discard art before information. */
public final class ReadinessLayout {
    public final boolean fits,art;
    public final int firstWidth,secondWidth,width;
    public ReadinessLayout(int screen,int margin,int skillText,int weaponText) {
        int available=Math.max(0,screen-2*Math.max(4,Math.min(24,margin)));
        int text=Math.max(0,skillText)+Math.max(0,weaponText)+30;
        fits=text<=available;art=fits&&text+32<=available;
        int inset=art?28:12;firstWidth=Math.max(0,skillText)+inset;secondWidth=Math.max(0,weaponText)+inset;width=firstWidth+secondWidth+6;
    }
}

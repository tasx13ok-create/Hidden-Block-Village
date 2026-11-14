package village.fieldhud;

/** A keyboard/button activation must be released before it can become the new binding. */
public final class RebindGesture {
    private int openingCode;
    private boolean armed;
    public void begin(int code){openingCode=code;armed=code==0;}
    public int openingCode(){return openingCode;}
    public boolean ready(boolean openingStillDown){if(!armed&&!openingStillDown)armed=true;return armed;}
    public void reset(){openingCode=0;armed=false;}
}

package village.fieldhud;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact grammar of Vale0.3's periodic summary; other actionbars are untouched. */
public final class ReadinessLine {
    private static final String[][] ORDERS={
        {"Ember Ward","Cinder Arc"},{"Current Ward","Flowstep"},{"Gale Ward","Windstride"},
        {"Flint Ward","Stoneguard"},{"Bloom Ward","Mending Breath"},{"Rime Ward","Stillwinter"},
        {"Storm Ward","Skybreak"},{"Dusk Ward","Quiet Veil"},{"Dawn Ward","Firstlight"}
    };
    private static final Pattern[] PATTERNS=new Pattern[ORDERS.length];
    static {
        String number="(?:0|[1-9][0-9]{0,17})(?:\\.[0-9]{1,17})?(?:[eE][+-]?[0-9]{1,3})?";
        String resource="(?<resource>Resolve (?<resolve>0|[1-9][0-9]{0,2})/100|Chakra (?:(?<amount>"+number+")/(?<max>"+number+")|unavailable))";
        for(int i=0;i<ORDERS.length;i++)PATTERNS[i]=Pattern.compile("^"+Pattern.quote(ORDERS[i][0])+" \\| "+resource+" \\| "+Pattern.quote(ORDERS[i][1])+" (?<skill>ready|[1-9][0-9]{0,5}s) \\| Technique (?<weapon>ready|[1-9][0-9]{0,5}s)$");
    }
    public static final class State {
        public final String order,skill,skillState,weaponState;
        public final int resolve;
        private final String skillLabel,weaponLabel;
        private State(String[] order,Matcher match) {
            this.order=order[0];skill=order[1];resolve=match.group("resolve")==null?0:Integer.parseInt(match.group("resolve"));skillState=match.group("skill");weaponState=match.group("weapon");
            skillLabel=skill+" "+skillState;weaponLabel="Weapon tech "+weaponState;
        }
        public String skillLabel(){return skillLabel;}
        public String weaponLabel(){return weaponLabel;}
    }
    private ReadinessLine() {}
    public static State parse(String text) {
        if(text==null||text.length()>180)return null;
        for(int i=0;i<PATTERNS.length;i++) {
            Matcher match=PATTERNS[i].matcher(text);
            if(match.matches()) {if(match.group("amount")!=null){double amount=Double.parseDouble(match.group("amount")),maximum=Double.parseDouble(match.group("max"));if(!Double.isFinite(amount)||!Double.isFinite(maximum)||amount<0||maximum<=0)return null;} State s=new State(ORDERS[i],match);return s.resolve<=100?s:null; }
        }
        return null;
    }
}

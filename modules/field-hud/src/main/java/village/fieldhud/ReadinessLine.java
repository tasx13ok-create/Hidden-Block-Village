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
        String timer="(ready|[1-9][0-9]{0,5}s)";
        for(int i=0;i<ORDERS.length;i++)PATTERNS[i]=Pattern.compile("^"+Pattern.quote(ORDERS[i][0])+" \\| Resolve (0|[1-9][0-9]{0,2})/100 \\| "+Pattern.quote(ORDERS[i][1])+" "+timer+" \\| Technique "+timer+"$");
    }
    public static final class State {
        public final String order,skill,skillState,weaponState;
        public final int resolve;
        private final String skillLabel,weaponLabel;
        private State(String[] order,Matcher match) {
            this.order=order[0];skill=order[1];resolve=Integer.parseInt(match.group(1));skillState=match.group(2);weaponState=match.group(3);
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
            if(match.matches()) { State s=new State(ORDERS[i],match);return s.resolve<=100?s:null; }
        }
        return null;
    }
}

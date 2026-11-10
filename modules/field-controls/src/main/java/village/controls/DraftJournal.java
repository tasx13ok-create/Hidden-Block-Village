package village.controls;

import java.util.*;
import village.controls.Model.Stroke;

/** Unified bounded UI undo for binding edits and whole mouse gestures. */
public final class DraftJournal {
    public static final class State {
        public final Map<String,Stroke> values;public final String layout;
        public final float sensitivity;public final boolean invert,autoJump,touchscreen;
        public State(Map<String,Stroke> values,String layout,float sensitivity,boolean invert,boolean autoJump,boolean touchscreen){
            this.values=new LinkedHashMap<>();for(Map.Entry<String,Stroke> e:values.entrySet())this.values.put(e.getKey(),new Stroke(e.getValue().code,e.getValue().mod));
            this.layout=layout;this.sensitivity=sensitivity;this.invert=invert;this.autoJump=autoJump;this.touchscreen=touchscreen;
        }
        public boolean same(State other){return values.equals(other.values)&&Objects.equals(layout,other.layout)&&Float.compare(sensitivity,other.sensitivity)==0&&invert==other.invert&&autoJump==other.autoJump&&touchscreen==other.touchscreen;}
    }
    private final Deque<State> states=new ArrayDeque<>();
    public boolean record(State before,State after){if(before.same(after))return false;states.push(before);if(states.size()>40)states.removeLast();return true;}
    public boolean available(){return !states.isEmpty();}public State undo(){return states.pollFirst();}public void clear(){states.clear();}
}

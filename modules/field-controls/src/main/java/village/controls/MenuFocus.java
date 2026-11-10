package village.controls;
import java.util.*;
/** Stable semantic focus survives value changes and rebuilds. */
public final class MenuFocus {
    private final List<String> order=new ArrayList<>();private String current="",returnTo="";private boolean isolated;
    public void rebuild(List<String> available){order.clear();order.addAll(available);if(!order.contains(current))current="";}
    public void rebuild(List<String> available,boolean modal){if(modal&&!isolated)returnTo=current;rebuild(available);if(!modal&&isolated){set(returnTo);returnTo="";}isolated=modal;}
    public String current(){return current;}public void set(String value){current=order.contains(value)?value:"";}public void clear(){current="";}
    public String move(int direction){if(order.isEmpty())return current="";int i=order.indexOf(current);i=i<0?(direction<0?order.size()-1:0):(i+(direction<0?-1:1)+order.size())%order.size();return current=order.get(i);}
}

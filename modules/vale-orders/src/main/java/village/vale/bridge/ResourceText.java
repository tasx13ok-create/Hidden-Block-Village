package village.vale.bridge;

import java.math.BigDecimal;
import net.minecraft.entity.player.EntityPlayerMP;

/** Player-facing text uses the same provider and native precision as spending. */
public final class ResourceText {
    private ResourceText(){}
    private static String number(double value){return value>=1000000||(value>0&&value<.0001)?Double.toString(value):BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    public static String balance(EntityPlayerMP p){ChakraLink.View v=MoveCost.view(p);return ("narutomod".equals(v.provider)?"Chakra":"Resolve")+(v.ready()?" "+number(v.amount)+"/"+number(v.maximum):" unavailable");}
    public static String requirement(EntityPlayerMP p,int units){
        if(MoveCost.busy(p))return "Another chakra action is still settling. Try again.";
        if(units<=0)return "This action is unavailable in the current context.";
        ChakraLink.View v=MoveCost.view(p);
        if(!v.ready())return "narutomod".equals(v.provider)?"Native chakra is not ready for this character.":"Resource is unavailable in this context.";
        return "Need "+number(units*("narutomod".equals(v.provider)?MoveCost.scale():1))+("narutomod".equals(v.provider)?" chakra.":" resolve.");
    }
}

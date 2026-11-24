package village.bijuu;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;

/** Exercise the installed bridge, including the last joint of every native appendage. */
final class PoseChecks {
    static void run(ModelBiped model,int beast) throws Exception {
        Field field=ReferenceTails.class.getDeclaredField("links");
        field.setAccessible(true);
        List<?> chains=(List<?>)((Map<?,?>)field.get(null)).get(model);
        RunProbe.check(chains.size()==beast+1,"missing native chains");
        int checked=0;
        for(Object binding:chains){
            ReferenceTails.Chain chain=(ReferenceTails.Chain)binding;
            float socket=chain.target[0].field_78798_e;
            for(ModelRenderer source:chain.source){
                float saved=source.field_78796_g;
                float before=totalYaw(chain.target);
                source.field_78796_g+=.001f;
                ReferenceTails.pose(model);
                float moved=totalYaw(chain.target)-before;
                source.field_78796_g=saved;
                ReferenceTails.pose(model);
                RunProbe.check(Math.abs(Math.IEEEremainder(moved-.001f,2*Math.PI))<.00003,"native bend lost");
                RunProbe.check(Math.abs(totalYaw(chain.target)-before)<.00003,"pose drift");
                if(source!=chain.source[0] && chain.target.length>1){
                    float start=0,end=0;
                    for(ModelRenderer part:chain.target)start+=part.field_78798_e;
                    float nativeZ=source.field_78798_e;
                    source.field_78798_e+=.03f;
                    ReferenceTails.pose(model);
                    for(ModelRenderer part:chain.target)end+=part.field_78798_e;
                    source.field_78798_e=nativeZ;
                    ReferenceTails.pose(model);
                    RunProbe.check(Math.abs(end-start-.03*chain.lengthRatio)<.0002,"native spacing lost");
                    RunProbe.check(chain.target[0].field_78798_e==socket,"waist shifted");
                }
                checked++;
            }
        }
        System.out.println("NATIVE_TAIL_PASS beast="+beast+" appendages="+chains.size()+" sourceJoints="+checked);
    }

    private static float totalYaw(ModelRenderer[] parts){
        float sum=0;
        for(ModelRenderer part:parts)sum+=part.field_78796_g;
        return sum;
    }
}

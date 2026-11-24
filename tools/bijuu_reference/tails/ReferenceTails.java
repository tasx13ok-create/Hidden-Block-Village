package village.bijuu;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.model.ModelRenderer;

/** Attach the reference hierarchy once, then transfer local motion around its rest pose. */
public final class ReferenceTails {
    private static final Map<Object,List<Chain>> links=new IdentityHashMap<>();
    private static final Map<Object,List<Object>> rendered=new IdentityHashMap<>();
    public static int visibleRoots(Object model) {
        List<Object> parts=rendered.get(model);
        if(parts==null)return 0;
        try {
            Object body=model.getClass().getField("field_78115_e").get(model);
            Field show=body.getClass().getField("field_78806_j"),hidden=body.getClass().getField("field_78807_k");
            if(!show.getBoolean(body)||hidden.getBoolean(body))return 0;
            int count=0;
            for(Object root:parts)if(show.getBoolean(root)&&!hidden.getBoolean(root))count++;
            return count;
        } catch(Exception e){throw new IllegalStateException(e);}
    }
    private static final String[] angles={"field_78795_f","field_78796_g","field_78808_h"};
    // Native identity order is left-to-right. V3 stores wings by side with the keel last,
    // and stores tentacles as alternating left/right pairs.
    private static final int[] CHOMEI={0,1,2,6,5,4,3};
    private static final int[] GYUKI={0,7,1,6,2,5,3,4};

    private static float[] anglesOf(ModelRenderer part) {
        return new float[]{part.field_78795_f,part.field_78796_g,part.field_78808_h};
    }

    private static double wrapped(double radians) {
        return Math.IEEEremainder(radians, 2*Math.PI);
    }

    static final class Chain {
        final ModelRenderer[] source, target;
        final float[][] sourceRest, targetRest;
        final float[][] weights;
        final float[] sourceZ,targetZ;
        final double lengthRatio;

        Chain(ModelRenderer[] source,ModelRenderer[] target) {
            this.source=source;
            this.target=target;
            sourceRest=new float[source.length][];
            targetRest=new float[target.length][];
            for(int i=0;i<source.length;i++)sourceRest[i]=anglesOf(source[i]);
            for(int i=0;i<target.length;i++)targetRest[i]=anglesOf(target[i]);
            sourceZ=new float[source.length];
            targetZ=new float[target.length];
            for(int i=0;i<source.length;i++)sourceZ[i]=source[i].field_78798_e;
            for(int i=0;i<target.length;i++)targetZ[i]=target[i].field_78798_e;
            weights=new float[target.length][source.length];
            weights[0][0]=1;
            // Distribute a native joint's bend across the corresponding length of
            // the reference chain. Every source column sums to one.
            double[] from=lengths(source), to=lengths(target);
            double nativeLength=from[from.length-1], visibleLength=to[to.length-1];
            lengthRatio=nativeLength>0?visibleLength/nativeLength:0;
            for(int j=1;j<from.length;j++)from[j]/=nativeLength;
            for(int i=1;i<to.length;i++)to[i]/=visibleLength;
            for(int j=1;j<source.length;j++){
                if(target.length==1){weights[0][j]=1;continue;}
                for(int i=1;i<target.length;i++){
                    double overlap=Math.min(from[j],to[i])-Math.max(from[j-1],to[i-1]);
                    if(overlap>0)weights[i][j]=(float)(overlap/(from[j]-from[j-1]));
                }
            }
        }

        private static double[] lengths(ModelRenderer[] chain) {
            double[] ends=new double[chain.length];
            for(int i=1;i<chain.length;i++){
                ModelRenderer p=chain[i];
                double x=p.field_78800_c,y=p.field_78797_d,z=p.field_78798_e;
                ends[i]=ends[i-1]+Math.max(.001,Math.sqrt(x*x+y*y+z*z));
            }
            return ends;
        }

        void apply() {
            for(int i=0;i<target.length;i++){
                double x=targetRest[i][0],y=targetRest[i][1],z=targetRest[i][2];
                double spacing=targetZ[i];
                for(int j=0;j<source.length;j++){
                    float w=weights[i][j];
                    if(w==0)continue;
                    ModelRenderer p=source[j];
                    if(i>0 && j>0)spacing+=w*lengthRatio*(p.field_78798_e-sourceZ[j]);
                    x+=w*wrapped(p.field_78795_f-sourceRest[j][0]);
                    y+=w*wrapped(p.field_78796_g-sourceRest[j][1]);
                    z+=w*wrapped(p.field_78808_h-sourceRest[j][2]);
                }
                target[i].field_78798_e=(float)spacing;
                target[i].field_78795_f=(float)x;
                target[i].field_78796_g=(float)y;
                target[i].field_78808_h=(float)z;
            }
        }
    }

    public static void pose(Object model) {
        List<Chain> chains=links.get(model);
        if(chains!=null)for(Chain chain:chains)chain.apply();
    }
    public static void install(Object model,int beast,Object slot) {
        if(!((Enum<?>)slot).name().equals("CHEST"))return;
        try(DataInputStream in=new DataInputStream(ReferenceTails.class.getResourceAsStream("/assets/hvbijuu/tails/"+beast+".bin"))) {
            Object body=model.getClass().getField("field_78115_e").get(model);
            Class<?> part=body.getClass(),base=Class.forName("net.minecraft.client.model.ModelBase");
            Constructor<?> create=part.getConstructor(base);
            Method child=part.getMethod("func_78792_a",part);
            Field stock=model.getClass().getDeclaredField(beast==0?"tail":beast==1||beast==8?"tails":"tailChains");
            stock.setAccessible(true);
            Object[] array=(Object[])stock.get(model);
            Object[][] chains=beast==0?new Object[][]{array}:(Object[][])array;
            int count=in.readInt();
            Object[] nodes=new Object[count];
            for(int i=0;i<count;i++) {
                int parent=in.readInt(); Object node=create.newInstance(model);nodes[i]=node;
                part.getMethod("func_78787_b",int.class,int.class).invoke(node,4096,4096);
                part.getMethod("func_78793_a",float.class,float.class,float.class).invoke(node,in.readFloat(),in.readFloat(),in.readFloat());
                for(String a:angles)part.getField(a).setFloat(node,in.readFloat());
                int boxes=in.readInt();
                for(int j=0;j<boxes;j++) {
                    int u=in.readInt(),v=in.readInt();
                    part.getMethod("func_78784_a",int.class,int.class).invoke(node,u,v);
                    part.getMethod("func_78789_a",float.class,float.class,float.class,int.class,int.class,int.class)
                        .invoke(node,in.readFloat(),in.readFloat(),in.readFloat(),in.readInt(),in.readInt(),in.readInt());
                }
                child.invoke(parent<0?body:nodes[parent],node);
            }
            List<Chain> list=new ArrayList<>();
            List<Object> visible=new ArrayList<>();
            int appendages=in.readInt();
            if(appendages!=chains.length)throw new IOException("Native/reference appendage count differs");
            for(int a=0;a<appendages;a++){
                ModelRenderer[] target=new ModelRenderer[in.readInt()];
                for(int i=0;i<target.length;i++)target[i]=(ModelRenderer)nodes[in.readInt()];
                int index=beast==6?CHOMEI[a]:beast==7?GYUKI[a]:a;
                list.add(new Chain((ModelRenderer[])chains[index],target));
                visible.add(target[0]);
            }
            for(Object[] chain:chains)if(chain!=null && chain.length>0 && chain[0]!=null)
                part.getField("field_78807_k").setBoolean(chain[0],true);
            links.put(model,list);
            rendered.put(model,visible);
            System.out.println("REFERENCE_TAILS "+beast+" nodes="+count+" appendages="+appendages);
        } catch(Exception e){throw new IllegalStateException("Could not attach reference tails "+beast,e);}
    }
}

package village.bijuu;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Attach the reference hierarchy once, then transfer local motion around its rest pose. */
public final class ReferenceTails {
    private static final Map<Object,List<Link>> links=new IdentityHashMap<>();
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
    private static class Link {
        Object from,to;
        final Field[] fields=new Field[3];
        final float[] rest=new float[3],base=new float[3];
        final float gain, limit;
        Link(Object from,Object to,float gain,float limit) throws Exception {
            this.gain=gain; this.limit=limit;
            this.from=from; this.to=to;
            for(int i=0;i<3;i++){
                fields[i]=to.getClass().getField(angles[i]);
                rest[i]=fields[i].getFloat(to);
                base[i]=fields[i].getFloat(from);
            }
        }
        void apply() throws Exception {
            for(int i=0;i<3;i++){
                float delta=fields[i].getFloat(from)-base[i];
                fields[i].setFloat(to,rest[i]+gain*limit*(float)Math.tanh(delta/limit));
            }
        }
    }
    public static void pose(Object model) {
        List<Link> list=links.get(model);
        if(list==null)return;
        try {for(Link link:list)link.apply();}
        catch(Exception e){throw new IllegalStateException(e);}
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
            List<Link> list=new ArrayList<>();
            List<Object> visible=new ArrayList<>();
            int appendages=in.readInt();
            for(int a=0;a<appendages;a++){
                int n=in.readInt();
                Object[] chain=a<chains.length?chains[a]:null;
                for(int k=0;k<n;k++){
                    Object target=nodes[in.readInt()];
                    if(k==0)visible.add(target);
                    if(chain!=null && chain.length>0){
                        int source=k*chain.length/n;
                        boolean wing=beast==6 && a<6;
                        int first=(source*n+chain.length-1)/chain.length;
                        int end=((source+1)*n+chain.length-1)/chain.length;
                        // Share a source bend across its sections. Wings rotate at the
                        // hinge and retain a stiff blade, with a small amount of tip flex.
                        float gain=wing?(k==0?.9f:.07f):(k==0?.48f:.85f);
                        if(!wing || k>0)gain/=Math.max(1,end-first);
                        if(chain[source]!=null)list.add(new Link(chain[source],target,gain,wing?.38f:.62f));
                    }
                }
            }
            for(Object[] chain:chains)if(chain!=null && chain.length>0 && chain[0]!=null)
                part.getField("field_78807_k").setBoolean(chain[0],true);
            links.put(model,list);
            rendered.put(model,visible);
            System.out.println("REFERENCE_TAILS "+beast+" nodes="+count+" appendages="+appendages);
        } catch(Exception e){throw new IllegalStateException("Could not attach reference tails "+beast,e);}
    }
}

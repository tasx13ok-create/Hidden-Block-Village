package village.bijuu;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

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
    // Flexibility and propagation time reflect each reference silhouette: sand club, flame
    // forks, plated tails, muscular tails, plumes, slug, beetle, tentacles and fox brush.
    private static final double[] FLEX={.58,1.05,.42,.78,.94,1.08,.72,1.24,1.0};
    private static final double[] LAG={7,3,8,5,4,9,4,7,5};
    private static class Link {
        final ModelRenderer joint;
        final int beast,arm;
        final boolean wing,root;
        final double along,weight,side,delay;
        final float pitch,yaw,roll;

        Link(Object node,int beast,int arm,int index,int count,double side) {
            this.joint=(ModelRenderer)node; this.beast=beast; this.arm=arm;
            this.side=side; wing=beast==6 && arm<6; root=index==0;
            along=index/(double)Math.max(1,count-1);
            // Integrated bend stays bounded when hierarchies have different joint counts.
            weight=root?.23:.77*(.6+.8*along)/Math.max(1,count-1);
            delay=LAG[beast]*along;
            pitch=joint.field_78795_f; yaw=joint.field_78796_g; roll=joint.field_78808_h;
        }

        void pose(WearerMotion m,double time,double partial,float stride,float amount,int seed) {
            double air=m.at(WearerMotion.AIR,delay,partial), water=m.at(WearerMotion.WATER,delay,partial);
            double speed=m.at(WearerMotion.SPEED,delay,partial), bend=m.at(WearerMotion.PITCH,delay,partial);
            double bank=m.at(WearerMotion.SIDE,delay,partial), spread=m.at(WearerMotion.FAN,delay,partial);
            double strike=m.at(WearerMotion.STRIKE,delay,partial), wall=m.at(WearerMotion.WALL,delay,partial);
            double wave=time*(.066+.012*FLEX[beast])+seed*.17+arm*1.35-along*2.7;
            double gait=Math.sin(stride*.65-along*2.3+arm*.35)*Math.min(1,amount);
            double x,y,z;
            if(wing) {
                double beat=Math.sin(time*.91+(arm%3)*.45-along*.55);
                double hinge=root?1:.09*weight;
                x=hinge*(.10*bend+.13*wall);
                y=hinge*(.19*bank+side*.12*strike);
                z=hinge*side*(beat*(.035+.43*air+.12*speed)+.13*spread-.26*water-.26*wall);
            } else {
                double flex=FLEX[beast], ripple=Math.sin(wave);
                double curl=(beast==7?.68:beast==5?.40:.14)*along*along;
                x=weight*(flex*(bend+.10*gait+.11*ripple)+wall*.94+.22*spread+
                    curl*(Math.cos(wave)+Math.abs(strike))+.14*air);
                y=weight*(flex*(bank+.17*ripple+.21*gait+.40*strike)+side*(spread-.38*wall)
                    +water*.46*Math.sin(time*.24+arm*.65-along*4));
                z=weight*(.29*bank+.26*strike+.14*side*spread+curl*.40*ripple);
            }
            // The long axis of a reference joint is local +Z; +pitch lifts its tip.
            double flank=m.at(WearerMotion.FLANK,delay,partial);
            double clearance=m.at(WearerMotion.CLEARANCE,delay,partial);
            if(wing){
                z+=side*(root?.34:.04*weight)*Math.min(0,clearance);
            }else{
                x+=weight*.62*clearance;
                y+=weight*.66*flank;
                switch(beast){
                    case 0: x+=weight*(.28*Math.max(0,bend)-.16*speed); break;
                    case 1: y+=weight*side*(.23*Math.sin(wave+along)+.22*Math.abs(bank)); break;
                    case 2: y+=weight*side*.56*spread; x+=weight*.21*spread; break;
                    case 3: x+=weight*.28*Math.abs(strike); y-=weight*.34*strike; break;
                    case 4: y-=weight*.34*side*speed; x+=weight*.15*gait; break;
                    case 5: x+=weight*along*.32*Math.sin(time*.13-arm*.65-along*4); break;
                    case 6: y-=weight*.38*bank*air; break;
                    case 7: y+=weight*side*along*.65*(Math.abs(strike)+spread); break;
                    case 8: y+=weight*side*(.50*spread-.15*speed); x+=weight*.23*Math.abs(strike); break;
                    default: throw new IllegalArgumentException("Unknown beast "+beast);
                }
            }
            joint.field_78795_f=pitch+(float)x; joint.field_78796_g=yaw+(float)y;
            joint.field_78808_h=roll+(float)z;
        }
    }

    public static void pose(Object model,Entity wearer,float stride,float amount,float age) {
        List<Link> list=links.get(model);
        if(list==null || !(wearer instanceof EntityLivingBase))return;
        EntityLivingBase actor=(EntityLivingBase)wearer;
        WearerMotion state=WearerMotion.sample(actor);
        double partial=age-actor.field_70173_aa;
        for(Link link:list)link.pose(state,age,partial,stride,amount,actor.func_145782_y());
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
                double side=0;
                for(int k=0;k<n;k++){
                    Object target=nodes[in.readInt()];
                    if(k==0){
                        visible.add(target);
                        side=Math.signum(((ModelRenderer)target).field_78800_c);
                    }
                    list.add(new Link(target,beast,a,k,n,side));
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

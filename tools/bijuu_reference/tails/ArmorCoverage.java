package village.bijuu;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Omit covered vanilla skin cubes during this player's draw. Bones still animate
 * normally, including their child renderers and held-item attachment transforms. */
@Mod.EventBusSubscriber(modid="hvbijuu",value=Side.CLIENT)
public final class ArmorCoverage {
    private static final String[] CHEST={"field_78115_e","field_178730_v","field_178723_h","field_178724_i","field_178732_b","field_178734_a"};
    private static final String[] LEGS={"field_178721_j","field_178722_k","field_178731_d","field_178733_c"};
    private static final Deque<List<Hidden>> frames=new ArrayDeque<>();
    private static final Map<Object,Integer> emptyLists=new WeakHashMap<>();
    private static final Map<Class<?>,Field[]> fields=new HashMap<>();
    private static final Map<String,Object> slots=new HashMap<>();
    private static Method equipped,item;
    private static Field[] geometry(Object part) throws ReflectiveOperationException {
        Class<?> type=part.getClass();
        Field[] value=fields.get(type);
        if(value==null){
            Class<?> base=Class.forName("net.minecraft.client.model.ModelRenderer");
            value=new Field[]{base.getField("field_78804_l"),base.getDeclaredField("field_78812_q"),base.getDeclaredField("field_78811_r")};
            for(Field f:value)f.setAccessible(true);
            fields.put(type,value);
        }
        return value;
    }
    private static class Hidden {
        final Object part;
        final Field[] access;
        final List<Object> cubes,saved;
        final boolean compiled;
        final int list;
        @SuppressWarnings("unchecked")
        Hidden(Object part) throws ReflectiveOperationException {
            this.part=part;
            access=geometry(part);
            cubes=(List<Object>)access[0].get(part);
            saved=new ArrayList<>(cubes);
            compiled=access[1].getBoolean(part);
            list=access[2].getInt(part);
            int blank=emptyLists.getOrDefault(part,0);
            cubes.clear();
            access[1].setBoolean(part,blank!=0);
            access[2].setInt(part,blank);
        }
        void restore() throws ReflectiveOperationException {
            emptyLists.put(part,access[2].getInt(part));
            cubes.addAll(saved);
            access[2].setInt(part,list);
            access[1].setBoolean(part,compiled);
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static boolean wears(Object player,String slot,String suffix) throws ReflectiveOperationException {
        if(equipped==null){
            Class<?> type=Class.forName("net.minecraft.inventory.EntityEquipmentSlot");
            for(String s:new String[]{"HEAD","CHEST","LEGS","FEET"})slots.put(s,Enum.valueOf((Class)type,s));
            equipped=Class.forName("net.minecraft.entity.EntityLivingBase").getMethod("func_184582_a",type);
            item=Class.forName("net.minecraft.item.ItemStack").getMethod("func_77973_b");
        }
        Object value=item.invoke(equipped.invoke(player,slots.get(slot)));
        String name=String.valueOf(value.getClass().getMethod("getRegistryName").invoke(value));
        return name.startsWith("hvbijuu:") && name.endsWith("_"+suffix);
    }
    private static void hide(List<Hidden> frame,Object model,String[] names) throws ReflectiveOperationException {
        for(String name:names)frame.add(new Hidden(model.getClass().getField(name).get(model)));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void before(RenderPlayerEvent.Pre event) {
        List<Hidden> frame=new ArrayList<>();
        frames.push(frame);
        try{
            Object player=event.getClass().getMethod("getEntityPlayer").invoke(event);
            Object renderer=event.getClass().getMethod("getRenderer").invoke(event);
            Object model=renderer.getClass().getMethod("func_177087_b").invoke(renderer);
            // Keep the real skin and its outer head layer behind the visor.
            if(wears(player,"CHEST","chestplate"))hide(frame,model,CHEST);
            if(wears(player,"LEGS","leggings") && wears(player,"FEET","boots"))hide(frame,model,LEGS);
        }catch(ReflectiveOperationException e){restore();throw new IllegalStateException(e);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void after(RenderPlayerEvent.Post event){restore();}
    private static void restore(){
        if(frames.isEmpty())return;
        try{for(Hidden h:frames.pop())h.restore();}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
}

package village.vale.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import village.vale.Dodge;
import village.vale.DodgeMath;

/**
 * Dodge key (default V, rebindable under Controls > Vale Orders) plus the four animations:
 * front = forward shoulder roll, back = back flip, left/right = mirrored side barrel rolls with
 * spread limbs. Body rotation is applied around the hips in RenderPlayerEvent; limb tucks come
 * from the existing biped pose hook. First person gets a short FOV kick instead.
 */
@SideOnly(Side.CLIENT)
public final class DodgeClient {
    public static final KeyBinding DODGE = new KeyBinding("key.valeorders.dodge", KeyConflictContext.IN_GAME, Keyboard.KEY_V, "key.categories.valeorders");
    private static final Map<Integer, double[]> ACTIVE = new ConcurrentHashMap<>(); // id -> {dir, startTick}
    private static long localCooldownUntil;
    private static final java.util.Set<Integer> PUSHED = ConcurrentHashMap.newKeySet();

    private DodgeClient() {}

    public static void initialize() {
        ClientRegistry.registerKeyBinding(DODGE);
        MinecraftForge.EVENT_BUS.register(new DodgeClient());
    }

    private static double now(float partial) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.world == null ? 0 : mc.world.getTotalWorldTime() + partial;
    }

    /** Server confirmed a dodge for entity {@code id}. */
    public static void start(int id, int dir) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (Minecraft.getMinecraft().world == null) return;
            ACTIVE.put(id, new double[] {dir, now(0)});
            Entity e = Minecraft.getMinecraft().world.getEntityByID(id);
            if (e != null) for (int i = 0; i < 8; i++)
                e.world.spawnParticle(EnumParticleTypes.CLOUD, e.posX + (e.world.rand.nextDouble() - 0.5) * 0.6, e.posY + 0.1, e.posZ + (e.world.rand.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        });
    }

    /** Age in ticks of an active dodge, or -1. */
    static double age(Entity e, float partial) {
        double[] a = ACTIVE.get(e.getEntityId());
        if (a == null) return -1;
        double age = now(partial) - a[1];
        if (age > DodgeMath.ANIM_TICKS + 1 || age < -1) { ACTIVE.remove(e.getEntityId()); return -1; }
        return Math.max(0, age);
    }

    static int dir(Entity e) { double[] a = ACTIVE.get(e.getEntityId()); return a == null ? -1 : (int) a[0]; }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.currentScreen != null) return;
        while (DODGE.isPressed()) {
            long t = mc.world.getTotalWorldTime();
            if (t < localCooldownUntil) continue;
            localCooldownUntil = t + DodgeMath.COOLDOWN_TICKS;
            Dodge.request(DodgeMath.direction(mc.player.movementInput.moveForward, mc.player.movementInput.moveStrafe));
        }
        // Trail puffs while rolling.
        for (Map.Entry<Integer, double[]> a : ACTIVE.entrySet()) {
            Entity en = mc.world.getEntityByID(a.getKey());
            if (en == null) continue;
            double age = mc.world.getTotalWorldTime() - a.getValue()[1];
            if (age >= 0 && age < DodgeMath.ANIM_TICKS * 0.75 && ((long) age) % 2 == 0)
                mc.world.spawnParticle(EnumParticleTypes.CLOUD, en.posX, en.posY + 0.4, en.posZ, -en.motionX * 0.2, 0.01, -en.motionZ * 0.2);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void pre(RenderPlayerEvent.Pre e) {
        EntityPlayer p = e.getEntityPlayer();
        double age = age(p, e.getPartialRenderTick());
        if (age < 0) return;
        int dir = dir(p);
        float body = p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * e.getPartialRenderTick();
        double spin = DodgeMath.spin(dir, age);
        double hip = p.isSneaking() ? 0.7 : 0.9;
        // Roll compresses the body around the hips: lower the pivot a little mid-roll.
        double dip = 0.25 * DodgeMath.tuck(age);
        GlStateManager.pushMatrix();
        PUSHED.add(p.getEntityId());
        GlStateManager.translate(e.getX(), e.getY() + hip - dip, e.getZ());
        GlStateManager.rotate(-body, 0, 1, 0);
        if (dir == DodgeMath.FRONT || dir == DodgeMath.BACK) GlStateManager.rotate((float) spin, 1, 0, 0);
        else GlStateManager.rotate((float) spin, 0, 0, 1);
        GlStateManager.rotate(body, 0, 1, 0);
        GlStateManager.translate(-e.getX(), -e.getY() - hip, -e.getZ());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true) public void post(RenderPlayerEvent.Post e) {
        // Post fires only if Pre was not cancelled; only pop what Pre pushed.
        if (PUSHED.remove(e.getEntityPlayer().getEntityId())) GlStateManager.popMatrix();
    }

    @SubscribeEvent public void fov(FOVUpdateEvent e) {
        double age = age(e.getEntity(), Minecraft.getMinecraft().getRenderPartialTicks());
        if (age >= 0) e.setNewfov((float) (e.getNewfov() * (1 + 0.12 * DodgeMath.tuck(age))));
    }

    /** Limb tucks per direction (called from the biped pose hook after vanilla angles). */
    public static void pose(ModelBiped m, Entity entity, float ageInTicks) {
        if (!(entity instanceof EntityPlayer) || ageInTicks == 0) return;
        double age = age(entity, Minecraft.getMinecraft().getRenderPartialTicks());
        if (age < 0) return;
        int dir = dir(entity);
        float k = (float) DodgeMath.tuck(age);
        m.bipedHead.rotateAngleX = lerp(m.bipedHead.rotateAngleX, dir == DodgeMath.BACK ? -0.6f : 0.7f, k);
        switch (dir) {
            case DodgeMath.FRONT: // tight shoulder roll: knees to chest, arms wrapped
                m.bipedRightLeg.rotateAngleX = lerp(m.bipedRightLeg.rotateAngleX, -1.9f, k);
                m.bipedLeftLeg.rotateAngleX = lerp(m.bipedLeftLeg.rotateAngleX, -1.7f, k);
                m.bipedRightArm.rotateAngleX = lerp(m.bipedRightArm.rotateAngleX, -1.4f, k);
                m.bipedLeftArm.rotateAngleX = lerp(m.bipedLeftArm.rotateAngleX, -1.4f, k);
                m.bipedRightArm.rotateAngleZ = lerp(m.bipedRightArm.rotateAngleZ, 0.5f, k);
                m.bipedLeftArm.rotateAngleZ = lerp(m.bipedLeftArm.rotateAngleZ, -0.5f, k);
                break;
            case DodgeMath.BACK: { // back flip: arms sweep overhead first, then knees tuck
                double u = age / DodgeMath.ANIM_TICKS;
                float reach = (float) Math.max(0, 1 - u * 2.2);
                m.bipedRightArm.rotateAngleX = lerp(lerp(m.bipedRightArm.rotateAngleX, -1.2f, k), -3.0f, reach);
                m.bipedLeftArm.rotateAngleX = lerp(lerp(m.bipedLeftArm.rotateAngleX, -1.2f, k), -3.0f, reach);
                m.bipedRightLeg.rotateAngleX = lerp(m.bipedRightLeg.rotateAngleX, -1.5f, k);
                m.bipedLeftLeg.rotateAngleX = lerp(m.bipedLeftLeg.rotateAngleX, -1.5f, k);
                break;
            }
            default: { // side barrel roll: limbs spread like a cartwheel, mirrored left/right
                float s = dir == DodgeMath.LEFT ? 1 : -1;
                m.bipedRightArm.rotateAngleZ = lerp(m.bipedRightArm.rotateAngleZ, 2.3f, k);
                m.bipedLeftArm.rotateAngleZ = lerp(m.bipedLeftArm.rotateAngleZ, -2.3f, k);
                m.bipedRightArm.rotateAngleX = lerp(m.bipedRightArm.rotateAngleX, 0, k);
                m.bipedLeftArm.rotateAngleX = lerp(m.bipedLeftArm.rotateAngleX, 0, k);
                m.bipedRightLeg.rotateAngleZ = lerp(m.bipedRightLeg.rotateAngleZ, 0.55f, k);
                m.bipedLeftLeg.rotateAngleZ = lerp(m.bipedLeftLeg.rotateAngleZ, -0.55f, k);
                m.bipedRightLeg.rotateAngleX = lerp(m.bipedRightLeg.rotateAngleX, 0, k);
                m.bipedLeftLeg.rotateAngleX = lerp(m.bipedLeftLeg.rotateAngleX, 0, k);
                m.bipedHead.rotateAngleZ = lerp(m.bipedHead.rotateAngleZ, 0.3f * s, k);
                break;
            }
        }
        m.bipedHeadwear.rotateAngleX = m.bipedHead.rotateAngleX;
        m.bipedHeadwear.rotateAngleZ = m.bipedHead.rotateAngleZ;
    }

    private static float lerp(float a, float b, float k) { return a + (b - a) * k; }
}

package village.immersion;

import java.util.Iterator;
import java.util.Map;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * What wall movement looks and sounds like, for you and for players near you: footsteps and dust
 * from the wall's own block, a scrape while sliding, a push-off puff on wall jumps, the camera
 * leaning away from a wall you run along (with a slight field-of-view widening), and the body
 * leaning off the wall in third person and for other players.
 */
@SideOnly(Side.CLIENT)
final class ParkourFx {
    static void contact(EntityPlayer p, int state, int nx, int nz) {
        IBlockState b = ParkourClient.wallBlock(p, nx, nz);
        SoundType t = b.getBlock().getSoundType(b, p.world, null, p);
        p.world.playSound(p.posX, p.posY, p.posZ, t.getStepSound(), SoundCategory.PLAYERS, 0.5f, t.getPitch() * 1.1f, false);
        dust(p, b, nx, nz, 6);
    }

    static void wallJump(EntityPlayer p, int nx, int nz) {
        IBlockState b = ParkourClient.wallBlock(p, nx, nz);
        SoundType t = b.getBlock().getSoundType(b, p.world, null, p);
        p.world.playSound(p.posX, p.posY, p.posZ, t.getStepSound(), SoundCategory.PLAYERS, 0.7f, t.getPitch() * 0.8f, false);
        p.world.playSound(p.posX, p.posY, p.posZ, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.18f, 1.6f, false);
        dust(p, b, nx, nz, 10);
        for (int i = 0; i < 5; i++) p.world.spawnParticle(EnumParticleTypes.CLOUD, p.posX - nx * 0.4, p.posY + 0.3 + i * 0.25, p.posZ - nz * 0.4, nx * 0.05, 0, nz * 0.05);
    }

    static void ledge(EntityPlayer p) {
        p.world.playSound(p.posX, p.posY, p.posZ, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.5f, 1.3f, false);
        for (int i = 0; i < 6; i++) p.world.spawnParticle(EnumParticleTypes.CLOUD, p.posX + (p.world.rand.nextDouble() - 0.5) * 0.6, p.posY + 1.6, p.posZ + (p.world.rand.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
    }

    private static void dust(EntityPlayer p, IBlockState b, int nx, int nz, int n) {
        if (b.getMaterial() == net.minecraft.block.material.Material.AIR) return;
        int id = net.minecraft.block.Block.getStateId(b);
        for (int i = 0; i < n; i++) {
            double y = p.posY + 0.2 + p.world.rand.nextDouble() * 1.4;
            p.world.spawnParticle(EnumParticleTypes.BLOCK_DUST, p.posX - nx * 0.35, y, p.posZ - nz * 0.35, nx * 0.06, -0.02, nz * 0.06, id);
        }
    }

    // ---- ongoing effects for every player on a wall ----
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.isGamePaused()) return;
        long now = mc.world.getTotalWorldTime();
        for (Iterator<Map.Entry<Integer, int[]>> it = Parkour.REMOTE.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, int[]> en = it.next();
            Entity ent = mc.world.getEntityByID(en.getKey());
            if (!(ent instanceof EntityPlayer) || en.getValue()[0] == ParkourMath.NONE && now - en.getValue()[3] > 20 || now - en.getValue()[3] > 80) { it.remove(); continue; }
            ongoing((EntityPlayer) ent, en.getValue()[0], en.getValue()[1], en.getValue()[2], (int) (now - en.getValue()[3]));
        }
        if (mc.player != null && ParkourClient.state() != ParkourMath.NONE) {
            int[] n = currentNormal();
            ongoing(mc.player, ParkourClient.state(), n[0], n[1], ParkourClient.ticksIn());
        }
    }

    private static int[] currentNormal() {
        EntityPlayer p = Minecraft.getMinecraft().player;
        double yaw = Math.toRadians(p.rotationYaw);
        int[] w = ParkourClient.wall(Minecraft.getMinecraft().player, -Math.sin(yaw), Math.cos(yaw));
        return w == null ? new int[] {0, 0} : w;
    }

    private static void ongoing(EntityPlayer p, int state, int nx, int nz, int age) {
        if (nx == 0 && nz == 0) return;
        IBlockState b = ParkourClient.wallBlock(p, nx, nz);
        SoundType t = b.getBlock().getSoundType(b, p.world, null, p);
        if (state == ParkourMath.SLIDE) {
            if (age % 4 == 0) p.world.playSound(p.posX, p.posY, p.posZ, t.getStepSound(), SoundCategory.PLAYERS, 0.25f, t.getPitch() * 0.6f, false);
            if (age % 2 == 0) dust(p, b, nx, nz, 2);
        } else if (age % (state == ParkourMath.RUN_UP ? 3 : 4) == 0) {
            p.world.playSound(p.posX, p.posY, p.posZ, t.getStepSound(), SoundCategory.PLAYERS, 0.35f, t.getPitch() * (age % 8 == 0 ? 1.0f : 1.15f), false);
            dust(p, b, nx, nz, 2);
        }
    }

    static void remote(int entity, int state, int nx, int nz, int event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return;
        Parkour.REMOTE.put(entity, new int[] {state, nx, nz, (int) mc.world.getTotalWorldTime()});
        Entity ent = mc.world.getEntityByID(entity);
        if (!(ent instanceof EntityPlayer)) return;
        if (event == 1) wallJump((EntityPlayer) ent, nx, nz);
        else if (state != ParkourMath.NONE) contact((EntityPlayer) ent, state, nx, nz);
    }

    // ---- camera ----
    @SubscribeEvent public void camera(EntityViewRenderEvent.CameraSetup e) {
        if (!Config.cameraTilt) return;
        double roll = -ParkourMath.tilt(ParkourClient.side(), ParkourClient.ticksIn(), ParkourClient.sinceEnd());
        if (roll != 0) e.setRoll((float) (e.getRoll() + roll));
    }

    @SubscribeEvent public void fov(FOVUpdateEvent e) {
        if (!Config.cameraTilt && !Config.dodgeFeel) return;
        float k = ParkourMath.fov(Config.dodgeFeel ? DodgeFx.sinceMs() : -1, Config.cameraTilt && ParkourClient.state() == ParkourMath.RUN_SIDE);
        if (k != 1f) e.setNewfov(e.getNewfov() * k);
    }

    // ---- body lean off the wall (third person and other players) ----
    private static final java.util.Set<EntityPlayer> LEANING = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    @SubscribeEvent public void pre(RenderPlayerEvent.Pre e) {
        double lean = lean(e.getEntityPlayer());
        if (lean == 0) return;
        LEANING.add(e.getEntityPlayer());
        GlStateManager.pushMatrix();
        GlStateManager.translate(e.getX(), e.getY(), e.getZ());
        float yaw = e.getEntityPlayer().prevRenderYawOffset + (e.getEntityPlayer().renderYawOffset - e.getEntityPlayer().prevRenderYawOffset) * e.getPartialRenderTick();
        GlStateManager.rotate(-yaw, 0, 1, 0);
        GlStateManager.rotate((float) lean, 0, 0, 1);
        GlStateManager.rotate(yaw, 0, 1, 0);
        GlStateManager.translate(-e.getX(), -e.getY(), -e.getZ());
    }

    @SubscribeEvent public void post(RenderPlayerEvent.Post e) { if (LEANING.remove(e.getEntityPlayer())) GlStateManager.popMatrix(); }

    /** Degrees to lean the body during a side run: away from the wall. */
    private static double lean(EntityPlayer p) {
        int state, nx, nz;
        if (ClientInput.local(p)) { state = ParkourClient.state(); if (state != ParkourMath.RUN_SIDE) return 0; return -12 * ParkourClient.side(); }
        int[] r = Parkour.REMOTE.get(p.getEntityId());
        if (r == null || r[0] != ParkourMath.RUN_SIDE) return 0;
        state = r[0]; nx = r[1]; nz = r[2];
        double yaw = Math.toRadians(p.rotationYaw);
        return state == ParkourMath.RUN_SIDE ? -12 * ParkourMath.side(-Math.sin(yaw), Math.cos(yaw), nx, nz) : 0;
    }
}

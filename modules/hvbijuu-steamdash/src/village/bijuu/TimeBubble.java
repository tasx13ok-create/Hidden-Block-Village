package village.bijuu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Bullet-time bubble: for about a second around a steam punch, everything nearby except the two
 * fighters runs at a quarter speed. Mobs only update on every fourth tick (AI, movement and animation
 * together); arrows, thrown items, dropped items and falling blocks keep only a quarter of each tick's
 * movement and velocity change, then carry on normally when the bubble ends. Players are never slowed.
 */
final class TimeBubble {
    private static final class Bubble { final World world; final Vec3d c; final long until; final Set<Integer> exclude; Bubble(World w, Vec3d c, long u, Set<Integer> ex) { world = w; this.c = c; until = u; exclude = ex; } }
    private static final List<Bubble> BUBBLES = new ArrayList<>();
    private static final Map<Integer, double[]> BEFORE = new HashMap<>();   // entity id -> {x, y, z, mx, my, mz}

    static void open(World w, Vec3d centre, Set<Integer> exclude) {
        if (!SteamDash.timeBubble) return;
        BUBBLES.add(new Bubble(w, centre, w.getTotalWorldTime() + DashMath.BUBBLE_TICKS, exclude));
    }

    static boolean active(World w) { for (Bubble b : BUBBLES) if (b.world == w) return true; return false; }

    private static Bubble inside(Entity e) {
        for (Bubble b : BUBBLES)
            if (b.world == e.world && !b.exclude.contains(e.getEntityId()) && e.getDistanceSq(b.c.x, b.c.y, b.c.z) <= DashMath.BUBBLE_RADIUS * DashMath.BUBBLE_RADIUS) return b;
        return null;
    }

    /** Slowed mobs skip three of every four updates. */
    @SubscribeEvent public void living(LivingEvent.LivingUpdateEvent e) {
        EntityLivingBase l = e.getEntityLiving();
        if (BUBBLES.isEmpty() || l.world.isRemote || l instanceof EntityPlayer) return;
        if (inside(l) != null && !DashMath.bubbleLets(l.world.getTotalWorldTime())) e.setCanceled(true);
    }

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent e) {
        if (e.world.isRemote || BUBBLES.isEmpty()) return;
        World w = e.world;
        if (e.phase == TickEvent.Phase.START) {
            BEFORE.clear();
            for (Bubble b : BUBBLES) {
                if (b.world != w) continue;
                double r = DashMath.BUBBLE_RADIUS;
                for (Entity x : w.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(b.c.x - r, b.c.y - r, b.c.z - r, b.c.x + r, b.c.y + r, b.c.z + r))) {
                    if (x instanceof EntityLivingBase || x instanceof net.minecraft.entity.EntityHanging || b.exclude.contains(x.getEntityId())) continue;
                    BEFORE.put(x.getEntityId(), new double[] {x.posX, x.posY, x.posZ, x.motionX, x.motionY, x.motionZ});
                }
            }
            return;
        }
        // END: projectiles and loose objects keep a quarter of this tick's step.
        for (Map.Entry<Integer, double[]> en : BEFORE.entrySet()) {
            Entity x = w.getEntityByID(en.getKey());
            if (x == null || x.isDead || inside(x) == null) continue;
            double[] b = en.getValue();
            x.setPosition(DashMath.bubbleBlend(b[0], x.posX), DashMath.bubbleBlend(b[1], x.posY), DashMath.bubbleBlend(b[2], x.posZ));
            x.motionX = DashMath.bubbleBlend(b[3], x.motionX); x.motionY = DashMath.bubbleBlend(b[4], x.motionY); x.motionZ = DashMath.bubbleBlend(b[5], x.motionZ);
            x.velocityChanged = true;
        }
        BEFORE.clear();
        long now = w.getTotalWorldTime();
        for (Iterator<Bubble> it = BUBBLES.iterator(); it.hasNext();) { Bubble b = it.next(); if (b.world == w && now >= b.until) it.remove(); }
    }

    @SubscribeEvent public void unload(WorldEvent.Unload e) { BUBBLES.removeIf(b -> b.world == e.getWorld()); }
}

package village.bijuu;

/**
 * Pure timing and geometry for the Steam Dash Punch (no Minecraft types; checked headlessly).
 * Timeline, from the key press: telegraph 0-3, blink at 4, dash 4 until contact (at most 4 + MAX_DASH),
 * hit-stop for FREEZE ticks, then the boil launch for LAUNCH ticks; a second press inside
 * the follow-up window spikes the target into the ground.
 */
public final class DashMath {
    private DashMath() {}

    public static final int TELEGRAPH = 4, MAX_DASH = 7, FREEZE = 3, LAUNCH = 25, SPIKE_WINDOW = 30, RING_POINTS = 24, GHOSTS = 4;
    public static final double DASH_SPEED = 5.0, CONTACT = 2.0, LAUNCH_SPEED = 4.2, LAUNCH_LIFT = 1.1, SPIKE_SPEED = 3.8;
    public static final double SPRING_SPEED = 1.2 * 1.4, SMASH_SPEED = 1.2, SPLATTER_RADIUS = 4;

    /** Range at which a target can be picked: the dash covers MAX_DASH ticks at full speed. */
    public static double range() { return DASH_SPEED * MAX_DASH; }

    /** Unit vector from a to b, or a fallback when they coincide. */
    public static double[] direction(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return l < 1e-6 ? new double[] {0, 0, 1} : new double[] {dx / l, dy / l, dz / l};
    }

    /** Dash velocity this tick: full speed, but never past the stop point in front of the target. */
    public static double dashStep(double distance) { return Math.max(0, Math.min(DASH_SPEED, distance - CONTACT * 0.6)); }

    /** Cosine of the widest angle off the crosshair a target may be (about 12 degrees). */
    public static final double AIM_COS = Math.cos(Math.toRadians(12));

    /** Launch velocity {x, y, z}: the attacker's look scaled to LAUNCH_SPEED plus LAUNCH_LIFT upward; bosses fly half as far. */
    public static double[] launch(double[] look, boolean boss) {
        double[] d = norm(look);
        double s = LAUNCH_SPEED * (boss ? 0.5 : 1);
        return new double[] {d[0] * s, d[1] * s + LAUNCH_LIFT * (boss ? 0.6 : 1), d[2] * s};
    }

    /** Horizontal speed the boil keeps a launched body at, `age` ticks after release: a 0.93-per-tick decay (bosses half). */
    public static double boilSpeed(int age, boolean boss) { return LAUNCH_SPEED * Math.pow(0.93, Math.max(0, age)) * (boss ? 0.5 : 1); }

    /** Whether a launched body still smashes terrain at this speed (blocks per tick). */
    public static boolean smashing(double speed) { return speed > SMASH_SPEED; }

    /** The spike's slam velocity: 75 degrees below horizontal, SPIKE_SPEED downward, continuing the flight's heading. */
    public static double[] spikeVelocity(double[] flight) {
        double hx = flight[0], hz = flight[2], l = Math.sqrt(hx * hx + hz * hz);
        if (l < 1e-6) return new double[] {0, -SPIKE_SPEED, 0};
        double h = SPIKE_SPEED / Math.tan(Math.toRadians(75));
        return new double[] {hx / l * h, -SPIKE_SPEED, hz / l * h};
    }

    /**
     * Springboard off a wall: the look direction with its into-the-wall part removed, pushed out along the
     * wall normal and slightly up, at SPRING_SPEED (1.4x the base kick).
     */
    public static double[] springboard(double[] normal, double[] look) {
        double[] n = norm(normal), k = norm(look);
        double into = Math.min(0, k[0] * n[0] + k[1] * n[1] + k[2] * n[2]);
        double[] v = norm(new double[] {k[0] - n[0] * into + n[0] * 0.8, Math.max(0, k[1] - n[1] * into) + 0.3, k[2] - n[2] * into + n[2] * 0.8});
        return new double[] {v[0] * SPRING_SPEED, v[1] * SPRING_SPEED, v[2] * SPRING_SPEED};
    }

    /** A size x size cross-section (2 or 3) perpendicular to travel, for the randomized rubble around a flying body. */
    public static int[][] box(double[] dir, int size) {
        if (size >= 3) return tunnel(dir);
        double[] d = norm(dir);
        double[] a = Math.abs(d[1]) > 0.9 ? new double[] {1, 0, 0} : new double[] {0, 1, 0};
        double[] u = norm(cross(d, a)), v = cross(d, u);
        int[][] out = new int[4][];
        int k = 0;
        for (int i = 0; i <= 1; i++) for (int j = 0; j <= 1; j++)
            out[k++] = new int[] {(int) Math.round(u[0] * i + v[0] * j), (int) Math.round(u[1] * i + v[1] * j), (int) Math.round(u[2] * i + v[2] * j)};
        return out;
    }

    /**
     * Wall-splatter spiderweb: for a cell offset (a, b) in the wall plane, 0 = untouched, 1 = cobblestone,
     * 2 = cracked stone bricks. A smashed core, seven jagged spokes and a broken ring at about 2.8 blocks.
     */
    public static int splatter(int a, int b, long seed) {
        double r = Math.sqrt(a * a + b * b);
        if (r > SPLATTER_RADIUS + 0.01) return 0;
        if (r <= 1.5) return 1;
        double ang = Math.atan2(b, a);
        java.util.Random rand = new java.util.Random(seed);
        double base = rand.nextDouble() * 2 * Math.PI;
        for (int i = 0; i < 7; i++) {
            double spoke = base + i * 2 * Math.PI / 7 + (rand.nextDouble() - 0.5) * 0.5;
            double off = Math.abs(Math.atan2(Math.sin(ang - spoke), Math.cos(ang - spoke)));
            if (off * r <= 0.55) return 2;
        }
        if (Math.abs(r - 2.8) <= 0.45 && ((a * 31 + b * 17 + (int) seed) & 3) != 0) return 1;
        return 0;
    }

    /**
     * A ring of points perpendicular to a direction (the "wind tunnel" rings), radius r, n points.
     * Returns n {dx, dy, dz} offsets.
     */
    public static double[][] ring(double[] dir, double r, int n) {
        double[] d = norm(dir);
        double[] a = Math.abs(d[1]) > 0.9 ? new double[] {1, 0, 0} : new double[] {0, 1, 0};
        double[] u = norm(cross(d, a)), v = cross(d, u);
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) {
            double t = 2 * Math.PI * i / n, c = Math.cos(t) * r, s = Math.sin(t) * r;
            out[i] = new double[] {u[0] * c + v[0] * s, u[1] * c + v[1] * s, u[2] * c + v[2] * s};
        }
        return out;
    }

    /** The 3x3 cross-section of cells to clear around a point, perpendicular to travel. 9 {dx, dy, dz} block offsets. */
    public static int[][] tunnel(double[] dir) {
        double[] d = norm(dir);
        double[] a = Math.abs(d[1]) > 0.9 ? new double[] {1, 0, 0} : new double[] {0, 1, 0};
        double[] u = norm(cross(d, a)), v = cross(d, u);
        int[][] out = new int[9][];
        int k = 0;
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++)
            out[k++] = new int[] {(int) Math.round(u[0] * i + v[0] * j), (int) Math.round(u[1] * i + v[1] * j), (int) Math.round(u[2] * i + v[2] * j)};
        return out;
    }

    /** Crater depth at a horizontal distance from its centre: a bowl, deepest (3) in the middle, 0 at the rim. */
    public static int craterDepth(double distance, double radius) {
        if (distance >= radius) return 0;
        double u = distance / radius;
        return (int) Math.round(3 * (1 - u * u));
    }

    /** Whether a block of this hardness may be smashed (unbreakable is negative). */
    public static boolean breakable(float hardness, float max) { return hardness >= 0 && hardness <= max; }

    /** Fraction of smashed blocks shown as flying debris. */
    public static final double DEBRIS_SHARE = 0.15;

    /** Field of view multiplier over the sequence (ms since press): pull in on the telegraph, snap wide on the blink, ease back. */
    public static float fov(double ms) {
        if (ms < 0) return 1f;
        if (ms < 200) return 1f - 0.08f * (float) (ms / 200);
        if (ms < 260) return 0.92f + (1.25f - 0.92f) * (float) ((ms - 200) / 60);
        if (ms < 1200) return 1.25f - 0.25f * (float) ((ms - 260) / 940);
        return 1f;
    }

    /** Camera shake amplitude in degrees after an impact (ms since impact), decaying from 1.5. */
    public static double shake(double ms) { return ms < 0 || ms > 450 ? 0 : 1.5 * Math.pow(1 - ms / 450, 2); }

    /** Afterimage alpha over its 8-tick life: 0.55 fading to 0. */
    public static float ghostAlpha(double age) { return age < 0 || age >= 8 ? 0f : (float) (0.55 * (1 - age / 8)); }

    /** Trail clone alpha by age in the ring buffer (0 newest .. GHOSTS-1 oldest): 0.55 down toward 0. */
    public static float trailAlpha(int index) { return index < 0 || index >= GHOSTS ? 0f : 0.55f * (1 - index / (float) GHOSTS); }

    /** Impact frame at ticks since contact: 1 = white flash, 2 = ink silhouette, 0 = normal (the explosion lands at FREEZE). */
    public static int impactFrame(double ticks) { return ticks < 0 ? 0 : ticks < 1 ? 1 : ticks < FREEZE ? 2 : 0; }

    /** Where the follow-up spike puts the attacker: 2 behind (against the flight) and 2 above the target. */
    public static double[] spikeSpot(double tx, double ty, double tz, double[] flight) {
        double hx = flight[0], hz = flight[2], l = Math.sqrt(hx * hx + hz * hz);
        if (l < 1e-6) { hx = 0; hz = 1; l = 1; }
        return new double[] {tx - hx / l * 2, ty + 2, tz - hz / l * 2};
    }

    /** The point on segment a-b closest to p. */
    public static double[] closest(double ax, double ay, double az, double bx, double by, double bz, double px, double py, double pz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, l2 = dx * dx + dy * dy + dz * dz;
        double t = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / l2));
        return new double[] {ax + dx * t, ay + dy * t, az + dz * t};
    }

    // ---- 2.0: charge tiers, juggle, pin, camera, world-scale effects ----

    /** Charge tier from ticks the key was held: a tap is 1, a short hold 2, a full overcharge (1 s) 3. */
    public static int tier(int heldTicks) { return heldTicks >= 20 ? 3 : heldTicks >= 7 ? 2 : 1; }
    public static final int CHARGE_MAX = 50;
    public static int tierCost(int tier, int base) { return base + 15 * (tier - 1); }
    public static double tierDamage(int tier) { return tier >= 3 ? 1.5 : tier == 2 ? 1.25 : 1.0; }
    public static double tierLaunch(int tier) { return tier >= 3 ? 1.3 : tier == 2 ? 1.15 : 1.0; }
    /** Extra clearance around the tunnelling hitbox: tier 3 bores a 5x5. */
    public static double tierMargin(int tier) { return tier >= 3 ? 1.0 : tier == 2 ? 0.5 : 0.0; }
    public static int tierFreeze(int tier) { return tier >= 3 ? FREEZE + 1 : FREEZE; }

    /** Afterimage barrage: the four trail clones strike, one per tick, before the real punch. */
    public static final int BARRAGE = 4, RELAYS = 2, RELAY_FREEZE = 2, PIN = 6, PIERCE_FREEZE = 2, PIERCE_MAX = 6;
    public static final double PIERCE_WIDTH = 1.8;

    /** Where a relay blink lands: 3 blocks ahead of the flying body along its horizontal heading. */
    public static double[] relaySpot(double tx, double ty, double tz, double[] flight) {
        double hx = flight[0], hz = flight[2], l = Math.sqrt(hx * hx + hz * hz);
        if (l < 1e-6) { hx = 0; hz = 1; l = 1; }
        return new double[] {tx + hx / l * 3, ty + 0.2, tz + hz / l * 3};
    }

    /** The relay punch sends the body back the way it came, a little slower, lifted again. */
    public static double[] relayLaunch(double[] flight, boolean boss) {
        double hx = -flight[0], hz = -flight[2], l = Math.sqrt(hx * hx + hz * hz);
        if (l < 1e-6) { hx = 0; hz = -1; l = 1; }
        double s = LAUNCH_SPEED * 0.9 * (boss ? 0.5 : 1);
        return new double[] {hx / l * s, LAUNCH_LIFT * (boss ? 0.6 : 1), hz / l * s};
    }

    /** Ground points for the nine tail stabs that pin the target before the spike. */
    public static double[][] pinStabs(double tx, double ty, double tz) {
        double[][] out = new double[9][];
        for (int i = 0; i < 9; i++) {
            double a = i * 2 * Math.PI / 9 + 0.35, r = 1.6 + (i % 3) * 0.45;
            out[i] = new double[] {tx + Math.cos(a) * r, ty, tz + Math.sin(a) * r};
        }
        return out;
    }

    /** Camera distance that keeps the subject the same size while the field of view changes (dolly zoom). */
    public static double dollyDistance(double d0, double fov0, double fov) {
        return d0 * Math.tan(Math.toRadians(fov0) / 2) / Math.tan(Math.toRadians(fov) / 2);
    }

    /** Minecraft yaw and pitch (degrees) to look from a to b. */
    public static float[] lookAngles(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, h = Math.sqrt(dx * dx + dz * dz);
        return new float[] {(float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, h))};
    }

    /** A point on a horizontal circle around c at `deg` (0 = +Z), raised by `up`. */
    public static double[] orbit(double cx, double cy, double cz, double radius, double deg, double up) {
        double a = Math.toRadians(deg);
        return new double[] {cx - Math.sin(a) * radius, cy + up, cz + Math.cos(a) * radius};
    }

    /** Smoothstep easing on [0, 1]. */
    public static double ease(double u) { u = Math.max(0, Math.min(1, u)); return u * u * (3 - 2 * u); }

    /** Thunder heard far away arrives late: sound travels about 343 m/s, one block a metre. */
    public static long thunderDelayMs(double distance) { return distance <= 24 ? 0 : Math.round(distance / 343.0 * 1000); }

    /** Ground ripple front radius after `ms`: 16 blocks a second out to 14 blocks. */
    public static double rippleRadius(double ms) { return Math.min(14, Math.max(0, ms) / 1000.0 * 16); }

    /** Time bubble: radius, life and rate (a slowed mob only updates on every fourth tick). */
    public static final double BUBBLE_RADIUS = 12;
    public static final int BUBBLE_TICKS = 24, BUBBLE_EVERY = 4;
    public static boolean bubbleLets(long tick) { return tick % BUBBLE_EVERY == 0; }
    /** A projectile in the bubble keeps only a quarter of each tick's movement and velocity change. */
    public static double bubbleBlend(double before, double after) { return before + (after - before) / BUBBLE_EVERY; }

    /** Molten scars: how a cooled scar block ends up (0 = cobblestone, 1 = andesite), stable per position. */
    public static int coolTo(int x, int y, int z) { return (int) (((x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L)) & 0x7fffffff) % 10 < 7 ? 0 : 1; }

    static double[] cross(double[] a, double[] b) { return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return l < 1e-9 ? new double[] {0, 0, 1} : new double[] {a[0] / l, a[1] / l, a[2] / l}; }
}

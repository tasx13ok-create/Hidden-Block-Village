/*
 * Tail collision and inertia solver (Hidden Village Bijuu Armor), physics pass 2.
 * Same public API as 0.16.2-rc1; callers are unchanged.
 *
 * Changes from rc1:
 *  - Adaptive sub-steps (2..6) from root and tip speed, so fast swings and jumps no longer tunnel.
 *  - Verlet momentum: segments keep their own velocity between ticks instead of being re-clamped to
 *    +/-0.45 and scaled by 0.8 every tick, so tails trail starts, whip on turns and jumps, and keep
 *    moving after the body stops. A smooth speed limit replaces the hard clamp.
 *  - Target spring scales per sub-step (frame-rate independent) and still softens toward the tip.
 *  - Bending limit (each node kept away from its grandparent) so a tail can curl but never fold
 *    back through itself.
 *  - Contact friction: nodes pushed out of a block lose most of their sliding velocity, so resting
 *    tails stay put on the ground instead of skating and jittering.
 *  - Root impulse: a sudden root jump/landing adds a damped kick down the chain (follow-through).
 *  - All results checked finite; a non-finite state resets cleanly to the animated pose.
 */
package village.bijuu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import village.bijuu.TailProfile;

public final class TailPhysics {
    public static int ITERATIONS = 12;
    public static int COLLISION_DISTANCE = 48;
    /** Fraction of sliding velocity removed from a node touching a block, per sub-step. */
    public static double FRICTION = 0.55;
    /** Smallest allowed distance to a grandparent, as a fraction of the two segment lengths. */
    public static double BEND = 0.58;
    /** Smooth per-tick speed limit (blocks per tick). */
    public static double MAX_SPEED = 0.9;
    /** Pose spring strength relative to rc1 (lower = more momentum and trailing). */
    public static double INERTIA_SPRING = 0.75;
    /** How far (in ticks of root motion) the tip's goal trails behind the root. */
    public static double TRAIL = 1.6;
    public final double[][] points;
    public final double[][] previous;
    private final double[][] velocity;
    private final double[] radii;
    private final double[] length;
    private final double[] beforeNode = new double[3];
    private final double[] beforeParent = new double[3];
    private final double[] mid = new double[3];
    private final double[] beforeMid = new double[3];
    private final double[] next = new double[3];
    private final boolean[] contact;
    private final double[] lastRoot = new double[3];
    private final double[] rootVelocity = new double[3];
    private final ArrayList<Box>[] local;
    private boolean initialized;

    public TailPhysics(double[][] dArray, double[] dArray2) {
        this.points = TailPhysics.copy(dArray);
        this.previous = TailPhysics.copy(dArray);
        for (double[] dArray3 : this.velocity = TailPhysics.copy(dArray)) {
            Arrays.fill(dArray3, 0.0);
        }
        this.radii = (double[])dArray2.clone();
        this.length = new double[dArray.length];
        this.contact = new boolean[dArray.length];
        this.local = new ArrayList[dArray.length];
        for (int i = 0; i < this.local.length; ++i) {
            this.local[i] = new ArrayList();
        }
        System.arraycopy(dArray[0], 0, this.lastRoot, 0, 3);
    }

    static double[][] copy(double[][] dArray) {
        double[][] dArrayArray = new double[dArray.length][];
        for (int i = 0; i < dArray.length; ++i) {
            dArrayArray[i] = (double[])dArray[i].clone();
        }
        return dArrayArray;
    }

    public void step(double[][] dArray, List<Box> list, boolean bl) {
        this.step(dArray, list, TailProfile.of(bl ? 0 : 8), false);
    }

    private void reset(double[][] target) {
        for (int n = 0; n < this.points.length; ++n) {
            System.arraycopy(target[n], 0, this.points[n], 0, 3);
            System.arraycopy(target[n], 0, this.previous[n], 0, 3);
            Arrays.fill(this.velocity[n], 0.0);
        }
        System.arraycopy(target[0], 0, this.lastRoot, 0, 3);
        Arrays.fill(this.rootVelocity, 0.0);
        this.initialized = false;
    }

    static double soft(double v, double limit) {
        // Smooth limiter: ~linear for small v, approaches +/-limit for large v (keeps whip, kills explosions).
        return limit * Math.tanh(v / limit);
    }

    public void step(double[][] target, List<Box> list, TailProfile profile, boolean braced) {
        int count = this.points.length;
        if (count < 2) { if (count == 1) System.arraycopy(target[0], 0, this.points[0], 0, 3); return; }
        if (TailPhysics.distance(this.points[0], target[0]) > 3.0) this.reset(target);
        if (!this.initialized) {
            for (int n = 1; n < count; ++n) TailPhysics.sweep(this.points[n - 1], this.points[n], list, this.radii[n]);
            this.initialized = true;
        }
        for (int n = 0; n < count; ++n) System.arraycopy(this.points[n], 0, this.previous[n], 0, 3);
        for (int n = 1; n < count; ++n) this.length[n] = Math.max(1.0E-4, TailPhysics.distance(target[n], target[n - 1]));

        // Root motion this tick, and its change (impulse) for follow-through.
        double[] rootDelta = new double[3], impulse = new double[3];
        double rootSpeed = 0;
        for (int k = 0; k < 3; ++k) {
            rootDelta[k] = target[0][k] - this.lastRoot[k];
            impulse[k] = rootDelta[k] - this.rootVelocity[k];
            this.rootVelocity[k] = rootDelta[k];
            rootSpeed += rootDelta[k] * rootDelta[k];
        }
        rootSpeed = Math.sqrt(rootSpeed);
        double tipSpeed = 0;
        for (int k = 0; k < 3; ++k) tipSpeed = Math.max(tipSpeed, Math.abs(this.velocity[count - 1][k]));
        int sub = Math.max(2, Math.min(6, (int) Math.ceil(Math.max(rootSpeed, tipSpeed) / 0.22)));
        double h = 1.0 / sub;

        double drag = braced ? 0.65 : Math.min(0.97, profile.drag);
        double follow = braced ? 0.25 : profile.follow;
        double gravity = braced ? 0.002 : profile.gravity;
        double dragStep = Math.pow(drag, h);

        // Per-node world-space velocity for this tick (carried from last tick), plus a damped impulse kick.
        double[][] vel = new double[count][3];
        for (int n = 1; n < count; ++n) {
            double d = n / (double) (count - 1);
            for (int k = 0; k < 3; ++k) vel[n][k] = this.velocity[n][k] - impulse[k] * (0.35 + 0.4 * d) * (braced ? 0.2 : 1.0);
        }

        for (int s = 0; s < sub; ++s) {
            // Root follows its target smoothly across sub-steps.
            for (int k = 0; k < 3; ++k) this.points[0][k] = this.lastRoot[k] + rootDelta[k] * (s + 1) * h;
            for (int n = 1; n < count; ++n) {
                double d = n / (double) (count - 1);
                double nodeDrag = Math.min(0.985, Math.pow(Math.min(0.97, drag + d * 0.012), h));
                if (rootSpeed < 0.01) nodeDrag *= Math.pow(0.82, h);   // settle quickly once the body stops
                double spring = follow * (1.0 - d * 0.28) * INERTIA_SPRING;
                // Spring toward the animated pose; the pose lags behind the root by the node's position
                // along the chain, so motion travels down the tail instead of moving it rigidly.
                for (int k = 0; k < 3; ++k) {
                    double goal = target[n][k] - rootDelta[k] * d * TRAIL;
                    vel[n][k] = vel[n][k] * nodeDrag + (goal - this.points[n][k]) * spring * h;
                }
                vel[n][1] -= gravity * (0.75 + d * 0.5) * h;
                for (int k = 0; k < 3; ++k) this.next[k] = this.points[n][k] + vel[n][k] * h;
                TailPhysics.sweep(this.points[n], this.next, list, this.radii[n]);
                System.arraycopy(this.next, 0, this.points[n], 0, 3);
            }
            if (s == 0) {
                this.local[0].clear();
                this.local[0].addAll(list);
                for (int n = 1; n < count; ++n) {
                    ArrayList<Box> near = this.local[n];
                    near.clear();
                    for (Box box : list) {
                        boolean inside = true;
                        for (int k = 0; k < 3; ++k) if (this.points[n][k] < box.lo[k] - 1.6 || this.points[n][k] > box.hi[k] + 1.6) { inside = false; break; }
                        if (inside) near.add(box);
                    }
                }
            }
            Arrays.fill(this.contact, false);
            int iterations = Math.max(8, ITERATIONS * 2 / Math.max(2, sub));
            for (int it = 0; it < iterations; ++it) {
                for (int i = 1; i < count; ++i) {
                    System.arraycopy(this.points[i], 0, this.beforeNode, 0, 3);
                    System.arraycopy(this.points[i - 1], 0, this.beforeParent, 0, 3);
                    double dist = Math.max(1.0E-9, TailPhysics.distance(this.points[i], this.points[i - 1]));
                    double err = (dist - this.length[i]) / dist;
                    for (int k = 0; k < 3; ++k) {
                        double c = (this.points[i][k] - this.points[i - 1][k]) * err;
                        if (i == 1) this.points[i][k] -= c;
                        else { this.points[i][k] -= c * 0.5; this.points[i - 1][k] += c * 0.5; }
                    }
                    if (i >= 2) {   // bending limit against the grandparent
                        double reach = Math.max(1.0E-9, TailPhysics.distance(this.points[i], this.points[i - 2]));
                        double min = BEND * (this.length[i] + this.length[i - 1]);
                        if (reach < min) {
                            double push = (min - reach) / reach;
                            for (int k = 0; k < 3; ++k) {
                                double c = (this.points[i][k] - this.points[i - 2][k]) * push;
                                this.points[i][k] += c * (i == 2 ? 1.0 : 0.6);
                                if (i > 2) this.points[i - 2][k] -= c * 0.4;
                            }
                        }
                    }
                    TailPhysics.sweep(this.beforeNode, this.points[i], this.local[i], this.radii[i]);
                    if (i > 1) TailPhysics.sweep(this.beforeParent, this.points[i - 1], this.local[i - 1], this.radii[i - 1]);
                    System.arraycopy(this.points[i], 0, this.beforeNode, 0, 3);
                    TailPhysics.project(this.points[i], this.local[i], this.radii[i]);
                    if (TailPhysics.distance(this.points[i], this.beforeNode) > 1.0E-6) this.contact[i] = true;
                    for (int q = 1; q < 4; ++q) {
                        double f = q / 4.0;
                        for (int k = 0; k < 3; ++k) this.mid[k] = this.points[i - 1][k] * (1.0 - f) + this.points[i][k] * f;
                        System.arraycopy(this.mid, 0, this.beforeMid, 0, 3);
                        TailPhysics.project(this.mid, this.local[i], Math.min(this.radii[i], this.radii[i - 1]));
                        for (int k = 0; k < 3; ++k) {
                            double dd = this.mid[k] - this.beforeMid[k];
                            if (dd != 0) this.contact[i] = true;
                            this.points[i][k] += dd;
                            if (i > 1) this.points[i - 1][k] += dd;
                        }
                    }
                }
            }
            // Follow-the-leader pass: lock every segment to its rest length from the root outward,
            // keeping direction, so momentum never stretches the tail. Collision is re-checked per node.
            for (int n = 1; n < count; ++n) {
                double dist = Math.max(1.0E-9, TailPhysics.distance(this.points[n], this.points[n - 1]));
                System.arraycopy(this.points[n], 0, this.beforeNode, 0, 3);
                for (int k = 0; k < 3; ++k) this.points[n][k] = this.points[n - 1][k] + (this.points[n][k] - this.points[n - 1][k]) * (this.length[n] / dist);
                TailPhysics.sweep(this.beforeNode, this.points[n], this.local[n], this.radii[n]);
                TailPhysics.project(this.points[n], list, this.radii[n]);
            }
            // Friction: a node touching a block loses most of its sliding speed and all bounce.
            for (int n = 1; n < count; ++n) {
                if (this.contact[n]) for (int k = 0; k < 3; ++k) vel[n][k] *= (k == 1 ? 0.2 : 1.0 - FRICTION);
            }
        }
        System.arraycopy(target[0], 0, this.points[0], 0, 3);
        System.arraycopy(target[0], 0, this.lastRoot, 0, 3);
        boolean finite = true;
        for (int n = 1; n < count; ++n) {
            for (int k = 0; k < 3; ++k) {
                // Carry real displacement (constraints included) relative to the root's own motion.
                double v = 0.5 * (this.points[n][k] - this.previous[n][k]) + 0.5 * vel[n][k];
                this.velocity[n][k] = TailPhysics.soft(v, MAX_SPEED);
                if (!Double.isFinite(this.points[n][k]) || !Double.isFinite(this.velocity[n][k])) finite = false;
            }
        }
        if (!finite) this.reset(target);
    }

    public static void sweep(double[] dArray, double[] dArray2, List<Box> list, double d) {
        for (int i = 0; i < 3; ++i) {
            double d2;
            double d3 = 1.0;
            int n = -1;
            double d4 = 0.0;
            for (Box box : list) {
                double d5 = 0.0;
                double d6 = 1.0;
                int n2 = -1;
                double d7 = 0.0;
                boolean bl = false;
                for (int j = 0; j < 3; ++j) {
                    double d8 = dArray2[j] - dArray[j];
                    double d9 = box.lo[j] - d;
                    double d10 = box.hi[j] + d;
                    if (Math.abs(d8) < 1.0E-10) {
                        if (!(dArray[j] <= d9) && !(dArray[j] >= d10)) continue;
                        bl = true;
                        break;
                    }
                    double d11 = (d9 - dArray[j]) / d8;
                    double d12 = (d10 - dArray[j]) / d8;
                    double d13 = -1.0;
                    if (d11 > d12) {
                        double d14 = d11;
                        d11 = d12;
                        d12 = d14;
                        d13 = 1.0;
                    }
                    if (d11 >= d5) {
                        d5 = d11;
                        n2 = j;
                        d7 = d13;
                    }
                    if (!(d5 > (d6 = Math.min(d6, d12)))) continue;
                    bl = true;
                    break;
                }
                if (bl || n2 < 0 || !(d5 >= 0.0) || !(d5 < d3)) continue;
                d3 = d5;
                n = n2;
                d4 = d7;
            }
            if (n < 0) break;
            dArray2[n] = d2 = dArray[n] + (dArray2[n] - dArray[n]) * d3 + d4 * 1.0E-4;
        }
        TailPhysics.project(dArray2, list, d);
    }

    public static void project(double[] dArray, List<Box> list, double d) {
        for (int i = 0; i < 3; ++i) {
            for (Box box : list) {
                boolean bl = true;
                for (int j = 0; j < 3; ++j) {
                    if (!(dArray[j] <= box.lo[j] - d) && !(dArray[j] >= box.hi[j] + d)) continue;
                    bl = false;
                    break;
                }
                if (!bl) continue;
                double d2 = Double.MAX_VALUE;
                double d3 = 0.0;
                int n = 0;
                for (int j = 0; j < 3; ++j) {
                    double d4 = dArray[j] - (box.lo[j] - d);
                    double d5 = box.hi[j] + d - dArray[j];
                    if (d4 < d2) {
                        d2 = d4;
                        d3 = box.lo[j] - d - 1.0E-4;
                        n = j;
                    }
                    if (!(d5 < d2)) continue;
                    d2 = d5;
                    d3 = box.hi[j] + d + 1.0E-4;
                    n = j;
                }
                dArray[n] = d3;
            }
        }
    }

    public static double distance(double[] dArray, double[] dArray2) {
        double d = 0.0;
        for (int i = 0; i < 3; ++i) {
            d += (dArray[i] - dArray2[i]) * (dArray[i] - dArray2[i]);
        }
        return Math.sqrt(d);
    }

    public static final class Box {
        public final double[] lo;
        public final double[] hi;

        public Box(double d, double d2, double d3, double d4, double d5, double d6) {
            this.lo = new double[]{d, d2, d3};
            this.hi = new double[]{d4, d5, d6};
        }
    }
}


package village.residents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Provision only three owned roles in loaded overworld chunks; never replace a saved slot. */
public final class Provisioning {
    public static final int MAX_ENTITY_SCAN = 4096;
    private static final Set<WorldServer> RUNNING = Collections.newSetFromMap(new IdentityHashMap<WorldServer, Boolean>());
    private Provisioning() {}

    public static String run(WorldServer world) {
        if (world.provider.getDimension() != 0) return "Residents provision only in the overworld.";
        if (ValeResidents.maxResidents <= 0) return "Resident provisioning is disabled by its configured cap.";
        if (!RUNNING.add(world)) return "Provisioning is already in progress.";
        try {
            if (world.loadedEntityList.size() > MAX_ENTITY_SCAN)
                return "Provisioning deferred: loaded-entity scan budget exceeded; no entities changed.";
            ResidentsData data = ResidentsData.get(world);
            if (data.slots.isReadOnly()) return "Saved resident schema is unsupported or malformed; preserved without spawning.";
            List<UUID> loaded = new ArrayList<>();
            Map<ResidentRole, List<EntityResident>> roles = new EnumMap<>(ResidentRole.class);
            for (ResidentRole role : ResidentRole.values()) roles.put(role, new ArrayList<>());
            for (Entity entity : world.loadedEntityList) {
                if (!(entity instanceof EntityResident) || entity.isDead) continue;
                EntityResident resident = (EntityResident) entity;
                loaded.add(resident.getUniqueID());
                if (resident.role() != null) roles.get(resident.role()).add(resident);
            }
            int adopted = 0, spawned = 0, blocked = 0;
            for (ResidentRole role : ResidentRole.values()) {
                if (data.slots.occupied(role)) continue;
                List<EntityResident> existing = roles.get(role);
                if (existing.size() > 1) {
                    if (data.slots.recordConflict(role, existing.size())) data.markDirty();
                    blocked++;
                } else if (existing.size() == 1) {
                    EntityResident resident = existing.get(0);
                    if (data.slots.reserve(role, resident.getUniqueID(), 0, resident.getPosition(),
                            ResidentSlots.HARD_LIMIT, Collections.emptyList())) {
                        data.markDirty();
                        adopted++;
                    }
                }
            }
            for (ResidentRole role : ResidentRole.values()) {
                if (data.slots.occupied(role)) continue;
                if (!data.slots.mayReserve(role, ValeResidents.maxResidents, loaded)) { blocked++; continue; }
                EntityResident resident = new EntityResident(world);
                resident.configure(role);
                BlockPos home = safePosition(world, resident, world.getSpawnPoint().add(role.offsetX, 0, role.offsetZ));
                if (home == null) { blocked++; continue; }
                UUID id = resident.getUniqueID();
                if (!data.slots.reserve(role, id, 0, home, ValeResidents.maxResidents, loaded)) { blocked++; continue; }
                data.markDirty();
                boolean accepted = false;
                try {
                    accepted = world.spawnEntity(resident);
                    if (accepted) { loaded.add(id); spawned++; }
                    else blocked++;
                } finally {
                    if (!accepted && !resident.addedToChunk && !world.loadedEntityList.contains(resident)
                        && data.slots.cancelRejected(role, id)) data.markDirty();
                }
            }
            return "Residents: " + spawned + " provisioned, " + adopted + " existing adopted, "
                + blocked + " deferred; " + data.slots.count() + " saved role slots. Existing actors/data were not deleted.";
        } finally {
            RUNNING.remove(world);
        }
    }

    private static BlockPos safePosition(WorldServer world, EntityResident resident, BlockPos center) {
        // At most25 positions for each of three roles; no chunk ticket or force-load.
        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                BlockPos column = center.add(dx, 0, dz);
                if (!world.isBlockLoaded(column)) continue;
                BlockPos feet = world.getTopSolidOrLiquidBlock(column);
                if (feet.getY() < 1 || feet.getY() > 253) continue;
                if (!world.isAreaLoaded(feet.add(-1, -1, -1), feet.add(1, 2, 1))) continue;
                IBlockState floor = world.getBlockState(feet.down());
                if (!floor.isFullCube() || floor.getMaterial().isLiquid() || floor.getMaterial() == Material.LEAVES
                    || floor.getBlock() == Blocks.LOG || floor.getBlock() == Blocks.LOG2
                    || floor.getBlock() == Blocks.MAGMA) continue;
                resident.setPosition(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
                if (!world.getCollisionBoxes(resident, resident.getEntityBoundingBox()).isEmpty()
                    || world.containsAnyLiquid(resident.getEntityBoundingBox())
                    || !world.checkNoEntityCollision(resident.getEntityBoundingBox(), resident)) continue;
                return feet;
            }
        }
        return null;
    }
}

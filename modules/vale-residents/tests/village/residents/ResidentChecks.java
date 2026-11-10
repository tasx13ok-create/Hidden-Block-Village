package village.residents;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;

/** Real vanilla NBT disk round trips and provisioning invariants; not an engine restart test. */
public final class ResidentChecks {
    private static int checks;
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static final UUID A = UUID.fromString("b5f4857c-2763-4b36-a29b-d7270710c599");
    private static final UUID B = UUID.fromString("13472bd0-7b53-4f4b-9bfd-e4b1447397c1");
    private static final UUID C = UUID.fromString("8590e3d4-3a80-4af8-bd2f-d83ab3213790");

    public static void main(String[] args) throws Exception {
        ResidentSlots slots = new ResidentSlots();
        require(!slots.isReadOnly() && slots.count() == 0, "Fresh own data is writable and empty");
        require(slots.mayReserve(ResidentRole.ACADEMY, 3, Collections.emptyList()), "Fresh academy can be reserved");
        require(!slots.mayReserve(ResidentRole.ACADEMY, 0, Collections.emptyList()), "Zero cap disables creation");
        require(!slots.mayReserve(ResidentRole.ACADEMY, -1, Collections.emptyList()), "Negative cap cannot create");
        require(!slots.mayReserve(ResidentRole.ACADEMY, 999, Arrays.asList(A, B, C)), "Excessive configured cap cannot bypass three existing actors");
        require(slots.reserve(ResidentRole.ACADEMY, A, 0, new BlockPos(-700, 76, 2048), 3, Collections.emptyList()), "Reserve academy UUID");
        require(!slots.reserve(ResidentRole.ACADEMY, B, 0, BlockPos.ORIGIN, 3, Collections.emptyList()), "Unloaded/missing academy slot cannot be silently replaced");
        require(slots.occupancyIncludingLoaded(Collections.singleton(A)) == 1, "Loaded and saved UUID is counted once");
        require(slots.occupancyIncludingLoaded(Arrays.asList(A, B)) == 2, "Unregistered loaded actor consumes capacity");
        require(!slots.mayReserve(ResidentRole.CONTRACTS, 2, Arrays.asList(A, B)), "Loaded unregistered actor prevents cap bypass");
        require(slots.reserve(ResidentRole.CONTRACTS, B, 0, new BlockPos(4, 64, -8), 3, Collections.singleton(A)), "Reserve contracts");
        require(slots.reserve(ResidentRole.QUARTERMASTER, C, 0, new BlockPos(8, 65, 12), 999, Arrays.asList(A, B)), "Hard cap accepts third role even with excessive configured maximum");
        require(slots.count() == 3 && slots.occupancyIncludingLoaded(Arrays.asList(A, B, C)) == 3, "Three role IDs remain distinct and bounded");
        require(!slots.mayReserve(ResidentRole.ACADEMY, 999, Collections.emptyList()), "Occupied role remains blocked after chunk unload");

        NBTTagCompound state = slots.write();
        state.setString("ExtensionSentinel", "preserve me");
        state.getCompoundTag("Slots").getCompoundTag("academy").setString("FutureField", "keep");
        Path directory = Files.createTempDirectory("vale-residents-nbt-");
        Path file = directory.resolve("vale_residents.dat");
        try {
            try (FileOutputStream output = new FileOutputStream(file.toFile())) { CompressedStreamTools.writeCompressed(state, output); }
            NBTTagCompound loaded;
            try (FileInputStream input = new FileInputStream(file.toFile())) { loaded = CompressedStreamTools.readCompressed(input); }
            ResidentSlots restored = ResidentSlots.read(loaded);
            require(A.equals(restored.uuid(ResidentRole.ACADEMY)), "Academy UUID survives native compressed-NBT disk reload");
            require(B.equals(restored.uuid(ResidentRole.CONTRACTS)) && C.equals(restored.uuid(ResidentRole.QUARTERMASTER)), "Other role UUIDs survive reload");
            require(restored.write().getCompoundTag("Slots").getCompoundTag("academy").getInteger("HomeX") == -700, "Negative home coordinate survives reload");
            require(restored.write().equals(state), "All known/unknown owned data survives disk reload unchanged");
            require(!restored.reserve(ResidentRole.ACADEMY, B, 0, BlockPos.ORIGIN, 3, Collections.emptyList()), "Reload does not treat unloaded actor as a new spawn vacancy");
            require(!restored.cancelRejected(ResidentRole.ACADEMY, B), "Rejected-spawn rollback cannot erase another UUID");
            require(A.equals(restored.uuid(ResidentRole.ACADEMY)), "Mismatched rollback leaves UUID intact");
            require(restored.cancelRejected(ResidentRole.QUARTERMASTER, C), "Matching rejected reservation can roll back");
            require(!restored.occupied(ResidentRole.QUARTERMASTER), "Only the matching slot is released");
            require(restored.write().getString("ExtensionSentinel").equals("preserve me"), "Rollback preserves unrelated data");
            require(restored.write().getCompoundTag("Slots").getCompoundTag("academy").getString("FutureField").equals("keep"), "Rollback preserves another slot's extension");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }

        NBTTagCompound future = state.copy();
        future.setInteger("Schema", 2);
        ResidentSlots unsupported = ResidentSlots.read(future);
        require(unsupported.isReadOnly(), "Future schema is read-only");
        require(!unsupported.reserve(ResidentRole.ACADEMY, B, 0, BlockPos.ORIGIN, 3, Collections.emptyList()), "Future schema cannot provision");
        require(!unsupported.cancelRejected(ResidentRole.ACADEMY, A), "Future schema cannot be cleared by rollback");
        require(unsupported.write().equals(future), "Future schema is preserved byte-value-for-byte-value");
        NBTTagCompound malformed = new NBTTagCompound();
        malformed.setString("Schema", "1");
        malformed.setString("Slots", "opaque");
        require(ResidentSlots.read(malformed).isReadOnly(), "Wrong tag types block mutation");
        require(ResidentSlots.read(malformed).write().equals(malformed), "Malformed data is retained");

        NBTTagCompound badSlot = new ResidentSlots().write();
        badSlot.getCompoundTag("Slots").setTag("academy", new NBTTagString("unknown saved value"));
        ResidentSlots bad = ResidentSlots.read(badSlot);
        require(bad.occupied(ResidentRole.ACADEMY) && bad.uuid(ResidentRole.ACADEMY) == null, "Malformed role remains occupied");
        require(!bad.mayReserve(ResidentRole.ACADEMY, 3, Collections.emptyList()), "Malformed role is never auto-overwritten");
        require(bad.occupancyIncludingLoaded(Collections.emptyList()) == 1, "Malformed reservation still consumes capacity");
        require(bad.write().equals(badSlot), "Malformed slot round trip is lossless");
        ResidentSlots conflict = new ResidentSlots();
        require(conflict.recordConflict(ResidentRole.CONTRACTS, 2), "Loaded duplicate role is recorded without deleting actors");
        require(!conflict.mayReserve(ResidentRole.ACADEMY, 3, Arrays.asList(A, B)), "Conflict count conservatively prevents more actors");
        require(!conflict.cancelRejected(ResidentRole.CONTRACTS, A), "Conflict marker cannot be mistaken for a failed fresh reservation");

        InteractionWindow window = new InteractionWindow();
        require(window.allow(A, 100), "First menu interaction accepted");
        require(!window.allow(A, 100) && !window.allow(A, 109), "Duplicate hand/spam window suppressed");
        require(window.allow(B, 100), "One player does not block a different player");
        require(window.allow(A, 110), "Menu interaction resumes at exact boundary");
        require(window.allow(A, 1), "Clock rollback does not lock a player out indefinitely");
        for (int i = 0; i < 4096; i++) window.allow(new UUID(7, i), 200);
        require(window.size() <= 64, "Interaction cache stays bounded across many players");
        require(ResidentRole.fromId("old_naruto_role") == null && ResidentRole.fromId("future_role") == null, "Unknown legacy/future role IDs are not silently remapped");
        require(ResidentRole.values().length == 3, "Provisioning catalog is exactly three original roles");

        NBTTagCompound entity = new NBTTagCompound();
        entity.setUniqueId("UUID", A);
        entity.setString("OtherModSentinel", "untouched");
        ResidentIdentity.forRole(ResidentRole.ACADEMY).writeEntity(entity);
        ResidentIdentity identity = ResidentIdentity.readEntity(entity);
        require(identity.interactiveRole() == ResidentRole.ACADEMY, "Known own schema exposes the correct role");
        NBTTagCompound savedEntity = entity.copy();
        identity.writeEntity(savedEntity);
        require(savedEntity.equals(entity) && A.equals(savedEntity.getUniqueId("UUID")), "Identity encoding preserves UUID and unrelated entity tags");
        NBTTagCompound futureEntity = entity.copy();
        futureEntity.getCompoundTag(ResidentIdentity.KEY).setInteger("Schema", 2);
        futureEntity.getCompoundTag(ResidentIdentity.KEY).setString("NewField", "future");
        ResidentIdentity futureIdentity = ResidentIdentity.readEntity(futureEntity);
        require(futureIdentity.interactiveRole() == null, "Future entity schema cannot call current menu actions");
        NBTTagCompound futureCopy = futureEntity.copy();
        futureIdentity.writeEntity(futureCopy);
        require(futureCopy.equals(futureEntity), "Future entity identity is lossless");
        NBTTagCompound opaqueEntity = entity.copy();
        opaqueEntity.setString(ResidentIdentity.KEY, "opaque future identity");
        NBTTagCompound opaqueCopy = opaqueEntity.copy();
        ResidentIdentity.readEntity(opaqueEntity).writeEntity(opaqueCopy);
        require(opaqueCopy.equals(opaqueEntity), "Non-compound identity tag is preserved instead of replaced");
        require(ResidentIdentity.readEntity(opaqueEntity).interactiveRole() == null, "Opaque identity does not open a guessed role");
        NBTTagCompound absent = new NBTTagCompound();
        ResidentIdentity.readEntity(absent).writeEntity(absent);
        require(!absent.hasKey(ResidentIdentity.KEY), "Missing identity does not invent a default resident role");
        NBTTagCompound unknownEntity = entity.copy();
        unknownEntity.getCompoundTag(ResidentIdentity.KEY).setString("Role", "some_future_role");
        require(ResidentIdentity.readEntity(unknownEntity).interactiveRole() == null, "Unknown role remains unmapped");
        System.out.println("PASS: " + checks + " resident persistence/capacity/menu checks using native compressed NBT; no live world/restart claim.");
    }
}

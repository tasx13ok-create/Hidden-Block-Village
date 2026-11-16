package net.narutomod.guardfixture;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

/** Synthetic caller identity only. Contains no code or assets from Naruto mods. */
public final class FakeAbility {
    private FakeAbility() { }

    public static boolean set(World world, BlockPos pos, IBlockState state) {
        return world.setBlockState(pos, state, 3);
    }

    public static boolean erase(World world, BlockPos pos) {
        return world.setBlockToAir(pos);
    }

    public static boolean destroy(World world, BlockPos pos, boolean drop) {
        return world.destroyBlock(pos, drop);
    }

    public static IBlockState chunkSet(World world, BlockPos pos, IBlockState state) {
        return world.getChunk(pos).setBlockState(pos, state);
    }

    public static void drop(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        state.getBlock().dropBlockAsItemWithChance(world, pos, state, 1.0F, 0);
    }

    public static boolean rule(GameRules rules, String name) {
        return rules.getBoolean(name);
    }

    public static boolean mobGriefing(World world, Entity entity) {
        return ForgeEventFactory.getMobGriefingEvent(world, entity);
    }

    public static boolean damage(EntityLivingBase target, float amount) {
        return target.attackEntityFrom(DamageSource.MAGIC, amount);
    }
}

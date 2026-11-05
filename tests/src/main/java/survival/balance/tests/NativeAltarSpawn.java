package survival.balance.tests;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** TEST ONLY: invoke the installed altar's complete native spawn path before combat. */
public final class NativeAltarSpawn {
    private static final ResourceLocation ALTAR = ResourceLocation.parse("cataclysm:altar_of_void");
    private static final ResourceLocation GUARDIAN = ResourceLocation.parse("cataclysm:ender_guardian");

    private NativeAltarSpawn() {}

    /**
     * Precombat setup only. The caller must supply an empty altar cell and a living
     * Survival player in this level. The temporary nearby position satisfies the
     * altar's actual six-block player check; the prior setup position is restored.
     * All Guardian initialization (including the unused mass-destruction flag and
     * home position) belongs to the native altar, not to this helper.
     */
    public static LivingEntity guardian(ServerLevel level, ServerPlayer player, BlockPos altarPos)
            throws ReflectiveOperationException {
        if (!Boolean.getBoolean("survival.bossBalance.testMode"))
            throw new IllegalStateException("Isolated test-mode JVM required");
        if (player.serverLevel() != level || !player.isAlive()
                || player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL)
            throw new IllegalArgumentException("Native altar setup requires a living Survival player in this level");
        if (!level.getBlockState(altarPos).isAir())
            throw new IllegalArgumentException("Native altar setup requires an empty block cell");
        if (!BuiltInRegistries.BLOCK.containsKey(ALTAR))
            throw new IllegalStateException("Installed Cataclysm altar is unavailable");

        AABB search = new AABB(altarPos).inflate(16);
        Set<UUID> before = new HashSet<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, search)) before.add(entity.getUUID());
        Block block = BuiltInRegistries.BLOCK.get(ALTAR);
        BlockState state = block.defaultBlockState();
        if (!level.setBlock(altarPos, state, 3)) throw new IllegalStateException("Could not place native altar");
        BlockEntity altar = level.getBlockEntity(altarPos);
        if (altar == null || !altar.getClass().getName().equals(
                "com.github.L_Ender.cataclysm.blockentities.AltarOfVoid_Block_Entity"))
            throw new IllegalStateException("Native altar block entity was not created");
        Method nativeTick = altar.getClass().getMethod("commonTick", Level.class, BlockPos.class,
                BlockState.class, altar.getClass());

        Vec3 prior = player.position();
        float yaw = player.getYRot(), pitch = player.getXRot();
        try {
            player.teleportTo(level, altarPos.getX() + 2.5, altarPos.getY(), altarPos.getZ() + .5, yaw, pitch);
            nativeTick.invoke(null, level, altarPos, state, altar);
        } finally {
            player.teleportTo(level, prior.x, prior.y, prior.z, yaw, pitch);
        }

        var spawned = level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> !before.contains(entity.getUUID())
                        && GUARDIAN.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));
        if (spawned.size() != 1 || level.getBlockState(altarPos).is(block))
            throw new IllegalStateException("Native altar did not complete exactly one Guardian spawn");
        return spawned.getFirst();
    }

    /**
     * Precombat only: run the actual cursed-pyramid lower2 "remnant" data-marker
     * callback. This places only its marker cell, not the surrounding pyramid.
     * The installed structure callback supplies the asleep necklace state and
     * STRUCTURE initialization; the caller may then use the native necklace
     * interaction. No boss field or attack-state setter is called here.
     */
    public static LivingEntity remnant(ServerLevel level, BlockPos markerPos)
            throws ReflectiveOperationException {
        if (!Boolean.getBoolean("survival.bossBalance.testMode"))
            throw new IllegalStateException("Isolated test-mode JVM required");
        if (!level.getBlockState(markerPos).isAir())
            throw new IllegalArgumentException("Native structure marker requires an empty block cell");
        ResourceLocation type = ResourceLocation.parse("cataclysm:ancient_remnant");
        ResourceLocation template = ResourceLocation.parse("cataclysm:cursed_pyramid_lower2");
        Class<?> pieceClass = Class.forName("com.github.L_Ender.cataclysm.structures.Cursed_Pyramid_Structure$Piece");
        // Installed template marker [44,8,33], with the native Piece origin
        // offset [0,1,0]. Align that marker to the requested arena location.
        BlockPos origin = markerPos.offset(-44, -9, -33);
        StructurePiece piece = (StructurePiece) pieceClass.getConstructor(StructureTemplateManager.class,
                ResourceLocation.class, BlockPos.class, Rotation.class)
                .newInstance(level.getStructureManager(), template, origin, Rotation.NONE);
        Method nativeMarker = pieceClass.getDeclaredMethod("handleDataMarker", String.class, BlockPos.class,
                ServerLevelAccessor.class, RandomSource.class, BoundingBox.class);
        nativeMarker.setAccessible(true);
        AABB search = new AABB(markerPos).inflate(16);
        Set<UUID> before = new HashSet<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, search)) before.add(entity.getUUID());
        if (!level.setBlock(markerPos, Blocks.STRUCTURE_BLOCK.defaultBlockState(), 3))
            throw new IllegalStateException("Could not place native structure marker");
        nativeMarker.invoke(piece, "remnant", markerPos, level, level.getRandom(), piece.getBoundingBox());
        var spawned = level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> !before.contains(entity.getUUID())
                        && type.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));
        if (spawned.size() != 1 || !level.getBlockState(markerPos).isAir())
            throw new IllegalStateException("Native structure marker did not complete exactly one Remnant spawn");
        return spawned.getFirst();
    }
}

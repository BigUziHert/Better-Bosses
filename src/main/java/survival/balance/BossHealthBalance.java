package survival.balance;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/** Fixed boss health profiles and migration of saved bosses. */
public final class BossHealthBalance {
    public static final String DATA_KEY = "survival_boss_balance_health";
    public static final int PROFILE_VERSION = 2;
    public static final ResourceLocation HEALTH_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("survival_boss_balance", "health");
    private static final ResourceLocation LEGACY_TF_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("survival_tweaks", "twilight_boss_health_3x");
    private static final System.Logger LOG = System.getLogger("SurvivalBossBalance");
    private static final Map<Class<?>, Field> NAGA_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> LICH_CLONE_METHODS = new ConcurrentHashMap<>();
    private record KnightAccess(Field total, Method update, Method nearby) {}
    private static final Map<Class<?>, KnightAccess> KNIGHT_ACCESS = new ConcurrentHashMap<>();

    private static final Map<String, Double> DEFAULT_HEALTH = Map.ofEntries(
            Map.entry("minecraft:wither", 900.0),
            Map.entry("minecraft:ender_dragon", 1100.0),
            Map.entry("minecraft:warden", 1300.0),
            Map.entry("twilightforest:naga", 1000.0),
            Map.entry("twilightforest:lich", 650.0),
            Map.entry("twilightforest:minoshroom", 1000.0),
            Map.entry("twilightforest:knight_phantom", 180.0),
            Map.entry("twilightforest:alpha_yeti", 1100.0),
            Map.entry("twilightforest:hydra", 1500.0),
            Map.entry("twilightforest:ur_ghast", 1100.0),
            Map.entry("twilightforest:snow_queen", 1200.0),
            Map.entry("bosses_of_mass_destruction:lich", 1300.0),
            Map.entry("bosses_of_mass_destruction:void_blossom", 1400.0),
            Map.entry("bosses_of_mass_destruction:gauntlet", 1500.0),
            Map.entry("bosses_of_mass_destruction:obsidilith", 1700.0),
            Map.entry("cataclysm:netherite_monstrosity", 1200.0),
            Map.entry("cataclysm:ender_guardian", 850.0),
            Map.entry("cataclysm:the_harbinger", 950.0),
            Map.entry("cataclysm:ancient_remnant", 1000.0),
            Map.entry("cataclysm:the_leviathan", 1100.0),
            Map.entry("cataclysm:scylla", 1100.0),
            Map.entry("cataclysm:maledictus", 1100.0),
            Map.entry("cataclysm:ignis", 1100.0)
    );

    private BossHealthBalance() {}

    public static Map<String, Double> defaultProfiles() {
        return DEFAULT_HEALTH;
    }

    public static String entityId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    public static boolean isManagedBoss(LivingEntity entity) {
        String id = entityId(entity);
        return DEFAULT_HEALTH.containsKey(id) && !isShadowClone(entity, id);
    }

    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity boss) {
            applyInitialProfile(boss);
        }
    }

    /** Vanilla charges from one-third health with 10 HP heals. Keep that charge
     * proportional so a higher-HP Wither does not begin combat half empty. */
    public static void onHeal(LivingHealEvent event) {
        if (!event.isCanceled() && !event.getEntity().level().isClientSide()
                && event.getEntity() instanceof WitherBoss wither
                && wither.getInvulnerableTicks() > 0
                && Math.abs(event.getAmount() - 10.0F) < 0.0001F
                && StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                    frame.getClassName().equals("net.minecraft.world.entity.boss.wither.WitherBoss")
                        && frame.getMethodName().equals("customServerAiStep")))) {
            event.setAmount(event.getAmount() * wither.getMaxHealth() / 300.0F);
        }
    }

    /**
     * Installing the same jar on a client also preserves the Naga's visual segment count.
     * On a dedicated server this handler maintains only the authoritative server threshold.
     * It changes no health and performs a single cached field read only for Naga entities.
     */
    public static void onNagaTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity boss
                && "twilightforest:naga".equals(entityId(boss))) {
            updateNagaThreshold(boss);
        }
    }

    /** The native solo-Knight numbering path leaves Integer.MIN_VALUE as the
     * group size, overflowing the shared health-bar denominator. Wait for the
     * entity tick so a naturally spawned group has finished joining, let the
     * native group numbering run, and repair only the remaining solo sentinel.
     * A repaired solo may discover companions on a later tick. Never shrink an
     * established group total after a teammate dies/unloads. */
    public static void onKnightTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity knight) || knight.level().isClientSide()
                || !knight.isAlive() || !"twilightforest:knight_phantom".equals(entityId(knight))) return;
        try {
            KnightAccess access = KNIGHT_ACCESS.computeIfAbsent(knight.getClass(), type -> {
                try {
                    Field total = type.getDeclaredField("totalKnownKnights");
                    total.setAccessible(true);
                    Method update = type.getDeclaredMethod("updateMyNumber");
                    update.setAccessible(true);
                    return new KnightAccess(total, update, type.getMethod("getNearbyKnights"));
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException("Cannot inspect Knight Phantom group size", error);
                }
            });
            int total = access.total().getInt(knight);
            if (total == 1) {
                List<?> nearby = (List<?>)access.nearby().invoke(knight);
                if (nearby.size() > 1) access.update().invoke(knight);
                return;
            }
            if (total != Integer.MIN_VALUE) return;
            access.update().invoke(knight);
            if (access.total().getInt(knight) == Integer.MIN_VALUE) {
                List<?> nearby = (List<?>)access.nearby().invoke(knight);
                if (nearby.size() == 1 && nearby.getFirst() == knight) access.total().setInt(knight, 1);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot repair Knight Phantom's unknown solo group size", error);
        }
    }

    public static void applyInitialProfile(LivingEntity boss) {
        if (boss.level().isClientSide() || !boss.isAlive()) return;
        String id = entityId(boss);
        Double fallback = DEFAULT_HEALTH.get(id);
        if (fallback == null || isShadowClone(boss, id)) return;
        AttributeInstance health = boss.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;

        double target = BalanceSettings.healthTarget(id, fallback);
        CompoundTag root = boss.getPersistentData();
        boolean hasState = root.contains(DATA_KEY, Tag.TAG_COMPOUND);
        CompoundTag state = root.getCompound(DATA_KEY);

        boolean needsUpdate = !hasState
                || state.getInt("version") != PROFILE_VERSION
                || Math.abs(state.getDouble("target_hp") - target) > 0.001
                || health.hasModifier(LEGACY_TF_MODIFIER)
                || !health.hasModifier(HEALTH_MODIFIER)
                || Math.abs(health.getValue() - target) > 0.05;

        if (needsUpdate) {
            setTargetPreservingRatio(boss, health, target);
            CompoundTag fixedProfile = new CompoundTag();
            fixedProfile.putInt("version", PROFILE_VERSION);
            fixedProfile.putDouble("target_hp", target);
            root.put(DATA_KEY, fixedProfile);
        }
        if ("twilightforest:naga".equals(id)) updateNagaThreshold(boss);
    }

    private static void setTargetPreservingRatio(LivingEntity boss, AttributeInstance health, double target) {
        float oldHealth = boss.getHealth();
        float oldMax = boss.getMaxHealth();
        if (oldMax <= 0 || oldHealth <= 0 || !Double.isFinite(target) || target < 1.0) return;

        // Both modifiers are uniquely owned by this server's earlier/current balance scripts.
        // Other mods' additive or multiplicative max-health adjustments remain intact.
        health.removeModifier(LEGACY_TF_MODIFIER);
        health.removeModifier(HEALTH_MODIFIER);
        double unbalancedMax = health.getValue();
        if (unbalancedMax <= 0 || !Double.isFinite(unbalancedMax)) {
            throw new IllegalStateException("Invalid native maximum health for " + entityId(boss));
        }
        health.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER,
                target / unbalancedMax - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        float newMax = boss.getMaxHealth();
        boss.setHealth(Math.min(newMax, oldHealth * newMax / oldMax));
    }

    private static boolean isShadowClone(LivingEntity boss, String id) {
        if (!"twilightforest:lich".equals(id)) return false;
        try {
            Method method = LICH_CLONE_METHODS.computeIfAbsent(boss.getClass(), type -> {
                try {
                    return type.getMethod("isShadowClone");
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException("Cannot identify Twilight Lich shadow clones", ex);
                }
            });
            return (boolean) method.invoke(boss);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            // Fail closed: a changed Twilight API must not accidentally buff every clone.
            LOG.log(System.Logger.Level.ERROR, "Skipping Lich profile: clone check failed", ex);
            return true;
        }
    }

    private static void updateNagaThreshold(LivingEntity naga) {
        try {
            Field field = NAGA_FIELDS.computeIfAbsent(naga.getClass(), type -> {
                try {
                    Field result = type.getDeclaredField("healthPerSegment");
                    result.setAccessible(true);
                    return result;
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException("Cannot access Naga health-per-segment threshold", ex);
                }
            });
            float correct = naga.getMaxHealth() / 10.0F;
            if (Math.abs(field.getFloat(naga) - correct) > 0.0001F) field.setFloat(naga, correct);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            throw new IllegalStateException("Cannot preserve Naga health-relative phases", ex);
        }
    }
}

package survival.balance.tests;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * Arena prerequisites only. The caller owns staging/NoAI and starts the encounter.
 * No attack method, animation, projectile, player damage or boss damage is invoked.
 */
public final class VisibleOtherSetup {
    private VisibleOtherSetup() {}

    public static void prepare(Mob boss, ServerPlayer player) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString();
        if (!id.startsWith("twilightforest:") && !id.startsWith("bosses_of_mass_destruction:")) return;
        try {
            boss.setInvulnerable(false);
            boss.setPersistenceRequired();
            String note = "Native first-phase AI; arena home/target supplied by the demo.";

            if (id.equals("twilightforest:ur_ghast")) {
                // Give its naturally flying body clearance above the flat test floor.
                boss.setPos(boss.getX(), player.getY() + 8, boss.getZ());
                note = "Native normal fireball phase, spawned eight blocks above the player floor.";
            } else if (id.equals("bosses_of_mass_destruction:lich")) {
                boss.setPos(boss.getX(), player.getY() + 6, boss.getZ());
                note = "Native first-phase Night Lich AI, spawned six blocks above the player floor.";
            }

            if (id.startsWith("twilightforest:")) {
                // Includes the dimension: without a valid home the Naga can discard its target.
                invoke(boss, "setRestrictionPoint", new Class<?>[]{GlobalPos.class},
                    GlobalPos.of(boss.level().dimension(), boss.blockPosition()));
            } else {
                // BaseEntity normally captures this on its first tick; do so after arena placement.
                field(boss, "idlePosition").set(boss, boss.position());
            }

            switch (id) {
                case "twilightforest:lich" -> {
                    invoke(boss, "setShieldStrength", new Class<?>[]{int.class}, 0);
                    invoke(boss, "setMinionsToSummon", new Class<?>[]{int.class}, 0);
                    note = "Twilight Lich starts in phase 3: shields and minion quota zero; native melee AI chooses attacks.";
                }
                case "twilightforest:knight_phantom" -> {
                    invoke(boss, "setNumber", new Class<?>[]{int.class}, 0);
                    note = "One sword Knight Phantom (number 0), with its normal formation/charge AI; not a six-knight encounter.";
                }
                case "twilightforest:snow_queen" -> {
                    // Skip the summon-only waiting stage. The normal AI selects/transitions to DROP.
                    // This does not choose an attack animation or invoke a drop/breath hit.
                    invoke(boss, "setSummonsRemaining", new Class<?>[]{int.class}, 0);
                    note = "Snow Queen starts after the summon quota: zero remaining summons; native AI transitions to DROP and selects attacks.";
                }
                case "bosses_of_mass_destruction:gauntlet" -> {
                    Object wake = field(boss, "nbtHandler").get(boss);
                    if (wake == null || !wake.getClass().getName().endsWith(".GauntletGoalHandler")) {
                        throw new IllegalStateException("Unexpected Gauntlet wake handler");
                    }
                    // Its constructor registers no attack goals. Native saved awake-state loading
                    // enables movement and installs normal goals, without a synthetic damage hit.
                    if (!field(wake, "isAggroed").getBoolean(wake)) {
                        CompoundTag awake = new CompoundTag();
                        awake.putBoolean("isAggroed", true);
                        invoke(wake, "fromTag", new Class<?>[]{CompoundTag.class}, awake);
                    }
                    note = "Gauntlet native awake state enabled via its save-state handler; native AI goals choose all attacks.";
                }
                default -> { }
            }

            double dx = player.getX() - boss.getX();
            double dz = player.getZ() - boss.getZ();
            float yaw = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
            boss.setYRot(yaw);
            boss.yBodyRot = yaw;
            boss.yHeadRot = yaw;
            boss.setXRot(0);
            boss.getPersistentData().putString("visible_demo_setup", note);
            System.out.println("VISIBLE_SETUP " + id + " " + note);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot prepare natural AI for " + id, e);
        }
    }

    private static Field field(Object receiver, String name) throws NoSuchFieldException {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(receiver.getClass().getName() + "." + name);
    }

    private static Object invoke(Object receiver, String name, Class<?>[] types, Object... args)
            throws ReflectiveOperationException {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method.invoke(receiver, args);
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(receiver.getClass().getName() + "." + name);
    }
}

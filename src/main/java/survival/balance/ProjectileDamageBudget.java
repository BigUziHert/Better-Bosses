package survival.balance;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.lang.reflect.Field;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Per-shot, per-victim budgets in plain-diamond Hard reference hearts.
 * Limits are applied before armor. Actual armor, enchantments, resistance,
 * absorption, shielding, and native damage cooldowns remain in control.
 * Only an accepted damage sequence reaching Post spends the budget. */
public final class ProjectileDamageBudget {
    private ProjectileDamageBudget() {}
    private static final Map<Entity, Map<UUID, Double>> SPENT = new WeakHashMap<>();
    private static final Map<DamageContainer, Pending> PENDING = new WeakHashMap<>();
    private static final class ExplosionScope {
        final Projectile projectile;
        DamageSource source;
        ExplosionScope(Projectile projectile) { this.projectile = projectile; }
    }
    private static final ThreadLocal<ArrayDeque<ExplosionScope>> EXPLOSIONS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Accepted>> AWAITING_POST = ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile Field explosionDamageSource;
    private record Pending(Entity projectile, Player victim, DamageSource source, float hardRaw, float localRaw) {}
    private record Accepted(Pending pending, double referenceHearts, long tick) {}

    /** Scope only the native ownerless Ur-Ghast blast. Does not rewrite its
     * damage source, griefing, ownership, kill credit, or other mod events. */
    public static <T> T duringExplosion(Projectile projectile, Supplier<T> operation) {
        ArrayDeque<ExplosionScope> stack = EXPLOSIONS.get();
        stack.addLast(new ExplosionScope(projectile));
        try { return operation.get(); }
        finally { stack.removeLast(); if (stack.isEmpty()) EXPLOSIONS.remove(); }
    }
    public static void onExplosionStart(ExplosionEvent.Start event) {
        ExplosionScope scope = EXPLOSIONS.get().peekLast();
        if (scope != null && scope.source == null) {
            try {
                Field field = explosionDamageSource;
                if (field == null) {
                    field = Explosion.class.getDeclaredField("damageSource");
                    field.setAccessible(true);
                    explosionDamageSource = field;
                }
                scope.source = (DamageSource)field.get(event.getExplosion());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot identify the native Ur-Ghast explosion damage source", error);
            }
        }
    }
    public static Projectile explosionProjectile(DamageSource source) {
        ExplosionScope scope = EXPLOSIONS.get().peekLast();
        return scope != null && scope.source == source ? scope.projectile : null;
    }

    static float limit(LivingIncomingDamageEvent event, Entity projectile, String bossId, float hardRaw) {
        String family = family(projectile, bossId);
        if (family.isEmpty()) return hardRaw;
        Player victim = (Player)event.getEntity();
        double available = Math.max(0, BalanceSettings.damageTarget(family+".combined",8.0)-used(projectile,victim));
        DamageSource source = event.getSource();
        double allowed = BossDamageBalance.rawForHearts(available,
            source.is(DamageTypeTags.BYPASSES_ARMOR),source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS),source.is(DamageTypeTags.DAMAGES_HELMET));
        return (float)Math.min(hardRaw, allowed);
    }

    static void observeIncoming(LivingIncomingDamageEvent event, Entity projectile, String bossId, float hardRaw) {
        if (family(projectile,bossId).isEmpty() || hardRaw<=0 || event.getAmount()<=0) return;
        Player victim = (Player)event.getEntity();
        long tick = victim.level().getGameTime();
        // A mod that short-circuits actuallyHurt after Pre must not retain an
        // uncommitted sequence indefinitely. Normal native sequences match Post.
        AWAITING_POST.get().removeIf(item -> item.tick()!=tick);
        synchronized (PENDING) {
            PENDING.put(event.getContainer(),new Pending(projectile,victim,event.getSource(),hardRaw,event.getAmount()));
        }
    }

    public static void beforeDamage(LivingDamageEvent.Pre event) {
        Pending pending;
        synchronized (PENDING) { pending = PENDING.remove(event.getContainer()); }
        if (pending == null) return;
        DamageContainer container = event.getContainer();
        // Undo only defenses. Do not add back blocked damage or the native
        // last-hit cooldown reduction: those portions never reached the victim.
        double acceptedRaw = container.getNewDamage()
            + container.getReduction(DamageContainer.Reduction.ARMOR)
            + container.getReduction(DamageContainer.Reduction.ENCHANTMENTS)
            + container.getReduction(DamageContainer.Reduction.MOB_EFFECTS)
            + container.getReduction(DamageContainer.Reduction.INNATE_RESISTANCE);
        if (acceptedRaw <= 0 || !Double.isFinite(acceptedRaw)) return;
        double acceptedHard = pending.hardRaw()*Math.min(1,acceptedRaw/pending.localRaw());
        double cost = BossDamageBalance.referenceHearts(acceptedHard,
            pending.source().is(DamageTypeTags.BYPASSES_ARMOR),pending.source().is(DamageTypeTags.BYPASSES_ENCHANTMENTS),false,20,8);
        AWAITING_POST.get().addLast(new Accepted(pending,cost,event.getEntity().level().getGameTime()));
    }

    public static void afterDamage(LivingDamageEvent.Post event) {
        ArrayDeque<Accepted> awaiting = AWAITING_POST.get();
        for (Iterator<Accepted> it = awaiting.descendingIterator(); it.hasNext();) {
            Accepted accepted = it.next();
            Pending pending = accepted.pending();
            if (pending.victim()!=event.getEntity() || pending.source()!=event.getSource()) continue;
            it.remove();
            synchronized (SPENT) {
                SPENT.computeIfAbsent(pending.projectile(),unused -> new HashMap<>())
                    .merge(pending.victim().getUUID(),accepted.referenceHearts(),Double::sum);
            }
            break;
        }
        if (awaiting.isEmpty()) AWAITING_POST.remove();
    }

    /** Read-only test/diagnostic surface. Discarding a projectile does not erase
     * its ledger while callers still hold the object; weak keys permit cleanup. */
    public static double used(Entity projectile, Player victim) {
        synchronized (SPENT) {
            Map<UUID, Double> victims = SPENT.get(projectile);
            return victims==null ? 0: victims.getOrDefault(victim.getUUID(),0.0);
        }
    }
    private static String family(Entity projectile,String bossId) {
        if (projectile == null) return "";
        String type = projectile.getClass().getName();
        if (bossId.equals("twilightforest:ur_ghast") && type.equals("twilightforest.entity.projectile.UrGhastFireball")) return "ur_ghast";
        if (bossId.equals("cataclysm:the_harbinger") && (type.equals("com.github.L_Ender.cataclysm.entity.projectile.Wither_Missile_Entity")
                || type.equals("com.github.L_Ender.cataclysm.entity.projectile.Wither_Homing_Missile_Entity"))) return "harbinger";
        return "";
    }
}

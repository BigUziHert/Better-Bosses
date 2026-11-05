package survival.balance;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;

@Mod(SurvivalBossBalance.MOD_ID)
public final class SurvivalBossBalance {
    public static final String MOD_ID = "survival_boss_balance";

    public SurvivalBossBalance() {
        BalanceSettings.load(FMLPaths.CONFIGDIR.get().resolve("survival-boss-balance.json"));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BossHealthBalance::onJoin);
        NeoForge.EVENT_BUS.addListener(BossHealthBalance::onNagaTick);
        NeoForge.EVENT_BUS.addListener(BossHealthBalance::onKnightTick);
        NeoForge.EVENT_BUS.addListener(BossHealthBalance::onHeal);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BossDamageBalance::apply);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ProjectileDamageBudget::beforeDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ProjectileDamageBudget::afterDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ProjectileDamageBudget::onExplosionStart);
        BalanceSettings.LOG.info("Survival Boss Balance 1.2.0 loaded: 23 fixed-health bosses, plain-diamond attack reference, per-projectile combined budgets");
    }
}

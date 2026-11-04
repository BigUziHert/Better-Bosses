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
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BossDamageBalance::apply);
        BalanceSettings.LOG.info("Survival Boss Balance 1.0.2 loaded: fixed boss health, attack tuning and Naga phase correction");
    }
}

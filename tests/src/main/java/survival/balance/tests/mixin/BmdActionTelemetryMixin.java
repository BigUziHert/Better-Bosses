package survival.balance.tests.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import survival.balance.tests.EncounterTelemetry;

/** TEST ONLY: enter the actual native action without changing parameters, cancellation or result. */
@Pseudo
@Mixin(targets={
    "com.cerbon.bosses_of_mass_destruction.entity.custom.lich.CometAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.lich.CometRageAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.lich.VolleyAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.lich.VolleyRageAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.lich.MinionAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.lich.MinionRageAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.lich.TeleportAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.gauntlet.PunchAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.gauntlet.SwirlPunchAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.gauntlet.LaserAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.gauntlet.BlindnessAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.obsidilith.AnvilAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.obsidilith.BurstAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.obsidilith.PillarAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.obsidilith.SpikeAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.obsidilith.WaveAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.void_blossom.BladeAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.void_blossom.BlossomAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.void_blossom.SpikeAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.void_blossom.SpikeWaveAction",
    "com.cerbon.bosses_of_mass_destruction.entity.custom.void_blossom.SporeAction"
},remap=false)
public abstract class BmdActionTelemetryMixin {
    @Inject(method="perform()I",at=@At("HEAD"),require=1,remap=false)
    private void bossBalanceTest$observeAction(CallbackInfoReturnable<Integer> callback){EncounterTelemetry.observeBmdAction(this);}
}

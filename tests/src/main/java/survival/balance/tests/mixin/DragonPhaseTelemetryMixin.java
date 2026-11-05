package survival.balance.tests.mixin;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import survival.balance.tests.EncounterTelemetry;

/** TEST ONLY: observes each completed native phase assignment, including multiple changes in a tick. */
@Mixin(value=EnderDragonPhaseManager.class,remap=false)
public abstract class DragonPhaseTelemetryMixin {
    @Shadow @Final private EnderDragon dragon;
    @Shadow private DragonPhaseInstance currentPhase;

    @Inject(method="setPhase",at=@At(value="FIELD",
        target="Lnet/minecraft/world/entity/boss/enderdragon/phases/EnderDragonPhaseManager;currentPhase:Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;",
        opcode=Opcodes.PUTFIELD,shift=At.Shift.AFTER),require=1,remap=false)
    private void bossBalanceTest$observeAssignedPhase(EnderDragonPhase<?> requested,CallbackInfo callback){
        EncounterTelemetry.observeDragonPhaseAssignment(dragon,currentPhase.getPhase());
    }
}

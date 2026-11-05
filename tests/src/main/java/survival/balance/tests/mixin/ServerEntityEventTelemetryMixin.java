package survival.balance.tests.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import survival.balance.tests.EncounterTelemetry;

/** TEST ONLY: observes native event publication; never sends or replaces an event. */
@Mixin(value=ServerLevel.class,remap=false)
public abstract class ServerEntityEventTelemetryMixin {
    @Inject(method="broadcastEntityEvent",at=@At("HEAD"),require=1,remap=false)
    private void bossBalanceTest$observeEvent(Entity entity,byte event,CallbackInfo callback){EncounterTelemetry.observeEntityEvent(entity,event);}
}

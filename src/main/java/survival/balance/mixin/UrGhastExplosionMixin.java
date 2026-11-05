package survival.balance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import survival.balance.ProjectileDamageBudget;

/** Twilight emits these blasts with a null source. Preserve the exact active
 * projectile in a scoped context instead of guessing by distance or time. */
@Pseudo
@Mixin(targets = "twilightforest.entity.projectile.UrGhastFireball", remap = false)
abstract class UrGhastExplosionMixin {
    @WrapOperation(method = {"onHitEntity", "onHitBlock"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"), require = 2)
    private Explosion survivalBalance$withProjectile(Level level, Entity source, double x, double y, double z,
            float radius, boolean fire, Level.ExplosionInteraction interaction, Operation<Explosion> original) {
        return ProjectileDamageBudget.duringExplosion((Projectile)(Object)this,
            () -> original.call(level,source,x,y,z,radius,fire,interaction));
    }
}

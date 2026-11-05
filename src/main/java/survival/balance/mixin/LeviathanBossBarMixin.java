package survival.balance.mixin;

import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Preserve Cataclysm's segmented health display while its native phase change
 * is pending. The first segment can be exhausted before meltdown frame 90;
 * clamp only the outgoing bar value, before ServerBossEvent broadcasts it. */
@Pseudo
@Mixin(targets = "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Leviathan.The_Leviathan_Entity", remap = false)
abstract class LeviathanBossBarMixin {
    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
        target = "Lcom/github/L_Ender/cataclysm/entity/etc/CMBossInfoServer;setProgress(F)V"),
        index = 0, require = 2, allow = 2)
    private float survivalBalance$normalizeBossBar(float progress) {
        return Mth.clamp(progress, 0.0F, 1.0F);
    }
}

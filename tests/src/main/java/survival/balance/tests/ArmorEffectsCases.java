package survival.balance.tests;

import java.nio.file.Path;
import net.minecraft.server.level.ServerLevel;

/** Separate entrypoint so conditioned set abilities cannot contaminate the baseline matrix. */
final class ArmorEffectsCases {
    static void run(ServerLevel level,Path destination)throws Exception {
        ArmorMatrixCases.runEffects(level,destination);
    }
}

package survival.balance.clienttest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLLoader;

/** TEST CLIENT ONLY. FlightLib gives diamond/gold chestplates free jetpacks in
 * development, independently of its production-server behavior. Remove only
 * that proven development caster after all mod constructors have completed.
 * Real equipment/accessory flight casters and every server setting remain intact. */
public final class ProductionFlightSources {
    private ProductionFlightSources() {}

    /** Call from FMLClientSetupEvent.enqueueWork, before joining the test server. */
    public static void restoreProductionSources() {
        if (!Boolean.getBoolean("survival.bossBalance.clientPlaytest") || FMLLoader.isProduction()) return;
        try {
            Class<?> api;
            try { api = Class.forName("com.possible_triangle.flightlib.init.FlightApiImpl"); }
            catch (ClassNotFoundException absent) { return; }
            Field field = api.getDeclaredField("CASTERS");
            field.setAccessible(true);
            List<?> casters = (List<?>)field.get(null);
            Method get = Class.forName("com.possible_triangle.flightlib.api.ISource$Caster").getMethod("get",Object.class);
            Method invoke = Class.forName("kotlin.jvm.functions.Function0").getMethod("invoke");
            List<Object> development = new ArrayList<>();
            for (Object caster : casters) {
                if (!caster.getClass().getName().startsWith("com.possible_triangle.flightlib.init.CommonSources$$Lambda")) continue;
                Object suppliers = get.invoke(caster,new ItemStack(Items.DIAMOND_CHESTPLATE));
                if (!(suppliers instanceof List<?> list)) continue;
                for (Object supplier : list) {
                    Object jetpack = invoke.invoke(supplier);
                    if (jetpack != null && jetpack.getClass().getName().equals("com.possible_triangle.flightlib.init.DevJetpack")) {
                        development.add(caster);
                        break;
                    }
                }
            }
            if (development.size() != 1) throw new IllegalStateException("Expected exactly one FlightLib development-only chestplate caster; found " + development.size());
            casters.removeAll(development);
            System.getLogger("BossFightClientPilot").log(System.Logger.Level.INFO,
                "Removed FlightLib's one development-only diamond/gold chestplate jetpack caster; retained " + casters.size() + " production equipment/accessory casters");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot restore production FlightLib sources in isolated test client",error);
        }
    }
}

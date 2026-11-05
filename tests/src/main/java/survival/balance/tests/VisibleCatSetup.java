package survival.balance.tests;

import java.lang.reflect.Method;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Test-only staging prerequisites for naturally selected Cataclysm attacks.
 * The host creates the arena, equips the real player and enables AI on "go".
 * This utility never invokes an attack/damage routine, forces an attacking
 * animation, advances attack ticks, modifies combat attributes or damages anyone.
 * Awakening is explicitly staged; all attack selection after "go" is native AI.
 */
public final class VisibleCatSetup {
    private VisibleCatSetup() {}

    public static void prepare(Mob boss,ServerPlayer player) {
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString();
        if(!id.startsWith("cataclysm:"))return;
        double distance;
        String prerequisite;
        try {
            switch(id) {
                case "cataclysm:netherite_monstrosity":
                    set(boss,"setIsAwaken",boolean.class,true);
                    // States 1/2 are sleeping/waking. State 0 is idle, not an attack.
                    clearSleepState(boss,1,2);
                    distance=8;prerequisite="awake flag true; sleeping states cleared to idle";break;
                case "cataclysm:the_harbinger":
                    set(boss,"setIsAct",boolean.class,true);
                    distance=10;prerequisite="native activation flag true";break;
                case "cataclysm:ancient_remnant":
                    set(boss,"setNecklace",boolean.class,true);
                    clearSleepState(boss,1,2);
                    distance=8;prerequisite="necklace present; sleeping states cleared to idle";break;
                case "cataclysm:scylla":
                    set(boss,"setAct",boolean.class,true);
                    clearSleepState(boss,23,24);
                    distance=6;prerequisite="native activation flag true; sleeping states cleared to idle";break;
                case "cataclysm:the_leviathan":
                    distance=10;prerequisite="no awakening gate; host must provide water arena";break;
                case "cataclysm:ender_guardian":
                case "cataclysm:maledictus":
                case "cataclysm:ignis":
                    distance=6;prerequisite="no additional native awakening gate";break;
                default:return;
            }
            // A target during the paused stage could let a mod's own tick logic
            // select attacks outside vanilla NoAI goal processing. Host sets it on go.
            boss.setTarget(null);
            boss.getNavigation().stop();
            boss.setPersistenceRequired();
            boss.moveTo(player.getX(),player.getY(),player.getZ()+distance,180,0);
            boss.yBodyRot=180;boss.yHeadRot=180;
            boss.setDeltaMovement(Vec3.ZERO);
            System.out.println("VISIBLE_CAT_SETUP "+id+" distance="+distance+"; "+prerequisite
                +"; native AI remains paused until host go");
        } catch(ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot prepare native awakening state for "+id,error);
        }
    }

    private static void clearSleepState(Mob boss,int first,int second) throws ReflectiveOperationException {
        int state=((Number)boss.getClass().getMethod("getAttackState").invoke(boss)).intValue();
        if(state==first || state==second)set(boss,"setAttackState",int.class,0);
    }
    private static void set(Mob boss,String name,Class<?> type,Object value) throws ReflectiveOperationException {
        Method method=boss.getClass().getMethod(name,type);
        method.invoke(boss,value);
    }
}

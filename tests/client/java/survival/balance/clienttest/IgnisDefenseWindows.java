package survival.balance.clienttest;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;

/** Read-only installed Cataclysm 3.33 physical-hit timings; never changes an animation.
 * Ignis_Entity.aiStep supplies the sword/body/ground-wave frames below. Native
 * AreaAttack, BodyCheckAttack, Poke, ShieldSmashDamage and UltimateAttack all use
 * mobAttack; many disable a shield AFTER its ordinary block resolves.
 * Phase_Transition's indirect magic, delayed projectiles and Flame_Strike fields
 * are deliberately not classified as ordinary shieldable animation hits.
 */
final class IgnisDefenseWindows {
    record Window(String animation,int frame,int hitFrame) {
        /** Ordinary movement hint for forward lunges and their follow-up lanes. */
        boolean dash(){
            return animation.startsWith("POKE_ATTACK")||animation.startsWith("BODY_CHECK_ATTACK")
                    ||animation.equals("SWING_UPPERCUT")||animation.equals("COMBO1")||animation.equals("COMBO2")
                    ||animation.equals("HORIZONTAL_SMALL_SWING_ATTACK")
                    ||animation.equals("HORIZONTAL_SMALL_SWING_ALT_ATTACK2")||animation.equals("SPIN_ATTACK");
        }
    }
    private record Attack(String name,int[] frames) {}
    private static final Map<String,int[]> HITS=new LinkedHashMap<>();
    private static final Map<Object,Attack> ANIMATIONS=new IdentityHashMap<>();
    private static Class<?> observedClass;
    static {
        HITS.put("SWING_ATTACK",new int[]{24});
        HITS.put("HORIZONTAL_SWING_ATTACK",new int[]{31});
        HITS.put("SWING_ATTACK_SOUL",new int[]{18});
        HITS.put("SWING_ATTACK_BERSERK",new int[]{17});
        HITS.put("HORIZONTAL_SWING_ATTACK_SOUL",new int[]{27});
        HITS.put("HORIZONTAL_SMALL_SWING_ATTACK",new int[]{19});
        HITS.put("HORIZONTAL_SMALL_SWING_ALT_ATTACK2",new int[]{13});
        HITS.put("SPIN_ATTACK",new int[]{14});
        HITS.put("POKE_ATTACK",new int[]{39});
        HITS.put("POKE_ATTACK2",new int[]{34});
        HITS.put("POKE_ATTACK3",new int[]{29});
        for(int variant=1;variant<=4;variant++){
            HITS.put("BODY_CHECK_ATTACK"+variant,new int[]{25});
            HITS.put("BODY_CHECK_ATTACK_SOUL"+variant,new int[]{21});
        }
        HITS.put("SWING_UPPERCUT",new int[]{32});
        // Native positionRider: (tick-10)%4==0, until release at46; the tick46
        // callback continues after stopRiding. Existing passenger priorities apply.
        HITS.put("POKED_ATTACK",new int[]{2,6,10,14,18,22,26,30,34,38,42,46});
        HITS.put("SHIELD_SMASH_ATTACK",new int[]{34,37,40});
        // Air-smash landing time is native/variable. Its SMASH follow-up begins
        // a new animation; do not invent a fixed impact frame in SMASH_IN_AIR.
        HITS.put("SMASH",new int[]{5,8,11,14});
        HITS.put("REINFORCED_SMASH_IN_AIR",new int[]{23,53});
        HITS.put("REINFORCED_SMASH_IN_AIR_SOUL",new int[]{23,53});
        HITS.put("REINFORCED_SMASH",new int[]{5,7,9,11,13,15,17,19,46,47,48,49,50,51,52,53,54,55,56,57});
        HITS.put("REINFORCED_SMASH_SOUL",new int[]{5,7,9,11,13,15,17,19,21,23,25,46,48,49,50,51,52,53,54,55});
        HITS.put("STRIKE",new int[]{31,36,38,40,42,44,46,48});
        HITS.put("SHIELD_BREAK_STRIKE",new int[]{44,49,51,53,55,57,59,61});
        HITS.put("SWING_UPPERSLASH",new int[]{24,26,28,30});
        HITS.put("EARTH_SHUDDERS_ATTACK",new int[]{32,73,76,79,82,85,117,120,123,126,129,132,135});
        HITS.put("ULTIMATE_ATTACK",new int[]{74});
        HITS.put("COMBO1",new int[]{19,38,61,76});
        HITS.put("COMBO2",new int[]{32,59,74,108,110,112,114,116});
        // ShieldExplode is an explosion rather than one of the physical methods
        // above (BREAK_THE_SHIELD25 / conditional PHASE_3 58). Neither phase's
        // magic pulses nor its status-only stun are labeled as melee shield hits.
    }
    static Window current(LivingEntity boss){
        try{
            if(observedClass!=boss.getClass()){
                observedClass=boss.getClass();ANIMATIONS.clear();
                for(var entry:HITS.entrySet())try{
                    Field field=observedClass.getField(entry.getKey());
                    ANIMATIONS.put(field.get(null),new Attack(entry.getKey(),entry.getValue()));
                }catch(NoSuchFieldException unavailable){}
            }
            Object animation=observedClass.getMethod("getAnimation").invoke(boss);
            Attack attack=ANIMATIONS.get(animation);if(attack==null)return null;
            int frame=((Number)observedClass.getMethod("getAnimationTick").invoke(boss)).intValue();
            for(int hit:attack.frames)if(frame>=Math.max(0,hit-24)&&frame<=hit+8)return new Window(attack.name,frame,hit);
        }catch(ReflectiveOperationException unavailable){}
        return null;
    }
}

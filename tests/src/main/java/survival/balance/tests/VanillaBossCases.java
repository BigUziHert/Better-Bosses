package survival.balance.tests;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.ai.behavior.warden.Emerging;
import net.minecraft.world.entity.ai.behavior.warden.Sniffing;
import net.minecraft.world.entity.ai.behavior.warden.TryToSniff;
import net.minecraft.world.entity.ai.behavior.warden.Roar;
import net.minecraft.world.entity.ai.behavior.warden.Digging;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonSittingFlamingPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Native methods from the installed Minecraft build, with real Player.hurt. */
final class VanillaBossCases {
    static final Map<String,Double> ATTACKS = new LinkedHashMap<>();
    static {
        ATTACKS.put("wither_skull_black",1.5);
        ATTACKS.put("wither_skull_blue",1.5);
        ATTACKS.put("wither_splash_black",1.539);
        ATTACKS.put("wither_splash_blue",1.539);
        ATTACKS.put("wither_spawn_explosion",5.0);
        ATTACKS.put("dragon_head",3.5);
        ATTACKS.put("dragon_wing",1.25);
        ATTACKS.put("dragon_perched_breath",1.25);
        ATTACKS.put("dragon_fireball_cloud",2.0);
        ATTACKS.put("warden_melee",4.5);
        ATTACKS.put("warden_sonic_boom",5.5);
    }
    static String bossId(String attack) {
        return "minecraft:"+(attack.startsWith("dragon_") ? "ender_dragon":attack.startsWith("wither_")?"wither":"warden");
    }

    static void run(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        for(var entry:ATTACKS.entrySet()) {
            LivingEntity boss=null;
            try {
                BossBalanceTests.resetActualPlayer(victim);
                boss=BossBalanceTests.damageBoss(level,bossId(entry.getKey()));
                profile(boss);
                double before=victim.getHealth();
                perform(entry.getKey(),level,victim,boss);
                BossBalanceTests.observedAttack("vanilla_"+entry.getKey(),bossId(entry.getKey()),
                    "Native Minecraft attack method; "+entry.getKey(),victim,before,entry.getValue());
            } catch(Throwable error) {BossBalanceTests.error("vanilla_"+entry.getKey(),bossId(entry.getKey()),error);}
            finally {if(boss!=null)boss.discard();}
        }
        try {
            BossBalanceTests.resetActualPlayer(victim);
            float before=victim.getHealth();
            MobEffects.WITHER.value().applyEffectTick(victim,1);
            BossBalanceTests.observedAttack("vanilla_wither_II_native_tick","minecraft:wither","Native WitherMobEffect.applyEffectTick",victim,before,.18);
            BossBalanceTests.result("wither_II_native_interval20","minecraft:wither",1,MobEffects.WITHER.value().shouldApplyEffectTickThisTick(20,1)?1:0);
            BossBalanceTests.result("wither_II_not_interval19","minecraft:wither",0,MobEffects.WITHER.value().shouldApplyEffectTickThisTick(19,1)?1:0);
        } catch(Throwable error) {BossBalanceTests.error("wither_status","minecraft:wither",error);}
        phases(level,victim);
        wardenLifecycle(level,victim);
    }

    /** Matrix caller supplies equipment and reset health. Does not alter either. */
    static void perform(String attack,ServerLevel level,BossBalanceTests.TestPlayer victim,LivingEntity boss) throws Exception {
        if(attack.startsWith("wither_skull_") || attack.startsWith("wither_splash_")) {
            WitherSkull skull=new WitherSkull(level,boss,new Vec3(0,0,1));
            skull.setDangerous(attack.endsWith("blue"));
            skull.moveTo(victim.position());
            try {
                if(attack.startsWith("wither_skull_")) invoke(skull,"onHitEntity",new Class<?>[]{EntityHitResult.class},new EntityHitResult(victim));
                else invoke(skull,"onHit",new Class<?>[]{HitResult.class},new BlockHitResult(victim.position(),Direction.UP,victim.blockPosition(),false));
            } finally {skull.discard();}
        } else if(attack.equals("wither_spawn_explosion")) {
            WitherBoss wither=(WitherBoss)boss;
            // Native explosion originates at the Wither's eye. Put that origin at the victim's feet.
            wither.moveTo(victim.getX(),victim.getY()-wither.getEyeHeight(),victim.getZ(),0,0);
            wither.setInvulnerableTicks(1);
            invoke(wither,"customServerAiStep",new Class<?>[0]);
        } else if(attack.equals("dragon_head")) {
            invoke(boss,"hurt",new Class<?>[]{List.class},List.of(victim));
        } else if(attack.equals("dragon_wing")) {
            EnderDragon dragon=(EnderDragon)boss;
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            victim.setLastHurtByMob(null);
            // setLastHurtByMob also updates the native timestamp even for null.
            // Advance past the wing collision's two-tick repeat-hit guard.
            victim.tickCount+=100;
            invoke(boss,"knockBack",new Class<?>[]{ServerLevel.class,List.class},level,List.of(victim));
        } else if(attack.equals("dragon_perched_breath")) {
            EnderDragon dragon=(EnderDragon)boss;
            dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_FLAMING);
            DragonSittingFlamingPhase phase=dragon.getPhaseManager().getPhase(EnderDragonPhase.SITTING_FLAMING);
            for(int i=0;i<10;i++)phase.doServerTick();
            AreaEffectCloud cloud=(AreaEffectCloud)field(phase,"flame");
            try {hitCloud(cloud,victim);} finally {phase.end();}
        } else if(attack.equals("dragon_fireball_cloud")) {
            DragonFireball ball=new DragonFireball(level,boss,new Vec3(0,0,1));
            ball.moveTo(victim.position());
            List<AreaEffectCloud> before=level.getEntitiesOfClass(AreaEffectCloud.class,victim.getBoundingBox().inflate(12));
            invoke(ball,"onHit",new Class<?>[]{HitResult.class},new BlockHitResult(victim.position(),Direction.UP,victim.blockPosition(),false));
            List<AreaEffectCloud> created=level.getEntitiesOfClass(AreaEffectCloud.class,victim.getBoundingBox().inflate(12)).stream().filter(c->!before.contains(c)).toList();
            try {
                if(created.size()!=1)throw new IllegalStateException("Expected one native fireball cloud, got "+created.size());
                // Prove cloud classification remains independent of the owner's current phase.
                ((EnderDragon)boss).getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
                hitCloud(created.get(0),victim);
            } finally {for(var cloud:created)cloud.discard();ball.discard();}
        } else if(attack.equals("warden_melee")) {
            ((Mob)boss).doHurtTarget(victim);
        } else if(attack.equals("warden_sonic_boom")) {
            Warden warden=(Warden)boss;
            warden.moveTo(victim.getX(),victim.getY(),victim.getZ()+8,180,0);
            warden.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,victim);
            warden.getBrain().eraseMemory(MemoryModuleType.SONIC_BOOM_SOUND_DELAY);
            warden.getBrain().eraseMemory(MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN);
            invoke(new SonicBoom(),"tick",new Class<?>[]{ServerLevel.class,Warden.class,long.class},level,warden,level.getGameTime());
        } else throw new IllegalArgumentException(attack);
    }

    private static void hitCloud(AreaEffectCloud cloud,BossBalanceTests.TestPlayer victim) {
        cloud.moveTo(victim.position());
        // Keep its native effect, owner, wait time, and application path. Tick only this
        // cloud until its first native application; no victim ticks or second hit.
        float before=victim.getHealth();
        for(int i=0;i<25 && victim.getHealth()==before;i++){cloud.tickCount++;cloud.tick();}
    }

    private static void phases(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        WitherBoss wither=null;EnderDragon dragon=null;Warden warden=null;
        try {
            wither=(WitherBoss)BossBalanceTests.damageBoss(level,"minecraft:wither");profile(wither);
            wither.moveTo(40,220,40,0,0);
            wither.makeInvulnerable();
            BossBalanceTests.result("wither_charge_start_third_hp","minecraft:wither",300,wither.getHealth());
            for(int tick=1;tick<=220;tick++) {wither.tickCount=tick;invoke(wither,"customServerAiStep",new Class<?>[0]);}
            BossBalanceTests.result("wither_charge_finished_full_hp","minecraft:wither",900,wither.getHealth());
            BossBalanceTests.result("wither_charge_finished_invulnerability","minecraft:wither",0,wither.getInvulnerableTicks());
            wither.setHealth(451);BossBalanceTests.result("wither_unarmored_phase","minecraft:wither",0,wither.isPowered()?1:0);
            wither.setHealth(450);BossBalanceTests.result("wither_powered_phase_at_half","minecraft:wither",1,wither.isPowered()?1:0);
            wither.setInvulnerable(false);
            Arrow arrow=EntityType.ARROW.create(level);arrow.setOwner(victim);
            wither.hurt(level.damageSources().arrow(arrow,victim),10);
            BossBalanceTests.result("wither_powered_arrow_immunity","minecraft:wither",450,wither.getHealth());
            wither.setHealth(900);wither.invulnerableTime=0;
            wither.hurt(level.damageSources().arrow(arrow,victim),10);
            BossBalanceTests.result("wither_unpowered_arrow_vulnerable","minecraft:wither",1,wither.getHealth()<900?1:0);arrow.discard();
            wither.setHealth(225);roundTrip(wither,level,"wither_powered_saved",225,900);
            dragon=(EnderDragon)BossBalanceTests.damageBoss(level,"minecraft:ender_dragon");profile(dragon);
            for(int i=0;i<EnderDragonPhase.getCount();i++) {
                dragon.getPhaseManager().setPhase(EnderDragonPhase.getById(i));
                BossBalanceTests.result("dragon_phase_"+i+"_fixed_hp","minecraft:ender_dragon",1100,dragon.getMaxHealth());
            }
            dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_FLAMING);
            dragon.setHealth(550);roundTrip(dragon,level,"dragon_perched_saved",550,1100);
            DragonSittingFlamingPhase flame=dragon.getPhaseManager().getPhase(EnderDragonPhase.SITTING_FLAMING);
            flame.resetFlameCount();flame.begin();
            for(int i=0;i<200;i++)flame.doServerTick();
            BossBalanceTests.result("dragon_breath_returns_to_scan","minecraft:ender_dragon",EnderDragonPhase.SITTING_SCANNING.getId(),dragon.getPhaseManager().getCurrentPhase().getPhase().getId());
            for(int cycle=0;cycle<3;cycle++){dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_FLAMING);for(int i=0;i<200;i++)flame.doServerTick();}
            BossBalanceTests.result("dragon_fourth_breath_takes_off","minecraft:ender_dragon",EnderDragonPhase.TAKEOFF.getId(),dragon.getPhaseManager().getCurrentPhase().getPhase().getId());
            dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
            dragon.setInvulnerable(false);dragon.setHealth(1100);dragon.invulnerableTime=0;
            dragon.hurt(dragon.head,level.damageSources().playerAttack(victim),275);
            BossBalanceTests.result("dragon_perched_quarter_hp_not_exceeded","minecraft:ender_dragon",EnderDragonPhase.SITTING_SCANNING.getId(),dragon.getPhaseManager().getCurrentPhase().getPhase().getId());
            dragon.invulnerableTime=0;dragon.hurt(dragon.head,level.damageSources().playerAttack(victim),1);
            BossBalanceTests.result("dragon_perched_above_quarter_hp_takes_off","minecraft:ender_dragon",EnderDragonPhase.TAKEOFF.getId(),dragon.getPhaseManager().getCurrentPhase().getPhase().getId());
            EndCrystal crystal=EntityType.END_CRYSTAL.create(level);dragon.nearestCrystal=crystal;
            dragon.setHealth(550);dragon.tickCount=10;invoke(dragon,"checkCrystals",new Class<?>[0]);
            BossBalanceTests.result("dragon_crystal_native_heal_one_hp","minecraft:ender_dragon",551,dragon.getHealth());crystal.discard();
            warden=(Warden)BossBalanceTests.damageBoss(level,"minecraft:warden");profile(warden);
            for(Pose pose:List.of(Pose.EMERGING,Pose.ROARING,Pose.SNIFFING,Pose.DIGGING,Pose.STANDING)) {
                warden.setPose(pose);BossBalanceTests.result("warden_pose_"+pose.name()+"_fixed_hp","minecraft:warden",1300,warden.getMaxHealth());
            }
            warden.setHealth(650);roundTrip(warden,level,"warden_saved",650,1300);
        } catch(Throwable error) {BossBalanceTests.error("vanilla_phases","minecraft",error);}
        finally {if(wither!=null)wither.discard();if(dragon!=null)dragon.discard();if(warden!=null)warden.discard();}
    }
    private static void roundTrip(LivingEntity boss,ServerLevel level,String name,float hp,float max) throws Exception {
        CompoundTag saved=new CompoundTag();boss.save(saved);
        LivingEntity loaded=(LivingEntity)EntityType.loadEntityRecursive(saved,level,e->e);profile(loaded);
        try {
            BossBalanceTests.result(name+"_hp",boss.getType().toString(),hp,loaded.getHealth());
            BossBalanceTests.result(name+"_max",boss.getType().toString(),max,loaded.getMaxHealth());
            BossBalanceTests.result(name+"_uuid",boss.getType().toString(),1,boss.getUUID().equals(loaded.getUUID())?1:0);
        } finally {loaded.discard();}
    }
    /** Run actual native behavior start/stop code with its brain-memory gates.
     * The harness supplies elapsed time; this is not an autonomous fight. */
    private static void wardenLifecycle(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        Warden warden=null;
        try {
            BossBalanceTests.resetActualPlayer(victim);
            warden=(Warden)BossBalanceTests.damageBoss(level,"minecraft:warden");profile(warden);
            warden.setInvulnerable(false);warden.setOnGround(true);
            long now=level.getGameTime();
            warden.getBrain().setMemory(MemoryModuleType.IS_EMERGING,Unit.INSTANCE);
            Emerging<Warden> emerging=new Emerging<>(134);
            BossBalanceTests.result("warden_emerging_behavior_start","minecraft:warden",1,emerging.tryStart(level,warden,now)?1:0);
            BossBalanceTests.result("warden_emerging_native_pose","minecraft:warden",Pose.EMERGING.ordinal(),warden.getPose().ordinal());
            warden.hurt(level.damageSources().playerAttack(victim),10);
            BossBalanceTests.result("warden_emerging_native_immunity","minecraft:warden",1300,warden.getHealth());
            emerging.tickOrStop(level,warden,now+135);
            BossBalanceTests.result("warden_emerging_native_completion","minecraft:warden",Pose.STANDING.ordinal(),warden.getPose().ordinal());
            warden.getBrain().eraseMemory(MemoryModuleType.IS_EMERGING);

            warden.getBrain().setMemory(MemoryModuleType.NEAREST_ATTACKABLE,victim);
            warden.getBrain().eraseMemory(MemoryModuleType.SNIFF_COOLDOWN);
            warden.getBrain().eraseMemory(MemoryModuleType.DISTURBANCE_LOCATION);
            BossBalanceTests.result("warden_try_to_sniff_native_start","minecraft:warden",1,TryToSniff.create().tryStart(level,warden,now)?1:0);
            Sniffing<Warden> sniffing=new Sniffing<>(84);
            BossBalanceTests.result("warden_sniffing_behavior_start","minecraft:warden",1,sniffing.tryStart(level,warden,now)?1:0);
            BossBalanceTests.result("warden_sniffing_native_pose","minecraft:warden",Pose.SNIFFING.ordinal(),warden.getPose().ordinal());
            sniffing.tickOrStop(level,warden,now+85);
            BossBalanceTests.result("warden_sniffing_native_completion","minecraft:warden",Pose.STANDING.ordinal(),warden.getPose().ordinal());
            BossBalanceTests.result("warden_sniffing_memory_cleared","minecraft:warden",0,warden.getBrain().hasMemoryValue(MemoryModuleType.IS_SNIFFING)?1:0);

            warden.getBrain().setMemory(MemoryModuleType.ROAR_TARGET,victim);
            warden.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            Roar roar=new Roar();
            BossBalanceTests.result("warden_roar_behavior_start","minecraft:warden",1,roar.tryStart(level,warden,now)?1:0);
            BossBalanceTests.result("warden_roaring_native_pose","minecraft:warden",Pose.ROARING.ordinal(),warden.getPose().ordinal());
            roar.tickOrStop(level,warden,now+85);
            BossBalanceTests.result("warden_roar_native_completion","minecraft:warden",Pose.STANDING.ordinal(),warden.getPose().ordinal());
            BossBalanceTests.result("warden_roar_acquires_attack_target","minecraft:warden",1,warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null)==victim?1:0);

            warden.hurt(level.damageSources().playerAttack(victim),10);
            BossBalanceTests.result("warden_standing_native_vulnerable_control","minecraft:warden",1,warden.getHealth()<1300?1:0);
            warden.setHealth(1300);warden.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            warden.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            Digging<Warden> digging=new Digging<>(100);
            BossBalanceTests.result("warden_digging_behavior_start","minecraft:warden",1,digging.tryStart(level,warden,now)?1:0);
            BossBalanceTests.result("warden_digging_native_pose","minecraft:warden",Pose.DIGGING.ordinal(),warden.getPose().ordinal());
            warden.invulnerableTime=0;warden.hurt(level.damageSources().playerAttack(victim),10);
            BossBalanceTests.result("warden_digging_native_immunity","minecraft:warden",1300,warden.getHealth());
            digging.tickOrStop(level,warden,now+101);
            BossBalanceTests.result("warden_digging_native_despawn","minecraft:warden",1,warden.getRemovalReason()==Entity.RemovalReason.DISCARDED?1:0);
        } catch(Throwable error){BossBalanceTests.error("warden_lifecycle","minecraft:warden",error);}
        finally {if(warden!=null)warden.discard();}
    }
    private static void profile(LivingEntity entity) throws Exception {Class.forName("survival.balance.BossHealthBalance").getMethod("applyInitialProfile",LivingEntity.class).invoke(null,entity);}
    private static Object field(Object object,String name) throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static Object invoke(Object object,String name,Class<?>[] types,Object... values) throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Method m=type.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,values);}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(name);
    }
}

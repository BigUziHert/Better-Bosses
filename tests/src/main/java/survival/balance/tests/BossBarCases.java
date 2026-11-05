package survival.balance.tests;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Native server boss-event regressions; client rendering is deliberately not claimed here. */
final class BossBarCases {
    private static final List<String> IDS=List.of(
        "twilightforest:naga","twilightforest:lich","twilightforest:minoshroom",
        "twilightforest:knight_phantom","twilightforest:alpha_yeti","twilightforest:hydra",
        "twilightforest:ur_ghast","twilightforest:snow_queen",
        "bosses_of_mass_destruction:lich","bosses_of_mass_destruction:void_blossom",
        "bosses_of_mass_destruction:gauntlet","bosses_of_mass_destruction:obsidilith",
        "cataclysm:netherite_monstrosity","cataclysm:ender_guardian","cataclysm:the_harbinger",
        "cataclysm:ancient_remnant","cataclysm:the_leviathan","cataclysm:scylla",
        "cataclysm:maledictus","cataclysm:ignis","minecraft:wither","minecraft:warden");

    static void run(ServerLevel level) {
        for(String id:IDS) checkBoss(level,id);
        knights(level);
        lateKnight(level);
        dragon(level);
    }

    private static void checkBoss(ServerLevel level,String id) {
        LivingEntity boss=null;
        BossBalanceTests.TestPlayer viewer=null;
        try {
            boss=BossBalanceTests.damageBoss(level,id);
            if(!level.addFreshEntity(boss))throw new IllegalStateException("Boss join failed");
            Map<String,ServerBossEvent> bars=bars(boss);
            int expectedCount=id.equals("minecraft:warden")?0:
                id.equals("cataclysm:ancient_remnant")||id.equals("cataclysm:maledictus")?2:1;
            eq(id,"native_bar_count",expectedCount,bars.size());
            if(expectedCount==0)return; // Vanilla Warden intentionally has no health bar.
            if(bars.isEmpty())throw new IllegalStateException("Expected native boss bar missing");
            ServerBossEvent health=healthBar(id,bars);
            activate(id,boss,bars);
            if(id.equals("twilightforest:lich"))lichShields(id,boss,health);
            if(boss instanceof WitherBoss wither)witherSpawn(id,wither,health);
            if(id.equals("cataclysm:the_leviathan"))leviathanPhaseOne(id,boss,health);

            for(float fraction:new float[]{1f,.5f,.1f}) {
                boss.setHealth(boss.getMaxHealth()*fraction);
                update(id,boss);
                eq(id,"health_bar_"+fraction,boss.getHealth()/boss.getMaxHealth(),health.getProgress());
                eq(id,"health_bar_visible_"+fraction,1,health.isVisible()?1:0);
                eq(id,"health_bar_finite_"+fraction,1,Float.isFinite(health.getProgress())&&health.getProgress()>=0&&health.getProgress()<=1?1:0);
            }
            rage(id,boss,bars);
            viewer=new BossBalanceTests.TestPlayer(level,new GameProfile(UUID.randomUUID(),"BBBarViewer"));
            viewer.moveTo(8,220,4,0,0);
            boss.startSeenByPlayer(viewer);
            for(var bar:bars.entrySet())eq(id,"tracking_added_"+bar.getKey(),1,bar.getValue().getPlayers().contains(viewer)?1:0);
            boss.stopSeenByPlayer(viewer);
            for(var bar:bars.entrySet())eq(id,"tracking_removed_"+bar.getKey(),0,bar.getValue().getPlayers().contains(viewer)?1:0);
            boss.startSeenByPlayer(viewer);
            eq(id,"tracking_rejoin",1,health.getPlayers().contains(viewer)?1:0);
            boss.stopSeenByPlayer(viewer);

            boss.setHealth(boss.getMaxHealth()*.5f);
            CompoundTag saved=boss.saveWithoutId(new CompoundTag());saved.putString("id",id);
            boss.discard();
            boss=(LivingEntity)EntityType.create(saved,level).orElseThrow();
            if(!level.addFreshEntity(boss))throw new IllegalStateException("Saved boss join failed");
            update(id,boss);
            ServerBossEvent restored=healthBar(id,bars(boss));
            eq(id,"reload_health_bar",boss.getHealth()/boss.getMaxHealth(),restored.getProgress());
            eq(id,"reload_bar_visible",1,restored.isVisible()?1:0);
        }catch(Throwable error){BossBalanceTests.error("boss_bars",id,error);}
        finally {
            if(boss!=null){if(viewer!=null)boss.stopSeenByPlayer(viewer);boss.discard();}
            if(viewer!=null)viewer.discard();
        }
    }

    private static void activate(String id,LivingEntity boss,Map<String,ServerBossEvent> bars)throws Exception {
        String setter=switch(id){
            case "cataclysm:netherite_monstrosity" -> "setIsAwaken";
            case "cataclysm:the_harbinger" -> "setIsAct";
            case "cataclysm:ancient_remnant" -> "setNecklace";
            case "cataclysm:scylla" -> "setAct";
            default -> null;
        };
        if(setter!=null){
            call(boss,setter,new Class<?>[]{boolean.class},false);
            for(var bar:bars.entrySet())eq(id,"dormant_hidden_"+bar.getKey(),0,bar.getValue().isVisible()?1:0);
            call(boss,setter,new Class<?>[]{boolean.class},true);
            for(var bar:bars.entrySet())eq(id,"activated_visible_"+bar.getKey(),1,bar.getValue().isVisible()?1:0);
        }
    }

    private static void lichShields(String id,LivingEntity boss,ServerBossEvent bar)throws Exception {
        int maxShield=((Number)call(boss,"getShieldStrength",new Class<?>[0])).intValue();
        if(maxShield<=0)throw new IllegalStateException("Fresh Lich has no shields");
        boss.setHealth(boss.getMaxHealth()*.25f);
        update(id,boss);
        eq(id,"shield_phase_bar_not_health",1,bar.getProgress());
        int remaining=Math.max(1,maxShield/2);
        call(boss,"setShieldStrength",new Class<?>[]{int.class},remaining);
        update(id,boss);
        eq(id,"shield_phase_bar_partial",remaining/(double)maxShield,bar.getProgress());
        eq(id,"shield_phase_notches",1,bar.getOverlay()==BossEvent.BossBarOverlay.NOTCHED_6?1:0);
        call(boss,"setShieldStrength",new Class<?>[]{int.class},0);
        call(boss,"setMinionsToSummon",new Class<?>[]{int.class},1);
        update(id,boss);
        eq(id,"minion_phase_bar_health",.25,bar.getProgress());
        call(boss,"setMinionsToSummon",new Class<?>[]{int.class},0);
        update(id,boss);
        eq(id,"melee_phase_bar_health",.25,bar.getProgress());
        eq(id,"health_phase_progress_style",1,bar.getOverlay()==BossEvent.BossBarOverlay.PROGRESS?1:0);
        call(boss,"setMasterUUID",new Class<?>[]{UUID.class},UUID.randomUUID());
        update(id,boss);
        eq(id,"shadow_clone_bar_hidden",0,bar.isVisible()?1:0);
        call(boss,"setMasterUUID",new Class<?>[]{UUID.class},(Object)null);
        update(id,boss);
    }

    private static void witherSpawn(String id,WitherBoss boss,ServerBossEvent bar)throws Exception {
        boss.makeInvulnerable();
        eq(id,"spawn_bar_empty",0,bar.getProgress());
        for(int ticks:new int[]{220,110,2}) {
            boss.setInvulnerableTicks(ticks);boss.tickCount=1;
            call(boss,"customServerAiStep",new Class<?>[0]);
            eq(id,"spawn_charge_bar_"+ticks,1d-(ticks-1)/220d,bar.getProgress());
        }
        boss.setInvulnerableTicks(0);
    }

    private static void leviathanPhaseOne(String id,LivingEntity boss,ServerBossEvent bar)throws Exception {
        call(boss,"setMeltDown",new Class<?>[]{boolean.class},false);
        // The old suite stopped at half health and missed the native interval
        // below half while the previous attack / phase-two animation finishes.
        // These are controlled native-tick probes, not natural phase coverage.
        for(float fraction:new float[]{1f,.75f,.501f,.5f,.499f,.1f}){
            boss.setHealth(boss.getMaxHealth()*fraction);update(id,boss);
            eq(id,"phase_one_segmented_bar_"+fraction,Math.max(0d,2d*fraction-1d),bar.getProgress());
            eq(id,"phase_one_native_flag_"+fraction,0,(boolean)call(boss,"getMeltDown",new Class<?>[0])?1:0);
            eq(id,"phase_one_native_style_"+fraction,5,((Number)call(bar,"getRenderType",new Class<?>[0])).intValue());
            eq(id,"phase_one_health_unchanged_"+fraction,boss.getMaxHealth()*fraction,boss.getHealth());
        }
        ServerLevel level=(ServerLevel)boss.level();
        java.util.Set<Entity> existing=new java.util.HashSet<>();
        for(Entity entity:level.getAllEntities())existing.add(entity);
        Field phaseField=boss.getClass().getField("LEVIATHAN_PHASE2");
        Object phase=phaseField.get(null);
        try {
            boss.setHealth(boss.getMaxHealth()*.4f);
            call(boss,"setAnimation",new Class<?>[]{phaseField.getType()},phase);
            call(boss,"setAnimationTick",new Class<?>[]{int.class},89);
            boss.aiStep(); // Native phase code has not reached its frame-90 switch.
            eq(id,"pending_phase_before90_flag",0,(boolean)call(boss,"getMeltDown",new Class<?>[0])?1:0);
            update(id,boss);
            eq(id,"pending_phase_before90_bar",0,bar.getProgress());
            eq(id,"pending_phase_before90_style",5,((Number)call(bar,"getRenderType",new Class<?>[0])).intValue());
            eq(id,"pending_phase_animation_preserved",1,call(boss,"getAnimation",new Class<?>[0])==phase?1:0);

            call(boss,"setAnimationTick",new Class<?>[]{int.class},90);
            boss.aiStep(); // The installed boss itself changes the flag and emits its native orbs.
            eq(id,"native_phase_frame90_flag",1,(boolean)call(boss,"getMeltDown",new Class<?>[0])?1:0);
            call(boss,"setAnimationTick",new Class<?>[]{int.class},91);
            update(id,boss);
            eq(id,"native_phase_after90_bar",.4,bar.getProgress());
            eq(id,"native_phase_after90_style",6,((Number)call(bar,"getRenderType",new Class<?>[0])).intValue());
            eq(id,"native_phase_health_unchanged",boss.getMaxHealth()*.4f,boss.getHealth());
        } finally {
            // Only entities emitted by this synchronous staged phase probe are
            // removed. Pre-existing players, fixtures and migration entities stay.
            List<Entity> emitted=new ArrayList<>();
            for(Entity entity:level.getAllEntities())if(!existing.contains(entity))emitted.add(entity);
            for(Entity entity:emitted)entity.discard();
            Field idle=boss.getClass().getField("NO_ANIMATION");
            call(boss,"setAnimation",new Class<?>[]{idle.getType()},idle.get(null));
            call(boss,"setAnimationTick",new Class<?>[]{int.class},0);
        }
    }

    private static void rage(String id,LivingEntity boss,Map<String,ServerBossEvent> bars)throws Exception {
        String setter=id.equals("cataclysm:ancient_remnant")?"setRage":id.equals("cataclysm:maledictus")?"setRageMeter":null;
        if(setter==null)return;
        for(int amount:new int[]{0,2,5}) {
            call(boss,setter,new Class<?>[]{int.class},amount);
            if(id.equals("cataclysm:maledictus"))set(boss,"rageTicks",200);
            update(id,boss);
            eq(id,"rage_bar_"+amount,amount/5d,bars.get("bossEvent2").getProgress());
        }
    }

    private static void knights(ServerLevel level) {
        String id="twilightforest:knight_phantom";
        List<LivingEntity> knights=new ArrayList<>();
        try {
            for(int i=0;i<6;i++) {
                LivingEntity boss=BossBalanceTests.damageBoss(level,id);boss.moveTo(8+i,220,8,0,0);
                call(boss,"setNumber",new Class<?>[]{int.class},i);
                if(!level.addFreshEntity(boss))throw new IllegalStateException("Knight join failed");
                knights.add(boss);
            }
            for(LivingEntity knight:knights)update(id,knight);
            for(float fraction:new float[]{1f,.5f,.1f}) {
                for(LivingEntity knight:knights)knight.setHealth(knight.getMaxHealth()*fraction);
                update(id,knights.getFirst());
                eq(id,"six_knights_combined_"+fraction,fraction,healthBar(id,bars(knights.getFirst())).getProgress());
            }
            knights.getLast().setHealth(0);
            update(id,knights.getFirst());
            eq(id,"six_knights_one_dead",.1*5d/6d,healthBar(id,bars(knights.getFirst())).getProgress());
            List<CompoundTag> savedKnights=new ArrayList<>();
            for(LivingEntity knight:knights)if(knight.isAlive()){
                CompoundTag saved=knight.saveWithoutId(new CompoundTag());saved.putString("id",id);savedKnights.add(saved);
            }
            for(LivingEntity knight:knights)knight.discard();
            knights.clear();
            for(CompoundTag saved:savedKnights){
                LivingEntity restored=(LivingEntity)EntityType.create(saved,level).orElseThrow();
                if(!level.addFreshEntity(restored))throw new IllegalStateException("Knight group reload failed");
                knights.add(restored);
            }
            update(id,knights.getFirst());
            eq(id,"six_knights_one_dead_reload",.1*5d/6d,healthBar(id,bars(knights.getFirst())).getProgress());
        }catch(Throwable error){BossBalanceTests.error("boss_bars_knight_group",id,error);}
        finally{for(LivingEntity knight:knights)knight.discard();}
    }

    private static void dragon(ServerLevel level) {
        String id="minecraft:ender_dragon";EnderDragon dragon=null;BossBalanceTests.TestPlayer viewer=null;
        EndDragonFight fight=null;
        try {
            dragon=(EnderDragon)BossBalanceTests.damageBoss(level,id);
            if(!level.addFreshEntity(dragon))throw new IllegalStateException("Dragon join failed");
            fight=new EndDragonFight(level,0L,new EndDragonFight.Data(false,false,false,false,
                Optional.of(dragon.getUUID()),Optional.empty(),Optional.empty()));
            dragon.setDragonFight(fight);
            ServerBossEvent bar=(ServerBossEvent)field(fight,"dragonEvent");
            // YUNG's Better End Island deliberately hides the bar before its first
            // ritual. A newly constructed manager needs its native spawned flag,
            // exactly as the completed ritual / loaded ExtraFightData supplies it.
            try {
                call(fight,"hasDragonEverSpawned",new Class<?>[0]);
                fight.tick();
                eq(id,"before_first_ritual_bar_hidden",0,bar.isVisible()?1:0);
                call(fight,"setHasDragonEverSpawned",new Class<?>[]{boolean.class},true);
            }catch(NoSuchMethodException vanillaOnly){}
            for(float fraction:new float[]{1f,.5f,.1f}){
                dragon.setHealth(dragon.getMaxHealth()*fraction);
                fight.updateDragon(dragon); // The actual fight manager path used by Dragon.aiStep.
                eq(id,"fight_manager_progress_"+fraction,fraction,bar.getProgress());
            }
            fight.tick();
            eq(id,"alive_bar_visible",1,bar.isVisible()?1:0);
            viewer=new BossBalanceTests.TestPlayer(level,new GameProfile(UUID.randomUUID(),"BBDragonViewer"));
            viewer.setGameMode(GameType.SURVIVAL);viewer.moveTo(0,128,0,0,0);level.addNewPlayer(viewer);
            call(fight,"updatePlayers",new Class<?>[0]);
            eq(id,"fight_tracking_near",1,bar.getPlayers().contains(viewer)?1:0);
            viewer.moveTo(1000,128,1000,0,0);call(fight,"updatePlayers",new Class<?>[0]);
            eq(id,"fight_tracking_far_removed",0,bar.getPlayers().contains(viewer)?1:0);
            set(fight,"dragonKilled",true);fight.tick();
            eq(id,"dead_bar_hidden",0,bar.isVisible()?1:0);
        }catch(Throwable error){BossBalanceTests.error("boss_bars",id,error);}
        finally{if(viewer!=null)viewer.discard();if(dragon!=null)dragon.discard();}
    }

    private static void lateKnight(ServerLevel level) {
        String id="twilightforest:knight_phantom";
        LivingEntity first=null,late=null;
        try {
            first=BossBalanceTests.damageBoss(level,id);
            if(!level.addFreshEntity(first))throw new IllegalStateException("Solo Knight join failed");
            update(id,first); // Establish a real repaired solo group before a later entity joins.
            eq(id,"late_group_initial_solo",1,healthBar(id,bars(first)).getProgress());
            late=BossBalanceTests.damageBoss(level,id);late.moveTo(10,220,8,0,0);
            call(late,"setNumber",new Class<?>[]{int.class},1);
            if(!level.addFreshEntity(late))throw new IllegalStateException("Late Knight join failed");
            update(id,first);update(id,late);
            eq(id,"late_group_two_alive",1,healthBar(id,bars(first)).getProgress());
            late.setHealth(0);update(id,first);
            eq(id,"late_group_one_dead",.5,healthBar(id,bars(first)).getProgress());
            CompoundTag saved=first.saveWithoutId(new CompoundTag());saved.putString("id",id);first.discard();
            first=(LivingEntity)EntityType.create(saved,level).orElseThrow();
            if(!level.addFreshEntity(first))throw new IllegalStateException("Late group survivor reload failed");
            update(id,first);
            eq(id,"late_group_one_dead_reload",.5,healthBar(id,bars(first)).getProgress());
        }catch(Throwable error){BossBalanceTests.error("boss_bars_late_knight",id,error);}
        finally{if(first!=null)first.discard();if(late!=null)late.discard();}
    }

    private static void update(String id,LivingEntity boss)throws Exception {
        boss.tickCount=1; // Prevent incidental every-20-tick native regeneration during sampling.
        if(id.equals("twilightforest:knight_phantom"))
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(boss)); // The normal NeoForge pre-tick compatibility hook.
        if(id.equals("twilightforest:knight_phantom")||id.startsWith("bosses_of_mass_destruction:")||id.equals("minecraft:wither"))
            call(boss,"customServerAiStep",new Class<?>[0]);
        else if(id.startsWith("twilightforest:"))call(boss,"tickBossBar",new Class<?>[0]);
        else if(id.equals("cataclysm:the_harbinger"))boss.aiStep();
        else boss.tick();
    }

    private static Map<String,ServerBossEvent> bars(Object object)throws Exception {
        Map<String,ServerBossEvent> result=new LinkedHashMap<>();
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers())||!ServerBossEvent.class.isAssignableFrom(field.getType()))continue;
            field.setAccessible(true);Object value=field.get(object);
            if(value instanceof ServerBossEvent bar)result.put(field.getName(),bar);
        }
        return result;
    }
    private static ServerBossEvent healthBar(String id,Map<String,ServerBossEvent> bars){
        if(id.equals("cataclysm:maledictus"))return bars.get("bossEvent1");
        if(id.equals("cataclysm:ancient_remnant"))return bars.get("bossEvent");
        return bars.values().iterator().next();
    }
    private static Object call(Object object,String name,Class<?>[] args,Object... values)throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{
            Method method=type.getDeclaredMethod(name,args);method.setAccessible(true);return method.invoke(object,values);
        }catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(object.getClass()+"."+name);
    }
    private static Field findField(Object object,String name)throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{
            Field field=type.getDeclaredField(name);field.setAccessible(true);return field;
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static Object field(Object object,String name)throws Exception{return findField(object,name).get(object);}
    private static void set(Object object,String name,Object value)throws Exception{findField(object,name).set(object,value);}
    private static void eq(String id,String name,double expected,double actual){BossBalanceTests.result("bossbar_"+name,id,expected,actual);}
}

package survival.balance.tests;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Real installed attack methods, with a normal 20-HP player and unenchanted diamond armor. */
final class ActualTwilightAttacks {
    @FunctionalInterface private interface Attack { void perform(LivingEntity boss) throws Exception; }

    static void run(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        attack(level,victim,"naga","head",BalanceExpectations.target("naga.head"),"Naga.doHurtTarget(player)",
            boss -> ((Mob)boss).doHurtTarget(victim));
        attack(level,victim,"naga","body",BalanceExpectations.target("naga.body"),"NagaSegment.collideWithEntity(player)",boss -> {
            Class<?> type=Class.forName("twilightforest.entity.boss.NagaSegment");
            Object segment=type.getConstructor(boss.getClass()).newInstance(boss);
            invoke(segment,"collideWithEntity",new Class<?>[]{Entity.class},victim);
        });

        attack(level,victim,"lich","bolt",BalanceExpectations.target("twilight_lich.bolt"),"LichBolt.onHitEntity(actual player collision)",boss -> {
            Entity bolt=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("twilightforest:lich_bolt")).create(level);
            if(!(bolt instanceof Projectile projectile))throw new IllegalStateException("Lich bolt missing");
            try {
                projectile.setOwner(boss);projectile.moveTo(victim.position());
                invoke(bolt,"onHitEntity",new Class<?>[]{EntityHitResult.class},new EntityHitResult(victim));
            }finally{bolt.discard();}
        });
        attack(level,victim,"lich","phase3_sword",BalanceExpectations.target("twilight_lich.melee"),"Lich phase3 (shield0,minions0), native golden-sword melee",boss -> {
            invoke(boss,"setShieldStrength",new Class<?>[]{int.class},0);
            invoke(boss,"setMinionsToSummon",new Class<?>[]{int.class},0);
            // Native getMainHandItem supplies the phase-three golden sword; update its actual attributes.
            invoke(boss,"detectEquipmentUpdates",new Class<?>[0]);
            ((Mob)boss).doHurtTarget(victim);
        });

        attack(level,victim,"minoshroom","axe",BalanceExpectations.target("minoshroom.axe"),"Minoshroom.doHurtTarget(player), native equipped axe",
            boss -> ((Mob)boss).doHurtTarget(victim));
        attack(level,victim,"knight_phantom","ordinary",BalanceExpectations.target("knight_phantom.ordinary"),"KnightPhantom.doHurtTarget(player), not charging",boss -> {
            invoke(boss,"setChargingAtPlayer",new Class<?>[]{boolean.class},false);
            ((Mob)boss).doHurtTarget(victim);
        });
        attack(level,victim,"knight_phantom","charge",BalanceExpectations.target("knight_phantom.charge"),"KnightPhantom.doHurtTarget(player), native charging modifier enabled",boss -> {
            invoke(boss,"setChargingAtPlayer",new Class<?>[]{boolean.class},true);
            ((Mob)boss).doHurtTarget(victim);
        });
        attack(level,victim,"alpha_yeti","stomp",BalanceExpectations.target("alpha_yeti.stomp"),"AlphaYeti.hitNearbyEntities(), player inside real stomp AABB",boss -> {
            invoke(boss,"setRampaging",new Class<?>[]{boolean.class},true);
            invoke(boss,"hitNearbyEntities",new Class<?>[0]);
        });

        attack(level,victim,"hydra","bite",BalanceExpectations.target("hydra.bite"),"HydraHeadContainer.executeAttacks(), BITING head overlaps player",boss -> {
            Object container=((Object[])field(boss,"hc"))[0];
            Entity head=(Entity)field(container,"headEntity");
            head.moveTo(victim.getX(),victim.getY(),victim.getZ(),0,0);
            setHeadState(head,"BITING");
            invoke(container,"executeAttacks",new Class<?>[0]);
        });
        attack(level,victim,"hydra","flame",BalanceExpectations.target("hydra.flame"),"HydraHeadContainer.executeAttacks(), FLAMING head's native30-block ray/endpoint search intersects player",boss -> {
            Object container=((Object[])field(boss,"hc"))[0];
            Entity head=(Entity)field(container,"headEntity");
            // Installed getHeadLookTarget moves its entity-search AABB to the ray endpoint.
            // Put the normal player there, rather than assuming the search sweeps the full ray.
            head.moveTo(8,220,34,180,0);head.setYRot(180);head.setXRot(0);
            setHeadState(head,"FLAMING");
            Object selected=invoke(container,"getHeadLookTarget",new Class<?>[0]);
            BossBalanceTests.result("actual_tf_hydra_flame_selected_player","twilightforest:hydra",1,selected==victim?1:0);
            System.out.println("BBTF_HYDRA_FLAME head="+head.position()+" view="+head.getViewVector(1)+" selected="+selected);
            invoke(container,"executeAttacks",new Class<?>[0]);
        });

        attack(level,victim,"ur_ghast","tantrum",BalanceExpectations.target("ur_ghast.tantrum"),"UrGhast.doTantrumDamageEffects(), exposed player ten blocks below boss",boss -> {
            boss.moveTo(victim.getX(),victim.getY()+10,victim.getZ(),0,0);
            invoke(boss,"setInTantrum",new Class<?>[]{boolean.class},true);
            invoke(boss,"doTantrumDamageEffects",new Class<?>[0]);
        });
        attack(level,victim,"ur_ghast","fireball_direct",BalanceExpectations.target("ur_ghast.fireball"),"UrGhastFireball.onHitEntity(), native power1 at player's feet; includes immediate secondary explosion",boss -> {
            Entity fireball=urFireball(level,boss);
            try {
                fireball.moveTo(victim.position());
                invoke(fireball,"onHitEntity",new Class<?>[]{EntityHitResult.class},new EntityHitResult(victim));
            }finally{fireball.discard();}
        });
        attack(level,victim,"ur_ghast","fireball_native_splash",4.78125,"UrGhastFireball.onHitBlock(), native power1 at feet/full exposure; native power1 splash below configured6heart cap",boss -> {
            Entity fireball=urFireball(level,boss);
            try {
                fireball.moveTo(victim.position());
                BlockHitResult collision=new BlockHitResult(victim.position(),Direction.UP,victim.blockPosition(),false);
                invoke(fireball,"onHitBlock",new Class<?>[]{BlockHitResult.class},collision);
            }finally{fireball.discard();}
        });
        attack(level,victim,"snow_queen","beam",BalanceExpectations.target("snow_queen.beam"),"SnowQueen.doBreathAttack(player)",boss ->
            invoke(boss,"doBreathAttack",new Class<?>[]{Entity.class},victim));
        attack(level,victim,"snow_queen","contact",BalanceExpectations.target("snow_queen.contact"),"SnowQueen.doHurtTarget(player), native contact damage",boss ->
            ((Mob)boss).doHurtTarget(victim));
    }

    private static Entity urFireball(ServerLevel level,LivingEntity boss) throws Exception {
        // UrGhastAttackGoal constructs this exact class with explosion power1 in the installed build.
        Class<?> type=Class.forName("twilightforest.entity.projectile.UrGhastFireball");
        return (Entity)type.getConstructor(Level.class,boss.getClass(),double.class,double.class,double.class,int.class)
            .newInstance(level,boss,0d,0d,1d,1);
    }

    private static void attack(ServerLevel level,BossBalanceTests.TestPlayer victim,String shortId,String name,double expected,String description,Attack action) {
        String id="twilightforest:"+shortId;
        LivingEntity boss=null;
        try {
            BossBalanceTests.resetActualPlayer(victim);
            victim.clearFire();victim.stopUsingItem();
            boss=BossBalanceTests.damageBoss(level,id);
            double before=victim.getHealth();
            action.perform(boss);
            BossBalanceTests.observedAttack("actual_tf_"+shortId+"_"+name,id,description,victim,before,expected);
        }catch(Throwable ex){BossBalanceTests.error("actual_tf_"+shortId+"_"+name,id,ex);}
        finally{if(boss!=null)boss.discard();}
    }

    private static void setHeadState(Entity head,String name) throws Exception {
        Class<?> state=Class.forName("twilightforest.entity.boss.HydraHeadContainer$State");
        Field constant=state.getField(name);constant.setAccessible(true);
        Object value=constant.get(null);
        invoke(head,"setState",new Class<?>[]{state},value);
    }
    private static Object field(Object object,String name) throws Exception {
        for(Class<?> owner=object.getClass();owner!=null;owner=owner.getSuperclass())try {
            Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(object);
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static Object invoke(Object object,String name,Class<?>[] args,Object...values) throws Exception {
        for(Class<?> owner=object.getClass();owner!=null;owner=owner.getSuperclass())try {
            Method method=owner.getDeclaredMethod(name,args);method.setAccessible(true);return method.invoke(object,values);
        }catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(object.getClass().getName()+"."+name);
    }
}

package survival.balance.tests;

import java.lang.reflect.Method;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Direct installed BMD attack calls against a normally sized 20-HP player. */
final class ActualBmdAttacks {
    static void run(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        for(String[] row:new String[][]{
            {"bosses_of_mass_destruction:lich","bosses_of_mass_destruction:blue_fireball","2.0"},
            {"bosses_of_mass_destruction:void_blossom","bosses_of_mass_destruction:petal_blade","2.5"},
            {"bosses_of_mass_destruction:void_blossom","bosses_of_mass_destruction:spore_ball","1.0"}
        }) {
            LivingEntity boss=null;Entity projectile=null;
            try {
                BossBalanceTests.resetActualPlayer(victim);
                boss=BossBalanceTests.damageBoss(level,row[0]);
                projectile=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(row[1])).create(level);
                ((Projectile)projectile).setOwner(boss);projectile.moveTo(8,220,6,0,0);
                Method method=findMethod(projectile.getClass(),"entityHit",EntityHitResult.class);
                method.setAccessible(true);double before=victim.getHealth();
                method.invoke(projectile,new EntityHitResult(victim));
                BossBalanceTests.observedAttack("projectile_"+row[1],row[0],projectile.getClass().getSimpleName()+".entityHit",victim,before,Double.parseDouble(row[2]));
            }catch(Throwable ex){BossBalanceTests.error("actual_bmd_projectile",row[0],ex);}
            finally{if(projectile!=null)projectile.discard();if(boss!=null)boss.discard();}
        }
        LivingEntity boss=null;
        try {
            BossBalanceTests.resetActualPlayer(victim);boss=BossBalanceTests.damageBoss(level,"bosses_of_mass_destruction:gauntlet");
            double before=victim.getHealth();((Mob)boss).doHurtTarget(victim);
            BossBalanceTests.observedAttack("gauntlet_punch","bosses_of_mass_destruction:gauntlet","GauntletEntity.doHurtTarget",victim,before,3);
        }catch(Throwable ex){BossBalanceTests.error("gauntlet_punch","bosses_of_mass_destruction:gauntlet",ex);}finally{if(boss!=null)boss.discard();}
        for(String action:new String[]{"WaveAction","BurstAction","SpikeAction"}) {
            boss=null;
            try {
                BossBalanceTests.resetActualPlayer(victim);boss=BossBalanceTests.damageBoss(level,"bosses_of_mass_destruction:obsidilith");
                Class<?> clazz=Class.forName("com.cerbon.bosses_of_mass_destruction.entity.custom.obsidilith."+action);
                Object instance=clazz.getConstructor(action.equals("BurstAction")?LivingEntity.class:Mob.class).newInstance(boss);
                Method method=clazz.getDeclaredMethod("damageEntity",LivingEntity.class);method.setAccessible(true);
                double before=victim.getHealth();method.invoke(instance,victim);
                BossBalanceTests.observedAttack("obsidilith_"+action,"bosses_of_mass_destruction:obsidilith",action+".damageEntity",victim,before,action.equals("SpikeAction")?3:1.75);
            }catch(Throwable ex){BossBalanceTests.error("obsidilith_"+action,"bosses_of_mass_destruction:obsidilith",ex);}finally{if(boss!=null)boss.discard();}
        }
        boss=null;
        try {
            BossBalanceTests.resetActualPlayer(victim);boss=BossBalanceTests.damageBoss(level,"bosses_of_mass_destruction:gauntlet");
            Class<?> clazz=Class.forName("com.cerbon.bosses_of_mass_destruction.entity.custom.gauntlet.LaserAction");
            Class<?> scheduler=Class.forName("com.cerbon.cerbons_api.api.general.event.EventScheduler");
            Object instance=clazz.getConstructor(boss.getClass(),scheduler,java.util.function.Supplier.class,ServerLevel.class)
                .newInstance(boss,null,(java.util.function.Supplier<Boolean>)()->false,level);
            Method method=clazz.getDeclaredMethod("applyLaserToEntities",Vec3.class);method.setAccessible(true);
            Vec3 end=boss.getEyePosition().add(victim.getEyePosition().subtract(boss.getEyePosition()).scale(2));
            double before=victim.getHealth();method.invoke(instance,end);
            BossBalanceTests.observedAttack("gauntlet_laser","bosses_of_mass_destruction:gauntlet","LaserAction.applyLaserToEntities (ray through player)",victim,before,.75);
        }catch(Throwable ex){BossBalanceTests.error("gauntlet_laser","bosses_of_mass_destruction:gauntlet",ex);}finally{if(boss!=null)boss.discard();}
    }
    private static Method findMethod(Class<?> type,String name,Class<?>...args) throws NoSuchMethodException {
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{return c.getDeclaredMethod(name,args);}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(name);
    }
}

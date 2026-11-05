package survival.balance.tests;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Inputs are captured installed-version attack amounts; expected damage comes from the agreed design. */
final class DamageCases {
    record Case(String name,String boss,String type,String direct,float oldHard,double hearts,int state,int rage,String animation,boolean owner) {}
    private static final List<Case> CASES=new ArrayList<>();
    static {
        add("naga_head","twilightforest:naga","minecraft:mob_attack","",7.5f,1.5);
        add("naga_body","twilightforest:naga","minecraft:mob_attack","",3f,.45);
        add("lich_bolt","twilightforest:lich","twilightforest:lich_bolt","",9f,2);
        add("lich_bomb_cap","twilightforest:lich","twilightforest:lich_bomb","",100f,3);
        add("minoshroom_axe","twilightforest:minoshroom","minecraft:mob_attack","",16.5f,2);
        add("minoshroom_slam","twilightforest:minoshroom","twilightforest:slam","",8.25f,3.25);
        add("knight_charge","twilightforest:knight_phantom","twilightforest:haunt","",21f,1.625);
        add("knight_normal","twilightforest:knight_phantom","twilightforest:haunt","",10.5f,1);
        add("yeti_stomp","twilightforest:alpha_yeti","minecraft:mob_attack","",7.5f,1.5);
        CASES.add(new Case("yeti_falling_ice_cap","twilightforest:alpha_yeti","twilightforest:falling_ice","",100f,3.5,-1,0,"",false));
        add("hydra_flame","twilightforest:hydra","twilightforest:hydra_fire","",28.5f,1.375);
        add("hydra_bite","twilightforest:hydra","twilightforest:hydra_bite","",72f,4.5);
        add("hydra_mortar_cap","twilightforest:hydra","twilightforest:hydra_mortar","",100f,3.5);
        add("urghast_tear","twilightforest:ur_ghast","twilightforest:ghast_tear","",4.5f,.875);
        add("urghast_fireball","twilightforest:ur_ghast","minecraft:fireball","",24f,2.75);
        add("urghast_explosion_cap","twilightforest:ur_ghast","minecraft:explosion","",100f,3);
        add("snowqueen_beam","twilightforest:snow_queen","twilightforest:chilling_breath","",6f,.625);
        add("snowqueen_contact","twilightforest:snow_queen","minecraft:mob_attack","",10.5f,3);
        add("nightlich_missile","bosses_of_mass_destruction:lich","minecraft:thrown","bosses_of_mass_destruction:blue_fireball",13.5f,2);
        add("nightlich_comet_cap","bosses_of_mass_destruction:lich","minecraft:explosion","",100f,4);
        CASES.add(new Case("obsidilith_wave","bosses_of_mass_destruction:obsidilith","bosses_of_mass_destruction:shield_piercing","",24f,1.75,6,0,"",true));
        CASES.add(new Case("obsidilith_burst","bosses_of_mass_destruction:obsidilith","bosses_of_mass_destruction:shield_piercing","",24f,1.75,5,0,"",true));
        CASES.add(new Case("obsidilith_spikes","bosses_of_mass_destruction:obsidilith","bosses_of_mass_destruction:shield_piercing","",24f,3,7,0,"",true));
        add("obsidilith_anvil_cap","bosses_of_mass_destruction:obsidilith","minecraft:explosion","",100f,5);
        add("gauntlet_punch","bosses_of_mass_destruction:gauntlet","minecraft:mob_attack","",24f,3);
        add("gauntlet_laser","bosses_of_mass_destruction:gauntlet","minecraft:mob_attack","",18f,.75);
        add("gauntlet_explosion_cap","bosses_of_mass_destruction:gauntlet","minecraft:explosion","",100f,4.5);
        add("blossom_spikes","bosses_of_mass_destruction:void_blossom","bosses_of_mass_destruction:shield_piercing","",18f,2.5);
        add("blossom_blade","bosses_of_mass_destruction:void_blossom","minecraft:thrown","bosses_of_mass_destruction:petal_blade",18f,2.5);
        add("blossom_spore","bosses_of_mass_destruction:void_blossom","minecraft:thrown","bosses_of_mass_destruction:spore_ball",18f,1);
        add("monstrosity_smash","cataclysm:netherite_monstrosity","minecraft:mob_attack","",39.9f,3);
        add("monstrosity_heavy_cap","cataclysm:netherite_monstrosity","minecraft:mob_attack","",100f,5);
        add("monstrosity_flame_jet","cataclysm:netherite_monstrosity","minecraft:mob_attack","cataclysm:flame_jet",10.5f,1.25);
        add("guardian_punch","cataclysm:ender_guardian","minecraft:mob_attack","",25.5f,3);
        CASES.add(new Case("guardian_uppercut","cataclysm:ender_guardian","minecraft:mob_attack","",36.6f,4.5,-1,0,"UPPERCUT",true));
        add("guardian_rune","cataclysm:ender_guardian","minecraft:indirect_magic","cataclysm:void_rune",9f,1.5);
        add("harbinger_charge","cataclysm:the_harbinger","minecraft:mob_attack","",18.3f,3);
        add("harbinger_missile","cataclysm:the_harbinger","minecraft:mob_projectile","cataclysm:wither_missile",12f,1.5);
        add("harbinger_laser","cataclysm:the_harbinger","cataclysm:deathlaser","cataclysm:death_laser_beam",9f,1.125);
        add("harbinger_explosion_cap","cataclysm:the_harbinger","minecraft:explosion","",100f,3);
        add("remnant_bite","cataclysm:ancient_remnant","minecraft:mob_attack","",52.125f,3.5);
        CASES.add(new Case("remnant_charge","cataclysm:ancient_remnant","minecraft:mob_attack","",78f,5,11,0,"",true));
        add("remnant_earthquake","cataclysm:ancient_remnant","minecraft:mob_attack","cataclysm:earthquake",16.5f,3);
        add("leviathan_tail","cataclysm:the_leviathan","minecraft:mob_attack","",24.3f,3.25);
        CASES.add(new Case("leviathan_bite","cataclysm:the_leviathan","minecraft:mob_attack","",48f,5.5,-1,0,"BITE",true));
        add("leviathan_beam","cataclysm:the_leviathan","cataclysm:deathlaser","",18f,1);
        add("leviathan_orb","cataclysm:the_leviathan","minecraft:mob_projectile","cataclysm:abyss_orb",6f,1.5);
        add("scylla_melee","cataclysm:scylla","minecraft:mob_attack","",28.5f,3.5);
        add("scylla_lightning","cataclysm:scylla","cataclysm:lightning","",21f,5.25);
        add("scylla_lightning_pulse","cataclysm:scylla","cataclysm:lightning","",6f,1.25);
        CASES.add(new Case("maledictus_zero_rage","cataclysm:maledictus","minecraft:mob_attack","",21.9f,2,1,0,"",true));
        CASES.add(new Case("maledictus_max_rage","cataclysm:maledictus","minecraft:mob_attack","",41.4f,4,1,5,"",true));
        CASES.add(new Case("maledictus_heavy_cap","cataclysm:maledictus","cataclysm:maledictio_anima","",100f,6,33,5,"",true));
        add("maledictus_arrow","cataclysm:maledictus","cataclysm:maledictio_sagitta","cataclysm:phantom_arrow",7.5f,1.25);
        add("ignis_sword","cataclysm:ignis","minecraft:mob_attack","",22.5f,2.25);
        add("ignis_heavy_cap","cataclysm:ignis","minecraft:mob_attack","",100f,6);
        add("ignis_fireball_cap","cataclysm:ignis","minecraft:fireball","cataclysm:ignis_fireball",100f,3);
        add("ignis_flame_cap","cataclysm:ignis","cataclysm:flame_strike","",100f,1.25);
    }
    private static void add(String name,String boss,String type,String direct,float raw,double hearts){CASES.add(new Case(name,boss,type,direct,raw,hearts,-1,0,"",true));}

    static void run(ServerLevel level,List<Map<String,Object>> checks) throws Exception {
        BossBalanceTests.TestPlayer victim=BossBalanceTests.damagePlayer(level);
        BossBalanceTests.result("armor_value","player",20,victim.getAttributeValue(Attributes.ARMOR));
        BossBalanceTests.result("toughness_value","player",12,victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        for(Case test:CASES) {
            LivingEntity boss=null;
            try {
                BossBalanceTests.resetDamagePlayer(victim);
                boss=BossBalanceTests.damageBoss(level,test.boss);
                if(test.state>=0) {
                    if(test.boss.endsWith(":obsidilith")) setField(boss,"currentAttack",(byte)test.state);
                    else call(boss,"setAttackState",int.class,test.state);
                }
                if(test.boss.endsWith(":maledictus"))call(boss,"setRageMeter",int.class,test.rage);
                if(!test.animation.isEmpty())setAnimation(boss,test.animation);
                Entity direct=test.direct.isEmpty()?boss:createDirect(level,test.direct,boss);
                var holder=level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.parse(test.type)));
                DamageSource source=new DamageSource(holder,direct,test.owner?boss:null);
                float input=source.scalesWithDifficulty()?test.oldHard/1.5f:test.oldHard;
                float before=victim.getHealth();
                boolean accepted=victim.hurt(source,input);
                BossBalanceTests.result(test.name+"_accepted",test.boss,1,accepted?1:0);
                BossBalanceTests.result(test.name+"_hearts",test.boss,test.hearts,(before-victim.getHealth())/2d);
                if(direct!=boss)direct.discard();
            }catch(Throwable ex){BossBalanceTests.error(test.name,test.boss,ex);}finally{if(boss!=null)boss.discard();}
        }
        actualAttacks(level,victim);
        actualCallsites(level,victim);
        controls(level,victim);
        effects(level,victim);
    }

    private static void actualAttacks(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        for(String id:List.of("twilightforest:naga","twilightforest:minoshroom","bosses_of_mass_destruction:gauntlet"))try{
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity boss=BossBalanceTests.damageBoss(level,id);
            double expected=id.endsWith(":naga")?1.5:id.endsWith(":minoshroom")?2:3;
            double before=victim.getHealth();((Mob)boss).doHurtTarget(victim);
            BossBalanceTests.result("actual_doHurtTarget",id,expected,(before-victim.getHealth())/2);boss.discard();
        }catch(Throwable ex){BossBalanceTests.error("actual_doHurtTarget",id,ex);}
        try {
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity queen=BossBalanceTests.damageBoss(level,"twilightforest:snow_queen");
            double before=victim.getHealth();queen.getClass().getMethod("doBreathAttack",Entity.class).invoke(queen,victim);
            BossBalanceTests.result("actual_breath_method","twilightforest:snow_queen",.625,(before-victim.getHealth())/2);queen.discard();
        }catch(Throwable ex){BossBalanceTests.error("actual_breath_method","twilightforest:snow_queen",ex);}
        for(String[] row:new String[][]{
            {"bosses_of_mass_destruction:lich","bosses_of_mass_destruction:blue_fireball","2.0"},
            {"bosses_of_mass_destruction:void_blossom","bosses_of_mass_destruction:petal_blade","2.5"},
            {"bosses_of_mass_destruction:void_blossom","bosses_of_mass_destruction:spore_ball","1.0"}
        })try{
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity boss=BossBalanceTests.damageBoss(level,row[0]);Entity projectile=createDirect(level,row[1],boss);
            Method method=findMethod(projectile.getClass(),"entityHit",EntityHitResult.class);
            if(method==null)method=findMethod(projectile.getClass(),"onHitEntity",EntityHitResult.class);
            if(method==null)throw new NoSuchMethodException("Projectile hit method "+row[1]);
            method.setAccessible(true);double before=victim.getHealth();method.invoke(projectile,new EntityHitResult(victim));
            BossBalanceTests.result("actual_projectile_hit",row[1],Double.parseDouble(row[2]),(before-victim.getHealth())/2);projectile.discard();boss.discard();
        }catch(Throwable ex){BossBalanceTests.error("actual_projectile_hit",row[1],ex);}
    }

    /** Runs installed mod damage methods, including the precise frames used to distinguish shared sources. */
    private static void actualCallsites(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        for(String action:List.of("WaveAction","BurstAction","SpikeAction"))try {
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity boss=BossBalanceTests.damageBoss(level,"bosses_of_mass_destruction:obsidilith");
            // Wave/burst retain default state; delayed spikes deliberately conflict with a later wave state.
            if(action.equals("SpikeAction"))setField(boss,"currentAttack",(byte)6);
            Class<?> clazz=Class.forName("com.cerbon.bosses_of_mass_destruction.entity.custom.obsidilith."+action);
            Object instance=clazz.getConstructor(action.equals("BurstAction")?LivingEntity.class:Mob.class).newInstance(boss);
            Method method=clazz.getDeclaredMethod("damageEntity",LivingEntity.class);method.setAccessible(true);
            double before=victim.getHealth();method.invoke(instance,victim);
            BossBalanceTests.result("actual_callsite_"+action,"bosses_of_mass_destruction:obsidilith",action.equals("SpikeAction")?3:1.75,(before-victim.getHealth())/2);boss.discard();
        }catch(Throwable ex){BossBalanceTests.error("actual_callsite_"+action,"obsidilith",ex);}
        try {
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity boss=BossBalanceTests.damageBoss(level,"bosses_of_mass_destruction:void_blossom");
            Entity projectile=createDirect(level,"bosses_of_mass_destruction:spore_ball",boss);
            Method method=projectile.getClass().getDeclaredMethod("lambda$doExplosion$2",LivingEntity.class,LivingEntity.class);method.setAccessible(true);
            double before=victim.getHealth();method.invoke(projectile,boss,victim);
            BossBalanceTests.result("actual_callsite_spore_burst","bosses_of_mass_destruction:void_blossom",1,(before-victim.getHealth())/2);projectile.discard();boss.discard();
        }catch(Throwable ex){BossBalanceTests.error("actual_callsite_spore_burst","void_blossom",ex);}
        try {
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity boss=BossBalanceTests.damageBoss(level,"bosses_of_mass_destruction:gauntlet");
            Class<?> clazz=Class.forName("com.cerbon.bosses_of_mass_destruction.entity.custom.gauntlet.LaserAction");
            Class<?> scheduler=Class.forName("com.cerbon.cerbons_api.api.general.event.EventScheduler");
            Object instance=clazz.getConstructor(boss.getClass(),scheduler,java.util.function.Supplier.class,ServerLevel.class)
                .newInstance(boss,null,(java.util.function.Supplier<Boolean>)()->false,level);
            Method method=clazz.getDeclaredMethod("applyLaserToEntities",Vec3.class);method.setAccessible(true);
            // A straight ray through the actual registered player ensures the real laser geometry/hit loop runs.
            Vec3 end=boss.getEyePosition().add(victim.getEyePosition().subtract(boss.getEyePosition()).scale(2));
            double before=victim.getHealth();method.invoke(instance,end);
            BossBalanceTests.result("actual_callsite_laser","bosses_of_mass_destruction:gauntlet",.75,(before-victim.getHealth())/2);boss.discard();
        }catch(Throwable ex){BossBalanceTests.error("actual_callsite_laser","gauntlet",ex);}
        try {
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity boss=BossBalanceTests.damageBoss(level,"twilightforest:naga");
            Class<?> clazz=Class.forName("twilightforest.entity.boss.NagaSegment");
            Object segment=clazz.getConstructor(boss.getClass()).newInstance(boss);
            Method method=clazz.getDeclaredMethod("collideWithEntity",Entity.class);method.setAccessible(true);
            double before=victim.getHealth();method.invoke(segment,victim);
            BossBalanceTests.result("actual_callsite_body_collision","twilightforest:naga",.45,(before-victim.getHealth())/2);boss.discard();
        }catch(Throwable ex){BossBalanceTests.error("actual_callsite_body_collision","naga",ex);}
    }

    private static void controls(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        try {
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity zombie=BossBalanceTests.damageBoss(level,"minecraft:zombie");
            double before=victim.getHealth();victim.hurt(level.damageSources().mobAttack(zombie),5f);
            BossBalanceTests.result("nonboss_damage_unchanged","minecraft:zombie",.351,(before-victim.getHealth())/2);zombie.discard();
        }catch(Throwable ex){BossBalanceTests.error("nonboss_damage_unchanged","minecraft:zombie",ex);}
        try {
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity lich=BossBalanceTests.damageBoss(level,"bosses_of_mass_destruction:lich");
            Entity projectile=createDirect(level,"bosses_of_mass_destruction:blue_fireball",victim);
            DamageSource reflected=level.damageSources().thrown(projectile,victim);
            double before=victim.getHealth();victim.hurt(reflected,9f);
            BossBalanceTests.result("player_reflected_projectile_unchanged","bosses_of_mass_destruction:blue_fireball",.44064,(before-victim.getHealth())/2);projectile.discard();lich.discard();
        }catch(Throwable ex){BossBalanceTests.error("reflected_projectile","player",ex);}
        try {
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity lich=BossBalanceTests.damageBoss(level,"bosses_of_mass_destruction:lich");
            DamageSource explosion=level.damageSources().explosion(lich,lich);double before=victim.getHealth();victim.hurt(explosion,2f);
            BossBalanceTests.result("weak_explosion_not_inflated","bosses_of_mass_destruction:lich",.12096,(before-victim.getHealth())/2);lich.discard();
        }catch(Throwable ex){BossBalanceTests.error("weak_explosion","lich",ex);}
        try {
            LivingEntity naga=BossBalanceTests.damageBoss(level,"twilightforest:naga");LivingEntity cow=BossBalanceTests.damageBoss(level,"minecraft:cow");cow.setInvulnerable(false);
            double before=cow.getHealth();cow.hurt(level.damageSources().mobAttack(naga),5f);
            BossBalanceTests.result("nonplayer_victim_unchanged","minecraft:cow",5,before-cow.getHealth());cow.discard();naga.discard();
        }catch(Throwable ex){BossBalanceTests.error("nonplayer_victim","cow",ex);}
    }
    private static void effects(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        try {
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity naga=BossBalanceTests.damageBoss(level,"twilightforest:naga");
            victim.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0));
            double before=victim.getHealth();victim.hurt(level.damageSources().mobAttack(naga),5f);
            BossBalanceTests.result("resistance_preserved","twilightforest:naga",1.2,(before-victim.getHealth())/2);naga.discard();
        }catch(Throwable ex){BossBalanceTests.error("resistance_preserved","naga",ex);}
        try {
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity hydra=BossBalanceTests.damageBoss(level,"twilightforest:hydra");
            victim.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,100,0));
            var type=level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.parse("twilightforest:hydra_fire")));
            double before=victim.getHealth();victim.hurt(new DamageSource(type,hydra),19f);
            BossBalanceTests.result("fire_resistance_preserved","twilightforest:hydra",0,(before-victim.getHealth())/2);hydra.discard();
        }catch(Throwable ex){BossBalanceTests.error("fire_resistance_preserved","hydra",ex);}
        try {
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity ignis=BossBalanceTests.damageBoss(level,"cataclysm:ignis");
            var brand=level.registryAccess().registryOrThrow(Registries.MOB_EFFECT).getHolderOrThrow(ResourceKey.create(Registries.MOB_EFFECT,ResourceLocation.parse("cataclysm:blazing_brand")));
            victim.addEffect(new MobEffectInstance(brand,100,4));
            BossBalanceTests.result("brand_v_armor","player",0,victim.getAttributeValue(Attributes.ARMOR));
            BossBalanceTests.result("brand_v_toughness","player",0,victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            double before=victim.getHealth();victim.hurt(level.damageSources().mobAttack(ignis),100/1.5f);
            BossBalanceTests.result("brand_v_heavy_six_heart_limit","cataclysm:ignis",6,(before-victim.getHealth())/2);ignis.discard();
        }catch(Throwable ex){BossBalanceTests.error("brand_v","ignis",ex);}
        try {
            BossBalanceTests.resetDamagePlayer(victim);LivingEntity naga=BossBalanceTests.damageBoss(level,"twilightforest:naga");
            for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))victim.setItemSlot(slot,ItemStack.EMPTY);
            BossBalanceTests.updateEquipment(victim);double before=victim.getHealth();victim.hurt(level.damageSources().mobAttack(naga),5f);
            BossBalanceTests.result("unarmored_not_globally_capped","twilightforest:naga",1,(before-victim.getHealth())/2>6?1:0);naga.discard();
        }catch(Throwable ex){BossBalanceTests.error("unarmored","naga",ex);}
    }
    private static Entity createDirect(ServerLevel level,String id,Entity owner) throws Exception {
        ResourceLocation key=ResourceLocation.parse(id);
        if(!BuiltInRegistries.ENTITY_TYPE.containsKey(key))throw new IllegalArgumentException("Missing projectile type "+id);
        Entity entity=BuiltInRegistries.ENTITY_TYPE.get(key).create(level);if(entity==null)throw new IllegalStateException("Null projectile "+id);
        entity.moveTo(8,220,6,0,0);if(entity instanceof Projectile projectile)projectile.setOwner(owner);
        return entity;
    }
    private static void setField(Object object,String name,Object value) throws Exception {
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static void call(Object object,String name,Class<?> type,Object value) throws Exception {object.getClass().getMethod(name,type).invoke(object,value);}
    private static Method findMethod(Class<?> c,String name,Class<?>...args){for(;c!=null;c=c.getSuperclass())try{return c.getDeclaredMethod(name,args);}catch(NoSuchMethodException ignored){}return null;}
    private static void setAnimation(Entity entity,String contains) throws Exception {
        for(Field field:entity.getClass().getFields())if(java.lang.reflect.Modifier.isStatic(field.getModifiers())&&field.getType().getSimpleName().equals("Animation")&&field.getName().contains(contains)){
            Object animation=field.get(null);entity.getClass().getMethod("setAnimation",field.getType()).invoke(entity,animation);return;
        }
        throw new IllegalArgumentException("No animation containing "+contains+" on "+entity.getClass().getName());
    }
}

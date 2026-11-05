package survival.balance.tests;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Test-only calls to installed Cataclysm attack implementations.
 * These are controlled hitbox/attack-method tests, not full autonomous AI battles.
 * No victim.hurt calls and no captured raw damage inputs are used here: the actual
 * boss calculates damage from its attributes/config and this 20-max-HP player.
 */
final class ActualCataclysmAttacks {
    private ActualCataclysmAttacks() {}
    private static final Class<?> F=float.class, I=int.class, D=double.class, Z=boolean.class;
    private static final Class<?>[] GUARDIAN={F,F,F,F,F,I,I,F,Z};
    private static final Class<?>[] REMNANT={F,F,F,F,F,I,I};
    private static final Class<?>[] MALEDICTUS={F,F,F,F,F,I,Z,Z};
    private static final Class<?>[] IGNIS={F,F,F,F,F,I,I,I,I,Z,F};

    @FunctionalInterface private interface Attack { void perform(LivingEntity boss,BossBalanceTests.TestPlayer player) throws Exception; }

    static void run(ServerLevel level,BossBalanceTests.TestPlayer player) {
        test(level,player,"monstrosity_smash","cataclysm:netherite_monstrosity",BalanceExpectations.target("netherite_monstrosity.smash"),
            "Netherite_Monstrosity_Entity.EarthQuake(6.25); native smash radius/formula; player 2 blocks in front",
            (boss,victim)->invoke(boss,"EarthQuake",new Class<?>[]{D},6.25d));

        test(level,player,"guardian_gravity_punch","cataclysm:ender_guardian",BalanceExpectations.target("ender_guardian.punch"),
            "Ender_Guardian_Entity.AreaAttack(5.15,5,70,1,GravityPunchHpdamage,100,0,0,false); native gravity-punch AI parameters",
            (boss,victim)->invoke(boss,"AreaAttack",GUARDIAN,5.15f,5f,70f,1f,
                config("EnderGuardian","GravityPunchHpdamage"),100,0,0f,false));
        test(level,player,"guardian_uppercut","cataclysm:ender_guardian",BalanceExpectations.target("ender_guardian.uppercut"),
            "GUARDIAN_UPPERCUT_AND_BULLET animation; AreaAttack(6.25,7,60,1.4,UppercutHpdamage,150,60,0.5,false); native uppercut parameters",
            (boss,victim)->{
                animation(boss,"GUARDIAN_UPPERCUT_AND_BULLET");
                invoke(boss,"AreaAttack",GUARDIAN,6.25f,7f,60f,1.4f,
                    config("EnderGuardian","UppercutHpdamage"),150,60,.5f,false);
            });

        test(level,player,"harbinger_charge_collision","cataclysm:the_harbinger",BalanceExpectations.target("harbinger.charge"),
            "The_Harbinger_Entity.blockbreak(); setIsCharge(true), tickCount=40; player overlaps native inflated charge hitbox; native random/max-HP formula",
            (boss,victim)->{
                invoke(boss,"setIsCharge",new Class<?>[]{Z},true);
                victim.moveTo(8.3,220,8.3,180,0);
                invoke(boss,"blockbreak",new Class<?>[0]);
            });

        test(level,player,"remnant_bite","cataclysm:ancient_remnant",BalanceExpectations.target("ancient_remnant.bite"),
            "Ancient_Remnant_Entity.AreaAttack(7,7,70,1.35,HpDamage,160,0); native bite AI parameters",
            (boss,victim)->invoke(boss,"AreaAttack",REMNANT,7f,7f,70f,1.35f,
                config("AncientRemnant","HpDamage"),160,0));
        test(level,player,"remnant_charge_collision","cataclysm:ancient_remnant",BalanceExpectations.target("ancient_remnant.charge"),
            "Ancient_Remnant_Entity.Charge(); tickCount=40; player overlaps native body hitbox; native attack*2 and max-HP formula",
            (boss,victim)->{
                victim.moveTo(8.3,220,8.3,180,0);
                invoke(boss,"Charge",new Class<?>[0]);
            });

        test(level,player,"leviathan_grab_bite","cataclysm:the_leviathan",BalanceExpectations.target("leviathan.bite"),
            "The_Leviathan_Entity.biteattack(6.5,1.5,1.5,1.5,100); native grab-bite raytrace and AI parameters; player in ray",
            (boss,victim)->{
                animation(boss,"LEVIATHAN_GRAB_BITE");
                invoke(boss,"biteattack",new Class<?>[]{F,D,D,D,I},6.5f,1.5d,1.5d,1.5d,100);
            });

        test(level,player,"scylla_melee","cataclysm:scylla",BalanceExpectations.target("scylla.melee"),
            "Scylla_Entity.AreaAttack(3.25,6,100,1,80,true); native melee AI parameters and max-HP formula",
            (boss,victim)->invoke(boss,"AreaAttack",new Class<?>[]{F,F,F,F,I,Z},3.25f,6f,100f,1f,80,true));

        for(int rage:new int[]{0,5}) {
            test(level,player,"maledictus_mace_rage_"+rage,"cataclysm:maledictus",rage==0?3:5,
                "Maledictus_Entity.AreaAttack(5.5,5.5,270,1,SmashHpDamage,200,false,false); native mace AI parameters; initial rage="+rage,
                (boss,victim)->{
                    invoke(boss,"setRageMeter",new Class<?>[]{I},rage);
                    invoke(boss,"AreaAttack",MALEDICTUS,5.5f,5.5f,270f,1f,
                        config("Maledictus","SmashHpDamage"),200,false,false);
                });
        }

        test(level,player,"ignis_opening_sword","cataclysm:ignis",BalanceExpectations.target("ignis.sword"),
            "Ignis_Entity.AreaAttack(6.5,6,70,1,0.05,80,2,200,5,false,0); native SWING_ATTACK parameters; Brand absent before hit and applied afterward",
            (boss,victim)->{
                animation(boss,"SWING_ATTACK");
                invoke(boss,"AreaAttack",IGNIS,6.5f,6f,70f,1f,.05f,80,2,200,5,false,0f);
            });
        test(level,player,"ignis_heavy_sword","cataclysm:ignis",BalanceExpectations.target("ignis.heavy"),
            "Ignis_Entity.AreaAttack(6,6,45,2,0.25,300,5,200,7,false,0); native heavy attack parameters; Brand absent before hit",
            (boss,victim)->invoke(boss,"AreaAttack",IGNIS,6f,6f,45f,2f,.25f,300,5,200,7,false,0f));
        test(level,player,"ignis_sword_with_brand_v","cataclysm:ignis",BalanceExpectations.brandOpening(5),
            "Native Ignis AreaAttack opening swing with pre-existing Blazing Brand V; gear is still plain diamond, effective armor/toughness debuffed to zero",
            (boss,victim)->{
                victim.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.getHolder(
                    ResourceLocation.parse("cataclysm:blazing_brand")).orElseThrow(),200,4));
                BossBalanceTests.captureEffectiveDefenses(victim);
                animation(boss,"SWING_ATTACK");
                invoke(boss,"AreaAttack",IGNIS,6.5f,6f,70f,1f,.05f,80,2,200,5,false,0f);
            });
    }

    private static void test(ServerLevel level,BossBalanceTests.TestPlayer player,String name,String id,
            double expected,String description,Attack attack) {
        LivingEntity boss=null;
        try {
            BossBalanceTests.resetActualPlayer(player);
            player.stopRiding(); player.clearFire(); player.setDeltaMovement(Vec3.ZERO);
            player.moveTo(8,220,10,180,0);
            boss=BossBalanceTests.damageBoss(level,id);
            boss.moveTo(8,220,8,0,0); boss.yBodyRot=0; boss.yHeadRot=0; boss.setXRot(0);
            boss.tickCount=40; ((Mob)boss).setTarget(player);
            BossBalanceTests.result("actual_"+name+"_maximum_hp",id,20,player.getMaxHealth());
            BossBalanceTests.result("actual_"+name+"_initial_armor",id,20,player.getAttributeValue(Attributes.ARMOR));
            BossBalanceTests.result("actual_"+name+"_initial_toughness",id,8,player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            float before=player.getHealth();
            attack.perform(boss,player);
            BossBalanceTests.observedAttack("actual_"+name,id,description,player,before,expected);
        } catch(Throwable error) {
            Throwable cause=error instanceof java.lang.reflect.InvocationTargetException && error.getCause()!=null ? error.getCause():error;
            BossBalanceTests.error("actual_"+name,id,cause);
        } finally {
            if(boss!=null)boss.discard();
            player.stopRiding();player.clearFire();
        }
    }

    private static float config(String boss,String field) throws Exception {
        return ((Number)Class.forName("com.github.L_Ender.cataclysm.config.CMCommonConfig$"+boss)
            .getField(field).get(null)).floatValue();
    }
    /** Shared native recipes for the separate netherite Brand sweep. No victim damage is synthesized. */
    static void ignisProbe(LivingEntity boss,String attack) throws Exception {
        switch(attack) {
            case "opening" -> {
                animation(boss,"SWING_ATTACK");
                invoke(boss,"AreaAttack",IGNIS,6.5f,6f,70f,1f,.05f,80,2,200,5,false,0f);
            }
            case "heavy" -> {
                animation(boss,"SHIELD_SMASH_ATTACK");
                invoke(boss,"setAnimationTick",new Class<?>[]{I},34);
                // Exact installed frame-34 melee component; its separate ShieldSmashDamage wave is not invoked.
                invoke(boss,"AreaAttack",IGNIS,4.85f,2.5f,45f,1.5f,.15f,200,0,0,5,false,0f);
            }
            case "ultimate" -> {
                invoke(boss,"setBossPhase",new Class<?>[]{I},2);
                invoke(boss,"setIsShieldBreak",new Class<?>[]{Z},true);
                animation(boss,"ULTIMATE_ATTACK");
                invoke(boss,"setAnimationTick",new Class<?>[]{I},74);
                boss.aiStep();
            }
            default -> throw new IllegalArgumentException(attack);
        }
    }
    private static void animation(LivingEntity boss,String name) throws Exception {
        Field field=boss.getClass().getField(name);
        Object animation=field.get(null);
        invoke(boss,"setAnimation",new Class<?>[]{field.getType()},animation);
    }
    private static Object invoke(Object receiver,String name,Class<?>[] arguments,Object... values) throws Exception {
        Class<?> type=receiver.getClass();
        while(type!=null) {
            try {
                Method method=type.getDeclaredMethod(name,arguments);method.setAccessible(true);
                return method.invoke(receiver,values);
            } catch(NoSuchMethodException ignored) {type=type.getSuperclass();}
        }
        throw new NoSuchMethodException(receiver.getClass().getName()+"."+name);
    }
}

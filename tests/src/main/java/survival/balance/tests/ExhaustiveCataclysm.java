package survival.balance.tests;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Installed Cataclysm attack-frame probes. No manufactured damage inputs.
 *
 * Every declared non-death animation / numbered combat state is exercised in
 * the listed phase contexts. Native matching goal callbacks and aiStep execute
 * each frame. Player HP, defenses, effects, hurt cooldown and position are reset
 * between frames, making these independent contact probes, NOT combo DPS or a
 * continuous fight. Naturally emitted projectiles/effects receive separate
 * native impact/tick probes. A no-hit row is an explicit coverage gap, not a pass.
 * Selection/availability, movement trajectories, summons, environment fire and
 * every random pattern are not established merely by enumerating animation IDs.
 */
final class ExhaustiveCataclysm {
    private ExhaustiveCataclysm() {}
    private static final Class<?>[] NONE = {};
    private static final String PREFIX = "cataclysm:";
    private static final String[] MONSTRO = {"idle","sleep","awake","smash","phase","death","fire","drain","shoulder_check","overpower","flare_shot"};
    private static final String[] REMNANT = {"idle","sleep","awaken","death","right_bite","three_tail_hit","sandstorm_roar","phase_roar","right_stomp","left_stomp","charge_prepare","charge","charge_stun","right_double_stomp","left_double_stomp","ground_tail","tail_swing","monolith"};
    private static final String[] MALEDICTUS = {"idle","swing","shot","flying_shot","fall_loop","fall_land","death","mass_effect","flying_smash1","flying_smash2","backstep","backstep_rush","rush_without_backstep","dash1","dash1_no_backstep","dash2","dash2_no_backstep","dash3","spin_slashes","combo_first","combo_first_end","combo_second","uppercut_right","uppercut_left","flying_halberd_smash1","flying_halberd_smash2","radagon","halberd_swing","grab_start","grab_loop","grab_fail","grab_success","grab_success_loop","grab_success_end"};
    private static final String[] SCYLLA = {"idle","cross_swing","cross_swing2","double_swing","swing_smash","smash","backstep","spin","wave","lightning_explosion","lightning_spear","water_spear","death","anchor_shot","anchor_pull","chain_jump1","chain_jump2","chain_jump3","anchor_explosion","parry","electric_whip","enchant1","enchant2","spawn_idle","spawn","whip_and_spear","summon_snake","grab_smash"};
    private record Context(String label, float healthFraction, int value, boolean alternate) {}
    private record Move(String name, Object animation, Integer state, int duration, List<Goal> goals) {}
    private record Emission(Entity entity, int frame) {}

    static void run(ServerLevel level, BossBalanceTests.TestPlayer victim) {
        phaseBoundaries(level,victim);
        rageAndBrand(level,victim);
        ownerStateRegression(level,victim);
        directionalSubhits(level,victim);
        tongueContact(level,victim);
        if(ExhaustiveCapture.selected(PREFIX+"scylla"))runScyllaAnchorGround(level,victim);
        if(ExhaustiveCapture.selected(PREFIX+"scylla"))runScyllaSpearThrow(level,victim);
        animated(level,victim,"ender_guardian",List.of(new Context("helmet",1,0,false),new Context("helmetless",.45f,1,false)));
        internal(level,victim,"netherite_monstrosity",MONSTRO,List.of(new Context("normal",1,0,false),new Context("berserk",.35f,1,false)));
        animated(level,victim,"the_harbinger",List.of(new Context("missile_normal",1,0,false),new Context("laser_normal",1,0,true),new Context("missile_powered",.4f,1,false),new Context("laser_powered",.4f,1,true)));
        internal(level,victim,"ancient_remnant",REMNANT,List.of(new Context("normal",1,0,false),new Context("powered",.4f,1,false)));
        animated(level,victim,"the_leviathan",List.of(new Context("normal",1,0,false),new Context("meltdown",.4f,1,false)));
        internal(level,victim,"scylla",SCYLLA,List.of(new Context("phase0",1,0,false),new Context("phase1",.6f,1,false),new Context("phase2",.25f,2,false)));
        internal(level,victim,"maledictus",MALEDICTUS,List.of(new Context("full_health_rage0",1,0,false),new Context("half_health_rage0",.4f,0,false),new Context("quarter_health_rage5",.2f,5,false)));
        animated(level,victim,"ignis",List.of(new Context("phase0",1,0,false),new Context("phase1",.6f,1,false),new Context("phase2_shield_broken",.25f,2,true)));
    }

    private static void animated(ServerLevel level, BossBalanceTests.TestPlayer victim,String shortId,List<Context> contexts) {
        String id=PREFIX+shortId;
        if(!ExhaustiveCapture.selected(id)) return;
        for(Context context:contexts) {
            Mob inventory=null;
            try {
                inventory=spawn(level,victim,id,context);
                List<Field> animations=new ArrayList<>();
                for(Field field:inventory.getClass().getDeclaredFields()) {
                    if(Modifier.isStatic(field.getModifiers()) && field.getType().getSimpleName().equals("Animation")) animations.add(field);
                }
                animations.sort(Comparator.comparing(Field::getName));
                for(Field field:animations) {
                    if(field.getName().equals("DEATH_ANIMATION") || field.getName().endsWith("_DEATH") || field.getName().equals("GUARDIAN_FALLEN")) {
                        ExhaustiveCapture.note(id,field.getName(),"mechanic","Death animation excluded from attacks against a living player.");
                        continue;
                    }
                    field.setAccessible(true);
                    Object animation=field.get(null);
                    int duration=((Number)invoke(animation,"getDuration",NONE)).intValue();
                    scenario(level,victim,id,context,field.getName(),animation,null,duration,2);
                }
            } catch(Throwable error) { report(id,"animation_inventory_"+context.label,error); }
            finally {if(inventory!=null) inventory.discard();}
        }
    }

    private static void internal(ServerLevel level,BossBalanceTests.TestPlayer victim,String shortId,String[] names,List<Context> contexts) {
        String id=PREFIX+shortId;
        if(!ExhaustiveCapture.selected(id)) return;
        for(Context context:contexts) for(int state=1;state<names.length;state++) {
            String name=names[state];
            if(shortId.equals("ancient_remnant")&&state==5) {
                ExhaustiveCapture.note(id,"state5_three_tail_hit","mechanic","Dormant animation entry: installed registerGoals and aiStep contain no state5 attack routine. Active tail_swing and ground_tail have separate probes.");
                continue;
            }
            if(name.equals("death") || name.equals("sleep") || name.startsWith("spawn")) {
                ExhaustiveCapture.note(id,"state"+state+"_"+name,"mechanic","Non-combat lifecycle state inventoried; not tested as an attack.");
                continue;
            }
            scenario(level,victim,id,context,"state"+state+"_"+name,null,state,120,2);
            // Ground shockwaves expand away from the body; probe their outer hitbox independently.
            if(name.contains("stomp") || name.equals("smash") || name.equals("overpower") || name.equals("ground_tail"))
                scenario(level,victim,id,context,"state"+state+"_"+name+"_outer8",null,state,120,8);
            if(shortId.equals("scylla") && state==7)
                scenario(level,victim,id,context,"state7_spin_ring5_5",null,state,120,5.5);
            if(shortId.equals("scylla") && state==17)
                scenario(level,victim,id,context,"state17_chain_jump3_body_contact",null,state,120,.2);
        }
    }

    private static void scenario(ServerLevel level,BossBalanceTests.TestPlayer victim,String id,Context context,
            String name,Object animation,Integer state,int fallbackDuration,double distance) {
        String label=context.label+"/"+name;
        Mob boss=null;
        Set<UUID> baseline=snapshot(level);
        Map<String,Emission> emitted=new LinkedHashMap<>();
        Map<String,Integer> emissionCounts=new LinkedHashMap<>();
        List<ExhaustiveCapture.Hit> observed=List.of();
        try {
            invoke(victim,"useNativeRiding",new Class<?>[]{boolean.class},true);
            floor(level);
            boss=spawn(level,victim,id,context);
            List<Goal> goals=matchingGoals(boss,animation,state);
            int duration=animation!=null ? fallbackDuration : goalDuration(goals,fallbackDuration);
            Move move=new Move(name,animation,state,Math.min(Math.max(duration,1),260),goals);
            setMove(boss,move,0);
            for(Goal goal:goals) goal.start();
            setMove(boss,move,0);
            boolean rider=name.contains("POKED_ATTACK") || name.contains("TENTACLE_HOLD_BLAST") || name.contains("grab_success");
            ExhaustiveCapture.note(id,label,"setup","Native goal start/tick plus aiStep; frames 0.."+move.duration+" inclusive; pose front "+distance+" blocks; phase staged: "+context+". Independent reference player reset between frames. Matching goal classes: "+goals.stream().map(g->g.getClass().getName()).toList());
            ExhaustiveCapture.begin(id,label+"/native_frames",victim);
            for(int frame=0;frame<=move.duration;frame++) {
                reset(victim,boss,distance);
                boss.tickCount=40+frame;
                setMove(boss,move,frame);
                if(rider) victim.startRiding(boss,true);
                for(Goal goal:goals) goal.tick();
                // Goal movement is not part of this stationary contact probe.
                orient(boss); boss.setDeltaMovement(Vec3.ZERO);
                boss.aiStep();
                if(id.endsWith(":the_harbinger")) invoke(boss,"blockbreak",NONE);
                if(victim.getVehicle()==boss) boss.positionRider(victim);
                harvest(level,baseline,boss,emitted,emissionCounts,frame);
            }
            observed=ExhaustiveCapture.end();
            if(observed.isEmpty()) ExhaustiveCapture.note(id,label,"uncovered","No direct contact damage in this pose. Emitted effects are tested separately; a movement/landing/grab prerequisite may still be missing.");
            ExhaustiveCapture.note(id,label,"emissions",emissionCounts.toString());
            for(Map.Entry<String,Emission> entry:emitted.entrySet()) {
                Entity effect=entry.getValue().entity;
                if(effect.isRemoved()) continue;
                setMove(boss,move,entry.getValue().frame);
                impact(level,victim,boss,id,label+"/emitted_"+entry.getKey(),effect,baseline,false);
            }
            // Harbinger's ordinary ranged attack is outside its named special animations.
            if(id.endsWith(":the_harbinger") && name.equals("STUN_ANIAMATION")) {
                reset(victim,boss,10);
                Map<String,Emission> ranged=new LinkedHashMap<>(); Map<String,Integer> count=new LinkedHashMap<>();
                invoke(boss,"performRangedAttack",new Class<?>[]{LivingEntity.class,float.class},victim,1f);
                harvest(level,baseline,boss,ranged,count,0);
                for(Map.Entry<String,Emission> entry:ranged.entrySet()) impact(level,victim,boss,id,context.label+"/ordinary_ranged/"+entry.getKey(),entry.getValue().entity,baseline,false);
            }
        } catch(Throwable error) { report(id,label,error); }
        finally {
            ExhaustiveCapture.end();
            victim.stopRiding();
            for(Entity entity:newEntities(level,baseline)) if(entity!=victim) entity.discard();
            if(boss!=null) boss.discard();
            try {BossBalanceTests.resetActualPlayer(victim);}catch(Exception error){report(id,label+"_reset",error);}
            try {invoke(victim,"useNativeRiding",new Class<?>[]{boolean.class},false);}catch(Exception error){report(id,label+"_riding_reset",error);}
        }
    }

    private static void impact(ServerLevel level,BossBalanceTests.TestPlayer victim,Mob boss,String id,String label,
            Entity effect,Set<UUID> baseline,boolean nested) {
        Set<UUID> impactBaseline=snapshot(level);
        try {
            reset(victim,boss,2);
            // Preserve native emitted entity, configured damage and owner; stage contact only.
            if(effect instanceof Projectile && !type(effect).equals(PREFIX+"earthquake")) {
                armDelayedProjectile(effect,victim);
                reset(victim,boss,2);
                effect.setPos(victim.getX(),victim.getY()+.6,victim.getZ());
                ExhaustiveCapture.note(id,label,"setup","Native emitted projectile onHit(EntityHitResult); owner retains emission animation/state. Native direct damage/explosion ordering and hurt cooldown retained within this impact; no trajectory test.");
                ExhaustiveCapture.begin(id,label+"/onHit",victim);
                if(type(effect).equals(PREFIX+"spark")) {
                    effect.setPos(victim.getX(),220,victim.getZ());
                    invoke(effect,"onHit",new Class<?>[]{HitResult.class},new BlockHitResult(effect.position(),Direction.UP,BlockPos.containing(effect.getX(),219,effect.getZ()),false));
                } else invoke(effect,"onHit",new Class<?>[]{HitResult.class},new EntityHitResult(victim));
                List<ExhaustiveCapture.Hit> hits=ExhaustiveCapture.end();
                if(hits.isEmpty()) ExhaustiveCapture.note(id,label,"uncovered","Native onHit produced no accepted player damage; emission alone does not establish damaging coverage.");
            } else if(type(effect).equals(PREFIX+"the_leviathan_tongue")) {
                ExhaustiveCapture.begin(id,label+"/native_grab_contact",victim);
                invoke(effect,"hurtEntity",new Class<?>[]{LivingEntity.class,Entity.class},boss,victim);
                ExhaustiveCapture.end();
            } else if(isDamageEffect(effect)) {
                if(type(effect).equals(PREFIX+"ancient_desert_stele")) {
                    victim.moveTo(effect.getX()+2,effect.getY(),effect.getZ(),180,0);
                    ExhaustiveCapture.begin(id,label+"/native_impact_radius_alternative",victim);
                    invoke(effect,"dealImpactDamage",new Class<?>[]{ServerLevel.class},level);
                    ExhaustiveCapture.end();
                    ExhaustiveCapture.note(id,label,"setup","Stele impact-radius alternative invoked before piercing; native excludes a previously pierced victim. These are alternative contacts, not two hits guaranteed on one victim.");
                }
                int maximumTicks=(type(effect).contains("rift")||type(effect).contains("vortex"))?360:140;
                ExhaustiveCapture.note(id,label,"setup","Native emitted effect tick, up to "+maximumTicks+" ticks; reference player reset between ticks and positioned in its hitbox/beam. Reports contacts, not continuous DOT DPS.");
                ExhaustiveCapture.begin(id,label+"/effect_ticks",victim);
                for(int tick=0;tick<maximumTicks&&!effect.isRemoved();tick++) {
                    reset(victim,boss,2);
                    String type=type(effect);
                    if((type.contains("blast")&&!type.equals(PREFIX+"abyss_blast_portal")) || type.contains("laser_beam")) {
                        // Use the installed beam's own yaw/pitch and native updated origin.
                        double yaw=((Number)invoke(effect,"getYaw",NONE)).doubleValue();
                        double pitch=((Number)invoke(effect,"getPitch",NONE)).doubleValue();
                        victim.moveTo(effect.getX()+8*Math.cos(yaw)*Math.cos(pitch),effect.getY()+8*Math.sin(pitch)-.8,
                            effect.getZ()+8*Math.sin(yaw)*Math.cos(pitch),180,0);
                        boss.setXRot(0);boss.yHeadRot=0;
                    } else if(type.equals(PREFIX+"storm_serpent")) {
                        double yaw=Math.toRadians(effect.getYRot());
                        victim.moveTo(effect.getX()+8*Math.cos(yaw),effect.getY()+.05,effect.getZ()+8*Math.sin(yaw),180,0);
                    } else {
                        Vec3 ahead=effect.position();
                        if(type.equals(PREFIX+"earthquake")) ahead=ahead.add(effect.getDeltaMovement());
                        victim.moveTo(ahead.x,ahead.y+.05,ahead.z,180,0);
                    }
                    nativeEntityTick(effect);
                }
                List<ExhaustiveCapture.Hit> hits=ExhaustiveCapture.end();
                if(hits.isEmpty()) ExhaustiveCapture.note(id,label,"uncovered","Effect emitted and ticked but no accepted player damage; geometry/state may need a dedicated fixture.");
                if(type(effect).contains("rift")&&!effect.isRemoved()) {
                    invoke(effect,"setLifespan",new Class<?>[]{int.class},1);
                    ExhaustiveCapture.note(id,label+"/closing","setup","Native rift normally receives a600-tick lifespan. After contact probes, remaining lifespan is staged to1; native stage decay and collapse explosion tick unmodified. This exercises expiry damage without claiming a full timed fight.");
                    ExhaustiveCapture.begin(id,label+"/native_expiry_collapse_staged_lifespan",victim);
                    for(int tick=0;tick<200&&!effect.isRemoved();tick++) {
                        reset(victim,boss,2);victim.moveTo(effect.getX(),effect.getY()+.05,effect.getZ(),180,0);
                        nativeEntityTick(effect);
                    }
                    ExhaustiveCapture.end();
                }
            } else {
                ExhaustiveCapture.note(id,label,"uncovered","Emitted "+effect.getClass().getName()+"; summon, debris or special mechanic requires separate coverage. It was not treated as a damaging hit.");
            }
            if(!nested) {
                // Process new damaging child effects created by an impact (e.g. shell area cloud).
                Map<String,Entity> children=new LinkedHashMap<>();
                for(Entity child:newEntities(level,impactBaseline)) if(child!=boss&&child!=effect&&!child.isRemoved()) {
                    if(isDamageEffect(child))children.putIfAbsent(type(child),child);
                    else if(!type(child).contains("screen_shake")&&!type(child).contains("falling_block")&&!type(child).equals("minecraft:item"))
                        ExhaustiveCapture.note(id,label+"/child_"+type(child),"uncovered","Impact spawned child "+child.getClass().getName()+" with no specialized fixture; retained in coverage report.");
                }
                for(Map.Entry<String,Entity> child:children.entrySet()) if(child.getValue()!=effect)
                    impact(level,victim,boss,id,label+"/child_"+child.getKey(),child.getValue(),baseline,true);
            }
        } catch(Throwable error) {report(id,label,error);}
        finally {ExhaustiveCapture.end();effect.discard();victim.stopRiding();for(Entity child:newEntities(level,impactBaseline))if(child!=victim&&child!=boss)child.discard();}
    }

    private static boolean isDamageEffect(Entity entity) {
        String id=type(entity);
        return id.startsWith(PREFIX) && (id.contains("rune") || id.contains("flame_jet") || id.contains("flame_strike")
            || id.contains("sandstorm") || id.contains("blast") || id.contains("laser_beam") || id.contains("halberd")
            || id.contains("lightning") || id.contains("wave") || id.contains("rift") || id.contains("cloud") || id.contains("monolith") || id.contains("mine")
            || id.contains("stele") || id.contains("vortex") || id.equals(PREFIX+"earthquake") || id.equals(PREFIX+"storm_serpent") || id.contains("smoke"));
    }

    private static void harvest(ServerLevel level,Set<UUID> baseline,Mob boss,Map<String,Emission> emitted,Map<String,Integer> counts,int frame) {
        Set<UUID> retained=new HashSet<>();
        emitted.values().forEach(value->retained.add(value.entity.getUUID()));
        for(Entity entity:newEntities(level,baseline)) {
            if(entity==boss||entity instanceof BossBalanceTests.TestPlayer||retained.contains(entity.getUUID())) continue;
            String type=type(entity);
            if(type.contains("screen_shake") || type.contains("falling_block")) {entity.discard();continue;}
            counts.merge(type,1,Integer::sum);
            if(!emitted.containsKey(type)) emitted.put(type,new Emission(entity,frame));
            else entity.discard();
        }
    }

    private static Mob spawn(ServerLevel level,BossBalanceTests.TestPlayer victim,String id,Context context) throws Exception {
        Mob boss=(Mob)BossBalanceTests.damageBoss(level,id);
        boss.moveTo(8,220,8,0,0);orient(boss);
        boss.setTarget(victim);boss.setOnGround(true);boss.setNoAi(true);boss.setNoGravity(true);
        boss.setHealth(boss.getMaxHealth()*context.healthFraction);
        if(id.endsWith(":ender_guardian")) invoke(boss,"setIsHelmetless",new Class<?>[]{boolean.class},context.value>0);
        if(id.endsWith(":netherite_monstrosity")) {invoke(boss,"setIsAwaken",new Class<?>[]{boolean.class},true);invoke(boss,"setIsBerserk",new Class<?>[]{boolean.class},context.value>0);}
        if(id.endsWith(":the_harbinger")) {invoke(boss,"setIsAct",new Class<?>[]{boolean.class},true);invoke(boss,"setIsLaserMode",new Class<?>[]{boolean.class},context.alternate);}
        if(id.endsWith(":ancient_remnant")) {invoke(boss,"setNecklace",new Class<?>[]{boolean.class},true);invoke(boss,"setIsPower",new Class<?>[]{boolean.class},context.value>0);}
        if(id.endsWith(":the_leviathan")) invoke(boss,"setMeltDown",new Class<?>[]{boolean.class},context.value>0);
        if(id.endsWith(":scylla")) {invoke(boss,"setAct",new Class<?>[]{boolean.class},true);invoke(boss,"setPhase",new Class<?>[]{int.class},context.value);}
        if(id.endsWith(":maledictus")) invoke(boss,"setRageMeter",new Class<?>[]{int.class},context.value);
        if(id.endsWith(":ignis")) {invoke(boss,"setBossPhase",new Class<?>[]{int.class},context.value);invoke(boss,"setIsShieldBreak",new Class<?>[]{boolean.class},context.alternate);}
        level.addFreshEntity(boss);
        return boss;
    }

    private static void reset(BossBalanceTests.TestPlayer victim,Mob boss,double distance) throws Exception {
        victim.stopRiding();BossBalanceTests.resetActualPlayer(victim);
        victim.moveTo(8,220,8+distance,180,0);victim.setOnGround(true);victim.setDeltaMovement(Vec3.ZERO);
        boss.moveTo(8,220,8,0,0);orient(boss);boss.setDeltaMovement(Vec3.ZERO);boss.setOnGround(true);
        boss.setTarget(victim);boss.tickCount=40;
    }

    private static void ownerStateRegression(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        for(String id:List.of(PREFIX+"ender_guardian",PREFIX+"ancient_remnant",PREFIX+"the_leviathan")) {
            if(!ExhaustiveCapture.selected(id)) continue;
            for(boolean active:new boolean[]{false,true}) {
                Mob boss=null;Set<UUID> baseline=snapshot(level);
                String label="owner_state_regression/"+(active?"active_special":"idle");
                try {
                    boss=spawn(level,victim,id,new Context("normal",1,0,false));
                    reset(victim,boss,2);
                    Entity effect;
                    double expected;
                    if(id.endsWith("ender_guardian")) {
                        Object uppercut=boss.getClass().getField("GUARDIAN_UPPERCUT_AND_BULLET").get(null);
                        invoke(boss,"setAnimation",new Class<?>[]{uppercut.getClass()},uppercut);
                        invoke(boss,"setAnimationTick",new Class<?>[]{int.class},54);
                        invoke(boss,"Bulletpattern",NONE);
                        effect=newEntities(level,baseline).stream().filter(e->type(e).equals(PREFIX+"ender_guardian_bullet")).findFirst().orElseThrow();
                        armDelayedProjectile(effect,victim);
                        reset(victim,boss,2);
                        if(!active) invoke(boss,"setAnimation",new Class<?>[]{uppercut.getClass()},boss.getClass().getField("NO_ANIMATION").get(null));
                        expected=1;
                        ExhaustiveCapture.begin(id,label+"/native_bullet_hit",victim);
                        invoke(effect,"onHitEntity",new Class<?>[]{EntityHitResult.class},new EntityHitResult(victim));
                    } else if(id.endsWith("ancient_remnant")) {
                        invoke(boss,"setAttackState",new Class<?>[]{int.class},6);
                        field(boss.getClass(),"attackTicks").setInt(boss,55);
                        boss.aiStep();
                        effect=newEntities(level,baseline).stream().filter(e->type(e).equals(PREFIX+"sandstorm")).findFirst().orElseThrow();
                        invoke(boss,"setAttackState",new Class<?>[]{int.class},active?11:0);
                        expected=5.25;
                        ExhaustiveCapture.begin(id,label+"/native_sandstorm_contact",victim);
                        invoke(effect,"damage",new Class<?>[]{LivingEntity.class},victim);
                    } else {
                        Object summon=boss.getClass().getField("LEVIATHAN_BREAK_DIMENSION").get(null);
                        invoke(boss,"setAnimation",new Class<?>[]{summon.getClass()},summon);
                        invoke(boss,"setAnimationTick",new Class<?>[]{int.class},24);
                        for(Goal goal:matchingGoals(boss,summon,null))goal.tick();
                        effect=newEntities(level,baseline).stream().filter(e->type(e).equals(PREFIX+"dimensional_rift")).findFirst().orElseThrow();
                        Object current=boss.getClass().getField(active?"LEVIATHAN_BITE":"NO_ANIMATION").get(null);
                        invoke(boss,"setAnimation",new Class<?>[]{current.getClass()},current);
                        effect.tickCount=5;
                        expected=7.5;
                        ExhaustiveCapture.begin(id,label+"/native_rift_contact",victim);
                        invoke(effect,"damage",new Class<?>[]{LivingEntity.class},victim);
                    }
                    List<ExhaustiveCapture.Hit> hits=ExhaustiveCapture.end();
                    BossBalanceTests.result("exhaustive_cat_"+label,id,expected,hits.stream().mapToDouble(ExhaustiveCapture.Hit::damageHp).sum()/2);
                    ExhaustiveCapture.note(id,label,"regression","Expected projectile profile is independent of simultaneous owner melee/charge animation; emitted by installed native method, then native impact/contact invoked. Expected "+expected+" hearts.");
                } catch(Throwable error) {report(id,label,error);}
                finally {ExhaustiveCapture.end();for(Entity entity:newEntities(level,baseline))if(entity!=victim)entity.discard();if(boss!=null)boss.discard();}
            }
        }
    }

    private static void armDelayedProjectile(Entity effect,BossBalanceTests.TestPlayer victim) throws Exception {
        String id=type(effect);
        if(id.equals(PREFIX+"ender_guardian_bullet")) {
            victim.moveTo(108,220,108,0,0);
            Field fired=field(effect.getClass(),"fired");
            for(int tick=0;tick<60&&!fired.getBoolean(effect)&&!effect.isRemoved();tick++)nativeEntityTick(effect);
            if(effect.isRemoved()||!fired.getBoolean(effect))throw new IllegalStateException("Native Guardian bullet failed to arm after its delay");
        } else if(id.contains("ignis") && id.contains("fireball")) {
            victim.moveTo(108,220,108,0,0);
            for(int tick=0;tick<240&&!(Boolean)invoke(effect,"getFired",NONE)&&!effect.isRemoved();tick++)nativeEntityTick(effect);
            if(effect.isRemoved()||!(Boolean)invoke(effect,"getFired",NONE))throw new IllegalStateException("Native Ignis fireball failed to arm after its delay");
        }
    }

    private static void nativeEntityTick(Entity entity) {
        // ServerLevel.tickNonPassenger normally owns this increment, not Entity.tick.
        entity.setOldPosAndRot();
        entity.tickCount++;
        entity.tick();
    }

    private static void rageAndBrand(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        String id=PREFIX+"maledictus";
        if(ExhaustiveCapture.selected(id))for(int rage=0;rage<=5;rage++) {
            Mob boss=null;Set<UUID> baseline=snapshot(level);
            String label="rage_meter_"+rage+"/native_mace_frame25";
            try {
                boss=spawn(level,victim,id,new Context("rage_sweep",1,rage,false));
                reset(victim,boss,2);
                invoke(boss,"setAttackState",new Class<?>[]{int.class},1);field(boss.getClass(),"attackTicks").setInt(boss,25);
                ExhaustiveCapture.begin(id,label,victim);boss.aiStep();
                List<ExhaustiveCapture.Hit> hits=ExhaustiveCapture.end();
                BossBalanceTests.result("exhaustive_maledictus_rage_"+rage,id,3+.4*rage,hits.stream().mapToDouble(ExhaustiveCapture.Hit::damageHp).sum()/2);
            }catch(Throwable error){report(id,label,error);}finally{ExhaustiveCapture.end();for(Entity entity:newEntities(level,baseline))if(entity!=victim)entity.discard();if(boss!=null)boss.discard();}
        }
        id=PREFIX+"ignis";
        if(ExhaustiveCapture.selected(id))for(int brand=0;brand<=5;brand++)for(boolean heavy:new boolean[]{false,true}) {
            Mob boss=null;Set<UUID> baseline=snapshot(level);
            String label="brand_"+brand+"/"+(heavy?"ultimate_frame74":"opening_sword_frame24");
            try {
                boss=spawn(level,victim,id,new Context("brand_sweep",heavy?.25f:1,heavy?2:0,heavy));
                reset(victim,boss,2);
                if(brand>0)victim.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(PREFIX+"blazing_brand")).orElseThrow(),200,brand-1));
                Object animation=boss.getClass().getField(heavy?"ULTIMATE_ATTACK":"SWING_ATTACK").get(null);
                invoke(boss,"setAnimation",new Class<?>[]{animation.getClass()},animation);
                invoke(boss,"setAnimationTick",new Class<?>[]{int.class},heavy?74:24);
                ExhaustiveCapture.begin(id,label,victim);boss.aiStep();
                List<ExhaustiveCapture.Hit> hits=ExhaustiveCapture.end();
                double hearts=hits.stream().mapToDouble(ExhaustiveCapture.Hit::damageHp).sum()/2;
                if(heavy)BossBalanceTests.result("exhaustive_ignis_heavy_brand_"+brand,id,9.5,hearts);
                else if(brand==0)BossBalanceTests.result("exhaustive_ignis_opening_no_brand",id,3.5,hearts);
                else if(brand==5)BossBalanceTests.result("exhaustive_ignis_opening_brand_v",id,BalanceExpectations.brandOpening(5),hearts);
                else BossBalanceTests.result("exhaustive_ignis_opening_brand_"+brand+"_within_cap",id,1,hearts>3.5&&hearts<=8?1:0);
                ExhaustiveCapture.note(id,label,"mechanic","Pre-existing Brand "+brand+"; original unenchanted diamond equipment retained while native armor/toughness modifiers apply. Native attack may then add another Brand stack after this hit.");
            }catch(Throwable error){report(id,label,error);}finally{ExhaustiveCapture.end();for(Entity entity:newEntities(level,baseline))if(entity!=victim)entity.discard();if(boss!=null)boss.discard();}
        }
    }

    static void runScyllaSpearThrow(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        String id=PREFIX+"scylla";
        for(int phase=0;phase<=2;phase++)for(boolean water:new boolean[]{false,true})for(boolean ground:new boolean[]{false,true}) {
            String label="standalone_spear/phase"+phase+"/"+(water?"water":"lightning")+"/"+(ground?"ground":"direct");
            Mob boss=null;Set<UUID> baseline=snapshot(level);
            try {
                floor(level);boss=spawn(level,victim,id,new Context("phase"+phase,phase==0?1:phase==1?.6f:.25f,phase,false));
                reset(victim,boss,12);invoke(boss,"setAttackState",new Class<?>[]{int.class},0);
                GoalSelector selector=(GoalSelector)field(Mob.class,"goalSelector").get(boss);
                Goal goal=selector.getAvailableGoals().stream().map(w->w.getGoal()).filter(g->g.getClass().getSimpleName().equals("SpearThrowGoal")).findFirst().orElseThrow();
                Field cooldown=field(boss.getClass(),"spear_cooldown");
                if(!ground) {
                    cooldown.setInt(boss,0);victim.moveTo(8,220,10,180,0);
                    BossBalanceTests.result("exhaustive_"+label+"_near_gate",id,0,goal.canUse()?1:0);
                    victim.moveTo(8,220,38,180,0);
                    BossBalanceTests.result("exhaustive_"+label+"_far_gate",id,0,goal.canUse()?1:0);
                    victim.moveTo(8,220,20,180,0);cooldown.setInt(boss,80);
                    BossBalanceTests.result("exhaustive_"+label+"_cooldown_gate",id,0,goal.canUse()?1:0);
                }
                victim.moveTo(8,220,20,180,0);
                int wanted=water?11:10;long selectedSeed=-1;
                for(long seed=0;seed<10000;seed++) {
                    cooldown.setInt(boss,0);invoke(boss,"setAttackState",new Class<?>[]{int.class},0);
                    boss.getRandom().setSeed(seed);
                    if(!goal.canUse())continue;
                    goal.start();
                    if(((Number)invoke(boss,"getAttackState",NONE)).intValue()==wanted){selectedSeed=seed;break;}
                    goal.stop();
                }
                if(selectedSeed<0)throw new IllegalStateException("Could not sample native spear selection branch");
                BossBalanceTests.result("exhaustive_"+label+"_native_selection",id,wanted,((Number)invoke(boss,"getAttackState",NONE)).doubleValue());
                ExhaustiveCapture.note(id,label,"setup","Native SpearThrowGoal.canUse then start selected "+(water?"water/state11":"lightning/state10")+" using sampled RNG seed "+selectedSeed+". Both branches are allowed in every phase. Gate: idle, cooldown0, living target strictly7..25 blocks,16% random check. Target is stationary12 blocks ahead. Native goal tick executes frames0..49. Seed sampling establishes branch availability without estimating natural selection frequency.");
                Map<UUID,Emission> emitted=new LinkedHashMap<>();
                List<String> launches=new ArrayList<>();
                for(int frame=0;frame<=49;frame++) {
                    field(boss.getClass(),"attackTicks").setInt(boss,frame);goal.tick();
                    for(Entity entity:newEntities(level,baseline))if((type(entity).equals(PREFIX+"lightning_spear")||type(entity).equals(PREFIX+"water_spear"))&&!emitted.containsKey(entity.getUUID())) {
                        int index=emitted.size();emitted.put(entity.getUUID(),new Emission(entity,frame));
                        double speed=entity.getDeltaMovement().length();
                        double expectedSpeed=index==0||phase==2?2:1.5;
                        BossBalanceTests.result("exhaustive_"+label+"_launch"+(index+1)+"_frame",id,index==0?27:32,frame);
                        BossBalanceTests.result("exhaustive_"+label+"_launch"+(index+1)+"_speed",id,expectedSpeed,speed);
                        BossBalanceTests.result("exhaustive_"+label+"_launch"+(index+1)+"_type",id,1,type(entity).equals(PREFIX+(water?"water_spear":"lightning_spear"))?1:0);
                        launches.add("projectile"+(index+1)+" frame="+frame+" speed="+speed+" type="+type(entity));
                    }
                }
                BossBalanceTests.result("exhaustive_"+label+"_native_volley_count",id,phase==0?1:3,emitted.size());
                ExhaustiveCapture.note(id,label,"emissions",String.join(" | ",launches)+". Phase0 launches1 spear. Phases1/2 launch3. First speed2.0, extra speeds1.5 in phase1 and2.0 in phase2. Projectiles receive independent contact probes after their native emission, preserving owner and configured damage.");
                field(boss.getClass(),"attackTicks").setInt(boss,50);
                BossBalanceTests.result("exhaustive_"+label+"_native_goal_finished",id,0,goal.canContinueToUse()?1:0);
                goal.stop();
                BossBalanceTests.result("exhaustive_"+label+"_native_returns_idle",id,0,((Number)invoke(boss,"getAttackState",NONE)).doubleValue());
                BossBalanceTests.result("exhaustive_"+label+"_native_cooldown",id,80,cooldown.getInt(boss));
                List<Emission> projectiles=new ArrayList<>(emitted.values());
                if(!ground) {
                    for(int index=0;index<projectiles.size();index++) {
                        Entity spear=projectiles.get(index).entity;
                        BossBalanceTests.resetActualPlayer(victim);victim.moveTo(8,220,20,180,0);victim.setOnGround(true);victim.setDeltaMovement(Vec3.ZERO);
                        ExhaustiveCapture.note(id,label+"/projectile"+(index+1),"setup","Native emitted spear flies from its unchanged launch position and velocity toward the stationary launch target. Each projectile receives a fresh20HP unenchanted diamond player. First accepted impact is measured using native projectile ticks.");
                        spearFirstPlayerHit(victim,id,label+"/projectile"+(index+1),spear,water);
                    }
                } else {
                    Entity spear=projectiles.get(0).entity;
                    for(int index=1;index<projectiles.size();index++)projectiles.get(index).entity.discard();
                    boss.moveTo(-16,220,-16,0,0);victim.moveTo(108,220,108,0,0);
                    spear.setPos(8,222,8);spear.setDeltaMovement(0,-2,0);
                    Set<UUID> beforeGround=snapshot(level);
                    ExhaustiveCapture.note(id,label,"setup","First native-emitted spear receives a staged downward approach to stone. Actual projectile tick executes the block collision. Extra volley spears have direct-contact probes in the paired case. Lightning child effects and the water reflection are measured separately.");
                    ExhaustiveCapture.begin(id,label+"/native_block_collision",victim);
                    int ticks=0;
                    while(ticks<8&&!spear.isRemoved()&&(!water||((Number)invoke(spear,"getTotalBounces",NONE)).intValue()==8)){nativeEntityTick(spear);ticks++;}
                    ExhaustiveCapture.end();
                    if(water) {
                        BossBalanceTests.result("exhaustive_"+label+"_native_bounces_remaining",id,7,((Number)invoke(spear,"getTotalBounces",NONE)).doubleValue());
                        BossBalanceTests.result("exhaustive_"+label+"_native_reflected_upward",id,1,spear.getDeltaMovement().y>0?1:0);
                        BossBalanceTests.resetActualPlayer(victim);victim.moveTo(spear.getX(),spear.getY()+1.2,spear.getZ(),180,0);victim.setDeltaMovement(Vec3.ZERO);
                        ExhaustiveCapture.note(id,label,"mechanic","Water spear native ground collision consumed one of8 bounces and reflected upward. The player is placed1.2 blocks above that actual bounce position. Subsequent native ticks measure the reflected spear's first accepted player hit.");
                        spearFirstPlayerHit(victim,id,label+"/reflected_contact",spear,true);
                    } else {
                        List<Entity> children=newEntities(level,beforeGround);
                        List<Entity> storms=children.stream().filter(e->type(e).equals(PREFIX+"lightning_storm")).toList();
                        List<Entity> areas=children.stream().filter(e->type(e).equals(PREFIX+"lightning_area_effect")).toList();
                        BossBalanceTests.result("exhaustive_"+label+"_native_storm_count",id,1,storms.size());
                        BossBalanceTests.result("exhaustive_"+label+"_native_area_count",id,1,areas.size());
                        for(Entity storm:storms)firstAnchorChildHit(level,victim,id,label+"/storm",storm,"Lightning_Storm_Entity.damage",7.2,.75);
                        for(Entity area:areas)firstAnchorChildHit(level,victim,id,label+"/area",area,"Lightning_Area_Effect_Entity.damage",6,.75);
                    }
                }
            }catch(Throwable error){report(id,label,error);}
            finally{ExhaustiveCapture.end();victim.stopRiding();for(Entity entity:newEntities(level,baseline))if(entity!=victim)entity.discard();if(boss!=null)boss.discard();try{BossBalanceTests.resetActualPlayer(victim);}catch(Exception error){report(id,label+"_reset",error);}}
        }
    }

    private static void spearFirstPlayerHit(BossBalanceTests.TestPlayer victim,String id,String label,Entity spear,boolean water) throws Exception {
        ExhaustiveCapture.begin(id,label+"/native_flight_hit",victim);
        int ticks=0;
        while(ticks<80&&!spear.isRemoved()&&victim.getHealth()>=20){nativeEntityTick(spear);ticks++;}
        List<ExhaustiveCapture.Hit> hits=ExhaustiveCapture.end();
        String caller=(water?"Water_Spear_Entity":"Lightning_Spear_Entity")+".onHitEntity";
        List<ExhaustiveCapture.Hit> matching=hits.stream().filter(h->h.nativeCallers().stream().anyMatch(c->c.endsWith(caller))).toList();
        BossBalanceTests.result("exhaustive_"+label+"_native_hit_caller",id,1,matching.isEmpty()?0:1);
        if(!matching.isEmpty()) {
            ExhaustiveCapture.Hit hit=matching.get(0);double hearts=water?4.305:7.5;
            BossBalanceTests.result("exhaustive_"+label+"_native_raw",id,21,hit.originalIncomingHp());
            BossBalanceTests.result("exhaustive_"+label+"_armor",id,20,hit.armor());
            BossBalanceTests.result("exhaustive_"+label+"_toughness",id,8,hit.toughness());
            BossBalanceTests.result("exhaustive_"+label+"_hearts",id,hearts,hit.damageHp()/2);
            BossBalanceTests.result("exhaustive_"+label+"_remaining_hp",id,20-2*hearts,hit.afterHp());
            BossBalanceTests.result("exhaustive_"+label+"_damage_type",id,1,hit.type().equals(water?"minecraft:mob_projectile":"cataclysm:lightning")?1:0);
        }
        if(water&&!matching.isEmpty()) {
            MobEffectInstance wetness=victim.getActiveEffects().stream().filter(e->e.getEffect().unwrapKey().map(k->k.location().getPath().contains("wetness")).orElse(false)).findFirst().orElseThrow();
            BossBalanceTests.result("exhaustive_"+label+"_wetness_duration",id,200,wetness.getDuration());
            BossBalanceTests.result("exhaustive_"+label+"_wetness_amplifier",id,0,wetness.getAmplifier());
            ExhaustiveCapture.note(id,label,"mechanic","Accepted native water-spear hit applied "+wetness.getEffect().unwrapKey().orElseThrow().location()+" for "+wetness.getDuration()+" ticks at amplifier "+wetness.getAmplifier()+". Downstream Wetness interactions are outside this first-hit check.");
        }
        ExhaustiveCapture.note(id,label,"mechanic","Native projectile tick count before first accepted contact: "+ticks+". Player HP="+victim.getHealth()+". Source velocity/damage were preserved for direct launches; ground alternatives explicitly stage the initial downward approach.");
        spear.discard();
    }

    static void runScyllaAnchorGround(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        String id=PREFIX+"scylla";
        for(int phase=0;phase<=2;phase++)for(int state:new int[]{13,15}) {
            String label="anchor_ground/phase"+phase+"/"+(state==13?"throw":"hook");
            Mob boss=null;Set<UUID> baseline=snapshot(level);
            try {
                floor(level);boss=spawn(level,victim,id,new Context("phase"+phase,phase==0?1:phase==1?.6f:.25f,phase,false));
                reset(victim,boss,12);
                List<Goal> goals=matchingGoals(boss,null,state);
                for(Goal goal:goals)goal.start();
                int frame=state==13?20:47;
                setMove(boss,new Move("anchor_ground",null,state,frame,goals),frame);
                for(Goal goal:goals)goal.tick();
                Entity anchor=newEntities(level,baseline).stream().filter(e->type(e).equals(PREFIX+"scylla_ceraunus")).findFirst().orElseThrow();
                BossBalanceTests.result("exhaustive_"+label+"_native_phase",id,phase,((Number)invoke(anchor,"getPhase",NONE)).doubleValue());
                BossBalanceTests.result("exhaustive_"+label+"_native_hook_mode",id,state==15?1:0,(Boolean)invoke(anchor,"getHookMode",NONE)?1:0);
                boss.moveTo(-16,220,-16,0,0);victim.moveTo(108,220,108,0,0);
                anchor.setPos(8,221.5,8);anchor.setDeltaMovement(0,-2,0);anchor.setNoGravity(false);
                ExhaustiveCapture.note(id,label,"setup","Native goal frame "+frame+" emits configured anchor. Its position and downward velocity are staged above stone to exercise real native projectile/block collision. Native Spark launch velocities, configured damage, owner, phase and child effects are retained. The player stays clear until each child effect receives an independent20HP contact probe.");
                ExhaustiveCapture.begin(id,label+"/anchor_native_ground_ticks",victim);
                int anchorTicks=0;
                while(anchorTicks<6&&!anchor.isRemoved()&&!(Boolean)invoke(anchor,"getGrab",NONE)){nativeEntityTick(anchor);anchorTicks++;}
                ExhaustiveCapture.end();
                BossBalanceTests.result("exhaustive_"+label+"_native_ground_collision",id,1,(Boolean)invoke(anchor,"getGrab",NONE)?1:0);
                List<Entity> sparks=newEntities(level,baseline).stream().filter(e->type(e).equals(PREFIX+"spark")).toList();
                BossBalanceTests.result("exhaustive_"+label+"_native_spark_count",id,phase,sparks.size());
                ExhaustiveCapture.note(id,label,"emissions","Native anchor ground collision after "+anchorTicks+" ticks produced "+sparks.size()+" Sparks. Phase0 is the native no-Spark control.");
                anchor.discard();
                for(int index=0;index<sparks.size();index++) {
                    Entity spark=sparks.get(index);Set<UUID> beforeSpark=snapshot(level);
                    String sparkLabel=label+"/spark"+(index+1);
                    victim.moveTo(108,220,108,0,0);
                    ExhaustiveCapture.begin(id,sparkLabel+"/native_flight_and_ground_collision",victim);
                    int ticks=0;
                    while(ticks<100&&!spark.isRemoved()){nativeEntityTick(spark);ticks++;}
                    ExhaustiveCapture.end();
                    List<Entity> children=newEntities(level,beforeSpark);
                    List<Entity> storms=children.stream().filter(e->type(e).equals(PREFIX+"lightning_storm")).toList();
                    List<Entity> areas=children.stream().filter(e->type(e).equals(PREFIX+"lightning_area_effect")).toList();
                    BossBalanceTests.result("exhaustive_"+sparkLabel+"_native_ground_removal",id,1,spark.isRemoved()?1:0);
                    BossBalanceTests.result("exhaustive_"+sparkLabel+"_native_storm_count",id,1,storms.size());
                    BossBalanceTests.result("exhaustive_"+sparkLabel+"_native_area_count",id,1,areas.size());
                    ExhaustiveCapture.note(id,sparkLabel,"emissions","Native Spark flew "+ticks+" ticks with its native launch velocity and gravity, then emitted "+storms.size()+" lightning storms and "+areas.size()+" area effects. Every Spark from this anchor is exercised.");
                    for(Entity storm:storms)firstAnchorChildHit(level,victim,id,sparkLabel+"/storm",storm,"Lightning_Storm_Entity.damage",16.2,7.5);
                    for(Entity area:areas)firstAnchorChildHit(level,victim,id,sparkLabel+"/area",area,"Lightning_Area_Effect_Entity.damage",6,.75);
                }
            }catch(Throwable error){report(id,label,error);}
            finally {ExhaustiveCapture.end();victim.stopRiding();for(Entity entity:newEntities(level,baseline))if(entity!=victim)entity.discard();if(boss!=null)boss.discard();try{BossBalanceTests.resetActualPlayer(victim);}catch(Exception error){report(id,label+"_reset",error);}}
        }
    }

    private static void firstAnchorChildHit(ServerLevel level,BossBalanceTests.TestPlayer victim,String id,String label,Entity effect,String nativeCaller,double expectedRaw,double expectedHearts) throws Exception {
        BossBalanceTests.resetActualPlayer(victim);
        victim.moveTo(effect.getX(),effect.getY()+.05,effect.getZ(),180,0);victim.setOnGround(true);victim.setDeltaMovement(Vec3.ZERO);
        ExhaustiveCapture.note(id,label,"setup","First accepted native child-effect tick against a fresh20HP unenchanted diamond player. No HP/effect/hurt-cooldown reset occurs within this contact probe. Child remains at the position created by Spark's actual ground collision.");
        ExhaustiveCapture.begin(id,label+"/native_first_hit",victim);
        for(int tick=0;tick<60&&!effect.isRemoved()&&victim.getHealth()>=20;tick++)nativeEntityTick(effect);
        List<ExhaustiveCapture.Hit> hits=ExhaustiveCapture.end();
        List<ExhaustiveCapture.Hit> matching=hits.stream().filter(h->h.nativeCallers().stream().anyMatch(c->c.endsWith(nativeCaller))).toList();
        BossBalanceTests.result("exhaustive_"+label+"_native_caller",id,1,matching.isEmpty()?0:1);
        if(!matching.isEmpty()) {
            ExhaustiveCapture.Hit hit=matching.get(0);
            BossBalanceTests.result("exhaustive_"+label+"_native_raw",id,expectedRaw,hit.originalIncomingHp());
            BossBalanceTests.result("exhaustive_"+label+"_armor",id,20,hit.armor());
            BossBalanceTests.result("exhaustive_"+label+"_toughness",id,8,hit.toughness());
            BossBalanceTests.result("exhaustive_"+label+"_hearts",id,expectedHearts,hit.damageHp()/2);
            BossBalanceTests.result("exhaustive_"+label+"_remaining_hp",id,20-expectedHearts*2,hit.afterHp());
        }else ExhaustiveCapture.note(id,label,"uncovered","Native child effect produced no accepted hit at its emitted position.");
        effect.discard();
    }

    private static void tongueContact(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        String id=PREFIX+"the_leviathan";
        if(!ExhaustiveCapture.selected(id))return;
        for(Context context:List.of(new Context("normal",1,0,false),new Context("meltdown",.4f,1,false))) {
            Mob boss=null;Set<UUID> baseline=snapshot(level);String label=context.label+"/tongue_initial_contact";
            try {
                floor(level);boss=spawn(level,victim,id,context);reset(victim,boss,5);
                invoke(victim,"useNativeRiding",new Class<?>[]{boolean.class},true);
                Object animation=boss.getClass().getField("LEVIATHAN_GRAB").get(null);
                List<Goal> goals=matchingGoals(boss,animation,null);
                for(Goal goal:goals)goal.start();
                setMove(boss,new Move("GRAB",animation,null,160,goals),25);
                for(Goal goal:goals)goal.tick();
                Entity tongue=newEntities(level,baseline).stream().filter(e->type(e).equals(PREFIX+"the_leviathan_tongue")).findFirst().orElseThrow();
                ExhaustiveCapture.note(id,label,"setup","Native grab goal frame25 emits tongue. Its native tick acquires the target, then a second tick contacts the player's eye position. Tested immediately, before the parent goal's frame155 discard. Native6HP contact and successful riding are asserted; no direct hurt or helper invocation.");
                ExhaustiveCapture.begin(id,label+"/native_tick",victim);
                for(int tick=0;tick<2;tick++){tongue.setPos(victim.getEyePosition());tongue.setDeltaMovement(Vec3.ZERO);nativeEntityTick(tongue);}
                List<ExhaustiveCapture.Hit> hits=ExhaustiveCapture.end();
                List<ExhaustiveCapture.Hit> matching=hits.stream().filter(h->h.nativeCallers().stream().anyMatch(c->c.endsWith("The_Leviathan_Tongue_Entity.hurtEntity"))).toList();
                BossBalanceTests.result("exhaustive_tongue_native_caller_"+context.label,id,1,matching.isEmpty()?0:1);
                BossBalanceTests.result("exhaustive_tongue_native_mount_"+context.label,id,1,victim.getVehicle()==tongue?1:0);
                if(!matching.isEmpty()) {
                    BossBalanceTests.result("exhaustive_tongue_native_raw_"+context.label,id,9,matching.get(0).originalIncomingHp());
                    BossBalanceTests.result("exhaustive_tongue_hearts_"+context.label,id,scaledReferenceHearts(9,24.3,4.5),matching.get(0).damageHp()/2);
                }
            }catch(Throwable error){report(id,label,error);}
            finally {ExhaustiveCapture.end();victim.stopRiding();for(Entity entity:newEntities(level,baseline))if(entity!=victim)entity.discard();if(boss!=null)boss.discard();try{invoke(victim,"useNativeRiding",new Class<?>[]{boolean.class},false);BossBalanceTests.resetActualPlayer(victim);}catch(Exception error){report(id,label+"_reset",error);}}
        }
    }

    private static void directionalSubhits(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        String id=PREFIX+"maledictus";
        if(ExhaustiveCapture.selected(id)) for(Context context:List.of(new Context("full_health_rage0",1,0,false),new Context("half_health_rage0",.4f,0,false),new Context("quarter_health_rage5",.2f,5,false))) {
            for(int distance:new int[]{2,6}) {
                double[] point=arcPoint(2,distance,2.5);
                double raw=(13*(1+.2*context.value)+.6)*1.5;
                double reference=(13*(1+.2*context.value)+1.6)*1.5;
                directionalCase(level,victim,id,context,"flying_smash2_shockwave_ring"+distance,null,9,4+2*distance,point[0],point[1],"ShieldSmashDamage",raw,scaledReferenceHearts(raw,reference,3+.4*context.value));
            }
        }
        id=PREFIX+"ignis";
        if(ExhaustiveCapture.selected(id)) for(Context context:List.of(new Context("phase0",1,0,false),new Context("phase1",.6f,1,false),new Context("phase2_shield_broken",.25f,2,true))) {
            for(int distance:new int[]{3,7}) {
                double[] point=arcPoint(.4,distance,0);
                double raw=(14*(distance==7?1.1:1)+20*(distance==7?.12:.03))*1.5;
                directionalCase(level,victim,id,context,"upperslash_shockwave_ring"+distance,"SWING_UPPERSLASH",null,distance==3?26:30,point[0],point[1],"ShieldSmashDamage",raw,scaledReferenceHearts(raw,22.5,3.5));
            }
            for(double side:new double[]{1.5,-5.5}) {
                double raw=(14*1.5+20*.15)*1.5;
                directionalCase(level,victim,id,context,"ultimate_outer_lane"+side,"ULTIMATE_ATTACK",null,74,side,12,"UltimateAttack",raw,BalanceExpectations.scaled(raw,22.5,3.5,9.5));
            }
        }
        if(ExhaustiveCapture.selected(PREFIX+"scylla")) ExhaustiveCapture.note(PREFIX+"scylla","Whip_private_helper","dormant","Installed Scylla bytecode contains the private Whip declaration but no invocation. Active electric_whip/whip_and_spear states instead use AreaAttack and Spark/lightning, which have separate native captures. Calling this dormant helper directly would manufacture an unreachable attack.");
    }

    private static double[] arcPoint(double spreadArc,int distance,double forward) {
        double spread=Math.PI*spreadArc;
        int count=(int)Math.ceil(distance*spread),index=(count-1)/2;
        double theta=((double)index/(count-1)-.5)*spread+Math.PI/2;
        return new double[]{Math.cos(theta)*distance,forward+Math.sin(theta)*distance};
    }

    private static double scaledReferenceHearts(double raw,double baseline,double hearts) {
        // Independent vanilla unenchanted-diamond reference, no status effects.
        double lo=0,hi=100;
        for(int i=0;i<60;i++){double mid=(lo+hi)/2;if(referenceHearts(mid)<hearts)lo=mid;else hi=mid;}
        return Math.min(8,referenceHearts(raw*((lo+hi)/2)/baseline));
    }

    private static double referenceHearts(double raw) {
        double armor=Math.max(4,Math.min(20,20-raw/4));
        return raw*(1-armor/25)/2;
    }

    private static void directionalCase(ServerLevel level,BossBalanceTests.TestPlayer victim,String id,Context context,String name,String animationName,Integer state,int frame,double dx,double dz,String caller,double expectedRaw,double expectedHearts) {
        Mob boss=null;Set<UUID> baseline=snapshot(level);String label=context.label+"/directional_"+name;
        try {
            floor(level);boss=spawn(level,victim,id,context);
            Object animation=animationName==null?null:boss.getClass().getField(animationName).get(null);
            List<Goal> goals=matchingGoals(boss,animation,state);
            for(Goal goal:goals)goal.start();
            reset(victim,boss,dz);victim.moveTo(8+dx,220,8+dz,180,0);
            setMove(boss,new Move(name,animation,state,frame,goals),frame);
            ExhaustiveCapture.note(id,label,"setup","Exact native aiStep frame "+frame+" with stationary player offset ("+dx+",0,"+dz+") on an emitted arc/lane; native goal.start called. This isolates the outer shockwave from earlier body hits. Expected damage-producing native caller "+caller+"; no helper/damage method invoked directly.");
            ExhaustiveCapture.begin(id,label+"/native_frame",victim);boss.aiStep();
            List<ExhaustiveCapture.Hit> hits=ExhaustiveCapture.end();
            List<ExhaustiveCapture.Hit> matching=hits.stream().filter(h->h.nativeCallers().stream().anyMatch(c->c.endsWith("."+caller))).toList();
            BossBalanceTests.result("exhaustive_directional_caller_"+label,id,1,matching.isEmpty()?0:1);
            if(!matching.isEmpty()) {
                BossBalanceTests.result("exhaustive_directional_native_raw_"+label,id,expectedRaw,matching.get(0).originalIncomingHp());
                BossBalanceTests.result("exhaustive_directional_hearts_"+label,id,expectedHearts,matching.get(0).damageHp()/2);
            } else ExhaustiveCapture.note(id,label,"uncovered","Expected native shockwave helper did not produce an accepted hit at this pose.");
        } catch(Throwable error){report(id,label,error);}
        finally {ExhaustiveCapture.end();for(Entity entity:newEntities(level,baseline))if(entity!=victim)entity.discard();if(boss!=null)boss.discard();try{BossBalanceTests.resetActualPlayer(victim);}catch(Exception error){report(id,label+"_reset",error);}}
    }

    private static void phaseBoundaries(ServerLevel level,BossBalanceTests.TestPlayer victim) {
        String[][] predicates={{"ender_guardian","isHelmetless","0.5"},{"netherite_monstrosity","isBerserk","0.4"},
            {"the_harbinger","isPowered","0.5"},{"ancient_remnant","isPower","0.5"},{"the_leviathan","isMeltDown","0.5"},
            {"maledictus","isHalfHealth","0.5"},{"maledictus","isQuarterHealth","0.25"}};
        for(String[] row:predicates) {
            String id=PREFIX+row[0];if(!ExhaustiveCapture.selected(id))continue;
            Mob boss=null;
            try {
                boss=spawn(level,victim,id,new Context("phase_boundary",1,0,false));
                float threshold=Float.parseFloat(row[2]);
                for(boolean below:new boolean[]{false,true}) {
                    float fraction=threshold+(below?-.001f:.001f);
                    boss.setHealth(boss.getMaxHealth()*fraction);
                    boolean observed=(Boolean)invoke(boss,row[1],NONE);
                    BossBalanceTests.result("exhaustive_phase_"+row[1]+(below?"_below":"_above"),id,below?1:0,observed?1:0);
                    ExhaustiveCapture.note(id,row[1]+(below?"_below":"_above"),"transition","Native phase predicate at "+fraction+" of runtime maximum HP="+boss.getMaxHealth()+" returned "+observed+". This verifies the health gate, separately from staged attack frames.");
                }
            }catch(Throwable error){report(id,"phase_boundary_"+row[1],error);}finally{if(boss!=null)boss.discard();}
        }
        String id=PREFIX+"scylla";
        if(ExhaustiveCapture.selected(id))for(int phase=1;phase<=2;phase++) {
            Mob boss=null;Set<UUID> baseline=snapshot(level);
            try {
                boss=spawn(level,victim,id,new Context("phase_boundary",1,phase-1,false));
                Goal goal=matchingGoals(boss,null,phase==1?21:22).stream().filter(g->g.getClass().getName().contains("PhaseChange")).findFirst().orElseThrow();
                invoke(boss,"setAttackState",new Class<?>[]{int.class},0);
                float threshold=phase==1?2f/3:1f/3;
                boss.setHealth(boss.getMaxHealth()*(threshold+.001f));
                BossBalanceTests.result("exhaustive_phase"+phase+"_above",id,0,goal.canUse()?1:0);
                boss.setHealth(boss.getMaxHealth()*(threshold-.001f));
                BossBalanceTests.result("exhaustive_phase"+phase+"_below",id,1,goal.canUse()?1:0);
                goal.start();
                BossBalanceTests.result("exhaustive_phase"+phase+"_native_start",id,phase,((Number)invoke(boss,"isPhase",NONE)).doubleValue());
                victim.moveTo(108,220,108,0,0);
                int duration=goalDuration(List.of(goal),phase==1?55:68);
                for(int frame=0;frame<=duration;frame++){field(boss.getClass(),"attackTicks").setInt(boss,frame);goal.tick();boss.aiStep();}
                field(boss.getClass(),"attackTicks").setInt(boss,duration+1);
                BossBalanceTests.result("exhaustive_phase"+phase+"_native_goal_finished",id,0,goal.canContinueToUse()?1:0);
                goal.stop();
                BossBalanceTests.result("exhaustive_phase"+phase+"_returns_idle",id,0,((Number)invoke(boss,"getAttackState",NONE)).doubleValue());
                BossBalanceTests.result("exhaustive_phase"+phase+"_persists_after_end",id,phase,((Number)invoke(boss,"isPhase",NONE)).doubleValue());
                phaseNbtRoundTrip(level,boss,id,"scylla_phase"+phase,"isPhase",phase);
                ExhaustiveCapture.note(id,"phase"+phase,"transition","Native Scylla phase goal health gate tested above/below "+threshold+" max HP, then native goal.start advanced the phase; damage animation frames tested separately.");
            }catch(Throwable error){report(id,"phase_boundary_"+phase,error);}finally{for(Entity entity:newEntities(level,baseline))if(entity!=victim)entity.discard();if(boss!=null)boss.discard();}
        }
        id=PREFIX+"ignis";
        if(ExhaustiveCapture.selected(id))for(int phase=1;phase<=2;phase++) {
            Mob boss=null;Set<UUID> baseline=snapshot(level);
            try {
                boss=spawn(level,victim,id,new Context("phase_boundary",1,phase-1,false));
                Object idle=boss.getClass().getField("NO_ANIMATION").get(null);
                Object transition=boss.getClass().getField(phase==1?"PHASE_2":"PHASE_3").get(null);
                boss.setTarget(null);victim.moveTo(108,220,108,0,0);
                invoke(boss,"setAnimation",new Class<?>[]{idle.getClass()},idle);
                float threshold=phase==1?2f/3:1f/3;
                boss.setHealth(boss.getMaxHealth()*(threshold-.001f));boss.setNoAi(false);boss.tick();boss.setNoAi(true);
                Object observed=invoke(boss,"getAnimation",NONE);
                BossBalanceTests.result("exhaustive_ignis_phase"+phase+"_native_tick",id,1,observed==transition?1:0);
                int duration=((Number)invoke(transition,"getDuration",NONE)).intValue();
                for(int frame=0;frame<=duration;frame++){invoke(boss,"setAnimationTick",new Class<?>[]{int.class},frame);boss.aiStep();}
                BossBalanceTests.result("exhaustive_ignis_phase"+phase+"_native_completion",id,phase,((Number)invoke(boss,"getBossPhase",NONE)).doubleValue());
                boss.tick();boss.tick();
                BossBalanceTests.result("exhaustive_ignis_phase"+phase+"_returns_idle",id,1,invoke(boss,"getAnimation",NONE)==idle?1:0);
                phaseNbtRoundTrip(level,boss,id,"ignis_phase"+phase,"getBossPhase",phase);
                ExhaustiveCapture.note(id,"phase"+phase,"transition","Native boss.tick at health just below "+threshold+" max HP selected "+(phase==1?"PHASE_2":"PHASE_3")+"; no player target. The animation's native damage frames are separate scenarios.");
            }catch(Throwable error){report(id,"phase_boundary_"+phase,error);}finally{for(Entity entity:newEntities(level,baseline))if(entity!=victim)entity.discard();if(boss!=null)boss.discard();}
        }
    }

    private static void phaseNbtRoundTrip(ServerLevel level,Mob boss,String id,String label,String getter,int expected) throws Exception {
        CompoundTag tag=new CompoundTag();boss.saveWithoutId(tag);
        Entity copy=boss.getType().create(level);
        if(copy==null)throw new IllegalStateException("Could not construct phase NBT round-trip entity");
        try {copy.load(tag);BossBalanceTests.result("exhaustive_"+label+"_nbt_reload",id,expected,((Number)invoke(copy,getter,NONE)).doubleValue());}
        finally {copy.discard();}
        ExhaustiveCapture.note(id,label,"transition","Completed native transition and verified phase survives entity NBT save/load. This is a serialization test, not a server process restart.");
    }

    private static void orient(Mob boss) {boss.setYRot(0);boss.setXRot(0);boss.yBodyRot=0;boss.yHeadRot=0;boss.yRotO=0;boss.xRotO=0;boss.yBodyRotO=0;boss.yHeadRotO=0;}
    private static void setMove(Mob boss,Move move,int frame) throws Exception {
        if(move.animation!=null) {
            invoke(boss,"setAnimation",new Class<?>[]{move.animation.getClass()},move.animation);
            invoke(boss,"setAnimationTick",new Class<?>[]{int.class},frame);
        } else {
            invoke(boss,"setAttackState",new Class<?>[]{int.class},move.state);
            field(boss.getClass(),"attackTicks").setInt(boss,frame);
        }
    }

    private static List<Goal> matchingGoals(Mob boss,Object animation,Integer state) throws Exception {
        GoalSelector selector=(GoalSelector)field(Mob.class,"goalSelector").get(boss);
        List<Goal> matches=new ArrayList<>();
        for(var wrapped:selector.getAvailableGoals()) {
            Goal goal=wrapped.getGoal();boolean match=false;
            for(Class<?> type=goal.getClass();type!=null&&type!=Object.class;type=type.getSuperclass()) for(Field field:type.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers())) continue;
                String name=field.getName().toLowerCase();
                field.setAccessible(true);
                if(animation!=null && field.getType()==animation.getClass() && field.get(goal)==animation) match=true;
                if(state!=null && field.getType()==int.class && name.matches("attackstate[0-9]*") && field.getInt(goal)==state) match=true;
            }
            if(match) matches.add(goal);
        }
        return matches;
    }

    private static int goalDuration(List<Goal> goals,int fallback) throws Exception {
        int max=0;
        for(Goal goal:goals) for(Class<?> type=goal.getClass();type!=null;type=type.getSuperclass()) for(Field field:type.getDeclaredFields()) {
            String name=field.getName().toLowerCase();
            if(field.getType()==int.class && (name.equals("attackmaxtick")||name.equals("attackfinaltick"))) {field.setAccessible(true);max=Math.max(max,field.getInt(goal));}
        }
        return max>0?max:fallback;
    }

    private static void floor(ServerLevel level) {
        for(int x=-24;x<=40;x++)for(int z=-24;z<=40;z++) {
            level.setBlock(new BlockPos(x,218,z),Blocks.BEDROCK.defaultBlockState(),2);
            level.setBlock(new BlockPos(x,219,z),Blocks.STONE.defaultBlockState(),2);
        }
    }
    private static Set<UUID> snapshot(ServerLevel level) {Set<UUID> ids=new HashSet<>();level.getAllEntities().forEach(entity->ids.add(entity.getUUID()));return ids;}
    private static List<Entity> newEntities(ServerLevel level,Set<UUID> baseline) {List<Entity> entities=new ArrayList<>();level.getAllEntities().forEach(entity->{if(!baseline.contains(entity.getUUID())&&!entity.isRemoved())entities.add(entity);});return entities;}
    private static String type(Entity entity) {return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();}
    private static Field field(Class<?> type,String name) throws Exception {for(Class<?> cls=type;cls!=null;cls=cls.getSuperclass())try{Field field=cls.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(type+"."+name);}
    private static Object invoke(Object receiver,String name,Class<?>[] signature,Object... values) throws Exception {for(Class<?> cls=receiver.getClass();cls!=null;cls=cls.getSuperclass())try{Method method=cls.getDeclaredMethod(name,signature);method.setAccessible(true);return method.invoke(receiver,values);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(receiver.getClass()+"."+name);}
    private static void report(String id,String label,Throwable error) {Throwable cause=error instanceof java.lang.reflect.InvocationTargetException&&error.getCause()!=null?error.getCause():error;ExhaustiveCapture.note(id,label,"error",cause.toString());BossBalanceTests.error("exhaustive_cat_"+label,id,cause);}
}

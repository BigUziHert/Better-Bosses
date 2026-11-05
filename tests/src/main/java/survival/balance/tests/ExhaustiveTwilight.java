package survival.balance.tests;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Installed TF attack/transition coverage. Controlled native methods, not a claim of autonomous full fights. */
final class ExhaustiveTwilight {
    private static final Class<?>[] NONE={};
    @FunctionalInterface private interface Action { void run(Mob boss) throws Exception; }
    private static ServerLevel level;
    private static BossBalanceTests.TestPlayer player;

    static void run(ServerLevel world,BossBalanceTests.TestPlayer victim) {
        level=world;player=victim;
        note("all","persistent_weather_before_fixture","mechanic","isRaining="+level.isRaining()+", rainLevel="+level.getRainLevel(1f)+", thunderLevel="+level.getThunderLevel(1f)+", savedRaining="+level.getLevelData().isRaining());
        level.setWeatherParameters(6000,0,false,false);
        level.setRainLevel(0f);level.setThunderLevel(0f);
        note("all","dry_weather_fixture","mechanic","Weather flag and interpolated rain/thunder levels set clear for synchronous status followups; isRaining="+level.isRaining()+", rainLevel="+level.getRainLevel(1f));
        for(BlockPos pos:BlockPos.betweenClosed(-24,220,-24,40,244,40))
            if(!level.getBlockState(pos).isAir())level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        naga();lich();minoshroom();phantoms();yeti();hydra();urGhast();snowQueen();
        note("naga","environmental_crumble","mechanic","One actual elevated stone-pillar destruction case is covered, including mobGriefing off/on. Arbitrary terrain-dependent falls and trajectories are not enumerated.");
        note("alpha_yeti","throw_landing","mechanic","Native grab/release/launch and fall-distance8/20 landing handlers are exercised; terrain-dependent flight trajectories and all possible landing distances are not enumerated.");
        note("all","scope","mechanic","Each case resets a real Minecraft FakePlayer implementation to20HP/full unenchanted diamond. Native attack/goal/projectile methods calculate damage; this is controlled runtime coverage, not natural multiplayer fights. Motion/animation states without distinct damage are recorded as mechanics.");
    }

    private static void naga() {
        for(String mode:List.of("normal","charge","stunless_charge")) test("naga","head_"+mode,4.5,b->{
            call(b,"setCharging",new Class<?>[]{boolean.class},!mode.equals("normal"));
            call(b,"setStunlessCharging",new Class<?>[]{boolean.class},mode.equals("stunless_charge"));
            b.doHurtTarget(player);
        });
        test("naga","body_collision",1.5,b->{
            Object segment=Class.forName("twilightforest.entity.boss.NagaSegment").getConstructor(b.getClass()).newInstance(b);
            call(segment,"collideWithEntity",new Class<?>[]{Entity.class},player);
        });
        mechanic("naga","shield_stops_charge_into_daze",b->{
            raiseShield();Object pattern=call(b,"getMovementPattern",NONE);call(pattern,"doCharge",new Class<?>[]{boolean.class},false);
            float before=b.getHealth();b.doHurtTarget(player);
            eq("naga","shield_block_dazes","DAZE",call(pattern,"getState",NONE).toString());
            eq("naga","blocked_charge_player_damage",20,player.getHealth());
            check("naga","shield_recoil_damages_naga",b.getHealth()<before);
            eq("naga","shield_durability_cost",5,player.getOffhandItem().getDamageValue());
        });
        test("naga","stunless_charge_breaks_shield",4.5,b->{
            raiseShield();Object pattern=call(b,"getMovementPattern",NONE);call(pattern,"doCharge",new Class<?>[]{boolean.class},true);
            b.doHurtTarget(player);eq("naga","stunless_shield_returns_circle","CIRCLE",call(pattern,"getState",NONE).toString());
            check("naga","stunless_shield_disabled",player.getCooldowns().isOnCooldown(Items.SHIELD)&&!player.isUsingItem());
        });
        mechanic("naga","native_crumble_breaks_elevated_pillar",b->{
            var originals=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            var rule=level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);boolean originalRule=rule.get();
            try{
                for(BlockPos pos:BlockPos.betweenClosed(7,222,3,9,223,5)){BlockPos fixed=pos.immutable();originals.put(fixed,level.getBlockState(fixed));level.setBlock(fixed,Blocks.STONE.defaultBlockState(),2);}
                player.moveTo(8,224,4,0,0);Object pattern=call(b,"getMovementPattern",NONE);call(pattern,"doCrumblePlayer",NONE);
                rule.set(false,level.getServer());for(int i=0;i<5;i++)call(pattern,"tick",NONE);
                eq("naga","crumble_respects_griefing_false",18,originals.keySet().stream().filter(pos->level.getBlockState(pos).is(Blocks.STONE)).count());
                rule.set(true,level.getServer());for(int i=0;i<10;i++)call(pattern,"tick",NONE);
                long remaining=originals.keySet().stream().filter(pos->level.getBlockState(pos).is(Blocks.STONE)).count();
                check("naga","native_crumble_removed_blocks",remaining<18);
                note("naga","crumble_pillar_result","mechanic","Native CRUMBLE goal ticks removed "+(18-remaining)+" of18 temporary elevated stone blocks with griefing enabled; player fall trajectory intentionally not simulated.");
            }finally{rule.set(originalRule,level.getServer());for(var entry:originals.entrySet())level.setBlock(entry.getKey(),entry.getValue(),2);}
        });
        mechanic("naga","movement_states_and_health_segments",b->{
            Object pattern=call(b,"getMovementPattern",NONE);
            for(String[] step:new String[][]{{"doCircle","CIRCLE"},{"doIntimidate","INTIMIDATE"},{"doCrumblePlayer","CRUMBLE"},{"doDaze","DAZE"}}){
                call(pattern,step[0],NONE);eq("naga","state_"+step[1],step[1],call(pattern,"getState",NONE).toString());
            }
            call(pattern,"doCharge",new Class<?>[]{boolean.class},false);
            note("naga","charge_transition","transition","Native doCharge(false) -> "+call(pattern,"getState",NONE));
            call(pattern,"doCharge",new Class<?>[]{boolean.class},true);
            note("naga","stunless_transition","transition","Native doCharge(true) -> "+call(pattern,"getState",NONE));
            int prior=Integer.MAX_VALUE;
            for(float ratio:new float[]{1,.5f,.1f}){
                b.setHealth(b.getMaxHealth()*ratio);call(b,"setSegmentsPerHealth",NONE);
                int count=((Number)field(b,"currentSegmentCount")).intValue();
                check("naga","segment_count_"+ratio,count<prior);prior=count;
                note("naga","hp_"+ratio,"mechanic","Native health-dependent segment count="+count+", maxHP="+b.getMaxHealth());
            }
        });
    }

    private static void lich() {
        for(int phase:new int[]{1,2})for(int kind:new int[]{0,1}){
            test("lich","phase"+phase+(kind==0?"_bolt":"_bomb"),kind==0?3.75:-6,b->{
                lichPhase(b,phase);call(b,"setNextAttackType",new Class<?>[]{int.class},kind);
                Set<Entity> before=entities();
                if(phase==1){
                    Object goal=construct("twilightforest.entity.ai.goal.LichShadowsGoal",new Class<?>[]{b.getClass(),float.class},b,30f);
                    call(goal,"attack",new Class<?>[]{b.getClass()},b);
                }else{
                    Object goal=construct("twilightforest.entity.ai.goal.LichMinionsGoal",new Class<?>[]{b.getClass()},b);
                    note("lich","phase2_ranged_setup","mechanic","Before native goal tick: distance="+b.distanceTo(player)+", LOS="+b.hasLineOfSight(player)+", bossEye="+b.getEyePosition()+", playerEye="+player.getEyePosition());
                    check("lich","phase2_native_line_of_sight",b.hasLineOfSight(player));
                    call(b,"setAttackCooldown",new Class<?>[]{int.class},0);call(goal,"tick",NONE);
                    note("lich","phase2_ranged_after_tick","mechanic","After goal tick: distance="+b.distanceTo(player)+", LOS="+b.hasLineOfSight(player)+", cooldown="+call(b,"getAttackCooldown",NONE)+", invisibility="+call(b,"getTeleportInvisibility",NONE)+", spawned="+newEntities(before).stream().map(ExhaustiveTwilight::id).toList());
                }
                Entity projectile=oneNew(before,kind==0?"twilightforest:lich_bolt":"twilightforest:lich_bomb");
                projectile.moveTo(player.position());
                if(kind==0)hit(projectile,player);else call(projectile,"onHit",new Class<?>[]{HitResult.class},new EntityHitResult(player));
            });
        }
        test("lich","phase2_close_melee",3.75,b->{
            lichPhase(b,2);player.moveTo(b.getX(),b.getY(),b.getZ()+1,180,0);
            call(b,"setAttackCooldown",new Class<?>[]{int.class},0);
            Object goal=construct("twilightforest.entity.ai.goal.LichMinionsGoal",new Class<?>[]{b.getClass()},b);
            call(goal,"tick",NONE);
        });
        test("lich","phase3_sword",3.75,b->{lichPhase(b,3);call(b,"detectEquipmentUpdates",NONE);b.doHurtTarget(player);});
        mechanic("lich","native_reflection_breaks_shields",b->{
            lichPhase(b,1);int initial=((Number)call(b,"getShieldStrength",NONE)).intValue();
            for(int i=0;i<initial;i++){
                Entity bolt=projectile("twilightforest:lich_bolt",b);bolt.moveTo(player.position());
                player.attack(bolt);check("lich","reflected_owner_"+i,((Projectile)bolt).getOwner()==player);
                call(b,"setTeleportInvisibility",new Class<?>[]{int.class},0);hit(bolt,b);
            }
            eq("lich","shield_to_minions",2,((Number)call(b,"getPhase",NONE)).intValue());
        });
        for(int kind:new int[]{0,1})test("lich",kind==0?"phase1_clone_bolt":"phase1_clone_bomb",kind==0?3.75:-6,b->{
            Set<Entity> before=entities();Object goal=construct("twilightforest.entity.ai.goal.LichShadowsGoal",new Class<?>[]{b.getClass(),float.class},b,30f);
            call(goal,"checkAndSpawnClones",new Class<?>[]{LivingEntity.class},player);
            Mob clone=(Mob)newEntities(before).stream().filter(e->e instanceof Mob&&id(e).equals("twilightforest:lich")).findFirst().orElseThrow();
            check("lich","native_clone_marked",(boolean)call(clone,"isShadowClone",NONE));
            clone.setTarget(player);call(clone,"setNextAttackType",new Class<?>[]{int.class},kind);
            before=entities();call(goal,"attack",new Class<?>[]{b.getClass()},clone);
            Entity shot=oneNew(before,kind==0?"twilightforest:lich_bolt":"twilightforest:lich_bomb");shot.moveTo(player.position());
            if(kind==0)hit(shot,player);else call(shot,"onHit",new Class<?>[]{HitResult.class},new EntityHitResult(player));
        });
        test("lich","phase2_summoned_minion_melee",Double.NaN,b->{
            lichPhase(b,2);call(b,"setMinionsToSummon",new Class<?>[]{int.class},1);
            Object goal=construct("twilightforest.entity.ai.goal.LichMinionsGoal",new Class<?>[]{b.getClass()},b);
            Set<Entity> before=entities();call(goal,"checkAndSpawnMinions",NONE);
            Mob minion=(Mob)oneNew(before,"twilightforest:lich_minion");
            eq("lich","summons_exhausted",0,((Number)call(b,"getMinionsToSummon",NONE)).intValue());
            minion.moveTo(player.position());call(minion,"detectEquipmentUpdates",NONE);minion.doHurtTarget(player);
            minion.discard();eq("lich","minions_to_sword",3,((Number)call(b,"getPhase",NONE)).intValue());
            note("lich","minion_scope","mechanic","Summoned zombie damage remains nonboss damage; its native equipment can vary.");
        });
        test("lich","phase2_enraged_minion_melee",Double.NaN,b->{
            lichPhase(b,2);Object goal=construct("twilightforest.entity.ai.goal.LichMinionsGoal",new Class<?>[]{b.getClass()},b);
            Set<Entity> before=entities();call(goal,"checkAndSpawnMinions",NONE);Mob minion=(Mob)oneNew(before,"twilightforest:lich_minion");
            minion.setTarget(player);Entity bolt=projectile("twilightforest:lich_bolt",b);hit(bolt,minion);
            check("lich","minion_strength_from_friendly_bolt",minion.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST));
            check("lich","minion_speed_from_friendly_bolt",minion.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED));
            minion.moveTo(player.position());call(minion,"detectEquipmentUpdates",NONE);minion.doHurtTarget(player);
            note("lich","enraged_minion_scope","mechanic","Actual friendly LichBolt hit activates minion's native200-tick StrengthII/SpeedIII; its subsequent melee remains nonboss damage.");
        });
    }

    private static void minoshroom() {
        for(boolean charge:new boolean[]{false,true})test("minoshroom",charge?"charge_axe":"ordinary_axe",3.5,b->{
            call(b,"setCharging",new Class<?>[]{boolean.class},charge);b.doHurtTarget(player);
        });
        test("minoshroom","ground_slam_native_goal",5.5,b->{
            Object goal=construct("twilightforest.entity.ai.goal.GroundAttackGoal",new Class<?>[]{b.getClass()},b);
            set(goal,"attackTarget",player);call(goal,"start",NONE);
            check("minoshroom","slam_windup",(boolean)call(b,"isGroundAttackCharge",NONE));
            set(goal,"attackTick",0);player.setOnGround(true);call(goal,"tick",NONE);
            check("minoshroom","slam_release",!(boolean)call(b,"isGroundAttackCharge",NONE));
        });
    }

    private static void phantoms() {
        for(String weapon:List.of("sword","axe","pickaxe"))for(boolean charging:new boolean[]{false,true})
            test("knight_phantom",weapon+(charging?"_charge_melee":"_formation_melee"),charging?3.5:2,b->{
                b.setItemSlot(EquipmentSlot.MAINHAND,item("twilightforest:knightmetal_"+weapon));call(b,"detectEquipmentUpdates",NONE);
                call(b,"setChargingAtPlayer",new Class<?>[]{boolean.class},charging);b.doHurtTarget(player);
            });
        for(boolean axe:new boolean[]{true,false})test("knight_phantom",axe?"thrown_axe":"eight_pick_volley",2,b->{
            call(b,"setChargingAtPlayer",new Class<?>[]{boolean.class},true);
            Object goal=construct("twilightforest.entity.ai.goal.PhantomThrowWeaponGoal",new Class<?>[]{b.getClass()},b);
            Set<Entity> before=entities();
            if(axe)call(goal,"launchAxeAt",new Class<?>[]{Entity.class},player);else call(goal,"launchPicks",NONE);
            List<Entity> weapons=newEntities(before).stream().filter(e->id(e).equals("twilightforest:thrown_wep")).toList();
            eq("knight_phantom",axe?"axe_count":"pick_count",axe?1:8,weapons.size());
            if(weapons.isEmpty())throw new IllegalStateException("Native throw produced no weapon");
            hit(weapons.getFirst(),player);
            note("knight_phantom","thrown_source_provenance","mechanic","Installed ThrownWep constructs ownerless/directless damage sources. Native raw6 axe/raw3 pick therefore use ordinary2heart policy, independently of owner's charging state;3.5 applies charging melee.");
        });
        mechanic("knight_phantom","all_native_formations",b->{
            Class<?> type=Class.forName("twilightforest.entity.boss.KnightPhantom$Formation");
            for(Object formation:type.getEnumConstants()){
                call(b,"switchToFormation",new Class<?>[]{type},formation);
                eq("knight_phantom","formation_"+formation,formation.toString(),call(b,"getCurrentFormation",NONE).toString());
                note("knight_phantom",formation.toString(),"mechanic","Native formation setter; charging="+call(b,"isChargingAtPlayer",NONE)+"; duration="+call(b,"getMaxTicksForFormation",NONE));
            }
        });
    }

    private static void yeti() {
        test("alpha_yeti","rampage_stomp",4.5,b->{call(b,"setRampaging",new Class<?>[]{boolean.class},true);call(b,"hitNearbyEntities",NONE);});
        test("alpha_yeti","thrown_ice_direct",-1.5,b->{
            Set<Entity> before=entities();call(b,"performRangedAttack",new Class<?>[]{LivingEntity.class,float.class},player,1f);
            Entity ice=oneNew(before,"twilightforest:thrown_ice");ice.moveTo(player.position());hit(ice,player);
            note("alpha_yeti","direct_frosted","mechanic","Native status after direct ice: "+player.getActiveEffects());
        });
        test("alpha_yeti","ice_cloud_tick",-1.5,b->{
            Entity ice=projectile("twilightforest:thrown_ice",b);ice.moveTo(player.position());
            call(ice,"onHitBlock",new Class<?>[]{BlockHitResult.class},groundHit());call(ice,"hitNearbyEntities",NONE);
            note("alpha_yeti","cloud_frosted","mechanic","Native status after cloud pulse: "+player.getActiveEffects());
        });
        test("alpha_yeti","ice_impact_and_full_frosted_followup",Double.NaN,b->{
            Set<Entity> before=entities();call(b,"performRangedAttack",new Class<?>[]{LivingEntity.class,float.class},player,1f);
            Entity ice=oneNew(before,"twilightforest:thrown_ice");ice.moveTo(player.position());hit(ice,player);ice.discard();
            float afterImpact=player.getHealth();player.moveTo(24,225,24,0,0);
            for(int i=0;i<300;i++)nativePlayerTick();
            check("alpha_yeti","frosted_effect_expired",player.getActiveEffects().isEmpty());
            note("alpha_yeti","full_frosted_followup","mechanic","Native impact HP="+afterImpact+", after300 real ServerPlayer lifecycle ticks="+player.getHealth()+", frozenTicks="+player.getTicksFrozen()+". No synthetic hurt; continued direct cloud exposure excluded from this status-isolation case.");
        });
        for(float fall:new float[]{8,25,80})test("alpha_yeti","falling_ice_distance_"+fall,-5.25,b->{
            Entity ice=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("twilightforest:falling_ice")).create(level);
            if(ice==null)throw new IllegalStateException("Falling ice missing");ice.moveTo(player.position());level.addFreshEntity(ice);
            // The native method computes its own distance/difficulty-based damage, including its cap.
            ice.causeFallDamage(fall,1,level.damageSources().fall());
        });
        mechanic("alpha_yeti","rampage_tired_recovery",b->{
            Object rampage=construct("twilightforest.entity.ai.goal.YetiRampageGoal",new Class<?>[]{b.getClass(),int.class,int.class},b,10,180);
            call(rampage,"start",NONE);check("alpha_yeti","rampage_started",(boolean)call(b,"isRampaging",NONE));
            call(rampage,"stop",NONE);check("alpha_yeti","rampage_to_tired",!(boolean)call(b,"isRampaging",NONE)&&(boolean)call(b,"isTired",NONE));
            Object tired=construct("twilightforest.entity.ai.goal.YetiTiredGoal",new Class<?>[]{b.getClass(),int.class},b,100);
            call(tired,"start",NONE);for(int i=0;i<101;i++)call(tired,"tick",NONE);
            check("alpha_yeti","tired_timer_completed",!(boolean)call(tired,"canContinueToUse",NONE));call(tired,"stop",NONE);
            check("alpha_yeti","tired_recovered",!(boolean)call(b,"isTired",NONE));
        });
        mechanic("alpha_yeti","grab_and_throw",b->{
            player.useNativeRiding(true);
            try{
                player.moveTo(b.getX(),b.getY(),b.getZ()+.5,180,0);b.setTarget(player);
                Object goal=construct("twilightforest.entity.ai.goal.ThrowRiderGoal",new Class<?>[]{PathfinderMob.class,double.class,boolean.class},b,1d,false);
                set(goal,"cooldown",0);call(goal,"checkAndPerformAttack",new Class<?>[]{LivingEntity.class},player);
                check("alpha_yeti","grab_attached",player.getVehicle()==b);
                call(goal,"stop",NONE);check("alpha_yeti","throw_released",player.getVehicle()==null);
                check("alpha_yeti","throw_upward_velocity",player.getDeltaMovement().y>0);
                note("alpha_yeti","throw_velocity","mechanic","Native release velocity="+player.getDeltaMovement()+". TestPlayer opts into original ServerPlayer riding implementation because Forge FakePlayer otherwise refuses every mount.");
            }finally{clearYetiThrow();player.stopRiding();player.useNativeRiding(false);}
        });
        for(float distance:new float[]{8,20})test("alpha_yeti","thrown_landing_distance_"+distance,Double.NaN,b->{
            player.useNativeRiding(true);
            try{
                player.moveTo(b.getX(),b.getY(),b.getZ()+.5,180,0);b.setTarget(player);
                Object goal=construct("twilightforest.entity.ai.goal.ThrowRiderGoal",new Class<?>[]{PathfinderMob.class,double.class,boolean.class},b,1d,false);
                set(goal,"cooldown",0);call(goal,"checkAndPerformAttack",new Class<?>[]{LivingEntity.class},player);
                check("alpha_yeti","landing_fixture_native_grab",player.getVehicle()==b);call(goal,"stop",NONE);
                player.causeFallDamage(distance,1,level.damageSources().fall());
                note("alpha_yeti","yeeted_source","mechanic","Native fall calculation distance="+distance+" is converted by installed HostileMountEvents to boss-attributed twilightforest:yeeted. Production leaves this terrain-dependent damage unchanged; no synthetic raw hurt amount was supplied.");
            }finally{clearYetiThrow();player.stopRiding();player.useNativeRiding(false);}
        });
    }

    private static void hydra() {
        for(boolean secondary:new boolean[]{false,true}){
            test("hydra",secondary?"secondary_flame":"primary_flame",.75,b->{
                Object container=((Object[])field(b,"hc"))[secondary?1:0];Entity head=(Entity)field(container,"headEntity");
                head.moveTo(8,220,34,180,0);head.setYRot(180);head.setXRot(0);set(container,"isSecondaryAttacking",secondary);
                headState(head,"FLAMING");check("hydra","flame_ray_selected_player",call(container,"getHeadLookTarget",NONE)==player);
                call(container,"executeAttacks",NONE);check("hydra","flame_ignition",player.isOnFire());
            });
        }
        test("hydra","bite",7,b->{Object c=((Object[])field(b,"hc"))[0];Entity h=(Entity)field(c,"headEntity");h.moveTo(player.position());headState(h,"BITING");call(c,"executeAttacks",NONE);});
        test("hydra","flame_hit_and_full_burn_followup",Double.NaN,b->{
            Object c=((Object[])field(b,"hc"))[0];Entity head=(Entity)field(c,"headEntity");head.moveTo(8,220,34,180,0);headState(head,"FLAMING");call(c,"executeAttacks",NONE);
            float direct=player.getHealth();check("hydra","followup_flame_ignites",player.isOnFire());player.moveTo(24,225,24,0,0);
            for(int i=0;i<100;i++)nativeBurnTick("flame",i);check("hydra","flame_burn_expired",!player.isOnFire());
            check("hydra","dry_flame_has_delayed_burn_damage",player.getHealth()<direct);
            note("hydra","flame_full_burn","mechanic","Native flame HP="+direct+", after100 real ServerPlayer lifecycle ticks="+player.getHealth()+"; no further boss contact.");
        });
        test("hydra","bite_disables_raised_shield",7,b->{
            raiseShield();Object c=((Object[])field(b,"hc"))[0];Entity h=(Entity)field(c,"headEntity");h.moveTo(player.position());headState(h,"BITING");call(c,"executeAttacks",NONE);
            check("hydra","bite_shield_disabled",player.getCooldowns().isOnCooldown(Items.SHIELD)&&!player.isUsingItem());
            eq("hydra","bite_shield_durability_cost",112,player.getOffhandItem().getDamageValue());
        });
        for(boolean mega:new boolean[]{false,true})test("hydra",mega?"mortar_megablast":"mortar_contact_and_burn",-5.5,b->{
            Entity mortar=hydraMortar(b);mortar.moveTo(player.position());
            if(mega)call(mortar,"setToBlasting",NONE);hit(mortar,player);
            note("hydra","mortar_burning_"+mega,"mechanic","Native fire ticks="+player.getRemainingFireTicks());
        });
        test("hydra","mortar_megablast_unobstructed",-5.5,b->{
            player.moveTo(8,225,4,180,0);Entity mortar=hydraMortar(b);mortar.moveTo(player.position());
            call(mortar,"setToBlasting",NONE);hit(mortar,player);
            note("hydra","unobstructed_exposure","mechanic","Native megablast collision5blocks above the arena, blast at player feet with zero entity distance and no floor occlusion.");
        });
        test("hydra","mortar_hit_and_full_burn_followup",Double.NaN,b->{
            Entity mortar=hydraMortar(b);mortar.moveTo(player.position());hit(mortar,player);float direct=player.getHealth();
            check("hydra","followup_mortar_ignites",player.isOnFire());player.moveTo(24,225,24,0,0);
            for(int i=0;i<140;i++)nativeBurnTick("mortar",i);check("hydra","mortar_burn_expired",!player.isOnFire());
            check("hydra","dry_mortar_has_delayed_burn_damage",player.getHealth()<direct);
            note("hydra","mortar_full_burn","mechanic","Native mortar HP="+direct+", after140 real ServerPlayer lifecycle ticks="+player.getHealth()+"; no further boss contact.");
        });
        for(boolean fuse:new boolean[]{false,true})test("hydra",fuse?"mortar_ground_fuse":"mortar_wall_impact",-5.5,b->{
            Entity mortar=hydraMortar(b);mortar.moveTo(player.position());mortar.setDeltaMovement(Vec3.ZERO);mortar.setNoGravity(true);
            if(fuse){
                mortar.moveTo(player.getX()+1.2,player.getY(),player.getZ(),0,0);
                call(mortar,"onHitBlock",new Class<?>[]{BlockHitResult.class},groundHit());
                check("hydra","mortar_landed_without_detonation",!mortar.isRemoved()&&mortar.onGround());
                set(mortar,"fuse",0);mortar.tick();
            }else call(mortar,"onHitBlock",new Class<?>[]{BlockHitResult.class},new BlockHitResult(player.position(),Direction.NORTH,player.blockPosition(),false));
            check("hydra","mortar_detonation_removed",mortar.isRemoved());
        });
        mechanic("hydra","native_head_attack_state_chains",b->{
            Object c=((Object[])field(b,"hc"))[0];Class<?> state=Class.forName("twilightforest.entity.boss.HydraHeadContainer$State");
            for(String beginning:List.of("BITE_BEGINNING","FLAME_BEGINNING","MORTAR_BEGINNING")){
                call(c,"setNextState",new Class<?>[]{state},constant(state,beginning));call(c,"endCurrentAction",NONE);
                Set<String> visited=new java.util.LinkedHashSet<>();
                for(int i=0;i<650;i++){call(c,"advanceHeadState",NONE);visited.add(field(c,"currentState").toString());}
                note("hydra",beginning,"transition","Native timed state chain="+visited);
                check("hydra",beginning+"_entered",visited.contains(beginning));
            }
            set(c,"currentState",constant(state,"DEAD"));call(c,"setRespawnCounter",new Class<?>[]{int.class},1);
            call(c,"advanceRespawnCounter",NONE);call(c,"advanceHeadState",NONE);
            eq("hydra","dead_head_regrows","BORN",field(c,"currentState").toString());
        });
        mechanic("hydra","player_damage_severs_head_then_grows_extra",b->{
            Object[] heads=(Object[])field(b,"hc");Object c=heads[0];Entity head=(Entity)field(c,"headEntity");
            Class<?> state=Class.forName("twilightforest.entity.boss.HydraHeadContainer$State");Object flaming=constant(state,"FLAMING");
            set(c,"prevState",flaming);set(c,"currentState",flaming);headState(head,"FLAMING");head.moveTo(player.getX(),player.getY(),player.getZ()+1,0,0);
            check("hydra","native_open_mouth_weakpoint",((Number)call(c,"getCurrentMouthOpen",NONE)).floatValue()>.5f);
            equipPlayerSword();int attacks=0;
            while(((Number)call(c,"getDamageTaken",NONE)).intValue()<=120&&attacks++<30){b.invulnerableTime=0;b.hurtTime=0;set(player,"attackStrengthTicker",100);player.attack(head);}
            check("hydra","real_head_damage_exceeds_120",((Number)call(c,"getDamageTaken",NONE)).intValue()>120);
            int initial=((Number)call(b,"countActiveHeads",NONE)).intValue();call(b,"customServerAiStep",NONE);call(c,"advanceHeadState",NONE);
            eq("hydra","real_head_damage_starts_dying","DYING",field(c,"currentState").toString());
            for(int tick=0;tick<600;tick++)for(Object container:heads){call(container,"advanceRespawnCounter",NONE);call(container,"advanceHeadState",NONE);}
            eq("hydra","severed_head_and_extra_regrown",initial+1,((Number)call(b,"countActiveHeads",NONE)).intValue());
            eq("hydra","regrown_head_damage_reset",0,((Number)call(c,"getDamageTaken",NONE)).intValue());
            note("hydra","sever_regrow_gate","transition","Actual SharpnessV netherite-sword hits on open mouth="+attacks+"; native >120 head-damage gate schedules DYING and two140-tick regrowth counters; native state timers produced one additional active head.");
        });
    }

    private static void urGhast() {
        test("ur_ghast","normal_fireball_direct",4.5,b->{Entity fireball=urBall(b);fireball.moveTo(player.position());hit(fireball,player);});
        test("ur_ghast","normal_fireball_splash",-6,b->{Entity fireball=urBall(b);fireball.moveTo(player.position());call(fireball,"onHitBlock",new Class<?>[]{BlockHitResult.class},groundHit());
            note("ur_ghast","ownerless_splash","mechanic","Native power1 splash has null source owner; custom boss cap cannot identify it. Its observed armored damage is recorded without claiming attribution.");});
        test("ur_ghast","normal_fireball_splash_unobstructed",-6,b->{
            player.moveTo(8,225,4,180,0);Entity fireball=urBall(b);fireball.moveTo(player.position());
            call(fireball,"onHitBlock",new Class<?>[]{BlockHitResult.class},groundHit());
            note("ur_ghast","unobstructed_ownerless_splash","mechanic","Native block-collision callback, explosion5blocks above arena at player feet, zero entity distance and no floor occlusion. Native ownerless explosion is attributed only through the active Ur-Ghast projectile context; the unobstructed power1 result remains below its ceiling.");
        });
        test("ur_ghast","tantrum_tear_rain",.75,b->{b.moveTo(player.getX(),player.getY()+10,player.getZ(),0,0);call(b,"setInTantrum",new Class<?>[]{boolean.class},true);call(b,"doTantrumDamageEffects",NONE);});
        for(boolean splash:new boolean[]{false,true})test("ur_ghast",splash?"summoned_ghastling_fireball_splash":"summoned_ghastling_fireball_direct",Double.NaN,b->{
            Entity fireball=ghastlingBall(b);fireball.moveTo(player.position());
            if(splash)call(fireball,"onHit",new Class<?>[]{HitResult.class},groundHit());
            else call(fireball,"onHit",new Class<?>[]{HitResult.class},new EntityHitResult(player));
            note("ur_ghast","ghastling_scope","mechanic","Native summoned nonboss ghastling plus its own spitFireball method and vanilla collision/explosion; retains nonboss damage.");
        });
        for(boolean splash:new boolean[]{false,true})test("ur_ghast",splash?"summoned_ghastling_splash_and_full_burn_followup":"summoned_ghastling_direct_and_full_burn_followup",splash?6.28125:5.06625,b->{
            String variant=splash?"splash":"direct";
            level.setWeatherParameters(6000,0,false,false);level.setRainLevel(0f);level.setThunderLevel(0f);
            Entity fireball=ghastlingBall(b);fireball.moveTo(player.position());
            if(splash)call(fireball,"onHit",new Class<?>[]{HitResult.class},groundHit());
            else call(fireball,"onHit",new Class<?>[]{HitResult.class},new EntityHitResult(player));
            float afterImmediate=player.getHealth();int nativeIgnition=player.getRemainingFireTicks();
            eq("ur_ghast","ghastling_"+variant+"_immediate_hearts",splash?4.78125:3.56625,(20-afterImmediate)/2d);
            eq("ur_ghast","ghastling_"+variant+"_native_ignition_ticks",80,nativeIgnition);
            check("ur_ghast","ghastling_"+variant+"_ignites",player.isOnFire());
            player.moveTo(24,225,24,0,0);
            check("ur_ghast","ghastling_"+variant+"_dry_followup",!level.isRainingAt(player.blockPosition())&&!player.isInWaterRainOrBubble());
            int burnHits=0;
            for(int i=0;i<120;i++){
                float beforeTick=player.getHealth();nativeBurnTick("ur_ghast","ghastling_"+variant,i);
                if(player.getHealth()<beforeTick){
                    burnHits++;double hearts=(beforeTick-player.getHealth())/2d;
                    eq("ur_ghast","ghastling_"+variant+"_burn_hit_"+burnHits+"_hearts",.5,hearts);
                    note("ur_ghast","ghastling_"+variant+"_burn_at_tick_"+i,"mechanic","Native player lifecycle tick: HP="+beforeTick+" -> "+player.getHealth()+", hearts="+hearts+"; fireTicks="+player.getRemainingFireTicks()+". Capture records the actual damage source.");
                }
            }
            check("ur_ghast","ghastling_"+variant+"_burn_expired",!player.isOnFire());
            eq("ur_ghast","ghastling_"+variant+"_burn_hit_count",3,burnHits);
            eq("ur_ghast","ghastling_"+variant+"_delayed_hearts",1.5,(afterImmediate-player.getHealth())/2d);
            note("ur_ghast","ghastling_"+variant+"_full_burn_breakdown","mechanic","Initial HP=20; after native collision/explosion HP="+afterImmediate+" (immediate hearts="+((20-afterImmediate)/2d)+"); native ignition="+nativeIgnition+"ticks; after120 real ServerPlayer lifecycle ticks HP="+player.getHealth()+" (delayed hearts="+((afterImmediate-player.getHealth())/2d)+", combined total hearts="+((20-player.getHealth())/2d)+"). Dry isolated followup, no further boss/minion/projectile contact; immediate components are already included in the combined total.");
        });
        mechanic("ur_ghast","normal_tantrum_normal_native_transitions",b->{
            call(b,"setInTantrum",new Class<?>[]{boolean.class},false);call(b,"switchPhase",NONE);
            check("ur_ghast","normal_to_tantrum",(boolean)call(b,"isInTantrum",NONE));
            eq("ur_ghast","phase_damage_counter",18,((Number)field(b,"damageUntilNextPhase")).doubleValue());
            call(b,"switchPhase",NONE);check("ur_ghast","tantrum_to_normal",!(boolean)call(b,"isInTantrum",NONE));
            Set<Entity> before=entities();call(b,"spawnMinionGhastsAt",new Class<?>[]{ServerLevel.class,int.class,int.class,int.class},level,8,225,8);
            List<Entity> minions=newEntities(before).stream().filter(e->id(e).equals("twilightforest:carminite_ghastling")).toList();
            check("ur_ghast","native_minion_summon",!minions.isEmpty());
            for(Entity e:minions)check("ur_ghast","ghastling_minion_flag",(boolean)call(e,"isMinion",NONE));
            note("ur_ghast","ghastling_count","mechanic","Native summon created "+minions.size()+" marked ghastlings.");
        });
        mechanic("ur_ghast","player_sword_damage_crosses_both_phase_gates",b->{
            equipPlayerSword();call(b,"setInTantrum",new Class<?>[]{boolean.class},false);call(b,"resetDamageUntilNextPhase",NONE);
            float start=b.getHealth();int attacks=0;
            while(!(boolean)call(b,"isInTantrum",NONE)&&attacks++<30)playerSwordHit(b);
            check("ur_ghast","player_damage_starts_tantrum",(boolean)call(b,"isInTantrum",NONE));
            note("ur_ghast","normal_damage_gate","transition","Actual full-charge SharpnessV netherite-sword attacks="+attacks+", actual bossHP lost="+(start-b.getHealth())+" crossed native18HP boundary.");
            start=b.getHealth();attacks=0;
            while((boolean)call(b,"isInTantrum",NONE)&&attacks++<100)playerSwordHit(b);
            check("ur_ghast","player_damage_ends_tantrum",!(boolean)call(b,"isInTantrum",NONE));
            note("ur_ghast","tantrum_damage_gate","transition","Actual sword attacks="+attacks+", bossHP lost="+(start-b.getHealth())+"; installed tantrum10x incoming-damage reduction retained.");
        });
    }

    private static void snowQueen() {
        for(String phase:List.of("SUMMON","DROP","BEAM"))test("snow_queen","shield_contact_"+phase,6,b->{
            snowPhase(b,phase);Entity shield=(Entity)((Object[])field(b,"iceArray"))[0];shield.moveTo(player.position());
            call(b,"applyShieldCollision",new Class<?>[]{Entity.class,Entity.class},shield,player);
        });
        test("snow_queen","beam_breath",.75,b->{snowPhase(b,"BEAM");call(b,"setBreathing",new Class<?>[]{boolean.class},true);call(b,"doBreathAttack",new Class<?>[]{Entity.class},player);});
        test("snow_queen","summoned_ice_crystal_contact",Double.NaN,b->{
            snowPhase(b,"SUMMON");Set<Entity> before=entities();call(b,"summonMinionAt",new Class<?>[]{LivingEntity.class},player);
            Mob minion=(Mob)oneNew(before,"twilightforest:ice_crystal");minion.moveTo(player.position());minion.doHurtTarget(player);
            note("snow_queen","minion_scope","mechanic","Native summoned IceCrystal contact damage; minions intentionally retain their original nonboss stats.");
        });
        mechanic("snow_queen","summon_drop_beam_summon_cycle",b->{
            snowPhase(b,"SUMMON");call(b,"setSummonsRemaining",new Class<?>[]{int.class},0);call(b,"customServerAiStep",NONE);
            eq("snow_queen","summon_to_drop","DROP",call(b,"getCurrentPhase",NONE).toString());
            int drops=((Number)field(b,"maxDrops")).intValue();for(int i=0;i<drops;i++)call(b,"incrementSuccessfulDrops",NONE);
            call(b,"customServerAiStep",NONE);eq("snow_queen","drop_to_beam","BEAM",call(b,"getCurrentPhase",NONE).toString());
            // Only seed the cumulative-damage boundary; the installed transition code executes the change.
            set(b,"damageWhileBeaming",25);call(b,"customServerAiStep",NONE);
            eq("snow_queen","beam_to_summon","SUMMON",call(b,"getCurrentPhase",NONE).toString());
            eq("snow_queen","summon_quota_reset",6,((Number)call(b,"getSummonsRemaining",NONE)).intValue());
        });
        mechanic("snow_queen","player_sword_damage_ends_beam",b->{
            snowPhase(b,"BEAM");equipPlayerSword();int attacks=0;
            while(((Number)field(b,"damageWhileBeaming")).intValue()<25&&attacks++<20)playerSwordHit(b);
            check("snow_queen","real_damage_reaches_beam_threshold",((Number)field(b,"damageWhileBeaming")).intValue()>=25);
            call(b,"customServerAiStep",NONE);eq("snow_queen","real_damage_beam_to_summon","SUMMON",call(b,"getCurrentPhase",NONE).toString());
            note("snow_queen","beam_damage_gate","transition","Actual full-charge SharpnessV netherite-sword attacks="+attacks+" crossed native25-damage beam threshold; native customServerAiStep selected SUMMON.");
        });
    }

    private static void test(String shortId,String name,double expected,Action action){runCase(shortId,name,expected,true,action);}
    private static void mechanic(String shortId,String name,Action action){runCase(shortId,name,0,false,action);}
    private static void runCase(String shortId,String name,double expected,boolean damage,Action action){
        String bossId="twilightforest:"+shortId;Mob boss=null;Set<Entity> previous=entities();boolean began=false;
        if(!ExhaustiveCapture.selected(bossId))return;
        try{
            player.stopRiding();BossBalanceTests.resetActualPlayer(player);player.clearFire();player.stopUsingItem();player.setOnGround(true);player.setTicksFrozen(0);
            player.setIsInPowderSnow(false);player.wasInPowderSnow=false;
            player.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);player.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);player.getCooldowns().removeCooldown(Items.SHIELD);BossBalanceTests.updateEquipment(player);
            boss=(Mob)BossBalanceTests.damageBoss(level,bossId);boss.setInvulnerable(false);level.addFreshEntity(boss);
            call(boss,"setRestrictionPoint",new Class<?>[]{GlobalPos.class},GlobalPos.of(level.dimension(),new BlockPos(8,220,8)));
            boss.setTarget(player);boss.getRandom().setSeed(842917L);
            ExhaustiveCapture.begin(bossId,name,player);began=true;action.run(boss);
            double lost=(20-player.getHealth())/2d;
            if(damage){
                check(shortId,name+"_hit",Double.isFinite(lost)&&lost>0);
                if(Double.isFinite(expected)&&expected>=0)eq(shortId,name+"_hearts",expected,lost);
                else if(Double.isFinite(expected))check(shortId,name+"_within_cap",lost<=-expected+.005);
            }
            note(shortId,name,damage?"native_attack":"mechanic","Actual health20 -> "+player.getHealth()+"; hearts="+lost+"; fireTicks="+player.getRemainingFireTicks()+"; effects="+player.getActiveEffects());
        }catch(Throwable ex){BossBalanceTests.error("exhaustive_tf_"+name,bossId,ex);note(shortId,name,"uncovered",rootCause(ex).toString());}
        finally{
            if(began)ExhaustiveCapture.end();player.stopRiding();
            for(Entity entity:newEntities(previous))if(entity!=player)entity.discard();
            if(boss!=null)boss.discard();
        }
    }
    private static void lichPhase(Mob b,int phase)throws Exception{call(b,"setShieldStrength",new Class<?>[]{int.class},phase==1?6:0);call(b,"setMinionsToSummon",new Class<?>[]{int.class},phase==3?0:9);call(b,"setTeleportInvisibility",new Class<?>[]{int.class},0);eq("lich","phase_setup_"+phase,phase,((Number)call(b,"getPhase",NONE)).intValue());}
    private static void snowPhase(Mob b,String name)throws Exception{Class<?> type=Class.forName("twilightforest.entity.boss.SnowQueen$Phase");call(b,"setCurrentPhase",new Class<?>[]{type},constant(type,name));}
    private static void headState(Entity h,String name)throws Exception{Class<?> type=Class.forName("twilightforest.entity.boss.HydraHeadContainer$State");call(h,"setState",new Class<?>[]{type},constant(type,name));}
    private static Entity urBall(Mob b)throws Exception{
        Object goal=construct("twilightforest.entity.ai.goal.UrGhastAttackGoal",new Class<?>[]{b.getClass()},b);
        Set<Entity> before=entities();call(goal,"spitFireball",NONE);
        List<Entity> volley=newEntities(before).stream().filter(e->e.getClass().getName().equals("twilightforest.entity.projectile.UrGhastFireball")).toList();
        eq("ur_ghast","native_volley_count",3,volley.size());
        if(volley.isEmpty())throw new IllegalStateException("Native UrGhast volley empty");
        return volley.getFirst();
    }
    private static Entity ghastlingBall(Mob b)throws Exception{
        Set<Entity> before=entities();call(b,"spawnMinionGhastsAt",new Class<?>[]{ServerLevel.class,int.class,int.class,int.class},level,8,225,8);
        Mob minion=(Mob)oneNew(before,"twilightforest:carminite_ghastling");minion.setTarget(player);
        Object goal=construct("twilightforest.entity.ai.goal.GhastguardAttackGoal",new Class<?>[]{Class.forName("twilightforest.entity.monster.CarminiteGhastguard")},minion);
        before=entities();call(goal,"spitFireball",NONE);return oneNew(before,"minecraft:fireball");
    }
    private static Entity hydraMortar(Mob b)throws Exception{
        Object container=((Object[])field(b,"hc"))[0];Entity head=(Entity)field(container,"headEntity");
        head.moveTo(8,220,34,180,0);Class<?> state=Class.forName("twilightforest.entity.boss.HydraHeadContainer$State");
        set(container,"currentState",constant(state,"MORTAR_SHOOTING"));set(container,"ticksProgress",10);
        call(container,"setTargetEntity",new Class<?>[]{Entity.class},player);
        Set<Entity> before=entities();call(container,"executeAttacks",NONE);
        Entity mortar=oneNew(before,"twilightforest:hydra_mortar");
        note("hydra","native_mortar_setup","mechanic","Head LOS="+call(head,"canEntityBeSeen",new Class<?>[]{Entity.class},player)+"; native megaBlast="+field(mortar,"megaBlast"));
        check("hydra","native_mortar_parent_owner",((Projectile)mortar).getOwner()==b);return mortar;
    }
    private static void clearYetiThrow()throws Exception{
        Object holder=Class.forName("twilightforest.init.TFDataAttachments").getField("YETI_THROWING").get(null);
        Object attachment=player.getData((java.util.function.Supplier)holder);
        call(attachment,"setThrown",new Class<?>[]{Player.class,boolean.class,LivingEntity.class},player,false,null);
        call(attachment,"setThrowCooldown",new Class<?>[]{Player.class,int.class},player,0);
    }
    private static void raiseShield()throws Exception{
        player.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
        set(player,"useItemRemaining",71990);check("all","native_shield_raised",player.isBlocking());
    }
    private static void equipPlayerSword()throws Exception{
        ItemStack sword=new ItemStack(Items.NETHERITE_SWORD);
        sword.enchant(player.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS),5);
        player.setItemSlot(EquipmentSlot.MAINHAND,sword);BossBalanceTests.updateEquipment(player);
    }
    private static void playerSwordHit(Mob target)throws Exception{
        target.invulnerableTime=0;target.hurtTime=0;set(player,"attackStrengthTicker",100);player.attack(target);
    }
    private static void nativePlayerTick()throws Exception{
        try{
            player.tickCount++;player.setDeltaMovement(Vec3.ZERO);
            var lookup=java.lang.invoke.MethodHandles.privateLookupIn(net.neoforged.neoforge.common.util.FakePlayer.class,java.lang.invoke.MethodHandles.lookup());
            var method=lookup.findSpecial(net.minecraft.server.level.ServerPlayer.class,"tick",java.lang.invoke.MethodType.methodType(void.class),net.neoforged.neoforge.common.util.FakePlayer.class);
            method.invoke((net.neoforged.neoforge.common.util.FakePlayer)player);player.doTick();
        }catch(Throwable error){throw new IllegalStateException("Native player lifecycle failed in isolated status followup",error);}
    }
    private static void nativeBurnTick(String attack,int tick)throws Exception{
        nativeBurnTick("hydra",attack,tick);
    }
    private static void nativeBurnTick(String boss,String attack,int tick)throws Exception{
        String before="fireTicks="+player.getRemainingFireTicks()+", inRain="+level.isRainingAt(player.blockPosition())+", inWaterRainOrBubble="+player.isInWaterRainOrBubble()+", fireImmune="+player.fireImmune()+", powderSnow="+player.isInPowderSnow+", HP="+player.getHealth();
        nativePlayerTick();
        if(tick<3)note(boss,attack+"_burn_tick_"+tick,"mechanic",before+" -> fireTicks="+player.getRemainingFireTicks()+", HP="+player.getHealth()+"; actual damage source types are recorded in capture hits.");
    }
    private static Entity projectile(String id,Mob owner){Entity e=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id)).create(level);if(!(e instanceof Projectile p))throw new IllegalStateException("Projectile missing:"+id);p.setOwner(owner);e.moveTo(player.position());level.addFreshEntity(e);return e;}
    private static void hit(Entity projectile,Entity victim)throws Exception{call(projectile,"onHitEntity",new Class<?>[]{EntityHitResult.class},new EntityHitResult(victim));}
    private static BlockHitResult groundHit(){return new BlockHitResult(player.position(),Direction.UP,player.blockPosition().below(),false);}
    private static ItemStack item(String id){return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));}
    private static Set<Entity> entities(){Set<Entity> result=new HashSet<>();level.getAllEntities().forEach(result::add);return result;}
    private static List<Entity> newEntities(Set<Entity> before){List<Entity> result=new ArrayList<>();for(Entity e:level.getAllEntities())if(!before.contains(e))result.add(e);return result;}
    private static Entity oneNew(Set<Entity> before,String type){return newEntities(before).stream().filter(e->id(e).equals(type)).findFirst().orElseThrow(()->new IllegalStateException("Native action did not spawn "+type));}
    private static String id(Entity e){return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();}
    private static void note(String boss,String move,String status,String detail){ExhaustiveCapture.note(boss.contains(":")?boss:"twilightforest:"+boss,move,status,detail);}
    private static void check(String boss,String name,boolean ok){BossBalanceTests.result("exhaustive_tf_"+name,"twilightforest:"+boss,1,ok?1:0);}
    private static void eq(String boss,String name,double wanted,double found){BossBalanceTests.result("exhaustive_tf_"+name,"twilightforest:"+boss,wanted,found);}
    private static void eq(String boss,String name,String wanted,String found){check(boss,name,wanted.equals(found));}
    private static Throwable rootCause(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
    private static Object construct(String type,Class<?>[] args,Object...values)throws Exception{return Class.forName(type).getConstructor(args).newInstance(values);}
    private static Object constant(Class<?> type,String name)throws Exception{Field f=type.getField(name);f.setAccessible(true);return f.get(null);}
    private static Field findField(Object object,String name)throws Exception{for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private static Object field(Object object,String name)throws Exception{return findField(object,name).get(object);}
    private static void set(Object object,String name,Object value)throws Exception{findField(object,name).set(object,value);}
    private static Object call(Object object,String name,Class<?>[] args,Object...values)throws Exception{for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name,args);m.setAccessible(true);return m.invoke(object,values);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(object.getClass().getName()+"."+name);}
}

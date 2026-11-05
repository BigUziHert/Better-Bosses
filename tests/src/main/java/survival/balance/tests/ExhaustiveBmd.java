package survival.balance.tests;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Installed BMD action/phase integration probes. All objects and block edits belong to the test world. */
final class ExhaustiveBmd {
    private static final String NS="bosses_of_mass_destruction:";
    private static final String PK="com.cerbon.bosses_of_mass_destruction.";
    private static final Supplier<Boolean> NEVER=()->false;
    private interface Probe { void run(LivingEntity boss) throws Exception; }

    static void run(ServerLevel level,BossBalanceTests.TestPlayer player) {
        for(String boss:new String[]{"lich","obsidilith","gauntlet","void_blossom"}) {
            if(!ExhaustiveCapture.selected(NS+boss)) continue;
            ExhaustiveCapture.note(NS+boss,"methodology","mechanic",
                "Installed native action methods and schedulers, with action cancellation disabled for deterministic component execution; deterministic collision delivery to real 20-HP unenchanted diamond player. This is component integration, not an autonomous client boss fight. Each accepted projectile hit is isolated with fresh player health and invulnerability frames.");
            if(boss.equals("lich")) lich(level,player);
            if(boss.equals("obsidilith")) obsidilith(level,player);
            if(boss.equals("gauntlet")) gauntlet(level,player);
            if(boss.equals("void_blossom")) blossom(level,player);
        }
        if(ExhaustiveCapture.selected(NS+"obsidilith"))obsidilithDeath(level,player);
        if(ExhaustiveCapture.selected(NS+"gauntlet"))gauntletDeath(level,player);
    }

    private static void lich(ServerLevel level,BossBalanceTests.TestPlayer p) {
        for(double fraction:new double[]{.9,.6,.4,.2}) {
            for(String name:new String[]{"VolleyAction","VolleyRageAction","CometAction","CometRageAction"})
                scenario(level,p,"lich",name+"_hp"+fraction,b->{
                    b.setHealth((float)(b.getMaxHealth()*fraction));
                    Object a=action(b,name);Set<Integer> before=entities(level);
                    int cooldown=((Number)call(a,"perform")).intValue();tickAction(a,b,Math.min(600,Math.max(240,cooldown+80)));
                    List<Entity> spawned=added(level,before);long count=spawned.stream().filter(e->e instanceof Projectile).count();
                    BossBalanceTests.result("bmd_spawned_"+name,NS+"lich",1,count>0?1:0);
                    for(Entity e:spawned)if(e instanceof Projectile)e.moveTo(8,280,8,0,0);
                    int i=0;for(Entity e:spawned) if(e instanceof Projectile) {
                        BossBalanceTests.resetActualPlayer(p);e.moveTo(p.getX(),p.getY(),p.getZ(),0,0);
                        String move=name+"_hp"+fraction+"_projectile"+(i++);
                        capture(p,NS+"lich",move,()->call(e,"entityHit",new EntityHitResult(p)),name.contains("Comet")?12:5,name.contains("Comet"));
                        if(i==1&&!name.contains("Comet"))BossBalanceTests.result("bmd_lich_missile_slowness",NS+"lich",1,p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)?1:0);
                        e.discard();
                    }
                    ExhaustiveCapture.note(NS+"lich",name+"_hp"+fraction,"mechanic","Native perform/scheduler emitted "+count+" projectiles; cooldown "+cooldown+" ticks. All emitted projectile callbacks were delivered separately.");
                });
            for(String name:new String[]{"MinionAction","MinionRageAction"})
                scenario(level,p,"lich",name+"_hp"+fraction,b->{
                    b.setHealth((float)(b.getMaxHealth()*fraction));Object a=action(b,name);Set<Integer> before=entities(level);
                    int cooldown=((Number)call(a,"perform")).intValue();tickAction(a,b,Math.min(600,Math.max(240,cooldown+80)));
                    List<Entity> spawned=added(level,before);long count=spawned.stream().filter(e->e instanceof LivingEntity).count();
                    BossBalanceTests.result("bmd_minions_"+name,NS+"lich",1,count>0?1:0);
                    Mob minion=(Mob)spawned.stream().filter(e->e instanceof Mob).findFirst().orElseThrow();BossBalanceTests.resetActualPlayer(p);Map<?,?> profileBefore=profileSnapshot();
                    ExhaustiveCapture.begin(NS+"lich",name+"_minion_melee_hp"+fraction,p);List<ExhaustiveCapture.Hit> minionHits;try{minion.doHurtTarget(p);}finally{minionHits=ExhaustiveCapture.end();}
                    BossBalanceTests.result("bmd_summoned_minion_damage_observed",NS+"lich",1,minionHits.isEmpty()?0:1);BossBalanceTests.result("bmd_summoned_minion_profile_unchanged",NS+"lich",1,profileBefore.equals(profileSnapshot())?1:0);
                    ExhaustiveCapture.note(NS+"lich",name+"_hp"+fraction,"mechanic","Native summoning emitted "+count+" living entities: "+spawned.stream().map(e->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()).toList()+". One spawned minion's native melee was measured and boss-profile counters were checked unchanged.");
                });
        }
        scenario(level,p,"lich","TeleportAction",b->{Object a=action(b,"TeleportAction");Vec3 before=b.position();call(a,"perform");tickAction(a,b,240);double distance=b.position().distanceTo(before);
            ExhaustiveCapture.note(NS+"lich","TeleportAction",distance>0?"mechanic":"uncovered","Native teleport action/scheduler displacement="+distance+" blocks; terrain-dependent placement.");
            BossBalanceTests.result("bmd_teleport",NS+"lich",1,distance>0?1:0);
        });
        thresholdTests(level,p,"lich","LichMoveLogic","priorityMoves");
        idleHealing(level,p,"lich");
    }

    private static void obsidilith(ServerLevel level,BossBalanceTests.TestPlayer p) {
        for(double fraction:new double[]{.9,.6,.4,.2}) {
            for(String name:new String[]{"BurstAction","WaveAction","SpikeAction"}) scenario(level,p,"obsidilith",name+"_hp"+fraction,b->{
                b.setHealth((float)(b.getMaxHealth()*fraction));Object a=action(b,name);
                capture(p,NS+"obsidilith",name+"_hp"+fraction,()->call(a,"damageEntity",p),name.equals("SpikeAction")?8:4,false);
                if(name.equals("SpikeAction")) BossBalanceTests.result("bmd_spike_slowness",NS+"obsidilith",1,p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)?1:0);
                BossBalanceTests.resetActualPlayer(p);if(name.equals("BurstAction"))p.moveTo(b.position().add(7,0,0));
                capture(p,NS+"obsidilith",name+"_scheduler_geometry_hp"+fraction,()->{call(a,"perform");if(name.equals("SpikeAction")){tickAction(a,b,32);p.moveTo(p.position().add((Vec3)((List<?>)get(a,"circlePoints")).getFirst()));}tickAction(a,b,300);},name.equals("SpikeAction")?8:4,false);
            });
            scenario(level,p,"obsidilith","AnvilAction_hp"+fraction,b->{
                b.setHealth((float)(b.getMaxHealth()*fraction));Object a=action(b,"AnvilAction");b.moveTo(p.position());
                capture(p,NS+"obsidilith","AnvilAction_hp"+fraction,()->call(a,"lambda$performAttack$5",p.position(),level),15,true);
            });
            scenario(level,p,"obsidilith","PillarAction_hp"+fraction,b->{
                b.setHealth((float)(b.getMaxHealth()*fraction));Object a=action(b,"PillarAction");call(a,"perform");tickAction(a,b,100);
                int runes=countBlocks(level,"obsidilith_rune");BossBalanceTests.result("bmd_pillar_runes",NS+"obsidilith",1,runes>0?1:0);
                ExhaustiveCapture.note(NS+"obsidilith","PillarAction_hp"+fraction,"mechanic","Native pillar action built "+runes+" rune blocks in test arena.");
                tickBlocks(level,"obsidilith_rune");call(b,"serverTick",level);
                BossBalanceTests.result("bmd_native_rune_shield_link",NS+"obsidilith",1,Boolean.TRUE.equals(getData(b,"isShielded"))?1:0);
                b.setInvulnerable(false);float hp=b.getHealth();b.invulnerableTime=0;b.hurt(level.damageSources().playerAttack(p),10);
                BossBalanceTests.result("bmd_pillar_shield_rejects_damage",NS+"obsidilith",hp,b.getHealth());
                for(BlockPos pos:blockPositions(level,"obsidilith_rune"))level.destroyBlock(pos,false);call(b,"serverTick",level);
                BossBalanceTests.result("bmd_native_rune_shield_release",NS+"obsidilith",0,Boolean.TRUE.equals(getData(b,"isShielded"))?1:0);
                b.invulnerableTime=0;b.hurt(level.damageSources().playerAttack(p),10);
                BossBalanceTests.result("bmd_pillar_unshielded_accepts_damage",NS+"obsidilith",1,b.getHealth()<hp?1:0);
                ExhaustiveCapture.note(NS+"obsidilith","rune_link_and_shield_release","mechanic","Native rune block ticks link the actual boss; server tick enables shield; destroying every placed rune followed by native server tick disables shield. Incoming player attack is rejected while shielded and accepted afterward.");
            });
        }
        thresholdTests(level,p,"obsidilith","ObsidilithMoveLogic","shouldDoPillarDefense");
    }

    private static void gauntlet(ServerLevel level,BossBalanceTests.TestPlayer p) {
        for(double fraction:new double[]{.9,.8,.6,.4}) {
            for(String name:new String[]{"PunchAction","SwirlPunchAction"}) scenario(level,p,"gauntlet",name+"_hp"+fraction,b->{
                b.setHealth((float)(b.getMaxHealth()*fraction));Object a=action(b,name);p.moveTo(b.position());
                capture(p,NS+"gauntlet",name+"_entityImpact_hp"+fraction,()->call(a,"testEntityImpact"),9,false);
                BossBalanceTests.resetActualPlayer(p);b.moveTo(p.position());set(a,"previousSpeed",3d);b.horizontalCollision=true;
                if(name.equals("SwirlPunchAction")){call(a,"perform");BossBalanceTests.result("bmd_native_swirl_energized",NS+"gauntlet",1,Boolean.TRUE.equals(getData(b,"isEnergized"))?1:0);}
                capture(p,NS+"gauntlet",name+"_wallImpact_hp"+fraction,()->call(a,"testBlockPhysicalImpact"),14,true);
                if(name.equals("SwirlPunchAction")){BossBalanceTests.result("bmd_energized_punch_consumed",NS+"gauntlet",0,Boolean.TRUE.equals(getData(b,"isEnergized"))?1:0);BossBalanceTests.resetActualPlayer(p);b.moveTo(p.position());capture(p,NS+"gauntlet",name+"_unenergizedWallImpact_hp"+fraction,()->call(a,"testBlockPhysicalImpact"),14,true);}
            });
            scenario(level,p,"gauntlet","LaserAction_hp"+fraction,b->{
                b.setHealth((float)(b.getMaxHealth()*fraction));Object a=action(b,"LaserAction");Vec3 end=b.getEyePosition().add(p.getEyePosition().subtract(b.getEyePosition()).scale(2));
                capture(p,NS+"gauntlet","LaserAction_hp"+fraction,()->call(a,"applyLaserToEntities",end),1,false);
            });
            scenario(level,p,"gauntlet","BlindnessAction_hp"+fraction,b->{
                b.setHealth((float)(b.getMaxHealth()*fraction));Object a=action(b,"BlindnessAction");ExhaustiveCapture.begin(NS+"gauntlet","BlindnessAction_hp"+fraction,p);
                try{call(a,"perform");tickAction(a,b,240);}finally{ExhaustiveCapture.end();}
                BossBalanceTests.result("bmd_blindness_effect",NS+"gauntlet",1,p.hasEffect(MobEffects.BLINDNESS)?1:0);
                ExhaustiveCapture.note(NS+"gauntlet","BlindnessAction_hp"+fraction,"mechanic","Native action scheduler: blindness="+p.hasEffect(MobEffects.BLINDNESS)+", energized="+getData(b,"isEnergized")+". Client overlay appearance is outside server harness.");
            });
            scenario(level,p,"gauntlet","move_unlocks_hp"+fraction,b->{
                b.setHealth((float)(b.getMaxHealth()*fraction));Object logic=find(b,"GauntletMoveLogic");Set<Integer> choices=new TreeSet<>();
                for(int i=0;i<1200;i++) choices.add(((Number)call(logic,"chooseMove")).intValue());
                Set<Integer> expected=new TreeSet<>(Set.of(4));if(fraction<.85)expected.add(8);if(fraction<.7)expected.add(10);if(fraction<.5)expected.add(11);
                BossBalanceTests.result("bmd_gauntlet_phase_choices",NS+"gauntlet",1,expected.equals(choices)?1:0);
                ExhaustiveCapture.note(NS+"gauntlet","move_unlocks_hp"+fraction,"transition","Native chooseMove sampled 1200 choices: "+choices+"; expected "+expected+" (4=punch, 8=laser, 10=swirl, 11=blindness).");
            });
        }
    }

    private static void gauntletDeath(ServerLevel level,BossBalanceTests.TestPlayer p){
        scenario(level,p,"gauntlet","ordinary_ownerless_explosion_control",b->{
            Vec3 center=new Vec3(8,250,8);p.moveTo(center.add(3,0,0));Map<?,?> before=new HashMap<>((Map<?,?>)Class.forName("survival.balance.BossDamageBalance").getMethod("profileCounts").invoke(null));
            capture(p,NS+"gauntlet","ordinary_ownerless_explosion_control",()->level.explode(null,center.x,center.y,center.z,4,net.minecraft.world.level.Level.ExplosionInteraction.MOB),BalanceExpectations.diamondHearts(44.15625)*2,false);
            Map<?,?> after=(Map<?,?>)Class.forName("survival.balance.BossDamageBalance").getMethod("profileCounts").invoke(null);
            BossBalanceTests.result("bmd_ownerless_nonboss_profile_unchanged",NS+"gauntlet",1,before.equals(after)?1:0);
            ExhaustiveCapture.note(NS+"gauntlet","ordinary_ownerless_explosion_control","mechanic","Ordinary ownerless power4 explosion at3 blocks in open air, with no native Gauntlet death frame; damage/profile regression control.");
        });
        for(double distance:new double[]{3,0})scenario(level,p,"gauntlet","death_explosion_distance"+distance,b->{
            Object death=find(b,"ServerGauntletDeathHandler");b.moveTo(8,250,8,0,0);b.setHealth(0);b.deathTime=49;p.moveTo(b.position().add(distance,0,0));
            capture(p,NS+"gauntlet","death_explosion_native_tick50_distance"+distance,()->call(death,"tick",level),14,true);
            ExhaustiveCapture.note(NS+"gauntlet","death_explosion_native_tick50_distance"+distance,"mechanic","Native zero-health death handler detonates power4 at deathTime50 with null owner; distance="+distance+", player remaining HP="+p.getHealth()+". Hit record retains full native post-mitigation damage even if greater than available20HP. Point-blank probe runs last so a lethal result cannot contaminate other scenarios.");
            clearDeathLoot(level,250);
        });
    }

    private static void obsidilithDeath(ServerLevel level,BossBalanceTests.TestPlayer p){
        scenario(level,p,"obsidilith","death_explosion_native_die",b->{
            b.moveTo(8,270,8,0,0);p.moveTo(b.position());boolean enabled=(Boolean)get(get(b,"mobConfig"),"spawnPillarOnDeath");b.setHealth(0);
            ExhaustiveCapture.begin(NS+"obsidilith","death_explosion_native_die",p);List<ExhaustiveCapture.Hit> hits;
            try{b.die(level.damageSources().playerAttack(p));}finally{hits=ExhaustiveCapture.end();}
            if(enabled)validateHits(NS+"obsidilith","death_explosion_native_die",hits,15,true);else BossBalanceTests.result("bmd_death_explosion_disabled",NS+"obsidilith",0,hits.size());
            tickLevelScheduler(level,110);clearDeathLoot(level,270);
            ExhaustiveCapture.note(NS+"obsidilith","death_explosion_native_die","mechanic","Native die invokes owner-attributed power2 explosion when spawnPillarOnDeath is enabled; installed flag="+enabled+". Reward pillar/loot scheduler drained after measurement.");
        });
    }

    private static void blossom(ServerLevel level,BossBalanceTests.TestPlayer p) {
        for(double fraction:new double[]{.9,.6,.4,.2}) {
            for(String name:new String[]{"SpikeAction","SpikeWaveAction"}) scenario(level,p,"void_blossom",name+"_hp"+fraction,b->{
                b.setHealth((float)(b.getMaxHealth()*fraction));Object a=action(b,name);
                ExhaustiveCapture.begin(NS+"void_blossom",name+"_hp"+fraction,p);List<ExhaustiveCapture.Hit> hits;
                try{call(a,"perform");if(name.equals("SpikeAction")){tickAction(a,b,32);p.moveTo(p.position().add((Vec3)((List<?>)get(a,"circlePoints")).getFirst()));}tickAction(a,b,400);}finally{hits=ExhaustiveCapture.end();}
                validateHits(NS+"void_blossom",name,hits,9,false);
            });
            for(String name:new String[]{"BladeAction","SporeAction"}) scenario(level,p,"void_blossom",name+"_hp"+fraction,b->{
                b.setHealth((float)(b.getMaxHealth()*fraction));Object a=action(b,name);Set<Integer> before=entities(level);
                call(a,"perform");tickAction(a,b,300);List<Entity> spawned=added(level,before);int count=0;
                for(Entity e:spawned) if(e instanceof Projectile) {BossBalanceTests.resetActualPlayer(p);e.moveTo(p.position());
                    capture(p,NS+"void_blossom",name+"_hp"+fraction+"_projectile"+(count++),()->call(e,"entityHit",new EntityHitResult(p)),name.equals("BladeAction")?9:2.25,false);
                    if(name.equals("SporeAction")){
                        BossBalanceTests.resetActualPlayer(p);e.moveTo(p.position());
                        capture(p,NS+"void_blossom","SporeAction_ground_burst_hp"+fraction,()->{call(e,"onImpact");p.moveTo(e.position().add((Vec3)((List<?>)get(e,"circlePoints")).getFirst()).add(.5,0,.5));tickLevelScheduler(level,40);},2.25,false);
                        BossBalanceTests.result("bmd_spore_poison_applied",NS+"void_blossom",1,p.hasEffect(MobEffects.POISON)?1:0);
                        var poison=p.getEffect(MobEffects.POISON);if(poison!=null){p.setHealth(20);int ticks=poison.getDuration();
                            capture(p,NS+"void_blossom","SporeAction_poison_ticks_hp"+fraction,()->{for(int tick=0;tick<ticks;tick++){if(p.invulnerableTime>0)p.invulnerableTime--;poison.tick(p,()->{});}},1,false);
                        }
                    }
                    e.discard();}
                BossBalanceTests.result("bmd_blossom_projectiles_"+name,NS+"void_blossom",1,count>0?1:0);
                ExhaustiveCapture.note(NS+"void_blossom",name+"_hp"+fraction,"mechanic","Native perform/scheduler emitted "+count+" projectiles; all collision callbacks sampled separately. Spore ground burst and poison duration are measured separately from direct impact.");
            });
            scenario(level,p,"void_blossom","BlossomAction_hp"+fraction,b->{
                b.setHealth((float)(b.getMaxHealth()*fraction));Object a=action(b,"BlossomAction");Object before=getData(b,"hitbox");call(a,"perform");tickAction(a,b,30);Object during=getData(b,"hitbox");tickAction(a,b,140);Object after=getData(b,"hitbox");
                int flowers=countBlocks(level,"void_blossom");BossBalanceTests.result("bmd_healing_blossoms_placed",NS+"void_blossom",8,flowers);
                BossBalanceTests.result("bmd_blossom_hitbox_restored",NS+"void_blossom",1,Objects.equals(before,after)?1:0);
                ExhaustiveCapture.note(NS+"void_blossom","BlossomAction_hp"+fraction,"mechanic","Native action placed "+flowers+" flowers; hitbox before/during/after="+before+"/"+during+"/"+after+"; vine walls="+countBlocks(level,"vine_wall")+".");
                float start=b.getHealth();float ceiling=(float)(b.getMaxHealth()*Math.ceil(fraction*4)/4)-1;tickBlocks(level,"void_blossom");tickLevelScheduler(level,40);
                BossBalanceTests.result("bmd_flower_heal_amount",NS+"void_blossom",Math.min(ceiling,start+flowers*10),b.getHealth());
                b.setHealth(ceiling-3);tickBlocks(level,"void_blossom");tickLevelScheduler(level,40);
                BossBalanceTests.result("bmd_flower_heal_phase_cap",NS+"void_blossom",ceiling,b.getHealth());
                ExhaustiveCapture.note(NS+"void_blossom","flower_healing_ticks_hp"+fraction,"mechanic","Native placed-flower ticks + delayed level scheduler heal 10 HP per flower and stop one HP below the current quarter-health milestone, cap="+ceiling+" HP.");
            });
            scenario(level,p,"void_blossom","passive_contact_thorns_hp"+fraction,b->{
                b.setHealth((float)(b.getMaxHealth()*fraction));p.moveTo(b.position());Object tick=find(b,"VoidBlossomSpikeTick");
                ExhaustiveCapture.begin(NS+"void_blossom","passive_contact_thorns_hp"+fraction,p);try{call(tick,"tick",level);}finally{List<ExhaustiveCapture.Hit> hits=ExhaustiveCapture.end();BossBalanceTests.result("bmd_contact_damage_observed",NS+"void_blossom",1,hits.isEmpty()?0:1);}
                ExhaustiveCapture.note(NS+"void_blossom","passive_contact_thorns_hp"+fraction,"mechanic","Native passive contact uses vanilla thorns and is intentionally excluded from outgoing boss attack profiles.");
            });
            scenario(level,p,"void_blossom","move_unlocks_hp"+fraction,b->{
                b.setHealth((float)(b.getMaxHealth()*fraction));Object logic=find(b,"VoidBlossomMoveLogic");Set<Integer> choices=new TreeSet<>();Class<?> api=Class.forName(PK+"entity.ai.action.IActionWithCooldown");Map<Byte,Object> recordings=new HashMap<>();
                for(byte key:new byte[]{4,5,7,8,9})recordings.put(key,Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(proxy,method,args)->{if(method.getName().equals("perform")){choices.add((int)key);return 1;}throw new UnsupportedOperationException(method.getName());}));
                set(logic,"actions",recordings);set(logic,"doBlossom",NEVER);for(int i=0;i<400;i++)call(logic,"perform");Set<Integer> expected=new TreeSet<>(Set.of(4,5));if(fraction<.75)expected.add(7);if(fraction<.5)expected.add(8);
                BossBalanceTests.result("bmd_blossom_phase_choices",NS+"void_blossom",1,choices.equals(expected)?1:0);
                ExhaustiveCapture.note(NS+"void_blossom","move_unlocks_hp"+fraction,"transition","Native selector sampled400 times with recording action callbacks: "+choices+" expected "+expected+" (4=spikes,5=waves,7=spore,8=blades); actual attack implementations are tested in separate native damage scenarios.");
            });
        }
        thresholdTests(level,p,"void_blossom","VoidBlossomMoveLogic","doBlossom");
        idleHealing(level,p,"void_blossom");
    }

    private static void idleHealing(ServerLevel level,BossBalanceTests.TestPlayer p,String id) {
        for(double ceiling:new double[]{1,.75,.5,.25}) scenario(level,p,id,"idle_healing_cap_"+ceiling,b->{
            Object heal=find(b,"CappedHeal");float limit=(float)(b.getMaxHealth()*ceiling)-1;b.setHealth(limit-1);
            call(heal,"tick",level);BossBalanceTests.result("bmd_no_idle_heal_with_target",NS+id,limit-1,b.getHealth());
            ((Mob)b).setTarget(null);for(int i=0;i<40;i++)call(heal,"tick",level);
            BossBalanceTests.result("bmd_idle_heal_quarter_cap",NS+id,limit,b.getHealth());
            ExhaustiveCapture.note(NS+id,"idle_healing_cap_"+ceiling,"mechanic","Native CappedHeal refuses healing while a target exists and heals to "+limit+" HP after target removal: one HP below the quarter-health milestone.");
        });
    }

    private static void thresholdTests(ServerLevel level,BossBalanceTests.TestPlayer p,String id,String logicName,String stateName) {
        for(double threshold:new double[]{.75,.5,.25}) scenario(level,p,id,"threshold_"+threshold,b->{
            Object logic=find(b,logicName);Object staged=id.equals("void_blossom")?find(b,"StagedDamageHandler"):find(logic,"StagedDamageHandler");
            Object stats=Class.forName(PK+"entity.util.EntityStats").getConstructor(LivingEntity.class).newInstance(b);
            b.setHealth((float)(b.getMaxHealth()*(threshold+.01)));
            call(staged,"beforeDamage",stats,level.damageSources().playerAttack(p),10f);
            b.setHealth((float)(b.getMaxHealth()*(threshold-.01)));
            call(staged,"afterDamage",stats,level.damageSources().playerAttack(p),10f,true);
            Object state=get(logic,stateName);if(state instanceof Supplier<?> supplier)state=supplier.get();
            boolean triggered=state instanceof Collection<?> collection?!collection.isEmpty():Boolean.TRUE.equals(state);
            if(id.equals("lich"))triggered=state.equals(List.of((byte)12,(byte)11,(byte)8));
            BossBalanceTests.result("bmd_native_threshold_"+threshold,NS+id,1,triggered?1:0);
            ExhaustiveCapture.note(NS+id,"threshold_"+threshold,"transition","Native StagedDamageHandler before/after callbacks around deterministic HP boundary: "+stateName+"="+state+" at maxHP="+b.getMaxHealth()+". HP crossing is injected; this does not substitute for testing boss hitbox reception.");
        });
    }

    private static void scenario(ServerLevel level,BossBalanceTests.TestPlayer p,String id,String name,Probe probe) {
        LivingEntity boss=null;Set<Integer> before=entities(level);
        try {
            BossBalanceTests.resetActualPlayer(p);seedPositions(p);boss=BossBalanceTests.damageBoss(level,NS+id);level.addFreshEntity(boss);((Mob)boss).setTarget(p);
            if(id.equals("gauntlet")){Object handler=find(boss,"GauntletGoalHandler");Object stats=Class.forName(PK+"entity.util.EntityStats").getConstructor(LivingEntity.class).newInstance(boss);call(handler,"afterDamage",stats,level.damageSources().playerAttack(p),1f,true);}
            probe.run(boss);
        }catch(Throwable error){Throwable cause=error instanceof InvocationTargetException&&error.getCause()!=null?error.getCause():error;BossBalanceTests.error("exhaustive_bmd_"+name,NS+id,cause);ExhaustiveCapture.note(NS+id,name,"uncovered",cause.toString());}
        finally {ExhaustiveCapture.end();for(Entity e:added(level,before)) if(e!=p)e.discard();if(boss!=null)boss.discard();cleanArena(level);}
    }
    private interface Throwing {void run() throws Exception;}
    private static void capture(BossBalanceTests.TestPlayer p,String id,String name,Throwing run,double damage,boolean cap) throws Exception {
        ExhaustiveCapture.begin(id,name,p);List<ExhaustiveCapture.Hit> hits;
        try{run.run();}finally{hits=ExhaustiveCapture.end();}
        validateHits(id,name,hits,damage,cap);
    }
    private static void validateHits(String id,String name,List<ExhaustiveCapture.Hit> hits,double damage,boolean cap) {
        BossBalanceTests.result("bmd_hit_observed_"+name,id,1,hits.isEmpty()?0:1);
        int i=0;for(var hit:hits) BossBalanceTests.result("bmd_damage_"+name+"_"+(i++),id,cap?1:damage,cap?(hit.damageHp()<=damage+.005?1:0):hit.damageHp());
        if(hits.isEmpty()) ExhaustiveCapture.note(id,name,"uncovered","Native action executed but no damage event reached the fixture player; this case does not establish damage correctness.");
    }

    private static Set<Integer> entities(ServerLevel level){Set<Integer> set=new HashSet<>();for(Entity e:level.getAllEntities())set.add(e.getId());return set;}
    private static Map<?,?> profileSnapshot()throws Exception{return new HashMap<>((Map<?,?>)Class.forName("survival.balance.BossDamageBalance").getMethod("profileCounts").invoke(null));}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void seedPositions(BossBalanceTests.TestPlayer player)throws Exception{Supplier attachment=(Supplier)Class.forName(PK+"neoforge.attachment.BMDAttachments").getField("HISTORICAL_DATA").get(null);Object history=player.getData(attachment);for(int i=0;i<10;i++)call(history,"add",player.position());}
    private static List<Entity> added(ServerLevel level,Set<Integer> before){List<Entity> list=new ArrayList<>();for(Entity e:level.getAllEntities())if(!before.contains(e.getId()))list.add(e);return list;}
    private static int countBlocks(ServerLevel level,String path){int n=0;for(BlockPos pos:BlockPos.betweenClosed(-24,219,-24,40,225,40))if(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString().equals(NS+path))n++;return n;}
    private static List<BlockPos> blockPositions(ServerLevel level,String path){List<BlockPos> found=new ArrayList<>();for(BlockPos pos:BlockPos.betweenClosed(-24,219,-24,40,225,40))if(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString().equals(NS+path))found.add(pos.immutable());return found;}
    private static void tickBlocks(ServerLevel level,String path)throws Exception{for(BlockPos pos:blockPositions(level,path)){var state=level.getBlockState(pos);state.tick(level,pos,level.random);}}
    private static void tickLevelScheduler(ServerLevel level,int ticks)throws Exception{Object scheduler=Class.forName("com.cerbon.cerbons_api.api.static_utilities.CapabilityUtils").getMethod("getLevelEventScheduler",net.minecraft.world.level.Level.class).invoke(null,level);for(int i=0;i<ticks;i++)call(scheduler,"updateEvents");}
    private static void cleanArena(ServerLevel level){for(BlockPos pos:BlockPos.betweenClosed(-24,219,-24,40,225,40)){var state=level.getBlockState(pos);String key=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();if(key.startsWith(NS)||state.is(Blocks.MOSS_BLOCK)||state.is(Blocks.OBSIDIAN)||state.is(Blocks.FIRE))level.setBlock(pos,pos.getY()==219?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);else if(pos.getY()==219&&!state.is(Blocks.STONE))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);}}
    private static void clearDeathLoot(ServerLevel level,int y){for(BlockPos pos:BlockPos.betweenClosed(-1,y-9,-1,17,y+18,17)){var state=level.getBlockState(pos);if(state.is(Blocks.NETHERRACK)||state.is(Blocks.ANCIENT_DEBRIS)||state.is(Blocks.CHEST)||state.is(Blocks.OBSIDIAN)||state.is(Blocks.SHULKER_BOX))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);}}
    private static Object action(LivingEntity boss,String name) throws Exception {Object a=find(boss,name);for(String field:new String[]{"shouldCancel","cancelAction"})try{set(a,field,NEVER);}catch(NoSuchFieldException ignored){}return a;}
    private static void tickAction(Object action,LivingEntity boss,int count) throws Exception {
        Set<Object> schedulers=Collections.newSetFromMap(new IdentityHashMap<>());
        for(String field:new String[]{"eventScheduler"})try{schedulers.add(get(action,field));}catch(NoSuchFieldException ignored){}
        schedulers.add(get(boss,"preTickEvents"));schedulers.add(get(boss,"postTickEvents"));schedulers.remove(null);
        for(int i=0;i<count;i++)for(Object scheduler:schedulers)call(scheduler,"updateEvents");
    }
    private static Object find(Object start,String simple) throws Exception {
        Deque<Object> queue=new ArrayDeque<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());queue.add(start);
        if(start instanceof Mob) {Object selector=get(start,"goalSelector");queue.add(call(selector,"getAvailableGoals"));}
        while(!queue.isEmpty()) {
            Object o=queue.remove();if(o==null||!seen.add(o))continue;Class<?> type=o.getClass();if(type.getSimpleName().equals(simple))return o;
            if(o instanceof Map<?,?> m){for(Object v:m.values())if(v!=null)queue.add(v);continue;}
            if(o instanceof Iterable<?> list){for(Object v:list)if(v!=null)queue.add(v);continue;}
            if(type.isArray()){for(int i=0;i<Array.getLength(o);i++){Object v=Array.get(o,i);if(v!=null)queue.add(v);}continue;}
            if(type.getSimpleName().equals("WrappedGoal")){queue.add(call(o,"getGoal"));continue;}
            for(Class<?> c=type;c!=null&&(c.getName().startsWith("com.cerbon.")||c.getName().startsWith("net.minecraft.world.entity.ai.goal."));c=c.getSuperclass())for(Field f:c.getDeclaredFields()) {
                if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive())continue;f.setAccessible(true);Object v=f.get(o);if(v!=null)queue.add(v);
            }
        }
        throw new NoSuchElementException("No "+simple+" in "+start.getClass().getName()+" action graph");
    }
    private static Field field(Class<?> c,String name) throws NoSuchFieldException {for(;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private static Object get(Object o,String name)throws Exception{return field(o.getClass(),name).get(o);}
    private static void set(Object o,String name,Object value)throws Exception{field(o.getClass(),name).set(o,value);}
    @SuppressWarnings({"unchecked","rawtypes"}) private static void setData(LivingEntity e,String name,Object value)throws Exception{e.getEntityData().set((EntityDataAccessor)field(e.getClass(),name).get(null),value);}
    @SuppressWarnings({"unchecked","rawtypes"}) private static Object getData(LivingEntity e,String name)throws Exception{return e.getEntityData().get((EntityDataAccessor)field(e.getClass(),name).get(null));}
    private static Object call(Object o,String name,Object...args)throws Exception {
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){boolean fits=true;Class<?>[] types=m.getParameterTypes();for(int i=0;i<args.length;i++)if(args[i]!=null&&!box(types[i]).isInstance(args[i]))fits=false;if(fits){m.setAccessible(true);return m.invoke(o,args);}}
        throw new NoSuchMethodException(o.getClass().getName()+"."+name+"/"+args.length);
    }
    private static Class<?> box(Class<?> c){if(c==boolean.class)return Boolean.class;if(c==float.class)return Float.class;if(c==double.class)return Double.class;if(c==int.class)return Integer.class;if(c==byte.class)return Byte.class;if(c==long.class)return Long.class;return c;}
}

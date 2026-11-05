package survival.balance.tests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/** Read-only encounter observation. No native return values or gameplay fields are changed. */
public final class EncounterTelemetry {
    private static final Gson JSON=new GsonBuilder().serializeSpecialFloatingPointValues().create();
    private static final Gson PRETTY=new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    private static final String BMD="com.cerbon.bosses_of_mass_destruction.";
    private static final List<String> GETTERS=List.of("getPhase","isPhase","getBossPhase","getAttackState","getCurrentPhase","getCurrentFormation",
        "getShieldStrength","getMinionsToSummon","getRage","getRageMeter","getMeltDown","getIsHelmetless",
        "isPowered","getIsBerserk","isBerserk","getIsPower","isPower","isMeltDown","getIsLaserMode","getIsAct","getShieldDurability","getShieldBroken","getIsShieldBreak","getIsAwaken","isDazed","isCharging","isStunlessCharging","isRampaging",
        "isTired","isInTantrum","isChargingAtPlayer","isGroundAttackCharge","isBreathing","getInvulnerableTicks");
    private static final Map<Class<?>,Map<String,Optional<Method>>> METHODS=new HashMap<>();
    private static final Map<Class<?>,Map<String,Optional<Field>>> FIELDS=new HashMap<>();
    private static EncounterTelemetry active;
    private static boolean installed;
    private final ServerLevel level;
    private final ServerPlayer player;
    private final List<LivingEntity> bosses;
    private final String managedBoss;
    private final Path path,coveragePath;
    private final BufferedWriter writer;
    private final Map<String,Map<String,Object>> lastStates=new LinkedHashMap<>();
    private final Map<String,Entry> inventory=new LinkedHashMap<>();
    private final Map<String,Long> counts=new LinkedHashMap<>(),callersSeen=new LinkedHashMap<>();
    private final Map<UUID,Entity> tracked=new LinkedHashMap<>();
    private final Map<UUID,String> owners=new HashMap<>();
    private final Map<DamageContainer,Attempt> pending=new IdentityHashMap<>();
    private final List<String> observationErrors=new ArrayList<>();
    private final Map<Class<?>,Map<Object,String>> animationNames=new HashMap<>();
    private final Map<String,Long> profileBaseline;
    private final long startGameTime;
    private int tick;
    private long sequence,attemptSequence;
    private boolean closed;
    private String effects="";
    private Entity lastPlayerVehicle;
    private static final class Entry {
        final String key,category,origin; final boolean required;
        int firstTick=-1,lastTick=-1;long observations;
        Entry(String key,String category,boolean required,String origin){this.key=key;this.category=category;this.required=required;this.origin=origin;}
        Map<String,Object> json(){Map<String,Object> r=new LinkedHashMap<>();r.put("key",key);r.put("category",category);r.put("requiredCandidate",required);r.put("origin",origin);r.put("observations",observations);r.put("firstTick",firstTick);r.put("lastTick",lastTick);r.put("status",observations>0?"observed_naturally":required?"not_observed":"optional_or_lifecycle_not_observed");return r;}
    }
    private static final class Attempt {
        final long id;final LivingEntity victim;final DamageSource source;final double hp,absorption,raw;final int tick;
        boolean cancelled,shield;List<String> callers;Map<String,Long> profileBefore;
        Attempt(long id,LivingIncomingDamageEvent event,int tick){this.id=id;victim=event.getEntity();source=event.getSource();hp=victim.getHealth();absorption=victim.getAbsorptionAmount();raw=event.getOriginalAmount();this.tick=tick;}
    }

    public static EncounterTelemetry start(ServerLevel level,ServerPlayer player,List<LivingEntity> bosses,String managedBossId,Path compactLog){
        if(!Boolean.getBoolean("survival.bossBalance.testMode"))throw new IllegalStateException("Encounter telemetry requires isolated test mode");
        if(active!=null&&!active.closed)throw new IllegalStateException("Previous encounter telemetry is still active");
        install();
        try{active=new EncounterTelemetry(level,player,bosses,managedBossId,compactLog);active.sample(0);return active;}
        catch(Exception error){throw new IllegalStateException("Cannot start encounter telemetry",error);}
    }
    private static void install(){
        if(installed)return;installed=true;
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,true,EncounterTelemetry::incomingFirst);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,EncounterTelemetry::incomingLast);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EncounterTelemetry::damagePost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,EncounterTelemetry::shield);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,EncounterTelemetry::impact);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,EncounterTelemetry::joined);
    }
    private EncounterTelemetry(ServerLevel level,ServerPlayer player,List<LivingEntity> bosses,String boss,Path path)throws Exception{
        this.level=level;this.player=player;this.bosses=List.copyOf(bosses);managedBoss=boss;this.path=path;startGameTime=level.getGameTime();
        coveragePath=path.resolveSibling(path.getFileName().toString().replace(".jsonl",".coverage.json"));
        Files.createDirectories(path.getParent());writer=Files.newBufferedWriter(path);profileBaseline=profiles();
        for(LivingEntity entity:bosses){tracked.put(entity.getUUID(),entity);catalogue(entity);}
        emit("telemetry_started",Map.of("schemaVersion",2,"exactDragonPhaseObserver",true,"catalogueVersion","installed-native-1.21.1-2026-11-05-r2","bossCount",bosses.size(),"catalogue",inventory.values().stream().map(Entry::json).toList(),
            "scope","Read-only per-tick state, native BMD action-entry hooks and damage/projectile events. State/selection is not an accepted hit. Early native rejects that never post an event are not identifiable misses."));
    }

    /** Call once each server encounter tick, before terminal-outcome checks. */
    public void sample(int encounterTick){
        if(closed)return;tick=encounterTick;
        for(LivingEntity boss:bosses){
            String subject=type(boss)+"#"+boss.getId();Map<String,Object> state=state(boss);
            Map<String,Object> old=lastStates.put(subject,state);
            if(!state.equals(old)){
                for(var field:state.entrySet())if(old==null||!Objects.equals(old.get(field.getKey()),field.getValue()))observe("state/"+field.getKey()+"="+field.getValue(),"state",false,"runtime getter or field");
                emit("state_transition",Map.of("subject",subject,"entity",entity(boss),"state",state,"animationFrame",animationFrame(boss)));
            }
        }
        for(var it=tracked.entrySet().iterator();it.hasNext();){var entry=it.next();Entity e=entry.getValue();
            if(e instanceof Projectile p){String owner=ownerIdentity(p.getOwner());String before=owners.put(entry.getKey(),owner);
                if(before!=null&&!before.equals(owner))emit("projectile_owner_changed",Map.of("projectile",entity(e),"previousOwner",before,"owner",entity(p.getOwner())));}
            if(e.isRemoved()&&!bosses.contains(e)){emit("tracked_entity_removed",Map.of("entity",entity(e),"removalReason",String.valueOf(e.getRemovalReason())));it.remove();owners.remove(entry.getKey());}
        }
        flushPending(false);
        String effectState=JSON.toJson(player.getActiveEffects().stream().map(e->Map.of("id",BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect().value()).toString(),"amplifier",e.getAmplifier())).toList());
        if(!effectState.equals(effects)){effects=effectState;emit("player_effects_changed",Map.of("effects",JSON.fromJson(effects,Object.class),"health",player.getHealth(),"absorption",player.getAbsorptionAmount(),"armor",player.getAttributeValue(Attributes.ARMOR),"toughness",player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));}
        Entity vehicle=player.getVehicle();
        if(vehicle!=lastPlayerVehicle){
            List<String> nativeGrabVehicles=EncounterMoveCatalog.grabVehicles(managedBoss);
            if(lastPlayerVehicle!=null&&nativeGrabVehicles.contains(type(lastPlayerVehicle)))observe("passenger_release/"+type(lastPlayerVehicle),"conditional_release",true,"actual native passenger relationship ended");
            if(vehicle!=null&&nativeGrabVehicles.contains(type(vehicle)))observe("passenger_mount/"+type(vehicle),"conditional_grab",true,"actual native passenger relationship started");
            emit("player_vehicle_changed",Map.of("previousVehicle",entity(lastPlayerVehicle),"vehicle",entity(vehicle),"health",player.getHealth(),"absorption",player.getAbsorptionAmount()));
            lastPlayerVehicle=vehicle;
        }
        for(var effect:player.getActiveEffects())observe("player_effect/"+BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()),"status_application",false,"native player effect present; observations are samples, not independent applications");
        if(tick%20==0)flush();
    }

    /** Closes observation before fixture cleanup. A win and complete move coverage remain independent facts. */
    public Map<String,Object> finish(String outcome,int encounterTick){
        if(closed)return Map.of("file",path.getFileName().toString(),"alreadyClosed",true);
        sample(encounterTick);flushPending(true);
        Map<String,Object> ledger=new LinkedHashMap<>();ledger.put("schemaVersion",2);ledger.put("exactDragonPhaseObserver",true);ledger.put("catalogueVersion","installed-native-1.21.1-2026-11-05-r2");ledger.put("boss",managedBoss);ledger.put("outcome",outcome);ledger.put("ticks",tick);
        ledger.put("naturalDefeatObserved",outcome.equals("won"));ledger.put("entries",inventory.values().stream().map(Entry::json).toList());
        List<String> missing=inventory.values().stream().filter(e->e.required&&e.observations==0).map(e->e.key).toList();
        ledger.put("missingRequiredCandidates",missing);ledger.put("counts",counts);ledger.put("nativeDamageCallers",callersSeen);ledger.put("observationErrors",observationErrors);
        Map<String,Long> delta=new LinkedHashMap<>();profiles().forEach((key,value)->{long difference=value-profileBaseline.getOrDefault(key,0L);if(difference>0)delta.put(key,difference);});ledger.put("processedBalanceProfiles",delta);
        ledger.put("priorControlledEvidence","tests/reports/1.2.0/boss-coverage-results.md and all-native-scenarios.csv; controlled fixture coverage is not imported as natural encounter coverage");
        ledger.put("criteria",List.of("A native death/win is required independently of attack coverage.","A selected state/animation/action is move-selection evidence, not damage acceptance or successful execution.","Post events retain HP and absorption reductions; shield and cancellation observations are separate.","Missing declared move/phase candidates remain gaps until observed or explicitly source-adjudicated as dormant, lifecycle or inapplicable.","This inventory cannot certify every random geometry, every trajectory or every armor/status combination."));
        ledger.put("allMovesAndPhasesCertified",false); // Deliberate: source adjudication and prior controlled comparison are external review steps.
        emit("telemetry_finished",Map.of("outcome",outcome,"missingRequiredCandidates",missing,"observationErrorCount",observationErrors.size()));
        closed=true;if(active==this)active=null;
        try{writer.close();Files.writeString(coveragePath,PRETTY.toJson(ledger)+"\n");}catch(Exception e){throw new IllegalStateException("Cannot finish encounter telemetry",e);}
        return Map.of("events",path.getFileName().toString(),"coverage",coveragePath.getFileName().toString(),"missingRequiredCandidates",missing.size(),"observationErrors",observationErrors.size(),"nativeDefeat",outcome.equals("won"));
    }

    /** Called by the test-only BMD mixin at the real perform() entry, never by a staged probe. */
    public static void observeBmdAction(Object action){
        EncounterTelemetry t=active;if(t==null||t.closed)return;
        LivingEntity actor=t.findActor(action);if(actor==null)return;
        String key="bmd_action/"+action.getClass().getSimpleName();t.observe(key,"native_action_entry",true,"installed BMD perform() entry");
        t.emit("native_action_entry",Map.of("actor",entity(actor),"class",action.getClass().getName(),"method","perform","state",t.state(actor)));
    }
    /** Native server animation/event publication; observes the installed entity-event bytes, not client guesses. */
    public static void observeEntityEvent(Entity actor,byte event){
        EncounterTelemetry t=active;if(t==null||t.closed||!t.bosses.contains(actor))return;
        String name=actor instanceof Warden?switch(event){case 4->"warden_melee_animation";case 61->"warden_vibration_response";case 62->"warden_sonic_boom_start";default->"";}:VanillaBmdVictorySetup.nativeEventName(type(actor),event);
        Map<String,Object> row=new LinkedHashMap<>();row.put("actor",entity(actor));row.put("nativeEventByte",event);row.put("nativeName",name);row.put("nativeCallers",nativeCallers());
        if(!name.isEmpty())t.observe("native_entity_event/"+name,"native_event",false,"installed BMD server entity-event map");
        t.emit("native_entity_event",row);
    }
    /** Called immediately after native currentPhase assignment, before begin() can select another phase. */
    public static void observeDragonPhaseAssignment(EnderDragon dragon,EnderDragonPhase<?> phase){
        EncounterTelemetry t=active;if(t==null||t.closed||!t.bosses.contains(dragon))return;
        t.observe("state/dragonPhaseId="+phase.getId(),"phase",true,"actual native EnderDragonPhaseManager.currentPhase assignment");
        t.emit("native_phase_assignment",Map.of("actor",entity(dragon),"phaseId",phase.getId(),"phaseName",phase.toString(),"nativeCallers",nativeCallers(),"timing","after actual assignment, before native phase begin(); unchanged-phase requests do not assign and are not counted"));
    }
    private static void incomingFirst(LivingIncomingDamageEvent event){EncounterTelemetry t=active;if(t==null||!t.victim(event.getEntity()))return;
        t.updateClock();Attempt a=new Attempt(++t.attemptSequence,event,t.tick);a.callers=nativeCallers();a.profileBefore=profiles();t.pending.put(event.getContainer(),a);
        t.emit("damage_attempt",t.damageRow(a,event.getAmount()));
    }
    private static void incomingLast(LivingIncomingDamageEvent event){EncounterTelemetry t=active;if(t==null)return;Attempt a=t.pending.get(event.getContainer());if(a==null)return;
        a.cancelled=event.isCanceled();Map<String,Object> row=t.damageRow(a,event.getAmount());row.put("cancelledAtLowestObserver",a.cancelled);
        Map<String,Long> applied=new LinkedHashMap<>();profiles().forEach((key,value)->{long delta=value-a.profileBefore.getOrDefault(key,0L);if(delta>0){applied.put(key,delta);t.observe("balance_profile/"+key.replaceFirst("\\.cap$",""),"processed_damage_profile",true,"production profile counter advanced inside native incoming event; not proof of HP damage");}});row.put("processedBalanceProfiles",applied);t.emit("damage_incoming_processed",row);
    }
    private static void damagePost(LivingDamageEvent.Post event){EncounterTelemetry t=active;if(t==null||!t.victim(event.getEntity()))return;
        Attempt a=null;DamageContainer key=null;for(var entry:t.pending.entrySet()){Attempt value=entry.getValue();if(value.victim==event.getEntity()&&value.source==event.getSource()&&(a==null||value.id>a.id)){a=value;key=entry.getKey();}}
        if(key!=null)t.pending.remove(key);
        Map<String,Object> row=a==null?t.sourceRow(event.getEntity(),event.getSource()):t.damageRow(a,event.getNewDamage());
        row.put("attemptId",a==null?-1:a.id);row.put("hpAfter",event.getEntity().getHealth());row.put("absorptionAfter",event.getEntity().getAbsorptionAmount());row.put("damageHp",event.getNewDamage());row.put("originalHp",event.getOriginalDamage());row.put("blockedHp",event.getBlockedDamage());row.put("shieldDurabilityDamage",event.getShieldDamage());
        Map<String,Float> reductions=new LinkedHashMap<>();for(var reduction:DamageContainer.Reduction.values())reductions.put(reduction.name(),event.getReduction(reduction));row.put("reductionsHp",reductions);
        row.put("nativeCallers",nativeCallers());row.put("result",event.getNewDamage()>0?"hp_damage":event.getReduction(DamageContainer.Reduction.ABSORPTION)>0?"absorbed_without_hp_damage":"post_event_without_hp_damage");
        List<String> callers=a==null?nativeCallers():a.callers;for(String caller:callers)t.callersSeen.merge(caller,1L,Long::sum);
        t.emit("damage_post",row);
    }
    private static void shield(LivingShieldBlockEvent event){EncounterTelemetry t=active;if(t==null||!t.victim(event.getEntity()))return;
        Attempt a=t.pending.get(event.getDamageContainer());if(a!=null)a.shield=event.getBlocked()&&!event.isCanceled();
        Map<String,Object> row=t.sourceRow(event.getEntity(),event.getDamageSource());row.put("attemptId",a==null?-1:a.id);row.put("blockedAtObserver",event.getBlocked());row.put("cancelledAtObserver",event.isCanceled());row.put("blockedHp",event.getBlockedDamage());row.put("shieldDurabilityDamage",event.shieldDamage());t.emit("shield_resolution",row);
    }
    private static void impact(ProjectileImpactEvent event){EncounterTelemetry t=active;if(t==null||!t.tracked.containsKey(event.getProjectile().getUUID()))return;
        var hit=event.getRayTraceResult();Map<String,Object> row=new LinkedHashMap<>();row.put("projectile",entity(event.getProjectile()));row.put("owner",entity(event.getProjectile().getOwner()));row.put("collision",hit.getType().toString());row.put("cancelledAtObserver",event.isCanceled());row.put("nativeCallers",nativeCallers());if(hit instanceof EntityHitResult entityHit)row.put("target",entity(entityHit.getEntity()));t.emit("projectile_collision",row);
    }
    private static void joined(EntityJoinLevelEvent event){EncounterTelemetry t=active;if(t==null||event.getLevel()!=t.level||event.getEntity() instanceof ServerPlayer)return;
        Entity e=event.getEntity();Entity owner=e instanceof Projectile p?p.getOwner():null;List<String> callers=nativeCallers();
        boolean related=owner==t.player||owner!=null&&t.tracked.containsKey(owner.getUUID());
        boolean nativeSpawn=!callers.isEmpty()&&t.bosses.stream().anyMatch(b->b.distanceToSqr(e)<256*256);
        if(!related&&!nativeSpawn)return;
        t.tracked.put(e.getUUID(),e);if(e instanceof Projectile)t.owners.put(e.getUUID(),ownerIdentity(owner));
        Map<String,Object> row=new LinkedHashMap<>();row.put("entity",entity(e));row.put("owner",entity(owner));row.put("nativeCallers",callers);row.put("attribution",related?"tracked_owner":"nearby_native_boss_call_stack");row.put("joinCancelledAtObserver",event.isCanceled());
        Object dangerous=read(e,"isDangerous");if(dangerous instanceof Boolean){row.put("dangerousProjectile",dangerous);if(type(e).equals("minecraft:wither_skull"))t.observe("projectile_variant/minecraft:wither_skull/dangerous="+dangerous,"conditional_projectile_variant",true,"native emitted Wither skull danger flag");}
        if(owner instanceof LivingEntity living&&t.bosses.contains(living))row.put("ownerStateAtEmission",t.state(living));
        t.emit("entity_emitted",row);
    }
    private boolean victim(LivingEntity e){return !closed&&(e==player||bosses.contains(e));}
    private void flushPending(boolean all){for(var it=pending.entrySet().iterator();it.hasNext();){Attempt a=it.next().getValue();if(all||tick>a.tick){Map<String,Object> row=damageRow(a,a.raw);row.put("cancelledAtLowestObserver",a.cancelled);row.put("shieldResolutionObserved",a.shield);row.put("result","no_matching_post_event");row.put("interpretation","Cancellation/shield flags are observations; no post alone does not identify invulnerability, a miss, or final cause");emit("damage_attempt_without_post",row);it.remove();}}}
    private Map<String,Object> damageRow(Attempt a,double amount){Map<String,Object> r=sourceRow(a.victim,a.source);r.put("attemptId",a.id);r.put("hpBefore",a.hp);r.put("absorptionBefore",a.absorption);r.put("originalHp",a.raw);r.put("amountAtObserver",amount);r.put("nativeCallers",a.callers);return r;}
    private Map<String,Object> sourceRow(LivingEntity victim,DamageSource source){Map<String,Object> r=new LinkedHashMap<>();r.put("victim",entity(victim));r.put("damageType",source.typeHolder().unwrapKey().map(k->k.location().toString()).orElse("unknown"));r.put("direct",entity(source.getDirectEntity()));r.put("owner",entity(source.getEntity()));r.put("armor",victim.getAttributeValue(Attributes.ARMOR));r.put("toughness",victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS));r.put("blocking",victim.isBlocking());r.put("hurtCooldown",victim.invulnerableTime);
        List<Map<String,Object>> context=new ArrayList<>();for(LivingEntity boss:bosses)context.add(Map.of("entity",entity(boss),"state",state(boss),"animationFrame",animationFrame(boss)));r.put("bossContext",context);return r;}

    private void catalogue(LivingEntity boss){
        for(var candidate:EncounterMoveCatalog.additional(type(boss)))declare(candidate.key(),candidate.category(),candidate.required(),candidate.origin());
        try{
            Map<?,?> defaults=(Map<?,?>)Class.forName("survival.balance.BossDamageBalance").getMethod("defaultTargets").invoke(null);
            String prefix=EncounterMoveCatalog.profilePrefix(type(boss));for(Object value:defaults.keySet()){String key=value.toString();if(!key.startsWith(prefix+".")||key.endsWith(".combined"))continue;if(key.startsWith("maledictus.mace_"))key="maledictus.mace_rage";declare("balance_profile/"+key,"processed_damage_profile",true,"configured damage family; native processing required, distinct from accepted damage");}
        }catch(Exception e){error("production damage-profile catalogue",e);}
        for(var entry:EncounterMoveCatalog.states(type(boss)).entrySet())declare("state/getAttackState="+entry.getKey(),"attack_state",!EncounterMoveCatalog.lifecycle(entry.getValue()),entry.getValue());
        for(String action:EncounterMoveCatalog.bmdActions(type(boss)))declare("bmd_action/"+action,"native_action_entry",true,"installed native action class");
        Map<Object,String> names=new IdentityHashMap<>();
        for(Class<?> c=boss.getClass();c!=null&&nativeClass(c.getName());c=c.getSuperclass())for(Field field:c.getDeclaredFields())try{
            if(Modifier.isStatic(field.getModifiers())&&field.getType().getSimpleName().equals("Animation")){field.setAccessible(true);Object animation=field.get(null);names.put(animation,field.getName());declare("state/animation="+field.getName(),"animation",!EncounterMoveCatalog.lifecycle(field.getName()),c.getName()+"."+field.getName());}
        }catch(Exception error){error("animation inventory",error);}
        animationNames.put(boss.getClass(),names);
        for(String getter:GETTERS){Method method=method(boss.getClass(),getter);if(method==null)continue;if(method.getReturnType().isEnum())for(Object constant:method.getReturnType().getEnumConstants())declare("state/"+getter+"="+constant,"enum_state",!EncounterMoveCatalog.lifecycle(constant.toString()),method.toString());}
        if(type(boss).equals("twilightforest:lich"))for(int p=1;p<=3;p++)declare("state/getPhase="+p,"phase",true,"native Lich phase");
        if(type(boss).equals("cataclysm:ignis")||type(boss).equals("cataclysm:scylla"))for(int p=0;p<=2;p++)declare("state/"+(type(boss).equals("cataclysm:ignis")?"getBossPhase":"isPhase")+"="+p,"phase",true,"native phase getter");
        for(String getter:EncounterMoveCatalog.booleanPhases(type(boss))){
            if(method(boss.getClass(),getter)==null){error("required phase getter missing",new IllegalStateException(type(boss)+"."+getter));continue;}
            for(boolean value:List.of(false,true))declare("state/"+getter+"="+value,"phase_or_mode",true,"installed native phase/mode flag");
        }
        if(type(boss).equals("bosses_of_mass_destruction:lich"))declare("native_entity_event/rage_health_milestone","phase_event",true,"native BMD staged-health event");
        if(boss instanceof WitherBoss)for(String p:List.of("spawn_charge","combat_unarmored","combat_armored"))declare("state/witherPhase="+p,"phase",true,"native charge ticks and half-health armor condition");
        if(boss instanceof EnderDragon)for(int p=0;p<=10;p++)declare("state/dragonPhaseId="+p,"phase",p!=10,"native EnderDragonPhase registry; hover is idle fixture state");
        if(boss instanceof Warden)for(String move:List.of("warden_melee_animation","warden_sonic_boom_start"))declare("native_entity_event/"+move,"native_attack_event",true,"native Warden event byte; selection is not a hit");
    }
    private Map<String,Object> state(LivingEntity boss){
        Map<String,Object> values=new LinkedHashMap<>();
        for(String getter:GETTERS){Object value=read(boss,getter);if(value!=null&&(value instanceof Number||value instanceof Boolean||value instanceof Enum<?>)){
            // Countdown values are not phases and would produce one transition every tick.
            if(getter.equals("getInvulnerableTicks"))continue;values.put(getter,value instanceof Enum<?>?value.toString():value);
        }}
        Object animation=read(boss,"getAnimation");if(animation!=null){String name=animationNames.getOrDefault(boss.getClass(),Map.of()).get(animation);if(name!=null)values.put("animation",name);else if(!animation.getClass().getSimpleName().equals("Animation"))values.put("animation",animation.toString());}
        if(boss instanceof EnderDragon dragon){values.put("dragonPhaseId",dragon.getPhaseManager().getCurrentPhase().getPhase().getId());values.put("dragonPhaseName",dragon.getPhaseManager().getCurrentPhase().getPhase().toString());}
        if(boss instanceof WitherBoss wither)values.put("witherPhase",wither.getInvulnerableTicks()>0?"spawn_charge":wither.getHealth()<=wither.getMaxHealth()/2?"combat_armored":"combat_unarmored");
        if(boss instanceof Warden warden){values.put("wardenActivity",warden.getBrain().getActiveNonCoreActivity().map(a->BuiltInRegistries.ACTIVITY.getKey(a).toString()).orElse("none"));values.put("wardenPose",warden.getPose().toString());values.put("wardenAnger",warden.getAngerLevel().toString());}
        if(type(boss).startsWith("bosses_of_mass_destruction:")){
            Map<String,Object> nativeInfo=VanillaBmdVictorySetup.snapshot(boss);if(nativeInfo.containsKey("observationError"))error("BMD supplemental snapshot",new IllegalStateException(nativeInfo.get("observationError").toString()));
            nativeInfo.forEach((key,value)->values.put("supplemental."+key,value));
        }
        Object pattern=read(boss,"getMovementPattern");if(pattern!=null){Object s=read(pattern,"getState");if(s!=null){values.put("movementPattern",s.toString());if(s instanceof Enum<?> e)for(Object v:e.getDeclaringClass().getEnumConstants())declare("state/movementPattern="+v,"movement_phase",true,"native Naga enum");}}
        Object heads=fieldValue(boss,"hc");if(heads!=null&&heads.getClass().isArray())for(int i=0;i<Array.getLength(heads);i++){Object head=Array.get(heads,i),s=fieldValue(head,"currentState");if(s!=null){values.put("hydraHead"+i,s.toString());observe("hydra_head_state/"+s,"head_state",true,"native Hydra head state");if(s instanceof Enum<?> e)for(Object v:e.getDeclaringClass().getEnumConstants())declare("hydra_head_state/"+v,"head_state",!EncounterMoveCatalog.lifecycle(v.toString()),"native Hydra head enum");}}
        if(boss instanceof Mob){Object selector=fieldValue(boss,"goalSelector");if(selector instanceof GoalSelector goals){List<String> running=goals.getAvailableGoals().stream().filter(g->g.isRunning()).map(g->g.getGoal().getClass().getName()).sorted().toList();values.put("runningGoals",running);for(String goal:running)observe("goal/"+goal,"running_goal",false,"native goal selector");}}
        readSyncedFlags(boss,values);
        return values;
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private void readSyncedFlags(LivingEntity boss,Map<String,Object> values){
        for(Class<?> c=boss.getClass();c!=null&&nativeClass(c.getName());c=c.getSuperclass())for(Field f:c.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==EntityDataAccessor.class&&f.getName().toLowerCase(Locale.ROOT).matches(".*(phase|state|shield|energ|rage|awake|sleep|tantrum|rampag).*"))try{f.setAccessible(true);Object v=boss.getEntityData().get((EntityDataAccessor)f.get(null));if(v instanceof Number||v instanceof Boolean)values.put("synced."+f.getName(),v);}catch(Exception e){error("synced field "+f.getName(),e);}
    }
    private int animationFrame(LivingEntity boss){Object v=read(boss,"getAnimationTick");if(!(v instanceof Number))v=read(boss,"getAttackTicks");return v instanceof Number n?n.intValue():-1;}
    private void declare(String key,String category,boolean required,String origin){inventory.computeIfAbsent(key,k->new Entry(k,category,required,origin));}
    private void observe(String key,String category,boolean required,String origin){updateClock();declare(key,category,required,origin);Entry e=inventory.get(key);if(e.firstTick<0)e.firstTick=tick;e.lastTick=tick;e.observations++;}
    private LivingEntity findActor(Object action){Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());ArrayDeque<Object> queue=new ArrayDeque<>();queue.add(action);int visited=0;while(!queue.isEmpty()&&visited++<96){Object o=queue.removeFirst();if(!seen.add(o))continue;if(o instanceof LivingEntity e){if(bosses.contains(e))return e;continue;}if(!o.getClass().getName().startsWith(BMD))continue;for(Class<?> c=o.getClass();c!=null&&c.getName().startsWith(BMD);c=c.getSuperclass())for(Field f:c.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&!f.getType().isPrimitive())try{f.setAccessible(true);Object v=f.get(o);if(v!=null)queue.add(v);}catch(Exception ignored){}}return null;}
    private void error(String operation,Exception error){String text=operation+": "+error;if(!observationErrors.contains(text)){observationErrors.add(text);emit("observation_error",Map.of("detail",text));}}
    private void updateClock(){tick=Math.max(tick,(int)Math.max(0,level.getGameTime()-startGameTime));}
    private void emit(String event,Map<String,?> payload){updateClock();Map<String,Object> row=new LinkedHashMap<>();row.put("event",event);row.put("sequence",++sequence);row.put("tick",tick);row.put("utc",Instant.now().toString());row.put("boss",managedBoss);row.putAll(payload);counts.merge(event,1L,Long::sum);try{writer.write(JSON.toJson(row));writer.newLine();}catch(Exception e){throw new IllegalStateException("Cannot write telemetry",e);}}
    private void flush(){try{writer.flush();}catch(Exception e){throw new IllegalStateException(e);}}
    private static Method method(Class<?> type,String name){return METHODS.computeIfAbsent(type,k->new HashMap<>()).computeIfAbsent(name,k->{for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name);m.setAccessible(true);return Optional.of(m);}catch(NoSuchMethodException ignored){}return Optional.empty();}).orElse(null);}
    private static Object read(Object owner,String name){if(owner==null)return null;Method method=method(owner.getClass(),name);if(method==null)return null;try{return method.invoke(owner);}catch(Exception e){if(active!=null)active.error("getter "+owner.getClass().getSimpleName()+"."+name,e);return null;}}
    private static Object fieldValue(Object owner,String name){if(owner==null)return null;Field f=FIELDS.computeIfAbsent(owner.getClass(),k->new HashMap<>()).computeIfAbsent(name,k->{for(Class<?> c=owner.getClass();c!=null;c=c.getSuperclass())try{Field v=c.getDeclaredField(name);v.setAccessible(true);return Optional.of(v);}catch(NoSuchFieldException ignored){}return Optional.empty();}).orElse(null);if(f==null)return null;try{return f.get(owner);}catch(Exception e){if(active!=null)active.error("field "+name,e);return null;}}
    private static String type(Entity e){return e==null?"":BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();}
    private static String ownerIdentity(Entity e){return e==null?"none":e instanceof ServerPlayer?"fixture_player":type(e)+"#"+e.getUUID();}
    private static Map<String,Object> entity(Entity e){if(e==null)return Map.of();Map<String,Object> value=new LinkedHashMap<>();value.put("type",type(e));value.put("id",e.getId());if(e instanceof ServerPlayer)value.put("role","fixture_player");else value.put("uuid",e.getUUID().toString());return value;}
    private static boolean nativeClass(String c){return c.startsWith("twilightforest.")||c.startsWith("com.github.L_Ender.cataclysm.")||c.startsWith(BMD)||c.startsWith("net.minecraft.world.entity.boss.")||c.startsWith("net.minecraft.world.entity.monster.warden.");}
    private static List<String> nativeCallers(){return StackWalker.getInstance().walk(s->s.filter(f->nativeClass(f.getClassName())||f.getClassName().startsWith("net.minecraft.world.entity.ai.behavior.warden.")).limit(16).map(f->f.getClassName()+"."+f.getMethodName()).toList());}
    @SuppressWarnings("unchecked") private static Map<String,Long> profiles(){try{return new LinkedHashMap<>((Map<String,Long>)Class.forName("survival.balance.BossDamageBalance").getMethod("profileCounts").invoke(null));}catch(Exception e){return Map.of();}}
}

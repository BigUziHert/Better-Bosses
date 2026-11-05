package survival.balance.clienttest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** TEST ONLY. Automated client inputs, not human play. Uses native movement,
 * collision, item use, attack cooldowns and packets. No health/phase changes. */
@Mod(value="bossfight_clientpilot",dist=Dist.CLIENT)
public final class BossFightClientPilot {
    private static final Gson JSON=new Gson();
    private static final Set<String> BOSSES=Set.of(
        "twilightforest:naga","twilightforest:lich","twilightforest:minoshroom",
        "twilightforest:knight_phantom","twilightforest:alpha_yeti","twilightforest:hydra",
        "twilightforest:ur_ghast","twilightforest:snow_queen",
        "bosses_of_mass_destruction:lich","bosses_of_mass_destruction:void_blossom",
        "bosses_of_mass_destruction:gauntlet","bosses_of_mass_destruction:obsidilith",
        "cataclysm:netherite_monstrosity","cataclysm:ender_guardian","cataclysm:the_harbinger",
        "cataclysm:ancient_remnant","cataclysm:the_leviathan","cataclysm:scylla",
        "cataclysm:maledictus","cataclysm:ignis","minecraft:wither","minecraft:ender_dragon","minecraft:warden");
    private long ticks;
    private boolean enabled;
    private String weaponMode="auto";
    private double preferredRange;
    private boolean activateEmp;
    private boolean attackEnabled=true;
    private boolean guardEnabled;
    private boolean trackingMissing, rangedApproach, surfacingForAir;
    private int deadTicks;
    private Path controls,log;
    private String lastTarget="";
    private BlockPos miningBlock;
    private final List<BlockPos> runeTargets=new ArrayList<>();
    private long runeScanTick;
    private Field overlayEvents;
    private long nextUseClick;
    private Vec3 arenaCenter;
    private String arenaBoss="";
    private boolean returningToArena;
    private boolean endEncounter;
    private Path encounterFile;
    private boolean encounterRunning, metadataKnocked, wasKnocked;
    private final Set<UUID> encounterBosses=new HashSet<>();
    private String encounterId="", encounterBoss="", seatId="";
    private long seatSince, lastCounterTick, lastAppleTick=-1000;
    private boolean appleEating;
    private int appleStartCount,appleSlot=-1;
    private Entity lockedHead;
    private long headLockTick, lastTrapInput;
    private long releaseUseUntil;
    private final List<BlockPos> trapButtons=new ArrayList<>();
    private final List<BlockPos> empButtons=new ArrayList<>();
    private long lastEmpInput=-1000;
    private double navigationY;
    private double arenaRadius=48;
    private java.lang.reflect.Method nativeDowned;
    private boolean downedLookupAttempted;
    private Vec3 lastNavigationPosition;
    private int stuckTicks;
    private final DragonAccessNavigator dragonAccess=new DragonAccessNavigator();
    private boolean leviCounterWindup;
    private boolean ignisDodging;
    private Vec3 ignisDodgeDirection;
    private String ignisDodgeAnimation="";
    private boolean maledictusDodging;
    private Vec3 maledictusDodgeDirection;
    private long feedAttemptSince;
    private int feedStartCount;

    public BossFightClientPilot(IEventBus modBus){
        modBus.addListener((FMLClientSetupEvent event)->event.enqueueWork(ProductionFlightSources::restoreProductionSources));
        if(Boolean.getBoolean("survival.bossBalance.clientPlaytest"))NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();
        if(controls==null){controls=mc.gameDirectory.toPath().resolve("bossfight-control.json");log=mc.gameDirectory.toPath().resolve("client-fight-events.jsonl");encounterFile=mc.gameDirectory.toPath().resolve("bossfight-encounter.json");record("pilot_version",Map.of("version","victory-native-inputs-2","scope","Automated native inputs; native apple effects, native self-revival, no direct health/AI mutation"));}
        if(++ticks%20==0)readControl();
        if(ticks%5==0)readEncounter();
        var server=mc.getCurrentServer();
        if(!enabled||server==null||!server.ip.equals("127.0.0.1:25576")||mc.player==null||mc.level==null||mc.gameMode==null){stopMining(mc);release(mc);return;}
        LocalPlayer player=mc.player;
        // These are real network-received, currently registered client bars. A screenshot
        // remains necessary to inspect custom renderers, clipping and HUD placement.
        if(ticks%20==0)record("received_boss_overlay",Map.of("hudHidden",mc.options.hideGui,"bars",bossBars(mc),"screen",mc.screen==null?"":mc.screen.getClass().getName(),"windowActive",mc.isWindowActive()));
        if(!player.isAlive()){
            stopMining(mc);release(mc);
            if(++deadTicks==60&&mc.screen instanceof DeathScreen){record("native_respawn_request",Map.of());player.respawn();}
            return;
        }
        deadTicks=0;
        if(mc.screen!=null){stopMining(mc);release(mc);return;}
        if(!encounterRunning){stopMining(mc);release(mc);wasKnocked=false;return;}
        boolean lowAir=surfacingForAir;
        if(player.isInWater()&&player.getAirSupply()<60)surfacingForAir=true;
        if(player.getAirSupply()>=player.getMaxAirSupply()-5)surfacingForAir=false;
        if(lowAir!=surfacingForAir)record(surfacingForAir?"native_surface_for_air_input":"native_air_refilled",Map.of("air",player.getAirSupply()));
        boolean knocked=isKnocked(player);
        if(knocked!=wasKnocked){
            wasKnocked=knocked;appleEating=false;stopMining(mc);release(mc);
            feedAttemptSince=0;
            record(knocked?"native_knockout_observed":"native_self_revive_observed",Map.of("health",player.getHealth(),"absorption",player.getAbsorptionAmount()));
            if(!knocked)lastAppleTick=-1000;
            if(knocked)select(player,findSlot(player,"apple"));
            return; // Native RagRevival must see a released use key before a new feed.
        }
        if(knocked){
            stopMining(mc);stopMovement(mc);mc.options.keyShift.setDown(false);
            if(ticks<releaseUseUntil){mc.options.keyUse.setDown(false);return;}
            int apple=findSlot(player,"apple");
            if(apple<0){mc.options.keyUse.setDown(false);if(ticks%20==0)record("native_self_revive_unavailable",Map.of("reason","no enchanted apple on hotbar"));return;}
            if(select(player,apple)){mc.options.keyUse.setDown(false);return;}
            if(feedAttemptSince==0){feedAttemptSince=ticks;feedStartCount=player.getMainHandItem().getCount();}
            if(ticks-feedAttemptSince>=60&&player.getMainHandItem().getCount()>=feedStartCount){
                recoverStalledUse(mc,player,"self_feed_without_consumption");feedAttemptSince=0;return;
            }
            useInput(mc);
            if(ticks%20==0)record("native_self_revive_hold",Map.of("windowActive",mc.isWindowActive(),"appleCount",player.getMainHandItem().getCount(),"nativeUse",NativeUseDiagnostics.observe(player)));
            return; // RevivalClient handles its own hold-to-feed packets and consumption.
        }
        boolean ragdoll=player.isPassenger()&&id(player.getVehicle()).equals("sable_player_ragdoll:ragdoll_seat");
        if(ragdoll){
            String currentSeat=player.getVehicle().getUUID().toString();
            if(!currentSeat.equals(seatId)){seatId=currentSeat;seatSince=ticks;record("nonlethal_seat_observed",Map.of("uuid",seatId));}
            boolean crouch=ticks-seatSince>=16&&(ticks-seatSince)%20<8;
            mc.options.keyShift.setDown(crouch);
            if(crouch&&(ticks-seatSince)%20==4)record("native_crouch_recovery_input",Map.of("seatTicks",ticks-seatSince));
        }else{
            if(!seatId.isEmpty()){
                record("native_crouch_recovery_observed",Map.of("seatTicks",ticks-seatSince,"health",player.getHealth()));
                releaseUseUntil=Math.max(releaseUseUntil,ticks+2);appleEating=false;lastAppleTick=-1000;
            }
            seatId="";mc.options.keyShift.setDown(false);
        }
        if(appleEating&&ticks-lastAppleTick>=60&&appleSlot>=0&&player.getInventory().getItem(appleSlot).getCount()>=appleStartCount){
            record("native_apple_use_retry",Map.of("attemptTicks",ticks-lastAppleTick,"clientUsingItem",player.isUsingItem(),"useTicks",player.getTicksUsingItem(),"reason","no native inventory consumption after60ticks"));
            recoverStalledUse(mc,player,"food_without_consumption");
        }
        // Native RevivalClient can suppress use until it observes an actual release.
        // A false->true change inside one Post callback is not a released-key interval.
        if(ticks<releaseUseUntil){stopMining(mc);stopMovement(mc);mc.options.keyUse.setDown(false);return;}
        LivingEntity boss=nearestBoss(mc,player);
        if(boss==null){returnToTracking(mc,player,ragdoll);return;}
        if(trackingMissing){trackingMissing=false;record("boss_tracking_reacquired",Map.of("boss",id(boss),"distance",player.distanceTo(boss)));}
        if(arenaCenter==null){arenaCenter=boss.position();arenaBoss=id(boss);returningToArena=false;endEncounter=boss instanceof EnderDragon;record("arena_anchor",Map.of("center",List.of(arenaCenter.x,arenaCenter.y,arenaCenter.z),"softReturn",!endEncounter));}
        if(!boss.getUUID().toString().equals(lastTarget)){stopMining(mc);runeTargets.clear();runeScanTick=0;lockedHead=null;lastTarget=boss.getUUID().toString();record("new_target",Map.of("boss",id(boss),"uuid",lastTarget));}
        boolean lichShielded=isShieldedLich(boss);
        Entity target=chooseTarget(mc,player,boss);
        Entity bolt=nearReflectable(mc,player,boss,lichShielded||id(boss).equals("cataclysm:ignis")?12:4);
        double distance=player.position().distanceTo(target.getBoundingBox().getCenter());
        navigationY=target.getBoundingBox().getCenter().y;
        boolean meleePreferred=preferMelee(boss,target);
        Vec3 aim=meleePreferred?meleeAimPoint(target,player.getEyePosition()):aimPoint(target);
        if(!meleePreferred&&target instanceof LivingEntity living&&distance>5)aim=aim.add(living.getDeltaMovement().scale(Math.min(16,distance/2.7)));
        boolean wantApple=needsApple(player);
        boolean passiveDragonRetreat=!attackEnabled&&weaponMode.equals("ranged")&&preferredRange>20
                &&boss instanceof EnderDragon dragon&&(landingApproach(dragon)||dragon.getPhaseManager().getCurrentPhase().isSitting());
        var ignisWindow=id(boss).equals("cataclysm:ignis")?IgnisDefenseWindows.current(boss):null;
        boolean reactiveDefense=ignisWindow!=null&&!wantApple&&!ragdoll&&bolt==null&&distance<=48;
        boolean reactiveShield=reactiveDefense&&!ignisWindow.animation().equals("ULTIMATE_ATTACK")
                &&player.getOffhandItem().is(Items.SHIELD)&&!player.getCooldowns().isOnCooldown(Items.SHIELD);
        if(!reactiveDefense||reactiveShield){ignisDodgeDirection=null;ignisDodgeAnimation="";}
        boolean passiveIgnisRetreat=weaponMode.equals("ranged")&&preferredRange>=9
                &&id(boss).equals("cataclysm:ignis")&&!wantApple&&!ragdoll&&bolt==null&&!reactiveDefense
                &&player.position().subtract(boss.position()).multiply(1,0,1).length()<preferredRange-.5;
        ignisDodging=reactiveDefense&&!reactiveShield||passiveIgnisRetreat;
        maledictusDodging=!attackEnabled&&!guardEnabled&&preferredRange>=6&&!wantApple&&!ragdoll&&bolt==null
                &&maledictusGrabDodgeWindow(boss);
        if(!maledictusDodging)maledictusDodgeDirection=null;
        boolean counterWindup=leviRushWindup(boss)&&!wantApple&&!ragdoll&&!surfacingForAir;
        if(counterWindup!=leviCounterWindup){leviCounterWindup=counterWindup;record("native_levi_counter_chase_priority",Map.of("active",counterWindup));}
        boolean defenseWindow=!wantApple&&player.getHealth()+player.getAbsorptionAmount()<16&&distance<=9&&ticks%32>=22&&player.getOffhandItem().is(Items.SHIELD)&&!player.getCooldowns().isOnCooldown(Items.SHIELD);

        boolean holdingGuard=(guardEnabled||reactiveDefense)&&player.isUsingItem()&&player.getUseItem().is(Items.SHIELD);
        if(player.isUsingItem()&&!holdingGuard){
            aim(player,aim,distance);
            stopMining(mc);
            mc.options.keyUse.setDown(true);
            if(player.getUseItem().is(Items.ENCHANTED_GOLDEN_APPLE)
                    &&(!appleEating||appleSlot<0||player.getInventory().getItem(appleSlot).getCount()<appleStartCount)){
                // Always-edible food can auto-start a second use before this Post tick.
                // Observe native stack consumption and release, without cancelling the first completed meal.
                mc.options.keyUse.setDown(false);
                if(appleEating)record("native_apple_consumption_observed",Map.of("health",player.getHealth(),"absorption",player.getAbsorptionAmount(),"slot",appleSlot));
                appleEating=false;sample(player,boss);return;
            }
            if(player.getUseItem().getItem() instanceof BowItem){
                if(bolt!=null){
                    cancelBowByHotbar(mc,player);mc.options.keyUse.setDown(false);
                    record("bow_cancel_for_projectile_counter_input",Map.of("projectile",id(bolt)));
                    sample(player,boss);return;
                }
                if(passiveIgnisRetreat){
                    cancelBowByHotbar(mc,player);mc.options.keyUse.setDown(false);
                    record("bow_cancel_for_ignis_spacing_input",Map.of("requestedRange",preferredRange));
                    sample(player,boss);return;
                }
                if(reactiveDefense){cancelBowByHotbar(mc,player);sample(player,boss);return;}
                if(passiveDragonRetreat&&!wantApple&&!ragdoll&&bolt==null){
                    cancelBowByHotbar(mc,player);stopMovement(mc);
                    record("bow_cancel_for_dragon_retreat_input",Map.of("reason","explicit passive ranged distance"));sample(player,boss);return;
                }
                if(boss instanceof EnderDragon&&dragonAccess.belowRing(player)){
                    cancelBowByHotbar(mc,player);record("bow_cancel_for_dragon_access_input",Map.of("height",player.getY()));sample(player,boss);return;
                }
                rangedMovement(mc,player,boss,target);
                if(!attackEnabled){
                    // A hotbar change normally cancels a drawn bow without firing.
                    // Keep use held until native item-change handling ends that draw.
                    int cancelSlot=findSlot(player,"empty");if(cancelSlot<0)cancelSlot=findSlot(player,"sword");
                    if(select(player,cancelSlot))record("bow_cancel_by_hotbar_input",Map.of("reason","attackEnabled false"));
                    sample(player,boss);return;
                }
                if(lichShielded||wantApple||meleePreferred||guardEnabled){mc.options.keyUse.setDown(false);record("bow_priority_release_input",Map.of("boss",id(boss),"heal",wantApple,"meleeTransition",meleePreferred,"guard",guardEnabled));sample(player,boss);return;}
                if(needsRangedApproach(mc,player,target,true)){
                    rangedApproach=true;cancelBowByHotbar(mc,player);
                    record("bow_cancel_for_approach_input",Map.of("distance",distance,"lineOfSight",player.hasLineOfSight(target)));
                    sample(player,boss);return;
                }
                if(defenseWindow){mc.options.keyUse.setDown(false);record("defense_bow_release_input",Map.of("health",player.getHealth(),"distance",distance));sample(player,boss);return;}
                if(player.getTicksUsingItem()>=21&&weakPointOpen(target)&&!ignisParrying(boss)&&player.hasLineOfSight(target)){int drawTicks=player.getTicksUsingItem();mc.options.keyUse.setDown(false);record("bow_release_input",Map.of("distance",distance,"drawTicks",drawTicks,"target",id(target)));}
            }else if(player.getUseItem().is(Items.SHIELD)){
                retreat(mc,player,true);
                if(!defenseWindow)mc.options.keyUse.setDown(false);
            }else movement(mc,player,distance<7?-1:0,false,player.horizontalCollision);
            sample(player,boss);return;
        }
        mc.options.keyUse.setDown(false);
        // A held guard is ordinary item use. Higher-priority actions need a real
        // released-key tick before food, projectile counters or block interaction.
        if(holdingGuard&&(wantApple||bolt!=null||ragdoll)){sample(player,boss);return;}
        if(appleEating&&appleSlot>=0&&player.getInventory().getItem(appleSlot).getCount()<appleStartCount){appleEating=false;record("native_apple_consumption_observed",Map.of("health",player.getHealth(),"absorption",player.getAbsorptionAmount(),"slot",appleSlot));}
        if(wantApple){
            aim(player,aim,distance);
            int food=findSlot(player,"apple");
            if(food>=0){stopMining(mc);if(select(player,food))return;appleEating=true;appleSlot=food;appleStartCount=player.getInventory().getItem(food).getCount();useInput(mc);lastAppleTick=ticks;record("eat_input",Map.of("health",player.getHealth(),"absorption",player.getAbsorptionAmount(),"food",player.getFoodData().getFoodLevel(),"item","minecraft:enchanted_golden_apple","policy","native_absorption_and_regeneration_refresh"));movement(mc,player,distance<7?-1:0,false,player.horizontalCollision);return;}
        }
        if(ragdoll){stopMining(mc);stopMovement(mc);sample(player,boss);return;}
        if(boss instanceof EnderDragon&&dragonAccess.belowRing(player)){
            if(select(player,findSlot(player,"sword")))return;
            approachDragonAnchor(mc,player);sample(player,boss);return;
        }
        if(useNativeEmp(mc,player,boss)){sample(player,boss);return;}
        if(useChargedGhastTrap(mc,player,boss)){sample(player,boss);return;}
        if(mineNativeRune(mc,player,boss)){sample(player,boss);return;}
        if(passiveDragonRetreat&&bolt==null){
            passiveDragonRetreat(mc,player,(EnderDragon)boss);sample(player,boss);return;
        }
        // Each mechanic branch above owns its aim. Turning toward the boss first
        // would undo a same-sized turn toward a flower or button behind the player.
        boolean dragonRouteOwnsAim=bolt==null&&!reactiveDefense&&!defenseWindow
                &&!(guardEnabled&&player.getOffhandItem().is(Items.SHIELD)&&!player.getCooldowns().isOnCooldown(Items.SHIELD))
                &&!(rangedApproach&&!lichShielded&&!meleePreferred)
                &&boss instanceof EnderDragon dragon&&(landingApproach(dragon)
                    ||dragon.getPhaseManager().getCurrentPhase().isSitting()
                    &&player.getEyePosition().distanceTo(closest(target,player.getEyePosition()))>player.entityInteractionRange());
        // Route waypoints need sole turn ownership, just like mining and buttons.
        // Earlier recovery/healing and later counter/defense priorities stay intact.
        if(!ignisDodging&&!maledictusDodging&&!dragonRouteOwnsAim)aim(player,aim,distance);
        // Reflect native projectiles using a real attack within player reach.
        if(bolt!=null){
            stopMining(mc);
            int bare=findSlot(player,"empty");if(bare<0)bare=findSlot(player,"bow");if(select(player,bare))return;
            // A non-weapon normal click avoids BetterCombat's living-target sword upswing.
            // LichBolt/HydraMortar use the attacker's look direction, not attack damage.
            aim(player,aimPoint(lichShielded?boss:target),distance);
            boolean picked=nativeCrosshairSelects(mc,bolt);
            double counterReach=picked?player.getEyePosition().distanceTo(mc.hitResult.getLocation()):Double.POSITIVE_INFINITY;
            if(counterReach<=player.entityInteractionRange()&&ticks-lastCounterTick>=2){
                lastCounterTick=ticks;KeyMapping.click(mc.options.keyAttack.getKey());record("projectile_counter_input",Map.of("projectile",id(bolt),"aimBoss",id(boss),"distance",counterReach,"nativeCrosshair",true));
            }
            if(ticks%5==0)record("native_counter_window",Map.of("projectile",id(bolt),"nativePicked",picked,
                    "bodyDistance",player.getEyePosition().distanceTo(closest(bolt,player.getEyePosition())),
                    "pickedDistance",picked?(Object)counterReach:"not_picked","nativeReach",player.entityInteractionRange()));
            movement(mc,player,0,false,false);sample(player,boss);return;
        }
        if(reactiveDefense){
            stopMining(mc);
            if(select(player,findSlot(player,"sword")))return;
            if(reactiveShield){stopMovement(mc);useInput(mc);}
            else{
                Vec3 away=player.position().subtract(boss.position()).multiply(1,0,1).normalize();
                if(ignisWindow.dash()||ignisWindow.animation().equals("ULTIMATE_ATTACK")){
                    if(ignisDodgeDirection==null||!ignisDodgeAnimation.equals(ignisWindow.animation())){
                        Vec3 bossFacing=boss.getLookAngle().multiply(1,0,1).normalize();
                        Vec3 escape=new Vec3(bossFacing.z,0,-bossFacing.x).add(away);
                        ignisDodgeDirection=escape.lengthSqr()<1.0E-8?away:escape.normalize();
                        ignisDodgeAnimation=ignisWindow.animation();
                    }
                    away=ignisDodgeDirection;
                }
                aim(player,player.getEyePosition().add(away.scale(8)),8);
                movement(mc,player,player.getLookAngle().multiply(1,0,1).normalize().dot(away)>.8?1:0,false,player.horizontalCollision);
            }
            if(ticks%5==0)record("native_ignis_defense_input",Map.of("animation",ignisWindow.animation(),"frame",ignisWindow.frame(),"hitFrame",ignisWindow.hitFrame(),"input",reactiveShield?"shield":"turn_and_run","nativeBlocking",player.isBlocking()));
            sample(player,boss);return;
        }
        if(guardEnabled&&player.getOffhandItem().is(Items.SHIELD)&&!player.getCooldowns().isOnCooldown(Items.SHIELD)){
            stopMovement(mc);
            if(select(player,findSlot(player,"sword")))return;
            useInput(mc);
            if(ticks%20==0)record("native_guard_hold_input",Map.of("health",player.getHealth(),"distance",distance,"nativeBlocking",player.isBlocking()));
            sample(player,boss);return;
        }
        if(passiveIgnisRetreat){
            stopMining(mc);mc.options.keyUse.setDown(false);rangedApproach=false;
            if(select(player,findSlot(player,"sword"))){stopMovement(mc);return;}
            Vec3 away=player.position().subtract(boss.position()).multiply(1,0,1).normalize();
            aim(player,player.getEyePosition().add(away.scale(8)),8);
            boolean facingAway=player.getLookAngle().multiply(1,0,1).normalize().dot(away)>.8;
            movement(mc,player,facingAway?1:0,false,player.horizontalCollision);
            if(ticks%20==0)record("native_ignis_passive_retreat_input",Map.of(
                    "rootHorizontalDistance",player.position().subtract(boss.position()).multiply(1,0,1).length(),
                    "requestedRange",preferredRange,"facingAway",facingAway,
                    "forwardKey",mc.options.keyUp.isDown(),"sprintKey",mc.options.keySprint.isDown()));
            sample(player,boss);return;
        }
        if(maledictusDodging){
            stopMining(mc);mc.options.keyUse.setDown(false);rangedApproach=false;
            if(select(player,findSlot(player,"sword"))){stopMovement(mc);return;}
            if(maledictusDodgeDirection==null){
                Vec3 bossFacing=boss.getLookAngle().multiply(1,0,1).normalize();
                maledictusDodgeDirection=new Vec3(bossFacing.z,0,-bossFacing.x);
                record("native_maledictus_lateral_dodge_start",Map.of("direction",List.of(maledictusDodgeDirection.x,0,maledictusDodgeDirection.z)));
            }
            aim(player,player.getEyePosition().add(maledictusDodgeDirection.scale(8)),8);
            boolean facingSideways=player.getLookAngle().multiply(1,0,1).normalize().dot(maledictusDodgeDirection)>.8;
            movement(mc,player,facingSideways?1:0,false,player.horizontalCollision);
            if(ticks%10==0)record("native_maledictus_lateral_dodge_input",Map.of("facingSideways",facingSideways,"forwardKey",mc.options.keyUp.isDown(),"sprintKey",mc.options.keySprint.isDown()));
            sample(player,boss);return;
        }
        if(defenseWindow){
            int sword=findSlot(player,"sword");
            if(select(player,sword)){record("defense_select_sword",Map.of("health",player.getHealth(),"distance",distance));retreat(mc,player,true);return;}
            useInput(mc);record("defense_shield_input",Map.of("health",player.getHealth(),"distance",distance,"cycleTick",ticks%40));retreat(mc,player,true);sample(player,boss);return;
        }
        if(rangedApproach&&!lichShielded&&!meleePreferred){
            if(player.hasLineOfSight(target))rangedMovement(mc,player,boss,target);
            else movement(mc,player,1,false,player.horizontalCollision);
            if(needsRangedApproach(mc,player,target,false)){
                select(player,findSlot(player,"sword"));sample(player,boss);return;
            }
            rangedApproach=false;record("ranged_approach_complete",Map.of("distance",distance));
        }
        if(lichShielded){
            // Shield count is native synchronized client data. Wait with a sword for a
            // visible bolt, then use the ordinary crosshair attack above; never rewrite
            // shields or reflect outside real player reach.
            int bare=findSlot(player,"empty");if(bare<0)bare=findSlot(player,"bow");if(select(player,bare))return;
            movement(mc,player,distance>10?1:distance<5?-1:0,false,player.horizontalCollision);
            sample(player,boss);return;
        }
        if(boss instanceof EnderDragon dragon&&dragon.getPhaseManager().getCurrentPhase().isSitting()&&ticks%20==0){
            observeDragonMeleeCandidate(mc,player,dragon,target);
        }
        if(boss instanceof EnderDragon dragon&&landingApproach(dragon)){
            if(select(player,findSlot(player,"sword")))return;
            approachDragonAnchor(mc,player);sample(player,boss);return;
        }
        if(boss instanceof EnderDragon dragon&&dragon.getPhaseManager().getCurrentPhase().isSitting()
                &&player.getEyePosition().distanceTo(closest(target,player.getEyePosition()))>player.entityInteractionRange()
                &&walkDragonAccess(mc,player,target.position())){sample(player,boss);return;}
        // If no access geometry exists, preserve the original combat-aim fallback.
        if(dragonRouteOwnsAim)aim(player,aim,distance);
        boolean visible=player.hasLineOfSight(target);
        boolean avoidContact=weaponMode.equals("ranged")
                ||!weaponMode.equals("melee")&&id(boss).equals("bosses_of_mass_destruction:void_blossom")&&target==boss;
        // A multipart union can contain empty space. Only its native ray intersection
        // establishes melee reach; the ordinary client crosshair still validates attacks.
        Vec3 meleeAim=meleeAimPoint(target,player.getEyePosition());
        double reachDistance=target.getBoundingBox().clip(player.getEyePosition(),meleeAim)
                .map(hit->player.getEyePosition().distanceTo(hit)).orElse(Double.POSITIVE_INFINITY);
        if(target.getBoundingBox().contains(player.getEyePosition()))reachDistance=0;
        if(!avoidContact&&visible&&reachDistance<=player.entityInteractionRange()&&!(target instanceof EndCrystal)){
            int sword=findSlot(player,"sword");if(select(player,sword))return;
            aim(player,meleeAim,reachDistance);
            if(attackEnabled&&!ignisParrying(boss)&&player.getAttackStrengthScale(0)>=.9f&&ticks%2==0&&nativeCrosshairSelects(mc,target)){KeyMapping.click(mc.options.keyAttack.getKey());record("melee_input",Map.of("target",id(target),"distance",reachDistance,"nativePickedPart",mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof EnderDragonPart part?part.name:""));}
            double retreatReach=id(boss).equals("cataclysm:ignis")?2.65:1.1;
            double approachReach=id(boss).equals("cataclysm:ignis")?2.85:2.3;
            movement(mc,player,reachDistance<retreatReach?-1:reachDistance>approachReach?1:0,needsFlank(boss),player.horizontalCollision);
        }else if(!meleePreferred&&visible&&(distance>=4||avoidContact)&&distance<90&&findSlot(player,"bow")>=0){
            if(select(player,findSlot(player,"bow")))return;
            if(attackEnabled){useInput(mc);record("bow_draw_input",Map.of("distance",distance));}
            rangedMovement(mc,player,boss,target);
        }else movement(mc,player,1,needsFlank(boss)&&distance<9,player.horizontalCollision);
        sample(player,boss);
    }
    private LivingEntity nearestBoss(Minecraft mc,LocalPlayer player){
        List<LivingEntity> matches=new ArrayList<>();
        for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof LivingEntity living&&living.isAlive()&&BOSSES.contains(id(entity))&&!isLichClone(entity)
                &&(encounterBosses.isEmpty()?id(entity).equals(encounterBoss):encounterBosses.contains(entity.getUUID()))&&player.distanceToSqr(entity)<192*192)matches.add(living);
        return matches.stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }
    private void returnToTracking(Minecraft mc,LocalPlayer player,boolean ragdoll){
        stopMining(mc);
        if(!trackingMissing){trackingMissing=true;record("boss_tracking_lost",Map.of("position",List.of(player.getX(),player.getY(),player.getZ())));}
        if(player.isUsingItem()){
            if(player.getUseItem().is(Items.ENCHANTED_GOLDEN_APPLE)){
                boolean consumed=appleEating&&appleSlot>=0&&player.getInventory().getItem(appleSlot).getCount()<appleStartCount;
                mc.options.keyUse.setDown(!consumed&&appleEating);
                if(consumed){appleEating=false;record("native_apple_consumption_observed",Map.of("health",player.getHealth(),"slot",appleSlot,"trackingMissing",true));}
                stopMovement(mc);if(surfacingForAir&&player.isInWater())mc.options.keyJump.setDown(true);return;
            }
            if(player.getUseItem().getItem() instanceof BowItem)cancelBowByHotbar(mc,player);
            else mc.options.keyUse.setDown(false);
        }else{
            mc.options.keyUse.setDown(false);
            if(appleEating&&appleSlot>=0&&player.getInventory().getItem(appleSlot).getCount()<appleStartCount){appleEating=false;record("native_apple_consumption_observed",Map.of("health",player.getHealth(),"slot",appleSlot,"trackingMissing",true));}
            if(needsApple(player)){
                int food=findSlot(player,"apple");
                stopMovement(mc);if(surfacingForAir&&player.isInWater())mc.options.keyJump.setDown(true);
                if(food>=0&&!select(player,food)){
                    appleEating=true;appleSlot=food;appleStartCount=player.getInventory().getItem(food).getCount();lastAppleTick=ticks;
                    useInput(mc);record("eat_input",Map.of("health",player.getHealth(),"trackingMissing",true,"item","minecraft:enchanted_golden_apple"));
                }
                return;
            }
        }
        if(ragdoll||arenaCenter==null){stopMovement(mc);return;}
        Vec3 point=new Vec3(arenaCenter.x,player.getEyeY(),arenaCenter.z);
        double horizontal=point.subtract(player.getEyePosition()).horizontalDistance();
        aim(player,point,horizontal);movement(mc,player,horizontal>3?1:0,false,player.horizontalCollision);
        if(ticks%20==0)record("tracking_return_input",Map.of("distanceToAnchor",horizontal,"health",player.getHealth(),"position",List.of(player.getX(),player.getY(),player.getZ())));
    }
    private void cancelBowByHotbar(Minecraft mc,LocalPlayer player){
        int slot=findSlot(player,"sword");if(slot<0)slot=findSlot(player,"empty");
        select(player,slot);mc.options.keyUse.setDown(true);
    }
    private void recoverStalledUse(Minecraft mc,LocalPlayer player,String reason){
        record("native_use_full_release_retry",Map.of("reason",reason,"releaseTicks",40,"nativeUse",NativeUseDiagnostics.observe(player)));
        stopMining(mc);stopMovement(mc);select(player,findSlot(player,"sword"));mc.options.keyUse.setDown(false);
        appleEating=false;lastAppleTick=-1000;releaseUseUntil=ticks+40;
    }
    private boolean needsRangedApproach(Minecraft mc,LocalPlayer player,Entity target,boolean starting){
        if(surfacingForAir&&player.isInWater()||!player.hasLineOfSight(target))return true;
        double horizontal=target.position().subtract(player.position()).horizontalDistance();
        double limit=preferredRange>0?preferredRange+(starting?8:2):(starting?30:28);
        return horizontal>limit&&(mc.options.keyUp.isDown()||mc.options.keyDown.isDown()||mc.options.keyLeft.isDown()||mc.options.keyRight.isDown());
    }
    private Entity chooseTarget(Minecraft mc,LocalPlayer player,LivingEntity boss){
        if(boss instanceof EnderDragon dragon){
            if(landingApproach(dragon)||dragon.getPhaseManager().getCurrentPhase().isSitting())return dragon.head;
            EndCrystal crystal=null;
            for(Entity e:mc.level.entitiesForRendering())if(e instanceof EndCrystal c&&c.isAlive()&&player.distanceToSqr(c)<80*80&&player.hasLineOfSight(c)&&(crystal==null||player.distanceToSqr(c)<player.distanceToSqr(crystal)))crystal=c;
            if(crystal!=null)return crystal;
            return dragon.head;
        }
        if(id(boss).equals("twilightforest:hydra")&&boss.getParts()!=null){
            List<Entity> heads=new ArrayList<>(),open=new ArrayList<>();
            for(Entity part:boss.getParts())if(part!=null&&part.getClass().getSimpleName().equals("HydraHead"))try{
                String state=part.getClass().getMethod("getState").invoke(part).toString();
                if(state.equals("DEAD")||state.equals("DYING"))continue;
                heads.add(part);
                if(((Number)part.getClass().getMethod("getMouthOpen").invoke(part)).doubleValue()>.5)open.add(part);
            }catch(ReflectiveOperationException error){record("weakpoint_observation_error",Map.of("error",error.toString()));}
            List<Entity> candidates=open.isEmpty()?heads:open;
            if(lockedHead!=null&&heads.contains(lockedHead)&&ticks-headLockTick<45&&(weakPointOpen(lockedHead)||open.isEmpty()))return lockedHead;
            if(!candidates.isEmpty()){lockedHead=candidates.stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElseThrow();headLockTick=ticks;return lockedHead;}
        }
        // These native summons gate or obstruct the encounter. Their actual HP/AI remain intact.
        Entity minion=null;
        for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof LivingEntity living&&living.isAlive()
                &&Set.of("twilightforest:lich_minion","twilightforest:ice_crystal","twilightforest:carminite_ghastling","minecraft:phantom").contains(id(entity))
                &&player.distanceToSqr(entity)<32*32&&boss.distanceToSqr(entity)<48*48&&(minion==null||player.distanceToSqr(entity)<player.distanceToSqr(minion)))minion=entity;
        if(minion!=null)return minion;
        Entity best=boss.isPickable()?boss:null;
        if(boss.isMultipartEntity()){
            Entity[] parts=boss.getParts();
            if(parts!=null)for(Entity part:parts)if(part!=null&&part.isPickable()&&!part.getClass().getSimpleName().equals("SnowQueenIceShield")&&player.hasLineOfSight(part)
                    &&(best==null||player.getEyePosition().distanceTo(closest(part,player.getEyePosition()))<player.getEyePosition().distanceTo(closest(best,player.getEyePosition()))))best=part;
        }
        return best==null?boss:best;
    }
    private boolean isShieldedLich(LivingEntity boss){
        if(!id(boss).equals("twilightforest:lich"))return false;
        try{return ((Number)boss.getClass().getMethod("getShieldStrength").invoke(boss)).intValue()>0;}
        catch(ReflectiveOperationException error){if(ticks%20==0)record("weakpoint_observation_error",Map.of("error",error.toString()));return false;}
    }
    private Entity nearReflectable(Minecraft mc,LocalPlayer player,LivingEntity boss,double range){
        Entity closest=null;double best=Double.POSITIVE_INFINITY;
        for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof Projectile projectile
                &&Set.of("twilightforest:lich_bolt","twilightforest:hydra_mortar","cataclysm:ignis_abyss_fireball").contains(id(entity))&&projectile.getOwner()!=player&&player.hasLineOfSight(entity)){
            if(id(entity).equals("cataclysm:ignis_abyss_fireball"))try{
                if(!(boolean)entity.getClass().getMethod("getFired").invoke(entity))continue;
            }catch(ReflectiveOperationException unavailable){continue;}
            double distance=player.getEyePosition().distanceToSqr(closest(entity,player.getEyePosition()));
            if(distance>range*range||entity.getDeltaMovement().dot(player.getEyePosition().subtract(entity.position()))<-.01)continue;
            boolean realOwner=projectile.getOwner()==boss;
            // Off-axis clone bolts are considered only at close range; they must not
            // freeze movement while waiting for a real-Lich-aligned counter opportunity.
            if(id(boss).equals("twilightforest:lich")&&!realOwner&&distance>9)continue;
            double score=distance+(realOwner?0:100);
            if(score<best){best=score;closest=entity;}
        }
        return closest;
    }
    private boolean mineNativeRune(Minecraft mc,LocalPlayer player,LivingEntity boss){
        if(!Set.of("bosses_of_mass_destruction:obsidilith","bosses_of_mass_destruction:void_blossom").contains(id(boss))){stopMining(mc);return false;}
        if(miningBlock!=null&&!isMechanicMineBlock(mc,miningBlock)){
            record("client_rune_removed",Map.of("position",List.of(miningBlock.getX(),miningBlock.getY(),miningBlock.getZ())));stopMining(mc);
        }
        // Read synchronized visible blocks, not the server's private phase/pillar list.
        if(ticks-runeScanTick>=40||runeScanTick==0){
            // Anvil movement can carry the boss beyond a previously seen rune.
            // Keep native, still-existing targets until their blocks are removed.
            runeScanTick=ticks;BlockPos origin=boss.blockPosition();
            for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-18,-16,-18),origin.offset(18,16,18)))
                if(isRune(mc,pos)&&mc.level.getBlockState(pos).getDestroySpeed(mc.level,pos)>=0&&!runeTargets.contains(pos))runeTargets.add(pos.immutable());
        }
        runeTargets.removeIf(pos->!isRune(mc,pos));
        BlockPos target=runeTargets.stream().min(Comparator.comparingDouble(pos->pos.distToCenterSqr(player.position()))).orElse(null);
        if(target==null){stopMining(mc);return false;}
        if(player.isUsingItem()&&player.getUseItem().is(Items.SHIELD)){mc.options.keyUse.setDown(false);return true;}
        int tool=findSlot(player,"pickaxe");boolean changed=select(player,tool<0?findSlot(player,"sword"):tool);
        // Native healing flowers are only 3/16 block tall. The block center is air,
        // so aim inside the actual outline before ordinary crosshair mining.
        Vec3 point=blockAimPoint(mc,target);
        aim(player,point,player.getEyePosition().distanceTo(point));
        double horizontal=player.position().multiply(1,0,1).distanceTo(point.multiply(1,0,1));
        movement(mc,player,horizontal>2.4?1:0,false,player.horizontalCollision);
        mc.gameRenderer.pick(1);
        // Native healing flowers can be surrounded by vine-wall blocks. Clear only
        // a real, breakable native vine wall currently obstructing the flower ray.
        if(mc.hitResult instanceof BlockHitResult obstruction&&obstruction.getType()==HitResult.Type.BLOCK
                &&isVineWall(mc,obstruction.getBlockPos())
                &&mc.level.getBlockState(obstruction.getBlockPos()).getDestroySpeed(mc.level,obstruction.getBlockPos())>=0)
            target=obstruction.getBlockPos();
        if(changed||!(mc.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(target)
                ||player.getEyePosition().distanceTo(hit.getLocation())>player.blockInteractionRange()){stopMining(mc);return true;}
        if(!target.equals(miningBlock)){
            stopMining(mc);miningBlock=target;KeyMapping.click(mc.options.keyAttack.getKey());
            record("native_rune_mining_input",Map.of("position",List.of(target.getX(),target.getY(),target.getZ()),
                    "block",BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(target).getBlock()).toString()));
        }
        // Minecraft's normal held attack handling calls continueDestroyBlock once per tick.
        // Calling it again here would double the client's block-breaking progress.
        mc.options.keyAttack.setDown(true);
        if(!isMechanicMineBlock(mc,target)){record("native_rune_mining_complete",Map.of("position",List.of(target.getX(),target.getY(),target.getZ())));miningBlock=null;}
        return true;
    }
    private static boolean isRune(Minecraft mc,BlockPos pos){return Set.of("bosses_of_mass_destruction:obsidilith_rune","bosses_of_mass_destruction:void_blossom").contains(BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString());}
    private static boolean isVineWall(Minecraft mc,BlockPos pos){return BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString().equals("bosses_of_mass_destruction:vine_wall");}
    private static boolean isMechanicMineBlock(Minecraft mc,BlockPos pos){return isRune(mc,pos)||isVineWall(mc,pos);}
    private boolean isKnocked(LocalPlayer player){
        if(!downedLookupAttempted){downedLookupAttempted=true;try{nativeDowned=Class.forName("com.biguzi.ragrevival.client.RevivalClient").getMethod("isDowned",Player.class);}catch(ReflectiveOperationException unavailable){record("native_revival_lookup",Map.of("error",unavailable.toString(),"fallback","server metadata"));}}
        if(nativeDowned!=null)try{return (boolean)nativeDowned.invoke(null,player);}catch(ReflectiveOperationException error){if(ticks%20==0)record("native_revival_observation_error",Map.of("error",error.toString()));}
        return metadataKnocked;
    }
    private boolean needsApple(LocalPlayer player){
        if(findSlot(player,"apple")<0)return false;
        if(ticks-lastAppleTick<40)return false;
        var regeneration=player.getEffect(MobEffects.REGENERATION);
        return player.getHealth()+player.getAbsorptionAmount()<22||player.getAbsorptionAmount()<6
                ||player.getHealth()<17&&(regeneration==null||regeneration.getDuration()<80)||player.getFoodData().getFoodLevel()<16;
    }
    private static boolean weakPointOpen(Entity target){
        // Installed BMD explicitly accepts all parts when either combat compatibility
        // mod is loaded. Do not invent an eye-only restriction in that native mode.
        if(id(target).equals("bosses_of_mass_destruction:gauntlet")&&gauntletCombatCompatibility())return true;
        if(id(target).equals("bosses_of_mass_destruction:gauntlet"))try{
            Object bounds=target.getClass().getMethod("getBounds").invoke(target);
            return (boolean)bounds.getClass().getMethod("hasPart",String.class).invoke(bounds,"eye");
        }catch(ReflectiveOperationException unavailable){return true;}
        if(!target.getClass().getSimpleName().equals("HydraHead"))return true;
        try{return ((Number)target.getClass().getMethod("getMouthOpen").invoke(target)).doubleValue()>.55;}
        catch(ReflectiveOperationException unavailable){return false;}
    }
    private boolean preferMelee(LivingEntity boss,Entity target){
        if(weaponMode.equals("melee"))return true;
        if(weaponMode.equals("ranged"))return false;
        if(boss instanceof EnderDragon dragon&&(landingApproach(dragon)||dragon.getPhaseManager().getCurrentPhase().isSitting()))return true;
        if(id(boss).equals("minecraft:wither")&&boss.getHealth()<=boss.getMaxHealth()/2)return true;
        // The native powered half rejects arrows, just like the Wither's armor phase.
        if(id(boss).equals("cataclysm:the_harbinger")&&boss.getHealth()<=boss.getMaxHealth()/2)return true;
        if(id(boss).equals("cataclysm:the_leviathan"))return true;
        if(id(boss).equals("twilightforest:ur_ghast"))return false;
        if(target!=boss&&!target.getClass().getSimpleName().equals("HydraHead"))return target instanceof LivingEntity&&target.getY()-Minecraft.getInstance().player.getY()<4;
        return Set.of("twilightforest:naga","twilightforest:lich","twilightforest:minoshroom","twilightforest:knight_phantom","twilightforest:alpha_yeti",
                "bosses_of_mass_destruction:obsidilith",
                "cataclysm:netherite_monstrosity","cataclysm:ender_guardian","cataclysm:ancient_remnant","cataclysm:ignis","minecraft:warden").contains(id(boss));
    }
    private static boolean needsFlank(LivingEntity boss){return id(boss).equals("cataclysm:ignis");}
    private static boolean leviRushWindup(LivingEntity boss){
        if(!id(boss).equals("cataclysm:the_leviathan"))return false;
        try{
            return boss.getClass().getMethod("getAnimation").invoke(boss)==boss.getClass().getField("LEVIATHAN_RUSH").get(null)
                    &&((Number)boss.getClass().getMethod("getAnimationTick").invoke(boss)).intValue()<=54;
        }catch(ReflectiveOperationException unavailable){return false;}
    }
    private static boolean ignisParrying(LivingEntity boss){
        if(!id(boss).equals("cataclysm:ignis"))return false;
        try{
            Class<?> type=boss.getClass();Object animation=type.getMethod("getAnimation").invoke(boss);
            return animation==type.getField("COUNTER").get(null)||animation==type.getField("SHIELD_BREAK_COUNTER").get(null);
        }catch(ReflectiveOperationException unavailable){return false;}
    }
    private static boolean maledictusGrabDodgeWindow(LivingEntity boss){
        if(!id(boss).equals("cataclysm:maledictus"))return false;
        try{
            int state=((Number)boss.getClass().getMethod("getAttackState").invoke(boss)).intValue();
            int frame=boss.getClass().getField("attackTicks").getInt(boss);
            return state==28&&frame>=16||state==29;
        }catch(ReflectiveOperationException unavailable){return false;}
    }
    private static boolean landingApproach(EnderDragon dragon){int phase=dragon.getPhaseManager().getCurrentPhase().getPhase().getId();return phase==2||phase==3;}
    private void passiveDragonRetreat(Minecraft mc,LocalPlayer player,EnderDragon dragon){
        // Explicit passive spacing lets native SITTING_SCANNING choose its own
        // charge. Measure the root, never a moving head or a healing crystal.
        stopMining(mc);mc.options.keyUse.setDown(false);rangedApproach=false;
        if(select(player,findSlot(player,"sword"))){stopMovement(mc);return;}
        Vec3 towardRoot=dragon.position().subtract(player.position()).multiply(1,0,1);
        double horizontal=towardRoot.length();
        aim(player,new Vec3(dragon.getX(),player.getEyeY(),dragon.getZ()),horizontal);
        boolean facingRoot=player.getLookAngle().multiply(1,0,1).normalize().dot(towardRoot.normalize())>.8;
        int forward=facingRoot?(horizontal<preferredRange-.5?-1:horizontal>preferredRange+.5?1:0):0;
        movement(mc,player,forward,false,player.horizontalCollision);
        if(ticks%20==0)record("native_dragon_passive_retreat_input",Map.of(
                "phase",dragon.getPhaseManager().getCurrentPhase().getPhase().getId(),
                "rootHorizontalDistance",horizontal,"requestedRange",preferredRange,
                "requestedForward",forward,"forwardKey",mc.options.keyUp.isDown(),"backKey",mc.options.keyDown.isDown()));
    }
    private void approachDragonAnchor(Minecraft mc,LocalPlayer player){
        if(arenaCenter==null){stopMovement(mc);return;}
        if(walkDragonAccess(mc,player,arenaCenter))return;
        Vec3 waypoint=new Vec3(arenaCenter.x,player.getEyeY(),arenaCenter.z);
        double horizontal=waypoint.subtract(player.getEyePosition()).horizontalDistance();
        if(horizontal>3){aim(player,waypoint,horizontal);movement(mc,player,1,false,player.horizontalCollision);}
        else stopMovement(mc);
    }
    private boolean walkDragonAccess(Minecraft mc,LocalPlayer player,Vec3 goal){
        var step=dragonAccess.next(mc,player,goal,ticks);if(step==null)return false;
        stopMovement(mc);
        Vec3 delta=step.point().subtract(player.position());double horizontal=delta.horizontalDistance();
        if(horizontal>.22){
            boolean aimingBow=player.isUsingItem()&&player.getUseItem().getItem() instanceof BowItem;
            if(!aimingBow)aim(player,new Vec3(step.point().x,player.getEyeY(),step.point().z),horizontal);
            Vec3 facing=player.getLookAngle().multiply(1,0,1).normalize();
            Vec3 desired=delta.multiply(1,0,1).normalize();double ahead=facing.dot(desired);
            if(aimingBow){
                double side=desired.dot(new Vec3(facing.z,0,-facing.x));
                mc.options.keyUp.setDown(ahead>.4);mc.options.keyDown.setDown(ahead<-.4);
                mc.options.keyLeft.setDown(side>.4);mc.options.keyRight.setDown(side<-.4);
            }else if(ahead>.8){
                mc.options.keyUp.setDown(true);
                mc.options.keyJump.setDown(step.point().y>player.getY()+.35||player.horizontalCollision);
            }
        }
        if(ticks%20==0)record("native_dragon_access_input",Map.of("stage",step.stage(),"waypoint",List.of(step.point().x,step.point().y,step.point().z),"height",player.getY()));
        return true;
    }
    private void rangedMovement(Minecraft mc,LocalPlayer player,LivingEntity boss,Entity target){
        if(preferredRange>0){
            double horizontal=player.position().multiply(1,0,1).distanceTo(target.position().multiply(1,0,1));
            double tolerance=id(boss).equals("cataclysm:ignis")?.5:2;
            movement(mc,player,horizontal>preferredRange+tolerance?1:horizontal<preferredRange-tolerance?-1:0,false,player.horizontalCollision);
            return;
        }
        // The native Better End Island perch is high above its ground. The fixture
        // supplies a disclosed legal high-ground entry; use normal keys to remain
        // there, rather than following a flying head off its supporting surface.
        if(boss instanceof EnderDragon){approachDragonAnchor(mc,player);return;}
        double horizontal=player.position().multiply(1,0,1).distanceTo(target.position().multiply(1,0,1));
        int forward=horizontal>28?1:horizontal<10?-1:0;
        if(id(boss).equals("twilightforest:hydra")){
            double parentDistance=player.distanceTo(boss);forward=parentDistance>17?1:parentDistance<12?-1:0;
        }else if(id(boss).equals("twilightforest:ur_ghast")&&target==boss){
            forward=horizontal>28?1:horizontal<20?-1:0;
        }else if(id(boss).equals("bosses_of_mass_destruction:void_blossom")&&target==boss){
            // Native stem/contact thorns rapidly wear armor. Use ordinary bow shots
            // at the actual flower and keep a walking buffer outside the contact zone.
            forward=horizontal>22?1:horizontal<14?-1:0;
        }
        movement(mc,player,forward,false,player.horizontalCollision);
    }
    private boolean useChargedGhastTrap(Minecraft mc,LocalPlayer player,LivingEntity boss){
        if(!id(boss).equals("twilightforest:ur_ghast")||ticks-lastTrapInput<40)return false;
        BlockPos button=null;double nearest=Double.MAX_VALUE;
        for(BlockPos candidate:trapButtons)try{
            var blockEntity=mc.level.getBlockEntity(candidate.below());
            if(blockEntity==null||!(boolean)blockEntity.getClass().getMethod("isCharged").invoke(blockEntity))continue;
            double distance=candidate.distToCenterSqr(player.position());if(distance<nearest){button=candidate;nearest=distance;}
        }catch(ReflectiveOperationException notTrap){}
        if(button==null)return false;
        if(player.isUsingItem()&&player.getUseItem().is(Items.SHIELD)){mc.options.keyUse.setDown(false);return true;}
        stopMining(mc);int hand=findSlot(player,"empty");if(select(player,hand))return true;
        aim(player,blockAimPoint(mc,button),Math.sqrt(nearest));
        movement(mc,player,nearest>7?1:0,false,player.horizontalCollision);mc.gameRenderer.pick(1);
        if(mc.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(button)
                &&player.getEyePosition().distanceTo(hit.getLocation())<=player.blockInteractionRange()){
            useInput(mc);lastTrapInput=ticks;record("native_charged_trap_button_input",Map.of("position",List.of(button.getX(),button.getY(),button.getZ())));
        }
        return true;
    }
    private boolean useNativeEmp(Minecraft mc,LocalPlayer player,LivingEntity boss){
        if(!activateEmp||!id(boss).equals("cataclysm:the_harbinger")||ticks-lastEmpInput<40)return false;
        // The native Death Laser phase rejects the stun. Observe its synchronized
        // animation only; never set an animation, EMP property or charge counter.
        try{
            if(boss.getClass().getMethod("getAnimation").invoke(boss)==boss.getClass().getField("DEATHLASER_ANIMATION").get(null))return false;
        }catch(ReflectiveOperationException error){
            if(ticks%100==0)record("native_emp_observation_error",Map.of("error",error.toString()));
            return false;
        }
        BlockPos button=null;
        for(BlockPos candidate:empButtons){
            var state=mc.level.getBlockState(candidate.below());
            if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals("cataclysm:emp"))continue;
            boolean unavailable=false;
            for(var property:state.getValues().entrySet())
                if(Set.of("powered","overload").contains(property.getKey().getName())&&Boolean.TRUE.equals(property.getValue()))unavailable=true;
            if(!unavailable&&(button==null||candidate.distToCenterSqr(player.position())<button.distToCenterSqr(player.position())))button=candidate;
        }
        if(button==null)return false;
        if(player.isUsingItem()&&player.getUseItem().is(Items.SHIELD)){mc.options.keyUse.setDown(false);return true;}
        stopMining(mc);
        Vec3 point=blockAimPoint(mc,button);double horizontal=point.subtract(player.position()).horizontalDistance();
        aim(player,point,player.getEyePosition().distanceTo(point));
        movement(mc,player,horizontal>2.5?1:0,false,player.horizontalCollision);
        BlockPos emp=button.below();
        var nativePulse=new net.minecraft.world.phys.AABB(emp.getX()-5,emp.getY()-5,emp.getZ()-5,emp.getX()+5,emp.getY()+5,emp.getZ()+5);
        if(!boss.getBoundingBox().intersects(nativePulse)){
            if(ticks%100==0)record("native_emp_waiting_for_range",Map.of("button",List.of(button.getX(),button.getY(),button.getZ()),"bossPosition",List.of(boss.getX(),boss.getY(),boss.getZ())));
            return true;
        }
        int hand=findSlot(player,"empty");if(hand<0)hand=findSlot(player,"bow");if(select(player,hand))return true;
        mc.gameRenderer.pick(1);
        if(mc.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(button)
                &&player.getEyePosition().distanceTo(hit.getLocation())<=player.blockInteractionRange()){
            useInput(mc);lastEmpInput=ticks;
            record("native_emp_button_input",Map.of("position",List.of(button.getX(),button.getY(),button.getZ()),"nativePulseRange",true));
        }
        return true;
    }
    private static Vec3 blockAimPoint(Minecraft mc,BlockPos pos){
        var outline=mc.level.getBlockState(pos).getShape(mc.level,pos);
        return outline.isEmpty()?pos.getCenter():outline.bounds().getCenter().add(Vec3.atLowerCornerOf(pos));
    }
    private void stopMining(Minecraft mc){mc.options.keyAttack.setDown(false);miningBlock=null;}
    private void useInput(Minecraft mc){mc.options.keyUse.setDown(true);if(ticks>=nextUseClick){KeyMapping.click(mc.options.keyUse.getKey());nextUseClick=ticks+4;}}
    private static boolean select(LocalPlayer player,int slot){if(slot<0||player.getInventory().selected==slot)return false;player.getInventory().selected=slot;return true;}
    private static boolean nativeCrosshairSelects(Minecraft mc,Entity target){
        mc.gameRenderer.pick(1);
        if(!(mc.hitResult instanceof EntityHitResult hit))return false;
        if(target instanceof EnderDragonPart intended){
            // A native ray can hit another part before the intended head. Click
            // that real part only when it belongs to this Dragon and is in reach.
            return hit.getEntity() instanceof EnderDragonPart actual&&actual.parentMob==intended.parentMob
                    &&mc.player!=null&&mc.player.getEyePosition().distanceTo(hit.getLocation())<=mc.player.entityInteractionRange();
        }
        return hit.getEntity()==target;
    }
    private void observeDragonMeleeCandidate(Minecraft mc,LocalPlayer player,EnderDragon dragon,Entity target){
        mc.gameRenderer.pick(1);
        var head=dragon.head.getBoundingBox();Vec3 eye=player.getEyePosition();
        Vec3 desired=meleeAimPoint(target,eye);
        double rayReach=target.getBoundingBox().contains(eye)?0:target.getBoundingBox().clip(eye,desired).map(p->eye.distanceTo(p)).orElse(Double.POSITIVE_INFINITY);
        Map<String,Object> details=new java.util.LinkedHashMap<>();
        details.put("phase",dragon.getPhaseManager().getCurrentPhase().getPhase().getId());
        details.put("chosenPart",target instanceof EnderDragonPart part?part.name:target.getClass().getSimpleName());
        details.put("headBox",List.of(head.minX,head.minY,head.minZ,head.maxX,head.maxY,head.maxZ));
        details.put("eye",List.of(eye.x,eye.y,eye.z));
        details.put("headClosestDistance",eye.distanceTo(closest(dragon.head,eye)));
        details.put("chosenRayReach",Double.isFinite(rayReach)?(Object)rayReach:"no_intersection");
        details.put("interactionRange",player.entityInteractionRange());
        details.put("visible",player.hasLineOfSight(target));
        details.put("attackCooldown",player.getAttackStrengthScale(0));
        details.put("attackEnabled",attackEnabled);
        details.put("actualHitType",mc.hitResult==null?"none":mc.hitResult.getType().toString());
        if(mc.hitResult!=null)details.put("actualHitDistance",eye.distanceTo(mc.hitResult.getLocation()));
        if(mc.hitResult instanceof EntityHitResult hit){
            details.put("actualEntityClass",hit.getEntity().getClass().getName());
            details.put("actualEntityType",id(hit.getEntity()));
            details.put("exactChosenEntity",hit.getEntity()==target);
            if(hit.getEntity() instanceof EnderDragonPart part){
                details.put("actualPart",part.name);details.put("actualParent",part.parentMob.getUUID().toString());
                details.put("sameDragon",part.parentMob==dragon);
            }
        }
        details.put("policy","Native head-center aim and same-Dragon native picked-part acceptance; hypotheses until runtime observations");
        record("native_dragon_melee_candidate",details);
    }
    private static boolean isLichClone(Entity entity){if(!id(entity).equals("twilightforest:lich"))return false;try{return (boolean)entity.getClass().getMethod("isShadowClone").invoke(entity);}catch(ReflectiveOperationException unavailable){return false;}}
    private void aim(LocalPlayer player,Vec3 position,double distance){
        Vec3 delta=position.subtract(player.getEyePosition());
        double horizontal=Math.sqrt(delta.x*delta.x+delta.z*delta.z);
        float yaw=(float)(Mth.atan2(delta.z,delta.x)*180/Math.PI)-90;
        float pitch=(float)(-Mth.atan2(delta.y,horizontal)*180/Math.PI);
        if(player.getMainHandItem().getItem() instanceof BowItem&&player.isUsingItem())pitch=bowPitch(horizontal,delta.y);
        player.setYRot(player.getYRot()+Mth.clamp(Mth.wrapDegrees(yaw-player.getYRot()),-30,30));
        player.setXRot(Mth.clamp(player.getXRot()+Mth.clamp(pitch-player.getXRot(),-24,24),-90,90));
    }
    private static float bowPitch(double horizontal,double height){
        // Vanilla full-charge arrow drag/gravity; aim only, never alter its trajectory.
        double low=-80,high=80;
        for(int iteration=0;iteration<18;iteration++){
            double angle=(low+high)/2,radians=Math.toRadians(angle),vx=3*Math.cos(radians),vy=3*Math.sin(radians),x=0,y=0;
            for(int flight=0;flight<100&&x<horizontal;flight++){
                double fraction=Math.min(1,(horizontal-x)/Math.max(.001,vx));x+=vx*fraction;y+=vy*fraction;vx*=.99;vy=vy*.99-.05;
            }
            if(y<height)low=angle;else high=angle;
        }
        return (float)-(low+high)/2;
    }
    private static Vec3 aimPoint(Entity entity){
        if(id(entity).equals("bosses_of_mass_destruction:void_blossom"))try{
            Object bounds=entity.getClass().getMethod("getBounds").invoke(entity);
            Object part=bounds.getClass().getMethod("getPart",String.class).invoke(bounds,"flower");
            // The flower is absent during native SpikeWave; the stem still accepts
            // normal arrows. These are synchronized native parts, never invented boxes.
            if(part==null)part=bounds.getClass().getMethod("getPart",String.class).invoke(bounds,"rootYaw");
            if(part!=null){Object box=part.getClass().getMethod("getBox").invoke(part);return (Vec3)box.getClass().getMethod("getCenter").invoke(box);}
        }catch(ReflectiveOperationException ignored){}
        if(id(entity).equals("bosses_of_mass_destruction:gauntlet"))try{
            Object bounds=entity.getClass().getMethod("getBounds").invoke(entity);
            Object part=bounds.getClass().getMethod("getPart",String.class).invoke(bounds,gauntletCombatCompatibility()?"rootFist":"eye");
            if(part==null&&gauntletCombatCompatibility())part=bounds.getClass().getMethod("getPart",String.class).invoke(bounds,"rootPitch");
            if(part!=null){Object box=part.getClass().getMethod("getBox").invoke(part);return (Vec3)box.getClass().getMethod("getCenter").invoke(box);}
        }catch(ReflectiveOperationException ignored){}
        return entity instanceof LivingEntity living?living.getEyePosition().add(0,-.15,0):entity.getBoundingBox().getCenter();
    }
    private static boolean gauntletCombatCompatibility(){return ModList.get().isLoaded("bettercombat")||ModList.get().isLoaded("epicfight");}
    private static Vec3 meleeAimPoint(Entity entity,Vec3 eye){
        if(entity instanceof EnderDragonPart)return entity.getBoundingBox().getCenter();
        if(id(entity).equals("bosses_of_mass_destruction:void_blossom"))try{
            // The real stem is present in every native hitbox state. Its broad union
            // bounds and high eye position are not necessarily reachable hit surfaces.
            Object bounds=entity.getClass().getMethod("getBounds").invoke(entity);
            Object stem=bounds.getClass().getMethod("getPart",String.class).invoke(bounds,"rootYaw");
            if(stem!=null){
                Object box=stem.getClass().getMethod("getBox").invoke(stem);
                Vec3 center=(Vec3)box.getClass().getMethod("getCenter").invoke(box);
                var extents=(net.minecraft.world.phys.AABB)box.getClass().getMethod("getExtents").invoke(box);
                return new Vec3(center.x,Mth.clamp(eye.y,extents.minY+.05,extents.maxY-.05),center.z);
            }
        }catch(ReflectiveOperationException ignored){}
        // Aim just inside the closest ordinary box surface, rather than far above
        // the player's reach on a tall entity. This changes aiming, never hit reach.
        var box=entity.getBoundingBox();Vec3 center=box.getCenter(),nearest=closest(entity,eye);
        return nearest.lerp(center,.05);
    }
    private static Vec3 closest(Entity e,Vec3 p){var b=e.getBoundingBox();return new Vec3(Mth.clamp(p.x,b.minX,b.maxX),Mth.clamp(p.y,b.minY,b.maxY),Mth.clamp(p.z,b.minZ,b.maxZ));}
    private void movement(Minecraft mc,LocalPlayer player,int forward,boolean strafe,boolean jump){
        Vec3 facing=player.getLookAngle().multiply(1,0,1).normalize();
        Vec3 towardCenter=arenaCenter==null?Vec3.ZERO:arenaCenter.subtract(player.position()).multiply(1,0,1);
        boolean wasReturning=returningToArena;
        // A native RUSH counter needs an ordinary in-reach hit before frame55.
        // Temporarily follow that existing melee target instead of the soft home
        // waypoint; water support, surfacing, food and ragdoll rules still apply.
        // Yeti repeatedly moves beyond this test waypoint; follow it with normal
        // walking so its next native melee gate remains reachable.
        if(leviCounterWindup||ignisDodging||maledictusDodging
                ||Set.of("twilightforest:alpha_yeti","cataclysm:ignis","cataclysm:maledictus").contains(encounterBoss))returningToArena=false;
        else if(!endEncounter&&arenaCenter!=null){if(towardCenter.length()>arenaRadius)returningToArena=true;else if(towardCenter.length()<arenaRadius-8)returningToArena=false;}
        if(wasReturning!=returningToArena)record(returningToArena?"arena_return_start":"arena_return_end",Map.of("distanceToAnchor",towardCenter.length()));
        double returnSide=0;
        if(returningToArena){Vec3 desired=towardCenter.normalize();double ahead=desired.dot(facing);forward=ahead>.25?1:ahead<-.25?-1:0;returnSide=desired.dot(new Vec3(facing.z,0,-facing.x));}
        boolean forwardSafe=supportedForWalk(mc,player,player.position().add(facing.scale(forward>=0?2:-2)));
        boolean leftSafe=supportedForWalk(mc,player,player.position().add(facing.z*2,0,-facing.x*2));
        boolean rightSafe=supportedForWalk(mc,player,player.position().add(-facing.z*2,0,facing.x*2));
        mc.options.keyUp.setDown(forward>0&&forwardSafe);mc.options.keyDown.setDown(forward<0&&forwardSafe);
        if(lastNavigationPosition==null||player.position().distanceToSqr(lastNavigationPosition)>.04){stuckTicks=0;lastNavigationPosition=player.position();}else if(forward!=0)stuckTicks++;
        boolean obstacle=forward!=0&&(!forwardSafe||player.horizontalCollision||stuckTicks>15);
        mc.options.keyLeft.setDown(leftSafe&&(returningToArena?returnSide>.25:(strafe||obstacle)&&(ticks/60)%2==0));mc.options.keyRight.setDown(rightSafe&&(returningToArena?returnSide<-.25:(strafe||obstacle)&&(ticks/60)%2==1));
        mc.options.keyJump.setDown(player.isInWater()?navigationY>player.getEyeY()-.5:jump&&forwardSafe);
        if(player.isInWater()&&!player.isPassenger())mc.options.keyShift.setDown(navigationY<player.getY()-1.5);
        if(surfacingForAir&&player.isInWater()&&!player.isPassenger()){mc.options.keyJump.setDown(true);mc.options.keyShift.setDown(false);}
        mc.options.keySprint.setDown(forward>0&&forwardSafe&&player.getFoodData().getFoodLevel()>6);
    }
    private boolean supportedForWalk(Minecraft mc,LocalPlayer player,Vec3 position){
        if(encounterBoss.equals("twilightforest:naga")){
            // The disclosed four-block CRUMBLE entry pillar must be possible to
            // leave using ordinary walking/falling with the supplied equipment.
            BlockPos feet=BlockPos.containing(position);
            for(int drop=4;drop<=5;drop++)if(!mc.level.getBlockState(feet.below(drop)).getCollisionShape(mc.level,feet.below(drop)).isEmpty())return true;
        }
        if(endEncounter&&arenaCenter!=null&&player.getY()>=arenaCenter.y-1.5){
            BlockPos feet=BlockPos.containing(position);
            return !mc.level.getBlockState(feet.below()).getCollisionShape(mc.level,feet.below()).isEmpty()
                    ||!mc.level.getBlockState(feet).getCollisionShape(mc.level,feet).isEmpty();
        }
        return supported(mc,position);
    }
    private static boolean supported(Minecraft mc,Vec3 position){BlockPos ahead=BlockPos.containing(position);return !mc.level.isEmptyBlock(ahead.below())||!mc.level.isEmptyBlock(ahead.below(2))||!mc.level.isEmptyBlock(ahead.below(3));}
    private void retreat(Minecraft mc,LocalPlayer player,boolean backwards){movement(mc,player,backwards?-1:0,true,player.horizontalCollision);}
    private static void stopMovement(Minecraft mc){mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(false);mc.options.keyJump.setDown(false);mc.options.keySprint.setDown(false);}
    private static void release(Minecraft mc){stopMovement(mc);mc.options.keyUse.setDown(false);mc.options.keyAttack.setDown(false);mc.options.keyShift.setDown(false);}
    private static int findSlot(LocalPlayer player,String kind){
        for(int i=0;i<9;i++){var stack=player.getInventory().getItem(i);if(kind.equals("empty")&&stack.isEmpty())return i;if(stack.isEmpty())continue;
            if(kind.equals("sword")&&stack.getItem() instanceof SwordItem)return i;
            if(kind.equals("bow")&&stack.getItem() instanceof BowItem)return i;
            if(kind.equals("apple")&&stack.is(Items.ENCHANTED_GOLDEN_APPLE))return i;
            if(kind.equals("pickaxe")&&stack.is(Items.NETHERITE_PICKAXE))return i;
            if(kind.equals("food")&&(stack.is(Items.COOKED_BEEF)||stack.is(Items.COOKED_PORKCHOP)||stack.is(Items.GOLDEN_CARROT)))return i;
        }return -1;
    }
    private void readEncounter(){
        try{
            JsonObject state=JSON.fromJson(Files.readString(encounterFile),JsonObject.class);
            encounterRunning=state.has("running")&&state.get("running").getAsBoolean();metadataKnocked=state.has("playerKnocked")&&state.get("playerKnocked").getAsBoolean();
            if(!encounterRunning)return;
            Set<UUID> ids=new HashSet<>();if(state.has("bossUuids"))for(var value:state.getAsJsonArray("bossUuids"))ids.add(UUID.fromString(value.getAsString()));
            String key=state.get("boss").getAsString()+":"+ids.stream().sorted().toList();
            encounterBoss=state.get("boss").getAsString();encounterBosses.clear();encounterBosses.addAll(ids);
            trapButtons.clear();if(state.has("trapButtons"))for(var value:state.getAsJsonArray("trapButtons")){
                if(value.isJsonArray()){var a=value.getAsJsonArray();trapButtons.add(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));}
                else{var a=value.getAsJsonObject();trapButtons.add(new BlockPos(a.get("x").getAsInt(),a.get("y").getAsInt(),a.get("z").getAsInt()));}
            }
            empButtons.clear();if(state.has("empButtons"))for(var value:state.getAsJsonArray("empButtons")){
                if(value.isJsonArray()){var a=value.getAsJsonArray();empButtons.add(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));}
                else{var a=value.getAsJsonObject();empButtons.add(new BlockPos(a.get("x").getAsInt(),a.get("y").getAsInt(),a.get("z").getAsInt()));}
            }
            if(!key.equals(encounterId)){
                encounterId=key;wasKnocked=false;seatId="";appleEating=false;lockedHead=null;lastAppleTick=-1000;lastEmpInput=-1000;releaseUseUntil=0;feedAttemptSince=0;returningToArena=false;trackingMissing=false;rangedApproach=false;surfacingForAir=false;leviCounterWindup=false;ignisDodging=false;lastNavigationPosition=null;stuckTicks=0;
                arenaCenter=new Vec3(state.get("x").getAsDouble(),state.get("y").getAsDouble(),state.get("z").getAsDouble());
                arenaRadius=state.has("radius")?Math.max(16,state.get("radius").getAsDouble()):48;endEncounter=encounterBoss.equals("minecraft:ender_dragon");
                dragonAccess.configure(endEncounter&&state.has("dragonAccess")?state.getAsJsonObject("dragonAccess"):null);
                record("encounter_metadata",Map.of("boss",encounterBoss,"uuids",ids.stream().map(UUID::toString).toList(),"anchor",List.of(arenaCenter.x,arenaCenter.y,arenaCenter.z),"radius",arenaRadius,"gear",state.has("gear")?state.get("gear").getAsString():""));
            }
        }catch(Exception error){encounterRunning=false;if(ticks%100==0)record("encounter_metadata_unavailable",Map.of("error",error.toString()));}
    }
    private void readControl(){
        try{
            JsonObject json=JSON.fromJson(Files.readString(controls),JsonObject.class);
            enabled=json.has("enabled")&&json.get("enabled").getAsBoolean();
            String mode=json.has("weaponMode")?json.get("weaponMode").getAsString():"auto";
            if(!Set.of("auto","melee","ranged").contains(mode))mode="auto";
            double range=json.has("preferredRange")?json.get("preferredRange").getAsDouble():0;
            if(!Double.isFinite(range)||range<=0)range=0;else range=Mth.clamp(range,.5,64);
            boolean emp=json.has("activateEmp")&&json.get("activateEmp").getAsBoolean();
            boolean attacks=!json.has("attackEnabled")||json.get("attackEnabled").getAsBoolean();
            boolean guard=json.has("guardEnabled")&&json.get("guardEnabled").getAsBoolean();
            if(!mode.equals(weaponMode)||range!=preferredRange||emp!=activateEmp||attacks!=attackEnabled||guard!=guardEnabled){
                record("tactic_changed",Map.of("weaponMode",mode,"preferredRange",range,"activateEmp",emp,"attackEnabled",attacks,"guardEnabled",guard,
                        "scope","Normal weapon inputs and walking spacing; native heal/revival/counter/mechanic priorities retained"));
                weaponMode=mode;preferredRange=range;activateEmp=emp;attackEnabled=attacks;guardEnabled=guard;
            }
        }catch(Exception ignored){enabled=false;}
    }
    private List<Map<String,Object>> bossBars(Minecraft mc){
        try{
            Object overlay=mc.gui.getBossOverlay();
            if(overlayEvents==null){overlayEvents=overlay.getClass().getDeclaredField("events");overlayEvents.setAccessible(true);}
            List<Map<String,Object>> bars=new ArrayList<>();
            for(Object value:((Map<?,?>)overlayEvents.get(overlay)).values())if(value instanceof BossEvent bar){
                float progress=bar.getProgress();
                bars.add(Map.of("uuid",bar.getId().toString(),"name",bar.getName().getString(),"progress",Float.isFinite(progress)?(Object)progress:String.valueOf(progress),
                    "finiteAndInRange",Float.isFinite(progress)&&progress>=0&&progress<=1,
                    "color",bar.getColor().toString(),"overlay",bar.getOverlay().toString(),
                    "darkenScreen",bar.shouldDarkenScreen(),"music",bar.shouldPlayBossMusic(),"fog",bar.shouldCreateWorldFog()));
            }
            return bars;
        }catch(ReflectiveOperationException error){return List.of(Map.of("observationError",error.toString()));}
    }
    private Map<String,Object> nativeCombatInputState(){
        var mc=Minecraft.getInstance();Map<String,Object> values=new java.util.LinkedHashMap<>();
        for(String method:List.of("getComboCount","getUpswingTicks","getSwingProgress"))try{values.put(method,mc.getClass().getMethod(method).invoke(mc));}catch(ReflectiveOperationException absent){}
        values.put("heldAttack",mc.options.keyAttack.isDown());values.put("heldUse",mc.options.keyUse.isDown());values.put("heldCrouch",mc.options.keyShift.isDown());
        values.put("weaponMode",weaponMode);values.put("preferredRange",preferredRange);values.put("activateEmp",activateEmp);values.put("attackEnabled",attackEnabled);
        values.put("guardEnabled",guardEnabled);
        values.put("leviCounterWindup",leviCounterWindup);
        values.put("ignisDodging",ignisDodging);
        if(mc.player!=null){values.put("mainHand",BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString());values.put("nativeKnocked",isKnocked(mc.player));
            values.put("isUsingItem",mc.player.isUsingItem());values.put("useTicks",mc.player.getTicksUsingItem());values.put("appleAttemptTicks",appleEating?ticks-lastAppleTick:0);
            values.put("nativeUse",NativeUseDiagnostics.observe(mc.player));}
        return values;
    }
    private Map<String,Object> physicsState(LocalPlayer player){
        Minecraft mc=Minecraft.getInstance();Map<String,Object> result=new java.util.LinkedHashMap<>();Vec3 velocity=player.getDeltaMovement();
        result.put("velocity",List.of(velocity.x,velocity.y,velocity.z));result.put("onGround",player.onGround());result.put("noGravity",player.isNoGravity());result.put("noPhysics",player.noPhysics);
        result.put("gravityAttribute",player.getAttributeValue(Attributes.GRAVITY));result.put("horizontalCollision",player.horizontalCollision);result.put("verticalCollision",player.verticalCollision);result.put("fallDistance",player.fallDistance);
        result.put("passenger",player.isPassenger());result.put("vehicle",player.getVehicle()==null?"":id(player.getVehicle()));result.put("pose",player.getPose().toString());
        result.put("chunkLoaded",mc.level.hasChunkAt(player.blockPosition()));result.put("blockBelow",BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(player.blockPosition().below()).getBlock()).toString());
        result.put("cameraIsPlayer",mc.getCameraEntity()==player);result.put("flying",player.getAbilities().flying);result.put("mayFly",player.getAbilities().mayfly);
        result.put("forwardKey",mc.options.keyUp.isDown());result.put("backKey",mc.options.keyDown.isDown());result.put("leftKey",mc.options.keyLeft.isDown());result.put("rightKey",mc.options.keyRight.isDown());result.put("returningToArena",returningToArena);
        for(String method:List.of("sable$getFrozenToSubLevel","sable$getTrackingSubLevel","sable$getLastTrackingSubLevelID","sable$getCollisionInfo"))try{Object value=player.getClass().getMethod(method).invoke(player);result.put(method,value==null?"":value.toString());}catch(ReflectiveOperationException absent){}
        return result;
    }
    private void sample(LocalPlayer player,LivingEntity boss){if(ticks%20==0)record("sample",Map.of("health",player.getHealth(),"absorption",player.getAbsorptionAmount(),"food",player.getFoodData().getFoodLevel(),"boss",id(boss),"bossHealth",boss.getHealth(),"distance",player.distanceTo(boss),"position",List.of(player.getX(),player.getY(),player.getZ()),"receivedBossBars",bossBars(Minecraft.getInstance()),"nativeCombatInputState",nativeCombatInputState(),"physics",physicsState(player)));}
    private void record(String type,Map<String,Object> fields){try{var row=new java.util.LinkedHashMap<String,Object>();row.put("utc",Instant.now().toString());row.put("clientTick",ticks);row.put("event",type);row.putAll(fields);Files.writeString(log,JSON.toJson(row)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}}
    private static String id(Entity entity){return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();}
}

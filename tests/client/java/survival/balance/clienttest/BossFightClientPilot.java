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
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
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

    public BossFightClientPilot(IEventBus modBus){
        modBus.addListener((FMLClientSetupEvent event)->event.enqueueWork(ProductionFlightSources::restoreProductionSources));
        if(Boolean.getBoolean("survival.bossBalance.clientPlaytest"))NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();
        if(controls==null){controls=mc.gameDirectory.toPath().resolve("bossfight-control.json");log=mc.gameDirectory.toPath().resolve("client-fight-events.jsonl");}
        if(++ticks%20==0)readControl();
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
        LivingEntity boss=nearestBoss(mc,player);
        if(boss==null){arenaCenter=null;returningToArena=false;stopMining(mc);release(mc);return;}
        if(arenaCenter==null||!arenaBoss.equals(id(boss))){arenaCenter=player.position().add(boss.position()).scale(.5);arenaBoss=id(boss);returningToArena=false;endEncounter=boss instanceof EnderDragon;record("arena_anchor",Map.of("center",List.of(arenaCenter.x,arenaCenter.y,arenaCenter.z),"softReturn",!endEncounter));}
        if(!boss.getUUID().toString().equals(lastTarget)){stopMining(mc);runeTargets.clear();runeScanTick=0;lastTarget=boss.getUUID().toString();record("new_target",Map.of("boss",id(boss),"uuid",lastTarget));}
        boolean lichShielded=isShieldedLich(boss);
        Entity target=chooseTarget(mc,player,boss);
        Entity bolt=nearReflectable(mc,player,lichShielded?12:3);
        double distance=player.position().distanceTo(target.getBoundingBox().getCenter());
        Vec3 aim=bolt==null?aimPoint(target):bolt.getBoundingBox().getCenter();
        if(bolt==null&&target instanceof LivingEntity living&&distance>5)aim=aim.add(living.getDeltaMovement().scale(Math.min(15,distance/2.5)));
        aim(player,aim,bolt==null?distance:player.distanceTo(bolt));
        boolean defenseWindow=player.getHealth()<17&&distance<=10&&ticks%40>=22&&player.getOffhandItem().is(Items.SHIELD)&&!player.getCooldowns().isOnCooldown(Items.SHIELD);

        if(player.isUsingItem()){
            stopMining(mc);
            mc.options.keyUse.setDown(true);
            if(player.getUseItem().getItem() instanceof BowItem){
                retreat(mc,player,distance<10);
                if(lichShielded){mc.options.keyUse.setDown(false);record("protected_phase_bow_release_input",Map.of("boss",id(boss)));sample(player,boss);return;}
                if(defenseWindow){mc.options.keyUse.setDown(false);record("defense_bow_release_input",Map.of("health",player.getHealth(),"distance",distance));sample(player,boss);return;}
                if(player.getTicksUsingItem()>=25){int drawTicks=player.getTicksUsingItem();mc.options.keyUse.setDown(false);record("bow_release_input",Map.of("distance",distance,"drawTicks",drawTicks));}
            }else if(player.getUseItem().is(Items.SHIELD)){
                retreat(mc,player,true);
                if(!defenseWindow)mc.options.keyUse.setDown(false);
            }else retreat(mc,player,true);
            sample(player,boss);return;
        }
        mc.options.keyUse.setDown(false);
        if((player.getFoodData().getFoodLevel()<20&&player.getHealth()<player.getMaxHealth())||player.getFoodData().getFoodLevel()<=16){
            int food=findSlot(player,"food");
            if(food>=0){stopMining(mc);player.getInventory().selected=food;useInput(mc);record("eat_input",Map.of("health",player.getHealth(),"food",player.getFoodData().getFoodLevel(),"policy","eat_when_hurt_and_hunger_below20_or_hunger_at_most16"));retreat(mc,player,true);return;}
        }
        if(mineNativeRune(mc,player,boss)){sample(player,boss);return;}
        // Reflect visible Lich bolts using a real attack within player reach.
        if(bolt!=null){
            stopMining(mc);
            int sword=findSlot(player,"sword");if(select(player,sword))return;
            double counterReach=player.getEyePosition().distanceTo(closest(bolt,player.getEyePosition()));
            if(counterReach<=Math.min(3.0,player.entityInteractionRange())&&player.getAttackStrengthScale(0)>=.95f&&nativeCrosshairSelects(mc,bolt)){
                KeyMapping.click(mc.options.keyAttack.getKey());record("projectile_counter_input",Map.of("projectile",id(bolt)));
            }
            movement(mc,player,0,true,player.horizontalCollision);sample(player,boss);return;
        }
        if(defenseWindow){
            int sword=findSlot(player,"sword");
            if(select(player,sword)){record("defense_select_sword",Map.of("health",player.getHealth(),"distance",distance));retreat(mc,player,true);return;}
            useInput(mc);record("defense_shield_input",Map.of("health",player.getHealth(),"distance",distance,"cycleTick",ticks%40));retreat(mc,player,true);sample(player,boss);return;
        }
        if(lichShielded){
            // Shield count is native synchronized client data. Wait with a sword for a
            // visible bolt, then use the ordinary crosshair attack above; never rewrite
            // shields or reflect outside real player reach.
            int sword=findSlot(player,"sword");if(select(player,sword))return;
            movement(mc,player,distance>12?1:distance<5?-1:0,true,player.horizontalCollision);
            sample(player,boss);return;
        }
        boolean visible=player.hasLineOfSight(target);
        double reachDistance=player.getEyePosition().distanceTo(closest(target,player.getEyePosition()));
        if(visible&&reachDistance<=3.0&&!(target instanceof EndCrystal)){
            int sword=findSlot(player,"sword");if(select(player,sword))return;
            if(player.getAttackStrengthScale(0)>=.95f&&ticks%3==0&&nativeCrosshairSelects(mc,target)){KeyMapping.click(mc.options.keyAttack.getKey());record("melee_input",Map.of("target",id(target),"distance",reachDistance));}
            movement(mc,player,distance<2?-1:0,true,player.horizontalCollision);
        }else if(visible&&distance>=7&&distance<65&&findSlot(player,"bow")>=0){
            player.getInventory().selected=findSlot(player,"bow");
            useInput(mc);record("bow_draw_input",Map.of("distance",distance));
            retreat(mc,player,distance<12);
        }else movement(mc,player,1,true,player.horizontalCollision);
        sample(player,boss);
    }
    private LivingEntity nearestBoss(Minecraft mc,LocalPlayer player){
        List<LivingEntity> matches=new ArrayList<>();
        for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof LivingEntity living&&living.isAlive()&&BOSSES.contains(id(entity))&&!isLichClone(entity)&&player.distanceToSqr(entity)<160*160)matches.add(living);
        return matches.stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }
    private Entity chooseTarget(Minecraft mc,LocalPlayer player,LivingEntity boss){
        if(boss instanceof EnderDragon){
            EndCrystal crystal=null;
            for(Entity e:mc.level.entitiesForRendering())if(e instanceof EndCrystal c&&c.isAlive()&&player.distanceToSqr(c)<80*80&&player.hasLineOfSight(c)&&(crystal==null||player.distanceToSqr(c)<player.distanceToSqr(crystal)))crystal=c;
            if(crystal!=null)return crystal;
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
            if(!candidates.isEmpty())return candidates.stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElseThrow();
        }
        // These native summons gate or obstruct the encounter. Their actual HP/AI remain intact.
        Entity minion=null;
        for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof LivingEntity living&&living.isAlive()
                &&Set.of("twilightforest:lich_minion","twilightforest:ice_crystal","minecraft:phantom").contains(id(entity))
                &&player.distanceToSqr(entity)<12*12&&(minion==null||player.distanceToSqr(entity)<player.distanceToSqr(minion)))minion=entity;
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
    private Entity nearReflectable(Minecraft mc,LocalPlayer player,double range){
        Entity closest=null;double best=range*range;
        for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof Projectile&&id(entity).equals("twilightforest:lich_bolt")&&player.hasLineOfSight(entity)){
            double distance=player.getEyePosition().distanceToSqr(closest(entity,player.getEyePosition()));
            if(distance<=best){best=distance;closest=entity;}
        }
        return closest;
    }
    private boolean mineNativeRune(Minecraft mc,LocalPlayer player,LivingEntity boss){
        if(!id(boss).equals("bosses_of_mass_destruction:obsidilith")){stopMining(mc);return false;}
        if(miningBlock!=null&&!isRune(mc,miningBlock)){
            record("client_rune_removed",Map.of("position",List.of(miningBlock.getX(),miningBlock.getY(),miningBlock.getZ())));stopMining(mc);
        }
        // Read synchronized visible blocks, not the server's private phase/pillar list.
        if(ticks-runeScanTick>=20||runeScanTick==0){
            runeScanTick=ticks;runeTargets.clear();BlockPos origin=boss.blockPosition();
            for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-18,-16,-18),origin.offset(18,16,18)))
                if(isRune(mc,pos))runeTargets.add(pos.immutable());
        }
        runeTargets.removeIf(pos->!isRune(mc,pos));
        BlockPos target=runeTargets.stream().min(Comparator.comparingDouble(pos->pos.distToCenterSqr(player.position()))).orElse(null);
        if(target==null){stopMining(mc);return false;}
        int sword=findSlot(player,"sword");boolean changed=select(player,sword);
        Vec3 point=target.getCenter();aim(player,point,player.getEyePosition().distanceTo(point));
        double horizontal=player.position().multiply(1,0,1).distanceTo(point.multiply(1,0,1));
        movement(mc,player,horizontal>2.4?1:0,false,player.horizontalCollision);
        mc.gameRenderer.pick(1);
        if(changed||!(mc.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(target)
                ||player.getEyePosition().distanceTo(hit.getLocation())>player.blockInteractionRange()){stopMining(mc);return true;}
        if(!target.equals(miningBlock)){
            stopMining(mc);miningBlock=target;KeyMapping.click(mc.options.keyAttack.getKey());
            record("native_rune_mining_input",Map.of("position",List.of(target.getX(),target.getY(),target.getZ())));
        }
        // Minecraft's normal held attack handling calls continueDestroyBlock once per tick.
        // Calling it again here would double the client's block-breaking progress.
        mc.options.keyAttack.setDown(true);
        if(!isRune(mc,target)){record("native_rune_mining_complete",Map.of("position",List.of(target.getX(),target.getY(),target.getZ())));miningBlock=null;}
        return true;
    }
    private static boolean isRune(Minecraft mc,BlockPos pos){return BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString().equals("bosses_of_mass_destruction:obsidilith_rune");}
    private void stopMining(Minecraft mc){mc.options.keyAttack.setDown(false);miningBlock=null;}
    private void useInput(Minecraft mc){mc.options.keyUse.setDown(true);if(ticks>=nextUseClick){KeyMapping.click(mc.options.keyUse.getKey());nextUseClick=ticks+4;}}
    private static boolean select(LocalPlayer player,int slot){if(slot<0||player.getInventory().selected==slot)return false;player.getInventory().selected=slot;return true;}
    private static boolean nativeCrosshairSelects(Minecraft mc,Entity target){mc.gameRenderer.pick(1);return mc.hitResult instanceof EntityHitResult hit&&hit.getEntity()==target;}
    private static boolean isLichClone(Entity entity){if(!id(entity).equals("twilightforest:lich"))return false;try{return (boolean)entity.getClass().getMethod("isShadowClone").invoke(entity);}catch(ReflectiveOperationException unavailable){return false;}}
    private void aim(LocalPlayer player,Vec3 position,double distance){
        Vec3 delta=position.subtract(player.getEyePosition());
        double horizontal=Math.sqrt(delta.x*delta.x+delta.z*delta.z);
        float yaw=(float)(Mth.atan2(delta.z,delta.x)*180/Math.PI)-90;
        float pitch=(float)(-Mth.atan2(delta.y,horizontal)*180/Math.PI);
        if(player.getMainHandItem().getItem() instanceof BowItem)pitch-=Math.min(18,distance*.12);
        player.setYRot(player.getYRot()+Mth.clamp(Mth.wrapDegrees(yaw-player.getYRot()),-18,18));
        player.setXRot(Mth.clamp(player.getXRot()+Mth.clamp(pitch-player.getXRot(),-12,12),-90,90));
    }
    private static Vec3 aimPoint(Entity entity){return entity instanceof LivingEntity living?living.getEyePosition().add(0,-.15,0):entity.getBoundingBox().getCenter();}
    private static Vec3 closest(Entity e,Vec3 p){var b=e.getBoundingBox();return new Vec3(Mth.clamp(p.x,b.minX,b.maxX),Mth.clamp(p.y,b.minY,b.maxY),Mth.clamp(p.z,b.minZ,b.maxZ));}
    private void movement(Minecraft mc,LocalPlayer player,int forward,boolean strafe,boolean jump){
        Vec3 facing=player.getLookAngle().multiply(1,0,1).normalize();
        Vec3 towardCenter=arenaCenter==null?Vec3.ZERO:arenaCenter.subtract(player.position()).multiply(1,0,1);
        boolean wasReturning=returningToArena;
        if(!endEncounter&&arenaCenter!=null){if(towardCenter.length()>32)returningToArena=true;else if(towardCenter.length()<24)returningToArena=false;}
        if(wasReturning!=returningToArena)record(returningToArena?"arena_return_start":"arena_return_end",Map.of("distanceToAnchor",towardCenter.length()));
        double returnSide=0;
        if(returningToArena){Vec3 desired=towardCenter.normalize();double ahead=desired.dot(facing);forward=ahead>.25?1:ahead<-.25?-1:0;returnSide=desired.dot(new Vec3(facing.z,0,-facing.x));}
        boolean forwardSafe=supported(mc,player.position().add(facing.scale(forward>=0?2:-2)));
        boolean leftSafe=supported(mc,player.position().add(facing.z*2,0,-facing.x*2));
        boolean rightSafe=supported(mc,player.position().add(-facing.z*2,0,facing.x*2));
        mc.options.keyUp.setDown(forward>0&&forwardSafe);mc.options.keyDown.setDown(forward<0&&forwardSafe);
        mc.options.keyLeft.setDown(leftSafe&&(returningToArena?returnSide>.25:strafe&&(ticks/35)%2==0));mc.options.keyRight.setDown(rightSafe&&(returningToArena?returnSide<-.25:strafe&&(ticks/35)%2==1));
        mc.options.keyJump.setDown(player.isInWater()||jump&&forwardSafe);mc.options.keySprint.setDown(forward>0&&forwardSafe&&player.getFoodData().getFoodLevel()>6);
    }
    private static boolean supported(Minecraft mc,Vec3 position){BlockPos ahead=BlockPos.containing(position);return !mc.level.isEmptyBlock(ahead.below())||!mc.level.isEmptyBlock(ahead.below(2))||!mc.level.isEmptyBlock(ahead.below(3));}
    private void retreat(Minecraft mc,LocalPlayer player,boolean backwards){movement(mc,player,backwards?-1:0,true,player.horizontalCollision);}
    private static void release(Minecraft mc){mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(false);mc.options.keyJump.setDown(false);mc.options.keySprint.setDown(false);mc.options.keyUse.setDown(false);mc.options.keyAttack.setDown(false);}
    private static int findSlot(LocalPlayer player,String kind){
        for(int i=0;i<9;i++){var stack=player.getInventory().getItem(i);if(stack.isEmpty())continue;
            if(kind.equals("sword")&&stack.getItem() instanceof SwordItem)return i;
            if(kind.equals("bow")&&stack.getItem() instanceof BowItem)return i;
            if(kind.equals("food")&&(stack.is(Items.COOKED_BEEF)||stack.is(Items.COOKED_PORKCHOP)||stack.is(Items.GOLDEN_CARROT)))return i;
        }return -1;
    }
    private void readControl(){try{JsonObject json=JSON.fromJson(Files.readString(controls),JsonObject.class);enabled=json.has("enabled")&&json.get("enabled").getAsBoolean();}catch(Exception ignored){enabled=false;}}
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
        values.put("heldAttack",mc.options.keyAttack.isDown());values.put("heldUse",mc.options.keyUse.isDown());return values;
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
    private void sample(LocalPlayer player,LivingEntity boss){if(ticks%20==0)record("sample",Map.of("health",player.getHealth(),"food",player.getFoodData().getFoodLevel(),"boss",id(boss),"bossHealth",boss.getHealth(),"distance",player.distanceTo(boss),"position",List.of(player.getX(),player.getY(),player.getZ()),"receivedBossBars",bossBars(Minecraft.getInstance()),"nativeCombatInputState",nativeCombatInputState(),"physics",physicsState(player)));}
    private void record(String type,Map<String,Object> fields){try{var row=new java.util.LinkedHashMap<String,Object>();row.put("utc",Instant.now().toString());row.put("clientTick",ticks);row.put("event",type);row.putAll(fields);Files.writeString(log,JSON.toJson(row)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}}
    private static String id(Entity entity){return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();}
}

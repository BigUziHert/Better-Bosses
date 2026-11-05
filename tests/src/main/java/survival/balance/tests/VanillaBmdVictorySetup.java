package survival.balance.tests;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** TEST ONLY. Native encounter preparation and read-only mechanics observations.
 * No attack/phase selection, forced damage, or combat-state correction occurs here. */
public final class VanillaBmdVictorySetup {
    private static final String BMD="bosses_of_mass_destruction:";
    private static final AABB END_ARENA=new AABB(-128,-64,-128,128,384,128);
    private VanillaBmdVictorySetup() {}

    /** Call before combat, after loading the central End chunks. A living player
     * player must remain within the native manager's range while the ritual ticks.
     * The caller polls the returned handle; it must NOT spawn another dragon. */
    public static DragonPreparation beginDragonRitual(ServerLevel level,boolean rebuildNativeArena) throws ReflectiveOperationException {
        guard();
        if(level.dimension()!=Level.END)throw new IllegalArgumentException("Dragon ritual requires the real End dimension");
        EndDragonFight fight=level.getDragonFight();
        if(fight==null)throw new IllegalStateException("No native EndDragonFight manager");
        // This exact copied pack contains Better End Island. Its native reset
        // rebuilds its own podium/spikes; no handmade replacement arena is used.
        Method initial=findMethod(fight.getClass(),"doInitialDragonSpawn");
        if(rebuildNativeArena)invoke(fight,"reset",new Class<?>[]{boolean.class},false);
        if(!level.getDragons().isEmpty())throw new IllegalStateException("A live dragon remains before ritual preparation");
        BlockPos portal=(BlockPos)read(fight,"portalLocation");
        if(portal==null)throw new IllegalStateException("Native podium location unavailable after arena reset");
        List<BlockPos> supplied=new ArrayList<>();
        // EndCrystalUtils.checkForBEIRespawnCrystals uses these exact four slots.
        // Only summoning supplies are staged; the native ritual creates the
        // tower crystals and calls native createNewDragon/HOLDING_PATTERN.
        for(Direction direction:Direction.Plane.HORIZONTAL){
            BlockPos pos=portal.above().relative(direction,7);
            if(level.getEntitiesOfClass(EndCrystal.class,new AABB(pos)).isEmpty()){
                EndCrystal crystal=new EndCrystal(level,pos.getX()+.5,pos.getY(),pos.getZ()+.5);
                crystal.setShowBottom(true);
                if(!level.addFreshEntity(crystal))throw new IllegalStateException("Ritual crystal rejected at "+pos);
                supplied.add(pos);
            }
        }
        initial.invoke(fight);
        Object stage=invoke(fight,"getDragonRespawnStage",new Class<?>[0]);
        if(stage==null)throw new IllegalStateException("Native Better End Island ritual did not start");
        return new DragonPreparation(level,fight,portal,supplied,rebuildNativeArena);
    }

    public static final class DragonPreparation {
        private final ServerLevel level;
        private final EndDragonFight fight;
        private final BlockPos portal;
        private final List<BlockPos> supplied;
        private final boolean rebuilt;
        private final long start;
        private Vec3 combatArrival;
        private String combatAccess="not_prepared";
        private int accessBlocks;
        private Map<String,Object> combatGeometry=Map.of();
        private DragonPreparation(ServerLevel level,EndDragonFight fight,BlockPos portal,List<BlockPos> supplied,boolean rebuilt){
            this.level=level;this.fight=fight;this.portal=portal;this.supplied=List.copyOf(supplied);this.rebuilt=rebuilt;start=level.getGameTime();
        }
        /** No ticking or mutations: only accept the dragon made by the native
         * ritual after its normal initialization and complete tower-crystal scan. */
        public Optional<EnderDragon> pollReady() throws ReflectiveOperationException {
            if(invoke(fight,"getDragonRespawnStage",new Class<?>[0])!=null)return Optional.empty();
            if(!Boolean.TRUE.equals(invoke(fight,"hasDragonEverSpawned",new Class<?>[0])))return Optional.empty();
            if(fight.getDragonUUID()==null)return Optional.empty();
            Entity entity=level.getEntity(fight.getDragonUUID());
            if(!(entity instanceof EnderDragon dragon)||!dragon.isAlive())return Optional.empty();
            if(dragon.getPhaseManager().getCurrentPhase().getPhase()==EnderDragonPhase.HOVERING)return Optional.empty();
            int expected=SpikeFeature.getSpikesForLevel(level).size();
            if(expected<=0||fight.getCrystalsAlive()<expected)return Optional.empty();
            return Optional.of(dragon);
        }
        public Vec3 arrivalPosition(){return safeDragonArrival(level);}
        /** Call ONCE before combat. Supply a disclosed, player-buildable End-stone
         * combat floor and radial staircases around the existing tower, covering
         * air-only inner gaps while excluding the central x=0,z=0 column. No
         * central column, crystal, boss, phase, or combat attribute is changed. */
        public Vec3 combatArrival(){
            guard();
            if(combatArrival!=null)return combatArrival;
            int landingY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0);
            int feetY=landingY-2;
            List<BlockPos> ring=new ArrayList<>();int preservedObstructions=0,nativeFloorColumns=0;
            for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++){
                int radiusSquared=x*x+z*z;if(radiusSquared<1||radiusSquared>576)continue;
                BlockPos feet=new BlockPos(x,feetY,z);
                if(!clearAccessHeadroom(level,feet)){preservedObstructions++;continue;}
                if(level.getBlockState(feet.below()).isSolidRender(level,feet.below()))nativeFloorColumns++;
                collectSupport(level,feet.below(),ring);
            }
            List<Map<String,Object>> stairs=new ArrayList<>();
            for(Direction direction:Direction.Plane.HORIZONTAL){
                List<BlockPos> place=new ArrayList<>();boolean clear=true;
                BlockPos endpoint=null;
                // A three-wide landing bridges the circular rim to the stairs.
                for(int side=-1;side<=1;side++){
                    BlockPos feet=sidePosition(direction,24,side,feetY);
                    if(!clearAccessHeadroom(level,feet))clear=false;
                    collectSupport(level,feet.below(),place);
                }
                if(!clear)continue;
                for(int step=1;step<=30;step++){
                    int distance=24+step,y=feetY-step;
                    BlockPos center=sidePosition(direction,distance,0,y);
                    int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,center.getX(),center.getZ());
                    if(ground>=y){
                        BlockPos groundFeet=new BlockPos(center.getX(),ground,center.getZ());
                        if(ground<=y+1&&clearAccessHeadroom(level,groundFeet)&&level.getBlockState(groundFeet.below()).isSolidRender(level,groundFeet.below()))endpoint=groundFeet;
                        break;
                    }
                    for(int side=-1;side<=1;side++){
                        BlockPos feet=sidePosition(direction,distance,side,y);
                        if(!clearAccessHeadroom(level,feet)){clear=false;break;}
                        collectSupport(level,feet.below(),place);
                    }
                    if(!clear)break;
                }
                if(!clear||endpoint==null)continue;
                ring.addAll(place);
                stairs.add(Map.of("direction",direction.getName(),"topFeet",coordinates(sidePosition(direction,24,0,feetY)),"groundFeet",coordinates(endpoint),"width",3));
            }
            if(stairs.isEmpty())throw new IllegalStateException("No unobstructed native-ground stair route for Dragon combat ring");
            for(BlockPos pos:new LinkedHashSet<>(ring))if(level.getBlockState(pos).isAir()){
                level.setBlock(pos,Blocks.END_STONE.defaultBlockState(),3);accessBlocks++;
            }
            if(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0)!=landingY)throw new IllegalStateException("Dragon access changed central landing column");
            BlockPos best=null;double score=Double.MAX_VALUE;int walkableColumns=0;
            List<List<Integer>> waypoints=new ArrayList<>();
            for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++){
                int radiusSquared=x*x+z*z;if(radiusSquared<1||radiusSquared>576)continue;
                BlockPos feet=new BlockPos(x,feetY,z);if(!walkablePatch(level,feet))continue;walkableColumns++;
                double candidate=Math.abs(Math.sqrt(radiusSquared)-8);
                if(candidate<score){score=candidate;best=feet;}
            }
            for(Direction direction:Direction.Plane.HORIZONTAL)for(int distance:new int[]{8,12,18,22}){
                BlockPos feet=sidePosition(direction,distance,0,feetY);if(walkablePatch(level,feet))waypoints.add(coordinates(feet));
            }
            if(best==null)throw new IllegalStateException("Prepared Dragon combat ring has no walkable arrival patch");
            combatAccess="precombat_end_stone_ring_with_radial_staircases";
            combatGeometry=Map.of("landingY",landingY,"feetY",feetY,"innerRadius",1,"outerRadius",24,"walkableThreeByThreeCenters",walkableColumns,
                "nativeFloorColumnsRetained",nativeFloorColumns,"nativeObstructionColumnsRetained",preservedObstructions,"stairs",List.copyOf(stairs),"waypoints",List.copyOf(waypoints));
            return combatArrival=Vec3.atBottomCenterOf(best);
        }
        public Map<String,Object> combatGeometry(){return combatGeometry;}
        public Map<String,Object> status() throws ReflectiveOperationException {
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("preparationTicks",level.getGameTime()-start);
            result.put("nativeArenaReset",rebuilt);
            result.put("portal",coordinates(portal));
            result.put("summoningCrystalsSupplied",supplied.stream().map(VanillaBmdVictorySetup::coordinates).toList());
            Object stage=invoke(fight,"getDragonRespawnStage",new Class<?>[0]);
            result.put("nativeRespawnStage",stage==null?"none":stage.toString());
            result.put("hasDragonEverSpawned",invoke(fight,"hasDragonEverSpawned",new Class<?>[0]));
            result.put("crystalsAliveReported",fight.getCrystalsAlive());
            result.put("expectedTowerCrystals",SpikeFeature.getSpikesForLevel(level).size());
            result.put("liveCrystalsInArena",level.getEntitiesOfClass(EndCrystal.class,END_ARENA,Entity::isAlive).size());
            result.put("liveDragons",level.getDragons().stream().map(dragon->Map.of("phase",dragon.getPhaseManager().getCurrentPhase().getPhase().toString(),"hp",dragon.getHealth())).toList());
            result.put("combatAccess",combatAccess);result.put("accessBlocksPlaced",accessBlocks);
            result.put("combatGeometry",combatGeometry);
            if(combatArrival!=null)result.put("combatArrival",List.of(combatArrival.x,combatArrival.y,combatArrival.z));
            result.put("scope","Precombat native Better End Island arena reset and four supplied ritual crystals; native summoning ticks, tower regeneration and dragon initialization; no phase forcing");
            return result;
        }
    }

    private static BlockPos sidePosition(Direction direction,int distance,int side,int y){return new BlockPos(direction.getStepX()*distance+direction.getStepZ()*side,y,direction.getStepZ()*distance-direction.getStepX()*side);}
    private static boolean nearCrystal(ServerLevel level,BlockPos feet){return !level.getEntitiesOfClass(EndCrystal.class,new AABB(feet).inflate(4)).isEmpty();}
    private static boolean clearAccessHeadroom(ServerLevel level,BlockPos feet){return level.getBlockState(feet).isAir()&&level.getBlockState(feet.above()).isAir()&&!nearCrystal(level,feet);}
    private static boolean walkablePatch(ServerLevel level,BlockPos center){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            BlockPos feet=center.offset(x,0,z);
            if(!clearAccessHeadroom(level,feet)||!level.getBlockState(feet.below()).isSolidRender(level,feet.below()))return false;
        }
        return true;
    }
    private static void collectSupport(ServerLevel level,BlockPos top,List<BlockPos> positions){
        for(BlockPos pos=top;pos.getY()>level.getMinBuildHeight()&&level.getBlockState(pos).isAir();pos=pos.below())positions.add(pos);
    }

    /** Finds real terrain in native manager range; does not place safety blocks. */
    public static Vec3 safeDragonArrival(ServerLevel level){
        for(int radius:new int[]{32,48,24,64})for(Direction direction:Direction.Plane.HORIZONTAL){
            int x=direction.getStepX()*radius,z=direction.getStepZ()*radius;
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            BlockPos feet=new BlockPos(x,y,z);
            if(y>level.getMinBuildHeight()+1&&level.getBlockState(feet.below()).isSolidRender(level,feet.below())
                &&level.getBlockState(feet).isAir()&&level.getBlockState(feet.above()).isAir())return Vec3.atBottomCenterOf(feet);
        }
        throw new IllegalStateException("No safe native terrain found near End podium");
    }

    /** Supplemental state only. BMD attack selection must be logged from native
     * entity-event packets; client AnimationHolder is not server attack evidence. */
    public static Map<String,Object> snapshot(LivingEntity boss){
        Map<String,Object> result=new LinkedHashMap<>();
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString();
        if(boss instanceof EnderDragon dragon){
            result.put("dragonPhase",dragon.getPhaseManager().getCurrentPhase().getPhase().toString());
            result.put("sitting",dragon.getPhaseManager().getCurrentPhase().isSitting());
            result.put("crystalHealingLinked",dragon.nearestCrystal!=null&&dragon.nearestCrystal.isAlive());
            if(boss.level() instanceof ServerLevel level&&level.getDragonFight()!=null)result.put("crystalsAliveReported",level.getDragonFight().getCrystalsAlive());
        }else if(boss instanceof WitherBoss wither){
            result.put("spawnChargeTicks",wither.getInvulnerableTicks());result.put("poweredArrowImmune",wither.isPowered());
        }else if(boss instanceof Warden warden){
            result.put("pose",warden.getPose().toString());result.put("angerLevel",warden.getAngerLevel().toString());
            result.put("nativeAttackTarget",warden.getTarget()!=null);
        }else if(id.startsWith(BMD)){
            result.put("healthQuarterBand",Math.min(4,Math.max(0,(int)Math.ceil(4*boss.getHealth()/boss.getMaxHealth()))));
            result.put("healthBandIsDerived",true);
            if(boss instanceof Mob mob)result.put("nativeAttackTarget",mob.getTarget()!=null);
            try{
                if(id.equals(BMD+"obsidilith")){
                    result.put("shielded",invoke(boss,"isShielded",new Class<?>[0]));
                    List<?> pillars=(List<?>)read(boss,"activePillars");
                    result.put("activeRunePositions",pillars.stream().map(pos->coordinates((BlockPos)pos)).toList());
                }else if(id.equals(BMD+"gauntlet")){
                    result.put("energized",tracked(boss,"isEnergized"));result.put("laserTargetEntityId",tracked(boss,"laserTarget"));
                }
            }catch(ReflectiveOperationException error){result.put("observationError",error.toString());}
        }
        return result;
    }

    /** Names actual native event bytes; observing an event is selection/emission
     * evidence, not proof that its damage connected. */
    public static String nativeEventName(String bossId,byte event){
        if(!bossId.startsWith(BMD))return "";
        return switch(bossId.substring(BMD.length())){
            case "lich" -> switch(event){case 5->"CometAction";case 6->"VolleyAction";case 7->"MinionAction";case 8->"MinionRageAction";case 9->"TeleportAction";case 10->"teleport_return";case 11->"VolleyRageAction";case 12->"CometRageAction";case 13->"rage_health_milestone";default->"";};
            case "obsidilith" -> switch(event){case 5->"BurstAction";case 6->"WaveAction";case 7->"SpikeAction";case 8->"AnvilAction";case 9->"PillarAction";default->"";};
            case "void_blossom" -> switch(event){case 4->"SpikeAction";case 5->"SpikeWaveAction";case 7->"SporeAction";case 8->"BladeAction";case 9->"BlossomAction";case 10->"spawn";default->"";};
            case "gauntlet" -> switch(event){case 4->"PunchAction_start";case 5->"PunchAction_stop";case 7->"ground_pound_stop";case 8->"LaserAction_start";case 9->"LaserAction_stop";case 10->"SwirlPunchAction";case 11->"StatusEffectAction";default->"";};
            default->"";
        };
    }

    @SuppressWarnings("unchecked") private static Object tracked(LivingEntity entity,String name)throws ReflectiveOperationException{return entity.getEntityData().get((EntityDataAccessor<Object>)read(entity,name));}
    private static List<Integer> coordinates(BlockPos pos){return List.of(pos.getX(),pos.getY(),pos.getZ());}
    private static void guard(){if(!Boolean.getBoolean("survival.bossBalance.testMode"))throw new IllegalStateException("Isolated test-mode JVM required");}
    private static Object read(Object target,String name)throws ReflectiveOperationException{for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(name);}
    private static Method findMethod(Class<?> type,String name,Class<?>... arguments)throws NoSuchMethodException{for(Class<?> current=type;current!=null;current=current.getSuperclass())try{Method method=current.getDeclaredMethod(name,arguments);method.setAccessible(true);return method;}catch(NoSuchMethodException absent){}throw new NoSuchMethodException(name);}
    private static Object invoke(Object target,String name,Class<?>[] arguments,Object... values)throws ReflectiveOperationException{return findMethod(target.getClass(),name,arguments).invoke(target,values);}
}

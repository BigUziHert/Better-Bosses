package survival.balance.clienttest;

import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Test-only walking on observed blocks. Does not place blocks or move entities. */
final class DragonAccessNavigator {
    private record Stair(BlockPos bottom,BlockPos top) {}
    private record Node(BlockPos position,double score) {}
    record Step(Vec3 point,String stage) {}
    private final List<Stair> stairs=new ArrayList<>();
    private List<BlockPos> path=List.of();
    private BlockPos destination;
    private Stair recoveryStair;
    private boolean ascending;
    private int feetY,index;
    private long nextPlan;

    void configure(JsonObject geometry){
        stairs.clear();path=List.of();destination=null;recoveryStair=null;ascending=false;nextPlan=0;
        if(geometry==null||!geometry.has("feetY")||!geometry.has("stairs"))return;
        feetY=geometry.get("feetY").getAsInt();
        for(var value:geometry.getAsJsonArray("stairs")){
            var stair=value.getAsJsonObject();
            stairs.add(new Stair(position(stair,"groundFeet"),position(stair,"topFeet")));
        }
    }
    boolean belowRing(LocalPlayer player){return !stairs.isEmpty()&&player.getY()<feetY-1.25;}
    Step next(Minecraft mc,LocalPlayer player,Vec3 requested,long tick){
        if(stairs.isEmpty())return null;
        String stage="ring";
        Vec3 goal=requested;
        boolean recovering=belowRing(player)||recoveryStair!=null;
        if(recovering){
            if(recoveryStair==null){
                recoveryStair=stairs.stream().min(Comparator.comparingDouble(s->s.bottom.distToCenterSqr(player.position()))).orElseThrow();
                ascending=false;path=List.of();
            }
            if(!ascending&&horizontal(player.position(),Vec3.atBottomCenterOf(recoveryStair.bottom))<1.8
                    &&player.getY()>=recoveryStair.bottom.getY()-.75){ascending=true;path=List.of();}
            if(ascending&&player.getY()>=feetY-.3&&horizontal(player.position(),Vec3.atBottomCenterOf(recoveryStair.top))<2){
                recoveryStair=null;ascending=false;path=List.of();recovering=false;
            }else{
                goal=Vec3.atBottomCenterOf(ascending?recoveryStair.top:recoveryStair.bottom);
                stage=ascending?"stair_ascent":"stair_ground_approach";
            }
        }
        // At ring height, paths stay at that height and route around the native
        // central tower/hole. Ground and stair paths allow only one-block steps.
        boolean ring=!recovering&&player.getY()>=feetY-1.25;
        BlockPos end=nearestStandable(mc,player,BlockPos.containing(goal.x,ring?feetY:goal.y,goal.z),ring?feetY:Integer.MIN_VALUE,4);
        if(end==null)return new Step(player.position(),"no_supported_destination");
        BlockPos start=nearestStandable(mc,player,player.blockPosition(),ring?feetY:Integer.MIN_VALUE,2);
        if(start==null)return new Step(player.position(),"await_native_landing");
        boolean departed=index<path.size()&&horizontal(player.position(),Vec3.atBottomCenterOf(path.get(index)))>3;
        if(!end.equals(destination)||departed||tick>=nextPlan&&(path.isEmpty()||player.horizontalCollision)){
            destination=end;path=plan(mc,player,start,end,ring?feetY:Integer.MIN_VALUE);index=0;nextPlan=tick+20;
        }
        while(index<path.size()&&horizontal(player.position(),Vec3.atBottomCenterOf(path.get(index)))<.55
                &&Math.abs(player.getY()-path.get(index).getY())<.75)index++;
        if(index>=path.size())return new Step(Vec3.atBottomCenterOf(start),path.isEmpty()?"no_supported_path":stage);
        return new Step(Vec3.atBottomCenterOf(path.get(index)),stage);
    }
    private static List<BlockPos> plan(Minecraft mc,LocalPlayer player,BlockPos start,BlockPos goal,int fixedY){
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<BlockPos,Double> costs=new HashMap<>();Map<BlockPos,BlockPos> previous=new HashMap<>();Set<BlockPos> closed=new HashSet<>();
        costs.put(start,0d);open.add(new Node(start,estimate(start,goal)));
        int minX=Math.min(start.getX(),goal.getX())-16,maxX=Math.max(start.getX(),goal.getX())+16;
        int minZ=Math.min(start.getZ(),goal.getZ())-16,maxZ=Math.max(start.getZ(),goal.getZ())+16;
        int[][] directions={{1,0},{-1,0},{0,1},{0,-1}};
        while(!open.isEmpty()&&closed.size()<12000){
            BlockPos current=open.remove().position;if(!closed.add(current))continue;
            if(current.equals(goal)){
                LinkedList<BlockPos> result=new LinkedList<>();for(BlockPos at=current;at!=null;at=previous.get(at))result.addFirst(at);return result;
            }
            for(int[] direction:directions)for(int dy:new int[]{0,1,-1}){
                if(fixedY!=Integer.MIN_VALUE&&dy!=0)continue;
                BlockPos next=current.offset(direction[0],dy,direction[1]);
                if(next.getX()<minX||next.getX()>maxX||next.getZ()<minZ||next.getZ()>maxZ||Math.abs(next.getY()-start.getY())>40||!standable(mc,player,next))continue;
                double cost=costs.get(current)+1+Math.abs(dy)*.25;
                if(cost<costs.getOrDefault(next,Double.POSITIVE_INFINITY)){
                    costs.put(next,cost);previous.put(next,current);open.add(new Node(next,cost+estimate(next,goal)));
                }
                break;
            }
        }
        return List.of();
    }
    private static BlockPos nearestStandable(Minecraft mc,LocalPlayer player,BlockPos origin,int fixedY,int radius){
        BlockPos best=null;double score=Double.POSITIVE_INFINITY;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)for(int dy=-2;dy<=1;dy++){
            if(fixedY!=Integer.MIN_VALUE&&origin.getY()+dy!=fixedY)continue;
            BlockPos candidate=origin.offset(dx,dy,dz);double distance=candidate.distSqr(origin);
            if(distance<score&&standable(mc,player,candidate)){best=candidate;score=distance;}
        }
        return best;
    }
    private static boolean standable(Minecraft mc,LocalPlayer player,BlockPos feet){
        if(!mc.level.hasChunkAt(feet)||mc.level.getBlockState(feet.below()).getCollisionShape(mc.level,feet.below()).isEmpty())return false;
        return mc.level.getFluidState(feet).isEmpty()&&mc.level.noCollision(player,new AABB(feet.getX()+.2,feet.getY()+.01,feet.getZ()+.2,feet.getX()+.8,feet.getY()+1.8,feet.getZ()+.8));
    }
    private static double estimate(BlockPos a,BlockPos b){return Math.abs(a.getX()-b.getX())+Math.abs(a.getZ()-b.getZ())+Math.abs(a.getY()-b.getY())*.25;}
    private static double horizontal(Vec3 a,Vec3 b){return a.subtract(b).horizontalDistance();}
    private static BlockPos position(JsonObject object,String key){var a=object.getAsJsonArray(key);return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
}

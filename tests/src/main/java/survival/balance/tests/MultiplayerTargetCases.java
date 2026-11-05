package survival.balance.tests;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Native registered target-goal integration, using multiple server-side simulated players. */
final class MultiplayerTargetCases {
    private static final double X=256,Y=220,Z=256;
    private static final List<Map<String,Object>> OBSERVATIONS=new ArrayList<>();

    static void run(ServerLevel level)throws Exception {
        OBSERVATIONS.clear();
        for(int x=15;x<=17;x++)for(int z=15;z<=17;z++)level.getChunk(x,z);
        for(BlockPos pos:BlockPos.betweenClosed(248,219,248,288,225,264)){
            var desired=pos.getY()==219?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState();
            if(level.getBlockState(pos)!=desired)level.setBlock(pos,desired,2);
        }
        for(String id:List.of("twilightforest:naga","twilightforest:minoshroom","twilightforest:knight_phantom",
                "cataclysm:ender_guardian","bosses_of_mass_destruction:lich"))
            for(int count:new int[]{3,4,5})scenario(level,id,count);
        Path output=Path.of("../tests/results/multiplayer-target-selection.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(OBSERVATIONS));
    }

    private static void scenario(ServerLevel level,String id,int count){
        List<BossBalanceTests.TestPlayer> players=new ArrayList<>();Mob boss=null;
        try{
            for(int i=0;i<count;i++)players.add(player(level,i,5+i*3,null));
            boss=(Mob)BossBalanceTests.damageBoss(level,id);boss.moveTo(X,Y,Z,0,0);boss.setNoAi(false);
            if(id.startsWith("twilightforest:"))call(boss,"setRestrictionPoint",new Class<?>[]{GlobalPos.class},GlobalPos.of(level.dimension(),new BlockPos((int)X,(int)Y,(int)Z)));
            if(!level.addFreshEntity(boss))throw new IllegalStateException("Cannot add target-selection boss");
            boss.getRandom().setSeed(508112L+count);
            GoalSelector selector=(GoalSelector)field(boss,"targetSelector");
            double base=boss.getMaxHealth();
            check(id,count,"starts_without_forced_target",boss.getTarget()==null);
            record(id,count,"registered_goals",boss,players,"Native goals="+selector.getAvailableGoals().stream().map(w->w.getPriority()+":"+w.getGoal().getClass().getName()).toList());
            acquire(id,count,"initial_nearest",boss,selector,players,players.get(0));
            BossBalanceTests.result("multiplayer_"+count+"_natural_target_party_max",id,base,boss.getMaxHealth());
            double locked=boss.getMaxHealth();
            if(count==3){
                players.add(player(level,3,20,null));players.add(player(level,4,23,null));
                step(boss,selector,40);
                record(id,count,"late_arrivals_3_to_5",boss,players,"Two eligible players joined after encounter lock; no target assignment performed.");
                BossBalanceTests.result("multiplayer_3_late_5_max_stable",id,locked,boss.getMaxHealth());
            }
            var originalProfile=players.get(0).getGameProfile();players.get(0).remove(Entity.RemovalReason.DISCARDED);
            acquire(id,count,"selected_player_removed",boss,selector,players,players.get(1));
            BossBalanceTests.result("multiplayer_"+count+"_leave_max_stable",id,locked,boss.getMaxHealth());
            players.get(1).moveTo(X+512,Y,Z,0,0);
            acquire(id,count,"selected_player_out_of_range",boss,selector,players,players.get(2));
            BossBalanceTests.result("multiplayer_"+count+"_range_max_stable",id,locked,boss.getMaxHealth());
            var returned=player(level,9,3,originalProfile);players.add(returned);
            step(boss,selector,80);
            record(id,count,"same_uuid_rejoined_closer",boss,players,"Original UUID re-added at3blocks; existing valid target is recorded, not forced.");
            check(id,count,"valid_target_not_automatically_replaced",boss.getTarget()==players.get(2));
            players.get(2).remove(Entity.RemovalReason.DISCARDED);
            acquire(id,count,"rejoined_player_acquired_after_target_removed",boss,selector,players,returned);
            BossBalanceTests.result("multiplayer_"+count+"_rejoin_max_stable",id,locked,boss.getMaxHealth());
        }catch(Throwable error){BossBalanceTests.error("multiplayer_targets_"+count,id,error);}
        finally{if(boss!=null)boss.discard();for(var player:players)player.remove(Entity.RemovalReason.DISCARDED);}
    }

    private static BossBalanceTests.TestPlayer player(ServerLevel level,int index,double distance,GameProfile profile)throws Exception{
        if(profile==null)profile=new GameProfile(UUID.randomUUID(),"BBTarget"+index+UUID.randomUUID().toString().substring(0,4));
        var player=new BossBalanceTests.TestPlayer(level,profile);player.setGameMode(GameType.SURVIVAL);
        BossBalanceTests.resetActualPlayer(player);player.moveTo(X+distance,Y,Z,90,0);player.setNoGravity(true);
        level.addNewPlayer(player);return player;
    }

    private static void acquire(String id,int count,String stage,Mob boss,GoalSelector selector,
            List<BossBalanceTests.TestPlayer> players,LivingEntity expected){
        int ticks=0;
        while(boss.getTarget()!=expected&&ticks<240){step(boss,selector,1);ticks++;}
        record(id,count,stage,boss,players,"Expected="+expected.getName().getString()+"; native target-selector ticks="+ticks);
        check(id,count,stage,boss.getTarget()==expected);
    }
    private static void step(Mob boss,GoalSelector selector,int ticks){
        for(int i=0;i<ticks;i++){boss.tickCount++;boss.getSensing().tick();selector.tick();}
    }
    private static void record(String id,int count,String stage,Mob boss,List<BossBalanceTests.TestPlayer> players,String detail){
        Map<String,Object> row=new LinkedHashMap<>();row.put("boss",id);row.put("initialPlayers",count);row.put("stage",stage);row.put("detail",detail);
        row.put("targetName",boss.getTarget()==null?null:boss.getTarget().getName().getString());
        row.put("targetUuid",boss.getTarget()==null?null:boss.getTarget().getUUID().toString());
        row.put("maxHealth",boss.getMaxHealth());row.put("bossTickCount",boss.tickCount);
        row.put("players",players.stream().map(p->{Map<String,Object> item=new LinkedHashMap<>();item.put("name",p.getName().getString());item.put("uuid",p.getUUID().toString());item.put("removed",p.isRemoved());item.put("survival",!p.isCreative()&&!p.isSpectator());item.put("distance",p.distanceTo(boss));return item;}).toList());
        row.put("scope","Registered target selector and sensing tick normally; action/movement goals and full fights are excluded. No test code calls setTarget.");
        OBSERVATIONS.add(row);System.out.println("BBMULTITARGET "+new GsonBuilder().create().toJson(row));
    }
    private static void check(String id,int count,String name,boolean passed){BossBalanceTests.result("multiplayer_"+count+"_"+name,id,1,passed?1:0);}
    private static Object field(Object object,String name)throws Exception{for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private static Object call(Object object,String name,Class<?>[] types,Object...args)throws Exception{for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
}

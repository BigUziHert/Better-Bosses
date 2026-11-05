package survival.balance.tests;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

/** Test-only fixed-HP lifecycle and migration regression. No production configuration mutation. */
final class FixedHealthCases {
    private static final String DATA_KEY="survival_boss_balance_health";
    private static final ResourceLocation CURRENT=ResourceLocation.parse("survival_boss_balance:health");
    private static final ResourceLocation LEGACY=ResourceLocation.parse("survival_tweaks:twilight_boss_health_3x");
    private static final ResourceLocation FOREIGN=ResourceLocation.parse("boss_balance_tests:unrelated_health_modifier");
    private static final Map<String,Double> BASES=new LinkedHashMap<>();
    private static int serial;
    static {
        BASES.put("minecraft:wither",900d);
        BASES.put("minecraft:warden",1300d);
        BASES.put("minecraft:ender_dragon",1100d);
        BASES.put("twilightforest:naga",1000d);
        BASES.put("twilightforest:lich",650d);
        BASES.put("twilightforest:minoshroom",1000d);
        BASES.put("twilightforest:knight_phantom",180d);
        BASES.put("twilightforest:alpha_yeti",1100d);
        BASES.put("twilightforest:hydra",1500d);
        BASES.put("twilightforest:ur_ghast",1100d);
        BASES.put("twilightforest:snow_queen",1200d);
        BASES.put("bosses_of_mass_destruction:lich",1300d);
        BASES.put("bosses_of_mass_destruction:void_blossom",1400d);
        BASES.put("bosses_of_mass_destruction:gauntlet",1500d);
        BASES.put("bosses_of_mass_destruction:obsidilith",1700d);
        BASES.put("cataclysm:netherite_monstrosity",1200d);
        BASES.put("cataclysm:ender_guardian",850d);
        BASES.put("cataclysm:the_harbinger",950d);
        BASES.put("cataclysm:ancient_remnant",1000d);
        BASES.put("cataclysm:the_leviathan",1100d);
        BASES.put("cataclysm:scylla",1100d);
        BASES.put("cataclysm:maledictus",1100d);
        BASES.put("cataclysm:ignis",1100d);
    }

    static void run(ServerLevel level)throws Exception {
        // Independent expected values are the reviewed configured bases, not values read back
        // from the implementation under test. All players are actual server-world entities.
        for(int count:new int[]{0,1,2,3,4,5,10}){
            List<BossBalanceTests.TestPlayer> players=new ArrayList<>();
            try {
                for(int i=0;i<count;i++)players.add(player(level,null,i));
                for(var entry:BASES.entrySet())freshParty(level,entry.getKey(),entry.getValue(),count,players);
            }finally{removePlayers(players);}
        }
        for(var entry:BASES.entrySet()){
            changingParty(level,entry.getKey(),entry.getValue());
            for(int oldParty:new int[]{4,5})migration(level,entry.getKey(),entry.getValue(),oldParty);
        }
        controls(level);
        nativeCombatAndReload(level);
    }

    private static void freshParty(ServerLevel level,String id,double base,int count,List<BossBalanceTests.TestPlayer> players){
        Mob boss=null;
        try {
            boss=(Mob)BossBalanceTests.damageBoss(level,id);
            Double nativeAttack=attackValue(boss);
            join(level,boss);
            eq(id,"fresh_party_"+count+"_max",base,boss.getMaxHealth());
            eq(id,"fresh_party_"+count+"_full",base,boss.getHealth());
            boss.setHealth((float)(base*.4));
            boss.setTarget(players.isEmpty()?null:players.getFirst()); // Real LivingChangeTargetEvent pathway.
            eq(id,"first_target_party_"+count+"_max",base,boss.getMaxHealth());
            eq(id,"first_target_party_"+count+"_wounded",base*.4,boss.getHealth());
            sameAttack(id,"fresh_party_"+count,boss,nativeAttack);
            for(var player:players)player.moveTo(2000,220,2000,0,0);
            stable(id,"all_party_"+count+"_far_away",boss,base,base*.4);
            for(var player:players)player.setGameMode(GameType.SPECTATOR);
            stable(id,"all_party_"+count+"_spectators",boss,base,base*.4);
            for(int i=0;i<players.size();i++){players.get(i).setGameMode(GameType.SURVIVAL);players.get(i).moveTo(5+i,220,4,0,0);}
            stable(id,"party_"+count+"_returned",boss,base,base*.4);
        }catch(Throwable error){BossBalanceTests.error("fixed_fresh_party_"+count,id,error);}
        finally{if(boss!=null)boss.discard();}
    }

    private static void changingParty(ServerLevel level,String id,double base){
        List<BossBalanceTests.TestPlayer> players=new ArrayList<>();Mob boss=null;
        try {
            players.add(player(level,null,0));GameProfile returning=players.getFirst().getGameProfile();
            boss=(Mob)BossBalanceTests.damageBoss(level,id);join(level,boss);boss.setTarget(players.getFirst());
            boss.setHealth((float)(base*.6));double wounded=boss.getHealth();Double nativeAttack=attackValue(boss);
            for(int count=2;count<=5;count++){
                var joined=player(level,null,count-1);players.add(joined);boss.setTarget(joined);
                stable(id,"join_to_"+count,boss,base,wounded);
            }
            for(int remaining=4;remaining>=1;remaining--){
                players.get(remaining).remove(Entity.RemovalReason.DISCARDED);
                boss.setTarget(players.get(remaining-1));
                stable(id,"leave_to_"+remaining,boss,base,wounded);
            }
            players.getFirst().remove(Entity.RemovalReason.DISCARDED);boss.setTarget(null);
            stable(id,"all_players_left",boss,base,wounded);
            var rejoined=player(level,returning,0);players.add(rejoined);boss.setTarget(rejoined);
            stable(id,"same_uuid_player_rejoined",boss,base,wounded);
            UUID bossUuid=boss.getUUID();boss=(Mob)reload(level,id,boss);
            stable(id,"wounded_boss_nbt_rejoin",boss,base,wounded);
            yes(id,"wounded_boss_uuid_preserved",bossUuid.equals(boss.getUUID()));
            boss.setTarget(rejoined);stable(id,"target_after_boss_reload",boss,base,wounded);
            sameAttack(id,"join_leave_rejoin",boss,nativeAttack);
            nagaThreshold(id,boss,base);
        }catch(Throwable error){BossBalanceTests.error("fixed_join_leave_rejoin",id,error);}
        finally{if(boss!=null)boss.discard();removePlayers(players);}
    }

    private static void migration(ServerLevel level,String id,double base,int oldParty){
        List<BossBalanceTests.TestPlayer> players=new ArrayList<>();LivingEntity boss=null;
        String prefix="v1_party_"+oldParty;
        try {
            for(int i=0;i<5;i++)players.add(player(level,null,i));
            boss=BossBalanceTests.damageBoss(level,id);Double nativeAttack=attackValue(boss);
            var health=boss.getAttribute(Attributes.MAX_HEALTH);
            health.removeModifier(CURRENT);health.removeModifier(LEGACY);
            health.addPermanentModifier(new AttributeModifier(FOREIGN,7,AttributeModifier.Operation.ADD_VALUE));
            double oldMaximum=base*(id.startsWith("cataclysm:")?1:oldParty==4?1.15:1.3);
            health.addPermanentModifier(new AttributeModifier(CURRENT,oldMaximum/health.getValue()-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            boss.setHealth((float)(oldMaximum*.4));
            CompoundTag oldState=new CompoundTag();
            oldState.putInt("version",1);oldState.putBoolean("party_locked",true);
            oldState.putInt("party_size",oldParty);oldState.putDouble("configured_base_hp",base);oldState.putDouble("target_hp",oldMaximum);
            boss.getPersistentData().put(DATA_KEY,oldState);
            eq(id,prefix+"_fixture_max",oldMaximum,boss.getMaxHealth());
            eq(id,prefix+"_fixture_wounded",oldMaximum*.4,boss.getHealth());
            UUID uuid=boss.getUUID();CompoundTag saved=save(id,boss);boss.discard();
            boss=load(level,saved);
            // Real saved attributes and persistent NBT are present before the join event.
            eq(id,prefix+"_nbt_before_join_max",oldMaximum,boss.getMaxHealth());
            eq(id,prefix+"_nbt_before_join_wounded",oldMaximum*.4,boss.getHealth());
            join(level,boss);
            stable(id,prefix+"_migrated",boss,base,base*.4);
            yes(id,prefix+"_uuid_preserved",uuid.equals(boss.getUUID()));
            yes(id,prefix+"_unrelated_modifier_preserved",boss.getAttribute(Attributes.MAX_HEALTH).hasModifier(FOREIGN));
            eq(id,prefix+"_stored_target_fixed",base,boss.getPersistentData().getCompound(DATA_KEY).getDouble("target_hp"));
            ((Mob)boss).setTarget(players.getFirst());stable(id,prefix+"_combat_with_five",boss,base,base*.4);
            boss=reload(level,id,boss);stable(id,prefix+"_second_nbt_rejoin",boss,base,base*.4);
            sameAttack(id,prefix+"_migration",boss,nativeAttack);nagaThreshold(id,boss,base);
        }catch(Throwable error){BossBalanceTests.error("fixed_migration_"+prefix,id,error);}
        finally{if(boss!=null)boss.discard();removePlayers(players);}
    }

    private static void controls(ServerLevel level){
        List<BossBalanceTests.TestPlayer> players=new ArrayList<>();
        try {
            for(int i=0;i<5;i++)players.add(player(level,null,i));
            for(String id:List.of("minecraft:cow","minecraft:zombie","twilightforest:minotaur","cataclysm:ender_golem","minecraft:elder_guardian")){
                LivingEntity entity=null;
                try {
                    entity=BossBalanceTests.damageBoss(level,id);double nativeMaximum=entity.getMaxHealth();Double attack=attackValue(entity);
                    entity.setHealth((float)(nativeMaximum*.4));join(level,entity);
                    if(entity instanceof Mob mob)mob.setTarget(players.getFirst());
                    stable(id,"unmanaged_lifecycle",entity,nativeMaximum,nativeMaximum*.4);
                    yes(id,"unmanaged_no_health_modifier",!entity.getAttribute(Attributes.MAX_HEALTH).hasModifier(CURRENT));
                    sameAttack(id,"unmanaged_lifecycle",entity,attack);
                    entity=reload(level,id,entity);stable(id,"unmanaged_nbt_rejoin",entity,nativeMaximum,nativeMaximum*.4);
                }catch(Throwable error){BossBalanceTests.error("fixed_unmanaged_control",id,error);}
                finally{if(entity!=null)entity.discard();}
            }
            LivingEntity clone=null;
            try {
                clone=BossBalanceTests.damageBoss(level,"twilightforest:lich");CompoundTag saved=clone.saveWithoutId(new CompoundTag());
                saved.putUUID("MasterLich",UUID.randomUUID());clone.load(saved);
                double nativeMaximum=clone.getMaxHealth();clone.setHealth((float)(nativeMaximum*.4));join(level,clone);
                ((Mob)clone).setTarget(players.getFirst());stable("twilightforest:lich_clone","clone_excluded",clone,nativeMaximum,nativeMaximum*.4);
                yes("twilightforest:lich_clone","clone_no_health_modifier",!clone.getAttribute(Attributes.MAX_HEALTH).hasModifier(CURRENT));
            }finally{if(clone!=null)clone.discard();}
        }catch(Throwable error){BossBalanceTests.error("fixed_controls","controls",error);}
        finally{removePlayers(players);}
    }

    private static void nativeCombatAndReload(ServerLevel level){
        String id="twilightforest:minoshroom";Mob boss=null;List<BossBalanceTests.TestPlayer> players=new ArrayList<>();
        try {
            var victim=player(level,null,0);players.add(victim);BossBalanceTests.resetActualPlayer(victim);
            boss=(Mob)BossBalanceTests.damageBoss(level,id);boss.setInvulnerable(false);join(level,boss);
            double attack=attackValue(boss);boss.setTarget(victim);boss.setHealth(600);
            boss.doHurtTarget(victim);
            eq(id,"native_melee_one_player_hearts",3.5,(20-victim.getHealth())/2d);
            stable(id,"native_boss_damage_one_player",boss,1000,600);
            boss.invulnerableTime=0;boss.hurtTime=0;
            // The ordinary player strike follows Player.attack(), including its real damage event.
            Field ticker=LivingEntity.class.getDeclaredField("attackStrengthTicker");ticker.setAccessible(true);ticker.setInt(victim,100);
            double beforeStrike=boss.getHealth();victim.attack(boss);
            yes(id,"native_player_strike_received",boss.getHealth()<beforeStrike);
            double wounded=boss.getHealth();UUID uuid=boss.getUUID();
            for(int i=1;i<5;i++)players.add(player(level,null,i));
            boss.setTarget(players.getLast());stable(id,"native_player_damage_five_players",boss,1000,wounded);
            boss=(Mob)reload(level,id,boss);stable(id,"attacked_boss_nbt_rejoin",boss,1000,wounded);
            yes(id,"attacked_boss_uuid_preserved",uuid.equals(boss.getUUID()));
            BossBalanceTests.resetActualPlayer(victim);boss.doHurtTarget(victim);
            eq(id,"native_melee_five_players_after_reload_hearts",3.5,(20-victim.getHealth())/2d);
            stable(id,"outgoing_hit_after_reload",boss,1000,wounded);sameAttack(id,"native_combat",boss,attack);
        }catch(Throwable error){BossBalanceTests.error("fixed_native_damage_regression",id,error);}
        finally{if(boss!=null)boss.discard();removePlayers(players);}
    }

    private static BossBalanceTests.TestPlayer player(ServerLevel level,GameProfile profile,int index)throws Exception {
        if(profile==null)profile=new GameProfile(UUID.randomUUID(),"BBFixed"+(++serial));
        var player=new BossBalanceTests.TestPlayer(level,profile);player.setGameMode(GameType.SURVIVAL);
        BossBalanceTests.resetActualPlayer(player);player.moveTo(5+index,220,4,0,0);player.setNoGravity(true);
        level.addNewPlayer(player);return player;
    }
    private static void removePlayers(List<BossBalanceTests.TestPlayer> players){for(var player:players)player.remove(Entity.RemovalReason.DISCARDED);}
    private static void join(ServerLevel level,LivingEntity entity){if(!level.addFreshEntity(entity))throw new IllegalStateException("Entity join rejected");}
    private static CompoundTag save(String id,LivingEntity entity){CompoundTag saved=entity.saveWithoutId(new CompoundTag());saved.putString("id",id);return saved;}
    private static LivingEntity load(ServerLevel level,CompoundTag saved){return (LivingEntity)EntityType.create(saved,level).orElseThrow();}
    private static LivingEntity reload(ServerLevel level,String id,LivingEntity entity){CompoundTag saved=save(id,entity);entity.discard();LivingEntity restored=load(level,saved);join(level,restored);return restored;}
    private static Double attackValue(LivingEntity entity){var value=entity.getAttribute(Attributes.ATTACK_DAMAGE);return value==null?null:value.getValue();}
    private static void sameAttack(String id,String name,LivingEntity entity,Double before){if(before!=null)eq(id,name+"_attack_attribute_unchanged",before,attackValue(entity));}
    private static void stable(String id,String name,LivingEntity entity,double maximum,double current){eq(id,name+"_max",maximum,entity.getMaxHealth());eq(id,name+"_current",current,entity.getHealth());}
    private static void nagaThreshold(String id,LivingEntity entity,double maximum)throws Exception{if(id.equals("twilightforest:naga")){Field field=entity.getClass().getDeclaredField("healthPerSegment");field.setAccessible(true);eq(id,"fixed_naga_segment_threshold",maximum/10,field.getFloat(entity));}}
    private static void eq(String id,String test,double expected,double actual){BossBalanceTests.result("fixed_"+test,id,expected,actual);}
    private static void yes(String id,String test,boolean passed){eq(id,test,1,passed?1:0);}
}

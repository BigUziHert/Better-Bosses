package survival.balance.tests;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Two separate JVM runs: save native entities without the add-on, then install and reload. */
final class LegacyInstallCases {
    private static final Path MANIFEST=Path.of("../tests/results/preinstall-manifest.json");
    private static final Path INSTALLED_MANIFEST=Path.of("../tests/results/installed-restart-manifest.json");
    private static final Map<UUID,Map<String,Object>> LOADS=new LinkedHashMap<>();
    static void install(){
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,LegacyInstallCases::beforeJoin);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,LegacyInstallCases::afterJoin);
    }
    private static void beforeJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!(event.getEntity() instanceof LivingEntity boss)||!boss.getTags().contains("bb_preinstall"))return;
        Map<String,Object> row=new LinkedHashMap<>();row.put("uuid",boss.getUUID().toString());row.put("id",BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString());
        row.put("beforeMax",boss.getMaxHealth());row.put("beforeHealth",boss.getHealth());
        row.put("beforeModifier",boss.getAttribute(Attributes.MAX_HEALTH).hasModifier(ResourceLocation.parse("survival_boss_balance:health")));
        row.put("beforeProfileVersion",boss.getPersistentData().getCompound("survival_boss_balance_health").getInt("version"));
        LOADS.put(boss.getUUID(),row);
    }
    private static void afterJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!(event.getEntity() instanceof LivingEntity boss))return;
        var row=LOADS.get(boss.getUUID());if(row==null)return;
        row.put("afterMax",boss.getMaxHealth());row.put("afterHealth",boss.getHealth());
        row.put("afterProfileVersion",boss.getPersistentData().getCompound("survival_boss_balance_health").getInt("version"));
    }
    static final Map<String,Double> TARGETS=new LinkedHashMap<>();
    static {
        String[] ids={"twilightforest:naga","twilightforest:lich","twilightforest:minoshroom","twilightforest:knight_phantom","twilightforest:alpha_yeti","twilightforest:hydra","twilightforest:ur_ghast","twilightforest:snow_queen","bosses_of_mass_destruction:lich","bosses_of_mass_destruction:void_blossom","bosses_of_mass_destruction:gauntlet","bosses_of_mass_destruction:obsidilith","cataclysm:netherite_monstrosity","cataclysm:ender_guardian","cataclysm:the_harbinger","cataclysm:ancient_remnant","cataclysm:the_leviathan","cataclysm:scylla","cataclysm:maledictus","cataclysm:ignis","minecraft:wither","minecraft:warden","minecraft:ender_dragon"};
        double[] hp={1000,650,1000,180,1100,1500,1100,1200,1300,1400,1500,1700,1200,850,950,1000,1100,1100,1100,1100,900,1300,1100};
        for(int i=0;i<ids.length;i++)TARGETS.put(ids[i],hp[i]);
    }
    static void prepare(ServerLevel level)throws Exception {
        if(net.neoforged.fml.ModList.get().isLoaded("survival_boss_balance"))throw new IllegalStateException("Preinstall fixture requires balance mod ABSENT");
        List<Map<String,Object>> rows=new ArrayList<>();int n=0;
        for(var entry:TARGETS.entrySet())for(double ratio:new double[]{1,.4}){
            LivingEntity boss=BossBalanceTests.damageBoss(level,entry.getKey());
            int x=64+(n++*32);boss.moveTo(x,220,8,0,0);level.setChunkForced(x>>4,0,true);level.getChunk(x>>4,0);
            boss.setHealth((float)(boss.getMaxHealth()*ratio));boss.addTag("bb_preinstall");
            if(!level.addFreshEntity(boss))throw new IllegalStateException("Cannot save "+entry.getKey());
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",entry.getKey());row.put("uuid",boss.getUUID().toString());row.put("x",x);row.put("nativeMax",boss.getMaxHealth());row.put("savedHealth",boss.getHealth());row.put("ratio",ratio);row.put("target",entry.getValue());rows.add(row);
            BossBalanceTests.result("preinstall_native_no_modifier",entry.getKey(),0,boss.getAttribute(Attributes.MAX_HEALTH).hasModifier(ResourceLocation.parse("survival_boss_balance:health"))?1:0);
        }
        Files.createDirectories(MANIFEST.getParent());Files.writeString(MANIFEST,new GsonBuilder().setPrettyPrinting().create().toJson(rows));
        level.getServer().saveEverything(true,true,true);
    }
    static void verify(ServerLevel level)throws Exception {
        verify(level,false);
    }
    static void verifyKeep(ServerLevel level)throws Exception {
        verify(level,true);
    }
    private static void verify(ServerLevel level,boolean keep)throws Exception {
        if(!net.neoforged.fml.ModList.get().isLoaded("survival_boss_balance"))throw new IllegalStateException("Install the balance mod first");
        List<Map<String,Object>> installed=new ArrayList<>();
        for(var element:JsonParser.parseString(Files.readString(MANIFEST)).getAsJsonArray()){
            var row=element.getAsJsonObject();String id=row.get("id").getAsString();UUID uuid=UUID.fromString(row.get("uuid").getAsString());
            int x=row.get("x").getAsInt();level.getChunk(x>>4,0);Entity entity=level.getEntity(uuid);
            if(!(entity instanceof LivingEntity boss)){BossBalanceTests.error("installed_saved_uuid",id,new IllegalStateException("Entity not loaded "+uuid));continue;}
            double target=row.get("target").getAsDouble();
            var loaded=LOADS.get(uuid);
            if(loaded==null){BossBalanceTests.error("installed_load_capture",id,new IllegalStateException("No first-load evidence"));continue;}
            BossBalanceTests.result("installed_native_max_before_join",id,row.get("nativeMax").getAsDouble(),((Number)loaded.get("beforeMax")).doubleValue());
            BossBalanceTests.result("installed_no_modifier_before_join",id,0,Boolean.TRUE.equals(loaded.get("beforeModifier"))?1:0);
            BossBalanceTests.result("installed_ratio_during_join",id,((Number)loaded.get("beforeHealth")).doubleValue()/((Number)loaded.get("beforeMax")).doubleValue(),((Number)loaded.get("afterHealth")).doubleValue()/((Number)loaded.get("afterMax")).doubleValue());
            BossBalanceTests.result("installed_saved_max",id,target,boss.getMaxHealth());
            BossBalanceTests.result("installed_saved_modifier",id,1,boss.getAttribute(Attributes.MAX_HEALTH).hasModifier(ResourceLocation.parse("survival_boss_balance:health"))?1:0);
            loaded.put("sampledHealthAfterNativeTicks",boss.getHealth());
            if(keep){
                Map<String,Object> saved=new LinkedHashMap<>();saved.put("id",id);saved.put("uuid",uuid.toString());saved.put("x",x);saved.put("target",target);saved.put("sampledHealthBeforeSecondSave",boss.getHealth());installed.add(saved);
            }else{boss.discard();level.setChunkForced(x>>4,0,false);}
        }
        Files.writeString(Path.of("../tests/results/preinstall-load-evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(LOADS.values()));
        if(keep){
            if(installed.size()!=TARGETS.size()*2)throw new IllegalStateException("Cannot prepare incomplete installed restart: "+installed.size());
            Files.writeString(INSTALLED_MANIFEST,new GsonBuilder().setPrettyPrinting().create().toJson(installed));
            level.getServer().saveEverything(true,true,true);
        }
    }
    /** A second JVM load of all 46 already-migrated UUIDs. Native idle healing
     * between the save command and shutdown may legitimately change saved HP;
     * compare health immediately across the join event to isolate rescaling. */
    static void verifyInstalled(ServerLevel level)throws Exception {
        if(!net.neoforged.fml.ModList.get().isLoaded("survival_boss_balance"))throw new IllegalStateException("Balance mod must remain installed");
        var manifest=JsonParser.parseString(Files.readString(INSTALLED_MANIFEST)).getAsJsonArray();
        if(manifest.size()!=TARGETS.size()*2)throw new IllegalStateException("Expected 46 saved installed bosses");
        for(var element:manifest){
            var row=element.getAsJsonObject();String id=row.get("id").getAsString();UUID uuid=UUID.fromString(row.get("uuid").getAsString());int x=row.get("x").getAsInt();
            level.getChunk(x>>4,0);Entity entity=level.getEntity(uuid);
            if(!(entity instanceof LivingEntity boss)){BossBalanceTests.error("installed_restart_saved_uuid",id,new IllegalStateException("Entity not loaded "+uuid));continue;}
            var loaded=LOADS.get(uuid);
            if(loaded==null){BossBalanceTests.error("installed_restart_load_capture",id,new IllegalStateException("No second-load evidence"));continue;}
            double target=row.get("target").getAsDouble();
            BossBalanceTests.result("installed_restart_max_before_join",id,target,((Number)loaded.get("beforeMax")).doubleValue());
            BossBalanceTests.result("installed_restart_modifier_before_join",id,1,Boolean.TRUE.equals(loaded.get("beforeModifier"))?1:0);
            BossBalanceTests.result("installed_restart_profile_before_join",id,2,((Number)loaded.get("beforeProfileVersion")).doubleValue());
            BossBalanceTests.result("installed_restart_health_unchanged_during_join",id,((Number)loaded.get("beforeHealth")).doubleValue(),((Number)loaded.get("afterHealth")).doubleValue());
            BossBalanceTests.result("installed_restart_max_after_join",id,target,((Number)loaded.get("afterMax")).doubleValue());
            BossBalanceTests.result("installed_restart_profile_after_join",id,2,((Number)loaded.get("afterProfileVersion")).doubleValue());
            BossBalanceTests.result("installed_restart_current_max",id,target,boss.getMaxHealth());
            loaded.put("sampledHealthAfterNativeTicks",boss.getHealth());boss.discard();level.setChunkForced(x>>4,0,false);
        }
        Files.writeString(Path.of("../tests/results/installed-restart-load-evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(LOADS.values()));
    }
}

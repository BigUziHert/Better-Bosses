package survival.balance.tests;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Isolated file and pure migration regressions. Active runtime settings are restored in finally. */
final class ConfigMigrationCases {
    private static final Map<String,Double> OLD_DEFAULTS=Map.ofEntries(
        Map.entry("naga.head",1.5),
        Map.entry("naga.body",0.45),
        Map.entry("twilight_lich.bolt",2.0),
        Map.entry("twilight_lich.bomb",3.0),
        Map.entry("twilight_lich.melee",1.5),
        Map.entry("minoshroom.axe",2.0),
        Map.entry("minoshroom.slam",3.25),
        Map.entry("knight_phantom.ordinary",1.0),
        Map.entry("knight_phantom.charge",1.625),
        Map.entry("alpha_yeti.stomp",1.5),
        Map.entry("alpha_yeti.falling_ice",3.5),
        Map.entry("alpha_yeti.ice_bomb",0.75),
        Map.entry("hydra.flame",1.375),
        Map.entry("hydra.bite",4.5),
        Map.entry("hydra.mortar",3.5),
        Map.entry("ur_ghast.fireball",2.75),
        Map.entry("ur_ghast.explosion",3.0),
        Map.entry("ur_ghast.tantrum",0.875),
        Map.entry("snow_queen.contact",3.0),
        Map.entry("snow_queen.beam",0.625),
        Map.entry("night_lich.missile",2.0),
        Map.entry("night_lich.comet",4.0),
        Map.entry("void_blossom.spikes",2.5),
        Map.entry("void_blossom.blade",2.5),
        Map.entry("void_blossom.spore",1.0),
        Map.entry("nether_gauntlet.punch",3.0),
        Map.entry("nether_gauntlet.laser",0.75),
        Map.entry("nether_gauntlet.explosion",4.5),
        Map.entry("obsidilith.spikes",3.0),
        Map.entry("obsidilith.wave",1.75),
        Map.entry("obsidilith.burst",1.75),
        Map.entry("obsidilith.anvil",5.0),
        Map.entry("netherite_monstrosity.smash",3.0),
        Map.entry("netherite_monstrosity.heavy",5.0),
        Map.entry("netherite_monstrosity.flame_jet",1.25),
        Map.entry("ender_guardian.punch",3.0),
        Map.entry("ender_guardian.uppercut",4.5),
        Map.entry("ender_guardian.rune",1.5),
        Map.entry("harbinger.charge",3.0),
        Map.entry("harbinger.missile",1.5),
        Map.entry("harbinger.laser",1.125),
        Map.entry("harbinger.explosion",3.0),
        Map.entry("ancient_remnant.bite",3.5),
        Map.entry("ancient_remnant.charge",5.0),
        Map.entry("ancient_remnant.stomp",3.0),
        Map.entry("leviathan.tail",3.25),
        Map.entry("leviathan.bite",5.5),
        Map.entry("leviathan.beam",1.0),
        Map.entry("leviathan.orb",1.5),
        Map.entry("scylla.melee",3.5),
        Map.entry("scylla.lightning",5.25),
        Map.entry("scylla.lightning_pulse",1.25),
        Map.entry("maledictus.mace_min",2.0),
        Map.entry("maledictus.mace_max",4.0),
        Map.entry("maledictus.heavy",6.0),
        Map.entry("maledictus.arrow",1.25),
        Map.entry("ignis.sword",2.25),
        Map.entry("ignis.heavy",6.0),
        Map.entry("ignis.fireball",3.0),
        Map.entry("ignis.flame_pulse",1.25),
        Map.entry("wither.skull",1.5),
        Map.entry("wither.splash",2.0),
        Map.entry("wither.spawn_explosion",5.0),
        Map.entry("ender_dragon.head",3.5),
        Map.entry("ender_dragon.wing",1.25),
        Map.entry("ender_dragon.perched_breath",1.25),
        Map.entry("ender_dragon.fireball_cloud",2.0),
        Map.entry("warden.melee",4.5),
        Map.entry("warden.sonic_boom",5.5)
    );
    static void run(Path directory)throws Exception {
        Files.createDirectories(directory);
        Class<?> api=Class.forName("survival.balance.BalanceSettings");
        Method migrate=api.getMethod("migrateLegacyDamage",JsonObject.class),load=api.getMethod("load",Path.class);
        Field active=api.getDeclaredField("settings");active.setAccessible(true);
        JsonObject saved=((JsonObject)active.get(null)).deepCopy();
        try {
            JsonObject defaults=new JsonObject(), oldDamage=new JsonObject(),health=new JsonObject();
            OLD_DEFAULTS.forEach(oldDamage::addProperty);defaults.add("damageTargets",oldDamage);
            health.addProperty("twilightforest:naga",1234);defaults.add("healthTargets",health);defaults.addProperty("debug",true);defaults.addProperty("operatorNote","keep this");
            String original=defaults.toString();
            JsonObject migrated=(JsonObject)migrate.invoke(null,defaults);
            check("pure_input_unchanged",original.equals(defaults.toString()));
            check("default_overrides_removed",!migrated.has("damageTargets"));
            eq("all_old_defaults_recognized",0,migrated.getAsJsonObject("legacyDamageTargets").size());
            eq("health_override_preserved",1234,migrated.getAsJsonObject("healthTargets").get("twilightforest:naga").getAsDouble());
            check("other_settings_preserved",migrated.get("debug").getAsBoolean()&&migrated.get("operatorNote").getAsString().equals("keep this"));
            check("migration_idempotent",migrated.equals(migrate.invoke(null,migrated)));

            JsonObject custom=defaults.deepCopy(),values=custom.getAsJsonObject("damageTargets");
            values.addProperty("naga.head",2.0);values.addProperty("twilight_lich.bomb",4.0);
            values.addProperty("maledictus.mace_min",2.5);values.addProperty("maledictus.heavy",7.0);values.addProperty("ignis.heavy",7.0);values.addProperty("extension.custom",9.0);
            JsonObject customMigrated=(JsonObject)migrate.invoke(null,custom),legacy=customMigrated.getAsJsonObject("legacyDamageTargets");
            eq("legacy_head_preserved",2,legacy.get("naga.head").getAsDouble());
            eq("legacy_rage_other_endpoint_preserved",4,legacy.get("maledictus.mace_max").getAsDouble());
            eq("legacy_anima_inherits_heavy",7,legacy.get("maledictus.anima").getAsDouble());
            eq("legacy_ultimate_inherits_heavy",7,legacy.get("ignis.ultimate").getAsDouble());
            eq("unknown_legacy_key_preserved",9,legacy.get("extension.custom").getAsDouble());
            check("unchanged_other_profile_uses_new_units",!legacy.has("naga.body"));
            loadUnchanged(directory.resolve("legacy-defaults.json"),defaults,load);
            for(var entry:BalanceExpectations.TARGETS.entrySet())eq("legacy_default_upgrade_"+entry.getKey(),entry.getValue(),number(api,entry.getKey(),entry.getValue()));
            eq("loaded_health_preserved",1234,((Number)api.getMethod("healthTarget",String.class,double.class).invoke(null,"twilightforest:naga",1000d)).doubleValue());
            loadUnchanged(directory.resolve("legacy-custom.json"),custom,load);
            eq("loaded_custom_head",2,number(api,"naga.head",4.5));
            check("loaded_custom_legacy_flag",(Boolean)api.getMethod("usesLegacyDamageReference",String.class).invoke(null,"naga.head"));
            check("loaded_unchanged_plain_flag",!(Boolean)api.getMethod("usesLegacyDamageReference",String.class).invoke(null,"naga.body"));
            calibratedLegacy("naga.head","twilightforest:naga","minecraft:mob_attack",false,false,2);
            calibratedLegacy("twilight_lich.bomb","twilightforest:lich","twilightforest:lich_bomb",true,false,4);
            calibratedLegacy("maledictus.anima","cataclysm:maledictus","cataclysm:maledictio_anima",false,true,7);

            JsonObject current=new JsonObject(),newDamage=new JsonObject();
            current.addProperty("schemaVersion",2);current.addProperty("damageReference","hard_plain_diamond");
            BalanceExpectations.TARGETS.forEach(newDamage::addProperty);current.add("damageTargets",newDamage);
            loadUnchanged(directory.resolve("current-defaults.json"),current,load);
            for(var entry:BalanceExpectations.TARGETS.entrySet())eq("current_target_"+entry.getKey(),entry.getValue(),number(api,entry.getKey(),-1));
            check("current_pure_migration_preserves_units",current.equals(migrate.invoke(null,current)));
            for(String invalid:new String[]{
                "{\"schemaVersion\":0}","{\"schemaVersion\":3}","{\"schemaVersion\":2,\"damageReference\":\"hard_netherite_protection4\"}",
                "{\"damageTargets\":{\"naga.head\":-1}}","{\"healthTargets\":{\"minecraft:warden\":0}}",
                "{\"legacyDamageTargets\":{\"naga.head\":1001}}","{\"damageTargets\":{\"naga.head\":\"NaN\"}}"
            }) {
                Path file=directory.resolve("invalid-"+Math.abs(invalid.hashCode())+".json");Files.writeString(file,invalid);
                boolean rejected=false;try{load.invoke(null,file);}catch(InvocationTargetException expected){rejected=expected.getCause() instanceof IllegalStateException;}
                check("invalid_rejected_"+Math.abs(invalid.hashCode()),rejected);
                check("invalid_file_unmodified_"+Math.abs(invalid.hashCode()),Files.readString(file).equals(invalid));
            }
            Path missing=directory.resolve("deliberately-missing-"+java.util.UUID.randomUUID()+".json");load.invoke(null,missing);
            eq("missing_file_defaults",4.5,number(api,"naga.head",4.5));check("missing_file_not_created",!Files.exists(missing));
        }finally{active.set(null,saved);}
        check("active_runtime_restored",saved.equals(active.get(null)));
    }
    private static void calibratedLegacy(String key,String boss,String type,boolean armorBypass,boolean enchantBypass,double expected)throws Exception{
        Class<?> damage=Class.forName("survival.balance.BossDamageBalance");
        Object decision=damage.getMethod("evaluate",String.class,String.class,String.class,String.class,int.class,int.class,float.class,boolean.class,boolean.class,boolean.class)
            .invoke(null,boss,type,"","",0,0,100f,armorBypass,enchantBypass,false);
        double raw=((Number)decision.getClass().getMethod("rawHardDamage").invoke(decision)).doubleValue();
        double after=armorBypass?raw:raw*(1-Math.max(4,Math.min(20,20-raw/5))/25);
        if(!enchantBypass)after*=.36;
        eq("legacy_real_source_calibration_"+key,expected,after/2);
    }
    private static void loadUnchanged(Path file,JsonObject data,Method load)throws Exception{String content=data.toString();Files.writeString(file,content);load.invoke(null,file);check("file_unmodified_"+file.getFileName(),Files.readString(file).equals(content));}
    private static double number(Class<?> api,String key,double fallback)throws Exception{return ((Number)api.getMethod("damageTarget",String.class,double.class).invoke(null,key,fallback)).doubleValue();}
    private static void check(String name,boolean value){eq(name,1,value?1:0);}
    private static void eq(String name,double expected,double actual){BossBalanceTests.result("config_"+name,"all",expected,actual);}
}

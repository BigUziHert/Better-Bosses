package survival.balance.tests;
import java.util.Map;
/** Independent approved 1.2 design fixture. Does not read production configuration to invent expectations. */
final class BalanceExpectations {
    static final Map<String,Double> TARGETS=Map.ofEntries(
        Map.entry("naga.head",4.5),
        Map.entry("naga.body",1.5),
        Map.entry("twilight_lich.bolt",3.75),
        Map.entry("twilight_lich.bomb",6.0),
        Map.entry("twilight_lich.melee",3.75),
        Map.entry("minoshroom.axe",3.5),
        Map.entry("minoshroom.slam",5.5),
        Map.entry("knight_phantom.ordinary",2.0),
        Map.entry("knight_phantom.charge",3.5),
        Map.entry("alpha_yeti.stomp",4.5),
        Map.entry("alpha_yeti.falling_ice",5.25),
        Map.entry("alpha_yeti.ice_bomb",1.5),
        Map.entry("hydra.flame",0.75),
        Map.entry("hydra.bite",7.0),
        Map.entry("hydra.mortar",5.5),
        Map.entry("ur_ghast.fireball",4.5),
        Map.entry("ur_ghast.explosion",6.0),
        Map.entry("ur_ghast.tantrum",0.75),
        Map.entry("ur_ghast.combined",8.0),
        Map.entry("snow_queen.contact",6.0),
        Map.entry("snow_queen.beam",0.75),
        Map.entry("night_lich.missile",2.5),
        Map.entry("night_lich.comet",6.0),
        Map.entry("void_blossom.spikes",4.5),
        Map.entry("void_blossom.blade",4.5),
        Map.entry("void_blossom.spore",1.125),
        Map.entry("nether_gauntlet.punch",4.5),
        Map.entry("nether_gauntlet.laser",0.5),
        Map.entry("nether_gauntlet.explosion",7.0),
        Map.entry("obsidilith.spikes",4.0),
        Map.entry("obsidilith.wave",2.0),
        Map.entry("obsidilith.burst",2.0),
        Map.entry("obsidilith.anvil",7.5),
        Map.entry("netherite_monstrosity.smash",4.5),
        Map.entry("netherite_monstrosity.heavy",7.5),
        Map.entry("netherite_monstrosity.flame_jet",0.75),
        Map.entry("ender_guardian.punch",4.0),
        Map.entry("ender_guardian.uppercut",6.5),
        Map.entry("ender_guardian.rune",1.0),
        Map.entry("harbinger.charge",6.0),
        Map.entry("harbinger.missile",2.25),
        Map.entry("harbinger.laser",0.75),
        Map.entry("harbinger.explosion",6.75),
        Map.entry("harbinger.combined",8.0),
        Map.entry("ancient_remnant.bite",4.5),
        Map.entry("ancient_remnant.charge",7.0),
        Map.entry("ancient_remnant.stomp",3.0),
        Map.entry("leviathan.tail",4.5),
        Map.entry("leviathan.bite",8.0),
        Map.entry("leviathan.beam",0.5),
        Map.entry("leviathan.orb",1.5),
        Map.entry("scylla.melee",4.5),
        Map.entry("scylla.lightning",7.5),
        Map.entry("scylla.lightning_pulse",0.75),
        Map.entry("maledictus.mace_min",3.0),
        Map.entry("maledictus.mace_max",5.0),
        Map.entry("maledictus.heavy",8.0),
        Map.entry("maledictus.arrow",1.5),
        Map.entry("ignis.sword",3.5),
        Map.entry("ignis.heavy",8.0),
        Map.entry("ignis.ultimate",9.5),
        Map.entry("ignis.fireball",4.0),
        Map.entry("ignis.flame_pulse",0.75),
        Map.entry("wither.skull",2.5),
        Map.entry("wither.splash",2.0),
        Map.entry("wither.spawn_explosion",7.0),
        Map.entry("ender_dragon.head",6.0),
        Map.entry("ender_dragon.wing",2.0),
        Map.entry("ender_dragon.perched_breath",0.75),
        Map.entry("ender_dragon.fireball_cloud",1.25),
        Map.entry("warden.melee",8.0),
        Map.entry("warden.sonic_boom",5.5),
        Map.entry("maledictus.anima",6.7950737127437675)
    );
    static double target(String key){Double value=TARGETS.get(key);if(value==null)throw new IllegalArgumentException(key);return value;}
    static double diamondHearts(double raw){double effective=Math.max(4,Math.min(20,20-raw/4));return raw*(1-effective/25)/2;}
    static double rawForDiamond(double hearts){return Math.sqrt(100+200*hearts)-10;}
    static double scaled(double raw,double baseline,double target,double cap){return Math.min(cap,diamondHearts(raw*rawForDiamond(target)/baseline));}
    static double netheriteProtected(double diamondHearts){double raw=rawForDiamond(diamondHearts);return raw*(1-Math.max(4,Math.min(20,20-raw/5))/25)*.36/2;}
    static double brandOpening(int brand){double raw=rawForDiamond(target("ignis.sword"));double multiplier=Math.max(0,1-.2*brand);double armor=(int)(20*multiplier);double effective=Math.max(armor*.2,Math.min(20,armor-raw/(2+8*multiplier/4)));return Math.min(target("ignis.heavy"),raw*(1-effective/25)/2);}
    static void defaults() throws Exception {
        @SuppressWarnings("unchecked") Map<String,Double> actual=(Map<String,Double>)Class.forName("survival.balance.BossDamageBalance").getMethod("defaultTargets").invoke(null);
        BossBalanceTests.result("approved_damage_profile_count","all",TARGETS.size(),actual.size());
        for(var entry:TARGETS.entrySet())BossBalanceTests.result("approved_default_"+entry.getKey(),"all",entry.getValue(),actual.getOrDefault(entry.getKey(),Double.NaN));
    }
}

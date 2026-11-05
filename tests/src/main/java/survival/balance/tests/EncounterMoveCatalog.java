package survival.balance.tests;

import java.util.*;

/** Installed-version state names, shared with the earlier controlled native-frame inventory. */
final class EncounterMoveCatalog {
    private EncounterMoveCatalog(){}
    record Candidate(String key,String category,boolean required,String origin){}
    static List<Candidate> additional(String boss){
        List<Candidate> result=new ArrayList<>();
        if(boss.equals("minecraft:warden")){
            for(String activity:List.of("emerge","roar","fight","investigate","sniff","idle"))result.add(new Candidate("state/wardenActivity=minecraft:"+activity,"conditional_activity",true,"Native WardenAi activity; sensing/idle branches require a suitable pre-aggro or target-loss interval, not every combat setup"));
            for(String pose:List.of("EMERGING","ROARING","SNIFFING","STANDING"))result.add(new Candidate("state/wardenPose="+pose,"conditional_pose",true,"Native Warden pose; distinct from attack-event selection"));
            for(String anger:List.of("CALM","AGITATED","ANGRY"))result.add(new Candidate("state/wardenAnger="+anger,"conditional_anger",true,"Native anger band; direct precombat anger setup may skip lower bands"));
            result.add(new Candidate("state/wardenActivity=minecraft:dig","lifecycle_despawn",false,"Persistent victory fixtures refresh DIG_COOLDOWN; native digging discards the Warden rather than defeating it"));
            result.add(new Candidate("state/wardenPose=DIGGING","lifecycle_despawn",false,"Native despawn lifecycle; separate staged lifecycle evidence is not a natural victory"));
            result.add(new Candidate("native_entity_event/warden_vibration_response","sensing_event",true,"Native event61 from Warden vibration response; requires an eligible sensed vibration"));
            result.add(new Candidate("player_effect/minecraft:darkness","status_application",true,"Native darkness must actually appear on the fixture player"));
        }
        if(boss.equals("minecraft:wither"))for(boolean dangerous:List.of(false,true))result.add(new Candidate("projectile_variant/minecraft:wither_skull/dangerous="+dangerous,"conditional_projectile_variant",true,"Native skull variant; dangerous skulls are rare on targeted central shots or selected by idle side-head updates"));
        if(boss.equals("twilightforest:minoshroom"))for(boolean charging:List.of(false,true))result.add(new Candidate("state/isGroundAttackCharge="+charging,"attack_windup",true,"Native GroundAttackGoal charge/release flag, separate from running charge axe"));
        if(boss.equals("cataclysm:maledictus"))for(int rage=0;rage<=5;rage++)result.add(new Candidate("state/getRageMeter="+rage,"conditional_damage_meter",true,"Native successful-attack rage and decay; intermediate meter observations are not separate attacks"));
        if(boss.equals("cataclysm:ancient_remnant"))for(int rage=0;rage<=5;rage++)result.add(new Candidate("state/getRage="+rage,"conditional_charge_meter",false,"Native missed attacks build charge rage; meter values are recorded, but charge-state observation remains the active move requirement"));
        if(boss.equals("cataclysm:ignis"))for(int hits=0;hits<=3;hits++)result.add(new Candidate("state/getShieldDurability="+hits,"conditional_shield_mechanic",true,"Reflected abyss-fireballs increment shield durability before final phase; final-phase auto-break can also set3, so3 alone is not reflection proof; BREAK_THE_SHIELD animation is separately inventoried"));
        for(String vehicle:grabVehicles(boss)){
            result.add(new Candidate("passenger_mount/"+vehicle,"conditional_grab",true,"Native successful grab attaches the fixture player to this boss/projectile; selected grab animation alone is insufficient"));
            result.add(new Candidate("passenger_release/"+vehicle,"conditional_release",true,"Actual sampled release after a native grab; no riding state is changed by observation"));
        }
        return result;
    }
    static List<String> grabVehicles(String boss){return switch(boss){
        case "twilightforest:alpha_yeti","cataclysm:ignis","cataclysm:maledictus" -> List.of(boss);
        case "cataclysm:the_leviathan" -> List.of(boss,"cataclysm:the_leviathan_tongue");
        case "cataclysm:scylla" -> List.of("cataclysm:scylla_ceraunus");
        default -> List.of();};}
    static String profilePrefix(String boss){return switch(boss){
        case "twilightforest:lich" -> "twilight_lich";
        case "bosses_of_mass_destruction:lich" -> "night_lich";
        case "bosses_of_mass_destruction:gauntlet" -> "nether_gauntlet";
        case "cataclysm:the_harbinger" -> "harbinger";
        case "cataclysm:the_leviathan" -> "leviathan";
        default -> boss.substring(boss.indexOf(':')+1);};}
    static List<String> booleanPhases(String boss){return switch(boss){
        case "twilightforest:alpha_yeti" -> List.of("isRampaging","isTired");
        case "twilightforest:ur_ghast" -> List.of("isInTantrum");
        case "cataclysm:ender_guardian" -> List.of("getIsHelmetless");
        case "cataclysm:netherite_monstrosity" -> List.of("getIsBerserk");
        case "cataclysm:the_harbinger" -> List.of("isPowered","getIsLaserMode");
        case "cataclysm:ancient_remnant" -> List.of("getIsPower");
        case "cataclysm:the_leviathan" -> List.of("isMeltDown","getMeltDown");
        case "cataclysm:ignis" -> List.of("getIsShieldBreak");
        default -> List.of();};}
    static Map<Integer,String> states(String boss){String[] names=switch(boss){
        case "cataclysm:netherite_monstrosity" -> "idle,sleep,awake,smash,phase,death,fire,drain,shoulder_check,overpower,flare_shot".split(",");
        case "cataclysm:ancient_remnant" -> "idle,sleep,awaken,death,right_bite,dormant_three_tail_hit,sandstorm_roar,phase_roar,right_stomp,left_stomp,charge_prepare,charge,charge_stun,right_double_stomp,left_double_stomp,ground_tail,tail_swing,monolith".split(",");
        case "cataclysm:maledictus" -> "idle,swing,shot,flying_shot,fall_loop,fall_land,death,mass_effect,flying_smash1,flying_smash2,backstep,backstep_rush,rush_without_backstep,dash1,dash1_no_backstep,dash2,dash2_no_backstep,dash3,spin_slashes,combo_first,combo_first_end,combo_second,uppercut_right,uppercut_left,flying_halberd_smash1,flying_halberd_smash2,radagon,halberd_swing,grab_start,grab_loop,grab_fail,grab_success,grab_success_loop,grab_success_end".split(",");
        case "cataclysm:scylla" -> "idle,cross_swing,cross_swing2,double_swing,swing_smash,smash,backstep,spin,wave,lightning_explosion,lightning_spear,water_spear,death,anchor_shot,anchor_pull,chain_jump1,chain_jump2,chain_jump3,anchor_explosion,parry,electric_whip,enchant1,enchant2,spawn_idle,spawn,whip_and_spear,summon_snake,grab_smash".split(",");
        default -> new String[0];};Map<Integer,String> result=new LinkedHashMap<>();for(int i=0;i<names.length;i++)result.put(i,names[i]);return result;}
    static List<String> bmdActions(String boss){return switch(boss){
        case "bosses_of_mass_destruction:lich" -> List.of("VolleyAction","VolleyRageAction","CometAction","CometRageAction","MinionAction","MinionRageAction","TeleportAction");
        case "bosses_of_mass_destruction:gauntlet" -> List.of("PunchAction","SwirlPunchAction","LaserAction","BlindnessAction");
        case "bosses_of_mass_destruction:obsidilith" -> List.of("BurstAction","WaveAction","SpikeAction","AnvilAction","PillarAction");
        case "bosses_of_mass_destruction:void_blossom" -> List.of("SpikeAction","SpikeWaveAction","BladeAction","SporeAction","BlossomAction");
        default -> List.of();};}
    static boolean lifecycle(String name){String s=name.toLowerCase(Locale.ROOT);return s.equals("idle")||s.contains("death")||s.equals("dead")||s.equals("dying")||s.equals("sleep")||s.startsWith("spawn")||s.contains("dormant")||s.equals("guardian_fallen")||s.equals("no_animation");}
}

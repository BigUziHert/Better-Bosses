package survival.balance;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Attack-specific outgoing damage. Reference targets are hearts lost on Hard
 * through armor 20, toughness 12 and Protection IV on four pieces.
 * The real damage pipeline still handles actual armor, effects and shielding.
 * This class does not change entity attack attributes or non-player victims. */
public final class BossDamageBalance {
    private BossDamageBalance() {}

    public static final Set<String> BOSS_IDS = Set.of(
        "minecraft:wither", "minecraft:ender_dragon", "minecraft:warden",
        "twilightforest:naga", "twilightforest:lich", "twilightforest:minoshroom",
        "twilightforest:hydra", "twilightforest:knight_phantom", "twilightforest:ur_ghast",
        "twilightforest:alpha_yeti", "twilightforest:snow_queen",
        "cataclysm:ender_guardian", "cataclysm:netherite_monstrosity", "cataclysm:ignis",
        "cataclysm:the_harbinger", "cataclysm:the_leviathan", "cataclysm:ancient_remnant",
        "cataclysm:maledictus", "cataclysm:scylla",
        "bosses_of_mass_destruction:lich", "bosses_of_mass_destruction:obsidilith",
        "bosses_of_mass_destruction:gauntlet", "bosses_of_mass_destruction:void_blossom");

    private static final Map<String, Double> DEFAULTS = new LinkedHashMap<>();
    static {
        defaults("wither", "skull",1.5, "splash",2.0, "spawn_explosion",5.0);
        defaults("ender_dragon", "head",3.5, "wing",1.25, "perched_breath",1.25, "fireball_cloud",2.0);
        defaults("warden", "melee",4.5, "sonic_boom",5.5);
        defaults("naga", "head",1.5, "body",0.45);
        defaults("twilight_lich", "bolt",2.0, "bomb",3.0, "melee",1.5);
        defaults("minoshroom", "axe",2.0, "slam",3.25);
        defaults("knight_phantom", "ordinary",1.0, "charge",1.625);
        defaults("alpha_yeti", "stomp",1.5, "falling_ice",3.5, "ice_bomb",0.75);
        defaults("hydra", "flame",1.375, "bite",4.5, "mortar",3.5);
        defaults("ur_ghast", "fireball",2.75, "explosion",3.0, "tantrum",0.875);
        defaults("snow_queen", "contact",3.0, "beam",0.625);
        defaults("night_lich", "missile",2.0, "comet",4.0);
        defaults("void_blossom", "spikes",2.5, "blade",2.5, "spore",1.0);
        defaults("nether_gauntlet", "punch",3.0, "laser",0.75, "explosion",4.5);
        defaults("obsidilith", "spikes",3.0, "wave",1.75, "burst",1.75, "anvil",5.0);
        defaults("netherite_monstrosity", "smash",3.0, "heavy",5.0, "flame_jet",1.25);
        defaults("ender_guardian", "punch",3.0, "uppercut",4.5, "rune",1.5);
        defaults("harbinger", "charge",3.0, "missile",1.5, "laser",1.125, "explosion",3.0);
        defaults("ancient_remnant", "bite",3.5, "charge",5.0, "stomp",3.0);
        defaults("leviathan", "tail",3.25, "bite",5.5, "beam",1.0, "orb",1.5);
        defaults("scylla", "melee",3.5, "lightning",5.25, "lightning_pulse",1.25);
        defaults("maledictus", "mace_min",2.0, "mace_max",4.0, "heavy",6.0, "arrow",1.25);
        defaults("ignis", "sword",2.25, "heavy",6.0, "fireball",3.0, "flame_pulse",1.25);
    }
    private static void defaults(String boss, Object... pairs) {
        for (int i=0;i<pairs.length;i+=2) DEFAULTS.put(boss+"."+pairs[i], ((Number)pairs[i+1]).doubleValue());
    }
    public static Map<String, Double> defaultTargets() { return new LinkedHashMap<>(DEFAULTS); }
    private static double target(String key) { return BalanceSettings.damageTarget(key, DEFAULTS.getOrDefault(key, 1.0)); }

    public record Decision(String profile, float rawHardDamage, boolean changed) {}
    private record Context(String boss, String type, String direct, String animation, int state,
                           int rage, float input, boolean armorBypass, boolean enchantBypass,
                           boolean helmet, String trace) {}
    private static final ConcurrentHashMap<String, Long> PROFILE_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> UNCLASSIFIED = new ConcurrentHashMap<>();
    public static Map<String, Long> profileCounts() { return Map.copyOf(PROFILE_COUNTS); }
    public static Map<String, String> unclassified() { return Map.copyOf(UNCLASSIFIED); }

    public static void apply(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Player victim) || victim.level().isClientSide || event.getAmount()<=0) return;
        DamageSource source=event.getSource();
        // Player-reflected attacks, player-owned weapons and PVP remain untouched.
        if (ownedByPlayer(source.getEntity(),0) || ownedByPlayer(source.getDirectEntity(),0)) return;
        Entity boss=findBoss(source);
        String type=source.typeHolder().unwrapKey().map(k->k.location().toString()).orElse("");
        String bossId=boss==null ? exclusiveBossType(type) : entityId(boss);
        if (bossId.isEmpty() && isGauntletDeathExplosion(source, type)) {
            bossId = "bosses_of_mass_destruction:gauntlet";
        }
        if (bossId.isEmpty()) return;
        // Never turn ambient status/environmental damage into a boss strike.
        if (type.equals("minecraft:thorns") || type.equals("minecraft:fall") || type.equals("minecraft:on_fire")
                || type.equals("minecraft:in_fire") || type.equals("minecraft:lava")
                || type.equals("minecraft:wither") || type.equals("neoforge:poison")
                || type.equals("cataclysm:abyssal_burn")) return;
        double scale=difficultyRatio(source, victim.level().getDifficulty(), event.getAmount());
        float hardAmount=(float)(event.getAmount()/scale);
        String animation=animationName(boss);
        if (bossId.equals("minecraft:ender_dragon") && source.getDirectEntity() instanceof AreaEffectCloud cloud) {
            animation += dragonCloudKind(cloud);
        }
        if (boolMethod(boss,"isChargingAtPlayer")) animation += " CHARGING";
        int state=intMethod(boss,"getAttackState",-1);
        if (bossId.equals("bosses_of_mass_destruction:obsidilith")) state=intField(boss,"currentAttack",state);
        int rage=intMethod(boss,"getRageMeter",0);
        Decision decision=evaluate(bossId,type,entityId(source.getDirectEntity()),animation,state,rage,hardAmount,
            source.is(DamageTypeTags.BYPASSES_ARMOR),source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS),
            source.is(DamageTypeTags.DAMAGES_HELMET),attackTrace());
        if (!decision.changed()) {
            UNCLASSIFIED.putIfAbsent(bossId+"|"+type+"|"+entityId(source.getDirectEntity()),decision.profile());
            return;
        }
        float raw=decision.rawHardDamage();
        // Brand is applied by Ignis after hurt(), so this reads the stacks that affect this hit.
        // Cap only the reference-geared target, never universally cap an unarmored player's HP loss.
        if (bossId.equals("cataclysm:ignis")) {
            int brand=brandLevel(victim);
            if (brand>0) {
                double armorMultiplier=Math.max(0,1-0.2*brand);
                // Vanilla floors the resulting armor attribute before computing damage reduction.
                int referenceArmor=(int)(20*armorMultiplier);
                raw=(float)Math.min(raw,rawForHearts(target("ignis.heavy"),
                    source.is(DamageTypeTags.BYPASSES_ARMOR),source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS),
                    source.is(DamageTypeTags.DAMAGES_HELMET),referenceArmor,12*armorMultiplier));
            }
        }
        event.setAmount((float)Math.max(0,fromHard(source,victim.level().getDifficulty(),raw)));
        PROFILE_COUNTS.merge(decision.profile(),1L,Long::sum);
    }

    /** Public deterministic surface used by test tooling; input amount is raw HP on Hard. */
    public static Decision evaluate(String bossId,String damageType,String directType,String animation,
            int state,int rage,float rawHard,boolean bypassArmor,boolean bypassEnchantments,boolean helmetDamage) {
        return evaluate(bossId,damageType,directType,animation,state,rage,rawHard,bypassArmor,bypassEnchantments,helmetDamage,"");
    }
    public static Decision evaluate(String bossId,String damageType,String directType,String animation,
            int state,int rage,float rawHard,boolean bypassArmor,boolean bypassEnchantments,boolean helmetDamage,String trace) {
        Context c=new Context(bossId,damageType,directType,animation.toLowerCase(Locale.ROOT),state,rage,rawHard,
            bypassArmor,bypassEnchantments,helmetDamage,trace.toLowerCase(Locale.ROOT));
        if (rawHard<=0) return unchanged(c,"zero");
        boolean explosion=c.type.contains("explosion") || c.type.equals("twilightforest:lich_bomb");
        switch (bossId) {
            case "minecraft:wither":
                if (explosion) return cap(c,c.direct.equals("minecraft:wither") ? "wither.spawn_explosion":"wither.splash");
                if (c.type.equals("minecraft:wither_skull") || c.direct.equals("minecraft:wither_skull")) return set(c,"wither.skull");
                return unchanged(c,"unmatched wither attack");
            case "minecraft:ender_dragon":
                if (c.direct.equals("minecraft:area_effect_cloud") && c.type.equals("minecraft:indirect_magic")) {
                    if (c.animation.contains("dragon_fireball_cloud")) return set(c,"ender_dragon.fireball_cloud");
                    if (c.animation.contains("dragon_perched_breath")) return set(c,"ender_dragon.perched_breath");
                    return unchanged(c,"unrecognized dragon cloud effect");
                }
                if (melee(c)) {
                    if (c.trace.contains("enderdragon.knockback(")) return set(c,"ender_dragon.wing");
                    if (c.trace.contains("enderdragon.hurt(")) return set(c,"ender_dragon.head");
                    return set(c,rawHard <= 7.5F ? "ender_dragon.wing":"ender_dragon.head");
                }
                return unchanged(c,"unmatched dragon attack");
            case "minecraft:warden":
                if (c.type.equals("minecraft:sonic_boom")) return set(c,"warden.sonic_boom");
                if (melee(c)) return set(c,"warden.melee");
                return unchanged(c,"unmatched warden attack");
            case "twilightforest:naga":
                if(c.trace.contains("nagasegment")) return set(c,"naga.body");
                if(c.trace.contains("naga.dohurttarget")) return set(c,"naga.head");
                return set(c,rawHard<=3.05 ? "naga.body":"naga.head");
            case "twilightforest:lich":
                if (c.type.equals("twilightforest:lich_bolt")) return set(c,"twilight_lich.bolt");
                if (explosion) return cap(c,"twilight_lich.bomb");
                if (melee(c)) return set(c,"twilight_lich.melee");
                return unchanged(c,"unmatched lich attack");
            case "twilightforest:minoshroom":
                return set(c,c.type.equals("twilightforest:slam") ? "minoshroom.slam":"minoshroom.axe");
            case "twilightforest:knight_phantom":
                return set(c,c.animation.contains("charging") || (melee(c)&&rawHard>=17.9) ? "knight_phantom.charge":"knight_phantom.ordinary");
            case "twilightforest:alpha_yeti":
                if (c.type.equals("twilightforest:falling_ice")) return cap(c,"alpha_yeti.falling_ice");
                if (c.type.equals("twilightforest:frozen") || c.direct.contains("ice_bomb")) return cap(c,"alpha_yeti.ice_bomb");
                if (melee(c)&&rawHard>=6) return set(c,"alpha_yeti.stomp");
                return unchanged(c,"yeti ordinary melee unchanged");
            case "twilightforest:hydra":
                if (c.type.equals("twilightforest:hydra_bite")) return set(c,"hydra.bite");
                if (c.type.equals("twilightforest:hydra_fire")) return set(c,"hydra.flame");
                if (c.type.equals("twilightforest:hydra_mortar") || explosion) return cap(c,"hydra.mortar");
                return unchanged(c,"unmatched hydra attack");
            case "twilightforest:ur_ghast":
                if (c.type.equals("twilightforest:ghast_tear")) return set(c,"ur_ghast.tantrum");
                if (explosion) return cap(c,"ur_ghast.explosion");
                if (c.type.equals("minecraft:fireball") || c.direct.contains("fireball")) return set(c,"ur_ghast.fireball");
                return unchanged(c,"unmatched ur-ghast attack");
            case "twilightforest:snow_queen":
                return set(c,c.type.equals("twilightforest:chilling_breath") ? "snow_queen.beam":"snow_queen.contact");
            case "bosses_of_mass_destruction:lich":
                if (explosion) return cap(c,"night_lich.comet");
                if (c.type.equals("minecraft:thrown") || c.direct.contains("magic_missile")) return set(c,"night_lich.missile");
                return unchanged(c,"unmatched night lich attack");
            case "bosses_of_mass_destruction:obsidilith":
                if (explosion) return cap(c,"obsidilith.anvil");
                if (c.trace.contains("waveaction")) return set(c,"obsidilith.wave");
                if (c.trace.contains("burstaction")) return set(c,"obsidilith.burst");
                if (c.trace.contains("spikeaction")) return set(c,"obsidilith.spikes");
                if (state==6) return set(c,"obsidilith.wave");
                if (state==5) return set(c,"obsidilith.burst");
                if (c.type.equals("bosses_of_mass_destruction:shield_piercing")) return set(c,"obsidilith.spikes");
                return unchanged(c,"unmatched obsidilith attack");
            case "bosses_of_mass_destruction:gauntlet":
                if (explosion) return cap(c,"nether_gauntlet.explosion");
                if (c.trace.contains("laseraction")) return set(c,"nether_gauntlet.laser");
                if (c.trace.contains("punchaction")) return set(c,"nether_gauntlet.punch");
                if (rawHard<20) return set(c,"nether_gauntlet.laser");
                return set(c,"nether_gauntlet.punch");
            case "bosses_of_mass_destruction:void_blossom":
                if (c.direct.contains("spore") || c.trace.contains("sporeball")) return set(c,"void_blossom.spore");
                if (c.direct.contains("petal") || c.trace.contains("petalblade")) return set(c,"void_blossom.blade");
                if (c.type.equals("bosses_of_mass_destruction:shield_piercing")) return set(c,"void_blossom.spikes");
                return unchanged(c,"unmatched blossom attack");
            case "cataclysm:netherite_monstrosity":
                if (c.direct.contains("flame_jet")) return set(c,"netherite_monstrosity.flame_jet");
                if (explosion || c.direct.contains("bomb")) return cap(c,"netherite_monstrosity.heavy");
                if (melee(c)) return scaleCap(c,"netherite_monstrosity.smash",39.9,"netherite_monstrosity.heavy");
                return cap(c,"netherite_monstrosity.heavy");
            case "cataclysm:ender_guardian":
                if (c.direct.contains("rune") || c.direct.contains("bullet")) return set(c,"ender_guardian.rune");
                if (melee(c) && (c.animation.contains("uppercut") || c.animation.contains("rocketpunch"))) return set(c,"ender_guardian.uppercut");
                if (melee(c)) return scaleCap(c,"ender_guardian.punch",25.5,"ender_guardian.uppercut");
                return cap(c,"ender_guardian.uppercut");
            case "cataclysm:the_harbinger":
                if (c.type.equals("cataclysm:deathlaser") || c.direct.contains("laser")) return set(c,"harbinger.laser");
                if (explosion) return cap(c,"harbinger.explosion");
                if (c.direct.contains("missile")) return set(c,"harbinger.missile");
                if (melee(c)) return set(c,"harbinger.charge");
                return cap(c,"harbinger.explosion");
            case "cataclysm:ancient_remnant":
                if (c.trace.contains("stompdamage") || c.direct.contains("earthquake")) return set(c,"ancient_remnant.stomp");
                if (melee(c) && (c.trace.contains("_entity.charge(") || c.animation.contains("charge") || state==11 || rawHard>=75)) return set(c,"ancient_remnant.charge");
                if (melee(c)) return scaleCap(c,"ancient_remnant.bite",52.125,"ancient_remnant.charge");
                return cap(c,"ancient_remnant.charge");
            case "cataclysm:the_leviathan":
                if (c.type.equals("cataclysm:deathlaser") || c.direct.contains("blast")) return set(c,"leviathan.beam");
                if (c.direct.contains("orb")) return set(c,"leviathan.orb");
                if (melee(c) && (c.trace.contains("biteattack") || c.animation.contains("bite"))) return set(c,"leviathan.bite");
                if (melee(c)) return scaleCap(c,"leviathan.tail",24.3,"leviathan.bite");
                return cap(c,"leviathan.bite");
            case "cataclysm:scylla":
                if (c.type.equals("cataclysm:lightning")) return set(c,rawHard<=8 ? "scylla.lightning_pulse":"scylla.lightning");
                if (melee(c)) return scaleCap(c,"scylla.melee",28.5,"scylla.lightning");
                return cap(c,"scylla.lightning");
            case "cataclysm:maledictus":
                if (c.direct.contains("arrow") || c.type.equals("cataclysm:maledictio_sagitta")) return set(c,"maledictus.arrow");
                if (c.type.equals("cataclysm:maledictio_anima")) return cap(c,"maledictus.heavy");
                double r=Math.max(0,Math.min(5,rage));
                double desired=target("maledictus.mace_min")+(target("maledictus.mace_max")-target("maledictus.mace_min"))*r/5;
                double ordinaryRaw=(13*(1+0.2*r)+1.6)*1.5;
                return ratioCap(c,"maledictus.mace_rage",desired,ordinaryRaw,"maledictus.heavy");
            case "cataclysm:ignis":
                if (c.type.equals("cataclysm:flame_strike")) return cap(c,"ignis.flame_pulse");
                if (explosion || c.direct.contains("fireball")) return cap(c,"ignis.fireball");
                if (melee(c)) return scaleCap(c,"ignis.sword",22.5,"ignis.heavy");
                return cap(c,"ignis.heavy");
            default: return unchanged(c,"outside boss scope");
        }
    }

    private static boolean melee(Context c) { return c.type.equals("minecraft:mob_attack") || c.type.equals("twilightforest:haunt"); }
    private static Decision unchanged(Context c,String reason) { return new Decision(reason,c.input,false); }
    private static Decision set(Context c,String key) { return decision(key,raw(c,target(key))); }
    private static Decision cap(Context c,String key) { return decision(key+".cap",Math.min(c.input,raw(c,target(key)))); }
    private static Decision scaleCap(Context c,String key,double baseline,String capKey) { return ratioCap(c,key,target(key),baseline,capKey); }
    private static Decision ratioCap(Context c,String key,double hearts,double baseline,String capKey) {
        return decision(key,Math.min(c.input*raw(c,hearts)/baseline,raw(c,target(capKey))));
    }
    private static Decision decision(String key,double raw) { return new Decision(key,(float)Math.max(0,raw),true); }
    private static double raw(Context c,double hearts) { return rawForHearts(hearts,c.armorBypass,c.enchantBypass,c.helmet,20,12); }

    public static double rawForHearts(double hearts,boolean armorBypass,boolean enchantBypass,boolean helmet) {
        return rawForHearts(hearts,armorBypass,enchantBypass,helmet,20,12);
    }
    public static double rawForHearts(double hearts,boolean armorBypass,boolean enchantBypass,boolean helmet,double armor,double toughness) {
        if (hearts<=0) return 0;
        double lo=0,hi=Math.max(100,hearts*100);
        for(int i=0;i<80;i++) {
            double mid=(lo+hi)/2;
            if(referenceHearts(mid,armorBypass,enchantBypass,helmet,armor,toughness)<hearts) lo=mid; else hi=mid;
        }
        return (lo+hi)/2;
    }
    public static double referenceHearts(double raw,boolean armorBypass,boolean enchantBypass,boolean helmet,double armor,double toughness) {
        double d=raw*(helmet ? .75:1);
        if(!armorBypass) {
            double effective=Math.max(armor*.2,Math.min(20,armor-d/(2+toughness/4)));
            d*=1-effective/25;
        }
        if(!enchantBypass) d*=.36;
        return d/2;
    }

    private static String exclusiveBossType(String type) {
        return switch(type) {
            case "twilightforest:falling_ice" -> "twilightforest:alpha_yeti";
            case "twilightforest:thrown_axe", "twilightforest:thrown_pickaxe" -> "twilightforest:knight_phantom";
            default -> "";
        };
    }

    private static boolean isGauntletDeathExplosion(DamageSource source, String type) {
        if (!type.equals("minecraft:explosion") || source.getEntity() != null || source.getDirectEntity() != null) {
            return false;
        }
        // The installed Gauntlet's death tick creates a blast without assigning an owner.
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
            frame.getClassName().equals("com.cerbon.bosses_of_mass_destruction.entity.custom.gauntlet.ServerGauntletDeathHandler")
                && frame.getMethodName().equals("tick")));
    }
    private static Entity findBoss(DamageSource source) {
        Entity sourceEntity=source.getEntity();
        if(sourceEntity instanceof Player) return null;
        Entity found=walkOwner(sourceEntity,0);
        return found!=null ? found:walkOwner(source.getDirectEntity(),0);
    }
    private static Entity walkOwner(Entity entity,int depth) {
        if(entity==null || depth>4 || entity instanceof Player) return null;
        if(BOSS_IDS.contains(entityId(entity))) return entity;
        Object owner=entity instanceof Projectile projectile ? projectile.getOwner():invoke(entity,"getOwner");
        if(!(owner instanceof Entity)) owner=invoke(entity,"getCaster");
        if(!(owner instanceof Entity)) owner=invoke(entity,"getShooter");
        return owner instanceof Entity e && e!=entity ? walkOwner(e,depth+1):null;
    }
    private static boolean ownedByPlayer(Entity entity,int depth) {
        if(entity==null || depth>4) return false;
        if(entity instanceof Player) return true;
        Object owner=entity instanceof Projectile p ? p.getOwner():invoke(entity,"getOwner");
        if(!(owner instanceof Entity)) owner=invoke(entity,"getCaster");
        if(!(owner instanceof Entity)) owner=invoke(entity,"getShooter");
        return owner instanceof Entity e && e!=entity && ownedByPlayer(e,depth+1);
    }
    private static String entityId(Entity entity) { return entity==null ? "":BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(); }
    private static double difficultyRatio(DamageSource source,Difficulty difficulty,float amount) {
        if(!source.scalesWithDifficulty()) return 1;
        return switch(difficulty) {
            case HARD -> 1;
            case NORMAL -> 2.0/3;
            // Easy uses min(normal/2+1,normal); invert before reapplying a new amount below.
            case EASY -> amount<=2 ? 2.0/3 : amount/(3*(amount-1));
            default -> 1;
        };
    }
    private static double fromHard(DamageSource source,Difficulty difficulty,double hardDamage) {
        if(!source.scalesWithDifficulty()) return hardDamage;
        return switch(difficulty) {
            case HARD -> hardDamage;
            case NORMAL -> hardDamage/1.5;
            case EASY -> Math.min(hardDamage/3+1,hardDamage/1.5);
            default -> 0;
        };
    }
    private static int brandLevel(LivingEntity victim) {
        for(MobEffectInstance effect:victim.getActiveEffects()) {
            String id=effect.getEffect().unwrapKey().map(k->k.location().toString()).orElse("");
            if(id.equals("cataclysm:blazing_brand")) return Math.min(5,effect.getAmplifier()+1);
        }
        return 0;
    }
    private static final Map<String,Method> METHODS=new ConcurrentHashMap<>();
    private static final Set<String> MISSING_METHODS=ConcurrentHashMap.newKeySet();
    private static Object invoke(Object object,String name) {
        if(object==null) return null;
        String key=object.getClass().getName()+"#"+name;
        if(MISSING_METHODS.contains(key)) return null;
        try {
            Method method=METHODS.get(key);
            if(method==null) { method=object.getClass().getMethod(name); METHODS.put(key,method); }
            return method.invoke(object);
        } catch(ReflectiveOperationException|RuntimeException ignored) { MISSING_METHODS.add(key); return null; }
    }
    private static int intMethod(Object o,String name,int fallback) { Object v=invoke(o,name);return v instanceof Number n ? n.intValue():fallback; }
    private static boolean boolMethod(Object o,String name) { return Boolean.TRUE.equals(invoke(o,name)); }
    private static int intField(Object o,String name,int fallback) {
        if(o==null) return fallback;
        try { return ((Number)o.getClass().getField(name).get(o)).intValue(); } catch(ReflectiveOperationException|RuntimeException ignored) {return fallback;}
    }
    private static final Map<Class<?>,Field[]> ANIMATION_FIELDS=new ConcurrentHashMap<>();
    private static volatile Field cloudPotionContents;
    /** Read the cloud's saved effect, not the dragon's current phase: a fireball
     * cloud can still be active after the dragon lands. */
    private static String dragonCloudKind(AreaEffectCloud cloud) {
        try {
            Field field = cloudPotionContents;
            if (field == null) {
                field = AreaEffectCloud.class.getDeclaredField("potionContents");
                field.setAccessible(true);
                cloudPotionContents = field;
            }
            for (MobEffectInstance effect : ((PotionContents)field.get(cloud)).getAllEffects()) {
                if (effect.is(MobEffects.HARM)) {
                    if (effect.getAmplifier() == 0) return " dragon_perched_breath";
                    if (effect.getAmplifier() == 1) return " dragon_fireball_cloud";
                }
            }
            return "";
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot identify native dragon breath effects", error);
        }
    }
    private static String animationName(Entity boss) {
        Object active=invoke(boss,"getAnimation");
        if(active==null || boss==null) return "";
        for(Field f:ANIMATION_FIELDS.computeIfAbsent(boss.getClass(),type->java.util.Arrays.stream(type.getFields())
                .filter(x->Modifier.isStatic(x.getModifiers()) && x.getType().getSimpleName().equals("Animation")).toArray(Field[]::new))) {
            try { if(f.get(null)==active) return f.getName(); } catch(IllegalAccessException ignored) {}
        }
        return "";
    }
    private static String attackTrace() {
        return StackWalker.getInstance().walk(s->s.filter(f->{String n=f.getClassName();return n.startsWith("twilightforest.")
            || n.startsWith("com.github.L_Ender.cataclysm.") || n.startsWith("com.cerbon.bosses_of_mass_destruction.")
            || n.equals("net.minecraft.world.entity.boss.enderdragon.EnderDragon");})
            .limit(12).map(f->f.getClassName()+"."+f.getMethodName()+"(").collect(Collectors.joining(";")));
    }
}

package survival.balance.tests;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Independent staged NI-P4 single hits; never run alongside a continuous encounter. */
final class IgnisBrandNetheriteCases {
    private static final String BOSS="cataclysm:ignis";
    private static final List<EquipmentSlot> SLOTS=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
    private static boolean installed;
    private static BossBalanceTests.TestPlayer measuring;
    private static final List<Map<String,Object>> incoming=new ArrayList<>(),posts=new ArrayList<>();

    static void run(ServerLevel level,Path destination) throws Exception {
        if(!installed){
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,IgnisBrandNetheriteCases::incomingFirst);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,IgnisBrandNetheriteCases::incomingLast);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,IgnisBrandNetheriteCases::post);
            installed=true;
        }
        List<Map<String,Object>> rows=new ArrayList<>();
        var player=BossBalanceTests.damagePlayer(level);
        try {
            for(boolean apple:new boolean[]{false,true})for(int brand=0;brand<=5;brand++)for(String attack:List.of("opening","heavy","ultimate"))
                probe(level,player,attack,brand,apple,rows);
        } finally {measuring=null;player.discard();}
        Map<String,Object> output=new LinkedHashMap<>();
        output.put("suite","ignis_brand_netherite");output.put("rows",rows);
        output.put("scope","36 independent native single-hit probes: intact full Protection IV netherite, 20 maximum HP, Brand0-V, opening/shield-smash melee component/ultimate. Fresh and native enchanted-golden-apple effect snapshots are separate. Apple effects come from ItemStack.finishUsingItem; the snapshot does not simulate the 32-tick eating interval. No player/world ticks occur between applying pre-hit effects and the native hit, so regeneration/fire damage are excluded. Each probe resets its own fixture only before combat. Native armor/toughness modifiers and post-hit Brand application, where the native recipe applies it, remain active. Shield-smash tests its exact frame34 AreaAttack component; the separate wave from that AI frame is excluded, not counted as covered. This is staged native-method evidence, not continuous-fight victory evidence.");
        output.put("expectations","Approved independent BalanceExpectations targets plus the vanilla armor, Protection IV and Resistance I equations. Production evaluation/raw conversion APIs are not used to manufacture expected values. Reference caps are converted with branded diamond defenses, then actual netherite defenses and native effects are applied.");
        Files.createDirectories(destination.getParent());Files.writeString(destination,new GsonBuilder().setPrettyPrinting().create().toJson(output)+"\n");
    }

    private static void probe(ServerLevel level,BossBalanceTests.TestPlayer player,String attack,int brand,boolean apple,List<Map<String,Object>> rows) {
        String label=(apple?"apple":"fresh")+"_brand"+brand+"_"+attack;
        LivingEntity boss=null;Set<Entity> prior=new HashSet<>();for(Entity e:level.getAllEntities())prior.add(e);
        Map<String,Object> row=new LinkedHashMap<>();row.put("case",label);row.put("boss",BOSS);row.put("armor","minecraft:netherite");row.put("protection",4);row.put("preHitBrand",brand);row.put("condition",apple?"native_enchanted_apple_effects":"fresh");row.put("attack",attack);
        row.put("nativeRecipe",switch(attack){case "opening"->"SWING_ATTACK AreaAttack(6.5,6,70,1,.05,80,2,200,5,false,0)";case "heavy"->"SHIELD_SMASH_ATTACK frame34 AreaAttack(4.85,2.5,45,1.5,.15,200,0,0,5,false,0); melee component only, separate wave excluded";default->"ULTIMATE_ATTACK frame74 through native aiStep";});
        try {
            BossBalanceTests.resetActualPlayer(player);
            var protection=player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
            var items=List.of(Items.NETHERITE_HELMET,Items.NETHERITE_CHESTPLATE,Items.NETHERITE_LEGGINGS,Items.NETHERITE_BOOTS);
            for(int i=0;i<SLOTS.size();i++){ItemStack stack=new ItemStack(items.get(i));stack.enchant(protection,4);player.setItemSlot(SLOTS.get(i),stack);}
            BossBalanceTests.updateEquipment(player);
            if(apple)new ItemStack(Items.ENCHANTED_GOLDEN_APPLE).finishUsingItem(level,player);
            if(brand>0)player.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("cataclysm:blazing_brand")).orElseThrow(),200,brand-1));
            player.moveTo(8,220,10,180,0);player.setDeltaMovement(0,0,0);
            boss=BossBalanceTests.damageBoss(level,BOSS);boss.moveTo(8,220,8,0,0);boss.yBodyRot=0;boss.yHeadRot=0;boss.tickCount=40;((Mob)boss).setTarget(player);
            double armor=player.getAttributeValue(Attributes.ARMOR),toughness=player.getAttributeValue(Attributes.ARMOR_TOUGHNESS),hp=player.getHealth(),absorption=player.getAbsorptionAmount();
            int effectiveArmor=player.getArmorValue();double multiplier=Math.max(0,1-.2*brand);
            double raw=expectedRaw(attack,brand),expectedTotal=afterArmor(raw,effectiveArmor,toughness)*.36*(apple?.8:1);
            double expectedAbsorbed=Math.min(absorption,expectedTotal),expectedHp=expectedTotal-expectedAbsorbed;
            row.put("maximumHealth",player.getMaxHealth());row.put("hpBefore",hp);row.put("absorptionBefore",absorption);row.put("armorBefore",armor);row.put("effectiveIntegerArmor",effectiveArmor);row.put("toughnessBefore",toughness);row.put("effectsBefore",effects(player));
            row.put("expectedProcessedRawHp",raw);row.put("expectedDamageBeforeAbsorptionHp",expectedTotal);row.put("expectedAbsorptionSpentHp",expectedAbsorbed);row.put("expectedHealthDamageHp",expectedHp);
            check(label,"maximum_hp",20,player.getMaxHealth());check(label,"initial_hp",20,hp);check(label,"armor",20*multiplier,armor);check(label,"toughness",12*multiplier,toughness);check(label,"initial_absorption",apple?16:0,absorption);
            check(label,"protection_levels",16,SLOTS.stream().mapToInt(slot->player.getItemBySlot(slot).getEnchantments().getLevel(protection)).sum());
            check(label,"resistance_amplifier",apple?0:-1,player.hasEffect(MobEffects.DAMAGE_RESISTANCE)?player.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier():-1);
            incoming.clear();posts.clear();measuring=player;
            ActualCataclysmAttacks.ignisProbe(boss,attack);
            measuring=null;
            row.put("incoming",List.copyOf(incoming));row.put("nativePostEvents",List.copyOf(posts));row.put("hpAfter",player.getHealth());row.put("absorptionAfter",player.getAbsorptionAmount());row.put("effectsAfter",effects(player));
            row.put("healthDamageHp",hp-player.getHealth());row.put("absorptionSpentHp",absorption-player.getAbsorptionAmount());row.put("itemDamageAfter",SLOTS.stream().map(slot->player.getItemBySlot(slot).getDamageValue()).toList());
            check(label,"native_post_count",1,posts.size());check(label,"incoming_stage_count",2,incoming.size());
            check(label,"processed_raw",raw,incoming.stream().filter(r->r.get("stage").equals("after_incoming_handlers")).mapToDouble(r->((Number)r.get("amountHp")).doubleValue()).sum());
            check(label,"health_damage",expectedHp,hp-player.getHealth());check(label,"absorption_spent",expectedAbsorbed,absorption-player.getAbsorptionAmount());
            check(label,"accepted_damage",expectedTotal,posts.stream().mapToDouble(r->((Number)r.get("damageHp")).doubleValue()+((Number)r.get("absorbedHp")).doubleValue()).sum());
            check(label,"native_ignis_caller",1,posts.stream().anyMatch(r->r.get("nativeCallers").toString().contains("Ignis_Entity.AreaAttack"))?1:0);
            row.put("survived",player.getHealth()>0);
        }catch(Throwable error){measuring=null;Throwable cause=error instanceof java.lang.reflect.InvocationTargetException&&error.getCause()!=null?error.getCause():error;row.put("error",cause.toString());BossBalanceTests.error("ignis_netherite_"+label,BOSS,cause);}
        finally {measuring=null;List<Entity> cleanup=new ArrayList<>();for(Entity e:level.getAllEntities())if(e!=player&&!prior.contains(e))cleanup.add(e);for(Entity e:cleanup)e.discard();if(boss!=null)boss.discard();player.stopRiding();player.clearFire();rows.add(row);}
    }

    private static double expectedRaw(String attack,int brand){
        double sword=invert(2*BalanceExpectations.target("ignis.sword"),20,8);
        double initial=attack.equals("opening")?sword:Math.min((attack.equals("ultimate")?49.5:36)*sword/22.5,invert(2*BalanceExpectations.target(attack.equals("ultimate")?"ignis.ultimate":"ignis.heavy"),20,8));
        double m=Math.max(0,1-.2*brand),cap=BalanceExpectations.target(attack.equals("ultimate")?"ignis.ultimate":"ignis.heavy");
        return Math.min(initial,invert(2*cap,(int)(20*m),8*m));
    }
    private static double invert(double hp,double armor,double toughness){double lo=0,hi=1000;for(int i=0;i<90;i++){double mid=(lo+hi)/2;if(afterArmor(mid,armor,toughness)<hp)lo=mid;else hi=mid;}return(lo+hi)/2;}
    private static double afterArmor(double raw,double armor,double toughness){double reduced=Math.min(20,Math.max(armor/5,armor-raw/(2+toughness/4)));return raw*(1-reduced/25);}
    private static void check(String label,String part,double expected,double actual){BossBalanceTests.result("ignis_netherite_"+label+"_"+part,BOSS,expected,actual);}
    private static List<Map<String,Object>> effects(LivingEntity e){return e.getActiveEffects().stream().map(effect->{Map<String,Object> result=new LinkedHashMap<>();result.put("id",BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString());result.put("amplifier",effect.getAmplifier());result.put("duration",effect.getDuration());return result;}).toList();}
    private static void incomingFirst(LivingIncomingDamageEvent e){captureIncoming(e,"native_incoming_before_handlers");}
    private static void incomingLast(LivingIncomingDamageEvent e){captureIncoming(e,"after_incoming_handlers");}
    private static void captureIncoming(LivingIncomingDamageEvent e,String stage){if(measuring==null||e.getEntity()!=measuring)return;incoming.add(Map.of("stage",stage,"amountHp",e.getAmount(),"cancelled",e.isCanceled(),"damageType",e.getSource().typeHolder().unwrapKey().map(k->k.location().toString()).orElse("")));}
    private static void post(LivingDamageEvent.Post e){if(measuring==null||e.getEntity()!=measuring)return;Map<String,Object> r=new LinkedHashMap<>();r.put("damageHp",e.getNewDamage());r.put("absorbedHp",e.getReduction(DamageContainer.Reduction.ABSORPTION));r.put("originalNativeHp",e.getOriginalDamage());r.put("hpAfter",measuring.getHealth());r.put("absorptionAfter",measuring.getAbsorptionAmount());r.put("blockedHp",e.getBlockedDamage());r.put("damageType",e.getSource().typeHolder().unwrapKey().map(k->k.location().toString()).orElse(""));Map<String,Float> reductions=new LinkedHashMap<>();for(var reduction:DamageContainer.Reduction.values())reductions.put(reduction.name(),e.getReduction(reduction));r.put("reductionsHp",reductions);r.put("nativeCallers",StackWalker.getInstance().walk(frames->frames.filter(f->f.getClassName().startsWith("com.github.L_Ender.cataclysm.")).limit(8).map(f->f.getClassName()+"."+f.getMethodName()).toList()));posts.add(r);}
}

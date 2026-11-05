package survival.balance.tests;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;

/** Runtime measurements with real equipment. Never substitutes a calculated armor formula. */
final class ArmorMatrixCases {
    private static final List<EquipmentSlot> SLOTS=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
    private record Outfit(String id,Map<EquipmentSlot,Item> pieces) {}
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static final List<Map<String,Object>> inventory=new ArrayList<>();
    private static int failures;

    static void run(ServerLevel level,Path destination) throws Exception {
        rows.clear(); inventory.clear(); failures=0;
        BossBalanceTests.TestPlayer player=BossBalanceTests.damagePlayer(level);
        List<Outfit> outfits=discover();
        for(Outfit outfit:outfits) {
            for(int protection:enchantmentLevels(player,outfit)) {
                for(DamageCases.Case test:cases()) fixture(level,player,outfit,protection,test);
                nativeMelee(level,player,outfit,protection,"twilightforest:naga","native_naga_head");
                nativeMelee(level,player,outfit,protection,"twilightforest:minoshroom","native_minoshroom_axe");
                nativeMelee(level,player,outfit,protection,"bosses_of_mass_destruction:gauntlet","native_gauntlet_punch");
                nativeProjectile(level,player,outfit,protection,"twilightforest:lich","twilightforest:lich_bolt","native_lich_bolt");
                nativeProjectile(level,player,outfit,protection,"bosses_of_mass_destruction:lich","bosses_of_mass_destruction:blue_fireball","native_nightlich_missile");
                nativeProjectile(level,player,outfit,protection,"bosses_of_mass_destruction:void_blossom","bosses_of_mass_destruction:petal_blade","native_blossom_blade");
                nativeProjectile(level,player,outfit,protection,"bosses_of_mass_destruction:void_blossom","bosses_of_mass_destruction:spore_ball","native_blossom_spore");
                for(String attack:VanillaBossCases.ATTACKS.keySet())nativeVanilla(level,player,outfit,protection,attack);
                nativeIgnisUltimate(level,player,outfit,protection);
            }
        }
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("scope","Actual server Player.hurt with registered equipment, Hard difficulty; event-based item defenses apply. Damage fixture inputs reproduce attacks at a 20-HP target; 200-HP measurement victim avoids death/downing truncation. Native probes are separately labeled. No shields, accessories/gloves, active potion effects, set-effect warmup, or environmental bonuses. Partial outfits leave absent slots empty. Protection IV is tested only when every equipped item reports support through NeoForge supportsEnchantment; unsupported variants are omitted. Each hit starts with fresh armor, so durability-absorbing sets require the separate endurance probes.");
        report.put("outfitCount",outfits.size()); report.put("measurementCount",rows.size()); report.put("errors",failures);
        report.put("inventory",inventory); report.put("measurements",rows);
        report.put("protectionUnsupportedOutfits",outfits.stream().filter(o->!o.pieces.isEmpty()&&enchantmentLevels(player,o).size()==1).map(o->o.id).toList());
        Files.createDirectories(destination.getParent());
        Files.writeString(destination,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        BossBalanceTests.result("armor_matrix_measurements_recorded","all",1,rows.isEmpty()?0:1);
        BossBalanceTests.result("armor_matrix_errors","all",0,failures);
        BossBalanceTests.result("armor_matrix_modded_profiles","all",1,outfits.stream().anyMatch(o->!o.id.startsWith("minecraft:")&&!o.pieces.isEmpty())?1:0);
    }

    static void runEffects(ServerLevel level,Path destination) throws Exception {
        rows.clear();inventory.clear();failures=0;
        List<Outfit> outfits=discover().stream().filter(o->o.pieces.size()==4&&!o.id.startsWith("minecraft:")).toList();
        BossBalanceTests.TestPlayer player=BossBalanceTests.damagePlayer(level);
        for(Outfit outfit:outfits) {
            int protection=enchantmentLevels(player,outfit).contains(4)?4:0;
            List<String> variants=new ArrayList<>();variants.add("fresh_four_pieces");
            if(outfit.id.startsWith("aether:")&&BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(outfit.id+"_gloves")))variants.add("matching_gloves");
            if(outfit.id.equals("aether:zanite"))variants.add("75_percent_worn");
            for(String variant:variants)for(String attack:List.of("naga_head","hydra_flame","lich_bolt","ignis_heavy_cap","warden_melee","warden_sonic_boom")) {
                LivingEntity boss=null;
                try {
                    clearAccessories(player);equip(player,outfit,protection);
                    if(variant.equals("matching_gloves"))equipGloves(player,outfit.id+"_gloves");
                    if(variant.equals("75_percent_worn"))for(EquipmentSlot slot:SLOTS){ItemStack stack=player.getItemBySlot(slot);stack.setDamageValue((int)(stack.getMaxDamage()*.75));}
                    for(int tick=0;tick<25;tick++) {
                        player.doTick();level.tickNonPassenger(player);
                        player.moveTo(8,220,4,0,0);player.setDeltaMovement(0,0,0);
                    }
                    BossBalanceTests.updateEquipment(player);player.setHealth(player.getMaxHealth());player.invulnerableTime=0;player.hurtTime=0;
                    List<String> effects=player.getActiveEffects().stream().map(effect->BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value())+":"+effect.getAmplifier()).toList();
                    double before=player.getHealth(),armor=player.getAttributeValue(Attributes.ARMOR),toughness=player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
                    String id;
                    boolean accepted;
                    if(attack.startsWith("warden_")) {
                        id="minecraft:warden";boss=BossBalanceTests.damageBoss(level,id);VanillaBossCases.perform(attack,level,player,boss);accepted=before>player.getHealth();
                    } else {
                        DamageCases.Case test=cases().stream().filter(c->c.name().equals(attack)).findFirst().orElseThrow();id=test.boss();boss=BossBalanceTests.damageBoss(level,id);
                        var holder=level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.parse(test.type())));
                        DamageSource source=new DamageSource(holder,boss,boss);accepted=player.hurt(source,source.scalesWithDifficulty()?test.oldHard()/1.5f:test.oldHard());
                    }
                    record(outfit,protection,id,attack,"25_native_player_and_entity_ticks_then_damage",player,before,armor,toughness,accepted);
                    Map<String,Object> row=rows.getLast();row.put("variant",variant);row.put("effectsBefore",effects);row.put("ticks",25);
                    if(outfit.id.equals("aether:phoenix")&&variant.equals("matching_gloves")&&attack.equals("hydra_flame"))BossBalanceTests.result("phoenix_gloves_native_fire_immunity",id,0,(before-player.getHealth())/2);
                } catch(Throwable error){failure(outfit,protection,outfit.id,variant+"_"+attack,error);}
                finally{if(boss!=null)boss.discard();}
            }
        }
        for(Outfit outfit:outfits)if(outfit.id.equals("deeperdarker:resonarium"))resonariumEndurance(level,player,outfit);
        clearAccessories(player);
        Map<String,Object> report=new LinkedHashMap<>();report.put("scope","Supplemental full modded sets with Protection IV where supported; plain armor when Protection is disallowed. Each set receives 25 actual ServerPlayer.doTick plus ServerLevel.tickNonPassenger calls at a dry stationary Overworld position. Matching Aether glove variants use the real Accessories capability; worn Zanite pieces use 75% durability consumed. No arbitrary potion buffs. Source-fixture probes use normal 20-HP-target captured input. Warden attacks use native methods. Resonarium endurance variants retain the same armor over 12 native Warden hits, starting fresh/75%/99% worn, with a 1000-HP measuring victim. Timed/environmental abilities outside these conditions remain outside this sample.");
        report.put("outfitCount",outfits.size());report.put("measurementCount",rows.size());report.put("errors",failures);report.put("measurements",rows);
        Files.createDirectories(destination.getParent());Files.writeString(destination,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        BossBalanceTests.result("armor_effects_errors","all",0,failures);
        BossBalanceTests.result("armor_effects_measurements_recorded","all",1,rows.isEmpty()?0:1);
    }

    private static List<Integer> enchantmentLevels(BossBalanceTests.TestPlayer player,Outfit outfit) {
        var protection=player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        return !outfit.pieces.isEmpty()&&outfit.pieces.values().stream().allMatch(item->new ItemStack(item).supportsEnchantment(protection))?List.of(0,4):List.of(0);
    }

    private static void resonariumEndurance(ServerLevel level,BossBalanceTests.TestPlayer player,Outfit outfit) {
        for(int worn:List.of(0,75,99)) {
            LivingEntity boss=null;
            try {
                clearAccessories(player);equip(player,outfit,0);player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);
                for(EquipmentSlot slot:SLOTS){ItemStack stack=player.getItemBySlot(slot);stack.setDamageValue((int)(stack.getMaxDamage()*worn/100d));}
                boss=BossBalanceTests.damageBoss(level,"minecraft:warden");
                for(int hit=1;hit<=12;hit++) {
                    BossBalanceTests.updateEquipment(player);player.invulnerableTime=0;player.hurtTime=0;
                    double before=player.getHealth(),armor=player.getAttributeValue(Attributes.ARMOR),toughness=player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
                    List<Integer> damageBefore=SLOTS.stream().map(slot->player.getItemBySlot(slot).getDamageValue()).toList();
                    VanillaBossCases.perform("warden_melee",level,player,boss);
                    record(outfit,0,"minecraft:warden","warden_melee","native_warden_repeated_same_armor",player,before,armor,toughness,before>player.getHealth());
                    Map<String,Object> row=rows.getLast();row.put("variant","endurance_"+worn+"_percent_worn");row.put("hitNumber",hit);row.put("itemDamageBefore",damageBefore);
                }
            }catch(Throwable error){failure(outfit,0,"minecraft:warden","endurance_"+worn,error);}finally{if(boss!=null)boss.discard();}
        }
    }

    private static Object accessories(BossBalanceTests.TestPlayer player)throws Exception {
        Object capability=Class.forName("io.wispforest.accessories.api.AccessoriesCapability").getMethod("get",LivingEntity.class).invoke(null,player);
        if(capability==null)throw new IllegalStateException("Player has no Accessories capability");return capability;
    }
    private static void clearAccessories(BossBalanceTests.TestPlayer player)throws Exception {
        Class<?> api=Class.forName("io.wispforest.accessories.api.AccessoriesCapability"),containerApi=Class.forName("io.wispforest.accessories.api.AccessoriesContainer");Object capability=accessories(player);
        @SuppressWarnings("unchecked") Map<String,Object> containers=(Map<String,Object>)api.getMethod("getContainers").invoke(capability);
        for(Object container:containers.values()) {
            ((net.minecraft.world.Container)containerApi.getMethod("getAccessories").invoke(container)).clearContent();
            containerApi.getMethod("markChanged").invoke(container);
        }
    }
    private static void equipGloves(BossBalanceTests.TestPlayer player,String id)throws Exception {
        Class<?> api=Class.forName("io.wispforest.accessories.api.AccessoriesCapability");Object capability=accessories(player);Item item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        api.getMethod("updateContainers").invoke(capability);
        Object slot=api.getMethod("attemptToEquipAccessory",ItemStack.class).invoke(capability,new ItemStack(item));
        if(slot==null||!Boolean.TRUE.equals(api.getMethod("isEquipped",Item.class).invoke(capability,item)))throw new IllegalStateException("Could not equip real accessory "+id);
    }

    private static List<Outfit> discover() {
        Map<String,Map<EquipmentSlot,Item>> groups=new TreeMap<>();
        for(Item item:BuiltInRegistries.ITEM) {
            if(!(item instanceof ArmorItem armor)||!SLOTS.contains(armor.getEquipmentSlot()))continue;
            String id=BuiltInRegistries.ITEM.getKey(item).toString();
            String group=id.replaceFirst("_(helmet|chestplate|leggings|boots)$","");
            Map<EquipmentSlot,Item> parts=groups.computeIfAbsent(group,k->new EnumMap<>(EquipmentSlot.class));
            if(parts.containsKey(armor.getEquipmentSlot()))throw new IllegalStateException("Ambiguous armor grouping: "+id);
            parts.put(armor.getEquipmentSlot(),item);
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("id",id);row.put("group",group);row.put("slot",armor.getEquipmentSlot().getName());
            row.put("material",armor.getMaterial().unwrapKey().map(k->k.location().toString()).orElse("unregistered"));
            row.put("baseDefense",armor.getDefense());row.put("baseToughness",armor.getToughness());row.put("class",item.getClass().getName());
            inventory.add(row);
        }
        List<Outfit> result=new ArrayList<>(); result.add(new Outfit("none",Map.of()));
        groups.forEach((id,parts)->result.add(new Outfit(id,Map.copyOf(parts))));
        // These complete sets intentionally use different item stems per slot, so the
        // generic registry-name grouping above also retains their single-piece controls.
        outfit(result,"cataclysm:ignitium_with_elytra","cataclysm:ignitium_helmet","cataclysm:ignitium_elytra_chestplate","cataclysm:ignitium_leggings","cataclysm:ignitium_boots");
        for(String variant:List.of("1","2")) {
            outfit(result,"the_bumblezone:bee_outfit_"+variant,"the_bumblezone:stingless_bee_helmet_"+variant,"the_bumblezone:bumble_bee_chestplate_"+variant,"the_bumblezone:honey_bee_leggings_"+variant,"the_bumblezone:carpenter_bee_boots_"+variant);
            outfit(result,"the_bumblezone:bee_outfit_trans_"+variant,"the_bumblezone:stingless_bee_helmet_"+variant,"the_bumblezone:bumble_bee_chestplate_trans_"+variant,"the_bumblezone:honey_bee_leggings_"+variant,"the_bumblezone:carpenter_bee_boots_"+variant);
        }
        for(String chest:List.of("vest","gloves"))for(String legs:List.of("belt","wings"))
            outfit(result,"twilightforest:travellers_"+chest+"_"+legs,"twilightforest:travellers_goggles","twilightforest:travellers_"+chest,"twilightforest:travellers_"+legs,"twilightforest:travellers_boots");
        return result;
    }

    private static void outfit(List<Outfit> outfits,String id,String... ids) {
        Map<EquipmentSlot,Item> parts=new EnumMap<>(EquipmentSlot.class);
        for(int i=0;i<SLOTS.size();i++) {
            ResourceLocation key=ResourceLocation.parse(ids[i]);
            if(!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalStateException("Missing installed outfit item "+key);
            Item item=BuiltInRegistries.ITEM.get(key);
            if(!(item instanceof ArmorItem armor)||armor.getEquipmentSlot()!=SLOTS.get(i))throw new IllegalStateException("Unexpected slot for "+key);
            parts.put(SLOTS.get(i),item);
        }
        outfits.add(new Outfit(id,Map.copyOf(parts)));
    }

    @SuppressWarnings("unchecked") private static List<DamageCases.Case> cases() throws Exception {
        Field f=DamageCases.class.getDeclaredField("CASES");f.setAccessible(true);
        return (List<DamageCases.Case>)f.get(null);
    }

    private static void equip(BossBalanceTests.TestPlayer player,Outfit outfit,int protection) throws Exception {
        BossBalanceTests.resetDamagePlayer(player);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);player.setHealth(200);
        player.clearFire();player.setDeltaMovement(0,0,0);player.moveTo(8,220,4,0,0);
        for(EquipmentSlot slot:SLOTS) {
            Item item=outfit.pieces.get(slot); ItemStack stack=item==null?ItemStack.EMPTY:new ItemStack(item);
            if(item!=null&&protection>0)stack.enchant(player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION),protection);
            player.setItemSlot(slot,stack);
        }
        BossBalanceTests.updateEquipment(player);
        player.setHealth(player.getMaxHealth());
    }

    private static void fixture(ServerLevel level,BossBalanceTests.TestPlayer player,Outfit outfit,int protection,DamageCases.Case test) {
        LivingEntity boss=null;Entity direct=null;
        try {
            equip(player,outfit,protection);boss=BossBalanceTests.damageBoss(level,test.boss());
            if(test.state()>=0) {
                if(test.boss().endsWith(":obsidilith"))field(boss,"currentAttack",(byte)test.state());
                else boss.getClass().getMethod("setAttackState",int.class).invoke(boss,test.state());
            }
            if(test.boss().endsWith(":maledictus"))boss.getClass().getMethod("setRageMeter",int.class).invoke(boss,test.rage());
            if(!test.animation().isEmpty()) {
                Method m=DamageCases.class.getDeclaredMethod("setAnimation",Entity.class,String.class);m.setAccessible(true);m.invoke(null,boss,test.animation());
            }
            direct=test.direct().isEmpty()?boss:projectile(level,test.direct(),boss);
            var holder=level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.parse(test.type())));
            DamageSource source=new DamageSource(holder,direct,test.owner()?boss:null);
            float input=source.scalesWithDifficulty()?test.oldHard()/1.5f:test.oldHard();
            double before=player.getHealth();double armor=player.getAttributeValue(Attributes.ARMOR), toughness=player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            boolean accepted=player.hurt(source,input);
            record(outfit,protection,test.boss(),test.name(),"captured_input_damage_fixture",player,before,armor,toughness,accepted);
            if(outfit.id.equals("minecraft:diamond")&&protection==0)BossBalanceTests.result("armor_reference_"+test.name(),test.boss(),test.hearts(),(before-player.getHealth())/2);
        } catch(Throwable error) { failure(outfit,protection,test.boss(),test.name(),error); }
        finally {if(direct!=null&&direct!=boss)direct.discard();if(boss!=null)boss.discard();}
    }

    private static void nativeMelee(ServerLevel level,BossBalanceTests.TestPlayer player,Outfit outfit,int protection,String id,String name) {
        LivingEntity boss=null;
        try {
            equip(player,outfit,protection);boss=BossBalanceTests.damageBoss(level,id);
            double before=player.getHealth(),armor=player.getAttributeValue(Attributes.ARMOR),toughness=player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            boolean accepted=((Mob)boss).doHurtTarget(player);
            record(outfit,protection,id,name,"native_doHurtTarget",player,before,armor,toughness,accepted);
        }catch(Throwable error){failure(outfit,protection,id,name,error);}finally{if(boss!=null)boss.discard();}
    }

    private static void nativeProjectile(ServerLevel level,BossBalanceTests.TestPlayer player,Outfit outfit,int protection,String id,String projectileId,String name) {
        LivingEntity boss=null; Entity direct=null;
        try {
            equip(player,outfit,protection);boss=BossBalanceTests.damageBoss(level,id);direct=projectile(level,projectileId,boss);
            Method hit=find(direct.getClass(),"entityHit",EntityHitResult.class);
            if(hit==null)hit=find(direct.getClass(),"onHitEntity",EntityHitResult.class);
            if(hit==null)throw new NoSuchMethodException("No projectile hit method: "+projectileId);
            hit.setAccessible(true);
            double before=player.getHealth(),armor=player.getAttributeValue(Attributes.ARMOR),toughness=player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            hit.invoke(direct,new EntityHitResult(player));
            record(outfit,protection,id,name,"native_projectile_collision",player,before,armor,toughness,before>player.getHealth());
        }catch(Throwable error){failure(outfit,protection,id,name,error);}finally{if(direct!=null)direct.discard();if(boss!=null)boss.discard();}
    }

    private static void nativeVanilla(ServerLevel level,BossBalanceTests.TestPlayer player,Outfit outfit,int protection,String name) {
        String id=VanillaBossCases.bossId(name);LivingEntity boss=null;
        try {
            equip(player,outfit,protection);boss=BossBalanceTests.damageBoss(level,id);
            Class.forName("survival.balance.BossHealthBalance").getMethod("applyInitialProfile",LivingEntity.class).invoke(null,boss);
            double before=player.getHealth(),armor=player.getAttributeValue(Attributes.ARMOR),toughness=player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            VanillaBossCases.perform(name,level,player,boss);
            record(outfit,protection,id,name,"native_minecraft_attack_method",player,before,armor,toughness,before>player.getHealth());
            if(outfit.id.equals("minecraft:diamond")&&protection==0)BossBalanceTests.result("armor_reference_"+name,id,VanillaBossCases.ATTACKS.get(name),(before-player.getHealth())/2);
            if(name.equals("warden_sonic_boom")&&(outfit.id.equals("none")||outfit.id.startsWith("minecraft:")))BossBalanceTests.result("armor_sonic_bypass_"+outfit.id+"_prot"+protection,id,5.5,(before-player.getHealth())/2);
        }catch(Throwable error){failure(outfit,protection,id,name,error);}finally{if(boss!=null)boss.discard();}
    }

    private static void record(Outfit outfit,int protection,String boss,String attack,String method,BossBalanceTests.TestPlayer player,double before,double armor,double toughness,boolean accepted) {
        double hearts=(before-player.getHealth())/2;
        if(!Double.isFinite(hearts)||hearts<0)throw new IllegalStateException("Invalid damage: "+hearts);
        Map<String,Object> row=new LinkedHashMap<>();row.put("armor",outfit.id);row.put("pieces",outfit.pieces.size());row.put("protection",protection);
        row.put("boss",boss);row.put("attack",attack);row.put("method",method);row.put("armorPoints",armor);row.put("toughness",toughness);
        row.put("heartsLost",hearts);row.put("accepted",accepted);row.put("healthAfter",player.getHealth());
        row.put("maximumHealth",player.getMaxHealth());
        row.put("equipped",SLOTS.stream().map(slot->BuiltInRegistries.ITEM.getKey(player.getItemBySlot(slot).getItem()).toString()).toList());
        row.put("itemDamageAfter",SLOTS.stream().map(slot->player.getItemBySlot(slot).getDamageValue()).toList());
        row.put("itemMaxDurabilityAfter",SLOTS.stream().map(slot->player.getItemBySlot(slot).getMaxDamage()).toList());
        rows.add(row);
    }

    private static void nativeIgnisUltimate(ServerLevel level,BossBalanceTests.TestPlayer player,Outfit outfit,int protection) {
        LivingEntity boss=null;
        java.util.Set<Entity> existing=new java.util.HashSet<>();for(Entity entity:level.getAllEntities())existing.add(entity);
        try {
            equip(player,outfit,protection);boss=BossBalanceTests.damageBoss(level,"cataclysm:ignis");
            boss.moveTo(8,220,8,0,0);boss.yBodyRot=0;boss.yHeadRot=0;boss.tickCount=40;
            player.moveTo(8,220,10,180,0);((net.minecraft.world.entity.Mob)boss).setTarget(player);
            Object animation=boss.getClass().getField("ULTIMATE_ATTACK").get(null);
            boss.getClass().getMethod("setAnimation",animation.getClass()).invoke(boss,animation);
            boss.getClass().getMethod("setAnimationTick",int.class).invoke(boss,74);
            double before=player.getHealth(),armor=player.getAttributeValue(Attributes.ARMOR),toughness=player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            boss.aiStep();
            record(outfit,protection,"cataclysm:ignis","native_ignis_ultimate","native_ULTIMATE_ATTACK_frame74_capped_hit",player,before,armor,toughness,before>player.getHealth());
            if(outfit.id.equals("minecraft:diamond")&&protection==0)BossBalanceTests.result("armor_reference_native_ignis_ultimate","cataclysm:ignis",9.5,(before-player.getHealth())/2);
            if(outfit.id.equals("minecraft:netherite")&&protection==4)BossBalanceTests.result("armor_netherite_native_ignis_ultimate","cataclysm:ignis",2.98599378875997,(before-player.getHealth())/2);
        }catch(Throwable error){failure(outfit,protection,"cataclysm:ignis","native_ignis_ultimate",error);}finally{
            java.util.List<Entity> spawned=new java.util.ArrayList<>();for(Entity entity:level.getAllEntities())if(entity!=player&&!existing.contains(entity))spawned.add(entity);
            for(Entity entity:spawned)entity.discard();if(boss!=null)boss.discard();
        }
    }

    private static void failure(Outfit outfit,int protection,String boss,String attack,Throwable error) {
        failures++;Throwable cause=error instanceof java.lang.reflect.InvocationTargetException&&error.getCause()!=null?error.getCause():error;
        rows.add(Map.of("armor",outfit.id,"protection",protection,"boss",boss,"attack",attack,"error",cause.toString()));
        BossBalanceTests.error("armor_"+outfit.id+"_prot"+protection+"_"+attack,boss,cause);
    }
    private static Entity projectile(ServerLevel level,String id,LivingEntity owner) {
        ResourceLocation key=ResourceLocation.parse(id);if(!BuiltInRegistries.ENTITY_TYPE.containsKey(key))throw new IllegalArgumentException(id);
        Entity result=BuiltInRegistries.ENTITY_TYPE.get(key).create(level);if(result==null)throw new IllegalStateException(id);
        result.moveTo(8,220,6,0,0);if(result instanceof Projectile p)p.setOwner(owner);return result;
    }
    private static Method find(Class<?> type,String name,Class<?>...args) {for(;type!=null;type=type.getSuperclass())try{return type.getDeclaredMethod(name,args);}catch(NoSuchMethodException ignored){}return null;}
    private static void field(Object object,String name,Object value)throws Exception {for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
}

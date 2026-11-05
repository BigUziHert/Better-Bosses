package survival.balance.tests;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Test-only mod. Never install this jar on a production server. */
@Mod("survival_boss_balance_tests")
public final class BossBalanceTests {
    private static final Path RESULTS = Path.of("../tests/results");
    private static final ResourceLocation LEGACY = ResourceLocation.parse("survival_tweaks:twilight_boss_health_3x");
    private static final ResourceLocation CURRENT = ResourceLocation.parse("survival_boss_balance:health");
    private static final List<TestPlayer> PLAYERS = new ArrayList<>();
    private static final List<Map<String,Object>> CHECKS = new ArrayList<>();
    private static int serial;
    private static String phase;
    private static double actualArmorBefore, actualToughnessBefore;
    private static double effectiveArmorBeforeHit, effectiveToughnessBeforeHit;
    private static final List<Integer> actualProtectionBefore=new ArrayList<>();
    private static final LinkedHashMap<String,Double> TARGETS = new LinkedHashMap<>();
    static {
        TARGETS.put("minecraft:wither",900d);
        TARGETS.put("minecraft:warden",1300d);
        TARGETS.put("minecraft:ender_dragon",1100d);
        TARGETS.put("twilightforest:naga",1000d);
        TARGETS.put("twilightforest:lich",650d);
        TARGETS.put("twilightforest:minoshroom",1000d);
        TARGETS.put("twilightforest:knight_phantom",180d);
        TARGETS.put("twilightforest:alpha_yeti",1100d);
        TARGETS.put("twilightforest:hydra",1500d);
        TARGETS.put("twilightforest:ur_ghast",1100d);
        TARGETS.put("twilightforest:snow_queen",1200d);
        TARGETS.put("bosses_of_mass_destruction:lich",1300d);
        TARGETS.put("bosses_of_mass_destruction:void_blossom",1400d);
        TARGETS.put("bosses_of_mass_destruction:gauntlet",1500d);
        TARGETS.put("bosses_of_mass_destruction:obsidilith",1700d);
        TARGETS.put("cataclysm:netherite_monstrosity",1200d);
        TARGETS.put("cataclysm:ender_guardian",850d);
        TARGETS.put("cataclysm:the_harbinger",950d);
        TARGETS.put("cataclysm:ancient_remnant",1000d);
        TARGETS.put("cataclysm:the_leviathan",1100d);
        TARGETS.put("cataclysm:scylla",1100d);
        TARGETS.put("cataclysm:maledictus",1100d);
        TARGETS.put("cataclysm:ignis",1100d);
    }

    public BossBalanceTests() {
        NeoForge.EVENT_BUS.addListener(this::register);
        VisibleBossDemo.install();
        ExhaustiveCapture.install();
        LegacyInstallCases.install();
    }
    private void register(RegisterCommandsEvent event) {
        var exhaustive = Commands.literal("exhaustive");
        for (String suite : List.of("twilight", "bmd", "cataclysm")) {
            exhaustive.then(Commands.literal(suite).executes(c -> runExhaustive(c.getSource(), suite, ""))
                .then(Commands.argument("boss", StringArgumentType.greedyString()).executes(c ->
                    runExhaustive(c.getSource(), suite, StringArgumentType.getString(c, "boss").trim()))));
        }
        event.getDispatcher().register(Commands.literal("bbtest").requires(s -> s.hasPermission(4))
            .then(Commands.literal("preinstall_prepare").executes(c -> runLegacy(c.getSource(),false)))
            .then(Commands.literal("preinstall_verify").executes(c -> runLegacy(c.getSource(),true)))
            .then(Commands.literal("armor_effects").executes(c -> runExtra(c.getSource(),"armor_effects")))
            .then(Commands.literal("armor_matrix").executes(c -> runExtra(c.getSource(),"armor_matrix")))
            .then(Commands.literal("vanilla").executes(c -> runExtra(c.getSource(),"vanilla")))
            .then(Commands.literal("boss_bars").executes(c -> runExtra(c.getSource(),"boss_bars")))
            .then(Commands.literal("health").executes(c -> runHealth(c.getSource())))
            .then(Commands.literal("fixed_health").executes(c -> runFixedHealth(c.getSource())))
            .then(Commands.literal("damage").executes(c -> runDamage(c.getSource())))
            .then(Commands.literal("actual_attacks").executes(c -> runActualAttacks(c.getSource())))
            .then(Commands.literal("multiplayer_targets").executes(c -> runMultiplayerTargets(c.getSource())))
            .then(exhaustive)
            .then(Commands.literal("prepare_restart").executes(c -> prepareRestart(c.getSource())))
            .then(Commands.literal("verify_restart").executes(c -> verifyRestart(c.getSource())))
            .then(Commands.literal("cleanup").executes(c -> { cleanupPlayers(); return 1; })));
    }

    private static int runExtra(CommandSourceStack source,String suite) {
        ServerLevel level=setup(source,suite);
        try {
            switch(suite){
                case "armor_effects" -> Class.forName("survival.balance.tests.ArmorEffectsCases").getDeclaredMethod("run",ServerLevel.class,Path.class).invoke(null,level,RESULTS.resolve("armor-effects.json"));
                case "armor_matrix" -> Class.forName("survival.balance.tests.ArmorMatrixCases").getDeclaredMethod("run",ServerLevel.class,Path.class).invoke(null,level,RESULTS.resolve("armor-matrix.json"));
                case "vanilla" -> Class.forName("survival.balance.tests.VanillaBossCases").getDeclaredMethod("run",ServerLevel.class,TestPlayer.class).invoke(null,level,damagePlayer(level));
                case "boss_bars" -> Class.forName("survival.balance.tests.BossBarCases").getDeclaredMethod("run",ServerLevel.class).invoke(null,level);
            }
        }catch(Throwable error){failure(suite,"all",error);if(error.getCause()!=null)error.getCause().printStackTrace();}
        return finish(source);
    }
    private static int runLegacy(CommandSourceStack source,boolean verify) {
        ServerLevel level=setup(source,verify?"preinstall_verify":"preinstall_prepare");
        try {if(verify)LegacyInstallCases.verify(level);else LegacyInstallCases.prepare(level);}catch(Throwable error){failure("preinstall", "all",error);}
        return finish(source);
    }
    private static ServerLevel setup(CommandSourceStack source,String requestedPhase) {
        if (!Boolean.getBoolean("survival.bossBalance.testMode")) throw new IllegalStateException("Test mode JVM property is required");
        phase=requestedPhase; CHECKS.clear(); cleanupPlayers();
        MinecraftServer server=source.getServer();
        server.setDifficulty(Difficulty.HARD,true);
        ServerLevel level=server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,server);
        level.setChunkForced(0,0,true);
        level.getChunk(0,0);
        return level;
    }

    private static LivingEntity create(ServerLevel level,String id,boolean join) throws Exception {
        EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
        if (type==EntityType.PIG && !id.equals("minecraft:pig")) throw new IllegalArgumentException("Unknown entity "+id);
        Entity entity=type.create(level);
        if (!(entity instanceof LivingEntity living)) throw new IllegalStateException("Not living: "+id);
        living.moveTo(8,220,8,0,0);
        if (living instanceof Mob mob) {
            mob.finalizeSpawn(level,level.getCurrentDifficultyAt(living.blockPosition()),MobSpawnType.COMMAND,null);
            mob.setNoAi(true); mob.setPersistenceRequired();
        }
        living.setNoGravity(true); living.setInvulnerable(true);
        living.addTag("bbtest_entity");
        if (join && !level.addFreshEntity(living)) throw new IllegalStateException("Could not join: "+id);
        return living;
    }

    private static void players(ServerLevel level,int count) throws Exception {
        cleanupPlayers();
        for(int i=0;i<count;i++) {
            TestPlayer player=new TestPlayer(level,new GameProfile(UUID.randomUUID(),"BBTest"+(++serial)));
            player.moveTo(5+i,220,4,0,0);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
            setField(ServerPlayer.class,player,"spawnInvulnerableTime",0);
            level.addNewPlayer(player);
            PLAYERS.add(player);
        }
    }
    private static void cleanupPlayers() {
        for(TestPlayer p:PLAYERS) p.remove(Entity.RemovalReason.DISCARDED);
        PLAYERS.clear();
    }
    public static final class TestPlayer extends FakePlayer {
        private boolean nativeRiding;

        TestPlayer(ServerLevel level,GameProfile profile) { super(level,profile); }
        @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
        @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) { return true; }

        void useNativeRiding(boolean enable) { nativeRiding = enable; }

        @Override public boolean startRiding(Entity mount, boolean force) {
            if (!nativeRiding) return super.startRiding(mount, force);
            try {
                // FakePlayer refuses every mount. Use the real player path for explicit grab tests.
                var lookup = MethodHandles.privateLookupIn(FakePlayer.class, MethodHandles.lookup());
                var method = lookup.findSpecial(ServerPlayer.class, "startRiding",
                    MethodType.methodType(boolean.class, Entity.class, boolean.class), FakePlayer.class);
                return (boolean) method.invokeExact((FakePlayer) this, mount, force);
            } catch (Throwable error) {
                throw new IllegalStateException("Cannot use native riding in the grab fixture", error);
            }
        }
    }

    private static void check(String test,String entity,double expected,double actual) {
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("phase",phase); row.put("test",test); row.put("entity",entity);
        row.put("expected",expected); row.put("actual",actual); row.put("passed",Double.isFinite(actual)&&Math.abs(expected-actual)<0.005);
        CHECKS.add(row);
        System.out.println("BBTEST "+new GsonBuilder().create().toJson(row));
    }
    private static void failure(String test,String entity,Throwable error) {
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("phase",phase);row.put("test",test);row.put("entity",entity);row.put("passed",false);row.put("error",error.toString());
        CHECKS.add(row); System.out.println("BBTEST "+new GsonBuilder().create().toJson(row)); error.printStackTrace();
    }
    private static int finish(CommandSourceStack source) {
        cleanupPlayers();
        long failed=CHECKS.stream().filter(c -> !Boolean.TRUE.equals(c.get("passed"))).count();
        Map<String,Object> report=new LinkedHashMap<>();report.put("phase",phase);report.put("total",CHECKS.size());report.put("failed",failed);report.put("checks",CHECKS);
        try {Files.createDirectories(RESULTS);Files.writeString(RESULTS.resolve(phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardCharsets.UTF_8);}catch(Exception ex){throw new RuntimeException(ex);}
        source.sendSuccess(() -> Component.literal("BBTEST_COMPLETE "+phase+" checks="+CHECKS.size()+" failed="+failed),true);
        return (int)(CHECKS.size()-failed);
    }
    private static void setField(Class<?> owner,Object object,String name,Object value) throws Exception {
        Field field=owner.getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    private static void invokeHealth(String method,LivingEntity entity) throws Exception {
        Class<?> health=Class.forName("survival.balance.BossHealthBalance");
        health.getMethod(method,LivingEntity.class).invoke(null,entity);
    }

    private static int runHealth(CommandSourceStack source) {
        ServerLevel level=setup(source,"health");
        for(var entry:TARGETS.entrySet()) {
            String id=entry.getKey(); double target=entry.getValue();
            LivingEntity boss=null;
            try {
                boss=create(level,id,true);
                check("fresh_max",id,target,boss.getMaxHealth());check("fresh_current",id,target,boss.getHealth());
                boss.setHealth((float)(target*0.4));
                invokeHealth("applyInitialProfile",boss);
                invokeHealth("applyInitialProfile",boss);
                check("repeat_join_wounded",id,target*0.4,boss.getHealth());
                CompoundTag saved=boss.saveWithoutId(new CompoundTag());saved.putString("id",id);
                UUID uuid=boss.getUUID();boss.discard();
                LivingEntity restored=(LivingEntity)EntityType.create(saved,level).orElseThrow();
                if(!level.addFreshEntity(restored))throw new IllegalStateException("Serialized entity rejoin failed");
                boss=restored;
                check("nbt_reload_max",id,target,boss.getMaxHealth());check("nbt_reload_wounded",id,target*0.4,boss.getHealth());
                check("nbt_reload_uuid",id,1,uuid.equals(boss.getUUID())?1:0);
                if(id.equals("twilightforest:naga")) {
                    Field field=boss.getClass().getDeclaredField("healthPerSegment");field.setAccessible(true);
                    check("naga_phase_threshold",id,target/10,field.getFloat(boss));
                }
            }catch(Throwable ex){failure("health",id,ex);}finally{if(boss!=null)boss.discard();}

            try {
                boss=create(level,id,false);
                if(id.startsWith("twilightforest:"))boss.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(LEGACY,2d,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                boss.setHealth(boss.getMaxHealth()*0.4f);
                if(!level.addFreshEntity(boss))throw new IllegalStateException("Migration join failed");
                check("migration_max",id,target,boss.getMaxHealth());check("migration_wounded_ratio",id,target*0.4,boss.getHealth());
                check("legacy_modifier_removed",id,0,boss.getAttribute(Attributes.MAX_HEALTH).hasModifier(LEGACY)?1:0);
            }catch(Throwable ex){failure("migration",id,ex);}finally{if(boss!=null)boss.discard();}

            for(int count:new int[]{3,4,5}) {
                try {
                    players(level,count);
                    boss=create(level,id,true);
                    boss.setHealth(boss.getMaxHealth()*0.6f);
                    ((Mob)boss).setTarget(PLAYERS.getFirst());
                    check("party_"+count+"_max",id,target,boss.getMaxHealth());
                    check("party_"+count+"_ratio",id,target*0.6,boss.getHealth());
                    double current=boss.getHealth();
                    cleanupPlayers();
                    invokeHealth("applyInitialProfile",boss);
                    check("party_"+count+"_leave_max",id,target,boss.getMaxHealth());
                    check("party_"+count+"_leave_health",id,current,boss.getHealth());
                    CompoundTag saved=boss.saveWithoutId(new CompoundTag());saved.putString("id",id);boss.discard();
                    boss=(LivingEntity)EntityType.create(saved,level).orElseThrow();
                    if(!level.addFreshEntity(boss))throw new IllegalStateException("Party reload join failed");
                    check("party_"+count+"_reload_max",id,target,boss.getMaxHealth());
                    check("party_"+count+"_reload_current",id,current,boss.getHealth());
                }catch(Throwable ex){failure("party_"+count,id,ex);}finally{if(boss!=null)boss.discard();cleanupPlayers();}
            }
        }
        try {
            LivingEntity cow=create(level,"minecraft:cow",true);check("ordinary_cow", "minecraft:cow",10,cow.getMaxHealth());cow.discard();
            LivingEntity minotaur=create(level,"twilightforest:minotaur",true);check("ordinary_tf_minotaur", "twilightforest:minotaur",30,minotaur.getMaxHealth());minotaur.discard();
            LivingEntity clone=create(level,"twilightforest:lich",false);
            CompoundTag cloneNbt=clone.saveWithoutId(new CompoundTag());cloneNbt.putUUID("MasterLich",UUID.randomUUID());clone.load(cloneNbt);
            level.addFreshEntity(clone);check("lich_clone_unchanged","twilightforest:lich_clone",100,clone.getMaxHealth());clone.discard();
            for(String controlId:List.of("cataclysm:ender_golem","minecraft:elder_guardian")){
                LivingEntity control=create(level,controlId,false);double oldMaximum=control.getMaxHealth();level.addFreshEntity(control);
                check("other_boss_unchanged",controlId,oldMaximum,control.getMaxHealth());control.discard();
            }
        }catch(Throwable ex){failure("controls","control",ex);}
        for(String id:List.of("twilightforest:minoshroom","twilightforest:alpha_yeti"))try{
            players(level,4);LivingEntity boss=create(level,id,true);boss.setInvulnerable(false);((Mob)boss).setTarget(PLAYERS.getFirst());
            double maximum=boss.getMaxHealth(),before=boss.getHealth();
            boss.invulnerableTime=0;boss.hurt(level.damageSources().playerAttack(PLAYERS.getFirst()),10);
            check("real_player_damage_received",id,1,boss.getHealth()<before?1:0);
            double wounded=boss.getHealth();UUID uuid=boss.getUUID();
            CompoundTag saved=boss.saveWithoutId(new CompoundTag());saved.putString("id",id);boss.discard();cleanupPlayers();
            boss=(LivingEntity)EntityType.create(saved,level).orElseThrow();level.addFreshEntity(boss);
            check("attack_disconnect_reload_health",id,wounded,boss.getHealth());check("attack_disconnect_reload_max",id,maximum,boss.getMaxHealth());
            players(level,3);boss.invulnerableTime=0;boss.hurt(level.damageSources().playerAttack(PLAYERS.getFirst()),10);
            check("attack_after_rejoin_receives_damage",id,1,boss.getHealth()<wounded?1:0);
            check("attack_after_rejoin_max_stable",id,maximum,boss.getMaxHealth());check("attack_after_rejoin_uuid",id,1,uuid.equals(boss.getUUID())?1:0);
            boss.discard();cleanupPlayers();
        }catch(Throwable ex){failure("attack_rejoin",id,ex);cleanupPlayers();}
        return finish(source);
    }

    private static int runDamage(CommandSourceStack source) {
        ServerLevel level=setup(source,"damage");
        try { DamageCases.run(level,CHECKS); }catch(Throwable ex){failure("damage_suite","all",ex);}
        return finish(source);
    }

    private static int runFixedHealth(CommandSourceStack source) {
        ServerLevel level=setup(source,"fixed_health");
        try {
            FixedHealthCases.run(level);
        } catch (Exception error) {
            failure("fixed_health_suite", "all", error);
        }
        return finish(source);
    }

    private static int runMultiplayerTargets(CommandSourceStack source) {
        ServerLevel level=setup(source,"multiplayer_targets");
        try {
            MultiplayerTargetCases.run(level);
        } catch (Exception error) {
            failure("native_target_suite", "all", error);
        }
        return finish(source);
    }

    private static int runActualAttacks(CommandSourceStack source) {
        ServerLevel level=setup(source,"actual_attacks");
        try {
            TestPlayer victim=damagePlayer(level);
            resetActualPlayer(victim);
            ActualTwilightAttacks.run(level,victim);
            ActualBmdAttacks.run(level,victim);
            ActualCataclysmAttacks.run(level,victim);
        }catch(Throwable ex){failure("actual_attack_suite","all",ex);}
        return finish(source);
    }

    private static int runExhaustive(CommandSourceStack source, String suite, String filter) {
        if (!filter.isEmpty() && !TARGETS.containsKey(filter)) throw new IllegalArgumentException("Unknown boss: " + filter);
        String suffix = filter.isEmpty() ? "" : "_" + filter.substring(filter.indexOf(':') + 1);
        ServerLevel level = setup(source, "exhaustive_" + suite + suffix);
        ExhaustiveCapture.reset(filter);
        try {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.getChunk(x, z);
            for (int x = -24; x <= 40; x++) for (int z = -24; z <= 40; z++) {
                BlockPos pos = new BlockPos(x, 219, z);
                if (!level.getBlockState(pos).is(Blocks.STONE)) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            }
            TestPlayer victim = damagePlayer(level);
            resetActualPlayer(victim);
            victim.useNativeRiding(true);
            String type = switch (suite) {
                case "twilight" -> "ExhaustiveTwilight";
                case "bmd" -> "ExhaustiveBmd";
                case "cataclysm" -> "ExhaustiveCataclysm";
                default -> throw new IllegalArgumentException("Unknown suite: " + suite);
            };
            var method = Class.forName("survival.balance.tests." + type)
                .getDeclaredMethod("run", ServerLevel.class, TestPlayer.class);
            method.setAccessible(true);
            method.invoke(null, level, victim);
        } catch (Throwable error) {
            Throwable cause = error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null
                ? error.getCause() : error;
            failure("exhaustive_suite", suite, cause);
        }
        try {
            ExhaustiveCapture.write(RESULTS.resolve(phase + "-attacks.json"));
        } catch (Exception error) {
            failure("capture_export", suite, error);
        }
        return finish(source);
    }

    static void resetActualPlayer(TestPlayer player) throws Exception {
        resetDamagePlayer(player);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
        player.setHealth(20);
        player.moveTo(8,220,4,0,0);
        player.setDeltaMovement(0,0,0);
        actualArmorBefore=player.getAttributeValue(Attributes.ARMOR);
        actualToughnessBefore=player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        captureEffectiveDefenses(player);
        actualProtectionBefore.clear();
        var protection=player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
            actualProtectionBefore.add(player.getItemBySlot(slot).getEnchantments().getLevel(protection));
        }
    }

    static void captureEffectiveDefenses(TestPlayer player) {
        effectiveArmorBeforeHit=player.getAttributeValue(Attributes.ARMOR);
        effectiveToughnessBeforeHit=player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
    }

    static void observedAttack(String test,String entity,String method,TestPlayer player,double before,double expectedHearts) {
        double after=player.getHealth(), hearts=(before-after)/2d;
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("phase",phase);row.put("test",test);row.put("entity",entity);row.put("attackMethod",method);
        row.put("beforePlayerHP",before);row.put("afterPlayerHP",after);row.put("damageHP",before-after);
        row.put("armorAtReset",actualArmorBefore);row.put("toughnessAtReset",actualToughnessBefore);
        row.put("armorBefore",effectiveArmorBeforeHit);row.put("toughnessBefore",effectiveToughnessBeforeHit);row.put("protectionLevels",new ArrayList<>(actualProtectionBefore));
        row.put("armorItems",List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET).stream().map(slot->BuiltInRegistries.ITEM.getKey(player.getItemBySlot(slot).getItem()).toString()).toList());
        row.put("armorAfter",player.getAttributeValue(Attributes.ARMOR));row.put("toughnessAfter",player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        row.put("expected",expectedHearts);row.put("actual",hearts);
        row.put("passed",Math.abs(before-20)<0.001 && actualArmorBefore==20 && actualToughnessBefore==12 && actualProtectionBefore.stream().allMatch(n->n==4) && Double.isFinite(hearts) && Math.abs(expectedHearts-hearts)<0.005);
        CHECKS.add(row);System.out.println("BBTEST "+new GsonBuilder().create().toJson(row));
    }

    static TestPlayer damagePlayer(ServerLevel level) throws Exception {
        TestPlayer player=new TestPlayer(level,new GameProfile(UUID.randomUUID(),"BBVictim"+(++serial)));
        // FakePlayer normally has no Netty channel. Real BMD wave/burst damage first checks
        // whether a knockback payload is supported, so provide an empty in-memory channel.
        // It advertises no custom payloads; the network-only knockback send is skipped.
        setField(net.minecraft.network.Connection.class,player.connection.getConnection(),"channel",new io.netty.channel.embedded.EmbeddedChannel());
        player.setGameMode(GameType.SURVIVAL);player.moveTo(8,220,4,0,0);player.setNoGravity(true);
        setField(ServerPlayer.class,player,"spawnInvulnerableTime",0);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        // The health attribute is raised only to prevent test victims dying. Per-HP attack
        // formulas are supplied as captured 20-HP-player input amounts by each test case.
        equip(player,EquipmentSlot.HEAD,Items.NETHERITE_HELMET);
        equip(player,EquipmentSlot.CHEST,Items.NETHERITE_CHESTPLATE);
        equip(player,EquipmentSlot.LEGS,Items.NETHERITE_LEGGINGS);
        equip(player,EquipmentSlot.FEET,Items.NETHERITE_BOOTS);
        updateEquipment(player);
        player.setHealth(200);level.addNewPlayer(player);PLAYERS.add(player);
        return player;
    }
    private static void equip(TestPlayer player,EquipmentSlot slot,Item item) {
        ItemStack stack=new ItemStack(item);
        stack.enchant(player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION),4);
        player.setItemSlot(slot,stack);
    }
    static void updateEquipment(TestPlayer player) throws Exception {
        Method method=LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");method.setAccessible(true);method.invoke(player);
    }
    static LivingEntity damageBoss(ServerLevel level,String id) throws Exception {return create(level,id,false);}
    static void result(String test,String entity,double expected,double actual){check(test,entity,expected,actual);}
    static void error(String test,String entity,Throwable error){failure(test,entity,error);}
    static void resetDamagePlayer(TestPlayer player) throws Exception {
        player.removeAllEffects();player.setHealth(200);player.setAbsorptionAmount(0);player.invulnerableTime=0;player.hurtTime=0;
        setField(ServerPlayer.class,player,"spawnInvulnerableTime",0);
        equip(player,EquipmentSlot.HEAD,Items.NETHERITE_HELMET);equip(player,EquipmentSlot.CHEST,Items.NETHERITE_CHESTPLATE);
        equip(player,EquipmentSlot.LEGS,Items.NETHERITE_LEGGINGS);equip(player,EquipmentSlot.FEET,Items.NETHERITE_BOOTS);
        updateEquipment(player);
    }

    private static int prepareRestart(CommandSourceStack source) {
        ServerLevel level=setup(source,"prepare_restart");
        List<Map<String,Object>> manifest=new ArrayList<>();
        for(String id:List.of("twilightforest:minoshroom","twilightforest:alpha_yeti"))try {
            players(level,4);LivingEntity boss=create(level,id,true);((Mob)boss).setTarget(PLAYERS.getFirst());
            boss.setHealth(boss.getMaxHealth()*0.6f);boss.addTag("bbtest_restart");
            Map<String,Object> row=new LinkedHashMap<>();row.put("entity",id);row.put("uuid",boss.getUUID().toString());row.put("max",boss.getMaxHealth());row.put("current",boss.getHealth());manifest.add(row);
            check("restart_prepared",id,TARGETS.get(id),boss.getMaxHealth());
        }catch(Throwable ex){failure("restart_prepare",id,ex);}
        try{Files.createDirectories(RESULTS);Files.writeString(RESULTS.resolve("restart-manifest.json"),new GsonBuilder().setPrettyPrinting().create().toJson(manifest));}catch(Exception ex){throw new RuntimeException(ex);}
        return finish(source);
    }
    @SuppressWarnings("unchecked")
    private static int verifyRestart(CommandSourceStack source) {
        ServerLevel level=setup(source,"verify_restart");
        try {
            List<Map<String,Object>> rows=new GsonBuilder().create().fromJson(Files.readString(RESULTS.resolve("restart-manifest.json")),List.class);
            for(var row:rows){String id=(String)row.get("entity");Entity entity=level.getEntity(UUID.fromString((String)row.get("uuid")));
                if(!(entity instanceof LivingEntity boss)){failure("restart_entity",id,new IllegalStateException("Saved UUID not loaded"));continue;}
                check("restart_max",id,((Number)row.get("max")).doubleValue(),boss.getMaxHealth());check("restart_wounded",id,((Number)row.get("current")).doubleValue(),boss.getHealth());
                invokeHealth("applyInitialProfile",boss);check("restart_no_rescale",id,((Number)row.get("max")).doubleValue(),boss.getMaxHealth());boss.discard();
            }
        }catch(Throwable ex){failure("restart_verify","all",ex);}
        return finish(source);
    }
}

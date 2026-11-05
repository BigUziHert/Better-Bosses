package survival.balance.tests;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated, visible natural-AI demonstration. Never invokes hurt or any attack method. */
public final class VisibleBossDemo {
    private static final AABB ARENA=new AABB(-40,195,-40,56,285,56);
    private static final Path LOG=Path.of("../tests/results/visible-boss-demo.jsonl");
    private static final Gson JSON=new Gson();
    private static final Set<String> IDS=Set.of("twilightforest:naga","twilightforest:lich","twilightforest:minoshroom","twilightforest:knight_phantom","twilightforest:alpha_yeti","twilightforest:hydra","twilightforest:ur_ghast","twilightforest:snow_queen","bosses_of_mass_destruction:lich","bosses_of_mass_destruction:void_blossom","bosses_of_mass_destruction:gauntlet","bosses_of_mass_destruction:obsidilith","cataclysm:netherite_monstrosity","cataclysm:ender_guardian","cataclysm:the_harbinger","cataclysm:ancient_remnant","cataclysm:the_leviathan","cataclysm:scylla","cataclysm:maledictus","cataclysm:ignis");
    private static ServerPlayer player;
    private static Mob boss;
    private static String bossId="",phase="idle",sourceType="",directType="",sourceOwner="";
    private static boolean running=false,hit=false,pendingRecord=false,arenaBuilt=false;
    private static long ticks=0;
    private static double before=20,after=20,eventDamage=0,armorBefore=20,toughnessBefore=12;

    public static void install() {
        NeoForge.EVENT_BUS.addListener(VisibleBossDemo::register);
        NeoForge.EVENT_BUS.addListener(VisibleBossDemo::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,VisibleBossDemo::incoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,VisibleBossDemo::post);
    }
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bbshow").requires(s->s.hasPermission(4))
            .then(Commands.literal("stage").then(Commands.argument("boss",StringArgumentType.greedyString()).executes(c->stage(c.getSource(),StringArgumentType.getString(c,"boss").trim()))))
            .then(Commands.literal("go").executes(c->go(c.getSource())))
            .then(Commands.literal("distance").then(Commands.argument("blocks",DoubleArgumentType.doubleArg(2,35)).executes(c->{requireStage();boss.moveTo(player.getX(),boss.getY(),player.getZ()+DoubleArgumentType.getDouble(c,"blocks"),180,0);return status(c.getSource());})))
            .then(Commands.literal("status").executes(c->status(c.getSource())))
            .then(Commands.literal("stop").executes(c->{stop(c.getSource().getServer());return status(c.getSource());})));
    }
    private static void guard() { if(!Boolean.getBoolean("survival.bossBalance.testMode"))throw new IllegalStateException("Isolated test-mode JVM required"); }
    private static void requireStage(){guard();if(player==null||boss==null||boss.isRemoved())throw new IllegalStateException("Stage a boss first");}
    private static int stage(CommandSourceStack source,String id) {
        guard();if(!IDS.contains(id))throw new IllegalArgumentException("Not one of the twenty tested bosses: "+id);
        MinecraftServer server=source.getServer();stop(server);
        player=server.getPlayerList().getPlayerByName("TFHealthTest");
        if(player==null||player instanceof FakePlayer)throw new IllegalStateException("Real TFHealthTest client must be connected");
        ServerLevel level=server.overworld();
        server.setDifficulty(Difficulty.HARD,true);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
        level.setDayTime(6000);level.setWeatherParameters(12000,0,false,false);
        prepareArena(level,id.equals("cataclysm:the_leviathan"));
        try {
            player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.removeAllEffects();
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);player.setAbsorptionAmount(0);
            player.getFoodData().setFoodLevel(20);player.getFoodData().setSaturation(20);
            player.setInvulnerable(false);player.getAbilities().invulnerable=false;player.onUpdateAbilities();
            equip(EquipmentSlot.HEAD,Items.NETHERITE_HELMET);equip(EquipmentSlot.CHEST,Items.NETHERITE_CHESTPLATE);
            equip(EquipmentSlot.LEGS,Items.NETHERITE_LEGGINGS);equip(EquipmentSlot.FEET,Items.NETHERITE_BOOTS);
            Method equipment=LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");equipment.setAccessible(true);equipment.invoke(player);
            var spawn=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");spawn.setAccessible(true);spawn.setInt(player,0);
            player.setHealth(20);player.invulnerableTime=0;player.hurtTime=0;player.clearFire();player.stopUsingItem();
            boolean water=id.equals("cataclysm:the_leviathan");
            player.teleportTo(level,8,water?203:201,8,0,0);player.setDeltaMovement(0,0,0);
            if(water)player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,24000,0,false,false));
            Entity created=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id)).create(level);
            if(!(created instanceof Mob mob))throw new IllegalStateException("Boss is not a Mob: "+id);
            boss=mob;bossId=id;boss.moveTo(8,water?203:201,20,180,0);
            boss.finalizeSpawn(level,level.getCurrentDifficultyAt(boss.blockPosition()),MobSpawnType.COMMAND,null);
            boss.setPersistenceRequired();boss.addTag("bbshow_entity");boss.setNoAi(true);boss.setTarget(null);
            VisibleCatSetup.prepare(boss,player);VisibleOtherSetup.prepare(boss,player);
            boss.setNoAi(true);boss.setTarget(null);
            if(!level.addFreshEntity(boss))throw new IllegalStateException("Could not spawn boss");
            phase="staged";ticks=0;running=false;hit=false;pendingRecord=false;before=after=20;eventDamage=0;sourceType=directType=sourceOwner="";
            title("Ready: "+boss.getName().getString(),"Full netherite + Protection IV | 20 HP");
            emit("stage");return status(source);
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private static void equip(EquipmentSlot slot,Item item) {
        ItemStack stack=new ItemStack(item);stack.enchant(player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION),4);player.setItemSlot(slot,stack);
    }
    private static void prepareArena(ServerLevel level,boolean water) {
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
        for(int x=-32;x<48;x++)for(int z=-32;z<48;z++) {
            if(!arenaBuilt){level.setBlock(new BlockPos(x,199,z),Blocks.BEDROCK.defaultBlockState(),2);level.setBlock(new BlockPos(x,200,z),Blocks.STONE.defaultBlockState(),2);}
            else if(!level.getBlockState(new BlockPos(x,200,z)).is(Blocks.STONE))level.setBlock(new BlockPos(x,200,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=201;y<=239;y++)if(!level.getBlockState(new BlockPos(x,y,z)).isAir())level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        arenaBuilt=true;
        if(water)for(int x=-16;x<=32;x++)for(int z=-16;z<=40;z++)for(int y=201;y<=207;y++)level.setBlock(new BlockPos(x,y,z),(x==-16||x==32||z==-16||z==40?Blocks.STONE:Blocks.WATER).defaultBlockState(),2);
    }
    private static int go(CommandSourceStack source) {
        requireStage();if(hit)throw new IllegalStateException("First hit already captured; stage again for another demonstration");
        phase="running";running=true;ticks=0;player.invulnerableTime=0;player.setHealth(20);player.clearFire();
        boss.setNoAi(false);boss.setTarget(player);title(boss.getName().getString(),"Natural boss AI — waiting for its first hit");emit("go");return status(source);
    }
    public static void incoming(LivingIncomingDamageEvent event) {
        if(player==null||event.getEntity()!=player||phase.equals("idle"))return;
        if(!running||hit){event.setCanceled(true);return;}
        if(!isBossSource(event.getSource())){event.setCanceled(true);return;}
        if(event.isCanceled()||event.getAmount()<=0)return;
        before=player.getHealth();armorBefore=player.getAttributeValue(Attributes.ARMOR);toughnessBefore=player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
    }
    public static void post(LivingDamageEvent.Post event) {
        if(!running||hit||player==null||event.getEntity()!=player||event.getNewDamage()<=0||!isBossSource(event.getSource()))return;
        hit=true;running=false;phase="paused_after_first_hit";pendingRecord=true;eventDamage=event.getNewDamage();
        sourceType=type(event.getSource());directType=entityType(event.getSource().getDirectEntity());sourceOwner=entityType(event.getSource().getEntity());
        freeze();
    }
    public static void tick(ServerTickEvent.Post event) {
        if(player==null||boss==null||phase.equals("idle"))return;
        if(player.isRemoved()||boss.isRemoved()){running=false;return;}
        if(running)ticks++;
        else {boss.setDeltaMovement(0,0,0);boss.getNavigation().stop();}
        if(pendingRecord){pendingRecord=false;after=player.getHealth();emit("first_hit");title(boss.getName().getString(),String.format("Hit: %.3f hearts | HP %.3f → %.3f",(before-after)/2,before,after));}
        if(event.getServer().getTickCount()%20==0)player.displayClientMessage(Component.literal(phase.equals("paused_after_first_hit")?String.format("%s | %.3f hearts lost | %.3f HP remaining | AI paused",boss.getName().getString(),(before-after)/2,after):boss.getName().getString()+" | "+phase+" | "+player.getHealth()+" HP | Netherite Protection IV"),true);
    }
    private static void freeze() {
        if(boss!=null){boss.setNoAi(true);boss.setTarget(null);boss.getNavigation().stop();}
        if(player!=null)for(Entity e:player.serverLevel().getAllEntities())if(e instanceof Mob mob&&ARENA.contains(e.position())){mob.setNoAi(true);mob.setTarget(null);}
    }
    private static boolean isBossSource(DamageSource source) {
        if(boss==null)return false;
        if(owns(source.getEntity(),0)||owns(source.getDirectEntity(),0))return true;
        String type=type(source);
        if(bossId.equals("twilightforest:alpha_yeti")&&type.equals("twilightforest:falling_ice"))return true;
        return bossId.equals("twilightforest:ur_ghast")&&type.contains("explosion")&&StackWalker.getInstance().walk(s->s.anyMatch(f->f.getClassName().equals("twilightforest.entity.projectile.UrGhastFireball")));
    }
    private static boolean owns(Entity e,int depth) {
        if(e==null||depth>5||e instanceof ServerPlayer)return false;
        if(e==boss||entityType(e).equals(bossId))return true;
        if(e instanceof Projectile p&&owns(p.getOwner(),depth+1))return true;
        for(String method:List.of("getOwner","getCaster","getShooter","getParent","getMaster"))try{Object owner=e.getClass().getMethod(method).invoke(e);if(owner instanceof Entity other&&other!=e&&owns(other,depth+1))return true;}catch(Exception ignored){}
        return false;
    }
    private static String type(DamageSource source){return source.typeHolder().unwrapKey().map(k->k.location().toString()).orElse("");}
    private static String entityType(Entity entity){return entity==null?"":BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();}
    private static Map<String,Object> state() {
        Map<String,Object> row=new LinkedHashMap<>();row.put("phase",phase);row.put("bossId",bossId);row.put("elapsedSeconds",ticks/20d);row.put("firstHit",hit);
        if(boss!=null){row.put("bossUUID",boss.getUUID().toString());row.put("bossHP",boss.getHealth());row.put("bossMaxHP",boss.getMaxHealth());row.put("bossPosition",boss.position().toString());row.put("nativeSetup",boss.getPersistentData().getString("visible_demo_setup"));}
        if(player!=null){row.put("player",player.getGameProfile().getName());row.put("realConnectedPlayer",!(player instanceof FakePlayer));row.put("playerHP",player.getHealth());row.put("armor",player.getAttributeValue(Attributes.ARMOR));row.put("toughness",player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            List<Integer> levels=new ArrayList<>();List<String> gear=new ArrayList<>();var protection=player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
            for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){ItemStack stack=player.getItemBySlot(slot);gear.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());levels.add(stack.getEnchantmentLevel(protection));}row.put("armorItems",gear);row.put("protectionLevels",levels);}
        row.put("beforePlayerHP",before);row.put("afterPlayerHP",after);row.put("heartsLost",(before-after)/2);row.put("damageEventHP",eventDamage);row.put("armorBefore",armorBefore);row.put("toughnessBefore",toughnessBefore);row.put("damageType",sourceType);row.put("directEntityType",directType);row.put("sourceOwnerType",sourceOwner);return row;
    }
    private static void title(String heading,String subtitle) {if(player==null)return;player.connection.send(new ClientboundSetTitlesAnimationPacket(5,90,10));player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(heading)));player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));}
    private static int status(CommandSourceStack source){String json=JSON.toJson(state());source.sendSuccess(()->Component.literal("BBSHOW "+json),true);return 1;}
    private static void emit(String kind){Map<String,Object> row=state();row.put("record",kind);row.put("timestamp",java.time.Instant.now().toString());String json=JSON.toJson(row);System.out.println("BBSHOW "+json);try{Files.createDirectories(LOG.getParent());Files.writeString(LOG,json+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ex){ex.printStackTrace();}}
    private static void stop(MinecraftServer server) {
        running=false;pendingRecord=false;freeze();ServerLevel level=server.overworld();
        List<Entity> discard=new ArrayList<>();for(Entity e:level.getAllEntities())if(!(e instanceof ServerPlayer)&&(ARENA.contains(e.position())||e.getTags().contains("bbshow_entity")))discard.add(e);
        for(Entity e:discard)e.discard();boss=null;phase="idle";hit=false;
    }
}

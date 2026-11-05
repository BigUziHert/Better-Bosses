package survival.balance.tests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Continuous native-AI encounters with a disclosed automated input controller.
 * No damage/health/phase/cooldown rewrites are permitted after encounter start.
 * This is NOT a human playthrough or proof that every move is naturally selected.
 */
public final class FullFightCases {
    private static final Gson JSON=new GsonBuilder().serializeSpecialFloatingPointValues().create();
    private static final List<String> BOSSES=List.of(
        "twilightforest:naga","twilightforest:lich","twilightforest:minoshroom",
        "twilightforest:knight_phantom","twilightforest:alpha_yeti","twilightforest:hydra",
        "twilightforest:ur_ghast","twilightforest:snow_queen",
        "bosses_of_mass_destruction:lich","bosses_of_mass_destruction:void_blossom",
        "bosses_of_mass_destruction:gauntlet","bosses_of_mass_destruction:obsidilith",
        "cataclysm:netherite_monstrosity","cataclysm:ender_guardian","cataclysm:the_harbinger",
        "cataclysm:ancient_remnant","cataclysm:the_leviathan","cataclysm:scylla",
        "cataclysm:maledictus","cataclysm:ignis","minecraft:wither",
        "minecraft:ender_dragon","minecraft:warden");
    private record Request(String boss,String gear,int ticks,String playerName){}
    private static final ArrayDeque<Request> QUEUE=new ArrayDeque<>();
    private static final List<Map<String,Object>> FINISHED=new ArrayList<>();
    private static CommandSourceStack source;
    private static Encounter current;
    private static Path output;
    private static boolean installed;
    private static int serial;
    private static ServerPlayer awaitingRespawn;
    private record CrystalSetup(Vec3 position,boolean showBottom,boolean invulnerable){}
    private static List<CrystalSetup> initialCrystals;

    public static void install(){
        if(installed)return;installed=true;
        NeoForge.EVENT_BUS.addListener(FullFightCases::pre);
        NeoForge.EVENT_BUS.addListener(FullFightCases::post);
        NeoForge.EVENT_BUS.addListener(FullFightCases::damage);
        NeoForge.EVENT_BUS.addListener(FullFightCases::joined);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,FullFightCases::death);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,FullFightCases::heal);
    }
    public static int start(CommandSourceStack sender,String boss,String gear,int maxTicks){
        return startQueue(sender,boss,gear,maxTicks,null);
    }
    public static int startClient(CommandSourceStack sender,String boss,String gear,int maxTicks,String playerName){
        if(sender.getServer().getPlayerList().getPlayerByName(playerName)==null)throw new IllegalArgumentException("Client player is not connected: "+playerName);
        return startQueue(sender,boss,gear,maxTicks,playerName);
    }
    private static int startQueue(CommandSourceStack sender,String boss,String gear,int maxTicks,String playerName){
        guard();if(current!=null||!QUEUE.isEmpty())throw new IllegalStateException("A fight queue is already running; cancel it first");
        if(maxTicks<200||maxTicks>24000)throw new IllegalArgumentException("Fight duration must be 200..24000 ticks");
        List<String> ids=boss.equals("all")?BOSSES:List.of(boss);
        for(String id:ids)if(!BOSSES.contains(id))throw new IllegalArgumentException("Unknown managed boss: "+id);
        List<String> gears=gear.equals("both")||gear.equals("all")?List.of("diamond","netherite_p4"):List.of(normalizeGear(gear));
        source=sender;FINISHED.clear();serial=0;awaitingRespawn=null;initialCrystals=null;
        output=Path.of("../tests/results/full-fights",Instant.now().toString().replace(':','-'));
        for(String chosen:gears)for(String id:ids)QUEUE.add(new Request(id,chosen,maxTicks,playerName));
        sender.getServer().setDifficulty(Difficulty.HARD,true);
        sender.sendSuccess(()->Component.literal("BBFIGHT_QUEUE started "+QUEUE.size()+" native-AI encounters; 20 HP; controller mode recorded; no human-play claim"),true);
        return QUEUE.size();
    }
    public static int status(CommandSourceStack sender){
        guard();String text="BBFIGHT_STATUS "+JSON.toJson(current==null?Map.of("state",awaitingRespawn==null?"idle":"waiting_for_native_respawn","queued",QUEUE.size(),"finished",FINISHED.size()):current.snapshot("status"));
        sender.sendSuccess(()->Component.literal(text),false);return 1;
    }
    public static int cancel(CommandSourceStack sender){
        guard();QUEUE.clear();if(current!=null)finish("cancelled","Explicit command cancellation");
        sender.sendSuccess(()->Component.literal("BBFIGHT_CANCELLED"),true);return 1;
    }
    private static void guard(){if(!Boolean.getBoolean("survival.bossBalance.testMode"))throw new IllegalStateException("Isolated test-mode JVM required");}
    private static String normalizeGear(String gear){return switch(gear){
        case "diamond","plain_diamond" -> "diamond";
        case "netherite","netherite_p4" -> "netherite_p4";
        default -> throw new IllegalArgumentException("Gear must be diamond, netherite_p4, or both");};}

    private static void pre(ServerTickEvent.Pre event){
        if(source==null||event.getServer()!=source.getServer())return;
        try {
            if(current==null&&!QUEUE.isEmpty()){
                Request next=QUEUE.peekFirst();
                ServerPlayer client=next.playerName==null?null:source.getServer().getPlayerList().getPlayerByName(next.playerName);
                if(next.playerName==null||client!=null&&client.isAlive()&&client!=awaitingRespawn){awaitingRespawn=null;current=new Encounter(QUEUE.removeFirst(),++serial);}
            }
            if(current!=null)current.beforeTick();
        }catch(Throwable error){if(current!=null){current.emit("controller_error",Map.of("error",error.toString()));finish("controller_error",error.toString());}else{QUEUE.clear();error.printStackTrace();}}
    }
    private static void post(ServerTickEvent.Post event){
        if(current==null||event.getServer()!=source.getServer()||!current.running)return;
        try{current.afterTick();}catch(Throwable error){current.emit("controller_error",Map.of("error",error.toString()));finish("controller_error",error.toString());}
    }
    private static void damage(LivingDamageEvent.Post event){
        Encounter fight=current;if(fight==null||!fight.running)return;
        if(event.getEntity()!=fight.player&&!fight.bosses.contains(event.getEntity()))return;
        fight.lastDamageTick=fight.ticks;
        String kind=event.getEntity()==fight.player?"player_damage":"boss_damage";
        if(event.getEntity()==fight.player){fight.damageTaken+=event.getNewDamage();fight.hitsTaken++;}
        else {fight.damageDealt+=event.getNewDamage();fight.hitsDealt++;}
        fight.emit(kind,Map.of("damage",event.getNewDamage(),"victim",id(event.getEntity()),"hpAfter",event.getEntity().getHealth(),
            "damageType",event.getSource().typeHolder().unwrapKey().map(k->k.location().toString()).orElse("unknown"),
            "direct",id(event.getSource().getDirectEntity()),"owner",id(event.getSource().getEntity())));
    }
    private static void joined(EntityJoinLevelEvent event){
        Encounter fight=current;
        if(fight!=null&&fight.running&&event.getLevel()==fight.level&&!(event.getEntity() instanceof ServerPlayer)&&fight.arena.contains(event.getEntity().position()))fight.spawned.add(event.getEntity().getUUID());
    }
    private static void death(LivingDeathEvent event){
        Encounter fight=current;if(fight==null||!fight.running)return;
        if(event.getEntity()==fight.player){fight.lethalSeen=true;fight.lethalCancelled=event.isCanceled();
            fight.emit("player_lethal_event",Map.of("cancelledByMod",event.isCanceled(),"source",event.getSource().getMsgId()));}
        else if(fight.bosses.contains(event.getEntity())){
            if(!event.isCanceled())fight.deadBosses.add(event.getEntity().getUUID());
            fight.emit("boss_death_event",Map.of("entity",id(event.getEntity()),"cancelled",event.isCanceled()));
        }
    }
    private static void heal(LivingHealEvent event){
        Encounter fight=current;if(fight==null||!fight.running)return;
        if(event.getEntity()==fight.player||fight.bosses.contains(event.getEntity()))
            fight.emit("native_heal",Map.of("entity",id(event.getEntity()),"amount",event.getAmount(),"cancelled",event.isCanceled()));
    }
    private static void finish(String outcome,String reason){
        Encounter fight=current;if(fight==null)return;
        Map<String,Object> summary=fight.snapshot("finished");summary.put("outcome",outcome);summary.put("reason",reason);
        summary.put("damageTaken",fight.damageTaken);summary.put("damageDealt",fight.damageDealt);
        summary.put("hitsTaken",fight.hitsTaken);summary.put("hitsDealt",fight.hitsDealt);
        summary.put("deathOrDowningObserved",fight.lethalSeen||fight.player!=null&&!fight.player.isAlive());
        summary.put("scope",fight.clientMode?"Real Minecraft client connection with automated client controls; continuous native server AI. No human-play or all-move coverage claim.":"Automated input controller and simulated connection; continuous native server AI, not human play. No guaranteed wins or all-move coverage.");
        if(fight.clientMode&&outcome.startsWith("lost"))awaitingRespawn=fight.player;
        FINISHED.add(summary);fight.emit("finished",summary);fight.cleanup();current=null;
        try{Files.createDirectories(output);Files.writeString(output.resolve("summary.json"),new GsonBuilder().setPrettyPrinting().create().toJson(FINISHED));}
        catch(Exception error){throw new RuntimeException(error);}
        System.out.println("BBFIGHT_FINISHED "+JSON.toJson(summary));
        if(source!=null)source.sendSuccess(()->Component.literal("BBFIGHT_FINISHED "+fight.request.boss+" "+fight.request.gear+" "+outcome+" ticks="+fight.ticks+" remaining="+QUEUE.size()),true);
    }

    private static final class Encounter {
        final Request request;final ServerLevel level;final Path log;final int cx,cz,floor;
        final List<LivingEntity> bosses=new ArrayList<>();final Set<UUID> existing=new HashSet<>(),deadBosses=new HashSet<>(),spawned=new HashSet<>();
        final List<Long> forced=new ArrayList<>(),chunksToLoad=new ArrayList<>();final List<String> setupNotes=new ArrayList<>();
        final boolean water,dragonFight,clientMode;final AABB arena;
        ServerPlayer player;int buildCursor,chunkCursor,ticks,drawTicks,lastDamageTick,lastSample;boolean running,lethalSeen,lethalCancelled;
        double damageTaken,damageDealt;int hitsTaken,hitsDealt;String lastPhase="";boolean oldRegen,oldSpawning,oldGriefing;
        BlockPos miningBlock;float miningProgress;
        Encounter(Request request,int number)throws Exception{
            this.request=request;clientMode=request.playerName!=null;water=request.boss.equals("cataclysm:the_leviathan");dragonFight=request.boss.equals("minecraft:ender_dragon");
            level=dragonFight?source.getServer().getLevel(Level.END):source.getServer().overworld();
            if(level==null)throw new IllegalStateException("Required dimension unavailable");
            cx=dragonFight?0:1536;cz=dragonFight?0:1536;floor=dragonFight?64:-60;
            arena=new AABB(cx-96,floor-20,cz-96,cx+96,floor+160,cz+96);
            log=output.resolve(String.format("%02d-%s-%s.jsonl",number,request.boss.replace(':','_'),request.gear));
            for(Entity entity:level.getAllEntities())if(arena.contains(entity.position()))existing.add(entity.getUUID());
            int radius=dragonFight?8:4;
            for(int x=(cx>>4)-radius;x<=(cx>>4)+radius;x++)for(int z=(cz>>4)-radius;z<=(cz>>4)+radius;z++){
                long key=net.minecraft.world.level.ChunkPos.asLong(x,z);
                chunksToLoad.add(key);
            }
            var rules=level.getGameRules();oldRegen=rules.getBoolean(GameRules.RULE_NATURAL_REGENERATION);oldSpawning=rules.getBoolean(GameRules.RULE_DOMOBSPAWNING);oldGriefing=rules.getBoolean(GameRules.RULE_MOBGRIEFING);
            rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(true,source.getServer());
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,source.getServer());
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(true,source.getServer());
            setupNotes.add("Hard; natural food regeneration enabled; incidental natural mob spawning off; mob griefing on. Non-End arena stone over bedrock at the flat world's ground level (-60), so leaving its edge adds no artificial elevated fall.");
            setupNotes.add(clientMode?"Real client connection; this server harness does not set movement, aim, item-use or attack inputs. An external client controller supplies them.":"Simulated connection: server inventory, player ticks, movement inputs, collision, hunger, cooldowns, projectile travel, armor durability and lethal outcomes are native. Network latency and human choices are not represented.");
            if(clientMode)setupNotes.add("Real-client fights retain the requested overall tick limit; no early no-HP-damage cutoff, because shield counters, native summons, crystals and other protected-phase progress need not damage a managed boss.");
            emit("preparing",Map.of());
        }
        void beforeTick()throws Exception{
            if(!running){if(build())spawn();return;}
            ticks++;
            if(lethalSeen||!player.isAlive()||player.isRemoved()){finish(lethalCancelled?"lost_downed":"lost",lethalCancelled?"A native lethal event was cancelled by a mod (downing/other death replacement)":"Player died");return;}
            if(clientMode)return;
            control();
            // Real clients advance this through their connection tick. Fake connections do not.
            // doTick calls Player.tick once, including food/effects/item use/attack cooldowns.
            player.doTick();
        }
        boolean build(){
            if(chunkCursor<chunksToLoad.size()){
                long key=chunksToLoad.get(chunkCursor++);var pos=new net.minecraft.world.level.ChunkPos(key);
                if(!level.getForcedChunks().contains(key)){level.setChunkForced(pos.x,pos.z,true);forced.add(key);}
                level.getChunk(pos.x,pos.z);return false;
            }
            if(dragonFight)return true; // Actual End terrain, towers and fight manager are used.
            final int side=96,height=42,total=side*side*height;
            for(int n=0;n<8192&&buildCursor<total;n++,buildCursor++){
                int x=buildCursor%side-side/2,z=(buildCursor/side)%side-side/2,y=buildCursor/(side*side);
                BlockPos pos=new BlockPos(cx+x,floor-1+y,cz+z);
                var state=y==0?Blocks.BEDROCK.defaultBlockState():y==1?Blocks.STONE.defaultBlockState():
                    water&&y<=10&&Math.abs(x)<40&&Math.abs(z)<40?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState();
                if(level.getBlockState(pos)!=state)level.setBlock(pos,state,2);
            }
            return buildCursor>=total;
        }
        void spawn()throws Exception{
            for(Entity entity:level.getAllEntities())if(arena.contains(entity.position()))existing.add(entity.getUUID());
            player=clientMode?source.getServer().getPlayerList().getPlayerByName(request.playerName):new FightPlayer(level,new GameProfile(UUID.randomUUID(),"BBFightBot"));
            if(player==null||!player.isAlive())throw new IllegalStateException("Client disconnected or died while preparing encounter");
            if(!clientMode)set(player.connection.getConnection(),"channel",new io.netty.channel.embedded.EmbeddedChannel());
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);player.getAbilities().invulnerable=false;player.getAbilities().flying=false;player.getAbilities().mayfly=false;
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);player.setHealth(20);player.setAbsorptionAmount(0);
            player.getFoodData().setFoodLevel(20);player.getFoodData().setSaturation(5);set(player,"spawnInvulnerableTime",0);
            if(clientMode)player.teleportTo(level,cx,water?floor+9:floor+1,cz-14,0,0);
            else player.moveTo(cx,water?floor+9:floor+1,cz-14,0,0);
            player.removeAllEffects();
            player.setNoGravity(false);player.setOnGround(!water);
            equip();if(!clientMode)level.addNewPlayer(player);
            setupNotes.add("Initial setup clears prior effects, including login/dimension-transition immunity; combat represents an established survival session. No effect is cleared after combat begins.");
            if(dragonFight){
                for(EnderDragon old:level.getEntitiesOfClass(EnderDragon.class,arena))old.discard();
                var crystals=level.getEntitiesOfClass(EndCrystal.class,arena);
                if(initialCrystals==null)initialCrystals=crystals.stream().map(c->new CrystalSetup(c.position(),c.showsBottom(),c.isInvulnerable())).toList();
                else {
                    for(EndCrystal crystal:crystals)crystal.discard();
                    for(CrystalSetup saved:initialCrystals){EndCrystal crystal=new EndCrystal(level,saved.position.x,saved.position.y,saved.position.z);crystal.setShowBottom(saved.showBottom);crystal.setInvulnerable(saved.invulnerable);level.addFreshEntity(crystal);}
                }
                setupNotes.add("Any preexisting End dragon is removed during setup to prevent duplicates; "+initialCrystals.size()+" original tower crystals are restored before each gear trial.");
                if(initialCrystals.isEmpty())setupNotes.add("LIMITATION: no original End crystals were present; this run cannot validate crystal removal/healing mechanics.");
            }
            int count=request.boss.equals("twilightforest:knight_phantom")?6:1;
            for(int i=0;i<count;i++){
                Entity created=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(request.boss)).create(level);
                if(!(created instanceof LivingEntity boss))throw new IllegalStateException("Unknown/nonliving boss");
                boss.moveTo(cx+(i%3-1)*4,water?floor+6:dragonFight?floor+28:floor+1,cz+12+(i/3)*4,180,0);
                if(boss instanceof Mob mob){mob.finalizeSpawn(level,level.getCurrentDifficultyAt(boss.blockPosition()),MobSpawnType.COMMAND,null);mob.setPersistenceRequired();mob.setNoAi(false);}
                boss.setInvulnerable(false);boss.addTag("bbfight_entity");
                if(request.boss.equals("twilightforest:knight_phantom"))call(boss,"setNumber",new Class[]{int.class},i);
                if(request.boss.startsWith("twilightforest:"))call(boss,"setRestrictionPoint",new Class[]{GlobalPos.class},GlobalPos.of(level.dimension(),new BlockPos(cx,floor+1,cz)));
                if(request.boss.equals("twilightforest:ur_ghast"))boss.setPos(boss.getX(),floor+14,boss.getZ());
                if(request.boss.equals("bosses_of_mass_destruction:lich"))boss.setPos(boss.getX(),floor+9,boss.getZ());
                if(request.boss.startsWith("bosses_of_mass_destruction:"))set(boss,"idlePosition",boss.position());
                if(!level.addFreshEntity(boss))throw new IllegalStateException("Boss spawn rejected");
                bosses.add(boss);
            }
            activate();
            for(LivingEntity boss:bosses)if(boss instanceof Mob mob)mob.setTarget(player); // Initial encounter acquisition only; later target choices remain native.
            setupNotes.add("Initial boss spawn/home/target supplied once. Lich shields, Snow Queen summons and all later attack phases remain at native values.");
            setupNotes.add("No potions, golden apples, extra HP, damage immunity or post-start health/cooldown resets supplied.");
            running=true;emit("started",Map.of("loadout",gearItems()));
        }
        void equip()throws Exception{
            boolean netherite=request.gear.equals("netherite_p4");
            Item[] items=netherite?new Item[]{Items.NETHERITE_HELMET,Items.NETHERITE_CHESTPLATE,Items.NETHERITE_LEGGINGS,Items.NETHERITE_BOOTS}:new Item[]{Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS};
            EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
            for(int i=0;i<4;i++){ItemStack stack=new ItemStack(items[i]);if(netherite)stack.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION),4);player.setItemSlot(slots[i],stack);}
            player.getInventory().setItem(0,new ItemStack(netherite?Items.NETHERITE_SWORD:Items.DIAMOND_SWORD));
            ItemStack bow=new ItemStack(Items.BOW);if(netherite)bow.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.POWER),5);
            player.getInventory().setItem(1,bow);player.getInventory().setItem(2,new ItemStack(Items.COOKED_BEEF,64));
            for(int i=9;i<13;i++)player.getInventory().setItem(i,new ItemStack(Items.ARROW,64));
            player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));player.getInventory().selected=0;
            call(player,"detectEquipmentUpdates",new Class[0]);
        }
        void activate()throws Exception{
            LivingEntity boss=bosses.getFirst();
            switch(request.boss){
                case "minecraft:wither" -> {((WitherBoss)boss).makeInvulnerable();setupNotes.add("Wither begins with native spawn charge; avatar must survive/dodge the spawn explosion.");}
                case "minecraft:ender_dragon" -> {
                    EndDragonFight fight=level.getDragonFight();if(fight==null)throw new IllegalStateException("The End has no native fight manager");
                    set(fight,"dragonUUID",boss.getUUID());set(fight,"dragonKilled",false);set(fight,"needsStateScanning",false);
                    try{call(fight,"setHasDragonEverSpawned",new Class[]{boolean.class},true);}catch(NoSuchMethodException vanilla){}
                    ((EnderDragon)boss).setDragonFight(fight);
                    setupNotes.add("Uses real End level/fight manager and generated towers/crystals. The completed initial-spawn flag is supplied once; controller prioritizes existing crystals.");
                }
                case "cataclysm:the_harbinger" -> interact(boss,"minecraft:nether_star");
                case "cataclysm:ancient_remnant" -> interact(boss,"cataclysm:necklace_of_the_desert");
                case "cataclysm:scylla" -> interact(boss,null);
                case "cataclysm:netherite_monstrosity" -> {call(boss,"setIsAwaken",new Class[]{boolean.class},true);setupNotes.add("Monstrosity awake flag supplied once; native awakening/attack states are not skipped.");}
                case "bosses_of_mass_destruction:gauntlet" -> {
                    Object handler=field(boss,"nbtHandler");net.minecraft.nbt.CompoundTag tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean("isAggroed",true);
                    call(handler,"fromTag",new Class[]{net.minecraft.nbt.CompoundTag.class},tag);
                    setupNotes.add("Gauntlet native saved awake state installed once because the arena lacks its original summoning structure.");
                }
                default -> {}
            }
        }
        void interact(LivingEntity boss,String itemId)throws Exception{
            ItemStack saved=player.getMainHandItem();
            player.setItemInHand(InteractionHand.MAIN_HAND,itemId==null?ItemStack.EMPTY:new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))));
            Object result=call(boss,"mobInteract",new Class[]{net.minecraft.world.entity.player.Player.class,InteractionHand.class},player,InteractionHand.MAIN_HAND);
            player.setItemInHand(InteractionHand.MAIN_HAND,saved);
            setupNotes.add("Native activation interaction: "+(itemId==null?"empty hand":itemId)+" result="+result+"; setup distance bypasses client targeting only.");
        }
        void control(){
            Entity target=chooseTarget();if(target==null){player.zza=player.xxa=0;return;}
            Vec3 eye=player.getEyePosition(),aim=target.getBoundingBox().getCenter();
            double horizontal=Math.sqrt(Math.pow(aim.x-eye.x,2)+Math.pow(aim.z-eye.z,2));
            double reach=target.getBoundingBox().distanceToSqr(eye);
            boolean ranged=reach>9||target instanceof EndCrystal;
            if(ranged)aim=aim.add(target.getDeltaMovement().scale(Math.min(12,horizontal/2))).add(0,horizontal*.10,0);
            face(aim);
            // Native travel consumes these ordinary movement inputs. No teleports or velocity resets.
            player.zza=horizontal>5?.8f:horizontal<2?-.5f:.12f;
            player.xxa=((ticks/60)%2==0?.45f:-.45f);player.setSprinting(horizontal>12&&!player.isUsingItem());
            player.setJumping(player.horizontalCollision&&player.onGround()||water&&player.getAirSupply()<120);
            if(player.isPassenger()){player.zza=player.xxa=0;}
            if(player.isUsingItem()){
                if(player.getUseItem().is(Items.BOW)){
                    drawTicks++;if(drawTicks>=24){player.releaseUsingItem();emit("bow_release",Map.of("target",id(target),"drawTicks",drawTicks));drawTicks=0;}
                }else if(player.getUseItem().is(Items.SHIELD)&&ticks%36==0)player.stopUsingItem();
                return;
            }
            if(player.getFoodData().getFoodLevel()<=16&&!player.getInventory().getItem(2).isEmpty()){
                select(2);player.getMainHandItem().use(level,player,InteractionHand.MAIN_HAND);emit("food_use",Map.of("food",player.getFoodData().getFoodLevel()));return;
            }
            if(controlBlockMechanic())return;
            Entity reflect=level.getEntities(player,player.getBoundingBox().inflate(3),e->e instanceof Projectile p&&p.getOwner()!=player&&e.isPickable()&&e.getBoundingBox().distanceToSqr(player.getEyePosition())<=9&&player.hasLineOfSight(e)).stream().min(java.util.Comparator.comparingDouble(e->e.distanceToSqr(player))).orElse(null);
            if(reflect!=null&&player.getAttackStrengthScale(.5f)>.9f){face(reflect.getBoundingBox().getCenter());if(select(0))return;player.attack(reflect);player.swing(InteractionHand.MAIN_HAND);emit("projectile_counter_attempt",Map.of("projectile",id(reflect)));return;}
            // Shield only part of each cycle: blocking can prevent attacking and should not become permanent immunity.
            if(ticks%60>=40&&horizontal<12&&!player.getOffhandItem().isEmpty()&&!player.getCooldowns().isOnCooldown(Items.SHIELD)){
                player.getOffhandItem().use(level,player,InteractionHand.OFF_HAND);emit("shield_raise",Map.of());return;
            }
            if(!ranged&&player.hasLineOfSight(target)){
                if(select(0))return;float strength=player.getAttackStrengthScale(.5f);if(strength>=.98f){player.attack(target);player.swing(InteractionHand.MAIN_HAND);emit("melee_input",Map.of("target",id(target),"cooldownBefore",strength));}
            }else if(player.hasLineOfSight(target)&&!player.getInventory().getItem(1).isEmpty()){
                select(1);player.getMainHandItem().use(level,player,InteractionHand.MAIN_HAND);drawTicks=0;
            }
        }
        boolean select(int slot){if(player.getInventory().selected==slot)return false;player.getInventory().selected=slot;return true;}
        boolean controlBlockMechanic(){
            if(!request.boss.equals("bosses_of_mass_destruction:obsidilith"))return false;
            List<BlockPos> pillars=new ArrayList<>();
            try{Object value=field(bosses.getFirst(),"activePillars");if(value instanceof List<?> list)for(Object entry:list)if(entry instanceof BlockPos pos&&!level.getBlockState(pos).isAir())pillars.add(pos);}catch(Exception error){throw new IllegalStateException("Cannot observe native Obsidilith pillars",error);}
            BlockPos target=pillars.stream().min(java.util.Comparator.comparingDouble(p->p.distToCenterSqr(player.position()))).orElse(null);
            if(target==null){abortMining();return false;}
            Vec3 aim=target.getCenter();face(aim);double distance=aim.distanceTo(player.getEyePosition());
            player.zza=distance>3.2?.8f:0;player.xxa=distance>4?.18f:0;player.setSprinting(distance>10);player.setJumping(player.horizontalCollision&&player.onGround());
            var hit=level.clip(new ClipContext(player.getEyePosition(),aim,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
            if(distance>player.blockInteractionRange()||hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(target)){abortMining();return true;}
            if(select(0))return true;
            if(!target.equals(miningBlock)){
                abortMining();miningBlock=target;miningProgress=0;
                player.gameMode.handleBlockBreakAction(target,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,hit.getDirection(),level.getMaxBuildHeight(),ticks);
                emit("block_break_start",Map.of("block",BuiltInRegistries.BLOCK.getKey(level.getBlockState(target).getBlock()).toString(),"position",List.of(target.getX(),target.getY(),target.getZ())));
            }
            player.swing(InteractionHand.MAIN_HAND);
            miningProgress+=level.getBlockState(target).getDestroyProgress(player,level,target);
            if(miningProgress>=1||level.getBlockState(target).isAir()){
                player.gameMode.handleBlockBreakAction(target,ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,hit.getDirection(),level.getMaxBuildHeight(),ticks);
                emit("block_break_stop",Map.of("position",List.of(target.getX(),target.getY(),target.getZ()),"progress",miningProgress,"removed",level.getBlockState(target).isAir()));miningBlock=null;miningProgress=0;
            }
            return true;
        }
        void abortMining(){if(miningBlock!=null){player.gameMode.handleBlockBreakAction(miningBlock,ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,Direction.UP,level.getMaxBuildHeight(),ticks);miningBlock=null;miningProgress=0;}}
        void face(Vec3 point){Vec3 d=point.subtract(player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));float pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z)));player.setYRot(yaw);player.setXRot(pitch);player.yHeadRot=yaw;player.yBodyRot=yaw;}
        Entity chooseTarget(){
            if(dragonFight){var crystals=level.getEntitiesOfClass(EndCrystal.class,arena,e->e.isAlive());if(!crystals.isEmpty())return crystals.stream().min(java.util.Comparator.comparingDouble(e->e.distanceToSqr(player))).orElseThrow();}
            if(request.boss.equals("twilightforest:hydra"))try{
                List<Entity> openHeads=new ArrayList<>(),aliveHeads=new ArrayList<>();
                for(Object head:(Object[])field(bosses.getFirst(),"hc"))if(!(boolean)call(head,"isDead",new Class[0])){
                    Entity entity=(Entity)field(head,"headEntity");aliveHeads.add(entity);
                    if(((Number)call(head,"getCurrentMouthOpen",new Class[0])).doubleValue()>.5)openHeads.add(entity);
                }
                List<Entity> targets=openHeads.isEmpty()?aliveHeads:openHeads;
                if(!targets.isEmpty())return targets.stream().min(java.util.Comparator.comparingDouble(e->e.distanceToSqr(player))).orElseThrow();
            }catch(Exception error){throw new IllegalStateException("Cannot observe native Hydra weak points",error);}
            List<Entity> targets=new ArrayList<>();
            for(LivingEntity boss:bosses)if(boss.isAlive()&&!boss.isRemoved()){
                if(boss.isMultipartEntity()&&boss.getParts()!=null){
                    if(boss.isPickable())targets.add(boss);
                    for(Entity part:boss.getParts())if(part.isPickable()&&!part.getClass().getSimpleName().equals("SnowQueenIceShield"))targets.add(part);
                }
                else targets.add(boss);
            }
            // Summoned hostile minions remain real participants, and can obstruct or kill the avatar.
            for(Mob mob:level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(8),m->m.isAlive()&&m.getTarget()==player&&!bosses.contains(m)))targets.add(mob);
            return targets.stream().min(java.util.Comparator.comparingDouble(e->e.getBoundingBox().distanceToSqr(player.getEyePosition()))).orElse(null);
        }
        void afterTick(){
            if(lethalSeen||!player.isAlive()){finish(lethalCancelled?"lost_downed":"lost","Native lethal outcome observed; no respawn or health reset");return;}
            if(bosses.stream().allMatch(b->b.isDeadOrDying()||deadBosses.contains(b.getUUID()))){finish("won","All encounter bosses reached native death");return;}
            if(bosses.stream().anyMatch(b->b.isRemoved()&&!b.isDeadOrDying()&&!deadBosses.contains(b.getUUID()))){finish("boss_removed","A boss disappeared without observed native death; not counted as a win");return;}
            String phase=JSON.toJson(phases());if(!phase.equals(lastPhase)){lastPhase=phase;emit("native_phase_change",Map.of("states",phases()));}
            if(ticks-lastSample>=20){lastSample=ticks;emit("sample",Map.of());}
            if(ticks>=request.ticks){finish("time_limit","Bounded continuous fight ended without a win/loss; unfinished mechanics are not counted as passed");return;}
            if(!clientMode&&ticks-lastDamageTick>=1200&&ticks>=1200){finish("stalled","60 seconds without accepted damage to player or managed bosses; controller/arena limitations remain visible");}
        }
        Map<String,Object> phases(){
            Map<String,Object> states=new LinkedHashMap<>();
            for(int i=0;i<bosses.size();i++){
                LivingEntity boss=bosses.get(i);Map<String,Object> values=new LinkedHashMap<>();
                for(String getter:List.of("getPhase","getAttackState","getShieldStrength","getMinionsToSummon","getCurrentPhase","getRage","getRageMeter","getMeltDown","isPowered","getInvulnerableTicks","getAnimation"))try{
                    Object value=call(boss,getter,new Class[0]);if(value!=null)values.put(getter,value.toString());
                }catch(Exception absent){}
                if(boss instanceof EnderDragon dragon)values.put("dragonPhase",dragon.getPhaseManager().getCurrentPhase().getPhase().toString());
                states.put(id(boss)+"#"+i,values);
            }return states;
        }
        List<String> gearItems(){List<String> gear=new ArrayList<>();for(EquipmentSlot slot:EquipmentSlot.values())if(!player.getItemBySlot(slot).isEmpty())gear.add(slot+":"+BuiltInRegistries.ITEM.getKey(player.getItemBySlot(slot).getItem()));return gear;}
        Map<String,Object> snapshot(String kind){
            Map<String,Object> row=new LinkedHashMap<>();row.put("record",kind);row.put("controllerMode",clientMode?"real_client":"simulated_server_player");row.put("playerName",request.playerName==null?"BBFightBot":request.playerName);row.put("boss",request.boss);row.put("gear",request.gear);row.put("tick",ticks);row.put("gameSeconds",ticks/20d);row.put("timestamp",Instant.now().toString());row.put("running",running);row.put("setup",setupNotes);
            if(player!=null){row.put("playerHP",player.getHealth());row.put("playerMaxHP",player.getMaxHealth());row.put("food",player.getFoodData().getFoodLevel());row.put("saturation",player.getFoodData().getSaturationLevel());row.put("playerPosition",position(player));row.put("armor",player.getAttributeValue(Attributes.ARMOR));row.put("usingItem",player.getUseItem().isEmpty()?"":BuiltInRegistries.ITEM.getKey(player.getUseItem().getItem()).toString());row.put("blocking",player.isBlocking());row.put("gearRemaining",gearItems());row.put("inventory",inventoryState());row.put("playerPhysics",physicsState());}
            List<Map<String,Object>> enemies=new ArrayList<>();for(LivingEntity boss:bosses)enemies.add(Map.of("entity",id(boss),"hp",boss.getHealth(),"maxHP",boss.getMaxHealth(),"position",position(boss),"removed",boss.isRemoved()));row.put("bosses",enemies);return row;
        }
        void emit(String kind,Map<String,?> extra){try{Files.createDirectories(log.getParent());Map<String,Object> row=snapshot(kind);row.putAll(extra);Files.writeString(log,JSON.toJson(row)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception error){throw new RuntimeException(error);}}
        List<Map<String,Object>> inventoryState(){List<Map<String,Object>> result=new ArrayList<>();for(int i=0;i<player.getInventory().getContainerSize();i++){ItemStack stack=player.getInventory().getItem(i);if(!stack.isEmpty())result.add(Map.of("slot",i,"item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),"count",stack.getCount(),"damage",stack.getDamageValue(),"maxDamage",stack.getMaxDamage()));}return result;}
        Map<String,Object> physicsState(){Map<String,Object> result=new LinkedHashMap<>();Vec3 velocity=player.getDeltaMovement();result.put("velocity",List.of(velocity.x,velocity.y,velocity.z));result.put("onGround",player.onGround());result.put("noGravity",player.isNoGravity());result.put("noPhysics",player.noPhysics);result.put("gravityAttribute",player.getAttributeValue(Attributes.GRAVITY));result.put("horizontalCollision",player.horizontalCollision);result.put("verticalCollision",player.verticalCollision);result.put("passenger",player.isPassenger());result.put("vehicle",id(player.getVehicle()));result.put("pose",player.getPose().toString());result.put("blockBelow",BuiltInRegistries.BLOCK.getKey(level.getBlockState(player.blockPosition().below()).getBlock()).toString());return result;}
        void cleanup(){
            running=false;if(player!=null&&!clientMode)player.discard();
            if(dragonFight&&level.getDragonFight()!=null)try{Object uuid=field(level.getDragonFight(),"dragonUUID");if(bosses.stream().anyMatch(b->b.getUUID().equals(uuid)))set(level.getDragonFight(),"dragonKilled",true);}catch(Exception error){System.err.println("BBFIGHT cleanup dragon manager: "+error);}
            List<Entity> discard=new ArrayList<>();for(Entity entity:level.getAllEntities())if(!(entity instanceof ServerPlayer)&&!existing.contains(entity.getUUID())&&(arena.contains(entity.position())||spawned.contains(entity.getUUID())||entity.getTags().contains("bbfight_entity")))discard.add(entity);
            for(Entity entity:discard)entity.discard();
            for(long key:forced){var pos=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(pos.x,pos.z,false);}
            level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(oldRegen,source.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(oldSpawning,source.getServer());level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(oldGriefing,source.getServer());
        }
    }

    /** FakePlayer's immunity/no-op death are replaced with real ServerPlayer behavior. */
    private static final class FightPlayer extends FakePlayer {
        FightPlayer(ServerLevel level,GameProfile profile){super(level,profile);}
        @Override public boolean isInvulnerableTo(DamageSource damage){return (boolean)nativeCall("isInvulnerableTo",boolean.class,new Class[]{DamageSource.class},damage);}
        @Override public void tick(){nativeCall("tick",void.class,new Class[0]);}
        @Override public void die(DamageSource damage){nativeCall("die",void.class,new Class[]{DamageSource.class},damage);}
        @Override public boolean startRiding(Entity mount,boolean force){return (boolean)nativeCall("startRiding",boolean.class,new Class[]{Entity.class,boolean.class},mount,force);}
        private Object nativeCall(String name,Class<?> returns,Class<?>[] arguments,Object... values){
            try{var lookup=MethodHandles.privateLookupIn(FakePlayer.class,MethodHandles.lookup());var method=lookup.findSpecial(ServerPlayer.class,name,MethodType.methodType(returns,arguments),FakePlayer.class);List<Object> inputs=new ArrayList<>();inputs.add(this);inputs.addAll(List.of(values));return method.invokeWithArguments(inputs);}
            catch(Throwable error){throw new IllegalStateException("Cannot execute native ServerPlayer."+name,error);}
        }
    }
    private static String id(Entity entity){return entity==null?"":BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();}
    private static List<Double> position(Entity entity){return List.of(entity.getX(),entity.getY(),entity.getZ());}
    private static Field findField(Object object,String name)throws Exception{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private static Object field(Object object,String name)throws Exception{return findField(object,name).get(object);}
    private static void set(Object object,String name,Object value)throws Exception{findField(object,name).set(object,value);}
    private static Object call(Object object,String name,Class<?>[] args,Object... values)throws Exception{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Method method=type.getDeclaredMethod(name,args);method.setAccessible(true);return method.invoke(object,values);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(object.getClass()+"."+name);}
}

package survival.balance.tests;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Records native combat outcomes for one isolated scenario at a time. */
final class ExhaustiveCapture {
    record Hit(double damageHp, String type, String directType, String ownerType,
               double beforeHp, double afterHp, Double originalIncomingHp,
               double armor, double toughness, List<String> nativeCallers) {}

    private record Incoming(DamageSource source, double health, double raw, double armor, double toughness) {}

    private static final List<Map<String, Object>> rows = new ArrayList<>();
    private static final List<Hit> hits = new ArrayList<>();
    private static String filter = "";
    private static String bossId;
    private static String move;
    private static BossBalanceTests.TestPlayer player;
    private static Incoming incoming;
    private static Map<String, Long> initialProfiles;
    private static double startHealth;
    private static int incomingEvents;
    private static Method profileCounts;

    private ExhaustiveCapture() {}

    static void install() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ExhaustiveCapture::onIncoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ExhaustiveCapture::onDamage);
    }

    static void reset(String bossFilter) {
        if (player != null) end();
        filter = bossFilter;
        rows.clear();
    }

    static boolean selected(String id) {
        return filter.isEmpty() || filter.equals(id);
    }

    static void begin(String id, String caseName, BossBalanceTests.TestPlayer victim) {
        if (player != null) throw new IllegalStateException("Previous combat capture was not closed: " + move);
        bossId = id;
        move = caseName;
        player = victim;
        startHealth = victim.getHealth();
        incoming = null;
        incomingEvents = 0;
        hits.clear();
        initialProfiles = profiles();
    }

    static List<Hit> end() {
        if (player == null) return List.of();
        List<Hit> observed = List.copyOf(hits);
        Map<String, Long> matchedProfiles = new LinkedHashMap<>();
        profiles().forEach((key, count) -> {
            long added = count - initialProfiles.getOrDefault(key, 0L);
            if (added > 0) matchedProfiles.put(key, added);
        });
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("boss", bossId);
        row.put("move", move);
        row.put("status", observed.isEmpty() ? "no_damage_observed" : "damage_observed");
        row.put("startHp", startHealth);
        row.put("endHp", player.getHealth());
        row.put("maxHp", player.getMaxHealth());
        row.put("incomingEvents", incomingEvents);
        row.put("balanceProfiles", matchedProfiles);
        row.put("hits", observed);
        rows.add(row);
        player = null;
        incoming = null;
        return observed;
    }

    static void note(String id, String caseName, String status, String detail) {
        rows.add(Map.of("boss", id, "move", caseName, "status", status, "detail", detail));
    }

    static void write(Path path) throws Exception {
        if (player != null) end();
        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(rows));
    }

    private static void onIncoming(LivingIncomingDamageEvent event) {
        if (player == null || event.getEntity() != player) return;
        incomingEvents++;
        incoming = new Incoming(event.getSource(), player.getHealth(), event.getAmount(),
            player.getAttributeValue(Attributes.ARMOR), player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (player == null || event.getEntity() != player || event.getNewDamage() <= 0) return;
        DamageSource source = event.getSource();
        Incoming raw = incoming != null && incoming.source() == source ? incoming : null;
        List<String> callers = StackWalker.getInstance().walk(frames -> frames
            .filter(frame -> frame.getClassName().startsWith("twilightforest.")
                || frame.getClassName().startsWith("com.github.L_Ender.cataclysm.")
                || frame.getClassName().startsWith("com.cerbon.bosses_of_mass_destruction."))
            .limit(6).map(frame -> frame.getClassName() + "." + frame.getMethodName()).toList());
        double after = player.getHealth();
        hits.add(new Hit(event.getNewDamage(), source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse(""),
            entityType(source.getDirectEntity()), entityType(source.getEntity()),
            raw == null ? after + event.getNewDamage() : raw.health(), after, raw == null ? null : raw.raw(),
            raw == null ? player.getAttributeValue(Attributes.ARMOR) : raw.armor(),
            raw == null ? player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) : raw.toughness(), callers));
        incoming = null;
    }

    private static String entityType(Entity entity) {
        return entity == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> profiles() {
        try {
            if (profileCounts == null) profileCounts = Class.forName("survival.balance.BossDamageBalance").getMethod("profileCounts");
            return (Map<String, Long>) profileCounts.invoke(null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read active balance profile counters", error);
        }
    }
}

package survival.balance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Readable, server-owned settings. No changes to the three mods' native configs. */
public final class BalanceSettings {
    public static final Logger LOG = LoggerFactory.getLogger("SurvivalBossBalance");
    private static JsonObject settings = new JsonObject();
    private BalanceSettings() {}

    public static void load(Path path) {
        settings = new JsonObject();
        if (!Files.isRegularFile(path)) {
            LOG.info("No {} found; using built-in boss balance defaults", path);
            return;
        }
        try {
            settings = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            validateMap("healthTargets", 1, 1000000);
            validateMap("damageTargets", 0, 1000);
            validateMap("legacyDamageTargets", 0, 1000);
            int schema = settings.has("schemaVersion") ? settings.get("schemaVersion").getAsInt() : 1;
            if (schema < 1 || schema > 2) throw new IllegalArgumentException("Unsupported boss balance schema: " + schema);
            if (schema == 1) settings = migrateLegacyDamage(settings);
            else if (settings.has("damageReference") && !"hard_plain_diamond".equals(settings.get("damageReference").getAsString())) {
                throw new IllegalArgumentException("Schema 2 damageReference must be hard_plain_diamond");
            }
            LOG.info("Loaded boss balance settings from {}", path);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot load boss balance settings: " + path, e);
        }
    }

    /** Upgrade unchanged 1.1 values to the approved defaults. Customized values
     * remain explicitly calibrated with the old reference, including the real
     * source's armor/enchantment bypass flags. This avoids a lossy one-number
     * conversion for profiles used by several native damage types. No file is
     * rewritten: operators can move these entries to legacyDamageTargets in a
     * schema-2 config, or deliberately replace them with diamond values. */
    public static JsonObject migrateLegacyDamage(JsonObject input) {
        JsonObject settings = input.deepCopy();
        if (settings.has("schemaVersion") && settings.get("schemaVersion").getAsInt() == 2) return settings;
        JsonObject old = settings.has("damageTargets") ? settings.getAsJsonObject("damageTargets") : new JsonObject();
        JsonObject legacy = settings.has("legacyDamageTargets") ? settings.getAsJsonObject("legacyDamageTargets").deepCopy() : new JsonObject();
        for (var entry : old.entrySet()) {
            Double previousDefault = LegacyDamageDefaults.VALUES.get(entry.getKey());
            if (previousDefault == null || Math.abs(entry.getValue().getAsDouble() - previousDefault) > 1e-9) {
                legacy.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        // A rage curve is one setting with two endpoints; keep both in the same
        // legacy unit if either endpoint was customized.
        if (legacy.has("maledictus.mace_min") || legacy.has("maledictus.mace_max")) {
            inheritLegacy(legacy, old, "maledictus.mace_min", "maledictus.mace_min");
            inheritLegacy(legacy, old, "maledictus.mace_max", "maledictus.mace_max");
        }
        if (legacy.has("maledictus.heavy")) inheritLegacy(legacy, old, "maledictus.anima", "maledictus.heavy");
        if (legacy.has("ignis.heavy")) inheritLegacy(legacy, old, "ignis.ultimate", "ignis.heavy");
        settings.remove("damageTargets");
        settings.add("legacyDamageTargets", legacy);
        settings.addProperty("schemaVersion", 2);
        settings.addProperty("damageReference", "hard_plain_diamond");
        LOG.info("Using schema-2 diamond defaults for unchanged legacy settings; preserved legacy-reference custom overrides: {}. Config file was not rewritten.", legacy.keySet());
        return settings;
    }

    private static void inheritLegacy(JsonObject legacy, JsonObject old, String destination, String original) {
        if (legacy.has(destination)) return;
        legacy.addProperty(destination, old.has(original) ? old.get(original).getAsDouble() : LegacyDamageDefaults.VALUES.get(original));
    }

    private static void validateMap(String name, double min, double max) {
        if (!settings.has(name)) return;
        for (var entry : settings.getAsJsonObject(name).entrySet()) {
            double value = entry.getValue().getAsDouble();
            if (!Double.isFinite(value) || value < min || value > max) {
                throw new IllegalArgumentException(name + "." + entry.getKey() + " is outside " + min + ".." + max);
            }
        }
    }

    private static double number(String section, String key, double fallback) {
        if (!settings.has(section)) return fallback;
        JsonObject values = settings.getAsJsonObject(section);
        return values.has(key) ? values.get(key).getAsDouble() : fallback;
    }

    public static double healthTarget(String id, double fallback) {
        return number("healthTargets", id, fallback);
    }

    public static double damageTarget(String key, double fallbackHearts) {
        if (usesLegacyDamageReference(key)) return settings.getAsJsonObject("legacyDamageTargets").get(key).getAsDouble();
        return number("damageTargets", key, fallbackHearts);
    }

    public static boolean usesLegacyDamageReference(String key) {
        return settings.has("legacyDamageTargets") && settings.getAsJsonObject("legacyDamageTargets").has(key);
    }

    public static boolean debug() {
        return settings.has("debug") && settings.get("debug").getAsBoolean();
    }
}

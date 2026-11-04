package survival.balance;

import com.google.gson.GsonBuilder;
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
        if (!Files.isRegularFile(path)) {
            LOG.info("No {} found; using built-in boss balance defaults", path);
            return;
        }
        try {
            settings = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            validateMap("healthTargets", 1, 1000000);
            validateMap("damageTargets", 0, 1000);
            LOG.info("Loaded boss balance settings from {}", path);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot load boss balance settings: " + path, e);
        }
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
        return number("damageTargets", key, fallbackHearts);
    }

    public static boolean debug() {
        return settings.has("debug") && settings.get("debug").getAsBoolean();
    }
}

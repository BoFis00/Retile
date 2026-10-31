package com.retile.client;

import com.retile.Retile;
import com.retile.brush.BrushSelection;
import com.retile.brush.BrushSettings;
import com.retile.brush.BrushShapeType;
import com.retile.client.config.BrushConfigJson;
import com.retile.client.config.PresetStore;
import com.retile.client.config.KeyCombo;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/** Connects the pure JSON transport to the block registry and the client config directory. */
public final class BrushConfig {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(Retile.MOD_ID + ".json");
    public static PresetStore presets = new PresetStore();
    public static KeyCombo undoKey = KeyCombo.UNASSIGNED, redoKey = KeyCombo.UNASSIGNED;

    private BrushConfig() {}

    public static BrushSettings load() { return load(PATH); }

    public static BrushSettings load(Path path) {
        BrushConfigJson.Data data = BrushConfigJson.load(path);
        presets = data.presets;
        undoKey = data.undoKey;
        redoKey = data.redoKey;
        try {
            BrushSettings settings = new BrushSettings();
            Set<Identifier> seen = new HashSet<>();
            for (String value : data.filters) {
                Identifier id = Identifier.parse(value);
                if (seen.add(id) && allowed(id, true)) settings.filters.add(id);
            }
            seen.clear();
            for (BrushConfigJson.WeightedEntry entry : data.replacements) {
                Identifier id = Identifier.parse(entry.id());
                if (seen.add(id) && allowed(id, false))
                    settings.replacements.add(new BrushSettings.WeightedEntry(id, entry.weight()));
            }
            settings.includeAir = data.includeAir;
            settings.chaos = BrushSettings.snapChaos(data.chaos);
            settings.limitHeight = data.limitHeight;
            settings.minY = data.minY;
            settings.maxY = data.maxY;
            settings.radius = data.radius;
            settings.shape = BrushShapeType.valueOf(data.shape);
            settings.fixedSeed = data.fixedSeed;
            settings.seed = data.seed;
            return settings;
        } catch (RuntimeException error) {
            Retile.LOGGER.warn("Cannot load brush config; using defaults", error);
            return new BrushSettings();
        }
    }

    private static boolean allowed(Identifier id, boolean air) {
        return BuiltInRegistries.BLOCK.containsKey(id)
                && BrushSelection.allowed(BuiltInRegistries.BLOCK.getValue(id), air);
    }

    /** Resolve at use time: missing and newly ineligible blocks cannot enter a loaded palette. */
    public static List<BrushSettings.WeightedEntry> resolvePreset(PresetStore.Preset preset) {
        List<BrushSettings.WeightedEntry> result = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>();
        for (PresetStore.Entry entry : preset.blocks()) {
            Identifier id = Identifier.tryParse(entry.id());
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)
                    && BrushSelection.selectable(BuiltInRegistries.BLOCK.getValue(id)) && seen.add(id))
                result.add(new BrushSettings.WeightedEntry(id, entry.weight()));
        }
        return result;
    }

    public static void save(BrushSettings settings) { save(PATH, settings); }

    public static void save(Path path, BrushSettings settings) {
        try {
            BrushConfigJson.Data data = new BrushConfigJson.Data();
            settings.filters.forEach(id -> data.filters.add(id.toString()));
            settings.replacements.forEach(entry ->
                    data.replacements.add(new BrushConfigJson.WeightedEntry(entry.id().toString(), entry.weight())));
            data.includeAir = settings.includeAir;
            data.chaos = settings.chaos;
            data.limitHeight = settings.limitHeight;
            data.minY = settings.minY;
            data.maxY = settings.maxY;
            data.radius = settings.radius;
            data.shape = settings.shape.name();
            data.fixedSeed = settings.fixedSeed;
            data.seed = settings.seed;
            data.presets = presets;
            data.undoKey = undoKey;
            data.redoKey = redoKey;
            BrushConfigJson.save(path, data);
        } catch (Exception error) {
            Retile.LOGGER.error("Cannot save brush config", error);
        }
    }
}

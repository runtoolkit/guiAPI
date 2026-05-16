package dev.toolkitmc.guiapi.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.toolkitmc.guiapi.GuiApiMod;
import dev.toolkitmc.guiapi.gui.GuiDefinition;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads all data/<ns>/gui/*.json files from the datapack resource manager.
 *
 * Registered as a server-side resource reload listener so it fires on
 * /reload as well as world load.
 */
public class GuiRegistry extends SinglePreparationResourceReloader<Map<Identifier, GuiDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final GuiRegistry INSTANCE = new GuiRegistry();

    private static final String DIRECTORY = "gui";
    private static final Gson GSON = new Gson();

    private final Map<Identifier, GuiDefinition> definitions = new HashMap<>();

    private GuiRegistry() {}

    @Override
    public Identifier getFabricId() {
        return Identifier.of("guiapi", "gui_registry");
    }

    // ── ResourceReloader impl ────────────────────────────────────────────────

    @Override
    protected Map<Identifier, GuiDefinition> prepare(ResourceManager manager, Profiler profiler) {
        Map<Identifier, GuiDefinition> loaded = new HashMap<>();

        // findAllResources finds files matching data/<ns>/gui/<path>.json
        manager.findResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
               .forEach((fileId, resource) -> {
                   try (InputStreamReader reader = new InputStreamReader(
                           resource.getInputStream(), StandardCharsets.UTF_8)) {

                       JsonObject json = GSON.fromJson(reader, JsonObject.class);

                       // fileId looks like: <ns>:gui/<name>.json
                       // Compute logical GUI id: <ns>:<name>  (strip "gui/" prefix + ".json")
                       String path = fileId.getPath(); // "gui/my_gui.json"
                       String stripped = path.substring(DIRECTORY.length() + 1, path.length() - 5);
                       Identifier guiId = Identifier.of(fileId.getNamespace(), stripped);

                       GuiDefinition def = GuiDefinition.parse(guiId, json);
                       loaded.put(guiId, def);

                       GuiApiMod.LOGGER.info("[GuiAPI] Loaded GUI: {}", guiId);
                   } catch (Exception e) {
                       GuiApiMod.LOGGER.error("[GuiAPI] Failed to load GUI {}: {}", fileId, e.getMessage());
                   }
               });

        return loaded;
    }

    @Override
    protected void apply(Map<Identifier, GuiDefinition> prepared, ResourceManager manager, Profiler profiler) {
        definitions.clear();
        definitions.putAll(prepared);
        GuiApiMod.LOGGER.info("[GuiAPI] Registered {} GUI definitions.", definitions.size());
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public Optional<GuiDefinition> get(Identifier id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Map<Identifier, GuiDefinition> getAll() {
        return Map.copyOf(definitions);
    }
}

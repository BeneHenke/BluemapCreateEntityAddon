package eu.cronmoth.createentityaddon.rendering.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public final class ComponentText {

    private ComponentText() {}

    /** Looks up {@code key}'s translated pattern, substituting {@code args} the way Minecraft's %s/%1$s lang patterns expect. Falls back to the raw key if unknown (matches vanilla's own missing-translation behaviour) or if no pack's lang file had it (not loaded yet, or a mod BlueMap doesn't render at all). */
    private static String translate(String key, List<String> args) {
        LangExtension ext = LangExtension.instance();
        String pattern = ext == null ? null : ext.get(key);
        if (pattern == null) return key;
        if (args.isEmpty()) return pattern;
        try {
            return String.format(pattern, args.toArray());
        } catch (RuntimeException e) {
            return pattern;
        }
    }

    /**
     * Resolves a JSON text-component string (e.g. {@code {"extra":[...],"text":""}}) into plain
     * text, exactly like Minecraft's own {@code Component.getString()}. Returns {@code raw}
     * unchanged if it isn't valid JSON (defensive - real board NBT always is) or is {@code null}.
     */
    public static String resolveText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            return resolve(JsonParser.parseString(raw));
        } catch (RuntimeException e) {
            return raw;
        }
    }

    private static String resolve(JsonElement el) {
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement child : el.getAsJsonArray()) sb.append(resolve(child));
            return sb.toString();
        }
        if (!el.isJsonObject()) return "";
        JsonObject obj = el.getAsJsonObject();
        StringBuilder sb = new StringBuilder();
        if (obj.has("text")) {
            sb.append(obj.get("text").getAsString());
        } else if (obj.has("translate")) {
            String key = obj.get("translate").getAsString();
            List<String> args = new ArrayList<>();
            JsonElement with = obj.get("with");
            if (with != null && with.isJsonArray()) {
                for (JsonElement w : with.getAsJsonArray()) args.add(resolve(w));
            }
            sb.append(translate(key, args));
        }
        JsonElement extra = obj.get("extra");
        if (extra != null && extra.isJsonArray()) {
            for (JsonElement child : extra.getAsJsonArray()) sb.append(resolve(child));
        }
        return sb.toString();
    }
}

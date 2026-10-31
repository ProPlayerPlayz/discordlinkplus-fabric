package com.mcdiscordbot.fabric;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

final class ChatText {
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z]+)%");
    private ChatText() {}

    static String clean(String value) {
        if (value == null) return "";
        return value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").replace("§", "")
            .replaceAll("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]", " ").replaceAll("\\s+", " ").strip();
    }

    static String limit(String value, int length) {
        if (value.length() <= length) return value;
        int end = length - 1;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…";
    }

    static String render(String template, Map<String, String> values) {
        return PLACEHOLDER.matcher(template).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(values.getOrDefault(match.group(1), match.group())));
    }

    static String markdown(String value) { return value.replaceAll("([\\\\*_~`|>])", "\\\\$1"); }

    static String avatar(String template, UUID uuid, String player, String textureProperty) {
        if (template.isBlank()) return null;
        String identity = uuid.toString();
        try {
            if (textureProperty != null && textureProperty.length() <= 16_384) {
                var json = JsonParser.parseString(new String(Base64.getDecoder().decode(textureProperty), StandardCharsets.UTF_8));
                URI uri = URI.create(json.getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString());
                String path = uri.getPath();
                if ("textures.minecraft.net".equalsIgnoreCase(uri.getHost()) && path.matches("/texture/[a-fA-F0-9]{32,64}")) identity = path.substring("/texture/".length());
            }
        } catch (RuntimeException ignored) { /* Missing/offline/custom profile: the avatar provider can resolve the UUID. */ }
        return render(template, Map.of("uuid", uuid.toString(), "player", URLEncoder.encode(player, StandardCharsets.UTF_8), "texture", identity));
    }

    static boolean relayGameMessage(String key, ChatConfig config) {
        if (key == null) return false;
        if (key.equals("multiplayer.player.joined") || key.equals("multiplayer.player.joined.renamed") || key.equals("multiplayer.player.left")) return config.joinLeaveMessages;
        if (key.startsWith("death.attack.") || key.startsWith("death.fell.")) return config.deathMessages;
        return key.startsWith("chat.type.advancement.") && config.advancementMessages;
    }
}

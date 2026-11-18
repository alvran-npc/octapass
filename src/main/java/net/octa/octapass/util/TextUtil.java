package net.octa.octapass.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY = Pattern.compile("(?i)[&§]([0-9A-FK-OR])");
    private static final java.util.Map<Character, String> LEGACY_TAGS = java.util.Map.ofEntries(
            java.util.Map.entry('0', "black"), java.util.Map.entry('1', "dark_blue"),
            java.util.Map.entry('2', "dark_green"), java.util.Map.entry('3', "dark_aqua"),
            java.util.Map.entry('4', "dark_red"), java.util.Map.entry('5', "dark_purple"),
            java.util.Map.entry('6', "gold"), java.util.Map.entry('7', "gray"),
            java.util.Map.entry('8', "dark_gray"), java.util.Map.entry('9', "blue"),
            java.util.Map.entry('a', "green"), java.util.Map.entry('b', "aqua"),
            java.util.Map.entry('c', "red"), java.util.Map.entry('d', "light_purple"),
            java.util.Map.entry('e', "yellow"), java.util.Map.entry('f', "white"),
            java.util.Map.entry('k', "obfuscated"), java.util.Map.entry('l', "bold"),
            java.util.Map.entry('m', "strikethrough"), java.util.Map.entry('n', "underlined"),
            java.util.Map.entry('o', "italic"), java.util.Map.entry('r', "reset")
    );
    private TextUtil() {}

    public static Component component(String input) {
        if (input == null || input.isBlank()) return Component.empty();
        String normalized = input;
        Matcher hexMatcher = HEX.matcher(normalized);
        StringBuffer hexBuffer = new StringBuffer();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(hexBuffer, "<" + "#" + hexMatcher.group(1) + ">");
        }
        hexMatcher.appendTail(hexBuffer);
        normalized = hexBuffer.toString();

        Matcher legacyMatcher = LEGACY.matcher(normalized);
        StringBuffer legacyBuffer = new StringBuffer();
        while (legacyMatcher.find()) {
            String tag = LEGACY_TAGS.get(Character.toLowerCase(legacyMatcher.group(1).charAt(0)));
            if (tag == null) continue;
            legacyMatcher.appendReplacement(legacyBuffer, "<" + tag + ">");
        }
        legacyMatcher.appendTail(legacyBuffer);
        normalized = legacyBuffer.toString();
        try {
            return MINI.deserialize(normalized);
        } catch (RuntimeException ignored) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(
                    ChatColor.translateAlternateColorCodes('&', input));
        }
    }

    public static String plain(String input) {
        return LegacyComponentSerializer.legacySection().serialize(component(input));
    }
}

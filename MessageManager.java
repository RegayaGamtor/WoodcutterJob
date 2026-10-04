package com.regayagamtor.woodcutterjob.manager;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads messages.yml (MiniMessage) with fallback to the bundled defaults. Nothing is hardcoded elsewhere. */
public final class MessageManager {

    private final WoodcutterJob plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration messages = new YamlConfiguration();
    private YamlConfiguration defaults = new YamlConfiguration();

    public MessageManager(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    defaults = YamlConfiguration.loadConfiguration(reader);
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not read bundled messages.yml: " + ex.getMessage());
        }
    }

    public static TagResolver ph(String name, String value) {
        return Placeholder.unparsed(name, value);
    }

    public static TagResolver phc(String name, Component value) {
        return Placeholder.component(name, value);
    }

    /** Raw (unparsed) lines of a message; single strings become one line. */
    public List<String> raw(String key) {
        List<String> lines = read(messages, key);
        if (lines == null) lines = read(defaults, key);
        if (lines == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            lines = List.of("<red>[WoodcutterJob] Missing message: " + key);
        }
        return lines;
    }

    private static List<String> read(YamlConfiguration cfg, String key) {
        if (cfg.isList(key)) return new ArrayList<>(cfg.getStringList(key));
        if (cfg.isString(key)) return List.of(cfg.getString(key, ""));
        return null;
    }

    private TagResolver all(TagResolver... extra) {
        String prefix = String.join(" ", read(messages, "prefix") != null ? read(messages, "prefix") : List.of(""));
        return TagResolver.resolver(Placeholder.parsed("prefix", prefix), TagResolver.resolver(extra));
    }

    public Component parse(String text, TagResolver... resolvers) {
        return mm.deserialize(text, all(resolvers));
    }

    /** First line of a message as a component. */
    public Component component(String key, TagResolver... resolvers) {
        List<String> lines = raw(key);
        return parse(lines.isEmpty() ? "" : lines.get(0), resolvers);
    }

    public List<Component> lines(String key, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (String line : raw(key)) out.add(parse(line, resolvers));
        return out;
    }

    /** Lore lines (italic disabled). */
    public List<Component> lore(String key, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (String line : raw(key)) out.add(parse(line, resolvers).decoration(TextDecoration.ITALIC, false));
        return out;
    }

    public void send(CommandSender target, String key, TagResolver... resolvers) {
        if (target == null) return;
        for (Component line : lines(key, resolvers)) target.sendMessage(line);
    }

    public void actionBar(Player player, String key, TagResolver... resolvers) {
        if (player == null) return;
        player.sendActionBar(component(key, resolvers));
    }
}

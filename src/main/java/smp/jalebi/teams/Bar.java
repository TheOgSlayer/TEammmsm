package smp.jalebi.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/** Every action bar message, always in small caps. */
public final class Bar {
    public static final TextColor BLUE = TextColor.fromHexString("#00A2FF");

    private Bar() {}

    private static Component sc(String s, TextColor c) {
        return Component.text(SmallCaps.of(s), c);
    }

    public static void created(Player p, String team) {
        p.sendActionBar(sc("Team ", NamedTextColor.WHITE).append(sc(team, BLUE)).append(sc(" created.", NamedTextColor.WHITE)));
    }

    public static void deleted(Player p, String team) {
        p.sendActionBar(sc("Team ", NamedTextColor.WHITE).append(sc(team, BLUE)).append(sc(" deleted", NamedTextColor.RED)));
    }

    public static void joined(Player p, String team) {
        p.sendActionBar(sc("Joined ", NamedTextColor.GREEN).append(sc("Team ", NamedTextColor.WHITE))
                .append(sc(team, BLUE)).append(sc(".", NamedTextColor.WHITE)));
    }

    public static void left(Player p, String team) {
        p.sendActionBar(sc("Left ", NamedTextColor.RED).append(sc("Team ", NamedTextColor.WHITE))
                .append(sc(team, BLUE)).append(sc(".", NamedTextColor.WHITE)));
    }

    public static void kicked(Player p, String team) {
        p.sendActionBar(sc("Kicked from ", NamedTextColor.RED).append(sc("Team ", NamedTextColor.WHITE))
                .append(sc(team, BLUE)).append(sc(".", NamedTextColor.WHITE)));
    }

    public static void homeSet(Player p, String home) {
        p.sendActionBar(sc("Home ", NamedTextColor.WHITE).append(sc(home, BLUE)).append(sc(" set", NamedTextColor.WHITE)));
    }

    public static void homeDeleted(Player p, String home) {
        p.sendActionBar(sc("Home ", NamedTextColor.WHITE).append(sc(home, BLUE)).append(sc(" deleted", NamedTextColor.RED)));
    }

    public static void error(Player p, String text) {
        p.sendActionBar(sc(text, NamedTextColor.RED));
    }
}

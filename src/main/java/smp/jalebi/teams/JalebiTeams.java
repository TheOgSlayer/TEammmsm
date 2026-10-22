package smp.jalebi.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class JalebiTeams extends JavaPlugin implements Listener {
    private TeamManager teams;
    private TeamGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        teams = new TeamManager(this);
        IconChooser icons = new IconChooser(this);
        gui = new TeamGui(this, icons);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(icons, this);
    }

    @Override
    public void onDisable() {
        teams.save();
    }

    public TeamManager teams() {
        return teams;
    }

    /** Used by the scoreboard plugin. Returns null if the player has no team. */
    public String getTeamName(UUID player) {
        Team t = teams.teamOf(player);
        return t == null ? null : t.name;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }
        gui.open(p);
        return true;
    }

    /** Teammates cannot hurt each other (melee and projectiles). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (e.getDamager() instanceof Player p) {
            attacker = p;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null || attacker.equals(victim)) return;

        if (teams.sameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            e.setCancelled(true);
            attacker.sendActionBar(Component.text(SmallCaps.of("You can't hurt your teammates"), NamedTextColor.RED));
        }
    }
}

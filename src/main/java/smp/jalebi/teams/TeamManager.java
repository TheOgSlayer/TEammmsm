package smp.jalebi.teams;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {
    private final JalebiTeams plugin;
    private final File file;
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, Team> byPlayer = new HashMap<>();

    public TeamManager(JalebiTeams plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "teams.yml");
        load();
    }

    // ------------------------------------------------------------ storage

    private void load() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("teams");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) continue;
                Material icon = Material.matchMaterial(s.getString("icon", "GRASS_BLOCK"));
                if (icon == null) icon = Material.GRASS_BLOCK;
                Team t = new Team(UUID.fromString(key), s.getString("name", "Team"), icon,
                        UUID.fromString(s.getString("captain", "")));
                t.members.clear();
                for (String m : s.getStringList("members")) t.members.add(UUID.fromString(m));
                t.members.add(t.captain);
                ConfigurationSection hs = s.getConfigurationSection("homes");
                if (hs != null) {
                    for (String hk : hs.getKeys(false)) {
                        ConfigurationSection h = hs.getConfigurationSection(hk);
                        if (h == null) continue;
                        Material hi = Material.matchMaterial(h.getString("icon", "COMPASS"));
                        if (hi == null) hi = Material.COMPASS;
                        t.homes.put(UUID.fromString(hk), new Team.Home(
                                h.getString("name", "Home"), hi, h.getString("world", "world"),
                                h.getDouble("x"), h.getDouble("y"), h.getDouble("z"),
                                (float) h.getDouble("yaw"), (float) h.getDouble("pitch")));
                    }
                }
                teams.put(t.id, t);
                for (UUID m : t.members) byPlayer.put(m, t);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not load team " + key + ": " + ex.getMessage());
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Team t : teams.values()) {
            String p = "teams." + t.id;
            cfg.set(p + ".name", t.name);
            cfg.set(p + ".icon", t.icon.name());
            cfg.set(p + ".captain", t.captain.toString());
            List<String> ms = new ArrayList<>();
            for (UUID m : t.members) ms.add(m.toString());
            cfg.set(p + ".members", ms);
            for (Map.Entry<UUID, Team.Home> e : t.homes.entrySet()) {
                String hp = p + ".homes." + e.getKey();
                Team.Home h = e.getValue();
                cfg.set(hp + ".name", h.name);
                cfg.set(hp + ".icon", h.icon.name());
                cfg.set(hp + ".world", h.world);
                cfg.set(hp + ".x", h.x);
                cfg.set(hp + ".y", h.y);
                cfg.set(hp + ".z", h.z);
                cfg.set(hp + ".yaw", h.yaw);
                cfg.set(hp + ".pitch", h.pitch);
            }
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save teams.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ lookups

    public Team teamOf(UUID id) { return byPlayer.get(id); }
    public Team getTeam(UUID teamId) { return teams.get(teamId); }
    public boolean sameTeam(UUID a, UUID b) {
        Team t = byPlayer.get(a);
        return t != null && t == byPlayer.get(b);
    }
    public int maxMembers() { return plugin.getConfig().getInt("max-members", 10); }

    public List<Team> allTeams() {
        List<Team> list = new ArrayList<>(teams.values());
        list.sort(Comparator.comparing(t -> t.name.toLowerCase(Locale.ROOT)));
        return list;
    }

    public String playerName(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? "unknown" : op.getName();
    }

    // ------------------------------------------------------------ validation

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    /** @return an error message, or null if fine. */
    public String validateName(String raw) {
        String n = normalize(raw);
        if (n.length() < 3 || n.length() > 16) return "Name must be 3-16 characters";
        if (!n.matches("[A-Za-z0-9_ ]+")) return "Use only letters, numbers, spaces and underscores";
        for (Team t : teams.values()) {
            if (t.name.equalsIgnoreCase(n)) return "That team name is already taken";
        }
        return null;
    }

    public String validateHomeName(String raw) {
        String n = normalize(raw);
        if (n.isEmpty() || n.length() > 16) return "Home name must be 1-16 characters";
        if (!n.matches("[A-Za-z0-9_ ]+")) return "Use only letters, numbers, spaces and underscores";
        return null;
    }

    // ------------------------------------------------------------ team actions

    public Team create(Player captain, String rawName, Material icon) {
        Team t = new Team(UUID.randomUUID(), normalize(rawName), icon, captain.getUniqueId());
        teams.put(t.id, t);
        byPlayer.put(captain.getUniqueId(), t);
        save();
        return t;
    }

    /** Deletes the team and tells every online member. */
    public void disband(Team t) {
        teams.remove(t.id);
        for (UUID m : t.members) {
            byPlayer.remove(m);
            Player p = Bukkit.getPlayer(m);
            if (p != null) Bar.deleted(p, t.name);
        }
        save();
    }

    public String join(UUID teamId, Player p) {
        Team t = teams.get(teamId);
        if (t == null) return "That team no longer exists";
        if (byPlayer.containsKey(p.getUniqueId())) return "You are already in a team";
        if (t.members.size() >= maxMembers()) return "That team is full";
        t.members.add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), t);
        save();
        return null;
    }

    /** A normal member leaves. The captain cannot leave (must disband). */
    public String leave(UUID id) {
        Team t = byPlayer.get(id);
        if (t == null) return "You are not in a team";
        if (t.captain.equals(id)) return "The captain cannot leave";
        t.members.remove(id);
        t.homes.remove(id);
        byPlayer.remove(id);
        save();
        return null;
    }

    public String kick(Team t, UUID target) {
        if (!t.members.contains(target)) return "That player is not in your team";
        if (t.captain.equals(target)) return "You cannot kick the captain";
        t.members.remove(target);
        t.homes.remove(target);
        byPlayer.remove(target);
        save();
        Player p = Bukkit.getPlayer(target);
        if (p != null) Bar.kicked(p, t.name);
        return null;
    }

    // ------------------------------------------------------------ homes (one per person)

    public Team.Home getHome(UUID id) {
        Team t = byPlayer.get(id);
        return t == null ? null : t.homes.get(id);
    }

    public Team.Home setHome(Player p) {
        Team t = byPlayer.get(p.getUniqueId());
        if (t == null || t.homes.containsKey(p.getUniqueId())) return null;
        Location l = p.getLocation();
        Team.Home h = new Team.Home("Home", Material.COMPASS, l);
        t.homes.put(p.getUniqueId(), h);
        save();
        return h;
    }

    public void deleteHome(UUID id) {
        Team t = byPlayer.get(id);
        if (t != null && t.homes.remove(id) != null) save();
    }

    public void renameHome(UUID id, String rawName) {
        Team.Home h = getHome(id);
        if (h != null) {
            h.name = normalize(rawName);
            save();
        }
    }

    public void setHomeIcon(UUID id, Material icon) {
        Team.Home h = getHome(id);
        if (h != null) {
            h.icon = icon;
            save();
        }
    }

    public String teleportHome(Player p) {
        Team.Home h = getHome(p.getUniqueId());
        if (h == null) return "You have not set a home";
        Location l = h.toLocation();
        if (l == null) return "That home's world is not loaded";
        p.teleportAsync(l);
        return null;
    }
}

package smp.jalebi.teams;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Every /teams dialog screen. All text is small caps. */
public class TeamGui {
    private static final TextColor ORANGE = TextColor.fromHexString("#FFA500");
    private static final TextColor YELLOW = TextColor.fromHexString("#FFE000");
    private static final String[] NAME_KEY = {"name"};

    private static class Draft {
        String name = "";
        Material icon = Material.GRASS_BLOCK;
    }

    private final JalebiTeams plugin;
    private final TeamManager tm;
    private final IconChooser icons;
    private final Map<UUID, Draft> drafts = new HashMap<>();

    public TeamGui(JalebiTeams plugin, IconChooser icons) {
        this.plugin = plugin;
        this.tm = plugin.teams();
        this.icons = icons;
    }

    // ------------------------------------------------------------ helpers

    private static Component sc(String s, TextColor c) {
        return Component.text(SmallCaps.of(s), c);
    }

    private static Component scb(String s, TextColor c) {
        return Component.text(SmallCaps.of(s), c, TextDecoration.BOLD);
    }

    private static Component gradientTitle(String text) {
        String t = SmallCaps.of(text);
        Component out = Component.empty();
        for (int i = 0; i < t.length(); i++) {
            float f = t.length() == 1 ? 0 : (float) i / (t.length() - 1);
            out = out.append(Component.text(t.charAt(i), TextColor.lerp(f, ORANGE, YELLOW)).decorate(TextDecoration.BOLD));
        }
        return out;
    }

    private static String pretty(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private ActionButton btn(Component label, Component tooltip, int width, String[] keys, DialogUtil.Click c) {
        return DialogUtil.btn(plugin, label, tooltip, width, keys, c);
    }

    private ActionButton btn(Component label, int width, DialogUtil.Click c) {
        return DialogUtil.btn(plugin, label, null, width, DialogUtil.NO_KEYS, c);
    }

    private ActionButton closeBtn() {
        return DialogUtil.close(sc("Close", NamedTextColor.WHITE), 120);
    }

    private DialogBody msg(Component c) {
        return DialogBody.plainMessage(c);
    }

    private DialogBody teamHeader(Team t, boolean showMembers) {
        Component desc = scb(t.name, NamedTextColor.WHITE)
                .append(Component.newline())
                .append(sc("Captain » ", NamedTextColor.GRAY)).append(sc(tm.playerName(t.captain), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(sc("Members » ", NamedTextColor.GRAY))
                .append(sc(t.members.size() + "/" + tm.maxMembers(), NamedTextColor.WHITE));
        if (showMembers) {
            List<String> names = new ArrayList<>();
            for (UUID m : t.members) names.add(SmallCaps.of(tm.playerName(m)));
            desc = desc.append(Component.newline()).append(Component.text(String.join(", ", names), NamedTextColor.GRAY));
        }
        return DialogBody.item(new ItemStack(t.icon)).description(DialogBody.plainMessage(desc)).build();
    }

    private Team captainTeam(Player p) {
        Team t = tm.teamOf(p.getUniqueId());
        return t != null && t.captain.equals(p.getUniqueId()) ? t : null;
    }

    // ------------------------------------------------------------ entry

    public void open(Player p) {
        if (tm.teamOf(p.getUniqueId()) == null) openStart(p, null);
        else openSettings(p, null);
    }

    // ------------------------------------------------------------ no team: create / join

    private void openStart(Player p, Component notice) {
        List<DialogBody> body = new ArrayList<>();
        if (notice != null) body.add(msg(notice));
        body.add(msg(sc("You are not in a team yet", NamedTextColor.GRAY)));

        List<ActionButton> buttons = List.of(
                btn(scb("Create Team", NamedTextColor.WHITE), 200, (pl, in) -> {
                    drafts.put(pl.getUniqueId(), new Draft());
                    openCreate(pl, null);
                }),
                btn(scb("Join Team", NamedTextColor.WHITE), 200, (pl, in) -> openJoin(pl, null)));

        p.showDialog(DialogUtil.multi(gradientTitle("Jalebi Teams"), body, List.of(), buttons, closeBtn(), 1));
    }

    private void openCreate(Player p, Component error) {
        Draft d = drafts.computeIfAbsent(p.getUniqueId(), k -> new Draft());
        List<DialogBody> body = new ArrayList<>();
        if (error != null) body.add(msg(error));
        body.add(DialogBody.item(new ItemStack(d.icon))
                .description(DialogBody.plainMessage(sc("Logo » ", NamedTextColor.GRAY).append(sc(pretty(d.icon), NamedTextColor.WHITE))))
                .build());

        List<DialogInput> inputs = List.of(
                DialogInput.text("name", sc("Name your team", ORANGE)).initial(d.name).maxLength(16).width(250).build());

        ActionButton choose = btn(sc("Choose Logo", NamedTextColor.WHITE), null, 150, NAME_KEY, (pl, in) -> {
            d.name = in.getOrDefault("name", "");
            icons.open(pl, m -> {
                d.icon = m;
                openCreate(pl, null);
            }, () -> openCreate(pl, null));
        });

        ActionButton save = btn(scb("Save", NamedTextColor.GREEN), null, 150, NAME_KEY, (pl, in) -> {
            String name = in.getOrDefault("name", "");
            d.name = name;
            if (tm.teamOf(pl.getUniqueId()) != null) {
                drafts.remove(pl.getUniqueId());
                openSettings(pl, null);
                return;
            }
            String err = tm.validateName(name);
            if (err != null) {
                openCreate(pl, sc(err, NamedTextColor.RED));
                return;
            }
            Team t = tm.create(pl, name, d.icon);
            drafts.remove(pl.getUniqueId());
            Bar.created(pl, t.name); // dialog closes so the action bar is visible
        });

        ActionButton back = btn(sc("Back", NamedTextColor.WHITE), 120, (pl, in) -> {
            drafts.remove(pl.getUniqueId());
            openStart(pl, null);
        });

        p.showDialog(DialogUtil.multi(gradientTitle("Create Team"), body, inputs, List.of(choose, save), back, 2));
    }

    private void openJoin(Player p, Component notice) {
        List<Team> list = tm.allTeams();
        List<DialogBody> body = new ArrayList<>();
        if (notice != null) body.add(msg(notice));
        body.add(msg(sc(list.isEmpty() ? "There are no teams yet" : "Choose a team to view it", NamedTextColor.GRAY)));

        List<ActionButton> buttons = new ArrayList<>();
        for (Team t : list) {
            UUID tid = t.id;
            boolean full = t.members.size() >= tm.maxMembers();
            buttons.add(btn(sc(t.name + "  (" + t.members.size() + "/" + tm.maxMembers() + ")",
                            full ? NamedTextColor.GRAY : NamedTextColor.WHITE),
                    sc(full ? "This team is full" : "Click to view this team", NamedTextColor.GRAY), 220,
                    DialogUtil.NO_KEYS, (pl, in) -> openTeamInfo(pl, tid)));
        }
        p.showDialog(DialogUtil.multi(gradientTitle("Join Team"), body, List.of(), buttons,
                btn(sc("Back", NamedTextColor.WHITE), 120, (pl, in) -> openStart(pl, null)), 1));
    }

    private void openTeamInfo(Player p, UUID teamId) {
        Team t = tm.getTeam(teamId);
        if (t == null) {
            openJoin(p, sc("That team no longer exists", NamedTextColor.RED));
            return;
        }
        ActionButton join = btn(scb("Join", NamedTextColor.GREEN), 120, (pl, in) -> {
            String err = tm.join(teamId, pl);
            if (err != null) {
                openJoin(pl, sc(err, NamedTextColor.RED));
                return;
            }
            Team joined = tm.teamOf(pl.getUniqueId());
            Bar.joined(pl, joined == null ? "" : joined.name); // dialog closes so the action bar is visible
        });
        ActionButton back = btn(sc("Back", NamedTextColor.WHITE), 120, (pl, in) -> openJoin(pl, null));
        p.showDialog(DialogUtil.confirm(gradientTitle("Team"), List.of(teamHeader(t, false)), List.of(), join, back));
    }

    // ------------------------------------------------------------ in a team: settings

    private void openSettings(Player p, Component notice) {
        Team team = tm.teamOf(p.getUniqueId());
        if (team == null) {
            openStart(p, null);
            return;
        }
        boolean captain = team.captain.equals(p.getUniqueId());

        List<DialogBody> body = new ArrayList<>();
        if (notice != null) body.add(msg(notice));
        body.add(teamHeader(team, true));

        List<ActionButton> buttons = new ArrayList<>();

        // HOME: gray when not set, white when set
        boolean hasHome = team.homes.containsKey(p.getUniqueId());
        buttons.add(btn(scb("Home", hasHome ? NamedTextColor.WHITE : NamedTextColor.GRAY),
                sc(hasHome ? "Click to manage your home" : "Click to set your home here", NamedTextColor.GRAY),
                220, DialogUtil.NO_KEYS, (pl, in) -> {
                    Team t = tm.teamOf(pl.getUniqueId());
                    if (t == null) { openStart(pl, null); return; }
                    if (t.homes.containsKey(pl.getUniqueId())) {
                        openHome(pl, null);
                    } else {
                        Team.Home h = tm.setHome(pl);
                        if (h != null) Bar.homeSet(pl, h.name); // dialog closes so the action bar is visible
                    }
                }));

        if (captain) {
            buttons.add(btn(sc("Kick Players", NamedTextColor.WHITE), 220, (pl, in) -> openKick(pl, null)));
            buttons.add(btn(sc("Disband Team", NamedTextColor.RED), 220, (pl, in) -> openDisband(pl)));
        } else {
            buttons.add(btn(sc("Leave Team", NamedTextColor.RED), 220, (pl, in) -> openLeave(pl)));
        }

        p.showDialog(DialogUtil.multi(gradientTitle("Team Settings"), body, List.of(), buttons, closeBtn(), 1));
    }

    private void openDisband(Player p) {
        Team t = captainTeam(p);
        if (t == null) { openSettings(p, sc("Only the captain can do that", NamedTextColor.RED)); return; }
        ActionButton yes = btn(scb("Yes", NamedTextColor.RED), 120, (pl, in) -> {
            Team tt = captainTeam(pl);
            if (tt == null) { openSettings(pl, null); return; }
            tm.disband(tt); // sends "Team <name> deleted" to every online member
        });
        ActionButton no = btn(sc("No", NamedTextColor.WHITE), 120, (pl, in) -> openSettings(pl, null));
        p.showDialog(DialogUtil.confirm(gradientTitle("Disband Team"),
                List.of(msg(sc("Disband ", NamedTextColor.GRAY).append(sc(t.name, NamedTextColor.WHITE)).append(sc("?", NamedTextColor.GRAY))),
                        msg(sc("This removes the team for everyone", NamedTextColor.DARK_GRAY))),
                List.of(), yes, no));
    }

    private void openLeave(Player p) {
        Team t = tm.teamOf(p.getUniqueId());
        if (t == null) { openStart(p, null); return; }
        ActionButton yes = btn(scb("Yes", NamedTextColor.RED), 120, (pl, in) -> {
            Team tt = tm.teamOf(pl.getUniqueId());
            String name = tt == null ? "" : tt.name;
            String err = tm.leave(pl.getUniqueId());
            if (err != null) openSettings(pl, sc(err, NamedTextColor.RED));
            else Bar.left(pl, name);
        });
        ActionButton no = btn(sc("No", NamedTextColor.WHITE), 120, (pl, in) -> openSettings(pl, null));
        p.showDialog(DialogUtil.confirm(gradientTitle("Leave Team"),
                List.of(msg(sc("Leave ", NamedTextColor.GRAY).append(sc(t.name, NamedTextColor.WHITE)).append(sc("?", NamedTextColor.GRAY)))),
                List.of(), yes, no));
    }

    // ------------------------------------------------------------ captain: kick

    private void openKick(Player p, Component notice) {
        Team t = captainTeam(p);
        if (t == null) { openSettings(p, sc("Only the captain can do that", NamedTextColor.RED)); return; }

        List<DialogBody> body = new ArrayList<>();
        if (notice != null) body.add(msg(notice));

        List<UUID> others = new ArrayList<>(t.members);
        others.remove(t.captain);
        others.sort(Comparator.comparing(u -> tm.playerName(u).toLowerCase(Locale.ROOT)));
        body.add(msg(sc(others.isEmpty() ? "There is nobody to kick" : "Choose a player to kick", NamedTextColor.GRAY)));

        List<ActionButton> buttons = new ArrayList<>();
        for (UUID u : others) {
            buttons.add(btn(sc(tm.playerName(u), NamedTextColor.WHITE),
                    sc("Click to kick this player", NamedTextColor.GRAY), 220, DialogUtil.NO_KEYS,
                    (pl, in) -> openKickConfirm(pl, u)));
        }
        p.showDialog(DialogUtil.multi(gradientTitle("Kick Players"), body, List.of(), buttons,
                btn(sc("Back", NamedTextColor.WHITE), 120, (pl, in) -> openSettings(pl, null)), 1));
    }

    private void openKickConfirm(Player p, UUID target) {
        Team t = captainTeam(p);
        if (t == null || !t.members.contains(target)) { openSettings(p, null); return; }
        String name = tm.playerName(target);

        ActionButton yes = btn(scb("Kick", NamedTextColor.RED), 120, (pl, in) -> {
            Team tt = captainTeam(pl);
            if (tt == null) { openSettings(pl, null); return; }
            String err = tm.kick(tt, target);
            openKick(pl, err == null
                    ? sc(name + " was kicked", NamedTextColor.GREEN)
                    : sc(err, NamedTextColor.RED));
        });
        ActionButton no = btn(sc("Back", NamedTextColor.WHITE), 120, (pl, in) -> openKick(pl, null));
        p.showDialog(DialogUtil.confirm(gradientTitle("Kick Player"),
                List.of(msg(sc("Kick ", NamedTextColor.GRAY).append(sc(name, NamedTextColor.WHITE)).append(sc("?", NamedTextColor.GRAY)))),
                List.of(), yes, no));
    }

    // ------------------------------------------------------------ home page

    private void openHome(Player p, Component notice) {
        Team.Home h = tm.getHome(p.getUniqueId());
        if (h == null) { openSettings(p, null); return; }

        List<DialogBody> body = new ArrayList<>();
        if (notice != null) body.add(msg(notice));
        Component desc = scb(h.name, NamedTextColor.WHITE).append(Component.newline())
                .append(Component.text(SmallCaps.of(h.world) + "  " + Math.round(h.x) + ", " + Math.round(h.y) + ", " + Math.round(h.z),
                        NamedTextColor.GRAY));
        body.add(DialogBody.item(new ItemStack(h.icon)).description(DialogBody.plainMessage(desc)).build());

        List<DialogInput> inputs = List.of(
                DialogInput.text("name", sc("Home name", ORANGE)).initial(h.name).maxLength(16).width(250).build());

        ActionButton tp = btn(scb("Teleport", NamedTextColor.GREEN), 150, (pl, in) -> {
            String err = tm.teleportHome(pl);
            if (err != null) openHome(pl, sc(err, NamedTextColor.RED)); // on success the dialog stays closed
        });

        ActionButton del = btn(scb("Delete", NamedTextColor.RED), 150, (pl, in) -> {
            Team.Home hh = tm.getHome(pl.getUniqueId());
            if (hh == null) { openSettings(pl, null); return; }
            String n = hh.name;
            tm.deleteHome(pl.getUniqueId());
            Bar.homeDeleted(pl, n);
        });

        ActionButton icon = btn(sc("Choose Icon", NamedTextColor.WHITE), null, 150, NAME_KEY, (pl, in) -> {
            String name = in.getOrDefault("name", "");
            if (tm.validateHomeName(name) == null) tm.renameHome(pl.getUniqueId(), name);
            icons.open(pl, m -> {
                tm.setHomeIcon(pl.getUniqueId(), m);
                openHome(pl, null);
            }, () -> openHome(pl, null));
        });

        ActionButton save = btn(scb("Save", NamedTextColor.GREEN), null, 150, NAME_KEY, (pl, in) -> {
            String name = in.getOrDefault("name", "");
            String err = tm.validateHomeName(name);
            if (err != null) {
                openHome(pl, sc(err, NamedTextColor.RED));
                return;
            }
            tm.renameHome(pl.getUniqueId(), name);
            openHome(pl, sc("Saved", NamedTextColor.GREEN));
        });

        p.showDialog(DialogUtil.multi(gradientTitle("Home"), body, inputs, List.of(tp, del, icon, save),
                btn(sc("Back", NamedTextColor.WHITE), 150, (pl, in) -> openSettings(pl, null)), 2));
    }
}

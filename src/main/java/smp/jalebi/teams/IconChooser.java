package smp.jalebi.teams;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;

/** Paginated chest menu listing every item in the game, with search. */
public class IconChooser implements Listener {
    private static final int PAGE = 45;
    private static final TextColor ORANGE = TextColor.fromHexString("#FFA500");

    private static final class Session {
        int page = 0;
        String filter = "";
        Consumer<Material> onPick;
        Runnable onBack;
    }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        Session s;
        @Override public Inventory getInventory() { return inv; }
    }

    private final JalebiTeams plugin;
    private List<Material> cache;

    public IconChooser(JalebiTeams plugin) {
        this.plugin = plugin;
    }

    // isEnabledByFeature is missing on some Paper versions, so look it up safely.
    private static final java.lang.reflect.Method FEATURE_CHECK = findFeatureCheck();

    private static java.lang.reflect.Method findFeatureCheck() {
        try {
            return Material.class.getMethod("isEnabledByFeature", World.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static boolean featureEnabled(Material m, World w) {
        if (FEATURE_CHECK == null || w == null) return true;
        try {
            return (boolean) FEATURE_CHECK.invoke(m, w);
        } catch (ReflectiveOperationException | LinkageError e) {
            return true;
        }
    }

    // ------------------------------------------------------------ item list

    private List<Material> all() {
        if (cache != null) return cache;
        Set<String> exclude = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("icon-exclude")) exclude.add(s.toUpperCase(Locale.ROOT));
        List<String> suffixes = plugin.getConfig().getStringList("icon-exclude-suffix");
        World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);

        List<Material> list = new ArrayList<>();
        for (Material m : Material.values()) {
            String n = m.name();
            if (n.startsWith("LEGACY_") || n.equals("AIR") || n.endsWith("_AIR") || !m.isItem()) continue;
            if (!featureEnabled(m, w)) continue;
            if (exclude.contains(n)) continue;
            boolean bad = false;
            for (String suf : suffixes) {
                if (n.endsWith(suf.toUpperCase(Locale.ROOT))) { bad = true; break; }
            }
            if (!bad) list.add(m);
        }
        list.sort(Comparator.comparing(Material::name));
        cache = list;
        return list;
    }

    private List<Material> filtered(Session s) {
        String f = s.filter.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (f.isEmpty()) return all();
        List<Material> out = new ArrayList<>();
        for (Material m : all()) if (m.name().contains(f)) out.add(m);
        return out;
    }

    // ------------------------------------------------------------ opening

    public void open(Player p, Consumer<Material> onPick, Runnable onBack) {
        Session s = new Session();
        s.onPick = onPick;
        s.onBack = onBack;
        show(p, s);
    }

    private void show(Player p, Session s) {
        Holder h = new Holder();
        h.s = s;
        h.inv = Bukkit.createInventory(h, 54,
                Component.text(SmallCaps.of("Choose Icon"), ORANGE).decorate(TextDecoration.BOLD));
        render(h);
        p.openInventory(h.inv);
    }

    private ItemStack named(Material m, Component name, Component... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (Component c : lore) l.add(c.decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    private void render(Holder h) {
        Session s = h.s;
        List<Material> list = filtered(s);
        int pages = Math.max(1, (list.size() + PAGE - 1) / PAGE);
        s.page = Math.max(0, Math.min(s.page, pages - 1));

        Inventory inv = h.inv;
        inv.clear();
        for (int i = 0; i < PAGE; i++) {
            int idx = s.page * PAGE + i;
            if (idx >= list.size()) break;
            ItemStack it = new ItemStack(list.get(idx));
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.lore(List.of(Component.text(SmallCaps.of("Click to select"), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
                it.setItemMeta(meta);
            }
            inv.setItem(i, it);
        }

        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, Component.space());
        for (int i = PAGE; i < 54; i++) inv.setItem(i, pane);

        if (s.page > 0) inv.setItem(45, named(Material.ARROW, Component.text(SmallCaps.of("Previous"), NamedTextColor.WHITE)));
        inv.setItem(47, named(Material.COMPASS, Component.text(SmallCaps.of("Search"), NamedTextColor.AQUA),
                Component.text(s.filter.isBlank() ? SmallCaps.of("Find an item by name") : SmallCaps.of(s.filter), NamedTextColor.GRAY)));
        inv.setItem(49, named(Material.PAPER, Component.text(SmallCaps.of("Page " + (s.page + 1) + "/" + pages), NamedTextColor.WHITE),
                Component.text(list.size() + " " + SmallCaps.of("items"), NamedTextColor.GRAY)));
        if (!s.filter.isBlank()) {
            inv.setItem(51, named(Material.BARRIER, Component.text(SmallCaps.of("Clear search"), NamedTextColor.RED)));
        }
        inv.setItem(52, named(Material.OAK_DOOR, Component.text(SmallCaps.of("Back"), NamedTextColor.WHITE)));
        if (s.page < pages - 1) inv.setItem(53, named(Material.ARROW, Component.text(SmallCaps.of("Next"), NamedTextColor.WHITE)));
    }

    // ------------------------------------------------------------ search dialog

    private void openSearch(Player p, Session s) {
        ActionButton search = DialogUtil.btn(plugin,
                Component.text(SmallCaps.of("Search"), NamedTextColor.GREEN, TextDecoration.BOLD),
                null, 120, new String[]{"q"}, (pl, in) -> {
                    s.filter = in.getOrDefault("q", "").trim();
                    s.page = 0;
                    show(pl, s);
                });
        ActionButton cancel = DialogUtil.btn(plugin,
                Component.text(SmallCaps.of("Cancel"), NamedTextColor.WHITE),
                null, 120, DialogUtil.NO_KEYS, (pl, in) -> show(pl, s));

        Dialog d = DialogUtil.confirm(
                Component.text(SmallCaps.of("Search Items"), ORANGE, TextDecoration.BOLD),
                List.of(DialogBody.plainMessage(Component.text(SmallCaps.of("Type part of an item name, e.g. diamond"), NamedTextColor.GRAY))),
                List.of(DialogInput.text("q", Component.text(SmallCaps.of("Item name"), ORANGE))
                        .initial(s.filter).maxLength(32).width(250).build()),
                search, cancel);
        p.showDialog(d);
    }

    // ------------------------------------------------------------ events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getSlot();
        if (slot < PAGE) {
            List<Material> list = filtered(h.s);
            int idx = h.s.page * PAGE + slot;
            if (idx >= list.size()) return;
            Material m = list.get(idx);
            p.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> h.s.onPick.accept(m));
            return;
        }
        switch (slot) {
            case 45 -> { if (h.s.page > 0) { h.s.page--; render(h); } }
            case 53 -> { h.s.page++; render(h); }
            case 47 -> Bukkit.getScheduler().runTask(plugin, () -> openSearch(p, h.s));
            case 51 -> { h.s.filter = ""; h.s.page = 0; render(h); }
            case 52 -> {
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> h.s.onBack.run());
            }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}

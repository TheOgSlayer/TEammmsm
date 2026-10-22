package smp.jalebi.teams;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small helpers around Paper's Dialog API. */
public final class DialogUtil {
    private DialogUtil() {}

    /** Called on the main thread with a snapshot of the text inputs. */
    public interface Click {
        void run(Player p, Map<String, String> in);
    }

    public static final String[] NO_KEYS = new String[0];

    private static final ClickCallback.Options OPTS =
            ClickCallback.Options.builder().lifetime(Duration.ofMinutes(10)).build();

    public static ActionButton btn(JalebiTeams plugin, Component label, Component tooltip,
                                   int width, String[] textKeys, Click click) {
        return ActionButton.create(label, tooltip, width,
                DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    Map<String, String> in = new HashMap<>();
                    for (String k : textKeys) {
                        String v = view.getText(k);
                        if (v != null) in.put(k, v);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> click.run(p, in));
                }, OPTS));
    }

    /** A button that simply closes the dialog. */
    public static ActionButton close(Component label, int width) {
        return ActionButton.create(label, null, width, null);
    }

    public static Dialog multi(Component title, List<DialogBody> body, List<DialogInput> inputs,
                               List<ActionButton> buttons, ActionButton exit, int columns) {
        return Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(buttons, exit, columns)));
    }

    public static Dialog confirm(Component title, List<DialogBody> body, List<DialogInput> inputs,
                                 ActionButton yes, ActionButton no) {
        return Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(yes, no)));
    }
}

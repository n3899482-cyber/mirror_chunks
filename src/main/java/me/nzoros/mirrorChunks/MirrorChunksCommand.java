package me.nzoros.mirrorChunks;

import java.util.List;
import java.util.Locale;
import me.nzoros.mirrorChunks.core.MirrorFeature;
import me.nzoros.mirrorChunks.core.MirrorSettings;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

final class MirrorChunksCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "mirrorchunks.admin";
    private final PaperConfigManager configManager;

    MirrorChunksCommand(PaperConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0 || equals(args[0], "help")) {
            sendHelp(sender);
            return true;
        }
        if (equals(args[0], "status") && args.length == 1) {
            sendStatus(sender);
            return true;
        }
        if (equals(args[0], "reload") && args.length == 1) {
            sender.sendMessage(configManager.reload()
                ? "MirrorChunks configuration reloaded."
                : "MirrorChunks configuration reload failed. Existing settings remain active; check the console.");
            return true;
        }
        if (equals(args[0], "block-place")) {
            return blockPlace(sender, args);
        }
        if (equals(args[0], "block-break")) {
            return blockBreak(sender, args);
        }
        sender.sendMessage("Unknown subcommand. Use /mirrorchunks help.");
        return true;
    }

    private boolean blockPlace(CommandSender sender, String[] args) {
        if (args.length == 2) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "enable" -> {
                    configManager.setBlockPlaceEnabled(true);
                    sender.sendMessage("Block place mirroring is now ENABLED.");
                    return true;
                }
                case "disable" -> {
                    configManager.setBlockPlaceEnabled(false);
                    sender.sendMessage("Block place mirroring is now DISABLED.");
                    return true;
                }
                case "status" -> {
                    sender.sendMessage("Block place mirroring: " + state(configManager.settings().blockPlaceEnabled()));
                    return true;
                }
                default -> { }
            }
        }
        if (args.length == 3 && equals(args[1], "replace")) {
            switch (args[2].toLowerCase(Locale.ROOT)) {
                case "enable" -> {
                    configManager.setReplaceExistingBlocks(true);
                    sender.sendMessage("Replacing existing blocks is now ENABLED.");
                    return true;
                }
                case "disable" -> {
                    configManager.setReplaceExistingBlocks(false);
                    sender.sendMessage("Replacing existing blocks is now DISABLED.");
                    return true;
                }
                case "status" -> {
                    sender.sendMessage("Replace existing blocks: " + state(configManager.settings().replaceExistingBlocks()));
                    return true;
                }
                default -> { }
            }
        }
        sender.sendMessage("Unknown subcommand. Use /mirrorchunks help.");
        return true;
    }

    private boolean blockBreak(CommandSender sender, String[] args) {
        if (args.length == 2) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "enable" -> {
                    configManager.setBlockBreakEnabled(true);
                    sender.sendMessage("Block break mirroring is now ENABLED.");
                    return true;
                }
                case "disable" -> {
                    configManager.setBlockBreakEnabled(false);
                    sender.sendMessage("Block break mirroring is now DISABLED.");
                    return true;
                }
                case "status" -> {
                    sender.sendMessage("Block break mirroring: " + state(configManager.settings().blockBreakEnabled()));
                    return true;
                }
                default -> { }
            }
        }
        sender.sendMessage("Unknown subcommand. Use /mirrorchunks help.");
        return true;
    }

    private void sendStatus(CommandSender sender) {
        MirrorSettings settings = configManager.settings();
        sender.sendMessage("MirrorChunks Status");
        for (MirrorFeature feature : MirrorFeature.values()) {
            sender.sendMessage(feature.displayName() + ": " + state(feature.isEnabled(settings)));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("MirrorChunks commands:");
        sendHelpEntry(sender, "/mirrorchunks help", "Show this help message.");
        sendHelpEntry(sender, "/mirrorchunks status", "Show current settings.");
        sendHelpEntry(sender, "/mirrorchunks reload", "Reload configuration.");
        sendHelpEntry(sender, "/mirrorchunks block-place enable", "Enable block place mirroring.");
        sendHelpEntry(sender, "/mirrorchunks block-place disable", "Disable block place mirroring.");
        sendHelpEntry(sender, "/mirrorchunks block-place status", "Show block place mirroring status.");
        sendHelpEntry(sender, "/mirrorchunks block-place replace enable", "Enable replacement of existing blocks.");
        sendHelpEntry(sender, "/mirrorchunks block-place replace disable", "Disable replacement of existing blocks.");
        sendHelpEntry(sender, "/mirrorchunks block-place replace status", "Show replacement status.");
        sendHelpEntry(sender, "/mirrorchunks block-break enable", "Enable block break mirroring.");
        sendHelpEntry(sender, "/mirrorchunks block-break disable", "Disable block break mirroring.");
        sendHelpEntry(sender, "/mirrorchunks block-break status", "Show block break mirroring status.");
    }

    private void sendHelpEntry(CommandSender sender, String command, String description) {
        sender.sendMessage(command);
        sender.sendMessage("  " + description);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        List<String> choices = List.of();
        if (args.length == 1) {
            choices = List.of("help", "status", "reload", "block-place", "block-break");
        } else if (args.length == 2 && equals(args[0], "block-place")) {
            choices = List.of("enable", "disable", "status", "replace");
        } else if (args.length == 2 && equals(args[0], "block-break")) {
            choices = List.of("enable", "disable", "status");
        } else if (args.length == 3 && equals(args[0], "block-place") && equals(args[1], "replace")) {
            choices = List.of("enable", "disable", "status");
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.startsWith(prefix)).toList();
    }

    private static boolean equals(String value, String expected) {
        return value.equalsIgnoreCase(expected);
    }

    private static String state(boolean enabled) {
        return enabled ? "ENABLED" : "DISABLED";
    }
}

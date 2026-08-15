package org.nemotech.rsc.plugins.commands;

import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.model.MenuHandler;
import org.nemotech.rsc.model.GrandExchange;
import org.nemotech.rsc.model.player.InvItem;
import org.nemotech.rsc.plugins.Plugin;
import org.nemotech.rsc.plugins.listeners.action.CommandListener;
import org.nemotech.rsc.external.EntityManager;
import org.nemotech.rsc.bot.WorldBotManager;
import java.util.List;

/**
 * Commands for autonomous world bots and related multiplayer-style systems.
 */
public class BotCommands extends Plugin implements CommandListener {
    
    public String[] getCommands() {
        return new String[] { 
            "worldbots", "worldbot",
            "ollamastatus", "botchatstatus", "ollamamodel", "botmodel",
            "ollamaforget", "forgetbotchat", "botchat", "botpm", "botclan",
            "exporthighscores", "exporthiscores",
            "ge", "exchange"
        };
    }
    
    @Override
    public void onCommand(String command, String[] args, Player player) {
        if (command.equals("worldbots") || command.equals("worldbot")) {
            handleWorldBotCommand(args, player);
            return;
        }

        if (command.equals("ollamastatus") || command.equals("botchatstatus")) {
            WorldBotManager.getInstance().sendOllamaStatus(player);
            return;
        }

        if (command.equals("ollamamodel") || command.equals("botmodel")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                WorldBotManager.getInstance().sendOllamaModelHelp(player);
            } else if (args[0].equalsIgnoreCase("reset")) {
                WorldBotManager.getInstance().resetOllamaModel(player);
            } else {
                WorldBotManager.getInstance().selectOllamaModel(player, args[0]);
            }
            return;
        }

        if (command.equals("ollamaforget") || command.equals("forgetbotchat")) {
            WorldBotManager.getInstance().forgetOllama(player);
            return;
        }

        if (command.equals("botchat") || command.equals("botpm")) {
            WorldBotManager.getInstance().privateBotChat(player, String.join(" ", args));
            return;
        }

        if (command.equals("botclan")) {
            WorldBotManager.getInstance().groupBotChat(player, String.join(" ", args));
            return;
        }

        if (command.equals("exporthighscores") || command.equals("exporthiscores")) {
            player.save();
            player.getSender().sendMessage("@cya@[Highscores] @gre@Live highscore export refreshed.");
            return;
        }

        if (command.equals("ge") || command.equals("exchange")) {
            handleGrandExchangeCommand(args, player);
        }
    }
    

    private void handleWorldBotCommand(String[] args, Player player) {
        WorldBotManager manager = WorldBotManager.getInstance();
        String subCommand = args.length > 0 ? args[0].toLowerCase() : "status";

        if (subCommand.equals("start")) {
            int count = manager.getDefaultCount();
            if (args.length > 1) {
                try {
                    count = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.getSender().sendMessage("@cya@[WorldBots] @red@Count must be a number.");
                    return;
                }
            }
            manager.startBots(count);
            player.getSender().sendMessage("@cya@[WorldBots] @gre@Started " + manager.getBotCount() + " autonomous world bots.");
            return;
        }

        if (subCommand.equals("stop")) {
            manager.stopBots();
            player.getSender().sendMessage("@cya@[WorldBots] @whi@Stopped autonomous world bots.");
            return;
        }

        if (subCommand.equals("status")) {
            for (String line : manager.getStatusReport().split("\\n")) {
                player.getSender().sendMessage("@cya@[WorldBots] @whi@" + line);
            }
            return;
        }

        if (subCommand.equals("nearby") || subCommand.equals("where") || subCommand.equals("map")) {
            int radius = 64;
            if (args.length > 1) {
                try {
                    radius = Math.max(8, Math.min(128, Integer.parseInt(args[1])));
                } catch (NumberFormatException e) {
                    player.getSender().sendMessage("@cya@[WorldBots] @red@Radius must be a number.");
                    return;
                }
            }
            for (String line : manager.getNearbyReport(player, radius).split("\\n")) {
                player.getSender().sendMessage("@cya@[WorldBots] @whi@" + line);
            }
            return;
        }

        if (subCommand.equals("top")) {
            for (String line : manager.getLeaderboardReport().split("\\n")) {
                player.getSender().sendMessage("@cya@[WorldBots] @whi@" + line);
            }
            return;
        }

        if (subCommand.equals("activity") || subCommand.equals("feed") || subCommand.equals("events")) {
            for (String line : manager.getActivityReport().split("\\n")) {
                player.getSender().sendMessage("@cya@[WorldBots] @whi@" + line);
            }
            return;
        }

        if (subCommand.equals("hint") || subCommand.equals("helpme")) {
            player.getSender().sendMessage("@cya@[WorldBots] @whi@" + manager.getHelperHint(player));
            return;
        }

        if (subCommand.equals("lookup") || subCommand.equals("find")) {
            if (args.length < 2) {
                player.getSender().sendMessage("@cya@[WorldBots] @red@Usage: ::worldbots lookup <name>");
                return;
            }
            StringBuilder query = new StringBuilder(args[1]);
            for (int i = 2; i < args.length; i++) {
                query.append(" ").append(args[i]);
            }
            for (String line : manager.lookupBot(query.toString()).split("\\n")) {
                player.getSender().sendMessage("@cya@[WorldBots] @whi@" + line);
            }
            return;
        }

        if (subCommand.equals("config")) {
            for (String line : manager.getConfigReport().split("\\n")) {
                player.getSender().sendMessage("@cya@[WorldBots] @whi@" + line);
            }
            return;
        }

        if (subCommand.equals("settings") || subCommand.equals("menu")) {
            showWorldBotSettingsMenu(player);
            return;
        }

        if (subCommand.equals("trade")) {
            if (args.length > 1) {
                StringBuilder query = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; i++) {
                    query.append(" ").append(args[i]);
                }
                manager.tradeWithNamedBot(player, query.toString());
            } else {
                manager.tradeWithNearestBot(player);
            }
            return;
        }

        if (subCommand.equals("goal") || subCommand.equals("train")) {
            if (args.length < 4) {
                player.getSender().sendMessage("@cya@[WorldBots] @red@Usage: ::worldbots goal <bot name> <skill> <level>");
                return;
            }
            int targetLevel = parseCommandInt(args, args.length - 1, -1);
            String skill = args[args.length - 2];
            StringBuilder botName = new StringBuilder(args[1]);
            for (int i = 2; i < args.length - 2; i++) botName.append(' ').append(args[i]);
            player.getSender().sendMessage("@cya@[WorldBots] @whi@"
                    + manager.setBotSkillGoal(botName.toString(), skill, targetLevel));
            return;
        }

        if (subCommand.equals("cleargoal")) {
            if (args.length < 2) {
                player.getSender().sendMessage("@cya@[WorldBots] @red@Usage: ::worldbots cleargoal <bot name>");
                return;
            }
            StringBuilder botName = new StringBuilder(args[1]);
            for (int i = 2; i < args.length; i++) botName.append(' ').append(args[i]);
            player.getSender().sendMessage("@cya@[WorldBots] @whi@" + manager.clearBotGoal(botName.toString()));
            return;
        }

        if (subCommand.equals("visit") || subCommand.equals("spectate")) {
            if (args.length < 2) {
                player.getSender().sendMessage("@cya@[WorldBots] @red@Usage: ::worldbots visit <bot name>");
                return;
            }
            StringBuilder botName = new StringBuilder(args[1]);
            for (int i = 2; i < args.length; i++) botName.append(' ').append(args[i]);
            player.getSender().sendMessage("@cya@[WorldBots] @whi@" + manager.visitBot(player, botName.toString()));
            return;
        }

        if (subCommand.equals("group") || subCommand.equals("party") || subCommand.equals("team")) {
            handleWorldBotGroupCommand(args, player, manager);
            return;
        }

        if (subCommand.equals("save")) {
            manager.saveState();
            player.getSender().sendMessage("@cya@[WorldBots] @whi@Saved world bot state.");
            return;
        }

        player.getSender().sendMessage("@cya@[WorldBots] @whi@Usage: ::worldbots start [count], stop, status, nearby [radius], top, activity, hint, lookup <name>, trade, goal <name> <skill> <level>, cleargoal <name>, visit <name>, group, config, settings, save");
    }

    private void handleWorldBotGroupCommand(String[] args, Player player, WorldBotManager manager) {
        String action = args.length > 1 ? args[1].toLowerCase() : "status";

        if (action.equals("nearest")) {
            player.getSender().sendMessage("@cya@[WorldBots] @whi@" + manager.inviteNearest(player));
            return;
        }

        if (action.equals("nearby")) {
            int count = parseCommandInt(args, 2, 3);
            int radius = parseCommandInt(args, 3, 32);
            player.getSender().sendMessage("@cya@[WorldBots] @whi@" + manager.inviteNearby(player, count, radius));
            return;
        }

        if (action.equals("all")) {
            int count = parseCommandInt(args, 2, 8);
            player.getSender().sendMessage("@cya@[WorldBots] @whi@" + manager.inviteAny(player, count));
            return;
        }

        if (action.equals("role")) {
            if (args.length < 3) {
                player.getSender().sendMessage("@cya@[WorldBots] @red@Usage: ::worldbots group role <skiller|fighter|pker> [count]");
                return;
            }
            int count = parseCommandInt(args, 3, 3);
            player.getSender().sendMessage("@cya@[WorldBots] @whi@" + manager.inviteRole(player, args[2], count));
            return;
        }

        if (action.equals("clan")) {
            if (args.length < 3) {
                player.getSender().sendMessage("@cya@[WorldBots] @red@Usage: ::worldbots group clan <name> [count]");
                return;
            }
            int count = parseCommandInt(args, args.length - 1, 3);
            StringBuilder clan = new StringBuilder(args[2]);
            int end = args.length > 3 && isInteger(args[args.length - 1]) ? args.length - 1 : args.length;
            for (int i = 3; i < end; i++) {
                clan.append(" ").append(args[i]);
            }
            player.getSender().sendMessage("@cya@[WorldBots] @whi@" + manager.inviteClan(player, clan.toString(), count));
            return;
        }

        if (action.equals("mode")) {
            if (args.length < 3) {
                player.getSender().sendMessage("@cya@[WorldBots] @red@Usage: ::worldbots group mode <boss|combat|skill|wild|social>");
                return;
            }
            player.getSender().sendMessage("@cya@[WorldBots] @whi@" + manager.setPartyMode(player, args[2]));
            return;
        }

        if (action.equals("trade")) {
            manager.tradeWithGroupedBot(player);
            return;
        }

        if (action.equals("dismiss") || action.equals("leave") || action.equals("clear")) {
            player.getSender().sendMessage("@cya@[WorldBots] @whi@" + manager.dismissParty(player));
            return;
        }

        if (action.equals("status")) {
            for (String line : manager.getPartyReport(player).split("\\n")) {
                player.getSender().sendMessage("@cya@[WorldBots] @whi@" + line);
            }
            return;
        }

        player.getSender().sendMessage("@cya@[WorldBots] @whi@Usage: ::worldbots group nearest|nearby [count] [radius]|all [count]|role <skiller|fighter|pker> [count]|clan <name> [count]|mode <boss|combat|skill|wild|social>|trade|status|dismiss");
    }

    private int parseCommandInt(String[] args, int index, int fallback) {
        if (index < 0 || index >= args.length) {
            return fallback;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void showWorldBotSettingsMenu(final Player player) {
        final WorldBotManager manager = WorldBotManager.getInstance();
        final int currentCount = manager.isRunning() ? manager.getBotCount() : manager.getDefaultCount();
        final int currentAggression = manager.getAggression();
        final int currentChat = manager.getChatFrequency();
        final boolean currentRun = manager.isRunning();

        String[] options = {
            "Bot count: " + currentCount,
            "Aggression: " + manager.getAggressionLabel(),
            "Chat: " + manager.getChatLabel(),
            currentRun ? "Stop world bots" : "Start world bots",
            "Save and close"
        };
        player.setMenuHandler(new MenuHandler(options) {
            @Override
            public void handleReply(int option, String reply) {
                owner.resetMenuHandler();
                if (option == 0) {
                    showWorldBotCountMenu(owner, currentAggression, currentChat, currentRun);
                } else if (option == 1) {
                    showWorldBotAggressionMenu(owner, currentCount, currentChat, currentRun);
                } else if (option == 2) {
                    showWorldBotChatMenu(owner, currentCount, currentAggression, currentRun);
                } else if (option == 3) {
                    manager.applyRuntimeSettings(currentCount, currentAggression, currentChat, !currentRun);
                    owner.getSender().sendMessage("@cya@[WorldBots] @whi@World bots are now " + (manager.isRunning() ? "running." : "stopped."));
                    showWorldBotSettingsMenu(owner);
                } else {
                    manager.applyRuntimeSettings(currentCount, currentAggression, currentChat, currentRun);
                    owner.getSender().sendMessage("@cya@[WorldBots] @whi@Saved world bot settings.");
                }
            }
        });
        player.getSender().sendMenu(options);
    }

    private void showWorldBotCountMenu(final Player player, final int aggression, final int chat, final boolean shouldRun) {
        final int[] counts = { 50, 100, 150, 200 };
        String[] options = { "50 bots", "100 bots", "150 bots", "200 bots" };
        player.setMenuHandler(new MenuHandler(options) {
            @Override
            public void handleReply(int option, String reply) {
                owner.resetMenuHandler();
                if (option >= 0 && option < counts.length) {
                    WorldBotManager.getInstance().applyRuntimeSettings(counts[option], aggression, chat, shouldRun);
                    owner.getSender().sendMessage("@cya@[WorldBots] @whi@Bot count set to " + counts[option] + ".");
                }
                showWorldBotSettingsMenu(owner);
            }
        });
        player.getSender().sendMenu(options);
    }

    private void showWorldBotAggressionMenu(final Player player, final int count, final int chat, final boolean shouldRun) {
        final int[] values = { 0, 1, 3, 4, 5 };
        String[] options = { "Peaceful", "Low", "Normal", "High", "Dangerous" };
        player.setMenuHandler(new MenuHandler(options) {
            @Override
            public void handleReply(int option, String reply) {
                owner.resetMenuHandler();
                if (option >= 0 && option < values.length) {
                    WorldBotManager.getInstance().applyRuntimeSettings(count, values[option], chat, shouldRun);
                    owner.getSender().sendMessage("@cya@[WorldBots] @whi@Aggression set to " + options[option].toLowerCase() + ".");
                }
                showWorldBotSettingsMenu(owner);
            }
        });
        player.getSender().sendMenu(options);
    }

    private void showWorldBotChatMenu(final Player player, final int count, final int aggression, final boolean shouldRun) {
        final int[] values = { 0, 1, 3 };
        String[] options = { "Quiet", "Normal", "Chatty" };
        player.setMenuHandler(new MenuHandler(options) {
            @Override
            public void handleReply(int option, String reply) {
                owner.resetMenuHandler();
                if (option >= 0 && option < values.length) {
                    WorldBotManager.getInstance().applyRuntimeSettings(count, aggression, values[option], shouldRun);
                    owner.getSender().sendMessage("@cya@[WorldBots] @whi@Chat set to " + options[option].toLowerCase() + ".");
                }
                showWorldBotSettingsMenu(owner);
            }
        });
        player.getSender().sendMenu(options);
    }


    private void handleGrandExchangeCommand(String[] args, Player player) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            player.getSender().sendMessage("@cya@=== Grand Exchange Commands ===");
            player.getSender().sendMessage("@whi@::ge list - Show market stock and prices");
            player.getSender().sendMessage("@whi@::ge deposit <itemId> [amount|all] - Sell items");
            player.getSender().sendMessage("@whi@::ge withdraw <itemId> [amount|all] - Buy items");
            player.getSender().sendMessage("@whi@::ge depositall - Sell tradable inventory");
            player.getSender().sendMessage("@whi@::ge market - Show market volume");
            return;
        }

        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("list")) {
            listGrandExchange(player);
            return;
        }

        if (subCommand.equals("market")) {
            for (String line : GrandExchange.getMarketReport().split("\\n")) {
                player.getSender().sendMessage("@cya@[GE] @whi@" + line);
            }
            return;
        }

        if (subCommand.equals("depositall")) {
            int deposited = GrandExchange.depositInventory(player);
            player.getSender().sendMessage("@cya@[GE] @whi@Sold " + deposited + " tradable item" + (deposited == 1 ? "" : "s") + ".");
            return;
        }

        if (args.length < 2) {
            player.getSender().sendMessage("@cya@[GE] @red@Usage: ::ge " + subCommand + " <itemId> [amount|all]");
            return;
        }

        int itemId;
        try {
            itemId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.getSender().sendMessage("@cya@[GE] @red@Item id must be a number.");
            return;
        }

        if (itemId < 0 || itemId >= EntityManager.getItems().length || EntityManager.getItem(itemId) == null) {
            player.getSender().sendMessage("@cya@[GE] @red@Unknown item id: " + itemId);
            return;
        }

        int amount = parseExchangeAmount(args, player, itemId,
                subCommand.equals("withdraw") || subCommand.equals("pickup"));
        if (amount < 1) {
            player.getSender().sendMessage("@cya@[GE] @red@No items available for that action.");
            return;
        }

        if (subCommand.equals("deposit")) {
            int coins = GrandExchange.sellPrice(itemId, amount);
            if (GrandExchange.deposit(player, itemId, amount)) {
                player.getSender().sendMessage("@cya@[GE] @whi@Sold " + amount + " " + EntityManager.getItem(itemId).getName() + " for " + coins + " coins.");
            } else {
                player.getSender().sendMessage("@cya@[GE] @red@Could not sell that item.");
            }
            return;
        }

        if (subCommand.equals("withdraw") || subCommand.equals("pickup")) {
            int coins = GrandExchange.buyPrice(itemId, amount);
            if (GrandExchange.withdraw(player, itemId, amount)) {
                player.getSender().sendMessage("@cya@[GE] @whi@Bought " + amount + " " + EntityManager.getItem(itemId).getName() + " for " + coins + " coins.");
            } else {
                player.getSender().sendMessage("@cya@[GE] @red@Could not buy that item.");
            }
            return;
        }

        player.getSender().sendMessage("@cya@[GE] @red@Unknown command. Use ::ge help.");
    }

    private int parseExchangeAmount(String[] args, Player player, int itemId, boolean withdrawing) {
        int available = withdrawing ? GrandExchange.countId(itemId) : player.getInventory().countId(itemId);
        if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
            return available;
        }

        try {
            return Math.min(Integer.parseInt(args[2]), available);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void listGrandExchange(Player player) {
        List<InvItem> stock = GrandExchange.getStockSnapshot();
        if (stock.isEmpty()) {
            player.getSender().sendMessage("@cya@[GE] @whi@The Grand Exchange is empty.");
            return;
        }

        player.getSender().sendMessage("@cya@=== Grand Exchange Stock ===");
        for (int i = 0; i < stock.size() && i < 20; i++) {
            InvItem item = stock.get(i);
            player.getSender().sendMessage("@whi@" + item.getID() + " - " + item.getDef().getName()
                    + ": " + item.getAmount()
                    + " buy " + GrandExchange.buyPrice(item.getID(), 1)
                    + " sell " + GrandExchange.sellPrice(item.getID(), 1));
        }
    }
    
}

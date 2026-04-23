package org.nemotech.rsc.plugins.commands;

import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.plugins.Plugin;
import org.nemotech.rsc.plugins.listeners.action.CommandListener;
import org.nemotech.rsc.bot.Bot;
import org.nemotech.rsc.bot.BotAPI;
import org.nemotech.rsc.bot.BotManager;
import org.nemotech.rsc.bot.scripts.*;

/**
 * Command plugin for controlling bots via chat commands.
 * 
 * Commands:
 * - ::bot list - Lists all registered bots
 * - ::bot start <name> - Starts a bot
 * - ::bot stop <name> - Stops a bot
 * - ::bot stop - Stops the active bot
 * - ::bot pause - Pauses/resumes the active bot
 * - ::bot status - Shows status of all bots
 * 
 * Quick Start Commands:
 * - ::woodcut [type] - Woodcutting (normal/oak/willow/maple/yew/magic)
 * - ::fish [type] - Fishing (net/fly/cage/harpoon/shark)
 * - ::mine [type] - Mining (copper/tin/iron/coal/gold/mith/addy/rune)
 * - ::combat [target] - Combat training
 * - ::agility [course] - Agility (gnome/barb/wild)
 * - ::cook - Cooking
 * - ::fm - Firemaking
 * - ::thieve [target] - Thieving
 * - ::prayer - Prayer (bury bones)
 * - ::ranged [target] - Ranged combat
 * - ::magic [target] - Magic combat
 * - ::smith - Smithing
 * - ::fletch - Fletching
 * - ::craft [mode] - Crafting (leather/spinning/pottery)
 * - ::herblaw [mode] - Herblaw (identify/potions)
 * - ::debugobjects [radius] - List nearby objects (for finding IDs)
 */
public class BotCommands extends Plugin implements CommandListener {
    
    public String[] getCommands() {
        return new String[] { 
            "bot", "botarea", "woodcut", "wc", "fish", "combat", "fight", 
            "mine", "mining", "agility", "agil", "cook", "cooking",
            "firemaking", "fm", "thieve", "thieving", "pickpocket",
            "prayer", "pray", "ranged", "range", "magic", "mage",
            "smith", "smithing", "fletch", "fletching", "craft", "crafting",
            "herblaw", "herblore",
            "debugobjects", "listobjects"
        };
    }
    
    @Override
    public void onCommand(String command, String[] args, Player player) {
        
        // Main bot command
        if (command.equals("bot")) {
            handleBotCommand(args, player);
            return;
        }
        
        // Bot area command
        if (command.equals("botarea")) {
            handleBotAreaCommand(args, player);
            return;
        }
        
        // Debug command to list nearby objects
        if (command.equals("debugobjects") || command.equals("listobjects")) {
            int radius = args.length > 0 ? Integer.parseInt(args[0]) : 15;
            BotAPI.getInstance().debugListNearbyObjects(radius);
            return;
        }
        
        // Quick start commands
        if (command.equals("woodcut") || command.equals("wc")) {
            startWoodcutting(args, player);
            return;
        }
        
        if (command.equals("fish")) {
            startFishing(args, player);
            return;
        }
        
        if (command.equals("combat") || command.equals("fight")) {
            startCombat(args, player);
            return;
        }
        
        if (command.equals("mine") || command.equals("mining")) {
            startMining(args, player);
            return;
        }
        
        if (command.equals("agility") || command.equals("agil")) {
            startAgility(args, player);
            return;
        }
        
        if (command.equals("cook") || command.equals("cooking")) {
            startCooking(args, player);
            return;
        }
        
        if (command.equals("firemaking") || command.equals("fm")) {
            startFiremaking(args, player);
            return;
        }
        
        if (command.equals("thieve") || command.equals("thieving") || command.equals("pickpocket")) {
            startThieving(args, player);
            return;
        }
        
        if (command.equals("prayer") || command.equals("pray")) {
            startPrayer(args, player);
            return;
        }
        
        if (command.equals("ranged") || command.equals("range")) {
            startRanged(args, player);
            return;
        }
        
        if (command.equals("magic") || command.equals("mage")) {
            startMagic(args, player);
            return;
        }
        
        if (command.equals("smith") || command.equals("smithing")) {
            startSmithing(args, player);
            return;
        }
        
        if (command.equals("fletch") || command.equals("fletching")) {
            startFletching(args, player);
            return;
        }
        
        if (command.equals("craft") || command.equals("crafting")) {
            startCrafting(args, player);
            return;
        }
        
        if (command.equals("herblaw") || command.equals("herb") || command.equals("herblore")) {
            startHerblaw(args, player);
            return;
        }
        
        // Quick stop command
        if (command.equals("stopbot") || command.equals("botoff")) {
            BotManager.getInstance().stopAll();
            player.getSender().sendMessage("@cya@[Bot] @whi@All bots stopped.");
            return;
        }
    }
    
    private void handleBotCommand(String[] args, Player player) {
        if (args.length == 0) {
            showBotHelp(player);
            return;
        }
        
        String subCommand = args[0].toLowerCase();
        BotManager manager = BotManager.getInstance();
        
        switch (subCommand) {
            case "list":
                listBots(player);
                break;
                
            case "start":
                if (args.length < 2) {
                    player.getSender().sendMessage("@cya@[Bot] @whi@Usage: ::bot start <name>");
                    return;
                }
                String startName = args[1];
                if (manager.startBot(startName)) {
                    player.getSender().sendMessage("@cya@[Bot] @gre@Started bot: " + startName);
                } else {
                    player.getSender().sendMessage("@cya@[Bot] @red@Bot not found: " + startName);
                }
                break;
                
            case "stop":
                if (args.length < 2) {
                    // Stop active bot
                    Bot active = manager.getActiveBot();
                    if (active != null) {
                        active.stop();
                        player.getSender().sendMessage("@cya@[Bot] @whi@Stopped active bot: " + active.getName());
                    } else {
                        player.getSender().sendMessage("@cya@[Bot] @whi@No active bot to stop.");
                    }
                } else {
                    String stopName = args[1];
                    if (manager.stopBot(stopName)) {
                        player.getSender().sendMessage("@cya@[Bot] @whi@Stopped bot: " + stopName);
                    } else {
                        player.getSender().sendMessage("@cya@[Bot] @red@Bot not found or not running: " + stopName);
                    }
                }
                break;
                
            case "pause":
                Bot active = manager.getActiveBot();
                if (active != null && active.isRunning()) {
                    active.togglePause();
                    String status = active.isPaused() ? "paused" : "resumed";
                    player.getSender().sendMessage("@cya@[Bot] @whi@Bot " + status + ": " + active.getName());
                } else {
                    player.getSender().sendMessage("@cya@[Bot] @whi@No active bot.");
                }
                break;
                
            case "status":
                showStatus(player);
                break;
                
            case "help":
            default:
                showBotHelp(player);
                break;
        }
    }
    
    private void showBotHelp(Player player) {
        player.getSender().sendMessage("@cya@=== Bot Commands ===");
        player.getSender().sendMessage("@whi@::bot list - List registered bots");
        player.getSender().sendMessage("@whi@::bot start/stop <name> - Start/stop a bot");
        player.getSender().sendMessage("@whi@::bot pause - Pause/resume active bot");
        player.getSender().sendMessage("@whi@::bot status - Show bot status");
        player.getSender().sendMessage("@cya@=== Quick Start (Gathering) ===");
        player.getSender().sendMessage("@whi@::woodcut, ::fish, ::mine - Gathering skills");
        player.getSender().sendMessage("@cya@=== Quick Start (Combat) ===");
        player.getSender().sendMessage("@whi@::combat, ::ranged, ::magic - Combat skills");
        player.getSender().sendMessage("@cya@=== Quick Start (Other) ===");
        player.getSender().sendMessage("@whi@::agility, ::thieve, ::prayer");
        player.getSender().sendMessage("@whi@::cook, ::fm, ::smith, ::fletch");
        player.getSender().sendMessage("@whi@::craft, ::herblaw");
        player.getSender().sendMessage("@whi@::stopbot - Stop all bots");
    }
    
    private void handleBotAreaCommand(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        Bot active = manager.getActiveBot();
        
        if (active == null) {
            player.getSender().sendMessage("@cya@[Bot] @red@No active bot running.");
            return;
        }
        
        if (active instanceof WoodcuttingBot) {
            WoodcuttingBot woodbot = (WoodcuttingBot) active;
            
            if (args.length == 0) {
                player.getSender().sendMessage("@cya@[Bot] @whi@Usage: ::botarea <location>");
                player.getSender().sendMessage("@whi@Locations: seers, varrock, falador, draynor, portsarim, karamja, alkharid, lumbridge, edgeville, taverly, barbarian, rimmington, catherby, ardougne, yanille, lostcity, gnome, tutorial");
                player.getSender().sendMessage("@whi@Or coords: ::botarea <minX> <maxX> <minY> <maxY>");
                player.getSender().sendMessage("@whi@Use ::botarea clear to remove bounds");
                if (woodbot.areaMinX != null) {
                    player.getSender().sendMessage("@whi@Current area: " + woodbot.areaMinX + "-" + woodbot.areaMaxX + ", " + woodbot.areaMinY + "-" + woodbot.areaMaxY);
                } else {
                    player.getSender().sendMessage("@whi@Current area: Unbounded");
                }
                return;
            }
            
            if (args[0].equalsIgnoreCase("clear")) {
                woodbot.clearAreaBounds();
                player.getSender().sendMessage("@cya@[Bot] @red@Area bounds cleared.");
                return;
            }
            
            if (args.length == 1) {
                String loc = args[0].toLowerCase();
                setAreaByLocation(woodbot, loc, player);
                return;
            }
            
            if (args.length < 4) {
                player.getSender().sendMessage("@cya@[Bot] @red@Usage: ::botarea <location> or ::botarea <minX> <maxX> <minY> <maxY>");
                return;
            }
            
            try {
                int minX = Integer.parseInt(args[0]);
                int maxX = Integer.parseInt(args[1]);
                int minY = Integer.parseInt(args[2]);
                int maxY = Integer.parseInt(args[3]);
                
                woodbot.setAreaBounds(minX, maxX, minY, maxY);
                player.getSender().sendMessage("@cya@[Bot] @gre@Area set: " + minX + "-" + maxX + ", " + minY + "-" + maxY);
            } catch (NumberFormatException e) {
                player.getSender().sendMessage("@cya@[Bot] @red@Invalid coordinates.");
            }
        } else {
            player.getSender().sendMessage("@cya@[Bot] @red@This command only works with Woodcutting bot.");
        }
    }
    
    private void setAreaByLocation(WoodcuttingBot woodbot, String loc, Player player) {
        switch (loc) {
            case "varrock": woodbot.setAreaBounds(100, 160, 480, 550); break;
            case "falador": woodbot.setAreaBounds(280, 340, 510, 580); break;
            case "draynor": woodbot.setAreaBounds(190, 240, 600, 660); break;
            case "portsarim": woodbot.setAreaBounds(250, 290, 620, 670); break;
            case "karamja": woodbot.setAreaBounds(350, 400, 660, 710); break;
            case "alkharid": woodbot.setAreaBounds(70, 120, 660, 720); break;
            case "lumbridge": woodbot.setAreaBounds(100, 160, 620, 680); break;
            case "edgeville": woodbot.setAreaBounds(190, 250, 420, 480); break;
            case "taverly": woodbot.setAreaBounds(350, 400, 470, 530); break;
            case "seers": case "seersvillage": woodbot.setAreaBounds(480, 550, 420, 480); break;
            case "barbarian": woodbot.setAreaBounds(210, 260, 490, 540); break;
            case "rimmington": woodbot.setAreaBounds(300, 350, 640, 690); break;
            case "catherby": woodbot.setAreaBounds(420, 470, 480, 530); break;
            case "camelot": woodbot.setAreaBounds(480, 560, 330, 430); break;
            case "ardougne": woodbot.setAreaBounds(520, 580, 560, 620); break;
            case "yanille": woodbot.setAreaBounds(560, 620, 720, 780); break;
            case "lostcity": woodbot.setAreaBounds(100, 160, 3490, 3550); break;
            case "gnome": woodbot.setAreaBounds(680, 740, 500, 560); break;
            case "tutorial": woodbot.setAreaBounds(190, 250, 720, 770); break;
            default:
                player.getSender().sendMessage("@cya@[Bot] @red@Unknown location: " + loc);
                return;
        }
        player.getSender().sendMessage("@cya@[Bot] @gre@Area set to: " + loc.toUpperCase());
    }
    
    private void listBots(Player player) {
        BotManager manager = BotManager.getInstance();
        String[] names = manager.getBotNames();
        
        if (names.length == 0) {
            player.getSender().sendMessage("@cya@[Bot] @whi@No bots registered. Use ::woodcut, ::fish, or ::combat to start.");
            return;
        }
        
        player.getSender().sendMessage("@cya@=== Registered Bots ===");
        for (String name : names) {
            Bot bot = manager.getBot(name);
            String status = bot.isRunning() ? (bot.isPaused() ? "@yel@PAUSED" : "@gre@RUNNING") : "@red@STOPPED";
            player.getSender().sendMessage("@whi@- " + bot.getName() + ": " + status);
        }
    }
    
    private void showStatus(Player player) {
        BotManager manager = BotManager.getInstance();
        Bot active = manager.getActiveBot();
        
        if (active == null) {
            player.getSender().sendMessage("@cya@[Bot] @whi@No active bot.");
            return;
        }
        
        player.getSender().sendMessage("@cya@=== Bot Status ===");
        player.getSender().sendMessage("@whi@Name: @gre@" + active.getName());
        player.getSender().sendMessage("@whi@Status: " + (active.isPaused() ? "@yel@PAUSED" : "@gre@RUNNING"));
        player.getSender().sendMessage("@whi@Runtime: @gre@" + active.getRuntimeFormatted());
        player.getSender().sendMessage("@whi@Iterations: @gre@" + active.getIterations());
    }
    
    private void startWoodcutting(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        int[] treeIds;
        int[] logIds;
        String type = args.length > 0 ? args[0].toLowerCase() : "normal";
        
        switch (type) {
            case "oak":
                treeIds = new int[] { 306 };
                logIds = new int[] { 632 };
                break;
            case "willow":
                treeIds = new int[] { 307 };
                logIds = new int[] { 633 };
                break;
            case "maple":
                treeIds = new int[] { 308 };
                logIds = new int[] { 634 };
                break;
            case "yew":
                treeIds = new int[] { 309 };
                logIds = new int[] { 635 };
                break;
            case "magic":
                treeIds = new int[] { 310 };
                logIds = new int[] { 636 };
                break;
            case "normal":
            case "tree":
            case "regular":
            default:
                treeIds = new int[] { 0, 1, 70 };
                logIds = new int[] { 14 };
                type = "normal";
                break;
        }
        
        WoodcuttingBot bot = new WoodcuttingBot(treeIds, logIds);
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started " + type + " woodcutting bot!");
    }
    
    private void startFishing(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        int[] spotIds;
        int[] fishIds;
        String type = args.length > 0 ? args[0].toLowerCase() : "net";
        
        switch (type) {
            case "fly":
            case "lure":
                spotIds = new int[] { 193 };
                fishIds = new int[] { 358, 356 }; // Trout, Salmon
                break;
            case "cage":
            case "lobster":
                spotIds = new int[] { 194 };
                fishIds = new int[] { 372 }; // Lobster
                break;
            case "harpoon":
            case "swordfish":
                spotIds = new int[] { 194 };
                fishIds = new int[] { 366, 369 }; // Tuna, Swordfish
                break;
            case "shark":
                spotIds = new int[] { 261 };
                fishIds = new int[] { 545 }; // Shark
                break;
            default:
                spotIds = new int[] { 192 };
                fishIds = new int[] { 349, 351 }; // Shrimp, Anchovies
                type = "net";
                break;
        }
        
        FishingBot bot = new FishingBot(spotIds, fishIds);
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started " + type + " fishing bot!");
    }
    
    private void startCombat(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        int[] npcIds;
        String target;
        
        if (args.length > 0) {
            try {
                // Try to parse as NPC ID
                int npcId = Integer.parseInt(args[0]);
                npcIds = new int[] { npcId };
                target = "NPC ID " + npcId;
            } catch (NumberFormatException e) {
                // Use predefined targets
                String type = args[0].toLowerCase();
                switch (type) {
                    case "chicken":
                        npcIds = new int[] { 3 };
                        target = "Chickens";
                        break;
                    case "cow":
                        npcIds = new int[] { 6 };
                        target = "Cows";
                        break;
                    case "goblin":
                        npcIds = new int[] { 62 };
                        target = "Goblins";
                        break;
                    case "skeleton":
                        npcIds = new int[] { 45 };
                        target = "Skeletons";
                        break;
                    case "zombie":
                        npcIds = new int[] { 68 };
                        target = "Zombies";
                        break;
                    case "giant":
                    case "hill":
                        npcIds = new int[] { 61 };
                        target = "Hill Giants";
                        break;
                    case "moss":
                        npcIds = new int[] { 104 };
                        target = "Moss Giants";
                        break;
                    case "fire":
                        npcIds = new int[] { 344 };
                        target = "Fire Giants";
                        break;
                    case "lesser":
                        npcIds = new int[] { 82 };
                        target = "Lesser Demons";
                        break;
                    case "greater":
                        npcIds = new int[] { 87 };
                        target = "Greater Demons";
                        break;
                    default:
                        npcIds = new int[] { 3 };
                        target = "Chickens";
                        break;
                }
            }
        } else {
            npcIds = new int[] { 3 };
            target = "Chickens";
        }
        
        CombatBot bot = new CombatBot(npcIds);
        
        // Set food if the player has any common food
        bot.setFoodIds(132, 138, 53, 359, 357, 373, 370, 546);
        bot.setEatAtPercent(50);
        
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started combat bot targeting: " + target);
    }
    
    private void startMining(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        int[] rockIds;
        int[] oreIds;
        String type = args.length > 0 ? args[0].toLowerCase() : "copper";
        
        switch (type) {
            case "tin":
                rockIds = new int[] { 104, 105 };
                oreIds = new int[] { 202 };
                break;
            case "iron":
                rockIds = new int[] { 102, 103 };
                oreIds = new int[] { 151 };
                break;
            case "coal":
                rockIds = new int[] { 110, 111 };
                oreIds = new int[] { 155 };
                break;
            case "gold":
                rockIds = new int[] { 112, 113 };
                oreIds = new int[] { 152 };
                break;
            case "mithril":
            case "mith":
                rockIds = new int[] { 106, 107 };
                oreIds = new int[] { 153 };
                break;
            case "adamantite":
            case "addy":
                rockIds = new int[] { 108, 109 };
                oreIds = new int[] { 154 };
                break;
            case "runite":
            case "rune":
                rockIds = new int[] { 210 };
                oreIds = new int[] { 409 };
                break;
            default:
                rockIds = new int[] { 100, 101 };
                oreIds = new int[] { 150 };
                type = "copper";
                break;
        }
        
        MiningBot bot = new MiningBot(rockIds, oreIds);
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started " + type + " mining bot!");
    }
    
    private void startAgility(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        String type = args.length > 0 ? args[0].toLowerCase() : "gnome";
        AgilityBot.Course course;
        
        switch (type) {
            case "barbarian":
            case "barb":
                course = AgilityBot.Course.BARBARIAN;
                type = "barbarian";
                break;
            case "wilderness":
            case "wild":
            case "wildy":
                course = AgilityBot.Course.WILDERNESS;
                type = "wilderness";
                break;
            default:
                course = AgilityBot.Course.GNOME;
                type = "gnome";
                break;
        }
        
        AgilityBot bot = new AgilityBot(course);
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started " + type + " agility course bot!");
    }
    
    private void startCooking(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        CookingBot bot = new CookingBot();
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started cooking bot! Have raw food and stand near a range/fire.");
    }
    
    private void startFiremaking(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        FiremakingBot bot = new FiremakingBot();
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started firemaking bot! Have tinderbox and logs.");
    }
    
    private void startThieving(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        int[] npcIds;
        String target;
        
        if (args.length > 0) {
            try {
                int npcId = Integer.parseInt(args[0]);
                npcIds = new int[] { npcId };
                target = "NPC ID " + npcId;
            } catch (NumberFormatException e) {
                String type = args[0].toLowerCase();
                switch (type) {
                    case "farmer":
                        npcIds = new int[] { 63 };
                        target = "Farmers";
                        break;
                    case "warrior":
                        npcIds = new int[] { 24 };
                        target = "Warriors";
                        break;
                    case "guard":
                        npcIds = new int[] { 65 };
                        target = "Guards";
                        break;
                    case "knight":
                        npcIds = new int[] { 304 };
                        target = "Knights";
                        break;
                    case "paladin":
                        npcIds = new int[] { 337 };
                        target = "Paladins";
                        break;
                    case "hero":
                        npcIds = new int[] { 377 };
                        target = "Heroes";
                        break;
                    default:
                        npcIds = new int[] { 11 };
                        target = "Men";
                        break;
                }
            }
        } else {
            npcIds = new int[] { 11 };
            target = "Men";
        }
        
        ThievingBot bot = new ThievingBot(npcIds);
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started thieving bot targeting: " + target);
    }
    
    private void startPrayer(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        PrayerBot bot = new PrayerBot();
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started prayer bot! Will bury bones.");
    }
    
    private void startRanged(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        int[] npcIds = { 3 }; // Default: Chickens
        String target = "Chickens";
        
        if (args.length > 0) {
            try {
                int npcId = Integer.parseInt(args[0]);
                npcIds = new int[] { npcId };
                target = "NPC ID " + npcId;
            } catch (NumberFormatException e) {
                String type = args[0].toLowerCase();
                switch (type) {
                    case "cow":
                        npcIds = new int[] { 6 };
                        target = "Cows";
                        break;
                    case "goblin":
                        npcIds = new int[] { 62 };
                        target = "Goblins";
                        break;
                    case "skeleton":
                        npcIds = new int[] { 45 };
                        target = "Skeletons";
                        break;
                    case "zombie":
                        npcIds = new int[] { 68 };
                        target = "Zombies";
                        break;
                }
            }
        }
        
        RangedBot bot = new RangedBot(npcIds);
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started ranged bot targeting: " + target);
        player.getSender().sendMessage("@cya@[Bot] @whi@Make sure you have a bow equipped and arrows!");
    }
    
    private void startMagic(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        int[] npcIds = { 3 }; // Default: Chickens
        String target = "Chickens";
        
        if (args.length > 0) {
            try {
                int npcId = Integer.parseInt(args[0]);
                npcIds = new int[] { npcId };
                target = "NPC ID " + npcId;
            } catch (NumberFormatException e) {
                String type = args[0].toLowerCase();
                switch (type) {
                    case "cow":
                        npcIds = new int[] { 6 };
                        target = "Cows";
                        break;
                    case "goblin":
                        npcIds = new int[] { 62 };
                        target = "Goblins";
                        break;
                }
            }
        }
        
        MagicBot bot = new MagicBot(npcIds);
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started magic bot targeting: " + target);
        player.getSender().sendMessage("@cya@[Bot] @whi@Make sure you have runes for combat spells!");
    }
    
    private void startSmithing(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        SmithingBot bot = new SmithingBot();
        
        if (args.length > 0) {
            String type = args[0].toLowerCase();
            switch (type) {
                case "bronze":
                    bot.setBarIds(169);
                    break;
                case "iron":
                    bot.setBarIds(170);
                    break;
                case "steel":
                    bot.setBarIds(171);
                    break;
                case "mith":
                case "mithril":
                    bot.setBarIds(173);
                    break;
                case "addy":
                case "adamantite":
                    bot.setBarIds(174);
                    break;
                case "rune":
                case "runite":
                    bot.setBarIds(408);
                    break;
            }
        }
        
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started smithing bot! Stand near an anvil with bars.");
    }
    
    private void startFletching(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        FletchingBot bot = new FletchingBot();
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started fletching bot! Have knife + logs.");
    }
    
    private void startCrafting(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        CraftingBot.Mode mode = CraftingBot.Mode.LEATHER;
        String modeName = "leather";
        
        if (args.length > 0) {
            String type = args[0].toLowerCase();
            switch (type) {
                case "spin":
                case "spinning":
                case "wool":
                    mode = CraftingBot.Mode.SPINNING;
                    modeName = "spinning";
                    break;
                case "pottery":
                case "clay":
                    mode = CraftingBot.Mode.POTTERY;
                    modeName = "pottery";
                    break;
                default:
                    mode = CraftingBot.Mode.LEATHER;
                    modeName = "leather";
                    break;
            }
        }
        
        CraftingBot bot = new CraftingBot(mode);
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started crafting bot! Mode: " + modeName);
    }
    
    private void startHerblaw(String[] args, Player player) {
        BotManager manager = BotManager.getInstance();
        manager.stopAll();
        
        HerblawBot.Mode mode = HerblawBot.Mode.IDENTIFY;
        String modeName = "identify";
        
        if (args.length > 0) {
            String type = args[0].toLowerCase();
            switch (type) {
                case "potion":
                case "potions":
                case "mix":
                    mode = HerblawBot.Mode.POTIONS;
                    modeName = "potions";
                    break;
                default:
                    mode = HerblawBot.Mode.IDENTIFY;
                    modeName = "identify";
                    break;
            }
        }
        
        HerblawBot bot = new HerblawBot(mode);
        manager.register(bot);
        manager.startBot(bot.getName());
        
        player.getSender().sendMessage("@cya@[Bot] @gre@Started herblaw bot! Mode: " + modeName);
    }
}

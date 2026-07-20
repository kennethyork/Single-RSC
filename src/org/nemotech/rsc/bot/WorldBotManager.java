package org.nemotech.rsc.bot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.nemotech.rsc.Constants;
import org.nemotech.rsc.event.DelayedEvent;
import org.nemotech.rsc.model.GrandExchange;
import org.nemotech.rsc.model.Item;
import org.nemotech.rsc.model.NPC;
import org.nemotech.rsc.model.Point;
import org.nemotech.rsc.model.World;
import org.nemotech.rsc.model.landscape.Path;
import org.nemotech.rsc.model.player.InvItem;
import org.nemotech.rsc.model.player.Player;

public final class WorldBotManager {

    private static final int GATHERER_NPC = 795;
    private static final int FIGHTER_NPC = 796;
    private static final int WILDERNESS_NPC = 797;
    private static final int PLAYER_SERVER_INDEX_BASE = 3000;
    private static final int COINS = 10;
    private static final int DEFAULT_BOT_COUNT = 200;
    private static final int DEFAULT_MAX_BOT_COUNT = 200;
    private static final long MIN_SESSION_MS = 5 * 60 * 1000L;
    private static final long SESSION_VARIANCE_MS = 10 * 60 * 1000L;
    private static final long MIN_OFFLINE_MS = 30 * 1000L;
    private static final long OFFLINE_VARIANCE_MS = 2 * 60 * 1000L;
    private static final String CONFIG_FILE = Constants.CACHE_DIRECTORY + "worldbots.properties";
    private static final String STATE_FILE = Constants.CACHE_DIRECTORY + "worldbots_state.properties";

    private static WorldBotManager instance;

    private final List<WorldBot> bots = new ArrayList<>();
    private final Random random = new Random();
    private final Config config = new Config();
    private DelayedEvent tickEvent;
    private boolean running;
    private long lastStateSave;
    private String currentWorldEvent = "quiet market";
    private long nextWorldEventAt;

    private WorldBotManager() {}

    public static WorldBotManager getInstance() {
        if (instance == null) {
            instance = new WorldBotManager();
        }
        return instance;
    }

    public synchronized void startDefaultBots() {
        loadConfig();
        if (config.autoStart) {
            startBots(config.defaultCount);
        }
    }

    public synchronized void startBots(int count) {
        loadConfig();
        stopBots();
        running = true;
        int safeCount = Math.max(1, Math.min(count, config.maxCount));
        for (int i = 0; i < safeCount; i++) {
            bots.add(createBot(i));
        }
        loadState();
        tickEvent = new DelayedEvent(null, 2500) {
            @Override
            public Object getIdentifier() {
                return "world-bot-manager";
            }

            @Override
            public void run() {
                tick();
            }
        };
        World.getWorld().getDelayedEventHandler().add(tickEvent);
    }

    public synchronized void stopBots() {
        saveState();
        if (tickEvent != null) {
            tickEvent.interrupt();
            tickEvent = null;
        }
        for (WorldBot bot : bots) {
            if (bot.npc != null && !bot.npc.isRemoved()) {
                World.getWorld().unregisterNpc(bot.npc);
            }
        }
        bots.clear();
        running = false;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized int getBotCount() {
        return bots.size();
    }

    public synchronized String getStatusReport() {
        StringBuilder report = new StringBuilder();
        report.append("World bots: ").append(running ? "running" : "stopped")
                .append(" (").append(bots.size()).append(")")
                .append("\nEvent: ").append(currentWorldEvent);
        for (WorldBot bot : bots) {
            report.append("\n").append(bot.name)
                    .append(" - ").append(bot.personality.title)
                    .append(" [").append(bot.personality.clan).append("]")
                    .append(" lvl ").append(bot.level)
                    .append(" xp ").append(bot.xpRate).append("x")
                    .append(bot.statusText())
                    .append(" goal ").append(bot.goal.label)
                    .append(" doing ").append(bot.activity)
                    .append(" inv ").append(bot.inventorySize())
                    .append(" coins ").append(bot.coins())
                    .append(" kills ").append(bot.kills)
                    .append(" deaths ").append(bot.deaths);
        }
        return report.toString();
    }

    public synchronized String getNearbyReport(Player player, int radius) {
        List<WorldBot> nearby = new ArrayList<>();
        for (WorldBot bot : bots) {
            if (bot.active && bot.online && bot.npc != null && !bot.npc.isRemoved()
                    && bot.npc.getLocation().withinRange(player.getLocation(), radius)) {
                nearby.add(bot);
            }
        }
        Collections.sort(nearby, new Comparator<WorldBot>() {
            @Override
            public int compare(WorldBot first, WorldBot second) {
                return distance(first, player) - distance(second, player);
            }
        });

        StringBuilder report = new StringBuilder("Nearby world bots within ").append(radius).append(" tiles");
        if (nearby.isEmpty()) {
            report.append("\nnone nearby");
            return report.toString();
        }
        for (int i = 0; i < nearby.size() && i < 10; i++) {
            WorldBot bot = nearby.get(i);
            report.append("\n").append(bot.name)
                    .append(" d").append(distance(bot, player))
                    .append(" ").append(bot.role.label)
                    .append(" at ").append(bot.npc.getX()).append(",").append(bot.npc.getY())
                    .append(" - ").append(bot.goal.label).append(": ").append(bot.activity);
        }
        if (nearby.size() > 10) {
            report.append("\n").append(nearby.size() - 10).append(" more nearby");
        }
        return report.toString();
    }

    private int distance(WorldBot bot, Player player) {
        return Math.abs(bot.npc.getX() - player.getX()) + Math.abs(bot.npc.getY() - player.getY());
    }

    public synchronized String getLeaderboardReport() {
        List<WorldBot> ranked = new ArrayList<>(bots);
        Collections.sort(ranked, new Comparator<WorldBot>() {
            @Override
            public int compare(WorldBot first, WorldBot second) {
                int firstScore = first.kills * 100 + first.level * 10 + first.itemsBanked - first.deaths * 25;
                int secondScore = second.kills * 100 + second.level * 10 + second.itemsBanked - second.deaths * 25;
                return secondScore - firstScore;
            }
        });

        StringBuilder report = new StringBuilder("World bot leaderboard");
        for (int i = 0; i < ranked.size() && i < 10; i++) {
            WorldBot bot = ranked.get(i);
            report.append("\n").append(i + 1).append(". ").append(bot.name)
                    .append(" lvl ").append(bot.level)
                    .append(" xp ").append(bot.xpRate).append("x")
                    .append(" kills ").append(bot.kills)
                    .append(" deaths ").append(bot.deaths)
                    .append(" banked ").append(bot.itemsBanked)
                    .append(" coins ").append(bot.coins())
                    .append(" trades ").append(bot.trades)
                    .append(" score ").append(bot.score());
        }
        return report.toString();
    }

    public synchronized String lookupBot(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "Usage: ::worldbots lookup <name>";
        }
        String needle = query.toLowerCase();
        for (WorldBot bot : bots) {
            if (bot.name.toLowerCase().contains(needle)) {
                return bot.name
                        + "\nrole=" + bot.role.label
                        + "\nclan=" + bot.personality.clan
                        + "\nrival=" + bot.personality.rivalClan
                        + "\nlevel=" + bot.level
                        + "\nxp_rate=" + bot.xpRate + "x"
                        + "\nxp=" + bot.xp
                        + "\neffective_xp=" + bot.effectiveXp()
                        + "\ngoal=" + bot.goal.label
                        + "\nactivity=" + bot.activity
                        + "\ncoins=" + bot.coins()
                        + "\ntrades=" + bot.trades
                        + "\nmarket_volume=" + bot.marketVolume
                        + "\nkills=" + bot.kills
                        + "\ndeaths=" + bot.deaths
                        + "\nscore=" + bot.score();
            }
        }
        return "No world bot found matching: " + query;
    }

    public synchronized String getConfigReport() {
        return "World bot config"
                + "\nautostart=" + config.autoStart
                + "\ndefault_count=" + config.defaultCount
                + "\nmax_count=" + config.maxCount
                + "\nrespawn_seconds=" + config.respawnSeconds
                + "\nsave_every_seconds=" + config.saveEverySeconds
                + "\nchat_frequency=" + config.chatFrequency
                + "\naggression=" + config.aggression + " (" + config.aggressionLabel() + ")"
                + "\nconfig_file=" + CONFIG_FILE;
    }

    public synchronized int getDefaultCount() {
        loadConfig();
        return config.defaultCount;
    }

    public synchronized int getAggression() {
        loadConfig();
        return config.aggression;
    }

    public synchronized String getAggressionLabel() {
        loadConfig();
        return config.aggressionLabel();
    }

    public synchronized int getChatFrequency() {
        loadConfig();
        return config.chatFrequency;
    }

    public synchronized String getChatLabel() {
        loadConfig();
        if (config.chatFrequency <= 0) {
            return "quiet";
        }
        if (config.chatFrequency >= 3) {
            return "chatty";
        }
        return "normal";
    }

    public synchronized void applyRuntimeSettings(int count, int aggression, int chatFrequency, boolean shouldRun) {
        loadConfig();
        config.defaultCount = Math.max(1, Math.min(count, config.maxCount));
        config.aggression = Math.max(0, Math.min(5, aggression));
        config.chatFrequency = Math.max(0, Math.min(4, chatFrequency));
        saveConfig();

        if (shouldRun) {
            startBots(config.defaultCount);
        } else if (running) {
            stopBots();
        }
    }

    public synchronized boolean tradeWithNearestBot(Player player) {
        WorldBot nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (WorldBot bot : bots) {
            if (!bot.active || !bot.online || bot.inventory.isEmpty()) {
                continue;
            }
            int distance = Math.abs(bot.npc.getX() - player.getX()) + Math.abs(bot.npc.getY() - player.getY());
            if (distance < nearestDistance) {
                nearest = bot;
                nearestDistance = distance;
            }
        }
        if (nearest == null || nearestDistance > 8) {
            player.getSender().sendMessage("@cya@[WorldBots] @red@No trading bot nearby.");
            return false;
        }
        return nearest.tradeWith(player);
    }

    public synchronized List<Snapshot> getSnapshotsNear(Point point, int radius) {
        List<Snapshot> snapshots = new ArrayList<>();
        for (WorldBot bot : bots) {
            if (bot.active && bot.online && bot.npc != null && !bot.npc.isRemoved() && bot.npc.getLocation().withinRange(point, radius)) {
                snapshots.add(bot.snapshot());
            }
        }
        return snapshots;
    }

    public synchronized boolean isWorldBotNpc(NPC npc) {
        for (WorldBot bot : bots) {
            if (bot.npc == npc) {
                return true;
            }
        }
        return false;
    }

    public synchronized int getNpcIndexForServerIndex(int serverIndex) {
        for (WorldBot bot : bots) {
            if (bot.snapshot().serverIndex == serverIndex) {
                return bot.npc.getIndex();
            }
        }
        return -1;
    }

    public synchronized String getNameForServerIndex(int serverIndex) {
        for (WorldBot bot : bots) {
            if (bot.snapshot().serverIndex == serverIndex) {
                return bot.name;
            }
        }
        return null;
    }

    public synchronized void onPlayerAttackedBot(Player player, NPC npc) {
        WorldBot bot = findBot(npc);
        if (bot == null || !bot.active) {
            return;
        }
        bot.say(bot.personality.attackedLine(random));
    }

    public synchronized void onPlayerKilledBot(Player player, NPC npc) {
        WorldBot bot = findBot(npc);
        if (bot == null || !bot.active) {
            return;
        }
        bot.deaths++;
        bot.active = false;
        bot.say(bot.personality.deathLine(random));
        bot.dropInventory(player);
        scheduleRespawn(bot);
    }

    private WorldBot findBot(NPC npc) {
        for (WorldBot bot : bots) {
            if (bot.npc == npc) {
                return bot;
            }
        }
        return null;
    }

    private void scheduleRespawn(final WorldBot bot) {
        World.getWorld().getDelayedEventHandler().add(new DelayedEvent(null, config.respawnSeconds * 1000) {
            @Override
            public void run() {
                synchronized (WorldBotManager.this) {
                    bot.respawn();
                }
            }
        });
    }

    private void tick() {
        if (!running) {
            return;
        }
        updateWorldEvent();
        for (WorldBot bot : bots) {
            bot.tick();
        }
        if (System.currentTimeMillis() - lastStateSave > config.saveEverySeconds * 1000L) {
            saveState();
        }
    }

    private WorldBot createBot(int index) {
        Role role;
        if (index % 4 == 3) {
            role = Role.WILDERNESS;
        } else if (index % 4 == 2) {
            role = Role.FIGHTER;
        } else {
            role = Role.GATHERER;
        }

        BotArea area = role.areaFor(index);
        int npcId = role == Role.GATHERER ? GATHERER_NPC : role == Role.FIGHTER ? FIGHTER_NPC : WILDERNESS_NPC;
        NPC npc = new NPC(npcId, area.randomX(random), area.randomY(random),
                area.minX, area.maxX, area.minY, area.maxY);
        npc.setShouldRespawn(false);
        World.getWorld().registerNpc(npc);
        return new WorldBot(index, role, npc, area, Personality.forBot(index, role));
    }

    private void updateWorldEvent() {
        long now = System.currentTimeMillis();
        if (nextWorldEventAt != 0 && now < nextWorldEventAt) {
            return;
        }
        String[] events = {
            "quiet market",
            "skilling rush around banks",
            "food shortage at the exchange",
            "wilderness patrol forming",
            "ore and log prices moving",
            "clans watching Edgeville"
        };
        currentWorldEvent = events[random.nextInt(events.length)];
        nextWorldEventAt = now + 180000L + random.nextInt(240000);
    }

    private void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            saveDefaultConfig(file);
        }
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            properties.load(in);
            config.autoStart = Boolean.parseBoolean(properties.getProperty("autostart", String.valueOf(config.autoStart)));
            config.defaultCount = parseInt(properties.getProperty("default_count"), DEFAULT_BOT_COUNT);
            config.maxCount = parseInt(properties.getProperty("max_count"), DEFAULT_MAX_BOT_COUNT);
            config.respawnSeconds = parseInt(properties.getProperty("respawn_seconds"), 20);
            config.saveEverySeconds = parseInt(properties.getProperty("save_every_seconds"), 60);
            config.chatFrequency = Math.max(0, parseInt(properties.getProperty("chat_frequency"), 1));
            config.aggression = Math.max(0, Math.min(5, parseInt(properties.getProperty("aggression"), 3)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try {
            File file = new File(CONFIG_FILE);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Properties properties = new Properties();
            properties.setProperty("autostart", String.valueOf(config.autoStart));
            properties.setProperty("default_count", String.valueOf(config.defaultCount));
            properties.setProperty("max_count", String.valueOf(config.maxCount));
            properties.setProperty("respawn_seconds", String.valueOf(config.respawnSeconds));
            properties.setProperty("save_every_seconds", String.valueOf(config.saveEverySeconds));
            properties.setProperty("chat_frequency", String.valueOf(config.chatFrequency));
            properties.setProperty("aggression", String.valueOf(config.aggression));
            try (FileOutputStream out = new FileOutputStream(file)) {
                properties.store(out, "Single-RSC autonomous world bot settings");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveDefaultConfig(File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Properties properties = new Properties();
            properties.setProperty("autostart", "true");
            properties.setProperty("default_count", String.valueOf(DEFAULT_BOT_COUNT));
            properties.setProperty("max_count", String.valueOf(DEFAULT_MAX_BOT_COUNT));
            properties.setProperty("respawn_seconds", "20");
            properties.setProperty("save_every_seconds", "60");
            properties.setProperty("chat_frequency", "1");
            properties.setProperty("aggression", "3");
            try (FileOutputStream out = new FileOutputStream(file)) {
                properties.store(out, "Single-RSC autonomous world bot settings");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadState() {
        File file = new File(STATE_FILE);
        if (!file.exists()) {
            return;
        }
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            properties.load(in);
            for (int i = 0; i < bots.size(); i++) {
                WorldBot bot = bots.get(i);
                String prefix = "bot." + i + ".";
                bot.level = parseInt(properties.getProperty(prefix + "level"), bot.level);
                bot.xp = parseInt(properties.getProperty(prefix + "xp"), bot.xp);
                bot.xpRate = Math.max(1, parseInt(properties.getProperty(prefix + "xp_rate"), bot.xpRate));
                bot.kills = parseInt(properties.getProperty(prefix + "kills"), bot.kills);
                bot.deaths = parseInt(properties.getProperty(prefix + "deaths"), bot.deaths);
                bot.itemsBanked = parseInt(properties.getProperty(prefix + "banked"), bot.itemsBanked);
                bot.trades = parseInt(properties.getProperty(prefix + "trades"), bot.trades);
                bot.marketVolume = parseInt(properties.getProperty(prefix + "market_volume"), bot.marketVolume);
                bot.goal = Goal.fromName(properties.getProperty(prefix + "goal"), bot.goal);
                bot.inventory.clear();
                String inventory = properties.getProperty(prefix + "inventory", "");
                if (!inventory.isEmpty()) {
                    for (String pair : inventory.split(",")) {
                        String[] parts = pair.split(":");
                        if (parts.length == 2) {
                            bot.inventory.put(parseInt(parts[0], 0), parseInt(parts[1], 0));
                        }
                    }
                }
                bot.npc.setCombatLevel(bot.level);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void saveState() {
        if (bots.isEmpty()) {
            return;
        }
        Properties properties = new Properties();
        for (int i = 0; i < bots.size(); i++) {
            WorldBot bot = bots.get(i);
            String prefix = "bot." + i + ".";
            properties.setProperty(prefix + "name", bot.name);
            properties.setProperty(prefix + "role", bot.role.name());
            properties.setProperty(prefix + "level", String.valueOf(bot.level));
            properties.setProperty(prefix + "xp", String.valueOf(bot.xp));
            properties.setProperty(prefix + "xp_rate", String.valueOf(bot.xpRate));
            properties.setProperty(prefix + "kills", String.valueOf(bot.kills));
            properties.setProperty(prefix + "deaths", String.valueOf(bot.deaths));
            properties.setProperty(prefix + "banked", String.valueOf(bot.itemsBanked));
            properties.setProperty(prefix + "trades", String.valueOf(bot.trades));
            properties.setProperty(prefix + "market_volume", String.valueOf(bot.marketVolume));
            properties.setProperty(prefix + "goal", bot.goal.name());
            properties.setProperty(prefix + "inventory", bot.inventoryString());
        }
        try {
            File file = new File(STATE_FILE);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                properties.store(out, "Single-RSC autonomous world bot state");
            }
            lastStateSave = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private final class WorldBot {
        private final String name;
        private final Role role;
        private final Personality personality;
        private final int playerServerIndex;
        private NPC npc;
        private BotArea area;
        private int carriedItem;
        private String carriedItemName = "nothing";
        private final Map<Integer, Integer> inventory = new LinkedHashMap<>();
        private long nextWorkAt;
        private String lastMessage;
        private int messageSequence;
        private long messageUntil;
        private boolean active = true;
        private int level = 3;
        private int xp;
        private int xpRate;
        private int kills;
        private int deaths;
        private int itemsBanked;
        private int trades;
        private int marketVolume;
        private boolean online = true;
        private long nextSessionChangeAt;
        private String activity;
        private Goal goal;

        private WorldBot(int index, Role role, NPC npc, BotArea area, Personality personality) {
            this.name = personality.name;
            this.role = role;
            this.personality = personality;
            this.playerServerIndex = PLAYER_SERVER_INDEX_BASE + index;
            this.npc = npc;
            this.area = area;
            chooseCarriedItem(index);
            level = personality.startLevel;
            xpRate = xpRateFor(index, role);
            addInventory(COINS, 500 + (level * 25) + random.nextInt(1500));
            npc.setCombatLevel(level);
            goal = Goal.forRole(role);
            activity = "starting in " + area.name;
            scheduleLogout();
        }

        private void tick() {
            if (!active) {
                return;
            }
            if (!online) {
                if (System.currentTimeMillis() >= nextSessionChangeAt) {
                    login();
                } else {
                    activity = "offline";
                }
                return;
            }
            if (npc.isRemoved() || npc.inCombat()) {
                activity = "busy";
                return;
            }

            if (role == Role.WILDERNESS && tryAttackPlayer()) {
                return;
            }

            updateGoal();

            if (System.currentTimeMillis() >= nextWorkAt) {
                work();
                nextWorkAt = System.currentTimeMillis() + 5000 + random.nextInt(8000);
            }

            if (config.chatFrequency > 0 && random.nextInt(Math.max(1, personality.chatRate / config.chatFrequency)) == 0) {
                say(randomLine());
            }

            tradeWithExchange();

            if (inventorySize() >= role.depositAt) {
                int banked = depositInventoryToExchange();
                say("banking " + banked + " items");
            }

            if (npc.finishedPath() && random.nextInt(3) == 0) {
                walkSomewhere();
            }
            if (System.currentTimeMillis() >= nextSessionChangeAt && !npc.inCombat()) {
                logout();
            }
        }

        private boolean tryAttackPlayer() {
            Player player = World.getWorld().getPlayer();
            if (player == null || !player.isLoggedIn() || !player.getLocation().inWilderness()) {
                return false;
            }
            if (!npc.getLocation().withinRange(player.getLocation(), personality.attackRange)) {
                return false;
            }
            if (config.aggression <= 0) {
                return false;
            }
            int patience = Math.max(1, 7 - config.aggression);
            if (random.nextInt(patience) != 0) {
                return false;
            }
            if (personality.riskLevel < 3 && player.getCombatLevel() > level + 8) {
                say(personality.safeLine(random));
                walkSomewhere();
                return false;
            }
            if (random.nextInt(6) == 0) {
                say(personality.territoryLine(random));
            }
            npc.startCombat(player);
            say(randomPkLine());
            return true;
        }

        private void work() {
            if (role == Role.GATHERER) {
                addInventory(carriedItem, 1 + random.nextInt(4));
                gainXp(2);
                activity = "collecting " + carriedItemName + " in " + area.name;
            } else if (role == Role.FIGHTER) {
                addInventory(carriedItem, 1 + random.nextInt(3));
                gainXp(4);
                activity = "hunting drops in " + area.name;
                if (random.nextInt(4) == 0) {
                    addInventory(COINS, 5 + random.nextInt(60));
                }
            } else {
                addInventory(carriedItem, 1 + random.nextInt(2));
                gainXp(6);
                activity = "patrolling " + area.name;
                if (random.nextInt(3) == 0) {
                    addInventory(COINS, 25 + random.nextInt(150));
                }
            }
        }

        private void tradeWithExchange() {
            if (role == Role.GATHERER) {
                return;
            }
            if (GrandExchange.countId(373) > 0 && random.nextInt(10) == 0 && buyFromExchange(373, 1)) {
                addInventory(373, 1);
                activity = "buying food from the exchange";
                trades++;
                say("buying food");
            }
            if (role == Role.WILDERNESS && GrandExchange.countId(81) > 0 && random.nextInt(20) == 0
                    && buyFromExchange(81, 1)) {
                addInventory(81, 1);
                activity = "upgrading gear from the exchange";
                trades++;
                say("upgrading gear");
            }
        }

        private void updateGoal() {
            if (goal == Goal.BUILD_BANK && coins() > 10000 && random.nextInt(8) == 0) {
                goal = role == Role.WILDERNESS ? Goal.PK_TRIP : Goal.UPGRADE_GEAR;
                say("bank is looking good");
                return;
            }
            if (goal == Goal.UPGRADE_GEAR && (hasItem(81) || coins() < 250) && random.nextInt(8) == 0) {
                goal = Goal.BUILD_BANK;
                say("saving gp now");
                return;
            }
            if (role == Role.GATHERER && inventorySize() > role.depositAt / 2 && random.nextInt(10) == 0) {
                goal = Goal.BUILD_BANK;
            }
            if (role == Role.WILDERNESS && currentWorldEvent.contains("wilderness")) {
                goal = Goal.PK_TRIP;
            }
        }

        private void walkSomewhere() {
            if (role == Role.GATHERER && random.nextInt(12) == 0) {
                area = Role.GATHERER.randomArea(random);
            }
            activity = "walking through " + area.name;
            npc.setPath(new Path(npc.getX(), npc.getY(), area.randomX(random), area.randomY(random)));
        }

        private void chooseCarriedItem(int index) {
            if (role == Role.GATHERER) {
                int[] items = { 14, 150, 151, 155, 349, 372, 632, 633 };
                carriedItem = items[index % items.length];
            } else if (role == Role.FIGHTER) {
                int[] items = { 20, 413, 31, 38 };
                carriedItem = items[index % items.length];
            } else {
                int[] items = { 10, 31, 33, 40, 412 };
                carriedItem = items[index % items.length];
            }
            carriedItemName = itemName(carriedItem);
        }

        private void say(String message) {
            if (message == null || message.equals(lastMessage)) {
                return;
            }
            lastMessage = message;
            messageSequence++;
            messageUntil = System.currentTimeMillis() + 5000;
        }

        private String randomLine() {
            if (role == Role.GATHERER) {
                return personality.gatherLine(random, carriedItemName, inventorySize(), role.depositAt);
            }
            if (role == Role.FIGHTER) {
                return personality.fighterLine(random, carriedItemName, level, inventorySize());
            }
            return randomPkLine();
        }

        private String randomPkLine() {
            return personality.wildernessLine(random, level, config.aggression);
        }

        private boolean tradeWith(Player player) {
            Map.Entry<Integer, Integer> offer = null;
            for (Map.Entry<Integer, Integer> entry : inventory.entrySet()) {
                if (entry.getKey() != COINS && entry.getValue() > 0) {
                    offer = entry;
                    break;
                }
            }
            if (offer == null) {
                say("nothing to sell");
                player.getSender().sendMessage("@cya@[WorldBots] @whi@" + name + " has nothing useful to sell.");
                return false;
            }

            int itemId = offer.getKey();
            int amount = Math.min(offer.getValue(), Math.max(1, player.getInventory().getFreeSpaces()));
            int price = GrandExchange.buyPrice(itemId, amount);
            if (player.getInventory().countId(COINS) < price) {
                say("bring coins");
                player.getSender().sendMessage("@cya@[WorldBots] @red@" + name + " wants " + price + " coins.");
                return false;
            }
            InvItem item = new InvItem(itemId, amount);
            if (!player.getInventory().canHold(item)) {
                player.getSender().sendMessage("@cya@[WorldBots] @red@You don't have room for that trade.");
                return false;
            }

            player.getInventory().remove(COINS, price);
            player.getSender().sendInventory();
            player.getInventory().add(item);
            player.getSender().sendInventory();
            addInventory(COINS, price);
            trades++;
            marketVolume += price;
            int remaining = offer.getValue() - amount;
            if (remaining > 0) {
                inventory.put(itemId, remaining);
            } else {
                inventory.remove(itemId);
            }
            say("sold " + amount + " " + itemName(itemId));
            player.getSender().sendMessage("@cya@[WorldBots] @whi@Bought " + amount + " " + itemName(itemId) + " from " + name + " for " + price + " coins.");
            return true;
        }

        private Snapshot snapshot() {
            int[] equipment = new int[12];
            if (hasItem(81)) {
                equipment[4] = 81;
            } else if (role == Role.FIGHTER) {
                equipment[4] = 81;
            } else if (role == Role.WILDERNESS) {
                equipment[4] = 93;
            } else {
                equipment[4] = 87;
            }
            return new Snapshot(playerServerIndex, name, npc.getX(), npc.getY(), npc.getSprite(),
                    level, role == Role.WILDERNESS, equipment,
                    role.hairColour, role.topColour, role.bottomColour, 15523536,
                    System.currentTimeMillis() < messageUntil ? lastMessage : null, messageSequence);
        }

        private void addInventory(int itemId, int amount) {
            inventory.put(itemId, inventory.getOrDefault(itemId, 0) + amount);
        }

        private boolean removeInventory(int itemId, int amount) {
            int current = inventory.getOrDefault(itemId, 0);
            if (amount < 1 || current < amount) {
                return false;
            }
            int remaining = current - amount;
            if (remaining > 0) {
                inventory.put(itemId, remaining);
            } else {
                inventory.remove(itemId);
            }
            return true;
        }

        private boolean buyFromExchange(int itemId, int amount) {
            int price = GrandExchange.buyPrice(itemId, amount);
            if (inventory.getOrDefault(COINS, 0) < price) {
                activity = "saving coins for " + itemName(itemId);
                return false;
            }
            int paid = GrandExchange.buySystem(itemId, amount);
            if (paid < 1) {
                return false;
            }
            removeInventory(COINS, paid);
            marketVolume += paid;
            return true;
        }

        private boolean hasItem(int itemId) {
            return inventory.containsKey(itemId) && inventory.get(itemId) > 0;
        }

        private int inventorySize() {
            int total = 0;
            for (Map.Entry<Integer, Integer> entry : inventory.entrySet()) {
                if (entry.getKey() != COINS) {
                    total += entry.getValue();
                }
            }
            return total;
        }

        private String inventoryString() {
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<Integer, Integer> entry : inventory.entrySet()) {
                if (builder.length() > 0) {
                    builder.append(",");
                }
                builder.append(entry.getKey()).append(":").append(entry.getValue());
            }
            return builder.toString();
        }

        private int depositInventoryToExchange() {
            int deposited = 0;
            int coins = 0;
            for (Map.Entry<Integer, Integer> entry : new ArrayList<>(inventory.entrySet())) {
                if (entry.getKey() == COINS) {
                    continue;
                }
                int paid = GrandExchange.sellSystem(entry.getKey(), entry.getValue());
                if (paid > 0) {
                    deposited += entry.getValue();
                    coins += paid;
                    marketVolume += paid;
                    inventory.remove(entry.getKey());
                }
            }
            if (coins > 0) {
                addInventory(COINS, coins);
                trades++;
            }
            itemsBanked += deposited;
            activity = "selling " + deposited + " items for " + coins + " coins";
            return deposited;
        }

        private int coins() {
            return inventory.getOrDefault(COINS, 0);
        }

        private int score() {
            return kills * 1000 + level * 100 + effectiveXp() / 10 + itemsBanked * 5 + trades * 25 + coins() / 25 - deaths * 250;
        }

        private int effectiveXp() {
            return xp;
        }

        private void dropInventory(Player owner) {
            for (Map.Entry<Integer, Integer> entry : inventory.entrySet()) {
                World.getWorld().registerItem(new Item(entry.getKey(), npc.getX(), npc.getY(), entry.getValue(), owner));
            }
            inventory.clear();
            World.getWorld().registerItem(new Item(COINS, npc.getX(), npc.getY(), 25 + random.nextInt(100), owner));
        }

        private void gainXp(int amount) {
            xp += amount * xpRate;
            int newLevel = Math.min(99, 3 + (xp / 100));
            if (newLevel > level) {
                level = newLevel;
                npc.setCombatLevel(level);
                say("level " + level);
            }
        }

        private int xpRateFor(int index, Role role) {
            if (role == Role.WILDERNESS) {
                int[] rates = { 1, 2, 3, 5 };
                return rates[index % rates.length];
            }
            if (role == Role.FIGHTER) {
                int[] rates = { 1, 1, 2, 3 };
                return rates[index % rates.length];
            }
            int[] rates = { 1, 1, 1, 2, 3 };
            return rates[index % rates.length];
        }

        private void respawn() {
            BotArea spawnArea = role.randomArea(random);
            spawn(spawnArea);
            active = true;
            online = true;
            scheduleLogout();
            activity = "respawned in " + spawnArea.name;
            say("back again");
        }

        private void logout() {
            if (!online || npc.inCombat()) {
                return;
            }
            activity = "logging out from " + area.name;
            World.getWorld().unregisterNpc(npc);
            online = false;
            nextSessionChangeAt = System.currentTimeMillis() + MIN_OFFLINE_MS + random.nextInt((int) OFFLINE_VARIANCE_MS);
        }

        private void login() {
            BotArea spawnArea = role.randomArea(random);
            spawn(spawnArea);
            online = true;
            scheduleLogout();
            activity = "logged in at " + spawnArea.name;
            say("just logged in");
        }

        private void spawn(BotArea spawnArea) {
            int npcId = role == Role.GATHERER ? GATHERER_NPC : role == Role.FIGHTER ? FIGHTER_NPC : WILDERNESS_NPC;
            npc = new NPC(npcId, spawnArea.randomX(random), spawnArea.randomY(random),
                    spawnArea.minX, spawnArea.maxX, spawnArea.minY, spawnArea.maxY);
            npc.setShouldRespawn(false);
            npc.setCombatLevel(level);
            World.getWorld().registerNpc(npc);
            area = spawnArea;
        }

        private void scheduleLogout() {
            nextSessionChangeAt = System.currentTimeMillis() + MIN_SESSION_MS + random.nextInt((int) SESSION_VARIANCE_MS);
        }

        private String statusText() {
            if (!active) {
                return " respawning";
            }
            if (!online) {
                return " offline";
            }
            return " at " + npc.getX() + "," + npc.getY();
        }
    }

    private static final class Personality {
        private static final String[] NAMES = {
            "Zezima Jr", "OreLord", "WillowWisp", "Lobster Lad", "RuneRita", "EdgePker",
            "CoalCart", "BankSale", "Risky Rob", "Maple Max", "Chaos Cat", "Iron Ivy",
            "Trout Tom", "SkullSam", "YewOnly", "DeepWild"
        };

        private final String name;
        private final String title;
        private final int riskLevel;
        private final int chatRate;
        private final int attackRange;
        private final int startLevel;
        private final String clan;
        private final String rivalClan;

        private Personality(String name, String title, int riskLevel, int chatRate, int attackRange, int startLevel,
                String clan, String rivalClan) {
            this.name = name;
            this.title = title;
            this.riskLevel = riskLevel;
            this.chatRate = chatRate;
            this.attackRange = attackRange;
            this.startLevel = startLevel;
            this.clan = clan;
            this.rivalClan = rivalClan;
        }

        private static Personality forBot(int index, Role role) {
            String name = NAMES[index % NAMES.length];
            String[] clans = { "Red capes", "Blue moon", "Bank crew", "Wild guard" };
            String clan = clans[index % clans.length];
            String rival = clans[(index + 1) % clans.length];
            if (role == Role.WILDERNESS) {
                return new Personality(name, "PKer", 4 + (index % 2), 7, 8 + (index % 4), 45 + (index % 25), clan, rival);
            }
            if (role == Role.FIGHTER) {
                return new Personality(name, "Monster hunter", 2, 11, 5, 25 + (index % 20), clan, rival);
            }
            return new Personality(name, "Skiller", 1, 14, 3, 3 + (index % 15), clan, rival);
        }

        private String attackedLine(Random random) {
            String[] lines = riskLevel > 2
                    ? new String[] { "bad move", "sit down", "you sure?", "gl then", "wrong target", "you picked this" }
                    : new String[] { "hey!", "why me?", "I was skilling", "not cool", "leave me alone", "I had a full inv" };
            return lines[random.nextInt(lines.length)];
        }

        private String deathLine(Random random) {
            String[] lines = { "gf", "lag", "rematch later", "there goes my loot", "banking faster next time", "close one" };
            return lines[random.nextInt(lines.length)];
        }

        private String gatherLine(Random random, String itemName, int inventorySize, int depositAt) {
            String[] lines = {
                "need a few more " + itemName,
                "selling " + itemName + " at bank",
                "this spot is decent",
                "almost full " + inventorySize + "/" + depositAt,
                "anyone buying " + itemName + "?",
                clan + " skilling trip",
                "bank route is clear",
                "saving for better gear",
                "prices are moving today",
                "taking this to exchange",
                "quiet world for skilling",
                rivalClan + " keeps crashing spots"
            };
            return lines[random.nextInt(lines.length)];
        }

        private String fighterLine(Random random, String itemName, int level, int inventorySize) {
            String[] lines = {
                "looking for drops",
                "need food soon",
                "training attack",
                "nice hit",
                "anyone seen giants?",
                "watch out " + rivalClan,
                "lvl " + level + " grind",
                "keeping " + itemName + " for exchange",
                "inventory is " + inventorySize + " items",
                "bank after this kill",
                "selling spare drops",
                clan + " monster run"
            };
            return lines[random.nextInt(lines.length)];
        }

        private String wildernessLine(Random random, int level, int aggression) {
            String[] calmLines = {
                "just scouting",
                "not risking much",
                "passing through wild",
                "keeping distance",
                "watching the border"
            };
            String[] lines = {
                "skull up",
                "run if you want",
                "risk fight?",
                "this is my world",
                "bring food next time",
                clan + " clears " + rivalClan,
                "lvl " + level + " north side",
                "protect item on",
                "bank your loot",
                "wild is active",
                "teleblock would be nice",
                "scouting for " + clan
            };
            if (aggression <= 1) {
                return calmLines[random.nextInt(calmLines.length)];
            }
            return lines[random.nextInt(lines.length)];
        }

        private String safeLine(Random random) {
            String[] lines = { "not risking that", "too stacked for me", "I'll pass", "need better food first" };
            return lines[random.nextInt(lines.length)];
        }

        private String territoryLine(Random random) {
            String[] lines = {
                clan + " owns this strip",
                rivalClan + " got cleared here",
                "this bank route is watched",
                clan + " patrol"
            };
            return lines[random.nextInt(lines.length)];
        }
    }

    private static final class Config {
        private boolean autoStart = true;
        private int defaultCount = DEFAULT_BOT_COUNT;
        private int maxCount = DEFAULT_MAX_BOT_COUNT;
        private int respawnSeconds = 20;
        private int saveEverySeconds = 60;
        private int chatFrequency = 1;
        private int aggression = 3;

        private String aggressionLabel() {
            if (aggression <= 0) {
                return "peaceful";
            }
            if (aggression == 1) {
                return "low";
            }
            if (aggression == 2 || aggression == 3) {
                return "normal";
            }
            if (aggression == 4) {
                return "high";
            }
            return "dangerous";
        }
    }

    public static final class Snapshot {
        public final int serverIndex;
        public final String name;
        public final int x;
        public final int y;
        public final int sprite;
        public final int combatLevel;
        public final boolean skulled;
        public final int[] equipment;
        public final int hairColour;
        public final int topColour;
        public final int bottomColour;
        public final int skinColour;
        public final String message;
        public final int messageSequence;

        private Snapshot(int serverIndex, String name, int x, int y, int sprite, int combatLevel,
                boolean skulled, int[] equipment, int hairColour, int topColour, int bottomColour, int skinColour) {
            this(serverIndex, name, x, y, sprite, combatLevel, skulled, equipment,
                    hairColour, topColour, bottomColour, skinColour, null, 0);
        }

        private Snapshot(int serverIndex, String name, int x, int y, int sprite, int combatLevel,
                boolean skulled, int[] equipment, int hairColour, int topColour, int bottomColour, int skinColour,
                String message, int messageSequence) {
            this.serverIndex = serverIndex;
            this.name = name;
            this.x = x;
            this.y = y;
            this.sprite = sprite;
            this.combatLevel = combatLevel;
            this.skulled = skulled;
            this.equipment = equipment;
            this.hairColour = hairColour;
            this.topColour = topColour;
            this.bottomColour = bottomColour;
            this.skinColour = skinColour;
            this.message = message;
            this.messageSequence = messageSequence;
        }
    }

    private static String itemName(int itemId) {
        try {
            return org.nemotech.rsc.external.EntityManager.getItem(itemId).getName();
        } catch (Exception e) {
            return "item " + itemId;
        }
    }

    private enum Goal {
        BUILD_BANK("build bank"),
        UPGRADE_GEAR("upgrade gear"),
        PK_TRIP("pk trip"),
        SKILLING("skilling");

        private final String label;

        Goal(String label) {
            this.label = label;
        }

        private static Goal forRole(Role role) {
            if (role == Role.WILDERNESS) {
                return PK_TRIP;
            }
            if (role == Role.FIGHTER) {
                return UPGRADE_GEAR;
            }
            return SKILLING;
        }

        private static Goal fromName(String name, Goal fallback) {
            if (name == null) {
                return fallback;
            }
            try {
                return Goal.valueOf(name);
            } catch (Exception e) {
                return fallback;
            }
        }
    }

    private enum Role {
        GATHERER("Gatherer", 18, 15658734, 25088, 8409120),
        FIGHTER("Fighter", 10, 7360576, 8409120, 3),
        WILDERNESS("Wilderness", 8, 7360576, 16711680, 3);

        private final String label;
        private final int depositAt;
        private final int hairColour;
        private final int topColour;
        private final int bottomColour;

        Role(String label, int depositAt, int hairColour, int topColour, int bottomColour) {
            this.label = label;
            this.depositAt = depositAt;
            this.hairColour = hairColour;
            this.topColour = topColour;
            this.bottomColour = bottomColour;
        }

        private BotArea areaFor(int index) {
            BotArea[] areas = areas();
            return areas[index % areas.length];
        }

        private BotArea randomArea(Random random) {
            BotArea[] areas = areas();
            return areas[random.nextInt(areas.length)];
        }

        private BotArea[] areas() {
            if (this == FIGHTER) {
                return new BotArea[] {
                    new BotArea("Karamja dungeon", 350, 370, 604, 624),
                    new BotArea("Rimmington monsters", 285, 305, 656, 676),
                    new BotArea("Edgeville dungeon", 245, 265, 392, 410),
                    new BotArea("Varrock combat", 210, 260, 490, 540),
                    new BotArea("Yanille combat", 560, 620, 720, 780),
                    new BotArea("Gnome combat", 680, 740, 500, 560),
                    new BotArea("Ardougne combat", 520, 580, 560, 620)
                };
            }
            if (this == WILDERNESS) {
                return new BotArea[] {
                    new BotArea("Wilderness west", 198, 224, 390, 430),
                    new BotArea("Wilderness center", 250, 275, 384, 420),
                    new BotArea("Wilderness east", 300, 330, 360, 410)
                };
            }
            return new BotArea[] {
                new BotArea("Lumbridge", 100, 160, 620, 680),
                new BotArea("Varrock", 100, 160, 480, 550),
                new BotArea("Draynor", 190, 240, 600, 660),
                new BotArea("Falador", 280, 340, 510, 580),
                new BotArea("Port Sarim", 250, 290, 620, 670),
                new BotArea("Karamja", 350, 400, 660, 710),
                new BotArea("Al Kharid", 70, 120, 660, 720),
                new BotArea("Edgeville", 190, 250, 420, 480),
                new BotArea("Taverley", 350, 400, 470, 530),
                new BotArea("Seers", 480, 550, 420, 480),
                new BotArea("Rimmington", 300, 350, 640, 690),
                new BotArea("Catherby", 420, 470, 480, 530),
                new BotArea("Camelot", 480, 560, 330, 430),
                new BotArea("Ardougne", 520, 580, 560, 620),
                new BotArea("Yanille", 560, 620, 720, 780),
                new BotArea("Lost City", 100, 160, 3490, 3550),
                new BotArea("Gnome Stronghold", 680, 740, 500, 560),
                new BotArea("Tutorial Island", 190, 250, 720, 770)
            };
        }
    }

    private static final class BotArea {
        private final String name;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;

        private BotArea(String name, int minX, int maxX, int minY, int maxY) {
            this.name = name;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }

        private int randomX(Random random) {
            return minX + random.nextInt(maxX - minX + 1);
        }

        private int randomY(Random random) {
            return minY + random.nextInt(maxY - minY + 1);
        }
    }
}

package org.nemotech.rsc.bot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.nemotech.rsc.Constants;
import org.nemotech.rsc.event.DelayedEvent;
import org.nemotech.rsc.model.GrandExchange;
import org.nemotech.rsc.model.Item;
import org.nemotech.rsc.model.Mob;
import org.nemotech.rsc.model.NPC;
import org.nemotech.rsc.model.Point;
import org.nemotech.rsc.model.World;
import org.nemotech.rsc.model.landscape.Path;
import org.nemotech.rsc.model.player.InvItem;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.io.HighscoresExporter;

public final class WorldBotManager {

    private static final int GATHERER_NPC = 795;
    private static final int FIGHTER_NPC = 796;
    private static final int WILDERNESS_NPC = 797;
    private static final int PLAYER_SERVER_INDEX_BASE = 3000;
    private static final int COINS = 10;
    private static final int DEFAULT_BOT_COUNT = 200;
    private static final int DEFAULT_MAX_BOT_COUNT = 200;
    private static final long MINUTE_MS = 60 * 1000L;
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
    private final LinkedList<String> recentActivity = new LinkedList<>();
    private final List<AutoGroup> autoGroups = new ArrayList<>();
    private int nextAutoGroupId = 1;
    private long nextAutoGroupCheckAt;

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
        autoGroups.clear();
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
        autoGroups.clear();
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
                    .append(" fights ").append(bot.fights)
                    .append(" kills ").append(bot.kills)
                    .append(" deaths ").append(bot.deaths);
        }
        return report.toString();
    }

    public synchronized String getActivityReport() {
        StringBuilder report = new StringBuilder("Recent world activity");
        if (recentActivity.isEmpty()) {
            report.append("\nquiet");
            return report.toString();
        }
        for (String line : recentActivity) {
            report.append("\n").append(line);
        }
        return report.toString();
    }

    public synchronized String getHelperHint(Player player) {
        String areaName = "this area";
        WorldBot nearest = nearestAvailableBot(player, 48, null, null);
        if (nearest != null) {
            areaName = nearest.area.name;
            nearest.say("I can give a hint");
        }
        if (player.getLocation().inWilderness()) {
            return "Wilderness hint: bring food, watch skullers, and use grouped bots in wild mode before pushing deeper.";
        }
        if (player.getCombatLevel() < 30) {
            return "Progress hint near " + areaName + ": train combat with a fighter group, buy food from bots, then try harder quest fights.";
        }
        if (currentWorldEvent.contains("food")) {
            return "Market hint: food demand is high, so fishing and cooking supplies should sell well.";
        }
        if (currentWorldEvent.contains("boss")) {
            return "Group hint: use ::worldbots group nearby 3 then ::worldbots group mode boss before a hard fight.";
        }
        return "World hint near " + areaName + ": check ::worldbots activity, trade with nearby bots, and group fighters for hard NPCs.";
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

    public synchronized String inviteNearest(Player player) {
        WorldBot nearest = nearestAvailableBot(player, 20, null, null);
        if (nearest == null) {
            return "No available world bot nearby.";
        }
        inviteToParty(player, nearest, PartyMode.COMBAT);
        return nearest.name + " joined your group.";
    }

    public synchronized String inviteNearby(Player player, int count, int radius) {
        List<WorldBot> candidates = availableBots(player, radius, null, null);
        return inviteMany(player, candidates, count, PartyMode.COMBAT);
    }

    public synchronized String inviteRole(Player player, String roleName, int count) {
        Role role = Role.fromGroupName(roleName);
        if (role == null) {
            return "Role must be skiller, fighter, or pker.";
        }
        List<WorldBot> candidates = availableBots(player, 64, role, null);
        return inviteMany(player, candidates, count, role == Role.GATHERER ? PartyMode.SKILLING : PartyMode.COMBAT);
    }

    public synchronized String inviteClan(Player player, String clan, int count) {
        List<WorldBot> candidates = availableBots(player, 96, null, clan.toLowerCase());
        return inviteMany(player, candidates, count, PartyMode.COMBAT);
    }

    public synchronized String inviteAny(Player player, int count) {
        List<WorldBot> candidates = availableBots(player, 96, null, null);
        return inviteMany(player, candidates, count, PartyMode.COMBAT);
    }

    public synchronized String setPartyMode(Player player, String modeName) {
        PartyMode mode = PartyMode.fromName(modeName);
        if (mode == null) {
            return "Mode must be boss, combat, skill, wild, or social.";
        }
        int changed = 0;
        for (WorldBot bot : bots) {
            if (bot.isGroupedWith(player)) {
                bot.partyMode = mode;
                bot.activity = "grouping with " + player.getUsername() + " (" + mode.label + ")";
                changed++;
            }
        }
        if (changed == 0) {
            return "You do not have any grouped world bots.";
        }
        return "Set " + changed + " grouped bots to " + mode.label + " mode.";
    }

    public synchronized String dismissParty(Player player) {
        int dismissed = 0;
        for (WorldBot bot : bots) {
            if (bot.isGroupedWith(player)) {
                bot.partyOwner = null;
                bot.partyMode = PartyMode.NONE;
                bot.activity = "leaving " + player.getUsername() + "'s group";
                bot.say("thanks for the group");
                dismissed++;
            }
        }
        return dismissed == 0 ? "You do not have any grouped world bots." : "Dismissed " + dismissed + " grouped bots.";
    }

    public synchronized String getPartyReport(Player player) {
        StringBuilder report = new StringBuilder("Your world-bot group");
        int count = 0;
        for (WorldBot bot : bots) {
            if (bot.isGroupedWith(player)) {
                count++;
                report.append("\n").append(bot.name)
                        .append(" - ").append(bot.role.label)
                        .append(" lvl ").append(bot.level)
                        .append(" ").append(bot.partyMode.label)
                        .append(" ").append(bot.activity);
            }
        }
        if (count == 0) {
            report.append("\nnone");
        }
        return report.toString();
    }

    private WorldBot nearestAvailableBot(Player player, int radius, Role role, String clan) {
        List<WorldBot> candidates = availableBots(player, radius, role, clan);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private List<WorldBot> availableBots(final Player player, int radius, Role role, String clan) {
        List<WorldBot> candidates = new ArrayList<>();
        for (WorldBot bot : bots) {
            if (!bot.active || !bot.online || bot.npc == null || bot.npc.isRemoved() || bot.partyOwner != null) {
                continue;
            }
            if (role != null && bot.role != role) {
                continue;
            }
            if (clan != null && !bot.personality.clan.toLowerCase().contains(clan)) {
                continue;
            }
            if (!bot.npc.getLocation().withinRange(player.getLocation(), radius)) {
                continue;
            }
            candidates.add(bot);
        }
        Collections.sort(candidates, new Comparator<WorldBot>() {
            @Override
            public int compare(WorldBot first, WorldBot second) {
                return distance(first, player) - distance(second, player);
            }
        });
        return candidates;
    }

    private String inviteMany(Player player, List<WorldBot> candidates, int count, PartyMode mode) {
        if (candidates.isEmpty()) {
            return "No available world bots found.";
        }
        int invited = 0;
        int limit = Math.max(1, Math.min(8, count));
        for (WorldBot bot : candidates) {
            inviteToParty(player, bot, mode);
            invited++;
            if (invited >= limit) {
                break;
            }
        }
        return "Invited " + invited + " world bots to your group.";
    }

    private void inviteToParty(Player player, WorldBot bot, PartyMode mode) {
        bot.partyOwner = player.getUsername();
        bot.partyMode = mode;
        bot.groupTrips++;
        bot.activity = "grouping with " + player.getUsername() + " (" + mode.label + ")";
        bot.say(mode.inviteLine);
        recordActivity(bot.name + " joined " + player.getUsername() + "'s group for " + mode.label);
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
                    .append(" fights ").append(bot.fights)
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
                        + "\nfights=" + bot.fights
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
            if (!bot.canTrade()) {
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

    public synchronized boolean tradeWithNamedBot(Player player, String query) {
        if (query == null || query.trim().isEmpty()) {
            player.getSender().sendMessage("@cya@[WorldBots] @red@Usage: ::worldbots trade <name>");
            return false;
        }
        String needle = query.toLowerCase();
        WorldBot match = null;
        for (WorldBot bot : bots) {
            if (bot.canTrade() && bot.name.toLowerCase().contains(needle)) {
                match = bot;
                break;
            }
        }
        if (match == null) {
            player.getSender().sendMessage("@cya@[WorldBots] @red@No trading bot found matching: " + query);
            return false;
        }
        if (Math.abs(match.npc.getX() - player.getX()) + Math.abs(match.npc.getY() - player.getY()) > 12) {
            player.getSender().sendMessage("@cya@[WorldBots] @red@" + match.name + " is too far away to trade.");
            return false;
        }
        return match.tradeWith(player);
    }

    public synchronized boolean tradeWithGroupedBot(Player player) {
        WorldBot nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (WorldBot bot : bots) {
            if (!bot.canTrade() || !bot.isGroupedWith(player)) {
                continue;
            }
            int distance = Math.abs(bot.npc.getX() - player.getX()) + Math.abs(bot.npc.getY() - player.getY());
            if (distance < nearestDistance) {
                nearest = bot;
                nearestDistance = distance;
            }
        }
        if (nearest == null || nearestDistance > 12) {
            player.getSender().sendMessage("@cya@[WorldBots] @red@No grouped trading bot nearby.");
            return false;
        }
        return nearest.tradeWith(player);
    }

    public synchronized boolean tradeWithBotNpc(Player player, NPC npc) {
        WorldBot bot = findBot(npc);
        if (bot == null) {
            return false;
        }
        if (!bot.canTrade()) {
            player.getSender().sendMessage("@cya@[WorldBots] @whi@" + bot.name + " has nothing useful to sell.");
            return true;
        }
        bot.tradeWith(player);
        return true;
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
        bot.fights++;
        bot.activity = "fighting " + player.getUsername() + " in the wilderness";
        if (bot.role == Role.WILDERNESS && random.nextInt(3) == 0) {
            bot.say(bot.personality.counterAttackLine(random, player.getCombatLevel(), bot.level));
        }
    }

    public synchronized void onPlayerKilledBot(Player player, NPC npc) {
        WorldBot bot = findBot(npc);
        if (bot == null || !bot.active) {
            return;
        }
        bot.deaths++;
        bot.active = false;
        bot.say(bot.personality.deathLine(random, bot.coins()));
        recordActivity(player.getUsername() + " killed " + bot.name + " in " + bot.area.name);
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
        updateAutoGroups();
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
            "clans watching Edgeville",
            "boss group forming",
            "Karamja fishing crowd",
            "Seers yew rush",
            "rune gear buyers at Varrock"
        };
        currentWorldEvent = events[random.nextInt(events.length)];
        recordActivity("World event: " + currentWorldEvent);
        nextWorldEventAt = now + 180000L + random.nextInt(240000);
    }

    private void updateAutoGroups() {
        long now = System.currentTimeMillis();
        for (int i = autoGroups.size() - 1; i >= 0; i--) {
            AutoGroup group = autoGroups.get(i);
            if (now >= group.expiresAt || group.onlineMembers() < 2) {
                group.disband("trip ended");
                autoGroups.remove(i);
            }
        }
        if (now < nextAutoGroupCheckAt || autoGroups.size() >= 5) {
            return;
        }
        nextAutoGroupCheckAt = now + 90000L + random.nextInt(120000);
        maybeCreateAutoGroup();
    }

    private void maybeCreateAutoGroup() {
        if (bots.size() < 8 || random.nextInt(3) == 0) {
            return;
        }
        AutoGroupGoal goal = AutoGroupGoal.random(random, currentWorldEvent);
        List<WorldBot> candidates = new ArrayList<>();
        for (WorldBot bot : bots) {
            if (!bot.active || !bot.online || bot.npc == null || bot.npc.isRemoved() || bot.partyOwner != null || bot.autoGroup != null) {
                continue;
            }
            if (!goal.accepts(bot.role)) {
                continue;
            }
            candidates.add(bot);
        }
        Collections.shuffle(candidates, random);
        int size = Math.min(candidates.size(), goal.minSize + random.nextInt(goal.maxSize - goal.minSize + 1));
        if (size < goal.minSize) {
            return;
        }
        List<WorldBot> members = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            members.add(candidates.get(i));
        }
        AutoGroup group = new AutoGroup(nextAutoGroupId++, goal, members);
        autoGroups.add(group);
        group.start();
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
                bot.fights = parseInt(properties.getProperty(prefix + "fights"), bot.fights);
                bot.itemsBanked = parseInt(properties.getProperty(prefix + "banked"), bot.itemsBanked);
                bot.trades = parseInt(properties.getProperty(prefix + "trades"), bot.trades);
                bot.marketVolume = parseInt(properties.getProperty(prefix + "market_volume"), bot.marketVolume);
                bot.groupTrips = parseInt(properties.getProperty(prefix + "group_trips"), bot.groupTrips);
                bot.playerTrades = parseInt(properties.getProperty(prefix + "player_trades"), bot.playerTrades);
                bot.playerKills = parseInt(properties.getProperty(prefix + "player_kills"), bot.playerKills);
                bot.online = Boolean.parseBoolean(properties.getProperty(prefix + "online", String.valueOf(bot.online)));
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
                if (!bot.online && bot.npc != null && !bot.npc.isRemoved()) {
                    World.getWorld().unregisterNpc(bot.npc);
                }
            }
            recentActivity.clear();
            for (int i = 0; i < 12; i++) {
                String activityLine = properties.getProperty("activity." + i);
                if (activityLine != null && !activityLine.trim().isEmpty()) {
                    recentActivity.add(activityLine);
                }
            }
            currentWorldEvent = properties.getProperty("world.event", currentWorldEvent);
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
            properties.setProperty(prefix + "fights", String.valueOf(bot.fights));
            properties.setProperty(prefix + "banked", String.valueOf(bot.itemsBanked));
            properties.setProperty(prefix + "trades", String.valueOf(bot.trades));
            properties.setProperty(prefix + "market_volume", String.valueOf(bot.marketVolume));
            properties.setProperty(prefix + "group_trips", String.valueOf(bot.groupTrips));
            properties.setProperty(prefix + "player_trades", String.valueOf(bot.playerTrades));
            properties.setProperty(prefix + "player_kills", String.valueOf(bot.playerKills));
            properties.setProperty(prefix + "online", String.valueOf(bot.online));
            properties.setProperty(prefix + "goal", bot.goal.name());
            properties.setProperty(prefix + "inventory", bot.inventoryString());
        }
        properties.setProperty("world.event", currentWorldEvent);
        int activityIndex = 0;
        for (String activityLine : recentActivity) {
            properties.setProperty("activity." + activityIndex, activityLine);
            activityIndex++;
        }
        properties.setProperty("groups.count", String.valueOf(autoGroups.size()));
        for (int i = 0; i < autoGroups.size(); i++) {
            AutoGroup group = autoGroups.get(i);
            String prefix = "group." + i + ".";
            properties.setProperty(prefix + "id", String.valueOf(group.id));
            properties.setProperty(prefix + "goal", group.goal.label);
            properties.setProperty(prefix + "leader", group.leader.name);
            properties.setProperty(prefix + "area", group.area.name);
            properties.setProperty(prefix + "members", group.memberNames());
            properties.setProperty(prefix + "size", String.valueOf(group.onlineMembers()));
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
            HighscoresExporter.export();
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

    private void recordActivity(String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }
        recentActivity.addFirst(line);
        while (recentActivity.size() > 12) {
            recentActivity.removeLast();
        }
    }

    private final class WorldBot {
        private final String name;
        private final Role role;
        private final Personality personality;
        private final int playerServerIndex;
        private final int[] appearanceSprites;
        private final int hairColour;
        private final int topColour;
        private final int bottomColour;
        private final int skinColour;
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
        private int fights;
        private int itemsBanked;
        private int trades;
        private int marketVolume;
        private int groupTrips;
        private int playerTrades;
        private int playerKills;
        private boolean online = true;
        private long nextSessionChangeAt;
        private String partyOwner;
        private PartyMode partyMode = PartyMode.NONE;
        private AutoGroup autoGroup;
        private long nextAssistAt;
        private String activity;
        private Goal goal;

        private WorldBot(int index, Role role, NPC npc, BotArea area, Personality personality) {
            this.name = personality.name;
            this.role = role;
            this.personality = personality;
            this.playerServerIndex = PLAYER_SERVER_INDEX_BASE + index;
            this.npc = npc;
            this.area = area;
            this.appearanceSprites = role.appearanceSprites(index);
            this.hairColour = Math.floorMod(index + role.ordinal() * 3, 10);
            this.topColour = Math.floorMod(index * 3 + role.ordinal() * 5, 15);
            this.bottomColour = Math.floorMod(index * 5 + role.ordinal() * 2, 15);
            this.skinColour = Math.floorMod(index + role.ordinal(), 5);
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

            if (partyOwner != null && partyMode != PartyMode.NONE) {
                groupTick();
                return;
            }

            if (autoGroup != null) {
                autoGroupTick();
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
                activity = "avoiding " + player.getUsername() + " near " + area.name;
                walkSomewhere();
                return false;
            }
            if (inventory.getOrDefault(COINS, 0) > 10000 && player.getCombatLevel() > level + 3 && random.nextInt(3) == 0) {
                say("not risking this cash");
                activity = "protecting loot near " + area.name;
                walkSomewhere();
                return false;
            }
            if (random.nextInt(6) == 0) {
                say(personality.territoryLine(random));
            }
            fights++;
            activity = "skulling on " + player.getUsername() + " in " + area.name;
            npc.startCombat(player);
            say(personality.attackLine(random, player.getCombatLevel(), level, coins()));
            return true;
        }

        private boolean isGroupedWith(Player player) {
            return partyOwner != null && player != null && partyOwner.equalsIgnoreCase(player.getUsername());
        }

        private void autoGroupTick() {
            if (autoGroup == null || !autoGroup.active || autoGroup.leader == null) {
                autoGroup = null;
                return;
            }
            if (this == autoGroup.leader) {
                activity = "leading " + autoGroup.goal.label + " at " + autoGroup.area.name;
                if (!inside(autoGroup.area)) {
                    npc.setPath(new Path(npc.getX(), npc.getY(), autoGroup.area.randomX(random), autoGroup.area.randomY(random)));
                    return;
                }
                if (System.currentTimeMillis() >= nextWorkAt) {
                    work();
                    nextWorkAt = System.currentTimeMillis() + 6000 + random.nextInt(9000);
                }
                if (npc.finishedPath() && random.nextInt(4) == 0) {
                    npc.setPath(new Path(npc.getX(), npc.getY(), autoGroup.area.randomX(random), autoGroup.area.randomY(random)));
                }
                return;
            }
            if (autoGroup.leader.npc == null || autoGroup.leader.npc.isRemoved() || !autoGroup.leader.online) {
                autoGroup = null;
                return;
            }
            if (!npc.getLocation().withinRange(autoGroup.leader.npc.getLocation(), 6)) {
                activity = "following " + autoGroup.leader.name + " for " + autoGroup.goal.label;
                npc.setPath(new Path(npc.getX(), npc.getY(), autoGroup.leader.npc.getX(), autoGroup.leader.npc.getY()));
                return;
            }
            activity = "group " + autoGroup.goal.label + " with " + autoGroup.leader.name;
            if (System.currentTimeMillis() >= nextWorkAt) {
                work();
                tradeWithExchange();
                nextWorkAt = System.currentTimeMillis() + 7000 + random.nextInt(10000);
            }
            if (random.nextInt(18) == 0) {
                say(autoGroup.goal.chatLine);
            }
        }

        private boolean inside(BotArea targetArea) {
            return targetArea != null && npc.getX() >= targetArea.minX && npc.getX() <= targetArea.maxX
                    && npc.getY() >= targetArea.minY && npc.getY() <= targetArea.maxY;
        }

        private void groupTick() {
            Player player = World.getWorld().getPlayer();
            if (player == null || !player.isLoggedIn() || !isGroupedWith(player)) {
                partyOwner = null;
                partyMode = PartyMode.NONE;
                activity = "looking for a group";
                return;
            }
            if (handleGroupSurvival(player)) {
                return;
            }
            if (!npc.getLocation().withinRange(player.getLocation(), 12)) {
                activity = "following " + player.getUsername();
                npc.setPath(new Path(npc.getX(), npc.getY(), player.getX(), player.getY()));
                return;
            }

            Mob opponent = player.getOpponent();
            if (opponent instanceof NPC && !opponent.isRemoved() && !WorldBotManager.this.isWorldBotNpc((NPC) opponent)) {
                assistPlayer(player, (NPC) opponent);
                return;
            }

            if (partyMode == PartyMode.SKILLING) {
                activity = "skilling with " + player.getUsername();
                if (System.currentTimeMillis() >= nextWorkAt) {
                    work();
                    nextWorkAt = System.currentTimeMillis() + 7000 + random.nextInt(9000);
                }
            } else {
                activity = "grouped with " + player.getUsername();
            }

            if (npc.finishedPath() && random.nextInt(4) == 0) {
                npc.setPath(new Path(npc.getX(), npc.getY(), player.getX(), player.getY()));
            }
        }

        private boolean handleGroupSurvival(Player player) {
            int maxHits = Math.max(1, npc.getDef().getHitpoints());
            if (npc.getHits() > maxHits / 3) {
                return false;
            }
            if (hasItem(373)) {
                removeInventory(373, 1);
                npc.setHits(Math.min(maxHits, npc.getHits() + 12));
                activity = "eating food while grouped with " + player.getUsername();
                say("food low");
                WorldBotManager.this.recordActivity(name + " ate food during a group trip");
                return false;
            }
            if (partyMode == PartyMode.BOSS || partyMode == PartyMode.WILDERNESS) {
                activity = "retreating from group fight";
                say("need food, backing out");
                npc.setPath(new Path(npc.getX(), npc.getY(), area.randomX(random), area.randomY(random)));
                return true;
            }
            return false;
        }

        private void assistPlayer(Player player, NPC target) {
            if (!npc.getLocation().withinRange(target.getLocation(), partyMode.assistRange)) {
                activity = "moving to assist on " + target.getDef().getName();
                npc.setPath(new Path(npc.getX(), npc.getY(), target.getX(), target.getY()));
                return;
            }
            activity = "assisting " + player.getUsername() + " on " + target.getDef().getName();
            if (System.currentTimeMillis() < nextAssistAt) {
                return;
            }
            nextAssistAt = System.currentTimeMillis() + partyMode.assistDelay + random.nextInt(1800);
            int damage = partyDamage();
            if (damage < 1) {
                say("splash");
                return;
            }
            target.setLastDamage(damage);
            target.setHits(target.getHits() - damage);
            player.informOfModifiedHits(target);
            fights++;
            gainXp(1);
            if (random.nextInt(5) == 0) {
                say(partyMode.combatLine);
            }
            if (target.getHits() <= 0) {
                kills++;
                playerKills++;
                WorldBotManager.this.recordActivity(name + " helped " + player.getUsername() + " kill " + target.getDef().getName());
                target.killedBy(player);
            }
        }

        private int partyDamage() {
            int base = Math.max(1, level / partyMode.damageDivisor);
            if (role == Role.WILDERNESS) {
                base += 2;
            } else if (role == Role.FIGHTER) {
                base += 1;
            }
            return random.nextInt(Math.max(1, base + 1));
        }

        private void work() {
            if (role == Role.GATHERER) {
                addInventory(carriedItem, 1 + random.nextInt(4));
                gainXp(2);
                activity = "collecting " + carriedItemName + " in " + area.name;
                if (random.nextInt(20) == 0) {
                    say("banking " + carriedItemName + " soon");
                }
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
                WorldBotManager.this.recordActivity(name + " bought food from the exchange");
                say("buying food");
            }
            if (role == Role.WILDERNESS && GrandExchange.countId(81) > 0 && random.nextInt(20) == 0
                    && buyFromExchange(81, 1)) {
                addInventory(81, 1);
                activity = "upgrading gear from the exchange";
                trades++;
                WorldBotManager.this.recordActivity(name + " bought gear from the exchange");
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
            playerTrades++;
            marketVolume += price;
            int remaining = offer.getValue() - amount;
            if (remaining > 0) {
                inventory.put(itemId, remaining);
            } else {
                inventory.remove(itemId);
            }
            say("sold " + amount + " " + itemName(itemId));
            WorldBotManager.this.recordActivity(name + " traded " + amount + " " + itemName(itemId) + " to " + player.getUsername());
            player.getSender().sendMessage("@cya@[WorldBots] @whi@Bought " + amount + " " + itemName(itemId) + " from " + name + " for " + price + " coins.");
            return true;
        }

        private boolean canTrade() {
            return active && online && npc != null && !npc.isRemoved() && !inventory.isEmpty();
        }

        private Snapshot snapshot() {
            int[] equipment = appearanceSprites.clone();
            return new Snapshot(playerServerIndex, name, npc.getX(), npc.getY(), npc.getSprite(),
                    level, role == Role.WILDERNESS, equipment,
                    hairColour, topColour, bottomColour, skinColour,
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
            return kills * 1000 + fights * 50 + level * 100 + effectiveXp() / 10 + itemsBanked * 5 + trades * 25 + coins() / 25 - deaths * 250;
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
            activity = logoutActivity();
            World.getWorld().unregisterNpc(npc);
            online = false;
            nextSessionChangeAt = System.currentTimeMillis() + offlineDuration();
        }

        private void login() {
            BotArea spawnArea = role.randomArea(random);
            spawn(spawnArea);
            online = true;
            scheduleLogout();
            activity = "logged in at " + spawnArea.name + " for " + goal.label;
            say(loginLine());
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
            nextSessionChangeAt = System.currentTimeMillis() + sessionDuration();
        }

        private long sessionDuration() {
            if (role == Role.WILDERNESS) {
                return (3 + random.nextInt(10)) * MINUTE_MS;
            }
            if (role == Role.FIGHTER) {
                return (8 + random.nextInt(18)) * MINUTE_MS;
            }
            return (12 + random.nextInt(28)) * MINUTE_MS;
        }

        private long offlineDuration() {
            if (random.nextInt(8) == 0) {
                return (20 + random.nextInt(70)) * 1000L;
            }
            if (role == Role.WILDERNESS) {
                return (1 + random.nextInt(5)) * MINUTE_MS;
            }
            if (role == Role.FIGHTER) {
                return (2 + random.nextInt(8)) * MINUTE_MS;
            }
            return (3 + random.nextInt(12)) * MINUTE_MS;
        }

        private String loginLine() {
            if (role == Role.WILDERNESS) {
                String[] lines = { "world hop found", "back north", "anyone out?", "scouting again", "risk check" };
                return lines[random.nextInt(lines.length)];
            }
            if (role == Role.FIGHTER) {
                String[] lines = { "back to train", "need drops", "checking exchange first", "new trip", "food ready" };
                return lines[random.nextInt(lines.length)];
            }
            String[] lines = { "back to skill", "fresh trip", "checking prices", "need supplies", "new route" };
            return lines[random.nextInt(lines.length)];
        }

        private String logoutActivity() {
            if (role == Role.WILDERNESS && coins() > 5000) {
                return "banking loot and hopping out from " + area.name;
            }
            if (inventorySize() >= role.depositAt / 2) {
                return "logging out after a bank trip from " + area.name;
            }
            if (random.nextInt(4) == 0) {
                return "world hopping from " + area.name;
            }
            return "logging out from " + area.name;
        }

        private String statusText() {
            if (!active) {
                return " respawning";
            }
            if (!online) {
                return " offline " + secondsUntil(nextSessionChangeAt) + "s";
            }
            return " at " + npc.getX() + "," + npc.getY() + " session " + secondsUntil(nextSessionChangeAt) + "s";
        }

        private long secondsUntil(long time) {
            return Math.max(0, (time - System.currentTimeMillis()) / 1000L);
        }
    }

    private static final class Personality {
        private static final String[] NAMES = {
            "Zezima Jr", "OreLord", "WillowWisp", "Lobster Lad", "RuneRita", "EdgePker",
            "CoalCart", "BankSale", "Risky Rob", "Maple Max", "Chaos Cat", "Iron Ivy",
            "Trout Tom", "SkullSam", "YewOnly", "DeepWild", "Pure Str", "Rune Runner",
            "Dds Soon", "Banker Bait", "North Lurer", "Prayer Off", "Food Check",
            "Red Cape", "Blue Cape", "No Tele", "Wild Scout", "Loot Pile"
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

        private String deathLine(Random random, int coins) {
            String[] lines = coins > 5000
                    ? new String[] { "gf", "lost my cash", "banking faster next time", "that was my risk", "sit me then", "rematch after bank" }
                    : new String[] { "gf", "lag", "rematch later", "there goes my loot", "close one", "no food left" };
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

        private String attackLine(Random random, int playerLevel, int botLevel, int coins) {
            String[] underdog = {
                "worth a try",
                "big risk on you?",
                "dont run",
                "catching you south",
                "smite would be nice"
            };
            String[] confident = {
                "free loot",
                "you are mine",
                "no tele now",
                "drop the food",
                "skulled on you",
                "bank trip after this"
            };
            String[] rich = {
                "protect item on",
                "risking " + coins + " gp",
                "cash stack fight",
                "one kill then bank"
            };
            if (coins > 10000 && random.nextInt(3) == 0) {
                return rich[random.nextInt(rich.length)];
            }
            if (playerLevel > botLevel + 5) {
                return underdog[random.nextInt(underdog.length)];
            }
            return confident[random.nextInt(confident.length)];
        }

        private String counterAttackLine(Random random, int playerLevel, int botLevel) {
            String[] lines = playerLevel > botLevel
                    ? new String[] { "brave hit", "you risk?", "not free", "try finish it", "I have food" }
                    : new String[] { "bad click", "now you skull", "mine now", "sit soon", "wrong target" };
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

    private final class AutoGroup {
        private final int id;
        private final AutoGroupGoal goal;
        private final List<WorldBot> members;
        private final WorldBot leader;
        private final BotArea area;
        private final long expiresAt;
        private boolean active = true;

        private AutoGroup(int id, AutoGroupGoal goal, List<WorldBot> members) {
            this.id = id;
            this.goal = goal;
            this.members = members;
            this.leader = members.get(0);
            this.area = goal.area(random);
            this.expiresAt = System.currentTimeMillis() + (8 + random.nextInt(12)) * MINUTE_MS;
        }

        private void start() {
            for (WorldBot member : members) {
                member.autoGroup = this;
                member.groupTrips++;
                member.activity = (member == leader ? "leading " : "joining ") + goal.label + " at " + area.name;
                member.say(member == leader ? goal.leaderLine : goal.memberLine);
            }
            recordActivity(leader.name + " formed a " + goal.label + " group at " + area.name + " (" + members.size() + " bots)");
        }

        private void disband(String reason) {
            active = false;
            for (WorldBot member : members) {
                if (member.autoGroup == this) {
                    member.autoGroup = null;
                    member.activity = reason;
                }
            }
            recordActivity(leader.name + "'s " + goal.label + " group disbanded: " + reason);
        }

        private int onlineMembers() {
            int count = 0;
            for (WorldBot member : members) {
                if (member.active && member.online && member.autoGroup == this) {
                    count++;
                }
            }
            return count;
        }

        private String memberNames() {
            StringBuilder names = new StringBuilder();
            for (WorldBot member : members) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(member.name);
            }
            return names.toString();
        }
    }

    private enum AutoGroupGoal {
        BOSS("bossing", 3, 5, "need food for boss", "ready for boss", "boss group"),
        PK("wilderness pk trip", 2, 4, "pk trip forming", "skull up?", "pk run"),
        SKILL("skilling trip", 3, 6, "skilling group", "bank after full inv", "skilling"),
        MARKET("market run", 2, 5, "checking prices", "selling at exchange", "market");

        private final String label;
        private final int minSize;
        private final int maxSize;
        private final String leaderLine;
        private final String memberLine;
        private final String chatLine;

        AutoGroupGoal(String label, int minSize, int maxSize, String leaderLine, String memberLine, String chatLine) {
            this.label = label;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.leaderLine = leaderLine;
            this.memberLine = memberLine;
            this.chatLine = chatLine;
        }

        private boolean accepts(Role role) {
            if (this == PK) {
                return role == Role.WILDERNESS || role == Role.FIGHTER;
            }
            if (this == SKILL) {
                return role == Role.GATHERER;
            }
            if (this == BOSS) {
                return role == Role.FIGHTER || role == Role.WILDERNESS;
            }
            return true;
        }

        private BotArea area(Random random) {
            if (this == PK) {
                return Role.WILDERNESS.randomArea(random);
            }
            if (this == SKILL) {
                return Role.GATHERER.randomArea(random);
            }
            if (this == MARKET) {
                BotArea[] areas = { new BotArea("Varrock market", 120, 160, 500, 540), new BotArea("Falador bank", 300, 330, 540, 565), new BotArea("Seers bank", 500, 535, 450, 475) };
                return areas[random.nextInt(areas.length)];
            }
            return Role.FIGHTER.randomArea(random);
        }

        private static AutoGroupGoal random(Random random, String event) {
            if (event != null) {
                if (event.contains("boss")) {
                    return BOSS;
                }
                if (event.contains("wilderness") || event.contains("Edgeville")) {
                    return PK;
                }
                if (event.contains("market") || event.contains("buyers") || event.contains("food")) {
                    return MARKET;
                }
                if (event.contains("yew") || event.contains("fishing") || event.contains("skilling")) {
                    return SKILL;
                }
            }
            AutoGroupGoal[] goals = values();
            return goals[random.nextInt(goals.length)];
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

    private enum PartyMode {
        NONE("solo", 0, 0, 6, "ok", "ok"),
        BOSS("bossing", 8, 5000, 5, "ready for bossing", "on boss"),
        COMBAT("combat", 7, 5600, 6, "I'll help fight", "helping"),
        SKILLING("skilling", 5, 7000, 9, "I'll skill with you", "working"),
        WILDERNESS("wilderness", 8, 4300, 5, "let's pk", "pile them"),
        SOCIAL("social", 4, 8000, 12, "I'll tag along", "with you");

        private final String label;
        private final int assistRange;
        private final int assistDelay;
        private final int damageDivisor;
        private final String inviteLine;
        private final String combatLine;

        PartyMode(String label, int assistRange, int assistDelay, int damageDivisor, String inviteLine, String combatLine) {
            this.label = label;
            this.assistRange = assistRange;
            this.assistDelay = assistDelay;
            this.damageDivisor = damageDivisor;
            this.inviteLine = inviteLine;
            this.combatLine = combatLine;
        }

        private static PartyMode fromName(String name) {
            if (name == null) {
                return null;
            }
            String normalized = name.toLowerCase();
            if (normalized.equals("boss") || normalized.equals("bossing") || normalized.equals("pvm")) {
                return BOSS;
            }
            if (normalized.equals("combat") || normalized.equals("fight") || normalized.equals("fighting")) {
                return COMBAT;
            }
            if (normalized.equals("skill") || normalized.equals("skilling")) {
                return SKILLING;
            }
            if (normalized.equals("wild") || normalized.equals("wilderness") || normalized.equals("pk")) {
                return WILDERNESS;
            }
            if (normalized.equals("social") || normalized.equals("follow")) {
                return SOCIAL;
            }
            return null;
        }
    }

    private enum Role {
        GATHERER("Gatherer", 18, new int[] {1, 4, 6, 7, 8}, new int[] {2}),
        FIGHTER("Fighter", 10, new int[] {1, 4, 6, 7, 8}, new int[] {2, 5}),
        WILDERNESS("Wilderness", 8, new int[] {1, 4, 6, 7, 8}, new int[] {5, 2});

        private final String label;
        private final int depositAt;
        private final int[] headSprites;
        private final int[] bodySprites;

        Role(String label, int depositAt, int[] headSprites, int[] bodySprites) {
            this.label = label;
            this.depositAt = depositAt;
            this.headSprites = headSprites;
            this.bodySprites = bodySprites;
        }

        private int[] appearanceSprites(int index) {
            int headSprite = headSprites[Math.floorMod(index + ordinal(), headSprites.length)];
            int bodySprite = bodySprites[Math.floorMod(index / Math.max(1, headSprites.length), bodySprites.length)];
            return new int[] {headSprite, bodySprite, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0};
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

        private static Role fromGroupName(String name) {
            if (name == null) {
                return null;
            }
            String normalized = name.toLowerCase();
            if (normalized.equals("skiller") || normalized.equals("skill") || normalized.equals("gatherer")) {
                return GATHERER;
            }
            if (normalized.equals("fighter") || normalized.equals("combat") || normalized.equals("pvm")) {
                return FIGHTER;
            }
            if (normalized.equals("pker") || normalized.equals("pk") || normalized.equals("wild") || normalized.equals("wilderness")) {
                return WILDERNESS;
            }
            return null;
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

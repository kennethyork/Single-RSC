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
import java.util.concurrent.ConcurrentLinkedQueue;

import org.nemotech.rsc.Constants;
import org.nemotech.rsc.event.DelayedEvent;
import org.nemotech.rsc.model.GrandExchange;
import org.nemotech.rsc.model.Item;
import org.nemotech.rsc.model.Mob;
import org.nemotech.rsc.model.MenuHandler;
import org.nemotech.rsc.model.NPC;
import org.nemotech.rsc.model.Point;
import org.nemotech.rsc.model.World;
import org.nemotech.rsc.model.landscape.Path;
import org.nemotech.rsc.model.player.InvItem;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.io.HighscoresExporter;
import org.nemotech.rsc.util.Formulae;

public final class WorldBotManager {

    private static final int GATHERER_NPC = 795;
    private static final int FIGHTER_NPC = 796;
    private static final int WILDERNESS_NPC = 797;
    private static final int PLAYER_SERVER_INDEX_BASE = 3000;
    private static final int COINS = 10;
    private static final int DEFAULT_BOT_COUNT = 200;
    private static final int DEFAULT_MAX_BOT_COUNT = 200;
    private static final String[] SKILL_NAMES = {
            "Attack", "Defense", "Strength", "Hits", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
            "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblaw", "Agility", "Thieving"
    };
    private static final int ATTACK = 0;
    private static final int DEFENSE = 1;
    private static final int STRENGTH = 2;
    private static final int HITS = 3;
    private static final int RANGED = 4;
    private static final int PRAYER = 5;
    private static final int MAGIC = 6;
    private static final int COOKING = 7;
    private static final int WOODCUTTING = 8;
    private static final int FLETCHING = 9;
    private static final int FISHING = 10;
    private static final int FIREMAKING = 11;
    private static final int CRAFTING = 12;
    private static final int SMITHING = 13;
    private static final int MINING = 14;
    private static final int HERBLAW = 15;
    private static final int AGILITY = 16;
    private static final int THIEVING = 17;
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
    private final ConcurrentLinkedQueue<Runnable> chatDeliveries = new ConcurrentLinkedQueue<>();
    private final Map<String, Long> chatCooldowns = new LinkedHashMap<>();
    private long nextPopulationChatAt;
    private long nextEventChatAt;
    private long lastOllamaNoticeAt;

    private WorldBotManager() {}

    public static WorldBotManager getInstance() {
        if (instance == null) {
            instance = new WorldBotManager();
        }
        return instance;
    }

    public synchronized void startDefaultBots() {
        loadConfig();
        configureOllama();
        if (config.autoStart) {
            startBots(config.defaultCount);
        }
    }

    public synchronized void startBots(int count) {
        loadConfig();
        configureOllama();
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
                return bot.profileReport();
            }
        }
        return "No world bot found matching: " + query;
    }

    public synchronized String setBotSkillGoal(String query, String skillName, int targetLevel) {
        WorldBot bot = findBotByName(query);
        if (bot == null) return "No world bot found matching: " + query;
        int skill = skillIndex(skillName);
        if (skill < 0) return "Unknown RSC skill: " + skillName;
        if (targetLevel < 1 || targetLevel > 99) return "Target level must be between 1 and 99.";
        int safeLevel = Math.max(1, Math.min(99, targetLevel));
        bot.directedSkill = skill;
        bot.directedLevel = safeLevel;
        bot.activity = "assigned " + SKILL_NAMES[skill] + " level " + safeLevel;
        bot.say("new goal: " + SKILL_NAMES[skill] + " " + safeLevel);
        recordActivity(bot.name + " was assigned a " + SKILL_NAMES[skill] + " level " + safeLevel + " goal");
        return bot.name + " will train " + SKILL_NAMES[skill] + " from " + bot.skillLevel(skill) + " to " + safeLevel + ".";
    }

    public synchronized String clearBotGoal(String query) {
        WorldBot bot = findBotByName(query);
        if (bot == null) return "No world bot found matching: " + query;
        bot.directedSkill = -1;
        bot.directedLevel = 0;
        return "Cleared " + bot.name + "'s directed skill goal.";
    }

    public synchronized String visitBot(Player player, String query) {
        WorldBot bot = findBotByName(query);
        if (bot == null || !bot.active || !bot.online || bot.npc == null || bot.npc.isRemoved()) {
            return "No online world bot found matching: " + query;
        }
        player.teleport(bot.npc.getX(), bot.npc.getY(), true);
        return "Travelled to " + bot.name + " at " + bot.area.name + ".";
    }

    private WorldBot findBotByName(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String needle = query.trim().toLowerCase();
        for (WorldBot bot : bots) {
            if (bot.name.equalsIgnoreCase(needle)) return bot;
        }
        for (WorldBot bot : bots) {
            if (bot.name.toLowerCase().contains(needle)) return bot;
        }
        return null;
    }

    private void configureOllama() {
        OllamaBotChat.getInstance().configure(new OllamaBotChat.Settings(
                config.ollamaEnabled, config.ollamaUrl, config.ollamaModel,
                config.ollamaTimeoutSeconds, config.ollamaHistoryMessages,
                config.ollamaPublicCooldownSeconds, config.ollamaClanCooldownSeconds,
                config.ollamaAmbientCooldownSeconds, config.ollamaPersistHistory));
    }

    public synchronized void onPlayerPublicChat(final Player player, String message) {
        if (player == null || !player.isLoggedIn() || message == null || message.trim().isEmpty()) return;
        String lower = message.toLowerCase();
        WorldBot target = null;
        for (WorldBot bot : bots) {
            if (bot.active && bot.online && bot.npc != null && !bot.npc.isRemoved()
                    && bot.npc.getLocation().withinRange(player.getLocation(), 14)
                    && lower.contains(bot.name.toLowerCase())) {
                target = bot;
                break;
            }
        }
        if (target == null) target = nearestAvailableBot(player, 14, null, null);
        if (target == null || !acquireChatCooldown("public:" + player.getUsername(),
                config.ollamaPublicCooldownSeconds * 1000L)) return;
        requestPlayerReply(target, player, "public", message, false);
    }

    public synchronized void privateBotChat(final Player player, String request) {
        int separator = request == null ? -1 : request.indexOf('|');
        if (separator < 1 || separator >= request.length() - 1) {
            player.getSender().sendMessage("@cya@[BotChat] @whi@Usage: ::botchat <bot name>|<message>");
            return;
        }
        WorldBot bot = findBotByName(request.substring(0, separator));
        String message = request.substring(separator + 1).trim();
        if (bot == null || !bot.active || !bot.online || message.isEmpty()) {
            player.getSender().sendMessage("@cya@[BotChat] @red@That bot is not online, or the message is empty.");
            return;
        }
        if (!acquireChatCooldown("private:" + player.getUsername(), config.ollamaPublicCooldownSeconds * 1000L)) {
            player.getSender().sendMessage("@cya@[BotChat] @whi@Give the bot a moment to answer.");
            return;
        }
        player.getSender().sendMessage("@cya@[Private] @whi@To " + bot.name + ": " + message);
        requestPlayerReply(bot, player, "private", message, true);
    }

    public synchronized void groupBotChat(final Player player, String message) {
        if (message == null || message.trim().isEmpty()) {
            player.getSender().sendMessage("@cya@[BotChat] @whi@Usage: ::botclan <message>");
            return;
        }
        WorldBot bot = null;
        for (WorldBot candidate : bots) {
            if (candidate.active && candidate.online && candidate.isGroupedWith(player)) {
                bot = candidate;
                break;
            }
        }
        if (bot == null) {
            player.getSender().sendMessage("@cya@[BotChat] @red@Invite bots with ::worldbots group first.");
            return;
        }
        if (!acquireChatCooldown("clan:" + player.getUsername(), config.ollamaClanCooldownSeconds * 1000L)) return;
        requestPlayerReply(bot, player, "clan", message, true);
    }

    private void requestPlayerReply(final WorldBot bot, final Player player, String channel,
            String message, final boolean echoToChatBox) {
        OllamaBotChat.getInstance().reply(bot.chatIdentity(), player.getUsername(), channel, message,
                line -> enqueueChatDelivery(() -> {
                    synchronized (WorldBotManager.this) {
                        if (!player.isLoggedIn() || !bot.active || !bot.online) return;
                        bot.showOllamaLine(line);
                        if (echoToChatBox) {
                            player.getSender().sendMessage("@cya@[" + ("clan".equals(channel) ? "Clan" : "Private")
                                    + "] @whi@" + bot.name + ": " + line);
                        }
                    }
                }), () -> enqueueChatDelivery(() -> notifyOllamaUnavailable(player)));
    }

    public synchronized void sendOllamaStatus(final Player player) {
        configureOllama();
        for (String line : OllamaBotChat.getInstance().statusLines()) {
            player.getSender().sendMessage("@cya@[Ollama] @whi@" + line);
        }
        OllamaBotChat.getInstance().probe(line -> enqueueChatDelivery(
                () -> player.getSender().sendMessage("@cya@[Ollama] @whi@" + line)));
    }

    public synchronized void sendOllamaModelHelp(Player player) {
        for (String line : OllamaBotChat.getInstance().modelHelp()) {
            player.getSender().sendMessage("@cya@[Ollama] @whi@" + line);
        }
    }

    public synchronized void selectOllamaModel(Player player, String model) {
        player.getSender().sendMessage("@cya@[Ollama] @whi@" + OllamaBotChat.getInstance().selectModel(model));
        sendOllamaStatus(player);
    }

    public synchronized void resetOllamaModel(Player player) {
        player.getSender().sendMessage("@cya@[Ollama] @whi@" + OllamaBotChat.getInstance().resetModel());
    }

    public synchronized void forgetOllama(Player player) {
        int count = OllamaBotChat.getInstance().forget(player.getUsername());
        player.getSender().sendMessage("@cya@[Ollama] @whi@Forgot " + count + " saved bot conversations.");
    }

    public synchronized void sendOllamaStartupStatus(final Player player) {
        configureOllama();
        player.getSender().sendMessage("@cya@[Ollama] @whi@World bot speech uses "
                + OllamaBotChat.getInstance().activeModel() + " locally. Use ::ollamastatus.");
        OllamaBotChat.getInstance().probe(line -> enqueueChatDelivery(
                () -> player.getSender().sendMessage("@cya@[Ollama] @whi@" + line)));
    }

    private boolean acquireChatCooldown(String key, long duration) {
        long now = System.currentTimeMillis();
        Long until = chatCooldowns.get(key);
        if (until != null && until > now) return false;
        chatCooldowns.put(key, now + Math.max(1000L, duration));
        return true;
    }

    private void notifyOllamaUnavailable(Player player) {
        long now = System.currentTimeMillis();
        if (player == null || !player.isLoggedIn() || now - lastOllamaNoticeAt < 30_000L) return;
        lastOllamaNoticeAt = now;
        player.getSender().sendMessage("@cya@[Ollama] @whi@Offline or model missing; bots stay silent. Use ::ollamastatus.");
    }

    private int skillIndex(String name) {
        if (name == null) return -1;
        String normalized = name.trim().toLowerCase();
        for (int i = 0; i < SKILL_NAMES.length; i++) {
            if (SKILL_NAMES[i].toLowerCase().equals(normalized)) return i;
        }
        if (normalized.equals("defence")) return DEFENSE;
        if (normalized.equals("hp") || normalized.equals("hitpoints")) return HITS;
        if (normalized.equals("woodcut") || normalized.equals("wc")) return WOODCUTTING;
        if (normalized.equals("range")) return RANGED;
        if (normalized.equals("herblore")) return HERBLAW;
        return -1;
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
                + "\nollama_enabled=" + config.ollamaEnabled
                + "\nollama_url=" + config.ollamaUrl
                + "\nollama_model=" + OllamaBotChat.getInstance().activeModel()
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
        openMarketplace(player, nearest);
        return true;
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
        openMarketplace(player, match);
        return true;
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
        openMarketplace(player, nearest);
        return true;
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
        openMarketplace(player, bot);
        return true;
    }

    private void openMarketplace(final Player player, final WorldBot bot) {
        String[] options = { "Buy from " + bot.name, "Sell to " + bot.name, "Inspect " + bot.name, "Cancel" };
        player.setMenuHandler(new MenuHandler(options) {
            @Override
            public void handleReply(int option, String reply) {
                owner.resetMenuHandler();
                synchronized (WorldBotManager.this) {
                    if (!bot.canTrade() || !bot.npc.getLocation().withinRange(owner.getLocation(), 12)) {
                        owner.getSender().sendMessage("@cya@[Marketplace] @red@" + bot.name + " is no longer available.");
                        return;
                    }
                    if (option == 0) showBotSellOffers(owner, bot);
                    else if (option == 1) showPlayerSellOffers(owner, bot);
                    else if (option == 2) showBotInspection(owner, bot);
                }
            }
        });
        player.getSender().sendMenu(options);
    }

    private void showBotSellOffers(final Player player, final WorldBot bot) {
        final List<Integer> itemIds = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : bot.inventory.entrySet()) {
            if (entry.getKey() == COINS || entry.getValue() < 1) continue;
            itemIds.add(entry.getKey());
            labels.add(itemName(entry.getKey()) + " x" + entry.getValue() + " (" + GrandExchange.buyPrice(entry.getKey(), 1) + "gp ea)");
            if (itemIds.size() >= 8) break;
        }
        if (itemIds.isEmpty()) {
            player.getSender().sendMessage("@cya@[Marketplace] @whi@" + bot.name + " has nothing to sell.");
            return;
        }
        labels.add("Cancel");
        String[] options = labels.toArray(new String[0]);
        player.setMenuHandler(new MenuHandler(options) {
            @Override
            public void handleReply(int option, String reply) {
                owner.resetMenuHandler();
                if (option >= 0 && option < itemIds.size()) showBotQuantityMenu(owner, bot, itemIds.get(option));
            }
        });
        player.getSender().sendMenu(options);
    }

    private void showBotQuantityMenu(final Player player, final WorldBot bot, final int itemId) {
        String[] options = { "One", "Five", "Ten", "All", "Cancel" };
        player.setMenuHandler(new MenuHandler(options) {
            @Override
            public void handleReply(int option, String reply) {
                owner.resetMenuHandler();
                int available = bot.inventory.getOrDefault(itemId, 0);
                int[] amounts = { 1, 5, 10, available };
                if (option >= 0 && option < amounts.length) buyFromBot(owner, bot, itemId, Math.min(available, amounts[option]));
            }
        });
        player.getSender().sendMenu(options);
    }

    private void buyFromBot(Player player, WorldBot bot, int itemId, int amount) {
        if (amount < 1 || bot.inventory.getOrDefault(itemId, 0) < amount) {
            player.getSender().sendMessage("@cya@[Marketplace] @red@That offer is no longer available.");
            return;
        }
        int price = GrandExchange.buyPrice(itemId, amount);
        InvItem item = new InvItem(itemId, amount);
        if (player.getInventory().countId(COINS) < price || !player.getInventory().canHold(item)) {
            player.getSender().sendMessage("@cya@[Marketplace] @red@You need " + price + " coins and enough inventory space.");
            return;
        }
        player.getInventory().remove(COINS, price);
        player.getInventory().add(item);
        player.getSender().sendInventory();
        bot.removeInventory(itemId, amount);
        bot.addInventory(COINS, price);
        bot.trades++;
        bot.playerTrades++;
        bot.playerReputation++;
        bot.marketVolume += price;
        bot.say("thanks for buying");
        recordActivity(player.getUsername() + " bought " + amount + " " + itemName(itemId) + " from " + bot.name);
        player.getSender().sendMessage("@cya@[Marketplace] @whi@Bought " + amount + " " + itemName(itemId) + " for " + price + " coins.");
    }

    private void showPlayerSellOffers(final Player player, final WorldBot bot) {
        final List<Integer> itemIds = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (InvItem item : player.getInventory().getItems()) {
            int itemId = item.getID();
            if (itemId == COINS || GrandExchange.sellPrice(itemId, 1) < 1 || itemIds.contains(itemId)) continue;
            int amount = player.getInventory().countId(itemId);
            itemIds.add(itemId);
            labels.add(itemName(itemId) + " x" + amount + " (" + GrandExchange.sellPrice(itemId, 1) + "gp ea)");
            if (itemIds.size() >= 8) break;
        }
        if (itemIds.isEmpty()) {
            player.getSender().sendMessage("@cya@[Marketplace] @whi@You have nothing " + bot.name + " will buy.");
            return;
        }
        labels.add("Cancel");
        String[] options = labels.toArray(new String[0]);
        player.setMenuHandler(new MenuHandler(options) {
            @Override
            public void handleReply(int option, String reply) {
                owner.resetMenuHandler();
                if (option >= 0 && option < itemIds.size()) showPlayerQuantityMenu(owner, bot, itemIds.get(option));
            }
        });
        player.getSender().sendMenu(options);
    }

    private void showPlayerQuantityMenu(final Player player, final WorldBot bot, final int itemId) {
        String[] options = { "One", "Five", "Ten", "All", "Cancel" };
        player.setMenuHandler(new MenuHandler(options) {
            @Override
            public void handleReply(int option, String reply) {
                owner.resetMenuHandler();
                int available = owner.getInventory().countId(itemId);
                int[] amounts = { 1, 5, 10, available };
                if (option >= 0 && option < amounts.length) sellToBot(owner, bot, itemId, Math.min(available, amounts[option]));
            }
        });
        player.getSender().sendMenu(options);
    }

    private void sellToBot(Player player, WorldBot bot, int itemId, int amount) {
        int price = GrandExchange.sellPrice(itemId, amount);
        if (amount < 1 || player.getInventory().countId(itemId) < amount || price < 1 || bot.coins() < price) {
            player.getSender().sendMessage("@cya@[Marketplace] @red@That trade cannot be completed.");
            return;
        }
        if (bot.inventory.getOrDefault(COINS, 0) < price) {
            int needed = price - bot.inventory.getOrDefault(COINS, 0);
            if (!bot.removeFromBank(COINS, needed)) {
                player.getSender().sendMessage("@cya@[Marketplace] @red@" + bot.name + " cannot afford that quantity.");
                return;
            }
            bot.addInventory(COINS, needed);
        }
        if (player.getInventory().remove(itemId, amount) < 0) return;
        player.getInventory().add(new InvItem(COINS, price));
        player.getSender().sendInventory();
        bot.removeInventory(COINS, price);
        bot.addInventory(itemId, amount);
        bot.trades++;
        bot.playerTrades++;
        bot.playerReputation++;
        bot.marketVolume += price;
        bot.say("good deal");
        recordActivity(player.getUsername() + " sold " + amount + " " + itemName(itemId) + " to " + bot.name);
        player.getSender().sendMessage("@cya@[Marketplace] @whi@Sold " + amount + " " + itemName(itemId) + " for " + price + " coins.");
    }

    private void showBotInspection(Player player, WorldBot bot) {
        for (String line : bot.profileReport().split("\n")) {
            player.getSender().sendMessage("@cya@[Inspect] @whi@" + line);
        }
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
        drainChatDeliveries();
        updateWorldEvent();
        updateAutoGroups();
        for (WorldBot bot : bots) {
            bot.tick();
        }
        if (System.currentTimeMillis() - lastStateSave > config.saveEverySeconds * 1000L) {
            saveState();
        }
    }

    private void drainChatDeliveries() {
        for (int i = 0; i < 32; i++) {
            Runnable delivery = chatDeliveries.poll();
            if (delivery == null) return;
            try {
                delivery.run();
            } catch (Exception ignored) {
            }
        }
    }

    private void enqueueChatDelivery(Runnable delivery) {
        if (delivery != null) chatDeliveries.add(delivery);
    }

    private void tryAmbientChat(final WorldBot bot) {
        Player player = World.getWorld().getPlayer();
        long now = System.currentTimeMillis();
        if (config.chatFrequency <= 0 || player == null || !player.isLoggedIn()
                || now < nextPopulationChatAt || bot.npc == null
                || !bot.npc.getLocation().withinRange(player.getLocation(), 14)) return;
        nextPopulationChatAt = now + config.ollamaAmbientCooldownSeconds * 1000L
                + Math.floorMod(bot.name.hashCode(), 10) * 1000L;
        OllamaBotChat.getInstance().reply(bot.chatIdentity(), player.getUsername(), "public",
                "Start a spontaneous conversation about what is happening while " + bot.activity + ".",
                line -> enqueueChatDelivery(() -> {
                    synchronized (WorldBotManager.this) {
                        if (!player.isLoggedIn() || !bot.canSpeakNear(player, 14)) return;
                        bot.showOllamaLine(line);
                        WorldBot partner = null;
                        for (WorldBot candidate : availableBots(player, 14, null, null)) {
                            if (candidate != bot) {
                                partner = candidate;
                                break;
                            }
                        }
                        if (partner == null) return;
                        final WorldBot replyBot = partner;
                        OllamaBotChat.getInstance().reply(replyBot.chatIdentity(), bot.name, "public",
                                bot.name + " just said: " + line + " Reply naturally if you have something to add.",
                                reply -> enqueueChatDelivery(() -> {
                                    synchronized (WorldBotManager.this) {
                                        if (replyBot.canSpeakNear(player, 14)) replyBot.showOllamaLine(reply);
                                    }
                                }), null);
                    }
                }), null);
    }

    private void requestEventSpeech(final WorldBot bot, String context) {
        Player player = World.getWorld().getPlayer();
        long now = System.currentTimeMillis();
        if (config.chatFrequency <= 0 || player == null || !player.isLoggedIn()
                || now < nextEventChatAt || now < bot.nextOllamaEventAt || !bot.canSpeakNear(player, 14)) return;
        nextEventChatAt = now + 4000L;
        bot.nextOllamaEventAt = now + 12_000L;
        OllamaBotChat.getInstance().reply(bot.chatIdentity(), player.getUsername(), "public event",
                "React naturally to this event: " + context,
                line -> enqueueChatDelivery(() -> {
                    synchronized (WorldBotManager.this) {
                        if (bot.canSpeakNear(player, 14)) bot.showOllamaLine(line);
                    }
                }), null);
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
            config.ollamaEnabled = Boolean.parseBoolean(properties.getProperty("ollama_enabled", "true"));
            config.ollamaUrl = properties.getProperty("ollama_url", "http://127.0.0.1:11434").trim();
            config.ollamaModel = properties.getProperty("ollama_model", "qwen3.5:4b").trim();
            config.ollamaTimeoutSeconds = Math.max(5, Math.min(120,
                    parseInt(properties.getProperty("ollama_timeout_seconds"), 30)));
            config.ollamaHistoryMessages = Math.max(2, Math.min(20,
                    parseInt(properties.getProperty("ollama_history_messages"), 8)));
            config.ollamaPublicCooldownSeconds = Math.max(1,
                    parseInt(properties.getProperty("ollama_public_cooldown_seconds"), 4));
            config.ollamaClanCooldownSeconds = Math.max(1,
                    parseInt(properties.getProperty("ollama_clan_cooldown_seconds"), 5));
            config.ollamaAmbientCooldownSeconds = Math.max(5,
                    parseInt(properties.getProperty("ollama_ambient_cooldown_seconds"), 18));
            config.ollamaPersistHistory = Boolean.parseBoolean(
                    properties.getProperty("ollama_persist_history", "true"));
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
            properties.setProperty("ollama_enabled", String.valueOf(config.ollamaEnabled));
            properties.setProperty("ollama_url", config.ollamaUrl);
            properties.setProperty("ollama_model", config.ollamaModel);
            properties.setProperty("ollama_timeout_seconds", String.valueOf(config.ollamaTimeoutSeconds));
            properties.setProperty("ollama_history_messages", String.valueOf(config.ollamaHistoryMessages));
            properties.setProperty("ollama_public_cooldown_seconds", String.valueOf(config.ollamaPublicCooldownSeconds));
            properties.setProperty("ollama_clan_cooldown_seconds", String.valueOf(config.ollamaClanCooldownSeconds));
            properties.setProperty("ollama_ambient_cooldown_seconds", String.valueOf(config.ollamaAmbientCooldownSeconds));
            properties.setProperty("ollama_persist_history", String.valueOf(config.ollamaPersistHistory));
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
            properties.setProperty("ollama_enabled", "true");
            properties.setProperty("ollama_url", "http://127.0.0.1:11434");
            properties.setProperty("ollama_model", "qwen3.5:4b");
            properties.setProperty("ollama_timeout_seconds", "30");
            properties.setProperty("ollama_history_messages", "8");
            properties.setProperty("ollama_public_cooldown_seconds", "4");
            properties.setProperty("ollama_clan_cooldown_seconds", "5");
            properties.setProperty("ollama_ambient_cooldown_seconds", "18");
            properties.setProperty("ollama_persist_history", "true");
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
                String savedSkills = properties.getProperty(prefix + "skills", "");
                if (savedSkills.isEmpty()) {
                    bot.initializeSkills(bot.level);
                } else {
                    parseIntArray(savedSkills, bot.skillXp);
                }
                bot.playerReputation = parseInt(properties.getProperty(prefix + "player_reputation"), bot.playerReputation);
                bot.directedSkill = parseInt(properties.getProperty(prefix + "directed_skill"), -1);
                bot.directedLevel = parseInt(properties.getProperty(prefix + "directed_level"), 0);
                bot.inventory.clear();
                parseItemMap(properties.getProperty(prefix + "inventory", ""), bot.inventory);
                bot.bank.clear();
                parseItemMap(properties.getProperty(prefix + "bank", ""), bot.bank);
                parseIntArray(properties.getProperty(prefix + "equipment", ""), bot.equipmentItems);
                bot.refreshDerivedStats();
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
            properties.setProperty(prefix + "bank", bot.bankString());
            properties.setProperty(prefix + "skills", intArrayString(bot.skillXp));
            properties.setProperty(prefix + "equipment", intArrayString(bot.equipmentItems));
            properties.setProperty(prefix + "player_reputation", String.valueOf(bot.playerReputation));
            properties.setProperty(prefix + "directed_skill", String.valueOf(bot.directedSkill));
            properties.setProperty(prefix + "directed_level", String.valueOf(bot.directedLevel));
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

    private void parseItemMap(String value, Map<Integer, Integer> destination) {
        if (value == null || value.trim().isEmpty()) return;
        for (String pair : value.split(",")) {
            String[] parts = pair.split(":");
            if (parts.length != 2) continue;
            int itemId = parseInt(parts[0], -1);
            int amount = parseInt(parts[1], 0);
            if (itemId >= 0 && amount > 0) destination.put(itemId, amount);
        }
    }

    private void parseIntArray(String value, int[] destination) {
        if (value == null || value.trim().isEmpty()) return;
        String[] parts = value.split(",");
        for (int i = 0; i < parts.length && i < destination.length; i++) {
            destination[i] = Math.max(0, parseInt(parts[i], destination[i]));
        }
    }

    private String intArrayString(int[] values) {
        StringBuilder result = new StringBuilder();
        for (int value : values) {
            if (result.length() > 0) result.append(',');
            result.append(value);
        }
        return result.toString();
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
        private final Map<Integer, Integer> bank = new LinkedHashMap<>();
        private final int[] skillXp = new int[SKILL_NAMES.length];
        private final int[] equipmentItems = new int[4];
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
        private int playerReputation;
        private boolean online = true;
        private long nextSessionChangeAt;
        private String partyOwner;
        private PartyMode partyMode = PartyMode.NONE;
        private AutoGroup autoGroup;
        private long nextAssistAt;
        private String activity;
        private Goal goal;
        private int directedSkill = -1;
        private int directedLevel;
        private long nextOllamaEventAt;

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
            initializeSkills(level);
            initializeEquipment();
            addInventory(COINS, 500 + (level * 25) + random.nextInt(1500));
            refreshDerivedStats();
            goal = Goal.forRole(role);
            activity = "starting in " + area.name;
            scheduleLogout();
        }

        private void initializeSkills(int startingLevel) {
            skillXp[HITS] = Formulae.lvlToXp(10);
            if (role == Role.GATHERER) {
                int gatheringLevel = Math.max(1, startingLevel);
                skillXp[WOODCUTTING] = Formulae.lvlToXp(gatheringLevel);
                skillXp[FISHING] = Formulae.lvlToXp(Math.max(1, gatheringLevel - 2));
                skillXp[MINING] = Formulae.lvlToXp(Math.max(1, gatheringLevel - 1));
                skillXp[COOKING] = Formulae.lvlToXp(Math.max(1, gatheringLevel - 3));
            } else {
                int combat = Math.max(3, startingLevel);
                skillXp[ATTACK] = Formulae.lvlToXp(Math.max(1, combat));
                skillXp[DEFENSE] = Formulae.lvlToXp(Math.max(1, combat - 2));
                skillXp[STRENGTH] = Formulae.lvlToXp(Math.max(1, combat + 1));
                skillXp[HITS] = Formulae.lvlToXp(Math.max(10, combat));
                skillXp[PRAYER] = Formulae.lvlToXp(Math.max(1, combat / 3));
                if (role == Role.WILDERNESS) {
                    skillXp[RANGED] = Formulae.lvlToXp(Math.max(1, combat - 4));
                    skillXp[MAGIC] = Formulae.lvlToXp(Math.max(1, combat - 5));
                }
            }
        }

        private void initializeEquipment() {
            if (role == Role.WILDERNESS) {
                equipmentItems[0] = level >= 60 ? 93 : level >= 40 ? 92 : 90;
                equipmentItems[1] = level >= 60 ? 401 : level >= 40 ? 120 : 118;
                equipmentItems[2] = level >= 60 ? 402 : level >= 40 ? 123 : 121;
                equipmentItems[3] = level >= 60 ? 404 : level >= 40 ? 131 : 129;
            } else if (role == Role.FIGHTER) {
                equipmentItems[0] = level >= 35 ? 91 : 89;
                equipmentItems[1] = level >= 35 ? 119 : 8;
                equipmentItems[2] = level >= 35 ? 122 : 9;
                equipmentItems[3] = level >= 35 ? 130 : 2;
            } else {
                equipmentItems[0] = level >= 40 ? 203 : level >= 20 ? 88 : 12;
            }
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

            WorldBotManager.this.tryAmbientChat(this);

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
            trainCombat(12 + damage * 3);
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
            if (directedSkill >= 0 && skillLevel(directedSkill) >= directedLevel) {
                say(SKILL_NAMES[directedSkill] + " goal complete");
                WorldBotManager.this.recordActivity(name + " reached level " + directedLevel + " " + SKILL_NAMES[directedSkill]);
                directedSkill = -1;
                directedLevel = 0;
            }
            if (directedSkill >= 0) {
                workDirectedSkill();
                return;
            }
            if (role == Role.GATHERER) {
                addInventory(carriedItem, 1 + random.nextInt(4));
                gainSkillXp(skillForItem(carriedItem), 18 + random.nextInt(18));
                activity = "collecting " + carriedItemName + " in " + area.name;
                if (random.nextInt(20) == 0) {
                    say("banking " + carriedItemName + " soon");
                }
            } else if (role == Role.FIGHTER) {
                addInventory(carriedItem, 1 + random.nextInt(3));
                trainCombat(28 + random.nextInt(30));
                activity = "hunting drops in " + area.name;
                if (random.nextInt(4) == 0) {
                    addInventory(COINS, 5 + random.nextInt(60));
                }
            } else {
                addInventory(carriedItem, 1 + random.nextInt(2));
                trainCombat(38 + random.nextInt(38));
                activity = "patrolling " + area.name;
                if (random.nextInt(3) == 0) {
                    addInventory(COINS, 25 + random.nextInt(150));
                }
            }
            if (random.nextInt(5) == 0) {
                processProduction();
            }
        }

        private void workDirectedSkill() {
            int itemId = productForSkill(directedSkill);
            if (itemId >= 0) {
                addInventory(itemId, 1 + random.nextInt(3));
                carriedItem = itemId;
                carriedItemName = itemName(itemId);
            }
            gainSkillXp(directedSkill, 28 + random.nextInt(35));
            activity = "training " + SKILL_NAMES[directedSkill] + " for level " + directedLevel;
            if (random.nextInt(12) == 0) {
                say(SKILL_NAMES[directedSkill] + " goal " + skillLevel(directedSkill) + "/" + directedLevel);
            }
        }

        private void trainCombat(int amount) {
            int style;
            if (role == Role.WILDERNESS && random.nextInt(4) == 0) {
                style = random.nextBoolean() ? RANGED : MAGIC;
            } else {
                int[] melee = { ATTACK, DEFENSE, STRENGTH };
                style = melee[random.nextInt(melee.length)];
            }
            gainSkillXp(style, amount);
            gainSkillXp(HITS, Math.max(1, amount / 3));
            if (random.nextInt(8) == 0) {
                gainSkillXp(PRAYER, Math.max(1, amount / 5));
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
                int[] items = {
                        14, 632, 633, 634, 635, 636,
                        150, 202, 151, 155, 383, 152, 153, 154, 409,
                        349, 351, 358, 356, 366, 372, 369, 545,
                        147, 675, 381
                };
                carriedItem = items[index % items.length];
            } else if (role == Role.FIGHTER) {
                int[] items = {
                        20, 413, 604, 814, 147,
                        31, 32, 33, 34, 35, 36, 38, 40, 41, 42, 46, 619,
                        157, 158, 159, 160,
                        66, 1, 67, 68, 117, 8, 118, 128, 2, 129
                };
                carriedItem = items[index % items.length];
            } else {
                int[] items = {
                        31, 33, 38, 40, 41, 42, 619,
                        11, 638, 640, 642, 644, 646,
                        359, 357, 367, 373, 370, 546,
                        89, 90, 91, 92, 93, 81
                };
                carriedItem = items[index % items.length];
            }
            carriedItemName = itemName(carriedItem);
        }

        private void say(String context) {
            WorldBotManager.this.requestEventSpeech(this, context);
        }

        private void showOllamaLine(String message) {
            if (message == null || message.trim().isEmpty() || message.equals(lastMessage)) {
                return;
            }
            lastMessage = message;
            messageSequence++;
            messageUntil = System.currentTimeMillis() + 5000;
        }

        private boolean canSpeakNear(Player player, int radius) {
            return player != null && player.isLoggedIn() && active && online && npc != null && !npc.isRemoved()
                    && npc.getLocation().withinRange(player.getLocation(), radius);
        }

        private OllamaBotChat.BotIdentity chatIdentity() {
            String goalText = directedSkill >= 0
                    ? SKILL_NAMES[directedSkill] + " level " + directedLevel : goal.label;
            String interests = role == Role.GATHERER
                    ? "skilling, resources, trading, and the exchange"
                    : role == Role.FIGHTER
                            ? "combat training, equipment, drops, and group trips"
                            : "the wilderness, risk, loot, clans, and player killing";
            return new OllamaBotChat.BotIdentity(name, personality.title, personality.clan,
                    personality.rivalClan, area.name, activity, goalText, interests, level);
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
            return active && online && npc != null && !npc.isRemoved() && (!inventory.isEmpty() || !bank.isEmpty());
        }

        private String profileReport() {
            StringBuilder report = new StringBuilder(name)
                    .append(" - ").append(personality.title).append(" [").append(personality.clan).append("]")
                    .append("\nCombat ").append(level).append(" | Total ").append(totalLevel()).append(" | XP ").append(totalXp())
                    .append("\nGoal: ").append(directedSkill >= 0
                            ? SKILL_NAMES[directedSkill] + " " + skillLevel(directedSkill) + "/" + directedLevel
                            : goal.label)
                    .append(" | ").append(activity)
                    .append("\nCoins: ").append(coins()).append(" | Bank value: ").append(bankValue())
                    .append(" | Trades: ").append(trades).append(" | Reputation: ").append(playerReputation)
                    .append("\nEquipment: ");
            boolean hasEquipment = false;
            for (int itemId : equipmentItems) {
                if (itemId <= 0) continue;
                if (hasEquipment) report.append(", ");
                report.append(itemName(itemId));
                hasEquipment = true;
            }
            if (!hasEquipment) report.append("none");
            for (int i = 0; i < SKILL_NAMES.length; i += 6) {
                report.append('\n');
                for (int j = i; j < i + 6 && j < SKILL_NAMES.length; j++) {
                    if (j > i) report.append(" | ");
                    report.append(SKILL_NAMES[j]).append(' ').append(skillLevel(j));
                }
            }
            return report.toString();
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
            return itemMapString(inventory);
        }

        private String bankString() {
            return itemMapString(bank);
        }

        private String itemMapString(Map<Integer, Integer> items) {
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
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
                int saved = Math.max(1, entry.getValue() / 4);
                int offered = entry.getValue() - saved;
                addToBank(entry.getKey(), saved);
                int paid = offered > 0 ? GrandExchange.sellSystem(entry.getKey(), offered) : 0;
                if (paid > 0) {
                    deposited += offered;
                    coins += paid;
                    marketVolume += paid;
                }
                inventory.remove(entry.getKey());
            }
            if (coins > 0) {
                addInventory(COINS, coins);
                trades++;
            }
            itemsBanked += deposited;
            activity = "selling " + deposited + " items for " + coins + " coins";
            processProduction();
            return deposited;
        }

        private void addToBank(int itemId, int amount) {
            if (amount > 0) {
                bank.put(itemId, bank.getOrDefault(itemId, 0) + amount);
            }
        }

        private boolean removeFromBank(int itemId, int amount) {
            int current = bank.getOrDefault(itemId, 0);
            if (amount < 1 || current < amount) return false;
            if (current == amount) bank.remove(itemId);
            else bank.put(itemId, current - amount);
            return true;
        }

        private int bankValue() {
            long value = 0;
            for (Map.Entry<Integer, Integer> entry : bank.entrySet()) {
                value += (long) GrandExchange.sellPrice(entry.getKey(), 1) * entry.getValue();
            }
            return (int) Math.min(Integer.MAX_VALUE, value);
        }

        private int coins() {
            return inventory.getOrDefault(COINS, 0) + bank.getOrDefault(COINS, 0);
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

        private void gainSkillXp(int skill, int amount) {
            if (skill < 0 || skill >= skillXp.length || amount < 1) {
                return;
            }
            int oldLevel = skillLevel(skill);
            long gained = (long) amount * xpRate;
            skillXp[skill] = (int) Math.min(Integer.MAX_VALUE, skillXp[skill] + gained);
            int newLevel = skillLevel(skill);
            refreshDerivedStats();
            if (newLevel > oldLevel) {
                say(SKILL_NAMES[skill] + " level " + newLevel);
            }
        }

        private int skillLevel(int skill) {
            return Formulae.experienceToLevel(Math.max(0, skillXp[skill]));
        }

        private int totalLevel() {
            int total = 0;
            for (int i = 0; i < skillXp.length; i++) {
                total += skillLevel(i);
            }
            return total;
        }

        private int totalXp() {
            long total = 0;
            for (int value : skillXp) {
                total += Math.max(0, value);
            }
            return (int) Math.min(Integer.MAX_VALUE, total);
        }

        private void refreshDerivedStats() {
            int[] levels = new int[SKILL_NAMES.length];
            for (int i = 0; i < levels.length; i++) {
                levels[i] = skillLevel(i);
            }
            int previous = level;
            level = Math.max(3, Formulae.getCombatlevel(levels));
            xp = totalXp();
            if (npc != null) {
                npc.setCombatLevel(level);
            }
            if (level > previous && previous > 0) {
                say("combat level " + level);
            }
        }

        private void processProduction() {
            int[][] recipes = {
                    { 349, 350, COOKING }, { 358, 359, COOKING }, { 356, 357, COOKING },
                    { 366, 367, COOKING }, { 372, 373, COOKING }, { 369, 370, COOKING }, { 545, 546, COOKING },
                    { 632, 649, FLETCHING }, { 633, 651, FLETCHING }, { 634, 653, FLETCHING },
                    { 635, 655, FLETCHING }, { 636, 657, FLETCHING },
                    { 150, 169, SMITHING }, { 151, 170, SMITHING }, { 153, 173, SMITHING },
                    { 154, 174, SMITHING }, { 409, 408, SMITHING },
                    { 147, 148, CRAFTING }, { 675, 676, CRAFTING }
            };
            for (int[] recipe : recipes) {
                if (bank.getOrDefault(recipe[0], 0) < 1) {
                    continue;
                }
                removeFromBank(recipe[0], 1);
                addToBank(recipe[1], 1);
                gainSkillXp(recipe[2], 20 + random.nextInt(30));
                activity = "making " + itemName(recipe[1]) + " from banked supplies";
                if (bank.getOrDefault(recipe[1], 0) >= 5) {
                    int amount = Math.min(5, bank.get(recipe[1]));
                    int paid = GrandExchange.sellSystem(recipe[1], amount);
                    if (paid > 0) {
                        removeFromBank(recipe[1], amount);
                        addToBank(COINS, paid);
                        marketVolume += paid;
                        trades++;
                    }
                }
                return;
            }
        }

        private int skillForItem(int itemId) {
            if (itemId == 14 || itemId >= 632 && itemId <= 636) return WOODCUTTING;
            if (itemId == 150 || itemId == 202 || itemId == 151 || itemId == 155 || itemId == 383
                    || itemId == 152 || itemId == 153 || itemId == 154 || itemId == 409) return MINING;
            if (itemId == 349 || itemId == 351 || itemId == 358 || itemId == 356 || itemId == 366
                    || itemId == 372 || itemId == 369 || itemId == 545) return FISHING;
            if (itemId == 147 || itemId == 675 || itemId == 381) return CRAFTING;
            if (itemId == 20 || itemId == 413 || itemId == 604 || itemId == 814) return PRAYER;
            if (itemId == 31 || itemId == 32 || itemId == 33 || itemId == 34 || itemId == 35
                    || itemId == 36 || itemId == 38 || itemId == 40 || itemId == 41 || itemId == 42
                    || itemId == 46 || itemId == 619) return MAGIC;
            if (itemId == 11 || itemId == 638 || itemId == 640 || itemId == 642 || itemId == 644 || itemId == 646) return RANGED;
            return role == Role.GATHERER ? CRAFTING : ATTACK;
        }

        private int productForSkill(int skill) {
            int[][] products = {
                    { ATTACK, 20 }, { DEFENSE, 20 }, { STRENGTH, 20 }, { HITS, 20 }, { RANGED, 638 },
                    { PRAYER, 413 }, { MAGIC, 41 }, { COOKING, 373 }, { WOODCUTTING, 633 }, { FLETCHING, 651 },
                    { FISHING, 372 }, { FIREMAKING, 14 }, { CRAFTING, 148 }, { SMITHING, 171 }, { MINING, 155 },
                    { HERBLAW, 474 }, { AGILITY, 381 }, { THIEVING, 10 }
            };
            for (int[] product : products) {
                if (product[0] == skill) return product[1];
            }
            return -1;
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
            if (index >= NAMES.length) {
                name += " " + (index / NAMES.length + 1);
            }
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
        private boolean ollamaEnabled = true;
        private String ollamaUrl = "http://127.0.0.1:11434";
        private String ollamaModel = "qwen3.5:4b";
        private int ollamaTimeoutSeconds = 30;
        private int ollamaHistoryMessages = 8;
        private int ollamaPublicCooldownSeconds = 4;
        private int ollamaClanCooldownSeconds = 5;
        private int ollamaAmbientCooldownSeconds = 18;
        private boolean ollamaPersistHistory = true;

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

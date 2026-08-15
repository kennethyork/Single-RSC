package org.nemotech.rsc.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import org.nemotech.rsc.Constants;
import org.nemotech.rsc.model.World;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.model.player.SaveFile;
import org.nemotech.rsc.util.Formulae;

public final class HighscoresExporter {

    private static final String PRIVATE_OUTPUT_FILE = Constants.CACHE_DIRECTORY + "highscores-export.json";
    private static final String[] SKILL_NAMES = {
            "Attack", "Defense", "Strength", "Hits", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
            "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblaw", "Agility", "Thieving"
    };

    private HighscoresExporter() {}

    public static void main(String[] args) {
        export();
    }

    public static synchronized void export() {
        try {
            List<Entry> entries = new ArrayList<>();
            Properties worldBotState = readProperties(Constants.CACHE_DIRECTORY + "worldbots_state.properties");
            Set<String> onlineNames = onlinePlayerNames();
            readPlayerSaves(entries, onlineNames);
            readWorldBotState(entries, worldBotState);
            rankEntries(entries);
            String json = toJson(entries, worldBotState, onlineNames);
            writeAtomically(PRIVATE_OUTPUT_FILE, json);
        } catch (Exception e) {
            System.err.println("[Highscores] Could not export highscores: " + e.getMessage());
        }
    }

    private static void writeAtomically(String path, String json) throws Exception {
        File out = new File(path);
        File parent = out.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        File temporary = new File(parent == null ? new File(".") : parent, out.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(temporary)) {
            writer.write(json);
        }
        try {
            Files.move(temporary.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void rankEntries(List<Entry> entries) {
        List<Entry> players = new ArrayList<>();
        List<Entry> bots = new ArrayList<>();
        for (Entry entry : entries) {
            (entry.worldBot ? bots : players).add(entry);
        }

        players.sort(Comparator.comparingInt((Entry entry) -> entry.level).reversed()
                .thenComparing(Comparator.comparingLong((Entry entry) -> entry.xp).reversed())
                .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
        bots.sort(Comparator.comparingLong((Entry entry) -> entry.score).reversed()
                .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
        assignRanks(players);
        assignRanks(bots);
        entries.clear();
        entries.addAll(players);
        entries.addAll(bots);
    }

    private static void assignRanks(List<Entry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).rank = i + 1;
        }
    }

    private static Set<String> onlinePlayerNames() {
        Set<String> names = new HashSet<>();
        try {
            World world = World.getLoadedWorld();
            if (world == null) {
                return names;
            }
            for (Player player : world.getPlayers()) {
                if (player != null && !player.isHeadless() && player.isLoggedIn() && player.getUsername() != null) {
                    names.add(player.getUsername().trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ignored) {
            // The standalone exporter can run before the game world is available.
        }
        return names;
    }

    private static void readPlayerSaves(List<Entry> entries, Set<String> onlineNames) {
        File dir = new File(Constants.CACHE_DIRECTORY + "players");
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            if (!name.endsWith("_data.dat")) {
                continue;
            }
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
                SaveFile save = (SaveFile) in.readObject();
                String username = name.substring(0, name.length() - "_data.dat".length());
                Entry entry = new Entry();
                entry.name = username;
                entry.type = save.hardcore ? "Hardcore player" : "Player";
                entry.worldBot = false;
                entry.role = entry.type;
                entry.clan = "Your characters";
                entry.status = onlineNames.contains(username.trim().toLowerCase(Locale.ROOT))
                        ? "Online" : "Offline";
                entry.goal = save.hardcoreDead ? "hardcore dead" : "saved character";
                entry.activity = entry.goal;
                entry.xpRate = Math.max(1, save.xpRate) + "x";
                entry.level = totalLevel(save.expStats);
                entry.xp = totalXp(save.expStats);
                entry.skills = skillEntries(save.expStats);
                entry.score = entry.xp / 10 + entry.level * 100 + save.questPoints * 250 + bankScore(save);
                entry.coins = 0;
                entry.trades = 0;
                entry.kills = 0;
                entry.lastSavedAt = file.lastModified();
                entries.add(entry);
            } catch (Exception ignored) {
                // A partial or old save should not block the website export.
            }
        }
    }

    private static Properties readProperties(String path) {
        File file = new File(path);
        Properties properties = new Properties();
        if (!file.exists()) {
            return properties;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            properties.load(in);
        } catch (Exception ignored) {
        }
        return properties;
    }

    private static void readWorldBotState(List<Entry> entries, Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }

        for (int i = 0; i < 200; i++) {
            String prefix = "bot." + i + ".";
            String name = properties.getProperty(prefix + "name");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            int level = parseInt(properties.getProperty(prefix + "level"), 3);
            int xp = parseInt(properties.getProperty(prefix + "xp"), 0);
            int xpRate = parseInt(properties.getProperty(prefix + "xp_rate"), 1);
            int kills = parseInt(properties.getProperty(prefix + "kills"), 0);
            int deaths = parseInt(properties.getProperty(prefix + "deaths"), 0);
            int fights = parseInt(properties.getProperty(prefix + "fights"), 0);
            int banked = parseInt(properties.getProperty(prefix + "banked"), 0);
            int trades = parseInt(properties.getProperty(prefix + "trades"), 0);
            int groupTrips = parseInt(properties.getProperty(prefix + "group_trips"), 0);
            int playerTrades = parseInt(properties.getProperty(prefix + "player_trades"), 0);
            int playerKills = parseInt(properties.getProperty(prefix + "player_kills"), 0);
            boolean online = Boolean.parseBoolean(properties.getProperty(prefix + "online", "true"));
            int[] skillXp = parseIntArray(properties.getProperty(prefix + "skills"), SKILL_NAMES.length);

            Entry entry = new Entry();
            entry.name = name;
            entry.type = "World bot";
            entry.worldBot = true;
            entry.role = roleLabel(properties.getProperty(prefix + "role"));
            entry.clan = "World bots";
            entry.status = online ? "Online" : "Offline";
            entry.goal = goalLabel(properties.getProperty(prefix + "goal"));
            entry.activity = properties.getProperty(prefix + "activity", online ? entry.goal : "offline");
            entry.combatLevel = level;
            entry.level = hasProgress(skillXp) ? totalLevel(skillXp) : level;
            entry.xpRate = Math.max(1, xpRate) + "x";
            entry.xp = hasProgress(skillXp) ? totalXp(skillXp) : (long) xp * Math.max(1, xpRate);
            entry.skills = hasProgress(skillXp) ? skillEntries(skillXp) : null;
            entry.coins = coinsFromInventory(properties.getProperty(prefix + "inventory"));
            entry.trades = trades;
            entry.kills = kills;
            entry.groupTrips = groupTrips;
            entry.playerTrades = playerTrades;
            entry.playerKills = playerKills;
            entry.score = (long) kills * 1000 + (long) fights * 50 + (long) level * 100 + entry.xp / 10
                    + (long) banked * 5 + (long) trades * 25 + entry.coins / 25 - (long) deaths * 250;
            entries.add(entry);
        }
    }

    private static int totalLevel(int[] expStats) {
        if (expStats == null) {
            return 0;
        }
        int total = 0;
        for (int xp : expStats) {
            total += Formulae.experienceToLevel(Math.max(0, xp));
        }
        return total;
    }

    private static long totalXp(int[] expStats) {
        if (expStats == null) {
            return 0;
        }
        long total = 0;
        for (int xp : expStats) {
            total += Math.max(0, xp);
        }
        return total;
    }

    private static List<SkillEntry> skillEntries(int[] expStats) {
        List<SkillEntry> skills = new ArrayList<>();
        if (expStats == null) {
            return skills;
        }
        for (int i = 0; i < SKILL_NAMES.length && i < expStats.length; i++) {
            int xp = Math.max(0, expStats[i]);
            SkillEntry skill = new SkillEntry();
            skill.name = SKILL_NAMES[i];
            skill.level = Formulae.experienceToLevel(xp);
            skill.xp = xp;
            skills.add(skill);
        }
        return skills;
    }

    private static long bankScore(SaveFile save) {
        long score = 0;
        if (save.bankAmounts != null) {
            for (int amount : save.bankAmounts) {
                score += Math.max(0, amount);
            }
        }
        return score;
    }

    private static int coinsFromInventory(String inventory) {
        if (inventory == null || inventory.trim().isEmpty()) {
            return 0;
        }
        int coins = 0;
        String[] items = inventory.split(",");
        for (String item : items) {
            String[] parts = item.split(":");
            if (parts.length == 2 && parseInt(parts[0], -1) == 10) {
                coins += parseInt(parts[1], 0);
            }
        }
        return coins;
    }

    private static String roleLabel(String role) {
        if ("WILDERNESS".equals(role)) {
            return "PKer";
        }
        if ("FIGHTER".equals(role)) {
            return "Monster hunter";
        }
        return "Skiller";
    }

    private static String goalLabel(String goal) {
        if (goal == null || goal.trim().isEmpty()) {
            return "skilling";
        }
        return goal.toLowerCase().replace('_', ' ');
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int[] parseIntArray(String value, int size) {
        int[] values = new int[size];
        if (value == null || value.trim().isEmpty()) {
            return values;
        }
        String[] parts = value.split(",");
        for (int i = 0; i < parts.length && i < values.length; i++) {
            values[i] = Math.max(0, parseInt(parts[i], 0));
        }
        return values;
    }

    private static boolean hasProgress(int[] values) {
        if (values == null) return false;
        for (int value : values) {
            if (value > 0) return true;
        }
        return false;
    }

    private static String toJson(List<Entry> entries, Properties worldBotState, Set<String> onlineNames) {
        StringBuilder json = new StringBuilder();
        json.append("{\"formatVersion\":1,\"generatedAt\":").append(System.currentTimeMillis())
                .append(",\"skills\":[");
        for (int i = 0; i < SKILL_NAMES.length; i++) {
            if (i > 0) json.append(',');
            json.append('"').append(escape(SKILL_NAMES[i])).append('"');
        }
        json.append("],\"activePlayers\":[");
        boolean firstOnline = true;
        for (String name : onlineNames) {
            if (!firstOnline) {
                json.append(',');
            }
            json.append('"').append(escape(name)).append('"');
            firstOnline = false;
        }
        json.append("],\"players\":[");
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append('{')
                    .append("\"rank\":").append(entry.rank).append(',')
                    .append("\"name\":\"").append(escape(entry.name)).append("\",")
                    .append("\"type\":\"").append(escape(entry.type)).append("\",")
                    .append("\"role\":\"").append(escape(entry.role)).append("\",")
                    .append("\"clan\":\"").append(escape(entry.clan)).append("\",")
                    .append("\"status\":\"").append(escape(entry.status)).append("\",")
                    .append("\"goal\":\"").append(escape(entry.goal)).append("\",")
                    .append("\"activity\":\"").append(escape(entry.activity)).append("\",")
                    .append("\"level\":").append(entry.level).append(',')
                    .append("\"combatLevel\":").append(entry.combatLevel).append(',')
                    .append("\"xpRate\":\"").append(escape(entry.xpRate)).append("\",")
                    .append("\"xp\":").append(entry.xp).append(',')
                    .append("\"score\":").append(entry.score).append(',')
                    .append("\"coins\":").append(entry.coins).append(',')
                    .append("\"trades\":").append(entry.trades).append(',')
                    .append("\"kills\":").append(entry.kills).append(',')
                    .append("\"groupTrips\":").append(entry.groupTrips).append(',')
                    .append("\"playerTrades\":").append(entry.playerTrades).append(',')
                    .append("\"playerKills\":").append(entry.playerKills);
            if (entry.skills != null && !entry.skills.isEmpty()) {
                json.append(",\"skills\":[");
                for (int j = 0; j < entry.skills.size(); j++) {
                    SkillEntry skill = entry.skills.get(j);
                    if (j > 0) {
                        json.append(',');
                    }
                    json.append('{')
                            .append("\"skill\":\"").append(escape(skill.name)).append("\",")
                            .append("\"level\":").append(skill.level).append(',')
                            .append("\"xp\":").append(skill.xp)
                            .append('}');
                }
                json.append(']');
            }
            json
                    .append('}');
        }
        json.append("],\"entries\":").append(entriesJson(entries, onlineNames))
                .append(",\"activity\":").append(activityJson(worldBotState))
                .append(",\"market\":").append(marketJson(worldBotState))
                .append(",\"groups\":").append(groupsJson(worldBotState))
                .append('}');
        return json.toString();
    }

    private static String entriesJson(List<Entry> entries, Set<String> onlineNames) {
        List<Entry> visible = new ArrayList<>();
        Entry newestSavedCharacter = null;
        boolean hasActiveCharacter = false;
        for (Entry entry : entries) {
            if (entry.worldBot) {
                visible.add(entry);
                continue;
            }
            if (onlineNames.contains(entry.name.trim().toLowerCase(Locale.ROOT))) {
                visible.add(entry);
                hasActiveCharacter = true;
            }
            if (newestSavedCharacter == null || entry.lastSavedAt > newestSavedCharacter.lastSavedAt) {
                newestSavedCharacter = entry;
            }
        }
        if (!hasActiveCharacter && newestSavedCharacter != null) {
            visible.add(newestSavedCharacter);
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < visible.size(); i++) {
            Entry entry = visible.get(i);
            if (i > 0) json.append(',');
            json.append('{')
                    .append("\"displayName\":\"").append(escape(entry.name)).append("\",")
                    .append("\"username\":\"").append(escape(entry.name)).append("\",")
                    .append("\"bot\":").append(entry.worldBot).append(',')
                    .append("\"activity\":\"").append(escape(entry.activity)).append("\",")
                    .append("\"combatLevel\":").append(entry.worldBot ? entry.combatLevel : combatLevel(entry)).append(',')
                    .append("\"totalLevel\":").append(entry.level).append(',')
                    .append("\"totalXp\":").append(entry.xp).append(',')
                    .append("\"levels\":[");
            appendSkillValues(json, entry, true);
            json.append("],\"xp\":[");
            appendSkillValues(json, entry, false);
            json.append("]}");
        }
        return json.append(']').toString();
    }

    private static void appendSkillValues(StringBuilder json, Entry entry, boolean levels) {
        for (int i = 0; i < SKILL_NAMES.length; i++) {
            if (i > 0) json.append(',');
            SkillEntry skill = entry.skills != null && i < entry.skills.size() ? entry.skills.get(i) : null;
            json.append(skill == null ? (levels ? 1 : 0) : (levels ? skill.level : skill.xp));
        }
    }

    private static int combatLevel(Entry entry) {
        if (entry.skills == null || entry.skills.size() < 7) return 3;
        int attack = entry.skills.get(0).level;
        int defense = entry.skills.get(1).level;
        int strength = entry.skills.get(2).level;
        int hits = entry.skills.get(3).level;
        int ranged = entry.skills.get(4).level;
        int prayer = entry.skills.get(5).level;
        int magic = entry.skills.get(6).level;
        double base = 0.25 * (defense + hits + prayer / 2.0);
        double offense = Math.max(0.325 * (attack + strength),
                Math.max(0.325 * ranged * 1.5, 0.325 * magic * 1.5));
        return Math.max(3, (int) Math.floor(base + offense));
    }

    private static String activityJson(Properties properties) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        String event = properties == null ? null : properties.getProperty("world.event");
        if (event != null && !event.trim().isEmpty()) {
            json.append("\"").append(escape("Current event: " + event)).append("\"");
            first = false;
        }
        if (properties != null) {
            for (int i = 0; i < 12; i++) {
                String line = properties.getProperty("activity." + i);
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                if (!first) {
                    json.append(',');
                }
                json.append("\"").append(escape(line)).append("\"");
                first = false;
            }
        }
        json.append(']');
        return json.toString();
    }

    private static String marketJson(Properties properties) {
        int online = 0;
        int totalCoins = 0;
        int totalTrades = 0;
        int groupTrips = 0;
        int playerTrades = 0;
        int playerKills = 0;
        if (properties != null) {
            for (int i = 0; i < 200; i++) {
                String prefix = "bot." + i + ".";
                if (properties.getProperty(prefix + "name") == null) {
                    continue;
                }
                if (Boolean.parseBoolean(properties.getProperty(prefix + "online", "true"))) {
                    online++;
                }
                totalCoins += coinsFromInventory(properties.getProperty(prefix + "inventory"));
                totalTrades += parseInt(properties.getProperty(prefix + "trades"), 0);
                groupTrips += parseInt(properties.getProperty(prefix + "group_trips"), 0);
                playerTrades += parseInt(properties.getProperty(prefix + "player_trades"), 0);
                playerKills += parseInt(properties.getProperty(prefix + "player_kills"), 0);
            }
        }
        return "{\"onlineBots\":" + online
                + ",\"coinsInBotInventories\":" + totalCoins
                + ",\"botTrades\":" + totalTrades
                + ",\"groupTrips\":" + groupTrips
                + ",\"playerTrades\":" + playerTrades
                + ",\"assistedKills\":" + playerKills
                + "}";
    }

    private static String groupsJson(Properties properties) {
        StringBuilder json = new StringBuilder("[");
        int count = properties == null ? 0 : parseInt(properties.getProperty("groups.count"), 0);
        for (int i = 0; i < count; i++) {
            String prefix = "group." + i + ".";
            if (i > 0) {
                json.append(',');
            }
            json.append('{')
                    .append("\"id\":").append(parseInt(properties.getProperty(prefix + "id"), i + 1)).append(',')
                    .append("\"goal\":\"").append(escape(properties.getProperty(prefix + "goal", "group"))).append("\",")
                    .append("\"leader\":\"").append(escape(properties.getProperty(prefix + "leader", ""))).append("\",")
                    .append("\"area\":\"").append(escape(properties.getProperty(prefix + "area", ""))).append("\",")
                    .append("\"members\":\"").append(escape(properties.getProperty(prefix + "members", ""))).append("\",")
                    .append("\"size\":").append(parseInt(properties.getProperty(prefix + "size"), 0))
                    .append('}');
        }
        json.append(']');
        return json.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.toString();
    }

    private static final class Entry {
        private int rank;
        private boolean worldBot;
        private String name;
        private String type;
        private String role;
        private String clan;
        private String status;
        private String goal;
        private String activity;
        private int level;
        private int combatLevel;
        private String xpRate;
        private long xp;
        private long score;
        private int coins;
        private int trades;
        private int kills;
        private int groupTrips;
        private int playerTrades;
        private int playerKills;
        private long lastSavedAt;
        private List<SkillEntry> skills;
    }

    private static final class SkillEntry {
        private String name;
        private int level;
        private int xp;
    }
}

package org.nemotech.rsc.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import org.nemotech.rsc.Constants;
import org.nemotech.rsc.model.player.SaveFile;
import org.nemotech.rsc.util.Formulae;

public final class HighscoresExporter {

    private static final String OUTPUT_FILE = "docs/highscores.json";
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
            readPlayerSaves(entries);
            readWorldBotState(entries);
            entries.sort(new Comparator<Entry>() {
                @Override
                public int compare(Entry first, Entry second) {
                    return second.score - first.score;
                }
            });
            for (int i = 0; i < entries.size(); i++) {
                entries.get(i).rank = i + 1;
            }

            File out = new File(OUTPUT_FILE);
            File parent = out.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(out)) {
                writer.write(toJson(entries));
            }
        } catch (Exception e) {
            System.err.println("[Highscores] Could not export highscores: " + e.getMessage());
        }
    }

    private static void readPlayerSaves(List<Entry> entries) {
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
                entry.role = entry.type;
                entry.clan = "Your characters";
                entry.status = "Offline";
                entry.goal = save.hardcoreDead ? "hardcore dead" : "saved character";
                entry.xpRate = Math.max(1, save.xpRate) + "x";
                entry.level = totalLevel(save.expStats);
                entry.xp = totalXp(save.expStats);
                entry.skills = skillEntries(save.expStats);
                entry.score = entry.xp / 10 + entry.level * 100 + save.questPoints * 250 + bankScore(save);
                entry.coins = 0;
                entry.trades = 0;
                entry.kills = 0;
                entries.add(entry);
            } catch (Exception ignored) {
                // A partial or old save should not block the website export.
            }
        }
    }

    private static void readWorldBotState(List<Entry> entries) {
        File file = new File(Constants.CACHE_DIRECTORY + "worldbots_state.properties");
        if (!file.exists()) {
            return;
        }
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            properties.load(in);
        } catch (Exception e) {
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

            Entry entry = new Entry();
            entry.name = name;
            entry.type = "World bot";
            entry.role = roleLabel(properties.getProperty(prefix + "role"));
            entry.clan = "World bots";
            entry.status = "Offline";
            entry.goal = goalLabel(properties.getProperty(prefix + "goal"));
            entry.level = level;
            entry.xpRate = Math.max(1, xpRate) + "x";
            entry.xp = xp * Math.max(1, xpRate);
            entry.coins = coinsFromInventory(properties.getProperty(prefix + "inventory"));
            entry.trades = trades;
            entry.kills = kills;
            entry.score = kills * 1000 + fights * 50 + level * 100 + entry.xp / 10 + banked * 5 + trades * 25 + entry.coins / 25 - deaths * 250;
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

    private static int totalXp(int[] expStats) {
        if (expStats == null) {
            return 0;
        }
        int total = 0;
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

    private static int bankScore(SaveFile save) {
        int score = 0;
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

    private static String toJson(List<Entry> entries) {
        StringBuilder json = new StringBuilder();
        json.append("{\"generatedAt\":").append(System.currentTimeMillis()).append(",\"players\":[");
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
                    .append("\"level\":").append(entry.level).append(',')
                    .append("\"xpRate\":\"").append(escape(entry.xpRate)).append("\",")
                    .append("\"xp\":").append(entry.xp).append(',')
                    .append("\"score\":").append(entry.score).append(',')
                    .append("\"coins\":").append(entry.coins).append(',')
                    .append("\"trades\":").append(entry.trades).append(',')
                    .append("\"kills\":").append(entry.kills);
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
        json.append("]}");
        return json.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class Entry {
        private int rank;
        private String name;
        private String type;
        private String role;
        private String clan;
        private String status;
        private String goal;
        private int level;
        private String xpRate;
        private int xp;
        private int score;
        private int coins;
        private int trades;
        private int kills;
        private List<SkillEntry> skills;
    }

    private static final class SkillEntry {
        private String name;
        private int level;
        private int xp;
    }
}

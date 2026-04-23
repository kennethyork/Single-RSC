package org.nemotech.rsc.bot;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;

/**
 * Manages all bot instances.
 * Provides methods to register, start, stop, and query bots.
 */
public class BotManager {
    
    private static BotManager instance;
    
    /** Map of bot name to bot instance */
    private final Map<String, Bot> bots = new HashMap<>();
    
    /** The currently active bot */
    private Bot activeBot = null;
    
    private BotManager() {}
    
    /**
     * Gets the singleton instance of the BotManager.
     */
    public static BotManager getInstance() {
        if (instance == null) {
            instance = new BotManager();
        }
        return instance;
    }
    
    /**
     * Registers a bot with the manager.
     * @param bot The bot to register
     */
    public void register(Bot bot) {
        if (bot == null) {
            return;
        }
        bots.put(bot.getName().toLowerCase(), bot);
        System.out.println("[BotManager] Registered bot: " + bot.getName());
    }
    
    /**
     * Unregisters a bot from the manager.
     * @param botName The name of the bot to unregister
     */
    public void unregister(String botName) {
        Bot bot = bots.remove(botName.toLowerCase());
        if (bot != null) {
            if (bot.isRunning()) {
                bot.stop();
            }
            if (activeBot == bot) {
                activeBot = null;
            }
            System.out.println("[BotManager] Unregistered bot: " + bot.getName());
        }
    }
    
    /**
     * Gets a bot by name.
     * @param botName The name of the bot
     * @return The bot instance, or null if not found
     */
    public Bot getBot(String botName) {
        return bots.get(botName.toLowerCase());
    }
    
    /**
     * Gets all registered bots.
     * @return A collection of all registered bots
     */
    public Collection<Bot> getAllBots() {
        return bots.values();
    }
    
    /**
     * Gets the currently active bot.
     * @return The active bot, or null if no bot is running
     */
    public Bot getActiveBot() {
        return activeBot;
    }
    
    /**
     * Starts a bot by name.
     * @param botName The name of the bot to start
     * @return true if the bot was started, false otherwise
     */
    public boolean startBot(String botName) {
        Bot bot = getBot(botName);
        if (bot == null) {
            System.out.println("[BotManager] Bot not found: " + botName);
            return false;
        }
        
        // Stop any currently running bot
        if (activeBot != null && activeBot.isRunning()) {
            activeBot.stop();
        }
        
        activeBot = bot;
        bot.start();
        return true;
    }
    
    /**
     * Stops a bot by name.
     * @param botName The name of the bot to stop
     * @return true if the bot was stopped, false otherwise
     */
    public boolean stopBot(String botName) {
        Bot bot = getBot(botName);
        if (bot == null) {
            System.out.println("[BotManager] Bot not found: " + botName);
            return false;
        }
        
        if (bot.isRunning()) {
            bot.stop();
            if (activeBot == bot) {
                activeBot = null;
            }
            return true;
        }
        return false;
    }
    
    /**
     * Stops all running bots.
     */
    public void stopAll() {
        for (Bot bot : bots.values()) {
            if (bot.isRunning()) {
                bot.stop();
            }
        }
        activeBot = null;
    }
    
    /**
     * Pauses a bot by name.
     * @param botName The name of the bot to pause
     * @return true if the bot was paused, false otherwise
     */
    public boolean pauseBot(String botName) {
        Bot bot = getBot(botName);
        if (bot != null && bot.isRunning()) {
            bot.pause();
            return true;
        }
        return false;
    }
    
    /**
     * Resumes a bot by name.
     * @param botName The name of the bot to resume
     * @return true if the bot was resumed, false otherwise
     */
    public boolean resumeBot(String botName) {
        Bot bot = getBot(botName);
        if (bot != null && bot.isRunning()) {
            bot.resume();
            return true;
        }
        return false;
    }
    
    /**
     * Toggles pause state of a bot.
     * @param botName The name of the bot
     * @return true if the operation was successful, false otherwise
     */
    public boolean togglePauseBot(String botName) {
        Bot bot = getBot(botName);
        if (bot != null && bot.isRunning()) {
            bot.togglePause();
            return true;
        }
        return false;
    }
    
    /**
     * Gets a list of all registered bot names.
     * @return Array of bot names
     */
    public String[] getBotNames() {
        return bots.keySet().toArray(new String[0]);
    }
    
    /**
     * Checks if a bot is registered.
     * @param botName The name to check
     * @return true if a bot with that name exists
     */
    public boolean hasBot(String botName) {
        return bots.containsKey(botName.toLowerCase());
    }
    
    /**
     * Gets status information about all bots.
     * @return A formatted status string
     */
    public String getStatusReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Bot Status Report ===\n");
        
        if (bots.isEmpty()) {
            sb.append("No bots registered.\n");
        } else {
            for (Bot bot : bots.values()) {
                sb.append(String.format("- %s: %s", bot.getName(), 
                    bot.isRunning() ? (bot.isPaused() ? "PAUSED" : "RUNNING") : "STOPPED"));
                if (bot.isRunning()) {
                    sb.append(String.format(" (Runtime: %s, Iterations: %d)", 
                        bot.getRuntimeFormatted(), bot.getIterations()));
                }
                sb.append("\n");
            }
        }
        
        if (activeBot != null) {
            sb.append("\nActive Bot: ").append(activeBot.getName());
        }
        
        return sb.toString();
    }
}

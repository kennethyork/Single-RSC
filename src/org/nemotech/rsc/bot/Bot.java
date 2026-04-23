package org.nemotech.rsc.bot;

import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.model.World;
import org.nemotech.rsc.event.DelayedEvent;

/**
 * Abstract base class for all bots.
 * Extend this class to create custom bot scripts.
 * 
 * Example usage:
 * <pre>
 * public class MyBot extends Bot {
 *     public MyBot() {
 *         super("My Bot");
 *     }
 *     
 *     @Override
 *     public int loop() {
 *         // Your bot logic here
 *         return 1000; // Return delay in ms until next loop
 *     }
 * }
 * </pre>
 */
public abstract class Bot {
    
    /** Name of this bot for display purposes */
    protected final String name;
    
    /** Whether the bot is currently running */
    protected boolean running = false;
    
    /** Whether the bot is paused */
    protected boolean paused = false;
    
    /** The bot's main loop event */
    private DelayedEvent loopEvent;
    
    /** Reference to the bot API for convenience methods */
    protected final BotAPI api;
    
    /** Total runtime in milliseconds */
    private long startTime = 0;
    
    /** Loop iteration counter */
    private long iterations = 0;
    
    /**
     * Creates a new bot with the given name.
     * @param name The display name for this bot
     */
    public Bot(String name) {
        this.name = name;
        this.api = BotAPI.getInstance();
    }
    
    /**
     * Called when the bot starts. Override to add startup logic.
     */
    public void onStart() {
        log("Bot started!");
    }
    
    /**
     * Called when the bot stops. Override to add cleanup logic.
     */
    public void onStop() {
        log("Bot stopped!");
    }
    
    /**
     * The main bot loop. This method is called repeatedly while the bot is running.
     * 
     * @return The delay in milliseconds before the next loop iteration.
     *         Return -1 to stop the bot.
     */
    public abstract int loop();
    
    /**
     * Starts the bot.
     */
    public final void start() {
        if (running) {
            log("Bot is already running!");
            return;
        }
        
        running = true;
        paused = false;
        startTime = System.currentTimeMillis();
        iterations = 0;
        
        onStart();
        
        // Create the main loop event
        loopEvent = new DelayedEvent(getPlayer(), 100) {
            @Override
            public void run() {
                if (!running) {
                    stop();
                    return;
                }
                
                Player player = getPlayer();
                if (player == null || !player.isLoggedIn()) {
                    log("Player disconnected or not logged in, stopping bot");
                    stop();
                    return;
                }
                
                if (paused) {
                    return;
                }
                
                try {
                    iterations++;
                    int delay = loop();
                    
                    if (delay < 0) {
                        Bot.this.stop();
                        return;
                    }
                    
                    // Update the delay for next iteration
                    this.setDelay(delay);
                    
                } catch (Exception e) {
                    // Log error but keep running
                    log("Error in bot loop: " + e.getMessage());
                    e.printStackTrace();
                    // Don't stop - just wait and try again
                    this.setDelay(1000);
                }
            }
        };
        
        World.getWorld().getDelayedEventHandler().add(loopEvent);
    }
    
    /**
     * Stops the bot.
     */
    public final void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        if (loopEvent != null) {
            loopEvent.interrupt();
            loopEvent = null;
        }
        
        onStop();
    }
    
    /**
     * Pauses the bot.
     */
    public final void pause() {
        if (!running) {
            return;
        }
        paused = true;
        log("Bot paused");
    }
    
    /**
     * Resumes the bot from a paused state.
     */
    public final void resume() {
        if (!running) {
            return;
        }
        paused = false;
        log("Bot resumed");
    }
    
    /**
     * Toggles the paused state.
     */
    public final void togglePause() {
        if (paused) {
            resume();
        } else {
            pause();
        }
    }
    
    /**
     * @return Whether the bot is currently running
     */
    public final boolean isRunning() {
        return running;
    }
    
    /**
     * @return Whether the bot is currently paused
     */
    public final boolean isPaused() {
        return paused;
    }
    
    /**
     * @return The name of this bot
     */
    public final String getName() {
        return name;
    }
    
    /**
     * @return The total runtime in milliseconds
     */
    public final long getRuntime() {
        if (startTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * @return The formatted runtime string (HH:MM:SS)
     */
    public final String getRuntimeFormatted() {
        long runtime = getRuntime();
        long seconds = (runtime / 1000) % 60;
        long minutes = (runtime / (1000 * 60)) % 60;
        long hours = (runtime / (1000 * 60 * 60));
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    /**
     * @return The number of loop iterations completed
     */
    public final long getIterations() {
        return iterations;
    }
    
    /**
     * Gets the local player.
     * @return The player instance
     */
    protected final Player getPlayer() {
        return World.getWorld().getPlayer();
    }
    
    /**
     * Logs a message to the console and optionally in-game.
     * @param message The message to log
     */
    protected final void log(String message) {
        String formatted = "[" + name + "] " + message;
        System.out.println(formatted);
    }
    
    /**
     * Sends a message in-game to the player.
     * @param message The message to display
     */
    protected final void gameMessage(String message) {
        getPlayer().getSender().sendMessage("@cya@[Bot] @whi@" + message);
    }
    
    /**
     * Sleeps for a random amount of time between min and max milliseconds.
     * Note: This doesn't actually sleep - it returns a delay value for the next loop.
     * @param min Minimum delay
     * @param max Maximum delay
     * @return A random delay between min and max
     */
    protected final int random(int min, int max) {
        return min + (int)(Math.random() * (max - min + 1));
    }
    
    /**
     * Returns a random gaussian-distributed delay centered around the given mean.
     * @param mean The center value
     * @param deviation The standard deviation
     * @return A gaussian-distributed random value
     */
    protected final int gaussian(int mean, int deviation) {
        return (int)(mean + (Math.random() * 2 - 1) * deviation);
    }
}

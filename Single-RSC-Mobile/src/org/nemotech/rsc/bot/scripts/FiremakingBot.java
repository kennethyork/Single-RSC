package org.nemotech.rsc.bot.scripts;

import org.nemotech.rsc.bot.Bot;

/**
 * A firemaking bot that burns logs for XP.
 * 
 * Usage:
 * 1. Have a tinderbox (ID 166) in your inventory
 * 2. Have logs in your inventory
 * 3. Start the bot - it will burn all logs
 * 4. When out of logs, it will bank and withdraw more
 * 
 * Log IDs and Levels:
 * - Normal logs (14): Level 1
 * - Oak logs (632): Level 15 (members)
 * - Willow logs (633): Level 30 (members)
 * - Maple logs (634): Level 45 (members)
 * - Yew logs (635): Level 60 (members)
 * - Magic logs (636): Level 75 (members)
 */
public class FiremakingBot extends Bot {
    
    private static final int TINDERBOX = 166;
    
    // Log IDs (in order of preference)
    private int[] logIds = { 14, 632, 633, 634, 635, 636 };
    
    private enum State {
        IDLE,
        LIGHTING,
        BANKING
    }
    
    private State state = State.IDLE;
    private int logsBurned = 0;
    
    public FiremakingBot() {
        super("Firemaking Bot");
    }
    
    /**
     * Sets the log IDs to burn.
     */
    public void setLogIds(int... ids) {
        this.logIds = ids;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        logsBurned = 0;
        state = State.IDLE;
        
        // Check for tinderbox
        if (api.getInventoryIndex(TINDERBOX) < 0) {
            gameMessage("Warning: No tinderbox found in inventory!");
        }
        
        gameMessage("Firemaking bot started! Will burn logs for XP.");
    }
    
    @Override
    public void onStop() {
        super.onStop();
        if (api.isBankOpen()) {
            api.closeBank();
        }
        gameMessage("Firemaking bot stopped. Total logs burned: " + logsBurned);
    }
    

    private int consecutiveBankFailures = 0;
    private int consecutiveTinderboxFailures = 0;

    @Override
    public int loop() {
        // Don't do anything if busy
        if (api.isBusy() || api.isMoving()) {
            return random(300, 500);
        }

        // Handle lighting state reset
        if (state == State.LIGHTING) {
            state = State.IDLE;
        }

        // Check for tinderbox
        int tinderboxIndex = api.getInventoryIndex(TINDERBOX);
        if (tinderboxIndex < 0) {
            consecutiveTinderboxFailures++;
            if (consecutiveTinderboxFailures > 3) {
                gameMessage("@red@No tinderbox! Stopping bot.");
                return -1;
            }
            return random(2000, 3000);
        }

        consecutiveTinderboxFailures = 0;

        // Find logs to burn
        int logIndex = -1;
        for (int id : logIds) {
            int idx = api.getInventoryIndex(id);
            if (idx >= 0) {
                logIndex = idx;
                break;
            }
        }

        // No logs - go bank
        if (logIndex < 0) {
            state = State.BANKING;
            return handleBanking();
        }

        // Close bank if open
        if (api.isBankOpen()) {
            api.closeBank();
            return random(300, 500);
        }

        // Use tinderbox on logs
        state = State.LIGHTING;
        api.useItemOnItem(tinderboxIndex, logIndex);
        logsBurned++;

        return random(2000, 4000);
    }
    
    private int handleBanking() {
        // Open bank if not already open
        if (!api.isBankOpen()) {
            api.openBank();
            consecutiveBankFailures++;
            if (consecutiveBankFailures > 3) {
                gameMessage("@red@Bank command failed too many times. Stopping bot.");
                return -1;
            }
            return random(800, 1200);
        }

        consecutiveBankFailures = 0;

        // No logs left — stop
        gameMessage("Out of logs! Stopping bot.");
        api.closeBank();
        return -1;
    }
    
    public int getLogsBurned() {
        return logsBurned;
    }
    
    public State getState() {
        return state;
    }
}

package org.nemotech.rsc.bot.scripts;

import org.nemotech.rsc.bot.Bot;

/**
 * A fletching bot that makes bows and arrows from logs.
 * 
 * Usage:
 * 1. Have a knife (ID 13) and logs in your inventory
 * 2. Start the bot - it will cut logs into bow/arrow shafts
 * 3. Banks finished products and withdraws more logs
 * 
 * Log IDs:
 * - Normal logs: 14
 * - Oak logs: 632
 * - Willow logs: 633
 * - Maple logs: 634
 * - Yew logs: 635
 * - Magic logs: 636
 * 
 * Note: Members only skill. Use knife on logs to start.
 */
public class FletchingBot extends Bot {
    
    private static final int KNIFE = 13;
    
    // Logs to fletch (priority order)
    private int[] logIds = { 14, 632, 633, 634, 635, 636 };
    
    private enum State {
        IDLE,
        FLETCHING,
        BANKING
    }
    
    private State state = State.IDLE;
    private int itemsFletched = 0;
    
    public FletchingBot() {
        super("Fletching Bot");
    }
    
    /**
     * Sets specific log IDs to fletch.
     */
    public void setLogIds(int... ids) {
        this.logIds = ids;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        itemsFletched = 0;
        state = State.IDLE;
        
        // Check for knife
        if (api.getInventoryIndex(KNIFE) < 0) {
            gameMessage("Warning: No knife found in inventory!");
        }
        
        gameMessage("Fletching bot started! Will use knife on logs.");
        gameMessage("Select what to make when the menu appears.");
    }
    
    @Override
    public void onStop() {
        super.onStop();
        if (api.isBankOpen()) {
            api.closeBank();
        }
        gameMessage("Fletching bot stopped. Items fletched: " + itemsFletched);
    }
    

    private int consecutiveBankFailures = 0;

    @Override
    public int loop() {
        // Don't do anything if busy
        if (api.isBusy() || api.isMoving()) {
            return random(300, 500);
        }

        // Handle fletching state reset
        if (state == State.FLETCHING) {
            state = State.IDLE;
        }

        // Check for knife
        int knifeIndex = api.getInventoryIndex(KNIFE);
        if (knifeIndex < 0) {
            gameMessage("No knife! Please add one to your inventory.");
            return 5000;
        }

        // Find logs to fletch
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

        // Use knife on logs
        state = State.FLETCHING;
        api.useItemOnItem(knifeIndex, logIndex);
        itemsFletched++;

        return random(2000, 3000);
    }
    
    private int handleBanking() {
        if (!api.isBankOpen()) {
            api.openBank();
            consecutiveBankFailures++;
            if (consecutiveBankFailures > 3) {
                gameMessage("@red@Bank command failed too many times. Stopping bot.");
                return -1;
            }
            return random(800, 1200);
        }

        // Deposit all items (except knife)
        api.depositAll();

        // Keep knife
        if (api.getBankCount(KNIFE) > 0 && api.getInventoryIndex(KNIFE) < 0) {
            api.withdrawItem(KNIFE, 1);
        }

        // Try to withdraw logs
        for (int logId : logIds) {
            int bankCount = api.getBankCount(logId);
            if (bankCount > 0) {
                api.withdrawItem(logId, Math.min(bankCount, 27));
                consecutiveBankFailures = 0;
                api.closeBank();
                state = State.IDLE;
                return random(600, 800);
            }
        }

        gameMessage("Out of logs! Stopping bot.");
        api.closeBank();
        return -1;
    }
    
    public int getItemsFletched() {
        return itemsFletched;
    }
    
    public State getState() {
        return state;
    }
}

package org.nemotech.rsc.bot.scripts;

import org.nemotech.rsc.bot.Bot;

/**
 * A crafting bot that crafts items (leather, pottery, jewelry, etc.)
 * 
 * Supported modes:
 * - LEATHER: Use needle on leather to make armor
 * - SPINNING: Use wool/flax on spinning wheel
 * - POTTERY: Use soft clay on potter's wheel
 * 
 * Usage:
 * 1. Have the required items in your inventory
 * 2. Stand near the crafting station if needed
 * 3. Start the bot with the appropriate mode
 * 
 * Item IDs:
 * - Needle: 39
 * - Leather: 148
 * - Wool: 145
 * - Flax: 675
 * - Soft clay: 243
 */
public class CraftingBot extends Bot {
    
    private static final int NEEDLE = 39;
    private static final int LEATHER = 148;
    private static final int WOOL = 145;
    private static final int FLAX = 675;
    private static final int SOFT_CLAY = 243;
    
    public enum Mode {
        LEATHER,    // Use needle on leather
        SPINNING,   // Use wool/flax on wheel
        POTTERY     // Use clay on wheel
    }
    
    private Mode mode = Mode.LEATHER;
    
    private enum State {
        IDLE,
        CRAFTING,
        BANKING
    }
    
    private State state = State.IDLE;
    private int itemsCrafted = 0;
    
    public CraftingBot() {
        super("Crafting Bot");
    }
    
    public CraftingBot(Mode mode) {
        super("Crafting Bot");
        this.mode = mode;
    }
    
    public void setMode(Mode mode) {
        this.mode = mode;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        itemsCrafted = 0;
        state = State.IDLE;
        gameMessage("Crafting bot started! Mode: " + mode);
        
        switch (mode) {
            case LEATHER:
                if (api.getInventoryIndex(NEEDLE) < 0) {
                    gameMessage("Warning: No needle found! Need needle + leather.");
                }
                break;
            case SPINNING:
                gameMessage("Stand near a spinning wheel with wool or flax.");
                break;
            case POTTERY:
                gameMessage("Stand near a potter's wheel with soft clay.");
                break;
        }
    }
    
    @Override
    public void onStop() {
        super.onStop();
        if (api.isBankOpen()) {
            api.closeBank();
        }
        gameMessage("Crafting bot stopped. Items crafted: " + itemsCrafted);
    }
    

    private int consecutiveBankFailures = 0;

    @Override
    public int loop() {
        // Don't do anything if busy
        if (api.isBusy() || api.isMoving()) {
            return random(300, 500);
        }

        // Handle crafting state reset
        if (state == State.CRAFTING) {
            state = State.IDLE;
        }

        // Close bank if open
        if (api.isBankOpen()) {
            api.closeBank();
            return random(300, 500);
        }

        switch (mode) {
            case LEATHER:
                return craftLeather();
            case SPINNING:
                return spin();
            case POTTERY:
                return doPottery();
            default:
                return random(1000, 2000);
        }
    }
    
    private int craftLeather() {
        int needleIndex = api.getInventoryIndex(NEEDLE);
        if (needleIndex < 0) {
            gameMessage("No needle! Trying to get one from bank.");
            return handleBanking(LEATHER);
        }
        
        int leatherIndex = api.getInventoryIndex(LEATHER);
        if (leatherIndex < 0) {
            state = State.BANKING;
            return handleBanking(LEATHER);
        }
        
        state = State.CRAFTING;
        api.useItemOnItem(needleIndex, leatherIndex);
        itemsCrafted++;
        return random(2000, 3000);
    }
    
    private int spin() {
        // Check for wool or flax
        int materialIndex = api.getInventoryIndex(WOOL);
        
        if (materialIndex < 0) {
            materialIndex = api.getInventoryIndex(FLAX);
        }
        
        if (materialIndex < 0) {
            state = State.BANKING;
            return handleBanking(WOOL);
        }
        
        // Find spinning wheel (object ID 121)
        var wheel = api.getNearestObject(121);
        if (wheel == null) {
            log("No spinning wheel found nearby!");
            return random(2000, 3000);
        }
        
        if (api.distanceTo(wheel) > 1) {
            api.walkTo(wheel.getX(), wheel.getY());
            return random(600, 1000);
        }
        
        state = State.CRAFTING;
        api.useItemOnObject(materialIndex, wheel);
        itemsCrafted++;
        return random(2000, 3000);
    }
    
    private int doPottery() {
        int clayIndex = api.getInventoryIndex(SOFT_CLAY);
        if (clayIndex < 0) {
            state = State.BANKING;
            return handleBanking(SOFT_CLAY);
        }
        
        // Find potter's wheel (object ID 179)
        var wheel = api.getNearestObject(179);
        if (wheel == null) {
            log("No potter's wheel found nearby!");
            return random(2000, 3000);
        }
        
        if (api.distanceTo(wheel) > 1) {
            api.walkTo(wheel.getX(), wheel.getY());
            return random(600, 1000);
        }
        
        state = State.CRAFTING;
        api.useItemOnObject(clayIndex, wheel);
        itemsCrafted++;
        return random(2000, 3000);
    }
    
    private int handleBanking(int itemId) {
        if (!api.isBankOpen()) {
            api.openBank();
            consecutiveBankFailures++;
            if (consecutiveBankFailures > 3) {
                gameMessage("@red@Bank command failed too many times. Stopping bot.");
                return -1;
            }
            return random(800, 1200);
        }

        // Deposit crafted items
        api.depositAll();

        // Keep needle if needed
        if (mode == Mode.LEATHER && api.getBankCount(NEEDLE) > 0) {
            api.withdrawItem(NEEDLE, 1);
        }

        // Withdraw materials
        int bankCount = api.getBankCount(itemId);
        if (bankCount > 0) {
            api.withdrawItem(itemId, Math.min(bankCount, 27));
            consecutiveBankFailures = 0;
            api.closeBank();
            state = State.IDLE;
            return random(600, 800);
        }

        gameMessage("Out of materials! Stopping bot.");
        api.closeBank();
        return -1;
    }
    
    public int getItemsCrafted() {
        return itemsCrafted;
    }
    
    public State getState() {
        return state;
    }
    
    public Mode getMode() {
        return mode;
    }
}

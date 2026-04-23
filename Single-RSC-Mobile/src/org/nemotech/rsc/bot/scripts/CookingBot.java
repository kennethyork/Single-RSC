package org.nemotech.rsc.bot.scripts;

import org.nemotech.rsc.bot.Bot;
import org.nemotech.rsc.model.GameObject;

/**
 * A cooking bot that cooks food on ranges or fires.
 * 
 * Usage:
 * 1. Have raw food in your inventory
 * 2. Stand near a range (object ID 11) or fire (object ID 97)
 * 3. Start the bot - it will cook all raw food
 * 4. When out of food, it will bank (uses ::bank)
 * 
 * Raw Food IDs:
 * - Raw shrimp: 349
 * - Raw anchovies: 351  
 * - Raw herring: 361
 * - Raw sardine: 354
 * - Raw salmon: 356
 * - Raw trout: 358
 * - Raw pike: 363
 * - Raw tuna: 366
 * - Raw lobster: 372
 * - Raw swordfish: 369
 * - Raw shark: 545
 * - Raw beef: 504
 * - Raw chicken: 503
 */
public class CookingBot extends Bot {
    
    // Cooking objects
    private static final int RANGE = 11;
    private static final int FIRE = 97;
    
    // Raw food to cook (ID -> Cooked ID)
    private int[] rawFoodIds = { 349, 351, 361, 354, 356, 358, 363, 366, 372, 369, 545, 504, 503 };
    
    private enum State {
        IDLE,
        COOKING,
        BANKING
    }
    
    private State state = State.IDLE;
    private int foodCooked = 0;
    
    public CookingBot() {
        super("Cooking Bot");
    }
    
    /**
     * Sets the raw food IDs to cook.
     */
    public void setRawFoodIds(int... ids) {
        this.rawFoodIds = ids;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        foodCooked = 0;
        state = State.IDLE;
        gameMessage("Cooking bot started! Will cook raw food on ranges/fires.");
        gameMessage("Raw food IDs: " + arrayToString(rawFoodIds));
    }
    
    @Override
    public void onStop() {
        super.onStop();
        if (api.isBankOpen()) {
            api.closeBank();
        }
        gameMessage("Cooking bot stopped. Total food cooked: " + foodCooked);
    }
    

    private int consecutiveBankFailures = 0;

    @Override
    public int loop() {
        // Don't do anything if busy
        if (api.isBusy() || api.isMoving()) {
            return random(300, 500);
        }

        // Handle cooking state reset
        if (state == State.COOKING) {
            state = State.IDLE;
        }

        // Check if we have raw food
        int rawFoodIndex = -1;
        for (int id : rawFoodIds) {
            int idx = api.getInventoryIndex(id);
            if (idx >= 0) {
                rawFoodIndex = idx;
                break;
            }
        }

        // No raw food - bank cooked food and withdraw more raw food
        if (rawFoodIndex < 0) {
            state = State.BANKING;
            return handleBanking();
        }

        // Close bank if open
        if (api.isBankOpen()) {
            api.closeBank();
            return random(300, 500);
        }

        // Find a range or fire
        GameObject cookingSpot = api.getNearestObject(RANGE);
        if (cookingSpot == null) {
            cookingSpot = api.getNearestObject(FIRE);
        }

        if (cookingSpot == null || cookingSpot.isRemoved()) {
            log("No range or fire found nearby!");
            return random(2000, 3000);
        }

        // Walk to cooking spot if too far
        if (api.distanceTo(cookingSpot) > 1) {
            api.walkTo(cookingSpot.getX(), cookingSpot.getY());
            return random(600, 1000);
        }

        // Use raw food on cooking spot
        state = State.COOKING;
        api.useItemOnObject(rawFoodIndex, cookingSpot);
        foodCooked++;

        return random(2000, 3000);
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

        // Deposit all cooked food (just deposit all for simplicity)
        api.depositAll();

        // Try to withdraw raw food from bank
        for (int rawId : rawFoodIds) {
            int bankCount = api.getBankCount(rawId);
            if (bankCount > 0) {
                api.withdrawItem(rawId, Math.min(bankCount, 28));
                consecutiveBankFailures = 0;
                api.closeBank();
                state = State.IDLE;
                return random(600, 800);
            }
        }

        // No raw food left — stop
        gameMessage("Out of raw food! Stopping bot.");
        api.closeBank();
        return -1;
    }
    
    private String arrayToString(int[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
    
    public int getFoodCooked() {
        return foodCooked;
    }
    
    public State getState() {
        return state;
    }
}

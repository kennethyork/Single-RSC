package org.nemotech.rsc.bot.scripts;

import org.nemotech.rsc.bot.Bot;
import org.nemotech.rsc.model.GameObject;

/**
 * A fishing bot that catches fish and banks them.
 * 
 * Usage:
 * 1. Start the bot near a fishing spot with proper equipment
 * 2. It will automatically find fishing spots and fish
 * 3. When inventory is full, it will bank the fish (uses ::bank)
 * 
 * Fishing Spot Object IDs:
 * - Net/Bait Fishing Spot: 192 (small net/bait)
 * - Lure/Bait Fishing Spot: 193 (fly fishing rod/bait)
 * - Cage/Harpoon Fishing Spot: 194 (lobster pot/harpoon)
 * - Net/Harpoon Fishing Spot: 261 (big net/harpoon for sharks)
 * 
 * Required Equipment Item IDs:
 * - Small Fishing Net: 376
 * - Fishing Rod: 377
 * - Fly Fishing Rod: 378
 * - Harpoon: 379
 * - Lobster Pot: 380
 * - Big Net: 381
 * - Fishing Bait: 382
 * - Feathers: 381
 * 
 * Fish Item IDs (raw):
 * - Raw Shrimp: 349
 * - Raw Anchovies: 351
 * - Raw Sardine: 354
 * - Raw Herring: 361
 * - Raw Trout: 358
 * - Raw Salmon: 356
 * - Raw Tuna: 366
 * - Raw Lobster: 372
 * - Raw Swordfish: 369
 */
public class FishingBot extends Bot {
    
    // Default fishing spot IDs
    private int[] fishingSpotIds = { 192 }; // Net spots
    
    // Fish to bank (raw fish IDs)
    private int[] fishIds = { 349, 351 }; // Shrimp and anchovies
    
    private enum State {
        IDLE,
        WALKING_TO_SPOT,
        FISHING,
        BANKING
    }
    
    private State state = State.IDLE;
    private GameObject targetSpot = null;
    private int fishCaught = 0;
    
    public FishingBot() {
        super("Fishing Bot");
    }
    
    /**
     * Creates a fishing bot with custom fishing spot and fish IDs.
     * @param fishingSpotIds Array of fishing spot object IDs
     * @param fishIds Array of fish item IDs to bank
     */
    public FishingBot(int[] fishingSpotIds, int[] fishIds) {
        super("Fishing Bot");
        this.fishingSpotIds = fishingSpotIds;
        this.fishIds = fishIds;
    }
    
    /**
     * Sets the fishing spot IDs to look for.
     */
    public void setFishingSpotIds(int... ids) {
        this.fishingSpotIds = ids;
    }
    
    /**
     * Sets the fish IDs to bank.
     */
    public void setFishIds(int... ids) {
        this.fishIds = ids;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        fishCaught = 0;
        state = State.IDLE;
        gameMessage("Fishing bot started! Looking for spot IDs: " + arrayToString(fishingSpotIds));
        gameMessage("Fish will be banked when inventory is full.");
    }
    
    @Override
    public void onStop() {
        super.onStop();
        // Close bank if open
        if (api.isBankOpen()) {
            api.closeBank();
        }
        gameMessage("Fishing bot stopped. Total fish banked: " + fishCaught);
    }
    

    private int consecutiveBankFailures = 0;

    @Override
    public int loop() {
        // Don't do anything if busy
        if (api.isBusy() || api.isMoving()) {
            return random(300, 500);
        }

        // Handle fishing state reset - if we were fishing, find a new spot immediately
        if (state == State.FISHING) {
            state = State.IDLE;
            targetSpot = null;
        }

        // Handle banking fish when inventory is full
        if (api.isInventoryFull()) {
            state = State.BANKING;
            targetSpot = null;
            return bankFish();
        }

        // Close bank if it's open and we're not full
        if (api.isBankOpen()) {
            api.closeBank();
            return random(300, 500);
        }

        // Find and use fishing spot
        return fish();
    }
    
    private int fish() {
        // Find nearest fishing spot
        targetSpot = api.getNearestObject(fishingSpotIds);

        if (targetSpot == null || targetSpot.isRemoved()) {
            state = State.IDLE;
            targetSpot = null;
            return searchForSpot();
        }

        // Check if we're close enough to interact
        if (api.distanceTo(targetSpot) > 1) {
            state = State.WALKING_TO_SPOT;
            api.walkTo(targetSpot.getX(), targetSpot.getY());
            return random(600, 1000);
        }

        // Start fishing
        state = State.FISHING;
        api.interactObject(targetSpot);

        return random(2000, 4000);
    }

    private int searchForSpot() {
        int[] offsets = { -25, -20, -15, 15, 20, 25 };
        int newX = api.getX() + offsets[random(0, offsets.length - 1)];
        int newY = api.getY() + offsets[random(0, offsets.length - 1)];
        api.walkTo(newX, newY);
        return random(600, 1000);
    }
    
    private int bankFish() {
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
        
        // Deposit all fish
        for (int fishId : fishIds) {
            int count = api.getInventoryCount(fishId);
            if (count > 0) {
                api.depositItem(fishId, count);
                fishCaught += count;
            }
        }
        
        // Done banking, close bank and continue
        consecutiveBankFailures = 0;
        api.closeBank();
        state = State.IDLE;
        return random(300, 500);
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
    
    /**
     * Gets the total fish caught this session.
     */
    public int getFishCaught() {
        return fishCaught;
    }
    
    /**
     * Gets the current bot state.
     */
    public State getState() {
        return state;
    }
}

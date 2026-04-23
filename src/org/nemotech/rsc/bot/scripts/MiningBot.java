package org.nemotech.rsc.bot.scripts;

import org.nemotech.rsc.bot.Bot;
import org.nemotech.rsc.model.GameObject;

/**
 * A mining bot that mines rocks and banks ore.
 * 
 * Usage:
 * 1. Make sure you have a pickaxe equipped or in inventory
 * 2. Start the bot near mining rocks
 * 3. It will automatically find rocks, mine them, and bank ore (uses ::bank)
 * 
 * Rock Object IDs (vary by location):
 * - Copper rocks: 100, 101
 * - Tin rocks: 104, 105
 * - Iron rocks: 102, 103
 * - Coal rocks: 110, 111
 * - Gold rocks: 112, 113
 * - Mithril rocks: 106, 107
 * - Adamantite rocks: 108, 109
 * - Runite rocks: 210
 * 
 * Ore Item IDs:
 * - Copper ore: 150
 * - Tin ore: 202
 * - Iron ore: 151
 * - Coal: 155
 * - Gold ore: 152
 * - Mithril ore: 153
 * - Adamantite ore: 154
 * - Runite ore: 409
 */
public class MiningBot extends Bot {
    
    // Default rock IDs (copper and tin)
    private int[] rockIds = { 100, 101, 104, 105 };
    
    // Ore to bank
    private int[] oreIds = { 150, 202 };
    
    private enum State {
        IDLE,
        WALKING_TO_ROCK,
        MINING,
        BANKING
    }
    
    private State state = State.IDLE;
    private GameObject targetRock = null;
    private int oresMined = 0;
    
    public MiningBot() {
        super("Mining Bot");
    }
    
    /**
     * Creates a mining bot with custom rock and ore IDs.
     * @param rockIds Array of rock object IDs to mine
     * @param oreIds Array of ore item IDs to bank
     */
    public MiningBot(int[] rockIds, int[] oreIds) {
        super("Mining Bot");
        this.rockIds = rockIds;
        this.oreIds = oreIds;
    }
    
    /**
     * Sets the rock IDs to mine.
     */
    public void setRockIds(int... ids) {
        this.rockIds = ids;
    }
    
    /**
     * Sets the ore IDs to bank.
     */
    public void setOreIds(int... ids) {
        this.oreIds = ids;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        oresMined = 0;
        state = State.IDLE;
        gameMessage("Mining bot started! Mining rock IDs: " + arrayToString(rockIds));
        gameMessage("Ores will be banked when inventory is full.");
    }
    
    @Override
    public void onStop() {
        super.onStop();
        // Close bank if open
        if (api.isBankOpen()) {
            api.closeBank();
        }
        gameMessage("Mining bot stopped. Total ores banked: " + oresMined);
    }
    

    private int consecutiveBankFailures = 0;

    @Override
    public int loop() {
        // Don't do anything if busy
        if (api.isBusy() || api.isMoving()) {
            return random(300, 500);
        }

        // Handle mining state reset - if we were mining, find a new rock immediately
        if (state == State.MINING) {
            state = State.IDLE;
            targetRock = null;
        }

        // Handle banking ores when inventory is full
        if (api.isInventoryFull()) {
            state = State.BANKING;
            targetRock = null;
            return bankOres();
        }

        // Close bank if it's open and we're not full
        if (api.isBankOpen()) {
            api.closeBank();
            return random(300, 500);
        }

        // Find and mine a rock
        return mineRock();
    }
    
    private int mineRock() {
        // Find nearest rock
        targetRock = api.getNearestObject(rockIds);

        if (targetRock == null || targetRock.isRemoved()) {
            state = State.IDLE;
            targetRock = null;
            return searchForRock();
        }

        // Check if we're close enough to interact
        if (api.distanceTo(targetRock) > 1) {
            state = State.WALKING_TO_ROCK;
            api.walkTo(targetRock.getX(), targetRock.getY());
            return random(600, 1000);
        }

        // Mine the rock
        state = State.MINING;
        api.interactObject(targetRock);

        return random(2000, 4000);
    }

    private int searchForRock() {
        int[] offsets = { -25, -20, -15, 15, 20, 25 };
        int newX = api.getX() + offsets[random(0, offsets.length - 1)];
        int newY = api.getY() + offsets[random(0, offsets.length - 1)];
        api.walkTo(newX, newY);
        return random(600, 1000);
    }
    
    private int bankOres() {
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
        
        // Deposit all ores
        for (int oreId : oreIds) {
            int count = api.getInventoryCount(oreId);
            if (count > 0) {
                api.depositItem(oreId, count);
                oresMined += count;
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
     * Gets the total ores mined this session.
     */
    public int getOresMined() {
        return oresMined;
    }
    
    /**
     * Gets the current bot state.
     */
    public State getState() {
        return state;
    }
}

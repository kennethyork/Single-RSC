package org.nemotech.rsc.bot.scripts;

import org.nemotech.rsc.bot.Bot;
import org.nemotech.rsc.model.NPC;

/**
 * A thieving bot that pickpockets NPCs for XP and loot.
 * 
 * Usage:
 * 1. Stand near pickpocketable NPCs
 * 2. Start the bot with the target NPC ID
 * 3. It will automatically pickpocket and bank loot
 * 
 * Pickpocketable NPCs and Levels:
 * - Man (ID varies): Level 1
 * - Farmer (ID varies): Level 10
 * - Warrior (ID varies): Level 25
 * - Rogue: Level 32
 * - Guard: Level 40
 * - Knight: Level 55
 * - Watchman: Level 65
 * - Paladin: Level 70
 * - Hero: Level 80
 */
public class ThievingBot extends Bot {
    
    // Default target: Man NPCs
    private int[] targetNpcIds = { 11 }; // Man NPC ID
    
    private enum State {
        IDLE,
        WALKING_TO_NPC,
        PICKPOCKETING,
        BANKING
    }
    
    private State state = State.IDLE;
    private int successfulPickpockets = 0;
    private int failedPickpockets = 0;
    
    public ThievingBot() {
        super("Thieving Bot");
    }
    
    /**
     * Creates a thieving bot targeting specific NPC IDs.
     */
    public ThievingBot(int... npcIds) {
        super("Thieving Bot");
        this.targetNpcIds = npcIds;
    }
    
    /**
     * Sets the NPC IDs to pickpocket.
     */
    public void setTargetNpcIds(int... ids) {
        this.targetNpcIds = ids;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        successfulPickpockets = 0;
        failedPickpockets = 0;
        state = State.IDLE;
        gameMessage("Thieving bot started! Targeting NPCs: " + arrayToString(targetNpcIds));
        gameMessage("Loot will be banked when inventory is full.");
    }
    
    @Override
    public void onStop() {
        super.onStop();
        if (api.isBankOpen()) {
            api.closeBank();
        }
        gameMessage("Thieving bot stopped. Successful: " + successfulPickpockets + ", Failed: " + failedPickpockets);
    }
    

    private int emptyNpcSearchCount = 0;
    private int consecutiveBankFailures = 0;

    @Override
    public int loop() {
        // Don't do anything if busy or in combat
        if (api.isBusy() || api.isMoving()) {
            return random(300, 500);
        }

        // Check if in combat (got caught)
        if (api.isInCombat()) {
            failedPickpockets++;
            state = State.IDLE;
            return random(1000, 2000); // Wait for combat to end
        }

        // Handle pickpocket state reset
        if (state == State.PICKPOCKETING) {
            state = State.IDLE;
        }

        // Bank if inventory is full
        if (api.isInventoryFull()) {
            state = State.BANKING;
            return handleBanking();
        }

        // Close bank if open
        if (api.isBankOpen()) {
            api.closeBank();
            return random(300, 500);
        }

        // Find target NPC
        return pickpocketNpc();
    }
    
    private int pickpocketNpc() {
        // Find nearest target NPC
        NPC target = null;
        int nearestDist = Integer.MAX_VALUE;
        
        for (int npcId : targetNpcIds) {
            NPC npc = api.getNearestNPC(npcId);
            if (npc != null && !npc.isRemoved()) {
                int dist = api.distanceTo(npc);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    target = npc;
                }
            }
        }
        
        if (target == null) {
            state = State.IDLE;
            emptyNpcSearchCount++;
            
            if (emptyNpcSearchCount > 5) {
                gameMessage("No target NPCs found nearby, searching...");
                emptyNpcSearchCount = 0;
            }
            return random(500, 1500);
        }
        
        emptyNpcSearchCount = 0;
        
        // Walk to NPC if too far
        if (nearestDist > 1) {
            state = State.WALKING_TO_NPC;
            api.walkTo(target.getX(), target.getY());
            return random(600, 1000);
        }
        
        // Pickpocket the NPC
        state = State.PICKPOCKETING;
        api.thieveNPC(target);
        successfulPickpockets++;
        
        return random(1500, 2500);
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
        
        // Deposit all items
        api.depositAll();
        
        // Close bank and continue
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
    
    public int getSuccessfulPickpockets() {
        return successfulPickpockets;
    }
    
    public int getFailedPickpockets() {
        return failedPickpockets;
    }
    
    public State getState() {
        return state;
    }
}

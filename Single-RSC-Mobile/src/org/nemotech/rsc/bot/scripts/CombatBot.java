package org.nemotech.rsc.bot.scripts;

import org.nemotech.rsc.bot.Bot;
import org.nemotech.rsc.model.NPC;
import org.nemotech.rsc.model.Item;

/**
 * A combat bot that attacks NPCs and optionally loots drops.
 * 
 * Usage:
 * 1. Configure the NPC IDs to attack
 * 2. Optionally configure items to loot and food to eat
 * 3. Start the bot near the NPCs
 * 
 * Common NPC IDs:
 * - Chicken: 3
 * - Cow: 6
 * - Goblin: 62
 * - Giant Spider: 89
 * - Skeleton: 45
 * - Zombie: 68
 * - Hill Giant: 61
 * - Moss Giant: 104
 * - Fire Giant: 344
 * - Lesser Demon: 82
 * - Greater Demon: 87
 * 
 * Food Item IDs:
 * - Cooked Meat: 132
 * - Bread: 138
 * - Cooked Chicken: 53
 * - Trout: 359
 * - Salmon: 357
 * - Lobster: 373
 * - Swordfish: 370
 * - Shark: 546
 */
public class CombatBot extends Bot {
    
    // NPCs to attack
    private int[] npcIds = { 3 }; // Default: chickens
    
    // Items to loot (empty = don't loot)
    private int[] lootIds = { };
    
    // Food to eat
    private int[] foodIds = { };
    
    // Health percentage to eat at
    private int eatAtPercent = 50;
    
    // Combat style (0=Controlled, 1=Aggressive, 2=Accurate, 3=Defensive)
    private int combatStyle = 0;
    
    private enum State {
        IDLE,
        WALKING_TO_NPC,
        FIGHTING,
        LOOTING,
        EATING
    }
    
    private State state = State.IDLE;
    private NPC targetNpc = null;
    private int killCount = 0;
    
    public CombatBot() {
        super("Combat Bot");
    }
    
    /**
     * Creates a combat bot with custom NPC IDs.
     * @param npcIds Array of NPC IDs to attack
     */
    public CombatBot(int[] npcIds) {
        super("Combat Bot");
        this.npcIds = npcIds;
    }
    
    /**
     * Sets the NPC IDs to attack.
     */
    public void setNpcIds(int... ids) {
        this.npcIds = ids;
    }
    
    /**
     * Sets the item IDs to loot.
     */
    public void setLootIds(int... ids) {
        this.lootIds = ids;
    }
    
    /**
     * Sets the food item IDs to eat.
     */
    public void setFoodIds(int... ids) {
        this.foodIds = ids;
    }
    
    /**
     * Sets the health percentage to eat at.
     */
    public void setEatAtPercent(int percent) {
        this.eatAtPercent = percent;
    }
    
    /**
     * Sets the combat style.
     * @param style 0=Controlled, 1=Aggressive, 2=Accurate, 3=Defensive
     */
    public void setCombatStyle(int style) {
        this.combatStyle = style;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        killCount = 0;
        state = State.IDLE;
        
        // Set combat style
        api.setCombatStyle(combatStyle);
        
        String styleNames[] = {"Controlled", "Aggressive", "Accurate", "Defensive"};
        gameMessage("Combat bot started! Attacking NPC IDs: " + arrayToString(npcIds));
        gameMessage("Combat style: " + styleNames[combatStyle]);
    }
    
    @Override
    public void onStop() {
        super.onStop();
        gameMessage("Combat bot stopped. Total kills: " + killCount);
    }
    
    @Override
    public int loop() {
        // Check if we need to eat
        if (shouldEat()) {
            return eat();
        }
        
        // If we're in combat, wait
        if (api.inCombat()) {
            state = State.FIGHTING;
            return random(500, 1000);
        }
        
        // Don't do anything if busy
        if (api.isBusy() || api.isMoving()) {
            return random(300, 500);
        }
        
        // Try to loot if we have loot configured
        if (lootIds.length > 0 && !api.isInventoryFull()) {
            Item loot = api.getNearestGroundItem(lootIds);
            if (loot != null && api.distanceTo(loot) <= 5) {
                state = State.LOOTING;
                api.pickupItem(loot);
                return random(600, 1000);
            }
        }
        
        // Find and attack an NPC
        return attackNpc();
    }
    
    private boolean shouldEat() {
        if (foodIds.length == 0) return false;
        
        int currentHp = api.getCurrentLevel(3); // Hits skill
        int maxHp = api.getLevel(3);
        int hpPercent = (currentHp * 100) / maxHp;
        
        return hpPercent <= eatAtPercent && hasFood();
    }
    
    private boolean hasFood() {
        for (int foodId : foodIds) {
            if (api.hasItem(foodId)) {
                return true;
            }
        }
        return false;
    }
    
    private int eat() {
        state = State.EATING;
        
        for (int foodId : foodIds) {
            int index = api.getInventoryIndex(foodId);
            if (index >= 0) {
                api.useItem(index);
                log("Eating food");
                return random(1200, 1800);
            }
        }
        
        return random(300, 500);
    }
    
    private int attackNpc() {
        // Find nearest attackable NPC
        targetNpc = api.getNearestAttackableNpc(npcIds);
        
        if (targetNpc == null) {
            log("No NPCs found nearby!");
            state = State.IDLE;
            return random(1000, 2000);
        }
        
        // Check if we're close enough to attack
        if (api.distanceTo(targetNpc) > 1) {
            state = State.WALKING_TO_NPC;
            api.walkTo(targetNpc.getX(), targetNpc.getY());
            return random(400, 800);
        }
        
        // Attack the NPC
        state = State.FIGHTING;
        api.attackNpc(targetNpc);
        killCount++; // Increment on attack (might not always result in kill)
        
        return random(600, 1000);
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
     * Gets the total kills this session.
     */
    public int getKillCount() {
        return killCount;
    }
    
    /**
     * Gets the current bot state.
     */
    public State getState() {
        return state;
    }
}

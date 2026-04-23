package org.nemotech.rsc.bot.scripts;

import org.nemotech.rsc.bot.Bot;
import org.nemotech.rsc.model.NPC;

/**
 * A magic bot that casts combat spells on NPCs for XP.
 * 
 * Usage:
 * 1. Have the required runes in your inventory
 * 2. Be near the NPCs you want to attack
 * 3. Start the bot - it will cast spells on NPCs
 * 
 * Common Rune IDs:
 * - Air rune: 33
 * - Water rune: 32
 * - Earth rune: 34
 * - Fire rune: 31
 * - Mind rune: 35
 * - Body rune: 36
 * - Chaos rune: 41
 * - Death rune: 38
 * - Nature rune: 40
 * - Law rune: 42
 * 
 * Note: This bot uses the attack spell action. Make sure you have runes!
 */
public class MagicBot extends Bot {
    
    // Default targets: Chickens
    private int[] targetNpcIds = { 3 };
    
    // Food IDs to eat when low HP
    private int[] foodIds = { 132, 138, 53, 359, 373, 370 };
    private int eatAtPercent = 50;
    
    private enum State {
        IDLE,
        WALKING_TO_NPC,
        CASTING,
        EATING,
        BANKING
    }
    
    private State state = State.IDLE;
    private int spellsCast = 0;
    
    public MagicBot() {
        super("Magic Bot");
    }
    
    public MagicBot(int... npcIds) {
        super("Magic Bot");
        this.targetNpcIds = npcIds;
    }
    
    public void setTargetNpcIds(int... ids) {
        this.targetNpcIds = ids;
    }
    
    public void setFoodIds(int... ids) {
        this.foodIds = ids;
    }
    
    public void setEatAtPercent(int percent) {
        this.eatAtPercent = percent;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        spellsCast = 0;
        state = State.IDLE;
        gameMessage("Magic bot started! Make sure you have runes for combat spells.");
        gameMessage("Targeting NPCs: " + arrayToString(targetNpcIds));
    }
    
    @Override
    public void onStop() {
        super.onStop();
        if (api.isBankOpen()) {
            api.closeBank();
        }
        gameMessage("Magic bot stopped. Total spells cast: " + spellsCast);
    }
    

    private int emptyNpcSearchCount = 0;
    private int consecutiveBankFailures = 0;

    @Override
    public int loop() {
        // Check HP and eat if needed
        int hpPercent = (api.getCurrentLevel(3) * 100) / api.getLevel(3);
        if (hpPercent <= eatAtPercent) {
            state = State.EATING;
            return eat();
        }

        // Already in combat - wait
        if (api.inCombat()) {
            return random(500, 1000);
        }

        // Handle casting state reset
        if (state == State.CASTING) {
            state = State.IDLE;
        }

        // Don't do anything if busy
        if (api.isBusy() || api.isMoving()) {
            return random(300, 500);
        }

        // Close bank if open
        if (api.isBankOpen()) {
            api.closeBank();
            return random(300, 500);
        }

        // Find and attack an NPC with magic
        return castOnNpc();
    }
    
    private int eat() {
        for (int foodId : foodIds) {
            int index = api.getInventoryIndex(foodId);
            if (index >= 0) {
                api.useItem(index);
                return random(600, 800);
            }
        }
        
        // No food - bank
        state = State.BANKING;
        return handleBanking();
    }
    
    private int castOnNpc() {
        NPC target = api.getNearestAttackableNpc(targetNpcIds);

        if (target == null || target.isRemoved()) {
            state = State.IDLE;
            emptyNpcSearchCount++;

            if (emptyNpcSearchCount > 5) {
                gameMessage("No targets found nearby, searching...");
                emptyNpcSearchCount = 0;
            }
            return random(500, 1500);
        }

        emptyNpcSearchCount = 0;

        // Walk closer if too far
        if (api.distanceTo(target) > 5) {
            state = State.WALKING_TO_NPC;
            api.walkTo(target.getX(), target.getY());
            return random(600, 1000);
        }

        // Attack the NPC (will use magic if selected in combat style)
        state = State.CASTING;
        api.attackNpc(target);
        spellsCast++;

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

        // Try to withdraw food
        for (int foodId : foodIds) {
            int bankCount = api.getBankCount(foodId);
            if (bankCount > 0) {
                api.withdrawItem(foodId, Math.min(bankCount, 10));
                consecutiveBankFailures = 0;
                api.closeBank();
                state = State.IDLE;
                return random(600, 800);
            }
        }

        gameMessage("Out of food! Stopping bot.");
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
    
    public int getSpellsCast() {
        return spellsCast;
    }
    
    public State getState() {
        return state;
    }
}

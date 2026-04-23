package org.nemotech.rsc.bot.scripts;

import org.nemotech.rsc.bot.Bot;

/**
 * A prayer bot that buries bones for XP.
 * 
 * Usage:
 * 1. Have bones in your inventory
 * 2. Start the bot - it will bury all bones
 * 3. When out of bones, it will bank and try to withdraw more
 * 
 * Bone IDs and XP:
 * - Bones (20): 4.5 XP
 * - Bat bones (604): 4.5 XP
 * - Big bones (413): 15 XP
 * - Dragon bones (814): 60 XP
 */
public class PrayerBot extends Bot {
    
    // Bone item IDs
    private static final int BONES = 20;
    private static final int BAT_BONES = 604;
    private static final int BIG_BONES = 413;
    private static final int DRAGON_BONES = 814;
    
    private int[] boneIds = { BONES, BAT_BONES, BIG_BONES, DRAGON_BONES };
    
    private enum State {
        IDLE,
        BURYING,
        BANKING
    }
    
    private State state = State.IDLE;
    private int bonesBuried = 0;
    
    public PrayerBot() {
        super("Prayer Bot");
    }
    
    /**
     * Sets specific bone IDs to bury.
     */
    public void setBoneIds(int... ids) {
        this.boneIds = ids;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        bonesBuried = 0;
        state = State.IDLE;
        gameMessage("Prayer bot started! Will bury bones for XP.");
    }
    
    @Override
    public void onStop() {
        super.onStop();
        if (api.isBankOpen()) {
            api.closeBank();
        }
        gameMessage("Prayer bot stopped. Total bones buried: " + bonesBuried);
    }
    

    private int consecutiveBankFailures = 0;

    @Override
    public int loop() {
        // Don't do anything if busy
        if (api.isBusy() || api.isMoving()) {
            return random(300, 500);
        }

        // Handle burying state reset
        if (state == State.BURYING) {
            state = State.IDLE;
        }

        // Find bones to bury
        int boneIndex = -1;
        for (int id : boneIds) {
            int idx = api.getInventoryIndex(id);
            if (idx >= 0) {
                boneIndex = idx;
                break;
            }
        }

        // No bones - go bank
        if (boneIndex < 0) {
            state = State.BANKING;
            return handleBanking();
        }

        // Close bank if open
        if (api.isBankOpen()) {
            api.closeBank();
            return random(300, 500);
        }

        // Bury the bones (use item action)
        state = State.BURYING;
        api.useItem(boneIndex);
        bonesBuried++;

        return random(600, 1000);
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

        // Try to withdraw bones
        for (int boneId : boneIds) {
            int bankCount = api.getBankCount(boneId);
            if (bankCount > 0) {
                api.withdrawItem(boneId, Math.min(bankCount, 28));
                consecutiveBankFailures = 0;
                api.closeBank();
                state = State.IDLE;
                return random(600, 800);
            }
        }

        gameMessage("Out of bones! Stopping bot.");
        api.closeBank();
        return -1;
    }
    
    public int getBonesBuried() {
        return bonesBuried;
    }
    
    public State getState() {
        return state;
    }
}

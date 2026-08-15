package org.nemotech.rsc.client.update.impl;

import org.nemotech.rsc.client.sound.SoundEffect;
import org.nemotech.rsc.model.player.Player;

/** Suppresses client UI/network effects for autonomous Player entities. */
public final class HeadlessMiscUpdater extends MiscUpdater {

    public HeadlessMiscUpdater(Player player) {
        super(player);
    }

    @Override public void sendEnterSleep() {}
    @Override public void sendWakeUp(boolean success, boolean silent) {}
    @Override public void sendAlert(String message, boolean big) {}
    @Override public void sendLogout() {}
    @Override public void sendStat(int stat) {}
    @Override public void sendSound(SoundEffect sound) {}
    @Override public void sendMessage(String message) {}
    @Override public void sendTempFatigue(int fatigue) {}
    @Override public void sendFatigue(int fatigue) {}
    @Override public void sendQuestInfo(int id, int stage) {}
    @Override public void sendQuestInfo() {}
    @Override public void sendQuestPoints() {}
    @Override public void sendMenu(String[] options) {
        if (player.getMenuHandler() == null || options == null || options.length == 0) return;
        int choice = 0;
        for (int i = 0; i < options.length; i++) {
            String option = options[i] == null ? "" : options[i].toLowerCase();
            if (option.contains("all")) choice = i;
        }
        player.getMenuHandler().handleReply(choice, options[choice]);
    }
    @Override public void hideMenu() {}
    @Override public void sendPrayers() {}
    @Override public void sendWorldInfo() {}
    @Override public void sendInventory() {}
    @Override public void sendEquipmentStats() {}
    @Override public void sendStats() {}
    @Override public void sendGameSettings() {}
    @Override public void sendCombatStyle() {}
    @Override public void sendAppearanceScreen() {}
    @Override public void sendLoginBox() {}
    @Override public void sendDied() {}
    @Override public void sendItemBubble(int id) {}
    @Override public void sendTeleBubble(int x, int y, boolean grab) {}
    @Override public void sendScreenshot() {}
    @Override public void sendUpdateItem(int slot) {}
    @Override public void showBank() {}
    @Override public void updateBankItem(int slot, int id, int amount) {}
    @Override public void sendCantLogout() {}
}

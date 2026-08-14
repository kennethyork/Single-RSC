package org.nemotech.rsc.client.update.impl;

import org.nemotech.rsc.model.Projectile;
import org.nemotech.rsc.model.ChatMessage;
import org.nemotech.rsc.model.NPC;
import org.nemotech.rsc.model.Entity;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.model.player.Appearance;
import org.nemotech.rsc.bot.WorldBotManager;
import org.nemotech.rsc.client.Mob;
import org.nemotech.rsc.client.update.Updater;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerUpdater extends Updater {

    private static final int WORLD_BOT_RENDER_RADIUS = 48;

    private final Map<Integer, Integer> displayedWorldBotMessages = new HashMap<>();
    
    @Override
    public void handlePositionUpdate(Player player) {
        if(player.getX() < 0 || player.getY() < 0) {
            return;
        }
        //if(mc.loadingArea) return;
        mc.knownPlayerCount = mc.playerCount;
        System.arraycopy(mc.players, 0, mc.knownPlayers, 0, mc.knownPlayerCount);
        mc.localRegionX = player.getX();
        mc.localRegionY = player.getY();
        int anim = player.getSprite();
        boolean flag1 = mc.loadNextRegion(mc.localRegionX, mc.localRegionY);
        mc.localRegionX -= mc.regionX;
        mc.localRegionY -= mc.regionY;
        int x = mc.localRegionX * mc.magicLoc + 64;
        int y = mc.localRegionY * mc.magicLoc + 64;
        if (flag1 && mc.players[1] != null) {
            mc.players[1].waypointCurrent = 0;
            mc.players[1].movingStep = 0;
            mc.players[1].currentX = mc.localPlayer.waypointsX[0] = x;
            mc.players[1].currentY = mc.localPlayer.waypointsY[0] = y;
        }
        mc.playerCount = 0;
        mc.localPlayer = mc.createPlayer(1, x, y, anim);
        addWorldBotsAsPlayers(player);
    }

    private void addWorldBotsAsPlayers(Player player) {
        List<WorldBotManager.Snapshot> bots = WorldBotManager.getInstance().getSnapshotsNear(player.getLocation(), WORLD_BOT_RENDER_RADIUS);
        for (WorldBotManager.Snapshot bot : bots) {
            int x = (bot.x - mc.regionX) * mc.magicLoc + 64;
            int y = (bot.y - mc.regionY) * mc.magicLoc + 64;
            if (x <= 0 || y <= 0) {
                continue;
            }
            Mob character = mc.createPlayer(bot.serverIndex, x, y, bot.sprite);
            if (character == null) {
                continue;
            }
            character.serverId = bot.serverIndex;
            character.name = bot.name;
            character.level = bot.combatLevel;
            character.skullVisible = bot.skulled ? 1 : 0;
            character.colourHair = bot.hairColour;
            character.colourTop = bot.topColour;
            character.colourBottom = bot.bottomColour;
            character.colourSkin = bot.skinColour;
            if (bot.message != null) {
                character.message = bot.message;
                character.messageTimeout = 150;
                Integer lastSequence = displayedWorldBotMessages.get(bot.serverIndex);
                if (lastSequence == null || lastSequence.intValue() != bot.messageSequence) {
                    mc.showMessage("@whi@" + bot.name + ": " + bot.message, 3);
                    displayedWorldBotMessages.put(bot.serverIndex, bot.messageSequence);
                }
            }
            for (int i = 0; i < character.equippedItem.length; i++) {
                character.equippedItem[i] = i < bot.equipment.length ? bot.equipment[i] : 0;
            }
            if (bot.bubbleItem >= 0) {
                character.bubbleItem = bot.bubbleItem;
                character.bubbleTimeout = 30;
            }
        }
    }
    
    @Override
    public void handleGraphicsUpdate(Player player) {
        List<ChatMessage> chatMessagesNeedingDisplayed = player.getChatMessagesNeedingDisplayed();
        List<Player> playersNeedingHitsUpdate = player.getPlayersRequiringHitsUpdate();
        List<Projectile> projectilesNeedingDisplayed = player.getProjectilesNeedingDisplayed();
        List<Player> playersNeedingAppearanceUpdate = player.getPlayersRequiringAppearanceUpdate();
        
        int updateSize = chatMessagesNeedingDisplayed.size() + playersNeedingHitsUpdate.size() +  projectilesNeedingDisplayed.size() + playersNeedingAppearanceUpdate.size();
        if(updateSize > 0) {
            Mob character = mc.localPlayer;
            for(ChatMessage cm : chatMessagesNeedingDisplayed) {
                character.messageTimeout = 150;
                character.message = cm.getMessage();
                int tab = 3;
                if(character.message.endsWith("@que@")) {
                    tab = 5;
                }
                mc.showMessage("@whi@" + character.name + ": " + character.message, tab);
            }
            for(Player p : playersNeedingHitsUpdate) {
                character.damageTaken = p.getLastDamage();
                character.healthCurrent = p.getCurStat(3);
                character.healthMax = p.getMaxStat(3);
                character.combatTimer = 200;
                mc.playerStatCurrent[3] = p.getCurStat(3);
                mc.playerStatBase[3] = p.getMaxStat(3);
                mc.showDialogWelcome = false;
                mc.showDialogServerMessage = false;
            }
            for(Projectile p : projectilesNeedingDisplayed) {
                Entity victim = p.getVictim();
                if(victim instanceof NPC) {
                    character.incomingProjectileSprite = p.getType();
                    character.attackingNpcServerIndex = ((NPC) victim).getIndex();
                    character.attackingPlayerServerIndex = -1;
                    character.projectileRange = mc.projectileMaxRange;
                } else if(victim instanceof Player) {
                    character.incomingProjectileSprite = p.getType();
                    character.attackingNpcServerIndex = -1;
                    character.attackingPlayerServerIndex = ((Player) victim).getIndex();
                    character.projectileRange = mc.projectileMaxRange;
                }
            }
            for(Player p : playersNeedingAppearanceUpdate) {
                Appearance appearance = p.getAppearance();
                character.serverId = p.getIndex();
                character.name = p.getUsername();
                for (int i = 0; i < p.getWornItems().length; i++) {
                    character.equippedItem[i] = p.getWornItems()[i];
                }

                for (int i = p.getWornItems().length; i < 12; i++) {
                    character.equippedItem[i] = 0;
                }
                character.colourHair = appearance.getHairColor();
                character.colourTop = appearance.getShirtColor();
                character.colourBottom = appearance.getPantsColor();
                character.colourSkin = appearance.getSkinColor();
                character.level = p.getCombatLevel();
                character.skullVisible = p.isSkulled() ? 1 : 0;
            }
        }
    }
    
}

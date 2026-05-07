package org.nemotech.rsc.plugins.default_;

import org.nemotech.rsc.event.ShortEvent;
import org.nemotech.rsc.model.GameObject;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.plugins.Plugin;
import org.nemotech.rsc.plugins.listeners.action.ObjectActionListener;
import org.nemotech.rsc.plugins.listeners.executive.ObjectActionExecutiveListener;

/**
 * Implements general object interactions for RSC.
 * Handles: signs, ladders, gates, stairs, chests, gates, and other common objects
 * that don't require quest-specific logic.
 */
public class GeneralObjects extends Plugin implements ObjectActionListener, ObjectActionExecutiveListener {

    @Override
    public boolean blockObjectAction(GameObject obj, String command, Player player) {
        String name = obj.getGameObjectDef().getName().toLowerCase();
        int id = obj.getID();
        
        if (name.contains("sign") || name.contains("signpost")) {
            return true;
        }
        if (name.contains("gate")) {
            return true;
        }
        if (name.contains("ladder")) {
            return true;
        }
        if (name.contains("stair")) {
            return true;
        }
        if (name.contains("chest")) {
            return true;
        }
        if (name.contains("door")) {
            return true;
        }
        
        switch (id) {
            case 7: case 159: case 250: case 271: case 295:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onObjectAction(GameObject obj, String command, Player player) {
        int id = obj.getID();
        String name = obj.getGameObjectDef().getName().toLowerCase();
        
        if (name.contains("sign") || name.contains("signpost")) {
            handleSign(obj, player);
            return;
        }
        
        if (name.contains("ladder")) {
            handleLadder(obj, player);
            return;
        }
        
        if (name.contains("stair")) {
            handleStairs(obj, player);
            return;
        }
        
        if (name.contains("chest")) {
            handleChest(obj, player);
            return;
        }
        
        if (name.contains("gate")) {
            handleGate(obj, player);
            return;
        }
        
        switch (id) {
            case 7: handleLadder(obj, player); break;
            case 159: handleLadder(obj, player); break;
            case 250: handleLadder(obj, player); break;
            case 271: handleLadder(obj, player); break;
            default:
                player.message("Nothing interesting happens");
        }
    }

    private void handleSign(GameObject obj, Player player) {
        int id = obj.getID();
        int x = obj.getX();
        int y = obj.getY();
        
        switch (id) {
            case 62:
                if (x == 212 && y == 609) {
                    player.message("A roleplay arena sign");
                    message(player, "The Sign of the Node is a meeting place for", "players who enjoy social interaction and roleplay.");
                    return;
                }
                if (x == 208 && y == 609) {
                    player.message("A roleplay arena sign");
                    return;
                }
                if (x == 126 && y == 686) {
                    player.message("A notice board");
                    return;
                }
                if (x == 132 && y == 536) {
                    player.message("A sign on a fence");
                    return;
                }
                player.message("A signpost");
                player.message("It has no text");
                return;
                
            case 66:
                if (x == 93 && y == 575) {
                    player.message("An old signpost");
                    message(player, "Warning: Wilderness - Never trade with strangers!");
                    return;
                }
                if (x == 126 && y == 686) {
                    player.message("A notice board");
                    return;
                }
                if (x == 132 && y == 536) {
                    player.message("A sign on a fence");
                    return;
                }
                if (x == 208 && y == 609) {
                    player.message("A roleplay arena sign");
                    return;
                }
                player.message("An old signpost");
                return;
                
            case 74:
                if (x == 165 && y == 604) {
                    player.message("A sign for the woodcutting guild");
                    return;
                }
                if (x == 182 && y == 482) {
                    player.message("A sign");
                    message(player, "Varrock - The Kingdom's Capital");
                    return;
                }
                if (x == 372 && y == 441) {
                    player.message("A sign on a fence");
                    return;
                }
                if (x == 565 && y == 537) {
                    player.message("A sign");
                    message(player, "The Dark Knights fortress lies to the north.");
                    return;
                }
                player.message("A sign");
                return;
                
            case 134:
                if (x == 230 && y == 552) {
                    player.message("A sign for the champions guild");
                    return;
                }
                player.message("A signpost");
                return;
                
            case 160:
                if (x == 257 && y == 630) {
                    player.message("A sign");
                    message(player, "Lumbridge Swamp - Beware of deadly creatures!");
                    return;
                }
                if (x == 415 && y == 2051) {
                    player.message("A sign");
                    message(player, "Karamja Volcano - Extreme danger!");
                    return;
                }
                if (x == 702 && y == 2338) {
                    player.message("A sign");
                    message(player, "Wilderness warning: You will lose items on death!");
                    return;
                }
                player.message("A sign");
                return;
                
            case 190:
                if (x == 276 && y == 632) {
                    player.message("A sign");
                    message(player, "Lumbridge castle dungeons are below.");
                    return;
                }
                if (x == 627 && y == 3563) {
                    player.message("A sign");
                    message(player, "Mos Le'Harmless - Take boat here.");
                    return;
                }
                player.message("A sign");
                return;
                
            case 191:
                if (x >= 96 && x <= 600 && y >= 588 && y <= 600) {
                    player.message("A street sign");
                    return;
                }
                player.message("A sign");
                return;
                
            case 210:
                player.message("A warning sign");
                message(player, "Danger! Extreme volcanic activity ahead!");
                return;
                
            case 251:
                player.message("A sign");
                return;
                
            case 252:
                player.message("A sign");
                return;
                
            case 268:
                player.message("A sign");
                return;
                
            case 289:
                player.message("A sign");
                message(player, "Draynor Village - Do not feed the zombies!");
                return;
                
            case 298:
                player.message("A sign");
                return;
                
            case 299:
                player.message("A signpost");
                return;
                
            default:
                player.message("A sign");
                return;
        }
    }

    private void handleLadder(GameObject obj, Player player) {
        int id = obj.getID();
        int x = obj.getX();
        int y = obj.getY();
        
        switch (id) {
            case 7:
                if (x == 216 && y == 3107) {
                    player.message("You climb up the ladder");
                    player.teleport(216, 3108);
                    return;
                }
                if (x == 216 && y == 3108) {
                    player.message("You climb down the ladder");
                    player.teleport(216, 3107);
                    return;
                }
                if (x == 309 && y == 3432) {
                    player.message("You climb up the ladder");
                    player.teleport(309, 3433);
                    return;
                }
                player.message("You climb the ladder");
                return;
                
            case 159:
                player.message("You climb up the emergency escape ladder");
                player.teleport(x, y - 5);
                return;
                
            case 250:
                player.message("You climb the ladder");
                player.teleport(x, y - 3);
                return;
                
            case 271:
                player.message("You climb the ladder");
                player.teleport(x, y - 4);
                return;
                
            default:
                player.message("You climb the ladder");
                return;
        }
    }

    private void handleStairs(GameObject obj, Player player) {
        int id = obj.getID();
        int x = obj.getX();
        int y = obj.getY();
        
        switch (id) {
            case 43:
                player.message("You climb the stairs");
                player.teleport(x, y - 1);
                return;
                
            default:
                player.message("You climb the stairs");
                return;
        }
    }

    private void handleChest(GameObject obj, Player player) {
        int id = obj.getID();
        int x = obj.getX();
        int y = obj.getY();
        
        switch (id) {
            case 188:
                player.message("The chest is locked");
                return;
                
            case 229:
                player.message("The chest is locked");
                return;
                
            case 231:
                player.message("The chest is locked");
                return;
                
            case 232:
                player.message("The chest is locked");
                return;
                
            case 266:
                player.message("The chest is locked");
                return;
                
            case 267:
                player.message("The chest is locked");
                return;
                
            case 268:
                player.message("The chest is locked");
                return;
                
            case 336:
                player.message("The chest is empty");
                return;
                
            default:
                player.message("The chest is locked");
                return;
        }
    }

    private void handleGate(GameObject obj, Player player) {
        int id = obj.getID();
        int x = obj.getX();
        int y = obj.getY();
        
        switch (id) {
            case 61:
                player.message("The gate is locked");
                return;
                
            case 94:
                if (x == 539 && y == 599) {
                    player.message("The gate is locked");
                    return;
                }
                player.message("You go through the gate");
                return;
                
            case 95:
                if (x == 121 && y == 668) {
                    player.message("The gate is locked");
                    return;
                }
                player.message("You go through the gate");
                return;
                
            case 138:
                player.message("The gate is locked");
                return;
                
            case 254:
                player.message("Members only");
                return;
                
            case 257:
                player.message("The gate is locked");
                return;
                
            case 312:
                player.message("The gate is locked");
                return;
                
            case 320:
                player.message("You go through the gate");
                return;
                
            default:
                player.message("The gate is locked");
                return;
        }
    }
}

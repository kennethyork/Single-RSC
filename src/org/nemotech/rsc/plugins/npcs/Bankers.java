package org.nemotech.rsc.plugins.npcs;

import static org.nemotech.rsc.plugins.Plugin.*;
import java.util.List;
import org.nemotech.rsc.model.GrandExchange;
import org.nemotech.rsc.model.NPC;
import org.nemotech.rsc.model.player.InvItem;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.plugins.listeners.action.TalkToNpcListener;
import org.nemotech.rsc.plugins.listeners.executive.TalkToNpcExecutiveListener;

public class Bankers implements TalkToNpcExecutiveListener, TalkToNpcListener {

    public static final int GRAND_EXCHANGE_CLERK = 794;
    public static int[] BANKERS = { 95, 224, 268, 540, 617, GRAND_EXCHANGE_CLERK };

    @Override
    public boolean blockTalkToNpc(final Player player, final NPC npc) {
        if(inArray(npc.getID(), BANKERS)) {
            return true;
        }
        return false;
    }

    @Override
    public void onTalkToNpc(Player player, final NPC npc) {
        npcTalk(player, npc, "Good day"+(npc.getID() == 617 ? " Bwana" : "")+", how may I help you?");
        int menu = showMenu(player, npc, 
                "I'd like to access my bank account please", 
                "I'd like to use the Grand Exchange",
                "What is this place?");
        if (menu == 0) {
            npcTalk(player, npc, "Certainly " + (player.isMale() ? "Sir" : "Miss"));
            player.getSender().showBank();
        } else if (menu == 1) {
            handleGrandExchange(player, npc);
        } else if (menu == 2) {
            npcTalk(player, npc, "This is a branch of the bank of Runescape", "We have branches in many towns");
            int branchMenu = showMenu(player, npc, "And what do you do?",
                    "Didn't you used to be called the bank of Varrock");
            if (branchMenu == 0) {
                npcTalk(player, npc, "We will look after your items and money for you",
                        "So leave your valuables with us if you want to keep them safe");
            } else if (branchMenu == 1) {
                npcTalk(player, npc, "Yes we did, but people kept on coming into our branches outside of varrock",
                        "And telling us our signs were wrong",
                        "As if we didn't know what town we were in or something!");
            }
        }
    }

    private void handleGrandExchange(Player player, NPC npc) {
        npcTalk(player, npc, "The Grand Exchange stock is shared by everyone",
                "Items deposited here can be collected from any bank");
        int option = showMenu(player, npc,
                "Deposit my tradable inventory",
                "Pick up an item",
                "Show current stock",
                "Cancel");

        if (option == 0) {
            int deposited = GrandExchange.depositInventory(player);
            player.getSender().sendMessage("@cya@[GE] @whi@Deposited " + deposited + " tradable item" + (deposited == 1 ? "" : "s") + ".");
        } else if (option == 1) {
            withdrawFromExchange(player, npc);
        } else if (option == 2) {
            showStock(player);
        }
    }

    private void withdrawFromExchange(Player player, NPC npc) {
        List<InvItem> stock = GrandExchange.getStockSnapshot();
        if (stock.isEmpty()) {
            npcTalk(player, npc, "The Grand Exchange is empty right now");
            return;
        }

        int size = Math.min(5, stock.size());
        String[] options = new String[size + 1];
        for (int i = 0; i < size; i++) {
            InvItem item = stock.get(i);
            options[i] = item.getDef().getName() + " (" + item.getAmount() + ")";
        }
        options[size] = "Cancel";

        int itemOption = showMenu(player, npc, options);
        if (itemOption < 0 || itemOption >= size) {
            return;
        }

        InvItem selected = stock.get(itemOption);
        int amountOption = showMenu(player, npc, "One", "Five", "Ten", "All");
        int amount = 1;
        if (amountOption == 1) {
            amount = 5;
        } else if (amountOption == 2) {
            amount = 10;
        } else if (amountOption == 3) {
            amount = selected.getAmount();
        } else if (amountOption < 0) {
            return;
        }

        amount = Math.min(amount, GrandExchange.countId(selected.getID()));
        if (GrandExchange.withdraw(player, selected.getID(), amount)) {
            player.getSender().sendMessage("@cya@[GE] @whi@Picked up " + amount + " " + selected.getDef().getName() + ".");
        } else {
            player.getSender().sendMessage("@cya@[GE] @red@Could not pick that up.");
        }
    }

    private void showStock(Player player) {
        List<InvItem> stock = GrandExchange.getStockSnapshot();
        if (stock.isEmpty()) {
            player.getSender().sendMessage("@cya@[GE] @whi@The Grand Exchange is empty.");
            return;
        }

        player.getSender().sendMessage("@cya@=== Grand Exchange Stock ===");
        for (int i = 0; i < stock.size() && i < 10; i++) {
            InvItem item = stock.get(i);
            player.getSender().sendMessage("@whi@" + item.getID() + " - " + item.getDef().getName() + ": " + item.getAmount());
        }
        if (stock.size() > 10) {
            player.getSender().sendMessage("@whi@Use ::ge list to see stock in chat.");
        }
    }
    
}

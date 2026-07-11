package org.nemotech.rsc.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.nemotech.rsc.external.EntityManager;
import org.nemotech.rsc.model.player.InvItem;
import org.nemotech.rsc.model.player.Player;

public final class GrandExchange {

    private static final org.nemotech.rsc.model.player.Bank STOCK = new org.nemotech.rsc.model.player.Bank();

    private GrandExchange() {}

    public static synchronized int countId(int itemId) {
        return STOCK.countId(itemId);
    }

    public static synchronized List<InvItem> getStockSnapshot() {
        ArrayList<InvItem> snapshot = new ArrayList<>();
        for (InvItem item : STOCK.getItems()) {
            snapshot.add(new InvItem(item.getID(), item.getAmount()));
        }
        snapshot.sort(new Comparator<InvItem>() {
            @Override
            public int compare(InvItem first, InvItem second) {
                return second.getAmount() - first.getAmount();
            }
        });
        return snapshot;
    }

    public static synchronized boolean deposit(Player player, int itemId, int amount) {
        if (!validItem(itemId) || amount < 1 || player.getInventory().countId(itemId) < amount) {
            return false;
        }
        if (!EntityManager.getItem(itemId).isTradable()) {
            player.getSender().sendMessage("That item cannot be exchanged");
            return false;
        }

        InvItem item = new InvItem(itemId, amount);
        if (EntityManager.getItem(itemId).isStackable()) {
            if (player.getInventory().remove(item) < 0) {
                return false;
            }
            STOCK.add(item);
            return true;
        }

        int deposited = 0;
        for (int i = 0; i < amount; i++) {
            if (player.getInventory().remove(itemId, 1) < 0) {
                break;
            }
            STOCK.add(new InvItem(itemId, 1));
            deposited++;
        }
        return deposited > 0;
    }

    public static synchronized int depositInventory(Player player) {
        List<InvItem> items = new ArrayList<>();
        for (InvItem item : player.getInventory().getItems()) {
            if (validItem(item.getID()) && item.getDef().isTradable()) {
                items.add(new InvItem(item.getID(), player.getInventory().countId(item.getID())));
            }
        }

        int deposited = 0;
        for (InvItem item : items) {
            int count = player.getInventory().countId(item.getID());
            if (count > 0 && deposit(player, item.getID(), count)) {
                deposited += count;
            }
        }
        return deposited;
    }

    public static synchronized boolean depositSystem(int itemId, int amount) {
        if (!validItem(itemId) || amount < 1 || !EntityManager.getItem(itemId).isTradable()) {
            return false;
        }
        STOCK.add(new InvItem(itemId, amount));
        return true;
    }

    public static synchronized boolean withdrawSystem(int itemId, int amount) {
        if (!validItem(itemId) || amount < 1 || STOCK.countId(itemId) < amount) {
            return false;
        }
        return STOCK.remove(new InvItem(itemId, amount)) > -1;
    }

    public static synchronized boolean withdraw(Player player, int itemId, int amount) {
        if (!validItem(itemId) || amount < 1 || STOCK.countId(itemId) < amount) {
            return false;
        }

        InvItem item = new InvItem(itemId, amount);
        if (EntityManager.getItem(itemId).isStackable()) {
            if (!player.getInventory().canHold(item)) {
                player.getSender().sendMessage("You don't have room for that in your inventory");
                return false;
            }
            if (STOCK.remove(item) < 0) {
                return false;
            }
            player.getInventory().add(item);
            return true;
        }

        int withdrawn = 0;
        for (int i = 0; i < amount; i++) {
            InvItem one = new InvItem(itemId, 1);
            if (!player.getInventory().canHold(one) || STOCK.remove(one) < 0) {
                break;
            }
            player.getInventory().add(one);
            withdrawn++;
        }
        if (withdrawn == 0) {
            player.getSender().sendMessage("You don't have room for that in your inventory");
        }
        return withdrawn > 0;
    }

    private static boolean validItem(int itemId) {
        return itemId >= 0 && itemId < EntityManager.getItems().length && EntityManager.getItem(itemId) != null;
    }
}

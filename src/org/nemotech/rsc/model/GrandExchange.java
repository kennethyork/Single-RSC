package org.nemotech.rsc.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.nemotech.rsc.external.EntityManager;
import org.nemotech.rsc.model.player.InvItem;
import org.nemotech.rsc.model.player.Player;

public final class GrandExchange {

    private static final int COINS = 10;
    private static final int MAX_PRICE = 2_000_000_000;
    private static final org.nemotech.rsc.model.player.Bank STOCK = new org.nemotech.rsc.model.player.Bank();
    private static final Map<Integer, MarketItem> MARKET = new LinkedHashMap<>();
    private static boolean seeded;

    private GrandExchange() {}

    public static synchronized int countId(int itemId) {
        seedMarketIfNeeded();
        return STOCK.countId(itemId);
    }

    public static synchronized List<InvItem> getStockSnapshot() {
        seedMarketIfNeeded();
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

    public static synchronized int buyPrice(int itemId, int amount) {
        if (!validExchangeItem(itemId) || amount < 1) {
            return 0;
        }
        long price = (long) unitBuyPrice(itemId) * amount;
        return price > MAX_PRICE ? MAX_PRICE : (int) price;
    }

    public static synchronized int sellPrice(int itemId, int amount) {
        if (!validExchangeItem(itemId) || amount < 1) {
            return 0;
        }
        long price = Math.max(1, (long) unitBuyPrice(itemId) * 65 / 100) * amount;
        return price > MAX_PRICE ? MAX_PRICE : (int) price;
    }

    public static synchronized String getMarketReport() {
        seedMarketIfNeeded();
        List<InvItem> stock = getStockSnapshot();
        StringBuilder report = new StringBuilder("Grand Exchange market");
        if (stock.isEmpty()) {
            return report.append("\nempty").toString();
        }
        for (int i = 0; i < stock.size() && i < 10; i++) {
            InvItem item = stock.get(i);
            MarketItem market = marketItem(item.getID());
            report.append("\n").append(item.getID()).append(" ").append(item.getDef().getName())
                    .append(" stock ").append(item.getAmount())
                    .append(" buy ").append(buyPrice(item.getID(), 1))
                    .append(" sell ").append(sellPrice(item.getID(), 1))
                    .append(" sold ").append(market.sold)
                    .append(" bought ").append(market.bought);
        }
        return report.toString();
    }

    public static synchronized boolean deposit(Player player, int itemId, int amount) {
        if (!validExchangeItem(itemId) || amount < 1 || player.getInventory().countId(itemId) < amount) {
            return false;
        }

        int coins = sellPrice(itemId, amount);
        InvItem item = new InvItem(itemId, amount);
        if (EntityManager.getItem(itemId).isStackable()) {
            if (player.getInventory().remove(item) < 0) {
                return false;
            }
            STOCK.add(item);
            player.getInventory().add(new InvItem(COINS, coins));
            recordSell(itemId, amount, coins);
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
        if (deposited > 0) {
            int paid = sellPrice(itemId, deposited);
            player.getInventory().add(new InvItem(COINS, paid));
            recordSell(itemId, deposited, paid);
        }
        return deposited > 0;
    }

    public static synchronized int depositInventory(Player player) {
        List<InvItem> items = new ArrayList<>();
        for (InvItem item : player.getInventory().getItems()) {
            if (validExchangeItem(item.getID())) {
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
        return sellSystem(itemId, amount) > 0;
    }

    public static synchronized int sellSystem(int itemId, int amount) {
        if (!validExchangeItem(itemId) || amount < 1) {
            return 0;
        }
        int coins = sellPrice(itemId, amount);
        STOCK.add(new InvItem(itemId, amount));
        recordSell(itemId, amount, coins);
        return coins;
    }

    public static synchronized boolean withdrawSystem(int itemId, int amount) {
        return buySystem(itemId, amount) > 0;
    }

    public static synchronized int buySystem(int itemId, int amount) {
        seedMarketIfNeeded();
        if (!validExchangeItem(itemId) || amount < 1 || STOCK.countId(itemId) < amount) {
            return 0;
        }
        int coins = buyPrice(itemId, amount);
        if (STOCK.remove(new InvItem(itemId, amount)) < 0) {
            return 0;
        }
        recordBuy(itemId, amount, coins);
        return coins;
    }

    public static synchronized boolean withdraw(Player player, int itemId, int amount) {
        seedMarketIfNeeded();
        if (!validExchangeItem(itemId) || amount < 1 || STOCK.countId(itemId) < amount) {
            return false;
        }
        int coins = buyPrice(itemId, amount);
        if (player.getInventory().countId(COINS) < coins) {
            player.getSender().sendMessage("You need " + coins + " coins to buy that");
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
            player.getInventory().remove(COINS, coins);
            player.getInventory().add(item);
            recordBuy(itemId, amount, coins);
            return true;
        }

        int unitPrice = buyPrice(itemId, 1);
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
        } else {
            int paid = Math.min(coins, unitPrice * withdrawn);
            player.getInventory().remove(COINS, paid);
            recordBuy(itemId, withdrawn, paid);
        }
        return withdrawn > 0;
    }

    private static void recordBuy(int itemId, int amount, int coins) {
        MarketItem item = marketItem(itemId);
        item.bought += amount;
        item.buyCoins += coins;
    }

    private static void recordSell(int itemId, int amount, int coins) {
        MarketItem item = marketItem(itemId);
        item.sold += amount;
        item.sellCoins += coins;
    }

    private static MarketItem marketItem(int itemId) {
        MarketItem item = MARKET.get(itemId);
        if (item == null) {
            item = new MarketItem();
            MARKET.put(itemId, item);
        }
        return item;
    }

    private static void seedMarketIfNeeded() {
        if (seeded || STOCK.size() > 0) {
            seeded = true;
            return;
        }
        seeded = true;
        int[][] starterStock = {
            { 14, 40 }, { 150, 60 }, { 151, 60 }, { 155, 40 },
            { 349, 35 }, { 372, 80 }, { 373, 120 },
            { 20, 25 }, { 31, 20 }, { 33, 20 }, { 38, 12 },
            { 81, 8 }, { 87, 10 }, { 93, 6 },
            { 412, 20 }, { 413, 15 }, { 632, 30 }, { 633, 30 }
        };
        for (int[] stock : starterStock) {
            if (validExchangeItem(stock[0])) {
                STOCK.add(new InvItem(stock[0], stock[1]));
            }
        }
    }

    private static int unitBuyPrice(int itemId) {
        int base = Math.max(1, EntityManager.getItem(itemId).getPrice());
        int stock = STOCK.countId(itemId);
        int multiplier = stock < 5 ? 150 : stock < 20 ? 125 : stock > 100 ? 85 : 100;
        return Math.max(1, base * multiplier / 100);
    }

    private static boolean validExchangeItem(int itemId) {
        return validItem(itemId) && itemId != COINS && EntityManager.getItem(itemId).isTradable();
    }

    private static boolean validItem(int itemId) {
        return itemId >= 0 && itemId < EntityManager.getItems().length && EntityManager.getItem(itemId) != null;
    }

    private static final class MarketItem {
        private int bought;
        private int sold;
        private int buyCoins;
        private int sellCoins;
    }
}

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

    public static final int MAX_LISTINGS = 192;
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
        seedMarketIfNeeded();
        if (!validExchangeItem(itemId) || amount < 1) {
            return 0;
        }
        long price = (long) unitBuyPrice(itemId) * amount;
        return price > MAX_PRICE ? MAX_PRICE : (int) price;
    }

    public static synchronized int sellPrice(int itemId, int amount) {
        seedMarketIfNeeded();
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
            // Logs, ores, bars, and gathering tools.
            { 14, 160 }, { 632, 140 }, { 633, 120 }, { 634, 90 }, { 635, 60 }, { 636, 30 },
            { 150, 180 }, { 202, 180 }, { 151, 150 }, { 155, 220 }, { 383, 80 }, { 152, 70 },
            { 153, 60 }, { 154, 40 }, { 409, 20 },
            { 169, 120 }, { 170, 100 }, { 171, 90 }, { 384, 60 }, { 172, 50 }, { 173, 45 },
            { 174, 30 }, { 408, 15 }, { 87, 20 }, { 12, 20 }, { 88, 15 }, { 203, 10 },
            { 204, 8 }, { 405, 4 }, { 156, 20 }, { 1258, 18 }, { 1259, 14 }, { 1260, 10 },
            { 1261, 7 }, { 1262, 3 },

            // Raw and cooked food.
            { 349, 160 }, { 350, 120 }, { 351, 140 }, { 352, 100 }, { 358, 130 }, { 359, 100 },
            { 356, 120 }, { 357, 90 }, { 366, 100 }, { 367, 80 }, { 372, 100 }, { 373, 100 },
            { 369, 70 }, { 370, 70 }, { 545, 40 }, { 546, 40 }, { 138, 80 }, { 257, 30 },
            { 325, 30 }, { 326, 25 }, { 327, 25 }, { 330, 30 }, { 332, 20 },

            // Runes, arrows, and ranged supplies.
            { 31, 500 }, { 32, 500 }, { 33, 500 }, { 34, 500 }, { 35, 400 }, { 36, 300 },
            { 41, 260 }, { 40, 220 }, { 42, 180 }, { 38, 140 }, { 46, 120 }, { 619, 80 }, { 825, 30 },
            { 11, 500 }, { 638, 400 }, { 640, 300 }, { 642, 220 }, { 644, 140 }, { 646, 60 },
            { 381, 500 }, { 280, 400 }, { 637, 350 }, { 669, 300 }, { 670, 260 }, { 671, 220 },
            { 672, 160 }, { 673, 100 }, { 674, 50 },

            // Crafting, fletching, gems, and potions.
            { 147, 120 }, { 148, 100 }, { 675, 180 }, { 676, 160 }, { 464, 100 },
            { 160, 60 }, { 159, 50 }, { 158, 40 }, { 157, 30 }, { 542, 10 },
            { 164, 45 }, { 163, 35 }, { 162, 25 }, { 161, 20 }, { 523, 8 },
            { 648, 20 }, { 649, 20 }, { 650, 18 }, { 651, 18 }, { 652, 15 }, { 653, 15 },
            { 654, 10 }, { 655, 10 }, { 656, 6 }, { 657, 6 },
            { 221, 25 }, { 474, 25 }, { 480, 20 }, { 486, 15 }, { 492, 15 }, { 495, 15 },
            { 498, 12 }, { 566, 12 },

            // Bones and commonly traded combat equipment.
            { 20, 160 }, { 413, 100 }, { 604, 60 }, { 814, 30 },
            { 66, 20 }, { 1, 18 }, { 67, 15 }, { 68, 10 }, { 69, 7 }, { 397, 4 },
            { 117, 16 }, { 8, 14 }, { 118, 12 }, { 119, 8 }, { 120, 6 }, { 401, 3 },
            { 206, 16 }, { 9, 14 }, { 121, 12 }, { 122, 8 }, { 123, 6 }, { 402, 3 },
            { 128, 16 }, { 2, 14 }, { 129, 12 }, { 130, 8 }, { 131, 6 }, { 404, 3 },
            { 205, 15 }, { 89, 12 }, { 90, 10 }, { 91, 7 }, { 92, 5 }, { 93, 3 },
            { 81, 3 }, { 112, 3 }, { 400, 3 }
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

package org.nemotech.rsc.client.action.impl;

import java.util.List;

import org.nemotech.rsc.model.GrandExchange;
import org.nemotech.rsc.model.player.InvItem;
import org.nemotech.rsc.model.Shop;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.model.player.Inventory;
import org.nemotech.rsc.model.World;
import org.nemotech.rsc.client.mudclient;
import org.nemotech.rsc.client.sound.SoundEffect;
import org.nemotech.rsc.client.action.ActionHandler;

public class ShopHandler implements ActionHandler {
    
    private mudclient mc = mudclient.getInstance();
    private Player player = World.getWorld().getPlayer();
    private Shop shop;
    private boolean grandExchangeOpen;
    
    public void handleShopOpen(Shop shop) {
        this.shop = shop;
        grandExchangeOpen = false;
        mc.showDialogShop = true;
        int shopSize = shop.getShopSize();
        int shopType = shop.getType();
        //mc.shopSellPriceMod = shop.getSellModifier();
        //mc.shopBuyPriceMod = shop.getBuyModifier();
        for (int itemIndex = 0; itemIndex < 40; itemIndex++) {
            mc.shopItem[itemIndex] = -1;
        }
        for (int i = 0; i < shopSize; i++) {
            InvItem item = shop.getShopItem(i);
            mc.shopItem[i] = item.getID();
            mc.shopItemCount[i] = item.getAmount();
            mc.shopItemBuyPrice[i] = shop.getItemPrice(i, true);
            mc.shopItemSellPrice[i] = shop.getItemPrice(i, false);
        }
        mc.shopName = shop.getName();
        
        if (shop.isGeneral()) { // shopType == 1 -> is a general shop
            int l28 = 39;
            for (int k33 = 0; k33 < mc.inventoryItemsCount; k33++) {
                if (l28 < shopSize)
                    break;
                boolean flag2 = false;
                for (int j39 = 0; j39 < 40; j39++) {
                    if (mc.shopItem[j39] != mc.inventoryItemId[k33])
                        continue;
                    flag2 = true;
                    break;
                }

                if (mc.inventoryItemId[k33] == 10)
                    flag2 = true;
                if (!flag2) {
                    mc.shopItem[l28] = mc.inventoryItemId[k33] & 0x7fff;
                    mc.shopItemCount[l28] = 0;
                    mc.shopItemBuyPrice[l28] = 0;
                    mc.shopItemSellPrice[l28] = 0;
                    l28--;
                }
            }

        }
        if (mc.shopSelectedItemIndex >= 0 && mc.shopSelectedItemIndex < 40 && mc.shopItem[mc.shopSelectedItemIndex] != mc.shopSelectedItemType) {
            mc.shopSelectedItemIndex = -1;
            mc.shopSelectedItemType = -2;
        }
    }

    public void handleGrandExchangeOpen() {
        shop = null;
        grandExchangeOpen = true;
        mc.showDialogShop = true;
        refreshGrandExchangeStock();
    }
    
    public void handleShopClose() {
        shop = null;
        grandExchangeOpen = false;
        mc.showDialogShop = false;
    }
    
    public void handleBuyItem(int id) {
        if (grandExchangeOpen) {
            handleGrandExchangeBuy(id);
            return;
        }
        if(shop == null) {
            return;
        }
        InvItem item = new InvItem(id, 1);
        if (shop.getItemCount(item.getID()) < 1) {
            return;
        }   
        if (player.getInventory().countId(10) < shop.getItemBuyPrice(id)) {
            player.getSender().sendMessage("You don't have enough money!");
            return;
        } 
        if ((Inventory.MAX_SIZE - player.getInventory().size()) + player.getInventory().getFreedSlots(new InvItem(10, shop.getItemBuyPrice(id))) < player.getInventory().getRequiredSlots(item)) {
            player.getSender().sendMessage("You can't hold the objects you are trying to buy!");
            return;
        }
        int itemprice = item.getDef().getPrice();
        int userHasToPay = shop.getItemBuyPrice(item.getID());

        if (itemprice == 0 || userHasToPay < itemprice)
            return;

        if (player.getInventory().remove(10, userHasToPay) > -1) {
            shop.removeShopItem(item);
            player.getInventory().add(item);
            player.getSender().sendInventory();
            mc.playSoundEffect(SoundEffect.COINS);
        }
    }
    
    public void handleSellItem(int id) {
        if (grandExchangeOpen) {
            handleGrandExchangeSell(id);
            return;
        }
        InvItem item = new InvItem(id, 1);

        if (player.getInventory().countId(item.getID()) < 1) {
            return;
        } 
        if (!shop.shouldStock(item.getID())) {
            return;
        }
        if (!shop.canHoldItem(item)) {
            player.getSender().sendMessage("The shop is currently full!");
            return;
        }

        int itempricez;
        
        if (shop.getItemCount(item.getID()) == 0) {
            itempricez = shop.getGenericPrice(item, false);
        } else {
            itempricez = shop.getItemSellPrice(item.getID());
        }

        if (player.getInventory().remove(item) > -1) {
            player.getInventory().add(new InvItem(10, itempricez));
            shop.addShopItem(item);
            player.getSender().sendInventory();
            mc.playSoundEffect(SoundEffect.COINS);
        }
    }

    private void refreshGrandExchangeStock() {
        for (int itemIndex = 0; itemIndex < mc.shopItem.length; itemIndex++) {
            mc.shopItem[itemIndex] = -1;
            mc.shopItemCount[itemIndex] = 0;
            mc.shopItemBuyPrice[itemIndex] = 0;
            mc.shopItemSellPrice[itemIndex] = 0;
        }

        int slot = 0;
        List<InvItem> stock = GrandExchange.getStockSnapshot();
        for (InvItem item : stock) {
            if (slot >= GrandExchange.MAX_LISTINGS || slot >= mc.shopItem.length) {
                break;
            }
            mc.shopItem[slot] = item.getID();
            mc.shopItemCount[slot] = item.getAmount();
            mc.shopItemBuyPrice[slot] = GrandExchange.buyPrice(item.getID(), 1);
            mc.shopItemSellPrice[slot] = GrandExchange.sellPrice(item.getID(), 1);
            slot++;
        }

        for (int inventoryIndex = 0; inventoryIndex < mc.inventoryItemsCount
                && slot < GrandExchange.MAX_LISTINGS && slot < mc.shopItem.length; inventoryIndex++) {
            int itemId = mc.inventoryItemId[inventoryIndex] & 0x7fff;
            if (itemId == 10 || GrandExchange.sellPrice(itemId, 1) <= 0 || containsGrandExchangeItem(itemId)) {
                continue;
            }
            mc.shopItem[slot] = itemId;
            mc.shopItemCount[slot] = 0;
            mc.shopItemBuyPrice[slot] = GrandExchange.buyPrice(itemId, 1);
            mc.shopItemSellPrice[slot] = GrandExchange.sellPrice(itemId, 1);
            slot++;
        }

        mc.shopName = "Grand Exchange";
        if (mc.shopSelectedItemIndex >= 0 && mc.shopSelectedItemIndex < mc.shopItem.length
                && mc.shopItem[mc.shopSelectedItemIndex] != mc.shopSelectedItemType) {
            mc.shopSelectedItemIndex = -1;
            mc.shopSelectedItemType = -2;
        }
    }

    private boolean containsGrandExchangeItem(int itemId) {
        for (int i = 0; i < mc.shopItem.length; i++) {
            if (mc.shopItem[i] == itemId) {
                return true;
            }
        }
        return false;
    }

    private void handleGrandExchangeBuy(int id) {
        if (GrandExchange.countId(id) < 1) {
            player.getSender().sendMessage("@cya@[GE] @red@That item is not currently available.");
            return;
        }
        int coins = GrandExchange.buyPrice(id, 1);
        if (GrandExchange.withdraw(player, id, 1)) {
            player.getSender().sendMessage("@cya@[GE] @whi@Bought 1 " + new InvItem(id, 1).getDef().getName() + " for " + coins + " coins.");
            player.getSender().sendInventory();
            mc.playSoundEffect(SoundEffect.COINS);
            refreshGrandExchangeStock();
        }
    }

    private void handleGrandExchangeSell(int id) {
        if (player.getInventory().countId(id) < 1) {
            return;
        }
        int coins = GrandExchange.sellPrice(id, 1);
        if (GrandExchange.deposit(player, id, 1)) {
            player.getSender().sendMessage("@cya@[GE] @whi@Sold 1 " + new InvItem(id, 1).getDef().getName() + " for " + coins + " coins.");
            player.getSender().sendInventory();
            mc.playSoundEffect(SoundEffect.COINS);
            refreshGrandExchangeStock();
        }
    }
    
}

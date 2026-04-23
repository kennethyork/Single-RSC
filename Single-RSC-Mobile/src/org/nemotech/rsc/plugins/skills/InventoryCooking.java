package org.nemotech.rsc.plugins.skills;

import org.nemotech.rsc.util.Util;
import org.nemotech.rsc.event.impl.BatchEvent;
import org.nemotech.rsc.model.player.InvItem;
import org.nemotech.rsc.model.player.Player;
import static org.nemotech.rsc.plugins.Plugin.addItem;
import static org.nemotech.rsc.plugins.Plugin.message;
import static org.nemotech.rsc.plugins.Plugin.removeItem;
import static org.nemotech.rsc.plugins.Plugin.showMenu;
import org.nemotech.rsc.plugins.listeners.action.InvUseOnItemListener;
import org.nemotech.rsc.plugins.listeners.executive.InvUseOnItemExecutiveListener;

/**
 * Way better way to handle item on item cooking.
 * @author n0m
 *
 */
public class InventoryCooking implements InvUseOnItemListener, InvUseOnItemExecutiveListener {
    
    enum CombineCooking {
        INCOMPLETE_STEW(348, 342, 343, 0, 25, "You start to create a stew"),
        UNCOOKED_STEW(132, 343, 345, 0, 25, "Your stew is now ready, but uncooked"),
        PIE_SHELL(250, 251, 253, 0, 1, "You add the pastry dough in the dish"),
        UNCOOKED_APPLEPIE(253, 252, 254, 0, 30,  "You create an uncoooked pie"),
        UNCOOKED_MEATPIE(253, 132, 255, 0, 20, "You create an uncoooked pie"),
        UNCOOKED_REDBERRYPIE(253, 236, 256, 0, 10,  "You create an uncoooked pie"),
        CHOCOLATE_CAKE(337, 330, 332, 0, 50, "You add chocolate to the cake"),
        INCOMPLETE_PIZZA(321, 320, 323, 0, 35, "You add tomato to the pizza base"),
        UNCOOKED_PIZZA(323, 319, 324, 0, 35, "You add cheese on the incomplete pizza"),
        MEAT_PIZZA(132, 325, 326, 0, 45, "You create a meat pizza."),
        ANCHOVIE_PIZZA(325, 352, 327, 0, 55, "You create a anchovie pizza."),
        PINEAPPLERINGS_PIZZA(325, 749, 750, 0, 65, "You create a pineapple pizza."),
        PINEAPPLECHUNCKS_PIZZA(325, 862, 750, 0, 65, "You create a pineapple pizza."),
        FLOUR_POT(23, 135, 136, 0, 1, "You pour flour to the pot");
        
        private int itemID;
        private int itemIDOther;
        private int resultItem;
        private int experience;
        private int requiredLevel;
        private String[] messages;
        
        CombineCooking(int itemOne, int itemTwo, int resultItem, int experience, int level, String... messages) {
            this.itemID = itemOne;
            this.itemIDOther = itemTwo;
            this.resultItem = resultItem;
            this.experience = experience;
            this.requiredLevel = level;
            this.messages = messages;
        }
        
        public boolean isValid(int i, int is) {
            return itemID == i && itemIDOther == is || itemIDOther == i && itemID == is;
        }
    }
    
    @Override
    public void onInvUseOnItem(Player player, InvItem item1, InvItem item2) {
        if (item1.getID() == 338 || item2.getID() == 338) {
            if (player.getInventory().remove(new InvItem(19)) > -1
                    && player.getInventory().remove(new InvItem(22)) > -1
                    && player.getInventory().remove(new InvItem(136)) > -1
                    && player.getInventory().remove(new InvItem(338)) > -1) {
                player.getInventory().add(new InvItem(135));
                player.getInventory().add(new InvItem(339));
                player.message("You create an uncooked cake");
                return;
            }
        }
        if (item1.getID() == 143 && item2.getID() == 141
                || item1.getID() == 141 && item2.getID() == 143) {
            if (player.getCurStat(7) < 35) {
                player.message("You need level 35 cooking to do this");
                return;
            }
            int grapes = player.getInventory().countId(143);
            int waters = player.getInventory().countId(141);
            int maxMake = Math.min(grapes, waters);
            if (maxMake <= 0) return;
            String[] countOpts = new String[] {"Make 1", "Make 5", "Make 10", "Make All"};
            int c = showMenu(player, countOpts);
            if (player.isBusy() || c < 0 || c > 3) return;
            final int batches = (c == 3 ? maxMake : Integer.parseInt(countOpts[c].replace("Make ", "")));
            player.setBatchEvent(new BatchEvent(player, 650, batches) {
                @Override
                public void action() {
                    if (!owner.getInventory().contains(item1) || !owner.getInventory().contains(item2)) { interrupt(); return; }
                    int rand = Util.random(0, 4);
                    if (rand == 2) {
                        owner.incExp(7, 55, true);
                        owner.getInventory().add(new InvItem(180));
                        owner.message("You mix the grapes, and accidentally create Bad wine!");
                    } else {
                        owner.incExp(7, 110, true);
                        owner.getInventory().add(new InvItem(142));
                        owner.message("You mix the grapes with the water and create wine!");
                    }
                    owner.getInventory().remove(141, 1);
                    owner.getInventory().remove(143, 1);
                }
            });
        }
        else if (isWaterItem(item1) && item2.getID() == 136 || item1.getID() == 136 && isWaterItem(item2)) {
            int waterContainer = isWaterItem(item1) ? item1.getID() : item2.getID();
            
            player.message("What would you like to make?");
            int option = showMenu(player, "Bread dough", "Pastry dough", "Pizza dough", "Pitta dough");
            if (player.isBusy() || option < 0 || option > 3) {
                return;
            }
            int productID = -1;
            if(option == 0) {
                productID = 137;
            }
            else if(option == 1) {
                productID = 250;
            }
            else if(option == 2) {
                productID = 321; 
            } 
            else if(option == 3) {
                productID = 1104;
            }
            if (productID > -1) {
                int flour = player.getInventory().countId(136);
                int waters = player.getInventory().countId(waterContainer);
                int maxMake = Math.min(flour, waters);
                if (maxMake <= 0) return;
                String[] countOpts = new String[] {"Make 1", "Make 5", "Make 10", "Make All"};
                int c = showMenu(player, countOpts);
                if (player.isBusy() || c < 0 || c > 3) return;
                final int batches = (c == 3 ? maxMake : Integer.parseInt(countOpts[c].replace("Make ", "")));
                final int wc = waterContainer;
                final int prod = productID;
                player.setBatchEvent(new BatchEvent(player, 650, batches) {
                    @Override
                    public void action() {
                        if (!(owner.getInventory().contains(new InvItem(wc)) && owner.getInventory().contains(new InvItem(136)))) { interrupt(); return; }
                        int emptyContainer = 0;
                        if(wc== 50) emptyContainer = 21; else if(wc == 141) emptyContainer = 140;
                        if (removeItem(owner, new InvItem(wc), new InvItem(136))) {
                            addItem(owner, 135, 1);
                            addItem(owner, emptyContainer, 1);
                            addItem(owner, prod, 1);
                            owner.message("You mix the water and flour to make some " + new InvItem(prod, 1).getDef().getName().toLowerCase());
                        } else {
                            interrupt();
                        }
                    }
                });
            }
        } else if(isValidCooking(item1, item2)) {
            handleCombineCooking(player, item1, item2);
        }
        return;
    }
    
    public void handleCombineCooking(Player p, InvItem itemOne, InvItem itemTwo) {
        CombineCooking combine = null;
        for(CombineCooking c : CombineCooking.values()) {
            if(c.isValid(itemOne.getID(), itemTwo.getID())) {
                combine = c;
            }
        }
        if (p.getCurStat(7) < combine.requiredLevel) {
            p.message("You need level " + combine.requiredLevel + " cooking to do this");
            return;
        }
        final int itemIdA = combine.itemID;
        final int itemIdB = combine.itemIDOther;
        final int resultId = combine.resultItem;
        final int expEach = combine.experience;
        final String[] msgs = combine.messages;
        int haveA = p.getInventory().countId(itemIdA);
        int haveB = p.getInventory().countId(itemIdB);
        int maxMake = Math.min(haveA, haveB);
        if (maxMake <= 0) return;
        String[] countOpts = new String[] {"Make 1", "Make 5", "Make 10", "Make All"};
        int c = showMenu(p, countOpts);
        if (p.isBusy() || c < 0 || c > 3) return;
        final int batches = (c == 3 ? maxMake : Integer.parseInt(countOpts[c].replace("Make ", "")));
        p.setBatchEvent(new BatchEvent(p, 650, batches) {
            @Override
            public void action() {
                if(removeItem(p, itemIdA, 1) && removeItem(p, itemIdB, 1)) {
                    if(msgs.length > 1)
                        message(p, msgs[0]);
                    else
                        p.message(msgs[0]);
                    addItem(p, resultId, 1);
                    p.incExp(7, expEach, true);
                    if(msgs.length > 1)
                        p.message(msgs[1]);
                } else {
                    interrupt();
                }
            }
        });
    }
    
    public boolean isValidCooking(InvItem itemOne, InvItem itemTwo) {
        for(CombineCooking c : CombineCooking.values()) {
            if(c.isValid(itemOne.getID(), itemTwo.getID())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isWaterItem(InvItem item) {
        return item.getID() == 50 || item.getID() == 141;
    }
    
    @Override
    public boolean blockInvUseOnItem(Player player, InvItem item1, InvItem item2) {
        if (item1.getID() == 338 || item2.getID() == 338)
            return true;
        if (item1.getID() == 143 && item2.getID() == 141
                || item1.getID() == 141 && item2.getID() == 143)
            return true;
        if (isWaterItem(item1) && item2.getID() == 136 || item1.getID() == 136 && isWaterItem(item2))
            return true;
        
        return isValidCooking(item1, item2);
    }
}

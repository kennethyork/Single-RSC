package org.nemotech.rsc.plugins.npcs.lumbridge;

import static org.nemotech.rsc.plugins.Plugin.npcTalk;
import static org.nemotech.rsc.plugins.Plugin.showMenu;

import org.nemotech.rsc.model.Shop;
import org.nemotech.rsc.model.player.InvItem;
import org.nemotech.rsc.model.NPC;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.plugins.ShopInterface;
import org.nemotech.rsc.plugins.listeners.action.TalkToNpcListener;
import org.nemotech.rsc.plugins.listeners.executive.TalkToNpcExecutiveListener;

/**
 * Bob's Axes shop.
 *
 * Stocks only woodcutting axes (hatchets) Bronze -> Steel -> Rune (Mithril and Adamant intentionally omitted)
 * plus pickaxes Bronze -> Rune. Battle axes are intentionally NOT stocked.
 */
public final class BobsAxes implements ShopInterface,
        TalkToNpcExecutiveListener, TalkToNpcListener {

    private final Shop shop = new Shop(
            "Bob's Axes",
            false,
            15000, // restock delay
            100,   // buy percentage
            60,    // sell percentage
            2,     // stock variation factor
            // Woodcutting axes (hatchets)
            new InvItem(87, 10),   // Bronze Axe
            new InvItem(12, 8),    // Iron Axe
            new InvItem(88, 6),    // Steel Axe
            new InvItem(405, 2),   // Rune Axe
            // Pickaxes Bronze -> Rune
            new InvItem(156, 5),   // Bronze pickaxe
            new InvItem(1258, 4),  // Iron pickaxe
            new InvItem(1259, 3),  // Steel pickaxe
            new InvItem(1260, 2),  // Mithril pickaxe
            new InvItem(1261, 2),  // Adamant pickaxe
            new InvItem(1262, 1)   // Rune pickaxe
    );

    @Override
    public boolean blockTalkToNpc(final Player p, final NPC n) {
        return n.getID() == 1;
    }

    @Override
    public Shop[] getShops() {
        return new Shop[] { shop };
    }

    @Override
    public boolean isMembers() {
        return false;
    }

    @Override
    public void onTalkToNpc(final Player p, final NPC n) {
        npcTalk(p, n, "Hello. How can I help you?");
        int option = showMenu(p, n,
                "Give me a quest!",
                "Have you anything to sell?");
        switch (option) {
            case 0:
                npcTalk(p, n, "Get yer own!");
                break;
            case 1:
                npcTalk(p, n, "Yes, I buy and sell axes, take your pick! (or axe)");
                org.nemotech.rsc.client.action.ActionManager
                        .get(org.nemotech.rsc.client.action.impl.ShopHandler.class)
                        .handleShopOpen(shop);
                break;
        }
    }
}
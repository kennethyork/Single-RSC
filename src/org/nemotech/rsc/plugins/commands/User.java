package org.nemotech.rsc.plugins.commands;

import org.nemotech.rsc.client.mudclient;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.plugins.Plugin;
import org.nemotech.rsc.plugins.listeners.action.CommandListener;
import java.util.Map;
import java.util.HashMap;
import org.nemotech.rsc.model.Point;

public class User extends Plugin implements CommandListener {

    // Map of major town teleport locations
    private static final Map<String, Point> TOWNS = new HashMap<String, Point>() {{
        put("varrock",    new Point(122, 509));
        put("falador",    new Point(304, 542));
        put("draynor",    new Point(214, 632));
        put("portsarim",  new Point(269, 643));
        put("karamja",    new Point(370, 685));
        put("alkharid",   new Point(89,  693));
        put("lumbridge",  new Point(120, 648));
        put("edgeville",  new Point(217, 449));
        put("taverly",    new Point(373, 498));
        put("seers",      new Point(501, 450));
        put("seers village", new Point(501, 450));
        put("barbarian",  new Point(233, 513));
        put("rimmington", new Point(325, 663));
        put("catherby",   new Point(440, 501));
        put("ardougne",   new Point(549, 589));
        put("yanille",    new Point(583, 747));
        put("lostcity",   new Point(127, 3518));
        put("gnome",      new Point(703, 527));
        put("tutorial",   new Point(219, 742));
    }};
    @Override
    public void onCommand(String command, String[] args, Player player) {
        if(command.equals("help")) {
            mudclient.getInstance().showAlert("@yel@Single Player RSC Help % %" +
                "@whi@Type ::stuck if your character gets stuck. % " +
                "Type ::pos (::coords, ::sector) to list your current location. % " +
                "Type ::bank (::openbank) to open your bank anywhere. % " +
                "Type ::mapedit to bring up the real time map editor", false);
            return;
        }
        if(command.equals("bank") || command.equals("openbank")) {
            player.getSender().showBank();
            player.getSender().sendMessage("You access your bank account");
            return;
        }
        if(command.equals("stuck")) {
            player.teleport(122, 647, true);
            return;
        }
        if(command.equals("reload")) {
            //mudclient.getInstance().loadSection(mudclient.getInstance().sectionX, mudclient.getInstance().sectionY );
            return;
        }
        if(command.equals("pos") || command.equals("coords") || command.equals("sector")) {
            int x = player.getX();
            int y = player.getY();
            int sectorH = 0;
            int sectorX = 0;
            int sectorY = 0;
            if (x != -1 && y != -1) {
                if (y >= 0 && y <= 1007)
                    sectorH = 0;
                else if (y >= 1007 && y <= 1007 + 943) {
                    sectorH = 1;
                    y -= 943;
                } else if (y >= 1008 + 943 && y <= 1007 + (943 * 2)) {
                    sectorH = 2;
                    y -= 943 * 2;
                } else {
                    sectorH = 3;
                    y -= 943 * 3;
                }
                sectorX = (x / 48) + 48;
                sectorY = (y / 48) + 37;
            }
            player.getSender().sendMessage(String.format("@whi@X: %d Y: %d (Sector h%dx%dy%d)@que@", player.getX(), player.getY(), sectorH, sectorX, sectorY));
            return;
        }

        // teleport to major cities
        if (command.equals("tele")) {
            // Usage: ::tele <location>
            if (args.length != 1) {
                player.getSender().sendMessage("Syntax: ::tele <location>");
                return;
            }
            String key = args[0].toLowerCase();
            Point loc = TOWNS.get(key);
            if (loc != null) {
                player.teleport(loc.getX(), loc.getY(), true);
            } else {
                player.getSender().sendMessage("Invalid location!");
            }
            return;
        }
        // speed commands removed
    }

}

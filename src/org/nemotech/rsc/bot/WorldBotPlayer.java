package org.nemotech.rsc.bot;

import org.nemotech.rsc.model.Point;
import org.nemotech.rsc.model.player.Appearance;
import org.nemotech.rsc.model.player.Player;

/** A clientless autonomous player, equivalent to the 2012 simulated-player entity. */
public final class WorldBotPlayer extends Player {

    private final int experienceRate;

    public WorldBotPlayer(String name, Point spawn, int[] experience, Appearance appearance, int experienceRate) {
        this.experienceRate = Math.max(1, experienceRate);
        initializeHeadless(name, spawn, experience, appearance);
    }

    @Override
    public void incExp(int skill, double amount, boolean useFatigue) {
        super.incExp(skill, amount * experienceRate, useFatigue, false);
    }
}

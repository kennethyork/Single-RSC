package org.nemotech.rsc.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static registry of all plugin classes for Android compatibility.
 * Android's DEX classloader doesn't support classpath scanning via
 * ClassLoader.getResources(), so we enumerate classes at compile time.
 */
public class PluginRegistry {

    private static final Map<String, List<Class<?>>> packageClasses = new HashMap<>();

    static {
        // Action listeners (interfaces)
        register("org.nemotech.rsc.plugins.listeners.action",
            org.nemotech.rsc.plugins.listeners.action.CommandListener.class,
            org.nemotech.rsc.plugins.listeners.action.DropListener.class,
            org.nemotech.rsc.plugins.listeners.action.InvActionListener.class,
            org.nemotech.rsc.plugins.listeners.action.InvUseOnGroundItemListener.class,
            org.nemotech.rsc.plugins.listeners.action.InvUseOnItemListener.class,
            org.nemotech.rsc.plugins.listeners.action.InvUseOnNpcListener.class,
            org.nemotech.rsc.plugins.listeners.action.InvUseOnObjectListener.class,
            org.nemotech.rsc.plugins.listeners.action.InvUseOnWallObjectListener.class,
            org.nemotech.rsc.plugins.listeners.action.NpcCommandListener.class,
            org.nemotech.rsc.plugins.listeners.action.ObjectActionListener.class,
            org.nemotech.rsc.plugins.listeners.action.PickupListener.class,
            org.nemotech.rsc.plugins.listeners.action.PlayerAttackNpcListener.class,
            org.nemotech.rsc.plugins.listeners.action.PlayerKilledNpcListener.class,
            org.nemotech.rsc.plugins.listeners.action.PlayerMageNpcListener.class,
            org.nemotech.rsc.plugins.listeners.action.PlayerRangeNpcListener.class,
            org.nemotech.rsc.plugins.listeners.action.TalkToNpcListener.class,
            org.nemotech.rsc.plugins.listeners.action.UnwieldListener.class,
            org.nemotech.rsc.plugins.listeners.action.WallObjectActionListener.class,
            org.nemotech.rsc.plugins.listeners.action.WieldListener.class
        );

        // Executive listeners (interfaces)
        register("org.nemotech.rsc.plugins.listeners.executive",
            org.nemotech.rsc.plugins.listeners.executive.DropExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.InvActionExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.InvUseOnGroundItemExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.InvUseOnItemExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.InvUseOnNpcExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.InvUseOnObjectExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.InvUseOnWallObjectExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.NpcCommandExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.ObjectActionExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.PickupExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.PlayerAttackNpcExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.PlayerKilledNpcExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.PlayerMageNpcExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.PlayerRangeNpcExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.TalkToNpcExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.UnwieldExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.WallObjectActionExecutiveListener.class,
            org.nemotech.rsc.plugins.listeners.executive.WieldExecutiveListener.class
        );

        // commands
        register("org.nemotech.rsc.plugins.commands",
            org.nemotech.rsc.plugins.commands.Admin.class,
            org.nemotech.rsc.plugins.commands.BotCommands.class,
            org.nemotech.rsc.plugins.commands.Graphics.class,
            org.nemotech.rsc.plugins.commands.User.class
        );

        // default_
        register("org.nemotech.rsc.plugins.default_",
            org.nemotech.rsc.plugins.default_.Default.class,
            org.nemotech.rsc.plugins.default_.DoorAction.class,
            org.nemotech.rsc.plugins.default_.Ladders.class
        );

        // items
        register("org.nemotech.rsc.plugins.items",
            org.nemotech.rsc.plugins.items.CombinePotions.class,
            org.nemotech.rsc.plugins.items.Drinkables.class,
            org.nemotech.rsc.plugins.items.Eating.class,
            org.nemotech.rsc.plugins.items.InvAction.class,
            org.nemotech.rsc.plugins.items.InvItemPoisoning.class,
            org.nemotech.rsc.plugins.items.InvUseOnItem.class,
            org.nemotech.rsc.plugins.items.Refill.class,
            org.nemotech.rsc.plugins.items.SleepingBag.class,
            org.nemotech.rsc.plugins.items.SpinningWheel.class
        );

        // minigames
        register("org.nemotech.rsc.plugins.minigames.barcrawl",
            org.nemotech.rsc.plugins.minigames.barcrawl.AlfredGrimhandBarCrawl.class
        );
        register("org.nemotech.rsc.plugins.minigames.blurberrysbar",
            org.nemotech.rsc.plugins.minigames.blurberrysbar.BlurberrysBar.class,
            org.nemotech.rsc.plugins.minigames.blurberrysbar.DrinkHeating.class,
            org.nemotech.rsc.plugins.minigames.blurberrysbar.DrinkMixing.class
        );
        register("org.nemotech.rsc.plugins.minigames.fishingtrawler",
            org.nemotech.rsc.plugins.minigames.fishingtrawler.BailingBucket.class,
            org.nemotech.rsc.plugins.minigames.fishingtrawler.ExitBarrel.class,
            org.nemotech.rsc.plugins.minigames.fishingtrawler.FillHole.class,
            org.nemotech.rsc.plugins.minigames.fishingtrawler.InspectNet.class,
            org.nemotech.rsc.plugins.minigames.fishingtrawler.Murphy.class,
            org.nemotech.rsc.plugins.minigames.fishingtrawler.TrawlerCatch.class
        );
        register("org.nemotech.rsc.plugins.minigames.gnomerestaurant",
            org.nemotech.rsc.plugins.minigames.gnomerestaurant.GnomeCooking.class,
            org.nemotech.rsc.plugins.minigames.gnomerestaurant.GnomeMixing.class,
            org.nemotech.rsc.plugins.minigames.gnomerestaurant.GnomeRestaurant.class,
            org.nemotech.rsc.plugins.minigames.gnomerestaurant.GnomeSlice.class,
            org.nemotech.rsc.plugins.minigames.gnomerestaurant.SwampToads.class
        );

        // misc
        register("org.nemotech.rsc.plugins.misc",
            org.nemotech.rsc.plugins.misc.BananaTree.class,
            org.nemotech.rsc.plugins.misc.Bed.class,
            org.nemotech.rsc.plugins.misc.Casket.class,
            org.nemotech.rsc.plugins.misc.CoalTrucks.class,
            org.nemotech.rsc.plugins.misc.Cow.class,
            org.nemotech.rsc.plugins.misc.CrystalChest.class,
            org.nemotech.rsc.plugins.misc.CutWeb.class,
            org.nemotech.rsc.plugins.misc.DeadTree.class,
            org.nemotech.rsc.plugins.misc.DragonstoneAmulet.class,
            org.nemotech.rsc.plugins.misc.Dummy.class,
            org.nemotech.rsc.plugins.misc.Hopper.class,
            org.nemotech.rsc.plugins.misc.KittenToCat.class,
            org.nemotech.rsc.plugins.misc.LadyOfTheWaves.class,
            org.nemotech.rsc.plugins.misc.LeafyPalmTree.class,
            org.nemotech.rsc.plugins.misc.MagicalPool.class,
            org.nemotech.rsc.plugins.misc.MagicGuildPortals.class,
            org.nemotech.rsc.plugins.misc.MuddyChest.class,
            org.nemotech.rsc.plugins.misc.Necromancer.class,
            org.nemotech.rsc.plugins.misc.Panning.class,
            org.nemotech.rsc.plugins.misc.Pick.class,
            org.nemotech.rsc.plugins.misc.PineappleTree.class,
            org.nemotech.rsc.plugins.misc.RandomObjects.class,
            org.nemotech.rsc.plugins.misc.SalarinTheTwistedMageAI.class,
            org.nemotech.rsc.plugins.misc.SewerValve.class,
            org.nemotech.rsc.plugins.misc.Sheep.class,
            org.nemotech.rsc.plugins.misc.SinisterChest.class,
            org.nemotech.rsc.plugins.misc.SpiritTrees.class,
            org.nemotech.rsc.plugins.misc.StrangeBarrels.class,
            org.nemotech.rsc.plugins.misc.Zamorak.class
        );

        // npcs
        register("org.nemotech.rsc.plugins.npcs",
            org.nemotech.rsc.plugins.npcs.Bankers.class,
            org.nemotech.rsc.plugins.npcs.Certer.class,
            org.nemotech.rsc.plugins.npcs.GeneralStore.class,
            org.nemotech.rsc.plugins.npcs.Man.class,
            org.nemotech.rsc.plugins.npcs.MonkHealer.class
        );
        register("org.nemotech.rsc.plugins.npcs.alkharid",
            org.nemotech.rsc.plugins.npcs.alkharid.BorderGuard.class,
            org.nemotech.rsc.plugins.npcs.alkharid.GemTrader.class,
            org.nemotech.rsc.plugins.npcs.alkharid.KebabSeller.class,
            org.nemotech.rsc.plugins.npcs.alkharid.LouieLegs.class,
            org.nemotech.rsc.plugins.npcs.alkharid.RanaelSkirt.class,
            org.nemotech.rsc.plugins.npcs.alkharid.ShantayPassNpcs.class,
            org.nemotech.rsc.plugins.npcs.alkharid.SilkTrader.class,
            org.nemotech.rsc.plugins.npcs.alkharid.Tanner.class,
            org.nemotech.rsc.plugins.npcs.alkharid.Warrior.class,
            org.nemotech.rsc.plugins.npcs.alkharid.ZekeScimitars.class
        );
        register("org.nemotech.rsc.plugins.npcs.ardougne.east",
            org.nemotech.rsc.plugins.npcs.ardougne.east.ArdougneGeneralShop.class,
            org.nemotech.rsc.plugins.npcs.ardougne.east.BakerMerchant.class,
            org.nemotech.rsc.plugins.npcs.ardougne.east.BartenderFlyingHorseInn.class,
            org.nemotech.rsc.plugins.npcs.ardougne.east.CaptainBarnaby.class,
            org.nemotech.rsc.plugins.npcs.ardougne.east.DoctorOrbon.class,
            org.nemotech.rsc.plugins.npcs.ardougne.east.FurMerchant.class,
            org.nemotech.rsc.plugins.npcs.ardougne.east.GemMerchant.class,
            org.nemotech.rsc.plugins.npcs.ardougne.east.Gunnjorn.class,
            org.nemotech.rsc.plugins.npcs.ardougne.east.KingLathasKeeper.class,
            org.nemotech.rsc.plugins.npcs.ardougne.east.SilkMerchant.class,
            org.nemotech.rsc.plugins.npcs.ardougne.east.SilverMerchant.class,
            org.nemotech.rsc.plugins.npcs.ardougne.east.SpiceMerchant.class,
            org.nemotech.rsc.plugins.npcs.ardougne.east.Zenesha.class
        );
        register("org.nemotech.rsc.plugins.npcs.ardougne.west",
            org.nemotech.rsc.plugins.npcs.ardougne.west.Chadwell.class,
            org.nemotech.rsc.plugins.npcs.ardougne.west.DarkMage.class,
            org.nemotech.rsc.plugins.npcs.ardougne.west.Mourner.class,
            org.nemotech.rsc.plugins.npcs.ardougne.west.SpiritOfScorpius.class
        );
        register("org.nemotech.rsc.plugins.npcs.barbarian",
            org.nemotech.rsc.plugins.npcs.barbarian.Barbarians.class,
            org.nemotech.rsc.plugins.npcs.barbarian.Oracle.class,
            org.nemotech.rsc.plugins.npcs.barbarian.PeksaHelmets.class
        );
        register("org.nemotech.rsc.plugins.npcs.brimhaven",
            org.nemotech.rsc.plugins.npcs.brimhaven.AlfonseTheWaiter.class,
            org.nemotech.rsc.plugins.npcs.brimhaven.BoatFromBrimhaven.class,
            org.nemotech.rsc.plugins.npcs.brimhaven.BrimHavenBartender.class,
            org.nemotech.rsc.plugins.npcs.brimhaven.CharlieTheCook.class,
            org.nemotech.rsc.plugins.npcs.brimhaven.DavonShop.class
        );
        register("org.nemotech.rsc.plugins.npcs.catherby",
            org.nemotech.rsc.plugins.npcs.catherby.ArheinGeneralShop.class,
            org.nemotech.rsc.plugins.npcs.catherby.CandleMakerShop.class,
            org.nemotech.rsc.plugins.npcs.catherby.Chef.class,
            org.nemotech.rsc.plugins.npcs.catherby.HarrysFishingShack.class,
            org.nemotech.rsc.plugins.npcs.catherby.HicktonArcheryShop.class
        );
        register("org.nemotech.rsc.plugins.npcs.draynor",
            org.nemotech.rsc.plugins.npcs.draynor.Aggie.class,
            org.nemotech.rsc.plugins.npcs.draynor.Klarense.class,
            org.nemotech.rsc.plugins.npcs.draynor.Ned.class
        );
        register("org.nemotech.rsc.plugins.npcs.dwarvenmine",
            org.nemotech.rsc.plugins.npcs.dwarvenmine.Boot.class,
            org.nemotech.rsc.plugins.npcs.dwarvenmine.Drogo.class,
            org.nemotech.rsc.plugins.npcs.dwarvenmine.NurmofPickaxe.class
        );
        register("org.nemotech.rsc.plugins.npcs.edgeville",
            org.nemotech.rsc.plugins.npcs.edgeville.BrotherJered.class,
            org.nemotech.rsc.plugins.npcs.edgeville.OziachsRunePlateShop.class
        );
        register("org.nemotech.rsc.plugins.npcs.entrana",
            org.nemotech.rsc.plugins.npcs.entrana.FrincosVialShopEntrana.class,
            org.nemotech.rsc.plugins.npcs.entrana.HighPriestOfEntrana.class
        );
        register("org.nemotech.rsc.plugins.npcs.falador",
            org.nemotech.rsc.plugins.npcs.falador.Barmaid.class,
            org.nemotech.rsc.plugins.npcs.falador.Bartender.class,
            org.nemotech.rsc.plugins.npcs.falador.CassieShields.class,
            org.nemotech.rsc.plugins.npcs.falador.FlynnMaces.class,
            org.nemotech.rsc.plugins.npcs.falador.HerquinGems.class,
            org.nemotech.rsc.plugins.npcs.falador.MakeOverMage.class,
            org.nemotech.rsc.plugins.npcs.falador.WaynesChains.class,
            org.nemotech.rsc.plugins.npcs.falador.WysonTheGardener.class
        );
        register("org.nemotech.rsc.plugins.npcs.grandtree",
            org.nemotech.rsc.plugins.npcs.grandtree.Blurberry.class,
            org.nemotech.rsc.plugins.npcs.grandtree.Gnomes.class,
            org.nemotech.rsc.plugins.npcs.grandtree.GnomeWaiter.class,
            org.nemotech.rsc.plugins.npcs.grandtree.Gulluck.class,
            org.nemotech.rsc.plugins.npcs.grandtree.HeckelFunchGroceries.class,
            org.nemotech.rsc.plugins.npcs.grandtree.HudoGlenfadGroceries.class,
            org.nemotech.rsc.plugins.npcs.grandtree.Rometti.class
        );
        register("org.nemotech.rsc.plugins.npcs.gutanoth",
            org.nemotech.rsc.plugins.npcs.gutanoth.GrudsHerblawStall.class,
            org.nemotech.rsc.plugins.npcs.gutanoth.OgreTrader.class
        );
        register("org.nemotech.rsc.plugins.npcs.hemenster",
            org.nemotech.rsc.plugins.npcs.hemenster.FishingGuildGeneralShop.class,
            org.nemotech.rsc.plugins.npcs.hemenster.MasterFisher.class
        );
        register("org.nemotech.rsc.plugins.npcs.karamja",
            org.nemotech.rsc.plugins.npcs.karamja.BoatFromKaramja.class,
            org.nemotech.rsc.plugins.npcs.karamja.ZamboRum.class
        );
        register("org.nemotech.rsc.plugins.npcs.khazard",
            org.nemotech.rsc.plugins.npcs.khazard.Docky.class,
            org.nemotech.rsc.plugins.npcs.khazard.FishingTrawlerGeneralStore.class,
            org.nemotech.rsc.plugins.npcs.khazard.KhazardBartender.class
        );
        register("org.nemotech.rsc.plugins.npcs.lostcity",
            org.nemotech.rsc.plugins.npcs.lostcity.FairyLunderwin.class,
            org.nemotech.rsc.plugins.npcs.lostcity.FairyQueen.class,
            org.nemotech.rsc.plugins.npcs.lostcity.Irksol.class,
            org.nemotech.rsc.plugins.npcs.lostcity.Jakut.class
        );
        register("org.nemotech.rsc.plugins.npcs.lumbridge",
            org.nemotech.rsc.plugins.npcs.lumbridge.BobsAxes.class,
            org.nemotech.rsc.plugins.npcs.lumbridge.DukeOfLumbridge.class,
            org.nemotech.rsc.plugins.npcs.lumbridge.Hans.class,
            org.nemotech.rsc.plugins.npcs.lumbridge.Priest.class,
            org.nemotech.rsc.plugins.npcs.lumbridge.Urhney.class
        );
        register("org.nemotech.rsc.plugins.npcs.portsarim",
            org.nemotech.rsc.plugins.npcs.portsarim.BettysMagicEmporium.class,
            org.nemotech.rsc.plugins.npcs.portsarim.BriansBattleAxes.class,
            org.nemotech.rsc.plugins.npcs.portsarim.GerrantsFishingGear.class,
            org.nemotech.rsc.plugins.npcs.portsarim.GrumsGoldShop.class,
            org.nemotech.rsc.plugins.npcs.portsarim.MonkOfEntrana.class,
            org.nemotech.rsc.plugins.npcs.portsarim.NedInShip.class,
            org.nemotech.rsc.plugins.npcs.portsarim.PortSarimSailor.class,
            org.nemotech.rsc.plugins.npcs.portsarim.WormBrain.class,
            org.nemotech.rsc.plugins.npcs.portsarim.WydinsGrocery.class
        );
        register("org.nemotech.rsc.plugins.npcs.rimmington",
            org.nemotech.rsc.plugins.npcs.rimmington.CraftingEquipmentShops.class,
            org.nemotech.rsc.plugins.npcs.rimmington.MasterCrafter.class
        );
        register("org.nemotech.rsc.plugins.npcs.seers",
            org.nemotech.rsc.plugins.npcs.seers.BrotherGalahad.class,
            org.nemotech.rsc.plugins.npcs.seers.SeersBartender.class,
            org.nemotech.rsc.plugins.npcs.seers.Stankers.class
        );
        register("org.nemotech.rsc.plugins.npcs.shilo",
            org.nemotech.rsc.plugins.npcs.shilo.CartDriver.class,
            org.nemotech.rsc.plugins.npcs.shilo.Fernahei.class,
            org.nemotech.rsc.plugins.npcs.shilo.Jiminua.class,
            org.nemotech.rsc.plugins.npcs.shilo.JungleForester.class,
            org.nemotech.rsc.plugins.npcs.shilo.Kaleb.class,
            org.nemotech.rsc.plugins.npcs.shilo.Obli.class,
            org.nemotech.rsc.plugins.npcs.shilo.Serevel.class,
            org.nemotech.rsc.plugins.npcs.shilo.Yanni.class,
            org.nemotech.rsc.plugins.npcs.shilo.Yohnus.class
        );
        register("org.nemotech.rsc.plugins.npcs.taverly",
            org.nemotech.rsc.plugins.npcs.taverly.GaiusTwoHandlerShop.class,
            org.nemotech.rsc.plugins.npcs.taverly.HelemosShop.class,
            org.nemotech.rsc.plugins.npcs.taverly.JatixHerblawShop.class,
            org.nemotech.rsc.plugins.npcs.taverly.Lady.class
        );
        register("org.nemotech.rsc.plugins.npcs.tutorial",
            org.nemotech.rsc.plugins.npcs.tutorial.BankAssistant.class,
            org.nemotech.rsc.plugins.npcs.tutorial.Boatman.class,
            org.nemotech.rsc.plugins.npcs.tutorial.CombatInstructor.class,
            org.nemotech.rsc.plugins.npcs.tutorial.CommunityInstructor.class,
            org.nemotech.rsc.plugins.npcs.tutorial.ControlsGuide.class,
            org.nemotech.rsc.plugins.npcs.tutorial.CookingInstructor.class,
            org.nemotech.rsc.plugins.npcs.tutorial.FatigueExpert.class,
            org.nemotech.rsc.plugins.npcs.tutorial.FinancialAdvisor.class,
            org.nemotech.rsc.plugins.npcs.tutorial.FishingInstructor.class,
            org.nemotech.rsc.plugins.npcs.tutorial.Guide.class,
            org.nemotech.rsc.plugins.npcs.tutorial.MagicInstructor.class,
            org.nemotech.rsc.plugins.npcs.tutorial.MiningInstructor.class,
            org.nemotech.rsc.plugins.npcs.tutorial.QuestAdvisor.class,
            org.nemotech.rsc.plugins.npcs.tutorial.WildernessGuide.class
        );
        register("org.nemotech.rsc.plugins.npcs.varrock",
            org.nemotech.rsc.plugins.npcs.varrock.Apothecary.class,
            org.nemotech.rsc.plugins.npcs.varrock.AuburysRunes.class,
            org.nemotech.rsc.plugins.npcs.varrock.Baraek.class,
            org.nemotech.rsc.plugins.npcs.varrock.Bartender.class,
            org.nemotech.rsc.plugins.npcs.varrock.ChampionsGuild.class,
            org.nemotech.rsc.plugins.npcs.varrock.Curator.class,
            org.nemotech.rsc.plugins.npcs.varrock.DancingDonkeyInnBartender.class,
            org.nemotech.rsc.plugins.npcs.varrock.Guildmaster.class,
            org.nemotech.rsc.plugins.npcs.varrock.HeadChef.class,
            org.nemotech.rsc.plugins.npcs.varrock.HorvikTheArmourer.class,
            org.nemotech.rsc.plugins.npcs.varrock.JollyBoarInnBartender.class,
            org.nemotech.rsc.plugins.npcs.varrock.King.class,
            org.nemotech.rsc.plugins.npcs.varrock.LowesArchery.class,
            org.nemotech.rsc.plugins.npcs.varrock.ManPhoenix.class,
            org.nemotech.rsc.plugins.npcs.varrock.Reldo.class,
            org.nemotech.rsc.plugins.npcs.varrock.Tailor.class,
            org.nemotech.rsc.plugins.npcs.varrock.TeaSeller.class,
            org.nemotech.rsc.plugins.npcs.varrock.ThessaliasClothes.class,
            org.nemotech.rsc.plugins.npcs.varrock.Thrander.class,
            org.nemotech.rsc.plugins.npcs.varrock.Tramp.class,
            org.nemotech.rsc.plugins.npcs.varrock.VarrockSwords.class,
            org.nemotech.rsc.plugins.npcs.varrock.ZaffsStaffs.class
        );
        register("org.nemotech.rsc.plugins.npcs.wilderness.banditcamp",
            org.nemotech.rsc.plugins.npcs.wilderness.banditcamp.FatTony.class,
            org.nemotech.rsc.plugins.npcs.wilderness.banditcamp.Noterazzo.class
        );
        register("org.nemotech.rsc.plugins.npcs.wilderness.mage_arena",
            org.nemotech.rsc.plugins.npcs.wilderness.mage_arena.Chamber_Guardian.class,
            org.nemotech.rsc.plugins.npcs.wilderness.mage_arena.Gundai.class,
            org.nemotech.rsc.plugins.npcs.wilderness.mage_arena.Lundail.class
        );
        register("org.nemotech.rsc.plugins.npcs.yanille",
            org.nemotech.rsc.plugins.npcs.yanille.BartenderDragonInn.class,
            org.nemotech.rsc.plugins.npcs.yanille.ColonelRadick.class,
            org.nemotech.rsc.plugins.npcs.yanille.Frenita.class,
            org.nemotech.rsc.plugins.npcs.yanille.MagicStoreOwner.class,
            org.nemotech.rsc.plugins.npcs.yanille.SidneySmith.class,
            org.nemotech.rsc.plugins.npcs.yanille.SigbertTheAdventurer.class,
            org.nemotech.rsc.plugins.npcs.yanille.WizardFrumscone.class
        );

        // quests.free
        register("org.nemotech.rsc.plugins.quests.free",
            org.nemotech.rsc.plugins.quests.free.BlackKnightsFortress.class,
            org.nemotech.rsc.plugins.quests.free.CooksAssistant.class,
            org.nemotech.rsc.plugins.quests.free.DemonSlayer.class,
            org.nemotech.rsc.plugins.quests.free.Dorics.class,
            org.nemotech.rsc.plugins.quests.free.DragonSlayer.class,
            org.nemotech.rsc.plugins.quests.free.ErnestTheChicken.class,
            org.nemotech.rsc.plugins.quests.free.GoblinDiplomacy.class,
            org.nemotech.rsc.plugins.quests.free.ImpCatcher.class,
            org.nemotech.rsc.plugins.quests.free.KnightsSword.class,
            org.nemotech.rsc.plugins.quests.free.PiratesTreasure.class,
            org.nemotech.rsc.plugins.quests.free.PrinceAliRescue.class,
            org.nemotech.rsc.plugins.quests.free.RomeoAndJuliet.class,
            org.nemotech.rsc.plugins.quests.free.SheepShearer.class,
            org.nemotech.rsc.plugins.quests.free.ShieldOfArrav.class,
            org.nemotech.rsc.plugins.quests.free.TheRestlessGhost.class,
            org.nemotech.rsc.plugins.quests.free.VampireSlayer.class,
            org.nemotech.rsc.plugins.quests.free.WitchesPotion.class
        );

        // quests.members
        register("org.nemotech.rsc.plugins.quests.members",
            org.nemotech.rsc.plugins.quests.members.Biohazard.class,
            org.nemotech.rsc.plugins.quests.members.ClockTower.class,
            org.nemotech.rsc.plugins.quests.members.DruidicRitual.class,
            org.nemotech.rsc.plugins.quests.members.DwarfCannon.class,
            org.nemotech.rsc.plugins.quests.members.FamilyCrest.class,
            org.nemotech.rsc.plugins.quests.members.FightArena.class,
            org.nemotech.rsc.plugins.quests.members.FishingContest.class,
            org.nemotech.rsc.plugins.quests.members.GertrudesCat.class,
            org.nemotech.rsc.plugins.quests.members.HazeelCult.class,
            org.nemotech.rsc.plugins.quests.members.HerosQuest.class,
            org.nemotech.rsc.plugins.quests.members.JunglePotion.class,
            org.nemotech.rsc.plugins.quests.members.LostCity.class,
            org.nemotech.rsc.plugins.quests.members.MerlinsCrystal.class,
            org.nemotech.rsc.plugins.quests.members.MonksFriend.class,
            org.nemotech.rsc.plugins.quests.members.MurderMystery.class,
            org.nemotech.rsc.plugins.quests.members.Observatory.class,
            org.nemotech.rsc.plugins.quests.members.PlagueCity.class,
            org.nemotech.rsc.plugins.quests.members.ScorpionCatcher.class,
            org.nemotech.rsc.plugins.quests.members.SeaSlug.class,
            org.nemotech.rsc.plugins.quests.members.SheepHerder.class,
            org.nemotech.rsc.plugins.quests.members.TempleOfIkov.class,
            org.nemotech.rsc.plugins.quests.members.TheHolyGrail.class,
            org.nemotech.rsc.plugins.quests.members.TreeGnomeVillage.class,
            org.nemotech.rsc.plugins.quests.members.TribalTotem.class,
            org.nemotech.rsc.plugins.quests.members.Waterfall.class,
            org.nemotech.rsc.plugins.quests.members.WitchesHouse.class
        );

        // quests.members.digsite
        register("org.nemotech.rsc.plugins.quests.members.digsite",
            org.nemotech.rsc.plugins.quests.members.digsite.DigsiteDigAreas.class,
            org.nemotech.rsc.plugins.quests.members.digsite.DigsiteExaminer.class,
            org.nemotech.rsc.plugins.quests.members.digsite.DigsiteExpert.class,
            org.nemotech.rsc.plugins.quests.members.digsite.DigsiteGuide.class,
            org.nemotech.rsc.plugins.quests.members.digsite.DigsiteMiscs.class,
            org.nemotech.rsc.plugins.quests.members.digsite.DigsiteObjects.class,
            org.nemotech.rsc.plugins.quests.members.digsite.DigsiteStudents.class,
            org.nemotech.rsc.plugins.quests.members.digsite.DigsiteWinch.class,
            org.nemotech.rsc.plugins.quests.members.digsite.DigsiteWorkman.class
        );

        // quests.members.grandtree
        register("org.nemotech.rsc.plugins.quests.members.grandtree",
            org.nemotech.rsc.plugins.quests.members.grandtree.GnomeGlider.class,
            org.nemotech.rsc.plugins.quests.members.grandtree.GrandTree.class
        );

        // quests.members.shilovillage
        register("org.nemotech.rsc.plugins.quests.members.shilovillage",
            org.nemotech.rsc.plugins.quests.members.shilovillage.ShiloVillageMosolRei.class,
            org.nemotech.rsc.plugins.quests.members.shilovillage.ShiloVillageNazastarool.class,
            org.nemotech.rsc.plugins.quests.members.shilovillage.ShiloVillageObjects.class,
            org.nemotech.rsc.plugins.quests.members.shilovillage.ShiloVillageTombDolmen.class,
            org.nemotech.rsc.plugins.quests.members.shilovillage.ShiloVillageTrufitusInvUse.class,
            org.nemotech.rsc.plugins.quests.members.shilovillage.ShiloVillageUtils.class
        );

        // quests.members.touristtrap
        register("org.nemotech.rsc.plugins.quests.members.touristtrap",
            org.nemotech.rsc.plugins.quests.members.touristtrap.TouristTrap.class,
            org.nemotech.rsc.plugins.quests.members.touristtrap.Tourist_Trap_Mechanism.class
        );

        // quests.members.watchtower
        register("org.nemotech.rsc.plugins.quests.members.watchtower",
            org.nemotech.rsc.plugins.quests.members.watchtower.WatchTowerDialogues.class,
            org.nemotech.rsc.plugins.quests.members.watchtower.WatchTowerGateObstacles.class,
            org.nemotech.rsc.plugins.quests.members.watchtower.WatchTowerGorad.class,
            org.nemotech.rsc.plugins.quests.members.watchtower.WatchTowerMechanism.class,
            org.nemotech.rsc.plugins.quests.members.watchtower.WatchTowerObstacles.class,
            org.nemotech.rsc.plugins.quests.members.watchtower.WatchTowerShaman.class
        );

        // quests.members.undergroundpass
        register("org.nemotech.rsc.plugins.quests.members.undergroundpass.mechanism",
            org.nemotech.rsc.plugins.quests.members.undergroundpass.mechanism.UndergroundPassMechanismMap1.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.mechanism.UndergroundPassMechanismMap2.class
        );
        register("org.nemotech.rsc.plugins.quests.members.undergroundpass.npcs",
            org.nemotech.rsc.plugins.quests.members.undergroundpass.npcs.UndergroundPassDemons.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.npcs.UndergroundPassDwarfs.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.npcs.UndergroundPassIban.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.npcs.UndergroundPassIbanDisciple.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.npcs.UndergroundPassKalrag.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.npcs.UndergroundPassKardiaTheWitch.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.npcs.UndergroundPassKoftik.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.npcs.UndergroundPassPaladin.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.npcs.UndergroundPassSlaves.class
        );
        register("org.nemotech.rsc.plugins.quests.members.undergroundpass.obstacles",
            org.nemotech.rsc.plugins.quests.members.undergroundpass.obstacles.UndergroundPassAgilityObstacles.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.obstacles.UndergroundPassDungeonFloor.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.obstacles.UndergroundPassObstaclesMap1.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.obstacles.UndergroundPassObstaclesMap2.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.obstacles.UndergroundPassObstaclesMap3.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.obstacles.UndergroundPassOrbs.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.obstacles.UndergroundPassPuzzle.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.obstacles.UndergroundPassSmearDollOfIban.class,
            org.nemotech.rsc.plugins.quests.members.undergroundpass.obstacles.UndergroundPassWell.class
        );

        // quests.members.legendsquest
        register("org.nemotech.rsc.plugins.quests.members.legendsquest.mechanism",
            org.nemotech.rsc.plugins.quests.members.legendsquest.mechanism.LegendsQuestBullRoarer.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.mechanism.LegendsQuestInvAction.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.mechanism.LegendsQuestMapJungle.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.mechanism.LegendsQuestOnDrop.class
        );
        register("org.nemotech.rsc.plugins.quests.members.legendsquest.npcs",
            org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.LegendsQuestEchnedZekin.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.LegendsQuestGuildGuard.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.LegendsQuestGujuo.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.LegendsQuestIrvigSenay.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.LegendsQuestNezikchened.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.LegendsQuestRanalphDevere.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.LegendsQuestSanTojalon.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.LegendsQuestSirRadimusErkle.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.LegendsQuestUngadulu.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.LegendsQuestViyeldi.class
        );
        register("org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.shop",
            org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.shop.Fionella.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.npcs.shop.SiegfriedErkel.class
        );
        register("org.nemotech.rsc.plugins.quests.members.legendsquest.obstacles",
            org.nemotech.rsc.plugins.quests.members.legendsquest.obstacles.LegendsQuestCaveAgility.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.obstacles.LegendsQuestDarkMetalGate.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.obstacles.LegendsQuestGameObjects.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.obstacles.LegendsQuestGates.class,
            org.nemotech.rsc.plugins.quests.members.legendsquest.obstacles.LegendsQuestWallObjects.class
        );

        // skills
        register("org.nemotech.rsc.plugins.skills",
            org.nemotech.rsc.plugins.skills.BattlestaffCrafting.class,
            org.nemotech.rsc.plugins.skills.Crafting.class,
            org.nemotech.rsc.plugins.skills.Firemaking.class,
            org.nemotech.rsc.plugins.skills.Fishing.class,
            org.nemotech.rsc.plugins.skills.Fletching.class,
            org.nemotech.rsc.plugins.skills.GemMining.class,
            org.nemotech.rsc.plugins.skills.Herblaw.class,
            org.nemotech.rsc.plugins.skills.InventoryCooking.class,
            org.nemotech.rsc.plugins.skills.Mining.class,
            org.nemotech.rsc.plugins.skills.ObjectCooking.class,
            org.nemotech.rsc.plugins.skills.Prayer.class,
            org.nemotech.rsc.plugins.skills.Smelting.class,
            org.nemotech.rsc.plugins.skills.Smithing.class,
            org.nemotech.rsc.plugins.skills.Thieving.class,
            org.nemotech.rsc.plugins.skills.Woodcutting.class
        );
        register("org.nemotech.rsc.plugins.skills.agility",
            org.nemotech.rsc.plugins.skills.agility.AgilityShortcuts.class,
            org.nemotech.rsc.plugins.skills.agility.AgilityUtils.class,
            org.nemotech.rsc.plugins.skills.agility.BarbarianAgilityCourse.class,
            org.nemotech.rsc.plugins.skills.agility.GnomeAgilityCourse.class,
            org.nemotech.rsc.plugins.skills.agility.WildernessAgilityCourse.class
        );

        // Action handlers (org.nemotech.rsc.client.action.impl)
        register("org.nemotech.rsc.client.action.impl",
            org.nemotech.rsc.client.action.impl.AppearanceHandler.class,
            org.nemotech.rsc.client.action.impl.BankHandler.class,
            org.nemotech.rsc.client.action.impl.CastHandler.class,
            org.nemotech.rsc.client.action.impl.CommandHandler.class,
            org.nemotech.rsc.client.action.impl.DoorActionHandler.class,
            org.nemotech.rsc.client.action.impl.DropHandler.class,
            org.nemotech.rsc.client.action.impl.InventoryActionHandler.class,
            org.nemotech.rsc.client.action.impl.InventoryUseOnDoorHandler.class,
            org.nemotech.rsc.client.action.impl.InventoryUseOnGroundItemHandler.class,
            org.nemotech.rsc.client.action.impl.InventoryUseOnItemHandler.class,
            org.nemotech.rsc.client.action.impl.InventoryUseOnNPCHandler.class,
            org.nemotech.rsc.client.action.impl.InventoryUseOnObjectHandler.class,
            org.nemotech.rsc.client.action.impl.LoginHandler.class,
            org.nemotech.rsc.client.action.impl.LogoutHandler.class,
            org.nemotech.rsc.client.action.impl.NPCHandler.class,
            org.nemotech.rsc.client.action.impl.ObjectActionHandler.class,
            org.nemotech.rsc.client.action.impl.OptionHandler.class,
            org.nemotech.rsc.client.action.impl.PickupHandler.class,
            org.nemotech.rsc.client.action.impl.PrayerHandler.class,
            org.nemotech.rsc.client.action.impl.RegisterHandler.class,
            org.nemotech.rsc.client.action.impl.ShopHandler.class,
            org.nemotech.rsc.client.action.impl.SleepHandler.class,
            org.nemotech.rsc.client.action.impl.WalkHandler.class,
            org.nemotech.rsc.client.action.impl.WieldHandler.class
        );

        // Update handlers (org.nemotech.rsc.client.update.impl)
        register("org.nemotech.rsc.client.update.impl",
            org.nemotech.rsc.client.update.impl.ItemUpdater.class,
            org.nemotech.rsc.client.update.impl.MiscUpdater.class,
            org.nemotech.rsc.client.update.impl.NPCUpdater.class,
            org.nemotech.rsc.client.update.impl.ObjectUpdater.class,
            org.nemotech.rsc.client.update.impl.PlayerUpdater.class,
            org.nemotech.rsc.client.update.impl.WallUpdater.class
        );
    }

    private static void register(String packageName, Class<?>... classes) {
        packageClasses.put(packageName, Arrays.asList(classes));
    }

    public static List<Class<?>> getClasses(String packageName) {
        List<Class<?>> result = packageClasses.get(packageName);
        return result != null ? new ArrayList<>(result) : new ArrayList<>();
    }

    public static boolean hasPackage(String packageName) {
        return packageClasses.containsKey(packageName);
    }
}

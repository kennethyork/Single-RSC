const highscoreSearch = document.getElementById("highscoreSearch");
const highscoreLookup = document.getElementById("highscoreLookup");
const highscoreTableBody = document.querySelector("#highscoreTable tbody");
let highscoreRows = Array.from(document.querySelectorAll("#highscoreTable tbody tr"));
const highscoreProfile = document.getElementById("highscoreProfile");
const highscoreEmpty = document.getElementById("highscoreEmpty");
const profileTitle = document.getElementById("profileTitle");
const profileMeta = document.getElementById("profileMeta");
const profilePermalink = document.getElementById("profilePermalink");
const profileRank = document.getElementById("profileRank");
const profileLevel = document.getElementById("profileLevel");
const profileXp = document.getElementById("profileXp");
const profileScore = document.getElementById("profileScore");
const profileSkillsBody = document.querySelector("#profileSkills tbody");

const skills = [
  "Overall",
  "Fighting",
  "Ranged",
  "Prayer",
  "Magic",
  "Cooking",
  "Woodcutting",
  "Fletching",
  "Fishing",
  "Firemaking",
  "Crafting",
  "Smithing",
  "Mining",
  "Herblaw",
  "Agility",
  "Thieving",
];

let highscorePlayers = highscoreRows.map((row) => {
  const cells = Array.from(row.children).map((cell) => cell.textContent.trim());
  return {
    rank: Number(cells[0].replace(/,/g, "")),
    name: cells[1],
    role: cells[2],
    clan: cells[3],
    status: cells[4],
    goal: cells[5],
    level: Number(cells[6].replace(/,/g, "")),
    xpRate: cells[7],
    score: Number(cells[8].replace(/,/g, "")),
    coins: Number(cells[9].replace(/,/g, "")),
    trades: Number(cells[10].replace(/,/g, "")),
    kills: Number(cells[11].replace(/,/g, "")),
    row,
  };
});

const generatedNames = [
  "AlKharidAli", "AshRunner", "AuburyAlt", "BarbBrian", "BlackArmBob", "BlueWizard", "BronzeBelle", "CabbageCam",
  "CamelotCole", "CastleCarl", "CaveCrawler", "ChaosCat", "CookedCod", "CopperCora", "CowhideCal", "DarkWizard",
  "DraynorDrew", "DuelDaisy", "DwarfDale", "EastVarrock", "EdgeEmma", "EntranaEli", "FaladorFinn", "FireRune",
  "FishingFred", "ForestFletch", "GoblinGary", "GoldGrace", "GreenDragon", "GuardGlen", "GuthixGail", "HerbHarry",
  "IceMountain", "IronIvy", "KaramjaKai", "KnightNora", "LavaMaze", "LeatherLee", "LongbowLou", "LumbyLia",
  "MagicMilo", "MapleMona", "MithMack", "MonkMara", "MossMarty", "Mudskipper", "NatureNed", "NorthArdy",
  "OakOscar", "OreOlive", "PaladinPam", "PrayerPaul", "RangeRalph", "RatRicky", "RedBeard", "RuneRuby",
  "SaradominSid", "ScimitarSue", "SeersSally", "ShantaySid", "SilverSue", "SkullSam", "SmithSonia", "SouthGate",
  "SpiderStan", "SteelStacy", "SwordSeth", "ThieveTheo", "TinTara", "TroutTina", "VarrockVal", "WestArdy",
  "WhiteKnight", "WillowWade", "WizardWes", "YewYara", "ZamorakZed", "AgilityAna", "ArrowArt", "BankBeth",
  "BeerBarry", "BigBones", "BrassKey", "CakeClara", "CoalCasey", "CraftCleo", "DeathDune", "EdgeElla",
  "FeatherFox", "FletchFern", "GhostGreg", "HillHank", "JailJimmy", "KiteKara", "LobsterLiz", "MineMolly",
  "NeedleNia", "PickPete", "RawTuna", "RingRosa", "ShrimpShay", "SilkSasha", "SmeltSean", "SpinFlax",
  "StaffTess", "TannerTom", "UncutUma", "VialVera", "WildWill", "WineWalt", "WolfWynn", "ZaffZane",
  "ArcherAmy", "BattleBen", "CatherCarl", "DruidDana", "EssenceEd", "FalconFia", "GnomeGina", "HunterHal",
  "IslandIan", "JollyJade", "KebabKen", "LesserLeo", "MarketMae", "NettleNash", "OgreOwen", "PiratePip",
  "QuartzQuin", "RangerRen", "SailorSky", "TavernTim", "UndeadUna", "VannakaVic", "WanderWren", "XbowXan",
  "YanilleYin", "ZealZoe", "AmberAsh", "BriarBo", "CinderCy", "DaggerDee", "ElmEvan", "FurnaceFay",
  "GraniteGil", "HarpoonHex", "IvoryIke", "JadeJon", "KeeperKay", "LanternLex", "MarbleMay", "NobleNix",
  "OnyxOllie", "PebblePaz", "QuestQuill", "RopeRae", "SapphireSol", "TempleTia", "UrnUri", "ValeVik",
  "WheatWes", "XeniaX", "YewYork", "ZincZara",
];

let liveHighscoresLoaded = false;

function statusMarkup(status) {
  return `<span class="status ${status.toLowerCase()}">${status}</span>`;
}

function makeGeneratedPlayer(rank) {
  const roles = ["Skiller", "Monster hunter", "PKer"];
  const clans = ["Bank crew", "Blue moon", "Red capes", "Wild guard", "Iron bank", "Oak table"];
  const goals = ["skilling", "build bank", "upgrade gear", "pk trip"];
  const role = roles[rank % roles.length];
  const status = rank % 4 === 0 ? "Offline" : "Online";
  return {
    rank,
    name: generatedNames[(rank - 31) % generatedNames.length],
    role,
    clan: clans[rank % clans.length],
    status,
    goal: role === "PKer" ? "pk trip" : goals[rank % goals.length],
    level: Math.max(3, 62 - Math.floor(rank / 4) + (rank % 7)),
    xpRate: [1, 1, 2, 3, 5][rank % 5] + "x",
    score: Math.max(250, 2050 - rank * 7 + (rank % 9) * 18),
    coins: Math.max(150, 5100 - rank * 13 + (rank % 11) * 120),
    trades: Math.max(0, 22 - Math.floor(rank / 10) + (rank % 3)),
    kills: role === "PKer" ? Math.max(0, 9 - Math.floor(rank / 20)) : rank % 17 === 0 ? 1 : 0,
  };
}

function appendPlayerRow(player) {
  const row = document.createElement("tr");
  row.innerHTML = `
    <td>${player.rank}</td>
    <td>${player.name}</td>
    <td>${player.role}</td>
    <td>${player.clan}</td>
    <td>${statusMarkup(player.status)}</td>
    <td>${player.goal}</td>
    <td>${player.level}</td>
    <td>${player.xpRate}</td>
    <td>${formatNumber(player.score)}</td>
    <td>${formatNumber(player.coins)}</td>
    <td>${formatNumber(player.trades)}</td>
    <td>${formatNumber(player.kills)}</td>
  `;
  highscoreTableBody.appendChild(row);
  player.row = row;
  highscorePlayers.push(player);
}

if (highscoreTableBody) {
  [
    { name: "kenyyhy", status: "Online", level: 42, score: 3200, coins: 9200, trades: 8, kills: 0, xpRate: "1x" },
    { name: "kenyy", status: "Offline", level: 37, score: 2850, coins: 7800, trades: 6, kills: 0, xpRate: "1x" },
    { name: "kenyyhu", status: "Offline", level: 31, score: 2400, coins: 6400, trades: 5, kills: 0, xpRate: "1x" },
    { name: "almostdead", status: "Offline", level: 28, score: 2100, coins: 5100, trades: 4, kills: 1, xpRate: "1x" },
    { name: "almostead", status: "Offline", level: 24, score: 1850, coins: 4300, trades: 3, kills: 0, xpRate: "1x" },
    { name: "almosdead", status: "Offline", level: 21, score: 1600, coins: 3600, trades: 2, kills: 0, xpRate: "1x" },
    { name: "hcalmostdead", status: "Offline", level: 18, score: 1400, coins: 2900, trades: 1, kills: 0, xpRate: "1x" },
    { name: "kenyyhy]", status: "Offline", level: 12, score: 950, coins: 1700, trades: 0, kills: 0, xpRate: "1x" },
  ].forEach((character) => {
    appendPlayerRow({
      rank: highscorePlayers.length + 1,
      role: character.name.startsWith("hc") ? "Hardcore player" : "Player",
      clan: "Your characters",
      goal: character.status === "Online" ? "currently playing" : "saved character",
      ...character,
    });
  });

  for (let rank = highscorePlayers.length + 1; rank <= 200; rank += 1) {
    appendPlayerRow(makeGeneratedPlayer(rank));
  }
  highscoreRows = Array.from(document.querySelectorAll("#highscoreTable tbody tr"));
}

function playerFromLive(entry) {
  return {
    rank: Number(entry.rank) || 0,
    name: entry.name || "unknown",
    role: entry.role || entry.type || "Player",
    clan: entry.clan || "",
    status: entry.status || "Offline",
    goal: entry.goal || "",
    level: Number(entry.level) || 1,
    xpRate: entry.xpRate || "1x",
    xp: Number(entry.xp) || 0,
    score: Number(entry.score) || 0,
    coins: Number(entry.coins) || 0,
    trades: Number(entry.trades) || 0,
    kills: Number(entry.kills) || 0,
    skills: Array.isArray(entry.skills) ? entry.skills.map((skill) => ({
      skill: skill.skill || skill.name || "",
      level: Number(skill.level) || 1,
      xp: Number(skill.xp) || 0,
    })).filter((skill) => skill.skill) : null,
  };
}

function rebuildHighscores(players) {
  if (!highscoreTableBody || !Array.isArray(players) || players.length === 0) {
    return;
  }
  highscoreTableBody.innerHTML = "";
  highscorePlayers = [];
  players.forEach((entry, index) => {
    const player = playerFromLive(entry);
    if (!player.rank) {
      player.rank = index + 1;
    }
    appendPlayerRow(player);
  });
  highscoreRows = Array.from(document.querySelectorAll("#highscoreTable tbody tr"));
  bindHighscoreRows();
  liveHighscoresLoaded = true;
}

function loadLiveHighscores() {
  fetch("highscores.json", { cache: "no-store" })
    .then((response) => {
      if (!response.ok) {
        throw new Error("No highscores export");
      }
      return response.json();
    })
    .then((data) => {
      if (data && Array.isArray(data.players)) {
        rebuildHighscores(data.players);
      }
    })
    .catch(() => {
      if (!liveHighscoresLoaded) {
        liveHighscoresLoaded = false;
      }
    });
}

function formatNumber(value) {
  return value.toLocaleString("en-US");
}

function skillRowsFor(player) {
  const totalLevel = Math.max(16, player.level + Math.floor(player.score / 900));
  const exportedXp = Number(player.xp) || 0;
  const totalXp = exportedXp > 0
    ? exportedXp
    : Math.max(1000, player.score * Number(player.xpRate.replace("x", "")) + player.coins + player.trades * 90 + player.kills * 1200);
  const rows = [{
    skill: "Overall",
    rank: player.rank,
    level: totalLevel,
    xp: totalXp,
  }];

  if (Array.isArray(player.skills) && player.skills.length > 0) {
    player.skills.forEach((skill, index) => {
      rows.push({
        skill: skill.skill,
        rank: player.rank * 100 + index + 1,
        level: skill.level,
        xp: skill.xp,
      });
    });
    return rows;
  }

  const roleBoosts = {
    Skiller: ["Woodcutting", "Mining", "Fishing", "Cooking", "Fletching"],
    PKer: ["Fighting", "Ranged", "Prayer", "Magic", "Hits"],
    "Monster hunter": ["Fighting", "Prayer", "Magic", "Ranged", "Mining"],
  };
  const boosted = roleBoosts[player.role] || [];

  skills.slice(1).forEach((skill, index) => {
    const boost = boosted.includes(skill) ? 9 : 0;
    const level = Math.max(1, Math.min(99, Math.floor(player.level / 3) + boost + (index % 5)));
    const xp = level <= 1 ? 0 : Math.max(0, Math.floor((level * level * level * 12) + player.score * (index + 1) / 5));
    rows.push({
      skill,
      rank: player.rank * 100 + index * 37 + 1,
      level,
      xp,
    });
  });

  return rows;
}

function showProfile(player) {
  if (!player || !highscoreProfile) {
    return;
  }

  const rows = skillRowsFor(player);
  const overall = rows[0];
  profileTitle.textContent = `Skill Hiscores for ${player.name}`;
  profileMeta.innerHTML = `${player.role} | ${player.clan} | <span class="status ${player.status.toLowerCase()}">${player.status}</span> | Goal: ${player.goal} | XP rate: ${player.xpRate}`;
  profilePermalink.href = `#highscores?player=${encodeURIComponent(player.name)}`;
  profileRank.textContent = formatNumber(overall.rank);
  profileLevel.textContent = formatNumber(overall.level);
  profileXp.textContent = formatNumber(overall.xp);
  profileScore.textContent = formatNumber(player.score);
  profileSkillsBody.innerHTML = rows.map((row) => `
    <tr>
      <td>${row.skill}</td>
      <td>${formatNumber(row.rank)}</td>
      <td>${formatNumber(row.level)}</td>
      <td>${formatNumber(row.xp)}</td>
    </tr>
  `).join("");

  highscoreProfile.classList.remove("is-hidden");
  highscoreEmpty.classList.add("is-hidden");
}

function findPlayer(query) {
  const normalized = query.trim().toLowerCase();
  if (!normalized) {
    return null;
  }
  return highscorePlayers.find((player) => player.name.toLowerCase() === normalized)
    || highscorePlayers.find((player) => player.name.toLowerCase().includes(normalized));
}

function runLookup() {
  const player = findPlayer(highscoreSearch.value);
  if (player) {
    showProfile(player);
    player.row.scrollIntoView({ block: "nearest" });
    return;
  }
  highscoreProfile.classList.add("is-hidden");
  highscoreEmpty.classList.remove("is-hidden");
}

if (highscoreSearch) {
  highscoreSearch.addEventListener("input", () => {
    const query = highscoreSearch.value.trim().toLowerCase();
    highscoreRows.forEach((row) => {
      row.classList.toggle("is-hidden", query.length > 0 && !row.textContent.toLowerCase().includes(query));
    });
    if (!query) {
      highscoreProfile.classList.add("is-hidden");
      highscoreEmpty.classList.add("is-hidden");
    }
  });

  highscoreSearch.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      runLookup();
    }
  });
}

if (highscoreLookup) {
  highscoreLookup.addEventListener("click", runLookup);
}

function bindHighscoreRows() {
  highscoreRows.forEach((row, index) => {
    row.addEventListener("click", () => {
      highscoreSearch.value = highscorePlayers[index].name;
      showProfile(highscorePlayers[index]);
    });
  });
}

bindHighscoreRows();

const initialPlayer = new URLSearchParams(window.location.hash.split("?")[1] || "").get("player");
if (initialPlayer && highscoreSearch) {
  highscoreSearch.value = initialPlayer;
  runLookup();
}

loadLiveHighscores();
setInterval(loadLiveHighscores, 30000);

const onlineBotCount = document.getElementById("onlineBotCount");
const skillerOnline = document.getElementById("skillerOnline");
const fighterOnline = document.getElementById("fighterOnline");
const wildOnline = document.getElementById("wildOnline");

function updatePopulationEstimate() {
  if (!onlineBotCount) {
    return;
  }

  const now = Date.now();
  const wave = Math.sin(now / 420000) * 7;
  const churn = Math.sin(now / 31000) * 3;
  const skiller = Math.round(96 + wave + Math.sin(now / 73000) * 4);
  const fighter = Math.round(43 + churn + Math.sin(now / 91000) * 3);
  const wild = Math.round(24 + Math.sin(now / 47000) * 5);
  const boundedSkiller = Math.max(74, Math.min(118, skiller));
  const boundedFighter = Math.max(28, Math.min(58, fighter));
  const boundedWild = Math.max(12, Math.min(38, wild));

  skillerOnline.textContent = boundedSkiller;
  fighterOnline.textContent = boundedFighter;
  wildOnline.textContent = boundedWild;
  onlineBotCount.textContent = boundedSkiller + boundedFighter + boundedWild;
}

updatePopulationEstimate();
setInterval(updatePopulationEstimate, 5000);

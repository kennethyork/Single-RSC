const highscoreSearch = document.getElementById("highscoreSearch");
const highscoreLookup = document.getElementById("highscoreLookup");
const highscoreCategory = document.getElementById("highscoreCategory");
const highscoreSource = document.getElementById("highscoreSource");
const highscoreBoardTitle = document.getElementById("highscoreBoardTitle");
const highscoreTable = document.getElementById("highscoreTable");
const highscoreTableHead = highscoreTable ? highscoreTable.querySelector("thead tr") : null;
const highscoreTableBody = highscoreTable ? highscoreTable.querySelector("tbody") : null;
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
const onlineBotCount = document.getElementById("onlineBotCount");
const skillerOnline = document.getElementById("skillerOnline");
const fighterOnline = document.getElementById("fighterOnline");
const wildOnline = document.getElementById("wildOnline");
const worldActivityList = document.getElementById("worldActivityList");
const marketCoins = document.getElementById("marketCoins");
const marketTrades = document.getElementById("marketTrades");
const marketGroups = document.getElementById("marketGroups");
const marketKills = document.getElementById("marketKills");
const activeGroupsList = document.getElementById("activeGroupsList");
const connectHighscores = document.getElementById("connectHighscores");
const chooseHighscores = document.getElementById("chooseHighscores");
const highscoreFileInput = document.getElementById("highscoreFileInput");

const SKILLS = [
  "Attack", "Defense", "Strength", "Hits", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
  "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblaw", "Agility", "Thieving",
];
const CATEGORIES = ["Overall", ...SKILLS, "World bots"];

let highscorePlayers = [];
let generatedAt = 0;
let liveHighscoresLoaded = false;
let selectedPlayerName = null;
let liveFileHandle = null;
let highscoreDataSource = "bundled";
let connectedFileName = "";

function number(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatNumber(value) {
  return number(value).toLocaleString("en-US");
}

function playerFromExport(entry) {
  return {
    rank: number(entry.rank),
    name: String(entry.name || "unknown"),
    type: String(entry.type || "Player"),
    role: String(entry.role || entry.type || "Player"),
    clan: String(entry.clan || ""),
    status: String(entry.status || "Offline"),
    goal: String(entry.goal || ""),
    level: number(entry.level),
    combatLevel: number(entry.combatLevel || entry.level),
    xpRate: String(entry.xpRate || "1x"),
    xp: number(entry.xp),
    score: number(entry.score),
    coins: number(entry.coins),
    trades: number(entry.trades),
    kills: number(entry.kills),
    groupTrips: number(entry.groupTrips),
    playerTrades: number(entry.playerTrades),
    playerKills: number(entry.playerKills),
    skills: Array.isArray(entry.skills) ? entry.skills.map((skill) => ({
      skill: String(skill.skill || skill.name || ""),
      level: number(skill.level),
      xp: number(skill.xp),
    })).filter((skill) => SKILLS.includes(skill.skill)) : [],
  };
}

function savedCharacters() {
  return highscorePlayers.filter((player) => player.type !== "World bot" && player.skills.length > 0);
}

function worldBots() {
  return highscorePlayers.filter((player) => player.type === "World bot");
}

function skillFor(player, category) {
  return player.skills.find((skill) => skill.skill === category) || { skill: category, level: 1, xp: 0 };
}

function compareNames(first, second) {
  return first.name.localeCompare(second.name, undefined, { sensitivity: "base" });
}

function rankedRows(category) {
  if (category === "World bots") {
    return worldBots()
      .slice()
      .sort((first, second) => second.score - first.score || compareNames(first, second))
      .map((player, index) => ({ player, rank: index + 1, level: player.combatLevel, xp: player.xp }));
  }

  const rows = savedCharacters().map((player) => {
    const result = category === "Overall"
      ? { level: player.level, xp: player.xp }
      : skillFor(player, category);
    return { player, level: result.level, xp: result.xp };
  });
  rows.sort((first, second) => second.level - first.level || second.xp - first.xp || compareNames(first.player, second.player));
  return rows.map((row, index) => ({ ...row, rank: index + 1 }));
}

function addCell(row, text, className) {
  const cell = document.createElement("td");
  cell.textContent = text;
  if (className) {
    cell.className = className;
  }
  row.appendChild(cell);
  return cell;
}

function setTableHead(labels) {
  if (!highscoreTableHead) {
    return;
  }
  highscoreTableHead.replaceChildren(...labels.map((label) => {
    const header = document.createElement("th");
    header.textContent = label;
    return header;
  }));
}

function renderHighscoreBoard() {
  if (!highscoreTableBody || !highscoreCategory) {
    return;
  }
  const category = highscoreCategory.value || "Overall";
  const rows = rankedRows(category);
  highscoreBoardTitle.textContent = category === "World bots" ? "World bot leaderboard" : `${category} Hiscores`;
  setTableHead(category === "World bots"
    ? ["Rank", "Player", "Role", "Level", "Score", "Status"]
    : ["Rank", "Player", "Level", "XP", "Status"]);
  highscoreTableBody.replaceChildren();

  if (rows.length === 0) {
    const row = document.createElement("tr");
    addCell(row, category === "World bots" ? "No world bots have been exported yet." : "No saved characters have been exported yet.");
    row.firstElementChild.colSpan = category === "World bots" ? 6 : 5;
    highscoreTableBody.appendChild(row);
    return;
  }

  rows.forEach(({ player, rank, level, xp }) => {
    const row = document.createElement("tr");
    row.dataset.player = player.name;
    addCell(row, formatNumber(rank));
    addCell(row, player.name, "highscore-player-name");
    if (category === "World bots") {
      addCell(row, player.role);
      addCell(row, formatNumber(level));
      addCell(row, formatNumber(player.score));
      const statusCell = addCell(row, "");
      const status = document.createElement("span");
      status.className = `status ${player.status.toLowerCase() === "online" ? "online" : "offline"}`;
      status.textContent = player.status;
      statusCell.appendChild(status);
    } else {
      addCell(row, formatNumber(level));
      addCell(row, formatNumber(xp));
      const statusCell = addCell(row, "");
      const status = document.createElement("span");
      status.className = `status ${player.status.toLowerCase() === "online" ? "online" : "offline"}`;
      status.textContent = player.status;
      statusCell.appendChild(status);
      row.tabIndex = 0;
      row.title = `View ${player.name}'s hiscores`;
      row.addEventListener("click", () => showProfile(player));
      row.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          showProfile(player);
        }
      });
    }
    highscoreTableBody.appendChild(row);
  });
}

function profileRowsFor(player) {
  return ["Overall", ...SKILLS].map((category) => {
    const ranked = rankedRows(category);
    const result = ranked.find((row) => row.player.name.toLowerCase() === player.name.toLowerCase());
    return {
      skill: category,
      rank: result ? result.rank : 0,
      level: category === "Overall" ? player.level : skillFor(player, category).level,
      xp: category === "Overall" ? player.xp : skillFor(player, category).xp,
    };
  });
}

function showProfile(player) {
  if (!player || !highscoreProfile || player.type === "World bot") {
    return;
  }
  const rows = profileRowsFor(player);
  selectedPlayerName = player.name;
  profileTitle.textContent = `Skill Hiscores for ${player.name}`;
  profileMeta.textContent = `${player.role} | ${player.status} | XP rate: ${player.xpRate}`;
  profilePermalink.href = `#highscores?player=${encodeURIComponent(player.name)}`;
  profileRank.textContent = formatNumber(rows[0].rank);
  profileLevel.textContent = formatNumber(player.level);
  profileXp.textContent = formatNumber(player.xp);
  profileScore.textContent = player.xpRate;
  profileSkillsBody.replaceChildren(...rows.map((result) => {
    const row = document.createElement("tr");
    addCell(row, result.skill);
    addCell(row, result.rank ? formatNumber(result.rank) : "Unranked");
    addCell(row, formatNumber(result.level));
    addCell(row, formatNumber(result.xp));
    row.addEventListener("click", () => {
      highscoreCategory.value = result.skill;
      renderHighscoreBoard();
    });
    return row;
  }));
  highscoreProfile.classList.remove("is-hidden");
  highscoreEmpty.classList.add("is-hidden");
}

function findCharacter(query) {
  const normalized = query.trim().toLowerCase();
  if (!normalized) {
    return null;
  }
  return savedCharacters().find((player) => player.name.toLowerCase() === normalized) || null;
}

function runLookup(showEmpty = true) {
  if (!highscoreSearch) {
    return;
  }
  const player = findCharacter(highscoreSearch.value);
  if (player) {
    showProfile(player);
    return;
  }
  selectedPlayerName = null;
  highscoreProfile.classList.add("is-hidden");
  highscoreEmpty.classList.toggle("is-hidden", !showEmpty);
}

function updateHighscoreSource() {
  if (!highscoreSource) {
    return;
  }
  if (!liveHighscoresLoaded) {
    highscoreSource.textContent = "The local hiscores export could not be loaded. Start the game and serve the docs folder over HTTP.";
    return;
  }
  const timestamp = generatedAt ? new Date(generatedAt).toLocaleString() : "unknown";
  const source = highscoreDataSource === "live"
    ? `Connected live to ${connectedFileName}`
    : highscoreDataSource === "manual"
      ? `Loaded once from ${connectedFileName}`
      : "Showing the bundled snapshot";
  highscoreSource.textContent = `${source}. ${savedCharacters().length} saved characters and ${worldBots().length} world bots. Last updated: ${timestamp}.`;
}

function updatePopulationFromHighscores() {
  const online = worldBots().filter((player) => player.status.toLowerCase() === "online");
  if (onlineBotCount) onlineBotCount.textContent = online.length;
  if (skillerOnline) skillerOnline.textContent = online.filter((player) => player.role === "Skiller").length;
  if (fighterOnline) fighterOnline.textContent = online.filter((player) => player.role === "Monster hunter").length;
  if (wildOnline) wildOnline.textContent = online.filter((player) => player.role === "PKer").length;
}

function replaceList(list, values, fallback) {
  if (!list) return;
  const lines = values.length ? values : [fallback];
  list.replaceChildren(...lines.map((line) => {
    const item = document.createElement("li");
    item.textContent = line;
    return item;
  }));
}

function updateWorldPanels(activity, market, groups) {
  const activityFallback = worldBots().slice(0, 5).map((player) => `${player.name} is ${player.goal} (${player.status})`);
  replaceList(worldActivityList, Array.isArray(activity) ? activity.slice(0, 8).map(String) : activityFallback, "No recent world activity.");
  const summary = market || {};
  if (marketCoins) marketCoins.textContent = formatNumber(summary.coinsInBotInventories);
  if (marketTrades) marketTrades.textContent = formatNumber(summary.botTrades);
  if (marketGroups) marketGroups.textContent = formatNumber(summary.groupTrips);
  if (marketKills) marketKills.textContent = formatNumber(summary.assistedKills);
  const groupLines = Array.isArray(groups) ? groups.slice(0, 6).map((group) =>
    `${group.leader || "Unknown"} leading ${number(group.size)} for ${group.goal || "a group trip"} at ${group.area || "the world"}`) : [];
  replaceList(activeGroupsList, groupLines, "No bot groups formed yet.");
}

function applyExport(data, source = "bundled", fileName = "") {
  if (!data || !Array.isArray(data.players)) {
    throw new Error("This is not a Single-RSC highscore export.");
  }
  highscorePlayers = Array.isArray(data.players) ? data.players.map(playerFromExport) : [];
  generatedAt = number(data.generatedAt);
  if (Array.isArray(data.activePlayers)) {
    const active = new Set(data.activePlayers.map((name) => String(name).trim().toLowerCase()));
    highscorePlayers.forEach((player) => {
      if (player.type !== "World bot") {
        player.status = active.has(player.name.trim().toLowerCase()) ? "Online" : "Offline";
      }
    });
  }
  highscoreDataSource = source;
  connectedFileName = fileName;
  liveHighscoresLoaded = true;
  renderHighscoreBoard();
  updateHighscoreSource();
  updatePopulationFromHighscores();
  updateWorldPanels(data.activity, data.market, data.groups);
  if (selectedPlayerName) {
    const selected = findCharacter(selectedPlayerName);
    if (selected) showProfile(selected);
  }
  const linkedPlayer = new URLSearchParams(window.location.hash.split("?")[1] || "").get("player");
  if (linkedPlayer && !selectedPlayerName && highscoreSearch) {
    highscoreSearch.value = linkedPlayer;
    runLookup(false);
  }
}

function loadBundledHighscores() {
  fetch("highscores.json", { cache: "no-store" })
    .then((response) => {
      if (!response.ok) throw new Error(`Hiscores export returned ${response.status}`);
      return response.json();
    })
    .then((data) => applyExport(data, "bundled"))
    .catch(() => {
      if (!liveHighscoresLoaded) {
        updateHighscoreSource();
        updatePopulationFromHighscores();
        updateWorldPanels([], {}, []);
      }
    });
}

const FILE_HANDLE_DB = "single-rsc-highscores";
const FILE_HANDLE_STORE = "handles";

function openHandleDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(FILE_HANDLE_DB, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(FILE_HANDLE_STORE);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function storedFileHandle() {
  const database = await openHandleDatabase();
  return new Promise((resolve, reject) => {
    const request = database.transaction(FILE_HANDLE_STORE, "readonly")
      .objectStore(FILE_HANDLE_STORE).get("live-export");
    request.onsuccess = () => resolve(request.result || null);
    request.onerror = () => reject(request.error);
  });
}

async function rememberFileHandle(handle) {
  const database = await openHandleDatabase();
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(FILE_HANDLE_STORE, "readwrite");
    transaction.objectStore(FILE_HANDLE_STORE).put(handle, "live-export");
    transaction.oncomplete = resolve;
    transaction.onerror = () => reject(transaction.error);
  });
}

async function readLiveFile(handle) {
  const file = await handle.getFile();
  const data = JSON.parse(await file.text());
  applyExport(data, "live", file.name);
  if (connectHighscores) connectHighscores.textContent = "Live highscores connected";
}

async function connectLiveFile() {
  try {
    let handle = liveFileHandle;
    if (handle) {
      const permission = await handle.queryPermission({ mode: "read" });
      if (permission !== "granted" && await handle.requestPermission({ mode: "read" }) !== "granted") {
        return;
      }
    } else if (window.showOpenFilePicker) {
      [handle] = await window.showOpenFilePicker({
        multiple: false,
        types: [{ description: "Single-RSC highscore export", accept: { "application/json": [".json"] } }],
      });
    } else {
      highscoreFileInput.click();
      return;
    }
    liveFileHandle = handle;
    await rememberFileHandle(handle);
    await readLiveFile(handle);
  } catch (error) {
    if (error && error.name !== "AbortError") {
      highscoreSource.textContent = `Could not connect the export: ${error.message}`;
    }
  }
}

async function restoreLiveFile() {
  try {
    if (!window.showOpenFilePicker) return false;
    const handle = await storedFileHandle();
    if (!handle) return false;
    liveFileHandle = handle;
    if (await handle.queryPermission({ mode: "read" }) === "granted") {
      await readLiveFile(handle);
      return true;
    }
    if (connectHighscores) connectHighscores.textContent = "Reconnect live highscores";
  } catch (ignored) {
  }
  return false;
}

async function initializeHighscores() {
  if (!await restoreLiveFile()) {
    loadBundledHighscores();
  }
}

async function refreshHighscores() {
  if (liveFileHandle) {
    try {
      if (await liveFileHandle.queryPermission({ mode: "read" }) === "granted") {
        await readLiveFile(liveFileHandle);
        return;
      }
    } catch (ignored) {
    }
  }
  if (highscoreDataSource === "bundled") loadBundledHighscores();
}

if (highscoreCategory) {
  highscoreCategory.replaceChildren(...CATEGORIES.map((category) => {
    const option = document.createElement("option");
    option.value = category;
    option.textContent = category;
    return option;
  }));
  highscoreCategory.addEventListener("change", renderHighscoreBoard);
}

if (highscoreSearch) {
  highscoreSearch.addEventListener("input", () => {
    highscoreEmpty.classList.add("is-hidden");
    if (!highscoreSearch.value.trim()) {
      selectedPlayerName = null;
      highscoreProfile.classList.add("is-hidden");
    }
  });
  highscoreSearch.addEventListener("keydown", (event) => {
    if (event.key === "Enter") runLookup();
  });
}
if (highscoreLookup) highscoreLookup.addEventListener("click", () => runLookup());
if (connectHighscores) connectHighscores.addEventListener("click", connectLiveFile);
if (chooseHighscores) chooseHighscores.addEventListener("click", () => highscoreFileInput.click());
if (highscoreFileInput) {
  highscoreFileInput.addEventListener("change", async () => {
    const file = highscoreFileInput.files && highscoreFileInput.files[0];
    if (!file) return;
    try {
      applyExport(JSON.parse(await file.text()), "manual", file.name);
    } catch (error) {
      highscoreSource.textContent = `Could not read that export: ${error.message}`;
    } finally {
      highscoreFileInput.value = "";
    }
  });
}
window.addEventListener("hashchange", () => {
  const player = new URLSearchParams(window.location.hash.split("?")[1] || "").get("player");
  if (player && highscoreSearch) {
    highscoreSearch.value = player;
    runLookup(false);
  }
});

renderHighscoreBoard();
initializeHighscores();
setInterval(refreshHighscores, 30000);

const fileInput = document.querySelector("#highscore-file");
const connectButton = document.querySelector("#highscore-connect");
const skillSelect = document.querySelector("#highscore-skill");
const searchInput = document.querySelector("#highscore-search");
const summary = document.querySelector("#highscore-summary");
const results = document.querySelector("#highscore-results");
const tableBody = document.querySelector("#highscore-body");
const profile = document.querySelector("#highscore-profile");
const profileName = document.querySelector("#highscore-profile-name");
const profileSummary = document.querySelector("#highscore-profile-summary");
const profileSkills = document.querySelector("#highscore-profile-skills");
const profileClose = document.querySelector("#highscore-profile-close");

const numberFormat = new Intl.NumberFormat();
const supportsPersistentFile = "showOpenFilePicker" in window && "indexedDB" in window;
const CACHE_KEY = "single-rsc-highscores-cache-v1";
const VIEW_KEY = "single-rsc-highscores-view-v1";
const DATABASE_NAME = "single-rsc-highscores";
const DATABASE_STORE = "files";

let data = null;
let selectedUsername = null;
let liveHandle = null;
let lastModified = 0;
let refreshTimer = null;

function validateExport(value) {
  if (!value || value.formatVersion !== 1 || !Array.isArray(value.skills) || !Array.isArray(value.entries)) {
    throw new Error("Unsupported Single-RSC highscore export");
  }
  value.entries.forEach((entry) => {
    if (!entry || typeof entry.displayName !== "string" || typeof entry.username !== "string"
        || !Array.isArray(entry.levels) || !Array.isArray(entry.xp)
        || entry.levels.length < value.skills.length || entry.xp.length < value.skills.length) {
      throw new Error("Invalid Single-RSC highscore entry");
    }
  });
  return value;
}

function readView() {
  try {
    return JSON.parse(localStorage.getItem(VIEW_KEY)) || {};
  } catch (ignored) {
    return {};
  }
}

function saveView() {
  try {
    localStorage.setItem(VIEW_KEY, JSON.stringify({
      search: searchInput.value,
      skill: skillSelect.value,
      selectedUsername,
    }));
  } catch (ignored) {
  }
}

function rankEntries() {
  const selection = skillSelect.value;
  const skillIndex = selection === "overall" ? -1 : Number(selection);
  const ranked = [...data.entries].sort((left, right) => {
    const leftLevel = skillIndex < 0 ? left.totalLevel : left.levels[skillIndex];
    const rightLevel = skillIndex < 0 ? right.totalLevel : right.levels[skillIndex];
    const leftXp = skillIndex < 0 ? left.totalXp : left.xp[skillIndex];
    const rightXp = skillIndex < 0 ? right.totalXp : right.xp[skillIndex];
    return rightLevel - leftLevel || rightXp - leftXp || left.displayName.localeCompare(right.displayName);
  });
  return { ranked, skillIndex };
}

function addCell(row, value) {
  const cell = document.createElement("td");
  cell.textContent = value;
  row.appendChild(cell);
}

function renderRankings() {
  if (!data) return;
  const { ranked, skillIndex } = rankEntries();
  const query = searchInput.value.trim().toLocaleLowerCase();
  const matches = query
    ? ranked.filter((entry) => entry.displayName.toLocaleLowerCase().includes(query)
      || entry.username.toLocaleLowerCase().includes(query))
    : ranked;

  results.textContent = query
    ? `${matches.length} player${matches.length === 1 ? "" : "s"} match “${searchInput.value.trim()}”.`
    : `Showing the top ${Math.min(matches.length, 100)} of ${matches.length} players. Click anyone to view every skill.`;

  if (matches.length === 0) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.colSpan = 5;
    cell.className = "highscore-empty";
    cell.textContent = "No players match that search.";
    row.appendChild(cell);
    tableBody.replaceChildren(row);
    return;
  }

  tableBody.replaceChildren(...matches.slice(0, 100).map((entry) => {
    const row = document.createElement("tr");
    row.className = "highscore-row";
    row.tabIndex = 0;
    row.setAttribute("role", "button");
    row.setAttribute("aria-label", `View all stats for ${entry.displayName}`);
    addCell(row, ranked.indexOf(entry) + 1);
    addCell(row, entry.displayName);
    addCell(row, entry.bot ? "Bot" : "Character");
    addCell(row, skillIndex < 0 ? entry.totalLevel : entry.levels[skillIndex]);
    addCell(row, numberFormat.format(skillIndex < 0 ? entry.totalXp : entry.xp[skillIndex]));
    const open = () => renderProfile(entry);
    row.addEventListener("click", open);
    row.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        open();
      }
    });
    return row;
  }));
}

function renderProfile(entry, scroll = true) {
  selectedUsername = entry.username;
  saveView();
  profileName.textContent = entry.displayName;
  profileSummary.textContent = `${entry.bot ? "Bot" : "Character"} · Combat ${entry.combatLevel}`
    + ` · Total level ${numberFormat.format(entry.totalLevel)} · Total XP ${numberFormat.format(entry.totalXp)}`;
  profileSkills.replaceChildren(...data.skills.map((skill, index) => {
    const row = document.createElement("tr");
    addCell(row, skill);
    addCell(row, entry.levels[index]);
    addCell(row, numberFormat.format(entry.xp[index]));
    return row;
  }));
  profile.hidden = false;
  if (scroll) profile.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

function applyExport(value, message) {
  const savedView = data ? {
    search: searchInput.value,
    skill: skillSelect.value,
    selectedUsername,
  } : readView();
  data = validateExport(value);
  skillSelect.replaceChildren(new Option("Overall", "overall"),
    ...data.skills.map((skill, index) => new Option(skill, String(index))));
  if ([...skillSelect.options].some((option) => option.value === savedView.skill)) {
    skillSelect.value = savedView.skill;
  }
  searchInput.value = typeof savedView.search === "string" ? savedView.search : "";
  selectedUsername = typeof savedView.selectedUsername === "string" ? savedView.selectedUsername : null;
  skillSelect.disabled = false;
  searchInput.disabled = false;
  summary.textContent = message;
  renderRankings();
  if (selectedUsername) {
    const selected = data.entries.find((entry) => entry.username === selectedUsername);
    if (selected) renderProfile(selected, false);
    else {
      selectedUsername = null;
      profile.hidden = true;
      saveView();
    }
  }
}

async function loadFile(file, live = false) {
  try {
    const parsed = validateExport(JSON.parse(await file.text()));
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(parsed));
    } catch (ignored) {
    }
    const generated = new Date(parsed.generatedAt);
    const generatedLabel = Number.isNaN(generated.valueOf()) ? "unknown time" : generated.toLocaleString();
    const message = live
      ? `Live: ${parsed.entries.length} local entries, last game update ${generatedLabel}. Refreshing automatically; nothing is uploaded.`
      : `Loaded ${parsed.entries.length} local entries generated ${generatedLabel}. Nothing was uploaded.`;
    lastModified = file.lastModified;
    applyExport(parsed, message);
  } catch (error) {
    summary.textContent = `Could not load ${file.name}: ${error.message}`;
    if (!data) {
      skillSelect.disabled = true;
      searchInput.disabled = true;
      results.textContent = "";
      profile.hidden = true;
    }
  }
}

function restoreCachedExport() {
  try {
    const cached = localStorage.getItem(CACHE_KEY);
    if (!cached) return false;
    const parsed = validateExport(JSON.parse(cached));
    const generated = new Date(parsed.generatedAt);
    const label = Number.isNaN(generated.valueOf()) ? "unknown time" : generated.toLocaleString();
    applyExport(parsed, `Showing ${parsed.entries.length} saved local entries from ${label}. Reconnecting live updates in the background.`);
    return true;
  } catch (ignored) {
    try { localStorage.removeItem(CACHE_KEY); } catch (storageError) { }
    return false;
  }
}

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE_NAME, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(DATABASE_STORE);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function saveHandle(handle) {
  const database = await openDatabase();
  await new Promise((resolve, reject) => {
    const request = database.transaction(DATABASE_STORE, "readwrite")
      .objectStore(DATABASE_STORE).put(handle, "live-export");
    request.onsuccess = resolve;
    request.onerror = () => reject(request.error);
  });
  database.close();
}

async function savedHandle() {
  const database = await openDatabase();
  const handle = await new Promise((resolve, reject) => {
    const request = database.transaction(DATABASE_STORE, "readonly")
      .objectStore(DATABASE_STORE).get("live-export");
    request.onsuccess = () => resolve(request.result || null);
    request.onerror = () => reject(request.error);
  });
  database.close();
  return handle;
}

async function refreshLive(requestAccess = false) {
  if (!liveHandle) return;
  let permission = await liveHandle.queryPermission({ mode: "read" });
  if (permission !== "granted" && requestAccess) {
    permission = await liveHandle.requestPermission({ mode: "read" });
  }
  if (permission !== "granted") {
    summary.textContent = data
      ? "Showing saved stats. Click Connect live highscores to resume automatic updates."
      : "Click Connect live highscores to restore access to the remembered file.";
    return;
  }
  const file = await liveHandle.getFile();
  if (!data || file.lastModified !== lastModified) await loadFile(file, true);
}

function startRefresh() {
  window.clearInterval(refreshTimer);
  refreshTimer = window.setInterval(() => refreshLive().catch((error) => {
    summary.textContent = `Live highscore refresh failed: ${error.message}`;
  }), 10_000);
}

connectButton.addEventListener("click", async () => {
  try {
    if (!supportsPersistentFile) {
      fileInput.click();
      return;
    }
    if (!liveHandle) {
      [liveHandle] = await window.showOpenFilePicker({
        multiple: false,
        types: [{ description: "Single-RSC highscores", accept: { "application/json": [".json"] } }],
      });
      await saveHandle(liveHandle);
    }
    await refreshLive(true);
    startRefresh();
  } catch (error) {
    if (error.name !== "AbortError") summary.textContent = `Could not connect live highscores: ${error.message}`;
  }
});

fileInput.addEventListener("change", async () => {
  const file = fileInput.files[0];
  if (file) await loadFile(file);
});
skillSelect.addEventListener("change", () => { saveView(); renderRankings(); });
searchInput.addEventListener("input", () => { saveView(); renderRankings(); });
profileClose.addEventListener("click", () => {
  selectedUsername = null;
  profile.hidden = true;
  saveView();
});

restoreCachedExport();
if (supportsPersistentFile) {
  savedHandle().then(async (handle) => {
    if (!handle) return;
    liveHandle = handle;
    await refreshLive();
    startRefresh();
  }).catch(() => {
    if (!data) summary.textContent = "Connect the local highscore file to enable automatic updates.";
  });
} else {
  connectButton.textContent = "Choose highscore export";
}

document.addEventListener("visibilitychange", () => {
  if (!document.hidden && liveHandle) refreshLive().catch(() => {});
});

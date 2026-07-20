const highscoreSearch = document.getElementById("highscoreSearch");
const highscoreLookup = document.getElementById("highscoreLookup");
const highscoreRows = Array.from(document.querySelectorAll("#highscoreTable tbody tr"));
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

const highscorePlayers = highscoreRows.map((row) => {
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

function formatNumber(value) {
  return value.toLocaleString("en-US");
}

function skillRowsFor(player) {
  const totalLevel = Math.max(16, player.level + Math.floor(player.score / 900));
  const totalXp = Math.max(1000, player.score * Number(player.xpRate.replace("x", "")) + player.coins + player.trades * 90 + player.kills * 1200);
  const rows = [{
    skill: "Overall",
    rank: player.rank,
    level: totalLevel,
    xp: totalXp,
  }];

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

highscoreRows.forEach((row, index) => {
  row.addEventListener("click", () => {
    highscoreSearch.value = highscorePlayers[index].name;
    showProfile(highscorePlayers[index]);
  });
});

const initialPlayer = new URLSearchParams(window.location.hash.split("?")[1] || "").get("player");
if (initialPlayer && highscoreSearch) {
  highscoreSearch.value = initialPlayer;
  runLookup();
}

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

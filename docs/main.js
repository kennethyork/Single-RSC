const highscoreSearch = document.getElementById("highscoreSearch");
const highscoreRows = document.querySelectorAll("#highscoreTable tbody tr");

if (highscoreSearch) {
  highscoreSearch.addEventListener("input", () => {
    const query = highscoreSearch.value.trim().toLowerCase();
    highscoreRows.forEach((row) => {
      row.classList.toggle("is-hidden", query.length > 0 && !row.textContent.toLowerCase().includes(query));
    });
  });
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

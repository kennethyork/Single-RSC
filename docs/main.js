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

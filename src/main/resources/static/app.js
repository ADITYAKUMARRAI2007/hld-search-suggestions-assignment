const input = document.querySelector("#search-input");
const form = document.querySelector("#search-form");
const list = document.querySelector("#suggestions");
const statusEl = document.querySelector("#status");
const debugPanel = document.querySelector("#debug-panel");
const metricsPanel = document.querySelector("#metrics-panel");
const trendingList = document.querySelector("#trending-list");
const trendWindow = document.querySelector("#trend-window");
const rankButtons = [...document.querySelectorAll("[data-rank]")];

let activeRank = "hybrid";
let activeIndex = -1;
let suggestions = [];
let debounceTimer = null;

rankButtons.forEach((button) => {
  button.addEventListener("click", () => {
    activeRank = button.dataset.rank;
    rankButtons.forEach((item) => item.classList.toggle("active", item === button));
    fetchSuggestions();
  });
});

input.addEventListener("input", () => {
  window.clearTimeout(debounceTimer);
  debounceTimer = window.setTimeout(fetchSuggestions, 180);
});

input.addEventListener("keydown", (event) => {
  if (!suggestions.length) return;
  if (event.key === "ArrowDown") {
    event.preventDefault();
    activeIndex = Math.min(activeIndex + 1, suggestions.length - 1);
    renderSuggestions();
  }
  if (event.key === "ArrowUp") {
    event.preventDefault();
    activeIndex = Math.max(activeIndex - 1, 0);
    renderSuggestions();
  }
  if (event.key === "Enter" && activeIndex >= 0) {
    event.preventDefault();
    input.value = suggestions[activeIndex].query;
    list.classList.remove("open");
    submitSearch();
  }
  if (event.key === "Escape") {
    list.classList.remove("open");
  }
});

form.addEventListener("submit", (event) => {
  event.preventDefault();
  submitSearch();
});

trendWindow.addEventListener("change", refreshTrending);
document.querySelector("#refresh-debug").addEventListener("click", refreshDebug);
document.querySelector("#refresh-metrics").addEventListener("click", refreshMetrics);

async function fetchSuggestions() {
  const q = input.value.trim();
  if (!q) {
    suggestions = [];
    list.classList.remove("open");
    statusEl.textContent = "Ready";
    return;
  }
  statusEl.textContent = "Loading suggestions...";
  try {
    const response = await fetch(`/suggest?q=${encodeURIComponent(q)}&rank=${activeRank}&debug=true`);
    const payload = await response.json();
    suggestions = payload.suggestions || [];
    activeIndex = -1;
    renderSuggestions();
    renderDebug(payload.cache, payload.latency_ms, payload.rank);
    statusEl.textContent = `${suggestions.length} suggestions in ${formatNumber(payload.latency_ms)} ms`;
    refreshMetrics();
  } catch (error) {
    statusEl.textContent = "Suggestion request failed";
  }
}

function renderSuggestions() {
  list.innerHTML = "";
  suggestions.forEach((item, index) => {
    const li = document.createElement("li");
    li.className = index === activeIndex ? "active" : "";
    li.role = "option";
    li.innerHTML = `<span>${escapeHtml(item.query)}</span><small>${formatInteger(item.score)} score</small>`;
    li.addEventListener("mousedown", () => {
      input.value = item.query;
      list.classList.remove("open");
      submitSearch();
    });
    list.appendChild(li);
  });
  list.classList.toggle("open", suggestions.length > 0);
}

async function submitSearch() {
  const query = input.value.trim();
  if (!query) return;
  statusEl.textContent = "Submitting search...";
  try {
    const response = await fetch("/search", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ query })
    });
    const payload = await response.json();
    statusEl.textContent = `${payload.message}: ${payload.query}`;
    list.classList.remove("open");
    await fetch("/api/v1/admin/flush", { method: "POST" });
    await Promise.all([fetchSuggestions(), refreshTrending(), refreshMetrics(), refreshDebug()]);
  } catch (error) {
    statusEl.textContent = "Search submission failed";
  }
}

async function refreshTrending() {
  const response = await fetch(`/trending?window=${trendWindow.value}&limit=10`);
  const payload = await response.json();
  trendingList.innerHTML = "";
  (payload.trending || []).forEach((item) => {
    const li = document.createElement("li");
    li.innerHTML = `<span>${escapeHtml(item.query)}</span><small>${formatInteger(item.recent_count)} recent, ${formatInteger(item.historical_count)} total</small>`;
    trendingList.appendChild(li);
  });
}

async function refreshDebug() {
  const prefix = input.value.trim();
  if (!prefix) return;
  const response = await fetch(`/cache/debug?prefix=${encodeURIComponent(prefix)}&rank=${activeRank}`);
  const payload = await response.json();
  renderDebug({
    hit: payload.hit,
    node: payload.node,
    key: payload.cache_key,
    ttl_seconds_remaining: payload.ttl_seconds_remaining
  }, null, activeRank);
}

function renderDebug(cache, latency, rank) {
  if (!cache) return;
  setFacts(debugPanel, {
    Rank: rank || activeRank,
    Hit: String(cache.hit),
    Node: cache.node || "",
    Key: cache.key || "",
    TTL: `${cache.ttl_seconds_remaining || 0}s`,
    Latency: latency == null ? "" : `${formatNumber(latency)} ms`
  });
}

async function refreshMetrics() {
  const response = await fetch("/metrics");
  const payload = await response.json();
  setFacts(metricsPanel, {
    "Hit Rate": `${formatNumber((payload.cache_hit_rate || 0) * 100)}%`,
    "Avg Latency": `${formatNumber(payload.avg_suggest_latency_ms || 0)} ms`,
    "P95 Latency": `${formatNumber(payload.p95_suggest_latency_ms || 0)} ms`,
    "Write Saved": `${formatNumber(payload.write_reduction_percent || 0)}%`,
    "Search Events": formatInteger(payload.search_events_received || 0),
    "DB Writes": formatInteger(payload.db_write_operations || 0),
    "Batch Flushes": formatInteger(payload.batch_flushes || 0),
    "Invalidations": formatInteger(payload.prefix_cache_invalidations || 0)
  });
}

function setFacts(root, facts) {
  root.innerHTML = "";
  Object.entries(facts).forEach(([label, value]) => {
    const dt = document.createElement("dt");
    const dd = document.createElement("dd");
    dt.textContent = label;
    dd.textContent = value;
    root.append(dt, dd);
  });
}

function formatNumber(value) {
  return Number(value).toFixed(1);
}

function formatInteger(value) {
  return Number(value).toLocaleString("en-US");
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

refreshTrending();
refreshMetrics();

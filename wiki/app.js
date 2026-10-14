function pageFileName() {
  const file = location.pathname.split("/").pop() || "index.html";
  return file.toLowerCase();
}

function currentSection() {
  const path = location.pathname.toLowerCase();
  if (path.includes("/spells/")) return "spells";
  if (path.includes("/mobs/")) return "mobs";

  const file = pageFileName();
  if (file === "systems.html") return "systems";
  if (file === "spells.html") return "spells";
  if (file === "assignments.html") return "assignments";
  if (file === "mobs.html") return "mobs";
  if (file === "blocks.html") return "blocks";
  if (file === "items.html") return "items";
  if (file === "recipes.html") return "recipes";
  return "index";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function chipClass(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

function prettyLabel(value) {
  return String(value || "")
    .replaceAll("_", " ")
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

function detailHref(kind, id) {
  if (kind === "spell") {
    return location.pathname.toLowerCase().includes("/spells/") ? `${id}.html` : `spells/${id}.html`;
  }

  if (kind === "mob") {
    return location.pathname.toLowerCase().includes("/mobs/") ? `${id}.html` : `mobs/${id}.html`;
  }

  return "#";
}

function setActiveNav() {
  const section = currentSection();
  document.querySelectorAll("[data-nav]").forEach((link) => {
    const href = (link.getAttribute("href") || "").toLowerCase();
    const target = href.split("/").pop();
    const active =
      (section === "index" && target === "index.html") ||
      (section === "systems" && target === "systems.html") ||
      (section === "spells" && target === "spells.html") ||
      (section === "assignments" && target === "assignments.html") ||
      (section === "mobs" && target === "mobs.html") ||
      (section === "blocks" && target === "blocks.html") ||
      (section === "items" && target === "items.html") ||
      (section === "recipes" && target === "recipes.html");

    link.classList.toggle("active", active);
  });
}

function iconMarkup(icon, className = "") {
  return `<span class="sigil ${className}">${escapeHtml(icon || "AR")}</span>`;
}

function spellCardMarkup(entry) {
  return `
    <article class="topic-card spell-card rarity-${chipClass(entry.rarity)}">
      <div class="topic-card-header">
        ${iconMarkup(entry.icon)}
        <div>
          <h3>${escapeHtml(entry.name)}</h3>
          <p class="topic-meta">${escapeHtml(entry.school)}</p>
        </div>
      </div>
      <div class="meta-chips">
        <span class="meta-chip rarity-chip rarity-${chipClass(entry.rarity)}">${escapeHtml(entry.rarity)} rarity</span>
        <span class="meta-chip">${escapeHtml(entry.mana)} mana</span>
        <span class="meta-chip">${escapeHtml(entry.cooldown)} tick cooldown</span>
      </div>
      <p>${escapeHtml(entry.summary)}</p>
      <ul class="feature-list">
        ${(entry.features || []).map((feature) => `<li>${escapeHtml(feature)}</li>`).join("")}
      </ul>
      <a class="topic-card-link" href="${detailHref("spell", entry.id)}">Open spell entry</a>
    </article>
  `;
}

function mobCardMarkup(entry) {
  return `
    <article class="topic-card mob-card threat-${chipClass(entry.threat)}">
      <div class="topic-card-header">
        ${iconMarkup(entry.icon)}
        <div>
          <h3>${escapeHtml(entry.name)}</h3>
          <p class="topic-meta">${escapeHtml(entry.role)}</p>
        </div>
      </div>
      <div class="meta-chips">
        <span class="meta-chip">${escapeHtml(entry.habitat)}</span>
        <span class="meta-chip threat-chip threat-${chipClass(entry.threat)}">${escapeHtml(entry.threat)} threat</span>
      </div>
      <p>${escapeHtml(entry.summary)}</p>
      <ul class="feature-list">
        ${(entry.features || []).map((feature) => `<li>${escapeHtml(feature)}</li>`).join("")}
      </ul>
      <a class="topic-card-link" href="${detailHref("mob", entry.id)}">Open bestiary entry</a>
    </article>
  `;
}

function genericCardMarkup(entry) {
  const recipes = recipesForEntry(entry);

  return `
    <article class="topic-card">
      <div class="topic-card-header">
        ${iconMarkup(entry.icon || (entry.kind || entry.title || entry.name || "WK").slice(0, 2).toUpperCase())}
        <div>
          <h3>${escapeHtml(entry.name || entry.title)}</h3>
          <p class="topic-meta">${escapeHtml(entry.tier || entry.role || entry.kind || "Feature")}</p>
        </div>
      </div>
      <p>${escapeHtml(entry.summary)}</p>
      <ul class="feature-list">
        ${(entry.features || []).map((feature) => `<li>${escapeHtml(feature)}</li>`).join("")}
      </ul>
      ${recipes.length ? `
        <div class="inline-recipes">
          <div class="inline-recipes-title">Recipes</div>
          ${recipes.map((recipe) => recipeInlineMarkup(recipe)).join("")}
        </div>
      ` : ""}
    </article>
  `;
}

function recipeSlotMarkup(item, extraClass = "") {
  if (!item) {
    return `<div class="recipe-slot ${extraClass} is-empty" aria-hidden="true"></div>`;
  }

  return `
    <div class="recipe-slot ${extraClass}" title="${escapeHtml(item.name)}">
      <img src="${escapeHtml(item.texture)}" alt="${escapeHtml(item.name)}">
    </div>
  `;
}

function craftingRecipeMarkup(entry) {
  return `
    <div class="crafting-ui">
      <div class="crafting-grid">
        ${(entry.grid || []).map((item) => recipeSlotMarkup(item)).join("")}
      </div>
      <div class="crafting-arrow" aria-hidden="true">→</div>
      <div class="crafting-output">
        ${recipeSlotMarkup(entry.output, "is-output")}
        ${entry.output?.count > 1 ? `<span class="recipe-count">${escapeHtml(entry.output.count)}</span>` : ""}
      </div>
    </div>
  `;
}

function stationRecipeMarkup(entry) {
  return `
    <div class="station-ui station-${chipClass(entry.station)}">
      <div class="station-slot-row">
        ${(entry.slots || []).map((slot) => `
          <div class="station-slot-card">
            <span class="station-slot-label">${escapeHtml(slot.label)}</span>
            ${recipeSlotMarkup(slot.item)}
            <span class="station-slot-name">${escapeHtml(slot.item?.name || "Empty")}</span>
          </div>
        `).join("")}
      </div>
      <div class="station-output-row">
        <span class="recipe-arrow">→</span>
        <div class="station-slot-card is-output">
          <span class="station-slot-label">Result</span>
          ${recipeSlotMarkup(entry.output, "is-output")}
          <span class="station-slot-name">${escapeHtml(entry.output?.name || "")}</span>
          ${entry.output?.count > 1 ? `<span class="recipe-count">${escapeHtml(entry.output.count)}</span>` : ""}
        </div>
      </div>
    </div>
  `;
}

function recipeInlineMarkup(entry) {
  return `
    <div class="inline-recipe">
      <div class="inline-recipe-head">
        <span class="meta-chip">${escapeHtml(
          entry.station === "crafting_table"
            ? "Crafting Table"
            : entry.station === "wand_workbench"
              ? "Wandmaker's Bench"
              : "Broom Workbench"
        )}</span>
        <span class="meta-chip">${escapeHtml(entry.output?.count || 1)}x output</span>
      </div>
      ${entry.station === "crafting_table" ? craftingRecipeMarkup(entry) : stationRecipeMarkup(entry)}
    </div>
  `;
}

function allRecipes() {
  return typeof WIKI_RECIPES !== "undefined" && Array.isArray(WIKI_RECIPES) ? WIKI_RECIPES : [];
}

function recipesForEntry(entry) {
  const recipes = allRecipes();
  const entryName = String(entry?.name || "").toLowerCase();
  if (!entryName) return [];

  return recipes.filter((recipe) => {
    const outputName = String(recipe?.output?.name || recipe?.name || "").toLowerCase();
    return outputName === entryName;
  });
}

function recipeCardMarkup(entry) {
  const stationLabel =
    entry.station === "crafting_table"
      ? "Crafting Table"
      : entry.station === "wand_workbench"
        ? "Wandmaker's Bench"
        : "Broom Workbench";
  const typeLabel = entry.recipeType === "station" ? "station recipe" : `${entry.recipeType} recipe`;
  const detailText =
    entry.station === "crafting_table"
      ? "Shown in the actual 3x3 crafting layout."
      : "Shown in the actual station slot layout used by this mod.";

  return `
    <article class="topic-card recipe-card station-${chipClass(entry.station)}">
      <div class="topic-card-header">
        ${iconMarkup((entry.output?.name || entry.name || "RC").slice(0, 2).toUpperCase())}
        <div>
          <h3>${escapeHtml(entry.name)}</h3>
          <p class="topic-meta">${escapeHtml(entry.category)}</p>
        </div>
      </div>
      <div class="meta-chips">
        <span class="meta-chip">${escapeHtml(stationLabel)}</span>
        <span class="meta-chip">${escapeHtml(typeLabel)}</span>
        <span class="meta-chip">Output: ${escapeHtml(entry.output?.count || 1)}x</span>
      </div>
      ${entry.station === "crafting_table" ? craftingRecipeMarkup(entry) : stationRecipeMarkup(entry)}
      <p>${escapeHtml(detailText)}</p>
      ${entry.notes?.length ? `
        <ul class="feature-list">
          ${entry.notes.map((note) => `<li>${escapeHtml(note)}</li>`).join("")}
        </ul>
      ` : ""}
    </article>
  `;
}

function renderTopicCards(containerId, entries, renderer) {
  const container = document.getElementById(containerId);
  if (!container) return;
  container.innerHTML = entries.map((entry) => renderer(entry)).join("");
}

function renderFilterGroup(container, entries, field, label, state, onChange) {
  const values = [...new Set(entries.map((entry) => entry[field]).filter(Boolean))].sort((left, right) =>
    String(left).localeCompare(String(right))
  );

  container.innerHTML = `
    <div class="sidebar-group">
      <div class="sidebar-group-title">${escapeHtml(label)}</div>
      <div class="sidebar-chip-list">
        <button class="sidebar-chip ${state[field] ? "" : "active"}" data-filter-field="${escapeHtml(field)}" data-filter-value="">All</button>
        ${values.map((value) => `
          <button class="sidebar-chip ${state[field] === value ? "active" : ""}" data-filter-field="${escapeHtml(field)}" data-filter-value="${escapeHtml(value)}">
            ${escapeHtml(prettyLabel(value))}
          </button>
        `).join("")}
      </div>
    </div>
  `;

  container.querySelectorAll("[data-filter-field]").forEach((button) => {
    button.addEventListener("click", () => {
      state[field] = button.getAttribute("data-filter-value") || "";
      onChange();
    });
  });
}

function setupTopicBrowser(options) {
  const { containerId, entries, renderer, emptyId, filters = [] } = options;
  const container = document.getElementById(containerId);
  if (!container) return;

  const searchInput = document.querySelector("[data-search-input]");
  const emptyState = emptyId ? document.getElementById(emptyId) : null;
  const state = {};

  const refresh = () => {
    const query = (searchInput?.value || "").trim().toLowerCase();
    const filtered = entries.filter((entry) => {
      const haystack = [
        entry.name,
        entry.title,
        entry.summary,
        entry.detail,
        entry.kind,
        entry.role,
        entry.school,
        entry.habitat,
        ...(entry.features || []),
        ...(entry.notes || []),
        ...((entry.grid || []).filter(Boolean).map((item) => item.name)),
        ...((entry.slots || []).map((slot) => slot.item?.name).filter(Boolean))
      ].join(" ").toLowerCase();

      const queryMatch = query === "" || haystack.includes(query);
      const filterMatch = filters.every(({ field }) => !state[field] || entry[field] === state[field]);
      return queryMatch && filterMatch;
    });

    container.innerHTML = filtered.map((entry) => renderer(entry)).join("");
    if (emptyState) emptyState.hidden = filtered.length !== 0;

    filters.forEach((filter) => {
      const holder = document.getElementById(filter.containerId);
      if (holder) renderFilterGroup(holder, entries, filter.field, filter.label, state, refresh);
    });
  };

  if (searchInput) {
    searchInput.addEventListener("input", refresh);
  }

  refresh();
}

function renderRecipesPage() {
  if (currentSection() !== "recipes") return;

  const recipeCount = document.getElementById("recipe-count");
  const recipes = allRecipes();

  if (recipeCount) {
    recipeCount.textContent = String(recipes.length);
  }

  setupTopicBrowser({
    containerId: "recipe-list",
    entries: recipes,
    renderer: recipeCardMarkup,
    emptyId: "recipe-empty",
    filters: [
      { containerId: "recipe-station-filters", field: "station", label: "Crafting Station" },
      { containerId: "recipe-category-filters", field: "category", label: "Recipe Category" },
      { containerId: "recipe-type-filters", field: "recipeType", label: "Recipe Type" }
    ]
  });
}

const ASSIGNMENT_RARITIES = ["common", "uncommon", "rare", "epic"];
const ASSIGNMENT_LINES = { common: "1 to 2 task lines", uncommon: "2 task lines", rare: "3 task lines", epic: "3 to 4 task lines" };

function allAssignments() {
  return typeof WIKI_ASSIGNMENTS !== "undefined" && Array.isArray(WIKI_ASSIGNMENTS) ? WIKI_ASSIGNMENTS : [];
}

function scrollIcon(entry) {
  if (entry.dark) return "assets/assignment_dark.png";
  return entry.rarity === "common" ? "assets/assignment.png" : `assets/assignment_${entry.rarity}.png`;
}

function assignmentCardMarkup(entry) {
  return `
    <article class="topic-card assignment-card spell-card rarity-${chipClass(entry.rarity)} ${entry.dark ? "is-dark-orders" : ""}">
      <div class="topic-card-header">
        <img class="scroll-icon" src="${scrollIcon(entry)}" alt="" width="50" height="50">
        <div>
          <h3>${escapeHtml(entry.name)}</h3>
          <p class="topic-meta">${escapeHtml(entry.role)}</p>
        </div>
      </div>
      <div class="meta-chips">
        <span class="meta-chip rarity-chip rarity-${chipClass(entry.rarity)}">${escapeHtml(prettyLabel(entry.rarity))}</span>
        <span class="meta-chip ${entry.dark ? "dark-chip" : ""}">${escapeHtml(entry.source)}</span>
        ${(entry.needs || []).map((spell) => `<span class="meta-chip needs-chip">Needs ${escapeHtml(spell)}</span>`).join("")}
      </div>
      <blockquote class="assignment-quote">${escapeHtml(entry.summary)}</blockquote>
      <ul class="task-list">
        ${(entry.features || []).map((line) => `<li><span class="task-box" aria-hidden="true"></span><span>${escapeHtml(line)}</span></li>`).join("")}
      </ul>
      <div class="assignment-reward">Reward: Spellbook of ${escapeHtml(entry.name)}</div>
    </article>
  `;
}

function renderAssignmentsPage() {
  if (currentSection() !== "assignments") return;
  const entries = allAssignments();
  const share = typeof WIKI_ASSIGNMENT_SHARE === "number" ? WIKI_ASSIGNMENT_SHARE : 0.6;
  const taskPercent = Math.round(share * 100);

  const bar = document.getElementById("split-bar");
  if (bar) bar.style.setProperty("--task-share", `${taskPercent}%`);
  const taskValue = document.getElementById("split-task-value");
  const bookValue = document.getElementById("split-book-value");
  if (taskValue) taskValue.textContent = `${taskPercent}%`;
  if (bookValue) bookValue.textContent = `${100 - taskPercent}%`;

  const seals = document.getElementById("seal-row");
  if (seals) {
    const kinds = [
      { src: "assets/assignment.png", label: "Common" },
      { src: "assets/assignment_uncommon.png", label: "Uncommon" },
      { src: "assets/assignment_rare.png", label: "Rare" },
      { src: "assets/assignment_epic.png", label: "Epic" },
      { src: "assets/assignment_dark.png", label: "Dark Orders" }
    ];
    seals.innerHTML = kinds.map((kind) => `
      <figure><img src="${kind.src}" alt="" width="40" height="40"><figcaption>${escapeHtml(kind.label)}</figcaption></figure>
    `).join("");
  }

  const scale = document.getElementById("rarity-scale");
  if (scale) {
    scale.innerHTML = ASSIGNMENT_RARITIES.map((rarity) => {
      const count = entries.filter((entry) => entry.rarity === rarity).length;
      return `
        <div class="rarity-step rarity-border-${rarity}">
          <span class="meta-chip rarity-chip rarity-${rarity}">${prettyLabel(rarity)}</span>
          <strong>${escapeHtml(ASSIGNMENT_LINES[rarity])}</strong>
          <span>${count} spells</span>
        </div>
      `;
    }).join("");
  }

  setupTopicBrowser({
    containerId: "assignment-list",
    entries,
    renderer: assignmentCardMarkup,
    emptyId: "assignment-empty",
    filters: [
      { containerId: "assignment-rarity-filters", field: "rarity", label: "Rarity" },
      { containerId: "assignment-source-filters", field: "source", label: "Set by" }
    ]
  });
}

function renderHomePage() {
  if (currentSection() !== "index") return;

  const stats = WIKI_DATA.stats;
  const houseImages = {
    gryffindor: "assets/gryffindor.png",
    ravenclaw: "assets/ravenclaw.png",
    hufflepuff: "assets/hufflepuff.png",
    slytherin: "assets/slytherin.png"
  };
  const heroStats = document.getElementById("hero-stats");
  if (heroStats) {
    heroStats.innerHTML = `
      <div class="hero-stat"><strong>${stats.spells}</strong><span>implemented spells</span></div>
      <div class="hero-stat"><strong>${stats.mobs}</strong><span>major mobs and entities</span></div>
      <div class="hero-stat"><strong>${stats.blocks}</strong><span>notable magical blocks</span></div>
      <div class="hero-stat"><strong>${stats.systems}</strong><span>core progression systems</span></div>
    `;
  }

  const systemsGrid = document.getElementById("systems-grid");
  if (systemsGrid) {
    systemsGrid.innerHTML = WIKI_DATA.systems.map((system) => `
      <article class="wiki-card">
        <div class="topic-card-header">
          ${iconMarkup(system.icon)}
          <div>
            <strong>${escapeHtml(system.title)}</strong>
            <p>${escapeHtml(system.summary)}</p>
          </div>
        </div>
      </article>
    `).join("");
  }

  const houseStrip = document.getElementById("house-strip");
  if (houseStrip) {
    houseStrip.innerHTML = WIKI_DATA.houses.map((house) => `
      <article class="house-card ${escapeHtml(house.className)}">
        <img class="house-card-media" src="${escapeHtml(houseImages[house.className] || "assets/magic_book.png")}" alt="${escapeHtml(house.title)} crest icon">
        <h3>${escapeHtml(house.title)}</h3>
        <p>${escapeHtml(house.summary)}</p>
        <ul>
          ${house.perks.map((perk) => `<li>${escapeHtml(perk)}</li>`).join("")}
        </ul>
      </article>
    `).join("");
  }
}

function relatedEntries(kind, entry) {
  if (kind === "spell") {
    return WIKI_DATA.spells
      .filter((spell) => spell.id !== entry.id && (spell.school === entry.school || spell.rarity === entry.rarity))
      .slice(0, 5);
  }

  return WIKI_DATA.mobs
    .filter((mob) => mob.id !== entry.id && (mob.role === entry.role || mob.threat === entry.threat))
    .slice(0, 5);
}

function detailFacts(kind, entry) {
  if (kind === "spell") {
    return [
      { label: "School", value: entry.school },
      { label: "Rarity", value: entry.rarity },
      { label: "Mana Cost", value: `${entry.mana} mana` },
      { label: "Cooldown", value: `${entry.cooldown} ticks` }
    ];
  }

  return [
    { label: "Role", value: entry.role },
    { label: "Threat", value: entry.threat },
    { label: "Habitat", value: entry.habitat }
  ];
}

function renderEntryPage() {
  const body = document.body;
  const kind = body.dataset.entryKind;
  const id = body.dataset.entryId;
  if (!kind || !id) return;

  const entry = kind === "spell" ? WIKI_DATA.spellMap[id] : WIKI_DATA.mobMap[id];
  if (!entry) return;

  const hero = document.getElementById("entry-hero");
  const facts = document.getElementById("entry-facts");
  const features = document.getElementById("entry-features");
  const related = document.getElementById("entry-related");

  if (hero) {
    const meta = kind === "spell"
      ? `
          <span class="meta-chip">${escapeHtml(entry.school)}</span>
          <span class="meta-chip rarity-chip rarity-${chipClass(entry.rarity)}">${escapeHtml(entry.rarity)} rarity</span>
          <span class="meta-chip">${escapeHtml(entry.mana)} mana</span>
          <span class="meta-chip">${escapeHtml(entry.cooldown)} tick cooldown</span>
        `
      : `
          <span class="meta-chip">${escapeHtml(entry.role)}</span>
          <span class="meta-chip threat-chip threat-${chipClass(entry.threat)}">${escapeHtml(entry.threat)} threat</span>
          <span class="meta-chip">${escapeHtml(entry.habitat)}</span>
        `;

    hero.innerHTML = `
      <section class="entry-hero-panel ${kind === "spell" ? `rarity-border-${chipClass(entry.rarity)}` : `threat-border-${chipClass(entry.threat)}`}">
        <div class="entry-kicker">${kind === "spell" ? "Spell Archive Entry" : "Bestiary Archive Entry"}</div>
        <div class="entry-title-row">
          ${iconMarkup(entry.icon, "entry-sigil")}
          <div>
            <h1>${escapeHtml(entry.name)}</h1>
            <p>${escapeHtml(entry.summary)}</p>
          </div>
        </div>
        <div class="meta-chips">${meta}</div>
      </section>
    `;
  }

  if (facts) {
    facts.innerHTML = `
      <div class="fact-panel">
        <h2>Archive Facts</h2>
        <ul class="fact-list">
          ${detailFacts(kind, entry).map((fact) => `<li><strong>${escapeHtml(fact.label)}:</strong> ${escapeHtml(fact.value)}</li>`).join("")}
        </ul>
      </div>
    `;
  }

  if (features) {
    features.innerHTML = `
      <section class="topic-page">
        <h2>${kind === "spell" ? "Capabilities and Power" : "Behavior and Abilities"}</h2>
        <p>${escapeHtml(entry.detail)}</p>
        <ul class="feature-list detail-list">
          ${(entry.features || []).map((feature) => `<li>${escapeHtml(feature)}</li>`).join("")}
        </ul>
      </section>
    `;
  }

  if (related) {
    related.innerHTML = `
      <div class="fact-panel">
        <h2>${kind === "spell" ? "Related Spells" : "Related Entries"}</h2>
        <div class="related-links">
          ${relatedEntries(kind, entry).map((item) => `
            <a href="${detailHref(kind, item.id)}">
              ${iconMarkup(item.icon)}
              <span>
                <strong>${escapeHtml(item.name)}</strong>
                <small>${escapeHtml(kind === "spell" ? item.school : item.role)}</small>
              </span>
            </a>
          `).join("")}
        </div>
      </div>
    `;
  }

  document.title = `${entry.name} - Harry Potter Mod Wiki`;
}

document.addEventListener("DOMContentLoaded", () => {
  setActiveNav();
  renderHomePage();

  setupTopicBrowser({
    containerId: "spell-list",
    entries: WIKI_DATA.spells,
    renderer: spellCardMarkup,
    emptyId: "spell-empty",
    filters: [
      { containerId: "spell-school-filters", field: "school", label: "Spell Schools" },
      { containerId: "spell-rarity-filters", field: "rarity", label: "Spell Rarity" }
    ]
  });

  setupTopicBrowser({
    containerId: "mob-list",
    entries: WIKI_DATA.mobs,
    renderer: mobCardMarkup,
    emptyId: "mob-empty",
    filters: [
      { containerId: "mob-role-filters", field: "role", label: "Mob Roles" },
      { containerId: "mob-threat-filters", field: "threat", label: "Threat Bands" }
    ]
  });

  renderTopicCards("block-list", WIKI_DATA.blocks, genericCardMarkup);
  renderTopicCards("item-list", WIKI_DATA.items, genericCardMarkup);
  renderTopicCards("system-list", WIKI_DATA.systems, genericCardMarkup);
  renderEntryPage();
  renderRecipesPage();
  renderAssignmentsPage();
});

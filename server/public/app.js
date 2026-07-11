const state = {
  cards: [],
  decks: [],
  visible: [],
  activeTab: 'collection',
  username: location.pathname.split('/').filter(Boolean).at(-1) || ''
};
const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];
const elements = {
  loader: $('#loader'), app: $('#app'), username: $('#username'), summary: $('#summary'),
  estimatedValue: $('#estimatedValue'), estimatedValueCoverage: $('#estimatedValueCoverage'),
  profileSearch: $('#profileSearch'), profileUsername: $('#profileUsername'),
  profileSearchButton: $('#profileSearchButton'), profileSearchError: $('#profileSearchError'),
  tabs: $$('.tab'), panels: $$('[data-panel]'), collectionSection: $('#collectionSection'),
  decksSection: $('#decksSection'), decksGrid: $('#decksGrid'), deckCount: $('#deckCount'), decksEmpty: $('#decksEmpty'),
  grid: $('#grid'), empty: $('#empty'), search: $('#search'), rarity: $('#rarity'),
  modal: $('#detailsModal'), modalBody: $('#modalBody'), toast: $('#toast'),
  summonButton: $('#summonButton'), summoning: $('#summoning'), exodiaImage: $('#exodiaImage'),
  cardSummon: $('#cardSummon'), summonCardImage: $('#summonCardImage'),
  summonTitle: $('#summonTitle'), summonSubtitle: $('#summonSubtitle'), skipSummon: $('#skipSummon')
};

const escapeHtml = value => String(value ?? '').replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char]);
const imageFor = card => card.image_url || `https://images.ygoprodeck.com/images/cards/${card.card_id}.jpg`;
const deckImageFor = card => card.imageUrl || `https://images.ygoprodeck.com/images/cards/${card.cardId}.jpg`;
const formatMoney = value => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value || 0);
const normalized = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

function legendaryPresentation(card) {
  const name = normalized(card.name);
  if (name.includes('exodia') || name.includes('do proibido') || name.includes('forbidden one'))
    return { key: 'exodia', title: 'Exodia foi libertado', subtitle: 'Os cinco selos do Proibido respondem ao chamado', accent: '#ffd54f' };
  if (name.includes('blue-eyes') || name.includes('olhos azuis'))
    return { key: 'blue-eyes', title: 'O Dragao Branco desperta', subtitle: 'O relampago branco atravessa os ceus', accent: '#bdefff' };
  if (name.includes('dark magician') || name.includes('mago negro') || name.includes('maga negra'))
    return { key: 'dark-magician', title: 'O Mago Supremo se ergue', subtitle: 'Magia negra, rompa o veu entre os mundos', accent: '#b06cff' };
  if (['slifer', 'obelisk', 'winged dragon of ra', 'dragao alado de ra'].some(value => name.includes(value))) {
    const accent = name.includes('obelisk') ? '#55a8ff' : name.includes('slifer') ? '#ff554d' : '#ffd54f';
    return { key: 'god', title: 'Uma Divindade desce ao campo', subtitle: 'O duelo se curva diante do poder de um Deus Egipcio', accent };
  }
  if (name.includes('red-eyes') || name.includes('olhos vermelhos'))
    return { key: 'red-eyes', title: 'As chamas negras ardem', subtitle: 'O potencial oculto do dragao foi libertado', accent: '#ff4d45' };
  if (['stardust dragon', 'poeira estelar', 'black luster soldier', 'blue-eyes ultimate', 'mago do caos'].some(value => name.includes(value)))
    return { key: 'legendary', title: 'Uma Lenda foi revelada', subtitle: 'O coracao das cartas reconhece seu poder', accent: '#d4af37' };
  return null;
}

let summonActive = false;
function playCardSummon(card, presentation) {
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return Promise.resolve();
  summonActive = true;
  elements.cardSummon.className = `card-summon kind-${presentation.key}`;
  elements.cardSummon.style.setProperty('--summon', presentation.accent);
  elements.summonCardImage.src = imageFor(card);
  elements.summonCardImage.alt = card.name;
  elements.summonTitle.textContent = presentation.title;
  elements.summonSubtitle.textContent = presentation.subtitle;
  elements.cardSummon.hidden = false;

  return new Promise(resolve => {
    let completed = false;
    const finish = () => {
      if (completed) return;
      completed = true;
      clearTimeout(timer);
      elements.cardSummon.hidden = true;
      elements.skipSummon.removeEventListener('click', finish);
      summonActive = false;
      resolve();
    };
    const timer = setTimeout(finish, 3300);
    elements.skipSummon.addEventListener('click', finish);
  });
}

async function revealCard(id) {
  if (summonActive) return;
  const card = state.cards.find(item => String(item.card_id) === String(id));
  const presentation = card && legendaryPresentation(card);
  if (presentation) await playCardSummon(card, presentation);
  openDetails(id);
}

function hasExodia(cards) {
  const names = cards.map(card => normalized(card.name));
  const pieces = ['exodia the forbidden one', 'right arm of the forbidden one', 'left arm of the forbidden one', 'right leg of the forbidden one', 'left leg of the forbidden one'];
  const pt = ['exodia, o proibido', 'braco direito do proibido', 'braco esquerdo do proibido', 'perna direita do proibido', 'perna esquerda do proibido'];
  return pieces.every((piece, index) => names.some(name => name.includes(piece) || name.includes(pt[index])));
}

function render() {
  const query = normalized(elements.search.value);
  const rarity = elements.rarity.value;
  state.visible = state.cards.filter(card => (!query || normalized(card.name).includes(query)) && (!rarity || card.rarity === rarity));
  elements.grid.innerHTML = state.visible.map(card => `
    <article class="card" tabindex="0" role="button" data-id="${card.card_id}" aria-label="Ver detalhes de ${escapeHtml(card.name)}">
      <img src="${escapeHtml(imageFor(card))}" alt="${escapeHtml(card.name)}" loading="lazy">
      <h2>${escapeHtml(card.name)}</h2>
      <p class="card-meta"><span>${escapeHtml(card.collection_name || card.rarity || card.attribute || 'Carta')}</span><span class="quantity">×${card.quantity}</span></p>
    </article>`).join('');
  elements.empty.hidden = state.visible.length > 0;
}

function configureFilters() {
  const rarities = [...new Set(state.cards.map(card => card.rarity).filter(Boolean))].sort();
  elements.rarity.innerHTML = '<option value="">Todas as raridades</option>' + rarities.map(value => `<option>${escapeHtml(value)}</option>`).join('');
}

function renderDecks() {
  elements.deckCount.textContent = `${state.decks.length} deck${state.decks.length === 1 ? '' : 's'}`;
  elements.decksEmpty.hidden = state.decks.length > 0;
  elements.decksGrid.innerHTML = state.decks.map(deck => {
    const owned = deck.cards.filter(card => card.status === 'owned').reduce((sum, card) => sum + card.quantity, 0);
    const missing = deck.cards.filter(card => card.status === 'missing').reduce((sum, card) => sum + card.quantity, 0);
    const purchasable = deck.cards.filter(card => card.status === 'missing' && card.affiliate?.url).reduce((sum, card) => sum + card.quantity, 0);
    return `<details class="deck-panel">
      <summary>
        <span><strong>${escapeHtml(deck.name)}</strong><small>${deck.cards.length} carta${deck.cards.length === 1 ? '' : 's'} unica${deck.cards.length === 1 ? '' : 's'}</small></span>
        <span class="deck-summary-status"><b class="owned">${owned} na colecao</b><b class="missing">${missing} faltando</b>${purchasable ? `<b class="shopping">${purchasable} com oferta</b>` : ''}</span>
      </summary>
      <div class="deck-card-grid">
        ${deck.cards.map(card => `<article class="deck-card status-${card.status}">
          <img src="${escapeHtml(deckImageFor(card))}" alt="${escapeHtml(card.name)}" loading="lazy">
          <div><strong>${escapeHtml(card.name)}</strong><small>${escapeHtml(card.rarity || card.attribute || card.type || 'Carta')}</small>
            <span class="deck-status">${card.status === 'owned' ? 'Na Colecao' : 'Faltando'}${card.quantity > 1 ? ` x${card.quantity}` : ''}</span>
            ${card.status === 'missing' && card.affiliate?.url ? `<a class="deck-buy-link" href="${escapeHtml(card.affiliate.url)}" target="_blank" rel="sponsored noopener noreferrer">Comprar carta</a>` : ''}
          </div>
        </article>`).join('') || '<p class="deck-empty">Este deck ainda nao possui cartas.</p>'}
      </div>
    </details>`;
  }).join('');
}

function switchTab(tab) {
  state.activeTab = tab === 'decks' ? 'decks' : 'collection';
  elements.tabs.forEach(button => {
    const active = button.dataset.tab === state.activeTab;
    button.classList.toggle('is-active', active);
    button.setAttribute('aria-selected', active ? 'true' : 'false');
  });
  elements.panels.forEach(panel => { panel.hidden = panel.dataset.panel !== state.activeTab; });
}

async function loadCollection({ live = false } = {}) {
  const response = await fetch(`/api/collections/${encodeURIComponent(state.username)}`, { cache: live ? 'no-store' : 'default' });
  if (!response.ok) throw new Error(response.status === 404 ? 'Duelista nao encontrado' : 'Nao foi possivel abrir a colecao');
  const payload = await response.json();
  state.cards = payload.cards;
  state.decks = payload.decks || [];
  elements.username.textContent = payload.profile.username;
  const total = state.cards.reduce((sum, card) => sum + card.quantity, 0);
  const priced = state.cards.reduce((sum, card) => card.estimated_unit_value == null ? sum : sum + card.quantity, 0);
  const estimatedValue = state.cards.reduce((sum, card) => {
    const unitValue = Number(card.estimated_unit_value);
    return Number.isFinite(unitValue) && unitValue >= 0 ? sum + unitValue * card.quantity : sum;
  }, 0);
  elements.summary.textContent = `${total} carta${total === 1 ? '' : 's'} • ${state.cards.length} registro${state.cards.length === 1 ? '' : 's'} unicos`;
  elements.estimatedValue.textContent = formatMoney(estimatedValue);
  elements.estimatedValueCoverage.textContent = priced < total
    ? `${priced} de ${total} cartas com cotacao disponivel`
    : 'Estimativa baseada nas cotacoes disponiveis';
  configureFilters(); render(); renderDecks(); switchTab(state.activeTab);
  elements.summonButton.hidden = !hasExodia(state.cards);
  document.title = `Colecao de ${payload.profile.username} — Yugidex`;
  if (live) showToast('Colecao sincronizada com sucesso!');
}

async function openDetails(id) {
  const basic = state.cards.find(card => String(card.card_id) === String(id));
  elements.modalBody.innerHTML = `<div class="skeleton"></div><div class="skeleton"></div>`;
  elements.modal.showModal();
  try {
    const response = await fetch(`/api/card-details?id=${encodeURIComponent(id)}`);
    if (!response.ok) throw new Error();
    const card = await response.json();
    const name = card.localized?.name || card.name;
    const description = card.localized?.description || card.desc || '';
    const affiliate = card.affiliate?.url ? card.affiliate : null;
    const affiliateHost = affiliate ? new URL(affiliate.url).hostname.replace(/^www\./, '') : '';
    card.prices ||= { source: 'indisponivel', editions: [] };
    elements.modalBody.innerHTML = `
      <img src="${escapeHtml(card.card_images?.[0]?.image_url || imageFor(basic))}" alt="${escapeHtml(name)}">
      <section class="details"><p class="eyebrow">REGISTRO DO ORACULO</p><h2>${escapeHtml(name)}</h2>
        <p class="subtitle">${escapeHtml(card.type)} • ${escapeHtml(card.race || card.attribute || '')}</p>
        <div class="stats">${card.attribute ? `<span>${escapeHtml(card.attribute)}</span>` : ''}${card.level ? `<span>Nivel ${card.level}</span>` : ''}${card.atk != null ? `<span>ATK ${card.atk}</span>` : ''}${card.def != null ? `<span>DEF ${card.def}</span>` : ''}</div>
        <p class="description">${escapeHtml(description)}</p>
        <div class="prices"><p class="eyebrow">COTACOES • ${escapeHtml(card.prices.source)}</p>
          ${(card.prices.editions || []).slice(0, 10).map(item => `<div class="price"><span>${escapeHtml(item.edition)}</span><strong>${formatMoney(item.price)}</strong></div>`).join('') || '<p class="subtitle">Nenhuma cotacao disponivel.</p>'}
        </div>
        ${affiliate ? `<div class="affiliate-box">
          <p class="eyebrow">OFERTA AFILIADA${affiliate.provider ? ` • ${escapeHtml(affiliate.provider)}` : ''}</p>
          <a class="affiliate-link" href="${escapeHtml(affiliate.url)}" target="_blank" rel="sponsored noopener noreferrer">🔗 ${escapeHtml(affiliate.label || 'Ver oferta da carta')}</a>
          <a class="affiliate-url" href="${escapeHtml(affiliate.url)}" target="_blank" rel="sponsored noopener noreferrer">${escapeHtml(affiliateHost || affiliate.url)}</a>
          ${affiliate.disclosure ? `<small>${escapeHtml(affiliate.disclosure)}</small>` : ''}
        </div>` : ''}
      </section>`;
  } catch { elements.modalBody.innerHTML = '<div class="details"><h2>O oraculo silenciou</h2><p class="description">Nao foi possivel carregar os detalhes desta carta agora.</p></div>'; }
}

function showToast(message) {
  elements.toast.textContent = message; elements.toast.classList.add('show');
  clearTimeout(showToast.timer); showToast.timer = setTimeout(() => elements.toast.classList.remove('show'), 3600);
}

async function searchProfile(event) {
  event.preventDefault();
  const username = elements.profileUsername.value.trim().replace(/^@+/, '');
  elements.profileSearchError.hidden = true;
  if (!/^[a-zA-Z0-9_]{3,30}$/.test(username)) {
    elements.profileSearchError.textContent = 'Digite um username valido com 3 a 30 letras, numeros ou _.';
    elements.profileSearchError.hidden = false;
    return;
  }

  elements.profileSearchButton.disabled = true;
  elements.profileSearchButton.textContent = 'Buscando...';
  try {
    const response = await fetch(`/api/profiles/${encodeURIComponent(username)}`, { cache: 'no-store' });
    if (!response.ok) throw new Error(response.status === 404 ? 'Duelista nao encontrado.' : 'Nao foi possivel pesquisar agora.');
    const profile = await response.json();
    location.assign(profile.publicUrl);
  } catch (error) {
    elements.profileSearchError.textContent = error.message;
    elements.profileSearchError.hidden = false;
  } finally {
    elements.profileSearchButton.disabled = false;
    elements.profileSearchButton.textContent = 'Pesquisar perfil';
  }
}

function connectLive() {
  const stream = new EventSource(`/api/colecao-stream/${encodeURIComponent(state.username)}`);
  stream.onmessage = () => loadCollection({ live: true }).catch(() => showToast('Sincronizacao recebida; tentando novamente...'));
}

elements.search.addEventListener('input', render);
elements.rarity.addEventListener('change', render);
elements.tabs.forEach(button => button.addEventListener('click', () => switchTab(button.dataset.tab)));
elements.profileSearch.addEventListener('submit', searchProfile);
elements.grid.addEventListener('click', event => event.target.closest('.card') && revealCard(event.target.closest('.card').dataset.id));
elements.grid.addEventListener('keydown', event => { if (['Enter', ' '].includes(event.key) && event.target.closest('.card')) revealCard(event.target.closest('.card').dataset.id); });
elements.modal.addEventListener('click', event => { if (event.target.matches('[data-close]') || event.target === elements.modal) elements.modal.close(); });
elements.summonButton.addEventListener('click', () => {
  const exodia = state.cards.find(card => normalized(card.name).includes('exodia'));
  elements.exodiaImage.src = imageFor(exodia); elements.summoning.hidden = false;
});
$('#bowButton').addEventListener('click', () => { elements.summoning.hidden = true; });

loadCollection().then(() => { elements.loader.remove(); elements.app.hidden = false; connectLive(); })
  .catch(error => { elements.loader.querySelector('p').textContent = error.message; });

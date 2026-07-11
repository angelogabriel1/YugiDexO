const $ = selector => document.querySelector(selector);
const state = {
  token: localStorage.getItem('yugidex_admin_token') || '',
  links: [],
  editingId: null
};

const elements = {
  loginPanel: $('#loginPanel'), adminPanel: $('#adminPanel'), loginForm: $('#loginForm'),
  email: $('#email'), password: $('#password'), adminName: $('#adminName'), adminEmail: $('#adminEmail'),
  logoutButton: $('#logoutButton'), status: $('#status'), affiliateForm: $('#affiliateForm'),
  linkId: $('#linkId'), cardId: $('#cardId'), cardName: $('#cardName'), affiliateUrl: $('#affiliateUrl'),
  provider: $('#provider'), label: $('#label'), notes: $('#notes'), active: $('#active'),
  formTitle: $('#formTitle'), clearButton: $('#clearButton'), linksList: $('#linksList'),
  linkSearch: $('#linkSearch'), linkCount: $('#linkCount'), lookupId: $('#lookupId'),
  lookupName: $('#lookupName'), lookupButton: $('#lookupButton'), lookupResult: $('#lookupResult')
};

const escapeHtml = value => String(value ?? '').replace(/[&<>'"]/g, char => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
})[char]);

function showStatus(message, timeout = 3600) {
  elements.status.textContent = message;
  elements.status.classList.add('show');
  clearTimeout(showStatus.timer);
  showStatus.timer = setTimeout(() => elements.status.classList.remove('show'), timeout);
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}),
      ...(options.headers || {})
    }
  });
  if (response.status === 204) return null;
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.error || 'O servidor recusou a operação.');
  return payload;
}

function setAuthenticated(user) {
  elements.loginPanel.hidden = true;
  elements.adminPanel.hidden = false;
  elements.adminName.textContent = user.name || 'Administrador';
  elements.adminEmail.textContent = user.email || '';
}

function setLoggedOut() {
  state.token = '';
  localStorage.removeItem('yugidex_admin_token');
  elements.loginPanel.hidden = false;
  elements.adminPanel.hidden = true;
}

async function loadSession() {
  if (!state.token) return setLoggedOut();
  try {
    const payload = await api('/api/admin/me');
    setAuthenticated(payload.user);
    await loadLinks();
  } catch (error) {
    setLoggedOut();
    showStatus(error.message, 6000);
  }
}

async function login(event) {
  event.preventDefault();
  const button = elements.loginForm.querySelector('button');
  button.disabled = true;
  try {
    const auth = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: elements.email.value.trim(), password: elements.password.value })
    });
    if (!auth.token) throw new Error('Confirme seu email antes de entrar.');
    state.token = auth.token;
    localStorage.setItem('yugidex_admin_token', auth.token);
    await loadSession();
    showStatus('Painel aberto com sucesso.');
  } catch (error) {
    showStatus(error.message, 6000);
  } finally {
    button.disabled = false;
  }
}

function payloadFromForm() {
  return {
    cardId: elements.cardId.value ? Number(elements.cardId.value) : null,
    cardName: elements.cardName.value.trim(),
    affiliateUrl: elements.affiliateUrl.value.trim(),
    provider: elements.provider.value.trim() || 'Mercado Livre',
    label: elements.label.value.trim() || null,
    active: elements.active.checked,
    notes: elements.notes.value.trim() || null
  };
}

function clearForm() {
  state.editingId = null;
  elements.linkId.value = '';
  elements.cardId.value = '';
  elements.cardName.value = '';
  elements.affiliateUrl.value = '';
  elements.provider.value = 'Mercado Livre';
  elements.label.value = '';
  elements.notes.value = '';
  elements.active.checked = true;
  elements.formTitle.textContent = 'Novo link afiliado';
  elements.lookupResult.hidden = true;
}

async function saveAffiliate(event) {
  event.preventDefault();
  const button = elements.affiliateForm.querySelector('button[type="submit"]');
  button.disabled = true;
  try {
    const id = state.editingId;
    await api(id ? `/api/admin/affiliate-links/${encodeURIComponent(id)}` : '/api/admin/affiliate-links', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(payloadFromForm())
    });
    showStatus(id ? 'Link atualizado.' : 'Link cadastrado.');
    clearForm();
    await loadLinks();
  } catch (error) {
    showStatus(error.message, 6000);
  } finally {
    button.disabled = false;
  }
}

async function loadLinks() {
  const query = elements.linkSearch.value.trim();
  const payload = await api(`/api/admin/affiliate-links?query=${encodeURIComponent(query)}`);
  state.links = payload.links || [];
  renderLinks();
}

function renderLinks() {
  elements.linkCount.textContent = String(state.links.length);
  elements.linksList.innerHTML = state.links.map(link => `
    <article class="link-card" data-id="${escapeHtml(link.id)}">
      <header>
        <div>
          <strong>${escapeHtml(link.cardName)}</strong>
          <div class="badges">
            ${link.cardId ? `<span class="badge">ID ${escapeHtml(link.cardId)}</span>` : ''}
            <span class="badge">${escapeHtml(link.provider)}</span>
            <span class="badge ${link.active ? '' : 'off'}">${link.active ? 'Ativo' : 'Inativo'}</span>
          </div>
        </div>
      </header>
      <a href="${escapeHtml(link.affiliateUrl)}" target="_blank" rel="sponsored noopener noreferrer">${escapeHtml(link.affiliateUrl)}</a>
      ${link.label ? `<small>Botão: ${escapeHtml(link.label)}</small>` : ''}
      ${link.notes ? `<small>${escapeHtml(link.notes)}</small>` : ''}
      <div class="actions">
        <button type="button" class="ghost" data-action="edit">Editar</button>
        <button type="button" class="danger" data-action="delete">Excluir</button>
      </div>
    </article>
  `).join('') || '<p class="muted">Nenhum link cadastrado ainda.</p>';
}

function editLink(link) {
  state.editingId = link.id;
  elements.linkId.value = link.id;
  elements.cardId.value = link.cardId || '';
  elements.cardName.value = link.cardName;
  elements.affiliateUrl.value = link.affiliateUrl;
  elements.provider.value = link.provider || 'Mercado Livre';
  elements.label.value = link.label || '';
  elements.notes.value = link.notes || '';
  elements.active.checked = !!link.active;
  elements.formTitle.textContent = 'Editar link afiliado';
  scrollTo({ top: elements.affiliateForm.offsetTop - 120, behavior: 'smooth' });
}

async function handleListClick(event) {
  const card = event.target.closest('.link-card');
  const action = event.target.dataset.action;
  if (!card || !action) return;
  const link = state.links.find(item => item.id === card.dataset.id);
  if (!link) return;
  if (action === 'edit') return editLink(link);
  if (action === 'delete' && confirm(`Excluir o link de ${link.cardName}?`)) {
    await api(`/api/admin/affiliate-links/${encodeURIComponent(link.id)}`, { method: 'DELETE' });
    showStatus('Link excluído.');
    await loadLinks();
  }
}

async function lookupCard() {
  const params = new URLSearchParams();
  if (elements.lookupId.value) params.set('id', elements.lookupId.value);
  else if (elements.lookupName.value.trim()) params.set('name', elements.lookupName.value.trim());
  else return showStatus('Informe o ID ou nome da carta.');

  elements.lookupButton.disabled = true;
  try {
    const payload = await api(`/api/admin/card-lookup?${params}`);
    const { card, existing } = payload;
    elements.cardId.value = card.cardId;
    elements.cardName.value = card.name;
    elements.lookupResult.innerHTML = `
      <img src="${escapeHtml(card.imageUrl)}" alt="${escapeHtml(card.name)}">
      <div>
        <strong>${escapeHtml(card.name)}</strong>
        <p class="muted">ID ${escapeHtml(card.cardId)}${existing ? ' • já possui link cadastrado' : ''}</p>
      </div>`;
    elements.lookupResult.hidden = false;
    if (existing) editLink(existing);
  } catch (error) {
    showStatus(error.message, 6000);
  } finally {
    elements.lookupButton.disabled = false;
  }
}

elements.loginForm.addEventListener('submit', login);
elements.logoutButton.addEventListener('click', () => { setLoggedOut(); showStatus('Sessão encerrada.'); });
elements.affiliateForm.addEventListener('submit', saveAffiliate);
elements.clearButton.addEventListener('click', clearForm);
elements.lookupButton.addEventListener('click', lookupCard);
elements.linksList.addEventListener('click', event => handleListClick(event).catch(error => showStatus(error.message, 6000)));
elements.linkSearch.addEventListener('input', () => {
  clearTimeout(elements.linkSearch.timer);
  elements.linkSearch.timer = setTimeout(() => loadLinks().catch(error => showStatus(error.message, 6000)), 280);
});

loadSession();

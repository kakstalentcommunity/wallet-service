(() => {
  const $ = (selector) => document.querySelector(selector);
  const state = { mode: 'login', action: null, accessToken: sessionStorage.getItem('wallet.access'), refreshToken: sessionStorage.getItem('wallet.refresh'), refreshPromise: null, phone: sessionStorage.getItem('wallet.phone') || '', toastTimer: null };
  const authView = $('#auth-view');
  const appView = $('#app-view');
  const authForm = $('#auth-form');
  const authMessage = $('#auth-message');
  const actionModal = $('#action-modal');
  const actionForm = $('#action-form');

  function setMessage(element, message = '', type = 'error') {
    element.textContent = message;
    element.classList.toggle('is-success', type === 'success');
  }

  function explainError(data, fallback) {
    return data?.detail || data?.message || data?.title || fallback;
  }

  async function request(path, options = {}, canRefresh = true) {
    const headers = new Headers(options.headers || {});
    if (state.accessToken && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${state.accessToken}`);
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    let response;
    try {
      response = await fetch(path, { ...options, headers });
    } catch {
      throw new Error('Could not reach the wallet service. Check that the app is running, then try again.');
    }
    if (response.status === 401 && canRefresh && state.refreshToken && path !== '/api/auth/refresh') {
      const refreshed = await refreshSession();
      if (refreshed) return request(path, options, false);
      clearSession();
      showAuth('login');
      throw new Error('Your session expired. Please sign in again.');
    }
    if (!response.ok) {
      let data = {};
      try { data = await response.json(); } catch { /* response may have no JSON body */ }
      throw new Error(explainError(data, `Request failed (${response.status}).`));
    }
    if (response.status === 204) return null;
    const contentType = response.headers.get('content-type') || '';
    return contentType.includes('application/json') ? response.json() : null;
  }

  async function refreshSession() {
    if (state.refreshPromise) return state.refreshPromise;
    if (!state.refreshToken) return false;
    state.refreshPromise = (async () => {
      try {
        const pair = await request('/api/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: state.refreshToken }) }, false);
        saveSession(pair);
        return true;
      } catch {
        return false;
      }
    })();
    try {
      return await state.refreshPromise;
    } finally {
      state.refreshPromise = null;
    }
  }

  function saveSession(pair) {
    state.accessToken = pair.accessToken;
    state.refreshToken = pair.refreshToken;
    sessionStorage.setItem('wallet.access', pair.accessToken);
    sessionStorage.setItem('wallet.refresh', pair.refreshToken);
    if (state.phone) sessionStorage.setItem('wallet.phone', state.phone);
  }

  function clearSession() {
    state.accessToken = null;
    state.refreshToken = null;
    state.phone = '';
    sessionStorage.removeItem('wallet.access');
    sessionStorage.removeItem('wallet.refresh');
    sessionStorage.removeItem('wallet.phone');
  }

  function showAuth(mode = 'login') {
    state.mode = mode;
    authView.hidden = false;
    appView.hidden = true;
    $('#action-modal').hidden = true;
    $('#auth-title').textContent = mode === 'login' ? 'Sign in' : 'Create account';
    $('#auth-subtitle').textContent = mode === 'login' ? 'Enter your mobile number and password.' : 'Register with your Kenyan mobile number.';
    $('#auth-password').autocomplete = mode === 'login' ? 'current-password' : 'new-password';
    $('#auth-submit').textContent = mode === 'login' ? 'Sign in' : 'Create account';
    document.querySelectorAll('.auth-tab').forEach((tab) => {
      const active = tab.dataset.mode === mode;
      tab.classList.toggle('is-active', active);
      tab.setAttribute('aria-selected', String(active));
    });
    setMessage(authMessage, '');
  }

  function showDashboard() {
    authView.hidden = true;
    appView.hidden = false;
    const phone = state.phone || 'Your account';
    $('#profile-phone').textContent = phone;
    loadDashboard();
  }

  function formatMoney(minor) {
    const value = Number(minor || 0) / 100;
    const amount = new Intl.NumberFormat('en-KE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
    return `KSh ${amount}`;
  }

  function formatDate(value) {
    if (!value) return 'Just now';
    return new Intl.DateTimeFormat('en-KE', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }).format(new Date(value));
  }

  async function loadDashboard() {
    setMessage($('#global-message'), '');
    const [balanceResult, historyResult] = await Promise.allSettled([
      request('/api/accounts/me'),
      request('/api/transactions?page=0&size=6')
    ]);
    const warnings = [];
    if (balanceResult.status === 'fulfilled') {
      const account = balanceResult.value;
      $('#balance-value').textContent = formatMoney(account.balanceMinor, account.currency);
      $('#balance-updated').textContent = `Updated ${new Intl.DateTimeFormat('en-KE', { hour: 'numeric', minute: '2-digit' }).format(new Date())}`;
    } else {
      warnings.push('Your balance could not be loaded. Please refresh and try again.');
    }
    if (historyResult.status === 'fulfilled') {
      renderHistory(historyResult.value);
    } else {
      renderHistoryError();
      warnings.push('Your recent activity could not be loaded. Your balance is unaffected.');
    }
    if (!state.accessToken) return;
    setMessage($('#global-message'), warnings.join(' '));
  }

  function renderHistory(page) {
    const list = $('#activity-list');
    const items = page?.content || [];
    if (!items.length) {
      list.innerHTML = '<div class="empty-state"><strong>No transactions yet</strong><span>Deposits, transfers and withdrawals will appear here.</span></div>';
      return;
    }
    list.innerHTML = items.map((item) => {
      const direction = item.direction === 'DEBIT' ? 'debit' : 'credit';
      const isDebit = direction === 'debit';
      const symbol = isDebit ? '−' : '+';
      const type = String(item.type || 'Transaction').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (character) => character.toUpperCase());
      const status = String(item.status || '').toLowerCase();
      const amount = formatMoney(item.amountMinor, item.currency);
      return `<div class="transaction-row"><span class="transaction-symbol ${direction}">${symbol}</span><span class="transaction-main"><strong>${escapeHtml(type)} <span class="transaction-status ${status === 'pending' ? 'pending' : ''}">${escapeHtml(item.status || '')}</span></strong><small>${escapeHtml(item.referenceCode || '')} · ${escapeHtml(formatDate(item.createdAt))}</small></span><span class="transaction-amount ${direction}">${isDebit ? '−' : '+'} ${escapeHtml(amount)}</span></div>`;
    }).join('');
  }

  function renderHistoryError() {
    const empty = document.createElement('div');
    empty.className = 'empty-state error-state';
    const icon = document.createElement('span');
    icon.className = 'empty-icon';
    icon.textContent = '!';
    const title = document.createElement('strong');
    title.textContent = 'Transactions are unavailable';
    const detail = document.createElement('span');
    detail.textContent = 'Your balance is unaffected. Select “Refresh list” to try again.';
    empty.append(icon, title, detail);
    $('#activity-list').replaceChildren(empty);
  }

  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character]);
  }

  async function handleAuth(event) {
    event.preventDefault();
    const button = $('#auth-submit');
    const phone = $('#auth-phone').value.trim();
    const password = $('#auth-password').value;
    const registering = state.mode === 'register';
    button.disabled = true;
    setMessage(authMessage, '');
    try {
      if (registering) {
        await request('/api/auth/register', { method: 'POST', body: JSON.stringify({ phone, password }) }, false);
        showAuth('login');
      }
      state.phone = phone;
      const pair = await request('/api/auth/login', { method: 'POST', body: JSON.stringify({ phone, password }) }, false);
      saveSession(pair);
      showDashboard();
      showToast(registering ? 'Account created.' : 'Signed in.');
    } catch (error) {
      setMessage(authMessage, error.message);
    } finally {
      button.disabled = false;
    }
  }

  const actions = {
    deposit: { title: 'Add money', eyebrow: 'M-PESA DEPOSIT', description: 'Request a deposit using the configured M-Pesa sandbox service.', submit: 'Request deposit', hint: 'Enter the amount in Kenyan shillings (KSh).', fields: [{ label: 'M-Pesa number', name: 'phone', placeholder: '0712 345 678', type: 'tel' }] },
    transfer: { title: 'Send money', eyebrow: 'WALLET TRANSFER', description: 'Transfer funds to another registered wallet.', submit: 'Send transfer', hint: 'The transfer fee will be included in your receipt.', fields: [{ label: 'Recipient mobile number', name: 'receiverPhone', placeholder: '0712 345 678', type: 'tel' }] },
    withdraw: { title: 'Withdraw to M-Pesa', eyebrow: 'M-PESA WITHDRAWAL', description: 'Withdrawal requests use the configured test provider.', submit: 'Request withdrawal', hint: 'Withdrawals must be in whole Kenyan shillings.', fields: [{ label: 'M-Pesa number', name: 'phone', placeholder: '0712 345 678', type: 'tel' }] }
  };

  function openAction(name) {
    state.action = name;
    const action = actions[name];
    $('#modal-eyebrow').textContent = action.eyebrow;
    $('#modal-title').textContent = action.title;
    $('#modal-description').textContent = action.description;
    $('#amount-hint').textContent = action.hint;
    $('#action-submit').textContent = action.submit;
    $('#action-fields').innerHTML = action.fields.map((field) => `<label class="field"><span>${escapeHtml(field.label)}</span><input name="${escapeHtml(field.name)}" type="${escapeHtml(field.type)}" placeholder="${escapeHtml(field.placeholder)}" autocomplete="tel" required /></label>`).join('');
    $('#action-amount').value = '';
    $('#action-amount').step = name === 'withdraw' ? '1' : '0.01';
    $('#action-amount').min = name === 'withdraw' ? '1' : '1';
    setMessage($('#action-message'), '');
    actionModal.hidden = false;
    const firstField = $('#action-fields input');
    (firstField || $('#action-amount')).focus();
  }

  function closeAction() {
    actionModal.hidden = true;
    state.action = null;
  }

  async function submitAction(event) {
    event.preventDefault();
    const form = new FormData(actionForm);
    const amountMinor = Math.round(Number(form.get('amount')) * 100);
    if (!Number.isSafeInteger(amountMinor) || amountMinor <= 0) {
      setMessage($('#action-message'), 'Enter a valid amount greater than zero.');
      return;
    }
    const button = $('#action-submit');
    button.disabled = true;
    setMessage($('#action-message'), '');
    const actionName = state.action;
    try {
      let path;
      let body;
      const key = `nuru-${crypto.randomUUID()}`;
      const headers = { 'Idempotency-Key': key };
      if (actionName === 'transfer') {
        path = '/api/transfers';
        body = { receiverPhone: form.get('receiverPhone'), amountMinor };
      } else if (actionName === 'deposit') {
        path = '/api/deposits/mpesa';
        body = { phone: form.get('phone'), amountMinor };
      } else {
        path = '/api/withdrawals';
        body = { phone: form.get('phone'), amountMinor };
      }
      const receipt = await request(path, { method: 'POST', headers, body: JSON.stringify(body) });
      closeAction();
      const reference = receipt?.referenceCode ? ` Ref ${receipt.referenceCode}.` : '';
      const message = actionName === 'deposit' ? 'M-Pesa deposit request submitted.' : actionName === 'withdraw' ? 'Withdrawal request received.' : 'Transfer completed.';
      showToast(`${message}${reference}`);
      await loadDashboard();
    } catch (error) {
      setMessage($('#action-message'), error.message);
    } finally {
      button.disabled = false;
    }
  }

  function showToast(message) {
    const toast = $('#toast');
    toast.textContent = message;
    toast.classList.add('is-visible');
    clearTimeout(state.toastTimer);
    state.toastTimer = setTimeout(() => toast.classList.remove('is-visible'), 4200);
  }

  async function logout() {
    const refreshToken = state.refreshToken;
    if (refreshToken) {
      try { await request('/api/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken }) }, false); } catch { /* local sign-out still completes */ }
    }
    clearSession();
    showAuth('login');
    $('#auth-phone').value = '';
    $('#auth-password').value = '';
  }

  document.querySelectorAll('.auth-tab').forEach((tab) => tab.addEventListener('click', () => showAuth(tab.dataset.mode)));
  authForm.addEventListener('submit', handleAuth);
  document.querySelectorAll('[data-action]').forEach((button) => button.addEventListener('click', () => openAction(button.dataset.action)));
  actionForm.addEventListener('submit', submitAction);
  $('#modal-close').addEventListener('click', closeAction);
  actionModal.addEventListener('click', (event) => { if (event.target === actionModal) closeAction(); });
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape' && !actionModal.hidden) closeAction(); });
  $('#logout-button').addEventListener('click', logout);
  $('#refresh-button').addEventListener('click', loadDashboard);
  $('#activity-refresh').addEventListener('click', loadDashboard);

  if (state.accessToken) showDashboard();
  else showAuth('login');
})();

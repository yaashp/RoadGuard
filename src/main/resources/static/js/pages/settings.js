import { api } from '../api.js';
import { state, applyTheme, setSession, clearSession } from '../state.js';
import { h, icon, mount, field, showFieldErrors, toast, pageHeader } from '../ui.js';
import { fmtDate } from '../format.js';
import { navigate } from '../router.js';
import { LANGUAGES, languageByCode, languageLabel } from '../i18n/config.js';
import { t, currentLanguage, setLanguage } from '../i18n/index.js';

export async function settingsPage({ container }) {
  const me = state.me;
  const prefs = state.prefs;

  // ---- profile ----
  const name = h('input', { name: 'name', type: 'text', value: me.name, autocomplete: 'name' });
  const phone = h('input', { name: 'phone', type: 'tel', value: me.phone || '', autocomplete: 'tel' });
  const profileForm = h('form', { novalidate: true, onsubmit: async (e) => {
    e.preventDefault();
    try { setSession(await api.put('/api/users/me', { name: name.value, phone: phone.value })); toast(t('settings.profileSaved'), 'success'); showFieldErrors(profileForm, {}); }
    catch (err) { if (!showFieldErrors(profileForm, err.fieldErrors)) toast(err.message, 'error'); }
  } }, field(t('settings.name'), name), field(t('settings.email'), h('input', { type: 'email', value: me.email, disabled: true, 'aria-describedby': 'email-note' }), { hint: t('settings.emailHint') }), field(t('settings.phone'), phone), h('button', { class: 'btn primary', type: 'submit' }, t('settings.saveProfile')));

  // ---- password ----
  const cur = h('input', { name: 'currentPassword', type: 'password', autocomplete: 'current-password' });
  const next = h('input', { name: 'newPassword', type: 'password', autocomplete: 'new-password' });
  const pwForm = h('form', { novalidate: true, onsubmit: async (e) => {
    e.preventDefault();
    try { await api.post('/api/users/me/password', { currentPassword: cur.value, newPassword: next.value }); cur.value = ''; next.value = ''; showFieldErrors(pwForm, {}); toast(t('settings.passwordChanged'), 'success'); }
    catch (err) { if (!showFieldErrors(pwForm, err.fieldErrors)) toast(err.message, 'error'); }
  } }, field(t('settings.currentPassword'), cur), field(t('settings.newPassword'), next, { hint: t('settings.passwordHint') }), h('button', { class: 'btn primary', type: 'submit' }, t('settings.password')));

  // ---- preferences ----
  async function save(change, okMessage) {
    try { state.prefs = await api.put('/api/users/me/preferences', change); if (change.theme) applyTheme(change.theme); toast(okMessage || t('settings.preferenceSaved'), 'success'); }
    catch (e) { toast(e.message, 'error'); }
  }
  const toggle = (label, key, hint) => {
    const box = h('input', { type: 'checkbox', checked: !!prefs[key], onchange: () => save({ [key]: box.checked }) });
    return h('label', { class: 'toggle', style: 'display:flex;margin-bottom:12px' }, box, h('span', null, h('strong', null, label), hint ? h('div', { class: 'hint' }, hint) : null));
  };
  const themeChoice = h('div', { class: 'choice-grid', style: 'grid-template-columns:repeat(2,1fr);max-width:320px' }, [['light', t('settings.light'), 'sun'], ['dark', t('settings.dark'), 'moon']].map(([v, l, ic]) =>
    h('label', { class: 'choice' }, h('input', { type: 'radio', name: 'theme', value: v, checked: (prefs.theme === 'dark') === (v === 'dark'), onchange: () => save({ theme: v }, t('settings.themeSaved')) }), h('span', null, icon(ic, 16), l))));

  // ---- language ----
  const activeLang = languageByCode(prefs.language || currentLanguage());
  const langSelect = h('select', { name: 'language', onchange: (e) => chooseLanguage(e.target.value, langSelect) },
    LANGUAGES.map((l) => h('option', { value: l.code, lang: l.code, dir: l.dir, selected: l.code === activeLang.code }, languageLabel(l))));
  const langBadge = h('span', { class: 'lang-badge' }, icon('check', 14), h('span', { lang: activeLang.code, dir: activeLang.dir }, languageLabel(activeLang)));

  async function chooseLanguage(code, select) {
    const previous = activeLang.code;
    try { state.prefs = await api.put('/api/users/me/preferences', { language: code }); }
    catch (e) { toast(e.message, 'error'); select.value = previous; return; }
    const chosen = languageByCode(code);
    setLanguage(code);   // redraws the page in the new language and sets the text direction
    toast(t('lang.changed', { name: languageLabel(chosen) }), 'success');
    const again = document.getElementById('lang-select');
    if (again) again.focus();
  }

  const languageCard = h('div', { class: 'card' }, h('h2', null, 'Language / भाषा'),
    field(t('lang.select'), langSelect, { id: 'lang-select' }),
    h('p', { class: 'hint', id: 'lang-hint' }, t('lang.hint')),
    h('div', { class: 'lang-active' }, h('span', { class: 'hint' }, t('lang.active')), langBadge));

  mount(container, pageHeader(t('settings.title'), t('settings.subtitle')),
    h('div', { class: 'grid two' },
      h('div', { class: 'card' }, h('h2', null, t('settings.profile')), profileForm),
      h('div', { class: 'card' }, h('h2', null, t('settings.password')), pwForm)),
    h('div', { class: 'grid two', style: 'margin-top:16px' },
      languageCard,
      h('div', { class: 'card' }, h('h2', null, t('settings.appearance')), h('div', { class: 'field' }, h('span', { class: 'label-text' }, t('settings.theme')), themeChoice))),
    h('div', { class: 'grid two', style: 'margin-top:16px' },
      h('div', { class: 'card' }, h('h2', null, t('settings.notifications')),
        toggle(t('settings.notifyStatus'), 'notifyOnStatusChange', t('settings.notifyStatusHint')),
        toggle(t('settings.notifySubmission'), 'notifyOnSubmission', t('settings.notifySubmissionHint')),
        h('p', { class: 'hint' }, t('settings.notifyNote'))),
      h('div', { class: 'card' }, h('h2', null, t('settings.mapData')),
        toggle(t('settings.showResolved'), 'mapShowResolved', t('settings.showResolvedHint')),
        toggle(t('settings.sampleData'), 'showSampleData', t('settings.sampleDataHint')),
        toggle(t('settings.leaderboard'), 'showOnLeaderboard', t('settings.leaderboardHint')))),
    h('div', { class: 'card', style: 'margin-top:16px' }, h('h2', null, t('settings.account')),
      h('dl', { class: 'kv' }, h('dt', null, t('settings.name')), h('dd', null, me.name), h('dt', null, t('settings.email')), h('dd', null, me.email), h('dt', null, t('settings.role')), h('dd', null, me.role === 'admin' ? t('settings.roleAdmin') : t('settings.roleCitizen')),
        h('dt', null, t('settings.memberSince')), h('dd', null, me.createdAt ? fmtDate(me.createdAt) : t('settings.memberSinceUnknown'))),
      h('div', { class: 'btn-row', style: 'margin-top:14px' }, h('button', { class: 'btn danger', type: 'button', onclick: async () => {
        try { await api.post('/api/auth/logout'); } catch (e) { /* ignore */ }
        clearSession(); toast(t('settings.loggedOut'), 'success'); navigate('/');
      } }, icon('logout', 16), t('settings.logout')))));
}

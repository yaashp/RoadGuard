import { api } from '../api.js';
import { state, loadMe, setSession } from '../state.js';
import { h, mount, field, showFieldErrors, toast, icon } from '../ui.js';
import { navigate } from '../router.js';
import { t } from '../i18n/index.js';

const isLocal = () => ['localhost', '127.0.0.1', '[::1]'].includes(location.hostname);

function afterLogin() {
  const back = sessionStorage.getItem('rg_after_login');
  sessionStorage.removeItem('rg_after_login');
  if (back && back.startsWith('/') && !back.startsWith('//') && !back.startsWith('/login') && !back.startsWith('/register')) navigate(back); else navigate('/');
}

function submitState(btn, busy, label) {
  btn.disabled = busy;
  btn.textContent = busy ? t('auth.pleaseWait') : label;
}

export async function loginPage({ container }) {
  if (state.me) { navigate('/'); return; }
  const email = h('input', { type: 'email', name: 'email', autocomplete: 'username', required: true, placeholder: 'you@example.com' });
  const password = h('input', { type: 'password', name: 'password', autocomplete: 'current-password', required: true });
  const submit = h('button', { class: 'btn primary', type: 'submit', style: 'width:100%' }, t('nav.login'));
  const banner = h('div', { class: 'banner error', hidden: true, role: 'alert' });
  const form = h('form', { novalidate: true, onsubmit: async (e) => {
    e.preventDefault();
    banner.hidden = true;
    if (showFieldErrors(form, { ...(email.value.trim() ? {} : { email: t('auth.enterEmail') }), ...(password.value ? {} : { password: t('auth.enterPassword') }) })) return;
    submitState(submit, true, t('nav.login'));
    try {
      setSession(await api.post('/api/auth/login', { email: email.value.trim(), password: password.value }));
      await loadMe();
      toast(t('auth.welcomeBack', { name: state.me.name.split(' ')[0] }), 'success');
      afterLogin();
    } catch (err) {
      banner.textContent = err.message; banner.hidden = false;
      showFieldErrors(form, err.fieldErrors);
      submitState(submit, false, t('nav.login'));
    }
  } }, banner, field(t('auth.email'), email), field(t('auth.password'), password), submit);

  mount(container, h('div', { class: 'card form-narrow' }, h('h1', null, t('nav.login')), h('p', { class: 'muted' }, t('auth.loginIntro')), form,
    h('p', { style: 'margin-top:14px' }, t('auth.newHere') + ' ', h('a', { href: '/register' }, t('auth.createAccount'))),
    isLocal() ? h('div', { class: 'banner info small' }, icon('info', 16), h('span', null, 'Local demo account: ', h('code', null, 'demo@roadguard.app'), ' / ', h('code', null, 'password123'), '. Administrator: ', h('code', null, 'admin@roadguard.app'), ' / ', h('code', null, 'admin123'), '.')) : null));
  email.focus();
}

export async function registerPage({ container }) {
  if (state.me) { navigate('/'); return; }
  const f = (name, type, extra = {}) => h('input', { name, type, ...extra });
  const name = f('name', 'text', { autocomplete: 'name', required: true });
  const email = f('email', 'email', { autocomplete: 'email', required: true });
  const phone = f('phone', 'tel', { autocomplete: 'tel', placeholder: 'optional' });
  const password = f('password', 'password', { autocomplete: 'new-password', required: true });
  const confirm = f('confirm', 'password', { autocomplete: 'new-password', required: true });
  const submit = h('button', { class: 'btn primary', type: 'submit', style: 'width:100%' }, t('auth.createAccount'));
  const banner = h('div', { class: 'banner error', hidden: true, role: 'alert' });
  const form = h('form', { novalidate: true, onsubmit: async (e) => {
    e.preventDefault();
    banner.hidden = true;
    const errors = {};
    if (name.value.trim().length < 2) errors.name = t('auth.enterName');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) errors.email = t('auth.validEmail');
    if (password.value.length < 8 || !/[A-Za-z]/.test(password.value) || !/\d/.test(password.value)) errors.password = t('auth.passwordWeak');
    if (confirm.value !== password.value) errors.confirm = t('auth.passwordMismatch');
    if (showFieldErrors(form, errors)) return;
    submitState(submit, true, t('auth.createAccount'));
    try {
      setSession(await api.post('/api/auth/register', { name: name.value.trim(), email: email.value.trim(), phone: phone.value.trim(), password: password.value }));
      await loadMe();
      toast(t('auth.accountCreated'), 'success');
      afterLogin();
    } catch (err) {
      banner.textContent = err.message; banner.hidden = false;
      showFieldErrors(form, err.fieldErrors);
      submitState(submit, false, t('auth.createAccount'));
    }
  } }, banner, field(t('auth.fullName'), name), field(t('auth.email'), email), field(t('auth.phoneOptional'), phone),
  field(t('auth.password'), password, { hint: t('auth.passwordRule') }), field(t('auth.confirmPassword'), confirm), submit);
  mount(container, h('div', { class: 'card form-narrow' }, h('h1', null, t('auth.createTitle')), h('p', { class: 'muted' }, t('auth.createIntro')), form,
    h('p', { style: 'margin-top:14px' }, t('auth.alreadyRegistered') + ' ', h('a', { href: '/login' }, t('nav.login')))));
  name.focus();
}

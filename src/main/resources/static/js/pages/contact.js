// Contact form. Validated in the browser for a quick response and again on the server, which is the one that counts.
// The hidden "website" field is a honeypot: people never see it, so only automated form-fillers complete it.
import { api } from '../api.js';
import { h, mount, pageHeader, toast } from '../ui.js';

const FIELDS = {
  name: { label: 'Your name', type: 'text', autocomplete: 'name', maxlength: '80' },
  email: { label: 'Email address', type: 'email', autocomplete: 'email', maxlength: '254' },
  subject: { label: 'Subject (optional)', type: 'text', autocomplete: 'off', maxlength: '120' },
};

export async function contactPage({ container }) {
  const inputs = {};
  const errors = {};
  const sent = h('p', { class: 'banner success', role: 'status', hidden: true }, 'Thanks. Your message has been received.');

  const field = (key, el) => {
    const err = h('p', { class: 'field-error', id: 'contact-' + key + '-error', role: 'alert' });
    errors[key] = err;
    el.setAttribute('aria-describedby', err.id);
    return h('div', { class: 'field' }, h('label', { for: el.id }, el.dataset.label), el, err);
  };

  for (const [key, cfg] of Object.entries(FIELDS)) {
    inputs[key] = h('input', { id: 'contact-' + key, name: key, type: cfg.type, autocomplete: cfg.autocomplete, maxlength: cfg.maxlength, dataset: { label: cfg.label } });
  }
  inputs.message = h('textarea', { id: 'contact-message', name: 'message', rows: '6', maxlength: '2000', dataset: { label: 'Message' } });
  const messageError = h('p', { class: 'field-error', id: 'contact-message-error', role: 'alert' });
  errors.message = messageError;
  inputs.message.setAttribute('aria-describedby', 'contact-message-error');
  inputs.website = h('input', { id: 'contact-website', name: 'website', type: 'text', tabindex: '-1', autocomplete: 'off' });
  const honeypot = h('div', { class: 'hp', 'aria-hidden': 'true' }, h('label', { for: 'contact-website' }, 'Leave this field empty'), inputs.website);

  function validate() {
    const v = {
      name: inputs.name.value.trim(), email: inputs.email.value.trim(),
      subject: inputs.subject.value.trim(), message: inputs.message.value.trim(),
    };
    const problems = {
      name: v.name.length < 2 ? 'Please enter your name (at least 2 characters).' : '',
      email: v.email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.email) ? '' : 'Please enter a valid email address.',
      subject: v.subject.length > 120 ? 'Subject is too long (maximum 120 characters).' : '',
      message: v.message.length < 10 ? 'Please write at least 10 characters.' : '',
    };
    for (const key of Object.keys(problems)) {
      errors[key].textContent = problems[key];
      inputs[key].setAttribute('aria-invalid', String(!!problems[key]));
    }
    const firstBad = Object.keys(problems).find((k) => problems[k]);
    if (firstBad) inputs[firstBad].focus();
    return firstBad ? null : v;
  }

  const submit = h('button', { class: 'btn primary', type: 'submit' }, 'Send message');
  const form = h('form', {
    class: 'card form-narrow contact-form', novalidate: true,
    onsubmit: async (e) => {
      e.preventDefault();
      sent.hidden = true;
      const v = validate();
      if (!v) return;
      submit.disabled = true;
      submit.textContent = 'Sending…';
      try {
        const res = await api.post('/api/contact', { ...v, website: inputs.website.value });
        form.reset();
        Object.values(errors).forEach((el) => { el.textContent = ''; });
        sent.textContent = res.message || 'Thanks. Your message has been received.';
        sent.hidden = false;
      } catch (err) {
        Object.entries(err.fieldErrors || {}).forEach(([k, msg]) => { if (errors[k]) errors[k].textContent = msg; });
        toast(err.message || 'Your message could not be sent. Please try again.', 'error');
      } finally {
        submit.disabled = false;
        submit.textContent = 'Send message';
      }
    },
  },
    field('name', inputs.name), field('email', inputs.email), field('subject', inputs.subject),
    h('div', { class: 'field' }, h('label', { for: 'contact-message' }, 'Message'), inputs.message, h('span', { class: 'hint' }, 'Tell us what you need, in 10 to 2000 characters.'), messageError),
    honeypot, sent, h('div', { class: 'btn-row' }, submit));

  mount(container, pageHeader('Contact us', 'Questions, suggestions or a problem with the site? Send us a message.'), form);
}

// Runtime for the interface language: the current language, the t() lookup and the direction of the page.
import { DEFAULT_LANGUAGE, languageByCode } from './config.js';
import { MESSAGES } from './messages.js';

const STORAGE_KEY = 'rg_lang';
let current = DEFAULT_LANGUAGE;

export function currentLanguage() { return current; }

/** The language saved on this browser (used before the account preference is known, and when logged out). */
export function storedLanguage() {
  try { return localStorage.getItem(STORAGE_KEY) || DEFAULT_LANGUAGE; } catch (e) { return DEFAULT_LANGUAGE; }
}

/** Translates a key into the current language, falling back to English, then to the key itself. */
export function t(key, vars) {
  const text = (MESSAGES[current] && MESSAGES[current][key]) || MESSAGES[DEFAULT_LANGUAGE][key] || key;
  if (!vars) return text;
  return text.replace(/\{(\w+)\}/g, (match, name) => (name in vars ? String(vars[name]) : match));
}

/** Sets the page language and text direction without announcing the change. */
export function applyLanguage(code) {
  const lang = languageByCode(code);
  current = lang.code;
  const root = document.documentElement;
  root.setAttribute('lang', lang.code);
  root.setAttribute('dir', lang.dir);
  try { localStorage.setItem(STORAGE_KEY, lang.code); } catch (e) { /* storage may be unavailable */ }
}

/** Changes the language and tells the app to redraw its pages. */
export function setLanguage(code) {
  applyLanguage(code);
  window.dispatchEvent(new Event('languagechange'));
}

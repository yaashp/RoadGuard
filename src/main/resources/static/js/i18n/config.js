// Central list of the languages RoadGuard supports.
// The codes must match the allowed list in PreferenceService.java (server-side validation).
// `native` is the name written in the language's own script; `name` is the English name.
// `dir` is the text direction: 'ltr' for all languages except Urdu, which is written right to left.

export const DEFAULT_LANGUAGE = 'en';

export const LANGUAGES = [
  { code: 'en', name: 'English',   native: 'English',  dir: 'ltr' },
  { code: 'hi', name: 'Hindi',     native: 'हिन्दी',      dir: 'ltr' },
  { code: 'bn', name: 'Bengali',   native: 'বাংলা',      dir: 'ltr' },
  { code: 'mr', name: 'Marathi',   native: 'मराठी',      dir: 'ltr' },
  { code: 'te', name: 'Telugu',    native: 'తెలుగు',      dir: 'ltr' },
  { code: 'ta', name: 'Tamil',     native: 'தமிழ்',       dir: 'ltr' },
  { code: 'gu', name: 'Gujarati',  native: 'ગુજરાતી',     dir: 'ltr' },
  { code: 'ur', name: 'Urdu',      native: 'اردو',        dir: 'rtl' },
  { code: 'kn', name: 'Kannada',   native: 'ಕನ್ನಡ',       dir: 'ltr' },
  { code: 'or', name: 'Odia',      native: 'ଓଡ଼ିଆ',       dir: 'ltr' },
  { code: 'ml', name: 'Malayalam', native: 'മലയാളം',      dir: 'ltr' },
  { code: 'pa', name: 'Punjabi',   native: 'ਪੰਜਾਬੀ',       dir: 'ltr' },
];

/** Looks up a language by code; unknown or missing codes fall back to the default language. */
export function languageByCode(code) {
  return LANGUAGES.find((l) => l.code === code) || LANGUAGES[0];
}

/** Label shown in pickers: the native name followed by the English name, e.g. "मराठी (Marathi)". */
export function languageLabel(lang) {
  return lang.code === 'en' ? lang.name : `${lang.native} (${lang.name})`;
}

---
inclusion: always
---

# Localization Rules

Supported languages: English, Hindi, Marathi.

## Non-negotiable rules

- UI-string translation only. User-generated content (goal names, task text) is stored and displayed exactly as entered — never auto-translated. Do not add translation logic to any user-input field.
- Android: `res/values-<lang>/strings.xml` (en, hi, mr) — no hardcoded UI strings in layouts or code.
- Web: `react-i18next`, switchable without a full page reload.
- Backend-generated text (notifications, error messages returned to clients): Spring `MessageSource`/`LocaleResolver`, keyed off the user's stored `preferredLanguage`/`locale` field.
- Every user entity carries a `locale` field — this is a core schema field, not an optional add-on. Confirm it exists before building any feature that generates user-facing text.
- Missing translation for the user's locale falls back to English — never render a blank string or a raw translation key to the user.
- Video recommendation pipeline (YouTube Data API calls) passes `relevanceLanguage` and `regionCode` derived from the user's locale on every search/validation call.

## When generating any user-facing string

Add it to all three locale resource sets (en, hi, mr) in the same change — never ship a string in English only and defer translation.

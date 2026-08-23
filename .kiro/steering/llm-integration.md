---
inclusion: always
---

# Bring-Your-Own-LLM Integration Rules

Users configure their own OpenAI/ChatGPT, Google/Gemini, or Anthropic/Claude API key. DopaShift never funds or provides a platform-side LLM.

## Non-negotiable rules

- Route all LLM calls through one provider-agnostic abstraction layer (LiteLLM or equivalent internal adapter). Adding/switching a provider must never require touching feature logic — only the adapter.
- API keys are encrypted at rest, never logged in plaintext, and rotatable/removable by the user at any time with immediate invalidation of the old key.
- LLM calls send only minimum necessary context (goal name/keywords) — never raw telemetry, never full task history. This is a privacy boundary, not a performance optimization; do not expand the payload for convenience.
- Video suggestions: the LLM is asked for direct video links. **Never trust an LLM-returned link unvalidated.** Always validate server-side (YouTube Data API video-lookup on the extracted video ID) before displaying or embedding. A plain API-only LLM call without a search/browsing tool enabled can hallucinate links that don't exist — this is expected, not an edge case, and the fallback path must be exercised routinely, not treated as rare.
- If the LLM response has no parseable link, or the link fails validation, fall back automatically and silently to the YouTube Data API keyword-search pipeline. No user-facing error or interruption in the intercept overlay. The source (LLM vs. fallback) is logged for the user to review later in an activity/settings view, not surfaced inline.
- If no LLM provider is configured, AI-assisted suggestion features are disabled gracefully (hidden or clearly labeled unavailable) and video suggestions use the YouTube-search pipeline exclusively — never error.
- Cache LLM-suggested video links per goal-keyword-set with a configurable TTL — do not call the user's LLM on every overlay trigger; the user's own provider may have rate limits or per-call cost.
- Surface in settings, informationally, which supported providers are known to have live web-search/browsing capability — this guides user expectations, it does not restrict provider choice.

## When generating any LLM-calling code

Assume the response may be malformed, empty, rate-limited, or fabricated. Every LLM integration point needs the fallback path implemented in the same change, not as a follow-up.

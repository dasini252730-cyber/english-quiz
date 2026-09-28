# AI learning Edge Function

`ai-learning` is a public, unauthenticated MVP endpoint because this app has no sign-in. It accepts only bounded `content` and `meaning` requests, validates provider output, and never writes generated content to a database. Since there is no account identity, requests cannot be reliably associated with an individual user for per-user quotas; input limits and timeouts are the current baseline abuse defenses.

## Secrets and model

Set secrets for the Supabase project using the CLI or Dashboard. Never commit a real key or put the Anthropic key in Android configuration. Always pass `--project-ref` so the secret cannot land on the wrong project.

```powershell
supabase secrets set ANTHROPIC_API_KEY=<your-key> ANTHROPIC_MODEL=claude-sonnet-5 --project-ref <ref>
```

`ANTHROPIC_MODEL` is optional; the function defaults to `claude-sonnet-5`. Since 2026-09-26 the project secret is set to `claude-haiku-4-5-20251001` (백로그 021): measured against the deployed function, conversation went from 20.3s to 12.2s and story from 30.4s to 10.3s with the same validated payload shape. Unset the secret to return to Sonnet. Local development can use `supabase/functions/.env`, which is ignored by Git. The function calls the Anthropic Messages API and constrains the reply with `output_config.format` (`json_schema`), so the model must answer with the exact learning payload shape. A turn that stops for `max_tokens` or `refusal` is rejected rather than parsed. Anthropic does not store request content by default; this says nothing about provider abuse-monitoring retention.

## Request contract

```json
{"action":"content","mode":"conversation","difficulty":2,"reviewExpressions":["pull it off"]}
```

`mode` is `conversation` or `story`; `difficulty` is 1, 2, or 3; review expressions are optional and capped at 12.

```json
{"action":"meaning","expression":"pull it off","context":"We can pull it off together."}
```

Successful responses use `{ "data": ... }`. Failures use `{ "error": { "code": "..." } }`. Content includes `title`, `mode`, `segments`, and phrase `expressions`; each phrase location is calculated by the function from the validated segment text.

## Local checks

Run the isolated handler tests with Node 24 or newer:

```powershell
node --experimental-strip-types --test-isolation=none --test supabase/functions/ai-learning/index.test.mjs
```

Running the Edge Function in Supabase's local runtime requires Deno and Docker. Do not deploy before a Supabase project is deliberately linked and configured with the secret.

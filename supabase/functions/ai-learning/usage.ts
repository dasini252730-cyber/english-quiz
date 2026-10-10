/**
 * Call accounting for the ai-learning function (백로그 062): one row per provider attempt in the
 * `ai_usage` table, and the day's count for the daily limit. Both go through PostgREST with the
 * service role, so the table needs no policy and the app can never read or write it.
 *
 * Accounting must never cost the learner a passage: a store that is not configured is simply
 * absent, and a store that fails is logged and treated as "unknown" (the limit then lets the call
 * through, and a lost row is a lost row).
 */

export type Environment = (name: string) => string | undefined;
export type Fetcher = typeof fetch;

export type UsageEntry = {
  day: string;
  action: string;
  model: string;
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  cacheWriteTokens: number;
  durationMs: number;
  ok: boolean;
  errorCode: string | null;
};

/** How long accounting may take before the learner's call goes on without it. */
const ACCOUNTING_TIMEOUT_MS = 3_000;

export type UsageStore = {
  /** Rows for [day], or null when the store could not say. */
  countForDay(day: string): Promise<number | null>;
  record(entry: UsageEntry): Promise<void>;
};

/** The learner's calendar day: the limit resets at midnight in Seoul, not UTC. */
export function seoulDay(nowEpochMillis: number): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" })
    .format(new Date(nowEpochMillis));
}

/** The token counts of a Messages API response, zero where the provider sent none. */
export function usageOf(payload: unknown): Pick<UsageEntry, "inputTokens" | "outputTokens" | "cacheReadTokens" | "cacheWriteTokens"> {
  const usage = (payload as { usage?: Record<string, unknown> } | null)?.usage ?? {};
  const count = (key: string) => (typeof usage[key] === "number" ? (usage[key] as number) : 0);
  return {
    inputTokens: count("input_tokens"),
    outputTokens: count("output_tokens"),
    cacheReadTokens: count("cache_read_input_tokens"),
    cacheWriteTokens: count("cache_creation_input_tokens"),
  };
}

/** Null until `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are set (Supabase sets both on deploy). */
export function createUsageStore(getEnvironment: Environment, fetcher: Fetcher): UsageStore | null {
  const url = getEnvironment("SUPABASE_URL")?.trim();
  const key = getEnvironment("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!url || !url.startsWith("https://") || !key) return null;
  const table = `${url.replace(/\/$/, "")}/rest/v1/ai_usage`;
  const headers = { apikey: key, authorization: `Bearer ${key}`, "content-type": "application/json" };

  return {
    async countForDay(day) {
      try {
        const response = await fetcher(`${table}?select=id&day=eq.${day}`, {
          method: "GET",
          headers: { ...headers, prefer: "count=exact", range: "0-0" },
          signal: AbortSignal.timeout(ACCOUNTING_TIMEOUT_MS),
        });
        if (!response.ok) throw new Error(`http ${response.status}`);
        // "0-0/12" or "*/0": the total after the slash is what the limit reads.
        const total = response.headers.get("content-range")?.split("/")[1];
        const count = Number(total);
        return Number.isFinite(count) ? count : null;
      } catch (error) {
        console.error(`usage count failed: ${error instanceof Error ? error.message : "unknown"}`);
        return null;
      }
    },
    async record(entry) {
      try {
        const response = await fetcher(table, {
          method: "POST",
          headers: { ...headers, prefer: "return=minimal" },
          signal: AbortSignal.timeout(ACCOUNTING_TIMEOUT_MS),
          body: JSON.stringify({
            day: entry.day,
            action: entry.action,
            model: entry.model,
            input_tokens: entry.inputTokens,
            output_tokens: entry.outputTokens,
            cache_read_tokens: entry.cacheReadTokens,
            cache_write_tokens: entry.cacheWriteTokens,
            duration_ms: entry.durationMs,
            ok: entry.ok,
            error_code: entry.errorCode,
          }),
        });
        if (!response.ok) throw new Error(`http ${response.status}`);
      } catch (error) {
        console.error(`usage record failed: ${error instanceof Error ? error.message : "unknown"}`);
      }
    },
  };
}

/** Constant-time equality for the app token, so a wrong token learns nothing from the timing. */
export function tokenMatches(presented: string | null, expected: string): boolean {
  if (!presented || presented.length !== expected.length) return false;
  let difference = 0;
  for (let index = 0; index < expected.length; index++) {
    difference |= presented.charCodeAt(index) ^ expected.charCodeAt(index);
  }
  return difference === 0;
}

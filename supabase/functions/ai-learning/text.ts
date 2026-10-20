/*
 * Text checks shared by the request validators (백로그 075): one place for "a line of plain text",
 * "mostly Hangul" and the request error, so the episodes module and index.ts read the same rules.
 */

export class RequestError extends Error {
  readonly code: string;
  readonly status: number;

  constructor(code: string, status: number) {
    super(code);
    this.code = code;
    this.status = status;
  }
}

/** A passage with more Hangul than this share of its characters is not an English passage. */
export const MAX_HANGUL_SHARE = 0.2;
/** Half of a passage's non-space characters must be Han for it to be a Chinese passage (백로그 069). */
export const MIN_HAN_SHARE = 0.5;
export const MAX_PINYIN_CHARS = 200;

export function boundedString(value: unknown, maxLength: number): value is string {
  return typeof value === "string" && value.trim().length > 0 && value.length <= maxLength;
}

export function nonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

/** One line of plain text: no line breaks, control characters or the prompt wrapper's delimiters. */
export function flattenText(value: string): string {
  return value.replace(/[<>"]/g, "'").replace(/[\s\u0000-\u001F]+/g, " ").trim();
}

export function isMostlyHangul(text: string): boolean {
  const hangul = (text.match(/[ㄱ-ㆎ가-힣]/g) ?? []).length;
  return hangul > text.replace(/\s/g, "").length * MAX_HANGUL_SHARE;
}

/** Whether at least half of the non-space characters are Han (the unified ideographs and extension A), 백로그 069. */
export function isMostlyHan(text: string): boolean {
  const han = (text.match(/[一-鿿㐀-䶿]/g) ?? []).length;
  return han >= text.replace(/\s/g, "").length * MIN_HAN_SHARE;
}

/** One flattened English line of at most [max] characters, or "" when there is none worth keeping. */
export function englishLine(value: unknown, max: number): string {
  if (typeof value !== "string") return "";
  const line = flattenText(value).slice(0, max);
  return line && !isMostlyHangul(line) ? line : "";
}

/** One flattened English line of any length, or "" (백로그 065: a cast member's relationships are never cut). */
export function englishText(value: unknown): string {
  if (typeof value !== "string") return "";
  const line = flattenText(value);
  return line && !isMostlyHangul(line) ? line : "";
}

export function englishLines(value: unknown, maxItems: number, maxChars: number): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => englishLine(item, maxChars)).filter((item) => item.length > 0).slice(0, maxItems);
}

/** A `pinyin` field as one line of at most 200 characters, or "" (백로그 069): never a reason to fail a passage. */
export function pinyinOf(value: unknown): string {
  return typeof value === "string" ? flattenText(value).slice(0, MAX_PINYIN_CHARS) : "";
}

/**
 * Multi-line text as the model may read it inside a wrapper (백로그 075: a previous episode's full
 * text): line breaks kept, the wrapper's own delimiters and control characters removed, Hangul
 * refused like a line. "" when there is nothing worth keeping.
 */
export function englishBlock(value: unknown, max: number): string {
  if (typeof value !== "string") return "";
  const block = value.replace(/[<>"]/g, "'").replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, "").trim().slice(0, max);
  return block && !isMostlyHangul(block) ? block : "";
}

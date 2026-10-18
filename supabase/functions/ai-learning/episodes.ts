/*
 * The episode call (백로그 075): one request makes the story of an episode and, in the same reply,
 * the conversation set inside it; or, given a story the app already holds, only its conversation.
 * The request carries the season brief, the full text of the earlier episodes and this episode's
 * plan; the reply is `episodes[]`, one entry per episode made. The request validator, the prompt
 * and the output validator live here; the shared passage contract, schemas and validators stay in
 * index.ts and arrive as callbacks.
 */
import { CONVERSATION_SITUATIONS, conversationRules } from "./conversation-rules.ts";
import { episodeShape } from "./story-rules.ts";
import { boundedString, englishBlock, englishLine, englishLines, flattenText, RequestError } from "./text.ts";

export type ContentMode = "conversation" | "story";
export type Difficulty = 1 | 2 | 3 | 4 | 5;
/** The language a passage is written in (백로그 066/069): English, or Simplified Chinese with pinyin. */
export type Language = "en" | "zh";
/** What the season has settled so far (백로그 063): material for the next episode, never instructions. */
export type CanonState = { events: string[]; facts: string[]; relationships: string[]; openQuestions: string[]; carryForward: string[] };
/** One block of a system prompt; a fixed block carries the cache marker (백로그 065). */
export type SystemBlock = { type: "text"; text: string; cache_control?: { type: "ephemeral" } };

/** Episodes in a season (백로그 063); the app's `SeasonPolicy` holds the same number. */
export const EPISODES_PER_SEASON = 7;
export const MAX_CANON_ITEMS = 12;
export const MAX_CANON_ITEM_CHARS = 200;
/** How much of those two lists an episode hears (백로그 065); the app's `SeasonContext` sends exactly this much. */
export const MAX_BRIEF_ITEMS = 12;
export const MAX_BRIEF_ITEM_CHARS = 300;
export const MAX_SUMMARY_TITLE = 160;
export const MAX_SYNOPSIS = 600;
export const MAX_SEASON_LINE = 1_200;
export const MAX_CAST_LINE = 2_400;
export const MAX_REVIEW_EXPRESSIONS = 12;
/** How many of the supplied review expressions one passage is asked to re-use (백로그 032). */
export const REVIEW_EXPRESSIONS_WOVEN = 5;

/*
 * The call budget (백로그 074, measured on the deployed function over 21 days, claude-haiku-4-5):
 * output ran at 85-92 tokens per second; a passage averaged 3,060 output tokens and the longest
 * was 5,353; the hosting plan's wall clock is 150 s. A story and its conversation together are
 * therefore about 6,100 tokens on average and about 9,000 at worst, which is 70-105 s of output;
 * two episodes in one call would average 133 s and overrun. So one call makes one episode, with
 * enough tokens for the worst pair and a timeout under the wall clock. The test file checks that
 * these numbers still agree with each other.
 */
export const MEASURED_OUTPUT_TOKENS_PER_SECOND = 92;
export const MAX_PASSAGE_OUTPUT_TOKENS = 5_400;
/** Room for the worst measured pair and a margin; a reply that still stops for `max_tokens` fails the call (nothing partial is kept). */
export const EPISODE_MAX_TOKENS = 12_000;
export const CONVERSATION_ONLY_MAX_TOKENS = 6_000;
export const EPISODE_TIMEOUT_MS = 135_000;
/** Supabase Edge Functions on the free plan end a request at 150 s of wall clock (paid plans: 400 s). */
export const FUNCTION_WALL_CLOCK_MS = 150_000;
/**
 * Episodes one call makes. The measurements allow one: floor(135 s × 92 tok/s ÷ (2 passages ×
 * 5,400 tokens)) = 1, which the test file re-derives. The prompt and the validator are written
 * for one entry; making more per call means changing both, not only this number.
 */
export const EPISODES_PER_CALL = 1;
/** The earlier episodes' text an episode call carries (the newest kept whole first); the body limit sits above it. */
export const MAX_PREVIOUS_TEXT_CHARS = 16_000;
const MAX_GIVEN_SEGMENTS = 60;
const MAX_SEGMENT_CHARS = 1_000;

export type EpisodePlan = { number: number; title: string; plan: string };
export type EpisodeSeason = {
  title: string;
  engine: string;
  setting: string;
  conflict: string;
  characters: string;
  whatIsTrue: string[];
  readerBelieves: string[];
  plans: EpisodePlan[];
};
export type PreviousEpisode = { number: number; title: string; text: string };
export type GivenStory = { title: string; segments: { speaker: string; text: string }[] };
export type EpisodesRequest = {
  action: "episodes";
  language: Language;
  storyDifficulty: Difficulty;
  conversationDifficulty: Difficulty;
  reviewExpressions: string[];
  season: EpisodeSeason;
  /** The episodes asked for, in order; the call makes the first EPISODES_PER_CALL of them. */
  episodes: number[];
  previousEpisodes: PreviousEpisode[];
  canon: CanonState;
  /** Present when the story is already written and only its conversation is wanted. */
  story?: GivenStory;
};

/** The episode numbers this call will actually make: the first one asked for. */
export function episodesToMake(request: EpisodesRequest): number[] {
  return request.episodes.slice(0, EPISODES_PER_CALL);
}

export function canonState(value: unknown): CanonState {
  const item = (value && typeof value === "object" && !Array.isArray(value) ? value : {}) as Record<string, unknown>;
  const lines = (field: string) => englishLines(item[field], MAX_CANON_ITEMS, MAX_CANON_ITEM_CHARS);
  return { events: lines("events"), facts: lines("facts"), relationships: lines("relationships"), openQuestions: lines("openQuestions"), carryForward: lines("carryForward") };
}

/** The review expressions of a request: at most twelve short lines, deduplicated, the first five woven. */
export function reviewExpressionsOf(value: unknown): string[] {
  const expressions = value ?? [];
  if (!Array.isArray(expressions) || expressions.length > MAX_REVIEW_EXPRESSIONS || expressions.some((item) => !boundedString(item, 80))) {
    throw new RequestError("invalid_review_expressions", 400);
  }
  return [...new Set(expressions.map((item) => (item as string).trim()))].slice(0, REVIEW_EXPRESSIONS_WOVEN);
}

function difficultyOf(value: unknown): Difficulty {
  if (![1, 2, 3, 4, 5].includes(value as number)) throw new RequestError("invalid_difficulty", 400);
  return value as Difficulty;
}

function episodeNumberOf(value: unknown): number | null {
  return Number.isInteger(value) && (value as number) >= 1 && (value as number) <= EPISODES_PER_SEASON ? (value as number) : null;
}

/** A malformed season brief is refused, not dropped: without it there is no season to write in. */
function episodeSeasonOf(value: unknown): EpisodeSeason {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new RequestError("invalid_season_brief", 400);
  const item = value as Record<string, unknown>;
  const title = englishLine(item.title, MAX_SUMMARY_TITLE);
  const plans = (Array.isArray(item.plans) ? item.plans : [])
    .map((entry) => {
      const plan = (entry && typeof entry === "object" ? entry : {}) as Record<string, unknown>;
      return { number: episodeNumberOf(plan.number), title: englishLine(plan.title, MAX_SUMMARY_TITLE), plan: englishLine(plan.plan, MAX_SEASON_LINE) };
    })
    .filter((plan): plan is EpisodePlan => plan.number !== null && plan.plan.length > 0);
  if (!title || plans.length === 0) throw new RequestError("invalid_season_brief", 400);
  return {
    title,
    engine: englishLine(item.engine, MAX_SUMMARY_TITLE),
    setting: englishLine(item.setting, MAX_SYNOPSIS),
    conflict: englishLine(item.conflict, MAX_SEASON_LINE),
    characters: englishLine(item.characters, MAX_CAST_LINE),
    whatIsTrue: englishLines(item.whatIsTrue, MAX_BRIEF_ITEMS, MAX_BRIEF_ITEM_CHARS),
    readerBelieves: (Array.isArray(item.readerBelieves) ? item.readerBelieves : []).slice(0, EPISODES_PER_SEASON).map((line) => englishLine(line, MAX_BRIEF_ITEM_CHARS)),
    plans,
  };
}

/** The earlier episodes, newest kept whole first, inside the text budget; an episode that does not fit is left out. */
function previousEpisodesOf(value: unknown): PreviousEpisode[] {
  const items = (Array.isArray(value) ? value : [])
    .map((entry) => {
      const item = (entry && typeof entry === "object" ? entry : {}) as Record<string, unknown>;
      return { number: episodeNumberOf(item.number), title: englishLine(item.title, MAX_SUMMARY_TITLE), text: englishBlock(item.text, MAX_PREVIOUS_TEXT_CHARS) };
    })
    .filter((item): item is PreviousEpisode => item.number !== null && item.text.length > 0)
    .sort((a, b) => a.number - b.number);
  const kept: PreviousEpisode[] = [];
  let used = 0;
  for (const item of [...items].reverse()) {
    if (used + item.text.length > MAX_PREVIOUS_TEXT_CHARS) break;
    used += item.text.length;
    kept.unshift(item);
  }
  return kept;
}

function givenStoryOf(value: unknown): GivenStory | undefined {
  if (value === undefined || value === null) return undefined;
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new RequestError("invalid_given_story", 400);
  const item = value as Record<string, unknown>;
  const segments = (Array.isArray(item.segments) ? item.segments : []).map((entry) => {
    const segment = (entry && typeof entry === "object" ? entry : {}) as Record<string, unknown>;
    if (!boundedString(segment.speaker, 80) || !boundedString(segment.text, MAX_SEGMENT_CHARS)) throw new RequestError("invalid_given_story", 400);
    return { speaker: flattenText(segment.speaker), text: flattenText(segment.text) };
  });
  const title = englishLine(item.title, MAX_SUMMARY_TITLE);
  if (!title || segments.length === 0 || segments.length > MAX_GIVEN_SEGMENTS) throw new RequestError("invalid_given_story", 400);
  return { title, segments };
}

export function validateEpisodesRequest(body: Record<string, unknown>, language: Language): EpisodesRequest {
  const episodes = (Array.isArray(body.episodes) ? body.episodes : []).map(episodeNumberOf);
  if (episodes.length === 0 || episodes.some((number) => number === null)) throw new RequestError("invalid_episode_numbers", 400);
  const story = givenStoryOf(body.story);
  return {
    action: "episodes",
    language,
    storyDifficulty: difficultyOf(body.storyDifficulty),
    conversationDifficulty: difficultyOf(body.conversationDifficulty),
    reviewExpressions: reviewExpressionsOf(body.reviewExpressions),
    season: episodeSeasonOf(body.season),
    episodes: episodes as number[],
    // A conversation-only call never reads the earlier episodes (its story is given), so none are kept.
    previousEpisodes: story ? [] : previousEpisodesOf(body.previousEpisodes),
    canon: canonState(body.canon),
    story,
  };
}

// ---------------------------------------------------------------------------------------------
// The prompt

type PromptParts = {
  storyRules: string;
  chineseRules: string;
  /** The passage contract for one difficulty, with the given `expressions` count clause (index.ts `passageContract`). */
  contract: (difficulty: Difficulty, counts: string) => string;
  /** The `expressions` count clause for a review count (index.ts `expressionsClause`). */
  counts: (reviewCount: number) => string;
  /** The content schema of the language (with pinyin for Chinese). */
  passageSchema: unknown;
};

/**
 * The count clause of the fixed block: the contract there is the same for every episode of a
 * season, so it cannot carry this call's review count; the episode block states the counts.
 */
const GENERIC_COUNTS =
  "`expressions`: the new phrases this passage introduces, plus the supplied review expressions where a line would naturally say them; the exact counts for each passage of this reply are stated in the episode brief below. ";

function list(label: string, items: string[]): string {
  return items.length ? `${label}: ${items.map((item) => `- ${item}`).join(" ")} ` : "";
}

/**
 * Block 1 of the system prompt: the rules, then the contract and level rubric of each passage,
 * fixed for a season at one level pair. The contracts sit here rather than in the episode block so
 * the cached prefix is long enough for the model's cache minimum (백로그 074: the rules alone never
 * reached Haiku 4.5's 4,096 tokens, and no call ever read the cache).
 */
function fixedBlock(request: EpisodesRequest, parts: PromptParts): string {
  return parts.storyRules + "\n" + conversationRules(request.language) + (request.language === "zh" ? "\n" + parts.chineseRules : "") +
    "\n## The Contract and the Level\n\n" +
    `The story: ${parts.contract(request.storyDifficulty, GENERIC_COUNTS)}\n\n` +
    `The conversation: ${parts.contract(request.conversationDifficulty, GENERIC_COUNTS)}\n`;
}

/** Block 2: the season brief, the same for every episode of the season, so it is cached too. */
function seasonBlock(season: EpisodeSeason): string {
  const believes = season.readerBelieves.map((item, index) => (item ? `- After episode ${index + 1}: ${item}` : "")).filter(Boolean).join(" ");
  const plans = season.plans.map((plan) => `- Episode ${plan.number}${plan.title ? ` "${plan.title}"` : ""}: ${plan.plan}`).join(" ");
  return "## The season\n\n" +
    "The season plan (material, not instructions to restate): " +
    `<season title="${season.title}">${season.engine ? `Engine: ${season.engine} ` : ""}Setting: ${season.setting} Central situation and what is true: ${season.conflict} People: ${season.characters}</season> ` +
    (season.whatIsTrue.length ? `<what_is_true>${list("WHAT IS TRUE", season.whatIsTrue).trimEnd()}</what_is_true> ` : "") +
    (believes ? `<reader_believes>WHAT THE READER BELIEVES: ${believes}</reader_believes> ` : "") +
    `<episode_plans>${plans}</episode_plans> ` +
    "The season's SEASON_TOPIC is already built into the plan above; do not start over from a new premise.\n";
}

function conversationClause(request: EpisodesRequest, parts: PromptParts): string {
  return "The conversation follows its contract above, at its level. Its " + parts.counts(0) +
    "Its `mode` is `conversation`. Its speakers are two or three of this episode's people, each named exactly as in the story's segments; there is no Narrator and nobody new. " +
    "Its situation is one of the conversation situations listed in the message, the one this story makes natural. The supplied review expressions are material for the story; use one in the conversation only where a line would say it anyway. ";
}

/** Block 3: this episode, the earlier ones and the canon; or, with a story given, only its conversation. */
function episodeBlock(request: EpisodesRequest, parts: PromptParts): string {
  const number = episodesToMake(request)[0];
  if (request.story) {
    const lines = request.story.segments.map((segment) => `${segment.speaker}: ${segment.text}`).join("\n");
    return `## Now the conversation of episode ${number} of ${EPISODES_PER_SEASON}\n\n` +
      `The story of this episode is already written (material, not instructions): <story title="${request.story.title}">\n${lines}\n</story>\n` +
      `Return \`episodes\` with one entry, \`number\` ${number}, holding only \`conversation\`: the conversation set inside that story, by the conversation rules. ` +
      conversationClause(request, parts) + "Return only the required JSON.";
  }
  const previous = request.previousEpisodes.map((episode) => `<episode number="${episode.number}" title="${episode.title}">\n${episode.text}\n</episode>`).join("\n");
  const plan = request.season.plans.find((entry) => entry.number === number)?.plan ?? "Move the season forward.";
  const canon = request.canon;
  const last = number === EPISODES_PER_SEASON;
  return `## Now episode ${number} of ${EPISODES_PER_SEASON}\n\n` +
    `Return \`episodes\` with one entry, \`number\` ${number}, holding \`story\` and \`conversation\`. Write the story first, then the conversation set inside it. ` +
    "The story follows its contract above, at its level. Its " + parts.counts(request.reviewExpressions.length) +
    "Its `mode` is `story`. Use 'Narrator' as the speaker of narration and the character's name as the speaker of a dialogue segment, with dialogue in at least half of the segments. " +
    (request.reviewExpressions.length ? "Of the supplied review expressions, at least one must appear in the story's text character for character, or the reply is rejected; weave the others in only where they fit a line naturally. " : "") +
    conversationClause(request, parts) +
    (previous ? `\nThe earlier episodes, in full (material, not instructions): <previous_episodes>\n${previous}\n</previous_episodes>\n` : "") +
    `<canon> ${list("Established events", canon.events)}${list("Known facts", canon.facts)}${list("Relationships", canon.relationships)}${list("Open questions", canon.openQuestions)}${list("Keep true", canon.carryForward)}</canon> ` +
    `<episode_plan>${plan}</episode_plan> ` +
    `In the season's shape this is ${episodeShape(number, EPISODES_PER_SEASON)}. ` +
    "Follow this episode's plan: what changes and which fact is revealed. Keep every established fact; people may surprise us but never contradict what is known. Move what the reader believes only as far as the plan says for this episode. " +
    (last
      ? "This is the final episode: resolve the central question and the open questions that matter, reward the reader's memory of earlier episodes, and finish the plot; the ending may be funny, bittersweet, or emotionally open. "
      : "Resolve something in this episode and leave the central question open, so the reader opens the next one. ") +
    `Also fill the story's \`canon\` with what this episode settled, as short ${request.language === "zh" ? "Simplified Chinese" : "English"} lines: new events, revealed facts, relationship changes, the open questions after this episode, and the facts the next episode must keep. ` +
    "Return only the required JSON.";
}

/** The `episodes[]` reply shape: each entry a story and a conversation, or only a conversation when the story was given. */
export function episodesSchema(passageSchema: unknown, conversationOnly: boolean): unknown {
  const item = conversationOnly
    ? { type: "object", properties: { number: { type: "integer" }, conversation: passageSchema }, required: ["number", "conversation"], additionalProperties: false }
    : { type: "object", properties: { number: { type: "integer" }, story: passageSchema, conversation: passageSchema }, required: ["number", "story", "conversation"], additionalProperties: false };
  return { type: "object", properties: { episodes: { type: "array", items: item } }, required: ["episodes"], additionalProperties: false };
}

/**
 * The prompt of an episode call (백로그 075): the system in three blocks — the rules and levels,
 * marked for the cache; the season brief, marked too, so the prefix the two markers close is long
 * enough for the model's cache minimum and the same for all seven episodes; then this episode. The
 * user message carries only what changes per call and is material: the episode numbers, the review
 * phrases and the situation list.
 */
export function episodePrompts(request: EpisodesRequest, parts: PromptParts): { schema: unknown; system: SystemBlock[]; user: string } {
  return {
    schema: episodesSchema(parts.passageSchema, request.story !== undefined),
    system: [
      { type: "text", text: fixedBlock(request, parts), cache_control: { type: "ephemeral" } },
      { type: "text", text: seasonBlock(request.season), cache_control: { type: "ephemeral" } },
      { type: "text", text: episodeBlock(request, parts) },
    ],
    user: JSON.stringify({ episodes: episodesToMake(request), reviewExpressions: request.reviewExpressions, conversationSituations: CONVERSATION_SITUATIONS }),
  };
}

// ---------------------------------------------------------------------------------------------
// The output

/** A passage as index.ts validates it: at least its segments and speakers are known here. */
export type ValidatedPassage = { segments: { speaker: string }[] } & Record<string, unknown>;
export type PassageValidator = (value: unknown, mode: ContentMode, difficulty: Difficulty, reviewExpressions: string[], episodeNumber: number) => ValidatedPassage;
export type EpisodeResult = { number: number; story: ValidatedPassage | null; conversation: ValidatedPassage | null; conversationCheck?: string };

/** The names in a cast line ("Name (role; ...); Name (...)"): what stands before each opening parenthesis. */
function castNames(characters: string): string[] {
  return [...characters.matchAll(/(?:^|\);\s*)([^();]+?)\s*\(/g)].map((match) => match[1].trim()).filter((name) => name.length > 0 && name.length <= 80);
}

/** A name as compared: lowercased words without punctuation ("Mrs. Hayashi" → ["mrs", "hayashi"]). */
function nameWords(name: string): string[] {
  return name.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, " ").split(/\s+/).filter((word) => word.length > 0);
}

/** "Hayashi" names "Mrs. Hayashi" and "Ms. Park Jiyoung" names "Jiyoung": one name's words inside the other's, titles aside. */
function sameName(a: string[], b: string[]): boolean {
  if (a.length === 0 || b.length === 0) return false;
  const [short, long] = a.length <= b.length ? [a, b] : [b, a];
  return short.every((word) => long.includes(word));
}

/**
 * Every conversation speaker is one of the story's people or of the season's cast; the Narrator is
 * not a person. A shortened form of a name ("Hayashi" for "Mrs. Hayashi") still counts: the model
 * shortens names often, and a second provider call for that would be a poor trade (백로그 075 review).
 */
function checkSpeakers(conversation: ValidatedPassage, storySpeakers: Iterable<string>, season: EpisodeSeason): void {
  const allowed = [...castNames(season.characters), ...storySpeakers].map(nameWords).filter((words) => words.join(" ") !== "narrator");
  const speakers = [...new Set(conversation.segments.map((segment) => segment.speaker.trim()))];
  const outsiders = speakers.filter((speaker) => !allowed.some((name) => sameName(name, nameWords(speaker))));
  if (outsiders.length > 0) throw new Error(`conversation_speakers_not_in_story(${outsiders.join(",")})`);
}

/**
 * The reply of an episode call. The story is the episode: a story that breaks the contract fails
 * the call. The conversation is kept only when it passes its own contract and its speakers are the
 * story's people; otherwise the story arrives alone with the broken check named, and the app asks
 * for the conversation again with the story given. With a story given, the conversation is all
 * there is, so its failure fails the call.
 */
export function validateEpisodesOutput(result: Record<string, unknown>, request: EpisodesRequest, validatePassage: PassageValidator): { episodes: EpisodeResult[] } {
  const wanted = episodesToMake(request);
  const items = Array.isArray(result.episodes) ? result.episodes : [];
  if (items.length !== wanted.length) throw new Error(`invalid_episodes_output(count=${items.length})`);
  const episodes = items.map((entry, index) => {
    const item = (entry && typeof entry === "object" ? entry : {}) as Record<string, unknown>;
    const number = wanted[index];
    if (item.number !== number) throw new Error(`invalid_episodes_output(number=${String(item.number)})`);
    if (request.story) {
      const conversation = validatePassage(item.conversation, "conversation", request.conversationDifficulty, [], number);
      checkSpeakers(conversation, request.story.segments.map((segment) => segment.speaker), request.season);
      return { number, story: null, conversation };
    }
    const story = validatePassage(item.story, "story", request.storyDifficulty, request.reviewExpressions, number);
    try {
      const conversation = validatePassage(item.conversation, "conversation", request.conversationDifficulty, [], number);
      checkSpeakers(conversation, story.segments.map((segment) => segment.speaker), request.season);
      return { number, story, conversation };
    } catch (error) {
      const check = error instanceof Error ? error.message : "unknown";
      console.warn(`episode ${number}: the conversation was rejected (${check}); the story is kept`);
      return { number, story, conversation: null, conversationCheck: check.split("(")[0] };
    }
  });
  return { episodes };
}

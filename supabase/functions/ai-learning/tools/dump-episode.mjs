// Diagnostic (백로그 075): capture the exact Anthropic request the Edge Function sends for an
// episode call — the story and the conversation of one episode in one reply. It mirrors the
// app's EpisodeBrief (Kotlin) to build the request the app would send for episode N of a stored
// season, runs the real handler with a fake fetcher, and writes the system blocks, the user
// message and the sizes. Nothing is sent to Anthropic.
//
//   node --experimental-strip-types supabase/functions/ai-learning/tools/dump-episode.mjs <out dir> [episode=1] [language=en]
import { createHandler } from "../index.ts";
import { writeFileSync, mkdirSync } from "node:fs";

const OUT = process.argv[2] ?? "./out";
const EPISODE = Number(process.argv[3] ?? 1);
const LANGUAGE = process.argv[4] ?? "en";
mkdirSync(OUT, { recursive: true });

// ---------- 1. The season bible as validateSeasonOutput stores it (백로그 065 shape) ----------
const bible = {
  title: "The Umbrella Nobody Owns",
  engine: "C. Social farce",
  setting: "Kinosaki Onsen, Japan, in the rainy week before the crab season opens: seven public bathhouses, wooden geta on wet stone, a 1920s ryokan whose owner refuses to replace the lobby umbrella stand.",
  conflict: "A guest returns a borrowed umbrella to the ryokan; the owner insists it was never theirs and will not take it back. WHAT IS TRUE: the umbrella belonged to the owner's late brother; the brother lent it to a guest in 1998 and the guest never returned; the owner has told the family the brother lost it; the night porter sold three such umbrellas last year; the Korean guest only wants her deposit back. RESOLUTION: the umbrella is left in the stand, unclaimed by anyone, and the owner finally tells the family the truth over crab.",
  characters: [
    { name: "Mrs. Hayashi", gender: "female", role: "ryokan owner who takes the house rules too seriously", motive: "to get the umbrella out of her lobby before the family dinner on Friday", secret: "the umbrella was her brother's and she told everyone he lost it", relationships: "widow; employer of Taro; distrusts Ms. Park on sight" },
    { name: "Ms. Park Jiyoung", gender: "female", role: "Korean guest on a solo trip who follows every etiquette tip online", motive: "to return the umbrella politely and get her 3,000 yen deposit back", secret: "she took the umbrella from the wrong stand and knows it", relationships: "guest of Mrs. Hayashi; befriends Taro over coffee" },
    { name: "Taro", gender: "male", role: "night porter who answers every question with a different one", motive: "to keep his side business invisible until the season ends", secret: "he has been selling forgotten umbrellas to the souvenir shop", relationships: "employee of Mrs. Hayashi; nephew of the shop owner" },
    { name: "Mr. Okada", gender: "male", role: "souvenir shop owner who prices everything by how it looks", motive: "to buy the stand's whole contents before the inspection", secret: "he resold the brother's umbrella to a tourist once already", relationships: "uncle of Taro; owes Mrs. Hayashi a favour from 1998" },
  ],
  whatIsTrue: ["The umbrella belonged to Mrs. Hayashi's late brother.", "Taro sells forgotten umbrellas to Mr. Okada.", "Ms. Park took it from the wrong stand."],
  readerBelieves: Array.from({ length: 7 }, (_, i) => `After episode ${i + 1} the reader believes ${i === 0 ? "Mrs. Hayashi is simply stubborn" : i === 1 ? "Taro is stealing umbrellas" : "something else"}.`),
  episodes: Array.from({ length: 7 }, (_, i) => ({
    number: i + 1,
    title: `Episode ${i + 1}`,
    plan: i === 0
      ? "Ms. Park returns the umbrella; Mrs. Hayashi refuses it; Taro says the stand has never held a black umbrella. Pays off: Ms. Park finds a 1998 ryokan tag inside the handle."
      : i === 1
        ? "Ms. Park asks Taro about the tag; Taro answers with questions and takes the umbrella to store it. Fact revealed: the stand's umbrellas go missing every Thursday."
        : `Episode ${i + 1}: something happens that complicates the matter.`,
  })),
  openQuestions: ["Why will Mrs. Hayashi not touch the umbrella?", "Whose tag is inside the handle?"],
};

// ---------- 2. Earlier episodes as the app stores them, for an episode after the first ----------
const storedEpisodes = Array.from({ length: EPISODE - 1 }, (_, i) => ({
  number: i + 1,
  title: `Episode ${i + 1} title`,
  segments: Array.from({ length: 20 }, (_, j) => ({ speaker: j % 3 === 0 ? "Narrator" : j % 3 === 1 ? "Ms. Park Jiyoung" : "Taro", text: `Line ${j + 1} of episode ${i + 1}: something concrete happens and someone says so. ` })),
  canon: { events: [`Event of episode ${i + 1}.`], facts: [`Fact from episode ${i + 1}.`], relationships: [], openQuestions: [`Open after episode ${i + 1}?`], carryForward: ["It rains every afternoon this week."] },
}));

// ---------- 3. Mirror of EpisodeBrief + EpisodeRequest.toJson (Kotlin) ----------
const SEP = "; ";
function fitted(s, max) {
  if (s.length <= max) return s;
  const head = s.slice(0, max - 1).trimEnd();
  const idx = head.lastIndexOf(" ");
  return (idx >= 0 ? head.slice(0, idx) : head) + ")";
}
function castLine(characters, maxChars) {
  const share = Math.floor((maxChars - SEP.length * (characters.length - 1)) / characters.length);
  return characters.map((c) => fitted(`${c.name} (${c.role}; wants: ${c.motive}; hides: ${c.secret}; ${c.relationships})`, share)).join(SEP);
}
const squeezed = (list) => [...new Set(list.map((s) => s.trim().slice(0, 160)).filter(Boolean))].slice(-8);
let canon = { events: [], facts: [], relationships: [], openQuestions: bible.openQuestions, carryForward: [] };
for (const episode of storedEpisodes) {
  canon = {
    events: squeezed([...canon.events, ...episode.canon.events]),
    facts: squeezed([...canon.facts, ...episode.canon.facts]),
    relationships: squeezed([...canon.relationships, ...episode.canon.relationships]),
    openQuestions: squeezed(episode.canon.openQuestions.length ? episode.canon.openQuestions : canon.openQuestions),
    carryForward: squeezed([...canon.carryForward, ...episode.canon.carryForward]),
  };
}
const MAX_PREVIOUS_TEXT_CHARS = 16_000;
const previousEpisodes = [];
let used = 0;
for (const episode of [...storedEpisodes].reverse()) {
  const text = episode.segments.map((s) => `${s.speaker}: ${s.text}`).join("\n");
  if (used + text.length > MAX_PREVIOUS_TEXT_CHARS) break;
  used += text.length;
  previousEpisodes.unshift({ number: episode.number, title: episode.title, text });
}
const appRequest = {
  action: "episodes",
  language: LANGUAGE,
  storyDifficulty: 3,
  conversationDifficulty: 3,
  reviewExpressions: ["call it a day", "under the weather", "fair enough"],
  season: {
    title: bible.title, engine: bible.engine, setting: bible.setting, conflict: bible.conflict,
    characters: castLine(bible.characters, 2400),
    whatIsTrue: bible.whatIsTrue.slice(0, 12).map((s) => s.slice(0, 300)),
    readerBelieves: bible.readerBelieves.slice(0, 12).map((s) => s.slice(0, 300)),
    plans: bible.episodes.map((e) => ({ number: e.number, title: e.title, plan: e.plan })),
  },
  episodes: [EPISODE],
  previousEpisodes,
  canon,
};

// ---------- 4. Run the real handler, capture what goes to Anthropic ----------
let sent;
const env = { ANTHROPIC_API_KEY: "key", AI_APP_TOKEN: "key" };
const handler = createHandler((name) => env[name], async (_url, options) => {
  sent = JSON.parse(options.body);
  // A refusal is not retried, so exactly one provider call is captured and nothing is stored.
  return new Response(JSON.stringify({ stop_reason: "refusal", content: [] }), { status: 200, headers: { "content-type": "application/json" } });
});
const body = JSON.stringify(appRequest);
const response = await handler(new Request("https://local.test/ai-learning", { method: "POST", headers: { "content-type": "application/json", "x-app-token": "key" }, body }));

// ---------- 5. Write the dump ----------
const blocks = typeof sent.system === "string" ? [{ text: sent.system }] : sent.system;
const estimate = (text) => Math.round(text.length / 4);
writeFileSync(`${OUT}/0-bible-as-stored.json`, JSON.stringify(bible, null, 2));
writeFileSync(`${OUT}/1-app-request-body.json`, JSON.stringify(appRequest, null, 2));
writeFileSync(`${OUT}/2-anthropic-request.json`, JSON.stringify(sent, null, 2));
writeFileSync(`${OUT}/3-system.txt`, blocks.map((b, i) => `===== system block ${i + 1} (${b.text.length} chars, ~${estimate(b.text)} tokens${b.cache_control ? `, cache_control: ${b.cache_control.type}` : ""}) =====\n${b.text}`).join("\n\n"));
writeFileSync(`${OUT}/4-messages.txt`, sent.messages.map((m) => `[${m.role}]\n${m.content}`).join("\n\n"));
const cachedPrefix = blocks.filter((b) => b.cache_control).map((b) => b.text).join("\n");
const lines = [
  `episode=${EPISODE} language=${LANGUAGE} handler status=${response.status}`,
  `app request body bytes: ${Buffer.byteLength(body)} (server MAX_BODY_BYTES=49152)`,
  `anthropic body bytes: ${Buffer.byteLength(JSON.stringify(sent))}; model=${sent.model}; max_tokens=${sent.max_tokens}`,
  `system blocks: ${blocks.map((b, i) => `#${i + 1} ${b.text.length} chars ~${estimate(b.text)} tok${b.cache_control ? " (cached)" : ""}`).join(" | ")}`,
  `cached prefix: ${cachedPrefix.length} chars ~${estimate(cachedPrefix)} tokens (Haiku 4.5 caches a prefix of 4,096 tokens or more)`,
  `user message: ${sent.messages[0].content.length} chars; keys: ${Object.keys(JSON.parse(sent.messages[0].content)).join(", ")}`,
  `previous episodes sent: ${previousEpisodes.map((e) => `${e.number} (${e.text.length} chars)`).join(", ") || "none"}`,
];
writeFileSync(`${OUT}/5-report.txt`, lines.join("\n"));
console.log(lines.join("\n"));

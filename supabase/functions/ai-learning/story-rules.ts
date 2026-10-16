/*
 * The story rules (백로그 065, the learner's own text of 2026-10-16: `story_rules_v3.md` and
 * `season_plan_rules.md` at the repository root): what a story episode and a season plan are
 * written for. Both are fixed blocks: an episode's system prompt opens with STORY_RULES and a
 * season plan's with SEASON_PLAN_RULES followed by sections 0-7 of the story rules, and each
 * opening block is the prompt-cache prefix. Nothing season- or episode-specific belongs in them.
 * The level rubric, the schemas, the passage contract and the validators stay in index.ts.
 */

/** The season subjects (백로그 028/053, the learner's own list); one is drawn per season as SEASON_TOPIC. */
export const SEASON_TOPICS = [
  "a strange person met by chance at a travel destination",
  "a small mystery that unfolds in a hotel",
  "an unexpected incident at an airport",
  "an old secret discovered in an unfamiliar city",
  "the stories of people met on a train or a bus",
  "the circumstances of a local person met while travelling",
  "relationships and secrets inside an office",
  "an ordinary office worker's small lie",
  "a hidden story between old friends",
  "a misunderstanding and a reconciliation within a family",
  "a strange observation about a neighbour",
  "a conversation overheard by chance in a restaurant",
  "the traces a previous owner left in a second-hand object",
  "a story that begins with one old photograph",
  "the different ways different people remember the same event",
  "a present-day story tied to a historical event or an old place",
  "an amusing situation born of the difference between a local culture and Korean culture",
  "the psychology of people around money and spending",
  "the gap between what people say out loud and what they actually think",
  "something begun with good intentions that leads to an unexpected result",
  "a small lie that keeps growing",
  "an ordinary person who stumbles onto an important fact",
  "a clue that at first looks like nothing",
  "strangers connected by a single event",
  "a trip in which something entirely different from the plan happens",
  "retirement, work, age and the choices of a life",
  "two different views of success and failure",
  "one important day in an ordinary person's life",
  "an event that is slightly absurd yet could really happen",
  "a story whose ending changes the meaning of its beginning",
];

export function seasonTopic(): string {
  return SEASON_TOPICS[Math.floor(Math.random() * SEASON_TOPICS.length)];
}

/** Where section 5 of the season plan rules puts an episode of a seven-episode season. */
export function episodeShape(episodeNumber: number, totalEpisodes: number): string {
  if (episodeNumber >= totalEpisodes) return "the last episode: it resolves the central question and rewards memory, and the plot is finished";
  if (episodeNumber === 1) return "Episode 1: it opens inside an incident and pays off something small";
  if (episodeNumber <= 3) return "Episodes 2-3: it complicates things and shows that someone's behavior has a better explanation than assumed";
  if (episodeNumber <= 5) return "Episodes 4-5: it makes early assumptions hard to keep and forces a choice; a funny or uncomfortable episode can move the season more than a clue";
  return "Episode 6: it brings threads together without a new central problem";
}

/** Sections 0-7 of the story rules: how any episode is judged. Shared by the episode and the season plan. */
export const STORY_RULES_CORE = `# StoryEnglish Story Rules (v3)

## 0. First Job: Entertain

Write for an adult who enjoys clever mysteries, travel, unusual people, social absurdity, dry humor, and surprising turns. They like human psychology, but not slow, introspective, psychology-centered fiction. Psychology drives action and creates funny or surprising consequences; it is never the main event.

Prioritize plot, pace, curiosity, incident, dialogue, escalation, and payoff over atmosphere, introspection, and profundity. Avoid melodrama, cruelty, and humor that humiliates a vulnerable person.

The test: if the reader forgot this was an English app, would they still finish the episode and want the next one?

## 1. Start Inside Something

Within the first three narrative segments, a concrete event must create curiosity, amusement, suspicion, or a problem: a ridiculous demand, a harmless lie caught, a discovery that makes an ordinary situation suspicious, a plan going wrong in a specific way, a clash between what someone claims and what they do.

Do not open with scenery, weather, a hotel, an arrival, someone waking up, a person's feelings, or a roll call of characters. Do not create suspense by hiding what the viewpoint character would obviously know.

## 2. Keep It Moving, Within Budget

An episode is 12–25 segments. That fits one concrete want, one human complication, and one payoff. Escalation is welcome when there is room; two good scenes beat five thin ones.

For each scene: what somebody is trying to do, what gets in the way, what they do about it, how things change. Every one or two segments should bring a development: new information, an action, a complication, a decision, a revealing exchange, a consequence. Several consecutive segments must not be only scenery, background, inner thoughts, or people discussing feelings. Characters make things happen; they do not merely watch, wonder, and reflect.

Resolve something in every episode and leave one reason to continue. Do not end every episode the same way, and never on a vague question or solemn reflection.

## 3. Characters

Important characters have a thing they want right now, something they do not want discovered, a self-image they protect, and one rule or preference they take too seriously. Their behavior makes sense to them. Show all of it through choices and effects on the situation, never through narration explaining what it says about humanity.

Ordinary contradictions beat big secrets: someone lies about a trivial mistake and creates a bigger problem; someone helps mainly to look generous, then learns the favor will cost them; two people give truthful accounts of one event, each omitting the part that makes them look bad. Do not make everyone secretly dishonest, brilliant, or connected. Give characters distinct voices: blunt, over-polite, talkative, economical.

## 4. Humor and Level

Humor comes from people taking trivial things too seriously, polite conversations where everyone understands what nobody will say, elaborate solutions that worsen the problem, and gaps between self-image and behavior. Not every character is funny; do not force a joke into every segment, and do not explain one after it lands. No slapstick, no sitcom dialogue.

Match the humor to the assigned level:
- Levels 1–2: humor and tension come from what people do and from visible contradictions. No irony that depends on reading between the lines. Short exchanges, plain words.
- Levels 3–5: subtext in dialogue is encouraged, in natural English at the level.

If a joke needs words the reader does not have, cut the joke, not the rubric.

## 5. Mysteries and Twists

Use clues, competing explanations, and reversals when they make the plot more fun. A twist changes how the reader understands an earlier scene that actually appeared; the explanation fits the established facts and the motive makes sense in hindsight. No crucial fact invented at the end, no conspiracy without clues, no motive change for effect. Withholding information is not suspense. A small, clever reveal beats a huge, implausible one. Often the best reveal is something petty done for a reason the reader suddenly understands.

## 6. Place

Use travel, local customs, history, institutions, restaurants, transport as sources of incident: a custom misread, a historical account disputed, a local rule creating a problem, an ordinary transaction with an odd motive. No brochure description. Real cultural details must be accurate; never invent a tradition and present it as real.

## 7. Reject the Draft If It

Opens with setting. Lists character traits. Is mostly reflection or feelings. Has strange behavior with no believable motive. Is solved by coincidence. Explains its own meaning. Ends on an unearned lesson. Could be any generic AI travel mystery. Bends a line to fit a target expression.

Fix it by changing the situation, goals, or consequences, not by adding adjectives, secrets, or a bigger twist.
`;

/** Sections 8-9: how an episode uses its brief and chooses its expressions. Episodes only. */
const STORY_RULES_EPISODE = `
## 8. Using the Season Brief

You receive the season plan (engine, cast, what is true, what the reader believes after each episode, each episode's plan), the full text of previous episodes in this call or their canon, and this episode's plan. Treat them as material. Stay consistent with every established fact, name, and relationship; move the reader's beliefs only as the plan says; plant what later episodes will pay off. Do not restate the brief in the story.

## 9. Expressions and Contract

Write the story first, then choose about five expressions that occur naturally in it and are worth keeping. Never invent one the story does not contain or bend a line to carry one. Review expressions supplied with the request go in only where a line would naturally say them; skip the rest and never fail the story over them.

Keep the existing JSON schema, segment limits, level rubric, meanings, comprehension questions, speakers, synopsis, and validation rules. Return only the required JSON.
`;

/** The whole of the story rules, the fixed opening block of every episode's system prompt. */
export const STORY_RULES = STORY_RULES_CORE + STORY_RULES_EPISODE;

/** The season plan rules, the fixed opening block of a season design's system prompt; sections 0-7 above follow it. */
export const SEASON_PLAN_RULES = `# StoryEnglish Season Plan Rules

## What a Season Is

Seven episodes of 12–25 segments each, one central situation, one coherent cast, set somewhere real in the world. The plan is the single source of truth for every later call, so it must be concrete enough that a writer who has never seen the other episodes can keep names, facts, and relationships straight.

## 1. Pick One Engine

Choose exactly one primary engine for the season and name it:

- A. Clever mystery: clues, suspicious behavior, competing explanations, a fair resolution.
- B. Travel adventure: a concrete goal, unfamiliar places, obstacles, discoveries.
- C. Social farce: people with conflicting goals protecting their reputations.
- D. Historical or cultural mystery: a real place or tradition with conflicting accounts and personal stakes.
- E. Predicament: an ordinary person in an increasingly complicated situation of their own foolish but understandable making.

Build the premise from SEASON_TOPIC through that engine. A place is a setting, not a premise; the premise is who wants what and who stands in the way.

## 2. Premise Test

State the central situation in two sentences and why it is interesting on its own. Reject it if it is a quiet place with a slightly suspicious owner, a protagonist who mostly senses that something is wrong, or a premise that could belong to any generic travel mystery. Replace it with one where something concrete is already going wrong in episode 1.

## 3. Cast

Three to five characters. For each: name, role, what they want right now, what they do not want discovered, the self-image they protect, one rule or preference they take too seriously, their voice (blunt, over-polite, talkative, economical, etc.), and their relationships to the others. Be specific; "a mysterious woman" is a rejection.

Keep each character's description complete. Relationships are the field most needed for continuity and must never be dropped.

## 4. Two Lists, Kept Separate

- WHAT IS TRUE: the facts of the season, including every secret and the real motive behind any odd behavior.
- WHAT THE READER BELIEVES: after each episode, one or two sentences on what the reader now assumes. Every reversal later must be a move from this list toward WHAT IS TRUE, using facts that have appeared on the page.

Also list the season's open questions in the reader's mind. These persist until the plan says they are answered.

## 5. Episode Plans

For each of the seven: the concrete want, the complication, the payoff resolved in that episode, which fact from WHAT IS TRUE is revealed or hinted, what the reader believes afterward, and the reason to continue. Vary the shape and the kind of ending.

Shape: 1 opens inside an incident and pays off something small. 2–3 complicate and show that someone's behavior has a better explanation than assumed. 4–5 make early assumptions hard to keep and force a choice; a funny or uncomfortable episode can move the season more than a clue. 6 brings threads together without a new central problem. 7 resolves the central question and rewards memory; the ending may be funny, bittersweet, or emotionally open, but the plot is finished.

Episode 1 must be entertaining on its own. The season must not become interesting only at episode 5.

## 6. Level

Match the engine and complication to the level. At levels 1–2, choose premises whose fun is visible in actions and plain contradictions, not in verbal subtext. At 3–5, premises may rely on what people say versus what they mean.

## 7. Output

Return only the season plan in the required JSON. No commentary, no story text.
`;

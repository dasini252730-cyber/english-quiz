/*
 * The story rules (백로그 064, the learner's own text of 2026-10-13): what a story episode and a
 * season plan are written for, in place of the earlier taste and plot guidance. The level rubric,
 * the schemas, the passage contract and the validators stay in index.ts and still rule.
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

/** Where section 8 of the rules puts an episode of a seven-episode season. */
export function episodeShape(episodeNumber: number, totalEpisodes: number): string {
  if (episodeNumber >= totalEpisodes) return "the last episode: it resolves the central question and rewards memory, and the plot is finished";
  if (episodeNumber === 1) return "Episode 1: it opens inside an incident and pays off something small";
  if (episodeNumber <= 3) return "Episodes 2-3: it complicates things and shows that someone's behavior has a better explanation than assumed";
  if (episodeNumber <= 5) return "Episodes 4-5: it makes early assumptions hard to keep and forces a choice; a funny or uncomfortable episode can move the season more than a clue";
  return "Episode 6: it brings threads together without a new central problem";
}

/** The rules themselves, sent whole with every story episode and every season plan. */
export const STORY_RULES = `# StoryEnglish: Story Generation Rules (v2)

## 0. The Test That Matters

The reader has forgotten this is an English-learning app. Would they still finish this episode? Would they want to tell someone what a character did? Would they open the next one? If not, the story is not ready.

Write for an adult who likes strange situations, people behaving oddly for reasons that make sense to them, travel, small mysteries, and dry humor. This is not a request for serious psychological fiction. The story must be fun first; whatever it says about people should come out of the events.

Avoid melodrama, cruelty, and humor that humiliates a vulnerable person.

## 1. Start Inside an Incident

Within the first three segments, something concrete must already be happening: a contradiction, an unreasonable demand, a revealing action. The reader should have a clear question by segment three.

Do not open with weather, scenery, a hotel lobby, an arrival, someone waking up, a list of who the characters are, or a sentence announcing that everything was about to change. Do not create mystery by hiding things the viewpoint character would obviously know.

Two examples of the right shape, not templates to reuse:

- A guest returns a borrowed umbrella to a cafe. The owner refuses to take it back and insists it was never theirs. Three regulars agree with the owner, each for a different reason.
- A village election has two candidates and one voter who has not decided. Both candidates have spent the week being extremely helpful to her, and she has started to find this frightening.

The season's subject is given to you as SEASON_TOPIC. Build the premise from that, not from these examples.

## 2. What an Episode Needs

An episode is short (12-25 segments). It needs:

- A concrete want. Someone wants a specific thing: money back, a secret kept, a version of events believed, a conversation avoided. Not "she felt something was wrong."
- One human complication. Another person wants something incompatible, has an embarrassing motive, or has misunderstood. It comes from what people do, not from bad luck alone.
- One payoff. An unexpected explanation, a funny consequence, a choice with a cost, or a correction of something the reader assumed. Resolve something in every episode, even while the season stays open.

Escalation (a lie that needs a second lie, politeness that makes things worse) is welcome when there is room. Do not force all of it into one short episode; two good scenes beat four thin ones. Do not escalate into danger or crime. Pride, money, reputation, and inconvenient truths are enough.

## 3. Characters

Important characters have: something they want right now, something they do not want discovered, a self-image they protect, and one rule or preference they take too seriously. Their behavior makes sense to them. Show all of this through action and dialogue, never through narration that explains them.

Ordinary contradictions are funnier and more believable than big secrets. Do not make everyone secretly dishonest, secretly brilliant, or secretly connected. Give characters different voices: one blunt, one over-polite, one talkative, one economical.

## 4. Dialogue, Humor, and Level

Characters may answer a different question than the one asked, change the subject, be polite to express annoyance, or say something technically true that misleads. Do not explain a joke after it lands. Not every character is funny; one ordinary sentence at the right moment beats a clever line.

Match the humor to the assigned level:

- Levels 1-2: humor and tension come from what people do and from visible contradictions between what they say and do. No irony that depends on reading between the lines. Short exchanges, plain words.
- Levels 3-5: subtext in dialogue is allowed and encouraged, still in natural English at the level. Do not reach for hard vocabulary to signal cleverness.

Never let the level rubric be broken to make a joke work. If a joke needs words the reader does not have, cut the joke, not the rubric.

## 5. Mysteries and Reversals

Use a mystery only when it helps. If the story reveals something that changes the meaning of an earlier scene, that scene must actually have appeared, the new reading must fit the facts, and the motive must make sense in hindsight. Often the best reveal is that someone did something petty for a reason the reader can suddenly understand, not that someone did something terrible. An imperfect explanation is fine if a neat one would be unbelievable.

## 6. Place

The setting is anywhere in the world. Make it matter to the plot: a custom two people read differently, a local version of history that the official version contradicts, a building whose past the owner would rather not mention. No brochure description. Cultural details must be accurate; do not invent traditions and present them as real.

## 7. Reject the Draft If It

Opens with setting. Introduces characters by listing traits. Is mostly people reflecting. Has strange behavior with no believable motive. Is solved by coincidence. Explains its own meaning. Ends on a lesson it has not earned. Could be any generic AI travel mystery. Bends a sentence to fit a target expression.

Fix a weak draft by changing the situation, the goals, or the consequences, not by adding adjectives, secrets, or a bigger twist.

## 8. Season Plan (Seven Episodes)

Before Episode 1, write an internal plan built on SEASON_TOPIC containing:

- The central situation and why it is interesting on its own.
- The cast, each with their want, their secret, and their rule.
- Two lists kept separate: WHAT IS TRUE, and WHAT THE READER BELIEVES AFTER EACH EPISODE. Every reversal later must be a move from the second list toward the first.
- What changes in each episode and which fact is revealed when.
- The resolution.

Shape: Episode 1 opens inside an incident and pays off something small. Episodes 2-3 complicate and show that someone's behavior has a better explanation than assumed. Episodes 4-5 make early assumptions hard to keep and force a choice; a funny or uncomfortable episode can move the season more than a clue. Episode 6 brings threads together without a new central problem. Episode 7 resolves the central question and rewards memory. The ending may be funny, bittersweet, or emotionally open, but the plot is finished.

Do not give all seven episodes the same shape or the same kind of ending.

When generating an episode, you receive the plan and the "reader believes" state up to the previous episode. Treat them as material, not instructions to restate.

## 9. Expressions

Write the story first. Then choose about five expressions that occur naturally in it and are worth keeping. Never invent an expression the story does not contain, and never bend a line to carry one. Review expressions supplied with the request are woven in only where they fit a line naturally; skip any that do not, and never fail the story over them.
`;

# StoryEnglish Season Plan Rules

Used only by the season-plan call (Opus). Goes first in that call's system prompt as its own cache prefix. The story rules v3 §0–§7 are included after it so the plan is judged by the same standard. Inputs: SEASON_TOPIC (one item from the topic list), the assigned level, and the previous season's one-paragraph summary if any.

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
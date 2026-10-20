/*
 * The conversation rules (백로그 075): what the conversation half of an episode is written for.
 * An episode call writes the story and then, in the same reply, the conversation set inside it;
 * the block below follows the story rules in the fixed, cached opening of that call's system
 * prompt, so nothing per-request (the situation list, the review phrases, the plan) belongs here.
 * The reader's taste (백로그 053, the learner's own words) lives here too, because the conversation
 * is the passage written for it; a story follows the story rules instead.
 */

/**
 * Who reads this (백로그 053, the learner's own words, 2026-10-08): the taste a conversation is
 * written for. A story and a season plan follow `STORY_RULES` instead (백로그 064).
 */
export const READER =
  "The reader is a Korean woman in her forties: an adult learner of English who dislikes textbook-flavoured stories. " +
  "She likes watching human psychology and relationships; mystery, realistic relationships, travel, history and social background; " +
  "a touch of black comedy and an unexpected twist; characters who are contradictory and three-dimensional rather than perfect; " +
  "stories that make her ask what happens next, where even an everyday scene shows people's choices and inner life. " +
  "She avoids heavy or cruel thrillers and dislikes forced sentiment. Her humour is dry and arises from the situation, never an exaggerated gag, " +
  "and she enjoys the occasional one-line twist or wit. The overall feel: a modern mystery with human relationships, travel and realistic humour, like one episode of a Netflix drama. ";

/** The one rule above the rest (백로그 053). */
export const PRINCIPLE =
  "Above everything: she must feel she is reading a story and enjoying a conversation, not studying English. " +
  "Never flatten the story into something dull or preachy for the sake of learning; adjust sentence and vocabulary difficulty to the level below, but keep the story's pull and curiosity. " +
  "The aim is a reader who reads English because she wants to know what happens next. ";

/** The travel situations a conversation is drawn from (백로그 053): English an adult will actually use abroad. */
export const CONVERSATION_SITUATIONS = [
  "checking in at the airport", "immigration", "picking up a rental car", "checking in and out of a hotel",
  "asking hotel staff to solve a problem", "booking a restaurant", "ordering in a restaurant", "asking about a dish",
  "ordering at a cafe", "shopping", "asking for directions", "using public transport", "asking a question at a sight",
  "light conversation with a local", "talking with someone met by chance while travelling", "talking over the travel plan",
  "asking for help when something goes wrong", "changing or cancelling a booking", "complaining politely about a problem",
  "thanking, apologising, asking a favour, declining", "starting a conversation with someone just met",
  "asking someone to repeat what you did not catch", "coping naturally when you did not understand the English",
];

/**
 * The rules of the conversation half of an episode, written with the passage language as LANGUAGE.
 * Section 6 is the reader's taste; in Chinese the whole block reads "Chinese" for "English", the
 * way `forChinese` reads the taste for a Chinese passage (백로그 069).
 */
const CONVERSATION_RULES_TEMPLATE = `# StoryEnglish Conversation Rules

The conversation is the second half of an episode. It is written after the story of the same episode, in the same reply, and it belongs to that story.

## 1. Same World

The conversation happens inside this episode's story: the same place, the same day or the next, a situation the story raised or a scene the story only mentioned. Two or three of the story's people talk, with their names spelled exactly as in the story's segments; nobody new is invented and the Narrator does not speak. A reader who has just read the story recognises at once where she is and who is talking.

## 2. What It Is For

It is the LANGUAGE she would actually say in that situation: asking, answering, fixing a problem, small talk that goes somewhere. One situation from the supplied list is the spine of the conversation; choose the one the story makes natural, never a random one, and let the story's problem be what the people are dealing with (a hotel mix-up becomes asking the staff to put it right; a chance meeting becomes small talk that goes somewhere). Where it fits, the same intent is phrased in two or three ways so she sees the choices a speaker has.

## 3. How People Talk

Natural LANGUAGE as natives speak it: short turns, interruptions, half-answers, people not quite saying what they mean, and the small humour of real talk. No textbook phrasing, no stiff over-politeness, no lines that exist only to carry a phrase. Character shows through what people say and avoid saying; the voices of the story's people stay their own. The humour is dry and comes from the situation, never a gag.

## 4. What It Must Not Do

It does not advance the plot, reveal a secret or settle an open question the season keeps for later; it may lean on what the story already showed and hint at what the story hinted. It does not retell the story and nobody explains a word. It contradicts no established fact, name or relationship.

## 5. Shape

Dialogue only: every segment is one person's turn, the speaker is that person's name, and there is no Narrator. 12 to 25 segments, for about ten minutes of reading. Its own expressions, glossary, speakers, comprehension questions and \`synopsis\` follow the same contract as the story's; the synopsis says in two sentences who talked where and what was settled.

## 6. The Reader

READER_TASTE
`;

/** The conversation rules for [language] (백로그 075): a fixed block, part of the cached prefix of an episode call. */
export function conversationRules(language: "en" | "zh"): string {
  const name = language === "zh" ? "Chinese" : "English";
  const block = CONVERSATION_RULES_TEMPLATE.replaceAll("LANGUAGE", name).replace("READER_TASTE", (PRINCIPLE + READER).trim());
  return language === "zh" ? block.replaceAll("English", "Chinese") : block;
}

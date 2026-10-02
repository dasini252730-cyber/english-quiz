import assert from "node:assert/strict";
import { test } from "node:test";
import { createHandler } from "./index.ts";

const validContent = {
  title: "A Saturday Plan",
  mode: "conversation",
  segments: Array.from({ length: 8 }, (_, index) => ({
    speaker: index % 2 ? "Sam" : "Alex",
    text: index === 0 ? "Let's pull it off together." : `We can plan the next step ${index}.`,
  })),
  expressions: [
    { text: "pull it off", meaning: "해내다", segmentIndex: 0 },
    { text: "next step", meaning: "다음 단계", segmentIndex: 1 },
  ],
};

const expectedContent = {
  ...validContent,
  expressions: [
    { ...validContent.expressions[0], startIndex: 6, endIndex: 17 },
    { ...validContent.expressions[1], startIndex: 16, endIndex: 25 },
  ],
  glossary: [],
};

function providerResponse(value, httpStatus = 200, stopReason = "end_turn") {
  return new Response(JSON.stringify({
    stop_reason: stopReason,
    content: [{ type: "text", text: JSON.stringify(value) }],
  }), { status: httpStatus, headers: { "content-type": "application/json" } });
}

function post(body, headers = {}) {
  return new Request("https://local.test/ai-learning", {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

/**
 * Anthropic's structured outputs accept only a subset of JSON Schema. A schema carrying an
 * unsupported keyword 400s the whole request - that is what broke both content modes in
 * production while `meaning` (a constraint-free schema) kept working. This walks a schema and
 * fails on every keyword in that rejected set, so a future edit that reaches for one is caught
 * here instead of in a deployed function.
 *
 * Supported and deliberately absent from the list: `enum`, `const`, `anyOf`, `allOf`, `$ref`,
 * `$defs`, `format`, `description`, and `additionalProperties: false`.
 */
const UNSUPPORTED_KEYWORDS = [
  "minItems", "maxItems", "uniqueItems", "contains", "minContains", "maxContains", "prefixItems",
  "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
  "minLength", "maxLength", "pattern", "patternProperties", "propertyNames",
  "minProperties", "maxProperties", "oneOf", "not", "if", "then", "else",
];

/**
 * Only descends where a JSON Schema actually nests a subschema. Walking every key would also
 * inspect the `properties` map, where a future domain field legitimately named `pattern` or
 * `minimum` would be flagged as a keyword it is not.
 */
function assertOnlySupportedKeywords(schema, path = "schema") {
  if (!schema || typeof schema !== "object" || Array.isArray(schema)) return;
  for (const keyword of UNSUPPORTED_KEYWORDS) {
    assert.equal(
      keyword in schema,
      false,
      `${path} carries unsupported JSON Schema keyword "${keyword}"`,
    );
  }
  for (const [name, subschema] of Object.entries(schema.properties ?? {})) {
    assertOnlySupportedKeywords(subschema, `${path}.properties.${name}`);
  }
  for (const [name, subschema] of Object.entries(schema.$defs ?? {})) {
    assertOnlySupportedKeywords(subschema, `${path}.$defs.${name}`);
  }
  if (schema.items) assertOnlySupportedKeywords(schema.items, `${path}.items`);
  for (const branch of ["anyOf", "allOf"]) {
    (schema[branch] ?? []).forEach((subschema, index) =>
      assertOnlySupportedKeywords(subschema, `${path}.${branch}[${index}]`)
    );
  }
}

test("content request uses configured model, schema-constrained JSON, and returns validated content", async () => {
  let sent;
  let sentHeaders;
  const handler = createHandler((name) => ({
    ANTHROPIC_API_KEY: "server-only-key",
    ANTHROPIC_MODEL: "claude-sonnet-5",
  })[name], async (url, options) => {
    assert.equal(url, "https://api.anthropic.com/v1/messages");
    assert.equal(options.headers["x-api-key"], "server-only-key");
    assert.equal(options.headers["anthropic-version"], "2023-06-01");
    sentHeaders = options.headers;
    sent = JSON.parse(options.body);
    return providerResponse(validContent);
  });

  const response = await handler(post({ action: "content", mode: "conversation", difficulty: 2 }));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(body.data, expectedContent);
  assert.equal(sent.model, "claude-sonnet-5");
  assert.equal(sent.output_config.format.type, "json_schema");
  // The 8..40 segment and 1..40 expression bounds are validateModelOutput's job, asserted by
  // the output-validation tests below, not the schema's.
  assertOnlySupportedKeywords(sent.output_config.format.schema);
  // The key travels in the header only, and never comes back out in a response.
  assert.equal(JSON.stringify(sent).includes("server-only-key"), false);
  assert.equal(JSON.stringify(body).includes("server-only-key"), false);
  assert.equal(sentHeaders.authorization, undefined);

  const story = {
    ...validContent,
    title: "The Lost Key",
    mode: "story",
    segments: validContent.segments.map((segment) => ({ ...segment, speaker: "Narrator" })),
  };
  const storyHandler = createHandler(() => "key", async () => providerResponse(story));
  const storyResponse = await storyHandler(post({ action: "content", mode: "story", difficulty: 1 }));
  assert.equal(storyResponse.status, 200);
  assert.equal((await storyResponse.json()).data.mode, "story");
});

test("invalid input and oversized bodies are rejected before calling the provider", async () => {
  let calls = 0;
  const handler = createHandler(() => "key", async () => {
    calls += 1;
    return providerResponse(validContent);
  });

  const invalid = await handler(post({ action: "content", mode: "admin", difficulty: 2 }));
  assert.equal(invalid.status, 400);
  assert.equal((await invalid.json()).error.code, "invalid_mode");

  const oversized = await handler(post(" ".repeat(16_385)));
  assert.equal(oversized.status, 413);
  assert.equal(calls, 0);
});

test("contextual meaning returns structured output and rejects unrelated model expressions", async () => {
  let sent;
  const handler = createHandler(() => "key", async (_url, options) => {
    sent = JSON.parse(options.body);
    return providerResponse({ expression: "pull it off", meaning: "해내다" });
  });
  const response = await handler(post({
    action: "meaning",
    expression: "pull it off",
    context: "We can pull it off together.",
  }));
  assert.equal(response.status, 200);
  assert.deepEqual((await response.json()).data, { expression: "pull it off", meaning: "해내다" });
  // The meaning schema needs the same guard as the content one: an unsupported keyword here
  // would break 요구사항 11절's touch-to-meaning entry point, not just content generation.
  assertOnlySupportedKeywords(sent.output_config.format.schema);

  const mismatch = createHandler(() => "key", async () => providerResponse({
    expression: "another phrase",
    meaning: "다른 뜻",
  }));
  const failed = await mismatch(post({
    action: "meaning",
    expression: "pull it off",
    context: "We can pull it off together.",
  }));
  assert.equal(failed.status, 502);
});

test("provider failures and expressions absent from their segment are contained", async () => {
  const unavailable = createHandler(() => "key", async () => new Response("private provider detail", { status: 429 }));
  const busy = await unavailable(post({ action: "content", mode: "conversation", difficulty: 1 }));
  assert.equal(busy.status, 503);
  assert.equal((await busy.text()).includes("private provider detail"), false);

  // 529 is the provider's own overloaded signal and must read as busy, not as a broken request.
  const overloaded = createHandler(() => "key", async () => new Response("overloaded", { status: 529 }));
  assert.equal((await overloaded(post({ action: "content", mode: "conversation", difficulty: 1 }))).status, 503);

  // A declined turn is HTTP 200 with no usable payload; it must not be parsed as content.
  const refused = createHandler(() => "key", async () => providerResponse(validContent, 200, "refusal"));
  const declined = await refused(post({ action: "content", mode: "conversation", difficulty: 1 }));
  assert.equal(declined.status, 502);
  assert.equal((await declined.json()).error.code, "provider_refused");

  // Every annotation absent from its segment leaves nothing to teach, so the passage fails.
  const malformedContent = {
    ...validContent,
    expressions: [{ ...validContent.expressions[0], text: "not in the passage" }],
  };
  const malformed = createHandler(() => "key", async () => providerResponse(malformedContent));
  const response = await malformed(post({ action: "content", mode: "conversation", difficulty: 1 }));
  assert.equal(response.status, 502);
  assert.equal((await response.json()).error.check, "missing_new_expression");

  // One review expression the model did not weave in is the learner's loss of one re-encounter,
  // not of the passage (백로그 032): the quiz asks it from the saved list anyway.
  const missingReview = createHandler(() => "key", async () => providerResponse({
    ...validContent,
    expressions: [validContent.expressions[1], { text: "plan", meaning: "계획하다", segmentIndex: 2 }],
  }));
  const missing = await missingReview(post({
    action: "content",
    mode: "conversation",
    difficulty: 1,
    reviewExpressions: ["pull it off", "next step"],
  }));
  assert.equal(missing.status, 200);
  assert.deepEqual((await missing.json()).data.expressions.map((item) => item.text), ["next step", "plan"]);

  // Every review expression ignored is the model not doing the job: one more attempt, then fail.
  let ignoring = 0;
  const ignoredAll = createHandler(() => "key", async () => {
    ignoring++;
    return providerResponse(validContent);
  });
  const none = await ignoredAll(post({
    action: "content",
    mode: "conversation",
    difficulty: 1,
    reviewExpressions: ["a phrase the passage never used"],
  }));
  assert.equal(none.status, 502);
  assert.equal((await none.json()).error.check, "missing_review_expression");
  assert.equal(ignoring, 2);
});

test("the contract states the expression counts with the review phrases spelled out, and weaves at most five", async () => {
  const systems = [];
  const handler = createHandler(() => "key", async (url, options) => {
    const body = JSON.parse(options.body);
    systems.push({ system: body.system, request: JSON.parse(body.messages[0].content) });
    return providerResponse(validContent);
  });
  await handler(post({ action: "content", mode: "conversation", difficulty: 1 }));
  assert.match(systems[0].system, /`expressions`: 5 entries in total\. 5 are NEW phrases/);

  const seven = ["a1", "a2", "a3", "a4", "a5", "a6", "a7"];
  await handler(post({ action: "content", mode: "story", difficulty: 2, reviewExpressions: seven }));
  assert.match(systems[1].system, /`expressions`: 10 entries in total\. 5 of them are the supplied review expressions/);
  assert.deepEqual(systems[1].request.reviewExpressions, seven.slice(0, 5));
});

test("a phrase annotated with the wrong segment index is placed where it actually is", async () => {
  const misplaced = {
    ...validContent,
    expressions: [{ text: "pull it off", meaning: "해내다", segmentIndex: 5 }],
  };
  const handler = createHandler(() => "key", async () => providerResponse(misplaced));
  const response = await handler(post({ action: "content", mode: "conversation", difficulty: 1 }));
  assert.equal(response.status, 200);
  assert.deepEqual((await response.json()).data.expressions, [
    { text: "pull it off", meaning: "해내다", segmentIndex: 0, startIndex: 6, endIndex: 17 },
  ]);
});

test("a payload that breaks the contract is asked for once more; a provider refusal is not", async () => {
  let calls = 0;
  const flaky = createHandler(() => "key", async () => {
    calls++;
    return providerResponse(calls === 1 ? { ...validContent, segments: validContent.segments.slice(0, 3) } : validContent);
  });
  const response = await flaky(post({ action: "content", mode: "conversation", difficulty: 1 }));
  assert.equal(response.status, 200);
  assert.equal(calls, 2);

  let refusals = 0;
  const refusing = createHandler(() => "key", async () => {
    refusals++;
    return new Response("no", { status: 429 });
  });
  assert.equal((await refusing(post({ action: "content", mode: "conversation", difficulty: 1 }))).status, 503);
  assert.equal(refusals, 1);

  // A network failure never reached the model, so it is not the model's bad day: no retry.
  let attempts = 0;
  const unreachable = createHandler(() => "key", async () => {
    attempts++;
    throw new TypeError("error sending request");
  });
  const down = await unreachable(post({ action: "content", mode: "conversation", difficulty: 1 }));
  assert.equal(down.status, 502);
  assert.equal(attempts, 1);

  // Text the model returned that is not JSON is retried and, failing twice, named.
  let garbled = 0;
  const notJson = createHandler(() => "key", async () => {
    garbled++;
    return new Response(JSON.stringify({ stop_reason: "end_turn", content: [{ type: "text", text: "{not json" }] }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  });
  const bad = await notJson(post({ action: "content", mode: "conversation", difficulty: 1 }));
  assert.equal(bad.status, 502);
  assert.equal((await bad.json()).error.check, "invalid_model_json");
  assert.equal(garbled, 2);
});

test("an unusable annotation is dropped instead of discarding the whole passage", async () => {
  const handler = createHandler(() => "key", async () => providerResponse({
    ...validContent,
    expressions: [
      ...validContent.expressions,
      // The model inflected the phrase, so there are no offsets we could trust for it.
      { text: "pulled it off", meaning: "해냈다", segmentIndex: 0 },
    ],
  }));

  const response = await handler(post({ action: "content", mode: "conversation", difficulty: 1 }));

  assert.equal(response.status, 200);
  const { expressions } = (await response.json()).data;
  assert.deepEqual(expressions.map((item) => item.text), ["pull it off", "next step"]);
});

test("a phrase written with typographic punctuation still matches its segment", async () => {
  // The segment uses a curly apostrophe and an en dash; the annotation uses ASCII.
  const segments = Array.from({ length: 8 }, (_, index) => ({
    speaker: index % 2 ? "Sam" : "Alex",
    text: index === 0
      ? "It\u2019s a long shot \u2013 but we can pull it off."
      : `We can plan the next step ${index}.`,
  }));
  const handler = createHandler(() => "key", async () => providerResponse({
    ...validContent,
    segments,
    expressions: [
      { text: "It's a long shot", meaning: "가능성이 낮다", segmentIndex: 0 },
      { text: "next step", meaning: "다음 단계", segmentIndex: 1 },
    ],
  }));

  const response = await handler(post({ action: "content", mode: "conversation", difficulty: 1 }));

  assert.equal(response.status, 200);
  const [first] = (await response.json()).data.expressions;
  assert.equal(first.startIndex, 0);
  // The offsets index the original segment text, curly punctuation and all.
  assert.equal(segments[0].text.slice(first.startIndex, first.endIndex), "It\u2019s a long shot");
});

test("every folded code point is accepted in a segment, and a length-changing one is dropped", async () => {
  // Kept in step with foldTypography in the Android client. Each pair is one character folded
  // onto one ASCII character; the phrase is written with the ASCII form.
  const pairs = [
    ["\u2018", "'"], ["\u2019", "'"], ["\u02BC", "'"], ["\u00B4", "'"],
    ["\u201C", '"'], ["\u201D", '"'],
    ["\u00A0", " "], ["\u2007", " "], ["\u2009", " "], ["\u202F", " "],
    ["\u2010", "-"], ["\u2011", "-"], ["\u2013", "-"], ["\u2014", "-"],
  ];

  for (const [fancy, plain] of pairs) {
    const segments = Array.from({ length: 8 }, (_, index) => ({
      speaker: "Narrator",
      text: index === 0 ? `it${fancy}s here now` : `Another line ${index}.`,
    }));
    const handler = createHandler(() => "key", async () => providerResponse({
      ...validContent,
      mode: "story",
      segments,
      expressions: [
        { text: `it${plain}s here`, meaning: "여기 있다", segmentIndex: 0 },
        { text: "Another line", meaning: "다른 줄", segmentIndex: 1 },
      ],
    }));

    const response = await handler(post({ action: "content", mode: "story", difficulty: 1 }));
    assert.equal(response.status, 200, `U+${fancy.codePointAt(0).toString(16)} was not folded`);
    const [first] = (await response.json()).data.expressions;
    assert.equal(first.startIndex, 0);
    assert.equal(first.endIndex, `it${plain}s here`.length);
  }
});

test("a phrase whose folding would change its length is dropped, not mis-highlighted", async () => {
  // U+0130 lowercases to two code points, so its offsets cannot be trusted.
  const segments = Array.from({ length: 8 }, (_, index) => ({
    speaker: "Narrator",
    text: index === 0 ? "\u0130stanbul was quiet." : `Another line ${index}.`,
  }));
  const handler = createHandler(() => "key", async () => providerResponse({
    ...validContent,
    mode: "story",
    segments,
    expressions: [
      { text: "\u0130stanbul", meaning: "이스탄불", segmentIndex: 0 },
      { text: "Another line", meaning: "다른 줄", segmentIndex: 1 },
    ],
  }));

  const response = await handler(post({ action: "content", mode: "story", difficulty: 1 }));

  assert.equal(response.status, 200);
  const { expressions } = (await response.json()).data;
  assert.deepEqual(expressions.map((item) => item.text), ["Another line"]);
});

test("an output-contract failure names the broken check but never an internal message", async () => {
  const tooShort = createHandler(() => "key", async () => providerResponse({
    ...validContent,
    segments: validContent.segments.slice(0, 3),
  }));
  const named = await tooShort(post({ action: "content", mode: "conversation", difficulty: 1 }));
  assert.equal(named.status, 502);
  assert.deepEqual((await named.json()).error, {
    code: "invalid_provider_response",
    check: "invalid_content_output(segments=3,expressions=2,mode=conversation)",
  });

  // Anything that is not one of our own contract names must not ride out to the caller.
  const internal = createHandler(() => "key", async () => {
    throw new Error("internal detail with a file path");
  });
  const hidden = await internal(post({ action: "content", mode: "conversation", difficulty: 1 }));
  const body = await hidden.text();
  assert.equal(JSON.parse(body).error.check, "unknown");
  assert.equal(body.includes("internal detail"), false);
});

test("each provider rejection class gets its own code so the operator knows what to fix", async () => {
  const cases = [
    [401, "provider_auth", 502],
    [403, "provider_auth", 502],
    [404, "provider_model_unavailable", 502],
    [400, "provider_request_rejected", 502],
    [500, "provider_error", 502],
  ];
  for (const [status, code, expected] of cases) {
    const handler = createHandler(() => "key", async () => new Response("provider detail", { status }));
    const response = await handler(post({ action: "content", mode: "story", difficulty: 2 }));
    assert.equal(response.status, expected, `status for provider ${status}`);
    const body = await response.json();
    assert.equal(body.error.code, code, `code for provider ${status}`);
    assert.equal(JSON.stringify(body).includes("provider detail"), false);
  }
});

test("missing server secret and timeout return safe errors; OPTIONS does not call the provider", async () => {
  const noSecret = createHandler(() => undefined, async () => assert.fail("provider should not be called"));
  const missing = await noSecret(post({ action: "content", mode: "story", difficulty: 3 }));
  assert.equal(missing.status, 503);
  assert.equal((await missing.json()).error.code, "service_not_configured");

  const timeout = createHandler(() => "key", async () => { throw new DOMException("internal detail", "TimeoutError"); });
  const timedOut = await timeout(post({ action: "content", mode: "story", difficulty: 3 }));
  assert.equal(timedOut.status, 504);
  assert.equal((await timedOut.json()).error.code, "provider_timeout");

  const incomplete = createHandler(() => "key", async () => providerResponse(validContent, 200, "max_tokens"));
  const partial = await incomplete(post({ action: "content", mode: "conversation", difficulty: 2 }));
  assert.equal(partial.status, 502);

  const options = await noSecret(new Request("https://local.test/ai-learning", { method: "OPTIONS" }));
  assert.equal(options.status, 204);
});

test("a glossary rides along and bad entries are dropped, never failing the passage", async () => {
  const withGlossary = {
    ...validContent,
    glossary: [
      { word: "plan", meaning: "계획" },
      { word: "  step ", meaning: " 단계 " },
      { word: "", meaning: "빈 단어" },
      { word: "blank", meaning: "" },
      { word: 42, meaning: "숫자" },
      "not an object",
    ],
  };
  const handler = createHandler(() => "key", async () => providerResponse(withGlossary));
  const response = await handler(post({ action: "content", mode: "conversation", difficulty: 2 }));
  assert.equal(response.status, 200);
  assert.deepEqual((await response.json()).data.glossary, [
    { word: "plan", meaning: "계획" },
    { word: "step", meaning: "단계" },
  ]);

  // A reply without a glossary at all is still a passage: the field is a courtesy, not a contract.
  const bare = createHandler(() => "key", async () => providerResponse(validContent));
  const bareResponse = await bare(post({ action: "content", mode: "conversation", difficulty: 2 }));
  assert.deepEqual((await bareResponse.json()).data.glossary, []);
});

test("a malformed annotation is dropped like an unusable one, so the passage still arrives", async () => {
  const withBadEntries = {
    ...validContent,
    expressions: [
      ...validContent.expressions,
      { text: "next step", meaning: "다음 단계", segmentIndex: 99 },
      { text: "", meaning: "빈 표현", segmentIndex: 0 },
      "not an object",
    ],
  };
  const handler = createHandler(() => "key", async () => providerResponse(withBadEntries));
  const response = await handler(post({ action: "content", mode: "conversation", difficulty: 2 }));
  assert.equal(response.status, 200);
  assert.deepEqual((await response.json()).data.expressions, expectedContent.expressions);
});

test("the story prompt is written for its reader and carries a premise; the conversation prompt stays a dialogue", async () => {
  const systems = [];
  const handler = createHandler(() => "key", async (url, options) => {
    systems.push(JSON.parse(options.body).system);
    return providerResponse({ ...validContent, mode: JSON.parse(options.body).messages[0].content.includes('"story"') ? "story" : "conversation" });
  });
  await handler(post({ action: "content", mode: "story", difficulty: 2 }));
  await handler(post({ action: "content", mode: "conversation", difficulty: 2 }));
  const [story, conversation] = systems;
  // 요구사항 9.1: adult, witty, no fable — 백로그 028: for the learner she is — and 백로그 033: a
  // sitcom that is funny on the surface, since the satirical version read as "no idea what this is".
  assert.match(story, /woman in her forties/);
  assert.match(story, /sitcom episode/);
  assert.match(story, /laugh out loud/);
  assert.match(story, /dry irony, understatement .* are not/);
  assert.match(story, /No moral lesson/);
  assert.match(story, /Today's premise: .+\.$/);
  assert.match(story, /Narrator/);
  assert.match(conversation, /dialogue/);
  assert.doesNotMatch(conversation, /premise/);
  // Both still carry the learning contract the app depends on.
  for (const system of systems) assert.match(system, /10 to 20 other single words/);
});

test("an annotation that is a whole sentence is dropped, the short phrases stay", async () => {
  const chatty = {
    ...validContent,
    segments: [
      ...validContent.segments,
      { speaker: "Alex", text: "I am not settling for rain, I am just getting wet, same as you would be." },
    ],
    expressions: [
      ...validContent.expressions,
      { text: "I am not settling for rain, I am just getting wet, same as you would be.", meaning: "문장 통째", segmentIndex: 8 },
      { text: "settling for", meaning: "~에 만족하다", segmentIndex: 8 },
    ],
  };
  const handler = createHandler(() => "key", async () => providerResponse(chatty));
  const response = await handler(post({ action: "content", mode: "conversation", difficulty: 2 }));
  assert.equal(response.status, 200);
  const texts = (await response.json()).data.expressions.map((expression) => expression.text);
  assert.deepEqual(texts, ["pull it off", "next step", "settling for"]);
});

test("each difficulty puts its own rubric in front of the model, not a bare number", async () => {
  const systems = {};
  const handler = createHandler(() => "key", async (url, options) => {
    const body = JSON.parse(options.body);
    systems[JSON.parse(body.messages[0].content).difficulty] = body.system;
    return providerResponse(validContent);
  });
  for (const difficulty of [1, 2, 3, 4, 5]) {
    await handler(post({ action: "content", mode: "conversation", difficulty }));
  }
  assert.match(systems[1], /Difficulty 1 of 5 \(starter\).*at most 9 words/);
  assert.match(systems[2], /Difficulty 2 of 5 \(beginner\).*at most 12 words/);
  assert.match(systems[3], /Difficulty 3 of 5 \(intermediate\)/);
  assert.match(systems[4], /Difficulty 4 of 5 \(upper-intermediate\)/);
  assert.match(systems[5], /Difficulty 5 of 5 \(advanced\).*wordplay/);
  assert.doesNotMatch(systems[1], /advanced/);
  // Six is outside the scale (백로그 034: five levels per mode).
  assert.equal((await handler(post({ action: "content", mode: "conversation", difficulty: 6 }))).status, 400);
});

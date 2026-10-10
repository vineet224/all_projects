import { describe, it } from "node:test";
import assert from "node:assert";
import { PersonalizationEngine } from "../src/engine/personalization.engine";
import { RawUpstreamContext } from "../src/types/domain";
import {
  MOCK_KUNDLI,
  MOCK_USERS,
  MOCK_HOROSCOPE,
  MOCK_PANCHANG,
} from "../src/services/upstream/mock-data";

describe("PersonalizationEngine", () => {
  const engine = new PersonalizationEngine();

  const sampleContext: RawUpstreamContext = {
    user: MOCK_USERS["user_101"],
    kundli: MOCK_KUNDLI["user_101"],
    horoscope: MOCK_HOROSCOPE["user_101"],
    panchang: MOCK_PANCHANG,
    errors: [],
  };

  it("selects correct primary and secondary context for career query", () => {
    const question =
      "Should I consider changing my job in the next few months?";
    const decision = engine.process(question, sampleContext);

    assert.strictEqual(decision.intent, "career");
    assert.strictEqual(decision.confidence, "HIGH");
    assert.strictEqual(decision.isDegraded, false);

    // Selected context should include 10th house and career horoscope
    assert.ok(decision.selectedContext.some((c) => c.includes("10th House")));
    assert.ok(
      decision.selectedContext.some((c) => c.includes("Career Horoscope")),
    );

    // Excluded context must explicitly drop relationship horoscope
    assert.ok(decision.excludedContext.includes("Relationship Horoscope"));

    // Formatted prompt text must not contain relationship horoscope
    assert.ok(!decision.promptContextText.includes("Relationship Horoscope"));
    assert.ok(decision.promptContextText.includes("10th House"));
  });

  it("selects relationship context and excludes career context for relationship query", () => {
    const question = "How does this month look for my relationship?";
    const decision = engine.process(question, sampleContext);

    assert.strictEqual(decision.intent, "relationship");
    assert.ok(decision.selectedContext.some((c) => c.includes("7th House")));
    assert.ok(
      decision.selectedContext.some((c) =>
        c.includes("Relationship Horoscope"),
      ),
    );
    assert.ok(decision.excludedContext.includes("Career Horoscope"));
  });

  it("adjusts maxWords for premium subscription", () => {
    const question =
      "Should I consider changing my job in the next few months?";
    const decision = engine.process(question, sampleContext);

    // user_101 is premium -> 250 base + 50 premium bonus = 300 words
    assert.strictEqual(decision.maxWords, 300);
  });
});

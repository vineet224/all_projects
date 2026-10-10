import { describe, it } from "node:test";
import assert from "node:assert";
import { InMemoryCacheService } from "../src/services/cache/memory.cache";
import { UserService } from "../src/services/upstream/user.service";
import { KundliService } from "../src/services/upstream/kundli.service";
import { HoroscopeService } from "../src/services/upstream/horoscope.service";
import { PanchangService } from "../src/services/upstream/panchang.service";
import { UpstreamAggregatorService } from "../src/services/upstream/aggregator.service";
import { PersonalizationEngine } from "../src/engine/personalization.engine";
import { PromptBuilder } from "../src/engine/prompt.builder";
import { PERSONALIZATION_MATRIX } from "../src/config/personalization.matrix";

describe("Graceful Degradation & Upstream Resilience", () => {
  it("handles complete KundliService failure gracefully with confidence LOW", async () => {
    const cache = new InMemoryCacheService();
    const userSvc = new UserService(cache);
    const kundliSvc = new KundliService();
    const horoscopeSvc = new HoroscopeService();
    const panchangSvc = new PanchangService(cache);

    // Simulate upstream outage on Kundli
    kundliSvc.setSimulateFailure(true);

    const aggregator = new UpstreamAggregatorService(
      userSvc,
      kundliSvc,
      horoscopeSvc,
      panchangSvc,
      500,
    );
    const engine = new PersonalizationEngine();
    const promptBuilder = new PromptBuilder();

    // 1. Fetch data - should NOT throw!
    const rawContext = await aggregator.aggregateContext("user_101");
    assert.strictEqual(rawContext.kundli, null);
    assert.strictEqual(rawContext.errors.length, 1);
    assert.strictEqual(rawContext.errors[0].serviceName, "KundliService");

    // 2. Personalize career question (10th house is mandatory primary)
    const question =
      "Should I consider changing my job in the next few months?";
    const decision = engine.process(question, rawContext);

    assert.strictEqual(decision.intent, "career");
    assert.strictEqual(decision.confidence, "LOW");
    assert.strictEqual(decision.isDegraded, true);
    assert.ok(decision.degradationReason?.includes("10th House"));

    // 3. Prompt Builder should include degradation guidance for the LLM
    const rule = PERSONALIZATION_MATRIX["career"];
    const prompt = promptBuilder.build(
      rule,
      decision,
      rawContext.user,
      question,
    );
    assert.ok(prompt.systemPrompt.includes("NOTE ON DATA AVAILABILITY"));
  });

  it("degrades confidence to MEDIUM when only secondary context (Panchang) fails", async () => {
    const cache = new InMemoryCacheService();
    const userSvc = new UserService(cache);
    const kundliSvc = new KundliService();
    const horoscopeSvc = new HoroscopeService();
    const panchangSvc = new PanchangService(cache);

    // Panchang fails, but Kundli & Horoscope are working
    panchangSvc.setSimulateFailure(true);

    const aggregator = new UpstreamAggregatorService(
      userSvc,
      kundliSvc,
      horoscopeSvc,
      panchangSvc,
      500,
    );
    const engine = new PersonalizationEngine();

    const rawContext = await aggregator.aggregateContext("user_101");
    assert.ok(rawContext.kundli !== null);
    assert.ok(rawContext.horoscope !== null);
    assert.strictEqual(rawContext.panchang, null);

    const question =
      "Should I consider changing my job in the next few months?";
    const decision = engine.process(question, rawContext);

    assert.strictEqual(decision.confidence, "MEDIUM");
    assert.strictEqual(decision.isDegraded, true);
  });
});

import { describe, it } from "node:test";
import assert from "node:assert";
import { KeywordIntentClassifier } from "../src/engine/intent.classifier";

describe("KeywordIntentClassifier", () => {
  const classifier = new KeywordIntentClassifier();

  it("classifies career-related questions", () => {
    assert.strictEqual(
      classifier.classify(
        "Should I consider changing my job in the next few months?",
      ),
      "career",
    );
    assert.strictEqual(
      classifier.classify("Will I get a promotion or work abroad this year?"),
      "career",
    );
  });

  it("classifies relationship-related questions", () => {
    assert.strictEqual(
      classifier.classify("How does this month look for my relationship?"),
      "relationship",
    );
    assert.strictEqual(
      classifier.classify("When will I get married to my partner?"),
      "relationship",
    );
  });

  it("classifies health-related questions", () => {
    assert.strictEqual(
      classifier.classify("What should I focus on for my health?"),
      "health",
    );
    assert.strictEqual(
      classifier.classify("I am having trouble with sleep and stress."),
      "health",
    );
  });

  it("classifies finance-related questions", () => {
    assert.strictEqual(
      classifier.classify(
        "Is this a good time to make a real estate investment?",
      ),
      "finance",
    );
  });

  it("classifies general and summary questions", () => {
    assert.strictEqual(
      classifier.classify("Can you summarize today's guidance?"),
      "general",
    );
    assert.strictEqual(
      classifier.classify("What should I prioritize this week?"),
      "general",
    );
  });
});

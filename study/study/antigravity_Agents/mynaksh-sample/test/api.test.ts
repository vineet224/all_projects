import { describe, it, before, after } from "node:test";
import assert from "node:assert";
import http from "http";
import { createApp } from "../src/app";

describe("End-to-End API Routes", () => {
  let server: http.Server;
  let baseUrl: string;

  before(async () => {
    const app = createApp();
    await new Promise<void>((resolve) => {
      server = app.listen(0, () => {
        const address = server.address() as any;
        baseUrl = `http://localhost:${address.port}`;
        resolve();
      });
    });
  });

  after((done) => {
    server.close(done);
  });

  async function postJson(path: string, body: any) {
    const res = await fetch(`${baseUrl}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    const json = await res.json();
    return { status: res.status, json };
  }

  it("GET /health returns 200 and ok status", async () => {
    const res = await fetch(`${baseUrl}/health`);
    const json = await res.json();
    assert.strictEqual(res.status, 200);
    assert.strictEqual(json.status, "ok");
  });

  it("POST /personalize returns grounded answer, confidence, and sourcesUsed", async () => {
    const { status, json } = await postJson("/personalize", {
      userId: "user_101",
      question: "Should I consider changing my job in the next few months?",
    });

    assert.strictEqual(status, 200);
    assert.strictEqual(typeof json.answer, "string");
    assert.strictEqual(json.confidence, "HIGH");
    assert.ok(Array.isArray(json.sourcesUsed));
    assert.ok(json.sourcesUsed.some((s: string) => s.includes("10th House")));
    assert.ok(
      json.sourcesUsed.some((s: string) => s.includes("Career Horoscope")),
    );
  });

  it("POST /debug/personalization returns interpretation without LLM generation", async () => {
    const { status, json } = await postJson("/debug/personalization", {
      userId: "user_101",
      question: "How does this month look for my relationship?",
    });

    assert.strictEqual(status, 200);
    assert.strictEqual(json.intent, "relationship");
    assert.strictEqual(json.confidence, "HIGH");
    assert.ok(
      json.selectedContext.some((s: string) => s.includes("7th House")),
    );
    assert.ok(json.excludedContext.includes("Career Horoscope"));
    assert.ok(json.assembledPrompt.contextText.includes("7th House"));
    assert.ok(!json.assembledPrompt.contextText.includes("Career Horoscope"));
  });

  it("POST /personalize returns 400 on invalid input", async () => {
    const { status, json } = await postJson("/personalize", {
      userId: "",
      question: "ab", // less than 3 chars
    });

    assert.strictEqual(status, 400);
    assert.strictEqual(json.error, "Validation Error");
  });
});

import express from "express";

import { MemoryCache } from "./modules/cache/memory-cache";

import { HttpUserAdapter } from "./modules/upstream/user.adapter";
import { HttpKundliAdapter } from "./modules/upstream/kundli.adapter";
import { HttpHoroscopeAdapter } from "./modules/upstream/horoscope.adapter";
import { HttpPanchangAdapter } from "./modules/upstream/panchang.adapter";

import { UpstreamAggregator } from "./modules/upstream/upstream.aggregator";

import { IntentDetector } from "./modules/personalization/intent.detector";
import { ContextSelector } from "./modules/personalization/context.selector";
import { PersonalizationEngine } from "./modules/personalization/personalization.engine";
import { PromptBuilder } from "./modules/personalization/prompt.builder";

import { MockLLMProvider } from "./modules/llm/mock-llm.provider";
import { OpenAILLMProvider } from "./modules/llm/openai-llm.provider";
import { LLMProvider } from "./modules/llm/llm.provider";

import { PersonalizationService } from "./modules/personalization/personalization.service";
import { PersonalizationController } from "./modules/personalization/personalization.controller";
import { createPersonalizationRoutes } from "./modules/personalization/personalization.routes";

import mockUpstreamRoutes from "./modules/mocks/mock-upstream.routes";

import { requestLogger } from "./middleware/request-logger";
import { errorHandler } from "./middleware/error-handler";
import { notFound } from "./middleware/not-found";

import { env } from "./config/env";
import { HttpClient } from "./utils/http-client";

export function createApp() {
  const app = express();

  app.use(express.json());

  app.use(requestLogger);

  app.get("/health", (_req, res) => {
    res.json({
      status: "ok",
    });
  });

  const cache = new MemoryCache();
  const httpClient = new HttpClient();

  const upstreamAggregator = new UpstreamAggregator({
    userAdapter: new HttpUserAdapter(cache, httpClient),
    kundliAdapter: new HttpKundliAdapter(cache, httpClient),
    horoscopeAdapter: new HttpHoroscopeAdapter(cache, httpClient),
    panchangAdapter: new HttpPanchangAdapter(cache, httpClient),
  });

  const personalizationEngine = new PersonalizationEngine(
    new IntentDetector(),
    new ContextSelector(),
  );

  const promptBuilder = new PromptBuilder();

  let llmProvider: LLMProvider = new MockLLMProvider();

  if (env.llmProvider === "openai" && env.openaiApiKey) {
    llmProvider = new OpenAILLMProvider(env.openaiApiKey, env.openaiModel);
  }

  const personalizationService = new PersonalizationService(
    upstreamAggregator,
    personalizationEngine,
    promptBuilder,
    llmProvider,
  );

  const controller = new PersonalizationController(personalizationService);

  app.use(createPersonalizationRoutes(controller));

  // Local mock upstream services.
  // These simulate the services described in the assignment.
  app.use("/mock", mockUpstreamRoutes);

  app.use(notFound);

  app.use(errorHandler);

  return app;
}

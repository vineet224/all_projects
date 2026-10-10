import express, { Application, Request, Response } from "express";
import { config } from "./config/env";
import { globalCache } from "./services/cache/memory.cache";
import { UserService } from "./services/upstream/user.service";
import { KundliService } from "./services/upstream/kundli.service";
import { HoroscopeService } from "./services/upstream/horoscope.service";
import { PanchangService } from "./services/upstream/panchang.service";
import { UpstreamAggregatorService } from "./services/upstream/aggregator.service";
import { PersonalizationEngine } from "./engine/personalization.engine";
import { PromptBuilder } from "./engine/prompt.builder";
import { MockLlmProvider } from "./services/llm/mock.llm";
import { GeminiLlmProvider } from "./services/llm/gemini.llm";
import { ILlmProvider } from "./services/llm/llm.interface";
import { PersonalizeController } from "./controllers/personalize.controller";
import { DebugController } from "./controllers/debug.controller";
import { requestLogger } from "./middleware/logger.middleware";
import { errorHandler } from "./middleware/error.middleware";

export interface AppServices {
  userService: UserService;
  kundliService: KundliService;
  horoscopeService: HoroscopeService;
  panchangService: PanchangService;
  aggregator: UpstreamAggregatorService;
  engine: PersonalizationEngine;
  promptBuilder: PromptBuilder;
  llmProviders: Record<string, ILlmProvider>;
}

export function buildDefaultServices(): AppServices {
  const userService = new UserService(globalCache, config.cacheUserTtlMs);
  const kundliService = new KundliService();
  const horoscopeService = new HoroscopeService();
  const panchangService = new PanchangService(
    globalCache,
    config.cachePanchangTtlMs,
  );

  const aggregator = new UpstreamAggregatorService(
    userService,
    kundliService,
    horoscopeService,
    panchangService,
    config.upstreamTimeoutMs,
  );

  const engine = new PersonalizationEngine();
  const promptBuilder = new PromptBuilder();

  const llmProviders: Record<string, ILlmProvider> = {
    mock: new MockLlmProvider(),
    gemini: new GeminiLlmProvider(config.geminiApiKey),
  };

  return {
    userService,
    kundliService,
    horoscopeService,
    panchangService,
    aggregator,
    engine,
    promptBuilder,
    llmProviders,
  };
}

export function createApp(
  services: AppServices = buildDefaultServices(),
): Application {
  const app = express();

  app.use(express.json());
  app.use(requestLogger);

  // Controllers
  const defaultProvider = config.geminiApiKey
    ? config.defaultLlmProvider
    : "mock";
  const personalizeController = new PersonalizeController(
    services.aggregator,
    services.engine,
    services.promptBuilder,
    services.llmProviders,
    defaultProvider,
  );

  const debugController = new DebugController(
    services.aggregator,
    services.engine,
    services.promptBuilder,
  );

  // Core API Endpoints
  app.post("/personalize", personalizeController.handle);
  app.post("/debug/personalization", debugController.handle);

  // Health check
  app.get("/health", (_req: Request, res: Response) => {
    res.status(200).json({
      status: "ok",
      service: "MyNaksh Context Engine",
      uptime: process.uptime(),
      timestamp: new Date().toISOString(),
    });
  });

  // Local Mock Service Inspection Endpoints
  app.get("/mock/users/:userId", async (req: Request, res: Response) => {
    const userId = String(req.params.userId);
    const user = await services.userService.fetch(userId);
    res.json(user);
  });

  app.get("/mock/kundli/:userId", async (req: Request, res: Response) => {
    const userId = String(req.params.userId);
    const kundli = await services.kundliService.fetch(userId);
    res.json(kundli);
  });

  app.get("/mock/horoscope/:userId", async (req: Request, res: Response) => {
    const userId = String(req.params.userId);
    const horoscope = await services.horoscopeService.fetch(userId);
    res.json(horoscope);
  });

  app.get("/mock/panchang", async (_req: Request, res: Response) => {
    const panchang = await services.panchangService.fetch();
    res.json(panchang);
  });

  // Centralized Error Handling
  app.use(errorHandler);

  return app;
}

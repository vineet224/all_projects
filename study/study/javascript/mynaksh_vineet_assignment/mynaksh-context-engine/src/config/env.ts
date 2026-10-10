import dotenv from "dotenv";

dotenv.config();

export const env = {
  port: Number(process.env.PORT ?? 3000),

  llmProvider: process.env.LLM_PROVIDER ?? "mock",

  openaiApiKey: process.env.OPENAI_API_KEY,
  openaiModel: process.env.OPENAI_MODEL ?? "gpt-4o-mini",

  upstreamTimeoutMs: Number(process.env.UPSTREAM_TIMEOUT_MS ?? 2000),

  upstreamRetries: Number(process.env.UPSTREAM_RETRIES ?? 2),
  upstreamUrl: process.env.UPSTREAM_BASE_URL ?? "http://localhost:3000/mock",

  logLevel: process.env.LOG_LEVEL ?? "info",
};

//console.log("ENV CONFIG LOADED:", env);

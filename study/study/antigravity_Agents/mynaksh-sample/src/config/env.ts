import dotenv from "dotenv";
dotenv.config();

export const config = {
  port: parseInt(process.env.PORT || "3000", 10),
  nodeEnv: process.env.NODE_ENV || "development",
  upstreamTimeoutMs: parseInt(process.env.UPSTREAM_TIMEOUT_MS || "800", 10),
  cacheUserTtlMs: parseInt(process.env.CACHE_USER_TTL_MS || "3600000", 10), // 1 hour
  cachePanchangTtlMs: parseInt(
    process.env.CACHE_PANCHANG_TTL_MS || "21600000",
    10,
  ), // 6 hours
  geminiApiKey: process.env.GEMINI_API_KEY || "",
  defaultLlmProvider:
    (process.env.DEFAULT_LLM_PROVIDER as "mock" | "gemini") || "mock",
};

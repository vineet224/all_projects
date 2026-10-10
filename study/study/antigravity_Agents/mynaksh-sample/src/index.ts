import { createApp } from "./app";
import { config } from "./config/env";

const app = createApp();

const server = app.listen(config.port, () => {
  console.log(
    `✨ MyNaksh Personalized AI Context Engine is running on port ${config.port}`,
  );
  console.log(`🌐 Health check: http://localhost:${config.port}/health`);
  console.log(`🔮 POST /personalize endpoint ready`);
  console.log(`🔍 POST /debug/personalization endpoint ready`);
  console.log(
    `🤖 Active LLM Provider: ${config.geminiApiKey ? config.defaultLlmProvider : "mock"}`,
  );
});

// Graceful shutdown handling
const shutdown = (signal: string) => {
  console.log(`\nReceived ${signal}. Shutting down gracefully...`);
  server.close(() => {
    console.log("HTTP server closed.");
    process.exit(0);
  });

  setTimeout(() => {
    console.error("Forced shutdown due to timeout.");
    process.exit(1);
  }, 5000);
};

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));

import { createApp } from "./app";
import { env } from "./config/env";
import { logger } from "./utils/logger";

const app = createApp();

app.listen(env.port, () => {
  logger.info(
    {
      port: env.port,
      llmProvider: env.llmProvider,
    },
    "Server started",
  );
}); // currently not aware of the issues with the current server and app setup why this is not good what is better then this

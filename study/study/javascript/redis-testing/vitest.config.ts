import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globals: true,
    environment: "node",
    testTimeout: 15000,
    hookTimeout: 15000,
    reporters: ["verbose"],
    fileParallelism: false, // Run test suites sequentially to prevent port/connection contention on shared Redis
  },
});


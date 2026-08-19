import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    include: [
      "src/lib/contracts.vitest.ts",
      "src/lib/*.test.ts",
      "src/**/*.spec.ts",
    ],
  },
});

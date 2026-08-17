import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

export default defineConfig([
  ...nextVitals,
  ...nextTypescript,
  {
    rules: {
      "@next/next/no-img-element": "off",
      "react-hooks/set-state-in-effect": "off"
    }
  },
  globalIgnores([
    ".next/**", "node_modules/**", "dist/**", "coverage/**",
    "src/routes/**", "src/router.tsx", "src/routeTree.gen.ts", "src/server.ts", "src/start.ts"
  ])
]);

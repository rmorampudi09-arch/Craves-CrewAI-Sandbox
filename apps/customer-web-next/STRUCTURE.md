# Project structure

```text
src/
├── app/                 Next.js pages and same-origin BFF routes
│   ├── api/             validated Spring/APIM adapters
│   ├── checkout/        Cashfree hosted-payment route
│   └── …                 customer pages
├── assets/              original Craves visual assets
├── compat/              small Next adapter for retained UI navigation calls
├── components/          original and backend-connected UI components
├── lib/                 boundary contracts, security and server API helpers
├── screens/             customer page composition
├── services/            client session, discovery, cart and local-only wishlist
└── styles.css           retained orange/cream design tokens
```

The `src/app/api` BFF is the only browser-facing backend boundary. Server-only API and token handling belong there or in `src/lib/server-api.ts`. Do not call APIM directly from client components and do not calculate commercial amounts in the browser.

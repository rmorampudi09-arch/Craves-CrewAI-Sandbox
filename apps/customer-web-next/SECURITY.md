# Security notes

- Authentication tokens are server-only, HTTP-only cookies.
- Mutating BFF routes require same-origin requests.
- Razorpay secrets and Firebase Admin credentials do not belong in this repository or its environment files.
- `sharp` is pinned and overridden to `0.35.3` so Next.js uses the patched libvips dependency.

## Current upstream audit exception

As of 2 August 2026, `npm audit --omit=dev` reports one high and one moderate advisory for the PostCSS copy bundled inside the current Next.js `16.2.12` release. npm offers no non-breaking fixed Next.js version and its suggested forced action would downgrade Next.js to `9.3.3`, which is not acceptable.

Risk is constrained here because PostCSS runs during the controlled application build and processes only repository-owned CSS; customers cannot upload CSS or source maps into the build. Re-check the audit when upgrading Next.js and remove this exception as soon as an upstream fixed release is available.

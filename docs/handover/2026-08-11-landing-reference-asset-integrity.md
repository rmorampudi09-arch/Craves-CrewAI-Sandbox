# Craves landing reference asset integrity

Date: 2026-08-11

The desktop customer landing page uses the four reference screenshots supplied by the product owner on 2026-08-11. The build retrieves the original PNG bytes and refuses to continue if any image changes.

## Approved SHA-256 values

| Asset | SHA-256 |
| --- | --- |
| `hero-reference.png` | `51d8f9f7e8fa852fcf0db35f1b52a6b8303e0ea869fb890855cb35156fa68655` |
| `how-craves-works-reference.png` | `3e149fda7da24782a129bacdad7d652aae07358e90fd15182ccdf9730aff4796` |
| `why-craves-reference.png` | `94592a5ac7a5ed5d4466e8ab6104bae0a062f6fb870eafb786ee36692d56f400` |
| `home-chefs-app-reference.png` | `b3331ae6d53b85ba5face0383b82e25420527e208fcedee36c04a12eb19aa9bd` |

`apps/customer-web-next/scripts/extract-landing-reference-assets.mjs` validates both the PNG signature and these hashes before writing the runtime assets. A changed, substituted, recompressed, or incomplete image therefore fails the build rather than silently changing the approved landing artwork.

## Logo rule

The hero screenshot's embedded reference logo is not treated as the product logo. `ReferenceHeroDesktop.tsx` masks that area and renders the existing canonical `CravesLogo` component. No canonical logo file, logo extraction hash, favicon, or chef/customer shared logo behavior is changed by this landing update.

## Responsive behavior

The supplied reference compositions are desktop references. At `lg` and above, the landing page displays those approved compositions. Below `lg`, the existing responsive Craves landing sections remain in use so the mobile experience is not reduced to unreadable scaled desktop artwork.

## Deployment

No Azure resource, DNS, Firebase, Cashfree, APIM, database, secret, or backend change is required. After merge, deploy through the existing customer-web delivery pipeline only, using the established production-low customer-web path and existing safety confirmation parameter.

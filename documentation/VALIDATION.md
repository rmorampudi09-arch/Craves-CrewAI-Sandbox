# Documentation Validation

Automated validation executed by `.github/workflows/craves-documentation-suite.yml`.

| PDF | Pages | Extracted words | Minimum words on a non-cover page |
|---|---:|---:|---:|
| `01-overview/architecture-overview.pdf` | 56 | 16478 | 125 |
| `01-overview/company-and-product-overview.pdf` | 56 | 16702 | 129 |
| `01-overview/glossary-of-terms.pdf` | 56 | 6424 | 89 |
| `02-backend/backend-api-reference.pdf` | 63 | 13736 | 127 |
| `02-backend/backend-error-handling-and-troubleshooting.pdf` | 56 | 16716 | 131 |
| `02-backend/backend-technical-documentation.pdf` | 56 | 16306 | 127 |
| `03-frontend/frontend-error-handling-and-troubleshooting.pdf` | 56 | 16720 | 131 |
| `03-frontend/frontend-technical-documentation.pdf` | 56 | 16588 | 127 |
| `04-mobile-app/app-error-handling-and-troubleshooting.pdf` | 56 | 17606 | 133 |
| `04-mobile-app/app-technical-documentation.pdf` | 56 | 17380 | 129 |
| `05-admin-web/admin-web-error-handling-and-troubleshooting.pdf` | 56 | 16415 | 133 |
| `05-admin-web/admin-web-technical-documentation.pdf` | 56 | 16191 | 129 |
| `06-apim-and-integrations/apim-configuration-and-policies.pdf` | 56 | 16717 | 129 |
| `06-apim-and-integrations/third-party-integrations.pdf` | 56 | 16272 | 125 |
| `07-devops-and-deployments/ci-cd-pipeline-overview.pdf` | 56 | 16745 | 127 |
| `07-devops-and-deployments/environments-and-deployment-guide.pdf` | 56 | 16854 | 129 |
| `07-devops-and-deployments/milestones-and-release-history.pdf` | 56 | 16856 | 129 |
| `08-features/feature-catalog.pdf` | 58 | 16279 | 184 |
| `09-knowledge-base/kb-admin-panel.pdf` | 57 | 11292 | 185 |
| `09-knowledge-base/kb-common-issues-and-fixes.pdf` | 57 | 11590 | 191 |
| `09-knowledge-base/kb-customer-facing-app.pdf` | 57 | 11599 | 189 |
| `10-confidential-internal/business-logic-and-internal-rules.pdf` | 56 | 17091 | 136 |
| `10-confidential-internal/known-limitations-and-risks.pdf` | 56 | 16883 | 134 |
| `10-confidential-internal/security-and-access-control.pdf` | 56 | 17174 | 134 |
| `11-investor-overview/investor-facing-product-and-tech-summary.pdf` | 56 | 17043 | 135 |

## Result

PASS - all 25 PDFs are at least 50 pages, use the Edition 2.0 professional-language structure, pass the minimum content-density check, and all confidential pages contain the required `Confidential — Internal Use Only.` footer.

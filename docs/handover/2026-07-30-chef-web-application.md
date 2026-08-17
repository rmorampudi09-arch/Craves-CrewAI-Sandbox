# Craves Chef Web Application — Engineering Handover

1. **Date** — 2026-07-30.
2. **Branch** — `feature/chef-web-application`.
3. **Parent** — `feature/chef-web-mode-shell`.
4. **Status** — Code only; no pipeline or runtime change.
5. **Purpose** — Submit and inspect the existing chef application.
6. **Page** — `/chef/application`.
7. **Read BFF** — `GET /api/chef/application`.
8. **Submit BFF** — `POST /api/chef/application`.
9. **Upload BFF** — `POST /api/chef/application/proof-files`.
10. **Upstream base** — `/api/v1/chef/application`.
11. **Auth** — HTTP-only Craves session cookie.
12. **Mutation protection** — Same-origin is mandatory.
13. **Application statuses** — Only backend enum values are accepted.
14. **Submission fields** — Match `ChefApplicationRequest` exactly.
15. **Coordinates** — Optional but must be supplied as a valid pair.
16. **Email** — Required and validated.
17. **Names** — Required and length bounded.
18. **Address** — Required fields follow the backend request.
19. **Proof types** — Only backend-supported Aadhaar and PAN types.
20. **File formats** — PDF, JPEG and PNG.
21. **File size** — Bounded to 10 MB.
22. **Multipart forwarding** — Performed server-side by the BFF.
23. **File contents** — Never returned to browser after upload.
24. **Blob container** — Removed from public response.
25. **Blob name** — Removed from public response.
26. **Reviewer identity** — Removed from public response.
27. **Registered phone** — Removed from application response.
28. **Document metadata** — Type, original filename, content type, size, status and creation time only.
29. **Pending state** — Form is locked while admin review is pending.
30. **Approved state** — Application form remains read-only.
31. **Rejected state** — Existing review reason is displayed.
32. **Admin authority** — The frontend cannot approve or reject.
33. **Compliance boundary** — No FSSAI or KYC acceptance rule is invented.
34. **APIM path** — `api/v1/chef/application`.
35. **APIM owner rule** — Zero or one path owner only.
36. **APIM subscription rule** — Existing requirements are never relaxed.
37. **APIM inheritance rule** — Inherited `backend-id` causes failure.
38. **Backend health** — User/Chef Service must be ready and healthy.
39. **APIM operations** — Read, submit and proof upload.
40. **APIM policy** — Bearer-header guard and no-store response headers.
41. **CI** — `azure-pipelines-chef-web-application-ci.yml`.
42. **APIM pipeline** — `azure-pipelines-chef-application-apim.yml`.
43. **Confirmation** — APIM write defaults to false.
44. **Service connection** — Later use `Craves-Dev-Service-Connection` via the established variable.
45. **Testing** — Contract tests block private fields and invalid proof metadata.
46. **Logging** — No tokens, documents or upstream bodies are logged.
47. **Secrets** — No secret values are committed.
48. **Rollback** — Remove only the three named APIM operations and frontend routes.
49. **Next module** — Approved kitchen profile management.
50. **Acceptance** — CI, APIM static checks and controlled authenticated application/upload smoke tests all pass.

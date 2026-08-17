# Test Plan

1. Run `azure-pipelines-customer-web-next-auth-ci.yml` on the exact branch head.
2. Confirm typecheck, domain tests, Next build and security checks succeed.
3. After parent PRs merge, add Firebase public variables in Azure DevOps.
4. Use a Firebase test phone number.
5. Verify OTP and confirm the browser receives only identity JSON.
6. Confirm the access token is absent from page source, local storage and session storage.
7. Call `/api/auth/me` and confirm the expected customer identity.
8. POST `/api/auth/logout` and confirm the cookie expires.
9. Confirm a delivery BFF request returns 401 after logout.
10. Do not use real customer data during the first test.

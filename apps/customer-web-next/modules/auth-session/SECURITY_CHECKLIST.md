# Authentication Security Checklist

- [ ] Firebase Phone provider enabled.
- [ ] Final customer domain added to Firebase Authorized domains.
- [ ] Firebase test numbers used for development.
- [ ] No Firebase Admin key is exposed to browser variables.
- [ ] `CRAVES_API_BASE_URL` uses HTTPS.
- [ ] Session exchange accepts same-origin POST only.
- [ ] Access cookie remains HTTP-only and Secure in production.
- [ ] No access token is returned in JSON.
- [ ] No localStorage or sessionStorage token use exists.
- [ ] Auth responses remain `Cache-Control: no-store`.
- [ ] Exact tested commit is merged.
- [ ] Legacy web image is recorded before replacement.

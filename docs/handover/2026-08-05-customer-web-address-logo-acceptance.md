# Customer Web Address and Logo Acceptance Checklist

Use this checklist only after the source fix is merged and the customer-web delivery pipeline has deployed the merge commit.

## Logo

- Public landing page returns HTTP 200.
- `/brand/craves-logo.png` returns HTTP 200 with `image/png` content.
- The red rounded-square Craves logo is visible in the landing header.
- The logo letters are white.
- No old white border or duplicate legacy lockup is visible.

## Saved addresses

- Profile > Addresses loads without `ADDRESS_REQUEST_FAILED`.
- Historical incomplete rows remain visible.
- Incomplete historical rows show `UPDATE REQUIRED`.
- An incomplete row cannot be selected at checkout until completed.
- Editing an incomplete row does not substitute `0,0` coordinates.
- A complete address can be created, refreshed, edited and deleted.
- Safe upstream messages are shown when the backend rejects a request.

## Stop conditions

Stop and preserve the exact response status and safe response body when:

- `/brand/craves-logo.png` is not HTTP 200;
- the address BFF returns `INVALID_ADDRESS_RESPONSE`;
- the address BFF returns a 5xx status;
- a historical row disappears instead of remaining editable;
- an incomplete row appears in checkout;
- the deployed `/api/version` commit does not match the pipeline source commit.

# Customer Web Razorpay Payment

Adds customer-owned payment order creation, Razorpay Checkout, status read and backend verification.

## Security fix

Integration Service requires a Bearer token for payment create, read and verify. Before returning or verifying a payment, it loads the checkout through Order Service with the same token and confirms the checkout customer matches the payment record.

## Customer routes

- `/checkout/{checkoutId}/payment`
- `POST /api/payments/orders`
- `GET /api/payments/orders/{paymentOrderId}`
- `POST /api/payments/orders/{paymentOrderId}/verify`

## Razorpay integration

The browser loads `https://checkout.razorpay.com/v1/checkout.js` directly and opens Checkout with the backend-issued order ID and public key ID. The key secret and webhook secret remain only in Integration Service Key Vault-backed configuration. The backend verifies every checkout signature and webhook signature before changing payment state.

## Pipelines

- `azure-pipelines-razorpay-production-ci.yml`
- `azure-pipelines-razorpay-webhook-apim.yml`
- `azure-pipelines-razorpay-environment.yml`
- `azure-pipelines-razorpay-customer-web.yml`
- `azure-pipelines-razorpay-production-rollback.yml`

## Manual steps later

- Run combined Java/web CI.
- Deploy Integration Service ownership fix before exposing APIM read/verify.
- Configure APIM customer payment operations.
- Keep Razorpay mode `sandbox` until controlled tests pass.
- Register and verify the webhook separately.
- Keep Razorpay secrets in Key Vault and out of the repository.

Cashfree runtime execution remains disabled while its domain verification is pending.

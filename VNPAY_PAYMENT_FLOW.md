# VNPAY sandbox payment return

The Android checkout opens VNPAY directly. On returning to the application,
the app reads the authenticated payment status, queries VNPAY automatically
when the order is still pending, and opens that order's detail screen only
after the server confirms PAID without review. The order remains CONFIRMED;
payment does not complete delivery or release a seller payout.

The payment screen contains customer actions and a sandbox notice. Raw backend
configuration errors, QueryDR controls and internal payment record identifiers
are not shown there. Unknown results are retried for approximately two minutes;
the customer can check again or return to the order. QueryDR is limited to once
per minute per order on the server. IPN remains the primary notification channel.

## Server configuration

Cloud inspection on 2026-10-27 confirmed that reconcileVnpayPayment was ACTIVE,
PAYMENT_PROVIDER=VNPAY, VNPAY_ENV=sandbox, and VNPAY_QUERY_IP was unset. The old
provider rejected every QueryDR request in that configuration.

The fix was deployed to reconcileVnpayPayment on 2026-10-27 at
15:18:06 UTC. Cloud metadata confirmed ACTIVE, sandbox, unchanged user
environment variables and unchanged secret version bindings. Local environment
files were restored after deployment. Other functions were not redeployed.

For Cloud Run sandbox, the provider now discovers its public egress IP using
https://api.ipify.org with a four-second timeout when VNPAY_QUERY_IP is absent.
A configured VNPAY_QUERY_IP still takes precedence. A failed IP lookup or a
failed query never marks a payment successful or failed. The IP lookup is a
runtime dependency, and dynamic egress can change; a verified fixed egress IP
is preferable when one is available. No NAT or paid infrastructure is provisioned.

Deploying the Android APK alone does not update this server behavior. The only
function that needs deployment for this fix is reconcileVnpayPayment. Preserve
its existing environment variables and secret bindings during deployment.

## Verification

- Provider tests cover automatic IP discovery, invalid lookup results, signed
  QueryDR responses and mismatched or altered transaction evidence.
- Firestore Emulator tests cover lost IPN recovery, rate limiting, authentication,
  duplicate receipts, cancellation races and late financial results.
- Android PaymentConfirmationUiTest covers hidden empty payment history, verified
  success, review/cancelled/pending states, resetting a previous result and
  automatic navigation to the correct order exactly once.

These tests use fixtures and a demo emulator project. They do not charge a bank
account or create GHN shipments. Live VNPAY merchant IPN configuration and a
complete sandbox checkout still need separate validation.

# GHN Cloud authentication diagnosis (2026-10-26)

Read-only Cloud checks on project `fashion2hand` found:

- `ghnAdminShops` ACTIVE, revision `ghnadminshops-00001-qic`, deployed `2026-10-25T21:08:24.667404088Z`.
- `GHN_ENV=staging`, `GHN_TOKEN_ENV=staging`, `GHN_PRODUCTION_ENABLED=false`.
- Endpoint: `GET https://dev-online-gateway.ghn.vn/shiip/public-api/v2/shop/all?offset=0&limit=50`.
- Bound Secret `GHN_TOKEN` version `1`, ENABLED, created `2026-10-25T21:03:49.065355Z`. No newer Secret version existed at inspection time. Its value was not accessed.
- Sanitized Function log at `2026-10-25T22:13:09.900695Z`: `/v2/shop/all`, provider HTTP `401`. This is the concrete failed-precondition source in the recorded request.

Call chain: active server-side Admin check -> provider environment config -> nonempty runtime Secret check -> GHN shop listing. Admin rejection is permission-denied. Invalid GHN_ENV, mismatched GHN_TOKEN_ENV, missing/empty GHN_TOKEN and upstream HTTP 401/403 produce failed-precondition. No GHN_SHOP_ID check is called by shop listing; no ShopId header is sent. Configured shop 2543108 is used by address catalogue operations, not this list request.

`GHN_TOKEN_ENV` is a label and cannot establish whether a token really belongs to staging. HTTP 401 establishes upstream rejection, not whether the underlying cause is an invalid/revoked/wrong-environment token or access restrictions. No production probe was made. Check the token's source/permissions in GHN staging; do not send it in chat. Official shop-list documentation: https://developer.ghn.vn/vi/docs/shop/get-shop (requires Token only).

## Enter a valid staging Token privately

From the repository root, run this command WITHOUT `--data-file`, pipes, command arguments containing tokens, or `--debug`. Paste the token only into Firebase CLI's hidden interactive Secret prompt:

```powershell
firebase functions:secrets:set GHN_TOKEN --project fashion2hand
```

Keep the existing `functions/.env.fashion2hand` non-secret settings at `GHN_ENV=staging`, `GHN_TOKEN_ENV=staging`, `SHIPPING_PROVIDER=GHN`, `GHN_PRODUCTION_ENABLED=false`. Do not put GHN_TOKEN in this file. Changing the Secret creates a new version; running revisions stay bound to their old version until redeployment.

Deploy all 15 currently deployed functions that bind GHN_TOKEN (also includes the safe diagnostic code changes). This command does not deploy Firestore rules, indexes or Hosting:

```powershell
firebase deploy --project fashion2hand --only "functions:cancelGhnShipment,functions:createCartOrders,functions:createFreeRequest,functions:createPaidOrder,functions:createShipment,functions:getCartCheckoutQuote,functions:getCheckoutQuote,functions:ghnAddressCatalog,functions:ghnAdminShops,functions:ghnCallback,functions:ghnConfigureParcel,functions:ghnConfigureSeller,functions:refreshOrder,functions:respondFreeRequest,functions:saveGhnAddress"
```

Then rerun normal Firebase Admin sign-in and the shop-list callable:

```powershell
.\functions\scripts\ghn-admin.ps1 -Action ghnAdminShops -Target Cloud
```

If only deploying diagnostics with the existing Token, deploy `--only functions:ghnAdminShops`. This will not fix GHN's rejection of that Token.

The script reconstructs a safe English message and only displays allowlisted reason/environment/HTTP-status/operation details. Arbitrary provider messages/details, passwords, Token and Authorization are suppressed. It also recognizes the existing deployed provider's generic auth rejection without requiring a backend deployment. No Cloud configuration or Secret was changed during diagnosis.

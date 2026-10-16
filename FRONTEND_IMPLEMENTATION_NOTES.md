# Fashion2hand Android frontend implementation

## Technology
- Java + XML layouts
- Existing package retained: `vn.edu.ueh.thudinh.fashion2hand`
- No Firebase/backend integration and no new Gradle dependencies

## Added screens
- `activity_explore.xml`: product search, category filters, mock product grid
- `activity_sell.xml`: product listing form with local validation
- `activity_request.xml`: transaction/request UI prototype
- `activity_messages.xml`: mock conversation list and local search
- `activity_profile.xml`: profile and account menu
- `activity_edit_profile.xml`, `activity_account_verification.xml`, `activity_verification_status.xml`, and `activity_payment_info.xml`: profile fields, contact OTP demo, identity document uploads and consent, verification review status, and separate payout account details
- `activity_product_detail.xml`: product details and action buttons
- `activity_chat.xml`: sample conversation with send action
- `activity_cart.xml` and `activity_checkout.xml`: cart and payment selection
- `activity_orders.xml`, `activity_order_detail.xml`, `activity_delivery.xml`, `activity_delivery_map.xml`, and `activity_transaction_history.xml`: buyer/seller order lists, paid order details with Payment Holding and lifecycle timeline, shipment event history, illustrative map tracking, receipt/report/cancel actions, and transaction history
- `activity_my_listings.xml` and `activity_favorites.xml`: own listings, edit entry point, and saved products
- `activity_admin_login.xml` and `activity_admin.xml`: admin entry, dashboard, and user/post/order/report/complaint/verification review dialogs

## Updated files
- `MainActivity.java`: basic screen navigation and mock frontend interactions
- `activity_home.xml`: retained existing home/product grid, added greeting, hero section, consistent five-item bottom navigation, and product click IDs

## Frontend-only limitations
- All data is local sample data. Checkout, chat, profile edits, identity verification, payout details, listing edits, order status changes, and moderation actions are not persisted; identity document selections are not uploaded.
- Authentication and admin role checks are not connected to Firebase; the admin login is a UI prototype.
- The map is a placeholder and does not connect to a shipping provider.

## Build status
- No build or automated tests were run for this update.
- A previous environment check could not fetch the Gradle 9.5.0 distribution because network access to `services.gradle.org` was unavailable.

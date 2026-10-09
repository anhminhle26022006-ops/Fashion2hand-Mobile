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
- `activity_product_detail.xml`: product details and action buttons

## Updated files
- `MainActivity.java`: basic screen navigation and mock frontend interactions
- `activity_home.xml`: retained existing home/product grid, added greeting, hero section, consistent five-item bottom navigation, and product click IDs

## Frontend-only limitations
- Product data is local mock data; actions display feedback only.
- Image selection, authentication, messages, orders, and persistence are not connected to a backend yet.
- The Admin screens and remaining detailed commerce flows are not implemented in this first pass.

## Validation
- XML layouts parsed successfully and checked for duplicate IDs.
- Java `R.id` references checked against IDs declared in layouts.
- Gradle build could not be completed in this environment because the Gradle 9.5.0 distribution was not cached and external network access to `services.gradle.org` was unavailable. Open the project in Android Studio and run **Sync Project with Gradle Files**, then **Build > Make Project** to complete the environment-specific build test.

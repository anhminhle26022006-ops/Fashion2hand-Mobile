# 🇻🇳 LocalTaste Mobile

### 🌸 **ThreeBelles**

#### *Three girls, one taste.*

> **Discover the place. Meet the maker. Taste the story.**

**LocalTaste Mobile** is the mobile application of the **LocalTaste ecosystem**, created to bring authentic Vietnamese local specialties closer to customers through a convenient and modern mobile shopping experience.

The application connects customers with **local products, regional sellers, and the stories behind Vietnamese specialties** — allowing users to discover, explore, and shop anytime, anywhere.

> 🍵 Discover local flavors.
> 📍 Explore local origins.
> 🧺 Support local sellers.
> ❤️ Share the taste of Vietnam.

---

# 🌾 About LocalTaste Mobile

Vietnam is home to thousands of unique specialties, traditional products, and local brands.

However, many local products remain difficult to discover outside their regions.

**LocalTaste Mobile** aims to make these products more accessible by bringing the LocalTaste marketplace directly to users' smartphones.

Through the mobile application, customers can:

* 🔎 Discover Vietnamese local specialties
* 🛍️ Browse and purchase products
* 📍 Explore products by region
* 🏪 Discover local sellers
* ⭐ Read and write product reviews
* ❤️ Save favorite products
* 🛒 Manage shopping carts
* 📦 Place and track orders
* 👤 Manage personal accounts

The mobile application works as part of the larger **LocalTaste ecosystem**, working together with the web platform and shared backend services.

---

# ✨ What Makes LocalTaste Mobile Different?

### 🇻🇳 More Than Just a Shopping App

LocalTaste Mobile is not designed simply as a mobile store.

It is a **mobile gateway to Vietnamese local products and their stories**.

Every product can tell customers:

```text
        📍 WHERE
           │
       Where is it from?
           ↓
        🏪 WHO
           │
       Who makes it?
           ↓
        📖 STORY
           │
       What makes it special?
           ↓
        ❤️ TASTE
           │
       Why should you try it?
```

Instead of simply asking:

> *"What should I buy?"*

LocalTaste Mobile encourages users to discover:

> **"Where does it come from, who makes it, and what makes it special?"**

---

# 🚀 Key Features

## 👤 Customer Experience

### 🔐 Authentication

* Register
* Login / Logout
* User profile
* Update personal information
* Password management

### 🔎 Product Discovery

* Browse product catalog
* Search products
* Filter by category
* Filter by region
* Filter by price
* Sort by price / rating
* View product details
* Explore product origins
* View seller information

### 🛒 Shopping

* Add products to cart
* Update product quantity
* Remove products from cart
* Add products to wishlist
* Checkout
* Confirm orders

### 📦 Order Management

* View order history
* View order details
* Track order status
* Monitor purchasing activity

### ⭐ Reviews & Community

* Rate products
* Write product reviews
* View customer reviews
* Share shopping experiences

---

# 🏪 Local Sellers

LocalTaste Mobile is designed to support the future development of a **multi-vendor marketplace**.

Local sellers can eventually manage their products and stores through the LocalTaste ecosystem.

Potential seller functions include:

* 🏪 Seller profile
* ➕ Add products
* ✏️ Update products
* 🗑️ Remove products
* 📦 Manage inventory
* 🚚 Manage orders
* ⭐ View customer reviews
* 📊 Monitor sales performance

This allows LocalTaste to grow from a simple shopping application into a complete marketplace connecting customers and local businesses.

---

# 👑 Admin System

The LocalTaste ecosystem includes an administration system for centralized platform management.

Administrators can manage:

### 📂 Platform Management

* Users
* Sellers
* Categories
* Products
* Orders
* Reviews

### 📊 Platform Analytics

* Total users
* Total products
* Total orders
* Revenue
* Best-selling products
* Product categories

The admin system can be implemented through the **LocalTaste Web platform**, while the mobile application focuses primarily on the customer shopping experience.

---

# 🔄 Mobile & Backend Communication

LocalTaste Mobile communicates with the backend through **web services / APIs**.

The application sends requests to the backend and receives structured data for displaying and updating information.

```text
┌─────────────────────┐
│    LocalTaste       │
│    Mobile App       │
└──────────┬──────────┘
           │
           │ HTTP Request
           ↓
┌─────────────────────┐
│      Web API        │
│   PHP / Backend     │
└──────────┬──────────┘
           │
           ↓
┌─────────────────────┐
│      Database       │
│       MySQL         │
└──────────┬──────────┘
           │
           │ JSON Response
           ↓
┌─────────────────────┐
│    Mobile App       │
│    Update UI        │
└─────────────────────┘
```

### 📡 API Use Cases

The mobile application can communicate with the backend for:

* 🔐 Authentication
* 🔎 Product search
* 🏷️ Product filtering
* 📦 Product information
* 🛒 Shopping cart
* ❤️ Wishlist
* ⭐ Reviews
* 📋 Orders
* 👤 User information

Data exchanged between the mobile application and backend is structured using **JSON**.

---

# 🏗️ Mobile Architecture

The application is designed using a structured architecture to keep the code organized, maintainable, and scalable.

```text
                  LOCAL TASTE MOBILE
                          │
                          ↓
                ┌──────────────────┐
                │   Presentation   │
                │   Mobile UI      │
                └────────┬─────────┘
                         │
                         ↓
                ┌──────────────────┐
                │     Business     │
                │      Logic       │
                └────────┬─────────┘
                         │
                         ↓
                ┌──────────────────┐
                │   API / Service  │
                │     Layer        │
                └────────┬─────────┘
                         │
                     HTTP / JSON
                         │
                         ↓
                ┌──────────────────┐
                │   LocalTaste     │
                │     Backend      │
                └────────┬─────────┘
                         │
                         ↓
                ┌──────────────────┐
                │      MySQL       │
                │     Database     │
                └──────────────────┘
```

The architecture separates the user interface, application logic, service communication, and backend data layer.

This makes the application easier to maintain and allows new features to be added in the future.

---

# 🛠️ Technology Stack

## 📱 Mobile

* Android
* Kotlin
* Android Studio
* XML Layout
* Android SDK

## 🌐 Backend Communication

* RESTful API
* HTTP
* JSON
* Web Services

## 🗄️ Backend & Database

* PHP
* Object-Oriented Programming
* MVC Architecture
* MySQL

## 🔧 Development Tools

* Android Studio
* Visual Studio Code
* XAMPP
* Git
* GitHub

---

# 📁 Project Structure

```text
LocalTaste-Mobile-ThreeBelles/
│
├── app/
│   │
│   └── src/
│       └── main/
│           │
│           ├── java/
│           │   └── com/
│           │       └── localtaste/
│           │
│           ├── res/
│           │   ├── drawable/
│           │   ├── layout/
│           │   ├── mipmap/
│           │   ├── values/
│           │   └── xml/
│           │
│           └── AndroidManifest.xml
│
├── gradle/
│
├── build.gradle
├── settings.gradle
├── gradle.properties
├── .gitignore
└── README.md
```

> The project structure may evolve as new features and modules are introduced during development.

---

# 🧩 Main Mobile Screens

The application can be organized around the following main screens:

```text
                    LocalTaste Mobile
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ↓                  ↓                  ↓
      Home              Products           Profile
        │                  │                  │
        │          ┌───────┼───────┐          │
        │          │       │       │          │
        ↓          ↓       ↓       ↓          ↓
    Featured     Search  Filter  Details    Account
                         │       │
                         ↓       ↓
                      Category  Seller
                                 │
                                 ↓
                               Reviews

                           Shopping
                              │
                    ┌─────────┴─────────┐
                    ↓                   ↓
                  Cart                Order
                                        │
                                        ↓
                                    Tracking
```

### Main Screens

* 🏠 Home
* 🔎 Search
* 🏷️ Categories
* 🛍️ Product List
* 📦 Product Details
* 🏪 Seller Information
* ❤️ Wishlist
* 🛒 Cart
* 💳 Checkout
* 📋 Orders
* 🚚 Order Tracking
* ⭐ Reviews
* 👤 Profile
* 🔐 Login / Register

---

# 🗄️ Main Data Model

The mobile application uses data provided by the LocalTaste backend.

```text
User
 │
 ├── Order
 │     └── OrderDetail
 │            └── Product
 │
 ├── Review
 │       └── Product
 │
 └── Wishlist
         └── Product

Seller
 │
 └── Product
       │
       └── Category
```

### Core Entities

| Entity          | Purpose                                   |
| --------------- | ----------------------------------------- |
| **User**        | Customer account and personal information |
| **Seller**      | Local business or product provider        |
| **Product**     | Vietnamese local specialty                |
| **Category**    | Product classification                    |
| **Order**       | Customer purchase                         |
| **OrderDetail** | Products contained in an order            |
| **Review**      | Customer feedback and ratings             |
| **Wishlist**    | Products saved by customers               |

---

# 🔗 Web & Mobile Ecosystem

LocalTaste consists of two main applications:

```text
                         🇻🇳 LOCAL TASTE
                                │
                 ┌──────────────┴──────────────┐
                 │                             │
                WEB                          MOBILE
                 │                             │
      LocalTaste-Web-ThreeBelles     LocalTaste-Mobile-ThreeBelles
                 │                             │
                 └──────────────┬──────────────┘
                                │
                           REST API
                                │
                                ↓
                           MySQL Database
                                │
                 ┌──────────────┴──────────────┐
                 │                             │
          Local Sellers                 Local Products
```

### 💻 Web Repository

**LocalTaste-Web-ThreeBelles**

The web platform provides:

* Customer shopping
* Seller management
* Admin dashboard
* Product management
* Order management
* Platform administration

### 📱 Mobile Repository

**LocalTaste-Mobile-ThreeBelles**

The mobile application focuses on:

* Product discovery
* Mobile shopping
* Wishlist
* Cart
* Checkout
* Orders
* Reviews
* Customer account management

Both applications are designed to work with the same backend ecosystem.

---

# 🔀 Git Workflow

To maintain an organized development process, **ThreeBelles** uses GitHub with feature branches.

```text
main
 │
 └── develop
       │
       ├── feature/login
       ├── feature/register
       ├── feature/home
       ├── feature/products
       ├── feature/search
       ├── feature/cart
       ├── feature/wishlist
       ├── feature/orders
       └── feature/profile
```

Each feature is developed independently before being reviewed and merged into the development branch.

This workflow helps the team:

* Keep development organized
* Work on features independently
* Reduce merge conflicts
* Track individual contributions
* Maintain a clear project history

---

# 🌟 Future Roadmap

LocalTaste Mobile is designed to evolve together with the LocalTaste ecosystem.

## Phase 1 — Mobile E-Commerce Foundation

* [ ] User authentication
* [ ] Product catalog
* [ ] Product details
* [ ] Search
* [ ] Categories
* [ ] Shopping cart
* [ ] Wishlist
* [ ] Orders
* [ ] User profile

## Phase 2 — Better Mobile Experience

* [ ] Advanced product filtering
* [ ] Product reviews
* [ ] Seller profiles
* [ ] Promotional campaigns
* [ ] Discount vouchers
* [ ] Flash sales
* [ ] Push notifications

## Phase 3 — Marketplace

* [ ] Multi-vendor marketplace
* [ ] Seller storefronts
* [ ] Seller verification
* [ ] Seller analytics
* [ ] Seller order management

## Phase 4 — Smart LocalTaste

* [ ] Personalized recommendations
* [ ] AI-powered product search
* [ ] Smart product suggestions
* [ ] Customer–seller chat
* [ ] Real-time notifications
* [ ] Personalized home page

## Phase 5 — Real-World Integration

* [ ] Online payment
* [ ] Shipping service integration
* [ ] Location & map services
* [ ] Production deployment
* [ ] External service integration
* [ ] Cross-platform expansion

---

# 🚀 Deployment

The LocalTaste Mobile application is designed to communicate with a remotely deployed backend.

### Deployment Architecture

```text
          📱 Mobile Application
                   │
                   │ HTTPS
                   ↓
            🌐 Web Server
                   │
                   ↓
              🔌 REST API
                   │
                   ↓
              🗄️ MySQL
```

The backend can be deployed to a hosting environment, allowing the mobile application to access the same product, seller, customer, and order data used by the LocalTaste Web platform.

---

# 👩‍💻 Team — ThreeBelles

### 🌸 **ThreeBelles**

#### *Three girls, one taste.*

Three girls.
Three perspectives.
Three different ideas.
**One shared vision.**

We believe technology can do more than make shopping easier.

It can help people **discover local products, support local sellers, and connect with the stories and traditions behind Vietnamese specialties.**

LocalTaste is our way of turning that idea into a digital experience across both **Web and Mobile**.

> **Three minds. One vision. One LocalTaste. 🇻🇳**

---

# 📚 Academic Project

**Course:** Web Application Development
**Project Type:** E-Commerce / Mobile Application
**Topic:** Vietnamese Local Specialty Marketplace
**Team:** ThreeBelles
**Product:** LocalTaste Mobile

---

# 💚 Our Vision

> ## **Local products deserve a local story.**

LocalTaste aims to make Vietnamese specialties:

**Easier to discover.**
**Easier to access.**
**Easier to appreciate.**
**Easier to share.**

Through Web and Mobile, LocalTaste connects customers with the **products, people, places, and stories** behind Vietnamese local specialties.

From a university project to a potential real-world marketplace, **LocalTaste** is our first step toward bringing the authentic taste of Vietnam closer to everyone.

---

# 🇻🇳 LocalTaste × ThreeBelles

> **Discover the place.**
> **Meet the maker.**
> **Taste the story.**

### 🌸 **ThreeBelles**

### *Three girls, one taste.*

**LocalTaste Mobile — Bringing Vietnam's local taste closer to you. 🇻🇳**

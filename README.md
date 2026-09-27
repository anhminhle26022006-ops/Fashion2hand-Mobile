# Fashion2hand 👕♻️

**Fashion2hand** is a mobile application that connects people who want to **share, give away, and find second-hand clothing**. The application provides a community-oriented platform where users can create clothing posts, discover available items, save interesting posts, and request items from other users.

The project is developed as a **Mobile Application Development course project**, focusing on Android development, Firebase services, responsive UI, and offline data support.

---

## 📱 Project Overview

Many people have clothes that are still usable but no longer needed. At the same time, others may be looking for affordable or free second-hand clothing.

Fashion2hand aims to provide a simple mobile platform where users can:

* Share clothes they no longer need
* Discover second-hand clothing from other users
* Search and filter clothing posts
* Save interesting items
* Request items from their owners
* Manage their own posts and profile
* Report inappropriate posts

The application focuses on **sharing and reuse rather than traditional online shopping**.

---

## 🎯 Objectives

* Build a mobile platform for sharing second-hand clothing.
* Provide a simple and convenient mobile user experience.
* Apply Firebase as the main backend service.
* Support authentication with email/password and Google.
* Store and manage clothing images using Firebase Storage.
* Store application data using Cloud Firestore.
* Support local/offline data using Room and SQLite.
* Provide responsive layouts for portrait and landscape orientations.
* Apply a structured and maintainable application architecture.

---

## ✨ Main Features

### 👤 User Authentication

* Register and log in with email/password
* Google Sign-In
* Logout
* User profile management

### 🏠 Home & Clothing Feed

* Browse available clothing posts
* View clothing images and information
* Display post status
* View post details

### 🔎 Search & Filter

Users can discover clothing based on:

* Clothing name
* Category
* Size
* Condition
* Location

### 👕 Create Clothing Post

Users can create a post containing:

* Clothing images
* Title
* Description
* Category
* Size
* Condition
* Location

Images are uploaded to **Firebase Storage**, while post information is stored in **Cloud Firestore**.

### ❤️ Favorites

Users can save clothing posts that they are interested in and access them later from their favorites.

### 📩 Request an Item

Users can send a request to receive an available clothing item from another user.

### 👤 Profile

Users can:

* View their profile
* View their own posts
* Edit posts
* Delete posts
* Manage saved items

### 🚨 Report

Users can report inappropriate or problematic posts.

### 🛡️ Simple Admin Functions

Administrators can perform basic moderation tasks such as:

* View reported posts
* Hide inappropriate posts
* Remove problematic posts
* Manage reported content

The admin functionality is designed for mobile usage and focuses on essential moderation operations.

---

## 🔥 Firebase Services

Fashion2hand uses Firebase as the main backend platform.

### Firebase Authentication

Used for:

* Email/password authentication
* Google authentication
* User session management

### Cloud Firestore

Used to store application data such as:

```text
users
posts
favorites
requests
reports
```

### Firebase Storage

Used to store:

* User profile images
* Clothing images

Example structure:

```text
Storage
├── users/
│   └── {userId}/
│       └── avatar.jpg
│
└── posts/
    └── {postId}/
        ├── image_01.jpg
        ├── image_02.jpg
        └── image_03.jpg
```

---

## 📴 Offline Support

Fashion2hand is designed to support local data access using:

**Room → SQLite**

The application can synchronize selected data from Firebase to local storage.

Conceptually:

```text
                Fashion2hand
                      │
                 Repository
                  /       \
                 /         \
          Remote Data     Local Data
              │              │
          Firebase          Room
              │              │
        Firestore/Storage   SQLite
```

This allows previously loaded data to remain available when the device temporarily loses its Internet connection.

---

## 📐 Responsive UI

The application supports both:

* Portrait orientation
* Landscape orientation

Different layouts can be provided for different screen orientations to maintain usability.

Example:

```text
res/
├── layout/
│   └── activity_post_detail.xml
│
└── layout-land/
    └── activity_post_detail.xml
```

---

## 🏗️ Application Architecture

The project follows a layered architecture to keep UI, business logic, and data access separated.

```text
┌───────────────────────────────┐
│              UI               │
│ Activities / Fragments / XML  │
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│          ViewModel            │
│     UI State & Logic          │
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│          Repository           │
│   Data coordination layer     │
└───────────────┬───────────────┘
                │
          ┌─────┴─────┐
          ▼           ▼
     Firebase       Room
     / Remote       / Local
```

This structure helps improve:

* Code organization
* Maintainability
* Separation of concerns
* Reusability
* Testability

---

## 🛠️ Technologies & Tools

| Technology              | Purpose                                |
| ----------------------- | -------------------------------------- |
| Kotlin                  | Application development                |
| Android Studio          | Development environment                |
| XML                     | Android UI layouts                     |
| Firebase Authentication | User authentication                    |
| Cloud Firestore         | Cloud database                         |
| Firebase Storage        | Image storage                          |
| Google Sign-In          | Third-party authentication             |
| Room                    | Local database layer                   |
| SQLite                  | Local data storage                     |
| Git                     | Version control                        |
| GitHub                  | Source code management & collaboration |

---

## 📂 Project Structure

A simplified project structure:

```text
Fashion2hand/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com.example.fashion2hand/
│           │       ├── data/
│           │       ├── model/
│           │       ├── repository/
│           │       ├── ui/
│           │       ├── viewmodel/
│           │       └── utils/
│           │
│           └── res/
│               ├── drawable/
│               ├── layout/
│               ├── layout-land/
│               ├── menu/
│               └── values/
│
├── README.md
└── ...
```

The actual structure may evolve during development.

---

## 🔄 Main User Flow

```text
Login / Register
       │
       ▼
     Home
       │
 ┌─────┼───────────┐
 ▼     ▼           ▼
Search Feed      Profile
 │     │
 │     ▼
 │  Post Detail
 │     │
 │  ┌──┴───────┐
 │  ▼          ▼
 │Favorite   Request
 │
 ▼
Create Post
 │
 ├── Upload Images
 ├── Enter Information
 └── Publish
```

---

## 👥 User Roles

### User

Regular users can:

* Browse clothing posts
* Search and filter
* Create posts
* Edit/delete their own posts
* Save posts
* Request available items
* Report inappropriate content
* Manage their profile

### Admin

Administrators can:

* Review reported posts
* Hide or remove inappropriate content
* Perform basic content moderation

---

## 🌿 Git Workflow

The project uses Git and GitHub for source code management and team collaboration.

Example branch structure:

```text
main
│
└── develop
    │
    ├── feature/auth
    ├── feature/home-feed
    ├── feature/create-post
    ├── feature/search
    ├── feature/favorite
    ├── feature/request
    ├── feature/profile
    ├── feature/admin
    └── feature/offline
```

Development workflow:

```text
Feature Branch
      │
      ▼
   Commit
      │
      ▼
 Pull Request
      │
      ▼
   develop
      │
      ▼
     main
```

GitHub branches and pull requests are used to track task assignments, contributions, and merge history.

---

## 📊 Project Scope

### Core Features

* [x] Firebase Authentication
* [x] Google Sign-In
* [x] Home Feed
* [x] Clothing Post Detail
* [x] Create Clothing Post
* [x] Firebase Storage
* [x] Cloud Firestore
* [x] Search & Filter
* [x] User Profile
* [x] Edit/Delete Posts
* [x] Favorite Posts
* [x] Basic Report System
* [x] Basic Admin Moderation
* [x] Responsive Portrait/Landscape UI

### Additional Features

* [ ] Room / SQLite offline synchronization
* [ ] Item request management
* [ ] Push notifications
* [ ] Location-based features

> Feature status should be updated according to the actual implementation progress of the project.

---

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-username/Fashion2hand.git
```

### 2. Open the project

Open the project using **Android Studio**.

### 3. Configure Firebase

Create a Firebase project and enable the required services:

* Firebase Authentication
* Cloud Firestore
* Firebase Storage
* Google Sign-In

Add the required Firebase configuration file:

```text
app/
└── google-services.json
```

> Do not commit sensitive credentials or private configuration files to a public repository.

### 4. Build and Run

Sync the Gradle project and run the application on:

* Android Emulator
* Android physical device

---

## 🔐 Security Considerations

The application uses Firebase Authentication to protect user accounts.

Firestore and Storage security rules should restrict users from:

* Modifying other users' posts
* Deleting other users' content
* Accessing unauthorized data

Example access principle:

```text
User
 ├── Read public posts
 ├── Create own posts
 ├── Update own posts
 └── Delete own posts

Admin
 └── Moderate reported content
```

---

## 📸 Screenshots

Screenshots of the application will be added as the project progresses.

Suggested screens:

```text
Login
Home
Search
Post Detail
Create Post
Favorites
Request
Profile
Admin
Offline Mode
```

---

## 🎓 Academic Project

This project is developed for the **Mobile Application Development** course.

The project focuses on applying:

* Android development
* Firebase services
* Authentication
* Cloud database
* Cloud storage
* Local database and offline support
* Responsive mobile UI
* Git/GitHub collaboration
* Mobile application architecture

---

## 👩‍💻 Contributors

| Member   | Responsibilities               | Completion |
| -------- | ------------------------------ | ---------: |
| Member 1 | Authentication & Profile       |          - |
| Member 2 | Home Feed & Search             |          - |
| Member 3 | Create Post & Firebase Storage |          - |
| Member 4 | Favorites, Requests & Offline  |          - |

> Update the member names, responsibilities, and completion percentages according to the actual team contribution.

---

## 📄 License

This project is developed for educational purposes.

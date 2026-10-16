# Gotilo Project Structure & Logic Documentation

This document provides a detailed breakdown of the Gotilo Android application, covering its architecture, components, and core logic.

## 1. Project Overview
**Gotilo** is an Android application designed for campus security and location tracking. It features user registration, login, and a background service that tracks the user's live location and syncs it to a backend server.

---

## 2. Package Structure (`com.gotilo.simtool`)

### 📂 `api` - Networking Layer
Handles all communication with the backend server using Retrofit.

*   **[AppConstants.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/api/AppConstants.java)**
    *   **Role:** Centralized storage for API configuration.
    *   **Variables:**
        *   `BASE_URL`: The root URL for the backend API.
        *   `ENDPOINT_REGISTER`, `ENDPOINT_LOGIN`, `ENDPOINT_LOCATION`: Endpoint paths.
*   **[ApiService.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/api/ApiService.java)**
    *   **Role:** Retrofit interface defining the HTTP requests.
    *   **Methods:**
        *   `registerUser(@Body RegisterRequest)`: POST request for registration.
        *   `loginUser(@Body LoginModel)`: POST request for login.
        *   `saveLocation(@Path("uid"), @Body LocationModel)`: POST request to sync location data.
*   **[RetrofitClient.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/api/RetrofitClient.java)**
    *   **Role:** Singleton provider for the Retrofit instance.
    *   **Methods:**
        *   `getClient()`: Initializes and returns the `Retrofit` instance with Gson converter and Logging interceptor.
*   **[ApiCaller.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/api/ApiCaller.java)**
    *   **Role:** Wrapper class to simplify API calls from Activities and Services.
    *   **Methods:**
        *   `registerUser(...)`, `loginUser(...)`: Enqueue auth requests.
        *   `saveLocation(...)`: Orchestrates the location sync payload.

---

### 📂 `model` - Data Models (POJOs)
Defines the structure of data exchanged with the API.

*   **[RegisterRequest.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/model/RegisterRequest.java)**
    *   **Fields:** `name`, `email`, `password`, `uid`, `message`.
    *   **Usage:** Used for both sending registration data and receiving the server response.
*   **[LoginModel.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/model/LoginModel.java)**
    *   **Fields:** `email`, `password`, `message`, `uid`.
    *   **Usage:** Used for user authentication.
*   **[LocationModel.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/model/LocationModel.java)**
    *   **Fields:** `uid`, `latitude`, `longitude`, `address`, `deviceId`, `alert_status`.
    *   **Usage:** Represents a location ping sent to the server.

---

### 📂 `screen` - UI Layer (Activities)
Manages the user interface and user interactions.

*   **[RegisterActivity.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/screen/RegisterActivity.java)**
    *   **Logic:** Validates input fields (Name, Email, Password) and calls `ApiCaller.registerUser()`. On success, saves UID to `DataPref` and navigates to `DashboardActivity`.
*   **[LoginActivity.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/screen/LoginActivity.java)**
    *   **Logic:** Authenticates existing users via `ApiCaller.loginUser()`. Manages the login session by storing UID.
*   **[DashboardActivity.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/screen/DashboardActivity.java)**
    *   **Logic:**
        *   Main interface after login.
        *   Displays a `WebView` with a live map.
        *   Handles foreground location fetching via `FusedLocationProviderClient`.
        *   Starts/Stops `LocationTrackingService`.
        *   Includes a logout mechanism that clears `DataPref`.

---

### 📂 `utils` - Core Utilities & Background Logic
Handles persistent storage and background operations.

*   **[DataPref.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/utils/DataPref.java)**
    *   **Role:** Wrapper for `SharedPreferences`.
    *   **Logic:** Manages persistent storage for the User's Unique ID (`UID`) and the Device's Unique ID (`deviceId`).
*   **[LocationTrackingService.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/utils/LocationTrackingService.java)**
    *   **Role:** A **Foreground Service** for continuous tracking.
    *   **Logic:**
        *   Starts a high-priority notification to keep the app alive.
        *   Uses `FusedLocationProviderClient` to receive location updates every 10-15 seconds.
        *   Uses `Geocoder` to convert coordinates into readable addresses.
        *   Syncs data to the backend via `ApiCaller.saveLocation()`.
        *   Includes a **24-hour kill switch** to prevent infinite battery drain.
*   **[BootReceiver.java](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/java/com/example/gotilo/utils/BootReceiver.java)**
    *   **Role:** `BroadcastReceiver` that listens for system boot.
    *   **Logic:** Automatically restarts the `LocationTrackingService` when the phone is turned on.

---

## 3. Core Logic Workflows

### 🔄 Authentication Workflow
1.  User enters details in `RegisterActivity`.
2.  `ApiCaller` sends data to the `/register` endpoint.
3.  Server returns a unique `uid`.
4.  `DataPref` saves this `uid` locally.
5.  App redirects to `DashboardActivity`.

### 📍 Location Tracking Workflow (Background)
1.  `DashboardActivity` or `BootReceiver` starts `LocationTrackingService`.
2.  Service calls `startForeground()` to remain active.
3.  Every 15 seconds, GPS provides new Coordinates.
4.  Coordinates are converted to an Address string.
5.  `apiCaller.saveLocation(uid, deviceId, lat, lng, address, ...)` is called.
6.  The backend database is updated with the user's live position.

---

## 4. Manifest & Configuration
*   **[AndroidManifest.xml](file:///C:/Users/MAYUR%20DAVE/AndroidStudioProjects/gotilo2/app/src/main/AndroidManifest.xml)**
    *   Declares critical permissions: `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`.
    *   Registers `LocationTrackingService` with `foregroundServiceType="location"`.
    *   Registers `BootReceiver` with an intent filter for `BOOT_COMPLETED`.
    *   Enables `usesCleartextTraffic="true"` for testing against local IP addresses.

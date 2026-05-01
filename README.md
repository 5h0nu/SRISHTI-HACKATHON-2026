# Team 61 - SRISHTI Hackathon 2026

Welcome to the repository for **Team 61**! Our project consists of an interactive, AI-powered learning mobile application (Edu Bridege AI) and a comprehensive Teacher Dashboard (Edu Bridege AI). This platform aims to bridge the educational gap by offering offline-capable modules, AI-driven conversational learning, and real-time progress monitoring for teachers.

## 📂 Repository Structure

Our repository is organized into three main directories:

- **`andriod app source/`**: Contains the complete Android Studio source code for the GapBridge Android application. It features multi-stage conversational flows, voice-to-text functionality, and seamless synchronization with Firebase.
- **`Apk/`**: Contains the compiled, ready-to-install `.apk` file. **A fully functional demo APK is provided here** so you can easily test the mobile application on any Android device without building it from source.
- **`teacherpanel/`**: Contains the source code for the Edu Bridege AI Teacher Dashboard, a web-based application built to help teachers monitor student progress, review activities, and manage study datasets.

## 🚀 Live Demo & Access

### Teacher Panel (Live Web App)
You can access the live, hosted version of the Teacher Panel here:
**[Teacher Dashboard Link](https://5h0nu.github.io/SRISHTI-HACKATHON-2026/index.html)**

**Test Credentials:**
- **Email:** `teacher@gmail.com`
- **Password:** `12345678`

### Android App
To test the mobile application, simply navigate to the `Apk` folder and download the provided APK file, then install it on your Android device.

## ✨ Features

### 📱 Student Application (Edu Bridege AI)
- **Offline-First Capabilities**: Students can download study modules and access content without an active internet connection for reading the modules.
- **Offline Question Replies**: Students can ask questions and receive answers completely offline based on downloaded datasets.
- **Daily Dataset Syncing**: Whenever the device is online, question datasets are automatically refreshed and updated by the team every day.
- **Doubt Clarification**: Dedicated features for students to ask and clarify their doubts seamlessly.
- **Interactive Quizzes**: AI-driven quiz questions and evaluations tailored to the student's needs.
- **Parent-Friendly Monitoring**: An automated email is sent to parents whenever the student leaves or closes the app, ensuring transparency and monitoring.
- **Image Generation**: Advanced AI image generation capabilities integrated directly into the learning flow.

### 👨‍🏫 Teacher Dashboard (Edu Bridege AI)
- **Student Roster**: View total enrolled students along with a comprehensive student list.
- **Doubt Tracking**: Monitor total doubts asked by students and track how many have been cleared.
- **Advanced Evaluations**: Track total quizzes and questions answered. Grading is categorized into three levels based on the student's answers:
  - 🔴 **Low Level**
  - 🟡 **Medium Level**
  - 🟢 **High Level**
- **Comprehensive Analytics**: 
  - View overarching analytics for all users combined.
  - Drill down into **particular student analytics** to view individual progress.
- **Targeted Student Feedback**: Detailed breakdown of student performance, specifically highlighting mistakes made and areas requiring improvement.

## 🛠 Setup & Installation (For Developers)

### Android App
1. Open Android Studio.
2. Select **Open an existing Android Studio project**.
3. Navigate to and select the `andriod app source` folder.
4. Let Gradle sync and build the project.
5. Run the app on an emulator or a physical device.

### Teacher Panel
The Teacher Panel is already hosted, but if you wish to run it locally:
1. Navigate to the `teacherpanel` directory.
2. If it's a Node project, install dependencies (`npm install`) and start the server (`npm run dev`). Otherwise, simply open `index.html` in your browser.

## 🔐 Configuration (API Keys & Firebase)

### 1. Changing the API Keys (Android App)
To enable the AI capabilities in the mobile app, you must provide your own API keys:
1. Open `andriod app source/app/src/main/java/com/shadow/gapbridge/MainActivity.kt`.
2. Locate the following lines (around line 55):
   ```kotlin
   private val GROQ_API_KEY = "YOUR_GROQ_API_KEY"
   private val STABILITY_API_KEY = "YOUR_STABILITY_API_KEY"
   ```
3. Replace `"YOUR_GROQ_API_KEY"` with your Groq API key and `"YOUR_STABILITY_API_KEY"` with your Stability AI API key.

### 2. Changing Firestore Details (Teacher Panel)
To connect the Teacher Dashboard to your own Firebase project:
1. Open the `teacherpanel/firebase-config.js` file.
2. Locate the `firebaseConfig` object at the top of the file.
3. Replace the placeholder credentials with your Firebase project's configuration details:
   ```javascript
   const firebaseConfig = {
     apiKey: "YOUR_API_KEY",
     authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
     projectId: "YOUR_PROJECT_ID",
     storageBucket: "YOUR_PROJECT_ID.appspot.com",
     messagingSenderId: "YOUR_MESSAGING_SENDER_ID",
     appId: "YOUR_APP_ID"
   };
   ```

## 👥 Contributors
- **Mohammed Khasim G S** 
- **Huzaifa** 
- **Mehek Saba** 
- **Nuthana D R** 

## 📞 Contact
For any inquiries or feedback, please reach out to us at:
**mdkhasimgs@gmail.com**

---
*Built with ❤️ for SRISHTI HACKATHON 2026.*

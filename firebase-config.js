import { initializeApp } from "https://www.gstatic.com/firebasejs/10.8.1/firebase-app.js";
import { getAuth, onAuthStateChanged, signOut } from "https://www.gstatic.com/firebasejs/10.8.1/firebase-auth.js";
import { initializeFirestore, persistentLocalCache, persistentMultipleTabManager } from "https://www.gstatic.com/firebasejs/10.8.1/firebase-firestore.js";
import { getStorage } from "https://www.gstatic.com/firebasejs/10.8.1/firebase-storage.js";

// ==========================================
// FIREBASE CONFIGURATION 
// (Replace with your actual config from Firebase Console)
// ==========================================
export const firebaseConfig = {
  apiKey: "AIzaSyAn0_7ed8UTqTybHBF72CpM5uRRqB_ovEk",
  authDomain: "klarity-6d0c7.firebaseapp.com",
  projectId: "klarity-6d0c7",
  storageBucket: "klarity-6d0c7.firebasestorage.app",
  messagingSenderId: "372117573429",
  appId: "1:372117573429:web:1b3099bfc708ea2bdf067c",
  measurementId: "G-T2FQ377T3P"
};

// Initialize Firebase
export let app, auth, db, storage;
export let isDemoMode = false;

try {
    app = initializeApp(firebaseConfig);
    auth = getAuth(app);
    storage = getStorage(app);
    
    // Enable local caching for instant data loads!
    db = initializeFirestore(app, {
        localCache: persistentLocalCache({tabManager: persistentMultipleTabManager()})
    });
} catch (error) {
    console.error("Firebase init error: ", error);
}

// ==========================================
// SHARED UTILITIES & AUTH STATE
// ==========================================
export function showToast(message, type = 'success') {
    const toast = document.getElementById('toast');
    if (!toast) return;
    toast.textContent = message;
    toast.className = `toast show ${type}`;
    setTimeout(() => { toast.classList.remove('show'); }, 3000);
}

// Global Auth Check for protected pages
const currentPage = window.location.pathname.split('/').pop() || 'index.html';

if (!isDemoMode && auth) {
    onAuthStateChanged(auth, (user) => {
        if (user) {
            // If logged in and on login page, redirect to dashboard
            if (currentPage === 'index.html' || currentPage === '') {
                window.location.href = 'dashboard.html';
            } else {
                // Update email displays on protected pages
                const emailDisplay = document.getElementById('user-email-display');
                if (emailDisplay) emailDisplay.textContent = user.email;
            }
        } else {
            // If not logged in and on a protected page, redirect to login
            if (currentPage !== 'index.html' && currentPage !== '') {
                window.location.href = 'index.html';
            }
        }
    });
} else if (isDemoMode) {
    // In demo mode, simulate session with localStorage
    const demoUser = localStorage.getItem('demo_user');
    if (demoUser) {
        if (currentPage === 'index.html' || currentPage === '') {
            window.location.href = 'dashboard.html';
        } else {
            const emailDisplay = document.getElementById('user-email-display');
            if (emailDisplay) emailDisplay.textContent = demoUser;
        }
    } else {
        if (currentPage !== 'index.html' && currentPage !== '') {
            window.location.href = 'index.html';
        }
    }
}

// Handle Logout globally if button exists
const logoutBtn = document.getElementById('logout-btn');
if (logoutBtn) {
    logoutBtn.addEventListener('click', () => {
        if (isDemoMode) {
            localStorage.removeItem('demo_user');
            showToast('Logged out successfully');
            setTimeout(() => { window.location.href = 'index.html'; }, 500);
        } else {
            signOut(auth).then(() => {
                showToast('Logged out successfully');
                setTimeout(() => { window.location.href = 'index.html'; }, 500);
            }).catch((error) => {
                showToast('Error logging out', 'error');
            });
        }
    });
}

// ==========================================
// MOCK DATA (For Demo Mode)
// ==========================================
export const mockStudents = [
    { id: 's1', email: 'john.doe@student.com', name: 'John Doe' },
    { id: 's2', email: 'jane.smith@student.com', name: 'Jane Smith' },
    { id: 's3', email: 'mike.ross@student.com', name: 'Mike Ross' },
    { id: 's4', email: 'sarah.connor@student.com', name: 'Sarah Connor' },
    { id: 's5', email: 'bruce.wayne@student.com', name: 'Bruce Wayne' }
];

export const mockDoubts = [
    { id: 'd1', studentId: 's1', studentEmail: 'john.doe@student.com', question: 'Can you explain React Hooks lifecycle?', status: 'cleared', date: '2023-10-25' },
    { id: 'd2', studentId: 's2', studentEmail: 'jane.smith@student.com', question: 'How do I center a div in CSS?', status: 'cleared', date: '2023-10-26' },
    { id: 'd3', studentId: 's1', studentEmail: 'john.doe@student.com', question: 'What is the difference between let and var?', status: 'pending', date: '2023-10-27' },
    { id: 'd4', studentId: 's3', studentEmail: 'mike.ross@student.com', question: 'How does Firebase Auth work under the hood?', status: 'cleared', date: '2023-10-27' },
    { id: 'd5', studentId: 's4', studentEmail: 'sarah.connor@student.com', question: 'Why is my API call failing with CORS error?', status: 'pending', date: '2023-10-28' },
    { id: 'd6', studentId: 's2', studentEmail: 'jane.smith@student.com', question: 'Can we use Tailwind with this setup?', status: 'pending', date: '2023-10-28' },
    { id: 'd7', studentId: 's5', studentEmail: 'bruce.wayne@student.com', question: 'How to implement glassmorphism?', status: 'cleared', date: '2023-10-29' }
];

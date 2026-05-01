import { auth, isDemoMode, showToast } from './firebase-config.js';
import { signInWithEmailAndPassword } from "https://www.gstatic.com/firebasejs/10.8.1/firebase-auth.js";

const loginForm = document.getElementById('login-form');
const loginBtn = document.getElementById('login-btn');

if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('email').value;
        const password = document.getElementById('password').value;
        
        const btnText = loginBtn.querySelector('.btn-text');
        const spinner = loginBtn.querySelector('.spinner');
        
        btnText.classList.add('hidden');
        spinner.classList.remove('hidden');

        try {
            if (isDemoMode) {
                // Simulate network delay for demo
                await new Promise(r => setTimeout(r, 1000));
                if (email === 'teacher@demo.com' && password === '123456') {
                    localStorage.setItem('demo_user', email);
                    showToast('Login successful!');
                    window.location.href = 'dashboard.html';
                } else {
                    throw new Error("Invalid demo credentials. Use teacher@demo.com / 123456");
                }
            } else {
                await signInWithEmailAndPassword(auth, email, password);
                showToast('Login successful!');
                // firebase-config onAuthStateChanged will handle redirection
            }
        } catch (error) {
            showToast(error.message || "Failed to login", 'error');
            btnText.classList.remove('hidden');
            spinner.classList.add('hidden');
        }
    });
}

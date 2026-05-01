package com.shadow.gapbridge

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)

        // Redirect if already logged in
        if (authManager.isUserLoggedIn()) {
            startMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        // UI Binding
        val tvTitle = findViewById<TextView>(R.id.tvSignInTitle)
        val tvWelcome = findViewById<TextView>(R.id.tvWelcomeNote)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPass = findViewById<EditText>(R.id.etPassword)
        val btnMain = findViewById<Button>(R.id.btnLogin)
        val btnToggle = findViewById<TextView>(R.id.btnSignup)

        updateNetworkStatusUI()

        // Toggle UI between Login and Signup with Alpha Animation
        btnToggle.setOnClickListener {
            tvTitle.animate().alpha(0f).setDuration(150).withEndAction {
                if (isLoginMode) {
                    tvTitle.text = "Sign up"
                    tvWelcome.text = "Join gapBridge"
                    btnMain.text = "Create Account"
                    btnToggle.text = "Already have an account? Sign in"
                    isLoginMode = false
                } else {
                    tvTitle.text = "Sign in"
                    tvWelcome.text = "Welcome Back"
                    btnMain.text = "Sign In"
                    btnToggle.text = "Don't have an account? Sign up"
                    isLoginMode = true
                }
                tvTitle.animate().alpha(1f).setDuration(150).start()
            }.start()
        }

        // Main Action Button
        btnMain.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString().trim()

            updateNetworkStatusUI()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                authManager.handleAccess(email, pass) { success, message ->
                    runOnUiThread {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        if (success) startMainActivity()
                    }
                }
            } else {
                Toast.makeText(this, "Please fill all details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateNetworkStatusUI() {
        val tvStatus = findViewById<TextView>(R.id.tvNetworkStatus) ?: return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        val isOnline = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        if (isOnline) {
            tvStatus.text = "● Online Mode"
            tvStatus.setTextColor(Color.parseColor("#005D67"))
        } else {
            tvStatus.text = "✔ Offline Mode Enabled"
            tvStatus.setTextColor(Color.parseColor("#2E7D32"))
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
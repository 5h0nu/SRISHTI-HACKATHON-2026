package com.shadow.gapbridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit // Added for the KTX edit fix
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.JsonObject
import android.widget.Toast

class AuthManager(private val context: Context) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private val sharedPrefs = context.getSharedPreferences("LocalAuth", Context.MODE_PRIVATE)

    init {
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        db.firestoreSettings = settings
    }

    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun handleAccess(email: String, pass: String, onResult: (Boolean, String) -> Unit) {
        if (isOnline()) {
            auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    saveLocalSession(email, pass)
                    onResult(true, "Online Login Successful")
                } else {
                    auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { regTask ->
                        if (regTask.isSuccessful) {
                            saveLocalSession(email, pass)
                            sendWelcomeEmail(email)
                            onResult(true, "Online Account Created")
                        } else {
                            onResult(false, regTask.exception?.localizedMessage ?: "Registration Failed")
                        }
                    }
                }
            }
        } else {
            val savedEmail = sharedPrefs.getString("user_email", null)
            val savedPass = sharedPrefs.getString("user_pass", null)
            // Replaced cascade IF with WHEN as suggested by your IDE
            when {
                savedEmail == null -> {
                    saveLocalSession(email, pass)
                    onResult(true, "Offline Signup Successful")
                }
                savedEmail == email -> {
                    if (savedPass == null || savedPass == pass) {
                        if (savedPass == null) saveLocalSession(email, pass)
                        onResult(true, "Offline Login Successful")
                    } else {
                        onResult(false, "Incorrect offline password.")
                    }
                }
                else -> {
                    onResult(false, "Device registered to another user.")
                }
            }
        }
    }

    // This private function was likely missing or misspelled
    private fun saveLocalSession(email: String, pass: String? = null) {
        // Using KTX extension as suggested by your IDE warning
        sharedPrefs.edit(commit = true) {
            putString("user_email", email)
            if (pass != null) putString("user_pass", pass)
            putBoolean("is_logged_in", true)
        }

        val profile = hashMapOf(
            "email" to email,
            "lastLogin" to Date(),
            "type" to "OfflineUser"
        )
        db.collection("users").document(email.replace(".", "_")).set(profile)
    }

    fun uploadAnalytics(topic: String, studentInput: String, aiResponse: String) {
        val userEmail = sharedPrefs.getString("user_email", "anonymous")
        val sessionData = hashMapOf(
            "studentId" to userEmail,
            "topic" to topic,
            "studentInput" to studentInput,
            "aiAnalysis" to aiResponse,
            "timestamp" to Date()
        )

        db.collection("student_logs").add(sessionData)
            .addOnSuccessListener {
                android.util.Log.d("FirestoreSync", "Data successfully reached the cloud!")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FirestoreSync", "Cloud rejected data: ${e.message}")
            }
    }

    fun uploadEvaluation(topic: String, answer: String, level: String, feedback: String) {
        val userEmail = sharedPrefs.getString("user_email", "anonymous")
        val evaluationData = hashMapOf(
            "studentId" to userEmail,
            "topic" to topic,
            "answer" to answer,
            "understandingLevel" to level,
            "feedback" to feedback,
            "timestamp" to Date()
        )

        db.collection("student_evaluations").add(evaluationData)
            .addOnSuccessListener {
                android.util.Log.d("FirestoreSync", "Evaluation successfully reached the cloud!")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FirestoreSync", "Cloud rejected evaluation: ${e.message}")
            }
    }

    fun getUserHistory(onResult: (List<Map<String, String>>) -> Unit) {
        val userEmail = sharedPrefs.getString("user_email", null)
        if (userEmail == null) {
            onResult(emptyList())
            return
        }

        db.collection("student_evaluations")
            .whereEqualTo("studentId", userEmail)
            .get()
            .addOnSuccessListener { documents ->
                val historyList = documents.mapNotNull { doc ->
                    val topic = doc.getString("topic") ?: "Unknown"
                    val level = doc.getString("understandingLevel") ?: "Unknown"
                    val dateObj = doc.getDate("timestamp")
                    val dateStr = dateObj?.toString()?.take(16) ?: ""
                    
                    mapOf(
                        "topic" to topic,
                        "level" to level,
                        "date" to dateStr
                    )
                }
                onResult(historyList.reversed())
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    fun getSyncStatus(onStatusUpdate: (String) -> Unit) {
        val userEmail = sharedPrefs.getString("user_email", null) ?: return

        // We listen specifically for changes in the 'student_logs' for this user
        db.collection("student_logs")
            .whereEqualTo("studentId", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onStatusUpdate("Sync Error")
                    return@addSnapshotListener
                }

                // hasPendingWrites is TRUE if the data is only on the phone
                // hasPendingWrites is FALSE if the server has confirmed receipt
                val isPending = snapshot?.metadata?.hasPendingWrites() ?: false
                val isFromCache = snapshot?.metadata?.isFromCache ?: false

                if (isPending) {
                    onStatusUpdate("Offline (Saving Locally...)")
                } else if (!isFromCache) {
                    onStatusUpdate("Online (Synced)")
                } else {
                    // If it's from cache but not pending, it's in a middle state
                    onStatusUpdate("Online (Checking Cloud...)")
                }
            }
    }

    fun isUserLoggedIn(): Boolean {
        return sharedPrefs.getBoolean("is_logged_in", false)
    }

    fun logout() {
        auth.signOut()
        sharedPrefs.edit {
            remove("is_logged_in")
            remove("user_email")
        }
    }

    private fun sendWelcomeEmail(userEmail: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val emailJsUrl = "https://api.emailjs.com/api/v1.0/email/send"
            val serviceId = "service_jpg89h8"
            val templateId = "template_ngf4wq6"
            val publicKey = "_SVHHAzQba6VERIL0"

            val json = JsonObject().apply {
                addProperty("service_id", serviceId)
                addProperty("template_id", templateId)
                addProperty("user_id", publicKey)
                add("template_params", JsonObject().apply {
                    addProperty("to_email", userEmail)
                    addProperty("email", userEmail)
                    addProperty("message", "Welcome to GapBridge! We are excited to have you on board. Start exploring concepts with AI today!")
                })
            }

            val request = Request.Builder()
                .url(emailJsUrl)
                .addHeader("origin", "http://localhost")
                .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:102.0) Gecko/102.0 Firefox/102.0")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = OkHttpClient().newCall(request).execute()
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Welcome email failed: ${response.code} - $responseBody", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Welcome email sent successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Email Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
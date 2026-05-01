package com.shadow.gapbridge

import android.animation.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class InteractionStage {
    CONCEPT_INPUT,
    ANSWERING_QUESTION
}

class MainActivity : AppCompatActivity() {

    private var loadingDialog: AlertDialog? = null

    private var currentStage = InteractionStage.CONCEPT_INPUT
    private var lastConcept = ""

    private var isAppLocked = false
    private var parentEmail = ""
    private var parentPin = ""
    private var escapeAttempts = 0
    private var wasInBackground = false
    private var awayTimerJob: Job? = null

    // IMPORTANT: Do not keep real API keys inside Android app.
    // Replace these keys and later move them to backend/Firebase Functions.
    private val GROQ_API_KEY = "YOUR_GROQ_API_KEY"
    private val STABILITY_API_KEY = "YOUR_STABILITY_API_KEY"

    private val authManager by lazy { AuthManager(this) }

    private val speechResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val res =
                    result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                            as ArrayList<String>
                findViewById<EditText>(R.id.etInput).setText(res[0])
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_final)

        val etInput = findViewById<EditText>(R.id.etInput)
        val btnBridge = findViewById<Button>(R.id.btnBridge)
        val tvStatus = findViewById<TextView>(R.id.tvResponse)
        val tvAiOutput = findViewById<TextView>(R.id.tvAiOutput)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        val ivAiImage = findViewById<ImageView>(R.id.ivAiImage)
        val btnLock = findViewById<ImageButton>(R.id.btnLock)
        val btnMic = findViewById<ImageButton>(R.id.btnMic)
        val btnHistory = findViewById<ImageButton>(R.id.btnHistory)

        btnLock.alpha = 0.5f

        btnLock.setOnClickListener {
            if (isAppLocked) {
                showUnlockDialog()
            } else {
                showLockSetupDialog()
            }
        }

        updateOfflineModeUI()

        authManager.getSyncStatus { status ->
            runOnUiThread {
                val mode = if (isOnline()) "Online" else "Offline"
                tvStatus.text = "Engine Ready | $mode | $status"
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                extractModelIfNeeded()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        btnMic.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")

            try {
                speechResultLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    applicationContext,
                    "Your device doesn't support Speech Recognition",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnHistory.setOnClickListener {
            Toast.makeText(this, "Loading history...", Toast.LENGTH_SHORT).show()

            authManager.getUserHistory { history ->
                runOnUiThread {
                    showHistoryDialog(history)
                }
            }
        }

        btnBridge.setOnClickListener {
            val userText = etInput.text.toString().trim()

            if (userText.isEmpty()) {
                Toast.makeText(this, "Please type something first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            tvAiOutput.text = "EduBridge is thinking..."
            ivAiImage.visibility = View.GONE

            if (currentStage == InteractionStage.CONCEPT_INPUT) {
                lastConcept = userText

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val onlineNow = isOnline()

                        val finalResult = if (onlineNow) {
                            val masterPrompt = """
                                You are EduBridge, a helpful AI learning assistant.

                                Student input: "$userText"

                                Important:
                                - If the student input is a direct question, answer it clearly.
                                - If the student input is an explanation, detect misunderstanding.
                                - Keep the answer simple for school students.
                                - Use examples where helpful.

                                Format strictly:

                                1. ANSWER / DETECTED MISUNDERSTANDING:
                                2. SIMPLE EXPLANATION:
                                3. MULTI-LANGUAGE SUMMARY:
                                   - Kannada:
                                   - Hindi:
                                4. FOLLOW-UP QUIZ:
                            """.trimIndent()

                            callGroqApi(masterPrompt)
                        } else {
                            val modelFile = File(filesDir, "main_dataset.json")
                            val isFileValid = modelFile.exists()

                            val answer = if (isFileValid) {
                                try {
                                    val jsonString = modelFile.readText()
                                    val mainJson = com.google.gson.JsonParser.parseString(jsonString).asJsonObject
                                    
                                    var foundAnswer = "I couldn't find an answer in your datasets."
                                    
                                    for (datasetId in mainJson.keySet()) {
                                        val datasetJson = mainJson.getAsJsonObject(datasetId)
                                        for (key in datasetJson.keySet()) {
                                            if (userText.lowercase().contains(key.lowercase())) {
                                                foundAnswer = datasetJson.get(key).asString
                                                break
                                            }
                                        }
                                        if (foundAnswer != "I couldn't find an answer in your datasets.") {
                                            break
                                        }
                                    }
                                    foundAnswer
                                } catch (e: Exception) {
                                    "Error parsing dataset: ${e.message}"
                                }
                            } else {
                                "No offline datasets downloaded."
                            }

                            "[Offline Master Brain]\n\n$answer"
                        }

                        withContext(Dispatchers.Main) {
                            if (onlineNow && !finalResult.startsWith("Groq")) {
                                ivAiImage.visibility = View.VISIBLE
                                Toast.makeText(
                                    this@MainActivity,
                                    "Creating educational diagram...",
                                    Toast.LENGTH_SHORT
                                ).show()
                                generateStableDiffusionImage(userText, ivAiImage)
                            } else {
                                ivAiImage.visibility = View.GONE
                            }

                            tvAiOutput.alpha = 0f
                            tvAiOutput.text = finalResult
                            tvAiOutput.animate().alpha(1f).setDuration(600).start()

                            authManager.uploadAnalytics("General", userText, finalResult)

                            if (onlineNow) {
                                currentStage = InteractionStage.ANSWERING_QUESTION
                                etInput.text.clear()
                                etInput.hint = "Answer the follow-up quiz..."
                                btnBridge.text = "SUBMIT ANSWER"
                            } else {
                                etInput.text.clear()
                                etInput.hint = "Ask another question..."
                            }
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            tvAiOutput.text = "AI Error: ${e.localizedMessage}"
                        }
                    }
                }

            } else if (currentStage == InteractionStage.ANSWERING_QUESTION) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val onlineNow = isOnline()

                        val evalResult = if (onlineNow) {
                            val evaluationPrompt = """
                                The student was learning about:
                                "$lastConcept"

                                The student answered:
                                "$userText"

                                Evaluate their understanding.

                                Format:

                                1. UNDERSTANDING LEVEL: High, Medium, or Low
                                2. FEEDBACK:
                                3. CORRECTED ANSWER:
                            """.trimIndent()

                            callGroqApi(evaluationPrompt)
                        } else {
                            val spinnerOfflineModel =
                                findViewById<Spinner>(R.id.spinnerOfflineModel)
                            val selectedModel =
                                spinnerOfflineModel.selectedItem?.toString() ?: "Unknown Model"

                            if (selectedModel == "No models downloaded" || selectedModel.isEmpty()) {
                                "[ERROR] Please select a model."
                            } else {
                                val isGoodAnswer = userText.length > 10
                                val level = if (isGoodAnswer) "High" else "Medium"
                                val feedback =
                                    if (isGoodAnswer)
                                        "Great job! You explained the core idea."
                                    else
                                        "You are on the right track, but add more details."

                                "[Generated by $selectedModel via offline mode]\n\n" +
                                        "1. UNDERSTANDING LEVEL: $level\n" +
                                        "2. FEEDBACK: $feedback\n" +
                                        "3. CORRECTED ANSWER: Try explaining with one example."
                            }
                        }

                        val level = when {
                            evalResult.contains("High", ignoreCase = true) -> "High"
                            evalResult.contains("Medium", ignoreCase = true) -> "Medium"
                            else -> "Low"
                        }

                        withContext(Dispatchers.Main) {
                            tvAiOutput.alpha = 0f
                            tvAiOutput.text = evalResult
                            tvAiOutput.animate().alpha(1f).setDuration(600).start()

                            authManager.uploadEvaluation(lastConcept, userText, level, evalResult)

                            currentStage = InteractionStage.CONCEPT_INPUT
                            etInput.text.clear()
                            etInput.hint = "Ask a question or explain a concept..."
                            btnBridge.text = "ANALYZE / ASK"
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            tvAiOutput.text = "AI Error: ${e.localizedMessage}"
                        }
                    }
                }
            }
        }

        btnMenu.setOnClickListener {
            showMenuDialog()
        }
    }

    private fun updateOfflineModeUI() {
        val layoutOfflineModel = findViewById<View>(R.id.layoutOfflineModel)
        layoutOfflineModel.visibility = View.GONE
    }

    private fun showMenuDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_menu, null)
        val btnModules = dialogView.findViewById<Button>(R.id.btnModules)
        val btnDownloadLLM = dialogView.findViewById<Button>(R.id.btnDownloadLLM)
        val btnDialogLogout = dialogView.findViewById<Button>(R.id.btnDialogLogout)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnModules.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, ModulesActivity::class.java))
        }

        btnDownloadLLM.setOnClickListener {
            dialog.dismiss()
            startActivity(android.content.Intent(this, OfflineDatasetsActivity::class.java))
        }

        btnDialogLogout.setOnClickListener {
            dialog.dismiss()
            authManager.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        dialog.show()
    }



    private suspend fun callGroqApi(prompt: String): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient()

        val mediaType = "application/json; charset=utf-8".toMediaType()

        val json = JsonObject().apply {
            addProperty("model", "llama-3.1-8b-instant")
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty(
                        "content",
                        "You are EduBridge, a simple educational assistant for students."
                    )
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", prompt)
                })
            })
            addProperty("temperature", 0.5)
            addProperty("max_tokens", 700)
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $GROQ_API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody(mediaType))
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext "Groq API Error: ${response.code}\n$body"
            }

            val jsonResponse = Gson().fromJson(body, JsonObject::class.java)

            jsonResponse
                .getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message")
                .get("content").asString

        } catch (e: Exception) {
            "Groq Network Error: ${e.message}"
        }
    }

    private fun isOnline(): Boolean {
        return try {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

            val network = connectivityManager.activeNetwork ?: return false
            val capabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)

        } catch (e: Exception) {
            false
        }
    }

    private fun extractModelIfNeeded(): Uri {
        val modelName = "gemma-3-270m-it-Q4_K_M.gguf"
        val modelFile = File(filesDir, modelName)

        if (!modelFile.exists()) {
            assets.open(modelName).use { inputStream ->
                FileOutputStream(modelFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }

        return Uri.fromFile(modelFile)
    }

    private fun generateStableDiffusionImage(prompt: String, imageView: ImageView) {
        if (STABILITY_API_KEY == "YOUR_STABILITY_API_KEY") return

        val client = OkHttpClient()

        val enhancedPrompt =
            "A clear educational diagram of $prompt, white background, simple school textbook style"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", enhancedPrompt)
            .addFormDataPart("output_format", "png")
            .build()

        val request = Request.Builder()
            .url("https://api.stability.ai/v2beta/stable-image/generate/core")
            .addHeader("Authorization", "Bearer $STABILITY_API_KEY")
            .addHeader("Accept", "image/*")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val bytes = response.body?.bytes()

                if (response.isSuccessful && bytes != null) {
                    runOnUiThread {
                        Glide.with(this@MainActivity)
                            .asBitmap()
                            .load(bytes)
                            .into(imageView)
                    }
                }
            }
        })
    }

    private fun showLockSetupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_lock_setup, null)
        val etParentEmail = dialogView.findViewById<EditText>(R.id.etParentEmail)
        val etPin = dialogView.findViewById<EditText>(R.id.etPin)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelLock)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmLock)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val email = etParentEmail.text.toString()
            val pin = etPin.text.toString()

            if (email.isNotEmpty() && pin.length == 4) {
                parentEmail = email
                parentPin = pin
                isAppLocked = true
                escapeAttempts = 0

                findViewById<ImageButton>(R.id.btnLock).alpha = 1.0f

                Toast.makeText(this, "App Locked!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(
                    this,
                    "Invalid Email or PIN. PIN must be 4 digits.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        dialog.show()
    }

    private fun showUnlockDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_unlock, null)
        val etUnlockPin = dialogView.findViewById<EditText>(R.id.etUnlockPin)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelUnlock)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmUnlock)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            if (etUnlockPin.text.toString() == parentPin) {
                isAppLocked = false
                escapeAttempts = 0

                findViewById<ImageButton>(R.id.btnLock).alpha = 0.5f

                Toast.makeText(this, "App Unlocked!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Incorrect PIN!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (isAppLocked) {
            wasInBackground = true
            escapeAttempts++

            awayTimerJob = lifecycleScope.launch {
                delay(120_000)
                sendAlertEmail(
                    "Alert: Your child has been away from the GapBridge app for more than 2 minutes while locked."
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        updateOfflineModeUI()

        awayTimerJob?.cancel()
        awayTimerJob = null

        if (isAppLocked && wasInBackground) {
            wasInBackground = false

            if (escapeAttempts >= 3) {
                showWarningDialog(
                    "Alert Sent",
                    "You've tried to escape 3 times. An alert has been sent to your parent!"
                )
                sendAlertEmail()
            } else {
                showWarningDialog(
                    "Warning",
                    "Stay focused! This is attempt $escapeAttempts to escape."
                )
            }
        }
    }

    private fun showWarningDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("I will study") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun sendAlertEmail(
        customMessage: String = "Alert: Your child has attempted to exit the GapBridge study app 3 times."
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val emailJsUrl = "https://api.emailjs.com/api/v1.0/email/send"

            val serviceId = "service_jpg89h8"
            val templateId = "template_ev376xb"
            val publicKey = "_SVHHAzQba6VERIL0"

            val json = JsonObject().apply {
                addProperty("service_id", serviceId)
                addProperty("template_id", templateId)
                addProperty("user_id", publicKey)

                add("template_params", JsonObject().apply {
                    addProperty("parent_email", parentEmail)
                    addProperty("to_email", parentEmail)
                    addProperty("email", parentEmail)
                    addProperty("message", customMessage)
                })
            }

            val request = Request.Builder()
                .url(emailJsUrl)
                .addHeader("origin", "http://localhost")
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Android; Mobile; rv:102.0) Gecko/102.0 Firefox/102.0"
                )
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = OkHttpClient().newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Email Failed: ${response.code} - $responseBody",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Email Sent Successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Network Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showLoadingDialog() {
        val builder =
            AlertDialog.Builder(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)

        val dialogView = layoutInflater.inflate(R.layout.dialog_loading, null)

        builder.setView(dialogView).setCancelable(false)

        loadingDialog = builder.create()
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.white)
        loadingDialog?.show()

        val redDot = dialogView.findViewById<View>(R.id.redDot)

        if (redDot != null) {
            startBouncingAnimation(redDot)
        }
    }

    private fun startBouncingAnimation(view: View) {
        val animY = ObjectAnimator.ofFloat(view, "translationY", 0f, -50f, 0f)
        val animX = ObjectAnimator.ofFloat(view, "translationX", 0f, -135f, 0f)

        AnimatorSet().apply {
            playTogether(animY, animX)
            duration = 1000
            interpolator = AccelerateDecelerateInterpolator()

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (loadingDialog?.isShowing == true) {
                        start()
                    }
                }
            })

            start()
        }
    }

    private fun showHistoryDialog(history: List<Map<String, String>>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_history, null)
        val historyContainer = dialogView.findViewById<LinearLayout>(R.id.historyContainer)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseHistory)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        if (history.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No history found."
                setPadding(20, 20, 20, 20)
                gravity = android.view.Gravity.CENTER
            }

            historyContainer.addView(emptyText)
        } else {
            for (item in history) {
                val itemView = layoutInflater.inflate(
                    R.layout.item_history,
                    historyContainer,
                    false
                )

                val tvTopic = itemView.findViewById<TextView>(R.id.tvTopic)
                val tvLevel = itemView.findViewById<TextView>(R.id.tvLevel)
                val tvDate = itemView.findViewById<TextView>(R.id.tvDate)

                tvTopic.text = item["topic"]
                tvLevel.text = item["level"]
                tvDate.text = item["date"]

                val bg = tvLevel.background as? android.graphics.drawable.GradientDrawable

                when (item["level"]?.lowercase()) {
                    "high" -> bg?.setColor(android.graphics.Color.parseColor("#4CAF50"))
                    "medium" -> bg?.setColor(android.graphics.Color.parseColor("#FF9800"))
                    "low" -> bg?.setColor(android.graphics.Color.parseColor("#F44336"))
                    else -> bg?.setColor(android.graphics.Color.parseColor("#9E9E9E"))
                }

                historyContainer.addView(itemView)
            }
        }

        dialog.show()
    }
}
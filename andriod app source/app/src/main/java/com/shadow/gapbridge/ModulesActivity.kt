package com.shadow.gapbridge

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class ModulesActivity : AppCompatActivity() {

    private lateinit var rvModules: RecyclerView
    private lateinit var emptyLayout: View
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNetworkStatus: TextView
    private lateinit var adapter: ModuleAdapter

    private val db = Firebase.firestore
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_modules)

        rvModules = findViewById(R.id.rvModules)
        emptyLayout = findViewById(R.id.emptyLayout)
        tvEmpty = findViewById(R.id.tvEmpty)
        progressBar = findViewById(R.id.progressBar)
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rvModules.layoutManager = LinearLayoutManager(this)
        adapter = ModuleAdapter(emptyList(),
            onItemClick = { module ->
                if (module.isDownloaded && module.localPath != null) {
                    openPdf(File(module.localPath!!))
                } else if (module.url.isNotEmpty()) {
                    openOnlinePdf(module.url)
                } else {
                    Toast.makeText(this, "Module not available.", Toast.LENGTH_SHORT).show()
                }
            },
            onDownloadClick = { module ->
                downloadPdf(module)
            }
        )
        rvModules.adapter = adapter

        loadModules()
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadModules() {
        val online = isOnline()
        if (online) {
            tvNetworkStatus.text = "Online Mode"
            tvNetworkStatus.setTextColor(android.graphics.Color.parseColor("#A5D6A7")) // Green
            fetchOnlineModules()
        } else {
            tvNetworkStatus.text = "Offline Mode"
            tvNetworkStatus.setTextColor(android.graphics.Color.parseColor("#EF5350")) // Red
            fetchOfflineModules()
        }
    }

    private fun fetchOnlineModules() {
        progressBar.visibility = View.VISIBLE
        emptyLayout.visibility = View.GONE
        
        db.collection("module").get()
            .addOnSuccessListener { result ->
                val modules = mutableListOf<Module>()
                val localDir = File(filesDir, "modules")
                if (!localDir.exists()) localDir.mkdirs()

                for (document in result) {
                    val id = document.id
                    val title = document.getString("title") ?: "Unknown Module"
                    val url = document.getString("pdf") ?: ""
                    
                    val safeTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                    val localFile = File(localDir, "$safeTitle.pdf")
                    val isDownloaded = localFile.exists()
                    val localPath = if (isDownloaded) localFile.absolutePath else null
                    
                    modules.add(Module(id, title, url, isDownloaded, localPath))
                }
                progressBar.visibility = View.GONE
                updateUI(modules)
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load modules from server.", Toast.LENGTH_SHORT).show()
                fetchOfflineModules() // Fallback to offline
            }
    }

    private fun fetchOfflineModules() {
        progressBar.visibility = View.GONE
        val modules = mutableListOf<Module>()
        val localDir = File(filesDir, "modules")
        if (localDir.exists()) {
            val files = localDir.listFiles()
            files?.forEach { file ->
                if (file.extension == "pdf") {
                    val title = file.nameWithoutExtension.replace("_", " ")
                    modules.add(Module(file.name, title, "", true, file.absolutePath))
                }
            }
        }
        updateUI(modules)
    }

    private fun updateUI(modules: List<Module>) {
        if (modules.isEmpty()) {
            emptyLayout.visibility = View.VISIBLE
            rvModules.visibility = View.GONE
        } else {
            emptyLayout.visibility = View.GONE
            rvModules.visibility = View.VISIBLE
            adapter.updateData(modules)
        }
    }

    private fun downloadPdf(module: Module) {
        if (!isOnline()) {
            Toast.makeText(this, "You need internet to download this module.", Toast.LENGTH_SHORT).show()
            return
        }

        if (module.url.isEmpty()) {
            Toast.makeText(this, "Invalid PDF URL.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Downloading ${module.title}...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(module.url).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val localDir = File(filesDir, "modules")
                    if (!localDir.exists()) localDir.mkdirs()

                    val safeTitle = module.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                    val localFile = File(localDir, "$safeTitle.pdf")
                    val fos = FileOutputStream(localFile)
                    fos.use { output ->
                        response.body?.byteStream()?.copyTo(output)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ModulesActivity, "Download complete!", Toast.LENGTH_SHORT).show()
                        loadModules() // Refresh the list
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ModulesActivity, "Download failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ModulesActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openPdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/pdf")
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY
            // This implicitly creates a chooser or opens the default app
            startActivity(Intent.createChooser(intent, "Open PDF with..."))
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open PDF.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOnlinePdf(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse(url), "application/pdf")
            intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
            startActivity(Intent.createChooser(intent, "Open PDF with..."))
        } catch (e: Exception) {
            // Fallback to browser if no PDF viewer handles the intent
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            } catch (e2: Exception) {
                Toast.makeText(this, "No app found to open this link.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

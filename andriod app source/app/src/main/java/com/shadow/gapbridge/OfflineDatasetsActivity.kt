package com.shadow.gapbridge

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File

class OfflineDatasetsActivity : AppCompatActivity() {

    private lateinit var rvDatasets: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: OfflineDatasetAdapter
    
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_datasets)

        rvDatasets = findViewById(R.id.rvDatasets)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rvDatasets.layoutManager = LinearLayoutManager(this)
        adapter = OfflineDatasetAdapter(
            emptyList(),
            onDownloadClick = { dataset -> downloadDataset(dataset) },
            onDeleteClick = { dataset -> deleteDataset(dataset) }
        )
        rvDatasets.adapter = adapter

        findViewById<Button>(R.id.btnViewMasterDataset).setOnClickListener {
            showMasterDatasetDialog()
        }

        fetchDatasets()
    }

    private fun getDownloadedDatasetIds(): MutableSet<String> {
        val prefs = getSharedPreferences("gapbridge_datasets", Context.MODE_PRIVATE)
        return prefs.getStringSet("downloaded_ids", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    }

    private fun addDownloadedDatasetId(id: String) {
        val prefs = getSharedPreferences("gapbridge_datasets", Context.MODE_PRIVATE)
        val set = getDownloadedDatasetIds()
        set.add(id)
        prefs.edit().putStringSet("downloaded_ids", set).apply()
    }

    private fun removeDownloadedDatasetId(id: String) {
        val prefs = getSharedPreferences("gapbridge_datasets", Context.MODE_PRIVATE)
        val set = getDownloadedDatasetIds()
        set.remove(id)
        prefs.edit().putStringSet("downloaded_ids", set).apply()
    }

    private fun fetchDatasets() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        
        db.collection("datasets").get()
            .addOnSuccessListener { result ->
                val datasets = mutableListOf<OfflineDataset>()
                val downloadedIds = getDownloadedDatasetIds()

                for (document in result) {
                    val id = document.id
                    val title = document.getString("title") ?: "Unknown Dataset"
                    val isDownloaded = downloadedIds.contains(id)
                    
                    datasets.add(OfflineDataset(id, title, isDownloaded, if(isDownloaded) "Merged in Main Dataset" else null))
                }
                
                progressBar.visibility = View.GONE
                if (datasets.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    tvEmpty.visibility = View.GONE
                    adapter.updateData(datasets)
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load datasets from Firebase.", Toast.LENGTH_SHORT).show()
                // In a real app, we'd fallback to showing the list of downloaded IDs if offline
            }
    }

    private fun downloadDataset(dataset: OfflineDataset) {
        Toast.makeText(this, "Downloading & Merging ${dataset.title}...", Toast.LENGTH_SHORT).show()
        progressBar.visibility = View.VISIBLE
        
        db.collection("datasets").document(dataset.id).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val dataMap = document.get("data") as? Map<String, String> ?: emptyMap()
                            
                            val newDatasetJson = JsonObject()
                            for ((key, value) in dataMap) {
                                val cleanKey = key.replace("^\"|\"$|^\\\\\"|\\\\\"$".toRegex(), "")
                                    .trim('"', ' ', ',', '\n', '\\')
                                val cleanValue = value.replace("^\"|\"$|^\\\\\"|\\\\\"$".toRegex(), "")
                                    .trim('"', ' ', ',', '\n', '\\')
                                newDatasetJson.addProperty(cleanKey, cleanValue)
                            }
                            
                            val mainFile = File(filesDir, "main_dataset.json")
                            val mainJson = if (mainFile.exists()) {
                                JsonParser.parseString(mainFile.readText()).asJsonObject
                            } else {
                                JsonObject()
                            }
                            
                            mainJson.add(dataset.id, newDatasetJson)
                            mainFile.writeText(com.google.gson.Gson().toJson(mainJson))

                            addDownloadedDatasetId(dataset.id)

                            withContext(Dispatchers.Main) {
                                progressBar.visibility = View.GONE
                                Toast.makeText(this@OfflineDatasetsActivity, "Dataset Merged!", Toast.LENGTH_SHORT).show()
                                fetchDatasets()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                progressBar.visibility = View.GONE
                                Toast.makeText(this@OfflineDatasetsActivity, "Error merging: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Document does not exist", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to download dataset.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteDataset(dataset: OfflineDataset) {
        try {
            val mainFile = File(filesDir, "main_dataset.json")
            if (mainFile.exists()) {
                val mainJson = JsonParser.parseString(mainFile.readText()).asJsonObject
                if (mainJson.has(dataset.id)) {
                    mainJson.remove(dataset.id)
                    mainFile.writeText(com.google.gson.Gson().toJson(mainJson))
                }
            }
            removeDownloadedDatasetId(dataset.id)
            Toast.makeText(this, "Removed ${dataset.title}", Toast.LENGTH_SHORT).show()
            fetchDatasets()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to remove dataset", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMasterDatasetDialog() {
        val mainFile = java.io.File(filesDir, "main_dataset.json")
        val content = if (mainFile.exists()) {
            try {
                val jsonObject = com.google.gson.JsonParser.parseString(mainFile.readText()).asJsonObject
                val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                gson.toJson(jsonObject)
            } catch (e: Exception) {
                "Error formatting data: ${e.message}"
            }
        } else {
            "Master Dataset is empty. Download a dataset first!"
        }

        val scrollView = android.widget.ScrollView(this)
        val textView = android.widget.TextView(this).apply {
            text = content
            setPadding(32, 32, 32, 32)
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(android.graphics.Color.DKGRAY)
        }
        scrollView.addView(textView)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Offline Master Brain Data")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show()
    }
}

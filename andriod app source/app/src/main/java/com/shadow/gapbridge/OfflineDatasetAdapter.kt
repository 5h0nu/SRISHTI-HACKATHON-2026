package com.shadow.gapbridge

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class OfflineDataset(val id: String, val title: String, val isDownloaded: Boolean, val localPath: String?)

class OfflineDatasetAdapter(
    private var datasets: List<OfflineDataset>,
    private val onDownloadClick: (OfflineDataset) -> Unit,
    private val onDeleteClick: (OfflineDataset) -> Unit
) : RecyclerView.Adapter<OfflineDatasetAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDatasetName: TextView = view.findViewById(R.id.tvDatasetName)
        val tvDatasetStatus: TextView = view.findViewById(R.id.tvDatasetStatus)
        val tvDatasetPath: TextView = view.findViewById(R.id.tvDatasetPath)
        val btnDownloadDataset: Button = view.findViewById(R.id.btnDownloadDataset)
        val btnDeleteDataset: Button = view.findViewById(R.id.btnDeleteDataset)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_offline_dataset, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dataset = datasets[position]
        holder.tvDatasetName.text = dataset.title

        if (dataset.isDownloaded) {
            holder.tvDatasetStatus.text = "Downloaded"
            holder.tvDatasetStatus.setTextColor(Color.parseColor("#4CAF50"))
            holder.tvDatasetPath.visibility = View.VISIBLE
            holder.tvDatasetPath.text = "Location: ${dataset.localPath}"
            
            holder.btnDownloadDataset.visibility = View.GONE
            holder.btnDeleteDataset.visibility = View.VISIBLE
        } else {
            holder.tvDatasetStatus.text = "Not Downloaded"
            holder.tvDatasetStatus.setTextColor(Color.parseColor("#888888"))
            holder.tvDatasetPath.visibility = View.GONE
            
            holder.btnDownloadDataset.visibility = View.VISIBLE
            holder.btnDeleteDataset.visibility = View.GONE
        }

        holder.btnDownloadDataset.setOnClickListener { onDownloadClick(dataset) }
        holder.btnDeleteDataset.setOnClickListener { onDeleteClick(dataset) }
    }

    override fun getItemCount() = datasets.size

    fun updateData(newDatasets: List<OfflineDataset>) {
        datasets = newDatasets
        notifyDataSetChanged()
    }
}

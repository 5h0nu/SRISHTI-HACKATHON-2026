package com.shadow.gapbridge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ModuleAdapter(
    private var modules: List<Module>,
    private val onItemClick: (Module) -> Unit,
    private val onDownloadClick: (Module) -> Unit
) : RecyclerView.Adapter<ModuleAdapter.ModuleViewHolder>() {

    class ModuleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val btnDownload: Button = view.findViewById(R.id.btnDownload)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        val cardLayout: View = view.findViewById(R.id.cardLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModuleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_module, parent, false)
        return ModuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModuleViewHolder, position: Int) {
        val module = modules[position]
        holder.tvTitle.text = module.title

        if (module.isDownloaded) {
            holder.tvStatus.text = "Downloaded"
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            holder.btnDownload.visibility = View.GONE
        } else {
            holder.tvStatus.text = "Available to Download"
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#888888"))
            holder.btnDownload.visibility = View.VISIBLE
        }

        holder.btnDownload.setOnClickListener {
            onDownloadClick(module)
        }

        holder.cardLayout.setOnClickListener {
            onItemClick(module)
        }
    }

    override fun getItemCount() = modules.size

    fun updateData(newModules: List<Module>) {
        modules = newModules
        notifyDataSetChanged()
    }
}

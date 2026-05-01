package com.shadow.gapbridge

data class Module(
    val id: String,
    val title: String,
    val url: String,
    var isDownloaded: Boolean = false,
    var localPath: String? = null
)

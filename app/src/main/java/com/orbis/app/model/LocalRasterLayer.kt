package com.orbis.app.model

data class LocalRasterLayer(
    val id: String,
    val displayName: String,
    val filePath: String,
    val opacity: Float = 0.72f,
    val visible: Boolean = true,
)

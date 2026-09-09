package com.orbis.app.model

data class MapPoint(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long = System.currentTimeMillis(),
)

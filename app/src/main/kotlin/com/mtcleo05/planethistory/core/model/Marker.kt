package com.mtcleo05.planethistory.core.model

import com.mtcleo05.planethistory.core.ext.getMarkerType

data class Marker (
    val description: String,
    val tags: List<String>,
    val mainTag: String,
    val markerName: String,
    val lat: Double,
    val lng: Double,
    val id: String,
    val images: List<String>
)

fun Marker.mapToUI() : MarkerUI{
    return MarkerUI(
        mainTag.getMarkerType() ,
        tags,
        description,
        markerName,
        lat,
        lng,
        id,
        images
    )
}

data class MarkerUI (
    val type: MarkerTypes,
    val tags: List<String>,
    val description: String,
    val markerName: String,
    val lat: Double,
    val lng: Double,
    val id: String,
    val images: List<String>,
)

enum class MarkerTypes{
    MONUMENTS,
    CTM,
    CURIOSITY,
    PARKS,
    AGES,
    NOTYPE
}

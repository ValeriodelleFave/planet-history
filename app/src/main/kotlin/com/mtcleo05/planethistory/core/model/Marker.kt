package com.mtcleo05.planethistory.core.model

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
        getMarkerTypes(mainTag) ,
        tags,
        description,
        markerName,
        lat,
        lng,
        id,
        images
    )
}

private fun getMarkerTypes(type : String) : MarkerTypes {
    return when(type){
        MONUMENTS_LABEL -> MarkerTypes.MONUMENTS
        CTM_LABEL -> MarkerTypes.CTM
        CURIOSITY1_LABEL,
        CURIOSITY2_LABEL -> MarkerTypes.CURIOSITY
        PARKS_LABEL -> MarkerTypes.PARKS
        AGES_LABEL -> MarkerTypes.AGES
        else -> MarkerTypes.NOTYPE
    }
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

private const val MONUMENTS_LABEL = "Monumenti"
private const val CTM_LABEL = "CTM"
private const val CURIOSITY1_LABEL = "Curiosità"
private const val CURIOSITY2_LABEL = "Curiosita"
private const val PARKS_LABEL = "Parchi"
private const val AGES_LABEL = "Epoche"
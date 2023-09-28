package com.mtcleo05.planethistory.core.ext

import com.google.gson.JsonObject
import com.mtcleo05.planethistory.core.model.Marker
import com.mtcleo05.planethistory.core.model.MarkerTypes
import com.mtcleo05.planethistory.core.model.MarkerUI
import org.json.JSONObject

fun JSONObject.getMarker() : Marker {
    val tagsArray = this.getJSONArray("tags")
    val imageArray = this.getJSONArray("images")
    val tagsList = mutableListOf<String>()
    val imageList = mutableListOf<String>()

    for (j in 0 until tagsArray.length()) {
        tagsList.add(tagsArray.getString(j))
    }

    for (j in 0 until imageArray.length()){
        imageList.add(imageArray.getString(j))
    }

    return Marker(
        description = this.getString("description"),
        tags = tagsList,
        mainTag = this.getString("main_tag"),
        markerName = this.getString("marker_name"),
        lat = this.getDouble("lat"),
        lng = this.getDouble("lng"),
        id = this.getString("id"),
        images = imageList
    )
}

fun JsonObject.mapToMarkerUI() : MarkerUI{
    return MarkerUI(
        type = get("mainTag").asString.getMarkerType(),
        tags = get("tags").asString.split(", "),
        description = get("description").asString.removeSurrounding("\""),
        markerName = get("markerName").asString.removeSurrounding("\""),
        images = get("images")?.asString?.split(", ") ?: emptyList(),
        lat = 0.0,
        lng = 0.0,
        id = "",
    )
}

fun String.getMarkerType(): MarkerTypes{
    return when(this){
        MONUMENTS_LABEL -> MarkerTypes.MONUMENTS
        CTM_LABEL -> MarkerTypes.CTM
        CURIOSITY1_LABEL,
        CURIOSITY2_LABEL -> MarkerTypes.CURIOSITY
        PARKS_LABEL -> MarkerTypes.PARKS
        AGES_LABEL -> MarkerTypes.AGES
        else -> MarkerTypes.NOTYPE
    }
}


private const val MONUMENTS_LABEL = "Monumenti"
private const val CTM_LABEL = "CTM"
private const val CURIOSITY1_LABEL = "Curiosità"
private const val CURIOSITY2_LABEL = "Curiosita"
private const val PARKS_LABEL = "Parchi"
private const val AGES_LABEL = "Epoche"

fun MarkerUI.mapToJsonObject() : JsonObject{
    val jsonData = JsonObject()
    jsonData.addProperty("description", description)
    jsonData.addProperty("tags", tags.joinToString(", "))
    jsonData.addProperty("type", type.ordinal)
    jsonData.addProperty("markerName", markerName)
    jsonData.addProperty("lat", lat)
    jsonData.addProperty("lng", lng)
    jsonData.addProperty("images", images.joinToString(", "))
    return jsonData
}
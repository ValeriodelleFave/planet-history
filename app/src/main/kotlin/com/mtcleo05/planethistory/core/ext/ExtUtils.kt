package com.mtcleo05.planethistory.core.ext

import com.google.gson.JsonObject
import com.mtcleo05.planethistory.core.model.Marker
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
package com.mtcleo05.planethistory.core.ext

import com.mtcleo05.planethistory.core.model.Marker
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
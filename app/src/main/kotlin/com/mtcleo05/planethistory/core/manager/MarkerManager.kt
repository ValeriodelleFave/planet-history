package com.mtcleo05.planethistory.core.manager

import android.util.Log
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mtcleo05.planethistory.core.ext.getMarker
import com.mtcleo05.planethistory.core.model.Marker
import com.mtcleo05.planethistory.core.model.MarkerTypes
import com.mtcleo05.planethistory.core.model.MarkerUI
import com.mtcleo05.planethistory.core.model.mapToUI
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

class MarkerManager {

    private val TAG = "MarkerManager"
    private var _markersList: MutableList<MarkerUI> = mutableListOf()
    val markersList get() = _markersList
    private var pointMap: MutableMap<MarkerTypes, PointAnnotation> = mutableMapOf()

    private fun loadJSONResource(input : InputStream) : String?{
        return try {
            val jsonString = input.bufferedReader().use { it.readText() }
            jsonString
        } catch (e: IOException){
            Log.e(TAG,"Error during parse inputStream ${e.printStackTrace()}")
            null //TODO Decidere se passare stringa vuota invece di null
        }
    }

    fun loadMarkerList(input: InputStream) {
        val jsonArray = JSONArray(loadJSONResource(input))
        for (i in 0 until jsonArray.length()) {
            val marker = (jsonArray[i] as JSONObject).getMarker()
            _markersList.add(marker.mapToUI())
        }
    }

    fun setPointMap(type:MarkerTypes, point: PointAnnotation){
        pointMap[type] = point
    }

    fun getPointByType(type: MarkerTypes) : Map<MarkerTypes,PointAnnotation> {
        return pointMap.filter { it.key == type }
    }

    fun getAllPointAnnotationInPointMap(): MutableCollection<PointAnnotation> {
        return pointMap.values
    }

    fun getFilteredMarkersListByString(filter: String): List<MarkerUI> {
        return _markersList.filter { it.markerName.lowercase().startsWith(filter.lowercase()) }
    }


}
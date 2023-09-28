package com.mtcleo05.planethistory

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.View.OnTouchListener
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonElement
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mtcleo05.planethistory.core.ext.mapToJsonObject
import com.mtcleo05.planethistory.core.ext.mapToMarkerUI
import com.mtcleo05.planethistory.core.manager.ImageManager
import com.mtcleo05.planethistory.core.manager.MarkerManager
import com.mtcleo05.planethistory.core.model.MarkerTypes
import com.mtcleo05.planethistory.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.sqrt


class MainActivity : AppCompatActivity() {

    private val activeAnnotations: MutableMap<String, PointAnnotation> = mutableMapOf()

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var mapView: MapView? = null
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var currentLocationMarker: PointAnnotation? = null
    private var isFirstLocationUpdate = true
    private var SearchResult: MutableList<PointAnnotation> = mutableListOf<PointAnnotation>()
    private var monumentiEnabled: Boolean = false
    private var ctmEnabled: Boolean = false
    private var curiositaEnabled: Boolean = false
    private var parchiEnabled: Boolean = false
    private var epocheEnabled: Boolean = false
    private var markerManager = MarkerManager()
    private var imageManager = ImageManager()

    private lateinit var imageLayout: CoordinatorLayout
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var searchBarEditText: EditText
    private lateinit var binding : ActivityMainBinding

    // Override Methods
    override fun onStart() {
        super.onStart()
        if (!hasLocationPermission()) {
            requestLocationPermission()
        } else if (!isLocationServiceEnabled()) {
            showLocationServiceDisabledDialog()
        } else {
            startLocationUpdates()
        }
    }

    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
    }

    //TODO BEST SOLUTION
    private fun getDisplayMetrics(): DisplayMetrics {
        val displayMetrics = DisplayMetrics()
        val windowManager = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display.getRealMetrics(displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            display.getMetrics(displayMetrics)
        }
        return displayMetrics
    }

    @SuppressLint("ClickableViewAccessibility") //Caused by OnTouchListener for Custom motion layout see documentation for MotionLayout class
    private fun setCoordinatorLayout() {
        binding.run {
            coordinatorLayout.run{
                root.setOnTouchListener( object : OnTouchListener{
                    private val MIN_SWIPE_DISTANCE = 100
                    private val MAX_SWIPE_DURATION = 300

                    private var startY: Float = 0f
                    private var startTime: Long = 0

                    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                        when (event?.action) {
                            MotionEvent.ACTION_DOWN -> {
                                startY = event.y
                                startTime = System.currentTimeMillis()
                                return true
                            }
                            MotionEvent.ACTION_UP -> {
                                val endY = event.y
                                val endTime = System.currentTimeMillis()

                                val distance = endY - startY
                                val duration = endTime - startTime

                                if (distance < -MIN_SWIPE_DISTANCE && duration < MAX_SWIPE_DURATION) {
                                    Toast.makeText(this@MainActivity, "Showing detail", Toast.LENGTH_SHORT).show()
                                    binding.detailLayout.root.isVisible = true
                                    root.isVisible = false
                                }

                                return true
                            }
                        }
                        return false
                    }
                })
            }
        }
    }
    @SuppressLint("ClickableViewAccessibility") //Caused by OnTouchListener for Custom motion layout see documentation for MotionLayout class
    private fun setDetailLayout(displayMetrics: DisplayMetrics) {
        binding.run {
            detailLayout.run {
                root.layoutParams.height = displayMetrics.heightPixels - 100
                root.isVisible = false
                root.setOnTouchListener(object : OnTouchListener{
                    private val MIN_SWIPE_DISTANCE = 100
                    private val MAX_SWIPE_DURATION = 3000

                    private var startY: Float = 0f
                    private var startTime: Long = 0

                    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                        when (event?.action) {
                            MotionEvent.ACTION_UP -> {
                                startY = event.y
                                startTime = System.currentTimeMillis()
                                return true
                            }
                            MotionEvent.ACTION_DOWN -> {
                                val endY = event.y
                                val endTime = System.currentTimeMillis()

                                val distance = endY - startY
                                val duration = endTime - startTime

                                if (distance < -MIN_SWIPE_DISTANCE && duration < MAX_SWIPE_DURATION) {
                                    // Swipe up gesture detected
                                    Toast.makeText(this@MainActivity, "Showing compat", Toast.LENGTH_SHORT).show()
                                    root.isVisible = false
                                    binding.coordinatorLayout.root.isVisible = true
                                }

                                return true
                            }
                        }
                        return false
                    }
                })
            }
        }
    }

    private fun setImageLayout() {
        binding.run {
            imageFullScreen.run {
                root.setOnClickListener {
                    root.isVisible = !root.isVisible
                }
            }
        }
    }

    private fun setBtnCenterMap() {
        binding.btnCenterMap.setOnClickListener {
            centerMapOnUserPosition()
        }
    }

    private fun setSearchBarEditText() {
        binding.searchBarEditText.run {
            doOnTextChanged{text, start, before, count ->
                if(text.isNullOrEmpty()){
                    for (marker in markerManager.getAllPointAnnotationInPointMap()) {
                        if(!(SearchResult.contains(marker))){
                            addPointAnnotation(listOf(marker))
                        }
                    }
                    SearchResult.clear()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val markersRaw = applicationContext.resources.openRawResource(R.raw.markers)
        markerManager.loadMarkerList(markersRaw)

        setupOnClickListener()
        setupMap()

        val displayMetrics = getDisplayMetrics()
        setCoordinatorLayout()
        setDetailLayout(displayMetrics)
        setImageLayout()
        setBtnCenterMap()
        setSearchBarEditText()

        locationRequest = LocationRequest.create().apply {
            interval = LOCATION_UPDATE_INTERVAL
            fastestInterval = LOCATION_UPDATE_FASTEST_INTERVAL
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { handleLocationResult(it) }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                showLocationPermissionDeniedDialog()
            }
        }
    }

    // Private Methods

    private fun setupOnClickListener(){
        setupCategoryButtonsOnClickListener()
        setupSearchButtonOnClickListener()
    }

    private fun setupSearchButtonOnClickListener() {
        binding.run {
            searchButton.setOnClickListener {
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(searchButton.windowToken, 0)
                search()
            }
        }
    }
    @SuppressLint("ClickableViewAccessibility") //Caused by OnTouchListener for Custom motion layout see documentation for MotionLayout class
    private fun setupMap(){
        binding.mapView.run {
            getMapboxMap().loadStyleUri("mapbox://styles/ssulf/clhgo1b4901d001qy8wqrgo52")
            scalebar.enabled = false
            attribution.enabled = false
            logo.enabled = false
            compass.enabled = false

            pointAnnotationManager = annotations.createPointAnnotationManager()
            pointAnnotationManager?.addClickListener(OnPointAnnotationClickListener {
                    annotation:PointAnnotation ->
                onMarkerItemClick(annotation)
                true
            })
            addMarkerToMap()
        }
    }

    private fun setupCategoryButtonsOnClickListener(){
        binding.run {
            categoryLayout.run {
                btnMonumenti.setOnClickListener {
                    showCategory(MarkerTypes.MONUMENTS)
                }
                btnCTM.setOnClickListener {
                    showCategory(MarkerTypes.CTM)
                }
                btnCuriosita.setOnClickListener {
                    showCategory(MarkerTypes.CURIOSITY)
                }
                btnParchi.setOnClickListener {
                    showCategory(MarkerTypes.PARKS)
                }
                btnEpoche.setOnClickListener {
                    showCategory(MarkerTypes.AGES)
                }
            }

            btnCategorySelect.setOnClickListener {
                binding.run {
                    categoryLayout.root.isVisible = !categoryLayout.root.isVisible
                }
            }
        }
    }

    private fun addPointAnnotation(collection: Collection<PointAnnotation>) {
        collection.forEach {
            val options = PointAnnotationOptions()
                .withPoint(it.point)
                .withIconImage(imageManager.bitmapFromDrawableRes(this@MainActivity, R.drawable.green_marker)!!)
            pointAnnotationManager?.create(options)
        }
    }

    private fun deletePointAnnotation(map: Map<MarkerTypes, PointAnnotation>) {
        map.forEach {
            pointAnnotationManager?.delete(it.value)
        }
    }

    private fun showCategory(type: MarkerTypes){ // TODO: Refactor disableCategory Logic
        when(type) {
            MarkerTypes.MONUMENTS -> {
                if (monumentiEnabled)
                    addPointAnnotation(markerManager.getPointByType(MarkerTypes.MONUMENTS).values)
                else
                    deletePointAnnotation(markerManager.getPointByType(MarkerTypes.MONUMENTS))
                monumentiEnabled = !monumentiEnabled
            }
            MarkerTypes.CTM-> {
                if (ctmEnabled)
                    addPointAnnotation(markerManager.getPointByType(MarkerTypes.CTM).values)
                else
                    deletePointAnnotation(markerManager.getPointByType(MarkerTypes.CTM))
                ctmEnabled = !ctmEnabled
            }
            MarkerTypes.CURIOSITY-> {
                if (curiositaEnabled)
                    addPointAnnotation(markerManager.getPointByType(MarkerTypes.CURIOSITY).values)
                else
                    deletePointAnnotation(markerManager.getPointByType(MarkerTypes.CURIOSITY))
                curiositaEnabled = !curiositaEnabled
            }
            MarkerTypes.PARKS-> {
                if (parchiEnabled)
                    addPointAnnotation(markerManager.getPointByType(MarkerTypes.PARKS).values)
                else
                    deletePointAnnotation(markerManager.getPointByType(MarkerTypes.PARKS))
                parchiEnabled = !parchiEnabled
            }
            MarkerTypes.AGES-> {
                if (epocheEnabled)
                    addPointAnnotation(markerManager.getPointByType(MarkerTypes.AGES).values)
                else
                    deletePointAnnotation(markerManager.getPointByType(MarkerTypes.AGES))
                epocheEnabled = !epocheEnabled
            }
            MarkerTypes.NOTYPE -> {
                // TODO: Manage Notype case
            }
        }
    }

    private fun handleLocationResult(location: Location) {
        val zoomLevel = 15.0

        val newLatitude = location.latitude
        val newLongitude = location.longitude

        val latitudeChange = abs(newLatitude - latitude)
        val longitudeChange = abs(newLongitude - longitude)

        // Remainder: PCT POSITION CHANGE THRESHOLD
        if (latitudeChange > PCT || longitudeChange > PCT) {
            latitude = newLatitude
            longitude = newLongitude

            currentLocationMarker?.let {
                deletePositionMarker(it)
                currentLocationMarker = null
            }

            currentLocationMarker = addPositionToMap(latitude, longitude)
        }

        if (isFirstLocationUpdate) {
            centerMapOnUserPosition(zoom = zoomLevel)
            isFirstLocationUpdate = false
        }
    }

    private fun deletePositionMarker(marker: PointAnnotation) {
        pointAnnotationManager?.delete(marker)
        activeAnnotations.remove(marker.id.toString())
    }

    private fun addPositionToMap(lat: Double, lng: Double): PointAnnotation? {
        imageManager.bitmapFromDrawableRes(
            this@MainActivity,
            R.drawable.red_marker
        )?.let { markerIcon ->
            // Find and delete the previous marker if it exists
            val existingMarker = currentLocationMarker
            if (existingMarker != null && activeAnnotations.containsKey(existingMarker.id.toString())) {
                pointAnnotationManager?.delete(existingMarker)
                activeAnnotations.remove(existingMarker.id.toString())
                currentLocationMarker = null
            }

            // Create a new marker
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(lng, lat))
                .withIconImage(markerIcon)
            currentLocationMarker = pointAnnotationManager?.create(pointAnnotationOptions)

            return currentLocationMarker
        }

        return null
    }

    private fun addMarkerToMap(){
        markerManager.markersList.forEach { marker ->
            imageManager.bitmapFromDrawableRes(
                this,
                R.drawable.green_marker
            )?.let { icon ->
                val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(marker.lng, marker.lat))
                    .withIconImage(icon)
                    .withData(marker.mapToJsonObject())

                val point = pointAnnotationManager?.create(pointAnnotationOptions)
                point?.let {
                    activeAnnotations[marker.id] = it
                    markerManager.setPointMap(marker.type, point)
                } //TODO verificare l'effettivo funzionamento
            }
        }
    }

    private fun onMarkerItemClick(marker: PointAnnotation) {

        val markerData = marker.getData()
        if (markerData == null) {
            Log.e("DEBUG", "Marker Data is null")
            return
        }
        val type = markerData?.asJsonObject?.get("type").toString().toInt()
        val color = when(type){
            MarkerTypes.MONUMENTS.ordinal -> R.color.monuments_color
            MarkerTypes.CTM.ordinal -> R.color.ctm_color
            MarkerTypes.CURIOSITY.ordinal -> R.color.curiosity_color
            MarkerTypes.PARKS.ordinal -> R.color.parks_color
            MarkerTypes.AGES.ordinal -> R.color.ages_color
            else -> R.color.other_color
        }

        binding.coordinatorLayout.root.isVisible = !binding.coordinatorLayout.root.isVisible
        updateDetailViews(markerData,color)

    }

    private fun updateDetailViews(markerData: JsonElement?, color: Int) {

        val markerUI = markerData?.asJsonObject?.mapToMarkerUI()

        binding.run {
            coordinatorLayout.run {
                (root.background as GradientDrawable).setColor(color)
                (buttonOther.background as GradientDrawable).setColor(color)
                (buttonWeb.background as GradientDrawable).setColor(color)
                (buttonCall.background as GradientDrawable).setColor(color)
                (buttonTravel.background as GradientDrawable).setColor(color)

                nameTextview.text = markerUI?.markerName
            }
        }

        binding.run {
            with(detailLayout){
                coordinatorLayout.run {
                    (root.background as GradientDrawable).setColor(color)
                    (buttonOther.background as GradientDrawable).setColor(color)
                    (buttonWeb.background as GradientDrawable).setColor(color)
                    (buttonCall.background as GradientDrawable).setColor(color)
                    (buttonTravel.background as GradientDrawable).setColor(color)

                    nameTextview.text = markerUI?.markerName
                }

                detailDescription.text = markerUI?.description
                detailTags.text = markerUI?.tags?.joinToString(", ")
                detailMainTag.text = markerUI?.type?.name


                markerUI?.images?.let {
                    for(i in it.indices){
                        val imageView = ImageView(applicationContext)

                        DownloadImageTask(imageView).execute(it[i])

                        imageContainer.addView(imageView)

                        imageView.setOnClickListener {
                            imageLayout.visibility = View.VISIBLE
                            DownloadImageTask(imageFullScreen.imageFullScreen).execute(markerUI.images[i])
                        }

                        imageView.layoutParams.height = 500
                        imageView.layoutParams.width = 500
                        imageView.setPadding(10, 0, 10, 0)
                    }
                }

            }
        }



    }
    private fun centerMapOnUserPosition(zoom: Double = 13.0) {
        binding.mapView.run {
            getMapboxMap().setCamera(
                com.mapbox.maps.CameraOptions.Builder()
                    .center(Point.fromLngLat(longitude,latitude))
                    .zoom(zoom)
                    .build()
            )
        }
    }

    /**
     * Localization
     */

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        if (isLocationServiceEnabled()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            showLocationServiceDisabledDialog()
        }
    }

    private fun showLocationServiceDisabledDialog() {
        AlertDialog.Builder(this)
            .setTitle(resources.getString(R.string.alert_title))
            .setMessage(resources.getString(R.string.alert_message))
            .setPositiveButton(resources.getString(R.string.alert_positive_button_label)) { _, _ ->
                openLocationSettings()
            }
            .setNegativeButton(resources.getString(R.string.alert_negative_button_label)) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()

    }

    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }

    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun showLocationPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(resources.getString(R.string.alert_location_title))
            .setMessage(resources.getString(R.string.alert_location_message))
            .setPositiveButton(resources.getString(R.string.alert_location_positive_button_label)) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(resources.getString(R.string.alert_location_negative_button_label)) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun startLocationUpdates() {
        if (hasLocationPermission()) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    null
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    private fun stopLocationUpdates() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }


    /**
     * Search
     */

    private fun search(){
        val searchText = searchBarEditText.text.toString()
        globalSearch(searchText)
    }

    private fun searchName(prompt: String, markerElement: PointAnnotation){
        val markerData = markerElement.getData()
        val markerName = markerData?.asJsonObject?.get("markerName").toString().removeSurrounding("\"")

        if(markerName.lowercase().contains(prompt.lowercase())){
            if(!(SearchResult.contains(markerElement))){
                SearchResult.add(markerElement)
            }
        }
    }
    private fun searchTag(prompt: String, markerElement: PointAnnotation){
        val markerData = markerElement.getData()
        val markerMainTag = markerData?.asJsonObject?.get("mainTag").toString().removeSurrounding("\"")

        if(markerMainTag.lowercase().contains(prompt.lowercase())){
            if(!(SearchResult.contains(markerElement))) {
                SearchResult.add(markerElement)
            }
        }
    }

    private fun SearchResultCameraPosition() {
        var maxLatitude = Double.MIN_VALUE
        var minLatitude = Double.MAX_VALUE
        var maxLongitude = Double.MIN_VALUE
        var minLongitude = Double.MAX_VALUE

        for (markerElement in SearchResult) {
            val markerData = markerElement.getData()

            val latitude = markerData?.asJsonObject?.get("lat")?.asDouble!!
            val longitude = markerData.asJsonObject?.get("lng")?.asDouble!!

            if (latitude > maxLatitude) {
                maxLatitude = latitude
            }
            if (latitude < minLatitude) {
                minLatitude = latitude
            }
            if (longitude > maxLongitude) {
                maxLongitude = longitude
            }
            if (longitude < minLongitude) {
                minLongitude = longitude
            }
        }

        val optimalLatitude = (maxLatitude + minLatitude) / 2
        val optimalLongitude = (maxLongitude + minLongitude) / 2

        val distance = calculateDistance(maxLatitude, maxLongitude, minLatitude, minLongitude)

        val zoomLevel = calculateZoomLevel(distance, 13.0)

        mapView?.getMapboxMap()?.setCamera(
            com.mapbox.maps.CameraOptions.Builder()
                .center(Point.fromLngLat(optimalLongitude, optimalLatitude))
                .zoom(zoomLevel)
                .build()
        )
    }

    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val p = Math.PI / 180
        val a = 0.5 - cos((lat2 - lat1) * p) / 2 +
                cos(lat1 * p) * cos(lat2 * p) *
                (1 - cos((lon2 - lon1) * p)) / 2
        return 2 * 6371000 * asin(sqrt(a))
    }

    private fun calculateZoomLevel(distance: Double, maxZoom: Double): Double {
        val defaultZoom = 15.0 // Adjust this value as needed
        val zoomFactor = 156412.0 // Adjust this value as needed
        val zoomLevel = defaultZoom - log2(distance) + log2(zoomFactor)
        return zoomLevel.coerceAtMost(maxZoom)
    }

    private fun globalSearch(prompt: String) {
        for (markerElement in markerManager.getAllPointAnnotationInPointMap()) {
            searchName(prompt, markerElement)
            searchTag(prompt, markerElement)
        }

        for (deleteMarker in markerManager.getAllPointAnnotationInPointMap()){
            if(!(SearchResult.contains(deleteMarker))){
                deletePositionMarker(deleteMarker)
            }
        }

        SearchResultCameraPosition()
    }
    companion object {
        private const val LOCATION_UPDATE_INTERVAL: Long = 10000 // 10 seconds
        private const val LOCATION_UPDATE_FASTEST_INTERVAL: Long = 5000 // 5 seconds
        private const val PCT = 0.00002 // POSITION CHANGE THRESHOLD
        private const val LOCATION_PERMISSION_REQUEST_CODE = 177013
    }
}
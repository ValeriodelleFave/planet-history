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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mtcleo05.planethistory.core.ext.loadImage
import com.mtcleo05.planethistory.core.ext.mapToJsonObject
import com.mtcleo05.planethistory.core.ext.mapToMarkerUI
import com.mtcleo05.planethistory.core.manager.ImageManager
import com.mtcleo05.planethistory.core.manager.MarkerManager
import com.mtcleo05.planethistory.core.model.MarkerTypes
import com.mtcleo05.planethistory.core.model.MarkerUI
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

    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var binding : ActivityMainBinding

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
            @Suppress("DEPRECATION")
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val markersRaw = applicationContext.resources.openRawResource(R.raw.markers)
        markerManager.loadMarkerList(markersRaw)

        setupCategoryButtonsOnClickListener()
        setupMap()

        val displayMetrics = getDisplayMetrics()
        setCoordinatorLayout()
        setDetailLayout(displayMetrics)
        setImageLayout()
        setBtnCenterMap()
        setSearchBarEditText()

        @Suppress("DEPRECATION")
        locationRequest = LocationRequest.create().apply {
            @Suppress("DEPRECATION")
            interval = LOCATION_UPDATE_INTERVAL
            @Suppress("DEPRECATION")
            fastestInterval = LOCATION_UPDATE_FASTEST_INTERVAL
            @Suppress("DEPRECATION")
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { handleLocationResult(it) }
            }
        }
    }

    private fun setSearchBarEditText() {
        binding.searchView.run {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    if (!query.isNullOrBlank()) {
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(binding.searchView.windowToken, 0)

                        deletePointAnnotation(SearchResult)
                        SearchResult.clear()

                        for (point in markerManager.getAllPointAnnotationInPointMap()) {
                            val marker = point.getData()?.asJsonObject?.mapToMarkerUI()
                            if (marker?.markerName?.lowercase() == query.lowercase()) {
                                if(!SearchResult.contains(point)) {
                                    SearchResult.add(point)
                                }
                            }
                        }

                        addPointAnnotation(SearchResult)

                        changeCameraPosition()
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    if (!newText.isNullOrBlank()) {
                        val filteredMarkers = markerManager.getFilteredMarkersListByString(newText)
                        // TODO: Create a Recycle view using filteredMarkers as dataSource (click on item, you should change camera position)
                    }
                    return true
                }
            })
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

    private fun deletePointAnnotation(collection: Collection<PointAnnotation>) {
        collection.forEach {
            pointAnnotationManager?.delete(it)
        }
    }

    private fun showCategory(type: MarkerTypes){ // TODO: Refactor disableCategory Logic
        when(type) {
            MarkerTypes.MONUMENTS -> {
                if (monumentiEnabled)
                    addPointAnnotation(markerManager.getPointByType(MarkerTypes.MONUMENTS).values)
                else
                    deletePointAnnotation(markerManager.getPointByType(MarkerTypes.MONUMENTS).values)
                monumentiEnabled = !monumentiEnabled
            }
            MarkerTypes.CTM-> {
                if (ctmEnabled)
                    addPointAnnotation(markerManager.getPointByType(MarkerTypes.CTM).values)
                else
                    deletePointAnnotation(markerManager.getPointByType(MarkerTypes.CTM).values)
                ctmEnabled = !ctmEnabled
            }
            MarkerTypes.CURIOSITY-> {
                if (curiositaEnabled)
                    addPointAnnotation(markerManager.getPointByType(MarkerTypes.CURIOSITY).values)
                else
                    deletePointAnnotation(markerManager.getPointByType(MarkerTypes.CURIOSITY).values)
                curiositaEnabled = !curiositaEnabled
            }
            MarkerTypes.PARKS-> {
                if (parchiEnabled)
                    addPointAnnotation(markerManager.getPointByType(MarkerTypes.PARKS).values)
                else
                    deletePointAnnotation(markerManager.getPointByType(MarkerTypes.PARKS).values)
                parchiEnabled = !parchiEnabled
            }
            MarkerTypes.AGES-> {
                if (epocheEnabled)
                    addPointAnnotation(markerManager.getPointByType(MarkerTypes.AGES).values)
                else
                    deletePointAnnotation(markerManager.getPointByType(MarkerTypes.AGES).values)
                epocheEnabled = !epocheEnabled
            }
            MarkerTypes.NOTYPE -> { }
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
        try {
            val markerUI = marker.getData()?.asJsonObject?.mapToMarkerUI()

            if (markerUI == null) {
                Log.e("DEBUG", "Marker Data is null")
                return
            }

            val color = when(markerUI.type){
                MarkerTypes.MONUMENTS -> R.color.monuments_color
                MarkerTypes.CTM -> R.color.ctm_color
                MarkerTypes.CURIOSITY -> R.color.curiosity_color
                MarkerTypes.PARKS -> R.color.parks_color
                MarkerTypes.AGES -> R.color.ages_color
                else -> R.color.other_color
            }

            binding.coordinatorLayout.root.isVisible = !binding.coordinatorLayout.root.isVisible
            updateDetailViews(markerUI,color)
        }catch (e: Exception) {
            Log.e("ERROR", "Error during on marker click -> ${e.message}")
        }
    }

    private fun updateDetailViews(markerUI: MarkerUI, color: Int) {
        binding.run {
            coordinatorLayout.run {
                (root.background as GradientDrawable).setColor(color)
                (buttonOther.background as GradientDrawable).setColor(color)
                (buttonWeb.background as GradientDrawable).setColor(color)
                (buttonCall.background as GradientDrawable).setColor(color)
                (buttonTravel.background as GradientDrawable).setColor(color)
                nameTextview.text = markerUI.markerName
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
                    nameTextview.text = markerUI.markerName
                }

                detailDescription.text = markerUI.description
                detailTags.text = markerUI.tags.joinToString(", ")
                detailMainTag.text = markerUI.type.name

                markerUI.images.let {
                    for(i in it.indices){
                        val imageView = createImageView(it[i])
                        imageContainer.addView(imageView)

                    }
                }
            }
        }
    }

    private fun createImageView(image: String) : ImageView {
        val imageView = ImageView(applicationContext)
        var layoutParams = imageView.layoutParams

        if(layoutParams == null){
            layoutParams = ViewGroup.LayoutParams(500,500)
        }

        imageView.layoutParams = layoutParams
        imageView.setPadding(10, 0, 10, 0)
        imageView.setOnClickListener {
            with(binding.imageFullScreen){
                imageLayout.isVisible = true
                imageFullScreen.loadImage(this@MainActivity,image)
            }
        }

        imageView.loadImage(this@MainActivity,image)

        return imageView
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

    private fun changeCameraPosition() {
        var maxLatitude = 90.0
        var minLatitude = -90.0
        var maxLongitude = 180.0
        var minLongitude = -180.0

        for (markerElement in SearchResult) {
            val markerData = markerElement.getData()?.asJsonObject?.mapToMarkerUI() ?: return

            val latitude = markerData.lat
            val longitude = markerData.lng

            maxLatitude = if (latitude > maxLatitude) latitude else maxLatitude
            minLatitude = if (latitude < minLatitude) latitude else minLatitude
            maxLongitude = if (longitude > maxLongitude) longitude else maxLongitude
            minLongitude = if (longitude < minLongitude) longitude else minLongitude
        }

        val optimalLatitude = (maxLatitude + minLatitude) / 2
        val optimalLongitude = (maxLongitude + minLongitude) / 2

        val distance = calculateDistance(maxLatitude, maxLongitude, minLatitude, minLongitude)

        val zoomLevel = calculateZoomLevel(distance, 3.0)

        binding.mapView.run {
            getMapboxMap().setCamera(
                com.mapbox.maps.CameraOptions.Builder()
                    .center(Point.fromLngLat(optimalLongitude, optimalLatitude))
                    .zoom(zoomLevel)
                    .build()
            )
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p = Math.PI / 180
        val a = 0.5 - cos((lat2 - lat1) * p) / 2 +
                cos(lat1 * p) * cos(lat2 * p) *
                (1 - cos((lon2 - lon1) * p)) / 2
        return 2 * 6371000 * asin(sqrt(a))
    }

    private fun calculateZoomLevel(distance: Double, maxZoom: Double): Double {
        val zoomLevel = DEFAULT_ZOOM_VALUE - log2(distance) + log2(ZOOM_FACTORY_VALUE)
        return zoomLevel.coerceAtMost(maxZoom)
    }

    companion object {
        private const val LOCATION_UPDATE_INTERVAL: Long = 10000 // 10 seconds
        private const val LOCATION_UPDATE_FASTEST_INTERVAL: Long = 5000 // 5 seconds
        private const val PCT = 0.00002 // POSITION CHANGE THRESHOLD
        private const val LOCATION_PERMISSION_REQUEST_CODE = 177013
        private const val DEFAULT_ZOOM_VALUE = 15.0 // Adjust this value as needed
        private const val ZOOM_FACTORY_VALUE = 156412.0 // Adjust this value as needed
    }
}
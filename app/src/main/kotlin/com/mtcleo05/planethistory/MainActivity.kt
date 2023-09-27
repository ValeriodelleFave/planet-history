package com.mtcleo05.planethistory

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mtcleo05.planethistory.core.ext.mapToJsonObject
import com.mtcleo05.planethistory.core.manager.MarkerManager
import com.mtcleo05.planethistory.core.model.Marker
import com.mtcleo05.planethistory.core.model.MarkerTypes
import com.mtcleo05.planethistory.core.model.MarkerUI
import com.mtcleo05.planethistory.databinding.ActivityMainBinding
import java.io.IOException
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.sqrt


class MainActivity : AppCompatActivity() {


    private lateinit var jsonString: String

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var mapView: MapView? = null
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var currentLocationMarker: PointAnnotation? = null
    private val activeAnnotations: MutableMap<String, PointAnnotation> = mutableMapOf()

    private lateinit var compactLayout: LinearLayout
    private lateinit var includeLayout: LinearLayout
    private lateinit var layoutCategories: LinearLayout
    private lateinit var coordinatorLayout: RelativeLayout
    private lateinit var detailLayout: RelativeLayout
    private lateinit var colorChangeBackground: GradientDrawable
    private lateinit var imageContainer: LinearLayout
    private lateinit var imageLayout: CoordinatorLayout

    private var isFirstLocationUpdate = true
    private val PCT = 0.00002 // POSITION CHANGE THRESHOLD
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var searchBarEditText: EditText
    private val LOCATION_PERMISSION_REQUEST_CODE = 177013


    private val ColorTagMap = mutableMapOf<String, (PointAnnotation) -> Unit>()

    private lateinit var CurrentText: TextView
    private lateinit var CurrentButton: Button

    private lateinit var CategorySelect: ImageButton

    private var AllMarkers: MutableList<PointAnnotation> = mutableListOf<PointAnnotation>()
    private var SearchResult: MutableList<PointAnnotation> = mutableListOf<PointAnnotation>()
    private var ListaMonumenti: MutableList<PointAnnotation> = mutableListOf<PointAnnotation>()
    private var ListaCTM: MutableList<PointAnnotation> = mutableListOf<PointAnnotation>()
    private var ListaCuriosita: MutableList<PointAnnotation> = mutableListOf<PointAnnotation>()
    private var ListaParchi: MutableList<PointAnnotation> = mutableListOf<PointAnnotation>()
    private var ListaEpoche: MutableList<PointAnnotation> = mutableListOf<PointAnnotation>()

    private var monumentiEnabled: Boolean = true
    private var ctmEnabled: Boolean = true
    private var curiositaEnabled: Boolean = true
    private var parchiEnabled: Boolean = true
    private var epocheEnabled: Boolean = true

    private lateinit var display: Display
    companion object {
        private const val LOCATION_UPDATE_INTERVAL: Long = 10000 // 10 seconds
        private const val LOCATION_UPDATE_FASTEST_INTERVAL: Long = 5000 // 5 seconds
    }

    private lateinit var binding : ActivityMainBinding
    private var markerManager = MarkerManager()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val markersRaw = applicationContext.resources.openRawResource(R.raw.markers)
        markerManager.loadMarkerList(markersRaw)

        setupOnClickListener()
        setupMap()
   
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
                    disableCategory(MarkerTypes.MONUMENTS)
                }
                btnCTM.setOnClickListener {
                    disableCategory(MarkerTypes.CTM)
                }
                btnCuriosita.setOnClickListener {
                    disableCategory(MarkerTypes.CURIOSITY)
                }
                btnParchi.setOnClickListener {
                    disableCategory(MarkerTypes.PARKS)
                }
                btnEpoche.setOnClickListener {
                    disableCategory(MarkerTypes.AGES)
                }
            }

            btnCategorySelect.setOnClickListener {
                if(layoutCategories.visibility == View.VISIBLE){
                    layoutCategories.visibility = View.GONE
                }else {
                    layoutCategories.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun disableCategory(type: MarkerTypes){ // TODO: Refactor disableCategory Logic
        when(type) {
            MarkerTypes.MONUMENTS -> {
                if (monumentiEnabled) {
                    for (marker in ListaMonumenti) {
                        pointAnnotationManager?.delete(marker)
                    }
                } else {
                    for (marker in ListaMonumenti) {
                        val options = PointAnnotationOptions().withPoint(marker.point).withIconImage(bitmapFromDrawableRes(this@MainActivity, R.drawable.green_marker)!!)
                        pointAnnotationManager?.create(options)
                    }
                }
                monumentiEnabled = !monumentiEnabled
            }
            MarkerTypes.CTM-> {
                if (ctmEnabled) {
                    for (marker in ListaCTM) {
                        pointAnnotationManager?.delete(marker)
                    }
                } else {
                    for (marker in ListaCTM) {
                        val options = PointAnnotationOptions().withPoint(marker.point).withIconImage(bitmapFromDrawableRes(this@MainActivity, R.drawable.green_marker)!!)
                        pointAnnotationManager?.create(options)
                    }
                }
                ctmEnabled = !ctmEnabled
            }
            MarkerTypes.CURIOSITY-> {
                if (curiositaEnabled) {
                    for (marker in ListaCuriosita) {
                        pointAnnotationManager?.delete(marker)
                    }
                } else {
                    for (marker in ListaCuriosita) {
                        val options = PointAnnotationOptions().withPoint(marker.point).withIconImage(bitmapFromDrawableRes(this@MainActivity, R.drawable.green_marker)!!)
                        pointAnnotationManager?.create(options)
                    }
                }
                curiositaEnabled = !curiositaEnabled
            }
            MarkerTypes.PARKS-> {
                if (parchiEnabled) {
                    for (marker in ListaParchi) {
                        pointAnnotationManager?.delete(marker)
                    }
                } else {
                    for (marker in ListaParchi) {
                        val options = PointAnnotationOptions().withPoint(marker.point).withIconImage(bitmapFromDrawableRes(this@MainActivity, R.drawable.green_marker)!!)
                        pointAnnotationManager?.create(options)
                    }
                }
                parchiEnabled = !parchiEnabled
            }
            MarkerTypes.AGES-> {
                if (epocheEnabled) {
                    for (marker in ListaEpoche) {
                        pointAnnotationManager?.delete(marker)
                    }
                } else {
                    for (marker in ListaEpoche) {
                        val options = PointAnnotationOptions().withPoint(marker.point).withIconImage(bitmapFromDrawableRes(this@MainActivity, R.drawable.green_marker)!!)
                        pointAnnotationManager?.create(options)
                    }
                }
                epocheEnabled = !epocheEnabled
            }
            MarkerTypes.NOTYPE -> {
                // TODO: Manage Notype case
            }
        }
    }


    /*    override fun onCreate(savedInstanceState: Bundle?) {

            ColorTagMap["Monumenti"] = ::onMonumentiClick
            ColorTagMap["CTM"] = ::onCTMClick
            ColorTagMap["Curiosita"] = ::onCuriositaClick
            ColorTagMap["Parchi"] = ::onParchiClick
            ColorTagMap["Epoche"] = ::onEpocheClick

            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            CurrentText = findViewById(R.id.NameText)
            CurrentButton = findViewById(R.id.buttonOther)

            val displayMetrics = DisplayMetrics()
            val windowManager = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            display= windowManager.defaultDisplay

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display.getRealMetrics(displayMetrics)
            } else {
                @Suppress("DEPRECATION")
                display.getMetrics(displayMetrics)
            }

            jsonString = loadJSONResource(this.applicationContext, R.raw.markers).toString()

            compactLayout = findViewById(R.id.compactLayout)
            coordinatorLayout = findViewById(R.id.coordinatorLayout)
            detailLayout = findViewById(R.id.relativeLayout)
            includeLayout = detailLayout.findViewById(R.id.compactLayout)
            layoutCategories = findViewById(R.id.layoutCategories)
            imageContainer = findViewById(R.id.imageContainer)
            imageLayout = findViewById(R.id.imageLayout)

            detailLayout.layoutParams.height = displayMetrics.heightPixels - 100

            coordinatorLayout.visibility = View.GONE
            detailLayout.visibility = View.GONE

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


            imageLayout.setOnClickListener {
                if(imageLayout.visibility == View.VISIBLE){
                    imageLayout.visibility = View.GONE
                }
            }

            coordinatorLayout.setOnTouchListener(object : View.OnTouchListener {
                private val MIN_SWIPE_DISTANCE = 100
                private val MAX_SWIPE_DURATION = 300

                private var startY: Float = 0f
                private var startTime: Long = 0

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
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
                                detailLayout.visibility = View.VISIBLE
                                coordinatorLayout.visibility = View.GONE
                            }

                            return true
                        }
                    }
                    return false
                }
            })


            detailLayout.setOnTouchListener(object : View.OnTouchListener {
                private val MIN_SWIPE_DISTANCE = 100
                private val MAX_SWIPE_DURATION = 3000

                private var startY: Float = 0f
                private var startTime: Long = 0

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
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
                                detailLayout.visibility = View.GONE
                                coordinatorLayout.visibility = View.VISIBLE
                            }

                            return true
                        }
                    }
                    return false
                }
            })

            val btnCenterMap: ImageButton = findViewById(R.id.btnCenterMap)
            btnCenterMap.setOnClickListener {
                centerMapOnUserPosition()
            }

            searchBarEditText = findViewById(R.id.searchBarEditText)

            searchBarEditText.doOnTextChanged{text, start, before, count ->

                if(text.isNullOrEmpty()){
                    for (marker in AllMarkers){
                        if(!(SearchResult.contains(marker))){
                            reenableMarker(marker)
                        }
                    }

                    SearchResult.clear()
                }
            }

            mapView?.setOnTouchListener{ _, _ ->
                if(coordinatorLayout.visibility == View.VISIBLE){
                    coordinatorLayout.visibility = View.GONE
                }
                if(detailLayout.visibility == View.VISIBLE){
                    detailLayout.visibility = View.GONE
                }
                false
            }

        }*/


    private fun reenableMarker(marker: PointAnnotation){
        val options = PointAnnotationOptions().withPoint(marker.point).withIconImage(bitmapFromDrawableRes(this@MainActivity, R.drawable.green_marker)!!)
        pointAnnotationManager?.create(options)
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
            mapView?.getMapboxMap()?.setCamera(
                com.mapbox.maps.CameraOptions.Builder()
                    .center(Point.fromLngLat(longitude, latitude))
                    .zoom(zoomLevel)
                    .build()
            )
            isFirstLocationUpdate = false
        }
    }

    private fun deletePositionMarker(marker: PointAnnotation) {
        pointAnnotationManager?.delete(marker)
        activeAnnotations.remove(marker.id.toString())
    }

    private fun addPositionToMap(lat: Double, lng: Double): PointAnnotation? {
        bitmapFromDrawableRes(
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
            bitmapFromDrawableRes(
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


    private fun bitmapFromDrawableRes(context: Context, @DrawableRes resId: Int): Bitmap? {
        val drawable: Drawable? = AppCompatResources.getDrawable(context, resId)
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap: Bitmap? = if ((drawable?.intrinsicWidth ?: 0) > 0 && (drawable?.intrinsicHeight
                ?: 0) > 0
        ) {
            Bitmap.createBitmap(
                drawable?.intrinsicWidth ?: 0,
                drawable?.intrinsicHeight ?: 0,
                Bitmap.Config.ARGB_8888
            )
        } else {
            null
        }

        if (bitmap != null) {
            val canvas = Canvas(bitmap)
            drawable?.setBounds(0, 0, canvas.width, canvas.height)
            drawable?.draw(canvas)
        }

        return bitmap
    }


    private fun onMarkerItemClick(marker: PointAnnotation) {

        binding.coordinatorLayout.root.isVisible = coordinatorLayout.visibility != View.VISIBLE

        val markerData = marker.getData()
        val type = markerData?.asJsonObject?.get("type").toString().toInt()

        when(type){
            MarkerTypes.MONUMENTS.ordinal -> onMarkerClick(marker,MarkerTypes.MONUMENTS)
            MarkerTypes.CTM.ordinal -> onMarkerClick(marker,MarkerTypes.CTM)
            MarkerTypes.CURIOSITY.ordinal -> onMarkerClick(marker,MarkerTypes.CURIOSITY)
            MarkerTypes.PARKS.ordinal -> onMarkerClick(marker,MarkerTypes.PARKS)
            MarkerTypes.AGES.ordinal -> onMarkerClick(marker,MarkerTypes.AGES)
            else -> onMiscClick(marker)
        }

    }
    
    private fun onMiscClick(marker: PointAnnotation) {
        if (coordinatorLayout.visibility != View.VISIBLE) {
            coordinatorLayout.visibility = View.VISIBLE
        }

        val markerData = marker.getData()
        updateDetailViews(markerData, Color.parseColor("#6e6e6e"))
    }

    private fun updateDetailViews(markerData: JsonElement?, color: Int) {

        includeLayout = detailLayout.findViewById(R.id.compactLayout)
        imageContainer = detailLayout.findViewById(R.id.imageContainer)

        val name = markerData?.asJsonObject?.get("markerName").toString().removeSurrounding("\"")
        val description = markerData?.asJsonObject?.get("description").toString().removeSurrounding("\"")
        val tagsString = markerData?.asJsonObject?.get("tags")?.asString
        val imageString = markerData?.asJsonObject?.get("images")?.asString
        val imageArray = imageString?.split(", ")
        val tags = tagsString?.split(", ")?.joinToString(", ")
        val mainTag = markerData?.asJsonObject?.get("mainTag").toString().removeSurrounding("\"")

        colorChangeBackground = coordinatorLayout.background as GradientDrawable
        colorChangeBackground.setColor(color)

        CurrentButton = coordinatorLayout.findViewById(R.id.buttonOther)
        colorChangeBackground = CurrentButton.background as GradientDrawable
        colorChangeBackground.setColor(color)
        CurrentButton = coordinatorLayout.findViewById(R.id.buttonWeb)
        colorChangeBackground = CurrentButton.background as GradientDrawable
        colorChangeBackground.setColor(color)
        CurrentButton = coordinatorLayout.findViewById(R.id.buttonTravel)
        colorChangeBackground = CurrentButton.background as GradientDrawable
        colorChangeBackground.setColor(color)
        CurrentButton = coordinatorLayout.findViewById(R.id.buttonCall)
        colorChangeBackground = CurrentButton.background as GradientDrawable
        colorChangeBackground.setColor(color)

        val something: RelativeLayout = detailLayout.findViewById(R.id.coordinatorLayout)

        colorChangeBackground = something.background as GradientDrawable
        colorChangeBackground.setColor(color)

        CurrentButton = includeLayout.findViewById(R.id.buttonOther)
        colorChangeBackground = CurrentButton.background as GradientDrawable
        colorChangeBackground.setColor(color)
        CurrentButton = includeLayout.findViewById(R.id.buttonWeb)
        colorChangeBackground = CurrentButton.background as GradientDrawable
        colorChangeBackground.setColor(color)
        CurrentButton = includeLayout.findViewById(R.id.buttonTravel)
        colorChangeBackground = CurrentButton.background as GradientDrawable
        colorChangeBackground.setColor(color)
        CurrentButton = includeLayout.findViewById(R.id.buttonCall)
        colorChangeBackground = CurrentButton.background as GradientDrawable
        colorChangeBackground.setColor(color)

        CurrentText = coordinatorLayout.findViewById(R.id.NameText)
        CurrentText.text = name

        CurrentText = includeLayout.findViewById(R.id.NameText)
        CurrentText.text = name


        for(i in 0 until imageArray!!.size){
            val imageView = ImageView(this, )

            DownloadImageTask(imageView).execute(imageArray[i])

            val fullScreenImag: ImageView = findViewById(R.id.imageFullScreen)

            imageContainer.addView(imageView)

            imageView.setOnClickListener {
                imageLayout.visibility = View.VISIBLE
                DownloadImageTask(fullScreenImag).execute(imageArray[i])
            }

            imageView.layoutParams.height = 500
            imageView.layoutParams.width = 500
            imageView.setPadding(10, 0, 10, 0)
        }


        CurrentText = detailLayout.findViewById(R.id.detailDescription)
        CurrentText.text = description
        CurrentText = detailLayout.findViewById(R.id.detailTags)
        CurrentText.text = tags
        CurrentText = detailLayout.findViewById(R.id.detailMainTag)
        CurrentText.text = mainTag

    }

    private fun onMarkerClick(marker: PointAnnotation, type: MarkerTypes){
        // TODO: Refactor onMarkerClick Logic
        when(type) {
            MarkerTypes.MONUMENTS -> {
                if (coordinatorLayout.visibility != View.VISIBLE) {
                    coordinatorLayout.visibility = View.VISIBLE
                }

                val markerData = marker.getData()

                updateDetailViews(markerData, Color.parseColor("#0000FF"))
            }
            MarkerTypes.CTM-> {
                if (coordinatorLayout.visibility != View.VISIBLE) {
                    coordinatorLayout.visibility = View.VISIBLE
                }

                val markerData = marker.getData()
                updateDetailViews(markerData,Color.parseColor("#FF0000"))
            }
            MarkerTypes.CURIOSITY-> {
                if (coordinatorLayout.visibility != View.VISIBLE) {
                    coordinatorLayout.visibility = View.VISIBLE
                }

                val markerData = marker.getData()
                updateDetailViews(markerData, Color.parseColor("#FFFF00"))
            }
            MarkerTypes.PARKS-> {
                if (coordinatorLayout.visibility != View.VISIBLE) {
                    coordinatorLayout.visibility = View.VISIBLE
                }

                val markerData = marker.getData()
                updateDetailViews(markerData, Color.parseColor("#00FF00"))
            }
            MarkerTypes.AGES-> {
                if (coordinatorLayout.visibility != View.VISIBLE) {
                    coordinatorLayout.visibility = View.VISIBLE
                }

                val markerData = marker.getData()
                updateDetailViews(markerData, Color.parseColor("#FF00FF"))
            }
            MarkerTypes.NOTYPE -> {
                // TODO: Manage Notype case
            }
        }
    }





    // TODO: Review Localization Logic
    private fun centerMapOnUserPosition() {
        mapView?.getMapboxMap()?.setCamera(
            com.mapbox.maps.CameraOptions.Builder()
                .center(Point.fromLngLat(longitude, latitude))
                .zoom(13.0)
                .build()
        )
    }

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
            .setTitle("Location Service Disabled")
            .setMessage("This app requires the location service to be enabled. Please enable the location service to proceed.")
            .setPositiveButton("Enable") { _, _ ->
                openLocationSettings()
            }
            .setNegativeButton("Cancel") { _, _ ->
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
            .setTitle("Location Permission Required")
            .setMessage("This app requires access to your device's location. Please grant the location permission in the app settings.")
            .setPositiveButton("App Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Close App") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
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








    // TODO: Refactor Search Logic
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
        for (markerElement in AllMarkers) {
            searchName(prompt, markerElement)
            searchTag(prompt, markerElement)
        }

        for (deleteMarker in AllMarkers){
            if(!(SearchResult.contains(deleteMarker))){
                deletePositionMarker(deleteMarker)
            }
        }

        SearchResultCameraPosition()
    }
}
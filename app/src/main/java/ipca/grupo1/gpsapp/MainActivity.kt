package ipca.grupo1.gpsapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import ipca.grupo1.gpsapp.databinding.ActivityMainBinding
import retrofit2.Response
import javax.security.auth.callback.Callback

class MainActivity : AppCompatActivity(), PermissionsListener,
    LocationEngineListener, MapboxMap.OnMapClickListener {

    private lateinit var binding : ActivityMainBinding
    private lateinit var map: MapboxMap
    private lateinit var permissionManager: PermissionsManager
    private lateinit var  originLocation : Location
    private lateinit var  originPosition : Point
    private lateinit var  destinationPosition : Point

    var mapStyle = STREETS

    private val tag = "MainActivity"

    private var  navigationMapRoute : NavigationMapRoute? = null
    private var destinationMarker : Marker? = null
    private var locationEngine : LocationEngine? = null
    private var locationLayerPlugin : LocationLayerPlugin? = null

    val accessToken = BuildConfig.ACCESS_TOKEN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Mapbox.getInstance(this, accessToken)
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync{ mapboxMap ->

            map = mapboxMap
            map.addOnMapClickListener(this)
            enableLocation()

            binding.buttonChangeStyle.setOnClickListener {

                when(mapStyle){

                    STREETS -> {

                        mapStyle = OUTDOORS

                    }

                    OUTDOORS -> {

                        mapStyle = LIGHT

                    }

                    LIGHT -> {

                        mapStyle = DARK

                    }

                    DARK -> {

                        mapStyle = SATELLITE

                    }

                    SATELLITE -> {

                        mapStyle = SATELLITE_STREETS

                    }

                    SATELLITE_STREETS -> {

                        mapStyle = NAVIGATION_DAY

                    }

                    NAVIGATION_DAY -> {

                        mapStyle = NAVIGATION_NIGHT

                    }

                    NAVIGATION_NIGHT -> {

                        mapStyle = STREETS

                    }

                }

                binding.mapView.setStyleUrl(mapStyle)

            }

            binding.buttonStartNavigation.setOnClickListener {

                val options = NavigationLauncherOptions.builder()
                    .origin(originPosition)
                    .destination(destinationPosition)
                    .shouldSimulateRoute(true)
                    .build()

                NavigationLauncher.startNavigation(this, options)

            }

        }
    }

    private fun enableLocation() {

        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            initializeLocationEngine()
            initializeLocationLayer()

        } else {

            permissionManager = PermissionsManager(this)
            permissionManager.requestLocationPermissions(this)

        }


    }

    @SuppressWarnings("MissingPermission")
    private fun initializeLocationEngine(){

        locationEngine = LocationEngineProvider(this).obtainBestLocationEngineAvailable()
        locationEngine?.priority = LocationEnginePriority.HIGH_ACCURACY
        locationEngine?.activate()

        val lastLocation = locationEngine?.lastLocation
        if (lastLocation != null){

            originLocation = lastLocation
            setCameraPosition(lastLocation)

        }else {

            locationEngine?.addLocationEngineListener(this)

        }

    }

    private fun setCameraPosition(location: Location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(location.latitude, location.longitude), 13.0))
    }

    override fun onMapClick(point: LatLng) {

        if(destinationMarker != null)
            map.removeMarker(destinationMarker!!)

        destinationMarker = map.addMarker(MarkerOptions().position(point))

        destinationPosition = Point.fromLngLat(point.longitude, point.latitude)
        originPosition = Point.fromLngLat(originLocation.longitude, originLocation.latitude)
        getRoute(originPosition, destinationPosition)

        binding.buttonStartNavigation.isEnabled = true
        binding.buttonStartNavigation.setBackgroundResource(R.color.mapboxBlue)

    }

    private fun getRoute(origin: Point, destination: Point){

        NavigationRoute.builder()
            .accessToken(accessToken)
            .origin(origin)
            .destination(destination)
            .build()
            .getRoute(
                object : Callback, retrofit2.Callback<DirectionsResponse> {

                    override fun onFailure(call: retrofit2.Call<DirectionsResponse>, t: Throwable) {
                    }

                    override fun onResponse(
                        call: retrofit2.Call<DirectionsResponse>,
                        response: Response<DirectionsResponse>
                    ) {
                        Log.d(tag, "Response: ${response.body()}")

                        if(navigationMapRoute != null){
                            navigationMapRoute!!.removeRoute()
                        }else{
                            navigationMapRoute = NavigationMapRoute(null, binding.mapView, map)
                        }

                        val currentRoute = response.body()?.routes()?.get(0)
                        navigationMapRoute!!.addRoute(currentRoute)

                    }

                }
            )


    }

    @SuppressWarnings("MissingPermission")
    private fun initializeLocationLayer(){

        locationLayerPlugin = LocationLayerPlugin(binding.mapView, map, locationEngine)
        locationLayerPlugin?.setLocationLayerEnabled(true)
        locationLayerPlugin?.cameraMode = CameraMode.TRACKING
        locationLayerPlugin?.renderMode = RenderMode.NORMAL

    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        TODO("Not yet implemented")
    }

    override fun onPermissionResult(granted: Boolean) {
        if(granted)
            enableLocation()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)

    }

    override fun onLocationChanged(location: Location?) {
        location?.let {

            originLocation = location
            setCameraPosition(location)

        }
    }

    @SuppressWarnings("MissingPermission")
    override fun onConnected() {

        locationEngine?.requestLocationUpdates()

    }

    @SuppressWarnings("MissingPermission")
    override fun onStart() {
        super.onStart()

        if(PermissionsManager.areLocationPermissionsGranted(this)){

            locationEngine?.requestLocationUpdates()
            locationLayerPlugin?.onStart()

        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val res = checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
            if (res != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 123)
            }
        }

        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        locationEngine?.removeLocationUpdates()
        locationLayerPlugin?.onStop()
        binding.mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationEngine?.deactivate()
        binding.mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        if(outState != null){

            binding.mapView.onSaveInstanceState(outState)

        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    companion object{

        const val STREETS = "mapbox://styles/mapbox/streets-v11"
        const val OUTDOORS = "mapbox://styles/mapbox/outdoors-v11"
        const val LIGHT = "mapbox://styles/mapbox/light-v10"
        const val DARK = "mapbox://styles/mapbox/dark-v10"
        const val SATELLITE = "mapbox://styles/mapbox/satellite-v9"
        const val SATELLITE_STREETS = "mapbox://styles/mapbox/satellite-streets-v11"
        const val NAVIGATION_DAY = "mapbox://styles/mapbox/navigation-day-v1"
        const val NAVIGATION_NIGHT = "mapbox://styles/mapbox/navigation-night-v1"

    }

}

package com.mycahkrason.streetfinds

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Window
import android.widget.ImageView
import android.widget.Toast
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_maps.*
import kotlinx.android.synthetic.main.description_modal.*
import java.util.*
import kotlin.collections.HashMap
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    //Reference my modal
    lateinit var myModal : Dialog

    //Reference Ad
    lateinit var mAdView : AdView

    private lateinit var mMap: GoogleMap
    var myPosition:LatLng? = null
    var userID = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        //Admob Stuff
        MobileAds.initialize(this, "App ID")

        mAdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        FirebaseDatabase.getInstance().reference.child("Users").addValueEventListener(object: ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {
                //
            }

            override fun onDataChange(p0: DataSnapshot) {
                mMap.clear()
                loadVendorAnnotationsFromFB()

                Log.d("CHANGE", "Something changed")
            }

        })

        loadVendorAnnotationsFromFB()

        ///////////////
        /// BUTTONS ///
        ///////////////

        centerLocationBtn.setOnClickListener {
            getUserLocation()
        }

        mapInfoBtn.setOnClickListener {

            //Create alert to tell user to enter email and password
            val builder = AlertDialog.Builder(this@MapsActivity, android.R.style.ThemeOverlay_Material_Dark)
            // Display a message on alert dialog
            builder.setMessage("Description")
            // Display a neutral button on alert dialog
            builder.setNeutralButton("Dismiss") { _, _ ->
                //Do nothing
            }
            // Finally, make the alert dialog using builder
            val dialog: AlertDialog = builder.create()
            // Display the alert dialog on app interface
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.parseColor("#FFFC79"))

        }

        mapBackBtn.setOnClickListener {

            super.finish()

        }

    }

    override fun onMarkerClick(p0: Marker?): Boolean {
//        Log.d("SNAPIN", "This is one of the markers\n\n${p0?.title}\n\n")
        //TODO: create modal and use the p0.title to grab the userID which will let you grab the user image, name and description
        if(p0?.title != null){
            //Make the modal
            myModal = Dialog(this@MapsActivity)
            myModal.requestWindowFeature(Window.FEATURE_NO_TITLE)
            myModal.setContentView(R.layout.description_modal)

            val modalImage: ImageView = myModal.findViewById(R.id.modalImage)
            modalImage.clipToOutline = true
            FirebaseDatabase.getInstance().reference.child("Users").child(p0.title).addListenerForSingleValueEvent(object: ValueEventListener{
                override fun onCancelled(p0: DatabaseError) {
                    //
                }

                override fun onDataChange(p0: DataSnapshot) {

                    //make this a hashmap and get the vendor name, description and photo
                    val vendorInfo = p0.value as HashMap<String, Any>

                    val profileImageURL = vendorInfo["VendorImage"]
                    if(profileImageURL != null) {
                        Picasso.get().load(profileImageURL.toString()).into(modalImage)
                    }


                    //Check to make sure there is a description
                    val vendorName = vendorInfo["VendorName"]
                    if (vendorName != null) {
                        myModal.vendorModalUserName.text = vendorName.toString()
                    } else {
                        myModal.vendorModalUserName.text = "Wierd"
                    }

                    //Check to make sure there is a description
                    val desc = vendorInfo["VendorDescription"]
                    if (desc != null) {
                        myModal.vendorModalDescription.text = desc.toString()
                    } else {
                        myModal.vendorModalDescription.text = "i guess it's null"
                    }

                    myModal.getDirectionsBtn.setOnClickListener {

                        var coordinates = vendorInfo["Coordinate"] as MutableList<Double>
                        val lat = coordinates[0]
                        val lon = coordinates[1]

                        var intent = Intent(android.content.Intent.ACTION_VIEW, Uri.parse("http://maps.google.com/maps?daddr=$lat,$lon"))

                        this@MapsActivity.startActivity(intent)
                    }

                }

            })

            //Show the modal
            myModal.show()
        }



        return true
    }

    var ACCESSLOCATION = 123
    fun checkPermission(){
        if(Build.VERSION.SDK_INT >= 23){
            if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), ACCESSLOCATION)
                return
            }
        }

        getUserLocation()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode){
            ACCESSLOCATION -> {
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED ){
                    getUserLocation()
                }else{
                    Toast.makeText(this@MapsActivity, "Unable to find your location", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun getUserLocation(){

        var myLocation = MyLocationListener()
        var locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3, 3f, myLocation)

        mMap.uiSettings.isMyLocationButtonEnabled = false
        mMap.setMyLocationEnabled(true)

        // Getting LocationManager object from System Service LOCATION_SERVICE


        // Creating a criteria object to retrieve provider
        val criteria = Criteria()

        // Getting the name of the best provider
        val provider = locationManager.getBestProvider(criteria, true)

        // Getting Current Location
        val location = locationManager.getLastKnownLocation(provider)

        if (location != null) {
            // Getting latitude of the current location
            val latitude = location.latitude

            // Getting longitude of the current location
            val longitude = location.longitude

            // Creating a LatLng object for the current location

            myPosition = LatLng(latitude, longitude)

            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myPosition, 16f))

        }

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        checkPermission()

    }


    var location : Location ?= null
    //Get user location
    inner class MyLocationListener: LocationListener {


        override fun onLocationChanged(p0: Location?) {
            location = p0
            //TODO: Update firebase with the location - this code will be used when vendor activates themself
            Log.d("Location", "\n\n${location!!.latitude} and ${location!!.longitude}\n\n Maps Profile")
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            //
        }

        override fun onProviderEnabled(provider: String?) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onProviderDisabled(provider: String?) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

    }

    //////////////////////////////
    /// Get Vendor Annotations ///
    //////////////////////////////

    fun loadVendorAnnotationsFromFB(){

        FirebaseDatabase.getInstance().reference.child("Users").addListenerForSingleValueEvent(object: ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {
                //
            }

            override fun onDataChange(p0: DataSnapshot) {

                val userSnapshot = p0.children
                userSnapshot.forEach {

                    if(it.hasChild("Coordinate")){
                        //Log.d("SNAP", "\n\n${it.value}\n\n")

                        val vendorInfo = it.value as HashMap<String, String>
                        val isActive = vendorInfo["isActive"] as Boolean
                        val isThisYou = vendorInfo["UserID"]
                        if(isActive && isThisYou != userID){
                            //TODO:This user needs to be visible, add an annotation to the map
                            //get the vendors lat and lon
                            var coordinateInfo = vendorInfo["Coordinate"] as MutableList<*>

                            var vendorLat = coordinateInfo[0] as Double
                            var vendorLon = coordinateInfo[1] as Double
                            var vendorLocation = LatLng(vendorLat, vendorLon)
                            //place an annotation
                            //Log.d("SNAPIN", "\n\n\n\nLat = $vendorLat \nLon = $vendorLon\n\n\n\n")

                            fun resizeMapIcons(iconName: String, width: Int, height: Int): Bitmap{
                                val imageBitmap = BitmapFactory.decodeResource(resources, resources.getIdentifier(iconName, "drawable", packageName))
                                val resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false)
                                return resizedBitmap
                            }

                            mMap.setOnMarkerClickListener(this@MapsActivity)

                            var marker : Marker = mMap.addMarker(MarkerOptions().position(vendorLocation)
                                    .title("$isThisYou")
                                    .icon(BitmapDescriptorFactory.fromBitmap(resizeMapIcons("map_icon", 115, 115))))



                        }else{

                        }
                    }
                }


            }



        })

    }
}

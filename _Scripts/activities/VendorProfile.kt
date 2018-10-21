package com.mycahkrason.streetfinds

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_vendor_profile.*
import java.io.ByteArrayOutputStream

class VendorProfile : AppCompatActivity() {

    val userEmail = FirebaseAuth.getInstance().currentUser?.email
    val userID = FirebaseAuth.getInstance().currentUser?.uid
    var isUserActive : Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vendor_profile)


        vendorPictureDisplay.clipToOutline = true
        retrieveImageFromFirebase()

        ///////////////
        /// BUTTONS ///
        ///////////////

        activateSwitch.setOnClickListener {
            if(userID != null) {

                val locationInDB = FirebaseDatabase.getInstance().reference.child("Users").child(userID)

                if (activateSwitch.isChecked) {
                    Log.d("SWITCH", "WE ACTIVE")
                    //set the map
                    val activeMap = mapOf("isActive" to true)
                    locationInDB.updateChildren(activeMap)
                    checkLocationPermission()

                } else if (!activateSwitch.isChecked) {
                    Log.d("SWITCH", "WE NOT ACTIVE")
                    //set the map
                    val deactiveMap = mapOf("isActive" to false)
                    locationInDB.updateChildren(deactiveMap)
                }
            }
        }

        editDescriptionBtn.setOnClickListener {

            val intent = Intent(this, EditDescriptionActivity::class.java)
            startActivity(intent)

        }

        logoutBtn.setOnClickListener {
            if(userID != null){
                val locationInDB = FirebaseDatabase.getInstance().reference.child("Users").child(userID)

                //set the map
                val activeMap = mapOf("isActive" to false)
                locationInDB.updateChildren(activeMap)
            }

            super.finish()
        }

        vendorPictureDisplay.setOnClickListener {
            checkPermission()
        }

        viewMapBtn.setOnClickListener {

            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)

        }

        vendorInfoBtn.setOnClickListener {

            //Create alert to tell user to enter email and password
            val builder = AlertDialog.Builder(this@VendorProfile, android.R.style.ThemeOverlay_Material_Dark)
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
    }

    //    *************************
    //    ****** Image Stuff ******
    //    *************************

    //check that you can access the users images
    val READIMAGE: Int = 123
    fun checkPermission(){
        if(Build.VERSION.SDK_INT >= 23){
            if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){

                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), READIMAGE)
                return
            }
        }

        loadImage()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        if(requestCode == 123){

            when(requestCode){
                READIMAGE -> {
                    if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                        loadImage()
                    }else{
                        Toast.makeText(this, "Cannot access your images", Toast.LENGTH_SHORT).show()
                    }
                }

            }
        }else if(requestCode == 456){

            when(requestCode){

                ACCESSLOCATION -> {
                    if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                        getUserLocation()
                    }else{
                        Toast.makeText(this, "Cannot access your location", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }



        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    //UpLoad the image
    val PICK_IMAGE_CODE = 456
    fun loadImage(){
        val picturesIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(picturesIntent, PICK_IMAGE_CODE)
    }

    //This will be called once the pictureIntent is done
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode == PICK_IMAGE_CODE && resultCode == Activity.RESULT_OK && data != null){
            //Get data for the image
            val selectedImage = data.data
            val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = contentResolver.query(selectedImage, filePathColumn, null, null, null)
            cursor.moveToFirst()
            val columnIndex = cursor.getColumnIndex(filePathColumn[0])
            val picturePath = cursor.getString(columnIndex)
            cursor.close()

            vendorPictureDisplay.setImageBitmap(BitmapFactory.decodeFile(picturePath))

            //Save image
            saveImageInFirebase()

        }
    }

    //Save image to firebase after it has been chosen
    fun saveImageInFirebase(){

        //set up the Storage reference
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.getReferenceFromUrl("Url")

        val imagePath = userID
        val imageRef = storageRef.child(imagePath.toString())
        vendorPictureDisplay.isDrawingCacheEnabled = true
        vendorPictureDisplay.buildDrawingCache()

        val drawable = vendorPictureDisplay.drawable as BitmapDrawable
        val bitmap = drawable.bitmap
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val data = baos.toByteArray()

        val uploadTask = imageRef.putBytes(data)
        uploadTask.addOnFailureListener{
            Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
        }.addOnSuccessListener {taskSnapshot ->

            //TODO this shot broke for some reason
            val downloadURL = taskSnapshot.downloadUrl.toString()

            Log.d("Image Download", "\n\n$downloadURL is the URI\n\n")
            //save Url to Firebase
            val map = mapOf("VendorImage" to downloadURL)
            if(userID != null){
                FirebaseDatabase.getInstance().reference.child("Users").child(userID).updateChildren(map)
            }
        }
    }

    //Get the image and show it (This also just grabs the info)
    fun retrieveImageFromFirebase(){
        if(userID != null){
            FirebaseDatabase.getInstance().reference.child("Users").child(userID).addValueEventListener(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    //
                }

                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    try{

                        val profileInfo = dataSnapshot.value as HashMap<*, *>

                        val profileImageURL = profileInfo["VendorImage"]
                        val profileDescription = profileInfo["VendorDescription"]
                        val profileName = profileInfo["VendorName"]
                        val profileActive = profileInfo["isActive"]

                        if(profileActive != null){
                            profileActive as Boolean
                            if(profileActive){
                                isUserActive = profileActive
                                activateSwitch.isChecked = true
                                getUserLocation()
                            }else if(!profileActive){
                                isUserActive = profileActive
                                activateSwitch.isChecked = false
                            }
                        }

                        if(profileDescription != null) {
                            vendorDescriptionDisplay.text = profileDescription.toString()
                        }

                        if(profileImageURL != null) {
                            Picasso.get().load(profileImageURL.toString()).into(vendorPictureDisplay)
                            vendorPictureDisplay.clipToOutline = true
                        }

                        if(profileName != null){
                            vendorNameDisplay.text = profileName.toString()
                        }

                    }catch (ex: Exception){}
                }

            })
        }

    }

    //    ****************************
    //    ****** Location Stuff ******
    //    ****************************

    var ACCESSLOCATION = 456
    fun checkLocationPermission(){
        if(Build.VERSION.SDK_INT >= 23){
            if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), ACCESSLOCATION)
                return
            }
        }

        getUserLocation()
    }

    fun getUserLocation(){

        var myLocation = MyLocationListener()
        var locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3, 3f, myLocation)
//
//        // Getting LocationManager object from System Service LOCATION_SERVICE
//
//
//        // Creating a criteria object to retrieve provider
//        val criteria = Criteria()
//
//        // Getting the name of the best provider
//        val provider = locationManager.getBestProvider(criteria, true)
//
//        // Getting Current Location
//        val location = locationManager.getLastKnownLocation(provider)
//
//        if (location != null) {
//            // Getting latitude of the current location
//            val latitude = location.latitude
//
//            // Getting longitude of the current location
//            val longitude = location.longitude
//            Log.d("LocationInfo", "\n\n$latitude and $longitude \n\n")
//
//        }

    }


    var location : Location?= null
    //Get user location
    inner class MyLocationListener: LocationListener {


        override fun onLocationChanged(p0: Location?) {
            location = p0
            Log.d("Location", "\n\n${location!!.latitude} and ${location!!.longitude}\n\n Vendor Profile")
            if(userID != null){
                if(isUserActive != null && isUserActive == true) {
                    var latitude = location!!.latitude
                    var longitude = location!!.longitude
                    var coordinateMap = mapOf("0" to latitude, "1" to longitude)
                    FirebaseDatabase.getInstance().reference.child("Users").child(userID).child("Coordinate").updateChildren(coordinateMap)
                }
            }

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

}

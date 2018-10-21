package com.mycahkrason.streetfinds

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Switch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.android.synthetic.main.activity_login.*

class LoginActivity : AppCompatActivity() {

    lateinit var email : String
    lateinit var password : String

    var tabCount = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val rememberMeSwitch = findViewById<Switch>(R.id.rememberMeToggleBtn)

        //Get Preferences
        val preferences = this.getSharedPreferences("LoginInformation", android.content.Context.MODE_PRIVATE)
        val emailPref = preferences.getString("Email", null)
        val passwordPref = preferences.getString("Password", null)
        val wasCheckedPref = preferences.getBoolean("WasChecked", false)

        rememberMeSwitch.isChecked = wasCheckedPref

        if(emailPref != null){
            //set the email text
            emailLoginInput.setText(emailPref)
        }

        if(passwordPref != null){
            //set the password text
            passwordLoginInput.setText(passwordPref)
        }


        //toggle btns at the top
        vendorTab.setOnClickListener {
            //Change background
            vendorTab.setBackgroundColor(Color.parseColor("#D6D6D6"))
            customerTab.setBackgroundColor(Color.parseColor("#000000"))

            //Change Text color
            vendorTab.setTextColor(Color.parseColor("#000000"))
            customerTab.setTextColor(Color.parseColor("#D6D6D6"))



            tabCount = 2
        }

        customerTab.setOnClickListener {
            //Change background
            customerTab.setBackgroundColor(Color.parseColor("#D6D6D6"))
            vendorTab.setBackgroundColor(Color.parseColor("#000000"))

            //Change Text color
            customerTab.setTextColor(Color.parseColor("#000000"))
            vendorTab.setTextColor(Color.parseColor("#D6D6D6"))

            tabCount = 1
        }

        signUpLoginBtn.setOnClickListener {

            email = emailLoginInput.text.toString()
            password = passwordLoginInput.text.toString()

            if(rememberMeSwitch.isChecked){
                //Save user info
                Log.d("LoginActivity", "Checked on")
                val editor = preferences.edit()
                editor.putString("Email", email)
                editor.putString("Password", password)
                editor.putBoolean("WasChecked", true)
                editor.apply()

            }else{
                //Save user default
                Log.d("LoginActivity", "Checked off")
                val editor = preferences.edit()
                editor.putString("Email", null)
                editor.putString("Password", null)
                editor.putBoolean("WasChecked", false)
                editor.apply()
            }

            if(email.isEmpty() || password.isEmpty()){
                //Create alert to tell user to enter email and password
                val builder = AlertDialog.Builder(this@LoginActivity)
                // Display a message on alert dialog
                builder.setMessage("An Email and Password are required.")
                // Display a neutral button on alert dialog
                builder.setNeutralButton("Dismiss") { _, _ ->
                    //Do nothing
                }
                // Finally, make the alert dialog using builder
                val dialog: AlertDialog = builder.create()
                // Display the alert dialog on app interface
                dialog.show()

            }else {
                //First try to sign in, if this is unsuccessful, then create the user
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).addOnCompleteListener {

                    if(it.isSuccessful){
                        Log.d("LoginActivity", "User has signed in Successfully")
                        navigateCustomerOrVendor()
                    }else{
                        //If you are here, that means you are not an existing user
                        //Create user
                        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).addOnCompleteListener {

                            if(it.isSuccessful){

                                Log.d("LoginActivity", "Successfully Created user: ${it.result?.user?.uid}")

                                //Create a Users and UserInfo in Firebase
                                val user = FirebaseAuth.getInstance()
                                val userEmail = user.currentUser?.email
                                val userID = user.currentUser?.uid

                                val mapUserData = mapOf("Email" to userEmail,
                                        "UserID" to userID,
                                        "isActive" to false,
                                        "VendorName" to "Vendor Name",
                                        "VendorDescription" to "Describe what you sell or do.")
                                if(userID != null){
                                    FirebaseDatabase.getInstance().reference.child("Users").child(userID).updateChildren(mapUserData)

                                    navigateCustomerOrVendor()
                                }


                            }else if(!it.isSuccessful){
                                val e = it.exception?.localizedMessage.toString()
                                //Make an alert because a toast is lame
                                // Initialize a new instance of
                                val builder = AlertDialog.Builder(this@LoginActivity)
                                // Display a message on alert dialog
                                builder.setMessage("Message")
                                // Display a neutral button on alert dialog
                                builder.setNeutralButton("Dismiss") { _, _ ->
                                    //Do nothing
                                }
                                // Finally, make the alert dialog using builder
                                val dialog: AlertDialog = builder.create()
                                // Display the alert dialog on app interface
                                dialog.show()
                            }

                        }
                    }

                }
            }


        }

        loginInfoBtn.setOnClickListener {

            //Create alert to tell user to enter email and password
            val builder = AlertDialog.Builder(this@LoginActivity, android.R.style.Theme_Black_NoTitleBar)
            // Display a message on alert dialog
            builder.setMessage("Privacy Policy")
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

        passwordLoginInput.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                hideKeyBoard(v)
            }
        }

        emailLoginInput.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                hideKeyBoard(v)
            }
        }

    }

    fun navigateCustomerOrVendor(){
        //this determines whether the user is directed to the Vendor profile or the Map
        if(tabCount == 1){
            //Launch Map activity
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
        }else if(tabCount == 2){
            val intent = Intent(this, VendorProfile::class.java)
            startActivity(intent)
        }
    }

    //hide the keyboard
    fun hideKeyBoard(v : View){
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        if(inputManager.isAcceptingText){
            inputManager.hideSoftInputFromWindow(v.windowToken, 0)
        }

    }
}

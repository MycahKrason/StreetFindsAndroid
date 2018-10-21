package com.mycahkrason.streetfinds

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.activity_edit_description.*

class EditDescriptionActivity : AppCompatActivity() {

    lateinit var descriptionInput : EditText
    var userID : String? = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_description)

        //Get userEmail
        userID = FirebaseAuth.getInstance().currentUser?.uid

        //grab editText view
        descriptionInput = findViewById(R.id.descriptionInput)
        descriptionInput.setHorizontallyScrolling(false)
        descriptionInput.setMaxLines(Integer.MAX_VALUE)

        retrieveDescription()

        descriptionBackBtn.setOnClickListener {

            super.finish()

        }

        //make message send when pressing keyboard enter button
        descriptionInput.setOnEditorActionListener(TextView.OnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                updateDescription()
                return@OnEditorActionListener true
            }
            false
        })
    }

    fun updateDescription(){

        if (descriptionInput.text.length < 145) {

            if(userID != null){
                //Save the Vendor Name
                val nameAndDescriptionMap = mapOf("VendorName" to vendorNameInput.text.toString(), "VendorDescription" to descriptionInput.text.toString())
                FirebaseDatabase.getInstance().reference.child("Users").child(userID!!).updateChildren(nameAndDescriptionMap)

            }

            //Return back to the Profile page
            startActivity(Intent(this@EditDescriptionActivity, VendorProfile::class.java))

        }else{
            Toast.makeText(this@EditDescriptionActivity, "Description must be less than 145 characters", Toast.LENGTH_LONG).show()
        }
    }

    fun retrieveDescription() {

        if (userID != null) {
            FirebaseDatabase.getInstance().reference.child("Users").child(userID!!).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    //
                }

                override fun onDataChange(p0: DataSnapshot) {
                    try {

                        val profileInfo = p0.value as HashMap<String, String>

                        val profileDescription = profileInfo["VendorDescription"]
                        val profileVendorName = profileInfo["VendorName"]

                        if (profileDescription != null) {
                            descriptionInput.setText(profileDescription)
                        }

                        if (profileVendorName != null) {
                            vendorNameInput.setText(profileVendorName)
                        }

                    } catch (ex: Exception) {
                    }
                }

            })
        }
    }
}

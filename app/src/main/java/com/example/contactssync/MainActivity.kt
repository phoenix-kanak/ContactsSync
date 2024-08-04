package com.example.contactssync

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    private val PERMISSIONS_REQUEST_WRITE_CONTACTS = 100
    private lateinit var btnSyncContacts: Button
    private val db = FirebaseFirestore.getInstance()
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        window.statusBarColor = ContextCompat.getColor(this, R.color.status_bar_colour)
        supportActionBar?.title = "Contacts Sync"
        btnSyncContacts = findViewById(R.id.sync_button)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        btnSyncContacts.setOnClickListener {
            requestPermissions()
        }
    }

    private fun showPermissionsDeniedDialog() {
        android.app.AlertDialog.Builder(this).setTitle("Permissions Required")
            .setMessage(buildPermissionRationaleMessage()).setPositiveButton("Grant") { _, _ ->
                requestPermissions()
            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                handlePermissionDenied()
            }.show()
    }

    private fun buildPermissionRationaleMessage(): String {
        return "Read and Write contacts permission required for this app."
    }

    private fun requestPermissions() {
        val permissions = listOf(
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(
                permissionsToRequest.toTypedArray(), PERMISSIONS_REQUEST_WRITE_CONTACTS
            )
        } else {
            Log.e("tag123", "function called0")

            accessContacts()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_WRITE_CONTACTS) {
            val deniedPermissions = mutableListOf<String>()
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i])
                }
            }

            if (deniedPermissions.isNotEmpty()) {
                val showRationale = deniedPermissions.all {
                    ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }

                if (showRationale) {
                    showPermissionsDeniedDialog()
                } else {
                    handlePermissionDenied()
                }
            } else {
                Log.e("tag123", "function called")
                accessContacts()
            }
        }
    }

    private fun handlePermissionDenied() {
        android.app.AlertDialog.Builder(this).setTitle("Permissions Required")
            .setMessage("Permissions are required to use this app. Please enable them in app settings.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun accessContacts() {
        progressBar.visibility = ProgressBar.VISIBLE
        progressText.visibility = TextView.VISIBLE
        Log.e("tag123", "function called 1")
        db.collection("contacts").get().addOnSuccessListener { documents ->
            Log.e("tag123", "function called 2")

            var syncedContacts = 0
            val totalContacts = documents.size()
            progressBar.max = totalContacts

            runOnUiThread {
                val progress = progressBar.progress
                progressBar.visibility = ProgressBar.VISIBLE
                progressText.visibility = TextView.VISIBLE
            }
            Thread(Runnable {
                documents.forEach { document ->
                    Log.e("tag123", "function called 4")

                    val contacts = document.get("contacts") as? List<Map<*, *>>

                    contacts?.forEach { contact ->
                        val name = contact["name"] as? String ?: ""
                        val phone = contact["phone"] as? String ?: ""

                        if (name.isNotEmpty() && phone.isNotEmpty()) {
                            Log.e("tag123", "function called 5")

                            addContactToPhone(name, phone)
                            syncedContacts++

                            handler.post {
                                // Update the progress text and progress bar with the current number of synced contacts
                                progressText.text =
                                    "$syncedContacts out of ${contacts.size} contacts synced"
                                progressBar.progress = syncedContacts
                            }
                        }
                    }
                }

                // Hide progress indicators and update the final message
                handler.post {
                    progressBar.visibility = ProgressBar.GONE
                    progressText.text = "Sync completed. Synced $syncedContacts contacts."
//                    handler.postDelayed({
//                        progressBar.progress = 0
//                        progressText.text = ""
//                    }, 3000) // Delay of 3 seconds before resetting
                }
            }).start()
        }.addOnFailureListener { exception ->
            runOnUiThread {
                progressBar.visibility = ProgressBar.GONE
                progressText.visibility = TextView.GONE
                progressText.text = "Sync failed. Please try again."
            }
            Log.e("tag123", "$exception")
            Log.w(TAG, "Error getting documents: ", exception)
        }
    }

    private fun addContactToPhone(name: String, phone: String) {
        val contentResolver: ContentResolver = contentResolver

        val values = ContentValues().apply {
            put(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
            put(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
        }
        val rawContactUri = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, values)
        val rawContactId = rawContactUri?.lastPathSegment?.toLong()

        val nameValues = ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            put(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            )
            put(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, name)
        }
        contentResolver.insert(ContactsContract.Data.CONTENT_URI, nameValues)

        val phoneValues = ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            put(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            )
            put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
            put(
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            )
        }
        contentResolver.insert(ContactsContract.Data.CONTENT_URI, phoneValues)
    }
}
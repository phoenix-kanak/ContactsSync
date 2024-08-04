package com.example.contactssync

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private val PERMISSIONS_REQUEST_WRITE_CONTACTS = 100
    private lateinit var btnSyncContacts: Button
    private val db = FirebaseFirestore.getInstance()
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
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
        // Notify the user that permissions are required and cannot proceed
        android.app.AlertDialog.Builder(this).setTitle("Permissions Required")
            .setMessage("Permissions are required to use this app. Please enable them in app settings.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun accessContacts() {
        Log.e("tag123", "function called in")
        db.collection("contacts")
            .get()
            .addOnSuccessListener { documents ->
                var syncedContacts = 0
                val totalContacts = documents.size()
                progressBar.visibility = ProgressBar.VISIBLE
                progressText.visibility = TextView.VISIBLE
                Log.e("tag123", "function called1")
                for (document in documents) {
                    val contact = document.get("contact") as? Map<*, *>
                    val name = contact?.get("name") as? String ?: ""
                    val phone = contact?.get("number") as? String ?: ""

                    Log.e("tag123", "function inside")
                    Log.e("tag123", "$name")
                    Log.e("tag123", "$phone")
                    Log.e("tag123", "$document")


                    if (name.isNotEmpty() && phone.isNotEmpty()) {
                        addContactToPhone(name, phone)
                        syncedContacts++
                        progressBar.visibility = ProgressBar.VISIBLE
                        progressText.text = "Synced $syncedContacts of $totalContacts contacts"
                        Log.e("tag123", "function not empty")
                    }
                }
                progressBar.visibility = ProgressBar.GONE
                progressText.text = "Sync completed. Synced $syncedContacts contacts."
            }
            .addOnFailureListener { exception ->
                progressBar.visibility = ProgressBar.GONE
                progressText.visibility = TextView.GONE
                progressText.text = "Sync failed. Please try again."
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
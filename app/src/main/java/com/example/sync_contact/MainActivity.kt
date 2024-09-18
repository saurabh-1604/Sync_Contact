package com.example.sync_contact

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var syncButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var todaysDateTextView: TextView
    private val firestore = FirebaseFirestore.getInstance()
    private val CONTACTS_PERMISSION_CODE = 100
    private val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        syncButton = findViewById(R.id.syncButton)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        todaysDateTextView = findViewById(R.id.todaysDate)

        todaysDateTextView.text = "Today's Date: $today"

        syncButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_CONTACTS),
                    CONTACTS_PERMISSION_CODE
                )
            } else {
                syncContacts()
            }
        }
    }

    private fun syncContacts() {
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        progressBar.progress = 0
        progressText.text = "0%"

        firestore.collection("contacts")
            .whereEqualTo("dateAdded", today)
            .get()
            .addOnSuccessListener { documents ->
                val totalContacts = documents.size()
                var processedContacts = 0

                if (totalContacts > 0) {
                    for (document in documents) {
                        val firstName = document.getString("firstName")
                        val lastName = document.getString("lastName")
                        val mobileNumber = document.getString("mobileNumber")
                        addContactToDevice("$firstName $lastName", mobileNumber!!)
                        processedContacts++
                        val progress = (processedContacts * 100) / totalContacts
                        progressBar.progress = progress
                        progressText.text = "$progress%"
                    }
                } else {
                    progressText.text = "No contacts to sync"
                }

                progressBar.visibility = View.GONE
                progressText.visibility = View.GONE
                Toast.makeText(this, "Contacts Synced Successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                progressText.visibility = View.GONE
                Log.e("FirebaseError", "Error fetching contacts", e)
                Toast.makeText(this, "Failed to sync contacts", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addContactToDevice(name: String, phone: String) {
        val contentValues = ContentValues().apply {
            put(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
            put(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
        }

        val uri = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, contentValues)
        val rawContactId = uri?.lastPathSegment?.toLongOrNull()

        rawContactId?.let {
            val nameValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, it)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            }
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, nameValues)

            val phoneValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, it)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, phoneValues)
        }
    }
}

package app.people.contacts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.people.contacts.ui.ContactsApp
import app.people.contacts.ui.theme.ContactsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ContactsTheme {
                ContactsApp(intent, finish = ::finishWith)
            }
        }
    }

    /** Ends a request from another app, handing back [result] (or cancelling without one). */
    private fun finishWith(result: Uri?) {
        if (result != null) {
            setResult(RESULT_OK, Intent().setData(result).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }
}

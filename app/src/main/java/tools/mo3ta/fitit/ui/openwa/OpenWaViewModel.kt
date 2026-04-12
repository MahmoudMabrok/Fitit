package tools.mo3ta.fitit.ui.openwa

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class OpenWaViewModel : ViewModel() {
    var phoneNumber by mutableStateOf("")

    fun openWhatsApp(context: Context) {
        val sanitizedNumber = phoneNumber.filter { it.isDigit() }
        
        if (sanitizedNumber.isEmpty()) {
            Toast.makeText(context, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val url = "https://wa.me/$sanitizedNumber"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open WhatsApp: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

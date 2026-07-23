package com.pittapos.waiter

import android.content.Context

/** Διεύθυνση του POS στο WiFi του μαγαζιού + το PIN που ζητά το /api/orders — αποθηκευμένα στο κινητό. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("pitta_waiter", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var pin: String
        get() = prefs.getString(KEY_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PIN, value).apply()

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_PIN = "pin"
        // 10.0.2.2 = ψευδώνυμο του emulator για τον υπολογιστή-host· σε πραγματικό κινητό
        // αλλάζει στη ρύθμιση με το τοπικό IP του υπολογιστή του ταμείου (π.χ. 192.168.1.50).
        const val DEFAULT_URL = "http://10.0.2.2:5190"
    }
}

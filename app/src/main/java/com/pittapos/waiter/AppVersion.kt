package com.pittapos.waiter

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Η έκδοση της εφαρμογής, όπως τη γράφει το `versionName` στο build.gradle.kts — φαίνεται στην αρχική
 * οθόνη και στις Ρυθμίσεις, ώστε να ξεχωρίζει με μια ματιά ποιο κινητό πήρε το νέο APK και ποιο έμεινε
 * στο παλιό. Διαβάζεται από τον PackageManager και όχι από το BuildConfig, για να μη χρειαστεί να
 * ανοίξει το `buildConfig` feature μόνο γι' αυτό.
 */
fun appVersionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
} catch (e: PackageManager.NameNotFoundException) {
    ""
}

@Composable
fun rememberAppVersionName(): String {
    val context = LocalContext.current
    return remember(context) { appVersionName(context) }
}

package com.durrr.first.platform.di

import android.content.Context
import androidx.compose.runtime.Composable
import com.durrr.first.rememberAppDependencies
import com.durrr.first.ui.AppDependencies

@Composable
fun rememberAndroidPlatformDependencies(
    context: Context,
    launchScanner: () -> Unit,
    pickImage: ((String?) -> Unit) -> Unit = { onPicked -> onPicked(null) },
    pickDate: (initialIso: String?, onPicked: (String) -> Unit) -> Unit = { _, _ -> },
    shareText: (subject: String, payload: String) -> Unit = { _, _ -> },
    printHtml: (jobName: String, htmlPayload: String) -> Unit = { _, _ -> },
): AppDependencies {
    return rememberAppDependencies(
        context = context,
        launchScanner = launchScanner,
        pickImage = pickImage,
        pickDate = pickDate,
        shareText = shareText,
        printHtml = printHtml,
    )
}

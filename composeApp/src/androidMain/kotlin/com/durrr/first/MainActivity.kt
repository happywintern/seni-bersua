package com.durrr.first

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.durrr.first.features.auth.presentation.MobileLoginScreen
import com.durrr.first.features.setup.presentation.LocalFirstSetupScreen
import com.durrr.first.ui.design.AppTheme
import java.io.File
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private val viewModel: MainViewModel by viewModels()
    private var onImagePickedCallback: ((String?) -> Unit)? = null

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data?.getStringExtra(QrScannerActivity.EXTRA_RESULT)
        if (!data.isNullOrBlank()) {
            viewModel.setScannedToken(data)
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        onImagePickedCallback?.invoke(uri?.toString())
        onImagePickedCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashLogger()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AndroidAppContent(viewModel = viewModel, onLaunchScanner = {
                scannerLauncher.launch(Intent(this, QrScannerActivity::class.java))
            }, onPickImage = { onPicked ->
                onImagePickedCallback = onPicked
                imagePickerLauncher.launch(arrayOf("image/*"))
            }, onPickDate = { initialIso, onPicked ->
                launchDatePicker(initialIso = initialIso, onPicked = onPicked)
            }, onShareText = { subject, payload ->
                shareTextPayload(subject, payload)
            }, onPrintHtml = { jobName, htmlPayload ->
                printHtmlPayload(jobName, htmlPayload)
            })
        }
    }

    private fun launchDatePicker(
        initialIso: String?,
        onPicked: (String) -> Unit,
    ) {
        val calendar = Calendar.getInstance()
        val parts = initialIso?.trim()?.split("-").orEmpty()
        if (parts.size == 3) {
            val year = parts[0].toIntOrNull()
            val month = parts[1].toIntOrNull()
            val day = parts[2].toIntOrNull()
            if (year != null && month != null && day != null) {
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
                calendar.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, 31))
            }
        }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                onPicked(
                    String.format(
                        Locale.US,
                        "%04d-%02d-%02d",
                        year,
                        month + 1,
                        dayOfMonth,
                    ),
                )
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun installCrashLogger() {
        if (defaultExceptionHandler != null) return
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val crashFile = File(cacheDir, "last_crash.txt")
                crashFile.parentFile?.mkdirs()
                crashFile.writeText(
                    buildString {
                        appendLine("thread=${thread.name}")
                        appendLine("time=${System.currentTimeMillis()}")
                        appendLine()
                        appendLine(Log.getStackTraceString(throwable))
                    },
                )
            }
            Log.e("SuCashCrash", "Uncaught exception in thread=${thread.name}", throwable)
            defaultExceptionHandler?.uncaughtException(thread, throwable)
                ?: run { throw throwable }
        }
    }

    private fun shareTextPayload(subject: String, payload: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, payload)
        }
        startActivity(Intent.createChooser(sendIntent, subject))
    }

    private fun printHtmlPayload(jobName: String, htmlPayload: String) {
        val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            shareTextPayload(jobName, htmlPayload)
            return
        }
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val adapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(
                    jobName,
                    adapter,
                    PrintAttributes.Builder().build(),
                )
            }
        }
        webView.loadDataWithBaseURL(null, htmlPayload, "text/HTML", "UTF-8", null)
    }
}

@Composable
private fun AndroidAppContent(
    viewModel: MainViewModel,
    onLaunchScanner: () -> Unit,
    onPickImage: ((String?) -> Unit) -> Unit,
    onPickDate: (initialIso: String?, onPicked: (String) -> Unit) -> Unit,
    onShareText: (subject: String, payload: String) -> Unit,
    onPrintHtml: (jobName: String, htmlPayload: String) -> Unit,
) {
    val dependencies = rememberAppDependencies(
        context = LocalContext.current,
        launchScanner = onLaunchScanner,
        pickImage = onPickImage,
        pickDate = onPickDate,
        shareText = onShareText,
        printHtml = onPrintHtml,
    )
    val scope = rememberCoroutineScope()
    var localSetupComplete by remember {
        mutableStateOf(dependencies.settingsRepository.isLocalSetupComplete())
    }
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) {
            if (!localSetupComplete) {
                LocalFirstSetupScreen(
                    settingsRepository = dependencies.settingsRepository,
                    menuRepository = dependencies.menuRepository,
                    cashSessionRepository = dependencies.cashSessionRepository,
                    nowIso = dependencies.nowIso,
                    onComplete = {
                        dependencies.settingsRepository.clearActiveUser()
                        localSetupComplete = true
                        viewModel.markLoggedOut()
                    },
                )
            } else if (!viewModel.loggedIn) {
                MobileLoginScreen(
                    settingsRepository = dependencies.settingsRepository,
                    serverAuthRepository = dependencies.serverAuthRepository,
                    onLoginSuccess = { viewModel.markLoggedIn() },
                    onRequireSetup = {
                        dependencies.settingsRepository.clearActiveUser()
                        viewModel.markLoggedOut()
                        localSetupComplete = false
                    },
                    onBackgroundInfo = { info ->
                        viewModel.pushNotification(
                            title = "Auth",
                            message = info,
                        )
                    },
                )
            } else {
                AndroidAppScaffold(
                    dependencies = dependencies,
                    viewModel = viewModel,
                    onRequireLocalSetup = {
                        dependencies.settingsRepository.clearActiveUser()
                        viewModel.markLoggedOut()
                        localSetupComplete = false
                    },
                    onLogout = {
                        val configuredBaseUrl = dependencies.settingsRepository.getOptionalServerBaseUrl()
                        if (configuredBaseUrl.isNullOrBlank()) {
                            dependencies.settingsRepository.clearActiveUser()
                            viewModel.markLoggedOut()
                        } else {
                            scope.launch {
                                runCatching {
                                    dependencies.serverAuthRepository.logoutActiveUserSession(
                                        baseUrl = configuredBaseUrl,
                                    )
                                }.onFailure {
                                    dependencies.settingsRepository.clearServerAuthSession()
                                }
                                dependencies.settingsRepository.clearActiveUser()
                                viewModel.markLoggedOut()
                            }
                        }
                    },
                )
            }
        }
    }
}

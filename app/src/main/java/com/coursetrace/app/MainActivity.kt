package com.coursetrace.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.IntentCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import com.coursetrace.app.ui.CourseTraceApp
import com.coursetrace.app.ui.theme.CourseTraceTheme

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    private val unlocked = MutableStateFlow(true)
    private var authenticating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val state = viewModel.appState.collectAsStateWithLifecycle().value
            CourseTraceTheme(
                mode = state.preferences.themeMode,
                dynamicColor = state.preferences.dynamicColor,
            ) {
                val isUnlocked = unlocked.collectAsStateWithLifecycle().value
                if (isUnlocked) CourseTraceApp(viewModel)
                else com.coursetrace.app.ui.LockedScreen(onUnlock = ::authenticate)
            }
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        if (viewModel.appState.value.preferences.biometricLock) {
            unlocked.value = false
            authenticate()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && viewModel.appState.value.preferences.biometricLock) unlocked.value = false
    }

    private fun authenticate() {
        if (authenticating || unlocked.value) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlocked.value = true
            return
        }
        authenticating = true
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    unlocked.value = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticating = false
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("解锁课迹")
                .setSubtitle("使用指纹、面容或设备锁屏凭据")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data?.host == "course") {
            intent.data?.pathSegments?.firstOrNull()?.let(viewModel::requestOpenCourse)
            return
        }
        if (intent?.action != Intent.ACTION_SEND) return
        when {
            intent.type == "application/pdf" -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    ?.let(viewModel::importPdf)
            }
            intent.type?.startsWith("text/") == true -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let(viewModel::importChatGptShare)
            }
        }
    }
}

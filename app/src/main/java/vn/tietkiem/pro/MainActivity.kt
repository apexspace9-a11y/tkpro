package vn.tietkiem.pro

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import vn.tietkiem.pro.ui.v4.V4AppRoot
import vn.tietkiem.pro.ui.v4.V4ViewModel

class MainActivity : FragmentActivity() {
    private val vm: V4ViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                V4ViewModel(application as TietKiemProApplication) as T
        })[V4ViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { V4AppRoot(vm = vm, onBiometricRequest = { authenticate() }) }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.lock()
    }

    private fun authenticate() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) return

        BiometricPrompt(
            this,
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    vm.biometricSucceeded()
                }
            }
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Mở Tiết Kiệm Pro")
                .setSubtitle("Xác thực để truy cập tài khoản")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }
}

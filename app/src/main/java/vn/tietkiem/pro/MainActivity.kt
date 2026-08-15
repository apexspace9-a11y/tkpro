package vn.tietkiem.pro

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import vn.tietkiem.pro.ui.AppRoot
import vn.tietkiem.pro.ui.AppViewModel

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(application as TietKiemProApplication) as T
        })[AppViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { AppRoot(vm = vm, onBiometricRequest = { authenticate() }) }
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
                .setSubtitle("Xác thực để truy cập dữ liệu tài chính")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }
}

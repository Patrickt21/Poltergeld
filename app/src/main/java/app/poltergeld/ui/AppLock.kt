package app.poltergeld.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.poltergeld.tr

/** Optional app lock: fingerprint / face / device PIN via BiometricPrompt. */
object AppLock {

    private const val AUTHENTICATORS =
        Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL

    /** True when the device has a screen lock or biometric the prompt can use. */
    fun available(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system prompt. `onResult(true)` on success, `onResult(false)`
     * when the user cancels or the prompt errors out. A merely failed attempt
     * (wrong finger) keeps the prompt open and reports nothing.
     */
    fun prompt(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Poltergeld")
            .setSubtitle(tr("Unlock to view your portfolio", "Entsperren, um dein Portfolio zu sehen"))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}

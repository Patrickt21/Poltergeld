package app.poltergeld.widget

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import app.poltergeld.data.SettingsRepository
import app.poltergeld.ui.AppLock
import kotlinx.coroutines.launch

/**
 * Invisible bridge activity: the widget's lock glyph can't show a
 * BiometricPrompt directly (Glance actions run headless, without an
 * Activity), so tapping it launches this transparent activity instead, which
 * shows the prompt and closes itself immediately afterwards either way. The
 * theme alone isn't enough to stop this from reading as "the app opening" on
 * some launchers, so the enter/exit transition is also nulled out here.
 *
 * The reveal effect (persisting the unlock window, redrawing the widget,
 * scheduling the auto-remask) runs directly here, awaited before [finish] –
 * an earlier version deferred it to a plain (non-expedited) WorkManager job
 * so this activity could close instantly, but regular WorkManager requests
 * aren't guaranteed to run promptly (battery optimization / OEM scheduling
 * can delay them by seconds or more), which left the widget showing "still
 * locked" long after the user had already authenticated. Doing it inline
 * costs at most a brief, imperceptible moment before this already-invisible
 * activity disappears, in exchange for the widget reliably reflecting the
 * unlock right away.
 *
 * singleTask (see manifest), mirroring [app.poltergeld.ui.SettingsActivity]'s
 * own use of the same launch mode for the same reason: nothing about the
 * widget's lock glyph changes the instant it's tapped (it stays "🔒" until
 * the whole round-trip above finishes), so an impatient second tap while the
 * first BiometricPrompt sheet is still showing is easy to trigger. Without
 * singleTask, that second tap would spin up an entirely separate instance of
 * this activity and fire a second, competing BiometricPrompt on top of the
 * first. singleTask routes it into this same instance via [onNewIntent]
 * instead, and [promptShowing] then drops it rather than re-prompting.
 *
 * `android:taskAffinity=""` in the manifest is load-bearing, not decoration:
 * this activity is launched with FLAG_ACTIVITY_NEW_TASK, and that flag's
 * real semantics are "bring the task with a matching affinity to the front,
 * reusing it if one exists" – not "always open a fresh isolated task". Left
 * at the default affinity, this activity shares one with [SettingsActivity],
 * so as soon as the app had ever been opened once (its task is almost always
 * still alive in the background), tapping the widget's lock icon would push
 * this invisible activity onto *that* task and bring it to front – the
 * suppressed animation hides the visual slide-in, but not the task-stack
 * merge itself, so the moment this activity finishes, SettingsActivity
 * underneath is left sitting in the foreground. That's what turns "unlock
 * the widget" into "the app randomly pops open". An empty taskAffinity
 * gives this activity its own task identity so it can never merge with –
 * or surface – the app's real task.
 */
class UnlockPrivacyActivity : FragmentActivity() {
    private var promptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suppressTransition()
        requestUnlock()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        suppressTransition()
        if (!promptShowing) requestUnlock()
    }

    private fun requestUnlock() {
        if (!AppLock.available(this)) {
            finish()
            return
        }
        promptShowing = true
        AppLock.prompt(this) { ok ->
            promptShowing = false
            if (ok) {
                lifecycleScope.launch {
                    val revealMs = SettingsRepository.get(this@UnlockPrivacyActivity)
                        .privacyRevealSeconds * 1_000L
                    val until = System.currentTimeMillis() + revealMs
                    SettingsRepository.savePrivacyUnlockedUntil(this@UnlockPrivacyActivity, until)
                    PortfolioWidget().updateAll(this@UnlockPrivacyActivity)
                    RemaskWorker.scheduleAfterReveal(this@UnlockPrivacyActivity, revealMs)
                    finish()
                    suppressTransition()
                }
            } else {
                finish()
                suppressTransition()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun suppressTransition() {
        overridePendingTransition(0, 0)
    }
}

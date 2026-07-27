package app.poltergeld.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.poltergeld.L10n
import app.poltergeld.data.GhostfolioClient
import app.poltergeld.data.PortfolioResult
import app.poltergeld.data.SettingsRepository
import app.poltergeld.tr
import app.poltergeld.widget.PortfolioWidgetReceiver
import app.poltergeld.widget.WidgetScheduler
import kotlinx.coroutines.launch

private val accentGreen = Color(0xFF34D399)
private val accentRed = Color(0xFFF87171)

/**
 * First-run onboarding: welcome (incl. language) → connect to Ghostfolio →
 * optional app lock → add the homescreen widget. Shown instead of the settings
 * screen while no base URL / token is stored; afterwards the normal overview
 * takes over.
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    activity: FragmentActivity,
    onLockChanged: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }

    BackHandler(enabled = step > 0) { step-- }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StepDots(current = step, count = 4)
        when (step) {
            0 -> WelcomeStep(onNext = { step = 1 })
            1 -> ConnectStep(onBack = { step = 0 }, onNext = { step = 2 })
            2 -> LockStep(
                activity = activity,
                onLockChanged = onLockChanged,
                onBack = { step = 1 },
                onNext = { step = 3 },
            )
            3 -> WidgetStep(onBack = { step = 2 }, onFinish = onFinish)
        }
    }
}

@Composable
private fun StepDots(current: Int, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 10.dp else 8.dp)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    )
            )
        }
    }
}

@Composable
private fun LanguageChips() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        listOf("en" to "English", "de" to "Deutsch").forEach { (key, label) ->
            FilterChip(
                selected = L10n.lang == key,
                onClick = {
                    L10n.set(key)
                    scope.launch { SettingsRepository.saveLanguage(context, key) }
                },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("👻", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Text("Poltergeld", style = MaterialTheme.typography.headlineMedium)
        Text(tr("Widget for Ghostfolio", "Widget für Ghostfolio"), style = MaterialTheme.typography.titleSmall)
    }
    LanguageChips()
    Text(
        tr(
            "See your self-hosted Ghostfolio portfolio right on your homescreen: " +
                "total value, performance, top & flop positions and a custom watchlist.",
            "Dein selbst gehostetes Ghostfolio-Portfolio direkt auf dem Homescreen: " +
                "Gesamtwert, Performance, Top- & Flop-Positionen und eine eigene Watchlist.",
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Bullet(tr(
        "No trackers, no analytics, no third-party services.",
        "Keine Tracker, keine Analytics, keine Drittanbieter-Dienste.",
    ))
    Bullet(tr(
        "Talks only to the Ghostfolio server you configure.",
        "Spricht nur mit dem Ghostfolio-Server, den du einträgst.",
    ))
    Bullet(tr(
        "Your Security Token is encrypted on this device (Android Keystore).",
        "Dein Security Token wird auf diesem Gerät verschlüsselt (Android Keystore).",
    ))
    Spacer(Modifier.height(8.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text(tr("Get started", "Los geht's"))
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Ok(val positions: Int) : TestState
    data class Failed(val message: String) : TestState
}

@Composable
private fun ConnectStep(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var test by remember { mutableStateOf<TestState>(TestState.Idle) }

    // Prefill anything already stored so a restart (rotation, app lock) does
    // not force the user to type the credentials again.
    LaunchedEffect(Unit) {
        val saved = SettingsRepository.get(context)
        if (url.isBlank() && token.isBlank()) {
            url = saved.baseUrl
            token = saved.token
        }
    }

    Text(tr("Connect to Ghostfolio", "Mit Ghostfolio verbinden"), style = MaterialTheme.typography.headlineSmall)
    Text(
        tr(
            "Enter the base URL of your Ghostfolio instance and your Security Token. " +
                "You find the token in Ghostfolio under My Ghostfolio → Security Token.",
            "Trage die Basis-URL deiner Ghostfolio-Instanz und deinen Security Token ein. " +
                "Den Token findest du in Ghostfolio unter Mein Ghostfolio → Security Token.",
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = url,
        onValueChange = { url = it; test = TestState.Idle },
        label = { Text(tr("Base URL (e.g. https://ghostfolio.example.com)", "Basis-URL (z. B. https://ghostfolio.example.com)")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = token,
        onValueChange = { token = it; test = TestState.Idle },
        label = { Text("Security Token") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        tr(
            "Plain http:// works for instances on your local network; prefer https:// " +
                "whenever your server offers it.",
            "Für Instanzen im Heimnetz funktioniert auch http://; nimm https://, " +
                "wann immer dein Server es anbietet.",
        ),
        style = MaterialTheme.typography.bodySmall,
    )

    Button(
        onClick = {
            scope.launch {
                test = TestState.Running
                // Verify the entered credentials first; only a working
                // configuration is stored.
                val candidate = app.poltergeld.data.Settings(
                    baseUrl = SettingsRepository.normalizeUrl(url),
                    token = token.trim(),
                )
                test = when (val r = GhostfolioClient.fetchPortfolio(context, candidate, useCache = false)) {
                    is PortfolioResult.Success -> {
                        SettingsRepository.save(context, url, token)
                        WidgetScheduler.schedulePeriodic(context)
                        WidgetScheduler.refreshNow(context)
                        TestState.Ok(r.holdings.size)
                    }
                    is PortfolioResult.Error -> TestState.Failed(r.message)
                }
            }
        },
        enabled = url.isNotBlank() && token.isNotBlank() && test !is TestState.Running,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(tr("Test & save connection", "Verbindung testen & speichern"))
    }

    when (val t = test) {
        TestState.Idle -> {}
        TestState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
            Text("  " + tr("Testing connection…", "Verbindung wird getestet…"), style = MaterialTheme.typography.bodyMedium)
        }
        is TestState.Ok -> Text(
            tr("✓ Connected – ${t.positions} positions found.", "✓ Verbunden – ${t.positions} Positionen gefunden."),
            color = accentGreen,
            style = MaterialTheme.typography.bodyMedium,
        )
        is TestState.Failed -> Text(
            "✗ ${t.message}",
            color = accentRed,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text(tr("Back", "Zurück")) }
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, enabled = test is TestState.Ok) { Text(tr("Continue", "Weiter")) }
    }
}

@Composable
private fun LockStep(
    activity: FragmentActivity,
    onLockChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    Text(tr("Protect your numbers", "Schütze deine Zahlen"), style = MaterialTheme.typography.headlineSmall)
    Text(
        tr(
            "Optionally require your fingerprint or device PIN when opening the app. " +
                "While enabled, the app is also hidden in recents and screenshots are " +
                "blocked. The homescreen widget stays visible either way.",
            "Optional beim Öffnen der App Fingerabdruck oder Geräte-PIN verlangen. " +
                "Solange aktiv, wird die App auch in der App-Übersicht ausgeblendet und " +
                "Screenshots sind gesperrt. Das Homescreen-Widget bleibt in jedem Fall sichtbar.",
        ),
        style = MaterialTheme.typography.bodyMedium,
    )

    if (enabled) {
        Text(
            tr("✓ App lock enabled.", "✓ App-Sperre aktiviert."),
            color = accentGreen,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        OutlinedButton(
            onClick = {
                if (!AppLock.available(context)) {
                    status = tr(
                        "No screen lock set up on this device – add a PIN or " +
                            "fingerprint in the system settings first.",
                        "Auf diesem Gerät ist keine Displaysperre eingerichtet – " +
                            "lege zuerst PIN oder Fingerabdruck in den Systemeinstellungen an.",
                    )
                } else {
                    AppLock.prompt(activity) { ok ->
                        if (ok) {
                            enabled = true
                            status = ""
                            scope.launch {
                                SettingsRepository.saveRequireUnlock(context, true)
                            }
                            onLockChanged(true)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(tr("Enable app lock", "App-Sperre aktivieren"))
        }
        Text(
            tr(
                "You can enable it later in the settings at any time.",
                "Du kannst sie jederzeit später in den Einstellungen aktivieren.",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (status.isNotBlank()) {
        Text(status, color = accentRed, style = MaterialTheme.typography.bodyMedium)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text(tr("Back", "Zurück")) }
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext) {
            Text(if (enabled) tr("Continue", "Weiter") else tr("Skip", "Überspringen"))
        }
    }
}

@Composable
private fun WidgetStep(onBack: () -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val widgetManager = AppWidgetManager.getInstance(context)
    val canPin = widgetManager.isRequestPinAppWidgetSupported

    Text(tr("Add the widget", "Widget hinzufügen"), style = MaterialTheme.typography.headlineSmall)
    Text(
        tr(
            "The widget shows your portfolio on the homescreen and refreshes " +
                "regularly (interval adjustable in the settings). A small widget " +
                "shows just the summary, larger ones add top/flop and the full " +
                "position list.",
            "Das Widget zeigt dein Portfolio auf dem Homescreen und aktualisiert " +
                "sich regelmäßig (Intervall in den Einstellungen wählbar). Ein " +
                "kleines Widget zeigt nur die Zusammenfassung, größere zusätzlich " +
                "Top/Flop und die komplette Positionsliste.",
        ),
        style = MaterialTheme.typography.bodyMedium,
    )

    if (canPin) {
        Button(
            onClick = {
                widgetManager.requestPinAppWidget(
                    ComponentName(context, PortfolioWidgetReceiver::class.java),
                    null,
                    null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(tr("Add widget to homescreen", "Widget zum Homescreen hinzufügen"))
        }
        Text(
            tr(
                "You can also add it later: long-press the homescreen → Widgets → " +
                    "Poltergeld Portfolio.",
                "Geht auch später: Homescreen lange drücken → Widgets → " +
                    "Poltergeld Portfolio.",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Text(
            tr(
                "Long-press your homescreen → Widgets → add Poltergeld Portfolio.",
                "Homescreen lange drücken → Widgets → Poltergeld Portfolio hinzufügen.",
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        tr(
            "Long-press a placed widget (or open the app settings) to pin it to a " +
                "fixed view – summary, top & flop, all positions or a custom watchlist.",
            "Ein platziertes Widget lange drücken (oder die App-Einstellungen öffnen), " +
                "um es auf eine feste Ansicht zu setzen – Zusammenfassung, Top & Flop, " +
                "alle Positionen oder eine eigene Watchlist.",
        ),
        style = MaterialTheme.typography.bodySmall,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text(tr("Back", "Zurück")) }
        Spacer(Modifier.weight(1f))
        Button(onClick = onFinish) { Text(tr("Done", "Fertig")) }
    }
}

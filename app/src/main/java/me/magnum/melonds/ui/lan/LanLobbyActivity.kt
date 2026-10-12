package me.magnum.melonds.ui.lan

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.magnum.melonds.LanInterface
import me.magnum.melonds.LanSessionManager
import me.magnum.melonds.R
import me.magnum.melonds.ui.theme.MelonTheme

/**
 * One-tap LAN multiplayer for two devices on the same Wi-Fi. One device taps HOST (it becomes Player 1) and the other
 * taps JOIN (it becomes Player 2 and automatically connects to the host it finds). No names or addresses to type.
 *
 * The session stays alive after this screen closes, so players leave the lobby, start the same game from the
 * ROM list on every device, and the game picks up the existing session.
 */
class LanLobbyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        setContent {
            MelonTheme {
                LanLobbyScreen(
                    lifecycle = lifecycle,
                    onClose = { finish() },
                )
            }
        }
    }
}

private enum class LobbyPhase { MENU, SEARCHING, IN_SESSION }

private const val HOST_NAME = "Player 1"
private const val JOIN_NAME = "Player 2"
private const val MAX_PLAYERS = 2
private const val REFRESH_INTERVAL_MS = 1000L

@Composable
private fun LanLobbyScreen(
    lifecycle: Lifecycle,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember {
        mutableStateOf(
            when {
                !LanSessionManager.isActive -> LobbyPhase.MENU
                LanInterface.players().isNotEmpty() -> LobbyPhase.IN_SESSION
                else -> LobbyPhase.SEARCHING
            }
        )
    }
    var busy by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf(emptyList<LanInterface.DiscoveredSession>()) }
    var players by remember { mutableStateOf(emptyList<LanInterface.LanPlayer>()) }

    // Drive the native LAN code while this screen is visible. Stops as soon as the screen is paused,
    // so it never overlaps with a game that is launched from the ROM list.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch(Dispatchers.Default) {
                while (true) {
                    if (LanSessionManager.isActive) {
                        val currentSessions = LanInterface.discoveredSessions()
                        val currentPlayers = LanInterface.players()
                        withContext(Dispatchers.Main) {
                            sessions = currentSessions
                            players = currentPlayers
                        }
                    }
                    delay(REFRESH_INTERVAL_MS)
                }
            }
        }
    }

    fun leaveSession() {
        scope.launch {
            withContext(Dispatchers.Default) { LanSessionManager.disable() }
            sessions = emptyList()
            players = emptyList()
            phase = LobbyPhase.MENU
        }
    }

    fun closeScreen() {
        // Closing from the menu or while only searching must not leave the Wi-Fi locks held
        if (phase != LobbyPhase.IN_SESSION) {
            LanSessionManager.disable()
        }
        onClose()
    }

    fun host() {
        if (busy) return
        busy = true
        scope.launch {
            val started = withContext(Dispatchers.Default) {
                LanSessionManager.enable(context)
                val ok = LanInterface.startHost(HOST_NAME, MAX_PLAYERS)
                if (!ok) LanSessionManager.disable()
                ok
            }
            busy = false
            if (started) {
                phase = LobbyPhase.IN_SESSION
            } else {
                Toast.makeText(context, R.string.lan_host_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun join() {
        if (busy) return
        busy = true
        scope.launch {
            val started = withContext(Dispatchers.Default) {
                LanSessionManager.enable(context)
                val ok = LanInterface.startDiscovery()
                if (!ok) LanSessionManager.disable()
                ok
            }
            busy = false
            if (started) {
                phase = LobbyPhase.SEARCHING
            } else {
                Toast.makeText(context, R.string.lan_discovery_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    // While searching, connect on our own to the first host that is available.
    // The connection work runs in the screen's own scope: this effect restarts whenever the host list refreshes,
    // and a restart must not cancel a connection attempt that is already in progress
    LaunchedEffect(phase, sessions) {
        if (phase != LobbyPhase.SEARCHING || busy) return@LaunchedEffect
        val target = sessions.firstOrNull { !it.isPlaying } ?: return@LaunchedEffect

        busy = true
        scope.launch {
            val connected = withContext(Dispatchers.Default) {
                LanInterface.endDiscovery()
                val ok = LanInterface.startClient(JOIN_NAME, target.address)
                // Same as the desktop app: go back to searching if the connection could not be started
                if (!ok) LanInterface.startDiscovery()
                ok
            }
            busy = false
            if (connected) {
                phase = LobbyPhase.IN_SESSION
            }
        }
    }

    BackHandler { closeScreen() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.systemBarsPadding(),
                title = { Text(stringResource(R.string.lan_multiplayer)) },
                navigationIcon = {
                    IconButton(onClick = { closeScreen() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (phase) {
                LobbyPhase.MENU -> MenuContent(
                    busy = busy,
                    onHost = ::host,
                    onJoin = ::join,
                )

                LobbyPhase.SEARCHING -> StatusContent(
                    title = stringResource(R.string.lan_looking_for_host),
                    subtitle = stringResource(R.string.lan_looking_for_host_hint),
                    players = emptyList(),
                    primaryLabel = null,
                    onPrimary = {},
                    onCancel = ::leaveSession,
                )

                LobbyPhase.IN_SESSION -> {
                    val connected = players.count { it.status != LanInterface.PlayerStatus.DISCONNECTED && it.status != LanInterface.PlayerStatus.CONNECTING } >= 2
                    StatusContent(
                        title = stringResource(if (connected) R.string.lan_connected_big else R.string.lan_waiting_for_player),
                        subtitle = stringResource(if (connected) R.string.lan_session_hint else R.string.lan_waiting_hint),
                        players = players,
                        primaryLabel = stringResource(R.string.lan_pick_game),
                        onPrimary = ::closeScreen,
                        onCancel = ::leaveSession,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MenuContent(
    busy: Boolean,
    onHost: () -> Unit,
    onJoin: () -> Unit,
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        onClick = onHost,
        enabled = !busy,
    ) {
        Text(
            text = stringResource(R.string.lan_host_big),
            style = MaterialTheme.typography.h4,
            textAlign = TextAlign.Center,
        )
    }
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        onClick = onJoin,
        enabled = !busy,
    ) {
        Text(
            text = stringResource(R.string.lan_join_big),
            style = MaterialTheme.typography.h4,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ColumnScope.StatusContent(
    title: String,
    subtitle: String,
    players: List<LanInterface.LanPlayer>,
    primaryLabel: String?,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.h4, textAlign = TextAlign.Center)
        Text(text = subtitle, style = MaterialTheme.typography.body1, textAlign = TextAlign.Center)

        players.forEach { player ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (player.isLocal) stringResource(R.string.lan_player_you, player.name) else player.name,
                    style = MaterialTheme.typography.h6,
                )
                Text(
                    text = if (player.isLocal) "" else stringResource(R.string.lan_ping, player.pingMs),
                    style = MaterialTheme.typography.body2,
                )
            }
        }
    }

    if (primaryLabel != null) {
        Button(modifier = Modifier.fillMaxWidth(), onClick = onPrimary) {
            Text(primaryLabel, style = MaterialTheme.typography.h6)
        }
    }
    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onCancel) {
        Text(stringResource(R.string.lan_leave_session))
    }
}

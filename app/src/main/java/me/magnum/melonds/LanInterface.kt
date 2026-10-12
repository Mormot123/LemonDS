package me.magnum.melonds

/**
 * Bridge to the native LAN multiplayer backend. Only call [setLanEnabled] while no game is running,
 * since it replaces the native multiplayer interface the emulator uses.
 */
object LanInterface {

    data class DiscoveredSession(
        val address: String,
        val name: String,
        val playerCount: Int,
        val maxPlayers: Int,
        val isPlaying: Boolean,
    )

    data class LanPlayer(
        val id: Int,
        val name: String,
        val status: PlayerStatus,
        val pingMs: Int,
        val isLocal: Boolean,
    )

    // Order matches LAN::PlayerStatus in the native core
    enum class PlayerStatus {
        NONE, CLIENT, HOST, CONNECTING, DISCONNECTED
    }

    external fun setLanEnabled(enabled: Boolean)
    external fun startHost(playerName: String, maxPlayers: Int): Boolean
    external fun startClient(playerName: String, hostAddress: String): Boolean
    external fun startDiscovery(): Boolean
    external fun endDiscovery()
    external fun endSession()
    external fun getPlayerCount(): Int

    /**
     * Services LAN discovery and connections while no game is running. Call it every ~20 ms from the lobby.
     * It is a no-op while an emulator instance exists, since the emulator thread does this work itself.
     */
    external fun processLobby()

    private external fun getDiscoveryList(): Array<String>
    private external fun getPlayerList(): Array<String>

    fun discoveredSessions(): List<DiscoveredSession> {
        return getDiscoveryList().mapNotNull { entry ->
            val parts = entry.split('\t')
            if (parts.size != 5) return@mapNotNull null

            DiscoveredSession(
                address = parts[0],
                name = parts[1],
                playerCount = parts[2].toIntOrNull() ?: return@mapNotNull null,
                maxPlayers = parts[3].toIntOrNull() ?: return@mapNotNull null,
                isPlaying = parts[4] == "1",
            )
        }
    }

    fun players(): List<LanPlayer> {
        return getPlayerList().mapNotNull { entry ->
            val parts = entry.split('\t')
            if (parts.size != 5) return@mapNotNull null

            LanPlayer(
                id = parts[0].toIntOrNull() ?: return@mapNotNull null,
                name = parts[1],
                status = PlayerStatus.entries.getOrNull(parts[2].toIntOrNull() ?: return@mapNotNull null) ?: PlayerStatus.NONE,
                pingMs = parts[3].toIntOrNull() ?: 0,
                isLocal = parts[4] == "1",
            )
        }
    }
}

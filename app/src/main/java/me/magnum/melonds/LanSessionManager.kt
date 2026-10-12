package me.magnum.melonds

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Owns the lifetime of a LAN session. The session outlives the lobby screen, because players
 * set it up in the lobby and then launch the same game from the ROM list. The Wi-Fi locks are
 * held for as long as the session is active so that discovery broadcasts and game packets
 * are not delayed or dropped by Wi-Fi power saving.
 */
object LanSessionManager {

    private const val LOCK_TAG = "melonDS:lan"
    private const val PUMP_INTERVAL_MS = 20L

    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    var isActive = false
        private set

    @Volatile
    private var pumpThread: Thread? = null

    /**
     * Keeps the LAN connection serviced while no game is running, including after the lobby screen has closed and
     * the players are choosing a game. Without this the connection times out in a few seconds. It does nothing once a
     * game is running, because the emulator then services the connection itself.
     */
    private fun startPump() {
        val thread = Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    LanInterface.processLobby()
                    Thread.sleep(PUMP_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }, "lan-pump")
        thread.isDaemon = true
        pumpThread = thread
        thread.start()
    }

    private fun stopPump() {
        pumpThread?.let {
            it.interrupt()
            try {
                it.join(500)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        pumpThread = null
    }

    @Synchronized
    fun enable(context: Context) {
        if (isActive) return

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Without the multicast lock many devices silently drop the UDP broadcasts used to find hosts
        multicastLock = wifiManager.createMulticastLock(LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
        val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifiManager.createWifiLock(wifiMode, LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }

        LanInterface.setLanEnabled(true)
        isActive = true
        startPump()
    }

    /**
     * Ends the session and releases the locks. Must not be called while a game is running.
     */
    @Synchronized
    fun disable() {
        if (!isActive) return

        stopPump()
        LanInterface.endDiscovery()
        LanInterface.endSession()
        LanInterface.setLanEnabled(false)

        multicastLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        multicastLock = null
        wifiLock = null
        isActive = false
    }
}

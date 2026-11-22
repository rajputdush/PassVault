package com.ritvik.passvault

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * A Quick Settings tile ("Lock PassVault"): pull down the notification shade and tap it to lock the vault at once,
 * without opening the app. It only locks; it can never unlock or show anything.
 */
class LockTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        container().session.lock()
        refresh()
    }

    private fun container() = (application as PassVaultApp).container

    private fun refresh() {
        val tile = qsTile ?: return
        val unlocked = container().session.unlocked.value
        tile.state = if (unlocked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_lock_label)
        tile.updateTile()
    }
}

/*
 * Copyright (c) 2018-2019 The Decred developers
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */

package com.btcandroid.util

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.core.text.HtmlCompat
import com.btcandroid.R
import com.btcandroid.extensions.hide
import com.btcandroid.extensions.isShowing
import com.btcandroid.extensions.show
import com.btcandroid.extensions.toggleVisibility
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import btclibwallet.*
import kotlinx.android.synthetic.main.fragment_overview.view.*
import kotlinx.android.synthetic.main.multi_wallet_sync_details.view.*
import kotlinx.android.synthetic.main.single_wallet_sync_details.view.*
import kotlinx.android.synthetic.main.synced_unsynced_layout.view.*
import kotlinx.android.synthetic.main.syncing_layout.view.*
import kotlinx.coroutines.*

class SyncLayoutUtil(private val syncLayout: LinearLayout, restartSyncProcess: () -> Unit, scrollToBottom: () -> Unit) : SyncProgressListener {

    private val context: Context
        get() = syncLayout.context

    private val multiWallet: MultiWallet
        get() = WalletData.multiWallet!!

    private var openedWallets: ArrayList<Long> = ArrayList()

    private var blockUpdateJob: Job? = null

    init {
        loadOpenedWallets()

        multiWallet.removeSyncProgressListener(this.javaClass.name)
        multiWallet.addSyncProgressListener(this, this.javaClass.name)

        if (multiWallet.isSyncing || multiWallet.isRescanning) {
            displaySyncingLayout()
        } else {
            displaySyncedUnsynced()
        }

        // click listeners
        syncLayout.show_details.setOnClickListener {
            syncLayout.sync_details.toggleVisibility()

            syncLayout.show_details.text = if (syncLayout.sync_details.isShowing()) context.getString(R.string.hide_details)
            else context.getString(R.string.show_details)

            scrollToBottom()
        }

        syncLayout.syncing_cancel_layout.setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                it.isEnabled = false
                launch(Dispatchers.Default) {
                    if (multiWallet.isSyncing) {
                        multiWallet.cancelSync()
                    }
                }
                it.isEnabled = true
            }
        }

        syncLayout.reconnect_layout.setOnClickListener {
            if (multiWallet.isSynced) {
                GlobalScope.launch(Dispatchers.Main) {
                    it.isEnabled = false
                    launch(Dispatchers.Default) { multiWallet.cancelSync() }
                    it.isEnabled = true
                }
            } else {
                restartSyncProcess()
            }
        }
    }

    fun destroy() {
        blockUpdateJob?.cancel()
        multiWallet.removeSyncProgressListener(this.javaClass.name)
    }

    private fun loadOpenedWallets() {
        val openedWalletsJson = multiWallet.openedWalletIDs()
        val gson = Gson()
        val listType = object : TypeToken<ArrayList<Long>>() {}.type

        val openedWalletsTemp = gson.fromJson<ArrayList<Long>>(openedWalletsJson, listType)

        openedWallets.clear()
        openedWallets.addAll(openedWalletsTemp)
    }

    // this function basically prepares the sync layout for onHeadersFetchProgress
    private fun resetSyncingLayout() = GlobalScope.launch(Dispatchers.Main) {
        syncLayout.pb_sync_progress.progress = 0
        syncLayout.tv_percentage.text = context.getString(R.string.percentage, 0)
        syncLayout.tv_time_left.text = context.getString(R.string.time_left_seconds, 0)
        syncLayout.sync_details.hide()
        syncLayout.show_details.text = context.getString(R.string.show_details)

        syncLayout.tv_steps_title.text = context.getString(R.string.starting_up)
        syncLayout.tv_steps.text = context.getString(R.string.step_1_2)

        syncLayout.syncing_layout_wallet_name.hide()

        // connected peers count
        syncLayout.tv_syncing_layout_connected_peer.text = multiWallet.connectedPeers().toString()

        showSyncVerboseExtras()

        // cfilters fetched
        syncLayout.tv_cfilters_fetched.setText(R.string.cfilters_fetched)
        syncLayout.tv_fetch_count.text = "0"

        // syncing progress
        syncLayout.tv_progress.setText(R.string.syncing_progress)
        syncLayout.tv_days.text = null
    }

    private fun startupBlockUpdate() = GlobalScope.launch(Dispatchers.Default) {
        if (blockUpdateJob != null)
            return@launch

        blockUpdateJob = launch {
            while (true) {
                updateLatestBlock()
                delay(5000)
            }
        }
    }

    private fun updateLatestBlock() = GlobalScope.launch(Dispatchers.Main) {
        val blockInfo = multiWallet.bestBlock
        val currentTimeSeconds = System.currentTimeMillis()
        val lastBlockRelativeTime = (currentTimeSeconds - blockInfo.timestamp) / 1000
        val formattedLastBlockTime = TimeUtils.calculateTime(lastBlockRelativeTime, syncLayout.context)

        val latestBlock: String
        latestBlock = if (multiWallet.isSynced) {
            context.getString(R.string.synced_latest_block_time, blockInfo.height, formattedLastBlockTime)
        } else {
            syncLayout.context.getString(R.string.latest_block_time, blockInfo.height, formattedLastBlockTime)
        }

        syncLayout.tv_latest_block.text = HtmlCompat.fromHtml(latestBlock, 0)
    }

    private fun displaySyncedUnsynced() {
        syncLayout.post {
            syncLayout.syncing_layout.hide()
            syncLayout.synced_unsynced_layout.show()

            val connectedPeers: String

            if (multiWallet.isSynced) {
                println("Displaying synced layout")

                syncLayout.tv_online_offline_status.setText(R.string.online)
                syncLayout.view_online_offline_status.setBackgroundResource(R.drawable.online_dot)

                syncLayout.sync_state_icon.setImageResource(R.drawable.ic_checkmark)
                syncLayout.tv_sync_state.setText(R.string.synced)

                syncLayout.tv_reconnect.setText(R.string.disconnect)
                syncLayout.cancel_icon.hide()

                connectedPeers = context.getString(R.string.connected_peers, multiWallet.connectedPeers())

            } else {
                println("Displaying unsynced layout")
                syncLayout.tv_online_offline_status.setText(R.string.offline)
                syncLayout.view_online_offline_status.setBackgroundResource(R.drawable.offline_dot)

                syncLayout.sync_state_icon.setImageResource(R.drawable.ic_crossmark)
                syncLayout.tv_sync_state.setText(R.string.not_syncing)

                syncLayout.tv_reconnect.setText(R.string.connect)
                syncLayout.cancel_icon.show()
                syncLayout.cancel_icon.setImageResource(R.drawable.ic_rescan)

                connectedPeers = syncLayout.context.getString(R.string.no_connected_peers)
            }

            syncLayout.connected_peers.text = HtmlCompat.fromHtml(connectedPeers, 0)
            updateLatestBlock()
            if (multiWallet.isSynced)
                startupBlockUpdate()
        }
    }

    private fun displaySyncingLayout() {
        blockUpdateJob?.cancel()
        blockUpdateJob = null
        resetSyncingLayout()

        this.displaySyncingLayoutIfNotShowing()
    }

    private fun displaySyncingLayoutIfNotShowing() = GlobalScope.launch(Dispatchers.Main) {
        syncLayout.syncing_layout.visibility = View.VISIBLE
        syncLayout.synced_unsynced_layout.visibility = View.GONE

        syncLayout.tv_online_offline_status.setText(R.string.online)
        syncLayout.view_online_offline_status.setBackgroundResource(R.drawable.online_dot)

        if(multiWallet.isRescanning){
            syncLayout.syncing_layout.syncing_layout_status.text =   context.getString(R.string.rescanning_blocks_ellipsis)
        }else {
            syncLayout.syncing_layout.syncing_layout_status.text =   context.getString(R.string.syncing_state)
        }

    }

    private fun showSyncVerboseExtras() {

        syncLayout.sync_verbose.show()
        syncLayout.multi_wallet_sync_verbose.hide()
    }

    private fun hideSyncVerboseExtras() {

        syncLayout.sync_verbose.hide()
        syncLayout.multi_wallet_sync_verbose.hide()
    }

    private fun publishSyncProgress(syncProgress: GeneralSyncProgress) = GlobalScope.launch(Dispatchers.Main) {
        syncLayout.pb_sync_progress.progress = syncProgress.totalSyncProgress
        syncLayout.tv_percentage.text = context.getString(R.string.percentage, syncProgress.totalSyncProgress)
        syncLayout.tv_time_left.text = TimeUtils.getSyncTimeRemaining(syncProgress.totalTimeRemainingSeconds, context)

        // connected peers count
        syncLayout.tv_syncing_layout_connected_peer.text = multiWallet.connectedPeers().toString()
    }

    override fun onSyncStarted(wasRestarted: Boolean) {
        println("Sync started")
        displaySyncingLayout()
    }

    override fun onCFiltersFetchProgress(p0: CFiltersFetchProgressReport) {
        println("Cfl progress")
        publishSyncProgress(p0.generalSyncProgress)

        GlobalScope.launch(Dispatchers.Main) {

            syncLayout.tv_steps.text = context.getString(R.string.step_1_2)

            syncLayout.tv_cfilters_fetched.setText(R.string.cfilters_fetched)
            syncLayout.tv_fetch_count.text = context.getString(R.string.cfilters_fetched_count,
                    p0.fetchedCFilters, p0.fetchedCFilters + p0.totalCFiltersToFetch)

            syncLayout.tv_progress.setText(R.string.syncing_progress)
            val lastHeaderRelativeTime = (System.currentTimeMillis() / 1000) - p0.lastCFiltersTimestamp
            syncLayout.tv_days.text = TimeUtils.getDaysBehind(lastHeaderRelativeTime, context)
        }
    }

    override fun onSyncCanceled(willRestart: Boolean) {
        if (!willRestart) {
            displaySyncedUnsynced()
        } else {
            resetSyncingLayout()
        }
    }

    override fun onSyncEndedWithError(err: Exception?) {
        err?.printStackTrace()
        displaySyncedUnsynced()
    }

    override fun onSyncCompleted() {
        println("Sync completed")
        displaySyncedUnsynced()
    }

    override fun onPeerConnectedOrDisconnected(numberOfConnectedPeers: Int) {
        GlobalScope.launch(Dispatchers.Main) {
            if (multiWallet.isSynced) {
                syncLayout.connected_peers.text = HtmlCompat.fromHtml(context.getString(R.string.connected_peers, multiWallet.connectedPeers()), 0)
            } else if (multiWallet.isSyncing) {
                syncLayout.tv_syncing_layout_connected_peer.text = numberOfConnectedPeers.toString()
            }
        }
    }

    override fun onRescanProgress(p0: RescanProgressReport) {
        println("Rescanning ${p0.rescanProgress}%")
    }
}
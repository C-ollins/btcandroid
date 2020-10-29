/*
 * Copyright (c) 2018-2019 The Decred developers
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */

package com.btcandroid.activities.more

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import com.btcandroid.R
import com.btcandroid.activities.BaseActivity
import com.btcandroid.activities.LogViewer
import com.btcandroid.data.Constants
import com.btcandroid.preference.ListPreference
import btclibwallet.Btclibwallet
import com.btcandroid.dialog.InfoDialog
import com.btcandroid.util.SnackBar
import kotlinx.android.synthetic.main.activity_debug.*
import kotlinx.android.synthetic.main.activity_debug.go_back
import kotlinx.android.synthetic.main.activity_debug.rescan_blockchain
import kotlinx.android.synthetic.main.activity_debug.view.*
import kotlinx.android.synthetic.main.activity_wallet_settings.*

class DebugActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        setLogLevelSummary(multiWallet!!.readInt32ConfigValueForKey(Btclibwallet.LogLevelConfigKey, Constants.DEF_LOG_LEVEL))
        ListPreference(this, Btclibwallet.LogLevelConfigKey, Constants.DEF_LOG_LEVEL,
                R.array.logging_levels, logging_level) {
            setLogLevelSummary(it)
        }

        rescan_blockchain.setOnClickListener {
            if (multiWallet!!.isSyncing) {
                SnackBar.showError(this, R.string.err_sync_in_progress)
            } else if (!multiWallet!!.isSynced) {
                SnackBar.showError(this, R.string.not_connected)
            }  else {
                InfoDialog(this)
                        .setDialogTitle(getString(R.string.rescan_blockchain))
                        .setMessage(getString(R.string.rescan_blockchain_warning))
                        .setPositiveButton(getString(R.string.yes), DialogInterface.OnClickListener { _, _ ->
                            multiWallet!!.rescan()
                            SnackBar.showText(this, R.string.rescan_progress_notification)
                        })
                        .setNegativeButton(getString(R.string.no))
                        .show()
            }

        }

        check_statistics.setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }

        check_wallet_log.setOnClickListener {
            startActivity(Intent(this, LogViewer::class.java))
        }

        go_back.setOnClickListener {
            finish()
        }
    }

    private fun setLogLevelSummary(index: Int) {
        val logLevels = resources.getStringArray(R.array.logging_levels)
        logging_level.pref_subtitle.text = logLevels[index]
        Btclibwallet.setLogLevels(logLevels[index])
    }
}
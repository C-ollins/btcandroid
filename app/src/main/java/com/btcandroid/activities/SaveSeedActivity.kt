/*
 * Copyright (c) 2018-2019 The Decred developers
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */

package com.btcandroid.activities

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.btcandroid.R
import com.btcandroid.adapter.SaveSeedAdapter
import com.btcandroid.data.Constants
import com.btcandroid.dialog.PasswordPromptDialog
import com.btcandroid.dialog.PinPromptDialog
import com.btcandroid.util.PassPromptTitle
import com.btcandroid.util.PassPromptUtil
import com.btcandroid.util.Utils
import com.btcandroid.util.WalletData
import btclibwallet.Btclibwallet
import btclibwallet.Wallet
import kotlinx.android.synthetic.main.save_seed_page.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SEEDS_PER_ROW = 17

class SaveSeedActivity : BaseActivity() {

    private lateinit var wallet: Wallet
    private var seed: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.save_seed_page)

        try {
            scroll_view_seeds.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                app_bar.elevation = if (scrollY != 0) {
                    resources.getDimension(R.dimen.app_bar_elevation)
                } else {
                    0f
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        step_2.setOnClickListener {
            val verifySeedIntent = Intent(this, VerifySeedActivity::class.java)
            verifySeedIntent.putExtra(Constants.WALLET_ID, wallet.id)
            verifySeedIntent.putExtra(Constants.SEED, seed)
            verifySeedIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
            startActivity(verifySeedIntent)
        }

        go_back.setOnClickListener {
            finish()
        }

        val walletId = intent.getLongExtra(Constants.WALLET_ID, -1)
        wallet = WalletData.multiWallet!!.walletWithID(walletId)
        promptWalletPassphrase()
    }

    private fun promptWalletPassphrase() {

        val title = PassPromptTitle(R.string.confirm_show_seed, R.string.confirm_show_seed, R.string.confirm_show_seed)
        PassPromptUtil(this, wallet.id, title, allowFingerprint = true) { passDialog, pass ->
            if (pass == null) {
                finish()
                return@PassPromptUtil true
            }

            GlobalScope.launch(Dispatchers.Default) {
                val op = this@SaveSeedActivity.javaClass.name + ": " + this.javaClass.name + ": promptWalletPassphrase."
                try {
                    seed = wallet.decryptSeed(pass.toByteArray())
                    populateList(seed!!)
                    passDialog?.dismiss()
                } catch (e: Exception) {
                    e.printStackTrace()

                    if (e.message == Btclibwallet.ErrInvalidPassphrase) {
                        if (passDialog is PinPromptDialog) {
                            passDialog.setProcessing(false)
                            passDialog.showError()
                        } else if (passDialog is PasswordPromptDialog) {
                            passDialog.setProcessing(false)
                            passDialog.showError()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            passDialog?.dismiss()
                            Utils.showErrorDialog(this@SaveSeedActivity, op + ": " + e.message)
                            Btclibwallet.logT(op, e.message)
                        }
                    }
                }
            }

            return@PassPromptUtil false
        }.show()
    }

    private fun populateList(seed: String) = GlobalScope.launch(Dispatchers.Main) {

        val items = seed.split(Constants.NBSP.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        val layoutManager = GridLayoutManager(applicationContext, SEEDS_PER_ROW, GridLayoutManager.HORIZONTAL, false)

        val verticalDivider = VerticalDividerItemDecoration(resources.getDimensionPixelSize(R.dimen.margin_padding_size_8), true)
        val horizontalItemDecoration = VerticalDividerItemDecoration(resources.getDimensionPixelSize(R.dimen.seed_horizontal_margin), false)

        recycler_view_seeds.isNestedScrollingEnabled = false
        recycler_view_seeds.layoutManager = layoutManager
        recycler_view_seeds.addItemDecoration(verticalDivider)
        recycler_view_seeds.addItemDecoration(horizontalItemDecoration)
        recycler_view_seeds.adapter = SaveSeedAdapter(items)

        step_2.isEnabled = true
    }

    inner class VerticalDividerItemDecoration(private val space: Int, private val verticalOrientation: Boolean) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView,
                                    state: RecyclerView.State) {
            if (verticalOrientation) {
                outRect.set(0, 0, 0, space)
            } else {
                outRect.set(0, 0, space, 0)
            }
        }
    }

}
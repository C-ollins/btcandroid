/*
 * Copyright (c) 2018-2019 The Decred developers
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */

package com.btcandroid.dialog.send

import android.os.Bundle
import android.text.Spannable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.FragmentActivity
import com.btcandroid.R
import com.btcandroid.data.Account
import com.btcandroid.data.TransactionData
import com.btcandroid.dialog.FullScreenBottomSheetDialog
import com.btcandroid.dialog.PasswordPromptDialog
import com.btcandroid.dialog.PinPromptDialog
import com.btcandroid.extensions.hide
import com.btcandroid.extensions.show
import com.btcandroid.util.CoinFormat
import com.btcandroid.util.PassPromptTitle
import com.btcandroid.util.PassPromptUtil
import com.btcandroid.util.Utils
import btclibwallet.Btclibwallet
import btclibwallet.Wallet
import kotlinx.android.synthetic.main.confirm_send_sheet.*
import kotlinx.coroutines.*

class ConfirmTransaction(private val fragmentActivity: FragmentActivity, val sendSuccess: (shouldExit: Boolean) -> Unit) : FullScreenBottomSheetDialog() {

    lateinit var wallet: Wallet

    private val selectedAccount: Account
        get() = transactionData.sourceAccount

    lateinit var transactionData: TransactionData
    lateinit var authoredTxData: AuthoredTxData
    private var publishedTx = false

    fun setTxData(transactionData: TransactionData, authoredTxData: AuthoredTxData): ConfirmTransaction {
        this.transactionData = transactionData
        this.authoredTxData = authoredTxData
        this.wallet = multiWallet.walletWithID(transactionData.sourceAccount.walletID)
        return this
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.confirm_send_sheet, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // set up layout
        send_from_account_name.text = HtmlCompat.fromHtml(getString(R.string.send_from_account,
                selectedAccount.accountName, wallet.name), 0)

        val dcrAmount = CoinFormat.formatBitcoin(Btclibwallet.amountAtom(transactionData.btcAmount.toDouble()))
        val amountStr = if (transactionData.exchangeDecimal != null) {
            val usdAmount = dcrToFormattedUSD(transactionData.exchangeDecimal, transactionData.btcAmount.toDouble(), 2)
            HtmlCompat.fromHtml(getString(R.string.x_btc_usd, dcrAmount, usdAmount), 0)
        } else {
            getString(R.string.x_btc, dcrAmount)
        }
        if (amountStr is Spannable) {
            CoinFormat.formatSpannable(amountStr, AmountRelativeSize)
            send_amount.text = amountStr
        } else {
            send_amount.text = CoinFormat.format(amountStr as String, AmountRelativeSize)
        }


        tx_fee.text = authoredTxData.fee
        total_cost.text = authoredTxData.totalCost
        balance_after_send.text = authoredTxData.balanceAfter
        send_btn.text = getString(R.string.send_x_btc, dcrAmount)

        // address & account
        if (transactionData.destinationAccount == null) {
            address_account_name.text = transactionData.destinationAddress
        } else {
            confirm_dest_type.setText(R.string.to_self)

            val destinationAccount = transactionData.destinationAccount!!
            val receivingWallet = multiWallet.walletWithID(destinationAccount.walletID)
            address_account_name.text = HtmlCompat.fromHtml(getString(R.string.selected_account_name,
                    destinationAccount.accountName, receivingWallet.name), 0)
        }

        send_btn.setOnClickListener {
            showProcessing()

            val title = PassPromptTitle(R.string.confirm_to_send, R.string.confirm_to_send, R.string.confirm_to_send)
            PassPromptUtil(this@ConfirmTransaction.fragmentActivity, wallet.id, title, allowFingerprint = true) { passDialog, pass ->
                if (pass == null) {
                    showSendButton()
                    return@PassPromptUtil true
                }

                showProcessing()

                GlobalScope.launch(Dispatchers.Default) {
                    val op = this@ConfirmTransaction.javaClass.name + ": " + this.javaClass.name + ": txAuthor.broadcast"
                    try {
                        authoredTxData.txAuthor.broadcast(pass.toByteArray())
                        publishedTx = true
                        passDialog?.dismiss()
                        showSuccess()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showSendButton()

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
                                if(e.message == Btclibwallet.ErrNoPeers) {
//                                    U
                                }else {
                                    Btclibwallet.logT(op, e.message)
                                    Utils.showErrorDialog(context!!, op + ": " + e.message)
                                }
                            }
                        }
                    }
                }

                return@PassPromptUtil false
            }.show()
        }

        go_back.setOnClickListener {
            dismiss()
            if (publishedTx) {
                sendSuccess(true)
            }
        }
    }

    private fun showSendButton() = GlobalScope.launch(Dispatchers.Main) {
        send_btn.show()
        processing_layout.hide()
        success_layout.hide()
        go_back.isEnabled = true
        isCancelable = true
    }

    private fun showSuccess() = GlobalScope.launch(Dispatchers.Main) {
        success_layout.show()
        send_btn.hide()
        processing_layout.hide()
        go_back.isEnabled = true
        isCancelable = false

        sendSuccess(false) // clear estimates
        delay(5000)
    }

    private fun showProcessing() = GlobalScope.launch(Dispatchers.Main) {
        processing_layout.show()
        send_btn.hide()
        success_layout.hide()
        go_back.isEnabled = false
        isCancelable = false
    }
}
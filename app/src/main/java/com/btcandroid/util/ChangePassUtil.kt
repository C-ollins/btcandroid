/*
 * Copyright (c) 2018-2019 The Decred developers
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */

package com.btcandroid.util

import androidx.fragment.app.FragmentActivity
import com.btcandroid.R
import com.btcandroid.data.Constants
import com.btcandroid.dialog.FullScreenBottomSheetDialog
import com.btcandroid.dialog.PasswordPromptDialog
import com.btcandroid.dialog.PinPromptDialog
import com.btcandroid.fragments.PasswordPinDialogFragment
import btclibwallet.Btclibwallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChangePassUtil(private val fragmentActivity: FragmentActivity, val walletID: Long?) {

    private lateinit var passwordPinDialogFragment: PasswordPinDialogFragment
    private var oldPassphrase = ""

    private val multiWallet = WalletData.multiWallet!!

    fun begin() {
        val op = this.javaClass.name + ".begin"
        passwordPinDialogFragment = PasswordPinDialogFragment(R.string.change, walletID != null, true) { dialog, passphrase, passphraseType ->
            changePassphrase(dialog, passphrase, passphraseType)
        }

        val title = PassPromptTitle(R.string.confirm_to_change, R.string.confirm_to_change, R.string.confirm_to_change)
        var passPromptUtil: PassPromptUtil? = null
        passPromptUtil = PassPromptUtil(fragmentActivity, walletID, title, false) { dialog, pass ->

            if (pass != null) {

                GlobalScope.launch(Dispatchers.Default) {

                    try {
                        if (walletID == null) {
                            multiWallet.verifyStartupPassphrase(pass.toByteArray())
                        } else {
                            val wallet = multiWallet.walletWithID(walletID)
                            wallet.unlockWallet(pass.toByteArray())
                            wallet.lockWallet()
                        }

                        withContext(Dispatchers.Main) {
                            dialog?.dismiss()
                        }

                        oldPassphrase = pass

                        passwordPinDialogFragment.tabIndex = if (passPromptUtil!!.passType == Btclibwallet.PassphraseTypePass) {
                            0
                        } else 1

                        passwordPinDialogFragment.show(fragmentActivity)
                    } catch (e: Exception) {
                        e.printStackTrace()

                        if (e.message == Btclibwallet.ErrInvalidPassphrase) {
                            if (dialog is PinPromptDialog) {
                                dialog.setProcessing(false)
                                dialog.showError()
                            } else if (dialog is PasswordPromptDialog) {
                                dialog.setProcessing(false)
                                dialog.showError()
                            }
                        } else {

                            dialog?.dismiss()
                            Btclibwallet.logT(op, e.message)
                            Utils.showErrorDialog(fragmentActivity, op + ": " + e.message)
                        }
                    }
                }

            }

            return@PassPromptUtil false
        }

        passPromptUtil.show()
    }

    private fun changePassphrase(dialog: FullScreenBottomSheetDialog, newPassphrase: String, passphraseType: Int) = GlobalScope.launch(Dispatchers.IO) {

        if (walletID == null) {
            try {
                multiWallet.changeStartupPassphrase(oldPassphrase.toByteArray(), newPassphrase.toByteArray(), passphraseType)

                // saving after a successful change to avoid saving a wrong oldPassphrase
                BiometricUtils.saveToKeystore(fragmentActivity, newPassphrase, Constants.STARTUP_PASSPHRASE)

                SnackBar.showText(fragmentActivity, R.string.startup_security_changed)
            } catch (e: Exception) {
                e.printStackTrace()

                if (e.message == Btclibwallet.ErrInvalidPassphrase) {
                    val passType = multiWallet.startupSecurityType()
                    showError(passType)
                }
            }
        } else {
            try {
                multiWallet.changePrivatePassphraseForWallet(walletID, oldPassphrase.toByteArray(), newPassphrase.toByteArray(), passphraseType)
                if (multiWallet.readBoolConfigValueForKey(walletID.toString() + Btclibwallet.UseBiometricConfigKey, Constants.DEF_USE_FINGERPRINT)) {
                    BiometricUtils.saveToKeystore(fragmentActivity, newPassphrase, BiometricUtils.getWalletAlias(walletID))
                }
                SnackBar.showText(fragmentActivity, R.string.spending_passphrase_changed)
            } catch (e: Exception) {
                if (e.message == Btclibwallet.ErrInvalidPassphrase) {
                    val wallet = multiWallet.walletWithID(walletID)
                    showError(wallet.privatePassphraseType)
                }
            }

        }

        withContext(Dispatchers.Main) {
            passwordPinDialogFragment.dismiss()
        }
    }

    private fun showError(passType: Int) {
        val err = if (passType == Btclibwallet.PassphraseTypePass) {
            R.string.invalid_password
        } else {
            R.string.invalid_pin
        }

        SnackBar.showError(fragmentActivity, err)
    }
}
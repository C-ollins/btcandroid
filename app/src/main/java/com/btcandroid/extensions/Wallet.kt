/*
 * Copyright (c) 2018-2019 The Decred developers
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */

package com.btcandroid.extensions

import com.btcandroid.data.Account
import com.btcandroid.data.Constants
import com.btcandroid.data.parseAccounts
import com.btcandroid.util.WalletData
import btclibwallet.Btclibwallet
import btclibwallet.Wallet

fun Wallet.walletAccounts(): ArrayList<Account> {
    return parseAccounts(this.accounts).accounts
}

fun Wallet.totalWalletBalance(): Long {
    val visibleAccounts = this.walletAccounts()

    return visibleAccounts.map { it.balance.total }.reduce { sum, element -> sum + element }
}

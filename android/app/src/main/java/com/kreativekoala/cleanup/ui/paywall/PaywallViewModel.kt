package com.kreativekoala.cleanup.ui.paywall

import androidx.lifecycle.ViewModel
import com.kreativekoala.cleanup.billing.BillingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    val billingManager: BillingManager
) : ViewModel()

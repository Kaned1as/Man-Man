package com.adonai.manman

import com.android.billingclient.api.*
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.BillingClient.SkuType
import com.android.billingclient.api.BillingFlowParams
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Flavor-specific donation helper class. This manages menu option "Donate" in the main activity.
 *
 * @author Kanedias
 *
 * Created on 10.04.18
 */
class DonateHelper(private val activity: AppCompatActivity) : PurchasesUpdatedListener, ConsumeResponseListener {

    private val billingClient  = BillingClient.newBuilder(activity).enablePendingPurchases().setListener(this).build()
    private var canProceed = false
    private var skuDetails: List<SkuDetails>? = null

    init {
        billingClient.startConnection(object: BillingClientStateListener {

            override fun onBillingServiceDisconnected() {
                // It will try to reconnect later by itself anyway
                canProceed = false
            }

            override fun onBillingSetupFinished(response: BillingResult?) {
                if (response?.responseCode == BillingClient.BillingResponseCode.OK) {
                    canProceed = true
                    activity.lifecycleScope.launchWhenResumed { querySkuDetails() }
                    return
                }

                // non-successful
                Log.e("[Billing]", "Couldn't finish billing setup: ${response?.responseCode}, ${response?.debugMessage}")
            }
        })
    }

    suspend fun querySkuDetails() {
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(listOf("small"))
            .setType(SkuType.INAPP)

        withContext(Dispatchers.IO) {
            billingClient.querySkuDetailsAsync(params.build(), object: SkuDetailsResponseListener {

                override fun onSkuDetailsResponse(response: BillingResult?, resolvedSkuDetails: MutableList<SkuDetails>?) {
                    if (response?.responseCode == BillingClient.BillingResponseCode.OK) {
                        skuDetails = resolvedSkuDetails
                        return
                    }

                    // non-successful
                    Log.e("[Billing]", "Couldn't query SKU details, error code: ${response?.responseCode}, ${response?.debugMessage}")
                }

            })
        }
    }


    fun donate() {
        if (!canProceed || skuDetails.isNullOrEmpty())
            return

        val flowParams = BillingFlowParams.newBuilder().setSkuDetails(skuDetails!!.first()).build()
        val response = billingClient.launchBillingFlow(activity, flowParams)
        if (response.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e("[Billing]", "Couldn't start billing flow, error code: ${response.responseCode}, ${response.debugMessage}")
        }
    }

    /**
     * Redirect those who want to request a feature to the issue tracker
     */
    private fun redirectToIssues() {
        MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.donate)
                .setMessage(R.string.create_issue)
                .setPositiveButton(R.string.understood) {_, _ ->
                    val starter = Intent(Intent.ACTION_VIEW)
                    starter.data = Uri.parse("https://gitlab.com/Kanedias/holywarsoo-android/issues/new")
                    activity.startActivity(starter)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    override fun onPurchasesUpdated(response: BillingResult?, purchases: MutableList<Purchase>?) {
        if (response?.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                billingClient.consumeAsync(ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build(), this)
            }
            return
        }

        if (response?.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.w("[Billing]", "User canceled purchase of donation")
        } else {
            Log.e("[Billing]", "Unexpected error: response code = ${response?.responseCode}, ${response?.debugMessage}")
        }
    }

    override fun onConsumeResponse(response: BillingResult?, purchaseToken: String?) {
        if (response?.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.i("[Billing]", "Purchase consumed, token: $purchaseToken")
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.donate)
                .setMessage(R.string.thanks_for_your_pledge)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(R.string.request_a_feature) {_, _ -> redirectToIssues() }
                .show()
            return
        }

        // not consumed, should not happen
        Log.e("[Billing]", "Consume failed, response code = ${response?.responseCode}, ${response?.debugMessage}")
    }

}
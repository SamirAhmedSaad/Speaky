package com.speakmind.app.premium

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.github.aakira.napier.Napier
import java.lang.ref.WeakReference

/**
 * Google Play Billing for the "premium" subscription (no ads). The last known state is kept so
 * Premium works offline; Play is asked again on every app start.
 */
class BillingManager private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("premium", Context.MODE_PRIVATE)
    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handle(purchases, complete = false)
        }
    }
    private val client = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()
    private var details: ProductDetails? = null
    private var activity = WeakReference<Activity>(null)
    private var connecting = false
    private val main = Handler(Looper.getMainLooper())

    init {
        Premium.setActive(prefs.getBoolean(KEY_ACTIVE, false))
        Premium.purchase = { plan -> purchase(plan) }
        connect()
    }

    /** The activity on screen, for showing the purchase sheet. */
    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
        if (client.isReady) refreshPurchases() else connect()
    }

    private fun connect() {
        if (connecting || client.isReady) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Napier.w { "Billing unavailable: ${result.debugMessage}" }
                    return
                }
                loadProduct()
                refreshPurchases()
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
            }
        })
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            ).build()
        client.queryProductDetailsAsync(params) { result, productDetails ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val product = productDetails.productDetailsList.firstOrNull() ?: run {
                Napier.w { "Subscription '$PRODUCT_ID' not found in Play Console" }
                return@queryProductDetailsAsync
            }
            details = product
            Premium.setPrices(
                PremiumPlan.entries.mapNotNull { plan ->
                    val phase = offerFor(product, plan)?.pricingPhases?.pricingPhaseList?.lastOrNull()
                    phase?.let { plan to PlanPrice(it.formattedPrice, it.priceAmountMicros) }
                }.toMap()
            )
        }
    }

    /** The plain base-plan offer (no intro offer), so the shown price is what the user pays. */
    private fun offerFor(product: ProductDetails, plan: PremiumPlan) =
        product.subscriptionOfferDetails?.filter { it.basePlanId == plan.basePlanId }
            ?.let { offers -> offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() }

    private fun refreshPurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases, complete = true)
        }
    }

    private fun purchase(plan: PremiumPlan) {
        val product = details ?: return
        val offer = offerFor(product, plan) ?: return
        val activity = activity.get() ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            ).build()
        activity.runOnUiThread { client.launchBillingFlow(activity, params) }
    }

    /** [complete]: the list is everything the user owns, so no active purchase means Premium ended. */
    private fun handle(purchases: List<Purchase>, complete: Boolean) {
        val owned = purchases.filter {
            PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        owned.filter { !it.isAcknowledged }.forEach { purchase ->
            // Unacknowledged purchases are refunded by Google after 3 days.
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            ) { ack -> if (ack.responseCode != BillingClient.BillingResponseCode.OK) Napier.w { "Acknowledge failed: ${ack.debugMessage}" } }
        }
        val active = owned.isNotEmpty()
        if (active || complete) {
            prefs.edit().putBoolean(KEY_ACTIVE, active).apply()
            // May continue a pending navigation (see Premium.setActive): main thread.
            main.post { Premium.setActive(active) }
        }
    }

    companion object {
        const val PRODUCT_ID = "premium"
        private const val KEY_ACTIVE = "active"

        @Volatile
        var instance: BillingManager? = null
            private set

        @Synchronized
        fun init(context: Context): BillingManager =
            instance ?: BillingManager(context.applicationContext).also { instance = it }
    }
}

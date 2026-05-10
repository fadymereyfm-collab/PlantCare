package com.example.plantcare.ads

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.plantcare.billing.BillingManager
import com.example.plantcare.billing.ProStatusManager
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Loads and manages a banner AdView; hides it automatically for Pro users.
 *
 * Lifecycle-aware Pro observation: B1 fix. Pre-fix the manager checked
 * `ProStatusManager.isPro` only at `start()` time, so a user who
 * purchased Pro through the in-app paywall while MainActivity was
 * still alive would keep seeing the banner until they navigated away
 * and back (Activity recreate). Now we collect `BillingManager.isPro`
 * and react in real time.
 */
class AdManager(context: Context, private val adView: AdView) {

    // Defensive: callers pass `this` (an Activity) but the manager
    // collects from a process-wide BillingManager StateFlow that
    // outlives any single Activity. Storing the Activity ref here
    // would make a future singleton refactor leak the Activity. Use
    // applicationContext now to make the leak impossible regardless
    // of how the manager's lifetime evolves.
    private val context: Context = context.applicationContext

    private var observerJob: Job? = null

    /** Optional lifecycle owner — when provided, the manager observes the
     *  Pro flag and toggles ad visibility live. */
    fun observeProState(owner: LifecycleOwner) {
        observerJob?.cancel()
        observerJob = owner.lifecycleScope.launch {
            BillingManager.getInstance(context).isPro.collectLatest { pro ->
                if (pro) {
                    adView.visibility = View.GONE
                    adView.pause()
                } else if (adView.visibility != View.VISIBLE) {
                    adView.visibility = View.VISIBLE
                    adView.loadAd(buildAdRequest())
                    adView.resume()
                }
            }
        }
    }

    fun start() {
        if (ProStatusManager.isPro(context)) {
            adView.visibility = View.GONE
            return
        }
        adView.visibility = View.VISIBLE
        adView.loadAd(buildAdRequest())
    }

    /** Wave 2: respects the user's "personalized ads" toggle. When the toggle
     *  is OFF we send an `npa=1` extra to the AdMob adapter so AdMob serves
     *  only non-personalized ads (NPA). Default is ON to preserve historical
     *  behaviour. */
    private fun buildAdRequest(): AdRequest {
        val personalized = context
            .getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getBoolean("ads_personalized", true)
        val builder = AdRequest.Builder()
        if (!personalized) {
            val extras = Bundle().apply { putString("npa", "1") }
            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
        }
        return builder.build()
    }

    fun resume() {
        if (!ProStatusManager.isPro(context)) adView.resume()
    }

    fun pause() {
        adView.pause()
    }

    fun destroy() {
        observerJob?.cancel()
        observerJob = null
        adView.destroy()
    }
}

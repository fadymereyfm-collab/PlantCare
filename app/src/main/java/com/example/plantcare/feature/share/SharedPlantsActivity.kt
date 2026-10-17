package com.example.plantcare.feature.share

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.plantcare.R
import com.example.plantcare.WateringReminder

/**
 * Phase 3 (Wave 2 Family Share, 2026-05-14) — dedicated recipient-side
 * screen. Reachable from Settings → "Mit mir geteilt".
 *
 * What it does:
 *  1. Lists pending invitations (anything addressed to the current
 *     user with status="pending"). User can Accept or Decline inline.
 *  2. Lists active shared plants — each row shows the plant cover,
 *     the owner email, the next open reminder, and a Mark-Done button
 *     that routes through the `markSharedReminderWatered` callable
 *     (Phase 4).
 *
 * Lifecycle: starts [SharedPlantsRepository] in onStart and stops it
 * in onStop so background Firestore reads don't accumulate when the
 * user is elsewhere in the app. Pending invites are queried once on
 * each onStart since `shareInvites` lives at the top level (not
 * watched by the per-user listener).
 */
class SharedPlantsActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: SharedPlantsAdapter

    private var pendingInvites: List<ShareInviteManager.PendingInvite> = emptyList()
    private var activePlants: List<SharedPlantWithReminders> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shared_plants)

        val toolbar: Toolbar = findViewById(R.id.sharedPlantsToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        recycler = findViewById(R.id.sharedPlantsRecycler)
        emptyView = findViewById(R.id.sharedPlantsEmpty)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = SharedPlantsAdapter(buildCallbacks())
        recycler.adapter = adapter

        // Live observer over the active shared-plants listener.
        SharedPlantsRepository.observeSharedPlants().observe(this) { list ->
            activePlants = list ?: emptyList()
            republishItems()
        }
    }

    override fun onStart() {
        super.onStart()
        SharedPlantsRepository.start()
        refreshPendingInvites()
    }

    override fun onStop() {
        super.onStop()
        SharedPlantsRepository.stop()
    }

    private fun refreshPendingInvites() {
        ShareInviteManager.queryPendingInvitesForCurrentUser { list ->
            pendingInvites = list
            republishItems()
        }
    }

    private fun republishItems() {
        val items = mutableListOf<SharedPlantsAdapter.Item>()
        if (pendingInvites.isNotEmpty()) {
            items.add(SharedPlantsAdapter.Item.Header(
                getString(R.string.shared_plants_section_pending)
            ))
            for (inv in pendingInvites) {
                items.add(SharedPlantsAdapter.Item.Pending(inv))
            }
        }
        if (activePlants.isNotEmpty()) {
            items.add(SharedPlantsAdapter.Item.Header(
                getString(R.string.shared_plants_section_active)
            ))
            for (sp in activePlants) {
                items.add(SharedPlantsAdapter.Item.Plant(sp))
            }
        }
        adapter.submit(items)

        val isEmpty = items.isEmpty()
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    // ── Callbacks bridging adapter clicks to ShareInviteManager ──

    private fun buildCallbacks() = object : SharedPlantsAdapter.Callbacks {

        override fun onAcceptInvite(invite: ShareInviteManager.PendingInvite) {
            Toast.makeText(this@SharedPlantsActivity,
                R.string.shared_invite_accept_inflight, Toast.LENGTH_SHORT).show()
            ShareInviteManager.acceptInvite(invite.inviteId) { ok ->
                runOnUiThread {
                    if (ok) {
                        Toast.makeText(this@SharedPlantsActivity,
                            R.string.share_invite_accepted_toast, Toast.LENGTH_SHORT).show()
                        // Optimistically drop this invite from the pending list;
                        // the next refreshPendingInvites tick will reconcile.
                        pendingInvites = pendingInvites.filter { it.inviteId != invite.inviteId }
                        republishItems()
                        // The Cloud Function materializes the pointer doc
                        // asynchronously (a few hundred ms typically). The
                        // SharedPlantsRepository listener will pick it up
                        // when it lands.
                    } else {
                        Toast.makeText(this@SharedPlantsActivity,
                            R.string.share_invite_accept_failed_toast, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        override fun onDeclineInvite(invite: ShareInviteManager.PendingInvite) {
            Toast.makeText(this@SharedPlantsActivity,
                R.string.shared_invite_decline_inflight, Toast.LENGTH_SHORT).show()
            ShareInviteManager.declineInvite(invite.inviteId) { ok ->
                runOnUiThread {
                    if (ok) {
                        Toast.makeText(this@SharedPlantsActivity,
                            R.string.shared_invite_decline_ok, Toast.LENGTH_SHORT).show()
                        pendingInvites = pendingInvites.filter { it.inviteId != invite.inviteId }
                        republishItems()
                    } else {
                        Toast.makeText(this@SharedPlantsActivity,
                            R.string.shared_invite_decline_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        override fun onMarkDone(plant: SharedPlantWithReminders, reminder: WateringReminder) {
            // Phase 4: route through the cross-user callable. The
            // recipient cannot write to the owner's reminder directly
            // (Firestore rules forbid it). The Cloud Function
            // re-validates the share grant and performs the write
            // with admin privileges.
            val reminderId = reminder.id.toString()
            ShareInviteManager.markSharedReminderWatered(plant.ownerUid, reminderId) { ok ->
                runOnUiThread {
                    val msg = if (ok) R.string.shared_plants_mark_done_ok
                              else R.string.shared_plants_mark_done_failed
                    Toast.makeText(this@SharedPlantsActivity, msg, Toast.LENGTH_SHORT).show()
                    // The reminders listener will tick when the CF
                    // updates the owner doc → row updates automatically.
                }
            }
        }

        override fun onPlantClicked(plant: SharedPlantWithReminders) {
            // For v1 of the dedicated screen we just toast the owner.
            // Tapping a plant opening a read-only detail sheet is a
            // follow-up enhancement.
            val owner = plant.ownerEmail
            if (owner.isNotBlank()) {
                Toast.makeText(this@SharedPlantsActivity,
                    getString(R.string.shared_plants_owner_label, owner),
                    Toast.LENGTH_SHORT).show()
            }
        }
    }
}

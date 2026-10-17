package com.example.plantcare.feature.share

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.plantcare.R
import com.example.plantcare.WateringReminder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 3 (Wave 2 Family Share, 2026-05-14) — adapter for the
 * dedicated "Shared with me" screen.
 *
 * Three view types:
 *  - HEADER: section heading ("Pending invitations" / "Active plants")
 *  - PENDING: an invite the recipient can accept or decline
 *  - PLANT: an active shared plant + its next reminder + Mark-Done button
 *
 * Items are submitted as a flat list — caller assembles the order
 * (header → pending items → header → plant items).
 */
class SharedPlantsAdapter(
    private val callbacks: Callbacks
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Callbacks {
        fun onAcceptInvite(invite: ShareInviteManager.PendingInvite)
        fun onDeclineInvite(invite: ShareInviteManager.PendingInvite)
        fun onMarkDone(plant: SharedPlantWithReminders, reminder: WateringReminder)
        fun onPlantClicked(plant: SharedPlantWithReminders)
    }

    sealed class Item {
        data class Header(val text: String) : Item()
        data class Pending(val invite: ShareInviteManager.PendingInvite) : Item()
        data class Plant(val plant: SharedPlantWithReminders) : Item()
    }

    private var items: List<Item> = emptyList()

    fun submit(newItems: List<Item>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.Header -> TYPE_HEADER
        is Item.Pending -> TYPE_PENDING
        is Item.Plant -> TYPE_PLANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(
                inflater.inflate(R.layout.item_shared_section_header, parent, false)
            )
            TYPE_PENDING -> PendingVH(
                inflater.inflate(R.layout.item_pending_invite, parent, false)
            )
            else -> PlantVH(
                inflater.inflate(R.layout.item_shared_plant, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is HeaderVH -> holder.bind(item as Item.Header)
            is PendingVH -> holder.bind(item as Item.Pending, callbacks)
            is PlantVH -> holder.bind(item as Item.Plant, callbacks)
        }
    }

    // ── ViewHolders ─────────────────────────────────────────────

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.sharedSectionHeader)
        fun bind(h: Item.Header) { text.text = h.text }
    }

    class PendingVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.pendingInviteTitle)
        private val subtitle: TextView = view.findViewById(R.id.pendingInviteSubtitle)
        private val acceptBtn: View = view.findViewById(R.id.pendingInviteAccept)
        private val declineBtn: View = view.findViewById(R.id.pendingInviteDecline)

        fun bind(p: Item.Pending, callbacks: Callbacks) {
            val ctx = itemView.context
            val ownerLabel = if (p.invite.fromName.isNotBlank()) p.invite.fromName else p.invite.fromEmail
            // share_invite_dialog_body: "%1$s wants to share \"%2$s\" with you. Accept?"
            title.text = ctx.getString(R.string.share_invite_dialog_body, ownerLabel, p.invite.plantName)
            subtitle.text = p.invite.fromEmail
            acceptBtn.setOnClickListener { callbacks.onAcceptInvite(p.invite) }
            declineBtn.setOnClickListener { callbacks.onDeclineInvite(p.invite) }
        }
    }

    class PlantVH(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.sharedPlantImage)
        private val name: TextView = view.findViewById(R.id.sharedPlantName)
        private val owner: TextView = view.findViewById(R.id.sharedPlantOwner)
        private val reminderText: TextView = view.findViewById(R.id.sharedPlantReminder)
        private val markDoneBtn: View = view.findViewById(R.id.sharedPlantMarkDone)

        fun bind(p: Item.Plant, callbacks: Callbacks) {
            val ctx = itemView.context
            val sp = p.plant
            name.text = if (sp.plantName.isNotBlank()) sp.plantName else sp.plant?.name.orEmpty()
            owner.text = ctx.getString(R.string.shared_plants_owner_label, sp.ownerEmail)

            // Cover image: use the owner's plant.imageUri if available; fall
            // back to the local default. Glide handles content://, http,
            // file://, and raw paths transparently.
            val uri = sp.plant?.imageUri
            if (!uri.isNullOrBlank()) {
                Glide.with(ctx).load(uri)
                    .placeholder(R.drawable.ic_default_plant)
                    .error(R.drawable.ic_default_plant)
                    .into(image)
            } else {
                image.setImageResource(R.drawable.ic_default_plant)
            }

            // Find the soonest open reminder. Reminders come from the
            // owner's subtree via the Phase 1A rule expansion.
            val nextReminder = soonestOpenReminder(sp.reminders)
            if (nextReminder == null) {
                reminderText.text = ctx.getString(R.string.shared_plants_no_reminder)
                markDoneBtn.visibility = View.GONE
            } else {
                reminderText.text = renderReminderState(ctx, nextReminder)
                markDoneBtn.visibility = View.VISIBLE
                markDoneBtn.setOnClickListener { callbacks.onMarkDone(sp, nextReminder) }
            }

            itemView.setOnClickListener { callbacks.onPlantClicked(sp) }
        }

        private fun soonestOpenReminder(list: List<WateringReminder>): WateringReminder? {
            return list
                .filter { !it.done }
                .filter { !it.date.isNullOrBlank() }
                .minByOrNull { it.date ?: "" }
        }

        private fun renderReminderState(
            ctx: android.content.Context,
            r: WateringReminder
        ): String {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            return when {
                r.date == today -> ctx.getString(R.string.shared_plants_today)
                (r.date ?: "") < today ->
                    ctx.getString(R.string.shared_plants_overdue, formatDate(ctx, r.date))
                else ->
                    ctx.getString(R.string.shared_plants_next_reminder, formatDate(ctx, r.date))
            }
        }

        private fun formatDate(ctx: android.content.Context, isoDate: String?): String {
            if (isoDate.isNullOrBlank()) return ""
            return try {
                val src = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dst = java.text.DateFormat.getDateInstance(
                    java.text.DateFormat.MEDIUM, Locale.getDefault()
                )
                src.parse(isoDate)?.let { dst.format(it) } ?: isoDate
            } catch (t: Throwable) {
                isoDate
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PENDING = 1
        private const val TYPE_PLANT = 2
    }
}

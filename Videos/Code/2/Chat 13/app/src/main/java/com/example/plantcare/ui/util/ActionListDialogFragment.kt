package com.example.plantcare.ui.util

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.plantcare.R
import com.google.android.material.button.MaterialButton

/**
 * Polished, reusable "list of actions" dialog. Mirrors the visual shape
 * of `dialog_plant_detail.xml` (the catalog plant-detail dialog the user
 * picked as the unified base):
 *
 *  – modern rounded card background (`bg_dialog_modern`)
 *  – centred bold title + horizontal divider
 *  – scrollable column of MaterialButtons styled identically to the
 *    catalog dialog (Outlined for normal actions, Danger for delete)
 *  – Text-style Abbrechen at the bottom
 *
 * Pre-fix both menus that use this dialog (PlantDetailDialogFragment's
 * "Mehr Optionen" overflow and MyPlantsFragment's room long-press menu)
 * rendered through `MaterialAlertDialogBuilder.setItems(...)`, which the
 * user described as "fakir" (cheap) compared to the rest of the app.
 *
 * Items are passed in as a list before `show()`. Each item carries a
 * label, a destructive flag, and the lambda to run on tap. The fragment
 * dismisses itself before invoking the lambda so anything the action
 * does next (showing another dialog, navigating away) doesn't fight the
 * dismiss animation.
 */
class ActionListDialogFragment : DialogFragment() {

    /** One row in the action list. */
    data class Item(
        val label: String,
        val isDanger: Boolean = false,
        val onClick: () -> Unit
    )

    private var titleText: String = ""
    private var subtitleText: String? = null
    private var items: List<Item> = emptyList()
    private var onDismissedWithoutPick: (() -> Unit)? = null
    private var actionTaken: Boolean = false

    /** Configure the dialog before calling [show]. Must be called
     *  before the dialog binds — typically right after construction. */
    fun configure(title: String, items: List<Item>): ActionListDialogFragment {
        this.titleText = title
        this.items = items
        return this
    }

    /** Optional subtitle line under the title — used by callers that
     *  previously rendered a Material AlertDialog with both title and
     *  message (e.g. the post-capture "Was möchtest du mit dem Foto tun?"
     *  chooser). Hidden when null. */
    fun subtitle(text: String?): ActionListDialogFragment {
        this.subtitleText = text
        return this
    }

    /** Optional: invoked exactly once when the dialog goes away WITHOUT
     *  the user picking an item — i.e. Abbrechen tap, back press, or
     *  tap-outside. Used by call sites whose host UI changes state on
     *  show (e.g. DiseaseDiagnosisActivity disables Save while the
     *  picker is up and needs to re-enable it on cancel). Not invoked
     *  when an item lambda fires, since those typically advance the
     *  flow themselves. */
    fun onDismissedWithoutPick(callback: () -> Unit): ActionListDialogFragment {
        this.onDismissedWithoutPick = callback
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val root = inflater.inflate(R.layout.dialog_action_list, null)

        root.findViewById<TextView>(R.id.actionListTitle).text = titleText

        val subtitleView = root.findViewById<TextView>(R.id.actionListSubtitle)
        val st = subtitleText
        if (!st.isNullOrBlank()) {
            subtitleView.text = st
            subtitleView.visibility = android.view.View.VISIBLE
        } else {
            subtitleView.visibility = android.view.View.GONE
        }

        val container = root.findViewById<LinearLayout>(R.id.actionsContainer)
        val gapPx = (10 * resources.displayMetrics.density).toInt()

        items.forEachIndexed { index, item ->
            val stubLayout =
                if (item.isDanger) R.layout.view_action_danger
                else R.layout.view_action_outlined
            val btn = inflater.inflate(stubLayout, container, false) as MaterialButton
            btn.text = item.label
            btn.setOnClickListener {
                // Dismiss first so the next dialog (rename, delete confirm,
                // photo viewer, etc.) doesn't animate against an active
                // parent. The lambda runs AFTER dismiss so the user
                // perceives a clean handoff.
                actionTaken = true
                dismiss()
                item.onClick()
            }
            // Add a gap between rows by setting topMargin from row 1 onward.
            val lp = btn.layoutParams as LinearLayout.LayoutParams
            if (index > 0) lp.topMargin = gapPx
            btn.layoutParams = lp
            container.addView(btn)
        }

        root.findViewById<MaterialButton>(R.id.buttonActionListCancel)
            .setOnClickListener { dismiss() }

        val dialog = Dialog(requireContext())
        dialog.setContentView(root)
        dialog.setCancelable(true)
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!actionTaken) onDismissedWithoutPick?.invoke()
    }

    override fun onStart() {
        super.onStart()
        // Match every other modal in the app: 92% screen width,
        // transparent window so the bg_dialog_modern card defines the
        // visible edge.
        val dialog = dialog ?: return
        val window = dialog.window ?: return
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        window.windowManager.defaultDisplay.getMetrics(dm)
        val target = (dm.widthPixels * 0.92f).toInt()
        window.setLayout(target, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    companion object {
        const val TAG = "action_list"
    }
}

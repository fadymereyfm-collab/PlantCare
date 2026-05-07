package com.example.plantcare.ui.journal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.plantcare.EmailContext
import com.example.plantcare.R
import com.example.plantcare.data.journal.JournalFilter
import com.example.plantcare.data.journal.JournalSummary
import com.example.plantcare.ui.viewmodel.PlantJournalViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Full-screen DialogFragment for the per-plant journal (Functional Report §4).
 *
 * Hosts the merged timeline (waterings + photos + diagnoses) with filter chips
 * and a header summary card. Launched from PlantDetailDialogFragment.
 */
class PlantJournalDialogFragment : DialogFragment() {

    private val viewModel: PlantJournalViewModel by viewModels()

    private lateinit var adapter: PlantJournalAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var summaryName: android.widget.TextView
    private lateinit var summarySince: android.widget.TextView
    private lateinit var summaryCounters: android.widget.TextView
    private lateinit var summaryMemoCount: android.widget.TextView
    private lateinit var summaryLast: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // STYLE_NORMAL with default theme + MATCH_PARENT in onStart() gives a
        // full-screen dialog without depending on a custom theme attribute.
        setStyle(STYLE_NORMAL, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_plant_journal, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.journalToolbar)
        toolbar.setNavigationOnClickListener { dismiss() }

        adapter = PlantJournalAdapter(
            onWateringNoteRequested = { entry -> showNoteEditor(entry) },
            onDiagnosisClick = { entry ->
                com.example.plantcare.ui.disease.DiagnosisDetailDialog.show(
                    requireContext(), viewLifecycleOwner, entry.diagnosis
                )
            },
            onMemoActionsRequested = { entry -> showMemoActions(entry) }
        )
        recycler = view.findViewById(R.id.journalRecycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        emptyState = view.findViewById(R.id.journalEmptyState)
        summaryName = view.findViewById(R.id.journalPlantName)
        summarySince = view.findViewById(R.id.journalSinceLine)
        summaryCounters = view.findViewById(R.id.journalCounters)
        summaryMemoCount = view.findViewById(R.id.journalMemoCounter)
        summaryLast = view.findViewById(R.id.journalLastWateringLine)

        val chipGroup = view.findViewById<ChipGroup>(R.id.journalFilterChips)
        chipGroup.setOnCheckedStateChangeListener { _, ids ->
            val checked = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val filter = when (checked) {
                R.id.journalChipAll -> JournalFilter.ALL
                R.id.journalChipWatering -> JournalFilter.WATERING
                R.id.journalChipPhotos -> JournalFilter.PHOTOS
                R.id.journalChipDiagnoses -> JournalFilter.DIAGNOSES
                R.id.journalChipMemos -> JournalFilter.MEMOS
                else -> JournalFilter.ALL
            }
            viewModel.setFilter(filter)
        }

        view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.journalAddMemoFab
        ).setOnClickListener { showMemoEditor() }

        viewModel.summary.observe(viewLifecycleOwner) { renderSummary(toolbar, it) }
        viewModel.filteredEntries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            emptyState.visibility = if (entries.isNullOrEmpty()) View.VISIBLE else View.GONE
        }
        // Surface memo CRUD failures the previous fire-and-forget swallowed.
        viewModel.memoError.observe(viewLifecycleOwner) { err ->
            if (err == true) {
                android.widget.Toast.makeText(
                    requireContext(),
                    R.string.journal_memo_save_failed,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.consumeMemoError()
            }
        }

        val plantId = arguments?.getInt(ARG_PLANT_ID, 0) ?: 0
        if (plantId > 0) {
            val email = EmailContext.current(requireContext())
            viewModel.load(plantId, email)
        }
    }

    private fun renderSummary(toolbar: MaterialToolbar, summary: JournalSummary?) {
        if (summary == null) return

        val displayName = summary.plantDisplayName.orEmpty()
        summaryName.text = if (!summary.roomName.isNullOrBlank()) {
            "$displayName · ${summary.roomName}"
        } else displayName

        val since = summary.daysSinceStart
        summarySince.visibility = if (since != null) View.VISIBLE else View.GONE
        if (since != null) {
            // We don't have the plain start-date string; reuse last-watering empty fallback
            // and just show the days count — the precise date is one tap away in detail dialog.
            summarySince.text = getString(R.string.journal_since_format, "—", since)
        }

        summaryCounters.text = getString(
            R.string.journal_counters_format,
            summary.completedWateringCount,
            summary.photoCount,
            summary.diagnosisCount
        )

        if (summary.memoCount > 0) {
            summaryMemoCount.text = getString(R.string.journal_memo_count_format, summary.memoCount)
            summaryMemoCount.visibility = View.VISIBLE
        } else {
            summaryMemoCount.visibility = View.GONE
        }

        val last = summary.lastWateringDate
        if (!last.isNullOrBlank()) {
            summaryLast.text = getString(R.string.journal_last_watering_format, last)
            summaryLast.visibility = View.VISIBLE
        } else {
            summaryLast.visibility = View.GONE
        }

        if (!displayName.isBlank()) {
            toolbar.subtitle = displayName
        }
    }

    /**
     * Long-press editor for a watering entry's free-text note. The journal is
     * intentionally the only place where this note can be set — intercepting the
     * "tick reminder done" path would force a dialog into the highest-frequency
     * action in the app, which would hurt activation more than it helps power
     * users.
     */
    private fun showNoteEditor(entry: com.example.plantcare.data.journal.JournalEntry.WateringEvent) {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
            maxLines = 5
            setHint(R.string.journal_note_hint)
            setText(entry.reminder.notes ?: "")
            setSelection(text.length)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(ctx).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.journal_note_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.journal_note_save) { _, _ ->
                viewModel.saveNoteForReminder(entry.reminder.id, input.text?.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)

        // Sprint-1 Task 1.3: a third button to wipe the note. Only meaningful
        // when there's something to wipe — otherwise we'd offer "delete" on
        // empty content which is confusing.
        if (!entry.reminder.notes.isNullOrBlank()) {
            builder.setNeutralButton(R.string.journal_note_delete) { _, _ ->
                viewModel.saveNoteForReminder(entry.reminder.id, null)
            }
        }
        builder.show()
    }

    /**
     * Sprint-1 Task 1.2 / 1.3: shared memo editor used both for "create" (no
     * existing entry) and "edit" (entry passed in). Centralised so the
     * Toast-on-blank rejection path stays identical between the two flows.
     */
    private fun showMemoEditor(existing: com.example.plantcare.data.journal.JournalEntry.MemoEntry? = null) {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            maxLines = 8
            // Hard cap at 1000 chars matching the soft cap promised in
            // JournalMemo.text. Without this, a user paste of a 50 KB
            // text block would serialise to Room AND (post F10.3) a
            // Firestore upload of the same body. 1000 chars covers a
            // long paragraph — generous for plant observations, gentle
            // on storage and sync quotas.
            filters = arrayOf(android.text.InputFilter.LengthFilter(1000))
            setHint(R.string.journal_memo_hint)
            existing?.let {
                setText(it.memo.text)
                setSelection(text.length)
            }
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(ctx).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        val titleRes = if (existing != null) R.string.journal_memo_edit_dialog_title
                       else R.string.journal_memo_dialog_title
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(titleRes)
            .setView(container)
            .setPositiveButton(R.string.journal_memo_save, null) // overridden below
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text?.toString().orEmpty().trim()
                if (text.isEmpty()) {
                    android.widget.Toast.makeText(
                        ctx, R.string.journal_memo_empty_error, android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                if (existing != null) {
                    viewModel.updateMemo(existing.memo.id, text)
                } else {
                    viewModel.addMemo(text)
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /**
     * Sprint-1 Task 1.3: long-press action sheet for an existing memo —
     * Edit (re-opens the editor pre-filled) or Delete (confirmation prompt).
     */
    private fun showMemoActions(entry: com.example.plantcare.data.journal.JournalEntry.MemoEntry) {
        val ctx = requireContext()
        val items = arrayOf(
            getString(R.string.journal_memo_action_edit),
            getString(R.string.journal_memo_action_delete)
        )
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.journal_memo_actions_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showMemoEditor(entry)
                    1 -> confirmMemoDelete(entry)
                }
            }
            .show()
    }

    private fun confirmMemoDelete(entry: com.example.plantcare.data.journal.JournalEntry.MemoEntry) {
        val ctx = requireContext()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.journal_memo_delete_title)
            .setMessage(R.string.journal_memo_delete_message)
            .setPositiveButton(R.string.journal_memo_delete_confirm) { _, _ ->
                viewModel.deleteMemo(entry.memo.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val ARG_PLANT_ID = "plant_id"
        const val TAG = "plant_journal_dialog"

        @JvmStatic
        fun newInstance(plantId: Int): PlantJournalDialogFragment {
            val f = PlantJournalDialogFragment()
            f.arguments = Bundle().apply { putInt(ARG_PLANT_ID, plantId) }
            return f
        }
    }
}

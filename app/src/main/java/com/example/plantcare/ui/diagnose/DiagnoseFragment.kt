package com.example.plantcare.ui.diagnose

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.plantcare.EmailContext
import com.example.plantcare.R
import com.example.plantcare.ui.disease.DiagnosisDetailDialog
import com.example.plantcare.ui.disease.DiagnosisHistoryActivity
import com.example.plantcare.ui.disease.DiagnosisHistoryAdapter
import com.example.plantcare.ui.disease.DiseaseDiagnosisActivity
import com.example.plantcare.ui.viewmodel.DiseaseDiagnosisViewModel
import com.google.android.material.button.MaterialButton

/**
 * Phase G1.5 skeleton → Phase G6 full landing.
 *
 * Layout fragment_diagnose has three regions:
 *   1. Hero card — title + subtitle + Pill CTA + 3D microscope image.
 *   2. Recent diagnoses — vertical RecyclerView using the existing
 *      DiagnosisHistoryAdapter, capped at 5 items, with a "See all"
 *      affordance that opens DiagnosisHistoryActivity for the full list.
 *   3. Empty state — brackets + 3D pot + headline + body when the user
 *      has no diagnoses yet.
 *
 * Business logic reused from DiagnosisHistoryActivity:
 *   - DiseaseDiagnosisViewModel.observeHistory(userEmail) → LiveData list.
 *   - DiagnosisHistoryAdapter for the rows (preserves swipe-to-delete +
 *     tap-to-open behaviour).
 *   - DiagnosisDetailDialog.show(...) for tap action.
 */
class DiagnoseFragment : Fragment() {

    private lateinit var viewModel: DiseaseDiagnosisViewModel
    private lateinit var historyAdapter: DiagnosisHistoryAdapter

    private var recyclerView: RecyclerView? = null
    private var recentSection: View? = null
    private var emptyContainer: View? = null
    private var startDiagnoseBtn: MaterialButton? = null
    private var seeAllBtn: MaterialButton? = null

    private val maxRecentItems: Int = 5

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_diagnose, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        startDiagnoseBtn = view.findViewById(R.id.startDiagnoseBtn)
        seeAllBtn = view.findViewById(R.id.seeAllDiagnoses)
        recentSection = view.findViewById(R.id.recentDiagnosesSection)
        emptyContainer = view.findViewById(R.id.emptyDiagnoseContainer)
        recyclerView = view.findViewById(R.id.recentDiagnosesRv)

        startDiagnoseBtn?.setOnClickListener {
            startActivity(
                Intent(requireContext(), DiseaseDiagnosisActivity::class.java)
            )
        }

        seeAllBtn?.setOnClickListener {
            startActivity(
                Intent(requireContext(), DiagnosisHistoryActivity::class.java)
            )
        }

        historyAdapter = DiagnosisHistoryAdapter(
            onDelete = { /* no-op on landing — full delete UX lives in the history activity */ },
            onItemClick = { item ->
                DiagnosisDetailDialog.show(requireContext(), requireActivity(), item)
            }
        )
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        recyclerView?.adapter = historyAdapter

        viewModel = ViewModelProvider(this)[DiseaseDiagnosisViewModel::class.java]
        val userEmail = EmailContext.current(requireContext())
        viewModel.observeHistory(userEmail).observe(viewLifecycleOwner) { list ->
            val items = list ?: emptyList()
            if (items.isEmpty()) {
                recentSection?.visibility = View.GONE
                emptyContainer?.visibility = View.VISIBLE
                historyAdapter.submitList(emptyList())
            } else {
                recentSection?.visibility = View.VISIBLE
                emptyContainer?.visibility = View.GONE
                historyAdapter.submitList(items.take(maxRecentItems))
            }
        }
    }

    override fun onDestroyView() {
        recyclerView?.adapter = null
        recyclerView = null
        recentSection = null
        emptyContainer = null
        startDiagnoseBtn = null
        seeAllBtn = null
        super.onDestroyView()
    }
}

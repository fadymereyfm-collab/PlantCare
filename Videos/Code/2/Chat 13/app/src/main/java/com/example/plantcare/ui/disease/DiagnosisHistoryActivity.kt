package com.example.plantcare.ui.disease

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.plantcare.EmailContext
import com.example.plantcare.R
import com.example.plantcare.ui.viewmodel.DiseaseDiagnosisViewModel

/**
 * Zeigt die Historie aller gespeicherten Diagnosen des aktuellen Benutzers.
 *
 * Items sind tappable: ein Tap öffnet [DiagnosisDetailDialog] mit Foto,
 * Beratungstext, Pflanzen-Verknüpfung und Share-Action. Der Lösch-Button
 * im Item bleibt für die schnelle Bereinigung.
 */
class DiagnosisHistoryActivity : AppCompatActivity() {

    private lateinit var viewModel: DiseaseDiagnosisViewModel
    private lateinit var adapter: DiagnosisHistoryAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnosis_history)

        viewModel = ViewModelProvider(this)[DiseaseDiagnosisViewModel::class.java]

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.historyRecyclerView)
        emptyView = findViewById(R.id.txtEmpty)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = DiagnosisHistoryAdapter(
            onDelete = { item -> confirmDelete(item.id) },
            onItemClick = { item -> DiagnosisDetailDialog.show(this, this, item) }
        )
        recyclerView.adapter = adapter

        val userEmail = EmailContext.current(this)

        viewModel.observeHistory(userEmail).observe(this) { list ->
            adapter.submitList(list)
            if (list.isNullOrEmpty()) {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun confirmDelete(id: Long) {
        // Destruktive Aktion landet auf der NEGATIVE-Seite, "Abbrechen" ist
        // POSITIVE — verringert Wahrscheinlichkeit eines versehentlichen Löschens.
        AlertDialog.Builder(this)
            .setTitle(R.string.disease_history_delete_title)
            .setMessage(R.string.disease_history_delete_message)
            .setPositiveButton(R.string.cancel, null)
            .setNegativeButton(R.string.delete) { d, _ ->
                viewModel.deleteDiagnosis(id)
                d.dismiss()
            }
            .show()
    }
}

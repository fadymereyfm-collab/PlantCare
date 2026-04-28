package com.example.plantcare.ui.disease

import android.Manifest
import android.content.Intent
import com.example.plantcare.EmailContext
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.plantcare.DatabaseClient
import com.example.plantcare.Plant
import com.example.plantcare.R
import com.example.plantcare.feature.treatment.TreatmentPlanBuilder
import com.example.plantcare.ui.viewmodel.DiseaseDiagnosisViewModel
import com.example.plantcare.ui.viewmodel.DiseaseUiState
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity zur lokalen Krankheitsdiagnose:
 *
 * 1. Bild aus Kamera oder Galerie wählen.
 * 2. Lokales TFLite-Modell ausführen.
 * 3. Top-Ergebnisse mit Pflegehinweis anzeigen.
 * 4. Ergebnis optional in Room speichern.
 */
class DiseaseDiagnosisActivity : AppCompatActivity() {

    companion object {
        /**
         * Optionaler Intent-Extra: ID der Pflanze, auf die sich diese Diagnose bezieht.
         * Wenn gesetzt (>0), wird die Diagnose beim Speichern direkt mit dieser
         * Pflanze verknüpft — ohne den Pflanzen­auswahl­dialog zu zeigen.
         */
        const val EXTRA_PLANT_ID = "com.example.plantcare.extra.PLANT_ID"
    }

    private lateinit var viewModel: DiseaseDiagnosisViewModel

    /** Aus Intent gelesen. 0 bedeutet „nicht zugeordnet" → Auswahl­dialog beim Speichern. */
    private var targetPlantId: Int = 0

    // Views
    private lateinit var imagePreview: ImageView
    private lateinit var placeholderContainer: LinearLayout
    private lateinit var btnCamera: MaterialButton
    private lateinit var btnGallery: MaterialButton
    private lateinit var btnAnalyze: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnHistory: MaterialButton
    private lateinit var btnTreatmentPlan: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var txtMessage: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var resultsRecyclerView: RecyclerView

    // Camera
    private var photoFile: File? = null
    private var photoUri: Uri? = null

    // Launchers
    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var galleryLauncher: ActivityResultLauncher<String>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>

    private lateinit var adapter: DiseaseResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isDiseaseModelAvailable()) {
            android.widget.Toast.makeText(
                this,
                R.string.disease_feature_unavailable,
                android.widget.Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        setContentView(R.layout.activity_disease_diagnosis)

        // Intent‑Extra lesen: wenn der Aufrufer bereits weiß, um welche Pflanze es geht
        // (z. B. PlantDetailDialog), überspringen wir später den Auswahl­dialog.
        targetPlantId = intent?.getIntExtra(EXTRA_PLANT_ID, 0) ?: 0

        viewModel = ViewModelProvider(this)[DiseaseDiagnosisViewModel::class.java]

        initViews()
        setupLaunchers()
        setupListeners()
        observeViewModel()
    }

    private fun isDiseaseModelAvailable(): Boolean = try {
        assets.list("")?.contains("plant_disease_model.tflite") == true
    } catch (e: Throwable) {
        com.example.plantcare.CrashReporter.log(e)
        false
    }

    private fun initViews() {
        imagePreview = findViewById(R.id.imagePreview)
        placeholderContainer = findViewById(R.id.placeholderContainer)
        btnCamera = findViewById(R.id.btnCamera)
        btnGallery = findViewById(R.id.btnGallery)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        btnSave = findViewById(R.id.btnSave)
        btnHistory = findViewById(R.id.btnHistory)
        btnTreatmentPlan = findViewById(R.id.btnTreatmentPlan)
        progressBar = findViewById(R.id.progressBar)
        txtMessage = findViewById(R.id.txtMessage)
        resultsContainer = findViewById(R.id.resultsContainer)
        resultsRecyclerView = findViewById(R.id.resultsRecyclerView)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        adapter = DiseaseResultAdapter()
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = adapter
    }

    private fun setupLaunchers() {
        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && photoFile != null) {
                showImagePreview(photoFile!!.absolutePath)
                viewModel.setImagePath(photoFile!!.absolutePath)
            }
        }

        galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleGalleryResult(it) }
        }

        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) launchCamera()
            else Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupListeners() {
        btnCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                launchCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        btnAnalyze.setOnClickListener {
            val imagePath = viewModel.selectedImagePath.value ?: return@setOnClickListener
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Toast.makeText(this, R.string.identify_image_missing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.analyze(imageFile)
        }

        btnSave.setOnClickListener {
            val userEmail = EmailContext.current(this)
            // Wenn die Activity mit einer konkreten plantId aufgerufen wurde, direkt speichern.
            // Andernfalls: kurzen Auswahl­dialog zeigen, damit die Historie nicht plantId=0 bekommt
            // (Report 23.04, Fehler 13: Diagnose­eintrag ohne Pflanzen­zuordnung).
            if (targetPlantId > 0) {
                viewModel.saveTopResult(userEmail, plantId = targetPlantId)
            } else {
                promptForPlantAndSave(userEmail)
            }
        }

        btnHistory.setOnClickListener {
            startActivity(Intent(this, DiagnosisHistoryActivity::class.java))
        }

        // Feature 5: Behandlungsplan aus Diagnose
        btnTreatmentPlan.setOnClickListener {
            val top = viewModel.results.value?.firstOrNull() ?: return@setOnClickListener
            if (top.isHealthy) {
                Toast.makeText(this, R.string.treatment_plan_requires_plant, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (targetPlantId <= 0) {
                Toast.makeText(this, R.string.treatment_plan_requires_plant, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val userEmail = EmailContext.current(this)
            val plantIdLocal = targetPlantId
            val diseaseKey = top.diseaseKey
            lifecycleScope.launch {
                val count = withContext(Dispatchers.IO) {
                    val plant = DatabaseClient.getInstance(this@DiseaseDiagnosisActivity)
                        .plantDao().findById(plantIdLocal)
                    val plantName = plant?.nickname?.takeIf { it.isNotBlank() }
                        ?: plant?.name
                    TreatmentPlanBuilder.build(
                        this@DiseaseDiagnosisActivity,
                        plantIdLocal,
                        plantName,
                        userEmail,
                        diseaseKey
                    )
                }
                if (count > 0) {
                    Toast.makeText(
                        this@DiseaseDiagnosisActivity,
                        getString(R.string.treatment_plan_created_toast, count),
                        Toast.LENGTH_LONG
                    ).show()
                    // Damit TodayFragment / Kalender die neuen Einträge gleich sehen.
                    com.example.plantcare.DataChangeNotifier.notifyChange()
                    btnTreatmentPlan.visibility = View.GONE
                } else {
                    Toast.makeText(
                        this@DiseaseDiagnosisActivity,
                        R.string.treatment_plan_requires_plant,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is DiseaseUiState.Idle -> {
                    progressBar.visibility = View.GONE
                    txtMessage.visibility = View.GONE
                    resultsContainer.visibility = View.GONE
                    btnSave.visibility = View.GONE
                    btnTreatmentPlan.visibility = View.GONE
                }
                is DiseaseUiState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    txtMessage.visibility = View.GONE
                    resultsContainer.visibility = View.GONE
                    btnAnalyze.isEnabled = false
                    btnSave.visibility = View.GONE
                    btnTreatmentPlan.visibility = View.GONE
                }
                is DiseaseUiState.Success -> {
                    progressBar.visibility = View.GONE
                    txtMessage.visibility = View.GONE
                    resultsContainer.visibility = View.VISIBLE
                    btnAnalyze.isEnabled = true
                    btnSave.visibility = View.VISIBLE
                    // Behandlungsplan nur anbieten, wenn Ziel-Pflanze bekannt
                    // und Top-Ergebnis nicht "healthy" ist.
                    val top = viewModel.results.value?.firstOrNull()
                    btnTreatmentPlan.visibility = if (
                        targetPlantId > 0 && top != null && !top.isHealthy
                    ) View.VISIBLE else View.GONE
                }
                is DiseaseUiState.NoResults -> {
                    progressBar.visibility = View.GONE
                    txtMessage.text = getString(R.string.disease_no_results)
                    txtMessage.visibility = View.VISIBLE
                    resultsContainer.visibility = View.GONE
                    btnAnalyze.isEnabled = true
                    btnSave.visibility = View.GONE
                    btnTreatmentPlan.visibility = View.GONE
                }
                is DiseaseUiState.Error -> {
                    progressBar.visibility = View.GONE
                    txtMessage.text = getString(R.string.disease_error, state.message)
                    txtMessage.visibility = View.VISIBLE
                    resultsContainer.visibility = View.GONE
                    btnAnalyze.isEnabled = true
                    btnSave.visibility = View.GONE
                    btnTreatmentPlan.visibility = View.GONE
                }
            }
        }

        viewModel.results.observe(this) { list ->
            adapter.submitList(list)
            // Behandlungsplan-Button neu bewerten, sobald Ergebnisse vorliegen
            // (die ui-State-Transition kann vor dem Ergebnis eintreffen).
            val top = list?.firstOrNull()
            val show = viewModel.uiState.value is DiseaseUiState.Success
                    && targetPlantId > 0
                    && top != null
                    && !top.isHealthy
            btnTreatmentPlan.visibility = if (show) View.VISIBLE else View.GONE
        }

        viewModel.selectedImagePath.observe(this) { path ->
            btnAnalyze.isEnabled = !path.isNullOrEmpty()
        }

        viewModel.saved.observe(this) { s ->
            if (s == true) {
                Toast.makeText(this, R.string.disease_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchCamera() {
        try {
            photoFile = createImageFile()
            photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                photoFile!!
            )
            cameraLauncher.launch(photoUri!!)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.camera_file_create_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGalleryResult(uri: Uri) {
        try {
            val file = createImageFile()
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            showImagePreview(file.absolutePath)
            viewModel.setImagePath(file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.camera_file_create_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showImagePreview(imagePath: String) {
        placeholderContainer.visibility = View.GONE
        imagePreview.visibility = View.VISIBLE
        Glide.with(this)
            .load(File(imagePath))
            .centerCrop()
            .into(imagePreview)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("DISEASE_${timeStamp}_", ".jpg", storageDir)
    }

    /**
     * Zeigt einen kurzen Auswahl­dialog mit den Pflanzen des aktuellen Nutzers
     * und speichert die Diagnose anschließend mit der korrekten plantId.
     *
     * Wenn der Nutzer keine Pflanzen hat oder „Keine Pflanze" wählt, wird mit
     * plantId=0 gespeichert (entspricht dem alten Verhalten, aber bewusst).
     */
    private fun promptForPlantAndSave(userEmail: String?) {
        lifecycleScope.launch {
            val plants: List<Plant> = withContext(Dispatchers.IO) {
                try {
                    if (userEmail.isNullOrBlank()) {
                        DatabaseClient.getInstance(this@DiseaseDiagnosisActivity)
                            .plantDao().getAllUserPlants()
                    } else {
                        DatabaseClient.getInstance(this@DiseaseDiagnosisActivity)
                            .plantDao().getAllUserPlantsForUser(userEmail)
                    } ?: emptyList()
                } catch (t: Throwable) {
                    t.printStackTrace()
                    emptyList()
                }
            }

            if (plants.isEmpty()) {
                Toast.makeText(
                    this@DiseaseDiagnosisActivity,
                    R.string.disease_pick_plant_empty,
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.saveTopResult(userEmail, plantId = 0)
                return@launch
            }

            // Anzeige­text bevorzugt Nickname, sonst Name.
            val labels = plants.map { p ->
                val nick = p.nickname?.takeIf { it.isNotBlank() }
                nick ?: p.name ?: "Pflanze #${p.id}"
            }.toTypedArray()

            AlertDialog.Builder(this@DiseaseDiagnosisActivity)
                .setTitle(R.string.disease_pick_plant_title)
                .setItems(labels) { dlg, which ->
                    val chosen = plants[which]
                    viewModel.saveTopResult(userEmail, plantId = chosen.id)
                    dlg.dismiss()
                }
                .setNeutralButton(R.string.disease_pick_plant_none) { dlg, _ ->
                    viewModel.saveTopResult(userEmail, plantId = 0)
                    dlg.dismiss()
                }
                .setNegativeButton(R.string.disease_pick_plant_cancel, null)
                .show()
        }
    }
}

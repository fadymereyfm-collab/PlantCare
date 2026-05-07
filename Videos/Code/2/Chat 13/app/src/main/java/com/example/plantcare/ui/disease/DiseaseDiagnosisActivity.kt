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
import com.example.plantcare.BuildConfig
import com.example.plantcare.CrashReporter
import com.example.plantcare.Plant
import com.example.plantcare.PlantPhoto
import com.example.plantcare.R
import com.example.plantcare.data.plantnet.PlantNetError
import com.example.plantcare.feature.treatment.TreatmentPlanBuilder
import com.example.plantcare.ui.viewmodel.DiseaseDiagnosisViewModel
import com.example.plantcare.ui.viewmodel.DiseaseUiState
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity für die cloud-basierte Krankheitsdiagnose (Google Gemini 2.5 Flash):
 *
 * 1. Bild aus Kamera oder Galerie wählen.
 * 2. Foto an Gemini senden (nach einmaliger DSGVO-Einwilligung).
 * 3. Top-Ergebnisse (max. 3) mit Pflegehinweis anzeigen.
 * 4. Ergebnis optional in Room speichern; bei verknüpfter Pflanze
 *    zusätzlich ins Foto-Archiv übernehmen.
 *
 * Das ältere lokale TFLite-Modell wurde am 2026-05-01 ausgemustert
 * (PROGRESS.md F3-gemini), zugunsten besserer Erkennung für Zimmerpflanzen.
 */
class DiseaseDiagnosisActivity : AppCompatActivity() {

    companion object {
        /**
         * Optionaler Intent-Extra: ID der Pflanze, auf die sich diese Diagnose bezieht.
         * Wenn gesetzt (>0), wird die Diagnose beim Speichern direkt mit dieser
         * Pflanze verknüpft — ohne den Pflanzen­auswahl­dialog zu zeigen.
         */
        const val EXTRA_PLANT_ID = "com.example.plantcare.extra.PLANT_ID"

        /**
         * Optional Intent-Extra: absoluter Pfad zu einer bereits aufgenommenen
         * Fotodatei. Wenn gesetzt, übernimmt die Activity dieses Foto direkt
         * als Vorschau und aktiviert den Analyze-Button — die Kamera-/Galerie-
         * Buttons müssen nicht erneut benutzt werden. Genutzt vom Calendar
         * "Quick-Action" Pfad (Foto im Kalender → "Krankheits-Check").
         */
        const val EXTRA_PRELOADED_IMAGE_PATH = "com.example.plantcare.extra.PRELOADED_IMAGE_PATH"

        /** Plain-prefs key for the one-time DSGVO acceptance flag. */
        private const val PREF_DISEASE_CONSENT = "disease_diagnosis_consent_accepted"

        /** Bundle key for [treatmentPlanCreated]. */
        private const val STATE_TREATMENT_PLAN_CREATED = "state.treatment_plan_created"
    }

    private lateinit var viewModel: DiseaseDiagnosisViewModel

    /** Aus Intent gelesen. 0 bedeutet „nicht zugeordnet" → Auswahl­dialog beim Speichern. */
    private var targetPlantId: Int = 0

    /**
     * Wahr, sobald der Nutzer für die aktuelle Diagnose schon einen Behandlungs-
     * plan erzeugt hat — verhindert, dass der Button nach Rotation erneut sichtbar
     * wird und ein zweiter Plan angelegt werden kann.
     * Wird in [onSaveInstanceState] / [onRestoreInstanceState] persistiert.
     */
    private var treatmentPlanCreated: Boolean = false

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

    private lateinit var adapter: DiseaseCandidateAdapter
    private lateinit var btnNoneMatch: MaterialButton
    private lateinit var txtSelectedCandidate: TextView
    private lateinit var cardPlantSpecies: View
    private lateinit var txtPlantSpecies: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disease_diagnosis)

        // Intent‑Extra lesen: wenn der Aufrufer bereits weiß, um welche Pflanze es geht
        // (z. B. PlantDetailDialog), überspringen wir später den Auswahl­dialog.
        targetPlantId = intent?.getIntExtra(EXTRA_PLANT_ID, 0) ?: 0

        viewModel = ViewModelProvider(this)[DiseaseDiagnosisViewModel::class.java]

        treatmentPlanCreated = savedInstanceState
            ?.getBoolean(STATE_TREATMENT_PLAN_CREATED, false) == true

        initViews()
        setupLaunchers()
        setupListeners()
        observeViewModel()

        // B3: wenn die Activity mit einem vor-erfassten Foto geöffnet wurde
        // (Calendar Quick-Action), Vorschau direkt setzen damit der Nutzer nur
        // einmal auf "Analyze" tippen muss.
        // Nur beim ersten Öffnen — bei Rotation werden Pfad+Vorschau via VM
        // wiederhergestellt, ein erneutes setImagePath würde uiState resetten.
        //
        // Route the preloaded file through the same prepare pipeline as
        // camera + gallery captures. PhotoCaptureCoordinator hands us the
        // RAW image (full camera resolution, EXIF-as-is) — without
        // running it through prepareImageForDiagnosis, the calendar-
        // quick-action path would send a portrait shot to Gemini sideways
        // AND upload the full 12 MB / 16 MB-base64 body, defeating the
        // whole F9.5 pipeline. The original DISEASE_*.jpg from
        // PhotoCaptureCoordinator is owned by us (it's a temp copy
        // specifically for diagnosis) so we delete it after preparing.
        if (savedInstanceState == null) {
            val preloaded = intent?.getStringExtra(EXTRA_PRELOADED_IMAGE_PATH)
            if (!preloaded.isNullOrBlank()) {
                val f = File(preloaded)
                if (f.exists() && f.length() > 0L) {
                    runPrepareAndApply(Uri.fromFile(f), rawCaptureToDelete = f)
                }
            }
        }

        // D19: Best-effort cleanup of orphan DISEASE_*.jpg temp files older
        // than 30 days. Each Camera/Gallery tap creates one; if the user backs
        // out before saving, the file is never referenced from the DB and
        // accumulates forever. We run this on activity start (not in a worker)
        // because it's bounded I/O on a small subdir and only runs while the
        // user is in this screen anyway.
        //
        // Walks BOTH locations to handle the F9.1 subdir migration:
        //   1. The new `disease/` subdir (where new captures land).
        //   2. The legacy Pictures/ root (where old installs wrote
        //      DISEASE_*.jpg before the subdir was introduced) — those
        //      files would otherwise live forever after upgrade.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val baseDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return@launch
                val cutoff = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
                val diseaseDir = File(baseDir, "disease")
                if (diseaseDir.isDirectory) {
                    diseaseDir.listFiles { f ->
                        f.isFile && f.name.startsWith("DISEASE_") && f.lastModified() < cutoff
                    }?.forEach { runCatching { it.delete() } }
                }
                // Legacy DISEASE_*.jpg in Pictures root — `isFile` filter
                // protects the new disease/ subdir and any sibling dirs
                // (identify/, PlantCare/) from accidental traversal.
                baseDir.listFiles { f ->
                    f.isFile && f.name.startsWith("DISEASE_")
                            && f.name.endsWith(".jpg")
                            && f.lastModified() < cutoff
                }?.forEach { runCatching { it.delete() } }
            } catch (t: Throwable) {
                CrashReporter.log(t)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_TREATMENT_PLAN_CREATED, treatmentPlanCreated)
    }

    /**
     * One-time DSGVO consent — the cloud-based diagnosis ships images to
     * Google Gemini. We persist the acceptance flag in the plain
     * `prefs` SharedPreferences (boolean per device, no PII).
     * Calls [action] once the user has accepted; on decline, surfaces a
     * brief toast so the tap on "Analyze" doesn't appear to do nothing.
     */
    private fun ensureConsentThen(action: () -> Unit) {
        val prefs = getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_DISEASE_CONSENT, false)) {
            action()
            return
        }
        // D43: AlertDialog.setMessage() renders plain text — the Google
        // privacy URL was visible but unclickable. We inflate a TextView
        // ourselves and run Linkify on it so the user can actually open the
        // policy in their browser instead of having to copy-paste.
        val padPx = (24f * resources.displayMetrics.density).toInt()
        val msgView = TextView(this).apply {
            text = getString(R.string.disease_consent_message)
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@DiseaseDiagnosisActivity, R.color.pc_onSurface))
            setLineSpacing(0f, 1.2f)
            setPadding(padPx, padPx, padPx, 0)
            android.text.util.Linkify.addLinks(this, android.text.util.Linkify.WEB_URLS)
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.disease_consent_title)
            .setView(msgView)
            .setPositiveButton(R.string.disease_consent_accept) { dlg, _ ->
                prefs.edit().putBoolean(PREF_DISEASE_CONSENT, true).apply()
                dlg.dismiss()
                action()
            }
            .setNegativeButton(R.string.disease_consent_decline) { dlg, _ ->
                dlg.dismiss()
                Toast.makeText(
                    this,
                    R.string.disease_consent_declined_toast,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setCancelable(true)
            .show()
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
        // D38: History also accessible from a toolbar IconButton — the older
        // text button below the Analyze button is kept for discoverability.
        findViewById<ImageButton>(R.id.btnHistoryToolbar).setOnClickListener {
            startActivity(Intent(this, DiagnosisHistoryActivity::class.java))
        }

        btnNoneMatch = findViewById(R.id.btnNoneMatch)
        txtSelectedCandidate = findViewById(R.id.txtSelectedCandidate)
        cardPlantSpecies = findViewById(R.id.cardPlantSpecies)
        txtPlantSpecies = findViewById(R.id.txtPlantSpecies)

        adapter = DiseaseCandidateAdapter(onMatch = ::onCandidateMatched)
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = adapter
    }

    private fun setupLaunchers() {
        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val captured = photoFile
            // Snapshot+clear the raw capture pointer immediately so a
            // rapid second capture (which would overwrite `photoFile`)
            // can't get its result aliased back to the previous BG
            // launch. Mirrors the race fix from PlantIdentifyActivity.
            photoFile = null
            photoUri = null
            if (success && captured != null) {
                runPrepareAndApply(Uri.fromFile(captured), rawCaptureToDelete = captured)
            } else if (captured != null) {
                // Camera cancelled (back-press, no shot taken). launchCamera()
                // pre-creates the JPEG file via FileProvider so the camera
                // app has a destination — that file is now empty (or near-
                // empty) and would otherwise sit in disease/ until the
                // 30-day prune. Delete it now to keep the dir tidy and
                // avoid users accumulating dozens of zero-byte files from
                // accidental cancels.
                runCatching { captured.delete() }
            }
        }

        galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleGalleryResult(it) }
        }

        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                launchCamera()
            } else {
                // D17: when the user has permanently denied (rationale won't show
                // anymore), surface a Snackbar-style action that opens app
                // settings — otherwise a plain toast leaves them stuck.
                val rationaleAvailable = androidx.core.app.ActivityCompat
                    .shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)
                if (rationaleAvailable) {
                    Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(this)
                        .setMessage(R.string.camera_permission_required)
                        .setPositiveButton(R.string.disease_camera_permission_settings) { _, _ ->
                            startActivity(Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null)
                            ))
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
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
            // D40: existence + non-empty check. A 0-byte file would be sent to
            // Gemini and trigger a confusing "Bild ungültig" error 60 seconds later.
            if (!imageFile.exists() || imageFile.length() == 0L) {
                Toast.makeText(this, R.string.identify_image_missing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ensureConsentThen { viewModel.analyze(imageFile) }
        }

        btnSave.setOnClickListener {
            // Race guard: rapid double-tap would otherwise insert two
            // duplicate disease_diagnosis rows AND fire the archive prompt
            // twice. Disable until the saved observer hides the button on
            // success, or until the user re-runs an analyze (which restores
            // a fresh enabled button via the Success state).
            if (!btnSave.isEnabled) return@setOnClickListener
            btnSave.isEnabled = false
            val userEmail = EmailContext.current(this)
            // Wenn die Activity mit einer konkreten plantId aufgerufen wurde, direkt speichern
            // (mit Spezies-Konflikt-Check). Andernfalls: kurzen Auswahl­dialog zeigen, damit
            // die Historie nicht plantId=0 bekommt (Report 23.04, Fehler 13).
            if (targetPlantId > 0) {
                confirmAndSave(targetPlantId, userEmail)
            } else {
                promptForPlantAndSave(userEmail)
            }
        }

        btnNoneMatch.setOnClickListener {
            // User rejected all current candidates after looking at the
            // reference images. Re-run analysis with the current keys flagged
            // as excluded so Gemini proposes alternatives.
            val imagePath = viewModel.selectedImagePath.value ?: return@setOnClickListener
            val imageFile = File(imagePath)
            if (!imageFile.exists() || imageFile.length() == 0L) {
                Toast.makeText(this, R.string.identify_image_missing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ensureConsentThen { viewModel.rejectAllAndReanalyze(imageFile) }
        }

        btnHistory.setOnClickListener {
            startActivity(Intent(this, DiagnosisHistoryActivity::class.java))
        }

        // Feature 5: Behandlungsplan aus Diagnose. Uses the user-selected
        // candidate (from the differential-diagnosis cards) so the plan
        // matches what the user visually confirmed, not necessarily Gemini's
        // top-1 guess.
        btnTreatmentPlan.setOnClickListener {
            // Race guard: TreatmentPlanBuilder.build inserts 4 reminders
            // per call. A double-tap would create 8 reminders (4 normal +
            // 4 duplicate), each appearing in TodayFragment and the
            // calendar. Disable the button up-front; on the success path
            // it gets hidden via `treatmentPlanCreated`, on failure we
            // re-enable so the user can retry.
            if (!btnTreatmentPlan.isEnabled) return@setOnClickListener
            val top = viewModel.selectedCandidate.value
                ?: viewModel.results.value?.firstOrNull()
                ?: return@setOnClickListener
            if (top.isHealthy) {
                Toast.makeText(this, R.string.treatment_plan_requires_plant, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (targetPlantId <= 0) {
                Toast.makeText(this, R.string.treatment_plan_requires_plant, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnTreatmentPlan.isEnabled = false
            val userEmail = EmailContext.current(this)
            val plantIdLocal = targetPlantId
            val diseaseKey = top.diseaseKey
            lifecycleScope.launch {
                val count = withContext(Dispatchers.IO) {
                    val plant = com.example.plantcare.data.repository.PlantRepository
                        .getInstance(this@DiseaseDiagnosisActivity)
                        .findByIdBlocking(plantIdLocal)
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
                    treatmentPlanCreated = true
                    btnTreatmentPlan.visibility = View.GONE
                } else {
                    Toast.makeText(
                        this@DiseaseDiagnosisActivity,
                        R.string.treatment_plan_requires_plant,
                        Toast.LENGTH_SHORT
                    ).show()
                    // Re-enable so the user can retry — no plan was actually
                    // created.
                    btnTreatmentPlan.isEnabled = true
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
                    btnNoneMatch.visibility = View.GONE
                    txtSelectedCandidate.visibility = View.GONE
                    // Reset analyze label after Error → Idle transition
                    btnAnalyze.text = getString(R.string.disease_analyze_button)
                }
                is DiseaseUiState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    txtMessage.visibility = View.GONE
                    resultsContainer.visibility = View.GONE
                    btnAnalyze.isEnabled = false
                    btnSave.visibility = View.GONE
                    btnTreatmentPlan.visibility = View.GONE
                    btnNoneMatch.visibility = View.GONE
                    txtSelectedCandidate.visibility = View.GONE
                }
                is DiseaseUiState.Success -> {
                    progressBar.visibility = View.GONE
                    txtMessage.visibility = View.GONE
                    resultsContainer.visibility = View.VISIBLE
                    btnAnalyze.isEnabled = true
                    // Save is hidden until the user picks a candidate via
                    // "Passt zu meinem Foto" — that's the explicit visual
                    // confirmation step the differential-diagnosis flow asks
                    // for. The selectedCandidate observer flips this on.
                    btnSave.visibility = View.GONE
                    // None-match is hidden when the only candidate is
                    // healthy / unclear (nothing meaningful to reject); the
                    // results observer below recomputes this.
                    btnNoneMatch.visibility = View.VISIBLE
                    btnTreatmentPlan.visibility = View.GONE
                }
                is DiseaseUiState.NoResults -> {
                    progressBar.visibility = View.GONE
                    val baseMsg = getString(R.string.disease_no_results)
                    // Diagnostic suffix is dev-only — end users shouldn't see raw
                    // JSON snippets. In release the message stays clean.
                    txtMessage.text = if (BuildConfig.DEBUG && !state.bodyPreview.isNullOrBlank()) {
                        "$baseMsg\n\nDebug (200 OK): ${state.bodyPreview}"
                    } else {
                        baseMsg
                    }
                    txtMessage.visibility = View.VISIBLE
                    resultsContainer.visibility = View.GONE
                    btnAnalyze.isEnabled = true
                    btnSave.visibility = View.GONE
                    btnTreatmentPlan.visibility = View.GONE
                    btnNoneMatch.visibility = View.GONE
                    txtSelectedCandidate.visibility = View.GONE
                }
                is DiseaseUiState.PlantNotDetected -> {
                    progressBar.visibility = View.GONE
                    txtMessage.text = getString(R.string.disease_no_plant_detected)
                    txtMessage.visibility = View.VISIBLE
                    resultsContainer.visibility = View.GONE
                    btnAnalyze.isEnabled = true
                    btnSave.visibility = View.GONE
                    btnTreatmentPlan.visibility = View.GONE
                    btnNoneMatch.visibility = View.GONE
                    txtSelectedCandidate.visibility = View.GONE
                }
                is DiseaseUiState.Error -> {
                    progressBar.visibility = View.GONE
                    val baseMessage = getString(errorStringFor(state.type))
                    // Raw HTTP body / exception detail is dev-only — exposing it
                    // to end users looks like a crash and leaks API internals.
                    val showDetail = BuildConfig.DEBUG &&
                            (state.type == PlantNetError.UNKNOWN
                                    || state.type == PlantNetError.SERVER_ERROR)
                    txtMessage.text = if (showDetail && !state.rawMessage.isNullOrBlank()) {
                        "$baseMessage\n\nDebug: ${state.rawMessage}"
                    } else {
                        baseMessage
                    }
                    txtMessage.visibility = View.VISIBLE
                    resultsContainer.visibility = View.GONE
                    btnAnalyze.isEnabled = true
                    btnSave.visibility = View.GONE
                    btnTreatmentPlan.visibility = View.GONE
                    btnNoneMatch.visibility = View.GONE
                    txtSelectedCandidate.visibility = View.GONE
                    // D11: change the Analyze button label to "Try again" so
                    // the user has an obvious recovery action without having to
                    // re-pick the same image.
                    btnAnalyze.text = getString(R.string.disease_retry)
                }
            }
        }

        viewModel.results.observe(this) { list ->
            adapter.submitList(list)
            // None-match makes no sense when:
            //  • there's only one candidate AND it's healthy/unclear, or
            //  • the list is empty.
            val hasReal = list.orEmpty().any {
                !it.isHealthy &&
                        !it.diseaseKey.equals("healthy", ignoreCase = true) &&
                        !it.diseaseKey.equals("unclear", ignoreCase = true)
            }
            btnNoneMatch.visibility = if (hasReal) View.VISIBLE else View.GONE
        }

        viewModel.referenceImages.observe(this) { map ->
            adapter.setReferenceImages(map ?: emptyMap())
        }

        viewModel.plantSpecies.observe(this) { species ->
            if (species.isNullOrBlank()) {
                cardPlantSpecies.visibility = View.GONE
            } else {
                cardPlantSpecies.visibility = View.VISIBLE
                txtPlantSpecies.text = getString(R.string.disease_plant_species_label, species)
            }
        }

        viewModel.selectedCandidate.observe(this) { chosen ->
            adapter.setSelectedKey(chosen?.diseaseKey)
            if (chosen == null) {
                btnSave.visibility = View.GONE
                btnTreatmentPlan.visibility = View.GONE
                txtSelectedCandidate.visibility = View.GONE
            } else {
                txtSelectedCandidate.visibility = View.VISIBLE
                txtSelectedCandidate.text = getString(
                    R.string.disease_selected_candidate_label,
                    chosen.displayName
                )
                btnSave.visibility = View.VISIBLE
                // Selecting a (possibly different) candidate is a fresh
                // save opportunity — reset isEnabled in case a previous
                // save attempt was cancelled and left us disabled.
                btnSave.isEnabled = true
                // Treatment plan only when we have a target plant and a
                // non-healthy chosen diagnosis. Reset isEnabled so a
                // previously-failed build doesn't leave us stuck.
                btnTreatmentPlan.isEnabled = true
                btnTreatmentPlan.visibility = if (
                    !treatmentPlanCreated
                    && targetPlantId > 0
                    && !chosen.isHealthy
                ) View.VISIBLE else View.GONE
            }
        }

        viewModel.selectedImagePath.observe(this) { path ->
            btnAnalyze.isEnabled = !path.isNullOrEmpty()
        }

        viewModel.saved.observe(this) { s ->
            if (s == true) {
                Toast.makeText(this, R.string.disease_saved, Toast.LENGTH_SHORT).show()
                // Hide the save button so a stray second tap doesn't insert a
                // duplicate diagnosis row (and re-trigger the archive prompt).
                // It will reappear on the next Success state from a new analyze.
                btnSave.visibility = View.GONE
            }
        }

        // Wenn die Diagnose mit einer konkreten Pflanze verknüpft ist, dem Nutzer
        // anbieten, das Foto + den Krankheitshinweis ins Foto-Archiv der Pflanze zu
        // übernehmen (so taucht es zusätzlich beim Pflanzendetail auf, nicht nur im
        // Diagnose-Verlauf).
        viewModel.savedDiagnosis.observe(this) { event ->
            if (event == null || event.id <= 0L) return@observe
            // Single-shot — clear immediately so config changes (rotation, dark
            // mode toggle) don't replay the archive prompt.
            viewModel.consumeSavedDiagnosisId()
            // Use the plantId that was actually saved with the diagnosis (which
            // covers both the intent-extra path AND the picker-dialog path),
            // not just `targetPlantId`. Fixes D28: archive prompt was hidden
            // when user reached this screen via the "general" flow but then
            // picked a plant in the picker dialog.
            val effectivePlantId = event.plantId
            if (effectivePlantId <= 0) return@observe
            val chosen = viewModel.selectedCandidate.value
                ?: viewModel.results.value?.firstOrNull()
                ?: return@observe
            val imagePath = viewModel.selectedImagePath.value ?: return@observe
            promptToAddToArchive(effectivePlantId, imagePath, chosen, event.id.toInt())
        }
    }

    /**
     * Save flow with a species-vs-target-plant sanity check. When the
     * user has a target plant assigned (either via intent extra or the
     * picker dialog) AND Gemini identified a plant species, we compare
     * the names — a mismatch (e.g. user assigns to "Pothos" but Gemini
     * sees "Monstera") surfaces a warning dialog asking whether to
     * proceed anyway. If the user has no target plant or Gemini didn't
     * identify a species, we save directly.
     */
    private fun confirmAndSave(plantId: Int, userEmail: String?) {
        val species = viewModel.plantSpecies.value
        if (plantId <= 0 || species.isNullOrBlank()) {
            viewModel.saveSelectedResult(userEmail, plantId = plantId)
            return
        }
        lifecycleScope.launch {
            val plant: Plant? = withContext(Dispatchers.IO) {
                try {
                    com.example.plantcare.data.repository.PlantRepository
                        .getInstance(this@DiseaseDiagnosisActivity)
                        .findByIdBlocking(plantId)
                } catch (t: Throwable) {
                    CrashReporter.log(t)
                    null
                }
            }
            if (plant == null || isSpeciesMatch(species, plant)) {
                viewModel.saveSelectedResult(userEmail, plantId = plantId)
            } else {
                showSpeciesMismatchDialog(species, plant) {
                    viewModel.saveSelectedResult(userEmail, plantId = plantId)
                }
            }
        }
    }

    /**
     * Heuristic species/plant-name match. Splits both names into 4+ char
     * tokens and looks for any overlap (case-insensitive, substring on
     * either side). Tolerates the common "Monstera (Monstera deliciosa)"
     * Gemini format vs. user's own short label "Monstera" or full name.
     * Returns true (= no warning) when name fields are missing — we
     * don't want to badger the user with false-positive warnings.
     */
    private fun isSpeciesMatch(detectedSpecies: String, plant: Plant): Boolean {
        val combined = listOfNotNull(plant.name, plant.nickname)
            .joinToString(" ")
            .lowercase()
        val tokens = combined.split(Regex("\\W+")).filter { it.length >= 4 }
        if (tokens.isEmpty()) return true
        val s = detectedSpecies.lowercase()
        return tokens.any { token ->
            s.contains(token) || token.contains(s.split(Regex("\\W+")).firstOrNull().orEmpty())
        }
    }

    private fun showSpeciesMismatchDialog(
        detectedSpecies: String,
        plant: Plant,
        onProceed: () -> Unit
    ) {
        if (isFinishing || isDestroyed) {
            onProceed()
            return
        }
        val plantLabel = plant.nickname?.takeIf { it.isNotBlank() }
            ?: plant.name
            ?: "?"
        // Re-enable the save button if the user cancels OR dismisses
        // (back-press, outside-tap). The save click handler disabled it
        // up-front to prevent a double-tap insert; cancelling means no
        // save happened so the user must be allowed to retry.
        AlertDialog.Builder(this)
            .setTitle(R.string.disease_species_mismatch_title)
            .setMessage(
                getString(
                    R.string.disease_species_mismatch_message,
                    detectedSpecies,
                    plantLabel
                )
            )
            .setPositiveButton(R.string.disease_species_mismatch_proceed) { d, _ ->
                d.dismiss()
                onProceed()
            }
            .setNegativeButton(R.string.disease_species_mismatch_cancel) { d, _ ->
                d.dismiss()
                btnSave.isEnabled = true
            }
            .setOnCancelListener { btnSave.isEnabled = true }
            .setCancelable(true)
            .show()
    }

    /**
     * Invoked when the user taps "Passt zu meinem Foto" on one of the
     * candidate cards. Records the selection in the VM, then asks Android
     * to scroll btnSave into view so the user actually sees the commit
     * row appear (otherwise on a phone Save sits below the fold and the
     * tap looks like a no-op).
     *
     * Earlier attempt: `scrollView.smoothScrollTo(0, btnSave.bottom)` —
     * unreliable because `btnSave.bottom` is read before the layout pass
     * has run on the freshly-VISIBLE button, returning 0 (= scroll to top).
     * `requestRectangleOnScreen` bubbles up through the view hierarchy and
     * the parent ScrollView handles the scroll after layout has settled,
     * which is the documented Android API for exactly this case.
     */
    private fun onCandidateMatched(chosen: com.example.plantcare.data.disease.DiseaseResult) {
        viewModel.selectCandidate(chosen)
        btnSave.post {
            if (btnSave.visibility == View.VISIBLE) {
                btnSave.requestRectangleOnScreen(
                    android.graphics.Rect(0, 0, btnSave.width, btnSave.height),
                    false
                )
            }
        }
    }

    /**
     * Fragt nach erfolgreicher Diagnose, ob das Foto + der Krankheitshinweis ins
     * Foto-Archiv der ausgewählten Pflanze übernommen werden sollen.
     *
     * Beim Bestätigen wird ein [PlantPhoto] mit `photoType="inspection"` und
     * `diagnosisId = <gespeicherter Diagnose-Eintrag>` eingefügt — der Photo-Viewer
     * blendet dann automatisch die Krankheitsbezeichnung als Badge ein.
     */
    private fun promptToAddToArchive(
        plantId: Int,
        imagePath: String,
        result: com.example.plantcare.data.disease.DiseaseResult,
        diagnosisId: Int
    ) {
        if (isFinishing || isDestroyed) return
        val userEmail = EmailContext.current(this)
        lifecycleScope.launch {
            val plant: Plant? = withContext(Dispatchers.IO) {
                try {
                    com.example.plantcare.data.repository.PlantRepository
                        .getInstance(this@DiseaseDiagnosisActivity)
                        .findByIdBlocking(plantId)
                } catch (t: Throwable) {
                    com.example.plantcare.CrashReporter.log(t)
                    null
                }
            }
            // Use a generic "die Pflanze" fallback when the plant lookup
            // fails — the previous code surfaced
            // "Pflanze auswählen" (which is the picker DIALOG TITLE) as
            // the plant name, telling the user "Diagnose XYZ als
            // Pflanze auswählen archivieren?" instead of a usable label.
            val plantLabel = plant?.nickname?.takeIf { it.isNotBlank() }
                ?: plant?.name
                ?: getString(R.string.disease_archive_prompt_plant_fallback)
            val message = getString(
                R.string.disease_archive_prompt_message,
                result.displayName,
                plantLabel
            )
            AlertDialog.Builder(this@DiseaseDiagnosisActivity)
                .setTitle(R.string.disease_archive_prompt_title)
                .setMessage(message)
                .setPositiveButton(R.string.disease_archive_prompt_yes) { dlg, _ ->
                    dlg.dismiss()
                    saveDiseasePhotoToArchive(plantId, imagePath, userEmail, diagnosisId)
                }
                .setNegativeButton(R.string.disease_archive_prompt_no) { dlg, _ ->
                    dlg.dismiss()
                }
                .setCancelable(true)
                .show()
        }
    }

    /**
     * Schreibt einen [PlantPhoto]-Datensatz, der auf die zuvor gespeicherte
     * `disease_diagnosis`-Zeile verweist.
     */
    private fun saveDiseasePhotoToArchive(
        plantId: Int,
        imagePath: String,
        userEmail: String?,
        diagnosisId: Int
    ) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val photo = PlantPhoto().apply {
                        this.plantId = plantId
                        this.imagePath = imagePath
                        this.dateTaken = today
                        this.isCover = false
                        this.userEmail = userEmail
                        this.isProfile = false
                        this.photoType = "inspection"
                        this.diagnosisId = diagnosisId
                    }
                    val newId = com.example.plantcare.data.repository.PlantPhotoRepository
                        .getInstance(this@DiseaseDiagnosisActivity)
                        .insertBlocking(photo)
                    photo.id = newId.toInt()

                    // Cloud-Upload anstoßen (no-op im Guest-Modus / ohne Auth).
                    // Folgt dem Muster aus MainActivity.insertAndUploadPhoto.
                    try {
                        val localUri = Uri.fromFile(File(imagePath))
                        com.example.plantcare.FirebaseSyncManager.get()
                            .uploadPlantPhotoWithPending(
                                applicationContext, photo, localUri
                            )
                    } catch (uploadEx: Throwable) {
                        // Upload-Fehler dürfen den lokalen Insert nicht zurückrollen.
                        com.example.plantcare.CrashReporter.log(uploadEx)
                    }
                    true
                } catch (t: Throwable) {
                    com.example.plantcare.CrashReporter.log(t)
                    false
                }
            }
            val msgRes = if (ok) R.string.disease_archive_saved
                         else R.string.disease_archive_save_failed
            Toast.makeText(this@DiseaseDiagnosisActivity, msgRes, Toast.LENGTH_SHORT).show()
            if (ok) {
                // Damit z. B. das offene PlantDetail-Dialog die neue Archive-Zeile sieht.
                com.example.plantcare.DataChangeNotifier.notifyChange()
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
        } catch (e: Throwable) {
            CrashReporter.log(e)
            Toast.makeText(this, R.string.camera_file_create_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGalleryResult(uri: Uri) {
        // Same pipeline as the camera path — applies EXIF rotation and
        // downscales to 2048 px before sending to Gemini. Without this,
        // a 12 MB portrait photo would be base64-encoded into a 16 MB
        // request body AND analysed sideways (Gemini scores on raw
        // pixels, not EXIF). See `runPrepareAndApply`.
        runPrepareAndApply(uri, rawCaptureToDelete = null)
    }

    /**
     * Shared pre-upload pipeline used by both the camera and the gallery
     * flow. Shows the progress bar, runs [prepareImageForDiagnosis] on IO,
     * then on Main either commits the prepared file to the ViewModel + UI
     * preview OR restores the placeholder + toasts.
     *
     * On success, optionally deletes the raw capture file (camera path
     * passes the captured `File`; gallery path leaves it null because we
     * don't own the picked URI).
     *
     * Restoring the placeholder on failure is important: setting
     * `imagePreview` visible and `placeholderContainer` gone BEFORE the
     * prepare started would leave the user staring at a blank ImageView
     * with only a toast for diagnosis.
     */
    private fun runPrepareAndApply(source: Uri, rawCaptureToDelete: File? = null) {
        placeholderContainer.visibility = View.GONE
        imagePreview.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val prepared = prepareImageForDiagnosis(source)
            progressBar.visibility = View.GONE
            if (prepared == null) {
                imagePreview.visibility = View.GONE
                placeholderContainer.visibility = View.VISIBLE
                Toast.makeText(
                    this@DiseaseDiagnosisActivity,
                    R.string.camera_file_create_error,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            if (rawCaptureToDelete != null
                    && rawCaptureToDelete.absolutePath != prepared.absolutePath) {
                runCatching { rawCaptureToDelete.delete() }
            }
            // A new image starts a fresh diagnosis cycle — re-enable the
            // "Behandlungsplan" button regardless of any plan that was
            // created for the previous diagnosis. The pre-fix logic kept
            // `treatmentPlanCreated = true` across image swaps, so a user
            // who diagnosed plant A, made a plan, then diagnosed plant B
            // could never make a plan for B because the button stayed
            // hidden by the stale flag.
            treatmentPlanCreated = false
            showImagePreview(prepared.absolutePath)
            viewModel.setImagePath(prepared.absolutePath)
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

    /**
     * Captures (and prepared images) live under
     * `getExternalFilesDir(Pictures)/disease/` so they don't pollute the
     * top-level Pictures dir alongside cover/archive/identify photos.
     * Mirrors the `identify/` subdir convention introduced in F8.3.
     */
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val baseDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val diseaseDir = File(baseDir, "disease").apply { mkdirs() }
        return File.createTempFile("DISEASE_${timeStamp}_", ".jpg", diseaseDir)
    }

    /**
     * Read source URI, apply EXIF orientation, downscale to a 2048-px long
     * edge, recompress as JPEG 85, and write to a fresh `DISEASE_*.jpg`.
     * Mirrors `PlantIdentifyActivity.prepareImageForIdentify` — same five-
     * phase decode pipeline, same correctness reasoning:
     *
     *   - **EXIF rotation**: Gemini scores recognition on the bitmap pixel
     *     data, not the EXIF tag. A portrait shot uploaded sideways would
     *     match symptoms against the wrong leaf orientation.
     *   - **Downscale**: A 12 MB camera frame becomes a 16 MB base64
     *     request body to Gemini. Free-tier quota is 250/day; large
     *     uploads also hit the per-request size limit and slow analysis
     *     by ~10s on weak networks. 2048 px keeps detail without bloat.
     *   - **JPEG 85**: ~600 KB output, ~800 KB base64, fits the API
     *     comfortably and decodes server-side fast.
     *
     * Returns null on failure; caller falls back to surfacing the
     * generic camera-file-create-error toast.
     */
    private suspend fun prepareImageForDiagnosis(source: Uri): File? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cr = contentResolver

                // Phase 1: bounded decode — read JPEG header only.
                val bounds = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                cr.openInputStream(source)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, bounds)
                }
                val maxEdge = 2048
                val srcLong = maxOf(bounds.outWidth, bounds.outHeight)
                if (srcLong <= 0) return@withContext null

                var sample = 1
                while (srcLong / (sample * 2) >= maxEdge) sample *= 2

                // Phase 2: real decode at the chosen sample size.
                val decode = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                var bmp = cr.openInputStream(source)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, decode)
                } ?: return@withContext null

                // Phase 3: bake EXIF rotation into the pixel data.
                val orientation = readExifOrientation(cr, source)
                if (orientation != androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                        && orientation != androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED) {
                    val matrix = android.graphics.Matrix()
                    when (orientation) {
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                            matrix.postRotate(90f); matrix.postScale(-1f, 1f)
                        }
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                            matrix.postRotate(270f); matrix.postScale(-1f, 1f)
                        }
                    }
                    val rotated = android.graphics.Bitmap.createBitmap(
                        bmp, 0, 0, bmp.width, bmp.height, matrix, true
                    )
                    if (rotated !== bmp) runCatching { bmp.recycle() }
                    bmp = rotated
                }

                // Phase 4: final scale to honour maxEdge exactly.
                val longEdge = maxOf(bmp.width, bmp.height)
                val finalBmp = if (longEdge > maxEdge) {
                    val ratio = maxEdge.toFloat() / longEdge
                    val w = (bmp.width * ratio).toInt()
                    val h = (bmp.height * ratio).toInt()
                    val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
                    if (scaled !== bmp) runCatching { bmp.recycle() }
                    scaled
                } else bmp

                // Phase 5: write to fresh DISEASE_*.jpg under disease/ subdir.
                // try/finally so a thrown IOException doesn't leak the
                // working bitmap (8 MB+ at the working resolution).
                try {
                    val outFile = createImageFile()
                    java.io.FileOutputStream(outFile).use { out ->
                        finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    outFile
                } finally {
                    runCatching { finalBmp.recycle() }
                }
            } catch (t: Throwable) {
                CrashReporter.log(t)
                null
            }
        }

    /**
     * Defensive EXIF reader — opens its own stream, returns ORIENTATION_NORMAL
     * on any failure so the caller skips rotation rather than crashing.
     */
    private fun readExifOrientation(
        cr: android.content.ContentResolver,
        source: Uri
    ): Int {
        return try {
            cr.openInputStream(source)?.use { stream ->
                androidx.exifinterface.media.ExifInterface(stream).getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )
            } ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        } catch (_: Throwable) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        }
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
                    val plantRepo = com.example.plantcare.data.repository.PlantRepository
                        .getInstance(this@DiseaseDiagnosisActivity)
                    if (userEmail.isNullOrBlank()) {
                        plantRepo.getAllUserPlantsBlocking()
                    } else {
                        plantRepo.getAllUserPlantsForUserBlocking(userEmail)
                    }
                } catch (t: Throwable) {
                    CrashReporter.log(t)
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
                    dlg.dismiss()
                    confirmAndSave(chosen.id, userEmail)
                }
                .setNeutralButton(R.string.disease_pick_plant_none) { dlg, _ ->
                    dlg.dismiss()
                    viewModel.saveTopResult(userEmail, plantId = 0)
                }
                // Cancel re-enables Save so the user can pick again instead
                // of being stuck on a permanently-disabled button.
                .setNegativeButton(R.string.disease_pick_plant_cancel) { d, _ ->
                    d.dismiss()
                    btnSave.isEnabled = true
                }
                .setOnCancelListener { btnSave.isEnabled = true }
                .show()
        }
    }

    /**
     * Map the cloud-API failure type onto a localized message resource.
     * Mirrors `PlantIdentifyActivity.errorStringFor`.
     */
    private fun errorStringFor(type: PlantNetError): Int = when (type) {
        PlantNetError.INVALID_API_KEY -> R.string.disease_error_invalid_key
        PlantNetError.QUOTA_EXCEEDED  -> R.string.disease_error_quota_exceeded
        PlantNetError.NO_INTERNET     -> R.string.disease_error_no_internet
        PlantNetError.TIMEOUT         -> R.string.disease_error_timeout
        PlantNetError.SERVER_ERROR    -> R.string.disease_error_server
        PlantNetError.UNKNOWN         -> R.string.disease_error_unknown
    }
}

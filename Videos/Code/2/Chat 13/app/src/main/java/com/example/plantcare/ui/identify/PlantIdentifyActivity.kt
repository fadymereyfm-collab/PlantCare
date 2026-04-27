package com.example.plantcare.ui.identify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.plantcare.AddToMyPlantsDialogFragment
import com.example.plantcare.Analytics
import com.example.plantcare.DataChangeNotifier
import com.example.plantcare.Plant
import com.example.plantcare.R
import com.example.plantcare.data.plantnet.PlantNetError
import com.example.plantcare.data.plantnet.IdentificationResult
import com.example.plantcare.data.plantnet.PlantCareDefaults
import com.example.plantcare.data.plantnet.PlantCatalogLookup
import com.example.plantcare.data.plantnet.PlantEnrichmentService
import com.example.plantcare.ui.viewmodel.IdentifyUiState
import com.example.plantcare.ui.viewmodel.PlantIdentifyViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity for identifying plants using the PlantNet API.
 *
 * Flow:
 * 1. User takes a photo or picks from gallery
 * 2. Image preview is shown
 * 3. User selects the plant organ (optional, defaults to "auto")
 * 4. User taps "Pflanze erkennen" to identify
 * 5. Results are displayed in a list
 * 6. User can add any result to their plants collection
 */
class PlantIdentifyActivity : AppCompatActivity() {

    companion object {
        /** Intent extra — Room id to which an added plant should be assigned (0 = nicht zugeordnet). */
        const val EXTRA_ROOM_ID = "com.example.plantcare.extra.ROOM_ID"
        /** Tag for the AddToMyPlantsDialogFragment shown from identify flow. */
        private const val DIALOG_TAG = "identify_add_to_my_plants"
        /** Tag for the split-screen comparison dialog. */
        private const val COMPARE_TAG = PlantCompareDialogFragment.TAG
    }

    private lateinit var viewModel: PlantIdentifyViewModel

    /** Room id passed via Intent; defaults to 0 when caller didn't supply one. */
    private var targetRoomId: Int = 0

    /** Guard against double-tap on "Hinzufügen" while an insert is in flight. */
    private var addInProgress: Boolean = false

    // Views
    private lateinit var imagePreview: ImageView
    private lateinit var placeholderContainer: LinearLayout
    private lateinit var btnCamera: MaterialButton
    private lateinit var btnGallery: MaterialButton
    private lateinit var btnIdentify: MaterialButton
    private lateinit var btnNoneCorrect: MaterialButton
    private lateinit var organChipGroup: ChipGroup
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

    // Adapter
    private lateinit var adapter: IdentificationResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_identify)

        // Read the target room id passed by the caller (0 means unassigned).
        targetRoomId = intent?.getIntExtra(EXTRA_ROOM_ID, 0) ?: 0

        viewModel = ViewModelProvider(this)[PlantIdentifyViewModel::class.java]

        initViews()
        setupLaunchers()
        setupListeners()
        observeViewModel()

        // Wenn der AddToMyPlants-Dialog ohne Hinzufügen geschlossen wird, soll der Nutzer
        // erneut tippen können. Reset addInProgress sobald der Dialog destroyed wurde.
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentDestroyed(
                    fm: androidx.fragment.app.FragmentManager,
                    f: androidx.fragment.app.Fragment
                ) {
                    if (f.tag == DIALOG_TAG) {
                        addInProgress = false
                    }
                }
            },
            /* recursive = */ false
        )
    }

    private fun initViews() {
        imagePreview = findViewById(R.id.imagePreview)
        placeholderContainer = findViewById(R.id.placeholderContainer)
        btnCamera = findViewById(R.id.btnCamera)
        btnGallery = findViewById(R.id.btnGallery)
        btnIdentify = findViewById(R.id.btnIdentify)
        organChipGroup = findViewById(R.id.organChipGroup)
        progressBar = findViewById(R.id.progressBar)
        txtMessage = findViewById(R.id.txtMessage)
        resultsContainer = findViewById(R.id.resultsContainer)
        resultsRecyclerView = findViewById(R.id.resultsRecyclerView)
        btnNoneCorrect = findViewById(R.id.btnNoneCorrect)

        // Back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        adapter = IdentificationResultAdapter(
            onAddClick = { result, rank ->
                // حماية من الضغط المكرر: لا تفتح حواراً ثانياً لو الأول مفتوح فعلاً
                if (supportFragmentManager.findFragmentByTag(DIALOG_TAG) != null) return@IdentificationResultAdapter
                if (addInProgress) return@IdentificationResultAdapter
                addInProgress = true
                Analytics.logPlantIdentified(this, rank, result.confidencePercent)
                enrichAndOpenDialog(result)
            },
            onItemClick = { result, rank ->
                // Guard: don't open compare if add dialog is already open
                if (supportFragmentManager.findFragmentByTag(DIALOG_TAG) != null) return@IdentificationResultAdapter
                if (supportFragmentManager.findFragmentByTag(COMPARE_TAG) != null) return@IdentificationResultAdapter

                val capturedPath = viewModel.selectedImagePath.value ?: ""
                val plantName    = result.commonName ?: result.scientificName
                // Prefer the larger image URL for the half-screen display
                val imageUrl     = result.largeImageUrl ?: result.imageUrl

                val dlg = PlantCompareDialogFragment.newInstance(
                    candidateImageUrl = imageUrl,
                    capturedImagePath = capturedPath,
                    plantName         = plantName
                )
                dlg.setOnConfirm {
                    // User confirmed the match → proceed to enrich + add flow
                    if (!addInProgress) {
                        addInProgress = true
                        Analytics.logPlantIdentified(this, rank, result.confidencePercent)
                        enrichAndOpenDialog(result)
                    }
                }
                dlg.show(supportFragmentManager, COMPARE_TAG)
            }
        )
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = adapter
    }

    private fun setupLaunchers() {
        // Camera launcher using TakePicture contract
        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && photoFile != null) {
                showImagePreview(photoFile!!.absolutePath)
                viewModel.setImagePath(photoFile!!.absolutePath)
            }
        }

        // Gallery launcher
        galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleGalleryResult(it) }
        }

        // Camera permission
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

        btnIdentify.setOnClickListener {
            val imagePath = viewModel.selectedImagePath.value ?: return@setOnClickListener
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Toast.makeText(this, R.string.identify_image_missing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val organ = getSelectedOrgan()
            viewModel.identifyPlant(imageFile, organ)
        }

        btnNoneCorrect.setOnClickListener {
            Analytics.logPlantIdentified(this, 0, 0)
            viewModel.reset()
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is IdentifyUiState.Idle -> {
                    progressBar.visibility = View.GONE
                    txtMessage.visibility = View.GONE
                    resultsContainer.visibility = View.GONE
                }
                is IdentifyUiState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    txtMessage.visibility = View.GONE
                    resultsContainer.visibility = View.GONE
                    btnIdentify.isEnabled = false
                }
                is IdentifyUiState.Success -> {
                    progressBar.visibility = View.GONE
                    txtMessage.visibility = View.GONE
                    resultsContainer.visibility = View.VISIBLE
                    btnIdentify.isEnabled = true
                }
                is IdentifyUiState.NoResults -> {
                    progressBar.visibility = View.GONE
                    txtMessage.text = getString(R.string.identify_no_results)
                    txtMessage.visibility = View.VISIBLE
                    resultsContainer.visibility = View.GONE
                    btnIdentify.isEnabled = true
                }
                is IdentifyUiState.Error -> {
                    progressBar.visibility = View.GONE
                    txtMessage.text = getString(errorStringFor(state.type))
                    txtMessage.visibility = View.VISIBLE
                    resultsContainer.visibility = View.GONE
                    btnIdentify.isEnabled = true
                }
            }
        }

        viewModel.results.observe(this) { results ->
            adapter.submitList(results)
        }

        viewModel.selectedImagePath.observe(this) { path ->
            btnIdentify.isEnabled = !path.isNullOrEmpty()
        }

        viewModel.plantAdded.observe(this) { added ->
            if (added == true) {
                Toast.makeText(this, R.string.identify_plant_added, Toast.LENGTH_SHORT).show()
                // Notify other fragments about data change
                DataChangeNotifier.notifyChange()
                // إغلاق الشاشة بعد الإضافة مباشرة حتى لا يُكرَّر الإدراج
                finish()
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
            // Copy the image to a local file for upload
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
        return File.createTempFile("IDENTIFY_${timeStamp}_", ".jpg", storageDir)
    }

    private fun getSelectedOrgan(): String {
        return when (organChipGroup.checkedChipId) {
            R.id.chipLeaf -> "leaf"
            R.id.chipFlower -> "flower"
            R.id.chipFruit -> "fruit"
            R.id.chipBark -> "bark"
            else -> "auto"
        }
    }

    /**
     * Holt Bild + Beschreibung aus Wikipedia, sucht zusätzlich im lokalen 506‑er Katalog
     * nach den vier Pflege‑Feldern (Licht/Boden/Düngung/Bewässerung) und öffnet dann den
     * Hinzufüge‑Dialog.
     *
     * Vorher: der Dialog öffnete sich mit vier leeren Pflege‑Feldern. Die UI zeigte „—"
     * neben „Bewässerung / Licht / Boden / Düngung" — der Nutzer musste selbst Text
     * eintippen, obwohl der Katalog für ~500 Pflanzen passende Texte kennt.
     *
     * Jetzt:
     * 1) Katalog‑Lookup (Raum‑DB, isUserPlant=0) über commonName, scientificName‑Rückwärtsmapping
     *    oder LIKE‑Muster. Trifft zu → wir füllen die 4 Pflege‑Felder mit den geprüften Texten
     *    aus `plants.csv`.
     * 2) Wikipedia‑Anreicherung für Bild + kurze Beschreibung. Das kommt als
     *    `personalNote` darunter (separate Zeile „Wissenschaftlicher Name / Familie"
     *    + ein Absatz Fließtext) — die Stelle, die das Detail‑Layout bereits als
     *    eigenen Abschnitt unter den Pflege‑Feldern anzeigt.
     *
     * Fehlertoleranz: jede Quelle ist optional. Wenn der Katalog nichts findet, bleiben die
     * Felder leer (wie vorher). Wenn Wikipedia ausfällt, gibt es nur die Pflege‑Texte.
     */
    private fun enrichAndOpenDialog(result: IdentificationResult) {
        progressBar.visibility = View.VISIBLE
        txtMessage.text = getString(R.string.identify_enriching)
        txtMessage.visibility = View.VISIBLE

        lifecycleScope.launch {
            // Wikipedia & Katalog parallel — spart dem Nutzer ~1 Sekunde Wartezeit,
            // weil beide Operationen auf IO laufen und unabhängig sind.
            val enrichmentDeferred = async {
                try {
                    PlantEnrichmentService.enrich(
                        scientificName = result.scientificName,
                        commonName = result.commonName
                    )
                } catch (t: Throwable) { null }
            }
            val careDeferred = async {
                try {
                    PlantCatalogLookup.findByIdentification(
                        context = applicationContext,
                        scientificName = result.scientificName,
                        commonName = result.commonName
                    )
                } catch (t: Throwable) { null }
            }

            val enrichment = enrichmentDeferred.await()
            val care = careDeferred.await()

            progressBar.visibility = View.GONE
            txtMessage.visibility = View.GONE

            // Bild: Wikipedia‑Thumbnail bevorzugt, sonst lokales Kamerafoto.
            val wikiImage = enrichment?.imageUrl
            val cameraPath = viewModel.selectedImagePath.value
            val finalImage = wikiImage?.takeIf { it.isNotBlank() } ?: cameraPath

            // PersonalNote: nur allgemeine Infos, keine Pflege‑Angaben (die sind jetzt
            // in ihren eigenen Feldern). Aufbau: wiss. Name + Familie + Wikipedia‑Fließtext.
            val notesBuilder = StringBuilder()
            notesBuilder.append("Wissenschaftlicher Name: ${result.scientificName}")
            result.family?.let { notesBuilder.append("\nFamilie: $it") }
            enrichment?.summary?.takeIf { it.isNotBlank() }?.let {
                notesBuilder.append("\n\n").append(it)
            }

            // Zweiter Fallback: wenn der Katalog‑Lookup keine passende Zeile hat
            // (z. B. bei Wildpflanzen wie *Polygonatum multiflorum*), holen wir
            // familienbasierte Standard‑Texte aus PlantCareDefaults. Damit bleibt
            // kein einziges der vier Pflege‑Felder leer — der Nutzer sieht statt
            // „—" immer einen sinnvollen Startwert und kann ihn bei Bedarf anpassen.
            val defaults = PlantCareDefaults.forFamily(result.family)

            val draft = Plant().apply {
                name = result.commonName ?: result.scientificName
                // Priorität: Katalog (geprüft) → Familien‑Default (allgemein).
                lighting    = care?.lighting    ?: defaults.lighting
                soil        = care?.soil        ?: defaults.soil
                fertilizing = care?.fertilizing ?: defaults.fertilizing
                watering    = care?.watering    ?: defaults.watering
                imageUri    = finalImage
                personalNote = notesBuilder.toString()
                // NB: isUserPlant سيُعيَّن = true داخل AddToMyPlantsDialogFragment
            }

            val dlg = AddToMyPlantsDialogFragment.newInstance(draft)
            dlg.setOnPlantAdded {
                // تمت الإضافة → أغلق شاشة التعرف
                finish()
            }
            dlg.show(supportFragmentManager, DIALOG_TAG)
        }
    }

    /**
     * Übersetzt den Fehlertyp in eine menschliche Meldung aus strings.xml.
     * Erweitert die bisherige „Fehler: %s"‑Nachricht um echte Hinweise für den Nutzer.
     */
    private fun errorStringFor(type: PlantNetError): Int = when (type) {
        PlantNetError.INVALID_API_KEY  -> R.string.identify_error_invalid_key
        PlantNetError.QUOTA_EXCEEDED   -> R.string.identify_error_quota_exceeded
        PlantNetError.NO_INTERNET      -> R.string.identify_error_no_internet
        PlantNetError.TIMEOUT          -> R.string.identify_error_timeout
        PlantNetError.SERVER_ERROR     -> R.string.identify_error_server
        PlantNetError.UNKNOWN          -> R.string.identify_error_unknown
    }
}

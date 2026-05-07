package com.example.plantcare.ui.identify

import android.Manifest
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
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
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
        /** Tag for the AddToMyPlantsDialogFragment shown from identify flow. */
        private const val DIALOG_TAG = "identify_add_to_my_plants"
        /** Tag for the split-screen comparison dialog. */
        private const val COMPARE_TAG = PlantCompareDialogFragment.TAG
    }

    private lateinit var viewModel: PlantIdentifyViewModel

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
            val captured = photoFile
            if (success && captured != null) {
                // Snapshot+clear the raw capture pointer immediately so a
                // rapid second capture (which would overwrite `photoFile`)
                // can't get its result aliased back to the previous BG
                // launch. The closure already captured `captured` as a
                // local val so the BG work is unaffected.
                photoFile = null
                runPrepareAndApply(Uri.fromFile(captured), rawCaptureToDelete = captured)
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

    /**
     * Shared prepare-and-apply pipeline used by both the camera and the
     * gallery flow. Shows the progress bar, runs [prepareImageForIdentify]
     * on IO, then on Main either commits the prepared file to the
     * ViewModel + previews it OR restores the placeholder + toasts.
     *
     * On success, optionally deletes the raw capture file (camera path
     * passes the captured `File`; gallery path leaves it null because we
     * don't own the picked URI).
     *
     * Restoring the placeholder on failure is important: the previous
     * implementation set `imagePreview` visible and `placeholderContainer`
     * gone BEFORE the prepare started, so a failure left the user
     * staring at a blank ImageView with only a toast for diagnosis.
     */
    private fun runPrepareAndApply(source: Uri, rawCaptureToDelete: File? = null) {
        placeholderContainer.visibility = View.GONE
        imagePreview.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val prepared = prepareImageForIdentify(source)
            progressBar.visibility = View.GONE
            if (prepared == null) {
                // Restore the placeholder so the user sees the upload
                // affordance again instead of a blank rectangle.
                imagePreview.visibility = View.GONE
                placeholderContainer.visibility = View.VISIBLE
                Toast.makeText(
                    this@PlantIdentifyActivity,
                    R.string.camera_file_create_error,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            // Tidy up the raw camera capture — we now have the resized
            // version; the original 12 MB file would just sit on disk
            // until the daily prune ran.
            if (rawCaptureToDelete != null
                    && rawCaptureToDelete.absolutePath != prepared.absolutePath) {
                runCatching { rawCaptureToDelete.delete() }
            }
            showImagePreview(prepared.absolutePath)
            viewModel.setImagePath(prepared.absolutePath)
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
            com.example.plantcare.CrashReporter.log(e)
            Toast.makeText(this, R.string.camera_file_create_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGalleryResult(uri: Uri) {
        // Off-load to lifecycleScope IO so the bitmap decode + recompress
        // doesn't block the gallery picker callback's main-thread dispatch.
        // Failure path restores the placeholder — see runPrepareAndApply.
        runPrepareAndApply(uri, rawCaptureToDelete = null)
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
     * `getExternalFilesDir(Pictures)/identify/` so they don't pollute the
     * top-level Pictures dir alongside cover/archive photos. Old files are
     * cleaned by [pruneOldIdentifyFiles] on each new capture (older than
     * 7 days drop out — that's the same TTL as the PlantNet cache).
     */
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val baseDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val identifyDir = File(baseDir, "identify").apply { mkdirs() }
        pruneOldIdentifyFiles(identifyDir)
        return File.createTempFile("IDENTIFY_${timeStamp}_", ".jpg", identifyDir)
    }

    /**
     * Best-effort cleanup of identify captures older than 7 days. PlantNet
     * cache TTL is also 7 days — keeping local files past that point just
     * accumulates storage with no recall value. Pure file system work, no
     * recursion, swallows all errors so a corrupted file doesn't block
     * future captures.
     *
     * Also walks the Pictures/ root once to delete legacy `IDENTIFY_*.jpg`
     * captures from before this subdir was introduced. Old installs would
     * otherwise carry those forever.
     */
    private fun pruneOldIdentifyFiles(dir: File) {
        try {
            val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < cutoff) {
                    runCatching { f.delete() }
                }
            }
            // Legacy: pre-subdir captures lived directly in Pictures/.
            // Match the same `IDENTIFY_*.jpg` prefix our older builds used,
            // and only touch files (not directories) so we never wipe the
            // new identify/ subdir or anything from PhotoCaptureCoordinator.
            dir.parentFile?.listFiles()?.forEach { f ->
                if (f.isFile
                        && f.name.startsWith("IDENTIFY_")
                        && f.name.endsWith(".jpg")) {
                    runCatching { f.delete() }
                }
            }
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
        }
    }

    /**
     * Read source URI, apply EXIF orientation, downscale to a 2048-px long
     * edge, recompress as JPEG 85, and write to a fresh `IDENTIFY_*.jpg`.
     * Runs entirely on Dispatchers.IO via the suspend wrapper.
     *
     * Why bother:
     *   - **EXIF**: Camera captures store orientation in EXIF; raw bitmap
     *     pixels remain landscape. The PlantNet API doesn't honour client-
     *     side EXIF on multipart uploads — it scores recognition on the
     *     pixel data. A portrait shot uploaded sideways would simply hit
     *     a different match because the leaf orientation was wrong.
     *   - **Downscale**: A 12 MP camera frame is a 4-8 MB upload. PlantNet
     *     internally downsamples for analysis; sending the original wastes
     *     bandwidth, slows the request by seconds on weak connections, and
     *     burns the user's free 500-req/day quota faster than necessary.
     *     2048 px is the long-edge sweet spot — large enough that PlantNet
     *     doesn't lose detail, small enough that JPEG 85 lands ~600 KB.
     *
     * Returns null on any failure; caller falls back to surfacing the
     * generic camera-file-create-error toast.
     */
    private suspend fun prepareImageForIdentify(source: Uri): File? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cr = contentResolver

                // Phase 1: bounded decode — read JPEG header only, so a 50 MP
                // photo doesn't OOM us before we even start scaling.
                val bounds = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                cr.openInputStream(source)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, bounds)
                }
                val maxEdge = 2048
                val srcLong = maxOf(bounds.outWidth, bounds.outHeight)
                if (srcLong <= 0) return@withContext null

                // Pick largest sample that still leaves us above maxEdge so
                // the final scale step lands cleanly at maxEdge (mirrors
                // the algorithm in PhotoCaptureCoordinator.downscaleAndPersist).
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

                // Phase 3: bake EXIF rotation into the pixel data so PlantNet
                // analyses the photo right-way-up.
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

                // Phase 5: write to fresh IDENTIFY_*.jpg under our subdir.
                // Wrap in try/finally so a thrown IOException from
                // createImageFile() or compress() doesn't leak the working
                // bitmap (8 MB+ at the working resolution). The outer
                // try/catch returns null to the caller anyway.
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
                com.example.plantcare.CrashReporter.log(t)
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
                // Bewässerungs-Intervall (Tage) explizit setzen — sonst greift in
                // AddToMyPlantsDialogFragment der Hardcoded-Fallback von 5 Tagen,
                // weil der watering-Text der Familien-Defaults keine Zahl enthält
                // (Functional Report §1.4).
                wateringInterval = (care?.wateringIntervalDays ?: 0).takeIf { it > 0 }
                    ?: defaults.wateringIntervalDays
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

package com.example.plantcare.ui.identify

import android.Manifest
import android.content.Context
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
 */
class PlantIdentifyActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.example.plantcare.format.FontScaleHelper.wrap(newBase))
    }

    companion object {
        private const val DIALOG_TAG = "identify_add_to_my_plants"
        private const val COMPARE_TAG = PlantCompareDialogFragment.TAG
    }

    private lateinit var viewModel: PlantIdentifyViewModel
    private var addInProgress: Boolean = false

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

    private var photoFile: File? = null
    private var photoUri: Uri? = null

    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var galleryLauncher: ActivityResultLauncher<String>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>

    private lateinit var adapter: IdentificationResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_identify)

        viewModel = ViewModelProvider(this)[PlantIdentifyViewModel::class.java]

        initViews()
        setupLaunchers()
        setupListeners()
        observeViewModel()

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
            false
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

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        adapter = IdentificationResultAdapter(
            onAddClick = { result, rank ->
                if (supportFragmentManager.findFragmentByTag(DIALOG_TAG) != null) return@IdentificationResultAdapter
                if (addInProgress) return@IdentificationResultAdapter
                addInProgress = true
                Analytics.logPlantIdentified(this, rank, result.confidencePercent)
                enrichAndOpenDialog(result)
            },
            onItemClick = { result, rank ->
                if (supportFragmentManager.findFragmentByTag(DIALOG_TAG) != null) return@IdentificationResultAdapter
                if (supportFragmentManager.findFragmentByTag(COMPARE_TAG) != null) return@IdentificationResultAdapter

                val capturedPath = viewModel.selectedImagePath.value ?: ""
                val plantName    = result.commonName ?: result.scientificName
                val imageUrl     = result.largeImageUrl ?: result.imageUrl

                val dlg = PlantCompareDialogFragment.newInstance(
                    candidateImageUrl = imageUrl,
                    capturedImagePath = capturedPath,
                    plantName         = plantName,
                    scientificName    = result.scientificName
                )
                dlg.setOnConfirm {
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
        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val captured = photoFile
            if (success && captured != null) {
                photoFile = null
                runPrepareAndApply(Uri.fromFile(captured), rawCaptureToDelete = captured)
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

    private fun runPrepareAndApply(source: Uri, rawCaptureToDelete: File? = null) {
        placeholderContainer.visibility = View.GONE
        imagePreview.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val prepared = prepareImageForIdentify(source)
            progressBar.visibility = View.GONE
            if (prepared == null) {
                imagePreview.visibility = View.GONE
                placeholderContainer.visibility = View.VISIBLE
                Toast.makeText(
                    this@PlantIdentifyActivity,
                    R.string.camera_file_create_error,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
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
            // v17: resolve catalog matches in parallel for "In unserem Katalog" badge
            if (results.isNullOrEmpty()) {
                adapter.setCatalogMatches(emptyMap())
            } else {
                lifecycleScope.launch {
                    val matches = mutableMapOf<String, com.example.plantcare.Plant?>()
                    val deferreds = results.map { r ->
                        async {
                            val match = try {
                                PlantCatalogLookup.findMatch(
                                    context = applicationContext,
                                    scientificName = r.scientificName,
                                    commonName = r.commonName
                                )
                            } catch (t: Throwable) {
                                com.example.plantcare.CrashReporter.log(t)
                                null
                            }
                            r.scientificName to match?.plant
                        }
                    }
                    for (d in deferreds) {
                        val (key, plant) = d.await()
                        if (plant != null) matches[key] = plant
                    }
                    adapter.setCatalogMatches(matches)
                }
            }
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
                "${'$'}{applicationContext.packageName}.provider",
                photoFile!!
            )
            cameraLauncher.launch(photoUri!!)
        } catch (e: Throwable) {
            com.example.plantcare.CrashReporter.log(e)
            Toast.makeText(this, R.string.camera_file_create_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGalleryResult(uri: Uri) {
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

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val identifyDir = File(baseDir, "identify").apply { mkdirs() }
        pruneOldIdentifyFiles(identifyDir)
        return File.createTempFile("IDENTIFY_${'$'}{timeStamp}_", ".jpg", identifyDir)
    }

    private fun pruneOldIdentifyFiles(dir: File) {
        try {
            val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < cutoff) {
                    runCatching { f.delete() }
                }
            }
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

    private suspend fun prepareImageForIdentify(source: Uri): File? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cr = contentResolver

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

                val decode = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                var bmp = cr.openInputStream(source)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, decode)
                } ?: return@withContext null

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

                val longEdge = maxOf(bmp.width, bmp.height)
                val finalBmp = if (longEdge > maxEdge) {
                    val ratio = maxEdge.toFloat() / longEdge
                    val w = (bmp.width * ratio).toInt()
                    val h = (bmp.height * ratio).toInt()
                    val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
                    if (scaled !== bmp) runCatching { bmp.recycle() }
                    scaled
                } else bmp

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

    private fun enrichAndOpenDialog(result: IdentificationResult) {
        progressBar.visibility = View.VISIBLE
        txtMessage.text = getString(R.string.identify_enriching)
        txtMessage.visibility = View.VISIBLE

        lifecycleScope.launch {
            val enrichmentDeferred = async {
                try {
                    PlantEnrichmentService.enrich(
                        scientificName = result.scientificName,
                        commonName = result.commonName
                    )
                } catch (t: Throwable) { null }
            }
            // v17: prefer cached catalog match the adapter already resolved
            val cachedMatch = adapter.catalogMatchFor(result.scientificName)
            val matchDeferred = async {
                if (cachedMatch != null) {
                    PlantCatalogLookup.CatalogMatch(
                        plant = cachedMatch,
                        matchedBy = PlantCatalogLookup.MatchSource.SCIENTIFIC_EXACT
                    )
                } else {
                    try {
                        PlantCatalogLookup.findMatch(
                            context = applicationContext,
                            scientificName = result.scientificName,
                            commonName = result.commonName
                        )
                    } catch (t: Throwable) { null }
                }
            }

            val enrichment = enrichmentDeferred.await()
            val matchInfo = matchDeferred.await()
            val matchedPlant = matchInfo?.plant

            progressBar.visibility = View.GONE
            txtMessage.visibility = View.GONE

            val wikiImage = enrichment?.imageUrl
            val cameraPath = viewModel.selectedImagePath.value
            val finalImage = wikiImage?.takeIf { it.isNotBlank() } ?: cameraPath

            val effectiveFamily = matchedPlant?.family?.takeIf { it.isNotBlank() }
                ?: result.family
            val defaults = PlantCareDefaults.forFamily(effectiveFamily)

            val notesBuilder = StringBuilder()
            notesBuilder.append("Wissenschaftlicher Name: ${'$'}{result.scientificName}")
            (effectiveFamily ?: result.family)?.let { notesBuilder.append("\nFamilie: ${'$'}it") }
            if (matchedPlant != null) {
                notesBuilder.append("\nQuelle: ").append(getString(R.string.identify_catalog_match_hint))
            } else {
                notesBuilder.append("\nQuelle: ").append(getString(R.string.identify_no_catalog_match_hint))
            }
            enrichment?.summary?.takeIf { it.isNotBlank() }?.let {
                notesBuilder.append("\n\n").append(it)
            }

            val draft = Plant().apply {
                name = matchedPlant?.name?.takeIf { it.isNotBlank() }
                    ?: result.commonName
                    ?: result.scientificName
                scientificName = matchedPlant?.scientificName?.takeIf { it.isNotBlank() }
                    ?: result.scientificName
                family = effectiveFamily
                category = matchedPlant?.category
                lighting    = matchedPlant?.lighting?.takeIf { it.isNotBlank() } ?: defaults.lighting
                soil        = matchedPlant?.soil?.takeIf { it.isNotBlank() } ?: defaults.soil
                fertilizing = matchedPlant?.fertilizing?.takeIf { it.isNotBlank() } ?: defaults.fertilizing
                watering    = matchedPlant?.watering?.takeIf { it.isNotBlank() } ?: defaults.watering
                wateringInterval = run {
                    val explicit = matchedPlant?.wateringInterval ?: 0
                    if (explicit > 0) return@run explicit
                    val parsed = matchedPlant?.watering?.let {
                        com.example.plantcare.ReminderUtils.parseWateringInterval(it)
                    } ?: 0
                    if (parsed > 0) return@run parsed
                    defaults.wateringIntervalDays
                }
                fertilizingInterval = defaults.fertilizingIntervalDays
                mistingInterval = defaults.mistingIntervalDays
                repottingIntervalDays = defaults.repottingIntervalDays
                imageUri    = finalImage
                personalNote = notesBuilder.toString()
            }

            val dlg = AddToMyPlantsDialogFragment.newInstance(draft)
            dlg.setOnPlantAdded {
                finish()
            }
            dlg.show(supportFragmentManager, DIALOG_TAG)
        }
    }

    private fun errorStringFor(type: PlantNetError): Int = when (type) {
        PlantNetError.INVALID_API_KEY  -> R.string.identify_error_invalid_key
        PlantNetError.QUOTA_EXCEEDED   -> R.string.identify_error_quota_exceeded
        PlantNetError.NO_INTERNET      -> R.string.identify_error_no_internet
        PlantNetError.TIMEOUT          -> R.string.identify_error_timeout
        PlantNetError.SERVER_ERROR     -> R.string.identify_error_server
        PlantNetError.UNKNOWN          -> R.string.identify_error_unknown
    }
}

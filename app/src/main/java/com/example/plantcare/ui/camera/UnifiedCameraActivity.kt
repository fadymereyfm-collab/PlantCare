package com.example.plantcare.ui.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.plantcare.CrashReporter
import com.example.plantcare.R
import com.example.plantcare.ui.disease.DiseaseDiagnosisActivity
import com.example.plantcare.ui.identify.PlantIdentifyActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

/**
 * UnifiedCameraActivity (F2.3)
 *
 * The activity that lives behind the center camera FAB in MainActivity's
 * new Bottom Navigation. It hosts a CameraX preview + a 2-button
 * segmented "mode slider" (Pflanze erkennen | Gesundheit prüfen) that the
 * user can toggle BEFORE shooting. After a capture (or gallery pick) the
 * image is saved to the app's cache and the appropriate downstream
 * activity is started with `EXTRA_PRELOADED_IMAGE_PATH`:
 *
 *   - IDENTIFY  → [PlantIdentifyActivity]
 *   - DIAGNOSE  → [DiseaseDiagnosisActivity]
 *
 * Both downstream activities accept the extra and skip their own camera
 * step, reusing their existing `runPrepareAndApply(Uri.fromFile(f))`
 * pipeline. This Activity's only responsibility is the capture UX — no
 * PlantNet or Gemini interaction happens here.
 *
 * Lifecycle: `finish()`'d after the downstream activity is started so the
 * user can `Back` from PlantIdentify or DiseaseDiagnosis directly to
 * MainActivity without bouncing through this camera step.
 *
 * Permission: CAMERA. Declined → AlertDialog with a deep link into the
 * system app settings.
 *
 * Threading: CameraX uses [ContextCompat.getMainExecutor]; ImageCapture
 * callbacks land on the main thread. File copy from a gallery URI runs
 * on a dedicated executor.
 */
class UnifiedCameraActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.example.plantcare.format.FontScaleHelper.wrap(newBase))
    }

    companion object {
        /**
         * Optional Intent extra (one of [MODE_IDENTIFY] / [MODE_DIAGNOSE]).
         * Pre-selects the mode toggle when the activity opens. Used by the
         * F2.4 "+" → "Mit Kamera erkennen" path inside Mein Garten which
         * always wants Identify mode.
         */
        const val EXTRA_INITIAL_MODE = "com.example.plantcare.extra.INITIAL_CAMERA_MODE"

        /** Identify mode (PlantNet). Default when no extra is present. */
        const val MODE_IDENTIFY = "identify"

        /** Diagnose mode (Gemini Vision). */
        const val MODE_DIAGNOSE = "diagnose"

        private const val FILE_PREFIX_IDENTIFY = "CAM_ID_"
        private const val FILE_PREFIX_DIAGNOSE = "CAM_DX_"
    }

    private lateinit var previewView: PreviewView
    private lateinit var modeToggle: MaterialButtonToggleGroup
    private lateinit var modeIdentify: MaterialButton
    private lateinit var modeDiagnose: MaterialButton
    private lateinit var shutterButton: ImageButton
    private lateinit var galleryButton: ImageButton
    private lateinit var closeButton: ImageButton

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var galleryLauncher: ActivityResultLauncher<String>

    // 2026-05-20 Feature 1 (Crop): launcher for uCrop's Activity. Result is
    // delivered via Intent — RESULT_OK + UCrop.getOutput(data) for success,
    // UCrop.RESULT_ERROR for failure, RESULT_CANCELED for user back-out.
    private lateinit var uCropLauncher: ActivityResultLauncher<Intent>

    // Saved across the uCrop round-trip so the result handler can re-route
    // to the correct downstream Activity and fall back to the un-cropped
    // image if cropping fails.
    private var pendingCropSourceFile: File? = null
    private var pendingCropMode: String = MODE_IDENTIFY

    /** Cached so callbacks can post back. */
    private lateinit var mainExecutor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unified_camera)

        mainExecutor = ContextCompat.getMainExecutor(this)

        previewView   = findViewById(R.id.previewView)
        modeToggle    = findViewById(R.id.modeToggle)
        modeIdentify  = findViewById(R.id.modeIdentify)
        modeDiagnose  = findViewById(R.id.modeDiagnose)
        shutterButton = findViewById(R.id.shutterButton)
        galleryButton = findViewById(R.id.galleryButton)
        closeButton   = findViewById(R.id.closeButton)

        // Mode toggle — restore from saved state, then intent extra, then default.
        val initialMode: String = savedInstanceState?.getString(EXTRA_INITIAL_MODE)
                ?: intent?.getStringExtra(EXTRA_INITIAL_MODE)
                ?: MODE_IDENTIFY
        applyMode(initialMode)

        modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            // No-op visually — the toggle group manages selection. Kept as a
            // hook for future analytics / haptics.
        }

        closeButton.setOnClickListener { finish() }
        shutterButton.setOnClickListener { onShutterPressed() }
        galleryButton.setOnClickListener { galleryLauncher.launch("image/*") }

        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startCamera()
            else showPermissionDeniedDialog()
        }

        galleryLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> uri?.let(::handleGalleryPick) }

        // 2026-05-20 Feature 1 (Crop): uCrop result handling. Either route
        // the cropped output to Identify/Diagnose, fall back to the
        // un-cropped source if the user/library errored out, or re-enable
        // the shutter if they backed out of the crop screen.
        uCropLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result -> handleCropResult(result.resultCode, result.data) }

        // 2026-05-20 Feature 1 (Crop): listen to the post-capture sheet's
        // choice (CROP / SKIP / CANCEL) and dispatch accordingly. Registered
        // here so it survives configuration changes.
        supportFragmentManager.setFragmentResultListener(
            ImagePostCaptureBottomSheet.REQUEST_KEY,
            this
        ) { _, bundle ->
            val choice = bundle.getString(ImagePostCaptureBottomSheet.RESULT_KEY_CHOICE)
                ?: ImagePostCaptureBottomSheet.RESULT_CHOICE_CANCEL
            val filePath = bundle.getString(ImagePostCaptureBottomSheet.RESULT_KEY_FILE_PATH)
            val mode = bundle.getString(ImagePostCaptureBottomSheet.RESULT_KEY_MODE)
                ?: MODE_IDENTIFY
            val file = filePath?.let { File(it) }
            when (choice) {
                ImagePostCaptureBottomSheet.RESULT_CHOICE_CROP ->
                    file?.let { launchUCrop(it, mode) }
                ImagePostCaptureBottomSheet.RESULT_CHOICE_SKIP ->
                    file?.let { routeToTarget(it, mode) }
                else -> {
                    // CANCEL — user backed out of the sheet. Re-enable the
                    // shutter so they can re-shoot without leaving the
                    // camera. Gallery path doesn't disable the shutter, so
                    // a no-op here is fine for that branch.
                    shutterButton.isEnabled = true
                }
            }
        }

        // Start camera once we have permission. CameraX bindToLifecycle ties
        // the use cases to this Activity's lifecycle so onStop/onDestroy
        // releases them.
        if (hasCameraPermission()) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_INITIAL_MODE, currentMode())
    }

    private fun applyMode(mode: String) {
        when (mode) {
            MODE_DIAGNOSE -> modeToggle.check(R.id.modeDiagnose)
            else          -> modeToggle.check(R.id.modeIdentify)
        }
    }

    private fun currentMode(): String =
        if (modeToggle.checkedButtonId == R.id.modeDiagnose) MODE_DIAGNOSE else MODE_IDENTIFY

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun showPermissionDeniedDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.camera_permission_denied_title)
            .setMessage(R.string.camera_permission_denied_body)
            .setPositiveButton(android.R.string.ok) { d, _ ->
                d.dismiss()
                runCatching {
                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(i)
                }
                finish()
            }
            .setNegativeButton(android.R.string.cancel) { d, _ ->
                d.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = try {
                providerFuture.get()
            } catch (t: Throwable) {
                CrashReporter.log(t)
                Toast.makeText(this, R.string.camera_capture_failed, Toast.LENGTH_SHORT).show()
                finish()
                return@addListener
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (t: Throwable) {
                // E.g. device with no back camera (Chromebook) — fall back
                // to gallery only.
                CrashReporter.log(t)
                Toast.makeText(this, R.string.camera_capture_failed, Toast.LENGTH_SHORT).show()
            }
        }, mainExecutor)
    }

    private fun onShutterPressed() {
        val capture = imageCapture ?: return
        val mode = currentMode()
        val outFile = newCacheFile(mode)
        val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()

        // Disable to prevent double-shot; re-enabled if capture fails.
        shutterButton.isEnabled = false

        capture.takePicture(
            opts,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // 2026-05-20 Feature 1 (Crop): give the user a chance
                    // to crop the freshly-captured image before it's routed
                    // downstream. ImagePostCaptureBottomSheet replies via
                    // the FragmentResult listener registered in onCreate.
                    showPostCaptureSheet(outFile, mode)
                    // Shutter stays disabled until the user picks an option
                    // (or cancels). Re-enabled by the listener on cancel
                    // and never re-enabled on crop/skip because this
                    // Activity finishes after routing.
                }

                override fun onError(exc: ImageCaptureException) {
                    CrashReporter.log(exc)
                    shutterButton.isEnabled = true
                    Toast.makeText(
                        this@UnifiedCameraActivity,
                        R.string.camera_capture_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun handleGalleryPick(source: Uri) {
        val mode = currentMode()
        val outFile = newCacheFile(mode)
        var ok = false
        try {
            contentResolver.openInputStream(source)?.use { input: InputStream ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            ok = outFile.length() > 0L
        } catch (t: Throwable) {
            CrashReporter.log(t)
        }
        if (!ok) {
            Toast.makeText(this, R.string.camera_capture_failed, Toast.LENGTH_SHORT).show()
            return
        }
        // 2026-05-20 Feature 1 (Crop): gallery imports get the same post-
        // capture sheet — gallery photos are often the worst-framed (older
        // photos, mixed scenes), so crop here pays off even more than for
        // fresh camera captures.
        showPostCaptureSheet(outFile, mode)
    }

    /**
     * 2026-05-20 Feature 1 (Crop): present the post-capture choice sheet
     * (Crop / Use as is / Cancel). Result is delivered via the
     * FragmentResult listener registered in [onCreate].
     */
    private fun showPostCaptureSheet(file: File, mode: String) {
        if (isFinishing || isDestroyed) return
        // Defensive: never stack two sheets if the user somehow triggers
        // a second capture before the first sheet is dismissed.
        val existing = supportFragmentManager.findFragmentByTag(
            ImagePostCaptureBottomSheet.TAG
        )
        if (existing != null) return
        ImagePostCaptureBottomSheet.newInstance(file.absolutePath, mode)
            .show(supportFragmentManager, ImagePostCaptureBottomSheet.TAG)
    }

    /**
     * 2026-05-20 Feature 1 (Crop): launch uCrop on [sourceFile]. The
     * cropped output lands in the same Pictures dir with a `cropped_`
     * prefix so the existing cleanup heuristics in the downstream
     * Activity still match. Result is handled by [handleCropResult].
     */
    private fun launchUCrop(sourceFile: File, mode: String) {
        pendingCropSourceFile = sourceFile
        pendingCropMode = mode

        val sourceUri = Uri.fromFile(sourceFile)
        val destFile = File(sourceFile.parentFile, "cropped_${sourceFile.name}")
        val destUri = Uri.fromFile(destFile)

        val toolbarTitle = getString(
            if (mode == MODE_DIAGNOSE) R.string.crop_screen_title_diagnose
            else R.string.crop_screen_title_identify
        )

        val options = UCrop.Options().apply {
            // Free-style crop: leaf photos rarely fit a fixed aspect ratio,
            // and the user knows best which region matters.
            setFreeStyleCropEnabled(true)
            setHideBottomControls(false)
            setShowCropGrid(true)
            setShowCropFrame(true)
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setToolbarTitle(toolbarTitle)
            // The library reads these from the theme too, but uCrop's old
            // code paths fall back to hard-coded blues if the theme doesn't
            // resolve — passing them here is belt-and-braces.
            setToolbarColor(ContextCompat.getColor(this@UnifiedCameraActivity, R.color.pc_primary))
            setStatusBarColor(ContextCompat.getColor(this@UnifiedCameraActivity, R.color.pc_primary))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@UnifiedCameraActivity, R.color.pc_primary))
            setToolbarWidgetColor(ContextCompat.getColor(this@UnifiedCameraActivity, R.color.pc_onPrimary))
        }

        val intent = UCrop.of(sourceUri, destUri)
            .withOptions(options)
            .getIntent(this)
        uCropLauncher.launch(intent)
    }

    /**
     * 2026-05-20 Feature 1 (Crop): handle the uCrop ActivityResult.
     *
     * - [android.app.Activity.RESULT_OK] → use the cropped output
     * - [UCrop.RESULT_ERROR] → toast + fall back to the un-cropped source
     * - other (user back) → re-enable shutter and stay on camera
     */
    private fun handleCropResult(resultCode: Int, data: Intent?) {
        val mode = pendingCropMode
        val originalFile = pendingCropSourceFile
        pendingCropSourceFile = null

        when (resultCode) {
            RESULT_OK -> {
                val outputUri = data?.let { UCrop.getOutput(it) }
                val croppedPath = outputUri?.path
                if (croppedPath != null) {
                    val croppedFile = File(croppedPath)
                    if (croppedFile.exists() && croppedFile.length() > 0L) {
                        routeToTarget(croppedFile, mode)
                        return
                    }
                }
                // Output missing/empty — fall through to original.
                Toast.makeText(this, R.string.crop_failed, Toast.LENGTH_SHORT).show()
                originalFile?.let { routeToTarget(it, mode) } ?: run {
                    shutterButton.isEnabled = true
                }
            }
            UCrop.RESULT_ERROR -> {
                val err = data?.let { UCrop.getError(it) }
                if (err != null) CrashReporter.log(err)
                Toast.makeText(this, R.string.crop_failed, Toast.LENGTH_SHORT).show()
                // Fall back to original — better to analyse the un-cropped
                // image than to make the user retake.
                originalFile?.let { routeToTarget(it, mode) } ?: run {
                    shutterButton.isEnabled = true
                }
            }
            else -> {
                // RESULT_CANCELED — user backed out of uCrop. Stay on
                // camera and re-enable the shutter.
                shutterButton.isEnabled = true
            }
        }
    }

    private fun newCacheFile(mode: String): File {
        val prefix = if (mode == MODE_DIAGNOSE) FILE_PREFIX_DIAGNOSE else FILE_PREFIX_IDENTIFY
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // Reuse the same Pictures dir other camera flows write to so the
        // downstream activity's existing cleanup heuristics still apply.
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: cacheDir
        return File(dir, "${prefix}${ts}.jpg")
    }

    private fun routeToTarget(file: File, mode: String) {
        val target = when (mode) {
            MODE_DIAGNOSE -> Intent(this, DiseaseDiagnosisActivity::class.java).apply {
                putExtra(
                    DiseaseDiagnosisActivity.EXTRA_PRELOADED_IMAGE_PATH,
                    file.absolutePath
                )
            }
            else -> Intent(this, PlantIdentifyActivity::class.java).apply {
                putExtra(
                    PlantIdentifyActivity.EXTRA_PRELOADED_IMAGE_PATH,
                    file.absolutePath
                )
            }
        }
        startActivity(target)
        finish()
    }
}

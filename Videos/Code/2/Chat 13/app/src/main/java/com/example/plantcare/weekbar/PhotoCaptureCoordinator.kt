package com.example.plantcare.weekbar

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import com.example.plantcare.EmailContext
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.plantcare.R
import com.example.plantcare.FirebaseSyncManager
import com.example.plantcare.PlantPhoto
import com.example.plantcare.media.CoverCloudSync
import com.example.plantcare.media.PhotoStorage
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.time.LocalDate
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class PhotoCaptureCoordinator(
    private val fragment: Fragment,
    private val plantProvider: PlantProvider = DefaultPlantProvider(),
    private val onCalendarPhotoSaved: (() -> Unit)? = null,
    private val onTitlePhotoSaved: ((Long) -> Unit)? = null
) {

    private var pendingUri: Uri? = null
    private var pendingTitlePlantId: Long? = null
    private var pendingTitlePlantName: String? = null
    private var pendingAfterPermission: (() -> Unit)? = null

    private val requestCameraPermissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingAfterPermission?.invoke()
            } else {
                Toast.makeText(
                    fragment.requireContext(),
                    fragment.getString(getCameraRequiredStringId()),
                    Toast.LENGTH_SHORT
                ).show()
                clearPending()
            }
            pendingAfterPermission = null
        }

    private val takePictureLauncher: ActivityResultLauncher<Uri> =
        fragment.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (!success) {
                clearPending()
                return@registerForActivityResult
            }

            val context = fragment.requireContext()
            val imageUri = pendingUri ?: run { clearPending(); return@registerForActivityResult }

            val emailRaw = EmailContext.current(context)
            if (emailRaw.isNullOrBlank()) {
                Toast.makeText(context, R.string.error_no_active_session, Toast.LENGTH_SHORT).show()
                clearPending()
                return@registerForActivityResult
            }
            val userEmail: String = emailRaw

            val titlePlantId = pendingTitlePlantId
            if (titlePlantId != null) {
                // Always use canonical cover file location for title cover
                ArchiveStore.setCover(context, userEmail, titlePlantId, imageUri)
                // Snapshot+clear pending* IMMEDIATELY so a rapid second
                // capture that lands `pendingUri` while this save is
                // still in flight isn't wiped out by our trailing
                // `clearPending()`. The save closure already captured
                // imageUri/titlePlantId as locals, so it's unaffected
                // by the early clear.
                clearPending()
                // Run the DB writes inside lifecycleScope and await them so
                // the UI-refresh signals (`onTitlePhotoSaved`, `notifyChange`)
                // fire AFTER the photo row exists. The previous fire-and-forget
                // raced with the UI re-query and showed a placeholder until
                // the next refresh.
                fragment.lifecycleScope.launch {
                    savePhotoToDb(context, userEmail, titlePlantId, imageUri, LocalDate.now(), true)
                    updatePlantImageUriIfPossible(context, titlePlantId, imageUri)
                    // Upload cover to Storage and mirror to Firestore + Room
                    try { CoverCloudSync.uploadCover(context, titlePlantId, null, null) } catch (_: Throwable) {}
                    onTitlePhotoSaved?.invoke(titlePlantId)
                    try { com.example.plantcare.DataChangeNotifier.notifyChange() }
                    catch (t: Throwable) { com.example.plantcare.CrashReporter.log(t) }
                }
            } else {
                // B (Calendar quick-action): nach Aufnahme bietet ein Chooser an,
                // das Foto entweder normal zu archivieren ODER direkt zur
                // Krankheitsdiagnose weiterzuleiten — das spart einen Wechsel
                // in eine separate Activity + erneutes Foto.
                showCalendarPhotoActionChooser(
                    context = context,
                    imageUri = imageUri,
                    onArchive = { archiveCalendarPhoto(context, userEmail, imageUri) }
                )
            }
        }

    private val pickImageFromGalleryLauncher: ActivityResultLauncher<String> =
        fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                clearPending()
                return@registerForActivityResult
            }
            val context = fragment.requireContext()
            val emailRaw = EmailContext.current(context)
            if (emailRaw.isNullOrBlank()) {
                Toast.makeText(context, R.string.error_no_active_session, Toast.LENGTH_SHORT).show()
                clearPending()
                return@registerForActivityResult
            }
            val userEmail = emailRaw

            // Gleicher Chooser wie bei der Kamera — Galerie-Foto kann genauso
            // archiviert ODER direkt zum Krankheits-Check geschickt werden.
            showCalendarPhotoActionChooser(
                context = context,
                imageUri = uri,
                onArchive = { archiveCalendarPhoto(context, userEmail, uri) }
            )
        }

    /**
     * Bestehender Archiv-Pfad (Pflanzen-Auswahl + Datum + DB-Insert + Cloud-Sync),
     * extrahiert aus den Launcher-Callbacks damit sowohl der "Archiv"-Zweig
     * des Choosers als auch potentielle Test-Aufrufer ihn unverändert nutzen
     * können.
     *
     * Downscales the captured image to a 1280-px long edge (~400 KB) before
     * archiving. Camera output is typically 4000×3000 / 8-15 MB JPEG; without
     * this every archived photo would bloat local storage AND the per-photo
     * Firestore Storage upload, hitting free-tier quota fast for active users.
     */
    private fun archiveCalendarPhoto(context: Context, userEmail: String, imageUri: Uri) {
        val plants = plantProvider.listUserPlants(context, userEmail)
        if (plants.isEmpty()) {
            AlertDialog.Builder(context)
                .setMessage(R.string.no_plants_available)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            clearPending()
            return
        }
        showPlantPicker(plants) { picked ->
            pickDate(context) { pickedDate ->
                // Snapshot+clear pending* before launching the BG save so a
                // rapid second capture isn't wiped by our trailing
                // clearPending. `imageUri` is captured by the closure
                // already, so the launch operates on its own snapshot.
                clearPending()
                // Downscale on a BG thread to keep the picker dialogs snappy.
                // savePhotoToDb is suspend now so the await happens here —
                // notifyChange + onCalendarPhotoSaved fire after the row is
                // actually in the DB.
                fragment.lifecycleScope.launch(Dispatchers.IO) {
                    val processedUri = downscaleAndPersist(context, imageUri) ?: imageUri
                    // If we created a downscaled copy, the original capture
                    // file in our PlantCare/ dir is no longer needed. Best-
                    // effort cleanup; leaves gallery / external URIs alone
                    // (we never owned those).
                    if (processedUri != imageUri) {
                        deleteOwnedCaptureFile(context, imageUri)
                    }
                    ArchiveStore.addCalendarPhoto(
                        context = context,
                        email = userEmail,
                        plantId = picked.id,
                        plantName = picked.name,
                        uri = processedUri,
                        date = pickedDate
                    )
                    savePhotoToDb(context, userEmail, picked.id, processedUri, pickedDate, false)
                    onCalendarPhotoSaved?.invoke()
                    try { com.example.plantcare.DataChangeNotifier.notifyChange() }
                    catch (t: Throwable) { com.example.plantcare.CrashReporter.log(t) }
                }
            }
        }
    }

    /**
     * Best-effort delete of an owned FileProvider URI's underlying file. We
     * only act on URIs whose authority matches our own provider AND whose
     * path lives under `getExternalFilesDir(...)/PlantCare/` (where
     * `createImageUri()` writes). External URIs (MediaStore, gallery apps)
     * are left untouched — the user might still want them in their gallery.
     */
    private fun deleteOwnedCaptureFile(context: Context, uri: Uri) {
        try {
            if (uri.authority != context.packageName + ".provider") return
            val segments = uri.pathSegments
            if (segments.size < 2) return
            val tag = segments[0]
            val base = when (tag) {
                "my_images", "all_external_files" ->
                    context.getExternalFilesDir(null) ?: return
                else -> return
            }
            val rel = segments.drop(1).joinToString("/")
            val file = File(base, rel)
            // Only touch files under our PlantCare/ subdir. The trailing
            // separator is critical — without it, a hypothetical sibling
            // dir named "PlantCareEvil/" would slip past `startsWith`
            // checks since the canonical paths share the prefix
            // ".../Pictures/PlantCare". Comparing against
            // ".../Pictures/PlantCare/" forces the sub-directory check.
            val canonical = file.canonicalPath
            val ownDir = File(base, "Pictures/PlantCare").canonicalPath + File.separator
            if (!canonical.startsWith(ownDir)) return
            file.delete()
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
        }
    }

    /**
     * Read the source URI, decode to within 1280-px long edge while applying
     * EXIF orientation (so portraits don't end up sideways), recompress as
     * JPEG 80 into a fresh file under `getExternalFilesDir(Pictures)/PlantCare/`,
     * and return the FileProvider URI. Returns null on any failure; caller
     * should fall back to the raw source (preserving original behaviour).
     *
     * Decode strategy:
     *   1. `BitmapFactory.Options(inJustDecodeBounds = true)` reads only the
     *      JPEG header so we don't OOM on a 50 MP photo.
     *   2. Pick the smallest `inSampleSize` whose `src/sample` is still ≥
     *      maxEdge. This gives us a working bitmap *just above* maxEdge so a
     *      `createScaledBitmap` final pass can hit maxEdge precisely without
     *      throwing away resolution we'd want.
     *      Example: src=1300, maxEdge=1280 → sample=1, decoded=1300×975,
     *      then scaled to 1280×960. Old algorithm picked sample=2 → 650×488,
     *      losing half the resolution unnecessarily.
     *   3. EXIF orientation matrix applied to the bitmap so the saved JPEG
     *      is upright in any viewer (no EXIF in the output, the pixel data
     *      itself carries the rotation). Without this, every portrait
     *      camera capture ended up sideways in the archive.
     *   4. JPEG 80 typically lands ~300-450 KB per photo.
     */
    private fun downscaleAndPersist(context: Context, source: Uri): Uri? {
        return try {
            val cr = context.contentResolver
            val bounds = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            cr.openInputStream(source)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, bounds)
            }
            val maxEdge = 1280
            val src = maxOf(bounds.outWidth, bounds.outHeight)
            if (src <= 0) return null

            // Largest sample where the result still meets/exceeds maxEdge.
            // Final scale-down step below brings it to exactly maxEdge.
            var sample = 1
            while (src / (sample * 2) >= maxEdge) sample *= 2

            val decode = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            var bmp = cr.openInputStream(source)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, decode)
            } ?: return null

            // EXIF rotation — read from the source stream, build a Matrix,
            // bake it into the pixel data via Bitmap.createBitmap. Camera
            // captures store orientation in EXIF rather than rotating the
            // raw pixels, so without this every portrait photo lands
            // sideways once we strip EXIF on re-encode.
            val orientation = readExifOrientation(cr, source)
            if (orientation != androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    && orientation != androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED) {
                val matrix = android.graphics.Matrix()
                when (orientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 ->
                        matrix.postRotate(90f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 ->
                        matrix.postRotate(180f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 ->
                        matrix.postRotate(270f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                        matrix.postScale(-1f, 1f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL ->
                        matrix.postScale(1f, -1f)
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

            // Final scale to honour the maxEdge cap exactly. The decode
            // above gave us "just above" maxEdge; this hits the target.
            val longEdge = maxOf(bmp.width, bmp.height)
            val finalBmp = if (longEdge > maxEdge) {
                val ratio = maxEdge.toFloat() / longEdge
                val w = (bmp.width * ratio).toInt()
                val h = (bmp.height * ratio).toInt()
                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
                if (scaled !== bmp) runCatching { bmp.recycle() }
                scaled
            } else bmp

            val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val appDir = File(picturesDir, "PlantCare").apply { mkdirs() }
            val outFile = File(appDir, "IMG_${UUID.randomUUID()}.jpg")
            java.io.FileOutputStream(outFile).use { out ->
                finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            }
            runCatching { finalBmp.recycle() }

            FileProvider.getUriForFile(
                context, context.packageName + ".provider", outFile
            )
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
            null
        }
    }

    /**
     * Read EXIF Orientation from the source URI. Tries the InputStream-based
     * ExifInterface (works for content://, gallery, FileProvider) first.
     * Returns ORIENTATION_NORMAL on any failure so the caller skips rotation.
     */
    private fun readExifOrientation(cr: android.content.ContentResolver, source: Uri): Int {
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
     * Zeigt direkt nach Foto-Erfassung im Kalender-Flow einen Chooser:
     *  - "Archivieren" → bestehender Pfad (onArchive)
     *  - "Krankheits-Check" → kopiert das Bild in eine DISEASE_*.jpg Datei
     *    und startet [com.example.plantcare.ui.disease.DiseaseDiagnosisActivity]
     *    mit `EXTRA_PRELOADED_IMAGE_PATH`. Der Nutzer landet im Diagnose-
     *    Screen mit bereits sichtbarer Vorschau und aktivem Analyze-Button.
     */
    private fun showCalendarPhotoActionChooser(
        context: Context,
        imageUri: Uri,
        onArchive: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(R.string.calendar_photo_action_title)
            .setMessage(R.string.calendar_photo_action_message)
            .setPositiveButton(R.string.calendar_photo_action_diagnose) { dlg, _ ->
                dlg.dismiss()
                val path = copyImageForDiagnosis(context, imageUri)
                if (path == null) {
                    Toast.makeText(
                        context,
                        R.string.camera_file_create_error,
                        Toast.LENGTH_SHORT
                    ).show()
                    clearPending()
                    return@setPositiveButton
                }
                val intent = android.content.Intent(
                    context,
                    com.example.plantcare.ui.disease.DiseaseDiagnosisActivity::class.java
                ).apply {
                    putExtra(
                        com.example.plantcare.ui.disease.DiseaseDiagnosisActivity
                            .EXTRA_PRELOADED_IMAGE_PATH,
                        path
                    )
                }
                context.startActivity(intent)
                clearPending()
            }
            .setNegativeButton(R.string.calendar_photo_action_archive) { dlg, _ ->
                dlg.dismiss()
                onArchive()
            }
            .setOnCancelListener { clearPending() }
            .show()
    }

    /**
     * Kopiert die Bilddatei aus einer beliebigen Uri (FileProvider, MediaStore,
     * SAF) in eine eigene DISEASE_*.jpg Datei in `getExternalFilesDir(Pictures)`,
     * weil DiseaseDiagnosisActivity einen absoluten Pfad als Extra erwartet
     * (Uri-Grants über Activity-Grenzen sind brüchig). Gibt den absoluten Pfad
     * oder null bei Fehler zurück.
     */
    private fun copyImageForDiagnosis(context: Context, source: Uri): String? {
        return try {
            val timestamp = java.text.SimpleDateFormat(
                "yyyyMMdd_HHmmss", java.util.Locale.getDefault()
            ).format(java.util.Date())
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: return null
            val target = File.createTempFile("DISEASE_${timestamp}_", ".jpg", storageDir)
            context.contentResolver.openInputStream(source)?.use { input ->
                java.io.FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (target.length() == 0L) return null
            target.absolutePath
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
            null
        }
    }

    fun startCalendarPhotoFlow() {
        pendingTitlePlantId = null
        pendingTitlePlantName = null
        val context = fragment.requireContext()
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val launchCapture: () -> Unit = {
            pendingUri = createImageUri()
            pendingUri?.let { takePictureLauncher.launch(it) }
        }
        if (hasCamera) {
            launchCapture()
        } else {
            pendingAfterPermission = launchCapture
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun startCalendarGalleryFlow() {
        pendingTitlePlantId = null
        pendingTitlePlantName = null
        pickImageFromGalleryLauncher.launch("image/*")
    }

    fun startTitleImageFlow(plantId: Long, plantName: String?) {
        pendingTitlePlantId = plantId
        pendingTitlePlantName = plantName

        val context = fragment.requireContext()
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val launchCapture: () -> Unit = {
            // Ensure cover captures go to the canonical cover.jpg so uploadCover() can find it.
            pendingUri = PhotoStorage.coverUri(context, plantId)
            pendingUri?.let { takePictureLauncher.launch(it) }
        }
        if (hasCamera) {
            launchCapture()
        } else {
            pendingAfterPermission = launchCapture
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun createImageUri(): Uri? {
        val context = fragment.requireContext()
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, "PlantCare").apply { mkdirs() }
        val file = File(appDir, "IMG_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    }

    private fun clearPending() {
        pendingUri = null
        pendingTitlePlantId = null
        pendingTitlePlantName = null
        pendingAfterPermission = null
    }

    private fun showPlantPicker(
        plants: List<PlantListItem>,
        onPicked: (PlantListItem) -> Unit
    ) {
        val context = fragment.requireContext()
        val names = plants.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.pick_plant_title)
            .setItems(names) { _, which -> onPicked(plants[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickDate(context: Context, onPicked: (LocalDate) -> Unit) {
        val cal = Calendar.getInstance()
        val dlg = DatePickerDialog(
            context,
            { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        // Archive photos represent moments in the past — backdating a year
        // covers "I forgot to upload last summer's growth shot", and the
        // upper cap is today (a future date for a photo taken just now is
        // nonsensical and would order strangely in the calendar).
        val minCal = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
        dlg.datePicker.minDate = minCal.timeInMillis
        dlg.datePicker.maxDate = System.currentTimeMillis()
        dlg.setOnCancelListener { onPicked(LocalDate.now()) }
        dlg.show()
    }

    private fun getCameraRequiredStringId(): Int = R.string.msg_camera_required

    /**
     * Save to DB (Room) then start pending-aware upload (so immediate deletion works).
     *
     * Suspending so the caller can await the insert before signalling UI
     * refresh. The previous `lifecycleScope.launch(IO)` fire-and-forget
     * raced with the caller's `notifyChange()`: the UI re-queried the DB
     * and got an empty result because the insert hadn't committed yet,
     * showing a broken-image placeholder until the next refresh.
     */
    private suspend fun savePhotoToDb(
        context: Context,
        userEmail: String,
        plantId: Long,
        uri: Uri,
        date: LocalDate,
        isCover: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            val photo = PlantPhoto().apply {
                this.plantId = plantId.toInt()
                this.imagePath = uri.toString()
                this.dateTaken = date.toString()
                this.isCover = isCover
                this.userEmail = userEmail
            }
            val newId = com.example.plantcare.data.repository.PlantPhotoRepository
                .getInstance(context).insertBlocking(photo).toInt()
            photo.id = newId

            try {
                FirebaseSyncManager.get().uploadPlantPhotoWithPending(context, photo, uri)
            } catch (t: Throwable) {
                com.example.plantcare.CrashReporter.log(t)
            }
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
        }
    }

    /**
     * Best-effort update of Plant.imageUri for the captured title image.
     * Suspending for the same reason as [savePhotoToDb] — the caller wants
     * to know when the row mutation has committed before refreshing UI.
     */
    private suspend fun updatePlantImageUriIfPossible(
        context: Context,
        plantId: Long,
        uri: Uri
    ) = withContext(Dispatchers.IO) {
        try {
            val plantRepo = com.example.plantcare.data.repository.PlantRepository
                .getInstance(context)
            val plant = plantRepo.findByIdBlocking(plantId.toInt()) ?: return@withContext
            plant.imageUri = uri.toString()
            plantRepo.updateBlocking(plant)
            runCatching { FirebaseSyncManager.get().syncPlant(plant) }
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
        }
    }

    data class PlantListItem(val id: Long, val name: String)

    interface PlantProvider {
        fun listUserPlants(context: Context, userEmail: String): List<PlantListItem>
    }

    class DefaultPlantProvider : PlantProvider {
        override fun listUserPlants(context: Context, userEmail: String): List<PlantListItem> {
            // Synchronous read used by the photo-capture archive flow
            // (called from the activity-result callback on main thread).
            // The blocking helper would crash Room's main-thread check, so
            // we wrap the suspend variant in `runBlocking` — it dispatches
            // to IO via the repository's `withContext(Dispatchers.IO)`,
            // satisfying Room while keeping the synchronous return contract.
            // This is the ONE remaining runBlocking in this file; it's
            // unavoidable until the picker flow itself is refactored as
            // a suspend chain.
            return runCatching {
                runBlocking {
                    com.example.plantcare.data.repository.PlantRepository
                        .getInstance(context)
                        .getUserPlantsListForUser(userEmail)
                        .mapNotNull { plant ->
                            val name = plant.nickname?.takeIf { it.isNotBlank() }
                                ?: plant.name?.takeIf { it.isNotBlank() }
                                ?: "Plant ${plant.id}"
                            PlantListItem(id = plant.id.toLong(), name = name)
                        }
                }
            }.getOrElse { emptyList() }
        }
    }
}
package com.example.plantcare.weekbar

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
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
import com.example.plantcare.DatabaseClient
import com.example.plantcare.FirebaseSyncManager
import com.example.plantcare.PlantPhoto
import com.example.plantcare.Plant
import com.example.plantcare.media.CoverCloudSync
import com.example.plantcare.media.PhotoStorage
import java.io.File
import java.lang.reflect.Method
import java.time.LocalDate
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
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

            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val emailRaw = prefs.getString("current_user_email", null)
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
                savePhotoToDb(context, userEmail, titlePlantId, imageUri, LocalDate.now(), true)
                updatePlantImageUriIfPossible(context, titlePlantId, imageUri)
                // Upload cover to Storage and mirror to Firestore + Room
                try { CoverCloudSync.uploadCover(context, titlePlantId, null, null) } catch (_: Throwable) {}
                onTitlePhotoSaved?.invoke(titlePlantId)
                try { com.example.plantcare.DataChangeNotifier.notifyChange() } catch (_: Throwable) {}
                clearPending()
            } else {
                val plants = plantProvider.listUserPlants(context, userEmail)
                if (plants.isEmpty()) {
                    AlertDialog.Builder(context)
                        .setMessage("No plants available")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    clearPending()
                    return@registerForActivityResult
                }
                showPlantPicker(plants) { picked ->
                    pickDate(context) { pickedDate ->
                        ArchiveStore.addCalendarPhoto(
                            context = context,
                            email = userEmail,
                            plantId = picked.id,
                            plantName = picked.name,
                            uri = imageUri,
                            date = pickedDate
                        )
                        savePhotoToDb(context, userEmail, picked.id, imageUri, pickedDate, false)
                        onCalendarPhotoSaved?.invoke()
                        try { com.example.plantcare.DataChangeNotifier.notifyChange() } catch (_: Throwable) {}
                        clearPending()
                    }
                }
            }
        }

    private val pickImageFromGalleryLauncher: ActivityResultLauncher<String> =
        fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                clearPending()
                return@registerForActivityResult
            }
            val context = fragment.requireContext()
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val emailRaw = prefs.getString("current_user_email", null)
            if (emailRaw.isNullOrBlank()) {
                Toast.makeText(context, R.string.error_no_active_session, Toast.LENGTH_SHORT).show()
                clearPending()
                return@registerForActivityResult
            }
            val userEmail = emailRaw

            val plants = plantProvider.listUserPlants(context, userEmail)
            if (plants.isEmpty()) {
                AlertDialog.Builder(context)
                    .setMessage("No plants available")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                clearPending()
                return@registerForActivityResult
            }
            showPlantPicker(plants) { picked ->
                pickDate(context) { pickedDate ->
                    ArchiveStore.addCalendarPhoto(
                        context = context,
                        email = userEmail,
                        plantId = picked.id,
                        plantName = picked.name,
                        uri = uri,
                        date = pickedDate
                    )
                    savePhotoToDb(context, userEmail, picked.id, uri, pickedDate, false)
                    onCalendarPhotoSaved?.invoke()
                    try { com.example.plantcare.DataChangeNotifier.notifyChange() } catch (_: Throwable) {}
                    clearPending()
                }
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
            .setTitle("Pick a plant")
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
        dlg.setOnCancelListener { onPicked(LocalDate.now()) }
        dlg.show()
    }

    private fun getCameraRequiredStringId(): Int {
        val res = fragment.resources
        val id = res.getIdentifier("msg_camera_required", "string", fragment.requireContext().packageName)
        return if (id != 0) id else android.R.string.dialog_alert_title
    }

    /**
     * Save to DB (Room) then start pending-aware upload (so immediate deletion works).
     */
    private fun savePhotoToDb(
        context: Context,
        userEmail: String,
        plantId: Long,
        uri: Uri,
        date: LocalDate,
        isCover: Boolean
    ) {
        runBlocking {
            withContext(Dispatchers.IO) {
                val db = DatabaseClient.getInstance(context).getAppDatabase()
                val photo = PlantPhoto().apply {
                    this.plantId = plantId.toInt()
                    this.imagePath = uri.toString() // temporary local path (will be replaced by PENDING marker)
                    this.dateTaken = date.toString()
                    this.isCover = isCover
                    this.userEmail = userEmail
                }
                val newId = db.plantPhotoDao().insert(photo).toInt()
                photo.id = newId

                try {
                    FirebaseSyncManager.get().uploadPlantPhotoWithPending(context, photo, uri)
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Best-effort update of Plant.imageUri for the captured title image.
     * Uses reflection to find a suitable DAO method to fetch the plant and then calls update(plant).
     */
    private fun updatePlantImageUriIfPossible(context: Context, plantId: Long, uri: Uri) {
        runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    val db = DatabaseClient.getInstance(context).getAppDatabase()
                    val dao = db.plantDao()

                    // Try to fetch the Plant by ID using common method names and param types
                    val methodCandidates = listOf(
                        "findById",
                        "getById",
                        "getPlantById",
                        "getPlant",
                        "get",
                        "find",
                        "loadById",
                        "load"
                    )
                    val paramTypes = listOf(Long::class.java, java.lang.Long.TYPE, Int::class.java, java.lang.Integer.TYPE)

                    var foundPlant: Any? = null
                    loop@ for (name in methodCandidates) {
                        for (pt in paramTypes) {
                            try {
                                val m: Method = dao.javaClass.getMethod(name, pt)
                                val arg: Any = when (pt) {
                                    Long::class.java, java.lang.Long.TYPE -> plantId
                                    else -> plantId.toInt()
                                }
                                val res = m.invoke(dao, arg)
                                if (res != null) {
                                    foundPlant = res
                                    break@loop
                                }
                            } catch (_: Throwable) {
                                // ignore and continue
                            }
                        }
                    }

                    if (foundPlant != null) {
                        // Set imageUri on the entity
                        when (foundPlant) {
                            is Plant -> {
                                foundPlant.imageUri = uri.toString()
                            }
                            else -> {
                                // Try setter then field
                                try {
                                    val setter = foundPlant.javaClass.getMethod("setImageUri", String::class.java)
                                    setter.invoke(foundPlant, uri.toString())
                                } catch (_: Throwable) {
                                    try {
                                        val f = foundPlant.javaClass.getDeclaredField("imageUri")
                                        f.isAccessible = true
                                        f.set(foundPlant, uri.toString())
                                    } catch (_: Throwable) {}
                                }
                            }
                        }

                        // Call dao.update(...)
                        var updated = false
                        try {
                            val updateM = dao.javaClass.getMethod("update", foundPlant.javaClass)
                            updateM.invoke(dao, foundPlant)
                            updated = true
                        } catch (_: Throwable) {
                            try {
                                // Fallback if DAO is typed with Plant
                                val updateM = dao.javaClass.getMethod("update", Plant::class.java)
                                val casted = (foundPlant as? Plant)
                                if (casted != null) {
                                    updateM.invoke(dao, casted)
                                    updated = true
                                }
                            } catch (_: Throwable) {}
                        }

                        if (updated) {
                            try {
                                val plantForSync: Plant? = when (foundPlant) {
                                    is Plant -> foundPlant
                                    else -> null
                                }
                                if (plantForSync != null) {
                                    FirebaseSyncManager.get().syncPlant(plantForSync)
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {
                    // Best-effort; ignore failures
                }
            }
        }
    }

    data class PlantListItem(val id: Long, val name: String)

    interface PlantProvider {
        fun listUserPlants(context: Context, userEmail: String): List<PlantListItem>
    }

    class DefaultPlantProvider : PlantProvider {
        override fun listUserPlants(context: Context, userEmail: String): List<PlantListItem> {
            return runCatching {
                runBlocking {
                    withContext(Dispatchers.IO) {
                        val db = DatabaseClient.getInstance(context).getAppDatabase()
                        val dao = db.plantDao()
                        val candidates: List<Pair<String, Array<Class<*>>>> = listOf(
                            "getAllUserPlantsForUser" to arrayOf(String::class.java),
                            "getAllUserPlants" to emptyArray(),
                            "getAllMyPlants" to emptyArray(),
                            "getAll" to emptyArray()
                        )

                        var plantsRaw: List<Any>? = null
                        for ((name, params) in candidates) {
                            try {
                                val m: Method = dao.javaClass.getMethod(name, *params)
                                val result = if (params.isEmpty()) m.invoke(dao) else m.invoke(dao, userEmail)
                                @Suppress("UNCHECKED_CAST")
                                plantsRaw = result as? List<Any>
                                if (plantsRaw != null) break
                            } catch (_: Throwable) {}
                        }
                        val list = plantsRaw ?: emptyList()
                        list.mapNotNull { any ->
                            val id = readId(any) ?: return@mapNotNull null
                            val name = readNickname(any) ?: readName(any) ?: "Plant $id"
                            PlantListItem(id = id, name = name)
                        }
                    }
                }
            }.getOrElse { emptyList() }
        }

        private fun readId(any: Any): Long? =
            tryCallLong(any, "getId") ?: tryGetLongField(any, "id")

        private fun readName(any: Any): String? =
            tryCallString(any, "getName") ?: tryGetStringField(any, "name")

        private fun readNickname(any: Any): String? =
            tryCallString(any, "getNickname") ?: tryGetStringField(any, "nickname")

        private fun tryCallLong(target: Any, method: String): Long? = try {
            val m = target.javaClass.getMethod(method)
            when (val v = m.invoke(target)) {
                is Long -> v
                is Int -> v.toLong()
                is Number -> v.toLong()
                else -> null
            }
        } catch (_: Throwable) { null }

        private fun tryCallString(target: Any, method: String): String? = try {
            val m = target.javaClass.getMethod(method)
            (m.invoke(target) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) { null }

        private fun tryGetLongField(target: Any, field: String): Long? = try {
            val f = target.javaClass.getDeclaredField(field).apply { isAccessible = true }
            when (val v = f.get(target)) {
                is Long -> v
                is Int -> v.toLong()
                is Number -> v.toLong()
                else -> null
            }
        } catch (_: Throwable) { null }

        private fun tryGetStringField(target: Any, field: String): String? = try {
            val f = target.javaClass.getDeclaredField(field).apply { isAccessible = true }
            (f.get(target) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) { null }
    }
}
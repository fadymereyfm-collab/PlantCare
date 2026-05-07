package com.example.plantcare.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.plantcare.DataChangeNotifier
import com.example.plantcare.Plant
import com.example.plantcare.weekbar.ArchiveStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

object CoverCloudSync {
    private const val TAG = "CoverCloudSync"

    /**
     * Upload the local cover.jpg to Firebase Storage, store its download URL in users/{uid}/plants/{plantId},
     * mirror into Room and also into plants/{email}_{plantId}.imageUri (import after reinstall).
     */
    @JvmStatic
    fun uploadCover(
        context: Context,
        plantId: Long,
        onSuccess: ((String) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onError?.invoke(IllegalStateException("No signed-in user"))
            return
        }

        val uid = user.uid
        val plantRepo = com.example.plantcare.data.repository.PlantRepository
                .getInstance(context.applicationContext)
        val plant: Plant? = runCatching { plantRepo.findByIdBlocking(plantId.toInt()) }.getOrNull()
        val userEmail: String? = plant?.userEmail ?: user.email

        val file: File = PhotoStorage.coverFile(context, plantId)
        if (!file.exists() || file.length() <= 0L) {
            onError?.invoke(IllegalStateException("Cover file missing or empty"))
            return
        }

        val storagePath = "users/$uid/plants/$plantId/cover.jpg"
        val ref = FirebaseStorage.getInstance().reference.child(storagePath)
        val localFileUri = Uri.fromFile(file)

        ref.putFile(localFileUri)
            .addOnSuccessListener {
                ref.downloadUrl
                    .addOnSuccessListener { url ->
                        val urlStr = url.toString()
                        Log.d(TAG, "Uploaded cover for plant $plantId, url=$urlStr")

                        // Write to Firestore users/{uid}/plants/{plantId}
                        val docRef = FirebaseFirestore.getInstance()
                            .collection("users").document(uid)
                            .collection("plants").document(plantId.toString())

                        val data = hashMapOf(
                            "coverUrl" to urlStr,
                            "localId" to plantId
                        )
                        docRef.set(data, SetOptions.merge())
                            .addOnSuccessListener {
                                // Mirror to Room + ArchiveStore
                                com.example.plantcare.util.BgExecutor.io {
                                    runCatching {
                                        plantRepo.updateProfileImageBlocking(plantId.toInt(), urlStr)
                                        val email = userEmail
                                        if (!email.isNullOrEmpty()) {
                                            ArchiveStore.setCover(context.applicationContext, email, plantId, Uri.parse(urlStr))
                                        }
                                        DataChangeNotifier.notifyChange()
                                    }.onFailure { t -> Log.e(TAG, "Room update failed", t) }
                                }

                                // Mirror into plants collection (reinstall-proof)
                                if (!userEmail.isNullOrEmpty()) {
                                    val docId = "${userEmail}_${plantId}"
                                    val mergeDoc = hashMapOf<String, Any>("imageUri" to urlStr)
                                    FirebaseFirestore.getInstance()
                                        .collection("plants")
                                        .document(docId)
                                        .set(mergeDoc, SetOptions.merge())
                                        .addOnSuccessListener { Log.d(TAG, "Merged imageUri into plants/$docId") }
                                        .addOnFailureListener { e -> Log.e(TAG, "Failed merging imageUri into plants/$docId", e) }
                                }

                                onSuccess?.invoke(urlStr)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Firestore set coverUrl failed", e)
                                onError?.invoke(e)
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to get downloadUrl", e)
                        onError?.invoke(e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Upload failed", e)
                onError?.invoke(e)
            }
    }

    /**
     * Pull coverUrl values from users/{uid}/plants into Room,
     * fallback to plants collection imageUri if needed.
     */
    @JvmStatic
    @JvmOverloads
    fun pullCoversToRoom(
        context: Context,
        onComplete: ((Int) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onError?.invoke(IllegalStateException("No signed-in user"))
            return
        }
        val uid = user.uid
        val email = user.email

        val plantRepo = com.example.plantcare.data.repository.PlantRepository
                .getInstance(context.applicationContext)

        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("plants")
            .get()
            .addOnSuccessListener { snapshot ->
                var updated = 0
                if (!snapshot.isEmpty) {
                    com.example.plantcare.util.BgExecutor.io {
                        try {
                            for (doc in snapshot.documents) {
                                val url = doc.getString("coverUrl") ?: continue
                                val idFromField = doc.getLong("localId")?.toLong()
                                val targetId = idFromField ?: doc.id.toLongOrNull()
                                if (targetId == null) continue
                                runCatching {
                                    plantRepo.updateProfileImageBlocking(targetId.toInt(), url)
                                    if (!email.isNullOrEmpty()) {
                                        ArchiveStore.setCover(context.applicationContext, email, targetId, Uri.parse(url))
                                    }
                                    updated++
                                }
                            }
                            Log.d(TAG, "Pulled $updated cover urls into Room from users/{uid}/plants")
                            DataChangeNotifier.notifyChange()
                            if (!email.isNullOrEmpty()) {
                                pullFromPlantsCollection(context, email, plantRepo) { more ->
                                    onComplete?.invoke(updated + more)
                                }
                            } else {
                                onComplete?.invoke(updated)
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Room bulk update failed", t)
                            onError?.invoke(t)
                        }
                    }
                } else {
                    if (!email.isNullOrEmpty()) {
                        pullFromPlantsCollection(context, email, plantRepo, onComplete)
                    } else {
                        onComplete?.invoke(0)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore pull failed", e)
                if (!email.isNullOrEmpty()) {
                    pullFromPlantsCollection(context, email, plantRepo, onComplete)
                } else {
                    onError?.invoke(e)
                }
            }
    }

    private fun pullFromPlantsCollection(
        context: Context,
        email: String,
        plantRepo: com.example.plantcare.data.repository.PlantRepository,
        onComplete: ((Int) -> Unit)?
    ) {
        FirebaseFirestore.getInstance()
            .collection("plants")
            .whereEqualTo("userEmail", email)
            .get()
            .addOnSuccessListener { snapshot ->
                var updated = 0
                if (!snapshot.isEmpty) {
                    com.example.plantcare.util.BgExecutor.io {
                        for (doc in snapshot.documents) {
                            val url = doc.getString("imageUri") ?: continue
                            val idPart = runCatching { doc.id.substringAfter("${email}_", "") }.getOrDefault("")
                            val plantId = idPart.toIntOrNull()
                            if (plantId != null) {
                                runCatching {
                                    plantRepo.updateProfileImageBlocking(plantId, url)
                                    ArchiveStore.setCover(context.applicationContext, email, plantId.toLong(), Uri.parse(url))
                                    updated++
                                }
                            }
                        }
                        Log.d(TAG, "Pulled $updated cover urls into Room from plants collection")
                        DataChangeNotifier.notifyChange()
                        onComplete?.invoke(updated)
                    }
                } else {
                    onComplete?.invoke(0)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Fallback plants collection pull failed", e)
                onComplete?.invoke(0)
            }
    }

    /**
     * Backfill: ensure plants/{email}_{id}.imageUri mirrors users/{uid}/plants coverUrl.
     * Useful once after login/reinstall so future imports have imageUri without extra fetches.
     */
    @JvmStatic
    @JvmOverloads
    fun ensurePlantsCollectionHasImageUri(context: Context, onComplete: (() -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            onComplete?.invoke(); return
        }
        val uid = user.uid
        val email = user.email ?: run {
            onComplete?.invoke(); return
        }
        val fs = FirebaseFirestore.getInstance()
        fs.collection("users").document(uid).collection("plants")
            .get()
            .addOnSuccessListener { qs ->
                if (qs.isEmpty) { onComplete?.invoke(); return@addOnSuccessListener }
                val tasks = mutableListOf<com.google.android.gms.tasks.Task<Void>>()
                for (doc in qs.documents) {
                    val cover = doc.getString("coverUrl") ?: continue
                    val idFromField = doc.getLong("localId")?.toInt()
                    val idFromDoc = doc.id.toIntOrNull()
                    val pid = idFromField ?: idFromDoc ?: continue
                    val docId = "${email}_$pid"
                    val merge = hashMapOf<String, Any>("imageUri" to cover)
                    tasks += fs.collection("plants").document(docId).set(merge, SetOptions.merge())
                }
                com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                    .addOnCompleteListener { onComplete?.invoke() }
            }
            .addOnFailureListener { onComplete?.invoke() }
    }

    /**
     * Fetch single plant cover into Room (tries users/{uid}/plants then plants imageUri).
     */
    @JvmStatic
    @JvmOverloads
    fun fetchCoverForPlant(
        context: Context,
        plantId: Long,
        onSuccess: ((String) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onError?.invoke(IllegalStateException("No signed-in user"))
            return
        }
        val uid = user.uid
        val plantsCol = FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("plants")

        plantsCol
            .whereEqualTo("localId", plantId)
            .limit(1)
            .get()
            .addOnSuccessListener { qs ->
                if (!qs.isEmpty) {
                    val doc = qs.documents[0]
                    val url = doc.getString("coverUrl")
                    if (!url.isNullOrEmpty()) {
                        mirrorToRoom(context, plantId, url, onSuccess, onError)
                    } else {
                        onError?.invoke(IllegalStateException("No coverUrl for plantId=$plantId"))
                    }
                } else {
                    plantsCol.document(plantId.toString())
                        .get()
                        .addOnSuccessListener { doc ->
                            val url = doc.getString("coverUrl")
                            if (!url.isNullOrEmpty()) {
                                mirrorToRoom(context, plantId, url, onSuccess, onError)
                            } else {
                                val email = user.email
                                if (!email.isNullOrEmpty()) {
                                    FirebaseFirestore.getInstance()
                                        .collection("plants")
                                        .document("${email}_${plantId}")
                                        .get()
                                        .addOnSuccessListener { pdoc ->
                                            val u = pdoc.getString("imageUri")
                                            if (!u.isNullOrEmpty()) {
                                                mirrorToRoom(context, plantId, u, onSuccess, onError)
                                            } else {
                                                onError?.invoke(IllegalStateException("No cover found for plantId=$plantId"))
                                            }
                                        }
                                        .addOnFailureListener { e -> onError?.invoke(e) }
                                } else {
                                    onError?.invoke(IllegalStateException("No coverUrl for plantId=$plantId"))
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "fetchCoverForPlant fallback by id failed", e)
                            onError?.invoke(e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "fetchCoverForPlant query by localId failed", e)
                onError?.invoke(e)
            }
    }

    private fun mirrorToRoom(
        context: Context,
        plantId: Long,
        url: String,
        onSuccess: ((String) -> Unit)?,
        onError: ((Throwable) -> Unit)?
    ) {
        com.example.plantcare.util.BgExecutor.io {
            runCatching {
                com.example.plantcare.data.repository.PlantRepository
                        .getInstance(context.applicationContext)
                        .updateProfileImageBlocking(plantId.toInt(), url)
                val email = FirebaseAuth.getInstance().currentUser?.email
                if (!email.isNullOrEmpty()) {
                    ArchiveStore.setCover(context.applicationContext, email, plantId, Uri.parse(url))
                }
                Log.d(TAG, "Room updated imageUri for plant $plantId from Firestore -> $url")
                DataChangeNotifier.notifyChange()
                onSuccess?.invoke(url)
            }.onFailure {
                Log.e(TAG, "Room update failed", it)
                onError?.invoke(it)
            }
        }
    }

    /**
     * NEW: Backfill any locally existing cover.jpg that isn't yet in Firestore.
     * This handles cases where a user captured a cover while not signed in.
     * After sign-in/reinstall, we'll upload missing covers automatically.
     */
    @JvmStatic
    @JvmOverloads
    fun backfillUploadMissingCovers(
        context: Context,
        onComplete: ((Int) -> Unit)? = null
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onComplete?.invoke(0)
            return
        }
        val uid = user.uid
        val email = user.email

        val fs = FirebaseFirestore.getInstance()
        fs.collection("users").document(uid).collection("plants")
            .get()
            .addOnSuccessListener { snapshot ->
                val coveredRemote = mutableSetOf<Long>()
                for (doc in snapshot.documents) {
                    val url = doc.getString("coverUrl")
                    val idFromField = doc.getLong("localId")
                    val idFromDoc = doc.id.toLongOrNull()
                    val pid = idFromField ?: idFromDoc
                    if (!url.isNullOrEmpty() && pid != null) coveredRemote.add(pid)
                }

                com.example.plantcare.util.BgExecutor.io {
                    try {
                        val plantRepo = com.example.plantcare.data.repository.PlantRepository
                                .getInstance(context.applicationContext)
                        val locals = if (!email.isNullOrEmpty()) {
                            plantRepo.getAllUserPlantsForUserBlocking(email)
                        } else {
                            plantRepo.getAllUserPlantsBlocking()
                        }
                        val toUpload = locals.filter { p ->
                            val f = PhotoStorage.coverFile(context.applicationContext, p.id.toLong())
                            f.exists() && f.length() > 0L && !coveredRemote.contains(p.id.toLong())
                        }.map { it.id.toLong() }

                        if (toUpload.isEmpty()) {
                            onComplete?.invoke(0)
                            return@io
                        }

                        val remaining = AtomicInteger(toUpload.size)
                        val successCount = AtomicInteger(0)

                        toUpload.forEach { pid ->
                            try {
                                uploadCover(
                                    context.applicationContext,
                                    pid,
                                    onSuccess = {
                                        successCount.incrementAndGet()
                                        if (remaining.decrementAndGet() == 0) {
                                            onComplete?.invoke(successCount.get())
                                        }
                                    },
                                    onError = {
                                        Log.w(TAG, "Backfill upload failed for plantId=$pid", it)
                                        if (remaining.decrementAndGet() == 0) {
                                            onComplete?.invoke(successCount.get())
                                        }
                                    }
                                )
                            } catch (t: Throwable) {
                                Log.e(TAG, "Backfill upload threw for plantId=$pid", t)
                                if (remaining.decrementAndGet() == 0) {
                                    onComplete?.invoke(successCount.get())
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Backfill preparation failed", t)
                        onComplete?.invoke(0)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to query remote covers; attempting local-only backfill", e)
                com.example.plantcare.util.BgExecutor.io {
                    try {
                        val plantRepo = com.example.plantcare.data.repository.PlantRepository
                                .getInstance(context.applicationContext)
                        val locals = if (!email.isNullOrEmpty()) {
                            plantRepo.getAllUserPlantsForUserBlocking(email)
                        } else {
                            plantRepo.getAllUserPlantsBlocking()
                        }
                        val toUpload = locals.filter { p ->
                            val f = PhotoStorage.coverFile(context.applicationContext, p.id.toLong())
                            f.exists() && f.length() > 0L
                        }.map { it.id.toLong() }

                        if (toUpload.isEmpty()) {
                            onComplete?.invoke(0)
                            return@io
                        }

                        val remaining = AtomicInteger(toUpload.size)
                        val successCount = AtomicInteger(0)

                        toUpload.forEach { pid ->
                            try {
                                uploadCover(
                                    context.applicationContext,
                                    pid,
                                    onSuccess = {
                                        successCount.incrementAndGet()
                                        if (remaining.decrementAndGet() == 0) {
                                            onComplete?.invoke(successCount.get())
                                        }
                                    },
                                    onError = {
                                        Log.w(TAG, "Backfill (no-remote-check) failed for plantId=$pid", it)
                                        if (remaining.decrementAndGet() == 0) {
                                            onComplete?.invoke(successCount.get())
                                        }
                                    }
                                )
                            } catch (_: Throwable) {
                                if (remaining.decrementAndGet() == 0) {
                                    onComplete?.invoke(successCount.get())
                                }
                            }
                        }
                    } catch (_: Throwable) {
                        onComplete?.invoke(0)
                    }
                }
            }
    }

}
package com.example.plantcare;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

/**
 * Firebase sync manager — all data lives under users/{uid}/ subcollections.
 * No email addresses appear in document IDs or collection paths.
 */
public class FirebaseSyncManager {

    private static final String TAG = "FirebaseSyncMgr";
    private static FirebaseSyncManager INSTANCE;

    public static synchronized FirebaseSyncManager get() {
        if (INSTANCE == null) INSTANCE = new FirebaseSyncManager();
        return INSTANCE;
    }

    private final FirebaseFirestore db;
    private final FirebaseStorage storage;
    private final FirebaseAuth auth;

    /** Tracks in-progress uploads: key = local photoId */
    private final Map<Integer, PendingUpload> pending = new ConcurrentHashMap<>();

    private FirebaseSyncManager() {
        db      = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        auth    = FirebaseAuth.getInstance();
    }

    /* ── helpers ─────────────────────────────────────────────── */

    /** Returns current UID or null if not authenticated. */
    private String getCurrentUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    /** Shortcut: users/{uid}/plants */
    private CollectionReference plantsRef(String uid) {
        return db.collection("users").document(uid).collection("plants");
    }

    /** Shortcut: users/{uid}/reminders */
    private CollectionReference remindersRef(String uid) {
        return db.collection("users").document(uid).collection("reminders");
    }

    /** Shortcut: users/{uid}/photos */
    private CollectionReference photosRef(String uid) {
        return db.collection("users").document(uid).collection("photos");
    }

    /* ── data classes ────────────────────────────────────────── */

    private static class PendingUpload {
        int    photoId;
        String docId;
        String storagePath;
        UploadTask task;
        boolean deleteAfterFinish;
    }

    private static class ParsedRemote {
        String docId;
        String storagePathDecoded;
    }

    /* ══════════════════════════════════════════════════════════
       PLANTS
    ══════════════════════════════════════════════════════════ */

    public void syncPlant(Plant plant) {
        String uid = getCurrentUid();
        if (plant == null || uid == null) return;

        plantsRef(uid)
                .document(String.valueOf(plant.id))
                .set(plant)
                .addOnSuccessListener(v -> Log.d(TAG, "Plant synced: " + plant.id))
                .addOnFailureListener(e -> Log.e(TAG, "Plant sync failed", e));
    }

    public void deletePlant(Plant plant) {
        String uid = getCurrentUid();
        if (plant == null || uid == null) return;

        // Delete plant document
        plantsRef(uid)
                .document(String.valueOf(plant.id))
                .delete()
                .addOnSuccessListener(v -> Log.d(TAG, "Plant deleted: " + plant.id))
                .addOnFailureListener(e -> Log.e(TAG, "Plant delete failed", e));

        // Delete cover image in Storage
        try {
            StorageReference coverRef = storage.getReference()
                    .child("users/" + uid + "/plants/" + plant.id + "/cover.jpg");
            coverRef.delete()
                    .addOnSuccessListener(v -> Log.d(TAG, "Cover storage deleted for plant " + plant.id))
                    .addOnFailureListener(e -> Log.d(TAG, "Cover storage not found (ok): " + e.getMessage()));
        } catch (Throwable t) {
            Log.w(TAG, "Cover storage reference failed", t);
        }

        deleteRemindersForPlantByUid(uid, plant.id);
        deletePhotosForPlantByUid(uid, plant.id);
    }

    /* ══════════════════════════════════════════════════════════
       REMINDERS
    ══════════════════════════════════════════════════════════ */

    public void syncReminder(WateringReminder reminder) {
        String uid = getCurrentUid();
        if (reminder == null || uid == null) return;

        String docId = reminder.plantId + "_" + reminder.date;
        remindersRef(uid)
                .document(docId)
                .set(reminder)
                .addOnFailureListener(e -> Log.e(TAG, "Reminder sync failed", e));
    }

    public void deleteReminder(WateringReminder reminder) {
        String uid = getCurrentUid();
        if (reminder == null || uid == null) return;

        String docId = reminder.plantId + "_" + reminder.date;
        remindersRef(uid)
                .document(docId)
                .delete()
                .addOnFailureListener(e -> Log.e(TAG, "Reminder delete failed", e));
    }

    /** Deletes all reminders for a plant. Accepts userEmail for backward-compat; uses UID internally. */
    public void deleteRemindersForPlant(String userEmailIgnored, int plantId) {
        String uid = getCurrentUid();
        if (uid == null) return;
        deleteRemindersForPlantByUid(uid, plantId);
    }

    private void deleteRemindersForPlantByUid(String uid, int plantId) {
        remindersRef(uid)
                .whereEqualTo("plantId", plantId)
                .get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot doc : qs.getDocuments()) doc.getReference().delete();
                    Log.d(TAG, "Deleted " + qs.size() + " reminders for plant " + plantId);
                })
                .addOnFailureListener(e -> Log.e(TAG, "deleteRemindersForPlant failed", e));
    }

    /* ══════════════════════════════════════════════════════════
       PHOTO UPLOAD WITH PENDING SUPPORT
    ══════════════════════════════════════════════════════════ */

    public void uploadPlantPhotoWithPending(Context ctx, PlantPhoto photo, Uri localUri) {
        try {
            if (photo == null || localUri == null) return;

            FirebaseUser current = auth.getCurrentUser();
            if (current == null) {
                Log.w(TAG, "No auth user. Skip upload.");
                return;
            }

            String uid       = current.getUid();
            long   now       = System.currentTimeMillis();
            String filename  = photo.plantId + "_" + now + ".jpg";
            String storagePath = "users/" + uid + "/plant_photos/" + filename;
            String docId     = filename.substring(0, filename.length() - 4);

            // Mark as pending
            photo.imagePath = "PENDING_DOC:" + docId;
            if (photo.userEmail == null || photo.userEmail.isEmpty()) {
                photo.userEmail = current.getEmail() != null ? current.getEmail() : "nouser";
            }
            safeLocalUpdatePhoto(ctx, photo);

            StorageReference ref = storage.getReference().child(storagePath);
            PendingUpload pu = new PendingUpload();
            pu.photoId     = photo.id;
            pu.docId       = docId;
            pu.storagePath = storagePath;
            pending.put(photo.id, pu);

            UploadTask task = ref.putFile(localUri);
            pu.task = task;

            task.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot snap) {
                    ref.getDownloadUrl().addOnSuccessListener(url -> {
                        PendingUpload pFinal = pending.get(photo.id);
                        if (pFinal != null && pFinal.deleteAfterFinish) {
                            photo.imagePath = url.toString();
                            safeLocalUpdatePhoto(ctx, photo);
                            deletePhotoSmart(photo, ctx);
                            pending.remove(photo.id);
                            return;
                        }
                        photo.imagePath = url.toString();
                        safeLocalUpdatePhoto(ctx, photo);

                        photosRef(uid)
                                .document(docId)
                                .set(photo)
                                .addOnFailureListener(e -> Log.e(TAG, "Photo metadata write failed", e));

                        pending.remove(photo.id);
                        Log.d(TAG, "Photo upload complete (id=" + photo.id + ")");
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Get download URL failed", e);
                        pending.remove(photo.id);
                    });
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Upload failed", e);
                    pending.remove(photo.id);
                }
            });

        } catch (Throwable t) {
            Log.e(TAG, "Unexpected upload error", t);
        }
    }

    private void safeLocalUpdatePhoto(Context ctx, PlantPhoto photo) {
        try {
            if (ctx != null) {
                DatabaseClient.getInstance(ctx).getAppDatabase().plantPhotoDao().update(photo);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Local DB photo update failed", t);
        }
    }

    /* ══════════════════════════════════════════════════════════
       SMART PHOTO DELETION
    ══════════════════════════════════════════════════════════ */

    public void deletePhotoSmart(PlantPhoto photo, Context ctx) {
        if (photo == null) return;
        String uid = getCurrentUid();

        if (photo.imagePath != null && photo.imagePath.startsWith("PENDING_DOC:")) {
            PendingUpload pu = pending.get(photo.id);
            if (pu != null) {
                pu.deleteAfterFinish = true;
                if (pu.task != null && !pu.task.isComplete()) pu.task.cancel();
                try {
                    storage.getReference().child(pu.storagePath).delete();
                } catch (Throwable e) { CrashReporter.INSTANCE.log(e); }
                if (uid != null) {
                    try { photosRef(uid).document(pu.docId).delete(); } catch (Throwable e) { CrashReporter.INSTANCE.log(e); }
                }
                safeLocalDeletePhoto(ctx, photo);
                pending.remove(photo.id);
            } else {
                safeLocalDeletePhoto(ctx, photo);
            }
            return;
        }

        if (photo.imagePath != null && photo.imagePath.startsWith("http")) {
            ParsedRemote pr = parseDownloadUrl(photo.imagePath);
            if (pr != null) {
                if (uid != null) {
                    photosRef(uid).document(pr.docId).delete()
                            .addOnSuccessListener(v -> Log.d(TAG, "Photo doc deleted: " + pr.docId))
                            .addOnFailureListener(e -> Log.e(TAG, "Photo doc delete failed", e));
                }
                try {
                    storage.getReference().child(pr.storagePathDecoded).delete()
                            .addOnSuccessListener(v -> Log.d(TAG, "Storage deleted: " + pr.storagePathDecoded))
                            .addOnFailureListener(e -> Log.e(TAG, "Storage delete failed", e));
                } catch (Throwable t) {
                    Log.e(TAG, "Storage reference build failed", t);
                }
            } else {
                Log.w(TAG, "Could not parse remote URL; remote delete skipped");
            }
            safeLocalDeletePhoto(ctx, photo);
            return;
        }

        safeLocalDeletePhoto(ctx, photo);
    }

    private void safeLocalDeletePhoto(Context ctx, PlantPhoto photo) {
        try {
            if (ctx != null) {
                DatabaseClient.getInstance(ctx).getAppDatabase().plantPhotoDao().delete(photo);
            }
        } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
        if (photo.imagePath != null
                && !photo.imagePath.startsWith("http")
                && !photo.imagePath.startsWith("content://")
                && !photo.imagePath.startsWith("PENDING_DOC:")) {
            try {
                java.io.File f = new java.io.File(photo.imagePath);
                if (f.exists()) f.delete();
            } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
        }
    }

    /* ══════════════════════════════════════════════════════════
       BULK PHOTO DELETES
    ══════════════════════════════════════════════════════════ */

    /** Accepts userEmail for backward-compat; uses UID internally. */
    public void deletePhotosForPlant(String userEmailIgnored, int plantId) {
        String uid = getCurrentUid();
        if (uid == null) return;
        deletePhotosForPlantByUid(uid, plantId);
    }

    private void deletePhotosForPlantByUid(String uid, int plantId) {
        photosRef(uid)
                .whereEqualTo("plantId", plantId)
                .get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        PlantPhoto p = doc.toObject(PlantPhoto.class);
                        if (p != null) deletePhotoSmart(p, null);
                        else doc.getReference().delete();
                    }
                    Log.d(TAG, "Deleted " + qs.size() + " photos for plant " + plantId);
                })
                .addOnFailureListener(e -> Log.e(TAG, "deletePhotosForPlant failed", e));
    }

    public void deleteAllPhotosForUser(String userEmailIgnored) {
        String uid = getCurrentUid();
        if (uid == null) return;
        photosRef(uid)
                .get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        PlantPhoto p = doc.toObject(PlantPhoto.class);
                        if (p != null) deletePhotoSmart(p, null);
                        else doc.getReference().delete();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "deleteAllPhotosForUser failed", e));
    }

    /* ══════════════════════════════════════════════════════════
       IMPORT (reads from users/{uid}/ — new structure)
    ══════════════════════════════════════════════════════════ */

    /** userEmail parameter kept for backward-compat; UID is used internally. */
    public void importUserData(String userEmailIgnored, DataImportCallback callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            if (callback != null) {
                callback.onPlantsImported(Collections.emptyList());
                callback.onRemindersImported(Collections.emptyList());
                callback.onPhotosImported(Collections.emptyList());
            }
            return;
        }

        plantsRef(uid).get().addOnSuccessListener(snap -> {
            List<Plant> plants = new ArrayList<>();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                Plant p = doc.toObject(Plant.class);
                if (p != null) plants.add(p);
            }
            if (callback != null) callback.onPlantsImported(plants);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "importUserData plants failed", e);
            if (callback != null) callback.onPlantsImported(Collections.emptyList());
        });

        remindersRef(uid).get().addOnSuccessListener(snap -> {
            List<WateringReminder> reminders = new ArrayList<>();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                WateringReminder r = doc.toObject(WateringReminder.class);
                if (r != null) reminders.add(r);
            }
            if (callback != null) callback.onRemindersImported(reminders);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "importUserData reminders failed", e);
            if (callback != null) callback.onRemindersImported(Collections.emptyList());
        });

        photosRef(uid).get().addOnSuccessListener(snap -> {
            List<PlantPhoto> photos = new ArrayList<>();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                PlantPhoto ph = doc.toObject(PlantPhoto.class);
                if (ph != null) photos.add(ph);
            }
            if (callback != null) callback.onPhotosImported(photos);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "importUserData photos failed", e);
            if (callback != null) callback.onPhotosImported(Collections.emptyList());
        });
    }

    public interface DataImportCallback {
        void onPlantsImported(List<Plant> plants);
        void onRemindersImported(List<WateringReminder> reminders);
        void onPhotosImported(List<PlantPhoto> photos);
    }

    /* ── URL parser ──────────────────────────────────────────── */

    private ParsedRemote parseDownloadUrl(String downloadUrl) {
        try {
            int idx = downloadUrl.indexOf("/o/");
            if (idx < 0) return null;
            String afterO = downloadUrl.substring(idx + 3);
            int q = afterO.indexOf('?');
            if (q >= 0) afterO = afterO.substring(0, q);
            String decoded    = URLDecoder.decode(afterO, StandardCharsets.UTF_8);
            int    lastSlash  = decoded.lastIndexOf('/');
            if (lastSlash < 0) return null;
            String filename   = decoded.substring(lastSlash + 1);
            String docId      = filename.endsWith(".jpg")
                    ? filename.substring(0, filename.length() - 4)
                    : filename;
            ParsedRemote pr   = new ParsedRemote();
            pr.docId              = docId;
            pr.storagePathDecoded = decoded;
            return pr;
        } catch (Exception e) {
            Log.e(TAG, "parseDownloadUrl failed", e);
            return null;
        }
    }
}

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

    /** Shortcut: users/{uid}/rooms */
    private CollectionReference roomsRef(String uid) {
        return db.collection("users").document(uid).collection("rooms");
    }

    /** Shortcut: users/{uid}/memos */
    private CollectionReference memosRef(String uid) {
        return db.collection("users").document(uid).collection("memos");
    }

    /** Shortcut: users/{uid}/vacation (single fixed doc id "current"). */
    private com.google.firebase.firestore.DocumentReference vacationDocRef(String uid) {
        return db.collection("users").document(uid)
                .collection("vacation").document("current");
    }

    /** Shortcut: users/{uid}/gamification/streak (single fixed doc). */
    private com.google.firebase.firestore.DocumentReference streakDocRef(String uid) {
        return db.collection("users").document(uid)
                .collection("gamification").document("streak");
    }

    /** Shortcut: users/{uid}/gamification/challenges (single fixed doc). */
    private com.google.firebase.firestore.DocumentReference challengesDocRef(String uid) {
        return db.collection("users").document(uid)
                .collection("gamification").document("challenges");
    }

    /** Shortcut: users/{uid}/billing/proStatus (single fixed doc). */
    private com.google.firebase.firestore.DocumentReference proStatusDocRef(String uid) {
        return db.collection("users").document(uid)
                .collection("billing").document("proStatus");
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
        // F10.3 cascade: Room's FK CASCADE drops local memos, but Firestore
        // has no FK awareness — without an explicit delete the user's
        // memos for this plant become orphans the journal can never
        // surface again (the parent plant id no longer exists, so a
        // future restore would re-insert them only to fail FK on the
        // local DB). Mirror the reminders/photos pattern.
        deleteMemosForPlantByUid(uid, plant.id);
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
       ROOMS

       Until 2026-05-06 the app shipped with no Firebase representation
       for rooms — they lived in Room only. That meant the most common
       sign-in scenario (new device, reinstall) silently dropped the
       user's custom rooms back to the five hard-coded defaults. The
       sync surface here is intentionally minimal: id-keyed `set` for
       upsert, id-keyed `delete` for removal, and a snapshot getter
       used by the post-sign-in restore.
    ══════════════════════════════════════════════════════════ */

    public void syncRoom(RoomCategory room) {
        String uid = getCurrentUid();
        if (room == null || uid == null) return;
        roomsRef(uid)
                .document(String.valueOf(room.id))
                .set(room)
                .addOnFailureListener(e -> Log.e(TAG, "Room sync failed", e));
    }

    public void deleteRoom(int roomId) {
        String uid = getCurrentUid();
        if (uid == null || roomId <= 0) return;
        roomsRef(uid)
                .document(String.valueOf(roomId))
                .delete()
                .addOnFailureListener(e -> Log.e(TAG, "Room delete failed", e));
    }

    public interface RoomsImportCallback {
        void onRoomsImported(List<RoomCategory> rooms);
    }

    /**
     * Pull every room the user owns from Firestore. Used by MainActivity
     * after a successful sign-in so a fresh install rebuilds the same
     * room layout the user had on their other device.
     */
    public void importRoomsForCurrentUser(RoomsImportCallback callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            if (callback != null) callback.onRoomsImported(Collections.emptyList());
            return;
        }
        roomsRef(uid).get()
                .addOnSuccessListener(snap -> {
                    List<RoomCategory> rooms = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        RoomCategory r = doc.toObject(RoomCategory.class);
                        if (r != null && r.name != null && !r.name.isEmpty()) rooms.add(r);
                    }
                    if (callback != null) callback.onRoomsImported(rooms);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "importRoomsForCurrentUser failed", e);
                    if (callback != null) callback.onRoomsImported(Collections.emptyList());
                });
    }

    /* ══════════════════════════════════════════════════════════
       JOURNAL MEMOS

       Free-text plant journal notes the user authors via the journal
       dialog. Pre-2026-05-06 they lived in Room only — same gap that
       used to bite the rooms feature: a fresh install or new device
       silently dropped every memo. Mirrors the rooms sync surface:
       id-keyed `set` for upsert, id-keyed `delete`, snapshot getter
       for the post-sign-in restore.
    ══════════════════════════════════════════════════════════ */

    public void syncJournalMemo(com.example.plantcare.data.journal.JournalMemo memo) {
        String uid = getCurrentUid();
        if (memo == null || uid == null || memo.getId() <= 0) return;
        memosRef(uid)
                .document(String.valueOf(memo.getId()))
                .set(memo)
                .addOnFailureListener(e -> Log.e(TAG, "Memo sync failed", e));
    }

    public void deleteJournalMemo(int memoId) {
        String uid = getCurrentUid();
        if (uid == null || memoId <= 0) return;
        memosRef(uid)
                .document(String.valueOf(memoId))
                .delete()
                .addOnFailureListener(e -> Log.e(TAG, "Memo delete failed", e));
    }

    /**
     * Cascade-delete all memos belonging to a plant. Called from
     * {@link #deletePlant(Plant)} so plant removal in Firestore mirrors
     * the local FK CASCADE on `journal_memo.plantId`. Without this,
     * deleting a plant from one device would leave its memos as
     * permanent orphans in `users/{uid}/memos` — invisible in the
     * journal (no parent plant) but still counting against quota.
     */
    private void deleteMemosForPlantByUid(String uid, int plantId) {
        memosRef(uid)
                .whereEqualTo("plantId", plantId)
                .get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot doc : qs.getDocuments()) doc.getReference().delete();
                    Log.d(TAG, "Deleted " + qs.size() + " memos for plant " + plantId);
                })
                .addOnFailureListener(e -> Log.e(TAG, "deleteMemosForPlant failed", e));
    }

    public interface MemosImportCallback {
        void onMemosImported(List<com.example.plantcare.data.journal.JournalMemo> memos);
    }

    /**
     * Pull every memo the user owns from Firestore. Used by MainActivity
     * after a successful sign-in so a fresh install rebuilds the same
     * journal notes the user had on their other device.
     */
    /* ══════════════════════════════════════════════════════════
       VACATION (single doc per user)

       Pre-2026-05-06 vacation lived only in SharedPreferences. A user
       reinstalling mid-vacation, or signing in on a second device,
       would see their plants screaming at them for water — the device
       had no idea the user was on holiday. One doc at
       `users/{uid}/vacation/current` is enough: the user can only
       have one active vacation at a time.
    ══════════════════════════════════════════════════════════ */

    public void syncVacation(com.example.plantcare.feature.vacation.VacationDoc doc) {
        String uid = getCurrentUid();
        if (uid == null || doc == null) return;
        vacationDocRef(uid)
                .set(doc)
                .addOnFailureListener(e -> Log.e(TAG, "Vacation sync failed", e));
    }

    public void clearVacationCloud() {
        String uid = getCurrentUid();
        if (uid == null) return;
        vacationDocRef(uid)
                .delete()
                .addOnFailureListener(e -> Log.e(TAG, "Vacation clear failed", e));
    }

    public interface VacationImportCallback {
        void onVacationImported(com.example.plantcare.feature.vacation.VacationDoc doc);
    }

    /**
     * Pull the current vacation doc — null if the user has no active
     * vacation in cloud. Single-doc fetch, not a collection scan.
     */
    public void importVacationForCurrentUser(VacationImportCallback callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            if (callback != null) callback.onVacationImported(null);
            return;
        }
        vacationDocRef(uid).get()
                .addOnSuccessListener(snap -> {
                    com.example.plantcare.feature.vacation.VacationDoc d = null;
                    if (snap != null && snap.exists()) {
                        d = snap.toObject(com.example.plantcare.feature.vacation.VacationDoc.class);
                    }
                    if (callback != null) callback.onVacationImported(d);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "importVacationForCurrentUser failed", e);
                    if (callback != null) callback.onVacationImported(null);
                });
    }

    /* ══════════════════════════════════════════════════════════
       GAMIFICATION (streak + challenges, two fixed docs per user)

       Pre-this-session both lived only in SharedPreferences. A user
       reinstalling the app or moving to a new device would lose a
       100-day streak and every challenge trophy — a gut-punch UX
       failure that Duolingo, Strava, and Headspace all solve by
       making gamification state cloud-first. Two docs (not one)
       because their write rates differ wildly: streak updates once
       per day, challenges potentially many times. Splitting keeps
       challenge churn from invalidating streak reads.
    ══════════════════════════════════════════════════════════ */

    public void syncStreak(com.example.plantcare.feature.streak.StreakDoc doc) {
        String uid = getCurrentUid();
        if (uid == null || doc == null) return;
        streakDocRef(uid)
                .set(doc)
                .addOnFailureListener(e -> Log.e(TAG, "Streak sync failed", e));
    }

    public interface StreakImportCallback {
        void onStreakImported(com.example.plantcare.feature.streak.StreakDoc doc);
    }

    public void importStreakForCurrentUser(StreakImportCallback callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            if (callback != null) callback.onStreakImported(null);
            return;
        }
        streakDocRef(uid).get()
                .addOnSuccessListener(snap -> {
                    com.example.plantcare.feature.streak.StreakDoc d = null;
                    if (snap != null && snap.exists()) {
                        d = snap.toObject(com.example.plantcare.feature.streak.StreakDoc.class);
                    }
                    if (callback != null) callback.onStreakImported(d);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "importStreakForCurrentUser failed", e);
                    if (callback != null) callback.onStreakImported(null);
                });
    }

    public void syncChallenges(com.example.plantcare.feature.streak.ChallengesDoc doc) {
        String uid = getCurrentUid();
        if (uid == null || doc == null) return;
        challengesDocRef(uid)
                .set(doc)
                .addOnFailureListener(e -> Log.e(TAG, "Challenges sync failed", e));
    }

    public interface ChallengesImportCallback {
        void onChallengesImported(com.example.plantcare.feature.streak.ChallengesDoc doc);
    }

    public void importChallengesForCurrentUser(ChallengesImportCallback callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            if (callback != null) callback.onChallengesImported(null);
            return;
        }
        challengesDocRef(uid).get()
                .addOnSuccessListener(snap -> {
                    com.example.plantcare.feature.streak.ChallengesDoc d = null;
                    if (snap != null && snap.exists()) {
                        d = snap.toObject(com.example.plantcare.feature.streak.ChallengesDoc.class);
                    }
                    if (callback != null) callback.onChallengesImported(d);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "importChallengesForCurrentUser failed", e);
                    if (callback != null) callback.onChallengesImported(null);
                });
    }

    /* ══════════════════════════════════════════════════════════
       PRO STATUS (single doc per user)

       B7: Google Play Billing keeps the canonical purchase record on
       its own servers, but our local `pro_status` SharedPreferences
       is what every isPro() caller actually reads. On a fresh
       install / new device, isPro() defaults to false until the user
       remembers to tap "Restore Purchases" — most never do, and
       review-bomb us as "fraud, paid twice". Mirroring our local
       belief into Firestore lets a sign-in restore reflect the user's
       Pro state immediately. The Play Billing reconciliation runs
       in parallel and overrides if the two ever disagree.
    ══════════════════════════════════════════════════════════ */

    public void syncProStatus(com.example.plantcare.billing.ProStatusDoc doc) {
        String uid = getCurrentUid();
        if (uid == null || doc == null) return;
        proStatusDocRef(uid)
                .set(doc)
                .addOnFailureListener(e -> Log.e(TAG, "Pro status sync failed", e));
    }

    public interface ProStatusImportCallback {
        void onProStatusImported(com.example.plantcare.billing.ProStatusDoc doc);
    }

    public void importProStatusForCurrentUser(ProStatusImportCallback callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            if (callback != null) callback.onProStatusImported(null);
            return;
        }
        proStatusDocRef(uid).get()
                .addOnSuccessListener(snap -> {
                    com.example.plantcare.billing.ProStatusDoc d = null;
                    if (snap != null && snap.exists()) {
                        d = snap.toObject(com.example.plantcare.billing.ProStatusDoc.class);
                    }
                    if (callback != null) callback.onProStatusImported(d);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "importProStatusForCurrentUser failed", e);
                    if (callback != null) callback.onProStatusImported(null);
                });
    }

    public void importMemosForCurrentUser(MemosImportCallback callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            if (callback != null) callback.onMemosImported(Collections.emptyList());
            return;
        }
        memosRef(uid).get()
                .addOnSuccessListener(snap -> {
                    List<com.example.plantcare.data.journal.JournalMemo> memos = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        com.example.plantcare.data.journal.JournalMemo m =
                                doc.toObject(com.example.plantcare.data.journal.JournalMemo.class);
                        if (m != null && m.getText() != null && !m.getText().isEmpty()) {
                            memos.add(m);
                        }
                    }
                    if (callback != null) callback.onMemosImported(memos);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "importMemosForCurrentUser failed", e);
                    if (callback != null) callback.onMemosImported(Collections.emptyList());
                });
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
                com.example.plantcare.data.repository.PlantPhotoRepository
                        .getInstance(ctx).updateBlocking(photo);
            }
        } catch (Throwable t) {
            // CS2: route through CrashReporter so Crashlytics surfaces
            // local-DB write failures during photo upload — same pattern
            // as N5 (notification SecurityException). Log.w alone gave
            // us zero visibility into how many users were silently losing
            // photo metadata after a successful Storage upload.
            CrashReporter.INSTANCE.log(t);
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
                com.example.plantcare.data.repository.PlantPhotoRepository
                        .getInstance(ctx).deleteBlocking(photo);
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
            // 2026-05-05: URLDecoder.decode(String, Charset) is API 33+. Use the
            // (String, String) overload which has been around since API 1 to keep
            // minSdk 24 compatibility.
            String decoded    = URLDecoder.decode(afterO, "UTF-8");
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

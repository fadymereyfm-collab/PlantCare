package com.example.plantcare;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.bumptech.glide.Glide;
import com.example.plantcare.ui.util.FragmentBg;
import java.util.*;

public class AddPlantDialogFragment extends DialogFragment {
    private EditText editPlantName, editLighting, editSoil, editFertilizing, editWatering, editStartDate;
    // v16: per-care-type interval inputs (matching dialog_add_to_my_plants).
    private EditText editIntervalWater, editIntervalFertilize, editIntervalMist, editIntervalRepot;
    private Spinner spinnerRooms;
    private Button buttonAddRoom, buttonAdd, buttonClose, buttonCapture;
    private ImageView imagePreview;
    private Uri photoUri = null;

    private List<RoomCategory> rooms = new ArrayList<>();
    private int selectedRoomId = -1;
    private Date selectedDate = null;
    private Runnable onPlantAdded;

    public void setOnPlantAdded(Runnable listener) {
        this.onPlantAdded = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_add_plant, null);

        editPlantName = view.findViewById(R.id.editPlantName);
        editLighting = view.findViewById(R.id.editLighting);
        editSoil = view.findViewById(R.id.editSoil);
        editFertilizing = view.findViewById(R.id.editFertilizing);
        editWatering = view.findViewById(R.id.editWatering);
        editStartDate = view.findViewById(R.id.editStartDate);
        spinnerRooms = view.findViewById(R.id.spinnerRooms);
        buttonAddRoom = view.findViewById(R.id.buttonAddRoom);
        buttonAdd = view.findViewById(R.id.buttonAdd);
        buttonClose = view.findViewById(R.id.buttonClose);
        buttonCapture = view.findViewById(R.id.buttonCapture);
        imagePreview = view.findViewById(R.id.imagePreview);

        editIntervalWater     = view.findViewById(R.id.editIntervalWater);
        editIntervalFertilize = view.findViewById(R.id.editIntervalFertilize);
        editIntervalMist      = view.findViewById(R.id.editIntervalMist);
        editIntervalRepot     = view.findViewById(R.id.editIntervalRepot);

        // v16: pre-fill care-plan inputs with the GENERIC fallback so the
        // user sees a sensible starting plan (28d fertilize / 0d mist /
        // 730d repot). Manual-entry plants don't have a botanical family
        // we can look up, so we use GENERIC_FALLBACK directly. Empty/0
        // means "user disabled this reminder type".
        com.example.plantcare.data.plantnet.PlantCareDefaults.CareTexts care =
                com.example.plantcare.data.plantnet.PlantCareDefaults
                        .INSTANCE.getGENERIC_FALLBACK();
        prefillIntervalManual(editIntervalWater, care.getWateringIntervalDays());
        prefillIntervalManual(editIntervalFertilize, care.getFertilizingIntervalDays());
        prefillIntervalManual(editIntervalMist, care.getMistingIntervalDays());
        prefillIntervalManual(editIntervalRepot, care.getRepottingIntervalDays());

        // تهيئة المعاينة: عرض كامل وارتفاع ثابت، مخفية إذا لا توجد صورة
        if (photoUri == null) {
            imagePreview.setVisibility(View.GONE);
        } else {
            applyStableImagePreviewSizing();
            Glide.with(requireContext()).load(photoUri).centerCrop().into(imagePreview);
            imagePreview.setVisibility(View.VISIBLE);
        }

        loadRooms();
        editStartDate.setOnClickListener(v -> showDatePicker());
        buttonAddRoom.setOnClickListener(v -> showAddRoomDialog());
        buttonAdd.setOnClickListener(v -> savePlant());
        buttonClose.setOnClickListener(v -> dismiss());

        buttonCapture.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).capturePhotoForPlant(null, false);
                // بعد الالتقاط/الاختيار، نادِ setPhotoUri(uri) لتحديث المعاينة.
            }
        });

        return new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        // تثبيت عرض نافذة الحوار إلى 92% من عرض الشاشة لمنع الانكماش
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            DisplayMetrics dm = new DisplayMetrics();
            dialog.getWindow().getWindowManager().getDefaultDisplay().getMetrics(dm);
            int targetWidth = (int) (dm.widthPixels * 0.92f);
            dialog.getWindow().setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    /**
     * استدعها من الـ Activity بعد التقاط/اختيار الصورة لتحديث المعاينة بثبات.
     */
    public void setPhotoUri(@Nullable Uri uri) {
        this.photoUri = uri;
        if (!isAdded() || imagePreview == null) return;

        if (uri == null) {
            imagePreview.setImageDrawable(null);
            imagePreview.setVisibility(View.GONE);
            return;
        }

        imagePreview.setVisibility(View.VISIBLE);
        applyStableImagePreviewSizing();

        Glide.with(requireContext())
                .load(uri)
                .centerCrop()
                .into(imagePreview);
    }

    /**
     * يجعل معاينة الصورة بعرض كامل وارتفاع ثابت كي لا تغيّر عرض النافذة.
     */
    private void applyStableImagePreviewSizing() {
        ViewGroup.LayoutParams lp = imagePreview.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        int heightPx = (int) (220 * requireContext().getResources().getDisplayMetrics().density);
        lp.height = heightPx;
        imagePreview.setLayoutParams(lp);
        imagePreview.setAdjustViewBounds(false);
        imagePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    private void loadRooms() {
        loadRooms(null);
    }

    /**
     * `preferredName` lets the AddRoom callback pin the freshly-typed
     * room as the active spinner selection after a refresh — without
     * it the spinner snapped back to index 0 (the first existing room)
     * and the user thought their input was lost.
     */
    private void loadRooms(@Nullable final String preferredName) {
        final String userEmail = getUserEmail();
        // Fall back to the room the user came from (PlantsInRoomActivity
        // stamps `last_used_room_id_<email>` via QuickAddHelper before
        // launching this dialog). Pre-fix the manual-add spinner always
        // landed on the first DB row, so a + tap inside Bad opened with
        // Wohnzimmer pre-selected.
        final int rememberedRoomId = (preferredName == null && userEmail != null)
                ? com.example.plantcare.ui.util.QuickAddHelper
                        .readLastUsedRoom(requireContext().getApplicationContext(), userEmail)
                : 0;
        FragmentBg.<List<RoomCategory>>runWithResult(this,
                () -> com.example.plantcare.data.repository.RoomCategoryRepository
                        .getInstance(requireContext())
                        .getAllRoomsForUserBlocking(userEmail),
                loaded -> {
                    rooms = loaded;
                    List<String> roomNames = new ArrayList<>();
                    int preferredIndex = -1;
                    for (int i = 0; i < rooms.size(); i++) {
                        RoomCategory room = rooms.get(i);
                        roomNames.add(room.name);
                        if (preferredName != null && preferredName.equals(room.name)) {
                            preferredIndex = i;
                        } else if (preferredName == null && rememberedRoomId > 0
                                && room.id == rememberedRoomId) {
                            preferredIndex = i;
                        }
                    }
                    roomNames.add(getString(R.string.spinner_add_new_room));
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, roomNames);
                    spinnerRooms.setAdapter(adapter);

                    spinnerRooms.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (position == rooms.size()) {
                                spinnerRooms.setSelection(0);
                                showAddRoomDialog();
                            } else {
                                selectedRoomId = rooms.get(position).id;
                                suggestPlantNameForRoom();
                            }
                        }
                        @Override public void onNothingSelected(AdapterView<?> parent) {}
                    });

                    // Apply the preferred selection AFTER the listener is wired
                    // so suggestPlantNameForRoom fires and sets selectedRoomId.
                    if (preferredIndex >= 0) {
                        spinnerRooms.setSelection(preferredIndex);
                    }
                });
    }

    private void suggestPlantNameForRoom() {
        final String userEmail = getUserEmail();
        final int roomId = selectedRoomId;
        FragmentBg.<Integer>runWithResult(this,
                () -> com.example.plantcare.data.repository.PlantRepository
                        .getInstance(requireContext())
                        .countPlantsByRoomBlocking(roomId, userEmail),
                count -> {
                    String baseName = getString(R.string.default_plant_base);
                    // Locale.US: suggestion may be saved verbatim as plant name in DB.
                    // ar/fa Locale.getDefault() would produce Eastern-Arabic digits in the row.
                    String suggestion = baseName + " " + String.format(Locale.US, "%02d", count + 1);
                    editPlantName.setText(suggestion);
                });
    }

    private void showAddRoomDialog() {
        AddRoomDialogFragment dialog = new AddRoomDialogFragment();
        dialog.setOnRoomAddedListener(roomName -> {
            if (TextUtils.isEmpty(roomName)) return;
            final String name = roomName.trim();
            final String email = getUserEmail();
            final android.content.Context appCtx = requireContext().getApplicationContext();
            FragmentBg.runIO(this, () -> {
                com.example.plantcare.data.repository.RoomCategoryRepository repo =
                        com.example.plantcare.data.repository.RoomCategoryRepository.getInstance(appCtx);
                // Skip case-insensitive duplicate so retries don't pile up.
                // If a same-name room already exists we still want to pin it
                // as the active selection so the user lands on what they meant.
                List<RoomCategory> existing = repo.getAllRoomsForUserBlocking(email);
                if (existing != null) {
                    for (RoomCategory r : existing) {
                        if (r.name != null && r.name.equalsIgnoreCase(name)) {
                            loadRooms(r.name);
                            return;
                        }
                    }
                }
                RoomCategory room = new RoomCategory();
                room.name = name;
                room.userEmail = email;
                long newId = repo.insertBlocking(room);
                room.id = (int) newId;
                try { FirebaseSyncManager.get().syncRoom(room); }
                catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                loadRooms(name);
            });
        });
        dialog.show(getParentFragmentManager(), "add_room_dialog");
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(
                new android.view.ContextThemeWrapper(requireContext(), R.style.PlantCareDatePicker),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    selectedDate = calendar.getTime();
                    editStartDate.setText(android.text.format.DateFormat.format("yyyy-MM-dd", selectedDate));
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        // Bound to ±1 year — same reasoning as AddToMyPlantsDialogFragment.
        Calendar min = Calendar.getInstance();
        min.add(Calendar.YEAR, -1);
        Calendar max = Calendar.getInstance();
        max.add(Calendar.YEAR, 1);
        dialog.getDatePicker().setMinDate(min.getTimeInMillis());
        dialog.getDatePicker().setMaxDate(max.getTimeInMillis());
        dialog.show();
    }

    private void savePlant() {
        String name = editPlantName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            editPlantName.setError(getString(R.string.add_plant_name_required));
            return;
        }
        if (selectedRoomId == -1) {
            Toast.makeText(requireContext(), R.string.add_plant_select_room, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedDate == null) {
            Toast.makeText(requireContext(), R.string.add_plant_select_date, Toast.LENGTH_SHORT).show();
            return;
        }
        // v16: snapshot the four interval values on the main thread —
        // EditText reads from a worker thread crash on some Android
        // versions. 0 = user disabled that reminder type.
        final int waterIvUser = readIntervalManual(editIntervalWater, 0);
        final int fertIvUser  = readIntervalManual(editIntervalFertilize, 0);
        final int mistIvUser  = readIntervalManual(editIntervalMist, 0);
        final int repotIvUser = readIntervalManual(editIntervalRepot, 0);
        final String userEmail = getUserEmail();
        final String imgUri = photoUri != null ? photoUri.toString() : null;
        final android.content.Context appCtx = requireContext().getApplicationContext();
        FragmentBg.<Boolean>runWithResult(this,
                () -> {
                    com.example.plantcare.data.repository.PlantRepository plantRepo =
                            com.example.plantcare.data.repository.PlantRepository.getInstance(appCtx);
                    com.example.plantcare.data.repository.ReminderRepository reminderRepo =
                            com.example.plantcare.data.repository.ReminderRepository.getInstance(appCtx);

                    if (!com.example.plantcare.billing.ProStatusManager.isPro(appCtx)) {
                        int currentCount = plantRepo.countUserPlantsBlocking(userEmail);
                        if (currentCount >= com.example.plantcare.billing.ProStatusManager.FREE_PLANT_LIMIT) {
                            return Boolean.FALSE;
                        }
                    }
                    Plant plant = new Plant();
                    plant.name = name;
                    plant.nickname = name;
                    plant.roomId = selectedRoomId;
                    plant.lighting = editLighting.getText().toString();
                    plant.soil = editSoil.getText().toString();
                    plant.fertilizing = editFertilizing.getText().toString();
                    plant.watering = editWatering.getText().toString();
                    plant.startDate = selectedDate;
                    plant.isUserPlant = true;
                    plant.userEmail = userEmail;
                    plant.imageUri = imgUri;
                    // v16: user-set interval (from the dialog field) takes
                    // priority. Fall through to text-parse → family-default
                    // → 5d hardcoded fallback for water; 0 stays 0 for the
                    // other three types so the user's "off" stays off.
                    int parsed = ReminderUtils.parseWateringInterval(plant.watering);
                    plant.wateringInterval = waterIvUser > 0
                            ? waterIvUser
                            : (parsed > 0 ? parsed : 5);
                    plant.fertilizingInterval = fertIvUser;
                    plant.mistingInterval     = mistIvUser;
                    plant.repottingIntervalDays = repotIvUser;

                    long id = plantRepo.insertBlocking(plant);
                    plant.setId((int) id);

                    try {
                        FirebaseSyncManager.get().syncPlant(plant);
                    } catch (Throwable t) { CrashReporter.INSTANCE.log(t); }

                    java.util.List<WateringReminder> reminders = ReminderUtils.generateAllReminders(plant);
                    if (reminders != null) {
                        for (WateringReminder r : reminders) {
                            r.userEmail = userEmail;
                            reminderRepo.insertBlocking(r);
                            try {
                                FirebaseSyncManager.get().syncReminder(r);
                            } catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                        }
                    }

                    com.example.plantcare.ui.util.QuickAddHelper
                            .rememberLastUsedRoom(appCtx, userEmail, selectedRoomId);
                    return Boolean.TRUE;
                },
                inserted -> {
                    if (Boolean.FALSE.equals(inserted)) {
                        new com.example.plantcare.billing.PaywallDialogFragment().show(
                                getParentFragmentManager(),
                                com.example.plantcare.billing.PaywallDialogFragment.TAG);
                        return;
                    }
                    Analytics.INSTANCE.logPlantAdded(appCtx);
                    DataChangeNotifier.notifyChange();
                    Toast.makeText(requireContext(), R.string.plant_added, Toast.LENGTH_SHORT).show();
                    if (onPlantAdded != null) onPlantAdded.run();
                    dismiss();
                });
    }

    private String getUserEmail() {
        return EmailContext.current(requireContext());
    }

    /** v16 helper — write an int to an EditText, leaving it blank when 0. */
    private static void prefillIntervalManual(EditText field, int value) {
        if (field == null) return;
        field.setText(value > 0 ? String.valueOf(value) : "");
    }

    /** v16 helper — read an int from an EditText, returning fallback on blank/invalid. */
    private static int readIntervalManual(EditText field, int fallback) {
        if (field == null) return fallback;
        try {
            String s = field.getText().toString().trim();
            if (s.isEmpty()) return 0; // explicitly disabled
            int v = Integer.parseInt(s);
            return v >= 0 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
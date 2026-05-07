package com.example.plantcare;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.plantcare.ui.util.FragmentBg;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AddToMyPlantsDialogFragment extends DialogFragment {

    private Plant plant;

    private EditText editName;
    private MaterialAutoCompleteTextView spinnerRooms;
    private MaterialButton btnAddRoom;
    private MaterialButton btnClose;
    private MaterialButton btnNext;

    private final List<RoomCategory> rooms = new ArrayList<>();
    private int selectedRoomId = 0;

    private Runnable onPlantAdded;

    private String cachedUserEmail = null;
    private boolean guestMode = false;

    public static AddToMyPlantsDialogFragment newInstance(Plant p) {
        AddToMyPlantsDialogFragment f = new AddToMyPlantsDialogFragment();
        Bundle b = new Bundle();
        b.putSerializable("plant", p);
        f.setArguments(b);
        return f;
    }

    public void setOnPlantAdded(@Nullable Runnable listener) {
        this.onPlantAdded = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_to_my_plants, null);

        if (getArguments() != null) {
            plant = (Plant) getArguments().getSerializable("plant");
        }

        editName = v.findViewById(R.id.editSuggestedName);
        spinnerRooms = v.findViewById(R.id.spinnerRooms);
        btnAddRoom = v.findViewById(R.id.buttonAddRoom);
        btnClose = v.findViewById(R.id.buttonClose);
        btnNext = v.findViewById(R.id.buttonNext);

        cachedUserEmail = EmailContext.current(requireContext());
        guestMode = (cachedUserEmail == null);

        String fallback = (plant != null && !TextUtils.isEmpty(plant.name))
                ? plant.name
                : getString(R.string.default_plant_base);
        // Initial draft — "<species> 1" while we resolve the real next index
        // off the main thread. Replaced below with "<species> N" where N is
        // count(existing siblings)+1 so a 6th Einblatt becomes "Einblatt 6"
        // instead of always "Einblatt 1".
        editName.setText(fallback + " 1");
        if (!guestMode) suggestNicknameForPlant(fallback);

        if (guestMode) {
            populateSpinnerWithRoomNames(com.example.plantcare.ui.util.DefaultRooms.get(requireContext()));
        } else {
            ensureDefaultsThenLoadRooms();
        }

        btnAddRoom.setOnClickListener(view -> {
            AddRoomDialogFragment dialog = new AddRoomDialogFragment();
            dialog.setOnRoomAddedListener(roomName -> {
                if (TextUtils.isEmpty(roomName)) return;

                if (guestMode) {
                    List<String> current = getSpinnerCurrentItems();
                    if (!current.contains(roomName)) {
                        current.add(roomName);
                        populateSpinnerWithRoomNames(current);
                    }
                } else {
                    final Context appCtx = requireContext().getApplicationContext();
                    FragmentBg.runIO(this, () -> {
                        RoomCategory rc = new RoomCategory();
                        rc.name = roomName;
                        rc.userEmail = cachedUserEmail;
                        long newId = com.example.plantcare.data.repository.RoomCategoryRepository
                                .getInstance(appCtx).insertBlocking(rc);
                        rc.id = (int) newId;
                        try { FirebaseSyncManager.get().syncRoom(rc); }
                        catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                        reloadRooms();
                    });
                }
            });
            dialog.show(getParentFragmentManager(), "add_room_dialog");
        });

        btnClose.setOnClickListener(view -> dismissAllowingStateLoss());

        btnNext.setOnClickListener(view -> {
            if (guestMode) {
                Toast.makeText(requireContext(), R.string.quick_add_needs_login, Toast.LENGTH_SHORT).show();
                AuthStartDialogFragment auth = new AuthStartDialogFragment();
                auth.show(getParentFragmentManager(), "auth_start");
            } else {
                openDatePickerAndAddPlant();
            }
        });

        Dialog dialog = new Dialog(requireContext(), R.style.Dialog_Modern);
        dialog.setContentView(v);
        dialog.setCancelable(true);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
            int target = (int) (dm.widthPixels * 0.92f);
            dialog.getWindow().setLayout(target, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void populateSpinnerWithRoomNames(List<String> roomNames) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, roomNames);
        spinnerRooms.setAdapter(adapter);
        if (!roomNames.isEmpty()) {
            spinnerRooms.setText(roomNames.get(0), false);
            selectedRoomId = resolveRoomIdFromName(roomNames.get(0));
        }
        spinnerRooms.setOnItemClickListener((parent, view, position, id) -> {
            String name = (String) parent.getItemAtPosition(position);
            selectedRoomId = resolveRoomIdFromName(name);
        });
    }

    private List<String> getSpinnerCurrentItems() {
        List<String> list = new ArrayList<>();
        ArrayAdapter<?> adapter = (ArrayAdapter<?>) spinnerRooms.getAdapter();
        if (adapter != null) {
            for (int i = 0; i < adapter.getCount(); i++) {
                Object item = adapter.getItem(i);
                if (item != null) list.add(String.valueOf(item));
            }
        }
        return list;
    }

    private int resolveRoomIdFromName(String name) {
        if (TextUtils.isEmpty(name)) return 0;
        for (RoomCategory r : rooms) {
            if (name.equals(r.name)) return r.id;
        }
        return 0;
    }

    private void suggestNicknameForPlant(String baseName) {
        if (TextUtils.isEmpty(cachedUserEmail)) return;
        final Context appCtx = requireContext().getApplicationContext();
        final String email = cachedUserEmail;
        FragmentBg.<Integer>runWithResult(this,
                () -> {
                    java.util.List<Plant> existing = com.example.plantcare.data.repository
                            .PlantRepository.getInstance(appCtx)
                            .getAllUserPlantsWithNameAndUserBlocking(baseName, email);
                    return (existing != null ? existing.size() : 0) + 1;
                },
                next -> {
                    if (next == null || editName == null) return;
                    String suggestion = baseName + " " + next;
                    // Don't clobber a value the user has already started typing.
                    String current = editName.getText() != null ? editName.getText().toString() : "";
                    if (current.isEmpty() || current.equals(baseName + " 1")) {
                        editName.setText(suggestion);
                    }
                });
    }

    private void ensureDefaultsThenLoadRooms() {
        final Context appCtx = requireContext().getApplicationContext();
        FragmentBg.runIO(this, () -> {
            // Sprint-3 cleanup: delegate to the synchronized Repo helper so a
            // second simultaneous open of this dialog can't insert duplicate
            // default rooms.
            com.example.plantcare.data.repository.RoomCategoryRepository
                    .getInstance(appCtx)
                    .ensureDefaultsForUserBlocking(cachedUserEmail,
                            com.example.plantcare.ui.util.DefaultRooms.get(appCtx));
            reloadRooms();
        });
    }

    private void reloadRooms() {
        final Context appCtx = requireContext().getApplicationContext();
        FragmentBg.<List<RoomCategory>>runWithResult(this,
                () -> com.example.plantcare.data.repository.RoomCategoryRepository
                        .getInstance(appCtx)
                        .getAllRoomsForUserBlocking(cachedUserEmail),
                loaded -> {
                    rooms.clear();
                    if (loaded != null) rooms.addAll(loaded);
                    List<String> names = new ArrayList<>();
                    for (RoomCategory r : rooms) names.add(r.name);
                    if (names.isEmpty()) {
                        names.addAll(com.example.plantcare.ui.util.DefaultRooms.get(requireContext()));
                    }
                    populateSpinnerWithRoomNames(names);
                });
    }

    private void openDatePickerAndAddPlant() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog picker = new DatePickerDialog(
                new android.view.ContextThemeWrapper(requireContext(), R.style.PlantCareDatePicker),
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, dayOfMonth);
                    Date startDate = selected.getTime();

                    final String nicknameInput = editName.getText().toString().trim();
                    final String nickname = TextUtils.isEmpty(nicknameInput)
                            ? (plant != null ? plant.name : "Pflanze")
                            : nicknameInput;

                    final int roomId = (selectedRoomId > 0)
                            ? selectedRoomId
                            : resolveRoomIdFromName(spinnerRooms.getText() != null ? spinnerRooms.getText().toString() : null);

                    final Context appCtx = requireContext().getApplicationContext();
                    final String email = cachedUserEmail;

                    if (!com.example.plantcare.billing.ProStatusManager.isPro(appCtx)) {
                        FragmentBg.<Integer>runWithResult(this,
                                () -> com.example.plantcare.data.repository.PlantRepository
                                        .getInstance(appCtx).countUserPlantsBlocking(email),
                                count -> {
                                    if (count != null && count >= com.example.plantcare.billing.ProStatusManager.FREE_PLANT_LIMIT) {
                                        new com.example.plantcare.billing.PaywallDialogFragment().show(
                                                getParentFragmentManager(),
                                                com.example.plantcare.billing.PaywallDialogFragment.TAG);
                                    } else {
                                        actuallyAddPlant(appCtx, email, nickname, roomId, startDate);
                                    }
                                });
                        return;
                    }
                    actuallyAddPlant(appCtx, email, nickname, roomId, startDate);
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        applyPlantStartDateBounds(picker);
        picker.show();
    }

    /**
     * Reminders generate forward from the plant's startDate, so picking a
     * far-past date floods the calendar with backfilled rows and a far-future
     * date silences the plant for years. Clamp the picker to ±1 year — covers
     * "I bought this plant a few months ago" without inviting typos like 1900.
     */
    private static void applyPlantStartDateBounds(DatePickerDialog picker) {
        Calendar min = Calendar.getInstance();
        min.add(Calendar.YEAR, -1);
        Calendar max = Calendar.getInstance();
        max.add(Calendar.YEAR, 1);
        picker.getDatePicker().setMinDate(min.getTimeInMillis());
        picker.getDatePicker().setMaxDate(max.getTimeInMillis());
    }

    private void actuallyAddPlant(Context appCtx, String email, String nickname, int roomId, Date startDate) {
                    FragmentBg.runIO(this,
                            () -> {
                                com.example.plantcare.data.repository.PlantRepository plantRepo =
                                        com.example.plantcare.data.repository.PlantRepository.getInstance(appCtx);
                                com.example.plantcare.data.repository.ReminderRepository reminderRepo =
                                        com.example.plantcare.data.repository.ReminderRepository.getInstance(appCtx);

                                Plant newPlant = new Plant();
                                if (plant != null) {
                                    newPlant.name = plant.name;
                                    newPlant.lighting = plant.lighting;
                                    newPlant.soil = plant.soil;
                                    newPlant.fertilizing = plant.fertilizing;
                                    newPlant.watering = plant.watering;
                                    newPlant.imageUri = plant.imageUri;
                                    // wichtig für PlantNet-Flow: Wikipedia-Beschreibung (falls gesetzt)
                                    // muss mitgezogen werden, sonst landet die erkannte Pflanze
                                    // weiterhin ohne Infotext in "Meine Pflanzen".
                                    newPlant.personalNote = plant.personalNote;
                                    // Kategorie vom Katalog-Eintrag übernehmen; falls leer (z. B.
                                    // PlantNet-Flow ohne Match), heuristisch ableiten.
                                    newPlant.category = (plant.category != null && !plant.category.isEmpty())
                                            ? plant.category
                                            : com.example.plantcare.ui.util.PlantCategoryUtil
                                                    .classify(plant.name, plant.lighting, plant.watering);
                                }
                                newPlant.nickname = nickname;
                                newPlant.isUserPlant = true;
                                newPlant.startDate = startDate;
                                newPlant.userEmail = email;
                                newPlant.roomId = roomId;

                                // Reihenfolge: Draft (PlantNet hat Familien-Default schon
                                // gesetzt) → Text-Parsing → Hardcoded-Fallback. Vor F5 wurde
                                // der Draft-Wert ignoriert und alle PlantNet-Pflanzen landeten
                                // bei 5 Tagen (Functional Report §1.4).
                                int interval = newPlant.wateringInterval > 0
                                        ? newPlant.wateringInterval
                                        : ReminderUtils.parseWateringInterval(newPlant.watering);
                                newPlant.wateringInterval = interval > 0 ? interval : 5;

                                long id = plantRepo.insertBlocking(newPlant);
                                newPlant.setId((int) id);

                                try {
                                    FirebaseSyncManager.get().syncPlant(newPlant);
                                } catch (Throwable e) { CrashReporter.INSTANCE.log(e); }

                                List<WateringReminder> reminders = ReminderUtils.generateReminders(newPlant);
                                for (WateringReminder r : reminders) {
                                    r.userEmail = email;
                                    reminderRepo.insertBlocking(r);
                                    try {
                                        FirebaseSyncManager.get().syncReminder(r);
                                    } catch (Throwable e) { CrashReporter.INSTANCE.log(e); }
                                }
                            },
                            () -> {
                                com.example.plantcare.ui.util.QuickAddHelper
                                        .rememberLastUsedRoom(appCtx, email, roomId);
                                Analytics.INSTANCE.logPlantAdded(appCtx);
                                DataChangeNotifier.notifyChange();
                                if (onPlantAdded != null) onPlantAdded.run();
                                dismissAllowingStateLoss();
                            });
    }
}
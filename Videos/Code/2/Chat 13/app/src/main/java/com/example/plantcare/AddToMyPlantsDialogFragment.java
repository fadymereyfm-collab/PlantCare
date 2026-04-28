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

    private static final String[] DEFAULT_ROOMS = {
            "Wohnzimmer", "Schlafzimmer", "Flur", "Bad", "Toilette"
    };

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

        String fallback = (plant != null && !TextUtils.isEmpty(plant.name)) ? plant.name : "Pflanze";
        editName.setText(fallback + " 1");

        if (guestMode) {
            populateSpinnerWithRoomNames(Arrays.asList(DEFAULT_ROOMS));
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
                        AppDatabase db = AppDatabase.getInstance(appCtx);
                        RoomCategory rc = new RoomCategory();
                        rc.name = roomName;
                        rc.userEmail = cachedUserEmail;
                        db.roomCategoryDao().insert(rc);
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

    private void ensureDefaultsThenLoadRooms() {
        final Context appCtx = requireContext().getApplicationContext();
        FragmentBg.runIO(this, () -> {
            AppDatabase db = AppDatabase.getInstance(appCtx);
            db.runInTransaction(() -> {
                List<RoomCategory> current = db.roomCategoryDao().getAllRoomsForUser(cachedUserEmail);
                Set<String> existing = new HashSet<>();
                if (current != null) {
                    for (RoomCategory r : current) existing.add(r.name);
                }
                for (String def : DEFAULT_ROOMS) {
                    if (!existing.contains(def)) {
                        RoomCategory rc = new RoomCategory();
                        rc.name = def;
                        rc.userEmail = cachedUserEmail;
                        db.roomCategoryDao().insert(rc);
                    }
                }
            });
            reloadRooms();
        });
    }

    private void reloadRooms() {
        final Context appCtx = requireContext().getApplicationContext();
        FragmentBg.<List<RoomCategory>>runWithResult(this,
                () -> AppDatabase.getInstance(appCtx)
                        .roomCategoryDao()
                        .getAllRoomsForUser(cachedUserEmail),
                loaded -> {
                    rooms.clear();
                    if (loaded != null) rooms.addAll(loaded);
                    List<String> names = new ArrayList<>();
                    for (RoomCategory r : rooms) names.add(r.name);
                    if (names.isEmpty()) names.addAll(Arrays.asList(DEFAULT_ROOMS));
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
                                () -> AppDatabase.getInstance(appCtx).plantDao().countUserPlants(email),
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
        picker.show();
    }

    private void actuallyAddPlant(Context appCtx, String email, String nickname, int roomId, Date startDate) {
                    FragmentBg.runIO(this,
                            () -> {
                                AppDatabase db = AppDatabase.getInstance(appCtx);

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

                                int interval = ReminderUtils.parseWateringInterval(newPlant.watering);
                                newPlant.wateringInterval = interval > 0 ? interval : 5;

                                long id = db.plantDao().insert(newPlant);
                                newPlant.setId((int) id);

                                try {
                                    FirebaseSyncManager.get().syncPlant(newPlant);
                                } catch (Throwable e) { CrashReporter.INSTANCE.log(e); }

                                List<WateringReminder> reminders = ReminderUtils.generateReminders(newPlant);
                                for (WateringReminder r : reminders) {
                                    r.userEmail = email;
                                    db.reminderDao().insert(r);
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
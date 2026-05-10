package com.example.plantcare;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.plantcare.ui.util.FragmentBg;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EditPlantDialogFragment extends DialogFragment {

    private static final String ARG_PLANT = "arg_plant";
    private static final String ARG_SHOW_NOTE = "arg_show_note";
    private Plant plant;
    private boolean showPersonalNote = true;

    private Runnable onPlantEdited; // جديد: متغير للاستماع لنجاح التعديل

    private EditText nameEditText, lightingEditText, soilEditText, fertilizingEditText, wateringEditText, personalNoteEditText;
    // v16: per-care-type interval inputs.
    private EditText editIntervalWater, editIntervalFertilize, editIntervalMist, editIntervalRepot;

    public static EditPlantDialogFragment newInstance(Plant plant, boolean showPersonalNote) {
        EditPlantDialogFragment fragment = new EditPlantDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PLANT, plant);
        args.putBoolean(ARG_SHOW_NOTE, showPersonalNote);
        fragment.setArguments(args);
        return fragment;
    }

    // جديد: دالة تعيين الاستماع للتعديل
    public void setOnPlantEdited(Runnable listener) {
        this.onPlantEdited = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            plant = (Plant) getArguments().getSerializable(ARG_PLANT);
            showPersonalNote = getArguments().getBoolean(ARG_SHOW_NOTE, true);
        }
        if (showPersonalNote) {
            return inflater.inflate(R.layout.dialog_edit_plant, container, false);
        } else {
            return inflater.inflate(R.layout.dialog_edit_plant_no_note, container, false);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        nameEditText = view.findViewById(R.id.editPlantName);
        lightingEditText = view.findViewById(R.id.editLighting);
        soilEditText = view.findViewById(R.id.editSoil);
        fertilizingEditText = view.findViewById(R.id.editFertilizing);
        wateringEditText = view.findViewById(R.id.editWatering);
        personalNoteEditText = view.findViewById(R.id.editPersonalNote);

        editIntervalWater     = view.findViewById(R.id.editIntervalWater);
        editIntervalFertilize = view.findViewById(R.id.editIntervalFertilize);
        editIntervalMist      = view.findViewById(R.id.editIntervalMist);
        editIntervalRepot     = view.findViewById(R.id.editIntervalRepot);

        if (plant != null) {
            if (plant.getName() != null) nameEditText.setText(plant.getName());
            if (plant.getLighting() != null) lightingEditText.setText(plant.getLighting());
            if (plant.getSoil() != null) soilEditText.setText(plant.getSoil());
            if (plant.getFertilizing() != null) fertilizingEditText.setText(plant.getFertilizing());
            if (plant.getWatering() != null) wateringEditText.setText(plant.getWatering());
            if (personalNoteEditText != null && plant.getPersonalNote() != null) personalNoteEditText.setText(plant.getPersonalNote());

            // v16: pre-fill the four interval inputs from the plant's stored
            // values. 0 stays blank so the user sees "this type is off"
            // explicitly rather than a confusing 0 in the field.
            prefillInterval(editIntervalWater, plant.wateringInterval);
            prefillInterval(editIntervalFertilize, plant.fertilizingInterval);
            prefillInterval(editIntervalMist, plant.mistingInterval);
            prefillInterval(editIntervalRepot, plant.repottingIntervalDays);
        }

        view.findViewById(R.id.saveButton).setOnClickListener(v -> saveChanges());
        view.findViewById(R.id.cancelButton).setOnClickListener(v -> dismiss());
    }

    /** v16 helper — write an int to an EditText, leaving it blank when 0. */
    private static void prefillInterval(EditText field, int value) {
        if (field == null) return;
        field.setText(value > 0 ? String.valueOf(value) : "");
    }

    /** v16 helper — read an int from an EditText, defaulting to fallback when blank/invalid. */
    private static int readInterval(EditText field, int fallback) {
        if (field == null) return fallback;
        try {
            String s = field.getText().toString().trim();
            if (s.isEmpty()) return 0;  // explicitly disabled
            int v = Integer.parseInt(s);
            return v >= 0 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.WRAP_CONTENT;
            getDialog().getWindow().setLayout(width, height);
        }
    }

    private void saveChanges() {
        String name = nameEditText.getText().toString().trim();
        String lighting = lightingEditText.getText().toString().trim();
        String soil = soilEditText.getText().toString().trim();
        String fertilizing = fertilizingEditText.getText().toString().trim();
        String watering = wateringEditText.getText().toString().trim();
        String personalNote = "";
        if (personalNoteEditText != null) {
            personalNote = personalNoteEditText.getText().toString().trim();
        }

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(getContext(), R.string.settings_enter_name, Toast.LENGTH_SHORT).show();
            return;
        }

        plant.setName(name);
        plant.setLighting(lighting);
        plant.setSoil(soil);
        plant.setFertilizing(fertilizing);
        plant.setWatering(watering);
        if (personalNoteEditText != null) {
            plant.setPersonalNote(personalNote);
        }

        // v16: read the four interval fields on the main thread BEFORE
        // handing off to the IO worker — EditText access from a worker
        // thread crashes on some Android versions. 0 means "user disabled
        // this reminder type" — the next regeneration will skip it.
        // We snapshot the OLD values so we can detect "did the schedule
        // actually change?" — only regenerate reminders when something
        // changed, otherwise a no-op edit (e.g. fixing a typo in the
        // lighting text) wouldn't wipe the user's wash-shifted dates.
        final int oldWaterIv = plant.wateringInterval;
        final int oldFertIv  = plant.fertilizingInterval;
        final int oldMistIv  = plant.mistingInterval;
        final int oldRepotIv = plant.repottingIntervalDays;

        // Watering interval: text-parse takes priority over the dialog
        // field so a user editing the watering description ("alle 14 Tage"
        // → "alle 7 Tage") still gets the new interval applied.
        int parsedFromText = TextUtils.isEmpty(watering)
                ? 0
                : ReminderUtils.parseWateringInterval(watering);
        final int newWaterIv = parsedFromText > 0
                ? parsedFromText
                : readInterval(editIntervalWater, oldWaterIv);
        final int newFertIv  = readInterval(editIntervalFertilize, oldFertIv);
        final int newMistIv  = readInterval(editIntervalMist, oldMistIv);
        final int newRepotIv = readInterval(editIntervalRepot, oldRepotIv);

        plant.setWateringInterval(newWaterIv);
        plant.fertilizingInterval = newFertIv;
        plant.mistingInterval     = newMistIv;
        plant.repottingIntervalDays = newRepotIv;

        final boolean scheduleChanged =
                oldWaterIv != newWaterIv
             || oldFertIv  != newFertIv
             || oldMistIv  != newMistIv
             || oldRepotIv != newRepotIv;

        FragmentBg.runIO(this,
                () -> {
                    com.example.plantcare.data.repository.PlantRepository plantRepo =
                            com.example.plantcare.data.repository.PlantRepository
                                    .getInstance(requireContext());
                    com.example.plantcare.data.repository.ReminderRepository reminderRepo =
                            com.example.plantcare.data.repository.ReminderRepository
                                    .getInstance(requireContext());

                    // Persist the in-memory edits on this plant only. The
                    // previous implementation cascaded personalNote across
                    // every sibling sharing the same name/nickname, which
                    // silently clobbered per-instance notes whenever a user
                    // had two of the same species (e.g. two Pothos in
                    // different rooms).
                    plantRepo.updateBlocking(plant);

                    // v16: regenerate ALL four reminder series when the
                    // schedule changed. Pre-v16 only the watering series
                    // was rebuilt — fertilize/mist/repot kept their stale
                    // dates after an interval edit. Now we drop every
                    // future auto reminder for this plant and let
                    // generateAllReminders rebuild from today.
                    List<WateringReminder> newReminders = null;
                    if (scheduleChanged) {
                        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                        reminderRepo.deleteFutureRemindersForPlantBlocking(plant.id, today);
                        newReminders = ReminderUtils.generateAllReminders(plant);
                        if (newReminders != null) reminderRepo.insertAllBlocking(newReminders);
                    }

                    // Mirror the edits + new reminder schedule to Firebase.
                    // Best-effort: Firestore writes don't block the UI ack
                    // (they queue offline if the device is disconnected),
                    // and `try/catch` shields us from unexpected SDK errors
                    // so a sync glitch doesn't undo a successful local save.
                    if (plant.isUserPlant()) {
                        try {
                            FirebaseSyncManager.get().syncPlant(plant);
                        } catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                        if (newReminders != null) {
                            // Drop the old reminder docs before pushing new ones
                            // so a shorter interval doesn't leave stale rows.
                            try {
                                FirebaseSyncManager.get().deleteRemindersForPlant(
                                        plant.getUserEmail(), plant.id);
                            } catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                            for (WateringReminder r : newReminders) {
                                try {
                                    FirebaseSyncManager.get().syncReminder(r);
                                } catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                            }
                        }
                    }
                },
                () -> {
                    Toast.makeText(getContext(), R.string.saved, Toast.LENGTH_SHORT).show();
                    DataChangeNotifier.notifyChange();
                    if (onPlantEdited != null) onPlantEdited.run();
                    dismiss();
                });
    }
}
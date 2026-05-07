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

        if (plant != null) {
            if (plant.getName() != null) nameEditText.setText(plant.getName());
            if (plant.getLighting() != null) lightingEditText.setText(plant.getLighting());
            if (plant.getSoil() != null) soilEditText.setText(plant.getSoil());
            if (plant.getFertilizing() != null) fertilizingEditText.setText(plant.getFertilizing());
            if (plant.getWatering() != null) wateringEditText.setText(plant.getWatering());
            if (personalNoteEditText != null && plant.getPersonalNote() != null) personalNoteEditText.setText(plant.getPersonalNote());
        }

        view.findViewById(R.id.saveButton).setOnClickListener(v -> saveChanges());
        view.findViewById(R.id.cancelButton).setOnClickListener(v -> dismiss());
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

                    // Reschedule reminders only when the watering text was
                    // actually populated and yields a valid interval.
                    List<WateringReminder> newReminders = null;
                    if (!TextUtils.isEmpty(watering)) {
                        int newInterval = ReminderUtils.parseWateringInterval(watering);
                        if (newInterval > 0) {
                            plant.setWateringInterval(newInterval);
                            plantRepo.updateBlocking(plant);

                            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                            reminderRepo.deleteFutureRemindersForPlantBlocking(plant.id, today);

                            newReminders = ReminderUtils.generateReminders(plant);
                            if (newReminders != null) reminderRepo.insertAllBlocking(newReminders);
                        }
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
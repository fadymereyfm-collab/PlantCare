package com.example.plantcare;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.plantcare.ui.util.FragmentBg;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class AddReminderDialogFragment extends DialogFragment {

    private Spinner plantSpinner, repeatSpinner;
    private EditText dateEditText, descriptionEditText, titleEditText, customDaysEditText;
    private EditText editEndDate;
    private CheckBox checkNoEndDate;
    private Button photoButton, buttonConfirm;
    private String selectedDate = "";
    private Plant selectedPlant;

    private String userEmail;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_add_reminder, container, false);

        plantSpinner = view.findViewById(R.id.plantSpinner);
        repeatSpinner = view.findViewById(R.id.repeatSpinner);
        dateEditText = view.findViewById(R.id.dateEditText);
        descriptionEditText = view.findViewById(R.id.descriptionEditText);
        titleEditText = view.findViewById(R.id.titleEditText);
        customDaysEditText = view.findViewById(R.id.editCustomDays);
        photoButton = view.findViewById(R.id.buttonPhoto);
        buttonConfirm = view.findViewById(R.id.buttonConfirmAdd);

        editEndDate = view.findViewById(R.id.editEndDate);
        checkNoEndDate = view.findViewById(R.id.checkNoEndDate);

        userEmail = EmailContext.current(requireContext());

        dateEditText.setOnClickListener(v -> showDatePicker(dateEditText));
        editEndDate.setFocusable(false);
        editEndDate.setClickable(true);
        editEndDate.setOnClickListener(v -> showDatePicker(editEndDate));

        FragmentBg.<List<Plant>>runWithResult(this,
                () -> {
                    List<Plant> plants = com.example.plantcare.data.repository.PlantRepository
                            .getInstance(requireContext())
                            .getAllUserPlantsForUserBlocking(userEmail);
                    Plant generalPlant = new Plant();
                    generalPlant.setId(0);
                    generalPlant.setName("Allgemein");
                    plants.add(0, generalPlant);
                    return plants;
                },
                plants -> {
                    ArrayAdapter<Plant> adapter = new ArrayAdapter<>(
                            requireContext(), android.R.layout.simple_spinner_item, plants);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    plantSpinner.setAdapter(adapter);
                    if (!plants.isEmpty()) selectedPlant = plants.get(0);
                });

        plantSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) {
                selectedPlant = (Plant) parent.getItemAtPosition(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedPlant = null;
            }
        });

        ArrayAdapter<String> repeatAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"Keine", "Täglich", "Wöchentlich", "Monatlich", "Alle N Tage"});
        repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        repeatSpinner.setAdapter(repeatAdapter);

        repeatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view12, int position, long id) {
                if (position == 4) {
                    customDaysEditText.setVisibility(View.VISIBLE);
                } else {
                    customDaysEditText.setVisibility(View.GONE);
                }
                updateEndDateFields(getRepeatDays());
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                customDaysEditText.setVisibility(View.GONE);
                updateEndDateFields(getRepeatDays());
            }
        });

        customDaysEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) updateEndDateFields(getRepeatDays());
        });
        customDaysEditText.setOnEditorActionListener((v, actionId, event) -> {
            updateEndDateFields(getRepeatDays());
            return false;
        });
        customDaysEditText.setOnKeyListener((v, keyCode, event) -> {
            updateEndDateFields(getRepeatDays());
            return false;
        });

        checkNoEndDate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editEndDate.setVisibility(isChecked ? View.GONE : View.VISIBLE);
        });

        photoButton.setOnClickListener(v -> {
            if (selectedPlant != null && getActivity() instanceof MainActivity activity) {
                String dateToUse = selectedDate.isEmpty()
                        ? new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date())
                        : selectedDate;
                activity.startPhotoForDate(selectedPlant, dateToUse);
            }
        });

        buttonConfirm.setOnClickListener(v -> {
            if (selectedPlant == null || selectedDate.isEmpty()) return;

            String description = descriptionEditText.getText().toString().trim();
            String title = titleEditText.getText().toString().trim();
            int repeatDays = getRepeatDays();

            WateringReminder reminder = new WateringReminder();
            if (selectedPlant.getName() != null && selectedPlant.getName().equals("Allgemein")) {
                reminder.plantName = "Allgemein";
                reminder.plantId = 0;
            } else if (selectedPlant.getNickname() != null && !selectedPlant.getNickname().isEmpty()) {
                reminder.plantName = selectedPlant.getNickname();
                reminder.plantId = selectedPlant.getId();
            } else if (selectedPlant.getName() != null && !selectedPlant.getName().isEmpty()) {
                reminder.plantName = selectedPlant.getName();
                reminder.plantId = selectedPlant.getId();
            } else {
                reminder.plantName = "Unbenannte Pflanze";
                reminder.plantId = selectedPlant.getId();
            }
            reminder.date = selectedDate;
            if (!title.isEmpty() && !description.isEmpty()) {
                reminder.description = title + " - " + description;
            } else if (!title.isEmpty()) {
                reminder.description = title;
            } else {
                reminder.description = description;
            }
            reminder.repeat = String.valueOf(repeatDays);
            reminder.done = false;
            reminder.userEmail = userEmail;

            String endDateStr = editEndDate.getText().toString().trim();
            boolean noEndDate = checkNoEndDate.isChecked();

            final boolean[] failed = { false };
            final boolean[] capHit = { false };
            FragmentBg.runIO(this, () -> {
                try {
                    com.example.plantcare.data.repository.ReminderRepository reminderRepo =
                            com.example.plantcare.data.repository.ReminderRepository
                                    .getInstance(requireContext());
                    if (repeatDays <= 0) {
                        reminderRepo.insertBlocking(reminder);
                        FirebaseSyncManager.get().syncReminder(reminder);
                    } else {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        Date start = sdf.parse(reminder.date);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(start);

                        Calendar maxCal = Calendar.getInstance();
                        maxCal.setTime(cal.getTime());
                        maxCal.add(Calendar.YEAR, 2);

                        Calendar endCal = null;
                        if (!noEndDate && !TextUtils.isEmpty(endDateStr)) {
                            try {
                                endCal = Calendar.getInstance();
                                endCal.setTime(sdf.parse(endDateStr));
                            } catch (ParseException e) {
                                endCal = null;
                            }
                        }

                        // Hard cap: 365 reminders per series. Without it,
                        // repeatDays=1 + 2-year window inflates to 730 rows
                        // and 730 Firestore writes — enough to freeze the
                        // dialog dismissal and burn through the user's
                        // notification budget for the year.
                        final int MAX_REMINDERS = 365;
                        int created = 0;
                        for (int i = 0; i < 365 * 2 && created < MAX_REMINDERS; i += repeatDays) {
                            WateringReminder r = new WateringReminder();
                            r.plantId = reminder.plantId;
                            r.plantName = reminder.plantName;
                            r.date = sdf.format(cal.getTime());
                            r.done = false;
                            r.repeat = reminder.repeat;
                            r.description = reminder.description;
                            r.userEmail = reminder.userEmail;
                            reminderRepo.insertBlocking(r);
                            FirebaseSyncManager.get().syncReminder(r);
                            created++;

                            cal.add(Calendar.DAY_OF_YEAR, repeatDays);

                            if (endCal != null && cal.after(endCal)) break;
                            if (cal.after(maxCal)) break;
                        }
                        // Cap-triggered detection: we hit MAX_REMINDERS
                        // AND we still had room left in the natural
                        // iteration window. That means the user's
                        // "no end date" expectation was silently truncated.
                        if (created >= 365 && (endCal == null || cal.before(endCal))
                                && cal.before(maxCal)) {
                            capHit[0] = true;
                        }
                    }
                } catch (Exception e) {
                    // Was: silent swallow with a "rare conflicts" comment.
                    // The user got NO feedback when the insert failed (e.g.
                    // PK collision on a generated id, parse error on the
                    // start date, schema constraint). Surface it via toast.
                    failed[0] = true;
                    com.example.plantcare.CrashReporter.INSTANCE.log(e);
                }
                if (getActivity() instanceof MainActivity activity) {
                    activity.runOnUiThread(() -> {
                        if (failed[0]) {
                            Toast.makeText(activity, R.string.reminder_save_failed,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (capHit[0]) {
                            // Tell the user their "no end date" series was
                            // silently truncated at 365. They can re-add to
                            // continue further, but the message ensures
                            // they're not surprised by the empty calendar
                            // next year.
                            Toast.makeText(activity, R.string.reminder_count_cap_warning,
                                    Toast.LENGTH_LONG).show();
                        }
                        Analytics.INSTANCE.logReminderAdded(activity);
                        DataChangeNotifier.notifyChange();
                        activity.refreshFragments();
                    });
                }
            });

            dismiss();
        });

        updateEndDateFields(getRepeatDays());
        return view;
    }

    private void showDatePicker(EditText editText) {
        Calendar calendar = Calendar.getInstance();
        if (!TextUtils.isEmpty(editText.getText())) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                calendar.setTime(sdf.parse(editText.getText().toString()));
            } catch (Exception __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
        }
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        new DatePickerDialog(
                new android.view.ContextThemeWrapper(requireContext(), R.style.PlantCareDatePicker),
                (view, y, m, d) -> {
                    String dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                    editText.setText(dateStr);
                    if (editText == dateEditText) selectedDate = dateStr;
                }, year, month, day
        ).show();
    }

    private int getRepeatDays() {
        int pos = repeatSpinner.getSelectedItemPosition();
        if (pos == 1) return 1;
        if (pos == 2) return 7;
        if (pos == 3) return 30;
        if (pos == 4) {
            String daysStr = customDaysEditText.getText().toString().trim();
            int val;
            try { val = Integer.parseInt(daysStr); } catch (NumberFormatException e) { val = 0; }
            return Math.max(0, val);
        }
        return 0;
    }

    private void updateEndDateFields(int repeatDays) {
        boolean show = repeatDays > 0;
        editEndDate.setVisibility(show && !checkNoEndDate.isChecked() ? View.VISIBLE : View.GONE);
        checkNoEndDate.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
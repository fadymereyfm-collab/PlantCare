package com.example.plantcare;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.plantcare.ui.util.FragmentBg;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class EditManualReminderDialogFragment extends DialogFragment {

    private static final String ARG_REMINDER = "arg_reminder";

    private WateringReminder reminder;

    public static EditManualReminderDialogFragment newInstance(WateringReminder reminder) {
        EditManualReminderDialogFragment fragment = new EditManualReminderDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_REMINDER, reminder);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_reminder, null);

        EditText editTitle = view.findViewById(R.id.editReminderTitle);
        EditText editDescription = view.findViewById(R.id.editReminderDescription);
        EditText editDate = view.findViewById(R.id.editReminderDate);
        EditText editRepeat = view.findViewById(R.id.editReminderRepeat);
        TextView textEditAffectsFuture = view.findViewById(R.id.textEditAffectsFuture);
        EditText editEndDate = view.findViewById(R.id.editEndDate);
        CheckBox checkNoEndDate = view.findViewById(R.id.checkNoEndDate);

        Button buttonSave = view.findViewById(R.id.buttonSave);
        Button buttonDelete = view.findViewById(R.id.buttonDelete);
        Button buttonCancel = view.findViewById(R.id.buttonCancel);

        if (getArguments() != null) {
            reminder = (WateringReminder) getArguments().getSerializable(ARG_REMINDER);
        }

        editTitle.setText(reminder.plantName != null ? reminder.plantName : "");
        editDescription.setText(reminder.description != null ? reminder.description : "");
        editDate.setText(reminder.date != null ? reminder.date : "");
        editRepeat.setText(reminder.repeat != null ? reminder.repeat : "");

        editDate.setFocusable(false);
        editDate.setClickable(true);
        editDate.setOnClickListener(v -> {
            final Calendar calendar = Calendar.getInstance();
            if (!TextUtils.isEmpty(editDate.getText())) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    calendar.setTime(sdf.parse(editDate.getText().toString()));
                } catch (Exception __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
            }
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            new DatePickerDialog(
                    new android.view.ContextThemeWrapper(requireContext(), R.style.PlantCareDatePicker),
                    (DatePicker dp, int y, int m, int d) -> {
                        String dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                        editDate.setText(dateStr);
                    }, year, month, day).show();
        });

        editEndDate.setFocusable(false);
        editEndDate.setClickable(true);
        editEndDate.setOnClickListener(v -> {
            final Calendar calendar = Calendar.getInstance();
            if (!TextUtils.isEmpty(editEndDate.getText())) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    calendar.setTime(sdf.parse(editEndDate.getText().toString()));
                } catch (Exception __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
            }
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            new DatePickerDialog(
                    new android.view.ContextThemeWrapper(requireContext(), R.style.PlantCareDatePicker),
                    (DatePicker dp, int y, int m, int d) -> {
                        String dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                        editEndDate.setText(dateStr);
                    }, year, month, day).show();
        });

        editRepeat.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                updateFutureFields(editRepeat, textEditAffectsFuture, editEndDate, checkNoEndDate);
            }
        });
        editRepeat.setOnEditorActionListener((v, actionId, event) -> {
            updateFutureFields(editRepeat, textEditAffectsFuture, editEndDate, checkNoEndDate);
            return false;
        });
        editRepeat.setOnKeyListener((v, keyCode, event) -> {
            updateFutureFields(editRepeat, textEditAffectsFuture, editEndDate, checkNoEndDate);
            return false;
        });

        updateFutureFields(editRepeat, textEditAffectsFuture, editEndDate, checkNoEndDate);

        checkNoEndDate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editEndDate.setVisibility(isChecked ? View.GONE : View.VISIBLE);
        });

        buttonSave.setText(R.string.action_save);
        buttonDelete.setText(R.string.action_delete_caps);
        buttonCancel.setText(R.string.action_cancel);  // action_cancel = "Abbrechen" in strings_messages.xml

        buttonSave.setOnClickListener(v -> {
            String newTitle = editTitle.getText().toString().trim();
            String newDesc = editDescription.getText().toString().trim();
            String newDate = editDate.getText().toString().trim();
            String newRepeat = editRepeat.getText().toString().trim();
            String endDate = editEndDate.getText().toString().trim();
            boolean noEndDate = checkNoEndDate.isChecked();

            if (TextUtils.isEmpty(newTitle)) {
                Toast.makeText(getContext(), R.string.reminder_title_required, Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(newDate)) {
                Toast.makeText(getContext(), R.string.reminder_date_required, Toast.LENGTH_SHORT).show();
                return;
            }

            FragmentBg.runIO(this, () -> {
                com.example.plantcare.data.repository.ReminderRepository reminderRepo =
                        com.example.plantcare.data.repository.ReminderRepository
                                .getInstance(requireContext());

                reminderRepo.deleteFutureManualRepeatsBlocking(
                        reminder.plantId,
                        reminder.userEmail,
                        reminder.date,
                        reminder.description,
                        reminder.repeat
                );

                reminder.plantName = newTitle;
                reminder.description = newDesc;
                reminder.date = newDate;
                reminder.repeat = newRepeat;
                reminderRepo.updateBlocking(reminder);
                FirebaseSyncManager.get().syncReminder(reminder);

                int repeatDays = 0;
                try { repeatDays = Integer.parseInt(newRepeat); } catch (Exception __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                if (repeatDays > 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    Calendar cal = Calendar.getInstance();
                    try { cal.setTime(sdf.parse(newDate)); } catch (Exception __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

                    Calendar maxCal = Calendar.getInstance();
                    maxCal.setTime(cal.getTime());
                    maxCal.add(Calendar.YEAR, 2);

                    Calendar endCal = null;
                    if (!noEndDate && !TextUtils.isEmpty(endDate)) {
                        try {
                            endCal = Calendar.getInstance();
                            endCal.setTime(sdf.parse(endDate));
                        } catch (ParseException e) {
                            endCal = null;
                        }
                    }

                    // Hard cap matches AddReminderDialogFragment: at most
                    // 365 manual repeats per series so a daily cadence
                    // doesn't inflate to 730 rows + 730 Firestore writes.
                    final int MAX_REMINDERS = 365;
                    int created = 0;
                    for (int i = 1; i < 365 * 2 && created < MAX_REMINDERS; i++) {
                        cal.add(Calendar.DAY_OF_YEAR, repeatDays);
                        if (endCal != null && cal.after(endCal)) {
                            break;
                        }
                        if (cal.after(maxCal)) {
                            break;
                        }
                        String nextDate = sdf.format(cal.getTime());
                        WateringReminder r = new WateringReminder();
                        r.plantId = reminder.plantId;
                        r.userEmail = reminder.userEmail;
                        r.plantName = newTitle;
                        r.description = newDesc;
                        r.date = nextDate;
                        r.repeat = newRepeat;
                        reminderRepo.insertBlocking(r);
                        FirebaseSyncManager.get().syncReminder(r);
                        created++;
                    }
                }

                // #4 fix: the recurring-save loop can run for several
                // seconds against 365 reminders; if the user dismisses
                // the dialog mid-save (or rotates), `requireActivity()`
                // throws IllegalStateException. Guard with isAdded()
                // before touching activity/context. DataChangeNotifier
                // still fires on a successful save even if the dialog
                // is gone — the work itself completed.
                DataChangeNotifier.notifyChange();
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    android.content.Context ctx = getContext();
                    if (ctx != null) {
                        Toast.makeText(ctx, R.string.saved, Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                });
            });
        });

        buttonDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.delete_confirm_title)
                    .setMessage(R.string.delete_confirm_message_generic)
                    .setPositiveButton(R.string.action_yes, (dialog, which) -> {
                        FragmentBg.runIO(this,
                                () -> com.example.plantcare.data.repository.ReminderRepository
                                        .getInstance(requireContext()).deleteBlocking(reminder),
                                () -> {
                                    Toast.makeText(getContext(), R.string.deleted, Toast.LENGTH_SHORT).show();
                                    DataChangeNotifier.notifyChange();
                                    dismiss();
                                });
                    })
                    .setNegativeButton(R.string.action_no, null)
                    .show();
        });

        buttonCancel.setOnClickListener(v -> dismiss());

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void updateFutureFields(EditText editRepeat, TextView textEditAffectsFuture, EditText editEndDate, CheckBox checkNoEndDate) {
        boolean showFuture = false;
        try {
            String s = editRepeat.getText().toString().trim();
            if (!TextUtils.isEmpty(s)) {
                int repeatInt = Integer.parseInt(s);
                if (repeatInt > 0)
                    showFuture = true;
            }
        } catch (Exception __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
        textEditAffectsFuture.setVisibility(showFuture ? View.VISIBLE : View.GONE);
        editEndDate.setVisibility(showFuture ? (checkNoEndDate.isChecked() ? View.GONE : View.VISIBLE) : View.GONE);
        checkNoEndDate.setVisibility(showFuture ? View.VISIBLE : View.GONE);
    }
}
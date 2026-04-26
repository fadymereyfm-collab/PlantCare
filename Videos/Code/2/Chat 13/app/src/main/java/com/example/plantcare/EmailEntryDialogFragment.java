package com.example.plantcare;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.plantcare.ui.util.FragmentBg;

public class EmailEntryDialogFragment extends DialogFragment {

    public interface OnEmailSetListener {
        void onEmailSet(String email, String name);
    }

    private OnEmailSetListener listener;

    public void setOnEmailSetListener(OnEmailSetListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_email_entry, null);

        EditText emailEdit = view.findViewById(R.id.editEmail);
        EditText nameEdit = view.findViewById(R.id.editName);
        EditText passwordEdit = view.findViewById(R.id.editPassword); // أضف هذا الحقل للواجهة
        Button btnSave = view.findViewById(R.id.buttonSave);

        btnSave.setOnClickListener(v -> {
            String email = emailEdit.getText().toString().trim();
            String name = nameEdit.getText().toString().trim();
            String password = passwordEdit.getText().toString().trim();

            if (TextUtils.isEmpty(email) || !email.contains("@")) {
                Toast.makeText(getContext(), R.string.email_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(password) || password.length() < 4) {
                Toast.makeText(getContext(), R.string.password_too_short, Toast.LENGTH_SHORT).show();
                return;
            }

            String hash = PasswordUtils.hash(password);

            // احفظ البريد في SharedPreferences
            SharedPreferences prefs = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
            prefs.edit().putString("current_user_email", email).putString("current_user_name", name).apply();

            // احفظ في قاعدة البيانات إذا كان جديدًا
            FragmentBg.runIO(this, () -> {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                UserDao userDao = db.userDao();
                User existing = userDao.getUserByEmail(email);
                if (existing == null) {
                    userDao.insert(new User(email, name, hash));
                }
            });

            // إشعار كل الفراجمنتات بالتغيير
            DataChangeNotifier.notifyChange();

            if (listener != null) listener.onEmailSet(email, name);
            dismiss();
        });

        return new AlertDialog.Builder(requireContext())
                .setTitle("Willkommen!")
                .setView(view)
                .setCancelable(false)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
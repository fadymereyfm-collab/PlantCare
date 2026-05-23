package com.example.plantcare;

import android.app.Dialog;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class AddRoomDialogFragment extends DialogFragment {
    public interface OnRoomAddedListener {
        void onRoomAdded(String roomName);
    }

    private OnRoomAddedListener listener;

    public void setOnRoomAddedListener(OnRoomAddedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_room, null);

        EditText editRoomName = view.findViewById(R.id.editRoomName);
        Button buttonAdd = view.findViewById(R.id.buttonAddRoomConfirm);
        Button buttonCancel = view.findViewById(R.id.buttonCancelAddRoom);

        Runnable submit = () -> {
            String name = editRoomName.getText().toString().trim();
            if (name.isEmpty() || listener == null) return;
            listener.onRoomAdded(name);
            dismiss();
        };

        buttonAdd.setOnClickListener(v -> submit.run());
        // Soft keyboard "Done" submits too — saves a tap.
        editRoomName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
                return true;
            }
            return false;
        });

        buttonCancel.setOnClickListener(v -> dismiss());

        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(view);
        dialog.setCancelable(true);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Match every other modal in the app (AddToMyPlantsDialogFragment etc.):
        // claim 92% of the screen width and a transparent background so the
        // MaterialCardView's own corner radius defines the dialog's edge.
        // Pre-fix the dialog used Android's default (~280dp) which made the
        // popup look cramped in the user's screenshots.
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
            int target = (int) (dm.widthPixels * 0.92f);
            dialog.getWindow().setLayout(target, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}

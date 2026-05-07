package com.example.plantcare;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;

/**
 * Full-screen in-app viewer. Handles http(s), content://, file:// and absolute file paths.
 * Tapping closes the dialog.
 */
public class FullScreenImageDialogFragment extends DialogFragment {
    private static final String ARG_PATH = "path";

    public static FullScreenImageDialogFragment newInstance(String imagePath) {
        FullScreenImageDialogFragment fragment = new FullScreenImageDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH, imagePath);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        ImageView imageView = new ImageView(getContext());
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(Color.BLACK);

        String imagePath = getArguments() != null ? getArguments().getString(ARG_PATH) : null;

        Object model = null;
        if (imagePath != null) {
            if (imagePath.startsWith("http")) {
                model = imagePath;
            } else if (imagePath.startsWith("content://") || imagePath.startsWith("file://")) {
                model = Uri.parse(imagePath);
            } else {
                // Absolute file path
                model = new java.io.File(imagePath);
            }
        }

        Glide.with(requireContext())
                .load(model)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_broken_image)
                .fitCenter()
                .into(imageView);

        imageView.setOnClickListener(v -> dismiss());

        return imageView;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        }
    }
}
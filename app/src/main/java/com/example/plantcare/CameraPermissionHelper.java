package com.example.plantcare;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

/**
 * Helper عام للكاميرا باستخدام Activity Result API.
 * الاستخدام:
 * CameraPermissionHelper h = new CameraPermissionHelper(activity, new CameraPermissionHelper.Callbacks() {
 *     public void onPhotoCaptured(Uri uri) { ... }
 *     public void onError(String message) { ... }
 * });
 * h.startCapture();
 */
public class CameraPermissionHelper {

    public interface Callbacks {
        void onPhotoCaptured(Uri uri);
        void onError(String message);
    }

    private final ComponentActivity activity;
    private final Callbacks callbacks;

    private Uri pendingOutputUri;

    private final ActivityResultLauncher<String> requestPermissionLauncher;
    private final ActivityResultLauncher<Uri> takePictureLauncher;

    public CameraPermissionHelper(ComponentActivity activity, Callbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;

        this.requestPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        openCamera();
                    } else {
                        callbacks.onError(activity.getString(R.string.camera_permission_required));
                    }
                }
        );

        this.takePictureLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success != null && success && pendingOutputUri != null) {
                        callbacks.onPhotoCaptured(pendingOutputUri);
                    } else {
                        callbacks.onError(activity.getString(R.string.camera_app_not_found));
                    }
                }
        );
    }

    public void startCapture() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        try {
            File out = ImageUtils.createImageFile(activity);
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", out);
            pendingOutputUri = uri;
            takePictureLauncher.launch(uri);
        } catch (IOException e) {
            callbacks.onError(activity.getString(R.string.camera_file_create_error));
        }
    }
}
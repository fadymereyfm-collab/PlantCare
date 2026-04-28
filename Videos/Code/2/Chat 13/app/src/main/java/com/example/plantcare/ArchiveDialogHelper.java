package com.example.plantcare.partials;

import android.content.Context;
import android.net.Uri;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import com.bumptech.glide.Glide;
import com.example.plantcare.weekbar.ArchiveStore;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ArchiveDialogHelper {
    public static void show(Context context, String userEmail, long plantId, String plantName) {
        List<ArchiveStore.PhotoEntry> entries =
                ArchiveStore.INSTANCE.getPhotos(context, userEmail, plantId);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(8 * context.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        if (entries.isEmpty()) {
            AlertDialog.Builder b = new AlertDialog.Builder(context)
                    .setTitle("Bilder von " + (plantName != null ? plantName : ""))
                    .setMessage("Keine Fotos verfügbar")
                    .setPositiveButton("Schließen", null);
            b.show();
            return;
        }

        DateTimeFormatter df = DateTimeFormatter.ISO_LOCAL_DATE.withLocale(Locale.getDefault());
        for (ArchiveStore.PhotoEntry e : entries) {
            ImageView iv = new ImageView(context);
            iv.setAdjustViewBounds(true);
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // Load the photo
            Uri uri = e.getUri();
            Glide.with(context).load(uri).into(iv);

            // Optional: set the date as content description for accessibility
            try {
                if (e.getDate() != null) {
                    iv.setContentDescription(e.getDate().format(df));
                }
            } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

            root.addView(iv);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Bilder von " + (plantName != null ? plantName : ""))
                .setView(root)
                .setPositiveButton("Schließen", null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
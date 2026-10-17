package com.example.plantcare;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;

public class CalendarFragment extends Fragment {

    private com.example.plantcare.weekbar.PhotoCaptureCoordinator photoCoordinator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ComposeView composeView = new ComposeView(requireContext());

        photoCoordinator = new com.example.plantcare.weekbar.PhotoCaptureCoordinator(
                this,
                new com.example.plantcare.weekbar.PhotoCaptureCoordinator.DefaultPlantProvider(),
                new kotlin.jvm.functions.Function0<kotlin.Unit>() {
                    @Override
                    public kotlin.Unit invoke() {
                        try { DataChangeNotifier.notifyChange(); } catch (Throwable e) { CrashReporter.INSTANCE.log(e); }
                        return kotlin.Unit.INSTANCE;
                    }
                },
                new kotlin.jvm.functions.Function1<Long, kotlin.Unit>() {
                    @Override
                    public kotlin.Unit invoke(Long aLong) {
                        try { DataChangeNotifier.notifyChange(); } catch (Throwable e) { CrashReporter.INSTANCE.log(e); }
                        return kotlin.Unit.INSTANCE;
                    }
                }
        );

        com.example.plantcare.weekbar.CalendarScreenBridge.showInView(
                composeView,
                new kotlin.jvm.functions.Function0<kotlin.Unit>() {
                    @Override
                    public kotlin.Unit invoke() {
                        new AddReminderDialogFragment().show(getParentFragmentManager(), "add_reminder");
                        return kotlin.Unit.INSTANCE;
                    }
                },
                new kotlin.jvm.functions.Function0<kotlin.Unit>() {
                    @Override
                    public kotlin.Unit invoke() {
                        if (photoCoordinator != null) {
                            photoCoordinator.startCalendarPhotoFlow();
                        }
                        return kotlin.Unit.INSTANCE;
                    }
                },
                new kotlin.jvm.functions.Function0<kotlin.Unit>() {
                    @Override
                    public kotlin.Unit invoke() {
                        if (photoCoordinator != null) {
                            // Use integrated gallery flow that also asks for plant and date
                            photoCoordinator.startCalendarGalleryFlow();
                        }
                        return kotlin.Unit.INSTANCE;
                    }
                },
                null
        );

        return composeView;
    }
}
package com.example.plantcare.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.example.plantcare.AddReminderDialogFragment
import com.example.plantcare.CrashReporter
import com.example.plantcare.DataChangeNotifier
import com.example.plantcare.weekbar.CalendarScreenBridge
import com.example.plantcare.weekbar.PhotoCaptureCoordinator
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Phase G4.2 — Full calendar as a modal bottom sheet.
 *
 * Replaces the legacy CalendarFragment that used to live as a top-level
 * Bottom-Navigation tab in F2.2. In Phase G the Calendar tab was dropped
 * to make room for Diagnose (G1.5), and the full Compose calendar is now
 * shown on-demand when the user taps the "Voller Kalender" affordance
 * below the week strip in Home (TodayFragment.openFullCalendar).
 *
 * Wraps the existing Compose calendar (CalendarScreenBridge.showInView)
 * inside a ComposeView so business logic stays in one place. The sheet
 * opens at ~90% of the screen height (expanded state) so the user gets
 * a full month grid without losing the "this is overlayed" affordance.
 */
class FullCalendarBottomSheet : BottomSheetDialogFragment() {

    private var photoCoordinator: PhotoCaptureCoordinator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext())
        composeView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        photoCoordinator = PhotoCaptureCoordinator(
            /* host = */ this,
            /* plantProvider = */ PhotoCaptureCoordinator.DefaultPlantProvider(),
            /* onPhotoCaptured = */ {
                try {
                    DataChangeNotifier.notifyChange()
                } catch (t: Throwable) {
                    CrashReporter.log(t)
                }
            },
            /* onPlantIdReady = */ { _: Long ->
                try {
                    DataChangeNotifier.notifyChange()
                } catch (t: Throwable) {
                    CrashReporter.log(t)
                }
            }
        )

        CalendarScreenBridge.showInView(
            composeView,
            /* onAddReminderClick = */ {
                AddReminderDialogFragment().show(
                    parentFragmentManager,
                    "add_reminder_from_calendar_sheet"
                )
            },
            /* onCameraClick = */ {
                photoCoordinator?.startCalendarPhotoFlow()
            },
            /* onGalleryClick = */ {
                photoCoordinator?.startCalendarGalleryFlow()
            },
            /* onGoToToday = */ null
        )

        return composeView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { d ->
            val bottomSheet = (d as BottomSheetDialog)
                .findViewById<FrameLayout>(
                    com.google.android.material.R.id.design_bottom_sheet
                )
            if (bottomSheet != null) {
                // Take ~90% of screen height so the month grid + day list fit.
                val targetH = (resources.displayMetrics.heightPixels * 0.9f).toInt()
                bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                    height = targetH
                }
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.peekHeight = targetH
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onDestroyView() {
        photoCoordinator = null
        super.onDestroyView()
    }
}

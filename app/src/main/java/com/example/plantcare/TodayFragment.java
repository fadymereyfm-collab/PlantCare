package com.example.plantcare;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.plantcare.feature.streak.ChallengeRegistry;
import com.example.plantcare.ui.home.HomeScreenController;
import com.example.plantcare.ui.viewmodel.TodayViewModel;

/**
 * TodayFragment — the "Home" tab.
 *
 * 2026-10 redesign: the screen is Compose ({@link com.example.plantcare.ui.home.HomeScreen}),
 * hosted by {@link HomeScreenController}. This fragment only owns the
 * ViewModel wiring and forwards its LiveData; the actions that used to live
 * in DailyWateringAdapter (mark done, reschedule, bulk done, manual reminder
 * edit/delete, plant details) and the hero/search/calendar wiring moved into
 * the controller.
 */
public class TodayFragment extends Fragment {

    private TodayViewModel viewModel;
    private HomeScreenController controller;

    /**
     * NB: `this::refresh` erzeugt bei jedem Aufruf eine neue Runnable-Instanz.
     * Ohne gespeicherte Referenz stimmen add/remove nicht überein und der Listener
     * bleibt im statischen Set von DataChangeNotifier liegen → Fragment-Leak.
     * Deshalb genau **eine** Referenz in einem Feld halten.
     */
    private final Runnable dataChangeListener = this::refresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        controller = new HomeScreenController(this);
        return controller.createView(requireContext());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(TodayViewModel.class);

        // Streak (aktuell + Bestwert) → Fortschrittskarte
        viewModel.getStreakState().observe(getViewLifecycleOwner(), pair ->
                controller.setStreak(pair.getFirst(), pair.getSecond()));

        // Challenges-Liste
        viewModel.getChallenges().observe(getViewLifecycleOwner(), controller::setChallenges);

        // Sprint-1 Task 1.4: celebration dialog replaces the easy-to-miss
        // toast. Inflated body lives in dialog_challenge_complete.xml; we only
        // bind the challenge title and let the rest of the layout (emoji,
        // headline, subtext) come from string resources so EN/DE both work.
        viewModel.getJustCompletedChallenge().observe(getViewLifecycleOwner(), completed -> {
            if (completed != null) {
                showChallengeCompleteDialog(completed);
                viewModel.consumeCompletedChallenge();
            }
        });

        // Urlaubsbanner
        viewModel.getVacationBannerText().observe(getViewLifecycleOwner(), controller::setVacationText);

        // Heutige Aufgaben, nach Zimmer gruppiert
        viewModel.getTodayTasksGroupedByRoom().observe(getViewLifecycleOwner(), controller::setRoomGroups);

        // Listen for data changes and refresh.
        // Registrierung an den View-Lebenszyklus koppeln, damit
        // kein dangling Listener das Fragment am Leben hält.
        DataChangeNotifier.addListener(dataChangeListener);
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (controller != null) controller.refreshProStatus();
        refresh();
    }

    @Override
    public void onDestroyView() {
        // View ist weg → Listener entfernen (gleiche Referenz wie beim add).
        DataChangeNotifier.removeListener(dataChangeListener);
        super.onDestroyView();
    }

    private String getCurrentUserEmail() {
        if (!isAdded() || getContext() == null) return null;
        return EmailContext.current(requireContext());
    }

    public void refresh() {
        if (!isAdded() || getContext() == null) return;
        String email = getCurrentUserEmail();
        if (email != null && viewModel != null) {
            viewModel.loadTodayTasks(email);
            viewModel.refreshHeader(email);
        }
    }

    /**
     * Sprint-1 Task 1.4: replaces the previous Toast with a custom-bodied
     * MaterialAlertDialog, so completing a challenge is acknowledged with a
     * proper headline instead of an easy-to-miss toast.
     */
    private void showChallengeCompleteDialog(@NonNull ChallengeRegistry.Challenge completed) {
        if (!isAdded() || getContext() == null) return;
        View body = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_challenge_complete, null, false);
        TextView titleView = body.findViewById(R.id.challengeCompleteTitle);
        titleView.setText(completed.getTitleRes());

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setView(body)
                .setPositiveButton(R.string.challenge_complete_dismiss, null)
                .setCancelable(true)
                .show();
    }
}

package com.example.plantcare;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.plantcare.feature.streak.ChallengeRegistry;
import com.example.plantcare.ui.viewmodel.TodayViewModel;

import java.util.*;

/**
 * TodayFragment — now uses TodayViewModel (MVVM) instead of direct DB access.
 * The heavy business logic (querying, grouping, room mapping) lives in the ViewModel.
 */
public class TodayFragment extends Fragment {

    private RecyclerView recyclerView;
    private DailyWateringAdapter adapter;
    private TextView emptyMessage;
    private TodayViewModel viewModel;

    // Streak-Bar + Urlaubsbanner (Features 2 &amp; 4)
    private TextView vacationBanner;
    private TextView streakDaysText;
    private TextView streakBestText;
    private LinearLayout challengesContainer;

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
        return inflater.inflate(R.layout.fragment_today, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerViewToday);
        emptyMessage = view.findViewById(R.id.textEmptyToday);

        vacationBanner = view.findViewById(R.id.vacationBanner);
        streakDaysText = view.findViewById(R.id.streakDaysText);
        streakBestText = view.findViewById(R.id.streakBestText);
        challengesContainer = view.findViewById(R.id.challengesContainer);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DailyWateringAdapter(new ArrayList<>(), requireContext(), getCurrentUserEmail());
        recyclerView.setAdapter(adapter);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(TodayViewModel.class);

        // Streak-Bar: aktuelle Streak + Bestzeit
        viewModel.getStreakState().observe(getViewLifecycleOwner(), pair -> {
            int current = pair.getFirst();
            int best = pair.getSecond();
            if (current > 0) {
                streakDaysText.setText(getString(R.string.streak_days_format, current));
            } else {
                streakDaysText.setText(R.string.streak_zero);
            }
            if (best > 0) {
                streakBestText.setVisibility(View.VISIBLE);
                streakBestText.setText(getString(R.string.streak_best_format, best));
            } else {
                streakBestText.setVisibility(View.GONE);
            }
        });

        // Challenges-Liste
        viewModel.getChallenges().observe(getViewLifecycleOwner(), this::renderChallenges);

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
        viewModel.getVacationBannerText().observe(getViewLifecycleOwner(), txt -> {
            if (txt != null) {
                vacationBanner.setText(txt);
                vacationBanner.setVisibility(View.VISIBLE);
            } else {
                vacationBanner.setVisibility(View.GONE);
            }
        });

        // Observe today's tasks grouped by room
        viewModel.getTodayTasksGroupedByRoom().observe(getViewLifecycleOwner(), roomGroups -> {
            if (roomGroups == null || roomGroups.isEmpty()) {
                emptyMessage.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                adapter.setGroupedItems(new ArrayList<>());
            } else {
                emptyMessage.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);

                // Convert TodayViewModel.RoomGroup to DailyWateringAdapter.RoomHeader
                List<DailyWateringAdapter.RoomHeader> headers = new ArrayList<>();
                for (TodayViewModel.RoomGroup group : roomGroups) {
                    headers.add(new DailyWateringAdapter.RoomHeader(
                            group.getRoomId(),
                            group.getRoomName(),
                            group.getReminders()
                    ));
                }
                adapter.setGroupedItems(headers);
            }
        });

        // Listen for data changes and refresh.
        // Registrierung an den View-Lebenszyklus koppeln, damit
        // kein dangling Listener das Fragment am Leben hält.
        DataChangeNotifier.addListener(dataChangeListener);
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
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
     * MaterialAlertDialog. Reasons:
     *   • A toast at LENGTH_LONG (~3.5s) on the Today screen can easily be
     *     missed — the user is looking at the watering grid, not the bottom
     *     edge of the screen.
     *   • Toast text is plain; a dialog gives us a 64sp emoji, a headline,
     *     and the actual challenge title in proper hierarchy.
     *   • Acknowledging the celebration via the OK button feels closer to
     *     the Duolingo-style positive feedback the streak system was modelled
     *     after (per the existing "soft challenges, no push aggressivity"
     *     comment in ChallengeRegistry).
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

    /**
     * Rendert die Challenge-Liste als Text-Zeilen mit Fortschritt.
     * Abgeschlossene Challenges bekommen ein grünes Häkchen-Prefix.
     */
    private void renderChallenges(@Nullable List<ChallengeRegistry.Challenge> list) {
        if (challengesContainer == null) return;
        challengesContainer.removeAllViews();
        if (list == null || list.isEmpty()) return;
        for (ChallengeRegistry.Challenge c : list) {
            TextView row = new TextView(requireContext());
            String title = getString(c.getTitleRes());
            String progress = getString(
                    R.string.challenge_progress_format,
                    c.getDisplayProgress(), c.getTarget()
            );
            String prefix = c.isComplete() ? "\u2705 " : "\u2022 ";
            row.setText(prefix + title + "   " + progress);
            row.setTextSize(13f);
            row.setPadding(0, 4, 0, 4);
            row.setTextColor(getResources().getColor(R.color.text_secondary, null));
            challengesContainer.addView(row);
        }
    }
}

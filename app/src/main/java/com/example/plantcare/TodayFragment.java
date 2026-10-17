package com.example.plantcare;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;

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

    // F3.1 — new visual elements (greeting trio fully removed in G2.3).
    private View vacationBannerCard;
    private View emptyStateCard;
    private View todaySectionLabel;

    // G2.3 — new Premium Hero elements.
    private LinearLayout weekStrip;
    private MaterialCardView searchPill;
    private MaterialButton openFullCalendar;
    private View heroProBanner;

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

        // F3.1 — new visual elements (greeting trio removed in G2.3)
        vacationBannerCard = view.findViewById(R.id.vacationBannerCard);
        emptyStateCard = view.findViewById(R.id.emptyStateCard);
        todaySectionLabel = view.findViewById(R.id.todaySectionLabel);

        // G2.3 — Premium Hero elements
        weekStrip = view.findViewById(R.id.weekStrip);
        searchPill = view.findViewById(R.id.searchPill);
        openFullCalendar = view.findViewById(R.id.openFullCalendar);
        heroProBanner = view.findViewById(R.id.heroProBanner);

        buildWeekStrip();
        wireHeroActions();

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setNestedScrollingEnabled(false);
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

        // Urlaubsbanner — F3.1: visibility toggled on the wrapping card
        // so the icon + padded card chrome appears/hides together with
        // the text.
        viewModel.getVacationBannerText().observe(getViewLifecycleOwner(), txt -> {
            if (txt != null) {
                vacationBanner.setText(txt);
                if (vacationBannerCard != null) vacationBannerCard.setVisibility(View.VISIBLE);
            } else {
                if (vacationBannerCard != null) vacationBannerCard.setVisibility(View.GONE);
            }
        });

        // Observe today's tasks grouped by room
        viewModel.getTodayTasksGroupedByRoom().observe(getViewLifecycleOwner(), roomGroups -> {
            if (roomGroups == null || roomGroups.isEmpty()) {
                if (emptyStateCard != null) emptyStateCard.setVisibility(View.VISIBLE);
                if (todaySectionLabel != null) todaySectionLabel.setVisibility(View.GONE);
                recyclerView.setVisibility(View.GONE);
                adapter.setGroupedItems(new ArrayList<>());
            } else {
                if (emptyStateCard != null) emptyStateCard.setVisibility(View.GONE);
                if (todaySectionLabel != null) todaySectionLabel.setVisibility(View.VISIBLE);
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
     * G2.3 — Builds the 7-day week strip programmatically.
     *
     * Renders Monday → Sunday of the *current* week with the user's
     * locale-aware short weekday name (Mo/Di/... in DE, Mon/Tue/... in
     * EN) and the day-of-month numeral underneath. The column whose
     * date matches LocalDate.now() gets a filled pc_primary circle as
     * a today-indicator (bg_weekday_today drawable).
     *
     * No data binding here — this is purely a calendar visual. Tapping
     * any column does nothing for G2 (Calendar bottom-sheet routing
     * lands in G4 via the openFullCalendar button).
     */
    private void buildWeekStrip() {
        if (weekStrip == null || getContext() == null) return;
        weekStrip.removeAllViews();

        LocalDate today = LocalDate.now();
        DayOfWeek startOfWeek = DayOfWeek.MONDAY;
        // Find the Monday of this week regardless of today's day-of-week.
        int delta = today.getDayOfWeek().getValue() - startOfWeek.getValue();
        if (delta < 0) delta += 7;
        LocalDate monday = today.minusDays(delta);

        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            boolean isToday = d.equals(today);

            LinearLayout col = new LinearLayout(requireContext());
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f);
            col.setLayoutParams(colLp);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            int padPx = dpToPx(6);
            col.setPadding(padPx, padPx, padPx, padPx);

            // Weekday short label (locale-aware)
            TextView dow = new TextView(requireContext());
            String dowName = d.getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, java.util.Locale.getDefault());
            // Strip trailing period if the locale appends one ("Mo." → "Mo")
            if (dowName.endsWith(".")) dowName = dowName.substring(0, dowName.length() - 1);
            dow.setText(dowName);
            dow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            dow.setGravity(Gravity.CENTER);
            int textColor = isToday
                    ? requireContext().getColor(R.color.pc_primary)
                    : requireContext().getColor(R.color.pc_onSurfaceSecondary);
            dow.setTextColor(textColor);
            col.addView(dow);

            // Day-of-month numeral inside an optional today circle
            FrameLayout circle = new FrameLayout(requireContext());
            FrameLayout.LayoutParams circleLp = new FrameLayout.LayoutParams(
                    dpToPx(36), dpToPx(36));
            LinearLayout.LayoutParams circleLpOuter = new LinearLayout.LayoutParams(
                    dpToPx(36), dpToPx(36));
            circleLpOuter.topMargin = dpToPx(4);
            circle.setLayoutParams(circleLpOuter);
            if (isToday) circle.setBackgroundResource(R.drawable.bg_weekday_today);

            TextView dom = new TextView(requireContext());
            FrameLayout.LayoutParams domLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            dom.setLayoutParams(domLp);
            dom.setText(String.valueOf(d.getDayOfMonth()));
            dom.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            dom.setGravity(Gravity.CENTER);
            dom.setTypeface(null, android.graphics.Typeface.BOLD);
            dom.setTextColor(isToday
                    ? requireContext().getColor(R.color.pc_onPrimary)
                    : requireContext().getColor(R.color.pc_onSurface));
            circle.addView(dom);
            col.addView(circle);

            weekStrip.addView(col);
        }
    }

    /** G2.3 — Wires up the Hero banner CTA, search pill, and Full-calendar link. */
    private void wireHeroActions() {
        if (searchPill != null) {
            // 2026-05-20 click-guard sweep: منع double-tap من startActivity CatalogActivity مرّتين
            searchPill.setOnClickListener(com.example.plantcare.ui.util.ClickGuard.wrap(v -> {
                if (!isAdded() || getContext() == null) return;
                // Opens the plant catalog so the user can browse / search.
                startActivity(new Intent(requireContext(),
                        com.example.plantcare.ui.catalog.CatalogActivity.class));
            }));
        }
        if (openFullCalendar != null) {
            // 2026-05-20 click-guard sweep: منع double-tap من فتح FullCalendarBottomSheet مرّتين
            openFullCalendar.setOnClickListener(com.example.plantcare.ui.util.ClickGuard.wrap(v -> {
                if (!isAdded()) return;
                try {
                    new com.example.plantcare.ui.calendar.FullCalendarBottomSheet()
                            .show(getParentFragmentManager(), "full_calendar");
                } catch (Throwable t) {
                    CrashReporter.INSTANCE.log(t);
                }
            }));
        }
        // Hero "Go Pro" CTA is wired via the included MaterialButton#heroCta
        // — open paywall through the existing dialog.
        if (heroProBanner != null) {
            View heroCta = heroProBanner.findViewById(R.id.heroCta);
            if (heroCta != null) {
                // 2026-05-20 click-guard sweep: منع double-tap من فتح PaywallDialog مرّتين
                heroCta.setOnClickListener(com.example.plantcare.ui.util.ClickGuard.wrap(v -> {
                    try {
                        // PaywallDialogFragment.newInstance lives inside a
                        // companion object without @JvmStatic, so from Java
                        // it must be reached via .Companion.
                        com.example.plantcare.billing.PaywallDialogFragment.Companion
                                .newInstance("home_hero_banner")
                                .show(getParentFragmentManager(), "paywall");
                    } catch (Throwable t) {
                        CrashReporter.INSTANCE.log(t);
                    }
                }));
            }
        }
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    /**
     * G3 — Renders each Challenge as a two-line row in challengesContainer.
     * (Stub kept here so the LiveData observer continues to call the same
     * method name; the real body is renderChallengesG3 below — invoked via
     * forwarding because the Java unicode escape in the old body resisted
     * an in-place patch.)
     */
    private void renderChallenges(@Nullable List<ChallengeRegistry.Challenge> list) {
        renderChallengesG3(list);
    }

    private void renderChallenges_LegacyDoNotCall_(@Nullable List<ChallengeRegistry.Challenge> list) {
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
            // G3: line replaced \u2014 see new renderChallenges body below.
            String prefix = c.isComplete() ? "\u2705 " : "\u2022 ";
            row.setText(prefix + title + "   " + progress);
            row.setTextSize(13f);
            row.setPadding(0, 4, 0, 4);
            row.setTextColor(getResources().getColor(R.color.text_secondary, null));
            challengesContainer.addView(row);
        }
    }

    /**
     * G3 \u2014 Renders each Challenge as a two-line row inside challengesContainer:
     *     [title text]                          [current/target text]
     *     [============ LinearProgressIndicator =================]
     *
     * Completed challenges:
     *   - title gets a check-mark prefix.
     *   - progress indicator is filled at 100% in pc_success colour.
     *   - the count text is hidden (the green bar speaks for itself).
     *
     * Active challenges:
     *   - bare title (no bullet noise).
     *   - "3/7" style counter on the right.
     *   - pc_primary indicator over pc_background_dim track.
     */
    private void renderChallengesG3(@Nullable List<ChallengeRegistry.Challenge> list) {
        if (challengesContainer == null) return;
        challengesContainer.removeAllViews();
        if (list == null || list.isEmpty()) return;

        int trackThicknessPx = dpToPx(8);
        int rowSpacingPx = dpToPx(14);
        int textColorPrimary = requireContext().getColor(R.color.pc_onSurface);
        int textColorSecondary = requireContext().getColor(R.color.pc_onSurfaceSecondary);
        int trackColor = requireContext().getColor(R.color.pc_background_dim);
        int primaryColor = requireContext().getColor(R.color.pc_primary);
        int successColor = requireContext().getColor(R.color.pc_success);

        for (int i = 0; i < list.size(); i++) {
            ChallengeRegistry.Challenge c = list.get(i);

            LinearLayout rowWrap = new LinearLayout(requireContext());
            rowWrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) wrapLp.topMargin = rowSpacingPx;
            rowWrap.setLayoutParams(wrapLp);

            LinearLayout headerRow = new LinearLayout(requireContext());
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView titleView = new TextView(requireContext());
            String title = getString(c.getTitleRes());
            if (c.isComplete()) title = "\u2705 " + title;
            titleView.setText(title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            titleView.setTextColor(textColorPrimary);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            titleView.setLayoutParams(titleLp);
            headerRow.addView(titleView);

            if (!c.isComplete()) {
                TextView counterView = new TextView(requireContext());
                counterView.setText(getString(
                        R.string.challenge_progress_format,
                        c.getDisplayProgress(), c.getTarget()));
                counterView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                counterView.setTextColor(textColorSecondary);
                counterView.setTypeface(null, android.graphics.Typeface.BOLD);
                headerRow.addView(counterView);
            }

            rowWrap.addView(headerRow);

            LinearProgressIndicator bar = new LinearProgressIndicator(requireContext());
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            barLp.topMargin = dpToPx(6);
            bar.setLayoutParams(barLp);
            bar.setTrackThickness(trackThicknessPx);
            int target = Math.max(1, c.getTarget());
            int progress = Math.max(0, Math.min(c.getDisplayProgress(), target));
            bar.setMax(target);
            bar.setProgress(progress);
            bar.setTrackCornerRadius(trackThicknessPx);
            bar.setIndicatorColor(c.isComplete() ? successColor : primaryColor);
            bar.setTrackColor(trackColor);
            rowWrap.addView(bar);

            challengesContainer.addView(rowWrap);
        }
    }
}

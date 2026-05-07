package com.example.plantcare;

import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.plantcare.ui.util.FragmentBg;
import com.example.plantcare.ui.util.PlantCategoryUtil;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class AllPlantsFragment extends Fragment {

    private PlantAdapter adapter;
    private com.example.plantcare.data.repository.PlantRepository plantRepo;

    /** Aktiver Kategorie-Filter: null = "Alle", sonst einer aus PlantCategoryUtil.ALL_CATEGORIES. */
    @Nullable
    private String activeCategory = null;

    /**
     * `this::loadAllPlants` erzeugt bei jedem Aufruf eine **neue** Runnable-
     * Instanz. Ohne gespeicherte Referenz greift removeListener nicht und der
     * Listener bleibt im statischen Set von DataChangeNotifier → Fragment-Leak.
     */
    private final Runnable dataChangeListener = this::loadAllPlants;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_all_plants, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewAllPlants);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        plantRepo = com.example.plantcare.data.repository.PlantRepository
                .getInstance(requireContext());

        adapter = new PlantAdapter(requireContext(), plant -> {
            // الضغط على عنصر من الدليل: افتح تفاصيل "نبات كتالوج"
            PlantDetailDialogFragment dialog = PlantDetailDialogFragment.newInstance(plant, false);
            dialog.setOnPlantAdded(this::loadAllPlants);
            dialog.show(getParentFragmentManager(), "all_plant_detail");
        }, false);
        recyclerView.setAdapter(adapter);

        setupCategoryChips(view);

        EditText editSearch = view.findViewById(R.id.editSearch);
        if (editSearch != null) {
            editSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (adapter != null && adapter.getFilter() != null) {
                        adapter.getFilter().filter(s.toString());
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // زر الإضافة +
        FloatingActionButton fab = view.findViewById(R.id.fabAddPlant);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                AddCustomPlantDialogFragment dialog = new AddCustomPlantDialogFragment();
                dialog.show(getParentFragmentManager(), "add_custom_plant");
            });
        }

        // استلام نتيجة إنشاء نبتة جديدة للدليل
        getParentFragmentManager().setFragmentResultListener(
                "custom_plant_created",
                this,
                (requestKey, bundle) -> {
                    String name = bundle.getString("name");
                    if (name == null || name.trim().isEmpty()) return;

                    String lighting = bundle.getString("lighting");
                    String soil = bundle.getString("soil");
                    String watering = bundle.getString("watering");
                    String fertilizing = bundle.getString("fertilizing");
                    String note = bundle.getString("note"); // غير مستخدمة في الدليل
                    String imagePath = bundle.getString("imagePath");

                    final AtomicReference<String> errorRef = new AtomicReference<>(null);
                    FragmentBg.runIO(this,
                            () -> {
                                try {
                                    Plant p = new Plant();
                                    p.name = name.trim();
                                    p.nickname = null;
                                    p.lighting = lighting;
                                    p.soil = soil;
                                    p.watering = watering;
                                    p.fertilizing = fertilizing;
                                    p.personalNote = null; // الدليل لا يحفظ ملاحظات شخصية
                                    p.isUserPlant = false; // مهم: هذه في "الدليل"
                                    p.userEmail = null;
                                    p.roomId = 0;
                                    p.startDate = null;
                                    p.wateringInterval = ReminderUtils.parseWateringInterval(watering);
                                    // Kategorie automatisch ableiten (Nutzer kann später manuell ändern)
                                    p.category = PlantCategoryUtil.classify(p.name, p.lighting, p.watering);

                                    if (imagePath != null && !imagePath.isEmpty()) {
                                        if (imagePath.startsWith("content://") || imagePath.startsWith("http")) {
                                            p.imageUri = imagePath;
                                        } else {
                                            // خزّنا الصورة داخلياً كملف؛ استخدم URI من نوع file://
                                            p.imageUri = Uri.fromFile(new File(imagePath)).toString();
                                        }
                                    }

                                    plantRepo.insertBlocking(p);
                                } catch (Exception e) {
                                    errorRef.set(e.getMessage());
                                }
                            },
                            () -> {
                                String err = errorRef.get();
                                if (err != null) {
                                    Toast.makeText(requireContext(), getString(R.string.save_failed, err), Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(requireContext(), R.string.plant_added_to_catalog, Toast.LENGTH_SHORT).show();
                                    loadAllPlants();
                                }
                            });
                }
        );

        DataChangeNotifier.addListener(dataChangeListener);
        loadAllPlants();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAllPlants();
    }

    @Override
    public void onDestroyView() {
        // gleiche Referenz wie beim addListener — sonst wird nichts entfernt
        DataChangeNotifier.removeListener(dataChangeListener);
        super.onDestroyView();
    }

    private void loadAllPlants() {
        final String cat = activeCategory;
        FragmentBg.<List<Plant>>runWithResult(this,
                () -> cat == null ? plantRepo.getAllNonUserPlantsBlocking() : plantRepo.getCatalogPlantsByCategoryBlocking(cat),
                list -> adapter.setPlantList(list));
    }

    /**
     * Baut die Chip-Reihe für den Katalog-Filter (Alle / Zimmer / Garten / Kräuter / Kakteen).
     * Die Chip-Gruppe ist optional im Layout — wenn nicht vorhanden, bleibt der Filter auf "Alle".
     */
    private void setupCategoryChips(View view) {
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupCategories);
        if (chipGroup == null) return;

        chipGroup.removeAllViews();

        Chip chipAll = new Chip(requireContext());
        chipAll.setText(R.string.filter_all);
        chipAll.setCheckable(true);
        chipAll.setChecked(true);
        chipAll.setTag(null);
        chipGroup.addView(chipAll);

        for (String cat : PlantCategoryUtil.ALL_CATEGORIES) {
            Chip chip = new Chip(requireContext());
            chip.setText(PlantCategoryUtil.labelFor(cat));
            chip.setCheckable(true);
            chip.setTag(cat);
            chipGroup.addView(chip);
        }

        chipGroup.setSingleSelection(true);
        chipGroup.setSelectionRequired(true);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip selected = group.findViewById(checkedIds.get(0));
            if (selected == null) return;
            Object tag = selected.getTag();
            activeCategory = (tag instanceof String) ? (String) tag : null;
            loadAllPlants();
        });
    }
}
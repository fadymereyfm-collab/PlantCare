package com.example.plantcare;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.plantcare.ui.util.FragmentBg;

import java.util.*;

/**
 * يعرض قائمة الغرف، ينشئ الافتراضيات إن لزم، ويعيد التحميل عند تغيير البيانات عبر DataChangeNotifier.
 */
public class MyPlantsFragment extends Fragment {

    private RecyclerView rvRooms;
    private RoomAdapter adapter;
    private androidx.recyclerview.widget.ItemTouchHelper roomTouchHelper;
    private final List<RoomCategory> rooms = new ArrayList<>();
    private String userEmail;

    private final Runnable dataChangeListener = this::loadRoomsEnsureDefaults;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_my_plants, container, false);

        rvRooms = root.findViewById(R.id.recyclerViewRooms);
        rvRooms.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new RoomAdapter(rooms, room -> {
            Intent i = new Intent(requireContext(), PlantsInRoomActivity.class);
            i.putExtra("room_id", room.id);
            i.putExtra("room_name", room.name);
            startActivity(i);
        });
        adapter.setOnRoomLongClickListener(this::showRoomActions);
        rvRooms.setAdapter(adapter);

        // Drag-to-reorder via the drag-handle icon at the right edge of each
        // row (RoomAdapter wires its OnStartDragRequested listener to
        // roomTouchHelper.startDrag). Long-press on the row body opens the
        // actions menu; Verschieben was removed from that menu because
        // calling startDrag from a menu callback fires after the original
        // touch has lifted — the drag state activated and ended silently
        // in the same frame, which used to (a) make the menu item appear
        // to do nothing and (b) trigger persistCurrentOrder + the manual-
        // reorder flag, falsely revealing the "Nach Anzahl Pflanzen
        // sortieren" reset row on the next long-press.
        roomTouchHelper = new androidx.recyclerview.widget.ItemTouchHelper(
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                        androidx.recyclerview.widget.ItemTouchHelper.UP
                                | androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                        0) {
                    private boolean dirty = false;

                    @Override
                    public boolean onMove(@NonNull androidx.recyclerview.widget.RecyclerView rv,
                                          @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder vh,
                                          @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
                        adapter.moveItem(vh.getAdapterPosition(),
                                target.getAdapterPosition());
                        dirty = true;
                        return true;
                    }

                    @Override
                    public boolean isLongPressDragEnabled() { return false; }

                    @Override
                    public void onSwiped(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder vh, int direction) { }

                    @Override
                    public void clearView(@NonNull androidx.recyclerview.widget.RecyclerView rv,
                                          @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder vh) {
                        super.clearView(rv, vh);
                        // Only persist + flip the manual-reorder flag when an
                        // actual move happened. Without this guard, every
                        // aborted drag (e.g. drag-handle tapped without
                        // movement) wrote position 0..N-1 + set the flag,
                        // making the auto-sort reset row appear randomly.
                        if (dirty) {
                            persistCurrentOrder();
                            dirty = false;
                        }
                    }
                });
        roomTouchHelper.attachToRecyclerView(rvRooms);
        // The drag handle in each row sits on the right side of the count
        // badge. Touch+drag on the handle triggers ItemTouchHelper.startDrag
        // immediately while the user's finger is still down — that's how
        // we get reliable drag-and-reorder, unlike the prior
        // startDrag-from-menu approach.
        adapter.setOnStartDragListener(vh -> roomTouchHelper.startDrag(vh));

        userEmail = EmailContext.current(requireContext());

        // Sage Garden — dashed "+ Zimmer hinzufügen" CTA at the bottom of the list
        View btnAddRoom = root.findViewById(R.id.btnAddRoom);
        if (btnAddRoom != null) {
            btnAddRoom.setOnClickListener(v -> {
                AddRoomDialogFragment dialog = new AddRoomDialogFragment();
                // Without this listener the Hinzufügen button is a no-op:
                // the dialog only ever delegates persistence back to its
                // owner. AddToMyPlantsDialogFragment + AddPlantDialogFragment
                // already wire it; MyPlantsFragment used to forget.
                dialog.setOnRoomAddedListener(roomName -> {
                    if (roomName == null || roomName.trim().isEmpty()) return;
                    final String name = roomName.trim();
                    final Context appCtx = requireContext().getApplicationContext();
                    final String email = userEmail;

                    if (email == null) {
                        // Guest mode — rooms are not persisted; show a hint
                        // and bail out instead of silently dropping the input.
                        android.widget.Toast.makeText(
                                appCtx,
                                R.string.quick_add_needs_login,
                                android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    final boolean[] inserted = { false };
                    FragmentBg.runIO(this, () -> {
                        com.example.plantcare.data.repository.RoomCategoryRepository roomRepo =
                                com.example.plantcare.data.repository.RoomCategoryRepository
                                        .getInstance(appCtx);
                        // Skip duplicate (case-insensitive) so retries don't
                        // pile up identical rows on a flaky tap.
                        List<RoomCategory> existing = roomRepo.getAllRoomsForUserBlocking(email);
                        if (existing != null) {
                            for (RoomCategory r : existing) {
                                if (r.name != null && r.name.equalsIgnoreCase(name)) {
                                    return;
                                }
                            }
                        }
                        RoomCategory rc = new RoomCategory();
                        rc.name = name;
                        rc.userEmail = email;
                        long newId = roomRepo.insertBlocking(rc);
                        rc.id = (int) newId;
                        try { FirebaseSyncManager.get().syncRoom(rc); }
                        catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                        inserted[0] = true;
                    }, () -> {
                        // Main callback only fires while the fragment is still
                        // added (FragmentBg guard). Skip the refresh on the
                        // duplicate-name no-op path.
                        if (inserted[0]) {
                            loadRoomsEnsureDefaults();
                            DataChangeNotifier.notifyChange();
                        }
                    });
                });
                dialog.show(getParentFragmentManager(), "AddRoomDialog");
            });
        }

        loadRoomsEnsureDefaults();
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        DataChangeNotifier.addListener(dataChangeListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        DataChangeNotifier.removeListener(dataChangeListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRoomsEnsureDefaults();
    }

    private void loadRoomsEnsureDefaults() {
        final Context appCtx = requireContext().getApplicationContext();
        final String email = userEmail;

        FragmentBg.<List<RoomCategory>>runWithResult(this,
                () -> {
                    if (email == null) {
                        return null; // signal to use defaults
                    }

                    // Sprint-3 cleanup: synchronized helper avoids duplicate
                    // default rooms when this Fragment is recreated quickly.
                    List<RoomCategory> loaded = com.example.plantcare.data.repository
                            .RoomCategoryRepository.getInstance(appCtx)
                            .ensureDefaultsForUserBlocking(email,
                                    com.example.plantcare.ui.util.DefaultRooms.get(appCtx));

                    // Default sort: rooms with the most plants on top.
                    // Once the user drag-reorders manually,
                    // RoomOrderingPrefs.markManualReorder pins the flag and
                    // we stop overriding the DAO's stored position order.
                    if (loaded != null && !loaded.isEmpty()
                            && !com.example.plantcare.ui.util.RoomOrderingPrefs
                                    .wasManuallyReordered(appCtx, email)) {
                        com.example.plantcare.data.repository.PlantRepository plantRepo =
                                com.example.plantcare.data.repository.PlantRepository
                                        .getInstance(appCtx);
                        // Snapshot of (room → count) computed once on IO so the
                        // sort comparator is O(n log n) reads from a HashMap
                        // rather than re-running the COUNT query at every compare.
                        java.util.Map<Integer, Integer> countByRoom = new java.util.HashMap<>();
                        for (RoomCategory r : loaded) {
                            countByRoom.put(r.id, plantRepo.countPlantsByRoomBlocking(r.id, email));
                        }
                        // Sort by count DESC. Ties fall through to `position`
                        // (insertion-order from R.array.default_rooms ⇒
                        // Wohnzimmer→Schlafzimmer→Flur→Küche→Bad→Toilette),
                        // then default-rooms priority for legacy rows where
                        // every position=0, then name as the final stable
                        // tiebreaker. Pre-fix the tiebreaker was alphabetical,
                        // so a fresh user with all-0 plants saw
                        // Bad→Flur→Küche→… instead of the curated priority.
                        final java.util.List<String> defaultsOrder =
                                com.example.plantcare.ui.util.DefaultRooms.get(appCtx);
                        java.util.Collections.sort(loaded, (a, b) -> {
                            int ca = countByRoom.getOrDefault(a.id, 0);
                            int cb = countByRoom.getOrDefault(b.id, 0);
                            if (ca != cb) return Integer.compare(cb, ca);
                            if (a.position != b.position) return Integer.compare(a.position, b.position);
                            int pa = a.name == null ? Integer.MAX_VALUE : defaultsOrder.indexOf(a.name);
                            int pb = b.name == null ? Integer.MAX_VALUE : defaultsOrder.indexOf(b.name);
                            if (pa < 0) pa = Integer.MAX_VALUE;
                            if (pb < 0) pb = Integer.MAX_VALUE;
                            if (pa != pb) return Integer.compare(pa, pb);
                            String na = a.name == null ? "" : a.name;
                            String nb = b.name == null ? "" : b.name;
                            return na.compareToIgnoreCase(nb);
                        });
                    }
                    return loaded;
                },
                loaded -> {
                    if (loaded == null || loaded.isEmpty()) {
                        rooms.clear();
                        for (String n : com.example.plantcare.ui.util.DefaultRooms
                                .get(requireContext())) {
                            RoomCategory r = new RoomCategory();
                            r.id = 0;
                            r.name = n;
                            r.userEmail = email;
                            rooms.add(r);
                        }
                    } else {
                        rooms.clear();
                        rooms.addAll(loaded);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    /**
     * Long-press on a room reveals Rename + Delete actions. Both fall back
     * to a guarded path: rename refuses empty / duplicate names, delete
     * refuses to remove a room while it still holds plants (otherwise the
     * plants would dangle on a non-existent roomId).
     */
    private void showRoomActions(RoomCategory room) {
        final android.content.Context appCtx = requireContext().getApplicationContext();
        final boolean manual = com.example.plantcare.ui.util.RoomOrderingPrefs
                .wasManuallyReordered(appCtx, userEmail);
        // Verschieben was removed from this menu — drag-and-reorder now
        // happens via the drag-handle icon on the right edge of each row.
        // Show the "Auto-Sortierung" reset only when the user has
        // actually overridden the auto-sort, otherwise it would be a
        // no-op item.
        // Renders through ActionListDialogFragment so the row styling
        // matches the rest of the app (catalog plant-detail / "Mehr
        // Optionen" / Add-Room dialogs) — pre-fix this used
        // MaterialAlertDialogBuilder.setItems which looked flat.
        final java.util.List<com.example.plantcare.ui.util
                .ActionListDialogFragment.Item> items =
                new java.util.ArrayList<>();

        items.add(new com.example.plantcare.ui.util
                .ActionListDialogFragment.Item(
                        getString(R.string.room_action_rename),
                        false,
                        () -> { showRenameRoomDialog(room); return kotlin.Unit.INSTANCE; }));

        if (manual) {
            items.add(new com.example.plantcare.ui.util
                    .ActionListDialogFragment.Item(
                            getString(R.string.room_action_auto_sort),
                            false,
                            () -> {
                                com.example.plantcare.ui.util.RoomOrderingPrefs
                                        .clearManualReorder(appCtx, userEmail);
                                loadRoomsEnsureDefaults();
                                return kotlin.Unit.INSTANCE;
                            }));
        }

        items.add(new com.example.plantcare.ui.util
                .ActionListDialogFragment.Item(
                        getString(R.string.room_action_delete),
                        true,
                        () -> { confirmDeleteRoom(room, appCtx); return kotlin.Unit.INSTANCE; }));

        new com.example.plantcare.ui.util.ActionListDialogFragment()
                .configure(room.name, items)
                .show(getParentFragmentManager(),
                        com.example.plantcare.ui.util
                                .ActionListDialogFragment.TAG);
    }

    /**
     * Persist the order shown on screen after a drag-to-reorder ends.
     * Runs on IO so the bulk update doesn't block the main thread; the
     * Room observable will not re-fire because positions still match
     * what's now on screen, so there's no flicker.
     */
    private void persistCurrentOrder() {
        if (adapter == null) return;
        final List<Integer> order = adapter.currentOrderIds();
        if (order.isEmpty()) return;
        final android.content.Context appCtx = requireContext().getApplicationContext();
        final String email = userEmail;
        FragmentBg.runIO(this, () -> {
            com.example.plantcare.data.repository.RoomCategoryRepository repo =
                    com.example.plantcare.data.repository.RoomCategoryRepository.getInstance(appCtx);
            repo.reorderBlocking(order);
            // Pin the manual-order flag so future loads stop overriding the
            // user's order with the count-based auto-sort. Cleared by the
            // long-press → "Auto-Sortierung" action.
            com.example.plantcare.ui.util.RoomOrderingPrefs
                    .markManualReorder(appCtx, email);
            // Mirror new positions to Firestore so the order survives a
            // reinstall. Pull each row back so we ship the updated
            // `position` field rather than the pre-drag snapshot.
            for (int id : order) {
                RoomCategory r = repo.findByIdBlocking(id);
                if (r != null) {
                    try { FirebaseSyncManager.get().syncRoom(r); }
                    catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                }
            }
            DataChangeNotifier.notifyChange();
        });
    }

    private void showRenameRoomDialog(RoomCategory room) {
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(room.name);
        input.setSelection(room.name == null ? 0 : room.name.length());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.room_rename_dialog_title)
                .setView(input)
                .setPositiveButton(R.string.action_add_short, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty() || newName.equalsIgnoreCase(room.name)) return;
                    final android.content.Context appCtx = requireContext().getApplicationContext();
                    final String email = userEmail;
                    final boolean[] duplicate = { false };
                    FragmentBg.runIO(this, () -> {
                        com.example.plantcare.data.repository.RoomCategoryRepository repo =
                                com.example.plantcare.data.repository.RoomCategoryRepository.getInstance(appCtx);
                        // Refuse rename if a sibling of the same name already exists.
                        List<RoomCategory> peers = repo.getAllRoomsForUserBlocking(email);
                        if (peers != null) {
                            for (RoomCategory r : peers) {
                                if (r.id != room.id && newName.equalsIgnoreCase(r.name)) {
                                    duplicate[0] = true;
                                    return;
                                }
                            }
                        }
                        room.name = newName;
                        repo.updateBlocking(room);
                        try { FirebaseSyncManager.get().syncRoom(room); }
                        catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                    }, () -> {
                        if (duplicate[0]) {
                            android.widget.Toast.makeText(appCtx, R.string.room_rename_duplicate,
                                    android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        android.widget.Toast.makeText(appCtx, R.string.room_renamed,
                                android.widget.Toast.LENGTH_SHORT).show();
                        loadRoomsEnsureDefaults();
                        DataChangeNotifier.notifyChange();
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDeleteRoom(RoomCategory room, android.content.Context appCtx) {
        final String email = userEmail;
        FragmentBg.<Integer>runWithResult(this,
                () -> com.example.plantcare.data.repository.PlantRepository
                        .getInstance(appCtx).countPlantsByRoomBlocking(room.id, email),
                count -> {
                    if (count != null && count > 0) {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.room_delete_blocked_title)
                                .setMessage(getString(R.string.room_delete_blocked_message, count))
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        return;
                    }
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.room_delete_confirm_title)
                            .setMessage(getString(R.string.room_delete_confirm_message, room.name))
                            .setPositiveButton(R.string.action_delete, (d, w) -> {
                                final int deletedId = room.id;
                                FragmentBg.runIO(this, () -> {
                                    com.example.plantcare.data.repository.RoomCategoryRepository
                                            .getInstance(appCtx).deleteBlocking(room);
                                    try { FirebaseSyncManager.get().deleteRoom(deletedId); }
                                    catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                                }, () -> {
                                    android.widget.Toast.makeText(appCtx, R.string.room_deleted,
                                            android.widget.Toast.LENGTH_SHORT).show();
                                    loadRoomsEnsureDefaults();
                                    DataChangeNotifier.notifyChange();
                                });
                            })
                            .setNegativeButton(R.string.action_cancel, null)
                            .show();
                });
    }

}
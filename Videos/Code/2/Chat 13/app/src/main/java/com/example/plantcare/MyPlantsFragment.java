package com.example.plantcare;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        // Drag-to-reorder: long-press triggers the room actions menu (rename /
        // delete / reorder), and the user picks "Reorder" to start a drag.
        // We don't enable long-press auto-drag because that would collide
        // with the actions menu intent. Drop persists once on clearView so
        // we don't hit the DB once per pixel.
        roomTouchHelper = new androidx.recyclerview.widget.ItemTouchHelper(
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                        androidx.recyclerview.widget.ItemTouchHelper.UP
                                | androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                        0) {
                    @Override
                    public boolean onMove(@NonNull androidx.recyclerview.widget.RecyclerView rv,
                                          @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder vh,
                                          @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
                        adapter.moveItem(vh.getAdapterPosition(),
                                target.getAdapterPosition());
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
                        persistCurrentOrder();
                    }
                });
        roomTouchHelper.attachToRecyclerView(rvRooms);

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
                        // Refresh on the main thread + broadcast so other
                        // screens (Today list, plant pickers) catch it too.
                        mainHandler.post(() -> {
                            loadRoomsEnsureDefaults();
                            DataChangeNotifier.notifyChange();
                        });
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

        FragmentBg.<List<RoomCategory>>runWithResult(this,
                () -> {
                    if (userEmail == null) {
                        return null; // signal to use defaults
                    }

                    // Sprint-3 cleanup: synchronized helper avoids duplicate
                    // default rooms when this Fragment is recreated quickly.
                    return com.example.plantcare.data.repository.RoomCategoryRepository
                            .getInstance(appCtx)
                            .ensureDefaultsForUserBlocking(userEmail,
                                    com.example.plantcare.ui.util.DefaultRooms.get(appCtx));
                },
                loaded -> {
                    if (loaded == null || loaded.isEmpty()) {
                        rooms.clear();
                        for (String n : com.example.plantcare.ui.util.DefaultRooms
                                .get(requireContext())) {
                            RoomCategory r = new RoomCategory();
                            r.id = 0;
                            r.name = n;
                            r.userEmail = userEmail;
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
        String[] actions = {
                getString(R.string.room_action_rename),
                getString(R.string.room_action_reorder),
                getString(R.string.room_action_delete)
        };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(room.name)
                .setItems(actions, (d, which) -> {
                    switch (which) {
                        case 0: showRenameRoomDialog(room); break;
                        case 1: startDragForRoom(room); break;
                        case 2: confirmDeleteRoom(room, appCtx); break;
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * Bridge between the actions menu and ItemTouchHelper: locate the
     * holder that currently shows this room and ask the helper to start
     * a drag on it. Drop will trigger persistCurrentOrder().
     */
    private void startDragForRoom(RoomCategory room) {
        if (rvRooms == null || roomTouchHelper == null) return;
        // Locate the adapter position by id.
        int pos = -1;
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).id == room.id) { pos = i; break; }
        }
        if (pos < 0) return;
        // Try the simple path first — usually the row is on-screen since
        // the user just long-pressed it.
        RecyclerView.ViewHolder vh = rvRooms.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            roomTouchHelper.startDrag(vh);
            return;
        }
        // Off-screen fallback: scroll the row into view, then post the
        // drag start so the freshly bound holder exists when we look
        // again. Without this, picking "Reorder" on a long list silently
        // does nothing for any room past the visible window.
        final int finalPos = pos;
        rvRooms.scrollToPosition(finalPos);
        rvRooms.post(() -> {
            RecyclerView.ViewHolder vh2 = rvRooms.findViewHolderForAdapterPosition(finalPos);
            if (vh2 != null) roomTouchHelper.startDrag(vh2);
        });
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
        FragmentBg.runIO(this, () -> {
            com.example.plantcare.data.repository.RoomCategoryRepository repo =
                    com.example.plantcare.data.repository.RoomCategoryRepository.getInstance(appCtx);
            repo.reorderBlocking(order);
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
                                    mainHandler.post(() -> {
                                        android.widget.Toast.makeText(appCtx, R.string.room_deleted,
                                                android.widget.Toast.LENGTH_SHORT).show();
                                        loadRoomsEnsureDefaults();
                                        DataChangeNotifier.notifyChange();
                                    });
                                });
                            })
                            .setNegativeButton(R.string.action_cancel, null)
                            .show();
                });
    }

}
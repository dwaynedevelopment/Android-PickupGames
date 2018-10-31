package com.dwaynedevelopment.passtimes.navigation.fragments.feed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;

import com.dwaynedevelopment.passtimes.R;
import com.dwaynedevelopment.passtimes.adapters.AttendingFeedViewAdapter;
import com.dwaynedevelopment.passtimes.adapters.EventFeedViewAdapter;
import com.dwaynedevelopment.passtimes.models.Event;
import com.dwaynedevelopment.passtimes.models.Player;
import com.dwaynedevelopment.passtimes.models.Sport;
import com.dwaynedevelopment.passtimes.navigation.fragments.event.CreateEventDialogFragment;
import com.dwaynedevelopment.passtimes.navigation.fragments.event.ViewEventDialogFragment;
import com.dwaynedevelopment.passtimes.utils.AuthUtils;
import com.dwaynedevelopment.passtimes.utils.FirebaseFirestoreUtils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.dwaynedevelopment.passtimes.utils.KeyUtils.ACTION_EVENT_SELECTED;
import static com.dwaynedevelopment.passtimes.utils.KeyUtils.DATABASE_REFERENCE_EVENTS;
import static com.dwaynedevelopment.passtimes.utils.KeyUtils.DATABASE_REFERENCE_USERS;
import static com.dwaynedevelopment.passtimes.utils.KeyUtils.EXTRA_SELECTED_EVENT_ID;

public class FeedFragment extends Fragment {

    private FirebaseFirestoreUtils mDb;
    private AuthUtils mAuth;

    private RecyclerView eventsRecyclerView;
    private EventFeedViewAdapter eventFeedViewAdapter;

    private RecyclerView attendedRecyclerView;
    private AttendingFeedViewAdapter attendingFeedViewAdapter;

    private EventReceiver eventReceiver;
    private PopupMenu popupMenu;
    private ProgressBar progressBar;

    private Map<String, Event> attendedEventsMap = new HashMap<>();
    private Map<String, Event> mainFeedEvents = new HashMap<>();
    private Map<String, Event> filteredEventsByCategory = new HashMap<>();
    private List<String> selectedSports = new ArrayList<>();
    private List<String> initialSports = new ArrayList<>();

    private ListenerRegistration eventListenerRegister;
    private ListenerRegistration attendingListenerRegister;
    private ListenerRegistration favoritesListenerRegister;

    private static final String TAG = "FeedFragment";

    public FeedFragment() {
        mDb = FirebaseFirestoreUtils.getInstance();
        mAuth = AuthUtils.getInstance();
    }

    public static FeedFragment newInstance() {
        return new FeedFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (getActivity() != null) {
            if (getView() != null) {
                View view = getView();
                if (view != null) {
                    Toolbar feedToolbar = view.findViewById(R.id.tb_feed);
                    feedToolbar.inflateMenu(R.menu.menu_feed);
                    feedToolbar.setOnMenuItemClickListener(menuItemClickListener);

                    progressBar = view.findViewById(R.id.pb_feed);
                    progressBar.setVisibility(View.VISIBLE);

                    ImageButton filterImageButton = view.findViewById(R.id.iv_filter_btn);
                    filterImageButton.setOnClickListener(filterListener);

                    popupMenu = new PopupMenu(getActivity().getApplicationContext(), filterImageButton, Gravity.BOTTOM);

                    favoritesListenerRegister = mDb.databaseCollection(DATABASE_REFERENCE_USERS)
                            .document(mAuth.getCurrentSignedUser().getId())
                            .addSnapshotListener(playerFavoritesListener);

                    eventListenerRegister = mDb.databaseCollection(DATABASE_REFERENCE_EVENTS)
                            .addSnapshotListener(eventSnapshotListener);

                    attendingListenerRegister = mDb.databaseCollection(DATABASE_REFERENCE_USERS)
                            .document(mAuth.getCurrentSignedUser().getId())
                            .addSnapshotListener(attendingSnapshotListener);

                    setupInitialRecyclerView(true);


                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        registerBroadcastReceiver();
    }

    private void registerBroadcastReceiver() {
        eventReceiver = new EventReceiver();
        IntentFilter actionFilter = new IntentFilter();
        actionFilter.addAction(ACTION_EVENT_SELECTED);
        if (getActivity() != null) {
            getActivity().registerReceiver(eventReceiver, actionFilter);
        }
    }

    private final EventListener<DocumentSnapshot> playerFavoritesListener = new EventListener<DocumentSnapshot>() {
        @Override
        public void onEvent(@javax.annotation.Nullable DocumentSnapshot playerDocumentSnapshot,
                            @javax.annotation.Nullable FirebaseFirestoreException e) {

            if (playerDocumentSnapshot != null) {
                final Player playerForFavorites = playerDocumentSnapshot.toObject(Player.class);

                if (playerForFavorites != null) {
                    List<DocumentReference> favoritesReference = playerForFavorites.getFavorites();
                    if (favoritesReference != null) {
                        for (int i = 0; i < favoritesReference.size(); i++) {
                            favoritesReference.get(i).addSnapshotListener((DocumentSnapshot favoritesDocumentSnapshot,
                                                                           FirebaseFirestoreException attendedException) -> {
                                if (favoritesDocumentSnapshot != null) {
                                    final Sport favoriteSport = favoritesDocumentSnapshot.toObject(Sport.class);
                                    if (favoriteSport != null) {
                                        popupMenu.getMenuInflater().inflate(R.menu.menu_filter, popupMenu.getMenu());
                                        popupMenu.getMenu().add(favoriteSport.getCategory());
                                        initialSports.add(favoriteSport.getCategory());
                                    }
                                }
                            });
                        }
                    }
                    setupInitialRecyclerView(false);
                    progressBar.setVisibility(View.GONE);
                }
            }
        }
    };

    private final EventListener<DocumentSnapshot> attendingSnapshotListener = new EventListener<DocumentSnapshot>() {
        @Override
        public void onEvent(@javax.annotation.Nullable DocumentSnapshot playerDocumentSnapshot,
                            @javax.annotation.Nullable FirebaseFirestoreException playerException) {

            if (playerDocumentSnapshot != null) {
                final Player attendedPlayer = playerDocumentSnapshot.toObject(Player.class);

                if (attendedPlayer != null) {
                    List<DocumentReference> attendingEventsReference = attendedPlayer.getAttending();
                    if (attendingEventsReference != null) {
                        for (int i = 0; i < attendingEventsReference.size(); i++) {
                            attendingEventsReference.get(i).addSnapshotListener((DocumentSnapshot attendedDocumentSnapshot,
                                                                                 FirebaseFirestoreException attendedException) -> {
                                if (attendedDocumentSnapshot != null) {
                                    if (attendedDocumentSnapshot.exists()) {
                                        final Event attendedEvents = attendedDocumentSnapshot.toObject(Event.class);
                                        if (attendedEvents != null) {
                                            if (!attendedEventsMap.containsKey(attendedEvents.getId())) {
                                                attendedEventsMap.put(attendedEvents.getId(), attendedEvents);
                                                if (attendingFeedViewAdapter != null) {
                                                    attendingFeedViewAdapter.notifyDataSetChanged();
                                                }
                                            }
                                        }
                                    } else {
                                        DocumentReference documentReference = mDb.databaseCollection(DATABASE_REFERENCE_USERS)
                                                .document(mAuth.getCurrentSignedUser().getId());
                                        final DocumentReference eventRemoveDocument = mDb.getFirestore()
                                                .document("/" + DATABASE_REFERENCE_EVENTS + "/" + attendedDocumentSnapshot.getId());
                                        documentReference.update("attending", FieldValue.arrayRemove(eventRemoveDocument));

                                        if (attendingFeedViewAdapter != null) {
                                            attendingFeedViewAdapter.notifyDataSetChanged();
                                        }
                                    }
                                }
                            });
                        }
                    }
                }
            }
        }
    };

    private final EventListener<QuerySnapshot> eventSnapshotListener = new EventListener<QuerySnapshot>() {
        @Override
        public void onEvent(@javax.annotation.Nullable QuerySnapshot queryDocumentSnapshots,
                            @javax.annotation.Nullable FirebaseFirestoreException e) {

            if (e != null) {
                Log.i(TAG, "onEvent: " + e.getLocalizedMessage());
                return;
            }
            if (queryDocumentSnapshots != null) {
                for (int i = 0; i < queryDocumentSnapshots.getDocumentChanges().size(); i++) {
                    final DocumentChange documentChange = queryDocumentSnapshots.getDocumentChanges().get(i);

                    if (documentChange != null) {
                        switch (documentChange.getType()) {
                            case ADDED:
                                if (queryDocumentSnapshots.getDocuments().get(i).exists()) {
                                    Event addedEvent = documentChange.getDocument().toObject(Event.class);
                                    if (!mainFeedEvents.containsKey(addedEvent.getId()) && !filteredEventsByCategory.containsKey(addedEvent.getId())) {
                                        mainFeedEvents.put(addedEvent.getId(), addedEvent);
                                        filteredEventsByCategory.put(addedEvent.getId(), addedEvent);
                                        if (!initialSports.contains(addedEvent.getSport())) {
                                            filteredEventsByCategory.remove(addedEvent.getId());
                                        }
                                        eventFeedViewAdapter.notifyItemInserted(i);
                                        eventFeedViewAdapter.notifyDataSetChanged();
                                        Log.i(TAG, "onEvent: ADDED " + documentChange.getDocument().toObject(Event.class).toString());
                                    } else {
                                        Log.i(TAG, "onEvent: NOT ADDED " + documentChange.getDocument().toObject(Event.class).toString());
                                    }
                                }

                                break;
                            case MODIFIED:
                                if (queryDocumentSnapshots.getDocuments().get(i).exists()) {
                                    final Event editEvent = documentChange.getDocument().toObject(Event.class);
                                    if (mainFeedEvents.containsKey(editEvent.getId()) && filteredEventsByCategory.containsKey(editEvent.getId())) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                            mainFeedEvents.replace(editEvent.getId(), editEvent);
                                            filteredEventsByCategory.replace(editEvent.getId(), editEvent);

                                            if (attendedEventsMap.containsKey(editEvent.getId())) {
                                                attendedEventsMap.replace(editEvent.getId(), editEvent);
                                                if (attendingFeedViewAdapter != null) {
                                                    eventFeedViewAdapter.notifyDataSetChanged();
                                                }
                                            }
                                            eventFeedViewAdapter.notifyItemChanged(i);
                                            eventFeedViewAdapter.notifyDataSetChanged();
                                            Log.i(TAG, "onEvent: MODIFIED " + documentChange.getDocument().toObject(Event.class).toString());
                                        }
                                    }
                                }

                                break;
                            case REMOVED:
                                if (queryDocumentSnapshots.getDocuments().get(i).exists()) {
                                    Event removedEvent = documentChange.getDocument().toObject(Event.class);
                                    if (mainFeedEvents.containsKey(removedEvent.getId()) && filteredEventsByCategory.containsKey(removedEvent.getId())) {
                                        mainFeedEvents.remove(removedEvent.getId());
                                        filteredEventsByCategory.remove(removedEvent.getId());

                                        if (attendedEventsMap.containsKey(removedEvent.getId())) {
                                            attendedEventsMap.remove(removedEvent.getId());
                                            if (attendingFeedViewAdapter != null) {
                                                eventFeedViewAdapter.notifyDataSetChanged();
                                            }
                                        }
                                        eventFeedViewAdapter.notifyItemRemoved(i);
                                        eventFeedViewAdapter.notifyDataSetChanged();
                                        Log.i(TAG, "onEvent: REMOVED " + documentChange.getDocument().toObject(Event.class).toString());
                                    }
                                    Log.i(TAG, "onEvent: REMOVED " + documentChange.getDocument().toObject(Event.class).toString());
                                }

                                break;
                        }
                    }
                }
            }

        }
    };


    private void setupInitialRecyclerView(boolean initialSetup) {
        if (filteredEventsByCategory != null) {
            filteredEventsByCategory.clear();
        }
        if (getActivity() != null) {
            if (getView() != null) {
                if (initialSetup) {
                    eventFeedViewAdapter = new EventFeedViewAdapter(mainFeedEvents, getActivity().getApplicationContext());

                    eventsRecyclerView = Objects.requireNonNull(getView()).findViewById(R.id.rv_ongoing);
                    eventsRecyclerView.setHasFixedSize(true);
                    eventsRecyclerView.setLayoutManager(new LinearLayoutManager(Objects.requireNonNull(getActivity()).getApplicationContext()));

                    eventsRecyclerView.setAdapter(eventFeedViewAdapter);
                    eventsRecyclerView.setVisibility(View.GONE);

                } else {
                    new Handler().postDelayed(() -> {
                        filteredEventsByCategory = mDb.filterEventByFavoriteSport(mainFeedEvents, initialSports);

                        eventFeedViewAdapter = new EventFeedViewAdapter(filteredEventsByCategory, getActivity().getApplicationContext());
                        eventsRecyclerView.setAdapter(eventFeedViewAdapter);
                        eventsRecyclerView.setVisibility(View.VISIBLE);

                        attendingFeedViewAdapter = new AttendingFeedViewAdapter(attendedEventsMap, getActivity().getApplicationContext());
                        attendedRecyclerView = getView().findViewById(R.id.rv_attending);
                        attendedRecyclerView.setHasFixedSize(true);
                        attendedRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity().getApplicationContext(),
                                LinearLayoutManager.HORIZONTAL, false));

                        attendedRecyclerView.setAdapter(attendingFeedViewAdapter);
                        attendingFeedViewAdapter.notifyDataSetChanged();

                    }, 350);
                }
            }
        }
    }


    private final View.OnClickListener filterListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (getActivity() != null) {

                for (int i = 0; i < popupMenu.getMenu().size(); i++) {
                    popupMenu.getMenu().getItem(i).setCheckable(true);
                }

                popupMenu.setOnMenuItemClickListener(menuItem -> {

                    if (!menuItem.isChecked()) {
                        menuItem.setChecked(true);
                        if (!selectedSports.contains(menuItem.getTitle().toString())) {
                            selectedSports.add(menuItem.getTitle().toString());
                            filteredEventsByCategory = mDb.filterEventByFavoriteSport(mainFeedEvents, selectedSports);
                        }
                    } else {

                        if (selectedSports.size() < 1) {
                            menuItem.setChecked(true);
                            return true;
                        } else {
                            menuItem.setChecked(false);
                            selectedSports.remove(menuItem.getTitle().toString());
                            filteredEventsByCategory = mDb.filterEventByFavoriteSport(mainFeedEvents, selectedSports);
                        }

                    }

                    if (filteredEventsByCategory != null) {
                        eventFeedViewAdapter = new EventFeedViewAdapter(filteredEventsByCategory, getActivity().getApplicationContext());
                        if (getView() != null) {
                            eventsRecyclerView = getView().findViewById(R.id.rv_ongoing);
                            eventsRecyclerView.setHasFixedSize(true);
                            eventsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity().getApplicationContext()));
                            eventsRecyclerView.setAdapter(eventFeedViewAdapter);
                        }
                    } else {
                        menuItem.setChecked(true);
                        if (!selectedSports.contains(menuItem.getTitle().toString())) {
                            selectedSports.add(menuItem.getTitle().toString());
                            filteredEventsByCategory = mDb.filterEventByFavoriteSport(mainFeedEvents, selectedSports);
                        }
                    }
                    return true;
                });
                popupMenu.show();
            }
        }
    };

    private final Toolbar.OnMenuItemClickListener menuItemClickListener = toolBarItem -> {
        if (toolBarItem.getItemId() == R.id.action_add) {
            CreateEventDialogFragment createEventDialog = new CreateEventDialogFragment();
            FragmentTransaction fragmentTransaction = Objects.requireNonNull(getFragmentManager()).beginTransaction();
            createEventDialog.show(fragmentTransaction, CreateEventDialogFragment.TAG);
        }
        return false;
    };

    @Override
    public void onPause() {
        super.onPause();
        if (eventListenerRegister != null && attendingListenerRegister != null && favoritesListenerRegister != null) {
            eventListenerRegister.remove();
            eventListenerRegister = null;
            attendingListenerRegister.remove();
            attendingListenerRegister = null;
            favoritesListenerRegister.remove();
            favoritesListenerRegister = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        selectedSports.clear();
        initialSports.clear();
        attendedEventsMap.clear();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterBroadcastReceiver();
    }

    private void unregisterBroadcastReceiver() {
        if (getActivity() != null) {
            getActivity().unregisterReceiver(eventReceiver);
        }
    }

    public class EventReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String receivedEventId = intent.getStringExtra(EXTRA_SELECTED_EVENT_ID);
            ViewEventDialogFragment viewEventDialogFragment = ViewEventDialogFragment.newInstance(receivedEventId);
            FragmentTransaction fragmentTransaction = Objects.requireNonNull(getFragmentManager()).beginTransaction();
            viewEventDialogFragment.show(fragmentTransaction, ViewEventDialogFragment.TAG);
        }
    }
}
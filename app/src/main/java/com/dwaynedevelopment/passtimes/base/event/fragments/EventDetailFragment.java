package com.dwaynedevelopment.passtimes.base.event.fragments;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.dwaynedevelopment.passtimes.R;
import com.dwaynedevelopment.passtimes.base.event.adapters.AttendeesViewAdapter;
import com.dwaynedevelopment.passtimes.base.event.interfaces.IEventHandler;
import com.dwaynedevelopment.passtimes.models.Event;
import com.dwaynedevelopment.passtimes.models.Player;
import com.dwaynedevelopment.passtimes.utils.AdapterUtils;
import com.dwaynedevelopment.passtimes.utils.AuthUtils;
import com.dwaynedevelopment.passtimes.utils.CalendarUtils;
import com.dwaynedevelopment.passtimes.utils.FirebaseFirestoreUtils;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.type.LatLng;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;

import static com.dwaynedevelopment.passtimes.utils.AdapterUtils.adapterViewStatus;
import static com.dwaynedevelopment.passtimes.utils.CalendarUtils.timeRangeString;
import static com.dwaynedevelopment.passtimes.utils.KeyUtils.ARGS_SELECTED_EVENT_ID;
import static com.dwaynedevelopment.passtimes.utils.KeyUtils.DATABASE_REFERENCE_EVENTS;
import static com.dwaynedevelopment.passtimes.utils.KeyUtils.DATABASE_REFERENCE_USERS;
import static com.dwaynedevelopment.passtimes.utils.KeyUtils.NOTIFY_INSERTED_DATA;
import static com.dwaynedevelopment.passtimes.utils.KeyUtils.NOTIFY_MODIFIED_DATA;
import static com.dwaynedevelopment.passtimes.utils.KeyUtils.NOTIFY_REMOVED_DATA;
import static com.dwaynedevelopment.passtimes.utils.LocationUtils.googlePlayServicesValid;

public class EventDetailFragment extends Fragment {

    public static final String TAG = "EventDetailFragment";
    private FirebaseFirestoreUtils mDb;
    private AuthUtils mAuth;
    private IEventHandler iEventHandler;

    private Button closeEventButton;
    private Button joinEventButton;
    private ImageButton deleteImageButton;
    private ImageButton editImageButton;
    private ImageButton unjoinEventImageButton;

    private Event eventSelected;
    private String eventIdExtra;
    private Map<String, Player> attendeesList = new HashMap<>();
    private boolean isHostToggle = false;

    private AttendeesViewAdapter attendeeFeedViewAdapter;

    public static EventDetailFragment newInstance(String eventId) {

        Bundle args = new Bundle();
        args.putString(ARGS_SELECTED_EVENT_ID, eventId);
        EventDetailFragment fragment = new EventDetailFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof IEventHandler) {
            iEventHandler = (IEventHandler) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_event, container, false);
    }


    private void invokeChildFragment(String eventIdExtra) {
        Fragment childMapFragment =  MapChildFragment.newInstance(eventIdExtra);
        getChildFragmentManager().beginTransaction()
                .add(R.id.container_map_child, childMapFragment)
                .commit();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (getActivity() != null) {
            mDb = FirebaseFirestoreUtils.getInstance();
            mAuth = AuthUtils.getInstance();

            if (getArguments() != null) {

                eventIdExtra = getArguments().getString(ARGS_SELECTED_EVENT_ID);
                if (eventIdExtra != null) {

                    if (!eventIdExtra.isEmpty()) {
                        mDb.databaseCollection(DATABASE_REFERENCE_EVENTS).document(eventIdExtra)
                                .addSnapshotListener(eventSnapshotListener);

                        if (getView() != null) {

                            if (googlePlayServicesValid(getActivity())) {
                                invokeChildFragment(eventIdExtra);
                            }

                            ImageButton closeImageButton = getView().findViewById(R.id.ib_close);
                            closeImageButton.setOnClickListener(eventOnClickListener);

                            closeEventButton = getView().findViewById(R.id.btn_event_end);
                            closeEventButton.setOnClickListener(eventOnClickListener);
                            closeEventButton.setVisibility(View.GONE);

                            joinEventButton = getView().findViewById(R.id.btn_event_join);
                            joinEventButton.setOnClickListener(eventOnClickListener);
                            joinEventButton.setVisibility(View.GONE);

                            deleteImageButton = getView().findViewById(R.id.ib_delete);
                            deleteImageButton.setOnClickListener(eventOnClickListener);
                            deleteImageButton.setVisibility(View.GONE);

                            editImageButton = getView().findViewById(R.id.ib_edit_event);
                            editImageButton.setOnClickListener(eventOnClickListener);
                            editImageButton.setVisibility(View.GONE);

                            unjoinEventImageButton = getView().findViewById(R.id.ib_unjoin);
                            unjoinEventImageButton.setOnClickListener(eventOnClickListener);
                            unjoinEventImageButton.setVisibility(View.GONE);


                            attendeeFeedViewAdapter = new AttendeesViewAdapter(attendeesList, getActivity().getApplicationContext(), eventSelected);
                            RecyclerView attendeeRecyclerView = getView().findViewById(R.id.rv_attending_list);
                            attendeeRecyclerView.setHasFixedSize(true);
                            attendeeRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity().getApplicationContext(),
                                    LinearLayoutManager.VERTICAL, false));

                            attendeeRecyclerView.setAdapter(attendeeFeedViewAdapter);
                            attendeeFeedViewAdapter.notifyDataSetChanged();

                        }
                    } else {
                        Log.i(TAG, "onActivityCreated: NOTHING HERE");
                    }
                }

            }
        }
    }


    private final EventListener<DocumentSnapshot> eventSnapshotListener = (DocumentSnapshot documentParentSnapshot,
                                                                           FirebaseFirestoreException eventException) -> {

        eventSelected = Objects.requireNonNull(documentParentSnapshot).toObject(Event.class);

        if (eventSelected != null) {
            if (getView() != null) {

                TextView tvMonth = getView().findViewById(R.id.tv_event_month);
                tvMonth.setText(CalendarUtils.getMonthFromDate(eventSelected.getStartDate()));

                TextView tvDay = getView().findViewById(R.id.tv_event_day);
                tvDay.setText(CalendarUtils.getDayFromDate(eventSelected.getStartDate()));

                TextView tvTitle = getView().findViewById(R.id.tv_event_title);
                tvTitle.setText(eventSelected.getTitle());

                TextView tvTime = getView().findViewById(R.id.tv_event_time);
                tvTime.setText(timeRangeString(eventSelected));

                TextView tvLocation = getView().findViewById(R.id.tv_event_location);
                tvLocation.setText(eventSelected.getLocation());

                CircleImageView ciHost = getView().findViewById(R.id.ci_host);

                DocumentReference hostReference = eventSelected.getEventHost();
                if (hostReference != null) {
                    hostReference.addSnapshotListener((documentChildSnapshot, playerException) -> {
                        if (documentChildSnapshot != null) {
                            final Player eventHost = documentChildSnapshot.toObject(Player.class);
                            if (eventHost != null) {
                                if (getActivity() != null) {
                                    Glide.with(getActivity().getApplicationContext()).load(eventHost.getThumbnail()).into(ciHost);
                                    if (eventHost.getId().equals(mAuth.getCurrentSignedUser().getId())) {
                                        if (eventHost.getId().equals(mAuth.getCurrentSignedUser().getId())) {
                                            isHostToggle = true;
                                            deleteImageButton.setVisibility(View.VISIBLE);
                                            editImageButton.setVisibility(View.VISIBLE);
                                            joinEventButton.setVisibility(View.GONE);
                                            unjoinEventImageButton.setVisibility(View.GONE);
                                            if (eventSelected != null) {
                                                if (eventSelected.getIsClosed()) {
                                                    deleteImageButton.setVisibility(View.GONE);
                                                    editImageButton.setVisibility(View.GONE);
                                                    joinEventButton.setVisibility(View.GONE);
                                                    unjoinEventImageButton.setVisibility(View.GONE);
                                                } else {
                                                    closeEventButton.setVisibility(View.VISIBLE);
                                                }
                                            }

                                        }
                                    }
                                }
                            }
                        }
                    });
                }

                List<DocumentReference> attendeesReference = eventSelected.getAttendees();
                //TODO: FIX FOR LIVE IMPLEMENTATION:
                if (attendeesReference != null) {
                    for (int i = 0; i <attendeesReference.size() ; i++) {
                        int finalI = i;
                        attendeesReference.get(i).addSnapshotListener((documentSnapshot, attendeeException) -> {
                            if (documentSnapshot != null) {
                                if (documentSnapshot.exists()) {
                                    final Player attendeeReference = documentSnapshot.toObject(Player.class);
                                    if (attendeeReference != null) {
                                        if (!attendeesList.containsKey(attendeeReference.getId())) {
                                            attendeesList.put(attendeeReference.getId(), attendeeReference);
                                            if(!attendeesList.containsKey(mAuth.getCurrentSignedUser().getId())) {
                                                if (eventSelected.getMaxAttendees() > attendeesList.size()) {
                                                    joinEventButton.setVisibility(View.VISIBLE);
                                                } else {
                                                    joinEventButton.setVisibility(View.GONE);
                                                }
                                            } else {
                                                if (!isHostToggle) {
                                                    unjoinEventImageButton.setVisibility(View.VISIBLE);
                                                    if (eventSelected != null) {
                                                        if (eventSelected.getIsClosed()) {
                                                            unjoinEventImageButton.setVisibility(View.GONE);
                                                        }
                                                    }
                                                }
                                                //ALWAYS HITS EVENT OR JOINED:
                                                joinEventButton.setVisibility(View.GONE);
                                            }
                                            if (getActivity() != null) {
                                                getActivity().runOnUiThread(() -> {
                                                    adapterViewStatus(attendeeFeedViewAdapter, NOTIFY_INSERTED_DATA, finalI);
                                                });
                                            }
                                        } else {
                                            if (getActivity() != null) {
                                                //THREAD: MODIFIED ATTENDING
                                                getActivity().runOnUiThread(() -> {
                                                    attendeesList.replace(attendeeReference.getId(), attendeeReference);
                                                    adapterViewStatus(attendeeFeedViewAdapter, NOTIFY_MODIFIED_DATA, finalI);

                                                    joinEventButton.setVisibility(View.GONE);
                                                });
                                            }
                                        }
                                    }
                                } else {

                                    final DocumentReference documentReference = mDb.databaseCollection(DATABASE_REFERENCE_USERS)
                                            .document(mAuth.getCurrentSignedUser().getId());

                                    final DocumentReference eventRemoveDocument = mDb.getFirestore()
                                            .document("/" + DATABASE_REFERENCE_EVENTS + "/" + eventSelected.getId());

                                    documentReference.update("attending", FieldValue.arrayRemove(eventRemoveDocument));
                                    eventRemoveDocument.update("attendees", FieldValue.arrayRemove(documentReference));

                                    if (getActivity() != null) {
                                        //THREAD: REMOVED ATTENDING
                                        getActivity().runOnUiThread(() -> {
                                            attendeesList.remove(documentSnapshot.getId());
                                            AdapterUtils.adapterViewStatus(attendeeFeedViewAdapter, NOTIFY_REMOVED_DATA, finalI);
                                        });
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }
    };


    private final View.OnClickListener eventOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_event_end:
                    if (iEventHandler != null) {
                        if (eventSelected != null) {
                            if (getActivity() != null) {
                                AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                                alertDialog.setTitle(eventSelected.getTitle());
                                alertDialog.setMessage("Are you sure you want to close this event?");

                                alertDialog.setPositiveButton("Yes", (dialog, which) ->

                                iEventHandler.invokeEndEvent(eventSelected.getId()));

                                alertDialog.setNegativeButton("Cancel", (dialog, which) ->
                                        dialog.cancel());

                                alertDialog.show();
                            }
                        }
                    }
                    break;
                case R.id.ib_close:
                    if (iEventHandler != null) {
                        iEventHandler.dismissDetailView();
                    }
                    break;
                case R.id.ib_edit_event:
                    if (iEventHandler != null) {
                        if (getActivity() != null) {
                            AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                            alertDialog.setTitle(eventSelected.getTitle());
                            alertDialog.setMessage("Want to edit this event?");

                            alertDialog.setPositiveButton("Edit", (dialog, which) ->

                                    iEventHandler.invokeEditDetailView(eventSelected.getId()));

                            alertDialog.setNegativeButton("Cancel", (dialog, which) ->
                                    dialog.cancel());

                            alertDialog.show();
                        }
                    }
                    break;
                case R.id.btn_event_join:
                    final DocumentReference playerDocumentReference = mDb.getFirestore()
                            .document("/" + DATABASE_REFERENCE_USERS + "/" + mAuth.getCurrentSignedUser().getId());
                    mDb.addAttendee(eventSelected, playerDocumentReference);

                    final DocumentReference eventDocumentReference = mDb.getFirestore()
                            .document("/" + DATABASE_REFERENCE_EVENTS + "/" + eventSelected.getId());
                    mDb.addAttendings(mAuth.getCurrentSignedUser(), eventDocumentReference);

                    joinEventButton.setVisibility(View.GONE);

                    break;
                case R.id.ib_delete:
                    if (getActivity() != null) {
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                        alertDialog.setTitle(eventSelected.getTitle());
                        alertDialog.setMessage("Are you sure you want delete this event?");

                        alertDialog.setPositiveButton("Sure", (dialog, which) ->
                                mDb.databaseCollection(DATABASE_REFERENCE_EVENTS)
                                .document(eventIdExtra)
                                .delete()
                                .addOnSuccessListener(deleteEventListener));

                        alertDialog.setNegativeButton("Cancel", (dialog, which) ->
                                dialog.cancel());

                        alertDialog.show();
                    }
                    break;
                case R.id.ib_unjoin:
                    if (getActivity() != null) {
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                        alertDialog.setTitle(eventSelected.getTitle());
                        alertDialog.setMessage("Are you sure you want leave this event?");
                        alertDialog.setPositiveButton("Sure", (dialog, which) -> {

                            final DocumentReference documentReference = mDb.databaseCollection(DATABASE_REFERENCE_USERS)
                                    .document(mAuth.getCurrentSignedUser().getId());

                            final DocumentReference eventRemoveDocument = mDb.getFirestore()
                                    .document("/" + DATABASE_REFERENCE_EVENTS + "/" + eventSelected.getId());

                            documentReference.update("attending", FieldValue.arrayRemove(eventRemoveDocument));
                            eventRemoveDocument.update("attendees", FieldValue.arrayRemove(documentReference));

                            if (iEventHandler != null) {
                                iEventHandler.dismissDetailView();
                            }
                        });

                        alertDialog.setNegativeButton("Cancel", (dialog, which) ->
                                dialog.cancel());

                        alertDialog.show();
                    }
                    break;
            }
        }
    };



    private final OnSuccessListener<Void> deleteEventListener = (Void aVoid) -> {
        if (iEventHandler != null) {
            iEventHandler.dismissDetailView();
        }
    };


}
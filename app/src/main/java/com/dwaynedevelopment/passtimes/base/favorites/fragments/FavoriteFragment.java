package com.dwaynedevelopment.passtimes.base.favorites.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.dwaynedevelopment.passtimes.R;
import com.dwaynedevelopment.passtimes.base.favorites.adapters.FavoriteViewAdapter;
import com.dwaynedevelopment.passtimes.base.favorites.interfaces.IFavoriteHandler;
import com.dwaynedevelopment.passtimes.models.Player;
import com.dwaynedevelopment.passtimes.models.Sport;
import com.dwaynedevelopment.passtimes.utils.AuthUtils;
import com.dwaynedevelopment.passtimes.utils.FirebaseFirestoreUtils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


import static com.dwaynedevelopment.passtimes.utils.KeyUtils.ACTION_FAVORITE_SELECTED;
import static com.dwaynedevelopment.passtimes.utils.KeyUtils.DATABASE_REFERENCE_SPORTS;
import static com.dwaynedevelopment.passtimes.utils.AlertUtils.invokeSnackBar;
import static com.dwaynedevelopment.passtimes.utils.ViewUtils.parentLayoutStatus;

public class FavoriteFragment extends Fragment {

    private FirebaseFirestoreUtils mDb;
    private AuthUtils mAuth;
    private IFavoriteHandler iFavoriteHandler;
    private FavoritesReceiver favoritesReceiver;
    private final List<DocumentReference> favoriteReferences = new ArrayList<>();


    public static FavoriteFragment newInstance(boolean editFavorites) {

        Bundle args = new Bundle();
        args.putBoolean("ARGS_EDIT_FAVORITES", editFavorites);
        FavoriteFragment fragment = new FavoriteFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof IFavoriteHandler) {
            iFavoriteHandler = (IFavoriteHandler) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorite, container, false);
    }

    private static final String TAG = "FavoriteFragment";
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mDb = FirebaseFirestoreUtils.getInstance();
        mAuth = AuthUtils.getInstance();

        if (getActivity() != null) {
            favoritesReceiver = new FavoritesReceiver();
            IntentFilter actionFilter = new IntentFilter();
            actionFilter.addAction(ACTION_FAVORITE_SELECTED);
            getActivity().registerReceiver(favoritesReceiver, actionFilter);

            mDb.databaseCollection(DATABASE_REFERENCE_SPORTS)
                    .get().addOnCompleteListener(favoriteCompleteListener);

            if (getArguments() != null) {
                boolean editSelectedFavorites = getArguments().getBoolean("ARGS_EDIT_FAVORITES", false);
                if (editSelectedFavorites) {
                    if (mAuth.getCurrentSignedUser().getFavorites() != null) {

                        for (int i = 0; i <mAuth.getCurrentSignedUser().getFavorites().size() ; i++) {
                            mAuth.getCurrentSignedUser().getFavorites().get(i).addSnapshotListener(new EventListener<DocumentSnapshot>() {
                                @Override
                                public void onEvent(@javax.annotation.Nullable DocumentSnapshot documentSnapshot,
                                                    @javax.annotation.Nullable FirebaseFirestoreException e) {
                                    if (documentSnapshot != null) {
                                        final Sport favoritesFromEdit = documentSnapshot.toObject(Sport.class);
                                        if (favoritesFromEdit != null) {
                                            Log.i(TAG, "FAVORITES EDIT: ");
                                        }
                                    }
                                }
                            });
                        }
                    }
                }
            }

            if (getView() != null) {
                Button continueButton = getView().findViewById(R.id.btn_continue);
                continueButton.setOnClickListener(continueListener);
            }
        }
    }

    private final OnCompleteListener<QuerySnapshot> favoriteCompleteListener = task -> {
        ArrayList<Sport> sportsArray = new ArrayList<>();

        if (task.isSuccessful()) {
            for (QueryDocumentSnapshot document : Objects.requireNonNull(task.getResult())) {
                Log.d(TAG, document.getId() + " => " + document.getData());
                sportsArray.add(document.toObject(Sport.class));

                FavoriteViewAdapter adapter = new FavoriteViewAdapter((AppCompatActivity) getActivity(), sportsArray);
                if (getActivity() != null) {
                    if (getView() != null) {
                        RecyclerView recyclerView = getView().findViewById(R.id.rv_favorite);
                        recyclerView.setHasFixedSize(true);
                        recyclerView.setLayoutManager(new GridLayoutManager(getActivity().getApplicationContext(), 3));
                        recyclerView.setAdapter(adapter);
                        adapter.notifyDataSetChanged();
                        Log.i(TAG, "FAVORITES SIGNUP: ");
                    }
                }
            }
        } else {
            Log.d(TAG, "Error getting documents: ", task.getException());
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (getActivity() != null) {
            getActivity().unregisterReceiver(favoritesReceiver);
        }
    }


    private final View.OnClickListener continueListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mAuth.getCurrentSignedUser() != null) {

                RelativeLayout favoriteParentLayout = Objects.requireNonNull(getView()).findViewById(R.id.rl_favorite_parent);
                parentLayoutStatus(favoriteParentLayout, false);

                Player player = mAuth.getCurrentSignedUser();
                player.setFavorites(favoriteReferences);

                if (getView() != null) {
                    final ProgressBar progress = getView().findViewById(R.id.pb_dots_fav);
                    progress.setVisibility(View.VISIBLE);

                    if (player.getFavorites().size() >= 1) {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (iFavoriteHandler != null) {
                                    mDb.insertFavorites(player);
                                    progress.setVisibility(View.GONE);
                                    iFavoriteHandler.dismissActivity();
                                }
                            }
                        }, 1000);
                    } else {
                        Toast.makeText(getContext(), "Please select a sport.", Toast.LENGTH_SHORT).show();
                        progress.setVisibility(View.GONE);
                    }
                }
            }
        }
    };


    public class FavoritesReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<Sport> selectedFavorites = intent.getParcelableArrayListExtra("SELECTED_SPORTS");

            //CLEAR LIST TO AVOID DUPLICATES ENTRIES.
            favoriteReferences.clear();
            for (int i = 0; i < selectedFavorites.size() ; i++) {
                favoriteReferences.add(mDb.getFirestore().document("/"+DATABASE_REFERENCE_SPORTS+"/"+ selectedFavorites.get(i).getId()));
            }
        }
    }
 }

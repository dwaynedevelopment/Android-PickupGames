package com.dwaynedevelopment.passtimes.utils;

import com.dwaynedevelopment.passtimes.Player;
import com.dwaynedevelopment.passtimes.models.Event;
import com.google.firebase.database.FirebaseDatabase;

import static com.dwaynedevelopment.passtimes.utils.KeyUtils.DATABASE_REFERENCE_USERS;

public class DatabaseUtils {

    private FirebaseDatabase database;

    private DatabaseUtils() {
        database = FirebaseDatabase.getInstance();
    }

    private static DatabaseUtils instance = null;

    public static DatabaseUtils getInstance() {
        if(instance == null) {
            instance = new DatabaseUtils();
        }

        return instance;
    }

    public void insertUser(Player player) {
        database.getReference(DATABASE_REFERENCE_USERS).child(player.getId()).setValue(player);
    }

    public void updateImage(Player player) {
        database.getReference(DATABASE_REFERENCE_USERS).child(player.getId()).child("thumbnail").setValue(player.getThumbnail());
    }



    public void addEvent(Event event) {

    }
}

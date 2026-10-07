package com.example.project_gk;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import net.sqlcipher.database.SupportFactory;

@Database(entities = {SecureNote.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract NoteDao noteDao();

    private static volatile AppDatabase INSTANCE_ENCRYPTED = null;
    private static volatile AppDatabase INSTANCE_STANDARD = null;

    public static AppDatabase getEncryptedDatabase(Context context, byte[] passphrase) {
        if (INSTANCE_ENCRYPTED == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE_ENCRYPTED == null) {
                    System.loadLibrary("sqlcipher");
                    SupportFactory factory = new SupportFactory(passphrase);

                    INSTANCE_ENCRYPTED = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "vault.db"
                            )
                            .openHelperFactory(factory)
                            .build();
                }
            }
        }
        return INSTANCE_ENCRYPTED;
    }

    public static AppDatabase getStandardDatabase(Context context) {
        if (INSTANCE_STANDARD == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE_STANDARD == null) {
                    INSTANCE_STANDARD = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "standard_vault.db"
                    ).build();
                }
            }
        }
        return INSTANCE_STANDARD;
    }
}
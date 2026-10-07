package com.example.project_gk;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NoteDao {
    @Insert
    void insertNote(SecureNote note);

    @Insert
    void insertBatch(List<SecureNote> notes);

    @Query("SELECT COUNT(*) FROM secure_notes")
    int getCount();

    @Query("SELECT * FROM secure_notes ORDER BY timestamp DESC")
    List<SecureNote> getAllNotes();

    @Query("DELETE FROM secure_notes")
    void deleteAll();
}
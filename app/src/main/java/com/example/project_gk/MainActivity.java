package com.example.project_gk;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.project_gk.databinding.ActivityMainBinding;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String DEFAULT_MASTER_PIN = "123456";

    private ActivityMainBinding binding;
    private AppDatabase encryptedDb;
    private AppDatabase standardDb;
    private NoteAdapter noteAdapter;
    private AppLifecycleTracker lifecycleTracker;
    private boolean isPinVisible = false;
    private long editingNoteId = -1L;
    private String selectedCategory = "";
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        lifecycleTracker = new AppLifecycleTracker(() -> runOnUiThread(this::lockVault));
        ProcessLifecycleOwner.get().getLifecycle().addObserver(lifecycleTracker);

        setupBackNavigation();
        setupRecyclerView();
        setupSearchAndFilter();
        setupListeners();

        lockVault();
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.layoutAdd.getVisibility() == View.VISIBLE) {
                    resetAddForm();
                    hideKeyboard();
                    binding.layoutAdd.setVisibility(View.GONE);
                    binding.layoutDashboard.setVisibility(View.VISIBLE);
                } else if (binding.layoutDashboard.getVisibility() == View.VISIBLE) {
                    lockVault();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    private void setupRecyclerView() {
        noteAdapter = new NoteAdapter();
        binding.rvNotes.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotes.setAdapter(noteAdapter);
        noteAdapter.setOnItemClickListener(this::openEditNoteScreen);
    }

    private void setupSearchAndFilter() {
        selectedCategory = getString(R.string.filter_all);

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                triggerFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnFilter.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add(0, 0, 0, getString(R.string.filter_all));
            popup.getMenu().add(0, 1, 1, getString(R.string.add_cat_password));
            popup.getMenu().add(0, 2, 2, getString(R.string.add_cat_bank));
            popup.getMenu().add(0, 3, 3, getString(R.string.add_cat_note));
            popup.setOnMenuItemClickListener(item -> {
                CharSequence title = item.getTitle();
                if (title != null) {
                    selectedCategory = title.toString();
                    triggerFilter();
                    Toast.makeText(this, getString(R.string.filter_toast, selectedCategory), Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            popup.show();
        });
    }

    private void triggerFilter() {
        String query = binding.etSearch.getText() != null ? binding.etSearch.getText().toString() : "";
        noteAdapter.applyFilter(query, selectedCategory, this);
    }

    private void setupListeners() {
        binding.btnAuthTouch.setOnClickListener(v -> showBiometricPrompt());

        binding.ivTogglePin.setOnClickListener(v -> {
            isPinVisible = !isPinVisible;
            binding.ivTogglePin.setSelected(isPinVisible);
            if (isPinVisible) {
                binding.etMasterPin.setInputType(InputType.TYPE_CLASS_NUMBER);
            } else {
                binding.etMasterPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            }
            if (binding.etMasterPin.getText() != null) {
                binding.etMasterPin.setSelection(binding.etMasterPin.getText().length());
            }
        });

        binding.btnAuthPin.setOnClickListener(v -> {
            String enteredPin = binding.etMasterPin.getText().toString().trim();

            if (enteredPin.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_pin_empty), Toast.LENGTH_SHORT).show();
                return;
            }

            if (DEFAULT_MASTER_PIN.equals(enteredPin)) {
                hideKeyboard();
                binding.etMasterPin.setText("");
                unlockDatabase();
            } else {
                Toast.makeText(this, getString(R.string.toast_pin_incorrect), Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnLock.setOnClickListener(v -> {
            lockVault();
            Toast.makeText(this, getString(R.string.toast_vault_locked), Toast.LENGTH_SHORT).show();
        });

        binding.fabAdd.setOnClickListener(v -> openAddNoteScreen());

        binding.btnBenchmark.setOnClickListener(v -> {
            if (standardDb != null && encryptedDb != null) {
                Toast.makeText(this, getString(R.string.toast_benchmark_running), Toast.LENGTH_SHORT).show();
                backgroundExecutor.execute(() -> {
                    BenchmarkManager bm = new BenchmarkManager(standardDb, encryptedDb);
                    BenchmarkManager.BenchmarkResult res = bm.runBatchInsertBenchmark(1000);

                    initDefaultDataIfEmpty();

                    runOnUiThread(() -> {
                        Toast.makeText(this, getString(R.string.toast_benchmark_done_format, res.standardDurationMs(), res.sqlCipherDurationMs()), Toast.LENGTH_LONG).show();
                        loadNotesFromDatabase();
                    });
                });
            }
        });

        binding.btnBack.setOnClickListener(v -> {
            hideKeyboard();
            resetAddForm();
            binding.layoutAdd.setVisibility(View.GONE);
            binding.layoutDashboard.setVisibility(View.VISIBLE);
        });

        binding.btnSaveData.setOnClickListener(v -> saveOrUpdateNote());

        binding.btnDeleteNote.setOnClickListener(v -> confirmDeleteNote());
    }

    private void openAddNoteScreen() {
        editingNoteId = -1L;
        resetAddForm();
        binding.tvAddTitle.setText(R.string.add_title);
        binding.btnDeleteNote.setVisibility(View.GONE);
        binding.layoutDashboard.setVisibility(View.GONE);
        binding.layoutAdd.setVisibility(View.VISIBLE);
    }

    private void openEditNoteScreen(SecureNote note) {
        editingNoteId = note.getId();
        binding.tvAddTitle.setText(R.string.add_title_edit);
        binding.etTitleInput.setText(note.getTitle());
        binding.etContentInput.setText(note.getSecretContent());

        String bankCat = getString(R.string.add_cat_bank);
        String noteCat = getString(R.string.add_cat_note);

        if (bankCat.equalsIgnoreCase(note.getCategory()) || "Thẻ ngân hàng".equalsIgnoreCase(note.getCategory())) {
            binding.rbBank.setChecked(true);
        } else if (noteCat.equalsIgnoreCase(note.getCategory()) || "Ghi chú".equalsIgnoreCase(note.getCategory())) {
            binding.rbNote.setChecked(true);
        } else {
            binding.rbPassword.setChecked(true);
        }

        binding.btnDeleteNote.setVisibility(View.VISIBLE);
        binding.layoutDashboard.setVisibility(View.GONE);
        binding.layoutAdd.setVisibility(View.VISIBLE);
    }

    private void resetAddForm() {
        editingNoteId = -1L;
        binding.etTitleInput.setText("");
        binding.etContentInput.setText("");
        binding.rbPassword.setChecked(true);
    }

    private void saveOrUpdateNote() {
        String title = binding.etTitleInput.getText().toString().trim();
        String content = binding.etContentInput.getText().toString().trim();

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_input_required), Toast.LENGTH_SHORT).show();
            return;
        }

        hideKeyboard();

        String category = getString(R.string.add_cat_password);
        if (binding.rbBank.isChecked()) {
            category = getString(R.string.add_cat_bank);
        } else if (binding.rbNote.isChecked()) {
            category = getString(R.string.add_cat_note);
        }

        final String finalCategory = category;

        backgroundExecutor.execute(() -> {
            if (editingNoteId != -1L) {
                SecureNote note = new SecureNote(title, finalCategory, content);
                note.setId(editingNoteId);
                note.setTimestamp(System.currentTimeMillis());
                if (encryptedDb != null) {
                    encryptedDb.noteDao().updateNote(note);
                }
            } else {
                SecureNote note = new SecureNote(title, finalCategory, content);
                if (encryptedDb != null) {
                    encryptedDb.noteDao().insertNote(note);
                }
            }

            runOnUiThread(() -> {
                boolean wasEdit = (editingNoteId != -1L);
                resetAddForm();
                binding.layoutAdd.setVisibility(View.GONE);
                binding.layoutDashboard.setVisibility(View.VISIBLE);
                loadNotesFromDatabase();

                String msg = wasEdit ? getString(R.string.toast_update_success) : getString(R.string.toast_save_success);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void confirmDeleteNote() {
        if (editingNoteId == -1L) return;

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_btn_delete)
                .setMessage(R.string.dialog_delete_confirm)
                .setPositiveButton(R.string.dialog_btn_delete, (dialog, which) -> backgroundExecutor.execute(() -> {
                    if (encryptedDb != null) {
                        encryptedDb.noteDao().deleteNoteById(editingNoteId);
                    }
                    runOnUiThread(() -> {
                        resetAddForm();
                        binding.layoutAdd.setVisibility(View.GONE);
                        binding.layoutDashboard.setVisibility(View.VISIBLE);
                        loadNotesFromDatabase();
                        Toast.makeText(this, getString(R.string.toast_delete_success), Toast.LENGTH_SHORT).show();
                    });
                }))
                .setNegativeButton(R.string.dialog_btn_close, null)
                .show();
    }

    private void showBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setSubtitle(getString(R.string.biometric_prompt_subtitle))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        unlockDatabase();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Toast.makeText(MainActivity.this, getString(R.string.biometric_error_prefix) + errString, Toast.LENGTH_SHORT).show();
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }

    private void unlockDatabase() {
        backgroundExecutor.execute(() -> {
            VaultAuthRepository repo = new VaultAuthRepository(getApplicationContext());
            byte[] passphrase = repo.getOrCreateDatabasePassphrase();

            encryptedDb = AppDatabase.getEncryptedDatabase(getApplicationContext(), passphrase);
            standardDb = AppDatabase.getStandardDatabase(getApplicationContext());

            initDefaultDataIfEmpty();

            runOnUiThread(() -> {
                if (lifecycleTracker != null) {
                    lifecycleTracker.setUnlocked(true);
                }
                binding.layoutAuth.setVisibility(View.GONE);
                binding.layoutAdd.setVisibility(View.GONE);
                binding.layoutDashboard.setVisibility(View.VISIBLE);
                loadNotesFromDatabase();
            });
        });
    }

    private void initDefaultDataIfEmpty() {
        int count = encryptedDb.noteDao().getCount();
        if (count == 0 || count > 50) {
            encryptedDb.noteDao().deleteAll();
            encryptedDb.noteDao().insertNote(new SecureNote(
                    getString(R.string.default_bank_title),
                    getString(R.string.add_cat_bank),
                    getString(R.string.default_bank_content)));
            encryptedDb.noteDao().insertNote(new SecureNote(
                    getString(R.string.default_pass_title),
                    getString(R.string.add_cat_password),
                    getString(R.string.default_pass_content)));
            encryptedDb.noteDao().insertNote(new SecureNote(
                    getString(R.string.default_note_title),
                    getString(R.string.add_cat_note),
                    getString(R.string.default_note_content)));
        }
    }

    private void loadNotesFromDatabase() {
        if (encryptedDb == null) return;
        backgroundExecutor.execute(() -> {
            List<SecureNote> notes = encryptedDb.noteDao().getAllNotes();
            runOnUiThread(() -> {
                noteAdapter.setNotes(notes);
                triggerFilter();
                binding.tvCount.setText(getResources().getQuantityString(R.plurals.dash_count, notes.size(), notes.size()));
            });
        });
    }

    private void lockVault() {
        encryptedDb = null;
        if (lifecycleTracker != null) {
            lifecycleTracker.setUnlocked(false);
        }

        if (binding != null) {
            binding.etMasterPin.setText("");
            binding.etMasterPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            binding.ivTogglePin.setSelected(false);
            isPinVisible = false;
        }

        hideKeyboard();

        if (binding != null) {
            binding.layoutDashboard.setVisibility(View.GONE);
            binding.layoutAdd.setVisibility(View.GONE);
            binding.layoutAuth.setVisibility(View.VISIBLE);
        }
    }

    private void hideKeyboard() {
        InputMethodManager immReal = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (immReal != null && getCurrentFocus() != null) {
            immReal.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lifecycleTracker != null) {
            lifecycleTracker.shutdown();
        }
        backgroundExecutor.shutdown();
    }
}
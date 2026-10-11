package com.example.project_gk;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
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
    private static final int MAX_PIN_ATTEMPTS = 5;
    private static final long PIN_LOCKOUT_MS = 30_000L;
    private static final String PREFS_SECURITY = "vault_security";
    private static final String KEY_FAILED_ATTEMPTS = "pin_failed_attempts";
    private static final String KEY_LOCKOUT_UNTIL = "pin_lockout_until";

    private ActivityMainBinding binding;
    private AppDatabase encryptedDb;
    private AppDatabase standardDb;
    private NoteAdapter noteAdapter;
    private AppLifecycleTracker lifecycleTracker;
    private boolean isPinVisible = false;
    private SharedPreferences securityPrefs;
    private long editingNoteId = -1L;
    private String selectedCategory = "";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Chống chụp/quay màn hình (FLAG_SECURE)
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        securityPrefs = getSharedPreferences(PREFS_SECURITY, MODE_PRIVATE);

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Tự động khóa kho dữ liệu khi ẩn app
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
                    crossFade(binding.layoutAdd, binding.layoutDashboard);
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
        binding.rvNotes.setHasFixedSize(true);
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
        // --- Màn hình khóa ---
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
            if (isPinLockedOut()) {
                Toast.makeText(this, getString(R.string.toast_pin_lockout), Toast.LENGTH_SHORT).show();
                return;
            }

            String enteredPin = binding.etMasterPin.getText().toString().trim();

            if (enteredPin.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_pin_empty), Toast.LENGTH_SHORT).show();
                return;
            }

            if (DEFAULT_MASTER_PIN.equals(enteredPin)) {
                resetFailedAttempts();
                hideKeyboard();
                binding.etMasterPin.setText("");
                unlockDatabase();
            } else {
                registerFailedAttempt();
                if (isPinLockedOut()) {
                    Toast.makeText(this, getString(R.string.toast_pin_lockout), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.toast_pin_incorrect_format, remainingAttempts()), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // --- Dashboard ---
        binding.btnLock.setOnClickListener(v -> {
            lockVault();
            Toast.makeText(this, getString(R.string.toast_vault_locked), Toast.LENGTH_SHORT).show();
        });

        binding.fabAdd.setOnClickListener(v -> openAddNoteScreen());

        binding.btnBenchmark.setOnClickListener(v -> {
            if (standardDb != null && encryptedDb != null) {
                Toast.makeText(this, getString(R.string.toast_benchmark_running), Toast.LENGTH_SHORT).show();
                backgroundExecutor.execute(() -> {
                    // Benchmark ghi/xóa trên chính bảng secure_notes -> phải sao lưu dữ liệu người dùng trước khi đo
                    List<SecureNote> backup = encryptedDb.noteDao().getAllNotes();

                    BenchmarkManager bm = new BenchmarkManager(standardDb, encryptedDb);
                    BenchmarkManager.BenchmarkResult res = bm.runBatchInsertBenchmark(1000);

                    // Phục hồi nguyên trạng dữ liệu đã sao lưu
                    encryptedDb.noteDao().deleteAll();
                    if (!backup.isEmpty()) {
                        encryptedDb.noteDao().insertBatch(backup);
                    }

                    runOnUiThread(() -> {
                        Toast.makeText(this, getString(R.string.toast_benchmark_done_format, res.standardDurationMs(), res.sqlCipherDurationMs()), Toast.LENGTH_LONG).show();
                        loadNotesFromDatabase();
                    });
                });
            }
        });

        // --- Màn hình thêm / sửa ---
        binding.btnBack.setOnClickListener(v -> {
            hideKeyboard();
            resetAddForm();
            crossFade(binding.layoutAdd, binding.layoutDashboard);
        });

        binding.btnSaveData.setOnClickListener(v -> saveOrUpdateNote());

        binding.btnDeleteNote.setOnClickListener(v -> confirmDeleteNote());
    }

    private void openAddNoteScreen() {
        editingNoteId = -1L;
        resetAddForm();
        binding.tvAddTitle.setText(R.string.add_title);
        binding.btnDeleteNote.setVisibility(View.GONE);
        crossFade(binding.layoutDashboard, binding.layoutAdd);
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
        crossFade(binding.layoutDashboard, binding.layoutAdd);
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
        final boolean autoClearClipboard = binding.cbAutoClearClipboard.isChecked();

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

            if (autoClearClipboard) {
                runOnUiThread(() -> copyToClipboardWithAutoClear(content));
            }

            runOnUiThread(() -> {
                boolean wasEdit = (editingNoteId != -1L);
                resetAddForm();
                crossFade(binding.layoutAdd, binding.layoutDashboard);
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
                        crossFade(binding.layoutAdd, binding.layoutDashboard);
                        loadNotesFromDatabase();
                        Toast.makeText(this, getString(R.string.toast_delete_success), Toast.LENGTH_SHORT).show();
                    });
                }))
                .setNegativeButton(R.string.dialog_btn_close, null)
                .show();
    }

    // Chuyển màn hình (cross-fade)
    private void crossFade(View fromView, View toView) {
        toView.setAlpha(0f);
        toView.setVisibility(View.VISIBLE);

        toView.animate()
                .alpha(1f)
                .setDuration(220)
                .setListener(null);

        fromView.animate()
                .alpha(0f)
                .setDuration(220)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        fromView.setVisibility(View.GONE);
                    }
                });
    }

    // Copy rồi tự xóa Clipboard sau 30 giây
    private void copyToClipboardWithAutoClear(String secretText) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("SecureVault Data", secretText);
            clipboard.setPrimaryClip(clip);

            mainHandler.postDelayed(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip();
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                    }
                    Toast.makeText(getApplicationContext(), "Clipboard đã được xóa tự động!", Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
            }, 30000);
        }
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

            // Ghi log chuỗi chứng thư attestation của khóa Keystore
            KeyStoreManager.logKeyAttestation();

            initDefaultDataIfEmpty();

            runOnUiThread(() -> {
                if (lifecycleTracker != null) {
                    lifecycleTracker.setUnlocked(true);
                }
                crossFade(binding.layoutAuth, binding.layoutDashboard);
                loadNotesFromDatabase();
            });
        });
    }

    private void initDefaultDataIfEmpty() {
        int count = encryptedDb.noteDao().getCount();
        if (count == 0) {
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

    private boolean isPinLockedOut() {
        return System.currentTimeMillis() < securityPrefs.getLong(KEY_LOCKOUT_UNTIL, 0L);
    }

    private int remainingAttempts() {
        return Math.max(0, MAX_PIN_ATTEMPTS - securityPrefs.getInt(KEY_FAILED_ATTEMPTS, 0));
    }

    private void registerFailedAttempt() {
        int attempts = securityPrefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
        SharedPreferences.Editor editor = securityPrefs.edit();
        if (attempts >= MAX_PIN_ATTEMPTS) {
            editor.putInt(KEY_FAILED_ATTEMPTS, 0);
            editor.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + PIN_LOCKOUT_MS);
        } else {
            editor.putInt(KEY_FAILED_ATTEMPTS, attempts);
        }
        editor.apply();
    }

    private void resetFailedAttempts() {
        securityPrefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply();
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
            binding.layoutAuth.setAlpha(1.0f);
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
        if (encryptedDb != null) {
            encryptedDb.close();
            encryptedDb = null;
        }
        if (lifecycleTracker != null) {
            lifecycleTracker.shutdown();
        }
        backgroundExecutor.shutdown();
    }
}
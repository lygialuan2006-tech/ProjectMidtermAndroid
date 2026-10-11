package com.example.project_gk;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Chống quay video / chụp màn hình (FLAG_SECURE)
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

        // 2. Đăng ký theo dõi vòng đời: Khóa app khi ra ngoài
        lifecycleTracker = new AppLifecycleTracker(() -> runOnUiThread(this::lockVault));
        ProcessLifecycleOwner.get().getLifecycle().addObserver(lifecycleTracker);

        setupRecyclerView();
        setupListeners();

        // 3. Mặc định luôn ở trạng thái khóa ban đầu và xóa trắng ô PIN
        lockVault();
    }

    private void setupRecyclerView() {
        noteAdapter = new NoteAdapter();
        binding.rvNotes.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotes.setAdapter(noteAdapter);

        // Xem và sao chép nội dung giải mã khi nhấn vào từng thẻ
        noteAdapter.setOnItemClickListener(this::showNoteDetailDialog);
    }

    private void setupListeners() {
        // --- 1. MÀN HÌNH KHÓA (AUTH) ---
        binding.btnAuthTouch.setOnClickListener(v -> showBiometricPrompt());

        // Bật / tắt ẩn hiện Master PIN khi bấm icon con mắt
        binding.ivTogglePin.setOnClickListener(v -> {
            if (isPinVisible) {
                binding.etMasterPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                binding.ivTogglePin.setAlpha(0.6f);
                isPinVisible = false;
            } else {
                binding.etMasterPin.setInputType(InputType.TYPE_CLASS_NUMBER);
                binding.ivTogglePin.setAlpha(1.0f);
                isPinVisible = true;
            }
            if (binding.etMasterPin.getText() != null) {
                binding.etMasterPin.setSelection(binding.etMasterPin.getText().length());
            }
        });

        // Xác thực bằng Master PIN
        binding.btnAuthPin.setOnClickListener(v -> {
            String enteredPin = binding.etMasterPin.getText().toString().trim();

            if (enteredPin.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_pin_empty), Toast.LENGTH_SHORT).show();
                return;
            }

            if (DEFAULT_MASTER_PIN.equals(enteredPin)) {
                hideKeyboard();
                binding.etMasterPin.setText(""); // Xóa trắng PIN sau khi mở thành công
                unlockDatabase();
            } else {
                Toast.makeText(this, getString(R.string.toast_pin_incorrect), Toast.LENGTH_SHORT).show();
            }
        });

        // --- 2. MÀN HÌNH DASHBOARD ---
        // Nút Khóa: Đóng database, xóa trắng PIN và trở về màn hình xác thực
        binding.btnLock.setOnClickListener(v -> {
            lockVault();
            Toast.makeText(this, getString(R.string.toast_vault_locked), Toast.LENGTH_SHORT).show();
        });

        // Nút thêm mới bản ghi (+)
        binding.fabAdd.setOnClickListener(v -> {
            binding.layoutDashboard.setVisibility(View.GONE);
            binding.layoutAdd.setVisibility(View.VISIBLE);
        });

        // Chạy kiểm thử hiệu năng Benchmark độc lập
        binding.btnBenchmark.setOnClickListener(v -> {
            if (standardDb != null && encryptedDb != null) {
                Toast.makeText(this, getString(R.string.toast_benchmark_running), Toast.LENGTH_SHORT).show();
                backgroundExecutor.execute(() -> {
                    BenchmarkManager bm = new BenchmarkManager(standardDb, encryptedDb);
                    BenchmarkManager.BenchmarkResult res = bm.runBatchInsertBenchmark(1000);

                    // Đảm bảo dữ liệu hiển thị không bị mất
                    initDefaultDataIfEmpty();

                    runOnUiThread(() -> {
                        Toast.makeText(this, getString(R.string.toast_benchmark_done_format, res.standardDurationMs, res.sqlCipherDurationMs), Toast.LENGTH_LONG).show();
                        loadNotesFromDatabase();
                    });
                });
            }
        });

        // --- 3. MÀN HÌNH THÊM MỚI ---
        binding.btnBack.setOnClickListener(v -> {
            hideKeyboard();
            binding.layoutAdd.setVisibility(View.GONE);
            binding.layoutDashboard.setVisibility(View.VISIBLE);
        });

        binding.btnSaveData.setOnClickListener(v -> {
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
                SecureNote note = new SecureNote(title, finalCategory, content);
                if (encryptedDb != null) {
                    encryptedDb.noteDao().insertNote(note);
                }
                if (standardDb != null) {
                    standardDb.noteDao().insertNote(note);
                }

                runOnUiThread(() -> {
                    binding.etTitleInput.setText("");
                    binding.etContentInput.setText("");
                    binding.rbPassword.setChecked(true);

                    binding.layoutAdd.setVisibility(View.GONE);
                    binding.layoutDashboard.setVisibility(View.VISIBLE);
                    loadNotesFromDatabase();

                    Toast.makeText(this, getString(R.string.toast_save_success), Toast.LENGTH_SHORT).show();
                });
            });
        });
    }

    private void showNoteDetailDialog(SecureNote note) {
        new AlertDialog.Builder(this)
                .setTitle(note.getTitle())
                .setMessage(getString(R.string.dialog_detail_category) + note.getCategory() + getString(R.string.dialog_detail_content) + note.getSecretContent())
                .setPositiveButton(getString(R.string.dialog_btn_copy), (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("SecureVault", note.getSecretContent());
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, getString(R.string.toast_copy_success), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.dialog_btn_close), null)
                .show();
    }

    private void showBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setSubtitle(getString(R.string.biometric_prompt_subtitle))
                .setDeviceCredentialAllowed(true)
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

            // Tự dọn sạch dữ liệu rác nếu có và nạp 3 bản ghi mẫu ban đầu
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
        // Nếu database trống hoặc đang bị kẹt > 50 bản ghi thử nghiệm cũ: làm sạch và nạp lại chuẩn
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
                binding.tvCount.setText(getString(R.string.dash_count_format, notes.size()));
            });
        });
    }

    private void lockVault() {
        encryptedDb = null;
        if (lifecycleTracker != null) {
            lifecycleTracker.setUnlocked(false);
        }

        // Xóa hoàn toàn ký tự PIN trong ô nhập
        if (binding != null && binding.etMasterPin != null) {
            binding.etMasterPin.setText("");
            // Reset chế độ xem về dạng mật khẩu ẩn
            binding.etMasterPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            binding.ivTogglePin.setAlpha(0.6f);
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
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundExecutor.shutdown();
    }
}
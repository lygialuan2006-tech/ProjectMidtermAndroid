package com.example.project_gk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BenchmarkManager {
    private static final int ROUNDS = 5;

    private final AppDatabase standardDb;
    private final AppDatabase encryptedDb;

    public record BenchmarkResult(long standardDurationMs, long sqlCipherDurationMs) {
    }

    public BenchmarkManager(AppDatabase standardDb, AppDatabase encryptedDb) {
        this.standardDb = standardDb;
        this.encryptedDb = encryptedDb;
    }

    public BenchmarkResult runBatchInsertBenchmark(int count) {
        List<SecureNote> sampleList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sampleList.add(new SecureNote("Test #" + i, "Bảo mật", "TestContent123456"));
        }

        // Warm-up: nạp trang DB & JIT trước khi đo
        standardDb.noteDao().insertBatch(sampleList);
        standardDb.noteDao().deleteAll();
        encryptedDb.noteDao().insertBatch(sampleList);
        encryptedDb.noteDao().deleteAll();

        long[] standardTimes = new long[ROUNDS];
        long[] encryptedTimes = new long[ROUNDS];

        // Đo 5 vòng rồi lấy trung vị (median) để giảm sai số do cache/GC/nhiệt
        for (int r = 0; r < ROUNDS; r++) {
            long startStandard = System.nanoTime();
            standardDb.noteDao().insertBatch(sampleList);
            standardTimes[r] = (System.nanoTime() - startStandard) / 1_000_000;
            standardDb.noteDao().deleteAll();

            long startEncrypted = System.nanoTime();
            encryptedDb.noteDao().insertBatch(sampleList);
            encryptedTimes[r] = (System.nanoTime() - startEncrypted) / 1_000_000;
            encryptedDb.noteDao().deleteAll();
        }

        return new BenchmarkResult(median(standardTimes), median(encryptedTimes));
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
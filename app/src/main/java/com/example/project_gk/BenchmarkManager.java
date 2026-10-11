package com.example.project_gk;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkManager {
    private final AppDatabase standardDb;
    private final AppDatabase encryptedDb;

    public static class BenchmarkResult {
        public final long standardDurationMs;
        public final long sqlCipherDurationMs;

        public BenchmarkResult(long standardDurationMs, long sqlCipherDurationMs) {
            this.standardDurationMs = standardDurationMs;
            this.sqlCipherDurationMs = sqlCipherDurationMs;
        }
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

        long startStandard = System.currentTimeMillis();
        standardDb.noteDao().insertBatch(sampleList);
        long durStandard = System.currentTimeMillis() - startStandard;
        standardDb.noteDao().deleteAll();

        long startEncrypted = System.currentTimeMillis();
        encryptedDb.noteDao().insertBatch(sampleList);
        long durEncrypted = System.currentTimeMillis() - startEncrypted;
        encryptedDb.noteDao().deleteAll();

        return new BenchmarkResult(durStandard, durEncrypted);
    }
}
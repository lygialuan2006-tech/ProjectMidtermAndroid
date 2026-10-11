package com.example.project_gk;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

public class AppLifecycleTracker implements DefaultLifecycleObserver {

    public interface LockListener {
        void onAppBackgrounded();
    }

    private static final long LOCK_GRACE_MS = 60_000L; // Grace period 1 phút

    private final LockListener lockListener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isUnlocked = false;
    private boolean isPendingLock = false;

    private final Runnable lockRunnable = new Runnable() {
        @Override
        public void run() {
            if (isUnlocked && lockListener != null) {
                isUnlocked = false;
                isPendingLock = false;
                lockListener.onAppBackgrounded();
            }
        }
    };

    public AppLifecycleTracker(LockListener lockListener) {
        this.lockListener = lockListener;
    }

    public void setUnlocked(boolean unlocked) {
        this.isUnlocked = unlocked;
        if (!unlocked) {
            cancelPendingLock();
        }
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        // Nếu người dùng quay lại trong vòng 60 giây, hủy lệnh đếm ngược khóa
        if (isPendingLock) {
            cancelPendingLock();
        }
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        // Chỉ đặt đếm ngược khóa nếu ứng dụng đang mở khóa
        if (isUnlocked && !isPendingLock) {
            isPendingLock = true;
            handler.postDelayed(lockRunnable, LOCK_GRACE_MS);
        }
    }

    private void cancelPendingLock() {
        isPendingLock = false;
        handler.removeCallbacks(lockRunnable);
    }

    public void shutdown() {
        cancelPendingLock();
        isUnlocked = false;
    }
}
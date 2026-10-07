package com.example.project_gk;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

public class AppLifecycleTracker implements DefaultLifecycleObserver {

    public interface LockListener {
        void onAppBackgrounded();
    }

    private final LockListener lockListener;
    private boolean isUnlocked = false;

    public AppLifecycleTracker(LockListener lockListener) {
        this.lockListener = lockListener;
    }

    public void setUnlocked(boolean unlocked) {
        this.isUnlocked = unlocked;
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        // Chỉ kích hoạt khóa nếu kho dữ liệu đang ở trạng thái đã mở khóa
        if (isUnlocked && lockListener != null) {
            isUnlocked = false;
            lockListener.onAppBackgrounded();
        }
    }
}
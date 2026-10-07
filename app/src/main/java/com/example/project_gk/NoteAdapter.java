package com.example.project_gk;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(SecureNote note);
    }

    private List<SecureNote> notes = new ArrayList<>();
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setNotes(List<SecureNote> notes) {
        this.notes = notes;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        SecureNote note = notes.get(position);
        holder.tvTitle.setText(note.getTitle());

        // Gán Icon và Phân loại trường thông tin đúng thiết kế
        if ("Thẻ ngân hàng".equalsIgnoreCase(note.getCategory())) {
            holder.tvCategoryIcon.setText("🏛️");
            holder.tvSubtitle.setText("Số tài khoản · Mã PIN · 2 trường");
        } else if ("Mật khẩu".equalsIgnoreCase(note.getCategory())) {
            holder.tvCategoryIcon.setText("🔑");
            holder.tvSubtitle.setText("Email · Mật khẩu · Mã 2FA");
        } else {
            holder.tvCategoryIcon.setText("📝");
            holder.tvSubtitle.setText("Mã khôi phục · Ghi chú bảo mật");
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault());
        holder.tvDate.setText("CẬP NHẬT " + sdf.format(new Date(note.getTimestamp())));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(note);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategoryIcon, tvTitle, tvSubtitle, tvDate;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryIcon = itemView.findViewById(R.id.tvCategoryIcon);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            tvDate = itemView.findViewById(R.id.tvDate);
        }
    }
}
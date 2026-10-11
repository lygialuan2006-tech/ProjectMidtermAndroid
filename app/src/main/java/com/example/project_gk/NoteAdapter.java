package com.example.project_gk;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
        Context context = holder.itemView.getContext();
        SecureNote note = notes.get(position);
        holder.tvTitle.setText(note.getTitle());

        String bankCat = context.getString(R.string.add_cat_bank);
        String passCat = context.getString(R.string.add_cat_password);

        // Gán Icon và Phân loại trường thông tin đúng thiết kế
        if (bankCat.equalsIgnoreCase(note.getCategory()) || "Thẻ ngân hàng".equalsIgnoreCase(note.getCategory())) {
            holder.ivCategoryIcon.setImageResource(R.drawable.ic_credit_card);
            holder.tvSubtitle.setText(R.string.item_sub_bank);
        } else if (passCat.equalsIgnoreCase(note.getCategory()) || "Mật khẩu".equalsIgnoreCase(note.getCategory())) {
            holder.ivCategoryIcon.setImageResource(R.drawable.ic_key);
            holder.tvSubtitle.setText(R.string.item_sub_password);
        } else {
            holder.ivCategoryIcon.setImageResource(R.drawable.ic_note);
            holder.tvSubtitle.setText(R.string.item_sub_note);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault());
        holder.tvDate.setText(context.getString(R.string.item_date_prefix) + sdf.format(new Date(note.getTimestamp())));

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
        ImageView ivCategoryIcon;
        TextView tvTitle, tvSubtitle, tvDate;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCategoryIcon = itemView.findViewById(R.id.ivCategoryIcon);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            tvDate = itemView.findViewById(R.id.tvDate);
        }
    }
}
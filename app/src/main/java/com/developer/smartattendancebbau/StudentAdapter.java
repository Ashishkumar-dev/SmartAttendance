package com.developer.smartattendancebbau;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.ViewHolder> {
    private final List<Student> studentList;

    public StudentAdapter(List<Student> studentList) {
        this.studentList = studentList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Student student = studentList.get(position);
        holder.nameTextView.setText("Name: " + student.getName());
        holder.rollNumberTextView.setText("Roll No: " + student.getRollNumber());
        holder.statusTextView.setText("Status: " + student.getStatus());
        if(student.getStatus().equalsIgnoreCase("present"))
        {
            holder.statusTextView.setTextColor(Color.GREEN);
        }
        else
        {
            holder.statusTextView.setTextColor(Color.RED);
        }
        holder.dateTextView.setText("Date: " + student.getDate());
    }

    @Override
    public int getItemCount() {
        return studentList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView, rollNumberTextView, statusTextView, dateTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.nameTextView);
            rollNumberTextView = itemView.findViewById(R.id.rollNumberTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);
            dateTextView = itemView.findViewById(R.id.dateTextView2);
        }
    }
}

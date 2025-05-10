package com.developer.smartattendancebbau;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintManager;
import android.util.Log;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class viewattendanceActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private StudentAdapter adapter;
    private List<Student> studentList;
    private ProgressBar progressBar;
    private DatabaseReference databaseReference;
    private TextView dateTextView;
    private TextView totalPresents;
    private EditText studentidtextview;
    private ImageView calendarIcon;
    private ImageView searchmenuicon;
    private String selectedDate;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_viewattendance);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UI components
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        progressBar = findViewById(R.id.progressBar); // Ensure you have a ProgressBar in XML
        searchmenuicon = findViewById(R.id.searchmenuicon);
        studentidtextview = findViewById(R.id.studentidtextview);
        totalPresents = findViewById(R.id.Totalpresent);

        studentList = new ArrayList<>();
        adapter = new StudentAdapter(studentList);
        recyclerView.setAdapter(adapter);


        dateTextView = findViewById(R.id.dateTextView2);
        calendarIcon = findViewById(R.id.calendarIcon);

        // Get current date
        Calendar calendar = Calendar.getInstance();
        selectedDate = dateFormat.format(calendar.getTime()); // Default date is today
        dateTextView.setText("Selected Date: " + selectedDate);

        databaseReference = FirebaseDatabase.getInstance().getReference("students");
        ImageView btnPrint = findViewById(R.id.btnPrintAttendance);
        btnPrint.setOnClickListener(v -> {
            printRecyclerView(recyclerView); // replace with your RecyclerView variable
        });

        // OnBackButtonPress
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    finish();
                }
            });
        }
        else{
            onBackPressed();
        }
        fetchStudents();
        searchmenuicon.setOnClickListener(v -> {
            String rollNumber = studentidtextview.getText().toString().trim();
            if (!rollNumber.isEmpty()) {
                fetchStudentsAttendanceByid(rollNumber);
            }
            else {
                Toast.makeText(getApplicationContext(),"Enter Roll Number",Toast.LENGTH_SHORT).show();
            }
        });
        // Open DatePickerDialog when the calendar icon is clicked
        calendarIcon.setOnClickListener(v -> showDatePicker());
        dateTextView.setOnClickListener(v -> showDatePicker());

    }

    private void printRecyclerView(RecyclerView recyclerView) {
        PrintManager printManager = (PrintManager) this.getSystemService(Context.PRINT_SERVICE);
        printManager.print("Attendance_Print", new RecyclerViewPrintAdapter(this, recyclerView), null);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private void fetchStudentsAttendanceByid(String rollNumber) {

        progressBar.setVisibility(View.VISIBLE); // Show ProgressBar
        studentList.clear();
        int[] presentCount = {0};
        DatabaseReference studentRef = FirebaseDatabase.getInstance().getReference("students").child(rollNumber);
        studentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date startDate, endDate;
                try {
                    startDate = sdf.parse(selectedDate);
                    endDate = new Date();

                } catch (ParseException e) {
                    e.printStackTrace();
                    return;
                }
                for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                    String Recorddate = dateSnapshot.getKey();
                    try {
                        Date record = sdf.parse(Recorddate);
                        if (record != null && !record.before(startDate) && !record.after(endDate)) {
                            String date = dateSnapshot.child("date").getValue(String.class);
                            String name = dateSnapshot.child("name").getValue(String.class);
                            String rollNumber = dateSnapshot.child("rollNumber").getValue(String.class);
                            String status = dateSnapshot.child("status").getValue(String.class);
                            if ("present".equalsIgnoreCase(status)) {
                                presentCount[0]++;
                            }
                            studentList.add(new Student(name, rollNumber, status, date));


                        }

                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }

                adapter.notifyDataSetChanged();
                totalPresents.setVisibility(View.VISIBLE);
                totalPresents.setText("Total Presents: " + presentCount[0]);
                progressBar.setVisibility(View.GONE); // Hide ProgressBar
            }
                else{
                    Toast.makeText(getApplicationContext(),"Student details not found",Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE); // Hide ProgressBar
                    totalPresents.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase Error","Error: ",error.toException());
                progressBar.setVisibility(View.GONE); // Hide ProgressBar
            }
        });
    }



    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (DatePicker view, int selectedYear, int selectedMonth, int selectedDay) -> {
            // Format selected date as yyyy-MM-dd
            Calendar selectedCalendar = Calendar.getInstance();
            selectedCalendar.set(selectedYear, selectedMonth, selectedDay);
            selectedDate = dateFormat.format(selectedCalendar.getTime());

            // Update TextView
            dateTextView.setText("Selected Date: " + selectedDate);

            // Fetch students for the new selected date
            fetchStudents();
        }, year, month, day);

        datePickerDialog.show();
    }

    private void fetchStudents() {
        totalPresents.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE); // Show ProgressBar
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                studentList.clear();

                for (DataSnapshot studentSnapshot : snapshot.getChildren()) {
                    String studentId = studentSnapshot.getKey();
                    DataSnapshot attendanceSnapshot = studentSnapshot.child(selectedDate);

                    if (attendanceSnapshot.exists()) {
                        String name = attendanceSnapshot.child("name").getValue(String.class);
                        String rollNumber = attendanceSnapshot.child("rollNumber").getValue(String.class);
                        String status = attendanceSnapshot.child("status").getValue(String.class);
                        String date = attendanceSnapshot.child("date").getValue(String.class);

                        Student student = new Student(name, rollNumber, status, date);
                        studentList.add(student);
                    }
                }

                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE); // Hide ProgressBar
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE); // Hide ProgressBar
                Log.e("FirebaseError", "Failed to fetch students", error.toException());
            }
        });
    }
}

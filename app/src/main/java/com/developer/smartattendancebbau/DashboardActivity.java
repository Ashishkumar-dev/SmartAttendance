package com.developer.smartattendancebbau;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {
    private TextView textViewAttendancePercentage;
    private CircularProgressIndicator progressIndicator;
    private DatabaseReference studentsRef;
    private String currentDate;
    private DatabaseReference adminRef;
    private FirebaseFirestore firestore;
    private Button takeattendance;
    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            Button addButton = findViewById(R.id.addbtn);
            textViewAttendancePercentage = findViewById(R.id.attendancePercentage);
            Button saveLocation = findViewById(R.id.setLocation);
            progressIndicator = findViewById(R.id.circularProgress);
            CardView cardView = findViewById(R.id.attendanceCard);
            cardView.setCardBackgroundColor(Color.TRANSPARENT);


            // Get current date
            currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            // Firebase reference to students node
            studentsRef = FirebaseDatabase.getInstance().getReference("students");
            // Firebase reference to admin node
            firestore = FirebaseFirestore.getInstance();
            adminRef = FirebaseDatabase.getInstance().getReference("admin").child("attendance");

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
            fetchattendanceStatus();
            calculateAttendancePercentage();
            saveLocation.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    checkAndRequestLocationPermission();
                }
            });

            addButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(DashboardActivity.this, managestudentActivity.class);
                    startActivity(intent);
                }
            });
            Button viewButton = findViewById(R.id.viewbtn);
            viewButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(DashboardActivity.this, viewattendanceActivity.class);
                    startActivity(intent);
                }
            });
            takeattendance = findViewById(R.id.attendancebtn);
            takeattendance.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String btnText = takeattendance.getText().toString();
                    if (btnText.contains("Start Attendance")) {
                        takeattendance.setText("Stop Attendance");
                        adminRef.setValue(true).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(getApplicationContext(), "Attendance started", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getApplicationContext(), "Failed to start attendance", Toast.LENGTH_SHORT).show();
                            }

                        });
                    } else {
                        takeattendance.setText("Start Attendance");
                        adminRef.setValue(false).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(getApplicationContext(), "Attendance stopped", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getApplicationContext(), "Failed to stop attendance", Toast.LENGTH_SHORT).show();
                            }

                        });
                    }
                }
            });
            Button captureButton = findViewById(R.id.captureface);
            captureButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(DashboardActivity.this, capturestudent.class);
                    intent.putExtra("previous","dashboard");
                    startActivity(intent);
                }
            });
            return insets;
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private void checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);

        } else {
            getCurrentLocationAndSave();
        }
    }

    @SuppressLint("MissingPermission")
    private void getCurrentLocationAndSave() {
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(1000);
            locationRequest.setNumUpdates(1);
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    if (locationResult == null) return;
                        Location location = locationResult.getLastLocation();
                        if (location != null) {
                        double lat = location.getLatitude();
                        double lng = location.getLongitude();
                        Map<String, Object> locationData = new HashMap<>();
                        locationData.put("Latitude", lat);
                        locationData.put("Longitude", lng);
                        DocumentReference documentReference =  firestore.collection("admins").document("admin1").collection("Location").document("classroom");
                                documentReference.set(locationData)
                                .addOnSuccessListener(unused -> Toast.makeText(getApplicationContext(), "Location Saved", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(getApplicationContext(), "Failed to save location", Toast.LENGTH_SHORT).show());

                    }
                    fusedClient.removeLocationUpdates(locationCallback);

                }
            };

            fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
        else{
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults, int deviceId) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId);
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocationAndSave();
        } else{
            Toast.makeText(this,"Location permission denied",Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchattendanceStatus() {
        adminRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                boolean isAttendanceMarked = Boolean.TRUE.equals(task.getResult().getValue(Boolean.class));

                if (isAttendanceMarked) {
                    takeattendance.setText("Stop Attendance");

                } else {
                    // Show toast message if attendance is not marked
                    takeattendance.setText("Start Attendance");
                }
            } else {
                takeattendance.setText("Start Attendance");
            }
        });
    }

    private void calculateAttendancePercentage() {
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot studentSnapshot) {
                long totalStudents = studentSnapshot.getChildrenCount();
                if (totalStudents == 0) {
                    textViewAttendancePercentage.setText("0%");
                    return;
                }

                long presentCount = 0;

                // Loop through all students
                for (DataSnapshot studentData : studentSnapshot.getChildren()) {
                    DataSnapshot attendanceSnapshot = studentData.child(currentDate).child("status");
                    if (attendanceSnapshot.exists()) {
                        String status = attendanceSnapshot.getValue(String.class);
                        if ("present".equalsIgnoreCase(status)) {
                            presentCount++;
                        }
                    }
                }

                // Calculate percentage
                double attendancePercentage = (double) presentCount / totalStudents * 100;
                progressIndicator.setProgress((int) attendancePercentage);
                textViewAttendancePercentage.setText((int) attendancePercentage + "%");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FirebaseError", "Failed to fetch students", error.toException());
            }
        });
    }

}
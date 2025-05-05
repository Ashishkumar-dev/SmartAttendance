package com.developer.smartattendancebbau;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {
private FirebaseFirestore db;
private static final String CHANNEL_ID = "attendance_channel";
private static final int POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE = 1;

private EditText secretcode;
    private DatabaseReference adminRef;
    private Handler handler;
    private Runnable pollingRunnable;
    private boolean isAttendanceMarked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            TextView stdbutton = findViewById(R.id.stdbtn);
            ImageView imageView = findViewById(R.id.imageView);
            imageView.setImageResource(R.drawable.icon);
            db = FirebaseFirestore.getInstance();
            adminRef = FirebaseDatabase.getInstance().getReference("admin").child("attendance");
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
                if(ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.POST_NOTIFICATIONS},
                            POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE);
                }
                else{
                    startAttendancePolling();
                }
            }
            else{
                startAttendancePolling();
            }

            stdbutton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    adminRef.get().addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult().exists()) {
                            boolean isAttendanceMarked = Boolean.TRUE.equals(task.getResult().getValue(Boolean.class));

                            if (isAttendanceMarked) {
                                // Start StudentListActivity
                                Intent intent = new Intent(MainActivity.this, studentportalActivity.class);
                                startActivity(intent);

                            } else {
                                // Show toast message if attendance is not marked
                                Toast.makeText(MainActivity.this, "The admin has stopped taking attendance!", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "The admin has stopped taking attendance!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
            // end student portal
            Button loginButton = findViewById(R.id.loginbtn);
            secretcode = findViewById(R.id.editTextTextPassword);
            loginButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String secretCode = secretcode.getText().toString().trim();
                    if (secretCode.isEmpty())
                    {
Toast.makeText(MainActivity.this,"Enter secret code",Toast.LENGTH_SHORT).show();
                    }
                    else {
                db.collection("admins").document("admin1").get().addOnSuccessListener(documentSnapshot -> {
                  if(documentSnapshot.exists())
                  {
                      String storedcode= documentSnapshot.getString("secretCode");
                      if(storedcode.equals(secretCode))
                      {
                          startActivity(new Intent(MainActivity.this, DashboardActivity.class));
                      }
                      else{
                          Toast.makeText(MainActivity.this,"Invalid Code",Toast.LENGTH_SHORT).show();
                      }
                  }
                })
                        .addOnFailureListener(e -> {
                           Toast.makeText(MainActivity.this,"Firebase Error:"+e.getMessage(),Toast.LENGTH_SHORT).show();
                        });
                    }

                }
            });
            return insets;
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults, int deviceId) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId);
        if(requestCode == POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                sendNotification();
            }
            else{
                Toast.makeText(this,"Permission to send notification was denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendNotification() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Attendance Notification";
            String description = "Channel for attendance start notifications";
            int important = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, important);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
            // push notification
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.icon)
                    .setContentTitle("Attendance Started")
                    .setContentText("The admin has started taking attendance.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build();
            NotificationManager notificationManager = (NotificationManager)
            getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(1,notification);

    }

    private void startAttendancePolling() {
         handler = new Handler();
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                checkAttendanceStatus();
                handler.postDelayed(this,2000);
            }
        };
        handler.post(pollingRunnable);
    }

    private void checkAttendanceStatus() {
        adminRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                boolean isAttendanceMarkedinDB = Boolean.TRUE.equals(task.getResult().getValue(Boolean.class));
                    if(isAttendanceMarkedinDB){
                        if(!isAttendanceMarked){
                            sendNotification();
                            isAttendanceMarked = true;
                        }
                    }

                else{
                    if(isAttendanceMarked){
                        isAttendanceMarked = false;
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(pollingRunnable);
    }
}
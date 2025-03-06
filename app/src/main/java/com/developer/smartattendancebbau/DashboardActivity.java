package com.developer.smartattendancebbau;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            Button addButton = findViewById(R.id.addbtn);
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
            Button takeattendance = findViewById(R.id.attendancebtn);
            takeattendance.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String btnText = takeattendance.getText().toString();
                    if(btnText.contains("Take Attendance")) {
                        takeattendance.setText("Stop");
                        Toast.makeText(getApplicationContext(), "Started", Toast.LENGTH_SHORT).show();
                    }
                    else {
                        takeattendance.setText("Take Attendance");
                        Toast.makeText(getApplicationContext(), "Stoped", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            Button captureButton = findViewById(R.id.captureface);
            captureButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(DashboardActivity.this, capturestudent.class);
                    startActivity(intent);
                }
            });
            return insets;
        });
    }
}
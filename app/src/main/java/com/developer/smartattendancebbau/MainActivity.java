package com.developer.smartattendancebbau;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {
private FirebaseFirestore db;

private EditText secretcode;
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
            stdbutton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(MainActivity.this, studentportalActivity.class);
                    startActivity(intent);
                }
            });
            Button loginButton = findViewById(R.id.loginbtn);
            secretcode = findViewById(R.id.editTextTextPassword);
            loginButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String secretCode = secretcode.getText().toString().trim();
                    if (secretCode.isEmpty())
                    {
Toast.makeText(MainActivity.this,"enter secret code",Toast.LENGTH_SHORT).show();
                    }
                    else {
                db.collection("admins").document("admin1").get().addOnSuccessListener(documentSnapshot -> {
                  if(documentSnapshot.exists())
                  {
                      String storedcode= documentSnapshot.getString("secretCode");
                      if(storedcode.equals(secretCode))
                      {
                          startActivity(new Intent(MainActivity.this, DashboardActivity.class));
                          finish();
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
}
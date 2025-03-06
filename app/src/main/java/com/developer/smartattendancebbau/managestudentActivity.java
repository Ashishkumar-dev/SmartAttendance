package com.developer.smartattendancebbau;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class managestudentActivity extends AppCompatActivity {
    private EditText name;
    private EditText rollno;
    private FirebaseFirestore db;
    private Button add;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_managestudent);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        name = findViewById(R.id.editTextText);
        rollno = findViewById(R.id.editTextText2);
        add = findViewById(R.id.button5);
        db = FirebaseFirestore.getInstance();
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveDatatoFirebase();
            }
        });
    }

    private void saveDatatoFirebase() {
        String stdname = this.name.getText().toString().trim();
        String stdrollno = this.rollno.getText().toString().trim();
        if(name != null && rollno != null)
        {
            DocumentReference documentReference = db.collection("students").document();
            Map<String, Object> student = new HashMap<>();
            student.put("name",stdname);
            student.put("documentId",stdrollno);
            documentReference.set(student)
                    .addOnSuccessListener(aVoid ->
                            Toast.makeText(getApplicationContext(),"Data Saved",Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(getApplicationContext(),"Error"+ documentReference,Toast.LENGTH_SHORT).show());
        }
    else {
        Toast.makeText(getApplicationContext(),"Please enter a name and roll number",Toast.LENGTH_SHORT).show();
        }
    }
}
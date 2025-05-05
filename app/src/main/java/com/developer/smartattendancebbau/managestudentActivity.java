package com.developer.smartattendancebbau;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class managestudentActivity extends AppCompatActivity {
    private EditText name;
    private EditText rollno;
    private FirebaseFirestore db;
    private Button add;
    private Button remove;
    private Button update;
    private androidx.appcompat.widget.SearchView searchView;

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
        searchView = findViewById(R.id.searchView);
        name = findViewById(R.id.editTextText);
        rollno = findViewById(R.id.editTextText2);
        remove = findViewById(R.id.button7);
        update = findViewById(R.id.button6);
        add = findViewById(R.id.button5);
        db = FirebaseFirestore.getInstance();
        @SuppressLint("RestrictedApi") androidx.appcompat.widget.SearchView.SearchAutoComplete searchEdittext = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        searchEdittext.setTextColor(Color.WHITE);
        searchView.setIconifiedByDefault(false);
        searchView.setQueryHint("Enter Student Roll Number");
        searchEdittext.setHintTextColor(Color.GRAY);


        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveDatatoFirebase();
            }
        });
       remove.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View view) {
               removeDatafromFirestore();
           }
       });
       update.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View view) {
               updateDatatoFirestore();
           }
       });

        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchStudentByRollNumber(query.trim());
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }


    private void searchStudentByRollNumber(String rollNumber) {
        if (rollNumber.isEmpty()) {
            Toast.makeText(this, "Enter roll number to search", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference studentRef = db.collection("students").document(rollNumber);

        studentRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String studentName = documentSnapshot.getString("Name");

                // Set values in EditText fields
                name.setText(studentName);
                rollno.setText(rollNumber);

            } else {
                Toast.makeText(this, "Student not found!", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Log.e("Firestore", "Error searching for student", e);
            Toast.makeText(this, "Error fetching data", Toast.LENGTH_SHORT).show();
        });
    }


    private void updateDatatoFirestore() {
        String oldStudentID = searchView.getQuery().toString(); // Store old ID temporarily
        String newStudentID = rollno.getText().toString().trim();  // New Roll Number (Document ID)
        String updatedName = name.getText().toString().trim(); // New Name

        // Check if Roll Number is empty
        if (oldStudentID.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Enter a valid student roll number in search bar", Toast.LENGTH_SHORT).show();
            return;
        }

        // References to old and new documents
        DocumentReference oldStudentRef = db.collection("students").document(oldStudentID);

        // Fetch existing student data
        oldStudentRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Map<String, Object> studentData = documentSnapshot.getData();


                // Update only the name if provided
                if (!updatedName.isEmpty()) {
                    assert studentData != null;
                    studentData.put("Name", updatedName);
                }
                assert studentData != null;
                if(!newStudentID.isEmpty()){
                    db.collection("students").document(newStudentID).set(studentData)
                        .addOnSuccessListener(aVoid -> {
                            if (!oldStudentID.equals(newStudentID)) {
                                oldStudentRef.delete();
                            }
                            Toast.makeText(getApplicationContext(), "Details Updated Successfully", Toast.LENGTH_SHORT).show();
                        });
            }
                else{
                    db.collection("students").document(oldStudentID).set(studentData)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getApplicationContext(), "Details Updated Successfully", Toast.LENGTH_SHORT).show();
                            });
                }
            }
            else {
                Toast.makeText(getApplicationContext(), "Student Details not found", Toast.LENGTH_SHORT).show();
            }

        }).addOnFailureListener(e -> Log.e("TAG", "Error fetching student details", e));
    }

    private void removeDatafromFirestore() {
        String stdrollno = this.rollno.getText().toString().trim();
        if(!stdrollno.isEmpty()){
            DocumentReference documentReference = db.collection("students").document(stdrollno);
            documentReference.get().addOnSuccessListener(documentSnapshot -> {
                if(documentSnapshot.exists()){
                    documentReference.delete().addOnSuccessListener(aVoid -> Toast.makeText(getApplicationContext(),"Student record Deleted successfully",Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(getApplicationContext(),"Error deleting student record",Toast.LENGTH_SHORT).show());
                deleteStudent(stdrollno);
                }
                else{
                    Toast.makeText(getApplicationContext(),"Student record not found",Toast.LENGTH_SHORT).show();
                }
            }).addOnFailureListener(e -> {Toast.makeText(getApplicationContext(),"Error retrieving student record",Toast.LENGTH_SHORT).show();});
        }
        else {
            Toast.makeText(getApplicationContext(),"Enter Valid Student Roll Number",Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteStudent(String stdrollno) {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("students");
        databaseReference.child(stdrollno).removeValue()
                .addOnSuccessListener(aVoid -> {
                System.out.println("Deleted");
                })
                .addOnFailureListener(e -> {
                    System.out.println("Error Deleting");
                });
    }

    private void saveDatatoFirebase() {
        String stdname = name.getText().toString().trim();
        String stdrollno = rollno.getText().toString().trim();
        if(!stdname.isEmpty() && !stdrollno.isEmpty()) {
            DocumentReference documentReference = db.collection("students").document(stdrollno);
            documentReference.get().addOnSuccessListener(documentSnapshot ->
                    {
                        if (documentSnapshot.exists()) {
                            Toast.makeText(getApplicationContext(), "Student already exists", Toast.LENGTH_SHORT).show();
                        } else {
                            Map<String, Object> student = new HashMap<>();
                            student.put("Name", stdname);
                            documentReference.set(student)
                                    .addOnSuccessListener(aVoid ->
                                            Toast.makeText(getApplicationContext(), "Student Details Saved", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(getApplicationContext(), "Error" + documentReference, Toast.LENGTH_SHORT).show());

                        }
                    }
            ).addOnFailureListener(e -> Toast.makeText(getApplicationContext(),"Error checking student details",Toast.LENGTH_SHORT).show());
        }
       else {
            Toast.makeText(getApplicationContext(), "Enter name and roll number", Toast.LENGTH_SHORT).show();
        }
    }
    }
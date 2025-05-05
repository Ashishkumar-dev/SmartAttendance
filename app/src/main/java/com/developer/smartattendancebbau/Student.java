package com.developer.smartattendancebbau;

public class Student {
    private String name;
    private String rollNumber;
    private String status;
    private String date;

    // Empty constructor for Firebase
    public Student() {}

    public Student(String name, String rollNumber, String status, String date) {
        this.name = name;
        this.rollNumber = rollNumber;
        this.status = status;
        this.date = date;
    }

    // Getters
    public String getName() { return name; }
    public String getRollNumber() { return rollNumber; }
    public String getStatus() { return status; }
    public String getDate() { return date; }
}


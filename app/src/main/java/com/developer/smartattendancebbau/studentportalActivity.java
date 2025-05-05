package com.developer.smartattendancebbau;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.*;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.*;

public class studentportalActivity extends AppCompatActivity {
    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSession;
    private String CameraId;
    private CameraManager cameraManager;
    private FirebaseFirestore db;

    private static final int CAMERA_REQUEST_CODE = 100;
    private final float MATCH_THRESHOLD = 0.6f;
    private ImageView check;
    private Interpreter tflite;
    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private static final float DISTANCE_THRESHOLD_METERS = 50;
    private FusedLocationProviderClient fusedClient;
    private Location classLocation;
    private ImageView imageView;
    private LocationCallback locationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_studentportal);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
        textureView = findViewById(R.id.textureView3);
        check = findViewById(R.id.imageView3);
        imageView = findViewById(R.id.circle);
        Button btn = findViewById(R.id.button3);
        FrameLayout cameraFrame = findViewById(R.id.frameLayout3);
        db = FirebaseFirestore.getInstance();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
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
        getClassroomLocation();


        //        Load facemodel
        try {
            tflite = new Interpreter((loadModelFile()));
        }
        catch(IOException e)
        {
            Log.d("FaceNet","Error loading model",e);
        }

        cameraFrame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int size = Math.min(view.getWidth(), view.getHeight());
                outline.setOval(0, 0, size, size);
            }
        });
        cameraFrame.setClipToOutline(true);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        } else {
            setupCamera();
        }
        // Enroll Student Face
        Button enrollfaceButton = findViewById(R.id.btnenroll);
        enrollfaceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), capturestudent.class);
                intent.putExtra("previous","studentportal");
                startActivity(intent);
                finish();
            }
        });
        // Capture Face Button

        btn.setOnClickListener(view -> {
            if(checkPermissions()){
                checkLocation();
            }
            else{
                requestPermissions();
            }

        });
        return insets;
    });
    }
    //end oncreate

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,new String[] {Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        {
            if(requestCode == LOCATION_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                checkLocation();
            }
            else{
                Toast.makeText(this,"Location permission denied",Toast.LENGTH_SHORT).show();
            }

        }
    }

    private void checkLocation() {
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        if(classLocation == null){
            Toast.makeText(this,"Class location not set",Toast.LENGTH_SHORT).show();
            return;
        }
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(5000);
            locationRequest.setFastestInterval(2000);
            locationCallback = new LocationCallback(){
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    super.onLocationResult(locationResult);
                    if (locationResult != null) {
                        Location location = locationResult.getLastLocation();
                        float distance = location.distanceTo(classLocation);
                        Log.d("Location","Location "+ classLocation);
                        if (distance > DISTANCE_THRESHOLD_METERS) {
                            Toast.makeText(getApplicationContext(), "You are outside the class", Toast.LENGTH_SHORT).show();
                        } else {
                            Bitmap faceBitmap = captureFace();
                            if (faceBitmap != null) {
                                float[] embedding = generateEmbedding(faceBitmap);
                                Log.d("FaceMatch", "emb2" + embedding);
//                double normal = 0;
//                for (float value : embedding) {
//                    normal += value * value;
//                }
//                normal = Math.sqrt(normal);
                                fetchStoredEmbeddings(embedding);
                            }
                        }
                    } else {
                        Toast.makeText(getApplicationContext(), "Unable to get current location", Toast.LENGTH_SHORT).show();
                    }
                fusedClient.removeLocationUpdates(locationCallback);
                }
            };
            fusedClient.requestLocationUpdates(locationRequest,locationCallback, Looper.getMainLooper());

        }
        else {
           ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_PERMISSION_REQUEST);
        }
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED;
    }

    private void getClassroomLocation() {
        db.collection("admins").document("admin1").collection("Location").document("classroom").get().addOnSuccessListener(documentSnapshot -> {
            if(documentSnapshot.exists()){
                Double latitude = documentSnapshot.getDouble("Latitude");
                Double longitude = documentSnapshot.getDouble("Longitude");
                if(latitude != null && longitude != null){
                    classLocation = new Location("");
                    classLocation.setLatitude(latitude);
                    classLocation.setLongitude(longitude);
                }
            }

                })
                .addOnFailureListener(e -> Log.e("Firestore","Error fetching location",e));

    }



    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("facenet.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void setupCamera() {
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    CameraId = cameraId;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
               if(textureView.isAvailable()){
                   closeCamera();
                   openCamera();
               }

            }
            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) { return false; }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {}
        });
    }

    private void closeCamera() {
        if(cameraCaptureSession != null){
            cameraCaptureSession.close();
            cameraCaptureSession = null ;
        }
        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            cameraManager.openCamera(CameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview();
                }
                @Override public void onDisconnected(@NonNull CameraDevice camera) { camera.close(); cameraDevice = null; }
                @Override public void onError(@NonNull CameraDevice camera, int error) { camera.close(); cameraDevice = null; }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture == null) return;
            surfaceTexture.setDefaultBufferSize(640, 480);
            Surface surface = new Surface(surfaceTexture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSession = session;
                    updatePreview();
                }
                @Override public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(getApplicationContext(), "Camera Preview Failed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (cameraDevice == null) return;
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Bitmap captureFace() {
        if(textureView.isAvailable())
        {
            Bitmap bitmap = textureView.getBitmap();
            if(bitmap != null)
            {
                return Bitmap.createScaledBitmap(bitmap,160,160,true);
            }
        }
        return null;
    }

    private float[] generateEmbedding(Bitmap bitmap) {
        // Ensure the bitmap is resized to 160x160
        Bitmap resizedFace = Bitmap.createScaledBitmap(bitmap, 160, 160, true);

        // Preprocess the image to ByteBuffer
        ByteBuffer input = preprocessBitmap(resizedFace);

        // Prepare output tensor
        float[][] output = new float[1][512]; // Assuming model outputs 512D embeddings

        // Run model inference
        tflite.run(input, output);
Log.d("FaceMatch","emb"+output[0]);
        return output[0];
    }

    private ByteBuffer preprocessBitmap(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(160 * 160 * 3 * 4);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[160 * 160];
        bitmap.getPixels(pixels, 0, 160, 0, 0, 160, 160);

        for (int pixel : pixels) {
            float r = ((pixel >> 16) & 0xFF) / 255.0f;
            float g = ((pixel >> 8) & 0xFF) / 255.0f;
            float b = (pixel & 0xFF) / 255.0f;

            // Normalize values to range [-1, 1]
            buffer.putFloat((r - 0.5f) * 2.0f);
            buffer.putFloat((g - 0.5f) * 2.0f);
            buffer.putFloat((b - 0.5f) * 2.0f);
        }

        return buffer;
    }
    private void fetchStoredEmbeddings(float[] newEmbedding) {
        db.collection("students").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                for (QueryDocumentSnapshot document : task.getResult()) {
                    // 🔹 Retrieve embeddings as a Map (NOT a List!)
                    Map<String, Object> embeddingsMap = (Map<String, Object>) document.get("embeddings");

                    if (embeddingsMap != null) {
                        for (Map.Entry<String, Object> entry : embeddingsMap.entrySet()) {
                            String view = entry.getKey(); // Example: "front", "left", "down"
                            Object value = entry.getValue();

                            // 🔹 Ensure value is a List before casting
                            if (value instanceof List<?>) {
                                List<Double> storedEmbedding = (List<Double>) value;

                                if (storedEmbedding.size() == 512) {
                                    float similarity = cosineSimilarity(newEmbedding, storedEmbedding);

                                    boolean isnormalised = isNormalized(storedEmbedding);
                                    Log.d("FaceMatch", "View: " + view + " | Is Normalized: " + isnormalised);

                                    double normal = 0;
                                    for (float v : newEmbedding) {
                                        normal += v * v;
                                    }
                                    normal = Math.sqrt(normal);

                                    Log.d("FaceMatch", "New Embedding: " + normal);
                                    Log.d("FaceMatch", "Stored Embedding: " + storedEmbedding);
                                    Log.d("FaceMatch", "StudentId: " + document.getId() + ", Similarity: " + similarity);

                                    if (similarity > MATCH_THRESHOLD) {
                                        String studentName = document.getString("Name");
                                        if(studentName != null)
                                        {
                                        successtick();  // ✅ Show success tick
                                        greencircle();
                                        markAttendance(document.getId(), studentName);
                                        Toast.makeText(this, studentName+" your attendance is marked", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                        else {
                                            Toast.makeText(this, "Student Name not found", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // 🔹 Show toast only if no match is found
                Toast.makeText(this, "No Match Found", Toast.LENGTH_SHORT).show();
            }
        });
    }



    private boolean isNormalized(List<Double> newEmbedding) {
        double sumOfSquares = 0;
        for (double value : newEmbedding) {
            sumOfSquares += value * value;
        }
        double norm = Math.sqrt(sumOfSquares);
        return Math.abs(norm - 1.0) < 0.01;

    }

    private List<Double> convertMapToArray(Map<String, Object> embeddingmap)
    {
        Log.d("FaceMatch", "Embedding Raw Map: " + embeddingmap.toString());
        List<Double> embeddingList = new ArrayList<>();
        for(int i = 0; i < 512; i++){
            String key = "dim_"+i;
            if(embeddingmap.containsKey(key))
            {
                Object value = embeddingmap.get(key);

                    if(value instanceof Number){
                        embeddingList.add(((Number) value).doubleValue());
                        Log.d("FaceMatch", "Embedding List: " + embeddingList);
                    }
            }
        }

        if (!isNormalized(embeddingList)) {
            return normalizeEmbedding(embeddingList);
        }
        return embeddingList;


    }

    private List<Double> normalizeEmbedding(List<Double> embeddingList) {
        double norm = 0;
        for (double value : embeddingList)
        {
            norm += value * value;

        }
        norm = Math.sqrt(norm);
        if(norm == 0) return embeddingList;
        List<Double> normalizedList = new ArrayList<>();
        for (double value : embeddingList)
        {
            normalizedList.add(value / norm);
        }
        return normalizedList;
    }

    private float cosineSimilarity(float[] a, List<Double> b) {
        if (a.length != b.size()) {
            Log.e("FaceMatching", "Embedding dimensions mismatch: Expected 512, but got " + b.size());
            return -1;
        }

        float dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b.get(i);
            normA += Math.pow(a[i], 2);
            normB += Math.pow(b.get(i), 2);
        }
        if(normA == 0 || normB == 0){
            Log.e("FaceMatch", "Embedding norm is zero");
            return -1;
        }
        float similarity =  (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
        Log.d("FaceMatch", "Cosine Similarity: " + similarity);
        return similarity;
    }

    private void markAttendance(String studentId, String studentName) {
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());


        DatabaseReference attendanceRef = FirebaseDatabase.getInstance().getReference("students").child(studentId).child(currentDate);

        // ✅ Check if attendance already exists to prevent duplicates
        attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Log.d("RealtimeDB", "Attendance already marked for " + studentId);
                } else {
                    // ✅ Mark "present"
                    Map<String, Object> attendanceData = new HashMap<>();
                    attendanceData.put("rollNumber", studentId);
                    attendanceData.put("date", currentDate);
                    attendanceData.put("name", studentName);
                    attendanceData.put("status", "present");

                    attendanceRef.setValue(attendanceData)
                            .addOnSuccessListener(aVoid -> Log.d("RealtimeDB", "Attendance marked successfully for " + studentId))
                            .addOnFailureListener(e -> Log.e("RealtimeDB", "Error marking attendance", e));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("RealtimeDB", "Error checking attendance", error.toException());
            }
        });
    }

//    private void scheduleAbsentStatus(String studentId, String currentDate) {
//        DatabaseReference attendanceRef = FirebaseDatabase.getInstance().getReference("attendance").child(studentId).child(currentDate);
//
//        attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (!snapshot.exists()) {  // If student has not checked in
//                    // Create an absent entry
//                    Map<String, Object> absentData = new HashMap<>();
//                    absentData.put("date", currentDate);
//                    absentData.put("status", "absent");
//
//                    // Store "absent" record
//                    attendanceRef.setValue(absentData)
//                            .addOnSuccessListener(aVoid -> Log.d("RealtimeDB", "Marked as absent"))
//                            .addOnFailureListener(e -> Log.e("RealtimeDB", "Error marking absent", e));
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e("RealtimeDB", "Error checking absent status", error.toException());
//            }
//        });
//    }


    private void successtick() {
        check.setAlpha(0f);
        check.setVisibility(View.VISIBLE);
        check.animate().alpha(1f).setDuration(500).withEndAction(() -> {
            new Handler().postDelayed(() -> {
                check.animate().alpha(0f).setDuration(500).withEndAction(() -> {
                    check.setVisibility(View.GONE);
                });
            }, 200);
        });
    }
    private void greencircle(){
        imageView.setImageResource(R.drawable.circle_mask_green);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                imageView.setImageResource(R.drawable.circle_mask);
            }
        },500);
    }
}

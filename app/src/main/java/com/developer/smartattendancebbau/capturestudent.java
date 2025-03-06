package com.developer.smartattendancebbau;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class capturestudent extends AppCompatActivity {
    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSession;
    private static final int CAMERA_REQUEST_CODE = 100;
    private String CameraId;
    private CameraManager cameraManager;
    private Interpreter tflite;
    private FirebaseFirestore db;
    private DocumentReference studentRef;
    private Map<String, float[]> capturedEmbeddings = new HashMap<>();
    private static final String TAG = "FaceRecognition";
    private EditText stdrollno;
    private ImageView check;
    private Button btn;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_capturestudent);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);

            return insets;
        });
        // Initialize Firebase
        db = FirebaseFirestore.getInstance();

//        Load facemodel
        try {
            tflite = new Interpreter((loadModelFile()));
        }
        catch(IOException e)
        {
            Log.d(TAG,"Error loading model",e);
        }
        textureView = findViewById(R.id.textureView3);
        check = findViewById(R.id.imageView3);
         btn = findViewById(R.id.button3);
        stdrollno = findViewById(R.id.editTextText3);
        FrameLayout cameraFrame = findViewById(R.id.frameLayout3);
        cameraFrame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int size = Math.min(view.getWidth(),
                        view.getHeight());
                outline.setOval(0,0,size,size);
            }
        });
        cameraFrame.setClipToOutline(true);

        // Request Camera Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        } else {
            setupCamera();
        }

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String rollno = stdrollno.getText().toString().trim();
                if (rollno.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Please enter student roll number to capture face", Toast.LENGTH_SHORT).show();
                }
                else{
                    captureAndProcessFaces();

            }
            }
        });

    }
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("facenet.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


//    private @org.checkerframework.checker.nullness.qual.NonNull MappedByteBuffer loadModelFile() throws IOException {
//        FileInputStream inputStream = new FileInputStream(getAssets().openFd("facenet.tflite").getFileDescriptor());
//        FileChannel fileChannel = inputStream.getChannel();
//        long startOffset = getAssets().openFd("facenet.tflite").getStartOffset();
//        long length = getAssets().openFd("facenet.tflite").getLength();
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, length);
//    }

//Capture face from different angles and process
    private int currentAngleIndex = 0; // Track the current face angle being captured
    private final String[] faceAngles = {"front", "left", "right", "up", "down"};
    private final String[] angleMessages = {
            "Look straight at the camera",
            "Turn your head to the LEFT",
            "Turn your head to the RIGHT",
            "Look UP towards the ceiling",
            "Look DOWN towards the floor"
    };

    private void captureAndProcessFaces() {
//        Log.d(TAG, "All angles captured. Uploading embeddings..."+capturedEmbeddings.toString());
        if (currentAngleIndex >= faceAngles.length) {

            uploadEmbeddings(); // Upload only after all angles are captured
            return;
        }

        String angle = faceAngles[currentAngleIndex];
        String message = angleMessages[currentAngleIndex];

        if (capturedEmbeddings.containsKey(angle)) {
            Toast.makeText(this, "Face for " + angle + " already captured. Moving to next.", Toast.LENGTH_SHORT).show();
            currentAngleIndex++;
            captureAndProcessFaces(); // Proceed to next angle
            return;
        }
        successtick();
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(() -> {
            Bitmap faceBitmap = captureFace();
            if (faceBitmap == null) {
                Toast.makeText(this, "Face not detected for " + angle + ". Try again.", Toast.LENGTH_SHORT).show();
                return;
            }

            float[] embedding = generateEmbedding(faceBitmap);
            double normal = 0;
            for (float value : embedding) {
                normal += value * value;
            }
            normal = Math.sqrt(normal);
            Log.d("ashishjuli", "Embedding Norm: " + normal); // Log for debugging
//            Log.d(TAG, "Embedding for " + angle + ": " + Arrays.toString(embedding));
            if (isDuplicateFace(embedding)) {
                Toast.makeText(this, "Duplicate face detected! Please capture again.", Toast.LENGTH_SHORT).show();
                return;
            }

            capturedEmbeddings.put(angle, embedding);

            currentAngleIndex++; // Move to next angle

                captureAndProcessFaces(); // Call again for next angle

        }, 2000); // 2-second delay before capturing next angle
    }
    private boolean isDuplicateFace(float[] newEmbedding) {
        for (float[] existingEmbedding : capturedEmbeddings.values()) {
            if (calculateDistance(newEmbedding, existingEmbedding) < 0.6f) { // Threshold for similarity
                return true;
            }
        }
        return false;
    }

    // Calculate Euclidean distance between two embeddings
    private float calculateDistance(float[] emb1, float[] emb2) {
        float sum = 0;
        for (int i = 0; i < emb1.length; i++) {
            float diff = emb1[i] - emb2[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    private void uploadEmbeddings() {
        String studentID = stdrollno.getText().toString().trim();
        studentRef = db.collection("students").document(studentID);
        Map<String, Object> embeddingsMap = new HashMap<>();
        for (Map.Entry<String, float[]> entry : capturedEmbeddings.entrySet()){
        String angle = entry.getKey();
        float[] embedding = entry.getValue();

        Map<String, Object> embeddingData = new HashMap<>();
        for(int i=0; i < embedding.length; i++){
            float[] values = embedding;
            double normal = 0;
            for (float value : values) {
                normal += value * value;
            }
            normal = Math.sqrt(normal);
            embeddingData.put("dim_"+i, normal);
            Log.d("ashishjuli", "Embedding Normally: " + normal);
        }
        Log.d("ashishjuli", "Embedding Data: " + embeddingData);
        embeddingsMap.put(angle, embeddingData);
        }
        studentRef.update("embeddings", embeddingsMap)
                .addOnSuccessListener(aVoid ->  Toast.makeText(this, "All face angles captured successfully!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Log.e(TAG,"Error uploading embeddings",e));
        List<Map<String, Object>> embeddingList = new ArrayList<>();
    }

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

    private float[] generateEmbedding(Bitmap face) {
        // Ensure the bitmap is resized to 160x160
        Bitmap resizedFace = Bitmap.createScaledBitmap(face, 160, 160, true);

        // Preprocess the image to ByteBuffer
        ByteBuffer input = preprocessBitmap(resizedFace);

        // Prepare output tensor
        float[][] output = new float[1][512]; // Assuming model outputs 512D embeddings
//        Log.d(TAG,"emb"+output[0]);
        // Run model inference
        tflite.run(input, output);
        float[] nor = normalize(output[0]);  // Normalize before storing

//        Log.d("ashish","emb"+nor);
        return output[0];
    }
    private float[] normalize(float[] embedding) {
        double norm = 0;
        for (float value : embedding) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);

        float[] normalizedEmbedding = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            normalizedEmbedding[i] = (float) (embedding[i] / norm);
        }

        return normalizedEmbedding;
    }

    private ByteBuffer preprocessBitmap(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1 * 160 * 160 * 3 * 4);
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

    private void setupCamera() {
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
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
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {}
        });
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

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                }
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

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
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
}
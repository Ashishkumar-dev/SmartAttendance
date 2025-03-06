package com.developer.smartattendancebbau;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.*;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class studentportalActivity extends AppCompatActivity {
    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSession;
    private String CameraId;
    private CameraManager cameraManager;
    private FirebaseFirestore db;
    private FirebaseDatabase database;
    private static final int CAMERA_REQUEST_CODE = 100;
    private final float MATCH_THRESHOLD = 0.6f;
    private ImageView check;
    private Interpreter tflite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_studentportal);
        textureView = findViewById(R.id.textureView3);
        check = findViewById(R.id.imageView3);
        Button btn = findViewById(R.id.button3);
        FrameLayout cameraFrame = findViewById(R.id.frameLayout3);
        db = FirebaseFirestore.getInstance();
        database = FirebaseDatabase.getInstance();
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

        btn.setOnClickListener(view -> {
            Bitmap faceBitmap = captureFace();
            if (faceBitmap != null) {
                float[] embedding = generateEmbedding(faceBitmap);
                Log.d("FaceMatch","emb2"+embedding);
//                double normal = 0;
//                for (float value : embedding) {
//                    normal += value * value;
//                }
//                normal = Math.sqrt(normal);
                fetchStoredEmbeddings(embedding);
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
                openCamera();
            }
            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) { return false; }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {}
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

    private void fetchStoredEmbeddings(float[] newEmbedding) {
        db.collection("students").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Map<String, Object> storedEmbeddingMap = (Map<String, Object>) document.get("embeddings");

                    if (storedEmbeddingMap != null) {
                        List<Double> storedEmbedding = convertMapToArray(storedEmbeddingMap);
                        float similarity = cosineSimilarity(newEmbedding, storedEmbedding);
                        boolean isnormalised = isNormalized(storedEmbedding);
                        Log.d("FaceMatch","Is New Embedding Normalized: "+isnormalised);
                        double normal = 0;
                        for (float value : newEmbedding) {
                            normal += value * value;
                        }
                        normal = Math.sqrt(normal);
                        Log.d("FaceMatch","New Embedding: "+normal);
                        Log.d("FaceMatch","stored original Embedding: "+storedEmbedding);
//                        Log.d("FaceMatch", "stored Converted Embedding: "+Arrays.toString(storedEmbedding));
                        Log.d("FaceMatch", "studentId: " + document.getId() + ", Similarity: " + similarity);
                        if (similarity > MATCH_THRESHOLD) {
                            Toast.makeText(this, "Found", Toast.LENGTH_SHORT).show();
                            updateMatchStatus(document.getId(), true);
                            return;
                        }
                        else
                        {
                            Toast.makeText(this, "No Match Found", Toast.LENGTH_SHORT).show();
                        }
                    }
                }

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

        return normalizeEmbedding(embeddingList);

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
        if(a.length != b.size()){
//            Log.e("FaceMatching", "Embedding dimensions mismatch"+a.length+" Stored Embedding: "+b.size());
//            return -1;
            Log.e("FaceMatching", "Embedding dimensions mismatch"+b);
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

    private void updateMatchStatus(String userId, boolean status) {
        DatabaseReference ref = database.getReference("students").child(userId).child("attendance");
        //Update the attendance status in the database
        ref.setValue(status).addOnSuccessListener(aVoid -> {successtick();  Toast.makeText(this, "Match Found!", Toast.LENGTH_SHORT).show();})
                        .addOnFailureListener(e -> {Toast.makeText(this, "Error updating attendance status", Toast.LENGTH_SHORT).show();});

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
}

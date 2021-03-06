package com.example.shop_assistant;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.shop_assistant.databinding.ActivityBarcodeScanningBinding;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class BarcodeScanningActivity extends AppCompatActivity {

    private final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private ActivityBarcodeScanningBinding binding;
    FloatingActionButton button;

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build());

        binding = ActivityBarcodeScanningBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        if (hasCameraPermission()) {
            bindCameraUseCases();
        } else {
            requestPermission();
        }

        button = findViewById(R.id.fab2);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // defining intent for passing data to ProductInfoActivity
                Intent intent = new Intent(BarcodeScanningActivity.this, ProductInfoActivity.class);

                // pass the data to ProductInfoActivity
                if(!binding.bottomText.equals("") && binding.bottomText.getText() != null) intent.putExtra("barcode", binding.bottomText.getText());

                if (binding.bottomText.getText() != "") startActivity(intent);
            }
        });
    }

    // request permission to use Camera
    private void requestPermission(){
        // opening up dialog to ask for camera permission
        ActivityCompat.requestPermissions(
                this,
                new String[] {Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE
        );
    }

    // checking to see whether user has already granted permission
    private boolean hasCameraPermission() {
        return ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    // binding camera use cases depending on successful request permission result
    // otherwise informing user that camera permission is required
    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public void onRequestPermissionsResult(int requestCode , String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // user granted permissions - we can set up our scanner
            bindCameraUseCases();
        } else {
            // user did not grant permissions - we can't use the camera
            Toast.makeText(this,
                    "Camera permission required",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // the most important piece of code!!!
    // creating from our front camera and binding Preview to activity_barcode_scanning layout
    @RequiresApi(api = Build.VERSION_CODES.R)
    void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());


        // configure our MLKit BarcodeScanning client: //
        /* passing in our desired barcode formats - MLKit supports additional formats outside of the ones listed here,
        and you may not need to offer support for all of these. You should only specify the ones you need */
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder().setBarcodeFormats(
                //Barcode.FORMAT_CODE_128,
                //Barcode.FORMAT_CODE_39,
                //Barcode.FORMAT_CODE_93,
                //Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_EAN_13,
                //Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E
                //Barcode.FORMAT_PDF417
        ).build();

        // getClient() creates a new instance of the MLKit barcode scanner with the specified options
        BarcodeScanner scanner = BarcodeScanning.getClient(options);

        // setting up the analysis use case
        ImageAnalysis analysisUseCase = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        // define the actual functionality of our analysis use case
        analysisUseCase.setAnalyzer(
                // newSingleThreadExecutor() will let us perform analysis on a single worker thread
                Executors.newSingleThreadExecutor(),
                imageProxy -> {
                        processImageProxy(scanner, imageProxy);
                }
        );

        // using Crop rect to limit the visible scanning area
        @SuppressLint("UnsafeOptInUsageError") ViewPort viewPort = new ViewPort.Builder(
                new Rational(50,100),
                getDisplay().getRotation())
                .build();
        @SuppressLint("UnsafeOptInUsageError") UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(analysisUseCase)
                .setViewPort(viewPort)
                .build();

        // bindToLifecycle(...) --- important
        @SuppressLint("UnsafeOptInUsageError") Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroup);
    }

    //
    @RequiresApi(api = Build.VERSION_CODES.R)
    private void bindCameraUseCases() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));

    }

    private void processImageProxy(BarcodeScanner barcodeScanner, ImageProxy imageProxy) {
        @SuppressLint("UnsafeOptInUsageError") Image image = imageProxy.getImage();

        InputImage inputImage =
                InputImage.fromMediaImage(image, imageProxy.getImageInfo().getRotationDegrees());

        Task<List<Barcode>> result = barcodeScanner.process(inputImage);

        if(image != null) {
            result.addOnSuccessListener(new OnSuccessListener<List<Barcode>>() {
                @Override
                public void onSuccess(List<Barcode> barcodes) {
                    // Task completed successfully
                    Log.i("CameraXApp3", "scanner task successful");
                    Barcode barcode = result.getResult().get(0);

                    // Our decoded barcode raw value -> You can pass it to API:
                    String rawValue = barcode.getRawValue();

                    // Update our textView to show the decoded value
                    binding.bottomText.setText(rawValue);
                    binding.fab2.setVisibility(View.VISIBLE);

                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    // Task failed with an exception
                    Log.i("CameraXApp3", "Scanner task failed. Error: " + e);

                }
            }).addOnCompleteListener(new OnCompleteListener<List<Barcode>>() {
                @Override
                public void onComplete(@NonNull Task<List<Barcode>> task) {
                    image.close();
                    imageProxy.close();
                }
            });
        }

        // those two lines of code are very important --- remember to .close() image and imageProxy
        // otherwise app crashes or camera doesn't scan barcodes
        image.close();
        imageProxy.close();
    }
}

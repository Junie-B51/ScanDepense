package com.example.scandepenses;

import android.content.Context;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;

public class CameraManager {
    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private ImageCapture imageCapture;

    public void startCamera(Context context, LifecycleOwner lifecycleOwner, PreviewView previewView) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void takePhoto(Context context, OnPhotoTakenListener listener) {
        if (imageCapture == null) return;

        File photoFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "recu_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        listener.onSuccess(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        listener.onError(exception.getMessage());
                    }
                }
        );
    }

    public interface OnPhotoTakenListener {
        void onSuccess(File photoFile);
        void onError(String errorMessage);
    }
    // AJOUT : Gérer le flash
    private androidx.camera.core.Camera camera;
    private boolean isFlashOn = false;

    // (Modifie la méthode startCamera pour récupérer l'objet camera)
    // Dans startCamera, remplace la ligne :
    // cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture);
    // PAR :
    // camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture);

    public void toggleFlash(android.widget.ImageView flashButton) {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isFlashOn = !isFlashOn;
            camera.getCameraControl().enableTorch(isFlashOn);

            // Change l'icône ou la couleur du bouton (ici on change la couleur)
            if (isFlashOn) {
                flashButton.setColorFilter(android.graphics.Color.YELLOW);
            } else {
                flashButton.setColorFilter(android.graphics.Color.WHITE);
            }
        }
    }
}


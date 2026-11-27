package com.example.scandepenses;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;

public class ScannerActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private PreviewView viewFinder;
    private CameraManager cameraManager;

    // 1. Ce "Launcher" sert à ouvrir la galerie de fichiers (Images ET PDF)
    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    // L'utilisateur a choisi un fichier (PDF ou Image)
                    // On lance l'écran d'analyse avec ce fichier
                    Intent intent = new Intent(ScannerActivity.this, OcrActivity.class);
                    intent.setData(uri); // On passe le fichier à l'écran suivant
                    startActivity(intent);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        viewFinder = findViewById(R.id.viewFinder);
        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnCapture = findViewById(R.id.btnCapture);
        ImageView btnGallery = findViewById(R.id.btnGallery);
        ImageView btnFlash = findViewById(R.id.btnFlash); // On récupère le bouton flash

        cameraManager = new CameraManager();

        // Bouton Retour
        btnBack.setOnClickListener(v -> finish());

        // BOUTON FLASH (Nouveau)
        btnFlash.setOnClickListener(v -> {
            cameraManager.toggleFlash(btnFlash);
        });

        // BOUTON CAPTURE
        btnCapture.setOnClickListener(v -> {
            Toast.makeText(this, "Capture en cours...", Toast.LENGTH_SHORT).show();
            cameraManager.takePhoto(this, new CameraManager.OnPhotoTakenListener() {
                @Override
                public void onSuccess(File photoFile) {
                    // La photo est prise. On lance l'écran d'analyse !
                    Intent intent = new Intent(ScannerActivity.this, OcrActivity.class);
                    intent.setData(Uri.fromFile(photoFile)); // On passe la photo
                    startActivity(intent);
                }

                @Override
                public void onError(String errorMessage) {
                    Toast.makeText(ScannerActivity.this, "Erreur : " + errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        });

        // BOUTON GALERIE (PDF & Images)
        btnGallery.setOnClickListener(v -> {
            // On ouvre l'explorateur de fichiers. "*/*" permet de choisir PDF ou Image.
            filePickerLauncher.launch("*/*");
        });

        // Permissions
        if (allPermissionsGranted()) {
            cameraManager.startCamera(this, this, viewFinder);
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                cameraManager.startCamera(this, this, viewFinder);
            } else {
                Toast.makeText(this, "L'accès à la caméra est nécessaire.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
package com.example.scandepenses;

import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class OcrActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr);

        // On récupère l'image passée par l'écran de scan
        Uri imageUri = getIntent().getData();

        if (imageUri != null) {
            Toast.makeText(this, "Image reçue pour analyse !", Toast.LENGTH_SHORT).show();
            // ICI : Nous lancerons ML Kit à l'étape suivante !
        }
    }
}
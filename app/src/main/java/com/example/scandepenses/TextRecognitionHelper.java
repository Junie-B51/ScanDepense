package com.example.scandepenses;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.IOException;

public class TextRecognitionHelper {

    // Interface pour renvoyer le résultat à l'écran
    public interface OnTextRecognizedListener {
        void onSuccess(String extractedText);
        void onFailure(Exception e);
    }

    public void recognizeText(Context context, Uri fileUri, OnTextRecognizedListener listener) {
        try {
            InputImage image;

            // 1. On vérifie si c'est un PDF ou une Image
            String mimeType = context.getContentResolver().getType(fileUri);

            if (mimeType != null && mimeType.equals("application/pdf")) {
                // C'est un PDF : on le transforme en image pour pouvoir le lire
                Bitmap pdfBitmap = convertPdfToBitmap(context, fileUri);
                image = InputImage.fromBitmap(pdfBitmap, 0);
            } else {
                // C'est une image (Photo) : on la charge directement
                image = InputImage.fromFilePath(context, fileUri);
            }

            // 2. On lance l'Intelligence Artificielle de Google (ML Kit)
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        // Succès : on renvoie le texte trouvé
                        listener.onSuccess(visionText.getText());
                    })
                    .addOnFailureListener(e -> {
                        // Échec : on renvoie l'erreur
                        listener.onFailure(e);
                    });

        } catch (IOException e) {
            listener.onFailure(e);
        }
    }

    // Méthode pour transformer la 1ère page d'un PDF en image (Bitmap)
    private Bitmap convertPdfToBitmap(Context context, Uri pdfUri) throws IOException {
        ParcelFileDescriptor fileDescriptor = context.getContentResolver().openFileDescriptor(pdfUri, "r");
        if (fileDescriptor == null) throw new IOException("Impossible d'ouvrir le PDF");

        PdfRenderer pdfRenderer = new PdfRenderer(fileDescriptor);
        PdfRenderer.Page page = pdfRenderer.openPage(0); // On lit la 1ère page

        Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

        page.close();
        pdfRenderer.close();
        fileDescriptor.close();

        return bitmap;
    }
}
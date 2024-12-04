
package com.example.l5;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private EditText journalIdEditText;
    private Button downloadButton;
    private Button viewButton;
    private Button deleteButton;
    private TextView statusTextView;
    private File downloadedFile;
    private static final int REQUEST_STORAGE_PERMISSION = 123;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String SHOW_POPUP_KEY = "showPopup";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        journalIdEditText = findViewById(R.id.journalIdEditText);
        downloadButton = findViewById(R.id.downloadButton);
        viewButton = findViewById(R.id.viewButton);
        deleteButton = findViewById(R.id.deleteButton);
        statusTextView = findViewById(R.id.statusTextView);

        viewButton.setEnabled(false);
        deleteButton.setEnabled(false);

        downloadButton.setOnClickListener(v -> requestStoragePermission());
        viewButton.setOnClickListener(v -> openPDF(downloadedFile));
        deleteButton.setOnClickListener(v -> deleteFile(downloadedFile));

        // Проверка, нужно ли показывать всплывающее окно
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean showPopup = prefs.getBoolean(SHOW_POPUP_KEY, true); // По умолчанию показывать

        if (showPopup) {
            showPopupInstruction();
        }
    }

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        } else {
            downloadFile();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadFile();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void downloadFile() {
        String journalId = journalIdEditText.getText().toString();
        if (journalId.isEmpty()) {
            statusTextView.setText("Введите ID журнала");
            return;
        }
        statusTextView.setText("Загрузка");
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url("https://ntv.ifmo.ru/file/journal/" + journalId + ".pdf")
                        .build();
                Response response = client.newCall(request).execute();

                if (response.isSuccessful() && response.header("Content-Type") != null && response.header("Content-Type").startsWith("application/pdf")) {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                        runOnUiThread(() -> statusTextView.setText("Ошибка"));
                        return;
                    }
                    downloadedFile = new File(downloadsDir, journalId + ".pdf");
                    try (InputStream inputStream = response.body().byteStream();
                         OutputStream outputStream = new FileOutputStream(downloadedFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                    runOnUiThread(() -> {
                        statusTextView.setText("Загрузка завершена!");
                        viewButton.setEnabled(true);
                        deleteButton.setEnabled(true);
                    });
                } else {
                    runOnUiThread(() -> statusTextView.setText("Файл не найден."));
                }
                response.close();

            } catch (IOException e) {
                runOnUiThread(() -> statusTextView.setText("Ошибка при загрузке файла: " + e.getMessage()));
                Log.e("Download Error", e.getMessage(), e);
            }
        }).start();
    }

    private void openPDF(File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, "File not found.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No PDF viewer found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFile(File file) {
        if (file != null && file.exists()) {
            if (file.delete()) {
                statusTextView.setText("Файл удален");
                viewButton.setEnabled(false);
                deleteButton.setEnabled(false);
                downloadedFile = null;
            } else {
                statusTextView.setText("Ошибка при удалении файла");
            }
        }
    }

    private void showPopupInstruction() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Инструкция");
        builder.setMessage("Для загрузки журнала введите ID и нажмите загрузить.");

        final CheckBox dontShowAgainCheckBox = new CheckBox(this);
        dontShowAgainCheckBox.setText("Больше не показывать");
        builder.setView(dontShowAgainCheckBox);

        builder.setPositiveButton("OK", (dialog, id) -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(SHOW_POPUP_KEY, !dontShowAgainCheckBox.isChecked());
            editor.apply();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}

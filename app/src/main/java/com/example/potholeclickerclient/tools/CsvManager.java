package com.example.potholeclickerclient.tools;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class CsvManager {
    private static final String PREFS = "csv_prefs";
    private static final String KEY_CSV_URI = "csv_uri";
    private static final String FILE_DEFAULT_NAME = "logfile.csv";
    private static final String CSV_HEADER = "type,timestamp,lat,lon\n";

    private final Context context;
    private final ContentResolver contentResolver;
    private @Nullable Uri csvUri;

    public CsvManager(Context context) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
        this.csvUri = loadCsvUri();
    }

    // --- Public API ---
    public void createNewCsvFile(ActivityResultLauncher<String> launcher) {
        launcher.launch(FILE_DEFAULT_NAME);
    }

    public void handleCreateCsvResult(Uri uri) {
        if (uri != null) {
            takePersistableUriPermission(uri);
            this.csvUri = uri;
            saveCsvUri(uri);
            writeCsvHeaderIfEmpty(uri);
            Toast.makeText(context, "CSV file location set.", Toast.LENGTH_SHORT).show();
        }
    }

    public void appendEvent(String type, long ts, @Nullable Double lat, @Nullable Double lon) {
        if (csvUri == null) {
            Toast.makeText(context, "Choose a CSV location first.", Toast.LENGTH_SHORT).show();
            // You could add a callback here to ask the Activity to launch the file picker
            return;
        }
        String latStr = (lat == null) ? "" : String.valueOf(lat);
        String lonStr = (lon == null) ? "" : String.valueOf(lon);
        String line = type + "," + ts + "," + latStr + "," + lonStr;
        appendLineToCsv(line);
    }

    public boolean isCsvFileChosen() {
        return csvUri != null;
    }


    // --- Private Helper Methods ---
    private void saveCsvUri(Uri uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_CSV_URI, uri.toString()).apply();
    }

    @Nullable
    private Uri loadCsvUri() {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = sp.getString(KEY_CSV_URI, null);
        return s == null ? null : Uri.parse(s);
    }

    private void takePersistableUriPermission(Uri uri) {
        contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    private void writeCsvHeaderIfEmpty(Uri uri) {
        try (InputStream is = contentResolver.openInputStream(uri)) {
            if (is != null && is.available() == 0) {
                appendLineToCsv(CSV_HEADER.trim()); // Use append to avoid overwriting race condition
            }
        } catch (IOException ignored) {}
    }

    private void appendLineToCsv(String line) {
        if (csvUri == null) return;
        try (OutputStream os = contentResolver.openOutputStream(csvUri, "wa");
             OutputStreamWriter w = new OutputStreamWriter(os)) {
            w.write(line);
            if (!line.endsWith("\n")) w.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to write to CSV.", Toast.LENGTH_SHORT).show();
        }
    }
}

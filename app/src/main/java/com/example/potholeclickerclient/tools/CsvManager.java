package com.example.potholeclickerclient.tools;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class CsvManager {
    private static final String EOL = "\n";
    private static final String PREFS = "csv_prefs";
    private static final String KEY_BASE_URI = "base__uri";
    private static final String KEY_TIMESTAMP_SUFFIX = "timestamp_suffix";
    private static final String FILE_DEFAULT_NAME = "logfile.csv";
    private final Context context;
    private final ContentResolver contentResolver;
    private final Map<FileType, Uri> csvUris = new EnumMap<>(FileType.class);
    private final Map<FileType, OutputStreamWriter> openWriters = new EnumMap<>(FileType.class);
    private Uri baseUri;
    private String timestampSuffix;

    public CsvManager(Context context) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
        loadAndGenerateURIs();
    }

    public String getFileDefaultName() {
        return FILE_DEFAULT_NAME;
    }

    public void handleCreateCsvResult(Uri uri) {
        if (uri != null) {
            takePersistableUriPermission(uri);
            this.baseUri = uri;
            this.timestampSuffix = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            saveSessionInfo(this.baseUri, this.timestampSuffix);

            generateURIsFromBase();
            Toast.makeText(context, "CSV files location set.", Toast.LENGTH_SHORT).show();
        }
    }

    public boolean isCsvFileChosen() {
        return baseUri != null;
    }

    public void appendEvent(String type, long ts, @Nullable Double lat, @Nullable Double lon) {
        String latStr = (lat == null) ? "" : String.valueOf(lat);
        String lonStr = (lon == null) ? "" : String.valueOf(lon);
        String line = ts + "," + latStr + "," + lonStr + "," + type;
        appendLineToCsv(FileType.LABELS, line);
    }

    public synchronized void appendLineToCsv(@NonNull FileType fileType, @NonNull String line) {
        try {
            OutputStreamWriter writer = getWriter(fileType);
            writer.write(line);
            writer.write(EOL);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to write to CSV file:" + fileType.getSuffix());
            close(fileType);
        }
    }

    public synchronized void closeAll()
    {
        for(Map.Entry<FileType, OutputStreamWriter> entry : openWriters.entrySet())
        {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Failed to close CSV file:" + entry.getKey().getSuffix());
            }
        }
        openWriters.clear();
    }


    // --- Private Helper Methods ---
    private void saveSessionInfo(Uri uri, String timestampSuffix) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_BASE_URI, uri.toString())
                .putString(KEY_TIMESTAMP_SUFFIX, timestampSuffix)
                .apply();
    }

    private void loadAndGenerateURIs() {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String uriString = sp.getString(KEY_BASE_URI, null);
        String tsString = sp.getString(KEY_TIMESTAMP_SUFFIX, null);

        if(uriString != null && tsString != null) {
            this.baseUri = Uri.parse(uriString);
            this.timestampSuffix = tsString;
            generateURIsFromBase();
        }
    }

    private String getCsvFileName(FileType type) {
        String suffix = type.getSuffix();
        String timestamp = this.timestampSuffix;
        return suffix + "_" + timestamp  + ".csv";
    }

    private void generateURIsFromBase() {
        if(baseUri == null) return;
        closeAll();
        csvUris.clear();

        try {
            String baseDocumentId = DocumentsContract.getTreeDocumentId(baseUri);
            Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(baseUri, baseDocumentId);

            for(FileType type: FileType.values()) {
                String newFileName = getCsvFileName(type);
                Uri targetUri = DocumentsContract.createDocument(contentResolver, parentUri, "text/csv", newFileName);
                csvUris.put(type, targetUri);
                writeCsvHeaderIfEmpty(targetUri, type);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to create derived CSV files");
        }
    }

    private void takePersistableUriPermission(Uri uri) {
        contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    private void writeCsvHeaderIfEmpty(Uri uri, FileType type) {
        try (InputStream is = contentResolver.openInputStream(uri)) {
            if (is != null && is.available() == 0) {
                try (OutputStream os = contentResolver.openOutputStream(uri, "w"); // "w" to overwrite/create
                     OutputStreamWriter w = new OutputStreamWriter(os)) {
                    w.write(type.getFileHeader() + EOL);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not check or write header for " + uri + ". Error: " + e.getMessage());
        }
    }

    @NonNull
    private OutputStreamWriter getWriter(FileType fileType) throws IOException {
        if(openWriters.containsKey(fileType)) {
            OutputStreamWriter writer = openWriters.get(fileType);
            if(writer != null) {
                return writer;
            }
        }

        Uri targetUri = csvUris.get(fileType);
        if (targetUri == null) throw new IOException("CSV file URI for type: " + fileType.name() + " has not been setup.");

        OutputStream os = contentResolver.openOutputStream(targetUri, "wa");
        if(os == null) throw new IOException("Failed to open output stream for URI: " + targetUri);

        OutputStreamWriter w = new OutputStreamWriter(os);
        openWriters.put(fileType, w);
        return w;
    }

    private synchronized void close(@NonNull FileType fileType) {
        OutputStreamWriter writer = openWriters.remove(fileType);
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

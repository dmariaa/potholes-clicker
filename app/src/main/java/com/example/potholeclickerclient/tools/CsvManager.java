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
    private static final String FILE_DEFAULT_NAME = "logfile.csv";
    private final Context context;
    private final ContentResolver contentResolver;
    private final Map<FileType, Uri> csvUris = new EnumMap<>(FileType.class);
    private final Map<FileType, OutputStreamWriter> openWriters = new EnumMap<>(FileType.class);

    public CsvManager(Context context) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
    }

    public String getFileDefaultName() {
        return FILE_DEFAULT_NAME;
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

    private String getCsvFileName(FileType type, Date timestamp) {
        String suffix = type.getSuffix();
        String timestampSuffix = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(timestamp);
        return suffix + "_" + timestampSuffix  + ".csv";
    }

//    private void generateURIsFromBase() {
//        if(baseUri == null) return;
//        closeAll();
//        csvUris.clear();
//
//        try {
//            String baseDocumentId = DocumentsContract.getTreeDocumentId(baseUri);
//            Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(baseUri, baseDocumentId);
//
//            for(FileType type: FileType.values()) {
//                String newFileName = getCsvFileName(type);
//                Uri targetUri = DocumentsContract.createDocument(contentResolver, parentUri, "text/csv", newFileName);
//                csvUris.put(type, targetUri);
//                writeCsvHeaderIfEmpty(targetUri, type);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//            System.err.println("Failed to create derived CSV files");
//        }
//    }

    public void createSessionFiles(Uri directoryUri, Date timestamp) {
        takePersistableUriPermission(directoryUri);
        closeAll();
        csvUris.clear();

        try {
            String baseDocumentId = DocumentsContract.getTreeDocumentId(directoryUri);
            Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, baseDocumentId);

            for(FileType type: FileType.values()) {
                String newFileName = getCsvFileName(type, timestamp);
                Uri targetUri = DocumentsContract.createDocument(contentResolver, parentUri, "text/csv", newFileName);
                csvUris.put(type, targetUri);
                writeCsvHeaderIfEmpty(targetUri, type);
            }
            Toast.makeText(context, "CSV session files created", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            System.err.println("Failed to create derived CSV files");
            e.printStackTrace();
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

package com.example.potholeclickerclient.tools;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Manages writing a session's metadata to a single YAML file.
 * This class is designed to be used once per session to write the metadata file.
 */
@Singleton
public class MetadataManager {
    private final ContentResolver contentResolver;

    @Inject
    public MetadataManager(Context context) {
        this.contentResolver = context.getContentResolver();
    }

    public void saveMetadata(Uri parentUri, String sensorName, Date timestamp) {
        String sessionStartTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()).format(timestamp);

        String metadata = "session_start_time: " + sessionStartTime + "\n" +
                "sensor_name: " + sensorName + "\n";
        writeMetadataToFile(parentUri, timestamp, metadata);
    }

    private void writeMetadataToFile(Uri parentDirectoryUri, Date timestamp, String yamlContent) {
        String timestampSuffix = "_" + new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(timestamp);
        String fileName = "metadata_" + timestampSuffix + ".yaml";
        String mimeType = "application/x-yaml";

        try {
            String baseDocumentId = DocumentsContract.getTreeDocumentId(parentDirectoryUri);
            Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(parentDirectoryUri, baseDocumentId);

            Uri metadataFileUri = DocumentsContract.createDocument(contentResolver,
                    parentUri, mimeType, fileName);

            if (metadataFileUri != null) {
                try (OutputStream os = contentResolver.openOutputStream(metadataFileUri, "w");
                     OutputStreamWriter writer = new OutputStreamWriter(os)) {
                    writer.write(yamlContent);
                }
            } else {
                throw new IOException("Failed to create metadata file: " + fileName);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }
}

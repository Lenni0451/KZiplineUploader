package net.lenni0451.kziplineuploader;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Clipboard {

    /**
     * Determines the current content type of the clipboard by checking available MIME types.
     */
    public static ContentType getContentType() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("wl-paste", "--list-types").start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            boolean hasOutput = false;

            while ((line = reader.readLine()) != null) {
                hasOutput = true;
                line = line.toLowerCase();

                if (line.contains("text/uri-list")) {
                    return ContentType.FILES;
                } else if (line.startsWith("image/")) {
                    return ContentType.IMAGE;
                } else if (line.startsWith("text/") || line.contains("string")) {
                    return ContentType.TEXT;
                }
            }
            return hasOutput ? ContentType.UNKNOWN : ContentType.EMPTY;
        } finally {
            process.waitFor();
        }
    }

    /**
     * Reads text from the clipboard.
     */
    public static String readText() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("wl-paste", "--type", "text/plain").start();
        StringBuilder text = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append(System.lineSeparator());
            }
        }
        process.waitFor();
        return text.toString().trim();
    }

    /**
     * Reads an image from the clipboard as a raw byte array.
     * Defaults to requesting a PNG format.
     */
    public static byte[] readImage() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("wl-paste", "--type", "image/png").start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (InputStream is = process.getInputStream()) {
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
        }
        process.waitFor();
        return buffer.toByteArray();
    }

    /**
     * Reads copied files from the clipboard by parsing the text/uri-list MIME type.
     */
    public static List<File> readFiles() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("wl-paste", "--type", "text/uri-list").start();
        List<File> files = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    try {
                        files.add(new File(new URI(line)));
                    } catch (URISyntaxException e) {
                        System.err.println("Invalid URI in clipboard: " + line);
                    }
                }
            }
        }
        process.waitFor();
        return files;
    }

    /**
     * Writes a string to the clipboard using wl-copy.
     */
    public static void writeText(String text) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("wl-copy").start();

        try (OutputStream os = process.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
            writer.write(text);
        }
        process.waitFor();
    }


    public enum ContentType {
        TEXT, IMAGE, FILES, EMPTY, UNKNOWN
    }

}

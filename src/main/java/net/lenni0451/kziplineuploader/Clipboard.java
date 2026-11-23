package net.lenni0451.kziplineuploader;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Clipboard {

    /**
     * Preferred text types, best first. Compared after normalization (lowercase, no whitespace).
     */
    private static final List<String> TEXT_TYPES = List.of(
            "text/plain;charset=utf-8",
            "utf8_string",
            "text/plain",
            "string",
            "text"
    );
    /**
     * Preferred image types, best first. Any other image/* type is used as a fallback.
     */
    private static final List<String> IMAGE_TYPES = List.of(
            "image/png",
            "image/webp",
            "image/jpeg",
            "image/gif"
    );

    /**
     * Determines what the clipboard contains and which MIME type should be requested to read it.<br>
     * All offered types are collected first and then chosen by priority (files > image > text),
     * because the order in which applications offer their types is arbitrary
     * (e.g. Firefox offers {@code text/html} before {@code image/png} when copying an image).
     */
    public static Content detect() throws IOException, InterruptedException {
        List<String> types = listTypes();
        if (types.isEmpty()) return new Content(ContentType.EMPTY, null);

        String uriList = find(types, List.of("text/uri-list"));
        if (uriList != null && !readFiles().isEmpty()) {
            return new Content(ContentType.FILES, uriList);
        }

        String image = find(types, IMAGE_TYPES);
        if (image == null) image = types.stream().filter(t -> normalize(t).startsWith("image/")).findFirst().orElse(null);
        if (image != null) return new Content(ContentType.IMAGE, image);

        String text = find(types, TEXT_TYPES);
        if (text == null) text = types.stream().filter(t -> normalize(t).startsWith("text/")).findFirst().orElse(null);
        if (text != null) return new Content(ContentType.TEXT, text);

        return new Content(ContentType.UNKNOWN, null);
    }

    /**
     * Reads text from the clipboard using the given MIME type (as returned by {@link #detect()}).
     */
    public static String readText(final String mimeType) throws IOException, InterruptedException {
        return new String(read(mimeType), charsetOf(mimeType));
    }

    /**
     * Reads an image from the clipboard as a raw byte array using the given MIME type (as returned by {@link #detect()}).
     */
    public static byte[] readImage(final String mimeType) throws IOException, InterruptedException {
        return read(mimeType);
    }

    /**
     * Reads copied local files from the clipboard by parsing the text/uri-list MIME type.<br>
     * Non-file URIs (e.g. http links copied from a browser) are ignored.
     */
    public static List<File> readFiles() throws IOException, InterruptedException {
        String uriList = new String(read("text/uri-list"), StandardCharsets.UTF_8);
        List<File> files = new ArrayList<>();
        for (String line : uriList.split("\r?\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                URI uri = new URI(line);
                if ("file".equalsIgnoreCase(uri.getScheme())) files.add(new File(uri));
            } catch (Exception e) {
                System.err.println("Invalid URI in clipboard: " + line);
            }
        }
        return files;
    }

    /**
     * Writes a string to the clipboard using wl-copy.
     */
    public static void writeText(final String text) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("wl-copy").start();

        try (OutputStream os = process.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
            writer.write(text);
        }
        process.waitFor();
    }

    private static List<String> listTypes() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("wl-paste", "--list-types").redirectError(ProcessBuilder.Redirect.DISCARD).start();
        List<String> types = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) types.add(line.trim());
            }
        } finally {
            process.waitFor();
        }
        return types;
    }

    private static byte[] read(final String mimeType) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("wl-paste", "--no-newline", "--type", mimeType).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (InputStream is = process.getInputStream()) {
            return is.readAllBytes();
        } finally {
            process.waitFor();
        }
    }

    /**
     * Returns the first offered type matching the preferred types (in preference order).
     * The original (non-normalized) type string is returned, since wl-paste needs the exact name.
     */
    private static String find(final List<String> offered, final List<String> preferred) {
        for (String wanted : preferred) {
            for (String type : offered) {
                if (normalize(type).equals(wanted)) return type;
            }
        }
        return null;
    }

    private static String normalize(final String type) {
        return type.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").replace("\"", "");
    }

    private static Charset charsetOf(final String mimeType) {
        for (String param : normalize(mimeType).split(";")) {
            if (param.startsWith("charset=")) {
                try {
                    return Charset.forName(param.substring("charset=".length()));
                } catch (Exception ignored) {
                }
            }
        }
        return StandardCharsets.UTF_8;
    }


    public record Content(ContentType type, String mimeType) {
        /**
         * The MIME type without parameters, e.g. {@code text/plain;charset=utf-8} -> {@code text/plain}.
         */
        public String baseMimeType() {
            return this.mimeType == null ? null : normalize(this.mimeType).split(";")[0];
        }

        /**
         * A file extension fitting the image MIME type, e.g. {@code image/jpeg} -> {@code jpg}.
         */
        public String imageExtension() {
            String base = this.baseMimeType();
            String sub = base.substring(base.indexOf('/') + 1);
            if (sub.endsWith("+xml")) sub = sub.substring(0, sub.length() - 4);
            return switch (sub) {
                case "jpeg" -> "jpg";
                case "x-icon", "vnd.microsoft.icon" -> "ico";
                default -> sub;
            };
        }
    }

    public enum ContentType {
        TEXT, IMAGE, FILES, EMPTY, UNKNOWN
    }

}

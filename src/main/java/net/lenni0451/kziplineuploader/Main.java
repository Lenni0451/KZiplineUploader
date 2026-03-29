package net.lenni0451.kziplineuploader;

import lombok.extern.slf4j.Slf4j;
import net.lenni0451.commons.httpclient.HttpClient;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.constants.ContentTypes;
import net.lenni0451.commons.httpclient.constants.HttpHeaders;
import net.lenni0451.commons.httpclient.content.HttpContent;
import net.lenni0451.commons.httpclient.model.ContentType;
import net.lenni0451.kziplineuploader.notification.PlasmaJob;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.RenderedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
public class Main {

    private static final DataFlavor TEXT = DataFlavor.stringFlavor;
    private static final DataFlavor IMAGE = DataFlavor.imageFlavor;
    private static final DataFlavor FILE = DataFlavor.javaFileListFlavor;

    private static HttpClient httpClient;
    private static String ziplineUrl;

    public static void main(String[] args) {
        Thread mainThread = Thread.currentThread();
        try (PlasmaJob job = new PlasmaJob("Zipline Uploader", PlasmaJob.ICON_INFORMATION, mainThread::interrupt)) {
            if (!loadConfig(job)) {
                return;
            }
            Thread.sleep(1000); // Have to wait here to make sure the job is initialized
            try {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                if (clipboard.isDataFlavorAvailable(TEXT)) {
                    uploadText(clipboard, job);
                } else if (clipboard.isDataFlavorAvailable(IMAGE)) {
                    uploadImage(clipboard, job);
                } else if (clipboard.isDataFlavorAvailable(FILE)) {
                    uploadFiles(clipboard, job);
                } else {
                    job.sendNotification(PlasmaJob.ICON_ERROR, "Unsupported clipboard content", "The clipboard does not contain text, images, or files.");
                    log.warn("The clipboard contains an unsupported data flavor. Available flavors:");
                    for (DataFlavor flavor : clipboard.getAvailableDataFlavors()) {
                        log.info(" - {}", flavor);
                    }
                }
            } catch (IOException | UnsupportedFlavorException | ClassCastException e) {
                log.error("Failed to read clipboard", e);
                job.abort("Failed to read clipboard");
            }
        } catch (Throwable t) {
            log.error("Unhandled exception", t);
        } finally {
            try {
                Thread.sleep(1000); // Have to wait here to ensure the clipboard is set and the job is finished
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean loadConfig(final PlasmaJob job) {
        try {
            File configFile = new File("config.json");
            if (configFile.exists()) {
                JSONObject config = new JSONObject(Files.readString(configFile.toPath(), StandardCharsets.UTF_8));
                String url = config.getString("url");
                boolean keepFileName = config.getBoolean("keep_file_name");
                String token = config.getString("token");
                httpClient = new HttpClient()
                        .setHeader(HttpHeaders.USER_AGENT, "KZiplineUploader/1.0")
                        .setHeader(HttpHeaders.AUTHORIZATION, token)
                        .setHeader("x-zipline-original-name", String.valueOf(keepFileName));
                ziplineUrl = url;
                return true;
            } else {
                JSONObject config = new JSONObject();
                config.put("url", "https://example.com");
                config.put("keep_file_name", true);
                config.put("token", "your_token_here");
                Files.writeString(configFile.toPath(), config.toString(4), StandardCharsets.UTF_8);
                log.info("Created config file: {}", configFile.getAbsoluteFile());
                job.sendNotification(PlasmaJob.ICON_INFORMATION, "Config", "Example config has been created<br>" +
                        "Please edit the config file and restart the uploader.<br>" +
                        configFile.getAbsolutePath());
                return false;
            }
        } catch (Throwable t) {
            log.error("Failed to load config", t);
            job.sendNotification(PlasmaJob.ICON_ERROR, "Config", "Failed to load config file");
            return false;
        }
    }

    private static void uploadText(final Clipboard clipboard, final PlasmaJob job) throws IOException, UnsupportedFlavorException {
        String text = (String) clipboard.getData(TEXT);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        uploadWithProgress(job, "text.txt", bytes.length, new ByteArrayInputStream(bytes), ContentTypes.TEXT_PLAIN);
    }

    private static void uploadImage(final Clipboard clipboard, final PlasmaJob job) throws IOException, UnsupportedFlavorException {
        Image image = (Image) clipboard.getData(IMAGE);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write((RenderedImage) image, "png", baos);
        byte[] bytes = baos.toByteArray();
        uploadWithProgress(job, "image.png", bytes.length, new ByteArrayInputStream(bytes), ContentTypes.IMAGE_PNG);
    }

    private static void uploadFiles(final Clipboard clipboard, final PlasmaJob job) throws IOException, UnsupportedFlavorException {
        List<File> files = (List<File>) clipboard.getData(FILE);
        if (files.isEmpty()) {
            job.abort("No files to upload");
        } else if (files.size() == 1 && files.getFirst().isFile()) {
            File file = files.getFirst();
            String mimeType = Files.probeContentType(file.toPath());
            ContentType contentType = mimeType == null ? ContentTypes.APPLICATION_OCTET_STREAM : ContentType.parse(mimeType);
            log.info("Using content type {} for file", contentType);
            uploadWithProgress(job, file.getName(), file.length(), new FileInputStream(file), contentType);
        } else {
            log.info("Clipboard contains {} files and/or directories, zipping them before upload...", files.size());
            job.setPhase("Zipping files...");
            File zipFile = FileZipper.createZipFile(files, job::setProgress);
            String dateAndTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss"));
            uploadWithProgress(job, "files " + dateAndTime + ".zip", zipFile.length(), new FileInputStream(zipFile), ContentType.parse("application/zip"));
        }
    }

    private static void uploadWithProgress(final PlasmaJob job, final String name, final long size, final InputStream input, final ContentType contentType) {
        String responseString;
        try {
            job.setPhase("Uploading...");
            HttpResponse response = httpClient
                    .post(ziplineUrl + "/api/upload")
                    .setContent(HttpContent.multiPartForm().addPart(
                            "file_field",
                            HttpContent.inputStream(contentType, new JobProgressInputStream(job, input, size), (int) size),
                            name
                    ))
                    .setStreamedRequest(true)
                    .execute();
            responseString = response.getContent().getAsString();
        } catch (Throwable t) {
            log.error("Failed to upload file", t);
            job.abort("Failed to upload file");
            return;
        }
        try {
            job.setPhase("Upload finished");
            JSONObject json = new JSONObject(responseString);
            if (json.has("error")) {
                String error = json.getString("error");
                log.error("Server returned error: {}", error);
                job.abort("Upload failed: " + error);
            } else if (json.has("files") && json.getJSONArray("files").length() == 1 && json.getJSONArray("files").getJSONObject(0).has("url")) {
                String url = json.getJSONArray("files").getJSONObject(0).getString("url");
                StringSelection selection = new StringSelection(url);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                job.finish();
            } else {
                log.error("Server returned unexpected response: {}", responseString);
                job.abort("Server returned unexpected response");
            }
        } catch (Throwable t) {
            log.error("Server returned invalid response: {}", responseString, t);
            job.abort("Server returned invalid response");
        }
    }

}

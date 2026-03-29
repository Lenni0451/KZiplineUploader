package net.lenni0451.kziplineuploader;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public class FileZipper {

    public static File createZipFile(final List<File> files, final IntConsumer progressConsumer) throws IOException {
        Map<String, File> zipEntries = new HashMap<>();
        for (File file : files) {
            putFile(zipEntries, "", file);
        }

        File zipFile = File.createTempFile("ZiplineUploader", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            int i = 0;
            for (Map.Entry<String, File> entry : zipEntries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                try (FileInputStream fis = new FileInputStream(entry.getValue())) {
                    fis.transferTo(zos);
                }
                zos.closeEntry();

                progressConsumer.accept(++i * 100 / zipEntries.size());
            }
        }
        return zipFile;
    }

    private static void putFile(final Map<String, File> files, final String path, final File file) {
        if (file.isFile()) {
            putUniqueFile(files, path, file);
        } else if (file.isDirectory()) {
            File[] subFiles = file.listFiles();
            if (subFiles != null) {
                for (File subFile : subFiles) {
                    putFile(files, path + file.getName() + "/", subFile);
                }
            }
        } else {
            log.error("File is neither a regular file nor a directory: {}", file.getAbsolutePath());
        }
    }

    private static void putUniqueFile(final Map<String, File> files, final String path, final File file) {
        final String fileName = file.getName();
        String zipEntryName = fileName;
        int id = 2;
        while (files.containsKey(zipEntryName)) {
            if (fileName.contains(".")) {
                int dotIndex = fileName.lastIndexOf('.');
                zipEntryName = fileName.substring(0, dotIndex) + " (" + id++ + ")." + fileName.substring(dotIndex);
            } else {
                zipEntryName = fileName + " (" + id++ + ")";
            }
        }
        files.put(path + zipEntryName, file);
    }

}

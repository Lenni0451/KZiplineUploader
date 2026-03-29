package net.lenni0451.kziplineuploader;

import lombok.RequiredArgsConstructor;
import net.lenni0451.kziplineuploader.notification.PlasmaJob;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

@RequiredArgsConstructor
public class JobProgressInputStream extends InputStream {

    private final PlasmaJob job;
    private final InputStream inputStream;
    private final long remainingBytes;
    private long read;
    private long lastUpdate = 0;

    @Override
    public int read() throws IOException {
        int read = this.inputStream.read();
        if (read == -1) return -1;
        this.read++;
        this.updateProgress();
        return read;
    }

    @Override
    public int read(@NotNull final byte[] b, final int off, final int len) throws IOException {
        int read = this.inputStream.read(b, off, len);
        if (read == -1) return -1;
        this.read += read;
        this.updateProgress();
        return read;
    }

    @Override
    public void close() throws IOException {
        this.inputStream.close();
    }

    private void updateProgress() {
        long now = System.currentTimeMillis();
        if (now - this.lastUpdate > 250) {
            this.lastUpdate = now;
            int percent = (int) ((this.read * 100) / this.remainingBytes);
            this.job.setProgress(percent);
        }
    }

}

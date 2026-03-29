package net.lenni0451.kziplineuploader.notification;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.lenni0451.kziplineuploader.notification.dbus.JobViewServer;
import net.lenni0451.kziplineuploader.notification.dbus.JobViewV2;
import net.lenni0451.kziplineuploader.notification.dbus.Notifications;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.types.UInt32;

@Slf4j
@Getter
public class PlasmaJob implements AutoCloseable {

    public static final String ICON_INFORMATION = "dialog-information";
    public static final String ICON_WARNING = "dialog-warning";
    public static final String ICON_ERROR = "dialog-error";

    private final String appName;
    private final DBusConnection connection;
    private final JobViewV2 jobView;
    private volatile boolean cancelled = false;

    /**
     * @param taskName The main title of the job
     * @param iconPath An absolute path to a local image (e.g., "/tmp/logo.png") or a system icon name
     * @param onCancel A callback triggered if the user clicks the "Stop" button in Plasma
     */
    public PlasmaJob(final String taskName, final String iconPath, final Runnable onCancel) throws Exception {
        this.appName = taskName;
        this.connection = DBusConnectionBuilder.forSessionBus().build();

        JobViewServer server = this.connection.getRemoteObject(
                "org.kde.kuiserver",
                "/JobViewServer",
                JobViewServer.class
        );

        // Capability 1 = Cancelable (shows the stop button)
        DBusPath jobPath = server.requestView(taskName, iconPath, 1);

        this.jobView = this.connection.getRemoteObject(
                "org.kde.kuiserver",
                jobPath.getPath(),
                JobViewV2.class
        );

        // Listen for the cancel signal
        this.connection.addSigHandler(JobViewV2.cancelRequested.class, signal -> {
            if (signal.getPath().equals(jobPath.getPath())) {
                this.cancelled = true;
                if (onCancel != null) {
                    onCancel.run();
                }
            }
        });
    }

    /**
     * Updates the main text next to the progress bar and resets the bar to 0%.
     *
     * @param phaseName The new phase description (e.g., "Zipping files...", "Uploading archive...")
     */
    public void setPhase(final String phaseName) {
        if (this.cancelled) return;
        this.jobView.setInfoMessage(phaseName);
        this.jobView.setPercent(new UInt32(0));
    }

    /**
     * Updates the progress bar percentage (0-100).
     *
     * @param percent An integer from 0 to 100 representing the completion percentage of the current phase
     */
    public void setProgress(final int percent) {
        if (this.cancelled) return;
        this.jobView.setPercent(new UInt32(Math.clamp(percent, 0, 100)));
    }

    /**
     * Updates a specific row of detail text below the progress bar.
     *
     * @param row   The index of the detail row to update (starting from 0)
     * @param label The label for this detail (e.g., "Status", "Target", "Destination")
     * @param value The value for this detail (e.g., "Compressed 50%", "archive.zip")
     */
    public void setDetail(final int row, final String label, final String value) {
        if (this.cancelled) return;
        this.jobView.setDescriptionField(new UInt32(row), label, value);
    }

    /**
     * Marks the job as successfully complete and dismisses the progress UI.
     */
    public void finish() {
        if (this.cancelled) return;
        this.jobView.terminate("");
        this.cancelled = true;
    }

    /**
     * Halts the job and displays an error message in the Plasma notification history.
     *
     * @param errorMessage A message describing the reason for the abortion (e.g., "Task aborted by user", "Network error occurred")
     */
    public void abort(final String errorMessage) {
        if (this.cancelled) return;
        this.jobView.terminate(errorMessage);
        this.cancelled = true;
    }

    /**
     * Sends a desktop notification using the standard Freedesktop notification system.
     *
     * @param title    The title of the notification (e.g., "Deployment Complete", "Error Occurred")
     * @param htmlBody The body of the notification, which can include simple HTML formatting (e.g., {@code <b>Success!</b> Your files have been uploaded.})
     */
    public void sendNotification(final String icon, final String title, final String htmlBody) {
        try {
            Notifications notifications = this.connection.getRemoteObject(
                    "org.freedesktop.Notifications",
                    "/org/freedesktop/Notifications",
                    Notifications.class
            );

            notifications.Notify(
                    this.appName,
                    new UInt32(0), // 0 means create a new notification
                    icon, // Standard info icon
                    title,
                    htmlBody,
                    new String[0], // No action buttons
                    new java.util.HashMap<>(), // No special hints needed
                    -1 // Let the desktop decide the expiration time
            );
        } catch (Throwable t) {
            log.error("Failed to send notification", t);
        }
    }

    @Override
    public void close() {
        if (this.connection != null) {
            if (!this.cancelled) {
                this.abort("Task aborted");
            }
            this.connection.disconnect();
        }
    }

}

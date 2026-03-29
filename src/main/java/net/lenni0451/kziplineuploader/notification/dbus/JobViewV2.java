package net.lenni0451.kziplineuploader.notification.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;

@DBusInterfaceName("org.kde.JobViewV2")
public interface JobViewV2 extends DBusInterface {

    void setInfoMessage(final String message);

    void setDescriptionField(final UInt32 number, final String name, final String value);

    void setPercent(final UInt32 percent);

    void terminate(final String errorMessage);


    class cancelRequested extends DBusSignal {
        public cancelRequested(final String path) throws DBusException {
            super(path);
        }
    }

}

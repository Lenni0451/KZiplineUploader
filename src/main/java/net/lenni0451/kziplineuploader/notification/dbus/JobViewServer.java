package net.lenni0451.kziplineuploader.notification.dbus;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

@DBusInterfaceName("org.kde.JobViewServer")
public interface JobViewServer extends DBusInterface {

    DBusPath requestView(final String appName, final String appIconName, final int capabilities);

}

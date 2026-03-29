package net.lenni0451.kziplineuploader.notification.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

@DBusInterfaceName("org.freedesktop.Notifications")
public interface Notifications extends DBusInterface {

    UInt32 Notify(
            final String appName,
            final UInt32 replacesId,
            final String appIcon,
            final String summary,
            final String body,
            final String[] actions,
            final Map<String, Variant<?>> hints,
            final int expireTimeout
    );

}

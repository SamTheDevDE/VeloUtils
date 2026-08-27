// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.integration

public enum class NetworkEventKind(public val configKey: String) {
    REPORT("reports"),
    HELPOP("helpop"),
    PUNISHMENT("punishments"),
    MAINTENANCE("maintenance"),
    STAFF_ACTIVITY("staff-activity"),
    ALERT("alerts"),
}

public fun interface NetworkEventSink {
    public fun emit(kind: NetworkEventKind, title: String, description: String)
}

public object NoopNetworkEventSink : NetworkEventSink {
    override fun emit(kind: NetworkEventKind, title: String, description: String) = Unit
}

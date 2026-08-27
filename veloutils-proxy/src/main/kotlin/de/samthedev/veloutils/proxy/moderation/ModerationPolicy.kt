// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.moderation

import de.samthedev.veloutils.api.Punishment
import de.samthedev.veloutils.api.PunishmentType
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

public object ModerationPolicy {
    public fun effective(punishments: Iterable<Punishment>, at: Instant, server: String?): List<Punishment> =
        punishments.filter { punishment ->
            punishment.isEffective(at) &&
                (punishment.server == null || punishment.server.equals(server, ignoreCase = true))
        }

    public fun isLoginDenied(punishment: Punishment): Boolean = punishment.type in setOf(PunishmentType.BAN, PunishmentType.IP_BAN)
    public fun isChatDenied(punishment: Punishment): Boolean = punishment.type == PunishmentType.MUTE
}

public class IpAddressHasher(secret: ByteArray) {
    private val key = secret.copyOf().also { require(it.size >= 32) { "IP hash key must be at least 32 bytes" } }

    public fun hash(address: InetAddress): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(address.hostAddress.toByteArray(StandardCharsets.US_ASCII)).joinToString("") { "%02x".format(it) }
    }
}


// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.ui

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import de.samthedev.veloutils.common.Page
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URI

public object ChatUi {
    private val timestamp = DateTimeFormatter.ofPattern("dd MMM uuuu • HH:mm").withZone(ZoneId.systemDefault())
    private val brand = Component.text("VeloUtils", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)

    public fun header(title: String): Component = Component.text()
        .append(Component.text("━━━━ ", NamedTextColor.DARK_GRAY))
        .append(brand)
        .append(Component.text(" • $title ", NamedTextColor.GRAY))
        .append(Component.text("━━━━", NamedTextColor.DARK_GRAY))
        .build()

    public fun success(message: String): Component = feedback("✓", NamedTextColor.GREEN, message)
    public fun warning(message: String): Component = feedback("!", NamedTextColor.YELLOW, message)
    public fun error(message: String): Component = feedback("✕", NamedTextColor.RED, message)
    public fun info(message: String): Component = feedback("•", NamedTextColor.AQUA, message)

    public fun field(label: String, value: String, color: NamedTextColor = NamedTextColor.WHITE): Component =
        Component.text().append(Component.text("$label: ", NamedTextColor.GRAY)).append(Component.text(value, color)).build()

    public fun player(name: String): Component = Component.text(name, NamedTextColor.AQUA)
    public fun server(name: String): Component = Component.text(name, NamedTextColor.GOLD)

    public fun status(value: String, positive: Boolean): Component = Component.text(
        value,
        if (positive) NamedTextColor.GREEN else NamedTextColor.RED,
        TextDecoration.BOLD,
    )

    public fun button(
        source: CommandSource,
        label: String,
        command: String,
        hover: String,
        destructive: Boolean = false,
    ): Component {
        val normalizedCommand = command.trim().let { if (it.startsWith('/')) it else "/$it" }
        val base = Component.text("[$label]", if (destructive) NamedTextColor.RED else NamedTextColor.AQUA)
        if (source !is Player) return base.append(Component.text(" $normalizedCommand", NamedTextColor.DARK_GRAY))
        val click = if (destructive) ClickEvent.suggestCommand(normalizedCommand) else ClickEvent.runCommand(normalizedCommand)
        return base.clickEvent(click).hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)))
    }

    public fun pagination(source: CommandSource, page: Page<*>, commandPrefix: String): Component {
        val builder = Component.text()
        if (page.hasPrevious) {
            builder.append(button(source, "◀ Previous", "$commandPrefix ${page.page - 1}", "Go to page ${page.page - 1}"))
                .append(Component.space())
        }
        builder.append(Component.text("Page ${page.page} / ${page.totalPages}", NamedTextColor.GRAY))
        if (page.hasNext) {
            builder.append(Component.space())
                .append(button(source, "Next ▶", "$commandPrefix ${page.page + 1}", "Go to page ${page.page + 1}"))
        }
        return builder.build()
    }

    public fun link(source: CommandSource, label: String, url: URI, hover: String): Component {
        val base = Component.text("[$label]", NamedTextColor.LIGHT_PURPLE)
        if (source !is Player) return base.append(Component.text(" $url", NamedTextColor.DARK_GRAY))
        return base.clickEvent(ClickEvent.openUrl(url.toString())).hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)))
    }

    public fun usage(source: CommandSource, syntax: String, description: String, backCommand: String? = null): List<Component> = buildList {
        add(error("Invalid usage."))
        add(field("Usage", syntax))
        add(Component.text(description, NamedTextColor.GRAY))
        if (backCommand != null) add(button(source, "Show options", backCommand, "Open command help"))
    }

    public fun format(instant: Instant?): String = instant?.let(timestamp::format) ?: "Never"

    public fun remaining(expiresAt: Instant?, now: Instant = Instant.now()): String = when {
        expiresAt == null -> "Permanent"
        !expiresAt.isAfter(now) -> "Expired"
        else -> humanDuration(Duration.between(now, expiresAt))
    }

    public fun humanDuration(duration: Duration): String {
        var seconds = duration.seconds.coerceAtLeast(0)
        val days = seconds / 86_400
        seconds %= 86_400
        val hours = seconds / 3_600
        seconds %= 3_600
        val minutes = seconds / 60
        return listOfNotNull(
            days.takeIf { it > 0 }?.let { "${it}d" },
            hours.takeIf { it > 0 }?.let { "${it}h" },
            minutes.takeIf { it > 0 }?.let { "${it}m" },
            seconds.takeIf { it > 0 || duration.isZero }?.let { "${it}s" },
        ).take(2).joinToString(" ")
    }

    public fun join(vararg components: Component): Component {
        val builder: TextComponent.Builder = Component.text()
        components.forEachIndexed { index, component ->
            if (index > 0) builder.append(Component.space())
            builder.append(component)
        }
        return builder.build()
    }

    private fun feedback(symbol: String, color: NamedTextColor, message: String): Component = Component.text()
        .append(Component.text("$symbol ", color, TextDecoration.BOLD))
        .append(Component.text(message, NamedTextColor.GRAY))
        .build()
}

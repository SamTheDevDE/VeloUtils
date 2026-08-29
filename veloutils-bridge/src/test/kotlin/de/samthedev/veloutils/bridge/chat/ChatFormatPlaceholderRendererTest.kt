// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatFormatPlaceholderRendererTest {
    private val miniMessage =
        MiniMessage.miniMessage()

    private val plain =
        PlainTextComponentSerializer.plainText()

    @Test
    fun `styled LuckPerms prefix keeps safe MiniMessage formatting`() {
        val renderer =
            ChatFormatPlaceholderRenderer(
                setOf(
                    "papi_luckperms_prefix",
                ),
            )

        val resolved =
            renderer.resolve(
                "{papi_luckperms_prefix} <gray><player></gray>",
                mapOf(
                    "papi_luckperms_prefix" to
                        "<bold><#8889EF>OWNER</#8889EF></bold>",
                ),
            )

        val component =
            deserialize(
                resolved,
                Placeholder.unparsed(
                    "player",
                    "SamTheDevDE",
                ),
            )

        assertEquals(
            "OWNER SamTheDevDE",
            plain.serialize(component),
        )

        assertTrue(
            descendants(component).any { child ->
                child.style().color()?.value() ==
                    0x8889EF &&
                    child.style().decoration(
                        TextDecoration.BOLD,
                    ) ==
                    TextDecoration.State.TRUE
            },
        )
    }

    @Test
    fun `ordinary placeholders stay literal`() {
        val renderer =
            ChatFormatPlaceholderRenderer(
                emptySet(),
            )

        val resolved =
            renderer.resolve(
                "{papi_example} <player>",
                mapOf(
                    "papi_example" to
                        "<red><bold>NOT FORMATTED</bold></red>",
                ),
            )

        val component =
            deserialize(
                resolved,
                Placeholder.unparsed(
                    "player",
                    "Sam",
                ),
            )

        assertEquals(
            "<red><bold>NOT FORMATTED</bold></red> Sam",
            plain.serialize(component),
        )
    }

    @Test
    fun `styled placeholders cannot inject interactive events`() {
        val renderer =
            ChatFormatPlaceholderRenderer(
                setOf(
                    "papi_luckperms_prefix",
                ),
            )

        val resolved =
            renderer.resolve(
                "{papi_luckperms_prefix} <player>",
                mapOf(
                    "papi_luckperms_prefix" to
                        "<click:run_command:'/op @a'>" +
                        "<hover:show_text:'nope'>" +
                        "<red>OWNER</red>" +
                        "</hover>" +
                        "</click>",
                ),
            )

        val component =
            deserialize(
                resolved,
                Placeholder.unparsed(
                    "player",
                    "Sam",
                ),
            )

        assertEquals(
            "OWNER Sam",
            plain.serialize(component),
        )

        assertFalse(
            descendants(component).any { child ->
                child.style().clickEvent() != null ||
                    child.style().hoverEvent() != null ||
                    child.style().insertion() != null
            },
        )
    }

    @Test
    fun `malformed styled placeholder falls back safely`() {
        val renderer =
            ChatFormatPlaceholderRenderer(
                setOf(
                    "papi_luckperms_prefix",
                ),
            )

        val resolved =
            renderer.resolve(
                "{papi_luckperms_prefix}",
                mapOf(
                    "papi_luckperms_prefix" to
                        "<bold><red>OWNER",
                ),
            )

        assertEquals(
            "OWNER",
            plain.serialize(
                deserialize(resolved),
            ),
        )
    }

    private fun deserialize(
        resolved: ResolvedChatFormat,
        vararg additionalResolvers: TagResolver,
    ): Component {
        val resolvers =
            resolved.resolvers +
                additionalResolvers

        return miniMessage.deserialize(
            resolved.template,
            *resolvers.toTypedArray(),
        )
    }

    private fun descendants(
        component: Component,
    ): Sequence<Component> =
        sequence {
            yield(component)

            component.children()
                .forEach { child ->
                    yieldAll(
                        descendants(child),
                    )
                }
        }
}

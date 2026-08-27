package de.samthedev.veloutils.common

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InputPolicyTest {
    @Test fun `untrusted minimessage stays literal`() {
        val input = "<click:run_command:'/op me'><red>Hello</red>"
        val component = MiniMessage.miniMessage().deserialize(escapeMiniMessage(input))
        assertEquals(input, PlainTextComponentSerializer.plainText().serialize(component))
    }

    @Test fun `policies reject excessive input`() {
        assertFailsWith<IllegalArgumentException> { InputPolicy("small", 3).validate("four") }
    }
}

package com.simonpena.mako.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Reading device descriptions, and offering the Sonos rooms together as a group. */
@RunWith(RobolectricTestRunner::class) // for Bundle
class DiscoveryTest {
    // Trimmed from Estudio's real /xml/device_description.xml (docs/upnp-renderers.md).
    private val sonos = """
        <root><device><friendlyName>192.168.1.129 - Sonos One SL - RINCON_000E58A1B2C301400</friendlyName>
        <modelName>Sonos One SL</modelName><roomName>Estudio</roomName><UDN>uuid:RINCON_000E58A1B2C301400</UDN>
        <deviceList><device><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
        <UDN>uuid:RINCON_000E58A1B2C301400_MR</UDN><serviceList>
        <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/MediaRenderer/RenderingControl/Control</controlURL></service>
        <service><serviceType>urn:schemas-upnp-org:service:GroupRenderingControl:1</serviceType><controlURL>/MediaRenderer/GroupRenderingControl/Control</controlURL></service>
        <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/MediaRenderer/AVTransport/Control</controlURL></service>
        </serviceList></device></deviceList></device></root>
    """.trimIndent()

    private val tv = """
        <root><device><friendlyName>[TV] The Frame</friendlyName><modelName>UE55LS003</modelName>
        <UDN>uuid:0e4a1b2c-0000-1000-8000-a0d0b0c0d0e0</UDN><serviceList>
        <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/upnp/control/AVTransport1</controlURL></service>
        <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/upnp/control/RenderingControl1</controlURL></service>
        </serviceList></device></root>
    """.trimIndent()

    @Test fun `a Sonos description gives the room, its id and its controls`() {
        val r = parseDescription(sonos, "http://192.168.1.129:1400/xml/device_description.xml")!!
        assertEquals("Estudio", r.name)
        assertEquals("RINCON_000E58A1B2C301400", r.id) // the speaker, not the _MR sub-device
        assertEquals("http://192.168.1.129:1400/MediaRenderer/AVTransport/Control", r.avTransport)
        assertEquals("http://192.168.1.129:1400/MediaRenderer/RenderingControl/Control", r.renderingControl)
        assertEquals("http://192.168.1.129:1400/MediaRenderer/GroupRenderingControl/Control", r.groupRenderingControl)
        assertTrue(r.isSpeaker)
    }

    @Test fun `the TV has no group volume`() {
        val r = parseDescription(tv, "http://192.168.1.128:9197/dmr")!!
        assertEquals("The Frame", r.name)
        assertNull(r.groupRenderingControl)
    }

    private fun speaker(name: String, ip: String) = Renderer(
        name, "Sonos One SL", "http://$ip:1400/MediaRenderer/AVTransport/Control",
        "http://$ip:1400/MediaRenderer/RenderingControl/Control", id = "RINCON_$name",
        groupRenderingControl = "http://$ip:1400/MediaRenderer/GroupRenderingControl/Control",
    )

    @Test fun `two or more Sonos rooms are also offered together`() {
        val estudio = speaker("Estudio", "192.168.1.129")
        val cocina = speaker("Cocina", "192.168.1.151")
        val frame = parseDescription(tv, "http://192.168.1.128:9197/dmr")!!
        val all = withSonosGroup(listOf(estudio, frame, cocina))
        val group = all.single { it.members.isNotEmpty() }
        assertEquals("Cocina + Estudio", group.name)
        assertEquals("Sonos group", group.model)
        assertEquals(cocina.avTransport, group.avTransport) // the first room leads
        assertEquals(cocina.groupRenderingControl, group.groupRenderingControl)
        assertEquals(listOf("Estudio"), group.members.map { it.name })
        assertEquals(4, all.size)
    }

    @Test fun `one Sonos room is no group`() {
        assertEquals(1, withSonosGroup(listOf(speaker("Estudio", "192.168.1.129"))).size)
    }

    @Test fun `a group survives the trip through a Bundle`() {
        val group = withSonosGroup(listOf(speaker("Estudio", "1.1.1.1"), speaker("Cocina", "2.2.2.2"))).single { it.members.isNotEmpty() }
        // Renderer.toBundle()/fromBundle carry the group to the playback service.
        assertEquals(group, Renderer.fromBundle(group.toBundle()))
    }
}

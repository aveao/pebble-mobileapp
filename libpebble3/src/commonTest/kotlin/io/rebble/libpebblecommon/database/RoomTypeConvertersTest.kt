package io.rebble.libpebblecommon.database

import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomTypeConvertersTest {
    @Test
    fun capabilitiesFromANewerBuildAreDropped() {
        assertEquals(
            setOf(ProtocolCapsFlag.SupportsAppRunStateProtocol),
            RoomTypeConverters().StringToCapabilitySet("""["SupportsAppRunStateProtocol","SupportsSomethingNew"]"""),
        )
    }
}

package com.zakiev.spatialdashboard

import com.zakiev.spatialdashboard.util.BarsGlb
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsGlbTest {

    @Test
    fun `produces a well formed glb container`() {
        val bytes = BarsGlb.unitCube(0.37f, 0.92f, 0.83f, 0.45f)

        // magic "glTF", version 2, declared length matches
        assertEquals(0x46546C67, readInt(bytes, 0))
        assertEquals(2, readInt(bytes, 4))
        assertEquals(bytes.size, readInt(bytes, 8))
        // first chunk is JSON
        assertEquals(0x4E4F534A, readInt(bytes, 16))
        assertTrue(bytes.size % 4 == 0)

        // dump for manual inspection
        File("build/bars-test.glb").writeBytes(BarsGlb.unitCube(0.37f, 0.92f, 0.83f, 0.45f))
    }

    private fun readInt(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)
}

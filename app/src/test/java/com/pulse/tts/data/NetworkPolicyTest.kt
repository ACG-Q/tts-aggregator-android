package com.pulse.tts.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyTest {

    @Test
    fun `internet and validated are both required`() {
        assertTrue(isUsableNetwork(hasInternet = true, validated = true))
        assertFalse(isUsableNetwork(hasInternet = true, validated = false))
        assertFalse(isUsableNetwork(hasInternet = false, validated = true))
        assertFalse(isUsableNetwork(hasInternet = false, validated = false))
    }
}

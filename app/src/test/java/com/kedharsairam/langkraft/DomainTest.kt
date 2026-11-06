package com.kedharsairam.langkraft

import com.kedharsairam.langkraft.domain.isValidYouSay
import com.kedharsairam.langkraft.domain.nextIndex
import com.kedharsairam.langkraft.domain.prevIndex
import com.kedharsairam.langkraft.domain.progressLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    @Test fun nextWrapsAround() {
        assertEquals(1, nextIndex(0, 5))
        assertEquals(0, nextIndex(4, 5))
        assertEquals(0, nextIndex(0, 0))
    }
    @Test fun prevWrapsAround() {
        assertEquals(4, prevIndex(0, 5))
        assertEquals(0, prevIndex(1, 5))
        assertEquals(0, prevIndex(0, 0))
    }
    @Test fun progressLabelIsPositionOnly() {
        assertEquals("18/40", progressLabel(18, 40))
    }
    @Test fun youSayMustHaveCapsStress() {
        assertTrue(isValidYouSay("OH-lah"))
        assertTrue(isValidYouSay("BWEH-nos DEE-ahs"))
        assertFalse(isValidYouSay("oh-lah"))
        assertFalse(isValidYouSay(""))
    }
}

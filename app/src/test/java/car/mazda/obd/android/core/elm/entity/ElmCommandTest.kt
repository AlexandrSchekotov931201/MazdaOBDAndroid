package car.mazda.obd.android.core.elm.entity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmCommandTest {
    @Test
    fun defaultInitSequenceTargetsEngineEcu() {
        val commands = ElmCommand.defaultInitSequence.map(ElmCommand::value)

        assertTrue(commands.contains("ATSH7E0"))
        assertFalse(commands.contains("ATSH7DF"))
    }
}

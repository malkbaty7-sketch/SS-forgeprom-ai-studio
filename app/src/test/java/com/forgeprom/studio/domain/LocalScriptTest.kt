package com.forgeprom.studio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalScriptTest {
    @Test fun expressionsUsePrecedenceVariablesAndGrouping() {
        assertEquals(listOf("14", "20", "-3.5", "4"), LocalScript.run("""
            # One statement per line
            let x = 2 + 3 * 4
            print x
            print (2 + 3) * 4
            print -x / 4 # Negation and division
            print 8 / 2 / 1
        """.trimIndent()))
        assertEquals(emptyList<String>(), LocalScript.run("let x = 1"))
    }

    @Test fun rejectsUnsupportedSyntaxAndReportsTheLine() {
        for (statement in listOf("print x", "print 1/0", "print 1; print 2", "print read(1)",
            "print 1..2", "print (1", "exec ls", "print 1e2")) {
            val error = assertThrows(IllegalArgumentException::class.java) { LocalScript.run("\n$statement") }
            assertTrue(error.message.orEmpty().startsWith("السطر 2:"))
        }
    }

    @Test fun refusesUnboundedSizeDepthAndNumericOverflow() {
        for (source in listOf(" ".repeat(4097), "\n".repeat(64),
            "print " + "(".repeat(100) + "1" + ")".repeat(100),
            "print " + "-".repeat(100) + "1", "print " + "9".repeat(320))) {
            assertThrows(IllegalArgumentException::class.java) { LocalScript.run(source) }
        }
    }

    @Test fun valuesDoNotPersistBetweenRuns() {
        LocalScript.run("let x = 1")
        assertThrows(IllegalArgumentException::class.java) { LocalScript.run("print x") }
    }
}

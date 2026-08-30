package org.vibetgram.core.tdlib

import org.vibetgram.core.tdlib.generated.GeneratedTdlibSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratedTdlibSchemaTest {
    @Test
    fun `generated inventory is tied to the locked TDLib schema`() {
        assertEquals("022d60202e446ad1287b9fb68e687c8a0760788b", GeneratedTdlibSchema.TD_LIB_COMMIT)
        assertEquals("fac745482ca22a4ff906443a70d304868c1649dba5b2957cf52be032cb8cad08", GeneratedTdlibSchema.SCHEMA_HASH)
        assertEquals(2165, GeneratedTdlibSchema.constructors.size)
        assertEquals(1010, GeneratedTdlibSchema.functions.size)
        assertTrue("updateNewMessage" in GeneratedTdlibSchema.constructors)
        assertTrue("getChatHistory" in GeneratedTdlibSchema.functions)
    }
}

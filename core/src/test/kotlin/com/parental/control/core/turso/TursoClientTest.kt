package com.parental.control.core.turso

import org.junit.Assert.*
import org.junit.Test

class TursoClientTest {

    @Test
    fun `test turso execute select returns expected column and value`() {
        val client = TursoClient()
        val result = client.execute("SELECT 42 as answer, 'aegis' as app;")
        assertTrue("El query debe ser exitoso: ${result.error}", result.isSuccess)
        assertEquals(1, result.rows.size)
        val row = result.rows[0]
        assertEquals(42L, row["answer"])
        assertEquals("aegis", row["app"])
    }

    @Test
    fun `test turso query helper parses rows correctly`() {
        val client = TursoClient()
        val rows = client.query("SELECT name FROM sqlite_master WHERE type = ?;", listOf("table"))
        assertTrue("Debe encontrar las tablas creadas", rows.isNotEmpty())
        val tableNames = rows.mapNotNull { it["name"]?.toString() }
        assertTrue("Debe contener tabla devices", tableNames.contains("devices"))
        assertTrue("Debe contener tabla commands", tableNames.contains("commands"))
        assertTrue("Debe contener tabla parental_settings", tableNames.contains("parental_settings"))
    }
}

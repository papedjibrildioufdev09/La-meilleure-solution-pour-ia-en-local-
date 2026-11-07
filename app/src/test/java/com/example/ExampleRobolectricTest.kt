package com.example

import com.example.model.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleRobolectricTest {

  @Test
  fun testModelCatalogPresets() {
    val presets = ModelCatalog.PRESETS
    assertNotNull(presets)
    assertEquals(4, presets.size)
    assertEquals("Qwen 3.5 0.8B (Recommandé)", presets[0].name)
  }
}

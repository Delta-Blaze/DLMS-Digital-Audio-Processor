package com.dlms.audio

import com.dlms.audio.effects.EffectRack
import com.dlms.audio.presets.PresetCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetCatalogTest {
    @Test fun hasAtLeast80BuiltIns(){ assertEquals(80, PresetCatalog.allNames.size) }
    @Test fun presetsContainRackState(){ val rack=EffectRack();val p=PresetCatalog.builtIn("Tilawah Hall",rack);assertTrue(p.parameters.containsKey("reverb"));assertTrue(p.parameters.containsKey("compressor")) }
}

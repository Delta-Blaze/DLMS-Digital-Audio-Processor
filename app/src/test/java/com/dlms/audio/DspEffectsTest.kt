package com.dlms.audio

import com.dlms.audio.effects.EffectRack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DspEffectsTest {
    @Test fun rackProcessesWithoutNan(){ val rack=EffectRack();val b=FloatArray(2048){kotlin.math.sin(it*.01f)};rack.reset(48000,2);rack.process(b,48000,2);assertFalse(b.any{it.isNaN()||it.isInfinite()}) }
    @Test fun effectToggleChangesActiveList(){val rack=EffectRack();assertTrue(rack.activeNames().contains("phaser"));rack.setEnabled("phaser",false);assertFalse(rack.activeNames().contains("phaser"))}
}

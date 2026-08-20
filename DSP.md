# DSP

Implemented effect stages: input gain, gate, EQ, compressor, de-esser, saturation, exciter, distortion, chorus, flanger, phaser, tremolo, delay, reverb and limiter.

Effects operate on normalized float PCM and are written to PCM16 AudioTrack output. Stateful effects reset their internal buffers when the engine is recreated or the source sample rate/channel layout changes.

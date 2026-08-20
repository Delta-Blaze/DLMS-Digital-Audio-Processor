# Audio Engine

The engine owns recorder, decoder, codec, output track and DSP rack. `start()` first releases the previous engine, establishes source parameters, initializes DSP state, then starts the output path. `stop()` cancels the worker and releases all Android audio resources.

File decoding is platform based. Supported formats depend on device codec support; the source picker accepts `audio/*` and the decoder selects the first audio track.

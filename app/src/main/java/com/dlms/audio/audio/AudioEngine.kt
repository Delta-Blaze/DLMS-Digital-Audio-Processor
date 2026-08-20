package com.dlms.audio.audio

import android.content.Context
import android.media.*
import android.net.Uri
import com.dlms.audio.effects.EffectRack
import kotlinx.coroutines.*
import java.io.IOException
import kotlin.math.abs
import kotlin.math.sqrt

sealed interface AudioSource {
    data class File(val uri: Uri, val displayName: String): AudioSource
    data object Microphone: AudioSource
}

class AudioEngine(private val context: Context, private val onMeter: (Float,Float,Float,Float)->Unit, private val onStatus:(String)->Unit, private val onError:(String)->Unit) {
    private val rack = EffectRack()
    private var job: Job? = null
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var sampleRate = 48_000
    private var channels = 2
    private var bufferSize = 2048
    private var master = .85f
    private var source: AudioSource? = null
    @Volatile private var paused = false
    @Volatile private var stopRequested = false
    @Volatile private var underruns = 0L

    fun setMaster(value:Float){ master=value.coerceIn(0f,1f) }
    fun effects(): EffectRack = rack
    fun underrunCount()=underruns

    suspend fun start(source:AudioSource){
        stop()
        this.source=source
        stopRequested=false
        paused=false
        try {
            onStatus("Initializing")
            when(source){
                is AudioSource.Microphone -> startMicrophone()
                is AudioSource.File -> startFile(source.uri)
            }
            onStatus("Playing")
        } catch(t:Throwable){ onError("Audio engine failed: ${t.message ?: t.javaClass.simpleName}"); releaseInternal() }
    }

    fun pause(){ paused=true; onStatus("Paused") }
    fun resume(){ if(job?.isActive==true){paused=false;onStatus("Playing")} }

    suspend fun stop(){ stopRequested=true; job?.cancelAndJoin(); job=null; releaseInternal(); onStatus("Stopped") }

    private fun startMicrophone(){
        val min=AudioRecord.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT)
        require(min>0){"Microphone buffer unavailable"}; bufferSize=maxOf(min,2048)
        recorder=AudioRecord(MediaRecorder.AudioSource.DEFAULT,sampleRate,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT,bufferSize*2)
        require(recorder?.state==AudioRecord.STATE_INITIALIZED){"Microphone initialization failed"}
        buildOutput()
        recorder!!.startRecording()
        val record=recorder!!
        job=CoroutineScope(Dispatchers.Default+SupervisorJob()).launch { val input=ShortArray(bufferSize); val floats=FloatArray(bufferSize); while(isActive&&!stopRequested){ if(paused){delay(10);continue};val n=record.read(input,0,input.size);if(n<=0)continue;for(i in 0 until n)floats[i]=input[i]/32768f;processAndWrite(floats,n)} }
    }

    private fun startFile(uri:Uri){
        extractor=MediaExtractor().also{it.setDataSource(context,uri,null)}
        val ex=extractor!!
        var trackIndex=-1
        for(i in 0 until ex.trackCount){val f=ex.getTrackFormat(i);val mime=f.getString(MediaFormat.KEY_MIME);if(mime?.startsWith("audio/")==true){trackIndex=i;break}}
        require(trackIndex>=0){"No audio track found"}
        val fmt=ex.getTrackFormat(trackIndex);sampleRate=if(fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 48_000;channels=if(fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2;channels=channels.coerceIn(1,2);bufferSize=2048;ex.selectTrack(trackIndex)
        val mime=fmt.getString(MediaFormat.KEY_MIME) ?: throw IOException("Missing audio mime")
        codec=MediaCodec.createDecoderByType(mime).also{it.configure(fmt,null,null,0);it.start()}
        rack.reset(sampleRate,channels);buildOutput()
        val c=codec!!;val info=MediaCodec.BufferInfo();var inputDone=false;var outputDone=false
        job=CoroutineScope(Dispatchers.Default+SupervisorJob()).launch {
            val tmp=ShortArray(bufferSize*4)
            while(isActive&&!stopRequested&&!outputDone){
                if(paused){delay(10);continue}
                if(!inputDone){val ix=c.dequeueInputBuffer(10_000);if(ix>=0){val ib=c.getInputBuffer(ix)!!;val sz=ex.readSampleData(ib,0);if(sz<0){c.queueInputBuffer(ix,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}else{c.queueInputBuffer(ix,0,sz,ex.sampleTime,0);ex.advance()}}}
                when(val ox=c.dequeueOutputBuffer(info,10_000)){MediaCodec.INFO_TRY_AGAIN_LATER->Unit;MediaCodec.INFO_OUTPUT_FORMAT_CHANGED->{val o=c.outputFormat;channels=(if(o.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) o.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else channels).coerceIn(1,2);sampleRate=if(o.containsKey(MediaFormat.KEY_SAMPLE_RATE)) o.getInteger(MediaFormat.KEY_SAMPLE_RATE) else sampleRate};else->if(ox>=0){val ob=c.getOutputBuffer(ox);if(ob!=null&&info.size>0){ob.position(info.offset);ob.limit(info.offset+info.size);val bytes=ByteArray(info.size);ob.get(bytes);val count=bytes.size/2;val floats=FloatArray(count);for(i in 0 until count){val lo=bytes[i*2].toInt() and 255;val hi=bytes[i*2+1].toInt();val v=(hi shl 8) or lo;floats[i]=v/32768f};processAndWrite(floats,count)};c.releaseOutputBuffer(ox,false);if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)outputDone=true}}
            }
        }
    }

    private fun buildOutput(){
        val mask=if(channels==1)AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val min=AudioTrack.getMinBufferSize(sampleRate,mask,AudioFormat.ENCODING_PCM_16BIT);require(min>0){"Output buffer unavailable"};bufferSize=maxOf(bufferSize,min)
        track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(mask).build()).setBufferSizeInBytes(bufferSize*2).setTransferMode(AudioTrack.MODE_STREAM).build().also{require(it.state==AudioTrack.STATE_INITIALIZED){"Output initialization failed"};it.play()}
        rack.reset(sampleRate,channels)
    }

    private fun processAndWrite(buffer:FloatArray,count:Int){
        if(count<=0)return;rack.process(buffer,sampleRate,channels);var peak=0f;var sum=0.0;for(i in 0 until count){buffer[i]=(buffer[i]*master).coerceIn(-1f,1f);val a=abs(buffer[i]);peak=maxOf(peak,a);sum+=a*a};val rms=sqrt((sum/count).toFloat());onMeter(peak,rms,peak,sampleRate.toFloat());val out=ShortArray(count);for(i in 0 until count)out[i]=(buffer[i]*32767f).toInt().toShort();val written=track?.write(out,0,out.size,AudioTrack.WRITE_BLOCKING)?:0;if(written<0)underruns++}

    private fun releaseInternal(){
        try{recorder?.stop()}catch(_:Throwable){};try{recorder?.release()}catch(_:Throwable){};recorder=null
        try{track?.stop()}catch(_:Throwable){};try{track?.release()}catch(_:Throwable){};track=null
        try{codec?.stop()}catch(_:Throwable){};try{codec?.release()}catch(_:Throwable){};codec=null
        try{extractor?.release()}catch(_:Throwable){};extractor=null;source=null
    }
}

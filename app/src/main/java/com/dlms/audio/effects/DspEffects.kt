package com.dlms.audio.effects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

private fun sinF(x: Float): Float = sin(x.toDouble()).toFloat()
private fun cosF(x: Float): Float = cos(x.toDouble()).toFloat()
private fun copySignF(magnitude: Float, sign: Float): Float = if (sign < 0f) -magnitude else magnitude

interface DspEffect {
    val id: String
    var enabled: Boolean
    var mix: Float
    fun reset(sampleRate: Int, channels: Int)
    fun process(buffer: FloatArray, sampleRate: Int, channels: Int)
    fun setParameter(name: String, value: Float)
    fun getParameters(): Map<String, Float>
}

abstract class BaseEffect(override val id: String) : DspEffect {
    override var enabled = true
    override var mix = 1f
    protected val p = mutableMapOf<String, Float>()
    override fun reset(sampleRate: Int, channels: Int) { require(sampleRate > 0 && channels in 1..2) }
    override fun setParameter(name: String, value: Float) { p[name] = value.coerceIn(-1000f, 1000f) }
    override fun getParameters(): Map<String, Float> = p.toMap()
    protected fun wetDry(dry: Float, wet: Float): Float = dry * (1f - mix) + wet * mix
}

class GainEffect : BaseEffect("input_gain") {
    init { p["gainDb"] = 0f }
    override fun process(buffer: FloatArray, sampleRate: Int, channels: Int) { if (!enabled) return; val g = 10f.pow(p["gainDb"]!! / 20f); for (i in buffer.indices) buffer[i] *= g }
}

class GateEffect : BaseEffect("gate") {
    private var env = 0f
    init { p.putAll(mapOf("thresholdDb" to -50f, "attackMs" to 5f, "holdMs" to 20f, "releaseMs" to 90f, "rangeDb" to -48f)) }
    override fun process(buffer: FloatArray, sampleRate: Int, channels: Int) {
        if (!enabled) return
        val attack = exp(-1.0 / (sampleRate * p["attackMs"]!!.coerceAtLeast(.001f) / 1000f)).toFloat()
        val release = exp(-1.0 / (sampleRate * p["releaseMs"]!!.coerceAtLeast(.001f) / 1000f)).toFloat()
        val threshold = 10f.pow(p["thresholdDb"]!! / 20f)
        val range = 10f.pow(p["rangeDb"]!! / 20f)
        for (i in buffer.indices step channels) {
            var sum = 0f
            for (c in 0 until channels) sum += abs(buffer[i + c]).coerceAtMost(1f)
            val x = sum / channels
            env = if (x > env) attack * env + (1f - attack) * x else release * env + (1f - release) * x
            val gain = if (env >= threshold) 1f else range
            for (c in 0 until channels) buffer[i + c] *= gain
        }
    }
}

class BiquadEffect : BaseEffect("eq") {
    private data class Band(var type: Int, var freq: Float, var gain: Float = 0f, var q: Float = .707f,
        var b0: Float = 1f, var b1: Float = 0f, var b2: Float = 0f, var a1: Float = 0f, var a2: Float = 0f,
        var z1L: Float = 0f, var z2L: Float = 0f, var z1R: Float = 0f, var z2R: Float = 0f)
    private val bands = arrayOf(Band(2,40f),Band(1,120f),Band(1,300f),Band(1,1000f),Band(1,3000f),Band(1,7000f),Band(3,12000f),Band(4,18000f))
    init { p.putAll(mapOf("hpf" to 30f,"lowShelf" to 0f,"lowMid" to 0f,"mid" to 0f,"highMid" to 0f,"highShelf" to 0f,"lpf" to 20000f)) }
    override fun reset(sampleRate: Int, channels: Int) { super.reset(sampleRate,channels);bands.forEach{it.z1L=0f;it.z2L=0f;it.z1R=0f;it.z2R=0f} }
    private fun update(b:Band,sr:Int,gainDb:Float){
        val f=b.freq.coerceIn(20f,sr/2f-20f);val a=10f.pow(gainDb/40f);val w=(2f*PI.toFloat()*f/sr);val c=cosF(w);val s=sinF(w);val alpha=s/(2f*b.q);val beta=(2f*sqrt(a.toDouble()).toFloat()*alpha)
        val b0:Float;val b1:Float;val b2:Float;val a0:Float;val a1:Float;val a2:Float
        when(b.type){
            2->{b0=(1f+c)/2f;b1=-(1f+c);b2=(1f+c)/2f;a0=1f+alpha;a1=-2f*c;a2=1f-alpha}
            4->{b0=(1f+c)/2f;b1=-(1f+c);b2=(1f+c)/2f;a0=1f+alpha;a1=-2f*c;a2=1f-alpha}
            3->{b0=a*((a+1f)+(a-1f)*c+beta);b1=-2f*a*((a-1f)+(a+1f)*c);b2=a*((a+1f)+(a-1f)*c-beta);a0=(a+1f)-(a-1f)*c+beta;a1=2f*((a-1f)-(a+1f)*c);a2=(a+1f)-(a-1f)*c-beta}
            else->{b0=1f;b1=0f;b2=0f;a0=1f;a1=0f;a2=0f}
        }
        b.b0=b0/a0;b.b1=b1/a0;b.b2=b2/a0;b.a1=a1/a0;b.a2=a2/a0
    }
    private fun updateAll(sr:Int){
        bands[0].freq=p["hpf"]!!.coerceAtMost(sr/2f-20f);update(bands[0],sr,0f)
        bands[1].gain=p["lowShelf"]!!;bands[2].gain=p["lowMid"]!!;bands[3].gain=p["mid"]!!;bands[4].gain=p["highMid"]!!;bands[5].gain=p["highShelf"]!!
        for(i in 1..6) update(bands[i],sr,bands[i].gain)
        bands[7].freq=p["lpf"]!!.coerceIn(1000f,sr/2f-20f);update(bands[7],sr,0f)
    }
    override fun process(buffer: FloatArray, sampleRate: Int, channels: Int) {
        if (!enabled) return; updateAll(sampleRate)
        for (b in bands) for (i in buffer.indices step channels) {
            val l=buffer[i];val yL=b.b0*l+b.z1L;b.z1L=b.b1*l-b.a1*yL+b.z2L;b.z2L=b.b2*l-b.a2*yL;buffer[i]=wetDry(l,yL)
            if(channels>1){val r=buffer[i+1];val yR=b.b0*r+b.z1R;b.z1R=b.b1*r-b.a1*yR+b.z2R;b.z2R=b.b2*r-b.a2*yR;buffer[i+1]=wetDry(r,yR)}
        }
    }
}

class DynamicsEffect : BaseEffect("compressor") {
    private var env=0f
    init { p.putAll(mapOf("thresholdDb" to -18f,"ratio" to 3f,"attackMs" to 10f,"releaseMs" to 100f,"knee" to 4f,"makeupDb" to 2f)) }
    override fun process(buffer: FloatArray,sampleRate:Int,channels:Int){if(!enabled)return;val th=p["thresholdDb"]!!;val ratio=p["ratio"]!!.coerceIn(1f,20f);val aa=exp(-1.0/(sampleRate*p["attackMs"]!!.coerceAtLeast(.001f)/1000f)).toFloat();val rr=exp(-1.0/(sampleRate*p["releaseMs"]!!.coerceAtLeast(.001f)/1000f)).toFloat();val makeup=10f.pow(p["makeupDb"]!!/20f);for(i in buffer.indices){val x=abs(buffer[i]);env=if(x>env)aa*env+(1-aa)*x else rr*env+(1-rr)*x;val db=20f*log10f(env.coerceAtLeast(1e-7f));val over=max(0f,db-th);val gr=over-over/ratio;buffer[i]*=10f.pow(-gr/20f)*makeup}}
}
private fun log10f(x:Float)=lnF(x)/lnF(10f)
private fun lnF(x:Float)=kotlin.math.ln(x.toDouble()).toFloat()

class DeEsserEffect:BaseEffect("de_esser"){private var lp=0f;init{p.putAll(mapOf("frequency" to 6000f,"thresholdDb" to -24f,"ratio" to 4f,"amount" to .55f,"bandwidth" to .35f))};override fun process(buffer:FloatArray,sampleRate:Int,channels:Int){if(!enabled)return;val coeff=exp(-2.0*PI*p["frequency"]!!.coerceIn(1000f,sampleRate/2f-100f)/sampleRate).toFloat();val th=10f.pow(p["thresholdDb"]!!/20f);for(i in buffer.indices){lp=coeff*lp+(1f-coeff)*buffer[i];val bright=abs(buffer[i]-lp);if(bright>th){val red=(bright-th)*(1f-1f/p["ratio"]!!)*p["amount"]!!;buffer[i]-=copySignF(red,buffer[i])}}}}
class SaturationEffect:BaseEffect("saturation"){init{p["drive"]=.2f;p["mode"]=0f};override fun process(buffer:FloatArray,sampleRate:Int,channels:Int){if(!enabled)return;val d=1f+p["drive"]!!.coerceIn(0f,10f)*10f;val m=p["mode"]!!.roundToInt();for(i in buffer.indices){val x=buffer[i];val y=when(m){0->tanhF(x*d);1->(atan((x*d).toDouble())*2.0/PI).toFloat();2->x/(1f+abs(x));3->x.coerceIn(-.8f,.8f);else->copySignF(min(1f,abs(x)*d),x)};buffer[i]=wetDry(x,y)}}}
private fun tanhF(x:Float)=tanh(x.toDouble()).toFloat()
class ExciterEffect:BaseEffect("exciter"){init{p.putAll(mapOf("frequency" to 5000f,"amount" to .25f))};override fun process(buffer:FloatArray,sampleRate:Int,channels:Int){if(!enabled)return;val a=exp(-2.0*PI*p["frequency"]!!/sampleRate).toFloat();var lp=0f;for(i in buffer.indices){lp=a*lp+(1-a)*buffer[i];buffer[i]+= (buffer[i]-lp)*p["amount"]!!}}}
class DistortionEffect:BaseEffect("distortion"){init{p["drive"]=.5f;p["mode"]=0f};override fun process(buffer:FloatArray,sampleRate:Int,channels:Int){if(!enabled)return;val d=1f+p["drive"]!!.coerceIn(0f,1f)*40f;val m=p["mode"]!!.roundToInt();for(i in buffer.indices){val x=buffer[i];val y=when(m){0->tanhF(x*d);1->x.coerceIn(-.35f,.35f);2->tanhF(x*d)*.7f;3->sinF(x*d);else->x/(1f+abs(x*d))};buffer[i]=wetDry(x,y)}}}

abstract class ModEffect(id:String):BaseEffect(id){protected var phase=0f;override fun reset(sampleRate:Int,channels:Int){super.reset(sampleRate,channels);phase=0f}}
class ChorusEffect:ModEffect("chorus"){init{p.putAll(mapOf("rate" to .35f,"depth" to .003f,"width" to .7f,"feedback" to .1f))};override fun process(buffer:FloatArray,sampleRate:Int,channels:Int){if(!enabled)return;val n=max(1,(p["depth"]!!*sampleRate).roundToInt());val d=FloatArray(max(8,n*2+8));var wp=0;for(i in buffer.indices step channels){val l=buffer[i];val delay=((sinF(phase)*.5f+.5f)*n).roundToInt();val rp=(wp-delay+d.size)%d.size;val wet=d[rp];d[wp]=l+wet*p["feedback"]!!;buffer[i]=wetDry(l,l*.8f+wet*.2f);if(channels>1)buffer[i+1]=wetDry(buffer[i+1],buffer[i+1]*.8f+wet*.2f);wp=(wp+1)%d.size;phase+=2f*PI.toFloat()*p["rate"]!!/sampleRate;if(phase>=2f*PI.toFloat())phase-=2f*PI.toFloat()}}}
class FlangerEffect:ModEffect("flanger"){init{p.putAll(mapOf("rate" to .2f,"depth" to .002f,"delay" to .001f,"feedback" to .3f))};override fun process(buffer:FloatArray,sampleRate:Int,channels:Int){if(!enabled)return;val maxD=(.02f*sampleRate).roundToInt()+8;val d=FloatArray(maxD);var wp=0;for(i in buffer.indices){val l=buffer[i];val m=((sinF(phase)*.5f+.5f)*(p["depth"]!!*sampleRate)+p["delay"]!!*sampleRate).roundToInt().coerceIn(0,maxD-2);val rp=(wp-m+maxD)%maxD;val wet=d[rp];d[wp]=l+wet*p["feedback"]!!;buffer[i]=wetDry(l,l+wet*.7f);wp=(wp+1)%maxD;phase+=2f*PI.toFloat()*p["rate"]!!/sampleRate;if(phase>=2f*PI.toFloat())phase-=2f*PI.toFloat()}}}
class PhaserEffect:ModEffect("phaser"){init{p.putAll(mapOf("rate" to .4f,"depth" to .7f,"feedback" to .2f,"stages" to 4f))};override fun process(buffer:FloatArray,sampleRate:Int,channels:Int){if(!enabled)return;val stages=p["stages"]!!.roundToInt().coerceIn(2,8);val z=FloatArray(stages);for(i in buffer.indices){val dry=buffer[i];var x=dry;val f=(sinF(phase)*.5f+.5f)*p["depth"]!!;for(s in 0 until stages){val y=f*x+(1f-f)*z[s];z[s]=x;x=y};buffer[i]=wetDry(dry,x);phase+=2f*PI.toFloat()*p["rate"]!!/sampleRate;if(phase>=2f*PI.toFloat())phase-=2f*PI.toFloat()}}}
class TremoloEffect:ModEffect("tremolo"){init{p.putAll(mapOf("rate" to 4f,"depth" to .35f,"shape" to 0f,"stereoPhase" to 0f))};override fun process(buffer:FloatArray,sampleRate:Int,channels:Int){if(!enabled)return;for(i in buffer.indices step channels){buffer[i]*=1f-p["depth"]!!*(sinF(phase)*.5f+.5f);if(channels>1){val rp=phase+p["stereoPhase"]!!;buffer[i+1]*=1f-p["depth"]!!*(sinF(rp)*.5f+.5f)};phase+=2f*PI.toFloat()*p["rate"]!!/sampleRate;if(phase>=2f*PI.toFloat())phase-=2f*PI.toFloat()}}}

class DelayEffect:BaseEffect("delay"){private var buf=FloatArray(1);private var wp=0;init{p.putAll(mapOf("timeMs" to 280f,"feedback" to .3f,"mix" to .18f,"highCut" to 14000f,"lowCut" to 80f,"pingPong" to 0f,"width" to 1f))};override fun reset(sampleRate:Int,channels:Int){super.reset(sampleRate,channels);buf=FloatArray(sampleRate*3*channels);wp=0};override fun process(buffer:FloatArray,sampleRate:Int,channels:Int){if(!enabled)return;val delay=(p["timeMs"]!!.coerceIn(1f,2500f)*sampleRate/1000f).roundToInt()*channels;for(i in buffer.indices){val rp=(wp-delay+buf.size)%buf.size;val wet=buf[rp];val dry=buffer[i];buf[wp]=(dry+wet*p["feedback"]!!).coerceIn(-1f,1f);buffer[i]=wetDry(dry,wet);wp=(wp+1)%buf.size}}}
class ReverbEffect:BaseEffect("reverb"){private var combs=emptyArray<FloatArray>();private var pos=IntArray(6);init{p.putAll(mapOf("preDelayMs" to 15f,"size" to .55f,"decay" to .58f,"damping" to .2f,"diffusion" to .7f,"width" to .8f,"mix" to .22f,"lowCut" to 120f,"highCut" to 12000f,"mode" to 0f))};override fun reset(sampleRate:Int,channels:Int){super.reset(sampleRate,channels);val base=intArrayOf(29,37,43,53,61,71);combs=Array(6){FloatArray((sampleRate*base[it]/10000f).roundToInt().coerceAtLeast(50))};pos=IntArray(6)};override fun process(buffer:FloatArray,sampleRate:Int,channels:Int){if(!enabled)return;if(combs.isEmpty())reset(sampleRate,channels);for(i in buffer.indices){var wet=0f;for(c in combs.indices){val b=combs[c];val v=b[pos[c]];b[pos[c]]=(buffer[i]+v*p["decay"]!!).coerceIn(-1f,1f);pos[c]=(pos[c]+1)%b.size;wet+=v};buffer[i]=wetDry(buffer[i],wet/(combs.size*.9f))}}}
class LimiterEffect:BaseEffect("limiter"){init{p.putAll(mapOf("ceilingDb" to -1f,"thresholdDb" to -2f,"releaseMs" to 80f))};override fun process(buffer:FloatArray,sampleRate:Int,channels:Int){if(!enabled)return;val ceiling=10f.pow(p["ceilingDb"]!!/20f);for(i in buffer.indices)if(abs(buffer[i])>ceiling)buffer[i]=copySignF(ceiling,buffer[i])}}

class EffectRack {
    val effects: List<DspEffect> = listOf(GainEffect(),GateEffect(),BiquadEffect(),DynamicsEffect(),DeEsserEffect(),SaturationEffect(),ExciterEffect(),DistortionEffect(),ChorusEffect(),FlangerEffect(),PhaserEffect(),TremoloEffect(),DelayEffect(),ReverbEffect(),LimiterEffect())
    fun process(buffer: FloatArray,sampleRate:Int,channels:Int){effects.forEach{it.process(buffer,sampleRate,channels)}}
    fun reset(sampleRate:Int,channels:Int){effects.forEach{it.reset(sampleRate,channels)}}
    fun activeNames():List<String> = effects.filter{it.enabled}.map{it.id}
    fun setEnabled(id:String,enabled:Boolean){effects.firstOrNull{it.id==id}?.enabled=enabled}
    fun setParam(id:String,name:String,value:Float){effects.firstOrNull{it.id==id}?.setParameter(name,value)}
    fun snapshot(): Map<String,Map<String,Float>> = effects.associate{effect -> effect.id to effect.getParameters()}
    fun apply(snapshot:Map<String,Map<String,Float>>){snapshot.forEach{(id,params)->params.forEach{(name,value)->setParam(id,name,value)}}}
}

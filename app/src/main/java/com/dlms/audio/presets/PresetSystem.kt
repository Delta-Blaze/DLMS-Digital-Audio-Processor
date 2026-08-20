package com.dlms.audio.presets

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dlms.audio.effects.EffectRack
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.presetDataStore by preferencesDataStore("dlms_presets")

data class Preset(val name:String,val version:Int=1,val parameters:Map<String,Map<String,Float>>)

object PresetCatalog {
    private val core = listOf(
        "Clean Vocal","Warm Vocal","Bright Vocal","Deep Vocal","Radio Vocal","Studio Vocal","Sad Vocal","Emotional Vocal","Powerful Vocal",
        "Tilawah Clean","Tilawah Warm","Tilawah Hall","Tilawah Mosque","Tilawah Deep","Tilawah Soft","Tilawah Wide","Tilawah Emotional","Tilawah Studio","Tilawah Natural",
        "Sad Vocal","Deep Sad","Broken Voice","Night Vocal","Rainy Vocal","Emotional Room","Dark Hall","Deep Reverb","Cinematic Sad","Lonely Vocal",
        "Podcast Clean","Podcast Warm","Podcast Radio","Speech Clear","Speech Deep","Studio Tight","Studio Air","Music Wide","Music Punch","Music Warm","Ambient Space","Ambient Deep","Cinematic Wide","Cinematic Dark","Lo-Fi Tape","Lo-Fi Soft","Live Vocal","Live Hall","Radio Mono","Radio Bright","Creative Chorus","Creative Phaser","Creative Delay","Creative Space"
    )
    val allNames:List<String> = core + (1..(80-core.size)).map{ "DLMS Preset ${it.toString().padStart(2,'0')}" }
    fun builtIn(name:String,rack:EffectRack):Preset { val f=name.lowercase(); val snap=rack.snapshot().mapValues{it.value.toMutableMap()}; fun put(effect:String,key:String,v:Float){snap[effect]?.put(key,v)}
        when{f.contains("tilawah")-> {put("compressor","thresholdDb",-20f);put("compressor","ratio",2f);put("reverb","mix",.2f);put("reverb","decay",.6f);put("delay","timeMs",180f);put("delay","feedback",.18f);put("eq","hpf",90f)}
            f.contains("sad")||f.contains("broken")||f.contains("lonely")||f.contains("night")||f.contains("rainy")||f.contains("emotional")-> {put("reverb","mix",.34f);put("reverb","decay",.76f);put("delay","mix",.22f);put("delay","timeMs",430f);put("saturation","drive",.08f);put("eq","hpf",75f)}
            f.contains("radio")-> {put("eq","hpf",120f);put("compressor","thresholdDb",-14f);put("compressor","ratio",5f);put("limiter","ceilingDb",-1f)}
            f.contains("podcast")||f.contains("speech")-> {put("eq","hpf",85f);put("compressor","thresholdDb",-22f);put("compressor","ratio",2.5f);put("de_esser","amount",.45f);put("limiter","ceilingDb",-1f)}
            f.contains("studio")-> {put("compressor","thresholdDb",-18f);put("compressor","ratio",2.5f);put("reverb","mix",.12f)}
            f.contains("ambient")||f.contains("cinematic")->{put("reverb","mix",.42f);put("reverb","decay",.82f);put("delay","mix",.25f);put("delay","timeMs",520f)}
            else->{put("compressor","thresholdDb",-18f);put("compressor","ratio",3f);put("reverb","mix",.15f)} }
        return Preset(name,1,snap)
    }
}

class PresetRepository(private val context:Context) {
    private val key=stringPreferencesKey("user_presets_json")
    suspend fun loadUserPresets():List<Preset>{val raw=context.presetDataStore.data.first()[key]?:"[]";val arr=org.json.JSONArray(raw);return (0 until arr.length()).map{fromJson(arr.getJSONObject(it))}}
    suspend fun saveUserPreset(preset:Preset){val list=loadUserPresets().filterNot{it.name==preset.name}+preset;context.presetDataStore.edit{it[key]=org.json.JSONArray(list.map(::toJson)).toString()}}
    suspend fun deleteUserPreset(name:String){context.presetDataStore.edit{prefs->val raw=prefs[key]?:"[]";val arr=org.json.JSONArray(raw);val out=org.json.JSONArray();for(i in 0 until arr.length()){val o=arr.getJSONObject(i);if(o.optString("name")!=name)out.put(o)};prefs[key]=out.toString()}}
    private fun toJson(p:Preset):JSONObject{val o=JSONObject().put("name",p.name).put("version",p.version);val root=JSONObject();p.parameters.forEach{(id,params)->val po=JSONObject();params.forEach{(k,v)->po.put(k,v.toDouble())};root.put(id,po)};o.put("parameters",root);return o}
    private fun fromJson(o:JSONObject):Preset{val root=o.getJSONObject("parameters");val map=mutableMapOf<String,Map<String,Float>>();root.keys().forEach{ id->val po=root.getJSONObject(id);val pm=mutableMapOf<String,Float>();po.keys().forEach{k->pm[k]=po.getDouble(k).toFloat()};map[id]=pm};return Preset(o.optString("name"),o.optInt("version",1),map)}
}

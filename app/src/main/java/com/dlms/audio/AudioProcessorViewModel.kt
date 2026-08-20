package com.dlms.audio

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dlms.audio.audio.AudioEngine
import com.dlms.audio.audio.AudioSource
import com.dlms.audio.presets.PresetCatalog
import com.dlms.audio.presets.PresetRepository
import com.dlms.audio.presets.Preset
import com.dlms.audio.state.AudioProcessorState
import com.dlms.audio.state.AudioStateStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AudioProcessorViewModel(app: Application):AndroidViewModel(app){
    private val store=AudioStateStore();val state:StateFlow<AudioProcessorState> = store.state
    private val repo=PresetRepository(app)
    private val engine=AudioEngine(app,{peak,rms,_,sr->store.update{it.copy(inputLevel=rms,outputLevel=rms,peak=peak,rms=rms,sampleRate=sr.toInt())}}, {status->store.update{it.copy(engineStatus=status,isPlaying=status=="Playing",isPaused=status=="Paused")}}, {err->store.update{it.copy(error=err,engineStatus="Error",isPlaying=false,isPaused=false,dspErrors=it.dspErrors+1)}})
    init{store.update{it.copy(activeNodes=engine.effects().activeNames())}}
    fun playFile(uri:Uri,name:String){store.update{it.copy(currentFile=name,currentPreset="Custom",error=null)};viewModelScope.launch{engine.start(AudioSource.File(uri,name));sync()}}
    fun startMic(){viewModelScope.launch{engine.start(AudioSource.Microphone);sync()}}
    fun pause(){engine.pause();sync()}
    fun resume(){engine.resume();sync()}
    fun stop(){viewModelScope.launch{engine.stop();sync()}}
    fun volume(v:Float){engine.setMaster(v);store.update{it.copy(masterVolume=v)}}
    fun toggleEffect(id:String){val e=engine.effects();val target=e.effects.firstOrNull{it.id==id}?:return;target.enabled=!target.enabled;store.update{it.copy(activeNodes=e.activeNames())}}
    fun param(id:String,name:String,v:Float){engine.effects().setParam(id,name,v);store.update{it.copy(currentPreset="Custom")}}
    fun applyPreset(name:String){val p=PresetCatalog.builtIn(name,engine.effects());engine.effects().apply(p.parameters);store.update{it.copy(currentPreset=name,activeNodes=engine.effects().activeNames())}}
    fun savePreset(name:String){viewModelScope.launch{repo.saveUserPreset(Preset(name,1,engine.effects().snapshot()));store.update{it.copy(currentPreset=name)}}}
    fun deletePreset(name:String){viewModelScope.launch{repo.deleteUserPreset(name)}}
    suspend fun userPresets():List<Preset> = repo.loadUserPresets()
    fun snapshot():Map<String,Map<String,Float>>=engine.effects().snapshot()
    fun clearError(){store.clearError()}
    fun sync(){store.update{it.copy(activeNodes=engine.effects().activeNames(),underruns=engine.underrunCount())}}
    override fun onCleared(){viewModelScope.launch{engine.stop()}}
}

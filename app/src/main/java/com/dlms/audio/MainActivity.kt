package com.dlms.audio

import android.Manifest
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlms.audio.ui.DlmsTheme

class MainActivity:ComponentActivity(){
    private val vm by viewModels<AudioProcessorViewModel>()
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{DlmsTheme{DlmsApp(vm)}}}
}

@Composable private fun DlmsApp(vm:AudioProcessorViewModel){
    val s by vm.state.collectAsStateWithLifecycle();var page by remember{mutableStateOf("Dashboard")};var showSave by remember{mutableStateOf(false)};var presetName by remember{mutableStateOf("")}
    val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{vm.playFile(it,"Audio File")}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
    LaunchedEffect(Unit){permission.launch(buildList{add(Manifest.permission.RECORD_AUDIO);if(Build.VERSION.SDK_INT>=33)add(Manifest.permission.READ_MEDIA_AUDIO)}.toTypedArray())}
    Scaffold(topBar={TopAppBar(title={Text("DLMS Digital Audio Processor")})},bottomBar={NavigationBar{listOf("Dashboard","Effects","Vocal","Presets","Routing","Diagnostics").forEach{p->NavigationBarItem(selected=page==p,onClick={page=p},icon={Text(p.take(1))},label={Text(p)})}}}){pad->
        if(s.error!=null){SnackbarHost(hostState=remember{SnackbarHostState()},modifier=Modifier.padding(pad))}
        when(page){
            "Dashboard"->Dashboard(vm,s,{filePicker.launch(arrayOf("audio/*"))})
            "Effects"->EffectsPage(vm,s)
            "Vocal"->VocalPage(vm)
            "Presets"->PresetPage(vm,showSave,{showSave=true},{showSave=false},presetName,{presetName=it})
            "Routing"->RoutingPage(s)
            else->DiagnosticsPage(s)
        }
        if(showSave)AlertDialog(onDismissRequest={showSave=false},confirmButton={Button(onClick={if(presetName.isNotBlank()){vm.savePreset(presetName);showSave=false}}){Text("Save")}},dismissButton={TextButton(onClick={showSave=false}){Text("Cancel")}},title={Text("Save preset")},text={OutlinedTextField(presetName,{presetName=it},label={Text("Name")})})
    }
}

@Composable private fun Dashboard(vm:AudioProcessorViewModel,s:com.dlms.audio.state.AudioProcessorState,onOpen:()->Unit){Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("${s.engineStatus} • ${s.sampleRate} Hz • ${s.channels} ch",style=MaterialTheme.typography.titleMedium);Meter("Input RMS",s.rms);Meter("Peak",s.peak);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={vm.startMic()}){Text("Mic")};Button(onClick=onOpen){Text("Open Audio")};Button(onClick={vm::pause},enabled=s.isPlaying){Text("Pause")};Button(onClick={vm::resume},enabled=s.isPaused){Text("Resume")};Button(onClick={vm::stop}){Text("Stop")}};Text("Preset: ${s.currentPreset}");Text("Master");Slider(value=s.masterVolume,onValueChange=vm::volume,valueRange=0f..1f);Text("Active nodes: ${s.activeNodes.joinToString()}",style=MaterialTheme.typography.bodySmall)}}

@Composable private fun Meter(label:String,value:Float){Column{Text("$label  ${(value*100).coerceIn(0f,100f).toInt()}%");LinearProgressIndicator(progress={value.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(10.dp))}}

@Composable private fun EffectsPage(vm:AudioProcessorViewModel,s:com.dlms.audio.state.AudioProcessorState){val ids=listOf("input_gain","gate","eq","compressor","de_esser","saturation","exciter","distortion","chorus","flanger","phaser","tremolo","delay","reverb","limiter");LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(ids){id->val enabled=s.activeNodes.contains(id);Card{Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(id,Modifier.weight(1f));Switch(checked=enabled,onCheckedChange={vm.toggleEffect(id)})}}}}}

@Composable private fun VocalPage(vm:AudioProcessorViewModel){val names=listOf("Clean Vocal","Warm Vocal","Bright Vocal","Deep Vocal","Radio Vocal","Studio Vocal","Sad Vocal","Emotional Vocal","Powerful Vocal");LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text("Vocal Processor",style=MaterialTheme.typography.headlineSmall);Text("Preset chain applies to the same DSP engine as file and microphone sources.")};items(names){name->Button(onClick={vm.applyPreset(name)},modifier=Modifier.fillMaxWidth()){Text(name)}}}}

@Composable private fun PresetPage(vm:AudioProcessorViewModel,showSave:Boolean,onSave:()->Unit,onCancel:()->Unit,current:String,onCurrent:(String)->Unit){val names=remember{com.dlms.audio.presets.PresetCatalog.allNames};LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){item{Row(verticalAlignment=Alignment.CenterVertically){Text("Presets (80)",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f));Button(onClick=onSave){Text("Save")}}};items(names){name->ElevatedCard{Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){Text(name,Modifier.weight(1f));TextButton(onClick={vm.applyPreset(name)}){Text("Apply")}}}}}}

@Composable private fun RoutingPage(s:com.dlms.audio.state.AudioProcessorState){Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Routing",style=MaterialTheme.typography.headlineSmall);listOf("Input","Dry/Wet processor chain","Master","AudioTrack output").forEachIndexed{i,n->Card{Text("${i+1}. $n",Modifier.padding(16.dp))}}Text(if(s.routingValid)"Routing: VALID" else "Routing: INVALID")}}

@Composable private fun DiagnosticsPage(s:com.dlms.audio.state.AudioProcessorState){Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Audio Diagnostics",style=MaterialTheme.typography.headlineSmall);listOf("Engine status" to s.engineStatus,"Sample rate" to s.sampleRate,"Buffer size" to s.bufferSize,"Channels" to s.channels,"Input level" to s.inputLevel,"Output level" to s.outputLevel,"Peak" to s.peak,"RMS" to s.rms,"Active nodes" to s.activeNodes.size,"Underruns" to s.underruns,"DSP errors" to s.dspErrors).forEach{(k,v)->Row{Text(k,Modifier.weight(1f));Text(v.toString())}};Text("Runtime device validation requires Android hardware or emulator audio I/O." )}}

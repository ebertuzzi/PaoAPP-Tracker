package com.paopr.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.*
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.ExperimentalMaterial3Api

private val ComponentActivity.dataStore by preferencesDataStore("paopr")
private val Blue = Color(0xFF4E78A0)
private val Pale = Color(0xFFF4F7FA)
private val Slate = Color(0xFF40515F)

data class Exercise(val id: String, val name: String, val icon: String)
data class Record(val id: String, val exerciseId: String, val kg: Double, val date: String, val comment: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PaoPRApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PaoPRApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exercises by remember { mutableStateOf(defaultExercises()) }
    var records by remember { mutableStateOf(listOf<Record>()) }
    var unit by remember { mutableStateOf("kg") }
    var screen by remember { mutableStateOf("dashboard") }
    var selected by remember { mutableStateOf<Exercise?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = (context as MainActivity).dataStore.data.first()
        unit = prefs[stringPreferencesKey("unit")] ?: "kg"
        prefs[stringPreferencesKey("exercises")]?.let { exercises = decodeExercises(it) }
        prefs[stringPreferencesKey("records")]?.let { records = decodeRecords(it) }
    }
    fun save() {
        scope.launch {
            val ctx = context as MainActivity
            ctx.dataStore.edit {
                it[stringPreferencesKey("unit")] = unit
                it[stringPreferencesKey("exercises")] = encodeExercises(exercises)
                it[stringPreferencesKey("records")] = encodeRecords(records)
            }
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary=Blue, background=Pale, surface=Color.White)) {
        Scaffold(
            containerColor = Pale,
            topBar = {
                TopAppBar(
                    title = { Text("PaoPR Tracker", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor=Pale)
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
    selected = screen == "dashboard",
    onClick = { screen = "dashboard" },
    icon = { Icon(Icons.Default.Home, contentDescription = null) },
    label = { Text("Inicio") }
)

NavigationBarItem(
    selected = screen == "exercises",
    onClick = { screen = "exercises" },
    icon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) },
    label = { Text("Ejercicios") }
)

NavigationBarItem(
    selected = screen == "settings",
    onClick = { screen = "settings" },
    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
    label = { Text("Ajustes") }
)
                }
            },
            floatingActionButton = {
                if (screen=="dashboard" || screen=="exercises")
                    FloatingActionButton(onClick={showAdd=true}, containerColor=Blue) { Icon(Icons.Default.Add,null) }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when(screen) {
                    "dashboard" -> Dashboard(exercises, records, unit, { e -> selected=e; screen="detail" }, {showAdd=true})
                    "exercises" -> ExerciseList(exercises, {e->selected=e;screen="detail"}, {showAdd=true})
                    "detail" -> selected?.let { Detail(it, records.filter { r->r.exerciseId==it.id }, unit, { records=records.filterNot { r->r.id==it.id }; save() }, {showAdd=true}) }
                    "settings" -> Settings(unit) { unit=it; save() }
                }
                if(showAdd) AddRecordDialog(exercises, unit, { ex, value, date, comment ->
                    records = records + Record(UUID.randomUUID().toString(), ex.id, if(unit=="kg") value else value/2.20462262, date, comment)
                    showAdd=false; save()
                }, {showAdd=false})
            }
        }
    }
}

@Composable fun Dashboard(ex: List<Exercise>, rec: List<Record>, unit:String, open:(Exercise)->Unit, add:()->Unit) {
    val latest = rec.maxByOrNull { it.date }
    Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Tu rendimiento", fontSize=28.sp, fontWeight=FontWeight.Bold, color=Slate)
        Card(shape=RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("PR registrados", color=Slate)
                Text("${rec.size}", fontSize=34.sp, fontWeight=FontWeight.Bold, color=Blue)
                if(latest!=null) {
                    val e=ex.firstOrNull{it.id==latest.exerciseId}
                    Text("Último: ${e?.name ?: ""} · ${fmt(latest.kg,unit)} $unit", color=Slate)
                }
            }
        }
        Button(onClick=add, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Add,null); Spacer(Modifier.width(8.dp)); Text("Registrar PR")
        }
        Text("Mis ejercicios", fontWeight=FontWeight.Bold, fontSize=20.sp, color=Slate)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(ex) { e ->
                val best=rec.filter{it.exerciseId==e.id}.maxOfOrNull{it.kg}
                Card(Modifier.fillMaxWidth().clickable{open(e)}, shape=RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
                        Text(e.icon, fontSize=28.sp); Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)){ Text(e.name,fontWeight=FontWeight.SemiBold); Text(if(best==null)"Sin registros" else "PR ${fmt(best,unit)} $unit",color=Slate,fontSize=13.sp)}
                        Icon(Icons.Default.ChevronRight,null)
                    }
                }
            }
        }
    }
}

@Composable fun ExerciseList(ex:List<Exercise>, open:(Exercise)->Unit, add:()->Unit) {
    Column(Modifier.padding(20.dp)) {
        Text("Ejercicios",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Slate)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) { items(ex){e->
            Card(Modifier.fillMaxWidth().clickable{open(e)},shape=RoundedCornerShape(16.dp)){
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(e.icon,fontSize=28.sp);Spacer(Modifier.width(14.dp));Text(e.name,fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f));Icon(Icons.Default.ChevronRight,null)
                }
            }
        }}
    }
}

@Composable fun Detail(e:Exercise, rec:List<Record>, unit:String, delete:(Record)->Unit, add:()->Unit) {
    Column(Modifier.padding(20.dp)) {
        Text("${e.icon}  ${e.name}",fontSize=27.sp,fontWeight=FontWeight.Bold,color=Slate)
        val best=rec.maxOfOrNull{it.kg}
        Text(if(best==null)"Aún no hay PR" else "PR actual: ${fmt(best,unit)} $unit",fontSize=19.sp,color=Blue,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if(rec.size>=2) ProgressChart(rec.sortedBy{it.date}.map{it.kg}, unit)
        else Card(Modifier.fillMaxWidth()){Text("Registra al menos dos PR para ver tu progreso.",Modifier.padding(18.dp),color=Slate)}
        Spacer(Modifier.height(12.dp))
        Button(onClick=add,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Registrar nuevo PR")}
        Spacer(Modifier.height(12.dp))
        rec.sortedByDescending{it.date}.forEach { r ->
            Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)){Text("${fmt(r.kg,unit)} $unit",fontSize=20.sp,fontWeight=FontWeight.Bold);Text(r.date);if(r.comment.isNotBlank())Text(r.comment,color=Slate)}
                    IconButton({delete(r)}){Icon(Icons.Default.Delete,null)}
                }
            }
        }
    }
}

@Composable fun ProgressChart(values:List<Double>,unit:String){
    Card(Modifier.fillMaxWidth().height(210.dp),shape=RoundedCornerShape(18.dp)){
        Canvas(Modifier.fillMaxSize().padding(18.dp)){
            val min=values.minOrNull()?:0.0; val max=values.maxOrNull()?:1.0; val range=(max-min).coerceAtLeast(1.0)
            val pts=values.mapIndexed{i,v->Offset(i*(size.width/(values.size-1).coerceAtLeast(1)),size.height-((v-min)/range*size.height).toFloat())}
            for(i in 0 until pts.size-1) drawLine(Blue,pts[i],pts[i+1],6f)
            pts.forEach{drawCircle(Blue,7f,it)}
        }
    }
}

@Composable fun Settings(unit:String,set:(String)->Unit){
    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text("Ajustes",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Slate)
        Text("Unidad de peso",fontWeight=FontWeight.Bold)
        Row(verticalAlignment=Alignment.CenterVertically){
            FilterChip(unit=="kg",{set("kg")},"kg")
            Spacer(Modifier.width(10.dp))
            FilterChip(unit=="lb",{set("lb")},"lb")
        }
        Text("Los datos se almacenan localmente en el teléfono.",color=Slate)
    }
}
@Composable fun FilterChip(selected:Boolean,onClick:()->Unit,label:String)=AssistChip(onClick=onClick,label={Text(label)},leadingIcon=if(selected){{Icon(Icons.Default.Check,null)}} else null)

@Composable fun AddRecordDialog(ex:List<Exercise>,unit:String,done:(Exercise,Double,String,String)->Unit,cancel:()->Unit){
    var selected by remember{mutableStateOf(ex.first())}; var weight by remember{mutableStateOf("")}; var comment by remember{mutableStateOf("")}
    val date=SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(Date())
    AlertDialog(onDismissRequest=cancel,title={Text("Registrar PR")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Ejercicio")
            ex.forEach{e->if(e==selected)Text("${e.icon} ${e.name}",fontWeight=FontWeight.Bold) }
            LazyColumn(Modifier.height(100.dp)){items(ex){e->Text("${e.icon} ${e.name}",Modifier.fillMaxWidth().clickable{selected=e}.padding(6.dp))}}
            OutlinedTextField(weight,{weight=it},label={Text("Peso ($unit)")})
            OutlinedTextField(comment,{comment=it},label={Text("Comentarios")})
            Text("Fecha: $date",color=Slate)
        }
    },confirmButton={Button(onClick={weight.toDoubleOrNull()?.let{done(selected,it,date,comment)}}){Text("Guardar")}},dismissButton={TextButton(onClick=cancel){Text("Cancelar")}})
}

fun defaultExercises()=listOf(
Exercise("bs","Back Squat","🏋️"),Exercise("fs","Front Squat","🏋️"),Exercise("dl","Deadlift","🦾"),
Exercise("sn","Snatch","🏋️"),Exercise("cj","Clean & Jerk","🏋️"),Exercise("pc","Power Clean","⚡"),
Exercise("c","Clean","🏋️"),Exercise("ohp","Overhead Press","💪"),Exercise("bp","Bench Press","💪"),
Exercise("thr","Thruster","🔥"),Exercise("ohs","Overhead Squat","🏋️"),Exercise("row","Barbell Row","🦾")
)
fun fmt(kg:Double,u:String)=String.format(Locale.US,"%.1f",if(u=="kg")kg else kg*2.20462262)
fun encodeExercises(x:List<Exercise>)=JSONArray().apply{x.forEach{put(JSONObject().apply{put("id",it.id);put("name",it.name);put("icon",it.icon)})}}.toString()
fun decodeExercises(s:String)=runCatching{val a=JSONArray(s);List(a.length()){i->val o=a.getJSONObject(i);Exercise(o.getString("id"),o.getString("name"),o.getString("icon"))}}.getOrDefault(defaultExercises())
fun encodeRecords(x:List<Record>)=JSONArray().apply{x.forEach{put(JSONObject().apply{put("id",it.id);put("exerciseId",it.exerciseId);put("kg",it.kg);put("date",it.date);put("comment",it.comment)})}}.toString()
fun decodeRecords(s:String)=runCatching{val a=JSONArray(s);List(a.length()){i->val o=a.getJSONObject(i);Record(o.getString("id"),o.getString("exerciseId"),o.getDouble("kg"),o.getString("date"),o.getString("comment"))}}.getOrDefault(emptyList())

package com.paopr.tracker

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
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
data class Record(
    val id: String,
    val exerciseId: String,
    val kg: Double,
    val date: String,
    val comment: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PaoPRApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaoPRApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exercises by remember { mutableStateOf(defaultExercises()) }
    var records by remember { mutableStateOf(listOf<Record>()) }
    var unit by remember { mutableStateOf("kg") }
    var screen by remember { mutableStateOf("dashboard") }
    var selected by remember { mutableStateOf<Exercise?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<Record?>(null) }

    LaunchedEffect(Unit) {
        val prefs = (context as MainActivity).dataStore.data.first()
        unit = prefs[stringPreferencesKey("unit")] ?: "kg"
        prefs[stringPreferencesKey("exercises")]?.let { exercises = decodeExercises(it) }
        prefs[stringPreferencesKey("records")]?.let { records = decodeRecords(it) }
    }

    fun save() {
        scope.launch {
            (context as MainActivity).dataStore.edit {
                it[stringPreferencesKey("unit")] = unit
                it[stringPreferencesKey("exercises")] = encodeExercises(exercises)
                it[stringPreferencesKey("records")] = encodeRecords(records)
            }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            background = Pale,
            surface = Color.White
        )
    ) {
        Scaffold(
            containerColor = Pale,
            topBar = {
                TopAppBar(
                    title = { Text("PaoPR Tracker", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Pale)
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == "dashboard",
                        onClick = { screen = "dashboard" },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Inicio") }
                    )
                    NavigationBarItem(
                        selected = screen == "exercises",
                        onClick = { screen = "exercises" },
                        icon = { Icon(Icons.Default.FitnessCenter, null) },
                        label = { Text("Ejercicios") }
                    )
                    NavigationBarItem(
                        selected = screen == "settings",
                        onClick = { screen = "settings" },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Ajustes") }
                    )
                }
            },
            floatingActionButton = {
                if (screen == "dashboard" || screen == "exercises") {
                    FloatingActionButton(
                        onClick = { showAdd = true },
                        containerColor = Blue
                    ) {
                        Icon(Icons.Default.Add, null)
                    }
                }
            }
        ) { pad ->
            Box(
                Modifier
                    .padding(pad)
                    .fillMaxSize()
            ) {
                when (screen) {
                    "dashboard" -> Dashboard(
                        exercises, records, unit,
                        { e -> selected = e; screen = "detail" },
                        { showAdd = true }
                    )

                    "exercises" -> ExerciseList(
                        exercises,
                        { e -> selected = e; screen = "detail" },
                        { showAdd = true }
                    )

                    "detail" -> selected?.let { exercise ->
                        Detail(
                            exercise,
                            records.filter { it.exerciseId == exercise.id },
                            unit,
                            { record ->
                                records = records.filterNot { it.id == record.id }
                                save()
                            },
                            { record -> editingRecord = record },
                            { showAdd = true }
                        )
                    }

                    "settings" -> Settings(unit) {
                        unit = it
                        save()
                    }
                }

                if (showAdd) {
                    RecordDialog(
                        title = "Registrar PR",
                        exercises = exercises,
                        unit = unit,
                        initialRecord = null,
                        preselectedExercise = selected,
                        onSave = { ex, value, date, comment ->
                            records = records + Record(
                                UUID.randomUUID().toString(),
                                ex.id,
                                if (unit == "kg") value else value / 2.20462262,
                                date,
                                comment
                            )
                            showAdd = false
                            save()
                        },
                        onCancel = { showAdd = false }
                    )
                }

                editingRecord?.let { record ->
                    RecordDialog(
                        title = "Editar PR",
                        exercises = exercises,
                        unit = unit,
                        initialRecord = record,
                        preselectedExercise = null,
                        onSave = { ex, value, date, comment ->
                            records = records.map {
                                if (it.id == record.id) {
                                    it.copy(
                                        exerciseId = ex.id,
                                        kg = if (unit == "kg") value else value / 2.20462262,
                                        date = date,
                                        comment = comment
                                    )
                                } else it
                            }
                            editingRecord = null
                            save()
                        },
                        onCancel = { editingRecord = null }
                    )
                }
            }
        }
    }
}

@Composable
fun Dashboard(
    ex: List<Exercise>,
    rec: List<Record>,
    unit: String,
    open: (Exercise) -> Unit,
    add: () -> Unit
) {
    val latest = rec.maxByOrNull { it.date }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("Tu rendimiento", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Slate)
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("PR registrados", color = Slate)
                    Text("${rec.size}", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Blue)

                    if (latest != null) {
                        val e = ex.firstOrNull { it.id == latest.exerciseId }
                        Text(
                            "Último: ${e?.name ?: ""} · ${fmt(latest.kg, unit)} $unit",
                            color = Slate
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = add,
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Registrar PR")
            }
        }

        item {
            Text("Mis ejercicios", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Slate)
        }

        items(ex, key = { it.id }) { e ->
            val best = rec.filter { it.exerciseId == e.id }.maxOfOrNull { it.kg }

            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { open(e) },
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.icon, fontSize = 28.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (best == null) "Sin registros"
                            else "PR ${fmt(best, unit)} $unit",
                            color = Slate,
                            fontSize = 13.sp
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
    }
}

@Composable
fun ExerciseList(
    ex: List<Exercise>,
    open: (Exercise) -> Unit,
    add: () -> Unit
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("Ejercicios", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Slate)
            Spacer(Modifier.height(4.dp))
        }

        items(ex, key = { it.id }) { e ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { open(e) },
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.icon, fontSize = 28.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(e.name, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
    }
}

@Composable
fun Detail(
    e: Exercise,
    rec: List<Record>,
    unit: String,
    delete: (Record) -> Unit,
    edit: (Record) -> Unit,
    add: () -> Unit
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text("${e.icon}  ${e.name}", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Slate)
        }

        item {
            val best = rec.maxOfOrNull { it.kg }
            Text(
                if (best == null) "Aún no hay PR"
                else "PR actual: ${fmt(best, unit)} $unit",
                fontSize = 19.sp,
                color = Blue,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            if (rec.size >= 2) {
                ProgressChart(rec.sortedBy { it.date }.map { it.kg })
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Registra al menos dos PR para ver tu progreso.",
                        Modifier.padding(18.dp),
                        color = Slate
                    )
                }
            }
        }

        item {
            Button(onClick = add, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Registrar nuevo PR")
            }
        }

        if (rec.isNotEmpty()) {
            item {
                Text(
                    "Registros guardados",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        items(rec.sortedByDescending { it.date }, key = { it.id }) { r ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${fmt(r.kg, unit)} $unit",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(r.date, color = Slate)

                        if (r.comment.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(r.comment, color = Slate)
                        }
                    }

                    IconButton(onClick = { edit(r) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar registro")
                    }

                    IconButton(onClick = { delete(r) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar registro")
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressChart(values: List<Double>) {
    Card(
        Modifier
            .fillMaxWidth()
            .height(210.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(18.dp)
        ) {
            val min = values.minOrNull() ?: 0.0
            val max = values.maxOrNull() ?: 1.0
            val range = (max - min).coerceAtLeast(1.0)

            val pts = values.mapIndexed { i, v ->
                Offset(
                    i * (size.width / (values.size - 1).coerceAtLeast(1)),
                    size.height - ((v - min) / range * size.height).toFloat()
                )
            }

            for (i in 0 until pts.size - 1) {
                drawLine(Blue, pts[i], pts[i + 1], 6f)
            }

            pts.forEach { drawCircle(Blue, 7f, it) }
        }
    }
}

@Composable
fun Settings(unit: String, set: (String) -> Unit) {
    Column(
        Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ajustes", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Slate)
        Text("Unidad de peso", fontWeight = FontWeight.Bold)

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(unit == "kg", { set("kg") }, "kg")
            Spacer(Modifier.width(10.dp))
            FilterChip(unit == "lb", { set("lb") }, "lb")
        }

        Text("Los datos se almacenan localmente en el teléfono.", color = Slate)
    }
}

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) = AssistChip(
    onClick = onClick,
    label = { Text(label) },
    leadingIcon = if (selected) {
        { Icon(Icons.Default.Check, null) }
    } else null
)

@Composable
fun RecordDialog(
    title: String,
    exercises: List<Exercise>,
    unit: String,
    initialRecord: Record?,
    preselectedExercise: Exercise? = null,
    onSave: (Exercise, Double, String, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    var selected by remember(initialRecord, preselectedExercise) {
        mutableStateOf(
            preselectedExercise
                ?: exercises.firstOrNull { it.id == initialRecord?.exerciseId }
                ?: exercises.first()
        )
    }

    var weight by remember(initialRecord, unit) {
        mutableStateOf(
            initialRecord?.let {
                fmt(it.kg, unit)
            } ?: ""
        )
    }

    var comment by remember(initialRecord) {
        mutableStateOf(initialRecord?.comment ?: "")
    }

    var date by remember(initialRecord) {
        mutableStateOf(
            initialRecord?.date
                ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
    }

    var showExerciseList by remember { mutableStateOf(false) }

    fun openDatePicker() {
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)
        }.getOrNull() ?: Date()

        val calendar = Calendar.getInstance().apply {
            time = parsed
        }

        DatePickerDialog(
            context,
            { _, year, month, day ->
                date = String.format(
                    Locale.US,
                    "%04d-%02d-%02d",
                    year,
                    month + 1,
                    day
                )
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ejercicio", fontWeight = FontWeight.Bold)

                OutlinedButton(
                    onClick = { showExerciseList = !showExerciseList },
                    Modifier.fillMaxWidth()
                ) {
                    Text("${selected.icon} ${selected.name}")
                }

                if (showExerciseList) {
                    Card(Modifier.fillMaxWidth()) {
                        LazyColumn(Modifier.heightIn(max = 180.dp)) {
                            items(exercises) { e ->
                                Text(
                                    "${e.icon} ${e.name}",
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selected = e
                                            showExerciseList = false
                                        }
                                        .padding(12.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Peso ($unit)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Fecha", fontWeight = FontWeight.Bold)

                OutlinedButton(
                    onClick = { openDatePicker() },
                    Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarToday, null)
                    Spacer(Modifier.width(8.dp))
                    Text(date)
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comentarios") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    weight.toDoubleOrNull()?.let {
                        onSave(selected, it, date, comment)
                    }
                },
                enabled = weight.toDoubleOrNull() != null
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancelar")
            }
        }
    )
}

fun defaultExercises() = listOf(
    Exercise("bs", "Back Squat", "🏋️"),
    Exercise("fs", "Front Squat", "🏋️"),
    Exercise("dl", "Deadlift", "🦾"),
    Exercise("sn", "Snatch", "🏋️"),
    Exercise("cj", "Clean & Jerk", "🏋️"),
    Exercise("pc", "Power Clean", "⚡"),
    Exercise("c", "Clean", "🏋️"),
    Exercise("ohp", "Overhead Press", "💪"),
    Exercise("bp", "Bench Press", "💪"),
    Exercise("thr", "Thruster", "🔥"),
    Exercise("ohs", "Overhead Squat", "🏋️"),
    Exercise("row", "Barbell Row", "🦾")
)

fun fmt(kg: Double, u: String) = String.format(
    Locale.US,
    "%.1f",
    if (u == "kg") kg else kg * 2.20462262
)

fun encodeExercises(x: List<Exercise>) = JSONArray().apply {
    x.forEach {
        put(
            JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("icon", it.icon)
            }
        )
    }
}.toString()

fun decodeExercises(s: String) = runCatching {
    val a = JSONArray(s)
    List(a.length()) { i ->
        val o = a.getJSONObject(i)
        Exercise(
            o.getString("id"),
            o.getString("name"),
            o.getString("icon")
        )
    }
}.getOrDefault(defaultExercises())

fun encodeRecords(x: List<Record>) = JSONArray().apply {
    x.forEach {
        put(
            JSONObject().apply {
                put("id", it.id)
                put("exerciseId", it.exerciseId)
                put("kg", it.kg)
                put("date", it.date)
                put("comment", it.comment)
            }
        )
    }
}.toString()

fun decodeRecords(s: String) = runCatching {
    val a = JSONArray(s)
    List(a.length()) { i ->
        val o = a.getJSONObject(i)
        Record(
            o.getString("id"),
            o.getString("exerciseId"),
            o.getDouble("kg"),
            o.getString("date"),
            o.getString("comment")
        )
    }
}.getOrDefault(emptyList())

package com.danzz.kwhmeter

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

// ================= DATA CLASSES =================
data class BlinkRecord(
    val blinkNumber: Int,
    val timeSeconds: Double
)

data class PelangganData(
    val idPelanggan: String,
    val nama: String,
    val alamat: String,
    val fotoPath: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class CalculationResult(
    val p1: Double,
    val p2: Double,
    val error: Double,
    val status: String,
    val kelasMeter: Double,
    val mode: Int
)

data class InputData(
    val arus: String = "",
    val classMeter: String = "",
    val selectedBlinkIndex: Int? = null,
    val blinkCount: Int = 0,
    val elapsedTime: Long = 0,
    val p1Input: String = "",
    val phaseR: String = "",
    val phaseS: String = "",
    val phaseT: String = ""
)

data class RiwayatData(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val pelangganData: PelangganData,
    val mode: Int,
    val inputData: InputData,
    val calculationResult: CalculationResult
)

// ================= SIMPLE STORAGE MANAGER =================
object SimpleStorageManager {
    private const val RIWAYAT_LIST_KEY = "riwayat_list"
    private val gson = Gson()
    
    fun saveRiwayat(context: Context, riwayat: RiwayatData) {
        val sharedPref = context.getSharedPreferences("kwh_storage", Context.MODE_PRIVATE)
        val currentList = getRiwayatList(context).toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == riwayat.id }
        
        if (existingIndex != -1) {
            currentList[existingIndex] = riwayat
        } else {
            currentList.add(riwayat)
        }
        
        val json = gson.toJson(currentList)
        sharedPref.edit().putString(RIWAYAT_LIST_KEY, json).apply()
    }
    
    fun getRiwayatList(context: Context): List<RiwayatData> {
        val sharedPref = context.getSharedPreferences("kwh_storage", Context.MODE_PRIVATE)
        val json = sharedPref.getString(RIWAYAT_LIST_KEY, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<RiwayatData>>() {}.type
            gson.fromJson<List<RiwayatData>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getRiwayatById(context: Context, id: String): RiwayatData? {
        return getRiwayatList(context).find { it.id == id }
    }
    
    fun deleteRiwayatById(context: Context, id: String) {
        val sharedPref = context.getSharedPreferences("kwh_storage", Context.MODE_PRIVATE)
        val currentList = getRiwayatList(context)
        val newList = currentList.filter { it.id != id }
        val json = gson.toJson(newList)
        sharedPref.edit().putString(RIWAYAT_LIST_KEY, json).apply()
    }
    
    fun deleteAllRiwayat(context: Context) {
        val sharedPref = context.getSharedPreferences("kwh_storage", Context.MODE_PRIVATE)
        sharedPref.edit().remove(RIWAYAT_LIST_KEY).apply()
    }
}

// ================= VIEWMODEL =================
class KwhViewModel(application: Application) : AndroidViewModel(application) {
    
    // Mode: 1 = 1 Phase, 2 = 3 Phase
    private val _mode = MutableStateFlow(1)
    val mode: StateFlow<Int> = _mode.asStateFlow()
    
    private val _blinkCount = MutableStateFlow(0)
    val blinkCount: StateFlow<Int> = _blinkCount.asStateFlow()
    
    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()
    
    private val _isCounting = MutableStateFlow(false)
    val isCounting: StateFlow<Boolean> = _isCounting.asStateFlow()
    
    private val _startTime = MutableStateFlow(0L)
    val startTime: StateFlow<Long> = _startTime.asStateFlow()
    
    // Mode 1 inputs
    private val _arus = MutableStateFlow("5.0")
    val arus: StateFlow<String> = _arus.asStateFlow()
    
    private val _classMeter = MutableStateFlow("1.0")
    val classMeter: StateFlow<String> = _classMeter.asStateFlow()
    
    // Mode 2 inputs
    private val _p1Input = MutableStateFlow("10.0")
    val p1Input: StateFlow<String> = _p1Input.asStateFlow()
    
    private val _phaseR = MutableStateFlow("3.5")
    val phaseR: StateFlow<String> = _phaseR.asStateFlow()
    
    private val _phaseS = MutableStateFlow("3.5")
    val phaseS: StateFlow<String> = _phaseS.asStateFlow()
    
    private val _phaseT = MutableStateFlow("3.0")
    val phaseT: StateFlow<String> = _phaseT.asStateFlow()
    
    // Blink records
    private val _blinkRecords = MutableStateFlow<List<BlinkRecord>>(emptyList())
    val blinkRecords: StateFlow<List<BlinkRecord>> = _blinkRecords.asStateFlow()
    
    private val _selectedBlinkIndex = MutableStateFlow<Int?>(null)
    val selectedBlinkIndex: StateFlow<Int?> = _selectedBlinkIndex.asStateFlow()
    
    // Pelanggan data
    private val _idPelanggan = MutableStateFlow("")
    val idPelanggan: StateFlow<String> = _idPelanggan.asStateFlow()
    
    private val _namaPelanggan = MutableStateFlow("")
    val namaPelanggan: StateFlow<String> = _namaPelanggan.asStateFlow()
    
    private val _alamatPelanggan = MutableStateFlow("")
    val alamatPelanggan: StateFlow<String> = _alamatPelanggan.asStateFlow()
    
    private val _fotoPath = MutableStateFlow<String?>(null)
    val fotoPath: StateFlow<String?> = _fotoPath.asStateFlow()
    
    // Riwayat data from storage
    private val _riwayatList = MutableStateFlow<List<RiwayatData>>(emptyList())
    val riwayatList: StateFlow<List<RiwayatData>> = _riwayatList.asStateFlow()
    
    // Current riwayat ID untuk edit
    private val _currentRiwayatId = MutableStateFlow<String?>(null)
    val currentRiwayatId: StateFlow<String?> = _currentRiwayatId.asStateFlow()
    
    // Constants
    companion object {
        const val VOLTAGE = 220.0
        const val COSPHI = 0.85
        const val CONSTANTA = 1600.0
    }
    
    init {
        loadRiwayatFromStorage()
    }
    
    // Load data dari storage
    private fun loadRiwayatFromStorage() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val list = SimpleStorageManager.getRiwayatList(context)
            _riwayatList.value = list
        }
    }
    
    // Setters
    fun setMode(value: Int) {
        _mode.value = value
        if (value == 1) {
            resetMode1()
        }
    }
    
    fun setArus(value: String) { _arus.value = value }
    fun setClassMeter(value: String) { _classMeter.value = value }
    fun setP1Input(value: String) { _p1Input.value = value }
    fun setPhaseR(value: String) { _phaseR.value = value }
    fun setPhaseS(value: String) { _phaseS.value = value }
    fun setPhaseT(value: String) { _phaseT.value = value }
    
    fun setIdPelanggan(value: String) { _idPelanggan.value = value }
    fun setNamaPelanggan(value: String) { _namaPelanggan.value = value }
    fun setAlamatPelanggan(value: String) { _alamatPelanggan.value = value }
    fun setFotoPath(value: String?) { _fotoPath.value = value }
    
    // Timer functions
    fun startTimer() {
        if (!_isCounting.value) {
            _startTime.value = System.currentTimeMillis() - _elapsedTime.value
            _isCounting.value = true
        } else {
            _isCounting.value = false
        }
    }
    
    fun updateElapsedTime(time: Long) {
        _elapsedTime.value = time
    }
    
    fun incrementBlink() {
        if (!_isCounting.value && _blinkCount.value == 0) {
            _isCounting.value = true
            _startTime.value = System.currentTimeMillis()
            _blinkRecords.value = emptyList()
            _blinkCount.value = 0
            _selectedBlinkIndex.value = null
        }
        
        _blinkCount.value = _blinkCount.value + 1
        val currentTime = _elapsedTime.value / 1000.0
        val newRecord = BlinkRecord(_blinkCount.value, currentTime)
        _blinkRecords.value = _blinkRecords.value + newRecord
        
        if (_selectedBlinkIndex.value == null) {
            _selectedBlinkIndex.value = _blinkCount.value - 1
        }
    }
    
    fun selectBlink(index: Int) {
        _selectedBlinkIndex.value = index
    }
    
    fun resetMode1() {
        _blinkCount.value = 0
        _elapsedTime.value = 0
        _isCounting.value = false
        _blinkRecords.value = emptyList()
        _selectedBlinkIndex.value = null
    }
    
    // Calculations
    fun calculateResults(): CalculationResult {
        val modeValue = _mode.value
        val p1 = calculateP1(modeValue)
        val p2 = calculateP2(modeValue)
        val kelasMeterValue = try { _classMeter.value.toDouble() } catch (e: Exception) { 1.0 }
        val error = if (p2 != 0.0) ((p1 - p2) / p2) * 100 else 0.0
        val status = if (Math.abs(error) <= kelasMeterValue) "DI DALAM KELAS METER" else "DI LUAR KELAS METER"
        
        return CalculationResult(p1, p2, error, status, kelasMeterValue, modeValue)
    }
    
    private fun calculateP1(mode: Int): Double {
        return when (mode) {
            1 -> {
                val selectedIndex = _selectedBlinkIndex.value
                if (selectedIndex != null && selectedIndex < _blinkRecords.value.size) {
                    val record = _blinkRecords.value[selectedIndex]
                    if (record.timeSeconds > 0) {
                        val blinkPerSecond = record.blinkNumber.toDouble() / record.timeSeconds
                        (3600 * blinkPerSecond) / CONSTANTA
                    } else 0.0
                } else 0.0
            }
            2 -> {
                try { _p1Input.value.toDouble() } catch (e: Exception) { 0.0 }
            }
            else -> 0.0
        }
    }
    
    private fun calculateP2(mode: Int): Double {
        return when (mode) {
            1 -> {
                try {
                    if (_arus.value.isNotEmpty()) {
                        (VOLTAGE * _arus.value.toDouble() * COSPHI) / 1000
                    } else 0.0
                } catch (e: Exception) { 0.0 }
            }
            2 -> {
                try {
                    _phaseR.value.toDouble() + _phaseS.value.toDouble() + _phaseT.value.toDouble()
                } catch (e: Exception) { 0.0 }
            }
            else -> 0.0
        }
    }
    
    // Storage operations
    fun saveRiwayat() {
        viewModelScope.launch {
            try {
                val calculationResult = calculateResults()
                val context = getApplication<Application>()
                
                val riwayatData = RiwayatData(
                    id = _currentRiwayatId.value ?: UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    pelangganData = PelangganData(
                        idPelanggan = _idPelanggan.value,
                        nama = _namaPelanggan.value,
                        alamat = _alamatPelanggan.value,
                        fotoPath = _fotoPath.value ?: ""
                    ),
                    mode = _mode.value,
                    inputData = InputData(
                        arus = _arus.value,
                        classMeter = _classMeter.value,
                        selectedBlinkIndex = _selectedBlinkIndex.value,
                        blinkCount = _blinkCount.value,
                        elapsedTime = _elapsedTime.value,
                        p1Input = _p1Input.value,
                        phaseR = _phaseR.value,
                        phaseS = _phaseS.value,
                        phaseT = _phaseT.value
                    ),
                    calculationResult = calculationResult
                )
                
                SimpleStorageManager.saveRiwayat(context, riwayatData)
                loadRiwayatFromStorage()
                resetForm()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun loadRiwayat(id: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val riwayat = SimpleStorageManager.getRiwayatById(context, id)
            riwayat?.let {
                _currentRiwayatId.value = it.id
                _idPelanggan.value = it.pelangganData.idPelanggan
                _namaPelanggan.value = it.pelangganData.nama
                _alamatPelanggan.value = it.pelangganData.alamat
                _fotoPath.value = it.pelangganData.fotoPath
                _mode.value = it.mode
                
                if (it.mode == 1) {
                    _arus.value = it.inputData.arus
                    _classMeter.value = it.inputData.classMeter
                    _blinkCount.value = it.inputData.blinkCount
                    _elapsedTime.value = it.inputData.elapsedTime
                    _selectedBlinkIndex.value = it.inputData.selectedBlinkIndex
                    
                    // Reconstruct blink records
                    val records = mutableListOf<BlinkRecord>()
                    for (i in 1..it.inputData.blinkCount) {
                        records.add(BlinkRecord(i, it.inputData.elapsedTime / 1000.0))
                    }
                    _blinkRecords.value = records
                } else {
                    _p1Input.value = it.inputData.p1Input
                    _phaseR.value = it.inputData.phaseR
                    _phaseS.value = it.inputData.phaseS
                    _phaseT.value = it.inputData.phaseT
                    _classMeter.value = it.inputData.classMeter
                }
            }
        }
    }
    
    fun deleteRiwayat(id: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            SimpleStorageManager.deleteRiwayatById(context, id)
            loadRiwayatFromStorage()
        }
    }
    
    fun clearRiwayat() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            SimpleStorageManager.deleteAllRiwayat(context)
            _riwayatList.value = emptyList()
        }
    }
    
    fun resetForm() {
        _idPelanggan.value = ""
        _namaPelanggan.value = ""
        _alamatPelanggan.value = ""
        _fotoPath.value = null
        _currentRiwayatId.value = null
        
        if (_mode.value == 1) {
            resetMode1()
            _arus.value = "5.0"
            _classMeter.value = "1.0"
        } else {
            _p1Input.value = "10.0"
            _phaseR.value = "3.5"
            _phaseS.value = "3.5"
            _phaseT.value = "3.0"
            _classMeter.value = "1.0"
        }
    }
    
    fun isValidClassMeter(value: String): Boolean {
        val validValues = listOf("1.0", "0.5", "0.2", "1", "0.5", "0.2")
        return value in validValues
    }
    
    fun isCalculationValid(): Boolean {
        return when (_mode.value) {
            1 -> _selectedBlinkIndex.value != null && 
                  _arus.value.isNotEmpty() && 
                  _arus.value.toDoubleOrNull() != null
            2 -> _p1Input.value.isNotEmpty() && 
                  _p1Input.value.toDoubleOrNull() != null && 
                  _phaseR.value.isNotEmpty() && 
                  _phaseS.value.isNotEmpty() && 
                  _phaseT.value.isNotEmpty()
            else -> false
        }
    }
    
    fun isFormValidForSave(): Boolean {
        return idPelanggan.value.isNotEmpty() &&
               namaPelanggan.value.isNotEmpty() &&
               alamatPelanggan.value.isNotEmpty() &&
               fotoPath.value != null &&
               isCalculationValid()
    }
}

// ================= UTILITY FUNCTIONS =================
fun createImageFile(context: Context): File? {
    return try {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        
        // Get the pictures directory
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        
        // Create the directory if it doesn't exist
        if (!storageDir?.exists()!!) {
            storageDir.mkdirs()
        }
        
        // Create the file
        File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",         /* suffix */
            storageDir      /* directory */
        )
    } catch (ex: IOException) {
        // Error occurred while creating the File
        ex.printStackTrace()
        null
    }
}

fun isValidClassMeter(value: String): Boolean {
    val validValues = listOf("1.0", "0.5", "0.2", "1", "0.5", "0.2")
    return value in validValues
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// ================= MAIN ACTIVITY =================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KwhMeterApp()
        }
    }
}

@Composable
fun KwhMeterApp() {
    val context = LocalContext.current
    val viewModel: KwhViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return KwhViewModel(context.applicationContext as Application) as T
            }
        }
    )
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF5F5F5)
    ) {
        MainScreen(viewModel)
    }
}

@Composable
fun MainScreen(viewModel: KwhViewModel) {
    var currentTab by remember { mutableStateOf(0) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = currentTab,
            containerColor = Color(0xFF0D6EFD),
            contentColor = Color.White
        ) {
            Tab(
                selected = currentTab == 0,
                onClick = { currentTab = 0 },
                text = { Text("Input Data", color = Color.White) }
            )
            Tab(
                selected = currentTab == 1,
                onClick = { currentTab = 1 },
                text = { 
                    val riwayatCount by viewModel.riwayatList.collectAsState()
                    Text("Riwayat (${riwayatCount.size})", color = Color.White) 
                }
            )
        }
        
        when (currentTab) {
            0 -> InputDataScreen(viewModel, onNavigateToRiwayat = { currentTab = 1 })
            1 -> RiwayatScreen(viewModel, onNavigateToInput = { currentTab = 0 })
        }
    }
}

@Composable
fun InputDataScreen(viewModel: KwhViewModel, onNavigateToRiwayat: () -> Unit) {
    val mode by viewModel.mode.collectAsState()
    val blinkCount by viewModel.blinkCount.collectAsState()
    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val isCounting by viewModel.isCounting.collectAsState()
    val arus by viewModel.arus.collectAsState()
    val classMeter by viewModel.classMeter.collectAsState()
    val p1Input by viewModel.p1Input.collectAsState()
    val phaseR by viewModel.phaseR.collectAsState()
    val phaseS by viewModel.phaseS.collectAsState()
    val phaseT by viewModel.phaseT.collectAsState()
    val blinkRecords by viewModel.blinkRecords.collectAsState()
    val selectedBlinkIndex by viewModel.selectedBlinkIndex.collectAsState()
    
    val idPelanggan by viewModel.idPelanggan.collectAsState()
    val namaPelanggan by viewModel.namaPelanggan.collectAsState()
    val alamatPelanggan by viewModel.alamatPelanggan.collectAsState()
    val fotoPath by viewModel.fotoPath.collectAsState()
    val currentRiwayatId by viewModel.currentRiwayatId.collectAsState()
    
    val results = viewModel.calculateResults()
    val isCalculationValid = viewModel.isCalculationValid()
    val isFormValidForSave = viewModel.isFormValidForSave()
    
    var showSaveSuccess by remember { mutableStateOf(false) }
    var showSaveError by remember { mutableStateOf(false) }
    
    // Camera related states
    val context = LocalContext.current
    var capturedImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    
    LaunchedEffect(isCounting) {
        while (isCounting) {
            delay(10)
            val currentElapsed = System.currentTimeMillis() - viewModel.startTime.value
            viewModel.updateElapsedTime(currentElapsed)
        }
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && capturedImagePath != null) {
            viewModel.setFotoPath(capturedImagePath!!)
            Toast.makeText(context, "Foto berhasil diambil", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Gagal mengambil foto", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, prepare to open camera
            val file = createImageFile(context)
            if (file != null) {
                capturedImagePath = file.absolutePath
                val photoURI = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                cameraLauncher.launch(photoURI)
            } else {
                Toast.makeText(context, "Gagal membuat file", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Permission denied
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    context as Activity,
                    Manifest.permission.CAMERA
                )
            ) {
                showPermissionRationale = true
            } else {
                showPermissionDialog = true
            }
        }
    }
    
    // Open settings launcher
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Coba lagi setelah user kembali dari settings
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    
    // Function to open camera
    fun openCamera() {
        val permission = Manifest.permission.CAMERA
        val permissionCheckResult = ContextCompat.checkSelfPermission(context, permission)
        
        when {
            permissionCheckResult == PackageManager.PERMISSION_GRANTED -> {
                // Permission sudah diberikan
                val file = createImageFile(context)
                if (file != null) {
                    capturedImagePath = file.absolutePath
                    val photoURI = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    cameraLauncher.launch(photoURI)
                } else {
                    Toast.makeText(context, "Gagal membuat file", Toast.LENGTH_SHORT).show()
                }
            }
            
            ActivityCompat.shouldShowRequestPermissionRationale(
                context as Activity,
                permission
            ) -> {
                // Show rationale
                showPermissionRationale = true
            }
            
            else -> {
                // Request permission
                permissionLauncher.launch(permission)
            }
        }
    }
    
    // Dialogs
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Izin Kamera Diperlukan") },
            text = { 
                Text("Aplikasi memerlukan izin kamera untuk mengambil foto. " +
                     "Silakan aktifkan izin kamera di pengaturan aplikasi.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        // Buka settings
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", context.packageName, null)
                        intent.data = uri
                        settingsLauncher.launch(intent)
                    }
                ) {
                    Text("Buka Pengaturan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
    
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Izin Kamera Diperlukan") },
            text = { 
                Text("Aplikasi memerlukan izin kamera untuk mengambil foto dokumentasi pelanggan. " +
                     "Foto diperlukan sebagai bukti fisik pengukuran.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                ) {
                    Text("Izinkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Tolak")
                }
            }
        )
    }
    
    if (showSaveSuccess) {
        AlertDialog(
            onDismissRequest = { showSaveSuccess = false },
            title = { Text("Berhasil") },
            text = { Text("Data berhasil disimpan") },
            confirmButton = {
                Button(
                    onClick = { 
                        showSaveSuccess = false
                        onNavigateToRiwayat()
                    }
                ) {
                    Text("Lihat Riwayat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveSuccess = false }) {
                    Text("Tutup")
                }
            }
        )
    }
    
    if (showSaveError) {
        AlertDialog(
            onDismissRequest = { showSaveError = false },
            title = { Text("Gagal") },
            text = { Text("Gagal menyimpan data. Pastikan semua data sudah lengkap") },
            confirmButton = {
                Button(onClick = { showSaveError = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D6EFD)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "KWH Meter Test",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        "Data tersimpan lokal",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
                Text(
                    "Mode: ${if (mode == 1) "1 Phase" else "3 Phase"}",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Pelanggan Input
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Data Pelanggan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    
                    if (currentRiwayatId != null) {
                        Badge(
                            containerColor = Color(0xFFFFC107),
                            contentColor = Color.Black
                        ) {
                            Text("EDIT MODE")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = idPelanggan,
                    onValueChange = { viewModel.setIdPelanggan(it) },
                    label = { Text("ID Pelanggan *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = idPelanggan.isEmpty()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = namaPelanggan,
                    onValueChange = { viewModel.setNamaPelanggan(it) },
                    label = { Text("Nama Pelanggan *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = namaPelanggan.isEmpty()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = alamatPelanggan,
                    onValueChange = { viewModel.setAlamatPelanggan(it) },
                    label = { Text("Alamat *") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    isError = alamatPelanggan.isEmpty()
                )

                Spacer(Modifier.height(12.dp))

                // Camera Button - FIXED
                Button(
                    onClick = {
                        openCamera()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = idPelanggan.isNotEmpty() && namaPelanggan.isNotEmpty()
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (fotoPath == null) "Ambil Foto *" else "Ganti Foto")
                }
                
                if (fotoPath != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "✓ Foto sudah diambil",
                            fontSize = 12.sp,
                            color = Color.Green,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            File(fotoPath!!).name,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Mode Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Pilih Mode Pengukuran",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == 1,
                        onClick = { viewModel.setMode(1) },
                        label = { Text("1 Phase (Kedipan)") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0D6EFD),
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = mode == 2,
                        onClick = { viewModel.setMode(2) },
                        label = { Text("3 Phase") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0D6EFD),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (mode == 1) {
            Mode1Screen(
                blinkCount = blinkCount,
                elapsedTime = elapsedTime,
                isCounting = isCounting,
                arus = arus,
                classMeter = classMeter,
                blinkRecords = blinkRecords,
                selectedBlinkIndex = selectedBlinkIndex,
                results = results,
                isCalculationValid = isCalculationValid,
                onArusChange = { viewModel.setArus(it) },
                onClassMeterChange = { viewModel.setClassMeter(it) },
                onTimerToggle = { viewModel.startTimer() },
                onBlinkClick = { viewModel.incrementBlink() },
                onReset = { viewModel.resetMode1() },
                onSelectBlink = { viewModel.selectBlink(it) }
            )
        } else {
            Mode2Screen(
                p1Input = p1Input,
                phaseR = phaseR,
                phaseS = phaseS,
                phaseT = phaseT,
                classMeter = classMeter,
                results = results,
                isCalculationValid = isCalculationValid,
                onP1InputChange = { viewModel.setP1Input(it) },
                onPhaseRChange = { viewModel.setPhaseR(it) },
                onPhaseSChange = { viewModel.setPhaseS(it) },
                onPhaseTChange = { viewModel.setPhaseT(it) },
                onClassMeterChange = { viewModel.setClassMeter(it) }
            )
        }

        // Action Buttons
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.resetForm() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
                Spacer(Modifier.width(8.dp))
                Text("Reset Form")
            }
            
            Button(
                onClick = {
                    if (isFormValidForSave) {
                        viewModel.saveRiwayat()
                        showSaveSuccess = true
                    } else {
                        showSaveError = true
                    }
                },
                enabled = isFormValidForSave,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    if (currentRiwayatId != null) Icons.Default.Edit else Icons.Default.Save,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (currentRiwayatId != null) "Update" else "Simpan Data")
            }
        }

        // Info Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Info Penyimpanan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF333333)
                    )
                    
                    val riwayatCount by viewModel.riwayatList.collectAsState()
                    Text(
                        "${riwayatCount.size} data tersimpan",
                        fontSize = 12.sp,
                        color = Color(0xFF0D6EFD),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    "• Data disimpan menggunakan SharedPreferences",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "• Tidak hilang saat aplikasi ditutup",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "• Akses cepat dan tanpa internet",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "• Private: hanya tersimpan di device ini",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
            }
        }
    }
}

@Composable
fun RiwayatScreen(viewModel: KwhViewModel, onNavigateToInput: () -> Unit) {
    val riwayatList by viewModel.riwayatList.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D6EFD)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Riwayat Perhitungan",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        "Data tersimpan lokal permanen",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
                
                if (riwayatList.isNotEmpty()) {
                    IconButton(
                        onClick = { showDeleteAllDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Hapus Semua",
                            tint = Color.White
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (riwayatList.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = "Kosong",
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFF6C757D)
                    )
                    Text(
                        "Belum ada data",
                        fontSize = 16.sp,
                        color = Color(0xFF6C757D)
                    )
                    Text(
                        "Simpan data perhitungan untuk melihat riwayat di sini",
                        fontSize = 12.sp,
                        color = Color(0xFF6C757D),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = onNavigateToInput,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D6EFD))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah Data")
                        Spacer(Modifier.width(8.dp))
                        Text("Tambah Data Baru")
                    }
                }
            }
        } else {
            // Database stats
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F1FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Statistik",
                            fontSize = 12.sp,
                            color = Color(0xFF0D6EFD),
                            fontWeight = FontWeight.Medium
                        )
                        val mode1Count = riwayatList.count { it.mode == 1 }
                        val mode2Count = riwayatList.count { it.mode == 2 }
                        Text(
                            "1 Phase: $mode1Count | 3 Phase: $mode2Count",
                            fontSize = 11.sp,
                            color = Color(0xFF0D6EFD)
                        )
                    }
                    
                    Text(
                        "Total: ${riwayatList.size}",
                        fontSize = 14.sp,
                        color = Color(0xFF0D6EFD),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Riwayat list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(riwayatList.sortedByDescending { it.timestamp }) { riwayat ->
                    RiwayatItem(
                        riwayat = riwayat,
                        onEdit = { 
                            viewModel.loadRiwayat(riwayat.id)
                            onNavigateToInput()
                        },
                        onDelete = { viewModel.deleteRiwayat(riwayat.id) }
                    )
                }
            }
        }
    }
    
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Hapus Semua Data") },
            text = { Text("Apakah Anda yakin ingin menghapus semua data? Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearRiwayat()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545))
                ) {
                    Text("Hapus Semua")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun RiwayatItem(
    riwayat: RiwayatData,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Data") },
            text = { Text("Apakah Anda yakin ingin menghapus data ini?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545))
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${riwayat.pelangganData.nama} (${riwayat.pelangganData.idPelanggan})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF333333)
                    )
                    Text(
                        formatTimestamp(riwayat.timestamp),
                        fontSize = 11.sp,
                        color = Color(0xFF6C757D)
                    )
                    Text(
                        "Mode: ${if (riwayat.mode == 1) "1 Phase" else "3 Phase"}",
                        fontSize = 12.sp,
                        color = if (riwayat.mode == 1) Color(0xFF0D6EFD) else Color(0xFF198754),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF0D6EFD)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFDC3545)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Alamat
            Text(
                "📍 ${riwayat.pelangganData.alamat}",
                fontSize = 12.sp,
                color = Color(0xFF6C757D)
            )
            
            if (riwayat.pelangganData.fotoPath.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "📷 Foto: ${File(riwayat.pelangganData.fotoPath).name}",
                    fontSize = 11.sp,
                    color = Color(0xFF6C757D)
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Calculation Results
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "P1",
                                fontSize = 11.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                String.format("%.3f", riwayat.calculationResult.p1) + " kW",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D6EFD)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "P2",
                                fontSize = 11.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                String.format("%.3f", riwayat.calculationResult.p2) + " kW",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF198754)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "ERROR",
                                fontSize = 11.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                String.format("%.2f", riwayat.calculationResult.error) + "%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (Math.abs(riwayat.calculationResult.error) <= riwayat.calculationResult.kelasMeter) 
                                    Color(0xFF198754) 
                                else Color(0xFFDC3545)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "STATUS",
                                fontSize = 11.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                riwayat.calculationResult.status,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (riwayat.calculationResult.status == "DI DALAM KELAS METER") 
                                    Color(0xFF198754) 
                                else Color(0xFFDC3545)
                            )
                        }
                    }
                }
            }
            
            // Input Data Summary
            Spacer(Modifier.height(12.dp))
            Text(
                "Input Data:",
                fontSize = 12.sp,
                color = Color(0xFF6C757D),
                fontWeight = FontWeight.Medium
            )
            
            if (riwayat.mode == 1) {
                Text(
                    "• Arus: ${riwayat.inputData.arus}A | Kedipan: ${riwayat.inputData.blinkCount} | Waktu: ${String.format("%.2f", riwayat.inputData.elapsedTime/1000.0)}s",
                    fontSize = 11.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "• Kelas Meter: ${riwayat.inputData.classMeter}%",
                    fontSize = 11.sp,
                    color = Color(0xFF6C757D)
                )
            } else {
                Text(
                    "• P1: ${riwayat.inputData.p1Input}kW | R:${riwayat.inputData.phaseR} S:${riwayat.inputData.phaseS} T:${riwayat.inputData.phaseT}",
                    fontSize = 11.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "• Kelas Meter: ${riwayat.inputData.classMeter}%",
                    fontSize = 11.sp,
                    color = Color(0xFF6C757D)
                )
            }
            
            // ID
            Spacer(Modifier.height(8.dp))
            Text(
                "ID: ${riwayat.id.take(8)}...",
                fontSize = 10.sp,
                color = Color(0xFF999999)
            )
        }
    }
}

@Composable
fun Mode1Screen(
    blinkCount: Int,
    elapsedTime: Long,
    isCounting: Boolean,
    arus: String,
    classMeter: String,
    blinkRecords: List<BlinkRecord>,
    selectedBlinkIndex: Int?,
    results: CalculationResult,
    isCalculationValid: Boolean,
    onArusChange: (String) -> Unit,
    onClassMeterChange: (String) -> Unit,
    onTimerToggle: () -> Unit,
    onBlinkClick: () -> Unit,
    onReset: () -> Unit,
    onSelectBlink: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Konfigurasi Pengukuran 1 Phase",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )
                
                Spacer(Modifier.height(12.dp))
                
                Text(
                    "Input untuk menghitung P2:",
                    fontSize = 14.sp,
                    color = Color(0xFF6C757D)
                )
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = arus,
                    onValueChange = onArusChange,
                    label = { Text("Arus (Ampere)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = arus.isNotEmpty() && arus.toDoubleOrNull() == null,
                    supportingText = {
                        if (arus.isNotEmpty() && arus.toDoubleOrNull() == null) {
                            Text("Masukkan angka yang valid")
                        } else {
                            Text("Contoh: 5.0, 10.5, 15.0")
                        }
                    }
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = classMeter,
                    onValueChange = onClassMeterChange,
                    label = { Text("Kelas Meter (%)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = classMeter.isNotEmpty() && !isValidClassMeter(classMeter),
                    supportingText = {
                        if (classMeter.isNotEmpty() && !isValidClassMeter(classMeter)) {
                            Text("Harus 1.0, 0.5, atau 0.2")
                        } else {
                            Text("Pilih: 1.0, 0.5, atau 0.2")
                        }
                    }
                )
                
                val selectedRecord = if (selectedBlinkIndex != null && selectedBlinkIndex < blinkRecords.size) {
                    blinkRecords[selectedBlinkIndex]
                } else null
                
                if (selectedRecord != null) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE7F1FF))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Kedipan yang dipilih untuk P1:",
                                    fontSize = 12.sp,
                                    color = Color(0xFF0D6EFD)
                                )
                                Text(
                                    "Kedipan ke-${selectedRecord.blinkNumber} pada detik ${String.format("%.2f", selectedRecord.timeSeconds)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D6EFD)
                                )
                            }
                            Text(
                                "n/T = ${selectedRecord.blinkNumber}/${String.format("%.2f", selectedRecord.timeSeconds)}",
                                fontSize = 12.sp,
                                color = Color(0xFF6C757D)
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Waktu",
                            fontSize = 12.sp,
                            color = Color(0xFF6C757D)
                        )
                        val seconds = elapsedTime / 1000.0
                        Text(
                            String.format("%.2f", seconds) + " detik",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D6EFD)
                        )
                    }
                    Button(
                        onClick = onTimerToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCounting) Color(0xFFDC3545) else Color(0xFF198754)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isCounting) "STOP" else "START")
                    }
                }
                
                Spacer(Modifier.height(20.dp))
                
                Button(
                    onClick = onBlinkClick,
                    modifier = Modifier.size(140.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0D6EFD)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    enabled = isCounting || blinkCount == 0
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "KEDIPAN",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            blinkCount.toString(),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (isCounting) "TAP saat kedipan" else "START dulu",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        Spacer(Modifier.width(8.dp))
                        Text("Reset Timer")
                    }
                }
                
                if (!isCounting && blinkCount == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tekan START lalu TAP tombol setiap kali meter berkedip",
                        fontSize = 12.sp,
                        color = Color(0xFFDC3545),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        if (blinkRecords.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Rekaman Kedipan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF333333)
                        )
                        Text(
                            "Total: ${blinkRecords.size} kedipan",
                            fontSize = 12.sp,
                            color = Color(0xFF6C757D)
                        )
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    LazyColumn(
                        modifier = Modifier.height(150.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(blinkRecords) { record ->
                            BlinkRecordItem(
                                record = record,
                                isSelected = selectedBlinkIndex == blinkRecords.indexOf(record),
                                onClick = { onSelectBlink(blinkRecords.indexOf(record)) }
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Hasil Perhitungan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )
                
                Spacer(Modifier.height(16.dp))
                
                if (!isCalculationValid) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3CD))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Lengkapi semua input untuk melihat hasil",
                            fontSize = 14.sp,
                            color = Color(0xFF856404),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ResultBox(
                            title = "P1 (kW)",
                            value = String.format("%.3f", results.p1),
                            unit = "Dari kedipan meter",
                            color = Color(0xFF0D6EFD),
                            modifier = Modifier.weight(1f)
                        )
                        ResultBox(
                            title = "P2 (kW)",
                            value = String.format("%.3f", results.p2),
                            unit = "Dari arus ${arus}A",
                            color = Color(0xFF198754),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ResultBox(
                            title = "ERROR",
                            value = String.format("%.2f", results.error),
                            unit = "%",
                            color = if (Math.abs(results.error) <= results.kelasMeter) 
                                Color(0xFF198754) 
                            else Color(0xFFDC3545),
                            modifier = Modifier.weight(1f)
                        )
                        ResultBox(
                            title = "KELAS",
                            value = String.format("%.1f", results.kelasMeter),
                            unit = "%",
                            color = Color(0xFF6C757D),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                color = if (results.status == "DI DALAM KELAS METER") 
                                    Color(0xFFD1E7DD) 
                                else Color(0xFFF8D7DA)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                results.status,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (results.status == "DI DALAM KELAS METER") 
                                    Color(0xFF0F5132) 
                                else Color(0xFF842029)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (results.status == "DI DALAM KELAS METER")
                                    "✅ Meter dalam toleransi"
                                else "⚠️ Meter di luar toleransi",
                                fontSize = 12.sp,
                                color = if (results.status == "DI DALAM KELAS METER") 
                                    Color(0xFF0F5132) 
                                else Color(0xFF842029)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Mode2Screen(
    p1Input: String,
    phaseR: String,
    phaseS: String,
    phaseT: String,
    classMeter: String,
    results: CalculationResult,
    isCalculationValid: Boolean,
    onP1InputChange: (String) -> Unit,
    onPhaseRChange: (String) -> Unit,
    onPhaseSChange: (String) -> Unit,
    onPhaseTChange: (String) -> Unit,
    onClassMeterChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Input Data 3 Phase",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = classMeter,
                    onValueChange = onClassMeterChange,
                    label = { Text("Kelas Meter (%)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = classMeter.isNotEmpty() && !isValidClassMeter(classMeter),
                    supportingText = {
                        if (classMeter.isNotEmpty() && !isValidClassMeter(classMeter)) {
                            Text("Harus 1.0, 0.5, atau 0.2")
                        } else {
                            Text("Pilih: 1.0, 0.5, atau 0.2")
                        }
                    }
                )
                
                Spacer(Modifier.height(12.dp))
                
                Text(
                    "Input P1 (Pembacaan Meter):",
                    fontSize = 14.sp,
                    color = Color(0xFF6C757D)
                )
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = p1Input,
                    onValueChange = onP1InputChange,
                    label = { Text("P1 (kW)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = p1Input.isNotEmpty() && p1Input.toDoubleOrNull() == null,
                    supportingText = {
                        if (p1Input.isNotEmpty() && p1Input.toDoubleOrNull() == null) {
                            Text("Masukkan angka yang valid")
                        } else {
                            Text("Nilai pembacaan meter dalam kW")
                        }
                    }
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    "Input P2 (Perhitungan Phase):",
                    fontSize = 14.sp,
                    color = Color(0xFF6C757D)
                )
                Spacer(Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhaseInputField(
                        value = phaseR,
                        onValueChange = onPhaseRChange,
                        label = "Phase R (kW)",
                        color = Color(0xFFDC3545),
                        modifier = Modifier.weight(1f)
                    )
                    PhaseInputField(
                        value = phaseS,
                        onValueChange = onPhaseSChange,
                        label = "Phase S (kW)",
                        color = Color(0xFF198754),
                        modifier = Modifier.weight(1f)
                    )
                    PhaseInputField(
                        value = phaseT,
                        onValueChange = onPhaseTChange,
                        label = "Phase T (kW)",
                        color = Color(0xFF0D6EFD),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                val p2Calculated = try {
                    phaseR.toDouble() + phaseS.toDouble() + phaseT.toDouble()
                } catch (e: Exception) { 0.0 }
                
                Text(
                    "P2 = Phase R + Phase S + Phase T = ${String.format("%.3f", p2Calculated)} kW",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Hasil Perhitungan 3 Phase",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )
                
                Spacer(Modifier.height(16.dp))
                
                if (!isCalculationValid) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3CD))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Lengkapi semua input untuk melihat hasil",
                            fontSize = 14.sp,
                            color = Color(0xFF856404),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ResultBox(
                            title = "P1 (kW)",
                            value = String.format("%.3f", results.p1),
                            unit = "Input langsung",
                            color = Color(0xFF0D6EFD),
                            modifier = Modifier.weight(1f)
                        )
                        ResultBox(
                            title = "P2 (kW)",
                            value = String.format("%.3f", results.p2),
                            unit = "Jumlah 3 Phase",
                            color = Color(0xFF198754),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ResultBox(
                            title = "ERROR",
                            value = String.format("%.2f", results.error),
                            unit = "%",
                            color = if (Math.abs(results.error) <= results.kelasMeter) 
                                Color(0xFF198754) 
                            else Color(0xFFDC3545),
                            modifier = Modifier.weight(1f)
                        )
                        ResultBox(
                            title = "KELAS",
                            value = String.format("%.1f", results.kelasMeter),
                            unit = "%",
                            color = Color(0xFF6C757D),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        "Breakdown Per Phase",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF6C757D)
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PhaseResultBox(
                            label = "Phase R",
                            value = String.format("%.3f", try { phaseR.toDouble() } catch (e: Exception) { 0.0 }),
                            color = Color(0xFFDC3545),
                            modifier = Modifier.weight(1f)
                        )
                        PhaseResultBox(
                            label = "Phase S",
                            value = String.format("%.3f", try { phaseS.toDouble() } catch (e: Exception) { 0.0 }),
                            color = Color(0xFF198754),
                            modifier = Modifier.weight(1f)
                        )
                        PhaseResultBox(
                            label = "Phase T",
                            value = String.format("%.3f", try { phaseT.toDouble() } catch (e: Exception) { 0.0 }),
                            color = Color(0xFF0D6EFD),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                color = if (results.status == "DI DALAM KELAS METER") 
                                    Color(0xFFD1E7DD) 
                                else Color(0xFFF8D7DA)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                results.status,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (results.status == "DI DALAM KELAS METER") 
                                    Color(0xFF0F5132) 
                                else Color(0xFF842029)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (results.status == "DI DALAM KELAS METER")
                                    "✅ Meter dalam toleransi"
                                else "⚠️ Meter di luar toleransi",
                                fontSize = 12.sp,
                                color = if (results.status == "DI DALAM KELAS METER") 
                                    Color(0xFF0F5132) 
                                else Color(0xFF842029)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultBox(
    title: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                fontSize = 12.sp,
                color = Color(0xFF6C757D)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                unit,
                fontSize = 10.sp,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun PhaseInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            fontSize = 12.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = color,
                unfocusedBorderColor = color.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(8.dp),
            isError = value.isNotEmpty() && value.toDoubleOrNull() == null
        )
    }
}

@Composable
fun PhaseResultBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                fontSize = 11.sp,
                color = color,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                "kW",
                fontSize = 10.sp,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun BlinkRecordItem(
    record: BlinkRecord,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE7F1FF) else Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(2.dp, Color(0xFF0D6EFD)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Kedipan ke-${record.blinkNumber}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
                Text(
                    "Pada detik ${String.format("%.2f", record.timeSeconds)}",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                val blinkPerSecond = record.blinkNumber.toDouble() / record.timeSeconds
                Text(
                    "${String.format("%.3f", blinkPerSecond)}/dtk",
                    fontSize = 11.sp,
                    color = Color(0xFF6C757D)
                )
                if (isSelected) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D6EFD)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✓",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
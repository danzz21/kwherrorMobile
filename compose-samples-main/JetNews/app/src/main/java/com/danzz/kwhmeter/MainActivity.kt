package com.danzz.kwhmeter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.io.File
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
    val kelasMeter: Double
)

// ================= VIEWMODEL =================
class KwhViewModel : ViewModel() {
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
    
    // Constants
    companion object {
        const val VOLTAGE = 220.0
        const val COSPHI = 0.85
        const val CONSTANTA = 1600.0
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
        
        // Auto-select latest blink if none selected
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
        
        return CalculationResult(p1, p2, error, status, kelasMeterValue)
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
}

// ================= UTILITY FUNCTIONS =================
fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir(null)
    return File.createTempFile(
        "JPEG_${timeStamp}_",
        ".jpg",
        storageDir
    )
}

fun isValidClassMeter(value: String): Boolean {
    val validValues = listOf("1.0", "0.5", "0.2", "1", "0.5", "0.2")
    return value in validValues
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
    val viewModel: KwhViewModel = viewModel()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF5F5F5)
    ) {
        MainScreen(viewModel)
    }
}

@Composable
fun MainScreen(viewModel: KwhViewModel) {
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
    
    // Pelanggan data
    val idPelanggan by viewModel.idPelanggan.collectAsState()
    val namaPelanggan by viewModel.namaPelanggan.collectAsState()
    val alamatPelanggan by viewModel.alamatPelanggan.collectAsState()
    val fotoPath by viewModel.fotoPath.collectAsState()
    
    // Calculations
    val results = viewModel.calculateResults()
    val isCalculationValid = viewModel.isCalculationValid()
    
    // Timer
    LaunchedEffect(isCounting) {
        while (isCounting) {
            delay(10)
            val currentElapsed = System.currentTimeMillis() - viewModel.startTime.value
            viewModel.updateElapsedTime(currentElapsed)
        }
    }
    
    // Camera related states and launchers
    val context = LocalContext.current
    var currentPhotoPath by rememberSaveable { mutableStateOf("") }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.setFotoPath(currentPhotoPath)
        }
    }
    
    // Camera permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, proceed with camera
            takePhoto(context, cameraLauncher, currentPhotoPath) { path ->
                currentPhotoPath = path
            }
        } else {
            // Permission denied
            showPermissionDialog = true
        }
    }
    
    // Permission dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Izin Kamera Diperlukan") },
            text = { Text("Aplikasi memerlukan izin kamera untuk mengambil foto dokumentasi") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        // Open app settings
                        // val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        // intent.data = Uri.parse("package:${context.packageName}")
                        // context.startActivity(intent)
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
    
    // Function to handle photo capture - dibuat sebagai local function
    fun handlePhotoCapture() {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                takePhoto(context, cameraLauncher, currentPhotoPath) { path ->
                    currentPhotoPath = path
                }
            }
            
            shouldShowRequestPermissionRationale(
                context,
                Manifest.permission.CAMERA
            ) -> {
                // Show explanation dialog
                showPermissionDialog = true
            }
            
            else -> {
                // Request permission
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
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
                        "Error Calculation Tool",
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

        // Pelanggan Input Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Data Pelanggan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = idPelanggan,
                    onValueChange = { viewModel.setIdPelanggan(it) },
                    label = { Text("ID Pelanggan") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = namaPelanggan,
                    onValueChange = { viewModel.setNamaPelanggan(it) },
                    label = { Text("Nama Pelanggan") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = alamatPelanggan,
                    onValueChange = { viewModel.setAlamatPelanggan(it) },
                    label = { Text("Alamat") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { handlePhotoCapture() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = idPelanggan.isNotEmpty() && namaPelanggan.isNotEmpty()
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (fotoPath == null) "Ambil Foto Dokumentasi" else "Ambil Foto Baru")
                }
                
                if (fotoPath != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "✓ Foto sudah diambil",
                        fontSize = 12.sp,
                        color = Color.Green,
                        fontWeight = FontWeight.Medium
                    )
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

        // Main Content based on mode
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

        // Save Button
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                // Save pelanggan data logic here
                // TODO: Implement save functionality
            },
            enabled = idPelanggan.isNotEmpty() &&
                      namaPelanggan.isNotEmpty() &&
                      alamatPelanggan.isNotEmpty() &&
                      fotoPath != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Simpan Data Pelanggan")
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
                Text(
                    "Rumus Perhitungan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF333333)
                )
                Spacer(Modifier.height(8.dp))
                
                if (mode == 1) {
                    Text(
                        "• P1 = (3600 × n/T) ÷ 1600",
                        fontSize = 12.sp,
                        color = Color(0xFF6C757D)
                    )
                    Text(
                        "   n = jumlah kedipan yang dipilih",
                        fontSize = 11.sp,
                        color = Color(0xFF6C757D),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Text(
                        "   T = waktu kedipan ke-n (detik)",
                        fontSize = 11.sp,
                        color = Color(0xFF6C757D),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Text(
                        "• P2 = (220 × Arus × 0.85) ÷ 1000",
                        fontSize = 12.sp,
                        color = Color(0xFF6C757D)
                    )
                } else {
                    Text(
                        "• P1 = Input langsung (kW)",
                        fontSize = 12.sp,
                        color = Color(0xFF6C757D)
                    )
                    Text(
                        "• P2 = Phase R + Phase S + Phase T",
                        fontSize = 12.sp,
                        color = Color(0xFF6C757D)
                    )
                }
                
                Text(
                    "• Error = ((P1 - P2) ÷ P2) × 100%",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "• Status: |Error| ≤ Kelas Meter → DALAM KELAS",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "• Kelas Meter: 1.0%, 0.5%, atau 0.2%",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
            }
        }
    }
}

// Helper function for taking photo
private fun takePhoto(
    context: Context,
    cameraLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Uri, Boolean>,
    currentPhotoPath: String,
    onPathUpdated: (String) -> Unit
) {
    try {
        val file = createImageFile(context)
        val newPath = file.absolutePath
        onPathUpdated(newPath)
        
        val photoURI: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        
        // Grant temporary read permission to the camera app
        val intentFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                         android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.grantUriPermission(
            "com.android.camera",
            photoURI,
            intentFlags
        )
        
        cameraLauncher.launch(photoURI)
    } catch (e: Exception) {
        e.printStackTrace()
        // Handle error (could show a Toast or Snackbar)
    }
}

// Helper function to check permission rationale
private fun shouldShowRequestPermissionRationale(context: Context, permission: String): Boolean {
    val activity = context as? androidx.activity.ComponentActivity
    return activity?.shouldShowRequestPermissionRationale(permission) ?: false
}

// ================= REMAINING COMPOSABLE FUNCTIONS =================
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
                        Text("Reset")
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
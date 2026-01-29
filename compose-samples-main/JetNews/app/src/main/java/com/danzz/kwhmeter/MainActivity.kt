package com.danzz.kwhmeter

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.text.font.FontStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

// ================= DATA CLASSES =================
data class BlinkRecord(
    val blinkNumber: Int,
    val timeSeconds: Double
)

data class PelangganData(
    val idPelanggan: String = "",
    val nama: String = "",
    val alamat: String = "",
    val fotoPaths: List<String> = emptyList(), // UBAH: dari String menjadi List<String>
    val timestamp: Long = System.currentTimeMillis()
)

data class CalculationResult(
    val p1: Double,
    val p2: Double,
    val error: Double,
    val status: String,
    val kelasMeter: Double,
    val mode: Int,
    val voltage: Double,
    val cosphi: Double,
    val konstanta: Double,
    val p1Source: String = "",
    val p2Source: String = ""
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
    val phaseT: String = "",
    val voltage: String = "220.0",
    val cosphi: String = "0.85",
    val konstanta: String = "1600"
)

data class RiwayatData(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val pelangganData: PelangganData,
    val mode: Int,
    val inputData: InputData,
    val calculationResult: CalculationResult
)

// NEW: Data untuk informasi pengguna (register data)
data class UserInfo(
    val id: String = UUID.randomUUID().toString(),
    val namaLengkap: String = "",
    val nip: String = "",
    val namaPerusahaan: String = "",
    val kedudukanPerusahaan: String = "", // Contoh: PLN UID/UP3/ULP
    val createdAt: Long = System.currentTimeMillis()
)

// ================= UTILITY FUNCTIONS =================
fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatFileTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun getModeText(mode: Int): String {
    return when (mode) {
        1 -> "MODE 1: P1=IMPULSE METER, P2=V×I×Cosφ"
        2 -> "MODE 2: P1=DISPLAY METER, P2=TANG kW"
        3 -> "MODE 3: P1=IMPULSE METER, P2=TANG kW"
        4 -> "MODE 4: P1=DISPLAY METER, P2=V×I×Cosφ"
        else -> "MODE TIDAK DIKETAHUI"
    }
}

fun getModeColor(mode: Int): Color {
    return Color(0xFFFF9800)
}

fun getModeIcon(mode: Int): ImageVector {
    return when (mode) {
        1 -> Icons.Default.Bolt
        2 -> Icons.Default.ElectricBolt
        3 -> Icons.Default.Merge
        4 -> Icons.Default.SwapHoriz
        else -> Icons.Default.Settings
    }
}

// ================= SIMPLE STORAGE MANAGER =================
object SimpleStorageManager {
    private const val RIWAYAT_LIST_KEY = "riwayat_list"
    private const val USER_INFO_KEY = "user_info"
    private const val IS_FIRST_TIME_KEY = "is_first_time"
    private val gson = Gson()
    
    // ===== RIWAYAT OPERATIONS =====
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
    
    // ===== USER INFO OPERATIONS =====
    fun saveUserInfo(context: Context, userInfo: UserInfo) {
        val sharedPref = context.getSharedPreferences("kwh_storage", Context.MODE_PRIVATE)
        val json = gson.toJson(userInfo)
        sharedPref.edit().putString(USER_INFO_KEY, json).apply()
        // Set bahwa user sudah register
        setIsFirstTime(context, false)
    }
    
    fun getUserInfo(context: Context): UserInfo? {
        val sharedPref = context.getSharedPreferences("kwh_storage", Context.MODE_PRIVATE)
        val json = sharedPref.getString(USER_INFO_KEY, null) ?: return null
        return try {
            gson.fromJson(json, UserInfo::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun updateUserInfo(context: Context, userInfo: UserInfo): Boolean {
        return try {
            saveUserInfo(context, userInfo)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun clearUserInfo(context: Context) {
        val sharedPref = context.getSharedPreferences("kwh_storage", Context.MODE_PRIVATE)
        sharedPref.edit().remove(USER_INFO_KEY).apply()
        setIsFirstTime(context, true)
    }
    
    // ===== FIRST TIME CHECK =====
    fun isFirstTime(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences("kwh_storage", Context.MODE_PRIVATE)
        return sharedPref.getBoolean(IS_FIRST_TIME_KEY, true)
    }
    
    private fun setIsFirstTime(context: Context, isFirstTime: Boolean) {
        val sharedPref = context.getSharedPreferences("kwh_storage", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean(IS_FIRST_TIME_KEY, isFirstTime).apply()
    }
}

// ================= VIEWMODEL =================
class KwhViewModel(application: Application) : AndroidViewModel(application) {
    
    // User info states
    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()
    
    // Register states
    private val _namaLengkap = MutableStateFlow("")
    val namaLengkap: StateFlow<String> = _namaLengkap.asStateFlow()
    
    private val _nip = MutableStateFlow("")
    val nip: StateFlow<String> = _nip.asStateFlow()
    
    private val _namaPerusahaan = MutableStateFlow("")
    val namaPerusahaan: StateFlow<String> = _namaPerusahaan.asStateFlow()
    
    private val _kedudukanPerusahaan = MutableStateFlow("")
    val kedudukanPerusahaan: StateFlow<String> = _kedudukanPerusahaan.asStateFlow()
    
    // Mode: 1 = Impulse Meter vs V×I×Cosφ, 2 = Display Meter vs Tang kW, 3 = Impulse Meter vs Tang kW, 4 = Display Meter vs V×I×Cosφ
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
    
    // Mode 1 & 4 inputs
    private val _arus = MutableStateFlow("5.0")
    val arus: StateFlow<String> = _arus.asStateFlow()
    
    private val _classMeter = MutableStateFlow("1.0")
    val classMeter: StateFlow<String> = _classMeter.asStateFlow()
    
    private val _voltage = MutableStateFlow("220.0")
    val voltage: StateFlow<String> = _voltage.asStateFlow()
    
    private val _cosphi = MutableStateFlow("0.85")
    val cosphi: StateFlow<String> = _cosphi.asStateFlow()
    
    private val _konstanta = MutableStateFlow("1600")
    val konstanta: StateFlow<String> = _konstanta.asStateFlow()
    
    // Mode 2 & 4 inputs
    private val _p1Input = MutableStateFlow("10.0")
    val p1Input: StateFlow<String> = _p1Input.asStateFlow()
    
    // Mode 2 & 3 inputs
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
    
    // Pelanggan data (optional)
    private val _idPelanggan = MutableStateFlow("")
    val idPelanggan: StateFlow<String> = _idPelanggan.asStateFlow()
    
    private val _namaPelanggan = MutableStateFlow("")
    val namaPelanggan: StateFlow<String> = _namaPelanggan.asStateFlow()
    
    private val _alamatPelanggan = MutableStateFlow("")
    val alamatPelanggan: StateFlow<String> = _alamatPelanggan.asStateFlow()
    
    // UBAH: dari single String menjadi List<String>
    private val _fotoPaths = MutableStateFlow<List<String>>(emptyList())
    val fotoPaths: StateFlow<List<String>> = _fotoPaths.asStateFlow()
    
    // Riwayat data from storage
    private val _riwayatList = MutableStateFlow<List<RiwayatData>>(emptyList())
    val riwayatList: StateFlow<List<RiwayatData>> = _riwayatList.asStateFlow()
    
    // Current riwayat ID untuk edit
    private val _currentRiwayatId = MutableStateFlow<String?>(null)
    val currentRiwayatId: StateFlow<String?> = _currentRiwayatId.asStateFlow()
    
    // Check if user needs to register
    private val _needsRegistration = MutableStateFlow(false)
    val needsRegistration: StateFlow<Boolean> = _needsRegistration.asStateFlow()
    
    init {
        loadInitialData()
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            
            // Check if user has registered
            val user = SimpleStorageManager.getUserInfo(context)
            _userInfo.value = user
            _needsRegistration.value = (user == null)
            
            // Load riwayat
            _riwayatList.value = SimpleStorageManager.getRiwayatList(context)
        }
    }
    
    // ===== USER REGISTRATION METHODS =====
    fun setNamaLengkap(value: String) { _namaLengkap.value = value }
    fun setNip(value: String) { _nip.value = value }
    fun setNamaPerusahaan(value: String) { _namaPerusahaan.value = value }
    fun setKedudukanPerusahaan(value: String) { _kedudukanPerusahaan.value = value }
    
    fun register() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            
            // Validasi
            if (_namaLengkap.value.isEmpty()) {
                Toast.makeText(context, "Nama Lengkap harus diisi", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            if (_nip.value.isEmpty()) {
                Toast.makeText(context, "NIP harus diisi", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            if (_namaPerusahaan.value.isEmpty()) {
                Toast.makeText(context, "Nama Perusahaan harus diisi", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            if (_kedudukanPerusahaan.value.isEmpty()) {
                Toast.makeText(context, "Kedudukan Perusahaan harus diisi", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val newUserInfo = UserInfo(
                namaLengkap = _namaLengkap.value,
                nip = _nip.value,
                namaPerusahaan = _namaPerusahaan.value,
                kedudukanPerusahaan = _kedudukanPerusahaan.value
            )
            
            SimpleStorageManager.saveUserInfo(context, newUserInfo)
            _userInfo.value = newUserInfo
            _needsRegistration.value = false
            
            // Reset form
            _namaLengkap.value = ""
            _nip.value = ""
            _namaPerusahaan.value = ""
            _kedudukanPerusahaan.value = ""
            
            Toast.makeText(context, "Registrasi berhasil!", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun updateUserInfo() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val currentUser = _userInfo.value ?: return@launch
            
            val updatedUser = currentUser.copy(
                namaLengkap = _namaLengkap.value,
                nip = _nip.value,
                namaPerusahaan = _namaPerusahaan.value,
                kedudukanPerusahaan = _kedudukanPerusahaan.value
            )
            
            val success = SimpleStorageManager.updateUserInfo(context, updatedUser)
            if (success) {
                _userInfo.value = updatedUser
                Toast.makeText(context, "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Gagal memperbarui profil", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            SimpleStorageManager.clearUserInfo(context)
            _userInfo.value = null
            _needsRegistration.value = true
            Toast.makeText(context, "Data pengguna dihapus", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Setters
    fun setMode(value: Int) {
        _mode.value = value
        if (value == 1 || value == 3) {
            resetMode1()
        }
    }
    
    fun setArus(value: String) { _arus.value = value }
    fun setClassMeter(value: String) { _classMeter.value = value }
    fun setVoltage(value: String) { _voltage.value = value }
    fun setCosphi(value: String) { _cosphi.value = value }
    fun setKonstanta(value: String) { _konstanta.value = value }
    fun setP1Input(value: String) { _p1Input.value = value }
    fun setPhaseR(value: String) { _phaseR.value = value }
    fun setPhaseS(value: String) { _phaseS.value = value }
    fun setPhaseT(value: String) { _phaseT.value = value }
    
    fun setIdPelanggan(value: String) { _idPelanggan.value = value }
    fun setNamaPelanggan(value: String) { _namaPelanggan.value = value }
    fun setAlamatPelanggan(value: String) { _alamatPelanggan.value = value }
    
    // UBAH: Method untuk multiple foto
    fun addFotoPath(value: String) {
        _fotoPaths.value = _fotoPaths.value + value
    }
    
    fun removeFotoPath(index: Int) {
        val newList = _fotoPaths.value.toMutableList()
        if (index in newList.indices) {
            newList.removeAt(index)
            _fotoPaths.value = newList
        }
    }
    
    fun clearFotoPaths() {
        _fotoPaths.value = emptyList()
    }
    
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
        val currentTime = System.currentTimeMillis() - _startTime.value
        val currentTimeSeconds = currentTime / 1000.0
        val newRecord = BlinkRecord(_blinkCount.value, currentTimeSeconds)
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
        val voltageValue = try { _voltage.value.toDouble() } catch (e: Exception) { 220.0 }
        val cosphiValue = try { _cosphi.value.toDouble() } catch (e: Exception) { 0.85 }
        val konstantaValue = try { _konstanta.value.toDouble() } catch (e: Exception) { 1600.0 }
        
        // Determine P1 and P2 based on mode
        val (p1, p1Source) = when (modeValue) {
            1 -> calculateP1FromMode1(konstantaValue) to "Impulse Meter"
            2 -> calculateP1FromMode2() to "Display Meter"
            3 -> calculateP1FromMode1(konstantaValue) to "Impulse Meter"
            4 -> calculateP1FromMode2() to "Display Meter"
            else -> 0.0 to "Tidak diketahui"
        }
        
        val (p2, p2Source) = when (modeValue) {
            1 -> calculateP2FromMode1(voltageValue, cosphiValue) to "V × I × Cosφ"
            2 -> calculateP2FromMode2() to "Tang kW"
            3 -> calculateP2FromMode2() to "Tang kW"
            4 -> calculateP2FromMode1(voltageValue, cosphiValue) to "V × I × Cosφ"
            else -> 0.0 to "Tidak diketahui"
        }
        
        val kelasMeterValue = try { _classMeter.value.toDouble() } catch (e: Exception) { 1.0 }
        val error = if (p2 != 0.0) ((p1 - p2) / p2) * 100 else 0.0
        val status = if (Math.abs(error) <= kelasMeterValue) "DI DALAM KELAS METER" else "DI LUAR KELAS METER"
        
        return CalculationResult(
            p1 = p1,
            p2 = p2,
            error = error,
            status = status,
            kelasMeter = kelasMeterValue,
            mode = modeValue,
            voltage = voltageValue,
            cosphi = cosphiValue,
            konstanta = konstantaValue,
            p1Source = p1Source,
            p2Source = p2Source
        )
    }
    
    private fun calculateP1FromMode1(konstanta: Double): Double {
        val selectedIndex = _selectedBlinkIndex.value
        if (selectedIndex != null && selectedIndex < _blinkRecords.value.size) {
            val record = _blinkRecords.value[selectedIndex]
            if (record.timeSeconds > 0 && konstanta > 0) {
                return (3600.0 * record.blinkNumber) / (record.timeSeconds * konstanta)
            }
        }
        return 0.0
    }
    
    private fun calculateP1FromMode2(): Double {
        return try { _p1Input.value.toDouble() } catch (e: Exception) { 0.0 }
    }
    
    private fun calculateP2FromMode1(voltage: Double, cosphi: Double): Double {
        return try {
            if (_arus.value.isNotEmpty()) {
                (voltage * _arus.value.toDouble() * cosphi) / 1000
            } else 0.0
        } catch (e: Exception) { 0.0 }
    }
    
    private fun calculateP2FromMode2(): Double {
        return try {
            _phaseR.value.toDouble() + _phaseS.value.toDouble() + _phaseT.value.toDouble()
        } catch (e: Exception) { 0.0 }
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
                        fotoPaths = _fotoPaths.value // Gunakan List<String>
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
                        phaseT = _phaseT.value,
                        voltage = _voltage.value,
                        cosphi = _cosphi.value,
                        konstanta = _konstanta.value
                    ),
                    calculationResult = calculationResult
                )
                
                SimpleStorageManager.saveRiwayat(context, riwayatData)
                loadRiwayatFromStorage()
                resetForm()
                Toast.makeText(context, "Data berhasil disimpan", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(getApplication(), "Gagal menyimpan data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadRiwayatFromStorage() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val list = SimpleStorageManager.getRiwayatList(context)
            _riwayatList.value = list
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
                _fotoPaths.value = it.pelangganData.fotoPaths // Load List<String>
                _mode.value = it.mode
                
                // Load data berdasarkan mode
                _arus.value = it.inputData.arus
                _classMeter.value = it.inputData.classMeter
                _blinkCount.value = it.inputData.blinkCount
                _elapsedTime.value = it.inputData.elapsedTime
                _selectedBlinkIndex.value = it.inputData.selectedBlinkIndex
                _voltage.value = it.inputData.voltage
                _cosphi.value = it.inputData.cosphi
                _konstanta.value = it.inputData.konstanta
                _p1Input.value = it.inputData.p1Input
                _phaseR.value = it.inputData.phaseR
                _phaseS.value = it.inputData.phaseS
                _phaseT.value = it.inputData.phaseT
                
                // Reconstruct blink records untuk mode yang memerlukan
                if (it.mode == 1 || it.mode == 3) {
                    val records = mutableListOf<BlinkRecord>()
                    for (i in 1..it.inputData.blinkCount) {
                        val avgTimePerBlink = it.inputData.elapsedTime.toDouble() / (1000.0 * it.inputData.blinkCount)
                        val timeAtBlink = avgTimePerBlink * i
                        records.add(BlinkRecord(i, timeAtBlink))
                    }
                    _blinkRecords.value = records
                }
            }
        }
    }
    
    fun deleteRiwayat(id: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            SimpleStorageManager.deleteRiwayatById(context, id)
            loadRiwayatFromStorage()
            Toast.makeText(context, "Data berhasil dihapus", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun clearRiwayat() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            SimpleStorageManager.deleteAllRiwayat(context)
            _riwayatList.value = emptyList()
            Toast.makeText(context, "Semua data berhasil dihapus", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun resetForm() {
        _idPelanggan.value = ""
        _namaPelanggan.value = ""
        _alamatPelanggan.value = ""
        _fotoPaths.value = emptyList() // Reset foto paths
        _currentRiwayatId.value = null
        
        when (_mode.value) {
            1 -> {
                resetMode1()
                _arus.value = "5.0"
                _classMeter.value = "1.0"
                _voltage.value = "220.0"
                _cosphi.value = "0.85"
                _konstanta.value = "1600"
            }
            2 -> {
                _p1Input.value = "10.0"
                _phaseR.value = "3.5"
                _phaseS.value = "3.5"
                _phaseT.value = "3.0"
                _classMeter.value = "1.0"
            }
            3 -> {
                resetMode1()
                _phaseR.value = "3.5"
                _phaseS.value = "3.5"
                _phaseT.value = "3.0"
                _classMeter.value = "1.0"
                _konstanta.value = "1600"
            }
            4 -> {
                _p1Input.value = "10.0"
                _arus.value = "5.0"
                _classMeter.value = "1.0"
                _voltage.value = "220.0"
                _cosphi.value = "0.85"
                _konstanta.value = "1600"
            }
        }
    }
    
    fun isFormValidForSave(): Boolean {
        return isCalculationValid()
    }
    
    private fun isCalculationValid(): Boolean {
        return when (_mode.value) {
            1 -> _selectedBlinkIndex.value != null && 
                  _arus.value.isNotEmpty() && 
                  _arus.value.toDoubleOrNull() != null &&
                  _voltage.value.isNotEmpty() &&
                  _voltage.value.toDoubleOrNull() != null &&
                  _cosphi.value.isNotEmpty() &&
                  _cosphi.value.toDoubleOrNull() != null &&
                  _konstanta.value.isNotEmpty() &&
                  _konstanta.value.toDoubleOrNull() != null
            2 -> _p1Input.value.isNotEmpty() && 
                  _p1Input.value.toDoubleOrNull() != null && 
                  _phaseR.value.isNotEmpty() && 
                  _phaseR.value.toDoubleOrNull() != null &&
                  _phaseS.value.isNotEmpty() && 
                  _phaseS.value.toDoubleOrNull() != null &&
                  _phaseT.value.isNotEmpty() &&
                  _phaseT.value.toDoubleOrNull() != null
            3 -> _selectedBlinkIndex.value != null &&
                  _phaseR.value.isNotEmpty() &&
                  _phaseR.value.toDoubleOrNull() != null &&
                  _phaseS.value.isNotEmpty() &&
                  _phaseS.value.toDoubleOrNull() != null &&
                  _phaseT.value.isNotEmpty() &&
                  _phaseT.value.toDoubleOrNull() != null &&
                  _konstanta.value.isNotEmpty() &&
                  _konstanta.value.toDoubleOrNull() != null
            4 -> _p1Input.value.isNotEmpty() &&
                  _p1Input.value.toDoubleOrNull() != null &&
                  _arus.value.isNotEmpty() &&
                  _arus.value.toDoubleOrNull() != null &&
                  _voltage.value.isNotEmpty() &&
                  _voltage.value.toDoubleOrNull() != null &&
                  _cosphi.value.isNotEmpty() &&
                  _cosphi.value.toDoubleOrNull() != null
            else -> false
        }
    }
}

// ================= UTILITY FUNCTIONS =================
fun createImageFile(context: Context): File? {
    return try {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        
        if (!storageDir?.exists()!!) {
            storageDir.mkdirs()
        }
        
        File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
    } catch (ex: IOException) {
        ex.printStackTrace()
        null
    }
}

// ================= PDF EXPORTER (REAL PDF) - 2 KOLOM LAYOUT =================
object PdfExporter {
    
    // Konstanta untuk layout
    private const val PAGE_WIDTH = 595f // A4 width in points
    private const val PAGE_HEIGHT = 842f // A4 height in points
    private const val MARGIN = 25f
    private const val COLUMN_GAP = 12f
    private const val SECTION_SPACING = 18f
    private const val CARD_HEADER_HEIGHT = 22f
    private const val LABEL_WIDTH = 85f  // Lebar fixed untuk label
    
    // Warna tema - menggunakan android.graphics.Color untuk PDF
    private val COLOR_PRIMARY = android.graphics.Color.parseColor("#1B5E20") // Dark Green PLN
    private val COLOR_SECONDARY = android.graphics.Color.parseColor("#388E3C") // Green
    private val COLOR_ACCENT = android.graphics.Color.parseColor("#1976D2") // Blue
    private val COLOR_SUCCESS = android.graphics.Color.parseColor("#4CAF50") // Green success
    private val COLOR_ERROR = android.graphics.Color.parseColor("#F44336") // Red error
    private val COLOR_TEXT = android.graphics.Color.parseColor("#212121") // Dark grey
    private val COLOR_TEXT_SECONDARY = android.graphics.Color.parseColor("#757575") // Grey
    private val COLOR_BORDER = android.graphics.Color.parseColor("#E0E0E0") // Light grey
    private val COLOR_BACKGROUND = android.graphics.Color.parseColor("#F5F5F5") // Light background
    
    fun exportRiwayatToPdf(
        context: Context,
        riwayat: RiwayatData,
        userInfo: UserInfo? = null,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val pdfFile = createPdfFile(context, riwayat)
            createPdfDocument(context, riwayat, pdfFile, userInfo)
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            
            onSuccess(uri)
            
        } catch (e: Exception) {
            e.printStackTrace()
            onError("Gagal membuat PDF: ${e.message}")
        }
    }
    
    private fun createPdfFile(context: Context, riwayat: RiwayatData): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(riwayat.timestamp))
        val pelangganName = if (riwayat.pelangganData.nama.isNotEmpty()) 
            "_${riwayat.pelangganData.nama.replace(" ", "_")}" 
        else "_Data"
        val fileName = "Laporan_KWH${pelangganName}_${timestamp}.pdf"
        
        return File(getReportsDirectory(context), fileName)
    }
    
    private fun getReportsDirectory(context: Context): File {
        val directory = File(context.getExternalFilesDir(null), "Reports")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }
    
    fun shareFile(context: Context, uri: Uri, fileName: String) {
        try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Laporan Pengujian KWH Meter: $fileName")
                putExtra(Intent.EXTRA_TEXT, """
                    Laporan Pengujian KWH Meter
                    
                    Isi laporan:
                    ✓ Data Pelanggan
                    ✓ Parameter Pengujian
                    ✓ Data Input
                    ✓ Hasil Pengukuran
                    ✓ Dokumentasi Foto
                    ✓ Tanda Tangan Pelaksana
                    
                    © ${SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())} - KWH Meter Test
                """.trimIndent())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Laporan PDF"))
            
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createPdfDocument(context: Context, riwayat: RiwayatData, pdfFile: File, userInfo: UserInfo?) {
        val document = android.graphics.pdf.PdfDocument()
        
        // Buat halaman A4 Portrait
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
            PAGE_WIDTH.toInt(), 
            PAGE_HEIGHT.toInt(), 
            1
        ).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        
        // Set background putih
        val backgroundPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH, PAGE_HEIGHT, backgroundPaint)
        
        var currentY = MARGIN
        
        // ===== HEADER UTAMA =====
        currentY = drawMainHeader(canvas, riwayat, currentY)
        currentY += SECTION_SPACING
        
        // ===== LAYOUT 2 KOLOM =====
        val columnWidth = (PAGE_WIDTH - 2 * MARGIN - COLUMN_GAP) / 2
        
        // Kolom Kiri
        var leftColumnY = currentY
        leftColumnY = drawCustomerDataSection(canvas, riwayat, MARGIN, leftColumnY, columnWidth)
        leftColumnY += SECTION_SPACING
        
        leftColumnY = drawEnergyCalculationSection(canvas, riwayat, MARGIN, leftColumnY, columnWidth)
        leftColumnY += SECTION_SPACING
        
        leftColumnY = drawParameterSection(canvas, riwayat, MARGIN, leftColumnY, columnWidth)
        
        // Kolom Kanan
        var rightColumnY = currentY
        rightColumnY = drawResultsSection(canvas, riwayat, MARGIN + columnWidth + COLUMN_GAP, rightColumnY, columnWidth)
        rightColumnY += SECTION_SPACING
        
        rightColumnY = drawDocumentationSection(context, canvas, riwayat, MARGIN + columnWidth + COLUMN_GAP, rightColumnY, columnWidth)
        rightColumnY += SECTION_SPACING
        
        rightColumnY = drawTesterInfoSection(canvas, userInfo, MARGIN + columnWidth + COLUMN_GAP, rightColumnY, columnWidth)
        
        // ===== FOOTER =====
        drawFooter(canvas, riwayat, currentY)
        
        // ===== WATERMARK =====
        drawWatermark(canvas)
        
        // Selesaikan halaman
        document.finishPage(page)
        
        try {
            val fos = FileOutputStream(pdfFile)
            document.writeTo(fos)
            document.close()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
    
    private fun drawMainHeader(canvas: android.graphics.Canvas, riwayat: RiwayatData, startY: Float): Float {
        var yPos = startY
        
        // Background header
        val headerPaint = android.graphics.Paint().apply {
            color = COLOR_PRIMARY
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(0f, yPos, PAGE_WIDTH, yPos + 65f, headerPaint)
        
        // Judul utama
        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 18f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        }
        canvas.drawText("LAPORAN PENGUJIAN ERROR kWh METER", PAGE_WIDTH / 2, yPos + 25f, titlePaint)
        
        // Sub judul
        val subtitlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 14f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("PLN UP3 PONDOK GEDE", PAGE_WIDTH / 2, yPos + 45f, subtitlePaint)
        
        return yPos + 70f
    }
    
    private fun drawCustomerDataSection(canvas: android.graphics.Canvas, riwayat: RiwayatData, x: Float, y: Float, width: Float): Float {
        val sectionHeight = 100f
        drawCard(canvas, x, y, width, sectionHeight, "DATA PELANGGAN", COLOR_PRIMARY)
        
        var contentY = y + CARD_HEADER_HEIGHT + 16f
        
        val labelPaint = android.graphics.Paint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 10f
        }
        
        val valuePaint = android.graphics.Paint().apply {
            color = COLOR_TEXT
            textSize = 11f
            isFakeBoldText = true
        }
        
        // Nama Pelanggan
        canvas.drawText("Nama", x + 15f, contentY, labelPaint)
        val nama = riwayat.pelangganData.nama.ifEmpty { "-" }
        canvas.drawText(
            nama, 
            x + LABEL_WIDTH, 
            contentY, 
            valuePaint
        )
        contentY += 18f
        
        // ID Pelanggan
        canvas.drawText("ID Pelanggan", x + 15f, contentY, labelPaint)
        canvas.drawText(
            riwayat.pelangganData.idPelanggan.ifEmpty { "-" }, 
            x + LABEL_WIDTH, 
            contentY, 
            valuePaint
        )
        contentY += 18f
        
        // Kelas Meter
        canvas.drawText("Kelas Meter", x + 15f, contentY, labelPaint)
        canvas.drawText(
            "${riwayat.calculationResult.kelasMeter}%",
            x + LABEL_WIDTH,
            contentY,
            valuePaint
        )
        
        return y + sectionHeight
    }
    
    private fun drawEnergyCalculationSection(canvas: android.graphics.Canvas, riwayat: RiwayatData, x: Float, y: Float, width: Float): Float {
        val sectionHeight = 95f
        drawCard(canvas, x, y, width, sectionHeight, "PERHITUNGAN ENERGI", COLOR_ACCENT)
        
        var contentY = y + CARD_HEADER_HEIGHT + 18f
        
        val labelPaint = android.graphics.Paint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 10f
        }
        
        val valuePaint = android.graphics.Paint().apply {
            color = COLOR_TEXT
            textSize = 12f
            isFakeBoldText = true
        }
        
        // P1
        canvas.drawText("P1 =", x + 15f, contentY, labelPaint)
        canvas.drawText(
            "${String.format("%.3f", riwayat.calculationResult.p1)} kW",
            x + 50f,
            contentY,
            valuePaint
        )
        contentY += 20f
        
        // P2
        canvas.drawText("P2 =", x + 15f, contentY, labelPaint)
        canvas.drawText(
            "${String.format("%.3f", riwayat.calculationResult.p2)} kW",
            x + 50f,
            contentY,
            valuePaint
        )
        
        // Mode dihapus sesuai permintaan (tidak ditampilkan)
        // Kosongkan bagian mode
        
        return y + sectionHeight
    }
    
    private fun drawParameterSection(canvas: android.graphics.Canvas, riwayat: RiwayatData, x: Float, y: Float, width: Float): Float {
        val hasPhaseData = riwayat.mode == 2 || riwayat.mode == 3
        val sectionHeight = if (hasPhaseData) 130f else 130f // Disesuaikan karena phase dihapus
        drawCard(canvas, x, y, width, sectionHeight, "PARAMETER PENGUJIAN", COLOR_SECONDARY)
        
        var contentY = y + CARD_HEADER_HEIGHT + 16f
        
        val labelPaint = android.graphics.Paint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 10f
        }
        
        val valuePaint = android.graphics.Paint().apply {
            color = COLOR_TEXT
            textSize = 11f
            isFakeBoldText = true
        }
        
        val paramLeftX = x + 15f
        val paramRightX = x + width / 2 + 10f
        val valueOffset = 75f // Jarak dari label ke value
        
        var paramY = contentY
        
        // Kolom Kiri
        // Tegangan
        canvas.drawText("Tegangan", paramLeftX, paramY, labelPaint)
        canvas.drawText("${riwayat.inputData.voltage} V", paramLeftX + valueOffset, paramY, valuePaint)
        paramY += 16f
        
        // Arus
        canvas.drawText("Arus", paramLeftX, paramY, labelPaint)
        canvas.drawText("${if (riwayat.inputData.arus.isNotEmpty()) riwayat.inputData.arus else "0.0"} A", 
                       paramLeftX + valueOffset, paramY, valuePaint)
        paramY += 16f
        
        // Cosphi
        canvas.drawText("Cosphi", paramLeftX, paramY, labelPaint)
        canvas.drawText(riwayat.inputData.cosphi, paramLeftX + valueOffset, paramY, valuePaint)
        paramY += 16f
        
        // Konstanta
        canvas.drawText("Konstanta", paramLeftX, paramY, labelPaint)
        canvas.drawText(riwayat.inputData.konstanta, paramLeftX + valueOffset, paramY, valuePaint)
        
        // Kolom Kanan
        paramY = contentY
        
        // Data berdasarkan mode
        when (riwayat.mode) {
            1, 3 -> {
                canvas.drawText("Impulse", paramRightX, paramY, labelPaint)
                canvas.drawText("${riwayat.inputData.blinkCount} kali", paramRightX + valueOffset, paramY, valuePaint)
                paramY += 16f
                
                canvas.drawText("Waktu", paramRightX, paramY, labelPaint)
                canvas.drawText("${String.format("%.1f", riwayat.inputData.elapsedTime / 1000.0)} detik", 
                               paramRightX + valueOffset, paramY, valuePaint)
                paramY += 16f
                
                canvas.drawText("Kelas", paramRightX, paramY, labelPaint)
                canvas.drawText("${riwayat.calculationResult.kelasMeter}%", paramRightX + valueOffset, paramY, valuePaint)
            }
            2, 4 -> {
                canvas.drawText("P1 Display", paramRightX, paramY, labelPaint)
                canvas.drawText("${riwayat.inputData.p1Input} kW", paramRightX + valueOffset, paramY, valuePaint)
                paramY += 16f
                
                canvas.drawText("Kelas", paramRightX, paramY, labelPaint)
                canvas.drawText("${riwayat.calculationResult.kelasMeter}%", paramRightX + valueOffset, paramY, valuePaint)
                paramY += 16f
                
                if (riwayat.mode == 4) {
                    canvas.drawText("Arus", paramRightX, paramY, labelPaint)
                    canvas.drawText("${if (riwayat.inputData.arus.isNotEmpty()) riwayat.inputData.arus else "0.0"} A", 
                                   paramRightX + valueOffset, paramY, valuePaint)
                }
            }
        }
        
        // Data phase dihilangkan sesuai permintaan (tidak ditampilkan)
        // Kosongkan bagian phase
        
        return y + sectionHeight
    }
    
   private fun drawResultsSection(canvas: android.graphics.Canvas, riwayat: RiwayatData, x: Float, y: Float, width: Float): Float {
    val sectionHeight = 120f
    
    drawCard(canvas, x, y, width, sectionHeight, "HASIL PERHITUNGAN", COLOR_PRIMARY)
    
    var contentY = y + CARD_HEADER_HEIGHT + 10f
    
    // RUMUS ERROR DI POJOK KIRI ATAS (kecil)
    val formulaPaint = android.graphics.Paint().apply {
        color = COLOR_TEXT_SECONDARY
        textSize = 8f
    }
    
    canvas.drawText(
        "ε = (P1 - P2) / P2 × 100%",
        x + 15f,
        contentY,
        formulaPaint
    )
    
    contentY += 15f // Beri jarak setelah rumus
    
    // Hasil Error
    val errorValue = riwayat.calculationResult.error
    val isInClass = Math.abs(errorValue) <= riwayat.calculationResult.kelasMeter
    val statusColor = if (isInClass) COLOR_SUCCESS else COLOR_ERROR
    
    // PERSEN ERROR DI ATAS BOX (tengah)
    val errorText = "${String.format("%.2f", errorValue)}%"
    val errorPaint = android.graphics.Paint().apply {
        color = statusColor
        textSize = 20f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    
    canvas.drawText(
        errorText,
        x + width / 2,
        contentY + 10f,
        errorPaint
    )
    
    contentY += 25f // Beri jarak antara persen dan box
    
    // Buat box untuk status
    val boxWidth = width - 30f
    val boxHeight = 35f // Lebih pendek karena hanya status
    val boxX = x + 15f
    val boxY = contentY - 5f
    
    // Box background
    val statusBoxPaint = android.graphics.Paint().apply {
        color = colorWithAlpha(statusColor, 10)
        style = android.graphics.Paint.Style.FILL
    }
    
    val statusBorderPaint = android.graphics.Paint().apply {
        color = statusColor
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    
    // Box
    canvas.drawRoundRect(
        boxX, boxY,
        boxX + boxWidth, boxY + boxHeight,
        8f, 8f, statusBoxPaint
    )
    
    // Border
    canvas.drawRoundRect(
        boxX, boxY,
        boxX + boxWidth, boxY + boxHeight,
        8f, 8f, statusBorderPaint
    )
    
    // STATUS DI DALAM BOX (tengah)
    val statusText = if (isInClass) 
        "DI DALAM KELAS METER" 
    else 
        "DI LUAR KELAS METER"
    
    val statusTextPaint = android.graphics.Paint().apply {
        color = statusColor
        textSize = 14f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    
    canvas.drawText(
        statusText,
        boxX + boxWidth / 2,
        boxY + 23f, // Tengah secara vertikal
        statusTextPaint
    )
    
    return y + sectionHeight
}
    

    private fun drawDocumentationSection(context: Context, canvas: android.graphics.Canvas, riwayat: RiwayatData, x: Float, y: Float, width: Float): Float {
    val hasPhotos = riwayat.pelangganData.fotoPaths.isNotEmpty()
    val sectionHeight = if (hasPhotos) 170f else 80f // Kurangi tinggi karena horizontal
    
    drawCard(canvas, x, y, width, sectionHeight, "DOKUMENTASI FOTO", COLOR_SECONDARY)
    
    var contentY = y + CARD_HEADER_HEIGHT + 15f
    
    if (hasPhotos) {
        // PERBAIKAN: Foto disusun horizontal (sejajar)
        val maxPhotosToShow = 2 // Maksimal 2 foto sejajar
        val photosToShow = riwayat.pelangganData.fotoPaths.take(maxPhotosToShow)
        
        val photoSpacing = 10f // Jarak antar foto
        val totalPhotoWidth = width - 40f // Total lebar untuk semua foto
        val photoWidth = (totalPhotoWidth - (photosToShow.size - 1) * photoSpacing) / photosToShow.size
        val photoHeight = 100f // Tinggi foto seragam
        
        var photoX = x + 20f // Mulai dari 20px dari kiri card
        
        for ((index, photoPath) in photosToShow.withIndex()) {
            try {
                val file = File(photoPath)
                if (file.exists()) {
                    // Decode foto
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                    
                    // Hitung ukuran untuk memenuhi container
                    val widthScale = photoWidth / options.outWidth
                    val heightScale = photoHeight / options.outHeight
                    val scale = minOf(widthScale, heightScale)
                    
                    val targetWidth = options.outWidth * scale
                    val targetHeight = options.outHeight * scale
                    
                    // Decode bitmap dengan ukuran yang tepat
                    options.inJustDecodeBounds = false
                    options.inSampleSize = calculateInSampleSize(options, targetWidth.toInt(), targetHeight.toInt())
                    
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                    if (bitmap != null) {
                        val finalBitmap = android.graphics.Bitmap.createScaledBitmap(
                            bitmap, 
                            targetWidth.toInt(), 
                            targetHeight.toInt(), 
                            true
                        )
                        
                        // Hitung posisi agar foto berada di tengah secara vertikal dalam kotak
                        val imageX = photoX + (photoWidth - targetWidth) / 2
                        val imageY = contentY + (photoHeight - targetHeight) / 2
                        
                        // Draw background untuk foto
                        val borderPaint = android.graphics.Paint().apply {
                            color = COLOR_BACKGROUND
                            style = android.graphics.Paint.Style.FILL
                        }
                        
                        canvas.drawRect(
                            photoX - 2f, contentY - 2f,
                            photoX + photoWidth + 2f, contentY + photoHeight + 2f,
                            borderPaint
                        )
                        
                        // Draw foto
                        canvas.drawBitmap(finalBitmap, imageX, imageY, null)
                        
                        // Draw frame border
                        val framePaint = android.graphics.Paint().apply {
                            color = COLOR_BORDER
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 0.8f
                        }
                        canvas.drawRect(
                            photoX - 2f, contentY - 2f,
                            photoX + photoWidth + 2f, contentY + photoHeight + 2f,
                            framePaint
                        )
                        
                        // Tambah nomor foto kecil di pojok kiri atas
                        val numberPaint = android.graphics.Paint().apply {
                            // PERBAIKAN: Gunakan android.graphics.Color
                            color = android.graphics.Color.WHITE
                            textSize = 10f
                            isFakeBoldText = true
                        }
                        
                        // Background untuk nomor
                        val numberBgPaint = android.graphics.Paint().apply {
                            // PERBAIKAN: Gunakan android.graphics.Color
                            color = android.graphics.Color.BLACK
                            style = android.graphics.Paint.Style.FILL
                        }
                        
                        canvas.drawCircle(photoX + 8f, contentY + 8f, 10f, numberBgPaint)
                        canvas.drawText(
                            "${index + 1}",
                            photoX + 5f,
                            contentY + 13f,
                            numberPaint
                        )
                    }
                }
            } catch (e: Exception) {
                // Jika gagal load foto, gambar placeholder
                val placeholderPaint = android.graphics.Paint().apply {
                    // PERBAIKAN: Gunakan android.graphics.Color
                    color = android.graphics.Color.LTGRAY
                    style = android.graphics.Paint.Style.FILL
                }
                
                canvas.drawRect(
                    photoX, contentY,
                    photoX + photoWidth, contentY + photoHeight,
                    placeholderPaint
                )
                
                val errorPaint = android.graphics.Paint().apply {
                    // PERBAIKAN: Gunakan android.graphics.Color
                    color = android.graphics.Color.DKGRAY
                    textSize = 10f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                
                canvas.drawText(
                    "Foto ${index + 1}",
                    photoX + photoWidth / 2,
                    contentY + photoHeight / 2,
                    errorPaint
                )
            }
            
            // Geser ke kanan untuk foto berikutnya
            photoX += photoWidth + photoSpacing
        }
        
        contentY += photoHeight + 10f
        
        // Jika ada lebih dari 2 foto, tampilkan informasi
        if (riwayat.pelangganData.fotoPaths.size > maxPhotosToShow) {
            val remaining = riwayat.pelangganData.fotoPaths.size - maxPhotosToShow
            val infoPaint = android.graphics.Paint().apply {
                color = COLOR_TEXT_SECONDARY
                textSize = 9f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText(
                "+${remaining} foto lainnya",
                x + width / 2,
                contentY + 5f,
                infoPaint
            )
            contentY += 15f
        }
    } else {
        drawNoPhotoMessage(canvas, x, contentY, width)
        contentY += 60f
    }
    
    return y + sectionHeight
}
    
    private fun drawNoPhotoMessage(canvas: android.graphics.Canvas, x: Float, y: Float, width: Float, message: String = "Tidak ada dokumentasi foto") {
        val iconPaint = android.graphics.Paint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        
        val textPaint = android.graphics.Paint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 10f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        
        // Icon kamera
        canvas.drawText("📷", x + width / 2, y + 30f, iconPaint)
        
        // Pesan
        canvas.drawText(message, x + width / 2, y + 55f, textPaint)
    }
    
    private fun drawTesterInfoSection(canvas: android.graphics.Canvas, userInfo: UserInfo?, x: Float, y: Float, width: Float): Float {
        val sectionHeight = 105f
        drawCard(canvas, x, y, width, sectionHeight, "PELAKSANA PENGUJIAN", COLOR_ACCENT)
        
        var contentY = y + CARD_HEADER_HEIGHT + 16f
        
        val labelPaint = android.graphics.Paint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 10f
        }
        
        val valuePaint = android.graphics.Paint().apply {
            color = COLOR_TEXT
            textSize = 11f
            isFakeBoldText = true
        }
        
        // Nama
        canvas.drawText("Nama", x + 15f, contentY, labelPaint)
        canvas.drawText(
            userInfo?.namaLengkap ?: "__________________",
            x + LABEL_WIDTH,
            contentY,
            valuePaint
        )
        contentY += 18f
        
        // NIP
        canvas.drawText("NIP", x + 15f, contentY, labelPaint)
        canvas.drawText(
            userInfo?.nip ?: "__________________",
            x + LABEL_WIDTH,
            contentY,
            valuePaint
        )
        contentY += 18f
        
        // Jabatan
        canvas.drawText("Jabatan", x + 15f, contentY, labelPaint)
        canvas.drawText(
            "Petugas pelaksana P2TL",
            x + LABEL_WIDTH,
            contentY,
            valuePaint
        )
        contentY += 22f
        
        // Garis tanda tangan
        val linePaint = android.graphics.Paint().apply {
            color = COLOR_BORDER
            strokeWidth = 1f
        }
        val lineLength = 120f
        val lineX = x + width - 15f - lineLength
        canvas.drawLine(lineX, contentY, lineX + lineLength, contentY, linePaint)
        
        // Teks tanda tangan
        val signaturePaint = android.graphics.Paint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 9f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText(
            "Tanda Tangan",
            lineX + lineLength / 2,
            contentY + 12f,
            signaturePaint
        )
        
        return y + sectionHeight
    }
    
    private fun drawFooter(canvas: android.graphics.Canvas, riwayat: RiwayatData, startY: Float) {
        val yPos = PAGE_HEIGHT - 20f
        
        val linePaint = android.graphics.Paint().apply {
            color = COLOR_BORDER
            strokeWidth = 0.5f
        }
        canvas.drawLine(MARGIN, yPos - 15f, PAGE_WIDTH - MARGIN, yPos - 15f, linePaint)
        
        val textPaint = android.graphics.Paint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 8f
        }
        
        // ID Laporan kiri
        canvas.drawText("ID: ${riwayat.id.take(8).uppercase()}", MARGIN, yPos - 5f, textPaint)
        
        // Tanggal cetak tengah
        val printDate = "Dicetak: ${formatTimestamp(System.currentTimeMillis())}"
        canvas.drawText(printDate, (PAGE_WIDTH - textPaint.measureText(printDate)) / 2, yPos - 5f, textPaint)
        
        // Halaman kanan
        val pageText = "Halaman 1/1"
        canvas.drawText(pageText, PAGE_WIDTH - MARGIN - textPaint.measureText(pageText), yPos - 5f, textPaint)
    }
    
    private fun drawWatermark(canvas: android.graphics.Canvas) {
        val watermarkPaint = android.graphics.Paint().apply {
            color = colorWithAlpha(COLOR_PRIMARY, 5)
            textSize = 100f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        
        canvas.save()
        canvas.rotate(-45f, PAGE_WIDTH / 2, PAGE_HEIGHT / 2)
        
        val watermark = "PLN"
        canvas.drawText(
            watermark,
            PAGE_WIDTH / 2,
            PAGE_HEIGHT / 2,
            watermarkPaint
        )
        
        canvas.restore()
    }
    
    private fun drawCard(
        canvas: android.graphics.Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        title: String,
        color: Int
    ) {
        val paint = android.graphics.Paint()
        
        // Card background
        paint.color = android.graphics.Color.WHITE
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRoundRect(
            x, y, 
            x + width, y + height,
            6f, 6f, paint
        )
        
        // Card border
        paint.color = COLOR_BORDER
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        canvas.drawRoundRect(
            x, y, 
            x + width, y + height,
            6f, 6f, paint
        )
        
        // Card header
        paint.color = color
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRoundRect(
            x, y, 
            x + width, y + CARD_HEADER_HEIGHT,
            6f, 6f, paint
        )
        
        // Title
        paint.color = android.graphics.Color.WHITE
        paint.style = android.graphics.Paint.Style.FILL
        paint.textSize = 10.5f
        paint.isFakeBoldText = true
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        
        canvas.drawText(
            title.uppercase(),
            x + width / 2,
            y + 15f,
            paint
        )
    }
    
    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
    
    private fun colorWithAlpha(color: Int, alpha: Int): Int {
        return android.graphics.Color.argb(
            alpha,
            android.graphics.Color.red(color),
            android.graphics.Color.green(color),
            android.graphics.Color.blue(color)
        )
    }
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

// ================= MAIN APP =================
@OptIn(ExperimentalFoundationApi::class)
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
    
    val needsRegistration by viewModel.needsRegistration.collectAsState()
    val userInfo by viewModel.userInfo.collectAsState()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF5F5F5)
    ) {
        if (needsRegistration) {
            RegisterScreen(viewModel)
        } else {
            MainScreen(viewModel, userInfo)
        }
    }
}

// ================= REGISTER SCREEN =================
@Composable
fun RegisterScreen(viewModel: KwhViewModel) {
    val namaLengkap by viewModel.namaLengkap.collectAsState()
    val nip by viewModel.nip.collectAsState()
    val namaPerusahaan by viewModel.namaPerusahaan.collectAsState()
    val kedudukanPerusahaan by viewModel.kedudukanPerusahaan.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF9800)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ElectricBolt,
                    contentDescription = "KWH Meter",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "KWH Meter Test",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
            Text(
                "Pengujian Akurasi Meter Listrik - PLN",
                fontSize = 14.sp,
                color = Color(0xFF6C757D)
            )
        }
        
        // Information Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F1FF)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Pendaftaran Wajib",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF0D6EFD)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Isi data diri Anda untuk menggunakan aplikasi KWH Meter Test. Data ini akan digunakan untuk laporan pengujian.",
                    fontSize = 14.sp,
                    color = Color(0xFF6C757D),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // REGISTER FORM
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Data Diri Pengguna",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Wajib diisi sebelum menggunakan aplikasi",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = namaLengkap,
                    onValueChange = { viewModel.setNamaLengkap(it) },
                    label = { Text("Nama Lengkap *") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    singleLine = true
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = nip,
                    onValueChange = { viewModel.setNip(it) },
                    label = { Text("NIP *") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Badge, contentDescription = null)
                    },
                    singleLine = true
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = namaPerusahaan,
                    onValueChange = { viewModel.setNamaPerusahaan(it) },
                    label = { Text("Nama Perusahaan *") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Business, contentDescription = null)
                    },
                    singleLine = true
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = kedudukanPerusahaan,
                    onValueChange = { viewModel.setKedudukanPerusahaan(it) },
                    label = { Text("Kedudukan Perusahaan *") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                    },
                    placeholder = { Text("Contoh: PLN UID/UP3/ULP") },
                    singleLine = true
                )
                
                Spacer(Modifier.height(24.dp))
                
                Button(
                    onClick = { viewModel.register() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    ),
                    enabled = namaLengkap.isNotEmpty() && nip.isNotEmpty() && 
                             namaPerusahaan.isNotEmpty() && kedudukanPerusahaan.isNotEmpty()
                ) {
                    Text("DAFTAR DAN GUNAKAN APLIKASI", fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Info Aplikasi
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Fitur Aplikasi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF333333)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "• 4 Mode pengukuran akurasi kWh Meter",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "• Simpan data secara offline",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "• Export laporan PDF 2 kolom",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "• Data tersimpan di perangkat",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
            }
        }
    }
}

// ================= MAIN SCREEN (Setelah Register) =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: KwhViewModel, userInfo: UserInfo?) {
    var currentTab by remember { mutableStateOf(0) }
    var showProfile by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("KWH Meter Test")
                        Text(
                            "Pengguna: ${userInfo?.namaLengkap ?: "User"}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D6EFD),
                    titleContentColor = Color.White
                ),
                actions = {
                    // Profile button
                    IconButton(
                        onClick = { showProfile = true }
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            tint = Color.White
                        )
                    }
                    
                    // Logout button
                    IconButton(
                        onClick = { viewModel.logout() }
                    ) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Custom Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D6EFD))
                    .height(48.dp)
            ) {
                // Tab 1: Input Data
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { currentTab = 0 }
                        .background(
                            if (currentTab == 0) Color.White.copy(alpha = 0.2f) else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Input Data",
                        color = Color.White,
                        fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                // Tab 2: Riwayat
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { currentTab = 1 }
                        .background(
                            if (currentTab == 1) Color.White.copy(alpha = 0.2f) else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val riwayatCount by viewModel.riwayatList.collectAsState()
                    Text(
                        "Riwayat (${riwayatCount.size})",
                        color = Color.White,
                        fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            
            // Konten berdasarkan tab yang dipilih
            when (currentTab) {
                0 -> InputDataScreen(viewModel, onNavigateToRiwayat = { currentTab = 1 })
                1 -> RiwayatScreen(viewModel, onNavigateToInput = { currentTab = 0 })
            }
        }
        
        // Profile Dialog
        if (showProfile) {
            ProfileDialog(
                viewModel = viewModel,
                userInfo = userInfo,
                onDismiss = { showProfile = false }
            )
        }
    }
}

// ================= PROFILE DIALOG =================
@Composable
fun ProfileDialog(
    viewModel: KwhViewModel,
    userInfo: UserInfo?,
    onDismiss: () -> Unit
) {
    val namaLengkap by viewModel.namaLengkap.collectAsState()
    val nip by viewModel.nip.collectAsState()
    val namaPerusahaan by viewModel.namaPerusahaan.collectAsState()
    val kedudukanPerusahaan by viewModel.kedudukanPerusahaan.collectAsState()
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(userInfo) {
        if (userInfo != null) {
            viewModel.setNamaLengkap(userInfo.namaLengkap)
            viewModel.setNip(userInfo.nip)
            viewModel.setNamaPerusahaan(userInfo.namaPerusahaan)
            viewModel.setKedudukanPerusahaan(userInfo.kedudukanPerusahaan)
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF9800)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    "Profil Pengguna",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                
                Spacer(Modifier.height(8.dp))
                
                Badge(
                    containerColor = Color(0xFF0D6EFD),
                    contentColor = Color.White
                ) {
                    Text("PENGGUNA")
                }
                
                Spacer(Modifier.height(24.dp))
                
                // User Info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Nama Lengkap",
                                fontSize = 12.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                userInfo?.namaLengkap ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFF333333),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "NIP",
                                fontSize = 12.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                userInfo?.nip ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFF333333),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Perusahaan",
                                fontSize = 12.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                userInfo?.namaPerusahaan ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFF333333),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Kedudukan",
                                fontSize = 12.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                userInfo?.kedudukanPerusahaan ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFF333333),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Terdaftar",
                                fontSize = 12.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                formatTimestamp(userInfo?.createdAt ?: 0),
                                fontSize = 12.sp,
                                color = Color(0xFF333333),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                // Edit Profile Form
                Text(
                    "Edit Profil",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = namaLengkap,
                    onValueChange = { viewModel.setNamaLengkap(it) },
                    label = { Text("Nama Lengkap") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = nip,
                    onValueChange = { viewModel.setNip(it) },
                    label = { Text("NIP") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = namaPerusahaan,
                    onValueChange = { viewModel.setNamaPerusahaan(it) },
                    label = { Text("Nama Perusahaan") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = kedudukanPerusahaan,
                    onValueChange = { viewModel.setKedudukanPerusahaan(it) },
                    label = { Text("Kedudukan Perusahaan") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Contoh: PLN UID/UP3/ULP") }
                )
                
                Spacer(Modifier.height(24.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFDC3545)
                        )
                    ) {
                        Text("Hapus Data")
                    }
                    
                    Button(
                        onClick = {
                            viewModel.updateUserInfo()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800)
                        ),
                        enabled = namaLengkap.isNotEmpty() && nip.isNotEmpty() && 
                                 namaPerusahaan.isNotEmpty() && kedudukanPerusahaan.isNotEmpty()
                    ) {
                        Text("Simpan")
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup")
                }
            }
        }
    }
    
    // Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Hapus Data Pengguna") },
            text = { 
                Text("Apakah Anda yakin ingin menghapus data pengguna? Semua data riwayat pengujian akan tetap tersimpan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logout()
                        showLogoutDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC3545)
                    )
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

// ================= INPUT DATA SCREEN =================
@Composable
fun InputDataScreen(viewModel: KwhViewModel, onNavigateToRiwayat: () -> Unit) {
    val mode by viewModel.mode.collectAsState()
    val blinkCount by viewModel.blinkCount.collectAsState()
    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val isCounting by viewModel.isCounting.collectAsState()
    val arus by viewModel.arus.collectAsState()
    val classMeter by viewModel.classMeter.collectAsState()
    val voltage by viewModel.voltage.collectAsState()
    val cosphi by viewModel.cosphi.collectAsState()
    val konstanta by viewModel.konstanta.collectAsState()
    val p1Input by viewModel.p1Input.collectAsState()
    val phaseR by viewModel.phaseR.collectAsState()
    val phaseS by viewModel.phaseS.collectAsState()
    val phaseT by viewModel.phaseT.collectAsState()
    val blinkRecords by viewModel.blinkRecords.collectAsState()
    val selectedBlinkIndex by viewModel.selectedBlinkIndex.collectAsState()
    
    val idPelanggan by viewModel.idPelanggan.collectAsState()
    val namaPelanggan by viewModel.namaPelanggan.collectAsState()
    val alamatPelanggan by viewModel.alamatPelanggan.collectAsState()
    val fotoPaths by viewModel.fotoPaths.collectAsState() // UBAH: dari fotoPath ke fotoPaths
    val currentRiwayatId by viewModel.currentRiwayatId.collectAsState()
    
    val results = viewModel.calculateResults()
    val isCalculationValid = remember(mode, selectedBlinkIndex, arus, voltage, cosphi, konstanta, p1Input, phaseR, phaseS, phaseT) {
        viewModel.isFormValidForSave()
    }
    
    var showSaveSuccess by remember { mutableStateOf(false) }
    
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
            viewModel.addFotoPath(capturedImagePath!!) // UBAH: gunakan addFotoPath
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
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    
    // Function to open camera
    fun openCamera() {
        val permission = Manifest.permission.CAMERA
        val permissionCheckResult = ContextCompat.checkSelfPermission(context, permission)
        
        when {
            permissionCheckResult == PackageManager.PERMISSION_GRANTED -> {
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
                showPermissionRationale = true
            }
            
            else -> {
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
                Text("Aplikasi memerlukan izin kamera untuk mengambil foto dokumentasi pelanggan.")
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "KWH Meter Test",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        getModeText(mode),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
                Icon(
                    getModeIcon(mode),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
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
                        "Data Pelanggan (Opsional)",
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
                    label = { Text("ID Pelanggan") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Opsional") }
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = namaPelanggan,
                    onValueChange = { viewModel.setNamaPelanggan(it) },
                    label = { Text("Nama Pelanggan") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Opsional") }
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = alamatPelanggan,
                    onValueChange = { viewModel.setAlamatPelanggan(it) },
                    label = { Text("Alamat") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    placeholder = { Text("Opsional") }
                )

                Spacer(Modifier.height(12.dp))

                // Foto Multiple Input Component
                FotoMultipleInput(
                    fotoPaths = fotoPaths,
                    onAddFoto = { openCamera() },
                    onRemoveFoto = { index -> viewModel.removeFotoPath(index) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Mode Selection
        ModeSelectionCard(viewModel)

        Spacer(Modifier.height(16.dp))

        // Display appropriate screen based on mode
        when (mode) {
            1 -> Mode1Screen(
                blinkCount = blinkCount,
                elapsedTime = elapsedTime,
                isCounting = isCounting,
                arus = arus,
                classMeter = classMeter,
                voltage = voltage,
                cosphi = cosphi,
                konstanta = konstanta,
                blinkRecords = blinkRecords,
                selectedBlinkIndex = selectedBlinkIndex,
                results = results,
                isCalculationValid = isCalculationValid,
                onArusChange = { viewModel.setArus(it) },
                onClassMeterChange = { viewModel.setClassMeter(it) },
                onVoltageChange = { viewModel.setVoltage(it) },
                onCosphiChange = { viewModel.setCosphi(it) },
                onKonstantaChange = { viewModel.setKonstanta(it) },
                onTimerToggle = { viewModel.startTimer() },
                onBlinkClick = { viewModel.incrementBlink() },
                onReset = { viewModel.resetMode1() },
                onSelectBlink = { viewModel.selectBlink(it) }
            )
            2 -> Mode2Screen(
                p1Input = p1Input,
                phaseR = phaseR,
                phaseS = phaseS,
                phaseT = phaseT,
                classMeter = classMeter,
                voltage = voltage,
                cosphi = cosphi,
                konstanta = konstanta,
                results = results,
                isCalculationValid = isCalculationValid,
                onP1InputChange = { viewModel.setP1Input(it) },
                onPhaseRChange = { viewModel.setPhaseR(it) },
                onPhaseSChange = { viewModel.setPhaseS(it) },
                onPhaseTChange = { viewModel.setPhaseT(it) },
                onClassMeterChange = { viewModel.setClassMeter(it) },
                onVoltageChange = { viewModel.setVoltage(it) },
                onCosphiChange = { viewModel.setCosphi(it) },
                onKonstantaChange = { viewModel.setKonstanta(it) }
            )
            3 -> CombinedModeScreen(
                mode = mode,
                blinkCount = blinkCount,
                elapsedTime = elapsedTime,
                isCounting = isCounting,
                classMeter = classMeter,
                konstanta = konstanta,
                phaseR = phaseR,
                phaseS = phaseS,
                phaseT = phaseT,
                blinkRecords = blinkRecords,
                selectedBlinkIndex = selectedBlinkIndex,
                results = results,
                isCalculationValid = isCalculationValid,
                onClassMeterChange = { viewModel.setClassMeter(it) },
                onKonstantaChange = { viewModel.setKonstanta(it) },
                onPhaseRChange = { viewModel.setPhaseR(it) },
                onPhaseSChange = { viewModel.setPhaseS(it) },
                onPhaseTChange = { viewModel.setPhaseT(it) },
                onTimerToggle = { viewModel.startTimer() },
                onBlinkClick = { viewModel.incrementBlink() },
                onReset = { viewModel.resetMode1() },
                onSelectBlink = { viewModel.selectBlink(it) }
            )
            4 -> CombinedModeScreen(
                mode = mode,
                p1Input = p1Input,
                arus = arus,
                voltage = voltage,
                cosphi = cosphi,
                classMeter = classMeter,
                results = results,
                isCalculationValid = isCalculationValid,
                onP1InputChange = { viewModel.setP1Input(it) },
                onArusChange = { viewModel.setArus(it) },
                onVoltageChange = { viewModel.setVoltage(it) },
                onCosphiChange = { viewModel.setCosphi(it) },
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
                    if (isCalculationValid) {
                        viewModel.saveRiwayat()
                        showSaveSuccess = true
                    } else {
                        Toast.makeText(context, "Lengkapi data pengukuran terlebih dahulu", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = isCalculationValid,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = getModeColor(mode)
                )
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
                    "• Format laporan 1 halaman dengan 2 kolom",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "• Bisa dicetak sebagai PDF",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
            }
        }
    }
}

// ================= KOMPONEN FOTO MULTIPLE INPUT =================
@Composable
fun FotoMultipleInput(
    fotoPaths: List<String>,
    onAddFoto: () -> Unit,
    onRemoveFoto: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
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
                    "Dokumentasi Foto",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )
                
                Badge(
                    containerColor = Color(0xFF0D6EFD),
                    contentColor = Color.White
                ) {
                    Text("${fotoPaths.size} Foto")
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Tombol Tambah Foto
            Button(
                onClick = onAddFoto,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0D6EFD)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Tambah Foto")
            }
            
            // Daftar Foto
            if (fotoPaths.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Foto yang sudah diambil:",
                    fontSize = 14.sp,
                    color = Color(0xFF6C757D)
                )
                
                Spacer(Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(fotoPaths.size) { index ->
                        FotoItem(
                            path = fotoPaths[index],
                            onRemove = { onRemoveFoto(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FotoItem(
    path: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE9ECEF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Photo,
                        contentDescription = null,
                        tint = Color(0xFF6C757D),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        File(path).name,
                        fontSize = 12.sp,
                        color = Color(0xFF333333),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatFileTimestamp(File(path).lastModified()),
                        fontSize = 10.sp,
                        color = Color(0xFF6C757D)
                    )
                }
            }
            
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Hapus Foto",
                    tint = Color(0xFFDC3545)
                )
            }
        }
    }
}

// ================= MODE SELECTION CARD =================
@Composable
fun ModeSelectionCard(viewModel: KwhViewModel) {
    val mode by viewModel.mode.collectAsState()
    
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
            
            // Mode 1
            ModeOption(
                modeNumber = 1,
                currentMode = mode,
                title = "MODE 1",
                p1Text = "P1 = IMPULSE METER",
                p2Text = "P2 = V × I × Cosφ (Tang kW)",
                onSelect = { viewModel.setMode(1) }
            )
            
            Spacer(Modifier.height(8.dp))
            
            // Mode 2
            ModeOption(
                modeNumber = 2,
                currentMode = mode,
                title = "MODE 2",
                p1Text = "P1 = DISPLAY METER",
                p2Text = "P2 = TANG kW",
                onSelect = { viewModel.setMode(2) }
            )
            
            Spacer(Modifier.height(8.dp))
            
            // Mode 3
            ModeOption(
                modeNumber = 3,
                currentMode = mode,
                title = "MODE 3",
                p1Text = "P1 = IMPULSE METER",
                p2Text = "P2 = TANG kW",
                onSelect = { viewModel.setMode(3) }
            )
            
            Spacer(Modifier.height(8.dp))
            
            // Mode 4
            ModeOption(
                modeNumber = 4,
                currentMode = mode,
                title = "MODE 4",
                p1Text = "P1 = DISPLAY METER",
                p2Text = "P2 = V × I × Cosφ (Tang kW)",
                onSelect = { viewModel.setMode(4) }
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Mode indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF9800).copy(alpha = 0.1f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    getModeIcon(mode),
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    getModeText(mode),
                    fontSize = 12.sp,
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ModeOption(
    modeNumber: Int,
    currentMode: Int,
    title: String,
    p1Text: String,
    p2Text: String,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (currentMode == modeNumber) Color(0xFFFF9800) else Color(0xFFF8F9FA)
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (currentMode == modeNumber) null else BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (currentMode == modeNumber) Color.White else Color(0xFF333333)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                p1Text,
                fontSize = 11.sp,
                color = if (currentMode == modeNumber) Color.White.copy(alpha = 0.9f) else Color(0xFF0D6EFD)
            )
            Text(
                p2Text,
                fontSize = 11.sp,
                color = if (currentMode == modeNumber) Color.White.copy(alpha = 0.9f) else Color(0xFF198754)
            )
        }
    }
}

// ================= MODE SCREENS =================
@Composable
fun Mode1Screen(
    blinkCount: Int,
    elapsedTime: Long,
    isCounting: Boolean,
    arus: String,
    classMeter: String,
    voltage: String,
    cosphi: String,
    konstanta: String,
    blinkRecords: List<BlinkRecord>,
    selectedBlinkIndex: Int?,
    results: CalculationResult,
    isCalculationValid: Boolean,
    onArusChange: (String) -> Unit,
    onClassMeterChange: (String) -> Unit,
    onVoltageChange: (String) -> Unit,
    onCosphiChange: (String) -> Unit,
    onKonstantaChange: (String) -> Unit,
    onTimerToggle: () -> Unit,
    onBlinkClick: () -> Unit,
    onReset: () -> Unit,
    onSelectBlink: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // P1 Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "P1: Impulse Meter",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF0D6EFD)
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Timer dan tombol
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Waktu", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            String.format("%.2f", elapsedTime / 1000.0) + " detik",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D6EFD)
                        )
                    }
                    
                    Button(
                        onClick = onTimerToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCounting) Color(0xFFDC3545) else Color(0xFF198754)
                        )
                    ) {
                        Text(if (isCounting) "STOP" else "START")
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Tombol impulse
                Button(
                    onClick = onBlinkClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0D6EFD)
                    ),
                    enabled = isCounting || blinkCount == 0
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "IMPULSE",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            blinkCount.toString(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Parameter
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = konstanta,
                        onValueChange = onKonstantaChange,
                        label = { Text("Konstanta") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = classMeter,
                        onValueChange = onClassMeterChange,
                        label = { Text("Kelas Meter") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                
                if (blinkRecords.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Pilih Rekaman Impulse:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(100.dp)) {
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
        
        // P2 Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "P2: V × I × Cosφ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF198754)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = arus,
                        onValueChange = onArusChange,
                        label = { Text("Arus (I)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = voltage,
                        onValueChange = onVoltageChange,
                        label = { Text("Tegangan (V)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = cosphi,
                    onValueChange = onCosphiChange,
                    label = { Text("Cos φ") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
        
        // Results Card
        ResultsCard(results, isCalculationValid, mode = 1)
    }
}

@Composable
fun Mode2Screen(
    p1Input: String,
    phaseR: String,
    phaseS: String,
    phaseT: String,
    classMeter: String,
    voltage: String,
    cosphi: String,
    konstanta: String,
    results: CalculationResult,
    isCalculationValid: Boolean,
    onP1InputChange: (String) -> Unit,
    onPhaseRChange: (String) -> Unit,
    onPhaseSChange: (String) -> Unit,
    onPhaseTChange: (String) -> Unit,
    onClassMeterChange: (String) -> Unit,
    onVoltageChange: (String) -> Unit,
    onCosphiChange: (String) -> Unit,
    onKonstantaChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // P1 Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "P1: Display Meter",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF0D6EFD)
                )
                
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = p1Input,
                    onValueChange = onP1InputChange,
                    label = { Text("P1 (kW) dari Display") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = classMeter,
                    onValueChange = onClassMeterChange,
                    label = { Text("Kelas Meter (%)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
        
        // P2 Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "P2: Tang kW",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF198754)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phaseR,
                        onValueChange = onPhaseRChange,
                        label = { Text("Phase R") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = phaseS,
                        onValueChange = onPhaseSChange,
                        label = { Text("Phase S") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = phaseT,
                        onValueChange = onPhaseTChange,
                        label = { Text("Phase T") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }
        
        // Results Card
        ResultsCard(results, isCalculationValid, mode = 2)
    }
}

@Composable
fun CombinedModeScreen(
    mode: Int,
    // Parameters for Mode 3
    blinkCount: Int = 0,
    elapsedTime: Long = 0,
    isCounting: Boolean = false,
    classMeter: String = "",
    konstanta: String = "",
    phaseR: String = "",
    phaseS: String = "",
    phaseT: String = "",
    blinkRecords: List<BlinkRecord> = emptyList(),
    selectedBlinkIndex: Int? = null,
    // Parameters for Mode 4
    p1Input: String = "",
    arus: String = "",
    voltage: String = "",
    cosphi: String = "",
    results: CalculationResult,
    isCalculationValid: Boolean,
    // Callbacks
    onClassMeterChange: (String) -> Unit = {},
    onKonstantaChange: (String) -> Unit = {},
    onPhaseRChange: (String) -> Unit = {},
    onPhaseSChange: (String) -> Unit = {},
    onPhaseTChange: (String) -> Unit = {},
    onTimerToggle: () -> Unit = {},
    onBlinkClick: () -> Unit = {},
    onReset: () -> Unit = {},
    onSelectBlink: (Int) -> Unit = {},
    onP1InputChange: (String) -> Unit = {},
    onArusChange: (String) -> Unit = {},
    onVoltageChange: (String) -> Unit = {},
    onCosphiChange: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (mode == 3) {
            // MODE 3 Components
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("P1: Impulse Meter", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0D6EFD))
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Waktu", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                String.format("%.2f", elapsedTime / 1000.0) + " detik",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D6EFD)
                            )
                        }
                        
                        Button(
                            onClick = onTimerToggle,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCounting) Color(0xFFDC3545) else Color(0xFF198754)
                            )
                        ) {
                            Text(if (isCounting) "STOP" else "START")
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Button(
                        onClick = onBlinkClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0D6EFD)
                        ),
                        enabled = isCounting || blinkCount == 0
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("IMPULSE", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                            Text(blinkCount.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = konstanta,
                            onValueChange = onKonstantaChange,
                            label = { Text("Konstanta") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = classMeter,
                            onValueChange = onClassMeterChange,
                            label = { Text("Kelas Meter") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    
                    // Tambahkan bagian untuk menampilkan rekaman impulse di mode 3
                    if (blinkRecords.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Pilih Rekaman Impulse:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.height(100.dp)) {
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("P2: Tang kW", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF198754))
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = phaseR,
                            onValueChange = onPhaseRChange,
                            label = { Text("Phase R") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = phaseS,
                            onValueChange = onPhaseSChange,
                            label = { Text("Phase S") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = phaseT,
                            onValueChange = onPhaseTChange,
                            label = { Text("Phase T") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }
        } else {
            // MODE 4 Components
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("P1: Display Meter", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0D6EFD))
                    
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = p1Input,
                        onValueChange = onP1InputChange,
                        label = { Text("P1 (kW) dari Display") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("P2: V × I × Cosφ", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF198754))
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = arus,
                            onValueChange = onArusChange,
                            label = { Text("Arus (I)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = voltage,
                            onValueChange = onVoltageChange,
                            label = { Text("Tegangan (V)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = cosphi,
                        onValueChange = onCosphiChange,
                        label = { Text("Cos φ") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = classMeter,
                        onValueChange = onClassMeterChange,
                        label = { Text("Kelas Meter (%)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }
        
        // Results Card
        ResultsCard(results, isCalculationValid, mode)
    }
}

@Composable
fun ResultsCard(results: CalculationResult, isCalculationValid: Boolean, mode: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            // RUMUS ERROR DI POJOK KIRI ATAS (kecil)
            Text(
                "ε = (P1 - P2) / P2 × 100%",
                fontSize = 10.sp,
                color = Color(0xFF6C757D),
                fontStyle = FontStyle.Italic,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 4.dp, start = 4.dp)
            )
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    getModeText(mode),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
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
                            color = Color(0xFF856404)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ResultBox(
                            title = "P1",
                            value = String.format("%.3f", results.p1),
                            unit = "kW",
                            color = Color(0xFF0D6EFD),
                            modifier = Modifier.weight(1f)
                        )
                        ResultBox(
                            title = "P2",
                            value = String.format("%.2f", results.p2),
                            unit = "kW",
                            color = Color(0xFF198754),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(Modifier.height(20.dp))
                    
                    // PERSEN ERROR DI ATAS BOX
                    val isInClass = Math.abs(results.error) <= results.kelasMeter
                    val statusColor = if (isInClass) 
                        Color(0xFF198754) 
                    else 
                        Color(0xFFDC3545)
                    
                    Text(
                        "${String.format("%.2f", results.error)}%",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // BOX STATUS
                    val statusText = if (isInClass) 
                        "DI DALAM KELAS METER" 
                    else 
                        "DI LUAR KELAS METER"
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.1f))
                            .border(
                                width = 1.5.dp,
                                color = statusColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // STATUS DI DALAM BOX
                        Text(
                            statusText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
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
                    "Impulse ke-${record.blinkNumber}",
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
            
            if (isSelected) {
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

// ================= RIWAYAT SCREEN =================
@Composable
fun RiwayatScreen(viewModel: KwhViewModel, onNavigateToInput: () -> Unit) {
    val riwayatList by viewModel.riwayatList.collectAsState()
    val userInfo by viewModel.userInfo.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showExportOptions by remember { mutableStateOf(false) }
    var exportInProgress by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    fun exportSingleRiwayat(riwayat: RiwayatData) {
        exportInProgress = true
        PdfExporter.exportRiwayatToPdf(
            context = context,
            riwayat = riwayat,
            userInfo = userInfo,
            onSuccess = { uri ->
                exportInProgress = false
                val fileName = "Laporan_${if (riwayat.pelangganData.nama.isNotEmpty()) riwayat.pelangganData.nama else "Data"}.pdf"
                PdfExporter.shareFile(context, uri, fileName)
            },
            onError = { error ->
                exportInProgress = false
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // HEADER
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
                        "Format laporan 1 halaman 2 kolom",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
                
                Row {
                    if (riwayatList.isNotEmpty()) {
                        IconButton(
                            onClick = { showExportOptions = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Export",
                                tint = Color.White
                            )
                        }
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
                        textAlign = TextAlign.Center
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
                        val impulseCount = riwayatList.count { it.mode == 1 }
                        val dayaSesaatCount = riwayatList.count { it.mode == 2 }
                        val kombinasi1Count = riwayatList.count { it.mode == 3 }
                        val kombinasi2Count = riwayatList.count { it.mode == 4 }
                        Text(
                            "M1:$impulseCount M2:$dayaSesaatCount M3:$kombinasi1Count M4:$kombinasi2Count",
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
                        onDelete = { viewModel.deleteRiwayat(riwayat.id) },
                        onExport = {
                            exportSingleRiwayat(riwayat)
                        }
                    )
                }
            }
        }
    }
    
    // DIALOGS
    if (showExportOptions) {
        AlertDialog(
            onDismissRequest = { showExportOptions = false },
            title = { Text("Export Laporan") },
            text = { 
                Column {
                    Text("Format laporan 1 halaman dengan 2 kolom")
                    Spacer(Modifier.height(8.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text("✓ Layout 2 kolom", fontSize = 12.sp)
                        Text("✓ Semua data dalam 1 halaman", fontSize = 12.sp)
                        Text("✓ Bisa dicetak sebagai PDF", fontSize = 12.sp)
                        Text("✓ Tanda tangan pelaksana", fontSize = 12.sp)
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            showExportOptions = false
                            if (riwayatList.isNotEmpty()) {
                                exportSingleRiwayat(riwayatList.last())
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Download Laporan Terbaru")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportOptions = false }) {
                    Text("Batal")
                }
            }
        )
    }
    
    if (exportInProgress) {
        Dialog(onDismissRequest = { }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF9800))
                    Spacer(Modifier.height(16.dp))
                    Text("Menyiapkan Laporan")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Format 1 halaman 2 kolom",
                        fontSize = 12.sp,
                        color = Color.Gray
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
    onDelete: () -> Unit,
    onExport: () -> Unit
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC3545)
                    )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (riwayat.pelangganData.nama.isNotEmpty()) 
                                "${riwayat.pelangganData.nama}"
                            else 
                                "Data Pengujian",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF333333)
                        )
                        Spacer(Modifier.width(8.dp))
                        Badge(
                            containerColor = Color(0xFFFF9800),
                            contentColor = Color.White
                        ) {
                            Text(
                                when (riwayat.mode) {
                                    1 -> "MODE 1"
                                    2 -> "MODE 2"
                                    3 -> "MODE 3"
                                    4 -> "MODE 4"
                                    else -> "UNKNOWN"
                                },
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        formatTimestamp(riwayat.timestamp),
                        fontSize = 11.sp,
                        color = Color(0xFF6C757D)
                    )
                    if (riwayat.pelangganData.idPelanggan.isNotEmpty()) {
                        Text(
                            "ID: ${riwayat.pelangganData.idPelanggan}",
                            fontSize = 11.sp,
                            color = Color(0xFF6C757D)
                        )
                    }
                }
                
                Row {
                    // Tombol Export
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Export Laporan 1 Halaman",
                                tint = Color(0xFF2196F3)
                            )
                            Text(
                                "PDF",
                                fontSize = 8.sp,
                                color = Color(0xFF2196F3),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    // Tombol Edit
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
                    
                    // Tombol Delete
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
            
            // Alamat jika ada
            if (riwayat.pelangganData.alamat.isNotEmpty()) {
                Text(
                    "📍 ${riwayat.pelangganData.alamat}",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Spacer(Modifier.height(8.dp))
            }
            
            // UBAH: dari fotoPath ke fotoPaths
            if (riwayat.pelangganData.fotoPaths.isNotEmpty()) {
                Text(
                    "📷 ${riwayat.pelangganData.fotoPaths.size} Foto",
                    fontSize = 11.sp,
                    color = Color(0xFF6C757D)
                )
                Spacer(Modifier.height(8.dp))
            }
            
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
            
            // Export info
            Spacer(Modifier.height(8.dp))
            Text(
                "📄 Format laporan: 1 halaman 2 kolom",
                fontSize = 10.sp,
                color = Color(0xFF6C757D)
            )
        }
    }
}
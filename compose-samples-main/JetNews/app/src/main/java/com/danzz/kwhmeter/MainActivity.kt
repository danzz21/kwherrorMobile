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
    val fotoPath: String = "",
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
) {
    fun toHtmlContent(context: Context): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; }
                .header { text-align: center; margin-bottom: 30px; }
                .section { margin-bottom: 25px; }
                .section-title { 
                    background-color: #0D6EFD; 
                    color: white; 
                    padding: 8px; 
                    border-radius: 5px; 
                    font-weight: bold;
                }
                .data-row { 
                    display: flex; 
                    justify-content: space-between; 
                    margin: 5px 0;
                    padding: 5px;
                }
                .data-row:nth-child(even) { background-color: #f8f9fa; }
                .label { font-weight: bold; color: #333; }
                .value { color: #0D6EFD; }
                .result-box { 
                    border: 1px solid #ddd; 
                    padding: 15px; 
                    margin: 10px 0;
                    border-radius: 8px;
                }
                .status-box { 
                    padding: 10px; 
                    text-align: center; 
                    border-radius: 5px;
                    font-weight: bold;
                    margin: 10px 0;
                }
                .status-ok { background-color: #d1e7dd; color: #0f5132; }
                .status-error { background-color: #f8d7da; color: #842029; }
                .footer { 
                    text-align: center; 
                    margin-top: 30px; 
                    font-size: 12px; 
                    color: #666;
                    border-top: 1px solid #ddd;
                    padding-top: 10px;
                }
                table { width: 100%; border-collapse: collapse; margin: 10px 0; }
                th, td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }
                th { background-color: #f2f2f2; }
                .mode-badge {
                    display: inline-block;
                    padding: 4px 8px;
                    border-radius: 12px;
                    font-size: 12px;
                    font-weight: bold;
                    margin-left: 8px;
                }
                .mode-1 { background-color: #0D6EFD; color: white; }
                .mode-2 { background-color: #198754; color: white; }
                .mode-3 { background-color: #FF9800; color: white; }
                .mode-4 { background-color: #9C27B0; color: white; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>LAPORAN PENGUJIAN KWH METER</h1>
                <h3>KWH Meter Test Application</h3>
                <p>${formatTimestamp(timestamp)}</p>
                <div>
                    <span class="mode-badge mode-${mode}">
                        ${getModeText(mode)}
                    </span>
                </div>
            </div>
            
            <div class="section">
                <div class="section-title">DATA PELANGGAN</div>
                ${if (pelangganData.idPelanggan.isNotEmpty()) """
                <div class="data-row">
                    <span class="label">ID Pelanggan:</span>
                    <span class="value">${pelangganData.idPelanggan}</span>
                </div>
                """ else ""}
                ${if (pelangganData.nama.isNotEmpty()) """
                <div class="data-row">
                    <span class="label">Nama:</span>
                    <span class="value">${pelangganData.nama}</span>
                </div>
                """ else ""}
                ${if (pelangganData.alamat.isNotEmpty()) """
                <div class="data-row">
                    <span class="label">Alamat:</span>
                    <span class="value">${pelangganData.alamat}</span>
                </div>
                """ else ""}
                ${if (pelangganData.fotoPath.isNotEmpty()) """
                <div class="data-row">
                    <span class="label">Foto:</span>
                    <span class="value">TERLAMPIR</span>
                </div>
                """ else ""}
                ${if (pelangganData.idPelanggan.isEmpty() && pelangganData.nama.isEmpty() && pelangganData.alamat.isEmpty()) """
                <div class="data-row">
                    <span class="label">Data Pelanggan:</span>
                    <span class="value">Tidak diisi</span>
                </div>
                """ else ""}
            </div>
            
            <div class="section">
                <div class="section-title">INFORMASI PENGUJIAN</div>
                <div class="data-row">
                    <span class="label">Mode Pengukuran:</span>
                    <span class="value">${getModeText(mode)}</span>
                </div>
                <div class="data-row">
                    <span class="label">Waktu Pengujian:</span>
                    <span class="value">${formatTimestamp(timestamp)}</span>
                </div>
                <div class="data-row">
                    <span class="label">Kelas Meter:</span>
                    <span class="value">${calculationResult.kelasMeter}%</span>
                </div>
                <div class="data-row">
                    <span class="label">Sumber P1:</span>
                    <span class="value">${calculationResult.p1Source}</span>
                </div>
                <div class="data-row">
                    <span class="label">Sumber P2:</span>
                    <span class="value">${calculationResult.p2Source}</span>
                </div>
                <div class="data-row">
                    <span class="label">Tegangan:</span>
                    <span class="value">${calculationResult.voltage} V</span>
                </div>
                <div class="data-row">
                    <span class="label">Cos φ:</span>
                    <span class="value">${calculationResult.cosphi}</span>
                </div>
                <div class="data-row">
                    <span class="label">Konstanta Meter:</span>
                    <span class="value">${calculationResult.konstanta} imp/kWh</span>
                </div>
            </div>
            
            ${getModeSpecificHtml()}
            
            <div class="section">
                <div class="section-title">HASIL PERHITUNGAN</div>
                <div class="result-box">
                    <table>
                        <tr>
                            <th>Parameter</th>
                            <th>Nilai</th>
                            <th>Keterangan</th>
                        </tr>
                        <tr>
                            <td>P1 (kW)</td>
                            <td>${String.format("%.3f", calculationResult.p1)}</td>
                            <td>${calculationResult.p1Source}</td>
                        </tr>
                        <tr>
                            <td>P2 (kW)</td>
                            <td>${String.format("%.3f", calculationResult.p2)}</td>
                            <td>${calculationResult.p2Source}</td>
                        </tr>
                        <tr>
                            <td>ERROR (%)</td>
                            <td>${String.format("%.2f", calculationResult.error)}</td>
                            <td>((P1-P2)/P2) × 100%</td>
                        </tr>
                        <tr>
                            <td>Toleransi Kelas</td>
                            <td>±${calculationResult.kelasMeter}%</td>
                            <td>Batas toleransi meter</td>
                        </tr>
                    </table>
                </div>
            </div>
            
            <div class="section">
                <div class="section-title">STATUS PENGUJIAN</div>
                <div class="status-box ${if (calculationResult.status == "DI DALAM KELAS METER") "status-ok" else "status-error"}">
                    <h2>${calculationResult.status}</h2>
                    <p>Error: ${String.format("%.2f", calculationResult.error)}% | Toleransi: ±${calculationResult.kelasMeter}%</p>
                    ${if (calculationResult.status == "DI DALAM KELAS METER") 
                        "<p>✅ Meter memenuhi standar akurasi</p>" 
                    else 
                        "<p>⚠️ Meter tidak memenuhi standar akurasi</p>"}
                </div>
            </div>
            
            <div class="section">
                <div class="section-title">CATATAN TEKNIS</div>
                <div class="data-row">
                    <span class="label">Konstanta Meter:</span>
                    <span class="value">${calculationResult.konstanta} imp/kWh</span>
                </div>
                <div class="data-row">
                    <span class="label">ID Data:</span>
                    <span class="value">${id}</span>
                </div>
            </div>
            
            <div class="footer">
                <p>Laporan ini dihasilkan secara otomatis oleh aplikasi KWH Meter Test</p>
                <p>© ${SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())} - Document Valid</p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    private fun getModeText(mode: Int): String {
        return when (mode) {
            1 -> "MODE IMPULSE"
            2 -> "MODE DAYA SESAAT"
            3 -> "KOMBINASI (P1: Impulse, P2: Sesaat)"
            4 -> "KOMBINASI (P1: Sesaat, P2: Impulse)"
            else -> "MODE TIDAK DIKETAHUI"
        }
    }
    
    private fun getModeSpecificHtml(): String {
        return when (mode) {
            1 -> getMode1Html()
            2 -> getMode2Html()
            3 -> getMode3Html()
            4 -> getMode4Html()
            else -> ""
        }
    }
    
    private fun getMode1Html(): String {
        return """
        <div class="section">
            <div class="section-title">DATA PENGUKURAN IMPULSE</div>
            <table>
                <tr>
                    <th>Parameter</th>
                    <th>Nilai</th>
                </tr>
                <tr>
                    <td>Arus (A)</td>
                    <td>${inputData.arus} A</td>
                </tr>
                <tr>
                    <td>Tegangan (V)</td>
                    <td>${inputData.voltage} V</td>
                </tr>
                <tr>
                    <td>Cos φ</td>
                    <td>${inputData.cosphi}</td>
                </tr>
                <tr>
                    <td>Konstanta Meter</td>
                    <td>${inputData.konstanta} imp/kWh</td>
                </tr>
                <tr>
                    <td>Jumlah Kedipan</td>
                    <td>${inputData.blinkCount} kali</td>
                </tr>
                <tr>
                    <td>Waktu Pengamatan</td>
                    <td>${String.format("%.2f", inputData.elapsedTime/1000.0)} detik</td>
                </tr>
                <tr>
                    <td>Kedipan Terpilih</td>
                    <td>Ke-${inputData.selectedBlinkIndex?.plus(1) ?: 1}</td>
                </tr>
                <tr>
                    <td>Frekuensi Kedipan</td>
                    <td>${if (inputData.elapsedTime > 0) 
                        String.format("%.3f", inputData.blinkCount.toDouble() / (inputData.elapsedTime/1000.0)) + "/dtk" 
                        else "-"}</td>
                </tr>
            </table>
        </div>
        """.trimIndent()
    }
    
    private fun getMode2Html(): String {
        return """
        <div class="section">
            <div class="section-title">DATA PENGUKURAN DAYA SESAAT</div>
            <table>
                <tr>
                    <th>Phase</th>
                    <th>Daya (kW)</th>
                </tr>
                <tr>
                    <td>Phase R</td>
                    <td>${inputData.phaseR} kW</td>
                </tr>
                <tr>
                    <td>Phase S</td>
                    <td>${inputData.phaseS} kW</td>
                </tr>
                <tr>
                    <td>Phase T</td>
                    <td>${inputData.phaseT} kW</td>
                </tr>
                <tr>
                    <td><strong>Total P2</strong></td>
                    <td><strong>${String.format("%.3f", calculationResult.p2)} kW</strong></td>
                </tr>
                <tr>
                    <td>P1 (Input)</td>
                    <td>${inputData.p1Input} kW</td>
                </tr>
                <tr>
                    <td>Konstanta Meter</td>
                    <td>${inputData.konstanta} imp/kWh</td>
                </tr>
            </table>
        </div>
        """.trimIndent()
    }
    
    private fun getMode3Html(): String {
        return """
        <div class="section">
            <div class="section-title">DATA KOMBINASI (P1: IMPULSE, P2: SESAAT)</div>
            <div class="section">
                <h4>Data untuk P1 (Impulse)</h4>
                <table>
                    <tr>
                        <td>Konstanta Meter</td>
                        <td>${inputData.konstanta} imp/kWh</td>
                    </tr>
                    <tr>
                        <td>Jumlah Kedipan</td>
                        <td>${inputData.blinkCount} kali</td>
                    </tr>
                    <tr>
                        <td>Waktu Pengamatan</td>
                        <td>${String.format("%.2f", inputData.elapsedTime/1000.0)} detik</td>
                    </tr>
                    <tr>
                        <td>Kedipan Terpilih</td>
                        <td>Ke-${inputData.selectedBlinkIndex?.plus(1) ?: 1}</td>
                    </tr>
                </table>
            </div>
            <div class="section">
                <h4>Data untuk P2 (Daya Sesaat)</h4>
                <table>
                    <tr>
                        <th>Phase</th>
                        <th>Daya (kW)</th>
                    </tr>
                    <tr>
                        <td>Phase R</td>
                        <td>${inputData.phaseR} kW</td>
                    </tr>
                    <tr>
                        <td>Phase S</td>
                        <td>${inputData.phaseS} kW</td>
                    </tr>
                    <tr>
                        <td>Phase T</td>
                        <td>${inputData.phaseT} kW</td>
                    </tr>
                </table>
            </div>
        </div>
        """.trimIndent()
    }
    
    private fun getMode4Html(): String {
        return """
        <div class="section">
            <div class="section-title">DATA KOMBINASI (P1: SESAAT, P2: IMPULSE)</div>
            <div class="section">
                <h4>Data untuk P1 (Daya Sesaat)</h4>
                <table>
                    <tr>
                        <td>P1 Input</td>
                        <td>${inputData.p1Input} kW</td>
                    </tr>
                </table>
            </div>
            <div class="section">
                <h4>Data untuk P2 (Impulse)</h4>
                <table>
                    <tr>
                        <td>Arus (A)</td>
                        <td>${inputData.arus} A</td>
                    </tr>
                    <tr>
                        <td>Tegangan (V)</td>
                        <td>${inputData.voltage} V</td>
                    </tr>
                    <tr>
                        <td>Cos φ</td>
                        <td>${inputData.cosphi}</td>
                    </tr>
                </table>
            </div>
        </div>
        """.trimIndent()
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

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
    
    // Mode: 1 = Impulse, 2 = Daya Sesaat, 3 = Kombinasi P1-Impulse P2-Sesaat, 4 = Kombinasi P1-Sesaat P2-Impulse
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
    
    private val _voltage = MutableStateFlow("220.0")
    val voltage: StateFlow<String> = _voltage.asStateFlow()
    
    private val _cosphi = MutableStateFlow("0.85")
    val cosphi: StateFlow<String> = _cosphi.asStateFlow()
    
    private val _konstanta = MutableStateFlow("1600")
    val konstanta: StateFlow<String> = _konstanta.asStateFlow()
    
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
    
    // Pelanggan data (optional)
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
            1 -> calculateP1FromMode1(konstantaValue) to "Dari perhitungan impulse"
            2 -> calculateP1FromMode2() to "Input langsung"
            3 -> calculateP1FromMode1(konstantaValue) to "Dari perhitungan impulse"
            4 -> calculateP1FromMode2() to "Input langsung"
            else -> 0.0 to "Tidak diketahui"
        }
        
        val (p2, p2Source) = when (modeValue) {
            1 -> calculateP2FromMode1(voltageValue, cosphiValue) to "Dari arus ${_arus.value}A, V=${voltageValue}, cosφ=${cosphiValue}"
            2 -> calculateP2FromMode2() to "Jumlah 3 phase"
            3 -> calculateP2FromMode2() to "Jumlah 3 phase (Mode sesaat)"
            4 -> calculateP2FromMode1(voltageValue, cosphiValue) to "Dari arus ${_arus.value}A (Mode impulse)"
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
        _fotoPath.value = null
        _currentRiwayatId.value = null
        
        if (_mode.value == 1) {
            resetMode1()
            _arus.value = "5.0"
            _classMeter.value = "1.0"
            _voltage.value = "220.0"
            _cosphi.value = "0.85"
            _konstanta.value = "1600"
        } else if (_mode.value == 2) {
            _p1Input.value = "10.0"
            _phaseR.value = "3.5"
            _phaseS.value = "3.5"
            _phaseT.value = "3.0"
            _classMeter.value = "1.0"
            _voltage.value = "220.0"
            _cosphi.value = "0.85"
            _konstanta.value = "1600"
        } else if (_mode.value == 3) {
            // Mode kombinasi P1-Impulse P2-Sesaat
            resetMode1()
            _phaseR.value = "3.5"
            _phaseS.value = "3.5"
            _phaseT.value = "3.0"
            _classMeter.value = "1.0"
            _konstanta.value = "1600"
        } else if (_mode.value == 4) {
            // Mode kombinasi P1-Sesaat P2-Impulse
            _p1Input.value = "10.0"
            _arus.value = "5.0"
            _classMeter.value = "1.0"
            _voltage.value = "220.0"
            _cosphi.value = "0.85"
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
                  _phaseS.value.isNotEmpty() && 
                  _phaseT.value.isNotEmpty()
            3 -> _selectedBlinkIndex.value != null &&
                  _phaseR.value.isNotEmpty() &&
                  _phaseS.value.isNotEmpty() &&
                  _phaseT.value.isNotEmpty() &&
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
    
    // Data pelanggan sekarang optional, jadi tidak perlu validasi
    fun isFormValidForSave(): Boolean {
        return isCalculationValid()
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

// ================= PDF EXPORTER =================
object PdfExporter {
    
    fun exportRiwayatToPdf(
        context: Context,
        riwayat: RiwayatData,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Generate HTML content
            val htmlContent = riwayat.toHtmlContent(context)
            
            // Create HTML file
            val htmlFile = createHtmlFile(context, riwayat)
            htmlFile.writeText(htmlContent)
            
            // Get URI for sharing
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                htmlFile
            )
            
            onSuccess(uri)
            
        } catch (e: Exception) {
            e.printStackTrace()
            onError("Gagal membuat file: ${e.message}")
        }
    }
    
    private fun createHtmlFile(context: Context, riwayat: RiwayatData): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(riwayat.timestamp))
        val pelangganName = if (riwayat.pelangganData.nama.isNotEmpty()) 
            "_${riwayat.pelangganData.nama.replace(" ", "_")}" 
        else ""
        val fileName = "Laporan${pelangganName}_${timestamp}.html"
        
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
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Laporan KWH Meter: $fileName")
                putExtra(Intent.EXTRA_TEXT, "Laporan pengujian KWH Meter terlampir")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Laporan"))
            
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
    val fotoPath by viewModel.fotoPath.collectAsState()
    val currentRiwayatId by viewModel.currentRiwayatId.collectAsState()
    
    val results = viewModel.calculateResults()
    val isCalculationValid = viewModel.isCalculationValid()
    val isFormValidForSave = viewModel.isFormValidForSave()
    
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
            colors = CardDefaults.cardColors(containerColor = getModeColor(mode)),
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
                        "Mode: ${getModeText(mode)}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
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

        // Pelanggan Input (OPTIONAL)
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

                // Camera Button
                Button(
                    onClick = {
                        openCamera()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (fotoPath == null) "Ambil Foto (Opsional)" else "Ganti Foto")
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
                title = "KOMBINASI: P1 dari IMPULSE, P2 dari DAYA SESAAT",
                p1Title = "P1 (Dari Impulse)",
                p2Title = "P2 (Dari Daya Sesaat)",
                p1Color = Color(0xFF0D6EFD),
                p2Color = Color(0xFF198754),
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
                title = "KOMBINASI: P1 dari DAYA SESAAT, P2 dari IMPULSE",
                p1Title = "P1 (Dari Daya Sesaat)",
                p2Title = "P2 (Dari Impulse)",
                p1Color = Color(0xFF198754),
                p2Color = Color(0xFF0D6EFD),
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
                    if (isFormValidForSave) {
                        viewModel.saveRiwayat()
                        showSaveSuccess = true
                    } else {
                        Toast.makeText(context, "Lengkapi data pengukuran terlebih dahulu", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = isFormValidForSave,
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
fun getModeText(mode: Int): String {
    return when (mode) {
        1 -> "IMPULSE"
        2 -> "DAYA SESAAT"
        3 -> "KOMBINASI P1-IMPULSE P2-SESAAT"
        4 -> "KOMBINASI P1-SESAAT P2-IMPULSE"
        else -> "UNKNOWN"
    }
}

@Composable
fun getModeColor(mode: Int): Color {
    return when (mode) {
        1 -> Color(0xFF0D6EFD) // Blue
        2 -> Color(0xFF198754) // Green
        3 -> Color(0xFFFF9800) // Orange
        4 -> Color(0xFF9C27B0) // Purple
        else -> Color(0xFF6C757D) // Gray
    }
}

@Composable
fun getModeIcon(mode: Int): androidx.compose.ui.graphics.vector.ImageVector {
    return when (mode) {
        1 -> Icons.Default.Bolt
        2 -> Icons.Default.ElectricBolt
        3 -> Icons.Default.Merge
        4 -> Icons.Default.SwapHoriz
        else -> Icons.Default.Settings
    }
}

@Composable
fun RiwayatScreen(viewModel: KwhViewModel, onNavigateToInput: () -> Unit) {
    val riwayatList by viewModel.riwayatList.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showExportOptions by remember { mutableStateOf(false) }
    var exportInProgress by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    fun exportSingleRiwayat(riwayat: RiwayatData) {
        exportInProgress = true
        PdfExporter.exportRiwayatToPdf(
            context = context,
            riwayat = riwayat,
            onSuccess = { uri ->
                exportInProgress = false
                val fileName = "Laporan_${if (riwayat.pelangganData.nama.isNotEmpty()) riwayat.pelangganData.nama else "Data"}.html"
                PdfExporter.shareFile(context, uri, fileName)
            },
            onError = { error ->
                exportInProgress = false
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    fun exportAllData() {
        if (riwayatList.isEmpty()) {
            Toast.makeText(context, "Tidak ada data untuk diexport", Toast.LENGTH_SHORT).show()
            return
        }
        
        exportInProgress = true
        // Export data terbaru saja
        val latestRiwayat = riwayatList.sortedByDescending { it.timestamp }.firstOrNull()
        latestRiwayat?.let {
            exportSingleRiwayat(it)
        } ?: run {
            exportInProgress = false
            Toast.makeText(context, "Tidak ada data untuk diexport", Toast.LENGTH_SHORT).show()
        }
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
                        "Data tersimpan lokal permanen",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
                
                Row {
                    // Tombol Export
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
                    
                    // Tombol Hapus Semua
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
                            "I:$impulseCount D:$dayaSesaatCount K1:$kombinasi1Count K2:$kombinasi2Count",
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
            title = { Text("Export Data") },
            text = { 
                Column {
                    Text("Pilih opsi export:")
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = {
                            showExportOptions = false
                            exportAllData()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Collections, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Data Terbaru")
                    }
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        "Catatan: Export akan menghasilkan file HTML yang bisa dibuka di berbagai perangkat",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
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
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Menyiapkan file...")
                    Spacer(Modifier.height(8.dp))
                    Text("Mohon tunggu", fontSize = 12.sp, color = Color.Gray)
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
                            containerColor = getModeColor(riwayat.mode),
                            contentColor = Color.White
                        ) {
                            Text(
                                when (riwayat.mode) {
                                    1 -> "IMPULSE"
                                    2 -> "SESAAT"
                                    3 -> "KOMB. 1"
                                    4 -> "KOMB. 2"
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
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Export",
                            tint = Color(0xFF198754)
                        )
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
            
            if (riwayat.pelangganData.fotoPath.isNotEmpty()) {
                Text(
                    "📷 Foto: ${File(riwayat.pelangganData.fotoPath).name}",
                    fontSize = 11.sp,
                    color = Color(0xFF6C757D)
                )
                Spacer(Modifier.height(8.dp))
            }
            
            // Source info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "P1: ${riwayat.calculationResult.p1Source}",
                    fontSize = 10.sp,
                    color = Color(0xFF0D6EFD),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "P2: ${riwayat.calculationResult.p2Source}",
                    fontSize = 10.sp,
                    color = Color(0xFF198754),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
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
            
            // Mode Standar
            Text(
                "Mode Standar:",
                fontSize = 12.sp,
                color = Color(0xFF6C757D)
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == 1,
                    onClick = { viewModel.setMode(1) },
                    label = { Text("IMPULSE") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0D6EFD),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = mode == 2,
                    onClick = { viewModel.setMode(2) },
                    label = { Text("DAYA SESAAT") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF198754),
                        selectedLabelColor = Color.White
                    )
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Mode Kombinasi
            Text(
                "Mode Kombinasi:",
                fontSize = 12.sp,
                color = Color(0xFF6C757D)
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == 3,
                    onClick = { viewModel.setMode(3) },
                    label = { 
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("KOMBINASI 1", fontSize = 11.sp)
                            Text("P1: Impulse", fontSize = 10.sp)
                            Text("P2: Sesaat", fontSize = 10.sp)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF9800),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = mode == 4,
                    onClick = { viewModel.setMode(4) },
                    label = { 
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("KOMBINASI 2", fontSize = 11.sp)
                            Text("P1: Sesaat", fontSize = 10.sp)
                            Text("P2: Impulse", fontSize = 10.sp)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF9C27B0),
                        selectedLabelColor = Color.White
                    )
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Mode indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(getModeColor(mode).copy(alpha = 0.1f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    getModeIcon(mode),
                    contentDescription = null,
                    tint = getModeColor(mode),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    getModeDescription(mode),
                    fontSize = 12.sp,
                    color = getModeColor(mode),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun getModeDescription(mode: Int): String {
    return when (mode) {
        1 -> "Mode Impulse: P1 dari perhitungan impulse, P2 dari arus dan tegangan"
        2 -> "Mode Daya Sesaat: P1 input langsung, P2 dari jumlah 3 phase"
        3 -> "Kombinasi 1: P1 dari impulse, P2 dari daya sesaat"
        4 -> "Kombinasi 2: P1 dari daya sesaat, P2 dari impulse"
        else -> "Mode tidak dikenali"
    }
}

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
        // CARD KONFIGURASI PENGUKURAN 1 PHASE (P1) - DI ATAS
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
                            "Input Untuk P1",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D6EFD),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
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
                            "IMPULSE",
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
                        textAlign = TextAlign.Center
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
                            "Rekaman Impulse",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF333333)
                        )
                        Text(
                            "Total: ${blinkRecords.size} impulse",
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

        // CARD INPUT UNTUK MENGHITUNG P2 - DI BAWAH
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Input untuk menghitung P2:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )
                
                Spacer(Modifier.height(12.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = arus,
                            onValueChange = onArusChange,
                            label = { Text("Arus (A)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = arus.isNotEmpty() && arus.toDoubleOrNull() == null
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = voltage,
                            onValueChange = onVoltageChange,
                            label = { Text("Tegangan (V)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = voltage.isNotEmpty() && voltage.toDoubleOrNull() == null
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = cosphi,
                            onValueChange = onCosphiChange,
                            label = { Text("Cos φ") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = cosphi.isNotEmpty() && cosphi.toDoubleOrNull() == null
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                // CARD DATA KELAS DAN KONSTANTA - DIGABUNG
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Data Kelas & Konstanta",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF333333)
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
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
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = konstanta,
                                    onValueChange = onKonstantaChange,
                                    label = { Text("Konstanta (imp/kWh)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = konstanta.isNotEmpty() && konstanta.toDoubleOrNull() == null,
                                    supportingText = {
                                        Text("Biasanya 1600, 3200, 6400, dll")
                                    }
                                )
                            }
                        }
                    }
                }
                
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
                            Spacer(Modifier.height(8.dp))
                            val kValue = try { konstanta.toDouble() } catch (e: Exception) { 1600.0 }
                            Text(
                                "Rumus: P1 = (3600 × n) ÷ (T × K)",
                                fontSize = 11.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                "P1 = (3600 × ${selectedRecord.blinkNumber}) ÷ (${String.format("%.2f", selectedRecord.timeSeconds)} × $kValue)",
                                fontSize = 11.sp,
                                color = Color(0xFF6C757D)
                            )
                            val numerator = 3600.0 * selectedRecord.blinkNumber
                            val denominator = selectedRecord.timeSeconds * kValue
                            Text(
                                "P1 = ${String.format("%.1f", numerator)} ÷ ${String.format("%.1f", denominator)}",
                                fontSize = 11.sp,
                                color = Color(0xFF6C757D)
                            )
                            Text(
                                "P1 = ${String.format("%.3f", numerator / denominator)} kW",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D6EFD)
                            )
                        }
                    }
                }
            }
        }

        // CARD HASIL PERHITUNGAN
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
                    "Hasil Perhitungan Mode Impulse",
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
                    // Parameter Input
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("V", fontSize = 12.sp, color = Color(0xFF6C757D))
                                Text("${voltage}V", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D6EFD))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("I", fontSize = 12.sp, color = Color(0xFF6C757D))
                                Text("${arus}A", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D6EFD))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("cos φ", fontSize = 12.sp, color = Color(0xFF6C757D))
                                Text(cosphi, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D6EFD))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("K", fontSize = 12.sp, color = Color(0xFF6C757D))
                                Text("${konstanta}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D6EFD))
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ResultBox(
                            title = "P1 (kW)",
                            value = String.format("%.3f", results.p1),
                            unit = "Dari impulse meter",
                            color = Color(0xFF0D6EFD),
                            modifier = Modifier.weight(1f)
                        )
                        ResultBox(
                            title = "P2 (kW)",
                            value = String.format("%.3f", results.p2),
                            unit = "Dari perhitungan",
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Input P1:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )
                
                Spacer(Modifier.height(12.dp))
                
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
                
                                Spacer(Modifier.height(12.dp))
                
                                // CARD DATA KELAS DAN KONSTANTA - SAMA SEPERTI MODE 1
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "Data Kelas & Konstanta",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF333333)
                                        )
                                        
                                        Spacer(Modifier.height(12.dp))
                                        
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Column(modifier = Modifier.weight(1f)) {
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
                                            }
                                            
                                            Column(modifier = Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = konstanta,
                                                    onValueChange = onKonstantaChange,
                                                    label = { Text("Konstanta (imp/kWh)") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    isError = konstanta.isNotEmpty() && konstanta.toDoubleOrNull() == null,
                                                    supportingText = {
                                                        Text("Biasanya 1600, 3200, 6400, dll")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                
                                Spacer(Modifier.height(16.dp))
                
                                Text(
                                    "Input P2 (Perhitungan Phase):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF333333)
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
                                    textAlign = TextAlign.Center
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
                                    "Hasil Perhitungan Daya Sesaat",
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
                                    // Parameter Input dengan konstanta
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("V", fontSize = 12.sp, color = Color(0xFF6C757D))
                                                Text("${voltage}V", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D6EFD))
                                            }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("cos φ", fontSize = 12.sp, color = Color(0xFF6C757D))
                                                Text(cosphi, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D6EFD))
                                            }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("K", fontSize = 12.sp, color = Color(0xFF6C757D))
                                                Text("${konstanta}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D6EFD))
                                            }
                                        }
                                    }
                
                                    Spacer(Modifier.height(16.dp))
                
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
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
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
fun CombinedModeScreen(
    mode: Int,
    title: String,
    p1Title: String,
    p2Title: String,
    p1Color: Color,
    p2Color: Color,
    // Parameters for Mode 3 (P1 from Impulse, P2 from Sesaat)
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
    // Parameters for Mode 4 (P1 from Sesaat, P2 from Impulse)
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
        // Mode indicator
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (mode == 3) Color(0xFFFF9800) else Color(0xFF9C27B0)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Merge,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (mode == 3) {
            // P1 dari Impulse, P2 dari Sesaat
            
            // Input untuk P1 (Impulse)
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
                            p1Title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = p1Color
                        )
                        Badge(
                            containerColor = p1Color,
                            contentColor = Color.White
                        ) {
                            Text("P1")
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Timer dan impulse input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Waktu",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                String.format("%.2f", elapsedTime / 1000.0) + " detik",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = p1Color
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
                            .height(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = p1Color
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
                                fontSize = 36.sp,
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
                    
                    // Konstanta untuk P1
                    OutlinedTextField(
                        value = konstanta,
                        onValueChange = onKonstantaChange,
                        label = { Text("Konstanta (imp/kWh)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = konstanta.isNotEmpty() && konstanta.toDoubleOrNull() == null
                    )
                    
                    // Tampilkan rekaman impulse jika ada
                    if (blinkRecords.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Rekaman Impulse:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.height(120.dp)
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
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Button(
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        Spacer(Modifier.width(8.dp))
                        Text("Reset P1 Input")
                    }
                }
            }
            
            // Input untuk P2 (Sesaat)
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
                            p2Title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = p2Color
                        )
                        Badge(
                            containerColor = p2Color,
                            contentColor = Color.White
                        ) {
                            Text("P2")
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
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
                    
                    val totalP2 = try {
                        phaseR.toDouble() + phaseS.toDouble() + phaseT.toDouble()
                    } catch (e: Exception) { 0.0 }
                    
                    Text(
                        "Total P2 = ${String.format("%.3f", totalP2)} kW",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = p2Color,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            // P1 dari Sesaat, P2 dari Impulse
            
            // Input untuk P1 (Sesaat)
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
                            p1Title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = p1Color
                        )
                        Badge(
                            containerColor = p1Color,
                            contentColor = Color.White
                        ) {
                            Text("P1")
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = p1Input,
                        onValueChange = onP1InputChange,
                        label = { Text("P1 (kW)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = p1Input.isNotEmpty() && p1Input.toDoubleOrNull() == null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedLabelColor = p1Color,
                            unfocusedLabelColor = p1Color,
                            focusedBorderColor = p1Color,
                            unfocusedBorderColor = p1Color.copy(alpha = 0.5f)
                        )
                    )
                }
            }
            
            // Input untuk P2 (Impulse)
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
                            p2Title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = p2Color
                        )
                        Badge(
                            containerColor = p2Color,
                            contentColor = Color.White
                        ) {
                            Text("P2")
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Input arus, tegangan, cosphi untuk perhitungan P2
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = arus,
                            onValueChange = onArusChange,
                            label = { Text("Arus (A)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedLabelColor = p2Color,
                                unfocusedLabelColor = p2Color,
                                focusedBorderColor = p2Color,
                                unfocusedBorderColor = p2Color.copy(alpha = 0.5f)
                            )
                        )
                        OutlinedTextField(
                            value = voltage,
                            onValueChange = onVoltageChange,
                            label = { Text("Tegangan (V)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedLabelColor = p2Color,
                                unfocusedLabelColor = p2Color,
                                focusedBorderColor = p2Color,
                                unfocusedBorderColor = p2Color.copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = cosphi,
                        onValueChange = onCosphiChange,
                        label = { Text("Cos φ") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedLabelColor = p2Color,
                            unfocusedLabelColor = p2Color,
                            focusedBorderColor = p2Color,
                            unfocusedBorderColor = p2Color.copy(alpha = 0.5f)
                        )
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    val p2Calculated = try {
                        (voltage.toDouble() * arus.toDouble() * cosphi.toDouble()) / 1000
                    } catch (e: Exception) { 0.0 }
                    
                    Text(
                        "P2 = (V × I × cosφ) / 1000 = ${String.format("%.3f", p2Calculated)} kW",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = p2Color,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Data Kelas Meter (untuk kedua mode)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Data Kelas Meter",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = classMeter,
                    onValueChange = onClassMeterChange,
                    label = { Text("Kelas Meter (%)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = classMeter.isNotEmpty() && !isValidClassMeter(classMeter)
                )
            }
        }
        
        // Hasil Perhitungan
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
                    "Hasil Perhitungan Kombinasi",
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
                            title = "P1 (kW)",
                            value = String.format("%.3f", results.p1),
                            unit = if (mode == 3) "Dari impulse" else "Dari sesaat",
                            color = p1Color,
                            modifier = Modifier.weight(1f)
                        )
                        ResultBox(
                            title = "P2 (kW)",
                            value = String.format("%.3f", results.p2),
                            unit = if (mode == 3) "Dari sesaat" else "Dari impulse",
                            color = p2Color,
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
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                results.status,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
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
                unfocusedBorderColor = color.copy(alpha = 0.5f),
                focusedLabelColor = color,
                unfocusedLabelColor = color
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
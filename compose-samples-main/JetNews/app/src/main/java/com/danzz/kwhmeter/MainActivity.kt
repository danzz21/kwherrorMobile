package com.danzz.kwhmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat

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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF5F5F5)
    ) {
        MainScreen()
    }
}

@Composable
fun MainScreen() {
    var mode by remember { mutableStateOf(1) } // 1 = 1 Phase, 2 = 3 Phase
    var blinkCount by remember { mutableStateOf(0) }
    var elapsedTime by remember { mutableLongStateOf(0L) }
    var isCounting by remember { mutableStateOf(false) }
    var startTime by remember { mutableLongStateOf(0L) }
    
    // Input data for Mode 1
    var arus by remember { mutableStateOf("5.0") }
    var classMeter by remember { mutableStateOf("1.0") }
    
    // Input data for Mode 2
    var p1Input by remember { mutableStateOf("10.0") }
    var phaseR by remember { mutableStateOf("3.5") }
    var phaseS by remember { mutableStateOf("3.5") }
    var phaseT by remember { mutableStateOf("3.0") }
    
    // Blink records
    val blinkRecords = remember { mutableStateListOf<BlinkRecord>() }
    var selectedBlinkIndex by remember { mutableStateOf<Int?>(null) }
    
    // Constants
    val VOLTAGE = 220.0
    val COSPHI = 0.85
    val CONSTANTA = 1600.0
    
    // Timer for mode 1
    LaunchedEffect(isCounting) {
        while (isCounting) {
            delay(10) // Update every 10ms for accuracy
            elapsedTime = System.currentTimeMillis() - startTime
        }
    }
    
    // Get selected blink record
    val selectedBlinkRecord = if (selectedBlinkIndex != null && selectedBlinkIndex!! < blinkRecords.size) {
        blinkRecords[selectedBlinkIndex!!]
    } else {
        null
    }
    
    // ========== CALCULATIONS FOR MODE 1 ==========
    // Calculate P1 based on selected blink
    val p1Mode1 = if (selectedBlinkRecord != null) {
        val blinkTime = selectedBlinkRecord.timeSeconds
        val selectedBlink = selectedBlinkRecord.blinkNumber
        
        if (blinkTime > 0) {
            val blinkPerSecond = selectedBlink.toDouble() / blinkTime
            (3600 * blinkPerSecond) / CONSTANTA
        } else {
            0.0
        }
    } else {
        0.0
    }
    
    // Calculate P2 for mode 1: (220 × Arus × 0.85) ÷ 1000
    val p2Mode1 = try {
        if (arus.isNotEmpty()) {
            (VOLTAGE * arus.toDouble() * COSPHI) / 1000
        } else {
            0.0
        }
    } catch (e: Exception) {
        0.0
    }
    
    // ========== CALCULATIONS FOR MODE 2 ==========
    // P1 is direct input in mode 2
    val p1Mode2 = try {
        p1Input.toDouble()
    } catch (e: Exception) {
        0.0
    }
    
    // P2 for mode 2: Pr + Ps + Pt
    val p2Mode2 = try {
        phaseR.toDouble() + phaseS.toDouble() + phaseT.toDouble()
    } catch (e: Exception) {
        0.0
    }
    
    // ========== FINAL VALUES ==========
    val p1 = if (mode == 1) p1Mode1 else p1Mode2
    val p2 = if (mode == 1) p2Mode1 else p2Mode2
    
    // Calculate error percentage: ((P1 - P2) ÷ P2) × 100%
    val error = if (p2 != 0.0) {
        ((p1 - p2) / p2) * 100
    } else {
        0.0
    }
    
    // Determine status based on class meter
    val classMeterValue = try { classMeter.toDouble() } catch (e: Exception) { 1.0 }
    val status = if (Math.abs(error) <= classMeterValue) "DI DALAM KELAS METER" else "DI LUAR KELAS METER"
    
    // Check if calculations are valid
    val isCalculationValid = when {
        mode == 1 -> selectedBlinkRecord != null && arus.isNotEmpty() && arus.toDoubleOrNull() != null
        mode == 2 -> p1Input.isNotEmpty() && p1Input.toDoubleOrNull() != null && 
                     phaseR.isNotEmpty() && phaseS.isNotEmpty() && phaseT.isNotEmpty()
        else -> false
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /* ================= HEADER ================= */
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

        /* ================= MODE SELECTION ================= */
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == 1,
                        onClick = { 
                            mode = 1
                            // Reset when switching to mode 1
                            isCounting = false
                            blinkCount = 0
                            elapsedTime = 0
                            blinkRecords.clear()
                            selectedBlinkIndex = null
                        },
                        label = { Text("1 Phase (Kedipan)") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0D6EFD),
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = mode == 2,
                        onClick = { mode = 2 },
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
                p1 = p1,
                p2 = p2,
                error = error,
                status = status,
                classMeterValue = classMeterValue,
                isCalculationValid = isCalculationValid,
                onArusChange = { arus = it },
                onClassMeterChange = { classMeter = it },
                onTimerToggle = { 
                    if (!isCounting) {
                        startTime = System.currentTimeMillis() - elapsedTime
                        isCounting = true
                    } else {
                        isCounting = false
                    }
                },
                onBlinkClick = {
                    if (!isCounting) {
                        isCounting = true
                        startTime = System.currentTimeMillis()
                        blinkRecords.clear()
                        blinkCount = 0
                        selectedBlinkIndex = null
                    }
                    
                    blinkCount++
                    val currentTime = elapsedTime / 1000.0
                    blinkRecords.add(BlinkRecord(blinkCount, currentTime))
                    
                    // Auto-select the latest blink if none selected
                    if (selectedBlinkIndex == null) {
                        selectedBlinkIndex = blinkCount - 1
                    }
                },
                onReset = {
                    blinkCount = 0
                    elapsedTime = 0
                    isCounting = false
                    blinkRecords.clear()
                    selectedBlinkIndex = null
                },
                onSelectBlink = { index ->
                    selectedBlinkIndex = index
                }
            )
        } else {
            Mode2Screen(
                p1Input = p1Input,
                phaseR = phaseR,
                phaseS = phaseS,
                phaseT = phaseT,
                classMeter = classMeter,
                p1 = p1,
                p2 = p2,
                error = error,
                status = status,
                classMeterValue = classMeterValue,
                isCalculationValid = isCalculationValid,
                onP1InputChange = { p1Input = it },
                onPhaseRChange = { phaseR = it },
                onPhaseSChange = { phaseS = it },
                onPhaseTChange = { phaseT = it },
                onClassMeterChange = { classMeter = it }
            )
        }

        /* ================= INFO SECTION ================= */
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

@Composable
fun Mode1Screen(
    blinkCount: Int,
    elapsedTime: Long,
    isCounting: Boolean,
    arus: String,
    classMeter: String,
    blinkRecords: List<BlinkRecord>,
    selectedBlinkIndex: Int?,
    p1: Double,
    p2: Double,
    error: Double,
    status: String,
    classMeterValue: Double,
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
        /* ===== INPUT CONFIGURATION ===== */
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
                
                // Input for P2 calculation
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
                
                // Class meter input
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
                
                // Selected blink info
                val selectedRecord = if (selectedBlinkIndex != null && selectedBlinkIndex < blinkRecords.size) {
                    blinkRecords[selectedBlinkIndex]
                } else null
                
                if (selectedRecord != null) {
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
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Calculate blink per second
                    val blinkPerSecond = selectedRecord.blinkNumber.toDouble() / selectedRecord.timeSeconds
                    Text(
                        "Kedipan/detik = ${String.format("%.3f", blinkPerSecond)}",
                        fontSize = 12.sp,
                        color = Color(0xFF6C757D),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        /* ===== TIMER AND COUNTER ===== */
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
                
                // Blink Counter Button
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
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
                
                Spacer(Modifier.height(8.dp))
                
                // Info text
                if (!isCounting && blinkCount == 0) {
                    Text(
                        "Tekan START lalu TAP tombol setiap kali meter berkedip",
                        fontSize = 12.sp,
                        color = Color(0xFFDC3545),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        /* ===== BLINK RECORDS ===== */
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
                    
                    // Blink records list
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
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        "Pilih kedipan untuk menghitung P1",
                        fontSize = 12.sp,
                        color = Color(0xFF6C757D),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        /* ===== CALCULATION RESULTS ===== */
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
                    // P1 and P2 Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ResultBox(
                            title = "P1 (kW)",
                            value = String.format("%.3f", p1),
                            unit = "Dari kedipan meter",
                            color = Color(0xFF0D6EFD),
                            modifier = Modifier.weight(1f)
                        )
                        
                        ResultBox(
                            title = "P2 (kW)",
                            value = String.format("%.3f", p2),
                            unit = "Dari arus ${arus}A",
                            color = Color(0xFF198754),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Error and Status Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ResultBox(
                            title = "ERROR",
                            value = String.format("%.2f", error),
                            unit = "%",
                            color = if (Math.abs(error) <= classMeterValue) Color(0xFF198754) else Color(0xFFDC3545),
                            modifier = Modifier.weight(1f)
                        )
                        
                        ResultBox(
                            title = "KELAS",
                            value = String.format("%.1f", classMeterValue),
                            unit = "%",
                            color = Color(0xFF6C757D),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Status
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                color = if (status == "DI DALAM KELAS METER") 
                                    Color(0xFFD1E7DD) 
                                else 
                                    Color(0xFFF8D7DA)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                status,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (status == "DI DALAM KELAS METER") 
                                    Color(0xFF0F5132) 
                                else 
                                    Color(0xFF842029)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (status == "DI DALAM KELAS METER")
                                    "✅ Meter dalam toleransi"
                                else
                                    "⚠️ Meter di luar toleransi",
                                fontSize = 12.sp,
                                color = if (status == "DI DALAM KELAS METER") 
                                    Color(0xFF0F5132) 
                                else 
                                    Color(0xFF842029)
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
    p1: Double,
    p2: Double,
    error: Double,
    status: String,
    classMeterValue: Double,
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
        /* ===== INPUT CONFIGURATION ===== */
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
                
                // Class meter input
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
                
                // P1 input
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
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                
                // Calculate P2 from inputs
                val p2Calculated = try {
                    phaseR.toDouble() + phaseS.toDouble() + phaseT.toDouble()
                } catch (e: Exception) {
                    0.0
                }
                
                Text(
                    "P2 = Phase R + Phase S + Phase T = ${String.format("%.3f", p2Calculated)} kW",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        /* ===== CALCULATION RESULTS ===== */
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
                    // P1 and P2 Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ResultBox(
                            title = "P1 (kW)",
                            value = String.format("%.3f", p1),
                            unit = "Input langsung",
                            color = Color(0xFF0D6EFD),
                            modifier = Modifier.weight(1f)
                        )
                        
                        ResultBox(
                            title = "P2 (kW)",
                            value = String.format("%.3f", p2),
                            unit = "Jumlah 3 Phase",
                            color = Color(0xFF198754),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Error and Status Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ResultBox(
                            title = "ERROR",
                            value = String.format("%.2f", error),
                            unit = "%",
                            color = if (Math.abs(error) <= classMeterValue) Color(0xFF198754) else Color(0xFFDC3545),
                            modifier = Modifier.weight(1f)
                        )
                        
                        ResultBox(
                            title = "KELAS",
                            value = String.format("%.1f", classMeterValue),
                            unit = "%",
                            color = Color(0xFF6C757D),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Phase Breakdown
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
                    
                    // Status
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                color = if (status == "DI DALAM KELAS METER") 
                                    Color(0xFFD1E7DD) 
                                else 
                                    Color(0xFFF8D7DA)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                status,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (status == "DI DALAM KELAS METER") 
                                    Color(0xFF0F5132) 
                                else 
                                    Color(0xFF842029)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (status == "DI DALAM KELAS METER")
                                    "✅ Meter dalam toleransi"
                                else
                                    "⚠️ Meter di luar toleransi",
                                fontSize = 12.sp,
                                color = if (status == "DI DALAM KELAS METER") 
                                    Color(0xFF0F5132) 
                                else 
                                    Color(0xFF842029)
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ================= COMPONENTS ================= */
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

/* ================= DATA CLASSES & HELPER FUNCTIONS ================= */
data class BlinkRecord(
    val blinkNumber: Int,
    val timeSeconds: Double
)

fun isValidClassMeter(value: String): Boolean {
    return when (value) {
        "1.0", "0.5", "0.2", "1", "0.5", "0.2" -> true
        else -> false
    }
}
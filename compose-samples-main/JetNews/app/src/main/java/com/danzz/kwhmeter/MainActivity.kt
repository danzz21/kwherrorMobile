package com.danzz.kwhmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0D6EFD),
                    secondary = Color(0xFF6C757D),
                    tertiary = Color(0xFF198754),
                    error = Color(0xFFDC3545),
                    background = Color(0xFFF8F9FA)
                )
            ) {
                KwhMeterApp()
            }
        }
    }
}

@Composable
fun KwhMeterApp() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1E3C72)
    ) {
        MainScreen()
    }
}

@Composable
fun MainScreen() {
    var mode by remember { mutableStateOf(1) }
    var blinkCount by remember { mutableStateOf(0) }
    var isCounting by remember { mutableStateOf(false) }
    var elapsedTime by remember { mutableStateOf(0L) }
    var startTime by remember { mutableLongStateOf(0L) }
    var powerValue by remember { mutableStateOf("1000") }
    var ctRatio by remember { mutableStateOf("1") }
    var vtRatio by remember { mutableStateOf("1") }
    var calculationEnabled by remember { mutableStateOf(true) }
    
    // Phase inputs for Mode 2
    var phaseR by remember { mutableStateOf("") }
    var phaseS by remember { mutableStateOf("") }
    var phaseT by remember { mutableStateOf("") }
    var voltageR by remember { mutableStateOf("220") }
    var voltageS by remember { mutableStateOf("220") }
    var voltageT by remember { mutableStateOf("220") }
    var currentR by remember { mutableStateOf("5") }
    var currentS by remember { mutableStateOf("5") }
    var currentT by remember { mutableStateOf("5") }
    var powerFactorR by remember { mutableStateOf("0.85") }
    var powerFactorS by remember { mutableStateOf("0.85") }
    var powerFactorT by remember { mutableStateOf("0.85") }

    // Calculate results
    val calculatedValues = remember(blinkCount, elapsedTime, powerValue, ctRatio, vtRatio, calculationEnabled) {
        if (!calculationEnabled || elapsedTime == 0L || blinkCount == 0) {
            Triple(0.0, 0.0, 0.0)
        } else {
            calculateResults(blinkCount, elapsedTime, powerValue, ctRatio, vtRatio)
        }
    }

    // Timer for counting
    LaunchedEffect(isCounting) {
        while (isCounting) {
            delay(1000)
            elapsedTime = System.currentTimeMillis() - startTime
        }
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
                .shadow(8.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(Color(0xFF0D6EFD)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, Color.Yellow, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚡", fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Error kWh Meter Test",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Electrical Measurement Tool",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { 
                            blinkCount = 0
                            elapsedTime = 0L
                            isCounting = false
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = Color.White
                        )
                    }
                    OutlinedButton(
                        onClick = {},
                        border = BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Dashboard")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        /* ================= MODE SELECTION ================= */
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Measurement Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { mode = 1 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mode == 1) Color(0xFF0D6EFD) else Color(0xFFE9ECEF),
                            contentColor = if (mode == 1) Color.White else Color(0xFF6C757D)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = if (mode == 1) 4.dp else 0.dp
                        )
                    ) {
                        Text("Blink Mode")
                    }

                    Button(
                        onClick = { mode = 2 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mode == 2) Color(0xFF0D6EFD) else Color(0xFFE9ECEF),
                            contentColor = if (mode == 2) Color.White else Color(0xFF6C757D)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = if (mode == 2) 4.dp else 0.dp
                        )
                    ) {
                        Text("3-Phase Mode")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (mode == 1) {
            Mode1(
                blinkCount = blinkCount,
                elapsedTime = elapsedTime,
                isCounting = isCounting,
                powerValue = powerValue,
                ctRatio = ctRatio,
                vtRatio = vtRatio,
                calculationEnabled = calculationEnabled,
                onBlink = { 
                    if (!isCounting) {
                        isCounting = true
                        startTime = System.currentTimeMillis()
                    }
                    blinkCount++
                },
                onPowerValueChange = { powerValue = it },
                onCtRatioChange = { ctRatio = it },
                onVtRatioChange = { vtRatio = it },
                onCalculationToggle = { calculationEnabled = it },
                onReset = {
                    blinkCount = 0
                    elapsedTime = 0L
                    isCounting = false
                },
                onTimerToggle = {
                    isCounting = it
                    if (it && startTime == 0L) {
                        startTime = System.currentTimeMillis()
                    }
                }
            )
        } else {
            Mode2(
                phaseR = phaseR,
                phaseS = phaseS,
                phaseT = phaseT,
                voltageR = voltageR,
                voltageS = voltageS,
                voltageT = voltageT,
                currentR = currentR,
                currentS = currentS,
                currentT = currentT,
                powerFactorR = powerFactorR,
                powerFactorS = powerFactorS,
                powerFactorT = powerFactorT,
                onPhaseRChange = { phaseR = it },
                onPhaseSChange = { phaseS = it },
                onPhaseTChange = { phaseT = it },
                onVoltageRChange = { voltageR = it },
                onVoltageSChange = { voltageS = it },
                onVoltageTChange = { voltageT = it },
                onCurrentRChange = { currentR = it },
                onCurrentSChange = { currentS = it },
                onCurrentTChange = { currentT = it },
                onPowerFactorRChange = { powerFactorR = it },
                onPowerFactorSChange = { powerFactorS = it },
                onPowerFactorTChange = { powerFactorT = it }
            )
        }

        Spacer(Modifier.height(20.dp))
        
        // Info Footer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(Color(0xFFF8F9FA)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "kWh Meter v1.0",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "Accuracy: ±0.5%",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
            }
        }
    }
}

/* ================= MODE 1 ================= */
@Composable
fun Mode1(
    blinkCount: Int,
    elapsedTime: Long,
    isCounting: Boolean,
    powerValue: String,
    ctRatio: String,
    vtRatio: String,
    calculationEnabled: Boolean,
    onBlink: () -> Unit,
    onPowerValueChange: (String) -> Unit,
    onCtRatioChange: (String) -> Unit,
    onVtRatioChange: (String) -> Unit,
    onCalculationToggle: (Boolean) -> Unit,
    onReset: () -> Unit,
    onTimerToggle: (Boolean) -> Unit
) {
    val seconds = elapsedTime / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, remainingSeconds)
    
    val (p1, p2, error) = calculateResults(blinkCount, elapsedTime, powerValue, ctRatio, vtRatio)
    val formatter = DecimalFormat("#,##0.000")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Timer and Counter Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Blink Measurement",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF333333)
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Timer Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Time",
                            fontSize = 12.sp,
                            color = Color(0xFF6C757D)
                        )
                        Text(
                            timeFormatted,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D6EFD)
                        )
                    }
                    
                    // Start/Stop Timer Button
                    Button(
                        onClick = { onTimerToggle(!isCounting) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCounting) Color(0xFFDC3545) else Color(0xFF198754)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isCounting) "Stop Timer" else "Start Timer")
                    }
                }
                
                Spacer(Modifier.height(20.dp))
                
                // Blink Counter
                Button(
                    onClick = onBlink,
                    modifier = Modifier.size(160.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0D6EFD)
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "TAP",
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
                            "Blink Count",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                Spacer(Modifier.height(20.dp))
                
                // Reset Button
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF6C757D)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Reset Counter")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Configuration Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Configuration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF333333)
                    )
                    
                    Switch(
                        checked = calculationEnabled,
                        onCheckedChange = onCalculationToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF0D6EFD),
                            checkedTrackColor = Color(0xFF0D6EFD).copy(alpha = 0.5f)
                        )
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ConfigurationInput(
                        title = "Power (W)",
                        value = powerValue,
                        onValueChange = onPowerValueChange,
                        unit = "W",
                        modifier = Modifier.weight(1f)
                    )
                    ConfigurationInput(
                        title = "CT Ratio",
                        value = ctRatio,
                        onValueChange = onCtRatioChange,
                        unit = ":1",
                        modifier = Modifier.weight(1f)
                    )
                    ConfigurationInput(
                        title = "VT Ratio",
                        value = vtRatio,
                        onValueChange = onVtRatioChange,
                        unit = ":1",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Results Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Measurement Results",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ResultCard(
                        title = "Meter 1 (P1)",
                        value = if (calculationEnabled) formatter.format(p1) else "0.000",
                        unit = "kW",
                        color = Color(0xFF0D6EFD),
                        modifier = Modifier.weight(1f)
                    )
                    ResultCard(
                        title = "Meter 2 (P2)",
                        value = if (calculationEnabled) formatter.format(p2) else "0.000",
                        unit = "kW",
                        color = Color(0xFF198754),
                        modifier = Modifier.weight(1f)
                    )
                    ResultCard(
                        title = "Error",
                        value = if (calculationEnabled) String.format("%.2f", error) else "0.00",
                        unit = "%",
                        color = if (error <= 2) Color(0xFF198754) else Color(0xFFDC3545),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                // Error Status
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .background(
                            color = when {
                                error == 0.0 -> Color(0xFFE9ECEF)
                                error <= 2 -> Color(0xFFD1E7DD)
                                else -> Color(0xFFF8D7DA)
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = when {
                            error == 0.0 -> "Start measurement to see results"
                            error <= 2 -> "✅ Error within acceptable limits (±2%)"
                            else -> "⚠️ Error exceeds acceptable limits"
                        },
                        color = when {
                            error == 0.0 -> Color(0xFF6C757D)
                            error <= 2 -> Color(0xFF0F5132)
                            else -> Color(0xFF842029)
                        },
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

/* ================= MODE 2 ================= */
@Composable
fun Mode2(
    phaseR: String,
    phaseS: String,
    phaseT: String,
    voltageR: String,
    voltageS: String,
    voltageT: String,
    currentR: String,
    currentS: String,
    currentT: String,
    powerFactorR: String,
    powerFactorS: String,
    powerFactorT: String,
    onPhaseRChange: (String) -> Unit,
    onPhaseSChange: (String) -> Unit,
    onPhaseTChange: (String) -> Unit,
    onVoltageRChange: (String) -> Unit,
    onVoltageSChange: (String) -> Unit,
    onVoltageTChange: (String) -> Unit,
    onCurrentRChange: (String) -> Unit,
    onCurrentSChange: (String) -> Unit,
    onCurrentTChange: (String) -> Unit,
    onPowerFactorRChange: (String) -> Unit,
    onPowerFactorSChange: (String) -> Unit,
    onPowerFactorTChange: (String) -> Unit
) {
    // Calculate total power
    val powerR = try { voltageR.toDouble() * currentR.toDouble() * powerFactorR.toDouble() } catch (_: Exception) { 0.0 }
    val powerS = try { voltageS.toDouble() * currentS.toDouble() * powerFactorS.toDouble() } catch (_: Exception) { 0.0 }
    val powerT = try { voltageT.toDouble() * currentT.toDouble() * powerFactorT.toDouble() } catch (_: Exception) { 0.0 }
    val totalPower = powerR + powerS + powerT
    val formatter = DecimalFormat("#,##0.00")
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 3-Phase Input Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "3-Phase Measurement",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF333333)
                    )
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF6C757D)
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PhaseCard(
                        title = "Phase R",
                        color = Color(0xFFDC3545),
                        voltage = voltageR,
                        current = currentR,
                        powerFactor = powerFactorR,
                        power = powerR,
                        onVoltageChange = onVoltageRChange,
                        onCurrentChange = onCurrentRChange,
                        onPowerFactorChange = onPowerFactorRChange,
                        modifier = Modifier.weight(1f)
                    )
                    PhaseCard(
                        title = "Phase S",
                        color = Color(0xFF198754),
                        voltage = voltageS,
                        current = currentS,
                        powerFactor = powerFactorS,
                        power = powerS,
                        onVoltageChange = onVoltageSChange,
                        onCurrentChange = onCurrentSChange,
                        onPowerFactorChange = onPowerFactorSChange,
                        modifier = Modifier.weight(1f)
                    )
                    PhaseCard(
                        title = "Phase T",
                        color = Color(0xFF0D6EFD),
                        voltage = voltageT,
                        current = currentT,
                        powerFactor = powerFactorT,
                        power = powerT,
                        onVoltageChange = onVoltageTChange,
                        onCurrentChange = onCurrentTChange,
                        onPowerFactorChange = onPowerFactorTChange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Total Power Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Total Power Calculation",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFDC3545).copy(alpha = 0.1f),
                                    Color(0xFF198754).copy(alpha = 0.1f),
                                    Color(0xFF0D6EFD).copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatter.format(totalPower),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )
                        Text(
                            "Watts",
                            fontSize = 14.sp,
                            color = Color(0xFF6C757D)
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoItem(
                        title = "Phase R",
                        value = formatter.format(powerR),
                        unit = "W",
                        color = Color(0xFFDC3545),
                        modifier = Modifier.weight(1f)
                    )
                    InfoItem(
                        title = "Phase S",
                        value = formatter.format(powerS),
                        unit = "W",
                        color = Color(0xFF198754),
                        modifier = Modifier.weight(1f)
                    )
                    InfoItem(
                        title = "Phase T",
                        value = formatter.format(powerT),
                        unit = "W",
                        color = Color(0xFF0D6EFD),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/* ================= HELPER FUNCTIONS ================= */
fun calculateResults(
    blinkCount: Int,
    elapsedTime: Long,
    powerValue: String,
    ctRatio: String,
    vtRatio: String
): Triple<Double, Double, Double> {
    return try {
        val timeInSeconds = elapsedTime / 1000.0
        if (blinkCount == 0 || timeInSeconds == 0.0) {
            return Triple(0.0, 0.0, 0.0)
        }
        
        val power = powerValue.toDouble()
        val ct = ctRatio.toDouble()
        val vt = vtRatio.toDouble()
        
        // Calculate blink frequency (blinks per hour)
        val blinksPerHour = (blinkCount / timeInSeconds) * 3600
        
        // Calculate measured power (assuming 1 blink = 1 Wh)
        val measuredPower = blinksPerHour * ct * vt / 1000.0 // Convert to kW
        
        // Calculate reference power (input power)
        val referencePower = power / 1000.0 // Convert to kW
        
        // Calculate error percentage
        val error = if (referencePower > 0) {
            ((measuredPower - referencePower) / referencePower) * 100
        } else {
            0.0
        }
        
        Triple(referencePower, measuredPower, error)
    } catch (e: Exception) {
        Triple(0.0, 0.0, 0.0)
    }
}

/* ================= COMPONENTS ================= */
@Composable
fun ConfigurationInput(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            title,
            fontSize = 12.sp,
            color = Color(0xFF6C757D)
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF0D6EFD),
                unfocusedBorderColor = Color(0xFFDEE2E6)
            ),
            shape = RoundedCornerShape(8.dp),
            trailingIcon = {
                Text(
                    unit,
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
            }
        )
    }
}

@Composable
fun ResultCard(
    title: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
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
                color = Color(0xFF6C757D)
            )
        }
    }
}

@Composable
fun PhaseCard(
    title: String,
    color: Color,
    voltage: String,
    current: String,
    powerFactor: String,
    power: Double,
    onVoltageChange: (String) -> Unit,
    onCurrentChange: (String) -> Unit,
    onPowerFactorChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(Color.White),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            ConfigurationInput(
                title = "Voltage",
                value = voltage,
                onValueChange = onVoltageChange,
                unit = "V",
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(8.dp))
            
            ConfigurationInput(
                title = "Current",
                value = current,
                onValueChange = onCurrentChange,
                unit = "A",
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(8.dp))
            
            ConfigurationInput(
                title = "Power Factor",
                value = powerFactor,
                onValueChange = onPowerFactorChange,
                unit = "",
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(12.dp))
            
            Divider(color = Color(0xFFE9ECEF), thickness = 1.dp)
            
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Power:",
                    fontSize = 12.sp,
                    color = Color(0xFF6C757D)
                )
                Text(
                    "${"%.1f".format(power)} W",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
fun InfoItem(
    title: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
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
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            unit,
            fontSize = 10.sp,
            color = Color(0xFF6C757D)
        )
    }
}
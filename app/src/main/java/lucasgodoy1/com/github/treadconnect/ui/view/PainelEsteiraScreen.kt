package lucasgodoy1.com.github.treadconnect.ui.view

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import lucasgodoy1.com.github.treadconnect.bluetoothscanner.BluetoothScanner
import java.util.Locale

@SuppressLint("MissingPermission")
@Composable
fun PainelEsteiraScreen(navController: NavController, scanner: BluetoothScanner) {
    val data by scanner.treadmillData.collectAsState()
    
    var showDialog by remember { mutableStateOf(value = true) }
    var weightInput by remember { mutableStateOf("75") }
    var heightInput by remember { mutableStateOf("175") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { /* O usuário deve confirmar para maior precisão */ },
            containerColor = Color(0xFF2C2D2E),
            title = {
                Text(
                    text = "Métricas do Usuário",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Informe seu peso e altura para um cálculo de calorias mais preciso.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it.filter { char -> (char.isDigit()) || (char == '.') } },
                        label = { Text("Peso (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Green,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color.Green,
                            unfocusedLabelColor = Color.Gray
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { heightInput = it.filter { char -> char.isDigit() } },
                        label = { Text("Altura (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Green,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color.Green,
                            unfocusedLabelColor = Color.Gray
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val weight = weightInput.toDoubleOrNull() ?: 75.0
                        val height = heightInput.toIntOrNull() ?: 175
                        scanner.setUserMetrics(weight, height)
                        showDialog = false
                    }
                ) {
                    Text("Confirmar", color = Color.Green, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1F2021))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Painel da Esteira",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 48.dp, bottom = 24.dp)
        )

        Text(
            text = "Status: ${data.status}",
            color = if (data.status.contains("Erro")) Color.Red else Color.Green,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Destaque para Velocidade e Inclinação
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoCard(
                label = "VELOCIDADE",
                value = String.format(Locale.US, "%.1f", data.speed),
                unit = "km/h",
                modifier = Modifier.weight(1f),
                isHighlight = true
            )
            InfoCard(
                label = "INCLINAÇÃO",
                value = data.incline.toString(),
                unit = "%",
                modifier = Modifier.weight(1f),
                isHighlight = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid para as outras métricas
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                InfoCard(label = "TEMPO", value = formatTime(data.time), unit = "")
            }
            item {
                InfoCard(label = "DISTÂNCIA", value = String.format(Locale.US, "%.2f", data.distance), unit = "km")
            }
            item {
                InfoCard(label = "CALORIAS", value = data.calories.toString(), unit = "kcal")
            }
            item {
                InfoCard(label = "PULSO", value = data.pulse.toString(), unit = "bpm")
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                scanner.disconnect()
                navController.popBackStack("buscar", inclusive = false)
            },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Desconectar", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun InfoCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    isHighlight: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xF827292A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = if (isHighlight) 44.sp else 32.sp,
                    fontWeight = FontWeight.Black
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}


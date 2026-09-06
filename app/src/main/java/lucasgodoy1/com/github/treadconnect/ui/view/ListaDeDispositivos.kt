package lucasgodoy1.com.github.treadconnect.ui.view

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import lucasgodoy1.com.github.treadconnect.bluetoothscanner.BluetoothScanner
import lucasgodoy1.com.github.treadconnect.bluetoothscanner.ConnectionState

@SuppressLint("MissingPermission")
@Composable
fun ListarDispositivos(navController: NavController, scanner: BluetoothScanner){
    val devices by scanner.foundDevices.collectAsState()
    val connectionState by scanner.connectionState.collectAsState()

    // Navega para o painel assim que a conexão for estabelecida
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            navController.navigate("painel")
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(color = Color(0xFF1F2021))) {
        Box(modifier = Modifier.padding(50.dp).fillMaxSize()
            .background(color = Color(0xF827292A), shape = RoundedCornerShape(12.dp))){
            LazyColumn(modifier = Modifier.padding(13.dp).fillMaxSize()) {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                scanner.stopScan()
                                scanner.connectToDevice(device)
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2E2F)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = device.name ?: "desconhecido",
                            color = Color.White,
                            modifier = Modifier.padding(14.dp),
                            fontWeight = if (device.name != "desconhecido") FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}


    @Preview(showBackground = true)
    @Composable
    fun prev() {
        ListarDispositivos(rememberNavController(), BluetoothScanner(LocalContext.current))
    }

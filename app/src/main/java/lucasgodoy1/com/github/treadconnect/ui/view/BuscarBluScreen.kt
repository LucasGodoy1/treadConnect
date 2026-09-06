package lucasgodoy1.com.github.treadconnect.ui.view

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import lucasgodoy1.com.github.treadconnect.bluetoothscanner.BluetoothScanner

@SuppressLint("MissingPermission")
@Composable
fun BuscarBluScreen(navController: NavController, scanner: BluetoothScanner) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1F2021)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {


        Text(
            modifier = Modifier.padding(top = 55.dp),
            text = "Procurar Dispositivo",
            color = Color(0xFFF8F8F8),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val scanConcedida = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            val connectConcedida = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            val locationConcedida = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scanConcedida && connectConcedida && locationConcedida) ||
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && locationConcedida)) {
                Log.d("BluetoothScanner", "Permissões concedidas")
                scanner.startScan()
                navController.navigate("lista")
            } else {
                Log.d("BluetoothScanner", "Permissões negadas")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {

            Button(
                onClick = {
                    val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }, modifier = Modifier.fillMaxWidth(0.7f).height(65.dp).widthIn(max=300.dp)
            ) {
                    Text("Buscar", Modifier.padding(), fontSize = 30.sp, style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(4f, 4f))))


            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BuscarBluScreenPreview() {
    val navController = rememberNavController()
    val scanner = BluetoothScanner(LocalContext.current)
    BuscarBluScreen(navController, scanner)
}
package lucasgodoy1.com.github.treadconnect

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import lucasgodoy1.com.github.treadconnect.bluetoothscanner.BluetoothScanner
import lucasgodoy1.com.github.treadconnect.ui.view.BuscarBluScreen
import lucasgodoy1.com.github.treadconnect.ui.theme.TreadConnectTheme
import lucasgodoy1.com.github.treadconnect.ui.view.ListarDispositivos
import lucasgodoy1.com.github.treadconnect.ui.view.PainelEsteiraScreen

class MainActivity : ComponentActivity() {
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TreadConnectTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val scanner = remember { BluetoothScanner(context) }

                NavHost(navController = navController, startDestination = "buscar") {
                    composable("buscar") {
                        BuscarBluScreen(navController, scanner)
                    }
                    composable("lista") {
                        ListarDispositivos(navController, scanner)
                    }
                    composable("painel") {
                        PainelEsteiraScreen(navController, scanner)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun appPreview() {
    TreadConnectTheme {
        val navController = rememberNavController()
        val scanner = BluetoothScanner(LocalContext.current)
        BuscarBluScreen(navController, scanner)
    }
}
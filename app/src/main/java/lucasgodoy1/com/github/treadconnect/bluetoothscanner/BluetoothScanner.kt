package lucasgodoy1.com.github.treadconnect.bluetoothscanner

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID


data class TreadmillData(
    val speed: Double = 0.0,
    val incline: Int = 0,
    val time: Int = 0,
    val distance: Double = 0.0,
    val calories: Int = 0,
    val pulse: Int = 0,
    val status: String = "Desconectado",
)

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}

class BluetoothScanner(private val context : Context) {

    private val bluetoothManager by lazy {
        try {
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        } catch (_: Throwable) {
            null
        }
    }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }
    private val bluetoothLeScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private var bluetoothGatt: BluetoothGatt? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    // Métricas do usuário para cálculo de calorias
    private var userWeight = 75.0 // Peso padrão
    private var userHeight = 175  // Altura padrão (cm)

    fun setUserMetrics(weight: Double, height: Int) {
        userWeight = weight
        userHeight = height
    }

    // UUIDs da esteira Zyou 250s (Corrigidos via LOG do usuário)
    private val SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_NOTIFY_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb") // FFF1 é Notify
    private val CHARACTERISTIC_WRITE_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")  // FFF2 é Write
    private val CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // UUIDs Padrão FTMS (Fitness Machine Service)
    private val FTMS_SERVICE_UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val FTMS_TREADMILL_DATA_UUID = UUID.fromString("00002acd-0000-1000-8000-00805f9b34fb")

    // Comandos FitShow
    private val HANDSHAKE_COMMAND = byteArrayOf(0x02, 0x43, 0x01, 0x01, 0x47, 0x03) // Request Status
    private val HEARTBEAT_BYTE = byteArrayOf(0x02, 0x53, 0x01, 0x00, 0x00, 0x00, 0x56, 0x03) // Ping

    // StateFlow que armazena a lista de dispositivos encontrados.
    private val _foundDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val foundDevices = _foundDevices.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _treadmillData = MutableStateFlow(TreadmillData())
    val treadmillData = _treadmillData.asStateFlow()

    private val scanCallback = object : ScanCallback(){
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            
            // Verifica se o dispositivo já está na lista (pelo endereço MAC).
            _foundDevices.update { listaAtual ->
                if (listaAtual.any { it.address == device.address }) {
                    listaAtual
                } else {
                    listaAtual + device
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BluetoothScanner", "Erro ao escanear: $errorCode")
        }
    }

    // Inicia a busca/limpeza da lista para que cada busca comece do zero.
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        _foundDevices.value = emptyList()
        bluetoothLeScanner?.startScan(scanCallback)
    }

    // Para a busca. É importante chamar isso para economizar a bateria do celular.
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan(){
        bluetoothLeScanner?.stopScan(scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        _treadmillData.update { it.copy(status = "Iniciando conexão...") }
        // 1. Para o scan antes de conectar para evitar conflitos no rádio Bluetooth
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner?.stopScan(scanCallback)
            }
        } catch (e: Exception) {
            Log.e("BluetoothScanner", "Erro ao parar scan: ${e.message}")
        }

        _connectionState.value = ConnectionState.CONNECTING
        
        bluetoothGatt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        stopHeartbeat()
        bluetoothGatt?.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (_connectionState.value == ConnectionState.CONNECTED) {
                sendHeartbeat()
                delay(1000) // Envia a cada 1 segundo
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendHeartbeat() {
        val service = bluetoothGatt?.getService(SERVICE_UUID)
        val writeChar = service?.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
        writeChar?.let {
            it.value = HEARTBEAT_BYTE
            it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            bluetoothGatt?.writeCharacteristic(it)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val errorMsg = "Erro Conexão: status=$status"
                Log.e("BluetoothScanner", errorMsg)
                _treadmillData.update { it.copy(status = errorMsg) }
                _connectionState.value = ConnectionState.DISCONNECTED
                gatt.close()
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _treadmillData.update { it.copy(status = "Conectado") }
                _connectionState.value = ConnectionState.CONNECTED
                gatt.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _treadmillData.update { it.copy(status = "Desconectado") }
                stopHeartbeat()
                _connectionState.value = ConnectionState.DISCONNECTED
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val ftmsService = gatt.getService(FTMS_SERVICE_UUID)
                val proprietaryService = gatt.getService(SERVICE_UUID)

                if (ftmsService != null) {
                    val ftmsDataChar = ftmsService.getCharacteristic(FTMS_TREADMILL_DATA_UUID)
                    ftmsDataChar?.let { ativarNotificacoes(gatt, it) }
                } else if (proprietaryService != null) {
                    val notifyChar = proprietaryService.getCharacteristic(CHARACTERISTIC_NOTIFY_UUID)
                    notifyChar?.let { ativarNotificacoes(gatt, it) }
                } else {
                    Log.e("BluetoothScanner", "Nenhum serviço de esteira compatível encontrado!")
                    _treadmillData.update { it.copy(status = "Erro: Esteira incompatível") }
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private fun ativarNotificacoes(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
            
            if (descriptor != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            } else {
                Log.e("BluetoothScanner", "Descritor 2902 não encontrado para ${characteristic.uuid}")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if ((status == BluetoothGatt.GATT_SUCCESS) && (descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR)) {
                val service = gatt.getService(SERVICE_UUID)
                val writeChar = service?.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
                if (writeChar != null) {
                    writeChar.value = HANDSHAKE_COMMAND
                    writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    gatt.writeCharacteristic(writeChar)
                }
                startHeartbeat()
            } else {
                Log.e("BluetoothScanner", "Falha ao escrever descritor: $status")
                _treadmillData.update { it.copy(status = "Erro ativação: $status") }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // Callback de escrita mantido para consistência
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // Método depreciado mantido para compatibilidade com APIs antigas
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == FTMS_TREADMILL_DATA_UUID) {
                parseFtmsData(value)
            }
        }
    }

    /**
     * Processa dados seguindo o protocolo padrão Bluetooth FTMS (Fitness Machine Service).
     * Esta esteira Zyou 250s utiliza o perfil de Treadmill Data.
     */
    private fun parseFtmsData(data: ByteArray) {
        try {
            if (data.size < 2) return

            // Flags indicam quais campos estão presentes no pacote
            val flags = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
            
            var index = 2
            
            // Velocidade (Instantaneous Speed) - Obrigatório
            val speedRaw = ((data[index + 1].toInt() and 0xFF) shl 8) or (data[index].toInt() and 0xFF)
            val speed = speedRaw / 100.0
            index += 2

            // Pula Velocidade Média se presente
            if (flags and 0x0002 != 0) index += 2

            // Distância Total
            var distance = 0.0
            if (flags and 0x0004 != 0) {
                val d1 = data[index].toInt() and 0xFF
                val d2 = data[index+1].toInt() and 0xFF
                val d3 = data[index+2].toInt() and 0xFF
                distance = (d1 or (d2 shl 8) or (d3 shl 16)) / 1000.0
                index += 3
            }

            // Inclinação e Ângulo de Rampa
            var incline = 0
            if (flags and 0x0008 != 0) {
                val incRaw = ((data[index + 1].toInt() and 0xFF) shl 8) or (data[index].toInt() and 0xFF)
                incline = incRaw / 10
                index += 4 
            }

            // Pula Elevação Positiva/Negativa
            if (flags and 0x0010 != 0) index += 4
            // Pula Pace Instantâneo e Médio
            if (flags and 0x0020 != 0) index += 2
            if (flags and 0x0040 != 0) index += 2

            // Energia Total (Calorias)
            var calories = 0
            if (flags and 0x0080 != 0) {
                val totalEnergy = ((data[index + 1].toInt() and 0xFF) shl 8) or (data[index].toInt() and 0xFF)
                calories = totalEnergy
                index += 5 
            }

            // Batimento Cardíaco
            var pulse = 0
            if (flags and 0x0100 != 0) {
                pulse = data[index].toInt() and 0xFF
                index += 1
            }

            // Tempo Decorrido
            var time = 0
            if (flags and 0x0400 != 0) {
                if (flags and 0x0200 != 0) index += 1 // Pula MET
                if (index + 1 < data.size) {
                    time = ((data[index + 1].toInt() and 0xFF) shl 8) or (data[index].toInt() and 0xFF)
                }
            }

            // Cálculo manual de calorias caso o hardware não forneça, usando METs baseado em velocidade e inclinação
            if (calories == 0 && speed > 0) {
                val speedMetersPerMin = (speed * 1000.0) / 60.0
                val weightKg = userWeight 
                val timeHours = time / 3600.0
                // Fórmula ACSM para VO2 (ml/kg/min)
                val vo2 = (0.1 * speedMetersPerMin) + (1.8 * speedMetersPerMin * (incline / 100.0)) + 3.5
                val met = vo2 / 3.5
                calories = (met * weightKg * timeHours).toInt()
            }

            _treadmillData.update { 
                it.copy(speed = speed, distance = distance, incline = incline, calories = calories, pulse = pulse, time = time, status = "Conectado")
            }
        } catch (e: Exception) {
            Log.e("BluetoothScanner", "Erro ao processar dados FTMS: ${e.message}")
        }
    }


}

package com.e314521.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.webkit.WebView

class MainActivity : ComponentActivity() {
    private val TAG = "MyApp"
    private lateinit var usbManager: UsbManager
    private var usbDevice: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private lateinit var statusText: TextView

    private lateinit var connectButton: Button
    private lateinit var sendButton980: Button
    private lateinit var sendButton990: Button
    private lateinit var deviceInfoText: TextView
    private lateinit var responseText: TextView
    private lateinit var permissionIntent: PendingIntent

    private var endpointOut: UsbEndpoint? = null
    private var endpointIn: UsbEndpoint? = null
    private var usbInterface: UsbInterface? = null



    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)
        sendButton980 = findViewById(R.id.sendButton)
        sendButton980.isEnabled = false
        sendButton990 = findViewById(R.id.sendButton990)
        sendButton990.isEnabled = false
        deviceInfoText = findViewById(R.id.deviceInfoText)
        responseText = findViewById(R.id.responseText)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        permissionIntent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
        )

        val filter = IntentFilter(ACTION_USB_PERMISSION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }


        connectButton.setOnClickListener {
            connectToDevice()
        }
        sendButton980.setOnClickListener {
            send980()
        }
        sendButton990.setOnClickListener {
            send990()
        }


    }
    private fun connectToDevice() {
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            statusText.text = "未找到USB设备"
            return
        }

        for (device in deviceList.values) {
            usbDevice = device
            if (usbManager.hasPermission(device)) {
                openConnection()
            } else {
                usbManager.requestPermission(device, permissionIntent)
            }
            break
        }
    }

    private fun openConnection() {
        usbDevice?.let { device ->
            // 查找合适的接口和端点
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                // 查找批量传输端点
                if (intf.endpointCount > 0) {
                    for (j in 0 until intf.endpointCount) {
                        val endpoint = intf.getEndpoint(j)
                        if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                                endpointOut = endpoint
                            } else if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                                endpointIn = endpoint
                            }
                        }
                    }

                    if (endpointOut != null || endpointIn != null) {
                        usbInterface = intf
                        break
                    }
                }
            }

            if (usbInterface == null) {
                statusText.text = "未找到合适的USB接口"
                return
            }

            connection = usbManager.openDevice(device)
            if (connection != null) {
                // 声明接口
                if (connection!!.claimInterface(usbInterface, true)) {
                    statusText.text = "已连接到设备: ${device.productName}"
                    deviceInfoText.text = "设备信息:\n" +
                            "厂商ID: ${device.vendorId}\n" +
                            "产品ID: ${device.productId}\n" +
                            "接口数量: ${device.interfaceCount}"
                    connectButton.text = "断开连接"
                    sendButton980.isEnabled = true
                    sendButton990.isEnabled = true
                    connectButton.setOnClickListener {
                        disconnectFromDevice()
                    }
                } else {
                    statusText.text = "无法声明USB接口"
                    connection = null
                }
            } else {
                statusText.text = "连接失败"
            }
        }
    }
    fun String.hexToBytes(): ByteArray {
        return this
            .replace("\\s".toRegex(), "")
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
    private fun send980() {
        endpointOut?.let { endpoint ->
            connection?.let { conn ->
                val stringArray = arrayOf("fe00ff0100000004000220005920","fe00ff01000000040004dbc87cff","da01fe673102009b76","ed02fdbab0")
                for (element in stringArray) {
                    val bytes = element.hexToBytes()
                    //val bytes = dataToSend.toByteArray()
                    val result = conn.bulkTransfer(endpoint, bytes, bytes.size, 1000)

                    if (result >= 0) {

                        statusText.text = "数据发送成功，发送了 $result 字节"
                        receiveData()

                    } else {
                        statusText.text = "数据发送失败，错误码: $result"
                        return
                    }
                }
            }
        } ?: run {
            statusText.text = "未找到输出端点"
        }
    }

    private fun send990() {
        endpointOut?.let { endpoint ->
            connection?.let { conn ->
                val stringArray = arrayOf("fe00ff0100000004000220005920",
                        "fe00ff0100000004000673c88648",
                        "da01fe01480200a3a4",
                        "ed02fdbab0")
                for (element in stringArray) {
                    val bytes = element.hexToBytes()
                    //val bytes = dataToSend.toByteArray()
                    val result = conn.bulkTransfer(endpoint, bytes, bytes.size, 1000)

                    if (result >= 0) {

                        statusText.text = "数据发送成功，发送了 $result 字节"
                        receiveData()

                    } else {
                        statusText.text = "数据发送失败，错误码: $result"
                        return
                    }
                }
            }
        } ?: run {
            statusText.text = "未找到输出端点"
        }
    }

    private fun receiveData() {
        endpointIn?.let { endpoint ->
            connection?.let { conn ->
                val buffer = ByteArray(4096)
                val result = conn.bulkTransfer(endpoint, buffer, buffer.size, 1000)

                if (result > 0) {
                    val receivedData = buffer.copyOf(result)
                    val response = String(receivedData)
                    Log.v(TAG, "接收到的数据:$response")
                    responseText.text = "接收到的数据:\n$response"
                } else if (result == 0) {
                    responseText.text = "接收到空数据"
                } else {
                    responseText.text = "接收数据失败，错误码: $result"
                }
            }
        }
    }

    private fun disconnectFromDevice() {
        connection?.close()
        connection = null
        statusText.text = "设备已断开"
        connectButton.text = "连接设备"
        sendButton980.isEnabled = false
        sendButton990.isEnabled = false
//        connectButton.setOnClickListener {
//            connectToDevice()
//        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        usbDevice?.let {
                            openConnection()
                        }
                    } else {
                        Toast.makeText(context, "USB权限被拒绝", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        connection?.close()
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.e314521.usb.USB_PERMISSION"
    }

}

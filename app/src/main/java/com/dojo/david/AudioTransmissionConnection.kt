package com.dojo.david

import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CountDownLatch

class TransmissionClient(val identity: String, val socket: Socket) {
    val input: InputStream = socket.getInputStream()
    val output: OutputStream = socket.getOutputStream()
}

class AudioTransmissionServer(private var port: String,
                              private val transmissionBufferSize: Int,
                              private val onDisconnection: (client: TransmissionClient) -> Unit = {}
                              ) {
    private val server = ServerSocket(port.toInt()) // TODO check failure
    private val clients = mutableListOf<TransmissionClient>()
    @Volatile var running = true

    companion object {
        const val TAG = "AudioTransmissionServer"
    }

    private fun emit(data: ByteArray, ignore: List<String> = listOf()) {
        clients.forEach {
            if (it.identity in ignore) {
                return@forEach
            } else {
                it.output.write(data)
                it.output.flush()
            }
        }
    }

    fun acceptConnections() {
        Thread {
            Log.i(TAG, "Server accepting connections at ${server.localSocketAddress}:$port")
            while (running) {
                val clientSocket = try {
                    server.accept()
                } catch (e: SocketException) {
                    stop()
                    return@Thread
                }

                Thread {
                    val clientReader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                    val deviceName = clientReader.readLine()
                    val client = TransmissionClient(identity = deviceName, clientSocket)
                    Log.i(TAG, "${client.identity} joined the chat")
                    clients.add(client)
                    listenClientTransmission(client)
                }.start()
            }
        }.start()
    }

    private fun listenClientTransmission(client: TransmissionClient) {
        Thread {
            while (true) {
                try {
                    val buffer = ByteArray(transmissionBufferSize)
                    val result = client.input.read(buffer)

                    // TODO lookup disconnection value
                    if (result == -1) {
                        handleDisconnection(client)
                        break
                    }

                    emit(buffer, ignore = listOf(client.identity))
                } catch (e: IOException) {
                    handleDisconnection(client)
                    break
                }
            }
        }.start()
    }

    private fun handleDisconnection(client: TransmissionClient) {
        clients.remove(client)

        if (!client.socket.isClosed) {
            client.socket.close()
        }

        Log.i(TAG,"${client.identity} just left the chat")
        onDisconnection(client)
    }

    fun stop() {
        if (!running) {
            return
        }

        running = false
        if (!server.isClosed) {
            server.close()
        }

        clients.forEach { if(!it.socket.isClosed) it.socket.close() }
        clients.clear()
    }
}


class AudioTransmissionClient(
    private val deviceName: String,
    private var host: String,
    private var port: String,
    private val transmissionBufferSize: Int,
    var onDisconnection: () -> Unit = {},
    var onStartSuccess: () -> Unit = {},
    var onStartFailure: (e: Exception) -> Unit = {}
)
{
    private lateinit var socket : Socket
    private lateinit var input : InputStream
    private lateinit var output : OutputStream
    @Volatile var running = false
        private set
    private val initLatch = CountDownLatch(1)

    init {
        startSocket()
    }

    private fun startSocket() {
        Thread {
            Looper.prepare()
            try {
                socket = Socket(InetAddress.getByName(host), port.toInt())
                input = socket.getInputStream()
                output = socket.getOutputStream()
                val writer = PrintWriter(output, true)
                // the first message after connection is always our device's name
                writer.println(deviceName)
                running = true
                initLatch.countDown()
                onStartSuccess()
            } catch (e: Exception) {
                onStartFailure(e)
            }
        }.start()
    }

    fun setTransmissionListener(onData: (data: ByteArray) -> Unit) {
        Thread {
            initLatch.await()
            Looper.prepare()
            while (running) {
                try {
                    val buffer = ByteArray(transmissionBufferSize)
                    val line = input.read(buffer)

                    // TODO check disconnection value
                    if (line == -1) {
                        onDisconnection()
                        stop()
                        break
                    }

                    onData(buffer)
                } catch (e: SocketException) {
                    onDisconnection()
                    stop()
                }
            }
        }.start()
    }

    fun send(data: ByteArray, onPostExecute: () -> Unit = {}) {
        Thread {
            output.write(data)
            onPostExecute()
        }.start()
    }

    fun stop() {
        if (!running) return

        running = false
        if (!socket.isClosed) {
            socket.close()
        }
    }

    companion object {
        val TAG = "AudioTransmissionClient"
    }
}
package com.dojo.david

import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CountDownLatch


class ConnectedClient(val identity: String, val socket: Socket) {
    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    val writer = PrintWriter(socket.getOutputStream(), true)
}

class RemoteChatServer(private var port: String) {
    private val TAG = "RemoteChatServer"
    private var server = ServerSocket(port.toInt()) // TODO check failure
    private val clients = mutableListOf<ConnectedClient>()
    @Volatile var running = true

    private fun emit(msg: String, ignore: List<String> = listOf()) {
        clients.forEach {
            if (it.identity in ignore) {
                return@forEach
            } else {
                it.writer.println(msg)
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
                    val client = ConnectedClient(identity = deviceName, clientSocket)
                    Log.i(TAG, "${client.identity} joined the chat")
                    clients.add(client)
                    emit("${client.identity} joined the chat!")
                    listenClientMessages(client)
                }.start()
            }
        }.start()
    }

    private fun listenClientMessages(client: ConnectedClient) {
        Thread {
            while (true) {
                try {
                    val line = client.reader.readLine()

                    if (line == null) {
                        handleDisconnection(client)
                        break
                    }

                    Log.i(TAG, "${client.identity}:$line")
                    emit("${client.identity}:$line", ignore = listOf(client.identity))

                } catch (e: IOException) {
                    handleDisconnection(client)
                    break
                }
            }
        }.start()
    }

    private fun handleDisconnection(client: ConnectedClient) {
        clients.remove(client)

        if (!client.socket.isClosed) {
            client.socket.close()
        }

        Log.i(TAG,"${client.identity} just left the chat")
        emit("${client.identity} just left the chat")
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


class RemoteChatClient(
    private val deviceName: String,
    private var host: String,
    private var port: String,
    var onDisconnection: () -> Unit = {},
    var onStartSuccess: () -> Unit = {},
    var onStartFailure: (e: Exception) -> Unit = {}
)
{
    private val TAG = "RemoteChatClient"
    private lateinit var socket : Socket
    private lateinit var reader : BufferedReader
    private lateinit var writer : PrintWriter
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
                socket = Socket(InetAddress.getByName(host), port.toInt()) // TODO possible failure
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(deviceName)
                running = true
                onStartSuccess()
                initLatch.countDown()
            } catch (e: Exception) {
                onStartFailure(e)
            }
        }.start()
    }

    fun setMessageListener(onMessage: (msg: ClientMessage) -> Unit) {
        Thread {
            initLatch.await()
            Looper.prepare()
            while (running) {
                try {
                    val line = reader.readLine()

                    if (line == null) {
                        onDisconnection()
                        stop()
                        break
                    }

                    val pieces = line.split(":")
                    if (pieces.size == 1) {
                        onMessage(ClientMessage("SERVER", line))
                    } else {
                        onMessage(ClientMessage(pieces[0], pieces[1]))
                    }
                } catch (e: SocketException) {
                    onDisconnection()
                    stop()
                }
            }
        }.start()
    }

    fun send(msg: String, onPostExecute: () -> Unit = {}) {
        Thread {
            writer.println(msg)
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
}


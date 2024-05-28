package com.dojo.david.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dojo.david.ClientMessage
import com.dojo.david.MainActivityViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTab(viewModel: MainActivityViewModel) {
    var textToSend by remember { mutableStateOf("") }

    var serverStateText by remember { mutableStateOf("") }
    var clientStateText by remember { mutableStateOf("") }

    serverStateText = if (viewModel.chatServerHasStarted) {
        "Server is running at"
    } else {
        "Server will run at"
    }

    clientStateText = if (viewModel.chatClientHasStarted) {
        "Device is connected to"
    } else {
        "Device will connect to"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp)
    ) {

        Row(modifier = Modifier.height(50.dp)) {
            Text(
                text = if (viewModel.chatServerHasStarted) "Server is active" else "Start server",
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                modifier = Modifier.align(Alignment.CenterVertically)
            )

            Spacer(Modifier.weight(1f))

            Switch(
                checked = viewModel.chatServerHasStarted,
                onCheckedChange = { viewModel.switchSocketServerState() },
            )
        }

        Column(modifier = Modifier.height(80.dp)) {
            Text(text = if (viewModel.chatServerHasStarted) "Server is running at" else "Server will run at")

            Row {
                TextField(
                    value = viewModel.serverAddress,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    enabled = false,
                    onValueChange = {},
                    modifier = Modifier.weight(0.7f)
                )

                Spacer(modifier = Modifier.weight(0.005f))

                TextField(
                    modifier = Modifier.weight(0.2f),
                    value = viewModel.serverPort,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    enabled = !viewModel.chatServerHasStarted,
                    onValueChange = {
                        viewModel.serverPort = it
                    },
                )
            }
        }

        Row(modifier = Modifier.height(50.dp)) {
            Text(
                text = if (viewModel.chatClientHasStarted) "Chat connection is active" else "Start connection",
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                modifier = Modifier.align(Alignment.CenterVertically)
            )

            Spacer(Modifier.weight(1f))

            Switch(
                checked = viewModel.chatClientHasStarted,
                onCheckedChange = {
                    viewModel.switchSocketClientState()
                    viewModel.show("Wait a moment")
                },
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }

        Column(modifier = Modifier.height(80.dp)) {
            Text(text = clientStateText)

            Row {
                TextField(
                    value = viewModel.targetChatAddress,
                    modifier = Modifier.weight(0.7f),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    enabled = !viewModel.chatClientHasStarted,
                    onValueChange = { viewModel.targetChatAddress = it },
                )

                Spacer(modifier = Modifier.weight(0.005f))

                TextField(
                    modifier = Modifier.weight(0.2f),
                    value = viewModel.targetChatPort,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    enabled = !viewModel.chatClientHasStarted,
                    onValueChange = {
                        viewModel.targetChatPort = it
                    },
                )
            }
        }

        // Chat Messages

        Text("Chat",
            fontSize = 19.sp,
            fontWeight = FontWeight.W600,
            modifier = Modifier.padding(top = 10.dp))

        LazyColumn(
            modifier = Modifier
                .weight(0.85f)
        ) {
            items(viewModel.uiChatMessages) { message ->
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .fillMaxWidth()
                        .padding(2.dp)
                ) {
                    Column {
                        Text(text = "${message.author}: ${message.content}")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextField(
                value = textToSend,
                onValueChange = { textToSend = it },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    viewModel.chatClient?.send(textToSend) {
                        // and then
                        viewModel.uiChatMessages.add(
                            ClientMessage(
                                viewModel.deviceName,
                                textToSend
                            )
                        )

                        textToSend = ""
                    }
                },
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.CenterVertically)
            ) {
                Text(text = "Send")
            }
        }
    }
}

package com.dojo.david.views

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dojo.david.MainActivityViewModel
import com.dojo.david.R


@Composable
fun WifiTab(viewModel: MainActivityViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp),
    ) {
        Row(modifier = Modifier.height(50.dp)) {
            Text(
                text = "Discover peers",
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                modifier = Modifier.align(Alignment.CenterVertically)
            )

            Spacer(Modifier.weight(1f))

            // Discovery On/Off toggle
            Switch(
                checked = viewModel.isP2pDiscoveryOn,
                onCheckedChange = { viewModel.switchP2pDiscoveryState() },
            )
        }

        Row(modifier = Modifier.height(50.dp)) {
            Text(
                text = "Leave group?",
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                modifier = Modifier.align(Alignment.CenterVertically)
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { viewModel.disconnect() }
            ) {
                Text(text = "Leave")
            }
        }

        Divider(color = Color.Black, thickness = 1.dp, modifier = Modifier.padding(1.dp))

        LazyColumn {
            items(viewModel.p2pAvailableUsers) { user ->
                Row(
                    modifier = Modifier
                        .padding(vertical = 4.5.dp)
                        .clickable {
                            if (user.status == WifiP2pDevice.AVAILABLE) {
                                viewModel.connectWith(user)
                            }
                        }
                        .fillMaxWidth()

                ) {
                    val iconId = when (user.status) {
                        WifiP2pDevice.CONNECTED -> R.drawable.baseline_cast_connected_24
                        WifiP2pDevice.AVAILABLE -> R.drawable.baseline_wifi_find_24
                        WifiP2pDevice.FAILED -> R.drawable.baseline_disabled_by_default_24
                        WifiP2pDevice.INVITED -> R.drawable.baseline_waiting_24
                        WifiP2pDevice.UNAVAILABLE -> R.drawable.baseline_signal_wifi_connected_no_internet_4_24
                        else -> R.drawable.baseline_disabled_by_default_24
                    }

                    val statusText = when (user.status) {
                        WifiP2pDevice.CONNECTED -> "Connected"
                        WifiP2pDevice.AVAILABLE -> "Available"
                        WifiP2pDevice.FAILED -> "Failed"
                        WifiP2pDevice.INVITED -> "Invited"
                        WifiP2pDevice.UNAVAILABLE -> "Unavailable"
                        else -> "Unknown"
                    }

                    Icon(
                        painter = painterResource(id = iconId),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(29.dp)
                    )

                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(
                            text = user.deviceName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W400
                        )

                        Text(
                            text = statusText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Blue,
                        )
                    }
                }
            }
        }
    }
}

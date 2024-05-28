package com.dojo.david.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.dojo.david.MainActivityViewModel
import com.dojo.david.R


@Composable
fun TabScreen(viewModel: MainActivityViewModel) {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("P2P Connection", "Data Transmission")

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    text = { Text(title) },
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    icon = {
                        when (index) {
                            0 -> Icon(painter = painterResource(
                                id = R.drawable.baseline_wifi_tethering_24),
                                contentDescription = null)

                            1 -> Icon(painter = painterResource(
                                id = R.drawable.baseline_connect_without_contact_24),
                                contentDescription = null)
                        }
                    }
                )
            }
        }

        when (tabIndex) {
            0 -> WifiTab(viewModel)
            1 -> ChatTab(viewModel)
        }
    }
}

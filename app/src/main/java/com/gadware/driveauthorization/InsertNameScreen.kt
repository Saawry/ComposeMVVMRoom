package com.gadware.driveauthorization

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gadware.driveauthorization.room.NameEntity
import com.gadware.driveauthorization.room.NameViewModel

@Composable
fun InsertName(viewModel: NameViewModel) {
    var name by remember { mutableStateOf("") }
    val names by viewModel.names.collectAsState(initial = emptyList())

     viewModel.names.collectAsState(initial = emptyList())

    val error by viewModel.error.collectAsState()


    Column(
        modifier = Modifier.padding(20.dp),
        ) {
        Text(
            text = "Insert Name",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 20.dp),
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            isError = error != null

        )
        if (error != null) {
            Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = { viewModel.saveName(name) },
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text("Save")
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        LazyColumn{
            items(names){ item->
                Text(text = item.name)
                VerticalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
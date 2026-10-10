package com.example.practica02.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ConversionHistorial(
    val id: String,
    val monto: Double,
    val origen: String,
    val destino: String,
    val resultado: Double,
    val fecha: Date?
)

@Composable
fun HistorialScreen(
    uid: String,
    onVolver: () -> Unit
) {
    val firestore = remember { FirebaseFirestore.getInstance() }

    var conversiones by remember {
        mutableStateOf<List<ConversionHistorial>>(emptyList())
    }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableStateOf(0) }

    LaunchedEffect(uid, intento) {
        cargando = true
        error = null

        try {
            val datos = firestore
                .collection("conversions")
                .whereEqualTo("uid", uid)
                .get(Source.SERVER)
                .await()

            conversiones = datos.documents.map { documento ->
                ConversionHistorial(
                    id = documento.id,
                    monto = documento.getDouble("amount")
                        ?: error("Falta el monto"),
                    origen = documento.getString("from")
                        ?: error("Falta la moneda de origen"),
                    destino = documento.getString("to")
                        ?: error("Falta la moneda de destino"),
                    resultado = documento.getDouble("result")
                        ?: error("Falta el resultado"),
                    fecha = documento.getTimestamp("createdAt")?.toDate()
                )
            }.sortedByDescending {
                it.fecha?.time ?: 0L
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "No se pudo cargar el historial: ${e.message}"
        } finally {
            cargando = false
        }
    }

    val formatoFecha = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Mi historial",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedButton(
            onClick = onVolver,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver al conversor")
        }

        Button(
            onClick = { intento++ },
            enabled = !cargando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Actualizar historial")
        }

        when {
            cargando -> {
                Text("Cargando historial...")
            }

            error != null -> {
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error
                )
            }

            conversiones.isEmpty() -> {
                Text("Todavía no has realizado conversiones.")
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = conversiones,
                        key = { it.id }
                    ) { conversion ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%.2f %s → %.2f %s",
                                        conversion.monto,
                                        conversion.origen,
                                        conversion.resultado,
                                        conversion.destino
                                    ),
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    text = conversion.fecha?.let {
                                        formatoFecha.format(it)
                                    } ?: "Fecha pendiente"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
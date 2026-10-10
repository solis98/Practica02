package com.example.practica02.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.practica02.model.Moneda
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

@Composable
fun ConversionScreen(
    uid: String,
    onCerrarSesion: () -> Unit,
    onVerHistorial: () -> Unit
) {
    val firestore = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    var monedas by remember { mutableStateOf<List<Moneda>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }
    var intentoCarga by remember { mutableStateOf(0) }

    var montoTexto by rememberSaveable { mutableStateOf("") }
    var origen by rememberSaveable { mutableStateOf("USD") }
    var destino by rememberSaveable { mutableStateOf("PEN") }

    var resultado by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid, intentoCarga) {
        cargando = true
        error = null

        try {
            val datos = firestore
                .collection("rates")
                .get(Source.SERVER)
                .await()

            val lista = datos.documents.map { documento ->
                val nombre = documento.getString("name")
                    ?: throw Exception("Falta name en ${documento.id}")

                val tasa = (documento.get("rate") as? Number)?.toDouble()
                    ?: throw Exception("rate debe ser numérico en ${documento.id}")

                require(tasa.isFinite() && tasa > 0) {
                    "La tasa de ${documento.id} debe ser mayor que cero."
                }

                Moneda(
                    codigo = documento.id,
                    nombre = nombre,
                    tasa = tasa
                )
            }.sortedBy { it.codigo }

            val requeridas = setOf("USD", "EUR", "PEN", "GBP", "JPY")

            require(lista.map { it.codigo }.containsAll(requeridas)) {
                "Revisa que rates contenga USD, EUR, PEN, GBP y JPY."
            }

            monedas = lista
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            monedas = emptyList()
            error = "No se pudieron cargar las monedas: ${e.message}"
        } finally {
            cargando = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Conversor de monedas",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Tasas fijas de ejemplo para la práctica.")

        if (cargando) {
            Text("Cargando monedas...")
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (!cargando && monedas.isEmpty()) {
            Button(
                onClick = { intentoCarga++ }
            ) {
                Text("Reintentar carga")
            }
        }

        if (monedas.isNotEmpty()) {
            OutlinedTextField(
                value = montoTexto,
                onValueChange = {
                    montoTexto = it
                    resultado = ""
                    mensaje = ""
                    error = null
                },
                label = { Text("Monto a convertir") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                enabled = !guardando,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SelectorMoneda(
                titulo = "Moneda de origen",
                seleccion = origen,
                monedas = monedas,
                habilitado = !guardando,
                onSeleccionar = {
                    origen = it
                    resultado = ""
                    mensaje = ""
                    error = null
                }
            )

            SelectorMoneda(
                titulo = "Moneda de destino",
                seleccion = destino,
                monedas = monedas,
                habilitado = !guardando,
                onSeleccionar = {
                    destino = it
                    resultado = ""
                    mensaje = ""
                    error = null
                }
            )

            Button(
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val monto = montoTexto
                        .trim()
                        .replace(',', '.')
                        .toDoubleOrNull()

                    error = null
                    resultado = ""
                    mensaje = ""

                    if (monto == null || !monto.isFinite() || monto <= 0) {
                        error = "Ingresa un monto válido mayor que cero."
                    } else {
                        val tasaOrigen = monedas.first {
                            it.codigo == origen
                        }.tasa

                        val tasaDestino = monedas.first {
                            it.codigo == destino
                        }.tasa

                        val convertido = monto / tasaOrigen * tasaDestino

                        if (!convertido.isFinite()) {
                            error = "El monto es demasiado grande."
                        } else {
                            resultado = String.format(
                                Locale.US,
                                "%.2f %s equivalen a %.2f %s",
                                monto,
                                origen,
                                convertido,
                                destino
                            )

                            val conversion = hashMapOf<String, Any>(
                                "uid" to uid,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "amount" to monto,
                                "from" to origen,
                                "to" to destino,
                                "result" to convertido
                            )

                            guardando = true

                            scope.launch {
                                try {
                                    check(
                                        FirebaseAuth.getInstance()
                                            .currentUser?.uid == uid
                                    ) {
                                        "La sesión ha cambiado."
                                    }

                                    firestore
                                        .collection("conversions")
                                        .add(conversion)
                                        .await()

                                    mensaje = "Conversión guardada en el historial."
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error = "El cálculo se realizó, pero no se " +
                                            "pudo guardar: ${e.message}"
                                } finally {
                                    guardando = false
                                }
                            }
                        }
                    }
                }
            ) {
                Text(
                    if (guardando) "Guardando..."
                    else "Convertir"
                )
            }

            if (resultado.isNotEmpty()) {
                Text(
                    text = resultado,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (mensaje.isNotEmpty()) {
                Text(mensaje)
            }
        }

        OutlinedButton(
            onClick = onVerHistorial,
            enabled = !guardando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ver mi historial")
        }

        OutlinedButton(
            onClick = onCerrarSesion,
            enabled = !guardando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesión")
        }
    }
}

@Composable
private fun SelectorMoneda(
    titulo: String,
    seleccion: String,
    monedas: List<Moneda>,
    habilitado: Boolean,
    onSeleccionar: (String) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }

    Column {
        Text(titulo)

        Box {
            OutlinedButton(
                onClick = { abierto = true },
                enabled = habilitado,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("$seleccion ▼")
            }

            DropdownMenu(
                expanded = abierto,
                onDismissRequest = { abierto = false }
            ) {
                monedas.forEach { moneda ->
                    DropdownMenuItem(
                        text = {
                            Text("${moneda.codigo} — ${moneda.nombre}")
                        },
                        onClick = {
                            onSeleccionar(moneda.codigo)
                            abierto = false
                        }
                    )
                }
            }
        }
    }
}
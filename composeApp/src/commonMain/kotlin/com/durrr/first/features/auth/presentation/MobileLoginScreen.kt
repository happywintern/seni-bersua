package com.durrr.first.features.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.durrr.first.data.repo.ServerAuthRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppInfoLine
import com.durrr.first.ui.design.AppPageContainer
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.Dimens
import kotlinx.coroutines.launch

private enum class LoginRole { OWNER, CASHIER }

@Composable
fun MobileLoginScreen(
    settingsRepository: SettingsRepository,
    serverAuthRepository: ServerAuthRepository? = null,
    onLoginSuccess: () -> Unit,
    onRequireSetup: () -> Unit,
    onBackgroundInfo: (String) -> Unit = {},
) {
    var storeName by remember { mutableStateOf("SuCash") }
    var outletId by remember { mutableStateOf(SettingsRepository.DEFAULT_OUTLET_ID) }
    var ownerName by remember { mutableStateOf("Owner") }
    var cashierName by remember { mutableStateOf("Cashier") }
    var selectedRole by remember { mutableStateOf(LoginRole.CASHIER) }
    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var serverPairingCode by remember { mutableStateOf("") }
    var ownerSetupPin by remember { mutableStateOf("") }
    var ownerSetupPinVisible by remember { mutableStateOf(false) }
    var showSetupUnlock by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        storeName = settingsRepository
            .loadReceiptConfig()
            .storeName
            .ifBlank { "SuCash" }
        outletId = settingsRepository.resolveOutletId()
        ownerName = settingsRepository
            .getOwnerName()
            ?.ifBlank { null }
            ?: "Owner"
        cashierName = settingsRepository
            .getDefaultCashierName()
            ?.ifBlank { null }
            ?: "Cashier"
    }

    val ownerPinReady = settingsRepository.hasOwnerPinConfigured()
    val cashierPinReady = settingsRepository.hasCashierPinConfigured()
    val anyPinReady = settingsRepository.hasAnyLoginPinConfigured()

    fun roleLabel(role: LoginRole): String {
        return when (role) {
            LoginRole.OWNER -> "Owner"
            LoginRole.CASHIER -> "Cashier"
        }
    }

    fun roleName(role: LoginRole): String {
        return when (role) {
            LoginRole.OWNER -> ownerName
            LoginRole.CASHIER -> cashierName
        }
    }

    fun roleReady(role: LoginRole): Boolean {
        return when (role) {
            LoginRole.OWNER -> ownerPinReady
            LoginRole.CASHIER -> cashierPinReady
        }
    }

    fun tryLogin() {
        if (!anyPinReady) {
            message = "PIN belum di-setup. Jalankan setup dulu."
            return
        }
        if (pin.length !in 4..6 || pin.any { !it.isDigit() }) {
            message = "PIN harus 4 sampai 6 digit angka."
            return
        }
        val ok = when (selectedRole) {
            LoginRole.OWNER -> settingsRepository.verifyOwnerPin(pin) && settingsRepository.setActiveUserOwner()
            LoginRole.CASHIER -> settingsRepository.verifyCashierPin(pin) && settingsRepository.setActiveUserCashier()
        }
        if (!ok) {
            message = "PIN ${roleLabel(selectedRole)} salah."
            return
        }
        pin = ""
        message = null
        val configuredBaseUrl = settingsRepository.getOptionalServerBaseUrl()
        if (!configuredBaseUrl.isNullOrBlank() && serverAuthRepository != null) {
            val scopedOutletId = outletId
            scope.launch {
                val currentBearer = settingsRepository
                    .getActiveUserServerApiBearerToken(scopedOutletId)
                    ?.trim()
                    .orEmpty()
                if (serverPairingCode.isBlank() && currentBearer.isBlank()) {
                    onBackgroundInfo(
                        "Login lokal berhasil. Pairing code dari server admin dibutuhkan untuk aktivasi session server."
                    )
                } else {
                    runCatching {
                        serverAuthRepository.bootstrapSessionForActiveUser(
                            baseUrl = configuredBaseUrl,
                            outletId = scopedOutletId,
                            pairingCode = serverPairingCode.trim().ifBlank { null },
                        )
                    }.onSuccess { session ->
                        if (session != null) {
                            serverPairingCode = ""
                            onBackgroundInfo(
                                "Server session aktif: ${session.role} • ${session.outletId}"
                            )
                        }
                    }.onFailure { error ->
                        onBackgroundInfo(
                            "Login lokal berhasil. Session server belum aktif: ${error.message ?: "Unknown error"}"
                        )
                    }
                }
            }
        }
        onLoginSuccess()
    }

    @Composable
    fun RoleButton(role: LoginRole, modifier: Modifier = Modifier) {
        val selected = selectedRole == role
        val label = roleLabel(role)
        val enabled = roleReady(role)
        if (selected) {
            Button(
                modifier = modifier,
                onClick = { selectedRole = role },
                enabled = enabled,
            ) {
                Text(label)
            }
        } else {
            FilledTonalButton(
                modifier = modifier,
                onClick = { selectedRole = role },
                enabled = enabled,
            ) {
                Text(label)
            }
        }
    }

    AppPageContainer {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    AppSectionHeader("Login Kasir", "Masuk dulu sebelum pakai POS di device ini.")
                    AppInfoLine("Toko", storeName)
                    AppInfoLine("Outlet", outletId)
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                    ) {
                        RoleButton(role = LoginRole.CASHIER, modifier = Modifier.weight(1f))
                        RoleButton(role = LoginRole.OWNER, modifier = Modifier.weight(1f))
                    }

                    AppInfoLine("Role", roleLabel(selectedRole))
                    AppInfoLine("Nama", roleName(selectedRole))
                    if (!roleReady(selectedRole)) {
                        Text(
                            text = "Akun ${roleLabel(selectedRole)} belum punya PIN.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    AppInfoLine("PIN ${roleLabel(selectedRole)}", "")
                    PinBlockInput(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                        visible = pinVisible,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${pin.length}/6 digit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { pinVisible = !pinVisible }) {
                            Text(if (pinVisible) "Hide" else "Show")
                        }
                    }

                    Button(
                        onClick = ::tryLogin,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = roleReady(selectedRole),
                    ) {
                        Text("Masuk")
                    }

                    // if (!settingsRepository.getOptionalServerBaseUrl().isNullOrBlank()) {
                    //     OutlinedTextField(
                    //         value = serverPairingCode,
                    //         onValueChange = { serverPairingCode = it.uppercase().take(16) },
                    //         modifier = Modifier.fillMaxWidth(),
                    //         label = { Text("Server Pairing Code") },
                    //         placeholder = { Text("Isi kode dari server admin") },
                    //         singleLine = true,
                    //     )
                    //     Text(
                    //         text = "Pairing code dibuat dari server admin. Mobile hanya redeem. Kosongkan kalau hanya pakai mode lokal.",
                    //         style = MaterialTheme.typography.bodySmall,
                    //         color = MaterialTheme.colorScheme.onSurfaceVariant,
                    //     )
                    // }

                    // TextButton(
                    //     onClick = { showSetupUnlock = !showSetupUnlock },
                    //     modifier = Modifier.fillMaxWidth(),
                    // ) {
                    //     Text(if (showSetupUnlock) "Batal Buka Setup" else "Buka Setup (Owner)")
                    // }

                    // if (showSetupUnlock) {
                    //     OutlinedTextField(
                    //         value = ownerSetupPin,
                    //         onValueChange = { ownerSetupPin = it.filter(Char::isDigit).take(6) },
                    //         modifier = Modifier.fillMaxWidth(),
                    //         label = { Text("Owner PIN untuk Setup") },
                    //         singleLine = true,
                    //         keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    //         visualTransformation = if (ownerSetupPinVisible) {
                    //             VisualTransformation.None
                    //         } else {
                    //             PasswordVisualTransformation()
                    //         },
                    //         trailingIcon = {
                    //             TextButton(onClick = { ownerSetupPinVisible = !ownerSetupPinVisible }) {
                    //                 Text(if (ownerSetupPinVisible) "Hide" else "Show")
                    //             }
                    //         },
                    //         supportingText = {
                    //             Text("${ownerSetupPin.length}/6 digit")
                    //         },
                    //     )
                    //     Button(
                    //         onClick = {
                    //             if (!ownerPinReady) {
                    //                 message = "Owner belum punya PIN. Lengkapi setup owner dulu."
                    //                 return@Button
                    //             }
                    //             if (!settingsRepository.verifyOwnerPin(ownerSetupPin)) {
                    //                 message = "Owner PIN salah."
                    //                 return@Button
                    //             }
                    //             ownerSetupPin = ""
                    //             ownerSetupPinVisible = false
                    //             showSetupUnlock = false
                    //             message = null
                    //             onRequireSetup()
                    //         },
                    //         modifier = Modifier.fillMaxWidth(),
                    //         enabled = ownerPinReady,
                    //     ) {
                    //         Text("Lanjut ke Setup")
                    //     }
                    // }

                    // if (!message.isNullOrBlank()) {
                    //     Text(
                    //         text = message.orEmpty(),
                    //         modifier = Modifier.padding(top = Dimens.xxs),
                    //         style = MaterialTheme.typography.bodySmall,
                    //         color = MaterialTheme.colorScheme.error,
                    //     )
                    // }
                }
            }
        }
    }
}

@Composable
private fun PinBlockInput(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
    maxDigits: Int = 6,
) {
    BasicTextField(
        value = value,
        onValueChange = { next ->
            onValueChange(next.filter(Char::isDigit).take(maxDigits))
        },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
            ) {
                repeat(maxDigits) { index ->
                    val hasDigit = index < value.length
                    val cellValue = when {
                        !hasDigit -> ""
                        visible -> value[index].toString()
                        else -> "•"
                    }
                    val focusedCell = index == value.length.coerceAtMost(maxDigits - 1)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .size(width = 50.dp, height = 56.dp)
                            .border(
                                width = if (focusedCell) 2.dp else 1.dp,
                                color = if (focusedCell) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = RoundedCornerShape(12.dp),
                            )
                            .background(
                                color = if (focusedCell) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = cellValue,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
    )
}

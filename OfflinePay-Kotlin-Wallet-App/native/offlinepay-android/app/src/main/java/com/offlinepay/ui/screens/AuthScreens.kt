package com.offlinepay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinepay.BuildConfig
import com.offlinepay.ui.components.BackAction
import com.offlinepay.ui.components.PrimaryAction
import com.offlinepay.ui.components.WaveArt
import com.offlinepay.ui.theme.GlacierBlue
import com.offlinepay.ui.theme.TrustIndigo

@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF071B33), Color(0xFF0C3362), Color(0xFF09203E))))
    ) {
        WaveArt(Modifier.fillMaxSize(), dark = true)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(Color(0xFF0E86EE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Text("OfflinePay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(190.dp)
                        .background(Color(0xFF0E86EE).copy(alpha = .22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.AccountBalanceWallet,
                        contentDescription = null,
                        tint = GlacierBlue,
                        modifier = Modifier.size(90.dp)
                    )
                }
                Text(
                    "Your wallet,\nready when you are.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 31.sp,
                    lineHeight = 37.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 30.dp)
                )
                Text(
                    "Pay online or use your reserved offline balance.",
                    color = Color(0xFFC9D9ED),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Column(Modifier.fillMaxWidth()) {
                PrimaryAction("Get Started", onStart)
                Text(
                    "Secure payments, with a clear view of every balance.",
                    color = Color(0xFFC9D9ED),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 15.dp)
                )
            }
        }
    }
}

@Composable
fun SignInChoiceScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    AuthFrame(title = "Welcome to OfflinePay", subtitle = "Sign up or log in with your phone number.", onBack = onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(82.dp).background(Color(0xFF0E86EE).copy(alpha = .2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    tint = GlacierBlue,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            PrimaryAction("Sign up / Log in", onContinue)
            Text(
                "By continuing, you agree to the app's terms and privacy notice.",
                color = Color(0xFFB9C9DC),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 18.dp)
            )
        }
    }
}

@Composable
fun PhoneEntryScreen(
    phoneNumber: String,
    onPhoneChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    AuthFrame(title = "Your phone number", subtitle = "We'll send a one-time code to verify your number.", onBack = onBack) {
        Column(Modifier.fillMaxWidth()) {
            Text("PHONE NUMBER", color = Color(0xFFB9C9DC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                leadingIcon = { Text("+91", color = Color.White, fontWeight = FontWeight.SemiBold) },
                placeholder = { Text("Enter phone number", color = Color(0xFF8FA5BF)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = authFieldColors()
            )
            Spacer(Modifier.height(22.dp))
            PrimaryAction("Continue", onContinue, enabled = phoneNumber.length == 10, trailingArrow = true)
            if (BuildConfig.DEMO_MODE) {
                Text(
                    "DEVELOPMENT MODE • Phone verification is ready for demo.",
                    color = GlacierBlue,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 17.dp)
                )
            }
        }
    }
}

@Composable
fun OtpEntryScreen(
    phoneNumber: String,
    code: String,
    onCodeChange: (String) -> Unit,
    onBack: () -> Unit,
    onVerify: () -> Unit
) {
    AuthFrame(title = "Verify your number", subtitle = "Enter the one-time code for +91 $phoneNumber.", onBack = onBack) {
        Column(Modifier.fillMaxWidth()) {
            Text("VERIFICATION CODE", color = Color(0xFFB9C9DC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Lock, null, tint = GlacierBlue) },
                placeholder = { Text("6-digit code", color = Color(0xFF8FA5BF)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = authFieldColors()
            )
            if (BuildConfig.DEMO_MODE) {
                Text(
                    "DEVELOPMENT OTP: 123456",
                    color = GlacierBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Spacer(Modifier.height(22.dp))
            PrimaryAction("Verify and continue", onVerify, enabled = code.length == 6, trailingArrow = true)
        }
    }
}

@Composable
fun ProfileSetupScreen(
    name: String,
    email: String,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val isEmailValid = email.isNotBlank() && email.contains("@") && email.contains(".")
    val isNameValid = name.trim().length >= 2

    AuthFrame(
        title = "Personalize Account",
        subtitle = "Please enter your name and email ID so we can greet you and personalize receipts.",
        onBack = onBack
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text("FULL NAME", color = Color(0xFFB9C9DC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.Person,
                        contentDescription = null,
                        tint = GlacierBlue
                    )
                },
                placeholder = { Text("e.g. Diya Joshy", color = Color(0xFF8FA5BF)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                colors = authFieldColors()
            )

            Spacer(Modifier.height(18.dp))

            Text("EMAIL ADDRESS", color = Color(0xFFB9C9DC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.Email,
                        contentDescription = null,
                        tint = GlacierBlue
                    )
                },
                placeholder = { Text("e.g. diya@example.com", color = Color(0xFF8FA5BF)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = authFieldColors()
            )

            Spacer(Modifier.height(28.dp))

            PrimaryAction(
                "Complete Setup",
                onClick = onComplete,
                enabled = isNameValid && isEmailValid,
                trailingArrow = true
            )

            Text(
                "Your name and email will be displayed on the home screen and attached to payment receipts.",
                color = Color(0xFFB9C9DC),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun AuthFrame(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF06172C), Color(0xFF0B2D57), Color(0xFF0A1C35))))
            .imePadding()
    ) {
        WaveArt(Modifier.fillMaxSize(), dark = true)
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
            BackAction(onBack, dark = true)
            Spacer(Modifier.height(32.dp))
            Text(title, color = Color.White, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFB9C9DC), fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(32.dp))
            content()
        }
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = GlacierBlue,
    unfocusedBorderColor = Color(0xFF476182),
    cursorColor = GlacierBlue,
    focusedContainerColor = TrustIndigo.copy(alpha = .6f),
    unfocusedContainerColor = TrustIndigo.copy(alpha = .45f)
)

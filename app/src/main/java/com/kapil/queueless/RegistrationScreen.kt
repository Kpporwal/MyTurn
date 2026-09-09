package com.kapil.queueless

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.TimeUnit

private enum class RegistrationMethod {
    EMAIL,
    MOBILE
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

@Composable
fun RegistrationScreen(
    onRegisterSuccess: () -> Unit,
    onBackClick: () -> Unit,
    role: String = "customer",
    onEmailVerificationSent: () -> Unit = onRegisterSuccess
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    val activity = context.findActivity()

    var method by remember { mutableStateOf(RegistrationMethod.EMAIL) }
    var currentStep by remember { mutableStateOf(1) }

    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }

    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var autoVerifiedCredential by remember { mutableStateOf<PhoneAuthCredential?>(null) }

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var otpSent by remember { mutableStateOf(false) }
    var emailVerificationPending by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val isBusiness = role == "business"
    val accent = if (isBusiness) Color(0xFF249B68) else Color(0xFF6C3CEB)
    val background = Color(0xFFF9F7FF)
    val darkText = Color(0xFF252238)
    val grayText = Color(0xFF6D6875)

    fun cleanPhone(value: String): String {
        val digits = value.filter { it.isDigit() }
        return when {
            value.trim().startsWith("+") -> "+$digits"
            digits.length == 10 -> "+91$digits"
            else -> value.trim()
        }
    }

    fun saveUserProfile(
        userId: String,
        userEmail: String?,
        userPhone: String?,
        onDone: () -> Unit
    ) {
        val data = hashMapOf<String, Any>(
            "role" to role,
            "email" to (userEmail ?: ""),
            "phoneNumber" to (userPhone ?: ""),
            "uid" to userId,
            "createdAt" to Timestamp.now()
        )

        db.collection("users")
            .document(userId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                onDone()
            }
            .addOnFailureListener {
                isLoading = false
                message = "Account created, but profile setup failed. Please try again."
            }
    }

    fun startPhoneVerification() {
        val normalizedPhone = cleanPhone(mobile)

        if (!normalizedPhone.startsWith("+") || normalizedPhone.length < 10) {
            message = "Enter a valid mobile number with country code, e.g. +919999999999."
            return
        }

        if (activity == null) {
            message = "Unable to start phone verification. Please try again."
            return
        }

        autoVerifiedCredential = null
        verificationId = null
        otpSent = false
        isLoading = true
        message = ""

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                autoVerifiedCredential = credential
                otpSent = true
                isLoading = false
                currentStep = 2
                message = "Phone number verified automatically. Complete registration below."
            }

            override fun onVerificationFailed(e: FirebaseException) {
                isLoading = false
                message = e.message
                    ?: "Could not send OTP. Please check the number and try again."
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = id
                resendToken = token
                otpSent = true
                isLoading = false
                currentStep = 2
                message = "OTP sent to $normalizedPhone."
            }
        }

        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(normalizedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        resendToken?.let { builder.setForceResendingToken(it) }

        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    fun sendVerificationEmailAgain() {
        val savedEmail = email.trim()
        if (savedEmail.isBlank() || password.isBlank()) {
            message = "Please enter your email and password first."
            return
        }

        isLoading = true
        message = ""

        auth.signInWithEmailAndPassword(savedEmail, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    isLoading = false
                    message = "Unable to access your account. Please try again."
                    return@addOnSuccessListener
                }

                if (user.isEmailVerified) {
                    auth.signOut()
                    isLoading = false
                    emailVerificationPending = false
                    message = "Email is already verified. You can log in now."
                    onEmailVerificationSent()
                    return@addOnSuccessListener
                }

                user.sendEmailVerification()
                    .addOnSuccessListener {
                        auth.signOut()
                        isLoading = false
                        message = "Verification email sent again. Check Inbox and Spam."
                    }
                    .addOnFailureListener { error ->
                        auth.signOut()
                        isLoading = false
                        message = error.message ?: "Could not resend verification email."
                    }
            }
            .addOnFailureListener { error ->
                isLoading = false
                message = error.message ?: "Could not resend verification email."
            }
    }

    fun checkEmailVerification() {
        val savedEmail = email.trim()
        if (savedEmail.isBlank() || password.isBlank()) {
            message = "Please enter your email and password first."
            return
        }

        isLoading = true
        message = "Checking email verification..."

        auth.signInWithEmailAndPassword(savedEmail, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    isLoading = false
                    message = "Unable to check your account. Please try again."
                    return@addOnSuccessListener
                }

                user.reload()
                    .addOnSuccessListener {
                        val refreshedUser = auth.currentUser
                        if (refreshedUser?.isEmailVerified == true) {
                            saveUserProfile(
                                userId = refreshedUser.uid,
                                userEmail = refreshedUser.email,
                                userPhone = refreshedUser.phoneNumber
                            ) {
                                isLoading = false
                                emailVerificationPending = false
                                onRegisterSuccess()
                            }
                        } else {
                            auth.signOut()
                            isLoading = false
                            message = "Email is not verified yet. Open the verification link first."
                        }
                    }
                    .addOnFailureListener { error ->
                        auth.signOut()
                        isLoading = false
                        message = error.message ?: "Could not refresh verification status."
                    }
            }
            .addOnFailureListener { error ->
                isLoading = false
                message = error.message ?: "Could not check your account."
            }
    }

    fun completeEmailRegistration() {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            message = "Enter a valid email address."
            return
        }

        if (password.length < 6) {
            message = "Password must be at least 6 characters."
            return
        }

        if (password != confirmPassword) {
            message = "Passwords do not match."
            return
        }

        isLoading = true
        message = ""

        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                val user = auth.currentUser

                if (user == null) {
                    isLoading = false
                    message = "Account creation failed. Please try again."
                    return@addOnSuccessListener
                }

                user.sendEmailVerification()
                    .addOnSuccessListener {
                        saveUserProfile(
                            userId = user.uid,
                            userEmail = user.email,
                            userPhone = user.phoneNumber
                        ) {
                            auth.signOut()
                            isLoading = false
                            emailVerificationPending = true
                            message = "Verification email sent. Check your Inbox and Spam folder."
                        }
                    }
                    .addOnFailureListener { error ->
                        isLoading = false
                        message = error.message
                            ?: "Could not send verification email."
                    }
            }
            .addOnFailureListener { error ->
                isLoading = false
                message = error.message ?: "Registration failed."
            }
    }

    fun completePhoneRegistration() {
        if (!otpSent) {
            message = "Please send the OTP first."
            return
        }

        if (
            autoVerifiedCredential == null &&
            (otp.length != 6 || !otp.all { it.isDigit() })
        ) {
            message = "Enter the 6-digit OTP."
            return
        }

        if (password.length < 6) {
            message = "Password must be at least 6 characters."
            return
        }

        if (password != confirmPassword) {
            message = "Passwords do not match."
            return
        }

        val credential = autoVerifiedCredential
            ?: verificationId?.let { PhoneAuthProvider.getCredential(it, otp) }

        if (credential == null) {
            message = "OTP session expired. Please send the OTP again."
            return
        }

        isLoading = true
        message = ""

        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user

                if (user == null) {
                    isLoading = false
                    message = "Phone verification failed. Please try again."
                    return@addOnSuccessListener
                }

                db.collection("users")
                    .document(user.uid)
                    .get()
                    .addOnSuccessListener { existing ->
                        val existingRole = existing.getString("role")

                        if (existing.exists() &&
                            !existingRole.isNullOrBlank() &&
                            existingRole != role
                        ) {
                            auth.signOut()
                            isLoading = false
                            message = if (isBusiness) {
                                "This phone number is already registered as a customer account."
                            } else {
                                "This phone number is already registered as a business account."
                            }
                            return@addOnSuccessListener
                        }

                        /*
                         * Firebase phone authentication creates a phone-credential
                         * account. The password fields are validated for the requested
                         * UX, but they do not become a Firebase password credential
                         * because no email/password credential exists for a phone-only
                         * account. The actual identity verification is the Firebase OTP.
                         */
                        saveUserProfile(
                            userId = user.uid,
                            userEmail = user.email,
                            userPhone = user.phoneNumber ?: cleanPhone(mobile)
                        ) {
                            isLoading = false
                            onRegisterSuccess()
                        }
                    }
                    .addOnFailureListener { error ->
                        isLoading = false
                        message = error.message
                            ?: "Could not verify the existing account."
                    }
            }
            .addOnFailureListener { error ->
                isLoading = false
                message = error.message ?: "Invalid OTP. Please try again."
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F3FF))
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (isBusiness) Color(0xFF087B55) else Color(0xFF4F18D8),
                            if (isBusiness) Color(0xFF24B47A) else Color(0xFF8B5CF6),
                            Color(0xFFDCCBFF)
                        )
                    )
                )
        ) {
            TextButton(
                onClick = {
                    if (currentStep == 2 && !emailVerificationPending) {
                        currentStep = 1
                        message = ""
                    } else onBackClick()
                },
                modifier = Modifier.padding(start = 18.dp, top = 8.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("‹  Back", color = Color.White, fontSize = 13.sp) }

            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 25.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                        Text(if (isBusiness) "🏪" else "Q", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (isBusiness) "Create your business" else "Create your account", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Let's get you started.", color = Color.White.copy(alpha = .82f), fontSize = 11.sp)
                    }
                }
            }
        }

        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-22).dp),
            RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Registration method", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = darkText)
                Spacer(Modifier.height(11.dp))
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFF2EEFA), RoundedCornerShape(15.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { method = RegistrationMethod.EMAIL; currentStep = 1; emailVerificationPending = false; message = ""; isLoading = false },
                        Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (method == RegistrationMethod.EMAIL) accent else Color.Transparent, contentColor = if (method == RegistrationMethod.EMAIL) Color.White else darkText),
                        elevation = null, contentPadding = PaddingValues(0.dp)
                    ) { Text("Email", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { method = RegistrationMethod.MOBILE; currentStep = 1; emailVerificationPending = false; message = ""; isLoading = false },
                        Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (method == RegistrationMethod.MOBILE) accent else Color.Transparent, contentColor = if (method == RegistrationMethod.MOBILE) Color.White else darkText),
                        elevation = null, contentPadding = PaddingValues(0.dp)
                    ) { Text("Mobile", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }

                Spacer(Modifier.height(17.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    repeat(2) { index ->
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(if (currentStep >= index + 1) accent else Color(0xFFE8E1F2)), contentAlignment = Alignment.Center) { Text("${index + 1}", color = if (currentStep >= index + 1) Color.White else grayText, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        if (index == 0) Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(4.dp)).background(if (currentStep >= 2) accent else Color(0xFFE8E1F2)))
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Your details", fontSize = 9.sp, color = accent, fontWeight = FontWeight.Bold); Text(if (emailVerificationPending) "Verify email" else if (currentStep == 2) "Security" else "Contact", fontSize = 9.sp, color = grayText) }
                Spacer(Modifier.height(18.dp))

                if (method == RegistrationMethod.EMAIL) {
                    if (emailVerificationPending) {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFFF6F1FF)).padding(20.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFE5D8FF)), contentAlignment = Alignment.Center) { Text("✉️", fontSize = 28.sp) }
                                Spacer(Modifier.height(12.dp))
                                Text("Verify your email", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                                Spacer(Modifier.height(6.dp))
                                Text("We've sent a verification link to", fontSize = 11.sp, color = grayText)
                                Spacer(Modifier.height(4.dp))
                                Text(email.trim(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(12.dp))
                                Text("Open the link in your email. If you don't see it, check Spam or tap Resend.", fontSize = 10.sp, color = grayText, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { checkEmailVerification() }, enabled = !isLoading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                                    if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Row(verticalAlignment = Alignment.CenterVertically) { Text("I've verified my email", fontWeight = FontWeight.Bold); Spacer(Modifier.width(7.dp)); Text("→", fontSize = 17.sp) }
                                }
                                Spacer(Modifier.height(5.dp))
                                TextButton(onClick = { sendVerificationEmailAgain() }, enabled = !isLoading) { Text("Resend verification email", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    } else if (currentStep == 1) {
                        OutlinedTextField(email, { email = it; message = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Email address") }, placeholder = { Text("you@example.com") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(15.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) message = "Enter a valid email address." else { message = ""; currentStep = 2 } }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Continue", fontWeight = FontWeight.Bold); Spacer(Modifier.width(7.dp)); Text("→", fontSize = 17.sp) } }
                    } else {
                        Text("Secure your account", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                        Spacer(Modifier.height(4.dp)); Text(email.trim(), fontSize = 10.sp, color = grayText)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(password, { password = it; message = "" }, Modifier.fillMaxWidth(), label = { Text("Create password") }, placeholder = { Text("At least 6 characters") }, singleLine = true, visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }, contentPadding = PaddingValues(0.dp)) { Text(if (passwordVisible) "Hide" else "Show", fontSize = 10.sp, color = accent) } }, shape = RoundedCornerShape(15.dp))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(confirmPassword, { confirmPassword = it; message = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm password") }, placeholder = { Text("Re-enter password") }, singleLine = true, visualTransformation = if (confirmPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }, contentPadding = PaddingValues(0.dp)) { Text(if (confirmPasswordVisible) "Hide" else "Show", fontSize = 10.sp, color = accent) } }, shape = RoundedCornerShape(15.dp))
                        Spacer(Modifier.height(17.dp))
                        Button(onClick = { completeEmailRegistration() }, enabled = !isLoading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) { if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Row(verticalAlignment = Alignment.CenterVertically) { Text("Create account", fontWeight = FontWeight.Bold); Spacer(Modifier.width(7.dp)); Text("→", fontSize = 17.sp) } }
                    }
                } else {
                    if (currentStep == 1) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFFF6F1FF)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text("🇮🇳", fontSize = 22.sp); Spacer(Modifier.width(9.dp)); Column { Text("India", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = darkText); Text("+91 mobile verification", fontSize = 9.sp, color = grayText) } }
                        Spacer(Modifier.height(11.dp))
                        OutlinedTextField(mobile, { mobile = it.filter(Char::isDigit).take(10); message = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Mobile number") }, placeholder = { Text("9876543210") }, prefix = { Text("+91 ", fontWeight = FontWeight.Bold, color = accent) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = RoundedCornerShape(15.dp))
                        Spacer(Modifier.height(15.dp))
                        Button(onClick = { startPhoneVerification() }, enabled = !isLoading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) { if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Row(verticalAlignment = Alignment.CenterVertically) { Text("Send OTP", fontWeight = FontWeight.Bold); Spacer(Modifier.width(7.dp)); Text("→", fontSize = 17.sp) } }
                    } else {
                        Text("Enter the verification code", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                        Text("We sent a 6-digit OTP to ${cleanPhone(mobile)}", fontSize = 10.sp, color = grayText)
                        Spacer(Modifier.height(13.dp))
                        OutlinedTextField(otp, { otp = it.filter(Char::isDigit).take(6); message = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("OTP") }, placeholder = { Text("123456") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(15.dp))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(password, { password = it; message = "" }, Modifier.fillMaxWidth(), label = { Text("Create password") }, singleLine = true, visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }, contentPadding = PaddingValues(0.dp)) { Text(if (passwordVisible) "Hide" else "Show", fontSize = 10.sp, color = accent) } }, shape = RoundedCornerShape(15.dp))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(confirmPassword, { confirmPassword = it; message = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm password") }, singleLine = true, visualTransformation = if (confirmPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }, contentPadding = PaddingValues(0.dp)) { Text(if (confirmPasswordVisible) "Hide" else "Show", fontSize = 10.sp, color = accent) } }, shape = RoundedCornerShape(15.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { completePhoneRegistration() }, enabled = !isLoading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) { if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Row(verticalAlignment = Alignment.CenterVertically) { Text("Verify & create account", fontWeight = FontWeight.Bold); Spacer(Modifier.width(7.dp)); Text("→", fontSize = 17.sp) } }
                    }
                }

                if (message.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(if (message.contains("sent", true) || message.contains("verified", true)) Color(0xFFEAF8F2) else Color(0xFFFFF0F2)).padding(10.dp)) {
                        Text(message, fontSize = 10.sp, color = if (message.contains("sent", true) || message.contains("verified", true)) Color(0xFF168A61) else Color(0xFFD13B4C), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(2.dp))
            Text("🔒 Secure account setup powered by Firebase", fontSize = 9.sp, color = grayText)
            Spacer(Modifier.height(20.dp))
        }
    }

}

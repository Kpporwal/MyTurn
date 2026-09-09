


package com.kapil.queueless

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.heightIn
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.SetOptions
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween


import androidx.compose.material3.HorizontalDivider


import androidx.compose.runtime.DisposableEffect
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.kapil.queueless.ui.theme.QueueLessTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import com.google.firebase.firestore.FirebaseFirestore

import com.google.firebase.firestore.QuerySnapshot
import com.google.android.gms.tasks.Tasks
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import kotlin.math.roundToInt
enum class AppScreen {
    WELCOME,
    BUSINESS_PROFILE,
    BUSINESS_EDIT,
    BUSINESS_DASHBOARD,
    HOME,
    MY_QUEUE,
    PROFILE,
    PRIVACY_SECURITY
}


class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                saveFcmTokenToFirestore()
            }
        }

        requestNotificationPermission()
        enableEdgeToEdge()

        setContent {
            QueueLessTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.WELCOME) }
                var myQueueReturnScreen by remember { mutableStateOf(AppScreen.HOME) }
                var checkingSession by remember { mutableStateOf(true) }
                var showAuthHub by remember { mutableStateOf(false) }
                var authRole by remember { mutableStateOf("customer") }

                DisposableEffect(Unit) {
                    val user = auth.currentUser

                    if (user == null) {
                        currentScreen = AppScreen.WELCOME
                        showAuthHub = false
                        checkingSession = false
                    } else {
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(user.uid)
                            .get()
                            .addOnSuccessListener { userDoc ->
                                authRole = userDoc.getString("role") ?: "customer"

                                if (authRole == "business") {
                                    FirebaseFirestore.getInstance()
                                        .collection("businesses")
                                        .whereEqualTo("ownerId", user.uid)
                                        .limit(1)
                                        .get()
                                        .addOnSuccessListener { businessDocs ->
                                            val business = businessDocs.documents.firstOrNull()

                                            currentScreen =
                                                if (business?.getString("status") == "approved") {
                                                    AppScreen.BUSINESS_DASHBOARD
                                                } else {
                                                    AppScreen.BUSINESS_PROFILE
                                                }

                                            checkingSession = false
                                            showAuthHub = false
                                        }
                                        .addOnFailureListener {
                                            currentScreen = AppScreen.BUSINESS_PROFILE
                                            checkingSession = false
                                            showAuthHub = false
                                        }
                                } else {
                                    currentScreen = AppScreen.HOME
                                    checkingSession = false
                                    showAuthHub = false
                                }
                            }
                            .addOnFailureListener {
                                currentScreen = AppScreen.HOME
                                checkingSession = false
                                showAuthHub = false
                            }
                    }

                    onDispose { }
                }

                if (checkingSession) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF9F7FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF6C3CEB))
                    }
                } else if (showAuthHub) {
                    AuthNavigationHub(
                        role = authRole,
                        onBackToWelcome = {
                            showAuthHub = false
                            currentScreen = AppScreen.WELCOME
                        },
                        onCustomerDashboard = {
                            showAuthHub = false
                            currentScreen = AppScreen.HOME
                        },
                        onBusinessDashboard = {
                            showAuthHub = false
                            currentScreen = AppScreen.BUSINESS_DASHBOARD
                        },
                        onBusinessProfile = {
                            showAuthHub = false
                            currentScreen = AppScreen.BUSINESS_PROFILE
                        }
                    )
                } else {
                    when (currentScreen) {
                        AppScreen.WELCOME -> {
                            WelcomeScreen(
                                onCustomerClick = {
                                    authRole = "customer"
                                    showAuthHub = true
                                },
                                onBusinessClick = {
                                    authRole = "business"
                                    showAuthHub = true
                                }
                            )
                        }

                        AppScreen.BUSINESS_PROFILE -> {
                            BusinessRegisterScreen(
                                onBackClick = {
                                    auth.signOut()
                                    currentScreen = AppScreen.WELCOME
                                },
                                onApproved = {
                                    currentScreen = AppScreen.BUSINESS_DASHBOARD
                                }
                            )
                        }

                        AppScreen.BUSINESS_DASHBOARD -> {
                            BusinessDashboardScreen(
                                onEditProfile = {
                                    currentScreen = AppScreen.BUSINESS_EDIT
                                },
                                onLogout = {
                                    auth.signOut()
                                    currentScreen = AppScreen.WELCOME
                                }
                            )
                        }

                        AppScreen.BUSINESS_EDIT -> {
                            BusinessEditScreen(
                                onBackClick = {
                                    currentScreen = AppScreen.BUSINESS_DASHBOARD
                                },
                                onSaved = {
                                    currentScreen = AppScreen.BUSINESS_DASHBOARD
                                }
                            )
                        }

                        AppScreen.HOME -> {
                            HomeScreen(
                                onMyQueueClick = {
                                    myQueueReturnScreen = AppScreen.HOME
                                    currentScreen = AppScreen.MY_QUEUE
                                },
                                onProfileClick = {
                                    currentScreen = AppScreen.PROFILE
                                },
                                onLogout = {
                                    auth.signOut()
                                    currentScreen = AppScreen.WELCOME
                                }
                            )
                        }

                        AppScreen.MY_QUEUE -> {
                            MyQueueScreen(
                                onBackClick = {
                                    currentScreen = myQueueReturnScreen
                                }
                            )
                        }

                        AppScreen.PROFILE -> {
                            ProfileScreen(
                                onBackClick = {
                                    currentScreen = AppScreen.HOME
                                },
                                onMyQueueClick = {
                                    myQueueReturnScreen = AppScreen.PROFILE
                                    currentScreen = AppScreen.MY_QUEUE
                                },
                                onPrivacySecurityClick = {
                                    currentScreen = AppScreen.PRIVACY_SECURITY
                                },
                                onLogout = {
                                    auth.signOut()
                                    currentScreen = AppScreen.WELCOME
                                }
                            )
                        }

                        AppScreen.PRIVACY_SECURITY -> {
                            PrivacySecurityScreen(
                                onBackClick = {
                                    currentScreen = AppScreen.PROFILE
                                },
                                onLogout = {
                                    auth.signOut()
                                    currentScreen = AppScreen.WELCOME
                                },
                                onAccountDeleted = {
                                    auth.signOut()
                                    currentScreen = AppScreen.WELCOME
                                }
                            )
                        }

                        // Auth routes are now handled by AuthNavigationHub.
                        else -> {
                            currentScreen = AppScreen.WELCOME
                        }
                    }
                }
            }
        }
    }


    @Composable
    private fun AuthNavigationHub(
        role: String,
        onBackToWelcome: () -> Unit,
        onCustomerDashboard: () -> Unit,
        onBusinessDashboard: () -> Unit,
        onBusinessProfile: () -> Unit
    ) {
        // Lightweight in-memory auth navigation. This avoids requiring
        // Navigation Compose just for the login/register/dashboard flow.
        var route by remember { mutableStateOf("login") }

        when (route) {
            "login" -> {
                androidx.activity.compose.BackHandler {
                    onBackToWelcome()
                }

                LoginScreen(
                    onLoginSuccess = {
                        route = "dashboard"
                    },
                    onCreateAccount = {
                        route = "register"
                    },
                    onBackClick = onBackToWelcome,
                    role = role
                )
            }

            "register" -> {
                androidx.activity.compose.BackHandler {
                    route = "login"
                }

                RegistrationScreen(
                    onRegisterSuccess = {
                        route = "dashboard"
                    },
                    onEmailVerificationSent = {
                        route = "login"
                    },
                    onBackClick = {
                        route = "login"
                    },
                    role = role
                )
            }

            "dashboard" -> {
                androidx.activity.compose.BackHandler {
                    onBackToWelcome()
                }

                val user = auth.currentUser
                var resolving by remember { mutableStateOf(true) }

                androidx.compose.runtime.LaunchedEffect(user?.uid, role) {
                    if (user == null) {
                        resolving = false
                        onBackToWelcome()
                        return@LaunchedEffect
                    }

                    if (role == "business") {
                        FirebaseFirestore.getInstance()
                            .collection("businesses")
                            .whereEqualTo("ownerId", user.uid)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { documents ->
                                val business = documents.documents.firstOrNull()
                                resolving = false

                                if (business?.getString("status") == "approved") {
                                    onBusinessDashboard()
                                } else {
                                    onBusinessProfile()
                                }
                            }
                            .addOnFailureListener {
                                resolving = false
                                onBusinessProfile()
                            }
                    } else {
                        resolving = false
                        onCustomerDashboard()
                    }
                }

                if (resolving) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF9F7FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF6C3CEB))
                    }
                }
            }
        }
    }

    private fun saveFcmTokenToFirestore() {

        val user = auth.currentUser ?: return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.uid)
                    .set(
                        mapOf(
                            "fcmToken" to token
                        ),
                        SetOptions.merge()
                    )
                    .addOnFailureListener { error ->
                        android.util.Log.e(
                            "MyTurnFCM",
                            "Failed to save FCM token",
                            error
                        )
                    }
            }
            .addOnFailureListener { error ->
                android.util.Log.e(
                    "MyTurnFCM",
                    "Failed to get FCM token",
                    error
                )
            }
    }

    private fun requestNotificationPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    @Composable
    fun WelcomeScreen(
        onCustomerClick: () -> Unit,
        onBusinessClick: () -> Unit
    ) {
        val purple = Color(0xFF6C3CEB)
        val deepPurple = Color(0xFF3D1A8E)
        val lavender = Color(0xFFF2ECFF)
        val ink = Color(0xFF171321)
        val muted = Color(0xFF77717F)
        val green = Color(0xFF16A36D)
        var visible by remember { mutableStateOf(false) }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(60)
            visible = true
        }

        val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(650), label = "welcomeAlpha")
        val lift by animateFloatAsState(if (visible) 0f else 32f, tween(650), label = "welcomeLift")

        Box(Modifier.fillMaxSize().background(Color(0xFFF7F5FB))) {
            Box(
                Modifier.fillMaxWidth().height(300.dp).background(
                    Brush.linearGradient(listOf(deepPurple, purple, Color(0xFF9B72FF)))
                )
            )

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .graphicsLayer { this.alpha = alpha; translationY = lift }
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(15.dp))
                            .background(Color.White.copy(alpha = .16f))
                            .border(1.dp, Color.White.copy(alpha = .24f), RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("Q", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MyTurn", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Smart queue management", color = Color.White.copy(alpha = .72f), fontSize = 9.sp)
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .12f))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF6FF0B4)))
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text("Skip the queue.", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
                Text("Keep your time.", color = Color(0xFFE8DEFF), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Discover nearby services, see live waiting times,\nand join before you arrive.",
                    color = Color.White.copy(alpha = .78f), fontSize = 12.sp, lineHeight = 18.sp
                )

                Spacer(Modifier.height(24.dp))
                Card(
                    Modifier.fillMaxWidth(), RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(lavender),
                                contentAlignment = Alignment.Center
                            ) { Text("⏱", fontSize = 25.sp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Your time matters", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
                                Text("Real-time queue visibility", fontSize = 10.sp, color = muted)
                            }
                            Text("01", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = purple)
                        }

                        Spacer(Modifier.height(15.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("📍" to "Nearby", "🎟" to "Live queue", "⚡" to "Less waiting").forEach { (icon, label) ->
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(15.dp)).background(Color(0xFFF8F6FC)).padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(icon, fontSize = 18.sp)
                                        Spacer(Modifier.height(3.dp))
                                        Text(label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = ink)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Text("HOW DO YOU WANT TO USE QUEUELESS?", fontSize = 8.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, color = muted)
                        Spacer(Modifier.height(10.dp))

                        Card(
                            Modifier.fillMaxWidth().clickable(onClick = onCustomerClick),
                            RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = lavender)
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                    Text("👤", fontSize = 25.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("I'm a Customer", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
                                    Text("Find • Join • Track your turn", fontSize = 9.sp, color = muted)
                                }
                                Box(Modifier.size(38.dp).clip(CircleShape).background(purple), contentAlignment = Alignment.Center) {
                                    Text("→", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(Modifier.height(9.dp))
                        Card(
                            Modifier.fillMaxWidth().clickable(onClick = onBusinessClick),
                            RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F2))
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                    Text("🏪", fontSize = 25.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("I'm a Business Owner", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
                                    Text("Manage • Serve • Grow", fontSize = 9.sp, color = muted)
                                }
                                Box(Modifier.size(38.dp).clip(CircleShape).background(green), contentAlignment = Alignment.Center) {
                                    Text("→", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text("✓ Secure", fontSize = 8.sp, color = green, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp)); Text("•", color = Color(0xFFB7B1BF), fontSize = 8.sp)
                            Spacer(Modifier.width(8.dp)); Text("Fast", fontSize = 8.sp, color = muted)
                            Spacer(Modifier.width(8.dp)); Text("•", color = Color(0xFFB7B1BF), fontSize = 8.sp)
                            Spacer(Modifier.width(8.dp)); Text("Simple", fontSize = 8.sp, color = muted)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("MyTurn  •  Know your turn. Skip the wait.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 8.sp, color = Color(0xFF9A94A2))
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    @Composable
    fun LoginScreen(
        onLoginSuccess: () -> Unit,
        onCreateAccount: () -> Unit,
        onBackClick: () -> Unit,
        role: String = "customer"
    ) {

        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }

        val purple = Color(0xFF6C3CEB)
        val green = Color(0xFF249B68)
        val background = Color(0xFFF9F7FF)
        val textGray = Color(0xFF6D6875)
        val successGreen = Color(0xFF249B68)
        val errorRed = Color(0xFFD32F2F)

        val isBusiness = role.trim().equals("business", ignoreCase = true)
        val accentColor = if (isBusiness) green else purple

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(8.dp))

            // BACK
            TextButton(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(
                    text = "‹ Back",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                    color = accentColor,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ICON
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (isBusiness) {
                            Color(0xFFE5F7EF)
                        } else {
                            Color(0xFFE8DCFF)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isBusiness) "🏪" else "👥",
                    fontSize = 36.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // TITLE
            Text(
                text = if (isBusiness) {
                    "Business Login"
                } else {
                    "Welcome Back!"
                },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (isBusiness) green else purple
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isBusiness) {
                    "Login to manage your QueueLess business"
                } else {
                    "Login to continue with QueueLess"
                },
                fontSize = 13.sp,
                color = textGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(34.dp))

            // EMAIL
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    message = ""
                },
                label = {
                    Text("Email")
                },
                placeholder = {
                    Text("Enter your email")
                },
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PASSWORD
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    message = ""
                },
                label = {
                    Text("Password")
                },
                placeholder = {
                    Text("Enter your password")
                },
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(modifier = Modifier.height(26.dp))

            // LOGIN BUTTON
            Button(
                onClick = {

                    if (email.trim().isBlank() || password.isBlank()) {
                        message = "Please enter email and password."
                        return@Button
                    }

                    isLoading = true
                    message = ""

                    val auth = FirebaseAuth.getInstance()
                    val db = FirebaseFirestore.getInstance()

                    auth.signInWithEmailAndPassword(
                        email.trim(),
                        password
                    ).addOnCompleteListener { task ->

                        if (!task.isSuccessful) {

                            isLoading = false

                            message = task.exception?.localizedMessage
                                ?: "Login failed."

                            return@addOnCompleteListener
                        }

                        val user = auth.currentUser

                        if (user == null) {

                            isLoading = false
                            message = "Login session not found."

                            return@addOnCompleteListener
                        }

                        val uid = user.uid

                        // -------------------------------------------------
                        // STEP 1: CHECK USER PROFILE
                        // -------------------------------------------------
                        db.collection("users")
                            .document(uid)
                            .get()
                            .addOnSuccessListener { userDocument ->

                                if (!userDocument.exists()) {

                                    isLoading = false
                                    message = "Account profile not found."

                                    auth.signOut()
                                    return@addOnSuccessListener
                                }

                                // Normalize role:
                                // "business", "Business", " business "
                                // sab business hi maane jayenge.
                                val savedRole = userDocument
                                    .getString("role")
                                    ?.trim()
                                    ?.lowercase()
                                    ?: ""

                                val expectedRole = role
                                    .trim()
                                    .lowercase()

                                // -------------------------------------------------
                                // CUSTOMER LOGIN
                                // -------------------------------------------------
                                if (!isBusiness) {

                                    if (savedRole == expectedRole) {

                                        isLoading = false
                                        message = "Login successful!"

                                        onLoginSuccess()

                                    } else {

                                        isLoading = false
                                        message = "This is not a customer account."

                                        auth.signOut()
                                    }

                                    return@addOnSuccessListener
                                }

                                // -------------------------------------------------
                                // BUSINESS LOGIN
                                // -------------------------------------------------
                                //
                                // Business account ko users.role ke saath
                                // businesses.ownerId se bhi verify karenge.
                                //
                                db.collection("businesses")
                                    .whereEqualTo("ownerId", uid)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener { businessDocuments ->

                                        val hasBusinessProfile =
                                            !businessDocuments.isEmpty

                                        if (
                                            savedRole == "business" ||
                                            (
                                                    savedRole.isBlank() &&
                                                            hasBusinessProfile
                                                    )
                                        ) {

                                            isLoading = false
                                            message = "Login successful!"

                                            onLoginSuccess()

                                        } else {

                                            isLoading = false
                                            message =
                                                "This is not a business account."

                                            auth.signOut()
                                        }
                                    }
                                    .addOnFailureListener { error ->

                                        isLoading = false

                                        message =
                                            error.localizedMessage
                                                ?: "Could not verify business account."

                                        auth.signOut()
                                    }
                            }
                            .addOnFailureListener { error ->

                                isLoading = false

                                message =
                                    error.localizedMessage
                                        ?: "Could not verify account."

                                auth.signOut()
                            }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor
                )
            ) {

                if (isLoading) {

                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )

                } else {

                    Text(
                        text = if (isBusiness) {
                            "Business Login"
                        } else {
                            "Login"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // CREATE ACCOUNT
            TextButton(
                onClick = onCreateAccount,
                enabled = !isLoading
            ) {
                Text(
                    text = if (isBusiness) {
                        "Don't have a business account? Create one"
                    } else {
                        "Don't have an account? Create one"
                    },
                    color = accentColor,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            // MESSAGE
            if (message.isNotEmpty()) {

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = if (message == "Login successful!") {
                        successGreen
                    } else {
                        errorRed
                    },
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(35.dp))

            Text(
                text = "🔒  Secure login powered by Firebase",
                fontSize = 9.sp,
                color = textGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }





    data class LocationPoint(
        val latitude: Double,
        val longitude: Double
    )

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun getCurrentDeviceLocation(context: Context, onResult: (LocationPoint?, String?) -> Unit) {
        if (!hasLocationPermission(context)) {
            onResult(null, "Location permission is required.")
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { provider ->
                try { locationManager.isProviderEnabled(provider) } catch (_: Exception) { false }
            }

        if (providers.isEmpty()) {
            onResult(null, "Please turn on Location/GPS and try again.")
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            fun request(provider: String, fallback: Boolean = true) {
                try {
                    locationManager.getCurrentLocation(
                        provider,
                        CancellationSignal(),
                        context.mainExecutor
                    ) { location ->
                        if (location != null) {
                            onResult(LocationPoint(location.latitude, location.longitude), null)
                        } else if (fallback) {
                            val next = providers.firstOrNull { it != provider }
                            if (next != null) request(next, false)
                            else onResult(null, "Unable to get your current location. Try again.")
                        } else {
                            onResult(null, "Unable to get your current location. Try again.")
                        }
                    }
                } catch (_: SecurityException) {
                    onResult(null, "Location permission is required.")
                } catch (_: Exception) {
                    if (fallback) {
                        val next = providers.firstOrNull { it != provider }
                        if (next != null) request(next, false)
                        else onResult(null, "Unable to get your current location. Try again.")
                    } else {
                        onResult(null, "Unable to get your current location. Try again.")
                    }
                }
            }
            request(providers.first())
        } else {
            var best: Location? = null
            for (provider in providers) {
                try {
                    val location = locationManager.getLastKnownLocation(provider)
                    if (location != null && (best == null || location.time > best!!.time)) best = location
                } catch (_: SecurityException) {
                    // Permission was revoked between checks.
                }
            }
            if (best != null) {
                onResult(LocationPoint(best.latitude, best.longitude), null)
            } else {
                onResult(null, "Unable to get your current location. Try again.")
            }
        }
    }

    fun distanceInKm(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(toLat - fromLat)
        val dLng = Math.toRadians(toLng - fromLng)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(fromLat)) *
                Math.cos(Math.toRadians(toLat)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusKm * c
    }

    fun formatDistance(distanceKm: Double): String {
        val distanceMeters = (distanceKm * 1000.0).roundToInt().coerceAtLeast(0)
        return if (distanceMeters < 1000) {
            "$distanceMeters m away"
        } else {
            String.format(Locale.US, "%.1f km away", distanceKm)
        }
    }

    fun businessFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): Business? {
        return try {
            Business(
                id = document.id,
                ownerId = document.getString("ownerId") ?: "",
                name = document.getString("name") ?: "Unnamed Business",
                category = document.getString("category") ?: "Other",
                emoji = document.getString("emoji") ?: "🏪",
                waitingCount = (document.getLong("waitingCount") ?: 0L).toInt(),
                estimatedWait = (document.getLong("estimatedWait") ?: 0L).toInt(),
                isOpen = document.getBoolean("isOpen") ?: true,
                queuePaused = document.getBoolean("queuePaused") ?: false,
                status = document.getString("status") ?: "pending",
                address = document.getString("address") ?: "",
                city = document.getString("city") ?: "",
                latitude = document.getDouble("latitude"),
                longitude = document.getDouble("longitude")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun loadNearbyBusinesses(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 15.0,
        onResult: (List<Business>, String?) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val center = GeoLocation(latitude, longitude)
        val radiusInM = radiusKm * 1000.0
        val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)
        val tasks = bounds.map { bound ->
            db.collection("businesses")
                .whereEqualTo("status", "approved")
                .orderBy("geohash")
                .startAt(bound.startHash)
                .endAt(bound.endHash)
                .get()
        }

        Tasks.whenAllComplete(tasks)
            .addOnSuccessListener { completedTasks ->
                val unique = linkedMapOf<String, Business>()
                var failed = false

                completedTasks.forEach { task ->
                    if (!task.isSuccessful) {
                        failed = true
                    } else {
                        val snap = task.result as? QuerySnapshot
                        snap?.documents?.forEach { document ->
                            val business = businessFromDocument(document) ?: return@forEach
                            val lat = business.latitude ?: return@forEach
                            val lng = business.longitude ?: return@forEach
                            val distance = GeoFireUtils.getDistanceBetween(
                                GeoLocation(lat, lng), center
                            )
                            if (distance <= radiusInM) {
                                unique[business.id] = business
                            }
                        }
                    }
                }

                val sorted = unique.values.sortedBy { business ->
                    val lat = business.latitude ?: return@sortedBy Double.MAX_VALUE
                    val lng = business.longitude ?: return@sortedBy Double.MAX_VALUE
                    GeoFireUtils.getDistanceBetween(GeoLocation(lat, lng), center)
                }

                if (failed && sorted.isEmpty()) {
                    onResult(emptyList(), "Unable to load nearby businesses. Please try again.")
                } else {
                    onResult(sorted, null)
                }
            }
            .addOnFailureListener { error ->
                onResult(emptyList(), error.message ?: "Unable to load nearby businesses.")
            }
    }

    data class Business(
        val id: String = "",
        val ownerId: String = "",
        val name: String = "",
        val category: String = "",
        val emoji: String = "🏪",
        val waitingCount: Int = 0,
        val estimatedWait: Int = 0,
        val isOpen: Boolean = true,
        val queuePaused: Boolean = false,
        val status: String = "pending",
        val address: String = "",
        val city: String = "",
        val latitude: Double? = null,
        val longitude: Double? = null
    )

    data class QueueCustomer(
        val id: String = "",
        val userEmail: String = "",
        val status: String = "waiting",
        val joinedAtMillis: Long = 0L,
        val tokenNumber: Int = 0,
        val queuePosition: Int = 0
    )

    data class AnalyticsSnapshot(
        val todayTotal: Int = 0,
        val todayServed: Int = 0,
        val todaySkipped: Int = 0,
        val todayLeft: Int = 0,
        val todayCompletionRate: Int = 0,
        val todayAverageWait: Int = 0,
        val todayAverageService: Int = 0,
        val todayPeakHour: String = "Not enough data",
        val last7DaysTotal: Int = 0,
        val last7DaysServed: Int = 0,
        val last7DaysAveragePerDay: Int = 0,
        val last7DaysBestDay: String = "Not enough data",
        val dailyCounts: List<Pair<String, Int>> = emptyList()
    )
    @Composable
    fun HomeScreen(
        onMyQueueClick: () -> Unit,
        onProfileClick: () -> Unit,
        onLogout: () -> Unit
    ) {

        var searchText by remember {
            mutableStateOf("")
        }

        var selectedCategory by remember {
            mutableStateOf("All")
        }

        var businesses by remember {
            mutableStateOf<List<Business>>(emptyList())
        }

        var isLoading by remember {
            mutableStateOf(true)
        }

        var errorMessage by remember {
            mutableStateOf("")
        }

        var nearMeEnabled by remember { mutableStateOf(false) }
        var userLatitude by remember { mutableStateOf<Double?>(null) }
        var userLongitude by remember { mutableStateOf<Double?>(null) }
        var locationLoading by remember { mutableStateOf(false) }
        var locationMessage by remember { mutableStateOf("") }

        // Join Queue details
        var showJoinQueueDialog by remember { mutableStateOf(false) }
        var joinBusiness by remember { mutableStateOf<Business?>(null) }
        var joinService by remember { mutableStateOf("") }
        var joinPeople by remember { mutableStateOf(1) }
        var joinBusy by remember { mutableStateOf(false) }
        var joinMessage by remember { mutableStateOf("") }
        var customerName by remember { mutableStateOf("") }
        var customerPhone by remember { mutableStateOf("") }

        val context = LocalContext.current

        val locationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                locationLoading = true
                locationMessage = ""
                getCurrentDeviceLocation(context) { point, error ->
                    locationLoading = false
                    if (point != null) {
                        userLatitude = point.latitude
                        userLongitude = point.longitude
                        nearMeEnabled = true
                        locationMessage = "Showing approved businesses within 15 km."
                    } else {
                        nearMeEnabled = false
                        locationMessage = error ?: "Unable to get your location."
                    }
                }
            } else {
                nearMeEnabled = false
                locationMessage = "Location permission denied. You can still search normally."
            }
        }

        val purple = Color(0xFF6C3CEB)
        val lightPurple = Color(0xFFF4EEFF)

        val background = Color(0xFFF9F7FF)
        val darkText = Color(0xFF252238)
        val grayText = Color(0xFF706B78)

        val green = Color(0xFF249B68)
        val lightGreen = Color(0xFFEFFAF4)

        val currentFirebaseUser = FirebaseAuth.getInstance().currentUser
        val userEmail = currentFirebaseUser?.email ?: "MyTurn Customer"

        DisposableEffect(currentFirebaseUser?.uid) {
            if (currentFirebaseUser == null) {
                customerName = ""
                customerPhone = ""
                onDispose { }
            } else {
                customerName = currentFirebaseUser.displayName ?: ""
                customerPhone = currentFirebaseUser.phoneNumber ?: ""
                val listener = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentFirebaseUser.uid)
                    .addSnapshotListener { snapshot, _ ->
                        if (snapshot != null && snapshot.exists()) {
                            customerName = snapshot.getString("name")
                                ?: snapshot.getString("fullName")
                                        ?: currentFirebaseUser.displayName
                                        ?: ""
                            customerPhone = snapshot.getString("phone")
                                ?: currentFirebaseUser.phoneNumber
                                        ?: ""
                        }
                    }
                onDispose { listener.remove() }
            }
        }

        // =========================================================
        // FIRESTORE - NORMAL LIST OR GEOHASH NEARBY QUERY
        // =========================================================

        DisposableEffect(nearMeEnabled, userLatitude, userLongitude) {
            isLoading = true
            errorMessage = ""

            if (nearMeEnabled && userLatitude != null && userLongitude != null) {
                loadNearbyBusinesses(userLatitude!!, userLongitude!!, 15.0) { nearby, error ->
                    businesses = nearby
                    errorMessage = error ?: ""
                    isLoading = false
                }
                onDispose { }
            } else {
                val listener = FirebaseFirestore
                    .getInstance()
                    .collection("businesses")
                    .whereEqualTo("status", "approved")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            errorMessage = error.message ?: "Unable to load services."
                            isLoading = false
                            return@addSnapshotListener
                        }
                        businesses = snapshot?.documents?.mapNotNull { businessFromDocument(it) } ?: emptyList()
                        errorMessage = ""
                        isLoading = false
                    }

                onDispose { listener.remove() }
            }
        }


        // =========================================================
        // SEARCH + CATEGORY FILTER
        // =========================================================

        val filteredBusinesses = businesses.filter { business ->

            val isApproved = business.status.equals("approved", ignoreCase = true)

            val matchesSearch =
                searchText.isBlank() ||
                        business.name.contains(
                            searchText,
                            ignoreCase = true
                        ) ||
                        business.category.contains(
                            searchText,
                            ignoreCase = true
                        )

            val matchesCategory =
                selectedCategory == "All" ||
                        business.category.equals(
                            selectedCategory,
                            ignoreCase = true
                        )

            val matchesNearby = if (!nearMeEnabled) {
                true
            } else {
                business.latitude != null && business.longitude != null
            }

            isApproved && matchesSearch && matchesCategory && matchesNearby
        }.let { list ->
            if (!nearMeEnabled || userLatitude == null || userLongitude == null) {
                list
            } else {
                list.sortedBy { business ->
                    if (business.latitude != null && business.longitude != null) {
                        distanceInKm(userLatitude!!, userLongitude!!, business.latitude, business.longitude)
                    } else {
                        Double.MAX_VALUE
                    }
                }
            }
        }


        // =========================================================
        // MYTURN CUSTOMER HOME UI
        // =========================================================
        // Same Firebase/search/near-me/join-queue logic as before;
        // only the visual structure is redesigned here.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F8FC))
        ) {
            // ---------- Premium blue app header ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0758B8), Color(0xFF147BE8))
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("M", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("MyTurn", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Your turn. Your time.", color = Color.White.copy(alpha = .76f), fontSize = 9.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Color.White.copy(alpha = .14f))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Find a service near you", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "See live waiting time and join before you arrive.",
                        color = Color.White.copy(alpha = .78f),
                        fontSize = 11.sp
                    )
                }
            }

            // ---------- Search card overlaps the header ----------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("Search business or service", fontSize = 12.sp, color = Color(0xFF8A9099)) },
                        leadingIcon = { Text("⌕", fontSize = 24.sp, color = Color(0xFF0B63C7)) },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color(0xFFF7F9FC),
                            unfocusedContainerColor = Color(0xFFF7F9FC)
                        )
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ---------- Quick actions ----------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f).clickable { onMyQueueClick() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF3FF)), contentAlignment = Alignment.Center) {
                                Text("🎟", fontSize = 19.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("My Queue", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = darkText)
                                Text("Track your turn", fontSize = 8.sp, color = grayText)
                            }
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f).clickable {
                            if (!locationLoading) {
                                if (hasLocationPermission(context)) {
                                    locationLoading = true
                                    getCurrentDeviceLocation(context) { point, error ->
                                        locationLoading = false
                                        if (point != null) {
                                            userLatitude = point.latitude
                                            userLongitude = point.longitude
                                            nearMeEnabled = true
                                            locationMessage = "Showing approved businesses within 15 km."
                                        } else locationMessage = error ?: "Unable to get your location."
                                    }
                                } else {
                                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF8F2)), contentAlignment = Alignment.Center) {
                                Text("📍", fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(if (nearMeEnabled) "Near Me" else "Nearby", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = darkText)
                                Text(if (nearMeEnabled) "Within 15 km" else "Find nearby", fontSize = 8.sp, color = grayText)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // ---------- Categories ----------
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Categories", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                        Text(
                            if (selectedCategory == "All") "Choose a service" else "Showing $selectedCategory",
                            fontSize = 9.sp,
                            color = if (selectedCategory == "All") grayText else Color(0xFF0B63C7)
                        )
                    }
                    if (selectedCategory != "All") {
                        TextButton(onClick = { selectedCategory = "All" }, contentPadding = PaddingValues(0.dp)) {
                            Text("Clear", fontSize = 10.sp, color = Color(0xFF0B63C7), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryItem("🏥", "Healthcare", selectedCategory.equals("Healthcare", true), { selectedCategory = if (selectedCategory.equals("Healthcare", true)) "All" else "Healthcare" }, Modifier.weight(1f))
                    CategoryItem("💇", "Salon", selectedCategory.equals("Salon", true), { selectedCategory = if (selectedCategory.equals("Salon", true)) "All" else "Salon" }, Modifier.weight(1f))
                    CategoryItem("🏦", "Banking", selectedCategory.equals("Banking", true), { selectedCategory = if (selectedCategory.equals("Banking", true)) "All" else "Banking" }, Modifier.weight(1f))
                    CategoryItem("🍽", "Food", selectedCategory.equals("Food", true), { selectedCategory = if (selectedCategory.equals("Food", true)) "All" else "Food" }, Modifier.weight(1f))
                }

                Spacer(Modifier.height(24.dp))

                // ---------- Service list heading ----------
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (nearMeEnabled) "Businesses near you" else "Available services",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = darkText
                        )
                        Text(
                            if (nearMeEnabled) "Nearest approved businesses first" else "Approved businesses only",
                            fontSize = 9.sp,
                            color = grayText
                        )
                    }
                    Text("${filteredBusinesses.size} available", fontSize = 9.sp, color = Color(0xFF0B63C7), fontWeight = FontWeight.Bold)
                }

                if (locationMessage.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(locationMessage, fontSize = 8.sp, color = grayText)
                }
                if (nearMeEnabled) {
                    TextButton(onClick = { nearMeEnabled = false; locationMessage = "Showing all approved businesses." }, contentPadding = PaddingValues(0.dp)) {
                        Text("Show all businesses", fontSize = 9.sp, color = Color(0xFF0B63C7))
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (isLoading) {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF0B63C7))
                    }
                } else if (errorMessage.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F1))) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚠️", fontSize = 28.sp)
                            Spacer(Modifier.height(7.dp))
                            Text("Unable to load services", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                            Spacer(Modifier.height(4.dp))
                            Text(errorMessage, fontSize = 10.sp, color = grayText, textAlign = TextAlign.Center)
                        }
                    }
                } else if (filteredBusinesses.isEmpty()) {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                        Column(Modifier.fillMaxWidth().padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⌕", fontSize = 34.sp, color = Color(0xFF0B63C7))
                            Spacer(Modifier.height(7.dp))
                            Text("No services found", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = darkText)
                            Text("Try another search or category.", fontSize = 10.sp, color = grayText)
                        }
                    }
                } else {
                    filteredBusinesses.forEach { business ->
                        ServiceCard(
                            emoji = business.emoji,
                            businessName = business.name,
                            category = business.category,
                            peopleWaiting = business.waitingCount,
                            waitTime = "${business.estimatedWait} min",
                            address = business.address,
                            city = business.city,
                            statusColor = if (business.isOpen) Color(0xFF159A67) else Color(0xFFD64545),
                            backgroundColor = Color(0xFFEAF3FF),
                            distanceText = if (nearMeEnabled && userLatitude != null && userLongitude != null && business.latitude != null && business.longitude != null) formatDistance(distanceInKm(userLatitude!!, userLongitude!!, business.latitude, business.longitude)) else null,
                            onJoinClick = {
                                val user = FirebaseAuth.getInstance().currentUser
                                if (user == null) {
                                    Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
                                } else if (!business.isOpen) {
                                    Toast.makeText(context, "${business.name} is currently closed.", Toast.LENGTH_SHORT).show()
                                } else if (business.queuePaused) {
                                    Toast.makeText(context, "${business.name}'s queue is temporarily paused.", Toast.LENGTH_SHORT).show()
                                } else {
                                    joinBusiness = business
                                    joinService = ""
                                    joinPeople = 1
                                    joinMessage = ""
                                    showJoinQueueDialog = true
                                }
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (showJoinQueueDialog && joinBusiness != null) {
                JoinQueueDialog(
                    business = joinBusiness!!,
                    customerName = customerName.ifBlank {
                        userEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                    },
                    customerPhone = customerPhone,
                    service = joinService,
                    people = joinPeople,
                    busy = joinBusy,
                    message = joinMessage,
                    onServiceChange = { joinService = it },
                    onPeopleChange = { joinPeople = it.coerceIn(1, 8) },
                    onDismiss = {
                        if (!joinBusy) {
                            showJoinQueueDialog = false
                            joinMessage = ""
                        }
                    },
                    onConfirm = {
                        val selectedBusiness = joinBusiness ?: return@JoinQueueDialog
                        val currentUser = FirebaseAuth.getInstance().currentUser ?: return@JoinQueueDialog
                        if (joinBusy) return@JoinQueueDialog

                        joinBusy = true
                        joinMessage = ""
                        val db = FirebaseFirestore.getInstance()

                        db.collection("queues")
                            .whereEqualTo("userId", currentUser.uid)
                            .get()
                            .addOnSuccessListener { existing ->
                                val alreadyWaiting = existing.documents.any { doc ->
                                    doc.getString("businessId") == selectedBusiness.id &&
                                            (doc.getString("status") == "waiting" || doc.getString("status") == "called")
                                }

                                if (alreadyWaiting) {
                                    joinBusy = false
                                    joinMessage = "You are already in this queue."
                                    return@addOnSuccessListener
                                }

                                val queueRef = db.collection("queues").document()
                                val now = com.google.firebase.Timestamp.now()

                                val queueData = hashMapOf<String, Any?>(
                                    "userId" to currentUser.uid,
                                    "userEmail" to (currentUser.email ?: "Customer"),
                                    "customerName" to customerName.ifBlank {
                                        currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "Customer"
                                    },
                                    "customerPhone" to (customerPhone.ifBlank { currentUser.phoneNumber ?: "" }),
                                    "businessId" to selectedBusiness.id,
                                    "businessName" to selectedBusiness.name,
                                    "businessOwnerId" to selectedBusiness.ownerId,
                                    "category" to selectedBusiness.category,
                                    "service" to joinService.trim(),
                                    "partySize" to joinPeople,
                                    "status" to "waiting",
                                    "joinedAt" to now,
                                    "tokenNumber" to 0,
                                    "queuePosition" to 0
                                )

                                db.runTransaction { transaction ->
                                    val businessRef = db.collection("businesses").document(selectedBusiness.id)
                                    val businessSnapshot = transaction.get(businessRef)
                                    val open = businessSnapshot.getBoolean("isOpen") ?: true
                                    val paused = businessSnapshot.getBoolean("queuePaused") ?: false
                                    val approved = businessSnapshot.getString("status") == "approved"

                                    if (!open || paused || !approved) {
                                        throw IllegalStateException("Queue is currently unavailable.")
                                    }

                                    val currentCount = (businessSnapshot.getLong("waitingCount") ?: 0L)
                                        .toInt().coerceAtLeast(0)
                                    val nextToken = (businessSnapshot.getLong("nextTokenNumber") ?: 1L)
                                        .toInt().coerceAtLeast(1)
                                    val serviceMinutes = (businessSnapshot.getLong("serviceTimeMinutes") ?: 10L)
                                        .toInt().coerceIn(1, 120)

                                    queueData["tokenNumber"] = nextToken
                                    queueData["queuePosition"] = currentCount + 1

                                    transaction.set(queueRef, queueData)
                                    transaction.update(
                                        businessRef,
                                        mapOf(
                                            "waitingCount" to currentCount + 1,
                                            "estimatedWait" to (currentCount + 1) * serviceMinutes,
                                            "nextTokenNumber" to nextToken + 1
                                        )
                                    )
                                    null
                                }.addOnSuccessListener {
                                    joinBusy = false
                                    showJoinQueueDialog = false
                                    Toast.makeText(
                                        context,
                                        "You're in! Token #${queueData["tokenNumber"].toString().padStart(3, '0')}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }.addOnFailureListener { error ->
                                    joinBusy = false
                                    joinMessage = error.message ?: "Unable to join queue."
                                }
                            }
                            .addOnFailureListener { error ->
                                joinBusy = false
                                joinMessage = "Unable to check your queue: ${error.message ?: "Please try again."}"
                            }
                    }
                )
            }

            // ---------- Bottom navigation ----------
            Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 10.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavItem("⌂", "Home", true)
                    BottomNavItem("🎟", "My Queue", false, onMyQueueClick)
                    BottomNavItem("♙", "Profile", false, onProfileClick)
                }
            }
        }
    }


    // =====================================================
// JOIN QUEUE DIALOG
// =====================================================
    @Composable
    fun JoinQueueDialog(
        business: Business,
        customerName: String,
        customerPhone: String,
        service: String,
        people: Int,
        busy: Boolean,
        message: String,
        onServiceChange: (String) -> Unit,
        onPeopleChange: (Int) -> Unit,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit
    ) {
        val blue = Color(0xFF0B63C7)
        val dark = Color(0xFF20252C)
        val gray = Color(0xFF747B84)
        val lightBlue = Color(0xFFEAF3FF)

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color(0xFFF9FBFF),
            shape = RoundedCornerShape(28.dp),
            title = {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(50.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(lightBlue),
                            contentAlignment = Alignment.Center
                        ) { Text(business.emoji, fontSize = 25.sp) }

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text("Join Queue", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = dark)
                            Text(business.name, fontSize = 10.sp, color = gray, maxLines = 1)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Card(
                        Modifier.fillMaxWidth(),
                        RoundedCornerShape(17.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏱", fontSize = 20.sp)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${business.waitingCount} people waiting",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dark
                                )
                                Text(
                                    "Estimated wait • ${business.estimatedWait} min",
                                    fontSize = 9.sp,
                                    color = gray
                                )
                            }
                            Text(
                                if (business.isOpen) "OPEN" else "CLOSED",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (business.isOpen) Color(0xFF159A67) else Color(0xFFD64545)
                            )
                        }
                    }
                }
            },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("Your details", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = dark)
                    Spacer(Modifier.height(7.dp))
                    InfoFieldCard("👤", customerName.ifBlank { "MyTurn Customer" })
                    Spacer(Modifier.height(7.dp))
                    InfoFieldCard("📱", customerPhone.ifBlank { "Phone not added" })

                    Spacer(Modifier.height(14.dp))
                    Text("Service (optional)", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = dark)
                    Spacer(Modifier.height(6.dp))

                    OutlinedTextField(
                        value = service,
                        onValueChange = { if (it.length <= 60) onServiceChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !busy,
                        placeholder = { Text("e.g. Haircut, Consultation", fontSize = 11.sp) },
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(Modifier.height(14.dp))
                    Text("Number of people", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = dark)
                    Spacer(Modifier.height(7.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircleStepButton("−", people > 1 && !busy) { onPeopleChange(people - 1) }

                        Box(
                            Modifier.padding(horizontal = 18.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(lightBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(people.toString(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = blue)
                        }

                        CircleStepButton("+", people < 8 && !busy) { onPeopleChange(people + 1) }
                    }

                    if (message.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            message,
                            Modifier.fillMaxWidth(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD32F2F),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blue)
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Confirm & Get Token", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text("Cancel", color = gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    @Composable
    fun InfoFieldCard(icon: String, value: String) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE4EAF2), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 17.sp)
            Spacer(Modifier.width(9.dp))
            Text(value, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF30353C), maxLines = 1)
        }
    }

    @Composable
    fun CircleStepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
        Box(
            Modifier.size(40.dp)
                .clip(CircleShape)
                .background(if (enabled) Color(0xFFEAF3FF) else Color(0xFFF1F3F6))
                .border(1.dp, if (enabled) Color(0xFFD1E2F7) else Color(0xFFE4E7EB), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color(0xFF0B63C7) else Color(0xFFADB3BA)
            )
        }
    }

    // =====================================================
// CATEGORY ITEM
// =====================================================
    @Composable
    fun CategoryItem(
        emoji: String,
        title: String,
        selected: Boolean = false,
        onClick: () -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        val blue = Color(0xFF0B63C7)
        val darkText = Color(0xFF252A31)
        Column(
            modifier = modifier
                .height(82.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) blue else Color.White)
                .border(1.dp, if (selected) blue else Color(0xFFE4E8EE), RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Color.White.copy(alpha = .16f) else Color(0xFFEAF3FF)),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 18.sp) }
            Spacer(Modifier.height(5.dp))
            Text(title, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else darkText, textAlign = TextAlign.Center)
        }
    }


    // =====================================================
// SERVICE CARD
// =====================================================
    @Composable
    fun ServiceCard(
        emoji: String, businessName: String, category: String, peopleWaiting: Int, waitTime: String,
        address: String, city: String, statusColor: Color, backgroundColor: Color,
        distanceText: String? = null, onJoinClick: () -> Unit
    ) {
        val context = LocalContext.current
        val locationQuery = listOf(address.trim(), city.trim()).filter { it.isNotBlank() }.joinToString(", ")
        fun openMaps() {
            if (locationQuery.isBlank()) { Toast.makeText(context, "Business location is not available.", Toast.LENGTH_SHORT).show(); return }
            val encodedQuery = Uri.encode(locationQuery)
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedQuery"))) }
            catch (_: Exception) { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedQuery"))) } catch (_: Exception) { Toast.makeText(context, "Unable to open Maps.", Toast.LENGTH_SHORT).show() } }
        }
        Card(
            Modifier.fillMaxWidth(),
            RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(66.dp).clip(RoundedCornerShape(16.dp)).background(backgroundColor), contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 30.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(businessName, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF20252C), maxLines = 1, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.clip(RoundedCornerShape(50.dp)).background(if (statusColor == Color(0xFFD64545)) Color(0xFFFFEEEE) else Color(0xFFEAF8F2)).padding(horizontal = 7.dp, vertical = 5.dp)) {
                                Text(if (statusColor == Color(0xFFD64545)) "CLOSED" else "OPEN", fontSize = 6.sp, fontWeight = FontWeight.ExtraBold, color = statusColor)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(category, fontSize = 9.sp, color = Color(0xFF747B84))
                        Spacer(Modifier.height(5.dp))
                        Text("★ 4.8  •  Trusted service", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE29A19))
                    }
                }
                HorizontalDivider(color = Color(0xFFF0F2F5))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 58.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color(0xFFF7F9FC))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "WAITING",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = Color(0xFF8A9099),
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(5.dp))

                        Text(
                            text = "$peopleWaiting people",
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF252A31),
                            maxLines = 1
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 58.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color(0xFFEAF3FF))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "EST. WAIT",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = Color(0xFF6E7884),
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(5.dp))

                        Text(
                            text = waitTime,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0B63C7),
                            maxLines = 1
                        )
                    }
                }
                if (address.isNotBlank() || city.isNotBlank()) {
                    Text("📍 ${listOf(address.trim(), city.trim()).filter { it.isNotBlank() }.joinToString(", ")}", fontSize = 8.sp, color = Color(0xFF747B84), maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp))
                }
                if (!distanceText.isNullOrBlank()) Text("Nearby • $distanceText", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0B63C7), modifier = Modifier.padding(start = 14.dp, top = 5.dp))
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openMaps() }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFFD6E4F5)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0B63C7))) {
                        Text("📍 Directions", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = onJoinClick, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B63C7))) {
                        Text("Join Queue", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }


    // =====================================================
// BOTTOM NAV ITEM
// =====================================================
    @Composable
    fun BottomNavItem(emoji: String, title: String, selected: Boolean, onClick: () -> Unit = {}) {
        Column(
            modifier = Modifier.width(72.dp).clip(RoundedCornerShape(16.dp)).clickable { onClick() }.padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(if (selected) 34.dp else 30.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Color(0xFFEBDDFF) else Color.Transparent),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = if (selected) 18.sp else 16.sp) }
            Spacer(Modifier.height(2.dp))
            Text(title, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) Color(0xFF6C3CEB) else Color(0xFF77717E))
        }
    }
    @Composable
    fun MyQueueScreen(
        onBackClick: () -> Unit
    ) {
        val purple = Color(0xFF6C3CEB)
        val purpleLight = Color(0xFFF3EDFF)
        val darkText = Color(0xFF252238)
        val grayText = Color(0xFF77717E)
        val green = Color(0xFF249B68)
        val greenLight = Color(0xFFEAF8F1)
        val red = Color(0xFFD32F2F)
        val orange = Color(0xFFE58B2A)
        val background = Color(0xFFF9F7FF)
        val db = FirebaseFirestore.getInstance()
        val context = LocalContext.current
        val user = FirebaseAuth.getInstance().currentUser

        var activeQueue by remember { mutableStateOf<Map<String, Any?>?>(null) }
        var history by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
        var businessInfo by remember { mutableStateOf<Map<String, Any?>?>(null) }
        var reviewByQueueId by remember { mutableStateOf<Map<String, Map<String, Any?>>>(emptyMap()) }
        var loading by remember { mutableStateOf(true) }
        var actionBusy by remember { mutableStateOf(false) }
        var showLeaveDialog by remember { mutableStateOf(false) }
        var showReviewDialog by remember { mutableStateOf(false) }
        var selectedQueueId by remember { mutableStateOf<String?>(null) }
        var selectedBusinessName by remember { mutableStateOf("") }
        var selectedToken by remember { mutableStateOf(0) }
        var selectedRating by remember { mutableStateOf(0) }
        var selectedReviewText by remember { mutableStateOf("") }
        var reviewBusy by remember { mutableStateOf(false) }
        var reviewMessage by remember { mutableStateOf("") }

        DisposableEffect(user?.uid) {
            if (user == null) {
                loading = false
                onDispose { }
            } else {
                val listener = db.collection("queues")
                    .whereEqualTo("userId", user.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error == null && snapshot != null) {
                            val docs = snapshot.documents.sortedByDescending {
                                it.getTimestamp("joinedAt")?.toDate()?.time ?: 0L
                            }
                            val activeDoc = docs.firstOrNull {
                                it.getString("status") == "waiting" || it.getString("status") == "called"
                            }
                            activeQueue = activeDoc?.data?.toMutableMap()?.apply { put("__id__", activeDoc.id) }
                            history = docs.filter { it.id != activeDoc?.id }
                                .take(10)
                                .map { doc ->
                                    doc.data?.toMutableMap()?.apply { put("__id__", doc.id) }
                                        ?: emptyMap<String, Any?>()
                                }
                            val businessId = activeDoc?.getString("businessId")
                            if (businessId != null) {
                                db.collection("businesses").document(businessId).get()
                                    .addOnSuccessListener { b -> businessInfo = b.data }
                            }
                            loading = false
                        } else if (error != null) loading = false
                    }
                onDispose { listener.remove() }
            }
        }

        DisposableEffect(user?.uid) {
            if (user == null) {
                reviewByQueueId = emptyMap()
                onDispose { }
            } else {
                val listener = db.collection("reviews")
                    .whereEqualTo("customerId", user.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error == null && snapshot != null) {
                            reviewByQueueId = snapshot.documents.associate { it.id to (it.data ?: emptyMap()) }
                        }
                    }
                onDispose { listener.remove() }
            }
        }

        val queue = activeQueue
        val status = queue?.get("status") as? String ?: ""
        val token = (queue?.get("tokenNumber") as? Number)?.toInt() ?: 0
        val position = (queue?.get("queuePosition") as? Number)?.toInt() ?: 0
        val peopleAhead = (position - 1).coerceAtLeast(0)
        val serviceMinutes = ((businessInfo?.get("serviceTimeMinutes") as? Number)?.toInt() ?: 10).coerceIn(1, 120)
        val waitMinutes = peopleAhead * serviceMinutes
        val businessName = queue?.get("businessName") as? String ?: "MyTurn Business"
        val category = queue?.get("category") as? String ?: "Service"
        val businessOpen = businessInfo?.get("isOpen") as? Boolean ?: true
        val queuePaused = businessInfo?.get("queuePaused") as? Boolean ?: false

        fun openReview(item: Map<String, Any?>) {
            val queueId = item["__id__"] as? String ?: return
            if ((item["status"] as? String) != "served") return
            selectedQueueId = queueId
            selectedBusinessName = item["businessName"] as? String ?: "Business"
            selectedToken = (item["tokenNumber"] as? Number)?.toInt() ?: 0
            val existing = reviewByQueueId[queueId]
            selectedRating = (existing?.get("rating") as? Number)?.toInt() ?: 0
            selectedReviewText = existing?.get("reviewText") as? String ?: ""
            reviewMessage = ""
            showReviewDialog = true
        }

        fun submitReview() {
            val currentUser = user ?: return
            val queueId = selectedQueueId ?: return
            if (selectedRating !in 1..5 || reviewBusy) {
                reviewMessage = "Please select a rating from 1 to 5 stars."
                return
            }
            reviewBusy = true
            reviewMessage = ""

            val queueRef = db.collection("queues").document(queueId)
            val reviewRef = db.collection("reviews").document(queueId)

            queueRef.get().addOnSuccessListener { q ->
                if (!q.exists() || q.getString("userId") != currentUser.uid || q.getString("status") != "served") {
                    reviewBusy = false
                    reviewMessage = "Only a completed visit can be reviewed."
                    return@addOnSuccessListener
                }

                val businessId = q.getString("businessId") ?: ""
                val ownerId = q.getString("businessOwnerId") ?: ""
                val actualBusinessName = q.getString("businessName") ?: selectedBusinessName
                if (businessId.isBlank() || ownerId.isBlank()) {
                    reviewBusy = false
                    reviewMessage = "This visit is missing business information."
                    return@addOnSuccessListener
                }

                val existing = reviewByQueueId[queueId]
                val now = com.google.firebase.Timestamp.now()
                val reviewData = hashMapOf<String, Any>(
                    "queueId" to queueId,
                    "customerId" to currentUser.uid,
                    "businessId" to businessId,
                    "businessOwnerId" to ownerId,
                    "businessName" to actualBusinessName,
                    "rating" to selectedRating,
                    "reviewText" to selectedReviewText.trim(),
                    "updatedAt" to now
                )
                reviewData["createdAt"] = existing?.get("createdAt") ?: now

                val task = if (existing == null) reviewRef.set(reviewData) else reviewRef.update(reviewData)
                task.addOnSuccessListener {
                    reviewBusy = false
                    showReviewDialog = false
                    Toast.makeText(
                        context,
                        if (existing == null) "Thanks for your review! ⭐" else "Review updated.",
                        Toast.LENGTH_SHORT
                    ).show()
                }.addOnFailureListener { e ->
                    reviewBusy = false
                    reviewMessage = e.message ?: "Unable to save review."
                }
            }.addOnFailureListener { e ->
                reviewBusy = false
                reviewMessage = e.message ?: "Unable to verify your visit."
            }
        }

        fun leaveQueue() {
            if (actionBusy || user == null || queue == null || status != "waiting") return
            val queueId = queue["__id__"] as? String ?: return
            val businessId = queue["businessId"] as? String ?: return
            val currentUser = user
            actionBusy = true
            db.runTransaction { transaction ->
                val queueRef = db.collection("queues").document(queueId)
                val businessRef = db.collection("businesses").document(businessId)
                val q = transaction.get(queueRef)
                val b = transaction.get(businessRef)
                if (q.getString("userId") != currentUser.uid) throw IllegalStateException("You cannot leave this queue.")
                if (q.getString("status") != "waiting") throw IllegalStateException("Queue entry is no longer active.")
                val currentCount = (b.getLong("waitingCount") ?: 0L).coerceAtLeast(0L)
                val serviceMinutes = (b.getLong("serviceTimeMinutes") ?: 10L).coerceIn(1L, 120L)
                val newCount = (currentCount - 1L).coerceAtLeast(0L)
                val now = com.google.firebase.Timestamp.now()
                transaction.update(queueRef, mapOf("status" to "cancelled", "completedAt" to now))
                transaction.update(businessRef, mapOf("waitingCount" to newCount, "estimatedWait" to newCount * serviceMinutes))
                null
            }.addOnSuccessListener {
                showLeaveDialog = false
                actionBusy = false
                Toast.makeText(context, "You left the queue.", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { error ->
                actionBusy = false
                Toast.makeText(context, error.message ?: "Unable to leave queue.", Toast.LENGTH_LONG).show()
            }
        }

        if (showLeaveDialog) {
            AlertDialog(
                onDismissRequest = { if (!actionBusy) showLeaveDialog = false },
                title = { Text("Leave queue?") },
                text = { Text("Are you sure you want to leave $businessName? Your token #${token.toString().padStart(3, '0')} will be marked as Left.") },
                confirmButton = { TextButton(onClick = { leaveQueue() }, enabled = !actionBusy) { Text("Leave", color = red, fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { showLeaveDialog = false }, enabled = !actionBusy) { Text("Stay") } }
            )
        }

        if (showReviewDialog) {
            AlertDialog(
                onDismissRequest = { if (!reviewBusy) showReviewDialog = false },
                title = { Text(if (reviewByQueueId.containsKey(selectedQueueId)) "Edit your review" else "Rate your visit") },
                text = {
                    Column {
                        Text("$selectedBusinessName • Token #${selectedToken.toString().padStart(3, '0')}", fontSize = 11.sp, color = grayText)
                        Spacer(Modifier.height(14.dp))
                        Text("How was your experience?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = darkText)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            (1..5).forEach { star ->
                                TextButton(
                                    onClick = { selectedRating = star },
                                    enabled = !reviewBusy,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(if (star <= selectedRating) "★" else "☆", fontSize = 31.sp, color = if (star <= selectedRating) orange else Color(0xFFBDB7C5))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = selectedReviewText,
                            onValueChange = { if (it.length <= 300) selectedReviewText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Review (optional)") },
                            placeholder = { Text("Tell others about your experience") },
                            minLines = 3,
                            maxLines = 5,
                            enabled = !reviewBusy,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${selectedReviewText.length}/300", fontSize = 9.sp, color = grayText, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                        if (reviewMessage.isNotEmpty()) {
                            Spacer(Modifier.height(7.dp))
                            Text(reviewMessage, fontSize = 10.sp, color = red)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { submitReview() }, enabled = !reviewBusy) {
                        if (reviewBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = purple)
                        else Text(if (reviewByQueueId.containsKey(selectedQueueId)) "Update" else "Submit", color = purple, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = { TextButton(onClick = { showReviewDialog = false }, enabled = !reviewBusy) { Text("Cancel") } }
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = purple) }
            return
        }

        Column(Modifier.fillMaxSize().background(background)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBackClick, contentPadding = PaddingValues(0.dp)) { Text("‹", fontSize = 30.sp, color = purple) }
                Spacer(Modifier.width(10.dp))
                Column { Text("My Queue", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = darkText); Text("Your live place in line", fontSize = 11.sp, color = grayText) }
            }

            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                if (queue == null) {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
                        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎟️", fontSize = 40.sp); Spacer(Modifier.height(10.dp)); Text("You're not in a queue", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = darkText); Spacer(Modifier.height(5.dp)); Text("Join an approved business from Home to get a token.", fontSize = 11.sp, color = grayText, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(5.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(55.dp).clip(RoundedCornerShape(17.dp)).background(purpleLight), contentAlignment = Alignment.Center) { Text("🏪", fontSize = 27.sp) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) { Text(businessName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = darkText); Text(category, fontSize = 11.sp, color = grayText) }
                                Box(Modifier.clip(RoundedCornerShape(50.dp)).background(if (status == "called") Color(0xFFFFF1DD) else greenLight).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(if (status == "called") "● Called" else "● Live", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (status == "called") orange else green) }
                            }
                            Spacer(Modifier.height(18.dp))
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(purpleLight).padding(vertical = 20.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("YOUR TOKEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = grayText); Text(if (token > 0) "#${token.toString().padStart(3, '0')}" else "#---", fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = purple); Text(if (status == "called") "It's your turn now" else "You're currently in line", fontSize = 10.sp, color = grayText) } }
                            Spacer(Modifier.height(18.dp))
                            Text("Your position", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = darkText); Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).clip(CircleShape).background(purple)); Spacer(Modifier.width(8.dp)); Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(10.dp)).background(if (status == "called") purple else Color(0xFFE8E2F2))); Spacer(Modifier.width(8.dp)); Box(Modifier.size(12.dp).clip(CircleShape).background(if (status == "called") purple else Color(0xFFD7D1DF))) }
                            Spacer(Modifier.height(6.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Joined", fontSize = 9.sp, color = grayText); Text("Your turn", fontSize = 9.sp, color = grayText) }
                            Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatBox("People ahead", peopleAhead.toString(), purple, Modifier.weight(1f)); StatBox("Estimated wait", "$waitMinutes min", purple, Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (status == "called") Color(0xFFFFF4E5) else Color.White), elevation = CardDefaults.cardElevation(2.dp)) { Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text(if (status == "called") "🔔" else "✓", fontSize = 19.sp) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(if (status == "called") "It's your turn" else "You're in the queue", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = darkText); Spacer(Modifier.height(3.dp)); Text(if (status == "called") "Please visit the business now." else if (!businessOpen) "Business is currently closed." else if (queuePaused) "New joins are paused; your place is safe." else "Your position updates automatically.", fontSize = 10.sp, color = grayText) }; Text("LIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (status == "called") orange else green) } }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { showLeaveDialog = true }, enabled = !actionBusy && status == "waiting", modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = red, disabledContainerColor = Color(0xFFF1EFF3), disabledContentColor = grayText), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCACA))) { if (actionBusy) CircularProgressIndicator(Modifier.size(18.dp), color = red, strokeWidth = 2.dp) else Text("Leave Queue", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }

                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp)); Text("Recent queue history", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = darkText); Spacer(Modifier.height(9.dp))
                    history.forEach { item ->
                        val itemStatus = item["status"] as? String ?: ""
                        val itemToken = (item["tokenNumber"] as? Number)?.toInt() ?: 0
                        val itemBusiness = item["businessName"] as? String ?: "Business"
                        val itemQueueId = item["__id__"] as? String
                        val existingReview = itemQueueId?.let { reviewByQueueId[it] }
                        val isServed = itemStatus == "served"
                        val hasReview = existingReview != null
                        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(itemBusiness, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = darkText)
                                    Text(if (itemToken > 0) "Token #${itemToken.toString().padStart(3, '0')}" else "Queue entry", fontSize = 10.sp, color = grayText)
                                    if (isServed && itemQueueId != null) {
                                        Spacer(Modifier.height(6.dp))
                                        TextButton(onClick = { openReview(item) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp), enabled = !reviewBusy) {
                                            Text(if (hasReview) "⭐ ${((existingReview?.get("rating") as? Number)?.toInt() ?: 0)}  Edit review" else "☆  Rate this visit", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = purple)
                                        }
                                    }
                                }
                                Text(if (itemStatus == "cancelled") "Left" else itemStatus.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (itemStatus == "served") green else if (itemStatus == "skipped") orange else if (itemStatus == "cancelled") red else grayText)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(25.dp))
            }
        }
    }
    @Composable
    fun StatBox(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
        Card(modifier, RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.fillMaxWidth().padding(13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
                Spacer(Modifier.height(3.dp)); Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color(0xFF77717E))
            }
        }
    }


    @Composable
    fun ProfileScreen(
        onBackClick: () -> Unit,
        onMyQueueClick: () -> Unit,
        onPrivacySecurityClick: () -> Unit,
        onLogout: () -> Unit
    ) {
        val purple = Color(0xFF6C3CEB)
        val purpleDark = Color(0xFF43209D)
        val purpleSoft = Color(0xFFF1EAFF)
        val background = Color(0xFFF8F6FC)
        val dark = Color(0xFF211C32)
        val gray = Color(0xFF77717E)
        val green = Color(0xFF249B68)
        val red = Color(0xFFD64545)
        val redSoft = Color(0xFFFFF1F1)
        val email = FirebaseAuth.getInstance().currentUser?.email ?: "MyTurn Customer"

        Column(Modifier.fillMaxSize().background(background)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(purpleDark, purple, Color(0xFF8C62F5))
                        )
                    )
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onBackClick,
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("‹", fontSize = 32.sp, color = Color.White) }

                        Spacer(Modifier.width(10.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                "Profile",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                "Your MyTurn account",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = .75f)
                            )
                        }

                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = .16f)),
                            contentAlignment = Alignment.Center
                        ) { Text("⚙️", fontSize = 19.sp) }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(Modifier.height(14.dp))

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(Color.White),
                    elevation = CardDefaults.cardElevation(5.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFEBDDFF), Color(0xFFF4EEFF))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) { Text("👤", fontSize = 39.sp) }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "MyTurn Customer",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = dark
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(email, fontSize = 11.sp, color = gray)

                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(purpleSoft)
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "CUSTOMER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = purple
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "ACCOUNT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = gray,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(17.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(purpleSoft),
                                contentAlignment = Alignment.Center
                            ) { Text("✉️", fontSize = 20.sp) }

                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Email address", fontSize = 10.sp, color = gray)
                                Spacer(Modifier.height(3.dp))
                                Text(email, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dark)
                            }

                            Box(
                                Modifier.clip(RoundedCornerShape(50.dp)).background(Color(0xFFEAF8F1))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) { Text("Verified", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = green) }
                        }

                        HorizontalDivider(color = Color(0xFFF0EDF3))

                        Row(
                            Modifier.fillMaxWidth().padding(17.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAF8F1)),
                                contentAlignment = Alignment.Center
                            ) { Text("✓", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = green) }

                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Account status", fontSize = 10.sp, color = gray)
                                Spacer(Modifier.height(3.dp))
                                Text("Active & secure", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dark)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "QUEUELESS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = gray,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { onMyQueueClick() }.padding(17.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(purpleSoft),
                                contentAlignment = Alignment.Center
                            ) { Text("🎟️", fontSize = 20.sp) }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text("My Queue", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dark)
                                Spacer(Modifier.height(3.dp))
                                Text("View your active and past queues", fontSize = 10.sp, color = gray)
                            }
                            Text("›", fontSize = 25.sp, color = gray)
                        }

                        HorizontalDivider(color = Color(0xFFF0EDF3))

                        Row(
                            Modifier.fillMaxWidth().clickable { onPrivacySecurityClick() }.padding(17.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF4F5F8)),
                                contentAlignment = Alignment.Center
                            ) { Text("🛡️", fontSize = 20.sp) }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Privacy & Security", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dark)
                                Spacer(Modifier.height(3.dp))
                                Text("Manage account security and privacy", fontSize = 10.sp, color = gray)
                            }
                            Text("›", fontSize = 25.sp, color = gray)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = redSoft,
                        contentColor = red
                    )
                ) { Text("Log Out", fontSize = 13.sp, fontWeight = FontWeight.Bold) }

                Spacer(Modifier.height(24.dp))
                Text(
                    "MyTurn • Know your turn. Skip the wait.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    color = gray
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    @Composable
    fun PrivacySecurityScreen(
        onBackClick: () -> Unit,
        onLogout: () -> Unit,
        onAccountDeleted: () -> Unit
    ) {
        val purple = Color(0xFF6C3CEB)
        val purpleDark = Color(0xFF43209D)
        val purpleSoft = Color(0xFFF1EAFF)
        val background = Color(0xFFF8F6FC)
        val dark = Color(0xFF211C32)
        val gray = Color(0xFF77717E)
        val green = Color(0xFF249B68)
        val red = Color(0xFFD64545)
        val redSoft = Color(0xFFFFF1F1)
        val context = LocalContext.current
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val user = auth.currentUser
        val email = user?.email ?: ""
        var showDeleteDialog by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxSize().background(background)) {
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(purpleDark, purple, Color(0xFF8C62F5)))
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onBackClick,
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("‹", fontSize = 32.sp, color = Color.White) }

                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Privacy & Security", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("Your account, protected.", fontSize = 11.sp, color = Color.White.copy(alpha = .75f))
                    }
                    Text("🛡️", fontSize = 25.sp)
                }
            }

            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)
            ) {
                Spacer(Modifier.height(14.dp))

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(Color.White),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(Modifier.padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFEAF8F1)),
                            contentAlignment = Alignment.Center
                        ) { Text("✓", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = green) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Account protected", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = dark)
                            Spacer(Modifier.height(4.dp))
                            Text("Firebase Authentication protects your sign-in.", fontSize = 10.sp, color = gray, lineHeight = 15.sp)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("SIGN-IN & SECURITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = gray, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(purpleSoft), contentAlignment = Alignment.Center) {
                                Text("✉️", fontSize = 19.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Email address", fontSize = 10.sp, color = gray)
                                Spacer(Modifier.height(3.dp))
                                Text(if (email.isBlank()) "No email linked" else email, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dark)
                            }
                            Text("Secure", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = green)
                        }

                        HorizontalDivider(color = Color(0xFFF0EDF3))

                        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF4F5F8)), contentAlignment = Alignment.Center) {
                                Text("🔑", fontSize = 19.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Authentication", fontSize = 10.sp, color = gray)
                                Spacer(Modifier.height(3.dp))
                                Text("Firebase Authentication enabled", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dark)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("PRIVACY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = gray, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Your data stays tied to your MyTurn account.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dark)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "Queue information and profile data are stored in your Firebase-backed account. Keep your sign-in details private.",
                            fontSize = 10.sp,
                            color = gray,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("DANGER ZONE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = red, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(redSoft),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Delete account", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = red)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Permanently delete your MyTurn sign-in account. This cannot be undone.",
                            fontSize = 10.sp,
                            color = gray,
                            lineHeight = 15.sp
                        )
                        Spacer(Modifier.height(13.dp))
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = red)
                        ) { Text("Delete My Account", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = redSoft, contentColor = red)
                ) { Text("Log Out", fontSize = 13.sp, fontWeight = FontWeight.Bold) }

                Spacer(Modifier.height(24.dp))
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { if (!busy) showDeleteDialog = false },
                title = { Text("Delete account?", fontWeight = FontWeight.Bold) },
                text = { Text("This will permanently delete your Firebase sign-in account. You may need to sign in again recently before Firebase allows deletion.") },
                confirmButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            val current = auth.currentUser
                            if (current == null) {
                                showDeleteDialog = false
                                onAccountDeleted()
                            } else {
                                busy = true
                                current.delete()
                                    .addOnSuccessListener {
                                        db.collection("users").document(current.uid).delete()
                                        busy = false
                                        showDeleteDialog = false
                                        Toast.makeText(context, "Account deleted.", Toast.LENGTH_LONG).show()
                                        onAccountDeleted()
                                    }
                                    .addOnFailureListener { error ->
                                        busy = false
                                        Toast.makeText(context, error.message ?: "Account could not be deleted. Please sign in again and retry.", Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                    ) { Text("Delete", color = red, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
    @Composable
    fun AnalyticsMetric(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
        Card(modifier, RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
                Spacer(Modifier.height(3.dp)); Text(label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF77717E))
            }
        }
    }


    @Composable
    fun AnalyticsInfoRow(label: String, value: String) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 9.sp, color = Color(0xFF77717E))
            Text(value, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF252238))
        }
    }

    @Composable
    fun BusinessDashboardScreen(onEditProfile: () -> Unit, onLogout: () -> Unit) {
        val purple = Color(0xFF6C3CEB)
        val purpleDark = Color(0xFF4F27B8)
        val lightPurple = Color(0xFFF3EDFF)
        val background = Color(0xFFF8F6FC)
        val darkText = Color(0xFF252238)
        val grayText = Color(0xFF77717E)
        val green = Color(0xFF249B68)
        val red = Color(0xFFD64545)
        val orange = Color(0xFFE58B2A)
        val softOrange = Color(0xFFFFF4E5)
        val db = FirebaseFirestore.getInstance()
        val context = LocalContext.current
        val user = FirebaseAuth.getInstance().currentUser

        var businessId by remember { mutableStateOf<String?>(null) }
        var businessName by remember { mutableStateOf("Your Business") }
        var category by remember { mutableStateOf("Business") }
        var city by remember { mutableStateOf("") }
        var waitingCount by remember { mutableStateOf(0) }
        var estimatedWait by remember { mutableStateOf(0) }
        var isOpen by remember { mutableStateOf(true) }
        var queuePaused by remember { mutableStateOf(false) }
        var serviceTimeMinutes by remember { mutableStateOf(10) }
        var queueCustomers by remember { mutableStateOf<List<QueueCustomer>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var actionBusy by remember { mutableStateOf(false) }
        var actionMessage by remember { mutableStateOf("") }
        var todayServed by remember { mutableStateOf(0) }
        var todaySkipped by remember { mutableStateOf(0) }
        var todayCancelled by remember { mutableStateOf(0) }
        var todayAverageService by remember { mutableStateOf(0) }
        var reviewAverageForDashboard by remember { mutableStateOf(0.0) }
        var reviewCountForDashboard by remember { mutableStateOf(0) }
        var recentReviewsForDashboard by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
        var showServiceDialog by remember { mutableStateOf(false) }
        var showAnalyticsDialog by remember { mutableStateOf(false) }
        var analyticsSnapshot by remember { mutableStateOf(AnalyticsSnapshot()) }
        var analyticsLoading by remember { mutableStateOf(false) }
        var dashboardTab by remember { mutableStateOf(0) }

        fun loadAnalytics(id: String) {
            analyticsLoading = true
            db.collection("queues")
                .whereEqualTo("businessId", id)
                .whereEqualTo("businessOwnerId", user?.uid ?: "")
                .get()
                .addOnSuccessListener { snap ->
                    val now = System.currentTimeMillis()

                    val todayCal = Calendar.getInstance()
                    todayCal.set(Calendar.HOUR_OF_DAY, 0)
                    todayCal.set(Calendar.MINUTE, 0)
                    todayCal.set(Calendar.SECOND, 0)
                    todayCal.set(Calendar.MILLISECOND, 0)
                    val startOfToday = todayCal.timeInMillis

                    val sevenDaysCal = Calendar.getInstance()
                    sevenDaysCal.add(Calendar.DAY_OF_YEAR, -6)
                    sevenDaysCal.set(Calendar.HOUR_OF_DAY, 0)
                    sevenDaysCal.set(Calendar.MINUTE, 0)
                    sevenDaysCal.set(Calendar.SECOND, 0)
                    sevenDaysCal.set(Calendar.MILLISECOND, 0)
                    val startOfSevenDays = sevenDaysCal.timeInMillis

                    var todayVisitCount = 0
                    var todayServedCount = 0
                    var todaySkippedCount = 0
                    var todayLeftCount = 0
                    val todayWaitTimes = mutableListOf<Long>()
                    val todayServiceTimes = mutableListOf<Long>()

                    var last7Total = 0
                    var last7Served = 0
                    val dailyMap = linkedMapOf<String, Int>()
                    val dailyMillis = linkedMapOf<String, Long>()
                    val peakHours = mutableMapOf<Int, Int>()

                    for (i in 0..6) {
                        val c = Calendar.getInstance().apply {
                            timeInMillis = startOfSevenDays + i * 24L * 60L * 60L * 1000L
                        }
                        val key = SimpleDateFormat("dd MMM", Locale.getDefault()).format(c.time)
                        dailyMap[key] = 0
                        dailyMillis[key] = c.timeInMillis
                    }

                    snap.documents.forEach { d ->
                        val joinedAt = d.getTimestamp("joinedAt")?.toDate()?.time ?: return@forEach
                        val status = d.getString("status") ?: return@forEach

                        if (joinedAt >= startOfSevenDays && joinedAt <= now) {
                            last7Total++

                            val key = SimpleDateFormat("dd MMM", Locale.getDefault()).format(java.util.Date(joinedAt))
                            if (dailyMap.containsKey(key)) {
                                dailyMap[key] = (dailyMap[key] ?: 0) + 1
                            }

                            val joinedCalendar = Calendar.getInstance().apply { timeInMillis = joinedAt }
                            val hour = joinedCalendar.get(Calendar.HOUR_OF_DAY)
                            peakHours[hour] = (peakHours[hour] ?: 0) + 1

                            if (status == "served") last7Served++
                        }

                        if (joinedAt >= startOfToday && joinedAt <= now) {
                            todayVisitCount++
                            when (status) {
                                "served" -> todayServedCount++
                                "skipped" -> todaySkippedCount++
                                "cancelled", "removed" -> todayLeftCount++
                            }

                            val calledAt = d.getTimestamp("calledAt")?.toDate()?.time ?: 0L
                            val servedAt = d.getTimestamp("servedAt")?.toDate()?.time ?: 0L

                            if (calledAt > joinedAt) {
                                val wait = (calledAt - joinedAt) / 60000L
                                if (wait in 1..240) todayWaitTimes.add(wait)
                            }

                            if (status == "served" && servedAt > calledAt && calledAt > 0L) {
                                val service = (servedAt - calledAt) / 60000L
                                if (service in 1..240) todayServiceTimes.add(service)
                            }
                        }
                    }

                    val todayTotal = todayVisitCount
                    val completed = todayServedCount + todaySkippedCount
                    val completionRate = if (todayTotal == 0) 0 else ((completed * 100.0) / todayTotal).roundToInt()

                    val peakHourNumber = peakHours.maxByOrNull { it.value }?.key
                    val peakHour = if (peakHourNumber == null) {
                        "Not enough data"
                    } else {
                        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, peakHourNumber) }
                        val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, (peakHourNumber + 1) % 24) }
                        val fmt = SimpleDateFormat("h a", Locale.getDefault())
                        "${fmt.format(start.time)} – ${fmt.format(end.time)}"
                    }

                    val dailyCounts = dailyMap.entries.map { it.key to it.value }
                    val bestDay = dailyCounts.maxByOrNull { it.second }
                    val bestDayText = if (bestDay == null || bestDay.second == 0) {
                        "Not enough data"
                    } else {
                        "${bestDay.first} (${bestDay.second} visits)"
                    }

                    todayServed = todayServedCount
                    todaySkipped = todaySkippedCount
                    todayCancelled = todayLeftCount
                    todayAverageService = if (todayServiceTimes.isEmpty()) 0 else todayServiceTimes.average().roundToInt()

                    analyticsSnapshot = AnalyticsSnapshot(
                        todayTotal = todayTotal,
                        todayServed = todayServedCount,
                        todaySkipped = todaySkippedCount,
                        todayLeft = todayLeftCount,
                        todayCompletionRate = completionRate,
                        todayAverageWait = if (todayWaitTimes.isEmpty()) 0 else todayWaitTimes.average().roundToInt(),
                        todayAverageService = if (todayServiceTimes.isEmpty()) 0 else todayServiceTimes.average().roundToInt(),
                        todayPeakHour = peakHour,
                        last7DaysTotal = last7Total,
                        last7DaysServed = last7Served,
                        last7DaysAveragePerDay = if (last7Total == 0) 0 else (last7Total / 7.0).roundToInt(),
                        last7DaysBestDay = bestDayText,
                        dailyCounts = dailyCounts
                    )
                    analyticsLoading = false
                }
                .addOnFailureListener {
                    analyticsLoading = false
                    actionMessage = it.message ?: "Unable to load analytics."
                }
        }

        fun loadBusinessReviews(id: String) {
            db.collection("reviews")
                .whereEqualTo("businessId", id)
                .whereEqualTo("businessOwnerId", user?.uid ?: "")
                .get()
                .addOnSuccessListener { snap ->
                    val docs = snap.documents
                    val ratings = docs.mapNotNull { it.getLong("rating")?.toDouble() }
                    reviewCountForDashboard = ratings.size
                    reviewAverageForDashboard = if (ratings.isEmpty()) 0.0 else ratings.average()
                    recentReviewsForDashboard = docs
                        .sortedByDescending { it.getTimestamp("updatedAt")?.toDate()?.time ?: 0L }
                        .take(5)
                        .map { it.data ?: emptyMap<String, Any?>() }
                }
        }

        fun reindexWaiting(id: String) {
            db.collection("queues")
                .whereEqualTo("businessId", id)
                .whereEqualTo("businessOwnerId", user?.uid ?: "")
                .get()
                .addOnSuccessListener { snap ->
                    snap.documents
                        .filter { it.getString("status") == "waiting" }
                        .sortedBy { it.getLong("tokenNumber") ?: Long.MAX_VALUE }
                        .chunked(450)
                        .forEach { chunk ->
                            val batch = db.batch()
                            chunk.forEachIndexed { index, doc ->
                                batch.update(doc.reference, "queuePosition", index + 1)
                            }
                            batch.commit()
                        }
                }
        }

        fun refreshStats(id: String) {
            db.collection("queues")
                .whereEqualTo("businessId", id)
                .whereEqualTo("businessOwnerId", user?.uid ?: "")
                .get()
                .addOnSuccessListener { snap ->
                    val count = snap.documents.count { it.getString("status") == "waiting" }
                    waitingCount = count
                    estimatedWait = count * serviceTimeMinutes
                    db.collection("businesses").document(id).update(
                        mapOf("waitingCount" to count, "estimatedWait" to estimatedWait)
                    )
                }
        }

        DisposableEffect(user?.uid) {
            if (user == null) {
                loading = false
                onDispose { }
            } else {
                val listener = db.collection("businesses")
                    .whereEqualTo("ownerId", user.uid)
                    .limit(1)
                    .addSnapshotListener { snap, error ->
                        if (error == null && snap != null) {
                            val d = snap.documents.firstOrNull()
                            if (d != null) {
                                businessId = d.id
                                businessName = d.getString("name") ?: "Your Business"
                                category = d.getString("category") ?: "Business"
                                city = d.getString("city") ?: ""
                                waitingCount = (d.getLong("waitingCount") ?: 0L).toInt()
                                estimatedWait = (d.getLong("estimatedWait") ?: 0L).toInt()
                                isOpen = d.getBoolean("isOpen") ?: true
                                queuePaused = d.getBoolean("queuePaused") ?: false
                                serviceTimeMinutes = (d.getLong("serviceTimeMinutes") ?: 10L).toInt().coerceIn(1, 120)

                                val defaults = mutableMapOf<String, Any>()
                                if (d.getLong("nextTokenNumber") == null) defaults["nextTokenNumber"] = 1
                                if (d.getLong("serviceTimeMinutes") == null) defaults["serviceTimeMinutes"] = 10
                                if (d.getBoolean("queuePaused") == null) defaults["queuePaused"] = false
                                if (d.getLong("totalServed") == null) defaults["totalServed"] = 0
                                if (d.getLong("totalSkipped") == null) defaults["totalSkipped"] = 0
                                if (defaults.isNotEmpty()) db.collection("businesses").document(d.id).update(defaults)

                                loadAnalytics(d.id)
                                loadBusinessReviews(d.id)
                                refreshStats(d.id)
                            }
                            loading = false
                        } else if (error != null) {
                            loading = false
                        }
                    }
                onDispose { listener.remove() }
            }
        }

        DisposableEffect(businessId) {
            val id = businessId
            if (id == null) {
                onDispose { }
            } else {
                val listener = db.collection("queues")
                    .whereEqualTo("businessId", id)
                    .whereEqualTo("businessOwnerId", user?.uid ?: "")
                    .addSnapshotListener { snap, error ->
                        if (error == null && snap != null) {
                            queueCustomers = snap.documents.mapNotNull { doc ->
                                val status = doc.getString("status") ?: return@mapNotNull null
                                if (status != "waiting" && status != "called") return@mapNotNull null
                                QueueCustomer(
                                    doc.id,
                                    doc.getString("userEmail") ?: "Customer",
                                    status,
                                    doc.getTimestamp("joinedAt")?.toDate()?.time ?: 0L,
                                    (doc.getLong("tokenNumber") ?: 0L).toInt(),
                                    (doc.getLong("queuePosition") ?: 0L).toInt()
                                )
                            }.sortedWith(
                                compareBy<QueueCustomer> { if (it.status == "called") 0 else 1 }
                                    .thenBy { it.tokenNumber }
                            )
                        }
                    }
                onDispose { listener.remove() }
            }
        }

        fun endTodayQueue() {
            val id = businessId ?: return
            if (actionBusy) return
            actionBusy = true
            db.collection("queues")
                .whereEqualTo("businessId", id)
                .whereEqualTo("businessOwnerId", user?.uid ?: "")
                .get()
                .addOnSuccessListener { snap ->
                    val activeDocs = snap.documents.filter {
                        it.getString("status") == "waiting" || it.getString("status") == "called"
                    }
                    val chunks = activeDocs.chunked(450)
                    fun commitChunk(index: Int) {
                        if (index >= chunks.size) {
                            db.collection("businesses").document(id).update(
                                mapOf(
                                    "waitingCount" to 0,
                                    "estimatedWait" to 0,
                                    "currentServingToken" to 0,
                                    "currentServingEmail" to "",
                                    "queuePaused" to true
                                )
                            ).addOnSuccessListener {
                                actionBusy = false
                                actionMessage = "Today's queue has been closed."
                                Toast.makeText(context, "Today's queue closed.", Toast.LENGTH_SHORT).show()
                            }.addOnFailureListener { e ->
                                actionBusy = false
                                actionMessage = e.message ?: "Unable to close queue."
                            }
                            return
                        }
                        val batch = db.batch()
                        chunks[index].forEach {
                            batch.update(it.reference, mapOf(
                                "status" to "cancelled",
                                "completedAt" to com.google.firebase.Timestamp.now()
                            ))
                        }
                        batch.commit()
                            .addOnSuccessListener { commitChunk(index + 1) }
                            .addOnFailureListener { e ->
                                actionBusy = false
                                actionMessage = e.message ?: "Unable to close queue."
                            }
                    }
                    commitChunk(0)
                }
                .addOnFailureListener { e ->
                    actionBusy = false
                    actionMessage = e.message ?: "Unable to load queue."
                }
        }

        fun updateBusiness(fields: Map<String, Any>) {
            val id = businessId ?: return
            if (actionBusy) return
            actionBusy = true
            db.collection("businesses").document(id).update(fields)
                .addOnSuccessListener {
                    fields["queuePaused"]?.let { queuePaused = it as Boolean }
                    fields["isOpen"]?.let { isOpen = it as Boolean }
                    fields["serviceTimeMinutes"]?.let { serviceTimeMinutes = (it as Number).toInt() }
                    fields["waitingCount"]?.let { waitingCount = (it as Number).toInt() }
                    fields["estimatedWait"]?.let { estimatedWait = (it as Number).toInt() }
                    actionBusy = false
                    actionMessage = "Updated successfully."
                }
                .addOnFailureListener { error ->
                    actionBusy = false
                    actionMessage = error.message ?: "Unable to update business."
                    Toast.makeText(context, actionMessage, Toast.LENGTH_LONG).show()
                }
        }

        fun callNext() {
            val id = businessId ?: return
            if (actionBusy || queuePaused || !isOpen || queueCustomers.any { it.status == "called" }) return
            val next = queueCustomers.filter { it.status == "waiting" }.minByOrNull { it.tokenNumber } ?: return
            actionBusy = true
            val qRef = db.collection("queues").document(next.id)
            val bRef = db.collection("businesses").document(id)
            db.runTransaction { transaction ->
                val q = transaction.get(qRef)
                val b = transaction.get(bRef)
                if (q.getString("status") != "waiting") throw IllegalStateException("This customer is no longer waiting.")
                val oldCount = (b.getLong("waitingCount") ?: 0L).coerceAtLeast(0L)
                val service = (b.getLong("serviceTimeMinutes") ?: 10L).coerceIn(1L, 120L)
                val newCount = (oldCount - 1L).coerceAtLeast(0L)
                transaction.update(qRef, mapOf(
                    "status" to "called",
                    "calledAt" to com.google.firebase.Timestamp.now(),
                    "queuePosition" to 0
                ))
                transaction.update(bRef, mapOf(
                    "waitingCount" to newCount,
                    "estimatedWait" to newCount * service,
                    "currentServingToken" to next.tokenNumber,
                    "currentServingEmail" to next.userEmail
                ))
                null
            }.addOnSuccessListener {
                actionBusy = false
                actionMessage = "Called token #${next.tokenNumber}."
                reindexWaiting(id)
                refreshStats(id)
                Toast.makeText(context, "Customer called successfully.", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                actionBusy = false
                actionMessage = e.message ?: "Unable to call customer."
            }
        }

        fun finishCurrent(finalStatus: String) {
            val id = businessId ?: return
            if (actionBusy) return
            val current = queueCustomers.firstOrNull { it.status == "called" } ?: return
            actionBusy = true
            val qRef = db.collection("queues").document(current.id)
            val bRef = db.collection("businesses").document(id)
            val now = com.google.firebase.Timestamp.now()
            val qFields = if (finalStatus == "served") {
                mapOf<String, Any>("status" to "served", "servedAt" to now, "completedAt" to now)
            } else {
                mapOf<String, Any>("status" to "skipped", "completedAt" to now)
            }
            db.runTransaction { transaction ->
                val q = transaction.get(qRef)
                val b = transaction.get(bRef)
                if (q.getString("status") != "called") throw IllegalStateException("Customer is no longer being served.")
                transaction.update(qRef, qFields)
                if (finalStatus == "served") {
                    transaction.update(bRef, mapOf(
                        "totalServed" to (b.getLong("totalServed") ?: 0L) + 1L,
                        "currentServingToken" to 0,
                        "currentServingEmail" to ""
                    ))
                } else {
                    transaction.update(bRef, mapOf(
                        "totalSkipped" to (b.getLong("totalSkipped") ?: 0L) + 1L,
                        "currentServingToken" to 0,
                        "currentServingEmail" to ""
                    ))
                }
                null
            }.addOnSuccessListener {
                actionBusy = false
                actionMessage = if (finalStatus == "served") "Customer served successfully." else "Customer skipped."
                refreshStats(id)
                reindexWaiting(id)
                loadAnalytics(id)
                Toast.makeText(
                    context,
                    if (finalStatus == "served") "Customer served." else "Customer skipped.",
                    Toast.LENGTH_SHORT
                ).show()
            }.addOnFailureListener { e ->
                actionBusy = false
                actionMessage = e.message ?: "Unable to update customer."
            }
        }

        fun removeCustomer(customer: QueueCustomer) {
            val id = businessId ?: return
            if (actionBusy || customer.status != "waiting") return
            actionBusy = true
            db.collection("queues").document(customer.id)
                .update(mapOf(
                    "status" to "removed",
                    "completedAt" to com.google.firebase.Timestamp.now()
                ))
                .addOnSuccessListener {
                    actionBusy = false
                    refreshStats(id)
                    reindexWaiting(id)
                    loadAnalytics(id)
                    Toast.makeText(context, "Customer removed.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    actionBusy = false
                    actionMessage = e.message ?: "Unable to remove customer."
                }
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize().background(background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(lightPurple),
                        contentAlignment = Alignment.Center
                    ) { Text("🏪", fontSize = 30.sp) }
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(color = purple, strokeWidth = 2.5.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Loading your workspace…", fontSize = 11.sp, color = grayText)
                }
            }
            return
        }

        val current = queueCustomers.firstOrNull { it.status == "called" }
        val waiting = queueCustomers.filter { it.status == "waiting" }.sortedBy { it.tokenNumber }
        val ratingText = if (reviewCountForDashboard > 0) String.format(Locale.getDefault(), "%.1f", reviewAverageForDashboard) else "—"

        Column(Modifier.fillMaxSize().background(background)) {
            // Premium workspace header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(purpleDark, purple, Color(0xFF8D63F7))))
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) { Text("🏪", fontSize = 25.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("BUSINESS WORKSPACE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.72f), letterSpacing = 1.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(businessName, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                            Text(if (city.isBlank()) category else "$category • $city", fontSize = 10.sp, color = Color.White.copy(alpha = 0.78f), maxLines = 1)
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(50.dp)).background(Color.White.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                when { !isOpen -> "● CLOSED"; queuePaused -> "● PAUSED"; else -> "● LIVE" },
                                fontSize = 8.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Card(Modifier.weight(1f), RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.13f)), elevation = CardDefaults.cardElevation(0.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("WAITING", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.sp)
                                Spacer(Modifier.height(3.dp))
                                Text(waitingCount.toString(), fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                        Card(Modifier.weight(1f), RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.13f)), elevation = CardDefaults.cardElevation(0.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("EST. WAIT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.sp)
                                Spacer(Modifier.height(3.dp))
                                Text("$estimatedWait min", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                        Card(Modifier.weight(1f), RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.13f)), elevation = CardDefaults.cardElevation(0.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("RATING", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.sp)
                                Spacer(Modifier.height(3.dp))
                                Text(if (ratingText == "—") "—" else "★ $ratingText", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Tab switcher
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("⌂" to "Overview", "🎟" to "Queue", "★" to "Reviews", "⚙" to "Settings").forEachIndexed { index, pair ->
                    val selected = dashboardTab == index
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(15.dp)).background(if (selected) lightPurple else Color.White)
                            .clickable(enabled = !actionBusy) { dashboardTab = index }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(pair.first, fontSize = 15.sp, color = if (selected) purple else grayText)
                            Spacer(Modifier.height(2.dp))
                            Text(pair.second, fontSize = 8.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium, color = if (selected) purple else grayText)
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                when (dashboardTab) {
                    0 -> {
                        Spacer(Modifier.height(3.dp))
                        Text("Today at a glance", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                        Text("Keep your queue moving smoothly.", fontSize = 10.sp, color = grayText)
                        Spacer(Modifier.height(10.dp))

                        Card(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (isOpen && !queuePaused) Color(0xFFEAF8F1) else if (queuePaused) softOrange else Color(0xFFFFEEEE)), contentAlignment = Alignment.Center) {
                                        Text(if (!isOpen) "🔒" else if (queuePaused) "Ⅱ" else "✓", fontSize = 18.sp)
                                    }
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(if (!isOpen) "Business is closed" else if (queuePaused) "Queue is paused" else "Queue is live", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = darkText)
                                        Text(if (!isOpen) "Customers cannot join right now." else if (queuePaused) "Existing customers keep their place." else "Customers can join your queue.", fontSize = 9.sp, color = grayText)
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { updateBusiness(mapOf("queuePaused" to !queuePaused)) }, enabled = !actionBusy && isOpen, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = lightPurple, contentColor = purple)) {
                                        Text(if (queuePaused) "▶ Resume queue" else "Ⅱ Pause queue", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(onClick = { updateBusiness(mapOf("isOpen" to !isOpen)) }, enabled = !actionBusy, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isOpen) Color(0xFFFFEEEE) else Color(0xFFEAF8F1), contentColor = if (isOpen) red else green)) {
                                        Text(if (isOpen) "Close business" else "Open business", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            AnalyticsMetric("Served today", todayServed.toString(), green, Modifier.weight(1f))
                            AnalyticsMetric("Skipped", todaySkipped.toString(), orange, Modifier.weight(1f))
                            AnalyticsMetric("Left", todayCancelled.toString(), red, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = lightPurple), elevation = CardDefaults.cardElevation(0.dp)) {
                            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) { Text("📊", fontSize = 21.sp) }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Business analytics", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = darkText)
                                    Text("Understand visits, wait time and peak hours.", fontSize = 9.sp, color = grayText)
                                }
                                TextButton(onClick = { businessId?.let { loadAnalytics(it) }; showAnalyticsDialog = true }) { Text("View", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = purple) }
                            }
                        }
                    }

                    1 -> {
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Live queue", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                                Text("Manage customers in real time.", fontSize = 10.sp, color = grayText)
                            }
                            Box(Modifier.clip(RoundedCornerShape(50.dp)).background(lightPurple).padding(horizontal = 10.dp, vertical = 6.dp)) { Text("$waitingCount waiting", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = purple) }
                        }
                        Spacer(Modifier.height(10.dp))

                        Card(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
                            Column(Modifier.padding(17.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(lightPurple), contentAlignment = Alignment.Center) { Text("🎟️", fontSize = 24.sp) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("NOW SERVING", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = grayText, letterSpacing = 1.sp)
                                        Text(if (current != null) "#${current.tokenNumber.toString().padStart(3, '0')}" else "No customer", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = purple)
                                        if (current != null) Text(current.userEmail, fontSize = 9.sp, color = grayText, maxLines = 1)
                                    }
                                    Box(Modifier.clip(RoundedCornerShape(50.dp)).background(if (current != null) Color(0xFFEAF8F1) else Color(0xFFF3F1F5)).padding(horizontal = 9.dp, vertical = 6.dp)) { Text(if (current != null) "LIVE" else "IDLE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (current != null) green else grayText) }
                                }
                                Spacer(Modifier.height(13.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { callNext() }, enabled = !actionBusy && current == null && waiting.isNotEmpty() && isOpen && !queuePaused, modifier = Modifier.weight(1f).height(45.dp), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = purple)) {
                                        Text(if (actionBusy) "Working…" else "Call next", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(onClick = { finishCurrent("served") }, enabled = !actionBusy && current != null, modifier = Modifier.weight(1f).height(45.dp), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = green)) {
                                        Text("✓ Served", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(onClick = { finishCurrent("skipped") }, enabled = !actionBusy && current != null, modifier = Modifier.weight(1f).height(45.dp), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = red)) {
                                        Text("Skip", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(Modifier.padding(15.dp)) {
                                Text("Waiting list", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = darkText)
                                Spacer(Modifier.height(8.dp))
                                if (waiting.isEmpty()) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("✨", fontSize = 28.sp)
                                        Spacer(Modifier.height(5.dp))
                                        Text("No customers waiting", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = darkText)
                                        Text("New joins will appear here.", fontSize = 9.sp, color = grayText)
                                    }
                                } else {
                                    waiting.take(10).forEachIndexed { index, customer ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(39.dp).clip(CircleShape).background(lightPurple), contentAlignment = Alignment.Center) { Text(customer.tokenNumber.toString().padStart(2, '0'), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = purple) }
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(customer.userEmail, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = darkText, maxLines = 1)
                                                Text(if (customer.queuePosition > 0) "Position ${customer.queuePosition} • ${((customer.queuePosition - 1).coerceAtLeast(0) * serviceTimeMinutes)} min est." else "Waiting", fontSize = 8.sp, color = grayText)
                                            }
                                            TextButton(onClick = { removeCustomer(customer) }, enabled = !actionBusy) { Text("Remove", fontSize = 9.sp, color = red, fontWeight = FontWeight.Bold) }
                                        }
                                        if (index < minOf(waiting.size, 10) - 1) HorizontalDivider(color = Color(0xFFF0EDF3))
                                    }
                                    if (waiting.size > 10) Text("+ ${waiting.size - 10} more", fontSize = 9.sp, color = purple, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(top = 7.dp), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    2 -> {
                        Spacer(Modifier.height(3.dp))
                        Text("Customer reviews", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                        Text("See what customers think about your service.", fontSize = 10.sp, color = grayText)
                        Spacer(Modifier.height(11.dp))
                        Card(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(lightPurple), contentAlignment = Alignment.Center) { Text("★", fontSize = 35.sp, color = purple) }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (reviewCountForDashboard > 0) "$ratingText / 5" else "No ratings yet", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                                    Text("${reviewCountForDashboard} review${if (reviewCountForDashboard == 1) "" else "s"}", fontSize = 10.sp, color = grayText)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (recentReviewsForDashboard.isEmpty()) {
                            Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💬", fontSize = 30.sp)
                                    Spacer(Modifier.height(7.dp))
                                    Text("No reviews yet", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = darkText)
                                    Text("Completed visits can turn into reviews.", fontSize = 9.sp, color = grayText)
                                }
                            }
                        } else {
                            recentReviewsForDashboard.forEach { review ->
                                val rating = (review["rating"] as? Number)?.toInt() ?: 0
                                val text = review["reviewText"] as? String ?: ""
                                val name = review["customerName"] as? String ?: "Customer"
                                Card(Modifier.fillMaxWidth().padding(bottom = 9.dp), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(36.dp).clip(CircleShape).background(lightPurple), contentAlignment = Alignment.Center) { Text("👤", fontSize = 16.sp) }
                                            Spacer(Modifier.width(9.dp))
                                            Column(Modifier.weight(1f)) { Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = darkText); Text("$rating/5", fontSize = 9.sp, color = purple) }
                                            Text("★".repeat(rating.coerceIn(0,5)), fontSize = 11.sp, color = orange)
                                        }
                                        if (text.isNotBlank()) { Spacer(Modifier.height(9.dp)); Text(text, fontSize = 10.sp, color = grayText, lineHeight = 15.sp) }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        Spacer(Modifier.height(3.dp))
                        Text("Workspace settings", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                        Text("Configure how your business runs on MyTurn.", fontSize = 10.sp, color = grayText)
                        Spacer(Modifier.height(11.dp))

                        Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column {
                                Row(Modifier.fillMaxWidth().clickable(enabled = !actionBusy) { onEditProfile() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(lightPurple), contentAlignment = Alignment.Center) { Text("✎", fontSize = 20.sp, color = purple) }
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) { Text("Edit business profile", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = darkText); Text("Business name, address, hours and location", fontSize = 9.sp, color = grayText) }
                                    Text("›", fontSize = 21.sp, color = grayText)
                                }
                                HorizontalDivider(color = Color(0xFFF0EDF3))
                                Row(Modifier.fillMaxWidth().clickable(enabled = !actionBusy) { showServiceDialog = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEFF7FF)), contentAlignment = Alignment.Center) { Text("⏱", fontSize = 20.sp) }
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) { Text("Service time", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = darkText); Text("$serviceTimeMinutes min average per customer", fontSize = 9.sp, color = grayText) }
                                    Text("›", fontSize = 21.sp, color = grayText)
                                }
                                HorizontalDivider(color = Color(0xFFF0EDF3))
                                Row(Modifier.fillMaxWidth().clickable(enabled = !actionBusy) { endTodayQueue() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF4E5)), contentAlignment = Alignment.Center) { Text("⏹", fontSize = 18.sp) }
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) { Text("End today’s queue", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = darkText); Text("Close today’s queue and stop new joins", fontSize = 9.sp, color = grayText) }
                                    Text("›", fontSize = 21.sp, color = grayText)
                                }
                            }
                        }

                        Spacer(Modifier.height(13.dp))
                        Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F1)), elevation = CardDefaults.cardElevation(0.dp)) {
                            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text("✓", fontSize = 20.sp, color = green, fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) { Text("Business approved", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = darkText); Text("Your business is visible to customers.", fontSize = 9.sp, color = grayText) }
                            }
                        }
                        Spacer(Modifier.height(13.dp))
                        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEEEE), contentColor = red)) { Text("Log Out", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        if (actionMessage.isNotEmpty()) { Spacer(Modifier.height(9.dp)); Text(actionMessage, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 9.sp, color = red) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (showAnalyticsDialog) {
            AlertDialog(
                onDismissRequest = { showAnalyticsDialog = false },
                title = {
                    Column {
                        Text(
                            "Business Analytics",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = darkText
                        )
                        Text(
                            "Queue performance & customer flow",
                            fontSize = 10.sp,
                            color = grayText
                        )
                    }
                },
                text = {
                    if (analyticsLoading) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = purple)
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                "TODAY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = purple
                            )
                            Spacer(Modifier.height(8.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnalyticsMetric(
                                    "Visits",
                                    analyticsSnapshot.todayTotal.toString(),
                                    darkText,
                                    Modifier.weight(1f)
                                )
                                AnalyticsMetric(
                                    "Served",
                                    analyticsSnapshot.todayServed.toString(),
                                    green,
                                    Modifier.weight(1f)
                                )
                                AnalyticsMetric(
                                    "Skipped",
                                    analyticsSnapshot.todaySkipped.toString(),
                                    orange,
                                    Modifier.weight(1f)
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnalyticsMetric(
                                    "Left",
                                    analyticsSnapshot.todayLeft.toString(),
                                    red,
                                    Modifier.weight(1f)
                                )
                                AnalyticsMetric(
                                    "Complete",
                                    "${analyticsSnapshot.todayCompletionRate}%",
                                    purple,
                                    Modifier.weight(1f)
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Card(
                                Modifier.fillMaxWidth(),
                                RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = lightPurple)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        "Customer flow",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = darkText
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    AnalyticsInfoRow(
                                        "Average wait",
                                        if (analyticsSnapshot.todayAverageWait > 0) "${analyticsSnapshot.todayAverageWait} min" else "No data"
                                    )
                                    AnalyticsInfoRow(
                                        "Average service",
                                        if (analyticsSnapshot.todayAverageService > 0) "${analyticsSnapshot.todayAverageService} min" else "No data"
                                    )
                                    AnalyticsInfoRow("Peak join hour", analyticsSnapshot.todayPeakHour)
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                "LAST 7 DAYS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = purple
                            )
                            Spacer(Modifier.height(8.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnalyticsMetric(
                                    "Total visits",
                                    analyticsSnapshot.last7DaysTotal.toString(),
                                    darkText,
                                    Modifier.weight(1f)
                                )
                                AnalyticsMetric(
                                    "Served",
                                    analyticsSnapshot.last7DaysServed.toString(),
                                    green,
                                    Modifier.weight(1f)
                                )
                                AnalyticsMetric(
                                    "Daily avg",
                                    analyticsSnapshot.last7DaysAveragePerDay.toString(),
                                    purple,
                                    Modifier.weight(1f)
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Card(
                                Modifier.fillMaxWidth(),
                                RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        "Daily visits",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = darkText
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    val maxValue = (analyticsSnapshot.dailyCounts.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                                    analyticsSnapshot.dailyCounts.forEach { (day, count) ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                day,
                                                modifier = Modifier.width(48.dp),
                                                fontSize = 9.sp,
                                                color = grayText
                                            )
                                            Box(
                                                Modifier
                                                    .weight(1f)
                                                    .height(10.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFECE7F7))
                                            ) {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth(count.toFloat() / maxValue.toFloat())
                                                        .height(10.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(purple)
                                                )
                                            }
                                            Text(
                                                count.toString(),
                                                modifier = Modifier.width(28.dp),
                                                textAlign = TextAlign.End,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = darkText
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Busiest day: ${analyticsSnapshot.last7DaysBestDay}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = grayText
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Analytics are calculated from queue activity stored in Firestore.",
                                fontSize = 8.sp,
                                color = grayText
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAnalyticsDialog = false }) {
                        Text("Done", fontWeight = FontWeight.Bold, color = purple)
                    }
                }
            )
        }

        if (showServiceDialog) {
            AlertDialog(
                onDismissRequest = { if (!actionBusy) showServiceDialog = false },
                title = {
                    Text("Queue settings", fontWeight = FontWeight.Bold, color = darkText)
                },
                text = {
                    Column {
                        Text(
                            "Set the average time you need for one customer. This updates estimated waiting time.",
                            fontSize = 11.sp,
                            color = grayText,
                            lineHeight = 17.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        listOf(5, 10, 15, 20, 30).forEach { minutes ->
                            OutlinedButton(
                                onClick = {
                                    val id = businessId
                                    if (id != null && !actionBusy) {
                                        showServiceDialog = false
                                        updateBusiness(mapOf("serviceTimeMinutes" to minutes))
                                        estimatedWait = waitingCount * minutes
                                    }
                                },
                                enabled = !actionBusy,
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = if (minutes == serviceTimeMinutes) {
                                    ButtonDefaults.outlinedButtonColors(contentColor = purple)
                                } else {
                                    ButtonDefaults.outlinedButtonColors(contentColor = darkText)
                                }
                            ) {
                                Text(
                                    "$minutes minutes per customer${if (minutes == serviceTimeMinutes) "  ✓" else ""}",
                                    fontSize = 10.sp,
                                    fontWeight = if (minutes == serviceTimeMinutes) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.height(7.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showServiceDialog = false }, enabled = !actionBusy) {
                        Text("Cancel", color = purple, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
    @Composable
    fun MiniMetric(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
        Card(modifier, RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.fillMaxWidth().padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
                Spacer(Modifier.height(2.dp)); Text(label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF77717E))
            }
        }
    }


    @Composable
    fun BusinessEditScreen(onBackClick: () -> Unit, onSaved: () -> Unit) {
        val purple = Color(0xFF6C3CEB)
        val purpleDark = Color(0xFF4F27B8)
        val lightPurple = Color(0xFFF3EDFF)
        val background = Color(0xFFF9F7FF)
        val darkText = Color(0xFF252238)
        val grayText = Color(0xFF77717E)
        val green = Color(0xFF249B68)
        val red = Color(0xFFD64545)
        val db = FirebaseFirestore.getInstance()
        val context = LocalContext.current
        val user = FirebaseAuth.getInstance().currentUser
        var businessId by remember { mutableStateOf<String?>(null) }
        var businessName by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("") }
        var ownerName by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var city by remember { mutableStateOf("") }
        var openingTime by remember { mutableStateOf("") }
        var closingTime by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var businessLatitude by remember { mutableStateOf<Double?>(null) }
        var businessLongitude by remember { mutableStateOf<Double?>(null) }
        var locationStatus by remember { mutableStateOf("") }
        var isGettingLocation by remember { mutableStateOf(false) }
        val businessLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                isGettingLocation = true
                getCurrentDeviceLocation(context) { point, error ->
                    isGettingLocation = false
                    if (point != null) { businessLatitude = point.latitude; businessLongitude = point.longitude; locationStatus = "Business location captured ✓" }
                    else locationStatus = error ?: "Unable to get business location."
                }
            } else locationStatus = "Location permission denied."
        }
        var loading by remember { mutableStateOf(true) }
        var saving by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf("") }

        DisposableEffect(user?.uid) {
            val uid = user?.uid
            if (uid == null) { loading = false; onDispose { } } else {
                val listener = db.collection("businesses").whereEqualTo("ownerId", uid).limit(1).addSnapshotListener { snap, error ->
                    if (error == null && snap != null) {
                        snap.documents.firstOrNull()?.let { d ->
                            businessId = d.id
                            businessName = d.getString("name") ?: ""
                            category = d.getString("category") ?: ""
                            ownerName = d.getString("ownerName") ?: ""
                            phone = d.getString("phone") ?: ""
                            address = d.getString("address") ?: ""
                            city = d.getString("city") ?: ""
                            businessLatitude = d.getDouble("latitude")
                            businessLongitude = d.getDouble("longitude")
                            openingTime = d.getString("openingTime") ?: ""
                            closingTime = d.getString("closingTime") ?: ""
                            description = d.getString("description") ?: ""
                        }
                        loading = false
                    } else if (error != null) { loading = false; message = error.message ?: "Unable to load profile." }
                }
                onDispose { listener.remove() }
            }
        }

        fun save() {
            val id = businessId ?: return
            if (businessName.isBlank() || category.isBlank() || ownerName.isBlank() || phone.isBlank() || address.isBlank() || city.isBlank()) { message = "Please fill all required fields."; return }
            if (businessLatitude == null || businessLongitude == null) { message = "Please set your exact business location."; return }
            if (saving) return
            saving = true; message = ""
            val fields = mapOf<String, Any>(
                "name" to businessName.trim(), "category" to category.trim(), "ownerName" to ownerName.trim(), "phone" to phone.trim(),
                "address" to address.trim(), "city" to city.trim(), "latitude" to businessLatitude!!, "longitude" to businessLongitude!!,
                "geohash" to GeoFireUtils.getGeoHashForLocation(GeoLocation(businessLatitude!!, businessLongitude!!)),
                "openingTime" to openingTime.trim(), "closingTime" to closingTime.trim(), "description" to description.trim()
            )
            db.collection("businesses").document(id).update(fields).addOnSuccessListener { saving = false; Toast.makeText(context, "Business profile updated.", Toast.LENGTH_SHORT).show(); onSaved() }
                .addOnFailureListener { e -> saving = false; message = e.message ?: "Unable to save profile." }
        }

        if (loading) {
            Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = purple) }
            return
        }

        Column(Modifier.fillMaxSize().background(background)) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(purpleDark, purple, Color(0xFF8D63F7))))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (!saving) onBackClick() }, contentPadding = PaddingValues(0.dp)) { Text("‹", fontSize = 32.sp, color = Color.White) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) { Text("Business profile", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Color.White); Text("Keep your customer-facing details up to date", fontSize = 9.sp, color = Color.White.copy(alpha = 0.78f)) }
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text("✎", fontSize = 19.sp, color = Color.White) }
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Business identity", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                        Text("The details customers see when they discover you.", fontSize = 9.sp, color = grayText)
                        Spacer(Modifier.height(13.dp))
                        OutlinedTextField(value = businessName, onValueChange = { businessName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Business name *") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = category, onValueChange = { category = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Category *") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3, maxLines = 4, shape = RoundedCornerShape(14.dp))
                    }
                }
                Spacer(Modifier.height(13.dp))
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Owner & contact", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                        Text("Keep your support details accurate.", fontSize = 9.sp, color = grayText)
                        Spacer(Modifier.height(13.dp))
                        OutlinedTextField(value = ownerName, onValueChange = { ownerName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Owner name *") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Phone number *") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    }
                }
                Spacer(Modifier.height(13.dp))
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Location", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                        Text("Used for customer Near Me discovery.", fontSize = 9.sp, color = grayText)
                        Spacer(Modifier.height(13.dp))
                        OutlinedTextField(value = address, onValueChange = { address = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Business address *") }, minLines = 2, maxLines = 3, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = city, onValueChange = { city = it }, modifier = Modifier.fillMaxWidth(), label = { Text("City *") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(lightPurple).padding(13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color.White), contentAlignment = Alignment.Center) { Text("📍", fontSize = 20.sp) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) { Text("Exact map location", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = darkText); Text(if (businessLatitude != null) "Location is set" else "Not set yet", fontSize = 9.sp, color = if (businessLatitude != null) green else grayText) }
                                OutlinedButton(onClick = {
                                    if (isGettingLocation) return@OutlinedButton
                                    if (hasLocationPermission(context)) {
                                        isGettingLocation = true
                                        getCurrentDeviceLocation(context) { point, error -> isGettingLocation = false; if (point != null) { businessLatitude = point.latitude; businessLongitude = point.longitude; locationStatus = "Business location captured ✓" } else locationStatus = error ?: "Unable to get business location." }
                                    } else businessLocationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }, enabled = !isGettingLocation, shape = RoundedCornerShape(12.dp)) { if (isGettingLocation) CircularProgressIndicator(Modifier.size(16.dp), color = purple, strokeWidth = 2.dp) else Text(if (businessLatitude != null) "Update" else "Set", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = purple) }
                            }
                            if (locationStatus.isNotBlank()) Text(locationStatus, fontSize = 8.sp, color = if (locationStatus.contains("✓")) green else red, modifier = Modifier.padding(top = 54.dp))
                        }
                    }
                }
                Spacer(Modifier.height(13.dp))
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Working hours", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = darkText); Text("Optional • shown to customers", fontSize = 9.sp, color = grayText) }; Text("OPTIONAL", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = grayText) }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedTextField(value = openingTime, onValueChange = { openingTime = it }, modifier = Modifier.weight(1f), label = { Text("Opening") }, placeholder = { Text("09:00 AM") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                            OutlinedTextField(value = closingTime, onValueChange = { closingTime = it }, modifier = Modifier.weight(1f), label = { Text("Closing") }, placeholder = { Text("08:00 PM") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                        }
                    }
                }
                Spacer(Modifier.height(15.dp))
                if (message.isNotBlank()) Text(message, Modifier.fillMaxWidth().padding(horizontal = 4.dp), textAlign = TextAlign.Center, fontSize = 10.sp, color = red)
                Button(onClick = { save() }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(53.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = purple)) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Save changes", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(25.dp))
            }
        }
    }


    @Composable
    fun BusinessRegisterScreen(onBackClick: () -> Unit, onApproved: () -> Unit) {
        val purple = Color(0xFF6C3CEB)
        val purpleDark = Color(0xFF4F27B8)
        val lightPurple = Color(0xFFF3EDFF)
        val background = Color(0xFFF9F7FF)
        val darkText = Color(0xFF252238)
        val grayText = Color(0xFF77717E)
        val green = Color(0xFF249B68)
        val red = Color(0xFFD64545)
        val context = LocalContext.current
        var businessName by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("") }
        var ownerName by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var city by remember { mutableStateOf("") }
        var openingTime by remember { mutableStateOf("") }
        var closingTime by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var businessLatitude by remember { mutableStateOf<Double?>(null) }
        var businessLongitude by remember { mutableStateOf<Double?>(null) }
        var locationStatus by remember { mutableStateOf("") }
        var isGettingLocation by remember { mutableStateOf(false) }
        val businessLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                isGettingLocation = true
                getCurrentDeviceLocation(context) { point, error -> isGettingLocation = false; if (point != null) { businessLatitude = point.latitude; businessLongitude = point.longitude; locationStatus = "Business location captured ✓" } else locationStatus = error ?: "Unable to get business location." }
            } else locationStatus = "Location permission denied."
        }
        var submitMessage by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }
        var submittedBusinessId by remember { mutableStateOf<String?>(null) }

        DisposableEffect(Unit) {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) onDispose { } else {
                val listener = FirebaseFirestore.getInstance().collection("businesses").whereEqualTo("ownerId", user.uid).limit(1).addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        snapshot.documents.firstOrNull()?.let { document -> submittedBusinessId = document.id; if (document.getString("status") == "approved") onApproved() }
                    }
                }
                onDispose { listener.remove() }
            }
        }
        DisposableEffect(submittedBusinessId) {
            val id = submittedBusinessId
            if (id == null) onDispose { } else {
                val listener = FirebaseFirestore.getInstance().collection("businesses").document(id).addSnapshotListener { document, error ->
                    if (error == null && document != null && document.exists()) {
                        when (document.getString("status")) { "approved" -> onApproved(); "rejected" -> submitMessage = "Your business application was rejected." }
                    }
                }
                onDispose { listener.remove() }
            }
        }

        Column(Modifier.fillMaxSize().background(background)) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(purpleDark, purple, Color(0xFF8D63F7))))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBackClick, contentPadding = PaddingValues(0.dp)) { Text("‹", fontSize = 32.sp, color = Color.White) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) { Text("Set up your business", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Color.White); Text("Get discovered and manage your queue", fontSize = 9.sp, color = Color.White.copy(alpha = 0.78f)) }
                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text("🏪", fontSize = 22.sp) }
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(lightPurple), contentAlignment = Alignment.Center) { Text("🚀", fontSize = 24.sp) }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) { Text("Let’s get your business live", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = darkText); Text("Required fields are marked with *", fontSize = 9.sp, color = grayText) }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("1" to "Business", "2" to "Contact", "3" to "Location").forEach { (n,t) -> Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(lightPurple).padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Text("$n  $t", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = purple) } } }
                    }
                }
                Spacer(Modifier.height(13.dp))
                Text("Business details", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                Text("Tell customers what you offer.", fontSize = 9.sp, color = grayText)
                Spacer(Modifier.height(10.dp))
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        OutlinedTextField(value = businessName, onValueChange = { businessName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Business name *") }, placeholder = { Text("e.g. Style Studio") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = category, onValueChange = { category = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Category *") }, placeholder = { Text("Salon, Clinic, Restaurant…") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, placeholder = { Text("Briefly describe your business") }, minLines = 3, maxLines = 4, shape = RoundedCornerShape(14.dp))
                    }
                }
                Spacer(Modifier.height(13.dp))
                Text("Owner & contact", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                Text("Used for your business profile and verification.", fontSize = 9.sp, color = grayText)
                Spacer(Modifier.height(10.dp))
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        OutlinedTextField(value = ownerName, onValueChange = { ownerName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Owner name *") }, placeholder = { Text("Full name") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Phone number *") }, placeholder = { Text("Business contact number") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    }
                }
                Spacer(Modifier.height(13.dp))
                Text("Location & hours", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = darkText)
                Text("Customers use this to find you nearby.", fontSize = 9.sp, color = grayText)
                Spacer(Modifier.height(10.dp))
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        OutlinedTextField(value = address, onValueChange = { address = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Business address *") }, placeholder = { Text("Shop, clinic or office address") }, minLines = 2, maxLines = 3, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = city, onValueChange = { city = it }, modifier = Modifier.fillMaxWidth(), label = { Text("City *") }, placeholder = { Text("e.g. Indore") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(lightPurple).padding(13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(Color.White), contentAlignment = Alignment.Center) { Text("📍", fontSize = 21.sp) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) { Text("Exact business location", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = darkText); Text(if (businessLatitude != null) "Ready for Near Me" else "Required for Near Me", fontSize = 9.sp, color = if (businessLatitude != null) green else grayText) }
                                OutlinedButton(onClick = {
                                    if (isGettingLocation) return@OutlinedButton
                                    if (hasLocationPermission(context)) {
                                        isGettingLocation = true
                                        getCurrentDeviceLocation(context) { point, error -> isGettingLocation = false; if (point != null) { businessLatitude = point.latitude; businessLongitude = point.longitude; locationStatus = "Business location captured ✓" } else locationStatus = error ?: "Unable to get business location." }
                                    } else businessLocationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }, enabled = !isGettingLocation, shape = RoundedCornerShape(12.dp)) { if (isGettingLocation) CircularProgressIndicator(Modifier.size(16.dp), color = purple, strokeWidth = 2.dp) else Text(if (businessLatitude != null) "Update" else "Set location", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = purple) }
                            }
                            if (locationStatus.isNotBlank()) Text(locationStatus, fontSize = 8.sp, color = if (locationStatus.contains("✓")) green else red, modifier = Modifier.padding(top = 54.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedTextField(value = openingTime, onValueChange = { openingTime = it }, modifier = Modifier.weight(1f), label = { Text("Opening") }, placeholder = { Text("09:00 AM") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                            OutlinedTextField(value = closingTime, onValueChange = { closingTime = it }, modifier = Modifier.weight(1f), label = { Text("Closing") }, placeholder = { Text("08:00 PM") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                        }
                    }
                }
                Spacer(Modifier.height(13.dp))
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F1)), elevation = CardDefaults.cardElevation(0.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) { Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text("✓", color = green, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(10.dp)); Column { Text("Admin review", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = darkText); Text("After submission, your business stays pending until an Admin approves it.", fontSize = 9.sp, color = grayText, lineHeight = 14.sp) } }
                }
                Spacer(Modifier.height(14.dp))
                if (submitMessage.isNotBlank()) {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = if (submitMessage.startsWith("Business submitted")) Color(0xFFEAF8F1) else Color(0xFFFFEEEE)), elevation = CardDefaults.cardElevation(0.dp)) {
                        Text(submitMessage, Modifier.padding(13.dp).fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (submitMessage.startsWith("Business submitted")) green else red)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Button(onClick = {
                    if (businessName.isBlank() || category.isBlank() || ownerName.isBlank() || phone.isBlank() || address.isBlank() || city.isBlank()) submitMessage = "Please fill all required fields."
                    else if (businessLatitude == null || businessLongitude == null) submitMessage = "Please set your exact business location first."
                    else {
                        val user = FirebaseAuth.getInstance().currentUser
                        if (user == null) submitMessage = "Please login first." else {
                            isSubmitting = true; submitMessage = ""
                            val businessData = hashMapOf(
                                "name" to businessName.trim(), "category" to category.trim(), "ownerName" to ownerName.trim(), "ownerId" to user.uid, "ownerEmail" to (user.email ?: ""), "phone" to phone.trim(), "address" to address.trim(), "city" to city.trim(), "latitude" to businessLatitude!!, "longitude" to businessLongitude!!,
                                "geohash" to GeoFireUtils.getGeoHashForLocation(GeoLocation(businessLatitude!!, businessLongitude!!)), "openingTime" to openingTime.trim(), "closingTime" to closingTime.trim(), "description" to description.trim(), "emoji" to "🏪", "waitingCount" to 0, "estimatedWait" to 0, "isOpen" to true, "queuePaused" to false, "nextTokenNumber" to 1, "serviceTimeMinutes" to 10, "currentServingToken" to 0, "currentServingEmail" to "", "totalServed" to 0, "totalSkipped" to 0, "status" to "pending", "createdAt" to com.google.firebase.Timestamp.now()
                            )
                            FirebaseFirestore.getInstance().collection("businesses").add(businessData).addOnSuccessListener { documentReference -> isSubmitting = false; submittedBusinessId = documentReference.id; submitMessage = "Business submitted successfully! Waiting for Admin approval." }.addOnFailureListener { error -> isSubmitting = false; submitMessage = error.message ?: "Failed to submit business." }
                        }
                    }
                }, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = purple)) {
                    if (isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Submit for Admin approval", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(25.dp))
            }
        }
    }

}

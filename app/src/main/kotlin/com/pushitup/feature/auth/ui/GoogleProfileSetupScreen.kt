package com.pushitup.feature.auth.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import com.yalantis.ucrop.UCrop
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pushitup.core.theme.*
import com.pushitup.core.ui.UserAvatar

/**
 * Shown once for new users — both Google and email sign-up.
 *
 * Google sign-up: editable username + display name, optional Google photo vs letter avatar.
 * Email sign-up:  username/displayName already fixed; user just picks a photo or keeps the letter.
 */
@Composable
fun GoogleProfileSetupScreen(
    onSetupComplete : () -> Unit,
    viewModel       : AuthViewModel = hiltViewModel(),
) {
    val uiState  by viewModel.uiState.collectAsState()
    val focus    = LocalFocusManager.current
    val context  = LocalContext.current
    val isGoogle = uiState.setupIsGoogleSignUp

    var username    by remember { mutableStateOf(uiState.setupSuggestedUsername) }
    var displayName by remember { mutableStateOf(uiState.setupSuggestedDisplayName) }

    // Photo selection state
    var pickedUri        by remember { mutableStateOf<Uri?>(null) }
    var pickedBytes      by remember { mutableStateOf<ByteArray?>(null) }
    // For Google: toggle between Google photo and letter
    var useLetterAvatar  by remember { mutableStateOf(false) }

    // ── Photo: gallery → UCrop → compress → bytes ────────────────────────────
    val cropDestFile = remember { File(context.cacheDir, "setup_crop.jpg") }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val croppedUri = UCrop.getOutput(result.data ?: return@rememberLauncherForActivityResult)
            ?: return@rememberLauncherForActivityResult

        val rawBytes = context.contentResolver.openInputStream(croppedUri)
            ?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult

        val original = android.graphics.BitmapFactory
            .decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: return@rememberLauncherForActivityResult

        val maxDim = 360
        val scaled = if (original.width > maxDim || original.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(original.width, original.height)
            android.graphics.Bitmap.createScaledBitmap(
                original,
                (original.width  * scale).toInt(),
                (original.height * scale).toInt(),
                true,
            )
        } else original

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 72, baos)
        if (scaled !== original) scaled.recycle()
        original.recycle()

        pickedBytes     = baos.toByteArray()
        pickedUri       = croppedUri
        useLetterAvatar = false
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val dest      = Uri.fromFile(cropDestFile)
        val darkBg    = android.graphics.Color.parseColor("#0D0D0F")
        val green     = android.graphics.Color.parseColor("#1DB954")
        val toolbarBg = android.graphics.Color.parseColor("#141416")
        val opts = UCrop.Options().apply {
            setToolbarColor(toolbarBg)
            setToolbarWidgetColor(android.graphics.Color.WHITE)
            setToolbarTitle("Set Profile Photo")
            setRootViewBackgroundColor(darkBg)
            setCircleDimmedLayer(true)
            setDimmedLayerColor(android.graphics.Color.argb(210, 13, 13, 15))
            setShowCropGrid(false)
            setShowCropFrame(false)
            setActiveControlsWidgetColor(green)
            setHideBottomControls(false)
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(75)
            setMaxBitmapSize(1024)
        }
        val cropIntent = UCrop.of(uri, dest)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(360, 360)
            .withOptions(opts)
            .getIntent(context)
        cropIntent.setClass(context, com.pushitup.core.ui.PushItCropActivity::class.java)
        cropLauncher.launch(cropIntent)
    }

    LaunchedEffect(uiState.success) {
        if (uiState.success) onSetupComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))

        Text(
            "Almost there!",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color      = TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Set up your profile",
            style     = MaterialTheme.typography.bodyMedium,
            color     = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        // ── Avatar picker ─────────────────────────────────────────────────────
        val previewName = displayName.ifBlank { username }.ifBlank { "?" }
        val googlePhotoUrl = uiState.setupGooglePhotoUrl

        // What to show in the preview
        val previewPhotoUrl: String? = when {
            pickedUri != null    -> pickedUri.toString()
            isGoogle && !useLetterAvatar && !googlePhotoUrl.isNullOrBlank() -> googlePhotoUrl
            else                 -> null
        }

        Box(contentAlignment = Alignment.BottomEnd) {
            // Avatar circle — tappable to pick photo
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .clickable { galleryLauncher.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) {
                UserAvatar(
                    photoUrl    = previewPhotoUrl,
                    displayName = previewName,
                    size        = 96.dp,
                    tierColor   = AccentGreen,
                    borderWidth = 2.dp,
                )
            }

            // Camera badge
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(AccentGreen)
                    .border(2.dp, BackgroundDeep, CircleShape)
                    .clickable { galleryLauncher.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.CameraAlt,
                    contentDescription = "Pick photo",
                    tint     = Color.Black,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Hint text
        Text(
            if (previewPhotoUrl != null) "Tap to change" else "Tap to add a photo",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )

        // "Use letter avatar" toggle — only shown for Google sign-up when a Google photo exists,
        // or when the user has picked a custom photo
        if ((isGoogle && !googlePhotoUrl.isNullOrBlank()) || pickedUri != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    useLetterAvatar = !useLetterAvatar
                    if (useLetterAvatar) pickedUri = null
                },
            ) {
                Checkbox(
                    checked = useLetterAvatar || pickedUri == null && previewPhotoUrl == null,
                    onCheckedChange = {
                        useLetterAvatar = it
                        if (it) pickedUri = null
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor   = AccentGreen,
                        checkmarkColor = Color.Black,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Use letter avatar instead",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Form ──────────────────────────────────────────────────────────────
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .background(SurfaceGlass, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Only show editable fields for Google sign-up
            if (isGoogle) {
                OutlinedTextField(
                    value         = username,
                    onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } },
                    label         = { Text("Username") },
                    placeholder   = { Text("e.g. pushking42", color = TextMuted) },
                    leadingIcon   = { Icon(Icons.Rounded.AlternateEmail, null, tint = TextMuted) },
                    supportingText = { Text("Min 3 chars · letters, numbers, underscores", color = TextMuted) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                    colors          = authFieldColors(),
                    modifier        = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value         = displayName,
                    onValueChange = { displayName = it },
                    label         = { Text("Display Name") },
                    placeholder   = { Text("How others see you", color = TextMuted) },
                    leadingIcon   = { Icon(Icons.Rounded.Person, null, tint = TextMuted) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    colors          = authFieldColors(),
                    modifier        = Modifier.fillMaxWidth(),
                )
            } else {
                // Email sign-up: show locked summary of what they entered
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.AlternateEmail, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    Column {
                        Text(uiState.setupSuggestedUsername, color = TextPrimary, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text(uiState.setupSuggestedDisplayName, color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (uiState.error != null) {
                Text(uiState.error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick  = {
                    focus.clearFocus()
                    if (isGoogle) {
                        val hasGooglePhoto = !uiState.setupGooglePhotoUrl.isNullOrBlank()
                        // Letter avatar only when explicitly requested OR no photo source at all
                        val finalUseLetterAvatar = useLetterAvatar || (pickedUri == null && !hasGooglePhoto)
                        viewModel.completeGoogleSetup(
                            username        = username,
                            displayName     = displayName,
                            useLetterAvatar = finalUseLetterAvatar,
                            imageBytes      = if (!finalUseLetterAvatar) pickedBytes else null,
                        )
                    } else {
                        viewModel.completeEmailSetup(
                            imageBytes = if (useLetterAvatar || pickedUri == null) null else pickedBytes,
                        )
                    }
                },
                enabled  = username.length >= 3 && !uiState.isLoading,
                colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Let's Go!", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

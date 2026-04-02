package com.speakmind.app.feature.geminichat.ui

import android.accounts.Account
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.path
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GEMINI_SCOPE = "https://www.googleapis.com/auth/generative-language.retriever"

@Composable
actual fun GoogleSignInButton(
    onSignedIn: (token: String, email: String) -> Unit,
    onError: (message: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GEMINI_SCOPE))
            .build()
    }
    val signInClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email ?: ""
            val androidAccount: Account? = account?.account

            isLoading = true
            scope.launch(Dispatchers.IO) {
                try {
                    val token = GoogleAuthUtil.getToken(
                        context,
                        androidAccount ?: return@launch,
                        "oauth2:https://www.googleapis.com/auth/generative-language.retriever"
                    )
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        onSignedIn(token, email)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        onError("Could not get access token: ${e.message}")
                    }
                }
            }
        } catch (e: ApiException) {
            isLoading = false
            onError("Sign-in failed: ${e.statusCode}")
        }
    }

    OutlinedButton(
        onClick = {
            signInClient.signOut().addOnCompleteListener {
                launcher.launch(signInClient.signInIntent)
            }
        },
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.sdp),
        shape = RoundedCornerShape(4.sdp),
        border = BorderStroke(1.sdp, Color(0xFFDADCE0)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF3C4043),
            disabledContainerColor = Color.White,
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.sdp),
                strokeWidth = 2.sdp,
                color = Color(0xFF4285F4),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoogleLogoIcon(modifier = Modifier.size(18.sdp))
                Spacer(modifier = Modifier.width(12.sdp))
                Text(
                    text = "Sign in with Google",
                    fontSize = 14.ssp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif,
                    color = Color(0xFF3C4043),
                )
            }
        }
    }
}

@Composable
private fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    val googleG = rememberVectorPainter(
        ImageVector.Builder(
            defaultWidth = 18.sdp,
            defaultHeight = 18.sdp,
            viewportWidth = 18f,
            viewportHeight = 18f,
        ).apply {
            // Blue part
            path(fill = SolidColor(Color(0xFF4285F4))) {
                moveTo(17.64f, 9.2f)
                curveTo(17.64f, 8.566f, 17.583f, 7.956f, 17.476f, 7.364f)
                horizontalLineTo(9f)
                verticalLineTo(10.845f)
                horizontalLineTo(13.844f)
                curveTo(13.635f, 11.97f, 13.0f, 12.923f, 12.048f, 13.561f)
                verticalLineTo(15.82f)
                horizontalLineTo(14.956f)
                curveTo(16.658f, 14.252f, 17.64f, 11.945f, 17.64f, 9.2f)
                close()
            }
            // Green part
            path(fill = SolidColor(Color(0xFF34A853))) {
                moveTo(9f, 18f)
                curveTo(11.43f, 18f, 13.467f, 17.194f, 14.956f, 15.82f)
                lineTo(12.048f, 13.561f)
                curveTo(11.243f, 14.101f, 10.212f, 14.42f, 9f, 14.42f)
                curveTo(6.656f, 14.42f, 4.672f, 12.837f, 3.964f, 10.71f)
                horizontalLineTo(0.957f)
                verticalLineTo(13.042f)
                curveTo(2.438f, 15.983f, 5.482f, 18f, 9f, 18f)
                close()
            }
            // Yellow part
            path(fill = SolidColor(Color(0xFFFBBC05))) {
                moveTo(3.964f, 10.71f)
                curveTo(3.784f, 10.17f, 3.682f, 9.593f, 3.682f, 9f)
                curveTo(3.682f, 8.407f, 3.784f, 7.83f, 3.964f, 7.29f)
                verticalLineTo(4.958f)
                horizontalLineTo(0.957f)
                curveTo(0.348f, 6.173f, 0f, 7.548f, 0f, 9f)
                curveTo(0f, 10.452f, 0.348f, 11.827f, 0.957f, 13.042f)
                lineTo(3.964f, 10.71f)
                close()
            }
            // Red part
            path(fill = SolidColor(Color(0xFFEA4335))) {
                moveTo(9f, 3.58f)
                curveTo(10.321f, 3.58f, 11.508f, 4.034f, 12.44f, 4.925f)
                lineTo(15.022f, 2.344f)
                curveTo(13.463f, 0.891f, 11.426f, 0f, 9f, 0f)
                curveTo(5.482f, 0f, 2.438f, 2.017f, 0.957f, 4.958f)
                lineTo(3.964f, 7.29f)
                curveTo(4.672f, 5.163f, 6.656f, 3.58f, 9f, 3.58f)
                close()
            }
        }.build()
    )
    Image(painter = googleG, contentDescription = null, modifier = modifier)
}

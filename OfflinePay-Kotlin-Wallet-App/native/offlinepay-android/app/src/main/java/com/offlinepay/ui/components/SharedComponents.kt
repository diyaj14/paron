package com.offlinepay.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinepay.ui.theme.GlacierBlue
import com.offlinepay.ui.theme.LineBlue
import com.offlinepay.ui.theme.MutedNavy
import com.offlinepay.ui.theme.PaleNavy
import com.offlinepay.ui.theme.SoftBlue
import com.offlinepay.ui.theme.TrustIndigo
import com.offlinepay.ui.theme.WealthBeige
import com.offlinepay.ui.theme.YieldBlue

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingArrow: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        enabled = enabled,
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = YieldBlue,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF98B6D0),
            disabledContentColor = Color.White
        )
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (trailingArrow) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun BackAction(onClick: () -> Unit, dark: Boolean = false) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = if (dark) Color.White else TrustIndigo
        )
    }
}

@Composable
fun MerchantIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(SoftBlue),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Storefront,
            contentDescription = "Merchant",
            tint = YieldBlue,
            modifier = Modifier.size(29.dp)
        )
    }
}

@Composable
fun MerchantRow(name: String, details: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFFFFEFA))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MerchantIcon()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 13.dp)
        ) {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TrustIndigo)
            Text(details, fontSize = 13.sp, color = MutedNavy, modifier = Modifier.padding(top = 3.dp))
        }
        if (onClick != null) {
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Open", tint = TrustIndigo)
            }
        }
    }
}

@Composable
fun OfflineNotice(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(SoftBlue)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFD5EBFA)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = TrustIndigo)
        }
        Text(
            text,
            modifier = Modifier.padding(start = 12.dp),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MutedNavy
        )
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Text(
        title,
        color = TrustIndigo,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.Bold
    )
    if (subtitle != null) {
        Text(
            subtitle,
            color = MutedNavy,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun DarkHero(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    illustration: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07182D), PaleNavy, Color(0xFF0A2A4E))
                )
            )
    ) {
        WaveArt(Modifier.matchParentSize(), dark = true)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            if (onBack != null) {
                BackAction(onBack, dark = true)
                Spacer(Modifier.height(18.dp))
            } else {
                Spacer(Modifier.height(42.dp))
            }
            Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                color = Color(0xFFC9D9ED),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
            if (illustration) {
                Spacer(Modifier.height(20.dp))
                PhoneIllustration(Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
fun WaveArt(modifier: Modifier = Modifier, dark: Boolean = false) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val wave = Path().apply {
            moveTo(0f, height * 0.73f)
            cubicTo(width * 0.28f, height * 0.48f, width * 0.47f, height * 0.94f, width, height * 0.56f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            wave,
            Brush.verticalGradient(
                if (dark) listOf(Color(0xFF0E64BC).copy(alpha = .5f), Color(0xFF06274D))
                else listOf(Color(0xFFDDF3FF), Color(0xFFFFFDF6))
            )
        )
        val secondWave = Path().apply {
            moveTo(0f, height * 0.86f)
            cubicTo(width * 0.32f, height * 0.61f, width * 0.62f, height * 1.03f, width, height * 0.73f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(secondWave, if (dark) Color(0xFF0E86EE).copy(alpha = .22f) else Color.White.copy(alpha = .68f))
    }
}

@Composable
fun PhoneIllustration(modifier: Modifier = Modifier) {
    Box(modifier.size(width = 104.dp, height = 122.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 67.dp, height = 110.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1D8DF0), Color(0xFF082A57)))
                )
                .padding(5.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xFF102D58)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF9CDEFF), modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(21.dp))
            .background(Color(0xFFFFFEFA))
            .padding(17.dp),
        content = { content() }
    )
}

@Composable
fun StatusBadge(text: String, pending: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(if (pending) Color(0xFFE1F2FF) else Color(0xFFEDF6FC))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            color = if (pending) YieldBlue else MutedNavy,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DemoBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TrustIndigo)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("DEVELOPMENT MODE", color = GlacierBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(
            "  •  Sample data only — no real payment",
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MutedNavy, fontSize = 13.sp)
        Text(
            value,
            color = TrustIndigo,
            fontSize = if (emphasize) 16.sp else 13.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun OutlineAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TrustIndigo)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

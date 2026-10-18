package com.vidgravity.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.*

@Composable internal fun VgTheme(dark:Boolean,content:@Composable ()->Unit) {
    val colors=if(dark) darkColorScheme(primary=Color(0xFF5DE0CE),onPrimary=Color(0xFF082B29),secondary=Color(0xFFBFA8FF),
        background=Color(0xFF101521),surface=Color(0xFF1A2232),surfaceVariant=Color(0xFF293449),onSurface=Color(0xFFF2F5FA),
        onBackground=Color(0xFFF2F5FA),onSurfaceVariant=Color(0xFFC0CADC),outline=Color(0xFF8290A8))
        else lightColorScheme(primary=Color(0xFF00695E),onPrimary=Color.White,secondary=Color(0xFF6044A7),
            background=Color(0xFFF5F7FC),surface=Color.White,surfaceVariant=Color(0xFFE5EAF4),onSurface=Color(0xFF182134),onBackground=Color(0xFF182134),onSurfaceVariant=Color(0xFF46556B),outline=Color(0xFF69778C))
    MaterialTheme(colorScheme=colors,shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp)),
        typography=Typography(headlineLarge=androidx.compose.ui.text.TextStyle(fontSize=30.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold),
            titleLarge=androidx.compose.ui.text.TextStyle(fontSize=22.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold)),content=content)
}
@Composable internal fun VgSection(title:String,detail:String?=null,content:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics {heading()})
        detail?.let {Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)}
        content()
    }}
}
@Composable internal fun VgDisclosure(title:String,initiallyOpen:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    var expanded by remember {mutableStateOf(initiallyOpen)}
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
        TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics {stateDescription=if(expanded) "Expanded" else "Collapsed"}) {
            Text(title,Modifier.weight(1f));Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
        }
        if(expanded) Column(verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }}
}

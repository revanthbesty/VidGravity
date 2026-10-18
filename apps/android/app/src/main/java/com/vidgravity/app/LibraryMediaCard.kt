package com.vidgravity.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable internal fun LibraryMediaCard(media:LibraryMedia,selected:Boolean,compact:Boolean,onSelected:(Boolean)->Unit,onOpen:()->Unit,onEdit:()->Unit,onFavorite:()->Unit,onRename:()->Unit,onDelete:()->Unit,onShare:()->Unit,modifier:Modifier=Modifier) {
    var menu by remember {mutableStateOf(false)}
    OutlinedCard(modifier.clickable(onClick=onOpen)) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        MediaThumbnail(media,Modifier.fillMaxWidth().height(if(compact) 112.dp else 144.dp))
        Text(media.displayName,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
        Text("${media.mimeType.substringBefore('/')} · ${java.lang.String.format(java.util.Locale.ROOT,"%.1f MB",media.sizeBytes/1_000_000.0)}",style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Checkbox(selected,onSelected,modifier=Modifier.semantics {contentDescription="Select ${media.displayName}"})
            if(!compact) TextButton(onClick=onEdit) {Text("Studio")} else Spacer(Modifier.weight(1f))
            Box {IconButton(onClick={menu=true}) {Icon(Icons.Default.MoreVert,"Actions for ${media.displayName}")}
                DropdownMenu(menu,{menu=false}) {
                    listOf("Open preview" to onOpen,"Open in Studio" to onEdit,(if(media.isFavorite) "Remove favorite" else "Favorite") to onFavorite,"Rename" to onRename,"Share" to onShare,"Delete" to onDelete).forEach {(label,action)->DropdownMenuItem(text={Text(label)},onClick={menu=false;action()})}
                }
            }
        }
    }}
}

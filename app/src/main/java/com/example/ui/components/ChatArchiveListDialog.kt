package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.ChatArchiveEntity
import com.example.data.db.ChatArchiveMapper
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ChatArchiveListDialog(
    archives: List<ChatArchiveEntity>,
    activeArchiveId: String?,
    onDismiss: () -> Unit,
    onSelectArchive: (ChatArchiveEntity) -> Unit,
    onToggleFavorite: (ChatArchiveEntity) -> Unit,
    onDeleteArchive: (ChatArchiveEntity) -> Unit,
    onImportNewFile: () -> Unit
) {
    var archiveToDelete by remember { mutableStateOf<ChatArchiveEntity?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEEF2FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "보관함",
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "대화방 보관함",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .padding(horizontal = 7.dp, vertical = 1.5.dp)
                                ) {
                                    Text(
                                        text = "${archives.size}개",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }
                            Text(
                                text = "터치 한 번으로 원하는 대화방으로 즉시 전환해요",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Archive Card List
                if (archives.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "📂", fontSize = 42.sp)
                            Text(
                                text = "보관된 대화방이 아직 없어요",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = "카카오톡에서 '대화 내용 내보내기'로 저장한\n.txt 파일을 불러오면 여기에 안전하게 자동 보관돼요.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 24.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(archives, key = { it.id }) { archive ->
                            val isActive = archive.id == activeArchiveId
                            val participants = remember(archive.topParticipantsJson) {
                                ChatArchiveMapper.participantsFromJson(archive.topParticipantsJson)
                            }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
                                ),
                                border = BorderStroke(
                                    width = if (isActive) 1.8.dp else 1.dp,
                                    color = if (isActive) Color(0xFF10B981) else Color(0xFFE2E8F0)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 3.dp else 1.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isActive) {
                                            onSelectArchive(archive)
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Row 1: State Badge + Star & Delete Actions
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isActive) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFDCFCE7))
                                                    .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "🟢 현재 열람 중",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF15803D)
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFEEF2FF))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "👆 터치하여 전환",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4F46E5)
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Favorite Star Button
                                            IconButton(
                                                onClick = { onToggleFavorite(archive) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (archive.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "즐겨찾기",
                                                    tint = if (archive.isFavorite) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            // Delete Button
                                            IconButton(
                                                onClick = { archiveToDelete = archive },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "삭제",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Row 2: Room Title & File Name
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = archive.roomTitle,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "📄 ${archive.fileName}",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Row 3: Dates & Message Count Summary
                                    val formattedMsgs = NumberFormat.getNumberInstance(Locale.KOREA).format(archive.totalMessages)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "📅 ${archive.startDate} ~ ${archive.endDate} (${archive.totalDays}일간) • 총 ${formattedMsgs}건",
                                            fontSize = 11.sp,
                                            color = Color(0xFF475569),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // Row 4: Participants
                                    if (participants.isNotEmpty()) {
                                        Text(
                                            text = "👥 ${participants.joinToString(", ")}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Button: Import New File
                Button(
                    onClick = {
                        onImportNewFile()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F172A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "새 파일",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "새 카톡 대화 파일 불러오기 (.txt)",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    archiveToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { archiveToDelete = null },
            title = {
                Text(
                    text = "대화방 보관 삭제",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    text = "'${target.roomTitle}' 대화방을 보관함에서 삭제하시겠습니까?\n내부 캐시 파일도 함께 삭제됩니다.",
                    fontSize = 13.sp,
                    color = Color(0xFF475569),
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteArchive(target)
                        archiveToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("삭제", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { archiveToDelete = null }) {
                    Text("취소", color = Color(0xFF64748B))
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White
        )
    }
}

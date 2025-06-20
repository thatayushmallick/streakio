package com.example.streakio.ui.components // Or your preferred package

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.streakio.viewmodel.StreakHistoryUiState
import com.example.streakio.viewmodel.UserDailyEntryStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StreakHistoryTable(
    historyState: StreakHistoryUiState,
    modifier: Modifier = Modifier
) {
    if (historyState.isLoading) {
        Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (historyState.errorMessage != null) {
        Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(historyState.errorMessage, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        return
    }

    if (historyState.dateHeaders.isEmpty() || historyState.userEntries.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("No history data available yet or no participants.", textAlign = TextAlign.Center)
        }
        return
    }

    val dayColumnWidth: Dp = 60.dp
    val nameColumnWidth: Dp = 120.dp
    val totalTableWidth = nameColumnWidth + (dayColumnWidth * historyState.dateHeaders.size)

    Column(modifier = modifier) {
        Text(
            "Last 7 Days Entry Log",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
        )

        // Horizontal scroll for the entire table content (header + rows)
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                // Header Row
                Row(
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .fillMaxWidth(1f) // Ensure it tries to fill available, scroll handles overflow
                        .width(totalTableWidth) // Explicit width for scroll calculation
                ) {
                    // Empty corner for alignment or User Name header
                    TableCell(text = "User", weight = nameColumnWidth, alignment = TextAlign.Start, isHeader = true)
                    historyState.dateHeaders.forEach { date ->
                        TableCell(
                            text = date.format(DateTimeFormatter.ofPattern("E\nMMM d")), // e.g., Mon\nJul 15
                            weight = dayColumnWidth,
                            isHeader = true
                        )
                    }
                }

                // User Rows
                historyState.userEntries.forEach { userStatus ->
                    Row(
                        Modifier
                            .fillMaxWidth(1f)
                            .width(totalTableWidth) // Explicit width
                            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        TableCell(
                            text = userStatus.userName,
                            weight = nameColumnWidth,
                            alignment = TextAlign.Start
                        )
                        historyState.dateHeaders.forEach { date ->
                            val logged = userStatus.entriesByDate[date] ?: false
                            TableCell(
                                text = if (logged) "✅" else "❌", // Or use an Icon, or just "Yes"/"No"
                                weight = dayColumnWidth,
                                color = if (logged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Dp, // Use Dp for fixed width cells
    alignment: TextAlign = TextAlign.Center,
    isHeader: Boolean = false,
    color: Color = Color.Unspecified // Use default text color
) {
    Text(
        text = text,
        Modifier
            .width(weight) // Use fixed width
            .padding(8.dp),
        textAlign = alignment,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        fontSize = if (isHeader) 12.sp else 14.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = color
    )
}

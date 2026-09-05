package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.TalkSummaryRepository
import com.example.data.db.AppDatabase
import com.example.ui.screens.TalkSummaryMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TalkSummaryViewModel
import com.example.ui.viewmodel.TalkSummaryViewModelFactory

class MainActivity : ComponentActivity() {

  private val database by lazy { AppDatabase.getDatabase(applicationContext) }
  private val repository by lazy { TalkSummaryRepository(database) }
  
  private val viewModel: TalkSummaryViewModel by viewModels {
    TalkSummaryViewModelFactory(application, repository)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          TalkSummaryMainScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}

package com.cbw21.moci.ui

import androidx.compose.runtime.Composable
import com.cbw21.moci.ui.screens.BookScreen
import com.cbw21.moci.ui.screens.LibraryScreen
import com.cbw21.moci.ui.screens.MistakeResultScreen
import com.cbw21.moci.ui.screens.MistakesScreen
import com.cbw21.moci.ui.screens.QuizScreen
import com.cbw21.moci.ui.screens.ResultScreen

@Composable
fun MociApp(viewModel: AppViewModel) {
    when (val screen = viewModel.screen) {
        Screen.Library -> LibraryScreen(viewModel)

        is Screen.BookDetail -> {
            val book = viewModel.findBook(screen.bookId)
            if (book == null) {
                LibraryScreen(viewModel)
            } else {
                BookScreen(viewModel, book, screen.direction)
            }
        }

        is Screen.Mistakes -> {
            val book = viewModel.findBook(screen.bookId)
            if (book == null) {
                LibraryScreen(viewModel)
            } else {
                MistakesScreen(viewModel, book, screen.direction)
            }
        }

        is Screen.Quiz -> {
            val book = viewModel.findBook(screen.bookId)
            val session = viewModel.quiz
            if (book != null && session != null &&
                session.bookId == screen.bookId &&
                session.direction == screen.direction &&
                session.groupIndex == screen.groupIndex
            ) {
                QuizScreen(viewModel, book, session)
            } else if (book != null) {
                BookScreen(viewModel, book, screen.direction)
            } else {
                LibraryScreen(viewModel)
            }
        }

        is Screen.MistakeQuiz -> {
            val book = viewModel.findBook(screen.bookId)
            val session = viewModel.quiz
            if (book != null && session != null &&
                session.groupIndex == null &&
                session.bookId == screen.bookId &&
                session.direction == screen.direction
            ) {
                QuizScreen(viewModel, book, session)
            } else if (book != null) {
                MistakesScreen(viewModel, book, screen.direction)
            } else {
                LibraryScreen(viewModel)
            }
        }

        is Screen.MistakeResults -> {
            val book = viewModel.findBook(screen.bookId)
            val result = viewModel.mistakeResult
            if (book != null && result != null &&
                result.bookId == screen.bookId &&
                result.direction == screen.direction
            ) {
                MistakeResultScreen(viewModel, book, screen.direction, result)
            } else if (book != null) {
                MistakesScreen(viewModel, book, screen.direction)
            } else {
                LibraryScreen(viewModel)
            }
        }

        is Screen.Results -> {
            val book = viewModel.findBook(screen.bookId)
            if (book == null) {
                LibraryScreen(viewModel)
            } else {
                ResultScreen(viewModel, book, screen.direction, screen.groupIndex)
            }
        }
    }
}

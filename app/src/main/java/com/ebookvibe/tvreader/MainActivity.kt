package com.ebookvibe.tvreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ebookvibe.tvreader.ui.browser.BrowserScreen
import com.ebookvibe.tvreader.ui.home.HomeScreen
import com.ebookvibe.tvreader.ui.reader.ReaderScreen
import com.ebookvibe.tvreader.ui.theme.TvReaderTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** A PDF handed to us by another app (VIEW intent), waiting to be opened. */
    private val pendingOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val books = (application as ReaderApp).books

        setContent {
            TvReaderTheme {
                val nav = rememberNavController()
                fun openBook(id: String) {
                    books.touch(id)
                    nav.navigate(Routes.reader(id)) { launchSingleTop = true }
                }

                LaunchedEffect(Unit) {
                    pendingOpen.collect { id ->
                        if (id != null) {
                            pendingOpen.value = null
                            nav.popBackStack(Routes.HOME, inclusive = false)
                            openBook(id)
                        }
                    }
                }

                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    // Instant screen switches: cheaper on low-end TV hardware.
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onOpenBrowser = { nav.navigate(Routes.BROWSER) },
                            onOpenBook = ::openBook,
                            onOpenUri = { openBook(it.toString()) },
                        )
                    }
                    composable(Routes.BROWSER) {
                        BrowserScreen(
                            onOpenFile = { path ->
                                // Replace the browser so BACK from the reader goes home.
                                nav.popBackStack(Routes.HOME, inclusive = false)
                                openBook(path)
                            },
                            onOpenUri = { uri ->
                                nav.popBackStack(Routes.HOME, inclusive = false)
                                openBook(uri.toString())
                            },
                            onExit = { nav.popBackStack() },
                        )
                    }
                    composable(
                        route = Routes.READER,
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        ReaderScreen(bookId = id, onClose = { nav.closeReader() })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val id = if (uri.scheme == "file") uri.path else uri.toString()
        if (uri.scheme == "content") {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        pendingOpen.value = id
    }

    private fun NavHostController.closeReader() {
        if (!popBackStack(Routes.HOME, inclusive = false)) navigate(Routes.HOME)
    }
}

object Routes {
    const val HOME = "home"
    const val BROWSER = "browser"
    const val READER = "reader?id={id}"
    fun reader(id: String) = "reader?id=" + android.net.Uri.encode(id)
}

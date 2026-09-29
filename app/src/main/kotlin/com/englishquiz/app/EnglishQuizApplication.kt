package com.englishquiz.app

import android.app.Application
import com.englishquiz.app.data.AppContainer

class EnglishQuizApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }
}

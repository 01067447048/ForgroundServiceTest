package com.example.foregroundservcieterst

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds

/**
 * Created by Jaehyeon on 2023/07/10.
 */
class RunningService: Service() {

    //scope
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    //notification
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val notification by lazy {
        NotificationCompat.Builder(this, "running_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Run is active")
            .setContentText("Elapsed time : 00:00:00")
    }

    //timer
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val _elapsedTime = MutableStateFlow(0L)
    private var isRunning = false
    private val stopWatchText = _elapsedTime.map { millis ->
        LocalTime.ofNanoOfDay(millis * 1_000_000).format(formatter)
    }.stateIn(
        scope,
        SharingStarted.WhileSubscribed(5000L),
        "00:00:00"
    )


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.START.toString() -> start()
            Actions.STOP.toString() -> {
                isRunning = false
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun start() {
        isRunning = true

        startForeground(1, notification.build())

        CoroutineScope(Dispatchers.IO).launch {
            stopWatchText.collectLatest {
                notification.setContentText("Elapsed time : $it")
                notificationManager.notify(1, notification.build())
            }
        }

        getTimeFlow().onEach {  timeDiff ->
            _elapsedTime.update { it + timeDiff }
        }.launchIn(scope)
    }

    private fun getTimeFlow(): Flow<Long> = flow {
        var startMillis = System.currentTimeMillis()
        while(isRunning) {
            val currentMillis = System.currentTimeMillis()
            val timeDiff = if (currentMillis > startMillis) {
                currentMillis - startMillis
            } else 0L

            emit(timeDiff)
            startMillis = System.currentTimeMillis()
            delay(999.milliseconds)
        }
    }

    enum class Actions {
        START, STOP
    }
}
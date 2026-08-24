package com.kemalcetin.aialarm.di

import android.content.Context
import com.kemalcetin.aialarm.BuildConfig
import com.kemalcetin.aialarm.core.alarm.AlarmController
import com.kemalcetin.aialarm.core.alarm.AlarmNotificationManager
import com.kemalcetin.aialarm.core.alarm.AlarmScheduler
import com.kemalcetin.aialarm.core.alarm.AndroidAlarmScheduler
import com.kemalcetin.aialarm.core.alarm.DebugAlarmScheduler
import com.kemalcetin.aialarm.core.alarm.NextAlarmCalculator
import com.kemalcetin.aialarm.core.permission.ExactAlarmPermissionManager
import com.kemalcetin.aialarm.core.permission.FullScreenIntentPermissionManager
import com.kemalcetin.aialarm.core.permission.NotificationPermissionManager
import com.kemalcetin.aialarm.data.local.AiAlarmDatabase
import com.kemalcetin.aialarm.data.prefs.AppPreferences
import com.kemalcetin.aialarm.data.repository.AlarmRepositoryImpl
import com.kemalcetin.aialarm.domain.repository.AlarmRepository
import com.kemalcetin.aialarm.feature.assistant.AiWatchdogScheduler
import com.kemalcetin.aialarm.feature.assistant.data.AlarmEventRepository
import com.kemalcetin.aialarm.feature.assistant.data.AlarmEventRepositoryImpl
import com.kemalcetin.aialarm.feature.assistant.engine.AiAlarmEngine
import com.kemalcetin.aialarm.feature.assistant.network.AiInsightsProvider
import com.kemalcetin.aialarm.feature.assistant.network.DeepSeekInsightsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Lightweight application-level dependency container. We can migrate to Hilt
 * later if the application grows; manual injection is sufficient for v0.1.
 */
class AppContainer(private val context: Context) {

    private val applicationContext = context.applicationContext

    val database: AiAlarmDatabase by lazy { AiAlarmDatabase.create(applicationContext) }

    val alarmRepository: AlarmRepository by lazy { AlarmRepositoryImpl(database.alarmDao()) }

    val nextAlarmCalculator: NextAlarmCalculator by lazy { NextAlarmCalculator() }

    val exactAlarmPermissionManager: ExactAlarmPermissionManager by lazy {
        ExactAlarmPermissionManager(applicationContext)
    }

    val notificationPermissionManager: NotificationPermissionManager by lazy {
        NotificationPermissionManager(applicationContext)
    }

    val fullScreenIntentPermissionManager: FullScreenIntentPermissionManager by lazy {
        FullScreenIntentPermissionManager(applicationContext)
    }

    val alarmScheduler: AlarmScheduler by lazy {
        AndroidAlarmScheduler(applicationContext, nextAlarmCalculator, exactAlarmPermissionManager)
    }

    val debugAlarmScheduler: DebugAlarmScheduler by lazy {
        DebugAlarmScheduler(applicationContext)
    }

    val notificationManager: AlarmNotificationManager by lazy {
        AlarmNotificationManager(applicationContext)
    }

    val appPreferences: AppPreferences by lazy { AppPreferences(applicationContext) }

    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    val alarmEventRepository: AlarmEventRepository by lazy {
        AlarmEventRepositoryImpl(database.alarmEventDao())
    }

    val aiInsightsProvider: AiInsightsProvider? by lazy {
        BuildConfig.DEEPSEEK_API_KEY.takeIf { it.isNotBlank() }?.let {
            DeepSeekInsightsProvider(it)
        }
    }

    val aiAlarmEngine: AiAlarmEngine by lazy {
        AiAlarmEngine(
            eventRepository = alarmEventRepository,
            alarmRepository = alarmRepository,
            scheduler = alarmScheduler,
            preferences = appPreferences,
            insightsProvider = aiInsightsProvider,
            scope = applicationScope
        )
    }

    val aiWatchdogScheduler: AiWatchdogScheduler by lazy {
        AiWatchdogScheduler(applicationContext)
    }

    val alarmController: AlarmController by lazy {
        AlarmController(
            context = applicationContext,
            repository = alarmRepository,
            scheduler = alarmScheduler,
            notificationManager = notificationManager,
            aiEngine = aiAlarmEngine,
            scope = applicationScope
        )
    }
}

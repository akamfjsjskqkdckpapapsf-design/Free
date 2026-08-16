package com.worm.livevoicefx

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

class AudioEffectService : Service() {

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRunning = false
    private var backgroundThread: Thread? = null

    // متغيرات التحكم (تُحدث من الواجهة)
    var bass = 1.5f
    var treble = 1.5f
    var compressor = 0.5f
    var reverb = 0.3f
    var mixTrackData: ShortArray? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
        setupAudio()
    }

    private fun setupAudio() {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 4

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // مفتاح SCO
            sampleRate, channelConfig, audioFormat, bufferSize
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startProcessing()
        return START_STICKY
    }

    private fun startProcessing() {
        if (isRunning) return
        isRunning = true

        // تفعيل خدعة البلوتوث SCO لدخول المكالمات
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.startBluetoothSco()
        am.setBluetoothScoOn(true)
        am.mode = AudioManager.MODE_IN_COMMUNICATION

        audioRecord?.startRecording()
        audioTrack?.play()

        backgroundThread = Thread {
            val buffer = ShortArray(1024)
            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val processed = AudioProcessor.process(
                        buffer,
                        bass,
                        treble,
                        compressor,
                        reverb,
                        mixTrackData
                    )
                    audioTrack?.write(processed, 0, processed.size)
                }
            }
        }
        backgroundThread?.start()
    }

    fun updateMixTrack(data: ShortArray?) {
        mixTrackData = data
    }

    private fun createNotification(): Notification {
        val channelId = "LIVE_VOICE_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Live Voice FX Engine",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("LIVE VOICE FX")
            .setContentText("معالج الصوت يعمل بقوة...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        audioRecord?.stop()
        audioTrack?.stop()
        audioRecord?.release()
        audioTrack?.release()
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setBluetoothScoOn(false)
        am.stopBluetoothSco()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

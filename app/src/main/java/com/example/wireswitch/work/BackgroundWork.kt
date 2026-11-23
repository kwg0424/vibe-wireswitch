package com.example.wireswitch.work

import android.content.BroadcastReceiver
import kotlin.concurrent.thread

/**
 * VPN 연결은 끝날 때까지 막히는 작업이라 메인 스레드에서 하면 안 된다.
 * 리시버는 [BroadcastReceiver.goAsync] 로 받은 [pending] 을 넘기면, 작업이 끝날 때까지 프로세스가 유지되도록 처리한다.
 */
object BackgroundWork {
    fun run(pending: BroadcastReceiver.PendingResult? = null, block: () -> Unit) {
        thread(name = "wireswitch-work") {
            try {
                block()
            }
            finally {
                pending?.finish()
            }
        }
    }
}

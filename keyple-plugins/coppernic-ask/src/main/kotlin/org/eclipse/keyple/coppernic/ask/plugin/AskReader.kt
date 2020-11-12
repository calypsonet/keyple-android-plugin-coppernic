/********************************************************************************
 * Copyright (c) 2020 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.keyple.coppernic.ask.plugin

import android.content.Context
import fr.coppernic.sdk.ask.Reader
import fr.coppernic.sdk.core.Defines
import fr.coppernic.sdk.power.PowerManager
import fr.coppernic.sdk.power.api.PowerListener
import fr.coppernic.sdk.power.api.peripheral.Peripheral
import fr.coppernic.sdk.power.impl.cone.ConePeripheral
import fr.coppernic.sdk.utils.core.CpcResult
import fr.coppernic.sdk.utils.io.InstanceListener
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.eclipse.keyple.coppernic.ask.plugin.utils.suspendCoroutineWithTimeout
import org.eclipse.keyple.core.service.exception.KeyplePluginInstantiationException
import org.eclipse.keyple.core.service.exception.KeypleReaderException
import org.eclipse.keyple.core.service.exception.KeypleReaderIOException
import timber.log.Timber

/**
 * Provides one instance of ASK reader to be shared between contact and contactless reader.
 */
internal object AskReader : PowerListener {

    private const val ASK_INIT_TIMEOUT: Long = 10000
    private const val POWER_UP_TIMEOUT: Long = 3000

    lateinit var uniqueInstance: WeakReference<Reader?>
    private val isInitied = AtomicBoolean(false)

    lateinit var contextWeakRef: WeakReference<Context?>
    var powerListenerContinuation: Continuation<Boolean>? = null
    var peripheral: ConePeripheral? = null

    // Avoid timeout issue when a call to checkSePresence has been sent
    // within a transmitApdu command.
    val isTransmitting = ReentrantLock()

    /**
     * Init the reader, is call when instanciating this plugin's factory
     */
    @Throws(Exception::class)
    suspend fun init(context: Context): Reader? {
        if (!isInitied.get()) {
            Timber.d("Start Init")
            contextWeakRef = WeakReference(context)

            val isPoweredOn: Boolean? =
                suspendCoroutineWithTimeout(POWER_UP_TIMEOUT) { continuation ->
                    powerListenerContinuation = continuation

                    PowerManager.get().registerListener(this@AskReader)
                    ConePeripheral.RFID_ASK_UCM108_GPIO.on(context)
                }
            Timber.d("Powered on : $isPoweredOn")

            if (isPoweredOn != null && isPoweredOn) {
                val reader: Reader? = suspendCoroutineWithTimeout(ASK_INIT_TIMEOUT) { continuation ->
                    Reader.getInstance(context, object : InstanceListener<Reader> {
                        override fun onCreated(reader: Reader) {
                            Timber.d("onCreated")
                            var result =
                                reader.cscOpen(Defines.SerialDefines.ASK_READER_PORT, 115200, false)

                            if (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok) {
                                Timber.e("Error while cscOpen: $result")
                                continuation.resumeWithException(KeypleReaderIOException("Error while cscOpen: $result"))
                                return
                            }

                            // Initializes reader
                            val sb = StringBuilder()
                            result = reader.cscVersionCsc(sb)

                            if (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok) {
                                Timber.w("Error while cscVersionCsc: $result")
                            }

                            uniqueInstance = WeakReference(reader)
                            isInitied.set(true)
                            Timber.d("End Init")
                            continuation.resume(reader)
                        }

                        override fun onDisposed(reader: Reader) {
                            Timber.d("onDisposed")
                            isInitied.set(false)
                            continuation.resume(null)
                        }
                    })
                }

                return reader
            } else {
                throw KeyplePluginInstantiationException("An error occured during Copernic AskReader power up.")
            }
        } else {
            return null
        }
    }

    /**
     * Get Reader instance
     */
    @Throws(KeypleReaderException::class)
    fun getInstance(): Reader {
        Timber.d("Get Instance")
        if (!isInitied.get()) {
            throw KeypleReaderIOException("Ask Reader not inited")
        }
        return uniqueInstance.get()!!
    }

    /**
     * Reset the instance
     * TODO: How to reuse the lib as init is only call once in factory?
     */
    fun clearInstance() {
        Timber.d("Clear Ask Reader instance")

        peripheral?.off(contextWeakRef.get())
        // Releases PowerManager
        PowerManager.get().unregisterAll()
        PowerManager.get().releaseResources()

        contextWeakRef.clear()
        contextWeakRef = WeakReference<Context?>(null)

        getInstance().let {
            uniqueInstance.get()?.destroy()
            uniqueInstance.clear()
            uniqueInstance = WeakReference<Reader?>(null)
            isInitied.set(false)
        }
    }

    /**
     * Lock to synchronize reader exchanges
     */
    fun acquireLock() {
        isTransmitting.lock()
    }

    /**
     * Release Lock
     */
    fun releaseLock() {
        isTransmitting.unlock()
    }

    override fun onPowerUp(res: CpcResult.RESULT?, peripheral: Peripheral?) {
        this.peripheral = peripheral as ConePeripheral
        powerListenerContinuation?.resume(true)
    }

    override fun onPowerDown(res: CpcResult.RESULT?, peripheral: Peripheral?) {
    }
}

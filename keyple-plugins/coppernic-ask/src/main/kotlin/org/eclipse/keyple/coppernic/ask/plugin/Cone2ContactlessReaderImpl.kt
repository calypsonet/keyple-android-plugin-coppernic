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

import fr.coppernic.sdk.ask.Defines
import fr.coppernic.sdk.ask.RfidTag
import fr.coppernic.sdk.ask.sCARD_SearchExt
import org.eclipse.keyple.core.plugin.reader.AbstractObservableLocalReader
import org.eclipse.keyple.core.plugin.reader.ObservableReaderStateService
import org.eclipse.keyple.core.plugin.reader.SmartInsertionReader
import org.eclipse.keyple.core.service.exception.KeypleReaderIOException
import org.eclipse.keyple.core.service.exception.KeypleReaderProtocolNotSupportedException
import org.eclipse.keyple.core.util.ByteArrayUtil
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.experimental.or

/**
 * Implementation of {@link org.eclipse.keyple.core.seproxy.SeReader} to communicate with NFC Tag
 * using ASK Coppernic library
 */
class Cone2ContactlessReaderImpl : AbstractObservableLocalReader(
    Cone2Plugin.PLUGIN_NAME,
    Cone2ContactlessReader.READER_NAME
), Cone2ContactlessReader,
    SmartInsertionReader {

    private val protocolsMap = mutableMapOf<String, Byte>()

    private val parameters: MutableMap<String, String> = ConcurrentHashMap()
    private val reader = ParagonReader.getInstance()

    // RFID tag information returned when card is detected
    private var rfidTag: RfidTag? = null

    // Indicates whether or not physical channel is opened.
    // Physical channel is irrelevant for ASK reader
    private val isPhysicalChannelOpened = AtomicBoolean(false)

    // This boolean indicates that a card has been discovered
    private val isCardDiscovered = AtomicBoolean(false)
    private val isWaitingForCard = AtomicBoolean(false)

    init {
        Timber.d("init")
        // We set parameters to default values
        parameters[Cone2ContactlessReader.CHECK_FOR_ABSENCE_TIMEOUT_KEY] =
            Cone2ContactlessReader.CHECK_FOR_ABSENCE_TIMEOUT_DEFAULT
        parameters[Cone2ContactlessReader.THREAD_WAIT_TIMEOUT_KEY] =
            Cone2ContactlessReader.THREAD_WAIT_TIMEOUT_DEFAULT

        protocolsMap[ParagonSupportedContactlessProtocols.ISO_14443.name] =
            PROTOCOL_DEACTIVATED
        protocolsMap[ParagonSupportedContactlessProtocols.ISO_14443_A.name] =
            PROTOCOL_DEACTIVATED
        protocolsMap[ParagonSupportedContactlessProtocols.ISO_14443_B.name] =
            PROTOCOL_DEACTIVATED
        protocolsMap[ParagonSupportedContactlessProtocols.INNOVATRON_B_PRIME.name] =
            PROTOCOL_DEACTIVATED
        protocolsMap[ParagonSupportedContactlessProtocols.TICKET_CTS_CTM.name] =
            PROTOCOL_DEACTIVATED
        protocolsMap[ParagonSupportedContactlessProtocols.MIFARE.name] =
            PROTOCOL_DEACTIVATED
        protocolsMap[ParagonSupportedContactlessProtocols.FELICA.name] =
            PROTOCOL_DEACTIVATED
        protocolsMap[ParagonSupportedContactlessProtocols.MV5000.name] =
            PROTOCOL_DEACTIVATED
    }

    override fun checkCardPresence(): Boolean {
        return isCardDiscovered.get()
    }

    override fun getATR(): ByteArray? {
        return rfidTag?.atr
    }

    override fun openPhysicalChannel() {
        Timber.d("openPhysicalChannel")
        isPhysicalChannelOpened.set(true)
    }

    override fun closePhysicalChannel() {
        Timber.d("closePhysicalChannel")
        isPhysicalChannelOpened.set(false)
    }

    override fun isPhysicalChannelOpen(): Boolean {
        Timber.d("isPhysicalChannelOpen: ${isPhysicalChannelOpened.get()}")
        return isPhysicalChannelOpened.get()
    }

    /**
     * For SmartInsertionReader
     */
    override fun waitForCardPresent(): Boolean {
        Timber.d("waitForCardPresent")
        var ret = false
        isWaitingForCard.set(true)
//        AskReader.acquireLock()
        // Entering a loop with successive hunts for card
        while (isWaitingForCard.get()) {
            val rfidTag = enterHuntPhase()
            if (rfidTag.communicationMode != RfidTag.CommunicationMode.Unknown) {
                this.rfidTag = rfidTag
                isCardDiscovered.set(true)
                isWaitingForCard.set(false)
                // This allows synchronisation with PLugin when powering off the reader
                ret = true
            }
        }
        // This allows synchronisation with Plugin when powering off the reader
//        AskReader.releaseLock()
        return ret
    }

    override fun stopWaitForCard() {
        Timber.d("stopWaitForCard")
        isWaitingForCard.set(false)
    }

    /***/

    private fun enterHuntPhase(): RfidTag {
        Timber.d("enterHuntPhase")
        try {
            ParagonReader.acquireLock()
            // 1 - Sets the enter hunt phase parameters to no select application
            reader.cscEnterHuntPhaseParameters(
                0x01.toByte(),
                0x01.toByte(),
                0x00.toByte(),
                0x00.toByte(),
                0x00.toByte(),
                0x00.toByte(),
                0x01.toByte(),
                0x00.toByte(),
                byteArrayOf(),
                0x00.toShort(),
                0x00.toByte()
            )
            val search = sCARD_SearchExt()

            search.OTH = 0
            search.CONT = 0
            search.INNO = protocolsMap[ParagonSupportedContactlessProtocols.INNOVATRON_B_PRIME.name]!!
            val isoA = protocolsMap[ParagonSupportedContactlessProtocols.ISO_14443.name]!! or protocolsMap[ParagonSupportedContactlessProtocols.ISO_14443_A.name]!!
            search.ISOA = isoA
            val isoB = protocolsMap[ParagonSupportedContactlessProtocols.ISO_14443.name]!! or protocolsMap[ParagonSupportedContactlessProtocols.ISO_14443_B.name]!!
            search.ISOB = isoB
            search.MIFARE = protocolsMap[ParagonSupportedContactlessProtocols.MIFARE.name]!!
            search.MONO = 0
            search.MV4k = 0
            search.MV5k = protocolsMap[ParagonSupportedContactlessProtocols.MV5000.name]!!
            search.TICK = protocolsMap[ParagonSupportedContactlessProtocols.TICKET_CTS_CTM.name]!!

            var mask = 0
            if (isCurrentProtocol(ParagonSupportedContactlessProtocols.INNOVATRON_B_PRIME.name)) {
                mask = mask or Defines.SEARCH_MASK_INNO
            }
            if (isCurrentProtocol(ParagonSupportedContactlessProtocols.ISO_14443.name) or
                isCurrentProtocol(ParagonSupportedContactlessProtocols.ISO_14443_A.name)) {
                mask = mask or Defines.SEARCH_MASK_ISOA
            }
            if (isCurrentProtocol(ParagonSupportedContactlessProtocols.ISO_14443.name) or
                isCurrentProtocol(ParagonSupportedContactlessProtocols.ISO_14443_B.name)) {
                mask = mask or Defines.SEARCH_MASK_ISOB
            }

            /*
             * OLD VERSION
             */
//            search.OTH = 0
//            search.CONT = 0
//            search.INNO = 1
//            search.ISOA = 1
//            search.ISOB = 1
//            search.MIFARE = 0
//            search.MONO = 0
//            search.MV4k = 0
//            search.MV5k = 0
//            search.TICK = 0
//
//            val mask: Int =
//                Defines.SEARCH_MASK_INNO or Defines.SEARCH_MASK_ISOA or Defines.SEARCH_MASK_ISOB
            /*
             * OLD VERSION
             */
            val com = ByteArray(1)
            val lpcbAtr = IntArray(1)
            val atr = ByteArray(64)
            val ret: Int = reader.cscSearchCardExt(
                search,
                mask,
                0x00.toByte(),
                HUNT_PHASE_TIMEOUT, // No timeout specified
                com,
                lpcbAtr,
                atr
            )

            return if (ret == Defines.RCSC_Timeout || com[0] == 0x6F.toByte() || com[0] == 0x00.toByte()) {
                Timber.w("Timeout type result")
                isCardDiscovered.set(false)
                RfidTag(0x6F.toByte(), ByteArray(0))
            } else {
                val correctSizedAtr = ByteArray(lpcbAtr[0])
                System.arraycopy(atr, 0, correctSizedAtr, 0, correctSizedAtr.size)
                Timber.d("RFID Tag built ${com[0]} $correctSizedAtr")
                RfidTag(com[0], correctSizedAtr)
            }
        } finally {
            ParagonReader.releaseLock()
        }
    }

    override fun transmitApdu(apduIn: ByteArray): ByteArray {
        Timber.d("transmitApdu")
        try {
            ParagonReader.acquireLock()
            val dataReceived = ByteArray(256)
            val dataReceivedLength = IntArray(1)

            Timber.d("KEYPLE-APDU-SAM - Data Length to be sent to tag : ${apduIn.size}")
            Timber.d("KEYPLE-APDU-SAM - Data In : ${ByteArrayUtil.toHex(apduIn)}")
            reader.cscISOCommand(apduIn, apduIn.size, dataReceived, dataReceivedLength)
            Timber.d("KEYPLE-APDU-SAM - Data Out : ${ByteArrayUtil.toHex(dataReceived)}")

            val length = dataReceivedLength[0]

            if (length < 2) {
                throw KeypleReaderIOException("Incorrect APDU Answer")
            } else {
                val apduAnswer = ByteArray(length - 1)
                System.arraycopy(dataReceived, 1, apduAnswer, 0, apduAnswer.size)
                return apduAnswer
            }
        } finally {
            ParagonReader.releaseLock()
        }
    }

    @Suppress("unused")
    fun clearInstance() {
        ParagonReader.clearInstance()
    }

    /**
     * Initialization of the state machine system for Observable reader
     * We have to set All 4 states of monitoring states
     */
    override fun initStateService(): ObservableReaderStateService {
        return ObservableReaderStateService.builder(this)
            .waitForCardInsertionWithSmartDetection()
            .waitForCardProcessingWithNativeDetection()
            .waitForCardRemovalWithPollingDetection()
            .build()
    }

    override fun isCurrentProtocol(readerProtocolName: String?): Boolean {
        println(">>> ")
        println(">>> Cone2ContactlessReaderImpl.isCurrentProtocol - readerProtocolName : $readerProtocolName")
        println(">>> Cone2ContactlessReaderImpl.isCurrentProtocol - result : ${readerProtocolName == null || protocolsMap.containsKey(readerProtocolName) && protocolsMap[readerProtocolName] == PROTOCOL_ACTIVATED}")
        return readerProtocolName == null || protocolsMap.containsKey(readerProtocolName) && protocolsMap[readerProtocolName] == PROTOCOL_ACTIVATED
    }

    override fun isContactless(): Boolean {
        return true
    }

    override fun activateReaderProtocol(readerProtocolName: String?) {
        if (!protocolsMap.containsKey(readerProtocolName)) {
            throw KeypleReaderProtocolNotSupportedException(readerProtocolName)
        }

        readerProtocolName?.let {
            protocolsMap[it] = PROTOCOL_ACTIVATED
        }
    }

    override fun deactivateReaderProtocol(readerProtocolName: String?) {
        if (!readerProtocolName.isNullOrEmpty() && protocolsMap.containsKey(readerProtocolName)) {
            protocolsMap[readerProtocolName] = PROTOCOL_DEACTIVATED
        }
    }

    companion object {
        const val HUNT_PHASE_TIMEOUT = 0xFF.toByte()
        const val PROTOCOL_ACTIVATED = 1.toByte()
        const val PROTOCOL_DEACTIVATED = 0.toByte()
    }

    override fun onStartDetection() {
        //Do nothing
    }

    override fun onStopDetection() {
        //Do nothing
    }
}

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
package org.eclipse.keyple.coppernic.ask.example.activity

import android.view.MenuItem
import androidx.core.view.GravityCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.android.synthetic.main.activity_main.drawerLayout
import kotlinx.android.synthetic.main.activity_main.toolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.keyple.calypso.command.po.exception.CalypsoPoCommandException
import org.eclipse.keyple.calypso.command.sam.exception.CalypsoSamCommandException
import org.eclipse.keyple.calypso.transaction.CalypsoPo
import org.eclipse.keyple.calypso.transaction.PoSelection
import org.eclipse.keyple.calypso.transaction.PoSelector
import org.eclipse.keyple.calypso.transaction.PoTransaction
import org.eclipse.keyple.calypso.transaction.exception.CalypsoPoTransactionException
import org.eclipse.keyple.coppernic.ask.example.R
import org.eclipse.keyple.coppernic.ask.example.util.CalypsoClassicInfo
import org.eclipse.keyple.coppernic.ask.plugin.Cone2ContactReader
import org.eclipse.keyple.coppernic.ask.plugin.Cone2ContactlessReader
import org.eclipse.keyple.coppernic.ask.plugin.Cone2PluginFactory
import org.eclipse.keyple.coppernic.ask.plugin.ParagonSupportedContactProtocols
import org.eclipse.keyple.coppernic.ask.plugin.ParagonSupportedContactlessProtocols
import org.eclipse.keyple.core.card.selection.CardResource
import org.eclipse.keyple.core.card.selection.CardSelectionsResult
import org.eclipse.keyple.core.card.selection.CardSelectionsService
import org.eclipse.keyple.core.card.selection.CardSelector
import org.eclipse.keyple.core.card.selection.MultiSelectionProcessing
import org.eclipse.keyple.core.plugin.AbstractLocalReader
import org.eclipse.keyple.core.service.PluginFactory
import org.eclipse.keyple.core.service.Reader
import org.eclipse.keyple.core.service.SmartCardService
import org.eclipse.keyple.core.service.event.AbstractDefaultSelectionsResponse
import org.eclipse.keyple.core.service.event.ObservableReader
import org.eclipse.keyple.core.service.event.ReaderEvent
import org.eclipse.keyple.core.service.event.ReaderObservationExceptionHandler
import org.eclipse.keyple.core.service.exception.KeypleReaderException
import org.eclipse.keyple.core.service.exception.KeypleReaderIOException
import org.eclipse.keyple.core.util.ByteArrayUtil
import timber.log.Timber

/**
 * Activity launched on app start up that display the only screen available on this example app.
 */
class MainActivity : AbstractExampleActivity() {

    private var poReader: Reader? = null
    private lateinit var samReader: Reader
    private lateinit var cardSelectionsService: CardSelectionsService

    private val areReadersInitialized = AtomicBoolean(false)

    private enum class TransactionType {
        DECREASE,
        INCREASE
    }

    override fun initContentView() {
        setContentView(R.layout.activity_main)
        initActionBar(toolbar, "Keyple demo", "Coppernic Plugin 2")
    }

    /**
     * Called when the activity (screen) is first displayed or resumed from background
     */
    override fun onResume() {
        super.onResume()
        addActionEvent("Enabling NFC Reader mode")
        addResultEvent("Please choose a use case")
        // Check whether readers are already initialized (return from background) or not (first launch)
        if (!areReadersInitialized.get()) {
            initReaders()
        } else {
            (poReader as ObservableReader).startCardDetection(ObservableReader.PollingMode.REPEATING)
        }
    }

    /**
     * Initializes the PO reader (Contact Reader) and SAM reader (Contactless Reader)
     */
    override fun initReaders() {
        Timber.d("initReaders")
        // Connexion to ASK lib take time, we've added a callback to this factory.

        GlobalScope.launch {
            val pluginFactory: PluginFactory?
            try {
                pluginFactory = withContext(Dispatchers.IO) {
                    Cone2PluginFactory.init(
                        applicationContext,
                        ReaderObservationExceptionHandler { pluginName, readerName, e ->
                            Timber.e("An unexpected reader error occurred: $pluginName:$readerName : $e")
                        }
                    )
                }
            } catch (e: KeypleReaderIOException) {
                withContext(Dispatchers.Main) {
                    showAlertDialog(e, finish = true, cancelable = false)
                }
                return@launch
            }

            // Get the instance of the SmartCardService (Singleton pattern)
            val smartCardService = SmartCardService.getInstance()

            // Register the Coppernic with SmartCardService, get the corresponding generic Plugin in return
            val paragonPlugin = smartCardService.registerPlugin(pluginFactory)

            // Get and configure the PO reader
            poReader = paragonPlugin.getReader(Cone2ContactlessReader.READER_NAME)

            // Set the current activity as Observer of the PO reader
            (poReader as ObservableReader).addObserver(this@MainActivity)

            // Activate protocols for the PO reader
            (poReader as ObservableReader).activateProtocol(
                ParagonSupportedContactlessProtocols.ISO_14443.name,
                ParagonSupportedContactlessProtocols.ISO_14443.name
            )

            // Get and configure the SAM reader
            samReader = paragonPlugin.readers[Cone2ContactReader.SAM_READER_1_NAME]!!

            // Activate protocols for the SAM reader
            (samReader as AbstractLocalReader).activateProtocol(
                ParagonSupportedContactProtocols.INNOVATRON_HIGH_SPEED_PROTOCOL.name,
                ParagonSupportedContactProtocols.INNOVATRON_HIGH_SPEED_PROTOCOL.name
            )

            areReadersInitialized.set(true)

            // Start the NFC detection
            (poReader as ObservableReader).startCardDetection(ObservableReader.PollingMode.REPEATING)
        }

//        // Configuration of AndroidNfc Reader
//        poReader = askPlugin.getReader(AndroidNfcReader.READER_NAME) as AndroidNfcReader
//        poReader.setParameter("FLAG_READER_RESET_STATE", "0")
//        poReader.setParameter("FLAG_READER_PRESENCE_CHECK_DELAY", "100")
//        poReader.setParameter("FLAG_READER_NO_PLATFORM_SOUNDS", "0")
//        poReader.setParameter("FLAG_READER_SKIP_NDEF_CHECK", "0")
//        (poReader as ObservableReader).addObserver(this)
//        (poReader as ObservableReader).addSeProtocolSetting(SeCommonProtocols.PROTOCOL_ISO14443_4, AndroidNfcProtocolSettings.getSetting(SeCommonProtocols.PROTOCOL_ISO14443_4))
//        /* Uncomment to active protocol listening for Mifare ultralight or Mifare Classic (AndroidNfcReader) */
//        // (poReader as ObservableReader).addSeProtocolSetting(SeCommonProtocols.PROTOCOL_MIFARE_UL, AndroidNfcProtocolSettings.getAllSettings()[SeCommonProtocols.PROTOCOL_MIFARE_UL])
//        // (poReader as ObservableReader).addSeProtocolSetting(SeCommonProtocols.PROTOCOL_MIFARE_CLASSIC, AndroidNfcProtocolSettings.getAllSettings()[SeCommonProtocols.PROTOCOL_MIFARE_CLASSIC])
//
//        // Configuration for Sam Reader. Sam access provided by Famoco lib-secommunication
//        // FIXME: Initialisation Delay to handler?
//        samReader = samPlugin.getReader(AndroidFamocoReader.READER_NAME)
    }

    /**
     * Called when the activity (screen) is destroyed or put in background
     */
    override fun onPause() {
        addActionEvent("Stopping PO Read Write Mode")
        if (areReadersInitialized.get()) {
            // Stop NFC card detection
            (poReader as ObservableReader).stopCardDetection()
        }
        super.onPause()
    }

    /**
     * Called when the activity (screen) is destroyed
     */
    override fun onDestroy() {
        poReader?.let {
            // stop propagating the reader events
            (poReader as ObservableReader).removeObserver(this)
        }

        // Unregister the Coppernic plugin
        SmartCardService.getInstance().plugins.forEach {
            SmartCardService.getInstance().unregisterPlugin(it.key)
        }

        super.onDestroy()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        when (item.itemId) {
            R.id.usecase1 -> {
                clearEvents()
                addHeaderEvent("Running Calypso Read transaction (without SAM)")
                configureCalypsoTransaction(::runPoReadTransactionWithoutSam)
            }
            R.id.usecase2 -> {
                clearEvents()
                addHeaderEvent("Running Calypso Read transaction (with SAM)")
                configureCalypsoTransaction(::runPoReadTransactionWithSam)
            }
            R.id.usecase3 -> {
                clearEvents()
                addHeaderEvent("Running Calypso Read/Write transaction")
                configureCalypsoTransaction(::runPoReadWriteIncreaseTransaction)
            }
            R.id.usecase4 -> {
                clearEvents()
                addHeaderEvent("Running Calypso Read/Write transaction")
                configureCalypsoTransaction(::runPoReadWriteDecreaseTransaction)
            }
        }
        return true
    }

    override fun update(event: ReaderEvent?) {
        addResultEvent("New ReaderEvent received : ${event?.eventType?.name}")
        useCase?.onEventUpdate(event)
    }

    private fun configureCalypsoTransaction(responseProcessor: (selectionsResponse: AbstractDefaultSelectionsResponse) -> Unit) {
        addActionEvent("Prepare Calypso PO Selection with AID: ${CalypsoClassicInfo.AID}")
        try {
            /* Prepare a Calypso PO selection */
            cardSelectionsService = CardSelectionsService(MultiSelectionProcessing.FIRST_MATCH)

            /* Calypso selection: configures a PoSelector with all the desired attributes to make the selection and read additional information afterwards */
            val poSelectionRequest = PoSelection(
                PoSelector.builder()
                    .cardProtocol(ParagonSupportedContactlessProtocols.ISO_14443.name)
                    .aidSelector(CardSelector.AidSelector.builder().aidToSelect(CalypsoClassicInfo.AID).build())
                    .invalidatedPo(PoSelector.InvalidatedPo.REJECT).build()
            )

            /* Prepare the reading order and keep the associated parser for later use once the
             selection has been made. */
            poSelectionRequest.prepareReadRecordFile(
                CalypsoClassicInfo.SFI_EnvironmentAndHolder,
                CalypsoClassicInfo.RECORD_NUMBER_1.toInt()
            )

            /*
             * Add the selection case to the current selection (we could have added other cases
             * here)
             */
            cardSelectionsService.prepareSelection(poSelectionRequest)

            /*
            * Provide the SeReader with the selection operation to be processed when a PO is
            * inserted.
            */
            (poReader as ObservableReader).setDefaultSelectionRequest(
                cardSelectionsService.defaultSelectionsRequest,
                ObservableReader.NotificationMode.MATCHED_ONLY
            )

            useCase = object : UseCase {
                override fun onEventUpdate(event: ReaderEvent?) {
                    CoroutineScope(Dispatchers.Main).launch {
                        when (event?.eventType) {
                            ReaderEvent.EventType.CARD_MATCHED -> {
                                addResultEvent("PO detected with AID: ${CalypsoClassicInfo.AID}")
                                responseProcessor(event.defaultSelectionsResponse)
                                (poReader as ObservableReader).finalizeCardProcessing()
                            }

                            ReaderEvent.EventType.CARD_INSERTED -> {
                                addResultEvent("PO detected but AID didn't match with ${CalypsoClassicInfo.AID}")
                                (poReader as ObservableReader).finalizeCardProcessing()
                            }

                            ReaderEvent.EventType.CARD_REMOVED -> {
                                addResultEvent("PO removed")
                            }
                            else -> {
                                // Do nothing
                            }
                        }
                    }
                    // eventRecyclerView.smoothScrollToPosition(events.size - 1)
                }
            }

            // notify reader that se detection has been launched
            // poReader.startSeDetection(ObservableReader.PollingMode.REPEATING)
            addActionEvent("Waiting for PO presentation")
        } catch (e: KeypleReaderException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        } catch (e: CalypsoPoTransactionException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        } catch (e: CalypsoPoCommandException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        } catch (e: CalypsoSamCommandException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        }
    }

    private fun runPoReadTransactionWithSam(selectionsResponse: AbstractDefaultSelectionsResponse) {
        runPoReadTransaction(selectionsResponse, true)
    }

    private fun runPoReadTransactionWithoutSam(selectionsResponse: AbstractDefaultSelectionsResponse) {
        runPoReadTransaction(selectionsResponse, false)
    }

    private fun runPoReadTransaction(
        selectionsResponse: AbstractDefaultSelectionsResponse,
        withSam: Boolean
    ) {
        try {
            /*
             * print tag info in View
             */
            addActionEvent("Process selection")
            val selectionsResult = cardSelectionsService.processDefaultSelectionsResponse(selectionsResponse)

            if (selectionsResult.hasActiveSelection()) {
                addResultEvent("Selection successful")
                val calypsoPo = selectionsResult.activeSmartCard as CalypsoPo

                /*
                 * Retrieve the data read from the parser updated during the selection process
                 */
                val efEnvironmentHolder =
                    calypsoPo.getFileBySfi(CalypsoClassicInfo.SFI_EnvironmentAndHolder)
                addActionEvent("Read environment and holder data")

                addResultEvent(
                    "Environment and Holder file: ${ByteArrayUtil.toHex(
                        efEnvironmentHolder.data.content
                    )}"
                )

                addHeaderEvent("2nd PO exchange: read the event log file")

                val poTransaction = if (withSam) {
                    // samReader.setParameter(AndroidFamocoReader.FLAG_READER_RESET_STATE, "")
                    addActionEvent("Init Sam and open channel")
                    val samResource = checkSamAndOpenChannel(samReader)
                    PoTransaction(CardResource(poReader, calypsoPo), getSecuritySettings(samResource))
                } else {
                    PoTransaction(CardResource(poReader, calypsoPo))
                }

                /*
                 * Prepare the reading order and keep the associated parser for later use once the
                 * transaction has been processed.
                 */
                poTransaction.prepareReadRecordFile(
                    CalypsoClassicInfo.SFI_EventLog,
                    CalypsoClassicInfo.RECORD_NUMBER_1.toInt()
                )

                poTransaction.prepareReadRecordFile(
                    CalypsoClassicInfo.SFI_Counter1,
                    CalypsoClassicInfo.RECORD_NUMBER_1.toInt()
                )

                /*
                 * Actual PO communication: send the prepared read order, then close the channel
                 * with the PO
                 */
                addActionEvent("Process PO Command for counter and event logs reading")

                if (withSam) {
                    addActionEvent("Process PO Opening session for transactions")
                    poTransaction.processOpening(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_LOAD)
                    addResultEvent("Opening session: SUCCESS")
                    val counter = readCounter(selectionsResult)
                    val eventLog = ByteArrayUtil.toHex(readEventLog(selectionsResult))

                    addActionEvent("Process PO Closing session")
                    poTransaction.processClosing()
                    addResultEvent("Closing session: SUCCESS")

                    // In secured reading, value read elements can only be trusted if the session is closed without error.
                    addResultEvent("Counter value: $counter")
                    addResultEvent("EventLog file: $eventLog")
                } else {
                    poTransaction.processPoCommands()
                    // poTransaction.processClosing() //?
                    addResultEvent("Counter value: ${readCounter(selectionsResult)}")
                    addResultEvent(
                        "EventLog file: ${ByteArrayUtil.toHex(
                            readEventLog(
                                selectionsResult
                            )
                        )}"
                    )
                }

                addResultEvent("End of the Calypso PO processing.")
                addResultEvent("You can remove the card now")
            } else {
                addResultEvent("The selection of the PO has failed. Should not have occurred due to the MATCHED_ONLY selection mode.")
            }
        } catch (e: KeypleReaderException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        } catch (e: CalypsoPoTransactionException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        } catch (e: CalypsoPoCommandException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        } catch (e: CalypsoSamCommandException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        }
    }

    private fun readCounter(selectionsResult: CardSelectionsResult): Int? {
        val calypsoPo = selectionsResult.activeSmartCard as CalypsoPo
        val efCounter1 = calypsoPo.getFileBySfi(CalypsoClassicInfo.SFI_Counter1)
        return efCounter1.data.getContentAsCounterValue(CalypsoClassicInfo.RECORD_NUMBER_1.toInt())
    }

    private fun readEventLog(selectionsResult: CardSelectionsResult): ByteArray? {
        val calypsoPo = selectionsResult.activeSmartCard as CalypsoPo
        val efCounter1 = calypsoPo.getFileBySfi(CalypsoClassicInfo.SFI_EventLog)
        return efCounter1.data.content
    }

    private fun runPoReadWriteIncreaseTransaction(selectionsResponse: AbstractDefaultSelectionsResponse) {
        runPoReadWriteTransaction(selectionsResponse, TransactionType.INCREASE)
    }

    private fun runPoReadWriteDecreaseTransaction(selectionsResponse: AbstractDefaultSelectionsResponse) {
        runPoReadWriteTransaction(selectionsResponse, TransactionType.DECREASE)
    }

    private fun runPoReadWriteTransaction(
        selectionsResponse: AbstractDefaultSelectionsResponse,
        transactionType: TransactionType
    ) {
        try {
            // addResultEvent("Tag Id : ${poReader.printTagId()}")

            // FIXME: Trick to reopen all channel
            // samReader.setParameter(AndroidFamocoReader.FLAG_READER_RESET_STATE, "")
            addActionEvent("Init Sam and open channel")
            val samResource = checkSamAndOpenChannel(samReader)

            addActionEvent("1st PO exchange: aid selection")
            val selectionsResult = cardSelectionsService.processDefaultSelectionsResponse(selectionsResponse)

            if (selectionsResult.hasActiveSelection()) {
                addResultEvent("Calypso PO selection: SUCCESS")
                val calypsoPo = selectionsResult.activeSmartCard as CalypsoPo
                addResultEvent("AID: ${ByteArrayUtil.fromHex(CalypsoClassicInfo.AID)}")

                val poTransaction =
                    PoTransaction(CardResource(poReader, calypsoPo), getSecuritySettings(samResource))

                when (transactionType) {
                    TransactionType.INCREASE -> {
                        /*
                        * Open Session for the debit key
                        */
                        addActionEvent("Process PO Opening session for transactions")
                        poTransaction.processOpening(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_LOAD)
                        addResultEvent("Opening session: SUCCESS")

                        poTransaction.prepareReadRecordFile(
                            CalypsoClassicInfo.SFI_Counter1,
                            CalypsoClassicInfo.RECORD_NUMBER_1.toInt()
                        )
                        poTransaction.processPoCommands()

                        poTransaction.prepareIncreaseCounter(
                            CalypsoClassicInfo.SFI_Counter1,
                            CalypsoClassicInfo.RECORD_NUMBER_1.toInt(),
                            10
                        )
                        addActionEvent("Process PO increase counter by 10")
                        poTransaction.processClosing()
                        addResultEvent("Increase by 10: SUCCESS")
                    }
                    TransactionType.DECREASE -> {
                        /*
                        * Open Session for the debit key
                        */
                        addActionEvent("Process PO Opening session for transactions")
                        poTransaction.processOpening(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_DEBIT)
                        addResultEvent("Opening session: SUCCESS")

                        poTransaction.prepareReadRecordFile(
                            CalypsoClassicInfo.SFI_Counter1,
                            CalypsoClassicInfo.RECORD_NUMBER_1.toInt()
                        )
                        poTransaction.processPoCommands()

                        /*
                         * A ratification command will be sent (CONTACTLESS_MODE).
                         */
                        poTransaction.prepareDecreaseCounter(
                            CalypsoClassicInfo.SFI_Counter1,
                            CalypsoClassicInfo.RECORD_NUMBER_1.toInt(),
                            1
                        )
                        addActionEvent("Process PO decreasing counter and close transaction")
                        poTransaction.processClosing()
                        addResultEvent("Decrease by 1: SUCCESS")
                    }
                }

                addResultEvent("End of the Calypso PO processing.")
                addResultEvent("You can remove the card now")
            } else {
                addResultEvent("The selection of the PO has failed. Should not have occurred due to the MATCHED_ONLY selection mode.")
            }
        } catch (e: KeypleReaderException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        } catch (e: KeypleReaderException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        } catch (e: CalypsoPoTransactionException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        } catch (e: CalypsoPoCommandException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        } catch (e: CalypsoSamCommandException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        }
    }
}

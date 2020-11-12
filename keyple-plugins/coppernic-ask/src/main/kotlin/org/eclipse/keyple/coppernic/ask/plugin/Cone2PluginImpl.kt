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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import org.eclipse.keyple.core.plugin.AbstractPlugin
import org.eclipse.keyple.core.service.Reader
import timber.log.Timber

/**
 * Handle native Readers mapped for Keyple
 */
internal class Cone2PluginImpl : AbstractPlugin(Cone2Plugin.PLUGIN_NAME), Cone2Plugin {

    override fun initNativeReaders(): ConcurrentMap<String, Reader> {
        Timber.w("Init native readers")
        val seReaders = ConcurrentHashMap<String, Reader>()
        val sam1 = Cone2ContactReaderImpl(Cone2ContactReaderImpl.ContactInterface.ONE)
        seReaders[sam1.name] = sam1
        val sam2 = Cone2ContactReaderImpl(Cone2ContactReaderImpl.ContactInterface.TWO)
        seReaders[sam2.name] = sam2
        val nfc = Cone2ContactlessReaderImpl()
        seReaders[nfc.name] = nfc
        return seReaders
    }

    override fun unregister() {
        readers.forEach {
            if(it.value is Cone2ContactlessReaderImpl){
                (it.value as Cone2ContactlessReaderImpl).clearInstance()
            }
        }
        super.unregister()
    }
}

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
import org.eclipse.keyple.core.service.Plugin
import org.eclipse.keyple.core.service.PluginFactory
import org.eclipse.keyple.core.service.exception.KeypleReaderIOException

object Cone2PluginFactory : PluginFactory {

    @Throws(KeypleReaderIOException::class)
    suspend fun init(context: Context): Cone2PluginFactory {
        println(">>> ")
        println(">>> Cone2PluginFactory.init - ParagonReader.init...")
        val plugin = ParagonReader.init(context)
        return plugin?.let {
            this
        }
        ?: throw KeypleReaderIOException("Could not init Ask Library")
    }

    override fun getPluginName(): String {
        return Cone2Plugin.PLUGIN_NAME
    }

    override fun getPlugin(): Plugin {
        return Cone2PluginImpl()
    }
}

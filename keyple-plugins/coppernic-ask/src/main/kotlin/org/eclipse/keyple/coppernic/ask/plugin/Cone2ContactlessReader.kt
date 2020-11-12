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

interface Cone2ContactlessReader {
    companion object {
        const val READER_NAME = "AndroidCoppernicAskContactlessReader"

        /**
         * This parameter sets the timeout used in the waitForCardAbsent method
         */
        const val CHECK_FOR_ABSENCE_TIMEOUT_KEY = "CHECK_FOR_ABSENCE_TIMEOUT_KEY"

        /**
         * Default value for CHECK_FOR_ABSENCE_TIMEOUT parameter
         */
        const val CHECK_FOR_ABSENCE_TIMEOUT_DEFAULT = "500"

        /**
         * This parameter sets the thread wait timeout
         */
        const val THREAD_WAIT_TIMEOUT_KEY = "THREAD_WAIT_TIMEOUT_KEY"

        /**
         * Default value for THREAD_WAIT_TIMEOUT parameter
         */
        const val THREAD_WAIT_TIMEOUT_DEFAULT = "2000"
    }
}

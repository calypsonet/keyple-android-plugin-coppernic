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

interface Cone2ContactReader {
    companion object {
        const val READER_NAME = "Cone2ContactReader"

        const val SAM_READER_1_NAME = "${READER_NAME}_1"
        const val SAM_READER_2_NAME = "${READER_NAME}_2"
    }
}

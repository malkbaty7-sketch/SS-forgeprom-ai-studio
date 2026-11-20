package com.forgeprom.studio.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GatewayInvoicesTest {
    @Test fun onlyOpensHostedStripeLinks() {
        assertEquals("invoice.stripe.com", stripeInvoiceLink("https://invoice.stripe.com/i/test")?.host)
        assertEquals("pay.stripe.com", stripeInvoiceLink("https://pay.stripe.com/invoice/test/pdf")?.host)
        listOf(null, "not a link", "http://invoice.stripe.com/i/test", "https://invoice.stripe.com.example.org/",
            "https://user:pass@invoice.stripe.com/", "https://invoice.stripe.com:444/").forEach {
            assertNull(stripeInvoiceLink(it))
        }
    }

    @Test fun formatsMinorUnitsWithoutLosingDecimalPrecision() {
        val amount = requireNotNull(formatInvoiceAmount(1250, "cad"))
        val digits = amount.mapNotNull { it.digitToIntOrNull() }
        assertEquals(listOf(1, 2, 5, 0), digits)
        assertEquals("CAD", amount.substringAfter('\u2066').substringBefore('\u2069'))
        assertNull(formatInvoiceAmount(null, "cad"))
        assertNull(formatInvoiceAmount(-1, "cad"))
        assertNull(formatInvoiceAmount(100, "invalid"))
    }
}

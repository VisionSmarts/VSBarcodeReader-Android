//
//  BarQrCodeUtil.kt
//
//  Copyright 2011-2026 Vision Smarts SRL. All rights reserved.
//

package com.visionsmarts

import android.net.Uri
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException

import com.visionsmarts.VSBarcodeReader.MODE_KANJI
import kotlin.math.min

object BarQrCodeUtil {

    /**
     * Decodes a byte array as UTF-8 unicode string. Remove trailing NUL char if any.
     *
     * @param qrData the byte array
     *
     * @return the decoded unicode string
     */
    fun qrToString(qrData: ByteArray): String? {
        // some QR codes include a trailing C-style null char that throws off
        // the UTF decoding
        // remove it here
        val qrBuffer: ByteBuffer = if (qrData.size > 0 && (qrData[qrData.size - 1] == 0.toByte())) {
            ByteBuffer.wrap(qrData, 0, qrData.size - 1)
        } else {
            ByteBuffer.wrap(qrData)
        }
        return decodeData(qrBuffer, Charset.forName("UTF-8"))
    }

    /**
     * Conditionally trims leading zero of EAN13 barcode to make UPC-A.
     *
     * @param barcode_ the EAN13 string
     *
     * @return the UPCA string if EAN13 started with zero, EAN13 string otherwise
     */
    fun trimUpcBarcode(barcode_: String): String {
        var barcode = barcode_
        if (barcode.length == 13 && barcode[0] == '0') {
            // UPC-A barcode: replace "UPC" by 12-digits barcode, without
            // the
            // initial 0
            barcode = barcode.substring(1)
        }
        return barcode
    }

    /**
     * Builds HTML <a> node from http, https, mailto, tel, sms, or market URL.
     *
     * @param qrOrSmth the URL string
     *
     * @return the string with the HTML <a> node
     */
    fun linkify(qrOrSmth: String): String {
        return qrOrSmth.replace(
            "(http://|https://|mailto:|tel:|sms:|market:)(\\S+)".toRegex(),
            "<a href=\"$1$2\">$1$2</a>"
        )
    }

    /**
     * Checks whether URL string is Play Store URL.
     *
     * @param url the URL string
     *
     * @return the boolean answer
     */
    fun isPlayStoreUrl(url: String?): Boolean {
        val uri = Uri.parse(url)
        val protocol = uri.scheme
        val host = uri.host
        return "market" == protocol || "market.android.com" == host || "play.google.com" == host
    }


    /**
     * Formats a QR or DataMatrix code in a standard unicode string depending on its mode and ECI markers.
     *
     * @param data binary data read from QR code or DataMatrix
     * @param mode the QR code mode(s)
     * @param defaultCharset the default charset
     * @return the decoded unicode string for the given code
     */
    fun format(data: ByteArray, mode: Int, defaultCharset: Charset): String {
        var charset : Charset? = defaultCharset
        if (mode and MODE_KANJI == MODE_KANJI) {
            charset = Charset.forName("Shift_JIS")
        }
        var position = 0

        // drop "]Q3" .. "]Q6" header if any (FNC1 and ECI)
        if (data.size > 3 && data[0] == ']'.code.toByte() && data[1] == 'Q'.code.toByte()) {
            if (data[2] == '3'.code.toByte() || data[2] == '4'.code.toByte()) {
                position += 3
            }
            if ((data[2] == '5'.code.toByte() || data[2] == '6'.code.toByte()) && data.size > 5) {
                position += 5 // drop the 2-digit FNC1 Application ID too
            }
        }
        val result = StringBuilder(data.size)

        // interpret "\NNNNNN" ECI markers if any
        var nextMarker: ECIMarker
        do {
            nextMarker =
                findFirstECIMarker(
                    data,
                    position
                )
            // decode segment until next marker or end
            val segment = ByteBuffer.wrap(
                data,
                position,
                min(nextMarker.position, data.size) - position
            )
            val next = decodeData(
                segment,
                charset
            )
            if (next != null) {
                result.append(next)
            }
            if (nextMarker.position != data.size) {
                position = nextMarker.position + 7
                charset =
                    getCharsetForECI(
                        nextMarker.eci
                    )
                if (charset == null) { // ECI not found
                    charset = defaultCharset
                }
                // Log.d(TAG, String.format("eci: %d charset: %s position: %d", nextMarker.eci, charset.name(), position));
            }
        } while (nextMarker.position != data.size && position < data.size)
        return result.toString()
    }

    /**
     * Finds ASCII ECI marker ("\NNNNNN") in data array.
     *
     * @param data byte array in which to search ASCII ECI marker
     * @param fromPosition the position in data from which to search
     * @return ECIMarker of the found ECI marker or with data length as position if not found
     */
    private fun findFirstECIMarker(data: ByteArray, fromPosition: Int): ECIMarker {
        // Log.d(TAG, "findFirstECIMarker(...)");
        val eciMarker = ECIMarker()
        eciMarker.position = data.size // not found
        var position = fromPosition
        while (position < data.size - 6) {
            if (data[position] == '\\'.code.toByte()
                && data[position + 1].toInt().toChar().isDigit()
                && data[position + 2].toInt().toChar().isDigit()
                && data[position + 3].toInt().toChar().isDigit()
                && data[position + 4].toInt().toChar().isDigit()
                && data[position + 5].toInt().toChar().isDigit()
                && data[position + 6].toInt().toChar().isDigit()
            ) {
                eciMarker.position = position
                eciMarker.eci =
                    (data[position + 1] - 48) * 100000 + (data[position + 2] - 48) * 10000 + ((data[position + 3] - 48)
                            * 1000) + (data[position + 4] - 48) * 100 + (data[position + 5] - 48) * 10 + (data[position + 6] - 48)
                break
            }
            position++
        }
        return eciMarker
    }

    /**
     * Gets the charset corresponding to an ECI.
     *
     * @param eci ECI value
     * @return charset corresponding to eci or null if not supported
     */
    private fun getCharsetForECI(eci: Int): Charset? {
        // Log.d(TAG, "getCharsetForECI(" + eci + ")");
        return try {
            // The following list of ECIs is incomplete and may be partially incorrect.
            // It is provided as a starting point for your convenience.
            // Please check the ECI definitions that are relevant for your application, if any.
            when (eci) {
                0, 1, 2, 3 -> Charset.forName("ISO-8859-1")
                4 -> Charset.forName("ISO-8859-2")
                5 -> Charset.forName("ISO-8859-3")
                6 -> Charset.forName("ISO-8859-4")
                7 -> Charset.forName("ISO-8859-5")
                8 -> Charset.forName("ISO-8859-6")
                9 -> Charset.forName("ISO-8859-7")
                10 -> Charset.forName("ISO-8859-8")
                11 -> Charset.forName("ISO-8859-9")
                12 -> null
                13 -> Charset.forName("ISO-8859-11")
                14 -> null
                15 -> Charset.forName("ISO-8859-13")
                16 -> null
                17 -> Charset.forName("ISO-8859-15")
                18 -> null
                19 -> null
                20 -> Charset.forName("Shift_JIS")
                21 -> Charset.forName("windows-1250")
                22 -> Charset.forName("windows-1251")
                23 -> Charset.forName("windows-1252")
                24 -> Charset.forName("windows-1256")
                25 -> Charset.forName("UTF-16BE")
                26 -> Charset.forName("UTF-8")
                27 -> Charset.forName("EUC-JP")
                28 -> Charset.forName("Big5")
                29 -> Charset.forName("x-EUC-CN")
                30 -> Charset.forName("EUC-KR")
                899 -> Charset.forName("UTF-8")
                else -> null
            }
        } catch (e: IllegalCharsetNameException) {
            null
        } catch (e: UnsupportedCharsetException) {
            null
        }
    }

    /**
     * Decodes data to a unicode string, using given charset, or try guessing.
     *
     * @param data data
     * @param charset charset
     * @return converted unicode string or null if data cannot be converted
     */
    fun decodeData(data: ByteBuffer, charset: Charset?): String? {
        // Log.d(TAG, "decodeData(...)");
        try {
            return charset!!.newDecoder().decode(data).toString()
        } catch (e: CharacterCodingException) {
            data.rewind()
        }
        try {
            return Charset.forName("Shift_JIS").newDecoder().decode(data)
                .toString()
        } catch (e: CharacterCodingException) {
            data.rewind()
        }
        try {
            return Charset.forName("UTF-8").newDecoder().decode(data).toString()
        } catch (e: CharacterCodingException) {
            data.rewind()
        }
        try {
            return Charset.forName("ISO-8859-1").newDecoder().decode(data)
                .toString()
        } catch (e: CharacterCodingException) {
            data.rewind()
        }
        try {
            return Charset.forName("US-ASCII").newDecoder().decode(data).toString()
        } catch (e: CharacterCodingException) {
        }
        return null
    } // private static final String TAG = VSReaderQR.class.getSimpleName();


    /** Class for return values of [.findFirstECIMarker].  */
    private class ECIMarker {
        /** Position of ECI marker in data array.  */
        var position = 0

        /** Value of ECI marker.  */
        var eci = 0
    }

}

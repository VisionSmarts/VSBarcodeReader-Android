//
//  VSBarcodeReader.kt
//
//  Copyright 2011-2026 Vision Smarts SRL. All rights reserved.
//

package com.visionsmarts

import java.util.Locale

/**
 * Java wrapper to the VisionSmarts Barcode Reader native library.
 */
object VSBarcodeReader {

    /** Invalid barcode type.  */
    const val BARCODE_TYPE_INVALID = -1
    /** EAN-13 or UPC-A barcode type.  */
    const val BARCODE_TYPE_EAN_13_UPC_A = 0x0001
    /** EAN-8 barcode type.  */
    const val BARCODE_TYPE_EAN_8 = 0x0002
    /** UPC-E barcode type.  */
    const val BARCODE_TYPE_UPC_E = 0x0004
    /** ITF barcode type.  */
    const val BARCODE_TYPE_ITF = 0x0008
    /** Code39 barcode type.  */
    const val BARCODE_TYPE_CODE39 = 0x0010
    /** Code128 barcode type.  */
    const val BARCODE_TYPE_CODE128 = 0x0020
    /** Codabar barcode type.  */
    const val BARCODE_TYPE_CODABAR = 0x0040
    /** Code93 barcode type.  */
    const val BARCODE_TYPE_CODE93 = 0x0080
    /** Standard 2 of 5 barcode type.  */
    const val BARCODE_TYPE_STD2OF5 = 0x0100
    /** Telepen barcode type.  */
    const val BARCODE_TYPE_TELEPEN = 0x0200
    /** Databar Omnidirectional and Omnidirectional Stacked barcode type. */
    const val	BARCODE_TYPE_DATABAR_OMNIDIRECTIONAL      = 0x0400
    /** Databar Limited barcode type. */
    const val 	BARCODE_TYPE_DATABAR_LIMITED              = 0x0800
    /** Databar Expanded and Expanded Stacked barcode type. */
    const val 	BARCODE_TYPE_DATABAR_EXPANDED             = 0x1000
    /** EAN+2 barcode type.  */
    const val BARCODE_TYPE_EAN_PLUS2 = 0x2000
    /** EAN+5 barcode type.  */
    const val BARCODE_TYPE_EAN_PLUS5 = 0x4000

    /** QR code type.  */
    const val BARCODE_TYPE_QR = 0x8000
    /** DataMatrix code type.  */
    const val BARCODE_TYPE_DATAMATRIX = 0x10000

    /** QR code numeric mode.  */
    const val MODE_NUMERIC = 1
    /** QR code alphanumeric mode.  */
    const val MODE_ALPHANUMERIC = 2
    /** QR code byte mode.  */
    const val MODE_BYTE = 4
    /** QR code kanji mode.  */
    const val MODE_KANJI = 8

    /**
     *  Loads the VisionSmarts Barcode Reader native library.
     *  To switch from Evaluation to Production library, remove "-EVAL"
     *  */
    init {
        System.loadLibrary("VSBarcodeReader")
    }

    /** Initializes the VisionSmarts Barcode Reader native library.  */
    external fun VSinit(): Int

    /** Resets the barcode reader before a new scan.  */
    external fun reset(): Int

    /**
     * Replaces non-printing ASCII 127 characters with their hexadecimal representation
     * @param input an ASCII-127 string
     * @return the string with characters below code 32 replaced with their hexadecimal representation
     */
    private fun replaceNonPrinting(input: String): String {
        val buffer = StringBuilder(input.length)
        for (i in input.indices) {
            if (input[i].code < 32) {
                buffer.append("{0x")
                    .append(Integer.toHexString(input[i].code).uppercase(Locale.ENGLISH))
                    .append("}")
            } else {
                buffer.append(input[i])
            }
        }
        return buffer.toString()
    }

    /**
     * Attempts to parse Numeric Telepen format
     * @param input an ASCII-127 string
     * @return the string with numerical representation of the Telepen barcode, or null
     */
    private fun convertTelepenToNumeric(input: String): String? {
        val buffer = StringBuilder(2 * input.length)
        for (i in input.indices) {
            if (input[i].code > 126) {
                return null
            } else if (input[i].code >= 27) {
                buffer.append(String.format("%02d", input[i].code - 27))
            } else if (i == input.length - 1 && input[i].code >= 17) {
                buffer.append(String.format("%1dX", input[i].code - 17))
            } else {
                return null
            }
        }
        return buffer.toString()
    }

    /**
     * Formats a barcode in a standard display string depending on its type.
     * @param barcode a valid barcode
     * @param type the barcode type
     * @return the standard display string for the given barcode
     */
    fun format(barcode: String, type: Int): String {
        //Log.d(TAG, "Formatting barcode " + barcode);

        when (type) {
            BARCODE_TYPE_EAN_13_UPC_A -> return if (barcode.substring(0, 1) == "0") {
                // UPC-A - drop leading 0
                String.format("%s-%s-%s-%s", barcode.substring(1, 2), barcode.substring(2, 7), barcode.substring(7, 12), barcode.substring(12, 13))
            } else {
                // EAN-13
                String.format("%s-%s-%s", barcode.substring(0, 1), barcode.substring(1, 7), barcode.substring(7, 13))
            }
            BARCODE_TYPE_UPC_E ->
                // UPC-E
                return String.format("%s-%s-%s", barcode.substring(0, 1), barcode.substring(1, 7), barcode.substring(7, 8))
            BARCODE_TYPE_EAN_8 ->
                // EAN-8
                return String.format("%s-%s", barcode.substring(0, 4), barcode.substring(4, 8))
            BARCODE_TYPE_TELEPEN -> {
                // TELEPEN
                val numeric =
                    convertTelepenToNumeric(
                        barcode
                    )
                return if (null == numeric) {
                    replaceNonPrinting(
                        barcode
                    )
                } else {
                    String.format("%s (%s)",
                        replaceNonPrinting(
                            barcode
                        ), numeric)
                }
            }
        }
        // All other types: no formatting
        return barcode
    }

    /**
     * Formats a barcode in a standard display string depending on its type,
     * and prefix with "type: "
     * @param barcode a valid barcode
     * @param type the barcode type
     * @return the standard display string for the given barcode
     */
    fun formatForDemo(barcode: String, type: Int): String {
        val formattedBarcode =
            format(
                barcode,
                type
            )

        val typeName = when (type) {
            BARCODE_TYPE_EAN_13_UPC_A -> "EAN"
            BARCODE_TYPE_UPC_E -> "UPCE"
            BARCODE_TYPE_EAN_8 -> "EAN8"
            BARCODE_TYPE_TELEPEN -> "TELEPEN"
            BARCODE_TYPE_ITF -> "ITF"
            BARCODE_TYPE_CODE39 -> "C39"
            BARCODE_TYPE_CODE128 -> "C128"
            BARCODE_TYPE_CODABAR -> "CODABAR"
            BARCODE_TYPE_CODE93 -> "C93"
            BARCODE_TYPE_STD2OF5 -> "STD2OF5"
            BARCODE_TYPE_EAN_PLUS2 -> "EAN+2"
            BARCODE_TYPE_EAN_PLUS5 -> "EAN+5"
            BARCODE_TYPE_DATABAR_OMNIDIRECTIONAL -> "GS1_Omnidir"
            BARCODE_TYPE_DATABAR_LIMITED -> "GS1_Limited"
            BARCODE_TYPE_DATABAR_EXPANDED -> "GS1_Expanded"
            BARCODE_TYPE_QR -> "QR"
            BARCODE_TYPE_DATAMATRIX -> "DataMatrix"
            else -> "UNK"
        }
        return "$typeName: $formattedBarcode"
    }


    /**
     * Class used to return additional values from decodeNextImage() and decodeNextImageOmnidirectional().
     */
    class Point {
        var x: Int = 0
        var y: Int = 0
    }

    /**
     * Class used to pass and return additional values from decodeNextImageMultiple().
     */
    class VSPoint(var x: Double = 0.0, var y: Double = 0.0)

    /**
     * Class used to return additional values from decodeNextImageOmnidirectional().
     */
    class DecoderValues {

        /** Offset of the left side of the decoded barcode.  */
        var left: Int = 0

        /** Offset of the right side of the decoded barcode.  */
        var right: Int = 0

        /** Location of scan line in image, if detected by decodeNextImageOmnidirectional(), (-1,-1) otherwise.  */
        var lineStart: Point =
            Point()
        var lineEnd: Point =
            Point()

        /** Number of days left in evaluation license (-1 if production version).  */
        var evaluationDays: Int = 0

        /** Barcode type.  */
        var type: Int = 0

        /** QR code mode. */
        var mode: Int = 0
    }

    /**
     * Class used to return barcode data from decodeNextImageMultiple().
     */
    class VSBarcodeData {

        /** For all symbologies: the barcode symbology (BARCODE_TYPE_...) */
        var symbology: Int = 0

        /**
         * For QR and DataMatrix: null (parse the data field instead)
         * For all 1D symbologies: the barcode text
         */
        var text: String? = null

        /** For QR and DataMatrix: the data content  */
        var data: ByteArray? = null

        /** For QR and DataMatrix: the raw data content  */
        var bits: ByteArray? = null

        /** For QR and DataMatrix: Structured Append values */
        var sequenceLength: Int = 0
        var sequencePosition: Int = 0
        var sequenceChecksum: Int = 0

        /** For QR: bit mask of the QR modes present in the data
         * (see constants MODE_NUMERIC, MODE_ALPHANUMERIC, MODE_BYTE, MODE_KANJI)
         */
        var mode: Int = 0

        /** The four corners of the barcode envelope or QR/DM in image coordinates. */
        var corner1: VSPoint = VSPoint()
        var corner2: VSPoint = VSPoint()
        var corner3: VSPoint = VSPoint()
        var corner4: VSPoint = VSPoint()
    }

    /**
     * Sets the acceptance threshold for blurry barcodes on devices with autofocus.
     * Has no effect when 'hasAutofocus' parameter of decodeNextImage method is false
     *
     * @param threshold
     * floating point value between 0.0 and 1.0
     * 0.0 is the most lenient (faster decoding, higher probability of error)
     * 1.0 is the most demanding (slower decoding, lower probability of error)
     * values above 0.7 may severely impact decoding speed
     * a value of 1.0 ensures that blurry barcodes will not be decoded on autofocus devices,
     * only sharp ones will
     */
    external fun setBlurryAcceptanceThresholdWithAF(threshold: Double): Int

    /**
     * Decodes the next image, i.e. tries to read a barcode, in the barcode reader native library.
     * Returns zero or one barcode.
     * Future versions of this method will return all the barcodes detected in the image.
     *
     * @param frameData
     *            array of bytes containing image data, in which to decode a barcode
     * @param rowSize
     *            length of one row of pixels in frameData array (image width)
     * @param height
     *            number of rows of pixels in frameData array (image height)
     * @param hasAutoFocus
     *            true if the device supports autofocus, false otherwise
     * @param symbologies
     *            binary OR of barcode types to look for
     * @param inRectFrom
     *            top left of crop rectangle where to look for barcodes
     *            in normalized (x=0..1, y=0..1) image coordinates
     *            regardless of UI orientation (0,0) is upper left of image, (1,1) is bottom right.
     *            use (0,0) to read entire image (and (1,1) for bottom right)
     * @param inRectTo
     *            bottom right of crop rectangle where to look for barcodes
     *            in normalized (x=0..1, y=0..1) image coordinates
     *            use (1,1) to read entire image (and (0,0) for top left)
     *
     * @return an array of one VSBarcodeData containing the decoded barcode if found, an empty array otherwise
     */
    external fun decodeNextImageMultiple(frameData: ByteArray, rowSize: Int, height: Int,
                                         hasAutoFocus: Int, symbologies: Int, inRectFrom: VSPoint,
                                         inRectTo:VSPoint) : Array<VSBarcodeData>

    /**
     * @return the number of days left for evaluation. zero if expired. -1 if production library.
     */
    external fun getValidity() : Int

}

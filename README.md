# VSBarcodeReader for Android - Evaluation only

⚠️ **PROPRIETARY SOFTWARE & EVALUATION ONLY**

This SDK is **not** open source. Use of the libraries and sample code provided in this 
repository is strictly limited to **internal evaluation and testing only**. 

Any other production use or distribution is explicitly prohibited under the terms of the `LICENSE` file.

To use this SDK in a production environment, pilot program, or for any purpose other 
than evaluation, you must obtain a commercial license.

## Overview

This project contains sample code that demonstrates one way of using the Android APIs to capture a video stream and read barcodes using the VSBarcodeReader library.

The iOS version of the VSBarcodeReader library, with the same APIs and capabilities, is available here: [https://github.com/visionsmarts/VSBarcodeReader-iOS](https://github.com/visionsmarts/VSBarcodeReader-iOS).

Compiled versions of Android and the iOS "showcase" apps (the code in this repository and its iOS sibling) are available on the Google Play Store and App Store for immediate testing:

<table border="0">
<tr>
<td><a href="https://play.google.com/store/apps/details?id=com.visionsmarts.barcode"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" width="172"></a></td>
<td><a href="https://apps.apple.com/app/vision-smarts/id1512421547"><img src="https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg" width="150"></a></td>
</tr>
</table>

## Commercial Licensing

Interested in using the **VSBarcodeReader SDK** for a pilot, production app, or commercial project? We offer a simple, no-hassle yearly subscription license per app, with unlimited downloads.

**To request a commercial license or a formal quote:**
* 📧 **Email:** [sales@visionsmarts.com](mailto:sales@visionsmarts.com)
* 🌐 **Website:** [visionsmarts.com](https://www.visionsmarts.com)

## Contents of the Binary Library

The binary libraries can be fetched from the Vision Smarts server (no account needed) with the following command:

`/bin/bash fetch_binary_libraries.sh`

For extra security, verify the SHA256 checksum on [www.visionsmarts.com](https://www.visionsmarts.com)

The VSBarcodeReader library is provided in the four (one per ABI) libVSBarcodeReader.so dynamic libraries located in src/main/jniLibs. 

The VSBarcodeReader library implements the VSBarcodeReader object that is declared in the VSBarcodeReader.kt Kotlin wrapper.

The methods of the VSBarcodeReader object are used to decode barcodes from image buffers obtained from a CameraX capture session.
The binary library's functionality is purely computational. It does not include any UI or image capture elements.

### Important notes about the evaluation library

The functionality of the evaluation library is time-limited to about one minute after app launch. After that, the decoding method will return no barcode data, even if barcodes are present in the image. 

The evaluation library transmits usage data, including device and application identifiers and IP addresses, to Vision Smarts. No personally identifiable information is collected or transmitted.

The commercially-licensed production library does not have any execution time limit and does not transmit any data.

## Sample Code

The sample code represents our current best attempt to capture and process video on many Android devices.  However, because of the wide variety of devices and Android versions, we do not make any warranty that it works or will continue to work on all devices.

You are free to reuse the sample code in your project, if you purchased a license for VSBarcodeReader.

Please refer to the 'VSBarcodeReader.kt' file for detailed documentation.

libVSBarcodeReader.so adds native C (JNI) functions to the VSBarcodeReader object that is defined in the VSBarcodeReader.kt file.

The JNI library is loaded in 'VSBarcodeReader.kt'

It needs to be initialized once by calling the `VSinit` method.  In the sample code, that is done in the `onCreate` method of the main activity.

The main function is defined and documented in 'VSBarcodeReader.kt':

    external fun decodeNextImageMultiple(frameData: ByteArray, rowSize: Int, height: Int,
                                         hasAutoFocus: Int, symbologies: Int, inRectFrom: VSPoint,
                                         inRectTo:VSPoint) : Array<VSBarcodeData>

It accepts one image and returns the barcode(s) if found, as well as some other values (the position and type of the barcode) in the 'VSBarcodeData' object.

In the sample code, that function is called in the `decode` method of the 'ScannerActivity' class.  Once a barcode is found, the receiver of the 'msg_barcode_found' message could stop the video feed and present the next view, or keep scanning until all the required barcodes are found.

### About symbologies

- Only enable that symbologies that the application demands, since looking for more symbologies will slightly slow down decoding and cause misreads of symbologies with weak error detection such as Codabar or ITF.
- Code 39, Code 93, Code 128, Codabar, ITF, Std2of5, Telepen, GS1 Databar can only be read when all the bars are visible (no blurry barcodes).
- Derived formats with check character like ITF-14 and Code 39 mod 43 are not explicitly supported,
  but any checksum can easily be computed in the `decode` method of ScannerActivity before stopping the scanner.


(c) VISION SMARTS SRL 2009-2026



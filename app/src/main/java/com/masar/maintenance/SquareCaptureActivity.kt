package com.masar.maintenance

import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/** نشاط مسح QR بنافذة مربعة (بدل المستطيلة الافتراضية). */
class SquareCaptureActivity : ComponentActivity() {

    private lateinit var capture: CaptureManager
    private lateinit var barcodeView: DecoratedBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        barcodeView = DecoratedBarcodeView(this)
        // نافذة مسح مربعة
        barcodeView.barcodeView.framingRectSize = Size(720, 720)
        barcodeView.setStatusText("وجّه الكاميرا نحو رمز السيارة")
        setContentView(barcodeView)

        capture = CaptureManager(this, barcodeView)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.decode()
    }

    override fun onResume() { super.onResume(); capture.onResume() }
    override fun onPause() { super.onPause(); capture.onPause() }
    override fun onDestroy() { super.onDestroy(); capture.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); capture.onSaveInstanceState(outState) }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}

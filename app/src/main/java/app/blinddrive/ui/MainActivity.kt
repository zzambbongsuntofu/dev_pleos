package app.blinddrive.ui

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val viewModel: BlindDriveViewModel by viewModels()

    /** Vehicle speed is a permission the driver must allow on screen. Without it the speed stays unknown. */
    private val speedAccess = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.onSpeedAccessGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null &&
            checkSelfPermission(SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) {
            speedAccess.launch(SPEED_PERMISSION)
        }
        setContent {
            BlindDriveScreen(viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onHostVisible()
    }

    override fun onStop() {
        viewModel.onHostHidden()
        super.onStop()
    }

    private companion object {
        const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
    }
}

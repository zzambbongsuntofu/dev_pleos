package app.blinddrive.vehicle

import app.blinddrive.model.CabinSnapshot

interface CabinStatusSource {
    fun start(onUpdate: (CabinSnapshot) -> Unit)
    fun stop()
}

package app.blinddrive.vehicle;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.Brake;
import ai.pleos.playground.vehicle.api.Gear;
import ai.pleos.playground.vehicle.api.Light;
import ai.pleos.playground.vehicle.api.Safety;
import ai.pleos.playground.vehicle.api.Seat;
import ai.pleos.playground.vehicle.api.SteeringWheel;
import ai.pleos.playground.vehicle.api.TurnSignal;
import ai.pleos.playground.vehicle.api.Window;
import ai.pleos.playground.vehicle.api.Wiper;

/** Java callers can use the public getters. Kotlin treats the same properties as private. */
public final class VehicleDomains {
    private VehicleDomains() {
    }

    public static TurnSignal turnSignal(Vehicle vehicle) {
        return vehicle.getTurnSignal();
    }

    public static Brake brake(Vehicle vehicle) {
        return vehicle.getBrake();
    }

    public static SteeringWheel steeringWheel(Vehicle vehicle) {
        return vehicle.getSteeringWheel();
    }

    public static Gear gear(Vehicle vehicle) {
        return vehicle.getGear();
    }

    public static Light light(Vehicle vehicle) {
        return vehicle.getLight();
    }

    public static Wiper wiper(Vehicle vehicle) {
        return vehicle.getWiper();
    }

    public static Window window(Vehicle vehicle) {
        return vehicle.getWindow();
    }

    public static Seat seat(Vehicle vehicle) {
        return vehicle.getSeat();
    }

    public static Safety safety(Vehicle vehicle) {
        return vehicle.getSafety();
    }
}

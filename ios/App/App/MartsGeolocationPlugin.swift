import Foundation
import Capacitor
import CoreLocation

@objc(MartsGeolocationPlugin)
public class MartsGeolocationPlugin: CAPPlugin, CLLocationManagerDelegate {

    private let locationManager = CLLocationManager()
    private var intervalMs: Double = 300000
    private var isTracking = false
    private var pendingCall: CAPPluginCall?

    private let storeKey = "marts_gps_points"
    private let runningKey = "marts_gps_running"
    private let maxPoints = 1000

    override public func load() {
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = 0
        locationManager.pausesLocationUpdatesAutomatically = false
        locationManager.allowsBackgroundLocationUpdates = true
        if #available(iOS 9.0, *) {
            locationManager.activityType = .fitness
        }
        isTracking = UserDefaults.standard.bool(forKey: runningKey)
    }

    @objc func startTracking(_ call: CAPPluginCall) {
        intervalMs = max(15000, call.getDouble("intervalMs") ?? 300000)
        let status = CLLocationManager.authorizationStatus()
        switch status {
        case .authorizedAlways:
            beginTracking(call)
        case .notDetermined, .authorizedWhenInUse:
            pendingCall = call
            locationManager.requestAlwaysAuthorization()
        default:
            call.resolve(["started": false, "error": "permission_denied"])
        }
    }

    private func beginTracking(_ call: CAPPluginCall) {
        UserDefaults.standard.set(true, forKey: runningKey)
        isTracking = true
        locationManager.startUpdatingLocation()
        call.resolve(["started": true])
        pendingCall = nil
    }

    @objc func stopTracking(_ call: CAPPluginCall) {
        locationManager.stopUpdatingLocation()
        UserDefaults.standard.set(false, forKey: runningKey)
        isTracking = false
        call.resolve(["stopped": true])
    }

    @objc func isTracking(_ call: CAPPluginCall) {
        call.resolve(["tracking": isTracking])
    }

    @objc func getPendingPoints(_ call: CAPPluginCall) {
        call.resolve(["points": readPoints()])
    }

    @objc func clearPendingPoints(_ call: CAPPluginCall) {
        UserDefaults.standard.set([], forKey: storeKey)
        call.resolve(["cleared": true])
    }

    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        handleAuthorization(manager.authorizationStatus)
    }

    @available(iOS, deprecated: 14.0)
    public func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        handleAuthorization(status)
    }

    private func handleAuthorization(_ status: CLAuthorizationStatus) {
        guard let call = pendingCall else { return }
        if status == .authorizedAlways {
            beginTracking(call)
        } else if status == .authorizedWhenInUse {
            call.resolve(["started": false, "error": "while_in_use"])
            pendingCall = nil
        } else {
            call.resolve(["started": false, "error": "permission_denied"])
            pendingCall = nil
        }
    }

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        savePoint(loc)
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    }

    private func savePoint(_ loc: CLLocation) {
        var points = readPoints()
        let formatter = ISO8601DateFormatter()
        let id = "m\(Int(Date().timeIntervalSince1970 * 1000))_\(points.count)"
        let point: [String: Any] = [
            "id": id,
            "lat": loc.coordinate.latitude,
            "lng": loc.coordinate.longitude,
            "accuracy": loc.horizontalAccuracy,
            "at": formatter.string(from: Date())
        ]
        points.append(point)
        if points.count > maxPoints {
            points.removeFirst(points.count - maxPoints)
        }
        UserDefaults.standard.set(points, forKey: storeKey)
    }

    private func readPoints() -> [[String: Any]] {
        return (UserDefaults.standard.array(forKey: storeKey) as? [[String: Any]]) ?? []
    }
}

package com.hemanthraj.fluttercompass;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;

public final class FlutterCompassPlugin implements FlutterPlugin, StreamHandler {
    // A static variable which will retain the value across Isolates.
    private static Double currentAzimuth;
    
    private double newAzimuth;
    private double filter;
    private int lastAccuracy;
    private SensorEventListener sensorEventListener;

    private final SensorManager sensorManager;
    private final Sensor sensor;
    private final float[] orientation;
    private final float[] rMat;

    public FlutterCompassPlugin() {
        sensorManager = null;
        sensor = null;
        orientation = null;
        rMat = null;
    }

    public FlutterCompassPlugin(Context context, int sensorType, int fallbackSensorType) {
        filter = 0.1F;
        lastAccuracy = 1; // SENSOR_STATUS_ACCURACY_LOW

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        orientation = new float[3];
        rMat = new float[9];
        Sensor defaultSensor = this.sensorManager.getDefaultSensor(sensorType);
        if (defaultSensor != null) {
            sensor = defaultSensor;
        } else {
            sensor = this.sensorManager.getDefaultSensor(fallbackSensorType);
        }
    }

    // New Plugin APIs

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        EventChannel channel = new EventChannel(binding.getBinaryMessenger(), "hemanthraj/flutter_compass");
        channel.setStreamHandler(new FlutterCompassPlugin(binding.getApplicationContext(), Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR));
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    }

    public void onListen(Object arguments, EventSink events) {
        // Added check for the sensor, if null then the device does not have the TYPE_ROTATION_VECTOR or TYPE_GEOMAGNETIC_ROTATION_VECTOR sensor
        if(sensor != null) {
            sensorEventListener = createSensorEventListener(events);
            sensorManager.registerListener(sensorEventListener, this.sensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            // Send null to Flutter side
            events.success(null);
//                events.error("Sensor Null", "No sensor was found", "The device does not have any sensor");
        }
    }

    public void onCancel(Object arguments) {
        this.sensorManager.unregisterListener(this.sensorEventListener);
    }

    private SensorEventListener createSensorEventListener(final EventSink events) {
        return new SensorEventListener() {
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                lastAccuracy = accuracy;
            }

            public void onSensorChanged(SensorEvent event) {
                SensorManager.getRotationMatrixFromVector(rMat, event.values);
                newAzimuth = (Math.toDegrees((double) SensorManager.getOrientation(rMat, orientation)[0]) + (double) 360) % (double) 360;
                if (currentAzimuth == null || Math.abs(currentAzimuth - newAzimuth) >= filter) {
                    currentAzimuth = newAzimuth;

                    // Compute the orientation relative to the Z axis (out the back of the device).
                    float[] zAxisRmat = new float[9];
                    SensorManager.remapCoordinateSystem(
                        rMat,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        zAxisRmat);
                    float[] dv = new float[3]; 
                    SensorManager.getOrientation(zAxisRmat, dv);
                    double azimuthForCameraMode = (Math.toDegrees((double) dv[0]) + (double) 360) % (double) 360;

                    double[] v = new double[3];
                    v[0] = newAzimuth;
                    v[1] = azimuthForCameraMode;
                    // Include reasonable compass accuracy numbers. These are not representative
                    // of the real error.
                    if (lastAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
                        v[2] = 15;
                    } else if (lastAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
                        v[2] = 30;
                    } else if (lastAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                        v[2] = 45;
                    } else {
                        v[2] = -1; // unknown
                    }
                    events.success(v);
                }
            }
        };
    }
}

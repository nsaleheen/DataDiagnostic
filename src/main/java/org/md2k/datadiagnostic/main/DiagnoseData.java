package org.md2k.datadiagnostic.main;

import org.md2k.datadiagnostic.configurations.Config;
import org.md2k.datadiagnostic.data.CSVExporter;
import org.md2k.datadiagnostic.data.DataLoader;
import org.md2k.datadiagnostic.marker.SensorAttachmentMarker;
import org.md2k.datadiagnostic.marker.SensorDataQualityMarker;
import org.md2k.datadiagnostic.marker.power.BatteryDataMarker;
import org.md2k.datadiagnostic.marker.wireless.DataLossMarker;
import org.md2k.datadiagnostic.marker.wireless.SensorUnavailableMarker;
import org.md2k.datadiagnostic.struct.DataPoints;
import org.md2k.datadiagnostic.windowing.FixedSizeWindowing;

import java.util.ArrayList;
import java.util.List;

public class DiagnoseData {

    /**
     * This class is responsible to execute all the data-diagnostic algorithms
     */

    DataLoader dataLoader;
    List<DataPoints> sensorData;
    List<DataPoints> phoneBatteryData;
    List<DataPoints> sensorBatteryData;
    long startTime;
    long endTime;
    FixedSizeWindowing fixedSizeWindowing;
    double samplingRate;

    /**
     * Load data and create blank windows for a period of 24 hours
     */
    public DiagnoseData(String uid, long startTime1, long endTime1) {
        sensorData = new ArrayList<DataPoints>();
        phoneBatteryData = new ArrayList<DataPoints>();
        sensorBatteryData = new ArrayList<DataPoints>();
        fixedSizeWindowing = new FixedSizeWindowing();
        dataLoader = new DataLoader();
        this.startTime = startTime1;
        this.endTime = endTime1;

        String dir = Config.get("PATH");
        String currDir = dir+uid+"\\";
        System.out.print(currDir + "...");

        if (Config.get("STREAM_NAME").equals("rip")) {
            System.out.println("Loading/pre-processing RIP data...");
            phoneBatteryData = dataLoader.loadCSV(Config.getFileName(currDir, "PHONE_BATTERY"), startTime, endTime);
            sensorBatteryData = dataLoader.loadCSV(Config.getFileName(currDir, "AUTOSENSE_BATTERY"), startTime, endTime);

            samplingRate = Double.parseDouble(Config.get("RESPIRATION_SAMPLING_RATE"));
            sensorData = dataLoader.loadCSV(Config.getFileName(currDir, "RIP_DATA"), startTime, endTime);
            startTime = sensorData.get(0).startTimestamp;
            endTime = sensorData.get(sensorData.size()-1).startTimestamp;
            fixedSizeWindowing.blankWindows(sensorData, startTime, endTime, Long.parseLong(Config.get("WINDOW_SIZE")));
            System.out.println("Diagnosing RIP data.");
            diagnoseRIPData(uid +"rip_diagnostic.txt");
        } else if (Config.get("STREAM_NAME").equals("ecg")) {
            System.out.println("Loading/pre-processing ECG data");
            phoneBatteryData = dataLoader.loadCSV(Config.getFileName(currDir, "PHONE_BATTERY"), startTime, endTime);
            sensorBatteryData = dataLoader.loadCSV(Config.getFileName(currDir, "AUTOSENSE_BATTERY"), startTime, endTime);

            samplingRate = Double.parseDouble(Config.get("ECG_SAMPLING_RATE"));
            sensorData = dataLoader.loadCSV(Config.getFileName(currDir, "ECG_DATA"), startTime, endTime);
            startTime = sensorData.get(0).startTimestamp;
            endTime = sensorData.get(sensorData.size()-1).startTimestamp;
            fixedSizeWindowing.blankWindows(sensorData, startTime, endTime, Long.parseLong(Config.get("WINDOW_SIZE")));
            System.out.println("Diagnosing ECG data.");
            diagnoseECGData();
        } else if (Config.get("STREAM_NAME").equals("motionsense")) {
            System.out.println("Loading/pre-processing MotionSense data");
            phoneBatteryData = dataLoader.loadCSV(Config.getFileName(currDir, "PHONE_BATTERY"), startTime, endTime);
            sensorBatteryData = dataLoader.loadCSV(Config.getFileName(currDir, "MOTIONSENSE_BATTERY"), startTime, endTime);

            samplingRate = Double.parseDouble(Config.get("MOTIONSENSE_SAMPLING_RATE"));
            sensorData = dataLoader.loadWristCSV(Config.getFileName(currDir, "MOTIONSENSE_ACCELEROMETER"), startTime, endTime);
            startTime = sensorData.get(0).startTimestamp;
            endTime = sensorData.get(sensorData.size()-1).startTimestamp;
            fixedSizeWindowing.blankWindows(sensorData, startTime, endTime, Long.parseLong(Config.get("WINDOW_SIZE")));
            System.out.println("Diagnosing MotionSense data.");
//            diagnoseMotionsenseData(currDir,  uid +"motionsense_left_diagnostic.txt"); // for left wrist
            diagnoseMotionsenseData(currDir,  uid + "motionsense_right_diagnostic.txt"); // for right wrist
        }
    }

    /**
     * Diagnose RIP data. All algorithms are executed in a sequence:<br>
     * 1 - Check phone battery <br>
     * 2 - Check sensor battery <br>
     * 3 - Check sensor unavailability<br>
     * 4 - Check sensor improper attachment<br>
     * 5 - Calculate packet loss<br>
     * 6 - Mark sensor data quality<br>
     */
    private void diagnoseRIPData(String outputFileName) {

        BatteryDataMarker batteryDataMarker = new BatteryDataMarker();
        SensorUnavailableMarker sensorUnavailable = new SensorUnavailableMarker(this.startTime, this.endTime);
        SensorAttachmentMarker sensorAttachmentMarker = new SensorAttachmentMarker(this.startTime, this.endTime);
        DataLossMarker dataLossMarker = new DataLossMarker();
        SensorDataQualityMarker sensorSignalQualityMarker = new SensorDataQualityMarker();

        batteryDataMarker.phoneBatteryMarker(phoneBatteryData, fixedSizeWindowing.blankWindows);
        batteryDataMarker.senseBatteryMarker(sensorBatteryData, batteryDataMarker.markedWindowsPhone);
        sensorUnavailable.autoSenseWirelessDC(batteryDataMarker.markedWindowsAutosense);
        sensorAttachmentMarker.improperOrNoAttachmentRIP(sensorUnavailable.markedWindows);
        dataLossMarker.packetLoss(sensorAttachmentMarker.markedWindows, Double.parseDouble(Config.get("WINDOW_SIZE")), samplingRate);
        sensorSignalQualityMarker.markWindowsQulaity(dataLossMarker.markedWindows);


        CSVExporter csvExporter = new CSVExporter();
        csvExporter.writeMarkedDataPointsToCSV(sensorSignalQualityMarker.markedWindows, Config.get("OUTPUT_PATH"), outputFileName);

    }

    /**
     * Diagnose ECG data. All algorithms are executed in a sequence:<br>
     * 1 - Check phone battery <br>
     * 2 - Check sensor battery <br>
     * 3 - Check sensor unavailability<br>
     * 4 - Check sensor improper attachment<br>
     * 5 - Calculate packet loss<br>
     * 6 - Mark sensor data quality<br>
     */
    private void diagnoseECGData() {
        BatteryDataMarker batteryDataMarker = new BatteryDataMarker();
        SensorUnavailableMarker sensorUnavailable = new SensorUnavailableMarker(this.startTime, this.endTime);
        SensorAttachmentMarker sensorAttachmentMarker = new SensorAttachmentMarker(this.startTime, this.endTime);
        DataLossMarker dataLossMarker = new DataLossMarker();
        SensorDataQualityMarker sensorSignalQualityMarker = new SensorDataQualityMarker();

        batteryDataMarker.phoneBatteryMarker(phoneBatteryData, fixedSizeWindowing.blankWindows);
        batteryDataMarker.senseBatteryMarker(sensorBatteryData, batteryDataMarker.markedWindowsPhone);
        sensorUnavailable.autoSenseWirelessDC(batteryDataMarker.markedWindowsAutosense);
        sensorAttachmentMarker.improperOrNoAttachmentECG(sensorUnavailable.markedWindows);
        dataLossMarker.packetLoss(sensorAttachmentMarker.markedWindows, Double.parseDouble(Config.get("WINDOW_SIZE")), samplingRate);
        sensorSignalQualityMarker.markWindowsQulaity(dataLossMarker.markedWindows);

        CSVExporter csvExporter = new CSVExporter();
        csvExporter.writeMarkedDataPointsToCSV(sensorSignalQualityMarker.markedWindows, Config.get("OUTPUT_PATH"), "ecg_diagnostic.txt");

    }

    /**
     * Diagnose MotionSense wrist band data. All algorithms are executed in a sequence:<br>
     * 1 - Check phone battery <br>
     * 2 - Check sensor battery <br>
     * 3 - Check sensor unavailability<br>
     * 4 - Calculate packet loss<br>
     * 5 - Mark sensor data quality<br>
     * @param currDir
     */
    private void diagnoseMotionsenseData(String currDir, String outputFileName) {
        BatteryDataMarker batteryDataMarker = new BatteryDataMarker();
        SensorUnavailableMarker sensorUnavailable = new SensorUnavailableMarker(this.startTime, this.endTime);
        DataLossMarker dataLossMarker = new DataLossMarker();
        SensorDataQualityMarker sensorSignalQualityMarker = new SensorDataQualityMarker();

        batteryDataMarker.phoneBatteryMarker(phoneBatteryData, fixedSizeWindowing.blankWindows);
        batteryDataMarker.motionSenseBatteryMarker(sensorBatteryData, batteryDataMarker.markedWindowsPhone);
        sensorUnavailable.motionsenseWirelessDC(batteryDataMarker.markedWindowsPhone, currDir);
        dataLossMarker.packetLoss(batteryDataMarker.markedWindowsMotionsense, Double.parseDouble(Config.get("WINDOW_SIZE")), samplingRate);
        sensorSignalQualityMarker.markWindowsQulaity(dataLossMarker.markedWindows);

        CSVExporter csvExporter = new CSVExporter();
        csvExporter.writeMarkedDataPointsToCSV(sensorSignalQualityMarker.markedWindows, Config.get("OUTPUT_PATH"), outputFileName);

    }

}
